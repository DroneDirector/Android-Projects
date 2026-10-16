package com.dronedirector.acluxpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import com.dronedirector.acluxpro.camera.GradeIO
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class LayerKind(val label: String) { TEXT("Text"), STICKER("Sticker"), WATERMARK("Watermark"), LOGO("Logo"), IMAGE("Image"), VIDEO("Video") }

enum class LayerFont(val label: String, val typeface: Typeface) {
    SANS("Sans", Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)),
    SERIF("Serif", Typeface.create(Typeface.SERIF, Typeface.BOLD)),
    MONO("Mono", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
    LIGHT("Light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
    CONDENSED("Condensed", Typeface.create("sans-serif-condensed", Typeface.BOLD)),
}

val STICKERS = listOf("🔥", "⭐", "❤️", "😂", "😍", "👍", "🎉", "✨", "📍", "➡️", "🎬", "📸", "🌅", "🏔️", "🌊", "🚁", "💯", "⚡", "🙌", "😎", "🎵", "☀️", "🌙", "💥")

/** A layer on top of the video (or photo): text, sticker, watermark text or logo image. */
data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val kind: LayerKind = LayerKind.TEXT,
    val text: String = "Your text",
    val imageUri: Uri? = null,
    val color: Int = 0xFFFFFFFF.toInt(),
    /** Height of the text / sticker / logo as a share of the frame height. */
    val size: Float = 0.07f,
    /** Centre position, 0..1 from the top-left. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val opacity: Float = 1f,
    val font: LayerFont = LayerFont.SANS,
    val shadow: Boolean = true,
    val box: Boolean = false,
    /** Timeline window in ms; ignored when [wholeVideo]. */
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val wholeVideo: Boolean = false,
    val visible: Boolean = true,
    /** Video layers: the overlaid clip, where in it to start, and its shape. */
    val videoUri: Uri? = null,
    val videoDurationMs: Long = 0L,
    val videoStartMs: Long = 0L,
    val mediaAspect: Float = 16f / 9f,
) {
    val isMedia get() = kind == LayerKind.VIDEO || kind == LayerKind.IMAGE || kind == LayerKind.LOGO
    fun activeAt(ms: Long) = visible && (wholeVideo || ms in startMs..endMs)
}

object LayerRenderer {
    private val logoCache = HashMap<String, Bitmap>()

    /** Draws a layer at the size it should have on a [frameW]×[frameH] frame. */
    fun render(context: Context, l: Layer, frameW: Int, frameH: Int): Bitmap? {
        val px = max(8f, l.size * frameH)
        return when (l.kind) {
            LayerKind.VIDEO -> null
            LayerKind.LOGO, LayerKind.IMAGE -> {
                val uri = l.imageUri ?: return null
                val src = synchronized(logoCache) {
                    logoCache.getOrPut(uri.toString()) { GradeIO.decode(context, uri, 1024) ?: return null }
                }
                val h = px.toInt().coerceAtLeast(4)
                val w = (src.width * (h.toFloat() / src.height)).toInt().coerceAtLeast(4)
                Bitmap.createScaledBitmap(src, min(w, frameW), h, true)
            }
            else -> renderText(l, px, frameW)
        }
    }

    private fun renderText(l: Layer, px: Float, frameW: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px
            color = l.color
            typeface = if (l.kind == LayerKind.STICKER) Typeface.DEFAULT else l.font.typeface
            if (l.shadow && l.kind != LayerKind.STICKER) setShadowLayer(px * 0.08f, 0f, px * 0.04f, 0xAA000000.toInt())
        }
        val lines = l.text.ifEmpty { " " }.split('\n')
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val pad = if (l.box) px * 0.35f else px * 0.15f
        val textW = lines.maxOf { paint.measureText(it) }
        val w = (textW + pad * 2).toInt().coerceIn(4, frameW * 2)
        val h = (lineH * lines.size + pad * 2).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (l.box) {
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), px * 0.3f, px * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        }
        lines.forEachIndexed { i, line ->
            val lw = paint.measureText(line)
            c.drawText(line, (w - lw) / 2f, pad - fm.ascent + i * lineH, paint)
        }
        return bmp
    }
}

/** Draws a watermark/logo layer onto a photo (returns a new bitmap). */
fun stampLayer(context: Context, src: Bitmap, l: Layer): Bitmap {
    if (!l.visible) return src
    val mark = LayerRenderer.render(context, l, src.width, src.height) ?: return src
    val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (l.opacity * 255).toInt().coerceIn(0, 255) }
    Canvas(out).drawBitmap(mark, l.x * src.width - mark.width / 2f, l.y * src.height - mark.height / 2f, p)
    return out
}

/** Media3 overlay for one layer: shown only inside its time window. */
@UnstableApi
class LayerOverlay(private val bitmap: Bitmap, l: Layer) : BitmapOverlay() {
    private val blank = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val startUs = l.startMs * 1000
    private val endUs = l.endMs * 1000
    private val whole = l.wholeVideo
    private val settings: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor((l.x * 2f - 1f).coerceIn(-1f, 1f), (1f - l.y * 2f).coerceIn(-1f, 1f))
        .setOverlayFrameAnchor(0f, 0f)
        .setAlphaScale(l.opacity.coerceIn(0f, 1f))
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        if (whole || presentationTimeUs in startUs..endUs) bitmap else blank

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}

/** Background music for the video editor. */
data class MusicTrack(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    /** Where in the song to start. */
    val offsetMs: Long = 0L,
    val volume: Float = 0.8f,
    val fadeInMs: Long = 1000L,
    val fadeOutMs: Long = 2000L,
    /** EQ / dynamics for the music. */
    val fx: AudioFx = AudioFx(),
    /** How much the music drops under clips marked "lower music" (0..1). */
    val duck: Float = 0.6f,
    val id: String = java.util.UUID.randomUUID().toString(),
    /** Where the track starts on the timeline. */
    val startMs: Long = 0L,
    /** How long it plays (0 = until the song or the timeline ends). */
    val lengthMs: Long = 0L,
) {
    /** Length on a timeline of [total] ms. */
    fun playLength(total: Long): Long {
        val avail = (durationMs - offsetMs).coerceAtLeast(0)
        val want = if (lengthMs > 0) lengthMs.coerceAtMost(avail) else avail
        return want.coerceAtMost((total - startMs).coerceAtLeast(0))
    }
}

/** An audio file in the media pool (not necessarily on the timeline). */
data class PoolAudio(val uri: android.net.Uri, val name: String, val durationMs: Long)

/** Gain with fade-in / fade-out for a stream of known length (16-bit PCM). */
@UnstableApi
class FadeAudioProcessor(private val volume: Float, private val fadeInMs: Long, private val fadeOutMs: Long, private val totalMs: Long) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val size = inputBuffer.remaining()
        val out = replaceOutputBuffer(size)
        val ch = fmt.channelCount.coerceAtLeast(1)
        val rate = fmt.sampleRate.coerceAtLeast(1)
        while (inputBuffer.remaining() >= 2 * ch) {
            val tMs = frames * 1000L / rate
            var g = volume
            if (fadeInMs > 0 && tMs < fadeInMs) g *= tMs.toFloat() / fadeInMs
            if (fadeOutMs > 0 && totalMs > 0 && tMs > totalMs - fadeOutMs) g *= ((totalMs - tMs).toFloat() / fadeOutMs).coerceIn(0f, 1f)
            for (c in 0 until ch) {
                val v = (inputBuffer.short * g).toInt().coerceIn(-32768, 32767)
                out.putShort(v.toShort())
            }
            frames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    override fun onFlush() { frames = 0 }
    override fun onReset() { frames = 0 }
}
