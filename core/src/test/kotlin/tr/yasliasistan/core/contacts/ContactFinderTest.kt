package tr.yasliasistan.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.masking.ContactName

/** G-36: permission path, the sentences after a lookup, the answer to "which one?", and speed. */
class ContactFinderTest {

    private val ayseOrnek = ContactName("1", "Ayşe Örnek")
    private val ayseDeneme = ContactName("2", "Ayşe Deneme")
    private val mehmetTest = ContactName("3", "Mehmet Test")
    private val book = ContactBook.of(listOf(ayseOrnek, ayseDeneme, mehmetTest))

    private class FakeAccess(var granted: Boolean, val book: ContactBook) : ContactAccess {
        var bookCalls = 0
        override fun isGranted() = granted
        override fun book(): ContactBook = book.also { bookCalls++ }
    }

    private object Phrases : ContactPhrases {
        override fun permissionReason() = "REASON"
        override fun noPermission() = "NO_PERMISSION"
        override fun chooseContact(names: List<String>) = "CHOOSE:" + names.joinToString("|")
        override fun tooManyContacts(count: Int) = "TOO_MANY:$count"
        override fun contactNotFound() = "NOT_FOUND"
    }

    @Test
    fun withoutPermissionTheBookIsNeverRead() {
        val access = FakeAccess(granted = false, book = book)
        val lookup = ContactFinder(access).find("Mehmet'i ara")
        assertEquals(ContactLookup.NoPermission, lookup)
        assertEquals(0, access.bookCalls)
        assertEquals("NO_PERMISSION", ContactPrompts.questionFor(lookup, Phrases))
    }

    @Test
    fun permissionGrantedLaterIsSeenAtOnce() {
        val access = FakeAccess(granted = false, book = book)
        val finder = ContactFinder(access)
        assertEquals(ContactLookup.NoPermission, finder.find("Mehmet'i ara"))
        access.granted = true
        assertEquals(ContactLookup.Matched(ContactMatch.One(mehmetTest, exact = true)), finder.find("Mehmet'i ara"))
    }

    @Test
    fun singleMatchAsksNothing() {
        val lookup = ContactFinder(FakeAccess(true, book)).find("mehmete mesaj yaz")
        assertNull(ContactPrompts.questionFor(lookup, Phrases))
    }

    @Test
    fun severalMatchesReadNames() {
        val lookup = ContactFinder(FakeAccess(true, book)).find("Ayşe'yi ara")
        assertEquals("CHOOSE:Ayşe Örnek|Ayşe Deneme", ContactPrompts.questionFor(lookup, Phrases))
    }

    @Test
    fun noMatchAndTooManyAskOneQuestion() {
        val finder = ContactFinder(FakeAccess(true, book))
        assertEquals("NOT_FOUND", ContactPrompts.questionFor(finder.find("Gülsüm'ü ara"), Phrases))
        val many = ContactBook.of((1..4).map { ContactName("a$it", "Ali Soyad$it") })
        assertEquals("TOO_MANY:4", ContactPrompts.questionFor(ContactFinder(FakeAccess(true, many)).find("Ali"), Phrases))
    }

    @Test
    fun answerToWhichOne() {
        val options = listOf(ayseOrnek, ayseDeneme)
        assertEquals(ContactChoice.Result.Picked(ayseDeneme), ContactChoice.resolve("ikincisi", options))
        assertEquals(ContactChoice.Result.Picked(ayseOrnek), ContactChoice.resolve("ilkini", options))
        assertEquals(ContactChoice.Result.Picked(ayseDeneme), ContactChoice.resolve("sonuncusu", options))
        assertEquals(ContactChoice.Result.Picked(ayseDeneme), ContactChoice.resolve("Deneme olanı", options))
        assertEquals(ContactChoice.Result.Picked(ayseOrnek), ContactChoice.resolve("ayşe örneği ara", options))
        assertEquals(ContactChoice.Result.Rejected, ContactChoice.resolve("hayır vazgeç", options))
        assertEquals(ContactChoice.Result.NotAnAnswer, ContactChoice.resolve("Ayşe", options))
        assertEquals(ContactChoice.Result.NotAnAnswer, ContactChoice.resolve("üçüncü", options))
    }

    @Test
    fun maskingIndexComesFromTheSameSnapshot() {
        assertTrue(!book.maskingIndex.isEmpty)
        assertTrue(ContactBook.EMPTY.maskingIndex.isEmpty)
        assertEquals(3, book.size)
    }

    /** NFG-23 / brief: matching stays in milliseconds even with a 2000-person address book. */
    @Test
    fun largeAddressBookIsFast() {
        val first = listOf("Ayşe", "Fatma", "Emine", "Hatice", "Zeynep", "Elif", "Mehmet", "Mustafa", "Ahmet", "Ali",
            "Hüseyin", "Hasan", "İbrahim", "Murat", "Ömer", "Yusuf", "Selin", "Gülsüm", "Burak", "Emre")
        val last = (1..100).map { "Soyad${'A' + it % 26}${it}x" }
        val contacts = (0 until 2000).map { i -> ContactName("c$i", first[i % first.size] + " " + last[i / first.size]) } +
            ContactName("special", "Kızım")
        val buildStart = System.nanoTime()
        val big = ContactBook.of(contacts)
        val buildMs = (System.nanoTime() - buildStart) / 1_000_000.0
        val queries = listOf("kızıma mesaj yaz", "Ayşe'yi ara", "mehmet soyadb1x'i ara", "hüsein'i ara", "gülsüme yaz", "kimseyi ara")
        repeat(50) { queries.forEach { big.matcher.match(it) } } // warm up the JIT
        val runs = 200
        val start = System.nanoTime()
        repeat(runs) { queries.forEach { big.matcher.match(it) } }
        val perLookupMs = (System.nanoTime() - start) / 1_000_000.0 / (runs * queries.size)
        println("G-36 speed: build=${"%.1f".format(buildMs)} ms, lookup=${"%.3f".format(perLookupMs)} ms (2001 contacts)")
        assertTrue("build took $buildMs ms", buildMs < 1000)
        assertTrue("lookup took $perLookupMs ms", perLookupMs < 5.0)
        assertEquals(ContactMatch.One(contacts.last(), exact = true), big.matcher.match("kızıma mesaj yaz"))
    }
}
