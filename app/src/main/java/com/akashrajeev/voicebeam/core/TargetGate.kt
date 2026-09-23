package com.akashrajeev.voicebeam.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/** Everything the gate knows at one audio frame. */
data class GateInputs(
    val hasLock: Boolean,
    val lockedSpeaking: Float,   // 0..1 from lip movement of the tapped face
    val othersSpeaking: Float,   // 0..1, max over other faces
    val voiceMatch: Float?,      // 0..1 from the voice fingerprint, null when not learned yet
    val voiceActive: Boolean,    // is there speech energy in this frame
    val lockedVisible: Boolean = true, // is the locked face in view right now
)

/**
 * Decides, frame by frame, how likely the sound is to be the locked person,
 * and turns that into a smooth gain. Others are turned down by [quietOthers]
 * (0 = leave them, 1 = fully mute). Gain moves up fast and down slowly so
 * the locked person's words are never clipped.
 */
class TargetGate(
    private val frameMs: Float = 10f,
    private val attackMs: Float = 25f,
    private val releaseMs: Float = 220f,
    private val holdMs: Float = 300f,
) {
    var quietOthers: Float = 0.8f
    var probability: Float = 1f
        private set
    var gain: Float = 1f
        private set
    private var holdLeft = 0f

    fun targetProbability(i: GateInputs): Float {
        if (!i.hasLock) return 1f
        val lips = i.lockedSpeaking
        val voice = i.voiceMatch
        var p = if (voice == null) {
            // Face turned away or covered: no lip evidence, so stay neutral instead of muting them.
            if (!i.lockedVisible) 0.6f else lips
        } else {
            // Either strong cue is enough; both together is best.
            1f - (1f - lips * 0.9f) * (1f - voice * 0.85f)
        }
        // Someone else's lips are clearly moving while ours are still.
        if (i.othersSpeaking > 0.55f && lips < 0.3f) p *= 0.4f
        return p.coerceIn(0f, 1f)
    }

    fun process(i: GateInputs): Float {
        val p = targetProbability(i)
        if (i.voiceActive || !i.hasLock) {
            probability = p
            holdLeft = when {
                p > 0.5f -> holdMs
                p < 0.3f -> 0f              // someone else is clearly talking: end the hold now
                else -> (holdLeft - frameMs).coerceAtLeast(0f)
            }
        } else {
            holdLeft = (holdLeft - frameMs).coerceAtLeast(0f)
        }
        val effective = if (holdLeft > 0f) maxOf(probability, 0.9f) else probability
        val wanted = 1f - quietOthers * (1f - effective)
        val tau = if (wanted > gain) attackMs else releaseMs
        val alpha = 1f - exp(-frameMs / tau)
        gain += (wanted - gain) * alpha
        return gain
    }

    fun reset() { probability = 1f; gain = 1f; holdLeft = 0f }

    companion object {
        fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
        fun linearToDb(v: Float): Float = 20f * ln(v.coerceAtLeast(1e-9f)) / ln(10f)
    }
}

/** Tracks the noise floor and says whether a frame holds speech-level energy. */
class EnergyVad(private val marginDb: Float = 9f) {
    private var floorDb = -60f
    fun isVoice(frame: FloatArray): Boolean {
        var e = 0.0
        for (s in frame) e += (s * s).toDouble()
        val rms = kotlin.math.sqrt(e / frame.size.coerceAtLeast(1)).toFloat()
        val db = TargetGate.linearToDb(rms)
        // Floor follows quiet frames quickly and loud frames very slowly.
        floorDb += if (db < floorDb) (db - floorDb) * 0.2f else (db - floorDb) * 0.002f
        floorDb = floorDb.coerceIn(-90f, -20f)
        return db > floorDb + marginDb && db > -55f
    }
}
