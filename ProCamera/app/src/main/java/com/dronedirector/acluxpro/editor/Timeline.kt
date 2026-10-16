package com.dronedirector.acluxpro.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.ui.Pro
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Evenly spaced frames of a clip's whole source, for the filmstrip. */
class Filmstrip(val frames: List<Bitmap>, val sourceDurationMs: Long) {
    fun frameAt(sourceMs: Long): Bitmap? {
        if (frames.isEmpty()) return null
        val i = ((sourceMs.toFloat() / sourceDurationMs) * frames.size).toInt().coerceIn(0, frames.lastIndex)
        return frames[i]
    }
}

/**
 * Multi-track timeline: ruler, V1 clips (filmstrips), one row per layer and the music track.
 * The playhead stays in the centre; drag to scrub, pinch or use the buttons to zoom,
 * Fit shows the whole edit.
 */
/** Zoom of the timeline; the zoom buttons live under the player. */
class TimelineZoom {
    var pxPerSec by mutableFloatStateOf(60f)
    internal var minPx: () -> Float = { 0.5f }
    fun zoomIn() { pxPerSec = (pxPerSec * 1.6f).coerceAtMost(600f) }
    fun zoomOut() { pxPerSec = (pxPerSec / 1.6f).coerceIn(min(minPx(), 0.5f), 600f) }
    fun fit() { pxPerSec = minPx().coerceIn(0.5f, 600f) }
}

@Composable
fun TimelineView(
    clips: List<Clip>,
    layers: List<Layer>,
    music: MusicTrack?,
    strips: Map<String, Filmstrip>,
    globalMs: Long,
    selectedClip: Int,
    selectedLayerId: String?,
    onScrub: (ms: Long, final: Boolean) -> Unit,
    onSelectClip: (Int) -> Unit,
    onSelectLayer: (String) -> Unit,
    zoom: TimelineZoom,
    onSelectTransition: (Int) -> Unit = {},
    extraAudio: List<MusicTrack> = emptyList(),
    /** Hold-and-drag edits: a layer moved / trimmed in time, or moved to another row (index in [layers]). */
    onLayerTime: (id: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onLayerOrder: (id: String, toIndex: Int) -> Unit = { _, _ -> },
    /** An audio track moved / trimmed: new timeline start, in-song offset and length. */
    onAudioTime: (id: String, startMs: Long, offsetMs: Long, lengthMs: Long) -> Unit = { _, _, _, _ -> },
) {
    val density = LocalDensity.current
    val totalMs = timelineLength(clips).coerceAtLeast(1)
    val starts = clipStarts(clips)
    // Zoom in pixels per second.
    var pxPerSec by zoom::pxPerSec
    var dragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableStateOf(0L) }
    var viewportW by remember { mutableFloatStateOf(1f) }
    var fittedOnce by remember { mutableStateOf(false) }
    val headMs = if (dragging) dragMs else globalMs
    val totalNow by rememberUpdatedState(totalMs)
    val minPx = { viewportW * 0.9f / (totalNow / 1000f) }

    zoom.minPx = minPx
    fun fit() = zoom.fit()
    LaunchedEffect(viewportW, totalMs) { if (!fittedOnce && viewportW > 10f) { fit(); fittedOnce = true } }

    val ruler = with(density) { 22.dp.toPx() }
    val clipRow = with(density) { 52.dp.toPx() }
    val layerRow = with(density) { 24.dp.toPx() }
    val musicRow = with(density) { 24.dp.toPx() }
    val gap = with(density) { 3.dp.toPx() }
    val shownLayers = layers.asReversed()
    val audioRows = listOfNotNull(music) + extraAudio
    val heightPx = ruler + clipRow + gap + shownLayers.size * (layerRow + gap) + audioRows.size * (musicRow + gap) + gap
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // The item being held (highlighted while it's moved or trimmed).
    var editingId by remember { mutableStateOf<String?>(null) }
    val curAudio by rememberUpdatedState(audioRows)
    val curLayerTime by rememberUpdatedState(onLayerTime)
    val curLayerOrder by rememberUpdatedState(onLayerOrder)
    val curAudioTime by rememberUpdatedState(onAudioTime)
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD } }
    val dimPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); typeface = Typeface.MONOSPACE } }
    val accent = Pro.Amber

    val currentHead by rememberUpdatedState(headMs)
    val currentScrub by rememberUpdatedState(onScrub)
    val curClips by rememberUpdatedState(clips)
    val curLayers by rememberUpdatedState(shownLayers)
    val curTotal by rememberUpdatedState(totalMs)
    val curSelectClip by rememberUpdatedState(onSelectClip)
    val curSelectLayer by rememberUpdatedState(onSelectLayer)
    val curSelectTransition by rememberUpdatedState(onSelectTransition)

    Column(Modifier.fillMaxWidth().background(Ed.Bg)) {
        // Toolbar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tc(headMs), style = Pro.Value.copy(color = accent, fontSize = 13.sp))
            Text(" / ${tc(totalMs)}", style = Pro.Value.copy(color = Pro.Dim, fontSize = 11.sp), modifier = Modifier.weight(1f))
            Text("pinch to zoom", style = Pro.Label.copy(fontSize = 9.sp))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(with(density) { heightPx.toDp() })
                .pointerInput(Unit) {
                    // One finger: scrub. Two fingers: pinch to zoom.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = 0f
                        dragging = false
                        dragMs = currentHead
                        // ---- Hold a layer / audio bar to move it, or hold its edge to trim it.
                        val centerX0 = size.width / 2f
                        fun xOfMs(ms: Long) = centerX0 + (ms - currentHead) * pxPerSec / 1000f
                        val edgePx = 18.dp.toPx()
                        var hitId: String? = null; var hitAudio = false; var hitEdge = 0   // -1 start, 0 body, 1 end
                        var hitStart = 0L; var hitEnd = 0L; var hitOffset = 0L; var hitRow = -1
                        run {
                            val y = down.position.y; val x = down.position.x
                            var top = ruler + clipRow + gap
                            curLayers.forEachIndexed { r, l ->
                                if (y in top..(top + layerRow)) {
                                    val a = if (l.wholeVideo) 0L else l.startMs; val b = if (l.wholeVideo) curTotal else l.endMs
                                    val xa = xOfMs(a); val xb = xOfMs(b)
                                    if (x in (xa - edgePx)..(xb + edgePx)) {
                                        hitId = l.id; hitStart = a; hitEnd = b; hitRow = r
                                        hitEdge = if (x < xa + edgePx) -1 else if (x > xb - edgePx) 1 else 0
                                    }
                                }
                                top += layerRow + gap
                            }
                            curAudio.forEach { m ->
                                if (y in top..(top + musicRow)) {
                                    val a = m.startMs; val b = m.startMs + m.playLength(curTotal)
                                    val xa = xOfMs(a); val xb = xOfMs(b)
                                    if (x in (xa - edgePx)..(xb + edgePx)) {
                                        hitId = m.id; hitAudio = true; hitStart = a; hitEnd = b; hitOffset = m.offsetMs
                                        hitEdge = if (x < xa + edgePx) -1 else if (x > xb - edgePx) 1 else 0
                                    }
                                }
                                top += musicRow + gap
                            }
                        }
                        if (hitId != null) {
                            val held = withTimeoutOrNull(380L) {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.first()
                                    if (!c.pressed || ev.changes.size > 1) return@withTimeoutOrNull false
                                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                                }
                                @Suppress("UNREACHABLE_CODE") false
                            } == null
                            if (held) {
                                val id = hitId!!
                                editingId = id
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                if (!hitAudio) curSelectLayer(id)
                                val minLen = 200L
                                var lastRow = hitRow
                                do {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.first()
                                    val dx = ((c.position.x - down.position.x) / pxPerSec * 1000f).toLong()
                                    if (hitAudio) {
                                        val m = curAudio.firstOrNull { it.id == id }
                                        if (m != null) when (hitEdge) {
                                            0 -> curAudioTime(id, (hitStart + dx).coerceAtLeast(0), hitOffset, hitEnd - hitStart)
                                            -1 -> {
                                                // Trim the front: start later in the song (or earlier, back to its beginning).
                                                val d = dx.coerceIn(-hitOffset, hitEnd - hitStart - minLen).coerceAtLeast(-hitStart)
                                                curAudioTime(id, hitStart + d, hitOffset + d, hitEnd - hitStart - d)
                                            }
                                            else -> {
                                                val maxLen = m.durationMs - hitOffset
                                                curAudioTime(id, hitStart, hitOffset, (hitEnd - hitStart + dx).coerceIn(minLen, maxLen))
                                            }
                                        }
                                    } else {
                                        when (hitEdge) {
                                            0 -> {
                                                val len = hitEnd - hitStart
                                                val ns = (hitStart + dx).coerceIn(0, (curTotal - len).coerceAtLeast(0))
                                                curLayerTime(id, ns, ns + len)
                                                // Up / down: change the stacking order.
                                                val rowH = layerRow + gap
                                                val row = (hitRow + ((c.position.y - down.position.y) / rowH).let { if (it < 0) it - 0.5f else it + 0.5f }.toInt())
                                                    .coerceIn(0, curLayers.size - 1)
                                                if (row != lastRow) {
                                                    lastRow = row
                                                    curLayerOrder(id, curLayers.size - 1 - row)
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                            -1 -> curLayerTime(id, (hitStart + dx).coerceIn(0, hitEnd - minLen), hitEnd)
                                            else -> curLayerTime(id, hitStart, (hitEnd + dx).coerceIn(hitStart + minLen, curTotal))
                                        }
                                    }
                                    ev.changes.forEach { it.consume() }
                                } while (ev.changes.any { it.pressed })
                                editingId = null
                                return@awaitEachGesture
                            }
                        }
                        do {
                            val ev = awaitPointerEvent()
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            if (ev.changes.size > 1 && zoom != 1f) {
                                pxPerSec = (pxPerSec * zoom).coerceIn(min(minPx(), 0.5f), 600f)
                            } else if (pan.x != 0f) {
                                moved += abs(pan.x)
                                if (moved > 6f) {
                                    dragging = true
                                    dragMs = (dragMs - (pan.x / pxPerSec * 1000f).toLong()).coerceIn(0, curTotal)
                                    currentScrub(dragMs, false)
                                }
                            }
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (ev.changes.any { it.pressed })
                        if (dragging) {
                            currentScrub(dragMs, true)
                            dragging = false
                        } else {
                            // A tap: select whatever is under the finger.
                            val centerX = size.width / 2f
                            val ms = (currentHead + ((down.position.x - centerX) / pxPerSec * 1000f)).toLong()
                            val y = down.position.y
                            if (y in ruler..(ruler + clipRow)) {
                                val st = clipStarts(curClips)
                                // A tap on a transition (or near a cut) opens the transition.
                                val tol = (24f / pxPerSec * 1000f).toLong()
                                val cut = (0 until curClips.size - 1).firstOrNull { i ->
                                    val ov = overlapMs(curClips, i)
                                    ms in (st[i + 1] - tol)..(st[i + 1] + ov + tol) && y > ruler + clipRow * 0.55f
                                }
                                if (cut != null) curSelectTransition(cut)
                                else curSelectClip(clipAt(curClips, ms).first)
                            } else {
                                var top = ruler + clipRow + gap
                                for (l in curLayers) {
                                    if (y in top..(top + layerRow)) { curSelectLayer(l.id); break }
                                    top += layerRow + gap
                                }
                            }
                            if (y < ruler) currentScrub(ms.coerceIn(0, curTotal), true)
                        }
                    }
                },
        ) {
            val w = constraints.maxWidth.toFloat()
            LaunchedEffect(w) { viewportW = w }
            Canvas(Modifier.matchParentSize()) {
                val centerX = size.width / 2f
                val pxPerMs = pxPerSec / 1000f
                fun xOf(ms: Long) = centerX + (ms - headMs) * pxPerMs

                // Ruler
                drawRect(Ed.Panel, Offset(0f, 0f), Size(size.width, ruler))
                val step = tickStep(pxPerSec)
                val firstTick = (((headMs - centerX / pxPerMs) / step).toLong() * step).coerceAtLeast(0)
                textPaint.textSize = 9.sp.toPx()
                dimPaint.textSize = 9.sp.toPx()
                var t = firstTick
                while (t <= totalMs && xOf(t) <= size.width + 40) {
                    val x = xOf(t)
                    val major = (t / step) % 5 == 0L
                    drawLine(Color.White.copy(alpha = if (major) 0.6f else 0.25f), Offset(x, ruler - (if (major) ruler * 0.55f else ruler * 0.3f)), Offset(x, ruler), 1f)
                    if (major) drawContext.canvas.nativeCanvas.drawText(shortTc(t), x + 3f, ruler * 0.45f, dimPaint)
                    t += step
                }

                // V1 clips
                val top = ruler
                clips.forEachIndexed { i, c ->
                    val x0 = xOf(starts[i]); val x1 = xOf(starts[i] + c.lengthMs)
                    if (x1 < 0 || x0 > size.width) return@forEachIndexed
                    val rectW = (x1 - x0).coerceAtLeast(2f)
                    drawRoundRect(Ed.Raised, Offset(x0, top), Size(rectW, clipRow), CornerRadius(6f))
                    // Filmstrip
                    val strip = strips[c.uri.toString()]
                    if (strip != null) {
                        val tileW = clipRow * (if (c.height > 0) (c.width.toFloat() / c.height).coerceIn(0.5f, 2f) else 1.6f)
                        clipRect(max(x0, 0f), top, min(x1, size.width), top + clipRow) {
                            var tx = x0
                            while (tx < x1) {
                                if (tx + tileW >= 0 && tx <= size.width) {
                                    val timelineOffset = ((tx - x0) / pxPerMs).toLong()
                                    val src = c.startMs + (timelineOffset * c.speed).toLong()
                                    strip.frameAt(src)?.let { bmp ->
                                        drawImage(
                                            bmp.asImageBitmap(),
                                            dstOffset = IntOffset(tx.roundToInt(), top.roundToInt()),
                                            dstSize = IntSize(tileW.roundToInt(), clipRow.roundToInt()),
                                        )
                                    }
                                }
                                tx += tileW
                            }
                        }
                    }
                    val sel = i == selectedClip
                    drawRoundRect(if (sel) accent else Color.White.copy(alpha = 0.35f), Offset(x0, top), Size(rectW, clipRow), CornerRadius(6f), style = Stroke(if (sel) 4f else 1.5f))
                    // Label
                    if (rectW > 50f) {
                        drawRect(Color.Black.copy(alpha = 0.55f), Offset(max(x0, 0f) + 2f, top + clipRow - 16.dp.toPx()), Size(min(rectW - 4f, 220.dp.toPx()), 14.dp.toPx()))
                        textPaint.textSize = 9.sp.toPx()
                        val label = "${i + 1} ${c.name}" + (if (c.speed != 1f) "  ${c.speed}x" else "") + (if (!c.grade.isIdentity || !c.detail.isZero) "  ●" else "")
                        drawContext.canvas.nativeCanvas.drawText(label.take(40), max(x0, 0f) + 5f, top + clipRow - 5.dp.toPx(), textPaint)
                    }
                }

                // Transitions: a marker on every cut (tap to choose one).
                for (i in 0 until clips.size - 1) {
                    val ov = overlapMs(clips, i)
                    val xs = xOf(starts[i + 1]); val xe = xOf(starts[i + 1] + ov)
                    if (xe < -30 || xs > size.width + 30) continue
                    val mh = clipRow * 0.4f
                    val my = top + clipRow - mh - 2f
                    if (ov > 0) {
                        drawRoundRect(accent.copy(alpha = 0.85f), Offset(xs, my), Size((xe - xs).coerceAtLeast(10f), mh), CornerRadius(5f))
                        // bow-tie
                        val cx = (xs + xe) / 2f; val cy = my + mh / 2f; val r = mh * 0.32f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(cx - r, cy - r); lineTo(cx + r, cy + r); lineTo(cx + r, cy - r); lineTo(cx - r, cy + r); close()
                        }
                        drawPath(path, Color.Black.copy(alpha = 0.75f))
                        if (xe - xs > 70f) {
                            textPaint.textSize = 8.sp.toPx()
                            textPaint.color = android.graphics.Color.BLACK
                            drawContext.canvas.nativeCanvas.drawText(clips[i].transition.type.label.take(14), xs + 4f, my - 3f + mh, textPaint)
                            textPaint.color = android.graphics.Color.WHITE
                        }
                    } else {
                        drawCircle(Color.White.copy(alpha = 0.6f), mh * 0.3f, Offset(xs, my + mh / 2f))
                        drawCircle(Color.Black.copy(alpha = 0.6f), mh * 0.3f, Offset(xs, my + mh / 2f), style = Stroke(2f))
                    }
                }

                // Layer tracks
                var rowTop = ruler + clipRow + gap
                for (l in shownLayers) {
                    val s = if (l.wholeVideo) 0L else l.startMs
                    val e = if (l.wholeVideo) totalMs else l.endMs
                    val x0 = xOf(s); val x1 = xOf(e)
                    val col = when (l.kind) {
                        LayerKind.VIDEO -> Color(0xFF3D7BFF)
                        LayerKind.IMAGE, LayerKind.LOGO -> Color(0xFF8E5CFF)
                        LayerKind.STICKER -> Color(0xFFFF7EB6)
                        LayerKind.WATERMARK -> Color(0xFF7A8899)
                        LayerKind.TEXT -> Color(0xFF2EBD8F)
                    }.copy(alpha = if (l.visible) 0.85f else 0.3f)
                    drawRoundRect(col, Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), layerRow), CornerRadius(5f))
                    if (l.id == selectedLayerId || l.id == editingId) drawRoundRect(accent, Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), layerRow), CornerRadius(5f), style = Stroke(if (l.id == editingId) 5f else 3f))
                    // Edge grips
                    if (l.id == editingId || l.id == selectedLayerId) {
                        drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(x0 + 2f, rowTop + layerRow * 0.25f), Size(4f, layerRow * 0.5f), CornerRadius(2f))
                        drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(x1 - 6f, rowTop + layerRow * 0.25f), Size(4f, layerRow * 0.5f), CornerRadius(2f))
                    }
                    textPaint.textSize = 9.sp.toPx()
                    val name = when (l.kind) { LayerKind.TEXT, LayerKind.WATERMARK, LayerKind.STICKER -> l.text.replace('\n', ' '); else -> l.kind.label + " " + l.text }
                    drawContext.canvas.nativeCanvas.drawText(name.take(30), max(x0, 0f) + 5f, rowTop + layerRow * 0.68f, textPaint)
                    rowTop += layerRow + gap
                }

                // Audio tracks (music + extra audio), each at its own place on the timeline.
                for (m in audioRows) {
                    val x0 = xOf(m.startMs); val x1 = xOf(m.startMs + m.playLength(totalMs))
                    val isMain = m.id == music?.id
                    drawRoundRect((if (isMain) Color(0xFF1FA35A) else Color(0xFF0FA3A3)).copy(alpha = 0.8f), Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), musicRow), CornerRadius(5f))
                    var mx = max(x0, 0f) + 4f
                    while (mx < min(x1, size.width)) {
                        val hgt = musicRow * (0.25f + 0.5f * abs(kotlin.math.sin(mx * 0.37f) * kotlin.math.cos(mx * 0.11f)))
                        drawLine(Color.Black.copy(alpha = 0.35f), Offset(mx, rowTop + (musicRow - hgt) / 2), Offset(mx, rowTop + (musicRow + hgt) / 2), 2f)
                        mx += 4f
                    }
                    if (m.id == editingId) drawRoundRect(accent, Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), musicRow), CornerRadius(5f), style = Stroke(5f))
                    textPaint.textSize = 9.sp.toPx()
                    drawContext.canvas.nativeCanvas.drawText("♪ " + m.name.take(30), max(x0, 0f) + 5f, rowTop + musicRow * 0.68f, textPaint)
                    rowTop += musicRow + gap
                }

                // End of edit
                val endX = xOf(totalMs)
                if (endX in 0f..size.width) drawLine(Color.White.copy(alpha = 0.3f), Offset(endX, ruler), Offset(endX, size.height), 2f)

                // Playhead
                drawLine(accent, Offset(centerX, 0f), Offset(centerX, size.height), 3f)
                drawCircle(accent, 6.dp.toPx(), Offset(centerX, 6.dp.toPx()))
            }
        }
    }
}

@Composable
private fun TimelineButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Tick spacing (ms) so small ticks are at least ~14 px apart. */
private fun tickStep(pxPerSec: Float): Long {
    val steps = longArrayOf(40, 100, 200, 500, 1000, 2000, 5000, 10_000, 30_000, 60_000, 120_000, 300_000, 600_000)
    for (s in steps) if (s / 1000f * pxPerSec >= 14f) return s
    return 1_200_000
}

private fun shortTc(ms: Long): String {
    val s = ms / 1000; val m = s / 60; val h = m / 60
    val f = (ms % 1000) / 100
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m % 60, s % 60)
        ms % 1000 != 0L && ms < 60_000 -> "%d.%ds".format(s, f)
        else -> "%d:%02d".format(m, s % 60)
    }
}
