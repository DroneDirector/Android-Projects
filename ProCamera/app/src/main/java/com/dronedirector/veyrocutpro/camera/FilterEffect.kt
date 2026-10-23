package com.dronedirector.veyrocutpro.camera

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
    @Volatile private var look: Look? = null
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
    /** Same colour pipeline, reading a normal 2D texture (the blended frames for electronic ND). */
    private var program2D = 0
    private var copyProg = 0
    private var blendProg = 0
    private var texId = 0

    /**
     * Electronic ND / motion blur for video: each output frame is a running blend of the last few
     * camera frames, like a longer shutter. 0 = off; 2, 4, 8 ≈ how many frames are blended.
     */
    /** Feed face blur from the camera texture (when there's no separate analysis stream). */
    @Volatile var faceFeed = false
    /** Sensor rotation for that feed (as an analysis frame would report it). */
    @Volatile var faceRotation = 0
    private var faceFbo = 0
    private var faceTex = 0
    private var faceW = 0
    private var faceH = 0
    private var faceAt = 0L
    private var faceBuf: ByteBuffer? = null

    @Volatile var ndFrames = 0
    /** Mirror left ↔ right (viewfinder and recording). */
    @Volatile var flipX = false
        set(v) { if (field != v) ndPrimed = false; field = v }
    private var ndW = 0
    private var ndH = 0
    private var ndFbo = 0
    private val ndTex = IntArray(3)   // 0 = this frame, 1 / 2 = accumulation ping-pong
    private var ndCur = 1
    @Volatile private var ndPrimed = false
    private var ndFailed = false
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

    // Your LUT (33³ strip from the LUT library), on the viewfinder and optionally in recordings.
    @Volatile private var lutStrip: IntArray? = null
    @Volatile private var lutDirty = false
    @Volatile private var lutPreview = false
    @Volatile private var lutVideo = false
    private var lutTex = 0

    fun setLut(strip: IntArray?, toPreview: Boolean, toVideo: Boolean) {
        lutStrip = strip
        lutDirty = true
        lutPreview = strip != null && toPreview
        lutVideo = strip != null && toVideo
    }

    val hasLut: Boolean get() = lutStrip != null

    fun setFilter(filter: Filter) {
        colorMatrix = filter.glMatrix()
        colorOffset = filter.glOffset()
        look = filter.look
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
        val nd = ndFrames > 1 && accumulateNd()
        if (faceFeed && CaptureExtras.faceBlur) feedFaces()

        val dead = ArrayList<SurfaceOutput>()
        for ((output, egl) in outputs) {
            if (!EGL14.eglMakeCurrent(display, egl, egl, context)) { dead += output; continue }
            val size = output.size
            GLES20.glViewport(0, 0, size.width, size.height)
            // The blended frame is already upright in camera space, so only the output's own transform applies.
            output.updateTransformMatrix(outMatrix, if (nd) identity else texMatrix)
            val toVideo = (output.targets and CameraEffect.VIDEO_CAPTURE) != 0
            draw(outMatrix, if (assist && !toVideo) 0 else curve, lut = if (toVideo) lutVideo else lutPreview, srcTex2D = if (nd) ndTex[ndCur] else 0)
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
        draw(texMatrix, curve, rec.zoom, lut = lutVideo)
        EGLExt.eglPresentationTimeANDROID(display, lapseSurface, pts)
        if (EGL14.eglSwapBuffers(display, lapseSurface)) rec.frameSubmitted()
    }

    private fun uploadLut() {
        if (!lutDirty) return
        lutDirty = false
        val strip = lutStrip ?: return
        val n = 33
        if (lutTex == 0) {
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); lutTex = t[0]
        }
        val buf = ByteBuffer.allocateDirect(strip.size * 4).order(ByteOrder.nativeOrder())
        for (px in strip) { buf.put(((px shr 16) and 0xFF).toByte()); buf.put(((px shr 8) and 0xFF).toByte()); buf.put((px and 0xFF).toByte()); buf.put(0xFF.toByte()) }
        buf.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n * n, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun draw(matrix: FloatArray, curveId: Int, zoom: Float = 1f, lut: Boolean = false, srcTex2D: Int = 0) {
        uploadLut()
        val program = if (srcTex2D != 0 && program2D != 0) program2D else this.program
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
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFlip"), if (flipX) 1f else 0f)
        val lk = look
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLook"), if (lk != null) 1 else 0)
        if (lk != null) {
            GLES20.glUniform3fv(GLES20.glGetUniformLocation(program, "uShadow"), 1, lk.shadow, 0)
            GLES20.glUniform3fv(GLES20.glGetUniformLocation(program, "uHigh"), 1, lk.highlight, 0)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSCurve"), lk.sCurve)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFade"), lk.fade)
        }

        // Privacy face blur (Settings → PRIVACY).
        val faces = if (CaptureExtras.faceBlur) FaceBlur.current() else FloatArray(0)
        val fn = (faces.size / 4).coerceAtMost(FaceBlur.MAX)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uFaceN"), fn)
        if (fn > 0) GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uFaces"), fn, faces, 0)
        val useLut = lut && lutTex != 0 && lutStrip != null
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLutOn"), if (useLut) 1 else 0)
        // The LUT sampler always gets its own texture unit (some GPUs refuse to draw — black — if two
        // sampler types point at the same unit, even when one isn't used).
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (useLut) lutTex else dummyTex())
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLut"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (srcTex2D != 0 && program == program2D) GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex2D)
        else GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    // ------------------------------------------------------------- face blur feed

    /** About 6 times a second: a small, unblurred copy of the camera picture for face detection. */
    private fun feedFaces() {
        val now = System.currentTimeMillis()
        if (now - faceAt < 160 || FaceBlur.isBusy) return
        val size = inputSize ?: return
        if (copyProg == 0) copyProg = buildSimple(COPY_FS, external = true)
        if (copyProg == 0) return
        faceAt = now
        val scale = 360f / maxOf(size.width, size.height)
        val w = (size.width * scale).toInt().coerceAtLeast(16) / 4 * 4
        val h = (size.height * scale).toInt().coerceAtLeast(16) / 4 * 4
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        if (faceFbo == 0 || w != faceW || h != faceH) {
            if (faceFbo != 0) { GLES20.glDeleteFramebuffers(1, intArrayOf(faceFbo), 0); GLES20.glDeleteTextures(1, intArrayOf(faceTex), 0) }
            val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); faceFbo = f[0]
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); faceTex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, faceTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, faceFbo)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, faceTex, 0)
            faceW = w; faceH = h
            faceBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, faceFbo)
        GLES20.glViewport(0, 0, w, h)
        // Identity matrix: pixel (x, y) here is texture coordinate (x / w, y / h) — the space the
        // blur shader works in, so the found boxes need no further conversion.
        drawSimple(copyProg, identity, ext = texId, a = 0, b = 0, k = 0f)
        val buf = faceBuf ?: return
        buf.position(0)
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        val luma = ByteArray(w * h)
        for (i in 0 until w * h) {
            val r = buf.get(i * 4).toInt() and 0xFF; val g = buf.get(i * 4 + 1).toInt() and 0xFF; val b = buf.get(i * 4 + 2).toInt() and 0xFF
            luma[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }
        FaceBlur.submitLuma(luma, w, h, faceRotation)
    }

    // ------------------------------------------------------------- electronic ND

    /** Sets up (or resizes) the frame / accumulation textures. False if this GPU can't. */
    private fun ensureNd(w: Int, h: Int): Boolean {
        if (ndFailed) return false
        if (copyProg == 0) copyProg = buildSimple(COPY_FS, external = true)
        if (blendProg == 0) blendProg = buildSimple(BLEND_FS, external = false)
        if (program2D == 0) program2D = buildProgram(faces = false, external = false)
        if (copyProg == 0 || blendProg == 0 || program2D == 0) { ndFailed = true; return false }
        if (ndFbo != 0 && w == ndW && h == ndH) return true
        if (ndFbo != 0) { GLES20.glDeleteFramebuffers(1, intArrayOf(ndFbo), 0); GLES20.glDeleteTextures(3, ndTex, 0) }
        val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); ndFbo = f[0]
        GLES20.glGenTextures(3, ndTex, 0)
        for (t in ndTex) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ndFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ndTex[0], 0)
        val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (!ok) { ndFailed = true; return false }
        ndW = w; ndH = h; ndPrimed = false
        return true
    }

    /** Copies this camera frame upright into a texture and blends it into the running average. */
    private fun accumulateNd(): Boolean {
        val size = inputSize ?: return false
        // Work at up to 1920 px on the long side: smooth enough, and light on the GPU.
        val scale = (1920f / maxOf(size.width, size.height)).coerceAtMost(1f)
        val w = (size.width * scale).toInt().coerceAtLeast(16)
        val h = (size.height * scale).toInt().coerceAtLeast(16)
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        if (!ensureNd(w, h)) return false
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ndFbo)
        GLES20.glViewport(0, 0, w, h)
        // 1. This frame → ndTex[0].
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ndTex[0], 0)
        drawSimple(copyProg, texMatrix, ext = texId, a = 0, b = 0, k = 0f)
        // 2. mix(previous blend, this frame, k) → the other accumulation texture.
        val src = ndCur
        val dst = if (ndCur == 1) 2 else 1
        val k = if (!ndPrimed) 1f else 2f / (ndFrames + 1f)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ndTex[dst], 0)
        drawSimple(blendProg, identity, ext = 0, a = ndTex[src], b = ndTex[0], k = k)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        ndCur = dst
        ndPrimed = true
        return true
    }

    private fun drawSimple(prog: Int, matrix: FloatArray, ext: Int, a: Int, b: Int, k: Float) {
        GLES20.glUseProgram(prog)
        val aPos = GLES20.glGetAttribLocation(prog, "aPos")
        val aTex = GLES20.glGetAttribLocation(prog, "aTex")
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uTexMatrix"), 1, false, matrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uZoom"), 1f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uFlip"), 0f)
        if (ext != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ext)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTex"), 0)
        } else {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, a)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uA"), 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, b)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uB"), 2)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uK"), k)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun buildSimple(fsSrc: String, external: Boolean): Int {
        fun compile(type: Int, src: String): Int {
            val sh = GLES20.glCreateShader(type)
            GLES20.glShaderSource(sh, src); GLES20.glCompileShader(sh)
            val ok = IntArray(1); GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) { android.util.Log.w("AlcorCutPro", "ND shader: " + GLES20.glGetShaderInfoLog(sh)); GLES20.glDeleteShader(sh); return 0 }
            return sh
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, VS_SRC)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        val linked = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) { GLES20.glDeleteProgram(p); return 0 }
        return p
    }

    private var dummy = 0
    private fun dummyTex(): Int {
        if (dummy != 0) return dummy
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); dummy = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dummy)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).put(byteArrayOf(0, 0, 0, -1)); px.position(0)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
        return dummy
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

    /** Builds the shader; if this GPU rejects the face-blur part, builds it without (never a black picture). */
    private fun buildProgram(): Int {
        val p = buildProgram(faces = true)
        if (p != 0) return p
        android.util.Log.w("AlcorCutPro", "Camera shader with face blur failed; using the plain shader")
        facesSupported = false
        return buildProgram(faces = false)
    }

    /** False if this GPU couldn't build the face-blur shader. */
    @Volatile var facesSupported = true
        private set

    private fun buildProgram(faces: Boolean, external: Boolean = true): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) android.util.Log.w("AlcorCutPro", "Shader: " + GLES20.glGetShaderInfoLog(s))
            return s
        }
        val vs = compile(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uTexMatrix;
            uniform float uZoom;
            uniform float uFlip;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vec2 t = (aTex.xy - 0.5) * uZoom + 0.5;
                if (uFlip > 0.5) t.x = 1.0 - t.x;
                vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;
            }
            """.trimIndent(),
        )
        val fsSrc0 =
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
            uniform int uLook;
            uniform vec3 uShadow;
            uniform vec3 uHigh;
            uniform float uSCurve;
            uniform float uFade;
            uniform sampler2D uLut;
            uniform int uLutOn;
            // Privacy face blur: faces as (centre, half size) in the camera texture.
            uniform vec4 uFaces[8];
            uniform int uFaceN;

            // Your 3D LUT, stored as 33 slices of 33×33 side by side (blue picks the slice).
            vec3 lut3(vec3 c) {
                float n = 33.0;
                c = clamp(c, 0.0, 1.0);
                float b = c.b * (n - 1.0);
                float b0 = floor(b);
                float b1 = min(b0 + 1.0, n - 1.0);
                float x = (c.r * (n - 1.0) + 0.5) / (n * n);
                float y = (c.g * (n - 1.0) + 0.5) / n;
                vec3 lo = texture2D(uLut, vec2(x + b0 / n, y)).rgb;
                vec3 hi = texture2D(uLut, vec2(x + b1 / n, y)).rgb;
                return mix(lo, hi, b - b0);
            }

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
            // Pixelates [tc] if it lies inside face [f] (about 12 blocks across the face).
            vec2 faceAt(vec2 tc, vec4 f) {
                vec2 h = max(f.zw, vec2(0.001));
                vec2 d = (vTex - f.xy) / h;
                if (dot(d, d) >= 1.0) return tc;
                vec2 cell = max(h / 6.0, vec2(0.002));
                return (floor(vTex / cell) + 0.5) * cell;
            }
            void main() {
                vec2 tc = vTex;
                FACES
                vec4 c = texture2D(uTex, tc);
                vec3 rgb = clamp(uColor * c.rgb + uOffset, 0.0, 1.0);
                if (uLook == 1) {
                    float l = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                    vec3 sc = rgb * rgb * (3.0 - 2.0 * rgb);
                    rgb = mix(rgb, sc, uSCurve);
                    rgb += uShadow * (1.0 - smoothstep(0.0, 0.5, l)) + uHigh * smoothstep(0.5, 1.0, l);
                    rgb = clamp(uFade + rgb * (1.0 - uFade), 0.0, 1.0);
                }
                if (uCurve != 0) rgb = clamp(applyCurve(rgb), 0.0, 1.0);
                if (uLutOn == 1) rgb = lut3(rgb);
                gl_FragColor = vec4(rgb, 1.0);
            }
            """.trimIndent().replace("FACES", if (faces) FACE_CODE else "")
        val fsSrc = if (external) fsSrc0 else fsSrc0
            .replace("#extension GL_OES_EGL_image_external : require\n", "")
            .replace("uniform samplerExternalOES uTex;", "uniform sampler2D uTex;")
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val linked = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        if (linked[0] == 0) {
            android.util.Log.w("AlcorCutPro", "Program: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteProgram(p)
            return 0
        }
        return p
    }

    companion object {
        /** Up to 8 faces, each with a constant array index (works on every GLES 2 GPU). */
        private val FACE_CODE = (0 until 8).joinToString("\n") { "if (uFaceN > $it) tc = faceAt(tc, uFaces[$it]);" }
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val VS_SRC = """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uTexMatrix;
            uniform float uZoom;
            uniform float uFlip;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vec2 t = (aTex.xy - 0.5) * uZoom + 0.5;
                if (uFlip > 0.5) t.x = 1.0 - t.x;
                vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        private val COPY_FS = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES uTex;
            void main() { gl_FragColor = vec4(texture2D(uTex, vTex).rgb, 1.0); }
        """.trimIndent()

        private val BLEND_FS = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uA;
            uniform sampler2D uB;
            uniform float uK;
            void main() { gl_FragColor = vec4(mix(texture2D(uA, vTex).rgb, texture2D(uB, vTex).rgb, uK), 1.0); }
        """.trimIndent()

        private fun floatBuffer(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(v)
                position(0)
            }
    }
}
