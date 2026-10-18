package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A short first-run tour of the camera's gestures, shown once (replay from Settings). */
object Tour {
    var visible by mutableStateOf(false)
    private const val KEY = "tour_done_v1"

    fun maybeStart(c: Context) {
        if (!c.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean(KEY, false)) visible = true
    }

    fun finish(c: Context) {
        visible = false
        c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    val steps = listOf(
        "👆" to ("Tap to focus" to "Tap anywhere on the picture to focus and set the brightness there."),
        "✋" to ("Hold 2 seconds for manual focus" to "A ring fills, then a focus slider appears on the right edge. Tap the small ✕ to go back to autofocus."),
        "🔒" to ("Lock the exposure" to "Hold about half a second until it ticks, then let go. Drag the slider under the lock to brighten or darken."),
        "🤏" to ("Pinch to zoom" to "Use two fingers on the picture. The lens buttons switch between wide, main and tele."),
        "🎬" to ("Regular and smart modes" to "REGULAR has Photo and Video (and Pro). SMART has Portrait, Pano, Long exposure, Time-lapse, Slow-mo and more."),
        "⚙" to ("Settings and guides" to "The gear has the full guides, the Simple / Pro layout, remote monitor, backup and everything else."),
    )
}

@Composable
fun TourOverlay() {
    if (!Tour.visible) return
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(Pro.PanelSolid).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(step, label = "tour") { i ->
                val (icon, tt) = Tour.steps[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(icon, fontSize = 44.sp)
                    Text(tt.first, color = Pro.Amber, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                    Text(tt.second, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tour.steps.indices.forEach { i ->
                    Box(Modifier.size(if (i == step) 9.dp else 6.dp).clip(CircleShape).background(if (i == step) Pro.Amber else Color.White.copy(alpha = 0.3f)))
                }
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SKIP", color = Pro.Dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { Tour.finish(ctx) }.padding(horizontal = 16.dp, vertical = 10.dp))
                val last = step == Tour.steps.lastIndex
                Text(
                    if (last) "START SHOOTING" else "NEXT", color = Color.Black, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { if (last) Tour.finish(ctx) else step++ }.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Settings row to show the tour again. */
@Composable
fun TourButton() {
    Text(
        "▶ SHOW THE QUICK TOUR AGAIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { Tour.visible = true }.padding(8.dp),
    )
}
