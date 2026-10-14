package com.dronedirector.acluxpro.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sweep panorama: frames are taken while the user turns, then aligned to each other by
 * matching brightness profiles and blended with soft seams. Works without a gyroscope.
 */
object Panorama {

    const val MAX_FRAMES = 14
    /** Take the next frame once the view has moved this share of its width. */
    const val STEP = 0.30f
    private const val FRAME_HEIGHT = 1280

    /** Rotates a captured frame upright and scales it to the working height. */
    fun prepareFrame(src: Bitmap, rotationDegrees: Int): Bitmap {
        val upright = if (rotationDegrees != 0) {
            val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
        } else src
        if (upright.height <= FRAME_HEIGHT) return upright
        val w = (upright.width * FRAME_HEIGHT.toFloat() / upright.height).roundToInt()
        return Bitmap.createScaledBitmap(upright, w, FRAME_HEIGHT, true).also { if (it !== upright) upright.recycle() }
    }

    /** [direction] > 0 means the user turned right (frames run left → right). */
    fun stitch(frames: List<Bitmap>, direction: Int, filter: Filter): Bitmap? {
        if (frames.isEmpty()) return null
        val ordered = if (direction < 0) frames.reversed() else frames
        val w = ordered[0].width
        val h = ordered[0].height

        // Align at small scale.
        val sw = 256
        val sh = max(16, h * sw / w)
        val grays = ordered.map { gray(it, sw, sh) }
        val offsets = ArrayList<IntArray>()
        offsets += intArrayOf(0, 0)
        var cx = 0
        var cy = 0
        for (i in 1 until ordered.size) {
            val (dx, dy) = register(grays[i - 1], grays[i], sw, sh)
            cx += (dx * w.toFloat() / sw).roundToInt()
            cy += (dy * h.toFloat() / sh).roundToInt()
            offsets += intArrayOf(cx, cy)
        }
        val minY = offsets.minOf { it[1] }
        val maxY = offsets.maxOf { it[1] }
        val outW = cx + w
        val outH = h + (maxY - minY)

        val pano = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pano)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(ordered[0], 0f, (offsets[0][1] - minY).toFloat(), paint)

        val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        for (i in 1 until ordered.size) {
            val overlap = offsets[i - 1][0] + w - offsets[i][0]
            val tile = ordered[i].copy(Bitmap.Config.ARGB_8888, true)
            if (overlap > 4) {
                // Blend across the middle of the overlap so seams are soft.
                val feather = min(overlap.toFloat(), w * 0.22f)
                val start = (overlap - feather) / 2f
                fade.shader = LinearGradient(start, 0f, start + feather, 0f, 0x00000000, -0x1000000, Shader.TileMode.CLAMP)
                Canvas(tile).drawRect(0f, 0f, start + feather, h.toFloat(), fade)
            }
            canvas.drawBitmap(tile, offsets[i][0].toFloat(), (offsets[i][1] - minY).toFloat(), paint)
            tile.recycle()
        }

        // Keep only rows every frame covers, so there are no black wedges.
        val top = maxY - minY
        val height = h - (maxY - minY)
        var result = if (top > 0 && height > 32) Bitmap.createBitmap(pano, 0, top, outW, height) else pano
        if (result !== pano) pano.recycle()

        if (filter != Filter.NONE) {
            val f = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            Canvas(f).drawBitmap(result, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(filter.matrix) })
            result.recycle()
            result = f
        }
        return result
    }

    private fun gray(b: Bitmap, sw: Int, sh: Int): FloatArray {
        val small = Bitmap.createScaledBitmap(b, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== b) small.recycle()
        return FloatArray(px.size) {
            val c = px[it]
            0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
    }

    /** Returns how far frame b sits to the right of / below frame a, in small-scale pixels. */
    private fun register(a: FloatArray, b: FloatArray, w: Int, h: Int): Pair<Int, Int> {
        val y0 = (h * 0.15f).toInt()
        val y1 = (h * 0.85f).toInt()
        val colA = FloatArray(w) { x -> var s = 0f; for (y in y0 until y1) s += a[y * w + x]; s / (y1 - y0) }
        val colB = FloatArray(w) { x -> var s = 0f; for (y in y0 until y1) s += b[y * w + x]; s / (y1 - y0) }
        // Gradients are insensitive to small brightness changes between frames.
        val gA = FloatArray(w) { if (it == 0) 0f else colA[it] - colA[it - 1] }
        val gB = FloatArray(w) { if (it == 0) 0f else colB[it] - colB[it - 1] }

        val expected = (w * STEP).toInt()
        val minDx = (w * 0.06f).toInt()
        val maxDx = (w * 0.75f).toInt()
        var bestDx = expected
        var bestErr = Float.MAX_VALUE
        for (dx in minDx..maxDx) {
            val n = w - dx
            var err = 0f
            for (x in 1 until n) err += abs(gA[x + dx] - gB[x])
            err /= n
            // Mild preference for the expected step when the scene is featureless.
            err += 0.002f * abs(dx - expected)
            if (err < bestErr) { bestErr = err; bestDx = dx }
        }

        // Vertical drift inside the overlap.
        val n = w - bestDx
        val rowA = FloatArray(h) { y -> var s = 0f; for (x in bestDx until w) s += a[y * w + x]; s / n }
        val rowB = FloatArray(h) { y -> var s = 0f; for (x in 0 until n) s += b[y * w + x]; s / n }
        val maxDy = (h * 0.08f).toInt()
        var bestDy = 0
        var bestDyErr = Float.MAX_VALUE
        for (dy in -maxDy..maxDy) {
            var err = 0f
            var cnt = 0
            for (y in 1 until h) {
                val ya = y + dy
                if (ya < 1 || ya >= h) continue
                err += abs((rowA[ya] - rowA[ya - 1]) - (rowB[y] - rowB[y - 1]))
                cnt++
            }
            if (cnt > h / 2) {
                err /= cnt
                if (err < bestDyErr) { bestDyErr = err; bestDy = dy }
            }
        }
        return bestDx to bestDy
    }
}
