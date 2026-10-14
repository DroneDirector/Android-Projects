package com.dronedirector.procam.ui

import androidx.compose.foundation.background
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
import com.dronedirector.procam.camera.CameraUiState
import com.dronedirector.procam.camera.ProCameraController
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
                .verticalScroll(rememberScrollState())
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
                    subtitle = if (opt.removable) "Removable card · DCIM/ProCam" else "This device · DCIM/ProCam",
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
            ChoiceRow("Bracket (PRO)", com.dronedirector.procam.camera.Bracket.entries.toList(), s.bracket, { it.label }) { c.setBracket(it) }

            Section("VIDEO")
            ToggleRow("Record audio", "Uses the microphone; levels show in the audio graph", s.audio) { c.toggleAudio() }
            ToggleRow("High bitrate", "More detail, bigger files", s.highBitrate) { c.toggleBitrate() }
            if (s.stabilizationSupported) {
                ToggleRow("Stabilization", "Electronic / optical video stabilization", s.stabilization) { c.toggleStabilization() }
            }

            Section("CONTROLS")
            ToggleRow("Volume keys shutter", "Press volume up or down to take a shot", s.volumeShutter) { c.toggleVolumeShutter() }

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
            Text("Photos and videos save to DCIM/ProCam.", style = Pro.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp))
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
