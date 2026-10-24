package com.dronedirector.veyrocutpro.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
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
 * The live picture and sound for a TV (Chromecast): frames from a source (the viewfinder, or the video
 * editor's viewer with its titles and layers) are encoded to H.264, sound to AAC, and served from this
 * phone as a live HLS stream on the local network — the format a Chromecast plays. Own code: the HLS
 * server and the MPEG-TS packaging are written here. The TV lags a few seconds behind (HLS works in
 * short segments). When no screen gives a picture, an AlcorCut Pro card is shown and the TV stays connected.
 */
object CastFeed {
    private const val PORT = 8766
    private const val W = 1280
    private const val H = 720
    private const val FPS = 24
    private const val SEG_FRAMES = FPS          // one-second segments
    private const val KEEP = 8
    private const val RATE = 48000

    /** Where the sound comes from: the microphone (Capture) or what the app plays ([pushPcm], video editor). */
    enum class Sound { MIC, APP }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var server: ServerSocket? = null
    private val segments = ArrayDeque<Pair<Int, ByteArray>>()
    private val durations = HashMap<Int, Double>()
    private var segStartPts = -1L
    private var seq = 0
    @Volatile var running = false
        private set
    /** The app colour (gear → App colour), for the AlcorCut Pro card on the TV. */
    @Volatile var accent: Int = 0xFFE8B04A.toInt()
    @Volatile private var sound = Sound.MIC
    @Volatile private var t0 = 0L
    private var ts = TsWriter()
    /** Sound from each player of the app (video, music, extra tracks), mixed for the TV. */
    private class Ring {
        val buf = ShortArray(RATE * 2)   // 0.5 s of stereo
        var r = 0; var n = 0
        @Synchronized fun write(x: ShortArray) {
            for (v in x) { if (n == buf.size) { r = (r + 1) % buf.size; n-- }; buf[(r + n) % buf.size] = v; n++ }
        }
        @Synchronized fun addTo(mix: IntArray) {
            val k = minOf(n, mix.size)
            for (i in 0 until k) { mix[i] += buf[r].toInt(); r = (r + 1) % buf.size }
            n -= k
        }
    }
    private val rings = java.util.concurrent.ConcurrentHashMap<Any, Ring>()

    /** The stream's address for the TV, or null when the phone isn't on a network. */
    fun url(): String? = RemoteMonitor.localIpAddress()?.let { "http://$it:$PORT/live.m3u8" }

    /** Changes where the sound comes from (the stream keeps running). */
    fun setSound(s: Sound) { sound = s; rings.clear() }

    /** Starts serving [source] (called on the main thread about 24 times a second; null = the idle card). */
    fun start(source: suspend () -> Bitmap?) {
        if (running) return
        running = true
        synchronized(segments) { segments.clear(); durations.clear(); seq = 0 }
        ts = TsWriter()
        t0 = System.nanoTime()
        val ss = try { ServerSocket(PORT) } catch (_: Throwable) { running = false; return }
        server = ss
        thread(name = "CastFeed-http", isDaemon = true) {
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (_: Throwable) { break }
                thread(isDaemon = true) { serve(s) }
            }
        }
        job = scope.launch { encode(source) }
        thread(name = "CastFeed-audio", isDaemon = true) { audio() }
    }

    fun stop() {
        running = false
        job?.cancel(); job = null
        try { server?.close() } catch (_: Throwable) {}
        server = null
        rings.clear()
    }

    /** Sound one of the app's players is playing ([from] tells the players apart; 16-bit PCM, interleaved). */
    fun pushPcm(from: Any, pcm: ShortArray, rate: Int, channels: Int) {
        if (!running || sound != Sound.APP || pcm.isEmpty()) return
        rings.getOrPut(from) { Ring() }.write(toStereo48k(pcm, rate, channels))
    }

    private fun toStereo48k(pcm: ShortArray, rate: Int, channels: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = pcm.size / ch
        val outFrames = if (rate == RATE) frames else (frames.toLong() * RATE / rate.coerceAtLeast(1)).toInt()
        val out = ShortArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val src = if (rate == RATE) i else ((i.toLong() * rate / RATE).toInt()).coerceAtMost(frames - 1)
            val l = pcm[src * ch]; val r = if (ch >= 2) pcm[src * ch + 1] else l
            out[2 * i] = l; out[2 * i + 1] = r
        }
        return out
    }

    // ---- picture

    private suspend fun encode(source: suspend () -> Bitmap?) {
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
        var config: ByteArray? = null
        var frames = 0
        val info = MediaCodec.BufferInfo()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; textSize = 54f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
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
                    } else { card.color = accent; c.drawText("AlcorCut Pro", W / 2f, H / 2f + 18f, card) }
                    surface.unlockCanvasAndPost(c)
                } catch (_: Throwable) {}
                b?.recycle()
                while (true) {
                    val i = codec.dequeueOutputBuffer(info, 0)
                    if (i < 0) break
                    val buf = codec.getOutputBuffer(i)
                    if (buf != null && info.size > 0) {
                        val data = ByteArray(info.size); buf.position(info.offset); buf.get(data)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) config = data
                        else {
                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                            val pts = now90()
                            if (key && frames >= SEG_FRAMES) { finishSegment(pts); frames = 0 }
                            if (key || frames > 0) {
                                if (frames == 0) segStartPts = pts
                                ts.video(if (key && config != null) config!! + data else data, pts, key)
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

    private fun now90() = (System.nanoTime() - t0) / 1_000_000L * 90 + 90_000   // starts at 1 s

    private fun finishSegment(endPts: Long) {
        val bytes = ts.take()
        if (bytes.isEmpty()) return
        synchronized(segments) {
            durations[seq] = ((endPts - segStartPts) / 90000.0).coerceIn(0.2, 10.0)
            segments.addLast(seq++ to bytes)
            while (segments.size > KEEP) durations.remove(segments.removeFirst().first)
        }
    }

    /** Segments ready on the phone (the TV is told to play once there are a few). */
    fun ready(): Int = synchronized(segments) { segments.size }

    // ---- sound (AAC, 48 kHz stereo)

    @SuppressLint("MissingPermission")
    private fun audio() {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = try { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply { configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start() } }
            catch (_: Throwable) { return }
        var mic: AudioRecord? = null
        val chunk = ShortArray(2048)   // 1024 stereo frames
        var nextMix = System.nanoTime()
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                // Next 1024 frames: the microphone, what the app plays, or silence.
                val pcm: ShortArray = when (sound) {
                    Sound.MIC -> {
                        if (mic == null) mic = try {
                            AudioRecord(MediaRecorder.AudioSource.CAMCORDER, RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                                maxOf(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT), 16384))
                                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }?.also { it.startRecording() }
                        } catch (_: Throwable) { null }
                        val m = mic
                        if (m != null) { val n = m.read(chunk, 0, chunk.size); if (n > 0) chunk.copyOf(n) else ShortArray(0) }
                        else { Thread.sleep(21); ShortArray(chunk.size) }
                    }
                    Sound.APP -> {
                        mic?.let { try { it.stop(); it.release() } catch (_: Throwable) {} }; mic = null
                        // Every 1024 frames (21.3 ms): the players' sound mixed together (silence when nothing plays).
                        val due = nextMix - System.nanoTime()
                        if (due > 0) Thread.sleep(due / 1_000_000, (due % 1_000_000).toInt())
                        nextMix = maxOf(nextMix, System.nanoTime() - 100_000_000L) + 1024L * 1_000_000_000L / RATE
                        val mix = IntArray(chunk.size)
                        rings.values.forEach { it.addTo(mix) }
                        ShortArray(mix.size) { mix[it].coerceIn(-32768, 32767).toShort() }
                    }
                }
                if (pcm.isEmpty()) continue
                var off = 0
                while (off < pcm.size && running) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii < 0) break
                    val ib = codec.getInputBuffer(ii) ?: break
                    ib.clear()
                    val n = minOf(pcm.size - off, ib.remaining() / 2)
                    for (k in 0 until n) ib.putShort(pcm[off + k])
                    off += n
                    codec.queueInputBuffer(ii, 0, n * 2, (System.nanoTime() - t0) / 1000, 0)
                }
                while (true) {
                    val oi = codec.dequeueOutputBuffer(info, 0)
                    if (oi < 0) break
                    val ob = codec.getOutputBuffer(oi)
                    if (ob != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val data = ByteArray(info.size); ob.position(info.offset); ob.get(data)
                        ts.audio(adts(data.size) + data, now90())
                    }
                    codec.releaseOutputBuffer(oi, false)
                }
            }
        } finally {
            mic?.let { try { it.stop(); it.release() } catch (_: Throwable) {} }
            try { codec.stop() } catch (_: Throwable) {}
            try { codec.release() } catch (_: Throwable) {}
        }
    }

    /** ADTS header for one AAC-LC frame (48 kHz, stereo). */
    private fun adts(payload: Int): ByteArray {
        val len = payload + 7
        val profile = 1; val freqIdx = 3; val chan = 2
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),
            ((profile shl 6) or (freqIdx shl 2) or (chan shr 2)).toByte(),
            (((chan and 3) shl 6) or (len shr 11)).toByte(),
            ((len shr 3) and 0xFF).toByte(),
            (((len and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    // ---- HTTP (playlist + segments), with the CORS header a Chromecast needs

    private fun serve(s: Socket) = s.use { sock ->
        try {
            sock.soTimeout = 10_000
            val reader = sock.getInputStream().bufferedReader()
            val line = reader.readLine() ?: return
            // Read the whole request first: closing with unread bytes would reset the connection on the TV's side.
            while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
            val path = line.split(' ').getOrNull(1) ?: "/"
            val out = sock.getOutputStream()
            fun reply(code: String, type: String, body: ByteArray) {
                out.write(("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(body); out.flush()
            }
            val method = line.substringBefore(' ')
            when {
                method == "OPTIONS" -> {
                    out.write(("HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray()); out.flush()
                }
                path.startsWith("/live.m3u8") -> {
                    val (list, durs) = synchronized(segments) { segments.toList() to HashMap(durations) }
                    val first = list.firstOrNull()?.first ?: 0
                    val target = kotlin.math.ceil(durs.values.maxOrNull() ?: 1.0).toInt().coerceAtLeast(1) + 1
                    val sb = StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:$target\n#EXT-X-MEDIA-SEQUENCE:$first\n")
                    list.forEach { (n, _) -> sb.append("#EXTINF:%.3f,\nseg$n.ts\n".format(java.util.Locale.US, durs[n] ?: 1.0)) }
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

    /** MPEG transport stream: one H.264 and one AAC track (PAT, PMT and PES packets). Thread-safe. */
    private class TsWriter {
        private val out = ByteArrayOutputStream()
        private val cc = IntArray(4)   // PAT, PMT, video, audio
        private var tablesWritten = false

        @Synchronized fun take(): ByteArray { val b = out.toByteArray(); out.reset(); tablesWritten = false; return b }

        @Synchronized fun video(nal: ByteArray, pts: Long, key: Boolean) {
            if (!tablesWritten || key) { psi(0, pat(), 0); psi(PMT_PID, pmt(), 1); tablesWritten = true }
            pes(VID_PID, 0xE0, nal, pts, pcr = key, counter = 2)
        }

        @Synchronized fun audio(adtsFrame: ByteArray, pts: Long) {
            if (!tablesWritten) return   // a segment starts with a picture
            pes(AUD_PID, 0xC0, adtsFrame, pts, pcr = false, counter = 3)
        }

        private fun pes(pid: Int, streamId: Int, payloadIn: ByteArray, pts: Long, pcr: Boolean, counter: Int) {
            val pes = ByteArrayOutputStream()
            val len = if (streamId == 0xE0) 0 else (payloadIn.size + 8).coerceAtMost(0xFFFF)
            pes.write(byteArrayOf(0, 0, 1, streamId.toByte(), (len shr 8).toByte(), len.toByte(), 0x80.toByte(), 0x80.toByte(), 5))
            pes.write(ptsBytes(pts))
            pes.write(payloadIn)
            val data = pes.toByteArray()
            var pos = 0; var first = true
            while (pos < data.size) {
                val pkt = ByteArray(188) { 0xFF.toByte() }
                pkt[0] = 0x47
                pkt[1] = ((if (first) 0x40 else 0) or (pid shr 8)).toByte()
                pkt[2] = (pid and 0xFF).toByte()
                val remaining = data.size - pos
                val needPcr = first && pcr
                val hdr: Int
                if (needPcr || remaining < 184) {
                    val payload = minOf(remaining, if (needPcr) 176 else 182)
                    val afLen = 183 - payload
                    pkt[3] = (0x30 or (cc[counter] and 0x0F)).toByte()
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
                } else { pkt[3] = (0x10 or (cc[counter] and 0x0F)).toByte(); hdr = 4 }
                val n = 188 - hdr
                System.arraycopy(data, pos, pkt, hdr, n)
                pos += n; first = false; cc[counter]++
                out.write(pkt)
            }
        }

        private fun psi(pid: Int, section: ByteArray, counter: Int) {
            val pkt = ByteArray(188) { 0xFF.toByte() }
            pkt[0] = 0x47; pkt[1] = (0x40 or (pid shr 8)).toByte(); pkt[2] = (pid and 0xFF).toByte()
            pkt[3] = (0x10 or (cc[counter]++ and 0x0F)).toByte(); pkt[4] = 0
            System.arraycopy(section, 0, pkt, 5, section.size)
            out.write(pkt)
        }

        private fun pat(): ByteArray = withCrc(byteArrayOf(0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0x00, 0x01, (0xE0 or (PMT_PID shr 8)).toByte(), (PMT_PID and 0xFF).toByte()))

        private fun pmt(): ByteArray = withCrc(byteArrayOf(0x02, 0xB0.toByte(), 0x17, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            (0xE0 or (VID_PID shr 8)).toByte(), (VID_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00,
            0x1B, (0xE0 or (VID_PID shr 8)).toByte(), (VID_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00,
            0x0F, (0xE0 or (AUD_PID shr 8)).toByte(), (AUD_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00))

        private fun withCrc(b: ByteArray): ByteArray {
            var crc = -1
            for (x in b) {
                crc = crc xor ((x.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return b + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
        }

        private fun ptsBytes(pts: Long): ByteArray = byteArrayOf(
            (0x20 or (((pts shr 30) and 0x07).toInt() shl 1) or 1).toByte(),
            (pts shr 22).toByte(), ((((pts shr 15) and 0x7F).toInt() shl 1) or 1).toByte(),
            (pts shr 7).toByte(), ((((pts) and 0x7F).toInt() shl 1) or 1).toByte(),
        )

        companion object { const val PMT_PID = 0x1000; const val VID_PID = 0x100; const val AUD_PID = 0x101 }
    }
}
