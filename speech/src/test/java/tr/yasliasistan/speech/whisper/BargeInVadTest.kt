package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** G-16: the stricter detector used while the assistant speaks (Whisper barge-in). */
class BargeInVadTest {

    private val frame = MicRecorder.FRAME
    private val frameMs = 30

    private fun tone(dbfs: Double): ShortArray {
        val amplitude = 32768.0 * Math.pow(10.0, dbfs / 20.0) * Math.sqrt(2.0)
        return ShortArray(frame) { i -> (amplitude * sin(2 * PI * 220 * i / 16_000)).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun EnergyVad.feed(level: Double, frames: Int): List<Boolean> = List(frames) { process(tone(level)) }

    /** Echo of the assistant's own voice: syllable-like loud/quiet chunks around -29 dBFS. */
    private fun EnergyVad.echo(seconds: Int) = repeat(seconds * 1000 / (6 * frameMs)) {
        feed(-24.0, 3)
        feed(-34.0, 3)
    }

    @Test
    fun ownVoiceEchoIsNotTheUser() {
        val vad = EnergyVad(EnergyVad.Config.DURING_SPEECH)
        vad.echo(seconds = 5)
        assertFalse(vad.heardSpeech)
    }

    @Test
    fun echoAlreadyPlayingWhenTheMicOpensIsLearned() {
        val vad = EnergyVad(EnergyVad.Config.DURING_SPEECH)
        vad.feed(-65.0, 3) // a moment of room sound, then the assistant's sentence
        vad.echo(seconds = 4)
        assertFalse(vad.heardSpeech)
    }

    @Test
    fun userOverTheEchoIsReportedWithin300Ms() {
        val vad = EnergyVad(EnergyVad.Config.DURING_SPEECH)
        vad.echo(seconds = 2)
        var frames = 0
        while (!vad.inSpeech && frames < 50) {
            vad.process(tone(-10.0)) // the user, close to the phone
            frames++
        }
        assertTrue(vad.inSpeech)
        assertTrue("detected after ${frames * frameMs} ms", frames * frameMs <= 300)
    }

    @Test
    fun nothingCountsDuringTheSettleTime() {
        val vad = EnergyVad(EnergyVad.Config.DURING_SPEECH)
        vad.feed(-65.0, 4)
        assertTrue(vad.feed(-10.0, EnergyVad.Config.DURING_SPEECH.settleFrames).none { it })
    }

    @Test
    fun defaultDetectorIsUnchanged() {
        val vad = EnergyVad()
        vad.feed(-65.0, 10)
        assertTrue(vad.feed(-25.0, 3).last())
    }
}
