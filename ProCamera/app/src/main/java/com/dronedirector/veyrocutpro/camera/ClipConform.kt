package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Finishes a clip after recording:
 * - **Pro audio**: the uncompressed take ([WavRecorder]) is lined up with the picture, saved as a
 *   24-bit WAV next to the clip, and becomes the clip's sound track (AAC).
 * - **NTSC rates**: phones film at whole rates (24, 30, 60), so 23.976 / 29.97 / 59.94 are made by
 *   slowing the clip by 0.1 % — picture timing and sound together (the broadcast "pull-down").
 */
object ClipConform {
    /** NTSC conform factor: 24 → 23.976 fps etc. */
    const val NTSC = 1001.0 / 1000.0

    /**
     * Rewrites [uri]. [take] = the pro-audio take (or null to keep / conform the clip's own sound);
     * [videoStartNanos] = when the first video frame was recorded (System.nanoTime), to line the take
     * up; [ntsc] = conform to the NTSC rate; [gainDb] / [limiter] = input gain for the take;
     * [wavOut] opens where the 24-bit WAV goes (null = no WAV). Returns false if it couldn't.
     */
    fun finish(
        context: Context, uri: Uri, take: WavRecorder.Take?, videoStartNanos: Long, ntsc: Boolean,
        gainDb: Int, limiter: Boolean, wavOut: (() -> OutputStream?)?,
    ): Boolean {
        val stretch = if (ntsc) NTSC else 1.0
        if (take == null && !ntsc) return true
        val tmpMp4 = File(context.cacheDir, "conform_${System.currentTimeMillis()}.mp4")
        val tmpWav = File(context.cacheDir, "conform_${System.currentTimeMillis()}.wav")
        var decoded: File? = null
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            var vIdx = -1; var aIdx = -1
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vIdx < 0) vIdx = i
                if (mime.startsWith("audio/") && aIdx < 0) aIdx = i
            }
            if (vIdx < 0) return false
            val vFmt = ex.getTrackFormat(vIdx)

            // ---- the sound to use: the pro take, or the clip's own (for the NTSC conform)
            var rate = 48_000; var ch = 2; var skip = 0L; var src: File? = null; var gain = 1f
            if (take != null) {
                rate = take.rate; ch = take.channels; src = take.file
                skip = (videoStartNanos - take.frame0Nanos) * rate / 1_000_000_000L
                gain = Math.pow(10.0, gainDb / 20.0).toFloat()
            } else if (aIdx >= 0) {
                val d = decodeToFloats(context, uri, aIdx) ?: return false
                decoded = d.first; rate = d.second; ch = d.third; src = d.first
            }

            // ---- process: line up, gain + soft limiter, NTSC stretch → float frames
            var encodedAudio: Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>? = null
            if (src != null) {
                val pcm = File(context.cacheDir, "conform_${System.currentTimeMillis()}.f32")
                val frames = render(src, ch, skip, gain, limiter && take != null, stretch, pcm)
                if (take != null && wavOut != null) {
                    writeWav24(pcm, rate, ch, frames, tmpWav)
                    wavOut()?.use { out -> tmpWav.inputStream().use { it.copyTo(out, 1 shl 16) } }
                }
                encodedAudio = encodeAac(pcm, rate, ch)
                pcm.delete()
            }

            // ---- new file: picture (re-timed for NTSC) + the sound
            val muxer = MediaMuxer(tmpMp4.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (vFmt.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(vFmt.getInteger(MediaFormat.KEY_ROTATION))
            val vt = muxer.addTrack(vFmt)
            val at = encodedAudio?.let { muxer.addTrack(it.first) }
            muxer.start()
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
                vinfo.set(0, n, (ex.sampleTime * stretch).toLong(), flags)
                muxer.writeSampleData(vt, vbuf, vinfo)
                ex.advance()
            }
            if (at != null) for ((bytes, bi) in encodedAudio!!.second) muxer.writeSampleData(at, ByteBuffer.wrap(bytes), bi)
            muxer.stop(); muxer.release()
            context.contentResolver.openOutputStream(uri, "wt")?.use { out -> tmpMp4.inputStream().use { it.copyTo(out, 1 shl 16) } } ?: return false
            return true
        } catch (_: Throwable) {
            return false
        } finally {
            ex.release()
            tmpMp4.delete(); tmpWav.delete(); decoded?.delete()
            take?.file?.delete()
        }
    }

    /** Reads float frames from [src] (skipping [skip] frames, or adding silence when negative), applies gain / limiter and the stretch; returns frames written. */
    private fun render(src: File, ch: Int, skip: Long, gain: Float, limiter: Boolean, stretch: Double, out: File): Long {
        val inp = BufferedInputStream(FileInputStream(src), 1 shl 16)
        val o = out.outputStream().buffered(1 shl 16)
        val frameBytes = 4 * ch
        if (skip > 0) { var left = skip * frameBytes; while (left > 0) { val k = inp.skip(left); if (k <= 0) break; left -= k } }
        val rb = ByteArray(frameBytes)
        val ob = ByteBuffer.allocate(frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        fun readFrame(dst: FloatArray): Boolean {
            var got = 0
            while (got < frameBytes) { val n = inp.read(rb, got, frameBytes - got); if (n < 0) return false; got += n }
            val bb = ByteBuffer.wrap(rb).order(ByteOrder.LITTLE_ENDIAN)
            for (c in 0 until ch) dst[c] = bb.float * gain
            return true
        }
        fun lim(x: Float): Float {
            if (!limiter) return x.coerceIn(-1f, 1f)
            val a = abs(x); return if (a <= 0.9f) x else Math.copySign(0.9f + 0.1f * tanh((a - 0.9f) / 0.1f), x)
        }
        var written = 0L
        fun emit(f: FloatArray) { ob.clear(); for (c in 0 until ch) ob.putFloat(lim(f[c])); o.write(ob.array()); written++ }
        // Silence first when the take started after the picture.
        if (skip < 0) { val z = FloatArray(ch); repeat((-skip).toInt()) { emit(z) } }
        val a = FloatArray(ch); val b = FloatArray(ch); val f = FloatArray(ch)
        if (!readFrame(a)) { inp.close(); o.close(); return written }
        var haveB = readFrame(b)
        if (stretch == 1.0) {
            emit(a)
            while (haveB) { emit(b); haveB = readFrame(b) }
        } else {
            // Output frame j sits at input position j / stretch (linear interpolation).
            var inPos = 0L
            var j = 0L
            while (true) {
                val pos = j / stretch
                while (pos >= inPos + 1 && haveB) { System.arraycopy(b, 0, a, 0, ch); haveB = readFrame(b); inPos++ }
                if (!haveB && pos >= inPos) break
                val t = (pos - inPos).toFloat()
                for (c in 0 until ch) f[c] = a[c] + (b[c] - a[c]) * t
                emit(f); j++
            }
        }
        inp.close(); o.close()
        return written
    }

    private fun writeWav24(pcm: File, rate: Int, ch: Int, frames: Long, out: File) {
        val dataBytes = frames * ch * 3
        RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(0)
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt((36 + dataBytes).coerceAtMost(0xFFFFFFFFL).toInt())
            h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray()); h.putInt(16)
            h.putShort(1); h.putShort(ch.toShort()); h.putInt(rate); h.putInt(rate * ch * 3); h.putShort((ch * 3).toShort()); h.putShort(24)
            h.put("data".toByteArray()); h.putInt(dataBytes.coerceAtMost(0xFFFFFFFFL).toInt())
            raf.write(h.array())
            BufferedInputStream(FileInputStream(pcm), 1 shl 16).use { inp ->
                val fb = ByteArray(4 * 4096)
                val outB = ByteArray(3 * 4096)
                while (true) {
                    val n = inp.read(fb); if (n <= 0) break
                    val bb = ByteBuffer.wrap(fb, 0, n - n % 4).order(ByteOrder.LITTLE_ENDIAN)
                    var k = 0
                    while (bb.remaining() >= 4) {
                        val v = (bb.float.coerceIn(-1f, 1f) * 8_388_607f).toInt()
                        outB[k++] = v.toByte(); outB[k++] = (v shr 8).toByte(); outB[k++] = (v shr 16).toByte()
                    }
                    raf.write(outB, 0, k)
                }
            }
        }
    }

    /** Float frames → AAC packets (in memory) with their format. */
    private fun encodeAac(pcm: File, rate: Int, ch: Int): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>? {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (ch > 1) 256_000 else 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
            val out = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
            var outFmt: MediaFormat? = null
            val inp = BufferedInputStream(FileInputStream(pcm), 1 shl 16)
            val fb = ByteArray(4 * ch * 1024)
            var frames = 0L
            var inEos = false
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (!inEos) {
                    val i = enc.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = inp.read(fb)
                        val pts = frames * 1_000_000L / rate
                        if (n <= 0) { enc.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true }
                        else {
                            val eb = enc.getInputBuffer(i)!!.order(ByteOrder.nativeOrder()); eb.clear()
                            val bb = ByteBuffer.wrap(fb, 0, n - n % (4 * ch)).order(ByteOrder.LITTLE_ENDIAN)
                            var cnt = 0
                            while (bb.remaining() >= 4) { eb.putShort((bb.float.coerceIn(-1f, 1f) * 32767f).toInt().toShort()); cnt++ }
                            enc.queueInputBuffer(i, 0, cnt * 2, pts, 0)
                            frames += cnt / ch
                        }
                    }
                }
                val o = enc.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFmt = enc.outputFormat
                else if (o >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val b = enc.getOutputBuffer(o)!!; b.position(info.offset); b.limit(info.offset + info.size)
                        out += ByteArray(info.size).also { b.get(it) } to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                    }
                    enc.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            inp.close()
            return (outFmt ?: return null) to out
        } catch (_: Throwable) { return null } finally { try { enc.stop() } catch (_: Throwable) {}; enc.release() }
    }

    /** The clip's own sound → float frames file, rate, channels. */
    private fun decodeToFloats(context: Context, uri: Uri, aIdx: Int): Triple<File, Int, Int>? {
        val ex = MediaExtractor()
        val file = File(context.cacheDir, "conform_dec_${System.currentTimeMillis()}.f32")
        var dec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val fmt = ex.getTrackFormat(aIdx)
            ex.selectTrack(aIdx)
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!).apply { configure(fmt, null, null, 0); start() }
            val info = MediaCodec.BufferInfo()
            var inEos = false
            file.outputStream().buffered(1 shl 16).use { o ->
                while (true) {
                    if (!inEos) {
                        val i = dec.dequeueInputBuffer(5_000)
                        if (i >= 0) {
                            val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                            if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true }
                            else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val oi = dec.dequeueOutputBuffer(info, 5_000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = dec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = dec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (oi >= 0) {
                        if (info.size > 0) {
                            val b = dec.getOutputBuffer(oi)!!; b.position(info.offset); b.limit(info.offset + info.size)
                            val sb = b.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                            val bb = ByteBuffer.allocate(sb.remaining() * 4).order(ByteOrder.LITTLE_ENDIAN)
                            while (sb.hasRemaining()) bb.putFloat(sb.get() / 32768f)
                            o.write(bb.array())
                        }
                        dec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return Triple(file, rate, ch)
        } catch (_: Throwable) { file.delete(); return null } finally {
            try { dec?.stop() } catch (_: Throwable) {}; try { dec?.release() } catch (_: Throwable) {}; ex.release()
        }
    }
}
