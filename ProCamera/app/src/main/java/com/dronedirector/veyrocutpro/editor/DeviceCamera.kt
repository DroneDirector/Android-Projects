package com.dronedirector.veyrocutpro.editor

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dronedirector.veyrocutpro.camera.MediaLibrary
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The phone's camera from inside the editors: take a photo or record a video without leaving the
 * editor. What's captured is saved to DCIM/AlcorCutPro like everything else the app makes, then handed
 * to [onPhoto] / [onVideo]. Own code (uses the phone's camera app through Android's standard capture).
 */
class DeviceCamera internal constructor(val takePhoto: () -> Unit, val recordVideo: () -> Unit)

@Composable
fun rememberDeviceCamera(onPhoto: (Uri) -> Unit = {}, onVideo: (Uri) -> Unit = {}): DeviceCamera {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var waiting by rememberSaveable { mutableStateOf(0) }   // 1 photo, 2 video: asked for permission first
    fun done(ok: Boolean, video: Boolean) {
        val u = pending?.let(Uri::parse) ?: return
        pending = null
        if (ok && hasData(context, u)) { if (video) onVideo(u) else onPhoto(u) }
        else try { context.contentResolver.delete(u, null, null) } catch (_: Throwable) {}
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { done(it, false) }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { done(it, true) }
    fun launch(kind: Int) {
        val u = newItem(context, kind == 2) ?: run {
            android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open the camera"), android.widget.Toast.LENGTH_SHORT).show(); return
        }
        pending = u.toString()
        try { if (kind == 2) video.launch(u) else photo.launch(u) }
        catch (_: Throwable) {
            pending = null; try { context.contentResolver.delete(u, null, null) } catch (_: Throwable) {}
            android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("No camera app found"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val k = waiting; waiting = 0
        if (granted && k != 0) launch(k)
        else if (!granted) android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Camera permission is needed to take photos and videos"), android.widget.Toast.LENGTH_LONG).show()
    }
    fun start(kind: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch(kind)
        else { waiting = kind; perm.launch(Manifest.permission.CAMERA) }
    }
    return remember { DeviceCamera({ start(1) }, { start(2) }) }
}

private fun hasData(context: Context, u: Uri): Boolean = try {
    context.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length != 0L } ?: false
} catch (_: Throwable) { false }

/** A new, empty picture / video in DCIM/AlcorCutPro for the camera app to write into. */
private fun newItem(context: Context, video: Boolean): Uri? = try {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val v = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, (if (video) "AC_VID_" else "AC_IMG_") + stamp + if (video) ".mp4" else ".jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, MediaLibrary.FOLDER)
    }
    context.contentResolver.insert(if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
} catch (_: Throwable) { null }

/** A choice of where to add media from (gallery, files, camera…), shown as a small sheet. */
@Composable
fun AddFromDialog(title: String, options: List<Triple<ImageVector, String, () -> Unit>>, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                options.forEach { (icon, label, act) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                            .clickable { onDismiss(); act() }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(22.dp))
                        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("CANCEL", color = Pro.Amber) } },
    )
}
