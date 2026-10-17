package com.dronedirector.acluxpro.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Kinds of local-adjustment masks. */
enum class MaskType(val label: String) { LINEAR("Linear gradient"), RADIAL("Radial"), BRUSH("Brush"), SUBJECT("Subject (AI)"), SKY("Sky (AI)"), COLOR_RANGE("Colour range"), LUMA_RANGE("Brightness range") }

/** One brush stroke: points (x, y pairs, 0..1 of the picture), size (share of the width), add or erase. */
data class BrushStroke(val points: List<Float>, val size: Float, val erase: Boolean = false, val soft: Float = 0.5f)

/**
 * A local adjustment: where (gradient, ellipse, brush strokes or an AI selection) and what
 * (its own light / colour settings applied on top of the global edit). Coordinates are 0..1
 * of the edited (cropped) picture.
 */
data class PhotoMask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: MaskType = MaskType.RADIAL,
    // Linear: full effect at (x0, y0), fading out to nothing at (x1, y1).
    val x0: Float = 0.5f, val y0: Float = 0.1f, val x1: Float = 0.5f, val y1: Float = 0.5f,
    // Radial: centre, radii (share of width / height) and feather.
    val cx: Float = 0.5f, val cy: Float = 0.5f, val rx: Float = 0.25f, val ry: Float = 0.25f,
    val feather: Float = 0.5f,
    val invert: Boolean = false,
    val strokes: List<BrushStroke> = emptyList(),
    /** AI masks: path of the saved selection (grey PNG) in the app's files. */
    val maskFile: String? = null,
    val grade: ProGrade = ProGrade(),
    val amount: Float = 1f,
    val visible: Boolean = true,
    // Colour range: hue (degrees) ± half of hueWidth; brightness range: lumLow..lumHigh (0..1).
    val hue: Float = 210f,
    val hueWidth: Float = 40f,
    val lumLow: Float = 0.7f,
    val lumHigh: Float = 1f,
    /** Softness of the range edges, 0..1. */
    val rangeSoft: Float = 0.4f,
)

/** A healed spot: centre and radius (share of the width) and where the patch is taken from (offset, same units). */
data class Spot(val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float, val clone: Boolean = false)

/** What to do with everything except the subject (needs an AI subject selection). */
data class PhotoBackground(val mode: Int = 0, val blur: Float = 0.5f, val color: Int = 0xFFFFFFFF.toInt(), val maskFile: String? = null) {
    /** 0 off, 1 blur (portrait), 2 remove (transparent), 3 colour. */
    val active get() = mode != 0 && maskFile != null
}

/** Sky replacement: the AI sky selection filled with a built-in sky or your own picture. */
data class SkySwap(val maskFile: String? = null, val preset: Int = 0, val imagePath: String? = null, val amount: Float = 1f, val warm: Float = 0.3f) {
    val active get() = maskFile != null && amount > 0f
}

object Skies {
    val names = listOf("Clear blue", "Golden hour", "Sunset", "Dramatic", "Night")

    /** A built-in sky as a w×h picture (top to horizon). */
    fun preset(i: Int, w: Int, h: Int): IntArray {
        val stops: List<Pair<Float, Int>> = when (i) {
            1 -> listOf(0f to 0xFF5A7FC2.toInt(), 0.6f to 0xFFE9B784.toInt(), 1f to 0xFFF8D49A.toInt())
            2 -> listOf(0f to 0xFF2E2463.toInt(), 0.5f to 0xFFC4506A.toInt(), 0.8f to 0xFFF27B4E.toInt(), 1f to 0xFFFFC77D.toInt())
            3 -> listOf(0f to 0xFF2B3038.toInt(), 0.55f to 0xFF59616B.toInt(), 1f to 0xFFA8B0B8.toInt())
            4 -> listOf(0f to 0xFF03081A.toInt(), 0.7f to 0xFF0F2347.toInt(), 1f to 0xFF27426E.toInt())
            else -> listOf(0f to 0xFF1F5FC9.toInt(), 0.65f to 0xFF6FA6E8.toInt(), 1f to 0xFFBFDCF7.toInt())
        }
        val out = IntArray(w * h)
        val rnd = java.util.Random(7L + i)
        for (y in 0 until h) {
            val t = y.toFloat() / (h - 1).coerceAtLeast(1)
            var k = 0
            while (k < stops.size - 2 && t > stops[k + 1].first) k++
            val (t0, c0) = stops[k]; val (t1, c1) = stops[k + 1]
            val f = ((t - t0) / (t1 - t0).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
            for (x in 0 until w) {
                // A little noise so the gradient doesn't band.
                val n = (rnd.nextFloat() - 0.5f) * 3f
                fun ch(s: Int) = (((c0 shr s) and 0xFF) * (1 - f) + ((c1 shr s) and 0xFF) * f + n).toInt().coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        if (i == 4) repeat(w * h / 2500) { val p = rnd.nextInt(w * (h * 2 / 3).coerceAtLeast(1)); out[p] = 0xFFFFFFFF.toInt() }
        return out
    }

    /** Puts the sky into [px] where the sky mask is; the rest gets a touch of the sky's warmth. */
    fun apply(px: IntArray, w: Int, h: Int, wt: FloatArray, sky: IntArray, s: SkySwap) {
        var sr = 0L; var sg = 0L; var sb = 0L
        for (c in sky) { sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
        val n = sky.size.coerceAtLeast(1)
        val tr = sr / n / 255f; val tg = sg / n / 255f; val tb = sb / n / 255f
        val avg = (tr + tg + tb) / 3f
        for (i in px.indices) {
            val a = (wt[i] * s.amount).coerceIn(0f, 1f)
            val o = px[i]; val k = sky[i]
            var r = ((o shr 16) and 0xFF).toFloat(); var g = ((o shr 8) and 0xFF).toFloat(); var b = (o and 0xFF).toFloat()
            // Match the foreground's colour to the new light, gently.
            val m = s.warm * (1f - a)
            r *= 1f + m * (tr - avg); g *= 1f + m * (tg - avg); b *= 1f + m * (tb - avg)
            r = r * (1 - a) + ((k shr 16) and 0xFF) * a
            g = g * (1 - a) + ((k shr 8) and 0xFF) * a
            b = b * (1 - a) + (k and 0xFF) * a
            px[i] = (o and 0xFF000000.toInt()) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }
}

/** Lens corrections. */
data class LensFix(
    /** + removes barrel distortion (wide lenses), − removes pincushion. */
    val distortion: Float = 0f,
    /** Brightens dark corners. */
    val vignette: Float = 0f,
    /** Lines up the red and blue channels (colour fringes at the edges). */
    val fringe: Float = 0f,
) {
    val isIdentity get() = this == LensFix()
}

object PhotoLocal {

    // ------------------------------------------------------------------ lens

    fun lens(src: Bitmap, l: LensFix): Bitmap {
        if (l.isIdentity) return src
        var b = src
        if (l.distortion != 0f) b = distort(b, l.distortion)
        if (l.vignette != 0f || l.fringe != 0f) {
            val w = b.width; val h = b.height
            val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
            val out = IntArray(w * h)
            val cx = w / 2f; val cy = h / 2f
            val maxR2 = cx * cx + cy * cy
            val kr = 1f + l.fringe * 0.004f; val kb = 1f - l.fringe * 0.004f
            val vig = l.vignette * 0.7f
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                var r: Int; var g: Int; var bl: Int
                val c = px[i]
                g = (c shr 8) and 0xFF
                if (l.fringe != 0f) {
                    r = (sample(px, w, h, cx + (x - cx) * kr, cy + (y - cy) * kr) shr 16) and 0xFF
                    bl = sample(px, w, h, cx + (x - cx) * kb, cy + (y - cy) * kb) and 0xFF
                } else { r = (c shr 16) and 0xFF; bl = c and 0xFF }
                if (vig != 0f) {
                    val d2 = ((x - cx) * (x - cx) + (y - cy) * (y - cy)) / maxR2
                    val k = 1f + vig * d2 * d2 * 1.6f
                    r = (r * k).toInt().coerceIn(0, 255); g = (g * k).toInt().coerceIn(0, 255); bl = (bl * k).toInt().coerceIn(0, 255)
                }
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            val nb = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
            if (b !== src) b.recycle()
            b = nb
        }
        return b
    }

    /** Bilinear sample (clamped). */
    private fun sample(px: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        val x = fx.coerceIn(0f, w - 1.001f); val y = fy.coerceIn(0f, h - 1.001f)
        val x0 = x.toInt(); val y0 = y.toInt(); val tx = x - x0; val ty = y - y0
        val a = px[y0 * w + x0]; val b = px[y0 * w + x0 + 1]; val c = px[(y0 + 1) * w + x0]; val d = px[(y0 + 1) * w + x0 + 1]
        fun ch(s: Int): Int {
            val va = (a shr s) and 0xFF; val vb = (b shr s) and 0xFF; val vc = (c shr s) and 0xFF; val vd = (d shr s) and 0xFF
            return ((va * (1 - tx) + vb * tx) * (1 - ty) + (vc * (1 - tx) + vd * tx) * ty).toInt()
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Radial distortion fix with a warped mesh, zoomed just enough to leave no empty edges. */
    private fun distort(src: Bitmap, amount: Float): Bitmap {
        val w = src.width; val h = src.height
        val k = -amount * 0.18f
        val n = 40
        val verts = FloatArray((n + 1) * (n + 1) * 2)
        val cx = w / 2f; val cy = h / 2f
        val norm = sqrt(cx * cx + cy * cy)
        // Corner position after the warp decides the zoom that hides empty edges.
        val corner = 1f + k
        val edge = 1f + k * (min(cx, cy) / norm).let { it * it }
        val zoom = 1f / min(1f, min(corner, edge)).coerceAtLeast(0.5f)
        var i = 0
        for (gy in 0..n) for (gx in 0..n) {
            val x = gx * w.toFloat() / n; val y = gy * h.toFloat() / n
            val dx = (x - cx) / norm; val dy = (y - cy) / norm
            val f = (1f + k * (dx * dx + dy * dy)) * zoom
            verts[i++] = cx + (x - cx) * f; verts[i++] = cy + (y - cy) * f
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmapMesh(src, n, n, verts, 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    // ------------------------------------------------------------------ perspective

    /** Keystone fix: [v] > 0 straightens converging verticals (buildings shot from below), [hz] the same sideways. */
    fun perspective(src: Bitmap, v: Float, hz: Float): Bitmap {
        if (v == 0f && hz == 0f) return src
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val iv = abs(v) * 0.22f * w; val ih = abs(hz) * 0.22f * h
        // Source trapezoid that becomes the full rectangle.
        var tl = floatArrayOf(0f, 0f); var tr = floatArrayOf(w, 0f); var br = floatArrayOf(w, h); var bl = floatArrayOf(0f, h)
        if (v > 0) { tl = floatArrayOf(tl[0] + iv, tl[1]); tr = floatArrayOf(tr[0] - iv, tr[1]) }
        if (v < 0) { bl = floatArrayOf(bl[0] + iv, bl[1]); br = floatArrayOf(br[0] - iv, br[1]) }
        if (hz > 0) { tl = floatArrayOf(tl[0], tl[1] + ih); bl = floatArrayOf(bl[0], bl[1] - ih) }
        if (hz < 0) { tr = floatArrayOf(tr[0], tr[1] + ih); br = floatArrayOf(br[0], br[1] - ih) }
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]), 0, floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, 4)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    // ------------------------------------------------------------------ healing

    /** Picks where to copy a patch from for a spot at (x, y) radius r (all 0..1 of the width). */
    fun findSource(src: Bitmap, x: Float, y: Float, r: Float): Pair<Float, Float> {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val R = r * w
        val cx = x * w; val cy = y * h
        var best = Float.MAX_VALUE; var bdx = 2.4f * r; var bdy = 0f
        for (dist in listOf(2.2f, 3.0f)) for (k in 0 until 16) {
            val a = k * Math.PI / 8
            val ox = (cos(a) * R * dist).toFloat(); val oy = (sin(a) * R * dist).toFloat()
            if (cx + ox - R < 0 || cx + ox + R >= w || cy + oy - R < 0 || cy + oy + R >= h) continue
            // Compare the ring just outside the spot with the ring around the candidate.
            var err = 0f; var cnt = 0
            for (j in 0 until 24) {
                val b = j * Math.PI / 12
                for (rr in listOf(1.15f, 1.4f)) {
                    val sx = cx + (cos(b) * R * rr).toFloat(); val sy = cy + (sin(b) * R * rr).toFloat()
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || sx + ox < 0 || sy + oy < 0 || sx + ox >= w || sy + oy >= h) continue
                    val c1 = px[sy.toInt() * w + sx.toInt()]; val c2 = px[(sy + oy).toInt() * w + (sx + ox).toInt()]
                    err += abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)) + abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)) + abs((c1 and 0xFF) - (c2 and 0xFF))
                    cnt++
                }
            }
            if (cnt > 0 && err / cnt < best) { best = err / cnt; bdx = ox / w; bdy = oy / w }
        }
        return bdx to bdy
    }

    /** Copies a feathered, colour-matched patch over every spot. */
    fun heal(src: Bitmap, spots: List<Spot>): Bitmap {
        if (spots.isEmpty()) return src
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        for (s in spots) {
            val R = s.r * w
            val cx = s.x * w; val cy = s.y * h
            val ox = s.dx * w; val oy = s.dy * w
            // Average colour difference on the border, so the patch blends in.
            var dr = 0f; var dg = 0f; var db = 0f; var n = 0
            for (j in 0 until 32) {
                val b = j * Math.PI / 16
                val sx = (cx + cos(b) * R * 1.1).toInt(); val sy = (cy + sin(b) * R * 1.1).toInt()
                val tx = (sx + ox).toInt(); val ty = (sy + oy).toInt()
                if (sx !in 0 until w || sy !in 0 until h || tx !in 0 until w || ty !in 0 until h) continue
                val c1 = px[sy * w + sx]; val c2 = px[ty * w + tx]
                dr += ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF); dg += ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF); db += (c1 and 0xFF) - (c2 and 0xFF); n++
            }
            if (n > 0) { dr /= n; dg /= n; db /= n }
            // Clone stamp: an exact copy (no colour matching), with a firmer edge.
            if (s.clone) { dr = 0f; dg = 0f; db = 0f }
            val edge = if (s.clone) 0.75f else 0.55f
            val x0 = max(0, (cx - R).toInt()); val x1 = min(w - 1, (cx + R).toInt())
            val y0 = max(0, (cy - R).toInt()); val y1 = min(h - 1, (cy + R).toInt())
            for (y in y0..y1) for (x in x0..x1) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / R
                if (d >= 1f) continue
                val t = 1f - ((d - edge) / (1f - edge)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                val sxp = (x + ox).toInt(); val syp = (y + oy).toInt()
                if (sxp !in 0 until w || syp !in 0 until h) continue
                val c = px[syp * w + sxp]; val o = px[y * w + x]
                val r = ((((c shr 16) and 0xFF) + dr) * t + ((o shr 16) and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
                val g = ((((c shr 8) and 0xFF) + dg) * t + ((o shr 8) and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
                val bl = (((c and 0xFF) + db) * t + (o and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
                px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ masks

    /** Mask strength 0..1 for every pixel of a w×h picture. */
    fun weights(m: PhotoMask, w: Int, h: Int, aiMask: Bitmap? = null, px: IntArray? = null): FloatArray {
        val out = FloatArray(w * h)
        when (m.type) {
            MaskType.LINEAR -> {
                val ax = m.x0 * w; val ay = m.y0 * h; val bx = m.x1 * w; val by = m.y1 * h
                val dx = bx - ax; val dy = by - ay
                val len2 = (dx * dx + dy * dy).coerceAtLeast(1f)
                for (y in 0 until h) for (x in 0 until w) {
                    val t = (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
                    out[y * w + x] = 1f - t * t * (3 - 2 * t)
                }
            }
            MaskType.RADIAL -> {
                val cx = m.cx * w; val cy = m.cy * h; val rx = (m.rx * w).coerceAtLeast(1f); val ry = (m.ry * h).coerceAtLeast(1f)
                val inner = (1f - m.feather.coerceIn(0.02f, 1f))
                for (y in 0 until h) for (x in 0 until w) {
                    val dx = (x - cx) / rx; val dy = (y - cy) / ry
                    val d = sqrt(dx * dx + dy * dy)
                    val t = ((d - inner) / (1f - inner).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    out[y * w + x] = 1f - t * t * (3 - 2 * t)
                }
            }
            MaskType.BRUSH -> {
                val scale = min(1f, 1024f / max(w, h))
                val mw = (w * scale).toInt().coerceAtLeast(1); val mh = (h * scale).toInt().coerceAtLeast(1)
                val mb = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
                val c = Canvas(mb)
                for (s in m.strokes) {
                    val rad = s.size * mw / 2f
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        strokeWidth = rad * 2
                        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                        style = Paint.Style.STROKE
                        if (s.soft > 0f) maskFilter = BlurMaskFilter((rad * s.soft).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                        if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    }
                    val pts = s.points
                    if (pts.size == 2) {
                        p.style = Paint.Style.FILL
                        c.drawCircle(pts[0] * mw, pts[1] * mh, rad, p)
                    } else {
                        val path = android.graphics.Path()
                        path.moveTo(pts[0] * mw, pts[1] * mh)
                        var i = 2
                        while (i + 1 < pts.size) { path.lineTo(pts[i] * mw, pts[i + 1] * mh); i += 2 }
                        c.drawPath(path, p)
                    }
                }
                fillFrom(mb, out, w, h)
                mb.recycle()
            }
            MaskType.SUBJECT, MaskType.SKY -> if (aiMask != null) fillFrom(aiMask, out, w, h)
            MaskType.COLOR_RANGE, MaskType.LUMA_RANGE -> if (px != null && px.size >= w * h) rangeWeights(m, px, out)
        }
        if (m.invert) for (i in out.indices) out[i] = 1f - out[i]
        if (m.amount != 1f) for (i in out.indices) out[i] *= m.amount
        return out
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        if (e1 <= e0) return if (x >= e1) 1f else 0f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Colour-range / brightness-range selection from the pixels themselves. */
    private fun rangeWeights(m: PhotoMask, px: IntArray, out: FloatArray) {
        val hsv = FloatArray(3)
        for (i in out.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (m.type == MaskType.COLOR_RANGE) {
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                var d = abs(hsv[0] - m.hue); if (d > 180f) d = 360f - d
                val inner = m.hueWidth / 2f
                val soft = 4f + m.rangeSoft * 60f
                // Grey pixels have no real colour, so they stay out.
                out[i] = (1f - smooth(inner, inner + soft, d)) * smooth(0.04f, 0.18f, hsv[1]) * smooth(0.03f, 0.12f, hsv[2])
            } else {
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val soft = 0.01f + m.rangeSoft * 0.3f
                out[i] = smooth(m.lumLow - soft, m.lumLow, l) * (1f - smooth(m.lumHigh, m.lumHigh + soft, l))
            }
        }
    }

    /** Reads a mask picture's brightness (resized to w×h) into [out]. */
    private fun fillFrom(mb: Bitmap, out: FloatArray, w: Int, h: Int) {
        val s = if (mb.width == w && mb.height == h) mb else Bitmap.createScaledBitmap(mb, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            val l = ((c shr 16) and 0xFF).coerceAtLeast((c shr 8) and 0xFF)
            out[i] = (if (mb.hasAlpha() && a < 255) a else l) / 255f
        }
        if (s !== mb) s.recycle()
    }

    /** Portrait blur, transparent or coloured background outside the subject ([wt] = subject 0..1). */
    fun background(px: IntArray, w: Int, h: Int, wt: FloatArray, bg: PhotoBackground) {
        when (bg.mode) {
            1 -> {
                // Blur at a quarter size (fast, and a wide soft blur looks like a real lens).
                val sw = max(1, w / 4); val sh = max(1, h / 4)
                val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                val small = Bitmap.createScaledBitmap(full, sw, sh, true)
                full.recycle()
                val sp = IntArray(sw * sh); small.getPixels(sp, 0, sw, 0, 0, sw, sh); small.recycle()
                val r = (bg.blur * max(sw, sh) * 0.03f).toInt().coerceIn(1, 60)
                val blurred = boxBlur(sp, sw, sh, r)
                val bb = Bitmap.createScaledBitmap(Bitmap.createBitmap(blurred, sw, sh, Bitmap.Config.ARGB_8888), w, h, true)
                val bp = IntArray(w * h); bb.getPixels(bp, 0, w, 0, 0, w, h); bb.recycle()
                for (i in px.indices) px[i] = mixPx(bp[i], px[i], wt[i])
            }
            2 -> for (i in px.indices) px[i] = ((wt[i] * 255).toInt().coerceIn(0, 255) shl 24) or (px[i] and 0x00FFFFFF)
            3 -> for (i in px.indices) px[i] = mixPx(bg.color, px[i], wt[i])
        }
    }

    private fun mixPx(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = (((a shr 16) and 0xFF) * (1 - k) + ((b shr 16) and 0xFF) * k).toInt()
        val g = (((a shr 8) and 0xFF) * (1 - k) + ((b shr 8) and 0xFF) * k).toInt()
        val bl = ((a and 0xFF) * (1 - k) + (b and 0xFF) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        var a = src.copyOf(); val t = IntArray(src.size)
        repeat(3) {
            for (y in 0 until h) {
                var sr = 0; var sg = 0; var sb = 0
                for (x in -r..r) { val c = a[y * w + x.coerceIn(0, w - 1)]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
                for (x in 0 until w) {
                    val n = 2 * r + 1
                    t[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                    val add = a[y * w + (x + r + 1).coerceAtMost(w - 1)]; val rem = a[y * w + (x - r).coerceAtLeast(0)]
                    sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF); sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF); sb += (add and 0xFF) - (rem and 0xFF)
                }
            }
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (y in -r..r) { val c = t[y.coerceIn(0, h - 1) * w + x]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
                for (y in 0 until h) {
                    val n = 2 * r + 1
                    a[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                    val add = t[(y + r + 1).coerceAtMost(h - 1) * w + x]; val rem = t[(y - r).coerceAtLeast(0) * w + x]
                    sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF); sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF); sb += (add and 0xFF) - (rem and 0xFF)
                }
            }
        }
        return a
    }

    /** Applies every visible mask's own grade where the mask is. */
    fun applyMasks(px: IntArray, w: Int, h: Int, masks: List<PhotoMask>, aiMasks: Map<String, Bitmap>) {
        // Range masks look at the picture before any mask changed it.
        val orig = if (masks.any { it.type == MaskType.COLOR_RANGE || it.type == MaskType.LUMA_RANGE }) px.copyOf() else null
        for (m in masks) {
            if (!m.visible || m.grade.isIdentity) continue
            val wt = weights(m, w, h, m.maskFile?.let { aiMasks[it] }, orig)
            val t = m.grade.tables()
            val one = IntArray(1)
            for (i in px.indices) {
                val a = wt[i]
                if (a < 0.003f) continue
                one[0] = px[i]
                ImageOps.applyLut(one, t)
                val o = px[i]; val g = one[0]
                val r = (((o shr 16) and 0xFF) * (1 - a) + ((g shr 16) and 0xFF) * a).toInt()
                val gg = (((o shr 8) and 0xFF) * (1 - a) + ((g shr 8) and 0xFF) * a).toInt()
                val b = ((o and 0xFF) * (1 - a) + (g and 0xFF) * a).toInt()
                px[i] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
            }
        }
    }
}
