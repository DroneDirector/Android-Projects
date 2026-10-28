package com.dronedirector.veyrocutpro

import android.app.Application

/** Set-up shared by every process of the app (the editors, the camera and the export process). */
class AlcorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.dronedirector.veyrocutpro.editor.TextFonts.init(assets)
    }
}
