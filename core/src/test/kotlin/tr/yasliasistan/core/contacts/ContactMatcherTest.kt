package tr.yasliasistan.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.masking.ContactName

/** G-36 / FG-101: one match -> never a question; several -> names; all names below are made up. */
class ContactMatcherTest {

    private var nextId = 0
    private fun c(name: String) = ContactName("id${nextId++}", name)

    private val ayseTest = c("Ayşe Test")
    private val mehmetAmca = c("Mehmet Amca")
    private val kizim = c("Kızım")
    private val oglumEmre = c("Oğlum Emre")
    private val zeynepOrnek = c("Zeynep Örnek")
    private val husnuDeneme = c("Hüseyin Deneme")
    private val fatmaTeyze = c("Fatma Teyze")
    private val ahmetAcar = c("Ahmet Acar")
    private val ahmetKaya = c("Ahmet Kaya")
    private val araGuler = c("Ara Güler")
    private val annem = c("Annem")
    private val esim = c("Eşim")
    private val esinSahte = c("Esin Sahte")
    private val burakUydurma = c("Burak Uydurma")

    private val oneOfEach = ContactMatcher.build(
        listOf(
            ayseTest, mehmetAmca, kizim, oglumEmre, zeynepOrnek, husnuDeneme, fatmaTeyze,
            ahmetAcar, ahmetKaya, araGuler, annem, esim, esinSahte, burakUydurma,
        ),
    )

    private fun assertOne(spoken: String, expected: ContactName, matcher: ContactMatcher = oneOfEach) {
        val m = matcher.match(spoken)
        assertTrue("'$spoken' -> $m", m is ContactMatch.One && m.contact == expected)
    }

    @Test
    fun singleMatchNeverAsks() {
        val cases = listOf(
            "Ayşe'yi ara" to ayseTest,
            "Ayşe’ye mesaj yaz" to ayseTest,
            "ayşeye mesaj at" to ayseTest,
            "AYŞE'Yİ ARA" to ayseTest,
            "ayseyi ara" to ayseTest,
            "ayse test i ara" to ayseTest,
            "Ayşe teyzeyi ara" to ayseTest,
            "Mehmet amcama mesaj yaz" to mehmetAmca,
            "mehmet amcayı ara" to mehmetAmca,
            "amcamı ara" to mehmetAmca,
            "kızıma mesaj yaz" to kizim,
            "kizimi ara" to kizim,
            "oğlumu ara" to oglumEmre,
            "ogluma yaz" to oglumEmre,
            "Emre'yi ara" to oglumEmre,
            "Zeynebi ara" to zeynepOrnek,
            "zeynep örneğe yaz" to zeynepOrnek,
            "Burağa mesaj at" to burakUydurma,
            "teyzemi ara" to fatmaTeyze,
            "annemi ara" to annem,
            "anneme mesaj gönder" to annem,
            "eşimi ara" to esim,
            "Esin'i ara" to esinSahte,
            "Ahmet Acar'ı ara" to ahmetAcar,
            "ahmet kayayı ara" to ahmetKaya,
            "Güler'i ara" to araGuler,
            "lütfen bana Ayşe'yi arar mısın" to ayseTest,
        )
        val failures = cases.filterNot { (spoken, expected) ->
            val m = oneOfEach.match(spoken)
            m is ContactMatch.One && m.contact == expected
        }.map { it.first }
        // An unnecessary question is an error (student decision): every case must be a single match.
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun commandVerbNeverSelectsAPersonOnItsOwn() {
        // "ara" is a verb here, not the contact "Ara Güler".
        assertOne("Ayşe'yi ara", ayseTest)
        assertEquals(ContactMatch.None, oneOfEach.match("ara"))
        assertEquals(ContactMatch.None, oneOfEach.match("mesaj yaz"))
    }

    @Test
    fun severalMatchesAreOfferedByName() {
        val m = oneOfEach.match("Ahmet'i ara")
        assertEquals(ContactMatch.Several(listOf(ahmetAcar, ahmetKaya)), m)
    }

    @Test
    fun fullNameResolvesWhatFirstNameCannot() {
        val a = c("Ali Örnek")
        val b = c("Ali Deneme")
        val matcher = ContactMatcher.build(listOf(a, b))
        assertTrue(matcher.match("Ali'yi ara") is ContactMatch.Several)
        assertOne("Ali Deneme'yi ara", b, matcher)
        assertOne("ali denemeye mesaj yaz", b, matcher)
    }

    @Test
    fun kinshipWordPicksTheRightNamesake() {
        val a = c("Ayşe Örnek")
        val b = c("Ayşe Teyze")
        val matcher = ContactMatcher.build(listOf(a, b, fatmaTeyze))
        assertOne("Ayşe teyzeme mesaj yaz", b, matcher)
        assertTrue(matcher.match("Ayşe'yi ara") is ContactMatch.Several)
    }

    @Test
    fun firstNameBeatsSurnameOrTitleOnlyMatch() {
        val a = c("Ayşe Örnek")
        val matcher = ContactMatcher.build(listOf(a, fatmaTeyze))
        assertOne("Ayşe teyzeyi ara", a, matcher)
    }

    @Test
    fun atMostThreeOptionsAreRead() {
        val alis = (1..5).map { c("Ali Soyad$it") }
        val three = ContactMatcher.build(alis.take(3)).match("Ali'yi ara")
        assertEquals(ContactMatch.Several(alis.take(3)), three)
        assertEquals(ContactMatch.TooMany(5), ContactMatcher.build(alis).match("Ali'yi ara"))
    }

    @Test
    fun duplicateEntriesOfOneNameAreOnePerson() {
        val a1 = c("Ayşe Örnek")
        val a2 = c("ayse ornek")
        val m = ContactMatcher.build(listOf(a1, a2)).match("Ayşe'yi ara")
        assertTrue(m is ContactMatch.One && m.contact == a1)
    }

    @Test
    fun noMatchAndEmptyBook() {
        assertEquals(ContactMatch.None, oneOfEach.match("Gülsüm'ü ara"))
        assertEquals(ContactMatch.None, ContactMatcher.EMPTY.match("Ayşe'yi ara"))
        assertEquals(ContactMatch.None, oneOfEach.match(""))
    }

    @Test
    fun speechSlipOnLongNameIsForgivenButMarkedInexact() {
        val m = oneOfEach.match("hüsein'i ara")
        assertTrue("$m", m is ContactMatch.One && m.contact == husnuDeneme && !m.exact)
        // Short names are not guessed.
        assertEquals(ContactMatch.None, oneOfEach.match("Ayşa'yı ara"))
    }

    @Test
    fun unmatchedSurnameMakesTheMatchInexact() {
        val exact = oneOfEach.match("Ayşe Test'i ara") as ContactMatch.One
        assertTrue(exact.exact)
        val other = oneOfEach.match("Ayşe Kaya'yı ara")
        // Kaya belongs to someone else; Ayşe (first name) wins, but it must be read back as unsure.
        assertTrue("$other", other is ContactMatch.One && other.contact == ayseTest && !other.exact)
    }

    @Test
    fun resultsDoNotPrintNames() {
        val text = oneOfEach.match("Ahmet'i ara").toString() + oneOfEach.match("Ayşe'yi ara").toString()
        assertFalse(text.contains("Ahmet") || text.contains("Ayşe") || text.contains("Acar"))
    }

    @Test
    fun withinOneEdit() {
        assertTrue(ContactMatcher.withinOneEdit("husein", "huseyin"))
        assertTrue(ContactMatcher.withinOneEdit("mehmat", "mehmet"))
        assertFalse(ContactMatcher.withinOneEdit("mehmet", "ahmet"))
    }
}
