package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.GradeIO
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Ways to combine several photos into one. */
enum class MergeKind(val label: String, val hint: String) {
    HDR("HDR merge", "2–5 bracketed shots of the same scene (e.g. −2 / 0 / +2 EV)"),
    PANORAMA("Panorama", "2–8 overlapping shots, picked left to right"),
    FOCUS("Focus stack", "2–8 shots focused at different distances"),
}

/** HDR (exposure fusion), panorama stitching and focus stacking, done on the phone. */
object Merge {
    fun run(context: Context, kind: MergeKind, uris: List<Uri>, onProgress: (String) -> Unit): Bitmap? {
        if (uris.size < 2) return null
        if (kind == MergeKind.PANORAMA) {
            onProgress("Loading photos…")
            val frames = uris.mapNotNull { decodeAny(context, it, 2400)?.let { b -> com.dronedirector.veyrocutpro.camera.Panorama.prepareFrame(b, 0) } }
            if (frames.size < 2) return null
            onProgress("Stitching…")
            // Field of view from the first photo's lens info (35 mm equivalent), else a typical phone lens.
            val f35 = try {
                context.contentResolver.openInputStream(uris[0])?.use { androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) } ?: 0
            } catch (_: Throwable) { 0 }
            val fov = if (f35 in 8..400) Math.toDegrees(2 * kotlin.math.atan(36.0 / (2 * f35))).toFloat() else 65f
            val o = PanoOpts
            val edges = o.warp > 0f || o.fill
            val raw = com.dronedirector.veyrocutpro.camera.Panorama.stitch(frames, null, fov, 1, Filter.NONE, keepEdges = edges) ?: return null
            val f = (kotlin.math.max(frames[0].width, frames[0].height) / (2.0 * kotlin.math.tan(Math.toRadians(fov.toDouble()) / 2))).toFloat()
            if (o.projection == 0 && !edges) return raw
            onProgress("Finishing the panorama…")
            return PanoOpts.finish(raw, f)
        }
        onProgress("Loading photos…")
        val maxPx = 2200
        val imgs = uris.take(if (kind == MergeKind.FOCUS) 6 else 5).mapNotNull { decodeAny(context, it, maxPx) }
        if (imgs.size < 2) return null
        val w = imgs.minOf { it.width }; val h = imgs.minOf { it.height }
        val px = imgs.map { b -> IntArray(w * h).also { (if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)).getPixels(it, 0, w, 0, 0, w, h) } }
        imgs.forEach { it.recycle() }
        // Line every shot up with the middle one (hand-held shots move a little).
        val ref = px.size / 2
        onProgress("Aligning…")
        val aligned = px.mapIndexed { i, p -> if (i == ref) p else shift(p, w, h, align(px[ref], p, w, h)) }
        onProgress(if (kind == MergeKind.HDR) "Merging exposures…" else "Merging sharp areas…")
        val out = if (kind == MergeKind.HDR) fuse(aligned, w, h) else stack(aligned, w, h)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun luma(c: Int) = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) shr 8

    /** Translation (dx, dy) that best matches [b] onto [a], found coarse-to-fine on edge maps. */
    private fun align(a: IntArray, b: IntArray, w: Int, h: Int): Pair<Int, Int> {
        var dx = 0; var dy = 0
        for (scale in listOf(8, 2, 1)) {
            val sw = w / scale; val sh = h / scale
            fun edges(p: IntArray): FloatArray {
                val l = FloatArray(sw * sh) { i -> luma(p[(i / sw) * scale * w + (i % sw) * scale]).toFloat() }
                // Normalise brightness so different exposures compare.
                val mean = l.average().toFloat().coerceAtLeast(1f)
                return FloatArray(sw * sh) { i -> val x = i % sw; if (x == 0) 0f else (l[i] - l[i - 1]) / mean }
            }
            val ea = edges(a); val eb = edges(b)
            val range = if (scale == 8) 12 else 3
            val cx = dx / scale; val cy = dy / scale
            var best = Float.MAX_VALUE; var bx = cx; var by = cy
            val step = if (scale == 1) 3 else 2
            for (oy in cy - range..cy + range) for (ox in cx - range..cx + range) {
                var err = 0f; var n = 0
                var y = sh / 8
                while (y < sh * 7 / 8) {
                    val yy = y + oy
                    if (yy in 0 until sh) {
                        var x = sw / 8
                        while (x < sw * 7 / 8) {
                            val xx = x + ox
                            if (xx in 0 until sw) { err += abs(ea[y * sw + x] - eb[yy * sw + xx]); n++ }
                            x += step
                        }
                    }
                    y += step
                }
                if (n > 0 && err / n < best) { best = err / n; bx = ox; by = oy }
            }
            dx = bx * scale; dy = by * scale
        }
        return dx to dy
    }

    private fun shift(p: IntArray, w: Int, h: Int, d: Pair<Int, Int>): IntArray {
        val (dx, dy) = d
        if (dx == 0 && dy == 0) return p
        return IntArray(w * h) { i ->
            val x = (i % w + dx).coerceIn(0, w - 1); val y = (i / w + dy).coerceIn(0, h - 1)
            p[y * w + x]
        }
    }

    /** Box blur (separable, a few passes ≈ gaussian) of a weight map. */
    private fun blur(m: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        var a = m.copyOf(); val t = FloatArray(m.size)
        repeat(2) {
            for (y in 0 until h) {
                var acc = 0f
                for (x in -r..r) acc += a[y * w + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    t[y * w + x] = acc / (2 * r + 1)
                    acc += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)]
                }
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += t[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    a[y * w + x] = acc / (2 * r + 1)
                    acc += t[(y + r + 1).coerceAtMost(h - 1) * w + x] - t[(y - r).coerceAtLeast(0) * w + x]
                }
            }
        }
        return a
    }

    /** Exposure fusion: each pixel comes mostly from the shot where it is well exposed, saturated and detailed. */
    private fun fuse(imgs: List<IntArray>, w: Int, h: Int): IntArray {
        val r = max(4, min(w, h) / 60)
        val weights = imgs.map { p ->
            val wt = FloatArray(w * h)
            for (i in p.indices) {
                val c = p[i]
                val rr = ((c shr 16) and 0xFF) / 255f; val gg = ((c shr 8) and 0xFF) / 255f; val bb = (c and 0xFF) / 255f
                val mean = (rr + gg + bb) / 3f
                val sat = kotlin.math.sqrt(((rr - mean) * (rr - mean) + (gg - mean) * (gg - mean) + (bb - mean) * (bb - mean)) / 3f)
                fun we(v: Float) = exp(-((v - 0.5f) * (v - 0.5f)) / (2 * 0.2f * 0.2f))
                val x = i % w
                val lap = if (x in 1 until w - 1 && i >= w && i < p.size - w)
                    abs(4 * luma(c) - luma(p[i - 1]) - luma(p[i + 1]) - luma(p[i - w]) - luma(p[i + w])) / 255f else 0f
                wt[i] = (lap + 0.05f) * (sat + 0.05f) * we(rr) * we(gg) * we(bb) + 1e-6f
            }
            blur(wt, w, h, r)
        }
        val out = IntArray(w * h)
        for (i in out.indices) {
            var sw = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            for (k in imgs.indices) {
                val wv = weights[k][i]; val c = imgs[k][i]
                sw += wv; sr += ((c shr 16) and 0xFF) * wv; sg += ((c shr 8) and 0xFF) * wv; sb += (c and 0xFF) * wv
            }
            out[i] = (0xFF shl 24) or ((sr / sw).toInt().coerceIn(0, 255) shl 16) or ((sg / sw).toInt().coerceIn(0, 255) shl 8) or (sb / sw).toInt().coerceIn(0, 255)
        }
        return out
    }

    /** Focus stacking: each area is taken from the shot where it is sharpest. */
    private fun stack(imgs: List<IntArray>, w: Int, h: Int): IntArray {
        val r = max(3, min(w, h) / 150)
        val weights = imgs.map { p ->
            val s = FloatArray(w * h)
            for (i in w until p.size - w) {
                val x = i % w
                if (x == 0 || x == w - 1) continue
                val l = abs(4 * luma(p[i]) - luma(p[i - 1]) - luma(p[i + 1]) - luma(p[i - w]) - luma(p[i + w])).toFloat()
                s[i] = l * l
            }
            blur(s, w, h, r)
        }
        val out = IntArray(w * h)
        for (i in out.indices) {
            // Sharpness-weighted, strongly favouring the sharpest shot.
            var sw = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            var mx = 0f
            for (k in imgs.indices) mx = max(mx, weights[k][i])
            for (k in imgs.indices) {
                val v = weights[k][i] / (mx + 1e-3f)
                val wv = v * v * v * v + 1e-4f
                val c = imgs[k][i]
                sw += wv; sr += ((c shr 16) and 0xFF) * wv; sg += ((c shr 8) and 0xFF) * wv; sb += (c and 0xFF) * wv
            }
            out[i] = (0xFF shl 24) or ((sr / sw).toInt().coerceIn(0, 255) shl 16) or ((sg / sw).toInt().coerceIn(0, 255) shl 8) or (sb / sw).toInt().coerceIn(0, 255)
        }
        return out
    }

    @Suppress("unused")
    private fun draw(b: Bitmap) = Canvas(b).drawColor(0, android.graphics.PorterDuff.Mode.CLEAR).also { Paint() }
}

/** A photo for merging; DNGs (e.g. drone AEB brackets) are developed from the RAW data, not their small preview. */
private fun decodeAny(context: android.content.Context, uri: android.net.Uri, maxPx: Int): android.graphics.Bitmap? =
    try {
        if (RawDng.isDng(context, uri)) RawDng.export(context, RawDng.open(context, uri), RawDng.Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f, maxPx)
        else GradeIO.decode(context, uri, maxPx)
    } catch (_: RawDng.UnsupportedRaw) { GradeIO.decode(context, uri, maxPx) } catch (_: OutOfMemoryError) { GradeIO.decode(context, uri, maxPx) }

/** Panorama options: projection, boundary warp and AI fill of the empty edges. */
object PanoOpts {
    val PROJECTIONS = listOf("Cylindrical", "Spherical", "Perspective")
    var projection = 0
    var warp = 0f
    var fill = false

    private fun empty(c: Int) = (c ushr 24) < 128

    /** Applies the projection, the boundary warp, then fills (AI) or trims whatever is still empty. */
    fun finish(src: Bitmap, f: Float): Bitmap {
        var b = project(src, f)
        if (warp > 0f) b = boundaryWarp(b, warp)
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        if (px.none { empty(it) }) return b
        if (fill) {
            val mask = Bitmap.createBitmap(IntArray(w * h) { if (empty(px[it])) -1 else 0xFF000000.toInt() }, w, h, Bitmap.Config.ARGB_8888)
            for (i in px.indices) if (empty(px[i])) px[i] = 0xFF000000.toInt()
            val solid = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            return AiSegment.fill(solid, mask)
        }
        return trim(px, w, h) ?: b
    }

    private fun project(src: Bitmap, f: Float): Bitmap {
        if (projection == 0) return src
        val w = src.width; val h = src.height
        val sp = IntArray(w * h); src.getPixels(sp, 0, w, 0, 0, w, h)
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
        val ow: Int; val oh: Int
        if (projection == 1) { ow = w; oh = (2 * f * kotlin.math.atan((h / 2f) / f)).toInt().coerceAtLeast(2) }
        else {
            val half = (w / 2f) / f
            // Straight lines only work below ~140° across; wider stays cylindrical.
            if (half > 1.2f) return src
            ow = (2 * f * kotlin.math.tan(half)).toInt().coerceIn(2, w * 4); oh = (h / kotlin.math.cos(half)).toInt().coerceIn(2, h * 3)
        }
        val ocx = (ow - 1) / 2f; val ocy = (oh - 1) / 2f
        val out = IntArray(ow * oh)
        for (y in 0 until oh) for (x in 0 until ow) {
            val xs: Float; val ys: Float
            if (projection == 1) { xs = x.toFloat(); ys = cy + f * kotlin.math.tan((y - ocy) / f) }
            else { val th = kotlin.math.atan((x - ocx) / f); xs = cx + f * th; ys = cy + (y - ocy) * kotlin.math.cos(th) }
            if (xs < 0f || ys < 0f || xs > w - 1f || ys > h - 1f) continue
            out[y * ow + x] = sp[ys.toInt() * w + xs.toInt()]
        }
        return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
    }

    /** Stretches each column so the picture reaches the top and bottom edges ([amount] 0..1). */
    private fun boundaryWarp(src: Bitmap, amount: Float): Bitmap {
        val w = src.width; val h = src.height
        val sp = IntArray(w * h); src.getPixels(sp, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (x in 0 until w) {
            var t = 0; while (t < h && empty(sp[t * w + x])) t++
            var b = h - 1; while (b > t && empty(sp[b * w + x])) b--
            if (b - t < 4) continue
            val tt = t * (1f - amount); val bb = b + (h - 1 - b) * amount
            for (y in 0 until h) {
                if (y < tt || y > bb) continue
                val ys = t + (y - tt) * (b - t) / (bb - tt).coerceAtLeast(1f)
                out[y * w + x] = sp[ys.toInt().coerceIn(0, h - 1) * w + x]
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Biggest band without empty corners (like the normal stitch). */
    private fun trim(px: IntArray, w: Int, h: Int): Bitmap? {
        val tops = IntArray(w); val bottoms = IntArray(w)
        for (x in 0 until w) {
            var y = 0; while (y < h && empty(px[y * w + x])) y++; tops[x] = y
            y = h - 1; while (y >= 0 && empty(px[y * w + x])) y--; bottoms[x] = y
        }
        val cov = (0 until w).filter { bottoms[it] - tops[it] > h / 3 }
        if (cov.isEmpty()) return null
        val ts = cov.map { tops[it] }.sorted(); val bs = cov.map { bottoms[it] }.sorted()
        val top = ts[(ts.size * 0.97).toInt().coerceAtMost(ts.size - 1)]; val bottom = bs[(bs.size * 0.03).toInt()]
        var l = cov.first(); var r = cov.last()
        while (l < r && (tops[l] > top + 2 || bottoms[l] < bottom - 2)) l++
        while (r > l && (tops[r] > top + 2 || bottoms[r] < bottom - 2)) r--
        val cw = r - l + 1; val ch = bottom - top + 1
        if (cw < 32 || ch < 32) return null
        val b = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        b.setPixels(px, top * w + l, w, 0, 0, cw, ch)
        return b
    }
}
