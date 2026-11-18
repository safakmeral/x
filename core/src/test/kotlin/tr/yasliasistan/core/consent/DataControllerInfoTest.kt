package tr.yasliasistan.core.consent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataControllerInfoTest {

    @Test
    fun placeholdersAreNotComplete() {
        val info = DataControllerInfo(DataControllerInfo.NAME_PLACEHOLDER, DataControllerInfo.CONTACT_PLACEHOLDER)
        assertFalse(info.isComplete)
        assertEquals(listOf("name", "contact"), info.missingFields())
    }

    @Test
    fun blankOrBracketedValuesAreNotComplete() {
        assertFalse(DataControllerInfo("  ", "a@b.c").isComplete)
        assertFalse(DataControllerInfo("Ad Soyad", "[ILETISIM]").isComplete)
        assertEquals(listOf("contact"), DataControllerInfo("Ad Soyad", "").missingFields())
    }

    @Test
    fun realValuesAreComplete() {
        assertTrue(DataControllerInfo("Ayşe Örnek", "ayse.ornek@example.com").isComplete)
    }
}
