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
 * Keeps an export running while you use other apps or the screen is off: a foreground service with a
 * progress notification (and a wake lock). The export itself runs in the editor as before; this only
 * keeps the app alive and shows where it is. When it ends, a "finished" notification opens the app.
 */
class ExportService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelfNow(); return START_NOT_STICKY }
        }
        val n = progressNotification(this, title, pct)
        try {
            if (Build.VERSION.SDK_INT >= 35) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            else if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, n)
        } catch (_: Throwable) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VeyroCutPro:export").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
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
        @Volatile private var running = false
        @Volatile private var title = "Exporting"
        @Volatile private var pct = 0
        private var lastPct = -1

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null)
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Exports", NotificationManager.IMPORTANCE_LOW).apply { description = "Progress of photo and video exports" })
            }
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun builder(context: Context): Notification.Builder =
            if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)

        fun progressNotification(context: Context, t: String, p: Int): Notification {
            channel(context)
            return builder(context)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(t)
                .setContentText("$p%")
                .setProgress(100, p.coerceIn(0, 100), p <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context))
                .build()
        }

        /** Export started: go to the foreground with a progress notification. */
        fun start(context: Context, what: String) {
            title = what; pct = 0; lastPct = -1
            val i = Intent(context, ExportService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            } catch (_: Throwable) {}
        }

        fun progress(context: Context, p: Int, what: String? = null) {
            if (what != null) title = what
            pct = p
            if (!running || p == lastPct) return
            lastPct = p
            try { context.getSystemService(NotificationManager::class.java)?.notify(ID, progressNotification(context, title, p)) } catch (_: Throwable) {}
        }

        /** Export ended: leave the foreground and say how it went. */
        fun finish(context: Context, text: String?) {
            try { context.startService(Intent(context, ExportService::class.java).setAction(ACTION_STOP)) } catch (_: Throwable) {}
            if (text.isNullOrBlank()) return
            channel(context)
            try {
                val n = builder(context)
                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    .setContentTitle("VeyroCut Pro")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(openApp(context))
                    .build()
                context.getSystemService(NotificationManager::class.java)?.notify(DONE_ID, n)
            } catch (_: Throwable) {}
        }
    }
}
