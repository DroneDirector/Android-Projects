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
    val audio: AudioFx = AudioFx(),
    /** Transition into the next clip. */
    val transition: Transition = Transition(),
    /** Colour masks: parts of the picture with their own grade (Color → Masks). */
    val masks: List<PhotoMask>? = null,
    val reframe: Reframe = Reframe(),
    val stab: Stabilize = Stabilize(),
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    /** Stream facts used to decide on a preview proxy. */
    val mime: String = "",
    val hdr: Boolean = false,
    val tenBit: Boolean = false,
    /** Anamorphic squeeze to undo (null / 1 = none), e.g. 1.33 or 1.8. */
    val desqueeze: Float? = null,
    /** Speed ramp: multipliers through the clip (null = constant speed) and its name. */
    val ramp: List<Float>? = null,
    val rampName: String? = null,
    /** Volume keyframes: (source ms from the clip start, gain 0..2). */
    val volKeys: List<VolKey>? = null,
) {
    val lengthMs: Long get() = (if (ramp != null) rampedLength() else ((endMs - startMs) / speed).toLong()).coerceAtLeast(1)
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
        val info = StreamProbe.probe(context, uri)
        if (dur <= 0) null else Clip(uri = uri, name = name, durationMs = dur, width = w, height = h, mime = info.mime, hdr = info.hdr, tenBit = info.tenBit)
    } catch (_: Throwable) {
        null
    }
}

/** Live preview effects for a clip (same pipeline as the export). */
@UnstableApi
fun clipEffects(c: Clip): List<Effect> = buildList {
    // A fresh LUT effect every time: an effect instance holds a GPU texture that belongs to one
    // player's GL context and must never be shared between the two preview players or the export.
    if (!c.grade.isIdentity) add(SingleColorLut.createFromCube(LutCache.cube(c.grade)))
    if (c.detail.needsVideoPass) add(DetailEffect(c.detail))
    c.activeMasks().takeIf { it.isNotEmpty() }?.let { add(MaskGradeEffect(it)) }
}

data class DeliverSettings(
    /** Short side in pixels, 0 = same as the first clip. */
    val shortSide: Int = 0,
    val fps: Int = 0,
    val hevc: Boolean = false,
    /** Mbit/s, 0 = automatic. */
    val bitrateMbps: Int = 0,
    val audio: Boolean = true,
    /** Project frame shape (width / height), 0 = same as the first clip. */
    val aspect: Float = 0f,
    /** true = fill the frame (crop), false = fit inside (bars). */
    val fill: Boolean = true,
    /** Save only the sound (AAC in an .m4a file, to Music/AcluxPro). */
    val audioOnly: Boolean = false,
    /** Export only this part of the timeline (ms); null = the whole edit. */
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
)

/** One-tap delivery settings for common destinations. */
data class ExportPreset(val label: String, val shortSide: Int, val fps: Int, val hevc: Boolean, val mbps: Int, val aspect: Float)

val EXPORT_PRESETS = listOf(
    ExportPreset("YouTube 4K", 2160, 0, false, 50, 16f / 9f),
    ExportPreset("YouTube 1080p", 1080, 0, false, 20, 16f / 9f),
    ExportPreset("Instagram Reel", 1080, 30, false, 20, 9f / 16f),
    ExportPreset("Instagram 4:5", 1080, 30, false, 20, 0.8f),
    ExportPreset("TikTok", 1080, 30, false, 20, 9f / 16f),
    ExportPreset("Square 1:1", 1080, 30, false, 20, 1f),
    ExportPreset("Small file", 720, 30, true, 0, 0f),
)

/** The part of an edit between [a] and [b] (timeline ms) as its own clips, layers and audio. */
data class RangeCut(val clips: List<Clip>, val layers: List<Layer>, val music: MusicTrack?, val extra: List<MusicTrack>)

fun cutRange(clips: List<Clip>, layers: List<Layer>, music: MusicTrack?, extra: List<MusicTrack>, a: Long, b: Long): RangeCut {
    val st = clipStarts(clips)
    val total = timelineLength(clips)
    val out = mutableListOf<Clip>()
    clips.forEachIndexed { i, c ->
        val s0 = st[i]; val e0 = s0 + c.lengthMs
        val from = maxOf(a, s0); val to = minOf(b, e0)
        if (to - from < 40) return@forEachIndexed
        val ns = c.srcAtOffset(from - s0)
        val ne = c.srcAtOffset(to - s0).coerceAtMost(c.endMs)
        // A ramp is kept for whole clips only (a part of a ramp would replay the whole curve).
        out += c.copy(id = java.util.UUID.randomUUID().toString(), startMs = ns, endMs = ne, ramp = if (ns == c.startMs && ne == c.endMs) c.ramp else null)
    }
    if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(transition = Transition())
    val len = (b - a).coerceAtLeast(1)
    val ls = layers.mapNotNull { l ->
        if (l.wholeVideo) l else {
            val s = l.startMs - a; val e = l.endMs - a
            if (e <= 0 || s >= len) null else l.copy(startMs = s.coerceAtLeast(0), endMs = e.coerceAtMost(len), videoStartMs = l.videoStartMs + (if (s < 0) -s else 0))
        }
    }
    fun shift(m: MusicTrack): MusicTrack? {
        val play = m.playLength(total)
        val s = m.startMs - a
        val e = s + play
        if (e <= 0 || s >= len) return null
        val cut = if (s < 0) -s else 0
        return m.copy(startMs = s.coerceAtLeast(0), offsetMs = m.offsetMs + cut, lengthMs = (play - cut).coerceAtLeast(1))
    }
    return RangeCut(out, ls, music?.let { shift(it) }, extra.mapNotNull { shift(it) })
}

/** Output frame size for the project (even numbers, as encoders need). */
fun outputSize(clips: List<Clip>, s: DeliverSettings): Pair<Int, Int> {
    val first = clips.firstOrNull()
    val fw = ((first?.width ?: 1920) * (first?.desqueeze ?: 1f)).toInt().coerceAtLeast(2)
    val fh = (first?.height ?: 1080).coerceAtLeast(2)
    val aspect = if (s.aspect > 0f) s.aspect else fw.toFloat() / fh
    val short = if (s.shortSide > 0) s.shortSide else minOf(fw, fh)
    val (w, h) = if (aspect >= 1f) (short * aspect).toInt() to short else short to (short / aspect).toInt()
    return (w / 2 * 2).coerceAtLeast(2) to (h / 2 * 2).coerceAtLeast(2)
}

/** Renders the timeline to a new video in DCIM/AcluxPro with Media3 Transformer. Start on the main thread. */
@UnstableApi
class TimelineExport(
    private val context: Context,
    private val clips: List<Clip>,
    private val settings: DeliverSettings,
    private val layers: List<Layer> = emptyList(),
    private val music: MusicTrack? = null,
    private val extraAudio: List<MusicTrack> = emptyList(),
    private val onProgress: (Int) -> Unit,
    private val onDone: (Uri?, String?) -> Unit,
    /** Keep the result in this file instead of saving it to the gallery (e.g. sound for captions). */
    private val toFile: File? = null,
) {
    private val audioOnly = settings.audioOnly
    private val out = File(context.cacheDir, "timeline_${System.currentTimeMillis()}." + if (settings.audioOnly) "m4a" else "mp4")
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private lateinit var transformer: Transformer

    fun start() {
        val (outW, outH) = outputSize(clips, settings)
        val videoLayers = if (audioOnly) emptyList() else layers.filter { it.visible && it.kind == LayerKind.VIDEO && it.videoUri != null }
        val layout = if (settings.fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        val totalMs = timelineLength(clips)
        val starts = clipStarts(clips)
        val overlaps = LongArray(clips.size) { overlapMs(clips, it) }
        val ab = overlaps.any { it > 0 }
        val compositing = !audioOnly && (videoLayers.isNotEmpty() || ab)
        val items = clips.mapIndexed { i, c ->
            val media = MediaItem.Builder()
                .setUri(c.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
                )
                .build()
            val audio = mutableListOf<AudioProcessor>()
            val gain = if (c.muted) 0f else c.volume
            // Audio transitions (Transitions → Audio) shape the sound at each cut.
            val inMs = if (i > 0) overlaps[i - 1] else 0L
            val outMs = overlaps[i]
            val xIn = if (i > 0) AudioXfade.forCut(clips, i - 1).second?.scaled(c.speed) else null
            val xOut = AudioXfade.forCut(clips, i).first?.scaled(c.speed)
            val afx = c.audio
            if (gain != 1f || !afx.isNeutral || !c.volKeys.isNullOrEmpty() || xIn != null || xOut != null) {
                audio += AudioFxProcessor(afx, gain, c.endMs - c.startMs, envelope = c.volKeys, xOut = xOut, xIn = xIn)
            }
            val video = mutableListOf<Effect>()
            if (!audioOnly) {
            if (c.detail.aiDenoise > 0f) video += AiDenoiseEffect(context, c.detail.aiDenoise)
            val motion = if (c.stab.enabled) Stabilizer.cached(context, c) else null
            if (!c.reframe.isIdentity || motion != null) video += ReframeEffect(c, motion)
            // Anamorphic: stretch the squeezed picture back to its real width.
            c.desqueeze?.takeIf { it > 1f }?.let { video += androidx.media3.effect.ScaleAndRotateTransformation.Builder().setScale(it, 1f).build() }
            video += clipEffects(c)
            // When compositing, every clip is sized to the project frame first.
            if (compositing) video += Presentation.createForWidthAndHeight(outW, outH, layout)
            if (ab) {
                // A/B roll: even clips on the top track, odd clips underneath.
                val top = i % 2 == 0
                val prevT = if (i > 0) clips[i - 1].transition.type else TransitionType.NONE
                val inMode = if (top && prevT.kind == TransitionKind.MASK) 1 else 0
                val outMode = if (top && c.transition.type.kind == TransitionKind.MASK) 2 else 0
                video += TransitionEffect(TransitionWindows(c.lengthMs, prevT, inMs, inMode, c.transition.type, outMs, outMode))
            }
            }
            val b = EditedMediaItem.Builder(media)
                .setEffects(Effects(audio, video))
                .setRemoveAudio(!settings.audio && !audioOnly)
                .setRemoveVideo(audioOnly)
            if (c.ramp != null) {
                // Speed ramp: the speed changes every 40 ms of source time along the curve.
                b.setSpeed(object : SpeedProvider {
                    override fun getSpeed(timeUs: Long): Float = c.speedAtSrc(c.startMs + timeUs / 1000)
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
                        val next = (timeUs / 40_000 + 1) * 40_000
                        return if (next >= (c.endMs - c.startMs) * 1000) androidx.media3.common.C.TIME_UNSET else next
                    }
                })
            } else if (c.speed != 1f) {
                val s = c.speed
                b.setSpeed(object : SpeedProvider {
                    override fun getSpeed(timeUs: Long): Float = s
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                })
            }
            b.build()
        }

        val global = mutableListOf<Effect>()
        // Project frame: size + shape, filling (crop) or fitting (bars).
        if (!audioOnly) global += Presentation.createForWidthAndHeight(outW, outH, layout)
        if (settings.fps > 0 && !audioOnly) global += FrameDropEffect.createDefaultFrameDropEffect(settings.fps.toFloat())
        // Text, stickers, watermarks and logos, bottom layer first.
        val pictureLayers = if (audioOnly) emptyList() else layers.filter { it.visible && it.kind != LayerKind.VIDEO }
        val overlays = pictureLayers.filter { (it.blend ?: LayerBlend.NORMAL) == LayerBlend.NORMAL }.mapNotNull { l ->
            LayerRenderer.render(context, l, outW, outH)?.let { LayerOverlay(it, l) as androidx.media3.effect.TextureOverlay }
        }
        if (overlays.isNotEmpty()) global += androidx.media3.effect.OverlayEffect(overlays)
        // Layers with a blend mode are drawn by their own shader, in layer order.
        for (l in pictureLayers) {
            if ((l.blend ?: LayerBlend.NORMAL) == LayerBlend.NORMAL) continue
            LayerRenderer.render(context, l, outW, outH)?.let { global += BlendLayerEffect(it, l, outW, outH) }
        }

        // Video layers become extra sequences composited over the main timeline. The compositor puts
        // the first sequence on top, so layers go first (top layer first) and the timeline last.
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val windows = mutableListOf<Pair<Long, Long>>()
        for (l in videoLayers.asReversed()) {
            val startMs = if (l.wholeVideo) 0L else l.startMs.coerceIn(0, totalMs)
            val wantEnd = if (l.wholeVideo) totalMs else l.endMs.coerceIn(startMs, totalMs)
            val avail = (l.videoDurationMs - l.videoStartMs).coerceAtLeast(100)
            val lenMs = (wantEnd - startMs).coerceIn(100, avail)
            val ph = ((l.size * outH).toInt() / 2 * 2).coerceAtLeast(16)
            val pw = ((ph * l.mediaAspect).toInt() / 2 * 2).coerceAtLeast(16)
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(l.videoUri!!)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(l.videoStartMs).setEndPositionMs(l.videoStartMs + lenMs).build())
                    .build()
            )
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), buildList {
                    add(Presentation.createForWidthAndHeight(pw, ph, Presentation.LAYOUT_SCALE_TO_FIT))
                    l.chroma?.let { add(ChromaKeyEffect(it, l.chromaTol ?: 0.4f, l.chromaSoft ?: 0.3f)) }
                }))
                .build()
            val seq = EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO))
            if (startMs > 0) seq.addGap(startMs * 1000)
            seq.addItem(item)
            val after = totalMs - startMs - lenMs
            if (after > 0) seq.addGap(after * 1000)
            sequences += seq.build()
            windows += (startMs * 1000) to ((startMs + lenMs) * 1000)
        }
        val layerSettings = videoLayers.asReversed()
        val firstTimeline = sequences.size
        if (!ab) {
            sequences += EditedMediaItemSequence.Builder(items).build()
        } else {
            val types = if (audioOnly) setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            else if (settings.audio) setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO, androidx.media3.common.C.TRACK_TYPE_VIDEO)
            else setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            for (parity in 0..1) {
                val seq = EditedMediaItemSequence.Builder(types)
                var t = 0L
                var any = false
                for (i in clips.indices) {
                    if (i % 2 != parity) continue
                    if (starts[i] > t) seq.addGap((starts[i] - t) * 1000)
                    seq.addItem(items[i]); any = true
                    t = starts[i] + clips[i].lengthMs
                }
                if (totalMs > t) seq.addGap((totalMs - t) * 1000)
                if (any) sequences += seq.build()
            }
        }
        val abCount = sequences.size - firstTimeline
        // Music + extra audio tracks, each placed at its own spot on the timeline.
        if (settings.audio || audioOnly) for (m in (listOfNotNull(music) + extraAudio).filter { !it.muted }) {
            val len = m.playLength(totalMs)
            if (len < 50) continue
            val end = m.offsetMs + len
            // Ducking ranges are relative to where this track starts.
            val ducks = duckRanges(clips).mapNotNull { r ->
                val a = r.first - m.startMs; val b = r.last - m.startMs
                if (b < 0 || a > len) null else a.coerceAtLeast(0) until b.coerceAtMost(len)
            }
            val musicItem = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(m.uri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.offsetMs).setEndPositionMs(end).build())
                    .build()
            )
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(
                    AudioFxProcessor(
                        m.fx.copy(fadeInMs = m.fadeInMs, fadeOutMs = m.fadeOutMs), m.volume, len,
                        duckRanges = ducks, duckGain = 1f - m.duck,
                    )
                ), emptyList()))
                .build()
            val seq = EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO))
            if (m.startMs > 0) seq.addGap(m.startMs * 1000)
            seq.addItem(musicItem)
            sequences += seq.build()
        }

        // Sound effects on cuts (built-in or imported): impacts start on the cut, risers end on it.
        if (settings.audio || audioOnly) for (k in 0 until clips.lastIndex) {
            val snd = try { cutSound(context, clips, k) } catch (_: Throwable) { null } ?: continue
            var at = cutSoundStart(clips, starts, k, snd.hitMs)
            var trim = 0L
            if (at < 0) { trim = -at; at = 0 }
            var len = snd.lengthMs - trim
            if (at + len > totalMs) len = totalMs - at
            if (len < 50) continue
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(Uri.fromFile(snd.file))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(trim).setEndPositionMs(trim + len).build())
                    .build()
            )
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(AudioFxProcessor(AudioFx(), snd.volume, len)), emptyList()))
                .build()
            val seq = EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO))
            if (at > 0) seq.addGap(at * 1000)
            seq.addItem(item)
            sequences += seq.build()
        }

        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), global))
            .apply {
                if (compositing) setVideoCompositorSettings(object : androidx.media3.common.VideoCompositorSettings {
                    override fun getOutputSize(inputSizes: List<androidx.media3.common.util.Size>) = androidx.media3.common.util.Size(outW, outH)
                    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): androidx.media3.common.OverlaySettings {
                        if (ab && inputId >= firstTimeline && inputId < firstTimeline + abCount) {
                            return abSettings(inputId - firstTimeline, presentationTimeUs / 1000, starts, overlaps)
                        }
                        val l0 = layerSettings.getOrNull(inputId) ?: return androidx.media3.effect.StaticOverlaySettings.Builder().build()
                        val (s, e) = windows[inputId]
                        val on = presentationTimeUs in s..e
                        val l = l0.animAt(presentationTimeUs / 1000)
                        val k = if (l0.size > 0f) l.size / l0.size else 1f
                        return androidx.media3.effect.StaticOverlaySettings.Builder()
                            .setBackgroundFrameAnchor((l.x * 2f - 1f).coerceIn(-1f, 1f), (1f - l.y * 2f).coerceIn(-1f, 1f))
                            .setOverlayFrameAnchor(0f, 0f)
                            .setScale(k, k)
                            .setAlphaScale(if (on) l.opacity.coerceIn(0f, 1f) else 0f)
                            .build()
                    }
                })
            }
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
                    if (toFile != null) {
                        val ok = out.renameTo(toFile) || try { out.copyTo(toFile, overwrite = true); out.delete(); true } catch (_: Throwable) { false }
                        handler.post { onDone(if (ok) Uri.fromFile(toFile) else null, if (ok) null else "Couldn't prepare the sound") }
                        return
                    }
                    Thread {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                        val uri = if (audioOnly) {
                            val name = "AUDIO_$stamp.m4a"
                            SaveFolder.write(context, true, name, "audio/mp4") { os -> out.inputStream().use { it.copyTo(os) } }
                                ?: publishAudio(context, out, name)
                        } else {
                            val name = "EDIT_$stamp.mp4"
                            SaveFolder.write(context, true, name, "video/mp4") { os -> out.inputStream().use { it.copyTo(os) } }
                                ?: GradeIO.publishVideo(context, out)
                        }
                        out.delete()
                        handler.post { onDone(uri, if (uri == null) (if (audioOnly) "Couldn't save the audio" else "Couldn't save the video") else null) }
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

    /** Track A (0, even clips, on top) or B (1): visibility and movement during transitions. */
    private fun abSettings(track: Int, ms: Long, starts: LongArray, overlaps: LongArray): androidx.media3.common.OverlaySettings {
        val b = androidx.media3.effect.StaticOverlaySettings.Builder()
        val i = clips.indices.firstOrNull { it % 2 == track && ms >= starts[it] && ms < starts[it] + clips[it].lengthMs }
            ?: return b.setAlphaScale(if (track == 1) 1f else 0f).build()
        var alpha = 1f; var tx = 0f; var ty = 0f
        val c = clips[i]
        // Coming in from the previous clip?
        if (i > 0 && overlaps[i - 1] > 0 && ms < starts[i] + overlaps[i - 1]) {
            val t = clips[i - 1].transition.type
            val p = ((ms - starts[i]).toFloat() / overlaps[i - 1]).coerceIn(0f, 1f)
            if (t.kind == TransitionKind.UNIFORM && track == 0) alpha = TransitionMath.uniformReveal(t, p)
            if (t.kind == TransitionKind.MOVE) TransitionMath.move(t, p, true).let { tx = it.first; ty = it.second }
        }
        // Going out into the next clip?
        val endOv = overlaps[i]
        if (endOv > 0 && ms >= starts[i] + c.lengthMs - endOv) {
            val t = c.transition.type
            val p = ((ms - (starts[i] + c.lengthMs - endOv)).toFloat() / endOv).coerceIn(0f, 1f)
            if (t.kind == TransitionKind.UNIFORM && track == 0) alpha = 1f - TransitionMath.uniformReveal(t, p)
            if (t.kind == TransitionKind.MOVE) TransitionMath.move(t, p, false).let { tx = it.first; ty = it.second }
        }
        return b.setAlphaScale(alpha.coerceIn(0f, 1f))
            .setBackgroundFrameAnchor(tx * 2f, -ty * 2f)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
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

/** Saves an exported sound file to Music/AcluxPro. */
fun publishAudio(context: Context, file: File, name: String): Uri? {
    val resolver = context.contentResolver
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Music/AcluxPro")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { o -> file.inputStream().use { it.copyTo(o, 1 shl 16) } }
            resolver.update(target, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            target
        } catch (_: Throwable) { resolver.delete(target, null, null); null }
    }
    return try {
        @Suppress("DEPRECATION")
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "AcluxPro").apply { mkdirs() }
        val dest = File(dir, name)
        file.inputStream().use { i -> dest.outputStream().use { i.copyTo(it) } }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(dest.path), arrayOf("audio/mp4"), null)
        Uri.fromFile(dest)
    } catch (_: Throwable) { null }
}

/** Timeline ranges (ms) of clips that ask for the music to be lowered under them. */
fun duckRanges(clips: List<Clip>): List<LongRange> {
    val out = mutableListOf<LongRange>()
    val s = clipStarts(clips)
    clips.forEachIndexed { i, c -> if (c.audio.duckMusic && !c.muted && c.volume > 0f) out += s[i] until s[i] + c.lengthMs }
    return out
}

/** Computing a colour table takes a moment; reuse the numbers (never the GPU effect itself). */
internal object LutCache {
    private val map = object : LinkedHashMap<ProGrade, Array<Array<IntArray>>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ProGrade, Array<Array<IntArray>>>?) = size > 8
    }
    @Synchronized
    fun cube(g: ProGrade): Array<Array<IntArray>> = map.getOrPut(g) { g.cube() }
}
