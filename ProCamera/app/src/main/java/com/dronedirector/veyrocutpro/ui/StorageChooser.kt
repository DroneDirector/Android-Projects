package com.dronedirector.veyrocutpro.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.camera.StorageArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Save to" choice for capture, the video editor or the photo editor: internal storage, an SD card /
 * USB drive Android lists, or a folder picked on any drive (USB stick, SSD, SD card).
 */
@Composable
fun StorageChooser(area: StorageArea, onChange: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var choice by remember { mutableStateOf(Storage.choice(ctx, area)) }
    var options by remember { mutableStateOf(Storage.options(ctx)) }
    var folderOk by remember { mutableStateOf(true) }
    LaunchedEffect(choice) {
        options = withContext(Dispatchers.IO) { Storage.options(ctx) }
        folderOk = !Storage.isTree(choice) || withContext(Dispatchers.IO) { Storage.available(ctx, choice) }
    }
    fun set(v: String) {
        val old = choice
        Storage.save(ctx, v, area)
        choice = v
        // Let go of a folder no part of the app uses any more.
        if (Storage.isTree(old) && old != v && StorageArea.entries.none { Storage.choice(ctx, it) == old }) {
            try {
                ctx.contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(old.removePrefix("tree:")),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Throwable) {}
        }
        onChange(v)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) set(Storage.rememberTree(ctx, tree))
    }
    val what = when (area) { StorageArea.CAPTURE -> "Photos and videos"; StorageArea.VIDEO -> "Exported videos"; StorageArea.PHOTO -> "Edited photos" }
    Text("$what are saved here.", color = Pro.Dim, fontSize = 12.sp)
    if (area != StorageArea.CAPTURE) Radio(
        "Same as capture", Storage.label(ctx, Storage.choice(ctx, StorageArea.CAPTURE)), choice == Storage.SAME,
    ) { set(Storage.SAME) }
    options.forEach { o ->
        Radio(o.label, if (o.removable) "SD card / USB drive · DCIM/VeyroCutPro" else "This device · DCIM/VeyroCutPro", choice == o.volume) { set(o.volume) }
    }
    if (Storage.isTree(choice)) Radio(
        Storage.label(ctx, choice),
        if (folderOk) "Folder you picked" else "Not connected — saving to internal storage until it's back",
        true,
    ) {}
    if (!Storage.isTree(choice) && choice != Storage.SAME && choice != Storage.INTERNAL && options.none { it.volume == choice }) Radio(
        "SD card / USB drive", "Not connected — saving to internal storage until it's back", true,
    ) {}
    Text(
        "CHOOSE A FOLDER ON A USB DRIVE / SD CARD…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))
            .clickable { pick.launch(null) }.padding(10.dp),
    )
    Text(
        "Plug in a USB stick, SSD or card reader (USB-C or an adapter), then pick or create a folder on it. " +
            "Videos record straight onto the drive. If it's unplugged, VeyroCut Pro saves to internal storage and tells you.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun Radio(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Pro.Amber))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Pro.Dim, fontSize = 12.sp)
        }
    }
}
