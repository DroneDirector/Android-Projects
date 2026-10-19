package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.FloatBuffer
import java.util.UUID
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Magic Mask (like DaVinci Resolve's Magic Mask): tap or box any person, animal, vehicle or object
 * on one frame; the AI outlines it (MobileSAM — Apache 2.0 — on the phone, through ONNX Runtime —
 * MIT) and it is tracked through the clip: every frame's outline is carried forward with
 * VeyroCut Pro's own optical flow and re-fitted by the AI.
 */

/** A prompt: x, y in 0..1 of the picture; label 1 = object, 0 = not the object, 2 / 3 = box corners. */
data class MaskPoint(val x: Float, val y: Float, val label: Int)

/** A Magic Mask selection on a clip (or a video layer's clip): where it was made and what was picked. */
data class MagicSel(
    val id: String = UUID.randomUUID().toString(),
    /** Source time (ms) of the frame the prompts were made on. */
    val srcMs: Long = 0L,
    val points: List<MaskPoint> = emptyList(),
    /** One-point picks: 0 = the AI's best guess, 1 = whole object, 2 = part, 3 = detail. */
    val choice: Int = 0,
    /** Source range the tracking covers, and how many frames it has (0 = not tracked yet). */
    val fromMs: Long = 0L,
    val toMs: Long = 0L,
    val frames: Int = 0,
)

// ---------------------------------------------------------------- the AI (MobileSAM through ONNX Runtime)

object Sam {
    private val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
    private var env: OrtEnvironment? = null
    private var enc: OrtSession? = null
    private var dec: OrtSession? = null
    @Volatile var failed: String? = null; private set

    /** Loads the model (first use takes a moment). */
    @Synchronized fun ready(context: Context): Boolean {
        if (enc != null && dec != null) return true
        return try {
            val e = OrtEnvironment.getEnvironment()
            fun opts() = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
                try { addXnnpack(mapOf("intra_op_num_threads" to Runtime.getRuntime().availableProcessors().coerceIn(2, 6).toString())) } catch (_: Throwable) {}
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val eb = context.assets.open("sam_encoder.onnx").use { it.readBytes() }
            val db = context.assets.open("sam_decoder.onnx").use { it.readBytes() }
            env = e; enc = e.createSession(eb, opts()); dec = e.createSession(db, opts())
            true
        } catch (t: Throwable) { failed = t.message ?: t.javaClass.simpleName; false }
    }

    /** A picture's AI features, and how it was scaled into the model's 1024 × 1024 square. */
    class Embedding(val data: FloatArray, val k: Float, val w: Int, val h: Int)

    /** Features of [bmp] (any size). Slow (≈ 0.3–1.5 s): call off the main thread. */
    fun encode(context: Context, bmp: Bitmap): Embedding? {
        if (!ready(context)) return null
        val k = 1024f / max(bmp.width, bmp.height)
        val sw = (bmp.width * k).roundToInt().coerceIn(1, 1024); val sh = (bmp.height * k).roundToInt().coerceIn(1, 1024)
        val s = if (sw == bmp.width && sh == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, sw, sh, true)
        val px = IntArray(sw * sh); s.getPixels(px, 0, sw, 0, 0, sw, sh)
        val n = 1024 * 1024
        val chw = FloatArray(3 * n)
        // Padding gets the mean colour, so it is zero after the model's normalisation (as in training).
        for (c in 0 until 3) java.util.Arrays.fill(chw, c * n, (c + 1) * n, MEAN[c])
        for (y in 0 until sh) for (x in 0 until sw) {
            val p = px[y * sw + x]; val i = y * 1024 + x
            chw[i] = ((p shr 16) and 255).toFloat(); chw[n + i] = ((p shr 8) and 255).toFloat(); chw[2 * n + i] = (p and 255).toFloat()
        }
        val e = env ?: return null
        return try {
            OnnxTensor.createTensor(e, FloatBuffer.wrap(chw), longArrayOf(1, 3, 1024, 1024)).use { t ->
                enc!!.run(mapOf("image" to t)).use { r ->
                    val fb = (r[0] as OnnxTensor).floatBuffer
                    val out = FloatArray(fb.remaining()); fb.get(out)
                    Embedding(out, k, bmp.width, bmp.height)
                }
            }
        } catch (t: Throwable) { failed = t.message; null }
    }

    /** Four answers (256 × 256 logits each, in the model's square) and their scores. */
    class Answer(val logits: FloatArray, val scores: FloatArray) {
        fun mask(i: Int): FloatArray = logits.copyOfRange(i * 65536, (i + 1) * 65536)
    }

    /** Runs the prompts (and optionally the previous frame's answer as a hint) on [e]. Fast (≈ 30 ms). */
    fun decode(e: Embedding, pts: List<MaskPoint>, hint: FloatArray? = null): Answer? {
        val en = env ?: return null
        val list = ArrayList<FloatArray>()
        for (p in pts) list += floatArrayOf(p.x * e.w * e.k, p.y * e.h * e.k, p.label.toFloat())
        // Points without a box need SAM's padding point.
        if (pts.none { it.label == 2 || it.label == 3 }) list += floatArrayOf(0f, 0f, -1f)
        val n = list.size
        val coords = FloatArray(n * 2) { list[it / 2][it % 2] }
        val labels = FloatArray(n) { list[it][2] }
        return try {
            val emb = OnnxTensor.createTensor(en, FloatBuffer.wrap(e.data), longArrayOf(1, 256, 64, 64))
            val c = OnnxTensor.createTensor(en, FloatBuffer.wrap(coords), longArrayOf(1, n.toLong(), 2))
            val l = OnnxTensor.createTensor(en, FloatBuffer.wrap(labels), longArrayOf(1, n.toLong()))
            val m = OnnxTensor.createTensor(en, FloatBuffer.wrap(hint ?: FloatArray(65536)), longArrayOf(1, 1, 256, 256))
            val h = OnnxTensor.createTensor(en, FloatBuffer.wrap(floatArrayOf(if (hint != null) 1f else 0f)), longArrayOf(1))
            try {
                dec!!.run(mapOf("embeddings" to emb, "coords" to c, "labels" to l, "mask_input" to m, "has_mask" to h)).use { r ->
                    val mb = (r[0] as OnnxTensor).floatBuffer; val sb = (r[1] as OnnxTensor).floatBuffer
                    val logits = FloatArray(mb.remaining()); mb.get(logits)
                    val scores = FloatArray(sb.remaining()); sb.get(scores)
                    Answer(logits, scores)
                }
            } finally { emb.close(); c.close(); l.close(); m.close(); h.close() }
        } catch (t: Throwable) { failed = t.message; null }
    }

    /** Which answer to use: several prompts → SAM's single answer; one point → by [choice] (whole / part / detail) or best score. */
    fun pick(a: Answer, pts: List<MaskPoint>, choice: Int): Int {
        if (pts.size != 1 || pts[0].label != 1) return 0
        if (choice == 0) return (1..3).maxBy { a.scores[it] }
        // Order the three answers by size: whole = largest.
        val areas = (1..3).map { i -> i to (0 until 65536).count { a.logits[i * 65536 + it] > 0f } }.sortedByDescending { it.second }
        return areas[(choice - 1).coerceIn(0, 2)].first
    }

    /** A logits square → an 8-bit mask of [ow] × [oh] covering the picture. */
    fun toMask(logits: FloatArray, e: Embedding, ow: Int, oh: Int): ByteArray {
        val out = ByteArray(ow * oh)
        val sx = e.w * e.k / 4f / ow; val sy = e.h * e.k / 4f / oh
        for (y in 0 until oh) for (x in 0 until ow) {
            val v = bilinear(logits, 256, 256, (x + 0.5f) * sx - 0.5f, (y + 0.5f) * sy - 0.5f)
            out[y * ow + x] = (255f / (1f + exp(-v * 1.5f))).roundToInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    fun bilinear(a: FloatArray, w: Int, h: Int, x0: Float, y0: Float): Float {
        val x = x0.coerceIn(0f, w - 1f); val y = y0.coerceIn(0f, h - 1f)
        val xi = x.toInt(); val yi = y.toInt(); val x1 = min(xi + 1, w - 1); val y1 = min(yi + 1, h - 1)
        val fx = x - xi; val fy = y - yi
        val t = a[yi * w + xi] + (a[yi * w + x1] - a[yi * w + xi]) * fx
        val b = a[y1 * w + xi] + (a[y1 * w + x1] - a[y1 * w + xi]) * fx
        return t + (b - t) * fy
    }
}

// ---------------------------------------------------------------- tracked masks on disk

/** The outline of a selection on every analysed frame (source ms), 8-bit, [w] × [h]. */
class MaskTrack(val w: Int, val h: Int, val times: LongArray, val masks: Array<ByteArray>) {
    /** The mask at [srcMs], blended between the two nearest analysed frames. */
    fun at(srcMs: Long, out: ByteArray) {
        if (times.isEmpty()) { java.util.Arrays.fill(out, 0); return }
        var i = java.util.Arrays.binarySearch(times, srcMs)
        if (i >= 0) { System.arraycopy(masks[i], 0, out, 0, out.size); return }
        i = -i - 1
        if (i <= 0) { System.arraycopy(masks[0], 0, out, 0, out.size); return }
        if (i >= times.size) { System.arraycopy(masks[times.size - 1], 0, out, 0, out.size); return }
        val a = masks[i - 1]; val b = masks[i]
        val f = ((srcMs - times[i - 1]).toFloat() / (times[i] - times[i - 1]).coerceAtLeast(1)).coerceIn(0f, 1f)
        for (k in out.indices) out[k] = ((a[k].toInt() and 255) * (1 - f) + (b[k].toInt() and 255) * f).roundToInt().toByte()
    }

    companion object {
        private fun file(context: Context, id: String) = File(File(context.filesDir, "magicmask").apply { mkdirs() }, "$id.bin")
        private val cache = HashMap<String, MaskTrack>()

        fun save(context: Context, id: String, t: MaskTrack) {
            DataOutputStream(DeflaterOutputStream(file(context, id).outputStream().buffered())).use { o ->
                o.writeInt(t.w); o.writeInt(t.h); o.writeInt(t.times.size)
                for (k in t.times.indices) { o.writeLong(t.times[k]); o.write(t.masks[k]) }
            }
            synchronized(cache) { cache[id] = t }
        }

        fun load(context: Context, id: String?): MaskTrack? {
            if (id == null) return null
            synchronized(cache) { cache[id]?.let { return it } }
            val f = file(context, id)
            if (!f.exists()) return null
            return try {
                DataInputStream(InflaterInputStream(f.inputStream().buffered())).use { i ->
                    val w = i.readInt(); val h = i.readInt(); val n = i.readInt()
                    val ts = LongArray(n); val ms = Array(n) { ByteArray(w * h) }
                    for (k in 0 until n) { ts[k] = i.readLong(); i.readFully(ms[k]) }
                    MaskTrack(w, h, ts, ms).also { synchronized(cache) { cache[id] = it } }
                }
            } catch (_: Throwable) { null }
        }
    }
}

// ---------------------------------------------------------------- reading frames in order

/** Decodes a video's frames in order as RGB bitmaps of at most [maxSide] px (rotation applied). */
object FrameReader {
    /** Calls [onFrame] for the frames from [fromMs] to [toMs], at least [stepMs] apart; return false from it to stop. */
    fun read(context: Context, uri: Uri, fromMs: Long, toMs: Long, maxSide: Int, stepMs: Long, onFrame: (Long, Bitmap) -> Boolean) {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var track = -1; var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; fmt = f; break } }
        if (track < 0 || fmt == null) { ex.release(); return }
        ex.selectTrack(track)
        val rotation = if (fmt.containsKey("rotation-degrees")) fmt.getInteger("rotation-degrees") else 0
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        var next = fromMs
        try {
            while (!outDone) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                        if (n < 0 || ex.sampleTime > toMs * 1000 + 150_000) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    val t = info.presentationTimeUs / 1000
                    if (info.size > 0 && t >= next && t <= toMs) {
                        val img = codec.getOutputImage(oi)
                        val bmp = img?.let { toBitmap(it, maxSide, rotation) }
                        img?.close()
                        codec.releaseOutputBuffer(oi, false)
                        if (bmp != null) { next = t + stepMs; if (!onFrame(t, bmp)) outDone = true }
                        continue
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (t > toMs) outDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release(); ex.release()
        }
    }

    /** YUV 4:2:0 (8- or 10-bit) → RGB at the target size. */
    private fun toBitmap(img: android.media.Image, maxSide: Int, rotation: Int): Bitmap {
        val cw = img.cropRect.width(); val ch = img.cropRect.height(); val cx = img.cropRect.left; val cy = img.cropRect.top
        val k = min(1f, maxSide.toFloat() / max(cw, ch))
        val ow = (cw * k).roundToInt().coerceAtLeast(2); val oh = (ch * k).roundToInt().coerceAtLeast(2)
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
        val ys = yP.rowStride; val yps = yP.pixelStride; val us = uP.rowStride; val ups = uP.pixelStride; val vs = vP.rowStride; val vps = vP.pixelStride
        // 10-bit frames (two bytes per sample): read the high byte.
        val hi = if (yps == 2) 1 else 0
        val px = IntArray(ow * oh)
        for (y in 0 until oh) {
            val sy = cy + ((y + 0.5f) / k).toInt().coerceAtMost(ch - 1)
            for (x in 0 until ow) {
                val sx = cx + ((x + 0.5f) / k).toInt().coerceAtMost(cw - 1)
                val Y = (yB.get(sy * ys + sx * yps + hi).toInt() and 255) - 16
                val U = (uB.get((sy / 2) * us + (sx / 2) * ups + (if (ups >= 2 && yps == 2) 1 else 0)).toInt() and 255) - 128
                val V = (vB.get((sy / 2) * vs + (sx / 2) * vps + (if (vps >= 2 && yps == 2) 1 else 0)).toInt() and 255) - 128
                val c = 1.164f * Y
                val r = (c + 1.596f * V).toInt().coerceIn(0, 255)
                val g = (c - 0.392f * U - 0.813f * V).toInt().coerceIn(0, 255)
                val b = (c + 2.017f * U).toInt().coerceIn(0, 255)
                px[y * ow + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bmp = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        return if (rotation == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, ow, oh, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    /** One frame at [srcMs] (exact), or null. */
    fun frameAt(context: Context, uri: Uri, srcMs: Long, maxSide: Int): Bitmap? {
        var out: Bitmap? = null
        read(context, uri, srcMs, srcMs + 1000, maxSide, 1) { _, b -> out = b; false }
        return out
    }
}

// ---------------------------------------------------------------- tracking

object MagicTracker {
    /** Stored mask size (long side): the export refines edges against the full picture. */
    private const val MASK_SIDE = 384
    /** Motion is measured at this size between neighbouring frames. */
    private const val FLOW_SIDE = 256

    private fun small(b: Bitmap, side: Int): Bitmap {
        val k = side.toFloat() / max(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(16), (b.height * k).roundToInt().coerceAtLeast(16), true)
    }

    /** The selection on its own frame, for the picker (mask at [ow] × [oh]). */
    fun preview(e: Sam.Embedding, sel: MagicSel, ow: Int, oh: Int): ByteArray? {
        if (sel.points.isEmpty()) return null
        val a = Sam.decode(e, sel.points) ?: return null
        return Sam.toMask(a.mask(Sam.pick(a, sel.points, sel.choice)), e, ow, oh)
    }

    private class State(var logits: FloatArray, var mask: ByteArray, var frame: Bitmap)

    /**
     * Tracks [sel] through [fromMs]..[toMs] (source time) of [uri]. Each step: the previous outline is
     * carried along the optical flow, turned into prompts (box + the point deepest inside) plus the
     * carried outline itself, and the AI fits it to the new frame; if the AI's answer disagrees wildly
     * (the object is hidden), the carried outline is kept.
     */
    fun track(context: Context, uri: Uri, fromMs: Long, toMs: Long, sel: MagicSel, onProgress: (Int) -> Unit, cancelled: () -> Boolean): MaskTrack? {
        if (!Sam.ready(context)) return null
        val f0 = FrameReader.frameAt(context, uri, sel.srcMs, 1024) ?: return null
        val e0 = Sam.encode(context, f0) ?: return null
        val a0 = Sam.decode(e0, sel.points) ?: return null
        val pick = Sam.pick(a0, sel.points, sel.choice)
        val k = MASK_SIDE.toFloat() / max(f0.width, f0.height)
        val mw = (f0.width * k).roundToInt().coerceAtLeast(16); val mh = (f0.height * k).roundToInt().coerceAtLeast(16)
        val first = State(a0.mask(pick), Sam.toMask(a0.mask(pick), e0, mw, mh), small(f0, FLOW_SIDE))
        val out = java.util.TreeMap<Long, ByteArray>()
        out[sel.srcMs] = first.mask
        // Frame step: every frame up to 30 per second.
        val fps = frameRate(context, uri).coerceIn(1f, 240f)
        val step = max(1000f / fps, 1000f / 30f).toLong().coerceAtLeast(1) - 1
        val total = ((toMs - fromMs).coerceAtLeast(1) / (step + 1)).coerceAtLeast(1)
        var done = 0L
        fun progress() { done++; onProgress((done * 100 / total).toInt().coerceIn(0, 99)) }

        // Forward from the picked frame.
        var st = first
        FrameReader.read(context, uri, sel.srcMs + 1, toMs, 1024, step) { t, bmp ->
            if (cancelled()) return@read false
            st = advance(context, st, bmp, mw, mh) ?: st
            out[t] = st.mask
            progress(); true
        }
        if (cancelled()) return null
        // Backward: frames before the picked one are decoded forwards into a temporary folder, then walked back.
        if (sel.srcMs > fromMs) {
            val tmp = File(context.cacheDir, "mm_${System.nanoTime()}").apply { mkdirs() }
            try {
                val list = ArrayList<Pair<Long, File>>()
                FrameReader.read(context, uri, fromMs, sel.srcMs - 1, 1024, step) { t, bmp ->
                    if (cancelled()) return@read false
                    val f = File(tmp, "$t.jpg"); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                    list += t to f; true
                }
                st = first
                for ((t, f) in list.asReversed()) {
                    if (cancelled()) return null
                    val bmp = BitmapFactory.decodeFile(f.path) ?: continue
                    st = advance(context, st, bmp, mw, mh) ?: st
                    out[t] = st.mask
                    progress()
                }
            } finally { tmp.deleteRecursively() }
        }
        if (cancelled()) return null
        onProgress(100)
        return MaskTrack(mw, mh, out.keys.toLongArray(), out.values.toTypedArray())
    }

    private fun frameRate(context: Context, uri: Uri): Float = try {
        val ex = MediaExtractor(); ex.setDataSource(context, uri, null)
        var r = 30f
        for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true && f.containsKey(MediaFormat.KEY_FRAME_RATE)) { r = f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat(); break } }
        ex.release(); r
    } catch (_: Throwable) { 30f }

    /** One step: carry the outline from [prev] onto [frame] and let the AI fit it. */
    private fun advance(context: Context, prev: State, frame: Bitmap, mw: Int, mh: Int): State? {
        val sm = small(frame, FLOW_SIDE)
        val ps = if (prev.frame.width == sm.width && prev.frame.height == sm.height) prev.frame else Bitmap.createScaledBitmap(prev.frame, sm.width, sm.height, true)
        val fw = sm.width; val fh = sm.height
        // Motion from this frame back to the previous one: where each pixel was.
        val flow = FloatArray(fw * fh * 2)
        val haveFlow = MorphNative.available && try { MorphNative.flow(sm, ps, flow) } catch (_: Throwable) { false }
        fun back(x: Float, y: Float): Pair<Float, Float> {
            if (!haveFlow) return x to y
            val fx = (x * fw - 0.5f); val fy = (y * fh - 0.5f)
            val xi = fx.roundToInt().coerceIn(0, fw - 1); val yi = fy.roundToInt().coerceIn(0, fh - 1)
            val q = (yi * fw + xi) * 2
            return (x + flow[q] / fw) to (y + flow[q + 1] / fh)
        }
        // Carried outline (stored size) and carried logits (model square).
        val carried = ByteArray(mw * mh)
        for (y in 0 until mh) for (x in 0 until mw) {
            val (sx, sy) = back((x + 0.5f) / mw, (y + 0.5f) / mh)
            carried[y * mw + x] = bilinearB(prev.mask, mw, mh, sx * mw - 0.5f, sy * mh - 0.5f).toByte()
        }
        val e = Sam.encode(context, frame) ?: return State(prev.logits, carried, sm)
        val sqW = e.w * e.k; val sqH = e.h * e.k
        val hint = FloatArray(65536)
        for (y in 0 until 256) for (x in 0 until 256) {
            val px = (x + 0.5f) * 4f; val py = (y + 0.5f) * 4f
            if (px > sqW || py > sqH) { hint[y * 256 + x] = -20f; continue }
            val (sx, sy) = back(px / sqW, py / sqH)
            hint[y * 256 + x] = Sam.bilinear(prev.logits, 256, 256, sx * sqW / 4f - 0.5f, sy * sqH / 4f - 0.5f)
        }
        // Prompts from the carried outline: its box, and the point deepest inside it.
        var x0 = mw; var y0 = mh; var x1 = -1; var y1 = -1; var area = 0
        for (y in 0 until mh) for (x in 0 until mw) if ((carried[y * mw + x].toInt() and 255) > 127) { area++; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        if (area < 4) return State(hint, carried, sm)   // gone from the picture
        val padX = ((x1 - x0) * 0.04f + 2); val padY = ((y1 - y0) * 0.04f + 2)
        val deep = deepest(carried, mw, mh)
        val pts = listOf(
            MaskPoint((deep.first + 0.5f) / mw, (deep.second + 0.5f) / mh, 1),
            MaskPoint(((x0 - padX) / mw).coerceIn(0f, 1f), ((y0 - padY) / mh).coerceIn(0f, 1f), 2),
            MaskPoint(((x1 + 1 + padX) / mw).coerceIn(0f, 1f), ((y1 + 1 + padY) / mh).coerceIn(0f, 1f), 3),
        )
        val a = Sam.decode(e, pts, hint) ?: return State(hint, carried, sm)
        val logits = a.mask(0)
        val fitted = Sam.toMask(logits, e, mw, mh)
        // Sanity: an answer far from the carried outline means the AI lost it — keep the carried one.
        var inter = 0; var uni = 0
        for (i in fitted.indices) { val p = (fitted[i].toInt() and 255) > 127; val c = (carried[i].toInt() and 255) > 127; if (p && c) inter++; if (p || c) uni++ }
        return if (uni > 0 && inter.toFloat() / uni < 0.35f) State(hint, carried, sm) else State(logits, fitted, sm)
    }

    private fun bilinearB(a: ByteArray, w: Int, h: Int, x0: Float, y0: Float): Int {
        val x = x0.coerceIn(0f, w - 1f); val y = y0.coerceIn(0f, h - 1f)
        val xi = x.toInt(); val yi = y.toInt(); val x1 = min(xi + 1, w - 1); val y1 = min(yi + 1, h - 1)
        val fx = x - xi; val fy = y - yi
        fun g(i: Int) = (a[i].toInt() and 255).toFloat()
        val t = g(yi * w + xi) + (g(yi * w + x1) - g(yi * w + xi)) * fx
        val b = g(y1 * w + xi) + (g(y1 * w + x1) - g(y1 * w + xi)) * fx
        return (t + (b - t) * fy).roundToInt().coerceIn(0, 255)
    }

    /** The pixel deepest inside the mask (largest distance to its edge, two-pass chamfer). */
    private fun deepest(m: ByteArray, w: Int, h: Int): Pair<Int, Int> {
        val d = FloatArray(w * h) { if ((m[it].toInt() and 255) > 127) 1e9f else 0f }
        val s2 = sqrt(2f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (d[i] == 0f) continue
            var v = d[i]
            if (x > 0) v = min(v, d[i - 1] + 1); if (y > 0) v = min(v, d[i - w] + 1)
            if (x > 0 && y > 0) v = min(v, d[i - w - 1] + s2); if (x < w - 1 && y > 0) v = min(v, d[i - w + 1] + s2)
            if (x == 0 || y == 0) v = min(v, 1f)
            d[i] = v
        }
        var best = 0; var bv = -1f
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x; if (d[i] == 0f) continue
            var v = d[i]
            if (x < w - 1) v = min(v, d[i + 1] + 1); if (y < h - 1) v = min(v, d[i + w] + 1)
            if (x < w - 1 && y < h - 1) v = min(v, d[i + w + 1] + s2); if (x > 0 && y < h - 1) v = min(v, d[i + w - 1] + s2)
            if (x == w - 1 || y == h - 1) v = min(v, 1f)
            d[i] = v
            if (v > bv) { bv = v; best = i }
        }
        return (best % w) to (best / w)
    }
}
