package com.dronedirector.acluxpro.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.dronedirector.acluxpro.camera.MediaItem
import com.dronedirector.acluxpro.camera.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private enum class GalleryTab(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), FAVORITES("♥") }

/**
 * In-app gallery of everything AcluxPro saved: a grid with All / Photos / Videos tabs and a
 * full-screen swipe viewer (pinch / double-tap zoom, video playback, share, delete, open in another app).
 */
@Composable
fun GalleryScreen(onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var tab by remember { mutableStateOf(GalleryTab.ALL) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var confirmItem by remember { mutableStateOf<MediaItem?>(null) }
    var favorites by remember { mutableStateOf(com.dronedirector.acluxpro.camera.Favorites.all(context)) }
    var grading by remember { mutableStateOf<MediaItem?>(null) }

    fun reload() { reloadKey++ }

    // Reading other files in the folder (e.g. after a reinstall) needs media read access.
    val readPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }
    LaunchedEffect(Unit) {
        val missing = readPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permLauncher.launch(readPermissions)
    }

    LaunchedEffect(reloadKey) {
        items = withContext(Dispatchers.IO) { MediaLibrary.query(context) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            // On Android 10 the system only grants permission; retry the delete now.
            if (Build.VERSION.SDK_INT == 29) pendingDelete?.let { runCatching { context.contentResolver.delete(it.uri, null, null) } }
            onChanged()
        }
        pendingDelete = null
        reload()
    }

    fun delete(item: MediaItem) {
        scope.launch {
            val needsSystem: IntentSenderRequest? = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(item.uri, null, null)
                    com.dronedirector.acluxpro.camera.Favorites.remove(context, item.uri)
                    null
                } catch (e: SecurityException) {
                    when {
                        Build.VERSION.SDK_INT >= 30 ->
                            IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri)).intentSender).build()
                        Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException ->
                            IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                        else -> null
                    }
                }
            }
            if (needsSystem != null) {
                pendingDelete = item
                deleteLauncher.launch(needsSystem)
            } else {
                onChanged()
                reload()
            }
        }
    }

    val all = items ?: emptyList()
    val shown = when (tab) {
        GalleryTab.ALL -> all
        GalleryTab.PHOTOS -> all.filter { !it.isVideo }
        GalleryTab.VIDEOS -> all.filter { it.isVideo }
        GalleryTab.FAVORITES -> all.filter { it.uri.toString() in favorites }
    }

    BackHandler { if (viewing != null) viewing = null else onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            // Swallow touches so nothing reaches the camera underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            GalleryHeader(count = all.size, onBack = onClose)
            TabRow(tab, all, favorites) { tab = it }
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pro.Amber)
                }
                shown.isEmpty() -> EmptyGallery(tab)
                else -> MediaGrid(shown, favorites) { viewing = it }
            }
        }

        val v = viewing
        if (v != null && shown.isNotEmpty()) {
            MediaViewer(
                items = shown,
                start = v.coerceIn(0, shown.lastIndex),
                onClose = { viewing = null },
                onDelete = { confirmItem = it },
                favorites = favorites,
                onFavorite = { item ->
                    com.dronedirector.acluxpro.camera.Favorites.toggle(context, item.uri)
                    favorites = com.dronedirector.acluxpro.camera.Favorites.all(context)
                },
                onGrade = { grading = it },
            )
        }

        grading?.let { g ->
            GradeEditor(
                item = g,
                onClose = { grading = null },
                onSaved = { reload(); onChanged() },
            )
        }

        val confirm = confirmItem
        if (confirm != null) {
            AlertDialog(
                onDismissRequest = { confirmItem = null },
                containerColor = Pro.PanelSolid,
                title = { Text(if (confirm.isVideo) "Delete video?" else "Delete photo?", color = Color.White) },
                text = { Text("${confirm.name} will be permanently removed from your device.", color = Pro.Dim) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmItem = null
                        // Leave the viewer if it was the last item in the list.
                        if (shown.size <= 1) viewing = null
                        delete(confirm)
                    }) { Text("Delete", color = Pro.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmItem = null }) { Text("Cancel", color = Color.White) }
                },
            )
        }
    }
}

@Composable
private fun GalleryHeader(count: Int, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera", tint = Color.White) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("Gallery", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("AcluxPro · $count ${if (count == 1) "item" else "items"}", style = Pro.Label)
        }
    }
}

@Composable
private fun TabRow(tab: GalleryTab, all: List<MediaItem>, favorites: Set<String>, onTab: (GalleryTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GalleryTab.entries.forEach { t ->
            val n = when (t) {
                GalleryTab.ALL -> all.size
                GalleryTab.PHOTOS -> all.count { !it.isVideo }
                GalleryTab.VIDEOS -> all.count { it.isVideo }
                GalleryTab.FAVORITES -> all.count { it.uri.toString() in favorites }
            }
            val sel = t == tab
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Pro.Amber else Color.White.copy(alpha = 0.08f))
                    .border(1.dp, if (sel) Pro.Amber else Pro.Line, RoundedCornerShape(50))
                    .clickable { onTab(t) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    "${t.label}  $n",
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyGallery(tab: GalleryTab) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(48.dp))
            Text(
                when (tab) {
                    GalleryTab.ALL -> "No photos or videos yet"
                    GalleryTab.PHOTOS -> "No photos yet"
                    GalleryTab.VIDEOS -> "No videos yet"
                    GalleryTab.FAVORITES -> "No favourites yet"
                },
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(if (tab == GalleryTab.FAVORITES) "Open a photo or video and tap the heart to add it here." else "Everything you shoot with AcluxPro shows up here.", color = Pro.Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, favorites: Set<String>, onOpen: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var lastDay = ""
        items.forEachIndexed { index, item ->
            val day = dayLabel(item.dateTaken)
            if (day != lastDay) {
                lastDay = day
                item(key = "h$day$index", span = { GridItemSpan(maxLineSpan) }) {
                    Text(day, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
                }
            }
            item(key = item.uri.toString()) { GridCell(item, item.uri.toString() in favorites) { onOpen(index) } }
        }
    }
}

@Composable
private fun GridCell(item: MediaItem, favorite: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceThumb(context, item.uri, 320)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
    ) {
        val t = thumb
        if (t != null) Image(t, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (favorite) {
            Icon(
                Icons.Filled.Favorite, contentDescription = "Favourite", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp),
            )
        }
        if (item.isVideo) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                )
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(duration(item.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun produceThumb(context: Context, uri: Uri, px: Int) =
    androidx.compose.runtime.produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, Size(px, px), null).asImageBitmap()
                } else {
                    decodeImage(context, uri, px)?.asImageBitmap()
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(
    items: List<MediaItem>,
    start: Int,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    favorites: Set<String>,
    onFavorite: (MediaItem) -> Unit,
    onGrade: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { items.size }
    var chrome by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.uri?.toString() ?: it },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            if (item.isVideo) {
                VideoPage(item, active = pager.currentPage == page, onToggleChrome = { chrome = !chrome })
            } else {
                PhotoPage(item, onToggleChrome = { chrome = !chrome })
            }
        }

        val current = items.getOrNull(pager.currentPage)
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent))
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to gallery", tint = Color.White) }
                if (current != null) {
                    Column(Modifier.weight(1f)) {
                        Text(fullDate(current.dateTaken), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(details(context, current), style = Pro.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${pager.currentPage + 1} / ${items.size}", style = Pro.Value.copy(color = Pro.Amber), modifier = Modifier.padding(end = 12.dp))
                }
            }
        }

        AnimatedVisibility(chrome && current != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
                    .padding(top = 28.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                current ?: return@Row
                val fav = current.uri.toString() in favorites
                ViewerAction(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Favourite", tint = if (fav) Pro.Red else Color.White,
                ) { onFavorite(current) }
                ViewerAction(Icons.Filled.Tune, "Grade", tint = Pro.Amber) { onGrade(current) }
                ViewerAction(Icons.Filled.Share, "Share") { share(context, current) }
                ViewerAction(Icons.AutoMirrored.Filled.OpenInNew, "Open") { openWith(context, current) }
                ViewerAction(Icons.Filled.Delete, "Delete", tint = Pro.Red) { onDelete(current) }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun PhotoPage(item: MediaItem, onToggleChrome: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val target = maxOf(constraints.maxWidth, constraints.maxHeight).coerceAtMost(3072)
        val bitmap by androidx.compose.runtime.produceState<ImageBitmap?>(null, item.uri) {
            value = withContext(Dispatchers.IO) { decodeImage(context, item.uri, target)?.asImageBitmap() }
        }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun clamp(o: Offset, s: Float) = Offset(
            o.x.coerceIn(-w * (s - 1f) / 2f, w * (s - 1f) / 2f),
            o.y.coerceIn(-h * (s - 1f) / 2f, h * (s - 1f) / 2f),
        )
        val b = bitmap
        if (b == null) {
            CircularProgressIndicator(color = Pro.Amber)
        } else {
            Image(
                b,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.uri) {
                        detectTapGestures(
                            onTap = { onToggleChrome() },
                            onDoubleTap = { tap ->
                                if (scale > 1f) {
                                    scale = 1f; offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    offset = clamp(Offset((w / 2f - tap.x) * 1.5f, (h / 2f - tap.y) * 1.5f), scale)
                                }
                            },
                        )
                    }
                    .pointerInput(item.uri) {
                        // Pinch to zoom; pan while zoomed. Single-finger swipes at 1x go to the pager.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed > 1 || scale > 1.01f) {
                                    val zoom = event.calculateZoom()
                                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (newScale <= 1f) Offset.Zero else clamp(offset + event.calculatePan(), newScale)
                                    scale = newScale
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )
        }
    }
}

@Composable
private fun VideoPage(item: MediaItem, active: Boolean, onToggleChrome: () -> Unit) {
    val context = LocalContext.current
    var playing by remember(item.uri) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) playing = false }
    val poster by produceThumb(context, item.uri, 1024)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(item.uri)
                        setOnPreparedListener { mp -> mp.isLooping = false; start(); controller.show(2500) }
                        setOnCompletionListener { playing = false }
                        setOnErrorListener { _, _, _ -> playing = false; true }
                    }
                },
                onRelease = { it.stopPlayback() },
                // VideoView sizes itself to the largest fit for the clip (portrait or landscape).
                modifier = Modifier.wrapContentSize(),
            )
        } else {
            val p = poster
            if (p != null) {
                Image(
                    p, contentDescription = item.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
                )
            }
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(2.dp, Pro.Amber, CircleShape)
                    .clickable { playing = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Text(
                duration(item.durationMs),
                style = Pro.Value,
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp),
            )
        }
    }
}

// ---- helpers ----

private fun decodeImage(context: Context, uri: Uri, maxPx: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxPx) {
                val f = maxPx.toFloat() / longest
                decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        val rotation = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (bmp != null && rotation != 0) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } else bmp
    }
} catch (_: Throwable) {
    null
}

private fun share(context: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mime)
        .putExtra(Intent.EXTRA_STREAM, item.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openWith(context: Context, item: MediaItem) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun duration(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun dayLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 3600 * 1000
    val today = DateFormat.format("yyyyMMdd", now).toString()
    val that = DateFormat.format("yyyyMMdd", millis).toString()
    return when {
        that == today -> "TODAY"
        that == DateFormat.format("yyyyMMdd", now - dayMs).toString() -> "YESTERDAY"
        else -> DateFormat.format("EEE d MMM yyyy", millis).toString().uppercase()
    }
}

private fun fullDate(millis: Long): String =
    DateFormat.format("d MMM yyyy · HH:mm", Date(millis)).toString()

private fun details(context: Context, item: MediaItem): String = buildList {
    if (item.width > 0 && item.height > 0) {
        add("${item.width}×${item.height}")
        if (!item.isVideo) add("%.1f MP".format(item.width * item.height / 1_000_000f))
    }
    if (item.isVideo) add(duration(item.durationMs))
    if (item.size > 0) add(Formatter.formatShortFileSize(context, item.size))
}.joinToString("  ·  ")
