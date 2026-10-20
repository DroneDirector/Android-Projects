package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dronedirector.veyrocutpro.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app logo with a glowing, sparkling ring in the app colour (Settings → App colour): a light
 * runs round the ring, sparkles orbit and twinkle, and a soft glow breathes behind it — the same
 * style as the download bar and the scroll wheels.
 */
@Composable
fun GlowLogo(size: Dp, modifier: Modifier = Modifier) {
    val accent = Pro.Amber
    val bright = lerp(accent, Color.White, 0.55f)
    val t = rememberInfiniteTransition(label = "logo")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val breath by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breath")
    // The ring and sparkles reach a little beyond the logo.
    val outer = size * 1.42f
    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = size.toPx() / 2f + 2.dp.toPx()
            // Soft glow behind everything.
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.34f * breath), accent.copy(alpha = 0.10f * breath), Color.Transparent), c, r * 1.42f), r * 1.42f, c)
            // Base ring.
            drawCircle(accent.copy(alpha = 0.45f), r, c, style = Stroke(2.5.dp.toPx()))
            // Running light: a bright arc sweeping round the ring.
            rotate(spin, c) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, accent.copy(alpha = 0.2f), bright, Color.White, Color.Transparent, Color.Transparent), c),
                    r, c, style = Stroke(4.dp.toPx()),
                )
                // Wider halo along the light.
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent, Color.Transparent), c),
                    r, c, style = Stroke(12.dp.toPx()),
                )
            }
            // Ticks that shimmer as the light passes (like the wheels' ridges).
            for (i in 0 until 48) {
                val a = i / 48f * 2f * PI.toFloat()
                val lit = ((1f - (((spin / 360f) - i / 48f + 1f) % 1f)) * 4f - 3f).coerceIn(0f, 1f)
                val p1 = c + Offset(cos(a), sin(a)) * (r + 5.dp.toPx())
                val p2 = c + Offset(cos(a), sin(a)) * (r + 8.dp.toPx())
                drawLine(lerp(accent.copy(alpha = 0.18f), Color.White.copy(alpha = 0.85f), lit), p1, p2, 1.5.dp.toPx())
            }
            // Orbiting sparkles with little tails.
            for (k in 0 until 7) {
                val f = (phase + k / 7f) % 1f
                val ang = (f * 2f * PI.toFloat()) * (if (k % 2 == 0) 1f else -0.7f) + k
                val rad = r * (1.12f + 0.12f * sin(f * 6.28f * 2 + k))
                val p = c + Offset(cos(ang), sin(ang)) * rad
                val tw = (sin(f * 6.28f * 3 + k * 1.7f) + 1f) / 2f
                drawCircle(accent.copy(alpha = 0.35f * tw), (2.5f + 4f * tw).dp.toPx(), p)
                drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * tw), (0.8f + 1.4f * tw).dp.toPx(), p)
                // Four-point star on the brightest moments.
                if (tw > 0.75f) {
                    val s = (tw - 0.75f) * 4f * 7.dp.toPx()
                    drawLine(Color.White.copy(alpha = 0.8f), p - Offset(s, 0f), p + Offset(s, 0f), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.8f), p - Offset(0f, s), p + Offset(0f, s), 1.dp.toPx())
                }
            }
        }
        // The logo itself.
        Box(Modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f })
        }
    }
}
