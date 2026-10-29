package com.dronedirector.veyrocutpro.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Where photos and videos are taken (Settings → Photo → Save location). Listens to the phone's
 * location while the camera is open and hands the latest fix to each photo and recording.
 */
object CaptureLocation {
    @Volatile private var last: Location? = null
    private var listening = false
    private val listener = LocationListener { l -> if (better(l, last)) last = l }

    private fun better(l: Location, old: Location?): Boolean =
        old == null || l.time - old.time > 20_000 || l.accuracy <= old.accuracy

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (listening || !CaptureExtras.location || !allowed(context)) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                if (!lm.isProviderEnabled(p)) continue
                lm.getLastKnownLocation(p)?.let { if (better(it, last)) last = it }
                lm.requestLocationUpdates(p, 5_000L, 5f, listener, Looper.getMainLooper())
                listening = true
            } catch (_: Throwable) {}
        }
    }

    fun stop(context: Context) {
        if (!listening) return
        listening = false
        try { (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.removeUpdates(listener) } catch (_: Throwable) {}
    }

    /** The fix to save with a photo or video now, or null (setting off, no permission, no fix younger than 10 minutes). */
    fun current(context: Context): Location? {
        if (!CaptureExtras.location || !allowed(context)) return null
        start(context)
        return last?.takeIf { System.currentTimeMillis() - it.time < 10 * 60_000L }
    }
}
