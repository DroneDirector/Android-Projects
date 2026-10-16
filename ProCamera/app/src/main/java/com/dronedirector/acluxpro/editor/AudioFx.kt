package com.dronedirector.acluxpro.editor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class AudioChannels(val label: String) { STEREO("Stereo"), MONO("Mono"), LEFT("Left only"), RIGHT("Right only"), SWAP("Swap L/R") }

/** Per-clip (and music) audio processing: EQ, dynamics, channels, fades and ducking. */
data class AudioFx(
    val low: Float = 0f,          // dB, shelf at 120 Hz
    val mid: Float = 0f,          // dB, peak at 1 kHz
    val high: Float = 0f,         // dB, shelf at 8 kHz
    val lowCut: Boolean = false,  // high-pass 80 Hz (wind, rumble, handling noise)
    val voice: Boolean = false,   // speech clarity: 100 Hz high-pass + presence lift
    val compressor: Float = 0f,   // 0..1
    val gate: Float = 0f,         // 0..1 noise gate
    val pan: Float = 0f,          // -1 left .. +1 right
    val channels: AudioChannels = AudioChannels.STEREO,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val duckMusic: Boolean = false,
    val limiter: Boolean = true,
) {
    val isNeutral get() = this == AudioFx() || this == AudioFx(limiter = false)
}

enum class AudioPreset(val label: String, val fx: AudioFx) {
    FLAT("Flat", AudioFx()),
    VOICE("Voice", AudioFx(low = -2f, mid = 1f, high = 2f, voice = true, compressor = 0.45f, gate = 0.2f)),
    INTERVIEW("Interview", AudioFx(low = -3f, mid = 1.5f, high = 1.5f, voice = true, compressor = 0.6f, gate = 0.35f, channels = AudioChannels.MONO)),
    WIND("Wind cut", AudioFx(low = -6f, lowCut = true, gate = 0.1f)),
    WARM("Warm", AudioFx(low = 3f, mid = 0.5f, high = -2f)),
    BRIGHT("Bright", AudioFx(low = -1f, high = 4f)),
    BASS("Bass boost", AudioFx(low = 7f, mid = -1f)),
    RADIO("Radio", AudioFx(low = -12f, mid = 6f, high = -10f, lowCut = true, compressor = 0.7f, channels = AudioChannels.MONO)),
    LOUD("Loud & punchy", AudioFx(low = 2f, high = 1.5f, compressor = 0.85f)),
}

/** Live peak levels (0..1) from the preview, for the meter. */
object AudioMeter {
    @Volatile var left = 0f
    @Volatile var right = 0f
}

private class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    private var z1 = DoubleArray(8); private var z2 = DoubleArray(8)
    var on = false

    fun process(x: Double, ch: Int): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = DoubleArray(8); z2 = DoubleArray(8) }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    // RBJ cookbook filters.
    fun highPass(f: Double, rate: Double, q: Double = 0.707) {
        val w = 2 * PI * f / rate; val al = sin(w) / (2 * q); val c = cos(w)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al); on = true
    }

    fun peak(f: Double, rate: Double, db: Double, q: Double = 0.9) {
        if (abs(db) < 0.05) { on = false; return }
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / rate; val al = sin(w) / (2 * q); val c = cos(w)
        set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a); on = true
    }

    fun lowShelf(f: Double, rate: Double, db: Double) {
        if (abs(db) < 0.05) { on = false; return }
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / rate; val c = cos(w); val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        set(a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa); on = true
    }

    fun highShelf(f: Double, rate: Double, db: Double) {
        if (abs(db) < 0.05) { on = false; return }
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / rate; val c = cos(w); val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        set(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa); on = true
    }
}

/**
 * 16-bit PCM processor used both for the export (fixed settings, with fades and ducking)
 * and for the live preview (settings can change while playing; set [fx]).
 */
@UnstableApi
class AudioFxProcessor(
    initial: AudioFx = AudioFx(),
    private val gain: Float = 1f,
    private val totalMs: Long = 0L,
    /** Ranges (ms) where the signal is lowered by [duckGain] — used for music under dialogue. */
    private val duckRanges: List<LongRange> = emptyList(),
    private val duckGain: Float = 1f,
    private val meter: Boolean = false,
    private val live: Boolean = false,
    /** Volume keyframes over the clip (export). */
    private val envelope: List<VolKey>? = null,
) : BaseAudioProcessor() {
    @Volatile var fx: AudioFx = initial
    private var built: AudioFx? = null
    private var builtRate = 0
    private val hp = Biquad(); private val ls = Biquad(); private val pk = Biquad(); private val hs = Biquad(); private val pres = Biquad()
    private var frames = 0L
    private var env = 0.0; private var gateGain = 1.0; private var duck = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        if (!live && fx.isNeutral && gain == 1f && duckRanges.isEmpty() && !meter && envelope.isNullOrEmpty()) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    private fun rebuild(f: AudioFx, rate: Int) {
        val r = rate.toDouble()
        if (f.voice) hp.highPass(100.0, r) else if (f.lowCut) hp.highPass(80.0, r) else hp.on = false
        ls.lowShelf(120.0, r, f.low.toDouble())
        pk.peak(1000.0, r, f.mid.toDouble())
        hs.highShelf(8000.0, r, f.high.toDouble())
        pres.peak(3200.0, r, if (f.voice) 4.0 else 0.0, 1.2)
        built = f; builtRate = rate
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val ch = fmt.channelCount.coerceIn(1, 8)
        val rate = fmt.sampleRate.coerceAtLeast(1)
        val f = fx
        if (f != built || rate != builtRate) rebuild(f, rate)
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val frame = DoubleArray(ch)
        // Dynamics constants
        val atk = exp(-1.0 / (0.005 * rate)); val rel = exp(-1.0 / (0.12 * rate))
        val compThr = 10.0.pow((-10.0 - f.compressor * 22.0) / 20.0)
        val ratio = 1.0 + f.compressor * 5.0
        val makeup = 10.0.pow(f.compressor * 7.0 / 20.0)
        val gateThr = 10.0.pow((-62.0 + f.gate * 30.0) / 20.0)
        val gateStep = 1.0 / (0.01 * rate)
        val duckStep = 1.0 / (0.25 * rate)
        val panL = if (f.pan > 0) 1.0 - f.pan else 1.0
        val panR = if (f.pan < 0) 1.0 + f.pan else 1.0
        var peakL = 0.0; var peakR = 0.0
        while (inputBuffer.remaining() >= 2 * ch) {
            for (c in 0 until ch) frame[c] = inputBuffer.short / 32768.0
            // Channels
            if (ch >= 2) {
                val l = frame[0]; val r = frame[1]
                when (f.channels) {
                    AudioChannels.STEREO -> {}
                    AudioChannels.MONO -> { val m = (l + r) * 0.5; frame[0] = m; frame[1] = m }
                    AudioChannels.LEFT -> { frame[0] = l; frame[1] = l }
                    AudioChannels.RIGHT -> { frame[0] = r; frame[1] = r }
                    AudioChannels.SWAP -> { frame[0] = r; frame[1] = l }
                }
                frame[0] *= panL; frame[1] *= panR
            }
            // EQ
            for (c in 0 until ch) {
                var x = frame[c]
                if (hp.on) x = hp.process(x, c)
                if (ls.on) x = ls.process(x, c)
                if (pk.on) x = pk.process(x, c)
                if (hs.on) x = hs.process(x, c)
                if (pres.on) x = pres.process(x, c)
                frame[c] = x
            }
            // Envelope (peak of all channels)
            var lvl = 0.0
            for (c in 0 until ch) lvl = maxOf(lvl, abs(frame[c]))
            env = if (lvl > env) atk * env + (1 - atk) * lvl else rel * env + (1 - rel) * lvl
            var g = gain.toDouble()
            if (f.gate > 0f) {
                gateGain = if (env < gateThr) (gateGain - gateStep).coerceAtLeast(0.0) else (gateGain + gateStep * 4).coerceAtMost(1.0)
                g *= gateGain
            }
            if (f.compressor > 0f && env > compThr) g *= (compThr * (env / compThr).pow(1.0 / ratio)) / env
            if (f.compressor > 0f) g *= makeup
            val tMs = frames * 1000L / rate
            if (!live) {
                if (f.fadeInMs > 0 && tMs < f.fadeInMs) g *= tMs.toDouble() / f.fadeInMs
                if (f.fadeOutMs > 0 && totalMs > 0 && tMs > totalMs - f.fadeOutMs) g *= ((totalMs - tMs).toDouble() / f.fadeOutMs).coerceIn(0.0, 1.0)
            }
            if (!envelope.isNullOrEmpty()) g *= volAt(envelope, tMs)
            if (duckRanges.isNotEmpty()) {
                val target = if (duckRanges.any { tMs in it }) duckGain.toDouble() else 1.0
                duck = if (duck > target) (duck - duckStep).coerceAtLeast(target) else (duck + duckStep).coerceAtMost(target)
                g *= duck
            }
            for (c in 0 until ch) {
                var y = frame[c] * g
                if (f.limiter && abs(y) > 0.9) y = (if (y > 0) 1 else -1) * (0.9 + 0.1 * tanh((abs(y) - 0.9) / 0.1))
                if (c == 0) peakL = maxOf(peakL, abs(y)) else if (c == 1) peakR = maxOf(peakR, abs(y))
                out.putShort((y * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
            frames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
        if (meter) {
            AudioMeter.left = maxOf(peakL.toFloat(), AudioMeter.left * 0.85f)
            AudioMeter.right = maxOf((if (ch >= 2) peakR else peakL).toFloat(), AudioMeter.right * 0.85f)
        }
    }

    private fun clear() { frames = 0; env = 0.0; gateGain = 1.0; duck = 1.0; listOf(hp, ls, pk, hs, pres).forEach { it.reset() } }
    override fun onFlush() = clear()
    override fun onReset() { clear(); built = null }
}

fun dbfs(v: Float): Float = if (v <= 0.00001f) -60f else (20 * log10(v)).coerceAtLeast(-60f)
