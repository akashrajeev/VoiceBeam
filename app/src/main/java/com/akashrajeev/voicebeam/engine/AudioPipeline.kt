package com.akashrajeev.voicebeam.engine

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import android.os.Process
import android.util.Log
import com.akashrajeev.voicebeam.core.EnergyVad
import com.akashrajeev.voicebeam.core.GateInputs
import com.akashrajeev.voicebeam.core.TargetGate
import com.akashrajeev.voicebeam.core.WavWriter
import com.akashrajeev.voicebeam.ml.AudioModels
import com.akashrajeev.voicebeam.ml.SAMPLE_RATE
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Mic -> noise removal -> target gate -> earphones, with side outputs for
 * captions, the voice fingerprint and recording. Runs on its own thread.
 */
class AudioPipeline(
    private val models: AudioModels,
    private val signals: () -> GateInputs,   // latest vision + voice info (voiceActive is filled here)
    private val onFrame: (FrameInfo) -> Unit,
) {
    data class FrameInfo(val level: Float, val gain: Float, val probability: Float, val voiceActive: Boolean)

    @Volatile var quietOthers = 0.8f
    @Volatile var boostDb = 12f
    @Volatile var denoiseMix = 1f          // 0 = raw, 1 = fully denoised
    @Volatile var monitorEnabled = true    // play to earphones
    @Volatile var rawWriter: WavWriter? = null
    @Volatile var cleanWriter: WavWriter? = null

    /** Gated clean audio for captions (consumer: caption thread). */
    val asrQueue = ArrayBlockingQueue<FloatArray>(400)
    /** Denoised audio + whether the locked face was clearly talking (consumer: voice-print thread). */
    val voiceQueue = ArrayBlockingQueue<Pair<FloatArray, Float>>(400)

    private val gate = TargetGate(frameMs = (models.denoiser.frameShift.takeIf { it > 0 } ?: 256) * 1000f / SAMPLE_RATE)
    private val vad = EnergyVad()
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(useSceneMic: Boolean) {
        if (running.getAndSet(true)) return
        thread = Thread({ loop(useSceneMic) }, "vb-audio").also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(1500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(useSceneMic: Boolean) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frameShift = models.denoiser.frameShift.takeIf { it > 0 } ?: 256
        val minRec = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val source = if (useSceneMic) MediaRecorder.AudioSource.CAMCORDER else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val rec = try {
            AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minRec, frameShift * 8))
        } catch (t: Throwable) {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minRec, frameShift * 8))
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                rec.setPreferredMicrophoneDirection(
                    if (useSceneMic) MicrophoneDirection.MIC_DIRECTION_AWAY_FROM_USER else MicrophoneDirection.MIC_DIRECTION_UNSPECIFIED
                )
            } catch (_: Throwable) {}
        }
        val minPlay = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minPlay, frameShift * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val input = FloatArray(frameShift)
        try {
            rec.startRecording()
            track.play()
            models.denoiser.reset()
            gate.reset()
            while (running.get()) {
                var read = 0
                while (read < frameShift && running.get()) {
                    val n = rec.read(input, read, frameShift - read, AudioRecord.READ_BLOCKING)
                    if (n <= 0) break
                    read += n
                }
                if (read < frameShift) continue
                rawWriter?.write(input)

                val denoised = try { models.denoiser.process(input.copyOf()) } catch (t: Throwable) { input.copyOf() }
                val n = minOf(denoised.size, input.size)
                val clean = FloatArray(n)
                val mix = denoiseMix
                if (n == 0) continue
                // denoiser output may lag input by a frame; mixing uses aligned sizes
                for (k in 0 until n) clean[k] = mix * denoised[k] + (1f - mix) * input[input.size - n + k]

                val voice = vad.isVoice(clean)
                val s = signals()
                gate.quietOthers = quietOthers
                val g = gate.process(s.copy(voiceActive = voice))
                val boost = TargetGate.dbToLinear(boostDb)
                val gated = FloatArray(n)
                val out = FloatArray(n)
                var e = 0f
                for (k in 0 until n) {
                    gated[k] = clean[k] * g
                    out[k] = (gated[k] * boost).coerceIn(-1f, 1f)
                    e += clean[k] * clean[k]
                }
                cleanWriter?.write(gated)
                if (monitorEnabled) track.write(out, 0, n, AudioTrack.WRITE_NON_BLOCKING)
                if (!asrQueue.offer(gated)) { asrQueue.poll(); asrQueue.offer(gated) }
                if (voice) {
                    val v = Pair(clean, if (s.hasLock && s.othersSpeaking < 0.3f) s.lockedSpeaking else 0f)
                    if (!voiceQueue.offer(v)) { voiceQueue.poll(); voiceQueue.offer(v) }
                }
                onFrame(FrameInfo(sqrt(e / n), g, gate.probability, voice))
            }
        } catch (t: Throwable) {
            Log.e("VoiceBeamAudio", "audio loop failed", t)
        } finally {
            try { rec.stop() } catch (_: Throwable) {}
            rec.release()
            try { track.stop() } catch (_: Throwable) {}
            track.release()
        }
    }
}
