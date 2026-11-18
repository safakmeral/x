package tr.yasliasistan.core.asr

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Builds measurement clips from mono float audio in [-1, 1] (G-13): silence padding, quiet room
 * noise for the "silent" clips, and speech mixed with background talk at a chosen SNR (a stand-in
 * for TV noise, NFG-21). Deterministic: the same seed gives the same clip.
 */
object AudioMix {

    fun rms(signal: FloatArray): Double {
        if (signal.isEmpty()) return 0.0
        var sum = 0.0
        for (s in signal) sum += s.toDouble() * s
        return sqrt(sum / signal.size)
    }

    fun dbfs(signal: FloatArray): Double = 20 * log10(rms(signal).coerceAtLeast(1e-10))

    fun silence(samples: Int) = FloatArray(samples)

    /** Gaussian noise with the given RMS level in dB full scale (e.g. -60 = very quiet room). */
    fun roomNoise(samples: Int, levelDbfs: Double, seed: Int): FloatArray {
        val random = Random(seed)
        val scale = 10.0.pow(levelDbfs / 20)
        return FloatArray(samples) { (gaussian(random) * scale).toFloat().coerceIn(-1f, 1f) }
    }

    /** [signal] with [before] and [after] samples of silence around it. */
    fun pad(signal: FloatArray, before: Int, after: Int): FloatArray {
        val out = FloatArray(before + signal.size + after)
        signal.copyInto(out, before)
        return out
    }

    /**
     * Overlapping talk: each source starts at a different offset and repeats to fill [samples];
     * the sum is scaled to unit RMS so [mixAtSnr] can set its level.
     */
    fun babble(sources: List<FloatArray>, samples: Int): FloatArray {
        val out = FloatArray(samples)
        sources.filter { it.isNotEmpty() }.forEachIndexed { index, src ->
            val offset = (index * src.size / (sources.size + 1))
            for (i in 0 until samples) out[i] += src[(i + offset) % src.size]
        }
        val level = rms(out)
        if (level > 0) for (i in out.indices) out[i] = (out[i] / level).toFloat()
        return out
    }

    /**
     * [speech] plus [noise] (repeated or cut to length) scaled to [snrDb]; result is clipped to [-1, 1].
     * [speechLevel] is the signal the SNR refers to; pass the unpadded speech when [speech] has
     * silence around it, otherwise the padding would lower the real SNR.
     */
    fun mixAtSnr(speech: FloatArray, noise: FloatArray, snrDb: Double, speechLevel: FloatArray = speech): FloatArray {
        val speechRms = rms(speechLevel)
        val noiseRms = rms(noise)
        if (speechRms == 0.0 || noiseRms == 0.0) return speech.copyOf()
        val gain = speechRms / (noiseRms * 10.0.pow(snrDb / 20))
        return FloatArray(speech.size) { i ->
            (speech[i] + noise[i % noise.size] * gain).toFloat().coerceIn(-1f, 1f)
        }
    }

    /** 16-bit PCM samples (what the system recognizer and WAV files take). */
    fun toPcm16(signal: FloatArray): ShortArray =
        ShortArray(signal.size) { (signal[it] * 32767f).toInt().coerceIn(-32768, 32767).toShort() }

    fun fromPcm16(samples: ShortArray): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }

    private fun gaussian(random: Random): Double {
        // Box-Muller
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
    }
}
