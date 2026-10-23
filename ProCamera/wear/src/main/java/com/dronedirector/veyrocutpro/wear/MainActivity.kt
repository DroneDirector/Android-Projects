package com.dronedirector.veyrocutpro.wear

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * VelaCut Pro watch remote: shows the phone camera's picture, records / takes photos, switches
 * between photo and video, and zooms. Talks to the VelaCut Pro phone app over the watch connection.
 */
class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {
    private class Phone(val recording: Boolean, val seconds: Long, val mode: String, val isVideo: Boolean, val ready: Boolean, val zoom: String, val busy: Boolean)

    private var phone by mutableStateOf<Phone?>(null)
    private var preview by mutableStateOf<ImageBitmap?>(null)
    private var lastHeard by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MaterialTheme { Remote() } }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        Wearable.getMessageClient(this).removeListener(this)
        send("bye")
        super.onPause()
    }

    override fun onMessageReceived(e: MessageEvent) {
        when (e.path) {
            "/vc/state" -> {
                val p = String(e.data).split('|')
                if (p.size >= 7) phone = Phone(p[0] == "1", p[1].toLongOrNull() ?: 0, p[2], p[3] == "1", p[4] == "1", p[5], p[6] == "1")
                lastHeard = System.currentTimeMillis()
            }
            "/vc/preview" -> {
                BitmapFactory.decodeByteArray(e.data, 0, e.data.size)?.let { preview = it.asImageBitmap() }
                lastHeard = System.currentTimeMillis()
            }
        }
    }

    /** Sends a command to every connected phone. */
    private fun send(cmd: String) {
        val ctx = applicationContext
        Wearable.getNodeClient(ctx).connectedNodes.addOnSuccessListener { nodes ->
            for (n in nodes) Wearable.getMessageClient(ctx).sendMessage(n.id, "/vc/cmd", cmd.toByteArray())
        }
    }

    @Composable
    private fun Remote() {
        // Tell the phone we're watching (repeated, so it keeps sending the picture).
        LaunchedEffect(Unit) {
            while (true) {
                try {
                    val nodes = Wearable.getNodeClient(applicationContext).connectedNodes.await()
                    for (n in nodes) Wearable.getMessageClient(applicationContext).sendMessage(n.id, "/vc/cmd", "hello".toByteArray()).await()
                } catch (_: Throwable) {}
                delay(2500)
            }
        }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
        val p = phone
        val connected = p != null && now - lastHeard < 6000
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (connected) preview?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.75f) }
            if (!connected || p == null) {
                Text(
                    "Open VelaCut Pro Capture on your phone",
                    color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                return@Box
            }
            Column(Modifier.fillMaxSize().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                // Top: mode, or the recording time.
                Text(
                    if (p.recording) "● %02d:%02d".format(p.seconds / 60, p.seconds % 60) else p.mode,
                    color = if (p.recording) Color(0xFFFF4D4D) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                // Middle: shutter / record.
                Box(
                    Modifier.size(64.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = p.ready && !p.busy) { send("shutter") },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        p.isVideo && p.recording -> Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF4D4D)))
                        p.isVideo -> Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
                        else -> Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White))
                    }
                }
                // Bottom: zoom out, photo / video, zoom in.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("−") { send("zoom-") }
                    Pill(if (p.isVideo) "PHOTO" else "VIDEO", enabled = !p.recording) { send("mode") }
                    Pill("+") { send("zoom+") }
                }
                Text(p.zoom, color = Color.White, fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun Pill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
        Text(
            text, color = if (enabled) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC2A2F3A)).clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
