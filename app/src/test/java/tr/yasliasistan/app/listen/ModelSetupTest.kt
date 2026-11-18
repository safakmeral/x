package tr.yasliasistan.app.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.speech.recognition.EngineAvailability
import tr.yasliasistan.speech.whisper.DownloadResult

class ModelSetupTest {

    private fun advice(a: EngineAvailability?, d: ModelDownloadUi = ModelDownloadUi.Idle) = ModelAdvice.from(a, d)

    @Test
    fun missingModelIsOfferedButNeverDownloadedWithoutATap() {
        val a = advice(EngineAvailability.LANGUAGE_NOT_INSTALLED)
        assertEquals(ModelAdvice.OFFER_DOWNLOAD, a)
        assertTrue(a.offerDownload)
        assertFalse(a.offerTyping)
    }

    @Test
    fun installedModelIsReady() {
        assertEquals(ModelAdvice.READY, advice(EngineAvailability.AVAILABLE))
        assertEquals(ModelAdvice.CHECKING, advice(null))
    }

    @Test
    fun runningDownloadShowsProgress() {
        val running = ModelDownloadUi.Running(74_000_000, 148_000_000)
        assertEquals(ModelAdvice.DOWNLOADING, advice(EngineAvailability.LANGUAGE_NOT_INSTALLED, running))
        assertEquals(50, running.percent)
        assertEquals(0, ModelDownloadUi.Running(5, 0).percent)
    }

    @Test
    fun failuresOfferRetryAndTyping() {
        mapOf(
            DownloadResult.NETWORK to ModelAdvice.FAILED_NETWORK,
            DownloadResult.CHECKSUM to ModelAdvice.FAILED_CHECKSUM,
            DownloadResult.NO_SPACE to ModelAdvice.FAILED_SPACE,
        ).forEach { (result, expected) ->
            val a = advice(EngineAvailability.LANGUAGE_NOT_INSTALLED, ModelDownloadUi.Ended(result))
            assertEquals(expected, a)
            assertTrue(a.offerDownload)
            assertTrue(a.offerTyping)
        }
    }

    @Test
    fun cancelledDownloadCanBeStartedAgain() {
        val a = advice(EngineAvailability.LANGUAGE_NOT_INSTALLED, ModelDownloadUi.Ended(DownloadResult.CANCELLED))
        assertEquals(ModelAdvice.OFFER_DOWNLOAD, a)
    }

    @Test
    fun whisperThatCannotRunOffersTypingOnly() {
        val a = advice(EngineAvailability.NO_ON_DEVICE_RECOGNIZER)
        assertEquals(ModelAdvice.UNAVAILABLE, a)
        assertFalse(a.offerDownload)
        assertTrue(a.offerTyping)
    }
}
