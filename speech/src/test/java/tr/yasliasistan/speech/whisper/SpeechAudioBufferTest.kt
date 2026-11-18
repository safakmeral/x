package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAudioBufferTest {

    private fun frame(value: Int, size: Int = 10) = ShortArray(size) { value.toShort() }

    @Test
    fun noSpeechGivesNoClip() {
        val buffer = SpeechAudioBuffer(maxSamples = 1000)
        buffer.append(frame(5), isSpeech = false)
        assertFalse(buffer.hasSpeech)
        assertNull(buffer.speechClip(padSamples = 5, minSamples = 0))
    }

    @Test
    fun clipCoversSpeechPlusPadding() {
        val buffer = SpeechAudioBuffer(maxSamples = 1000)
        buffer.append(frame(1), isSpeech = false)  // 0..9
        buffer.append(frame(2), isSpeech = false)  // 10..19
        buffer.append(frame(16384), isSpeech = true) // 20..29
        buffer.append(frame(3), isSpeech = false)  // 30..39
        buffer.append(frame(4), isSpeech = false)  // 40..49
        val clip = buffer.speechClip(padSamples = 5, minSamples = 0)!!
        assertEquals(20, clip.size) // 15..34
        assertEquals(2 / 32768f, clip[0])
        assertEquals(0.5f, clip[5])
        assertEquals(3 / 32768f, clip.last())
    }

    @Test
    fun onsetExtendsClipBackwards() {
        val buffer = SpeechAudioBuffer(maxSamples = 1000)
        repeat(5) { buffer.append(frame(1), isSpeech = false) }
        buffer.append(frame(100), isSpeech = true) // 50..59
        val clip = buffer.speechClip(padSamples = 0, minSamples = 0, onsetSamples = 20)!!
        assertEquals(30, clip.size) // 30..59
    }

    @Test
    fun paddingIsClampedToRecordedAudio() {
        val buffer = SpeechAudioBuffer(maxSamples = 1000)
        buffer.append(frame(100), isSpeech = true)
        assertEquals(10, buffer.speechClip(padSamples = 500, minSamples = 0)!!.size)
    }

    @Test
    fun shortClipIsPaddedWithSilenceToMinimum() {
        val buffer = SpeechAudioBuffer(maxSamples = 1000)
        buffer.append(frame(16384), isSpeech = true)
        val clip = buffer.speechClip(padSamples = 0, minSamples = 100)!!
        assertEquals(100, clip.size)
        assertEquals(0.5f, clip[9])
        assertEquals(0f, clip[10])
        assertEquals(0f, clip[99])
    }

    @Test
    fun appendStopsAtCapacityAndGrowsBeforeIt() {
        val buffer = SpeechAudioBuffer(maxSamples = 25, initialCapacity = 4)
        buffer.append(frame(1), isSpeech = true)
        buffer.append(frame(2), isSpeech = true)
        buffer.append(frame(3), isSpeech = true)
        assertEquals(25, buffer.size)
        assertTrue(buffer.isFull)
        val clip = buffer.speechClip(padSamples = 0, minSamples = 0)!!
        assertEquals(25, clip.size)
        assertEquals(3 / 32768f, clip.last())
    }

    @Test
    fun partialFrameLengthIsRespected() {
        val buffer = SpeechAudioBuffer(maxSamples = 100)
        buffer.append(frame(7, size = 10), length = 4, isSpeech = true)
        assertEquals(4, buffer.size)
    }

    @Test
    fun wipeRemovesAudioAndSpeechMarks() {
        val buffer = SpeechAudioBuffer(maxSamples = 100)
        buffer.append(frame(100), isSpeech = true)
        buffer.wipe()
        assertEquals(0, buffer.size)
        assertNull(buffer.speechClip(padSamples = 0, minSamples = 0))
        buffer.append(frame(0), isSpeech = true)
        assertArrayEquals(FloatArray(10), buffer.speechClip(padSamples = 0, minSamples = 0), 0f)
    }
}
