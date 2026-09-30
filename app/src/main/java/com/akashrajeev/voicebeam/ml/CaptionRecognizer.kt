package com.akashrajeev.voicebeam.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.akashrajeev.voicebeam.core.Captions
import java.io.File
import kotlin.math.sqrt

enum class AsrBackend { ZIPFORMER, WHISPER_SMALL, WHISPER_TURBO }
interface CaptionRecognizer {
    fun accept(samples: FloatArray): Pair<String, Boolean>
    fun resetStream()
    fun release()
}
internal object WhisperNative {
    init { System.loadLibrary("voicebeam_whisper") }
    external fun load(path: String): Long
    external fun decode(handle: Long, audio: FloatArray): String
    external fun free(handle: Long)
}
/** Windowed, not native streaming. CPU-only reference experiment. */
class WhisperCaptionRecognizer(context: Context, backend: AsrBackend) : CaptionRecognizer {
    private var handle = 0L
    private val buffer = FloatArray(8 * SAMPLE_RATE)
    private var used = 0
    private var silence = 0
    private var sinceDecode = 0
    private var partial = ""
    init {
        val name = when (backend) {
            AsrBackend.WHISPER_SMALL -> "ggml-small-q5_1.bin"
            AsrBackend.WHISPER_TURBO -> "ggml-large-v3-turbo-q5_0.bin"
            else -> error("Whisper backend required")
        }
        val folder = File(context.filesDir, "whisper").also { it.mkdirs() }
        val file = File(folder, name)
        if (!file.exists()) {
            val tmp = File(folder, "$name.tmp")
            try {
                context.assets.open("models/whisper/$name").use { src -> tmp.outputStream().use { src.copyTo(it) } }
                check(tmp.renameTo(file)) { "Cannot install local Whisper weights" }
            } finally { tmp.delete() }
        }
        handle = WhisperNative.load(file.absolutePath)
        check(handle != 0L) { "Cannot load local Whisper weights" }
    }
    @Synchronized override fun accept(samples: FloatArray): Pair<String, Boolean> {
        val rms = sqrt(samples.fold(0.0) { sum, v -> sum + v * v } / samples.size.coerceAtLeast(1))
        val speech = rms > 0.004
        if (used == 0 && !speech) return Pair("", false)
        val n = minOf(samples.size, buffer.size - used)
        samples.copyInto(buffer, used, 0, n)
        used += n; sinceDecode += n
        silence = if (speech) 0 else silence + n
        val ended = silence >= SAMPLE_RATE * 9 / 10 || used == buffer.size
        if (used >= SAMPLE_RATE && (ended || sinceDecode >= 2 * SAMPLE_RATE)) {
            val start = SystemClock.elapsedRealtime()
            partial = Captions.tidy(WhisperNative.decode(handle, buffer.copyOf(used)))
            Log.i("VoiceBeamAsr", "whisper audioMs=${used * 1000L / SAMPLE_RATE} decodeMs=${SystemClock.elapsedRealtime() - start} final=$ended")
            sinceDecode = 0
        }
        val result = Pair(partial, ended)
        if (ended) resetStream()
        return result
    }
    @Synchronized override fun resetStream() { used = 0; silence = 0; sinceDecode = 0; partial = "" }
    @Synchronized override fun release() { WhisperNative.free(handle); handle = 0 }
}
