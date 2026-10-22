package com.dronedirector.veyrocutpro.camera

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.os.Build

/**
 * How capable this phone is, for features that need a strong one: memory, processor cores, the
 * Android "media performance class" (set by the maker for phones that handle demanding video), the
 * graphics chip and the video encoder's top bitrate. Checked once.
 */
object DeviceClass {
    @Volatile private var checked = false
    var ramGb = 0.0; private set
    var cores = 0; private set
    var lowRam = false; private set
    var perfClass = 0; private set
    var strongGpu = false; private set
    var maxEncodeBitrate = 0; private set

    fun check(c: Context) {
        if (checked) return
        checked = true
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        ramGb = mi.totalMem / 1e9
        lowRam = am.isLowRamDevice
        cores = Runtime.getRuntime().availableProcessors()
        perfClass = if (Build.VERSION.SDK_INT >= 31) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0
        val pm = c.packageManager
        val gles32 = am.deviceConfigurationInfo.reqGlEsVersion >= 0x30002
        val vulkan = try { pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) } catch (_: Throwable) { false }
        strongGpu = gles32 && vulkan
        maxEncodeBitrate = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
                .flatMap { info -> info.supportedTypes.filter { it == "video/avc" || it == "video/hevc" }.map { t -> info.getCapabilitiesForType(t) } }
                .maxOfOrNull { it.videoCapabilities?.bitrateRange?.upper ?: 0 } ?: 0
        } catch (_: Throwable) { 0 }
    }

    /** 24-bit uncompressed audio next to video: a mid-range phone or better. */
    val proAudio: Boolean get() = !lowRam && ramGb >= 3.5 && cores >= 6

    /**
     * Extreme bitrate: flagship-class phones only — plenty of memory, eight cores, a strong graphics
     * chip and an encoder that can actually write that much (or the maker's own "media performance
     * class" 13+ rating).
     */
    fun extremeBitrate(highBitrate: Int): Boolean =
        !lowRam && ramGb >= 7.5 && cores >= 8 && strongGpu &&
            (perfClass >= Build.VERSION_CODES.TIRAMISU || maxEncodeBitrate >= (highBitrate * 1.6).toInt())

    /** Short reason shown when a feature is greyed out. */
    const val WHY = "Needs a more powerful phone (memory, processor and graphics)"
}
