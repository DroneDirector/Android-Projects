package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Keeps the details of the last crash on the phone (never sent anywhere by itself). On the next
 * start the app shows them once, so the user can share the report with the developer.
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")
    /** Set while an unsaved edit is being recovered (cleared once the editor has run a while). */
    private fun marker(context: Context) = File(context.filesDir, "recovering.flag")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Throwable) { "?" }
                val sw = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(sw))
                val during = if (marker(app).exists()) "while recovering an unsaved edit\n" else ""
                file(app).writeText(
                    "VelaCut Pro $version · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " · thread ${thread.name}\n" +
                        during + "\n" + sw.toString().take(12_000),
                )
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.let { try { it.readText() } catch (_: Throwable) { null } }
    fun clear(context: Context) { try { file(context).delete() } catch (_: Throwable) {} }

    fun startRecovering(context: Context) { try { marker(context).writeText("1") } catch (_: Throwable) {} }
    fun doneRecovering(context: Context) { try { marker(context).delete() } catch (_: Throwable) {} }
    /** True when the app crashed during the last recovery attempt. */
    fun recoveryCrashed(context: Context): Boolean = marker(context).exists()
}

/** Shown once after a crash: share or copy the report, or dismiss it. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReport.pending(context)) }
    val r = report ?: return
    AlertDialog(
        onDismissRequest = { CrashReport.clear(context); report = null },
        containerColor = Pro.PanelSolid,
        title = { Text("VelaCut Pro closed unexpectedly", color = Color.White) },
        text = {
            Text(
                "Sorry about that. A crash report was saved on this phone (it contains no photos or videos — only what went wrong). " +
                    "Share it with the developer so it can be fixed.\n\n" + r.lineSequence().take(6).joinToString("\n"),
                color = Pro.Dim, fontSize = 12.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "VelaCut Pro crash report"); putExtra(Intent.EXTRA_TEXT, r) }
                try { context.startActivity(Intent.createChooser(send, I18n.tr("Share crash report")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                CrashReport.clear(context); report = null
            }) { Text("SHARE REPORT", color = Pro.Amber) }
        },
        dismissButton = {
            Row2(
                { TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("VelaCut Pro crash report", r))
                    CrashReport.clear(context); report = null
                }) { Text("COPY", color = Pro.Amber) } },
                { TextButton(onClick = { CrashReport.clear(context); report = null }) { Text("DISMISS", color = Pro.Dim) } },
            )
        },
    )
}

@Composable
private fun Row2(a: @Composable () -> Unit, b: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row { a(); b() }
}
