package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Collections
import java.util.WeakHashMap

/**
 * Gear → Preferences → Audio hardware: which microphone and which speaker / headphones / interface
 * the app uses. Unplugged, the app goes back to the phone's own and says so. Own code.
 */
object AudioHardware {
    private const val PREFS = "procam"
    var version by mutableIntStateOf(0)
        private set
    /** Shown once when the chosen device is unplugged. */
    var notice by mutableStateOf<String?>(null)
    private var started = false
    @Volatile private var appCtx: Context? = null
    private val players = Collections.newSetFromMap(WeakHashMap<androidx.media3.exoplayer.ExoPlayer, Boolean>())

    fun typeName(t: Int): String = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in microphone"; AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "built-in speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"; AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"; AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"; AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"; AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"; AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (calls)"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"; AudioDeviceInfo.TYPE_LINE_ANALOG -> "line"; AudioDeviceInfo.TYPE_LINE_DIGITAL -> "digital line"
        AudioDeviceInfo.TYPE_AUX_LINE -> "aux line"; AudioDeviceInfo.TYPE_DOCK -> "dock"; 26 -> "Bluetooth LE"; 27 -> "Bluetooth LE speaker"
        else -> "other"
    }

    private val usefulOut = setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE, AudioDeviceInfo.TYPE_DOCK, 26, 27)
    private val usefulIn = setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, 26)

    fun key(d: AudioDeviceInfo) = "${d.type}|${d.productName}|${d.address}"
    fun label(d: AudioDeviceInfo) = "${d.productName} (${typeName(d.type)})"

    fun devices(c: Context, inputs: Boolean): List<AudioDeviceInfo> {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(if (inputs) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in (if (inputs) usefulIn else usefulOut) }.distinctBy { key(it) }
    }

    private fun pref(inputs: Boolean) = if (inputs) "audio_in" else "audio_out"
    fun chosenKey(c: Context, inputs: Boolean): String? = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(pref(inputs), null)
    fun chosen(c: Context, inputs: Boolean): AudioDeviceInfo? = chosenKey(c, inputs)?.let { k -> devices(c, inputs).firstOrNull { key(it) == k } }

    fun set(c: Context, inputs: Boolean, d: AudioDeviceInfo?) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (d == null) remove(pref(inputs)) else putString(pref(inputs), key(d)) }.apply()
        if (!inputs) applyAll(c)
        version++
    }

    /** Players in the editors follow the chosen output. */
    fun track(c: Context, p: androidx.media3.exoplayer.ExoPlayer) {
        start(c)
        players += p
        try { p.setPreferredAudioDevice(chosen(c, false)) } catch (_: Throwable) {}
    }
    private fun applyAll(c: Context) { val d = chosen(c, false); for (p in players.toList()) try { p.setPreferredAudioDevice(d) } catch (_: Throwable) {} }

    /** Microphone recordings (meter, 24-bit WAV, cast sound) use the chosen input. */
    fun applyTo(r: android.media.AudioRecord) { val c = appCtx ?: return; try { chosen(c, true)?.let { r.setPreferredDevice(it) } } catch (_: Throwable) {} }

    /** Watches for the chosen devices being unplugged. */
    fun start(c: Context) {
        if (started) return
        started = true
        val app = c.applicationContext
        appCtx = app
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                for (inputs in listOf(false, true)) {
                    val k = chosenKey(app, inputs) ?: continue
                    val gone = removed.firstOrNull { key(it) == k } ?: continue
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(pref(inputs)).apply()
                    if (!inputs) applyAll(app)
                    version++
                    notice = "${gone.productName} was disconnected. AlcorCut Pro uses the phone's own " + (if (inputs) "microphone" else "speaker / headphones") + " again."
                }
            }
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { version++ }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }
}

/** "… was disconnected" — once, with OK. */
@Composable
fun AudioHardwareNotice() {
    val n = AudioHardware.notice ?: return
    AlertDialog(
        onDismissRequest = { AudioHardware.notice = null }, containerColor = Pro.PanelSolid,
        title = { Text("Audio device disconnected", color = Color.White) },
        text = { Text(n, color = Pro.Dim) },
        confirmButton = { TextButton(onClick = { AudioHardware.notice = null }) { Text("OK", color = Pro.Amber) } },
    )
}

@Composable
fun AudioHardwareSettings() {
    val context = LocalContext.current
    remember { AudioHardware.start(context); 0 }
    val v = AudioHardware.version
    Column(Modifier.fillMaxWidth()) {
        Text("Pick what AlcorCut Pro records from and plays through. Unplug it and the app goes back to the phone's own, and tells you.", color = Pro.Dim, fontSize = 12.sp)
        for (inputs in listOf(true, false)) {
            Text(if (inputs) "MICROPHONE" else "PLAYBACK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            val list = remember(v) { AudioHardware.devices(context, inputs) }
            val cur = remember(v) { AudioHardware.chosenKey(context, inputs) }
            @Composable
            fun Opt(label: String, on: Boolean, pick: () -> Unit) {
                Text(label, color = if (on) Color.Black else Color.White, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f)).clickable(onClick = pick).padding(10.dp))
            }
            Opt("Automatic (what Android picks)", cur == null) { AudioHardware.set(context, inputs, null) }
            for (d in list) Opt(AudioHardware.label(d), cur == AudioHardware.key(d)) { AudioHardware.set(context, inputs, d) }
        }
        Text("The microphone choice is used where Android lets an app choose (the audio meter, 24-bit WAV and the TV's sound); video recordings follow Android's choice, which normally is the device you plugged in.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
