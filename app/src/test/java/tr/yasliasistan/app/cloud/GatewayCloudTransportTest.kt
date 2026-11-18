package tr.yasliasistan.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.yasliasistan.app.overlay.BubbleLook
import tr.yasliasistan.app.overlay.OverlayState
import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.cloud.TransportResult
import tr.yasliasistan.speech.net.BlockedAddressException
import tr.yasliasistan.speech.net.HttpPoster
import tr.yasliasistan.speech.net.PostResponse
import java.io.IOException
import java.net.SocketTimeoutException

/** G-34: fake HTTP only, no real network. */
class GatewayCloudTransportTest {

    private fun transport(poster: HttpPoster) = GatewayCloudTransport(poster)

    @Test
    fun responseIsPassedOnWithShortTimeoutsAndUtf8Body() {
        var timeouts: Pair<Int, Int>? = null
        var sentBody = ""
        val t = transport { _, bearer, body, connect, read ->
            timeouts = connect to read
            sentBody = String(body, Charsets.UTF_8)
            assertEquals("KodKodKod1234", bearer)
            PostResponse(429, "{}", 7)
        }
        assertEquals(TransportResult.Response(429, "{}", 7), t.post("https://a.workers.dev/v1/generate", "KodKodKod1234", "{\"ş\":1}"))
        assertEquals(Defaults.CLOUD_CONNECT_TIMEOUT_MS to Defaults.CLOUD_READ_TIMEOUT_MS, timeouts)
        assertEquals("{\"ş\":1}", sentBody)
    }

    @Test
    fun failuresBecomeSafeResults() {
        assertEquals(TransportResult.Timeout, transport { _, _, _, _, _ -> throw SocketTimeoutException() }.post("u", "c", "{}"))
        assertEquals(TransportResult.Blocked, transport { _, _, _, _, _ -> throw BlockedAddressException() }.post("u", "c", "{}"))
        assertEquals(TransportResult.NetworkError, transport { _, _, _, _, _ -> throw IOException("reset") }.post("u", "c", "{}"))
        assertEquals(TransportResult.NetworkError, transport { _, _, _, _, _ -> throw IllegalStateException() }.post("u", "c", "{}"))
    }

    @Test
    fun bubbleShowsTheCloudLookButTheOpenMicrophoneWins() {
        assertEquals(BubbleLook.CLOUD, OverlayState(sessionActive = true, cloudBusy = true).bubbleLook)
        assertEquals(BubbleLook.LISTENING, OverlayState(listening = true, cloudBusy = true).bubbleLook)
        assertEquals(BubbleLook.IDLE, OverlayState(sessionActive = true).bubbleLook)
    }
}
