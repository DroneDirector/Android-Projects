package com.dronedirector.acluxpro.camera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** One photo or video saved by AcluxPro. */
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
)

/** Reads the photos and videos AcluxPro saved (DCIM/AcluxPro on internal storage and SD cards). */
@Suppress("DEPRECATION")
object MediaLibrary {
    private const val FOLDER = "DCIM/AcluxPro"

    fun query(context: Context): List<MediaItem> {
        val images = queryCollection(context, video = false)
        val videos = queryCollection(context, video = true)
        return (images + videos).sortedByDescending { it.dateTaken }
    }

    private fun queryCollection(context: Context, video: Boolean): List<MediaItem> {
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
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("$FOLDER%")
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/$FOLDER/%")
        }
        val out = mutableListOf<MediaItem>()
        try {
            context.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
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
