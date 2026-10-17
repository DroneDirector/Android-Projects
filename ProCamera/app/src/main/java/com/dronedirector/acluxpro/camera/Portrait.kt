package com.dronedirector.acluxpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Portrait mode (Smart → PORTRAIT): after the shot, the person (ML Kit selfie segmentation, on the
 * phone) — or any other subject (the editor's subject model, or Cloud AI when it's on) — is kept
 * sharp and the background is blurred like a fast lens, with a soft, halo-free edge.
 */
object Portrait {
    /** Blur strengths, shown as lens apertures. */
    val APERTURES = listOf("f/1.4", "f/2", "f/2.8", "f/4", "f/5.6")
    private val STRENGTH = floatArrayOf(1f, 0.75f, 0.55f, 0.38f, 0.24f)
    private const val MAX_SIDE = 3264
    private const val MASK = 512

    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
        )
    }

    /** Person mask (0..1) at [mw]×[mh], or null if no person is found. */
    private fun personMask(small: Bitmap): FloatArray? = try {
        val m = Tasks.await(segmenter.process(InputImage.fromBitmap(small, 0)))
        val buf = m.buffer.order(ByteOrder.nativeOrder()); buf.rewind()
        val raw = FloatArray(m.width * m.height) { buf.float }
        val out = if (m.width == small.width && m.height == small.height) raw else resample(raw, m.width, m.height, small.width, small.height)
        val cover = out.count { it > 0.5f }.toFloat() / out.size
        if (cover < 0.02f) null else out
    } catch (_: Throwable) { null }

    /** Any subject: the editor's AI subject selection (cloud when chosen, else on the phone). */
    private fun subjectMask(context: Context, small: Bitmap): FloatArray? = try {
        val useCloud = CloudAi.usePhotos(context)
        val mb = com.dronedirector.acluxpro.editor.AiSegment.subject(context, small, useCloud)
        mb?.let { b ->
            val s = if (b.width == small.width && b.height == small.height) b else Bitmap.createScaledBitmap(b, small.width, small.height, true)
            val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
            val out = FloatArray(px.size) { ((px[it] shr 8) and 0xFF) / 255f }
            if (out.count { it > 0.5f } < out.size * 0.02f) null else out
        }
    } catch (_: Throwable) { null }

    private fun resample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray = FloatArray(dw * dh) { i ->
        val x = (i % dw) * (sw - 1f) / max(1, dw - 1); val y = (i / dw) * (sh - 1f) / max(1, dh - 1)
        val x0 = x.toInt().coerceAtMost(sw - 2).coerceAtLeast(0); val y0 = y.toInt().coerceAtMost(sh - 2).coerceAtLeast(0)
        val fx = x - x0; val fy = y - y0
        val a = src[y0 * sw + x0]; val b = src[y0 * sw + min(x0 + 1, sw - 1)]
        val c = src[min(y0 + 1, sh - 1) * sw + x0]; val d = src[min(y0 + 1, sh - 1) * sw + min(x0 + 1, sw - 1)]
        (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }

    /** Box blur of a float plane, [passes] times (≈ gaussian). */
    private fun blurPlane(p: FloatArray, w: Int, h: Int, r: Int, passes: Int = 3) {
        if (r < 1) return
        val tmp = FloatArray(max(w, h))
        repeat(passes) {
            for (y in 0 until h) {
                val o = y * w
                var acc = 0f
                for (x in -r..r) acc += p[o + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[x] = acc / (2 * r + 1)
                    acc += p[o + (x + r + 1).coerceAtMost(w - 1)] - p[o + (x - r).coerceAtLeast(0)]
                }
                System.arraycopy(tmp, 0, p, o, w)
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += p[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    tmp[y] = acc / (2 * r + 1)
                    acc += p[(y + r + 1).coerceAtMost(h - 1) * w + x] - p[(y - r).coerceAtLeast(0) * w + x]
                }
                for (y in 0 until h) p[y * w + x] = tmp[y]
            }
        }
    }

    /** Background blur on an upright picture; null if no subject was found. */
    fun render(context: Context, img: Bitmap, aperture: Int): Bitmap? {
        val w = img.width; val h = img.height
        val ms = MASK.toFloat() / max(w, h)
        val mw = (w * ms).roundToInt().coerceAtLeast(8); val mh = (h * ms).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(img, mw, mh, true)
        val mask = personMask(small) ?: subjectMask(context, small) ?: return null
        // Firm up, then soften the edge a little (no hard cut-out look).
        for (i in mask.indices) mask[i] = ((mask[i] - 0.35f) / 0.4f).coerceIn(0f, 1f)
        blurPlane(mask, mw, mh, 1, 2)

        // Blur at a reduced size (fast, and a large blur looks the same).
        val strength = STRENGTH[aperture.coerceIn(0, STRENGTH.size - 1)]
        val bs = 4
        val bw = (w / bs).coerceAtLeast(8); val bh = (h / bs).coerceAtLeast(8)
        val bsmall = Bitmap.createScaledBitmap(img, bw, bh, true)
        val px = IntArray(bw * bh); bsmall.getPixels(px, 0, bw, 0, 0, bw, bh)
        bsmall.recycle()
        val bmask = resample(mask, mw, mh, bw, bh)
        // Only the background is blurred (subject colours don't bleed out as a halo):
        // blur(colour × background) / blur(background).
        val R = FloatArray(px.size); val G = FloatArray(px.size); val B = FloatArray(px.size); val W = FloatArray(px.size)
        for (i in px.indices) {
            val bg = 1f - bmask[i]
            val c = px[i]
            R[i] = ((c shr 16) and 0xFF) * bg; G[i] = ((c shr 8) and 0xFF) * bg; B[i] = (c and 0xFF) * bg; W[i] = bg
        }
        val radius = (max(bw, bh) * 0.018f * strength).roundToInt().coerceAtLeast(1)
        for (pl in listOf(R, G, B, W)) blurPlane(pl, bw, bh, radius)
        for (i in px.indices) {
            val ww = W[i]
            px[i] = if (ww > 0.02f) {
                (0xFF shl 24) or ((R[i] / ww).toInt().coerceIn(0, 255) shl 16) or ((G[i] / ww).toInt().coerceIn(0, 255) shl 8) or (B[i] / ww).toInt().coerceIn(0, 255)
            } else px[i]
        }
        val blurredSmall = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, w, h, true)
        blurredSmall.recycle()

        // Composite row by row: subject from the original, background from the blur.
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rowO = IntArray(w); val rowB = IntArray(w)
        for (y in 0 until h) {
            img.getPixels(rowO, 0, w, 0, y, w, 1)
            blurred.getPixels(rowB, 0, w, 0, y, w, 1)
            val my = y * (mh - 1f) / max(1, h - 1)
            val y0 = my.toInt().coerceAtMost(mh - 2).coerceAtLeast(0); val fy = my - y0
            for (x in 0 until w) {
                val mx = x * (mw - 1f) / max(1, w - 1)
                val x0 = mx.toInt().coerceAtMost(mw - 2).coerceAtLeast(0); val fx = mx - x0
                val a = (mask[y0 * mw + x0] * (1 - fx) + mask[y0 * mw + x0 + 1] * fx) * (1 - fy) +
                    (mask[(y0 + 1) * mw + x0] * (1 - fx) + mask[(y0 + 1) * mw + x0 + 1] * fx) * fy
                val o = rowO[x]; val b = rowB[x]
                val r = (((o shr 16) and 0xFF) * a + ((b shr 16) and 0xFF) * (1 - a)).toInt()
                val g = (((o shr 8) and 0xFF) * a + ((b shr 8) and 0xFF) * (1 - a)).toInt()
                val bl = ((o and 0xFF) * a + (b and 0xFF) * (1 - a)).toInt()
                rowO[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            out.setPixels(rowO, 0, w, 0, y, w, 1)
        }
        blurred.recycle(); small.recycle()
        return out
    }

    /**
     * Blurs the background of a saved JPEG. Rewrites it in place (or saves a copy next to it when
     * [keepOriginal]), keeping its camera info. Returns the portrait's uri, or null if nothing was found.
     */
    fun applyToSaved(context: Context, uri: Uri, aperture: Int, keepOriginal: Boolean): Uri? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val exif = ExifInterface(bytes.inputStream())
        val rot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (max(bmp.width, bmp.height) > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
        }
        if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        val out = render(context, bmp, aperture) ?: return null
        bmp.recycle()
        val tmp = java.io.File(context.cacheDir, "portrait_${System.currentTimeMillis()}.jpg")
        try {
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            out.recycle()
            try {
                val o = ExifInterface(tmp.path)
                for (tag in listOf(
                    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
                )) exif.getAttribute(tag)?.let { o.setAttribute(tag, it) }
                o.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                o.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "AcluxPro portrait ${APERTURES[aperture.coerceIn(0, APERTURES.size - 1)]}")
                o.saveAttributes()
            } catch (_: Throwable) {}
            return if (keepOriginal) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "PRO_" + System.currentTimeMillis() + "_portrait.jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val target = Storage.insert(context, Storage.load(context), values, Storage.Kind.IMAGE) ?: return null
                context.contentResolver.openOutputStream(target)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                Storage.publish(context, target)
                target
            } else {
                context.contentResolver.openOutputStream(uri, "wt")?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                uri
            }
        } finally { tmp.delete() }
    }
}
