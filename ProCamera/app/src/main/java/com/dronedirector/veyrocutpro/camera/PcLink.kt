package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Connect to PC: this phone as a camera for AlcorCut Pro on Windows, over Wi-Fi or a USB cable (USB
 * tethering). The PC finds the phone on the network, connects with the 4-digit PIN shown here, sees the
 * live picture and works the camera; photos and videos are taken here at full quality and copied to
 * the PC. Own code (a small message protocol over TCP; nothing leaves the local network).
 */
object PcLink {
    const val PORT = 8768
    const val DISCOVERY = 8767
    private const val LONG = 1280

    var on by mutableStateOf(false); private set
    /** The PC that's connected now (its address), or null. */
    var peer by mutableStateOf<String?>(null); private set
    var pin by mutableStateOf(""); private set
    /** Photos and videos taken while connected also go to the PC. */
    var sendMedia by mutableStateOf(true)

    private var scope: CoroutineScope? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    @Volatile private var out: DataOutputStream? = null
    private val sendLock = Any()

    /** This phone's address for the PC (Wi-Fi, hotspot or USB tethering), or null. */
    fun address(): String? = RemoteMonitor.localIpAddress()

    fun start(context: Context, controller: ProCameraController) {
        if (on) return
        val app = context.applicationContext
        pin = (1000 + java.security.SecureRandom().nextInt(9000)).toString()
        sendMedia = app.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("pc_send_media", true)
        val ss = try { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) } } catch (_: Throwable) { controller.toast("Couldn't start Connect to PC (the port is in use)"); return }
        server = ss
        on = true
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sc
        // Answers PCs looking for phones on the network.
        sc.launch {
            val sock = try { DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(DISCOVERY)) } } catch (_: Throwable) { null } ?: return@launch
            udp = sock
            val buf = ByteArray(256)
            while (isActive && on) {
                val p = DatagramPacket(buf, buf.size)
                try { sock.receive(p) } catch (_: Throwable) { break }
                if (String(p.data, 0, p.length) != "ALCORCUT?") continue
                val name = (android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + android.os.Build.MODEL).trim()
                val reply = "ALCORCUT $PORT $name".toByteArray()
                try { sock.send(DatagramPacket(reply, reply.size, p.address, p.port)) } catch (_: Throwable) {}
            }
        }
        sc.launch {
            while (isActive && on) {
                val s = try { ss.accept() } catch (_: Throwable) { break }
                if (peer != null) { try { s.close() } catch (_: Throwable) {}; continue }
                serve(app, controller, s, sc)
            }
        }
    }

    fun stop() {
        on = false
        try { server?.close() } catch (_: Throwable) {}
        try { udp?.close() } catch (_: Throwable) {}
        server = null; udp = null
        scope?.cancel()
        scope = null
        peer = null
    }

    fun setSendMedia(context: Context, v: Boolean) {
        sendMedia = v
        context.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putBoolean("pc_send_media", v).apply()
    }

    // ---------------------------------------------------------------- one PC

    private suspend fun serve(context: Context, controller: ProCameraController, s: Socket, sc: CoroutineScope) {
        s.tcpNoDelay = true
        val inp = DataInputStream(s.getInputStream().buffered())
        val o = DataOutputStream(s.getOutputStream().buffered(1 shl 16))
        // The PC must know the PIN shown on this phone.
        try {
            s.soTimeout = 15_000
            val hello = inp.readUTF()
            if (hello != "ALCORCUT $pin") { o.writeUTF("PIN"); o.flush(); s.close(); return }
            o.writeUTF("OK"); o.flush()
            s.soTimeout = 0
        } catch (_: Throwable) { try { s.close() } catch (_: Throwable) {}; return }
        out = o
        peer = s.inetAddress.hostAddress
        withContext(Dispatchers.Main) { controller.toast("Connected to the PC (${s.inetAddress.hostAddress})") }
        val video = sc.launch { encode(controller) }
        val status = sc.launch {
            var lastJson = ""; var lastMedia = controller.state.value.lastMedia
            while (isActive) {
                val st = controller.state.value
                val j = stateJson(controller)
                if (j != lastJson) { send('S', j.toByteArray()); lastJson = j }
                if (st.lastMedia != null && st.lastMedia != lastMedia) { lastMedia = st.lastMedia; if (sendMedia && !st.recording) sendFile(context, st.lastMedia!!) }
                delay(200)
            }
        }
        try {
            while (true) {
                val type = inp.readByte().toInt().toChar()
                val n = inp.readInt()
                if (n < 0 || n > (1 shl 20)) break
                val body = ByteArray(n); inp.readFully(body)
                if (type == 'C') withContext(Dispatchers.Main) { command(controller, String(body)) }
            }
        } catch (_: Throwable) {
        } finally {
            video.cancel(); status.cancel()
            out = null; peer = null
            try { s.close() } catch (_: Throwable) {}
            withContext(Dispatchers.Main) { controller.toast("The PC disconnected") }
        }
    }

    private fun send(type: Char, body: ByteArray, extra: Long = 0L) {
        val o = out ?: return
        synchronized(sendLock) {
            try { o.writeByte(type.code); o.writeInt(body.size + 8); o.writeLong(extra); o.write(body); o.flush() } catch (_: Throwable) {}
        }
    }

    // ---------------------------------------------------------------- picture

    private suspend fun encode(controller: ProCameraController) {
        var w = 0; var h = 0
        var codec: MediaCodec? = null; var surface: Surface? = null
        val info = MediaCodec.BufferInfo()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val t0 = System.nanoTime()
        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val start = System.nanoTime()
                val b: Bitmap? = withContext(Dispatchers.Main) { try { controller.castFrame() } catch (_: Throwable) { null } }
                if (b != null) {
                    // The picture's own shape, long side 1280 (even sizes); a new encoder when the shape changes.
                    val k = LONG.toFloat() / maxOf(b.width, b.height)
                    val nw = (b.width * k).toInt() and 1.inv(); val nh = (b.height * k).toInt() and 1.inv()
                    if (nw != w || nh != h || codec == null) {
                        try { codec?.stop() } catch (_: Throwable) {}; try { codec?.release() } catch (_: Throwable) {}; try { surface?.release() } catch (_: Throwable) {}
                        w = nw; h = nh
                        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                        }
                        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                            it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                            surface = it.createInputSurface()
                            it.start()
                        }
                    }
                    try {
                        val c = surface!!.lockHardwareCanvas()
                        c.drawColor(Color.BLACK)
                        c.drawBitmap(b, null, Rect(0, 0, w, h), paint)
                        surface!!.unlockCanvasAndPost(c)
                    } catch (_: Throwable) {}
                    b.recycle()
                }
                val cd = codec
                while (cd != null) {
                    val i = cd.dequeueOutputBuffer(info, 0)
                    if (i < 0) break
                    val buf = cd.getOutputBuffer(i)
                    if (buf != null && info.size > 0) {
                        val data = ByteArray(info.size); buf.position(info.offset); buf.get(data)
                        val flags = (if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) 2L else 0L) or (if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) 1L else 0L)
                        send('V', data, ((System.nanoTime() - t0) / 1000) shl 2 or flags)
                    }
                    cd.releaseOutputBuffer(i, false)
                }
                delay((33L - (System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1))
            }
        } catch (_: Throwable) {
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { surface?.release() } catch (_: Throwable) {}
        }
    }

    // ---------------------------------------------------------------- state and commands

    private fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun stateJson(c: ProCameraController): String {
        val s = c.state.value
        val (iso, ns) = c.effectiveExposure(s)
        return buildString {
            append("{\"mode\":${q(s.mode.name)},\"ready\":${s.ready},\"recording\":${s.recording},\"paused\":${s.paused},\"seconds\":${s.recordSeconds}")
            append(",\"manualSensor\":${s.manualSensor},\"manualIso\":${s.manualIso},\"manualShutter\":${s.manualShutter},\"iso\":$iso,\"shutterNs\":$ns")
            append(",\"liveIso\":${s.liveIso ?: 0},\"liveShutterNs\":${s.liveShutterNs ?: 0}")
            append(",\"isoStops\":[${s.isoStops.joinToString(",")}],\"shutterStops\":[${s.shutterStops.joinToString(",")}]")
            append(",\"manualFocus\":${s.manualFocus},\"focus\":${s.focusDiopters},\"minFocus\":${s.minFocusDiopters}")
            append(",\"wb\":${q(s.whiteBalance.name)},\"ev\":${s.ev},\"evMin\":${s.evMin},\"evMax\":${s.evMax},\"evStep\":${s.evStep}")
            append(",\"aeLock\":${s.aeLock},\"awbLock\":${s.awbLock},\"zoom\":${s.zoom},\"minZoom\":${s.minZoom},\"maxZoom\":${s.maxZoom}")
            append(",\"flash\":${q(s.flash.name)},\"hasFlash\":${s.hasFlash},\"raw\":${s.rawSupported},\"format\":${q(s.photoFormat.name)}")
            append(",\"videoRes\":${q(s.videoRes.name)},\"resolutions\":[${s.videoResolutions.joinToString(",") { q(it.name) }}],\"fps\":${s.fps},\"fpsOptions\":[${s.fpsOptions.joinToString(",")}]")
            append(",\"profile\":${q(s.profile.name)},\"hlg\":${s.hlgSupported},\"codec\":${q(s.videoCodec.name)},\"hevc\":${s.hevcSupported}")
            append(",\"lens\":${s.lensIndex},\"lenses\":[${s.lenses.joinToString(",") { q(it.label) }}],\"audioLevel\":${s.audioLevel},\"capturing\":${s.capturing}")
            append(",\"stabilization\":${s.stabilization},\"stabSupported\":${s.stabilizationSupported},\"timer\":${s.timerSec},\"countdown\":${s.countdown}}")
        }
    }

    private fun command(c: ProCameraController, json: String) {
        val m = Regex("\"(\\w+)\"\\s*:\\s*(\"[^\"]*\"|[-\\d.eE]+|true|false)").findAll(json).associate { it.groupValues[1] to it.groupValues[2].trim('"') }
        val v = m["value"]
        when (m["cmd"]) {
            "shutter" -> c.shutter()
            "mode" -> CaptureMode.entries.firstOrNull { it.name == v }?.let { c.setMode(it) }
            "lens" -> v?.toIntOrNull()?.let { c.selectLens(it) }
            "iso" -> v?.toIntOrNull()?.let { c.setIso(it) }
            "isoAuto" -> c.setIsoAuto()
            "shutterNs" -> v?.toLongOrNull()?.let { c.setShutter(it) }
            "shutterAuto" -> c.setShutterAuto()
            "focus" -> v?.toFloatOrNull()?.let { c.setFocus(it) }
            "focusAuto" -> c.setFocusAuto()
            // A tap on the PC's picture, as a share of its width and height (0–1).
            "tapN" -> { val x = m["x"]?.toFloatOrNull(); val y = m["y"]?.toFloatOrNull(); if (x != null && y != null) c.tapToFocusShare(x, y) }
            "meterN" -> { val x = m["x"]?.toFloatOrNull(); val y = m["y"]?.toFloatOrNull(); if (x != null && y != null) c.meterAtShare(x, y) }
            "tap" -> { val x = m["x"]?.toFloatOrNull(); val y = m["y"]?.toFloatOrNull(); if (x != null && y != null) c.tapToFocus(x, y) }
            "wb" -> WhiteBalance.entries.firstOrNull { it.name == v }?.let { c.setWhiteBalance(it) }
            "ev" -> v?.toIntOrNull()?.let { c.setEv(it) }
            "zoom" -> v?.toFloatOrNull()?.let { c.setZoom(it) }
            "aeLock" -> c.toggleAeLock()
            "awbLock" -> c.toggleAwbLock()
            "flash" -> FlashSetting.entries.firstOrNull { it.name == v }?.let { c.setFlash(it) }
            "format" -> PhotoFormat.entries.firstOrNull { it.name == v }?.let { c.setPhotoFormat(it) }
            "videoRes" -> VideoRes.entries.firstOrNull { it.name == v }?.let { c.setVideoRes(it) }
            "fps" -> v?.toIntOrNull()?.let { c.setFps(it) }
            "profile" -> VideoProfile.entries.firstOrNull { it.name == v }?.let { c.setProfile(it) }
            "codec" -> VideoCodec.entries.firstOrNull { it.name == v }?.let { c.setVideoCodec(it) }
            "timer" -> v?.toIntOrNull()?.let { c.setTimer(it) }
            "stabilization" -> c.toggleStabilization()
            "pause" -> c.pauseResume()
            "sendMedia" -> sendMedia = v == "true"
        }
    }

    // ---------------------------------------------------------------- files

    /** Copies a photo or video just taken to the PC (in pieces, next to the live picture). */
    private suspend fun sendFile(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment ?: "AlcorCut_${System.currentTimeMillis()}"
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            send('F', name.toByteArray(), size)
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val buf = ByteArray(256 * 1024)
                while (true) { val n = ins.read(buf); if (n < 0) break; send('D', buf.copyOf(n)) }
            }
            send('E', name.toByteArray())
        } catch (_: Throwable) {}
    }
}
