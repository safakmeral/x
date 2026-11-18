package tr.yasliasistan.core.clarify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.MessageChannel
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.Rule
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.decision.SystemScreen
import tr.yasliasistan.core.decision.VolumeChange
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.masking.ContactName

/** G-37: call / message / settings commands decided end to end by rules (made-up names only). */
class ClarificationTest {

    private fun ask(id: Int, text: String) = DialogEvent.Replied(id, text, expectsAnswer = true)
    private fun say(id: Int, text: String) = DialogEvent.Replied(id, text, expectsAnswer = false)
    private fun confirm(id: Int, text: String) = DialogEvent.Understood(id, text, lowRisk = false)

    // --- Call ---

    @Test
    fun singleMatchCallsWithoutAnyQuestion() {
        val c = Harness().coordinator
        assertEquals(confirm(1, "CALL?Mehmet Test"), c.decide(1, "Mehmet'i ara"))
        assertEquals(Action.CallContact(People.mehmetTest), c.takeAction(1))
    }

    @Test
    fun severalMatchesAreReadByNameAndTheAnswerPicks() {
        val c = Harness().coordinator
        assertEquals(ask(1, "CHOOSE:Ayşe Örnek|Ayşe Deneme"), c.decide(1, "Ayşe'yi ara"))
        assertEquals(confirm(2, "CALL?Ayşe Deneme"), c.decide(2, "ikincisi"))
        assertEquals(Action.CallContact(People.ayseDeneme), c.takeAction(2))
    }

    @Test
    fun answerByNameWorksToo() {
        val c = Harness().coordinator
        c.decide(1, "Ayşe'yi arar mısın")
        assertEquals(confirm(2, "CALL?Ayşe Örnek"), c.decide(2, "Örnek olanı"))
    }

    @Test
    fun missingPersonGetsExactlyOneQuestion() {
        val c = Harness().coordinator
        assertEquals(ask(1, "WHOM_CALL?"), c.decide(1, "ara"))
        assertEquals(confirm(2, "CALL?Mehmet Test"), c.decide(2, "Mehmet"))
    }

    @Test
    fun theAnswerNeverLeadsToASecondQuestion() {
        val c = Harness().coordinator
        c.decide(1, "birini ara")
        // Two people are called Ayşe, but the one question is used up.
        assertEquals(say(2, "STILL_NOT_FOUND"), c.decide(2, "Ayşe"))
    }

    @Test
    fun unknownNameAsksOnceThenStops() {
        val c = Harness().coordinator
        assertEquals(ask(1, "NOT_FOUND"), c.decide(1, "Selim'i ara"))
        assertEquals(say(2, "STILL_NOT_FOUND"), c.decide(2, "Kemal"))
        assertEquals(ask(3, "NOT_FOUND"), c.decide(3, "Selim'i ara"))
        assertEquals(confirm(4, "CALL?Zeynep Örnek"), c.decide(4, "Zeynep"))
    }

    @Test
    fun inexactSingleMatchIsConfirmedExplicitly() {
        val c = Harness().coordinator
        assertEquals(confirm(1, "CALL_NEAREST?Hüseyin Deneme"), c.decide(1, "Hüsein'i ara"))
    }

    @Test
    fun noPermissionOpensThePermissionScreenWithoutReadingTheBook() {
        val h = Harness(granted = false)
        assertEquals(DialogEvent.Proceeding(1, "OPENING_PERMISSION"), h.coordinator.decide(1, "Ayşe'yi ara"))
        assertEquals(Action.RequestContactsPermission, h.coordinator.takeAction(1))
    }

    @Test
    fun tooManyAsksForMoreOfTheName() {
        val alis = listOf("Deneme", "Örnek", "Test", "Sınama").mapIndexed { i, s -> ContactName("a$i", "Ali $s") }
        val c = Harness(contacts = alis).coordinator
        assertEquals(ask(1, "TOO_MANY:4"), c.decide(1, "Ali'yi ara"))
        assertEquals(confirm(2, "CALL?Ali Sınama"), c.decide(2, "Sınama"))
    }

    @Test
    fun noAndNewCommandEndTheQuestion() {
        val c = Harness().coordinator
        c.decide(1, "Ayşe'yi ara")
        assertEquals(say(2, "CANCELLED"), c.decide(2, "hayır"))
        c.decide(3, "Ayşe'yi ara")
        assertEquals(DialogEvent.Proceeding(4, "OPENING:Ayarlar"), c.decide(4, "ayarları aç"))
    }

    @Test
    fun videoCallAndExtraWordsAreLeftToTheLanguageModel() {
        val h = Harness()
        assertEquals(ClarificationEngine.Result.Unresolved, h.engine.clarify(Command("Mehmet'i görüntülü ara")))
        assertEquals(ClarificationEngine.Result.Unresolved, h.engine.clarify(Command("Mehmet'i WhatsApp'tan ara")))
        assertEquals(ClarificationEngine.Result.Unresolved, h.engine.clarify(Command("Mehmet'e mesaj yaz yarın geliyorum")))
        assertEquals(say(1, "CANNOT"), h.coordinator.decide(1, "Mehmet'e mesaj yaz yarın geliyorum"))
    }

    // --- Message ---

    @Test
    fun messageWithOnlySmsAsksNothing() {
        val c = Harness().coordinator
        assertEquals(confirm(1, "MSG?Kızım@Mesajlar"), c.decide(1, "kızıma mesaj gönder"))
        assertEquals(Action.MessageContact(People.kizim, MessageChannel.Sms), c.takeAction(1))
    }

    @Test
    fun messageAppIsAskedWhenReallyAmbiguous() {
        val c = Harness(apps = listOf(Apps.settings, Apps.whatsapp)).coordinator
        assertEquals(ask(1, "WHICH_APP:Mesajlar|WhatsApp"), c.decide(1, "kızıma mesaj gönder"))
        assertEquals(confirm(2, "MSG?Kızım@WhatsApp"), c.decide(2, "WhatsApp'tan"))
        assertEquals(Action.MessageContact(People.kizim, MessageChannel.WhatsApp(Apps.whatsapp)), c.takeAction(2))
    }

    @Test
    fun namedAppIsNotAsked() {
        val c = Harness(apps = listOf(Apps.whatsapp, Apps.business)).coordinator
        assertEquals(confirm(1, "MSG?Kızım@WhatsApp"), c.decide(1, "kızıma WhatsApp'tan mesaj at"))
        assertEquals(confirm(2, "MSG?Kızım@WhatsApp Business"), c.decide(2, "kızıma WhatsApp Business'tan mesaj yaz"))
        assertEquals(confirm(3, "MSG?Kızım@Mesajlar"), c.decide(3, "kızıma SMS gönder"))
    }

    @Test
    fun whatsAppMissingIsSaidPlainly() {
        val c = Harness().coordinator
        assertEquals(say(1, "NOT_INSTALLED:WhatsApp"), c.decide(1, "kızıma WhatsApp'tan mesaj yaz"))
    }

    @Test
    fun personAndAppBothOpenStillMeansOneQuestion() {
        val c = Harness(apps = listOf(Apps.whatsapp)).coordinator
        assertEquals(ask(1, "CHOOSE:Ayşe Örnek|Ayşe Deneme"), c.decide(1, "Ayşe'ye mesaj gönder"))
        // The question is used: the phone's messaging app is taken and named in the readback.
        assertEquals(confirm(2, "MSG?Ayşe Örnek@Mesajlar"), c.decide(2, "birincisi"))
    }

    @Test
    fun missingRecipientIsAskedOnce() {
        val c = Harness().coordinator
        assertEquals(ask(1, "WHOM_MSG?"), c.decide(1, "mesaj yaz"))
        assertEquals(confirm(2, "MSG?Mehmet Test@Mesajlar"), c.decide(2, "Mehmet'e"))
    }

    // --- Settings and weather: never a question ---

    @Test
    fun volumeBrightnessTextSizeNeedNoQuestion() {
        val h = Harness()
        val c = h.coordinator
        assertEquals(DialogEvent.Proceeding(1, "VOLUME:true"), c.decide(1, "sesi aç"))
        assertEquals(Action.AdjustVolume(VolumeChange.UP), c.takeAction(1))
        assertEquals(DialogEvent.Proceeding(2, "VOLUME:false"), c.decide(2, "telefonun sesini biraz kısar mısın"))
        assertEquals(DialogEvent.Proceeding(3, "VOLUME:null"), c.decide(3, "ses ayarı"))
        assertEquals(DialogEvent.Proceeding(4, "BRIGHTNESS"), c.decide(4, "parlaklığı artır"))
        assertEquals(Action.OpenSystemScreen(SystemScreen.BRIGHTNESS, true), c.takeAction(4))
        assertEquals(DialogEvent.Proceeding(5, "TEXT_SIZE"), c.decide(5, "yazıları büyüt"))
        assertEquals(Action.OpenSystemScreen(SystemScreen.TEXT_SIZE, true), c.takeAction(5))
        assertEquals(ClarificationEngine.Result.Unresolved, h.engine.clarify(Command("sesi kapat")))
    }

    @Test
    fun partialTranscriptIsReadBackEvenForLowRisk() {
        val c = Harness().coordinator
        assertEquals(DialogEvent.Understood(1, "VOLUME?true", lowRisk = true), c.decide(1, "sesi aç", partialOnly = true))
    }

    @Test
    fun weatherOpensTheWeatherAppOrIsLeftToLaterLayers() {
        val h = Harness(apps = listOf(Apps.settings, Apps.weather))
        assertEquals(DialogEvent.Proceeding(1, "OPENING:Hava Durumu"), h.coordinator.decide(1, "hava durumu nasıl"))
        h.apps = listOf(Apps.settings)
        assertEquals(ClarificationEngine.Result.Unresolved, h.engine.clarify(Command("hava durumu nasıl")))
    }

    @Test
    fun openAppCommandsStillReachTheOpenAppRule() {
        val c = Harness(apps = listOf(Apps.settings, Apps.whatsapp)).coordinator
        assertEquals(DialogEvent.Proceeding(1, "OPENING:WhatsApp"), c.decide(1, "WhatsApp'ı aç"))
    }

    @Test
    fun unnecessaryQuestionIsFlaggedForMetrics() {
        val single = Decision.Ask("Hangisi: Mehmet Test?", { null }, candidates = 1)
        val c = DecisionCoordinator(DecisionEngine(RuleLayer(listOf(Rule { single }))), TestDecisionPhrases)
        assertTrue(c.decideTraced(1, "x").unnecessaryQuestion)
        assertFalse(Harness().coordinator.decideTraced(1, "Ayşe'yi ara").unnecessaryQuestion)
    }
}
