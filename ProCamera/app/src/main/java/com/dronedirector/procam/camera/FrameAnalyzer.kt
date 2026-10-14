package com.dronedirector.procam.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/** Settings the analyzer reads on every frame (written from the UI thread). */
data class AnalysisConfig(
    val histogram: Boolean = true,
    val zebras: Boolean = false,
    val zebraLevel: Int = 95,
    val peaking: Boolean = false,
    val mirror: Boolean = false,
)

class AnalysisResult(
    /** 64 normalized luma bins (0..1), or null when the histogram is off. */
    val histogram: FloatArray?,
    /** Zebra / focus-peaking overlay, already rotated to display orientation. */
    val overlay: Bitmap?,
    /** Share of pixels clipped at pure white / black, for the clipping warning. */
    val clipHigh: Float,
    val clipLow: Float,
)

/**
 * Works on the luma (Y) plane only, at a subsampled resolution, so it stays cheap enough
 * to run alongside preview and capture on mid-range tablets.
 */
class FrameAnalyzer(
    private val config: () -> AnalysisConfig,
    private val onResult: (AnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private var buffer = ByteArray(0)
    private var frame = 0L
    private var lastOverlay: Bitmap? = null

    override fun analyze(image: ImageProxy) {
        try {
            process(image)
        } catch (_: Throwable) {
            // A bad frame is never worth crashing the viewfinder for.
        } finally {
            image.close()
        }
    }

    private fun process(image: ImageProxy) {
        frame++
        val cfg = config()
        val wantOverlay = cfg.zebras || cfg.peaking
        // Histogram ~10 fps, overlays ~15 fps.
        val doHist = cfg.histogram && frame % 3 == 0L
        val doOverlay = wantOverlay && frame % 2 == 0L
        if (!doHist && !doOverlay) {
            if (!wantOverlay && lastOverlay != null) {
                lastOverlay = null
                onResult(AnalysisResult(null, null, 0f, 0f))
            }
            return
        }

        val plane = image.planes[0]
        val buf = plane.buffer
        buf.rewind()
        val size = buf.remaining()
        if (buffer.size < size) buffer = ByteArray(size)
        buf.get(buffer, 0, size)
        val rowStride = plane.rowStride
        val pixStride = plane.pixelStride
        val w = image.width
        val h = image.height

        var hist: FloatArray? = null
        var clipHigh = 0f
        var clipLow = 0f
        if (doHist) {
            val bins = IntArray(64)
            var hi = 0
            var lo = 0
            var n = 0
            var y = 0
            while (y < h) {
                val row = y * rowStride
                var x = 0
                while (x < w) {
                    val l = buffer[row + x * pixStride].toInt() and 0xFF
                    bins[l shr 2]++
                    if (l >= 250) hi++
                    if (l <= 5) lo++
                    n++
                    x += 4
                }
                y += 4
            }
            val max = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            hist = FloatArray(64) { bins[it] / max }
            if (n > 0) {
                clipHigh = hi.toFloat() / n
                clipLow = lo.toFloat() / n
            }
        }

        var overlay: Bitmap? = lastOverlay
        if (doOverlay) {
            overlay = buildOverlay(cfg, w, h, rowStride, pixStride, image.imageInfo.rotationDegrees)
            lastOverlay = overlay
        } else if (!wantOverlay) {
            overlay = null
            lastOverlay = null
        }

        onResult(AnalysisResult(hist, overlay, clipHigh, clipLow))
    }

    private fun buildOverlay(
        cfg: AnalysisConfig, w: Int, h: Int, rowStride: Int, pixStride: Int, rotation: Int,
    ): Bitmap {
        val step = 2
        val ow = w / step
        val oh = h / step
        val px = IntArray(ow * oh)
        val zebraThreshold = (cfg.zebraLevel * 255) / 100
        val peakThreshold = 48
        val peakColor = 0xFFFF2D55.toInt()
        val zebraDark = 0xC0000000.toInt()
        val zebraLight = 0x90FFFFFF.toInt()

        fun lum(x: Int, y: Int): Int = buffer[y * rowStride + x * pixStride].toInt() and 0xFF

        for (oy in 1 until oh - 1) {
            val sy = oy * step
            for (ox in 1 until ow - 1) {
                val sx = ox * step
                val l = lum(sx, sy)
                var c = 0
                if (cfg.peaking) {
                    val gx = abs(lum(sx + step, sy) - lum(sx - step, sy))
                    val gy = abs(lum(sx, sy + step) - lum(sx, sy - step))
                    if (gx + gy > peakThreshold) c = peakColor
                }
                if (c == 0 && cfg.zebras && l >= zebraThreshold) {
                    c = if (((ox + oy) / 4) % 2 == 0) zebraDark else zebraLight
                }
                px[oy * ow + ox] = c
            }
        }
        val bmp = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        if (rotation == 0 && !cfg.mirror) return bmp
        val m = Matrix()
        m.postRotate(rotation.toFloat())
        if (cfg.mirror) m.postScale(-1f, 1f)
        return Bitmap.createBitmap(bmp, 0, 0, ow, oh, m, false)
    }
}
