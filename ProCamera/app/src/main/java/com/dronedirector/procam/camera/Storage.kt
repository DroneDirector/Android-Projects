package com.dronedirector.procam.camera

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore

/** A place photos and videos can be saved: internal storage or a removable SD card. */
data class StorageOption(val volume: String, val label: String, val removable: Boolean)

object Storage {
    private const val PREFS = "procam"
    private const val KEY = "storage_volume"
    /** Same value as MediaStore.VOLUME_EXTERNAL_PRIMARY (a compile-time constant, safe on any API). */
    const val INTERNAL = "external_primary"

    fun options(context: Context): List<StorageOption> {
        val internal = StorageOption(INTERNAL, "Internal storage", false)
        if (Build.VERSION.SDK_INT < 29) return listOf(internal)
        val names = try { MediaStore.getExternalVolumeNames(context) } catch (_: Throwable) { emptySet<String>() }
        val sm = context.getSystemService(StorageManager::class.java)
        val result = mutableListOf(internal)
        for (name in names) {
            if (name == INTERNAL || name == "external") continue
            var label = "SD card"
            if (Build.VERSION.SDK_INT >= 30 && sm != null) {
                sm.storageVolumes.firstOrNull { it.mediaStoreVolumeName == name }?.let { v ->
                    label = v.getDescription(context) ?: label
                }
            }
            result += StorageOption(name, label, true)
        }
        return result
    }

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: INTERNAL

    fun save(context: Context, volume: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, volume).apply()
    }

    fun imagesUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(volume)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun videosUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(volume)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
}

