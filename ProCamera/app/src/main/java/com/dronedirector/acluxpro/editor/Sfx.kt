package com.dronedirector.acluxpro.editor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sound effects that can be put on a cut (Transitions → Audio transitions → SOUND EFFECTS).
 * [hitMs] is the moment in the sound that lands on the cut: 0 for impacts (they start on the cut),
 * the full length for risers (they build up to the cut).
 */
enum class TransitionSfx(val label: String, val detail: String, val lengthMs: Int, val hitMs: Int) {
    IMPACT("Cinematic impact", "Deep thump with a sharp crack and a rumbling tail", 2500, 0),
    BOOM("Cinematic boom", "Huge low boom that rolls out", 3500, 0),
    TRAILER_HIT("Trailer hit", "Boom with a metallic ring, like a movie trailer", 3000, 0),
    SUB_DROP("Sub drop", "Low tone that falls away under the cut", 1800, 0),
    WHOOSH("Whoosh", "Air rushing past, left to right", 1000, 500),
    SWOOSH("Fast swoosh", "Short, quick swish", 450, 220),
    SWIRL("Swirl", "Spinning, circling air that lands on the cut", 1500, 1000),
    RISER("Riser", "Tension that builds up into the cut", 2500, 2500),
    REVERSE("Reverse cymbal", "Swelling shimmer that sucks into the cut", 2000, 2000),
    GLITCH("Glitch", "Digital stutters and crackles", 700, 80),
}

/** Makes the sound effects on the phone (no downloaded audio, no licences) and caches them as WAV files. */
object SfxSynth {
    private const val SR = 44100
    private const val VERSION = 1

    /** The WAV file for [s]; made the first time it's needed. Call off the main thread. */
    @Synchronized
    fun file(context: Context, s: TransitionSfx): File {
        val f = File(File(context.cacheDir, "sfx").apply { mkdirs() }, "${s.name.lowercase()}_v$VERSION.wav")
        if (!f.exists() || f.length() < 100) {
            val (l, r) = render(s)
            val tmp = File(f.path + ".tmp")
            writeWav(tmp, l, r)
            tmp.renameTo(f)
        }
        return f
    }

    /** State-variable filter: low / band / high outputs with a cutoff that can change every sample. */
    private class Svf {
        var low = 0f; var band = 0f; var high = 0f
        fun step(x: Float, fc: Float, q: Float = 0.8f) {
            val f = (2.0 * sin(PI * fc.coerceIn(20f, 5200f) / SR)).toFloat()
            high = x - low - band / q
            band += f * high
            low += f * band
        }
    }

    private class Noise(seed: Long) {
        private val r = java.util.Random(seed)
        fun next() = r.nextFloat() * 2f - 1f
    }

    private fun render(s: TransitionSfx): Pair<FloatArray, FloatArray> {
        val n = s.lengthMs * SR / 1000
        val l = FloatArray(n); val r = FloatArray(n)
        val nl = Noise(11L + s.ordinal); val nr = Noise(97L + s.ordinal)
        val fl = Svf(); val fr = Svf(); val gl = Svf(); val gr = Svf()
        val len = s.lengthMs / 1000f
        var ph = 0.0; var ph2 = 0.0
        when (s) {
            TransitionSfx.IMPACT, TransitionSfx.TRAILER_HIT -> {
                val partials = floatArrayOf(87f, 143.5f, 231f, 377f, 611f)
                val pp = DoubleArray(partials.size)
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val f = 35f + 45f * exp(-t * 12f)
                    ph += 2 * PI * f / SR
                    val att = (t / 0.004f).coerceAtMost(1f)
                    val thump = sin(ph).toFloat() * exp(-t * 3.2f) * att
                    val a = nl.next(); val b = nr.next()
                    fl.step(a, 2500f); fr.step(b, 2500f)
                    gl.step(a, 380f); gr.step(b, 380f)
                    val crack = exp(-t * 35f) * 0.7f
                    val tail = exp(-t * 2.2f) * 0.9f
                    var metal = 0f
                    if (s == TransitionSfx.TRAILER_HIT) for (k in partials.indices) {
                        pp[k] += 2 * PI * partials[k] / SR
                        metal += sin(pp[k]).toFloat() * exp(-t * (1.3f + k * 0.5f)) * 0.22f * att
                    }
                    val body = tanh(1.6f * thump)
                    l[i] = body + fl.high * crack + gl.low * tail + metal
                    r[i] = body + fr.high * crack + gr.low * tail + metal * 0.9f
                }
            }
            TransitionSfx.BOOM -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                ph += 2 * PI * (28f + 30f * exp(-t * 4f)) / SR
                val att = (t / 0.006f).coerceAtMost(1f)
                val sub = tanh(1.8f * sin(ph).toFloat()) * exp(-t * 1.4f) * att
                val a = nl.next(); val b = nr.next()
                fl.step(a, 150f); fr.step(b, 150f)
                gl.step(a, 1200f); gr.step(b, 1200f)
                val rumble = exp(-t * 1.0f) * 1.6f
                val hit = exp(-t * 20f) * 0.6f
                l[i] = sub + fl.low * rumble + gl.low * hit
                r[i] = sub + fr.low * rumble + gr.low * hit
            }
            TransitionSfx.SUB_DROP -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                ph += 2 * PI * (30f + 90f * exp(-t * 2.5f)) / SR
                val att = (t / 0.005f).coerceAtMost(1f)
                val v = tanh(1.5f * sin(ph).toFloat()) * exp(-t * 1.2f) * att
                l[i] = v; r[i] = v
            }
            TransitionSfx.WHOOSH, TransitionSfx.SWOOSH -> {
                val (f0, f1) = if (s == TransitionSfx.WHOOSH) 250f to 3200f else 600f to 4800f
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val p = t / len
                    // Bell shape that peaks a little before the middle, then a quicker tail.
                    val env = sin(PI.toFloat() * p.toDouble().pow(0.85).toFloat()).let { it * it }
                    val fc = f0 + (f1 - f0) * p
                    fl.step(nl.next(), fc, 1.4f); fr.step(nr.next(), fc * 1.03f, 1.4f)
                    val pan = p * (PI.toFloat() / 2)
                    l[i] = fl.band * env * cos(pan) * 2.2f
                    r[i] = fr.band * env * sin(pan) * 2.2f
                }
            }
            TransitionSfx.SWIRL -> {
                val hit = s.hitMs / 1000f
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val p = (t / hit).coerceAtMost(1f)
                    var env = p.toDouble().pow(1.5).toFloat()
                    if (t > hit) env *= exp(-(t - hit) * 8f)
                    val fc = 400f + 1800f * p + 600f * sin(2 * PI * 7 * t).toFloat()
                    fl.step(nl.next(), fc, 2.2f); fr.step(nr.next(), fc, 2.2f)
                    val pan = (0.5f + 0.5f * sin(2 * PI * 3 * t).toFloat()) * (PI.toFloat() / 2)
                    l[i] = fl.band * env * cos(pan) * 2.6f
                    r[i] = fr.band * env * sin(pan) * 2.6f
                }
            }
            TransitionSfx.RISER -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val p = t / len
                val f = 180f * 2f.pow(p * 2.5f) * (1f + 0.006f * sin(2 * PI * 5.5 * t).toFloat())
                ph += 2 * PI * f / SR
                ph2 += 2 * PI * f * 1.005 / SR
                var tone = 0f
                for (k in 1..4) tone += (sin(ph * k) + sin(ph2 * k)).toFloat() / (2f * k)
                fl.step(nl.next(), 800f + 4200f * p); fr.step(nr.next(), 800f + 4200f * p)
                val env = p * p
                val end = ((len - t) / 0.015f).coerceIn(0f, 1f)
                l[i] = (tone * 0.35f + fl.high * 0.5f) * env * end
                r[i] = (tone * 0.35f + fr.high * 0.5f) * env * end
            }
            TransitionSfx.REVERSE -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val p = t / len
                val env = exp((t - len) * 3.5f) * p
                val end = ((len - t) / 0.01f).coerceIn(0f, 1f)
                fl.step(nl.next(), 3000f); fr.step(nr.next(), 3000f)
                gl.step(nl.next(), 6500f, 3f); gr.step(nr.next(), 6500f, 3f)
                l[i] = (fl.high * 0.7f + gl.band * 0.6f) * env * end
                r[i] = (fr.high * 0.7f + gr.band * 0.6f) * env * end
            }
            TransitionSfx.GLITCH -> {
                val rnd = java.util.Random(4242)
                var i = 0
                while (i < n) {
                    val seg = (SR * (0.02f + rnd.nextFloat() * 0.04f)).toInt()
                    val kind = rnd.nextInt(4)
                    val freq = 200f + rnd.nextFloat() * 1800f
                    val hold = 1 + rnd.nextInt(24)
                    val panL = 0.3f + rnd.nextFloat() * 0.7f; val panR = 1.3f - panL
                    var held = 0f
                    for (j in 0 until seg) {
                        if (i >= n) break
                        val t = i.toFloat() / SR
                        val env = exp(-t * 2.5f) * (if (j < 40) j / 40f else 1f) * (if (seg - j < 40) (seg - j) / 40f else 1f)
                        val v = when (kind) {
                            0 -> if (sin(2 * PI * freq * t) > 0) 0.5f else -0.5f
                            1 -> { if (j % hold == 0) held = nl.next(); held * 0.8f }
                            2 -> nl.next() * 0.6f
                            else -> 0f
                        }
                        l[i] = v * env * panL; r[i] = v * env * panR
                        i++
                    }
                }
            }
        }
        // Normalise to a safe peak.
        var peak = 0f
        for (i in 0 until n) peak = maxOf(peak, abs(l[i]), abs(r[i]))
        if (peak > 0f) { val k = 0.89f / peak; for (i in 0 until n) { l[i] *= k; r[i] *= k } }
        return l to r
    }

    private fun writeWav(f: File, l: FloatArray, r: FloatArray) {
        val n = l.size
        val data = n * 4
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(SR).putInt(SR * 4).putShort(4).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (i in 0 until n) {
            b.putShort((l[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            b.putShort((r[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        FileOutputStream(f).use { it.write(b.array()) }
    }
}

/** Where (timeline ms) the sound effect on the cut after clip [k] starts; can be negative near the start. */
fun sfxStartMs(clips: List<Clip>, starts: LongArray, k: Int): Long? {
    val fx = clips.getOrNull(k)?.transition?.sfx ?: return null
    if (k + 1 > clips.lastIndex) return null
    return starts[k + 1] + overlapMs(clips, k) / 2 - fx.hitMs
}

/** Plays a sound effect once (preview and the ▶ buttons). */
object SfxPlayer {
    fun play(context: Context, s: TransitionSfx, volume: Float, skipMs: Int = 0) {
        val app = context.applicationContext
        Thread {
            try {
                val f = SfxSynth.file(app, s)
                val mp = android.media.MediaPlayer()
                mp.setDataSource(f.path)
                mp.setOnCompletionListener { it.release() }
                mp.prepare()
                val v = volume.coerceIn(0f, 1f)
                mp.setVolume(v, v)
                if (skipMs > 0) mp.seekTo(skipMs)
                mp.start()
            } catch (_: Throwable) {}
        }.start()
    }
}
