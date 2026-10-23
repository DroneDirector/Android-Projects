package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Automatic RAW check: every sample DNG in the folder given by -DrawSamples is opened and developed
 * with the app's own RAW code, then compared with the preview the camera / drone stored in the
 * file: picture shape and orientation, overall brightness, colour cast and dark corners. Writes a
 * report (and side-by-side pictures) to -DrawReport. Fails only when a file can't be developed or
 * comes out the wrong shape; the rest is listed as warnings to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RawSamplesTest {
    private class Stats(val lum: Double, val rg: Double, val bg: Double, val vig: Double)

    private fun stats(b: Bitmap): Stats {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var sl = 0.0; var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
        val cx = w / 2.0; val cy = h / 2.0; val m = sqrt(cx * cx + cy * cy)
        val centre = ArrayList<Double>(); val outer = ArrayList<Double>()
        val step = maxOf(1, (w * h) / 200_000)
        var i = 0
        while (i < px.size) {
            val c = px[i]
            val r = ((c shr 16) and 255) / 255.0; val g = ((c shr 8) and 255) / 255.0; val bl = (c and 255) / 255.0
            val l = 0.2126 * r + 0.7152 * g + 0.0722 * bl
            sl += l; sr += r; sg += g; sb += bl; n++
            val x = i % w; val y = i / w
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / m
            if (d < 0.25) centre += l else if (d > 0.82) outer += l
            i += step
        }
        fun med(a: ArrayList<Double>) = if (a.isEmpty()) 0.0 else a.sorted()[a.size / 2]
        val c = med(centre).coerceAtLeast(1e-3); val o = med(outer).coerceAtLeast(1e-3)
        return Stats(sl / n, sr / sg.coerceAtLeast(1e-6), sb / sg.coerceAtLeast(1e-6), c / o)
    }

    private fun sideBySide(a: Bitmap, b: Bitmap, out: File) {
        val h = 360
        val wa = a.width * h / a.height; val wb = b.width * h / b.height
        val bmp = Bitmap.createBitmap(wa + wb + 8, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); val p = Paint(Paint.FILTER_BITMAP_FLAG)
        cv.drawColor(android.graphics.Color.BLACK)
        cv.drawBitmap(Bitmap.createScaledBitmap(a, wa, h, true), 0f, 0f, p)
        cv.drawBitmap(Bitmap.createScaledBitmap(b, wb, h, true), (wa + 8).toFloat(), 0f, p)
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    private fun group(make: String, path: String): String {
        val mk = make.lowercase().ifBlank { path.substringBefore('/').lowercase() }
        return when {
            mk.startsWith("google") -> "Pixel"
            mk.startsWith("samsung") -> "Galaxy"
            mk.startsWith("apple") -> "iPhone"
            mk.startsWith("huawei") -> "Huawei"
            mk.startsWith("xiaomi") -> "Xiaomi"
            mk.startsWith("oneplus") -> "OnePlus"
            mk.startsWith("lg") -> "LG"
            mk.startsWith("motorola") || mk.startsWith("moto") -> "Motorola"
            mk.startsWith("dji") || path.startsWith("DJI/") || path.startsWith("Hasselblad/L") -> "DJI"
            else -> mk.substringBefore(' ').replaceFirstChar { it.uppercase() }
        }
    }

    @Test
    fun developEverySample() {
        val dir = System.getProperty("rawSamples").orEmpty()
        Assume.assumeTrue("No samples folder (set RAW_SAMPLES)", dir.isNotBlank() && File(dir).isDirectory)
        val reportDir = File(System.getProperty("rawReport").orEmpty().ifBlank { "build/raw-report" }).apply { mkdirs() }
        val ctx = RuntimeEnvironment.getApplication()
        println("LibRaw available: ${LibRawBridge.available}")
        val files = File(dir).walkTopDown().filter { it.isFile && (it.extension.lowercase() == "dng" || it.extension.lowercase() in LibRawBridge.EXTENSIONS) }.sortedBy { it.path }.toList()
        val rows = ArrayList<String>(); val fails = ArrayList<String>()
        val fits = LinkedHashMap<String, MutableList<Pair<IntArray, Float>>>()
        for (f in files) {
            val name = f.relativeTo(File(dir)).path
            val t0 = System.currentTimeMillis()
            try {
                val uri = Uri.fromFile(f)
                if (!RawDng.isRaw(ctx, uri)) { rows += "| NOT RAW | $name |  |  |  | not a RAW file inside (the app opens it as a normal photo) |"; continue }
                val m = RawDng.open(ctx, uri)
                val lin = RawDng.preview(ctx, m, 1200)
                val dev = RawDng.develop(lin, m, RawDng.Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                val emb = RawDng.embedded(ctx, uri, 300)?.bitmap
                val ms = System.currentTimeMillis() - t0
                val notes = ArrayList<String>()
                var status = "OK"
                if (emb != null) {
                    val ar = dev.width.toDouble() / dev.height; val ae = emb.width.toDouble() / emb.height
                    if ((ar > 1) != (ae > 1) && abs(ar - 1) > 0.05) { status = "FAIL"; notes += "orientation differs from the camera's preview" }
                    else if (abs(ar - ae) / ae > 0.06) notes += "shape %.2f vs camera %.2f".format(ar, ae)
                    val a = stats(dev); val b = stats(emb)
                    val vr = a.vig / b.vig
                    if (vr > 1.25) notes += "dark corners: centre/edge %.2f vs camera %.2f".format(a.vig, b.vig)
                    if (vr < 0.8) notes += "bright corners: centre/edge %.2f vs camera %.2f".format(a.vig, b.vig)
                    if (abs(a.lum - b.lum) > 0.18) notes += "brightness %.2f vs camera %.2f".format(a.lum, b.lum)
                    if (abs(a.rg - b.rg) > 0.18 || abs(a.bg - b.bg) > 0.18) notes += "colour cast r/g %.2f vs %.2f, b/g %.2f vs %.2f".format(a.rg, b.rg, a.bg, b.bg)
                    sideBySide(emb, dev, File(reportDir, name.replace('/', '_').replace(' ', '_') + ".jpg"))
                    // How this camera renders its own picture, compared with the app's default develop.
                    try {
                        val (lut, sat) = RawDng.fitTone(dev, emb)
                        fits.getOrPut(group(m.make, name)) { ArrayList() } += lut to sat
                    } catch (_: Throwable) {}
                } else notes += "no camera preview to compare with"
                if (status == "OK" && notes.any { !it.startsWith("no camera") && !it.startsWith("shape") }) status = "WARN"
                if (status == "FAIL") fails += name
                rows += "| $status | $name | ${m.info.firstOrNull { it.first == "Camera" || it.first == "Drone" }?.second ?: ""} | ${dev.width}×${dev.height} | $ms ms | ${notes.joinToString("; ")} |"
            } catch (t: RawDng.UnsupportedRaw) {
                // A clear "not supported" message for the user is the expected result for formats nobody can read yet.
                rows += "| NOT SUPPORTED | $name |  |  |  | ${t.message.orEmpty().replace('|', '/')} |"
            } catch (t: Throwable) {
                fails += name
                rows += "| FAIL | $name |  |  |  | ${(t.javaClass.simpleName + ": " + (t.message ?: "")).replace('|', '/').take(200)} |"
            }
        }
        val ok = rows.count { it.startsWith("| OK") }; val warn = rows.count { it.startsWith("| WARN") }
        File(reportDir, "README.md").writeText(buildString {
            appendLine("# RAW sample test")
            appendLine()
            appendLine("${files.size} files: $ok OK, $warn to look at, ${rows.count { it.startsWith("| NOT SUPPORTED") }} not supported (clear message), ${rows.count { it.startsWith("| NOT RAW") }} not RAW, ${fails.size} failed. Side-by-side pictures: camera's own preview (left) and AlcorCut Pro's develop (right).")
            appendLine()
            appendLine("| Result | File | Camera | Size | Time | Notes |")
            appendLine("|---|---|---|---|---|---|")
            rows.forEach { appendLine(it) }
        })
        // Average look per maker: 17-point tone curve (input 0, 16 … 256) and colour strength.
        File(reportDir, "looks.tsv").writeText(buildString {
            appendLine("group\tfiles\tsat\tcurve(0,16..255)")
            for ((g, l) in fits.toSortedMap()) {
                val pts = (0..16).map { k -> val v = (k * 16).coerceAtMost(255); l.map { it.first[v] }.average().roundToInt() }
                val sat = exp(l.map { ln(it.second.toDouble()) }.average())
                appendLine("$g\t${l.size}\t${"%.3f".format(sat)}\t${pts.joinToString(",")}")
            }
        })
        println(File(reportDir, "README.md").readText())
        assert(fails.isEmpty()) { "RAW files that failed: " + fails.joinToString() }
    }
}
