package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Shape layers: what they look like. */
enum class ShapeKind(val label: String) { RECT("Rectangle"), ROUNDED("Rounded"), ELLIPSE("Ellipse"), LINE("Line"), ARROW("Arrow"), TRIANGLE("Triangle"), STAR("Star"), HEART("Heart") }

object ShapeRender {
    /** Draws shape layer [l] [h] px tall. */
    fun render(l: Layer, h0: Float, frameW: Int): Bitmap {
        val kind = ShapeKind.entries.getOrElse(l.shape ?: 0) { ShapeKind.RECT }
        val aspect = (l.shapeAspect ?: if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) 4f else if (kind == ShapeKind.RECT || kind == ShapeKind.ROUNDED) 1.6f else 1f).coerceIn(0.1f, 12f)
        val h = h0.coerceAtLeast(4f)
        val w = (h * aspect).coerceIn(4f, frameW * 2f)
        val stroke = (l.stroke ?: 0f).coerceIn(0f, 0.5f)
        val sw = if (stroke > 0f || kind == ShapeKind.LINE) max(2f, (if (stroke > 0f) stroke else 0.08f) * min(w, h)) else 0f
        val pad = sw / 2f + (if (l.shadow) h * 0.06f else 1f)
        val bmp = Bitmap.createBitmap((w + pad * 2).toInt().coerceAtLeast(4), (h + pad * 2).toInt().coerceAtLeast(4), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = l.color
            style = if (sw > 0f) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = sw; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            if (l.shadow) setShadowLayer(h * 0.05f, 0f, h * 0.025f, 0x99000000.toInt())
        }
        val r = RectF(pad, pad, pad + w, pad + h)
        when (kind) {
            ShapeKind.RECT -> c.drawRect(r, p)
            ShapeKind.ROUNDED -> c.drawRoundRect(r, min(w, h) * 0.22f, min(w, h) * 0.22f, p)
            ShapeKind.ELLIPSE -> c.drawOval(r, p)
            ShapeKind.LINE -> { p.style = Paint.Style.STROKE; c.drawLine(r.left, r.centerY(), r.right, r.centerY(), p) }
            ShapeKind.ARROW -> {
                val head = min(h, w * 0.35f)
                val path = Path().apply {
                    moveTo(r.left, r.centerY() - h * 0.16f); lineTo(r.right - head, r.centerY() - h * 0.16f); lineTo(r.right - head, r.top)
                    lineTo(r.right, r.centerY()); lineTo(r.right - head, r.bottom); lineTo(r.right - head, r.centerY() + h * 0.16f)
                    lineTo(r.left, r.centerY() + h * 0.16f); close()
                }
                c.drawPath(path, p)
            }
            ShapeKind.TRIANGLE -> c.drawPath(Path().apply { moveTo(r.centerX(), r.top); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, p)
            ShapeKind.STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val rr = if (i % 2 == 0) 1f else 0.42f
                    val x = r.centerX() + (cos(a) * rr * w / 2).toFloat(); val y = r.centerY() + (sin(a) * rr * h / 2).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, p)
            }
            ShapeKind.HEART -> {
                val path = Path().apply {
                    moveTo(r.centerX(), r.bottom)
                    cubicTo(r.left - w * 0.05f, r.top + h * 0.55f, r.left + w * 0.05f, r.top - h * 0.05f, r.centerX(), r.top + h * 0.28f)
                    cubicTo(r.right - w * 0.05f, r.top - h * 0.05f, r.right + w * 0.05f, r.top + h * 0.55f, r.centerX(), r.bottom)
                    close()
                }
                c.drawPath(path, p)
            }
        }
        return bmp
    }
}

/**
 * Lottie animations as layers (Airbnb's lottie-android, Apache 2.0): After Effects animations
 * exported as .json or .zip — animated titles, icons, lower thirds, transitions. They loop over the
 * layer's time.
 */
object LottieRender {
    private val comps = HashMap<String, LottieComposition?>()

    /** Copies a picked .json / .zip into the app (so projects keep it); returns the copy, or null if it isn't a Lottie file. */
    fun import(context: Context, uri: Uri): Uri? = try {
        val dir = File(context.filesDir, "lottie").apply { mkdirs() }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val zip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val f = File(dir, "${bytes.contentHashCode().toUInt().toString(16)}.${if (zip) "zip" else "json"}")
        if (!f.exists()) f.writeBytes(bytes)
        val u = Uri.fromFile(f)
        if (comp(context, u) == null) { f.delete(); null } else u
    } catch (_: Throwable) { null }

    fun comp(context: Context, uri: Uri): LottieComposition? = synchronized(comps) {
        comps.getOrPut(uri.toString()) {
            try {
                val path = uri.path ?: return@getOrPut null
                val f = File(path)
                val r = if (f.name.endsWith(".zip")) LottieCompositionFactory.fromZipStreamSync(context, ZipInputStream(f.inputStream()), uri.toString())
                else LottieCompositionFactory.fromJsonInputStreamSync(f.inputStream(), uri.toString())
                r.value
            } catch (_: Throwable) { null }
        }
    }

    /** Length of one loop (ms). */
    fun durationMs(context: Context, l: Layer): Long = l.imageUri?.let { comp(context, it) }?.duration?.toLong()?.coerceAtLeast(1) ?: 1000L

    /** Which frame shows at timeline [ms] (loops over the layer's time). */
    fun frameIndex(context: Context, l: Layer, ms: Long): Int {
        val c = l.imageUri?.let { comp(context, it) } ?: return 0
        val start = if (l.wholeVideo) 0L else l.startMs
        val dur = c.duration.toLong().coerceAtLeast(1)
        val t = ((ms - start).coerceAtLeast(0) % dur).toFloat() / dur
        return (t * (c.endFrame - c.startFrame)).toInt()
    }

    /** The animation at frame [frame], [h0] px tall. */
    fun render(context: Context, l: Layer, h0: Float, frameW: Int, frame: Int): Bitmap? {
        val c = l.imageUri?.let { comp(context, it) } ?: return null
        val b = c.bounds
        if (b.width() <= 0 || b.height() <= 0) return null
        val h = h0.toInt().coerceAtLeast(4)
        val w = (h * b.width().toFloat() / b.height()).toInt().coerceIn(4, frameW * 2)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        synchronized(this) {
            val d = LottieDrawable()
            d.composition = c
            d.frame = (c.startFrame + frame).toInt().coerceIn(c.startFrame.toInt(), c.endFrame.toInt())
            val cv = Canvas(out)
            cv.scale(w.toFloat() / b.width(), h.toFloat() / b.height())
            d.setBounds(0, 0, b.width(), b.height())
            d.draw(cv)
        }
        return out
    }
}
