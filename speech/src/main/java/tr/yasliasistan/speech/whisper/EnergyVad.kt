package tr.yasliasistan.speech.whisper

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Simple energy-based voice activity detector for 16-bit PCM frames (G-12).
 *
 * A frame counts as voice when its level is [Config.marginDb] above the tracked noise floor and
 * above an absolute minimum. The noise floor follows quiet frames quickly and creeps up slowly
 * during loud frames, so a steady TV/fan sound is eventually treated as background, not speech.
 * Speech starts after [Config.onsetFrames] voiced frames in a row and ends after
 * [Config.hangoverFrames] unvoiced frames (short pauses between words stay "speech").
 *
 * Pure Kotlin, no Android types: unit-tested on the JVM.
 */
class EnergyVad(private val config: Config = Config()) {

    data class Config(
        /** Levels below this (dBFS) are never speech, however quiet the room is. */
        val minSpeechDbfs: Double = -48.0,
        /** How far above the noise floor a frame must be to count as voice. */
        val marginDb: Double = 10.0,
        /** The first frames only measure the room (users rarely talk in the first ~100 ms). */
        val calibrationFrames: Int = 4,
        /** Voiced frames in a row needed to start speech (filters clicks and taps). */
        val onsetFrames: Int = 3,
        /** Unvoiced frames in a row needed to end speech (30 ms frames -> 300 ms). */
        val hangoverFrames: Int = 10,
        /** Noise floor smoothing on quiet frames (0..1, higher = faster). */
        val quietAdapt: Double = 0.1,
        /** Noise floor rise per loud frame, in dB (0.1 dB x 33 frames/s = ~3 dB/s). */
        val loudRiseDb: Double = 0.1,
        /**
         * Frames after calibration that only learn the background, loud or not (never speech). Used
         * while the assistant speaks (G-16): the floor settles on the leftover echo of its own voice.
         */
        val settleFrames: Int = 0,
        /** Floor smoothing during [settleFrames] (0..1). */
        val settleAdapt: Double = 0.2,
    ) {
        companion object {
            /**
             * Barge-in (G-16): the user must be clearly louder than the assistant's echo. 600 ms to
             * settle on the echo, then 15 dB above it for 5 frames: user speech is reported 150 ms after
             * it starts, well inside the 300 ms target (PLAN G-16).
             */
            val DURING_SPEECH = Config(
                minSpeechDbfs = -40.0,
                marginDb = 15.0,
                onsetFrames = 5,
                settleFrames = 20,
                loudRiseDb = 0.2,
            )
        }
    }

    /** True while the detector considers the user to be talking. */
    var inSpeech = false
        private set

    /** True once any speech has been detected in this turn. */
    var heardSpeech = false
        private set

    /** Current noise floor estimate in dBFS (for diagnostics only). */
    var noiseFloorDbfs = SILENCE_DBFS
        private set

    private var frames = 0
    private var voicedRun = 0
    private var unvoicedRun = 0

    /** Processes one frame; returns true if the frame belongs to speech. */
    fun process(samples: ShortArray, length: Int = samples.size): Boolean {
        val level = levelDbfs(samples, length)
        frames++
        if (frames <= config.calibrationFrames) {
            noiseFloorDbfs = if (frames == 1) level else min(noiseFloorDbfs, level)
            return false
        }
        if (frames <= config.calibrationFrames + config.settleFrames) {
            noiseFloorDbfs += (level - noiseFloorDbfs) * config.settleAdapt
            return false
        }

        val voiced = level >= max(config.minSpeechDbfs, noiseFloorDbfs + config.marginDb)
        if (voiced) {
            voicedRun++
            unvoicedRun = 0
            noiseFloorDbfs = min(level, noiseFloorDbfs + config.loudRiseDb)
            if (!inSpeech && voicedRun >= config.onsetFrames) {
                inSpeech = true
                heardSpeech = true
            }
        } else {
            unvoicedRun++
            voicedRun = 0
            noiseFloorDbfs += (level - noiseFloorDbfs) * config.quietAdapt
            if (inSpeech && unvoicedRun >= config.hangoverFrames) inSpeech = false
        }
        return inSpeech
    }

    fun reset() {
        inSpeech = false
        heardSpeech = false
        noiseFloorDbfs = SILENCE_DBFS
        frames = 0
        voicedRun = 0
        unvoicedRun = 0
    }

    companion object {
        const val SILENCE_DBFS = -96.0

        /** RMS level of a 16-bit frame in dB relative to full scale (digital silence = -96). */
        fun levelDbfs(samples: ShortArray, length: Int = samples.size): Double {
            if (length <= 0) return SILENCE_DBFS
            var sum = 0.0
            for (i in 0 until length) {
                val s = samples[i].toDouble()
                sum += s * s
            }
            val rms = sqrt(sum / length)
            if (rms < 1.0) return SILENCE_DBFS
            return max(SILENCE_DBFS, 20.0 * log10(rms / 32768.0))
        }
    }
}
