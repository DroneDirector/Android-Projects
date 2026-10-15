package com.dronedirector.acluxpro.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.dronedirector.acluxpro.camera.Filter
import com.dronedirector.acluxpro.camera.GradeIO
import com.dronedirector.acluxpro.ui.Pro
import com.dronedirector.acluxpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page(val label: String) { MEDIA("Media"), EDIT("Edit"), COLOR("Color"), AUDIO("Audio"), DELIVER("Deliver") }
private enum class ColorTab(val label: String) { INPUT("Input"), WHEELS("Wheels"), PRIMARIES("Primaries"), CURVES("Curves"), HSL("HSL"), DETAIL("Detail"), LOOKS("Looks") }

@Composable
fun VideoEditorScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<Clip>() }
    var loading by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        loading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    try { context.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                    ClipLoader.load(context, u)
                }
            }
            clips.addAll(loaded)
            loading = false
            if (clips.isNotEmpty()) started = true
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { addUris(it) }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }
    val pickGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    val pickFiles = { browse.launch(arrayOf("video/*")) }

    if (!started) {
        BackHandler { onExit() }
        Box(Modifier.fillMaxSize()) {
            MediaChooser(
                title = "Video editor",
                subtitle = "Add one or more clips to start a timeline. Log footage (D-Log, D-Log M, S-Log3, V-Log, LogC3, HLG) is converted in the Color page.",
                videos = true,
                onBack = onExit,
                onGallery = pickGallery,
                onFiles = pickFiles,
                onRecent = { addUris(listOf(it)) },
            )
            if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pro.Amber) }
        }
    } else {
        Workspace(clips, onAddGallery = pickGallery, onAddFiles = pickFiles, onExit = onExit)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Workspace(clips: MutableList<Clip>, onAddGallery: () -> Unit, onAddFiles: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.EDIT) }
    var colorTab by remember { mutableStateOf(ColorTab.INPUT) }
    var selected by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }      // position inside the current clip (source time)
    var current by remember { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var deliver by remember { mutableStateOf(DeliverSettings()) }
    var export by remember { mutableStateOf<TimelineExport?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
    var scopeHist by remember { mutableStateOf<Array<FloatArray>?>(null) }

    var effectsOk by remember { mutableStateOf(true) }
    var loadedId by remember { mutableStateOf<String?>(null) }
    var videoAspect by remember { mutableStateOf(16f / 9f) }
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }

    /**
     * The preview plays one clip at a time (the timeline advances clip by clip). Effects are set
     * before each prepare, which is the most reliable way across phones.
     */
    fun load(index: Int, startMs: Long, play: Boolean) {
        val c = clips.getOrNull(index) ?: return
        current = index
        loadedId = c.id
        player.stop()
        try {
            player.setVideoEffects(if (effectsOk) clipEffects(c) else emptyList())
        } catch (_: Throwable) {
            effectsOk = false
        }
        player.setMediaItem(
            MediaItem.Builder().setUri(c.uri).setMediaId(c.id)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build())
                .build(),
            startMs.coerceAtLeast(0),
        )
        player.playbackParameters = PlaybackParameters(c.speed)
        player.volume = if (c.muted) 0f else c.volume.coerceAtMost(1f)
        player.playWhenReady = play
        player.prepare()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (current < clips.lastIndex) load(current + 1, 0, true) else player.playWhenReady = false
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val pos = player.currentPosition
                if (effectsOk) {
                    // The phone's GPU preview pipeline failed: keep previewing without the live grade.
                    effectsOk = false
                    message = "Live grade preview isn't supported here — the grade is still applied on export"
                    load(current, pos, player.playWhenReady)
                } else {
                    message = "Can't play this clip on this phone (${error.errorCodeName})"
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); export?.cancel(); player.release() }
    }

    BackHandler { if (!exporting) confirmExit = true }

    // Reload when the clip under the playhead changes (added, removed, trimmed, reordered).
    val structure = clips.map { Triple(it.id, it.startMs, it.endMs) }
    LaunchedEffect(structure) {
        if (clips.isEmpty()) { player.stop(); player.clearMediaItems(); loadedId = null; return@LaunchedEffect }
        val idx = clips.indexOfFirst { it.id == loadedId }.takeIf { it >= 0 } ?: current.coerceIn(0, clips.lastIndex)
        load(idx, if (clips[idx].id == loadedId) player.currentPosition else 0L, player.playWhenReady && player.isPlaying)
    }
    // Live grade on the clip being shown.
    val cur = clips.getOrNull(current)
    LaunchedEffect(cur?.grade, cur?.detail, effectsOk) {
        delay(60)
        val c = cur ?: return@LaunchedEffect
        if (effectsOk && c.id == loadedId) {
            try { player.setVideoEffects(clipEffects(c)) } catch (_: Throwable) { effectsOk = false }
        }
    }
    LaunchedEffect(cur?.speed, cur?.volume, cur?.muted) {
        cur?.let {
            player.playbackParameters = PlaybackParameters(it.speed)
            player.volume = if (it.muted) 0f else it.volume.coerceAtMost(1f)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            posMs = player.currentPosition
            playing = player.isPlaying
            delay(100)
        }
    }
    LaunchedEffect(current) { if (page != Page.MEDIA) selected = current.coerceIn(0, (clips.size - 1).coerceAtLeast(0)) }
    // Timeline thumbnails.
    LaunchedEffect(clips.map { it.id to it.startMs }) {
        for (c in clips.toList()) {
            if (thumbs[c.id] == null) {
                val b = withContext(Dispatchers.IO) { GradeIO.videoFrame(context, c.uri, c.startMs * 1000 + 500_000, 240) }
                if (b != null) thumbs[c.id] = b
            }
        }
    }
    // Scope: graded still of the paused frame.
    LaunchedEffect(page, playing, current, posMs / 500, cur?.grade) {
        if (page != Page.COLOR || playing) return@LaunchedEffect
        val c = cur ?: return@LaunchedEffect
        delay(250)
        scopeHist = withContext(Dispatchers.Default) {
            val frame = GradeIO.videoFrame(context, c.uri, (c.startMs + posMs) * 1000, 360) ?: return@withContext null
            val out = ImageOps.render(frame, c.grade, Detail(), Geometry(), if (c.grade.isIdentity) null else c.grade.tables())
            ImageOps.histogram(out)
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(3000); message = null } }

    val totalMs = clips.sumOf { it.lengthMs }
    val beforeMs = clips.take(current.coerceAtMost(clips.size)).sumOf { it.lengthMs }
    val globalMs = beforeMs + ((posMs) / (cur?.speed ?: 1f)).toLong()

    fun seekGlobal(ms: Long) {
        var acc = 0L
        clips.forEachIndexed { i, c ->
            if (ms < acc + c.lengthMs || i == clips.lastIndex) {
                val off = ((ms - acc).coerceAtLeast(0) * c.speed).toLong()
                if (i == current && loadedId == c.id) player.seekTo(off) else load(i, off, player.isPlaying)
                return
            }
            acc += c.lengthMs
        }
    }

    fun update(i: Int, f: (Clip) -> Clip) { if (i in clips.indices) clips[i] = f(clips[i]) }

    fun startExport() {
        if (exporting || clips.isEmpty()) return
        player.pause()
        exporting = true
        progress = 0
        val e = TimelineExport(
            context, clips.toList(), deliver,
            onProgress = { progress = it },
            onDone = { uri, err ->
                exporting = false; export = null
                message = if (uri != null) "Exported to DCIM/AcluxPro" else err ?: "Export failed"
            },
        )
        export = e
        try { e.start() } catch (t: Throwable) { exporting = false; export = null; message = "Export failed: ${t.message}" }
    }

    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp

            val viewer = @Composable { mod: Modifier ->
                Box(mod.background(Color.Black), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            AspectRatioFrameLayout(ctx).apply {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                val tv = android.view.TextureView(ctx)
                                addView(tv, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
                                player.setVideoTextureView(tv)
                            }
                        },
                        update = { it.setAspectRatio(videoAspect) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (page == Page.COLOR) Scope(scopeHist, Modifier.align(Alignment.TopEnd).padding(8.dp).width(130.dp).height(64.dp))
                    Text(
                        "${tc(globalMs)} / ${tc(totalMs)}",
                        style = Pro.Value.copy(fontSize = 12.sp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (exporting) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = Pro.Amber)
                            Text("Rendering… $progress%", color = Color.White, fontSize = 15.sp)
                            Text("CANCEL", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { export?.cancel(); export = null; exporting = false }.padding(10.dp))
                        }
                    }
                    message?.let {
                        Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }

            val transport = @Composable {
                Column(Modifier.fillMaxWidth().background(Ed.Panel).padding(horizontal = 8.dp)) {
                    Slider(
                        value = if (totalMs > 0) globalMs.toFloat() / totalMs else 0f,
                        onValueChange = { seekGlobal((it * totalMs).toLong()) },
                        modifier = Modifier.height(26.dp),
                        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        TopIcon(Icons.Filled.SkipPrevious, "Previous clip") {
                            if (player.currentPosition > 1500 || current == 0) player.seekTo(0) else load(current - 1, 0, player.isPlaying)
                        }
                        TopIcon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Pro.Amber) {
                            if (player.isPlaying) player.pause() else {
                                when {
                                    player.playbackState == Player.STATE_ENDED && current == clips.lastIndex -> load(0, 0, true)
                                    player.playbackState == Player.STATE_IDLE -> load(current, posMs, true)
                                    else -> player.play()
                                }
                            }
                        }
                        TopIcon(Icons.Filled.SkipNext, "Next clip") { if (current < clips.lastIndex) load(current + 1, 0, player.isPlaying) }
                    }
                }
            }

            val timeline = @Composable {
                Row(
                    Modifier.fillMaxWidth().height(70.dp).background(Ed.Bg).hintedScroll(horizontal = true).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    clips.forEachIndexed { i, c ->
                        val w = (c.lengthMs / 80L).coerceIn(70L, 420L).toInt().dp
                        val sel = i == selected
                        Box(
                            Modifier.width(w).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                                .background(Ed.Raised)
                                .border(if (sel) 2.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, RoundedCornerShape(6.dp))
                                .clickable { selected = i; load(i, 0, false) },
                        ) {
                            thumbs[c.id]?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("${i + 1}  ${c.name}", color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tc(c.lengthMs) + (if (c.speed != 1f) "  ${c.speed}x" else "") + (if (!c.grade.isIdentity) "  ●" else ""), color = Pro.Amber, fontSize = 9.sp)
                            }
                            if (i == current) Box(Modifier.align(Alignment.TopStart).padding(3.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Pro.Red))
                        }
                    }
                    Box(
                        Modifier.width(56.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).border(1.dp, Pro.Line, RoundedCornerShape(6.dp)).clickable { onAddGallery() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add clip", tint = Pro.Amber) }
                }
            }

            val inspector = @Composable { mod: Modifier ->
                Column(mod.background(Ed.Panel)) {
                    Chips(Page.entries.map { it.label }, page.ordinal) { page = Page.entries[it] }
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        val sc = clips.getOrNull(selected)
                        when (page) {
                            Page.MEDIA -> MediaPage(clips, onAddGallery, onAddFiles)
                            Page.EDIT -> if (sc != null) EditPage(
                                sc, selected, clips.size, posMs,
                                onChange = { f -> update(selected, f) },
                                onSplit = {
                                    val c = clips[current]
                                    val cut = (c.startMs + posMs).coerceIn(c.startMs + 100, c.endMs - 100)
                                    if (cut > c.startMs + 100 && cut < c.endMs - 100) {
                                        clips[current] = c.copy(endMs = cut)
                                        clips.add(current + 1, c.copy(id = java.util.UUID.randomUUID().toString(), startMs = cut))
                                        message = "Split at ${tc(globalMs)}"
                                    }
                                },
                                onDuplicate = { clips.add(selected + 1, sc.copy(id = java.util.UUID.randomUUID().toString())) },
                                onDelete = {
                                    clips.removeAt(selected)
                                    selected = selected.coerceAtMost(clips.lastIndex).coerceAtLeast(0)

                                },
                                onMove = { dir ->
                                    val to = selected + dir
                                    if (to in clips.indices) { val c = clips.removeAt(selected); clips.add(to, c); selected = to }
                                },
                            )
                            Page.COLOR -> if (sc != null) ColorPage(sc, colorTab, { colorTab = it }, onChange = { f -> update(selected, f) }, onCopyToAll = {
                                val g = sc.grade; val d = sc.detail
                                for (i in clips.indices) clips[i] = clips[i].copy(grade = g, detail = d)
                                message = "Grade copied to all clips"
                            })
                            Page.AUDIO -> if (sc != null) AudioPage(sc, deliver) { f -> update(selected, f) }
                            Page.DELIVER -> DeliverPage(deliver, clips, exporting, { deliver = it }, ::startExport)
                        }
                        if (clips.isEmpty() && page != Page.MEDIA && page != Page.DELIVER) Text("Add a clip in the Media page.", color = Pro.Dim, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close", enabled = !exporting) { confirmExit = true }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Video editor", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${clips.size} CLIPS · ${tc(totalMs)}", style = Pro.Label)
                    }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                        modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !exporting) { page = Page.DELIVER }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            viewer(Modifier.fillMaxWidth().weight(1f))
                            transport()
                        }
                        inspector(Modifier.width(390.dp).fillMaxHeight())
                    }
                    timeline()
                } else {
                    viewer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    transport()
                    timeline()
                    inspector(Modifier.fillMaxWidth().weight(1f))
                }
            }

            if (confirmExit) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    containerColor = Pro.PanelSolid,
                    title = { Text("Leave the video editor?", color = Color.White) },
                    text = { Text("This timeline isn't saved. Export it first if you want to keep your edit.", color = Pro.Dim) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false; onExit() }) { Text("Leave", color = Pro.Red) } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { Text("Stay", color = Color.White) } },
                )
            }
        }
    }
}

@Composable
private fun MediaPage(clips: List<Clip>, onAddGallery: () -> Unit, onAddFiles: () -> Unit) {
    SectionTitle("MEDIA POOL")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(Icons.Filled.PhotoLibrary, "Gallery", Modifier.weight(1f), onAddGallery)
        BigButton(Icons.Filled.FolderOpen, "Files (MKV…)", Modifier.weight(1f), onAddFiles)
    }
    Text("Clips are added to the end of the timeline.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
    clips.forEachIndexed { i, c ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
            Text("${i + 1}", style = Pro.Value.copy(color = Pro.Amber), modifier = Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.width}×${c.height} · ${tc(c.durationMs)}", style = Pro.Label)
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPage(
    c: Clip, index: Int, count: Int, posMs: Long,
    onChange: ((Clip) -> Clip) -> Unit,
    onSplit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit,
) {
    SectionTitle("CLIP ${index + 1} OF $count · ${c.name}".uppercase())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.ContentCut, "Split", onSplit)
        SmallAction(Icons.Filled.ContentCopy, "Duplicate", onDuplicate)
        SmallAction(Icons.Filled.KeyboardArrowLeft, "Move left") { onMove(-1) }
        SmallAction(Icons.Filled.KeyboardArrowRight, "Move right") { onMove(1) }
        SmallAction(Icons.Filled.Delete, "Delete", onDelete)
    }
    SectionTitle("TRIM  ·  ${tc(c.startMs)} → ${tc(c.endMs)}")
    RangeSlider(
        value = c.startMs.toFloat()..c.endMs.toFloat(),
        onValueChange = { r ->
            val s = r.start.toLong(); val e = r.endInclusive.toLong()
            if (e - s >= 200) onChange { it.copy(startMs = s, endMs = e) }
        },
        valueRange = 0f..c.durationMs.toFloat(),
        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
    )
    Text("Split cuts the clip under the playhead (${tc(posMs)} into the clip).", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("SPEED")
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f)
    Chips(speeds.map { if (it == 1f) "1x" else "${it}x" }, speeds.indexOf(c.speed).coerceAtLeast(0)) { i -> onChange { it.copy(speed = speeds[i]) } }
}

@Composable
private fun ColorPage(
    c: Clip, tab: ColorTab, onTab: (ColorTab) -> Unit,
    onChange: ((Clip) -> Clip) -> Unit, onCopyToAll: () -> Unit,
) {
    val g = c.grade
    fun grade(f: (ProGrade) -> ProGrade) = onChange { it.copy(grade = f(it.grade)) }
    fun detail(f: (Detail) -> Detail) = onChange { it.copy(detail = f(it.detail)) }
    Chips(ColorTab.entries.map { it.label }, tab.ordinal) { onTab(ColorTab.entries[it]) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("COPY GRADE TO ALL CLIPS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onCopyToAll).padding(8.dp))
        Text("RESET CLIP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onChange { it.copy(grade = ProGrade(), detail = Detail()) } }.padding(8.dp))
    }
    when (tab) {
        ColorTab.INPUT -> {
            SectionTitle("INPUT COLOUR SPACE / GAMMA")
            Text("Pick how the clip was filmed. Log footage is converted to Rec.709 so it looks natural before grading.", color = Pro.Dim, fontSize = 11.sp)
            InputColor.entries.forEach { ic ->
                val sel = g.input == ic
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                        .border(1.dp, if (sel) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { grade { it.copy(input = ic) } }.padding(10.dp),
                ) {
                    Text(ic.label, color = if (sel) Pro.Amber else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                    Text(ic.detail, color = Pro.Dim, fontSize = 12.sp)
                }
            }
            Text("10-bit HDR (HLG / HDR10) clips are tone-mapped to SDR automatically on export.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        ColorTab.WHEELS -> {
            SectionTitle("PRIMARY WHEELS")
            WheelControl("Lift (shadows)", g.lift) { w -> grade { it.copy(lift = w) } }
            WheelControl("Gamma (midtones)", g.gamma) { w -> grade { it.copy(gamma = w) } }
            WheelControl("Gain (highlights)", g.gain) { w -> grade { it.copy(gain = w) } }
            SectionTitle("SPLIT TONES")
            WheelControl("Shadow tint", g.gradeShadows, showLuma = false) { w -> grade { it.copy(gradeShadows = w) } }
            WheelControl("Highlight tint", g.gradeHighlights, showLuma = false) { w -> grade { it.copy(gradeHighlights = w) } }
        }
        ColorTab.PRIMARIES -> {
            SectionTitle("PRIMARIES") { grade { it.copy(exposure = 0f, contrast = 0f, pivot = 0.435f, temperature = 0f, tint = 0f, saturation = 0f, vibrance = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f, dehaze = 0f) } }
            EdSlider("Exposure", g.exposure, -3f..3f, format = ::ev) { v -> grade { it.copy(exposure = v) } }
            EdSlider("Contrast", g.contrast) { v -> grade { it.copy(contrast = v) } }
            EdSlider("Pivot", g.pivot, 0.2f..0.7f, 0.435f, ::pct) { v -> grade { it.copy(pivot = v) } }
            EdSlider("Temperature", g.temperature) { v -> grade { it.copy(temperature = v) } }
            EdSlider("Tint", g.tint) { v -> grade { it.copy(tint = v) } }
            EdSlider("Saturation", g.saturation) { v -> grade { it.copy(saturation = v) } }
            EdSlider("Vibrance", g.vibrance) { v -> grade { it.copy(vibrance = v) } }
            EdSlider("Highlights", g.highlights) { v -> grade { it.copy(highlights = v) } }
            EdSlider("Shadows", g.shadows) { v -> grade { it.copy(shadows = v) } }
            EdSlider("Whites", g.whites) { v -> grade { it.copy(whites = v) } }
            EdSlider("Blacks", g.blacks) { v -> grade { it.copy(blacks = v) } }
            EdSlider("Dehaze", g.dehaze) { v -> grade { it.copy(dehaze = v) } }
        }
        ColorTab.CURVES -> {
            SectionTitle("CUSTOM CURVE (REGIONS)") { grade { it.copy(curveHighlights = 0f, curveLights = 0f, curveDarks = 0f, curveShadows = 0f) } }
            EdSlider("Highlights", g.curveHighlights) { v -> grade { it.copy(curveHighlights = v) } }
            EdSlider("Lights", g.curveLights) { v -> grade { it.copy(curveLights = v) } }
            EdSlider("Darks", g.curveDarks) { v -> grade { it.copy(curveDarks = v) } }
            EdSlider("Shadows", g.curveShadows) { v -> grade { it.copy(curveShadows = v) } }
            EdSlider("Fade (lift blacks)", g.fade, 0f..1f, format = ::pct) { v -> grade { it.copy(fade = v) } }
        }
        ColorTab.HSL -> HslMixer(g.hsl) { list -> grade { it.copy(hsl = list) } }
        ColorTab.DETAIL -> {
            SectionTitle("DETAIL") { detail { Detail() } }
            EdSlider("Sharpness", c.detail.sharpen, 0f..1f, format = ::pct) { v -> detail { it.copy(sharpen = v) } }
            EdSlider("Noise reduction", c.detail.denoise, 0f..1f, format = ::pct) { v -> detail { it.copy(denoise = v) } }
            SectionTitle("FINISHING")
            EdSlider("Vignette", c.detail.vignette) { v -> detail { it.copy(vignette = v) } }
            EdSlider("Film grain", c.detail.grain, 0f..1f, format = ::pct) { v -> detail { it.copy(grain = v) } }
        }
        ColorTab.LOOKS -> {
            SectionTitle("LOOKS / CREATIVE LUTS")
            LookStrip(g.look) { f -> grade { it.copy(look = f) } }
            if (g.look != Filter.NONE) EdSlider("Strength", g.lookAmount, 0f..1f, 1f, ::pct) { v -> grade { it.copy(lookAmount = v) } }
        }
    }
}

@Composable
private fun AudioPage(c: Clip, deliver: DeliverSettings, onChange: ((Clip) -> Clip) -> Unit) {
    SectionTitle("CLIP AUDIO")
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallAction(if (c.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (c.muted) "Muted" else "Sound on") { onChange { it.copy(muted = !it.muted) } }
    }
    EdSlider("Volume", c.volume, 0f..2f, 1f, { "${(it * 100).toInt()}%" }) { v -> onChange { it.copy(volume = v) } }
    Text("Above 100% boosts the clip on export; the preview plays it at most at full volume.", color = Pro.Dim, fontSize = 11.sp)
    if (!deliver.audio) Text("Audio is turned off in Deliver.", color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun DeliverPage(s: DeliverSettings, clips: List<Clip>, exporting: Boolean, onChange: (DeliverSettings) -> Unit, onExport: () -> Unit) {
    SectionTitle("RESOLUTION")
    val res = listOf(0 to "Source", 720 to "720p", 1080 to "1080p", 1440 to "1440p", 2160 to "4K UHD")
    Chips(res.map { it.second }, res.indexOfFirst { it.first == s.shortSide }.coerceAtLeast(0)) { onChange(s.copy(shortSide = res[it].first)) }
    SectionTitle("FRAME RATE")
    val fps = listOf(0, 24, 25, 30, 60)
    Chips(fps.map { if (it == 0) "Source" else "$it fps" }, fps.indexOf(s.fps).coerceAtLeast(0)) { onChange(s.copy(fps = fps[it])) }
    SectionTitle("CODEC")
    Chips(listOf("H.264", "HEVC (H.265)"), if (s.hevc) 1 else 0) { onChange(s.copy(hevc = it == 1)) }
    SectionTitle("BITRATE")
    val rates = listOf(0, 20, 50, 80, 120, 200)
    Chips(rates.map { if (it == 0) "Auto" else "$it Mbps" }, rates.indexOf(s.bitrateMbps).coerceAtLeast(0)) { onChange(s.copy(bitrateMbps = rates[it])) }
    SectionTitle("AUDIO")
    Chips(listOf("Include audio (AAC)", "No audio"), if (s.audio) 0 else 1) { onChange(s.copy(audio = it == 0)) }
    val total = clips.sumOf { it.lengthMs }
    Text(
        "Timeline ${tc(total)} · ${clips.size} clips. If the phone's encoder can't do the chosen size or bitrate, the closest supported setting is used.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp),
    )
    Text(
        if (exporting) "RENDERING…" else "RENDER & EXPORT",
        style = Pro.Value.copy(color = Color.Black, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (exporting || clips.isEmpty()) Pro.Amber.copy(alpha = 0.4f) else Pro.Amber)
            .clickable(enabled = !exporting && clips.isNotEmpty(), onClick = onExport).padding(14.dp),
    )
}

/** Timecode mm:ss.ff-style (hours when needed). */
fun tc(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val h = t / 3_600_000; val m = (t / 60_000) % 60; val s = (t / 1000) % 60; val cs = (t % 1000) / 10
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d.%02d".format(m, s, cs)
}
