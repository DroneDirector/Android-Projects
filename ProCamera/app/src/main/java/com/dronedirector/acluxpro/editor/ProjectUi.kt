package com.dronedirector.acluxpro.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.ui.Pro
import com.dronedirector.acluxpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The save button that sits next to EXPORT. */
@Composable
fun SaveButton(saved: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(Ed.Raised).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Save, contentDescription = "Save project", tint = Pro.Amber, modifier = Modifier.size(16.dp))
        Text(if (saved) " SAVE" else " SAVE", style = Pro.Value.copy(color = Pro.Amber, fontSize = 12.sp))
    }
}

/** Asks for a project name the first time a project is saved. */
@Composable
fun ProjectNameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("Save project", color = Color.White) },
        text = {
            Column {
                Text("Saves your edit so you can open it again later and continue where you left off. Your original media is not copied.", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text("Project name") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.ifBlank { initial }) }) { Text("Save", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

/** Saved projects on an editor's start page; tap to open, ✕ to delete (asks first). */
@Composable
fun SavedProjects(video: Boolean, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val list by produceState(emptyList<ProjectEntry>(), version) { value = withContext(Dispatchers.IO) { ProjectStore.list(context, video) } }
    var confirm by remember { mutableStateOf<ProjectEntry?>(null) }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Pro.PanelSolid,
            title = { Text("Delete this project?", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${p.name}\"? Your photos and videos are not deleted.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { ProjectStore.delete(context, video, p.id); confirm = null; version++ }) { Text("Yes", color = Pro.Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("No", color = Pro.Amber) } },
        )
    }
    if (list.isEmpty()) return
    Text("SAVED PROJECTS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { p ->
            val thumb by produceState<ImageBitmap?>(null, p.thumb, p.savedAt) {
                value = withContext(Dispatchers.IO) { p.thumb?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            }
            Column(Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable { onOpen(p.id) }) {
                Box(Modifier.fillMaxWidth().height(84.dp).background(Color.Black)) {
                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { confirm = p },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, contentDescription = "Delete project", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
                Column(Modifier.padding(8.dp)) {
                    Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.detail + " · " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.savedAt)), style = Pro.Label.copy(fontSize = 10.sp), maxLines = 1)
                }
            }
        }
    }
}
