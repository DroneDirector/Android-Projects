package com.dronedirector.veyrocutpro.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The live picture for a TV (Chromecast): frames from a [source] (the viewfinder, or the video editor's
 * viewer) are encoded to H.264 and served from this phone as a live HLS stream on the local network —
 * the format a Chromecast plays. Own code: the HLS server and the MPEG-TS packaging are written here.
 * The TV lags a few seconds behind (HLS works in short segments).
 */
object CastFeed {
    private const val PORT = 8766
    private const val W = 1280
    private const val H = 720
    private const val FPS = 24
    private const val SEG_FRAMES = FPS          // one-second segments
    private const val KEEP = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var server: ServerSocket? = null
    private val segments = ArrayDeque<Pair<Int, ByteArray>>()
    private var seq = 0
    @Volatile var running = false
        private set

    /** The stream's address for the TV, or null when the phone isn't on a network. */
    fun url(): String? = RemoteMonitor.localIpAddress()?.let { "http://$it:$PORT/live.m3u8" }

    /** Starts serving [source] (called on the main thread at about 24 frames a second; may return null). */
    fun start(source: () -> Bitmap?) {
        stop()
        running = true
        synchronized(segments) { segments.clear(); seq = 0 }
        val ss = try { ServerSocket(PORT) } catch (_: Throwable) { running = false; return }
        server = ss
        thread(name = "CastFeed-http", isDaemon = true) {
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (_: Throwable) { break }
                thread(isDaemon = true) { serve(s) }
            }
        }
        job = scope.launch { encode(source) }
    }

    fun stop() {
        running = false
        job?.cancel(); job = null
        try { server?.close() } catch (_: Throwable) {}
        server = null
    }

    // ---- encoding

    private suspend fun encode(source: () -> Bitmap?) {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
        }
        val codec = try { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) } catch (_: Throwable) { running = false; return }
        val surface: Surface
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
        } catch (_: Throwable) { try { codec.release() } catch (_: Throwable) {}; running = false; return }
        val ts = TsWriter()
        var config: ByteArray? = null
        var frames = 0
        val info = MediaCodec.BufferInfo()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val t0 = System.nanoTime()
        try {
            while (scope.isActive && running) {
                val frameStart = System.nanoTime()
                val b = withContext(Dispatchers.Main) { try { source() } catch (_: Throwable) { null } }
                try {
                    val c = surface.lockHardwareCanvas()
                    c.drawColor(Color.BLACK)
                    if (b != null) {
                        val k = minOf(W.toFloat() / b.width, H.toFloat() / b.height)
                        val dw = (b.width * k).toInt(); val dh = (b.height * k).toInt()
                        c.drawBitmap(b, null, Rect((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), paint)
                    }
                    surface.unlockCanvasAndPost(c)
                } catch (_: Throwable) {}
                b?.recycle()
                // Collect what the encoder has ready.
                while (true) {
                    val i = codec.dequeueOutputBuffer(info, 0)
                    if (i < 0) break
                    val buf = codec.getOutputBuffer(i)
                    if (buf != null && info.size > 0) {
                        val data = ByteArray(info.size); buf.position(info.offset); buf.get(data)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) config = data
                        else {
                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                            if (key && frames >= SEG_FRAMES) { finishSegment(ts); frames = 0 }
                            if (key || frames > 0) {
                                val pts90 = (System.nanoTime() - t0) / 1_000_000L * 90
                                ts.video(if (key && config != null) config!! + data else data, pts90, key)
                                frames++
                            }
                        }
                    }
                    codec.releaseOutputBuffer(i, false)
                }
                val took = (System.nanoTime() - frameStart) / 1_000_000L
                delay((1000L / FPS - took).coerceAtLeast(1))
            }
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            try { codec.release() } catch (_: Throwable) {}
            try { surface.release() } catch (_: Throwable) {}
        }
    }

    private fun finishSegment(ts: TsWriter) {
        val bytes = ts.take()
        if (bytes.isEmpty()) return
        synchronized(segments) {
            segments.addLast(seq++ to bytes)
            while (segments.size > KEEP) segments.removeFirst()
        }
    }

    // ---- HTTP (playlist + segments), with the CORS header a Chromecast needs

    private fun serve(s: Socket) = s.use { sock ->
        try {
            val line = sock.getInputStream().bufferedReader().readLine() ?: return
            val path = line.split(' ').getOrNull(1) ?: "/"
            val out = sock.getOutputStream()
            fun reply(code: String, type: String, body: ByteArray) {
                out.write(("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(body); out.flush()
            }
            when {
                path.startsWith("/live.m3u8") -> {
                    val list = synchronized(segments) { segments.toList() }
                    val first = list.firstOrNull()?.first ?: 0
                    val sb = StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:$first\n")
                    list.forEach { (n, _) -> sb.append("#EXTINF:1.000,\nseg$n.ts\n") }
                    reply("200 OK", "application/vnd.apple.mpegurl", sb.toString().toByteArray())
                }
                path.startsWith("/seg") -> {
                    val n = path.removePrefix("/seg").substringBefore(".ts").toIntOrNull()
                    val seg = synchronized(segments) { segments.firstOrNull { it.first == n }?.second }
                    if (seg != null) reply("200 OK", "video/mp2t", seg) else reply("404 Not Found", "text/plain", ByteArray(0))
                }
                else -> reply("404 Not Found", "text/plain", ByteArray(0))
            }
        } catch (_: Throwable) {}
    }

    /** MPEG transport stream for one H.264 track (PAT, PMT and PES packets). */
    private class TsWriter {
        private val out = ByteArrayOutputStream()
        private var ccPat = 0; private var ccPmt = 0; private var ccVid = 0
        private var tablesWritten = false

        fun take(): ByteArray { val b = out.toByteArray(); out.reset(); tablesWritten = false; return b }

        fun video(nal: ByteArray, pts: Long, key: Boolean) {
            if (!tablesWritten || key) { psi(0, pat()); psi(PMT_PID, pmt()); tablesWritten = true }
            // PES header with PTS; an access unit delimiter isn't needed by players.
            val pes = ByteArrayOutputStream()
            pes.write(byteArrayOf(0, 0, 1, 0xE0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5))
            pes.write(ptsBytes(pts, 0x21))
            pes.write(nal)
            val data = pes.toByteArray()
            var pos = 0; var first = true
            while (pos < data.size) {
                val pkt = ByteArray(188) { 0xFF.toByte() }
                pkt[0] = 0x47
                pkt[1] = ((if (first) 0x40 else 0) or (VID_PID shr 8)).toByte()
                pkt[2] = (VID_PID and 0xFF).toByte()
                var hdr = 4
                val remaining = data.size - pos
                val needPcr = first && key
                val afMin = if (needPcr) 8 else 0
                val room = 184 - afMin
                if (needPcr || remaining < room) {
                    // Adaptation field: PCR on keyframes, stuffing to fill the last packet.
                    val payload = minOf(remaining, 184 - (if (needPcr) 8 else 2).coerceAtMost(184))
                    val afLen = 183 - payload
                    pkt[3] = (0x30 or (ccVid and 0x0F)).toByte()
                    pkt[4] = afLen.toByte()
                    if (afLen > 0) {
                        pkt[5] = if (needPcr) 0x50 else 0x00
                        var p = 6
                        if (needPcr) {
                            val base = pts - 2 * 90
                            pkt[6] = (base shr 25).toByte(); pkt[7] = (base shr 17).toByte(); pkt[8] = (base shr 9).toByte()
                            pkt[9] = (base shr 1).toByte(); pkt[10] = (((base and 1) shl 7) or 0x7E).toInt().toByte(); pkt[11] = 0
                            p = 12
                        }
                        while (p < 5 + afLen) { pkt[p] = 0xFF.toByte(); p++ }
                    }
                    hdr = 5 + afLen
                } else pkt[3] = (0x10 or (ccVid and 0x0F)).toByte()
                val n = 188 - hdr
                System.arraycopy(data, pos, pkt, hdr, n)
                pos += n; first = false; ccVid++
                out.write(pkt)
            }
        }

        private fun psi(pid: Int, section: ByteArray) {
            val pkt = ByteArray(188) { 0xFF.toByte() }
            pkt[0] = 0x47; pkt[1] = (0x40 or (pid shr 8)).toByte(); pkt[2] = (pid and 0xFF).toByte()
            val cc = if (pid == 0) ccPat++ else ccPmt++
            pkt[3] = (0x10 or (cc and 0x0F)).toByte(); pkt[4] = 0
            System.arraycopy(section, 0, pkt, 5, section.size)
            out.write(pkt)
        }

        private fun pat(): ByteArray = withCrc(byteArrayOf(0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0x00, 0x01, (0xE0 or (PMT_PID shr 8)).toByte(), (PMT_PID and 0xFF).toByte()))

        private fun pmt(): ByteArray = withCrc(byteArrayOf(0x02, 0xB0.toByte(), 0x12, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            (0xE0 or (VID_PID shr 8)).toByte(), (VID_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00,
            0x1B, (0xE0 or (VID_PID shr 8)).toByte(), (VID_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00))

        private fun withCrc(b: ByteArray): ByteArray {
            var crc = -1
            for (x in b) {
                crc = crc xor ((x.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return b + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
        }

        private fun ptsBytes(pts: Long, marker: Int): ByteArray = byteArrayOf(
            ((marker and 0xF0) or (((pts shr 30) and 0x07).toInt() shl 1) or 1).toByte(),
            (pts shr 22).toByte(), ((((pts shr 15) and 0x7F).toInt() shl 1) or 1).toByte(),
            (pts shr 7).toByte(), ((((pts) and 0x7F).toInt() shl 1) or 1).toByte(),
        )

        companion object { const val PMT_PID = 0x1000; const val VID_PID = 0x100 }
    }
}
