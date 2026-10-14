package com.dronedirector.procam.camera

import android.hardware.camera2.CameraMetadata
import android.net.Uri
import androidx.camera.core.CameraInfo
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality

enum class CaptureMode { PHOTO, VIDEO }

enum class PhotoFormat(val label: String) { JPEG("JPEG"), RAW("RAW"), RAW_JPEG("RAW+J") }

enum class AspectMode(val label: String) { R4_3("4:3"), R16_9("16:9"), R1_1("1:1") }

enum class GridMode(val label: String) { OFF("OFF"), THIRDS("3x3"), PHI("PHI"), SQUARE("4x4"), CROSS("+") }

enum class FlashSetting(val label: String) { OFF("OFF"), AUTO("AUTO"), ON("ON"), TORCH("TORCH") }

enum class Scene(val label: String, val extensionMode: Int) {
    STANDARD("STD", ExtensionMode.NONE),
    HDR("HDR", ExtensionMode.HDR),
    NIGHT("NIGHT", ExtensionMode.NIGHT),
    BOKEH("BOKEH", ExtensionMode.BOKEH),
    AUTO("AUTO+", ExtensionMode.AUTO),
}

enum class WhiteBalance(val label: String, val awbMode: Int) {
    AUTO("AWB", CameraMetadata.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT("5500K", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("6500K", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    SHADE("7500K", CameraMetadata.CONTROL_AWB_MODE_SHADE),
    FLUORESCENT("4000K", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT),
    TUNGSTEN("3200K", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT),
    TWILIGHT("TWLT", CameraMetadata.CONTROL_AWB_MODE_TWILIGHT),
}

enum class VideoRes(val label: String, val quality: Quality, val highBitrate: Int) {
    UHD("4K", Quality.UHD, 85_000_000),
    FHD("1080p", Quality.FHD, 35_000_000),
    HD("720p", Quality.HD, 16_000_000),
    SD("480p", Quality.SD, 6_000_000),
}

enum class VideoProfile(val label: String) { STANDARD("STD"), FLAT("FLAT"), HLG("HLG 10b") }

/** Which manual parameter the pro panel is currently editing. */
enum class ProParam(val label: String) { ISO("ISO"), SHUTTER("S"), FOCUS("F"), WB("WB"), EV("EV"), ZOOM("ZOOM") }

class Lens(
    val id: String,
    val facing: Int,
    val label: String,
    val info: CameraInfo,
)

data class CameraUiState(
    val ready: Boolean = false,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val lenses: List<Lens> = emptyList(),
    val lensIndex: Int = 0,

    // What the current lens can do
    val manualSensor: Boolean = false,
    val isoStops: List<Int> = emptyList(),
    val shutterStops: List<Long> = emptyList(),
    val minFocusDiopters: Float = 0f,
    val evMin: Int = 0,
    val evMax: Int = 0,
    val evStep: Float = 1f / 3f,
    val rawSupported: Boolean = false,
    val hasFlash: Boolean = false,
    val scenes: List<Scene> = listOf(Scene.STANDARD),
    val videoResolutions: List<VideoRes> = emptyList(),
    val fpsOptions: List<Int> = listOf(30),
    val stabilizationSupported: Boolean = false,
    val hlgSupported: Boolean = false,
    val flatSupported: Boolean = false,

    // Exposure / focus / colour
    val manualIso: Boolean = false,
    val manualShutter: Boolean = false,
    val iso: Int = 100,
    val shutterNs: Long = 8_333_333L,
    val manualFocus: Boolean = false,
    val focusDiopters: Float = 0f,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    val ev: Int = 0,
    val aeLock: Boolean = false,
    val awbLock: Boolean = false,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val activeParam: ProParam? = null,

    // Photo options
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val aspect: AspectMode = AspectMode.R4_3,
    val flash: FlashSetting = FlashSetting.OFF,
    val scene: Scene = Scene.STANDARD,
    val timerSec: Int = 0,
    val burst: Int = 1,

    // Composition aids
    val grid: GridMode = GridMode.THIRDS,
    val showLevel: Boolean = true,
    val showHistogram: Boolean = true,
    val zebras: Boolean = false,
    val zebraLevel: Int = 95,
    val peaking: Boolean = false,
    val volumeShutter: Boolean = true,

    // Video options
    val videoRes: VideoRes = VideoRes.FHD,
    val fps: Int = 30,
    val stabilization: Boolean = true,
    val profile: VideoProfile = VideoProfile.STANDARD,
    val highBitrate: Boolean = true,
    val audio: Boolean = true,

    // Live readouts from the sensor
    val liveIso: Int? = null,
    val liveShutterNs: Long? = null,
    val liveAperture: Float? = null,
    val liveFocusDiopters: Float? = null,

    // Activity
    val recording: Boolean = false,
    val paused: Boolean = false,
    val recordSeconds: Long = 0,
    val audioLevel: Float = 0f,
    val countdown: Int = 0,
    val capturing: Boolean = false,
    val shutterFlash: Int = 0,
    val lastMedia: Uri? = null,
    val message: String? = null,
    val messageId: Int = 0,
    val focusPoint: Pair<Float, Float>? = null,
    val focusId: Int = 0,
) {
    val lens: Lens? get() = lenses.getOrNull(lensIndex)
    val isFront: Boolean get() = lens?.facing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT
    val manualExposure: Boolean get() = manualIso || manualShutter
    /** Extensions (HDR/Night…) can't be combined with manual sensor control or RAW. */
    val sceneActive: Boolean
        get() = mode == CaptureMode.PHOTO && scene != Scene.STANDARD &&
            !manualExposure && !manualFocus && photoFormat == PhotoFormat.JPEG
}

object Stops {
    private val iso = listOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250,
        1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600,
    )

    private val shutterDen = listOf(
        16000, 8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320,
        250, 200, 160, 125, 100, 80, 60, 50, 48, 40, 30, 25, 24, 20, 15, 13, 10, 8, 6, 5, 4, 3,
    )
    private val shutterSec = listOf(0.4, 0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0)

    fun isoStops(min: Int, max: Int): List<Int> {
        val list = iso.filter { it in min..max }.toMutableList()
        if (list.isEmpty() || list.first() != min) list.add(0, min)
        if (list.last() != max) list.add(max)
        return list
    }

    fun shutterStops(minNs: Long, maxNs: Long): List<Long> {
        val all = shutterDen.map { 1_000_000_000L / it } + shutterSec.map { (it * 1_000_000_000L).toLong() }
        return all.filter { it in minNs..maxNs }.ifEmpty { listOf(minNs, maxNs) }
    }

    fun shutterLabel(ns: Long): String {
        val sec = ns / 1_000_000_000.0
        return if (sec >= 0.3) {
            val v = if (sec >= 10 || sec == sec.toLong().toDouble()) sec.toLong().toString() else "%.1f".format(sec)
            "$v\""
        } else {
            "1/${(1.0 / sec).let { if (it >= 100) Math.round(it / 5.0) * 5 else Math.round(it) }}"
        }
    }

    fun focusLabel(diopters: Float): String =
        if (diopters <= 0.01f) "∞" else {
            val m = 1f / diopters
            if (m < 1f) "${(m * 100).toInt()}cm" else "%.1fm".format(m)
        }

    fun zoomLabel(z: Float): String =
        if (z < 10f) (if (z % 1f < 0.05f || z % 1f > 0.95f) "%.0fx".format(z) else "%.1fx".format(z)) else "%.0fx".format(z)

    fun <T : Comparable<T>> nearestIndex(list: List<T>, value: T, distance: (T, T) -> Double): Int {
        if (list.isEmpty()) return 0
        var best = 0
        var bestD = Double.MAX_VALUE
        list.forEachIndexed { i, v ->
            val d = distance(v, value)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
