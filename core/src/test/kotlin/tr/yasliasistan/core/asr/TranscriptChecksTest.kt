package tr.yasliasistan.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptChecksTest {

    @Test
    fun textFromSilenceIsHallucination() {
        assertTrue(TranscriptChecks.isHallucination("", "Altyazı M.K."))
        assertFalse(TranscriptChecks.isHallucination("", ""))
        assertFalse(TranscriptChecks.isHallucination("", " . "))
        assertFalse(TranscriptChecks.isHallucination("Saat kaç", "saat kaç"))
    }

    @Test
    fun repeatedWordsAreALoop() {
        assertTrue(TranscriptChecks.isLoop("Ayşe'yi ara", "Ayşe'yi ara ara ara ara"))
        assertTrue(TranscriptChecks.isLoop("Sesi kıs", "sesi kıs sesi kıs sesi kıs"))
        assertFalse(TranscriptChecks.isLoop("Sesi kıs", "sesi kıs sesi kıs"))
        assertFalse(TranscriptChecks.isLoop("Sesi kıs", "sesi kıs"))
    }

    @Test
    fun repetitionInTheReferenceIsNotALoop() {
        assertFalse(TranscriptChecks.isLoop("evet evet evet", "evet evet evet"))
    }

    @Test
    fun maxRepeatsFindsLongestRun() {
        assertEquals(0, TranscriptChecks.maxRepeats(emptyList()))
        assertEquals(1, TranscriptChecks.maxRepeats(listOf("a", "b", "c")))
        assertEquals(4, TranscriptChecks.maxRepeats("x a b a b a b a b y".split(" ")))
    }
}
