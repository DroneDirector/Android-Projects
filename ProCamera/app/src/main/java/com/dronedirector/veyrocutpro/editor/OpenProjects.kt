package com.dronedirector.veyrocutpro.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Several projects open in the video editor at once (File → Open projects): each keeps its edit
 * in memory while another is on screen, so switching is quick. Own code.
 */
class OpenProject(
    val key: String,
    /** The edit (null = a new, empty one not opened yet). Updated whenever another project comes on screen. */
    var data: VideoProjectData?,
    var team: TeamSession?,
    /** What it looked like when last saved / opened (to see unsaved changes); null = take it when it opens. */
    var savedSig: String? = null,
) {
    var name by mutableStateOf(data?.name ?: "")
}

class ProjectHost {
    val open = mutableStateListOf<OpenProject>()
    var active by mutableStateOf<String?>(null)
    /** Bumped to rebuild the editor (revert, reopen). */
    var reload by mutableIntStateOf(0)
    /** Set by the editor on screen: its edit as it is now. */
    var snapshot: (() -> VideoProjectData)? = null

    fun activeProject(): OpenProject? = open.firstOrNull { it.key == active }

    /** Unsaved changes in [op] (the one on screen is read live). */
    fun dirty(op: OpenProject): Boolean {
        val d = if (op.key == active) snapshot?.invoke() ?: op.data else op.data
        if (d == null) return false
        val empty = d.clips.isEmpty() && d.layers.isEmpty() && d.music == null && d.extraAudio.isEmpty()
        val s = op.savedSig ?: return !empty
        return sig(d) != s
    }

    companion object {
        /** The edit's content, without the things that change on every save. */
        fun sig(p: VideoProjectData): String = try { ProjectStore.gson.toJson(p.copy(id = "", savedAt = 0L, name = "")) } catch (_: Throwable) { "" }
    }
}

/** savedSig of a project read from disk: taken from the editor once it has opened. */
const val SIG_PENDING = "pending"

/** What the editor on screen can ask the video editor screen to do with the open projects. */
class EditorNav(
    val open: (data: VideoProjectData?, team: TeamSession?, fromDisk: Boolean) -> Unit,
    val switchTo: (String) -> Unit,
    val close: (String) -> Unit,
    val exitApp: () -> Unit,
    /** Opens [OpenProject] again from its (new) data — revert. */
    val reopen: (OpenProject) -> Unit,
)

/** The video editor's File menu, keyboard and bar-dialog state, kept in one place (the editor function is near Android's size limit). */
class WsFile {
    val fa = FileActions()
    val fm = FileMenuState()
    var showKeys by mutableStateOf(false)
    var barDlg by mutableStateOf<String?>(null)
    var closeAsk by mutableStateOf<Pair<List<OpenProject>, () -> Unit>?>(null)
}
