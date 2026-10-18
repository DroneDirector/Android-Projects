package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Re-encodes a video with a colour grade (baked into a 3D LUT) using Media3 Transformer.
 * The sound is kept as it is. Must be started on the main thread.
 */
@UnstableApi
class VideoGradeExport(
    private val context: Context,
    private val source: Uri,
    grade: Grade,
    private val onProgress: (Int) -> Unit,
    private val onDone: (Uri?, String?) -> Unit,
) {
    private val out = File(context.cacheDir, "grade_${System.currentTimeMillis()}.mp4")
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    private val transformer: Transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                finished = true
                Thread {
                    val uri = GradeIO.publishVideo(context, out)
                    out.delete()
                    handler.post { onDone(uri, if (uri == null) "Couldn't save the graded video" else null) }
                }.start()
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                finished = true
                out.delete()
                onDone(null, "Export failed: ${exportException.errorCodeName}")
            }
        })
        .build()

    private val lut = SingleColorLut.createFromCube(grade.cube())

    fun start() {
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(Effects(emptyList(), listOf(lut)))
            .build()
        transformer.start(item, out.path)
        poll()
    }

    private fun poll() {
        if (finished) return
        val holder = ProgressHolder()
        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
        handler.postDelayed({ poll() }, 300)
    }

    fun cancel() {
        if (finished) return
        finished = true
        transformer.cancel()
        out.delete()
    }
}
