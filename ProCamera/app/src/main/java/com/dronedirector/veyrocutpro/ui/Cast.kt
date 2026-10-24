package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.dronedirector.veyrocutpro.camera.CastFeed
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider

/** Chromecast set-up: the standard receiver on the TV plays the live stream from [CastFeed]. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/**
 * Casting to a TV: Capture's live picture or the video editor's viewer. Uses Google's free Cast
 * library (Google Play services) to talk to the Chromecast; the picture itself comes from this phone.
 */
object CastLink {
    var device by mutableStateOf<String?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set
    /** Screens that can give a picture, newest last (Capture opened over the editor, then the editor again). */
    private class Src(val frame: suspend () -> Bitmap?, val sound: CastFeed.Sound)
    private val sources = ArrayList<Src>()
    private var listening = false

    /** What the TV shows now: the newest screen's picture, or the idle card when none is open. */
    private suspend fun frame(): Bitmap? = sources.lastOrNull()?.frame?.invoke()

    private fun cast(c: Context): CastContext? = try { CastContext.getSharedInstance(c.applicationContext) } catch (_: Throwable) { null }

    /** Whether casting can work on this phone (Google Play services present). */
    fun available(c: Context) = cast(c) != null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) { device = s.castDevice?.friendlyName ?: "TV"; connecting = false; play(s) }
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { device = s.castDevice?.friendlyName ?: "TV"; connecting = false; play(s) }
        override fun onSessionEnded(s: CastSession, error: Int) { device = null; connecting = false; CastFeed.stop() }
        override fun onSessionStartFailed(s: CastSession, error: Int) { device = null; connecting = false; CastFeed.stop() }
        override fun onSessionStarting(s: CastSession) { connecting = true }
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) { connecting = false }
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    private fun listen(c: Context) {
        if (listening) return
        val cc = cast(c) ?: return
        cc.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        listening = true
    }

    /** A screen with a cast button opened: its picture (and sound) goes to the TV. */
    fun use(c: Context, src: suspend () -> Bitmap?, sound: CastFeed.Sound) {
        sources.removeAll { it.frame === src }
        sources.add(Src(src, sound))
        CastFeed.setSound(sound)
        listen(c)
        val s = cast(c)?.sessionManager?.currentCastSession
        if (s != null && s.isConnected) { device = s.castDevice?.friendlyName ?: "TV"; if (!CastFeed.running) play(s) }
    }

    /** The screen went away: the TV stays connected and shows the previous screen, or the AlcorCut Pro card. */
    fun release(c: Context, src: suspend () -> Bitmap?) {
        sources.removeAll { it.frame === src }
        sources.lastOrNull()?.let { CastFeed.setSound(it.sound) }
    }

    private fun play(s: CastSession) {
        CastFeed.start { frame() }
        val url = CastFeed.url() ?: return
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, "AlcorCut Pro") }
        val info = MediaInfo.Builder(url)
            .setContentType("application/x-mpegURL")
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setHlsSegmentFormat(HlsSegmentFormat.TS)
            .setMetadata(meta)
            .build()
        try { s.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build()) } catch (_: Throwable) {}
    }

    fun stop(c: Context) {
        try { cast(c)?.sessionManager?.endCurrentSession(true) } catch (_: Throwable) {}
        CastFeed.stop()
        device = null; connecting = false
    }

    fun selector(): MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build()
}

/** Where the cast button sits: 0 = on the picture (corner), 1 = below the picture. Per screen ("cap" / "ve"). */
object CastPlace {
    private val cache = HashMap<String, Int>()
    var version by mutableIntStateOf(0)
        private set
    fun get(c: Context, screen: String): Int { version; return cache.getOrPut(screen) { c.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("cast_place_$screen", 0) } }
    fun set(c: Context, screen: String, v: Int) {
        cache[screen] = v; version++
        c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putInt("cast_place_$screen", v).apply()
    }
}

/** Gear setting: where the cast button goes. */
@Composable
fun CastPlaceSetting(screen: String) {
    val ctx = LocalContext.current
    Text("Cast button (Chromecast)", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    com.dronedirector.veyrocutpro.editor.Chips(listOf("On the picture (corner)", "Below the picture"), CastPlace.get(ctx, screen)) { CastPlace.set(ctx, screen, it) }
    Text("Shows the live picture on a TV with a Chromecast or Google TV on the same Wi-Fi (a few seconds behind). Hide the button in the layout menu.", color = Pro.Dim, fontSize = 11.sp)
}

/** The cast button: pick a TV, or stop casting. [source] gives the current picture (called on the main thread). */
@Composable
fun CastButton(source: suspend () -> Bitmap?, modifier: Modifier = Modifier, small: Boolean = false, sound: CastFeed.Sound = CastFeed.Sound.MIC) {
    val ctx = LocalContext.current
    val src by rememberUpdatedState(source)
    val stable: suspend () -> Bitmap? = remember { suspend { src() } }
    DisposableEffect(Unit) {
        CastLink.use(ctx, stable, sound)
        onDispose { CastLink.release(ctx, stable) }
    }
    var picking by remember { mutableStateOf(false) }
    val on = CastLink.device != null
    Box(
        modifier.size(if (small) 34.dp else 40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .clickable { picking = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (on) Icons.Filled.CastConnected else Icons.Filled.Cast, contentDescription = "Cast to TV",
            tint = if (on || CastLink.connecting) Pro.Amber else Color.White, modifier = Modifier.size(if (small) 18.dp else 22.dp))
    }
    if (picking) CastPicker(onDismiss = { picking = false })
}

@Composable
private fun CastPicker(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val routes = remember { mutableStateListOf<MediaRouter.RouteInfo>() }
    val ok = remember { CastLink.available(ctx) }
    DisposableEffect(Unit) {
        val router = MediaRouter.getInstance(ctx)
        val sel = CastLink.selector()
        fun refresh() { routes.clear(); routes.addAll(router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(sel) }) }
        val cb = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        if (ok) { router.addCallback(sel, cb, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN); refresh() }
        onDispose { if (ok) router.removeCallback(cb) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text(CastLink.device?.let { "Casting to $it" } ?: "Cast to a TV", color = Color.White) },
        text = {
            Column {
                when {
                    !ok -> Text("Casting needs Google Play services, which this device doesn't have.", color = Pro.Dim)
                    CastLink.device != null -> Text("The live picture is on the TV (a few seconds behind).", color = Pro.Dim)
                    routes.isEmpty() -> Text("Looking for a Chromecast or Google TV on this Wi-Fi…", color = Pro.Dim)
                    else -> routes.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f))
                                .clickable { MediaRouter.getInstance(ctx).selectRoute(r); onDismiss() }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Cast, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(20.dp))
                            Text(r.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (CastLink.device != null) androidx.compose.material3.TextButton(onClick = { CastLink.stop(ctx); onDismiss() }) { Text("STOP CASTING", color = Pro.Amber) }
            else androidx.compose.material3.TextButton(onClick = onDismiss) { Text("CANCEL", color = Pro.Amber) }
        },
    )
}
