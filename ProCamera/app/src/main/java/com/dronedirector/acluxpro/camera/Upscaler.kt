package com.dronedirector.acluxpro.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.sqrt

/** AI upscale settings (off unless the user turns it on). */
object UpscaleSettings {
    private const val PREFS = "procam"
    var enabled by mutableStateOf(false)     // shows the AI sizes in the photo size picker
        private set
    /** Size chosen for the current photo (set by the camera from the selected AI size). */
    var targetMp by mutableIntStateOf(0)
    var high by mutableStateOf(false)         // High quality: slower, keeps more real detail
        private set
    var keepOriginal by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("ai_upscale_on", false)
        high = p.getBoolean("ai_upscale_high", false)
        keepOriginal = p.getBoolean("ai_upscale_keep", true)
        loaded = true
    }

    fun set(context: Context, on: Boolean = enabled, hq: Boolean = high, keep: Boolean = keepOriginal) {
        enabled = on; high = hq; keepOriginal = keep
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("ai_upscale_on", on).putBoolean("ai_upscale_high", hq).putBoolean("ai_upscale_keep", keep).apply()
    }
}

/**
 * On-device AI super-resolution (ESRGAN, 4×) run in overlapping tiles on the GPU when possible,
 * then resampled to the chosen megapixel size. Each photo is upscaled in the background and
 * saved as a new picture next to the original.
 */
object Upscaler {
    var pending by mutableIntStateOf(0)
        private set
    var progress by mutableIntStateOf(0)
        private set
    var lastNote by mutableStateOf<String?>(null)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ai-upscale").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private var interpreter: Interpreter? = null
    @Volatile private var jobGen = 0
    private var gpu: AutoCloseable? = null
    private var tileIn = 0
    private var tileOut = 0
    private val factor get() = tileOut / tileIn.coerceAtLeast(1)
    private var modelMissing = false

    /** Bumped by "stop": queued and running jobs from an older generation end straight away. */
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)
    private class Stopped : RuntimeException()

    fun cancelAll() {
        generation.incrementAndGet()
        lastNote = "AI upscaling stopped"
    }

    fun enqueue(context: Context, uri: Uri, mp: Int) {
        val gen = generation.get()
        if (mp <= 0) return
        val app = context.applicationContext
        val hq = UpscaleSettings.high
        val keep = UpscaleSettings.keepOriginal
        main.post { pending++ }
        worker.execute {
            val note = try {
                if (gen != generation.get()) null else { jobGen = gen; process(app, uri, mp, hq, keep) }
            } catch (_: Stopped) { null } catch (t: Throwable) { "AI upscale failed: ${t.message ?: t.javaClass.simpleName}" }
            main.post { pending--; progress = 0; if (note != null) lastNote = note }
        }
    }

    private fun setProgress(p: Int) = main.post { progress = p }

    /** Loads ESRGAN once; GPU first, CPU if the GPU path isn't available. Returns false if the model isn't in the app. */
    private fun ensureModel(context: Context): Boolean {
        if (interpreter != null) return true
        if (modelMissing) return false
        val bytes = try { context.assets.open("esrgan.tflite").use { it.readBytes() } } catch (_: Throwable) { modelMissing = true; return false }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
        fun build(useGpu: Boolean, size: Int = 0): Interpreter? {
            var delegate: AutoCloseable? = null
            var itp: Interpreter? = null
            try {
                val opts = Interpreter.Options()
                if (useGpu) {
                    val compat = org.tensorflow.lite.gpu.CompatibilityList()
                    if (!compat.isDelegateSupportedOnThisDevice) return null
                    val d = org.tensorflow.lite.gpu.GpuDelegate(compat.bestOptionsForThisDevice)
                    opts.addDelegate(d); delegate = d
                } else opts.setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
                itp = Interpreter(buf, opts)
                // Bigger tiles = far fewer model runs. Only kept if the self-test below passes.
                if (size > 0) { itp.resizeInput(0, intArrayOf(1, size, size, 3)); itp.allocateTensors() }
                // Use the model's real tile sizes (never assume them).
                val ins = itp.getInputTensor(0).shape()     // [1, h, w, 3]
                val outs = itp.getOutputTensor(0).shape()
                if (ins.size != 4 || outs.size != 4 || ins[3] != 3 || outs[3] != 3 || ins[1] != ins[2] || outs[1] != outs[2]) throw IllegalStateException("shape")
                val tin = ins[1]; val tout = outs[1]
                if (tout % tin != 0 || tout / tin < 2) throw IllegalStateException("scale")
                // Self-test on a smooth colour ramp: the result must keep the same colours.
                val inp = ByteBuffer.allocateDirect(tin * tin * 3 * 4).order(ByteOrder.nativeOrder())
                var sumIn = 0.0
                for (y in 0 until tin) for (x in 0 until tin) {
                    val r = 40f + 160f * x / tin; val g = 60f + 120f * y / tin; val b = 120f
                    inp.putFloat(r); inp.putFloat(g); inp.putFloat(b); sumIn += r + g + b
                }
                inp.rewind()
                val out = ByteBuffer.allocateDirect(tout * tout * 3 * 4).order(ByteOrder.nativeOrder())
                itp.run(inp, out)
                out.rewind()
                // Each output pixel must roughly match the ramp at the same place (catches layout mix-ups).
                var err = 0.0; var n = 0
                val k = tout / tin
                for (y in 0 until tout step 7) for (x in 0 until tout step 7) {
                    out.position(((y * tout) + x) * 3 * 4)
                    val r = out.float; val g = out.float
                    val er = 40f + 160f * (x / k) / tin; val eg = 60f + 120f * (y / k) / tin
                    err += kotlin.math.abs(r - er) + kotlin.math.abs(g - eg); n += 2
                }
                if (n == 0 || err / n > 14.0 || sumIn <= 0) throw IllegalStateException("self-test failed")
                gpu = delegate; tileIn = tin; tileOut = tout
                return itp
            } catch (_: Throwable) {
                try { itp?.close() } catch (_: Throwable) {}
                try { delegate?.close() } catch (_: Throwable) {}
                return null
            }
        }
        interpreter = build(true, 128) ?: build(true, 96) ?: build(true) ?: build(false, 96) ?: build(false)
        return interpreter != null
    }

    private fun process(context: Context, uri: Uri, mp: Int, hq: Boolean, keep: Boolean): String? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth; val h = bounds.outHeight
        if (w <= 0 || h <= 0) return "AI upscale: couldn't read the photo"
        val target = mp * 1_000_000.0
        if (w.toLong() * h >= target * 0.95) return null   // already that big
        val s = sqrt(target / (w.toDouble() * h))
        val tw = (w * s).toInt() / 2 * 2; val th = (h * s).toInt() / 2 * 2

        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) }
            ?: return "AI upscale: couldn't read the photo"

        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val ai = ensureModel(context)
        if (ai) {
            // ESRGAN is 4×: feed it a smaller copy (Standard) or close to full size (High), then resample.
            val f = factor.coerceAtLeast(1).toDouble()
            val pre = (if (hq) s * 2.0 / f else s / f).coerceAtMost(1.0)
            val iw = (w * pre).toInt().coerceAtLeast(8); val ih = (h * pre).toInt().coerceAtLeast(8)
            val inp = if (iw == w && ih == h) src else Bitmap.createScaledBitmap(src, iw, ih, true)
            runTiles(inp, out, tw.toFloat() / (iw * factor), th.toFloat() / (ih * factor))
            if (inp !== src) inp.recycle()
        } else {
            Canvas(out).drawBitmap(src, null, RectF(0f, 0f, tw.toFloat(), th.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        src.recycle()

        // Save as a new JPEG next to the original, keeping its EXIF (orientation, time, GPS, lens).
        val baseName = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.substringBeforeLast('.') ?: "IMG"
        val tmp = File(context.cacheDir, "upscale_${System.currentTimeMillis()}.jpg")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        out.recycle()
        try {
            val srcExif = cr.openInputStream(uri)?.use { ExifInterface(it) }
            val dst = ExifInterface(tmp.path)
            if (srcExif != null) for (tag in EXIF_TAGS) srcExif.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            dst.setAttribute(ExifInterface.TAG_SOFTWARE, "AcluxPro AI upscale ${mp} MP")
            dst.saveAttributes()
        } catch (_: Throwable) {}

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_AI${mp}MP.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val target2 = cr.insert(Storage.imagesUri(Storage.load(context)), values) ?: run { tmp.delete(); return "AI upscale: couldn't save" }
        cr.openOutputStream(target2)?.use { os -> tmp.inputStream().use { it.copyTo(os) } }
        if (Build.VERSION.SDK_INT >= 29) cr.update(target2, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        tmp.delete()
        if (!keep) try { cr.delete(uri, null, null) } catch (_: Throwable) {}
        return if (ai) "AI upscaled to ${mp} MP (${tw}×${th})" else "Upscaled to ${mp} MP (AI model unavailable — classic resize used)"
    }

    /** Runs ESRGAN over overlapping tiles and paints each result into [out] scaled by kx/ky. */
    private fun runTiles(inp: Bitmap, out: Bitmap, kx: Float, ky: Float) {
        val itp = interpreter ?: return
        val t = tileIn
        val f = factor
        val pad = (t / 10).coerceIn(3, 8)
        val step = t - 2 * pad
        val iw = inp.width; val ih = inp.height
        val px = IntArray(iw * ih)
        inp.getPixels(px, 0, iw, 0, 0, iw, ih)
        val inBuf = ByteBuffer.allocateDirect(t * t * 3 * 4).order(ByteOrder.nativeOrder())
        val ot = tileOut
        val outBuf = ByteBuffer.allocateDirect(ot * ot * 3 * 4).order(ByteOrder.nativeOrder())
        val tilePx = IntArray(ot * ot)
        val tileBmp = Bitmap.createBitmap(ot, ot, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val cols = ceil(iw / step.toDouble()).toInt(); val rows = ceil(ih / step.toDouble()).toInt()
        val total = cols * rows
        var done = 0
        for (ty in 0 until rows) for (tx in 0 until cols) {
            val x0 = tx * step; val y0 = ty * step
            inBuf.rewind()
            for (yy in 0 until t) {
                val sy = (y0 - pad + yy).coerceIn(0, ih - 1)
                for (xx in 0 until t) {
                    val sx = (x0 - pad + xx).coerceIn(0, iw - 1)
                    val c = px[sy * iw + sx]
                    inBuf.putFloat(((c shr 16) and 0xFF).toFloat())
                    inBuf.putFloat(((c shr 8) and 0xFF).toFloat())
                    inBuf.putFloat((c and 0xFF).toFloat())
                }
            }
            val vw = minOf(step, iw - x0); val vh = minOf(step, ih - y0)
            val dst = RectF(x0 * f * kx, y0 * f * ky, (x0 + vw) * f * kx, (y0 + vh) * f * ky)
            // Flat areas (sky, walls) gain nothing from the AI: a smooth resize is identical and instant.
            if (isFlat(px, iw, x0, y0, vw, vh)) {
                canvas.drawBitmap(inp, android.graphics.Rect(x0, y0, x0 + vw, y0 + vh), dst, paint)
                done++
                if (jobGen != generation.get()) { tileBmp.recycle(); throw Stopped() }
                if (done % 8 == 0) setProgress(done * 100 / total)
                continue
            }
            inBuf.rewind(); outBuf.rewind()
            itp.run(inBuf, outBuf)
            outBuf.rewind()
            for (i in 0 until ot * ot) {
                val r = outBuf.float.toInt().coerceIn(0, 255)
                val g = outBuf.float.toInt().coerceIn(0, 255)
                val b = outBuf.float.toInt().coerceIn(0, 255)
                tilePx[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            tileBmp.setPixels(tilePx, 0, ot, 0, 0, ot, ot)
            // Keep the inner part of the tile (edges of each tile are less accurate).
            val srcR = android.graphics.Rect(pad * f, pad * f, (pad + vw) * f, (pad + vh) * f)
            canvas.drawBitmap(tileBmp, srcR, dst, paint)
            done++
            if (jobGen != generation.get()) { tileBmp.recycle(); throw Stopped() }
            if (done % 4 == 0) setProgress(done * 100 / total)
        }
        tileBmp.recycle()
    }

    /** True when a tile has almost no detail (low contrast in luminance). */
    private fun isFlat(px: IntArray, iw: Int, x0: Int, y0: Int, w: Int, h: Int): Boolean {
        var mn = 255; var mx = 0
        var y = y0
        while (y < y0 + h) {
            var x = x0
            while (x < x0 + w) {
                val c = px[y * iw + x]
                val l = (((c shr 16) and 0xFF) * 3 + ((c shr 8) and 0xFF) * 6 + (c and 0xFF)) / 10
                if (l < mn) mn = l; if (l > mx) mx = l
                if (mx - mn > 10) return false
                x += 2
            }
            y += 2
        }
        return true
    }

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    )
}
