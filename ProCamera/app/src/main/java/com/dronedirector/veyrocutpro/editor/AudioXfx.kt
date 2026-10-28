package com.dronedirector.veyrocutpro.editor

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.tanh

/** Sound effects that run over an audio transition (the outgoing or incoming side), strength 0..1 over the transition. */
enum class AudioXfx {
    NONE, LOWPASS, HIGHPASS, ECHO, REVERB, TAPE_STOP, TAPE_START, STUTTER, BITCRUSH, TELEPHONE, RADIO,
    PITCH_DOWN, PITCH_UP, TREMOLO, CHOP, FLANGER, WOBBLE, RISER, DRIVE, UNDERWATER, GLITCH,
}

/** Zero-delay state-variable filter (low, band and high outputs), stable at any cutoff. Own code. */
private class Svf(ch: Int) {
    private val ic1 = DoubleArray(ch); private val ic2 = DoubleArray(ch)
    private var a1 = 0.0; private var a2 = 0.0; private var a3 = 0.0; private var k = 1.4
    var low = 0.0; var band = 0.0; var high = 0.0
    fun set(fc: Double, rate: Int, q: Double = 0.707) {
        val g = tan(PI * fc.coerceIn(10.0, rate * 0.45) / rate)
        k = 1.0 / q
        a1 = 1.0 / (1.0 + g * (g + k)); a2 = g * a1; a3 = g * a2
    }
    fun run(x: Double, c: Int) {
        val v3 = x - ic2[c]
        val v1 = a1 * ic1[c] + a2 * v3
        val v2 = ic2[c] + a2 * ic1[c] + a3 * v3
        ic1[c] = 2 * v1 - ic1[c]; ic2[c] = 2 * v2 - ic2[c]
        low = v2; band = v1; high = x - k * v1 - v2
    }
}

/**
 * Runs one [AudioXfx] on a frame of samples. Keeps its own history (for tape, stutter, echo, reverb and flanger).
 * Own code.
 */
class XfxDsp(private val ch: Int, private val rate: Int) {
    private val histLen = rate * 4
    private val hist = Array(ch) { DoubleArray(histLen) }
    private var w = 0
    private var readPos = -1.0
    private var tapeBlend = 0.0
    private val f1 = Svf(ch); private val f2 = Svf(ch)
    private var coefAt = -1
    // Echo / flanger feedback lines.
    private val echoLen = (rate * 0.32).toInt()
    private val echo = Array(ch) { DoubleArray(echoLen) }
    private var ew = 0
    // Small reverb: four combs and two all-passes per channel.
    private val combLen = intArrayOf(1116, 1188, 1277, 1356).map { (it * rate / 44100.0).toInt() }
    private val combs = Array(ch) { combLen.map { DoubleArray(it) } }
    private val combPos = Array(ch) { IntArray(4) }
    private val combLp = Array(ch) { DoubleArray(4) }
    private val apLen = intArrayOf(556, 441).map { (it * rate / 44100.0).toInt() }
    private val aps = Array(ch) { apLen.map { DoubleArray(it) } }
    private val apPos = Array(ch) { IntArray(2) }
    private val flLen = (rate * 0.012).toInt()
    private val fl = Array(ch) { DoubleArray(flLen) }
    private var fw = 0
    private var crushHold = DoubleArray(ch); private var crushN = 0
    private var stutterStart = -1; private var stutterI = 0
    private var noise = 12345
    private var pitch: PitchShiftX? = null
    private var t = 0L
    private var lastType = AudioXfx.NONE

    fun reset() {
        readPos = -1.0; tapeBlend = 0.0; stutterStart = -1; stutterI = 0; coefAt = -1
        for (c in 0 until ch) { echo[c].fill(0.0); fl[c].fill(0.0); combs[c].forEach { it.fill(0.0) }; aps[c].forEach { it.fill(0.0) }; combLp[c].fill(0.0) }
    }

    private fun rnd(): Double { noise = noise * 1103515245 + 12345; return ((noise ushr 8) and 0xFFFF) / 32768.0 - 1.0 }

    private fun histAt(c: Int, pos: Double): Double {
        val i = floor(pos).toInt(); val fr = pos - i
        val a = hist[c][((i % histLen) + histLen) % histLen]; val b = hist[c][(((i + 1) % histLen) + histLen) % histLen]
        return a + (b - a) * fr
    }

    /** [a] = strength 0..1 (how far into the effect). */
    fun process(frame: DoubleArray, type: AudioXfx, a0: Double) {
        val a = a0.coerceIn(0.0, 1.0)
        for (c in 0 until ch) hist[c][w] = frame[c]
        val absPos = t
        if (type != lastType) { reset(); lastType = type }
        val sec = t.toDouble() / rate
        val upd = (t % 32L) == 0L
        when (type) {
            AudioXfx.NONE -> {}
            AudioXfx.LOWPASS, AudioXfx.UNDERWATER -> {
                if (upd) f1.set(18000.0 * (if (type == AudioXfx.UNDERWATER) 250.0 else 350.0).div(18000.0).pow(a), rate, if (type == AudioXfx.UNDERWATER) 1.6 else 0.9)
                for (c in 0 until ch) { f1.run(frame[c], c); frame[c] = f1.low }
                if (type == AudioXfx.UNDERWATER && a > 0.05) {
                    val ps = pitch ?: PitchShiftX(ch, rate).also { pitch = it }
                    ps.process(frame, 1.0 + 0.025 * a * sin(2 * PI * 4.5 * sec))
                }
            }
            AudioXfx.HIGHPASS -> {
                if (upd) f1.set(20.0 * (2500.0 / 20.0).pow(a), rate)
                for (c in 0 until ch) { f1.run(frame[c], c); frame[c] = f1.high }
            }
            AudioXfx.TELEPHONE, AudioXfx.RADIO -> {
                if (upd) { f1.set(350.0, rate, 0.8); f2.set(3200.0, rate, 0.8) }
                for (c in 0 until ch) {
                    f1.run(frame[c], c); f2.run(f1.high, c)
                    var y = f2.low * 1.8
                    if (type == AudioXfx.RADIO) y = tanh(y * 2.5) / tanh(2.5) * 0.85 + rnd() * 0.02
                    frame[c] = frame[c] * (1 - a) + y * a
                }
            }
            AudioXfx.ECHO -> {
                val wet = 0.75 * a; val fb = 0.35 + 0.25 * a
                for (c in 0 until ch) {
                    val d = echo[c][ew]
                    echo[c][ew] = frame[c] + d * fb
                    frame[c] = frame[c] * (1 - 0.35 * a) + d * wet
                }
                ew = (ew + 1) % echoLen
            }
            AudioXfx.REVERB -> {
                for (c in 0 until ch) {
                    val x = frame[c] * 0.25
                    var sum = 0.0
                    for (k in 0 until 4) {
                        val buf = combs[c][k]; val p = combPos[c][k]
                        val y = buf[p]
                        combLp[c][k] = y * 0.6 + combLp[c][k] * 0.4
                        buf[p] = x + combLp[c][k] * 0.84
                        combPos[c][k] = (p + 1) % buf.size
                        sum += y
                    }
                    for (k in 0 until 2) {
                        val buf = aps[c][k]; val p = apPos[c][k]
                        val b = buf[p]
                        buf[p] = sum + b * 0.5
                        sum = b - sum * 0.5
                        apPos[c][k] = (p + 1) % buf.size
                    }
                    frame[c] = frame[c] * (1 - 0.5 * a) + sum * 0.9 * a
                }
            }
            AudioXfx.TAPE_STOP, AudioXfx.TAPE_START -> {
                // Varispeed: read the recent past slower and slower (stop), or from slow up to normal (start).
                val speed = if (type == AudioXfx.TAPE_STOP) (1.0 - a).pow(1.5) else (1.0 - a).pow(0.7)
                if (readPos < 0) readPos = absPos.toDouble()
                readPos += speed
                if (readPos > absPos) readPos = absPos.toDouble()
                if (absPos - readPos > histLen - 4) readPos = (absPos - histLen + 4).toDouble()
                for (c in 0 until ch) frame[c] = histAt(c, readPos) * (if (type == AudioXfx.TAPE_STOP) (1.0 - a * a) else 1.0)
            }
            AudioXfx.STUTTER, AudioXfx.GLITCH -> {
                // Repeat a short slice, more often and shorter as it goes on.
                val slice = (rate * (if (a > 0.6) 0.045 else 0.09)).toInt().coerceAtLeast(64)
                if (a > 0.2) {
                    if (stutterStart < 0 || stutterI >= slice * (if (a > 0.6) 6 else 3)) { stutterStart = w; stutterI = 0 }
                    val p = stutterStart - slice + (stutterI % slice)
                    val env = sin(PI * (stutterI % slice) / slice).coerceAtLeast(0.0).pow(0.25)
                    for (c in 0 until ch) frame[c] = hist[c][((p % histLen) + histLen) % histLen] * env
                    stutterI++
                } else stutterStart = -1
                if (type == AudioXfx.GLITCH) crush(frame, a * 0.8)
            }
            AudioXfx.BITCRUSH -> crush(frame, a)
            AudioXfx.PITCH_DOWN, AudioXfx.PITCH_UP -> {
                val ps = pitch ?: PitchShiftX(ch, rate).also { pitch = it }
                ps.process(frame, if (type == AudioXfx.PITCH_DOWN) 2.0.pow(-a) else 2.0.pow(a * 0.8))
            }
            AudioXfx.TREMOLO -> {
                val hz = 4.0 + 12.0 * a
                val g = 1.0 - a * (0.5 + 0.5 * sin(2 * PI * hz * sec))
                for (c in 0 until ch) frame[c] *= g
            }
            AudioXfx.CHOP -> {
                val hz = 8.0 + 8.0 * a
                val on = ((sec * hz) % 1.0) < 0.5
                val g = if (on) 1.0 else 1.0 - a
                for (c in 0 until ch) frame[c] *= g
            }
            AudioXfx.FLANGER -> {
                val d = (0.001 + 0.004 * (0.5 + 0.5 * sin(2 * PI * 0.35 * sec))) * rate
                for (c in 0 until ch) {
                    val rp = fw - d
                    val i = floor(rp).toInt(); val fr = rp - i
                    val x0 = fl[c][((i % flLen) + flLen) % flLen]; val x1 = fl[c][(((i + 1) % flLen) + flLen) % flLen]
                    val del = x0 + (x1 - x0) * fr
                    fl[c][fw] = frame[c] + del * 0.55
                    frame[c] = frame[c] * (1 - 0.3 * a) + del * 0.8 * a
                }
                fw = (fw + 1) % flLen
            }
            AudioXfx.WOBBLE -> {
                val lfo = 0.5 + 0.5 * sin(2 * PI * (2.0 + 4.0 * a) * sec)
                if (upd) f1.set(180.0 + 3200.0 * lfo, rate, 2.2)
                for (c in 0 until ch) { f1.run(frame[c], c); frame[c] = frame[c] * (1 - a) + f1.low * a * 1.2 }
            }
            AudioXfx.RISER -> {
                if (upd) f1.set(200.0 * (40.0).pow(a), rate, 1.4)
                val n = rnd()
                for (c in 0 until ch) { f1.run(n, c); frame[c] += f1.band * 0.35 * a * a }
            }
            AudioXfx.DRIVE -> {
                val drive = 1.0 + a * 9.0
                for (c in 0 until ch) frame[c] = frame[c] * (1 - a) + tanh(frame[c] * drive) / tanh(drive) * 0.8 * a
            }
        }
        w = (w + 1) % histLen
        t++
    }

    private fun crush(frame: DoubleArray, a: Double) {
        if (a <= 0.01) return
        val hold = 1 + (a * 14).toInt()
        val levels = 2.0.pow(16 - a * 12)
        if (crushN % hold == 0) for (c in 0 until ch) crushHold[c] = floor(frame[c] * levels) / levels
        crushN++
        for (c in 0 until ch) frame[c] = frame[c] * (1 - a) + crushHold[c] * a
    }
}

/** Pitch change keeping the length (two crossfaded read heads), for the transition effects. Own code. */
private class PitchShiftX(private val ch: Int, rate: Int) {
    private val size = (rate * 0.05).toInt().coerceAtLeast(256)
    private val buf = Array(ch) { DoubleArray(size * 2) }
    private var w = 0
    private var phase = 0.0
    fun process(frame: DoubleArray, ratio: Double) {
        val n = buf[0].size
        for (c in 0 until ch) buf[c][w] = frame[c]
        phase = (phase + (1.0 - ratio) / size).let { it - floor(it) }
        val p2 = (phase + 0.5) % 1.0
        val g1 = sin(PI * phase).let { it * it }; val g2 = sin(PI * p2).let { it * it }
        val d1 = phase * size + 1; val d2 = p2 * size + 1
        for (c in 0 until ch) {
            fun at(d: Double): Double { val r = w - d; val i = floor(r).toInt(); val f = r - i; val a = buf[c][((i % n) + n) % n]; val b = buf[c][(((i + 1) % n) + n) % n]; return a + (b - a) * f }
            frame[c] = at(d1) * g1 + at(d2) * g2
        }
        w = (w + 1) % n
    }
}

