package com.dronedirector.acluxpro.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.dronedirector.acluxpro.camera.GradeIO
import java.io.File
import java.util.UUID

/** One clip on the timeline. Times in milliseconds of the source file. */
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val startMs: Long = 0L,
    val endMs: Long = durationMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeIn: Boolean = false,
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
) {
    val lengthMs: Long get() = ((endMs - startMs) / speed).toLong().coerceAtLeast(1)
}

object ClipLoader {
    fun load(context: Context, uri: Uri): Clip? = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        var w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        mmr.release()
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Clip"
        if (dur <= 0) null else Clip(uri = uri, name = name, durationMs = dur, width = w, height = h)
    } catch (_: Throwable) {
        null
    }
}

/** Live preview effects for a clip (same pipeline as the export). */
@UnstableApi
fun clipEffects(c: Clip): List<Effect> = buildList {
    if (!c.grade.isIdentity) add(SingleColorLut.createFromCube(c.grade.cube()))
    if (c.detail.needsVideoPass) add(DetailEffect(c.detail))
}

data class DeliverSettings(
    /** Short side in pixels, 0 = same as the first clip. */
    val shortSide: Int = 0,
    val fps: Int = 0,
    val hevc: Boolean = false,
    /** Mbit/s, 0 = automatic. */
    val bitrateMbps: Int = 0,
    val audio: Boolean = true,
)

/** Renders the timeline to a new video in DCIM/AcluxPro with Media3 Transformer. Start on the main thread. */
@UnstableApi
class TimelineExport(
    private val context: Context,
    private val clips: List<Clip>,
    private val settings: DeliverSettings,
    private val onProgress: (Int) -> Unit,
    private val onDone: (Uri?, String?) -> Unit,
) {
    private val out = File(context.cacheDir, "timeline_${System.currentTimeMillis()}.mp4")
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private lateinit var transformer: Transformer

    fun start() {
        val items = clips.map { c ->
            val media = MediaItem.Builder()
                .setUri(c.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
                )
                .build()
            val audio = mutableListOf<AudioProcessor>()
            val gain = if (c.muted) 0f else c.volume
            if (gain != 1f) {
                audio += ChannelMixingAudioProcessor().apply {
                    putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 1).scaleBy(gain))
                    putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(gain))
                }
            }
            val b = EditedMediaItem.Builder(media)
                .setEffects(Effects(audio, clipEffects(c)))
                .setRemoveAudio(!settings.audio)
            if (c.speed != 1f) {
                val s = c.speed
                b.setSpeed(object : SpeedProvider {
                    override fun getSpeed(timeUs: Long): Float = s
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                })
            }
            b.build()
        }

        val first = clips.first()
        val global = mutableListOf<Effect>()
        if (settings.shortSide > 0) {
            val portrait = first.height > first.width
            val long = settings.shortSide * 16 / 9
            val w = if (portrait) settings.shortSide else long
            val h = if (portrait) long else settings.shortSide
            global += Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)
        } else if (clips.size > 1 && first.width > 0 && first.height > 0) {
            // Mixed clips: fit everything to the first clip's frame.
            global += Presentation.createForWidthAndHeight(first.width, first.height, Presentation.LAYOUT_SCALE_TO_FIT)
        }
        if (settings.fps > 0) global += FrameDropEffect.createDefaultFrameDropEffect(settings.fps.toFloat())

        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .setEffects(Effects(emptyList(), global))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        val encoderSettings = if (settings.bitrateMbps > 0)
            VideoEncoderSettings.Builder().setBitrate(settings.bitrateMbps * 1_000_000).build()
        else VideoEncoderSettings.DEFAULT

        transformer = Transformer.Builder(context)
            .setVideoMimeType(if (settings.hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .setEnableFallback(true)
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    Thread {
                        val uri = GradeIO.publishVideo(context, out)
                        out.delete()
                        handler.post { onDone(uri, if (uri == null) "Couldn't save the video" else null) }
                    }.start()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true
                    out.delete()
                    onDone(null, "Export failed: ${exportException.errorCodeName}")
                }
            })
            .build()
        transformer.start(composition, out.path)
        poll()
    }

    private fun poll() {
        if (finished) return
        val holder = ProgressHolder()
        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
        handler.postDelayed({ poll() }, 400)
    }

    fun cancel() {
        if (finished || !::transformer.isInitialized) return
        finished = true
        transformer.cancel()
        out.delete()
    }
}
