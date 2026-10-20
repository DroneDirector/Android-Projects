package com.dronedirector.veyrocutpro.editor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LIB_LABELS = listOf(0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2).map { it.toInt() }

/**
 * The photo library: every photo on the phone with ratings, flags, colour labels, keywords,
 * albums and smart albums, a search that also finds what's IN the photos (AI labels), info
 * (camera details), compare (two side by side) and survey (several at once), and editing many
 * photos together in the filmstrip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onBack: () -> Unit, onEdit: (List<Uri>) -> Unit, onVirtualCopy: (Uri) -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { LibraryStore.load(context) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var reload by remember { mutableStateOf(0) }
    val all by produceState(emptyList<LibPhoto>(), granted, reload) { value = withContext(Dispatchers.IO) { LibraryStore.photos(context) } }
    // AI labels in the background so photos can be found by what's in them.
    var stopIndex by remember { mutableStateOf(false) }
    LaunchedEffect(all) { if (all.isNotEmpty()) withContext(Dispatchers.Default) { LibraryStore.index(context, all) { stopIndex } } }
    DisposableEffect(Unit) { onDispose { stopIndex = true } }

    var text by remember { mutableStateOf("") }
    var minRating by remember { mutableStateOf(0) }
    var flagF by remember { mutableStateOf(2) }      // 0 any, 1 picks, 2 hide rejects, 3 rejects only
    var labelF by remember { mutableStateOf<Int?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var smart by remember { mutableStateOf<SmartAlbum?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    var detail by remember { mutableStateOf<LibPhoto?>(null) }
    var compare by remember { mutableStateOf<List<LibPhoto>?>(null) }
    var survey by remember { mutableStateOf<List<LibPhoto>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }   // "album", "keywords", "newAlbum", "newSmart"

    val shown = all.filter { p ->
        val m = LibraryStore.get(p.uri.toString())
        val s = smart
        (s == null || LibraryStore.matchesSmart(p, s)) &&
            (album == null || album in m.albums) &&
            m.rating >= minRating &&
            when (flagF) { 1 -> m.flag == 1; 2 -> m.flag != -1; 3 -> m.flag == -1; else -> true } &&
            (labelF == null || m.label == labelF) &&
            LibraryStore.matches(p, text)
    }
    val sel = all.filter { it.uri.toString() in picked }

    Column(Modifier.fillMaxSize().background(Ed.Bg).windowInsetsPadding(WindowInsets.displayCutout).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Text("Library", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
            LibraryStore.indexing?.let { (d, t) -> Text("AI ${d}/${t}", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(end = 8.dp)) }
            Text(if (selecting) "DONE" else "SELECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selecting = !selecting; picked.clear() }.padding(8.dp))
        }
        if (!granted) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.12f)).clickable { ask.launch(perm) }.padding(12.dp)) {
                Text("Allow access to your photos to see the whole library (now only VeyroCut Pro's own photos are shown). Tap to allow.", color = Color.White, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search: dog, beach, sunset, a keyword or a file name…", color = Pro.Dim, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, cursorColor = Pro.Amber),
        )
        // Filters.
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PillChip(if (minRating == 0) "★ Any" else "★ $minRating+", minRating > 0) { minRating = (minRating + 1) % 6 }
            PillChip("Picks", flagF == 1) { flagF = if (flagF == 1) 2 else 1 }
            PillChip("Rejected", flagF == 3) { flagF = if (flagF == 3) 2 else 3 }
            PillChip("Show rejects", flagF == 0) { flagF = if (flagF == 0) 2 else 0 }
            LIB_LABELS.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (labelF == c) Color.White else Color.Transparent, CircleShape).clickable { labelF = if (labelF == c) null else c }) }
        }
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("All photos", album == null && smart == null) { album = null; smart = null }
            LibraryStore.albums.forEach { a -> PillChip("▣ $a", album == a, onLongClick = { LibraryStore.albums.remove(a); LibraryStore.save(context); if (album == a) album = null }) { album = a; smart = null } }
            LibraryStore.smart.forEach { s -> PillChip("⚙ ${s.name}", smart == s, onLongClick = { LibraryStore.smart.remove(s); LibraryStore.save(context); if (smart == s) smart = null }) { smart = s; album = null } }
            PillChip("+ Album", false, accentText = true) { dialog = "newAlbum" }
            PillChip("+ Smart album", false, accentText = true) { dialog = "newSmart" }
        }
        if (selecting) Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${picked.size} selected", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp))
            PillChip("Select all", false) { picked.clear(); picked.addAll(shown.map { it.uri.toString() }) }
            if (picked.isNotEmpty()) {
                PillChip("✎ Edit", false, accentText = true) { onEdit(sel.map { it.uri }) }
                if (picked.size == 2) PillChip("Compare", false) { compare = sel }
                if (picked.size in 2..12) PillChip("Survey", false) { survey = sel }
                (1..5).forEach { r -> PillChip("★$r", false) { LibraryStore.update(context, picked) { it.copy(rating = r) } } }
                PillChip("⚑ Pick", false) { LibraryStore.update(context, picked) { it.copy(flag = 1) } }
                PillChip("✕ Reject", false) { LibraryStore.update(context, picked) { it.copy(flag = -1) } }
                PillChip("Unflag", false) { LibraryStore.update(context, picked) { it.copy(flag = 0) } }
                PillChip("+ Album", false) { dialog = "album" }
                PillChip("Keywords", false) { dialog = "keywords" }
            }
        }
        Text("${shown.size} photo" + (if (shown.size == 1) "" else "s"), style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(bottom = 4.dp))
        LazyVerticalGrid(GridCells.Adaptive(104.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.uri.toString() }) { p ->
                val k = p.uri.toString()
                val m = LibraryStore.get(k)
                val on = k in picked
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised)
                        .border(if (on) 2.dp else 0.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = { if (selecting) { if (on) picked.remove(k) else picked.add(k) } else detail = p },
                            onLongClick = { selecting = true; if (!on) picked.add(k) },
                        ),
                ) {
                    LibThumb(p.uri, 256)
                    if (m.flag == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.rating > 0) Text("★".repeat(m.rating), color = Pro.Amber, fontSize = 8.sp)
                        Box(Modifier.weight(1f))
                        if (m.flag == 1) Text("⚑", color = Color.White, fontSize = 9.sp)
                        m.label?.let { Box(Modifier.padding(start = 2.dp).size(7.dp).clip(CircleShape).background(Color(it))) }
                    }
                    if (selecting) Text(if (on) "☑" else "☐", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart).padding(3.dp))
                }
            }
        }
    }

    // ---- Dialogs
    when (dialog) {
        "newAlbum", "album" -> NameDialog(if (dialog == "album") "Add to album" else "New album", LibraryStore.albums.toList()) { name ->
            dialog = null
            if (name != null) {
                if (name !in LibraryStore.albums) LibraryStore.albums.add(name)
                if (picked.isNotEmpty()) LibraryStore.update(context, picked) { it.copy(albums = (it.albums + name).distinct()) } else LibraryStore.save(context)
            }
        }
        "keywords" -> NameDialog("Add keywords (comma separated)", emptyList()) { t ->
            dialog = null
            val kws = t?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()
            if (kws.isNotEmpty()) LibraryStore.update(context, picked) { it.copy(keywords = (it.keywords + kws).distinct()) }
        }
        "newSmart" -> NameDialog("Smart album name (uses the filters and search now set)", emptyList()) { name ->
            dialog = null
            if (name != null) { LibraryStore.smart.add(SmartAlbum(name, minRating, if (flagF == 1) 1 else if (flagF == 2) 2 else 0, labelF, text)); LibraryStore.save(context) }
        }
    }
    detail?.let { p -> PhotoDetail(p, onClose = { detail = null }, onEdit = { detail = null; onEdit(listOf(p.uri)) }, onVirtualCopy = { detail = null; onVirtualCopy(p.uri) }) }
    compare?.let { list -> CompareView(list, onClose = { compare = null }) }
    survey?.let { list -> SurveyView(list, onClose = { survey = null }, onEdit = { survey = null; onEdit(it) }) }
}

@Composable
private fun LibThumb(uri: Uri, size: Int, crop: Boolean = true) {
    val context = LocalContext.current
    val img by produceState<ImageBitmap?>(null, uri, size) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29 && size <= 512) context.contentResolver.loadThumbnail(uri, android.util.Size(size, size), null).asImageBitmap()
                else GradeIO.decode(context, uri, size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        }
    }
    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
}

@Composable
private fun NameDialog(title: String, existing: List<String>, onDone: (String?) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(t, { t = it.take(60) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, cursorColor = Pro.Amber))
                if (existing.isNotEmpty()) Row(Modifier.hintedScroll(horizontal = true).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    existing.forEach { e -> PillChip(e, false) { onDone(e) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(t.trim().ifBlank { null }) }) { Text("OK", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel", color = Pro.Dim) } },
    )
}

/** One photo large, with its rating, flag, label, keywords, albums and camera details. */
@Composable
private fun PhotoDetail(p: LibPhoto, onClose: () -> Unit, onEdit: () -> Unit, onVirtualCopy: () -> Unit) {
    val context = LocalContext.current
    val k = p.uri.toString()
    val m = LibraryStore.get(k)
    val info by produceState(emptyList<Pair<String, String>>(), p) { value = withContext(Dispatchers.IO) { LibraryStore.exif(context, p) } }
    var kw by remember(k) { mutableStateOf(m.keywords.joinToString(", ")) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ed.Bg).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) { LibThumb(p.uri, 1600, crop = false) }
            Column(Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
                    (0..5).forEach { r -> PillChip(if (r == 0) "☆" else "★$r", m.rating == r) { LibraryStore.update(context, listOf(k)) { it.copy(rating = r) } } }
                    PillChip("⚑ Pick", m.flag == 1) { LibraryStore.update(context, listOf(k)) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                    PillChip("✕ Reject", m.flag == -1) { LibraryStore.update(context, listOf(k)) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { LibraryStore.update(context, listOf(k)) { it.copy(label = null) } })
                    LIB_LABELS.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (m.label == c) Color.White else Color.Transparent, CircleShape).clickable { LibraryStore.update(context, listOf(k)) { it.copy(label = c) } }) }
                }
                OutlinedTextField(kw, { kw = it; LibraryStore.update(context, listOf(k)) { mm -> mm.copy(keywords = it.split(',').map { s -> s.trim().lowercase() }.filter { s -> s.isNotEmpty() }) } },
                    label = { Text("Keywords (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
                if (LibraryStore.albums.isNotEmpty()) Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryStore.albums.forEach { a -> PillChip("▣ $a", a in m.albums) { LibraryStore.update(context, listOf(k)) { it.copy(albums = if (a in it.albums) it.albums - a else it.albums + a) } } }
                }
                m.ai?.takeIf { it.isNotEmpty() }?.let { Text("AI sees: " + it.joinToString(", "), color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                SectionTitle("INFO")
                info.forEach { (a, b) -> Row(Modifier.padding(vertical = 1.dp)) { Text(a, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.width(110.dp)); Text(b, color = Color.White, fontSize = 12.sp) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PillChip("✎ EDIT", false, accentText = true) { onEdit() }
                PillChip("VIRTUAL COPY", false) { onVirtualCopy() }
            }
        }
    }
}

/** Two photos side by side (stacked when upright), zoomed and moved together. */
@Composable
private fun CompareView(list: List<LibPhoto>, onClose: () -> Unit) {
    val context = LocalContext.current
    var z by remember { mutableStateOf(1f) }
    var px by remember { mutableStateOf(0f) }
    var py by remember { mutableStateOf(0f) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            val wide = maxWidth > maxHeight
            val pane = @Composable { p: LibPhoto, mod: Modifier ->
                Column(mod.padding(4.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ -> z = (z * zoom).coerceIn(1f, 8f); px += pan.x; py += pan.y; if (z <= 1.001f) { px = 0f; py = 0f } }
                    }) {
                        Box(Modifier.fillMaxSize().graphicsLayer(scaleX = z, scaleY = z, translationX = px, translationY = py)) { LibThumb(p.uri, 2000, crop = false) }
                    }
                    val m = LibraryStore.get(p.uri.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PillChip("⚑ Pick", m.flag == 1) { LibraryStore.update(context, listOf(p.uri.toString())) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                        PillChip("✕", m.flag == -1) { LibraryStore.update(context, listOf(p.uri.toString())) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("COMPARE · pinch to zoom both", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
                }
                if (wide) Row(Modifier.fillMaxWidth().weight(1f)) { pane(list[0], Modifier.weight(1f)); pane(list[1], Modifier.weight(1f)) }
                else Column(Modifier.fillMaxWidth().weight(1f)) { pane(list[0], Modifier.weight(1f)); pane(list[1], Modifier.weight(1f)) }
            }
        }
    }
}

/** Several photos at once to choose from: drop the ones you don't want, keep the rest. */
@Composable
private fun SurveyView(list: List<LibPhoto>, onClose: () -> Unit, onEdit: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val left = remember { mutableStateListOf<LibPhoto>().apply { addAll(list) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SURVEY · ${left.size} left — tap ✕ to drop one", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            val cols = when { left.size <= 2 -> 2; left.size <= 6 -> 3; else -> 4 }
            LazyVerticalGrid(GridCells.Fixed(cols), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(left.toList(), key = { it.uri.toString() }) { p ->
                    Box(Modifier.aspectRatio(0.8f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised)) {
                        LibThumb(p.uri, 900, crop = false)
                        Text("✕", color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { left.remove(p) }.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PillChip("⚑ PICK THESE", false, accentText = true) { LibraryStore.update(context, left.map { it.uri.toString() }) { it.copy(flag = 1) }; onClose() }
                PillChip("✎ EDIT THESE", false, accentText = true) { onEdit(left.map { it.uri }) }
            }
        }
    }
}
