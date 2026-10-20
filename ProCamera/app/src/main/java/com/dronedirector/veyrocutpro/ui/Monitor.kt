package com.dronedirector.veyrocutpro.ui

import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView

/**
 * Layouts for an external monitor. Only when the app is shown on a monitor (Samsung DeX, HDMI /
 * USB-C desktop mode, any display other than the device's own): on the phone's or tablet's own
 * screen this is always [NONE], so those layouts never change. Unplugging switches back at once
 * (the screen isn't reloaded, so the open edit stays).
 */
enum class MonitorClass { NONE, WIDE, ULTRA_WIDE, SUPER_ULTRA_WIDE }

/**
 * Is the app on an external monitor right now? True when its window is on a display other than the
 * device's own (Samsung DeX on a monitor, desktop mode over HDMI / USB-C). DeX mode on a tablet's own
 * screen, or mirroring the phone screen, keep the normal layouts.
 */
@Composable
fun onExternalMonitor(): Boolean {
    LocalConfiguration.current   // re-check whenever the configuration changes (moving display, unplugging)
    val view = LocalView.current
    val displayId = try { view.display?.displayId ?: Display.DEFAULT_DISPLAY } catch (_: Throwable) { Display.DEFAULT_DISPLAY }
    return displayId != Display.DEFAULT_DISPLAY
}

/** Which monitor layout to use for a window of [widthDp] × [heightDp] (NONE off a monitor). */
@Composable
fun monitorClass(widthDp: Float, heightDp: Float): MonitorClass {
    if (!onExternalMonitor() || heightDp <= 0f || widthDp <= heightDp) return MonitorClass.NONE
    val r = widthDp / heightDp
    return when {
        r >= 2.9f -> MonitorClass.SUPER_ULTRA_WIDE   // 32:9 (3.56)
        r >= 2.0f -> MonitorClass.ULTRA_WIDE         // 21:9 (2.33–2.4)
        else -> MonitorClass.WIDE                    // 16:9 / 16:10
    }
}
