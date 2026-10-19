package com.dronedirector.veyrocutpro.editor

/**
 * AI voice isolation (Audio → Voice noise reduction → "Voice isolation (AI)"): keeps speech and
 * removes wind, traffic, hum, crowd and music, using RNNoise (BSD 3-clause, bundled as native code).
 *
 * RNNoise works on 48 kHz mono frames of 480 samples; other sample rates are resampled to 48 kHz
 * and back. Interleaved samples in (−1..1) → the same number of samples out, [delay] frames late.
 */
class VoiceIsolator(private val channels: Int, private val rate: Int, private val amount: Float = 1f) {
    companion object {
        private const val FRAME = 480
        val available: Boolean by lazy {
            try { System.loadLibrary("veyrocut_native"); true } catch (_: Throwable) { false }
        }
        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(h: Long)
        @JvmStatic private external fun nativeProcess(h: Long, input: FloatArray, output: FloatArray): Float
    }

    /** Frames of delay (at the input rate) before the first cleaned sample. */
    val delay: Int = (FRAME.toLong() * rate / 48000).toInt() + 4

    private inner class Chan {
        val h = nativeCreate()
        // Up-sampling (input rate → 48 kHz) state.
        var upPos = 0.0; var upPrev = 0f; var upHave = false
        // 48 kHz frame being filled, its dry copy, and cleaned 48 kHz output waiting to be down-sampled.
        val inBuf = FloatArray(FRAME); val outBuf = FloatArray(FRAME); var fill = 0
        val clean48 = ArrayDeque<Float>()
        val dry48 = ArrayDeque<Float>()
        // Down-sampling (48 kHz → input rate) state.
        var downPos = 0.0; var downPrev = 0f; var downPrevDry = 0f; var downHave = false
        val outQ = ArrayDeque<Float>()

        fun push48(v: Float) {
            inBuf[fill++] = v * 32768f
            if (fill == FRAME) {
                nativeProcess(h, inBuf, outBuf)
                for (i in 0 until FRAME) { clean48.addLast(outBuf[i] / 32768f); dry48.addLast(inBuf[i] / 32768f) }
                fill = 0
                drain()
            }
        }

        /** Down-sample whatever is ready back to the input rate. */
        fun drain() {
            val step = 48000.0 / rate
            while (clean48.isNotEmpty()) {
                val c = clean48.removeFirst(); val d = dry48.removeFirst()
                if (!downHave) { downPrev = c; downPrevDry = d; downHave = true; outQ.addLast(mix(c, d)); downPos = step; continue }
                // Emit every output sample that falls between the previous and this 48 kHz sample.
                while (downPos <= 1.0) {
                    val t = downPos.toFloat()
                    outQ.addLast(mix(downPrev + (c - downPrev) * t, downPrevDry + (d - downPrevDry) * t))
                    downPos += step
                }
                downPos -= 1.0
                downPrev = c; downPrevDry = d
            }
        }

        private fun mix(clean: Float, dry: Float) = if (amount >= 0.999f) clean else dry + (clean - dry) * amount

        fun push(v: Float) {
            if (rate == 48000) { push48(v); return }
            val step = rate / 48000.0
            if (!upHave) { upPrev = v; upHave = true; push48(v); upPos = step; return }
            while (upPos <= 1.0) { push48(upPrev + (v - upPrev) * upPos.toFloat()); upPos += step }
            upPos -= 1.0
            upPrev = v
        }
    }

    private val chans = Array(channels) { Chan() }
    private var primed = 0

    fun process(input: FloatArray): FloatArray {
        val frames = input.size / channels
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) chans[c].push(input[f * channels + c])
            // The first [delay] frames come out silent; after that, one cleaned frame per input frame.
            if (primed < delay) { primed++; continue }
            for (c in 0 until channels) out[f * channels + c] = chans[c].outQ.removeFirstOrNull() ?: 0f
        }
        return out
    }

    fun release() { chans.forEach { try { nativeDestroy(it.h) } catch (_: Throwable) {} } }
}
