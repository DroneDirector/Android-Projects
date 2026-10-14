package com.dronedirector.procam.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * A CameraX effect that runs every preview / video frame through a small OpenGL shader
 * applying the selected [Filter]'s colour matrix. Changing the filter needs no rebinding.
 */
class FilterEffect(targets: Int, processor: FilterProcessor, onError: (Throwable) -> Unit) :
    CameraEffect(targets, processor.executor, processor, { onError(it) })

class FilterProcessor : SurfaceProcessor {

    private val thread = HandlerThread("ProCamGL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    @Volatile private var colorMatrix = Filter.NONE.glMatrix()
    @Volatile private var colorOffset = Filter.NONE.glOffset()
    @Volatile private var curve = 0
    /** Time-lapse / hyperlapse recorder fed from this processor's frames. */
    @Volatile var lapse: LapseRecorder? = null
    private var lapseSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var lapseFor: LapseRecorder? = null
    /** Size of the camera frames coming in (camera sensor orientation). */
    @Volatile var inputSize: android.util.Size? = null
    @Volatile private var assist = false

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val outputs = LinkedHashMap<SurfaceOutput, EGLSurface>()
    private val texMatrix = FloatArray(16)
    private val outMatrix = FloatArray(16)
    private var released = false

    private val quad: FloatBuffer = floatBuffer(
        // x, y, u, v
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f,
    )

    /** Log curve for recordings (0 = none). With [viewAssist] the preview stays normal-looking. */
    fun setCurve(curve: Int, viewAssist: Boolean) {
        this.curve = curve
        this.assist = viewAssist
    }

    fun setFilter(filter: Filter) {
        colorMatrix = filter.glMatrix()
        colorOffset = filter.glOffset()
    }

    // ------------------------------------------------------------- SurfaceProcessor

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) {
            request.willNotProvideSurface()
            return
        }
        ensureGl()
        // Replace any previous input.
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(tex[0])
        val size = request.resolution
        inputSize = size
        st.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(st)
        st.setOnFrameAvailableListener({ onFrame(st) }, handler)

        val oldTex = texId
        texId = tex[0]
        surfaceTexture = st
        inputSurface = surface
        if (oldTex != 0) GLES20.glDeleteTextures(1, intArrayOf(oldTex), 0)

        request.provideSurface(surface, executor) {
            st.setOnFrameAvailableListener(null)
            st.release()
            surface.release()
            if (surfaceTexture === st) {
                surfaceTexture = null
                inputSurface = null
            }
        }
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (released) {
            output.close()
            return
        }
        ensureGl()
        val surface = output.getSurface(executor) { event ->
            if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                outputs.remove(output)?.let { EGL14.eglDestroySurface(display, it) }
                output.close()
            }
        }
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (egl == null || egl == EGL14.EGL_NO_SURFACE) {
            output.close()
            return
        }
        outputs[output] = egl
    }

    fun release() {
        handler.post {
            released = true
            outputs.forEach { (o, s) -> EGL14.eglDestroySurface(display, s); o.close() }
            outputs.clear()
            surfaceTexture?.release()
            inputSurface?.release()
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            display = EGL14.EGL_NO_DISPLAY
            thread.quitSafely()
        }
    }

    // ------------------------------------------------------------- rendering

    private fun onFrame(st: SurfaceTexture) {
        if (released || st !== surfaceTexture) return
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        try {
            st.updateTexImage()
        } catch (_: Throwable) {
            return
        }
        st.getTransformMatrix(texMatrix)
        val timestamp = st.timestamp

        val dead = ArrayList<SurfaceOutput>()
        for ((output, egl) in outputs) {
            if (!EGL14.eglMakeCurrent(display, egl, egl, context)) { dead += output; continue }
            val size = output.size
            GLES20.glViewport(0, 0, size.width, size.height)
            output.updateTransformMatrix(outMatrix, texMatrix)
            val toVideo = (output.targets and CameraEffect.VIDEO_CAPTURE) != 0
            draw(outMatrix, if (assist && !toVideo) 0 else curve)
            EGLExt.eglPresentationTimeANDROID(display, egl, timestamp)
            if (!EGL14.eglSwapBuffers(display, egl)) dead += output
        }
        renderLapse(timestamp)

        // An output whose surface went away (e.g. after a failed bind) is dropped.
        for (o in dead) {
            outputs.remove(o)?.let { EGL14.eglDestroySurface(display, it) }
        }
    }

    private val identity = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }

    private fun renderLapse(timestamp: Long) {
        val rec = lapse
        if (rec !== lapseFor) {
            if (lapseSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, lapseSurface)
            lapseSurface = EGL14.EGL_NO_SURFACE
            lapseFor = rec
            if (rec != null) {
                lapseSurface = EGL14.eglCreateWindowSurface(display, config, rec.inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
                    ?: EGL14.EGL_NO_SURFACE
            }
        }
        if (rec == null || lapseSurface == EGL14.EGL_NO_SURFACE) return
        val pts = rec.pick(timestamp) ?: return
        if (!EGL14.eglMakeCurrent(display, lapseSurface, lapseSurface, context)) return
        GLES20.glViewport(0, 0, rec.width, rec.height)
        // Camera-native orientation: the MP4 carries a rotation hint instead.
        draw(texMatrix, curve, rec.zoom)
        EGLExt.eglPresentationTimeANDROID(display, lapseSurface, pts)
        if (EGL14.eglSwapBuffers(display, lapseSurface)) rec.frameSubmitted()
    }

    private fun draw(matrix: FloatArray, curveId: Int, zoom: Float = 1f) {
        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aTex = GLES20.glGetAttribLocation(program, "aTex")
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aTex)

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, matrix, 0)
        GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(program, "uColor"), 1, false, colorMatrix, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(program, "uOffset"), 1, colorOffset, 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uCurve"), curveId)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uZoom"), zoom)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun ensureGl() {
        if (display != EGL14.EGL_NO_DISPLAY) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        program = buildProgram()
    }

    private fun buildProgram(): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val vs = compile(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uTexMatrix;
            uniform float uZoom;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vec2 t = (aTex.xy - 0.5) * uZoom + 0.5;
                vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;
            }
            """.trimIndent(),
        )
        val fs = compile(
            GLES20.GL_FRAGMENT_SHADER,
            """
            #extension GL_OES_EGL_image_external : require
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vTex;
            uniform samplerExternalOES uTex;
            uniform mat3 uColor;
            uniform vec3 uOffset;
            uniform int uCurve;

            // DJI D-Log, from DJI's published D-Log transfer function (scene linear -> code value).
            float dlog(float x) {
                return x <= 0.0078 ? 6.025 * x + 0.0929
                                   : (log(x * 0.9892 + 0.0108) / log(10.0)) * 0.256663 + 0.584555;
            }
            // D-Log M style: a milder log (DJI has not published the exact curve).
            float dlogm(float x) {
                return 0.09 + 0.75 * log2(1.0 + 15.0 * x) / 4.0;
            }
            float flatc(float x) {
                return (log(1.0 + 6.0 * x) / log(7.0)) * 0.9 + 0.04;
            }
            vec3 applyCurve(vec3 c) {
                // Undo the camera's display gamma to get (approximately) scene-linear light.
                vec3 lin = pow(max(c, vec3(0.0)), vec3(2.2));
                if (uCurve == 1) return vec3(flatc(lin.r), flatc(lin.g), flatc(lin.b));
                if (uCurve == 2) return vec3(dlog(lin.r), dlog(lin.g), dlog(lin.b));
                if (uCurve == 3) return vec3(dlogm(lin.r), dlogm(lin.g), dlogm(lin.b));
                return c;
            }
            void main() {
                vec4 c = texture2D(uTex, vTex);
                vec3 rgb = clamp(uColor * c.rgb + uOffset, 0.0, 1.0);
                if (uCurve != 0) rgb = clamp(applyCurve(rgb), 0.0, 1.0);
                gl_FragColor = vec4(rgb, 1.0);
            }
            """.trimIndent(),
        )
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private fun floatBuffer(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(v)
                position(0)
            }
    }
}
