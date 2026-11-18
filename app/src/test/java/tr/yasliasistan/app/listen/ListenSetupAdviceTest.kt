package tr.yasliasistan.app.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.speech.recognition.EngineAvailability

class ListenSetupAdviceTest {

    @Test
    fun everyAvailabilityHasAdvice() {
        assertEquals(LanguageAdvice.CHECKING, LanguageAdvice.from(null))
        assertEquals(LanguageAdvice.READY, LanguageAdvice.from(EngineAvailability.AVAILABLE))
        assertEquals(LanguageAdvice.DOWNLOAD_NEEDED, LanguageAdvice.from(EngineAvailability.LANGUAGE_NOT_INSTALLED))
        assertEquals(LanguageAdvice.DOWNLOADING, LanguageAdvice.from(EngineAvailability.LANGUAGE_DOWNLOADING))
        assertEquals(LanguageAdvice.NOT_SUPPORTED, LanguageAdvice.from(EngineAvailability.LANGUAGE_NOT_SUPPORTED))
        assertEquals(LanguageAdvice.NOT_SUPPORTED, LanguageAdvice.from(EngineAvailability.NO_ON_DEVICE_RECOGNIZER))
        assertEquals(LanguageAdvice.ANDROID_TOO_OLD, LanguageAdvice.from(EngineAvailability.ANDROID_TOO_OLD))
        assertEquals(LanguageAdvice.UNKNOWN, LanguageAdvice.from(EngineAvailability.UNKNOWN))
    }

    @Test
    fun missingPackOffersDownloadAndSettings() {
        val advice = LanguageAdvice.DOWNLOAD_NEEDED
        assertTrue(advice.offerDownload && advice.offerSettings && advice.offerRecheck)
    }

    @Test
    fun micAdviceFollowsPermissionState() {
        assertEquals(MicAdvice.GRANTED, MicAdvice.from(granted = true, askedBefore = true, rationaleAllowed = false))
        assertEquals(MicAdvice.ASK, MicAdvice.from(granted = false, askedBefore = false, rationaleAllowed = false))
        assertEquals(MicAdvice.ASK, MicAdvice.from(granted = false, askedBefore = true, rationaleAllowed = true))
        assertEquals(
            MicAdvice.OPEN_APP_SETTINGS,
            MicAdvice.from(granted = false, askedBefore = true, rationaleAllowed = false),
        )
    }

    @Test
    fun tryOutNeedsPermissionAndAnOfflineModel() {
        assertTrue(canTryOut(MicAdvice.GRANTED, LanguageAdvice.READY))
        assertTrue(canTryOut(MicAdvice.GRANTED, LanguageAdvice.UNKNOWN))
        assertFalse(canTryOut(MicAdvice.ASK, LanguageAdvice.READY))
        LanguageAdvice.entries.filter { it != LanguageAdvice.READY && it != LanguageAdvice.UNKNOWN }.forEach {
            assertFalse(it.name, canTryOut(MicAdvice.GRANTED, it))
        }
    }
}
