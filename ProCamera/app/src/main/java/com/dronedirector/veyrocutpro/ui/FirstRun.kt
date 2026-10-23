package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.editor.AppData
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * First launch: the AI models and soundtracks are downloaded once (they aren't inside the APK).
 * Full screen with a glowing progress bar in the timeline scroll wheels' style; "Use the app now"
 * lets it carry on in the background (a small badge shows the progress).
 */
@Composable
fun FirstRunGate() {
    val context = LocalContext.current
    var needed by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var askData by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Only after an app update does this ask the server (one small file); otherwise it just looks at the phone.
        needed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppData.init(context); AppData.checkAfterUpdate(context) }
        checked = true
        if (!needed) return@LaunchedEffect
        if (AppData.metered(context)) askData = true else AppData.start(context)
    }
    if (!checked && AppData.checking) { CheckingSplash(); return }
    // Waiting for Wi-Fi: start as soon as the phone is on a free network.
    LaunchedEffect(AppData.waitingForWifi) {
        while (AppData.waitingForWifi) {
            delay(5000)
            if (!AppData.metered(context) && AppData.online(context)) { AppData.waitingForWifi = false; AppData.start(context) }
        }
    }
    LaunchedEffect(AppData.finished) { if (AppData.finished) { delay(1500); needed = false } }
    if (!needed) return
    if (AppData.background) { DownloadBadge(); return }
    FirstRunScreen(
        askData = askData,
        onDownloadNow = { askData = false; AppData.start(context) },
        onWaitWifi = { askData = false; AppData.waitingForWifi = true },
        onRetry = { AppData.start(context) },
        onUseNow = { AppData.background = true },
    )
}

@Composable
private fun FirstRunScreen(askData: Boolean, onDownloadNow: () -> Unit, onWaitWifi: () -> Unit, onRetry: () -> Unit, onUseNow: () -> Unit) {
    val total = AppData.totalBytes().coerceAtLeast(1)
    val frac = (AppData.doneBytes.toFloat() / total).coerceIn(0f, 1f)
    val shown by animateFloatAsState(frac, tween(400), label = "frac")
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07090D), Color(0xFF10141C), Color(0xFF07090D))))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlowLogo(110.dp)
            Text("VelaCut Pro", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("FIRST-TIME SETUP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.padding(top = 6.dp))
            Text(
                "Downloading the AI models and soundtracks (about ${total / 1_000_000} MB). This happens only once — after that everything works offline.",
                color = Pro.Dim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp, bottom = 26.dp),
            )
            if (askData) {
                Text("You're on mobile data. Download about ${total / 1_000_000} MB now, or wait for Wi-Fi?", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("WAIT FOR WI-FI", false, onWaitWifi)
                    Pill("DOWNLOAD NOW", true, onDownloadNow)
                }
            } else {
                Text(
                    "${(shown * 100).toInt()}%", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace,
                )
                GlowBar(shown, AppData.running, Modifier.fillMaxWidth().height(22.dp).padding(top = 0.dp))
                Text(
                    "${AppData.doneBytes / 1_000_000} / ${total / 1_000_000} MB" + if (AppData.current.isNotEmpty()) " · ${AppData.current}" else "",
                    style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.padding(top = 12.dp),
                )
                when {
                    AppData.finished -> Text("All set — enjoy!", color = Pro.Amber, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
                    AppData.waitingForWifi -> Text("Waiting for Wi-Fi… it starts by itself.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
                    AppData.error != null -> {
                        Text("${AppData.error}. Check the internet connection.", color = Pro.Red, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
                        Row(Modifier.padding(top = 12.dp)) { Pill("TRY AGAIN", true, onRetry) }
                    }
                }
            }
            if (!AppData.finished) {
                Text(
                    "USE THE APP NOW", style = Pro.Label.copy(color = Color.White, fontSize = 12.sp),
                    modifier = Modifier.padding(top = 34.dp).clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .clickable(onClick = onUseNow).padding(horizontal = 18.dp, vertical = 10.dp),
                )
                Text("The download carries on in the background. Tools whose model hasn't arrived yet tell you so.", color = Pro.Dim, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        text, style = Pro.Label.copy(color = if (accent) Color.Black else Color.White, fontSize = 12.sp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (accent) Pro.Amber else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

/** The progress bar: glass track, glowing gradient fill, a scan light, sparkles and a moving rim. */
@Composable
fun GlowBar(frac: Float, active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bar")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (w < 8f || h < 4f) return@Canvas
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color(0xCC0D1016), size = size, cornerRadius = r)
        // Ridges, like the wheels.
        val sp = 6.dp.toPx()
        var x = -(phase * sp)
        while (x < w) { drawLine(Color.White.copy(alpha = 0.07f), Offset(x, h * 0.3f), Offset(x, h * 0.7f), 1.dp.toPx()); x += sp }
        val fw = (w * frac).coerceIn(0f, w)
        if (fw > h * 0.5f) {
            // Glow under the fill.
            drawRoundRect(Pro.Amber.copy(alpha = 0.18f), topLeft = Offset(-3f, -3f), size = Size(fw + 6f, h + 6f), cornerRadius = CornerRadius(h, h))
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFFFF7A00), Pro.Amber, Color(0xFFFFE08A)), startX = 0f, endX = fw.coerceAtLeast(1f)),
                size = Size(fw, h), cornerRadius = r,
            )
            // Pixel "upscale" blocks along the leading edge.
            val bs = h / 3f
            for (i in 0 until 6) for (j in 0 until 3) {
                val bx = fw - (i + 1) * bs
                if (bx < 0) continue
                val a = ((sin((phase * 6.28f) * 2 + i * 1.3f + j * 2.1f) + 1f) / 2f) * (1f - i / 6f) * 0.5f
                drawRect(Color.White.copy(alpha = a), Offset(bx, j * bs), Size(bs - 1f, bs - 1f))
            }
            // Scan light running across the fill.
            if (active) {
                val sx = (phase * (fw + h * 2)) - h
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), startX = sx - h, endX = sx + h), Offset((sx - h).coerceAtLeast(0f), 0f), Size((h * 2).coerceAtMost(fw), h))
            }
            // Sparkles at the tip.
            for (k in 0 until 5) {
                val f = (phase + k / 5f) % 1f
                val px = fw + f * h * 1.4f
                val py = h / 2 + sin((f + k) * 9f) * h * 0.6f
                if (px < w + h) drawCircle(Color(0xFFFFE08A).copy(alpha = (1f - f) * 0.9f), (1f - f) * h * 0.14f + 1f, Offset(px, py))
            }
        }
        // Moving rim.
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Pro.Amber.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f)), startX = phase * w * 2 - w, endX = phase * w * 2),
            size = size, cornerRadius = r, style = Stroke(1.5f.dp.toPx()),
        )
    }
}

/** While the download carries on in the background: a small badge at the top; tap it for the full screen. */
@Composable
private fun DownloadBadge() {
    val total = AppData.totalBytes().coerceAtLeast(1)
    val frac = (AppData.doneBytes.toFloat() / total).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.72f)).border(1.dp, Pro.Amber.copy(alpha = 0.4f), RoundedCornerShape(50))
                .clickable { AppData.background = false }.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (AppData.error != null) "Download paused — tap" else if (AppData.finished) "AI models ready" else "Downloading AI models · ${(frac * 100).toInt()}%",
                style = Pro.Label.copy(color = Color.White, fontSize = 10.sp),
            )
            GlowBar(frac, AppData.running, Modifier.size(140.dp, 5.dp).padding(top = 0.dp))
        }
    }
}

/** After an app update: a brief look for changed files on the server. */
@Composable
private fun CheckingSplash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF07090D)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlowLogo(110.dp)
            Text("VelaCut Pro", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Checking for updated AI files…", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
            GlowBar(0.35f, true, Modifier.size(180.dp, 6.dp))
        }
    }
}
