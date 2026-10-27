package com.dronedirector.veyrocutpro.editor

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * What the phone's video encoders can make: whether a frame size can be encoded with H.264 or HEVC, and at
 * what highest frame rate. Used to offer only the export sizes (up to 8K) this phone can really produce.
 */
object EncoderCaps {
    /** Export sizes beyond 4K (short side → label). 5.1K = DJI's 5120 × 2700 when the project shape is Original. */
    val BIG = listOf(2880 to "5K", 2700 to "5.1K", 3456 to "6K", 4032 to "7K", 4320 to "8K")

    private val cache = HashMap<String, Double>()

    private fun encoders(mime: String): List<MediaCodecInfo> = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(mime, true) } }
    } catch (_: Throwable) { emptyList() }

    /** The highest frame rate any encoder of [mime] can do at [w]×[h] (either way round); 0 = can't. */
    fun maxFps(mime: String, w: Int, h: Int): Double = synchronized(cache) {
        cache.getOrPut("$mime $w $h") {
            var best = 0.0
            for (info in encoders(mime)) {
                val vc = try { info.getCapabilitiesForType(mime).videoCapabilities } catch (_: Throwable) { null } ?: continue
                for ((a, b) in listOf(w to h, h to w)) {
                    if (!vc.isSizeSupported(a, b)) continue
                    val f = try { vc.getSupportedFrameRatesFor(a, b).upper } catch (_: Throwable) { 30.0 }
                    if (f > best) best = f
                }
            }
            best
        }
    }

    fun mimeOf(hevc: Boolean): String = if (hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

    /** "5K" etc. for a short side; null when it's not one of the big sizes. */
    fun labelFor(short: Int): String? = BIG.firstOrNull { it.first == short }?.second
}

/** Automatic picture test (screenshots on the test phone): opens the editor with a test clip at this clarity / texture. */
object FxTest { @Volatile var amount: Float? = null }
