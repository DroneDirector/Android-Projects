package com.dronedirector.veyrocutpro.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/** Phone side of in-app updates: picks the right APK for this phone and installs it with the system installer. */
object UpdatePlatform {
    const val repo = "DroneDirector/Android-Projects"
    const val tagPrefix = "veyrocutpro-"
    const val supported = true
    const val installNote = "Android asks you to confirm the install. Open the app again when it's done."
    const val installingText = "Installing — confirm on the screen Android shows"

    fun currentVersion(c: Context): String = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }

    /** The APK for this phone: ARM (64-bit), the old 32-bit build or the emulator build. */
    fun pickAsset(assets: List<Triple<String, String, Long>>): Triple<String, String, Long>? {
        val apks = assets.filter { it.first.endsWith(".apk") && !it.first.contains("WATCH") }
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("x86") -> apks.firstOrNull { it.first.contains("EMULATOR") }
            abi == "arm64-v8a" -> apks.firstOrNull { it.first.contains("-ARM-") }
            else -> apks.firstOrNull { it.first.contains("OLD-32bit") }
        } ?: apks.firstOrNull { it.first.contains("-ARM-") }
    }

    /** Hands the APK to the system installer. Returns a problem to show, or null. */
    fun install(c: Context, apk: File): String? {
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            try {
                c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
            return I18n.tr("Allow AlcorCut Pro to install updates (Android just opened the setting), then tap Download and install again.")
        }
        return try {
            val pi = c.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(c.packageName)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("app.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val intent = PendingIntent.getBroadcast(c, id, Intent(c, UpdateReceiver::class.java), flags)
                s.commit(intent.intentSender)
            }
            null
        } catch (t: Throwable) { t.message ?: t.toString() }
    }

    /** Readable reasons for the installer's answers. */
    fun reason(status: Int, msg: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "The install was blocked by the phone."
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "The update doesn't match the installed app (it's signed differently). Install it from the release page instead."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update doesn't work on this phone."
        PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded file is damaged. Try again."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space on the phone."
        else -> msg ?: "The install failed."
    }
}

/** The system installer's answer: asks you to confirm, or reports why it failed. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { try { c.startActivity(it) } catch (_: Throwable) {} }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> AppUpdates.failed(I18n.tr(UpdatePlatform.reason(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))))
        }
    }
}
