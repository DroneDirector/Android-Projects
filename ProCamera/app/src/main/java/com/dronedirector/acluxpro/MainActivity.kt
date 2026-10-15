package com.dronedirector.acluxpro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dronedirector.acluxpro.camera.AspectMode
import com.dronedirector.acluxpro.camera.CaptureMode
import com.dronedirector.acluxpro.camera.Filter
import com.dronedirector.acluxpro.camera.FrameGuide
import com.dronedirector.acluxpro.camera.GridMode
import com.dronedirector.acluxpro.camera.ProCameraController
import com.dronedirector.acluxpro.camera.ProParam
import com.dronedirector.acluxpro.ui.CameraScreen
import com.dronedirector.acluxpro.ui.Pro
import com.dronedirector.acluxpro.ui.ProCamTheme

class MainActivity : ComponentActivity() {

    private lateinit var controller: ProCameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        controller = ProCameraController(applicationContext)
        com.dronedirector.acluxpro.ui.AccentStore.load(applicationContext)
        val openSettings = applyLaunchOptions(intent)
        val openGallery = intent?.extras?.getBoolean("gallery", false) == true
        intent?.extras?.getString("accent")?.let { a ->
            com.dronedirector.acluxpro.ui.Accent.entries.firstOrNull { it.name == a }
                ?.let { com.dronedirector.acluxpro.ui.AccentStore.save(applicationContext, it) }
        }
        controller.refreshLastMedia()
        setContent {
            ProCamTheme {
                PermissionGate {
                    CameraScreen(controller, openSettings, openGallery)
                }
            }
        }
    }

    /**
     * Optional launch extras used by the automated screenshot run, e.g.
     * `am start -n com.dronedirector.acluxpro/.MainActivity --es mode VIDEO --es param ISO --ez settings true`.
     * Returns whether the settings panel should start open.
     */
    private fun applyLaunchOptions(intent: Intent?): Boolean {
        val extras = intent?.extras ?: return false
        extras.getString("mode")?.let { m ->
            CaptureMode.entries.firstOrNull { it.name == m }?.let { controller.setMode(it) }
        }
        extras.getString("param")?.let { p ->
            ProParam.entries.firstOrNull { it.name == p }?.let { controller.setActiveParam(it) }
        }
        extras.getString("aspect")?.let { a ->
            AspectMode.entries.firstOrNull { it.name == a }?.let { controller.setAspect(it) }
        }
        extras.getString("grid")?.let { g ->
            GridMode.entries.firstOrNull { it.name == g }?.let { controller.setGrid(it) }
        }
        extras.getString("filter")?.let { f ->
            Filter.entries.firstOrNull { it.name == f }?.let { controller.setFilter(it) }
        }
        if (extras.getBoolean("filters", false)) controller.toggleFilterStrip()
        extras.getString("guide")?.let { g ->
            FrameGuide.entries.firstOrNull { it.name == g }?.let { controller.setGuide(it) }
        }
        extras.getString("profile")?.let { p ->
            com.dronedirector.acluxpro.camera.VideoProfile.entries.firstOrNull { it.name == p }?.let { controller.setProfile(it) }
        }
        extras.getString("live")?.let { p ->
            com.dronedirector.acluxpro.camera.LivePlatform.entries.firstOrNull { it.name == p }?.let { controller.live.selectPlatform(it) }
        }
        if (extras.getBoolean("nosetup", false)) controller.live.showSetup(false)
        if (extras.getBoolean("peaking", false)) controller.togglePeaking()
        if (extras.getBoolean("zebras", false)) controller.toggleZebras()
        if (extras.getBoolean("falsecolor", false)) controller.toggleFalseColor()
        if (extras.getBoolean("screenanalysis", false)) controller.forceScreenAnalysis = true
        if (extras.containsKey("zebralevel")) controller.setZebraLevel(extras.getInt("zebralevel"))
        if (extras.getBoolean("clean", false)) controller.setCleanFeed(true)
        return extras.getBoolean("settings", false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!::controller.isInitialized) return super.onKeyDown(keyCode, event)
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val liveMode = controller.state.value.mode == CaptureMode.LIVE
        if (!liveMode && ((volume && controller.state.value.volumeShutter) || keyCode == KeyEvent.KEYCODE_CAMERA)) {
            if (event.repeatCount == 0) controller.shutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.release()
        super.onDestroy()
    }
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val permissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted = granted(Manifest.permission.CAMERA)
        asked = true
    }

    LaunchedEffect(Unit) {
        if (permissions.any { !granted(it) }) launcher.launch(permissions)
    }

    if (cameraGranted) {
        content()
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("AcluxPro", color = Pro.Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "AcluxPro needs the camera to work, and the microphone to record sound with video.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { launcher.launch(permissions) },
                    colors = ButtonDefaults.buttonColors(containerColor = Pro.Amber, contentColor = Color.Black),
                ) { Text("Allow camera") }
                if (asked) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Open app settings", color = Pro.Dim) }
                }
            }
        }
    }
}
