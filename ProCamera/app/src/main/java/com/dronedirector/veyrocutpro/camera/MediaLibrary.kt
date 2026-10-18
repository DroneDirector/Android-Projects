package com.dronedirector.veyrocutpro.camera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** One photo or video saved by VeyroCut Pro. */
data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val mime: String,
) {
    /** RAW (DNG) photo. */
    val isRaw: Boolean get() = !isVideo && (mime.contains("dng", ignoreCase = true) || mime.contains("raw", ignoreCase = true) || name.endsWith(".dng", ignoreCase = true))
}

/** Reads the photos and videos VeyroCut Pro saved (DCIM/VeyroCutPro on internal storage and SD cards). */
@Suppress("DEPRECATION")
object MediaLibrary {
    private const val FOLDER = "DCIM/VeyroCutPro"
    /** Where earlier versions (AcluxPro) saved — still shown in the gallery. */
    private const val LEGACY_FOLDER = "DCIM/AcluxPro"

    /** Items in the trash (Android 11+): kept 30 days by the system, then deleted. */
    fun queryTrash(context: Context): List<MediaItem> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        return (queryCollection(context, false, true) + queryCollection(context, true, true)).sortedByDescending { it.dateTaken }
    }

    /** Moves to / out of the trash. Returns false if Android needs to ask the user first. */
    fun setTrashed(context: Context, uris: List<Uri>, trashed: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        var all = true
        for (u in uris) try {
            val v = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0) }
            context.contentResolver.update(u, v, null, null)
        } catch (_: SecurityException) { all = false }
        return all
    }

    fun query(context: Context): List<MediaItem> {
        val images = queryCollection(context, video = false)
        val videos = queryCollection(context, video = true)
        val picked = Storage.trees(context).flatMap { queryFolder(context, it) }
        return (images + videos + picked).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
    }

    /** Photos and videos in a folder picked on a USB drive / SD card (Settings → save location). */
    private fun queryFolder(context: Context, tree: Uri): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        try {
            val parent = android.provider.DocumentsContract.getTreeDocumentId(tree)
            val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            context.contentResolver.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: continue
                    val name = c.getString(1) ?: ""
                    val video = mime.startsWith("video/")
                    val image = mime.startsWith("image/") || name.endsWith(".dng", true)
                    if (!video && !image) continue
                    val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    var w = 0; var h = 0; var dur = 0L
                    if (video) try {
                        val m = android.media.MediaMetadataRetriever()
                        try {
                            m.setDataSource(context, uri)
                            w = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                            h = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                            dur = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } finally { m.release() }
                    } catch (_: Throwable) {}
                    out += MediaItem(
                        uri = uri, isVideo = video, name = name,
                        dateTaken = if (c.isNull(4)) 0L else c.getLong(4),
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        width = w, height = h, durationMs = dur, mime = mime,
                    )
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun queryCollection(context: Context, video: Boolean, trashed: Boolean = false): List<MediaItem> {
        val collection: Uri = when {
            Build.VERSION.SDK_INT >= 29 && video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            Build.VERSION.SDK_INT >= 29 -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (video) add(MediaStore.Video.VideoColumns.DURATION)
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.MediaColumns.VOLUME_NAME)
                add(MediaStore.MediaColumns.DATE_TAKEN)
            }
        }.toTypedArray()
        val (selection, args) = if (Build.VERSION.SDK_INT >= 29) {
            "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?)" to
                arrayOf("$FOLDER%", "$LEGACY_FOLDER%")
        } else {
            "(${MediaStore.MediaColumns.DATA} LIKE ? OR ${MediaStore.MediaColumns.DATA} LIKE ?)" to
                arrayOf("%/$FOLDER/%", "%/$LEGACY_FOLDER/%")
        }
        val out = mutableListOf<MediaItem>()
        try {
            val cursor = if (trashed && Build.VERSION.SDK_INT >= 30) {
                val q = android.os.Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                }
                context.contentResolver.query(collection, projection, q, null)
            } else context.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val durCol = if (video) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                val volCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
                val takenCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val base: Uri = if (volCol >= 0 && Build.VERSION.SDK_INT >= 29) {
                        val vol = c.getString(volCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                        if (video) MediaStore.Video.Media.getContentUri(vol) else MediaStore.Images.Media.getContentUri(vol)
                    } else collection
                    val taken = if (takenCol >= 0 && !c.isNull(takenCol)) c.getLong(takenCol) else 0L
                    out += MediaItem(
                        uri = ContentUris.withAppendedId(base, id),
                        isVideo = video,
                        name = c.getString(nameCol) ?: "",
                        dateTaken = if (taken > 0) taken else c.getLong(addedCol) * 1000L,
                        size = c.getLong(sizeCol),
                        width = c.getInt(wCol),
                        height = c.getInt(hCol),
                        durationMs = if (durCol >= 0) c.getLong(durCol) else 0L,
                        mime = c.getString(mimeCol) ?: if (video) "video/mp4" else "image/jpeg",
                    )
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }
}
