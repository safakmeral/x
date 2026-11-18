package tr.yasliasistan.core.clarify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun primaryThenMobileThenFirst() {
        val home = PhoneEntry("0212 000 00 00", isMobile = false, isPrimary = false)
        val mobile = PhoneEntry("0500 000 00 01", isMobile = true, isPrimary = false)
        val primary = PhoneEntry("0212 000 00 02", isMobile = false, isPrimary = true)
        assertEquals(primary.number, PhoneNumbers.pick(listOf(home, mobile, primary)))
        assertEquals(mobile.number, PhoneNumbers.pick(listOf(home, mobile)))
        assertEquals(home.number, PhoneNumbers.pick(listOf(home)))
        assertNull(PhoneNumbers.pick(listOf(PhoneEntry("  ", isMobile = true, isPrimary = true))))
        assertNull(PhoneNumbers.pick(emptyList()))
    }

    @Test
    fun whatsAppNumbersGetTheCountryCode() {
        assertEquals("905000000001", PhoneNumbers.forWhatsApp("0500 000 00 01"))
        assertEquals("905000000001", PhoneNumbers.forWhatsApp("500 000 00 01"))
        assertEquals("905000000001", PhoneNumbers.forWhatsApp("+90 500 000 00 01"))
        assertEquals("905000000001", PhoneNumbers.forWhatsApp("0090 500 000 00 01"))
        assertEquals("491500000000", PhoneNumbers.forWhatsApp("+49 150 0000000"))
        assertNull(PhoneNumbers.forWhatsApp("112"))
    }
}
