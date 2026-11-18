package tr.yasliasistan.core.contacts

import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.masking.ContactName

/** Demo 18.11.2026: softened endings and word order of "fotoğraf gönder" commands (made-up name). */
class DemoPhotoNamesTest {
    private val safak = ContactName("id1", "Şafak Deneme")
    private val other = ContactName("id2", "Ayşe Test")
    private val matcher = ContactMatcher.build(listOf(safak, other))

    @Test
    fun softenedAndReordered() {
        listOf(
            "whatsapptan şafağa fotoğraf gönder",
            "şafağa whatsapptan son fotoğrafı gönder",
            "fotoğraf gönder şafağa",
            "şafak'a resim yolla",
            "Şafak Deneme'ye fotoğrafı gönder",
            "şafak denemeye fotoğraf at",
        ).forEach { spoken ->
            val m = matcher.match(spoken)
            assertTrue("'$spoken' -> $m", m is ContactMatch.One && m.contact == safak)
        }
    }
}
