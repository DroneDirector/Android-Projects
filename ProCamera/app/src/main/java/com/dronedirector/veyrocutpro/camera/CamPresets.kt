package com.dronedirector.veyrocutpro.camera

import android.content.Context

/** A saved camera set-up (C1 / C2 / C3), like the custom modes on pro cameras. */
data class CamPreset(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val lensIndex: Int = 0,
    val manualIso: Boolean = false, val manualShutter: Boolean = false,
    val iso: Int = 100, val shutterNs: Long = 8_333_333L,
    val manualFocus: Boolean = false, val focusDiopters: Float = 0f,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    val ev: Int = 0,
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val aspect: AspectMode = AspectMode.R4_3,
    val flash: FlashSetting = FlashSetting.OFF,
    val timerSec: Int = 0, val burst: Int = 1,
    val grid: GridMode = GridMode.THIRDS,
    val zebras: Boolean = false, val zebraLevel: Int = 95, val peaking: Boolean = false, val falseColor: Boolean = false,
    val videoRes: VideoRes = VideoRes.FHD, val fps: Int = 30, val stabilization: Boolean = false,
    val profile: VideoProfile = VideoProfile.STANDARD, val highBitrate: Boolean = true, val logAssist: Boolean = false,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val audioGainDb: Int = 0, val audioLimiter: Boolean = true,
    val filter: Filter = Filter.NONE, val guide: FrameGuide = FrameGuide.OFF,
) {
    companion object {
        fun from(s: CameraUiState) = CamPreset(
            s.mode, s.lensIndex, s.manualIso, s.manualShutter, s.iso, s.shutterNs, s.manualFocus, s.focusDiopters,
            s.whiteBalance, s.ev, s.photoFormat, s.aspect, s.flash, s.timerSec, s.burst, s.grid,
            s.zebras, s.zebraLevel, s.peaking, s.falseColor, s.videoRes, s.fps, s.stabilization,
            s.profile, s.highBitrate, s.logAssist, s.videoCodec, s.audioGainDb, s.audioLimiter, s.filter, s.guide,
        )
    }
}

object CamPresets {
    private val gson = com.google.gson.Gson()
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun load(c: Context, slot: Int): CamPreset? = try {
        prefs(c).getString("cam_preset_$slot", null)?.let { gson.fromJson(it, CamPreset::class.java) }
    } catch (_: Throwable) { null }

    fun save(c: Context, slot: Int, p: CamPreset) { prefs(c).edit().putString("cam_preset_$slot", gson.toJson(p)).apply() }
}
