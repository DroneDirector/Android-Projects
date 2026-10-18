package com.dronedirector.veyrocutpro.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.TonemapCurve
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.Quality
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class ProCameraController(private val context: Context) {

    private val _state = MutableStateFlow(
        CameraUiState(
            cameraSounds = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("camera_sounds", true),
            videoCodec = prefEnum(context, "video_codec", VideoCodec.H264),
            hevcSupported = hevcAvailable(),
            audioGainDb = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("audio_gain", 0),
            audioLimiter = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("audio_limiter", true),
            headphoneMonitor = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("hp_monitor", false),
            monitorVolume = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("hp_volume", 80),
            clipNaming = prefEnum(context, "clip_naming", ClipNaming.STANDARD),
            cameraLetter = (context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString("camera_letter", "A") ?: "A").first(),
            reel = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("reel", 1),
            clipNumber = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("clip", 1),
            shutterSoundRequired = android.os.Build.VERSION.SDK_INT >= 33 && MediaActionSound.mustPlayShutterSound(),
        )
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisResult?>(null)
    val analysis: StateFlow<AnalysisResult?> = _analysis.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val sound = MediaActionSound()

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var captureJob: kotlinx.coroutines.Job? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    /** Recording straight into a picked folder: its file and open descriptor. */
    private var recordDoc: Uri? = null
    private var recordPfd: android.os.ParcelFileDescriptor? = null
    private val filterProcessor = FilterProcessor()
    private var filterWarned = false

    val audioMeter = AudioMeter(context)
    init {
        val p = context.getSharedPreferences("procam", Context.MODE_PRIVATE)
        audioMeter.gainDb = p.getInt("audio_gain", 0)
        audioMeter.limiter = p.getBoolean("audio_limiter", true)
        audioMeter.monitorVolume = p.getInt("hp_volume", 80) / 100f
    }

    // ---- headphone detection
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val deviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) = refreshHeadphones()
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) = refreshHeadphones()
    }

    private fun refreshHeadphones() {
        val outs = try { audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).toList() } catch (_: Throwable) { emptyList() }
        val kind = outs.firstNotNullOfOrNull { d ->
            when (d.type) {
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                else -> if (android.os.Build.VERSION.SDK_INT >= 31 && d.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) "Bluetooth" else null
            }
        }
        set { it.copy(headphones = kind) }
        updateMonitorOutput()
    }

    /** Sound goes to the headphones only when monitoring is on and headphones are present (no speaker feedback). */
    private fun updateMonitorOutput() {
        audioMeter.headphones = st.headphoneMonitor && st.headphones != null
    }

    fun toggleHeadphoneMonitor() {
        val on = !st.headphoneMonitor
        prefs.edit().putBoolean("hp_monitor", on).apply()
        set { it.copy(headphoneMonitor = on) }
        updateMonitorOutput()
        if (on && st.headphones == null) toast("Connect headphones to listen")
    }

    fun setMonitorVolume(v: Int) {
        val x = v.coerceIn(0, 100)
        prefs.edit().putInt("hp_volume", x).apply()
        audioMeter.monitorVolume = x / 100f
        set { it.copy(monitorVolume = x) }
    }

    /** Whether the microphone monitor should keep running while a clip records. */
    val monitorWhileRecording: Boolean get() = st.headphoneMonitor && st.headphones != null
    private var monitorSilencedWarned = false
    private var deviceCallbackRegistered = false
    /** Whether the clip being recorded has a sound track (so the gain stage applies). */
    private var recordingHasAudio = false
    val live = LiveStreamer(context)

    // Panorama capture state (main thread).
    private val panoFrames = ArrayList<Bitmap>()
    private val panoAngles = ArrayList<Float>()
    private var panoGyro: PanoGyro? = null
    private var panoTurn = 0f
    private var panoLastShot = 0f
    private var panoStepDeg = 20f
    private var panoFovLong = 65f
    private var panoAccum = 0f
    private var panoBusy = false
    private var panoTimerJob: kotlinx.coroutines.Job? = null

    // Lapse / slow motion / burst
    private var lapseRecorder: LapseRecorder? = null
    private var lapseTicker: kotlinx.coroutines.Job? = null
    private var slowMoFile: java.io.File? = null
    private var holdBurstJob: kotlinx.coroutines.Job? = null
    private var lastSmartMode = CaptureMode.PANORAMA
    private var lastRegularMode = CaptureMode.PHOTO
    private var rotation = Surface.ROTATION_0

    private var isoRange = Range(100, 3200)
    private var exposureRange = Range(100_000L, 250_000_000L)
    /** ISO × exposure-time snapshot from the auto meter, used for ISO / shutter priority. */
    private var meteredExposure = 100.0 * 8_333_333.0
    private var frameCounter = 0L

    @Volatile
    private var analysisConfig = AnalysisConfig()

    private val analyzer = FrameAnalyzer({ analysisConfig }) { result ->
        _analysis.value = result
        if (result.motionX != 0f) mainExecutor.execute { onPanoMotion(result.motionX) }
    }

    private val st: CameraUiState get() = _state.value

    private fun set(f: (CameraUiState) -> CameraUiState) {
        _state.update(f)
        val s = _state.value
        analysisConfig = AnalysisConfig(
            histogram = s.proHistogram,
            zebras = s.proZebras,
            zebraLevel = s.zebraLevel,
            peaking = s.proPeaking,
            falseColor = s.proFalseColor,
            mirror = s.isFront,
            motion = s.panoActive,
        )
    }

    fun toast(message: String) = set { it.copy(message = message, messageId = it.messageId + 1) }

    /** Points the gallery thumbnail at the newest saved photo or video (after deletes, or on start). */
    fun refreshLastMedia() {
        scope.launch {
            val latest = kotlinx.coroutines.withContext(Dispatchers.IO) {
                MediaLibrary.query(context).firstOrNull()?.uri
            }
            set { it.copy(lastMedia = latest) }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /** Leaving the capture screen (home / editors): release the camera, mic and live stream. */
    fun detach() {
        try { if (st.recording) shutter() } catch (_: Throwable) {}
        startScreenAnalysis(false)
        audioMeter.stopMonitoring()
        try { live.close() } catch (_: Throwable) {}
        try { provider?.unbindAll() } catch (_: Throwable) {}
        camera = null
        imageCapture = null
        videoCapture = null
        imageAnalysis = null
        previewView = null
        set { it.copy(ready = false) }
    }

    fun attach(owner: LifecycleOwner, view: PreviewView) {
        this.owner = owner
        previewView = view
        refreshStorage()
        // Monitoring LUT chosen in settings (no rebind needed yet: bind() follows).
        CaptureExtras.monitorLut?.let { com.dronedirector.veyrocutpro.editor.LutLibrary.get(it) }?.strip(33)?.let {
            filterProcessor.setLut(it, toPreview = true, toVideo = CaptureExtras.bakeLut)
        }
        if (!deviceCallbackRegistered) {
            deviceCallbackRegistered = true
            try { audioManager.registerAudioDeviceCallback(deviceCallback, null) } catch (_: Throwable) {}
        }
        refreshHeadphones()
        if (provider != null) {
            bind()
            return
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = try {
                future.get()
            } catch (e: Exception) {
                toast("Camera unavailable: ${e.message}")
                return@addListener
            }
            provider = p
            discoverLenses(p)
            val extFuture = ExtensionsManager.getInstanceAsync(context, p)
            extFuture.addListener({
                extensions = try { extFuture.get() } catch (_: Exception) { null }
                refreshCapabilities()
                if (st.mode == CaptureMode.LIVE) {
                    live.open()
                    set { it.copy(ready = true) }
                } else bind()
            }, mainExecutor)
        }, mainExecutor)
    }

    fun release() {
        try { audioManager.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Throwable) {}
        deviceCallbackRegistered = false
        live.close()
        audioMeter.stopMonitoring()
        filterProcessor.release()
        ioExecutor.shutdown()
        recording?.stop()
        recording = null
        provider?.unbindAll()
        analysisExecutor.shutdown()
        scope.cancel()
        sound.release()
    }

    fun setDisplayRotation(r: Int) {
        if (r == rotation) return
        val turned90 = (r % 2) != (rotation % 2)
        rotation = r
        // The preview must follow the screen too, or it shows up sideways and stretched.
        previewUseCase?.setTargetRotation(r)
        imageCapture?.setTargetRotation(r)
        imageAnalysis?.setTargetRotation(r)
        if (!st.recording) videoCapture?.setTargetRotation(r)
        // After a portrait <-> landscape turn, rebuild the camera streams so the viewfinder is set
        // up for the new shape (otherwise it can show sideways and stretched). Skipped mid-recording,
        // which would stop the clip; the preview still follows the turn via its target rotation.
        if (turned90 && !st.recording && st.mode != CaptureMode.LIVE) {
            rotationRebind?.cancel()
            rotationRebind = scope.launch {
                delay(300)
                if (!st.recording) bind()
            }
        }
    }

    private var previewUseCase: Preview? = null
    private var effectBound = false
    private var rotationRebind: kotlinx.coroutines.Job? = null

    // ---------------------------------------------------------------- lenses & capabilities

    private fun idOf(info: CameraInfo): String = Camera2CameraInfo.from(info).cameraId

    private fun discoverLenses(p: ProcessCameraProvider) {
        val back = mutableListOf<Pair<Float, CameraInfo>>()
        val front = mutableListOf<Pair<Float, CameraInfo>>()
        for (info in p.availableCameraInfos) {
            val ratio = try { info.intrinsicZoomRatio } catch (_: Throwable) { 1f }
            when (info.lensFacing) {
                CameraSelector.LENS_FACING_BACK -> back += ratio to info
                CameraSelector.LENS_FACING_FRONT -> front += ratio to info
            }
        }
        back.sortBy { it.first }
        val lenses = mutableListOf<Lens>()
        val labels = mutableSetOf<String>()
        for ((ratio, info) in back) {
            var label = Stops.zoomLabel(ratio)
            while (label in labels) label += "'"
            labels += label
            lenses += Lens(idOf(info), CameraSelector.LENS_FACING_BACK, label, info)
        }
        front.firstOrNull()?.let { lenses += Lens(idOf(it.second), CameraSelector.LENS_FACING_FRONT, "FRONT", it.second) }

        if (lenses.isEmpty()) {
            toast("No camera found on this device")
            return
        }
        val mainBack = back.indices.minByOrNull { abs(back[it].first - 1f) } ?: 0
        set { it.copy(lenses = lenses, lensIndex = mainBack) }
    }

    private fun baseSelector(lens: Lens): CameraSelector =
        CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { idOf(it) == lens.id } }
            .build()

    private fun refreshCapabilities() {
        val lens = st.lens ?: return
        val info = lens.info
        val c2 = Camera2CameraInfo.from(info)

        val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val manual = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { isoRange = it }
        c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
            // Cap at 30 s; some sensors report absurd maximums.
            exposureRange = Range(it.lower, minOf(it.upper, 30_000_000_000L))
        }
        val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val tonemaps = c2.getCameraCharacteristic(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: IntArray(0)
        val flat = tonemaps.contains(CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)

        val exposure = info.exposureState
        val evSupported = exposure.isExposureCompensationSupported
        val evRange = exposure.exposureCompensationRange
        val evStep = exposure.exposureCompensationStep.toFloat()

        val rawFormats = try {
            ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats
        } catch (_: Throwable) {
            emptySet<Int>()
        }
        val rawOk = rawFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW)
        val rawJpegOk = rawFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)

        val vcap = try { Recorder.getVideoCapabilities(info) } catch (_: Throwable) { null }
        val sdr = vcap?.getSupportedQualities(DynamicRange.SDR) ?: emptyList()
        val resolutions = VideoRes.entries.filter { it.quality in sdr }.ifEmpty { listOf(VideoRes.FHD) }
        val hlg = vcap?.supportedDynamicRanges?.contains(DynamicRange.HLG_10_BIT) == true
        val stab = try { vcap?.isStabilizationSupported == true } catch (_: Throwable) { false }

        val fpsList = (info.supportedFrameRateRanges
            .filter { it.lower == it.upper }
            .map { it.upper } + 30)
            .filter { it in listOf(24, 25, 30, 48, 50, 60, 120) }
            .distinct()
            .sorted()

        val scenes = Scene.entries.filter { scene ->
            scene == Scene.STANDARD || extensions?.let { ext ->
                try { ext.isExtensionAvailable(baseSelector(lens), scene.extensionMode) } catch (_: Throwable) { false }
            } == true
        }

        refreshPhotoSizes(lens)
        val previewStab = try { Preview.getPreviewCapabilities(info).isStabilizationSupported } catch (_: Throwable) { false }
        val highSpeedFps: List<Int> = try {
            if (Recorder.getHighSpeedVideoCapabilities(info) == null) emptyList() else {
                val probe = HighSpeedVideoSessionConfig(VideoCapture.withOutput(Recorder.Builder().build()), Preview.Builder().build())
                info.getSupportedFrameRateRanges(probe).map { it.upper }.filter { it >= 100 }.distinct().sorted()
            }
        } catch (_: Throwable) { emptyList() }
        val slowList = highSpeedFps.filter { it < 200 }
        val superList = highSpeedFps.filter { it >= 200 }
        val fallback = if (fpsList.any { it >= 60 }) 60 else 0
        val isoStops = if (manual) Stops.isoStops(isoRange.lower, isoRange.upper) else emptyList()
        val shutterStops = if (manual) Stops.shutterStops(exposureRange.lower, exposureRange.upper) else emptyList()

        set { s ->
            s.copy(
                manualSensor = manual,
                isoStops = isoStops,
                shutterStops = shutterStops,
                minFocusDiopters = minFocus,
                evMin = if (evSupported) evRange.lower else 0,
                evMax = if (evSupported) evRange.upper else 0,
                evStep = evStep,
                rawSupported = rawOk || rawJpegOk,
                hasFlash = info.hasFlashUnit(),
                scenes = scenes,
                videoResolutions = resolutions,
                fpsOptions = fpsList,
                stabilizationSupported = stab,
                hlgSupported = hlg,
                flatSupported = flat,
                // Clamp current settings to what this lens can do.
                manualIso = s.manualIso && manual,
                manualShutter = s.manualShutter && manual,
                iso = s.iso.coerceIn(isoRange.lower, isoRange.upper),
                shutterNs = s.shutterNs.coerceIn(exposureRange.lower, exposureRange.upper),
                manualFocus = s.manualFocus && minFocus > 0f,
                focusDiopters = s.focusDiopters.coerceIn(0f, max(minFocus, 0f)),
                ev = if (evSupported) s.ev.coerceIn(evRange.lower, evRange.upper) else 0,
                photoFormat = when (s.photoFormat) {
                    PhotoFormat.RAW -> if (rawOk) PhotoFormat.RAW else PhotoFormat.JPEG
                    PhotoFormat.RAW_JPEG -> if (rawJpegOk) PhotoFormat.RAW_JPEG else if (rawOk) PhotoFormat.RAW else PhotoFormat.JPEG
                    PhotoFormat.JPEG -> PhotoFormat.JPEG
                },
                scene = if (s.scene in scenes) s.scene else Scene.STANDARD,
                videoRes = if (s.videoRes in resolutions) s.videoRes else (resolutions.firstOrNull { it == VideoRes.FHD } ?: resolutions.first()),
                fps = if (s.fps in fpsList) s.fps else 30,
                profile = when (s.profile) {
                    VideoProfile.HLG -> if (hlg) VideoProfile.HLG else VideoProfile.STANDARD
                    else -> s.profile
                },
                flash = if (info.hasFlashUnit()) s.flash else FlashSetting.OFF,
                previewStabilizationSupported = previewStab,
                slowMoFps = slowList,
                superSlowMoFps = superList,
                slowFallbackFps = fallback,
                slowFps = defaultSlowFps(s.mode, slowList, superList, fallback, s.slowFps),
            )
        }
    }

    private fun defaultSlowFps(mode: CaptureMode, slow: List<Int>, sup: List<Int>, fallback: Int, current: Int): Int = when (mode) {
        CaptureMode.SUPER_SLOW_MO -> if (current in sup) current else sup.maxOrNull() ?: 0
        else -> if (current in slow || current == fallback) current else slow.maxOrNull() ?: fallback
    }

    /** Re-reads the size list (e.g. after AI upscale is switched on or off). */
    fun refreshSizes() { refreshPhotoSizes(); bind() }

    private fun refreshPhotoSizes(target: Lens? = st.lens) {
        val lens = target ?: return
        UpscaleSettings.load(context)
        val aspect = st.aspect
        val sizes = PhotoSizes.forCamera(lens.info, aspect)
        val saved = PhotoSizes.load(context, lens.id, aspect)
        val chosen = sizes.firstOrNull { it.key == saved } ?: sizes.firstOrNull { !it.highRes } ?: sizes.firstOrNull()
        val report = PhotoSizes.report(lens.info)
        set { it.copy(photoSizes = sizes, photoSize = chosen, sensorReport = report) }
    }

    fun setPhotoSize(size: PhotoSize) {
        val lens = st.lens ?: return
        if (st.photoSize == size) return
        PhotoSizes.save(context, lens.id, st.aspect, size)
        set { it.copy(photoSize = size) }
        if (!st.isVideo && st.mode != CaptureMode.LIVE) bind()
    }

    /** RAW / RAW+JPEG availability, so the UI only offers formats that work. */
    fun photoFormats(): List<PhotoFormat> {
        val info = st.lens?.info ?: return listOf(PhotoFormat.JPEG)
        val formats = try {
            ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats
        } catch (_: Throwable) {
            emptySet<Int>()
        }
        return buildList {
            add(PhotoFormat.JPEG)
            if (formats.contains(ImageCapture.OUTPUT_FORMAT_RAW)) add(PhotoFormat.RAW)
            if (formats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)) add(PhotoFormat.RAW_JPEG)
        }
    }

    // ---------------------------------------------------------------- binding

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            frameCounter++
            if (frameCounter % 6L != 0L) return
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val aperture = result.get(CaptureResult.LENS_APERTURE)
            val focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
            _state.update {
                it.copy(liveIso = iso, liveShutterNs = exp, liveAperture = aperture, liveFocusDiopters = focus)
            }
        }
    }

    private fun bind(attempt: Int = 0) {
        val p = provider ?: return
        val owner = owner ?: return
        val view = previewView ?: return
        val lens = st.lens ?: return
        val s = st

        camera?.cameraInfo?.zoomState?.removeObservers(owner)
        // A pre-record buffer ends with the old camera setup (it restarts once bound again).
        if (preBuffer != null && preUserNs < 0) { preJob?.cancel(); try { preBuffer?.stop() } catch (_: Throwable) {} }
        p.unbindAll()
        startScreenAnalysis(false)
        camera = null
        imageCapture = null
        videoCapture = null
        imageAnalysis = null
        set { it.copy(ready = false) }

        // LIVE mode hands the camera to the live streamer.
        if (s.mode == CaptureMode.LIVE) {
            _analysis.value = null
            return
        }

        view.display?.let { rotation = it.rotation }

        val ext = extensions
        val useScene = s.sceneActive && ext != null && s.scene in s.scenes
        var selector = baseSelector(lens)
        if (useScene && ext != null) {
            selector = try { ext.getExtensionEnabledCameraSelector(selector, s.scene.extensionMode) } catch (_: Throwable) { selector }
        }

        val aspect = if (s.isVideo || (s.mode != CaptureMode.PANORAMA && s.aspect == AspectMode.R16_9))
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val resolution = ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build()

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(resolution)
            .setTargetRotation(rotation)
        if (!useScene && !s.mode.isSlowMo) Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(captureCallback)
        if (s.mode == CaptureMode.HYPERLAPSE && s.lapseStabilize && s.previewStabilizationSupported) {
            previewBuilder.setPreviewStabilizationEnabled(true)
        }
        val preview = previewBuilder.build()
        preview.setSurfaceProvider(view.surfaceProvider)
        previewUseCase = preview
        effectBound = false

        if (s.mode.isSlowMo) {
            bindSlowMo(p, owner, selector, s, preview)
            return
        }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspect)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .build()
        analysis.setAnalyzer(analysisExecutor, analyzer)

        var ic: ImageCapture? = null
        var vc: VideoCapture<Recorder>? = null
        val main: UseCase? = when {
            s.mode.isLapse -> null
            !s.isVideo -> buildImageCapture(s, resolution, useScene).also { ic = it }
            else -> buildVideoCapture(s, strict = true).also { vc = it }
        }
        // Video: if the chosen quality can't run in any combination, fall back to a lower one.
        val fallbackVideo: VideoCapture<Recorder>? = if (vc != null) buildVideoCapture(s, strict = false) else null

        // Live filter: a GPU effect on the preview (and on the recording in video modes).
        val wantEffect = needsEffect(s) && !useScene
        filterProcessor.setFilter(s.filter)
        filterProcessor.setCurve(logCurve(s), s.logAssist)
        val effect: CameraEffect? = if (wantEffect) {
            val targets = if (s.mode.isNormalVideo) CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE else CameraEffect.PREVIEW
            FilterEffect(targets, filterProcessor) { t -> mainExecutor.execute { toast("Filter error: ${t.message}") } }
        } else null

        data class Attempt(val effect: Boolean, val analysis: Boolean, val fallback: Boolean = false)
        val attempts = buildList {
            // The chosen video quality comes first: the analysis stream (histogram etc. — it falls back
            // to reading the screen) and then the live filter are dropped before the resolution is.
            if (effect != null) { add(Attempt(true, true)); add(Attempt(true, false)) }
            add(Attempt(false, true)); add(Attempt(false, false))
            if (fallbackVideo != null) {
                if (effect != null) add(Attempt(true, false, fallback = true))
                add(Attempt(false, false, fallback = true))
            }
        }.filter { !forceScreenAnalysis || !it.analysis }
        var usedFallback = false
        var bound: Camera? = null
        var withAnalysis = false
        var withEffect = false
        var error: Throwable? = null
        for (a in attempts) {
            try {
                p.unbindAll()
                val group = UseCaseGroup.Builder().addUseCase(preview)
                val useMain = if (a.fallback) fallbackVideo else main
                if (useMain != null) group.addUseCase(useMain)
                if (a.analysis) group.addUseCase(analysis)
                if (a.effect && effect != null) group.addEffect(effect)
                bound = p.bindToLifecycle(owner, selector, group.build())
                withAnalysis = a.analysis
                withEffect = a.effect
                usedFallback = a.fallback
                effectBound = a.effect && effect != null
                break
            } catch (t: Throwable) {
                error = t
            }
        }
        if (bound != null && s.mode.isLapse && !withEffect) {
            toast("${s.mode.label} isn't supported on this camera")
        } else if (bound != null && effect != null && !withEffect && !filterWarned) {
            filterWarned = true
            toast(if (s.isVideo) "Live filters aren't supported with these settings" else "Filter preview unavailable; photos will still be filtered")
        }

        if (bound == null) {
            // Step back to safer settings and try again.
            if (attempt < 2) {
                when {
                    !s.isVideo && s.photoSize?.highRes == true -> {
                        val fallback = s.photoSizes.firstOrNull { !it.highRes }
                        set { it.copy(photoSize = fallback) }
                        toast("${s.photoSize?.label} isn't available together with this mode, using ${fallback?.label ?: "default"}")
                    }
                    s.mode == CaptureMode.PRO_PHOTO && s.photoFormat != PhotoFormat.JPEG -> {
                        set { it.copy(photoFormat = PhotoFormat.JPEG) }
                        toast("RAW isn't available with this lens, using JPEG")
                    }
                    s.mode == CaptureMode.PHOTO && s.scene != Scene.STANDARD -> {
                        set { it.copy(scene = Scene.STANDARD) }
                        toast("${s.scene.label} isn't available here")
                    }
                    s.mode.isNormalVideo -> {
                        set { it.copy(fps = 30, profile = VideoProfile.STANDARD, stabilization = false) }
                        toast("Video settings not supported, reset to 30 fps")
                    }
                    else -> {}
                }
                bind(attempt + 1)
            } else {
                toast("Couldn't open camera: ${error?.message ?: "unknown error"}")
            }
            return
        }

        camera = bound
        imageCapture = ic
        if (usedFallback) vc = fallbackVideo
        videoCapture = vc
        // Tell the user if the recording won't be in the chosen resolution.
        vc?.let { v ->
            val got = try { v.resolutionInfo?.resolution } catch (_: Throwable) { null }
            val want = s.videoRes
            if (got != null) {
                val long = maxOf(got.width, got.height)
                val wantLong = when (want.quality) { Quality.UHD -> 3840; Quality.FHD -> 1920; Quality.HD -> 1280; else -> 0 }
                if (wantLong > 0 && long < wantLong * 0.9) toast("${want.label} isn't possible with these settings on this camera — recording in ${long}×${minOf(got.width, got.height)}. Try 30 fps or turn off stabilisation.")
            }
        }
        imageAnalysis = if (withAnalysis) analysis else null
        if (!withAnalysis) _analysis.value = null
        // Without its own analysis stream (common alongside video), read the on-screen picture instead.
        startScreenAnalysis(!withAnalysis)

        bound.cameraInfo.zoomState.observe(owner) { z ->
            _state.update { it.copy(zoom = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
        }
        set { it.copy(ready = true) }
        applyControls()
        if (s.mode.isNormalVideo) scope.launch { delay(600); startPreBuffer() }
    }

    // ---------------------------------------------------------------- pre-record buffer
    private var preBuffer: Recording? = null
    private var preFile: java.io.File? = null
    private var preUserNs = -1L
    private var preElapsedNs = 0L
    private var preJob: kotlinx.coroutines.Job? = null
    private var preHasAudio = false

    private fun preAllowed(s: CameraUiState) =
        CaptureExtras.preRecordSec > 0 && s.mode.isNormalVideo && !s.mode.isSlowMo && s.ready

    /** Settings changed: start or stop the pre-record buffer. */
    fun refreshPreRecord() {
        if (CaptureExtras.preRecordSec > 0) startPreBuffer()
        else if (preBuffer != null && preUserNs < 0) { preJob?.cancel(); try { preBuffer?.stop() } catch (_: Throwable) {} }
    }

    @SuppressLint("MissingPermission")
    private fun startPreBuffer() {
        val s = st
        val vc = videoCapture ?: return
        if (!preAllowed(s) || recording != null || preBuffer != null) return
        val file = java.io.File(context.cacheDir, "prerec_${System.currentTimeMillis()}.mp4")
        var pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        preHasAudio = s.audio && mic
        if (preHasAudio) { btMic(CaptureExtras.micBluetooth); pending = pending.withAudioEnabled() }
        preFile = file; preUserNs = -1L; preElapsedNs = 0L
        try {
            preBuffer = pending.start(mainExecutor) { e -> onPreEvent(e) }
        } catch (_: Throwable) {
            preBuffer = null; preFile = null; file.delete(); return
        }
        // Start a fresh buffer now and then so the temporary file stays small.
        preJob?.cancel()
        preJob = scope.launch { delay(90_000); if (preUserNs < 0) try { preBuffer?.stop() } catch (_: Throwable) {} }
    }

    private fun onPreEvent(e: VideoRecordEvent) {
        when (e) {
            is VideoRecordEvent.Status -> {
                preElapsedNs = e.recordingStats.recordedDurationNanos
                if (preUserNs >= 0) {
                    onRecordEvent(e)
                    set { it.copy(recordSeconds = (preElapsedNs - preUserNs) / 1_000_000_000L + CaptureExtras.preRecordSec) }
                } else {
                    val amp = try { e.recordingStats.audioStats.audioAmplitude.toFloat() } catch (_: Throwable) { 0f }
                    if (!audioMeter.isRunning) audioMeter.pushAmplitude((amp * 10f.pow(st.audioGainDb / 20f)).coerceAtMost(1f))
                }
            }
            is VideoRecordEvent.Pause, is VideoRecordEvent.Resume -> if (preUserNs >= 0) onRecordEvent(e)
            is VideoRecordEvent.Finalize -> {
                val file = preFile
                preFile = null; preBuffer = null
                val userNs = preUserNs
                preUserNs = -1L
                if (userNs >= 0 && file != null) {
                    recording = null
                    btMic(false)
                    set { it.copy(recording = false, paused = false, audioLevel = 0f, processing = true) }
                    val fromUs = userNs / 1000 - CaptureExtras.preRecordSec * 1_000_000L
                    val volume = st.storageVolume
                    val name = "${clipName()}.mp4"
                    val gainDb = st.audioGainDb; val limiter = st.audioLimiter; val wind = CaptureExtras.windFilter; val nr = CaptureExtras.noiseReduce
                    val withAudio = preHasAudio
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { PreRoll.trim(context, file, fromUs, volume, name) }
                        if (uri != null && withAudio && (gainDb != 0 || wind || nr > 0)) withContext(Dispatchers.IO) { AudioGain.apply(context, uri, gainDb, limiter, wind, nr) }
                        if (uri != null && CaptureExtras.titleOn && CaptureExtras.titleText.isNotBlank()) burnTitle(uri)
                        set { it.copy(processing = false, lastMedia = uri ?: it.lastMedia) }
                        if (uri != null) withContext(Dispatchers.IO) { Slate.log(context, uri, st, true) }
                        if (uri == null) toast("Couldn't save the recording")
                        delay(300)
                        startPreBuffer()
                    }
                    videoCapture?.setTargetRotation(rotation)
                } else {
                    file?.delete()
                    if (preAllowed(st)) scope.launch { delay(300); startPreBuffer() }
                }
            }
            else -> {}
        }
    }

    /** Bluetooth headset microphone on / off (when chosen in settings). */
    private fun btMic(on: Boolean) {
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= 31) {
                    audioManager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?.let { audioManager.setCommunicationDevice(it) }
                } else {
                    @Suppress("DEPRECATION") audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION") audioManager.isBluetoothScoOn = true
                }
            } else {
                if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
                else { @Suppress("DEPRECATION") audioManager.stopBluetoothSco(); @Suppress("DEPRECATION") audioManager.isBluetoothScoOn = false }
            }
        } catch (_: Throwable) {}
    }

    /** Microphones the phone reports right now (for the settings read-out). */
    fun inputDevices(): List<String> = try {
        audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).mapNotNull { d ->
            when (d.type) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone microphone"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset mic"
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB mic (${d.productName})"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth mic (${d.productName})"
                else -> null
            }
        }.distinct()
    } catch (_: Throwable) { emptyList() }

    private var screenAnalysisJob: kotlinx.coroutines.Job? = null
    /** Test hook: behave like a phone that can't add an analysis stream. */
    var forceScreenAnalysis = false

    private fun startScreenAnalysis(on: Boolean) {
        screenAnalysisJob?.cancel()
        screenAnalysisJob = null
        if (!on) return
        screenAnalysisJob = scope.launch {
            var busy = false
            while (true) {
                delay(70)
                val cfg = analysisConfig
                if (!(cfg.histogram || cfg.zebras || cfg.peaking || cfg.falseColor)) {
                    if (_analysis.value != null) _analysis.value = null
                    continue
                }
                if (busy) continue
                val view = previewView ?: continue
                val tv = view.findTextureView() ?: continue
                if (!tv.isAvailable || tv.width <= 0 || tv.height <= 0) continue
                // Small copy keeps this cheap: ~360 px on the long side.
                val scale = 360f / maxOf(tv.width, tv.height)
                val bw = (tv.width * scale).toInt().coerceAtLeast(16)
                val bh = (tv.height * scale).toInt().coerceAtLeast(16)
                val raw = try { tv.getBitmap(bw, bh) } catch (_: Throwable) { null } ?: continue
                // getBitmap ignores the view's crop/rotation transform; apply it so the copy matches the screen.
                val bmp = try {
                    val m = tv.getTransform(null)
                    val out = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(out).apply {
                        scale(scale, scale)
                        concat(m)
                        scale(1f / scale, 1f / scale)
                        drawBitmap(raw, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    }
                    raw.recycle()
                    out
                } catch (_: Throwable) { raw }
                busy = true
                analysisExecutor.execute {
                    analyzer.analyzeBitmap(bmp)
                    bmp.recycle()
                    busy = false
                }
            }
        }
    }

    private fun android.view.View.findTextureView(): android.view.TextureView? {
        if (this is android.view.TextureView) return this
        if (this is android.view.ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).findTextureView()?.let { return it }
        }
        return null
    }

    private fun flashModeFor(s: CameraUiState): Int = when (s.flash) {
        FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
        else -> ImageCapture.FLASH_MODE_OFF
    }

    private fun buildImageCapture(s: CameraUiState, resolution: ResolutionSelector, useScene: Boolean): ImageCapture {
        val format = if (useScene || s.mode != CaptureMode.PRO_PHOTO) ImageCapture.OUTPUT_FORMAT_JPEG else when (s.photoFormat) {
            PhotoFormat.RAW -> ImageCapture.OUTPUT_FORMAT_RAW
            PhotoFormat.RAW_JPEG -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
            PhotoFormat.JPEG -> ImageCapture.OUTPUT_FORMAT_JPEG
        }
        val mode = if (s.burst > 1 || s.mode == CaptureMode.PANORAMA || s.mode == CaptureMode.BURST) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        val size = s.photoSize
        val photoResolution = if (size != null && s.mode != CaptureMode.PANORAMA) {
            val aspect = if (s.aspect == AspectMode.R16_9) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspect)
                .setResolutionStrategy(ResolutionStrategy(Size(size.width, size.height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .apply { if (size.highRes) setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE) }
                .build()
        } else resolution
        val ic = ImageCapture.Builder()
            .setCaptureMode(if (size?.highRes == true) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else mode)
            .setResolutionSelector(photoResolution)
            .setOutputFormat(format)
            .setJpegQuality(96)
            .setFlashMode(flashModeFor(s))
            .setTargetRotation(rotation)
            .build()
        if (s.aspect == AspectMode.R1_1 && s.mode != CaptureMode.PANORAMA) ic.setCropAspectRatio(Rational(1, 1))
        return ic
    }

    @SuppressLint("RestrictedApi")
    private fun buildVideoCapture(s: CameraUiState, strict: Boolean = false): VideoCapture<Recorder> {
        val q = s.videoRes.quality
        // Strict: exactly the chosen quality (binding fails rather than silently dropping, e.g. 4K → 1080p).
        val recorderBuilder = Recorder.Builder()
            .setQualitySelector(if (strict) QualitySelector.from(q) else QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
        if (s.highBitrate) recorderBuilder.setTargetVideoEncodingBitRate(s.videoRes.highBitrate)
        // 8-bit codec choice; 10-bit HLG is always HEVC and is left to CameraX.
        val hlg = s.isPro && s.profile == VideoProfile.HLG && s.hlgSupported
        if (!hlg && s.videoCodec == VideoCodec.HEVC && s.hevcSupported) {
            try { recorderBuilder.setVideoMimeType(VideoCodec.HEVC.mime) } catch (_: Throwable) {}
        }
        val recorder = recorderBuilder.build()

        val builder = VideoCapture.Builder(recorder)
            .setTargetFrameRate(Range(s.fps, s.fps))
            .setTargetRotation(rotation)
        if (s.stabilizationSupported) builder.setVideoStabilizationEnabled(s.stabilization)
        if (s.isPro && s.profile == VideoProfile.HLG && s.hlgSupported) builder.setDynamicRange(DynamicRange.HLG_10_BIT)
        return builder.build()
    }

    // ---------------------------------------------------------------- manual controls → Camera2

    /** Log-style curve: lifted blacks, rolled-off highlights, for grading later. */
    private val flatCurve: TonemapCurve by lazy {
        val n = 33
        val pts = FloatArray(n * 2)
        for (i in 0 until n) {
            val x = i / (n - 1f)
            val y = (ln(1f + 6f * x) / ln(7f)) * 0.9f + 0.04f
            pts[i * 2] = x
            pts[i * 2 + 1] = y.coerceIn(0f, 1f)
        }
        TonemapCurve(pts, pts.copyOf(), pts.copyOf())
    }

    private fun evFactor(s: CameraUiState): Double = 2.0.pow(s.ev * s.evStep.toDouble())

    /** The values actually requested from the sensor when any exposure control is manual. */
    fun effectiveExposure(s: CameraUiState = st): Pair<Int, Long> {
        val target = meteredExposure * evFactor(s)
        return when {
            s.manualIso && s.manualShutter -> s.iso to s.shutterNs
            s.manualIso -> s.iso to (target / s.iso).toLong().coerceIn(exposureRange.lower, exposureRange.upper)
            else -> (target / s.shutterNs).toInt().coerceIn(isoRange.lower, isoRange.upper) to s.shutterNs
        }
    }

    private fun applyControls() {
        val cam = camera ?: return
        val s = st
        val control = cam.cameraControl

        control.enableTorch(s.flash == FlashSetting.TORCH)
        imageCapture?.flashMode = flashModeFor(s)

        // Camera extensions (HDR, Night…) run their own pipeline.
        if (s.sceneActive) return

        val b = CaptureRequestOptions.Builder()
        if (s.manualExposure && s.manualSensor) {
            val (iso, exp) = effectiveExposure(s)
            val frameNs = if (s.mode.isNormalVideo) 1_000_000_000L / s.fps else 33_333_333L
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, max(exp, frameNs))
        } else if (s.aeLock || s.panoActive) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }

        if (s.manualFocusActive && s.minFocusDiopters > 0f) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, s.focusDiopters)
        }

        if (s.isPro && s.whiteBalance != WhiteBalance.AUTO) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, s.whiteBalance.awbMode)
        }
        if ((s.isPro && s.awbLock) || s.panoActive) b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)


        try {
            Camera2CameraControl.from(control).setCaptureRequestOptions(b.build())
        } catch (_: Throwable) {
        }
        if (!s.manualExposure && s.evMax > s.evMin) {
            control.setExposureCompensationIndex(s.ev.coerceIn(s.evMin, s.evMax))
        }
    }

    private fun snapshotMeterIfAuto() {
        val s = st
        if (s.manualExposure) return
        val iso = s.liveIso ?: s.iso
        val exp = s.liveShutterNs ?: s.shutterNs
        meteredExposure = iso.toDouble() * exp.toDouble() / evFactor(s)
        // Start the manual dials from what the meter chose.
        val isoIdx = Stops.nearestIndex(s.isoStops, iso) { a, b -> abs(ln(a.toDouble() / b)) }
        val expIdx = Stops.nearestIndex(s.shutterStops, exp) { a, b -> abs(ln(a.toDouble() / b)) }
        set {
            it.copy(
                iso = s.isoStops.getOrElse(isoIdx) { iso },
                shutterNs = s.shutterStops.getOrElse(expIdx) { exp },
            )
        }
    }

    // ---------------------------------------------------------------- public controls

    fun setMode(mode: CaptureMode) {
        val s = st
        if (s.recording || s.panoActive || s.stitching || s.mode == mode) return
        if (s.mode == CaptureMode.LIVE && live.state.value.isBusy) {
            toast("End the live stream first")
            return
        }
        stopInterval()
        if (s.mode == CaptureMode.LIVE) live.close()
        if (mode == CaptureMode.LIVE) {
            // Free the camera for the live streamer.
            owner?.let { o -> camera?.cameraInfo?.zoomState?.removeObservers(o) }
            provider?.unbindAll()
            camera = null
            imageCapture = null
            videoCapture = null
            imageAnalysis = null
        }
        if (mode.isSmart) lastSmartMode = mode else lastRegularMode = mode
        set {
            it.copy(
                mode = mode, activeParam = null, showFilters = false,
                slowFps = defaultSlowFps(mode, it.slowMoFps, it.superSlowMoFps, it.slowFallbackFps, it.slowFps),
            )
        }
        if (mode == CaptureMode.LIVE) {
            audioMeter.stopMonitoring()
            if (provider != null) live.open()
            set { it.copy(ready = true) }
            return
        }
        // Panorama needs the back camera.
        if (mode == CaptureMode.PANORAMA && s.isFront) {
            val back = s.lenses.indices.filter { s.lenses[it].facing == CameraSelector.LENS_FACING_BACK }
                .minByOrNull { abs(s.lenses[it].info.intrinsicZoomRatio - 1f) }
            if (back != null) {
                set { it.copy(lensIndex = back) }
                refreshCapabilities()
            }
        }
        bind()
    }

    /** Switch between the REGULAR and SMART mode groups, returning to the last mode used there. */
    fun showModeGroup(smart: Boolean) {
        if (st.mode.isSmart == smart) return
        setMode(if (smart) lastSmartMode else lastRegularMode)
    }

    /** Custom set-ups C1 / C2 / C3: hold to save the current settings, tap to bring them back. */
    fun savePreset(slot: Int) {
        CamPresets.save(context, slot, CamPreset.from(st))
        toast("C$slot saved")
    }

    fun recallPreset(slot: Int) {
        val p = CamPresets.load(context, slot)
        if (p == null) { toast("C$slot is empty — hold it to save your current settings"); return }
        if (st.recording) { toast("Stop recording first"); return }
        if (p.mode == CaptureMode.LIVE) return
        if (p.mode != st.mode) setMode(p.mode)
        if (p.lensIndex in st.lenses.indices && p.lensIndex != st.lensIndex) selectLens(p.lensIndex)
        set { s ->
            s.copy(
                manualIso = p.manualIso && s.manualSensor, manualShutter = p.manualShutter && s.manualSensor,
                iso = p.iso, shutterNs = p.shutterNs,
                manualFocus = p.manualFocus && s.minFocusDiopters > 0f, focusDiopters = p.focusDiopters,
                whiteBalance = p.whiteBalance, ev = p.ev.coerceIn(s.evMin, s.evMax),
                photoFormat = if (p.photoFormat == PhotoFormat.JPEG || s.rawSupported) p.photoFormat else PhotoFormat.JPEG,
                aspect = p.aspect, flash = if (s.hasFlash) p.flash else FlashSetting.OFF,
                timerSec = p.timerSec, burst = p.burst, grid = p.grid,
                zebras = p.zebras, zebraLevel = p.zebraLevel, peaking = p.peaking, falseColor = p.falseColor,
                videoRes = if (p.videoRes in s.videoResolutions) p.videoRes else s.videoRes,
                fps = if (p.fps in s.fpsOptions) p.fps else s.fps,
                stabilization = p.stabilization && s.stabilizationSupported,
                profile = if (p.profile == VideoProfile.HLG && !s.hlgSupported) VideoProfile.STANDARD else p.profile,
                highBitrate = p.highBitrate, logAssist = p.logAssist,
                videoCodec = if (p.videoCodec == VideoCodec.HEVC && !s.hevcSupported) VideoCodec.H264 else p.videoCodec,
                audioGainDb = p.audioGainDb, audioLimiter = p.audioLimiter,
                filter = p.filter, guide = p.guide,
                aeLock = false, aeLockPoint = null, activeParam = null,
            )
        }
        bind()
        applyControls()
        toast("C$slot")
    }

    fun selectLens(index: Int) {
        if (st.recording || index == st.lensIndex || index !in st.lenses.indices) return
        set { it.copy(lensIndex = index, manualFocus = false) }
        refreshCapabilities()
        bind()
    }

    fun flipCamera() {
        val s = st
        val target = if (s.isFront) {
            s.lenses.indices.filter { s.lenses[it].facing == CameraSelector.LENS_FACING_BACK }
                .minByOrNull { abs((s.lenses[it].info.intrinsicZoomRatio) - 1f) }
        } else {
            s.lenses.indexOfFirst { it.facing == CameraSelector.LENS_FACING_FRONT }.takeIf { it >= 0 }
        }
        target?.let { selectLens(it) }
    }

    fun setActiveParam(p: ProParam?) = set { it.copy(activeParam = if (it.activeParam == p) null else p) }

    fun setIso(iso: Int) {
        if (!st.manualSensor) return
        snapshotMeterIfAuto()
        set { it.copy(manualIso = true, iso = iso) }
        applyControls()
    }

    fun setShutter(ns: Long) {
        if (!st.manualSensor) return
        snapshotMeterIfAuto()
        set { it.copy(manualShutter = true, shutterNs = ns) }
        applyControls()
    }

    fun setIsoAuto() {
        set { it.copy(manualIso = false) }
        applyControls()
    }

    fun setShutterAuto() {
        set { it.copy(manualShutter = false) }
        applyControls()
    }

    fun setFocus(diopters: Float) {
        if (st.minFocusDiopters <= 0f) return
        set { it.copy(manualFocus = true, focusDiopters = diopters.coerceIn(0f, it.minFocusDiopters)) }
        applyControls()
    }

    private var rackJob: kotlinx.coroutines.Job? = null

    /** Rack focus: glide the manual focus to [target] diopters over [durMs] (eased). */
    fun rackFocus(target: Float, durMs: Long) {
        if (st.minFocusDiopters <= 0f) return
        rackJob?.cancel()
        val from = if (st.manualFocus) st.focusDiopters else (st.liveFocusDiopters ?: st.focusDiopters)
        rackJob = scope.launch {
            val t0 = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - t0).toFloat() / durMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                val e = p * p * (3f - 2f * p)
                setFocus(from + (target - from) * e)
                if (p >= 1f) break
                delay(33)
            }
        }
    }

    /**
     * Press and hold the viewfinder for 2 s: switch to manual focus, starting at the distance the
     * autofocus had found so nothing jumps. The focus slider on the right edge then takes over.
     */
    fun holdManualFocus(x: Float, y: Float) {
        val s = st
        if (s.mode == CaptureMode.LIVE) return
        if (s.sceneActive) { toast("Manual focus isn't available in this scene mode"); return }
        if (s.minFocusDiopters <= 0f) { toast("This lens has fixed focus"); return }
        rackJob?.cancel()
        val start = (if (s.manualFocus) s.focusDiopters else (s.liveFocusDiopters ?: s.focusDiopters))
            .coerceIn(0f, s.minFocusDiopters)
        try { camera?.cameraControl?.cancelFocusAndMetering() } catch (_: Throwable) {}
        set { it.copy(manualFocus = true, focusDiopters = start, focusPoint = x to y, focusId = it.focusId + 1) }
        applyControls()
        if (!s.manualFocus) toast("Manual focus · slide on the right edge · ✕ for auto")
    }

    fun setFocusAuto() {
        rackJob?.cancel()
        set { it.copy(manualFocus = false) }
        applyControls()
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    fun setWhiteBalance(wb: WhiteBalance) {
        set { it.copy(whiteBalance = wb, awbLock = false) }
        applyControls()
    }

    fun toggleAwbLock() {
        set { it.copy(awbLock = !it.awbLock) }
        applyControls()
    }

    fun toggleAeLock() {
        set { it.copy(aeLock = !it.aeLock, aeLockPoint = null) }
        applyControls()
    }

    /** Long press on the viewfinder: meter at that spot, then lock the exposure there. */
    fun lockExposureAt(x: Float, y: Float) {
        val s = st
        if (s.manualExposure) { toast("Exposure is manual — no lock needed"); return }
        set { it.copy(aeLock = false, aeLockPoint = x to y, focusPoint = x to y, focusId = it.focusId + 1) }
        applyControls()
        val cam = camera; val view = previewView
        if (cam != null && view != null) try {
            val point = view.meteringPointFactory.createPoint(x, y)
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    point,
                    if (s.manualFocusActive) FocusMeteringAction.FLAG_AE else FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AF,
                ).disableAutoCancel().build()
            )
        } catch (_: Throwable) {}
        // Let the exposure settle on the new spot, then hold it.
        scope.launch {
            delay(700)
            if (st.aeLockPoint == (x to y)) {
                set { it.copy(aeLock = true) }
                applyControls()
            }
        }
    }

    /** Long press again, or the small X: exposure follows the scene again. */
    fun unlockExposure() {
        set { it.copy(aeLock = false, aeLockPoint = null, ev = 0) }
        applyControls()
        try { camera?.cameraControl?.cancelFocusAndMetering() } catch (_: Throwable) {}
    }

    fun setEv(index: Int) {
        set { it.copy(ev = index.coerceIn(it.evMin, it.evMax)) }
        applyControls()
    }

    fun setZoom(ratio: Float) {
        val s = st
        val z = ratio.coerceIn(s.minZoom, s.maxZoom)
        set { it.copy(zoom = z) }
        camera?.cameraControl?.setZoomRatio(z)
    }

    fun setFlash(f: FlashSetting) {
        set { it.copy(flash = f) }
        applyControls()
    }

    fun setPhotoFormat(f: PhotoFormat) {
        if (st.photoFormat == f) return
        set { it.copy(photoFormat = f, scene = if (f != PhotoFormat.JPEG) Scene.STANDARD else it.scene) }
        bind()
    }

    fun setAspect(a: AspectMode) {
        val needsRebind = (a == AspectMode.R16_9) != (st.aspect == AspectMode.R16_9) || a == AspectMode.R1_1 || st.aspect == AspectMode.R1_1
        set { it.copy(aspect = a) }
        st.lens?.let { refreshPhotoSizes(it) }
        if (needsRebind || st.photoSize != null) bind()
    }

    fun setScene(scene: Scene) {
        if (st.scene == scene) return
        set {
            if (scene == Scene.STANDARD) it.copy(scene = scene)
            else it.copy(
                scene = scene,
                manualIso = false,
                manualShutter = false,
                manualFocus = false,
                photoFormat = PhotoFormat.JPEG,
            )
        }
        bind()
    }

    fun setTimer(sec: Int) = set { it.copy(timerSec = sec) }

    fun setBurst(count: Int) {
        val rebind = (count > 1) != (st.burst > 1)
        set { it.copy(burst = count) }
        if (rebind) bind()
    }

    fun setGrid(g: GridMode) = set { it.copy(grid = g) }
    /** Bumped after overlay positions are reset so draggable overlays return to defaults. */
    fun bumpLayoutVersion() = set { it.copy(layoutVersion = it.layoutVersion + 1) }
    fun toggleLevel() = set { it.copy(showLevel = !it.showLevel) }
    fun toggleHistogram() = set { it.copy(showHistogram = !it.showHistogram) }
    fun toggleZebras() = set { it.copy(zebras = !it.zebras) }
    fun setZebraLevel(level: Int) = set { it.copy(zebraLevel = level.coerceIn(70, 100)) }
    fun togglePeaking() = set { it.copy(peaking = !it.peaking) }
    fun toggleFalseColor() = set { it.copy(falseColor = !it.falseColor) }
    fun toggleVolumeShutter() = set { it.copy(volumeShutter = !it.volumeShutter) }

    fun toggleCameraSounds() {
        if (st.shutterSoundRequired) {
            toast("Shutter sound is required by law in this region")
            return
        }
        val on = !st.cameraSounds
        context.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putBoolean("camera_sounds", on).apply()
        set { it.copy(cameraSounds = on) }
    }

    private val prefs get() = context.getSharedPreferences("procam", Context.MODE_PRIVATE)

    // ---- codec, audio, clip naming, clean feed

    fun setVideoCodec(codec: VideoCodec) {
        if (st.recording || st.videoCodec == codec) return
        if (codec == VideoCodec.HEVC && !st.hevcSupported) {
            toast("This phone has no HEVC video encoder")
            return
        }
        prefs.edit().putString("video_codec", codec.name).apply()
        set { it.copy(videoCodec = codec) }
        if (st.isVideo) bind()
    }

    fun setAudioGain(db: Int) {
        val v = db.coerceIn(-12, 24)
        prefs.edit().putInt("audio_gain", v).apply()
        audioMeter.gainDb = v
        set { it.copy(audioGainDb = v) }
    }

    fun toggleAudioLimiter() {
        val on = !st.audioLimiter
        prefs.edit().putBoolean("audio_limiter", on).apply()
        audioMeter.limiter = on
        set { it.copy(audioLimiter = on) }
    }

    fun setClipNaming(n: ClipNaming) {
        prefs.edit().putString("clip_naming", n.name).apply()
        set { it.copy(clipNaming = n) }
    }

    fun setCameraLetter(c: Char) {
        prefs.edit().putString("camera_letter", c.toString()).apply()
        set { it.copy(cameraLetter = c) }
    }

    /** A new reel starts again at clip 1. */
    fun setReel(reel: Int) {
        val r = reel.coerceIn(1, 999)
        prefs.edit().putInt("reel", r).putInt("clip", 1).apply()
        set { it.copy(reel = r, clipNumber = 1) }
    }

    fun setCleanFeed(on: Boolean) = set { it.copy(cleanFeed = on) }

    /** Base file name (no extension) for a new video clip, advancing the clip counter in cinema naming. */
    private fun clipName(suffix: String = ""): String {
        val s = st
        if (s.clipNaming != ClipNaming.CINEMA) return stamp() + suffix
        val date = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val name = "%c%03dC%03d_%s_ACLX".format(Locale.US, s.cameraLetter, s.reel, s.clipNumber, date)
        val next = (s.clipNumber + 1).coerceAtMost(999)
        prefs.edit().putInt("clip", next).apply()
        set { it.copy(clipNumber = next) }
        return name + suffix
    }

    /** Plays a camera sound unless the user muted them (never muted where the law requires it). */
    private fun playSound(type: Int) {
        if (st.cameraSounds || st.shutterSoundRequired) sound.play(type)
    }
    fun toggleAudio() = set { it.copy(audio = !it.audio) }

    fun setVideoRes(r: VideoRes) {
        if (st.recording || st.videoRes == r) return
        set { it.copy(videoRes = r) }
        bind()
    }

    fun setFps(fps: Int) {
        if (st.recording || st.fps == fps) return
        set { it.copy(fps = fps) }
        bind()
    }

    fun toggleStabilization() {
        if (st.recording) return
        set { it.copy(stabilization = !it.stabilization) }
        bind()
    }

    fun toggleBitrate() {
        if (st.recording) return
        set { it.copy(highBitrate = !it.highBitrate) }
        bind()
    }

    fun setProfile(p: VideoProfile) {
        val s = st
        if (s.recording || s.profile == p) return
        val rebind = p == VideoProfile.HLG || s.profile == VideoProfile.HLG ||
            needsEffect(s) != needsEffect(s.copy(profile = p))
        set { it.copy(profile = p) }
        filterProcessor.setCurve(logCurve(st), st.logAssist)
        if (rebind) bind() else applyControls()
    }

    fun tapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        val view = previewView ?: return
        val s = st
        set { it.copy(focusPoint = x to y, focusId = it.focusId + 1) }
        var flags = 0
        if (!s.manualFocusActive) flags = flags or FocusMeteringAction.FLAG_AF
        if (!s.manualExposure && !s.aeLock) flags = flags or FocusMeteringAction.FLAG_AE
        if (!(s.isPro && (s.whiteBalance != WhiteBalance.AUTO || s.awbLock))) flags = flags or FocusMeteringAction.FLAG_AWB
        if (flags == 0) return
        try {
            val point = view.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point, flags)
                .setAutoCancelDuration(5, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------- capture

    fun shutter() {
        val s = st
        if (s.mode == CaptureMode.LIVE) {
            live.toggleLive()
            return
        }
        if (!s.ready || s.stitching) return
        if (s.countdown > 0) {
            cancelCountdown()
            return
        }
        if (s.mode.isLapse) {
            if (s.recording) stopLapse()
            else if (s.timerSec > 0) {
                captureJob = scope.launch {
                    for (i in s.timerSec downTo 1) {
                        set { it.copy(countdown = i) }
                        delay(1000)
                    }
                    set { it.copy(countdown = 0) }
                    startLapse()
                }
            } else startLapse()
            return
        }
        if (s.isVideo) {
            if (s.recording) {
                stopRecording()
            } else if (s.timerSec > 0) {
                captureJob = scope.launch {
                    for (i in s.timerSec downTo 1) {
                        set { it.copy(countdown = i) }
                        delay(1000)
                    }
                    set { it.copy(countdown = 0) }
                    startRecording()
                }
            } else {
                startRecording()
            }
            return
        }
        if (s.mode == CaptureMode.PANORAMA) {
            if (s.panoActive) finishPanorama() else startPanorama()
            return
        }
        if (s.intervalRunning) {
            stopInterval()
            return
        }
        if (s.capturing) return
        if (s.intervalSec > 0) {
            startInterval()
            return
        }
        captureJob = scope.launch {
            if (s.timerSec > 0) {
                for (i in s.timerSec downTo 1) {
                    set { it.copy(countdown = i) }
                    delay(1000)
                }
                set { it.copy(countdown = 0) }
            }
            set { it.copy(capturing = true) }
            val saved = captureSet(s)
            set { it.copy(capturing = false) }
            if (saved > 1) toast("$saved photos saved")
        }
    }

    /** One press of the shutter: a single shot, a burst, or an exposure bracket. */
    private suspend fun captureSet(s: CameraUiState): Int {
        var saved = 0
        if (s.mode == CaptureMode.BURST) {
            repeat(s.burstModeCount) { if (takePhoto()) saved++ }
            return saved
        }
        if (s.mode == CaptureMode.PRO_PHOTO && s.bracket != Bracket.OFF) {
            val n = s.bracket.stops
            for (stops in listOf(-n, 0, n)) {
                applyBracketStep(s, stops)
                delay(if (s.manualExposure) 250 else 600)
                if (takePhoto()) saved++
            }
            applyBracketStep(s, 0)
            applyControls()
            return saved
        }
        repeat(max(1, s.burst)) { if (takePhoto()) saved++ }
        return saved
    }

    private fun applyBracketStep(s: CameraUiState, stops: Int) {
        val cam = camera ?: return
        if (s.manualExposure && s.manualSensor) {
            val (iso, exp) = effectiveExposure(s)
            val factor = 2.0.pow(stops.toDouble())
            val newExp = (exp * factor).toLong().coerceIn(exposureRange.lower, exposureRange.upper)
            val b = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, newExp)
                .setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, max(newExp, 33_333_333L))
            try { Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build()) } catch (_: Throwable) {}
        } else if (s.evMax > s.evMin) {
            val idx = (s.ev + Math.round(stops / s.evStep)).coerceIn(s.evMin, s.evMax)
            cam.cameraControl.setExposureCompensationIndex(idx)
        }
    }

    // ---------------------------------------------------------------- interval (time-lapse photos)

    private fun startInterval() {
        val s = st
        set { it.copy(intervalRunning = true, intervalCount = 0) }
        captureJob = scope.launch {
            while (st.intervalRunning) {
                set { it.copy(capturing = true) }
                val n = captureSet(st)
                set { it.copy(capturing = false, intervalCount = it.intervalCount + n) }
                for (i in s.intervalSec downTo 1) {
                    if (!st.intervalRunning) break
                    set { it.copy(countdown = 0) }
                    delay(1000)
                }
            }
        }
    }

    private fun stopInterval() {
        if (!st.intervalRunning) return
        captureJob?.cancel()
        captureJob = null
        val count = st.intervalCount
        set { it.copy(intervalRunning = false, capturing = false) }
        toast("Interval finished: $count photos")
    }

    fun setInterval(sec: Int) {
        stopInterval()
        set { it.copy(intervalSec = sec) }
    }

    fun setBracket(b: Bracket) = set { it.copy(bracket = b) }
    fun setGuide(g: FrameGuide) = set { it.copy(guide = g) }

    // ---------------------------------------------------------------- filters

    fun toggleFilterStrip() = set { it.copy(showFilters = !it.showFilters, activeParam = null) }

    private fun logCurve(s: CameraUiState): Int =
        if (s.mode == CaptureMode.PRO_VIDEO && s.profile.isLog) s.profile.curve else 0

    /** The GPU effect is needed for a colour filter or a log profile. HLG (10-bit) can't go through it. */
    private fun needsEffect(s: CameraUiState): Boolean {
        if (s.mode.isLapse) return true
        if (s.mode.isSlowMo) return false
        if (s.mode == CaptureMode.PRO_VIDEO && s.profile == VideoProfile.HLG && s.hlgSupported) return false
        return s.filter != Filter.NONE || logCurve(s) != 0 || filterProcessor.hasLut || CaptureExtras.faceBlur
    }

    /** Title overlay (Settings → TITLE): burned into the finished clip. */
    private suspend fun burnTitle(uri: Uri) {
        val ok = com.dronedirector.veyrocutpro.editor.TitleBurn.apply(context, uri, CaptureExtras.titleText, CaptureExtras.titlePos, CaptureExtras.titleSec)
        if (!ok) toast("Couldn't add the title; the clip was saved without it")
    }

    /** Privacy face blur on / off (needs the live filter, so the camera is set up again). */
    fun setFaceBlur(on: Boolean) {
        val before = needsEffect(st)
        CaptureExtras.faceBlur = on
        CaptureExtras.save(context)
        if (!on) FaceBlur.clear()
        if (needsEffect(st) != before) {
            if (st.recording) { toast("Face blur changes after this recording"); return }
            bind()
        }
    }

    /** Applies the Capture monitoring LUT (settings) to the viewfinder and, if chosen, recordings. */
    fun refreshMonitorLut() {
        val s = st
        val before = needsEffect(s)
        val strip = CaptureExtras.monitorLut?.let { com.dronedirector.veyrocutpro.editor.LutLibrary.get(it) }?.strip(33)
        filterProcessor.setLut(strip, toPreview = true, toVideo = CaptureExtras.bakeLut)
        if (needsEffect(s) != before) {
            if (s.recording) { toast("The LUT applies after this recording"); return }
            bind()
        }
    }

    fun toggleLogAssist() {
        set { it.copy(logAssist = !it.logAssist) }
        filterProcessor.setCurve(logCurve(st), st.logAssist)
    }

    fun setFilter(f: Filter) {
        val s = st
        if (s.filter == f) return
        val rebind = needsEffect(s) != needsEffect(s.copy(filter = f))
        if (rebind && s.recording) {
            toast("Pick a filter before you start recording")
            return
        }
        filterProcessor.setFilter(f)
        set { it.copy(filter = f) }
        if (rebind) bind()
    }

    // ---------------------------------------------------------------- storage

    fun refreshStorage() {
        val options = Storage.options(context)
        val volume = Storage.load(context)
        if (volume != Storage.INTERNAL && !Storage.available(context, volume)) {
            // Keep the choice (the drive may come back); saving falls back to internal storage meanwhile.
            toast(if (Storage.isTree(volume)) "Save folder not found (drive unplugged?) — saving to internal storage for now"
            else "SD card / drive not found — saving to internal storage for now")
        }
        set { it.copy(storageOptions = options, storageVolume = volume) }
    }

    fun setStorage(volume: String) {
        Storage.save(context, volume)
        set { it.copy(storageVolume = volume) }
        refreshStorage()
    }

    // ---------------------------------------------------------------- video snapshot

    /** Saves the current (filtered) viewfinder frame as a photo, e.g. while recording. */
    fun snapshot() {
        val bmp = try { previewView?.bitmap } catch (_: Throwable) { null } ?: return
        val name = stamp() + "_snap.jpg"
        val volume = st.storageVolume
        set { it.copy(shutterFlash = it.shutterFlash + 1) }
        scope.launch {
            val uri = withContext(Dispatchers.IO) { saveBitmap(bmp, name, volume) }
            bmp.recycle()
            if (uri != null) {
                set { it.copy(lastMedia = uri) }
                toast("Snapshot saved")
            } else toast("Couldn't save snapshot")
        }
    }

    private fun saveBitmap(bmp: Bitmap, fileName: String, volume: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/VeyroCutPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = Storage.insert(context, volume, values, Storage.Kind.IMAGE) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            if (Build.VERSION.SDK_INT >= 29) {
                Storage.publish(context, uri)
            }
            uri
        } catch (_: Throwable) {
            Storage.discard(context, uri)
            null
        }
    }

    // ---------------------------------------------------------------- time-lapse & hyperlapse

    fun setLapseInterval(ms: Int) = set { it.copy(lapseIntervalMs = ms) }
    fun setLapseLimit(min: Int) = set { it.copy(lapseLimitMin = min) }
    fun setHyperSpeed(x: Int) = set { it.copy(hyperSpeed = x) }

    fun toggleLapseStabilize() {
        if (st.recording) return
        set { it.copy(lapseStabilize = !it.lapseStabilize) }
        bind()
    }

    private fun startLapse() {
        val s = st
        val cam = camera ?: return
        val size = filterProcessor.inputSize
        if (size == null || !s.ready) {
            toast("${s.mode.label} isn't available right now")
            return
        }
        val w = size.width and 1.inv()
        val h = size.height and 1.inv()
        val hint = try { cam.cameraInfo.getSensorRotationDegrees(rotation) } catch (_: Throwable) { 90 }
        val hyper = s.mode == CaptureMode.HYPERLAPSE
        val rec = try {
            LapseRecorder(
                context, w, h, hint, s.storageVolume,
                "${clipName("_" + if (hyper) "hyperlapse" else "timelapse")}.mp4",
                intervalMs = s.lapseIntervalMs,
                everyNth = if (hyper) s.hyperSpeed else 0,
                zoom = if (hyper && s.lapseStabilize) 0.9f else 1f,
                onFrame = { n -> _state.update { it.copy(lapseFrames = n) } },
                hevc = s.videoCodec == VideoCodec.HEVC,
            )
        } catch (t: Throwable) {
            toast("Couldn't start: ${t.message}")
            return
        }
        lapseRecorder = rec
        filterProcessor.lapse = rec
        playSound(MediaActionSound.START_VIDEO_RECORDING)
        set { it.copy(recording = true, paused = false, recordSeconds = 0, lapseFrames = 0) }
        lapseTicker = scope.launch {
            var sec = 0L
            while (st.recording) {
                delay(1000)
                sec++
                set { it.copy(recordSeconds = sec) }
                val limit = st.lapseLimitMin
                if (limit > 0 && sec >= limit * 60L) {
                    stopLapse()
                    break
                }
            }
        }
    }

    private fun stopLapse() {
        val rec = lapseRecorder ?: return
        lapseRecorder = null
        filterProcessor.lapse = null
        lapseTicker?.cancel()
        playSound(MediaActionSound.STOP_VIDEO_RECORDING)
        val frames = rec.frameCount
        set { it.copy(recording = false, processing = true) }
        rec.stop { uri ->
            set { it.copy(processing = false, lastMedia = uri ?: it.lastMedia) }
            toast(
                if (uri != null) "Saved · $frames frames → %.1f s of video".format(frames / 30f)
                else "Nothing recorded — keep recording a little longer"
            )
        }
    }

    // ---------------------------------------------------------------- slow motion

    fun setSlowFps(fps: Int) {
        if (st.recording || st.slowFps == fps) return
        set { it.copy(slowFps = fps) }
        bind()
    }

    private fun bindSlowMo(p: ProcessCameraProvider, owner: LifecycleOwner, selector: CameraSelector, s: CameraUiState, preview: Preview) {
        val lens = s.lens ?: return
        val fps = s.slowFps
        val highSpeed = fps >= 100
        var bound: Camera? = null
        var vc: VideoCapture<Recorder>? = null
        try {
            if (highSpeed) {
                val caps = Recorder.getHighSpeedVideoCapabilities(lens.info)
                val qualities = caps?.getSupportedQualities(DynamicRange.SDR).orEmpty()
                val order = listOf(Quality.FHD, Quality.HD, Quality.SD).filter { it in qualities }.ifEmpty { listOf(Quality.HD) }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.fromOrderedList(order, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                vc = VideoCapture.withOutput(recorder)
                val config = HighSpeedVideoSessionConfig(vc, preview, Range(fps, fps), true)
                p.unbindAll()
                bound = p.bindToLifecycle(owner, selector, config)
            } else if (fps > 0) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
                    .build()
                vc = VideoCapture.Builder(recorder).setTargetFrameRate(Range(fps, fps)).setTargetRotation(rotation).build()
                p.unbindAll()
                bound = p.bindToLifecycle(owner, selector, preview, vc)
            } else {
                p.unbindAll()
                bound = p.bindToLifecycle(owner, selector, preview)
                toast(if (s.mode == CaptureMode.SUPER_SLOW_MO) "This camera can't film at 240 fps" else "This camera can't film fast enough for slow motion")
            }
        } catch (t: Throwable) {
            try {
                p.unbindAll()
                bound = p.bindToLifecycle(owner, selector, preview)
            } catch (_: Throwable) {}
            vc = null
            toast("Slow motion isn't available: ${t.message}")
        }
        camera = bound
        videoCapture = vc
        imageCapture = null
        imageAnalysis = null
        _analysis.value = null
        bound?.cameraInfo?.zoomState?.observe(owner) { z ->
            _state.update { it.copy(zoom = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
        }
        set { it.copy(ready = bound != null) }
        applyControls()
    }

    // ---------------------------------------------------------------- burst mode

    fun setBurstModeCount(n: Int) = set { it.copy(burstModeCount = n) }

    /** BURST mode: keep shooting while the shutter is held (up to 100 frames). */
    fun startHoldBurst() {
        if (st.mode != CaptureMode.BURST || st.capturing || holdBurstJob != null) return
        set { it.copy(holdBursting = true, capturing = true) }
        holdBurstJob = scope.launch {
            var n = 0
            while (st.holdBursting && n < 100) {
                if (takePhoto()) n++
            }
            set { it.copy(holdBursting = false, capturing = false) }
            holdBurstJob = null
            toast("$n photos saved")
        }
    }

    fun stopHoldBurst() = set { it.copy(holdBursting = false) }

    // ---------------------------------------------------------------- panorama

    private fun startPanorama() {
        if (imageCapture == null) return
        panoFrames.forEach { it.recycle() }
        panoFrames.clear()
        panoAngles.clear()
        panoAccum = 0f
        panoBusy = false
        panoTurn = 0f; panoLastShot = 0f
        panoFovLong = cameraFovLong()
        // Frames are taken every ~third of the frame's width (horizontal field of view of the upright frame).
        val landscape = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
        val hfov = if (landscape) panoFovLong else Math.toDegrees(2 * atan(tan(Math.toRadians(panoFovLong / 2.0)) * 0.75)).toFloat()
        panoStepDeg = (hfov * Panorama.STEP).coerceIn(6f, 40f)
        set { it.copy(panoActive = true, panoFrames = 0, panoDirection = 0, panoProgress = 0f) }
        applyControls() // lock exposure and white balance so frames match
        capturePanoFrame()
        val gyro = PanoGyro(context) { deg -> mainExecutor.execute { onPanoTurn(deg) } }
        if (gyro.start()) { panoGyro = gyro; return }
        if (imageAnalysis == null) {
            // No motion tracking available: take frames on a steady rhythm instead.
            panoTimerJob = scope.launch {
                while (st.panoActive) {
                    delay(1300)
                    if (st.panoDirection == 0) set { it.copy(panoDirection = 1) }
                    capturePanoFrame()
                }
            }
        }
    }

    /** Camera's field of view across the long side of the picture, at the current zoom. */
    private fun cameraFovLong(): Float = try {
        val c2 = Camera2CameraInfo.from(camera!!.cameraInfo)
        val f = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!.first()
        val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
        val z = st.zoom.coerceAtLeast(0.3f)
        Math.toDegrees(2 * atan(max(size.width, size.height) / (2.0 * f * z))).toFloat().coerceIn(30f, 130f)
    } catch (_: Throwable) { 65f }

    /** Gyroscope: total turn in degrees (right = positive). */
    private fun onPanoTurn(deg: Float) {
        val s = st
        if (!s.panoActive) return
        panoTurn = deg
        if (s.panoDirection == 0) {
            if (abs(deg) > 3f) set { it.copy(panoDirection = if (deg > 0) 1 else -1) }
            return
        }
        val turned = deg * s.panoDirection
        set { it.copy(panoProgress = (turned / Panorama.TARGET_DEG).coerceIn(0f, 1f)) }
        if (!panoBusy && turned - panoLastShot >= panoStepDeg) {
            capturePanoFrame()
        }
        if (turned >= Panorama.TARGET_DEG + panoStepDeg * 0.5f && !panoBusy) finishPanorama()
    }

    private fun onPanoMotion(dx: Float) {
        val s = st
        if (!s.panoActive || panoBusy || panoGyro != null) return
        // Ignore jitter until a clear direction appears.
        if (s.panoDirection == 0) {
            panoAccum += dx
            if (abs(panoAccum) > 0.06f) set { it.copy(panoDirection = if (panoAccum > 0) 1 else -1) }
            return
        }
        panoAccum += dx * s.panoDirection
        if (panoAccum < -0.2f) panoAccum = -0.2f
        set { it.copy(panoProgress = ((panoFrames.size - 1) + (panoAccum / Panorama.STEP).coerceIn(0f, 1f)) / (Panorama.MAX_FRAMES - 1)) }
        if (panoAccum >= Panorama.STEP) {
            panoAccum -= Panorama.STEP
            capturePanoFrame()
        }
    }

    private fun capturePanoFrame() {
        val ic = imageCapture ?: return
        if (panoBusy || !st.panoActive) return
        panoBusy = true
        val angle = panoTurn
        panoLastShot = angle * (if (st.panoDirection == 0) 1 else st.panoDirection)
        playSound(MediaActionSound.FOCUS_COMPLETE)
        ic.takePicture(ioExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val frame = try { Panorama.prepareFrame(image.toBitmap(), rot) } catch (_: Throwable) { null }
                image.close()
                mainExecutor.execute {
                    panoBusy = false
                    if (frame == null || !st.panoActive) { frame?.recycle(); return@execute }
                    panoFrames += frame
                    panoAngles += angle
                    val n = panoFrames.size
                    if (panoGyro == null) set { it.copy(panoFrames = n, panoProgress = (n - 1f) / (Panorama.MAX_FRAMES - 1)) }
                    else set { it.copy(panoFrames = n) }
                    if (n >= Panorama.MAX_FRAMES) finishPanorama()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                mainExecutor.execute { panoBusy = false }
            }
        })
    }

    private fun finishPanorama() {
        panoTimerJob?.cancel()
        panoTimerJob = null
        val hadGyro = panoGyro != null
        panoGyro?.stop(); panoGyro = null
        val frames = ArrayList(panoFrames)
        val angles = if (hadGyro) ArrayList(panoAngles) else null
        val fovLong = panoFovLong
        panoFrames.clear(); panoAngles.clear()
        val direction = st.panoDirection
        val filter = st.filter
        val volume = st.storageVolume
        set { it.copy(panoActive = false, stitching = frames.size >= 2, panoProgress = 0f) }
        applyControls() // release the exposure lock
        if (frames.size < 2) {
            frames.forEach { it.recycle() }
            toast("Turn slowly to the side after tapping the shutter")
            return
        }
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                val pano = try { Panorama.stitch(frames, angles, fovLong, direction, filter) } catch (_: Throwable) { null }
                frames.forEach { it.recycle() }
                pano?.let { p -> saveBitmap(p, stamp() + "_pano.jpg", volume).also { p.recycle() } }
            }
            set { it.copy(stitching = false, lastMedia = uri ?: it.lastMedia) }
            playSound(MediaActionSound.SHUTTER_CLICK)
            toast(if (uri != null) "Panorama saved (${frames.size} frames)" else "Couldn't build the panorama")
        }
    }

    /** Tapping the shutter again during the self-timer cancels it. */
    fun cancelCountdown() {
        captureJob?.cancel()
        captureJob = null
        set { it.copy(countdown = 0, capturing = false) }
    }

    private fun stamp(): String = "PRO_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    /** Photos for a picked folder are first written to a temp file, then copied there. */
    private val treePending = java.util.concurrent.ConcurrentHashMap<String, Triple<String, String, String>>()

    private fun photoOptions(fileName: String, mime: String): ImageCapture.OutputFileOptions {
        val place = Storage.resolve(context, st.storageVolume)
        if (Storage.isTree(place)) {
            val tmp = java.io.File(context.cacheDir, "cap_" + fileName)
            treePending[tmp.path] = Triple(place, fileName, mime)
            return ImageCapture.OutputFileOptions.Builder(tmp).build()
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/VeyroCutPro")
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            Storage.imagesUri(place),
            values,
        ).build()
    }

    /**
     * A photo CameraX wrote to a temp file goes into the picked folder: the file is created now,
     * the copy runs first on the (single) save thread, so the look / copyright / face blur steps
     * queued after it see the finished photo.
     */
    private fun moveToFolder(fileUri: Uri, showWhenDone: Boolean): Uri? {
        val tmp = java.io.File(fileUri.path ?: return null)
        val (place, name, mime) = treePending.remove(tmp.path) ?: return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/VeyroCutPro"); put(MediaStore.MediaColumns.IS_PENDING, 1) }
        }
        val target = Storage.insert(context, place, values, Storage.Kind.IMAGE) ?: run { tmp.delete(); toast("Couldn't save the photo"); return null }
        ioExecutor.execute {
            try {
                context.contentResolver.openOutputStream(target, "w")?.use { o -> tmp.inputStream().use { it.copyTo(o, 1 shl 16) } }
                Storage.publish(context, target)
                if (showWhenDone) set { it.copy(lastMedia = target) }
            } catch (_: Throwable) {
                toast("Couldn't write the photo to the drive")
            } finally { tmp.delete() }
        }
        return target
    }

    private suspend fun takePhoto(): Boolean {
        val ic = imageCapture ?: return false
        val format = ic.outputFormat
        val filter = st.filter
        val aiMp = if (UpscaleSettings.enabled && !st.isVideo) st.photoSize?.aiMp ?: 0 else 0
        val portrait = st.mode.isPortrait
        val name = stamp()
        playSound(MediaActionSound.SHUTTER_CLICK)
        set { it.copy(shutterFlash = it.shutterFlash + 1) }

        return suspendCancellableCoroutine { cont ->
            val expected = if (format == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) 2 else 1
            var count = 0
            var ok = false
            val callback = object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    count++
                    ok = true
                    val saved = output.savedUri
                    val toFolder = saved?.scheme == "file"
                    val uri = if (toFolder) saved?.let { moveToFolder(it, showWhenDone = expected == 1 || output.imageFormat != ImageFormat.RAW_SENSOR) } else saved
                    val isJpeg = output.imageFormat != ImageFormat.RAW_SENSOR && format != ImageCapture.OUTPUT_FORMAT_RAW
                    if (uri != null && isJpeg && filter != Filter.NONE) {
                        ioExecutor.execute { Filter.applyToSavedJpeg(context, uri, filter) }
                    }
                    // Your name / copyright in the photo's details.
                    if (uri != null && isJpeg) ioExecutor.execute { Copyright.stampUri(context, uri) }
                    // Slate: scene / shot / take in the photo, and a line in the shot log.
                    if (uri != null && isJpeg && CaptureExtras.slateOn) { val snap = st; ioExecutor.execute { Slate.stampPhoto(context, uri); Slate.log(context, uri, snap, false) } }
                    // Privacy: blur faces in the saved photo.
                    if (uri != null && isJpeg && CaptureExtras.faceBlur) {
                        ioExecutor.execute { if (!FaceBlur.applyToSavedJpeg(context, uri)) toast("Couldn't blur the faces in this photo") }
                    }
                    // Portrait: blur the background (after face blur, before the extra copies).
                    if (uri != null && isJpeg && portrait) {
                        set { it.copy(processing = true) }
                        val f = if (CaptureExtras.portraitAuto) Portrait.AUTO else CaptureExtras.portraitF; val keep = CaptureExtras.portraitKeepOriginal
                        ioExecutor.execute {
                            val out = try { Portrait.applyToSaved(context, uri, f, keep) } catch (_: Throwable) { null }
                            set { it.copy(processing = false, lastMedia = out?.first ?: it.lastMedia) }
                            toast(if (out != null) "Portrait saved (${if (f <= 0f) "auto " else ""}${Portrait.label(out.second)})" else "No person or subject found — saved without background blur")
                        }
                    }
                    // Auto-enhance: an extra, improved copy next to the original.
                    if (uri != null && isJpeg && CaptureExtras.autoEnhance) {
                        ioExecutor.execute { com.dronedirector.veyrocutpro.editor.AutoEnhance.saveCopy(context, uri) }
                    }
                    // Optional AI upscale (after any look has been applied to the saved JPEG).
                    if (uri != null && isJpeg && aiMp > 0) {
                        ioExecutor.execute { Upscaler.enqueue(context, uri, aiMp) }
                    }
                    if (uri != null && !toFolder && (expected == 1 || isJpeg)) {
                        set { it.copy(lastMedia = uri) }
                    }
                    if (count >= expected && cont.isActive) cont.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    count++
                    toast("Capture failed: ${exception.message ?: exception.imageCaptureError}")
                    if (count >= expected && cont.isActive) cont.resume(ok)
                }
            }
            try {
                when (format) {
                    ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> ic.takePicture(
                        photoOptions("$name.dng", "image/x-adobe-dng"),
                        photoOptions("$name.jpg", "image/jpeg"),
                        mainExecutor,
                        callback,
                    )
                    ImageCapture.OUTPUT_FORMAT_RAW ->
                        ic.takePicture(photoOptions("$name.dng", "image/x-adobe-dng"), mainExecutor, callback)
                    else ->
                        ic.takePicture(photoOptions("$name.jpg", "image/jpeg"), mainExecutor, callback)
                }
            } catch (t: Throwable) {
                toast("Capture failed: ${t.message}")
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        // Pre-record running: the clip simply continues from the buffer.
        val pb = preBuffer
        if (pb != null && preUserNs < 0) {
            preUserNs = preElapsedNs
            preJob?.cancel()
            recording = pb
            recordingHasAudio = preHasAudio
            if (!monitorWhileRecording) audioMeter.stopMonitoring()
            playSound(MediaActionSound.START_VIDEO_RECORDING)
            set { it.copy(recording = true, paused = false, recordSeconds = CaptureExtras.preRecordSec.toLong()) }
            return
        }
        val vc = videoCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${clipName()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/VeyroCutPro")
        }
        if (!monitorWhileRecording) audioMeter.stopMonitoring()
        monitorSilencedWarned = false
        val s0 = st
        // Slow motion without high-speed support: film at 60 fps, then stretch to half speed.
        val stretch = s0.mode.isSlowMo && s0.slowFps in 1..99
        var pending = if (stretch) {
            val file = java.io.File(context.cacheDir, "slowmo_${System.currentTimeMillis()}.mp4")
            slowMoFile = file
            vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        } else {
            slowMoFile = null
            val place = Storage.resolve(context, st.storageVolume)
            val doc = if (Storage.isTree(place)) Storage.createInTree(context, place, "video/mp4", values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)) else null
            val pfd = doc?.let { try { context.contentResolver.openFileDescriptor(it, "rw") } catch (_: Throwable) { null } }
            if (doc != null && pfd != null) {
                // Straight onto the drive (no copy afterwards, no internal space needed).
                recordDoc = doc; recordPfd = pfd
                vc.output.prepareRecording(context, FileDescriptorOutputOptions.Builder(pfd).build())
            } else {
                if (doc != null) { Storage.discard(context, doc); toast("Couldn't write to the save folder — recording to internal storage") }
                val output = MediaStoreOutputOptions.Builder(context.contentResolver, Storage.videosUri(if (Storage.isTree(place)) Storage.INTERNAL else place))
                    .setContentValues(values)
                    .build()
                vc.output.prepareRecording(context, output)
            }
        }
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        recordingHasAudio = s0.mode.isNormalVideo && st.audio && micGranted
        if (recordingHasAudio) { btMic(CaptureExtras.micBluetooth); pending = pending.withAudioEnabled() }
        if (s0.mode.isNormalVideo && st.audio && !micGranted) toast("Recording without sound (microphone not allowed)")
        playSound(MediaActionSound.START_VIDEO_RECORDING)
        try {
            recording = pending.start(mainExecutor) { event -> onRecordEvent(event) }
        } catch (t: Throwable) {
            toast("Couldn't start recording: ${t.message}")
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        playSound(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    fun setAudioMonitoring(on: Boolean) {
        if (on) audioMeter.startMonitoring() else audioMeter.stopMonitoring()
    }

    fun pauseResume() {
        val r = recording ?: return
        if (st.paused) r.resume() else r.pause()
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> set { it.copy(recording = true, paused = false, recordSeconds = 0) }
            is VideoRecordEvent.Pause -> set { it.copy(paused = true) }
            is VideoRecordEvent.Resume -> set { it.copy(paused = false) }
            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                val amp = try { stats.audioStats.audioAmplitude.toFloat() } catch (_: Throwable) { 0f }
                val audioState = try { stats.audioStats.audioState } catch (_: Throwable) { -1 }
                if (audioMeter.isRunning && audioState == androidx.camera.video.AudioStats.AUDIO_STATE_SOURCE_SILENCED) {
                    // Android gave the microphone to the monitor instead of the recording: hand it back.
                    audioMeter.stopMonitoring()
                    toast("Headphone monitoring paused: this phone can't share the microphone while recording")
                }
                if (audioMeter.isRunning && !audioMeter.silenced) {
                    // The headphone monitor already feeds the meter with the gain applied.
                } else {
                    if (audioMeter.isRunning && audioMeter.silenced && !monitorSilencedWarned) {
                        monitorSilencedWarned = true
                        toast("This phone mutes headphone monitoring while recording; the clip still records sound")
                    }
                    // Show the level with the input gain applied, matching the finished clip.
                    audioMeter.pushAmplitude((amp * 10f.pow(st.audioGainDb / 20f)).coerceAtMost(1f))
                }
                set { it.copy(recordSeconds = stats.recordedDurationNanos / 1_000_000_000L, audioLevel = amp) }
            }
            is VideoRecordEvent.Finalize -> {
                recording = null
                btMic(false)
                scope.launch { delay(400); startPreBuffer() }
                val slowFile = slowMoFile
                slowMoFile = null
                if (slowFile != null) {
                    val factor = st.slowFps / 30.0
                    val volume = st.storageVolume
                    set { it.copy(recording = false, paused = false, processing = true) }
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            SlowMotionRemux.slowDown(context, slowFile, factor, volume, "${clipName("_slowmo")}.mp4")
                        }
                        set { it.copy(processing = false, lastMedia = out ?: it.lastMedia) }
                        toast(if (out != null) "Slow-motion video saved (%.0f× slower)".format(factor) else "Couldn't save the slow-motion video")
                    }
                    videoCapture?.setTargetRotation(rotation)
                    return
                }
                val doc = recordDoc
                recordDoc = null
                recordPfd?.let { try { it.close() } catch (_: Throwable) {} }
                recordPfd = null
                val uri = if (doc != null && !(event.hasError() && event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA)) doc
                else { doc?.let { Storage.discard(context, it) }; event.outputResults.outputUri }
                set {
                    it.copy(
                        recording = false,
                        paused = false,
                        audioLevel = 0f,
                        lastMedia = if (uri != Uri.EMPTY) uri else it.lastMedia,
                    )
                }
                if (event.hasError()) {
                    toast(if (uri != Uri.EMPTY) "Video saved (with warning ${event.error})" else "Recording failed (error ${event.error})")
                }
                // Shot log (and the next take number).
                if (uri != Uri.EMPTY && CaptureExtras.slateOn) { val snap = st; ioExecutor.execute { Slate.log(context, uri, snap, true) } }
                // Manual input gain: re-encode the sound of the finished clip.
                val gainDb = st.audioGainDb
                val wind = CaptureExtras.windFilter
                val nr = CaptureExtras.noiseReduce
                val doAudio = recordingHasAudio && (gainDb != 0 || wind || nr > 0)
                val doTitle = CaptureExtras.titleOn && CaptureExtras.titleText.isNotBlank()
                if ((doAudio || doTitle) && uri != Uri.EMPTY) {
                    val limiter = st.audioLimiter
                    set { it.copy(processing = true) }
                    scope.launch {
                        if (doAudio) {
                            val ok = withContext(Dispatchers.IO) { AudioGain.apply(context, uri, gainDb, limiter, wind, nr) }
                            if (!ok) toast("Couldn't process the sound; the clip was saved without it")
                        }
                        if (doTitle) burnTitle(uri)
                        set { it.copy(processing = false) }
                    }
                }
                // Apply any rotation change that happened while recording.
                videoCapture?.setTargetRotation(rotation)
            }
        }
    }
}

private inline fun <reified E : Enum<E>> prefEnum(context: Context, key: String, default: E): E {
    val name = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString(key, null) ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

@OptIn(androidx.camera.video.ExperimentalMimeTypeApi::class)
private fun hevcAvailable(): Boolean =
    try { Recorder.getSupportedVideoMimeTypes().contains(VideoCodec.HEVC.mime) } catch (_: Throwable) { false }
