package com.dronedirector.acluxpro.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** One snapshot of the audio graph: recent levels (0..1, oldest first) plus current and peak dBFS. */
class AudioLevels(val history: FloatArray, val db: Float, val peakDb: Float, val live: Boolean)

/**
 * Real-time microphone levels for the video modes.
 * While idle it listens with its own [AudioRecord]; while recording, the recorder owns the
 * microphone and feeds levels in through [pushAmplitude].
 */
class AudioMeter(private val context: Context) {

    private val size = 64
    private val ring = FloatArray(size)
    private var head = 0
    private var peakDb = MIN_DB
    private var peakHoldFrames = 0

    private val _levels = MutableStateFlow(AudioLevels(FloatArray(size), MIN_DB, MIN_DB, false))
    val levels: StateFlow<AudioLevels> = _levels.asStateFlow()

    @Volatile private var running = false
    private var thread: Thread? = null

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (running || !hasPermission()) return
        running = true
        thread = Thread({
            val rate = 44_100
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) { running = false; return@Thread }
            val record = try {
                AudioRecord(MediaRecorder.AudioSource.CAMCORDER, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 4096))
            } catch (_: Throwable) {
                running = false
                return@Thread
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                running = false
                return@Thread
            }
            val buf = ShortArray(rate / 25) // ~40 ms per reading, 25 readings a second
            try {
                record.startRecording()
                while (running) {
                    val n = record.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) { val v = buf[i] / 32768.0; sum += v * v }
                    pushRms(sqrt(sum / n).toFloat())
                }
            } catch (_: Throwable) {
            } finally {
                try { record.stop() } catch (_: Throwable) {}
                record.release()
            }
        }, "ProCamAudioMeter").apply { start() }
    }

    fun stopMonitoring() {
        running = false
        thread?.join(300)
        thread = null
    }

    /** Amplitude 0..1 from CameraX recording stats. */
    fun pushAmplitude(amplitude: Float) = pushRms(amplitude * 0.7071f)

    fun clear() {
        ring.fill(0f)
        peakDb = MIN_DB
        _levels.value = AudioLevels(FloatArray(size), MIN_DB, MIN_DB, false)
    }

    @Synchronized
    private fun pushRms(rms: Float) {
        val db = if (rms <= 0.00001f) MIN_DB else (20f * log10(rms)).coerceIn(MIN_DB, 0f)
        ring[head] = (db - MIN_DB) / -MIN_DB
        head = (head + 1) % size
        if (db >= peakDb) {
            peakDb = db
            peakHoldFrames = 40
        } else if (--peakHoldFrames <= 0) {
            peakDb = max(db, peakDb - 1.5f)
        }
        val ordered = FloatArray(size) { ring[(head + it) % size] }
        _levels.value = AudioLevels(ordered, db, peakDb, true)
    }

    companion object {
        const val MIN_DB = -60f
    }
}
