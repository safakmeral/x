package tr.yasliasistan.speech.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptFilterTest {

    @Test
    fun emptyAndPunctuationAreNotCommands() {
        listOf(null, "", "   ", ".", " ?! ", "...").forEach { assertNull(it, TranscriptFilter.usableText(it)) }
    }

    @Test
    fun hesitationSoundsAreNotCommands() {
        listOf("ı", "ııı", "Eee", "hmm", "Hıı", "mmm", "ııı eee", "Ah.", "öff").forEach {
            assertNull(it, TranscriptFilter.usableText(it))
        }
    }

    @Test
    fun realCommandsAreKeptAndTidied() {
        assertEquals("Ayarları aç", TranscriptFilter.usableText("  Ayarları   aç "))
        assertEquals("eee Ayşe Test'i ara", TranscriptFilter.usableText("eee Ayşe Test'i ara"))
        assertEquals("evet", TranscriptFilter.usableText("evet"))
        assertEquals("dur", TranscriptFilter.usableText("Dur".lowercase()))
    }
}
