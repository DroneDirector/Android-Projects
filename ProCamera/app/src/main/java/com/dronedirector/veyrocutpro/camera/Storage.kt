package com.dronedirector.veyrocutpro.camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast

/** A place photos and videos can be saved: internal storage, an SD card or a USB drive Android lists. */
data class StorageOption(val volume: String, val label: String, val removable: Boolean)

/** Which part of the app is saving: each can have its own place (the editors follow capture by default). */
enum class StorageArea(val key: String, val label: String) {
    CAPTURE("storage_volume", "Capture"),
    VIDEO("storage_video", "Video editor"),
    PHOTO("storage_photo", "Photo editor"),
}

/**
 * Where VelaCut Pro saves. A place is either a media volume name (internal storage, an SD card or a USB
 * drive that Android lists — saved in DCIM/VeyroCutPro there) or "tree:<uri>" — a folder the user picked
 * on any drive (USB stick, SSD, SD card) through the system folder picker. If the place is gone
 * (drive unplugged), saving falls back to internal storage with a short notice.
 */
object Storage {
    private const val PREFS = "procam"
    /** Same value as MediaStore.VOLUME_EXTERNAL_PRIMARY (a compile-time constant, safe on any API). */
    const val INTERNAL = "external_primary"
    /** Editors: use whatever capture uses. */
    const val SAME = "same"
    private const val TREE = "tree:"

    enum class Kind { IMAGE, VIDEO, AUDIO }

    fun options(context: Context): List<StorageOption> {
        val internal = StorageOption(INTERNAL, "Internal storage", false)
        if (Build.VERSION.SDK_INT < 29) return listOf(internal)
        val names = try { MediaStore.getExternalVolumeNames(context) } catch (_: Throwable) { emptySet<String>() }
        val sm = context.getSystemService(StorageManager::class.java)
        val result = mutableListOf(internal)
        for (name in names) {
            if (name == INTERNAL || name == "external") continue
            var label = "SD card / USB drive"
            if (Build.VERSION.SDK_INT >= 30 && sm != null) {
                sm.storageVolumes.firstOrNull { it.mediaStoreVolumeName == name }?.let { v ->
                    label = v.getDescription(context) ?: label
                }
            }
            result += StorageOption(name, label, true)
        }
        return result
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The stored choice for [area] (editors may be [SAME]). */
    fun choice(context: Context, area: StorageArea): String {
        val p = prefs(context)
        p.getString(area.key, null)?.let { return it }
        if (area == StorageArea.CAPTURE) return INTERNAL
        // Earlier versions: an editor folder picked under "save_tree_video" / "save_tree_photo".
        val old = p.getString(if (area == StorageArea.VIDEO) "save_tree_video" else "save_tree_photo", null)
        return if (old != null) TREE + old else SAME
    }

    /** The place [area] saves to ([SAME] resolved to capture's place). */
    fun load(context: Context, area: StorageArea = StorageArea.CAPTURE): String {
        val c = choice(context, area)
        return if (c == SAME) choice(context, StorageArea.CAPTURE).let { if (it == SAME) INTERNAL else it } else c
    }

    fun save(context: Context, volume: String, area: StorageArea = StorageArea.CAPTURE) {
        prefs(context).edit().putString(area.key, volume).apply()
    }

    fun isTree(place: String) = place.startsWith(TREE)
    private fun treeUri(place: String): Uri = Uri.parse(place.removePrefix(TREE))

    /** Keeps access to a folder the user picked and returns it as a place. */
    fun rememberTree(context: Context, uri: Uri): String {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Throwable) {}
        return TREE + uri
    }

    /** All picked folders currently in use (for the gallery). */
    fun trees(context: Context): List<Uri> =
        StorageArea.entries.map { load(context, it) }.filter { isTree(it) }.distinct().map { treeUri(it) }

    private fun treeDoc(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun treeOk(context: Context, tree: Uri): Boolean = try {
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission } &&
            (context.contentResolver.query(treeDoc(tree), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true)
    } catch (_: Throwable) { false }

    /** Is [place] there right now (card inserted, drive plugged in, folder still allowed)? */
    fun available(context: Context, place: String): Boolean = when {
        place == INTERNAL || place == SAME -> true
        isTree(place) -> treeOk(context, treeUri(place))
        else -> options(context).any { it.volume == place }
    }

    /** Short name of a place for the settings. */
    fun label(context: Context, place: String): String = when {
        place == SAME -> "Same as capture"
        place == INTERNAL -> "Internal storage"
        isTree(place) -> {
            val tree = treeUri(place)
            val id = try { DocumentsContract.getTreeDocumentId(tree) } catch (_: Throwable) { "" }
            val folder = id.substringAfter(':', "").ifEmpty { "(top folder)" }
            (if (id.startsWith("primary:")) "This device" else "External drive") + " · " + folder
        }
        else -> options(context).firstOrNull { it.volume == place }?.label ?: "SD card / USB drive"
    }

    @Volatile private var lastWarn = 0L
    private fun warn(context: Context, text: String) {
        val now = System.currentTimeMillis()
        if (now - lastWarn < 8000) return
        lastWarn = now
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post { try { Toast.makeText(app, com.dronedirector.veyrocutpro.ui.I18n.tr(text), Toast.LENGTH_LONG).show() } catch (_: Throwable) {} }
    }

    /** [place], or internal storage (with a notice) if it isn't there right now. */
    fun resolve(context: Context, place: String): String {
        if (available(context, place)) return if (place == SAME) INTERNAL else place
        warn(context, if (isTree(place)) "Save folder not found (drive unplugged?) — saving to internal storage"
        else "SD card / drive not found — saving to internal storage")
        return INTERNAL
    }

    fun imagesUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(volume)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun videosUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(volume)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun audioUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(volume)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    /** A new, empty file in a picked folder. */
    fun createInTree(context: Context, place: String, mime: String, name: String): Uri? = try {
        DocumentsContract.createDocument(context.contentResolver, treeDoc(treeUri(place)), mime, name)
    } catch (_: Throwable) { null }

    /**
     * Creates the file a save writes into: in MediaStore (with [values], incl. IS_PENDING) or, for a
     * picked folder, a document named DISPLAY_NAME. Finish with [publish]; on failure call [discard].
     */
    fun insert(context: Context, place: String, values: ContentValues, kind: Kind): Uri? {
        val p = resolve(context, place)
        if (isTree(p)) {
            val name = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) ?: "VelaCutPro_${System.currentTimeMillis()}"
            val mime = values.getAsString(MediaStore.MediaColumns.MIME_TYPE) ?: "application/octet-stream"
            createInTree(context, p, mime, name)?.let { return it }
            warn(context, "Couldn't write to the save folder — saving to internal storage")
            return insert(context, INTERNAL, values, kind)
        }
        val col = when (kind) { Kind.IMAGE -> imagesUri(p); Kind.VIDEO -> videosUri(p); Kind.AUDIO -> audioUri(p) }
        return try { context.contentResolver.insert(col, values) } catch (_: Throwable) { null }
    }

    /** Makes a finished MediaStore file visible (a picked-folder file already is). */
    fun publish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT < 29 || uri.authority != MediaStore.AUTHORITY) return
        try { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) } catch (_: Throwable) {}
    }

    /** Removes a half-written or unwanted file, wherever it is. */
    fun discard(context: Context, uri: Uri): Boolean = try {
        if (uri.authority == MediaStore.AUTHORITY) context.contentResolver.delete(uri, null, null) > 0
        else DocumentsContract.deleteDocument(context.contentResolver, uri)
    } catch (_: Throwable) { false }
}
