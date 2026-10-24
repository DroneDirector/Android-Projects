package com.dronedirector.veyrocutpro.editor

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.dronedirector.veyrocutpro.camera.Upscaler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Photo exports run here, in a process of their own (":export"). Android gives every process its own
 * memory allowance, so the photo gets all of it — none is taken by the editor, its previews, Capture
 * or the video editor. The process ends after each export, so the next one starts with all memory free.
 */
class PhotoExportProcess : Service() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { m ->
        when (m.what) {
            RUN -> {
                val reply = m.replyTo
                val path = m.data?.getString("req")
                if (reply != null && path != null && !busy) start(path, reply)
            }
            STOP -> { AiDenoiser.stop(); Upscaler.cancelAll(); try { com.dronedirector.veyrocutpro.camera.CloudAi.stop() } catch (_: Throwable) {} }
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    private fun send(to: Messenger, what: Int, a1: Int = 0, a2: Int = 0, data: Bundle? = null) {
        try { to.send(Message.obtain(null, what, a1, a2).also { if (data != null) it.data = data }) } catch (_: Throwable) {}
    }

    private fun start(path: String, reply: Messenger) {
        busy = true
        // Progress of the AI steps, passed on twice a second.
        val tick = object : Runnable {
            override fun run() {
                send(reply, PROGRESS, 1, if (AiDenoiser.running) AiDenoiser.progress else -1)
                send(reply, PROGRESS, 2, if (Upscaler.pending > 0) Upscaler.progress else -1)
                main.postDelayed(this, 500)
            }
        }
        main.post(tick)
        Thread({
            val res = try {
                val req = ProjectStore.gson.fromJson(File(path).readText(), PhotoExportReq::class.java)
                runPhotoExport(applicationContext, req)
            } catch (t: Throwable) {
                PhotoExportRes(error = (t.message ?: t.javaClass.simpleName).take(80))
            } finally { File(path).delete() }
            main.post {
                main.removeCallbacks(tick)
                send(reply, PROGRESS, 1, -1); send(reply, PROGRESS, 2, -1)
                send(reply, DONE, data = Bundle().apply { putString("res", ProjectStore.gson.toJson(res)) })
                // End this process: the next export starts with all memory free.
                main.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 1500)
            }
        }, "photo-export").apply { priority = Thread.NORM_PRIORITY }.start()
    }

    companion object {
        const val RUN = 1
        const val STOP = 2
        const val PROGRESS = 3
        const val DONE = 4
    }
}

/** The editor's side: hands an export to [PhotoExportProcess] and waits for it. */
internal object PhotoExportRemote {
    suspend fun run(context: Context, req: PhotoExportReq): PhotoExportRes {
        val app = context.applicationContext
        val file = withContext(Dispatchers.IO) {
            File(app.cacheDir, "photo_export_${System.currentTimeMillis()}.json").apply { writeText(ProjectStore.gson.toJson(req)) }
        }
        val res = suspendCancellableCoroutine<PhotoExportRes?> { cont ->
            var done = false
            var service: Messenger? = null
            lateinit var conn: ServiceConnection
            fun finish(r: PhotoExportRes?) {
                if (done) return
                done = true
                AiDenoiser.onStop = null; Upscaler.onStop = null
                AiDenoiser.showRemote(false, 0); Upscaler.showRemote(false, 0)
                try { app.unbindService(conn) } catch (_: Throwable) {}
                if (cont.isActive) cont.resume(r)
            }
            val replies = Messenger(Handler(Looper.getMainLooper()) { m ->
                when (m.what) {
                    PhotoExportProcess.PROGRESS -> if (!done) {
                        if (m.arg1 == 1) AiDenoiser.showRemote(m.arg2 >= 0, m.arg2.coerceAtLeast(0))
                        else Upscaler.showRemote(m.arg2 >= 0, m.arg2.coerceAtLeast(0))
                    }
                    PhotoExportProcess.DONE -> finish(
                        try { ProjectStore.gson.fromJson(m.data.getString("res"), PhotoExportRes::class.java) } catch (_: Throwable) { null }
                            ?: PhotoExportRes(error = "the export didn't report back"),
                    )
                }
                true
            })
            val stop = { service?.let { try { it.send(Message.obtain(null, PhotoExportProcess.STOP)) } catch (_: Throwable) {} }; Unit }
            AiDenoiser.onStop = stop; Upscaler.onStop = stop
            conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) { finish(null); return }
                    try {
                        binder.linkToDeath({ Handler(Looper.getMainLooper()).post { finish(PhotoExportRes(error = "the export stopped unexpectedly — please try again")) } }, 0)
                        service = Messenger(binder)
                        service!!.send(Message.obtain(null, PhotoExportProcess.RUN).apply {
                            data = Bundle().apply { putString("req", file.path) }; replyTo = replies
                        })
                    } catch (_: Throwable) { finish(null) }
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    finish(PhotoExportRes(error = "the export stopped unexpectedly — please try again"))
                }
            }
            val bound = try {
                app.bindService(Intent(app, PhotoExportProcess::class.java), conn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            } catch (_: Throwable) { false }
            if (!bound) finish(null)
            cont.invokeOnCancellation { stop(); finish(null) }
        }
        // The export process couldn't be started: export here instead.
        return res ?: withContext(Dispatchers.IO) {
            try { runPhotoExport(app, ProjectStore.gson.fromJson(file.readText(), PhotoExportReq::class.java)) }
            catch (t: Throwable) { PhotoExportRes(error = (t.message ?: t.javaClass.simpleName).take(80)) }
            finally { file.delete() }
        }
    }
}
