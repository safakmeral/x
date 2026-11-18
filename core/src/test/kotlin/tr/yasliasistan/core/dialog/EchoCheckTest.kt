package tr.yasliasistan.core.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.dialog.EchoCheck.Verdict

class EchoCheckTest {

    private val readback = "Google Haritalar uygulamasını açayım mı?"
    private val again = "Evet mi, hayır mı?"

    @Test
    fun ownSentenceHeardBackIsEcho() {
        assertEquals(Verdict.ECHO, EchoCheck.classify("google haritalar uygulamasını", readback))
        assertEquals(Verdict.ECHO, EchoCheck.classify("Haritaları uygulaması açayım", readback)) // recognizer endings differ
    }

    @Test
    fun replyWordNotInTheSentenceIsTheUser() {
        assertEquals(Verdict.USER, EchoCheck.classify("hayır", readback))
        assertEquals(Verdict.USER, EchoCheck.classify("dur", readback))
        assertEquals(Verdict.USER, EchoCheck.classify("açayım mı hayır", readback)) // echo tail + user word
    }

    @Test
    fun echoCanNeverConfirm() {
        assertEquals(Verdict.ECHO, EchoCheck.classify("evet", again))
        assertEquals(Verdict.ECHO, EchoCheck.classify("evet mi hayır mı", again))
        assertNull(EchoCheck.userPart("evet", again))
    }

    @Test
    fun differentWordsAreTheUser() {
        assertEquals(Verdict.USER, EchoCheck.classify("rehberi aç lütfen", readback))
    }

    @Test
    fun singleUnknownWordIsNotEnoughToInterruptButIsKeptAtTheEnd() {
        assertEquals(Verdict.UNSURE, EchoCheck.classify("kamera", readback))
        assertEquals("kamera", EchoCheck.userPart("kamera", readback))
        assertEquals(Verdict.UNSURE, EchoCheck.classify("", readback))
    }

    @Test
    fun leadingEchoIsRemovedFromTheFinalText() {
        assertEquals("evet", EchoCheck.userPart("Google Haritalar uygulamasını evet", readback))
        assertEquals("evet açabilirsin", EchoCheck.stripLeadingEcho("açayım mı evet açabilirsin", readback))
    }

    @Test
    fun oneSharedLeadingWordIsKept() {
        val choice = "Hangisini açayım: WhatsApp, Mesajlar?"
        assertEquals("WhatsApp'ı aç", EchoCheck.stripLeadingEcho("WhatsApp'ı aç", choice))
        assertEquals("WhatsApp'ı aç", EchoCheck.userPart("WhatsApp'ı aç", choice))
    }

    @Test
    fun onlyEchoGivesNothing() {
        assertNull(EchoCheck.userPart("Google Haritalar uygulamasını açayım mı", readback))
        assertNull(EchoCheck.userPart("  ", readback))
    }

    @Test
    fun stemMatchingIsStrictForShortWords() {
        assertTrue(EchoCheck.sameWord("haritalar", "haritalari"))
        assertTrue(!EchoCheck.sameWord("acayim", "acalim"))
    }
}
