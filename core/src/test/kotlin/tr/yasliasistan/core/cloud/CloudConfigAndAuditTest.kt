package tr.yasliasistan.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudConfigAndAuditTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun onlyPlainWorkersDevAddressesAreAccepted() {
        assertEquals(
            "https://yasli-asistan-vekil.ornek.workers.dev/v1/generate",
            ProxyAddress.parse(" https://yasli-asistan-vekil.ornek.workers.dev/ ")?.endpoint,
        )
        assertNotNull(ProxyAddress.parse("HTTPS://Vekil.Ornek.Workers.Dev"))
        listOf(
            "http://vekil.ornek.workers.dev", // no cleartext
            "https://example.com",
            "https://workers.dev",
            "https://vekil.workers.dev.evil.com",
            "https://evilworkers.dev",
            "https://user@vekil.ornek.workers.dev",
            "https://vekil.ornek.workers.dev:8443",
            "https://vekil.ornek.workers.dev/v1/generate",
            "https://vekil.ornek.workers.dev/?x=1",
            "https://-bad.workers.dev",
            "vekil.ornek.workers.dev",
            "",
        ).forEach { assertNull(it, ProxyAddress.parse(it)) }
        assertTrue(ProxyAddress.isAllowedHost("a.b.workers.dev"))
        assertFalse(ProxyAddress.isAllowedHost("a_b.workers.dev"))
    }

    @Test
    fun accessCodeNeedsTwelveLettersOrDigitsAndIsNeverPrinted() {
        val code = AccessCode.parse("  Kx7mPq2Lw9Zt  ")
        assertEquals("Kx7mPq2Lw9Zt", code?.value)
        assertFalse("Kx7m" in code.toString())
        assertNull(AccessCode.parse("kisa123"))
        assertNull(AccessCode.parse("bosluk var 123456"))
        assertNull(AccessCode.parse("virgul,123456789"))
        assertFalse("Kx7m" in CloudConfig(ProxyAddress.parse("https://a.workers.dev")!!, code!!).toString())
    }

    @Test
    fun auditCopiesAreKeptSevenDaysThenDeleted() {
        val clock = MutableClock()
        val store = AuditCopyStore(tmp.root, clock)
        store.record(AuditCopy(clock.now, 1, "<komut>[KISI_1]'i ara</komut>"))
        store.record(AuditCopy(clock.now, 1, "ikinci"))
        assertEquals(listOf("<komut>[KISI_1]'i ara</komut>", "ikinci"), store.list().map { it.maskedUserText })

        clock.now += 6 * DAY
        store.record(AuditCopy(clock.now, 1, "yeni"))
        assertEquals(3, store.list().size)

        clock.now += 1 * DAY + 1
        assertEquals(listOf("yeni"), store.list().map { it.maskedUserText })

        store.clear()
        assertTrue(store.list().isEmpty())
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
