package com.dronedirector.acluxpro.camera

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Capture options that only change how things are shown / monitored (kept by the app):
 * shutter as an angle, anamorphic desqueeze of the viewfinder, and a monitoring LUT.
 */
object CaptureExtras {
    /** Show the video shutter as an angle (180° = 1 / (2 × fps)). */
    var shutterAngle by mutableStateOf(false)
    /** Anamorphic lens squeeze factor for the viewfinder (1 = off). */
    var desqueeze by mutableStateOf(1f)
    /** Your LUT (id in the editor's LUT library) shown on the viewfinder, or null. */
    var monitorLut by mutableStateOf<String?>(null)
    /** Also record the LUT into videos (otherwise it's for monitoring only). */
    var bakeLut by mutableStateOf(false)
    /** Wind filter on recorded sound. */
    var windFilter by mutableStateOf(false)
    /** Voice noise reduction on recorded sound: 0 off, 1 light, 2 strong. */
    var noiseReduce by mutableStateOf(0)
    /** Blur faces live in the viewfinder and in recordings / photos. */
    var faceBlur by mutableStateOf(false)
    /** Auto-enhance photos right after they're taken (saved as an extra copy). */
    var autoEnhance by mutableStateOf(false)
    /** Title burned into videos: text, on / off, position (0 lower third, 1 centre, 2 top) and seconds shown (0 = whole clip). */
    var titleText by mutableStateOf("")
    var titleOn by mutableStateOf(false)
    var titlePos by mutableStateOf(0)
    var titleSec by mutableStateOf(5)
    /** Written into every photo (EXIF Artist / Copyright). */
    /** Title-safe (80 %) and action-safe (90 %) frames in the viewfinder. */
    var safeAreas by mutableStateOf(false)
    /** Slate & shot log. */
    var slateOn by mutableStateOf(false)
    var slateScene by mutableStateOf("1")
    var slateShot by mutableStateOf("")
    var slateTake by mutableStateOf(1)
    var slateNotes by mutableStateOf("")
    var slateAutoTake by mutableStateOf(true)
    var creator by mutableStateOf("")
    var copyright by mutableStateOf("")
    /** Seconds kept from before you press record (0 = off). */
    var preRecordSec by mutableStateOf(0)
    /** Use a Bluetooth headset microphone when one is connected. */
    var micBluetooth by mutableStateOf(false)
    /** Magnified view while focusing manually. */
    var focusMagnifier by mutableStateOf(true)
    /** Rack focus points (diopters) — not kept between sessions. */
    var focusA by mutableStateOf<Float?>(null)
    var focusB by mutableStateOf<Float?>(null)
    /** Scope shown on the viewfinder in Pro modes: 0 histogram, 1 waveform, 2 RGB parade, 3 vectorscope. */
    var scope by mutableStateOf(0)

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        shutterAngle = p.getBoolean("x_shutter_angle", false)
        desqueeze = p.getFloat("x_desqueeze", 1f)
        monitorLut = p.getString("x_monitor_lut", null)
        bakeLut = p.getBoolean("x_bake_lut", false)
        windFilter = p.getBoolean("x_wind", false)
        noiseReduce = p.getInt("x_nr", 0)
        faceBlur = p.getBoolean("x_face_blur", false)
        autoEnhance = p.getBoolean("x_auto_enh", false)
        titleText = p.getString("x_title", "") ?: ""
        titleOn = p.getBoolean("x_title_on", false)
        titlePos = p.getInt("x_title_pos", 0)
        titleSec = p.getInt("x_title_sec", 5)
        creator = p.getString("x_creator", "") ?: ""
        safeAreas = p.getBoolean("x_safe", false)
        slateOn = p.getBoolean("x_slate", false)
        slateScene = p.getString("x_slate_scene", "1") ?: "1"
        slateShot = p.getString("x_slate_shot", "") ?: ""
        slateTake = p.getInt("x_slate_take", 1)
        slateNotes = p.getString("x_slate_notes", "") ?: ""
        slateAutoTake = p.getBoolean("x_slate_auto", true)
        copyright = p.getString("x_copyright", "") ?: ""
        preRecordSec = p.getInt("x_prerec", 0)
        micBluetooth = p.getBoolean("x_mic_bt", false)
        focusMagnifier = p.getBoolean("x_focus_mag", true)
        scope = p.getInt("x_scope", 0)
    }

    fun save(c: Context) {
        prefs(c).edit()
            .putBoolean("x_shutter_angle", shutterAngle)
            .putFloat("x_desqueeze", desqueeze)
            .putString("x_monitor_lut", monitorLut)
            .putBoolean("x_bake_lut", bakeLut)
            .putBoolean("x_wind", windFilter)
            .putInt("x_nr", noiseReduce)
            .putBoolean("x_face_blur", faceBlur)
            .putBoolean("x_auto_enh", autoEnhance)
            .putString("x_title", titleText)
            .putBoolean("x_title_on", titleOn)
            .putInt("x_title_pos", titlePos)
            .putInt("x_title_sec", titleSec)
            .putString("x_creator", creator)
            .putBoolean("x_safe", safeAreas)
            .putBoolean("x_slate", slateOn)
            .putString("x_slate_scene", slateScene)
            .putString("x_slate_shot", slateShot)
            .putInt("x_slate_take", slateTake)
            .putString("x_slate_notes", slateNotes)
            .putBoolean("x_slate_auto", slateAutoTake)
            .putString("x_copyright", copyright)
            .putInt("x_prerec", preRecordSec)
            .putBoolean("x_mic_bt", micBluetooth)
            .putBoolean("x_focus_mag", focusMagnifier)
            .putInt("x_scope", scope)
            .apply()
    }

    /** Shutter time as an angle at [fps] frames per second. */
    fun angleOf(shutterNs: Long, fps: Int): Int = Math.round(360.0 * fps * shutterNs / 1e9).toInt()

    /** Exposure time for a 180° shutter at [fps]. */
    fun ns180(fps: Int): Long = (1e9 / (2.0 * fps.coerceAtLeast(1))).toLong()
}

/** Your name and copyright notice, written into photos as they're saved (EXIF Artist / Copyright). */
object Copyright {
    private fun active() = CaptureExtras.creator.isNotBlank() || CaptureExtras.copyright.isNotBlank()

    private fun apply(e: androidx.exifinterface.media.ExifInterface) {
        if (CaptureExtras.creator.isNotBlank()) e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ARTIST, CaptureExtras.creator.trim())
        if (CaptureExtras.copyright.isNotBlank()) e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT, CaptureExtras.copyright.trim())
        e.saveAttributes()
    }

    fun stampFile(context: android.content.Context, f: java.io.File) {
        CaptureExtras.load(context)
        if (!active()) return
        try { apply(androidx.exifinterface.media.ExifInterface(f.path)) } catch (_: Throwable) {}
    }

    /** For a photo already in the gallery (camera captures). Call off the main thread. */
    fun stampUri(context: android.content.Context, uri: android.net.Uri) {
        if (!active()) return
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                apply(androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor))
            }
        } catch (_: Throwable) {}
    }
}
