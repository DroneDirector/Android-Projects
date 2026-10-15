package com.dronedirector.acluxpro.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream

/**
 * Where an editor saves its exports. Nothing chosen → the app's default location (DCIM/AcluxPro).
 * A chosen folder is a storage-access tree the user picked, kept with a persistable permission.
 */
object SaveFolder {
    private fun key(video: Boolean) = if (video) "save_tree_video" else "save_tree_photo"
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun get(context: Context, video: Boolean): Uri? = prefs(context).getString(key(video), null)?.let(Uri::parse)

    fun set(context: Context, video: Boolean, tree: Uri?) {
        val old = get(context, video)
        if (tree != null) {
            try {
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Throwable) {}
        }
        prefs(context).edit().putString(key(video), tree?.toString()).apply()
        // Let go of the old folder unless the other editor still uses it.
        if (old != null && old != tree && old != get(context, !video)) {
            try { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}
        }
    }

    /** Readable name such as "Internal storage/Movies/Edits". */
    fun label(tree: Uri?): String {
        if (tree == null) return "Default (DCIM/AcluxPro)"
        return try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val (vol, path) = id.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val v = if (vol == "primary") "Internal storage" else vol
            if (path.isEmpty()) v else "$v/$path"
        } catch (_: Throwable) { tree.lastPathSegment ?: "Chosen folder" }
    }

    /**
     * Writes a new file into the chosen folder. Returns null when no folder is chosen or it can't be
     * written (folder deleted, permission gone) so the caller falls back to the default location.
     */
    fun write(context: Context, video: Boolean, name: String, mime: String, body: (OutputStream) -> Unit): Uri? {
        val tree = get(context, video) ?: return null
        val resolver = context.contentResolver
        var doc: Uri? = null
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            doc = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return null
            resolver.openOutputStream(doc)?.use(body) ?: throw IllegalStateException("no stream")
            doc
        } catch (_: Throwable) {
            doc?.let { try { DocumentsContract.deleteDocument(resolver, it) } catch (_: Throwable) {} }
            null
        }
    }
}
