package com.dronedirector.veyrocutpro.camera

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Pro audio: records the microphone uncompressed (32-bit float, 48 kHz, stereo when the phone has
 * it) next to the video, so a 24-bit WAV can be saved and the clip's sound made from it. The video
 * itself is recorded without sound while this runs (only one recorder can own the microphone).
 */
class WavRecorder(private val context: Context, private val onLevel: (Float) -> Unit) {
    /** A finished take: raw little-endian floats, and when its first sample was heard (System.nanoTime). */
    class Take(val file: File, val rate: Int, val channels: Int, val frame0Nanos: Long)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var take: Take? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val rate = 48_000
        // The same microphone set-up as normal video recording (the phone's camcorder tuning).
        val source = MediaRecorder.AudioSource.CAMCORDER
        var rec: AudioRecord? = null
        var ch = 2
        for (mask in listOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)) {
            ch = if (mask == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
            val min = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT)
            if (min <= 0) continue
            val r = try { AudioRecord(source, rate, mask, AudioFormat.ENCODING_PCM_FLOAT, maxOf(min * 2, rate / 10 * ch * 4)) } catch (_: Throwable) { null }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) { rec = r; break }
            r?.release()
        }
        val ar = rec ?: return false
        val file = File(context.cacheDir, "wav_${System.currentTimeMillis()}.raw")
        val chans = ch
        running = true
        thread = Thread({
            var frame0 = 0L
            var frames = 0L
            try {
                ar.startRecording()
                BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
                    val buf = FloatArray(rate / 50 * chans)
                    val bytes = ByteBuffer.allocate(buf.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    var lastLevel = 0L
                    while (running) {
                        val n = ar.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                        if (n <= 0) continue
                        if (frame0 == 0L) {
                            val ts = AudioTimestamp()
                            frame0 = if (ar.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS)
                                ts.nanoTime - ts.framePosition * 1_000_000_000L / rate
                            else System.nanoTime() - (frames + n / chans) * 1_000_000_000L / rate
                        }
                        bytes.clear()
                        var peak = 0f
                        for (i in 0 until n) { val v = buf[i]; bytes.putFloat(v); val a = abs(v); if (a > peak) peak = a }
                        out.write(bytes.array(), 0, n * 4)
                        frames += n / chans
                        val now = System.currentTimeMillis()
                        if (now - lastLevel > 100) { lastLevel = now; onLevel(peak.coerceAtMost(1f)) }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { ar.stop() } catch (_: Throwable) {}
                ar.release()
                take = Take(file, rate, chans, if (frame0 != 0L) frame0 else System.nanoTime())
            }
        }, "wav-recorder").apply { priority = Thread.MAX_PRIORITY; start() }
        return true
    }

    /** Stops and returns the take (or null if nothing was recorded). */
    fun stop(): Take? {
        if (!running) return take
        running = false
        thread?.join(3000)
        thread = null
        return take?.takeIf { it.file.length() > 0 }.also { take = null }
    }

}
