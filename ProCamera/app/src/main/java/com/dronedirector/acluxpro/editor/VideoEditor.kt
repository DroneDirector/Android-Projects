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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page(val label: String) { MEDIA("Media"), EDIT("Edit"), TRANSITIONS("Transitions"), COLOR("Color"), LAYERS("Layers"), AUDIO("Audio"), DELIVER("Deliver") }
private enum class ColorTab(val label: String) { INPUT("Input"), LUTS("LUTs"), WHEELS("Wheels"), PRIMARIES("Primaries"), CURVES("Curves"), HSL("HSL"), DETAIL("Detail"), EFFECTS("Effects"), LOOKS("Looks") }

@Composable
fun VideoEditorScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<Clip>() }
    var project by remember { mutableStateOf<VideoProjectData?>(null) }
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
                onOpenProject = { id ->
                    loading = true
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { ProjectStore.loadVideo(context, id) }
                        loading = false
                        if (p == null || p.clips.isEmpty()) {
                            android.widget.Toast.makeText(context, "Couldn't open that project", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            clips.clear(); clips.addAll(p.clips); project = p; started = true
                        }
                    }
                },
            )
            if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pro.Amber) }
        }
    } else {
        androidx.compose.runtime.key(project?.id) {
            Workspace(
                clips, project, onAddGallery = pickGallery, onAddFiles = pickFiles, onExit = onExit,
                onLoad = { id ->
                    val p = ProjectStore.loadVideo(context, id)
                    if (p == null || p.clips.isEmpty()) android.widget.Toast.makeText(context, "Couldn't open that project", android.widget.Toast.LENGTH_SHORT).show()
                    else { clips.clear(); clips.addAll(p.clips); project = p }
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Workspace(clips: MutableList<Clip>, initial: VideoProjectData?, onAddGallery: () -> Unit, onAddFiles: () -> Unit, onExit: () -> Unit, onLoad: (String) -> Unit) {
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
    var deliver by remember { mutableStateOf(initial?.deliver ?: DeliverSettings()) }
    var projectId by remember { mutableStateOf(initial?.id) }
    var projectName by remember { mutableStateOf(initial?.name ?: "") }
    var askName by remember { mutableStateOf(false) }
    var leaveAfterSave by remember { mutableStateOf(false) }
    var showLoad by remember { mutableStateOf(false) }
    if (showLoad) LoadProjectDialog(true, onOpen = { showLoad = false; onLoad(it) }, onDismiss = { showLoad = false })
    var export by remember { mutableStateOf<TimelineExport?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var cloudExport by remember { mutableStateOf(false) }
    var cloudJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var previewMuted by remember { mutableStateOf(false) }
    // Timeline track switches for the main video track (clips).
    var videoMuted by remember { mutableStateOf(initial?.videoMuted ?: false) }
    var videoLocked by remember { mutableStateOf(initial?.videoLocked ?: false) }
    // Timeline markers and snapping.
    val markers = remember { mutableStateListOf<Long>().apply { initial?.markers?.let { addAll(it) } } }
    var snapping by remember { mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("tl_snap", true)) }
    val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
    val strips = remember { mutableStateMapOf<String, Filmstrip>() }
    var scopeHist by remember { mutableStateOf<Array<FloatArray>?>(null) }
    val layers = remember { mutableStateListOf<Layer>().apply { initial?.layers?.let { addAll(it) } } }
    var selectedLayer by remember { mutableStateOf<String?>(null) }
    // Remembered watermark: added to every new project; kept up to date while the option is on.
    LaunchedEffect(Unit) {
        if (initial == null && layers.none { it.kind == LayerKind.WATERMARK || (it.kind == LayerKind.LOGO && it.wholeVideo) }) {
            WatermarkMemory.load(context, true)?.let { layers.add(it) }
        }
    }
    val wmLayer = layers.firstOrNull { it.kind == LayerKind.WATERMARK } ?: layers.firstOrNull { it.kind == LayerKind.LOGO }
    val wmOn = WatermarkMemory.isOn(context, true)
    LaunchedEffect(wmLayer, wmOn) { if (wmOn && wmLayer != null) WatermarkMemory.save(context, true, wmLayer) }
    var music by remember { mutableStateOf(initial?.music) }
    // Extra audio tracks on the timeline, and audio files in the media pool.
    val extraAudio = remember { mutableStateListOf<MusicTrack>().apply { initial?.extraAudio?.let { addAll(it) } } }
    val audioPool = remember { mutableStateListOf<PoolAudio>().apply { initial?.audioPool?.let { addAll(it) } } }
    val audioPlayers = remember { HashMap<String, ExoPlayer>() }
    // Real waveforms for clips and audio tracks (decoded once in the background).
    val waves = remember { mutableStateMapOf<String, FloatArray>() }
    LaunchedEffect(clips.map { it.uri }.distinct(), music?.uri, extraAudio.map { it.uri }) {
        val uris = (clips.map { it.uri } + listOfNotNull(music?.uri) + extraAudio.map { it.uri }).distinct()
        for (u in uris) {
            if (waves[u.toString()] != null) continue
            withContext(Dispatchers.IO) { Waveforms.load(context, u) }?.let { waves[u.toString()] = it }
        }
    }
    DisposableEffect(Unit) { onDispose { audioPlayers.values.forEach { it.release() } } }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        for (uri in uris) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val mmr = android.media.MediaMetadataRetriever()
            val dur = try { mmr.setDataSource(context, uri); mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } catch (_: Throwable) { 0L } finally { try { mmr.release() } catch (_: Throwable) {} }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Audio"
            if (dur > 0 && audioPool.none { it.uri == uri }) audioPool.add(PoolAudio(uri, name, dur))
        }
    }
    val musicFxProc = remember { AudioFxProcessor(live = true) }
    val musicPlayer = remember { fxPlayer(context, musicFxProc) }
    DisposableEffect(Unit) { onDispose { musicPlayer.release() } }
    LaunchedEffect(Unit) { initial?.music?.let { musicPlayer.setMediaItem(MediaItem.fromUri(it.uri)); musicPlayer.prepare() } }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val mmr = android.media.MediaMetadataRetriever()
            val dur = try { mmr.setDataSource(context, uri); mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } catch (_: Throwable) { 0L } finally { try { mmr.release() } catch (_: Throwable) {} }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Music"
            if (dur > 0) {
                music = MusicTrack(uri, name, dur)
                if (audioPool.none { it.uri == uri }) audioPool.add(PoolAudio(uri, name, dur))
                musicPlayer.setMediaItem(MediaItem.fromUri(uri))
                musicPlayer.prepare()
            }
        }
    }
    val layerScope = rememberCoroutineScope()
    val pickLayerVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) layerScope.launch {
            val c = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }
            if (c == null) { message = "Can't open that video"; return@launch }
            val total = timelineLength(clips)
            val start = ((clipStarts(clips).getOrNull(current) ?: 0L) + (clips.getOrNull(current)?.let { it.tlOffsetOf(it.startMs + posMs) } ?: posMs)).coerceIn(0, total)
            val l = Layer(
                kind = LayerKind.VIDEO, text = c.name, videoUri = uri, videoDurationMs = c.durationMs,
                mediaAspect = if (c.height > 0) c.width.toFloat() / c.height else 16f / 9f,
                size = 0.35f, x = 0.72f, y = 0.28f, startMs = start, endMs = (start + c.durationMs).coerceAtMost(total.coerceAtLeast(start + 500)),
            )
            layers.add(l); selectedLayer = l.id
        }
    }
    val pickLayerImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val total = timelineLength(clips)
            val l = Layer(kind = LayerKind.IMAGE, text = "Image", imageUri = uri, size = 0.3f, x = 0.5f, y = 0.5f, startMs = 0, endMs = total.coerceAtLeast(1000), wholeVideo = true)
            layers.add(l); selectedLayer = l.id
        }
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val l = Layer(kind = LayerKind.LOGO, imageUri = uri, size = 0.12f, x = 0.85f, y = 0.88f, opacity = 0.85f, wholeVideo = true)
            layers.add(l); selectedLayer = l.id
        }
    }

    var effectsOk by remember { mutableStateOf(true) }
    // Preview proxies (optimized media), keyed by source file.
    var proxySettings by remember { mutableStateOf(ProxySettings.load(context)) }
    val proxies = remember { mutableStateMapOf<String, Uri>() }
    val proxyOff = remember { mutableStateListOf<String>() }          // files the user wants played as originals
    val proxyQueue = remember { mutableStateListOf<String>() }
    val proxyProgress = remember { mutableStateMapOf<String, Int>() }
    var proxyCancel by remember { mutableStateOf<(() -> Unit)?>(null) }
    val renderedFor = remember { mutableStateListOf<String?>(null, null) }
    var playSince by remember { mutableStateOf(0L) }
    var stallPos by remember { mutableStateOf(-1L) }
    var stallSince by remember { mutableStateOf(0L) }
    var stallTries by remember { mutableStateOf(0) }
    var showInfo by remember { mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("ve_info", false)) }
    var infoText by remember { mutableStateOf("") }
    fun sourceFor(c: Clip): Uri {
        val k = c.uri.toString()
        return if (k !in proxyOff) proxies[k] ?: c.uri else c.uri
    }
    fun requestProxy(c: Clip) {
        val k = c.uri.toString()
        proxyOff.remove(k)
        if (proxies[k] == null && k !in proxyQueue) proxyQueue.add(k)
    }
    if (showSettings) EditorSettingsDialog(
        "Video editor", com.dronedirector.acluxpro.ui.AppScreen.VIDEO_EDITOR,
        extra = {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show playback info", color = Color.White, fontSize = 15.sp)
                    Text("Small read-out on the player for troubleshooting", color = Pro.Dim, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(showInfo, {
                    showInfo = it
                    context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("ve_info", it).apply()
                }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
            }
            Text("PLAYBACK / PREVIEW COPIES", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
            Text("Light copies used only for smooth playback with grades and effects. Exports always use your original files.", color = Pro.Dim, fontSize = 12.sp)
            Chips(ProxyMode.entries.map { it.label }, proxySettings.mode.ordinal) { proxySettings = proxySettings.copy(mode = ProxyMode.entries[it]); ProxySettings.save(context, proxySettings) }
            Text("Preview codec", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Chips(listOf("H.264 (most compatible)", "HEVC (smaller)"), if (proxySettings.hevc) 1 else 0) { proxySettings = proxySettings.copy(hevc = it == 1); ProxySettings.save(context, proxySettings) }
            Text("Preview resolution", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Chips(listOf("720p (fastest)", "1080p (sharper)"), if (proxySettings.height >= 1080) 1 else 0) { proxySettings = proxySettings.copy(height = if (it == 1) 1080 else 720); ProxySettings.save(context, proxySettings) }
            var size by remember { mutableStateOf(ProxyMaker.sizeOnDisk(context)) }
            Text(
                "DELETE PREVIEW COPIES (${size / 1_000_000} MB)", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).clickable {
                    proxyCancel?.invoke(); proxyQueue.clear(); proxyProgress.clear(); proxies.clear()
                    ProxyMaker.clearAll(context); size = 0
                }.padding(6.dp),
            )
        },
    ) { showSettings = false }
    var loadedId by remember { mutableStateOf<String?>(null) }
    var videoAspect by remember { mutableStateOf(16f / 9f) }
    // Two players take turns: while one plays, the next clip is loaded and paused on its first
    // frame in the other, so the cut to the next clip is seamless.
    // Each preview player runs the clip's EQ / dynamics live.
    val audioFx = remember { listOf(AudioFxProcessor(meter = true, live = true), AudioFxProcessor(meter = true, live = true)) }
    val players = remember {
        audioFx.map { fx -> fxPlayer(context, fx).apply { repeatMode = Player.REPEAT_MODE_OFF } }
    }
    val zoom = remember { TimelineZoom() }
    // Crop / zoom on the player, stabilisation and transitions in the preview.
    var cropMode by remember { mutableStateOf(false) }
    var cropEditEnd by remember { mutableStateOf(false) }
    val motions = remember { mutableStateMapOf<String, Motion>() }
    val stabProgress = remember { mutableStateMapOf<String, Int>() }
    val stabCancels = remember { HashMap<String, () -> Unit>() }
    val frameViews = remember { arrayOfNulls<android.view.View>(2) }
    val texViews = remember { arrayOfNulls<android.view.View>(2) }
    val transitionOn = remember { booleanArrayOf(false) }
    val gpuGrade = remember { GradePreview.supported }
    var errorRetries by remember { mutableStateOf(0) }
    fun motionFor(c: Clip): Motion? = if (c.stab.enabled) motions[Stabilizer.keyOf(c)] else null
    fun analyze(c: Clip) {
        val k = Stabilizer.keyOf(c)
        if (stabProgress[k] != null || motions[k] != null) return
        stabProgress[k] = 0
        stabCancels[k] = Stabilizer.analyze(context, c, onProgress = { stabProgress[k] = it }) { m, err ->
            stabProgress.remove(k); stabCancels.remove(k)
            if (m != null) { motions[k] = m; message = "Stabilisation ready for ${c.name}" } else message = err
        }
    }
    val deck = remember { Deck(players) }
    val aspects = remember { mutableStateListOf(16f / 9f, 16f / 9f) }
    var preloadedId by remember { mutableStateOf<String?>(null) }

    fun mediaItemFor(c: Clip) = MediaItem.Builder().setUri(sourceFor(c)).setMediaId(c.id)
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build())
        .build()

    fun prepareOn(p: ExoPlayer, c: Clip, startMs: Long, play: Boolean) {
        p.stop()
        val pi = players.indexOf(p).coerceAtLeast(0)
        if (gpuGrade) {
            // Android 13+: the grade is drawn on the view; the player only decodes plain video.
            texViews[pi]?.let { if (effectsOk) GradePreview.apply(it, c) else GradePreview.clear(it) }
        } else {
            try { p.setVideoEffects(if (effectsOk) clipEffects(c) else emptyList()) } catch (_: Throwable) { effectsOk = false }
        }
        p.setMediaItem(mediaItemFor(c), startMs.coerceAtLeast(0))
        p.playbackParameters = PlaybackParameters(c.speedAtSrc(c.startMs + startMs))
        p.volume = if (c.muted || previewMuted || videoMuted) 0f else c.volume.coerceAtMost(1f)
        audioFx[players.indexOf(p).coerceAtLeast(0)].fx = c.audio
        p.playWhenReady = play
        p.prepare()
    }

    /** Loads the clip after [index] into the spare player, paused on its first frame. */
    fun preloadAfter(index: Int) {
        val n = clips.getOrNull(index + 1)
        if (n == null) { deck.spare.stop(); preloadedId = null; return }
        prepareOn(deck.spare, n, 0, false)
        preloadedId = n.id
    }

    fun load(index: Int, startMs: Long, play: Boolean) {
        val c = clips.getOrNull(index) ?: return
        current = index
        loadedId = c.id
        prepareOn(deck.cur, c, startMs, play)
        videoAspect = aspects[deck.active]
        preloadAfter(index)
    }

    /** End of a clip: switch to the preloaded next clip instantly. */
    fun advance() {
        val next = current + 1
        val n = clips.getOrNull(next)
        if (n == null) { deck.cur.playWhenReady = false; return }
        if (preloadedId == n.id && deck.spare.playbackState != Player.STATE_IDLE) {
            val old = deck.cur
            deck.active = 1 - deck.active
            current = next
            loadedId = n.id
            videoAspect = aspects[deck.active]
            deck.cur.playbackParameters = PlaybackParameters(n.speedAtSrc(n.startMs))
            deck.cur.volume = if (n.muted || previewMuted || videoMuted) 0f else n.volume.coerceAtMost(1f)
            deck.cur.play()
            old.pause()
            // Get the following clip ready in the player we just left.
            preloadAfter(next)
        } else {
            load(next, 0, true)
        }
    }

    DisposableEffect(Unit) {
        val listeners = players.mapIndexed { idx, p ->
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (idx == deck.active && state == Player.STATE_ENDED) advance()
                }

                override fun onRenderedFirstFrame() {
                    renderedFor[idx] = p.currentMediaItem?.mediaId
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        aspects[idx] = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        if (idx == deck.active) videoAspect = aspects[idx]
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (idx != deck.active) { preloadedId = null; return }
                    val pos = p.currentPosition
                    if (gpuGrade) {
                        // No effects pipeline in the player: just retry a couple of times.
                        if (errorRetries < 2) { errorRetries++; load(current, pos, p.playWhenReady) }
                        else message = "Can't play this clip on this phone (${error.errorCodeName})"
                        return
                    }
                    if (effectsOk) {
                        // The phone's GPU preview pipeline failed: keep previewing without the live grade.
                        effectsOk = false
                        message = "Live grade preview isn't supported here — the grade is still applied on export"
                        load(current, pos, p.playWhenReady)
                    } else {
                        message = "Can't play this clip on this phone (${error.errorCodeName})"
                    }
                }
            }.also { p.addListener(it) }
        }
        onDispose {
            players.forEachIndexed { i, p -> p.removeListener(listeners[i]); p.release() }
            export?.cancel()
            proxyCancel?.invoke()
            stabCancels.values.forEach { it() }
        }
    }

    BackHandler { if (!exporting) confirmExit = true }

    // Reload when the clip under the playhead changes (added, removed, trimmed, reordered).
    val structure = clips.map { listOf(it.id, it.startMs, it.endMs, sourceFor(it).toString()) }
    LaunchedEffect(structure) {
        if (clips.isEmpty()) { players.forEach { it.stop(); it.clearMediaItems() }; loadedId = null; preloadedId = null; return@LaunchedEffect }
        val idx = clips.indexOfFirst { it.id == loadedId }.takeIf { it >= 0 } ?: current.coerceIn(0, clips.lastIndex)
        load(idx, if (clips[idx].id == loadedId) deck.cur.currentPosition else 0L, deck.cur.playWhenReady && deck.cur.isPlaying)
    }
    // Decide which files need a preview proxy; reuse ones already made.
    LaunchedEffect(clips.map { it.uri.toString() }.distinct(), proxySettings) {
        for (c in clips.toList()) {
            val k = c.uri.toString()
            if (proxies[k] != null) continue
            val existing = ProxyMaker.existing(context, c, proxySettings)
            if (existing != null) { proxies[k] = existing; continue }
            if (proxySettings.needsProxy(c) && k !in proxyOff && k !in proxyQueue) proxyQueue.add(k)
        }
    }
    // Make proxies one at a time.
    val nextProxy = proxyQueue.firstOrNull()
    LaunchedEffect(nextProxy) {
        val k = nextProxy ?: return@LaunchedEffect
        val c = clips.firstOrNull { it.uri.toString() == k }
        if (c == null) { proxyQueue.remove(k); return@LaunchedEffect }
        proxyProgress[k] = 0
        proxyCancel = ProxyMaker.make(
            context, c, proxySettings,
            onProgress = { proxyProgress[k] = it },
            onDone = { uri, err ->
                proxyProgress.remove(k)
                proxyQueue.remove(k)
                proxyCancel = null
                if (uri != null) { proxies[k] = uri; message = "Preview copy ready — smooth playback for ${c.name}" }
                else message = err ?: "Couldn't make a preview copy"
            },
        )
    }

    // Live grade on the clip being shown.
    val cur = clips.getOrNull(current)
    // Live grade while dragging: the newest value is applied every ~80 ms (not only after letting go),
    // the colour table is built off the main thread, and a paused frame is redrawn.
    val gradeReq = remember { kotlinx.coroutines.channels.Channel<Clip>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    LaunchedEffect(cur?.grade, cur?.detail, effectsOk) { cur?.let { gradeReq.trySend(it) } }
    LaunchedEffect(Unit) {
        for (c in gradeReq) {
            if (gpuGrade) { if (effectsOk && c.id == loadedId) texViews[deck.active]?.let { GradePreview.apply(it, c) }; continue }
            if (!effectsOk || c.id != loadedId) continue
            val fx = withContext(Dispatchers.Default) { try { clipEffects(c) } catch (_: Throwable) { null } } ?: continue
            if (c.id != loadedId) continue
            try {
                deck.cur.setVideoEffects(fx)
                // Redraw a paused frame (never while loading or playing).
                if (!deck.cur.playWhenReady && deck.cur.playbackState == Player.STATE_READY) deck.cur.seekTo(deck.cur.currentPosition)
            } catch (_: Throwable) { effectsOk = false }
            delay(80)
        }
    }
    val nextClip = clips.getOrNull(current + 1)
    LaunchedEffect(nextClip?.id, nextClip?.grade, nextClip?.detail, nextClip?.startMs, nextClip?.endMs) {
        delay(150)
        if (clips.isNotEmpty()) preloadAfter(current)
    }
    LaunchedEffect(cur?.audio) { cur?.let { audioFx[deck.active].fx = it.audio } }
    LaunchedEffect(cur?.speed, cur?.volume, cur?.muted, previewMuted, videoMuted) {
        cur?.let {
            deck.cur.playbackParameters = PlaybackParameters(it.speedAtSrc(it.startMs + deck.cur.currentPosition))
            deck.cur.volume = if (it.muted || previewMuted || videoMuted) 0f else it.volume.coerceAtMost(1f)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            posMs = deck.cur.currentPosition
            val c0 = clips.getOrNull(current)
            if (showInfo) {
                fun st(p: ExoPlayer) = when (p.playbackState) { Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "LOAD"; Player.STATE_READY -> "READY"; else -> "END" } +
                    (if (p.playWhenReady) "▶" else "‖") + " " + (p.currentPosition / 100) / 10f + "s" + (p.playerError?.let { " ERR ${it.errorCodeName}" } ?: "")
                infoText = "clip ${current + 1}/${clips.size} · player ${deck.active + 1}\n" +
                    "A: ${st(players[0])}  B: ${st(players[1])}\n" +
                    "src ${if (c0 != null && proxies[c0.uri.toString()] != null && c0.uri.toString() !in proxyOff) "proxy" else "original"} · fx ${if (effectsOk) "on" else "off"} · frame ${if (renderedFor[deck.active] == c0?.id) "ok" else "none"} · stalls $stallTries"
            }
            val nowPlaying = deck.cur.isPlaying
            if (nowPlaying && !playing) playSince = System.currentTimeMillis()
            playing = nowPlaying
            // Frozen (playing but the position stopped moving): reload the clip where it stopped.
            if (deck.cur.playWhenReady && deck.cur.playbackState != Player.STATE_ENDED && c0 != null && c0.id == loadedId) {
                val p = deck.cur.currentPosition
                if (p != stallPos) { stallPos = p; stallSince = System.currentTimeMillis() }
                else if (System.currentTimeMillis() - stallSince > 2500 && stallTries < 3) {
                    stallSince = System.currentTimeMillis() + 2000
                    stallTries++
                    // Escalate: reload → reload without live effects → use a preview copy.
                    when (stallTries) {
                        1 -> load(current, p, true)
                        2 -> { if (effectsOk && !gpuGrade) { effectsOk = false; message = "Live effects paused in the preview to keep playback smooth (the export keeps them)" }; load(current, p, true) }
                        else -> {
                            if (proxySettings.mode != ProxyMode.OFF && proxies[c0.uri.toString()] == null) {
                                requestProxy(c0); message = "This clip is heavy for live playback — making a smooth preview copy…"
                            } else message = "Playback stalled — tap play to retry"
                            deck.cur.pause()
                        }
                    }
                }
            } else stallSince = System.currentTimeMillis()
            if (deck.cur.isPlaying && deck.cur.currentPosition != stallPos) { stallTries = 0; errorRetries = 0 }
            // Safety net: playing but no picture for a while → make a preview copy, or drop the live effects.
            val c = clips.getOrNull(current)
            if (c != null && nowPlaying && renderedFor[deck.active] != c.id && System.currentTimeMillis() - playSince > 2500) {
                playSince = System.currentTimeMillis() + 60_000
                if (proxySettings.mode != ProxyMode.OFF && proxies[c.uri.toString()] == null) {
                    requestProxy(c)
                    message = "This clip is too heavy to preview live — making a smooth preview copy…"
                } else if (effectsOk) {
                    effectsOk = false
                    message = "Live effects turned off for the preview; the export still uses them"
                    load(current, deck.cur.currentPosition, true)
                }
            }
            delay(100)
        }
    }
    // Load saved stabilisation results.
    LaunchedEffect(clips.filter { it.stab.enabled }.map { Stabilizer.keyOf(it) }.distinct()) {
        for (c in clips.toList()) {
            if (!c.stab.enabled) continue
            val k = Stabilizer.keyOf(c)
            if (motions[k] != null) continue
            val m = withContext(Dispatchers.IO) { Stabilizer.cached(context, c) }
            if (m != null) motions[k] = m else analyze(c)
        }
    }
    // Every frame: crop/zoom/stabilise the picture and play transitions between clips.
    LaunchedEffect(Unit) {
        var was = false
        var startedFor: String? = null
        fun vol(c: Clip) = if (c.muted || previewMuted || videoMuted) 0f else c.volume.coerceAtMost(1f)
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val i = current
            val c = clips.getOrNull(i) ?: continue
            val a = deck.active
            if (c.id == loadedId) texViews[a]?.let {
                ClipXform.applyToView(it, ClipXform.at(c, c.startMs + deck.cur.currentPosition, motionFor(c)))
                if (gpuGrade) { if (effectsOk) GradePreview.apply(it, c, System.nanoTime() / 1e9f) else GradePreview.clear(it) }
            }
            val n = clips.getOrNull(i + 1)
            val ov = overlapMs(clips, i)
            val local = c.tlOffsetOf(c.startMs + deck.cur.currentPosition)
            // Speed ramps: follow the curve while playing.
            if (c.ramp != null && c.id == loadedId) {
                val sp = c.speedAtSrc(c.startMs + deck.cur.currentPosition)
                if (kotlin.math.abs(deck.cur.playbackParameters.speed - sp) > 0.04f) deck.cur.playbackParameters = PlaybackParameters(sp)
            }
            // Volume keyframes.
            if (!c.volKeys.isNullOrEmpty() && c.id == loadedId && !transitionOn[0]) {
                deck.cur.volume = (vol(c) * volAt(c.volKeys, deck.cur.currentPosition)).coerceIn(0f, 1f)
            }
            val startT = c.lengthMs - ov
            if (ov > 0 && n != null && preloadedId == n.id && local >= startT && deck.cur.playbackState == Player.STATE_READY) {
                val p = ((local - startT).toFloat() / ov).coerceIn(0f, 1f)
                // Start the incoming clip once (seeking again while it loads would stall it).
                if (deck.cur.playWhenReady && !deck.spare.playWhenReady) {
                    if (startedFor != n.id) {
                        startedFor = n.id
                        val want = n.srcAtOffset(local - startT) - n.startMs
                        if (kotlin.math.abs(deck.spare.currentPosition - want) > 250) deck.spare.seekTo(want)
                    }
                    deck.spare.play()
                }
                if (!deck.cur.playWhenReady && deck.spare.playWhenReady) deck.spare.pause()
                transitionOn[0] = true
                texViews[1 - a]?.let {
                    ClipXform.applyToView(it, ClipXform.at(n, n.startMs + deck.spare.currentPosition, motionFor(n)))
                    if (gpuGrade && effectsOk) GradePreview.apply(it, n, System.nanoTime() / 1e9f)
                }
                frameViews[a]?.let { TransitionPreview.apply(it, c.transition.type, p, false) }
                frameViews[1 - a]?.let { TransitionPreview.apply(it, c.transition.type, p, true) }
                deck.cur.volume = vol(c) * (1f - p)
                deck.spare.volume = vol(n) * p
                was = true
            } else if (was) {
                was = false
                transitionOn[0] = false
                frameViews[a]?.let { TransitionPreview.reset(it, true) }
                frameViews[1 - a]?.let { TransitionPreview.reset(it, false) }
                deck.cur.volume = vol(c)
                if (deck.spare.playWhenReady) { deck.spare.pause(); deck.spare.seekTo(0) }
                startedFor = null
            }
        }
    }
    // Music follows the timeline while previewing.
    LaunchedEffect(playing, music, previewMuted) {
        val m = music
        if (m == null) { musicPlayer.pause(); return@LaunchedEffect }
        musicFxProc.fx = m.fx
        musicPlayer.volume = if (previewMuted || m.muted) 0f else m.volume.coerceIn(0f, 1f)
        if (!playing) { musicPlayer.pause(); return@LaunchedEffect }
        while (true) {
            val ducked = clips.getOrNull(current)?.let { it.audio.duckMusic && !it.muted && it.volume > 0f } == true
            musicPlayer.volume = if (previewMuted || m.muted) 0f else (m.volume * (if (ducked) 1f - m.duck else 1f)).coerceIn(0f, 1f)
            val before = clipStarts(clips).getOrNull(current) ?: 0L
            val g = before + (clips.getOrNull(current)?.let { it.tlOffsetOf(it.startMs + deck.cur.currentPosition) } ?: deck.cur.currentPosition)
            val len = m.playLength(timelineLength(clips))
            val want = m.offsetMs + (g - m.startMs)
            if (g < m.startMs || g >= m.startMs + len) { musicPlayer.pause() } else {
                if (kotlin.math.abs(musicPlayer.currentPosition - want) > 350) musicPlayer.seekTo(want)
                if (!musicPlayer.isPlaying) musicPlayer.play()
            }
            delay(500)
        }
    }
    // Extra audio tracks follow the timeline too.
    LaunchedEffect(playing, extraAudio.toList(), previewMuted) {
        // Players for new tracks; drop players of removed tracks.
        val ids = extraAudio.map { it.id }.toSet()
        audioPlayers.keys.filter { it !in ids }.forEach { audioPlayers.remove(it)?.release() }
        for (t in extraAudio) audioPlayers.getOrPut(t.id) {
            ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(t.uri)); prepare() }
        }
        if (!playing) { audioPlayers.values.forEach { it.pause() }; return@LaunchedEffect }
        while (true) {
            val before = clipStarts(clips).getOrNull(current) ?: 0L
            val g = before + (clips.getOrNull(current)?.let { it.tlOffsetOf(it.startMs + deck.cur.currentPosition) } ?: deck.cur.currentPosition)
            val total = timelineLength(clips)
            for (t in extraAudio) {
                val p = audioPlayers[t.id] ?: continue
                p.volume = if (previewMuted || t.muted) 0f else t.volume.coerceIn(0f, 1f)
                val len = t.playLength(total)
                if (g < t.startMs || g >= t.startMs + len) { if (p.isPlaying) p.pause(); continue }
                val want = t.offsetMs + (g - t.startMs)
                if (kotlin.math.abs(p.currentPosition - want) > 350) p.seekTo(want)
                if (!p.isPlaying) p.play()
            }
            delay(400)
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
    // Filmstrips for the timeline (one per source file).
    LaunchedEffect(clips.map { it.uri }.distinct()) {
        for (c in clips.toList()) {
            val key = c.uri.toString()
            if (strips[key] != null) continue
            val n = (c.durationMs / 1000).toInt().coerceIn(4, 40)
            val frames = withContext(Dispatchers.IO) {
                val out = mutableListOf<Bitmap>()
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    mmr.setDataSource(context, c.uri)
                    val h = 96
                    val w = if (c.height > 0) (h * c.width / c.height).coerceIn(48, 200) else 160
                    for (i in 0 until n) {
                        val t = (c.durationMs * (i + 0.5) / n * 1000).toLong()
                        val f = if (android.os.Build.VERSION.SDK_INT >= 27)
                            mmr.getScaledFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h)
                        else mmr.getFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, w, h, true) }
                        if (f != null) out += f
                    }
                    mmr.release()
                } catch (_: Throwable) {}
                out
            }
            if (frames.isNotEmpty()) strips[key] = Filmstrip(frames, c.durationMs)
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

    val totalMs = timelineLength(clips)
    val beforeMs = clipStarts(clips).getOrNull(current) ?: 0L
    val globalMs = beforeMs + (cur?.tlOffsetOf(cur.startMs + posMs) ?: posMs)

    fun seekGlobal(ms: Long) {
        if (clips.isEmpty()) return
        val (i, local) = clipAt(clips, ms)
        val c = clips[i]
        val off = c.srcAtOffset(local) - c.startMs
        if (i == current && loadedId == c.id) deck.cur.seekTo(off) else load(i, off, deck.cur.isPlaying)
    }

    fun update(i: Int, f: (Clip) -> Clip) { if (i in clips.indices) clips[i] = f(clips[i]) }

    /** Saves the whole edit (clips, grades, layers, music, delivery settings) as a project. */
    fun saveProject(name: String) {
        val p = VideoProjectData(
            id = projectId ?: java.util.UUID.randomUUID().toString(), name = name,
            clips = clips.toList(), layers = layers.toList(), music = music, deliver = deliver,
            extraAudio = extraAudio.toList(), audioPool = audioPool.toList(),
            videoMuted = videoMuted, videoLocked = videoLocked, markers = markers.toList(),
        )
        val saved = try { ProjectStore.saveVideo(context, p, clips.firstOrNull()?.let { thumbs[it.id] }) } catch (_: Throwable) { null }
        if (saved != null) { projectId = saved.id; projectName = saved.name; message = "Project \"${saved.name}\" saved" }
        else message = "Couldn't save the project"
    }
    if (askName) ProjectNameDialog(
        ProjectStore.defaultName(true),
        onSave = { askName = false; saveProject(it); if (leaveAfterSave) onExit() },
        onDismiss = { askName = false; leaveAfterSave = false },
    )

    /** Blade: cut the clip under the playhead at the exact frame shown. */
    fun splitAtPlayhead() {
        if (videoLocked) { message = "The video track is locked — tap its lock on the timeline to unlock"; return }
        val c = clips.getOrNull(current) ?: return
        val cut = c.startMs + deck.cur.currentPosition
        if (cut <= c.startMs + 100 || cut >= c.endMs - 100) {
            message = "Move the playhead inside a clip to split"
            return
        }
        clips[current] = c.copy(endMs = cut)
        clips.add(current + 1, c.copy(id = java.util.UUID.randomUUID().toString(), startMs = cut))
        selected = current
        message = "Split at ${tc(globalMs)}"
    }

    // ---- Voiceover: record the microphone while the edit plays.
    var voiceRec by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<java.io.File?>(null) }
    var voiceStart by remember { mutableStateOf(0L) }
    var voiceMs by remember { mutableStateOf(0L) }
    var mutedBeforeVoice by remember { mutableStateOf(false) }
    LaunchedEffect(voiceRec) {
        val t0 = System.currentTimeMillis()
        while (voiceRec != null) { voiceMs = System.currentTimeMillis() - t0; delay(200) }
    }
    fun beginVoice() {
        val dir = java.io.File(context.filesDir, "voice").apply { mkdirs() }
        val f = java.io.File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val r = try {
            @Suppress("DEPRECATION")
            (if (android.os.Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(context) else android.media.MediaRecorder()).apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(160_000)
                setAudioSamplingRate(48_000)
                setOutputFile(f.path)
                prepare()
                start()
            }
        } catch (t: Throwable) { message = "Couldn't start the microphone"; return }
        voiceFile = f; voiceRec = r; voiceStart = globalMs
        mutedBeforeVoice = previewMuted
        previewMuted = true
        if (!deck.cur.isPlaying) deck.cur.play()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) beginVoice() else message = "Allow the microphone to record a voiceover"
    }
    fun startVoice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) beginVoice()
        else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    fun stopVoice() {
        val r = voiceRec ?: return
        voiceRec = null
        deck.cur.pause()
        previewMuted = mutedBeforeVoice
        try { r.stop() } catch (_: Throwable) {}
        r.release()
        val f = voiceFile ?: return
        val dur = try {
            android.media.MediaMetadataRetriever().let { m -> m.setDataSource(f.path); val d = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L; m.release(); d }
        } catch (_: Throwable) { 0L }
        if (dur < 300) { f.delete(); message = "Voiceover too short"; return }
        val n = extraAudio.count { it.name.startsWith("Voiceover") } + 1
        extraAudio.add(MusicTrack(uri = android.net.Uri.fromFile(f), name = "Voiceover $n", durationMs = dur, volume = 1f, fadeInMs = 0, fadeOutMs = 150, duck = 0f, startMs = voiceStart))
        message = "Voiceover $n added at ${tc(voiceStart)}"
    }
    DisposableEffect(Unit) { onDispose { voiceRec?.let { try { it.stop() } catch (_: Throwable) {}; it.release() } } }

    /** Beat markers from a music / audio track. */
    fun addBeatMarkers(t: MusicTrack) {
        message = "Finding the beat…"
        layerScope.launch {
            val pk = waves[t.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, t.uri) }
            if (pk == null) { message = "Couldn't read that audio"; return@launch }
            val beats = withContext(Dispatchers.Default) { Beats.detect(pk) }
            val total = timelineLength(clips)
            val len = t.playLength(total)
            var added = 0
            for (b in beats) {
                if (b < t.offsetMs || b > t.offsetMs + len) continue
                val tl = t.startMs + (b - t.offsetMs)
                if (tl > total || markers.any { kotlin.math.abs(it - tl) < 80 }) continue
                markers.add(tl); added++
            }
            markers.sort()
            message = if (added > 0) "$added beat markers added" else "No clear beat found"
        }
    }

    // ---- AI captions
    fun makeCaptions(lang: String?, clipsOnly: Boolean, style: Int) {
        com.dronedirector.acluxpro.camera.CloudAi.load(context)
        if (!com.dronedirector.acluxpro.camera.CloudAi.hasKey) { message = "AI captions use your fal.ai account — add the key in settings → Cloud AI"; return }
        if (clips.isEmpty()) return
        deck.cur.pause()
        val file = java.io.File(context.cacheDir, "captions_${System.currentTimeMillis()}.m4a")
        message = "Preparing the sound…"
        val total = timelineLength(clips)
        val e = TimelineExport(
            context, clips.toList(), deliver.copy(audioOnly = true, rangeStart = null, rangeEnd = null), emptyList(),
            if (clipsOnly) null else music, if (clipsOnly) emptyList() else extraAudio.toList(),
            onProgress = {},
            onDone = { u, err ->
                if (u == null) message = err ?: "Couldn't prepare the sound"
                else layerScope.launch {
                    val segs = try { withContext(Dispatchers.IO) { com.dronedirector.acluxpro.camera.CloudAi.transcribe(context, file, lang) } }
                    catch (_: com.dronedirector.acluxpro.camera.CloudAi.Stopped) { message = "Captions stopped"; null }
                    catch (t: Throwable) { message = t.message ?: "Captions failed"; null }
                    file.delete()
                    if (segs != null) {
                        var n = 0
                        for ((a, b, text) in segs) {
                            // Long sentences become several captions of at most two short lines.
                            val pieces = captionPieces(text, 38)
                            val span = (b - a).coerceAtLeast(400) / pieces.size
                            pieces.forEachIndexed { k, p ->
                                val s0 = (a + k * span).coerceAtMost(total); val s1 = (a + (k + 1) * span).coerceAtMost(total)
                                if (s1 - s0 < 150) return@forEachIndexed
                                layers.add(Layer(
                                    kind = LayerKind.TEXT, text = p, size = if (style == 2) 0.062f else 0.046f, x = 0.5f, y = 0.84f,
                                    box = style == 0, shadow = true, font = if (style == 2) LayerFont.CONDENSED else LayerFont.SANS,
                                    startMs = s0, endMs = s1, name = "Caption",
                                ))
                                n++
                            }
                        }
                        message = if (n > 0) "$n captions added — tap one on the timeline to edit it" else "No speech found"
                    }
                }
            },
            toFile = file,
        )
        try { e.start() } catch (t: Throwable) { message = "Couldn't prepare the sound" }
    }

    // ---- Auto-reframe: follow the subject when changing the shape of the video.
    fun reframeTrack(c: Clip, aspect: Float): List<Float>? {
        val videoAspect = c.width * (c.desqueeze ?: 1f) / c.height.coerceAtLeast(1)
        val cw = (aspect / videoAspect).coerceAtMost(1f)
        if (cw > 0.97f) return null
        val maxShift = (1f - cw) / 2f
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, c.uri)
            val n = ((c.endMs - c.startMs) / 500).toInt().coerceIn(2, 120)
            var prev: IntArray? = null
            val xs = FloatArray(n)
            var last = 0.5f
            for (i in 0 until n) {
                val t = c.startMs + (c.endMs - c.startMs) * i / (n - 1)
                val f = (if (android.os.Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(t * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, (320 / videoAspect).toInt().coerceAtLeast(16))
                    else mmr.getFrameAtTime(t * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)) ?: continue
                val fb = if (f.config == Bitmap.Config.ARGB_8888) f else f.copy(Bitmap.Config.ARGB_8888, false)
                var cx = AiSegment.subjectCenter(context, fb)
                // No AI subject: follow where things move.
                val px = IntArray(fb.width * fb.height).also { fb.getPixels(it, 0, fb.width, 0, 0, fb.width, fb.height) }
                if (cx == null) prev?.takeIf { it.size == px.size }?.let { p ->
                    var sw = 0f; var sx = 0f
                    for (k in px.indices) {
                        val d = kotlin.math.abs((px[k] shr 8 and 0xFF) - (p[k] shr 8 and 0xFF)).toFloat()
                        if (d > 18) { sw += d; sx += d * (k % fb.width) }
                    }
                    if (sw > px.size * 0.5f) cx = sx / sw / fb.width
                }
                prev = px
                last = cx ?: last
                xs[i] = last
            }
            // Smooth, so the camera glides instead of jumping.
            val sm = FloatArray(n) { i -> var s = 0f; var k = 0; for (j in (i - 3).coerceAtLeast(0)..(i + 3).coerceAtMost(n - 1)) { s += xs[j]; k++ }; s / k }
            return sm.map { (0.5f - it).coerceIn(-maxShift, maxShift) }
        } catch (_: Throwable) { return null } finally { mmr.release() }
    }

    fun autoReframe(aspect: Float?) {
        if (videoLocked) { message = "The video track is locked"; return }
        if (aspect == null) {
            for (i in clips.indices) clips[i] = clips[i].copy(reframe = clips[i].reframe.copy(track = null))
            message = "Auto-reframe cleared"; return
        }
        deliver = deliver.copy(aspect = aspect, fill = true)
        deck.cur.pause()
        layerScope.launch {
            val list = clips.toList()
            for ((i, c) in list.withIndex()) {
                message = "Auto-reframe: clip ${i + 1} of ${list.size}…"
                val tr = withContext(Dispatchers.IO) { reframeTrack(c, aspect) } ?: continue
                val idx = clips.indexOfFirst { it.id == c.id }
                if (idx >= 0) clips[idx] = clips[idx].copy(reframe = clips[idx].reframe.copy(track = tr))
            }
            message = "Auto-reframe done — the picture follows the subject" + if (!AiSegment.hasModel(context)) " (motion)" else ""
        }
    }

    // ---- AI slow motion (frame interpolation in the cloud).
    fun aiSlowMo(i: Int) {
        if (videoLocked) { message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        com.dronedirector.acluxpro.camera.CloudAi.load(context)
        if (!com.dronedirector.acluxpro.camera.CloudAi.hasKey) { message = "AI slow-mo uses your fal.ai account — add the key in settings → Cloud AI"; return }
        if (c.endMs - c.startMs > 20_000) { message = "Trim the clip to 20 s or less for AI slow-mo"; return }
        deck.cur.pause()
        layerScope.launch {
            try {
                val trimmed = CloudVideoPrep.trim(context, c)
                val dir = java.io.File(context.filesDir, "edits").apply { mkdirs() }
                val out = java.io.File(dir, "slowmo_${System.currentTimeMillis()}.mp4")
                try { withContext(Dispatchers.IO) { com.dronedirector.acluxpro.camera.CloudAi.interpolate(context, trimmed, out, 3) } } finally { trimmed.delete() }
                val dur = withContext(Dispatchers.IO) {
                    android.media.MediaMetadataRetriever().let { m -> m.setDataSource(out.path); val d = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L; m.release(); d }
                }
                val idx = clips.indexOfFirst { it.id == c.id }
                if (idx < 0 || dur <= 0) { message = "AI slow-mo failed"; return@launch }
                clips[idx] = c.copy(
                    id = java.util.UUID.randomUUID().toString(), uri = android.net.Uri.fromFile(out), name = "Slow-mo · ${c.name}",
                    durationMs = dur, startMs = 0, endMs = dur, speed = 0.25f, ramp = null, rampName = null, volKeys = null, muted = true,
                    stab = Stabilize(), mime = "video/avc", hdr = false, tenBit = false,
                )
                load(idx.coerceAtMost(clips.lastIndex), 0, false)
                message = "AI slow motion: 4× more frames, playing at ¼ speed"
            } catch (_: com.dronedirector.acluxpro.camera.CloudAi.Stopped) { message = "AI slow-mo stopped" }
            catch (t: Throwable) { message = t.message ?: "AI slow-mo failed" }
        }
    }

    /** Freeze frame: 2 s of the frame under the playhead, inserted there (the clip is split around it). */
    fun freezeAtPlayhead() {
        if (videoLocked) { message = "The video track is locked"; return }
        val c = clips.getOrNull(current) ?: return
        val src = c.startMs + deck.cur.currentPosition
        deck.cur.pause()
        message = "Making a freeze frame…"
        layerScope.launch {
            val fc = withContext(Dispatchers.IO) { try { VideoTools.freeze(context, c, src, 2000) } catch (_: Throwable) { null } }
            if (fc == null) { message = "Couldn't make a freeze frame"; return@launch }
            val idx = clips.indexOfFirst { it.id == c.id }
            if (idx < 0) return@launch
            if (src > c.startMs + 100 && src < c.endMs - 100) {
                clips[idx] = c.copy(endMs = src, transition = Transition())
                clips.add(idx + 1, fc.copy(transition = Transition()))
                clips.add(idx + 2, c.copy(id = java.util.UUID.randomUUID().toString(), startMs = src))
            } else clips.add(idx + 1, fc)
            selected = idx + 1
            message = "2 s freeze frame added — trim it on the Edit page"
        }
    }

    /** Reverse: replaces the clip with a backwards copy (picture only). */
    fun reverseClip(i: Int) {
        if (videoLocked) { message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        deck.cur.pause()
        message = "Reversing… 0%"
        layerScope.launch {
            val rc = withContext(Dispatchers.IO) {
                try { VideoTools.reverse(context, c) { p -> layerScope.launch { message = "Reversing… $p%" } } } catch (_: Throwable) { null }
            }
            val idx = clips.indexOfFirst { it.id == c.id }
            if (rc == null || idx < 0) { message = "Couldn't reverse this clip"; return@launch }
            clips[idx] = rc.copy(transition = c.transition)
            load(idx.coerceAtMost(clips.lastIndex), 0, false)
            message = if (c.endMs - c.startMs > 60_000) "Reversed (first 60 s) — reversed clips are silent" else "Reversed — reversed clips are silent"
        }
    }

    /** Detach audio: the clip's sound becomes its own audio track (move / trim it for J- and L-cuts). */
    fun detachAudio(i: Int) {
        if (videoLocked) { message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        if (c.ramp != null || c.speed != 1f) { message = "Set the clip to 1x without a ramp to detach its audio"; return }
        if (c.muted) { message = "This clip has no sound to detach"; return }
        val st = clipStarts(clips).getOrNull(i) ?: 0L
        extraAudio.add(MusicTrack(
            uri = c.uri, name = "Audio · ${c.name}", durationMs = c.durationMs, offsetMs = c.startMs, volume = c.volume.coerceAtMost(1.5f),
            fadeInMs = c.audio.fadeInMs, fadeOutMs = c.audio.fadeOutMs, fx = c.audio, duck = 0f, startMs = st, lengthMs = c.endMs - c.startMs,
        ))
        clips[i] = c.copy(muted = true)
        message = "Audio detached — hold its bar on the timeline to move or extend it"
    }

    // ---- Undo / redo: every settled change to clips, layers and audio is a step.
    val history = remember { mutableStateListOf<EditSnap>() }
    var histPos by remember { mutableStateOf(-1) }
    val snapNow by remember { derivedStateOf { EditSnap(clips.toList(), layers.toList(), music, extraAudio.toList()) } }
    LaunchedEffect(snapNow) {
        delay(450)   // a slider drag or a held timeline move becomes one step
        if (histPos in history.indices && history[histPos] == snapNow) return@LaunchedEffect
        while (history.lastIndex > histPos) history.removeAt(history.lastIndex)
        history.add(snapNow)
        if (history.size > 80) history.removeAt(0)
        histPos = history.lastIndex
    }
    fun applySnap(s: EditSnap) {
        val wasPlaying = deck.cur.isPlaying
        val pos = deck.cur.currentPosition
        clips.clear(); clips.addAll(s.clips)
        layers.clear(); layers.addAll(s.layers)
        music = s.music
        extraAudio.clear(); extraAudio.addAll(s.extra)
        if (selectedLayer != null && layers.none { it.id == selectedLayer }) selectedLayer = null
        if (clips.isNotEmpty()) {
            val i = current.coerceIn(0, clips.lastIndex)
            selected = selected.coerceIn(0, clips.lastIndex)
            val c = clips[i]
            load(i, pos.coerceIn(0, (c.endMs - c.startMs - 50).coerceAtLeast(0)), wasPlaying)
        }
    }
    fun undo() {
        if (histPos <= 0) { message = "Nothing to undo"; return }
        histPos--; applySnap(history[histPos]); message = "Undo"
    }
    fun redo() {
        if (histPos >= history.lastIndex) { message = "Nothing to redo"; return }
        histPos++; applySnap(history[histPos]); message = "Redo"
    }

    var lastScrub by remember { mutableStateOf(0L) }
    /** Timeline scrubbing: fast keyframe seeks while dragging, exact frame when released. */
    fun scrub(ms: Long, final: Boolean) {
        val now = System.currentTimeMillis()
        if (!final && now - lastScrub < 60) return
        lastScrub = now
        if (deck.cur.isPlaying && !final) deck.cur.pause()
        deck.cur.setSeekParameters(if (final) androidx.media3.exoplayer.SeekParameters.EXACT else androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        seekGlobal(ms)
    }

    fun runExport(list0: List<Clip>) {
        // Export range: cut the edit down to the chosen part first.
        val ra = deliver.rangeStart; val rb = deliver.rangeEnd
        val cut = if (ra != null && rb != null && rb > ra) cutRange(list0, layers.toList(), music, extraAudio.toList(), ra, rb)
        else RangeCut(list0, layers.toList(), music, extraAudio.toList())
        if (cut.clips.isEmpty()) { exporting = false; message = "Nothing to export in that range"; return }
        val list = cut.clips
        val e = TimelineExport(
            context, if (videoMuted) list.map { it.copy(muted = true) } else list, deliver, cut.layers, cut.music, cut.extra,
            onProgress = { progress = it },
            onDone = { uri, err ->
                exporting = false; export = null
                message = if (uri != null) {
                    val folder = SaveFolder.get(context, true)?.let { SaveFolder.label(it) } ?: if (deliver.audioOnly) "Music/AcluxPro" else "DCIM/AcluxPro"
                    (if (deliver.audioOnly) "Audio exported to " else "Exported to ") + folder
                } else err ?: "Export failed"
            },
        )
        export = e
        try { e.start() } catch (t: Throwable) { exporting = false; export = null; message = "Export failed: ${t.message}" }
    }

    /** Saves the frame under the playhead (graded, with its text / logo layers) as a photo. */
    fun saveStill() {
        val c = clips.getOrNull(current) ?: return
        val src = c.startMs + deck.cur.currentPosition
        val at = globalMs
        val shownLayers = layers.filter { it.activeAt(at) && it.kind != LayerKind.VIDEO }
        message = "Saving frame…"
        layerScope.launch {
            val uri = withContext(Dispatchers.IO) {
                try {
                    val f = GradeIO.videoFrame(context, c.uri, src * 1000, 4096) ?: return@withContext null
                    var out = ImageOps.render(f, c.grade, c.detail.copy(aiDenoise = 0f), Geometry(), if (c.grade.isIdentity) null else c.grade.tables())
                    for (l in shownLayers) out = stampLayer(context, out, l)
                    savePicture(context, out, png = false, quality = 95)
                } catch (_: Throwable) { null }
            }
            message = if (uri != null) "Frame saved as a photo" else "Couldn't save the frame"
        }
    }

    fun startExport() {
        if (exporting || clips.isEmpty()) return
        deck.cur.pause()
        exporting = true
        progress = 0
        cloudExport = !deliver.audioOnly && com.dronedirector.acluxpro.camera.CloudAi.useVideo(context) && clips.any { it.detail.aiDenoise > 0f }
        if (!cloudExport) { runExport(clips.toList()); return }
        // Cloud AI first (Topaz Nyx on fal.ai), then the normal export with the cleaned clips.
        cloudJob = layerScope.launch {
            val list = try {
                CloudVideoPrep.prepare(context, clips.toList())
            } catch (_: com.dronedirector.acluxpro.camera.CloudAi.Stopped) {
                exporting = false; message = "Export stopped"; return@launch
            } catch (_: kotlinx.coroutines.CancellationException) {
                exporting = false; return@launch
            } catch (t: Throwable) {
                message = "Cloud AI unavailable (${t.message}) — using the phone's AI"
                cloudExport = false
                clips.toList()
            } finally {
                com.dronedirector.acluxpro.camera.CloudAi.show(null)
            }
            runExport(list)
        }
    }

    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        com.dronedirector.acluxpro.ui.AiBadgeHost(
            exporting && !cloudExport && clips.any { it.detail.aiDenoise > 0f }, "AI NOISE REDUCTION · EXPORT", 1, progress,
            onStop = { export?.cancel(); export = null; exporting = false; AiDenoiser.stop(); message = "Export stopped" },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        )
        com.dronedirector.acluxpro.ui.AiBadgeHost(
            com.dronedirector.acluxpro.camera.CloudAi.stage != null, com.dronedirector.acluxpro.camera.CloudAi.stage ?: "", 1,
            com.dronedirector.acluxpro.camera.CloudAi.progress,
            onStop = { com.dronedirector.acluxpro.camera.CloudAi.stop(); cloudJob?.cancel(); export?.cancel(); export = null; exporting = false; message = "Export stopped" },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(21f),
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp
            val boxW = maxWidth.value; val boxH = maxHeight.value

            val viewer = @Composable { mod: Modifier ->
                Box(mod.background(Color.Black), contentAlignment = Alignment.Center) {
                    val (ow, oh) = outputSize(clips, deliver)
                    val projAspect = ow.toFloat() / oh
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val boxAspect = maxWidth.value / maxHeight.value
                        val fw = if (boxAspect > projAspect) maxHeight * projAspect else maxWidth
                        val fh = if (boxAspect > projAspect) maxHeight else maxWidth / projAspect
                        Box(Modifier.width(fw).height(fh).clipToBounds().background(Color.Black)) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.FrameLayout(ctx).apply {
                                        players.forEachIndexed { pi, p ->
                                            val frame = AspectRatioFrameLayout(ctx)
                                            val tv = android.view.TextureView(ctx)
                                            frameViews[pi] = frame; texViews[pi] = tv
                                            frame.addView(tv, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
                                            addView(frame, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
                                            p.setVideoTextureView(tv)
                                        }
                                    }
                                },
                                update = { root ->
                                    for (i in 0..1) {
                                        val frame = root.getChildAt(i) as AspectRatioFrameLayout
                                        frame.resizeMode = if (deliver.fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        val shownClip = if (i == deck.active) clips.getOrNull(current) else clips.getOrNull(current + 1)
                                        frame.setAspectRatio(aspects[i] * (shownClip?.desqueeze ?: 1f))
                                        if (!transitionOn[0]) frame.alpha = if (i == deck.active) 1f else 0f
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            LayerPreview(
                                layers = layers,
                                timeMs = globalMs,
                                selectedId = if (page == Page.LAYERS) selectedLayer else null,
                                showAll = page == Page.LAYERS && !playing,
                                playing = playing,
                                onMove = { id, dx, dy ->
                                    val i = layers.indexOfFirst { it.id == id }
                                    if (i >= 0 && !layers[i].locked) {
                                        val o = layers[i]
                                        val cur = o.animAt(globalMs)
                                        layers[i] = o.editedAt(globalMs, cur.copy(x = (cur.x + dx).coerceIn(0f, 1f), y = (cur.y + dy).coerceIn(0f, 1f), keys = o.keys))
                                    }
                                },
                                onSelect = { selectedLayer = it; page = Page.LAYERS },
                            )
                            if (cropMode) CropOverlay(
                                editingEnd = cropEditEnd && clips.getOrNull(current)?.reframe?.animate == true,
                                onGesture = { pan, zf, rot, w, h ->
                                    update(current) { cl ->
                                        val r = cl.reframe
                                        val end = r.animate && cropEditEnd
                                        val f = if (end) r.end else r.start
                                        val nf = f.copy(
                                            zoom = (f.zoom * zf).coerceIn(0.25f, 8f),
                                            x = (f.x + pan.x / w).coerceIn(-2f, 2f), y = (f.y + pan.y / h).coerceIn(-2f, 2f),
                                            rotation = f.rotation + rot,
                                        )
                                        cl.copy(reframe = if (end) r.copy(end = nf) else r.copy(start = nf))
                                    }
                                },
                                onDone = { cropMode = false },
                            )
                        }
                    }
                    if (!fullscreen) Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f)).clickable { fullscreen = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen", tint = Color.White) }
                    clips.getOrNull(current)?.let { cc ->
                        val k = cc.uri.toString()
                        val badge = when {
                            proxyProgress[k] != null -> "MAKING PREVIEW ${proxyProgress[k]}%"
                            proxies[k] != null && k !in proxyOff -> "PROXY"
                            else -> null
                        }
                        if (badge != null) Text(
                            badge, style = Pro.Label.copy(color = Color.Black, fontSize = 9.sp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Pro.Amber).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    clips.getOrNull(current)?.let { cc ->
                        val pp = proxyProgress[cc.uri.toString()]
                        if (pp != null && proxies[cc.uri.toString()] == null) Column(
                            Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(progress = { pp / 100f }, color = Pro.Amber, modifier = Modifier.size(34.dp))
                            Text("Making smooth preview copy · $pp%", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                            Text("Playback switches to it automatically", color = Pro.Dim, fontSize = 11.sp)
                        }
                    }
                    if (showInfo) Text(
                        infoText, color = Color.White, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f)).padding(4.dp),
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

            fun togglePlay() {
                // Stuck loading? Play pressed again means "try again", not "do nothing".
                if (deck.cur.playWhenReady && !deck.cur.isPlaying && deck.cur.playbackState == Player.STATE_BUFFERING) {
                    stallTries = 0
                    load(current, deck.cur.currentPosition, true)
                    return
                }
                if (deck.cur.playWhenReady && deck.cur.playbackState != Player.STATE_ENDED) deck.cur.pause() else {
                    when {
                        deck.cur.playbackState == Player.STATE_ENDED && current == clips.lastIndex -> load(0, 0, true)
                        deck.cur.playbackState == Player.STATE_IDLE -> load(current, posMs, true)
                        else -> deck.cur.play()
                    }
                }
            }
            fun prevClip() { if (deck.cur.currentPosition > 1500 || current == 0) deck.cur.seekTo(0) else load(current - 1, 0, deck.cur.isPlaying) }
            fun nextClip() { if (current < clips.lastIndex) load(current + 1, 0, deck.cur.isPlaying) }

            val transport = @Composable {
                Column(Modifier.fillMaxWidth().background(Ed.Panel).padding(horizontal = 8.dp)) {
                    Slider(
                        value = if (totalMs > 0) globalMs.toFloat() / totalMs else 0f,
                        onValueChange = { seekGlobal((it * totalMs).toLong()) },
                        modifier = Modifier.height(26.dp),
                        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Split, undo and redo are always on screen; the zoom tools scroll if the row is narrow.
                        TransportIcon(Icons.Filled.ContentCut, "Split at playhead", accent = true) { splitAtPlayhead() }
                        TransportIcon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (histPos > 0) Color.White else Pro.Dim) { undo() }
                        TransportIcon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (histPos < history.lastIndex) Color.White else Pro.Dim) { redo() }
                      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TransportIcon(if (previewMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute preview") { previewMuted = !previewMuted }
                        TransportIcon(Icons.Filled.ZoomOut, "Zoom timeline out") { zoom.zoomOut() }
                        TransportIcon(Icons.Filled.FitScreen, "Show whole timeline") { zoom.fit() }
                        TransportIcon(Icons.Filled.ZoomIn, "Zoom timeline in") { zoom.zoomIn() }
                      }
                        TransportIcon(Icons.Filled.SkipPrevious, "Previous clip") { prevClip() }
                        TransportIcon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Pro.Amber) { togglePlay() }
                        TransportIcon(Icons.Filled.SkipNext, "Next clip") { nextClip() }
                        TransportIcon(Icons.Filled.Fullscreen, "Full screen") { fullscreen = true }
                    }
                }
            }

            val timeline = @Composable {
                TimelineView(
                    clips = clips, layers = layers, music = music, strips = strips,
                    globalMs = globalMs, selectedClip = selected,
                    selectedLayerId = if (page == Page.LAYERS) selectedLayer else null,
                    onScrub = { ms, final -> scrub(ms, final) },
                    onSelectClip = { selected = it; if (page == Page.MEDIA || page == Page.LAYERS || page == Page.DELIVER) page = Page.EDIT },
                    onSelectLayer = { selectedLayer = it; page = Page.LAYERS },
                    zoom = zoom,
                    onSelectTransition = { selected = it; page = Page.TRANSITIONS },
                    extraAudio = extraAudio,
                    videoMuted = videoMuted, videoLocked = videoLocked,
                    onVideoMute = { videoMuted = !videoMuted; message = if (videoMuted) "Video track sound muted" else "Video track sound on" },
                    onVideoLock = { videoLocked = !videoLocked; message = if (videoLocked) "Video track locked" else "Video track unlocked" },
                    onLayerFlags = { l -> val i = layers.indexOfFirst { it.id == l.id }; if (i >= 0) layers[i] = l },
                    onAudioFlags = { t ->
                        val m = music
                        if (m != null && m.id == t.id) music = t
                        else { val i = extraAudio.indexOfFirst { it.id == t.id }; if (i >= 0) extraAudio[i] = t }
                    },
                    onLocked = { message = "Locked — tap the lock at the left of the timeline to unlock" },
                    waves = waves,
                    markers = markers, snapping = snapping,
                    onToggleMarker = {
                        val near = markers.firstOrNull { kotlin.math.abs(it - globalMs) < 250 }
                        if (near != null) { markers.remove(near); message = "Marker removed" }
                        else { markers.add(globalMs); markers.sort(); message = "Marker at ${tc(globalMs)} — tap ◆ MARKER on it again to remove" }
                    },
                    onToggleSnap = {
                        snapping = !snapping
                        context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("tl_snap", snapping).apply()
                        message = if (snapping) "Snapping on" else "Snapping off"
                    },
                    onLayerTime = { id, a, b ->
                        val i = layers.indexOfFirst { it.id == id }
                        if (i >= 0) layers[i] = layers[i].copy(wholeVideo = false, startMs = a, endMs = b)
                    },
                    onLayerOrder = { id, to ->
                        val i = layers.indexOfFirst { it.id == id }
                        if (i >= 0 && to in layers.indices && to != i) { val l = layers.removeAt(i); layers.add(to, l) }
                    },
                    onAudioTime = { id, st, off, len ->
                        val m = music
                        if (m != null && m.id == id) music = m.copy(startMs = st, offsetMs = off, lengthMs = len)
                        else { val i = extraAudio.indexOfFirst { it.id == id }; if (i >= 0) extraAudio[i] = extraAudio[i].copy(startMs = st, offsetMs = off, lengthMs = len) }
                    },
                )
            }

            val inspector = @Composable { mod: Modifier ->
                Column(mod.background(Ed.Panel)) {
                    Chips(Page.entries.map { it.label }, page.ordinal) { page = Page.entries[it] }
                    if (clips.isNotEmpty() && (page == Page.EDIT || page == Page.COLOR || page == Page.AUDIO)) CopyPasteBar(
                        video = true,
                        onCopy = {
                            clips.getOrNull(selected)?.let { c ->
                                EditClipboard.copy(context, CopiedSettings(
                                    fromVideo = true, grade = c.grade, detail = c.detail, reframe = c.reframe, stab = c.stab,
                                    audio = c.audio, volume = c.volume, muted = c.muted, speed = c.speed,
                                ))
                                message = "Clip ${selected + 1} settings copied — select another clip and tap PASTE SETTINGS"
                            }
                        },
                        onPaste = { parts, all ->
                            if (all) {
                                for (i in clips.indices) clips[i] = EditClipboard.pasteOnClip(clips[i], parts)
                                message = "Settings pasted on all ${clips.size} clips"
                            } else {
                                update(selected) { EditClipboard.pasteOnClip(it, parts) }
                                message = "Settings pasted on clip ${selected + 1}"
                            }
                        },
                    )
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        val sc = clips.getOrNull(selected)
                        when (page) {
                            Page.MEDIA -> MediaPage(
                                clips, onAddGallery, onAddFiles,
                                audioPool = audioPool,
                                audioUses = { a -> (if (music?.uri == a.uri) 1 else 0) + extraAudio.count { it.uri == a.uri } },
                                onAddAudio = { pickAudio.launch(arrayOf("audio/*")) },
                                onPlaceAudio = { a ->
                                    val total = timelineLength(clips)
                                    val head = ((clipStarts(clips).getOrNull(current) ?: 0L) + (clips.getOrNull(current)?.let { it.tlOffsetOf(it.startMs + posMs) } ?: posMs)).coerceIn(0, total)
                                    if (music == null) {
                                        music = MusicTrack(a.uri, a.name, a.durationMs, startMs = head)
                                        musicPlayer.setMediaItem(MediaItem.fromUri(a.uri)); musicPlayer.prepare()
                                        message = "${a.name} added as the music track"
                                    } else {
                                        extraAudio.add(MusicTrack(a.uri, a.name, a.durationMs, volume = 1f, fadeInMs = 0, fadeOutMs = 300, startMs = head))
                                        message = "${a.name} added to the timeline at ${tc(head)}"
                                    }
                                },
                                onRemoveAudio = { a ->
                                    audioPool.remove(a)
                                    extraAudio.removeAll { it.uri == a.uri }
                                    if (music?.uri == a.uri) { music = null; musicPlayer.stop() }
                                },
                                proxyState = { c ->
                                    val k = c.uri.toString()
                                    when {
                                        proxyProgress[k] != null -> "MAKING PREVIEW ${proxyProgress[k]}%"
                                        k in proxyQueue -> "PREVIEW QUEUED"
                                        proxies[k] != null && k !in proxyOff -> "PREVIEW COPY ✓"
                                        proxies[k] != null -> "PLAYING ORIGINAL"
                                        else -> null
                                    }
                                },
                                onProxyToggle = { c ->
                                    val k = c.uri.toString()
                                    when {
                                        proxies[k] == null -> requestProxy(c)
                                        k in proxyOff -> proxyOff.remove(k)
                                        else -> proxyOff.add(k)
                                    }
                                },
                                onRemove = { c ->
                                    val k = c.uri.toString()
                                    if (proxyQueue.firstOrNull() == k) { proxyCancel?.invoke(); proxyCancel = null; proxyProgress.remove(k) }
                                    proxyQueue.remove(k); proxyOff.remove(k)
                                    clips.removeAll { it.uri.toString() == k }
                                    selected = selected.coerceAtMost(clips.lastIndex).coerceAtLeast(0)
                                    current = current.coerceAtMost(clips.lastIndex).coerceAtLeast(0)
                                    message = "${c.name} removed from the project"
                                },
                            )
                            Page.TRANSITIONS -> TransitionsPanel(
                                clips, selected, thumbs,
                                onChange = { t -> update(selected, { it.copy(transition = t) }) },
                                onApplyAll = { t -> for (i in 0 until clips.lastIndex) clips[i] = clips[i].copy(transition = t); message = "${t.type.label} on every cut" },
                                onPreview = {
                                    val st = clipStarts(clips)
                                    if (selected + 1 < clips.size) { seekGlobal((st[selected + 1] - 1500).coerceAtLeast(st[selected])); if (!deck.cur.isPlaying) deck.cur.play() }
                                },
                            )
                            Page.EDIT -> if (sc != null) Column {
                                EditPage(
                                sc, selected, clips.size, posMs, deliver, { deliver = it },
                                onChange = { f -> if (videoLocked) message = "The video track is locked" else update(selected, f) },
                                onSplit = { splitAtPlayhead() },
                                onFreeze = { freezeAtPlayhead() },
                                onReverse = { reverseClip(selected) },
                                onDetach = { detachAudio(selected) },
                                onSlowMo = { aiSlowMo(selected) },
                                onReframe = { a -> autoReframe(a) },
                                onDuplicate = { if (videoLocked) message = "The video track is locked" else clips.add(selected + 1, sc.copy(id = java.util.UUID.randomUUID().toString())) },
                                onDelete = {
                                    if (videoLocked) { message = "The video track is locked"; return@EditPage }
                                    clips.removeAt(selected)
                                    selected = selected.coerceAtMost(clips.lastIndex).coerceAtLeast(0)

                                },
                                onMove = { dir ->
                                    if (videoLocked) { message = "The video track is locked"; return@EditPage }
                                    val to = selected + dir
                                    if (to in clips.indices) { val c = clips.removeAt(selected); clips.add(to, c); selected = to }
                                },
                                )
                                val sk = Stabilizer.keyOf(sc)
                                ReframePanel(
                                    sc, cropEditEnd, cropMode, stabProgress[sk], motions[sk] != null,
                                    onEditEnd = { e ->
                                        cropEditEnd = e
                                        // Show the frame being edited.
                                        if (selected == current) deck.cur.seekTo(if (e) (sc.endMs - sc.startMs - 50).coerceAtLeast(0) else 0)
                                    },
                                    onCropMode = { on ->
                                        cropMode = on
                                        if (on && selected != current) load(selected, 0, false)
                                        if (on) deck.cur.pause()
                                    },
                                    onChange = { f -> update(selected, f) },
                                    onAnalyze = { analyze(sc) },
                                )
                                SectionTitle("ANAMORPHIC DESQUEEZE")
                                val sq = listOf(1f, 1.33f, 1.5f, 1.55f, 1.8f, 2f)
                                Chips(sq.map { if (it == 1f) "Off" else "${it}×" }, sq.indexOfFirst { it == (sc.desqueeze ?: 1f) }.coerceAtLeast(0)) { i ->
                                    update(selected) { it.copy(desqueeze = sq[i].takeIf { v -> v > 1f }) }
                                }
                                Text("For clips filmed with an anamorphic lens or adapter: stretches the picture back to its real shape (preview and export).", color = Pro.Dim, fontSize = 11.sp)
                            }
                            Page.COLOR -> if (sc != null) ColorPage(sc, colorTab, { colorTab = it }, onChange = { f -> update(selected, f) }, onCopyToAll = {
                                val g = sc.grade; val d = sc.detail
                                for (i in clips.indices) clips[i] = clips[i].copy(grade = g, detail = d)
                                message = "Grade copied to all clips"
                            })
                            Page.LAYERS -> LayersPage(
                                layers = layers, selectedId = selectedLayer, totalMs = totalMs, playheadMs = globalMs,
                                onSelect = { selectedLayer = it },
                                onAdd = { l -> layers.add(l); selectedLayer = l.id },
                                onAddLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onAddVideo = { pickLayerVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                onAddImage = { pickLayerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onUpdate = { l ->
                                    val i = layers.indexOfFirst { it.id == l.id }
                                    if (i >= 0) {
                                        val o = layers[i]
                                        // A locked layer only takes lock / show / name changes.
                                        if (o.locked && l.locked && l.copy(visible = o.visible, name = o.name) != o) message = "This layer is locked — tap its lock to edit"
                                        else layers[i] = o.editedAt(globalMs, l)
                                    }
                                },
                                onSeek = { seekGlobal(it) },
                                onCaptions = { lang, clipsOnly, style -> makeCaptions(lang, clipsOnly, style) },
                                onDelete = { id ->
                                    if (layers.any { it.id == id && it.locked }) message = "This layer is locked — tap its lock to delete it"
                                    else { layers.removeAll { it.id == id }; selectedLayer = layers.lastOrNull()?.id }
                                },
                                onMove = { id, dir ->
                                    val i = layers.indexOfFirst { it.id == id }; val to = i + dir
                                    if (i >= 0 && to in layers.indices) { val l = layers.removeAt(i); layers.add(to, l) }
                                },
                            )
                            Page.AUDIO -> AudioPage(
                                sc, deliver, music, playing,
                                onChange = { f -> update(selected, f) },
                                onPickMusic = { pickMusic.launch(arrayOf("audio/*")) },
                                onMusic = { music = it; if (it == null) musicPlayer.stop() },
                                extra = extraAudio,
                                onExtra = { id, t ->
                                    val i = extraAudio.indexOfFirst { it.id == id }
                                    if (i >= 0) { if (t == null) extraAudio.removeAt(i) else extraAudio[i] = t }
                                },
                                posInClip = if (selected == current) posMs else 0L,
                                recording = voiceRec != null, recordMs = voiceMs,
                                onVoice = { if (voiceRec == null) startVoice() else stopVoice() },
                                onBeats = { t -> addBeatMarkers(t) },
                            )
                            Page.DELIVER -> DeliverPage(deliver, clips, exporting, { deliver = it }, ::startExport, playheadMs = globalMs, onSaveFrame = { saveStill() })
                        }
                        if (clips.isEmpty() && page != Page.MEDIA && page != Page.DELIVER) Text("Add a clip in the Media page.", color = Pro.Dim, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (fullscreen) {
                BackHandler { fullscreen = false }
                var controls by remember { mutableStateOf(true) }
                var touch by remember { mutableStateOf(0) }
                // Controls hide by themselves a few seconds after the last touch while playing.
                LaunchedEffect(controls, touch, playing) {
                    if (controls && playing) { delay(3000); controls = false }
                }
                fun poke() { controls = true; touch++ }
                Box(
                    Modifier.fillMaxSize().background(Color.Black)
                        .pointerInput(Unit) { detectTapGestures(onTap = { if (controls) controls = false else poke() }, onDoubleTap = { togglePlay(); poke() }) },
                ) {
                    viewer(Modifier.fillMaxSize())
                    androidx.compose.animation.AnimatedVisibility(
                        controls, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            // Top: close + title
                            Row(
                                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TopIcon(Icons.Filled.FullscreenExit, "Exit full screen") { fullscreen = false }
                                Text(
                                    clips.getOrNull(current)?.name ?: "", color = Color.White, fontSize = 14.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                )
                                TopIcon(Icons.Filled.Close, "Close") { fullscreen = false }
                            }
                            // Centre: previous / play / next
                            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                RoundControl(Icons.Filled.SkipPrevious, "Previous", 52.dp) { prevClip(); poke() }
                                RoundControl(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", 72.dp, accent = true) { togglePlay(); poke() }
                                RoundControl(Icons.Filled.SkipNext, "Next", 52.dp) { nextClip(); poke() }
                            }
                            // Bottom: time, seek, mute
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Slider(
                                    value = if (totalMs > 0) globalMs.toFloat() / totalMs else 0f,
                                    onValueChange = { seekGlobal((it * totalMs).toLong()); poke() },
                                    modifier = Modifier.height(28.dp),
                                    colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tc(globalMs)} / ${tc(totalMs)}", style = Pro.Value.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
                                    TopIcon(if (previewMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute") { previewMuted = !previewMuted; poke() }
                                }
                            }
                        }
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close", enabled = !exporting) { confirmExit = true }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Video editor", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${clips.size} CLIPS · ${tc(totalMs)}", style = Pro.Label)
                    }
                    TopIcon(Icons.Filled.Settings, "Settings") { showSettings = true }
                    BarButton("LOAD", enabled = !exporting) { showLoad = true }
                    BarButton("SAVE", enabled = !exporting) { if (projectId == null) askName = true else saveProject(projectName) }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                        modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !exporting) { page = Page.DELIVER }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                // Resizable panels: hold a divider, then drag. Double-tap a divider to reset it.
                val tlPane = rememberPane(if (wide) "ve_timeline_land" else "ve_timeline_port")
                val timelineBox = @Composable {
                    if (tlPane.dp < 0f) timeline()
                    else Box(Modifier.fillMaxWidth().height(tlPane.dp.dp).verticalScroll(rememberScrollState())) { timeline() }
                }
                if (wide) {
                    val insp = rememberPane("ve_inspector_w")
                    val maxW = boxW
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            viewer(Modifier.fillMaxWidth().weight(1f))
                            transport()
                        }
                        ResizeHandle(false,
                            onDrag = { d -> insp.dp = ((if (insp.dp < 0f) 390f else insp.dp) - d).coerceIn(240f, maxW - 260f) },
                            onEnd = { insp.save() }, onReset = { insp.reset() })
                        inspector(Modifier.width((if (insp.dp < 0f) 390f else insp.dp).dp).fillMaxHeight())
                    }
                    ResizeHandle(true,
                        onDrag = { d -> tlPane.dp = ((if (tlPane.dp < 0f) 150f else tlPane.dp) - d).coerceIn(70f, boxH * 0.6f) },
                        onEnd = { tlPane.save() }, onReset = { tlPane.reset() })
                    timelineBox()
                } else {
                    val viewerPane = rememberPane("ve_viewer_port")
                    val autoViewer = boxW * 9f / 16f
                    val maxH = boxH
                    viewer(Modifier.fillMaxWidth().height((if (viewerPane.dp < 0f) autoViewer else viewerPane.dp).dp))
                    ResizeHandle(true,
                        onDrag = { d -> viewerPane.dp = ((if (viewerPane.dp < 0f) autoViewer else viewerPane.dp) + d).coerceIn(110f, maxH - 300f) },
                        onEnd = { viewerPane.save() }, onReset = { viewerPane.reset() })
                    transport()
                    timelineBox()
                    ResizeHandle(true,
                        onDrag = { d -> tlPane.dp = ((if (tlPane.dp < 0f) 150f else tlPane.dp) + d).coerceIn(70f, maxH * 0.5f) },
                        onEnd = { tlPane.save() }, onReset = { tlPane.reset() })
                    inspector(Modifier.fillMaxWidth().weight(1f))
                }
            }

            if (confirmExit) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    containerColor = Pro.PanelSolid,
                    title = { Text("Leave the video editor?", color = Color.White) },
                    text = { Text(if (projectId != null) "Save your latest changes to \"$projectName\" before leaving?" else "This timeline isn't saved yet. Save it as a project to continue later.", color = Pro.Dim) },
                    confirmButton = {
                        Row {
                            androidx.compose.material3.TextButton(onClick = { confirmExit = false; onExit() }) { Text("Leave", color = Pro.Red) }
                            androidx.compose.material3.TextButton(onClick = {
                                confirmExit = false
                                if (projectId == null) { leaveAfterSave = true; askName = true } else { saveProject(projectName); onExit() }
                            }) { Text("Save & leave", color = Pro.Amber) }
                        }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { Text("Stay", color = Color.White) } },
                )
            }
        }
    }
}

@Composable
private fun MediaPage(
    clips: List<Clip>, onAddGallery: () -> Unit, onAddFiles: () -> Unit,
    audioPool: List<PoolAudio>, audioUses: (PoolAudio) -> Int, onAddAudio: () -> Unit,
    onPlaceAudio: (PoolAudio) -> Unit, onRemoveAudio: (PoolAudio) -> Unit,
    proxyState: (Clip) -> String?, onProxyToggle: (Clip) -> Unit, onRemove: (Clip) -> Unit,
) {
    var confirm by remember { mutableStateOf<Clip?>(null) }
    confirm?.let { c ->
        val uses = clips.count { it.uri == c.uri }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Pro.PanelSolid,
            title = { Text("Delete this file?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to delete \"${c.name}\" from the media pool?" +
                        (if (uses > 1) " It is used $uses times on the timeline; all of them will be removed." else " It will also be removed from the timeline.") +
                        " The original on your phone is kept.",
                    color = Pro.Dim,
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onRemove(c); confirm = null }) { Text("Yes", color = Pro.Red) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("No", color = Pro.Amber) } },
        )
    }
    SectionTitle("MEDIA POOL")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(Icons.Filled.PhotoLibrary, "Gallery", Modifier.weight(1f), onAddGallery)
        BigButton(Icons.Filled.FolderOpen, "Files (MKV…)", Modifier.weight(1f), onAddFiles)
    }
    BigButton(Icons.Filled.MusicNote, "Audio (MP3, WAV, AAC…)", Modifier.fillMaxWidth().padding(top = 8.dp), onAddAudio)
    Text(
        "Clips are added to the end of the timeline. Heavy clips (HDR, 10-bit, 4K) get a light preview copy so grades and effects play smoothly; exports always use the original files.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp),
    )
    clips.distinctBy { it.uri.toString() }.forEachIndexed { i, c ->
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = Pro.Value.copy(color = Pro.Amber), modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val codec = when {
                        c.mime.contains("hevc") -> "HEVC"; c.mime.contains("avc") -> "H.264"; c.mime.contains("vp9") -> "VP9"
                        c.mime.contains("av01") -> "AV1"; c.mime.contains("dolby") -> "Dolby Vision"; else -> c.mime.removePrefix("video/").uppercase()
                    }
                    Text("${c.width}×${c.height} · ${tc(c.durationMs)} · $codec" + (if (c.hdr) " · HDR" else if (c.tenBit) " · 10-bit" else ""), style = Pro.Label)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { confirm = c }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete from media pool", tint = Pro.Red, modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val st = proxyState(c)
                Text(st ?: "PLAYING ORIGINAL", style = Pro.Label.copy(color = if (st?.startsWith("PREVIEW COPY") == true) Pro.Green else Pro.Dim, fontSize = 10.sp), modifier = Modifier.weight(1f))
                val action = when {
                    st == null -> "MAKE PREVIEW COPY"
                    st.startsWith("PREVIEW COPY") -> "PLAY ORIGINAL"
                    st == "PLAYING ORIGINAL" -> "USE PREVIEW COPY"
                    else -> null
                }
                if (action != null) Text(
                    action, style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onProxyToggle(c) }.padding(6.dp),
                )
            }
        }
    }
    if (audioPool.isNotEmpty()) {
        var confirmAudio by remember { mutableStateOf<PoolAudio?>(null) }
        confirmAudio?.let { a ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmAudio = null },
                containerColor = Pro.PanelSolid,
                title = { Text("Delete this file?", color = Color.White) },
                text = { Text("Are you sure you want to delete \"${a.name}\" from the media pool? It will also be removed from the timeline. The original on your phone is kept.", color = Pro.Dim) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { onRemoveAudio(a); confirmAudio = null }) { Text("Yes", color = Pro.Red) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmAudio = null }) { Text("No", color = Pro.Amber) } },
            )
        }
        SectionTitle("AUDIO")
        audioPool.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(a.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val n = audioUses(a)
                        Text(tc(a.durationMs) + if (n > 0) " · on timeline ×$n" else "", style = Pro.Label)
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { confirmAudio = a }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete from media pool", tint = Pro.Red, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    "ADD TO TIMELINE (AT PLAYHEAD)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onPlaceAudio(a) }.padding(6.dp),
                )
            }
        }
        Text("On the timeline: hold an audio bar to move it, or hold its start or end edge to trim it.", color = Pro.Dim, fontSize = 11.sp)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPage(
    c: Clip, index: Int, count: Int, posMs: Long, deliver: DeliverSettings, onDeliver: (DeliverSettings) -> Unit,
    onChange: ((Clip) -> Clip) -> Unit,
    onSplit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit,
    onFreeze: () -> Unit = {}, onReverse: () -> Unit = {}, onDetach: () -> Unit = {},
    onSlowMo: () -> Unit = {}, onReframe: (Float?) -> Unit = {},
) {
    SectionTitle("CLIP ${index + 1} OF $count · ${c.name}".uppercase())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.ContentCut, "Split", onSplit)
        SmallAction(Icons.Filled.PauseCircle, "Freeze", onFreeze)
        SmallAction(Icons.Filled.FastRewind, "Reverse", onReverse)
        SmallAction(Icons.Filled.CallSplit, "Detach audio", onDetach)
        SmallAction(Icons.Filled.SlowMotionVideo, "AI slow-mo", onSlowMo)
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
    SectionTitle("PROJECT FORMAT (ASPECT RATIO)")
    val formats = ASPECTS
    Chips(formats.map { it.first }, formats.indexOfFirst { it.second == deliver.aspect }.coerceAtLeast(0)) { onDeliver(deliver.copy(aspect = formats[it].second)) }
    Chips(listOf("Fill frame (crop)", "Fit (bars)"), if (deliver.fill) 0 else 1) { onDeliver(deliver.copy(fill = it == 0)) }
    SectionTitle("SPEED")
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f)
    Chips(speeds.map { if (it == 1f) "1x" else "${it}x" }, speeds.indexOf(c.speed).coerceAtLeast(0)) { i -> onChange { it.copy(speed = speeds[i]) } }
    SectionTitle("AUTO-REFRAME (AI)")
    Chips(listOf("9:16", "1:1", "4:5", "Clear"), -1) { i -> onReframe(listOf(9f / 16f, 1f, 0.8f, null)[i]) }
    Text("Changes the project to the chosen shape and makes every clip follow its subject (people, animals, vehicles…) so nothing important is cut off.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("SPEED RAMP")
    val ramps = RampPreset.entries
    Chips(listOf("None") + ramps.map { it.label }, if (c.ramp == null) 0 else ramps.indexOfFirst { it.label == c.rampName } + 1) { i ->
        onChange { if (i == 0) it.copy(ramp = null, rampName = null) else it.copy(ramp = ramps[i - 1].points, rampName = ramps[i - 1].label) }
    }
    c.ramp?.let { r ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised)) {
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..60) {
                val p = i / 60f
                val v = kotlin.math.ln(rampAt(r, p).toDouble()) / kotlin.math.ln(6.0)   // log scale, ±6x
                val y = size.height / 2f - (v.toFloat() * size.height / 2f * 0.9f)
                if (i == 0) path.moveTo(0f, y) else path.lineTo(p * size.width, y)
            }
            drawLine(Color.White.copy(alpha = 0.2f), androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), 1f)
            drawPath(path, Pro.Amber, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
        Text("Above the line = faster, below = slow motion. Combined with the speed above; the clip's length on the timeline follows the curve.", color = Pro.Dim, fontSize = 11.sp)
    }
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
            Text("Have the camera maker's conversion LUT? Import it in the LUTs tab and switch on \"Use as input conversion\".", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        ColorTab.LUTS -> {
            Text("Import your own 3D LUTs (.cube) — from grading software, camera makers (DJI, Sony, Panasonic…), or LUT packs. Tap + IMPORT, pick the file, then choose how it's used.", color = Pro.Dim, fontSize = 11.sp)
            LutSection(g) { ng -> grade { ng } }
            SectionTitle("TIP")
            Text("For log footage, switch on \"Use as input conversion\" with the camera maker's log → Rec.709 LUT, then grade on top. For a creative look, leave it off and lower Strength to taste. COPY GRADE TO ALL CLIPS applies the LUT to the whole edit.", color = Pro.Dim, fontSize = 11.sp)
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
            EdSlider("Image detail", c.detail.localDetail) { v -> detail { it.copy(localDetail = v) } }
            EdSlider("Noise reduction", c.detail.denoise, 0f..1f, format = ::pct) { v -> detail { it.copy(denoise = v) } }
            var aiInfo by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("AI NOISE REDUCTION", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
                Box(
                    Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape)
                        .border(1.5.dp, Pro.Amber, CircleShape).clickable { aiInfo = true },
                    contentAlignment = Alignment.Center,
                ) { Text("!", color = Pro.Amber, fontSize = 13.sp, fontWeight = FontWeight.Black) }
            }
            EdSlider("Strength", c.detail.aiDenoise, 0f..1f, format = { if (it <= 0f) "Off" else pct(it) }) { v -> detail { it.copy(aiDenoise = v) } }
            if (aiInfo) androidx.compose.material3.AlertDialog(
                onDismissRequest = { aiInfo = false },
                containerColor = Pro.PanelSolid,
                title = { Text("AI noise reduction", color = Color.White) },
                text = {
                    Text(
                        "The built-in AI (NAFNet) cleans every single frame of the video when you export. " +
                            "This is very demanding: depending on your phone's hardware, the clip's length and resolution, " +
                            "an export can take from several minutes up to about 1 hour.\n\n" +
                            "You can stop it at any time with the Stop button on the AI badge. " +
                            "The preview shows the normal noise reduction; the AI result appears in the exported video.",
                        color = Pro.Dim,
                    )
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { aiInfo = false }) { Text("OK", color = Pro.Amber) } },
            )
            SectionTitle("FINISHING")
            EdSlider("Vignette", c.detail.vignette) { v -> detail { it.copy(vignette = v) } }
            EdSlider("Film grain", c.detail.grain, 0f..1f, format = ::pct) { v -> detail { it.copy(grain = v) } }
        }
        ColorTab.EFFECTS -> {
            SectionTitle("EFFECTS") { detail { it.copy(fx = VideoFx.NONE, fxAmount = 0.6f) } }
            Chips(VideoFx.entries.map { it.label }, c.detail.fx.ordinal) { i -> detail { it.copy(fx = VideoFx.entries[i]) } }
            if (c.detail.fx != VideoFx.NONE) EdSlider("Amount", c.detail.fxAmount, 0f..1f, 0.6f, ::pct) { v -> detail { it.copy(fxAmount = v) } }
            Text("Effects show live in the viewer and are rendered on export.", color = Pro.Dim, fontSize = 11.sp)
        }
        ColorTab.LOOKS -> {
            SectionTitle("LOOKS / CREATIVE LUTS")
            LookStrip(g.look) { f -> grade { it.copy(look = f) } }
            if (g.look != Filter.NONE) EdSlider("Strength", g.lookAmount, 0f..1f, 1f, ::pct) { v -> grade { it.copy(lookAmount = v) } }
            UserPresetSection({ g to c.detail }) { p -> onChange { it.copy(grade = p.grade, detail = p.detail.copy(aiDenoise = it.detail.aiDenoise)) } }
        }
    }
}

@Composable
private fun AudioPage(
    c: Clip?, deliver: DeliverSettings, music: MusicTrack?, playing: Boolean,
    onChange: ((Clip) -> Clip) -> Unit, onPickMusic: () -> Unit, onMusic: (MusicTrack?) -> Unit,
    extra: List<MusicTrack> = emptyList(), onExtra: (String, MusicTrack?) -> Unit = { _, _ -> },
    posInClip: Long = 0L, recording: Boolean = false, recordMs: Long = 0L, onVoice: () -> Unit = {}, onBeats: (MusicTrack) -> Unit = {},
) {
    LevelMeter(playing)
    SectionTitle("VOICEOVER")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (recording) Pro.Red else Ed.Raised).border(2.dp, Pro.Red, CircleShape).clickable(onClick = onVoice),
            contentAlignment = Alignment.Center,
        ) {
            if (recording) Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            else Box(Modifier.size(22.dp).clip(CircleShape).background(Pro.Red))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(if (recording) "Recording… ${tc(recordMs)}" else "Record a voiceover", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (recording) "Tap to stop — it's added as an audio track where you started." else "Starts at the playhead and plays the edit (muted) while you speak. Headphones help.",
                color = Pro.Dim, fontSize = 11.sp,
            )
        }
    }
    if (music != null) {
        Text(
            "♪ ADD BEAT MARKERS FROM THE MUSIC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onBeats(music) }.padding(10.dp),
        )
        Text("Finds the beat of the music track and drops a marker on every beat — with SNAP on, layers and cuts lock to them.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (extra.isNotEmpty()) {
        SectionTitle("MORE AUDIO TRACKS")
        extra.forEach { t ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF0FA3A3))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(t.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("at ${tc(t.startMs)}", style = Pro.Label)
                    }
                    Text("BEATS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { onBeats(t) }.padding(6.dp))
                    Text("REMOVE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onExtra(t.id, null) }.padding(6.dp))
                }
                EdSlider("Volume", t.volume, 0f..1.5f, 1f, ::pct) { v -> onExtra(t.id, t.copy(volume = v)) }
                EdSlider("Fade in", t.fadeInMs / 1000f, 0f..5f, 0f, { "%.1f s".format(it) }) { v -> onExtra(t.id, t.copy(fadeInMs = (v * 1000).toLong())) }
                EdSlider("Fade out", t.fadeOutMs / 1000f, 0f..5f, 0.3f, { "%.1f s".format(it) }) { v -> onExtra(t.id, t.copy(fadeOutMs = (v * 1000).toLong())) }
            }
        }
    }
    if (c != null) {
        val fx = c.audio
        fun setFx(f: (AudioFx) -> AudioFx) = onChange { it.copy(audio = f(it.audio)) }
        SectionTitle("CLIP AUDIO", onReset = { onChange { it.copy(audio = AudioFx(), volume = 1f, muted = false) } })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(if (c.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (c.muted) "Muted" else "Sound on") { onChange { it.copy(muted = !it.muted) } }
            SmallAction(Icons.Filled.MusicNote, if (fx.duckMusic) "Lowers music" else "Music normal") { setFx { it.copy(duckMusic = !it.duckMusic) } }
        }
        EdSlider("Clip volume", c.volume, 0f..2f, 1f, { "%+.1f dB".format(dbfs(it)) }) { v -> onChange { it.copy(volume = v) } }
        SectionTitle("VOLUME KEYFRAMES") { onChange { it.copy(volKeys = null) } }
        Text("Rides the clip's volume over time — e.g. dip it under a voiceover. Add keyframes at different playhead positions and set each level.", color = Pro.Dim, fontSize = 11.sp)
        Text(
            "◆ ADD AT PLAYHEAD (${tc(posInClip)})", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                onChange { cl ->
                    val ks = cl.volKeys.orEmpty().filter { kotlin.math.abs(it.t - posInClip) > 100 }
                    cl.copy(volKeys = (ks + VolKey(posInClip, volAt(cl.volKeys, posInClip))).sortedBy { it.t })
                }
            }.padding(10.dp),
        )
        c.volKeys.orEmpty().forEachIndexed { ki, k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    EdSlider("◆ ${tc(k.t)}", k.gain, 0f..2f, 1f, { "%+.1f dB".format(dbfs(it)) }) { v ->
                        onChange { cl -> cl.copy(volKeys = cl.volKeys.orEmpty().mapIndexed { j, x -> if (j == ki) x.copy(gain = v) else x }) }
                    }
                }
                Icon(Icons.Filled.Close, contentDescription = "Delete keyframe", tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(18.dp).clickable {
                    onChange { cl -> cl.copy(volKeys = cl.volKeys.orEmpty().filterIndexed { j, _ -> j != ki }.ifEmpty { null }) }
                })
            }
        }
        EdSlider("Pan / balance", fx.pan, -1f..1f, 0f, { if (it < -0.01f) "L ${(-it * 100).toInt()}" else if (it > 0.01f) "R ${(it * 100).toInt()}" else "Centre" }) { v -> setFx { it.copy(pan = v) } }
        Text("Channels", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Chips(AudioChannels.entries.map { it.label }, fx.channels.ordinal) { i -> setFx { it.copy(channels = AudioChannels.entries[i]) } }
        EdSlider("Fade in", fx.fadeInMs / 1000f, 0f..5f, 0f, { "%.1f s".format(it) }) { v -> setFx { it.copy(fadeInMs = (v * 1000).toLong()) } }
        EdSlider("Fade out", fx.fadeOutMs / 1000f, 0f..5f, 0f, { "%.1f s".format(it) }) { v -> setFx { it.copy(fadeOutMs = (v * 1000).toLong()) } }

        SectionTitle("PRESETS")
        Chips(AudioPreset.entries.map { it.label }, AudioPreset.entries.indexOfFirst { p ->
            p.fx.copy(pan = fx.pan, fadeInMs = fx.fadeInMs, fadeOutMs = fx.fadeOutMs, duckMusic = fx.duckMusic, limiter = fx.limiter) == fx
        }) { i ->
            val p = AudioPreset.entries[i].fx
            setFx { it.copy(low = p.low, mid = p.mid, high = p.high, lowCut = p.lowCut, voice = p.voice, compressor = p.compressor, gate = p.gate, channels = p.channels) }
        }

        SectionTitle("EQUALIZER", onReset = { setFx { it.copy(low = 0f, mid = 0f, high = 0f, lowCut = false, voice = false) } })
        val db = { v: Float -> "%+.1f dB".format(v) }
        EdSlider("Low (bass)", fx.low, -12f..12f, 0f, db) { v -> setFx { it.copy(low = v) } }
        EdSlider("Mid", fx.mid, -12f..12f, 0f, db) { v -> setFx { it.copy(mid = v) } }
        EdSlider("High (treble)", fx.high, -12f..12f, 0f, db) { v -> setFx { it.copy(high = v) } }
        AudioSwitch("Low cut (80 Hz)", "Removes wind rumble and handling noise", fx.lowCut) { b -> setFx { it.copy(lowCut = b) } }
        AudioSwitch("Voice enhance", "Clearer speech: trims boom, lifts presence", fx.voice) { b -> setFx { it.copy(voice = b) } }

        SectionTitle("DYNAMICS", onReset = { setFx { it.copy(compressor = 0f, gate = 0f, limiter = true) } })
        EdSlider("Compressor", fx.compressor, 0f..1f, 0f, ::pct) { v -> setFx { it.copy(compressor = v) } }
        Text("Evens out loud and quiet parts and adds make-up gain.", color = Pro.Dim, fontSize = 11.sp)
        EdSlider("Noise gate", fx.gate, 0f..1f, 0f, { if (it <= 0f) "Off" else "%.0f dB".format(-62f + it * 30f) }) { v -> setFx { it.copy(gate = v) } }
        Text("Silences background hiss between words or sounds below the threshold.", color = Pro.Dim, fontSize = 11.sp)
        AudioSwitch("Limiter", "Soft-limits peaks so boosted audio never clips", fx.limiter) { b -> setFx { it.copy(limiter = b) } }
        Text("Fades play in the export; everything else is heard live in the preview.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }

    SectionTitle("MUSIC TRACK")
    if (music == null) {
        BigButton(Icons.Filled.MusicNote, "Add music (MP3, AAC, WAV…)", Modifier.fillMaxWidth(), onPickMusic)
        Text("The music plays under the whole timeline, mixed with the clips' own sound.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    } else {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Pro.Amber)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(music.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tc(music.durationMs), style = Pro.Label)
            }
            Text("REPLACE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable(onClick = onPickMusic).padding(6.dp))
            Text("REMOVE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onMusic(null) }.padding(6.dp))
        }
        EdSlider("Start from", music.offsetMs.toFloat(), 0f..(music.durationMs - 1000).coerceAtLeast(1).toFloat(), 0f, { tc(it.toLong()) }) { v -> onMusic(music.copy(offsetMs = v.toLong())) }
        EdSlider("Music volume", music.volume, 0f..1f, 0.8f, ::pct) { v -> onMusic(music.copy(volume = v)) }
        EdSlider("Fade in", music.fadeInMs / 1000f, 0f..5f, 1f, { "%.1f s".format(it) }) { v -> onMusic(music.copy(fadeInMs = (v * 1000).toLong())) }
        EdSlider("Fade out", music.fadeOutMs / 1000f, 0f..8f, 2f, { "%.1f s".format(it) }) { v -> onMusic(music.copy(fadeOutMs = (v * 1000).toLong())) }
        EdSlider("Ducking", music.duck, 0f..0.95f, 0.6f, { "%.0f dB".format(dbfs(1f - it)) }) { v -> onMusic(music.copy(duck = v)) }
        Text("How much the music drops under clips set to \"Lowers music\" (tap the music button on a clip).", color = Pro.Dim, fontSize = 11.sp)
        val db = { v: Float -> "%+.1f dB".format(v) }
        EdSlider("Music bass", music.fx.low, -12f..12f, 0f, db) { v -> onMusic(music.copy(fx = music.fx.copy(low = v))) }
        EdSlider("Music mids", music.fx.mid, -12f..12f, 0f, db) { v -> onMusic(music.copy(fx = music.fx.copy(mid = v))) }
        EdSlider("Music treble", music.fx.high, -12f..12f, 0f, db) { v -> onMusic(music.copy(fx = music.fx.copy(high = v))) }
    }
    if (!deliver.audio) Text("Audio is turned off in Deliver.", color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun AudioSwitch(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp)
            Text(detail, color = Pro.Dim, fontSize = 11.sp)
        }
        androidx.compose.material3.Switch(
            checked = on, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
        )
    }
}

/** Live L/R peak meter of the preview (after the clip's audio processing). */
@Composable
private fun LevelMeter(playing: Boolean) {
    var l by remember { mutableStateOf(-60f) }
    var r by remember { mutableStateOf(-60f) }
    LaunchedEffect(playing) {
        while (true) {
            if (!playing) { AudioMeter.left *= 0.5f; AudioMeter.right *= 0.5f }
            l = dbfs(AudioMeter.left); r = dbfs(AudioMeter.right)
            AudioMeter.left *= 0.8f; AudioMeter.right *= 0.8f
            delay(60)
        }
    }
    SectionTitle("LEVELS")
    listOf("L" to l, "R" to r).forEach { (name, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Pro.Label, modifier = Modifier.width(16.dp))
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
                val k = ((v + 60f) / 60f).coerceIn(0f, 1f)
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color(0xFF34C759), 0.75f to Color(0xFFFFCC00), 0.95f to Color(0xFFFF3B30),
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width * k, size.height),
                )
                // -12 dB and -6 dB guides
                listOf(-12f, -6f).forEach { g -> val x = size.width * (g + 60f) / 60f; drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height)) }
            }
            Text(if (v <= -59.9f) "−∞" else "%.0f".format(v), style = Pro.Value.copy(fontSize = 10.sp), modifier = Modifier.width(34.dp).padding(start = 6.dp))
        }
    }
}

/** Text, stickers and watermarks drawn over the viewer; drag the selected one to move it. */
@Composable
private fun LayerPreview(
    layers: List<Layer>, timeMs: Long, selectedId: String?, showAll: Boolean, playing: Boolean,
    onMove: (String, Float, Float) -> Unit, onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(2)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(2)
        layers.forEach { base ->
            if (!(base.activeAt(timeMs) || (showAll && base.visible) || base.id == selectedId)) return@forEach
            // Keyframed layers: where they are at this moment.
            val l = base.animAt(timeMs)
            if (l.kind == LayerKind.VIDEO) {
                val h = (l.size * hPx).toInt().coerceAtLeast(8)
                val w = (h * l.mediaAspect).toInt().coerceAtLeast(8)
                val sel = l.id == selectedId
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((l.x * wPx - w / 2f).toInt(), (l.y * hPx - h / 2f).toInt()) }
                        .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                        .then(if (sel) Modifier.border(1.dp, Pro.Amber) else Modifier)
                        .pointerInput(l.id) {
                            detectDragGestures(onDragStart = { onSelect(l.id) }) { change, drag ->
                                change.consume()
                                onMove(l.id, drag.x / wPx, drag.y / hPx)
                            }
                        },
                ) {
                    VideoLayerView(l, timeMs, playing && l.activeAt(timeMs))
                }
                return@forEach
            }
            val bmp by androidx.compose.runtime.produceState<Bitmap?>(null, base.copy(x = 0f, y = 0f, startMs = 0, endMs = 0, keys = null, opacity = 1f), wPx, hPx) {
                value = withContext(Dispatchers.Default) { LayerRenderer.render(context, base, wPx, hPx) }
            }
            val b0 = bmp ?: return@forEach
            // Keyframed size: scale the picture rendered at the layer's own size.
            val k = if (base.size > 0f) l.size / base.size else 1f
            val bw = (b0.width * k).toInt().coerceAtLeast(1); val bh = (b0.height * k).toInt().coerceAtLeast(1)
            val left = (l.x * wPx - bw / 2f).toInt()
            val top = (l.y * hPx - bh / 2f).toInt()
            val sel = l.id == selectedId
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(left, top) }
                    .size(with(density) { bw.toDp() }, with(density) { bh.toDp() })
                    .then(if (sel) Modifier.border(1.dp, Pro.Amber) else Modifier)
                    .pointerInput(l.id) {
                        detectDragGestures(onDragStart = { onSelect(l.id) }) { change, drag ->
                            change.consume()
                            onMove(l.id, drag.x / wPx, drag.y / hPx)
                        }
                    }
                    .clickable { onSelect(l.id) },
            ) {
                val img = remember(b0) { b0.asImageBitmap() }
                val mode = (l.blend ?: LayerBlend.NORMAL).compose
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawImage(img, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), alpha = l.opacity.coerceIn(0f, 1f), blendMode = mode)
                }
            }
        }
    }
}

private val TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFB300, 0xFFFF3B30, 0xFF34C759, 0xFF0A84FF, 0xFFFF7EB6, 0xFFB79CFF).map { it.toInt() }

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersPage(
    layers: List<Layer>, selectedId: String?, totalMs: Long, playheadMs: Long,
    onSelect: (String) -> Unit, onAdd: (Layer) -> Unit, onAddLogo: () -> Unit,
    onAddVideo: () -> Unit = {}, onAddImage: () -> Unit = {},
    onUpdate: (Layer) -> Unit, onDelete: (String) -> Unit, onMove: (String, Int) -> Unit,
    onSeek: (Long) -> Unit = {},
    onCaptions: (String?, Boolean, Int) -> Unit = { _, _, _ -> },
) {
    var stickers by remember { mutableStateOf(false) }
    // AI captions: speech to text (Whisper) turned into caption layers.
    var capLang by remember { mutableStateOf(0) }
    var capStyle by remember { mutableStateOf(0) }
    var capClipsOnly by remember { mutableStateOf(true) }
    val langs = listOf<Pair<String, String?>>("Auto" to null, "English" to "en", "Danish" to "da", "Faroese" to "fo", "Norwegian" to "no", "Swedish" to "sv", "German" to "de", "Spanish" to "es", "French" to "fr")
    SectionTitle("AI CAPTIONS")
    Chips(langs.map { it.first }, capLang) { capLang = it }
    Chips(listOf("Boxed", "Clean", "Big"), capStyle) { capStyle = it }
    Chips(listOf("Clips' speech only", "Everything (incl. music)"), if (capClipsOnly) 0 else 1) { capClipsOnly = it == 0 }
    Text(
        "MAKE CAPTIONS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onCaptions(langs[capLang].second, capClipsOnly, capStyle) }.padding(10.dp),
    )
    Text("Writes timed caption layers from the speech in your edit (Whisper, via your fal.ai account in Cloud AI settings). Edit or delete any caption like a normal text layer.", color = Pro.Dim, fontSize = 11.sp)
    val end = (playheadMs + 3000).coerceAtMost(totalMs.coerceAtLeast(1))
    SectionTitle("ADD LAYER")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.TextFields, "Text") { onAdd(Layer(kind = LayerKind.TEXT, startMs = playheadMs, endMs = end)) }
        SmallAction(Icons.Filled.EmojiEmotions, "Sticker") { stickers = !stickers }
        SmallAction(Icons.Filled.WaterDrop, "Watermark") {
            onAdd(Layer(kind = LayerKind.WATERMARK, text = "© AcluxPro", size = 0.04f, x = 0.84f, y = 0.92f, opacity = 0.65f, shadow = true, wholeVideo = true, font = LayerFont.LIGHT))
        }
        SmallAction(Icons.Filled.Movie, "Video", onAddVideo)
        SmallAction(Icons.Filled.Photo, "Image", onAddImage)
        SmallAction(Icons.Filled.Image, "Logo", onAddLogo)
    }
    if (stickers) {
        Column(Modifier.padding(vertical = 6.dp)) {
            STICKERS.chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { e ->
                        Text(e, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            onAdd(Layer(kind = LayerKind.STICKER, text = e, size = 0.14f, shadow = false, startMs = playheadMs, endMs = end)); stickers = false
                        }.padding(4.dp))
                    }
                }
            }
        }
    }
    SectionTitle("LAYERS (TOP FIRST)")
    if (layers.isEmpty()) Text("No layers yet. Add text, a sticker, a watermark or a logo.", color = Pro.Dim, fontSize = 12.sp)
    layers.asReversed().forEach { l ->
        val sel = l.id == selectedId
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                .border(1.dp, if (sel) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { onSelect(l.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(l.kind.label.uppercase(), style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.width(78.dp))
            Text(l.displayName, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (l.wholeVideo) "ALL" else "${tc(l.startMs)}", style = Pro.Label)
            Icon(
                if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Show",
                tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable { onUpdate(l.copy(visible = !l.visible)) },
            )
            Icon(
                if (l.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, contentDescription = "Lock",
                tint = if (l.locked) Pro.Amber else Pro.Dim, modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable { onUpdate(l.copy(locked = !l.locked)) },
            )
        }
    }
    val l = layers.firstOrNull { it.id == selectedId } ?: return
    SectionTitle("SELECTED ${l.kind.label.uppercase()}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.KeyboardArrowUp, "Up") { onMove(l.id, 1) }
        SmallAction(Icons.Filled.KeyboardArrowDown, "Down") { onMove(l.id, -1) }
        SmallAction(Icons.Filled.ContentCopy, "Copy") { onAdd(l.copy(id = java.util.UUID.randomUUID().toString(), x = (l.x + 0.05f).coerceAtMost(1f), y = (l.y + 0.05f).coerceAtMost(1f))) }
        SmallAction(Icons.Filled.Delete, "Delete") { onDelete(l.id) }
    }
    androidx.compose.material3.OutlinedTextField(
        value = l.name ?: "", onValueChange = { onUpdate(l.copy(name = it)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        singleLine = true,
        label = { Text(if (l.kind == LayerKind.WATERMARK) "Watermark name (list & timeline)" else "Layer name (list & timeline)") },
        placeholder = { Text(l.displayName, color = Pro.Dim) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
            ),
    )
    if (l.kind == LayerKind.TEXT || l.kind == LayerKind.WATERMARK) {
        androidx.compose.material3.OutlinedTextField(
            value = l.text, onValueChange = { onUpdate(l.copy(text = it)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            label = { Text(if (l.kind == LayerKind.WATERMARK) "Watermark text (shown on the video)" else "Text") },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
            ),
        )
        Chips(LayerFont.entries.map { it.label }, l.font.ordinal) { onUpdate(l.copy(font = LayerFont.entries[it])) }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TEXT_COLORS.forEach { c ->
                Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                    .border(if (c == l.color) 3.dp else 1.dp, if (c == l.color) Pro.Amber else Pro.Line, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onUpdate(l.copy(color = c)) })
            }
        }
        Chips(listOf("Shadow", "Box"), -1) { if (it == 0) onUpdate(l.copy(shadow = !l.shadow)) else onUpdate(l.copy(box = !l.box)) }
        Text("Shadow: ${if (l.shadow) "on" else "off"} · Box: ${if (l.box) "on" else "off"}", style = Pro.Label)
    }
    if (l.kind == LayerKind.VIDEO && l.videoDurationMs > 0) {
        EdSlider("Start inside the video", l.videoStartMs.toFloat(), 0f..(l.videoDurationMs - 500).coerceAtLeast(1).toFloat(), 0f, { tc(it.toLong()) }) { onUpdate(l.copy(videoStartMs = it.toLong())) }
        Text("Overlay videos play muted. Size 100% = full frame height.", color = Pro.Dim, fontSize = 11.sp)
    }
    // With keyframes, the controls show (and edit) the layer as it is at the playhead.
    val la = l.animAt(playheadMs)
    EdSlider("Size", la.size, 0.02f..(if (l.isMedia) 1f else 0.4f), 0.07f, ::pct) { onUpdate(la.copy(size = it)) }
    EdSlider("Opacity", la.opacity, 0.05f..1f, 1f, ::pct) { onUpdate(la.copy(opacity = it)) }
    SectionTitle("POSITION")
    Text("Drag the layer in the viewer, or snap it:", color = Pro.Dim, fontSize = 11.sp)
    val spots = listOf(0.12f, 0.5f, 0.88f)
    Column {
        spots.forEach { y ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                spots.forEach { x ->
                    val on = kotlin.math.abs(la.x - x) < 0.02f && kotlin.math.abs(la.y - y) < 0.02f
                    Box(Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Pro.Amber else Ed.Raised).clickable { onUpdate(la.copy(x = x, y = y)) })
                }
            }
        }
    }
    SectionTitle("KEYFRAMES") { onUpdate(l.copy(keys = null)) }
    Text(
        "Add a keyframe, move the playhead, then drag / resize / fade the layer — a keyframe is added there and the layer glides between them.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    Text(
        "◆ ADD KEYFRAME AT PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onUpdate(l.keyAt(playheadMs)) }.padding(10.dp),
    )
    l.keys.orEmpty().forEach { k ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (kotlin.math.abs(k.t - playheadMs) < 120) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                .clickable { onSeek(k.t) }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◆ ${tc(k.t)}", style = Pro.Value.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.width(96.dp))
            Text("size ${pct(k.size)} · opacity ${pct(k.opacity)}", style = Pro.Label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, contentDescription = "Delete keyframe", tint = Color.White, modifier = Modifier.size(18.dp).clickable {
                onUpdate(l.copy(keys = l.keys.orEmpty().filter { it !== k }.ifEmpty { null }))
            })
        }
    }
    if (l.kind != LayerKind.VIDEO) {
        SectionTitle("BLEND MODE")
        Chips(LayerBlend.entries.map { it.label }, (l.blend ?: LayerBlend.NORMAL).ordinal) { onUpdate(l.copy(blend = LayerBlend.entries[it].takeIf { b -> b != LayerBlend.NORMAL })) }
        Text("Screen / Add for light leaks and glows, Multiply for shadows and textures, Overlay / Soft light for colour.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (l.kind == LayerKind.VIDEO || l.kind == LayerKind.IMAGE || l.kind == LayerKind.LOGO) {
        SectionTitle("CHROMA KEY (GREEN SCREEN)")
        val keyColors = listOf<Int?>(null, 0xFF00B140.toInt(), 0xFF0047BB.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        Chips(listOf("Off", "Green", "Blue", "Black", "White"), keyColors.indexOf(l.chroma).coerceAtLeast(0)) { onUpdate(l.copy(chroma = keyColors[it])) }
        if (l.chroma != null) {
            EdSlider("Tolerance", l.chromaTol ?: 0.4f, 0f..1f, 0.4f, ::pct) { onUpdate(l.copy(chromaTol = it)) }
            EdSlider("Edge softness", l.chromaSoft ?: 0.3f, 0f..1f, 0.3f, ::pct) { onUpdate(l.copy(chromaSoft = it)) }
            if (l.kind == LayerKind.VIDEO && android.os.Build.VERSION.SDK_INT < 33) Text("This phone can't show the key in the preview (Android 13+ needed); the export is keyed.", color = Pro.Dim, fontSize = 11.sp)
        }
    }
    SectionTitle("TIMING")
    Chips(listOf("Part of the video", "Whole video"), if (l.wholeVideo) 1 else 0) { onUpdate(l.copy(wholeVideo = it == 1)) }
    if (!l.wholeVideo && totalMs > 0) {
        RangeSlider(
            value = l.startMs.coerceIn(0, totalMs).toFloat()..l.endMs.coerceIn(0, totalMs).toFloat(),
            onValueChange = { r -> onUpdate(l.copy(startMs = r.start.toLong(), endMs = r.endInclusive.toLong())) },
            valueRange = 0f..totalMs.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
        )
        Text("${tc(l.startMs)} → ${tc(l.endMs)}", style = Pro.Label)
        Text("START AT PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable {
            val len = (l.endMs - l.startMs).coerceAtLeast(500)
            onUpdate(l.copy(startMs = playheadMs, endMs = (playheadMs + len).coerceAtMost(totalMs)))
        }.padding(vertical = 8.dp))
    }
}

@Composable
private fun DeliverPage(
    s: DeliverSettings, clips: List<Clip>, exporting: Boolean, onChange: (DeliverSettings) -> Unit, onExport: () -> Unit,
    playheadMs: Long = 0L, onSaveFrame: () -> Unit = {},
) {
    SectionTitle("PRESETS")
    Chips(EXPORT_PRESETS.map { it.label }, EXPORT_PRESETS.indexOfFirst { p ->
        s.shortSide == p.shortSide && s.fps == p.fps && s.hevc == p.hevc && s.bitrateMbps == p.mbps && s.aspect == p.aspect
    }) { i ->
        val p = EXPORT_PRESETS[i]
        onChange(s.copy(shortSide = p.shortSide, fps = p.fps, hevc = p.hevc, bitrateMbps = p.mbps, aspect = p.aspect, audioOnly = false, audio = true))
    }
    Text("Sets size, shape, frame rate and quality for where the video is going. Change anything below afterwards.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("RANGE") { onChange(s.copy(rangeStart = null, rangeEnd = null)) }
    val total0 = timelineLength(clips)
    val ra = s.rangeStart; val rb = s.rangeEnd
    Chips(listOf("Whole timeline", "Part (in / out)"), if (ra != null && rb != null) 1 else 0) { i ->
        if (i == 0) onChange(s.copy(rangeStart = null, rangeEnd = null))
        else onChange(s.copy(rangeStart = ra ?: 0L, rangeEnd = rb ?: total0))
    }
    if (ra != null && rb != null) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("IN ${tc(ra)}", style = Pro.Value.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
            Text("SET IN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onChange(s.copy(rangeStart = playheadMs.coerceAtMost(rb - 100))) }.padding(8.dp))
            Text("OUT ${tc(rb)}", style = Pro.Value.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
            Text("SET OUT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onChange(s.copy(rangeEnd = playheadMs.coerceAtLeast(ra + 100))) }.padding(8.dp))
        }
        Text("Move the playhead on the timeline, then tap SET IN / SET OUT. Only that part is exported (${tc(rb - ra)}).", color = Pro.Dim, fontSize = 11.sp)
    }
    SectionTitle("STILL FRAME")
    Text(
        "SAVE FRAME AS PHOTO", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(onClick = onSaveFrame).padding(10.dp),
    )
    Text("Saves the frame under the playhead, with its grade and text / logo layers, as a full-size JPEG.", color = Pro.Dim, fontSize = 11.sp)
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
    Chips(listOf("Video with audio", "Video, no audio", "Audio only (.m4a)"), if (s.audioOnly) 2 else if (s.audio) 0 else 1) {
        onChange(s.copy(audio = it != 1, audioOnly = it == 2))
    }
    if (s.audioOnly) Text("Saves just the sound of the timeline — clips, music and audio tracks — as an AAC .m4a file in Music/AcluxPro (or your chosen save folder). Muted tracks are left out.", color = Pro.Dim, fontSize = 11.sp)
    val total = if (s.rangeStart != null && s.rangeEnd != null) (s.rangeEnd - s.rangeStart).coerceAtLeast(0) else timelineLength(clips)
    // Estimated file size: video bitrate (chosen, or what the encoder picks on Auto) + AAC audio.
    val (ow, oh) = outputSize(clips, s)
    val outFps = if (s.fps > 0) s.fps else 30
    val autoMbps = ow.toDouble() * oh * outFps * 0.14 / 1_000_000
    val videoMbps = if (s.audioOnly) 0.0 else if (s.bitrateMbps > 0) s.bitrateMbps.toDouble() else autoMbps
    val audioMbps = if (s.audio || s.audioOnly) 0.13 else 0.0
    val bytes = (videoMbps + audioMbps) * 1_000_000 / 8 * (total / 1000.0) * 1.01
    SectionTitle("ESTIMATED FILE SIZE")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ed.Raised).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("≈ ${fileSizeText(bytes)}", style = Pro.Value.copy(color = Pro.Amber, fontSize = 22.sp), modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            if (s.audioOnly) {
                Text("Audio only · AAC 128 kbps", style = Pro.Label)
                Text(tc(total), style = Pro.Label)
            } else {
                Text("${ow}×${oh} · ${if (s.fps > 0) "${s.fps} fps" else "source fps"}", style = Pro.Label)
                Text("%.0f Mbps${if (s.bitrateMbps == 0) " (auto)" else ""} · ${tc(total)}".format(videoMbps + audioMbps), style = Pro.Label)
            }
        }
    }
    Text(
        "Timeline ${tc(total)} · ${clips.size} clips. The real size can differ a little${if (s.bitrateMbps == 0) " (on Auto the phone's encoder decides the bitrate)" else ""}. If the phone's encoder can't do the chosen size or bitrate, the closest supported setting is used.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp),
    )
    Text(
        if (exporting) "RENDERING…" else if (s.audioOnly) "EXPORT AUDIO" else "RENDER & EXPORT",
        style = Pro.Value.copy(color = Color.Black, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (exporting || clips.isEmpty()) Pro.Amber.copy(alpha = 0.4f) else Pro.Amber)
            .clickable(enabled = !exporting && clips.isNotEmpty(), onClick = onExport).padding(14.dp),
    )
}

/** 1.2 GB / 340 MB style. */
fun fileSizeText(bytes: Double): String = when {
    bytes >= 1e9 -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1e6 -> "%.0f MB".format(bytes / 1e6)
    else -> "%.0f KB".format((bytes / 1e3).coerceAtLeast(1.0))
}

/** Timecode mm:ss.ff-style (hours when needed). */
fun tc(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val h = t / 3_600_000; val m = (t / 60_000) % 60; val s = (t / 1000) % 60; val cs = (t % 1000) / 10
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d.%02d".format(m, s, cs)
}

/** Common aspect ratios (label, width / height; 0 = original). */
val ASPECTS = listOf(
    "Original" to 0f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "1:1" to 1f, "4:5" to 0.8f, "4:3" to 4f / 3f,
    "3:4" to 0.75f, "21:9" to 21f / 9f, "2.39:1" to 2.39f, "3:2" to 1.5f,
)

/** Live preview of a video layer: its own muted player, kept in step with the timeline. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoLayerView(l: Layer, timeMs: Long, play: Boolean) {
    val context = LocalContext.current
    val p = remember(l.videoUri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            l.videoUri?.let { setMediaItem(MediaItem.fromUri(it)); prepare() }
        }
    }
    DisposableEffect(p) { onDispose { p.release() } }
    val target = l.videoStartMs + (timeMs - (if (l.wholeVideo) 0L else l.startMs)).coerceAtLeast(0)
    LaunchedEffect(play) { if (play) p.play() else p.pause() }
    LaunchedEffect(target / 300, play) {
        if (!play || kotlin.math.abs(p.currentPosition - target) > 400) p.seekTo(target.coerceAtMost((l.videoDurationMs - 50).coerceAtLeast(0)))
    }
    AndroidView(
        factory = { ctx -> android.view.TextureView(ctx).also { p.setVideoTextureView(it) } },
        update = { tv -> ChromaPreview.apply(tv, l) },
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = l.opacity },
    )
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (accent) Pro.Amber else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (accent) Color.Black else Color.White, modifier = Modifier.size(size * 0.55f)) }
}

/** The two preview players and which one is on screen. */
private class Deck(val players: List<ExoPlayer>) {
    var active by mutableStateOf(0)
    val cur: ExoPlayer get() = players[active]
    val spare: ExoPlayer get() = players[1 - active]
}

@Composable
private fun TransportIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).then(if (accent) Modifier.background(Pro.Amber) else Modifier).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (accent) Color.Black else tint, modifier = Modifier.size(20.dp))
    }
}

/** ExoPlayer whose audio runs through [fx] (EQ, dynamics, channels) before output. */
@OptIn(UnstableApi::class)
private fun fxPlayer(context: android.content.Context, fx: AudioFxProcessor): ExoPlayer {
    val rf = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(fx))
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
    }
    return ExoPlayer.Builder(context, rf).build()
}

/** Pinch to zoom, drag to move and twist to rotate the picture; thirds grid while editing. */
@Composable
private fun CropOverlay(editingEnd: Boolean, onGesture: (androidx.compose.ui.geometry.Offset, Float, Float, Float, Float) -> Unit, onDone: () -> Unit) {
    val cb by androidx.compose.runtime.rememberUpdatedState(onGesture)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoomF, rot ->
                cb(pan, zoomF, rot, size.width.toFloat(), size.height.toFloat())
            }
        }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = 0.45f)
            for (k in 1..2) {
                drawLine(c, androidx.compose.ui.geometry.Offset(size.width * k / 3, 0f), androidx.compose.ui.geometry.Offset(size.width * k / 3, size.height), 1.5f)
                drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height * k / 3), androidx.compose.ui.geometry.Offset(size.width, size.height * k / 3), 1.5f)
            }
            drawRect(Pro.Amber, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        }
        Text(
            if (editingEnd) "CROP · END FRAME" else "CROP", style = Pro.Label.copy(color = Color.Black, fontSize = 10.sp),
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Pro.Amber).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            "DONE", style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(onClick = onDone).padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** One undo step in the video editor. */
private data class EditSnap(val clips: List<Clip>, val layers: List<Layer>, val music: MusicTrack?, val extra: List<MusicTrack>)

/** Splits caption text into pieces of at most two lines of about [max] characters. */
fun captionPieces(text: String, max: Int): List<String> {
    val words = text.replace('\n', ' ').split(' ').filter { it.isNotBlank() }
    val lines = ArrayList<String>()
    var cur = StringBuilder()
    for (w in words) {
        if (cur.isNotEmpty() && cur.length + 1 + w.length > max) { lines += cur.toString(); cur = StringBuilder() }
        if (cur.isNotEmpty()) cur.append(' ')
        cur.append(w)
    }
    if (cur.isNotEmpty()) lines += cur.toString()
    return lines.chunked(2).map { it.joinToString("\n") }.ifEmpty { listOf(text) }
}
