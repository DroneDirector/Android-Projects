package com.dronedirector.procam.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dronedirector.procam.camera.AspectMode
import com.dronedirector.procam.camera.CameraUiState
import com.dronedirector.procam.camera.CaptureMode
import com.dronedirector.procam.camera.FlashSetting
import com.dronedirector.procam.camera.GridMode
import com.dronedirector.procam.camera.ProCameraController
import com.dronedirector.procam.camera.ProParam
import com.dronedirector.procam.camera.Stops
import com.dronedirector.procam.camera.VideoProfile
import com.dronedirector.procam.camera.WhiteBalance
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
    if (s.mode == CaptureMode.PHOTO) {
        if (s.hasFlash) {
            QuickItem("FLASH", s.flash.label, active = s.flash != FlashSetting.OFF) {
                c.setFlash(FlashSetting.entries.next(s.flash))
            }
        }
        QuickItem("TIMER", if (s.timerSec == 0) "OFF" else "${s.timerSec}s", active = s.timerSec > 0) {
            c.setTimer(listOf(0, 3, 5, 10).next(s.timerSec))
        }
        QuickItem("RATIO", s.aspect.label) { c.setAspect(AspectMode.entries.next(s.aspect)) }
        val formats = c.photoFormats()
        QuickItem("FORMAT", s.photoFormat.label, active = s.photoFormat != com.dronedirector.procam.camera.PhotoFormat.JPEG, enabled = formats.size > 1 && !s.sceneActive) {
            c.setPhotoFormat(formats.next(s.photoFormat))
        }
        if (s.scenes.size > 1) {
            QuickItem("SCENE", s.scene.label, active = s.scene != com.dronedirector.procam.camera.Scene.STANDARD) {
                c.setScene(s.scenes.next(s.scene))
            }
        }
        if (s.burst > 1) QuickItem("BURST", "x${s.burst}", active = true) { c.setBurst(1) }
    } else {
        if (s.hasFlash) {
            QuickItem("TORCH", if (s.flash == FlashSetting.TORCH) "ON" else "OFF", active = s.flash == FlashSetting.TORCH) {
                c.setFlash(if (s.flash == FlashSetting.TORCH) FlashSetting.OFF else FlashSetting.TORCH)
            }
        }
        QuickItem("RES", s.videoRes.label, enabled = !s.recording) { c.setVideoRes(s.videoResolutions.next(s.videoRes)) }
        QuickItem("FPS", "${s.fps}", active = s.fps != 30, enabled = !s.recording) { c.setFps(s.fpsOptions.next(s.fps)) }
        val profiles = buildList {
            add(VideoProfile.STANDARD)
            if (s.flatSupported) add(VideoProfile.FLAT)
            if (s.hlgSupported) add(VideoProfile.HLG)
        }
        QuickItem("PROFILE", s.profile.label, active = s.profile != VideoProfile.STANDARD, enabled = profiles.size > 1 && !s.recording) {
            c.setProfile(profiles.next(s.profile))
        }
        if (s.stabilizationSupported) {
            QuickItem("STAB", if (s.stabilization) "ON" else "OFF", active = s.stabilization, enabled = !s.recording) { c.toggleStabilization() }
        }
        QuickItem("BITRATE", if (s.highBitrate) "HIGH" else "STD", active = s.highBitrate, enabled = !s.recording) { c.toggleBitrate() }
    }
    QuickItem("GRID", s.grid.label, active = s.grid != GridMode.OFF) { c.setGrid(GridMode.entries.next(s.grid)) }
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
fun QuickBar(s: CameraUiState, c: ProCameraController, vertical: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    if (vertical) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) { QuickItems(s, c, onSettings) }
    } else {
        Row(
            modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) { QuickItems(s, c, onSettings) }
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
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val manualOk = s.manualSensor && !sceneLocked
            ParamChip(ProParam.ISO, if (s.manualIso) "${s.iso}" else "A ${isoShown ?: "--"}", s, manualOk, s.manualIso, c)
            ParamChip(ProParam.SHUTTER, if (s.manualShutter) Stops.shutterLabel(s.shutterNs) else "A ${shutterShown?.let { Stops.shutterLabel(it) } ?: "--"}", s, manualOk, s.manualShutter, c)
            ParamChip(ProParam.FOCUS, if (s.manualFocus) Stops.focusLabel(s.focusDiopters) else "AF", s, s.minFocusDiopters > 0f && !sceneLocked, s.manualFocus, c)
            ParamChip(ProParam.WB, s.whiteBalance.label + if (s.awbLock) "·L" else "", s, !sceneLocked, s.whiteBalance != WhiteBalance.AUTO || s.awbLock, c)
            ParamChip(ProParam.EV, "%+.1f".format(s.ev * s.evStep), s, s.evMax > s.evMin, s.ev != 0, c)
            ParamChip(ProParam.ZOOM, Stops.zoomLabel(s.zoom), s, s.maxZoom > s.minZoom, abs(s.zoom - 1f) > 0.05f, c)
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
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .widthIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.label, style = Pro.Label.copy(color = if (selected) Pro.Amber else Pro.Dim))
        Text(value, style = Pro.Value.copy(color = if (manual) Pro.Amber else Color.White), maxLines = 1)
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
            EditorRow("SHUTTER", Stops.shutterLabel(shown)) {
                AutoToggle("AUTO", !s.manualShutter) { c.setShutterAuto() }
                Spacer(Modifier.width(8.dp))
                StopSlider(stops.size, idx, { c.setShutter(stops[it]) }, Modifier.weight(1f))
            }
        }
        ProParam.FOCUS -> {
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
        }
        ProParam.WB -> {
            Column {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
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
fun ShutterButton(s: CameraUiState, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutterScale")
    val recording = s.mode == CaptureMode.VIDEO && s.recording
    val inner by animateDpAsState(if (recording) size * 0.36f else size * 0.78f, label = "shutterInner")
    val corner by animateDpAsState(if (recording) 6.dp else size, label = "shutterCorner")
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = if (s.capturing || s.countdown > 0) Pro.Amber else Color.White
            drawCircle(ring, radius = this.size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.5.dp.toPx()))
            val fill = when {
                s.mode == CaptureMode.VIDEO -> Pro.Red
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
fun GalleryThumb(uri: Uri?, size: Dp, onError: (String) -> Unit) {
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
            .clickable {
                val intent = if (uri != null) {
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                } else {
                    Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    onError("No gallery app found")
                }
            },
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

@Composable
private fun ModeSwitch(s: CameraUiState, c: ProCameraController, vertical: Boolean) {
    val items = @Composable {
        CaptureMode.entries.forEach { m ->
            val sel = s.mode == m
            Text(
                m.name,
                style = Pro.Value.copy(fontSize = 12.sp, letterSpacing = 1.5.sp, color = if (sel) Pro.Amber else Pro.Dim),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !s.recording) { c.setMode(m) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { items() }
    else Row(verticalAlignment = Alignment.CenterVertically) { items() }
}

@Composable
private fun LensSwitch(s: CameraUiState, c: ProCameraController, vertical: Boolean) {
    val back = s.lenses.withIndex().filter { it.value.facing == androidx.camera.core.CameraSelector.LENS_FACING_BACK }
    if (back.size < 2 || s.isFront) return
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
fun CaptureControls(s: CameraUiState, c: ProCameraController, vertical: Boolean, tablet: Boolean, modifier: Modifier = Modifier) {
    val shutter = if (tablet) 88.dp else 74.dp
    val small = if (tablet) 54.dp else 46.dp
    val recording = s.mode == CaptureMode.VIDEO && s.recording

    val leftButton = @Composable {
        if (recording) {
            RoundIcon(size = small, onClick = { c.pauseResume() }) {
                Icon(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
            }
        } else {
            GalleryThumb(s.lastMedia, small) { c.toast(it) }
        }
    }
    val rightButton = @Composable {
        val hasFront = s.lenses.any { it.facing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT }
        RoundIcon(enabled = hasFront && !s.recording, size = small, onClick = { c.flipCamera() }) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
        }
    }

    if (vertical) {
        Column(
            modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            ModeSwitch(s, c, vertical = true)
            LensSwitch(s, c, vertical = true)
            ShutterButton(s, shutter) { c.shutter() }
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
                ShutterButton(s, shutter) { c.shutter() }
                rightButton()
            }
        }
    }
}
