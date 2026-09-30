package com.akashrajeev.voicebeam.ml

import android.content.res.AssetManager
import android.util.Log
import com.akashrajeev.voicebeam.core.Captions
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

const val SAMPLE_RATE = 16000
private const val TAG = "VoiceBeamModels"

/** Streaming speech-to-text (sherpa-onnx zipformer transducer, runs fully on device). */
class Asr(assets: AssetManager) : CaptionRecognizer {
    private val recognizer: OnlineRecognizer
    private var stream: OnlineStream

    init {
        val cfg = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "models/asr/encoder.onnx",
                    decoder = "models/asr/decoder.onnx",
                    joiner = "models/asr/joiner.onnx",
                ),
                tokens = "models/asr/tokens.txt",
                numThreads = 2,
                modelType = "zipformer",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.0f, 0.0f),
                rule2 = EndpointRule(true, 0.9f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 12.0f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        recognizer = OnlineRecognizer(assetManager = assets, config = cfg)
        stream = recognizer.createStream()
    }

    /** Feed audio; returns the current partial text and whether an utterance just ended. */
    override fun accept(samples: FloatArray): Pair<String, Boolean> {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        val text = Captions.tidy(recognizer.getResult(stream).text)
        val ended = recognizer.isEndpoint(stream)
        if (ended) recognizer.reset(stream)
        return Pair(text, ended)
    }

    override fun resetStream() { recognizer.reset(stream) }

    override fun release() { stream.release(); recognizer.release() }
}

/** Real-time noise removal (GTCRN). */
class Denoiser(assets: AssetManager) {
    private val impl = OnlineSpeechDenoiser(
        assetManager = assets,
        config = OnlineSpeechDenoiserConfig(
            model = OfflineSpeechDenoiserModelConfig(
                gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(model = "models/gtcrn.onnx"),
                numThreads = 1,
            )
        ),
    )

    val frameShift: Int get() = impl.frameShiftInSamples

    fun process(samples: FloatArray): FloatArray = impl.run(samples, SAMPLE_RATE).samples

    fun reset() = impl.reset()
    fun release() = impl.release()
}

/** Voice fingerprint (CAM++ speaker embedding). */
class VoicePrint(assets: AssetManager) {
    private val extractor = SpeakerEmbeddingExtractor(
        assetManager = assets,
        config = SpeakerEmbeddingExtractorConfig(model = "models/speaker.onnx", numThreads = 1),
    )

    fun embed(samples: FloatArray): FloatArray? {
        val s = extractor.createStream()
        return try {
            s.acceptWaveform(samples, SAMPLE_RATE)
            s.inputFinished()
            if (extractor.isReady(s)) extractor.compute(s) else null
        } catch (t: Throwable) {
            Log.w(TAG, "embed failed", t); null
        } finally {
            s.release()
        }
    }

    fun release() = extractor.release()
}

/** Loads all audio models once; safe to call from a background thread. */
class AudioModels private constructor(val asr: CaptionRecognizer, val denoiser: Denoiser, val voicePrint: VoicePrint) {
    companion object {
        fun load(context: android.content.Context, backend: AsrBackend): AudioModels =
            AudioModels(if (backend == AsrBackend.ZIPFORMER) Asr(context.assets) else WhisperCaptionRecognizer(context, backend), Denoiser(context.assets), VoicePrint(context.assets))

        fun load(assets: AssetManager): AudioModels {
            val t0 = System.currentTimeMillis()
            val m = AudioModels(Asr(assets), Denoiser(assets), VoicePrint(assets))
            Log.i(TAG, "models loaded in ${System.currentTimeMillis() - t0} ms")
            return m
        }
    }
}
