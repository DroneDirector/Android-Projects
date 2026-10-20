package com.dronedirector.veyrocutpro.editor

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI models that are downloaded the first time a feature needs them (so the app itself stays small),
 * from the app's own GitHub release "ai-models-v1". Each model's licence allows use in a paid app.
 */
enum class AiModel(val label: String, val files: List<String>, val sizeMb: Int) {
    DEPTH("AI depth (Depth Anything V2 Small, Apache 2.0)", listOf("depth_anything_v2_small.onnx"), 99),
    INPAINT("AI object removal (LaMa, Apache 2.0)", listOf("lama_fp32.onnx"), 208),
    VOICE("AI voiceover (Kokoro 82M, Apache 2.0)", listOf("kokoro_v1_q8.onnx", "misaki_us_gold.json", "misaki_us_silver.json", "misaki_gb_gold.json", "misaki_gb_silver.json"), 105),
}

object ModelStore {
    private const val BASE = "https://github.com/DroneDirector/Android-Projects/releases/download/ai-models-v1/"

    fun dir(context: Context) = File(context.filesDir, "models").apply { mkdirs() }
    fun file(context: Context, name: String) = File(dir(context), name)
    fun has(context: Context, m: AiModel) = m.files.all { file(context, it).let { f -> f.exists() && f.length() > 1000 } }

    /** Downloads one file (blocking). Returns null when done, else why not. */
    fun fetch(context: Context, name: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): String? {
        val target = file(context, name)
        if (target.exists() && target.length() > 1000) return null
        val part = File(target.parentFile, "$name.part")
        return try {
            var url = URL(BASE + name)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code in 300..399 && hops < 8) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); hops++; continue }
                if (code != 200) return "Download failed (HTTP $code)"
                break
            }
            val total = conn.contentLengthLong
            var got = 0L
            conn.inputStream.use { ins ->
                part.outputStream().buffered(1 shl 16).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (cancelled()) return "Download cancelled"
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n); got += n
                        onProgress(got, total)
                    }
                }
            }
            if (total > 0 && got != total) return "Download incomplete"
            if (!part.renameTo(target)) return "Couldn't save the model"
            null
        } catch (t: Throwable) { "Download failed: ${t.message ?: "no connection?"}" } finally { if (!target.exists()) part.delete() }
    }

    /** Downloads everything [m] needs (blocking). [onProgress] gets 0..100. Returns null when ready, else why not. */
    fun ensure(context: Context, m: AiModel, onProgress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        if (has(context, m)) return null
        val todo = m.files.filter { !file(context, it).let { f -> f.exists() && f.length() > 1000 } }
        val totalMb = m.sizeMb.coerceAtLeast(1) * 1_048_576L
        var done = m.files.filter { it !in todo }.sumOf { file(context, it).length() }
        for (name in todo) {
            val base = done
            fetch(context, name, { got, _ -> onProgress(((base + got) * 100 / totalMb).toInt().coerceIn(0, 99)) }, cancelled)?.let { return it }
            done += file(context, name).length()
        }
        onProgress(100)
        return null
    }

    /** Frees the space a model takes. */
    fun delete(context: Context, m: AiModel) { m.files.forEach { file(context, it).delete() } }
}
