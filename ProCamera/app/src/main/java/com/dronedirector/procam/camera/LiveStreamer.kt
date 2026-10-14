package com.dronedirector.procam.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.QueueAwareBitrateAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where a live stream can go. Users paste the stream key from each platform's own dashboard. */
enum class LivePlatform(
    val label: String,
    val defaultServer: String,
    val keyPage: String,
    val vertical: Boolean,
    val help: String,
) {
    YOUTUBE(
        "YouTube", "rtmp://a.rtmp.youtube.com/live2", "https://studio.youtube.com/",
        false, "YouTube Studio → Create → Go live → Stream. Copy the Stream key.",
    ),
    TIKTOK(
        "TikTok", "", "https://livecenter.tiktok.com/producer",
        true, "TikTok LIVE Producer shows a Server URL and Stream key. Not every TikTok account has access to stream keys.",
    ),
    TWITCH(
        "Twitch", "rtmp://live.twitch.tv/app", "https://dashboard.twitch.tv/settings/stream",
        false, "Twitch Creator Dashboard → Settings → Stream. Copy the Primary Stream key.",
    ),
    FACEBOOK(
        "Facebook", "rtmps://live-api-s.facebook.com:443/rtmp", "https://www.facebook.com/live/producer",
        false, "Facebook Live Producer → Streaming software. Copy the Stream key.",
    ),
    CUSTOM(
        "Custom", "rtmp://", "",
        false, "Any RTMP, RTMPS or SRT server (your own server, Restream, Kick…). Put the full URL in Server.",
    ),
}

enum class LiveQuality(val label: String, val width: Int, val height: Int, val bitrate: Int) {
    SD("480p", 854, 480, 1_200_000),
    HD("720p", 1280, 720, 2_500_000),
    FHD("1080p", 1920, 1080, 4_500_000),
}

enum class LiveStatus { IDLE, CONNECTING, LIVE, RECONNECTING }

data class LiveState(
    val platform: LivePlatform = LivePlatform.YOUTUBE,
    val server: String = LivePlatform.YOUTUBE.defaultServer,
    val key: String = "",
    val vertical: Boolean = false,
    val quality: LiveQuality = LiveQuality.HD,
    val status: LiveStatus = LiveStatus.IDLE,
    val seconds: Long = 0,
    val kbps: Int = 0,
    val muted: Boolean = false,
    val torch: Boolean = false,
    val showSetup: Boolean = false,
    val message: String? = null,
    val messageId: Int = 0,
) {
    val isConnected: Boolean get() = status == LiveStatus.LIVE
    val isBusy: Boolean get() = status != LiveStatus.IDLE
    val hasKey: Boolean get() = key.isNotBlank() || platform == LivePlatform.CUSTOM
}

/**
 * Streams the camera and microphone to RTMP / RTMPS / SRT servers using RootEncoder.
 * In LIVE mode ProCam's own camera pipeline is switched off and this owns the camera.
 */
class LiveStreamer(private val context: Context) : ConnectChecker {

    private val prefs = context.getSharedPreferences("procam_live", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(
        loadPlatform(LivePlatform.entries.getOrElse(prefs.getInt("platform", 0)) { LivePlatform.YOUTUBE }, LiveState())
    )
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var stream: GenericStream? = null
    private var surfaceView: SurfaceView? = null
    private var startedAt = 0L
    private val ticker = object : Runnable {
        override fun run() {
            if (_state.value.isConnected) {
                _state.update { it.copy(seconds = (System.currentTimeMillis() - startedAt) / 1000) }
            }
            if (_state.value.isBusy) main.postDelayed(this, 1000)
        }
    }
    private var bitrateAdapter: QueueAwareBitrateAdapter? = null

    private fun toast(msg: String) = _state.update { it.copy(message = msg, messageId = it.messageId + 1) }

    // ------------------------------------------------------------- settings

    private fun loadPlatform(p: LivePlatform, base: LiveState): LiveState {
        return base.copy(
            platform = p,
            server = prefs.getString("server_${p.name}", null) ?: p.defaultServer,
            key = prefs.getString("key_${p.name}", null) ?: "",
            vertical = prefs.getBoolean("vertical_${p.name}", p.vertical),
            quality = LiveQuality.entries.getOrElse(prefs.getInt("quality", 1)) { LiveQuality.HD },
        )
    }

    fun selectPlatform(p: LivePlatform) {
        if (_state.value.isBusy) return
        prefs.edit().putInt("platform", p.ordinal).apply()
        val old = _state.value
        _state.value = loadPlatform(p, old).copy(showSetup = true)
        if (old.vertical != _state.value.vertical) reconfigure()
    }

    fun saveConnection(server: String, key: String) {
        val p = _state.value.platform
        prefs.edit()
            .putString("server_${p.name}", server.trim())
            .putString("key_${p.name}", key.trim())
            .apply()
        _state.update { it.copy(server = server.trim(), key = key.trim(), showSetup = false) }
        toast("${p.label} connected — tap GO LIVE")
    }

    fun forgetKey() {
        val p = _state.value.platform
        prefs.edit().remove("key_${p.name}").apply()
        _state.update { it.copy(key = "") }
    }

    fun showSetup(show: Boolean) = _state.update { it.copy(showSetup = show) }

    fun setVertical(vertical: Boolean) {
        if (_state.value.isBusy || _state.value.vertical == vertical) return
        prefs.edit().putBoolean("vertical_${_state.value.platform.name}", vertical).apply()
        _state.update { it.copy(vertical = vertical) }
        reconfigure()
    }

    fun setQuality(q: LiveQuality) {
        if (_state.value.isBusy || _state.value.quality == q) return
        prefs.edit().putInt("quality", q.ordinal).apply()
        _state.update { it.copy(quality = q) }
        reconfigure()
    }

    // ------------------------------------------------------------- lifecycle

    /** Called when LIVE mode opens. */
    fun open() {
        if (stream != null) return
        stream = GenericStream(context, this, Camera2Source(context), MicrophoneSource()).apply {
            getGlInterface().autoHandleOrientation = true
            // Fill the preview surface (crop) instead of shrinking the picture to fit.
            getGlInterface().setAspectRatioMode(AspectRatioMode.Fill)
            getStreamClient().setReTries(10)
        }
        prepare()
        // The preview surface may already exist (e.g. app opened straight into LIVE).
        surfaceView?.let { sv -> if (sv.holder.surface.isValid) surfaceCallback.surfaceCreated(sv.holder) }
    }

    /** Called when leaving LIVE mode; frees the camera for ProCam. */
    fun close() {
        val s = stream ?: return
        if (s.isStreaming) s.stopStream()
        if (s.isOnPreview) s.stopPreview()
        s.release()
        stream = null
        surfaceView = null
        _state.update { it.copy(status = LiveStatus.IDLE, seconds = 0, kbps = 0, torch = false) }
    }

    private fun prepare(): Boolean {
        val s = stream ?: return false
        val st = _state.value
        val ok = try {
            s.prepareVideo(st.quality.width, st.quality.height, st.quality.bitrate, 30, 2, if (st.vertical) 90 else 0) &&
                s.prepareAudio(44_100, true, 128_000)
        } catch (_: Throwable) {
            false
        }
        if (!ok) toast("This device can't encode ${st.quality.label} for streaming; try a lower quality")
        return ok
    }

    private fun reconfigure() {
        val s = stream ?: return
        val wasPreview = s.isOnPreview
        s.release()
        prepare()
        val sv = surfaceView
        if (wasPreview && sv != null && sv.holder.surface.isValid) s.startPreview(sv)
    }

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            val s = stream ?: return
            val sv = surfaceView ?: return
            if (!s.isOnPreview) {
                try { s.startPreview(sv) } catch (t: Throwable) { toast("Camera preview failed: ${t.message}") }
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            stream?.getGlInterface()?.setPreviewResolution(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            val s = stream ?: return
            if (s.isOnPreview) s.stopPreview()
        }
    }

    fun attachPreview(view: SurfaceView) {
        surfaceView = view
        view.holder.addCallback(surfaceCallback)
        if (view.holder.surface.isValid) surfaceCallback.surfaceCreated(view.holder)
    }

    fun detachPreview(view: SurfaceView) {
        view.holder.removeCallback(surfaceCallback)
        if (surfaceView === view) surfaceView = null
    }

    // ------------------------------------------------------------- streaming

    fun streamUrl(st: LiveState = _state.value): String {
        val server = st.server.trim().trimEnd('/')
        return if (st.key.isBlank()) server else "$server/${st.key.trim()}"
    }

    fun toggleLive() {
        val st = _state.value
        val s = stream ?: return
        if (st.isBusy) {
            s.stopStream()
            main.removeCallbacks(ticker)
            _state.update { it.copy(status = LiveStatus.IDLE, kbps = 0) }
            toast("Stream ended")
            return
        }
        if (!st.hasKey || st.server.length < 8) {
            _state.update { it.copy(showSetup = true) }
            toast("Add your ${st.platform.label} stream key first")
            return
        }
        bitrateAdapter = QueueAwareBitrateAdapter(st.quality.bitrate + 128_000) { b ->
            stream?.setVideoBitrateOnFly(b - 128_000)
        }
        _state.update { it.copy(status = LiveStatus.CONNECTING, seconds = 0, kbps = 0) }
        try {
            s.startStream(streamUrl(st))
            main.post(ticker)
        } catch (t: Throwable) {
            _state.update { it.copy(status = LiveStatus.IDLE) }
            toast("Couldn't start: ${t.message}")
        }
    }

    fun switchCamera() {
        (stream?.videoSource as? Camera2Source)?.switchCamera()
    }

    fun toggleTorch() {
        val cam = stream?.videoSource as? Camera2Source ?: return
        try {
            if (cam.isLanternEnabled()) cam.disableLantern() else cam.enableLantern()
            _state.update { it.copy(torch = cam.isLanternEnabled()) }
        } catch (_: Throwable) {
            toast("No torch on this camera")
        }
    }

    fun toggleMute() {
        val mic = stream?.audioSource as? MicrophoneSource ?: return
        if (mic.isMuted()) mic.unMute() else mic.mute()
        _state.update { it.copy(muted = mic.isMuted()) }
    }

    // ------------------------------------------------------------- ConnectChecker (background thread)

    override fun onConnectionStarted(url: String) {
        bitrateAdapter?.reset()
    }

    override fun onConnectionSuccess() = main.post {
        startedAt = System.currentTimeMillis()
        _state.update { it.copy(status = LiveStatus.LIVE, seconds = 0) }
        toast("You're live on ${_state.value.platform.label}")
    }.let {}

    override fun onConnectionFailed(reason: String) = main.post {
        val s = stream ?: return@post
        if (s.getStreamClient().reTry(5000, reason, null)) {
            _state.update { it.copy(status = LiveStatus.RECONNECTING) }
        } else {
            s.stopStream()
            main.removeCallbacks(ticker)
            _state.update { it.copy(status = LiveStatus.IDLE, kbps = 0) }
            toast("Connection failed: $reason")
        }
    }.let {}

    override fun onStreamingStats(report: StreamingStatsReport) {
        main.post {
            bitrateAdapter?.onStreamingStats(report)
            _state.update { it.copy(kbps = (report.smoothedBitrate / 1000).toInt()) }
        }
    }

    override fun onDisconnect() = main.post {
        if (_state.value.status == LiveStatus.LIVE) {
            _state.update { it.copy(status = LiveStatus.IDLE, kbps = 0) }
            main.removeCallbacks(ticker)
        }
    }.let {}

    override fun onAuthError() = main.post {
        stream?.stopStream()
        main.removeCallbacks(ticker)
        _state.update { it.copy(status = LiveStatus.IDLE, showSetup = true) }
        toast("The server rejected the stream key — check it and try again")
    }.let {}

    override fun onAuthSuccess() {}
}
