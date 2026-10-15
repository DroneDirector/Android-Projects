package com.dronedirector.acluxpro.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.ui.Pro

/** Transition browser for the cut after clip [index]. */
@Composable
fun TransitionsPanel(
    clips: List<Clip>, index: Int, thumbs: Map<String, Bitmap>,
    onChange: (Transition) -> Unit, onApplyAll: (Transition) -> Unit, onPreview: () -> Unit,
) {
    val a = clips.getOrNull(index)
    val b = clips.getOrNull(index + 1)
    SectionTitle("TRANSITIONS")
    if (a == null || b == null) {
        Text("Select a clip that has another clip after it — the transition goes on the cut between them.", color = Pro.Dim, fontSize = 12.sp)
        return
    }
    val t = a.transition
    Text("Clip ${index + 1} → clip ${index + 2}", color = Color.White, fontSize = 14.sp)
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("PREVIEW" to onPreview, "APPLY TO ALL CUTS" to { onApplyAll(t) }).forEach { (label, f) ->
            Text(label, style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = f).padding(8.dp))
        }
    }
    val maxMs = (minOf(a.lengthMs, b.lengthMs) * 0.45f).coerceAtLeast(200f)
    if (t.type != TransitionType.NONE) {
        EdSlider("Duration", t.durationMs / 1000f, 0.2f..3f, 0.8f, { "%.1f s".format(it) }) { onChange(t.copy(durationMs = (it * 1000).toLong())) }
        if (t.durationMs > maxMs) Text("Limited to %.1f s by the clip lengths.".format(maxMs / 1000f), color = Pro.Dim, fontSize = 11.sp)
    }
    val anim = rememberInfiniteTransition(label = "tr")
    val p by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val pp = ((p - 0.15f) / 0.7f).coerceIn(0f, 1f)
    val ta = thumbs[a.id]; val tb = thumbs[b.id]
    TransitionGroup.entries.forEach { g ->
        SectionTitle(g.label.uppercase())
        TransitionType.entries.filter { it.group == g }.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { type ->
                    TransitionTile(type, type == t.type, ta, tb, pp, Modifier.weight(1f)) { onChange(t.copy(type = type)) }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TransitionTile(type: TransitionType, selected: Boolean, a: Bitmap?, b: Bitmap?, p: Float, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Pro.Amber else Pro.Line, RoundedCornerShape(8.dp))
        ) {
            val showP = if (type == TransitionType.NONE) (if (p < 0.5f) 0f else 1f) else p
            TileSide(a, Color(0xFF2B4C7E), false, type, showP)
            TileSide(b, Color(0xFF7E4A2B), true, type, showP)
        }
        Text(
            type.label, color = if (selected) Pro.Amber else Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun TileSide(bmp: Bitmap?, fallback: Color, incoming: Boolean, type: TransitionType, p: Float) {
    val shader = remember { if (android.os.Build.VERSION.SDK_INT >= 33) android.graphics.RuntimeShader(TransitionMath.AGSL) else null }
    val mod = Modifier.fillMaxSize().graphicsLayer {
        val (tx, ty) = TransitionMath.move(type, p, incoming)
        translationX = tx * size.width; translationY = ty * size.height
        if (type == TransitionType.NONE) { alpha = if (incoming) p else 1f; return@graphicsLayer }
        if (shader != null && android.os.Build.VERSION.SDK_INT >= 33 && size.width > 0f) {
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uType", type.id)
            shader.setFloatUniform("uP", p)
            shader.setFloatUniform("uSide", if (incoming) 1f else 0f)
            shader.setFloatUniform("uMode", if (incoming && type.kind != TransitionKind.MOVE) 1f else 0f)
            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            clip = true
        } else if (incoming && type.kind != TransitionKind.MOVE) alpha = p
    }
    if (bmp != null) Image(bmp.asImageBitmap(), null, mod, contentScale = ContentScale.Crop)
    else Box(mod.background(Brush.linearGradient(listOf(fallback, fallback.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
        Text(if (incoming) "B" else "A", color = Color.White, fontSize = 16.sp)
    }
}

/** Crop / zoom / reposition and stabilisation controls for a clip. */
@Composable
fun ReframePanel(
    c: Clip, editingEnd: Boolean, cropMode: Boolean, stabProgress: Int?, analysed: Boolean,
    onEditEnd: (Boolean) -> Unit, onCropMode: (Boolean) -> Unit,
    onChange: ((Clip) -> Clip) -> Unit, onAnalyze: () -> Unit,
) {
    val r = c.reframe
    val f = if (r.animate && editingEnd) r.end else r.start
    fun setF(nf: Frame) = onChange { cl -> cl.copy(reframe = if (cl.reframe.animate && editingEnd) cl.reframe.copy(end = nf) else cl.reframe.copy(start = nf)) }
    SectionTitle("CROP & ZOOM", onReset = { onChange { it.copy(reframe = Reframe()) } })
    Text(
        if (cropMode) "DONE — STOP EDITING ON THE PLAYER" else "CROP ON THE PLAYER (PINCH · DRAG · TWIST)",
        style = Pro.Label.copy(color = if (cropMode) Color.Black else Pro.Amber, fontSize = 10.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (cropMode) Pro.Amber else Ed.Raised).clickable { onCropMode(!cropMode) }.padding(10.dp),
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Animate (Ken Burns)", color = Color.White, fontSize = 13.sp)
            Text("Moves smoothly from a start framing to an end framing over the clip", color = Pro.Dim, fontSize = 11.sp)
        }
        Switch(r.animate, { a -> onChange { it.copy(reframe = it.reframe.copy(animate = a, end = if (a && it.reframe.end == Frame()) it.reframe.start.copy(zoom = it.reframe.start.zoom * 1.3f) else it.reframe.end)) } },
            colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
    if (r.animate) Chips(listOf("Edit start", "Edit end"), if (editingEnd) 1 else 0) { onEditEnd(it == 1) }
    EdSlider("Zoom", f.zoom, 0.25f..4f, 1f, { "%.2fx".format(it) }) { setF(f.copy(zoom = it)) }
    EdSlider("Position X", f.x, -1f..1f, 0f) { setF(f.copy(x = it)) }
    EdSlider("Position Y", f.y, -1f..1f, 0f) { setF(f.copy(y = it)) }
    EdSlider("Rotate", f.rotation, -180f..180f, 0f, { "%.1f°".format(it) }) { setF(f.copy(rotation = it)) }
    Chips(listOf("Fit 1×", "1.25×", "1.5×", "2× punch-in", "Flip ⇆"), -1) { i ->
        when (i) {
            0 -> setF(Frame()); 1 -> setF(f.copy(zoom = 1.25f)); 2 -> setF(f.copy(zoom = 1.5f)); 3 -> setF(f.copy(zoom = 2f))
            else -> onChange { it.copy(reframe = it.reframe.copy(flip = !it.reframe.flip)) }
        }
    }

    SectionTitle("STABILIZE")
    val s = c.stab
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Stabilize shaky footage", color = Color.White, fontSize = 13.sp)
            Text(
                when {
                    stabProgress != null -> "Analysing motion… $stabProgress%"
                    s.enabled && analysed -> "Analysed ✓ — smoothing applied in preview and export"
                    s.enabled -> "Needs analysis"
                    else -> "Measures camera shake and smooths it out (crops in slightly)"
                },
                color = if (s.enabled && analysed) Pro.Green else Pro.Dim, fontSize = 11.sp,
            )
        }
        Switch(s.enabled, { on -> onChange { it.copy(stab = it.stab.copy(enabled = on)) }; if (on && !analysed) onAnalyze() },
            colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
    if (s.enabled) {
        Chips(listOf("Smooth motion", "Locked (tripod)"), if (s.tripod) 1 else 0) { i -> onChange { it.copy(stab = it.stab.copy(tripod = i == 1)) } }
        if (!s.tripod) EdSlider("Smoothness", s.strength, 0f..1f, 0.6f, ::pct) { v -> onChange { it.copy(stab = it.stab.copy(strength = v)) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto crop (hide moving edges)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(s.autoZoom, { v -> onChange { it.copy(stab = it.stab.copy(autoZoom = v)) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
        if (!analysed && stabProgress == null) Text(
            "ANALYSE NOW", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onAnalyze).padding(8.dp),
        )
    }
}
