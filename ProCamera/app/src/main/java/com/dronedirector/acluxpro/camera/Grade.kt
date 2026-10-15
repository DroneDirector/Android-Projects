package com.dronedirector.acluxpro.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A colour grade. Every control is a pure colour transform, so the whole grade can be baked into a
 * 3D lookup table: the same table drives the photo preview, saved photos and exported videos.
 */
data class Grade(
    /** Stops, -2..+2. */
    val exposure: Float = 0f,
    /** -1..1 for every control below. */
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    /** 0..1: lifts the blacks for a matte film finish. */
    val fade: Float = 0f,
    /** Split toning: hue 0..360 for shadows and highlights, with strength 0..1. */
    val shadowHue: Float = 200f,
    val shadowTone: Float = 0f,
    val highlightHue: Float = 35f,
    val highlightTone: Float = 0f,
    val look: Filter = Filter.NONE,
    /** 0..1 strength of the look. */
    val lookAmount: Float = 1f,
) {
    val isIdentity: Boolean get() = copy(lookAmount = 1f) == Grade()

    /** Grades one colour (components 0..1, modified in place). */
    fun apply(c: FloatArray) {
        var r = c[0]; var g = c[1]; var b = c[2]

        // Look first, blended by its strength.
        if (look != Filter.NONE && lookAmount > 0f) {
            val m = look.matrix
            val lr = (m[0] * r + m[1] * g + m[2] * b + m[4] / 255f).coerceIn(0f, 1f)
            val lg = (m[5] * r + m[6] * g + m[7] * b + m[9] / 255f).coerceIn(0f, 1f)
            val lb = (m[10] * r + m[11] * g + m[12] * b + m[14] / 255f).coerceIn(0f, 1f)
            val t = floatArrayOf(lr, lg, lb)
            look.look?.apply(t)
            r += (t[0] - r) * lookAmount; g += (t[1] - g) * lookAmount; b += (t[2] - b) * lookAmount
        }

        // Exposure in (approximately) linear light.
        if (exposure != 0f) {
            val k = 2f.pow(exposure)
            r = lin(r) * k; g = lin(g) * k; b = lin(b) * k
            r = gam(r); g = gam(g); b = gam(b)
        }

        // White balance.
        r += temperature * 0.08f
        b -= temperature * 0.08f
        g -= tint * 0.06f
        r += tint * 0.02f; b += tint * 0.02f

        // Tonal ranges.
        var l = luma(r, g, b)
        val sh = 1f - smooth(0f, 0.55f, l)
        val hi = smooth(0.45f, 1f, l)
        val bl = 1f - smooth(0f, 0.25f, l)
        val wh = smooth(0.75f, 1f, l)
        val d = shadows * 0.22f * sh + highlights * 0.22f * hi + blacks * 0.12f * bl + whites * 0.12f * wh
        r += d; g += d; b += d

        // Contrast around mid grey, as a gentle S-curve.
        if (contrast != 0f) {
            r = curve(r); g = curve(g); b = curve(b)
        }

        // Split toning.
        l = luma(r, g, b).coerceIn(0f, 1f)
        if (shadowTone > 0f) {
            val (tr, tg, tb) = hueColor(shadowHue)
            val w = shadowTone * 0.18f * (1f - smooth(0f, 0.6f, l))
            r += (tr - 0.5f) * w; g += (tg - 0.5f) * w; b += (tb - 0.5f) * w
        }
        if (highlightTone > 0f) {
            val (tr, tg, tb) = hueColor(highlightHue)
            val w = highlightTone * 0.18f * smooth(0.4f, 1f, l)
            r += (tr - 0.5f) * w; g += (tg - 0.5f) * w; b += (tb - 0.5f) * w
        }

        // Saturation and vibrance (vibrance protects colours that are already strong).
        l = luma(r, g, b)
        val sat = max(r, max(g, b)) - min(r, min(g, b))
        val s = ((1f + saturation) * (1f + vibrance * (1f - sat.coerceIn(0f, 1f)))).coerceAtLeast(0f)
        r = l + (r - l) * s; g = l + (g - l) * s; b = l + (b - l) * s

        // Fade: lifted blacks, softly compressed whites.
        if (fade > 0f) {
            val lift = fade * 0.14f
            r = lift + r * (1f - lift * 1.3f); g = lift + g * (1f - lift * 1.3f); b = lift + b * (1f - lift * 1.3f)
        }

        c[0] = r.coerceIn(0f, 1f); c[1] = g.coerceIn(0f, 1f); c[2] = b.coerceIn(0f, 1f)
    }

    private fun curve(x: Float): Float {
        val k = contrast
        val v = x.coerceIn(0f, 1f)
        val s = v * v * (3f - 2f * v)
        return if (k >= 0f) v + (s - v) * k * 1.4f else v + (0.5f - v) * (-k) * 0.6f
    }

    /** Bakes the grade into an N×N×N cube indexed [r][g][b], ARGB values. */
    fun cube(n: Int = 33): Array<Array<IntArray>> {
        val c = FloatArray(3)
        return Array(n) { ri ->
            Array(n) { gi ->
                IntArray(n) { bi ->
                    c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
                    apply(c)
                    (0xFF shl 24) or ((c[0] * 255f + 0.5f).toInt() shl 16) or
                        ((c[1] * 255f + 0.5f).toInt() shl 8) or (c[2] * 255f + 0.5f).toInt()
                }
            }
        }
    }

    companion object {
        private fun lin(v: Float) = v.coerceAtLeast(0f).pow(2.2f)
        private fun gam(v: Float) = v.coerceAtLeast(0f).pow(1f / 2.2f)
        private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
        private fun smooth(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Fully saturated colour for a hue, components 0..1. */
        fun hueColor(h: Float): Triple<Float, Float, Float> {
            val c = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, 1f, 1f))
            return Triple(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
        }
    }
}

/** Fast lookup-table application for bitmaps (trilinear interpolation). */
class GradeLut(grade: Grade, private val n: Int = 33) {
    private val r = FloatArray(n * n * n)
    private val g = FloatArray(n * n * n)
    private val b = FloatArray(n * n * n)

    init {
        val c = FloatArray(3)
        var i = 0
        for (ri in 0 until n) for (gi in 0 until n) for (bi in 0 until n) {
            c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
            grade.apply(c)
            r[i] = c[0]; g[i] = c[1]; b[i] = c[2]
            i++
        }
    }

    /** Grades [src] into a new bitmap. */
    fun apply(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        val s = (n - 1) / 255f
        val nn = n * n
        for (y in 0 until h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]
                val fr = ((p shr 16) and 0xFF) * s
                val fg = ((p shr 8) and 0xFF) * s
                val fb = (p and 0xFF) * s
                val r0 = fr.toInt().coerceAtMost(n - 2); val g0 = fg.toInt().coerceAtMost(n - 2); val b0 = fb.toInt().coerceAtMost(n - 2)
                val dr = fr - r0; val dg = fg - g0; val db = fb - b0
                val i000 = r0 * nn + g0 * n + b0
                val i100 = i000 + nn; val i010 = i000 + n; val i001 = i000 + 1
                val i110 = i100 + n; val i101 = i100 + 1; val i011 = i010 + 1; val i111 = i110 + 1
                fun lerp3(t: FloatArray): Float {
                    val c00 = t[i000] + (t[i100] - t[i000]) * dr
                    val c01 = t[i001] + (t[i101] - t[i001]) * dr
                    val c10 = t[i010] + (t[i110] - t[i010]) * dr
                    val c11 = t[i011] + (t[i111] - t[i011]) * dr
                    val c0 = c00 + (c10 - c00) * dg
                    val c1 = c01 + (c11 - c01) * dg
                    return c0 + (c1 - c0) * db
                }
                val outR = (lerp3(r) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outG = (lerp3(g) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outB = (lerp3(b) * 255f + 0.5f).toInt().coerceIn(0, 255)
                row[x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        return out
    }
}

object GradeIO {
    /** Upright decode, at most [maxPx] on the long side (0 = full size, capped for memory). */
    fun decode(context: Context, uri: Uri, maxPx: Int): Bitmap? = try {
        val limit = if (maxPx > 0) maxPx else 6000
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > limit) {
                    val f = limit.toFloat() / longest
                    d.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= limit) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            val rot = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            if (bmp != null && rot != 0) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true) else bmp
        }
    } catch (_: Throwable) {
        null
    }

    /** A video frame for the grade preview. */
    fun videoFrame(context: Context, uri: Uri, timeUs: Long, maxPx: Int): Bitmap? = try {
        val mmr = android.media.MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val bmp = if (Build.VERSION.SDK_INT >= 27) {
            val w = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxPx
            val h = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxPx
            val f = min(1f, maxPx.toFloat() / max(w, h))
            mmr.getScaledFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, (w * f).toInt(), (h * f).toInt())
        } else {
            mmr.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
        mmr.release()
        bmp?.copy(Bitmap.Config.ARGB_8888, false)
    } catch (_: Throwable) {
        null
    }

    fun gradedName(prefix: String, ext: String) =
        "${prefix}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_graded.$ext"

    /** Grades a photo at full resolution and saves it as a new picture next to the original. */
    fun savePhoto(context: Context, uri: Uri, grade: Grade): Uri? {
        val src = decode(context, uri, 0) ?: return null
        val out = GradeLut(grade).apply(src)
        src.recycle()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, gradedName("PRO", "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val target = resolver.insert(Storage.imagesUri(Storage.load(context)), values) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            out.recycle()
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            target
        } catch (_: Throwable) {
            resolver.delete(target, null, null)
            null
        }
    }

    /** Copies a finished file into the gallery as a new video. */
    fun publishVideo(context: Context, file: java.io.File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, gradedName("PRO", "mp4"))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val target = resolver.insert(Storage.videosUri(Storage.load(context)), values) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { o -> file.inputStream().use { it.copyTo(o, 1 shl 16) } }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            target
        } catch (_: Throwable) {
            resolver.delete(target, null, null)
            null
        }
    }
}

/** Favourites, kept by the app (works for every file and Android version). */
object Favorites {
    private const val PREFS = "procam"
    private const val KEY = "favorites"

    fun all(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun toggle(context: Context, uri: Uri): Boolean {
        val set = all(context).toMutableSet()
        val key = uri.toString()
        val now = if (key in set) { set.remove(key); false } else { set.add(key); true }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
        return now
    }

    fun remove(context: Context, uri: Uri) {
        val set = all(context).toMutableSet()
        if (set.remove(uri.toString())) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
        }
    }
}
