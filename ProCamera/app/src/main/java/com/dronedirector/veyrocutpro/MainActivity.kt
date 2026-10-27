package com.dronedirector.veyrocutpro

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
import com.dronedirector.veyrocutpro.ui.Text
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
import com.dronedirector.veyrocutpro.camera.AspectMode
import com.dronedirector.veyrocutpro.camera.CaptureMode
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.FrameGuide
import com.dronedirector.veyrocutpro.camera.GridMode
import com.dronedirector.veyrocutpro.camera.ProCameraController
import com.dronedirector.veyrocutpro.camera.ProParam
import com.dronedirector.veyrocutpro.ui.CameraScreen
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.ProCamTheme

private const val KEY_SCREEN = "screen"
private val TEST_KEYS = setOf(
    "mode", "param", "grid", "filter", "filters", "guide", "profile", "live", "nosetup", "peaking", "zebras",
    "settings", "gallery", "accent", "aspect", "falsecolor", "clean", "screenanalysis", "zebralevel",
)

class MainActivity : ComponentActivity() {

    private lateinit var controller: ProCameraController
    private var sharedPhoto: android.net.Uri? = null
    private var sharedVideos: List<android.net.Uri> = emptyList()

    /** Files handed over by another app, with whether each is a video. */
    private fun sharedMedia(i: Intent?): List<Pair<android.net.Uri, Boolean>> {
        if (i == null) return emptyList()
        val uris: List<android.net.Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_SEND_MULTIPLE -> (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
            Intent.ACTION_EDIT -> listOfNotNull(i.data)
            else -> emptyList()
        }
        return uris.map { u ->
            val mime = try { contentResolver.getType(u) } catch (_: Throwable) { null } ?: i.type ?: ""
            u to mime.startsWith("video/")
        }
    }
    private val screen = androidx.compose.runtime.mutableStateOf(com.dronedirector.veyrocutpro.ui.AppScreen.HOME)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN, screen.value.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread { com.dronedirector.veyrocutpro.editor.VoiceIsolator.prepare(applicationContext) }.start()
        super.onCreate(savedInstanceState)
        com.dronedirector.veyrocutpro.ui.CrashReport.install(this)
        BackgroundWatch.start(this)
        com.dronedirector.veyrocutpro.ui.HomeShortcuts.refresh(applicationContext)
        // Android 13+: background tasks show their progress in the notification shade only with this permission.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("procam", MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notify_bg", false)) {
                prefs.edit().putBoolean("asked_notify_bg", true).apply()
                try { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7301) } catch (_: Throwable) {}
            }
        }
        com.dronedirector.veyrocutpro.editor.LutLibrary.init(this)
        com.dronedirector.veyrocutpro.editor.UserPresets.init(this)
        com.dronedirector.veyrocutpro.camera.CaptureExtras.load(this)
        com.dronedirector.veyrocutpro.camera.SimpleMode.load(this)
        com.dronedirector.veyrocutpro.ui.I18n.load(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        controller = ProCameraController(applicationContext)
        com.dronedirector.veyrocutpro.ui.AccentStore.load(applicationContext)
        val openSettings = applyLaunchOptions(intent)
        val openGallery = intent?.extras?.getBoolean("gallery", false) == true
        // Test runs: --es theme light / dark.
        intent?.extras?.getString("theme")?.let { t -> com.dronedirector.veyrocutpro.ui.AppTheme.set(applicationContext, if (t == "light") 1 else 0) }
        intent?.extras?.getString("accent")?.let { a ->
            com.dronedirector.veyrocutpro.ui.Accent.entries.firstOrNull { it.name == a }
                ?.let { com.dronedirector.veyrocutpro.ui.AccentStore.save(applicationContext, it) }
        }
        controller.refreshLastMedia()
        // The start screen shows on every launch; camera shortcuts and test runs open Capture directly.
        val direct = intent?.action == android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA ||
            intent?.action == android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA ||
            (intent?.extras?.keySet()?.any { it in TEST_KEYS } == true)
        // Shared from another app ("Share" / "Edit with"): open the matching editor with the files.
        val shared = sharedMedia(intent)
        sharedPhoto = shared.firstOrNull { !it.second }?.first
        sharedVideos = shared.filter { it.second }.map { it.first }
        screen.value = if (sharedVideos.isNotEmpty()) com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
        else if (sharedPhoto != null) com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR
        else when (intent?.getStringExtra("screen")) {
            "capture" -> com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE
            "editor" -> com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR
            "photo" -> com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR
            "video" -> com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
            else -> if (direct) com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE else com.dronedirector.veyrocutpro.ui.StartScreen.get(applicationContext)
        }
        // Android closed and rebuilt the app while it was in the background: come back to the screen you were on,
        // not the start screen.
        if (shared.isEmpty()) savedInstanceState?.getString(KEY_SCREEN)?.let { n ->
            com.dronedirector.veyrocutpro.ui.AppScreen.entries.firstOrNull { it.name == n }?.let { screen.value = it }
        }
        com.dronedirector.veyrocutpro.ui.AppTheme.load(this)
        setContent {
            // Light look (Preferences → Appearance) only in the editors; Capture and the start screens stay dark.
            com.dronedirector.veyrocutpro.ui.AppTheme.screenAllowsLight =
                (screen.value == com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR || screen.value == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR) &&
                    com.dronedirector.veyrocutpro.ui.CaptureForEditor.request == null
            ProCamTheme {
                com.dronedirector.veyrocutpro.ui.CrashReportDialog()
                com.dronedirector.veyrocutpro.ui.AudioHardwareNotice()
                androidx.compose.runtime.LaunchedEffect(Unit) { com.dronedirector.veyrocutpro.ui.AudioHardware.start(this@MainActivity) }
                com.dronedirector.veyrocutpro.ui.AppExitLayer {
                com.dronedirector.veyrocutpro.ui.MousePointerHost {
                val current = screen.value
                androidx.compose.animation.Crossfade(current, label = "screen") { s ->
                    when (s) {
                        com.dronedirector.veyrocutpro.ui.AppScreen.HOME -> com.dronedirector.veyrocutpro.ui.HomeScreen(
                            onCapture = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE },
                            onEditor = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR },
                        )
                        com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE -> {
                            androidx.activity.compose.BackHandler { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.HOME }
                            PermissionGate {
                                CameraScreen(controller, openSettings, openGallery)
                            }
                        }
                        com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR -> com.dronedirector.veyrocutpro.ui.EditorHub(
                            onPhoto = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR },
                            onVideo = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR },
                            onBack = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.HOME },
                        )
                        com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR -> com.dronedirector.veyrocutpro.editor.PhotoEditorScreen(
                            onExit = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR },
                            initial = sharedPhoto.also { sharedPhoto = null },
                        )
                        com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR -> com.dronedirector.veyrocutpro.editor.VideoEditorScreen(
                            onExit = { screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR },
                            initialUris = sharedVideos.also { sharedVideos = emptyList() },
                        )
                    }
                }
                // Capture opened from an editor (its camera button): the full Capture screen over the editor.
                com.dronedirector.veyrocutpro.ui.CaptureForEditorHost(controller) { content -> PermissionGate(content) }
                // First launch: AI models and soundtracks are downloaded once (not inside the APK).
                com.dronedirector.veyrocutpro.ui.FirstRunGate()
                // Capture's keyboard shortcuts (Ctrl + / or Settings → Keyboard shortcuts).
                if (com.dronedirector.veyrocutpro.camera.CaptureKeys.show) com.dronedirector.veyrocutpro.editor.ShortcutsDialog(com.dronedirector.veyrocutpro.editor.CaptureShortcuts) { com.dronedirector.veyrocutpro.camera.CaptureKeys.show = false }
                }
                }
            }
        }
    }

    /**
     * Optional launch extras used by the automated screenshot run, e.g.
     * `am start -n com.dronedirector.veyrocutpro/.MainActivity --es mode VIDEO --es param ISO --ez settings true`.
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
            com.dronedirector.veyrocutpro.camera.VideoProfile.entries.firstOrNull { it.name == p }?.let { controller.setProfile(it) }
        }
        extras.getString("live")?.let { p ->
            com.dronedirector.veyrocutpro.camera.LivePlatform.entries.firstOrNull { it.name == p }?.let { controller.live.selectPlatform(it) }
        }
        if (extras.getBoolean("nosetup", false)) controller.live.showSetup(false)
        // Automated runs: let the first-time download carry on in the background.
        if (extras.getBoolean("bgsetup", false)) com.dronedirector.veyrocutpro.editor.AppData.background = true
        if (extras.getBoolean("peaking", false)) controller.togglePeaking()
        if (extras.getBoolean("zebras", false)) controller.toggleZebras()
        if (extras.getBoolean("falsecolor", false)) controller.toggleFalseColor()
        if (extras.getBoolean("screenanalysis", false)) controller.forceScreenAnalysis = true
        if (extras.containsKey("zebralevel")) controller.setZebraLevel(extras.getInt("zebralevel"))
        if (extras.getBoolean("clean", false)) controller.setCleanFeed(true)
        return extras.getBoolean("settings", false)
    }

    override fun onStop() {
        super.onStop()
        // "Keep settings after uninstall": save the settings file when leaving the app (only if changed).
        com.dronedirector.veyrocutpro.ui.KeepSettings.saveIfOn(applicationContext)
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
        // Keyboard shortcuts in the video and photo editors (keys no text field has used).
        if (screen.value != com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE && com.dronedirector.veyrocutpro.editor.KeyBus.dispatch(event)) return true
        if (!::controller.isInitialized || screen.value != com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE) return super.onKeyDown(keyCode, event)
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        // Keyboard shortcuts in Capture (changeable).
        if (!volume && keyCode != KeyEvent.KEYCODE_CAMERA && com.dronedirector.veyrocutpro.camera.CaptureKeys.run(this, controller, event)) return true
        val liveMode = controller.state.value.mode == CaptureMode.LIVE
        // The side (volume) buttons: shutter / record, zoom, or just volume (Settings → Side buttons).
        val side = com.dronedirector.veyrocutpro.camera.SideKeys.get(this)
        if (volume && side == 2) {
            val z = controller.state.value.zoom
            controller.setZoom(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) z * 1.15f else z / 1.15f)
            return true
        }
        if (!liveMode && ((volume && side == 1) || keyCode == KeyEvent.KEYCODE_CAMERA)) {
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
                Text("AlcorCut Pro", color = Pro.Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "AlcorCut Pro needs the camera to work, and the microphone to record sound with video.",
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
