package com.dronedirector.veyrocutpro.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecList
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Gear → System Info: the phone's hardware and drivers, for support and for knowing what it can do. Own code. */
object SystemInfo {
    class Section(val title: String, val rows: List<Pair<String, String>>)

    private fun gb(b: Long) = String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)

    private fun gpu(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val v = IntArray(2); EGL14.eglInitialize(dpy, v, 0, v, 1)
            val cfgs = arrayOfNulls<android.opengl.EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
            val ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(0x3098, 2, EGL14.EGL_NONE), 0)
            val surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            out += "Graphics chip" to (GLES20.glGetString(GLES20.GL_RENDERER) ?: "?")
            out += "Graphics maker" to (GLES20.glGetString(GLES20.GL_VENDOR) ?: "?")
            out += "OpenGL ES driver" to (GLES20.glGetString(GLES20.GL_VERSION) ?: "?")
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx); EGL14.eglTerminate(dpy)
        } catch (_: Throwable) { out += "Graphics chip" to "?" }
        return out
    }

    fun collect(c: Context): List<Section> {
        val s = ArrayList<Section>()
        s += Section("Device", buildList {
            add("Maker" to Build.MANUFACTURER); add("Model" to Build.MODEL); add("Device" to Build.DEVICE)
            add("Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            add("Security patch" to Build.VERSION.SECURITY_PATCH)
            add("Build" to Build.DISPLAY)
            try { add("App version" to (c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "")) } catch (_: Throwable) {}
        })
        s += Section("Processor", buildList {
            if (Build.VERSION.SDK_INT >= 31) { add("Chip" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}") }
            add("Board" to Build.BOARD); add("Hardware" to Build.HARDWARE)
            add("Cores" to Runtime.getRuntime().availableProcessors().toString())
            val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { i ->
                try { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000 } catch (_: Throwable) { null }
            }.distinct().sortedDescending()
            if (freqs.isNotEmpty()) add("Top speeds" to freqs.joinToString(" / ") { "$it MHz" })
            add("Instruction sets" to Build.SUPPORTED_ABIS.joinToString(", "))
        })
        s += Section("Graphics", buildList {
            addAll(gpu())
            val pm = c.packageManager
            val vk = pm.systemAvailableFeatures.firstOrNull { it.name == android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            if (vk != null) add("Vulkan" to "${vk.version shr 22}.${(vk.version shr 12) and 0x3FF}")
        })
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        s += Section("Memory & storage", listOf(
            "Memory" to "${gb(mi.totalMem)} (${gb(mi.availMem)} free)",
            "App memory limit" to "${am.largeMemoryClass} MB",
            "Storage" to "${gb(st.totalBytes)} (${gb(st.availableBytes)} free)",
        ))
        val dm = c.resources.displayMetrics
        s += Section("Display", buildList {
            add("Resolution" to "${dm.widthPixels} × ${dm.heightPixels} (${dm.densityDpi} dpi)")
            try {
                val d = (c.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
                add("Refresh rates" to d.supportedModes.map { it.refreshRate.toInt() }.distinct().sorted().joinToString(", ") { "$it Hz" })
                val hdr = d.hdrCapabilities?.supportedHdrTypes?.map {
                    when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "type $it" }
                }.orEmpty()
                add("HDR" to hdr.ifEmpty { listOf("no") }.joinToString(", "))
                if (Build.VERSION.SDK_INT >= 26) add("Wide colour" to if (d.isWideColorGamut) "yes" else "no")
            } catch (_: Throwable) {}
        })
        try {
            val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(id)
                val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) { CameraMetadata.LENS_FACING_FRONT -> "Front"; CameraMetadata.LENS_FACING_BACK -> "Back"; else -> "External" }
                val level = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"; CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"; CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"; else -> "External"
                }
                val px = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
                val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString(", ") { String.format(java.util.Locale.US, "%.1f mm", it) } ?: ""
                s += Section("Camera $id ($facing)", buildList {
                    if (px != null) add("Sensor" to String.format(java.util.Locale.US, "%.1f MP (%d × %d)", px.width * px.height / 1e6, px.width, px.height))
                    add("Lens" to focal)
                    add("Camera2 support" to level)
                    add("RAW" to if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps) "yes" else "no")
                    add("Manual controls" to if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps) "yes" else "no")
                    if (Build.VERSION.SDK_INT >= 33) add("10-bit HDR video" to if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT in caps) "yes" else "no")
                })
            }
        } catch (_: Throwable) {}
        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.supportedTypes.any { it.startsWith("video/") } && (Build.VERSION.SDK_INT < 29 || info.isHardwareAccelerated)
            }
            fun names(enc: Boolean) = list.filter { it.isEncoder == enc }.flatMap { i -> i.supportedTypes.filter { it.startsWith("video/") }.map { it.removePrefix("video/").uppercase() } }.distinct().sorted().joinToString(", ")
            s += Section("Video (hardware)", listOf("Encoders" to names(true), "Decoders" to names(false)))
        } catch (_: Throwable) {}
        try {
            val au = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            fun devs(flag: Int) = au.getDevices(flag).map { "${it.productName} (${AudioHardware.typeName(it.type)})" }.distinct().joinToString(", ")
            s += Section("Audio", listOf(
                "Inputs" to devs(AudioManager.GET_DEVICES_INPUTS),
                "Outputs" to devs(AudioManager.GET_DEVICES_OUTPUTS),
                "Sample rate" to (au.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "?") + " Hz",
            ))
        } catch (_: Throwable) {}
        try {
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                val health = when (b.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) { BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Too hot"; BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"; else -> "—" }
                val pm = c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                s += Section("Battery & temperature", buildList {
                    add("Battery" to "$lvl %"); add("Battery temperature" to "$temp °C"); add("Battery health" to health)
                    if (Build.VERSION.SDK_INT >= 29) add("Heat" to when (pm.currentThermalStatus) { 0 -> "Normal"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"; 4 -> "Critical"; else -> "Shutting down" })
                })
            }
        } catch (_: Throwable) {}
        return s
    }

    fun asText(l: List<Section>) = l.joinToString("\n\n") { sec -> sec.title.uppercase() + "\n" + sec.rows.joinToString("\n") { "${it.first}: ${it.second}" } }
}

@Composable
fun SystemInfoSection() {
    val context = LocalContext.current
    val info by produceState<List<SystemInfo.Section>?>(null) { value = withContext(Dispatchers.IO) { try { SystemInfo.collect(context) } catch (_: Throwable) { emptyList() } } }
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        val l = info
        if (l == null) Text("Reading the hardware…", color = Pro.Dim, fontSize = 12.sp)
        else {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("System info", SystemInfo.asText(l)))
                copied = true
            }) { Text(if (copied) "Copied" else "Copy all", color = Pro.Amber) }
            for (sec in l) {
                Text(sec.title.uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for ((k, v) in sec.rows) Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(k, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.width(130.dp))
                    Text(v.ifBlank { "—" }, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
