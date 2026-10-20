package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * AVIF export: the picture is compressed with the phone's own AV1 encoder (Android 14+ has one
 * built in; newer chips have hardware ones) and written into an AVIF file (the HEIF image
 * container with an AV1 picture) by the app itself.
 */
object AvifWriter {
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AV1

    private fun encoderInfo(): MediaCodecInfo? = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, true) } }
            // Software (exact, any size) before hardware.
            .sortedBy { if (it.name.startsWith("c2.android") || it.name.contains("aom", true)) 0 else 1 }
            .firstOrNull()
    } catch (_: Throwable) { null }

    /** Why the last write failed (for the message). */
    @Volatile var lastError: String? = null

    /** Can this phone write AVIF? */
    val supported: Boolean by lazy { encoderInfo() != null }

    /** Writes [src] as AVIF to [out]. [quality] 0..100. Returns false if it couldn't. */
    fun write(src: Bitmap, out: File, quality: Int): Boolean {
        val info = encoderInfo() ?: return false
        val caps = info.getCapabilitiesForType(MIME)
        val vc = caps.videoCapabilities ?: return false
        // Even size within what the encoder takes (scaled down if the photo is bigger).
        var w = src.width; var h = src.height
        val maxW = vc.supportedWidths.upper; val maxH = vc.supportedHeights.upper
        var k = minOf(1f, maxW.toFloat() / w, maxH.toFloat() / h)
        while (true) {
            w = ((src.width * k).toInt() / 2) * 2; h = ((src.height * k).toInt() / 2) * 2
            if (w < 16 || h < 16) return false
            if (vc.isSizeSupported(w, h)) break
            k *= 0.9f
        }
        val bmp = if (w != src.width || h != src.height) Bitmap.createScaledBitmap(src, w, h, true) else src
        val obus = try { encode(info.name, caps, bmp, w, h, quality) } finally { if (bmp !== src) bmp.recycle() } ?: return false
        val clean = clean(obus)
        val seq = findSequenceHeader(clean) ?: return false
        val cfg = av1Config(seq) ?: return false
        out.writeBytes(container(w, h, clean, cfg, seq))
        // Check the file opens (Android 12+ reads AVIF); a file that doesn't is not kept.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val back = try { android.graphics.BitmapFactory.decodeFile(out.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) } catch (_: Throwable) { null }
            if (back == null) { lastError = "the AVIF file didn't open again"; return false }
            back.recycle()
        }
        return true
    }

    // ---------------------------------------------------------------- encoding

    private fun encode(name: String, caps: MediaCodecInfo.CodecCapabilities, bmp: Bitmap, w: Int, h: Int, quality: Int): ByteArray? {
        val codec = MediaCodec.createByCodecName(name)
        try {
            val fmt = MediaFormat.createVideoFormat(MIME, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_FRAME_RATE, 1)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                val ec = caps.encoderCapabilities
                val cq = ec?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ) == true
                if (cq) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
                    val r = ec!!.qualityRange
                    setInteger(MediaFormat.KEY_QUALITY, (r.lower + (r.upper - r.lower) * quality / 100f).roundToInt().coerceIn(r.lower, r.upper))
                } else {
                    // Bits for the one picture: about 0.4 (low quality) to 3 (top) bits per pixel.
                    val bpp = 0.4f + (quality.coerceIn(0, 100) / 100f).let { it * it } * 2.6f
                    val br = (w.toLong() * h * bpp).toLong().coerceIn(200_000L, (caps.videoCapabilities?.bitrateRange?.upper ?: Int.MAX_VALUE).toLong())
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                    setInteger(MediaFormat.KEY_BIT_RATE, br.toInt())
                }
            }
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            var fed = false
            val outBytes = ByteArrayOutputStream()
            val bi = MediaCodec.BufferInfo()
            val until = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < until) {
                if (!fed) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val img = codec.getInputImage(ii) ?: return null
                        fillYuv(img, px, w, h)
                        codec.queueInputBuffer(ii, 0, w * h * 3 / 2, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        fed = true
                    }
                }
                val oi = codec.dequeueOutputBuffer(bi, 10_000)
                if (oi >= 0) {
                    val b = codec.getOutputBuffer(oi)
                    if (b != null && bi.size > 0) {
                        val a = ByteArray(bi.size); b.position(bi.offset); b.get(a, 0, bi.size)
                        // Codec config: either an av1C record (starts 0x81) or the sequence header itself.
                        if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) { if ((a[0].toInt() and 0xFF) != 0x81) outBytes.write(a) else if (a.size > 4) outBytes.write(a, 4, a.size - 4) }
                        else outBytes.write(a)
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return outBytes.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            try { codec.release() } catch (_: Throwable) {}
        }
    }

    /** RGB → YUV 4:2:0, BT.709, full range. */
    private fun fillYuv(img: android.media.Image, px: IntArray, w: Int, h: Int) {
        val pl = img.planes
        val y = pl[0].buffer; val yr = pl[0].rowStride; val yp = pl[0].pixelStride
        val u = pl[1].buffer; val ur = pl[1].rowStride; val up = pl[1].pixelStride
        val v = pl[2].buffer; val vr = pl[2].rowStride; val vp = pl[2].pixelStride
        for (j in 0 until h) for (i in 0 until w) {
            val c = px[j * w + i]
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            y.put(j * yr + i * yp, (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255).toByte())
        }
        for (j in 0 until h / 2) for (i in 0 until w / 2) {
            var r = 0; var g = 0; var b = 0
            for (dy in 0..1) for (dx in 0..1) { val c = px[(j * 2 + dy) * w + i * 2 + dx]; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255 }
            val rf = r / 4f; val gf = g / 4f; val bf = b / 4f
            val cb = (-0.1146f * rf - 0.3854f * gf + 0.5f * bf + 128f).roundToInt().coerceIn(0, 255)
            val cr = (0.5f * rf - 0.4542f * gf - 0.0458f * bf + 128f).roundToInt().coerceIn(0, 255)
            u.put(j * ur + i * up, cb.toByte()); v.put(j * vr + i * vp, cr.toByte())
        }
    }

    // ---------------------------------------------------------------- AV1 bits

    private fun leb128(a: ByteArray, at: Int): Pair<Long, Int> {
        var v = 0L; var n = 0
        while (n < 8 && at + n < a.size) { val b = a[at + n].toInt() and 0xFF; v = v or ((b and 0x7F).toLong() shl (7 * n)); n++; if (b and 0x80 == 0) break }
        return v to n
    }

    /** One picture as AVIF wants it: no temporal delimiters or padding, the sequence header once (first). */
    private fun clean(a: ByteArray): ByteArray {
        val keep = ArrayList<ByteArray>(); var seq: ByteArray? = null
        var p = 0
        while (p < a.size) {
            val hdr = a[p].toInt() and 0xFF
            val type = (hdr shr 3) and 0xF; val ext = (hdr shr 2) and 1
            if ((hdr shr 1) and 1 == 0) return a
            val (size, n) = leb128(a, p + 1 + ext)
            val end = p + 1 + ext + n + size.toInt()
            if (end > a.size) return a
            val o = a.copyOfRange(p, end)
            when (type) { 1 -> if (seq == null) seq = o; 2, 15 -> {}; else -> keep += o }
            p = end
        }
        val out = ByteArrayOutputStream(); seq?.let { out.write(it) }; keep.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** The whole sequence header OBU (header + payload) in the stream, or null. */
    private fun findSequenceHeader(a: ByteArray): ByteArray? {
        var p = 0
        while (p < a.size) {
            val hdr = a[p].toInt() and 0xFF
            val type = (hdr shr 3) and 0xF; val ext = (hdr shr 2) and 1; val hasSize = (hdr shr 1) and 1
            if (hasSize == 0) return null
            val (size, n) = leb128(a, p + 1 + ext)
            val start = p; val end = p + 1 + ext + n + size.toInt()
            if (end > a.size) return null
            if (type == 1) return a.copyOfRange(start, end)
            p = end
        }
        return null
    }

    private class Bits(val a: ByteArray, var pos: Int) {
        fun bit(): Int { val b = (a[pos shr 3].toInt() shr (7 - (pos and 7))) and 1; pos++; return b }
        fun bits(n: Int): Int { var v = 0; repeat(n) { v = (v shl 1) or bit() }; return v }
    }

    /** av1C: the 4 configuration bytes, read from the sequence header. */
    private fun av1Config(seq: ByteArray): ByteArray? = try {
        val hdr = seq[0].toInt() and 0xFF; val ext = (hdr shr 2) and 1
        val (_, n) = leb128(seq, 1 + ext)
        val r = Bits(seq, (1 + ext + n) * 8)
        val profile = r.bits(3); r.bit()   // still_picture
        val reduced = r.bit()
        var level = 0; var tier = 0
        if (reduced == 1) level = r.bits(5)
        else {
            val timing = r.bit()
            var decoderModel = 0
            var bufferDelayLen = 0
            if (timing == 1) {
                r.bits(32); r.bits(32)
                if (r.bit() == 1) { var z = 0; while (r.bit() == 0) z++; if (z > 0) r.bits(z) }   // equal_picture_interval + uvlc
                decoderModel = r.bit()
                if (decoderModel == 1) { bufferDelayLen = r.bits(5) + 1; r.bits(32); r.bits(5); r.bits(5) }
            }
            val initialDelay = r.bit()
            val ops = r.bits(5) + 1
            for (i in 0 until ops) {
                r.bits(12)
                val lv = r.bits(5); var tr = 0
                if (lv > 7) tr = r.bit()
                if (decoderModel == 1) { if (r.bit() == 1) { r.bits(bufferDelayLen); r.bits(bufferDelayLen); r.bit() } }
                if (initialDelay == 1) { if (r.bit() == 1) r.bits(4) }
                if (i == 0) { level = lv; tier = tr }
            }
        }
        val wb = r.bits(4) + 1; val hb = r.bits(4) + 1
        r.bits(wb); r.bits(hb)
        if (reduced == 0) { if (r.bit() == 1) { r.bits(4); r.bits(3) } }
        r.bit(); r.bit(); r.bit()   // 128 superblock, filter intra, intra edge
        var orderHint = 0
        if (reduced == 0) {
            r.bit(); r.bit(); r.bit(); r.bit()   // interintra, masked compound, warped motion, dual filter
            orderHint = r.bit()
            if (orderHint == 1) { r.bit(); r.bit() }
            val chooseSct = r.bit()
            val forceSct = if (chooseSct == 1) 2 else r.bit()
            if (forceSct > 0) { if (r.bit() == 0) r.bit() }
            if (orderHint == 1) r.bits(3)
        }
        r.bit(); r.bit(); r.bit()   // superres, cdef, restoration
        val high = r.bit()
        var twelve = 0
        if (profile == 2 && high == 1) twelve = r.bit()
        val mono = if (profile == 1) 0 else r.bit()
        var ssx = 1; var ssy = 1; var csp = 0
        var cp = 2; var tc = 2; var mc = 2
        if (r.bit() == 1) { cp = r.bits(8); tc = r.bits(8); mc = r.bits(8) }
        if (mono == 1) { r.bit(); ssx = 1; ssy = 1 }
        else if (cp == 1 && tc == 13 && mc == 0) { ssx = 0; ssy = 0 }
        else {
            r.bit()   // colour range
            when (profile) {
                0 -> { ssx = 1; ssy = 1 }
                1 -> { ssx = 0; ssy = 0 }
                else -> if (high == 1 && twelve == 1) { ssx = r.bit(); ssy = if (ssx == 1) r.bit() else 0 } else { ssx = 1; ssy = 0 }
            }
            if (ssx == 1 && ssy == 1) csp = r.bits(2)
        }
        byteArrayOf(
            0x81.toByte(),
            ((profile shl 5) or level).toByte(),
            ((tier shl 7) or (high shl 6) or (twelve shl 5) or (mono shl 4) or (ssx shl 3) or (ssy shl 2) or csp).toByte(),
            0,
        )
    } catch (_: Throwable) { null }

    // ---------------------------------------------------------------- the AVIF file

    private fun box(type: String, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.writeInt(8 + body.size); d.writeBytes(type); d.write(body); return o.toByteArray()
    }
    private fun full(type: String, version: Int, flags: Int, body: ByteArray) = box(type, byteArrayOf(version.toByte(), (flags shr 16).toByte(), (flags shr 8).toByte(), flags.toByte()) + body)
    private fun bytes(f: DataOutputStream.() -> Unit): ByteArray { val o = ByteArrayOutputStream(); DataOutputStream(o).f(); return o.toByteArray() }

    private fun container(w: Int, h: Int, data: ByteArray, cfg: ByteArray, seq: ByteArray): ByteArray {
        val ftyp = box("ftyp", bytes { writeBytes("avif"); writeInt(0); writeBytes("avif"); writeBytes("mif1"); writeBytes("miaf") })
        val hdlr = full("hdlr", 0, 0, bytes { writeInt(0); writeBytes("pict"); writeInt(0); writeInt(0); writeInt(0); writeByte(0) })
        val pitm = full("pitm", 0, 0, bytes { writeShort(1) })
        val infe = full("infe", 2, 0, bytes { writeShort(1); writeShort(0); writeBytes("av01"); writeByte(0) })
        val iinf = full("iinf", 0, 0, bytes { writeShort(1) } + infe)
        val ispe = full("ispe", 0, 0, bytes { writeInt(w); writeInt(h) })
        val av1C = box("av1C", cfg + seq)
        val colr = box("colr", bytes { writeBytes("nclx"); writeShort(1); writeShort(13); writeShort(1); writeByte(0x80) })
        val pixi = full("pixi", 0, 0, bytes { writeByte(3); writeByte(8); writeByte(8); writeByte(8) })
        val ipco = box("ipco", ispe + av1C + colr + pixi)
        val ipma = full("ipma", 0, 0, bytes { writeInt(1); writeShort(1); writeByte(4); writeByte(0x01); writeByte(0x82); writeByte(0x03); writeByte(0x04) })
        val iprp = box("iprp", ipco + ipma)
        fun iloc(offset: Int) = full("iloc", 0, 0, bytes { writeByte(0x44); writeByte(0x00); writeShort(1); writeShort(1); writeShort(0); writeShort(1); writeInt(offset); writeInt(data.size) })
        fun meta(offset: Int) = full("meta", 0, 0, hdlr + pitm + iloc(offset) + iinf + iprp)
        val metaSize = meta(0).size
        val dataAt = ftyp.size + metaSize + 8
        return ftyp + meta(dataAt) + box("mdat", data)
    }
}
