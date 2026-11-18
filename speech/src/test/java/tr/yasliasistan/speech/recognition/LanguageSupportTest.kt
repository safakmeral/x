package tr.yasliasistan.speech.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSupportTest {

    private fun classify(installed: List<String>, pending: List<String> = emptyList(), supported: List<String> = emptyList()) =
        LanguageSupport.classify(installed, pending, supported, "tr-TR")

    @Test
    fun installedTurkishIsAvailable() {
        assertEquals(EngineAvailability.AVAILABLE, classify(listOf("en-US", "tr-TR")))
        assertEquals(EngineAvailability.AVAILABLE, classify(listOf("tr")))
        assertEquals(EngineAvailability.AVAILABLE, classify(listOf("TR_tr")))
    }

    @Test
    fun pendingTurkishIsDownloading() {
        assertEquals(EngineAvailability.LANGUAGE_DOWNLOADING, classify(listOf("en-US"), pending = listOf("tr-TR")))
    }

    @Test
    fun supportedButMissingNeedsDownload() {
        assertEquals(
            EngineAvailability.LANGUAGE_NOT_INSTALLED,
            classify(listOf("en-US"), supported = listOf("en-US", "tr-TR")),
        )
        assertEquals(EngineAvailability.LANGUAGE_NOT_INSTALLED, classify(listOf("en-US")))
    }

    @Test
    fun notOfferedIsNotSupported() {
        assertEquals(
            EngineAvailability.LANGUAGE_NOT_SUPPORTED,
            classify(listOf("en-US"), supported = listOf("en-US", "de-DE")),
        )
    }

    @Test
    fun emptyReportIsUnknownSoListeningDecides() {
        assertEquals(EngineAvailability.UNKNOWN, classify(emptyList()))
        assertTrue(EngineAvailability.UNKNOWN.canListen)
    }

    @Test
    fun onlyAvailableAndUnknownMayOpenTheMicrophone() {
        val listenable = EngineAvailability.entries.filter { it.canListen }.toSet()
        assertEquals(setOf(EngineAvailability.AVAILABLE, EngineAvailability.UNKNOWN), listenable)
    }

    @Test
    fun otherLanguagesDoNotMatchTurkish() {
        assertFalse(LanguageSupport.sameLanguage("tk-TM", "tr-TR"))
        assertFalse(LanguageSupport.sameLanguage("", "tr-TR"))
    }
}
