package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class VideoProjectData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val savedAt: Long = 0L,
    val clips: List<Clip> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val music: MusicTrack? = null,
    val extraAudio: List<MusicTrack> = emptyList(),
    val audioPool: List<PoolAudio> = emptyList(),
    val deliver: DeliverSettings = DeliverSettings(),
    /** Main video track switches on the timeline. */
    val videoMuted: Boolean = false,
    val videoLocked: Boolean = false,
    /** Timeline markers (ms). */
    val markers: List<Long> = emptyList(),
    /** Notes on markers, keyed by the marker's time (ms) as text. */
    val markerNotes: Map<String, String>? = null,
)

data class PhotoProjectData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val savedAt: Long = 0L,
    val uri: Uri = Uri.EMPTY,
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    val geometry: Geometry = Geometry(),
    val watermark: Layer? = null,
    val format: Int = 0,
    val quality: Float = 0.95f,
    val sizeIdx: Int = 0,
    val masks: List<PhotoMask> = emptyList(),
    val spots: List<Spot> = emptyList(),
    val lens: LensFix = LensFix(),
    val texts: List<Layer>? = null,
    val sky: SkySwap? = null,
    val bg: PhotoBackground = PhotoBackground(),
    /** Photo after AI erase (a copy kept in the app's files), used instead of the original. */
    val baseFile: String? = null,
    /** RAW (DNG) develop choices. */
    val raw: RawDng.Dev? = null,
)

/** A saved project as shown in the list. */
data class ProjectEntry(val id: String, val name: String, val savedAt: Long, val thumb: File?, val detail: String)

/** Saves editor projects (settings + links to the media, never copies of it) in the app's private storage. */
object ProjectStore {
    internal val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(Uri::class.java, JsonSerializer<Uri> { src, _, _ -> JsonPrimitive(src.toString()) })
        .registerTypeHierarchyAdapter(Uri::class.java, JsonDeserializer { json, _, _ -> Uri.parse(json.asString) })
        .create()

    private fun dir(context: Context, video: Boolean) = File(context.filesDir, if (video) "projects/video" else "projects/photo").apply { mkdirs() }

    fun defaultName(video: Boolean): String =
        (if (video) "Video " else "Photo ") + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())

    private fun keepAccess(context: Context, uris: List<Uri?>) {
        for (u in uris) if (u != null && u.scheme == "content") {
            try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        }
    }

    private fun saveThumb(context: Context, video: Boolean, id: String, bmp: Bitmap?) {
        bmp ?: return
        try {
            val s = 320f / maxOf(bmp.width, bmp.height)
            val t = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true) else bmp
            File(dir(context, video), "$id.jpg").outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (_: Throwable) {}
    }

    fun saveVideo(context: Context, p: VideoProjectData, thumb: Bitmap?): VideoProjectData {
        val out = p.copy(savedAt = System.currentTimeMillis())
        keepAccess(context, out.clips.map { it.uri } + out.layers.flatMap { listOf(it.imageUri, it.videoUri) } + listOf(out.music?.uri) + out.extraAudio.map { it.uri } + out.audioPool.map { it.uri })
        File(dir(context, true), "${out.id}.json").writeText(gson.toJson(out))
        saveThumb(context, true, out.id, thumb)
        return out
    }

    fun savePhoto(context: Context, p: PhotoProjectData, thumb: Bitmap?): PhotoProjectData {
        val out = p.copy(savedAt = System.currentTimeMillis())
        keepAccess(context, listOf(out.uri, out.watermark?.imageUri))
        File(dir(context, false), "${out.id}.json").writeText(gson.toJson(out))
        saveThumb(context, false, out.id, thumb)
        return out
    }

    fun loadVideo(context: Context, id: String): VideoProjectData? = try {
        val p = gson.fromJson(File(dir(context, true), "$id.json").readText(), VideoProjectData::class.java)
        Defaults.fix(p, VideoProjectData())
        p.clips.forEach { Defaults.fix(it, Clip(uri = Uri.EMPTY, name = "", durationMs = 1, width = 1, height = 1)) }
        p.layers.forEach { Defaults.fix(it, Layer()) }
        p.music?.let { Defaults.fix(it, MusicTrack(Uri.EMPTY, "", 1)) }
        p.extraAudio.forEach { Defaults.fix(it, MusicTrack(Uri.EMPTY, "", 1)) }
        p
    } catch (_: Throwable) { null }

    fun loadPhoto(context: Context, id: String): PhotoProjectData? = try {
        val p = gson.fromJson(File(dir(context, false), "$id.json").readText(), PhotoProjectData::class.java)
        Defaults.fix(p, PhotoProjectData())
        p.watermark?.let { Defaults.fix(it, Layer()) }
        p
    } catch (_: Throwable) { null }

    fun list(context: Context, video: Boolean): List<ProjectEntry> {
        val d = dir(context, video)
        return d.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            val id = f.name.removeSuffix(".json")
            try {
                val o = com.google.gson.JsonParser.parseString(f.readText()).asJsonObject
                val detail = if (video) {
                    val n = o.getAsJsonArray("clips")?.size() ?: 0
                    "$n clip" + (if (n == 1) "" else "s")
                } else "Photo"
                ProjectEntry(id, o.get("name")?.asString ?: "Project", o.get("savedAt")?.asLong ?: f.lastModified(), File(d, "$id.jpg").takeIf { it.exists() }, detail)
            } catch (_: Throwable) { null }
        }.sortedByDescending { it.savedAt }
    }

    fun delete(context: Context, video: Boolean, id: String) {
        File(dir(context, video), "$id.json").delete()
        File(dir(context, video), "$id.jpg").delete()
    }
}

/**
 * Projects saved by an older version may lack newer settings; the JSON reader leaves those empty.
 * This fills every missing value (recursively) from a default instance.
 */
private object Defaults {
    fun fix(target: Any, template: Any) {
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(target)
                    val t = f.get(template)
                    if (v == null && t != null) f.set(target, t)
                    else if (v != null && t != null && v.javaClass == t.javaClass && v.javaClass.name.startsWith("com.dronedirector")) {
                        if (!v.javaClass.isEnum) fix(v, t)
                    } else if (v is List<*> && t is List<*> && t.isNotEmpty()) {
                        val proto = t.first() ?: continue
                        v.forEach { e -> if (e != null && e.javaClass == proto.javaClass && !e.javaClass.isEnum) fix(e, proto) }
                    }
                } catch (_: Throwable) {}
            }
            cls = cls.superclass
        }
    }
}
