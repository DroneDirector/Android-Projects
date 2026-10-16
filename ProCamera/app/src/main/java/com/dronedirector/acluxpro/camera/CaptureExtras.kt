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

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        shutterAngle = p.getBoolean("x_shutter_angle", false)
        desqueeze = p.getFloat("x_desqueeze", 1f)
        monitorLut = p.getString("x_monitor_lut", null)
        bakeLut = p.getBoolean("x_bake_lut", false)
    }

    fun save(c: Context) {
        prefs(c).edit()
            .putBoolean("x_shutter_angle", shutterAngle)
            .putFloat("x_desqueeze", desqueeze)
            .putString("x_monitor_lut", monitorLut)
            .putBoolean("x_bake_lut", bakeLut)
            .apply()
    }

    /** Shutter time as an angle at [fps] frames per second. */
    fun angleOf(shutterNs: Long, fps: Int): Int = Math.round(360.0 * fps * shutterNs / 1e9).toInt()

    /** Exposure time for a 180° shutter at [fps]. */
    fun ns180(fps: Int): Long = (1e9 / (2.0 * fps.coerceAtLeast(1))).toLong()
}
