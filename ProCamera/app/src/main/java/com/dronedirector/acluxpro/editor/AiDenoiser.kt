package com.dronedirector.acluxpro.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device AI noise reduction (NAFNet, trained on real smartphone noise — SIDD) run in
 * 256×256 tiles with overlap, on the GPU when possible. Built into the app; no internet.
 */
object AiDenoiser {
    private const val ASSET = "nafnet.tflite"
    private const val T = 256
    private const val PAD = 16

    var running by mutableStateOf(false)
        private set
    var progress by mutableIntStateOf(0)
        private set

    class Stopped : RuntimeException("stopped")

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ai-denoise") }
    private val main = Handler(Looper.getMainLooper())
    private val cancel = AtomicBoolean(false)
    private var model: CompiledModel? = null
    private var failed = false

    fun stop() = cancel.set(true)

    fun available(context: Context): Boolean = try { context.assets.open(ASSET).close(); true } catch (_: Throwable) { false }

    private fun ensure(context: Context): CompiledModel? {
        model?.let { return it }
        if (failed) return null
        for (acc in listOf(Accelerator.GPU, Accelerator.CPU)) {
            try {
                val m = CompiledModel.create(context.assets, ASSET, CompiledModel.Options(acc), null)
                model = m
                return m
            } catch (_: Throwable) {}
        }
        failed = true
        return null
    }

    /**
     * Fully denoised copy of ARGB pixels. Blocks the caller; the work runs on the AI thread.
     * Throws [Stopped] if [stop] is called; returns null when the model can't run on this phone.
     */
    fun denoise(context: Context, px: IntArray, w: Int, h: Int, showProgress: Boolean = true): IntArray? {
        val app = context.applicationContext
        return worker.submit(Callable { run(app, px, w, h, showProgress) }).get()
    }

    private fun run(context: Context, src: IntArray, w: Int, h: Int, showProgress: Boolean): IntArray? {
        val m = ensure(context) ?: return null
        cancel.set(false)
        if (showProgress) main.post { running = true; progress = 0 }
        try {
            val inputs = m.createInputBuffers()
            val outputs = m.createOutputBuffers()
            val out = IntArray(w * h)
            val step = T - 2 * PAD
            val cols = (w + step - 1) / step; val rows = (h + step - 1) / step
            val total = cols * rows
            val chw = FloatArray(3 * T * T)
            val plane = T * T
            var done = 0
            for (ty in 0 until rows) for (tx in 0 until cols) {
                if (cancel.get()) throw Stopped()
                val x0 = tx * step; val y0 = ty * step
                for (yy in 0 until T) {
                    val sy = (y0 - PAD + yy).coerceIn(0, h - 1)
                    for (xx in 0 until T) {
                        val sx = (x0 - PAD + xx).coerceIn(0, w - 1)
                        val c = src[sy * w + sx]
                        val i = yy * T + xx
                        chw[i] = ((c shr 16) and 0xFF) / 255f
                        chw[plane + i] = ((c shr 8) and 0xFF) / 255f
                        chw[2 * plane + i] = (c and 0xFF) / 255f
                    }
                }
                inputs[0].writeFloat(chw)
                m.run(inputs, outputs)
                val res = outputs[0].readFloat()
                val vw = minOf(step, w - x0); val vh = minOf(step, h - y0)
                for (yy in 0 until vh) for (xx in 0 until vw) {
                    val i = (yy + PAD) * T + (xx + PAD)
                    val r = (res[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val g = (res[plane + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val b = (res[2 * plane + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val o = (y0 + yy) * w + (x0 + xx)
                    out[o] = (src[o] and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                done++
                if (showProgress && done % 2 == 0) { val p = done * 100 / total; main.post { progress = p } }
            }
            inputs.forEach { try { it.close() } catch (_: Throwable) {} }
            outputs.forEach { try { it.close() } catch (_: Throwable) {} }
            return out
        } finally {
            if (showProgress) main.post { running = false; progress = 0 }
        }
    }

    /** a + (b - a) * t per pixel. */
    fun blend(a: IntArray, b: IntArray, t: Float): IntArray {
        if (t >= 0.999f) return b
        val k = (t.coerceIn(0f, 1f) * 256).toInt(); val ik = 256 - k
        return IntArray(a.size) { i ->
            val x = a[i]; val y = b[i]
            val r = (((x shr 16) and 0xFF) * ik + ((y shr 16) and 0xFF) * k) shr 8
            val g = (((x shr 8) and 0xFF) * ik + ((y shr 8) and 0xFF) * k) shr 8
            val bl = ((x and 0xFF) * ik + (y and 0xFF) * k) shr 8
            (x and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
        }
    }
}
