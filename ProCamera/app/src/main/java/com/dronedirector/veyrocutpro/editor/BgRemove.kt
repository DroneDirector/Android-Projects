package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Background removal behind people (Color → Detail → BACKGROUND, or a video layer's REMOVE
 * BACKGROUND): ML Kit selfie segmentation on every frame (on the phone, free to use in apps).
 * [mode] 0 = transparent (layers below show through), 1 = blurred background, 2 = a colour.
 */
data class BgRemoval(val mode: Int = 1, val color: Int = 0xFF00B140.toInt())

@UnstableApi
class BgRemoveEffect(private val mode: Int, private val color: Int = 0) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = BgRemoveProgram(mode, color, useHdr)
}

@UnstableApi
private class BgRemoveProgram(private val mode: Int, private val color: Int, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val copy = try { GlProgram(VERT, COPY) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1; private var h = 1
    private var sw = 256; private var sh = 144
    private var fbo = 0; private var smallTex = 0; private var maskTex = 0
    private var read: ByteBuffer? = null
    private var maskBytes: ByteBuffer? = null
    private var maskW = 0; private var maskH = 0
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).enableRawSizeMask().build()
    )

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        copy.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth; h = inputHeight
        // The model's own input is 256 px on the long side.
        if (w >= h) { sw = 256; sh = (256f * h / w).toInt().coerceAtLeast(16) } else { sh = 256; sw = (256f * w / h).toInt().coerceAtLeast(16) }
        return Size(inputWidth, inputHeight)
    }

    /** A small copy of the frame, read back to the CPU, through the person segmenter → mask texture. */
    private fun segment(texId: Int) {
        val prevFbo = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        if (fbo == 0) { smallTex = GlUtil.createTexture(sw, sh, false); fbo = GlUtil.createFboForTexture(smallTex) }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, sw, sh)
        copy.use()
        copy.setSamplerTexIdUniform("uTex", texId, 0)
        copy.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        val buf = read ?: ByteBuffer.allocateDirect(sw * sh * 4).order(ByteOrder.nativeOrder()).also { read = it }
        buf.clear()
        GLES20.glReadPixels(0, 0, sw, sh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
        buf.rewind()
        // GL rows run bottom-up: flip so people stand upright for the model.
        val raw = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        val bmp = Bitmap.createBitmap(raw, 0, 0, sw, sh, Matrix().apply { preScale(1f, -1f) }, false)
        raw.recycle()
        val m = try { Tasks.await(segmenter.process(InputImage.fromBitmap(bmp, 0))) } catch (_: Throwable) { null }
        bmp.recycle()
        if (m == null) return
        val fb = m.buffer.order(ByteOrder.nativeOrder()); fb.rewind()
        val mw = m.width; val mh = m.height
        val bytes = maskBytes?.takeIf { maskW == mw && maskH == mh } ?: ByteBuffer.allocateDirect(mw * mh).also { maskBytes = it }
        bytes.clear()
        // Back to GL's bottom-up rows.
        val rows = Array(mh) { FloatArray(mw) }
        for (y in 0 until mh) for (x in 0 until mw) rows[y][x] = if (fb.remaining() >= 4) fb.float else 0f
        for (y in mh - 1 downTo 0) for (x in 0 until mw) bytes.put((rows[y][x].coerceIn(0f, 1f) * 255f).toInt().toByte())
        bytes.rewind()
        if (maskTex == 0 || maskW != mw || maskH != mh) {
            if (maskTex != 0) try { GlUtil.deleteTexture(maskTex) } catch (_: Throwable) {}
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); maskTex = t[0]
            maskW = mw; maskH = mh
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mw, mh, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bytes)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            try { segment(inputTexId) } catch (_: Throwable) {}
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            // No mask yet (or none): keep the whole picture.
            program.setSamplerTexIdUniform("uMask", if (maskTex != 0) maskTex else inputTexId, 1)
            program.setFloatUniform("uHasMask", if (maskTex != 0) 1f else 0f)
            program.setFloatUniform("uMode", mode.toFloat())
            program.setFloatsUniform("uColor", floatArrayOf(((color shr 16) and 255) / 255f, ((color shr 8) and 255) / 255f, (color and 255) / 255f))
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        try { segmenter.close() } catch (_: Throwable) {}
        try {
            program.delete(); copy.delete()
            if (fbo != 0) GlUtil.deleteFbo(fbo)
            if (smallTex != 0) GlUtil.deleteTexture(smallTex)
            if (maskTex != 0) GlUtil.deleteTexture(maskTex)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        private const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
        """
        private const val COPY = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUv;
            void main() { gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }
        """
        private const val FRAG = """
            precision mediump float;
            uniform sampler2D uTex;
            uniform sampler2D uMask;
            uniform float uHasMask;
            uniform float uMode;
            uniform vec3 uColor;
            uniform vec2 uTexel;
            varying vec2 vUv;
            void main() {
                vec4 c = texture2D(uTex, vUv);
                float m = uHasMask > 0.5 ? smoothstep(0.3, 0.7, texture2D(uMask, vUv).r) : 1.0;
                if (uMode < 0.5) { gl_FragColor = vec4(c.rgb, c.a * m); return; }
                vec3 bg;
                if (uMode < 1.5) {
                    vec3 s = vec3(0.0);
                    for (int i = 0; i < 16; i++) {
                        float a = float(i) * 0.3927;
                        float r = (i < 8) ? 14.0 : 28.0;
                        s += texture2D(uTex, vUv + vec2(cos(a), sin(a)) * vec2(uTexel.x / uTexel.y, 1.0) * r / 720.0).rgb;
                    }
                    bg = s / 16.0;
                } else bg = uColor;
                gl_FragColor = vec4(mix(bg, c.rgb, m), c.a);
            }
        """
    }
}
