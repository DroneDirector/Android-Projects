package com.dronedirector.acluxpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * RAW (DNG) develop for the photo editor — our own code, no third-party RAW library.
 *
 * Reads the sensor data straight from the DNG (uncompressed or lossless-JPEG, strips or tiles,
 * Bayer CFA or linear RGB), applies the black / white levels, linearisation table and the phone's
 * lens-shading gain maps, demosaics (half-size superpixels for previews, Malvar-He-Cutler at full
 * size for exports), converts with the camera's colour matrix, then white balance, exposure,
 * highlights, shadows, whites and blacks are applied in floating point with headroom above white
 * and a soft highlight roll-off — only then does the picture become 8-bit for the other tools.
 */
object RawDng {

    /** Developing choices kept with the edit (the Light / Color sliders do the rest). */
    data class Dev(
        /** Index into [PROFILES]. */
        val profile: Int = 0,
        /** Index into [WB_PRESETS]. */
        val wb: Int = 0,
    )

    class Profile(val label: String, val contrast: Float, val sat: Float, val knee: Float, val mono: Boolean = false)
    val PROFILES = listOf(
        Profile("Standard", 0.22f, 1.10f, 0.74f),
        Profile("Flat", 0.0f, 0.96f, 0.60f),
        Profile("Vivid", 0.38f, 1.30f, 0.80f),
        Profile("Portrait", 0.12f, 1.02f, 0.70f),
        Profile("Landscape", 0.30f, 1.22f, 0.78f),
        Profile("Mono", 0.28f, 0f, 0.74f, mono = true),
    )
    /** White balance presets; 0 = as shot, 1 = auto, others a colour temperature in Kelvin. */
    val WB_PRESETS = listOf("As shot" to 0, "Auto" to 0, "Daylight" to 5500, "Cloudy" to 6500, "Shade" to 7500, "Tungsten" to 2850, "Fluorescent" to 3800)

    class UnsupportedRaw(msg: String) : Exception(msg)

    // ------------------------------------------------------------------ file access

    private class Src(context: Context, uri: Uri) : AutoCloseable {
        private val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw java.io.IOException("can't open")
        private val fis = FileInputStream(pfd.fileDescriptor)
        val ch: FileChannel = fis.channel
        val size: Long = ch.size()
        fun bytes(pos: Long, n: Int): ByteArray {
            val b = ByteBuffer.allocate(n)
            var p = pos
            while (b.hasRemaining()) { val r = ch.read(b, p); if (r <= 0) break; p += r }
            return b.array()
        }
        override fun close() { try { fis.close() } catch (_: Throwable) {}; try { pfd.close() } catch (_: Throwable) {} }
    }

    private class Entry(val type: Int, val count: Int, val data: ByteArray)
    private class Ifd(val tags: Map<Int, Entry>, val order: ByteOrder) {
        fun has(t: Int) = tags.containsKey(t)
        fun nums(t: Int): DoubleArray? {
            val e = tags[t] ?: return null
            val bb = ByteBuffer.wrap(e.data).order(order)
            return DoubleArray(e.count) { i ->
                when (e.type) {
                    1, 7 -> (bb.get(i).toInt() and 0xFF).toDouble()
                    6 -> bb.get(i).toInt().toDouble()
                    3 -> (bb.getShort(i * 2).toInt() and 0xFFFF).toDouble()
                    8 -> bb.getShort(i * 2).toInt().toDouble()
                    4, 13 -> (bb.getInt(i * 4).toLong() and 0xFFFFFFFFL).toDouble()
                    9 -> bb.getInt(i * 4).toDouble()
                    5 -> { val a = bb.getInt(i * 8).toLong() and 0xFFFFFFFFL; val b = bb.getInt(i * 8 + 4).toLong() and 0xFFFFFFFFL; if (b == 0L) 0.0 else a.toDouble() / b }
                    10 -> { val a = bb.getInt(i * 8); val b = bb.getInt(i * 8 + 4); if (b == 0) 0.0 else a.toDouble() / b }
                    11 -> bb.getFloat(i * 4).toDouble()
                    12 -> bb.getDouble(i * 8)
                    else -> 0.0
                }
            }
        }
        fun num(t: Int, def: Double = 0.0): Double = nums(t)?.firstOrNull() ?: def
        fun raw(t: Int): ByteArray? = tags[t]?.data
    }

    private val TYPE_SIZE = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 4)

    private fun readIfd(src: Src, pos: Long, order: ByteOrder): Pair<Ifd, Long>? {
        if (pos <= 0 || pos + 2 > src.size) return null
        val n = ByteBuffer.wrap(src.bytes(pos, 2)).order(order).short.toInt() and 0xFFFF
        if (n == 0 || n > 4000) return null
        val buf = ByteBuffer.wrap(src.bytes(pos + 2, n * 12 + 4)).order(order)
        val tags = HashMap<Int, Entry>()
        for (i in 0 until n) {
            val tag = buf.getShort(i * 12).toInt() and 0xFFFF
            val type = buf.getShort(i * 12 + 2).toInt() and 0xFFFF
            val count = buf.getInt(i * 12 + 4)
            if (type !in 1..13 || count < 0) continue
            val len = TYPE_SIZE[type].toLong() * count
            if (len > 64L * 1024 * 1024) continue
            val data = if (len <= 4) ByteArray(len.toInt()).also { for (k in it.indices) it[k] = buf.get(i * 12 + 8 + k) }
            else {
                val off = buf.getInt(i * 12 + 8).toLong() and 0xFFFFFFFFL
                if (off + len > src.size) continue
                src.bytes(off, len.toInt())
            }
            tags[tag] = Entry(type, count, data)
        }
        val next = buf.getInt(n * 12).toLong() and 0xFFFFFFFFL
        return Ifd(tags, order) to next
    }

    // ------------------------------------------------------------------ metadata

    class Meta internal constructor(
        val uri: Uri,
        internal val order: ByteOrder,
        val width: Int, val height: Int,
        internal val spp: Int, internal val bps: Int, internal val compression: Int,
        internal val tileW: Int, internal val tileH: Int, internal val offsets: LongArray, internal val counts: LongArray, internal val rowsPerStrip: Int,
        internal val cfa: IntArray?,
        internal val blackRep: IntArray, internal val black: DoubleArray, internal val blackDH: DoubleArray?, internal val blackDV: DoubleArray?,
        internal val white: DoubleArray, internal val linTable: IntArray?,
        internal val cropTop: Int, internal val cropLeft: Int, val cropW: Int, val cropH: Int,
        internal val gainMaps: List<GainMap>,
        /** XYZ → camera (3×3), or null. */
        internal val colorMatrix: DoubleArray?,
        internal val asShot: DoubleArray,
        val baselineExposure: Float,
        val orientation: Int,
    ) {
        /** Grey-world white balance from the preview, filled in by [preview]. */
        @Volatile internal var autoMul: FloatArray? = null
        /** Long side of the developed picture at full size. */
        val longSide: Int get() = max(cropW, cropH)
    }

    internal class GainMap(
        val top: Int, val left: Int, val bottom: Int, val right: Int, val plane: Int, val planes: Int,
        val rowPitch: Int, val colPitch: Int, val pv: Int, val ph: Int,
        val spV: Double, val spH: Double, val orV: Double, val orH: Double, val mapPlanes: Int, val gains: FloatArray,
    )

    /** True for files that look like DNGs (by name / type, confirmed by the DNGVersion tag). */
    fun isDng(context: Context, uri: Uri): Boolean = try {
        val type = context.contentResolver.getType(uri) ?: ""
        val name = (uri.lastPathSegment ?: "").lowercase()
        val hinted = type.contains("dng", true) || type.contains("x-adobe", true) || name.endsWith(".dng") || type == "image/x-raw" || type.isEmpty() || type == "application/octet-stream"
        hinted && Src(context, uri).use { s ->
            val h = s.bytes(0, 8)
            val order = when { h[0] == 'I'.code.toByte() && h[1] == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN; h[0] == 'M'.code.toByte() && h[1] == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN; else -> return@use false }
            val first = ByteBuffer.wrap(h).order(order).getInt(4).toLong() and 0xFFFFFFFFL
            readIfd(s, first, order)?.first?.has(50706) == true
        }
    } catch (_: Throwable) { false }

    fun open(context: Context, uri: Uri): Meta = Src(context, uri).use { s ->
        val h = s.bytes(0, 8)
        val order = if (h[0] == 'M'.code.toByte()) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val first = ByteBuffer.wrap(h).order(order).getInt(4).toLong() and 0xFFFFFFFFL
        val all = ArrayList<Ifd>()
        fun walk(pos: Long, depth: Int) {
            var p = pos; var guard = 0
            while (p > 0 && guard++ < 16) {
                val (ifd, next) = readIfd(s, p, order) ?: return
                all += ifd
                if (depth < 3) ifd.nums(330)?.forEach { walk(it.toLong(), depth + 1) }
                p = next
            }
        }
        walk(first, 0)
        val ifd0 = all.firstOrNull() ?: throw UnsupportedRaw("Not a DNG")
        val rawIfd = all.filter {
            val pi = it.num(262).toInt()
            (it.num(254, 0.0).toInt() and 1) == 0 && (pi == 32803 || pi == 34892) && it.has(256)
        }.maxByOrNull { it.num(256) * it.num(257) } ?: throw UnsupportedRaw("No raw image in this DNG")

        val photometric = rawIfd.num(262).toInt()
        val spp = rawIfd.num(277, 1.0).toInt()
        val w = rawIfd.num(256).toInt(); val ht = rawIfd.num(257).toInt()
        val compression = rawIfd.num(259, 1.0).toInt()
        if (compression != 1 && compression != 7) throw UnsupportedRaw("This DNG's compression isn't supported yet")
        val bps = rawIfd.num(258, 16.0).toInt()
        var cfa: IntArray? = null
        if (photometric == 32803) {
            if (spp != 1) throw UnsupportedRaw("Unusual sensor layout")
            val dim = rawIfd.nums(33421) ?: doubleArrayOf(2.0, 2.0)
            val pat = rawIfd.nums(33422) ?: throw UnsupportedRaw("Missing colour filter pattern")
            if (dim[0].toInt() != 2 || dim[1].toInt() != 2 || pat.size < 4) throw UnsupportedRaw("Only Bayer (2×2) sensors are supported")
            cfa = IntArray(4) { pat[it].toInt() }
            if (cfa.any { it !in 0..2 }) throw UnsupportedRaw("Unusual colour filter")
        } else if (spp != 3) throw UnsupportedRaw("Unusual sensor layout")

        val tiled = rawIfd.has(322)
        val offsets = (if (tiled) rawIfd.nums(324) else rawIfd.nums(273))?.map { it.toLong() }?.toLongArray() ?: throw UnsupportedRaw("No image data")
        val counts = (if (tiled) rawIfd.nums(325) else rawIfd.nums(279))?.map { it.toLong() }?.toLongArray() ?: throw UnsupportedRaw("No image data")

        val rep = rawIfd.nums(50713)?.map { it.toInt() }?.toIntArray() ?: intArrayOf(1, 1)
        val black = rawIfd.nums(50714) ?: ifd0.nums(50714) ?: doubleArrayOf(0.0)
        val white = rawIfd.nums(50717) ?: ifd0.nums(50717) ?: doubleArrayOf(((1L shl bps) - 1).toDouble())
        val lin = rawIfd.nums(50712)?.map { it.toInt() }?.toIntArray()

        // Active area, then the default crop inside it.
        val aa = rawIfd.nums(50829)?.map { it.toInt() }
        val aTop = aa?.getOrNull(0) ?: 0; val aLeft = aa?.getOrNull(1) ?: 0
        val aH = (aa?.getOrNull(2) ?: ht) - aTop; val aW = (aa?.getOrNull(3) ?: w) - aLeft
        val co = rawIfd.nums(50719); val cs = rawIfd.nums(50720)
        val cTop = aTop + (co?.getOrNull(1)?.roundToInt() ?: 0)
        val cLeft = aLeft + (co?.getOrNull(0)?.roundToInt() ?: 0)
        val cW = (cs?.getOrNull(0)?.roundToInt() ?: aW).coerceAtMost(w - cLeft).coerceAtLeast(8)
        val cH = (cs?.getOrNull(1)?.roundToInt() ?: aH).coerceAtMost(ht - cTop).coerceAtLeast(8)

        val gains = parseGainMaps(rawIfd.raw(51009) ?: ifd0.raw(51009))

        // Colour: the matrix for daylight when there are two.
        val cm1 = ifd0.nums(50721) ?: rawIfd.nums(50721)
        val cm2 = ifd0.nums(50722) ?: rawIfd.nums(50722)
        val il1 = ifd0.num(50778, 21.0).toInt(); val il2 = ifd0.num(50779, 21.0).toInt()
        val cm = when {
            cm1 != null && cm2 != null -> if (kotlin.math.abs(illumK(il2) - 6504) < kotlin.math.abs(illumK(il1) - 6504)) cm2 else cm1
            else -> cm1 ?: cm2
        }?.takeIf { it.size >= 9 }
        val asShot = (ifd0.nums(50728) ?: rawIfd.nums(50728))?.takeIf { it.size >= 3 && it.all { v -> v > 0 } } ?: doubleArrayOf(1.0, 1.0, 1.0)
        val be = (ifd0.nums(50730) ?: rawIfd.nums(50730))?.firstOrNull()?.toFloat() ?: 0f
        val orient = ifd0.num(274, 1.0).toInt()

        Meta(
            uri, order, w, ht, spp, bps, compression,
            if (tiled) rawIfd.num(322).toInt() else 0, if (tiled) rawIfd.num(323).toInt() else 0, offsets, counts,
            rawIfd.num(278, ht.toDouble()).toInt().coerceAtLeast(1),
            cfa, rep, black, rawIfd.nums(50715), rawIfd.nums(50716), white, lin,
            cTop, cLeft, cW, cH, gains, cm, asShot, be, orient,
        )
    }

    private fun illumK(code: Int): Int = when (code) {
        17, 3, 24 -> 2856; 18 -> 4874; 19 -> 6774; 20 -> 5503; 21, 10 -> 6504; 22, 11 -> 7504; 23 -> 5003
        2, 14 -> 4200; 12 -> 6400; 13 -> 5000; 15 -> 3450; else -> 5500
    }

    private fun parseGainMaps(b: ByteArray?): List<GainMap> {
        if (b == null || b.size < 4) return emptyList()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
        val out = ArrayList<GainMap>()
        try {
            val n = bb.int
            repeat(n) {
                val id = bb.int; bb.int; bb.int; val size = bb.int
                val start = bb.position()
                if (id == 9) {
                    val top = bb.int; val left = bb.int; val bottom = bb.int; val right = bb.int
                    val plane = bb.int; val planes = bb.int; val rp = bb.int; val cp = bb.int
                    val pv = bb.int; val ph = bb.int
                    val sv = bb.double; val sh = bb.double; val ov = bb.double; val oh = bb.double
                    val mp = bb.int
                    val cnt = pv * ph * mp
                    if (cnt in 1..1_000_000) {
                        val g = FloatArray(cnt) { bb.float }
                        out += GainMap(top, left, bottom, right, plane, planes, rp.coerceAtLeast(1), cp.coerceAtLeast(1), pv, ph, sv, sh, ov, oh, mp, g)
                    }
                }
                bb.position(start + size)
            }
        } catch (_: Throwable) {}
        return out
    }

    // ------------------------------------------------------------------ sensor decode

    /** Normalised sensor values, cropped: 0..1 ⇒ 0..[ONE] (headroom for lens-shading gains). */
    internal class Sensor(val w: Int, val h: Int, val spp: Int, val data: ShortArray, val cfa: IntArray?) {
        fun v(i: Int): Float = (data[i].toInt() and 0xFFFF) / ONE
        /** Colour (0 R, 1 G, 2 B) at x, y. */
        fun color(x: Int, y: Int): Int = cfa!![((y and 1) shl 1) or (x and 1)]
    }
    private const val ONE = 16383f

    internal fun decode(context: Context, m: Meta): Sensor = Src(context, m.uri).use { s ->
        val w = m.width; val h = m.height; val spp = m.spp
        val raw = ShortArray(w * h * spp)
        fun put(x: Int, y: Int, c: Int, v: Int) { if (x in 0 until w && y in 0 until h) raw[(y * w + x) * spp + c] = v.toShort() }
        val tiled = m.tileW > 0
        val tw = if (tiled) m.tileW else w
        val th = if (tiled) m.tileH else m.rowsPerStrip
        val across = if (tiled) (w + tw - 1) / tw else 1
        for (k in m.offsets.indices) {
            val bytes = s.bytes(m.offsets[k], m.counts.getOrElse(k) { 0L }.toInt().coerceAtLeast(0))
            val x0 = if (tiled) (k % across) * tw else 0
            val y0 = if (tiled) (k / across) * th else k * th
            if (m.compression == 7) {
                val lj = LJpeg(bytes).decode()
                val samplesPerRow = tw * spp
                for (i in 0 until min(lj.size, samplesPerRow * th)) {
                    val r = i / samplesPerRow; val c = i % samplesPerRow
                    put(x0 + c / spp, y0 + r, c % spp, lj[i])
                }
            } else {
                val bb = ByteBuffer.wrap(bytes).order(m.order)
                val rowLen = tw * spp
                when (m.bps) {
                    16 -> { var i = 0; val n = bytes.size / 2; while (i < n) { val r = i / rowLen; val c = i % rowLen; put(x0 + c / spp, y0 + r, c % spp, bb.getShort(i * 2).toInt() and 0xFFFF); i++ } }
                    8 -> for (i in bytes.indices) { val r = i / rowLen; val c = i % rowLen; put(x0 + c / spp, y0 + r, c % spp, bytes[i].toInt() and 0xFF) }
                    else -> {
                        // Packed 10 / 12 / 14 bits, most significant bit first, rows byte-aligned.
                        val rowBytes = (rowLen * m.bps + 7) / 8
                        var r = 0
                        while ((r + 1) * rowBytes <= bytes.size && r < th) {
                            var bit = r.toLong() * rowBytes * 8
                            for (c in 0 until rowLen) {
                                var v = 0
                                for (b in 0 until m.bps) { val byte = bytes[(bit ushr 3).toInt()].toInt(); v = (v shl 1) or ((byte shr (7 - (bit and 7).toInt())) and 1); bit++ }
                                put(x0 + c / spp, y0 + r, c % spp, v)
                            }
                            r++
                        }
                    }
                }
            }
        }
        // Linearise, black / white levels, normalise — in place (value × ONE, headroom for gains).
        val repR = m.blackRep.getOrElse(0) { 1 }.coerceAtLeast(1); val repC = m.blackRep.getOrElse(1) { 1 }.coerceAtLeast(1)
        val lt = m.linTable
        for (y in 0 until h) for (x in 0 until w) for (c in 0 until spp) {
            val i = (y * w + x) * spp + c
            var v = raw[i].toInt() and 0xFFFF
            if (lt != null && lt.isNotEmpty()) v = lt[v.coerceAtMost(lt.size - 1)]
            val bi = (((y % repR) * repC + (x % repC)) * spp + c).coerceAtMost(m.black.size - 1)
            var bl = m.black[bi]
            m.blackDH?.let { if (x < it.size) bl += it[x] }
            m.blackDV?.let { if (y < it.size) bl += it[y] }
            val wl = m.white[c.coerceAtMost(m.white.size - 1)]
            val f = ((v - bl) / (wl - bl).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            raw[i] = (f * ONE).roundToInt().toShort()
        }
        // Lens shading (OpcodeList2 gain maps), in stored-image coordinates.
        for (g in m.gainMaps) {
            val hh = (g.bottom - g.top).coerceAtLeast(1).toDouble(); val ww = (g.right - g.left).coerceAtLeast(1).toDouble()
            var y = g.top
            while (y < min(g.bottom, h)) {
                val gv = (((y - g.top) / hh) - g.orV) / g.spV.coerceAtLeast(1e-9)
                val v0 = gv.toInt().coerceIn(0, max(0, g.pv - 1)); val v1 = (v0 + 1).coerceAtMost(g.pv - 1); val fv = (gv - v0).toFloat().coerceIn(0f, 1f)
                var x = g.left
                while (x < min(g.right, w)) {
                    val gh = (((x - g.left) / ww) - g.orH) / g.spH.coerceAtLeast(1e-9)
                    val h0 = gh.toInt().coerceIn(0, max(0, g.ph - 1)); val h1 = (h0 + 1).coerceAtMost(g.ph - 1); val fh = (gh - h0).toFloat().coerceIn(0f, 1f)
                    for (p in g.plane until min(g.plane + g.planes, spp)) {
                        val mp = if (g.mapPlanes > 1) (p - g.plane).coerceAtMost(g.mapPlanes - 1) else 0
                        fun at(a: Int, b: Int) = g.gains[(a * g.ph + b) * g.mapPlanes + mp]
                        val gain = (at(v0, h0) * (1 - fh) + at(v0, h1) * fh) * (1 - fv) + (at(v1, h0) * (1 - fh) + at(v1, h1) * fh) * fv
                        val i = (y * w + x) * spp + p
                        raw[i] = ((raw[i].toInt() and 0xFFFF) * gain).roundToInt().coerceIn(0, 65535).toShort()
                    }
                    x += g.colPitch
                }
                y += g.rowPitch
            }
        }
        // Crop to the picture area (keeping the colour pattern's phase).
        val cw = m.cropW; val ch = m.cropH
        val out = if (cw == w && ch == h && m.cropTop == 0 && m.cropLeft == 0) raw else {
            val o = ShortArray(cw * ch * spp)
            for (y in 0 until ch) System.arraycopy(raw, ((y + m.cropTop) * w + m.cropLeft) * spp, o, y * cw * spp, cw * spp)
            o
        }
        val pat = m.cfa?.let { p -> IntArray(4) { k -> val yy = (k shr 1) + m.cropTop; val xx = (k and 1) + m.cropLeft; p[((yy and 1) shl 1) or (xx and 1)] } }
        Sensor(cw, ch, spp, out, pat)
    }

    // ------------------------------------------------------------------ lossless JPEG (ITU T.81, process 14)

    private class LJpeg(val d: ByteArray) {
        private var p = 0
        private var bitBuf = 0L; private var bitCnt = 0
        private class Huff(val lookup: IntArray, val maxCode: IntArray, val valPtr: IntArray, val minCode: IntArray, val vals: IntArray)
        private val tables = arrayOfNulls<Huff>(4)

        private fun u8() = d[p++].toInt() and 0xFF
        private fun u16(): Int { val a = u8(); return (a shl 8) or u8() }

        fun decode(): IntArray {
            if (u16() != 0xFFD8) throw UnsupportedRaw("Bad compressed RAW data")
            var w = 0; var h = 0; var comps = 0; var prec = 16
            var compIds = IntArray(0)
            var restart = 0
            while (p < d.size - 1) {
                var mk = u8(); if (mk != 0xFF) continue
                mk = u8(); while (mk == 0xFF) mk = u8()
                when (mk) {
                    0xC3 -> { val len = u16(); val end = p + len - 2; prec = u8(); h = u16(); w = u16(); comps = u8(); compIds = IntArray(comps) { val id = u8(); u8(); u8(); id }; p = end }
                    0xC4 -> {
                        val len = u16(); val end = p + len - 2
                        while (p < end) {
                            val tc = u8(); val counts = IntArray(17); var tot = 0
                            for (i in 1..16) { counts[i] = u8(); tot += counts[i] }
                            val vals = IntArray(tot) { u8() }
                            tables[tc and 3] = build(counts, vals)
                        }
                        p = end
                    }
                    0xDD -> { u16(); restart = u16() }
                    0xDA -> {
                        val len = u16(); val end = p + len - 2
                        val ns = u8(); val tsel = IntArray(ns)
                        for (i in 0 until ns) { val cid = u8(); val t = u8(); val idx = compIds.indexOf(cid).coerceAtLeast(i); if (idx < ns) tsel[idx] = t shr 4 }
                        val pred = u8(); u8(); val pt = u8() and 0xF
                        p = end
                        return scan(w, h, ns, tsel, pred, pt, prec, restart)
                    }
                    0xD9 -> break
                    else -> { val len = u16(); p += len - 2 }
                }
            }
            throw UnsupportedRaw("Bad compressed RAW data")
        }

        private fun build(counts: IntArray, vals: IntArray): Huff {
            val maxCode = IntArray(18) { -1 }; val minCode = IntArray(17); val valPtr = IntArray(17)
            var code = 0; var k = 0
            for (l in 1..16) {
                valPtr[l] = k; minCode[l] = code
                code += counts[l]; k += counts[l]
                maxCode[l] = if (counts[l] > 0) code - 1 else -1
                code = code shl 1
            }
            maxCode[17] = Int.MAX_VALUE
            return Huff(IntArray(0), maxCode, valPtr, minCode, vals)
        }

        private fun fill() {
            while (bitCnt <= 48) {
                var b = 0
                if (p < d.size) {
                    b = d[p].toInt() and 0xFF
                    if (b == 0xFF) {
                        val nx = if (p + 1 < d.size) d[p + 1].toInt() and 0xFF else 0
                        if (nx == 0x00) { p += 2 } else { b = 0 /* marker: feed zeros */ }
                    } else p++
                }
                bitBuf = (bitBuf shl 8) or b.toLong(); bitCnt += 8
            }
        }
        private fun bits(n: Int): Int {
            if (n == 0) return 0
            if (bitCnt < n) fill()
            val v = ((bitBuf ushr (bitCnt - n)) and ((1L shl n) - 1)).toInt()
            bitCnt -= n
            return v
        }
        private fun huff(t: Huff): Int {
            var code = bits(1); var l = 1
            while (l < 16 && code > t.maxCode[l]) { code = (code shl 1) or bits(1); l++ }
            if (code > t.maxCode[l]) return 0
            return t.vals[(t.valPtr[l] + code - t.minCode[l]).coerceIn(0, t.vals.size - 1)]
        }
        private fun diff(t: Huff): Int {
            val s = huff(t)
            if (s == 0) return 0
            if (s == 16) return 32768
            val v = bits(s)
            return if (v < (1 shl (s - 1))) v - (1 shl s) + 1 else v
        }
        private fun resync() {
            bitBuf = 0; bitCnt = 0
            while (p < d.size - 1) { if ((d[p].toInt() and 0xFF) == 0xFF && (d[p + 1].toInt() and 0xFF) in 0xD0..0xD7) { p += 2; return }; p++ }
        }

        private fun scan(w: Int, h: Int, nc: Int, tsel: IntArray, pred: Int, pt: Int, prec: Int, restart: Int): IntArray {
            val out = IntArray(w * h * nc)
            val rowW = w * nc
            val init = 1 shl (prec - pt - 1)
            val mask = (1 shl prec) - 1
            val ts = Array(nc) { tables[tsel[it]] ?: tables[0] ?: throw UnsupportedRaw("Bad compressed RAW data") }
            var mcu = 0
            var startRow = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (restart > 0 && mcu > 0 && mcu % restart == 0) { resync(); startRow = y }
                    for (c in 0 until nc) {
                        val i = y * rowW + x * nc + c
                        val pr = when {
                            y == startRow && x == 0 -> init
                            y == startRow -> out[i - nc]
                            x == 0 -> out[i - rowW]
                            else -> {
                                val ra = out[i - nc]; val rb = out[i - rowW]; val rc = out[i - rowW - nc]
                                when (pred) { 1 -> ra; 2 -> rb; 3 -> rc; 4 -> ra + rb - rc; 5 -> ra + ((rb - rc) shr 1); 6 -> rb + ((ra - rc) shr 1); 7 -> (ra + rb) shr 1; else -> ra }
                            }
                        }
                        out[i] = (pr + diff(ts[c])) and mask
                    }
                    mcu++
                }
            }
            if (pt > 0) for (i in out.indices) out[i] = out[i] shl pt
            return out
        }
    }

    // ------------------------------------------------------------------ colour

    private val XYZ_RGB = doubleArrayOf( // linear sRGB (D65) → XYZ
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041,
    )

    private fun mul3(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { k -> val r = k / 3; val c = k % 3; a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c] }
    private fun inv3(m: DoubleArray): DoubleArray? {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (kotlin.math.abs(det) < 1e-12) return null
        return doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det, (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det, (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det)
    }

    /** White-balanced camera RGB → linear sRGB (rows of camera←sRGB normalised so white stays white). */
    private fun rgbCam(m: Meta): DoubleArray {
        val cm = m.colorMatrix ?: return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val camRgb = mul3(cm.copyOf(9), XYZ_RGB)
        for (r in 0 until 3) { val s = camRgb[r * 3] + camRgb[r * 3 + 1] + camRgb[r * 3 + 2]; if (s > 1e-9) for (c in 0 until 3) camRgb[r * 3 + c] /= s }
        return inv3(camRgb) ?: doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    }

    /** CIE xy of a colour temperature (Planckian below 4000 K, daylight above). */
    private fun cctXy(t: Double): Pair<Double, Double> {
        val x = if (t < 4000) {
            -0.2661239e9 / (t * t * t) - 0.2343589e6 / (t * t) + 0.8776956e3 / t + 0.179910
        } else if (t <= 7000) {
            -4.6070e9 / (t * t * t) + 2.9678e6 / (t * t) + 0.09911e3 / t + 0.244063
        } else {
            -2.0064e9 / (t * t * t) + 1.9018e6 / (t * t) + 0.24748e3 / t + 0.237040
        }
        val y = if (t < 4000) -1.1063814 * x * x * x - 1.34811020 * x * x + 2.18555832 * x - 0.20219683
        else -3.0 * x * x + 2.87 * x - 0.275
        return x to y
    }

    /** Camera channel multipliers (green = 1) for the white balance choice and the Temperature / Tint sliders. */
    private fun wbMul(m: Meta, dev: Dev, temp: Float, tint: Float): FloatArray {
        val base: DoubleArray = when {
            dev.wb == 1 -> m.autoMul?.let { a -> doubleArrayOf(a[0].toDouble(), 1.0, a[2].toDouble()) } ?: m.asShot
            dev.wb >= 2 && m.colorMatrix != null -> {
                val (x, y) = cctXy(WB_PRESETS[dev.wb].second.toDouble())
                val xyz = doubleArrayOf(x / y, 1.0, (1 - x - y) / y)
                val cm = m.colorMatrix
                DoubleArray(3) { r -> cm[r * 3] * xyz[0] + cm[r * 3 + 1] * xyz[1] + cm[r * 3 + 2] * xyz[2] }.takeIf { n -> n.all { it > 1e-6 } } ?: m.asShot
            }
            else -> m.asShot
        }
        // Neutral → multipliers, green = 1.
        var r = (base[1] / base[0]).toFloat(); var g = 1f; var b = (base[1] / base[2]).toFloat()
        r *= 2f.pow(temp * 0.6f); b *= 2f.pow(-temp * 0.6f); g *= 2f.pow(-tint * 0.4f)
        return floatArrayOf(r, g, b)
    }

    // ------------------------------------------------------------------ develop

    /** Linear camera RGB (no white balance), upright, at most [maxSide] on the long side — for the live preview. */
    class Linear(val w: Int, val h: Int, val rgb: FloatArray)

    /** Decodes and makes the preview-size linear picture (half-size superpixels, then averaged down). */
    fun preview(context: Context, m: Meta, maxSide: Int): Linear {
        val s = decode(context, m)
        val half = superpixel(s)
        val down = boxDown(half, maxSide)
        // Grey-world auto white balance (ignoring near-black and clipped pixels).
        var sr = 0.0; var sg = 0.0; var sb = 0.0
        val px = down.rgb
        var i = 0
        while (i < px.size) {
            val r = px[i]; val g = px[i + 1]; val b = px[i + 2]
            val mx = max(r, max(g, b))
            if (mx in 0.02f..0.95f) { sr += r; sg += g; sb += b }
            i += 3
        }
        if (sr > 0 && sb > 0) m.autoMul = floatArrayOf((sr / sg).toFloat(), 1f, (sb / sg).toFloat())
        return orient(down, m.orientation)
    }

    private fun superpixel(s: Sensor): Linear {
        if (s.cfa == null) { // already RGB
            val f = FloatArray(s.w * s.h * 3) { s.v(it) }
            return Linear(s.w, s.h, f)
        }
        val w = s.w / 2; val h = s.h / 2
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val o = (y * w + x) * 3
            var g = 0f
            for (dy in 0..1) for (dx in 0..1) {
                val xx = x * 2 + dx; val yy = y * 2 + dy
                val v = s.v(yy * s.w + xx)
                when (s.color(xx, yy)) { 0 -> out[o] = v; 1 -> g += v * 0.5f; else -> out[o + 2] = v }
            }
            out[o + 1] = g
        }
        return Linear(w, h, out)
    }

    private fun boxDown(src: Linear, maxSide: Int): Linear {
        val k = max(src.w, src.h).toFloat() / maxSide
        if (k <= 1f) return src
        val w = (src.w / k).toInt().coerceAtLeast(1); val h = (src.h / k).toInt().coerceAtLeast(1)
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) {
            val y0 = (y * k).toInt(); val y1 = min(src.h, ((y + 1) * k).toInt().coerceAtLeast(y0 + 1))
            for (x in 0 until w) {
                val x0 = (x * k).toInt(); val x1 = min(src.w, ((x + 1) * k).toInt().coerceAtLeast(x0 + 1))
                var r = 0f; var g = 0f; var b = 0f; var n = 0
                for (yy in y0 until y1) for (xx in x0 until x1) { val i = (yy * src.w + xx) * 3; r += src.rgb[i]; g += src.rgb[i + 1]; b += src.rgb[i + 2]; n++ }
                val o = (y * w + x) * 3
                out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n
            }
        }
        return Linear(w, h, out)
    }

    /** Smaller copy of a preview (for the fast live preview while dragging). */
    fun shrink(src: Linear, maxSide: Int): Linear = boxDown(src, maxSide)

    private fun orient(src: Linear, o: Int): Linear {
        if (o <= 1 || o > 8) return src
        val sw = src.w; val sh = src.h
        val swap = o >= 5
        val w = if (swap) sh else sw; val h = if (swap) sw else sh
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            // Map output (x, y) back to the stored picture.
            val (sx, sy) = when (o) {
                2 -> (sw - 1 - x) to y
                3 -> (sw - 1 - x) to (sh - 1 - y)
                4 -> x to (sh - 1 - y)
                5 -> y to x
                6 -> y to (sh - 1 - x)
                7 -> (sw - 1 - y) to (sh - 1 - x)
                8 -> (sw - 1 - y) to x
                else -> x to y
            }
            val si = (sy * sw + sx) * 3; val di = (y * w + x) * 3
            out[di] = src.rgb[si]; out[di + 1] = src.rgb[si + 1]; out[di + 2] = src.rgb[si + 2]
        }
        return Linear(w, h, out)
    }

    /** Pre-computed develop: white balance, colour matrix, exposure and tone. */
    private class Pipe(m: Meta, dev: Dev, exposure: Float, highlights: Float, shadows: Float, whites: Float, blacks: Float, temp: Float, tint: Float) {
        val mul = wbMul(m, dev, temp, tint)
        val clip = min(mul[0], min(mul[1], mul[2]))
        val mtx = rgbCam(m).map { it.toFloat() }.toFloatArray()
        val k = 2f.pow(exposure + m.baselineExposure)
        val prof = PROFILES[dev.profile.coerceIn(0, PROFILES.size - 1)]
        val sh = shadows; val hi = highlights; val wh = whites; val bl = blacks
        // Display LUT: linear 0..1 → sRGB → profile contrast → 8-bit.
        val disp = IntArray(DISP + 1) { i ->
            val lv = i / DISP.toFloat()
            var v = if (lv <= 0.0031308f) lv * 12.92f else 1.055f * lv.pow(1f / 2.4f) - 0.055f
            val s = v * v * (3f - 2f * v)
            v += (s - v) * prof.contrast
            (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }

        fun tone(y: Float): Float {
            if (y <= 0f) return 0f
            var v = y
            var l = ln(v) / LN2
            if (sh != 0f) { v *= 2f.pow(sh * 1.6f * (1f - smooth(-5f, -1f, l))); l = ln(v) / LN2 }
            if (hi != 0f) { v *= 2f.pow(hi * 1.6f * smooth(-2.5f, 1f, l)); l = ln(v) / LN2 }
            if (wh != 0f) v *= 2f.pow(wh * 0.9f * smooth(-1.5f, 1.5f, l))
            if (bl != 0f) { val d = bl * 0.015f; v = if (d < 0f) ((v + d) / (1f + d)).coerceAtLeast(0f) else d + v * (1f - d) }
            // Soft highlight roll-off: everything above the knee is compressed, never hard-clipped.
            val kn = prof.knee
            return if (v <= kn) v else kn + (1f - kn) * (1f - exp(-(v - kn) / (1f - kn)))
        }

        /** Camera RGB (no WB) → ARGB. */
        fun px(r0: Float, g0: Float, b0: Float): Int {
            val cr = min(r0 * mul[0], clip); val cg = min(g0 * mul[1], clip); val cb = min(b0 * mul[2], clip)
            var r = (mtx[0] * cr + mtx[1] * cg + mtx[2] * cb) * k
            var g = (mtx[3] * cr + mtx[4] * cg + mtx[5] * cb) * k
            var b = (mtx[6] * cr + mtx[7] * cg + mtx[8] * cb) * k
            r = max(r, 0f); g = max(g, 0f); b = max(b, 0f)
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (y <= 1e-7f) return 0xFF000000.toInt()
            val y2 = tone(y)
            val gn = y2 / y
            if (prof.mono) { val d = disp[(y2.coerceIn(0f, 1f) * DISP).toInt()]; return (0xFF shl 24) or (d shl 16) or (d shl 8) or d }
            r = y2 + (r * gn - y2) * prof.sat; g = y2 + (g * gn - y2) * prof.sat; b = y2 + (b * gn - y2) * prof.sat
            val mx = max(r, max(g, b))
            if (mx > 1f && y2 < 1f) { val t = (1f - y2) / (mx - y2); r = y2 + (r - y2) * t; g = y2 + (g - y2) * t; b = y2 + (b - y2) * t }
            val ri = disp[(r.coerceIn(0f, 1f) * DISP).toInt()]
            val gi = disp[(g.coerceIn(0f, 1f) * DISP).toInt()]
            val bi = disp[(b.coerceIn(0f, 1f) * DISP).toInt()]
            return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
    private const val DISP = 16383
    private const val LN2 = 0.6931472f
    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    /** Develops a preview with the current settings (fast enough for live sliders). */
    fun develop(src: Linear, m: Meta, dev: Dev, exposure: Float, highlights: Float, shadows: Float, whites: Float, blacks: Float, temp: Float, tint: Float): Bitmap {
        val p = Pipe(m, dev, exposure, highlights, shadows, whites, blacks, temp, tint)
        val n = src.w * src.h
        val out = IntArray(n)
        val f = src.rgb
        for (i in 0 until n) out[i] = p.px(f[i * 3], f[i * 3 + 1], f[i * 3 + 2])
        return Bitmap.createBitmap(out, src.w, src.h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Full-quality develop for export: decodes the DNG again, demosaics (full size when the export
     * needs it, else half-size superpixels), develops, turns upright and fits [maxSide].
     */
    fun export(context: Context, m: Meta, dev: Dev, exposure: Float, highlights: Float, shadows: Float, whites: Float, blacks: Float, temp: Float, tint: Float, maxSide: Int): Bitmap {
        val s = decode(context, m)
        if (dev.wb == 1 && m.autoMul == null) { preview(context, m, 512) } // fills autoMul
        val p = Pipe(m, dev, exposure, highlights, shadows, whites, blacks, temp, tint)
        val full = max(s.w, s.h)
        val bmp: Bitmap = if (s.cfa == null || maxSide * 1.0f <= full / 2f) {
            val half = superpixel(s)
            val out = IntArray(half.w * half.h)
            for (i in out.indices) out[i] = p.px(half.rgb[i * 3], half.rgb[i * 3 + 1], half.rgb[i * 3 + 2])
            Bitmap.createBitmap(out, half.w, half.h, Bitmap.Config.ARGB_8888)
        } else {
            val w = s.w; val h = s.h
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val row = IntArray(w)
            val rgb = FloatArray(3)
            for (y in 0 until h) {
                for (x in 0 until w) { mhc(s, x, y, rgb); row[x] = p.px(rgb[0], rgb[1], rgb[2]) }
                b.setPixels(row, 0, w, 0, y, w, 1)
            }
            b
        }
        // Upright, then fit the export size.
        val mx = Matrix()
        when (m.orientation) {
            2 -> mx.postScale(-1f, 1f); 3 -> mx.postRotate(180f); 4 -> mx.postScale(1f, -1f)
            5 -> { mx.postRotate(90f); mx.postScale(-1f, 1f) }; 6 -> mx.postRotate(90f)
            7 -> { mx.postRotate(270f); mx.postScale(-1f, 1f) }; 8 -> mx.postRotate(270f)
        }
        val long = max(bmp.width, bmp.height)
        if (long > maxSide) { val k = maxSide.toFloat() / long; mx.postScale(k, k) }
        if (mx.isIdentity) return bmp
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    /** Malvar-He-Cutler demosaic of one pixel (gradient-corrected bilinear), camera RGB into [o]. */
    private fun mhc(s: Sensor, x: Int, y: Int, o: FloatArray) {
        val w = s.w; val h = s.h
        fun ix(v: Int, n: Int) = if (v < 0) -v else if (v >= n) 2 * n - 2 - v else v
        fun at(dx: Int, dy: Int) = s.v(ix(y + dy, h) * w + ix(x + dx, w))
        val c = s.color(x, y)
        val ctr = at(0, 0)
        if (c == 1) {
            val g = ctr
            val diag = at(1, 1) + at(-1, 1) + at(1, -1) + at(-1, -1)
            val rowColor = s.color(ix(x + 1, w), y)
            val horiz = (5f * g + 4f * (at(1, 0) + at(-1, 0)) - diag - (at(2, 0) + at(-2, 0)) + 0.5f * (at(0, 2) + at(0, -2))) / 8f
            val vert = (5f * g + 4f * (at(0, 1) + at(0, -1)) - diag - (at(0, 2) + at(0, -2)) + 0.5f * (at(2, 0) + at(-2, 0))) / 8f
            o[1] = g
            if (rowColor == 0) { o[0] = max(horiz, 0f); o[2] = max(vert, 0f) } else { o[2] = max(horiz, 0f); o[0] = max(vert, 0f) }
        } else {
            val cross2 = at(2, 0) + at(-2, 0) + at(0, 2) + at(0, -2)
            val g = (2f * (at(1, 0) + at(-1, 0) + at(0, 1) + at(0, -1)) + 4f * ctr - cross2) / 8f
            val other = (6f * ctr + 2f * (at(1, 1) + at(-1, 1) + at(1, -1) + at(-1, -1)) - 1.5f * cross2) / 8f
            o[1] = max(g, 0f)
            if (c == 0) { o[0] = ctr; o[2] = max(other, 0f) } else { o[2] = ctr; o[0] = max(other, 0f) }
        }
    }

}
