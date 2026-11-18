package tr.yasliasistan.core.asr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class AudioMixTest {

    private fun tone(samples: Int, amp: Float, freq: Double = 440.0) =
        FloatArray(samples) { (amp * sin(2 * PI * freq * it / 16_000)).toFloat() }

    @Test
    fun roomNoiseHasRequestedLevelAndIsDeterministic() {
        val a = AudioMix.roomNoise(32_000, -50.0, seed = 7)
        assertEquals(-50.0, AudioMix.dbfs(a), 0.5)
        assertArrayEquals(a, AudioMix.roomNoise(32_000, -50.0, seed = 7), 0f)
    }

    @Test
    fun mixHitsTargetSnr() {
        val speech = tone(16_000, 0.3f)
        val noise = AudioMix.roomNoise(8_000, -20.0, seed = 1)
        val mixed = AudioMix.mixAtSnr(speech, noise, snrDb = 5.0)
        assertEquals(speech.size, mixed.size)
        val residual = FloatArray(mixed.size) { mixed[it] - speech[it] }
        val snr = 20 * log10(AudioMix.rms(speech) / AudioMix.rms(residual))
        assertEquals(5.0, snr, 0.1)
    }

    @Test
    fun babbleIsUnitLevelAndFillsLength() {
        val b = AudioMix.babble(listOf(tone(4_000, 0.5f), tone(6_000, 0.2f, 300.0)), 20_000)
        assertEquals(20_000, b.size)
        assertEquals(1.0, AudioMix.rms(b), 1e-3)
    }

    @Test
    fun padAddsSilenceAroundSignal() {
        val out = AudioMix.pad(floatArrayOf(0.5f, -0.5f), before = 3, after = 2)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0.5f, -0.5f, 0f, 0f), out, 0f)
    }

    @Test
    fun pcmRoundTripKeepsSamples() {
        val signal = floatArrayOf(0f, 0.5f, -0.5f, 0.999f)
        val back = AudioMix.fromPcm16(AudioMix.toPcm16(signal))
        for (i in signal.indices) assertEquals(signal[i], back[i], 1e-4f)
        assertTrue(AudioMix.toPcm16(floatArrayOf(2f))[0] == Short.MAX_VALUE)
    }
}
