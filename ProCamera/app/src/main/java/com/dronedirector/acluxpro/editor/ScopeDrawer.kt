package com.dronedirector.acluxpro.editor

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.ui.Pro
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
fun scopeDrawerOpen(c: Context) = prefs(c).getBoolean("scope_drawer_open", false)

/**
 * RGB histogram in a drawer on the right edge of the player: drag the tab (or the panel) to
 * the left to pull it out, to the right to tuck it away; a tap on the tab toggles it.
 */
@Composable
fun ScopeDrawer(hist: Array<FloatArray>?, modifier: Modifier = Modifier, onOpenChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val panelW = 176.dp
    val panelPx = with(density) { panelW.toPx() }
    // 0 = tucked away, 1 = fully out.
    val p = remember { Animatable(if (scopeDrawerOpen(context)) 1f else 0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onOpenChange(p.value > 0.5f) }
    fun settle(open: Boolean) {
        prefs(context).edit().putBoolean("scope_drawer_open", open).apply()
        onOpenChange(open)
        scope.launch { p.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
    }
    val drag = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { settle(p.value > 0.5f) },
            onDragCancel = { settle(p.value > 0.5f) },
        ) { ch, dx ->
            ch.consume()
            scope.launch { p.snapTo((p.value - dx / panelPx).coerceIn(0f, 1f)) }
        }
    }
    Row(
        modifier.offset { IntOffset(((1f - p.value) * panelPx).roundToInt(), 0) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tab that stays visible on the edge.
        Box(
            Modifier
                .width(22.dp).height(64.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.75f))))
                .border(1.dp, Pro.Amber.copy(alpha = 0.55f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .then(drag)
                .pointerInput(Unit) { detectTapGestures { settle(p.value < 0.5f) } },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.BarChart, contentDescription = "RGB histogram", tint = Pro.Amber, modifier = Modifier.size(14.dp))
                Canvas(Modifier.padding(top = 4.dp).size(4.dp, 18.dp)) {
                    for (i in 0..2) drawCircle(Color.White.copy(alpha = 0.5f), 1.5.dp.toPx(), Offset(size.width / 2, size.height * (0.15f + i * 0.35f)))
                }
            }
        }
        // The panel.
        Column(
            Modifier
                .width(panelW)
                .shadow(10.dp, RoundedCornerShape(topStart = 4.dp, bottomStart = 14.dp))
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 14.dp))
                .background(Color(0xE60B0D10))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 4.dp, bottomStart = 14.dp))
                .then(drag)
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RGB", style = Pro.Label.copy(color = Pro.Amber, fontSize = 9.sp))
                Text("  HISTOGRAM", style = Pro.Label.copy(fontSize = 9.sp), modifier = Modifier.weight(1f))
                for ((c, l) in listOf(Color(0xFFFF4D4D) to "R", Color(0xFF4DFF6A) to "G", Color(0xFF4D8BFF) to "B")) {
                    Text(l, style = Pro.Label.copy(color = c, fontSize = 9.sp), modifier = Modifier.padding(start = 4.dp))
                }
            }
            Scope(hist, Modifier.padding(top = 4.dp).fillMaxWidth().height(70.dp))
            Text("Paused frame · drag to tuck away", style = Pro.Label.copy(fontSize = 8.sp), modifier = Modifier.padding(top = 3.dp))
        }
    }
}
