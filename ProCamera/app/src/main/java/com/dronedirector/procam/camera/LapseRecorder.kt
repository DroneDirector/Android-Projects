package com.dronedirector.procam.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes selected camera frames into an MP4 that plays at 30 fps.
 * - Time-lapse: one frame every [intervalMs].
 * - Hyperlapse: every [everyNth] camera frame (camera runs ~30 fps, so N ≈ speed-up).
 * Frames are drawn by [FilterProcessor] on its GL thread; this class owns the encoder + muxer.
 */
class LapseRecorder(
    private val context: Context,
    val width: Int,
    val height: Int,
    orientationHint: Int,
    volume: String,
    fileName: String,
    private val intervalMs: Int,
    private val everyNth: Int,
    /** Crop factor for the hyperlapse "stabilise" look (1 = full frame). */
    val zoom: Float,
    private val onFrame: (Int) -> Unit,
) {
    private val thread = HandlerThread("ProCamLapse").apply { start() }
    private val handler = Handler(thread.looper)
    private val codec: MediaCodec
    val inputSurface: Surface
    private val pfd: ParcelFileDescriptor
    private val muxer: MediaMuxer
    val uri: Uri
    private var track = -1
    private var muxerStarted = false
    private var finished = false
    private var onDone: ((Uri?) -> Unit)? = null

    @Volatile private var frames = 0
    @Volatile private var stopping = false
    private var lastPickNs = -1L
    private var seen = 0L

    init {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        uri = context.contentResolver.insert(Storage.videosUri(volume), values)
            ?: throw IllegalStateException("Couldn't create the video file")
        pfd = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IllegalStateException("Couldn't open the video file")
        muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(orientationHint)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width.toLong() * height * 30 * 0.25).toInt().coerceIn(4_000_000, 40_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                val buf = codec.getOutputBuffer(index)
                if (buf != null && info.size > 0 && muxerStarted &&
                    (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(track, buf, info)
                }
                codec.releaseOutputBuffer(index, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) finish()
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) = finish()

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                track = muxer.addTrack(format)
                muxer.start()
                muxerStarted = true
            }
        }, handler)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    val frameCount: Int get() = frames

    /**
     * Called on the GL thread for every camera frame. Returns the presentation time (ns) to encode
     * this frame at, or null to skip it.
     */
    fun pick(cameraTimestampNs: Long): Long? {
        if (stopping) return null
        seen++
        val take = if (everyNth > 0) {
            (seen - 1) % everyNth == 0L
        } else {
            lastPickNs < 0 || cameraTimestampNs - lastPickNs >= intervalMs * 1_000_000L
        }
        if (!take) return null
        lastPickNs = cameraTimestampNs
        val pts = frames * 33_333_333L
        return pts
    }

    /** Called on the GL thread after a picked frame was submitted to the encoder. */
    fun frameSubmitted() {
        frames++
        onFrame(frames)
    }

    fun stop(done: (Uri?) -> Unit) {
        if (stopping) return
        stopping = true
        onDone = done
        handler.post {
            try { codec.signalEndOfInputStream() } catch (_: Throwable) { finish() }
        }
        // Safety net in case the encoder never reports end of stream.
        handler.postDelayed({ finish() }, 4000)
    }

    private fun finish() {
        if (finished) return
        finished = true
        val ok = muxerStarted && frames > 0
        try { codec.stop() } catch (_: Throwable) {}
        try { codec.release() } catch (_: Throwable) {}
        try { if (muxerStarted) muxer.stop() } catch (_: Throwable) {}
        try { muxer.release() } catch (_: Throwable) {}
        try { pfd.close() } catch (_: Throwable) {}
        inputSurface.release()
        val resolver = context.contentResolver
        val result = if (ok) {
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            uri
        } else {
            try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
            null
        }
        val cb = onDone
        onDone = null
        android.os.Handler(android.os.Looper.getMainLooper()).post { cb?.invoke(result) }
        thread.quitSafely()
    }
}

/** Re-times a recorded video so it plays back [factor] times slower (video track only). */
object SlowMotionRemux {
    fun slowDown(context: Context, source: File, factor: Double, volume: String, fileName: String): Uri? {
        val extractor = MediaExtractor()
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val rotation = if (format.containsKey("rotation-degrees")) format.getInteger("rotation-degrees") else 0

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            uri = resolver.insert(Storage.videosUri(volume), values) ?: return null
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer.setOrientationHint(rotation)
                val out = muxer.addTrack(format)
                muxer.start()
                val max = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                val buffer = ByteBuffer.allocateDirect(maxOf(max, 8 * 1024 * 1024))
                val info = MediaCodec.BufferInfo()
                var first = -1L
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val t = extractor.sampleTime
                    if (first < 0) first = t
                    val flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, size, ((t - first) * factor).toLong(), flags)
                    muxer.writeSampleData(out, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
                muxer.release()
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            return uri
        } catch (_: Throwable) {
            uri?.let { try { resolver.delete(it, null, null) } catch (_: Throwable) {} }
            return null
        } finally {
            extractor.release()
            source.delete()
        }
    }
}
