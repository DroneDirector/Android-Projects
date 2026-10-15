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
    fun apply(context: Context, uri: Uri, gainDb: Int, limiter: Boolean): Boolean {
        if (gainDb == 0) return true
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
                            pending = process(buf.slice().order(ByteOrder.nativeOrder()), gain, limiter)
                            pendingOff = 0
                            pendingPts = info.presentationTimeUs
                            lastPts = info.presentationTimeUs
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decEos = true
                        dec.releaseOutputBuffer(o, false)
                    }
                }
                if (!encInEos && (pending != null || decEos)) {
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

    /** 16-bit PCM in, gain + soft limiter applied, little-endian bytes out. */
    private fun process(src: ByteBuffer, gain: Float, limiter: Boolean): ByteArray {
        val shorts = src.asShortBuffer()
        val out = ByteBuffer.allocate(shorts.remaining() * 2).order(ByteOrder.nativeOrder())
        while (shorts.hasRemaining()) {
            var x = shorts.get() / 32768f * gain
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
