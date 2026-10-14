package com.dronedirector.procam.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** A still-photo resolution a camera supports. [highRes] sizes need the slower high-resolution mode. */
data class PhotoSize(val width: Int, val height: Int, val highRes: Boolean, val square: Boolean = false) {
    private val pixels: Long get() = if (square) height.toLong() * height else width.toLong() * height
    val megapixels: Float get() = pixels / 1_000_000f
    val label: String
        get() = if (megapixels >= 10f) "${megapixels.roundToInt()} MP" else "%.1f MP".format(megapixels)
    val detail: String get() = if (square) "${height}×$height" else "${width}×$height"
    val key: String get() = "${width}x$height"
}

object PhotoSizes {
    private const val PREFS = "procam"

    /** Distinct megapixel options for this camera and aspect ratio, largest first. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun forCamera(info: CameraInfo, aspect: AspectMode): List<PhotoSize> {
        val map = try {
            Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        } catch (_: Throwable) { null } ?: return emptyList()
        val normal = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        val high = try { map.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() } catch (_: Throwable) { emptyList() }
        val ratio = if (aspect == AspectMode.R16_9) 16f / 9f else 4f / 3f
        val square = aspect == AspectMode.R1_1

        val all = high.map { PhotoSize(it.width, it.height, true, square) } +
            normal.map { PhotoSize(it.width, it.height, false, square) }
        val result = ArrayList<PhotoSize>()
        for (p in all.sortedByDescending { it.width.toLong() * it.height }) {
            val w = maxOf(p.width, p.height)
            val h = minOf(p.width, p.height)
            if (abs(w.toFloat() / h - ratio) > 0.03f) continue
            if (p.megapixels < 0.9f) continue
            // One entry per distinct megapixel label.
            if (result.any { it.label == p.label }) continue
            result += p.copy(width = w, height = h)
            if (result.size >= 6) break
        }
        return result
    }

    fun load(context: Context, cameraId: String, aspect: AspectMode): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("size_${cameraId}_${aspect.name}", null)

    fun save(context: Context, cameraId: String, aspect: AspectMode, size: PhotoSize) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("size_${cameraId}_${aspect.name}", size.key).apply()
    }
}
