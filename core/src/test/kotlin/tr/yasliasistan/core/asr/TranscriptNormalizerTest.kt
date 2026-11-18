package tr.yasliasistan.core.asr

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.yasliasistan.core.asr.NormalizationLevel.BASIC
import tr.yasliasistan.core.asr.NormalizationLevel.RAW
import tr.yasliasistan.core.asr.NormalizationLevel.TURKISH

class TranscriptNormalizerTest {

    private fun t(text: String, level: NormalizationLevel) = TranscriptNormalizer.text(text, level)

    @Test
    fun rawOnlySplitsOnWhitespace() {
        assertEquals("Ayarları aç.", t("  Ayarları   aç. ", RAW))
    }

    @Test
    fun basicLowercasesTurkishAndDropsPunctuation() {
        assertEquals("ırmak işte ayarları aç", t("IRMAK İŞTE Ayarları, aç!", BASIC))
        assertEquals("whatsappı aç", t("WhatsApp'ı aç.", BASIC))
        assertEquals("wi fiyi kapat", t("Wi-Fi'yi kapat", BASIC))
    }

    @Test
    fun turkishFoldsLetters() {
        assertEquals("kiziniza mesaj gonder", t("Kızınıza mesaj gönder", TURKISH))
    }

    @Test
    fun turkishWritesNumberWordsAsDigits() {
        val cases = mapOf(
            "beş dakika sonra" to "5 dakika sonra",
            "yirmi beş" to "25",
            "yüz yirmi üç" to "123",
            "iki bin yirmi altı" to "2026",
            "on bir" to "11",
            "beş yüz" to "500",
            "bin dokuz yüz seksen" to "1980",
            "yedide alarm kur" to "7 alarm kur",
            "saat on beş otuzda" to "saat 1530",
            "sıfır beş yüz elli beş" to "0555",
            "beş beş beş" to "555",
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, t(input, TURKISH)) }
    }

    @Test
    fun turkishKeepsWordsThatOnlyLookLikeNumbers() {
        assertEquals("ona mesaj at", t("ona mesaj at", TURKISH))
        assertEquals("masanin altinda", t("masanın altında", TURKISH))
        assertEquals("kalemin ucu", t("kalemin ucu", TURKISH))
    }

    @Test
    fun turkishHandlesDigitsAsWritten() {
        assertEquals("7 alarm", t("7'de alarm", TURKISH))
        assertEquals("1000", t("1.000", TURKISH))
        assertEquals("05550000001", t("0555 000 00 01", TURKISH))
        assertEquals("05551112233", t("sıfır beş yüz elli beş, yüz on bir, yirmi iki, otuz üç", TURKISH))
    }

    @Test
    fun turkishJoinsApostrophesBeforeFolding() {
        assertEquals(t("Ayşeyi ara", TURKISH), t("Ayşe'yi ara", TURKISH))
        assertEquals("whatsappta", t("WhatsApp’ta", TURKISH))
    }

    @Test
    fun emptyAndPunctuationOnlyGiveNoTokens() {
        for (level in NormalizationLevel.entries) {
            assertEquals(emptyList<String>(), TranscriptNormalizer.tokens("", level))
        }
        assertEquals(emptyList<String>(), TranscriptNormalizer.tokens(" ... ", BASIC))
    }
}
