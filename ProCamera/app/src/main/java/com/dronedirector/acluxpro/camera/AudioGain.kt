package com.dronedirector.acluxpro.camera

import android.annotation.SuppressLint
import androidx.camera.video.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Manual input gain for recordings: scales the 16-bit PCM the recorder captures before it is
 * encoded, with an optional soft limiter so boosted audio rolls off instead of clipping hard.
 * Also reports the processed level so the on-screen audio meter shows what is being recorded.
 */
@SuppressLint("RestrictedApi")
class AudioGainProcessor(private val onLevel: (rms: Float) -> Unit) : AudioProcessor {

    @Volatile var gainDb: Int = 0
    @Volatile var limiter: Boolean = true

    private var out: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var work: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnsupportedAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n <= 0) {
            out = AudioProcessor.EMPTY_BUFFER
            return
        }
        if (work.capacity() < n) work = ByteBuffer.allocateDirect(n * 2)
        work.clear()
        work.order(inputBuffer.order())
        val src = inputBuffer.order(inputBuffer.order())
        val gain = 10f.pow(gainDb / 20f)
        val limit = limiter
        var sum = 0.0
        var count = 0
        while (src.remaining() >= 2) {
            var x = src.short / 32768f * gain
            if (limit) {
                // Transparent below -2 dBFS, smooth roll-off above it.
                val a = abs(x)
                if (a > 0.8f) x = (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)) * if (x < 0) -1f else 1f
            }
            x = x.coerceIn(-1f, 0.99997f)
            sum += x * x
            count++
            work.putShort((x * 32768f).toInt().toShort())
        }
        // Any odd trailing byte is passed through untouched.
        while (src.hasRemaining()) work.put(src.get())
        work.flip()
        out = work
        if (count > 0) onLevel(sqrt(sum / count).toFloat())
    }

    override fun getOutput(): ByteBuffer {
        val o = out
        out = AudioProcessor.EMPTY_BUFFER
        return o
    }

    override fun flush() {
        out = AudioProcessor.EMPTY_BUFFER
    }

    override fun reset() {
        out = AudioProcessor.EMPTY_BUFFER
    }

    companion object {
        val ORDER: ByteOrder = ByteOrder.nativeOrder()
    }
}
