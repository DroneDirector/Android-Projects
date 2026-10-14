package com.dronedirector.procam.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dronedirector.procam.camera.AspectMode
import com.dronedirector.procam.camera.CaptureMode
import com.dronedirector.procam.camera.ProCameraController
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(controller: ProCameraController, openSettings: Boolean = false) {
    val s by controller.state.collectAsStateWithLifecycle()
    val analysis by controller.analysis.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSettings by rememberSaveable { mutableStateOf(openSettings) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    LaunchedEffect(previewView) { controller.attach(lifecycleOwner, previewView) }

    // Keep capture orientation in sync, including 180° flips that don't change the configuration.
    DisposableEffect(view) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                view.display?.let { controller.setDisplayRotation(it.rotation) }
            }
        }
        dm.registerDisplayListener(listener, null)
        view.display?.let { controller.setDisplayRotation(it.rotation) }
        onDispose { dm.unregisterDisplayListener(listener) }
    }

    // When manual, show the values we ask the sensor for; when auto, what the meter picked.
    val (manualIso, manualShutter) = controller.effectiveExposure(s)
    val isoShown = if (s.manualExposure) manualIso else s.liveIso
    val shutterShown = if (s.manualExposure) manualShutter else s.liveShutterNs

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        val landscape = maxWidth > maxHeight
        val tablet = minOf(maxWidth, maxHeight) >= 600.dp

        val sideBar = if (tablet) 92.dp else 76.dp
        val rail = if (tablet) 150.dp else 120.dp
        val topBar = if (tablet) 64.dp else 56.dp
        val bottomBar = if (tablet) 220.dp else 190.dp

        // Viewfinder area keeps the same place in the tree in both orientations,
        // so the camera preview never gets torn down on rotation.
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = if (landscape) sideBar else 0.dp,
                    end = if (landscape) rail else 0.dp,
                    top = if (landscape) 0.dp else topBar,
                    bottom = if (landscape) 0.dp else bottomBar,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val sensorRatio = if (s.mode == CaptureMode.VIDEO || s.aspect == AspectMode.R16_9) 16f / 9f else 4f / 3f
            val frameRatio = if (landscape) sensorRatio else 1f / sensorRatio

            Box(Modifier.aspectRatio(frameRatio)) {
                AndroidView(
                    factory = {
                        (previewView.parent as? ViewGroup)?.removeView(previewView)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                analysis?.overlay?.let { bmp ->
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (s.mode == CaptureMode.PHOTO && s.aspect == AspectMode.R1_1) SquareMask()
                GridOverlay(s.grid)
                if (s.showLevel) LevelIndicator()

                // Gestures: tap to focus/meter, pinch to zoom.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { o -> controller.tapToFocus(o.x, o.y) },
                                onDoubleTap = { controller.setActiveParam(null) },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) controller.setZoom(controller.state.value.zoom * zoom)
                            }
                        }
                )
                FocusReticle(s.focusPoint, s.focusId, locked = s.aeLock || s.manualFocus)
                ShutterFlash(s.shutterFlash)
                Countdown(s.countdown)

                // Top HUD inside the frame.
                ExposureHud(
                    s, isoShown, shutterShown,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
                if (s.showHistogram) {
                    Histogram(
                        analysis,
                        width = if (tablet) 170.dp else 112.dp,
                        height = if (tablet) 72.dp else 52.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 44.dp, end = 8.dp),
                    )
                }
                RecordingBadge(
                    s,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 44.dp),
                )
                if (s.sceneActive) {
                    Text(
                        s.scene.label,
                        style = Pro.Value.copy(color = Pro.Amber),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            ProPanel(
                s, controller, isoShown, shutterShown,
                Modifier.align(Alignment.BottomCenter),
            )
        }

        if (landscape) {
            QuickBar(
                s, controller, vertical = true, onSettings = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sideBar)
                    .fillMaxHeight(),
            )
            CaptureControls(
                s, controller, vertical = true, tablet = tablet,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(rail)
                    .fillMaxHeight(),
            )
        } else {
            QuickBar(
                s, controller, vertical = false, onSettings = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBar),
            )
            CaptureControls(
                s, controller, vertical = false, tablet = tablet,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomBar),
            )
        }

        ToastHost(s.message, s.messageId, Modifier.align(Alignment.Center))

        if (showSettings) SettingsPanel(s, controller) { showSettings = false }
    }
}

@Composable
private fun ToastHost(message: String?, id: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id == 0 || message == null) return@LaunchedEffect
        visible = message
        delay(2600)
        visible = null
    }
    AnimatedVisibility(visible = visible != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            visible ?: "",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
