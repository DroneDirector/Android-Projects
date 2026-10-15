package com.dronedirector.acluxpro.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** A still-photo resolution a camera supports. [highRes] sizes need the slower high-resolution mode. */
data class PhotoSize(val width: Int, val height: Int, val highRes: Boolean, val square: Boolean = false, val aiMp: Int = 0) {
    private val pixels: Long get() = if (square) height.toLong() * height else width.toLong() * height
    val megapixels: Float get() = pixels / 1_000_000f
    val label: String
        get() = if (aiMp > 0) "AI $aiMp MP" else if (megapixels >= 10f) "${megapixels.roundToInt()} MP" else "%.1f MP".format(megapixels)
    val detail: String
        get() = if (aiMp > 0) "Shot at ${if (square) "${height}×$height" else "${width}×$height"}, AI-upscaled to $aiMp MP in the background"
        else if (square) "${height}×$height" else "${width}×$height"
    val key: String get() = if (aiMp > 0) "ai$aiMp:${width}x$height" else "${width}x$height"
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
        val high = try { map.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() } catch (_: Throwable) { emptyList() } +
            maxResolutionSizes(info)
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
        // AI sizes (when AI upscale is switched on): shot at the largest normal size, then upscaled.
        if (UpscaleSettings.enabled) {
            val base = result.firstOrNull { !it.highRes }
            if (base != null) for (mp in listOf(24, 36, 48)) {
                if (base.megapixels < mp * 0.9f) result.add(0, base.copy(aiMp = mp))
            }
            result.sortByDescending { if (it.aiMp > 0) it.aiMp.toFloat() else it.megapixels }
        }
        return result
    }

    /**
     * Android 12+ "maximum resolution" mode: 50 / 108 / 200 MP sensors that normally bin 4→1 can
     * offer their full size here — if the phone maker lets other apps use it.
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun maxResolutionSizes(info: CameraInfo): List<android.util.Size> {
        if (android.os.Build.VERSION.SDK_INT < 31) return emptyList()
        return try {
            val m = Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                ?: return emptyList()
            (m.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() + (m.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()))
        } catch (_: Throwable) { emptyList() }
    }

    /** What the phone tells apps about this camera's sensor and the largest photos apps may take. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun report(info: CameraInfo): String {
        return try {
            val ci = Camera2CameraInfo.from(info)
            fun mp(w: Int, h: Int) = "%.1f MP".format(w.toLong() * h / 1_000_000f)
            val sensor = ci.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val sensorMax = if (android.os.Build.VERSION.SDK_INT >= 31)
                ci.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION) else null
            val map = ci.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val normal = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
            val high = try { map?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height } } catch (_: Throwable) { null }
            val maxRes = maxResolutionSizes(info).maxByOrNull { it.width.toLong() * it.height }
            val caps = ci.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
            val ultra = android.os.Build.VERSION.SDK_INT >= 31 && CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR in caps
            val best = listOfNotNull(normal, high, maxRes).maxByOrNull { it.width.toLong() * it.height }
            buildString {
                append("Sensor as reported to apps: ")
                append(sensor?.let { mp(it.width, it.height) } ?: "?")
                if (sensorMax != null) append(" (full: ${mp(sensorMax.width, sensorMax.height)})")
                append(". Largest photo this phone lets other apps take: ")
                append(best?.let { "${it.width}×${it.height} (${mp(it.width, it.height)})" } ?: "?")
                append(". Full-resolution mode: ")
                append(if (ultra) "offered" else "not offered to other apps")
                append(".")
            }
        } catch (_: Throwable) { "" }
    }

    fun load(context: Context, cameraId: String, aspect: AspectMode): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("size_${cameraId}_${aspect.name}", null)

    fun save(context: Context, cameraId: String, aspect: AspectMode, size: PhotoSize) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("size_${cameraId}_${aspect.name}", size.key).apply()
    }
}
