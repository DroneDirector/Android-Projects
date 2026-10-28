package com.dronedirector.veyrocutpro

import android.app.Application

/** Set-up shared by every process of the app (the editors, the camera and the export process). */
class AlcorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.dronedirector.veyrocutpro.editor.TextFonts.init(assets)
        com.dronedirector.veyrocutpro.ui.AppLog.init(this)
    }

    /** Android asks the app to free memory: noted in the app log with how much the app is using. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_MODERATE)
            com.dronedirector.veyrocutpro.ui.AppLog.w("Android: memory getting low (level $level) · " + com.dronedirector.veyrocutpro.ui.AppLog.memory(this))
    }
}
