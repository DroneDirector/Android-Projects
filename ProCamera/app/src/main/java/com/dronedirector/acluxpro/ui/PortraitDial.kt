package com.dronedirector.acluxpro.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.camera.CameraUiState
import com.dronedirector.acluxpro.camera.CaptureExtras
import com.dronedirector.acluxpro.camera.CaptureMode
import com.dronedirector.acluxpro.camera.Portrait
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Portrait mode blur control, sitting just above the filter / zoom bar.
 * Open: a 180° dial (turn left / right) from f/1.4 to f/16. Minimised: one row, blur picked automatically.
 */
@Composable
fun PortraitPanel(s: CameraUiState, modifier: Modifier = Modifier) {
    if (s.mode != CaptureMode.PORTRAIT) return
    val ctx = LocalContext.current
    val auto = CaptureExtras.portraitAuto
    Column(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (auto) Modifier.clickable { CaptureExtras.portraitAuto = false; CaptureExtras.save(ctx) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BACKGROUND BLUR", style = Pro.Label.copy(fontSize = 10.sp, color = Pro.Amber))
            if (auto) {
                Spacer(Modifier.width(8.dp))
                Text("AUTO", style = Pro.Value.copy(fontSize = 12.sp, color = Color.White))
            }
            Spacer(Modifier.weight(1f))
            // Minimise = auto; expand = manual dial.
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { CaptureExtras.portraitAuto = !auto; CaptureExtras.save(ctx) }
                    .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (auto) "MANUAL" else "AUTO", style = Pro.Label.copy(fontSize = 10.sp, color = Color.White))
                Icon(
                    if (auto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (auto) "Show blur dial" else "Hide dial, blur automatically",
                    tint = Color.White, modifier = Modifier.size(16.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = !auto,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BlurDial(
                    fNumber = CaptureExtras.portraitF,
                    onChange = { CaptureExtras.portraitF = it },
                    onSettled = { CaptureExtras.save(ctx) },
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "Turn the dial · lower f = more blur · added right after the shot",
                    style = Pro.Label.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                )
            }
        }
    }
}

private const val STEP_DEG = 13f          // angle between third-stops on the dial
private val MAJOR = setOf(0, 3, 6, 9, 12, 15, 18, 21) // full stops: 1.4 2 2.8 4 5.6 8 11 16

/** Half-circle aperture wheel. The value under the amber pointer at the top is the one used. */
@Composable
fun BlurDial(fNumber: Float, onChange: (Float) -> Unit, onSettled: () -> Unit, modifier: Modifier = Modifier) {
    val stops = Portrait.F_STOPS
    val last = (stops.size - 1).toFloat()
    val pos = remember { Animatable(Portrait.indexOf(fNumber).toFloat()) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Follow outside changes (e.g. settings restored) when not being turned.
    LaunchedEffect(fNumber) {
        val i = Portrait.indexOf(fNumber).toFloat()
        if (!pos.isRunning && abs(pos.value - i) >= 0.5f) pos.snapTo(i)
    }
    val lastIdx = remember { intArrayOf(Portrait.indexOf(fNumber)) }

    fun report(p: Float) {
        val i = p.roundToInt().coerceIn(0, stops.size - 1)
        if (i != lastIdx[0]) {
            lastIdx[0] = i
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(stops[i])
        }
    }
    fun settle(target: Float) = scope.launch {
        val t = target.roundToInt().coerceIn(0, stops.size - 1).toFloat()
        report(t)
        pos.animateTo(t, spring(stiffness = 500f))
        onSettled()
    }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    }
    val valuePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    }
    val amber = Pro.Amber

    Canvas(
        modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .height(118.dp)
            .pointerInput(Unit) {
                val radius = size.height - 14.dp.toPx()
                val pxPerStep = radius * (STEP_DEG * PI.toFloat() / 180f)
                detectHorizontalDragGestures(
                    onDragEnd = { settle(pos.value) },
                    onDragCancel = { settle(pos.value) },
                ) { change, dx ->
                    change.consume()
                    // Drag right turns the wheel clockwise: the lower (more blur) numbers come to the pointer.
                    val p = (pos.value - dx / pxPerStep).coerceIn(0f, last)
                    scope.launch { pos.snapTo(p) }
                    report(p)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    // Tap a number to turn straight to it.
                    val cx = size.width / 2f; val cy = size.height.toFloat()
                    val deg = (atan2(o.x - cx, cy - o.y) * 180f / PI.toFloat())
                    settle(pos.value + deg / STEP_DEG)
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height
        val r = size.height - 14.dp.toPx()
        val ring = 2.dp.toPx()

        // Ring.
        drawArc(
            Color.White.copy(alpha = 0.25f), 180f, 180f, false,
            topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = Stroke(ring),
        )

        labelPaint.textSize = 11.sp.toPx()
        for (i in stops.indices) {
            val a = (i - pos.value) * STEP_DEG
            if (abs(a) > 88f) continue
            val rad = (a - 90f) * PI.toFloat() / 180f
            val fade = (1f - (abs(a) / 90f)).coerceIn(0f, 1f)
            val major = i in MAJOR
            val len = if (major) 10.dp.toPx() else 5.dp.toPx()
            val c = cos(rad); val sn = sin(rad)
            val near = abs(a) < STEP_DEG / 2
            val col = if (near) amber else Color.White.copy(alpha = 0.35f + 0.6f * fade)
            drawLine(col, Offset(cx + c * r, cy + sn * r), Offset(cx + c * (r - len), cy + sn * (r - len)),
                strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
            if (major) {
                val lr = r - 22.dp.toPx()
                labelPaint.color = (if (near) amber else Color.White.copy(alpha = 0.3f + 0.7f * fade)).toArgb()
                val txt = Portrait.label(stops[i]).removePrefix("f/")
                drawContext.canvas.nativeCanvas.drawText(txt, cx + c * lr, cy + sn * lr + labelPaint.textSize / 3f, labelPaint)
            }
        }

        // Pointer at the top.
        val tip = cy - r + 1.dp.toPx()
        val w = 6.dp.toPx(); val h = 8.dp.toPx()
        drawPath(Path().apply { moveTo(cx, tip); lineTo(cx - w, tip - h); lineTo(cx + w, tip - h); close() }, amber)

        // Current value in the middle of the dial.
        val cur = stops[pos.value.roundToInt().coerceIn(0, stops.size - 1)]
        valuePaint.textSize = 22.sp.toPx(); valuePaint.color = amber.toArgb()
        drawContext.canvas.nativeCanvas.drawText(Portrait.label(cur), cx, cy - 22.dp.toPx(), valuePaint)
        valuePaint.textSize = 9.sp.toPx(); valuePaint.color = Color.White.copy(alpha = 0.6f).toArgb()
        drawContext.canvas.nativeCanvas.drawText("MORE BLUR  ◂  ▸  LESS", cx, cy - 6.dp.toPx(), valuePaint)
    }
}
