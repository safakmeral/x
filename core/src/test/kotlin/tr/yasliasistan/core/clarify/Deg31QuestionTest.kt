package tr.yasliasistan.core.clarify

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.yasliasistan.core.dialog.DialogEvent

/**
 * DEG-31 test set (FG-101, FG-102): a command whose person matches one contact gets no question;
 * several matches get the names read out; a truly incomplete command gets exactly one question.
 * The unnecessary-question rate (questions asked where none was needed / commands) must be 0, and no
 * needed question may be skipped.
 */
class Deg31QuestionTest {

    private class Case(val command: String, val questionNeeded: Boolean, val withWhatsApp: Boolean = false)

    private val cases = listOf(
        Case("Mehmet'i ara", false),
        Case("Mehmet Test'i ara", false),
        Case("Zeynep'i arar mısın", false),
        Case("kızımı ara", false),
        Case("Hüseyin'i ara", false),
        Case("Ayşe Örnek'i ara", false),
        Case("Ayşe Deneme'yi hemen ara lütfen", false),
        Case("kızıma mesaj gönder", false),
        Case("Mehmet'e mesaj yaz", false),
        Case("Zeynep'e WhatsApp'tan mesaj at", false, withWhatsApp = true),
        Case("Mehmet'e SMS gönder", false, withWhatsApp = true),
        Case("sesi aç", false),
        Case("sesi kıs", false),
        Case("parlaklığı azalt", false),
        Case("yazıları büyüt", false),
        Case("Ayşe'yi ara", true), // two people: read by name
        Case("Ayşe'ye mesaj gönder", true),
        Case("ara", true), // person missing
        Case("mesaj yaz", true),
        Case("Selim'i ara", true), // unknown name
        Case("kızıma mesaj gönder", true, withWhatsApp = true), // two messaging apps
    )

    @Test
    fun unnecessaryQuestionRateIsZeroAndNoNeededQuestionIsSkipped() {
        var unnecessary = 0
        var skipped = 0
        cases.forEachIndexed { i, case ->
            val apps = if (case.withWhatsApp) listOf(Apps.settings, Apps.whatsapp) else listOf(Apps.settings)
            val traced = Harness(apps = apps).coordinator.decideTraced(i, case.command)
            val asked = (traced.event as? DialogEvent.Replied)?.expectsAnswer == true
            if (asked && !case.questionNeeded) unnecessary++
            if (!asked && case.questionNeeded) skipped++
            assertEquals("flag for ${case.command}", false, traced.unnecessaryQuestion)
        }
        val rate = unnecessary.toDouble() / cases.size
        println("DEG-31: ${cases.size} komut, gereksiz soru oranı = $rate, atlanan gerekli soru = $skipped")
        assertEquals(0.0, rate, 0.0)
        assertEquals(0, skipped)
    }

    @Test
    fun severalMatchesAreReadWithTheirNames() {
        val event = Harness().coordinator.decide(1, "Ayşe'yi ara") as DialogEvent.Replied
        assertEquals("CHOOSE:Ayşe Örnek|Ayşe Deneme", event.text)
    }
}
