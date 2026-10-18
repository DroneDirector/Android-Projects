package com.dronedirector.veyrocutpro.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Pre-record: the camera keeps recording into a temporary file while you wait; when you stop,
 * the clip is cut to start a few seconds before you pressed record (no re-encoding: picture and
 * sound are copied, starting at the nearest keyframe).
 */
object PreRoll {
    fun trim(context: Context, source: File, fromUs: Long, volume: String, fileName: String): Uri? {
        val ex = MediaExtractor()
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            ex.setDataSource(source.absolutePath)
            val tracks = (0 until ex.trackCount).filter {
                val m = ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) ?: ""
                m.startsWith("video/") || m.startsWith("audio/")
            }
            val vIdx = tracks.firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") } ?: return null
            tracks.forEach { ex.selectTrack(it) }
            val vFmt = ex.getTrackFormat(vIdx)
            val rotation = if (vFmt.containsKey("rotation-degrees")) vFmt.getInteger("rotation-degrees") else 0
            ex.seekTo(fromUs.coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            // The first video keyframe at or before the cut decides the start.
            var start = -1L
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/VeyroCutPro")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            uri = Storage.insert(context, volume, values, Storage.Kind.VIDEO) ?: return null
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer.setOrientationHint(rotation)
                val map = HashMap<Int, Int>()
                for (t in tracks) map[t] = muxer.addTrack(ex.getTrackFormat(t))
                muxer.start()
                val buffer = ByteBuffer.allocateDirect(16 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val t = ex.sampleTime
                    val track = ex.sampleTrackIndex
                    if (start < 0) {
                        if (track == vIdx && ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) start = t
                        else { ex.advance(); continue }
                    }
                    if (t >= start) {
                        val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        info.set(0, size, t - start, flags)
                        muxer.writeSampleData(map[track]!!, buffer, info)
                    }
                    ex.advance()
                }
                muxer.stop()
                muxer.release()
            }
            Storage.publish(context, uri)
            return uri
        } catch (_: Throwable) {
            uri?.let { Storage.discard(context, it) }
            return null
        } finally {
            ex.release()
            source.delete()
        }
    }
}
