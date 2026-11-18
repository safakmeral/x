package tr.yasliasistan.core.masking

import org.junit.Assert.assertEquals
import org.junit.Test

/** The generated DefaultMaskingLexicon must equal the binding dictionaries of the corpus. */
@Suppress("UNCHECKED_CAST")
class MaskingLexiconTest {
    private val s = MaskCorpus.dictionaries
    private val lex = MaskingLexicon.default()

    private fun list(key: String) = s[key] as List<String>
    private fun sub(key: String) = s[key] as Map<String, Any?>

    @Test
    fun wordListsMatchCorpus() {
        assertEquals(list("komut"), lex.commandWords)
        assertEquals(list("akrabalik"), lex.kinship)
        assertEquals(list("hitap"), lex.honorifics)
        assertEquals(s["ekler"], lex.suffixes)
        assertEquals((s["yumusama"] as Map<String, String>).map { it.key[0] to it.value[0] }.toMap(), lex.softening)
        assertEquals(s["kisi_yuvasi_tetikleyicileri"], lex.personSlotTriggers)
        assertEquals(sub("dikte")["once_isaretleri"], lex.dictationBefore)
        assertEquals(sub("dikte")["sonra_isaretleri"], lex.dictationAfter)
        assertEquals(sub("dikte")["iki_nokta_sozcukleri"], lex.dictationColonWords)
        assertEquals(sub("dikte")["fiiller"], lex.dictationVerbs)
        assertEquals(sub("adres")["anahtarlar"], lex.addressAnchors)
        assertEquals(sub("adres")["numara_gostergeleri"], lex.addressNumberMarkers)
        assertEquals(s["sayi_sozcukleri"], lex.numberWords)
        assertEquals(sub("ozel_terimler")["tek"], lex.sensitiveRoots)
        assertEquals(sub("ozel_terimler")["obek"], lex.sensitivePhrases)
        assertEquals(sub("gizli")["tek_basina"], lex.secretPhrases)
        assertEquals(sub("gizli")["rakamla_gizli_bilgi"], lex.secretWithDigits)
        assertEquals(sub("gizli")["rakamla_dogrulama_kodu"], lex.codeWithDigits)
        assertEquals(sub("gizli")["haric"], lex.codeExceptions)
        assertEquals(list("durum"), lex.statusWords)
        assertEquals(list("eposta_tld"), lex.emailTlds)
    }

    @Test
    fun settingsMatchCorpus() {
        val a = MaskCorpus.settings
        val st = lex.settings
        assertEquals(a["niyet_esigi_sozcuk"], st.intentModeWordThreshold.toLong())
        assertEquals(a["en_kisa_rakam_dizisi"], st.minDigitRun.toLong())
        assertEquals(a["sayi_sozcugu_esigi"], st.numberWordThreshold.toLong())
        assertEquals(a["baglam_en_fazla_sozcuk"], st.contextMaxWords.toLong())
        assertEquals(a["adres_sola_en_fazla"], st.addressMaxLeftWords.toLong())
        assertEquals(a["eposta_sola_en_fazla"], st.emailMaxLeftWords.toLong())
        assertEquals(a["sozluk_onek_en_kisa"], st.dictionaryPrefixMinLength.toLong())
        assertEquals(a["gizli_rakam_en_kisa"], st.secretMinDigits.toLong())
    }
}
