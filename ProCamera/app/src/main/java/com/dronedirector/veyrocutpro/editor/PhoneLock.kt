package com.dronedirector.veyrocutpro.editor

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The phone's own screen lock (fingerprint, face, PIN, pattern or password — whatever is set up on
 * the phone) as a check before a saved NAS sign-in is used. The app only gets "unlocked" or "not
 * unlocked" back; fingerprints and the PIN stay with the phone. Own code.
 */
object PhoneLock {
    private const val PREFS = "procam"
    private const val KEY = "nas_lock"

    /** Ask for the phone's lock before a saved NAS sign-in is used (on by default). */
    var nasLock by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        nasLock = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setNasLock(c: Context, on: Boolean) {
        nasLock = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /** Is a fingerprint, PIN, pattern or password set up on the phone? */
    fun isSet(c: Context): Boolean = try {
        (c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    } catch (_: Throwable) { false }

    const val NOTE = "Your fingerprint, PIN or pattern stays in the phone — AlcorCut Pro never sees or stores it, it only gets told whether the phone was unlocked."
}

/**
 * Returns a function that shows the phone's own unlock (fingerprint, or PIN / pattern / password)
 * and reports true when it was unlocked.
 */
@Composable
fun rememberPhoneUnlock(): (title: String, onDone: (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    // Android 8–9: the system's "confirm your PIN / pattern / fingerprint" screen.
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        pending?.invoke(r.resultCode == Activity.RESULT_OK); pending = null
    }
    return remember(context) {
        { title, onDone ->
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val b = android.hardware.biometrics.BiometricPrompt.Builder(context)
                    .setTitle(com.dronedirector.veyrocutpro.ui.I18n.tr(title))
                    .setDescription(com.dronedirector.veyrocutpro.ui.I18n.tr(PhoneLock.NOTE))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    b.setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
                }
                try {
                    b.build().authenticate(CancellationSignal(), context.mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) { onDone(true) }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onDone(false) }
                    })
                } catch (_: Throwable) { onDone(false) }
            } else {
                @Suppress("DEPRECATION")
                val i = km.createConfirmDeviceCredentialIntent(com.dronedirector.veyrocutpro.ui.I18n.tr(title), com.dronedirector.veyrocutpro.ui.I18n.tr(PhoneLock.NOTE))
                if (i == null) onDone(false) else { pending = onDone; confirm.launch(i) }
            }
        }
    }
}
