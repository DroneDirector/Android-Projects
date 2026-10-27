package com.dronedirector.veyrocutpro.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dronedirector.veyrocutpro.ui.Pro
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

// The wheels use their own colour (gear settings → SCROLL WHEELS), apart from the app's accent.
private val Cyan: Color get() = com.dronedirector.veyrocutpro.ui.CursorStyle.wheel
private val Violet: Color get() = com.dronedirector.veyrocutpro.ui.CursorStyle.partner(com.dronedirector.veyrocutpro.ui.CursorStyle.wheel)
private val WheelTip: Color get() = androidx.compose.ui.graphics.lerp(com.dronedirector.veyrocutpro.ui.CursorStyle.wheel, Color.White, 0.45f)

/** Pseudo-random 0..1 for a cell (stable per cell and step). */
private fun hash(a: Int, b: Int, c: Int): Float {
    var h = a * 374761393 + b * 668265263 + c * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

/**
 * Slim vertical scroll wheel for the timeline (lower-right corner). Drag it like a wheel to move
 * the tracks up and down, or tap to jump. While it moves it "upscales": coarse pixel blocks resolve
 * into fine detail, a glowing scan line follows the scroll and the wheel's ridges roll.
 */
@Composable
fun TimelineWheel(state: ScrollState, modifier: Modifier = Modifier, fancy: Boolean = true, onActive: (Float) -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableFloatStateOf(0f) }
    // 0 = idle, 1 = fully "upscaling"; eases out after the movement stops.
    val energy = remember { Animatable(0f) }
    val moving = dragging || state.isScrollInProgress
    LaunchedEffect(moving, state.value) {
        if (moving) energy.snapTo(1f)
        else energy.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(energy.value, fancy) { onActive(if (fancy) energy.value else 0f) }
    // The glow only runs with effects on (plain look = no animation at all).
    val phase = if (fancy) {
        val glow = rememberInfiniteTransition(label = "wheel")
        glow.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "ph").value
    } else 0f
    val canScroll = state.maxValue > 0

    Canvas(
        modifier
            .width(16.dp).heightIn(min = 76.dp)
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dy ->
                    change.consume()
                    // Like a wheel: the drag moves the tracks, scaled so the full wheel = all tracks.
                    val k = (state.maxValue + size.height).toFloat() / size.height
                    state.dispatchRawDelta(dy * k)
                    val ridge = 6.dp.toPx() * k
                    if (abs(state.value - lastTick) > ridge) { lastTick = state.value.toFloat(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            }
            .pointerInput(state) {
                detectTapGestures { o ->
                    val f = (o.y / size.height).coerceIn(0f, 1f)
                    state.dispatchRawDelta(f * state.maxValue - state.value)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    ) {
        val w = size.width; val h = size.height
        // Too small to draw (e.g. while the layout settles): skip the frame — a zero-length
        // gradient would make Android throw.
        if (w < 4f || h < 8f || !w.isFinite() || !h.isFinite()) return@Canvas
        val r = CornerRadius(w / 2, w / 2)
        if (!fancy) {
            // Plain look: solid track and thumb, no effects or animation.
            drawRoundRect(Color(0xFF1A1E26), size = size, cornerRadius = r)
            val vp0 = state.viewportSize.toFloat().coerceAtLeast(1f)
            val vis0 = (if (canScroll) (h * vp0 / (vp0 + state.maxValue)).coerceIn(h * 0.22f, h) else h).let { if (it.isFinite() && it >= 4f) it else h }
            val t0 = (if (canScroll) state.value.toFloat() / state.maxValue else 0f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            val top0 = (h - vis0) * t0
            drawRoundRect(WheelTip.copy(alpha = if (canScroll) 0.9f else 0.35f), topLeft = Offset(w * 0.2f, top0 + 1f), size = Size(w * 0.6f, vis0 - 2f), cornerRadius = CornerRadius(w * 0.3f, w * 0.3f))
            drawRoundRect(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.18f), size = size, cornerRadius = r, style = Stroke(width = 1.dp.toPx()))
            return@Canvas
        }
        val e = energy.value
        val dim = if (canScroll) 1f else 0.4f
        // Glass track.
        drawRoundRect(Color(0xCC0D1016), size = size, cornerRadius = r)
        // Rolling ridges (the "wheel").
        val sp = 5.dp.toPx()
        val off = (state.value * 0.35f) % sp
        var y = -off
        while (y < h) {
            val edge = 1f - abs(y / h - 0.5f) * 2f            // fade towards the ends (curved wheel look)
            drawLine(Color.White.copy(alpha = (0.06f + 0.10f * edge) * dim), Offset(w * 0.3f, y), Offset(w * 0.7f, y), 1.dp.toPx())
            y += sp
        }
        // Thumb position and size.
        val vp = state.viewportSize.toFloat().coerceAtLeast(1f)
        val vis = (if (canScroll) (h * vp / (vp + state.maxValue)).coerceIn(h * 0.22f, h) else h).let { if (it.isFinite() && it >= 4f) it else h }
        val t = (if (canScroll) state.value.toFloat() / state.maxValue else 0f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        val top = (h - vis) * t
        // "Upscale" pixels: blocks that refine from coarse to fine as the energy fades.
        if (e > 0.01f) {
            val cell = (2.dp.toPx() + 7.dp.toPx() * e)
            val step = floor(phase * 12f).toInt()
            var cy = 0f; var j = 0
            while (cy < h) {
                var cx = 0f; var i = 0
                while (cx < w) {
                    val v = hash(i, j, step)
                    val near = 1f - (abs(cy + cell / 2 - (top + vis / 2)) / h).coerceIn(0f, 1f)
                    val a = e * near * (0.15f + 0.45f * v)
                    if (a > 0.02f) drawRect(if (v > 0.5f) Cyan.copy(alpha = a) else Violet.copy(alpha = a), Offset(cx, cy), Size(cell - 1f, cell - 1f))
                    cx += cell; i++
                }
                cy += cell; j++
            }
        }
        // Thumb with gradient glow.
        drawRoundRect(
            Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.85f * dim), Violet.copy(alpha = 0.85f * dim), WheelTip.copy(alpha = 0.9f * dim)), startY = top, endY = top + vis),
            topLeft = Offset(w * 0.18f, top + 1f), size = Size(w * 0.64f, vis - 2f), cornerRadius = CornerRadius(w * 0.32f, w * 0.32f),
        )
        // Scan line: a bright band sweeping through the thumb, stronger while moving.
        val scanY = top + vis * ((phase * 1.4f) % 1f)
        val sa = (0.25f + 0.75f * e) * dim
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = sa), Color.Transparent), startY = scanY - 6f, endY = scanY + 6f), Offset(0f, scanY - 6f), Size(w, 12f))
        // Sparkles while moving.
        if (e > 0.05f) for (k in 0 until 4) {
            val sx = w * hash(k, 7, floor(phase * 30f).toInt())
            val sy = top + vis * hash(k, 11, floor(phase * 30f).toInt())
            drawCircle(Color.White.copy(alpha = e * 0.9f), radius = 1.dp.toPx() * (0.6f + 0.6f * sin(phase * 40f + k).coerceAtLeast(0f)), center = Offset(sx, sy))
        }
        // Rim: animated gradient border.
        val rim = Brush.sweepGradient(listOf(Cyan, Violet, WheelTip, Cyan), center = Offset(w / 2, h * phase))
        drawRoundRect(rim, size = size, cornerRadius = r, style = Stroke(width = (1f + 1.2f * e).dp.toPx()), alpha = (0.45f + 0.55f * e) * dim)
    }
}

/**
 * The matching effect over the timeline while the wheel moves: a faint pixel grid that sharpens
 * and a glowing scan line that follows the scroll direction — like an image being upscaled.
 */
@Composable
fun TimelineUpscaleOverlay(energy: Float, scroll: Int, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    if (energy < 0.02f) return
    val prev = remember { mutableFloatStateOf(scroll.toFloat()) }
    val dir = if (scroll >= prev.floatValue) 1f else -1f
    LaunchedEffect(scroll) { prev.floatValue = scroll.toFloat() }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        if (w < 4f || h < 4f || !w.isFinite() || !h.isFinite()) return@Canvas
        val cell = 3.dp.toPx() + 10.dp.toPx() * energy
        val a = 0.10f * energy
        var x = 0f
        while (x < w) { drawLine(Cyan.copy(alpha = a), Offset(x, 0f), Offset(x, h), 1f); x += cell }
        var y = 0f
        while (y < h) { drawLine(Violet.copy(alpha = a), Offset(0f, y), Offset(w, y), 1f); y += cell }
        if (horizontal) {
            // Scan band sweeping sideways, with the scroll direction.
            val sx = if (dir > 0) w * (1f - energy) else w * energy
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Cyan.copy(alpha = 0.22f * energy), Color.White.copy(alpha = 0.35f * energy), Violet.copy(alpha = 0.22f * energy), Color.Transparent), startX = sx - 26f, endX = sx + 26f),
                Offset(sx - 26f, 0f), Size(52f, h),
            )
            return@Canvas
        }
        // Scan band, travelling with the scroll direction.
        val sy = if (dir > 0) h * (1f - energy) else h * energy
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Cyan.copy(alpha = 0.22f * energy), Color.White.copy(alpha = 0.35f * energy), Violet.copy(alpha = 0.22f * energy), Color.Transparent), startY = sy - 26f, endY = sy + 26f),
            Offset(0f, sy - 26f), Size(w, 52f),
        )
    }
}

/**
 * The horizontal twin of [TimelineWheel], along the bottom of the timeline: drag it like a wheel to
 * move through the edit left and right, or tap to jump. Same glass, ridges, pixel "upscale",
 * scan line and glowing rim, turned on its side.
 */
@Composable
fun TimelineHWheel(zoom: TimelineZoom, modifier: Modifier = Modifier, fancy: Boolean = true, onActive: (Float) -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableFloatStateOf(0f) }
    val energy = remember { Animatable(0f) }
    val value = zoom.scrollMs.toFloat()
    LaunchedEffect(dragging, zoom.scrollMs) {
        if (dragging) energy.snapTo(1f)
        else { if (energy.value > 0f) energy.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
    }
    // Moving the view any other way (pinch, playhead following) lights it up briefly too.
    val prevScroll = remember { mutableFloatStateOf(value) }
    LaunchedEffect(zoom.scrollMs) {
        if (!dragging && kotlin.math.abs(value - prevScroll.floatValue) > 1f) { energy.snapTo(0.6f); energy.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
        prevScroll.floatValue = value
    }
    LaunchedEffect(energy.value, fancy) { onActive(if (fancy) energy.value else 0f) }
    val phase = if (fancy) {
        val glow = rememberInfiniteTransition(label = "hwheel")
        glow.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "hph").value
    } else 0f
    val maxMs = zoom.maxScrollMs.toFloat()
    val canScroll = maxMs > 0f

    Canvas(
        modifier
            .height(16.dp).widthIn(min = 76.dp)
            .pointerInput(zoom) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dx ->
                    change.consume()
                    // Like a wheel: the full wheel = the whole edit.
                    val msPerPx = zoom.totalMs.toFloat() / size.width
                    zoom.scrollBy((dx * msPerPx).toLong())
                    val ridge = 6.dp.toPx() * msPerPx
                    if (kotlin.math.abs(zoom.scrollMs - lastTick) > ridge) { lastTick = zoom.scrollMs.toFloat(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            }
            .pointerInput(zoom) {
                detectTapGestures { o ->
                    val f = (o.x / size.width).coerceIn(0f, 1f)
                    zoom.scrollMs = (f * zoom.maxScrollMs).toLong()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    ) {
        val w = size.width; val h = size.height
        if (w < 8f || h < 4f || !w.isFinite() || !h.isFinite()) return@Canvas
        val r = CornerRadius(h / 2, h / 2)
        if (!fancy) {
            // Plain look: solid track and thumb, no effects or animation.
            drawRoundRect(Color(0xFF1A1E26), size = size, cornerRadius = r)
            val total0 = zoom.totalMs.toFloat().coerceAtLeast(1f)
            val vis0 = (if (canScroll) (w * zoom.viewMs / total0).coerceIn(w * 0.12f, w) else w).let { if (it.isFinite() && it >= 4f) it else w }
            val t0 = (if (canScroll) value / maxMs else 0f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            val left0 = (w - vis0) * t0
            drawRoundRect(WheelTip.copy(alpha = if (canScroll) 0.9f else 0.35f), topLeft = Offset(left0 + 1f, h * 0.2f), size = Size(vis0 - 2f, h * 0.6f), cornerRadius = CornerRadius(h * 0.3f, h * 0.3f))
            drawRoundRect(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.18f), size = size, cornerRadius = r, style = Stroke(width = 1.dp.toPx()))
            return@Canvas
        }
        val e = energy.value
        val dim = if (canScroll) 1f else 0.4f
        drawRoundRect(Color(0xCC0D1016), size = size, cornerRadius = r)
        // Rolling ridges.
        val sp = 5.dp.toPx()
        val off = (value * 0.02f) % sp
        var x = -off
        while (x < w) {
            val edge = 1f - abs(x / w - 0.5f) * 2f
            drawLine(Color.White.copy(alpha = (0.06f + 0.10f * edge) * dim), Offset(x, h * 0.3f), Offset(x, h * 0.7f), 1.dp.toPx())
            x += sp
        }
        // Thumb: the part of the edit in view.
        val total = zoom.totalMs.toFloat().coerceAtLeast(1f)
        val vis = (if (canScroll) (w * zoom.viewMs / total).coerceIn(w * 0.12f, w) else w).let { if (it.isFinite() && it >= 4f) it else w }
        val t = (if (canScroll) value / maxMs else 0f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        val left = (w - vis) * t
        if (e > 0.01f) {
            val cell = (2.dp.toPx() + 7.dp.toPx() * e)
            val step = floor(phase * 12f).toInt()
            var cx = 0f; var i = 0
            while (cx < w) {
                var cy = 0f; var j = 0
                while (cy < h) {
                    val v = hash(i, j, step)
                    val near = 1f - (abs(cx + cell / 2 - (left + vis / 2)) / w).coerceIn(0f, 1f)
                    val a = e * near * near * (0.15f + 0.45f * v)
                    if (a > 0.02f) drawRect(if (v > 0.5f) Cyan.copy(alpha = a) else Violet.copy(alpha = a), Offset(cx, cy), Size(cell - 1f, cell - 1f))
                    cy += cell; j++
                }
                cx += cell; i++
            }
        }
        drawRoundRect(
            Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.85f * dim), Violet.copy(alpha = 0.85f * dim), WheelTip.copy(alpha = 0.9f * dim)), startX = left, endX = left + vis),
            topLeft = Offset(left + 1f, h * 0.18f), size = Size(vis - 2f, h * 0.64f), cornerRadius = CornerRadius(h * 0.32f, h * 0.32f),
        )
        val scanX = left + vis * ((phase * 1.4f) % 1f)
        val sa = (0.25f + 0.75f * e) * dim
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = sa), Color.Transparent), startX = scanX - 6f, endX = scanX + 6f), Offset(scanX - 6f, 0f), Size(12f, h))
        if (e > 0.05f) for (k in 0 until 4) {
            val sx = left + vis * hash(k, 7, floor(phase * 30f).toInt())
            val sy = h * hash(k, 11, floor(phase * 30f).toInt())
            drawCircle(Color.White.copy(alpha = e * 0.9f), radius = 1.dp.toPx() * (0.6f + 0.6f * sin(phase * 40f + k).coerceAtLeast(0f)), center = Offset(sx, sy))
        }
        val rim = Brush.sweepGradient(listOf(Cyan, Violet, WheelTip, Cyan), center = Offset(w * phase, h / 2))
        drawRoundRect(rim, size = size, cornerRadius = r, style = Stroke(width = (1f + 1.2f * e).dp.toPx()), alpha = (0.45f + 0.55f * e) * dim)
    }
}
