package tr.yasliasistan.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishTextTest {

    /** Input as a recognizer or keyboard may produce it -> expected normalized form. */
    private val table = listOf(
        "Ayarları aç" to "ayarlari ac",
        "AYARLARI AÇ" to "ayarlari ac",
        "ayarlari ac" to "ayarlari ac",
        "WhatsApp'ı aç." to "whatsapp ac",
        "WhatsApp’ı açar mısın?" to "whatsapp acar misin",
        "whatsapp‘a gir" to "whatsapp gir",
        "İnternet'i aç" to "internet ac",
        "INSTAGRAM'I AÇ" to "instagram ac",
        "Instagram" to "instagram",
        "ışık" to "isik",
        "IŞIK" to "isik",
        "Şebeke" to "sebeke",
        "Güneş" to "gunes",
        "Öğretmen" to "ogretmen",
        "ÇAĞRI" to "cagri",
        "  ayarlar   aç  " to "ayarlar ac",
        "Ayarlar, aç!" to "ayarlar ac",
        "Hava Durumu'nu aç" to "hava durumu ac",
        "Saat'i aç lütfen" to "saat ac lutfen",
        "YouTube'u aç" to "youtube ac",
        "Google Haritalar'ı açın" to "google haritalar acin",
        "Mesajlar'a gir" to "mesajlar gir",
        "kamerayı açabilir misin" to "kamerayi acabilir misin",
        "Galeri'yi aç" to "galeri ac",
        "Ayarlar'a git" to "ayarlar git",
        "'ayarlar'" to "ayarlar",
        "\"Ayarlar\" aç" to "ayarlar ac",
        "Ayarları aç..." to "ayarlari ac",
        "ayarları\taç\n" to "ayarlari ac",
        "Âlem" to "alem",
        "İİ" to "ii",
        "ÇALIŞTIR" to "calistir",
        "e-Devlet'i aç" to "e devlet ac",
        "Facebook'ta" to "facebook",
        "123 aç" to "123 ac",
        "" to "",
        "?!" to "",
    )

    @Test
    fun normalizationTable() {
        assertTrue("at least 30 cases", table.size >= 30)
        for ((input, expected) in table) {
            assertEquals("input: $input", expected, TurkishText.normalize(input))
        }
    }

    @Test
    fun tokensSplitNormalizedText() {
        assertEquals(listOf("whatsapp", "acar", "misin"), TurkishText.tokens("WhatsApp'ı açar mısın?"))
        assertEquals(emptyList<String>(), TurkishText.tokens("  "))
    }

    @Test
    fun suffixMatchingAcceptsCaseEndings() {
        assertTrue(TurkishText.isStemWithSuffix("ayarlar", "ayarlar"))
        assertTrue(TurkishText.isStemWithSuffix("ayarlari", "ayarlar"))
        assertTrue(TurkishText.isStemWithSuffix("ayarlara", "ayarlar"))
        assertTrue(TurkishText.isStemWithSuffix("kamerayi", "kamera"))
        assertTrue(TurkishText.isStemWithSuffix("telefonu", "telefon"))
        assertTrue(TurkishText.isStemWithSuffix("havadurumunu", "havadurumu"))
        assertTrue(TurkishText.isStemWithSuffix("galeriye", "galeri"))
    }

    @Test
    fun suffixMatchingRejectsOtherWords() {
        assertFalse(TurkishText.isStemWithSuffix("saatler", "saat"))
        assertFalse(TurkishText.isStemWithSuffix("telefonlarim", "telefon"))
        assertFalse(TurkishText.isStemWithSuffix("ayar", "ayarlar"))
        assertFalse(TurkishText.isStemWithSuffix("", "ayarlar"))
    }
}
