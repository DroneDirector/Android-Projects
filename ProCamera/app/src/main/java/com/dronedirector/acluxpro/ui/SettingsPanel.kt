package com.dronedirector.acluxpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.camera.CameraUiState
import com.dronedirector.acluxpro.camera.ProCameraController
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(s: CameraUiState, c: ProCameraController, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Pro.PanelSolid)
                // Swallow taps so they don't close the panel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .hintedScroll(horizontal = false)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(6.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Section("SAVE PHOTOS & VIDEOS TO")
            s.storageOptions.forEach { opt ->
                ChoiceRadio(
                    title = opt.label,
                    subtitle = if (opt.removable) "Removable card · DCIM/AcluxPro" else "This device · DCIM/AcluxPro",
                    selected = s.storageVolume == opt.volume,
                ) { c.setStorage(opt.volume) }
            }
            if (s.storageOptions.none { it.removable }) {
                Text("No SD card detected. Insert one and it will appear here.", style = Pro.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp))
            }

            Section("COMPOSITION")
            ToggleRow("Histogram", "Pro modes: live brightness graph with clipping warnings", s.showHistogram) { c.toggleHistogram() }
            ToggleRow("Level", "Horizon line, turns green when straight", s.showLevel) { c.toggleLevel() }
            ToggleRow("Focus peaking", "Pro modes: highlights in-focus edges in red", s.peaking) { c.togglePeaking() }
            ToggleRow("False colour", "Pro modes: exposure as colour bands (green = mid grey, pink = skin, red = clipped)", s.falseColor) { c.toggleFalseColor() }
            ToggleRow("Zebras", "Pro modes: stripes over areas brighter than ${s.zebraLevel}%", s.zebras) { c.toggleZebras() }
            if (s.zebras) {
                Slider(
                    value = s.zebraLevel.toFloat(),
                    onValueChange = { c.setZebraLevel(it.roundToInt()) },
                    valueRange = 70f..100f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber),
                )
            }

            if (s.photoSizes.isNotEmpty()) {
                Section("PHOTO SIZE · ${s.lens?.label ?: ""} CAMERA · ${s.aspect.label}")
                s.photoSizes.forEach { ps ->
                    ChoiceRadio(
                        title = ps.label,
                        subtitle = ps.detail + if (ps.highRes) " · high-resolution mode, slower to take" else "",
                        selected = s.photoSize == ps,
                    ) { c.setPhotoSize(ps) }
                }
            }

            Section("PHOTO")
            ChoiceRow("Burst", listOf(1, 3, 5, 10), s.burst, { if (it == 1) "OFF" else "x$it" }) { c.setBurst(it) }
            ChoiceRow("Self-timer", listOf(0, 3, 5, 10), s.timerSec, { if (it == 0) "OFF" else "${it}s" }) { c.setTimer(it) }
            ChoiceRow("Interval", listOf(0, 2, 5, 10, 30), s.intervalSec, { if (it == 0) "OFF" else "${it}s" }) { c.setInterval(it) }
            ChoiceRow("Bracket (PRO)", com.dronedirector.acluxpro.camera.Bracket.entries.toList(), s.bracket, { it.label }) { c.setBracket(it) }

            Section("VIDEO")
            ToggleRow("Record audio", "Uses the microphone; levels show in the audio graph", s.audio) { c.toggleAudio() }
            ToggleRow("High bitrate", "More detail, bigger files", s.highBitrate) { c.toggleBitrate() }
            if (s.stabilizationSupported) {
                ToggleRow("Stabilization", "Electronic / optical video stabilization", s.stabilization) { c.toggleStabilization() }
            }
            ChoiceRow(
                "Codec (8-bit)",
                if (s.hevcSupported) com.dronedirector.acluxpro.camera.VideoCodec.entries.toList()
                else listOf(com.dronedirector.acluxpro.camera.VideoCodec.H264),
                s.videoCodec, { it.label },
            ) { c.setVideoCodec(it) }
            Text(
                (if (s.hevcSupported) "HEVC gives the same quality in about half the space. " else "This phone has no HEVC encoder, so H.264 is used. ") +
                    (if (s.hlgSupported) "HDR (HLG 10-bit) in Pro Video always records HEVC." else "This camera doesn't offer 10-bit HDR."),
                color = Pro.Dim, fontSize = 12.sp,
            )

            Section("AUDIO")
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Input gain", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    (if (s.audioGainDb > 0) "+" else "") + "${s.audioGainDb} dB",
                    style = Pro.Value.copy(color = if (s.audioGainDb != 0) Pro.Amber else Color.White),
                )
            }
            Slider(
                value = s.audioGainDb.toFloat(),
                onValueChange = { c.setAudioGain(it.roundToInt()) },
                valueRange = -12f..24f,
                steps = 35,
                colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber),
            )
            Text("Raise for quiet sources, lower for loud ones. The audio meter shows the adjusted level.", color = Pro.Dim, fontSize = 12.sp)
            ToggleRow("Limiter", "Rounds off peaks so loud sounds don't distort", s.audioLimiter) { c.toggleAudioLimiter() }

            Section("CLIP NAMING")
            com.dronedirector.acluxpro.camera.ClipNaming.entries.forEach { n ->
                ChoiceRadio(
                    title = n.label,
                    subtitle = if (n == com.dronedirector.acluxpro.camera.ClipNaming.CINEMA)
                        "Film-set style, e.g. ${nextClipName(s)}" else "e.g. PRO_20261015_143005_120.mp4",
                    selected = s.clipNaming == n,
                ) { c.setClipNaming(n) }
            }
            if (s.clipNaming == com.dronedirector.acluxpro.camera.ClipNaming.CINEMA) {
                ChoiceRow("Camera", listOf('A', 'B', 'C', 'D', 'E'), s.cameraLetter, { it.toString() }) { c.setCameraLetter(it) }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Reel", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Stepper("%03d".format(s.reel), onMinus = { c.setReel(s.reel - 1) }, onPlus = { c.setReel(s.reel + 1) })
                }
                Text("Next clip: ${nextClipName(s)}.mp4 · starting a new reel restarts clips at C001.", color = Pro.Dim, fontSize = 12.sp)
            }

            Section("OUTPUT")
            Text(
                "Clean feed hides every control so only the picture is on screen. Use it with a USB-C to HDMI adapter " +
                    "(on phones that support video out), screen mirroring or a capture card for a live, clean camera feed. " +
                    "Double-tap the screen to bring the controls back.",
                color = Pro.Dim, fontSize = 12.sp,
            )
            androidx.compose.material3.TextButton(onClick = {
                c.setCleanFeed(true)
                onClose()
            }) { Text("Start clean feed", color = Pro.Amber) }

            Section("APP COLOUR")
            AccentPicker()

            Section("LAYOUT")
            Text(
                "Drag the histogram, audio graph and exposure read-out anywhere on the viewfinder. Positions are remembered for portrait and landscape.",
                color = Pro.Dim, fontSize = 12.sp,
            )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.material3.TextButton(onClick = {
                OverlayLayout.reset(ctx)
                c.bumpLayoutVersion()
            }) { Text("Reset overlay positions", color = Pro.Amber) }

            Section("CONTROLS")
            ToggleRow("Volume keys shutter", "Press volume up or down to take a shot", s.volumeShutter) { c.toggleVolumeShutter() }
            ToggleRow(
                "Shutter sound",
                if (s.shutterSoundRequired) "Required by law in this region and can't be turned off"
                else "Click when taking photos, beeps when recording starts/stops and focus locks",
                s.cameraSounds || s.shutterSoundRequired,
            ) { c.toggleCameraSounds() }

            Section("THIS LENS")
            val lens = s.lens
            InfoRow("Camera", "${lens?.label ?: "-"}  (id ${lens?.id ?: "-"})")
            InfoRow("Manual ISO / shutter", if (s.manualSensor) "Yes" else "No")
            InfoRow("Manual focus", if (s.minFocusDiopters > 0f) "Yes" else "Fixed focus")
            InfoRow("RAW (DNG)", if (s.rawSupported) "Yes" else "No")
            InfoRow("Video", s.videoResolutions.joinToString(" · ") { it.label } + "  ·  " + s.fpsOptions.joinToString("/") + " fps")
            InfoRow("HLG 10-bit", if (s.hlgSupported) "Yes" else "No")
            InfoRow("Scenes", s.scenes.joinToString(" · ") { it.label })
            Spacer(Modifier.padding(4.dp))
            Text("Photos and videos save to DCIM/AcluxPro.", style = Pro.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Pro.Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val sel = o == selected
                Text(
                    label(o),
                    style = Pro.Value.copy(fontSize = 12.sp, color = if (sel) Color.Black else Color.White),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) Pro.Amber else Color.White.copy(alpha = 0.1f))
                        .clickable { onSelect(o) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(title, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun ChoiceRadio(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Pro.Amber),
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Pro.Dim, fontSize = 12.sp)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Text("Changes the yellow highlights across the app.", color = Pro.Dim, fontSize = 12.sp)
    Spacer(Modifier.size(10.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Accent.entries.forEach { a ->
            val selected = Pro.accent == a
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(a.color)
                        .then(
                            if (selected) Modifier.border(3.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                            else Modifier
                        )
                        .clickable { AccentStore.save(ctx, a) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }
                Text(a.label, color = if (selected) Color.White else Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    Spacer(Modifier.size(8.dp))
}

private fun nextClipName(s: CameraUiState): String {
    val date = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US).format(java.util.Date())
    return "%c%03dC%03d_%s_ACLX".format(java.util.Locale.US, s.cameraLetter, s.reel, s.clipNumber, date)
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("−" to onMinus, "" to {}, "+" to onPlus).forEach { (label, action) ->
            if (label.isEmpty()) {
                Text(value, style = Pro.Value.copy(fontSize = 15.sp), modifier = Modifier.padding(horizontal = 6.dp))
            } else {
                Text(
                    label,
                    style = Pro.Value.copy(fontSize = 16.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = action)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
