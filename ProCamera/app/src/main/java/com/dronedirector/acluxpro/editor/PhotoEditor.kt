package com.dronedirector.acluxpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.camera.Filter
import com.dronedirector.acluxpro.camera.GradeIO
import com.dronedirector.acluxpro.camera.MediaLibrary
import com.dronedirector.acluxpro.camera.Storage
import com.dronedirector.acluxpro.ui.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dronedirector.acluxpro.ui.hintedScroll
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhotoEdit(
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    val geometry: Geometry = Geometry(),
    val watermark: Layer? = null,
    val masks: List<PhotoMask> = emptyList(),
    val spots: List<Spot> = emptyList(),
    val lens: LensFix = LensFix(),
    val bg: PhotoBackground = PhotoBackground(),
    val baseFile: String? = null,
)

private enum class PhotoPanel(val label: String) {
    PRESETS("Presets"), LIGHT("Light"), CURVE("Curve"), COLOR("Color"), MIXER("HSL Mixer"),
    GRADING("Grading"), DETAIL("Detail"), EFFECTS("Effects"), MASKS("Masks"), HEAL("Heal"), LENS("Lens"),
    CROP("Crop & perspective"), WATERMARK("Watermark"), EXPORT("Export"),
}

@Composable
fun PhotoEditorScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<Uri?>(null) }
    var project by remember { mutableStateOf<PhotoProjectData?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) source = uri }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) source = uri }
    // Merge several photos (HDR, panorama, focus stack) into a new one, then edit it.
    val scope = rememberCoroutineScope()
    var mergeKind by remember { mutableStateOf<MergeKind?>(null) }
    var merging by remember { mutableStateOf<String?>(null) }
    val mergePick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { list ->
        val kind = mergeKind ?: return@rememberLauncherForActivityResult
        if (list.size < 2) { if (list.isNotEmpty()) android.widget.Toast.makeText(context, "Pick at least 2 photos", android.widget.Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        merging = "Starting…"
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                try {
                    val b = Merge.run(context, kind, list) { msg -> scope.launch { merging = msg } } ?: return@withContext null
                    savePhotoAs(context, b, 0, 95, list[list.size / 2], false).also { b.recycle() }
                } catch (_: OutOfMemoryError) { null } catch (_: Throwable) { null }
            }
            merging = null
            if (uri != null) { project = null; source = uri }
            else android.widget.Toast.makeText(context, "Couldn't merge those photos", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val src = source
    if (src == null) {
        BackHandler { onExit() }
        merging?.let { msg ->
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Pro.PanelSolid).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Pro.Amber)
                    Text(msg, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        MediaChooser(
            onMerge = { k -> mergeKind = k; mergePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            title = "Photo editor",
            subtitle = "Choose a photo to edit. The original is never changed; edits are exported as a new picture.",
            videos = false,
            onBack = onExit,
            onGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFiles = { browse.launch(arrayOf("image/*")) },
            onRecent = { project = null; source = it },
            onOpenProject = { id ->
                val p = ProjectStore.loadPhoto(context, id)
                if (p != null && p.uri != Uri.EMPTY) { project = p; source = p.uri }
                else android.widget.Toast.makeText(context, "Couldn't open that project", android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    } else {
        androidx.compose.runtime.key(src, project?.id) {
            PhotoWorkspace(src, project, onClose = { source = null; project = null }, onLoad = { id ->
                val p = ProjectStore.loadPhoto(context, id)
                if (p != null && p.uri != Uri.EMPTY) { project = p; source = p.uri }
                else android.widget.Toast.makeText(context, "Couldn't open that project", android.widget.Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** Start page of an editor: pick from gallery, browse files, or recent AcluxPro media. */
@Composable
fun MediaChooser(
    title: String,
    subtitle: String,
    videos: Boolean,
    onBack: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onRecent: (Uri) -> Unit,
    onOpenProject: (String) -> Unit = {},
    onMerge: ((MergeKind) -> Unit)? = null,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    if (settings) EditorSettingsDialog(title, if (videos) com.dronedirector.acluxpro.ui.AppScreen.VIDEO_EDITOR else com.dronedirector.acluxpro.ui.AppScreen.PHOTO_EDITOR) { settings = false }
    val recent by produceState<List<com.dronedirector.acluxpro.camera.MediaItem>>(emptyList()) {
        value = withContext(Dispatchers.IO) { MediaLibrary.query(context).filter { it.isVideo == videos }.take(60) }
    }
    Column(Modifier.fillMaxSize().background(Ed.Bg).windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp).weight(1f))
            TopIcon(Icons.Filled.Settings, "Settings") { settings = true }
        }
        Text(subtitle, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(Icons.Filled.PhotoLibrary, if (videos) "Gallery videos" else "Gallery photos", Modifier.weight(1f), onGallery)
            BigButton(Icons.Filled.FolderOpen, "Browse files", Modifier.weight(1f), onFiles)
        }
        if (onMerge != null) {
            Text("MERGE PHOTOS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
                MergeKind.entries.forEach { k ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable { onMerge(k) }.padding(10.dp)) {
                        Text(k.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(k.hint, color = Pro.Dim, fontSize = 10.sp, maxLines = 3)
                    }
                }
            }
        }
        if (videos) {
            Text("Browse files also opens MKV, MOV, WebM and clips from SD cards or USB drives.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        SavedProjects(videos, onOpenProject)
        Text("RECENT ACLUXPRO ${if (videos) "VIDEOS" else "PHOTOS"}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        if (recent.isEmpty()) {
            Text("Nothing yet.", color = Pro.Dim, fontSize = 13.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(recent, key = { it.uri.toString() }) { item ->
                    val thumb by produceState<ImageBitmap?>(null, item.uri) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(item.uri, android.util.Size(256, 256), null).asImageBitmap()
                                else GradeIO.decode(context, item.uri, 256)?.asImageBitmap()
                            } catch (_: Throwable) { null }
                        }
                    }
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onRecent(item.uri) }) {
                        thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        if (item.isRaw) Text("RAW", style = Pro.Label.copy(color = Color.Black, fontSize = 8.sp), modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Pro.Amber).padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BigButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Ed.Raised).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Pro.Amber)
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun PhotoWorkspace(uri: Uri, initial: PhotoProjectData?, onClose: () -> Unit, onLoad: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var edit by remember {
        mutableStateOf(
            @Suppress("USELESS_ELVIS")
            if (initial != null) PhotoEdit(initial.grade, initial.detail, initial.geometry, initial.watermark, initial.masks ?: emptyList(), initial.spots ?: emptyList(), initial.lens ?: LensFix(), initial.bg ?: PhotoBackground(), initial.baseFile)
            else PhotoEdit(watermark = WatermarkMemory.load(context, false))
        )
    }
    var projectId by remember { mutableStateOf(initial?.id) }
    var projectName by remember { mutableStateOf(initial?.name ?: "") }
    var askName by remember { mutableStateOf(false) }
    var showLoad by remember { mutableStateOf(false) }
    if (showLoad) LoadProjectDialog(false, onOpen = { showLoad = false; onLoad(it) }, onDismiss = { showLoad = false })
    var panel by remember { mutableStateOf(PhotoPanel.PRESETS) }
    var comparing by remember { mutableStateOf(false) }
    // Before / after split view with a draggable divider.
    var split by remember { mutableStateOf(false) }
    var splitX by remember { mutableStateOf(0.5f) }
    var beforeImg by remember { mutableStateOf<Bitmap?>(null) }
    // Panels menu (layout icon next to the settings gear): which parts of the editor are shown.
    val showScope = com.dronedirector.acluxpro.ui.panelOn("pe.scope")
    val showControls = com.dronedirector.acluxpro.ui.panelOn("pe.controls")
    val showCopyBar = com.dronedirector.acluxpro.ui.panelOn("pe.copypaste")
    val showCompare = com.dronedirector.acluxpro.ui.panelOn("pe.compare")
    var paneVersion by remember { mutableStateOf(0) }
    var hist by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) EditorSettingsDialog("Photo editor", com.dronedirector.acluxpro.ui.AppScreen.PHOTO_EDITOR) { showSettings = false }
    val undo = remember { mutableStateListOf(edit) }
    val wmOn = WatermarkMemory.isOn(context, false)
    LaunchedEffect(edit.watermark, wmOn) { if (wmOn && edit.watermark != null) WatermarkMemory.save(context, false, edit.watermark) }
    var undoPos by remember { mutableStateOf(0) }

    // Export options
    var format by remember { mutableStateOf(initial?.format ?: 0) }       // JPEG, PNG
    var quality by remember { mutableStateOf(initial?.quality ?: 0.95f) }
    var sizeIdx by remember { mutableStateOf(initial?.sizeIdx ?: 0) }       // see `sizes`
    var aiScale by remember { mutableStateOf(0) }                          // 0 = off, 2, 4
    var keepExif by remember { mutableStateOf(true) }
    var stripGps by remember { mutableStateOf(false) }
    val tools = remember { LocalToolsState() }
    // AI masks (subject / sky) are kept as grey pictures in the app's files.
    val aiMasks = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(edit.masks.mapNotNull { it.maskFile } + listOfNotNull(edit.bg.maskFile)) {
        for (f in edit.masks.mapNotNull { it.maskFile } + listOfNotNull(edit.bg.maskFile)) if (aiMasks[f] == null) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f) }?.let { aiMasks[f] = it }
    }
    fun saveProject(name: String) {
        val e = edit
        val p = PhotoProjectData(
            id = projectId ?: java.util.UUID.randomUUID().toString(), name = name, uri = uri,
            grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
            format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile,
        )
        val thumb = preview
        scope.launch {
            val saved = withContext(Dispatchers.IO) { try { ProjectStore.savePhoto(context, p, thumb) } catch (_: Throwable) { null } }
            if (saved != null) { projectId = saved.id; projectName = saved.name; message = "Project \"${saved.name}\" saved" }
            else message = "Couldn't save the project"
        }
    }
    if (askName) ProjectNameDialog(ProjectStore.defaultName(false), onSave = { askName = false; saveProject(it) }, onDismiss = { askName = false })
    val sizes = listOf(4800, 4096, 2560, 1920, 1080, 2048, 3840)

    BackHandler { if (!busy) onClose() }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) edit = edit.copy(watermark = Layer(kind = LayerKind.LOGO, imageUri = u, size = 0.1f, x = 0.86f, y = 0.9f, opacity = 0.85f, wholeVideo = true))
    }

    // The photo being edited: the original, or its AI-erased copy.
    fun sourceUri(e: PhotoEdit): Uri = e.baseFile?.let { Uri.fromFile(java.io.File(it)) } ?: uri
    LaunchedEffect(uri, edit.baseFile) {
        val b = withContext(Dispatchers.IO) { GradeIO.decode(context, sourceUri(edit), 1400) }
        if (b == null) failed = true else base = b
    }
    // Live preview: while a slider moves, a small copy is re-rendered continuously (never thrown
    // away half-way); once you pause, the full-size preview follows.
    val renderReq = remember { kotlinx.coroutines.channels.Channel<PhotoEdit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    LaunchedEffect(edit) { renderReq.trySend(edit) }
    // AI noise reduction: the AI cleans the preview image once; the strength slider then just blends.
    var aiBase by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(edit.baseFile) { aiBase = null }
    val aiOn = edit.detail.aiDenoise > 0f
    LaunchedEffect(base, aiOn) {
        val b = base ?: return@LaunchedEffect
        if (!aiOn || aiBase != null) return@LaunchedEffect
        val res = withContext(Dispatchers.IO) {
            try {
                val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                AiDenoiser.denoise(context, px, b.width, b.height)?.let { Bitmap.createBitmap(it, b.width, b.height, Bitmap.Config.ARGB_8888) } ?: "unavailable"
            } catch (_: AiDenoiser.Stopped) { "stopped" } catch (t: Throwable) { if (t.cause is AiDenoiser.Stopped) "stopped" else "unavailable" }
        }
        when (res) {
            is Bitmap -> aiBase = res
            "stopped" -> { edit = edit.copy(detail = edit.detail.copy(aiDenoise = 0f)); message = "AI noise reduction stopped" }
            else -> { edit = edit.copy(detail = edit.detail.copy(aiDenoise = 0f)); message = "AI noise reduction isn't available on this phone" }
        }
    }
    // Heal works on the whole, uncropped photo so taps land where they should.
    val healMode = rememberUpdatedState(panel == PhotoPanel.HEAL)
    LaunchedEffect(panel == PhotoPanel.HEAL, aiMasks.size) { renderReq.trySend(edit) }
    LaunchedEffect(base, aiBase) {
        val b = base ?: return@LaunchedEffect
        val ai = aiBase
        fun mixed(src: Bitmap, aiSrc: Bitmap?, t: Float): Bitmap {
            if (aiSrc == null || t <= 0f || aiSrc.width != src.width || aiSrc.height != src.height) return src
            val n = src.width * src.height
            val a = IntArray(n).also { src.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
            val c = IntArray(n).also { aiSrc.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
            return Bitmap.createBitmap(AiDenoiser.blend(a, c, t), src.width, src.height, Bitmap.Config.ARGB_8888)
        }
        fun scaleDown(x: Bitmap): Bitmap {
            val k = 640f / maxOf(x.width, x.height)
            return if (k < 1f) Bitmap.createScaledBitmap(x, (x.width * k).toInt().coerceAtLeast(1), (x.height * k).toInt().coerceAtLeast(1), true) else x
        }
        val small = withContext(Dispatchers.Default) { scaleDown(b) }
        val aiSmall = withContext(Dispatchers.Default) { ai?.let { scaleDown(it) } }
        fun draw(src: Bitmap, e: PhotoEdit): Bitmap {
            val isSmall = src === small
            val s0 = if (e.detail.aiDenoise > 0f) mixed(src, if (isSmall) aiSmall else ai, e.detail.aiDenoise) else src
            val tables = if (e.grade.isIdentity) null else e.grade.tables()
            val healNow = healMode.value
            val r = ImageOps.renderPhoto(s0, e.grade, e.detail, if (healNow) Geometry() else e.geometry, tables, e.lens, e.spots, if (healNow) null else e.masks, aiMasks.toMap(), if (healNow) null else e.bg)
            return if (healNow) r else (e.watermark?.let { stampLayer(context, r, it) } ?: r)
        }
        renderReq.trySend(edit)
        var next: PhotoEdit? = null
        while (true) {
            val e = next ?: renderReq.receive()
            next = null
            preview = withContext(Dispatchers.Default) { draw(small, e) }
            // Still dragging? Keep going with the newest value.
            val newer = kotlinx.coroutines.withTimeoutOrNull(140) { renderReq.receive() }
            if (newer != null) { next = newer; continue }
            val full = withContext(Dispatchers.Default) { draw(b, e) }
            preview = full
            hist = withContext(Dispatchers.Default) { ImageOps.histogram(full) }
        }
    }
    // "Before" for the split view: the original with the same crop, so both halves line up.
    LaunchedEffect(split, base, edit.geometry) {
        val b = base
        beforeImg = if (!split || b == null) null else withContext(Dispatchers.Default) { ImageOps.render(b, ProGrade(), Detail(), edit.geometry, null) }
    }
    // Undo history: record settled states.
    LaunchedEffect(edit) {
        delay(600)
        if (undo.getOrNull(undoPos) != edit) {
            while (undo.size > undoPos + 1) undo.removeAt(undo.lastIndex)
            undo.add(edit)
            if (undo.size > 50) undo.removeAt(0)
            undoPos = undo.lastIndex
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(2600); message = null } }

    fun export() {
        if (busy) return
        busy = true
        val e = edit
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    var full = GradeIO.decode(context, sourceUri(e), sizes[sizeIdx]) ?: return@withContext null
                    if (e.detail.aiDenoise > 0f) {
                        // Full-resolution AI noise reduction (progress badge; can be stopped).
                        val n = full.width * full.height
                        val px = IntArray(n).also { full.getPixels(it, 0, full.width, 0, 0, full.width, full.height) }
                        // Cloud AI (Topaz Denoise) when chosen in settings; otherwise / on failure the on-phone AI.
                        var den: IntArray? = null
                        if (com.dronedirector.acluxpro.camera.CloudAi.usePhotos(context)) {
                            try {
                                val jpeg = java.io.ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 96, it) }.toByteArray()
                                val res = com.dronedirector.acluxpro.camera.CloudAi.denoisePhoto(context, jpeg)
                                BitmapFactory.decodeByteArray(res, 0, res.size)?.let { b ->
                                    val sized = if (b.width == full.width && b.height == full.height) b else Bitmap.createScaledBitmap(b, full.width, full.height, true)
                                    den = IntArray(n).also { sized.getPixels(it, 0, full.width, 0, 0, full.width, full.height) }
                                }
                            } catch (t: com.dronedirector.acluxpro.camera.CloudAi.Stopped) { throw AiDenoiser.Stopped() } catch (_: Throwable) {}
                        }
                        if (den == null) den = AiDenoiser.denoise(context, px, full.width, full.height)
                        val dn = den
                        if (dn != null) {
                            val w0 = full.width; val h0 = full.height
                            full.recycle()
                            full = Bitmap.createBitmap(AiDenoiser.blend(px, dn, e.detail.aiDenoise), w0, h0, Bitmap.Config.ARGB_8888)
                        }
                    }
                    val tables = if (e.grade.isIdentity) null else e.grade.tables()
                    var out = ImageOps.renderPhoto(full, e.grade, e.detail, e.geometry, tables, e.lens, e.spots, e.masks, aiMasks.toMap(), e.bg)
                    full.recycle()
                    // AI upscale (×2 / ×4, at most 24 MP).
                    if (aiScale > 1) {
                        val k = minOf(aiScale.toFloat(), kotlin.math.sqrt(24e6f / (out.width.toFloat() * out.height)))
                        if (k > 1.05f) {
                            val up = com.dronedirector.acluxpro.camera.Upscaler.upscaleBitmap(context, out, (out.width * k).toInt() / 2 * 2, (out.height * k).toInt() / 2 * 2)
                                ?: throw AiDenoiser.Stopped()
                            out.recycle(); out = up
                        }
                    }
                    e.watermark?.let { out = stampLayer(context, out, it) }
                    // A removed background needs a format with transparency.
                    val fmt = if (e.bg.active && e.bg.mode == 2 && format != 1 && format != 2) 1 else format
                    savePhotoAs(context, out, fmt, (quality * 100).toInt(), if (keepExif) uri else null, stripGps)
                } catch (_: OutOfMemoryError) {
                    null
                } catch (t: Throwable) {
                    if (t is AiDenoiser.Stopped || t.cause is AiDenoiser.Stopped) "stopped" else null
                }
            }
            busy = false
            message = when {
                ok == "stopped" -> "Export stopped"
                ok != null -> "Saved"
                else -> "Couldn't export — try a smaller size"
            }
        }
    }

    // ---- AI selections (subject / sky), background and AI erase.
    val useCloud = com.dronedirector.acluxpro.camera.CloudAi.usePhotos(context)
    fun findMask(type: MaskType, done: (String) -> Unit) {
        val b = base ?: return
        message = if (type == MaskType.SKY) "Finding the sky…" else "Finding the subject…"
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val m = if (type == MaskType.SKY) AiSegment.sky(context, b, useCloud) else AiSegment.subject(context, b, useCloud)
                    m?.let { AiSegment.saveMask(context, it) }
                } catch (_: Throwable) { null }
            }
            if (path == null) message = if (type == MaskType.SKY) "Couldn't find a sky" else
                "Couldn't find a subject" + if (!useCloud && !AiSegment.hasModel(context)) " — turn on Cloud AI for photos" else ""
            else { message = null; done(path) }
        }
    }
    val aiMaskAction: ((MaskType) -> Unit)? = { type ->
        findMask(type) { path ->
            val pm = PhotoMask(type = type, maskFile = path)
            edit = edit.copy(masks = edit.masks + pm)
            tools.selected = pm.id
        }
    }
    var eraseMask by remember { mutableStateOf<PhotoMask?>(null) }
    fun runErase() {
        val em = eraseMask ?: return
        if (em.strokes.isEmpty() || busy) return
        busy = true
        val e = edit
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val full = GradeIO.decode(context, sourceUri(e), 2400) ?: return@withContext null
                    val wt = PhotoLocal.weights(em.copy(amount = 1f), full.width, full.height)
                    val mask = Bitmap.createBitmap(IntArray(wt.size) { if (wt[it] > 0.25f) -1 else 0xFF000000.toInt() }, full.width, full.height, Bitmap.Config.ARGB_8888)
                    var result: Bitmap? = null
                    if (useCloud) try {
                        val jpeg = java.io.ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
                        val png = java.io.ByteArrayOutputStream().also { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        val res = com.dronedirector.acluxpro.camera.CloudAi.erase(context, jpeg, png)
                        result = BitmapFactory.decodeByteArray(res, 0, res.size)?.let { if (it.width == full.width && it.height == full.height) it else Bitmap.createScaledBitmap(it, full.width, full.height, true) }
                    } catch (_: com.dronedirector.acluxpro.camera.CloudAi.Stopped) { return@withContext "stopped" } catch (_: Throwable) {}
                    if (result == null) result = AiSegment.fill(full, mask)
                    val dir = java.io.File(context.filesDir, "erased").apply { mkdirs() }
                    val f = java.io.File(dir, "erased_${System.currentTimeMillis()}.jpg")
                    f.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    f.path
                } catch (_: Throwable) { null }
            }
            busy = false
            when (path) {
                null -> message = "Couldn't erase"
                "stopped" -> message = "AI erase stopped"
                else -> { edit = edit.copy(baseFile = path); eraseMask = null; message = "Erased" + if (!useCloud) " (on the phone — Cloud AI gives better results on big objects)" else "" }
            }
        }
    }
    val aiEraseSection: @Composable () -> Unit = {
        SectionTitle("AI ERASE") { if (edit.baseFile != null) edit = edit.copy(baseFile = null) }
        if (eraseMask == null) {
            Text("Remove bigger things — a person, a sign, a power line: paint over them, then tap ERASE.", color = Pro.Dim, fontSize = 11.sp)
            Text(
                "PAINT WHAT TO REMOVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { eraseMask = PhotoMask(type = MaskType.BRUSH) }.padding(10.dp),
            )
            if (edit.baseFile != null) Text("The photo has AI-erased parts. Tap RESET above to go back to the original.", color = Pro.Dim, fontSize = 11.sp)
        } else {
            EdSlider("Brush size", tools.brushSize, 0.01f..0.3f, 0.08f, ::pct) { tools.brushSize = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (busy) "ERASING…" else "ERASE", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !busy) { runErase() }.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("CANCEL", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp), modifier = Modifier.clickable { eraseMask = null }.padding(8.dp))
            }
            Text(if (useCloud) "Uses Bria Eraser in the cloud (your fal.ai account)." else "Runs on the phone. For big objects, turn on Cloud AI for photos (Bria Eraser).", color = Pro.Dim, fontSize = 11.sp)
        }
    }
    val backgroundSection: @Composable () -> Unit = {
        val bg = edit.bg
        SectionTitle("BACKGROUND (AI)") { edit = edit.copy(bg = PhotoBackground()) }
        if (bg.maskFile == null) {
            Text("Finds the subject (people, pets, objects) so you can blur, remove or recolour everything behind it.", color = Pro.Dim, fontSize = 11.sp)
            Text(
                "FIND SUBJECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { findMask(MaskType.SUBJECT) { p -> edit = edit.copy(bg = edit.bg.copy(maskFile = p, mode = 1)) } }.padding(10.dp),
            )
        } else {
            Chips(listOf("Off", "Blur (portrait)", "Remove", "Colour"), bg.mode) { m -> edit = edit.copy(bg = bg.copy(mode = m)) }
            if (bg.mode == 1) EdSlider("Blur", bg.blur, 0.05f..1f, 0.5f, ::pct) { v -> edit = edit.copy(bg = bg.copy(blur = v)) }
            if (bg.mode == 2) Text("Exported as PNG (or WebP) with a transparent background.", color = Pro.Dim, fontSize = 11.sp)
            if (bg.mode == 3) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFFFFFFFF, 0xFF000000, 0xFF1E1E1E, 0xFFF2E8DA, 0xFF2D6CDF, 0xFF34C759, 0xFFFF7EB6, 0xFFFFB300).map { it.toInt() }.forEach { c ->
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).border(if (c == bg.color) 3.dp else 1.dp, if (c == bg.color) Pro.Amber else Pro.Line, CircleShape)
                        .clickable { edit = edit.copy(bg = bg.copy(color = c)) })
                }
            }
            Text("FIND SUBJECT AGAIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { findMask(MaskType.SUBJECT) { p -> edit = edit.copy(bg = edit.bg.copy(maskFile = p)) } }.padding(8.dp))
        }
    }

    /** Heal: tap a spot; the patch source is picked automatically. */
    fun addSpot(x: Float, y: Float) {
        val b = base ?: return
        val r = tools.healSize
        scope.launch {
            val (dx, dy) = withContext(Dispatchers.Default) { PhotoLocal.findSource(b, x, y, r) }
            edit = edit.copy(spots = edit.spots + Spot(x, y, r, dx, dy))
        }
    }

    /** Batch: the current look (and optionally crop, masks and watermark) applied to many photos. */
    var batchWithCrop by remember { mutableStateOf(false) }
    val batchPick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { list ->
        if (list.isEmpty() || busy) return@rememberLauncherForActivityResult
        busy = true
        val e = edit
        scope.launch {
            var done = 0
            for ((i, u) in list.withIndex()) {
                message = "Batch ${i + 1} / ${list.size}…"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val full = GradeIO.decode(context, u, sizes[sizeIdx]) ?: return@withContext false
                        val tables = if (e.grade.isIdentity) null else e.grade.tables()
                        var out = ImageOps.renderPhoto(full, e.grade, e.detail.copy(aiDenoise = 0f), if (batchWithCrop) e.geometry else Geometry(), tables, e.lens, null, if (batchWithCrop) e.masks.filter { it.maskFile == null } else null)
                        full.recycle()
                        if (batchWithCrop) e.watermark?.let { out = stampLayer(context, out, it) }
                        savePhotoAs(context, out, format, (quality * 100).toInt(), if (keepExif) u else null, stripGps) != null
                    } catch (_: Throwable) { false }
                }
                if (ok) done++
            }
            busy = false
            message = "Batch done: $done of ${list.size} photos saved"
        }
    }

    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        com.dronedirector.acluxpro.ui.AiBadgeHost(
            AiDenoiser.running, "AI NOISE REDUCTION", 1, AiDenoiser.progress, onStop = { AiDenoiser.stop() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        )
        com.dronedirector.acluxpro.ui.AiBadgeHost(
            com.dronedirector.acluxpro.camera.Upscaler.pending > 0, "AI UPSCALING", 1, com.dronedirector.acluxpro.camera.Upscaler.progress,
            onStop = { com.dronedirector.acluxpro.camera.Upscaler.cancelAll() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(22f),
        )
        com.dronedirector.acluxpro.ui.AiBadgeHost(
            com.dronedirector.acluxpro.camera.CloudAi.stage != null, com.dronedirector.acluxpro.camera.CloudAi.stage ?: "", 1,
            com.dronedirector.acluxpro.camera.CloudAi.progress, onStop = { com.dronedirector.acluxpro.camera.CloudAi.stop() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(21f),
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp
            val boxW = maxWidth.value; val boxH = maxHeight.value
            val previewBox = @Composable { mod: Modifier ->
                Box(
                    mod.pointerInput(split, panel) {
                        if (split) {
                            // Drag anywhere to move the before / after divider.
                            detectHorizontalDragGestures { ch, _ -> splitX = (ch.position.x / size.width).coerceIn(0.02f, 0.98f) }
                        } else if (panel != PhotoPanel.MASKS && panel != PhotoPanel.HEAL) {
                            detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false })
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = if (comparing) base else preview ?: base
                    val before = beforeImg
                    when {
                        failed -> Text("This file can't be opened.", color = Pro.Dim)
                        split && before != null && shown != null -> {
                            Image(before.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                            Image(
                                shown.asImageBitmap(), null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(6.dp).drawWithContent {
                                    clipRect(left = size.width * splitX) { this@drawWithContent.drawContent() }
                                },
                            )
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val x = size.width * splitX
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 3f)
                                drawCircle(Color.White, 14.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
                                drawCircle(Pro.Amber, 11.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
                            }
                            Text("BEFORE", style = Pro.Label.copy(color = Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp))
                            Text("AFTER", style = Pro.Label.copy(color = Color.White), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        shown != null -> Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                        else -> CircularProgressIndicator(color = Pro.Amber)
                    }
                    // Mask handles / brush, and heal taps, on top of the photo.
                    val shownNow = if (comparing) base else preview ?: base
                    if (!split && !comparing && shownNow != null) {
                        val selMask = edit.masks.firstOrNull { it.id == tools.selected }
                        if (panel == PhotoPanel.MASKS && selMask != null) MaskEditOverlay(selMask, shownNow.width, shownNow.height, tools, image = shownNow) { m ->
                            edit = edit.copy(masks = edit.masks.map { if (it.id == m.id) m else it })
                        }
                        val em = eraseMask
                        if (panel == PhotoPanel.HEAL && em != null) MaskEditOverlay(em, shownNow.width, shownNow.height, tools) { m -> eraseMask = m }
                        else if (panel == PhotoPanel.HEAL) HealOverlay(edit.spots, shownNow.width, shownNow.height, tools.healSize) { x, y -> addSpot(x, y) }
                    }
                    if (showScope && !comparing) Scope(hist, Modifier.align(Alignment.TopEnd).padding(8.dp).width(130.dp).height(64.dp))
                    if (showCompare || split) Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!split) Text(
                            if (comparing) "BEFORE" else "HOLD FOR BEFORE",
                            style = Pro.Label.copy(color = Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Text(
                            if (split) "SPLIT ✓" else "SPLIT",
                            style = Pro.Label.copy(color = if (split) Color.Black else Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (split) Pro.Amber else Color.Black.copy(alpha = 0.5f)).clickable { split = !split }.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Pro.Amber)
                            Text("Exporting…", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    message?.let {
                        Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            val controls = @Composable { mod: Modifier ->
                Column(mod.background(Ed.Panel)) {
                    PanelTabs(panel) { panel = it }
                    if (showCopyBar) CopyPasteBar(
                        video = false,
                        onCopy = {
                            val e = edit
                            EditClipboard.copy(context, CopiedSettings(fromVideo = false, grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark))
                            message = "Settings copied — open another photo or a video clip and tap PASTE SETTINGS"
                        },
                        onPaste = { parts, _ ->
                            val c = EditClipboard.current
                            if (c != null) {
                                var e = edit
                                if (PastePart.GRADE in parts) c.grade?.let { e = e.copy(grade = it) }
                                if (PastePart.DETAIL in parts) c.detail?.let { e = e.copy(detail = it) }
                                if (PastePart.CROP in parts) c.geometry?.let { e = e.copy(geometry = it) }
                                if (PastePart.WATERMARK in parts) c.watermark?.let { e = e.copy(watermark = it) }
                                edit = e
                                message = "Settings pasted"
                            }
                        },
                    )
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        when (panel) {
                            PhotoPanel.MASKS -> MasksPanel(edit.masks, tools, { edit = edit.copy(masks = it) }, onAi = aiMaskAction)
                            PhotoPanel.HEAL -> {
                                SectionTitle("HEAL / REMOVE SPOTS") { edit = edit.copy(spots = emptyList()) }
                                Text("Tap dust, blemishes or small objects on the photo. A matching patch from nearby is blended over each one (the whole photo is shown while you heal).", color = Pro.Dim, fontSize = 11.sp)
                                EdSlider("Size", tools.healSize, 0.005f..0.12f, 0.025f, ::pct) { tools.healSize = it }
                                Text("${edit.spots.size} spot(s) healed", style = Pro.Label)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("UNDO LAST", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { edit = edit.copy(spots = edit.spots.dropLast(1)) }.padding(8.dp))
                                    Text("CLEAR ALL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { edit = edit.copy(spots = emptyList()) }.padding(8.dp))
                                }
                                aiEraseSection()
                            }
                            PhotoPanel.LENS -> {
                                val l = edit.lens
                                SectionTitle("LENS CORRECTIONS") { edit = edit.copy(lens = LensFix()) }
                                EdSlider("Distortion", l.distortion) { v -> edit = edit.copy(lens = l.copy(distortion = v)) }
                                Text("+ straightens bulging lines from wide / ultra-wide lenses, − fixes pinched lines from telephotos.", color = Pro.Dim, fontSize = 11.sp)
                                EdSlider("Vignetting", l.vignette, 0f..1f, 0f, ::pct) { v -> edit = edit.copy(lens = l.copy(vignette = v)) }
                                EdSlider("Colour fringes", l.fringe) { v -> edit = edit.copy(lens = l.copy(fringe = v)) }
                                Text("Colour fringes lines up red and blue at the edges of the picture (purple / green outlines).", color = Pro.Dim, fontSize = 11.sp)
                            }
                            else -> PhotoPanelContent(
                                panel, edit, { edit = it },
                                exportState = ExportState(format, quality, sizeIdx),
                                onExportState = { f, q, si -> format = f; quality = q; sizeIdx = si },
                                onExport = { export() },
                                busy = busy,
                                onPickLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            )
                        }
                        if (panel == PhotoPanel.EFFECTS) backgroundSection()
                        if (panel == PhotoPanel.EXPORT) {
                            SectionTitle("AI UPSCALE")
                            Chips(listOf("Off", "AI 2×", "AI 4×"), when (aiScale) { 2 -> 1; 4 -> 2; else -> 0 }) { aiScale = listOf(0, 2, 4)[it] }
                            Text("Makes the exported photo bigger with the built-in AI (ESRGAN) — or Topaz in the cloud when Cloud AI is on. Up to 24 MP.", color = Pro.Dim, fontSize = 11.sp)
                            SectionTitle("CAMERA INFO")
                            Chips(listOf("Keep camera info", "Remove all"), if (keepExif) 0 else 1) { keepExif = it == 0 }
                            if (keepExif) Chips(listOf("Keep location", "Remove location"), if (stripGps) 1 else 0) { stripGps = it == 1 }
                            Text("Date, camera, lens and settings are copied into JPEG and WebP exports.", color = Pro.Dim, fontSize = 11.sp)
                            SectionTitle("BATCH")
                            Chips(listOf("Look only", "Look + crop + masks + watermark"), if (batchWithCrop) 1 else 0) { batchWithCrop = it == 1 }
                            Text(
                                if (busy) "WORKING…" else "BATCH EXPORT MANY PHOTOS…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised)
                                    .clickable(enabled = !busy) { batchPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(10.dp),
                            )
                            Text("Pick up to 50 photos: each gets this edit and is exported with the settings above.", color = Pro.Dim, fontSize = 11.sp)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close", enabled = !busy) { onClose() }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    BarIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = undoPos > 0) { undoPos--; edit = undo[undoPos] }
                    BarIcon(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = undoPos < undo.lastIndex) { undoPos++; edit = undo[undoPos] }
                    BarIcon(Icons.Filled.BarChart, "Scope", tint = if (showScope) Pro.Amber else Color.White) { com.dronedirector.acluxpro.ui.Panels.set(context, "pe.scope", !showScope) }
                    PhotoPanelsButton(
                        onTab = { panel = it },
                        onReset = {
                            listOf("pe_controls_w", "pe_controls_h").forEach { PaneSizes.set(context, it, -1f) }
                            paneVersion++
                            split = false
                            message = "Layout reset to original"
                        },
                    )
                    BarIcon(Icons.Filled.Settings, "Settings") { showSettings = true }
                    BarButton("LOAD", enabled = !busy) { showLoad = true }
                    BarButton("SAVE", enabled = !busy) { if (projectId == null) askName = true else saveProject(projectName) }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !busy) { panel = PhotoPanel.EXPORT; com.dronedirector.acluxpro.ui.Panels.set(context, "pe.controls", true) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                // Resizable panels: hold the divider, then drag. Double-tap the divider to reset.
                // With the tools panel hidden, the photo fills the screen; shown again, it returns to its saved size.
                if (!showControls) {
                    previewBox(Modifier.fillMaxWidth().weight(1f))
                } else if (wide) {
                    val pane = rememberPane("pe_controls_w", paneVersion)
                    val maxW = boxW
                    Row(Modifier.fillMaxSize()) {
                        previewBox(Modifier.weight(1f).fillMaxHeight())
                        ResizeHandle(false,
                            onDrag = { d -> pane.dp = ((if (pane.dp < 0f) 380f else pane.dp) - d).coerceIn(240f, maxW - 220f) },
                            onEnd = { pane.save() }, onReset = { pane.reset() })
                        controls(Modifier.width((if (pane.dp < 0f) 380f else pane.dp).dp).fillMaxHeight())
                    }
                } else {
                    val pane = rememberPane("pe_controls_h", paneVersion)
                    val maxH = boxH
                    previewBox(Modifier.fillMaxWidth().weight(1f))
                    ResizeHandle(true,
                        onDrag = { d -> pane.dp = ((if (pane.dp < 0f) 330f else pane.dp) - d).coerceIn(150f, maxH - 220f) },
                        onEnd = { pane.save() }, onReset = { pane.reset() })
                    controls(Modifier.fillMaxWidth().height((if (pane.dp < 0f) 330f else pane.dp).dp))
                }
            }
        }
    }
}

@Composable
fun TopIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PanelTabs(selected: PhotoPanel, onSelect: (PhotoPanel) -> Unit) {
    Chips(PhotoPanel.entries.map { it.label }, selected.ordinal) { onSelect(PhotoPanel.entries[it]) }
}

private data class ExportState(val format: Int, val quality: Float, val sizeIdx: Int)

@Composable
private fun PhotoPanelContent(
    panel: PhotoPanel,
    e: PhotoEdit,
    set: (PhotoEdit) -> Unit,
    exportState: ExportState,
    onExportState: (Int, Float, Int) -> Unit,
    onExport: () -> Unit,
    busy: Boolean,
    onPickLogo: () -> Unit = {},
) {
    val g = e.grade
    fun grade(f: (ProGrade) -> ProGrade) = set(e.copy(grade = f(g)))
    fun detail(f: (Detail) -> Detail) = set(e.copy(detail = f(e.detail)))
    fun geo(f: (Geometry) -> Geometry) = set(e.copy(geometry = f(e.geometry)))

    when (panel) {
        PhotoPanel.PRESETS -> {
            UserPresetSection({ g to e.detail }) { p -> set(e.copy(grade = p.grade, detail = p.detail.copy(aiDenoise = e.detail.aiDenoise))) }
            LutSection(g) { ng -> set(e.copy(grade = ng)) }
            SectionTitle("PROFILE / LOOK")
            LookStrip(g.look) { f -> grade { it.copy(look = f) } }
            if (g.look != Filter.NONE) EdSlider("Amount", g.lookAmount, 0f..1f, 1f, ::pct) { v -> grade { it.copy(lookAmount = v) } }
            SectionTitle("INPUT (LOG / FLAT PHOTOS)")
            Chips(InputColor.entries.map { it.label }, g.input.ordinal) { i -> grade { it.copy(input = InputColor.entries[i]) } }
            Text(g.input.detail, color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("EVERYTHING")
            Text(
                "RESET ALL EDITS", style = Pro.Label.copy(color = Pro.Red, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { set(PhotoEdit()) }.padding(8.dp),
            )
        }
        PhotoPanel.LIGHT -> {
            SectionTitle("LIGHT") { grade { it.copy(exposure = 0f, contrast = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f) } }
            EdSlider("Exposure", g.exposure, -3f..3f, format = ::ev) { v -> grade { it.copy(exposure = v) } }
            EdSlider("Contrast", g.contrast) { v -> grade { it.copy(contrast = v) } }
            EdSlider("Highlights", g.highlights) { v -> grade { it.copy(highlights = v) } }
            EdSlider("Shadows", g.shadows) { v -> grade { it.copy(shadows = v) } }
            EdSlider("Whites", g.whites) { v -> grade { it.copy(whites = v) } }
            EdSlider("Blacks", g.blacks) { v -> grade { it.copy(blacks = v) } }
        }
        PhotoPanel.CURVE -> {
            SectionTitle("PARAMETRIC TONE CURVE") { grade { it.copy(curveHighlights = 0f, curveLights = 0f, curveDarks = 0f, curveShadows = 0f) } }
            EdSlider("Highlights", g.curveHighlights) { v -> grade { it.copy(curveHighlights = v) } }
            EdSlider("Lights", g.curveLights) { v -> grade { it.copy(curveLights = v) } }
            EdSlider("Darks", g.curveDarks) { v -> grade { it.copy(curveDarks = v) } }
            EdSlider("Shadows", g.curveShadows) { v -> grade { it.copy(curveShadows = v) } }
            SectionTitle("CONTRAST PIVOT")
            EdSlider("Pivot", g.pivot, 0.2f..0.7f, 0.435f, ::pct) { v -> grade { it.copy(pivot = v) } }
        }
        PhotoPanel.COLOR -> {
            SectionTitle("WHITE BALANCE") { grade { it.copy(temperature = 0f, tint = 0f) } }
            EdSlider("Temperature", g.temperature) { v -> grade { it.copy(temperature = v) } }
            EdSlider("Tint", g.tint) { v -> grade { it.copy(tint = v) } }
            SectionTitle("PRESENCE") { grade { it.copy(vibrance = 0f, saturation = 0f) } }
            EdSlider("Vibrance", g.vibrance) { v -> grade { it.copy(vibrance = v) } }
            EdSlider("Saturation", g.saturation) { v -> grade { it.copy(saturation = v) } }
        }
        PhotoPanel.MIXER -> HslMixer(g.hsl) { list -> grade { it.copy(hsl = list) } }
        PhotoPanel.GRADING -> {
            SectionTitle("COLOR GRADING") { grade { it.copy(gradeShadows = Wheel(200f), gradeMids = Wheel(40f), gradeHighlights = Wheel(40f)) } }
            WheelControl("Shadows", g.gradeShadows, showLuma = false) { w -> grade { it.copy(gradeShadows = w) } }
            WheelControl("Midtones", g.gradeMids, showLuma = false) { w -> grade { it.copy(gradeMids = w) } }
            WheelControl("Highlights", g.gradeHighlights, showLuma = false) { w -> grade { it.copy(gradeHighlights = w) } }
        }
        PhotoPanel.DETAIL -> {
            SectionTitle("SHARPENING")
            EdSlider("Amount", e.detail.sharpen, 0f..1f, format = ::pct) { v -> detail { it.copy(sharpen = v) } }
            SectionTitle("AI NOISE REDUCTION")
            EdSlider("Strength", e.detail.aiDenoise, 0f..1f, format = { if (it <= 0f) "Off" else pct(it) }) { v -> detail { it.copy(aiDenoise = v) } }
            Text("Built-in AI (NAFNet, trained on real phone-camera noise). Runs on the phone; cleaning the full photo takes a little longer when exporting.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("NOISE REDUCTION")
            EdSlider("Luminance & colour", e.detail.denoise, 0f..1f, format = ::pct) { v -> detail { it.copy(denoise = v) } }
            SectionTitle("PRESENCE")
            EdSlider("Texture", e.detail.texture) { v -> detail { it.copy(texture = v) } }
            EdSlider("Clarity", e.detail.clarity) { v -> detail { it.copy(clarity = v) } }
            EdSlider("Dehaze", g.dehaze) { v -> grade { it.copy(dehaze = v) } }
        }
        PhotoPanel.EFFECTS -> {
            SectionTitle("EFFECTS")
            EdSlider("Vignette", e.detail.vignette) { v -> detail { it.copy(vignette = v) } }
            EdSlider("Grain", e.detail.grain, 0f..1f, format = ::pct) { v -> detail { it.copy(grain = v) } }
            EdSlider("Fade", g.fade, 0f..1f, format = ::pct) { v -> grade { it.copy(fade = v) } }
        }
        PhotoPanel.CROP -> {
            val geom = e.geometry
            SectionTitle("ASPECT") { geo { Geometry() } }
            val aspects = listOf(
                "Original" to 0f, "1:1" to 1f, "4:5" to 0.8f, "5:4" to 1.25f, "4:3" to 4f / 3f, "3:4" to 0.75f, "3:2" to 1.5f, "2:3" to 0.6667f,
                "16:9" to 16f / 9f, "9:16" to 9f / 16f, "21:9" to 21f / 9f, "2.39:1" to 2.39f, "5:7" to 5f / 7f,
            )
            Chips(aspects.map { it.first }, aspects.indexOfFirst { it.second == geom.aspect }.coerceAtLeast(0)) { i -> geo { it.copy(aspect = aspects[i].second) } }
            SectionTitle("ROTATE & FLIP")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Filled.RotateRight, "Rotate") { geo { it.copy(rotate90 = it.rotate90 + 1) } }
                SmallAction(Icons.Filled.Flip, "Flip H") { geo { it.copy(flipH = !it.flipH) } }
                SmallAction(Icons.Filled.Flip, "Flip V") { geo { it.copy(flipV = !it.flipV) } }
            }
            EdSlider("Straighten", geom.straighten, -45f..45f, format = { "%+.1f°".format(it) }) { v -> geo { it.copy(straighten = v) } }
            SectionTitle("PERSPECTIVE") { geo { it.copy(vertical = 0f, horizontal = 0f) } }
            EdSlider("Vertical", geom.vertical) { v -> geo { it.copy(vertical = v) } }
            Text("+ straightens buildings that lean in at the top (shot from below), − the opposite.", color = Pro.Dim, fontSize = 11.sp)
            EdSlider("Horizontal", geom.horizontal) { v -> geo { it.copy(horizontal = v) } }
            SectionTitle("FRAMING")
            EdSlider("Zoom", geom.zoom, 1f..4f, 1f, { "%.2fx".format(it) }) { v -> geo { it.copy(zoom = v) } }
            EdSlider("Horizontal", geom.offsetX) { v -> geo { it.copy(offsetX = v) } }
            EdSlider("Vertical", geom.offsetY) { v -> geo { it.copy(offsetY = v) } }
        }
        PhotoPanel.WATERMARK -> {
            val wm = e.watermark
            SectionTitle("WATERMARK") { set(e.copy(watermark = null)) }
            Chips(listOf("None", "Text", "Logo image"), when { wm == null -> 0; wm.kind == LayerKind.LOGO -> 2; else -> 1 }) { i ->
                when (i) {
                    0 -> set(e.copy(watermark = null))
                    1 -> set(e.copy(watermark = Layer(kind = LayerKind.WATERMARK, text = "© AcluxPro", size = 0.035f, x = 0.84f, y = 0.93f, opacity = 0.7f, font = LayerFont.LIGHT, wholeVideo = true)))
                    2 -> onPickLogo()
                }
            }
            if (wm != null) {
                if (wm.kind != LayerKind.LOGO) {
                    androidx.compose.material3.OutlinedTextField(
                        value = wm.text, onValueChange = { set(e.copy(watermark = wm.copy(text = it))) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text("Watermark text") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
                        ),
                    )
                    Chips(LayerFont.entries.map { it.label }, wm.font.ordinal) { set(e.copy(watermark = wm.copy(font = LayerFont.entries[it]))) }
                    Chips(listOf("White", "Black", "Accent"), when (wm.color) { 0xFF000000.toInt() -> 1; 0xFFFFFFFF.toInt() -> 0; else -> 2 }) {
                        val c = when (it) { 1 -> 0xFF000000.toInt(); 2 -> android.graphics.Color.argb(255, (Pro.Amber.red * 255).toInt(), (Pro.Amber.green * 255).toInt(), (Pro.Amber.blue * 255).toInt()); else -> 0xFFFFFFFF.toInt() }
                        set(e.copy(watermark = wm.copy(color = c)))
                    }
                }
                EdSlider("Size", wm.size, 0.015f..0.25f, 0.035f, ::pct) { set(e.copy(watermark = wm.copy(size = it))) }
                EdSlider("Opacity", wm.opacity, 0.05f..1f, 0.7f, ::pct) { set(e.copy(watermark = wm.copy(opacity = it))) }
                SectionTitle("POSITION")
                val spots = listOf(0.08f, 0.5f, 0.92f)
                spots.forEach { y ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        spots.forEach { x ->
                            val on = kotlin.math.abs(wm.x - x) < 0.1f && kotlin.math.abs(wm.y - y) < 0.1f
                            Box(Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Pro.Amber else Ed.Raised)
                                .clickable { set(e.copy(watermark = wm.copy(x = if (x < 0.5f) 0.16f else if (x > 0.5f) 0.84f else 0.5f, y = if (y < 0.5f) 0.07f else if (y > 0.5f) 0.93f else 0.5f))) })
                        }
                    }
                }
            }
        }
        PhotoPanel.MASKS, PhotoPanel.HEAL, PhotoPanel.LENS -> {}   // drawn by the workspace
        PhotoPanel.EXPORT -> {
            SectionTitle("FORMAT")
            Chips(PHOTO_FORMATS, exportState.format) { onExportState(it, exportState.quality, exportState.sizeIdx) }
            if (exportState.format == 3) Text("HEIC: about half the size of JPEG (Android 9+).", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format != 1) EdSlider("Quality", exportState.quality, 0.6f..1f, 0.95f, ::pct) { onExportState(exportState.format, it, exportState.sizeIdx) }
            SectionTitle("SIZE (LONG EDGE)")
            Chips(listOf("Full (≤4800)", "4096", "2560", "1920", "Instagram 1080", "Web 2048", "4K 3840"), exportState.sizeIdx) { onExportState(exportState.format, exportState.quality, it) }
            Text("Exports a new picture to DCIM/AcluxPro; the original stays untouched.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                if (busy) "EXPORTING…" else "EXPORT PHOTO",
                style = Pro.Value.copy(color = Color.Black, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (busy) Pro.Amber.copy(alpha = 0.4f) else Pro.Amber)
                    .clickable(enabled = !busy, onClick = onExport).padding(14.dp),
            )
        }
    }
}

@Composable
fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun HslMixer(bands: List<HslBand>, onChange: (List<HslBand>) -> Unit) {
    var sel by remember { mutableStateOf(0) }
    SectionTitle("HSL / COLOR MIXER") { onChange(List(8) { HslBand() }) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        HSL_CENTERS.forEachIndexed { i, h ->
            val (r, g, b) = hueRgb(h)
            Box(
                Modifier.size(if (i == sel) 32.dp else 26.dp).clip(CircleShape).background(Color(r, g, b))
                    .clickable { sel = i },
                contentAlignment = Alignment.Center,
            ) {
                if (bands[i] != HslBand()) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
    Text(HSL_NAMES[sel].uppercase(), style = Pro.Label.copy(color = Color.White, fontSize = 11.sp))
    val b = bands[sel]
    fun upd(nb: HslBand) = onChange(bands.toMutableList().also { it[sel] = nb })
    EdSlider("Hue", b.hue) { upd(b.copy(hue = it)) }
    EdSlider("Saturation", b.sat) { upd(b.copy(sat = it)) }
    EdSlider("Luminance", b.lum) { upd(b.copy(lum = it)) }
}

/** Saves an edited picture as a new file in DCIM/AcluxPro. */
fun savePicture(context: Context, bmp: Bitmap, png: Boolean, quality: Int): Uri? {
    val name = "EDIT_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + if (png) ".png" else ".jpg"
    // A folder chosen in the editor settings wins; otherwise the default app location.
    SaveFolder.write(context, false, name, if (png) "image/png" else "image/jpeg") {
        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
    }?.let { return it }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val target = resolver.insert(Storage.imagesUri(Storage.load(context)), values) ?: return null
    return try {
        resolver.openOutputStream(target)?.use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
        }
        if (Build.VERSION.SDK_INT >= 29) resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        target
    } catch (_: Throwable) {
        resolver.delete(target, null, null)
        null
    }
}

/** Slightly smaller top-bar icon so the photo editor's bar fits phones. */
@Composable
private fun BarIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(21.dp))
    }
}

/** Panels menu for the photo editor: the photo always stays; the rest can be hidden. */
@Composable
private fun PhotoPanelsButton(onTab: (PhotoPanel) -> Unit, onReset: () -> Unit) {
    val panels = listOf(
        com.dronedirector.acluxpro.ui.PanelDef("pe.controls", "Tools panel", "Presets, Light, Color, Masks, Heal, Export and the other tabs"),
        com.dronedirector.acluxpro.ui.PanelDef("pe.copypaste", "Copy / paste bar", "Copy settings to other photos or video clips"),
        com.dronedirector.acluxpro.ui.PanelDef("pe.scope", "Histogram", "RGB histogram on the photo"),
        com.dronedirector.acluxpro.ui.PanelDef("pe.compare", "Before / after buttons", "HOLD FOR BEFORE and SPLIT on the photo"),
    )
    val all = panels.map { it.key }.toSet()
    com.dronedirector.acluxpro.ui.PanelsButton(
        prefix = "pe",
        panels = panels,
        presets = listOf(
            com.dronedirector.acluxpro.ui.PanelPreset("Full", all),
            com.dronedirector.acluxpro.ui.PanelPreset("View", emptySet()),
            com.dronedirector.acluxpro.ui.PanelPreset("Edit", setOf("pe.controls", "pe.compare")) { onTab(PhotoPanel.LIGHT) },
            com.dronedirector.acluxpro.ui.PanelPreset("Color", setOf("pe.controls", "pe.scope", "pe.compare")) { onTab(PhotoPanel.COLOR) },
        ),
        onReset = onReset,
    )
}
