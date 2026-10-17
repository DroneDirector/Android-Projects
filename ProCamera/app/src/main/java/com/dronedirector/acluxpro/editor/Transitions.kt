package com.dronedirector.acluxpro.editor

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

enum class TransitionGroup(val label: String) { BASIC("Basic"), WIPES("Wipes & shapes"), MOTION("Motion"), FANCY("Stylized") }

/** How the incoming clip is combined with the outgoing one. */
enum class TransitionKind { UNIFORM, MASK, MOVE }

enum class TransitionType(val id: Float, val label: String, val group: TransitionGroup, val kind: TransitionKind) {
    NONE(0f, "Cut", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    CROSSFADE(1f, "Cross dissolve", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    DIP_BLACK(2f, "Dip to black", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    DIP_WHITE(3f, "Dip to white", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    FLASH(4f, "Flash", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    BLUR(5f, "Blur dissolve", TransitionGroup.BASIC, TransitionKind.UNIFORM),

    WIPE_LEFT(10f, "Wipe left", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_RIGHT(11f, "Wipe right", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_UP(12f, "Wipe up", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_DOWN(13f, "Wipe down", TransitionGroup.WIPES, TransitionKind.MASK),
    DIAGONAL(14f, "Diagonal", TransitionGroup.WIPES, TransitionKind.MASK),
    IRIS(15f, "Iris", TransitionGroup.WIPES, TransitionKind.MASK),
    CLOCK(16f, "Clock", TransitionGroup.WIPES, TransitionKind.MASK),
    BLINDS(17f, "Blinds", TransitionGroup.WIPES, TransitionKind.MASK),
    BARN_DOOR(18f, "Barn door", TransitionGroup.WIPES, TransitionKind.MASK),
    MOSAIC(19f, "Mosaic", TransitionGroup.WIPES, TransitionKind.MASK),

    PUSH_LEFT(30f, "Push left", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_RIGHT(31f, "Push right", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_UP(32f, "Push up", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_DOWN(33f, "Push down", TransitionGroup.MOTION, TransitionKind.MOVE),
    WHIP_LEFT(34f, "Whip pan left", TransitionGroup.MOTION, TransitionKind.MOVE),
    WHIP_RIGHT(35f, "Whip pan right", TransitionGroup.MOTION, TransitionKind.MOVE),
    ZOOM_IN(36f, "Zoom in", TransitionGroup.MOTION, TransitionKind.UNIFORM),
    ZOOM_OUT(37f, "Zoom out", TransitionGroup.MOTION, TransitionKind.UNIFORM),
    SPIN(38f, "Spin", TransitionGroup.MOTION, TransitionKind.UNIFORM),

    LIGHT_LEAK(6f, "Light leak", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    INK(20f, "Ink bleed", TransitionGroup.FANCY, TransitionKind.MASK),
    SPARKLE(21f, "Sparkle dissolve", TransitionGroup.FANCY, TransitionKind.MASK),
    GLITCH(40f, "Glitch", TransitionGroup.FANCY, TransitionKind.MASK),
    PIXELATE(41f, "Pixelate", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    CHROMA(42f, "Chromatic", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    RIPPLE(43f, "Ripple", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    LIGHT_SPEED(44f, "Light speed", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    SWIRL(45f, "Swirl", TransitionGroup.FANCY, TransitionKind.UNIFORM),
}

/**
 * Transition from a clip into the next one: the picture ([type]) and the sound ([audio]).
 * [audio] / [audioMs] are nullable so projects saved before audio transitions still load.
 */
data class Transition(
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 800L,
    val audio: AudioTransitionType? = null,
    val audioMs: Long? = null,
) {
    val audioType: AudioTransitionType get() = audio ?: AudioTransitionType.AUTO
    val audioLen: Long get() = audioMs ?: 600L
}

/** How the sound changes from one clip to the next. */
enum class AudioTransitionType(val label: String, val detail: String) {
    AUTO("Match video", "Smooth crossfade over the video transition; nothing on a plain cut."),
    CUT("Hard cut", "Sound switches in the middle of the transition, no fade."),
    CONSTANT_GAIN("Constant gain", "Straight-line crossfade. Can dip slightly in the middle."),
    CONSTANT_POWER("Constant power", "Equal-power crossfade — no dip in loudness. Best all-rounder."),
    EXPONENTIAL("Exponential fade", "Outgoing sound drops away quickly, incoming swells in late. Natural for music."),
    DIP("Dip to silence", "Outgoing fades out, a short silence, then the next clip fades in."),
    QUICK_OUT("Quick out, slow in", "Old sound disappears fast; new sound eases in."),
    QUICK_IN("Slow out, quick in", "Old sound lingers and fades; new sound arrives fast."),
}

enum class FadeCurve { LINEAR, POWER, EXP }

private fun curveIn(c: FadeCurve, p: Float): Float = when (c) {
    FadeCurve.LINEAR -> p
    FadeCurve.POWER -> kotlin.math.sin(p * (Math.PI / 2).toFloat())
    FadeCurve.EXP -> p * p
}

/** Fade at the end of the outgoing clip: starts [fromEndMs] before its end, takes [lenMs], then silence. */
data class FadeOut(val fromEndMs: Long, val lenMs: Long, val curve: FadeCurve) {
    fun gain(remainingMs: Long): Float {
        val elapsed = fromEndMs - remainingMs
        if (elapsed <= 0) return 1f
        val r = elapsed.toFloat() / lenMs.coerceAtLeast(1)
        return if (r >= 1f) 0f else curveIn(curve, 1f - r)
    }
    fun scaled(k: Float) = copy(fromEndMs = (fromEndMs * k).toLong(), lenMs = (lenMs * k).toLong().coerceAtLeast(1))
}

/** Fade at the start of the incoming clip: silent for [delayMs], then rises over [lenMs]. */
data class FadeIn(val delayMs: Long, val lenMs: Long, val curve: FadeCurve) {
    fun gain(tMs: Long): Float {
        if (tMs < delayMs) return 0f
        val p = (tMs - delayMs).toFloat() / lenMs.coerceAtLeast(1)
        return if (p >= 1f) 1f else curveIn(curve, p)
    }
    fun scaled(k: Float) = copy(delayMs = (delayMs * k).toLong(), lenMs = (lenMs * k).toLong().coerceAtLeast(1))
}

/** Sound shaping for one cut, used by both the preview and the export. */
object AudioXfade {
    /**
     * Fade-out for the clip before the cut and fade-in for the clip after it.
     * With a video transition the sound works inside the overlap ([overlapMs]); on a plain cut
     * (no overlap) the clips can't play together, so the sound fades out and back in around the cut,
     * over [audioMs] in total.
     */
    fun shapes(t: AudioTransitionType, overlapMs: Long, audioMs: Long): Pair<FadeOut?, FadeIn?> {
        val w = when {
            overlapMs > 0 -> overlapMs
            t == AudioTransitionType.AUTO || t == AudioTransitionType.CUT -> return null to null
            else -> (audioMs / 2).coerceAtLeast(20)
        }
        val q = (w / 4).coerceAtLeast(1)
        val h = (w / 2).coerceAtLeast(1)
        return when (t) {
            AudioTransitionType.AUTO, AudioTransitionType.CONSTANT_GAIN -> FadeOut(w, w, FadeCurve.LINEAR) to FadeIn(0, w, FadeCurve.LINEAR)
            AudioTransitionType.CONSTANT_POWER -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.EXPONENTIAL -> FadeOut(w, w, FadeCurve.EXP) to FadeIn(0, w, FadeCurve.EXP)
            AudioTransitionType.CUT -> FadeOut(h, 1, FadeCurve.LINEAR) to FadeIn(h, 1, FadeCurve.LINEAR)
            AudioTransitionType.DIP -> FadeOut(w, h, FadeCurve.POWER) to FadeIn(h, h, FadeCurve.POWER)
            AudioTransitionType.QUICK_OUT -> FadeOut(w, q, FadeCurve.LINEAR) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.QUICK_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, q, FadeCurve.LINEAR)
        }
    }

    /** The sound shaping for the cut after clip [i] (null pair when there is no next clip). */
    fun forCut(clips: List<Clip>, i: Int): Pair<FadeOut?, FadeIn?> {
        val a = clips.getOrNull(i) ?: return null to null
        if (i + 1 > clips.lastIndex) return null to null
        return shapes(a.transition.audioType, overlapMs(clips, i), a.transition.audioLen)
    }
}

/** Overlap (ms) between clip [i] and clip [i + 1]. */
fun overlapMs(clips: List<Clip>, i: Int): Long {
    val a = clips.getOrNull(i) ?: return 0
    val b = clips.getOrNull(i + 1) ?: return 0
    if (a.transition.type == TransitionType.NONE) return 0
    return a.transition.durationMs.coerceAtMost((minOf(a.lengthMs, b.lengthMs) * 0.45).toLong()).coerceAtLeast(0)
}

/** Where each clip starts on the timeline (transitions overlap neighbouring clips). */
fun clipStarts(clips: List<Clip>): LongArray {
    val out = LongArray(clips.size)
    for (i in 1 until clips.size) out[i] = out[i - 1] + clips[i - 1].lengthMs - overlapMs(clips, i - 1)
    return out
}

fun timelineLength(clips: List<Clip>): Long {
    if (clips.isEmpty()) return 0
    return clipStarts(clips).last() + clips.last().lengthMs
}

/** Clip under a timeline time (the later clip wins inside a transition) and the offset into it (timeline ms). */
fun clipAt(clips: List<Clip>, ms: Long): Pair<Int, Long> {
    if (clips.isEmpty()) return 0 to 0
    val s = clipStarts(clips)
    var i = 0
    while (i + 1 < clips.size && ms >= s[i + 1]) i++
    return i to (ms - s[i]).coerceIn(0, clips[i].lengthMs)
}

object TransitionMath {
    private fun ss(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    fun ease(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - Math.pow((-2.0 * x + 2), 3.0).toFloat() / 2

    /** How much of the incoming clip shows for uniform transitions (same as the shader). */
    fun uniformReveal(t: TransitionType, p: Float): Float = when (t.id.toInt()) {
        1 -> p
        2, 3 -> ss(0.48f, 0.52f, p)
        4 -> ss(0.35f, 0.65f, p)
        5, 6 -> ss(0.3f, 0.7f, p)
        36, 37, 38 -> ss(0.4f, 0.6f, p)
        41 -> ss(0.45f, 0.55f, p)
        42, 43, 44, 45 -> ss(0.25f, 0.75f, p)
        else -> 1f
    }

    /** Movement (fraction of the frame, x right, y down) for push / whip transitions. */
    fun move(t: TransitionType, p: Float, incoming: Boolean): Pair<Float, Float> {
        val e = if (t == TransitionType.WHIP_LEFT || t == TransitionType.WHIP_RIGHT) ease(ease(p)) else ease(p)
        return when (t) {
            TransitionType.PUSH_LEFT, TransitionType.WHIP_LEFT -> (if (incoming) 1 - e else -e) to 0f
            TransitionType.PUSH_RIGHT, TransitionType.WHIP_RIGHT -> (if (incoming) -(1 - e) else e) to 0f
            TransitionType.PUSH_UP -> 0f to (if (incoming) 1 - e else -e)
            TransitionType.PUSH_DOWN -> 0f to (if (incoming) -(1 - e) else e)
            else -> 0f to 0f
        }
    }

    /**
     * Shared transition shader body (GLSL ES 1.0 and AGSL both accept it). Needs `tex(uv)` and
     * uniforms uType, uP (0..1), uSide (0 outgoing, 1 incoming), uMode (0 opaque, 1 alpha = reveal,
     * 2 alpha = 1 - reveal) and uSize (pixels). uv has its origin bottom-left.
     */
    const val CORE = """
        float rnd(vec2 c) { return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453); }
        float vnoise(vec2 q) {
            vec2 i = floor(q); vec2 f = fract(q); f = f * f * (3.0 - 2.0 * f);
            float a = rnd(i); float b = rnd(i + vec2(1.0, 0.0)); float c = rnd(i + vec2(0.0, 1.0)); float d = rnd(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }
        float fbm(vec2 q) { float v = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { v += a * vnoise(q); q *= 2.0; a *= 0.5; } return v; }
        bool isT(float id) { return abs(uType - id) < 0.5; }
        float easeF(float x) { if (x < 0.5) return 4.0 * x * x * x; float y = -2.0 * x + 2.0; return 1.0 - y * y * y / 2.0; }
        float sstep(float a, float b, float x) { return smoothstep(a, b, x); }
        float cover(float v, float e, float s) { return 1.0 - smoothstep(e - s, e + s, v); }
        vec4 mtex(vec2 uv) { vec2 m = abs(mod(uv + 1.0, 2.0) - 1.0); return tex(m); }

        float reveal(vec2 uv) {
            float p = uP;
            float aspect = uSize.x / uSize.y;
            if (isT(1.0)) return p;
            if (isT(2.0) || isT(3.0)) return sstep(0.48, 0.52, p);
            if (isT(4.0)) return sstep(0.35, 0.65, p);
            if (isT(5.0) || isT(6.0)) return sstep(0.3, 0.7, p);
            if (isT(36.0) || isT(37.0) || isT(38.0)) return sstep(0.4, 0.6, p);
            if (isT(41.0)) return sstep(0.45, 0.55, p);
            if (isT(42.0) || isT(43.0) || isT(44.0) || isT(45.0)) return sstep(0.25, 0.75, p);
            float e = mix(-0.05, 1.05, p);
            if (isT(10.0)) return cover(1.0 - uv.x, e, 0.02);
            if (isT(11.0)) return cover(uv.x, e, 0.02);
            if (isT(12.0)) return cover(uv.y, e, 0.02);
            if (isT(13.0)) return cover(1.0 - uv.y, e, 0.02);
            if (isT(14.0)) return cover((uv.x + 1.0 - uv.y) * 0.5, e, 0.03);
            vec2 c = (uv - 0.5) * vec2(aspect, 1.0);
            if (isT(15.0)) { float md = length(vec2(0.5 * aspect, 0.5)); return cover(length(c), p * md * 1.06, 0.012); }
            if (isT(16.0)) { float a = fract(atan(c.x, c.y) / 6.2831853 + 1.0); return cover(a, mix(-0.02, 1.02, p), 0.006); }
            if (isT(17.0)) return cover(fract(uv.y * 10.0), e, 0.02);
            if (isT(18.0)) return cover(abs(uv.x - 0.5) * 2.0, e, 0.02);
            if (isT(19.0)) return step(rnd(floor(uv * vec2(14.0 * aspect, 14.0))) * 0.98 + 0.01, p);
            if (isT(20.0)) { float v = mix(uv.x, fbm(uv * vec2(aspect, 1.0) * 3.0), 0.6); return cover(v, mix(-0.15, 1.15, p), 0.05); }
            if (isT(21.0)) return step(rnd(floor(uv * uSize / 3.0)) * 0.98 + 0.01, p);
            if (isT(40.0)) { float b = rnd(vec2(floor(uv.y * 18.0), floor(p * 12.0))); return step(0.5 + (b - 0.5) * 0.4, p); }
            return 1.0;
        }

        vec4 shade(vec2 uv0) {
            if (uType < 0.5) return tex(uv0);
            float p = uP;
            float k = 1.0 - abs(2.0 * p - 1.0);
            bool inc = uSide > 0.5;
            float aspect = uSize.x / uSize.y;
            vec2 uv = uv0;
            vec2 c = uv - 0.5;
            if (isT(36.0)) { float s = inc ? mix(0.5, 1.0, easeF(p)) : mix(1.0, 2.5, easeF(p)); uv = c / s + 0.5; }
            if (isT(37.0)) { float s = inc ? mix(2.5, 1.0, easeF(p)) : mix(1.0, 0.5, easeF(p)); uv = c / s + 0.5; }
            if (isT(38.0)) {
                float ang = inc ? -(1.0 - easeF(p)) * 3.14159 : easeF(p) * 3.14159;
                vec2 q = c * vec2(aspect, 1.0);
                float cs = cos(ang); float sn = sin(ang);
                q = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
                uv = q / vec2(aspect, 1.0) / (1.0 + k * 0.6) + 0.5;
            }
            if (isT(43.0)) { float d = length(c * vec2(aspect, 1.0)); uv += c / max(d, 0.001) * sin(d * 40.0 - p * 30.0) * 0.02 * k; }
            if (isT(45.0)) {
                vec2 q = c * vec2(aspect, 1.0); float d = length(q);
                float a = k * 4.0 * (1.0 - smoothstep(0.0, 0.7, d));
                float cs = cos(a); float sn = sin(a);
                q = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
                uv = q / vec2(aspect, 1.0) + 0.5;
            }
            if (isT(41.0)) { float cells = mix(200.0, 12.0, k); vec2 cl = vec2(cells * aspect, cells); uv = (floor(uv * cl) + 0.5) / cl; }
            if (isT(40.0)) {
                float blk = rnd(vec2(floor(uv.y * 24.0), floor(p * 20.0)));
                if (blk > 1.0 - k * 0.6) uv.x += (rnd(vec2(blk, p)) - 0.5) * 0.25 * k;
            }
            vec3 col;
            if (isT(5.0) || isT(34.0) || isT(35.0) || isT(36.0) || isT(37.0) || isT(38.0) || isT(44.0)) {
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 12; i++) {
                    float f = float(i) / 11.0 - 0.5;
                    vec2 o;
                    if (isT(34.0) || isT(35.0)) o = vec2(f * 0.14 * k, 0.0);
                    else if (isT(5.0)) o = vec2(cos(f * 40.0), sin(f * 40.0)) * 0.014 * k * (0.3 + abs(f));
                    else o = (uv - 0.5) * f * 0.2 * k * (isT(44.0) ? 2.0 : 1.0);
                    acc += mtex(uv + o).rgb;
                }
                col = acc / 12.0;
            } else if (isT(40.0) || isT(42.0)) {
                float amt = (isT(40.0) ? 0.03 : 0.025) * k;
                vec2 dir = isT(42.0) ? (uv - 0.5) * 2.0 : vec2(1.0, 0.0);
                col = vec3(mtex(uv + dir * amt).r, mtex(uv).g, mtex(uv - dir * amt).b);
            } else {
                col = mtex(uv).rgb;
            }
            float dip = inc ? clamp((1.0 - p) * 2.0, 0.0, 1.0) : clamp(p * 2.0, 0.0, 1.0);
            if (isT(2.0)) col *= 1.0 - dip;
            if (isT(3.0)) col = mix(col, vec3(1.0), dip);
            if (isT(4.0)) col += vec3(k * k * 1.2);
            if (isT(6.0)) {
                float x = uv0.x + uv0.y * 0.3 - mix(-0.3, 1.6, p);
                float band = exp(-(x * 3.0) * (x * 3.0));
                col += vec3(1.0, 0.55, 0.2) * band * 1.4 * k + vec3(1.0, 0.8, 0.6) * k * k * 0.5;
            }
            if (isT(44.0)) col += vec3(k * k * 0.8);
            if (isT(40.0)) col *= 1.0 - 0.25 * k * step(0.5, fract(uv0.y * uSize.y * 0.5));
            if (isT(21.0)) { float r = rnd(floor(uv0 * uSize / 3.0)) * 0.98 + 0.01; col += vec3(1.0, 0.9, 0.7) * (1.0 - smoothstep(0.0, 0.05, abs(r - p))) * 1.5; }
            if (isT(20.0)) {
                float v = mix(uv0.x, fbm(uv0 * vec2(aspect, 1.0) * 3.0), 0.6);
                col += vec3(0.25, 0.1, 0.35) * (1.0 - smoothstep(0.0, 0.08, abs(v - mix(-0.15, 1.15, p)))) * k;
            }
            float rv = reveal(uv0);
            float a = uMode > 1.5 ? 1.0 - rv : (uMode > 0.5 ? rv : 1.0);
            return vec4(clamp(col, 0.0, 1.0), a);
        }
    """

    const val AGSL = """
        uniform shader content;
        uniform float2 uSize;
        uniform float uType;
        uniform float uP;
        uniform float uSide;
        uniform float uMode;
        vec4 tex(vec2 uv) { return vec4(content.eval(vec2(clamp(uv.x, 0.0, 1.0), 1.0 - clamp(uv.y, 0.0, 1.0)) * uSize)); }
    """ + CORE + """
        half4 main(float2 fc) {
            vec4 c = shade(vec2(fc.x / uSize.x, 1.0 - fc.y / uSize.y));
            return half4(c.rgb * c.a, c.a);
        }
    """

    const val GLSL_VERTEX = """
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;
        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
        }
    """

    const val GLSL_FRAGMENT = """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform vec2 uSize;
        uniform float uType;
        uniform float uP;
        uniform float uSide;
        uniform float uMode;
        varying vec2 vTexSamplingCoord;
        vec4 tex(vec2 uv) { return texture2D(uTexSampler, clamp(uv, 0.0, 1.0)); }
    """ + CORE + """
        void main() { gl_FragColor = shade(vTexSamplingCoord); }
    """
}

/** What one clip does at its start (coming in) and end (going out) during export. */
data class TransitionWindows(
    val lengthMs: Long,
    val inType: TransitionType, val inMs: Long, val inMode: Int,
    val outType: TransitionType, val outMs: Long, val outMode: Int,
)

/** Export: runs the transition shader on a clip's first / last frames. */
@UnstableApi
class TransitionEffect(private val w: TransitionWindows) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = TransitionProgram(w, useHdr)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        (w.inType == TransitionType.NONE || w.inMs <= 0) && (w.outType == TransitionType.NONE || w.outMs <= 0)
}

@UnstableApi
private class TransitionProgram(private val w: TransitionWindows, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try {
        GlProgram(TransitionMath.GLSL_VERTEX, TransitionMath.GLSL_FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var width = 1f
    private var height = 1f
    private var t0 = Long.MIN_VALUE

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth.toFloat(); height = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val local = (presentationTimeUs - t0) / 1000L
        var type = 0f; var p = 0f; var side = 0f; var mode = 0f
        if (w.inType != TransitionType.NONE && w.inMs > 0 && local < w.inMs) {
            type = w.inType.id; p = (local.toFloat() / w.inMs).coerceIn(0f, 1f); side = 1f; mode = w.inMode.toFloat()
        } else if (w.outType != TransitionType.NONE && w.outMs > 0 && local > w.lengthMs - w.outMs) {
            type = w.outType.id; p = (1f - (w.lengthMs - local).toFloat() / w.outMs).coerceIn(0f, 1f); side = 0f; mode = w.outMode.toFloat()
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uSize", floatArrayOf(width, height))
            program.setFloatUniform("uType", type)
            program.setFloatUniform("uP", p)
            program.setFloatUniform("uSide", side)
            program.setFloatUniform("uMode", mode)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

/** Live preview: applies a transition to a player's view (API 33+ shader, simple fade before that). */
object TransitionPreview {
    private val shaders = HashMap<android.view.View, Any>()

    fun apply(view: android.view.View, t: TransitionType, p: Float, incoming: Boolean) {
        val (tx, ty) = TransitionMath.move(t, p, incoming)
        view.translationX = tx * view.width
        view.translationY = ty * view.height
        view.translationZ = if (incoming) 2f else 0f
        if (android.os.Build.VERSION.SDK_INT >= 33 && view.width > 0 && view.height > 0) {
            view.alpha = 1f
            val sh = shaders.getOrPut(view) { android.graphics.RuntimeShader(TransitionMath.AGSL) } as android.graphics.RuntimeShader
            sh.setFloatUniform("uSize", view.width.toFloat(), view.height.toFloat())
            sh.setFloatUniform("uType", t.id)
            sh.setFloatUniform("uP", p)
            sh.setFloatUniform("uSide", if (incoming) 1f else 0f)
            sh.setFloatUniform("uMode", if (incoming && t.kind != TransitionKind.MOVE) 1f else 0f)
            view.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content"))
        } else {
            view.alpha = if (incoming) (if (t.kind == TransitionKind.MOVE) 1f else TransitionMath.uniformReveal(t, p).let { if (t.kind == TransitionKind.MASK) p else it }) else 1f
        }
    }

    fun reset(view: android.view.View, visible: Boolean) {
        view.translationX = 0f; view.translationY = 0f; view.translationZ = 0f
        view.alpha = if (visible) 1f else 0f
        if (android.os.Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
    }
}
