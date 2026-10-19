package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder

/** One spoken word in a clip, in the clip's source time (ms). */
data class TWord(val clipId: String, val startMs: Long, val endMs: Long, val text: String, val deleted: Boolean = false)

/** Speech models for on-device transcription (whisper.cpp, MIT; OpenAI Whisper weights, MIT). */
enum class SpeechModel(val label: String, val file: String, val sizeMb: Int) {
    BASE("Fast (Base)", "ggml-base-q5_1.bin", 57),
    SMALL("Accurate (Small)", "ggml-small-q5_1.bin", 181);
    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
}

/**
 * On-device speech to text with word timestamps (whisper.cpp, bundled as native code). The model is
 * downloaded once (MIT-licensed Whisper weights) and kept in the app's files.
 */
object Transcriber {
    val available: Boolean by lazy { try { System.loadLibrary("veyrocut_whisper"); true } catch (_: Throwable) { false } }
    @JvmStatic private external fun nativeInit(path: String): Long
    @JvmStatic private external fun nativeFree(h: Long)
    @JvmStatic private external fun nativeTranscribe(h: Long, pcm: FloatArray, lang: String?, threads: Int): String
    @JvmStatic private external fun nativeProgress(): Int
    @JvmStatic private external fun nativeAbort(abort: Boolean)

    private var ctx = 0L
    private var ctxModel: SpeechModel? = null
    @Volatile var stopped = false; private set

    fun modelFile(context: Context, m: SpeechModel) = File(File(context.filesDir, "whisper").apply { mkdirs() }, m.file)
    fun hasModel(context: Context, m: SpeechModel) = modelFile(context, m).let { it.exists() && it.length() > 1_000_000 }

    /** Downloads [m] (blocking, call off the main thread). Returns null on success, else why not. */
    fun download(context: Context, m: SpeechModel, onProgress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        val target = modelFile(context, m)
        val part = File(target.parentFile, target.name + ".part")
        return try {
            var url = URL(m.url)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000; conn.readTimeout = 30_000
                val code = conn.responseCode
                if (code in 300..399 && hops < 6) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); hops++; continue }
                if (code != 200) return "Download failed (HTTP $code)"
                break
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: (m.sizeMb * 1_048_576L)
            var got = 0L
            conn.inputStream.use { ins ->
                part.outputStream().buffered(1 shl 16).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (cancelled()) return "Download cancelled"
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n); got += n
                        onProgress((got * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (got < 1_000_000) return "Download incomplete"
            if (!part.renameTo(target)) return "Couldn't save the model"
            null
        } catch (t: Throwable) { "Download failed: ${t.message}" } finally { if (!target.exists()) part.delete() }
    }

    private fun ensure(context: Context, m: SpeechModel): Boolean {
        if (ctx != 0L && ctxModel == m) return true
        if (ctx != 0L) { nativeFree(ctx); ctx = 0L }
        ctx = nativeInit(modelFile(context, m).path)
        ctxModel = m
        return ctx != 0L
    }

    fun progress(): Int = if (available) try { nativeProgress() } catch (_: Throwable) { 0 } else 0
    fun stop() { stopped = true; if (available) try { nativeAbort(true) } catch (_: Throwable) {} }

    /** Transcribes the in-to-out range of [c] into words (blocking). */
    fun transcribe(context: Context, c: Clip, m: SpeechModel, lang: String?): List<TWord>? {
        stopped = false
        if (!available) return null
        cached(context, c, lang)?.let { return it }
        val pcm = decode16k(context, c.uri, c.startMs, c.endMs) ?: return null
        if (stopped) return null
        if (!ensure(context, m)) return null
        val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 6)
        val out = nativeTranscribe(ctx, pcm, lang, threads)
        if (stopped) return null
        val words = out.lineSequence().mapNotNull { line ->
            val p = line.split('\t'); if (p.size < 3) return@mapNotNull null
            val t = p[2].trim(); if (t.isEmpty() || t.startsWith("[") || t.startsWith("(")) return@mapNotNull null
            val a = p[0].toLongOrNull() ?: return@mapNotNull null; val b = p[1].toLongOrNull() ?: return@mapNotNull null
            TWord(c.id, c.startMs + a, (c.startMs + b).coerceAtMost(c.endMs), t)
        }.toList()
        saveCache(context, c, lang, words)
        return words
    }

    // ---- cache (per media + range + language), so re-opening a project is instant
    private fun cacheFile(context: Context, c: Clip, lang: String?) =
        File(File(context.filesDir, "transcripts").apply { mkdirs() }, "${MediaId.of(c)}_${c.startMs}_${c.endMs}_${lang ?: "auto"}.tsv")

    private fun cached(context: Context, c: Clip, lang: String?): List<TWord>? = try {
        val f = cacheFile(context, c, lang)
        if (!f.exists()) null else f.readLines().mapNotNull { l ->
            val p = l.split('\t'); if (p.size < 3) null else TWord(c.id, p[0].toLong(), p[1].toLong(), p[2])
        }
    } catch (_: Throwable) { null }

    private fun saveCache(context: Context, c: Clip, lang: String?, w: List<TWord>) {
        try { cacheFile(context, c, lang).writeText(w.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${it.text}" }) } catch (_: Throwable) {}
    }

    /** Decodes the audio of [uri] from [fromMs] to [toMs] into 16 kHz mono floats. */
    fun decode16k(context: Context, uri: Uri, fromMs: Long, toMs: Long): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            val outCount = ((toMs - fromMs) * 16).toInt().coerceAtLeast(1)
            val out = FloatArray(outCount)
            var written = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            var pos = 0.0   // output position in input-rate samples since fromMs
            while (!outDone && !stopped) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5_000)
                    if (ii >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                        if (n < 0 || ex.sampleTime > toMs * 1000 + 200_000) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val buf = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val bytesPer = if (isFloat) 4 else 2
                    val frames = info.size / (bytesPer * ch)
                    val t0 = info.presentationTimeUs / 1000.0
                    val step = rate / 16000.0
                    for (fi in 0 until frames) {
                        var s = 0f
                        for (k in 0 until ch) s += if (isFloat) buf.float else buf.short / 32768f
                        s /= ch
                        val tMs = t0 + fi * 1000.0 / rate
                        if (tMs < fromMs) continue
                        // Linear-enough resample: take every step-th sample (input rates are ≥ 16 kHz).
                        val idx = ((tMs - fromMs) * 16).toInt()
                        if (idx in 0 until outCount && idx >= written) { out[idx] = s; if (idx > written) { for (z in written until idx) out[z] = s }; written = idx + 1 }
                        pos += 1.0 / step
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || written >= outCount) outDone = true
                }
            }
            return if (stopped) null else out
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            ex.release()
        }
    }

    /** Filler words (by language) that REMOVE FILLERS marks for deletion. */
    private val FILLERS = setOf(
        "um", "umm", "uh", "uhh", "uhm", "erm", "er", "ah", "ahh", "hmm", "mm", "mhm",
        "øh", "øhm", "øhh", "æh", "æhm", "hm", "eh", "ehm",
    )
    fun isFiller(w: String): Boolean = w.lowercase().trim().trim('.', ',', '!', '?', '…', '-', '"').let { it in FILLERS }

    /**
     * The source ranges to cut from each clip, from the deleted words: each run of deleted words is cut
     * from just after the word before it to just before the word after it, so no dead air is left behind.
     */
    fun cutRanges(clip: Clip, words: List<TWord>): List<LongRange> {
        val ws = words.filter { it.clipId == clip.id }.sortedBy { it.startMs }
        val out = mutableListOf<LongRange>()
        var i = 0
        val pad = 40L
        while (i < ws.size) {
            if (!ws[i].deleted) { i++; continue }
            var j = i
            while (j + 1 < ws.size && ws[j + 1].deleted) j++
            val a = if (i > 0) (ws[i - 1].endMs + pad).coerceAtMost(ws[i].startMs) else clip.startMs.coerceAtLeast(ws[i].startMs - 150)
            val b = if (j + 1 < ws.size) (ws[j + 1].startMs - pad).coerceAtLeast(ws[j].endMs) else clip.endMs.coerceAtMost(ws[j].endMs + 150).let { if (j == ws.lastIndex) maxOf(it, ws[j].endMs) else it }
            if (b > a) out += a until b
            i = j + 1
        }
        return out
    }
}
