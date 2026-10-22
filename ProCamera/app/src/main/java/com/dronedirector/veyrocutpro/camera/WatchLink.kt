package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The phone side of the watch remote (VeyroCut Pro on a Wear OS watch, e.g. Galaxy Watch 4+).
 * While the watch app is open it says "hello" every few seconds; the phone then sends the camera's
 * state and a small live picture, and carries out the watch's commands.
 */
class WatchLink(
    private val context: Context,
    private val scope: CoroutineScope,
    private val controller: ProCameraController,
    private val preview: () -> PreviewView?,
) : MessageClient.OnMessageReceivedListener {
    private var watchNode: String? = null
    @Volatile private var lastHello = 0L
    private var loop: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        try { Wearable.getMessageClient(context).addListener(this) } catch (_: Throwable) {}
    }

    fun stop() {
        started = false
        loop?.cancel(); loop = null
        try { Wearable.getMessageClient(context).removeListener(this) } catch (_: Throwable) {}
    }

    override fun onMessageReceived(e: MessageEvent) {
        if (e.path != "/vc/cmd") return
        val cmd = String(e.data)
        watchNode = e.sourceNodeId
        scope.launch {
            when (cmd) {
                "hello" -> { lastHello = System.currentTimeMillis(); ensureLoop() }
                "bye" -> lastHello = 0L
                "shutter" -> controller.shutter()
                "mode" -> {
                    val s = controller.state.value
                    if (!s.recording) controller.setMode(
                        when (s.mode) {
                            CaptureMode.PRO_PHOTO -> CaptureMode.PRO_VIDEO
                            CaptureMode.PRO_VIDEO -> CaptureMode.PRO_PHOTO
                            else -> if (s.isVideo) CaptureMode.PHOTO else CaptureMode.VIDEO
                        }
                    )
                }
                "zoom+" -> controller.setZoom(controller.state.value.zoom * 1.25f)
                "zoom-" -> controller.setZoom(controller.state.value.zoom / 1.25f)
            }
            sendState()
        }
    }

    private fun ensureLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var tick = 0
            while (isActive && System.currentTimeMillis() - lastHello < 7000) {
                if (tick % 2 == 0) sendState()
                sendPreview()
                tick++
                delay(500)
            }
        }
    }

    private fun sendState() {
        val node = watchNode ?: return
        val s = controller.state.value
        val msg = listOf(
            if (s.recording) "1" else "0", s.recordSeconds.toString(), s.mode.label,
            if (s.isVideo) "1" else "0", if (s.ready) "1" else "0", Stops.zoomLabel(s.zoom),
            if (s.capturing || s.processing || s.countdown > 0) "1" else "0",
        ).joinToString("|")
        try { Wearable.getMessageClient(context).sendMessage(node, "/vc/state", msg.toByteArray()) } catch (_: Throwable) {}
    }

    /** A small JPEG of the viewfinder (about 240 px). */
    private suspend fun sendPreview() {
        val node = watchNode ?: return
        val bmp = try { preview()?.bitmap } catch (_: Throwable) { null } ?: return
        val bytes = withContext(Dispatchers.Default) {
            val k = 240f / maxOf(bmp.width, bmp.height)
            val small = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 60, it) }.toByteArray()
        }
        try { Wearable.getMessageClient(context).sendMessage(node, "/vc/preview", bytes) } catch (_: Throwable) {}
    }
}
