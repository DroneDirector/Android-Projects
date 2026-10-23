package com.dronedirector.veyrocutpro.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.util.Locale

/**
 * Records the phone's gyroscope and accelerometer while a video is filmed and saves it as a
 * Gyroflow log (.gcsv) with the same name as the clip. Load both in Gyroflow (gyroflow.xyz) on a
 * computer for gimbal-smooth stabilization from the real motion data.
 *
 * The camera's own stabilization is switched off while this is on, because Gyroflow needs the
 * unstabilized picture.
 */
class GyroLogger(private val context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED) ?: sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var thread: HandlerThread? = null

    // Samples as flat arrays: [t(ns), x, y, z] — filled on the sensor thread.
    private val gyroData = ArrayList<FloatArray>(20_000)
    private val gyroT = ArrayList<Long>(20_000)
    private val accData = ArrayList<FloatArray>(20_000)
    private val accT = ArrayList<Long>(20_000)
    private val lock = Any()
    @Volatile private var startNs = 0L
    @Volatile private var startWallMs = 0L
    @Volatile var running = false; private set

    val available: Boolean get() = gyro != null

    fun start() {
        if (running || gyro == null) return
        synchronized(lock) { gyroData.clear(); gyroT.clear(); accData.clear(); accT.clear() }
        startNs = SystemClock.elapsedRealtimeNanos()
        startWallMs = System.currentTimeMillis()
        val t = HandlerThread("GyroLog").apply { start() }
        thread = t
        val h = Handler(t.looper)
        // ~200 Hz (5 ms) — what Gyroflow likes; phones deliver what they can.
        sm.registerListener(this, gyro, 5_000, h)
        accel?.let { sm.registerListener(this, it, 5_000, h) }
        running = true
    }

    /** Re-anchors time zero to the moment the recording really started. */
    fun markStart() { startNs = SystemClock.elapsedRealtimeNanos(); startWallMs = System.currentTimeMillis() }

    fun cancel() {
        if (!running) return
        sm.unregisterListener(this)
        thread?.quitSafely(); thread = null
        running = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.timestamp < startNs - 200_000_000L) return
        synchronized(lock) {
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                accT += e.timestamp; accData += floatArrayOf(e.values[0], e.values[1], e.values[2])
            } else {
                gyroT += e.timestamp; gyroData += floatArrayOf(e.values[0], e.values[1], e.values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Stops logging and writes `<clip>.gcsv` next to the clip when the clip is in a picked folder,
     * otherwise in Documents/AlcorCutPro/Gyroflow. Returns a short place description, or null.
     */
    fun stopAndSave(videoUri: Uri, videoName: String, treePlace: String?): String? {
        if (!running) return null
        cancel()
        val text = buildGcsv(videoName) ?: return null
        val base = videoName.substringBeforeLast('.')
        val fileName = "$base.gcsv"
        return try {
            if (treePlace != null) {
                val doc = Storage.createInTree(context, treePlace, "application/octet-stream", fileName) ?: return null
                context.contentResolver.openOutputStream(doc)?.use { it.write(text.toByteArray()) }
                "next to the clip"
            } else if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/AlcorCutPro/Gyroflow")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "Documents/AlcorCutPro/Gyroflow"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "AlcorCutPro").apply { mkdirs() }
                File(dir, fileName).writeText(text)
                "DCIM/AlcorCutPro"
            }
        } catch (_: Throwable) { null }
    }

    private fun buildGcsv(videoName: String): String? {
        val gT: LongArray; val g: List<FloatArray>; val aT: LongArray; val a: List<FloatArray>
        synchronized(lock) {
            gT = gyroT.toLongArray(); g = gyroData.toList(); aT = accT.toLongArray(); a = accData.toList()
        }
        if (gT.size < 10) return null
        val sb = StringBuilder(gT.size * 64)
        sb.append("GYROFLOW IMU LOG\n")
        sb.append("version,1.3\n")
        sb.append("id,veyrocutpro_android\n")
        // Android sensor axes: X right, Y up, Z out of the screen (phone held upright).
        sb.append("orientation,XYZ\n")
        sb.append("note,AlcorCut Pro ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("fwversion,").append(Build.VERSION.RELEASE).append('\n')
        sb.append("timestamp,").append(startWallMs / 1000).append('\n')
        sb.append("vendor,").append(Build.MANUFACTURER).append('\n')
        sb.append("videofilename,").append(videoName).append('\n')
        sb.append("lensprofile,\n")
        sb.append("tscale,0.000001\n")   // t in microseconds
        sb.append("gscale,1.0\n")        // rad/s
        sb.append("ascale,").append(String.format(Locale.US, "%.8f", 1.0 / 9.80665)).append('\n') // m/s² → g
        sb.append("t,gx,gy,gz,ax,ay,az\n")
        // One row per gyro sample, with the nearest accelerometer sample.
        var ai = 0
        for (i in gT.indices) {
            val t = gT[i]
            if (t < startNs) continue
            while (ai + 1 < aT.size && kotlin.math.abs(aT[ai + 1] - t) <= kotlin.math.abs(aT[ai] - t)) ai++
            val v = g[i]
            sb.append((t - startNs) / 1000).append(',')
                .append(fmt(v[0])).append(',').append(fmt(v[1])).append(',').append(fmt(v[2]))
            if (aT.isNotEmpty()) {
                val av = a[ai]
                sb.append(',').append(fmt(av[0])).append(',').append(fmt(av[1])).append(',').append(fmt(av[2]))
            } else sb.append(",0,0,0")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun fmt(f: Float) = String.format(Locale.US, "%.6f", f)
}
