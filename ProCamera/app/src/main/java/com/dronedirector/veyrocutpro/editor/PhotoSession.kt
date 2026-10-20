package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.mouseActions
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One photo in the editor's filmstrip: its file, its own edit (a hidden project), rating, flag and colour label. */
data class SessionPhoto(
    val uri: String,
    val name: String = "",
    val projectId: String? = null,
    /** 0–5 stars; flag 1 = pick, −1 = reject; label = ARGB colour or null. */
    val rating: Int = 0,
    val flag: Int = 0,
    val label: Int? = null,
    val edited: Boolean = false,
    /** Bumped when the edit is saved, so the thumbnail is drawn again. */
    val version: Long = 0L,
)

/**
 * The photo editor's working set, like a desktop filmstrip: many photos imported at once, each with
 * its own edit. Kept until cleared, so you can come back later and carry on.
 */
object PhotoSession {
    val items = mutableStateListOf<SessionPhoto>()
    var current by mutableStateOf(-1)
    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "photo_session.json")

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(context)
            if (!f.exists()) return
            val t = object : TypeToken<List<SessionPhoto>>() {}.type
            val list: List<SessionPhoto> = ProjectStore.gson.fromJson(f.readText(), t) ?: emptyList()
            items.clear(); items.addAll(list.map { Defaults.fix(it, SessionPhoto("")); it })
        } catch (_: Throwable) {}
    }

    fun save(context: Context) {
        try { file(context).writeText(ProjectStore.gson.toJson(items.toList())) } catch (_: Throwable) {}
    }

    /** Adds photos (skipping ones already there); returns the index of the first new one. */
    fun add(context: Context, uris: List<Uri>): Int {
        load(context)
        var first = -1
        for (u in uris) {
            try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val s = u.toString()
            val at = items.indexOfFirst { it.uri == s }
            if (at >= 0) { if (first < 0) first = at; continue }
            items.add(SessionPhoto(s, displayName(context, u)))
            if (first < 0) first = items.lastIndex
        }
        save(context)
        return first
    }

    fun update(context: Context, i: Int, f: (SessionPhoto) -> SessionPhoto) {
        if (i !in items.indices) return
        items[i] = f(items[i]); save(context)
    }

    fun remove(context: Context, idx: Collection<Int>) {
        val keep = items.filterIndexed { i, _ -> i !in idx }
        val cur = items.getOrNull(current)
        // The hidden per-photo edits go too.
        idx.mapNotNull { items.getOrNull(it)?.projectId }.forEach { ProjectStore.delete(context, false, it) }
        items.clear(); items.addAll(keep)
        current = cur?.let { c -> items.indexOfFirst { it.uri == c.uri } } ?: -1
        save(context)
    }

    fun clear(context: Context) { remove(context, items.indices.toList()) }

    private fun displayName(context: Context, u: Uri): String = try {
        context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Throwable) { null } ?: u.lastPathSegment ?: "Photo"

    /** The edited look (if saved) or the photo itself, small. */
    fun thumb(context: Context, p: SessionPhoto): Bitmap? {
        p.projectId?.let { id -> ProjectStore.thumbFile(context, id)?.let { f -> BitmapFactory.decodeFile(f.path)?.let { return it } } }
        return try { com.dronedirector.veyrocutpro.camera.GradeIO.decode(context, Uri.parse(p.uri), 240) } catch (_: Throwable) { null }
    }
}

private val LABELS = listOf(0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2).map { it.toInt() }

/**
 * The filmstrip under the photo: tap to switch photos, hold for stars / flag / label, SELECT to work
 * on several (sync the current edit, rate, flag, export, remove). [current] = index being edited.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Filmstrip(
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onSync: (List<Int>) -> Unit,
    onExport: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    thumbSize: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val context = LocalContext.current
    val items = PhotoSession.items
    var selecting by remember { mutableStateOf(false) }
    // Everything here works by the photo's file (uri), never by its position: positions change when photos are removed.
    val picked = remember { mutableStateListOf<String>() }
    var filter by remember { mutableStateOf(0) }   // 0 all, 1 ★3+, 2 picks, 3 edited, 4 not rejected
    var menuFor by remember { mutableStateOf<String?>(null) }
    fun idx(uri: String) = items.indexOfFirst { it.uri == uri }
    fun upd(uri: String, f: (SessionPhoto) -> SessionPhoto) { val k = idx(uri); if (k >= 0) PhotoSession.update(context, k, f) }
    fun indices(uris: Collection<String>) = uris.map { idx(it) }.filter { it >= 0 }
    val shown = items.toList().filter { p ->
        when (filter) { 1 -> p.rating >= 3; 2 -> p.flag == 1; 3 -> p.edited; 4 -> p.flag != -1; else -> true }
    }
    val curUri = items.getOrNull(PhotoSession.current)?.uri
    // Drop selections of photos that are gone.
    LaunchedEffect(items.size) { picked.retainAll(items.map { it.uri }.toSet()); if (menuFor != null && idx(menuFor!!) < 0) menuFor = null }
    val state = rememberLazyListState()
    LaunchedEffect(curUri, filter) { val k = shown.indexOfFirst { it.uri == curUri }; if (k >= 0) state.animateScrollToItem((k - 2).coerceAtLeast(0)) }
    Column(modifier.fillMaxWidth().background(Color(0xFF0B0D12))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (PhotoSession.current >= 0) "${PhotoSession.current + 1} / ${items.size}" else "${items.size}", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(end = 8.dp))
            listOf("All", "★3+", "Picks", "Edited", "No rejects").forEachIndexed { i, n ->
                Text(n, style = Pro.Label.copy(color = if (filter == i) Pro.Amber else Pro.Dim, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = { filter = i }).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Box(Modifier.weight(1f))
            Text(if (selecting) "DONE" else "SELECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = { selecting = !selecting; picked.clear() }).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        if (selecting) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${picked.size} selected", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp))
            Chip("Select all") { picked.clear(); picked.addAll(shown.map { it.uri }) }
            if (picked.isNotEmpty()) {
                Chip("Sync edit") { onSync(indices(picked)) }
                Chip("Export") { onExport(indices(picked)) }
                Chip("★★★") { picked.toList().forEach { u -> upd(u) { it.copy(rating = 3) } } }
                Chip("Pick") { picked.toList().forEach { u -> upd(u) { it.copy(flag = 1) } } }
                Chip("Reject") { picked.toList().forEach { u -> upd(u) { it.copy(flag = -1) } } }
                Chip("Remove") { val list = indices(picked); picked.clear(); PhotoSession.remove(context, list) }
            }
        }
        if (items.isEmpty()) Text("No photos in the filmstrip — tap + to add some (you can pick many at once).", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(horizontal = 8.dp))
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(shown, key = { it.uri }) { p ->
                val u = p.uri
                val cur = u == curUri
                val sel = u in picked
                val bmp by produceState<Bitmap?>(null, p.uri, p.projectId, p.version) { value = withContext(Dispatchers.IO) { PhotoSession.thumb(context, p) } }
                Box(
                    Modifier.size(thumbSize).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1E26))
                        .border(if (cur || sel) 2.dp else 1.dp, if (sel) Color.White else if (cur) Pro.Amber else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .mouseActions(onRightClick = { menuFor = u })
                        .combinedClickable(
                            onClick = { if (selecting) { if (sel) picked.remove(u) else picked.add(u) } else if (!cur) { val k = idx(u); if (k >= 0) onOpen(k) } },
                            onLongClick = { menuFor = u },
                        ),
                ) {
                    bmp?.let { b -> val img = remember(b) { b.asImageBitmap() }; Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (p.flag == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.rating > 0) Text("★".repeat(p.rating), color = Pro.Amber, fontSize = 7.sp)
                        Box(Modifier.weight(1f))
                        if (p.flag == 1) Text("⚑", color = Color.White, fontSize = 8.sp)
                        if (p.flag == -1) Text("✕", color = Pro.Red, fontSize = 8.sp)
                        p.label?.let { Box(Modifier.padding(start = 2.dp).size(6.dp).clip(CircleShape).background(Color(it))) }
                    }
                    if (p.edited) Text("✎", color = Pro.Amber, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
                    if (selecting) Text(if (sel) "☑" else "☐", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
                    DropdownMenu(expanded = menuFor == u, onDismissRequest = { menuFor = null }, containerColor = Pro.PanelSolid) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(p.name, style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                (0..5).forEach { r -> Chip(if (r == 0) "☆" else "★$r", p.rating == r) { upd(u) { it.copy(rating = r) } } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                Chip("⚑ Pick", p.flag == 1) { upd(u) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                                Chip("✕ Reject", p.flag == -1) { upd(u) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(20.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).combinedClickable(onClick = { upd(u) { it.copy(label = null) } }))
                                LABELS.forEach { c -> Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (p.label == c) Color.White else Color.Transparent, CircleShape).combinedClickable(onClick = { upd(u) { it.copy(label = c) } })) }
                            }
                            Chip("Remove from strip") {
                                // Close the menu first, then remove on the next frame (the menu must not outlive its photo).
                                menuFor = null
                                val k = idx(u)
                                if (k >= 0) PhotoSession.remove(context, listOf(k))
                            }
                        }
                    }
                }
            }
            item {
                Box(
                    Modifier.size(thumbSize).clip(RoundedCornerShape(6.dp)).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).combinedClickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Pro.Amber, fontSize = 26.sp) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = Pro.Label.copy(color = if (on) Color.Black else Color.White, fontSize = 10.sp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Pro.Amber else Color.White.copy(alpha = 0.08f))
            .combinedClickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
