package com.dronedirector.acluxpro.camera

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Manual input gain for recordings. The recorder captures the microphone as-is; when a gain is
 * set, the sound track of the finished clip is re-encoded with the gain (and an optional soft
 * limiter) applied, while the picture is copied untouched. The live audio meter already shows
 * the gain-adjusted level, so what you see is what ends up in the file.
 */
object AudioGain {

    /** Rewrites [uri] in place. Returns false (leaving the clip untouched) if anything fails. */
    fun apply(context: Context, uri: Uri, gainDb: Int, limiter: Boolean, wind: Boolean = false, noise: Int = 0): Boolean {
        if (gainDb == 0 && !wind && noise <= 0) return true
        val tmp = File(context.cacheDir, "gain_${System.currentTimeMillis()}.mp4")
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        var enc: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            var vIdx = -1
            var aIdx = -1
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vIdx < 0) vIdx = i
                if (mime.startsWith("audio/") && aIdx < 0) aIdx = i
            }
            if (vIdx < 0 || aIdx < 0) return false
            val vFmt = ex.getTrackFormat(vIdx)
            val aFmt = ex.getTrackFormat(aIdx)
            val rate = aFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val ch = aFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val gain = 10f.pow(gainDb / 20f)
            // Wind filter: steep cut below ~120 Hz where wind rumble lives (per channel).
            val hp = if (wind) List(ch) { WindCut(rate) } else null
            // Voice noise reduction (Settings → AUDIO): delays the sound slightly, so the start is trimmed to stay in sync.
            val nr = if (noise > 0) NoiseReducer(ch, noise) else null
            var dropLeft = (nr?.delay ?: 0) * ch
            var basePts = -1L
            var emittedFrames = 0L
            var flushed = false

            // ---- 1. decode, apply gain, re-encode the sound into memory
            ex.selectTrack(aIdx)
            dec = MediaCodec.createDecoderByType(aFmt.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(aFmt, null, null, 0); start()
            }
            val encFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (ch > 1) 256_000 else 160_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start()
            }
            val encoded = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
            var outFmt: MediaFormat? = null
            val info = MediaCodec.BufferInfo()
            val einfo = MediaCodec.BufferInfo()
            var inEos = false
            var decEos = false
            var encInEos = false
            var pending: ByteArray? = null
            var pendingOff = 0
            var pendingPts = 0L
            var lastPts = 0L
            val deadline = System.currentTimeMillis() + 10 * 60_000L
            while (System.currentTimeMillis() < deadline) {
                if (!inEos) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                if (!decEos && pending == null) {
                    val o = dec.dequeueOutputBuffer(info, 5_000)
                    if (o >= 0) {
                        if (info.size > 0) {
                            val buf = dec.getOutputBuffer(o)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            if (nr == null) {
                                pending = process(buf.slice().order(ByteOrder.nativeOrder()), gain, limiter, hp)
                                pendingPts = info.presentationTimeUs
                            } else {
                                if (basePts < 0) basePts = info.presentationTimeUs
                                var f = nr.process(toFloats(buf.slice().order(ByteOrder.nativeOrder()), gain, hp))
                                if (dropLeft > 0) { val d = minOf(dropLeft, f.size); f = f.copyOfRange(d, f.size); dropLeft -= d }
                                pendingPts = basePts + emittedFrames * 1_000_000L / rate
                                emittedFrames += f.size / ch
                                pending = if (f.isEmpty()) null else toBytes(f, limiter)
                            }
                            pendingOff = 0
                            lastPts = info.presentationTimeUs
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decEos = true
                        dec.releaseOutputBuffer(o, false)
                    }
                }
                // Noise reduction: push out what's still inside the filter before ending.
                if (nr != null && decEos && !flushed && pending == null) {
                    flushed = true
                    var f = nr.flush()
                    if (dropLeft > 0) { val d = minOf(dropLeft, f.size); f = f.copyOfRange(d, f.size); dropLeft -= d }
                    if (f.isNotEmpty()) {
                        pendingPts = basePts.coerceAtLeast(0) + emittedFrames * 1_000_000L / rate
                        emittedFrames += f.size / ch
                        pending = toBytes(f, limiter); pendingOff = 0
                    }
                }
                if (!encInEos && (pending != null || (decEos && (nr == null || flushed)))) {
                    val i = enc.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val p = pending
                        if (p != null) {
                            val eb = enc.getInputBuffer(i)!!
                            eb.clear()
                            val len = min(eb.remaining(), p.size - pendingOff)
                            eb.put(p, pendingOff, len)
                            val pts = pendingPts + (pendingOff / (2 * ch)).toLong() * 1_000_000L / rate
                            enc.queueInputBuffer(i, 0, len, pts, 0)
                            pendingOff += len
                            if (pendingOff >= p.size) pending = null
                        } else {
                            enc.queueInputBuffer(i, 0, 0, lastPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encInEos = true
                        }
                    }
                }
                val eo = enc.dequeueOutputBuffer(einfo, 5_000)
                if (eo == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFmt = enc.outputFormat
                } else if (eo >= 0) {
                    if (einfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && einfo.size > 0) {
                        val b = enc.getOutputBuffer(eo)!!
                        b.position(einfo.offset); b.limit(einfo.offset + einfo.size)
                        val bytes = ByteArray(einfo.size).also { b.get(it) }
                        val copy = MediaCodec.BufferInfo().apply { set(0, einfo.size, einfo.presentationTimeUs, einfo.flags) }
                        encoded += bytes to copy
                    }
                    enc.releaseOutputBuffer(eo, false)
                    if (einfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            val audioFormat = outFmt ?: return false
            if (encoded.isEmpty()) return false

            // ---- 2. new file: picture copied as-is + the new sound
            val muxer = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (vFmt.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(vFmt.getInteger(MediaFormat.KEY_ROTATION))
            val vt = muxer.addTrack(vFmt)
            val at = muxer.addTrack(audioFormat)
            muxer.start()
            ex.unselectTrack(aIdx)
            ex.selectTrack(vIdx)
            ex.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val maxIn = if (vFmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) vFmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val vbuf = ByteBuffer.allocateDirect(maxOf(maxIn, 8 * 1024 * 1024))
            val vinfo = MediaCodec.BufferInfo()
            while (true) {
                vbuf.clear()
                val n = ex.readSampleData(vbuf, 0)
                if (n < 0) break
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                vinfo.set(0, n, ex.sampleTime, flags)
                muxer.writeSampleData(vt, vbuf, vinfo)
                ex.advance()
            }
            for ((bytes, bi) in encoded) muxer.writeSampleData(at, ByteBuffer.wrap(bytes), bi)
            muxer.stop()
            muxer.release()

            // ---- 3. replace the original clip's contents
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                tmp.inputStream().use { it.copyTo(out, 1 shl 16) }
            } ?: return false
            return true
        } catch (_: Throwable) {
            return false
        } finally {
            try { dec?.stop() } catch (_: Throwable) {}
            try { dec?.release() } catch (_: Throwable) {}
            try { enc?.stop() } catch (_: Throwable) {}
            try { enc?.release() } catch (_: Throwable) {}
            ex.release()
            tmp.delete()
        }
    }

    private fun toFloats(src: ByteBuffer, gain: Float, hp: List<WindCut>?): FloatArray {
        val shorts = src.asShortBuffer()
        val out = FloatArray(shorts.remaining())
        var c = 0
        for (i in out.indices) {
            var x = shorts.get() / 32768f * gain
            if (hp != null) { x = hp[c].process(x); c = (c + 1) % hp.size }
            out[i] = x
        }
        return out
    }

    private fun toBytes(f: FloatArray, limiter: Boolean): ByteArray {
        val out = ByteBuffer.allocate(f.size * 2).order(ByteOrder.nativeOrder())
        for (v in f) {
            var x = v
            if (limiter) {
                val a = abs(x)
                if (a > 0.8f) x = (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)) * if (x < 0) -1f else 1f
            }
            out.putShort((x.coerceIn(-1f, 0.99997f) * 32768f).toInt().toShort())
        }
        return out.array()
    }

    /** 16-bit PCM in, gain + soft limiter applied, little-endian bytes out. */
    private fun process(src: ByteBuffer, gain: Float, limiter: Boolean, hp: List<WindCut>? = null): ByteArray {
        val shorts = src.asShortBuffer()
        val out = ByteBuffer.allocate(shorts.remaining() * 2).order(ByteOrder.nativeOrder())
        var c = 0
        while (shorts.hasRemaining()) {
            var x = shorts.get() / 32768f * gain
            if (hp != null) { x = hp[c].process(x); c = (c + 1) % hp.size }
            if (limiter) {
                val a = abs(x)
                if (a > 0.8f) x = (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)) * if (x < 0) -1f else 1f
            }
            x = x.coerceIn(-1f, 0.99997f)
            out.putShort((x * 32768f).toInt().toShort())
        }
        return out.array()
    }
}

/** 4th-order high-pass (two biquads) at 120 Hz — removes wind rumble and handling noise. */
class WindCut(rate: Int) {
    private val b = DoubleArray(3); private val a = DoubleArray(3)
    private val z1 = DoubleArray(2); private val z2 = DoubleArray(2)

    init {
        val w = 2 * Math.PI * 120.0 / rate
        val cs = Math.cos(w); val al = Math.sin(w) / (2 * 0.7071)
        val a0 = 1 + al
        b[0] = (1 + cs) / 2 / a0; b[1] = -(1 + cs) / a0; b[2] = (1 + cs) / 2 / a0
        a[1] = -2 * cs / a0; a[2] = (1 - al) / a0
    }

    fun process(x: Float): Float {
        var v = x.toDouble()
        for (s in 0 until 2) {
            val y = b[0] * v + z1[s]
            z1[s] = b[1] * v - a[1] * y + z2[s]
            z2[s] = b[2] * v - a[2] * y
            v = y
        }
        return v.toFloat()
    }
}

/**
 * Voice noise reduction for recorded sound: spectral subtraction with a noise floor that follows the
 * quietest moments (hiss, hum, room and traffic noise), 1024-point frames with 50 % overlap.
 * Output is delayed by [delay] samples per channel; the caller drops that much from the start.
 */
class NoiseReducer(private val channels: Int, strength: Int) {
    private val n = 1024
    private val hop = n / 2
    val delay = n - hop
    private val over = if (strength >= 2) 2.6f else 1.6f
    private val floor = if (strength >= 2) 0.12f else 0.25f
    private val win = FloatArray(n) { kotlin.math.sqrt(0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * it / n).toFloat()) }
    private val hist = Array(channels) { FloatArray(n) }
    private val acc = Array(channels) { FloatArray(n) }
    private val noise = Array(channels) { FloatArray(n / 2 + 1) }
    private val gPrev = Array(channels) { FloatArray(n / 2 + 1) { 1f } }
    private var started = BooleanArray(channels)
    private val fresh = Array(channels) { FloatArray(hop) }
    private var fill = 0
    private val re = FloatArray(n); private val im = FloatArray(n)

    /** Interleaved samples in (−1..1) → interleaved samples out (same count, delayed). */
    fun process(input: FloatArray): FloatArray {
        val frames = input.size / channels
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) fresh[c][fill] = input[f * channels + c]
            fill++
            if (fill == hop) {
                for (c in 0 until channels) frame(c)
                fill = 0
            }
            // Emit from the overlap-add buffer, one sample per channel per input frame.
            for (c in 0 until channels) out[f * channels + c] = acc[c][fill]
        }
        return out
    }

    /** The last [delay] samples per channel still inside the filter (call once at the end). */
    fun flush(): FloatArray = process(FloatArray(delay * channels))

    private fun frame(c: Int) {
        val h = hist[c]
        System.arraycopy(h, hop, h, 0, n - hop)
        System.arraycopy(fresh[c], 0, h, n - hop, hop)
        for (i in 0 until n) { re[i] = h[i] * win[i]; im[i] = 0f }
        fft(re, im, false)
        val nz = noise[c]; val gp = gPrev[c]
        for (k in 0..n / 2) {
            val p = re[k] * re[k] + im[k] * im[k] + 1e-12f
            if (!started[c]) nz[k] = p
            // Noise floor: falls quickly to quiet moments, rises slowly (≈ +3 dB/s).
            nz[k] = if (p < nz[k]) 0.7f * nz[k] + 0.3f * p else nz[k] * 1.007f
            var g = kotlin.math.sqrt((1f - over * nz[k] / p).coerceAtLeast(floor * floor))
            // Smooth over time (fast attack for speech, slower release) against "musical noise".
            g = if (g > gp[k]) 0.3f * gp[k] + 0.7f * g else 0.65f * gp[k] + 0.35f * g
            gp[k] = g
            re[k] *= g; im[k] *= g
            if (k in 1 until n / 2) { re[n - k] = re[k]; im[n - k] = -im[k] }
        }
        started[c] = true
        fft(re, im, true)
        val a = acc[c]
        // Shift out the samples already emitted, then overlap-add the new frame.
        System.arraycopy(a, hop, a, 0, n - hop)
        java.util.Arrays.fill(a, n - hop, n, 0f)
        for (i in 0 until n) a[i] += re[i] * win[i]
    }

    /** In-place radix-2 FFT; [inverse] scales by 1/n. */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * Math.PI / len * (if (inverse) 1 else -1)
            val wr = kotlin.math.cos(ang).toFloat(); val wi = kotlin.math.sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}
