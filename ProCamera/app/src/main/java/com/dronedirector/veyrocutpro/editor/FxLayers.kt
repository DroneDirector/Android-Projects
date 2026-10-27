package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay layers drawn by the app (Layers → ADD LAYER): cinema bars, a flight path mini-map from the
 * drone's flight data, a progress bar and a lens flare. Drawn the same in the preview and the export. Own code.
 */
object FxLayers {
    const val BARS = 0
    const val MAP = 1
    const val PROGRESS = 2
    const val FLARE = 3

    val BAR_SHAPES = listOf("2.39:1" to 2.39f, "2.35:1" to 2.35f, "2:1" to 2f, "1.85:1" to 1.85f, "2.76:1" to 2.76f)
    val FLARE_STYLES = listOf("Warm sun", "Anamorphic blue", "Soft glow")
    private const val STEP_MS = 40L

    fun label(l: Layer): String = when (l.fx) { BARS -> "Cinema bars"; MAP -> "Flight path"; PROGRESS -> "Progress bar"; else -> "Lens flare" }

    /** Full-frame overlays sit still in the middle (they're not dragged; change them in the layer's settings). */
    fun fullFrame(l: Layer) = l.kind == LayerKind.FX && l.fx != MAP && l.fx != PROGRESS

    /** Which picture to draw at [ms] (a new number = a new picture); 0 for overlays that don't change. */
    fun frameIndex(l: Layer, ms: Long): Int = when (l.fx) {
        MAP, PROGRESS -> ((ms - l.startMs).coerceAtLeast(0) / STEP_MS).toInt()
        BARS -> if (l.fxB == 1f) {
            val inMs = ms - l.startMs; val outMs = l.endMs - ms
            when { inMs in 0 until 1000 -> 1 + (inMs / STEP_MS).toInt(); outMs in 0 until 1000 -> 1000 + (outMs / STEP_MS).toInt(); else -> 0 }
        } else 0
        else -> 0
    }

    private fun msOf(l: Layer, n: Int): Long = when (l.fx) {
        BARS -> if (n == 0) -1L else if (n < 1000) l.startMs + (n - 1) * STEP_MS else l.endMs - (n - 1000) * STEP_MS
        else -> l.startMs + n * STEP_MS
    }

    fun renderFrame(l: Layer, frameW: Int, frameH: Int, n: Int): Bitmap? = render(l, frameW, frameH, msOf(l, n))

    /** The overlay at timeline time [ms] (−1 = at rest), for a [frameW] × [frameH] picture. */
    fun render(l: Layer, frameW: Int, frameH: Int, ms: Long): Bitmap? = try {
        when (l.fx) {
            BARS -> bars(l, frameW, frameH, ms)
            MAP -> map(l, frameH, ms)
            PROGRESS -> progress(l, frameW, frameH, ms)
            else -> flare(l, frameW, frameH)
        }
    } catch (_: Throwable) { null }

    private fun bars(l: Layer, w: Int, h: Int, ms: Long): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val target = l.fxA ?: 2.39f
        var bar = ((h - w / target) / 2f).coerceAtLeast(0f)
        if (l.fxB == 1f && ms >= 0) {
            // Slide in over the first second, out over the last.
            val k = min(((ms - l.startMs) / 1000f).coerceIn(0f, 1f), ((l.endMs - ms) / 1000f).coerceIn(0f, 1f))
            bar *= k * k * (3 - 2 * k)
        }
        if (bar >= 0.5f) {
            val p = Paint().apply { color = l.color or 0xFF000000.toInt() }
            val c = Canvas(out)
            c.drawRect(0f, 0f, w.toFloat(), bar, p)
            c.drawRect(0f, h - bar, w.toFloat(), h.toFloat(), p)
        }
        return out
    }

    private fun progress(l: Layer, w: Int, h: Int, ms: Long): Bitmap {
        val th = ((l.fxA ?: 0.008f) * h).coerceAtLeast(2f)
        val out = Bitmap.createBitmap(w, th.toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawRect(0f, 0f, w.toFloat(), out.height.toFloat(), Paint().apply { color = 0x40FFFFFF })
        val len = (l.endMs - l.startMs).coerceAtLeast(1)
        val k = if (ms < 0) 0.35f else ((ms - l.startMs).toFloat() / len).coerceIn(0f, 1f)
        c.drawRect(0f, 0f, w * k, out.height.toFloat(), Paint().apply { color = l.color or 0xFF000000.toInt() })
        return out
    }

    /** Where the drone is at [ms]: index into the track and the share between two points. */
    private fun posAt(t: List<Double>, ms: Long): Pair<Double, Double>? {
        val n = t.size / 3
        if (n == 0) return null
        if (ms <= t[0]) return t[1] to t[2]
        for (i in 1 until n) {
            if (ms <= t[i * 3]) {
                val a = t[(i - 1) * 3]; val b = t[i * 3]
                val k = if (b > a) (ms - a) / (b - a) else 0.0
                return (t[(i - 1) * 3 + 1] + (t[i * 3 + 1] - t[(i - 1) * 3 + 1]) * k) to (t[(i - 1) * 3 + 2] + (t[i * 3 + 2] - t[(i - 1) * 3 + 2]) * k)
            }
        }
        return t[(n - 1) * 3 + 1] to t[(n - 1) * 3 + 2]
    }

    private fun map(l: Layer, h: Int, ms: Long): Bitmap {
        val side = max(48, (l.size * h).toInt())
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = side * 0.12f
        c.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        val t = l.track.orEmpty()
        val n = t.size / 3
        if (n < 2) return out
        // Flat projection: longitude shrinks with latitude; north up; the track fitted into the box.
        var la0 = Double.MAX_VALUE; var la1 = -Double.MAX_VALUE; var lo0 = Double.MAX_VALUE; var lo1 = -Double.MAX_VALUE
        for (i in 0 until n) { la0 = min(la0, t[i * 3 + 1]); la1 = max(la1, t[i * 3 + 1]); lo0 = min(lo0, t[i * 3 + 2]); lo1 = max(lo1, t[i * 3 + 2]) }
        val kx = cos(Math.toRadians((la0 + la1) / 2))
        val spanX = (lo1 - lo0) * kx; val spanY = la1 - la0
        val span = max(max(spanX, spanY), 1e-6)
        val pad = side * 0.14f; val box = side - pad * 2
        fun px(la: Double, lo: Double): Pair<Float, Float> {
            val x = pad + (((lo - lo0) * kx + (span - spanX) / 2) / span * box).toFloat()
            val y = pad + ((1 - ((la - la0) + (span - spanY) / 2) / span) * box).toFloat()
            return x to y
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = side * 0.022f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val all = Path(); val done = Path()
        val now = if (ms < 0) t[(n - 1) * 3] else ms.toDouble()
        for (i in 0 until n) {
            val (x, y) = px(t[i * 3 + 1], t[i * 3 + 2])
            if (i == 0) all.moveTo(x, y) else all.lineTo(x, y)
            if (t[i * 3] <= now) { if (i == 0) done.moveTo(x, y) else done.lineTo(x, y) }
        }
        val cur = posAt(t, now.toLong())
        cur?.let { (la, lo) -> val (x, y) = px(la, lo); done.lineTo(x, y) }
        line.color = 0x66FFFFFF; c.drawPath(all, line)
        line.color = l.color or 0xFF000000.toInt(); c.drawPath(done, line)
        cur?.let { (la, lo) ->
            val (x, y) = px(la, lo)
            c.drawCircle(x, y, side * 0.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            c.drawCircle(x, y, side * 0.032f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.color or 0xFF000000.toInt() })
        }
        // Start point.
        val (sx, sy) = px(t[1], t[2])
        c.drawCircle(sx, sy, side * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        // North arrow.
        val na = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = side * 0.09f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        c.drawText("N", side - pad * 0.6f, pad * 0.85f, na)
        return out
    }

    private fun flare(l: Layer, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cx = (l.fxX ?: 0.72f) * w; val cy = (l.fxY ?: 0.28f) * h
        val s = min(w, h).toFloat()
        val style = (l.fxA ?: 0f).toInt()
        val core = when (style) { 1 -> 0xFFCFE4FF.toInt(); 2 -> 0xFFFFF4E0.toInt(); else -> 0xFFFFF0C8.toInt() }
        val glow = when (style) { 1 -> 0x6650A0FF; 2 -> 0x55FFE6C0; else -> 0x77FFA040 }
        fun radial(x: Float, y: Float, r: Float, inner: Int, outer: Int = 0) {
            c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, inner, outer, Shader.TileMode.CLAMP) })
        }
        // Wide glow, bright core.
        radial(cx, cy, s * (if (style == 2) 0.75f else 0.55f), glow)
        radial(cx, cy, s * 0.09f, core)
        radial(cx, cy, s * 0.03f, 0xFFFFFFFF.toInt())
        // Streak across: long and blue for anamorphic, short and warm otherwise.
        if (style != 2) {
            val len = if (style == 1) w * 0.9f else w * 0.35f
            val th = s * (if (style == 1) 0.012f else 0.008f)
            val sc = if (style == 1) 0xCC6FB0FF.toInt() else 0x99FFD8A0.toInt()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(cx - len, cy, cx + len, cy, intArrayOf(0, sc, 0xFFFFFFFF.toInt(), sc, 0), floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f), Shader.TileMode.CLAMP) }
            c.drawOval(RectF(cx - len, cy - th, cx + len, cy + th), p)
        }
        // Ghosts along the line through the middle of the picture.
        val gx = w / 2f - cx; val gy = h / 2f - cy
        val ghosts = listOf(0.5f to 0.035f, 0.85f to 0.02f, 1.25f to 0.06f, 1.6f to 0.028f, 2.0f to 0.09f)
        for ((i, g) in ghosts.withIndex()) {
            val x = cx + gx * g.first * 2f; val y = cy + gy * g.first * 2f
            val col = when (style) {
                1 -> if (i % 2 == 0) 0x3360A0FF else 0x2890FFE0
                2 -> 0x22FFFFFF
                else -> if (i % 2 == 0) 0x33FFB060 else 0x2860FF90
            }
            radial(x, y, s * g.second, col, col and 0x00FFFFFF)
            if (style == 0 && i == 4) c.drawCircle(x, y, s * g.second, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = s * 0.004f; color = 0x30FFC890 })
        }
        return out
    }
}
