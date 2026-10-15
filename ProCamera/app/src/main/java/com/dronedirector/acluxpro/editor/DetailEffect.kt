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

/** GPU sharpen, denoise, vignette and film grain for video (preview and export). */
@UnstableApi
class DetailEffect(private val d: Detail) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = DetailProgram(d, useHdr)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = !d.needsVideoPass
}

@UnstableApi
private class DetailProgram(private val d: Detail, useHdr: Boolean) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(VERTEX, FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var texelW = 0f
    private var texelH = 0f
    private var aspect = 1f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelW = 1f / inputWidth
        texelH = 1f / inputHeight
        aspect = inputWidth.toFloat() / inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(texelW, texelH))
            program.setFloatUniform("uSharpen", d.sharpen * 1.5f)
            program.setFloatUniform("uDenoise", d.denoise)
            program.setFloatUniform("uVignette", d.vignette)
            program.setFloatUniform("uGrain", d.grain * 0.11f)
            program.setFloatUniform("uAspect", aspect)
            program.setFloatUniform("uTime", (presentationTimeUs % 10_000_000L) / 1_000_000f)
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

    companion object {
        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            uniform float uSharpen;
            uniform float uDenoise;
            uniform float uVignette;
            uniform float uGrain;
            uniform float uAspect;
            uniform float uTime;
            varying vec2 vTexSamplingCoord;

            float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233)) + uTime * 7.0) * 43758.5453); }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 c = texture2D(uTexSampler, uv);
                vec3 rgb = c.rgb;
                if (uDenoise > 0.0 || uSharpen > 0.0) {
                    vec3 sum = vec3(0.0);
                    float wsum = 0.0;
                    vec3 blur = vec3(0.0);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            vec3 s = texture2D(uTexSampler, uv + vec2(float(dx), float(dy)) * uTexel * 1.5).rgb;
                            blur += s;
                            // Edge-aware weight: similar colours count more.
                            float w = exp(-dot(s - rgb, s - rgb) * 60.0);
                            sum += s * w;
                            wsum += w;
                        }
                    }
                    blur /= 9.0;
                    if (uDenoise > 0.0) rgb = mix(rgb, sum / wsum, uDenoise);
                    if (uSharpen > 0.0) rgb = rgb + (rgb - blur) * uSharpen;
                }
                if (uVignette != 0.0) {
                    vec2 p = (uv - 0.5) * vec2(uAspect, 1.0) / sqrt(uAspect * uAspect * 0.25 + 0.25);
                    float d = length(p);
                    float f = clamp((d - 0.35) / 0.65, 0.0, 1.0);
                    rgb *= 1.0 + uVignette * f * f;
                }
                if (uGrain > 0.0) {
                    rgb += (rand(uv * 1000.0) - 0.5) * uGrain;
                }
                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
            }
        """
    }
}
