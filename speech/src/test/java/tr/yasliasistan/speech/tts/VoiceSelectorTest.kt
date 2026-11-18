package tr.yasliasistan.speech.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSelectorTest {

    private val samsung = "com.samsung.SMT"
    private val google = VoiceSelector.GOOGLE_ENGINE

    private fun voice(
        name: String,
        language: String = "tr",
        network: Boolean = false,
        installed: Boolean = true,
        quality: Int = 300,
    ) = VoiceInfo(name, language, network, installed, quality)

    private fun probe(
        pkg: String,
        isDefault: Boolean = false,
        language: LanguageSupport = LanguageSupport.INSTALLED,
        voices: List<VoiceInfo> = emptyList(),
    ) = EngineProbe(pkg, isDefault, language, voices)

    @Test
    fun noEnginesMeansInitFailed() {
        assertEquals(VoiceStatus.InitFailed, VoiceSelector.choose(emptyList()))
    }

    @Test
    fun englishOnlyDefaultFallsBackToGoogleTurkish() {
        // G-01 finding: Samsung default engine without Turkish, Google TTS also installed.
        val result = VoiceSelector.choose(
            listOf(
                probe(samsung, isDefault = true, language = LanguageSupport.NOT_SUPPORTED, voices = listOf(voice("en-us", "en"))),
                probe(google, voices = listOf(voice("tr-tr-x-mfm-local"))),
            ),
        )
        assertEquals(VoiceStatus.Ready(google, "tr-tr-x-mfm-local"), result)
    }

    @Test
    fun defaultEngineWinsWhenItHasTurkish() {
        val result = VoiceSelector.choose(
            listOf(
                probe(google, voices = listOf(voice("tr-google"))),
                probe(samsung, isDefault = true, voices = listOf(voice("tr-samsung"))),
            ),
        )
        assertEquals(VoiceStatus.Ready(samsung, "tr-samsung"), result)
    }

    @Test
    fun networkVoicesAreNeverChosen() {
        val result = VoiceSelector.choose(
            listOf(probe(google, language = LanguageSupport.MISSING_DATA, voices = listOf(voice("tr-network", network = true)))),
        )
        assertEquals(VoiceStatus.MissingData(google), result)
    }

    @Test
    fun notInstalledVoiceMeansMissingData() {
        val result = VoiceSelector.choose(
            listOf(
                probe(samsung, isDefault = true, language = LanguageSupport.NOT_SUPPORTED),
                probe(google, language = LanguageSupport.MISSING_DATA, voices = listOf(voice("tr-local", installed = false))),
            ),
        )
        assertEquals(VoiceStatus.MissingData(google), result)
    }

    @Test
    fun noTurkishAnywhereMeansNotSupported() {
        val result = VoiceSelector.choose(
            listOf(probe(samsung, isDefault = true, language = LanguageSupport.NOT_SUPPORTED, voices = listOf(voice("en", "en")))),
        )
        assertEquals(VoiceStatus.NotSupported, result)
    }

    @Test
    fun highestQualityOfflineVoiceIsPicked() {
        val best = VoiceSelector.bestOfflineVoice(
            probe(google, voices = listOf(voice("b-low", quality = 200), voice("a-high", quality = 400), voice("c-net", network = true, quality = 500))),
        )
        assertEquals("a-high", best?.name)
    }

    @Test
    fun engineWithoutAVoiceListIsNeverReady() {
        // UD-1 Ö-5: without a voice list, "offline" cannot be proven; the engine might pick a network voice.
        assertEquals(VoiceStatus.MissingData(samsung), VoiceSelector.choose(listOf(probe(samsung, isDefault = true))))
        assertEquals(
            VoiceStatus.MissingData(samsung),
            VoiceSelector.choose(listOf(probe(samsung, isDefault = true, language = LanguageSupport.MISSING_DATA))),
        )
    }

    @Test
    fun silentFallbackToAnotherEngineIsNotTakenForThatEngine() {
        // A15: Samsung's engine is private; asking for it binds Google instead.
        val googleProbe = probe(google, voices = listOf(voice("tr-local")), language = LanguageSupport.INSTALLED).copy(allVoicesKey = "K")
        val fakeSamsung = probe(samsung, isDefault = true, voices = listOf(voice("tr-local"))).copy(allVoicesKey = "K")

        assertEquals(listOf(googleProbe), VoiceSelector.dropFallbacks(listOf(googleProbe, fakeSamsung)))
        assertEquals(VoiceStatus.Ready(google, "tr-local"), VoiceSelector.choose(listOf(googleProbe, fakeSamsung)))
    }

    @Test
    fun onlyGoogleTheDefaultOrSystemEnginesAreTrusted() {
        assertTrue(VoiceSelector.isTrustedEngine(google, defaultPackage = null, isSystemApp = false))
        assertTrue(VoiceSelector.isTrustedEngine("x.tts", defaultPackage = "x.tts", isSystemApp = false))
        assertTrue(VoiceSelector.isTrustedEngine(samsung, defaultPackage = null, isSystemApp = true))
        assertFalse(VoiceSelector.isTrustedEngine("some.thirdparty.tts", defaultPackage = google, isSystemApp = false))
    }

    @Test
    fun installedLanguageWithOnlyNetworkTurkishVoicesIsNotReady() {
        val result = VoiceSelector.choose(listOf(probe(google, voices = listOf(voice("tr-net", network = true)))))
        assertEquals(VoiceStatus.MissingData(google), result)
    }
}
