package tr.yasliasistan.core.masking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.log.SafeLog

class MaskingUnitTest {

    private fun masked(text: String, source: InputSource = InputSource.SPEECH, session: MaskingSession = MaskingSession(MaskCorpus.contactIndex)) =
        (session.maskCommand(text, source) as CommandMaskResult.Masked).text.text

    @Test
    fun checksums() {
        assertTrue(NumberRules.isValidTckn("10000000146"))
        assertFalse(NumberRules.isValidTckn("12345678901"))
        assertTrue(NumberRules.isValidLuhn("4000000000000010"))
        assertFalse(NumberRules.isValidLuhn("4000000000000001"))
        assertTrue(NumberRules.isValidIban("TR200000000000000000000001"))
        assertFalse(NumberRules.isValidIban("TR000000000000000000000001"))
        assertEquals("5990000016", NumberRules.phoneKey("05990000016"))
        assertEquals("5990000024", NumberRules.phoneKey("00905990000024"))
    }

    @Test
    fun spokenNumbers() {
        fun d(vararg v: Long) = SpokenNumbers.toDigits(v.toList())
        assertEquals("05990000018", d(0, 5, 100, 90, 9, 0, 0, 0, 0, 0, 10, 8))
        assertEquals("2112", d(2, 1000, 100, 10, 2))
        assertEquals("1234", d(1, 2, 3, 4))
        assertEquals("350", d(3, 100, 50))
    }

    @Test
    fun turkishSuffixes() {
        assertEquals("'ten", TurkishSuffix.forValue("Osman Test", "'den"))
        assertEquals("'ü", TurkishSuffix.forValue("0599 000 00 14", "'i"))
        assertEquals("'a", TurkishSuffix.forValue("0599 000 00 10", "'e"))
        assertEquals("'nin", TurkishSuffix.forValue("Ali", "'in"))
        assertEquals("'ya", TurkishSuffix.forValue("Kadıköy Sokağı No 6", "'e"))
    }

    @Test
    fun samePersonGetsSamePlaceholderAcrossCommands() {
        val session = MaskingSession(MaskCorpus.contactIndex)
        assertEquals("[KISI_1]'i ara", masked("ayşeyi ara", session = session))
        assertEquals("[KISI_1]'e [METIN_1] diye mesaj at", masked("Ayşe'ye geldim diye mesaj at", InputSource.TYPED, session))
        assertEquals("[KISI_2]'i ara", masked("mehmedi ara", session = session))
    }

    @Test
    fun postalCodeIsNotAVerificationCode() {
        assertEquals("posta kodu [SAYI_1] olarak yaz", masked("posta kodu 34000 olarak yaz"))
        assertEquals(
            CommandMaskResult.Blocked(BlockReason.DOGRULAMA_KODU),
            MaskingSession(MaskCorpus.contactIndex).maskCommand("otp 552 gir", InputSource.SPEECH),
        )
    }

    @Test
    fun answerWithUnsentPlaceholderIsRejected() {
        val session = MaskingSession(MaskCorpus.contactIndex)
        masked("ayşeyi ara", session = session)
        assertEquals(UnmaskResult.Rejected(BlockReason.BILINMEYEN_YER_TUTUCU), session.unmask("[TEL_1]'i ara"))
        assertEquals(UnmaskResult.Restored("Ayşe Test'i arıyorum"), session.unmask("[KISI_1]'i arıyorum"))
    }

    @Test
    fun bracketsInInputCannotFakeAPlaceholder() {
        val out = masked("[KISI_1]'e para gönder")
        assertFalse(out.contains("[KISI_1]"))
    }

    @Test
    fun withoutContactPermissionTheSlotRuleStillMasksNames() {
        val session = MaskingSession(ContactIndex.EMPTY)
        assertEquals("[KISI_1]'i ara", masked("ayşeyi ara", session = session))
        assertEquals(mapOf("[KISI_1]" to "Ayşeyi"), session.table.snapshot())
        assertEquals("[KISI_2]'i ara", masked("Ayşe Test'i ara", session = session))
        assertEquals("Ayşe Test", session.table.snapshot()["[KISI_2]"])
    }

    @Test
    fun logsNeverContainContent() {
        val lines = ArrayList<String>()
        SafeLog.install({ _, _, m -> lines += m }, allowContent = false)
        try {
            masked("ayşeye yarın geliyorum diye mesaj at")
            MaskingSession(MaskCorpus.contactIndex).maskCommand("Şifrem 1234, gir", InputSource.TYPED)
        } finally {
            SafeLog.install(null, allowContent = false)
        }
        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            assertFalse(line, TurkishFold.fold(line).contains("ayse"))
            assertFalse(line, line.contains("geliyorum") || line.contains("1234"))
        }
    }

    @Test
    fun tableToStringHidesValues() {
        val session = MaskingSession(MaskCorpus.contactIndex)
        masked("ayşeyi ara", session = session)
        assertEquals("PlaceholderTable(size=1)", session.table.toString())
    }

    /** Rough JVM timing; the device budget (<= 5 ms per command on Galaxy A15) is measured in G-54. */
    @Test
    fun maskingIsFast() {
        val session = MaskingSession(MaskCorpus.contactIndex)
        val inputs = MaskCorpus.commandCases().map { it["girdi"] as String to MaskCorpus.source(it) }
        repeat(20) { inputs.forEach { (t, s) -> session.maskCommand(t, s); session.clear() } } // warm-up
        val start = System.nanoTime()
        val rounds = 20
        repeat(rounds) { inputs.forEach { (t, s) -> session.maskCommand(t, s); session.clear() } }
        val perCommandMs = (System.nanoTime() - start) / 1e6 / (rounds * inputs.size)
        println("masking: %.3f ms per command (JVM)".format(perCommandMs))
        assertTrue("too slow: $perCommandMs ms", perCommandMs < 5.0)
    }
}
