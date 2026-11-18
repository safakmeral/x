package tr.yasliasistan.core.cloud

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.llm.PromptTemplate
import tr.yasliasistan.core.llm.ResponseSchema

/** PLAN G-34 acceptance with a fake HTTP transport (no real network). */
class CloudClientTest {

    private val gate = SwitchGate()
    private val clock = MutableClock()
    private val copies = mutableListOf<AuditCopy>()
    private val indicator = mutableListOf<Boolean>()
    private var config: CloudConfig? = CloudFixtures.config

    private fun client(transport: CloudTransport) = CloudClient(
        gate = gate,
        config = { config },
        transport = transport,
        audit = object : AuditCopySink {
            override fun record(copy: AuditCopy) {
                copies += copy
            }
        },
        indicator = { indicator += it },
        clock = clock,
    )

    @Test
    fun withoutConsentNoRequestIsEverMade() {
        gate.allowed = false
        val transport = FakeTransport()
        val c = client(transport)
        assertEquals(CloudFailure.NO_CONSENT, c.precheck())
        assertEquals(CloudOutcome.Failed(CloudFailure.NO_CONSENT), c.generate(CloudFixtures.audited()))
        assertEquals(0, transport.requests.size)
        assertTrue("no audit copy without a request", copies.isEmpty())
        assertTrue("indicator never shown", indicator.isEmpty())
    }

    @Test
    fun revokedConsentStopsTheNextCallAtOnce() {
        val transport = FakeTransport()
        val c = client(transport)
        assertTrue(c.generate(CloudFixtures.audited()) is CloudOutcome.Answer)
        gate.allowed = false
        repeat(3) { c.generate(CloudFixtures.audited()) }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun withoutAddressAndCodeTheCloudIsOff() {
        config = null
        val transport = FakeTransport()
        val c = client(transport)
        assertEquals(CloudFailure.NOT_CONFIGURED, c.precheck())
        assertEquals(CloudOutcome.Failed(CloudFailure.NOT_CONFIGURED), c.generate(CloudFixtures.audited()))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun manyRequestsInARowAreNeverLimitedOnTheDevice() {
        val transport = FakeTransport()
        val c = client(transport)
        repeat(60) { assertTrue(c.generate(CloudFixtures.audited()) is CloudOutcome.Answer) }
        assertEquals(60, transport.requests.size)
        assertNull(c.precheck())
    }

    @Test
    fun requestFollowsTheContractAndCarriesOnlyMaskedText() {
        val transport = FakeTransport()
        client(transport).generate(CloudFixtures.audited())
        val sent = transport.requests.single()
        assertEquals("https://vekil.ornek.workers.dev/v1/generate", sent.url)
        assertEquals(CloudFixtures.CODE, sent.bearer)
        assertEquals(setOf("v", "system", "user", "schema"), sent.body.keys)
        assertEquals(JsonPrimitive(1), sent.body["v"])
        assertEquals(PromptTemplate.SYSTEM, (sent.body["system"] as JsonPrimitive).content)
        assertEquals(ResponseSchema.json, sent.body["schema"] as JsonObject)
        val user = (sent.body["user"] as JsonPrimitive).content
        assertTrue(user, "[TEL_1]" in user)
        assertFalse(user, "0555" in user)
    }

    @Test
    fun auditCopyIsTheMaskedUserText() {
        val transport = FakeTransport()
        client(transport).generate(CloudFixtures.audited())
        val copy = copies.single()
        assertEquals((transport.requests.single().body["user"] as JsonPrimitive).content, copy.maskedUserText)
        assertEquals(PromptTemplate.VERSION, copy.promptVersion)
        assertFalse("0555" in copy.maskedUserText)
        assertFalse("000 00 01" in copy.maskedUserText)
    }

    @Test
    fun indicatorIsOnOnlyWhileTheRequestTravels() {
        var seenDuringRequest: List<Boolean>? = null
        val c = client { _, _, _ ->
            seenDuringRequest = indicator.toList()
            TransportResult.Timeout
        }
        c.generate(CloudFixtures.audited())
        assertEquals(listOf(true), seenDuringRequest)
        assertEquals(listOf(true, false), indicator)
    }

    @Test
    fun quotaExhaustedSwitchesToOnDeviceWithoutRetry() {
        val transport = FakeTransport { FakeTransport.error(503, "QUOTA_EXHAUSTED", onDevice = true) }
        val c = client(transport)
        assertEquals(CloudOutcome.Failed(CloudFailure.QUOTA_EXHAUSTED), c.generate(CloudFixtures.audited()))
        assertEquals("exactly one call, no retry", 1, transport.requests.size)
        // Following commands stay on the device for a while: no call, same honest answer.
        assertEquals(CloudFailure.QUOTA_EXHAUSTED, c.precheck())
        assertEquals(CloudOutcome.Failed(CloudFailure.QUOTA_EXHAUSTED), c.generate(CloudFixtures.audited()))
        assertEquals(1, transport.requests.size)
        clock.now += Defaults.CLOUD_QUOTA_PAUSE_MS + 1
        assertNull(c.precheck())
        c.generate(CloudFixtures.audited())
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun timeoutAndNetworkErrorsEndSafelyWithOneAttempt() {
        val timeout = FakeTransport { TransportResult.Timeout }
        assertEquals(CloudOutcome.Failed(CloudFailure.TIMEOUT), client(timeout).generate(CloudFixtures.audited()))
        assertEquals(1, timeout.requests.size)

        val offline = FakeTransport { TransportResult.NetworkError }
        assertEquals(CloudOutcome.Failed(CloudFailure.NETWORK), client(offline).generate(CloudFixtures.audited()))

        val throwing = client { _, _, _ -> throw IllegalStateException("boom") }
        assertEquals(CloudOutcome.Failed(CloudFailure.NETWORK), throwing.generate(CloudFixtures.audited()))
        assertEquals(false, indicator.last())
    }

    @Test
    fun proxyErrorsAreMappedForTheirOwnSentence() {
        fun outcome(r: TransportResult) = client(FakeTransport { r }).generate(CloudFixtures.audited())
        assertEquals(CloudOutcome.Failed(CloudFailure.UNAUTHORIZED), outcome(FakeTransport.error(401, "UNAUTHORIZED")))
        assertEquals(CloudOutcome.Failed(CloudFailure.UNAVAILABLE), outcome(FakeTransport.error(503, "PROVIDERS_UNAVAILABLE", true)))
        assertEquals(CloudOutcome.Failed(CloudFailure.UNAVAILABLE), outcome(FakeTransport.error(502, "UPSTREAM_REJECTED", true)))
        assertEquals(CloudOutcome.Failed(CloudFailure.UNAVAILABLE), outcome(FakeTransport.error(500, "SERVER_MISCONFIGURED", true)))
        assertEquals(CloudOutcome.Failed(CloudFailure.REQUEST_REJECTED), outcome(FakeTransport.error(400, "INVALID_REQUEST")))
        assertEquals(CloudOutcome.Failed(CloudFailure.REQUEST_REJECTED), outcome(FakeTransport.error(413, "PAYLOAD_TOO_LARGE")))
        // Cloudflare's own HTML page (e.g. Error 1027) is a server failure.
        assertEquals(
            CloudOutcome.Failed(CloudFailure.UNAVAILABLE),
            outcome(TransportResult.Response(429, "<html>error code: 1027</html>")),
        )
        assertEquals(CloudOutcome.Failed(CloudFailure.BAD_RESPONSE), outcome(TransportResult.Response(200, "not json")))
        assertEquals(CloudOutcome.Failed(CloudFailure.BAD_RESPONSE), outcome(TransportResult.Response(200, """{"v":2,"ok":true,"result":{}}""")))
        assertEquals(CloudOutcome.Failed(CloudFailure.BAD_RESPONSE), outcome(TransportResult.Response(200, """{"v":1,"ok":true,"result":"x"}""")))
    }

    @Test
    fun rateLimitWaitsOnlyAsLongAsTheProxyAsks() {
        val transport = FakeTransport { if (it == 1) FakeTransport.error(429, "RATE_LIMITED", retryAfter = 5) else FakeTransport.ok(FakeTransport.PLAN_RESULT) }
        val c = client(transport)
        assertEquals(CloudOutcome.Failed(CloudFailure.RATE_LIMITED), c.generate(CloudFixtures.audited()))
        assertEquals(CloudFailure.RATE_LIMITED, c.precheck())
        clock.now += 5_001
        assertTrue(c.generate(CloudFixtures.audited()) is CloudOutcome.Answer)
        assertEquals(2, transport.requests.size)
    }
}
