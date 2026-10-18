package com.dronedirector.veyrocutpro

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap from the notification shade opens the camera. */
class CameraTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val i = Intent(this, MainActivity::class.java)
            .putExtra("mode", "PHOTO")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
