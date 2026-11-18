package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EnergyVadTest {

    private val frame = 480

    /** A 30 ms sine frame at the given level (dBFS of the RMS). */
    private fun tone(dbfs: Double): ShortArray {
        val amplitude = 32768.0 * Math.pow(10.0, dbfs / 20.0) * Math.sqrt(2.0)
        return ShortArray(frame) { i -> (amplitude * sin(2 * PI * 220 * i / 16_000)).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun EnergyVad.feed(level: Double, frames: Int): List<Boolean> = List(frames) { process(tone(level)) }

    @Test
    fun levelOfDigitalSilenceIsFloor() {
        assertEquals(EnergyVad.SILENCE_DBFS, EnergyVad.levelDbfs(ShortArray(frame)), 0.0)
    }

    @Test
    fun levelOfKnownToneIsMeasured() {
        assertEquals(-20.0, EnergyVad.levelDbfs(tone(-20.0)), 0.5)
    }

    @Test
    fun silenceIsNeverSpeech() {
        val vad = EnergyVad()
        assertTrue(vad.feed(-70.0, 100).none { it })
        assertFalse(vad.heardSpeech)
    }

    @Test
    fun voiceAfterQuietRoomStartsSpeechAfterOnset() {
        val vad = EnergyVad()
        vad.feed(-65.0, 10)
        val result = vad.feed(-25.0, 3)
        assertEquals(listOf(false, false, true), result)
        assertTrue(vad.heardSpeech)
    }

    @Test
    fun singleClickIsNotSpeech() {
        val vad = EnergyVad()
        vad.feed(-65.0, 10)
        vad.feed(-20.0, 2)
        vad.feed(-65.0, 20)
        assertFalse(vad.heardSpeech)
    }

    @Test
    fun shortPauseKeepsSpeechLongPauseEndsIt() {
        val vad = EnergyVad()
        vad.feed(-65.0, 10)
        vad.feed(-25.0, 10)
        assertTrue("200 ms pause is still speech", vad.feed(-65.0, 7).all { it })
        vad.feed(-25.0, 5)
        val afterLongPause = vad.feed(-65.0, 15)
        assertFalse(vad.inSpeech)
        assertTrue(afterLongPause.first())
        assertFalse(afterLongPause.last())
    }

    @Test
    fun quietSoundBelowAbsoluteMinimumIsNotSpeech() {
        val vad = EnergyVad()
        vad.feed(-90.0, 10)
        vad.feed(-55.0, 20) // 35 dB above the floor but below -48 dBFS
        assertFalse(vad.heardSpeech)
    }

    @Test
    fun steadyNoiseStopsCountingAsSpeech() {
        val vad = EnergyVad()
        vad.feed(-70.0, 10)
        vad.feed(-38.0, 300) // ~9 s of constant TV-like noise
        assertFalse("steady noise must become background", vad.inSpeech)
        // ...and real speech clearly louder than that noise is still detected.
        vad.feed(-18.0, 5)
        assertTrue(vad.inSpeech)
    }

    @Test
    fun resetForgetsEverything() {
        val vad = EnergyVad()
        vad.feed(-65.0, 10)
        vad.feed(-25.0, 5)
        vad.reset()
        assertFalse(vad.inSpeech)
        assertFalse(vad.heardSpeech)
        assertEquals(EnergyVad.SILENCE_DBFS, vad.noiseFloorDbfs, 0.0)
    }
}
