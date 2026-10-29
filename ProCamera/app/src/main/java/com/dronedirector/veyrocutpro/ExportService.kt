package com.dronedirector.veyrocutpro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Background work (exports, proxies, AI tasks, downloads…): a foreground service with one notification in the
 * phone's pull-down shade that lists everything running, with progress (and a wake lock), so the app keeps
 * working in other apps or with the screen off. Each task is added with [begin], updated with [update] and
 * removed with [end]; when the last one ends the service stops and a "finished" note can be posted.
 */
class ExportService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (synchronized(tasks) { tasks.isEmpty() }) { stopSelfNow(); return START_NOT_STICKY }
        }
        val n = buildNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= 35) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            else if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, n)
        } catch (_: Throwable) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlcorCutPro:background").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        running = true
        return START_NOT_STICKY
    }

    private fun stopSelfNow() {
        try { wake?.release() } catch (_: Throwable) {}
        wake = null
        running = false
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        try { wake?.release() } catch (_: Throwable) {}
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "export"
        private const val ID = 4711
        private const val DONE_ID = 4712
        private const val ACTION_STOP = "stop"
        /** The key used by [start] / [progress] / [finish] (video exports). */
        private const val EXPORT = "export"
        @Volatile private var running = false

        /** A task shown in the notification; [pct] < 0 = no percentage yet (moving bar). */
        private class Task(var title: String, var pct: Int)
        private val tasks = LinkedHashMap<String, Task>()
        private var lastShown = ""

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                val ch = nm.getNotificationChannel(CHANNEL)
                if (ch == null)
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background tasks", NotificationManager.IMPORTANCE_LOW).apply { description = "Exports, proxies, AI tasks and downloads running in the background" })
                else if (ch.name != "Background tasks") { ch.name = "Background tasks"; nm.createNotificationChannel(ch) }
            }
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun builder(context: Context): Notification.Builder =
            if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)

        private fun line(t: Task) = if (t.pct >= 0) "${t.title} — ${t.pct}%" else t.title

        private fun buildNotification(context: Context): Notification {
            channel(context)
            val list = synchronized(tasks) { tasks.values.map { Task(it.title, it.pct) } }
            val b = builder(context)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(openApp(context))
            when {
                list.isEmpty() -> b.setContentTitle("AlcorCut Pro").setContentText("Working in the background…").setProgress(0, 0, true)
                list.size == 1 -> {
                    val t = list[0]
                    b.setContentTitle(t.title).setContentText(if (t.pct >= 0) "${t.pct}%" else "Working…")
                        .setProgress(100, t.pct.coerceIn(0, 100), t.pct < 0)
                }
                else -> {
                    val style = Notification.InboxStyle().setBigContentTitle("${list.size} background tasks")
                    list.forEach { style.addLine(line(it)) }
                    // The bar shows the overall progress of the tasks that have a percentage.
                    val known = list.filter { it.pct >= 0 }
                    val avg = if (known.isEmpty()) 0 else known.sumOf { it.pct } / known.size
                    b.setContentTitle("${list.size} background tasks").setContentText(list.joinToString(" · ") { it.title })
                        .setStyle(style).setProgress(100, avg.coerceIn(0, 100), known.isEmpty())
                }
            }
            return b.build()
        }

        private fun refresh(context: Context) {
            val sig = synchronized(tasks) { tasks.values.joinToString("|") { line(it) } }
            if (!running || sig == lastShown) return
            lastShown = sig
            try { context.getSystemService(NotificationManager::class.java)?.notify(ID, buildNotification(context)) } catch (_: Throwable) {}
        }

        /** A background task started (call while the app is on screen, so Android lets the service start). */
        fun begin(context: Context, key: String, title: String, pct: Int = -1) {
            val app = context.applicationContext
            val first = synchronized(tasks) { val e = tasks.isEmpty(); tasks[key] = Task(title, pct); e }
            if (first || !running) {
                lastShown = ""
                val i = Intent(app, ExportService::class.java)
                try { if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i) } catch (_: Throwable) {}
            } else refresh(app)
        }

        /** New progress (0–100, or -1 for unknown) and optionally a new title. Starts the task if it isn't listed. */
        fun update(context: Context, key: String, pct: Int, title: String? = null) {
            val known = synchronized(tasks) { tasks[key]?.also { it.pct = pct; if (title != null) it.title = title } != null }
            if (!known) { if (title != null) begin(context, key, title, pct); return }
            refresh(context.applicationContext)
        }

        fun isRunning(key: String) = synchronized(tasks) { tasks.containsKey(key) }

        /** The task ended; [text] (if any) is posted as a "finished" notification. */
        fun end(context: Context, key: String, text: String? = null) {
            val app = context.applicationContext
            val last = synchronized(tasks) { if (tasks.remove(key) == null) return; tasks.isEmpty() }
            if (last) { try { app.startService(Intent(app, ExportService::class.java).setAction(ACTION_STOP)) } catch (_: Throwable) {} }
            else refresh(app)
            if (text.isNullOrBlank()) return
            channel(app)
            try {
                val n = builder(app)
                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    .setContentTitle("AlcorCut Pro")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(openApp(app))
                    .apply {
                        val u = shareUri
                        if (key == EXPORT && u != null) {
                            shareUri = null
                            val send = Intent(Intent.ACTION_SEND).setType(shareMime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            val chooser = Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val pi = PendingIntent.getActivity(app, 7, chooser, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            @Suppress("DEPRECATION") addAction(Notification.Action.Builder(null, "Share", pi).build())
                        }
                    }
                    .build()
                app.getSystemService(NotificationManager::class.java)?.notify(key, DONE_ID, n)
            } catch (_: Throwable) {}
        }

        // Video export (kept for the video editor).
        fun start(context: Context, what: String) = begin(context, EXPORT, what, 0)
        fun progress(context: Context, p: Int, what: String? = null) = update(context, EXPORT, p, what)
        fun finish(context: Context, text: String?) = end(context, EXPORT, text)

        /** The finished video (or audio) of the last export: its notification gets a Share button. */
        @Volatile var shareUri: android.net.Uri? = null
        @Volatile var shareMime: String = "video/mp4"
    }
}
