package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text

/**
 * Video timeline: hold a clip for its menu; "Select multiple" turns taps into picking clips,
 * and holding a clip again offers "Deselect all". Clips are kept by id (their places change). Own code.
 */
class ClipPick {
    var on by mutableStateOf(false)
    val ids = mutableStateListOf<String>()
    /** The clip whose menu is open (index on the timeline) and where it was held (px from the timeline's left). */
    var menuFor by mutableStateOf<Int?>(null)
    var menuX by mutableFloatStateOf(0f)
    var menuY by mutableFloatStateOf(0f)

    fun start(id: String) { ids.clear(); ids.add(id); on = true }
    fun toggle(id: String) { if (id in ids) ids.remove(id) else ids.add(id) }
    fun clear() { ids.clear(); on = false }
}

/** What the clip menu can do; the editor fills these in. */
class ClipActs(
    val splitHere: (Int) -> Unit,
    val duplicate: (List<Int>) -> Unit,
    val speed: (Int) -> Unit,
    val reverse: (Int) -> Unit,
    val mute: (List<Int>) -> Unit,
    val nest: (from: Int, count: Int) -> Unit,
    val delete: (Int) -> Unit,
    val deleteMany: (List<Int>) -> Unit,
)

/** The menu shown where a clip was held. */
@Composable
fun ClipMenu(clips: List<Clip>, pick: ClipPick, act: ClipActs) {
    val i = pick.menuFor ?: return
    val c = clips.getOrNull(i) ?: run { pick.menuFor = null; return }
    val x = with(LocalDensity.current) { pick.menuX.toDp() }
    val y = with(LocalDensity.current) { pick.menuY.toDp() }
    fun close() { pick.menuFor = null }
    @Composable
    fun item(t: String, color: Color = Color.White, a: () -> Unit) =
        DropdownMenuItem(text = { Text(t, color = color, fontSize = 14.sp) }, onClick = { close(); a() })
    Box(Modifier.offset(x = x, y = y)) {
        DropdownMenu(true, onDismissRequest = { close() }, containerColor = Pro.PanelSolid) {
            if (!pick.on) {
                Text("${i + 1}  ${c.name}", color = Pro.Dim, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                item("Select multiple") { pick.start(c.id) }
                HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                item("Split at the playhead") { act.splitHere(i) }
                item("Duplicate") { act.duplicate(listOf(i)) }
                item("Speed / Duration…") { act.speed(i) }
                item("Reverse") { act.reverse(i) }
                item(if (c.muted) "Turn sound on" else "Mute sound") { act.mute(listOf(i)) }
                HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                item("Delete", Pro.Red) { act.delete(i) }
            } else {
                val idx = clips.indices.filter { clips[it].id in pick.ids }
                Text("${idx.size} clip" + (if (idx.size == 1) "" else "s") + " selected", color = Pro.Amber, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                item("Deselect all") { pick.clear() }
                item("Select all") { pick.ids.clear(); pick.ids.addAll(clips.map { it.id }) }
                if (c.id !in pick.ids) item("Add this clip") { pick.ids.add(c.id) } else item("Leave this clip out") { pick.ids.remove(c.id) }
                if (idx.isNotEmpty()) {
                    HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                    item("Duplicate") { act.duplicate(idx) }
                    item(if (idx.all { clips[it].muted }) "Turn sound on" else "Mute sound") { act.mute(idx) }
                    // Nesting needs clips side by side.
                    if (idx.size >= 2 && idx.last() - idx.first() == idx.size - 1) item("Nest into one clip") { act.nest(idx.first(), idx.size); pick.clear() }
                    HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                    item("Delete selected", Pro.Red) { act.deleteMany(idx); pick.clear() }
                }
            }
        }
    }
}
