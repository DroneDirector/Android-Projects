package com.dronedirector.acluxpro.camera

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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@OptIn(androidx.camera.core.ExperimentalSessionConfig::class, androidx.camera.video.ExperimentalHighSpeedVideo::class)
class ProCameraController(private val context: Context) {

    private val _state = MutableStateFlow(CameraUiState())
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
    private val filterProcessor = FilterProcessor()
    private var filterWarned = false

    val audioMeter = AudioMeter(context)
    val live = LiveStreamer(context)

    // Panorama capture state (main thread).
    private val panoFrames = ArrayList<Bitmap>()
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
            mirror = s.isFront,
            motion = s.panoActive,
        )
    }

    fun toast(message: String) = set { it.copy(message = message, messageId = it.messageId + 1) }

    // ---------------------------------------------------------------- lifecycle

    fun attach(owner: LifecycleOwner, view: PreviewView) {
        this.owner = owner
        previewView = view
        refreshStorage()
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
        rotation = r
        imageCapture?.setTargetRotation(r)
        imageAnalysis?.setTargetRotation(r)
        if (!st.recording) videoCapture?.setTargetRotation(r)
    }

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

    private fun refreshPhotoSizes(target: Lens? = st.lens) {
        val lens = target ?: return
        val aspect = st.aspect
        val sizes = PhotoSizes.forCamera(lens.info, aspect)
        val saved = PhotoSizes.load(context, lens.id, aspect)
        val chosen = sizes.firstOrNull { it.key == saved } ?: sizes.firstOrNull { !it.highRes } ?: sizes.firstOrNull()
        set { it.copy(photoSizes = sizes, photoSize = chosen) }
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
        p.unbindAll()
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
            else -> buildVideoCapture(s).also { vc = it }
        }

        // Live filter: a GPU effect on the preview (and on the recording in video modes).
        val wantEffect = needsEffect(s) && !useScene
        filterProcessor.setFilter(s.filter)
        filterProcessor.setCurve(logCurve(s), s.logAssist)
        val effect: CameraEffect? = if (wantEffect) {
            val targets = if (s.mode.isNormalVideo) CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE else CameraEffect.PREVIEW
            FilterEffect(targets, filterProcessor) { t -> mainExecutor.execute { toast("Filter error: ${t.message}") } }
        } else null

        data class Attempt(val effect: Boolean, val analysis: Boolean)
        val attempts = buildList {
            if (effect != null) { add(Attempt(true, true)); add(Attempt(true, false)) }
            add(Attempt(false, true)); add(Attempt(false, false))
        }
        var bound: Camera? = null
        var withAnalysis = false
        var withEffect = false
        var error: Throwable? = null
        for (a in attempts) {
            try {
                p.unbindAll()
                val group = UseCaseGroup.Builder().addUseCase(preview)
                if (main != null) group.addUseCase(main)
                if (a.analysis) group.addUseCase(analysis)
                if (a.effect && effect != null) group.addEffect(effect)
                bound = p.bindToLifecycle(owner, selector, group.build())
                withAnalysis = a.analysis
                withEffect = a.effect
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
        videoCapture = vc
        imageAnalysis = if (withAnalysis) analysis else null
        if (!withAnalysis && (s.proHistogram || s.proZebras || s.proPeaking)) {
            _analysis.value = null
        }

        bound.cameraInfo.zoomState.observe(owner) { z ->
            _state.update { it.copy(zoom = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
        }
        set { it.copy(ready = true) }
        applyControls()
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

    private fun buildVideoCapture(s: CameraUiState): VideoCapture<Recorder> {
        val q = s.videoRes.quality
        val recorderBuilder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
        if (s.highBitrate) recorderBuilder.setTargetVideoEncodingBitRate(s.videoRes.highBitrate)
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

    fun setFocusAuto() {
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
        set { it.copy(aeLock = !it.aeLock) }
        applyControls()
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
    fun toggleVolumeShutter() = set { it.copy(volumeShutter = !it.volumeShutter) }
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
        return s.filter != Filter.NONE || logCurve(s) != 0
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
        var volume = Storage.load(context)
        if (options.none { it.volume == volume }) {
            if (volume != Storage.INTERNAL) toast("SD card not found, saving to internal storage")
            volume = Storage.INTERNAL
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
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = try { resolver.insert(Storage.imagesUri(volume), values) } catch (_: Throwable) { null } ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
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
                "${stamp()}_${if (hyper) "hyperlapse" else "timelapse"}.mp4",
                intervalMs = s.lapseIntervalMs,
                everyNth = if (hyper) s.hyperSpeed else 0,
                zoom = if (hyper && s.lapseStabilize) 0.9f else 1f,
                onFrame = { n -> _state.update { it.copy(lapseFrames = n) } },
            )
        } catch (t: Throwable) {
            toast("Couldn't start: ${t.message}")
            return
        }
        lapseRecorder = rec
        filterProcessor.lapse = rec
        sound.play(MediaActionSound.START_VIDEO_RECORDING)
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
        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
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
        panoAccum = 0f
        panoBusy = false
        set { it.copy(panoActive = true, panoFrames = 0, panoDirection = 0, panoProgress = 0f) }
        applyControls() // lock exposure and white balance so frames match
        capturePanoFrame()
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

    private fun onPanoMotion(dx: Float) {
        val s = st
        if (!s.panoActive || panoBusy) return
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
        sound.play(MediaActionSound.FOCUS_COMPLETE)
        ic.takePicture(ioExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val frame = try { Panorama.prepareFrame(image.toBitmap(), rot) } catch (_: Throwable) { null }
                image.close()
                mainExecutor.execute {
                    panoBusy = false
                    if (frame == null || !st.panoActive) { frame?.recycle(); return@execute }
                    panoFrames += frame
                    val n = panoFrames.size
                    set { it.copy(panoFrames = n, panoProgress = (n - 1f) / (Panorama.MAX_FRAMES - 1)) }
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
        val frames = ArrayList(panoFrames)
        panoFrames.clear()
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
                val pano = try { Panorama.stitch(frames, direction, filter) } catch (_: Throwable) { null }
                frames.forEach { it.recycle() }
                pano?.let { p -> saveBitmap(p, stamp() + "_pano.jpg", volume).also { p.recycle() } }
            }
            set { it.copy(stitching = false, lastMedia = uri ?: it.lastMedia) }
            sound.play(MediaActionSound.SHUTTER_CLICK)
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

    private fun photoOptions(fileName: String, mime: String): ImageCapture.OutputFileOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            Storage.imagesUri(st.storageVolume),
            values,
        ).build()
    }

    private suspend fun takePhoto(): Boolean {
        val ic = imageCapture ?: return false
        val format = ic.outputFormat
        val filter = st.filter
        val name = stamp()
        sound.play(MediaActionSound.SHUTTER_CLICK)
        set { it.copy(shutterFlash = it.shutterFlash + 1) }

        return suspendCancellableCoroutine { cont ->
            val expected = if (format == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) 2 else 1
            var count = 0
            var ok = false
            val callback = object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    count++
                    ok = true
                    val uri = output.savedUri
                    val isJpeg = output.imageFormat != ImageFormat.RAW_SENSOR && format != ImageCapture.OUTPUT_FORMAT_RAW
                    if (uri != null && isJpeg && filter != Filter.NONE) {
                        ioExecutor.execute { Filter.applyToSavedJpeg(context, uri, filter) }
                    }
                    if (uri != null && (expected == 1 || isJpeg)) {
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
        val vc = videoCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${stamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AcluxPro")
        }
        audioMeter.stopMonitoring()
        val s0 = st
        // Slow motion without high-speed support: film at 60 fps, then stretch to half speed.
        val stretch = s0.mode.isSlowMo && s0.slowFps in 1..99
        var pending = if (stretch) {
            val file = java.io.File(context.cacheDir, "slowmo_${System.currentTimeMillis()}.mp4")
            slowMoFile = file
            vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        } else {
            slowMoFile = null
            val output = MediaStoreOutputOptions.Builder(context.contentResolver, Storage.videosUri(st.storageVolume))
                .setContentValues(values)
                .build()
            vc.output.prepareRecording(context, output)
        }
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (s0.mode.isNormalVideo && st.audio && micGranted) pending = pending.withAudioEnabled()
        if (s0.mode.isNormalVideo && st.audio && !micGranted) toast("Recording without sound (microphone not allowed)")
        sound.play(MediaActionSound.START_VIDEO_RECORDING)
        try {
            recording = pending.start(mainExecutor) { event -> onRecordEvent(event) }
        } catch (t: Throwable) {
            toast("Couldn't start recording: ${t.message}")
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
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
                audioMeter.pushAmplitude(amp)
                set { it.copy(recordSeconds = stats.recordedDurationNanos / 1_000_000_000L, audioLevel = amp) }
            }
            is VideoRecordEvent.Finalize -> {
                recording = null
                val slowFile = slowMoFile
                slowMoFile = null
                if (slowFile != null) {
                    val factor = st.slowFps / 30.0
                    val volume = st.storageVolume
                    set { it.copy(recording = false, paused = false, processing = true) }
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            SlowMotionRemux.slowDown(context, slowFile, factor, volume, "${stamp()}_slowmo.mp4")
                        }
                        set { it.copy(processing = false, lastMedia = out ?: it.lastMedia) }
                        toast(if (out != null) "Slow-motion video saved (%.0f× slower)".format(factor) else "Couldn't save the slow-motion video")
                    }
                    videoCapture?.setTargetRotation(rotation)
                    return
                }
                val uri = event.outputResults.outputUri
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
                // Apply any rotation change that happened while recording.
                videoCapture?.setTargetRotation(rotation)
            }
        }
    }
}
