package com.dronedirector.veyrocutpro.editor

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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.mouseActions
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
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.camera.MediaLibrary
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.ui.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dronedirector.veyrocutpro.ui.hintedScroll
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
    /** Text and sticker layers drawn on the photo (Text tab). */
    val texts: List<Layer> = emptyList(),
    val sky: SkySwap = SkySwap(),
    /** RAW (DNG) develop choices: profile and white balance preset. */
    val raw: RawDng.Dev = RawDng.Dev(),
    /** Lens Blur from the AI depth map. */
    val lensBlur: LensBlur? = null,
)

/** RAW develop does exposure, white balance and the tonal ranges itself; the 8-bit grade skips them. */
private fun ProGrade.rawRest() = copy(exposure = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f, temperature = 0f, tint = 0f)

private enum class PhotoPanel(val label: String) {
    PRESETS("Presets"), LIGHT("Light"), CURVE("Curve"), COLOR("Color"), MIXER("HSL Mixer"),
    GRADING("Grading"), DETAIL("Detail"), EFFECTS("Effects"), MASKS("Masks"), HEAL("Heal"), LENS("Lens"),
    CROP("Crop & perspective"), WATERMARK("Watermark"), TEXT("Text"), HISTORY("History"), EXPORT("Export"),
}

@Composable
fun PhotoEditorScreen(onExit: () -> Unit, initial: Uri? = null) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(initial) }
    var project by remember { mutableStateOf<PhotoProjectData?>(null) }

    // Filmstrip: photos picked together stay in a working set, each with its own edit.
    LaunchedEffect(Unit) { PhotoSession.load(context) }
    fun openSession(i: Int) {
        val sp = PhotoSession.items.getOrNull(i) ?: return
        PhotoSession.current = i
        project = sp.projectId?.let { ProjectStore.loadPhoto(context, it) }
        source = Uri.parse(sp.uri)
    }
    fun addMany(list: List<Uri>) {
        if (list.isEmpty()) return
        val first = PhotoSession.add(context, list)
        // From inside the editor (+ on the filmstrip) the photos are only added; the current edit stays open.
        if (first >= 0 && (source == null || source == Uri.EMPTY)) openSession(first)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { addMany(it) }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addMany(it) }
    // Merge several photos (HDR, panorama, focus stack) into a new one, then edit it.
    val scope = rememberCoroutineScope()
    var mergeKind by remember { mutableStateOf<MergeKind?>(null) }
    var merging by remember { mutableStateOf<String?>(null) }
    val mergePick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { list ->
        val kind = mergeKind ?: return@rememberLauncherForActivityResult
        if (list.size < 2) { if (list.isNotEmpty()) android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Pick at least 2 photos"), android.widget.Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
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
            else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't merge those photos"), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val src = source
    var showLibrary by remember { mutableStateOf(false) }
    if (src == null && showLibrary) {
        LibraryScreen(
            onBack = { showLibrary = false },
            onEdit = { list -> showLibrary = false; val first = PhotoSession.add(context, list); if (first >= 0) openSession(first) },
            onVirtualCopy = { u -> showLibrary = false; openSession(PhotoSession.addCopy(context, u)) },
        )
        return
    }
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
        // Panorama: choose the projection and how to handle the uneven edges first.
        var panoAsk by remember { mutableStateOf(false) }
        if (panoAsk) {
            var proj by remember { mutableStateOf(PanoOpts.projection) }
            var warp by remember { mutableStateOf(PanoOpts.warp) }
            var fill by remember { mutableStateOf(PanoOpts.fill) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { panoAsk = false }, containerColor = Pro.PanelSolid,
                title = { Text("Panorama", color = Color.White) },
                text = {
                    Column {
                        Text("Projection", color = Color.White, fontSize = 13.sp)
                        Chips(PanoOpts.PROJECTIONS, proj) { proj = it }
                        Text(listOf("Best for wide, sweeping panoramas.", "Very wide or tall views (360° style); straight lines curve.", "Keeps straight lines straight (narrower panoramas, buildings).")[proj], color = Pro.Dim, fontSize = 11.sp)
                        EdSlider("Boundary warp", warp, 0f..1f, 0f, ::pct) { warp = it }
                        Text("Stretches the panorama to fill the frame instead of cropping away its uneven edges.", color = Pro.Dim, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Fill edges (AI)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(fill, { fill = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                        }
                        Text("Paints in the empty corners instead of cropping them.", color = Pro.Dim, fontSize = 11.sp)
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = {
                    PanoOpts.projection = proj; PanoOpts.warp = warp; PanoOpts.fill = fill; panoAsk = false
                    mergeKind = MergeKind.PANORAMA; mergePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Pick photos", color = Pro.Amber) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { panoAsk = false }) { Text("Cancel", color = Pro.Dim) } },
            )
        }
        MediaChooser(
            onMerge = { k -> if (k == MergeKind.PANORAMA) panoAsk = true else { mergeKind = k; mergePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            title = "Photo editor",
            subtitle = "Choose a photo to edit. The original is never changed; edits are exported as a new picture.",
            videos = false,
            onBack = onExit,
            onGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFiles = { browse.launch(arrayOf("image/*")) },
            onRecent = { project = null; addMany(listOf(it)) },
            onSkip = { project = null; PhotoSession.current = -1; source = Uri.EMPTY },
            banner = {
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ed.Raised)
                        .clickable { showLibrary = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Pro.Amber)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Library", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("All your photos: stars, flags, albums, keywords, AI search, compare", color = Pro.Dim, fontSize = 11.sp)
                    }
                    Text("→", color = Pro.Amber, fontSize = 20.sp)
                }
                if (PhotoSession.items.isNotEmpty()) {
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ed.Raised)
                        .clickable { openSession(PhotoSession.current.coerceIn(0, PhotoSession.items.lastIndex)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue filmstrip (${PhotoSession.items.size} photos)", color = Pro.Amber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Each photo keeps its own edit.", color = Pro.Dim, fontSize = 11.sp)
                    }
                    Text("Clear", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { PhotoSession.clear(context) }.padding(8.dp))
                }
                }
            },
            onOpenProject = { id ->
                val p = ProjectStore.loadPhoto(context, id)
                if (p != null && p.uri != Uri.EMPTY) { PhotoSession.current = -1; project = p; source = p.uri }
                else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open that project"), android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    } else {
        androidx.compose.runtime.key(src, project?.id) {
            PhotoWorkspace(src, project, onClose = { source = null; project = null },
                onOpenSession = { openSession(it) },
                onAddGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onAddFiles = { browse.launch(arrayOf("image/*")) },
                onLoad = { id ->
                val p = ProjectStore.loadPhoto(context, id)
                if (p != null && p.uri != Uri.EMPTY) { PhotoSession.current = -1; project = p; source = p.uri }
                else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open that project"), android.widget.Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** Start page of an editor: pick from gallery, browse files, or recent VeyroCut Pro media. */
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
    /** Shown only when set: go straight into the editor without choosing anything first. */
    onSkip: (() -> Unit)? = null,
    /** Optional card under the subtitle (e.g. recover an unsaved edit). */
    banner: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    if (settings) EditorSettingsDialog(title, if (videos) com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR else com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR) { settings = false }
    val recent by produceState<List<com.dronedirector.veyrocutpro.camera.MediaItem>>(emptyList()) {
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
        banner?.invoke()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(Icons.Filled.PhotoLibrary, if (videos) "Gallery videos" else "Gallery photos", Modifier.weight(1f), onGallery)
            BigButton(Icons.Filled.FolderOpen, "Browse files", Modifier.weight(1f), onFiles)
        }
        if (onSkip != null) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Pro.Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Skip — open the editor", color = Pro.Amber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (videos) "Start with an empty timeline and add clips later from the Media page." else "Open the editor now and add a photo from inside it.", color = Pro.Dim, fontSize = 11.sp)
                }
                Text("→", color = Pro.Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
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
        Text("RECENT VEYROCUT PRO ${if (videos) "VIDEOS" else "PHOTOS"}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
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
                        FormatTag(item, Modifier.align(Alignment.BottomStart).padding(4.dp))
                    }
                }
            }
        }
    }
}

/** File type for the browse thumbnails: "DNG RAW", "JPEG", "PNG", "HEIC" / "MP4", "MOV", "MKV"… */
internal fun formatLabel(name: String, mime: String, raw: Boolean): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    val t = ext.ifEmpty { mime.substringAfter('/', "").lowercase() }
    return when {
        t == "dng" || (raw && t.isEmpty()) -> "DNG RAW"
        raw -> "${t.uppercase()} RAW"
        t == "jpg" || t == "jpeg" -> "JPEG"
        t == "heif" -> "HEIF"
        t == "x-adobe-dng" -> "DNG RAW"
        t == "quicktime" -> "MOV"
        t == "x-matroska" -> "MKV"
        t == "3gpp" -> "3GP"
        t == "x-msvideo" -> "AVI"
        t == "mp2ts" || t == "mts" || t == "m2ts" -> "MTS"
        t.isEmpty() -> if (mime.startsWith("video")) "VIDEO" else "IMAGE"
        else -> t.uppercase().take(5)
    }
}

/** Small, readable file-type tag on a browse thumbnail (not shown inside the editor). */
@Composable
private fun FormatTag(item: com.dronedirector.veyrocutpro.camera.MediaItem, modifier: Modifier = Modifier) {
    val label = formatLabel(item.name, item.mime, item.isRaw && !item.isVideo)
    val raw = item.isRaw && !item.isVideo
    Text(
        label,
        color = if (raw) Color.Black else Color.White,
        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(4.dp))
            .background(if (raw) Pro.Amber else Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
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
private fun PhotoWorkspace(
    uri: Uri, initial: PhotoProjectData?, onClose: () -> Unit, onLoad: (String) -> Unit,
    /** Opened with SKIP (no photo yet, uri = Uri.EMPTY): pick one from the gallery / files. */
    onAddGallery: () -> Unit = {}, onAddFiles: () -> Unit = {},
    /** Switch to another photo of the filmstrip (index in [PhotoSession.items]). */
    onOpenSession: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var edit by remember {
        mutableStateOf(
            @Suppress("USELESS_ELVIS")
            if (initial != null) PhotoEdit(initial.grade, initial.detail, initial.geometry, initial.watermark, initial.masks ?: emptyList(), initial.spots ?: emptyList(), initial.lens ?: LensFix(), initial.bg ?: PhotoBackground(), initial.baseFile, initial.texts ?: emptyList(), initial.sky ?: SkySwap(), initial.raw ?: RawDng.Dev(), initial.lensBlur)
            else PhotoEdit(watermark = WatermarkMemory.load(context, false))
        )
    }
    // A filmstrip photo's hidden edit is not a named project: SAVE makes a separate one.
    var projectId by remember { mutableStateOf(initial?.id?.takeIf { initial.session != true && initial.autosave != true }) }
    var projectName by remember { mutableStateOf(initial?.name ?: "") }
    var askName by remember { mutableStateOf(false) }
    var showLoad by remember { mutableStateOf(false) }
    if (showLoad) LoadProjectDialog(false, onOpen = { showLoad = false; onLoad(it) }, onDismiss = { showLoad = false })
    var panel by remember { mutableStateOf(PhotoPanel.PRESETS) }
    var comparing by remember { mutableStateOf(false) }
    // Develop tools: eyedropper (1 = white balance, 2 = Point Color), clipping warning, sharpening mask, zoom / 1:1 loupe.
    var picking by remember { mutableStateOf(0) }
    var pendingObject by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var dustView by remember { mutableStateOf(false) }
    var dustSens by remember { mutableStateOf(0.5f) }
    var dustImg by remember { mutableStateOf<Bitmap?>(null) }
    var proofImg by remember { mutableStateOf<Bitmap?>(null) }
    var uprightBusy by remember { mutableStateOf(false) }
    var clipWarn by remember { mutableStateOf(false) }
    var sharpMaskView by remember { mutableStateOf(false) }
    var zoomK by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var clipImg by remember { mutableStateOf<Bitmap?>(null) }
    var sharpImg by remember { mutableStateOf<Bitmap?>(null) }
    var loupe by remember { mutableStateOf<Bitmap?>(null) }
    // Before / after split view with a draggable divider.
    var split by remember { mutableStateOf(false) }
    var splitX by remember { mutableStateOf(0.5f) }
    var beforeImg by remember { mutableStateOf<Bitmap?>(null) }
    // Panels menu (layout icon next to the settings gear): which parts of the editor are shown.
    val showScope = com.dronedirector.veyrocutpro.ui.panelOn("pe.scope")
    val showControls = com.dronedirector.veyrocutpro.ui.panelOn("pe.controls")
    val showCopyBar = com.dronedirector.veyrocutpro.ui.panelOn("pe.copypaste")
    val showCompare = com.dronedirector.veyrocutpro.ui.panelOn("pe.compare")
    var paneVersion by remember { mutableStateOf(0) }
    var hist by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportT0 by remember { mutableStateOf(0L) }
    var exportEstMs by remember { mutableStateOf(0L) }
    var batchN by remember { mutableStateOf(0) }
    var batchI by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) EditorSettingsDialog("Photo editor", com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR) { showSettings = false }
    val undo = remember { mutableStateListOf(edit) }
    val wmOn = WatermarkMemory.isOn(context, false)
    LaunchedEffect(edit.watermark, wmOn) { if (wmOn && edit.watermark != null) WatermarkMemory.save(context, false, edit.watermark) }
    var undoPos by remember { mutableStateOf(0) }
    // Snapshots: named versions of this edit you can jump between (History panel).
    val snapshots = remember { mutableStateListOf<Pair<String, PhotoEdit>>() }

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
    LaunchedEffect((edit.masks.flatMap { m -> listOfNotNull(m.maskFile) + m.subs.orEmpty().mapNotNull { it.maskFile } } + listOfNotNull(edit.bg.maskFile, edit.sky.maskFile, edit.sky.imagePath, edit.lensBlur?.depthFile))) {
        for (f in (edit.masks.flatMap { m -> listOfNotNull(m.maskFile) + m.subs.orEmpty().mapNotNull { it.maskFile } } + listOfNotNull(edit.bg.maskFile, edit.sky.maskFile, edit.sky.imagePath, edit.lensBlur?.depthFile))) if (aiMasks[f] == null) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f) }?.let { aiMasks[f] = it }
    }
    fun saveProject(name: String) {
        val e = edit
        val p = PhotoProjectData(
            id = projectId ?: java.util.UUID.randomUUID().toString(), name = name, uri = uri,
            grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
            format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile, texts = e.texts, sky = e.sky, raw = e.raw, lensBlur = e.lensBlur,
        )
        val thumb = preview
        scope.launch {
            val saved = withContext(Dispatchers.IO) { try { ProjectStore.savePhoto(context, p, thumb) } catch (_: Throwable) { null } }
            if (saved != null) { projectId = saved.id; projectName = saved.name; message = "Project \"${saved.name}\" saved" }
            else message = "Couldn't save the project"
        }
    }
    if (askName) ProjectNameDialog(ProjectStore.defaultName(false), onSave = { askName = false; saveProject(it) }, onDismiss = { askName = false })
    // Timed autosave (gear → AUTOSAVE): a full project copy every few minutes when something changed.
    val asMinutes = remember(AutoSave.changes) { AutoSave.minutes(context, false) }
    LaunchedEffect(asMinutes, uri) {
        if (asMinutes <= 0 || uri == Uri.EMPTY) return@LaunchedEffect
        var last: PhotoEdit? = initial?.let { null }
        while (true) {
            delay(asMinutes * 60_000L)
            val e = edit
            if (e == last || undo.size <= 1) continue
            last = e
            val p = PhotoProjectData(
                name = AutoSave.nameFor(projectName.ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "" }), uri = uri,
                grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
                format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile, texts = e.texts, sky = e.sky, raw = e.raw, lensBlur = e.lensBlur,
            )
            val thumb = preview
            withContext(Dispatchers.IO) { try { AutoSave.savePhoto(context, p, thumb) } catch (_: Throwable) {} }
        }
    }
    val sizes = listOf(4800, 4096, 2560, 1920, 1080, 2048, 3840)

    // ---- Filmstrip: this photo's edit is kept automatically before switching or leaving.
    val sessionIdx = PhotoSession.current.takeIf { i -> PhotoSession.items.getOrNull(i)?.uri == uri.toString() } ?: -1
    fun sessionSave() {
        val sp = PhotoSession.items.getOrNull(sessionIdx) ?: return
        val changed = undo.size > 1 || sp.edited
        if (!changed) return
        val e = edit
        val p = PhotoProjectData(
            id = sp.projectId ?: java.util.UUID.randomUUID().toString(), name = sp.name, uri = uri,
            grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
            format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile, texts = e.texts, sky = e.sky, raw = e.raw, lensBlur = e.lensBlur,
            session = true,
        )
        try {
            val saved = ProjectStore.savePhoto(context, p, preview)
            PhotoSession.update(context, sessionIdx) { it.copy(projectId = saved.id, edited = true, version = System.currentTimeMillis()) }
        } catch (_: Throwable) {}
    }
    fun switchTo(i: Int) { if (busy) return; sessionSave(); onOpenSession(i) }
    fun closeEditor() { sessionSave(); onClose() }

    BackHandler { if (!busy) closeEditor() }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) edit = edit.copy(watermark = Layer(kind = LayerKind.LOGO, imageUri = u, size = 0.1f, x = 0.86f, y = 0.9f, opacity = 0.85f, wholeVideo = true))
    }

    // The photo being edited: the original, or its AI-erased copy.
    fun sourceUri(e: PhotoEdit): Uri = e.baseFile?.let { Uri.fromFile(java.io.File(it)) } ?: uri
    LaunchedEffect(clipWarn, preview) { val pv = preview; clipImg = if (clipWarn && pv != null) withContext(Dispatchers.Default) { Develop.clipOverlay(pv) } else null }
    LaunchedEffect(dustView, dustSens, preview, panel) { val pv = preview; dustImg = if (dustView && panel == PhotoPanel.HEAL && pv != null) withContext(Dispatchers.Default) { Upright.visualise(pv, dustSens) } else null }
    LaunchedEffect(ExportOpts.proof, preview) { val pv = preview; proofImg = if (ExportOpts.proof && pv != null) withContext(Dispatchers.Default) { ExportOpts.proofView(pv) } else null }
    LaunchedEffect(Unit) { ExportOpts.load(context) }
    LaunchedEffect(sharpMaskView, preview, edit.detail.sharpenMasking) { val pv = preview; sharpImg = if (sharpMaskView && pv != null) withContext(Dispatchers.Default) { Develop.sharpenMaskPreview(pv, edit.detail) } else null }
    fun resetZoom() { zoomK = 1f; panX = 0f; panY = 0f }
    LaunchedEffect(panel, split) { if (panel == PhotoPanel.MASKS || panel == PhotoPanel.HEAL || split) resetZoom(); if (panel != PhotoPanel.COLOR && panel != PhotoPanel.MASKS && panel != PhotoPanel.EFFECTS) picking = 0 }
    /** A tap on the photo (box coordinates) → pixel of the shown picture, undoing padding, fit and zoom. */
    fun tapToPixel(o: androidx.compose.ui.geometry.Offset, img: Bitmap, padPx: Float): Pair<Int, Int>? {
        val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat()
        if (bw < 2f || bh < 2f) return null
        val lx = o.x - padPx; val ly = o.y - padPx
        val ux = (lx - bw / 2f - panX) / zoomK + bw / 2f; val uy = (ly - bh / 2f - panY) / zoomK + bh / 2f
        val sc = minOf(bw / img.width, bh / img.height)
        val ox = (bw - img.width * sc) / 2f; val oy = (bh - img.height * sc) / 2f
        val px = ((ux - ox) / sc).toInt(); val py = ((uy - oy) / sc).toInt()
        return if (px in 0 until img.width && py in 0 until img.height) px to py else null
    }
    fun pickAt(o: androidx.compose.ui.geometry.Offset, padPx: Float) {
        val img = preview ?: return
        val (px, py) = tapToPixel(o, img, padPx) ?: return
        val c = Develop.sample(img, px, py, (img.width / 300).coerceAtLeast(2))
        if (picking == 1) {
            val (t, ti) = Develop.wbFromSample(c[0], c[1], c[2], edit.grade.temperature, edit.grade.tint)
            edit = edit.copy(grade = edit.grade.copy(temperature = t, tint = ti)); message = "White balance set from the picked spot"
        } else if (picking == 3) {
            // Masks are made on the uncropped photo; with no crop the tap maps straight across.
            pendingObject = px.toFloat() / img.width to py.toFloat() / img.height
            if (!edit.geometry.isIdentity) message = "Tip: the object pick works best before cropping"
        } else if (picking == 4) {
            val lb = edit.lensBlur
            val d = lb?.depthFile?.let { aiMasks[it] }
            if (lb != null && d != null) { edit = edit.copy(lensBlur = lb.copy(focus = LensBlurFx.depthAt(d, px.toFloat() / img.width, py.toFloat() / img.height))); message = "Focus set" }
        } else if (picking == 2) {
            val hsv = FloatArray(3); android.graphics.Color.RGBToHSV(c[0].toInt(), c[1].toInt(), c[2].toInt(), hsv)
            edit = edit.copy(grade = edit.grade.copy(pointColors = (edit.grade.pointColors ?: emptyList()) + PointColor(hsv[0], hsv[1], hsv[2])))
            message = "Colour picked — adjust it in POINT COLOR"
        }
        picking = 0
    }
    // .xmp presets / sidecars: import (as your presets, the first one is applied) and export.
    val pickXmp = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        var n = 0; var first: Pair<ProGrade, Detail>? = null
        for (u in list) {
            val t = DevPresets.readText(context, u) ?: continue
            val res = DevPresets.parseXmp(t) ?: continue
            val name = DevPresets.nameOf(t, u.lastPathSegment?.substringAfterLast('/') ?: "Preset")
            UserPresets.init(context); UserPresets.add(name, res.first, res.second); n++
            if (first == null) first = DevPresets.parseXmp(t, edit.grade, edit.detail)
        }
        first?.let { (g, d) -> edit = edit.copy(grade = g, detail = d.copy(aiDenoise = edit.detail.aiDenoise)) }
        message = if (n == 0) "No develop settings found in that file" else "Imported $n preset" + (if (n == 1) "" else "s") + " (in MY PRESETS); the first one is applied"
    }
    fun exportXmp() {
        val name = projectName.ifBlank { "VeyroCut look " + java.text.SimpleDateFormat("d MMM HH.mm", java.util.Locale.getDefault()).format(java.util.Date()) }
        val e = edit
        scope.launch {
            val where = withContext(Dispatchers.IO) { DevPresets.savePreset(context, name, DevPresets.presetXmp(name, e.grade, e.detail)) }
            message = where?.let { "Preset saved: $it" } ?: "Couldn't save the preset"
        }
    }
    // RAW (DNG): the sensor data, developed by the editor itself (see RawDng). Null for other photos.
    var rawMeta by remember { mutableStateOf<RawDng.Meta?>(null) }
    var rawLin by remember { mutableStateOf<RawDng.Linear?>(null) }
    LaunchedEffect(uri, edit.baseFile) {
        if (uri == Uri.EMPTY && edit.baseFile == null) return@LaunchedEffect   // empty editor (SKIP)
        val src = sourceUri(edit)
        var note: String? = null
        val r = if (edit.baseFile != null) null else withContext(Dispatchers.IO) {
            try {
                if (RawDng.isDng(context, src)) RawDng.open(context, src).let { m -> m to RawDng.preview(context, m, 1400) } else null
            } catch (t: RawDng.UnsupportedRaw) { note = "${t.message} — opened as a normal photo"; null }
            catch (_: OutOfMemoryError) { note = "This RAW is too big for the phone's memory — opened as a normal photo"; null }
            catch (_: Throwable) { null }
        }
        rawMeta = r?.first; rawLin = r?.second
        note?.let { message = it }
        val b = if (r != null) withContext(Dispatchers.Default) { RawDng.develop(r.second, r.first, RawDng.Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f) }
            else withContext(Dispatchers.IO) { GradeIO.decode(context, src, 1400) }
        if (b == null) failed = true else base = b
    }
    /** True while the edit works on the RAW data (not after AI erase, which makes a normal copy). */
    fun rawOn(e: PhotoEdit) = rawMeta != null && rawLin != null && e.baseFile == null
    // 1:1 loupe: when zoomed in, a sharper render of the whole photo (up to 3600 px) replaces the preview.
    var loupeSrc by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    LaunchedEffect(zoomK > 1.6f, edit, aiMasks.size) {
        if (zoomK <= 1.6f) { loupe = null; return@LaunchedEffect }
        delay(500)
        val e = edit
        if (rawOn(e)) return@LaunchedEffect
        val key = sourceUri(e).toString()
        loupe = withContext(Dispatchers.Default) {
            try {
                val hi = loupeSrc?.takeIf { it.first == key }?.second ?: (GradeIO.decode(context, sourceUri(e), 3600) ?: return@withContext null).also { loupeSrc = key to it }
                val tables = if (e.grade.isIdentity) null else e.grade.tables()
                var o = ImageOps.renderPhoto(hi, e.grade, e.detail.copy(aiDenoise = 0f), e.geometry, tables, e.lens, e.spots, e.masks, aiMasks.toMap(), e.bg, e.sky, e.lensBlur)
                for (t in e.texts) o = stampLayer(context, o, t)
                e.watermark?.let { o = stampLayer(context, o, it) }
                o
            } catch (_: OutOfMemoryError) { null } catch (_: Throwable) { null }
        }
    }
    /** Developed RAW at full quality for export / AI steps; [sliders] false = neutral (they're applied later in 8-bit). */
    fun rawExport(e: PhotoEdit, maxPx: Int, sliders: Boolean = true): Bitmap {
        val g = e.grade
        return if (sliders) RawDng.export(context, rawMeta!!, e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, maxPx)
        else RawDng.export(context, rawMeta!!, e.raw, 0f, 0f, 0f, 0f, 0f, 0f, 0f, maxPx)
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
    LaunchedEffect(base, aiBase, rawLin) {
        val b = base ?: return@LaunchedEffect
        val ai = aiBase
        val rl = rawLin; val rm = rawMeta
        val rawSmall = rl?.let { withContext(Dispatchers.Default) { RawDng.shrink(it, 640) } }
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
        // RAW + AI noise reduction: the AI cleaned the neutral develop once; its noise-free difference is reused.
        fun residual(dev: Bitmap, neutral: Bitmap, aiSrc: Bitmap?, t: Float): Bitmap {
            if (aiSrc == null || t <= 0f || aiSrc.width != neutral.width || aiSrc.height != neutral.height || dev.width != neutral.width || dev.height != neutral.height) return dev
            val n = dev.width * dev.height
            val d = IntArray(n).also { dev.getPixels(it, 0, dev.width, 0, 0, dev.width, dev.height) }
            val a = IntArray(n).also { neutral.getPixels(it, 0, dev.width, 0, 0, dev.width, dev.height) }
            val c = IntArray(n).also { aiSrc.getPixels(it, 0, dev.width, 0, 0, dev.width, dev.height) }
            for (i in 0 until n) {
                fun ch(sh: Int): Int { val v = (d[i] shr sh) and 0xFF; val nz = ((a[i] shr sh) and 0xFF) - ((c[i] shr sh) and 0xFF); return (v - nz * t).toInt().coerceIn(0, 255) }
                d[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            return Bitmap.createBitmap(d, dev.width, dev.height, Bitmap.Config.ARGB_8888)
        }
        fun draw(src: Bitmap, e: PhotoEdit): Bitmap {
            val isSmall = src === small
            val raw = rl != null && rm != null && e.baseFile == null
            val s0 = if (raw) {
                val g = e.grade
                var dev = RawDng.develop(if (isSmall && rawSmall != null) rawSmall else rl!!, rm!!, e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint)
                if (dev.width != src.width || dev.height != src.height) dev = Bitmap.createScaledBitmap(dev, src.width, src.height, true)
                if (e.detail.aiDenoise > 0f) residual(dev, src, if (isSmall) aiSmall else ai, e.detail.aiDenoise) else dev
            } else if (e.detail.aiDenoise > 0f) mixed(src, if (isSmall) aiSmall else ai, e.detail.aiDenoise) else src
            val eg = if (raw) e.grade.rawRest() else e.grade
            val tables = if (eg.isIdentity) null else eg.tables()
            val healNow = healMode.value
            val r = ImageOps.renderPhoto(s0, eg, e.detail, if (healNow) Geometry() else e.geometry, tables, e.lens, e.spots, if (healNow) null else e.masks, aiMasks.toMap(), if (healNow) null else e.bg, if (healNow) null else e.sky, if (healNow) null else e.lensBlur)
            if (healNow) return r
            var o = r
            for (t in e.texts) o = stampLayer(context, o, t)
            return e.watermark?.let { stampLayer(context, o, it) } ?: o
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
        exportT0 = System.currentTimeMillis(); batchN = 0; batchI = 0
        // Time estimate from earlier exports (ms per megapixel), or a first guess.
        val mp = exportMp(base, sizes[sizeIdx])
        val per = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getFloat("pe_ms_per_mp", 250f)
        exportEstMs = (mp * per * (if (aiScale > 1) 4 else 1)).toLong()
        val e = edit
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val raw = rawOn(e)
                    var full = (if (raw) rawExport(e, sizes[sizeIdx]) else GradeIO.decode(context, sourceUri(e), sizes[sizeIdx])) ?: return@withContext null
                    if (e.detail.aiDenoise > 0f) {
                        // Full-resolution AI noise reduction (progress badge; can be stopped).
                        val n = full.width * full.height
                        val px = IntArray(n).also { full.getPixels(it, 0, full.width, 0, 0, full.width, full.height) }
                        // Cloud AI (Topaz Denoise) when chosen in settings; otherwise / on failure the on-phone AI.
                        var den: IntArray? = null
                        if (com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)) {
                            try {
                                val jpeg = java.io.ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 96, it) }.toByteArray()
                                val res = com.dronedirector.veyrocutpro.camera.CloudAi.denoisePhoto(context, jpeg)
                                BitmapFactory.decodeByteArray(res, 0, res.size)?.let { b ->
                                    val sized = if (b.width == full.width && b.height == full.height) b else Bitmap.createScaledBitmap(b, full.width, full.height, true)
                                    den = IntArray(n).also { sized.getPixels(it, 0, full.width, 0, 0, full.width, full.height) }
                                }
                            } catch (t: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { throw AiDenoiser.Stopped() } catch (_: Throwable) {}
                        }
                        if (den == null) den = AiDenoiser.denoise(context, px, full.width, full.height)
                        val dn = den
                        if (dn != null) {
                            val w0 = full.width; val h0 = full.height
                            full.recycle()
                            full = Bitmap.createBitmap(AiDenoiser.blend(px, dn, e.detail.aiDenoise), w0, h0, Bitmap.Config.ARGB_8888)
                        }
                    }
                    val eg = if (raw) e.grade.rawRest() else e.grade
                    val tables = if (eg.isIdentity) null else eg.tables()
                    var out = ImageOps.renderPhoto(full, eg, e.detail, e.geometry, tables, e.lens, e.spots, e.masks, aiMasks.toMap(), e.bg, e.sky, e.lensBlur)
                    full.recycle()
                    // AI upscale (×2 / ×4, at most 24 MP).
                    if (aiScale > 1) {
                        val k = minOf(aiScale.toFloat(), kotlin.math.sqrt(24e6f / (out.width.toFloat() * out.height)))
                        if (k > 1.05f) {
                            val up = com.dronedirector.veyrocutpro.camera.Upscaler.upscaleBitmap(context, out, (out.width * k).toInt() / 2 * 2, (out.height * k).toInt() / 2 * 2)
                                ?: throw AiDenoiser.Stopped()
                            out.recycle(); out = up
                        }
                    }
                    for (t in e.texts) out = stampLayer(context, out, t)
                    e.watermark?.let { out = stampLayer(context, out, it) }
                    // A removed background needs a format with transparency.
                    val fmt = if (e.bg.active && e.bg.mode == 2 && format !in listOf(1, 2, 4, 5)) 1 else format
                    savePhotoAs(context, out, fmt, (quality * 100).toInt(), if (keepExif) uri else null, stripGps).also { saved ->
                        // Camera Raw (.xmp) sidecar for the original, with the closest matching settings.
                        if (saved != null && PhotoMeta.sidecar && uri != Uri.EMPTY) {
                            val where = PhotoMeta.writeSidecar(context, uri, PhotoMeta.sidecarXmp(e.grade, e.detail, e.geometry, raw))
                            if (where == null) scope.launch { message = "Photo saved, but the .xmp sidecar couldn't be written" }
                        }
                    }
                } catch (_: OutOfMemoryError) {
                    null
                } catch (t: Throwable) {
                    if (t is AiDenoiser.Stopped || t.cause is AiDenoiser.Stopped) "stopped" else null
                }
            }
            busy = false
            // Learn how long exports take on this phone (plain exports only, no AI steps).
            if (ok != null && ok != "stopped" && e.detail.aiDenoise <= 0f && aiScale <= 1) {
                val mpDone = exportMp(base, sizes[sizeIdx])
                val took = (System.currentTimeMillis() - exportT0) / mpDone.coerceAtLeast(0.5f)
                val p = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE)
                p.edit().putFloat("pe_ms_per_mp", p.getFloat("pe_ms_per_mp", took) * 0.6f + took * 0.4f).apply()
            }
            message = when {
                ok == "stopped" -> "Export stopped"
                ok != null -> "Saved"
                else -> "Couldn't export — try a smaller size"
            }
        }
    }

    // ---- AI selections (subject / sky), background and AI erase.
    val useCloud = com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)
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
    /** The photo's AI depth map (made once, reused by depth masks and Lens Blur). */
    var depthBusy by remember { mutableStateOf(false) }
    fun withDepth(done: (String) -> Unit) {
        val have = edit.lensBlur?.depthFile ?: edit.masks.flatMap { listOf(it) + it.subs.orEmpty() }.firstOrNull { it.type == MaskType.DEPTH }?.maskFile
        if (have != null && java.io.File(have).exists()) { done(have); return }
        val b = base ?: return
        if (depthBusy) return
        depthBusy = true; message = "Working out the depth…"
        scope.launch {
            val path = withContext(Dispatchers.Default) { try { LensBlurFx.analyse(context, b) } catch (_: Throwable) { null } }
            depthBusy = false
            if (path == null) message = "Couldn't work out the depth" + (DepthModel.failed?.let { " ($it)" } ?: " — the AI model may still be downloading")
            else { message = null; done(path) }
        }
    }
    val aiMaskAction: ((MaskType) -> Unit)? = { type ->
        when (type) {
            MaskType.OBJECT -> { picking = 3; message = "Tap the object you want to select" }
            MaskType.DEPTH -> withDepth { path ->
                val pm = PhotoMask(type = MaskType.DEPTH, maskFile = path, lumLow = 0.6f, lumHigh = 1f)
                edit = edit.copy(masks = edit.masks + pm); tools.selected = pm.id
            }
            else -> findMask(type) { path ->
                val pm = PhotoMask(type = type, maskFile = path)
                edit = edit.copy(masks = edit.masks + pm)
                tools.selected = pm.id
            }
        }
    }
    /** Adds an AI selection to a mask as add / subtract / intersect. */
    val aiSubAction: (MaskType, Int, String) -> Unit = { type, combine, parent ->
        fun add(path: String) {
            val sub = PhotoMask(type = type, maskFile = path, combine = combine, lumLow = if (type == MaskType.DEPTH) 0.6f else 0.7f)
            edit = edit.copy(masks = edit.masks.map { if (it.id == parent) it.copy(subs = it.subs.orEmpty() + sub) else it })
        }
        if (type == MaskType.DEPTH) withDepth { add(it) } else findMask(type) { add(it) }
    }
    // Select Objects: the AI's features of the photo, made once per photo.
    var samCache by remember { mutableStateOf<Pair<Bitmap, Sam.Embedding>?>(null) }
    fun selectObjectAt(nx: Float, ny: Float) {
        val b = base ?: return
        message = "Selecting the object…"
        scope.launch {
            val path = withContext(Dispatchers.Default) {
                try {
                    val emb = samCache?.takeIf { it.first === b }?.second ?: (Sam.encode(context, b) ?: return@withContext null).also { samCache = b to it }
                    val pts = listOf(MaskPoint(nx, ny, 1))
                    val ans = Sam.decode(emb, pts) ?: return@withContext null
                    val i = Sam.pick(ans, pts, 0)
                    val bytes = Sam.toMask(ans.mask(i), emb, b.width, b.height)
                    val px = IntArray(bytes.size) { k -> val v = bytes[k].toInt() and 255; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
                    AiSegment.saveMask(context, Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888))
                } catch (_: Throwable) { null }
            }
            if (path == null) message = "Couldn't select that" + (Sam.failed?.let { " ($it)" } ?: "")
            else {
                val pm = PhotoMask(type = MaskType.OBJECT, maskFile = path)
                edit = edit.copy(masks = edit.masks + pm); tools.selected = pm.id; message = "Object selected"
            }
        }
    }
    LaunchedEffect(pendingObject) { pendingObject?.let { (x, y) -> pendingObject = null; selectObjectAt(x, y) } }
    var eraseMask by remember { mutableStateOf<PhotoMask?>(null) }
    fun runErase() {
        val em = eraseMask ?: return
        if (em.strokes.isEmpty() || busy) return
        busy = true
        val e = edit
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val full = (if (rawOn(e)) rawExport(e, 2400, sliders = false) else GradeIO.decode(context, sourceUri(e), 2400)) ?: return@withContext null
                    val wt = PhotoLocal.weights(em.copy(amount = 1f), full.width, full.height)
                    val mask = Bitmap.createBitmap(IntArray(wt.size) { if (wt[it] > 0.25f) -1 else 0xFF000000.toInt() }, full.width, full.height, Bitmap.Config.ARGB_8888)
                    var result: Bitmap? = null
                    if (useCloud) try {
                        val jpeg = java.io.ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
                        val png = java.io.ByteArrayOutputStream().also { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        val res = com.dronedirector.veyrocutpro.camera.CloudAi.erase(context, jpeg, png)
                        result = BitmapFactory.decodeByteArray(res, 0, res.size)?.let { if (it.width == full.width && it.height == full.height) it else Bitmap.createScaledBitmap(it, full.width, full.height, true) }
                    } catch (_: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { return@withContext "stopped" } catch (_: Throwable) {}
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
    // Sky replacement: your own sky picture is copied into the app.
    val pickSky = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val f = java.io.File(java.io.File(context.filesDir, "skies").apply { mkdirs() }, java.util.UUID.randomUUID().toString() + ".jpg")
                    GradeIO.decode(context, u, 3000)?.let { b -> f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }; f.path }
                } catch (_: Throwable) { null }
            }
            if (path != null) edit = edit.copy(sky = edit.sky.copy(imagePath = path)) else message = "Couldn't open that picture"
        }
    }
    val skySection: @Composable () -> Unit = {
        val sk = edit.sky
        SectionTitle("SKY REPLACEMENT (AI)") { edit = edit.copy(sky = SkySwap()) }
        if (sk.maskFile == null) {
            Text("Finds the sky and swaps it for a new one — the rest of the photo takes on a little of its light.", color = Pro.Dim, fontSize = 11.sp)
            Text("FIND SKY", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { findMask(MaskType.SKY) { p -> edit = edit.copy(sky = edit.sky.copy(maskFile = p)) } }.padding(10.dp))
        } else {
            Chips(Skies.names + "Your picture…", if (sk.imagePath != null) Skies.names.size else sk.preset) { i ->
                if (i == Skies.names.size) pickSky.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else edit = edit.copy(sky = sk.copy(preset = i, imagePath = null))
            }
            EdSlider("Strength", sk.amount, 0f..1f, 1f, ::pct) { v -> edit = edit.copy(sky = sk.copy(amount = v)) }
            EdSlider("Match the light", sk.warm, 0f..1f, 0.3f, ::pct) { v -> edit = edit.copy(sky = sk.copy(warm = v)) }
            Text("FIND SKY AGAIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { findMask(MaskType.SKY) { p -> edit = edit.copy(sky = edit.sky.copy(maskFile = p)) } }.padding(8.dp))
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
    var cloneMode by remember { mutableStateOf(false) }
    var cloneSrc by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var cloneOff by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    fun addSpot(x: Float, y: Float) {
        val b = base ?: return
        val r = tools.healSize
        // Clone stamp: first tap picks the source, then each tap paints a copy from the same offset.
        if (cloneMode) {
            val src = cloneSrc
            if (src == null) { cloneSrc = x to y; cloneOff = null; message = "Source set — now tap where to paint"; return }
            val off = cloneOff ?: ((src.first - x) to ((src.second - y) * b.height / b.width)).also { cloneOff = it }
            edit = edit.copy(spots = edit.spots + Spot(x, y, r, off.first, off.second, clone = true))
            return
        }
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
        exportT0 = System.currentTimeMillis(); batchN = list.size; batchI = 0
        scope.launch {
            var done = 0
            for ((i, u) in list.withIndex()) {
                batchI = i
                message = "Batch ${i + 1} / ${list.size}…"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        // RAW files in the batch are developed with the same settings.
                        val developed = if (RawDng.isDng(context, u)) {
                            val g = e.grade
                            try { RawDng.export(context, RawDng.open(context, u), e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, sizes[sizeIdx]) } catch (_: RawDng.UnsupportedRaw) { null }
                        } else null
                        val full = developed ?: GradeIO.decode(context, u, sizes[sizeIdx]) ?: return@withContext false
                        val eg = if (developed != null) e.grade.rawRest() else e.grade
                        val tables = if (eg.isIdentity) null else eg.tables()
                        var out = ImageOps.renderPhoto(full, eg, e.detail.copy(aiDenoise = 0f), if (batchWithCrop) e.geometry else Geometry(), tables, e.lens, null, if (batchWithCrop) e.masks.filter { it.maskFile == null } else null)
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

    /** The saved edit of a filmstrip photo, or a fresh one. */
    fun sessionProject(i: Int): PhotoProjectData? {
        val sp = PhotoSession.items.getOrNull(i) ?: return null
        return sp.projectId?.let { ProjectStore.loadPhoto(context, it) } ?: PhotoProjectData(name = sp.name, uri = Uri.parse(sp.uri), session = true)
    }
    /** Copies this photo's look (colour, light, detail, lens, RAW develop) to the selected photos. Crop, masks and spots stay their own. */
    fun syncTo(list: List<Int>) {
        if (busy) return
        sessionSave()
        val e = edit
        val targets = list.filter { it != sessionIdx }
        if (targets.isEmpty()) return
        busy = true
        scope.launch {
            for ((n, i) in targets.withIndex()) {
                message = "Syncing ${n + 1} / ${targets.size}…"
                withContext(Dispatchers.IO) {
                    try {
                        val p = sessionProject(i) ?: return@withContext
                        val q = p.copy(grade = e.grade, detail = e.detail, lens = e.lens, raw = e.raw, session = true)
                        val small = GradeIO.decode(context, q.uri, 320)
                        val thumb = small?.let { b ->
                            val tables = if (q.grade.isIdentity) null else q.grade.tables()
                            ImageOps.renderPhoto(b, q.grade, q.detail.copy(aiDenoise = 0f), q.geometry, tables, q.lens, null, null).also { if (it !== b) b.recycle() }
                        }
                        val saved = ProjectStore.savePhoto(context, q, thumb)
                        withContext(Dispatchers.Main) { PhotoSession.update(context, i) { it.copy(projectId = saved.id, edited = true, version = System.currentTimeMillis()) } }
                    } catch (_: Throwable) {}
                }
            }
            busy = false
            message = "Edit synced to ${targets.size} photo" + (if (targets.size == 1) "" else "s")
        }
    }
    /** Exports the selected filmstrip photos, each with its own edit, using the export settings here. */
    fun exportSession(list: List<Int>) {
        if (busy || list.isEmpty()) return
        sessionSave()
        busy = true
        exportT0 = System.currentTimeMillis(); batchN = list.size; batchI = 0
        scope.launch {
            var done = 0
            for ((n, i) in list.withIndex()) {
                batchI = n
                message = "Export ${n + 1} / ${list.size}…"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val p = sessionProject(i) ?: return@withContext false
                        val u = p.baseFile?.let { Uri.fromFile(java.io.File(it)) } ?: p.uri
                        val developed = if (p.baseFile == null && RawDng.isDng(context, u)) {
                            val g = p.grade
                            try { RawDng.export(context, RawDng.open(context, u), p.raw ?: RawDng.Dev(), g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, sizes[sizeIdx]) } catch (_: RawDng.UnsupportedRaw) { null }
                        } else null
                        val full = developed ?: GradeIO.decode(context, u, sizes[sizeIdx]) ?: return@withContext false
                        val eg = if (developed != null) p.grade.rawRest() else p.grade
                        val tables = if (eg.isIdentity) null else eg.tables()
                        var out = ImageOps.renderPhoto(full, eg, p.detail.copy(aiDenoise = 0f), p.geometry, tables, p.lens, p.spots, p.masks.filter { it.maskFile == null })
                        if (out !== full) full.recycle()
                        p.watermark?.let { out = stampLayer(context, out, it) }
                        savePhotoAs(context, out, format, (quality * 100).toInt(), if (keepExif) p.uri else null, stripGps) != null
                    } catch (_: Throwable) { false }
                }
                if (ok) done++
            }
            busy = false
            message = "Exported $done of ${list.size} photos"
        }
    }

    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            AiDenoiser.running, "AI NOISE REDUCTION", 1, AiDenoiser.progress, onStop = { AiDenoiser.stop() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        )
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            com.dronedirector.veyrocutpro.camera.Upscaler.pending > 0, "AI UPSCALING", 1, com.dronedirector.veyrocutpro.camera.Upscaler.progress,
            onStop = { com.dronedirector.veyrocutpro.camera.Upscaler.cancelAll() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(22f),
        )
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            com.dronedirector.veyrocutpro.camera.CloudAi.stage != null, com.dronedirector.veyrocutpro.camera.CloudAi.stage ?: "", 1,
            com.dronedirector.veyrocutpro.camera.CloudAi.progress, onStop = { com.dronedirector.veyrocutpro.camera.CloudAi.stop() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(21f),
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp
            val boxW = maxWidth.value; val boxH = maxHeight.value
            val previewBox = @Composable { mod: Modifier ->
                Box(
                    // Mouse: wheel = next / previous filmstrip photo, right-click = before / after split, double-click = hide / show the tools.
                    mod.mouseActions(
                        onWheel = { dy, _, at, ctrl, _ ->
                            if (ctrl || zoomK > 1f) {
                                val nz = (zoomK * (if (dy < 0) 1.25f else 0.8f)).coerceIn(1f, 8f)
                                val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat()
                                panX = (panX - (at.x - bw / 2f - panX) * (nz / zoomK - 1f)).coerceIn(-bw * (nz - 1f) / 2f, bw * (nz - 1f) / 2f)
                                panY = (panY - (at.y - bh / 2f - panY) * (nz / zoomK - 1f)).coerceIn(-bh * (nz - 1f) / 2f, bh * (nz - 1f) / 2f)
                                zoomK = nz; if (nz <= 1.001f) resetZoom()
                                return@mouseActions true
                            }
                            val n = PhotoSession.items.size
                            if (sessionIdx >= 0 && n > 1) { val to = (sessionIdx + if (dy > 0) 1 else -1).coerceIn(0, n - 1); if (to != sessionIdx) switchTo(to); true } else false
                        },
                        onRightClick = { split = !split },
                        onDoubleClick = { com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.controls", !showControls) },
                    ).pointerInput(split, panel, picking) {
                        // Pinch to zoom in (1:1 loupe), drag to look around while zoomed. Off in Masks / Heal and the split view.
                        if (!split && picking == 0 && panel != PhotoPanel.MASKS && panel != PhotoPanel.HEAL) detectTransformGestures { c, pan, z, _ ->
                            val nz = (zoomK * z).coerceIn(1f, 8f)
                            if (nz != zoomK || zoomK > 1f) {
                                val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat()
                                panX = ((panX + pan.x) - (c.x - bw / 2f - panX) * (nz / zoomK - 1f)).coerceIn(-bw * (nz - 1f) / 2f, bw * (nz - 1f) / 2f)
                                panY = ((panY + pan.y) - (c.y - bh / 2f - panY) * (nz / zoomK - 1f)).coerceIn(-bh * (nz - 1f) / 2f, bh * (nz - 1f) / 2f)
                                zoomK = nz
                                if (zoomK <= 1.001f) resetZoom()
                            }
                        }
                    }.pointerInput(split, panel, picking) {
                        if (split) {
                            // Drag anywhere to move the before / after divider.
                            detectHorizontalDragGestures { ch, _ -> splitX = (ch.position.x / size.width).coerceIn(0.02f, 0.98f) }
                        } else if (picking != 0) {
                            // Eyedropper: tap the spot to sample.
                            detectTapGestures(onTap = { o -> pickAt(o, 6.dp.toPx()) })
                        } else if (panel != PhotoPanel.MASKS && panel != PhotoPanel.HEAL) {
                            detectTapGestures(
                                onPress = { if (zoomK <= 1f) { comparing = true; tryAwaitRelease(); comparing = false } },
                                // Double-tap: 1:1 loupe at that spot, again to fit.
                                onDoubleTap = { o ->
                                    if (zoomK > 1f) resetZoom() else {
                                        val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat(); val z = 3f
                                        zoomK = z; panX = (-(o.x - bw / 2f) * (z - 1f)).coerceIn(-bw, bw); panY = (-(o.y - bh / 2f) * (z - 1f)).coerceIn(-bh, bh)
                                    }
                                },
                            )
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = if (comparing) base else preview ?: base
                    val before = beforeImg
                    when {
                        failed -> Text("This file can't be opened.", color = Pro.Dim)
                        uri == Uri.EMPTY && edit.baseFile == null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text("No photo yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Add a photo to start editing.", color = Pro.Dim, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Gallery", color = Color.Black, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(onClick = onAddGallery).padding(horizontal = 18.dp, vertical = 10.dp))
                                Text("Browse files", color = Pro.Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Pro.Amber, RoundedCornerShape(50)).clickable(onClick = onAddFiles).padding(horizontal = 18.dp, vertical = 10.dp))
                            }
                        }
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
                        shown != null -> Box(
                            Modifier.fillMaxSize().padding(6.dp).onSizeChanged { viewSize = it }
                                .graphicsLayer(scaleX = zoomK, scaleY = zoomK, translationX = panX, translationY = panY),
                        ) {
                            val img = if (comparing) shown else dustImg ?: (if (zoomK > 1.6f) loupe else null) ?: proofImg ?: shown
                            Image(img.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            if (!comparing) {
                                sharpImg?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                                clipImg?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                        else -> CircularProgressIndicator(color = Pro.Amber)
                    }
                    // Mask handles / brush, and heal taps, on top of the photo.
                    val shownNow = if (comparing) base else preview ?: base
                    if (!split && !comparing && shownNow != null) {
                        val selMask = edit.masks.firstOrNull { it.id == tools.selected }
                        if (panel == PhotoPanel.MASKS && selMask != null && picking == 0) MaskEditOverlay(selMask, shownNow.width, shownNow.height, tools, image = shownNow) { m ->
                            edit = edit.copy(masks = edit.masks.map { if (it.id == m.id) m else it })
                        }
                        val em = eraseMask
                        if (panel == PhotoPanel.HEAL && em != null) MaskEditOverlay(em, shownNow.width, shownNow.height, tools) { m -> eraseMask = m }
                        else if (panel == PhotoPanel.HEAL) HealOverlay(
                            edit.spots, shownNow.width, shownNow.height, tools.healSize,
                            onMove = { i, sp -> if (i in edit.spots.indices) edit = edit.copy(spots = edit.spots.toMutableList().also { it[i] = sp }) },
                            onDelete = { i -> if (i in edit.spots.indices) { edit = edit.copy(spots = edit.spots.filterIndexed { k, _ -> k != i }); message = "Spot removed" } },
                        ) { x, y -> addSpot(x, y) }
                    }
                    // RGB histogram: the same pull-out drawer as in the video editor, on the right edge.
                    if (showScope && !comparing) ScopeDrawer(hist, Modifier.align(Alignment.CenterEnd), frame = preview ?: base)
                    if (zoomK > 1f) Text(
                        (if (loupe != null && zoomK > 1.6f) "1:1 · " else "") + "${(zoomK * 100).toInt()}% · double-tap to fit", style = Pro.Label.copy(color = Color.White),
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).clickable { resetZoom() }.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (picking != 0) Text(
                        when (picking) { 1 -> "Tap something that should be neutral grey or white"; 3 -> "Tap the object to select"; 4 -> "Tap where the focus should be"; else -> "Tap the colour you want to change" },
                        style = Pro.Label.copy(color = Color.Black),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { picking = 0 }.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    if (rawOn(edit)) Text(
                        "RAW", style = Pro.Label.copy(color = Color.Black),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
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
                            // Estimated time remaining: learned from earlier exports (batch: from the photos done so far).
                            var now by remember { mutableStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
                            val elapsed = now - exportT0
                            val eta = when {
                                batchN > 0 && batchI > 0 -> etaText(elapsed * (batchN - batchI) / batchI)
                                batchN > 0 -> "Estimating time left…"
                                exportEstMs > 0 -> if (elapsed < exportEstMs) etaText(exportEstMs - elapsed) else "Almost done…"
                                else -> "Estimating time left…"
                            }
                            Text(eta, color = Pro.Dim, fontSize = 13.sp)
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
                            PhotoPanel.MASKS -> MasksPanel(edit.masks, tools, { edit = edit.copy(masks = it) }, onAi = aiMaskAction, onAiSub = aiSubAction)
                            PhotoPanel.HEAL -> {
                                SectionTitle("HEAL / CLONE") { edit = edit.copy(spots = emptyList()) }
                                Chips(listOf("Heal", "Clone stamp"), if (cloneMode) 1 else 0) { cloneMode = it == 1; cloneSrc = null; cloneOff = null }
                                if (cloneMode) {
                                    Text(if (cloneSrc == null) "Tap the spot to copy FROM." else "Tap where to paint the copy; more taps keep the same offset.", color = Pro.Amber, fontSize = 11.sp)
                                    Text("NEW SOURCE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { cloneSrc = null; cloneOff = null }.padding(8.dp))
                                }
                                Text("Tap dust, blemishes or small objects on the photo. A matching patch from nearby is blended over each one (the whole photo is shown while you heal). Drag a solid circle to move a spot, drag its dashed circle to choose where it copies from, hold a spot to delete it.", color = Pro.Dim, fontSize = 11.sp)
                                EdSlider("Size", tools.healSize, 0.005f..0.12f, 0.025f, ::pct) { tools.healSize = it }
                                Text("${edit.spots.size} spot(s) healed", style = Pro.Label)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("UNDO LAST", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { edit = edit.copy(spots = edit.spots.dropLast(1)) }.padding(8.dp))
                                    Text("CLEAR ALL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { edit = edit.copy(spots = emptyList()) }.padding(8.dp))
                                }
                                SectionTitle("DUST SPOTS")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PillChip(if (dustView) "VISUALISE SPOTS ✓" else "VISUALISE SPOTS", dustView) { dustView = !dustView }
                                    PillChip("FIND DUST AUTOMATICALLY", false, accentText = true) {
                                        val b = base
                                        if (b != null) scope.launch {
                                            message = "Looking for dust…"
                                            val found = withContext(Dispatchers.Default) { Upright.findDust(b, dustSens) }
                                            // Skip spots that are already healed.
                                            val fresh = found.filter { n -> edit.spots.none { kotlin.math.hypot((it.x - n.x).toDouble(), (it.y - n.y).toDouble()) < (it.r + n.r) } }
                                            edit = edit.copy(spots = edit.spots + fresh)
                                            message = if (fresh.isEmpty()) "No dust spots found" else "${fresh.size} dust spot(s) healed — check them and remove any that aren't dust"
                                        }
                                    }
                                }
                                EdSlider("Sensitivity", dustSens, 0f..1f, 0.5f, ::pct) { dustSens = it }
                                Text("VISUALISE SPOTS shows a black-and-white view where dust and specks stand out. FIND DUST heals small dark specks in smooth areas such as the sky.", color = Pro.Dim, fontSize = 11.sp)
                                aiEraseSection()
                            }
                            PhotoPanel.TEXT -> {
                                // Text and sticker layers on the photo.
                                var selText by remember { mutableStateOf<String?>(null) }
                                SectionTitle("TEXT & STICKERS") { edit = edit.copy(texts = emptyList()) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("+ TEXT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                                        val l = Layer(kind = LayerKind.TEXT, text = "Your text", size = 0.07f, x = 0.5f, y = 0.5f, wholeVideo = true)
                                        edit = edit.copy(texts = edit.texts + l); selText = l.id
                                    }.padding(10.dp))
                                    listOf("⭐", "❤️", "📍", "🔥", "✅", "➡️").forEach { em ->
                                        Text(em, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                                            val l = Layer(kind = LayerKind.STICKER, text = em, size = 0.12f, x = 0.5f, y = 0.5f, wholeVideo = true, shadow = false)
                                            edit = edit.copy(texts = edit.texts + l); selText = l.id
                                        }.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                edit.texts.forEach { t ->
                                    val sel = t.id == selText
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                                        .clickable { selText = t.id }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(t.text.replace('\n', ' ').take(30), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { edit = edit.copy(texts = edit.texts.filter { it.id != t.id }) }.padding(horizontal = 8.dp))
                                    }
                                }
                                val t = edit.texts.firstOrNull { it.id == selText }
                                if (t != null) {
                                    fun upd(n: Layer) { edit = edit.copy(texts = edit.texts.map { if (it.id == t.id) n else it }) }
                                    if (t.kind == LayerKind.TEXT) {
                                        androidx.compose.material3.OutlinedTextField(value = t.text, onValueChange = { upd(t.copy(text = it)) }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                        Chips(LayerFont.entries.map { it.label }, t.font.ordinal) { upd(t.copy(font = LayerFont.entries[it])) }
                                        val cols = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFB300.toInt(), 0xFFFF3B30.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt())
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                            cols.forEach { cl -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color(cl)).border(2.dp, if (t.color == cl) Pro.Amber else Pro.Line, CircleShape).clickable { upd(t.copy(color = cl)) }) }
                                        }
                                        Chips(listOf("Plain", "Shadow", "Box"), if (t.box) 2 else if (t.shadow) 1 else 0) { i -> upd(t.copy(shadow = i == 1, box = i == 2)) }
                                    }
                                    EdSlider("Size", t.size, 0.02f..0.4f, 0.07f, ::pct) { upd(t.copy(size = it)) }
                                    EdSlider("Across", t.x, 0f..1f, 0.5f, ::pct) { upd(t.copy(x = it)) }
                                    EdSlider("Down", t.y, 0f..1f, 0.5f, ::pct) { upd(t.copy(y = it)) }
                                    EdSlider("Opacity", t.opacity, 0.05f..1f, 1f, ::pct) { upd(t.copy(opacity = it)) }
                                } else if (edit.texts.isEmpty()) {
                                    Text("Add titles, captions, labels or stickers to the photo. They are drawn on top of the edit and saved with the export.", color = Pro.Dim, fontSize = 11.sp)
                                }
                            }
                            PhotoPanel.HISTORY -> {
                                SectionTitle("SNAPSHOTS")
                                Text("Save the look as it is now, try something else, and jump back any time. Snapshots last while this photo is open; SAVE the project to keep the current one.", color = Pro.Dim, fontSize = 11.sp)
                                Text("+ SAVE SNAPSHOT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { snapshots.add("Snapshot ${snapshots.size + 1}" to edit); message = "Snapshot ${snapshots.size} saved" }.padding(10.dp))
                                snapshots.toList().forEachIndexed { i, (name, se) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (se == edit) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                                        .clickable { edit = se; message = "$name" }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { snapshots.removeAt(i) }.padding(horizontal = 8.dp))
                                    }
                                }
                                SectionTitle("HISTORY")
                                Text("Every change, newest at the top — tap one to go back to it.", color = Pro.Dim, fontSize = 11.sp)
                                for (i in undo.indices.reversed()) {
                                    val label = if (i == 0) "Original" else "Step $i"
                                    Text(label + if (i == undoPos) "  ●" else "", color = if (i == undoPos) Pro.Amber else Color.White, fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { undoPos = i; edit = undo[i] }.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
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
                                isRaw = rawOn(edit),
                                rawMeta = if (rawOn(edit)) rawMeta else null,
                                thumbSrc = base,
                                dev = DevUi(
                                    picking = picking, setPicking = { picking = if (picking == it) 0 else it },
                                    clipWarn = clipWarn, setClipWarn = { clipWarn = it },
                                    sharpMask = sharpMaskView, setSharpMask = { sharpMaskView = it },
                                    autoTone = { base?.let { b -> edit = edit.copy(grade = Develop.autoTone(b, edit.grade)); message = "Auto tone applied" } },
                                    autoWb = { base?.let { b -> edit = edit.copy(grade = Develop.autoWb(b, edit.grade)); message = "Auto white balance applied" } },
                                    importXmp = { try { pickXmp.launch(arrayOf("*/*")) } catch (_: Throwable) {} },
                                    exportXmp = { exportXmp() },
                                    analyseDepth = { withDepth { p -> edit = edit.copy(lensBlur = (edit.lensBlur ?: LensBlur()).copy(depthFile = p, on = true)) } },
                                    depthBusy = depthBusy,
                                    upright = { mode ->
                                        val b = base
                                        if (mode < 0) edit = edit.copy(geometry = edit.geometry.copy(straighten = 0f, vertical = 0f, horizontal = 0f))
                                        else if (b != null && !uprightBusy) {
                                            uprightBusy = true
                                            scope.launch {
                                                val g = withContext(Dispatchers.Default) { try { Upright.solve(b, edit.geometry, mode) } catch (_: Throwable) { null } }
                                                uprightBusy = false
                                                if (g != null) { edit = edit.copy(geometry = g); message = "Upright: ${Upright.MODES[mode]} — rotate %.1f°".format(g.straighten) }
                                            }
                                        }
                                    },
                                    uprightBusy = uprightBusy,
                                ),
                            )
                        }
                        if (panel == PhotoPanel.EFFECTS) { backgroundSection(); skySection() }
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
                    // No back arrow here (it squeezed EXPORT); the phone's back gesture / button closes the editor.
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    BarIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = undoPos > 0) { undoPos--; edit = undo[undoPos] }
                    BarIcon(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = undoPos < undo.lastIndex) { undoPos++; edit = undo[undoPos] }
                    BarIcon(Icons.Filled.BarChart, "Scope", tint = if (showScope) Pro.Amber else Color.White) { com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.scope", !showScope) }
                    PhotoPanelsButton(
                        onTab = { panel = it },
                        onReset = {
                            listOf("pe_controls_w", "pe_controls_h", "pe_strip_h").forEach { PaneSizes.set(context, it, -1f) }
                            paneVersion++
                            split = false
                            message = "Layout reset to original"
                        },
                    )
                    BarIcon(Icons.Filled.Settings, "Settings") { showSettings = true }
                    BarButton("LOAD", enabled = !busy) { showLoad = true }
                    BarButton("SAVE", enabled = !busy) { if (projectId == null) askName = true else saveProject(projectName) }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !busy) { panel = PhotoPanel.EXPORT; com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.controls", true) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                // Shown whenever it is on in the Panels menu, even when empty (its + adds photos).
                val strip = com.dronedirector.veyrocutpro.ui.panelOn("pe.filmstrip")
                val stripPane = rememberPane("pe_strip_h", paneVersion)
                val stripSz = if (stripPane.dp < 0f) 64f else stripPane.dp
                val viewer = @Composable { mod: Modifier ->
                    Column(mod) {
                        previewBox(Modifier.fillMaxWidth().weight(1f))
                        // Divider between the photo and the filmstrip: hold, then drag up for bigger thumbnails.
                        if (strip) ResizeHandle(true,
                            onDrag = { d -> stripPane.dp = (stripSz - d).coerceIn(40f, 200f) },
                            onEnd = { stripPane.save() }, onReset = { stripPane.reset() })
                        if (strip) Filmstrip(
                            thumbSize = stripSz.dp,
                            onZoom = { step -> stripPane.dp = (stripSz + step).coerceIn(40f, 200f); stripPane.save() },
                            onOpen = { switchTo(it) },
                            onAdd = onAddGallery,
                            onSync = { syncTo(it) },
                            onExport = { exportSession(it) },
                        )
                    }
                }
                // Resizable panels: hold the divider, then drag. Double-tap the divider to reset.
                // With the tools panel hidden, the photo fills the screen; shown again, it returns to its saved size.
                if (!showControls) {
                    viewer(Modifier.fillMaxWidth().weight(1f))
                } else if (wide) {
                    val pane = rememberPane("pe_controls_w", paneVersion)
                    val maxW = boxW
                    Row(Modifier.fillMaxSize()) {
                        viewer(Modifier.weight(1f).fillMaxHeight())
                        ResizeHandle(false,
                            onDrag = { d -> pane.dp = ((if (pane.dp < 0f) 380f else pane.dp) - d).coerceIn(240f, maxW - 220f) },
                            onEnd = { pane.save() }, onReset = { pane.reset() })
                        controls(Modifier.width((if (pane.dp < 0f) 380f else pane.dp).dp).fillMaxHeight())
                    }
                } else {
                    val pane = rememberPane("pe_controls_h", paneVersion)
                    val maxH = boxH
                    viewer(Modifier.fillMaxWidth().weight(1f))
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
    val shown = if (com.dronedirector.veyrocutpro.camera.SimpleMode.on)
        listOf(PhotoPanel.PRESETS, PhotoPanel.LIGHT, PhotoPanel.COLOR, PhotoPanel.HEAL, PhotoPanel.CROP, PhotoPanel.TEXT, PhotoPanel.EXPORT)
    else PhotoPanel.entries.toList()
    Chips(shown.map { it.label }, shown.indexOf(selected)) { onSelect(shown[it]) }
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
    isRaw: Boolean = false,
    rawMeta: RawDng.Meta? = null,
    thumbSrc: Bitmap? = null,
    dev: DevUi? = null,
) {
    val g = e.grade
    fun grade(f: (ProGrade) -> ProGrade) = set(e.copy(grade = f(g)))
    fun detail(f: (Detail) -> Detail) = set(e.copy(detail = f(e.detail)))
    fun geo(f: (Geometry) -> Geometry) = set(e.copy(geometry = f(e.geometry)))

    when (panel) {
        PhotoPanel.PRESETS -> {
            UserPresetSection({ g to e.detail }) { p -> set(e.copy(grade = p.grade, detail = p.detail.copy(aiDenoise = e.detail.aiDenoise))) }
            if (dev != null) Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("IMPORT .XMP PRESETS", false, accentText = true) { dev.importXmp() }
                PillChip("EXPORT AS .XMP", false, accentText = true) { dev.exportXmp() }
            }
            Text("Import .xmp presets or sidecars (Camera Raw settings) (they're added to MY PRESETS). Export saves this look as an .xmp preset in Documents/VeyroCutPro/Presets.", color = Pro.Dim, fontSize = 11.sp)
            BuiltInPresetSection(thumbSrc, g, e.detail) { ng, nd -> set(e.copy(grade = ng, detail = nd.copy(aiDenoise = e.detail.aiDenoise))) }
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
            if (isRaw) {
                SectionTitle("RAW PROFILE") { set(e.copy(raw = e.raw.copy(profile = 0))) }
                // "Camera" (the maker's own profile in the file) only when the DNG has one.
                val profs = RawDng.PROFILES.withIndex().filter { !it.value.camera || rawMeta?.hasCameraProfile == true }
                Chips(profs.map { p -> if (p.value.camera) "Camera" + (rawMeta?.profileName?.let { " · " + it.take(18) } ?: "") else p.value.label },
                    profs.indexOfFirst { it.index == e.raw.profile }.coerceAtLeast(0)) { i -> set(e.copy(raw = e.raw.copy(profile = profs[i].index))) }
                Text("RAW (DNG): exposure, highlights, shadows, whites, blacks and white balance work on the sensor data, with headroom above white.", color = Pro.Dim, fontSize = 11.sp)
                if (rawMeta?.hasLensOps == true) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                        .clickable { set(e.copy(raw = e.raw.copy(lensOff = !e.raw.lensOff))) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lens corrections from the file", color = Color.White, fontSize = 14.sp)
                            Text("Distortion, colour fringing and vignetting as the camera recorded them in the DNG.", color = Pro.Dim, fontSize = 11.sp)
                        }
                        androidx.compose.material3.Switch(checked = !e.raw.lensOff, onCheckedChange = { set(e.copy(raw = e.raw.copy(lensOff = !it))) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber))
                    }
                }
                rawMeta?.info?.takeIf { it.isNotEmpty() }?.let { info ->
                    SectionTitle("RAW INFO")
                    info.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(k, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.width(130.dp))
                            Text(v, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
            SectionTitle("LIGHT") { grade { it.copy(exposure = 0f, contrast = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f) } }
            if (dev != null) Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("AUTO", false, accentText = true) { dev.autoTone() }
                PillChip(if (dev.clipWarn) "CLIPPING ⚠ ON" else "CLIPPING ⚠", dev.clipWarn) { dev.setClipWarn(!dev.clipWarn) }
            }
            if (dev?.clipWarn == true) Text("Red = blown-out highlights, blue = crushed shadows.", color = Pro.Dim, fontSize = 11.sp)
            EdSlider("Exposure", g.exposure, if (isRaw) -5f..5f else -3f..3f, format = ::ev) { v -> grade { it.copy(exposure = v) } }
            EdSlider("Contrast", g.contrast) { v -> grade { it.copy(contrast = v) } }
            EdSlider("Highlights", g.highlights) { v -> grade { it.copy(highlights = v) } }
            EdSlider("Shadows", g.shadows) { v -> grade { it.copy(shadows = v) } }
            EdSlider("Whites", g.whites) { v -> grade { it.copy(whites = v) } }
            EdSlider("Blacks", g.blacks) { v -> grade { it.copy(blacks = v) } }
        }
        PhotoPanel.CURVE -> {
            var ch by remember { mutableStateOf(0) }
            SectionTitle("POINT CURVES") { grade { it.copy(curveRgb = null, curveR = null, curveG = null, curveB = null) } }
            val all = listOf(g.curveRgb, g.curveR, g.curveG, g.curveB)
            CurveEditor(ch, { ch = it }, all[ch], { p ->
                grade { when (ch) { 1 -> it.copy(curveR = p); 2 -> it.copy(curveG = p); 3 -> it.copy(curveB = p); else -> it.copy(curveRgb = p) } }
            }, others = all)
            SectionTitle("PARAMETRIC TONE CURVE") { grade { it.copy(curveHighlights = 0f, curveLights = 0f, curveDarks = 0f, curveShadows = 0f) } }
            EdSlider("Highlights", g.curveHighlights) { v -> grade { it.copy(curveHighlights = v) } }
            EdSlider("Lights", g.curveLights) { v -> grade { it.copy(curveLights = v) } }
            EdSlider("Darks", g.curveDarks) { v -> grade { it.copy(curveDarks = v) } }
            EdSlider("Shadows", g.curveShadows) { v -> grade { it.copy(curveShadows = v) } }
            SectionTitle("CONTRAST PIVOT")
            EdSlider("Pivot", g.pivot, 0.2f..0.7f, 0.435f, ::pct) { v -> grade { it.copy(pivot = v) } }
        }
        PhotoPanel.COLOR -> {
            SectionTitle("WHITE BALANCE") { set(e.copy(raw = if (isRaw) e.raw.copy(wb = 0) else e.raw, grade = g.copy(temperature = 0f, tint = 0f))) }
            if (isRaw) Chips(RawDng.WB_PRESETS.map { it.first }, e.raw.wb) { i -> set(e.copy(raw = e.raw.copy(wb = i))) }
            if (dev != null) Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("AUTO", false, accentText = true) { dev.autoWb() }
                PillChip(if (dev.picking == 1) "EYEDROPPER — TAP THE PHOTO" else "EYEDROPPER", dev.picking == 1) { dev.setPicking(1) }
            }
            EdSlider("Temperature", g.temperature) { v -> grade { it.copy(temperature = v) } }
            EdSlider("Tint", g.tint) { v -> grade { it.copy(tint = v) } }
            SectionTitle("PRESENCE") { grade { it.copy(vibrance = 0f, saturation = 0f) } }
            EdSlider("Vibrance", g.vibrance) { v -> grade { it.copy(vibrance = v) } }
            EdSlider("Saturation", g.saturation) { v -> grade { it.copy(saturation = v) } }
            // ---- Point Color
            SectionTitle("POINT COLOR") { grade { it.copy(pointColors = null) } }
            if (dev != null) PillChip(if (dev.picking == 2) "TAP A COLOUR ON THE PHOTO" else "+ PICK A COLOUR", dev.picking == 2, accentText = true) { dev.setPicking(2) }
            val pcs = g.pointColors.orEmpty()
            var pcSel by remember { mutableStateOf(0) }
            if (pcs.isEmpty()) Text("Pick a colour on the photo, then shift just that colour's hue, saturation and brightness.", color = Pro.Dim, fontSize = 11.sp)
            else {
                val sel = pcSel.coerceIn(0, pcs.lastIndex)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    pcs.forEachIndexed { i, pc ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.HSVToColor(floatArrayOf(pc.hue, pc.sat, pc.lum.coerceAtLeast(0.3f)))))
                            .border(2.dp, if (i == sel) Color.White else Color.Transparent, CircleShape).clickable { pcSel = i })
                    }
                    Text("DELETE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { grade { it.copy(pointColors = pcs.filterIndexed { k, _ -> k != sel }.ifEmpty { null }) }; pcSel = 0 }.padding(6.dp))
                }
                val pc = pcs[sel]
                fun upd(n: PointColor) = grade { it.copy(pointColors = pcs.toMutableList().also { l -> l[sel] = n }) }
                EdSlider("Hue shift", pc.dHue) { v -> upd(pc.copy(dHue = v)) }
                EdSlider("Saturation", pc.dSat) { v -> upd(pc.copy(dSat = v)) }
                EdSlider("Luminance", pc.dLum) { v -> upd(pc.copy(dLum = v)) }
                EdSlider("Range", pc.range, 0f..1f, 0.5f, ::pct) { v -> upd(pc.copy(range = v)) }
            }
            // ---- Calibration
            val cal = g.calib ?: Calibration()
            fun calib(f: (Calibration) -> Calibration) = grade { it.copy(calib = f(cal).takeIf { c -> !c.isZero }) }
            SectionTitle("CALIBRATION") { grade { it.copy(calib = null) } }
            EdSlider("Shadows tint", cal.shadowTint) { v -> calib { it.copy(shadowTint = v) } }
            EdSlider("Red primary · hue", cal.redHue) { v -> calib { it.copy(redHue = v) } }
            EdSlider("Red primary · saturation", cal.redSat) { v -> calib { it.copy(redSat = v) } }
            EdSlider("Green primary · hue", cal.greenHue) { v -> calib { it.copy(greenHue = v) } }
            EdSlider("Green primary · saturation", cal.greenSat) { v -> calib { it.copy(greenSat = v) } }
            EdSlider("Blue primary · hue", cal.blueHue) { v -> calib { it.copy(blueHue = v) } }
            EdSlider("Blue primary · saturation", cal.blueSat) { v -> calib { it.copy(blueSat = v) } }
        }
        PhotoPanel.MIXER -> HslMixer(g.hsl) { list -> grade { it.copy(hsl = list) } }
        PhotoPanel.GRADING -> {
            SectionTitle("COLOR GRADING") { grade { it.copy(gradeShadows = Wheel(200f), gradeMids = Wheel(40f), gradeHighlights = Wheel(40f)) } }
            WheelControl("Shadows", g.gradeShadows, showLuma = false) { w -> grade { it.copy(gradeShadows = w) } }
            WheelControl("Midtones", g.gradeMids, showLuma = false) { w -> grade { it.copy(gradeMids = w) } }
            WheelControl("Highlights", g.gradeHighlights, showLuma = false) { w -> grade { it.copy(gradeHighlights = w) } }
            WheelControl("Global", g.gradeGlobal ?: Wheel(40f), showLuma = true) { w -> grade { it.copy(gradeGlobal = w.takeIf { x -> !x.isZero }) } }
            EdSlider("Blending", 0.5f + g.gradeBlend, 0f..1f, 0.5f, ::pct) { v -> grade { it.copy(gradeBlend = v - 0.5f) } }
            EdSlider("Balance", g.gradeBalance) { v -> grade { it.copy(gradeBalance = v) } }
            Text("Blending: how much the shadow, midtone and highlight tints overlap. Balance: moves the split between shadows and highlights.", color = Pro.Dim, fontSize = 11.sp)
        }
        PhotoPanel.DETAIL -> {
            SectionTitle("SHARPENING")
            EdSlider("Amount", e.detail.sharpen, 0f..1f, format = ::pct) { v -> detail { it.copy(sharpen = v) } }
            EdSlider("Radius", e.detail.sharpRadius, 0.5f..3f, 1f, { "%.1f".format(it) }) { v -> detail { it.copy(sharpenRadius = v - 1f) } }
            EdSlider("Detail", e.detail.sharpDetail, 0f..1f, 0.25f, ::pct) { v -> detail { it.copy(sharpenDetail = v - 0.25f) } }
            EdSlider("Masking", e.detail.sharpenMasking, 0f..1f, 0f, ::pct) { v -> detail { it.copy(sharpenMasking = v) } }
            if (dev != null) PillChip(if (dev.sharpMask) "SHOW MASK ✓" else "SHOW MASK", dev.sharpMask) { dev.setSharpMask(!dev.sharpMask) }
            Text("Masking keeps sharpening off smooth areas (skin, sky): white in SHOW MASK is sharpened. Pinch or double-tap the photo for a 1:1 view.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("AI NOISE REDUCTION")
            EdSlider("Strength", e.detail.aiDenoise, 0f..1f, format = { if (it <= 0f) "Off" else pct(it) }) { v -> detail { it.copy(aiDenoise = v) } }
            Text("Built-in AI (NAFNet, trained on real phone-camera noise). Runs on the phone; cleaning the full photo takes a little longer when exporting.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("NOISE REDUCTION")
            EdSlider("Luminance & colour", e.detail.denoise, 0f..1f, format = ::pct) { v -> detail { it.copy(denoise = v) } }
            SectionTitle("PRESENCE")
            EdSlider("Texture", e.detail.texture) { v -> detail { it.copy(texture = v) } }
            EdSlider("Clarity", e.detail.clarity) { v -> detail { it.copy(clarity = v) } }
            EdSlider("Dehaze", g.dehaze) { v -> grade { it.copy(dehaze = v) } }
            SectionTitle("DEFRINGE") { detail { it.copy(defringePurple = 0f, defringeGreen = 0f) } }
            EdSlider("Purple fringe", e.detail.defringePurple, 0f..1f, 0f, ::pct) { v -> detail { it.copy(defringePurple = v) } }
            EdSlider("Green fringe", e.detail.defringeGreen, 0f..1f, 0f, ::pct) { v -> detail { it.copy(defringeGreen = v) } }
        }
        PhotoPanel.EFFECTS -> {
            SectionTitle("EFFECTS")
            EdSlider("Vignette", e.detail.vignette) { v -> detail { it.copy(vignette = v) } }
            if (e.detail.vignette != 0f) {
                EdSlider("  Midpoint", e.detail.vigMid, 0f..1f, 0.5f, ::pct) { v -> detail { it.copy(vignetteMid = v - 0.5f) } }
                EdSlider("  Roundness", e.detail.vignetteRound) { v -> detail { it.copy(vignetteRound = v) } }
                EdSlider("  Feather", e.detail.vigFeather, 0f..1f, 0.5f, ::pct) { v -> detail { it.copy(vignetteFeather = v - 0.5f) } }
                EdSlider("  Highlights", e.detail.vignetteHighlights, 0f..1f, 0f, ::pct) { v -> detail { it.copy(vignetteHighlights = v) } }
            }
            EdSlider("Grain", e.detail.grain, 0f..1f, format = ::pct) { v -> detail { it.copy(grain = v) } }
            if (e.detail.grain > 0f) {
                EdSlider("  Size", e.detail.grainSz, 0f..1f, 0.25f, ::pct) { v -> detail { it.copy(grainSize = v - 0.25f) } }
                EdSlider("  Roughness", e.detail.grainRg, 0f..1f, 0.5f, ::pct) { v -> detail { it.copy(grainRough = v - 0.5f) } }
            }
            EdSlider("Fade", g.fade, 0f..1f, format = ::pct) { v -> grade { it.copy(fade = v) } }
            // ---- Lens Blur
            val lb = e.lensBlur
            SectionTitle("LENS BLUR (AI DEPTH)") { set(e.copy(lensBlur = null)) }
            if (lb?.depthFile == null) {
                Text(
                    if (dev?.depthBusy == true) "Working out the depth…" else "ANALYSE DEPTH", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = dev?.depthBusy != true) { dev?.analyseDepth?.invoke() }.padding(10.dp),
                )
                Text("A shallow depth of field like a fast lens: the AI works out how far everything is, then you pick the focus and how strong the blur is.", color = Pro.Dim, fontSize = 11.sp)
            } else {
                fun lbs(f: (LensBlur) -> LensBlur) = set(e.copy(lensBlur = f(lb)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lens blur", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(lb.on, { v -> lbs { it.copy(on = v) } }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                }
                EdSlider("Blur amount", lb.amount, 0f..1f, 0.5f, ::pct) { v -> lbs { it.copy(amount = v) } }
                if (dev != null) PillChip(if (dev.picking == 4) "TAP THE PHOTO TO FOCUS" else "⌖ PICK FOCUS", dev.picking == 4, accentText = true) { dev.setPicking(4) }
                EdSlider("Focus distance", lb.focus, 0f..1f, 0.8f, { if (it > 0.66f) "Near ${pct(it)}" else if (it < 0.33f) "Far ${pct(it)}" else pct(it) }) { v -> lbs { it.copy(focus = v) } }
                EdSlider("Focus range", lb.range, 0f..0.5f, 0.12f, ::pct) { v -> lbs { it.copy(range = v) } }
                Text("Bokeh", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Chips(BOKEH_SHAPES, lb.shape) { i -> lbs { it.copy(shape = i) } }
                EdSlider("Bokeh boost", lb.boost, 0f..1f, 0.4f, ::pct) { v -> lbs { it.copy(boost = v) } }
            }
        }
        PhotoPanel.CROP -> {
            val geom = e.geometry
            SectionTitle("ASPECT") { geo { Geometry() } }
            val aspects = listOf(
                "Original" to 0f, "1:1" to 1f, "4:5" to 0.8f, "5:4" to 1.25f, "4:3" to 4f / 3f, "3:4" to 0.75f, "3:2" to 1.5f, "2:3" to 0.6667f,
                "16:9" to 16f / 9f, "9:16" to 9f / 16f, "21:9" to 21f / 9f, "2.39:1" to 2.39f, "5:7" to 5f / 7f,
            )
            Chips(aspects.map { it.first }, aspects.indexOfFirst { it.second == geom.aspect }.coerceAtLeast(0)) { i -> geo { it.copy(aspect = aspects[i].second) } }
            SectionTitle("UPRIGHT") { dev?.upright?.invoke(-1) }
            if (dev != null) {
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Upright.MODES.forEachIndexed { i, n -> PillChip(n, false, accentText = i == 0) { dev.upright(i) } }
                    PillChip("Off", false) { dev.upright(-1) }
                }
                Text(if (dev.uprightBusy) "Finding the lines…" else "Auto: balanced fix · Level: straightens the horizon · Vertical: also makes buildings upright · Full: vertical and horizontal perspective.", color = if (dev.uprightBusy) Pro.Amber else Pro.Dim, fontSize = 11.sp)
            }
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
                    1 -> set(e.copy(watermark = Layer(kind = LayerKind.WATERMARK, text = "© VeyroCut Pro", size = 0.035f, x = 0.84f, y = 0.93f, opacity = 0.7f, font = LayerFont.LIGHT, wholeVideo = true)))
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
        PhotoPanel.MASKS, PhotoPanel.HEAL, PhotoPanel.LENS, PhotoPanel.HISTORY, PhotoPanel.TEXT -> {}   // drawn by the workspace
        PhotoPanel.EXPORT -> {
            run {
                val ctx = LocalContext.current
                SectionTitle("EXPORT PRESETS")
                var naming by remember { mutableStateOf(false) }
                Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (ExportOpts.builtIn + ExportOpts.user).forEach { p ->
                        PillChip(p.name, ExportOpts.presetName == p.name, onLongClick = if (p in ExportOpts.user) ({ ExportOpts.user.remove(p); ExportOpts.save(ctx) }) else null) {
                            onExportState(p.format, p.quality, p.sizeIdx)
                            ExportOpts.space = p.space; ExportOpts.sharpenFor = p.sharpenFor; ExportOpts.sharpenAmount = p.sharpenAmount; ExportOpts.template = p.template; ExportOpts.presetName = p.name
                            ExportOpts.save(ctx)
                        }
                    }
                    PillChip("+ SAVE", false, accentText = true) { naming = true }
                }
                if (naming) ProjectNameDialog("My export ${ExportOpts.user.size + 1}", onSave = { n ->
                    ExportOpts.user.add(ExportOpts.Preset(n, exportState.format, exportState.quality, exportState.sizeIdx, ExportOpts.space, ExportOpts.sharpenFor, ExportOpts.sharpenAmount, ExportOpts.template))
                    ExportOpts.presetName = n; ExportOpts.save(ctx); naming = false
                }, onDismiss = { naming = false }, title = "Save export preset", info = "Saves the format, size, quality, colour space, output sharpening and file name.", fieldLabel = "Preset name")
            }
            SectionTitle("FORMAT")
            Chips(PHOTO_FORMATS, exportState.format) { onExportState(it, exportState.quality, exportState.sizeIdx) }
            if (exportState.format >= 4) Text("TIFF: uncompressed and lossless (large files), for print and other editing software.", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format == 3) Text("HEIC: about half the size of JPEG (Android 9+).", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format != 1 && exportState.format < 4) EdSlider("Quality", exportState.quality, 0.6f..1f, 0.95f, ::pct) { onExportState(exportState.format, it, exportState.sizeIdx) }
            SectionTitle("SIZE (LONG EDGE)")
            Chips(listOf("Full (≤4800)", "4096", "2560", "1920", "Instagram 1080", "Web 2048", "4K 3840"), exportState.sizeIdx) { onExportState(exportState.format, exportState.quality, it) }
            Text("Exports a new picture to DCIM/VeyroCutPro; the original stays untouched.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            run {
                val ctx = LocalContext.current
                SectionTitle("OUTPUT")
                Text("Colour space (profile embedded)", color = Color.White, fontSize = 13.sp)
                Chips(ExportOpts.SPACES, ExportOpts.space) { ExportOpts.space = it; ExportOpts.save(ctx) }
                Text("Output sharpening", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(ExportOpts.SHARPEN_FOR, ExportOpts.sharpenFor) { ExportOpts.sharpenFor = it; ExportOpts.save(ctx) }
                if (ExportOpts.sharpenFor != 0) Chips(ExportOpts.SHARPEN_AMOUNT, ExportOpts.sharpenAmount) { ExportOpts.sharpenAmount = it; ExportOpts.save(ctx) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Soft proof (matte print)", color = Color.White, fontSize = 13.sp)
                        Text("Shows on screen how a print on matte paper will look: softer blacks and whites.", color = Pro.Dim, fontSize = 11.sp)
                    }
                    androidx.compose.material3.Switch(ExportOpts.proof, { ExportOpts.proof = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                }
                SectionTitle("FILE NAME")
                androidx.compose.material3.OutlinedTextField(
                    value = ExportOpts.template, onValueChange = { ExportOpts.template = it.take(80); ExportOpts.save(ctx) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Template") },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber),
                )
                Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportOpts.TOKENS.forEach { t -> PillChip(t, false) { ExportOpts.template = ExportOpts.template + (if (ExportOpts.template.isEmpty() || t == "{name}") "" else "_") + t; ExportOpts.save(ctx) } }
                }
                Text("{name} original file name · {date} · {time} · {seq} counting number (next: ${ExportOpts.seq}) · {preset} export preset", color = Pro.Dim, fontSize = 11.sp)
            }
            run {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) { PhotoMeta.load(ctx) }
                SectionTitle("CAPTION & KEYWORDS (IPTC)")
                val tf = @Composable { label: String, value: String, single: Boolean, onChange: (String) -> Unit ->
                    androidx.compose.material3.OutlinedTextField(
                        value = value, onValueChange = { onChange(it.take(if (single) 200 else 2000)); PhotoMeta.save(ctx) },
                        label = { Text(label) }, singleLine = single,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                tf("Title / headline", PhotoMeta.title, true) { PhotoMeta.title = it }
                tf("Caption", PhotoMeta.caption, false) { PhotoMeta.caption = it }
                tf("Keywords (comma-separated)", PhotoMeta.keywords, true) { PhotoMeta.keywords = it }
                Text("Written into every exported JPEG / WebP with your name and copyright — read by photo-management software and stock / news agencies. Kept for the next photos until you change it.", color = Pro.Dim, fontSize = 11.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                        .clickable { PhotoMeta.sidecar = !PhotoMeta.sidecar; PhotoMeta.save(ctx) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Also save an .xmp sidecar", color = Color.White, fontSize = 14.sp)
                        Text("Your edit (light, colour, HSL, detail, straighten) as Adobe Camera Raw settings, named after the original, in Documents/VeyroCutPro/XMP. Put it next to the original to continue in other photo software.", color = Pro.Dim, fontSize = 11.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = PhotoMeta.sidecar, onCheckedChange = { PhotoMeta.sidecar = it; PhotoMeta.save(ctx) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber),
                    )
                }
            }
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

/** Saves an edited picture as a new file in DCIM/VeyroCutPro. */
fun savePicture(context: Context, bmp: Bitmap, png: Boolean, quality: Int, area: com.dronedirector.veyrocutpro.camera.StorageArea = com.dronedirector.veyrocutpro.camera.StorageArea.PHOTO): Uri? {
    val name = "EDIT_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + if (png) ".png" else ".jpg"
    // A folder chosen in the editor settings wins; otherwise the default app location.
    SaveFolder.write(context, false, name, if (png) "image/png" else "image/jpeg") {
        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
    }?.let { return it }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/VeyroCutPro")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val target = Storage.insert(context, Storage.load(context, area), values, Storage.Kind.IMAGE) ?: return null
    return try {
        resolver.openOutputStream(target)?.use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
        }
        Storage.publish(context, target)
        target
    } catch (_: Throwable) {
        Storage.discard(context, target)
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
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.controls", "Tools panel", "Presets, Light, Color, Masks, Heal, Export and the other tabs"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.copypaste", "Copy / paste bar", "Copy settings to other photos or video clips"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.scope", "Histogram pull-out", "Show or hide the RGB histogram tab on the right edge of the photo"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.compare", "Before / after buttons", "HOLD FOR BEFORE and SPLIT on the photo"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.filmstrip", "Filmstrip", "The row of imported photos under the photo"),
    )
    val all = panels.map { it.key }.toSet()
    com.dronedirector.veyrocutpro.ui.PanelsButton(
        prefix = "pe",
        panels = panels,
        presets = listOf(
            com.dronedirector.veyrocutpro.ui.PanelPreset("Full", all),
            com.dronedirector.veyrocutpro.ui.PanelPreset("View", emptySet()),
            com.dronedirector.veyrocutpro.ui.PanelPreset("Edit", setOf("pe.controls", "pe.compare")) { onTab(PhotoPanel.LIGHT) },
            com.dronedirector.veyrocutpro.ui.PanelPreset("Color", setOf("pe.controls", "pe.scope", "pe.compare")) { onTab(PhotoPanel.COLOR) },
        ),
        onReset = onReset,
    )
}

/** Megapixels an export will have: long side [longSide] with the photo's shape. */
private fun exportMp(b: Bitmap?, longSide: Int): Float {
    val aspect = if (b != null && b.width > 0 && b.height > 0) minOf(b.width, b.height).toFloat() / maxOf(b.width, b.height) else 0.75f
    return longSide.toFloat() * longSide * aspect / 1e6f
}
