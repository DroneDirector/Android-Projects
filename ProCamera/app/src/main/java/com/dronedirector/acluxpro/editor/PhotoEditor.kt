package com.dronedirector.acluxpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhotoEdit(
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    val geometry: Geometry = Geometry(),
)

private enum class PhotoPanel(val label: String) {
    PRESETS("Presets"), LIGHT("Light"), CURVE("Curve"), COLOR("Color"), MIXER("HSL Mixer"),
    GRADING("Grading"), DETAIL("Detail"), EFFECTS("Effects"), CROP("Crop"), EXPORT("Export"),
}

@Composable
fun PhotoEditorScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) source = uri }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) source = uri }

    val src = source
    if (src == null) {
        BackHandler { onExit() }
        MediaChooser(
            title = "Photo editor",
            subtitle = "Choose a photo to edit. The original is never changed; edits are exported as a new picture.",
            videos = false,
            onBack = onExit,
            onGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFiles = { browse.launch(arrayOf("image/*")) },
            onRecent = { source = it },
        )
    } else {
        PhotoWorkspace(src, onClose = { source = null })
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
) {
    val context = LocalContext.current
    val recent by produceState<List<com.dronedirector.acluxpro.camera.MediaItem>>(emptyList()) {
        value = withContext(Dispatchers.IO) { MediaLibrary.query(context).filter { it.isVideo == videos }.take(60) }
    }
    Column(Modifier.fillMaxSize().background(Ed.Bg).windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        Text(subtitle, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(Icons.Filled.PhotoLibrary, if (videos) "Gallery videos" else "Gallery photos", Modifier.weight(1f), onGallery)
            BigButton(Icons.Filled.FolderOpen, "Browse files", Modifier.weight(1f), onFiles)
        }
        if (videos) {
            Text("Browse files also opens MKV, MOV, WebM and clips from SD cards or USB drives.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
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
private fun PhotoWorkspace(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var edit by remember { mutableStateOf(PhotoEdit()) }
    var panel by remember { mutableStateOf(PhotoPanel.PRESETS) }
    var comparing by remember { mutableStateOf(false) }
    var showScope by remember { mutableStateOf(true) }
    var hist by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf(PhotoEdit()) }
    var undoPos by remember { mutableStateOf(0) }

    // Export options
    var format by remember { mutableStateOf(0) }       // JPEG, PNG
    var quality by remember { mutableStateOf(0.95f) }
    var sizeIdx by remember { mutableStateOf(0) }       // Full, 4096, 2560, 1920, 1080
    val sizes = listOf(4800, 4096, 2560, 1920, 1080)

    BackHandler { if (!busy) onClose() }

    LaunchedEffect(uri) {
        val b = withContext(Dispatchers.IO) { GradeIO.decode(context, uri, 1400) }
        if (b == null) failed = true else base = b
    }
    LaunchedEffect(base, edit) {
        val b = base ?: return@LaunchedEffect
        delay(30)
        val e = edit
        val out = withContext(Dispatchers.Default) {
            val tables = if (e.grade.isIdentity) null else e.grade.tables()
            ImageOps.render(b, e.grade, e.detail, e.geometry, tables)
        }
        preview = out
        hist = withContext(Dispatchers.Default) { ImageOps.histogram(out) }
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
                    val full = GradeIO.decode(context, uri, sizes[sizeIdx]) ?: return@withContext null
                    val tables = if (e.grade.isIdentity) null else e.grade.tables()
                    val out = ImageOps.render(full, e.grade, e.detail, e.geometry, tables)
                    full.recycle()
                    savePicture(context, out, png = format == 1, quality = (quality * 100).toInt())
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: Throwable) {
                    null
                }
            }
            busy = false
            message = if (ok != null) "Saved to DCIM/AcluxPro" else "Couldn't export — try a smaller size"
        }
    }

    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp
            val previewBox = @Composable { mod: Modifier ->
                Box(
                    mod.pointerInput(Unit) {
                        detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = if (comparing) base else preview ?: base
                    when {
                        failed -> Text("This file can't be opened.", color = Pro.Dim)
                        shown != null -> Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                        else -> CircularProgressIndicator(color = Pro.Amber)
                    }
                    if (showScope && !comparing) Scope(hist, Modifier.align(Alignment.TopEnd).padding(8.dp).width(130.dp).height(64.dp))
                    Text(
                        if (comparing) "BEFORE" else "HOLD FOR BEFORE",
                        style = Pro.Label.copy(color = Color.White),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
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
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        PhotoPanelContent(
                            panel, edit, { edit = it },
                            exportState = ExportState(format, quality, sizeIdx),
                            onExportState = { f, q, si -> format = f; quality = q; sizeIdx = si },
                            onExport = { export() },
                            busy = busy,
                        )
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close", enabled = !busy) { onClose() }
                    Text("Photo editor", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    TopIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = undoPos > 0) { undoPos--; edit = undo[undoPos] }
                    TopIcon(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = undoPos < undo.lastIndex) { undoPos++; edit = undo[undoPos] }
                    TopIcon(Icons.Filled.BarChart, "Scope", tint = if (showScope) Pro.Amber else Color.White) { showScope = !showScope }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                        modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !busy) { panel = PhotoPanel.EXPORT }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        previewBox(Modifier.weight(1f).fillMaxHeight())
                        controls(Modifier.width(380.dp).fillMaxHeight())
                    }
                } else {
                    previewBox(Modifier.fillMaxWidth().weight(1f))
                    controls(Modifier.fillMaxWidth().height(330.dp))
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
) {
    val g = e.grade
    fun grade(f: (ProGrade) -> ProGrade) = set(e.copy(grade = f(g)))
    fun detail(f: (Detail) -> Detail) = set(e.copy(detail = f(e.detail)))
    fun geo(f: (Geometry) -> Geometry) = set(e.copy(geometry = f(e.geometry)))

    when (panel) {
        PhotoPanel.PRESETS -> {
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
            val aspects = listOf("Original" to 0f, "1:1" to 1f, "4:5" to 0.8f, "5:4" to 1.25f, "3:2" to 1.5f, "2:3" to 0.6667f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "2.39:1" to 2.39f)
            Chips(aspects.map { it.first }, aspects.indexOfFirst { it.second == geom.aspect }.coerceAtLeast(0)) { i -> geo { it.copy(aspect = aspects[i].second) } }
            SectionTitle("ROTATE & FLIP")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Filled.RotateRight, "Rotate") { geo { it.copy(rotate90 = it.rotate90 + 1) } }
                SmallAction(Icons.Filled.Flip, "Flip H") { geo { it.copy(flipH = !it.flipH) } }
                SmallAction(Icons.Filled.Flip, "Flip V") { geo { it.copy(flipV = !it.flipV) } }
            }
            EdSlider("Straighten", geom.straighten, -45f..45f, format = { "%+.1f°".format(it) }) { v -> geo { it.copy(straighten = v) } }
            SectionTitle("FRAMING")
            EdSlider("Zoom", geom.zoom, 1f..4f, 1f, { "%.2fx".format(it) }) { v -> geo { it.copy(zoom = v) } }
            EdSlider("Horizontal", geom.offsetX) { v -> geo { it.copy(offsetX = v) } }
            EdSlider("Vertical", geom.offsetY) { v -> geo { it.copy(offsetY = v) } }
        }
        PhotoPanel.EXPORT -> {
            SectionTitle("FORMAT")
            Chips(listOf("JPEG", "PNG (lossless)"), exportState.format) { onExportState(it, exportState.quality, exportState.sizeIdx) }
            if (exportState.format == 0) EdSlider("Quality", exportState.quality, 0.6f..1f, 0.95f, ::pct) { onExportState(exportState.format, it, exportState.sizeIdx) }
            SectionTitle("SIZE (LONG EDGE)")
            Chips(listOf("Full (≤4800)", "4096", "2560", "1920", "1080"), exportState.sizeIdx) { onExportState(exportState.format, exportState.quality, it) }
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
