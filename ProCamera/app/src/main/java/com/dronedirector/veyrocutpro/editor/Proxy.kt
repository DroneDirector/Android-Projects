package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
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
import java.io.File

/** What a clip's video stream is (used to decide whether it needs a preview proxy). */
data class StreamInfo(val mime: String, val hdr: Boolean, val tenBit: Boolean, val chroma: ChromaInfo? = null)

object StreamProbe {
    fun probe(context: Context, uri: Uri): StreamInfo = try {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var info = StreamInfo("", false, false)
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/")) continue
            val transfer = if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) f.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
            val profile = if (f.containsKey(MediaFormat.KEY_PROFILE)) f.getInteger(MediaFormat.KEY_PROFILE) else 0
            val hevc10 = mime == MediaFormat.MIMETYPE_VIDEO_HEVC && profile in setOf(
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
            val hdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG ||
                mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION
            info = StreamInfo(mime, hdr, hevc10 || hdr, Chroma.of(f))
            break
        }
        ex.release()
        info
    } catch (_: Throwable) {
        StreamInfo("", false, false)
    }
}

enum class ProxyMode(val label: String) { AUTO("Auto (HDR / 10-bit / 4K)"), ALWAYS("Always"), OFF("Off") }

data class ProxySettings(val mode: ProxyMode = ProxyMode.AUTO, val hevc: Boolean = false, val height: Int = 1080) {
    fun needsProxy(c: Clip): Boolean = when (mode) {
        // Clips that need a special decoder (4:2:2) always get a smooth 4:2:0 preview copy.
        ProxyMode.OFF -> c.decoder != null
        ProxyMode.ALWAYS -> true
        ProxyMode.AUTO -> c.hdr || c.tenBit || c.is422 || minOf(c.width, c.height) > 1440
    }

    companion object {
        fun load(context: Context): ProxySettings {
            val p = context.getSharedPreferences("procam", Context.MODE_PRIVATE)
            return ProxySettings(
                mode = ProxyMode.entries.firstOrNull { it.name == p.getString("proxy_mode", null) } ?: ProxyMode.AUTO,
                hevc = p.getBoolean("proxy_hevc", false),
                height = p.getInt("proxy_height", 1080),
            )
        }

        fun save(context: Context, s: ProxySettings) {
            context.getSharedPreferences("procam", Context.MODE_PRIVATE).edit()
                .putString("proxy_mode", s.mode.name).putBoolean("proxy_hevc", s.hevc).putInt("proxy_height", s.height).apply()
        }
    }
}

/**
 * Optimized preview media: an SDR (tone-mapped), lighter copy of a clip used only for playback
 * in the editor, so grades and effects play smoothly. Export always uses the original file.
 * Proxies are cached and reused between sessions.
 */
@UnstableApi
object ProxyMaker {
    private fun dir(context: Context) = File(context.cacheDir, "proxies").apply { mkdirs() }

    fun fileFor(context: Context, c: Clip, s: ProxySettings): File {
        val key = (c.uri.toString() + "|" + s.height + "|" + (if (s.hevc) "hevc" else "avc")).hashCode().toUInt().toString(16)
        return File(dir(context), "proxy_$key.mp4")
    }

    /** Existing finished proxy, if any. */
    fun existing(context: Context, c: Clip, s: ProxySettings): Uri? {
        val f = fileFor(context, c, s)
        return if (f.exists() && f.length() > 1024) Uri.fromFile(f) else null
    }

    fun clearAll(context: Context): Long {
        var freed = 0L
        dir(context).listFiles()?.forEach { freed += it.length(); it.delete() }
        return freed
    }

    fun sizeOnDisk(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** Makes a proxy; start on the main thread. Returns a cancel function. */
    fun make(
        context: Context,
        c: Clip,
        s: ProxySettings,
        onProgress: (Int) -> Unit,
        onDone: (Uri?, String?) -> Unit,
    ): () -> Unit {
        val target = fileFor(context, c, s)
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.delete()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        // Keep the shape; scale the short side down to the proxy height.
        val short = minOf(c.width, c.height).coerceAtLeast(2)
        val scale = minOf(1f, s.height.toFloat() / short)
        val w = ((c.width * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val h = ((c.height * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val transformer = Transformer.Builder(context)
            .also { b -> c.decoder?.let { b.setAssetLoaderFactory(Chroma.assetLoaders(context, listOf(it))) } }
            .setVideoMimeType(if (s.hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(if (s.height >= 1080) 14_000_000 else 7_000_000).build())
                    .setEnableFallback(true)
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    if (tmp.renameTo(target)) onDone(Uri.fromFile(target), null) else onDone(null, "Couldn't save the proxy")
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true
                    tmp.delete()
                    onDone(null, "Proxy failed: ${exportException.errorCodeName}")
                }
            })
            .build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(c.uri))
            .setEffects(Effects(emptyList(), if (w > 0 && h > 0) listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)) else emptyList()))
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(item)).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        try {
            transformer.start(composition, tmp.path)
        } catch (t: Throwable) {
            onDone(null, "Proxy failed: ${t.message}")
            return {}
        }
        fun poll() {
            if (finished) return
            val ph = ProgressHolder()
            if (transformer.getProgress(ph) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(ph.progress)
            handler.postDelayed({ poll() }, 500)
        }
        poll()
        return {
            if (!finished) {
                finished = true
                transformer.cancel()
                tmp.delete()
            }
        }
    }
}
