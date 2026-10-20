package com.dronedirector.veyrocutpro.editor

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
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import com.dronedirector.veyrocutpro.ui.Text
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
import com.dronedirector.veyrocutpro.ui.Pro
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
 * The edit starts at the left edge; drag to move the playhead, pinch or use the buttons to zoom,
 * Fit shows the whole edit.
 */
/** Zoom of the timeline; the zoom buttons live under the player. */
class TimelineZoom {
    var pxPerSec by mutableFloatStateOf(60f)
    internal var minPx: () -> Float = { 0.5f }
    fun zoomIn() { pxPerSec = (pxPerSec * 1.6f).coerceAtMost(600f) }
    fun zoomOut() { pxPerSec = (pxPerSec / 1.6f).coerceIn(min(minPx(), 0.5f), 600f) }
    fun fit() { pxPerSec = minPx().coerceIn(0.5f, 600f); scrollMs = 0L }
    /** Time at the left edge of the timeline view (the edit starts at the left). */
    var scrollMs by mutableStateOf(0L)
    /** Length of the edit and how much of it the view shows (ms), for the horizontal scroll wheel. */
    var totalMs by mutableStateOf(1L)
    var viewMs by mutableStateOf(1L)
    /** How far the view can scroll (ms). */
    val maxScrollMs: Long get() = (totalMs - viewMs).coerceAtLeast(0L)
    /** Moves the view by [dMs], kept inside the edit. */
    fun scrollBy(dMs: Long) { scrollMs = (scrollMs + dMs).coerceIn(0L, maxScrollMs) }
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
    /** Track header switches: mute / lock the video track, show / lock layers, mute / lock audio. */
    videoMuted: Boolean = false,
    videoLocked: Boolean = false,
    onVideoMute: () -> Unit = {},
    onVideoLock: () -> Unit = {},
    onLayerFlags: (Layer) -> Unit = {},
    onAudioFlags: (MusicTrack) -> Unit = {},
    /** Something locked was held. */
    onLocked: () -> Unit = {},
    /** Real audio peaks per file (uri string → peaks every [Waveforms.BUCKET_MS]). */
    waves: Map<String, FloatArray> = emptyMap(),
    /** Timeline markers (ms) and the marker / snapping buttons. */
    markers: List<Long> = emptyList(),
    snapping: Boolean = true,
    onToggleMarker: () -> Unit = {},
    onToggleSnap: () -> Unit = {},
    /** "+ VIDEO": add a new video layer (above or below the main video). */
    onAddVideoLayer: (() -> Unit)? = null,
    /** A V1 clip's in / out point changed by dragging its edge (source ms). */
    onClipTrim: (index: Int, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
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
    // Track header column on the left (mute / show and lock per row); the edit starts right after it.
    val headerW = with(density) { 50.dp.toPx() }
    val leftPad = headerW + with(density) { 6.dp.toPx() }
    val totalNow by rememberUpdatedState(totalMs)
    val minPx = { viewportW * 0.9f / (totalNow / 1000f) }

    zoom.minPx = minPx
    androidx.compose.runtime.SideEffect {
        zoom.totalMs = totalMs
        zoom.viewMs = ((viewportW - leftPad) / pxPerSec * 1000f).toLong().coerceAtLeast(1)
    }
    fun fit() = zoom.fit()
    LaunchedEffect(viewportW, totalMs) { if (!fittedOnce && viewportW > 10f) { fit(); fittedOnce = true } }
    // Keep the playhead in view while playing / after zooming (the view scrolls by itself).
    LaunchedEffect(globalMs, pxPerSec, viewportW) {
        if (dragging || viewportW < 10f) return@LaunchedEffect
        val viewMs = ((viewportW - leftPad) / pxPerSec * 1000f).toLong().coerceAtLeast(1)
        val head = globalMs
        if (head < zoom.scrollMs || head > zoom.scrollMs + viewMs * 0.85f) {
            zoom.scrollMs = (head - (viewMs * 0.15f).toLong()).coerceAtLeast(0)
        }
    }

    val ruler = with(density) { 22.dp.toPx() }
    val clipRow = with(density) { 52.dp.toPx() }
    val layerRow = with(density) { 24.dp.toPx() }
    val musicRow = with(density) { 24.dp.toPx() }
    val gap = with(density) { 3.dp.toPx() }
    val shownLayers = layers.asReversed()
    // Rows: layers on top of the video above V1 (topmost first),
    // video layers placed below the main video under V1, then the audio tracks.
    val aboveRows = shownLayers.filter { !it.isBelow }
    val belowRows = shownLayers.filter { it.isBelow }
    val clipTop = ruler + aboveRows.size * (layerRow + gap)
    val layerTops: Map<String, Float> = buildMap {
        aboveRows.forEachIndexed { i, l -> put(l.id, ruler + i * (layerRow + gap)) }
        belowRows.forEachIndexed { j, l -> put(l.id, clipTop + clipRow + gap + j * (layerRow + gap)) }
    }
    val audioTop = clipTop + clipRow + gap + belowRows.size * (layerRow + gap)
    var editingClip by remember { mutableStateOf(-1) }
    val audioRows = listOfNotNull(music) + extraAudio
    val heightPx = ruler + clipRow + gap + shownLayers.size * (layerRow + gap) + audioRows.size * (musicRow + gap) + gap
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // The item being held (highlighted while it's moved or trimmed).
    var editingId by remember { mutableStateOf<String?>(null) }
    val curAudio by rememberUpdatedState(audioRows)
    val curLayerTime by rememberUpdatedState(onLayerTime)
    val curLayerOrder by rememberUpdatedState(onLayerOrder)
    val curAudioTime by rememberUpdatedState(onAudioTime)
    val curLocked by rememberUpdatedState(onLocked)
    val curMarkers by rememberUpdatedState(markers)
    val curSnap by rememberUpdatedState(snapping)
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD } }
    val dimPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); typeface = Typeface.MONOSPACE } }
    val accent = Pro.Amber

    val currentHead by rememberUpdatedState(headMs)
    val currentScrub by rememberUpdatedState(onScrub)
    val curClips by rememberUpdatedState(clips)
    val curLayers by rememberUpdatedState(shownLayers)
    val curLayerTops by rememberUpdatedState(layerTops)
    val curClipTop by rememberUpdatedState(clipTop)
    val curAudioTop by rememberUpdatedState(audioTop)
    val curClipTrim by rememberUpdatedState(onClipTrim)
    val curVideoLocked by rememberUpdatedState(videoLocked)
    val curTotal by rememberUpdatedState(totalMs)
    val curSelectClip by rememberUpdatedState(onSelectClip)
    val curSelectLayer by rememberUpdatedState(onSelectLayer)
    val curSelectTransition by rememberUpdatedState(onSelectTransition)

    Column(Modifier.fillMaxWidth().background(Ed.Bg)) {
        // Toolbar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tc(headMs), style = Pro.Value.copy(color = accent, fontSize = 13.sp))
            Text(" / ${tc(totalMs)}", style = Pro.Value.copy(color = Pro.Dim, fontSize = 11.sp), modifier = Modifier.weight(1f))
            if (onAddVideoLayer != null) Text(
                "+ VIDEO", style = Pro.Label.copy(color = Color(0xFF7FA8FF), fontSize = 10.sp),
                modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3D7BFF).copy(alpha = 0.18f)).clickable(onClick = onAddVideoLayer).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                "◆ MARKER", style = Pro.Label.copy(color = accent, fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onToggleMarker).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                if (snapping) "SNAP ✓" else "SNAP", style = Pro.Label.copy(color = if (snapping) Color.Black else Color.White, fontSize = 10.sp),
                modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(if (snapping) accent else Color.White.copy(alpha = 0.1f)).clickable(onClick = onToggleSnap).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(with(density) { heightPx.toDp() })
                .pointerInput(Unit) {
                    // One finger: move the playhead. Two fingers: pinch to zoom, pan to scroll.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // The track header has its own buttons.
                        if (down.position.x < headerW && down.position.y > ruler) return@awaitEachGesture
                        var moved = 0f
                        dragging = false
                        dragMs = currentHead
                        // ---- Hold a layer / audio bar to move it, or hold its edge to trim it.
                        fun xOfMs(ms: Long) = leftPad + (ms - zoom.scrollMs) * pxPerSec / 1000f
                        fun msAt(x: Float) = zoom.scrollMs + ((x - leftPad) / pxPerSec * 1000f).toLong()
                        fun clampScroll() {
                            val viewMs = ((size.width - leftPad) / pxPerSec * 1000f).toLong()
                            zoom.scrollMs = zoom.scrollMs.coerceIn(0, (curTotal - viewMs / 2).coerceAtLeast(0))
                        }
                        // Trim grips: wide touch zones (about 3–4× the old ones); on short bars a third of the bar each.
                        val edgePx = 34.dp.toPx()
                        val outPx = 14.dp.toPx()
                        fun edgeFor(xa: Float, xb: Float) = min(edgePx, ((xb - xa) / 3f).coerceAtLeast(10.dp.toPx()))
                        var hitId: String? = null; var hitAudio = false; var hitEdge = 0   // -1 start, 0 body, 1 end
                        var hitStart = 0L; var hitEnd = 0L; var hitOffset = 0L; var hitRow = -1
                        var hitClip = -1; var clipEdge = 0
                        run {
                            val y = down.position.y; val x = down.position.x
                            // V1: the start / end edge of a clip.
                            if (y in curClipTop..(curClipTop + clipRow)) {
                                val st = clipStarts(curClips)
                                var best = Float.MAX_VALUE
                                curClips.forEachIndexed { i, cl ->
                                    val xa = xOfMs(st[i]); val xb = xOfMs(st[i] + cl.lengthMs)
                                    val ep = edgeFor(xa, xb)
                                    val dl = x - xa; val dr = xb - x
                                    if (dl >= -outPx && dl <= ep && abs(dl) < best) { best = abs(dl); hitClip = i; clipEdge = -1 }
                                    if (dr >= -outPx && dr <= ep && abs(dr) < best) { best = abs(dr); hitClip = i; clipEdge = 1 }
                                }
                            }
                            curLayers.forEachIndexed { r, l ->
                                val top = curLayerTops[l.id] ?: return@forEachIndexed
                                if (y in top..(top + layerRow)) {
                                    val a = if (l.wholeVideo) 0L else l.startMs; val b = if (l.wholeVideo) curTotal else l.endMs
                                    val xa = xOfMs(a); val xb = xOfMs(b)
                                    val ep = edgeFor(xa, xb)
                                    if (x in (xa - outPx)..(xb + outPx)) {
                                        hitId = l.id; hitStart = a; hitEnd = b; hitRow = r
                                        hitEdge = if (x < xa + ep) -1 else if (x > xb - ep) 1 else 0
                                    }
                                }
                            }
                            var top = curAudioTop
                            curAudio.forEach { m ->
                                if (y in top..(top + musicRow)) {
                                    val a = m.startMs; val b = m.startMs + m.playLength(curTotal)
                                    val xa = xOfMs(a); val xb = xOfMs(b)
                                    val ep = edgeFor(xa, xb)
                                    if (x in (xa - outPx)..(xb + outPx)) {
                                        hitId = m.id; hitAudio = true; hitStart = a; hitEnd = b; hitOffset = m.offsetMs
                                        hitEdge = if (x < xa + ep) -1 else if (x > xb - ep) 1 else 0
                                    }
                                }
                                top += musicRow + gap
                            }
                        }
                        if (hitClip >= 0 && hitId == null) {
                            val heldClip = withTimeoutOrNull(380L) {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.first()
                                    if (!c.pressed || ev.changes.size > 1) return@withTimeoutOrNull false
                                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                                }
                                @Suppress("UNREACHABLE_CODE") false
                            } == null
                            if (heldClip) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                if (curVideoLocked) {
                                    curLocked()
                                    do { val ev = awaitPointerEvent(); ev.changes.forEach { it.consume() } } while (ev.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                                val idx = hitClip
                                val c0 = curClips[idx]
                                val sp = c0.speed.coerceAtLeast(0.05f)
                                val minSrc = (200 * sp).toLong()
                                editingClip = idx
                                curSelectClip(idx)
                                do {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.first()
                                    val dSrc = ((c.position.x - down.position.x) / pxPerSec * 1000f * sp).toLong()
                                    if (clipEdge < 0) curClipTrim(idx, (c0.startMs + dSrc).coerceIn(0, c0.endMs - minSrc), c0.endMs)
                                    else curClipTrim(idx, c0.startMs, (c0.endMs + dSrc).coerceIn(c0.startMs + minSrc, c0.durationMs))
                                    ev.changes.forEach { it.consume() }
                                } while (ev.changes.any { it.pressed })
                                editingClip = -1
                                return@awaitEachGesture
                            }
                        }
                        val hitLocked = hitId != null && (if (hitAudio) curAudio.firstOrNull { it.id == hitId }?.locked == true else curLayers.firstOrNull { it.id == hitId }?.locked == true)
                        if (hitLocked) {
                            // Locked: a hold just says so; a short tap still selects it.
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
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                curLocked()
                                do { val ev = awaitPointerEvent(); ev.changes.forEach { it.consume() } } while (ev.changes.any { it.pressed })
                                return@awaitEachGesture
                            }
                            hitId = null
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
                                // Snapping: cuts, markers, the playhead and other items' edges.
                                val cands: LongArray = if (!curSnap) LongArray(0) else buildList {
                                    add(0L); add(curTotal); add(currentHead)
                                    val st = clipStarts(curClips)
                                    curClips.forEachIndexed { i, cl -> add(st[i]); add(st[i] + cl.lengthMs) }
                                    addAll(curMarkers)
                                    curLayers.forEach { if (it.id != id && !it.wholeVideo) { add(it.startMs); add(it.endMs) } }
                                    curAudio.forEach { if (it.id != id) { add(it.startMs); add(it.startMs + it.playLength(curTotal)) } }
                                }.toLongArray()
                                val snapTol = (12.dp.toPx() / pxPerSec * 1000f).toLong()
                                var lastSnap = Long.MIN_VALUE
                                fun nearest(ms: Long): Long? {
                                    var best: Long? = null; var bd = snapTol + 1
                                    for (cand in cands) { val d = abs(cand - ms); if (d < bd) { bd = d; best = cand } }
                                    return best
                                }
                                fun feel(target: Long?) {
                                    if (target != null && target != lastSnap) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    lastSnap = target ?: Long.MIN_VALUE
                                }
                                fun snap(ms: Long): Long { val n = nearest(ms); feel(n); return n ?: ms }
                                fun snapMove(start: Long, len: Long): Long {
                                    val a = nearest(start); val b = nearest(start + len)
                                    val r = when {
                                        a != null && (b == null || abs(a - start) <= abs(b - start - len)) -> { feel(a); a }
                                        b != null -> { feel(b); b - len }
                                        else -> { feel(null); start }
                                    }
                                    return r
                                }
                                do {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.first()
                                    val dx = ((c.position.x - down.position.x) / pxPerSec * 1000f).toLong()
                                    if (hitAudio) {
                                        val m = curAudio.firstOrNull { it.id == id }
                                        if (m != null) when (hitEdge) {
                                            0 -> curAudioTime(id, snapMove((hitStart + dx).coerceAtLeast(0), hitEnd - hitStart).coerceAtLeast(0), hitOffset, hitEnd - hitStart)
                                            -1 -> {
                                                // Trim the front: start later in the song (or earlier, back to its beginning).
                                                val d = (snap(hitStart + dx) - hitStart).coerceIn(-hitOffset, hitEnd - hitStart - minLen).coerceAtLeast(-hitStart)
                                                curAudioTime(id, hitStart + d, hitOffset + d, hitEnd - hitStart - d)
                                            }
                                            else -> {
                                                val maxLen = m.durationMs - hitOffset
                                                curAudioTime(id, hitStart, hitOffset, (snap(hitEnd + dx) - hitStart).coerceIn(minLen, maxLen))
                                            }
                                        }
                                    } else {
                                        when (hitEdge) {
                                            0 -> {
                                                val len = hitEnd - hitStart
                                                val ns = snapMove(hitStart + dx, len).coerceIn(0, (curTotal - len).coerceAtLeast(0))
                                                curLayerTime(id, ns, ns + len)
                                                // Up / down: change the stacking order.
                                                val rowH = layerRow + gap
                                                // Reorder within its group (above V1 or below V1).
                                                val below = curLayers.getOrNull(hitRow)?.isBelow == true
                                                val group = curLayers.indices.filter { curLayers[it].isBelow == below }
                                                val pos0 = group.indexOf(hitRow).coerceAtLeast(0)
                                                val steps = ((c.position.y - down.position.y) / rowH).let { if (it < 0) it - 0.5f else it + 0.5f }.toInt()
                                                val row = group.getOrElse((pos0 + steps).coerceIn(0, (group.size - 1).coerceAtLeast(0))) { hitRow }
                                                if (row != lastRow) {
                                                    lastRow = row
                                                    curLayerOrder(id, curLayers.size - 1 - row)
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                            -1 -> curLayerTime(id, snap(hitStart + dx).coerceIn(0, hitEnd - minLen), hitEnd)
                                            else -> {
                                                // Video layers can't run past the end of their own clip.
                                                val vl = curLayers.firstOrNull { it.id == id }
                                                val cap = if (vl != null && vl.kind == LayerKind.VIDEO && vl.videoDurationMs > 0)
                                                    minOf(curTotal, hitStart + (vl.videoDurationMs - vl.videoStartMs).coerceAtLeast(minLen)) else curTotal
                                                curLayerTime(id, hitStart, snap(hitEnd + dx).coerceIn(hitStart + minLen, cap.coerceAtLeast(hitStart + minLen)))
                                            }
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
                            val pinch = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            if (ev.changes.size > 1) {
                                // Two fingers: pinch to zoom around the fingers, pan to scroll.
                                moved += 100f
                                val cx = ev.calculateCentroid().x
                                val tAt = msAt(cx)
                                if (pinch != 1f) pxPerSec = (pxPerSec * pinch).coerceIn(min(minPx(), 0.5f), 600f)
                                zoom.scrollMs = tAt - ((cx - leftPad) / pxPerSec * 1000f).toLong() - (pan.x / pxPerSec * 1000f).toLong()
                                clampScroll()
                            } else if (pan.x != 0f || pan.y != 0f) {
                                // One finger: the playhead follows the finger (scrub); near the edges the view scrolls.
                                moved += abs(pan.x)
                                if (moved > 6f) {
                                    dragging = true
                                    val x = ev.changes.first().position.x
                                    val edge = 40.dp.toPx()
                                    if (x > size.width - edge) zoom.scrollMs += ((x - (size.width - edge)) / pxPerSec * 1000f * 0.25f).toLong()
                                    if (x < leftPad + edge) zoom.scrollMs -= (((leftPad + edge) - x) / pxPerSec * 1000f * 0.25f).toLong()
                                    clampScroll()
                                    dragMs = msAt(x).coerceIn(0, curTotal)
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
                            val ms = msAt(down.position.x)
                            val y = down.position.y
                            if (y < ruler) {
                                // A tap on the ruler moves the playhead there (onto a marker when close).
                                val tol = (14.dp.toPx() / pxPerSec * 1000f).toLong()
                                val mk = curMarkers.minByOrNull { abs(it - ms) }?.takeIf { abs(it - ms) <= tol }
                                currentScrub((mk ?: ms).coerceIn(0, curTotal), true)
                            } else if (y in curClipTop..(curClipTop + clipRow)) {
                                val st = clipStarts(curClips)
                                // A tap on a transition (or near a cut) opens the transition.
                                val tol = (24f / pxPerSec * 1000f).toLong()
                                val cut = (0 until curClips.size - 1).firstOrNull { i ->
                                    val ov = overlapMs(curClips, i)
                                    ms in (st[i + 1] - tol)..(st[i + 1] + ov + tol) && y > curClipTop + clipRow * 0.55f
                                }
                                if (cut != null) curSelectTransition(cut)
                                else curSelectClip(clipAt(curClips, ms).first)
                            } else {
                                for (l in curLayers) {
                                    val top = curLayerTops[l.id] ?: continue
                                    if (y in top..(top + layerRow)) { curSelectLayer(l.id); break }
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
                val scroll = zoom.scrollMs
                val pxPerMs = pxPerSec / 1000f
                fun xOf(ms: Long) = leftPad + (ms - scroll) * pxPerMs

                // Ruler
                drawRect(Ed.Panel, Offset(0f, 0f), Size(size.width, ruler))
                val step = tickStep(pxPerSec)
                val firstTick = ((scroll / step) * step).coerceAtLeast(0)
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
                val top = clipTop
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
                                    val src = c.srcAtOffset(timelineOffset)
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
                    // The clip's own sound as a waveform along the bottom edge.
                    waves[c.uri.toString()]?.let { pk ->
                        val wh = clipRow * 0.32f
                        val base = top + clipRow - 1f
                        drawRect(Color.Black.copy(alpha = 0.35f), Offset(max(x0, 0f), base - wh), Size(min(x1, size.width) - max(x0, 0f), wh))
                        var wx = max(x0, 0f) + 1f
                        val col = if (c.muted || videoMuted) Color.White.copy(alpha = 0.25f) else Color(0xFF7FE0A8).copy(alpha = 0.85f)
                        while (wx < min(x1, size.width)) {
                            val src = c.srcAtOffset(((wx - x0) / pxPerMs).toLong())
                            val h = wh * peakAt(pk, src, (3f / pxPerMs * c.speed).toLong())
                            drawLine(col, Offset(wx, base), Offset(wx, base - h), 2f)
                            wx += 3f
                        }
                    }
                    val sel = i == selectedClip
                    // Colour label: a stripe along the top of the clip.
                    c.label?.let { lc -> drawRect(Color(lc), Offset(max(x0, 0f), top), Size((min(x1, size.width) - max(x0, 0f)).coerceAtLeast(0f), 5.dp.toPx())) }
                    drawRoundRect(if (sel) accent else Color.White.copy(alpha = 0.35f), Offset(x0, top), Size(rectW, clipRow), CornerRadius(6f), style = Stroke(if (sel) 4f else 1.5f))
                    // Label
                    if (rectW > 50f) {
                        drawRect(Color.Black.copy(alpha = 0.55f), Offset(max(x0, 0f) + 2f, top + clipRow - 16.dp.toPx()), Size(min(rectW - 4f, 220.dp.toPx()), 14.dp.toPx()))
                        textPaint.textSize = 9.sp.toPx()
                        val label = "${i + 1} ${c.name}" + (if (c.ramp != null) "  ⤳${c.rampName ?: "ramp"}" else if (c.speed != 1f) "  ${c.speed}x" else "") + (if (!c.grade.isIdentity || !c.detail.isZero) "  ●" else "")
                        drawContext.canvas.nativeCanvas.drawText(label.take(40), max(x0, 0f) + 5f, top + clipRow - 5.dp.toPx(), textPaint)
                    }
                }

                if (videoLocked) clips.forEachIndexed { i, c -> lockHatch(xOf(starts[i]), xOf(starts[i] + c.lengthMs), top, clipRow) }
                else clips.forEachIndexed { i, c ->
                    val x0 = xOf(starts[i]); val x1 = xOf(starts[i] + c.lengthMs)
                    if (x1 < 0 || x0 > size.width) return@forEachIndexed
                    trimHandles(x0, x1, top, clipRow * 0.55f, i == editingClip || i == selectedClip, accent)
                    // Clip keyframes as diamonds along the top of the clip.
                    c.reframe.keys?.forEach { k ->
                        val kx = xOf(starts[i] + c.tlOffsetOf(c.startMs + k.t))
                        if (kx < x0 - 1f || kx > x1 + 1f) return@forEach
                        val cy = top + 8.dp.toPx(); val rr = 5.dp.toPx()
                        val d = androidx.compose.ui.graphics.Path().apply { moveTo(kx, cy - rr); lineTo(kx + rr, cy); lineTo(kx, cy + rr); lineTo(kx - rr, cy); close() }
                        drawPath(d, Color.White); drawPath(d, accent, style = Stroke(2f))
                    }
                    if (i == editingClip) lengthTag(x0, x1, top + 2f + 17.dp.toPx(), c.lengthMs, textPaint, accent)
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

                // Layer tracks (each at its own row: above or below V1)
                var rowTop: Float
                for (l in shownLayers) {
                    rowTop = layerTops[l.id] ?: continue
                    val s = if (l.wholeVideo) 0L else l.startMs
                    val e = if (l.wholeVideo) totalMs else l.endMs
                    val x0 = xOf(s); val x1 = xOf(e)
                    val col = when (l.kind) {
                        LayerKind.VIDEO -> Color(0xFF3D7BFF)
                        LayerKind.IMAGE, LayerKind.LOGO -> Color(0xFF8E5CFF)
                        LayerKind.STICKER -> Color(0xFFFF7EB6)
                        LayerKind.WATERMARK -> Color(0xFF7A8899)
                        LayerKind.TEXT -> Color(0xFF2EBD8F)
                        LayerKind.ADJUST -> Color(0xFFFFB300)
                        LayerKind.SHAPE -> Color(0xFFFF9500)
                        LayerKind.LOTTIE -> Color(0xFF00C7BE)
                    }.copy(alpha = if (l.visible) 0.85f else 0.3f)
                    drawRoundRect(col, Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), layerRow), CornerRadius(5f))
                    if (l.locked) lockHatch(x0, x1, rowTop, layerRow)
                    // Keyframes as small diamonds.
                    l.keys?.forEach { k ->
                        val kx = xOf(k.t); val cy = rowTop + layerRow / 2f; val r = layerRow * 0.28f
                        val d = androidx.compose.ui.graphics.Path().apply { moveTo(kx, cy - r); lineTo(kx + r, cy); lineTo(kx, cy + r); lineTo(kx - r, cy); close() }
                        drawPath(d, Color.White); drawPath(d, accent, style = Stroke(2f))
                    }
                    if (l.id == selectedLayerId || l.id == editingId) drawRoundRect(accent, Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), layerRow), CornerRadius(5f), style = Stroke(if (l.id == editingId) 5f else 3f))
                    // Trim handles on both ends (hold and drag to lengthen / shorten).
                    if (!l.locked) trimHandles(x0, x1, rowTop, layerRow, l.id == editingId || l.id == selectedLayerId, accent)
                    if (l.id == editingId) lengthTag(x0, x1, rowTop, e - s, textPaint, accent)
                    textPaint.textSize = 9.sp.toPx()
                    val name0 = l.name?.takeIf { it.isNotBlank() } ?: when (l.kind) { LayerKind.TEXT, LayerKind.WATERMARK, LayerKind.STICKER -> l.text.replace('\n', ' '); else -> l.kind.label + " " + l.text }
                    val name = if (l.kind == LayerKind.VIDEO) (if (l.isBelow) "↓ " else "↑ ") + name0 else name0
                    drawContext.canvas.nativeCanvas.drawText(name.take(30), max(x0, 0f) + 5f, rowTop + layerRow * 0.68f, textPaint)
                    rowTop += layerRow + gap
                }

                // Audio tracks (music + extra audio), each at its own place on the timeline.
                rowTop = audioTop
                for (m in audioRows) {
                    val x0 = xOf(m.startMs); val x1 = xOf(m.startMs + m.playLength(totalMs))
                    val isMain = m.id == music?.id
                    drawRoundRect((if (isMain) Color(0xFF1FA35A) else Color(0xFF0FA3A3)).copy(alpha = if (m.muted) 0.3f else 0.8f), Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), musicRow), CornerRadius(5f))
                    if (m.locked) lockHatch(x0, x1, rowTop, musicRow)
                    val peaks = waves[m.uri.toString()]
                    var mx = max(x0, 0f) + 1f
                    while (mx < min(x1, size.width)) {
                        val hgt = if (peaks != null) {
                            val srcMs = m.offsetMs + ((mx - x0) / pxPerMs).toLong()
                            musicRow * (0.06f + 0.86f * peakAt(peaks, srcMs, (3f / pxPerMs).toLong()))
                        } else musicRow * 0.12f
                        drawLine(Color.Black.copy(alpha = 0.45f), Offset(mx, rowTop + (musicRow - hgt) / 2), Offset(mx, rowTop + (musicRow + hgt) / 2), 2f)
                        mx += 3f
                    }
                    if (m.id == editingId) drawRoundRect(accent, Offset(x0, rowTop), Size((x1 - x0).coerceAtLeast(3f), musicRow), CornerRadius(5f), style = Stroke(5f))
                    if (!m.locked) trimHandles(x0, x1, rowTop, musicRow, m.id == editingId, accent)
                    if (m.id == editingId) lengthTag(x0, x1, rowTop, m.playLength(totalMs), textPaint, accent)
                    textPaint.textSize = 9.sp.toPx()
                    drawContext.canvas.nativeCanvas.drawText("♪ " + m.name.take(30), max(x0, 0f) + 5f, rowTop + musicRow * 0.68f, textPaint)
                    rowTop += musicRow + gap
                }

                // End of edit
                val endX = xOf(totalMs)
                if (endX in 0f..size.width) drawLine(Color.White.copy(alpha = 0.3f), Offset(endX, ruler), Offset(endX, size.height), 2f)

                // Markers: a flag on the ruler and a faint line through the tracks.
                for (mk in markers) {
                    val mx = xOf(mk)
                    if (mx < leftPad - 4f || mx > size.width + 4f) continue
                    drawLine(accent.copy(alpha = 0.35f), Offset(mx, ruler), Offset(mx, size.height), 1.5f)
                    val tri = androidx.compose.ui.graphics.Path().apply { moveTo(mx - 6f, 0f); lineTo(mx + 6f, 0f); lineTo(mx, ruler * 0.6f); close() }
                    drawPath(tri, accent)
                }

                // Playhead
                val headX = xOf(headMs)
                drawLine(accent, Offset(headX, 0f), Offset(headX, size.height), 3f)
                drawCircle(accent, 6.dp.toPx(), Offset(headX, 6.dp.toPx()))
            }
            // Track headers: one row per track, with its switches.
            Column(Modifier.width(with(density) { headerW.toDp() }).background(Ed.Panel)) {
                Box(Modifier.fillMaxWidth().height(with(density) { ruler.toDp() }))
                val layerHeader = @Composable { l: Layer ->
                    TrackHeader(with(density) { layerRow.toDp() }, null,
                        if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, !l.visible, "Show layer", { onLayerFlags(l.copy(visible = !l.visible)) },
                        l.locked, { onLayerFlags(l.copy(locked = !l.locked)) })
                }
                for (l in aboveRows) {
                    layerHeader(l)
                    Box(Modifier.height(with(density) { gap.toDp() }))
                }
                TrackHeader(with(density) { clipRow.toDp() }, "V1",
                    if (videoMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, videoMuted, "Mute video track", onVideoMute,
                    videoLocked, onVideoLock, vertical = true)
                for (l in belowRows) {
                    Box(Modifier.height(with(density) { gap.toDp() }))
                    layerHeader(l)
                }
                for (m in audioRows) {
                    Box(Modifier.height(with(density) { gap.toDp() }))
                    TrackHeader(with(density) { musicRow.toDp() }, null,
                        if (m.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, m.muted, "Mute audio track", { onAudioFlags(m.copy(muted = !m.muted)) },
                        m.locked, { onAudioFlags(m.copy(locked = !m.locked)) })
                }
            }
        }
    }
}

/** Loudest peak (0..1) over [spanMs] starting at [ms]. */
private fun peakAt(peaks: FloatArray, ms: Long, spanMs: Long): Float {
    val a = (ms / Waveforms.BUCKET_MS).toInt()
    val b = ((ms + spanMs.coerceAtLeast(1)) / Waveforms.BUCKET_MS).toInt().coerceAtLeast(a)
    var p = 0f
    for (i in a..b) if (i in peaks.indices && peaks[i] > p) p = peaks[i]
    return p
}

/**
 * Trim handles at both ends of a bar: a raised tab with a bracket
 * and grip lines, brighter when the item is selected or being trimmed.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.trimHandles(x0: Float, x1: Float, top: Float, h: Float, active: Boolean, accent: Color) {
    val wBar = x1 - x0
    if (wBar < 14f) return
    val gw = min(22.dp.toPx(), wBar / 3f)
    val tab = if (active) accent else Color.White.copy(alpha = 0.32f)
    val line = if (active) Color.Black.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.45f)
    for (left in listOf(true, false)) {
        val xa = if (left) x0 else x1 - gw
        if (xa + gw < 0f || xa > size.width) continue
        // Tab
        drawRoundRect(tab, Offset(xa, top), Size(gw, h), CornerRadius(4f))
        // Bracket on the outer edge
        val bx = if (left) xa + 1.5f else xa + gw - 1.5f
        val inward = if (left) 3.dp.toPx() else -3.dp.toPx()
        val bc = if (active) Color.White else Color.White.copy(alpha = 0.85f)
        drawLine(bc, Offset(bx, top + 2f), Offset(bx, top + h - 2f), 2.2f)
        drawLine(bc, Offset(bx, top + 2f), Offset(bx + inward, top + 2f), 2.2f)
        drawLine(bc, Offset(bx, top + h - 2f), Offset(bx + inward, top + h - 2f), 2.2f)
        // Grip lines
        val cx = xa + gw / 2f + (if (left) 1f else -1f)
        for (k in -1..1) drawLine(line, Offset(cx + k * 1.6f, top + h * 0.32f), Offset(cx + k * 1.6f, top + h * 0.68f), 1f)
    }
}

/** While trimming: the item's length above the bar. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.lengthTag(x0: Float, x1: Float, top: Float, lenMs: Long, paint: Paint, accent: Color) {
    val txt = if (lenMs < 60_000) "%.1f s".format(lenMs / 1000f) else "%d:%02d".format(lenMs / 60_000, (lenMs / 1000) % 60)
    paint.textSize = 10.sp.toPx()
    val tw = paint.measureText(txt) + 10f
    val cx = ((max(x0, 0f) + min(x1, size.width)) / 2f).coerceIn(tw / 2f, size.width - tw / 2f)
    val th = 15.dp.toPx()
    val ty = (top - th - 2f).coerceAtLeast(0f)
    drawRoundRect(accent, Offset(cx - tw / 2f, ty), Size(tw, th), CornerRadius(th / 2f))
    val c = paint.color
    paint.color = android.graphics.Color.BLACK
    drawContext.canvas.nativeCanvas.drawText(txt, cx - tw / 2f + 5f, ty + th * 0.72f, paint)
    paint.color = c
}

/** Diagonal stripes over a locked item. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.lockHatch(x0: Float, x1: Float, top: Float, h: Float) {
    val a = max(x0, 0f); val b = min(x1, size.width)
    if (b <= a) return
    clipRect(a, top, b, top + h) {
        var x = a - h
        while (x < b) {
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, top + h), Offset(x + h, top), 2f)
            x += 9f
        }
    }
}

/** A row's switches in the timeline's track header. */
@Composable
private fun TrackHeader(
    height: androidx.compose.ui.unit.Dp, label: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector, iconOff: Boolean, iconLabel: String, onIcon: () -> Unit,
    locked: Boolean, onLock: () -> Unit, vertical: Boolean = false,
) {
    val btn = @Composable { ic: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, on: () -> Unit ->
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(if (active) Pro.Amber.copy(alpha = 0.22f) else Color.Transparent).clickable(onClick = on), contentAlignment = Alignment.Center) {
            Icon(ic, contentDescription = desc, tint = if (active) Pro.Amber else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
        }
    }
    val lockIcon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen
    Box(Modifier.fillMaxWidth().height(height).background(Ed.Raised.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label != null) Text(label, style = Pro.Label.copy(fontSize = 8.sp))
            Row { btn(icon, iconLabel, iconOff, onIcon); btn(lockIcon, "Lock track", locked, onLock) }
        } else Row { btn(icon, iconLabel, iconOff, onIcon); btn(lockIcon, "Lock track", locked, onLock) }
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
