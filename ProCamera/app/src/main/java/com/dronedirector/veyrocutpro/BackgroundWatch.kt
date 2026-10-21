package com.dronedirector.veyrocutpro

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Puts the app's own background jobs (AI noise reduction, AI upscale, AI cull, Cloud AI, first-launch
 * downloads) into the background-tasks notification by watching their progress state. Started once per process.
 */
object BackgroundWatch {
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** [state] returns null when the job isn't running, else (title, percent or -1). */
    private fun watch(app: Context, key: String, state: () -> Pair<String, Int>?) {
        scope.launch {
            snapshotFlow { try { state() } catch (_: Throwable) { null } }.distinctUntilChanged().collect { s ->
                if (s == null) ExportService.end(app, key)
                else ExportService.update(app, key, s.second, s.first)
            }
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        watch(app, "ai-denoise") {
            if (com.dronedirector.veyrocutpro.editor.AiDenoiser.running) "AI noise reduction" to com.dronedirector.veyrocutpro.editor.AiDenoiser.progress else null
        }
        watch(app, "ai-upscale") {
            val u = com.dronedirector.veyrocutpro.camera.Upscaler
            if (u.pending > 0) ("AI upscale" + (if (u.pending > 1) " · ${u.pending} photos" else "")) to u.progress else null
        }
        watch(app, "ai-cull") {
            com.dronedirector.veyrocutpro.editor.Cull.progress?.let { (d, t) -> "AI cull · $d of $t" to (if (t > 0) d * 100 / t else -1) }
        }
        watch(app, "cloud-ai") {
            com.dronedirector.veyrocutpro.camera.CloudAi.stage?.let { "Cloud AI · $it" to com.dronedirector.veyrocutpro.camera.CloudAi.progress }
        }
        watch(app, "app-data") {
            val d = com.dronedirector.veyrocutpro.editor.AppData
            if (d.running) (if (d.waitingForWifi) "Downloads waiting for Wi-Fi" else "Downloading AI models & soundtracks") to (if (d.waitingForWifi) -1 else d.percent()) else null
        }
    }
}
