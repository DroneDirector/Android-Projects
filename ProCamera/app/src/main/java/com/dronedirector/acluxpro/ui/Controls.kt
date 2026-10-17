package com.dronedirector.acluxpro.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.camera.AspectMode
import com.dronedirector.acluxpro.camera.Bracket
import com.dronedirector.acluxpro.camera.Filter
import com.dronedirector.acluxpro.camera.FrameGuide
import com.dronedirector.acluxpro.camera.CameraUiState
import com.dronedirector.acluxpro.camera.CaptureMode
import com.dronedirector.acluxpro.camera.FlashSetting
import com.dronedirector.acluxpro.camera.GridMode
import com.dronedirector.acluxpro.camera.ProCameraController
import com.dronedirector.acluxpro.camera.ProParam
import com.dronedirector.acluxpro.camera.Stops
import com.dronedirector.acluxpro.camera.VideoProfile
import com.dronedirector.acluxpro.camera.WhiteBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

private fun <T> List<T>.next(current: T): T = if (isEmpty()) current else this[(indexOf(current) + 1).mod(size)]

// ------------------------------------------------------------------ quick settings bar

@Composable
private fun QuickItem(label: String, value: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = Pro.Label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = Pro.Value.copy(color = if (active) Pro.Amber else Color.White), maxLines = 1)
    }
}

@Composable
private fun QuickItems(s: CameraUiState, c: ProCameraController, onSettings: () -> Unit) {
    val mode = s.mode
    if (mode == CaptureMode.LIVE) {
        val ls by c.live.state.collectAsState()
        QuickItem("PLATFORM", ls.platform.label.uppercase(), active = true, enabled = !ls.isBusy) { c.live.showSetup(true) }
        QuickItem("MIC", if (ls.muted) "OFF" else "ON", active = ls.muted) { c.live.toggleMute() }
        QuickItem("TORCH", if (ls.torch) "ON" else "OFF", active = ls.torch) { c.live.toggleTorch() }
        QuickItem("FORMAT", if (ls.vertical) "9:16" else "16:9", enabled = !ls.isBusy) { c.live.setVertical(!ls.vertical) }
        QuickItem("QUALITY", ls.quality.label, enabled = !ls.isBusy) {
            c.live.setQuality(com.dronedirector.acluxpro.camera.LiveQuality.entries.next(ls.quality))
        }
        return
    }
    if (mode.isLapse || mode.isSlowMo || mode == CaptureMode.BURST) {
        SmartModeItems(s, c)
    } else if (!s.isVideo) {
        if (s.hasFlash && mode != CaptureMode.PANORAMA) {
            QuickItem("FLASH", s.flash.label, active = s.flash != FlashSetting.OFF) {
                c.setFlash(FlashSetting.entries.next(s.flash))
            }
        }
        if (mode != CaptureMode.PANORAMA) {
            QuickItem("TIMER", if (s.timerSec == 0) "OFF" else "${s.timerSec}s", active = s.timerSec > 0) {
                c.setTimer(listOf(0, 3, 5, 10).next(s.timerSec))
            }
            QuickItem("RATIO", s.aspect.label) { c.setAspect(AspectMode.entries.next(s.aspect)) }
            val size = s.photoSize
            if (size != null && s.photoSizes.size > 1 && !(mode == CaptureMode.PRO_PHOTO && s.photoFormat == com.dronedirector.acluxpro.camera.PhotoFormat.RAW)) {
                QuickItem("SIZE", size.label.replace(" ", ""), active = size == s.photoSizes.first()) {
                    c.setPhotoSize(s.photoSizes.next(size))
                }
            }
            QuickItem("INTERVAL", if (s.intervalSec == 0) "OFF" else "${s.intervalSec}s", active = s.intervalSec > 0) {
                c.setInterval(listOf(0, 2, 5, 10, 30, 60).next(s.intervalSec))
            }
        }
        if (mode == CaptureMode.PRO_PHOTO) {
            val formats = c.photoFormats()
            QuickItem("FORMAT", s.photoFormat.label, active = s.photoFormat != com.dronedirector.acluxpro.camera.PhotoFormat.JPEG, enabled = formats.size > 1) {
                c.setPhotoFormat(formats.next(s.photoFormat))
            }
            QuickItem("BRACKET", s.bracket.label, active = s.bracket != Bracket.OFF) {
                c.setBracket(Bracket.entries.next(s.bracket))
            }
            QuickItem("F.COLOR", if (s.falseColor) "ON" else "OFF", active = s.falseColor) { c.toggleFalseColor() }
        }
        if (mode == CaptureMode.PHOTO && s.scenes.size > 1) {
            QuickItem("SCENE", s.scene.label, active = s.scene != com.dronedirector.acluxpro.camera.Scene.STANDARD) {
                c.setScene(s.scenes.next(s.scene))
            }
        }
        if (mode != CaptureMode.PANORAMA && s.burst > 1) QuickItem("BURST", "x${s.burst}", active = true) { c.setBurst(1) }
    } else {
        if (s.hasFlash) {
            QuickItem("TORCH", if (s.flash == FlashSetting.TORCH) "ON" else "OFF", active = s.flash == FlashSetting.TORCH) {
                c.setFlash(if (s.flash == FlashSetting.TORCH) FlashSetting.OFF else FlashSetting.TORCH)
            }
        }
        QuickItem("RES", s.videoRes.label, enabled = !s.recording) { c.setVideoRes(s.videoResolutions.next(s.videoRes)) }
        QuickItem("FPS", "${s.fps}", active = s.fps != 30, enabled = !s.recording) { c.setFps(s.fpsOptions.next(s.fps)) }
        QuickItem("TIMER", if (s.timerSec == 0) "OFF" else "${s.timerSec}s", active = s.timerSec > 0, enabled = !s.recording) {
            c.setTimer(listOf(0, 3, 5, 10).next(s.timerSec))
        }
        if (s.audio) {
            QuickItem("MONITOR", if (s.headphoneMonitor) (if (s.headphones != null) "ON" else "NO HP") else "OFF", active = s.headphoneMonitor && s.headphones != null) { c.toggleHeadphoneMonitor() }
            if (mode != CaptureMode.PRO_VIDEO) {
                QuickItem("GAIN", (if (s.audioGainDb > 0) "+" else "") + "${s.audioGainDb}dB", active = s.audioGainDb != 0) {
                    c.setAudioGain(listOf(-12, -6, 0, 6, 12, 18, 24).let { l -> l.firstOrNull { it > s.audioGainDb } ?: l.first() })
                }
            }
        }
        if (s.stabilizationSupported) {
            QuickItem("STAB", if (s.stabilization) "ON" else "OFF", active = s.stabilization, enabled = !s.recording) { c.toggleStabilization() }
        }
        if (mode == CaptureMode.PRO_VIDEO) {
            val profiles = buildList {
                add(VideoProfile.STANDARD)
                add(VideoProfile.FLAT)
                add(VideoProfile.DLOG)
                add(VideoProfile.DLOG_M)
                if (s.hlgSupported) add(VideoProfile.HLG)
            }
            QuickItem("PROFILE", s.profile.label, active = s.profile != VideoProfile.STANDARD, enabled = !s.recording) {
                c.setProfile(profiles.next(s.profile))
            }
            if (s.profile.isLog) {
                QuickItem("ASSIST", if (s.logAssist) "ON" else "OFF", active = s.logAssist) { c.toggleLogAssist() }
            }
            QuickItem("BITRATE", if (s.highBitrate) "HIGH" else "STD", active = s.highBitrate, enabled = !s.recording) { c.toggleBitrate() }
            QuickItem("F.COLOR", if (s.falseColor) "ON" else "OFF", active = s.falseColor) { c.toggleFalseColor() }
            QuickItem("GUIDE", s.guide.label, active = s.guide != FrameGuide.OFF) { c.setGuide(FrameGuide.entries.next(s.guide)) }
            val hlg = s.profile == VideoProfile.HLG
            if (s.hevcSupported || hlg) {
                QuickItem("CODEC", if (hlg) "HEVC 10" else s.videoCodec.label, active = hlg || s.videoCodec != com.dronedirector.acluxpro.camera.VideoCodec.H264, enabled = !s.recording && !hlg && s.hevcSupported) {
                    c.setVideoCodec(com.dronedirector.acluxpro.camera.VideoCodec.entries.next(s.videoCodec))
                }
            }
            QuickItem("GAIN", (if (s.audioGainDb > 0) "+" else "") + "${s.audioGainDb}dB", active = s.audioGainDb != 0) {
                c.setAudioGain(listOf(-12, -6, 0, 6, 12, 18, 24).let { l -> l.firstOrNull { it > s.audioGainDb } ?: l.first() })
            }
        }
    }
    if (s.mode != CaptureMode.PANORAMA) {
        QuickItem("GRID", s.grid.label, active = s.grid != GridMode.OFF) { c.setGrid(GridMode.entries.next(s.grid)) }
    }
    // Quick switches for privacy blur and the video title (can be hidden in the Panels menu).
    val x = com.dronedirector.acluxpro.camera.CaptureExtras
    val qctx = androidx.compose.ui.platform.LocalContext.current
    if (panelOn("cap.qblur") && !mode.isSlowMo) {
        QuickItem("BLUR", if (x.faceBlur) "ON" else "OFF", active = x.faceBlur, enabled = !s.recording) { c.setFaceBlur(!x.faceBlur) }
    }
    if (s.isVideo && panelOn("cap.qtitle")) {
        QuickItem("TITLE", if (x.titleOn) "ON" else "OFF", active = x.titleOn, enabled = !s.recording) {
            x.titleOn = !x.titleOn; x.save(qctx)
            if (x.titleOn && x.titleText.isBlank()) c.toast("Type your title in Settings → PRIVACY & TITLE")
        }
    }
}

@Composable
private fun SettingsGear(onSettings: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onSettings)
            .padding(10.dp)
    ) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun QuickBar(s: CameraUiState, c: ProCameraController, vertical: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier, panelsButton: @Composable () -> Unit = {}) {
    // The panels icon and settings gear stay pinned; the other items scroll if they don't fit.
    if (vertical) {
        Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).hintedScroll(horizontal = false),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) { QuickItems(s, c, onSettings) }
            panelsButton()
            SettingsGear(onSettings)
        }
    } else {
        Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).hintedScroll(horizontal = true),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            ) { QuickItems(s, c, onSettings) }
            panelsButton()
            SettingsGear(onSettings)
        }
    }
}

// ------------------------------------------------------------------ pro panel

@Composable
fun ProPanel(s: CameraUiState, c: ProCameraController, isoShown: Int?, shutterShown: Long?, modifier: Modifier = Modifier) {
    val sceneLocked = s.sceneActive
    Column(
        modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = s.showFilters, enter = fadeIn(), exit = fadeOut()) {
            FilterStrip(s, c, Modifier.padding(bottom = 6.dp))
        }
        AnimatedVisibility(
            visible = s.activeParam != null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Pro.Panel)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                s.activeParam?.let { ParamEditor(it, s, c, isoShown, shutterShown) }
            }
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Pro.Panel)
                .hintedScroll(horizontal = true)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val manualOk = s.manualSensor && !sceneLocked
            ParamChip(ProParam.ISO, if (s.manualIso) "${s.iso}" else "A ${isoShown ?: "--"}", s, manualOk, s.manualIso, c)
            val angleOn = com.dronedirector.acluxpro.camera.CaptureExtras.shutterAngle && s.isVideo
            fun sh(ns: Long) = if (angleOn) "${com.dronedirector.acluxpro.camera.CaptureExtras.angleOf(ns, s.fps)}°" else Stops.shutterLabel(ns)
            ParamChip(ProParam.SHUTTER, if (s.manualShutter) sh(s.shutterNs) else "A ${shutterShown?.let { sh(it) } ?: "--"}", s, manualOk, s.manualShutter, c)
            ParamChip(ProParam.FOCUS, if (s.manualFocus) Stops.focusLabel(s.focusDiopters) else "AF", s, s.minFocusDiopters > 0f && !sceneLocked, s.manualFocus, c)
            ParamChip(ProParam.WB, s.whiteBalance.label + if (s.awbLock) "·L" else "", s, !sceneLocked, s.whiteBalance != WhiteBalance.AUTO || s.awbLock, c)
            ParamChip(ProParam.EV, "%+.1f".format(s.ev * s.evStep), s, s.evMax > s.evMin, s.ev != 0, c)
            ParamChip(ProParam.ZOOM, Stops.zoomLabel(s.zoom), s, s.maxZoom > s.minZoom, abs(s.zoom - 1f) > 0.05f, c)
            FilterChip(s, c)
        }
    }
}

@Composable
private fun ParamChip(p: ProParam, value: String, s: CameraUiState, enabled: Boolean, manual: Boolean, c: ProCameraController) {
    val selected = s.activeParam == p
    Column(
        Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Pro.Amber.copy(alpha = 0.18f) else Color.Transparent)
            .clickable {
                if (enabled) c.setActiveParam(p)
                else c.toast(
                    when {
                        s.sceneActive -> "Turn the scene mode off to use manual controls"
                        p == ProParam.FOCUS -> "This lens has fixed focus"
                        else -> "Manual ${p.label} isn't supported on this lens"
                    }
                )
            }
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .widthIn(min = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.label, style = Pro.Label.copy(color = if (selected) Pro.Amber else Pro.Dim))
        Text(value, style = Pro.Value.copy(fontSize = 12.sp, color = if (manual) Pro.Amber else Color.White), maxLines = 1)
    }
}

@Composable
private fun proSliderColors() = SliderDefaults.colors(
    thumbColor = Pro.Amber,
    activeTrackColor = Pro.Amber,
    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun AutoToggle(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = Pro.Value.copy(fontSize = 12.sp, color = if (on) Color.Black else Color.White),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) Pro.Amber else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun StopSlider(count: Int, index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (count < 2) return
    Slider(
        value = index.toFloat().coerceIn(0f, (count - 1).toFloat()),
        onValueChange = { v ->
            val i = v.roundToInt().coerceIn(0, count - 1)
            if (i != index) onIndex(i)
        },
        valueRange = 0f..(count - 1).toFloat(),
        steps = (count - 2).coerceAtLeast(0),
        colors = proSliderColors(),
        modifier = modifier,
    )
}

@Composable
private fun EditorRow(title: String, value: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(64.dp)) {
            Text(title, style = Pro.Label)
            Text(value, style = Pro.Value.copy(fontSize = 15.sp, color = Pro.Amber), maxLines = 1)
        }
        content()
    }
}

@Composable
private fun ParamEditor(p: ProParam, s: CameraUiState, c: ProCameraController, isoShown: Int?, shutterShown: Long?) {
    if ((p == ProParam.ISO || p == ProParam.SHUTTER) && !s.manualSensor) {
        Text(
            "This camera doesn't allow manual ${if (p == ProParam.ISO) "ISO" else "shutter speed"}. Exposure stays automatic; use EV to adjust brightness.",
            style = Pro.Label.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White),
        )
        return
    }
    if (p == ProParam.FOCUS && s.minFocusDiopters <= 0f) {
        Text("This lens has fixed focus.", style = Pro.Label.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White))
        return
    }
    when (p) {
        ProParam.ISO -> {
            val shown = if (s.manualIso) s.iso else (isoShown ?: s.iso)
            val idx = Stops.nearestIndex(s.isoStops, shown) { a, b -> abs(ln(a.toDouble() / b)) }
            EditorRow("ISO", "$shown") {
                AutoToggle("AUTO", !s.manualIso) { c.setIsoAuto() }
                Spacer(Modifier.width(8.dp))
                StopSlider(s.isoStops.size, idx, { c.setIso(s.isoStops[it]) }, Modifier.weight(1f))
            }
        }
        ProParam.SHUTTER -> {
            val shown = if (s.manualShutter) s.shutterNs else (shutterShown ?: s.shutterNs)
            // Slider runs fast → slow, so put long exposures on the right.
            val stops = s.shutterStops
            val idx = Stops.nearestIndex(stops, shown) { a, b -> abs(ln(a.toDouble() / b)) }
            val x = com.dronedirector.acluxpro.camera.CaptureExtras
            val angleOn = x.shutterAngle && s.isVideo
            EditorRow("SHUTTER", if (angleOn) "${x.angleOf(shown, s.fps)}° · ${Stops.shutterLabel(shown)}" else Stops.shutterLabel(shown)) {
                AutoToggle("AUTO", !s.manualShutter) { c.setShutterAuto() }
                Spacer(Modifier.width(8.dp))
                if (s.isVideo) {
                    AutoToggle("180°", s.manualShutter && kotlin.math.abs(x.angleOf(s.shutterNs, s.fps) - 180) <= 3) {
                        c.setShutter(x.ns180(s.fps).coerceIn(stops.minOrNull() ?: 0L, stops.maxOrNull() ?: Long.MAX_VALUE))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                StopSlider(stops.size, idx, { c.setShutter(stops[it]) }, Modifier.weight(1f))
            }
        }
        ProParam.FOCUS -> Column {
            val shown = if (s.manualFocus) s.focusDiopters else (s.liveFocusDiopters ?: 0f)
            EditorRow("FOCUS", Stops.focusLabel(shown)) {
                AutoToggle("AF", !s.manualFocus) { c.setFocusAuto() }
                Spacer(Modifier.width(8.dp))
                Text("∞", style = Pro.Value)
                // Square-root mapping gives finer control near infinity.
                val max = s.minFocusDiopters.coerceAtLeast(0.01f)
                Slider(
                    value = kotlin.math.sqrt((shown / max).coerceIn(0f, 1f)),
                    onValueChange = { v -> c.setFocus(v * v * max) },
                    colors = proSliderColors(),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                Text("MACRO", style = Pro.Label)
            }
            // Rack focus: store two focus points, then glide between them.
            val x = com.dronedirector.acluxpro.camera.CaptureExtras
            var rackSec by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(2) }
            Row(Modifier.padding(top = 4.dp).hintedScroll(horizontal = true), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("RACK", style = Pro.Label)
                AutoToggle(if (x.focusA != null) "SET A ✓" else "SET A", x.focusA != null) { x.focusA = shown }
                AutoToggle(if (x.focusB != null) "SET B ✓" else "SET B", x.focusB != null) { x.focusB = shown }
                x.focusA?.let { a -> AutoToggle("→ A", false) { c.rackFocus(a, rackSec * 1000L) } }
                x.focusB?.let { b -> AutoToggle("→ B", false) { c.rackFocus(b, rackSec * 1000L) } }
                for (sec in listOf(1, 2, 4)) AutoToggle("${sec}s", rackSec == sec) { rackSec = sec }
            }
        }
        ProParam.WB -> {
            Column {
                Row(
                    Modifier.hintedScroll(horizontal = true),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WhiteBalance.entries.forEach { wb ->
                        AutoToggle(wb.label, s.whiteBalance == wb) { c.setWhiteBalance(wb) }
                    }
                    Spacer(Modifier.width(6.dp))
                    AutoToggle(if (s.awbLock) "AWB-L ✓" else "AWB-L", s.awbLock) { c.toggleAwbLock() }
                }
            }
        }
        ProParam.EV -> {
            val count = s.evMax - s.evMin + 1
            EditorRow("EV", "%+.1f".format(s.ev * s.evStep)) {
                AutoToggle("0", s.ev == 0) { c.setEv(0) }
                Spacer(Modifier.width(8.dp))
                StopSlider(count, s.ev - s.evMin, { c.setEv(it + s.evMin) }, Modifier.weight(1f))
                if (!s.manualExposure) {
                    Spacer(Modifier.width(8.dp))
                    AutoToggle("AE-L", s.aeLock) { c.toggleAeLock() }
                }
            }
        }
        ProParam.ZOOM -> {
            val lo = ln(s.minZoom.coerceAtLeast(0.1f))
            val hi = ln(s.maxZoom.coerceAtLeast(s.minZoom + 0.01f))
            EditorRow("ZOOM", Stops.zoomLabel(s.zoom)) {
                listOf(1f, 2f, 5f).filter { it in s.minZoom..s.maxZoom }.forEach { z ->
                    AutoToggle(Stops.zoomLabel(z), abs(s.zoom - z) < 0.05f) { c.setZoom(z) }
                    Spacer(Modifier.width(4.dp))
                }
                Slider(
                    value = ((ln(s.zoom) - lo) / (hi - lo)).coerceIn(0f, 1f),
                    onValueChange = { v -> c.setZoom(kotlin.math.exp(lo + v * (hi - lo))) },
                    colors = proSliderColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ capture controls

@Composable
fun ShutterButton(
    s: CameraUiState,
    size: Dp,
    liveMode: Boolean = false,
    liveBusy: Boolean = false,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val clickPressed by interaction.collectIsPressedAsState()
    var holdPressed by remember { mutableStateOf(false) }
    val pressed = clickPressed || holdPressed
    val holdScope = androidx.compose.runtime.rememberCoroutineScope()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutterScale")
    val recording = (s.isVideo && s.recording) || s.panoActive || s.intervalRunning || (liveMode && liveBusy)
    val inner by animateDpAsState(if (recording) size * 0.36f else size * 0.78f, label = "shutterInner")
    val corner by animateDpAsState(if (recording) 6.dp else size, label = "shutterCorner")
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (onHoldStart == null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier.pointerInput(Unit) {
                    // Tap = one burst; press and hold = keep shooting until released.
                    detectTapGestures(
                        onPress = {
                            holdPressed = true
                            var held = false
                            val job = holdScope.launch {
                                kotlinx.coroutines.delay(350)
                                held = true
                                onHoldStart()
                            }
                            val released = tryAwaitRelease()
                            job.cancel()
                            holdPressed = false
                            if (held) onHoldEnd?.invoke() else if (released) onClick()
                        },
                    )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = if (s.capturing || s.countdown > 0 || s.holdBursting) Pro.Amber else Color.White
            drawCircle(ring, radius = this.size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.5.dp.toPx()))
            val fill = when {
                s.isVideo || s.panoActive || s.intervalRunning || liveMode -> Pro.Red
                s.capturing -> Pro.Amber
                else -> Color.White
            }
            val d = inner.toPx()
            val r = corner.toPx().coerceAtMost(d / 2)
            drawRoundRect(
                fill,
                topLeft = Offset((this.size.width - d) / 2, (this.size.height - d) / 2),
                size = GSize(d, d),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

@Composable
fun GalleryThumb(uri: Uri?, size: Dp, onOpen: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        thumb = if (uri != null && Build.VERSION.SDK_INT >= 29) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        } else null
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onOpen() },
        contentAlignment = Alignment.Center,
    ) {
        val t = thumb
        if (t != null) {
            Image(t, contentDescription = "Last capture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(size * 0.45f))
        }
    }
}

@Composable
private fun RoundIcon(enabled: Boolean = true, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** REGULAR / SMART switch: each shows its own set of modes. */
@Composable
private fun ModeGroupToggle(s: CameraUiState, c: ProCameraController, vertical: Boolean) {
    val enabled = !s.recording && !s.panoActive && !s.stitching && !s.processing
    val seg = @Composable { label: String, smart: Boolean ->
        val sel = s.mode.isSmart == smart
        Text(
            label,
            style = Pro.Value.copy(fontSize = 10.sp, letterSpacing = 1.4.sp, color = if (sel) Color.Black else Pro.Dim),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (sel) Pro.Amber else Color.Transparent)
                .clickable(enabled = enabled) { c.showModeGroup(smart) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
    val container = Modifier
        .clip(RoundedCornerShape(50))
        .background(Color.White.copy(alpha = 0.10f))
        .padding(2.dp)
    if (vertical) Column(container, horizontalAlignment = Alignment.CenterHorizontally) {
        seg("REGULAR", false); seg("SMART", true)
    } else Row(container, verticalAlignment = Alignment.CenterVertically) {
        seg("REGULAR MODES", false); seg("SMART MODES", true)
    }
}

@Composable
private fun ModeSwitch(s: CameraUiState, c: ProCameraController, vertical: Boolean, modifier: Modifier = Modifier) {
    val modes = if (s.mode.isSmart) CaptureMode.SMART else CaptureMode.REGULAR
    val enabled = !s.recording && !s.panoActive && !s.stitching && !s.processing
    val items = @Composable {
        modes.forEach { m ->
            val sel = s.mode == m
            Text(
                m.label,
                style = Pro.Value.copy(fontSize = 12.sp, letterSpacing = 1.5.sp, color = if (sel) Pro.Amber else Pro.Dim),
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) { c.setMode(m) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
    if (vertical) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            ModeGroupToggle(s, c, vertical = true)
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.weight(1f, fill = false).hintedScroll(horizontal = false),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { items() }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            ModeGroupToggle(s, c, vertical = false)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.hintedScroll(horizontal = true), verticalAlignment = Alignment.CenterVertically) { items() }
        }
    }
}

@Composable
private fun LensSwitch(s: CameraUiState, c: ProCameraController, vertical: Boolean) {
    val back = s.lenses.withIndex().filter { it.value.facing == androidx.camera.core.CameraSelector.LENS_FACING_BACK }
    if (back.size < 2 || s.isFront || s.mode == CaptureMode.LIVE) return
    val items = @Composable {
        back.forEach { (i, lens) ->
            val sel = i == s.lensIndex
            Box(
                Modifier
                    .padding(3.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (sel) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f))
                    .border(1.dp, if (sel) Pro.Amber else Color.Transparent, CircleShape)
                    .clickable(enabled = !s.recording) { c.selectLens(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(lens.label, style = Pro.Value.copy(fontSize = 11.sp, color = if (sel) Pro.Amber else Color.White))
            }
        }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { items() }
    else Row(verticalAlignment = Alignment.CenterVertically) { items() }
}

@Composable
fun CaptureControls(s: CameraUiState, c: ProCameraController, vertical: Boolean, tablet: Boolean, modifier: Modifier = Modifier, onGallery: () -> Unit = {}) {
    val ls by c.live.state.collectAsState()
    val liveMode = s.mode == CaptureMode.LIVE
    val shutter = if (tablet) 88.dp else 74.dp
    val small = if (tablet) 54.dp else 46.dp
    // Pause and photo-while-recording only apply to normal video.
    val recording = s.mode.isNormalVideo && s.recording

    val leftButton = @Composable {
        if (recording) {
            RoundIcon(size = small, onClick = { c.pauseResume() }) {
                Icon(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
            }
        } else {
            GalleryThumb(s.lastMedia, small, onGallery)
        }
    }
    val rightButton = @Composable {
        if (liveMode) {
            RoundIcon(size = small, onClick = { c.live.switchCamera() }) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
            }
        } else if (recording) {
            RoundIcon(size = small, onClick = { c.snapshot() }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Photo while recording", tint = Color.White)
            }
        } else {
            val hasFront = s.lenses.any { it.facing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT }
            RoundIcon(enabled = hasFront && !s.panoActive && !s.recording && s.mode != CaptureMode.PANORAMA, size = small, onClick = { c.flipCamera() }) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
            }
        }
    }

    if (vertical) {
        Column(
            modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            ModeSwitch(s, c, vertical = true, modifier = Modifier.weight(1f, fill = false))
            LensSwitch(s, c, vertical = true)
            ShutterButton(
                s, shutter, liveMode, ls.isBusy,
                onHoldStart = if (s.mode == CaptureMode.BURST) ({ c.startHoldBurst() }) else null,
                onHoldEnd = { c.stopHoldBurst() },
            ) { c.shutter() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                leftButton()
                rightButton()
            }
        }
    } else {
        Column(
            modifier.padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            LensSwitch(s, c, vertical = false)
            ModeSwitch(s, c, vertical = false)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftButton()
                ShutterButton(
                s, shutter, liveMode, ls.isBusy,
                onHoldStart = if (s.mode == CaptureMode.BURST) ({ c.startHoldBurst() }) else null,
                onHoldEnd = { c.stopHoldBurst() },
            ) { c.shutter() }
                rightButton()
            }
        }
    }
}


// ------------------------------------------------------------------ filters & simple-mode bar

@Composable
fun FilterChip(s: CameraUiState, c: ProCameraController) {
    Column(
        Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (s.showFilters) Pro.Amber.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { c.toggleFilterStrip() }
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .widthIn(min = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FILTER", style = Pro.Label.copy(color = if (s.showFilters) Pro.Amber else Pro.Dim))
        Text(
            if (s.filter == Filter.NONE) "OFF" else s.filter.label.uppercase(),
            style = Pro.Value.copy(color = if (s.filter != Filter.NONE) Pro.Amber else Color.White),
            maxLines = 1,
        )
    }
}

@Composable
fun FilterStrip(s: CameraUiState, c: ProCameraController, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Pro.Panel)
            .hintedScroll(horizontal = true)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filter.entries.forEach { f ->
            val sel = s.filter == f
            if (f == Filter.entries.first { it.isFilmLook }) {
                // Divider + label before the film looks.
                Column(
                    Modifier.padding(horizontal = 2.dp).height(64.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(1.dp).height(30.dp).background(Pro.Line))
                    Text("FILM", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { c.setFilter(f) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(f.swatch))
                        .border(2.dp, if (sel) Pro.Amber else Color.Transparent, CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                Text(f.label, style = Pro.Label.copy(fontSize = 10.sp, letterSpacing = 0.sp, color = if (sel) Pro.Amber else Color.White))
            }
        }
    }
}

/** Bottom bar for the simple PHOTO / VIDEO / PANO modes: filters and quick zoom steps. */
@Composable
fun SimpleBar(s: CameraUiState, c: ProCameraController, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = s.showFilters, enter = fadeIn(), exit = fadeOut()) {
            FilterStrip(s, c, Modifier.padding(bottom = 6.dp))
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Pro.Panel)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!s.mode.isSlowMo) FilterChip(s, c)
            val zooms = listOf(1f, 2f, 5f, 10f).filter { s.maxZoom >= it && it >= s.minZoom }
            if (zooms.size > 1) {
                Spacer(Modifier.width(6.dp))
                zooms.forEach { z ->
                    val sel = abs(s.zoom - z) < 0.05f
                    Text(
                        Stops.zoomLabel(z),
                        style = Pro.Value.copy(fontSize = 12.sp, color = if (sel) Pro.Amber else Color.White),
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (sel) Color.Black.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { c.setZoom(z) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}


// ------------------------------------------------------------------ smart-mode quick settings

private fun intervalLabel(ms: Int): String = if (ms < 1000) "${ms / 1000f}s" else "${ms / 1000}s"

@Composable
private fun SmartModeItems(s: CameraUiState, c: ProCameraController) {
    val busy = s.recording || s.processing
    when (s.mode) {
        CaptureMode.TIMELAPSE -> {
            QuickItem("INTERVAL", intervalLabel(s.lapseIntervalMs), active = true, enabled = !busy) {
                c.setLapseInterval(listOf(500, 1000, 2000, 5000, 10000, 30000).next(s.lapseIntervalMs))
            }
            QuickItem("STOP AFTER", if (s.lapseLimitMin == 0) "∞" else "${s.lapseLimitMin}m", active = s.lapseLimitMin > 0, enabled = !busy) {
                c.setLapseLimit(listOf(0, 1, 5, 10, 30, 60).next(s.lapseLimitMin))
            }
            QuickItem("AE LOCK", if (s.aeLock) "ON" else "OFF", active = s.aeLock) { c.toggleAeLock() }
            QuickItem("TIMER", if (s.timerSec == 0) "OFF" else "${s.timerSec}s", active = s.timerSec > 0, enabled = !busy) {
                c.setTimer(listOf(0, 3, 5, 10).next(s.timerSec))
            }
        }
        CaptureMode.HYPERLAPSE -> {
            QuickItem("SPEED", "×${s.hyperSpeed}", active = true, enabled = !busy) {
                c.setHyperSpeed(listOf(4, 8, 15, 30).next(s.hyperSpeed))
            }
            QuickItem("STABILIZE", if (s.lapseStabilize) "ON" else "OFF", active = s.lapseStabilize, enabled = !busy) { c.toggleLapseStabilize() }
            QuickItem("AE LOCK", if (s.aeLock) "ON" else "OFF", active = s.aeLock) { c.toggleAeLock() }
            QuickItem("TIMER", if (s.timerSec == 0) "OFF" else "${s.timerSec}s", active = s.timerSec > 0, enabled = !busy) {
                c.setTimer(listOf(0, 3, 5, 10).next(s.timerSec))
            }
        }
        CaptureMode.SLOW_MO, CaptureMode.SUPER_SLOW_MO -> {
            val options = if (s.mode == CaptureMode.SUPER_SLOW_MO) s.superSlowMoFps
            else (s.slowMoFps + listOfNotNull(s.slowFallbackFps.takeIf { it > 0 && s.slowMoFps.isEmpty() }))
            if (options.isNotEmpty()) {
                QuickItem("FPS", "${s.slowFps}", active = true, enabled = !busy && options.size > 1) { c.setSlowFps(options.next(s.slowFps)) }
                QuickItem("SLOWER", "×${(s.slowFps / 30).coerceAtLeast(1)}", active = true) {}
            } else {
                QuickItem("FPS", "N/A", enabled = false) {}
            }
            QuickItem("AE LOCK", if (s.aeLock) "ON" else "OFF", active = s.aeLock) { c.toggleAeLock() }
            if (s.hasFlash) {
                QuickItem("TORCH", if (s.flash == FlashSetting.TORCH) "ON" else "OFF", active = s.flash == FlashSetting.TORCH) {
                    c.setFlash(if (s.flash == FlashSetting.TORCH) FlashSetting.OFF else FlashSetting.TORCH)
                }
            }
        }
        CaptureMode.BURST -> {
            QuickItem("PER TAP", "${s.burstModeCount}", active = true, enabled = !s.capturing) {
                c.setBurstModeCount(listOf(5, 10, 20, 30).next(s.burstModeCount))
            }
            val size = s.photoSize
            if (size != null && s.photoSizes.size > 1) {
                QuickItem("SIZE", size.label.replace(" ", ""), enabled = !s.capturing) { c.setPhotoSize(s.photoSizes.next(size)) }
            }
            QuickItem("AE LOCK", if (s.aeLock) "ON" else "OFF", active = s.aeLock) { c.toggleAeLock() }
        }
        else -> {}
    }
}
