package tr.yasliasistan.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.clarify.Apps
import tr.yasliasistan.core.clarify.ClarificationRule
import tr.yasliasistan.core.clarify.Harness
import tr.yasliasistan.core.decision.DecisionCoordinator
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionSource
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.dialog.DialogEvent

/** G-39: references to the previous command, repeat, indirect phrases and layered "neden?". */
class ConversationContextTest {

    private val h = ContextHarness()

    private fun DecisionCoordinator.Traced.text(): String = when (val e = event) {
        is DialogEvent.Understood -> e.readback
        is DialogEvent.Proceeding -> e.announcement
        is DialogEvent.Replied -> e.text
        else -> error("unexpected $e")
    }

    // --- "onu da ara", "tekrar ara": the previous person (FG-08) ---

    @Test
    fun backReferenceUsesThePreviousPersonAcrossSessions() {
        h.sayAndRun("Mehmet Test'i ara")
        val traced = h.sayAndRun("onu da ara")
        assertTrue(traced.event is DialogEvent.Understood) // still Medium risk: readback + yes
        assertEquals("CALL?Mehmet Test", traced.text())
        assertEquals(DecisionSource.RULE, traced.source)
        assertEquals("CALL?Mehmet Test", h.sayAndRun("bir daha ara").text())
        assertEquals("CALL?Mehmet Test", h.sayAndRun("tekrar ara").text())
        assertTrue(h.modelInputs.isEmpty())
    }

    @Test
    fun backReferenceToAMessagedPersonForACall() {
        h.base.apps = listOf(Apps.settings) // only SMS: no app question
        assertEquals("MSG?Ayşe Örnek@Mesajlar", h.sayAndRun("Ayşe Örnek'e mesaj yaz").text())
        assertEquals("CALL?Ayşe Örnek", h.sayAndRun("onu da ara").text())
    }

    @Test
    fun withoutAPreviousPersonTheOneQuestionIsAsked() {
        assertEquals("WHOM_CALL?", h.say("onu ara").second.text())
    }

    @Test
    fun contextIsForgottenAfterTheTimeLimit() {
        h.sayAndRun("Mehmet Test'i ara")
        h.now += Defaults.CONTEXT_TTL_MS + 1
        assertEquals("WHOM_CALL?", h.say("onu da ara").second.text())
    }

    @Test
    fun backReferenceToThePreviousApp() {
        h.sayAndRun("WhatsApp'ı aç")
        val traced = h.sayAndRun("onu tekrar aç")
        assertEquals(DialogEvent.Proceeding(2, "OPENING:WhatsApp"), traced.event)
    }

    // --- "tekrar", "aynısını yap" ---

    @Test
    fun repeatRunsTheLastExecutedActionAgain() {
        assertEquals("VOLUME:true", h.sayAndRun("sesi aç").text())
        val again = h.sayAndRun("aynısını yap")
        assertTrue(again.event is DialogEvent.Proceeding)
        assertEquals("VOLUME:true", again.text())
        assertEquals("VOLUME:true", h.sayAndRun("tekrar").text())
    }

    @Test
    fun repeatWithNothingDoneSaysSo() {
        assertEquals("NOTHING", h.say("tekrar").second.text())
        // An action that was proposed but not run (user said "hayır") is not repeated either.
        h.say("Mehmet Test'i ara")
        assertEquals("NOTHING", h.say("aynısını yap").second.text())
    }

    // --- Indirect phrases (FG-09) ---

    @Test
    fun indirectPhraseActsWithoutAQuestion() {
        val traced = h.say("Sesi duyamıyorum").second
        assertTrue(traced.event is DialogEvent.Proceeding)
        assertEquals("VOLUME:true", traced.text())
        assertFalse(traced.unnecessaryQuestion)
        assertEquals("BRIGHTNESS", h.say("ekran çok karanlık").second.text())
        assertEquals("TEXT_SIZE", h.say("yazılar küçük").second.text())
    }

    @Test
    fun ambiguousComplaintGoesToTheLanguageModelUnchanged() {
        h.say("torunumu göremiyorum")
        assertEquals(listOf("torunumu göremiyorum"), h.modelInputs)
    }

    // --- Layered "neden?" (FG-15, NFG-09) ---

    @Test
    fun whyFirstOneLineThenTheGrounds() {
        h.sayAndRun("Sesi duyamıyorum")
        val first = h.say("neden?").second
        assertEquals(DialogEvent.Replied(2, "INDIRECT:VOLUME_UP => VOLUME_DONE:true", expectsAnswer = false), first.event)
        assertEquals("UNDO_NOTE", h.say("neden?").second.text())
        assertEquals("UNDO_NOTE", h.say("biraz daha anlat").second.text())
    }

    @Test
    fun detailForACallNamesTheContactAndTheConfirmation() {
        h.sayAndRun("Mehmet Test'i ara")
        assertEquals("ASKED => DIALED:Mehmet Test", h.say("niye").second.text())
        assertEquals("CONTACT:Mehmet Test CONFIRM_NOTE", h.say("ayrıntılı anlat").second.text())
    }

    @Test
    fun whyForAnOpenAppNamesTheInstalledApp() {
        h.sayAndRun("WhatsApp'ı aç")
        assertEquals("ASKED => OPENED:WhatsApp", h.say("neden böyle yaptın").second.text())
        assertEquals("INSTALLED:WhatsApp UNDO_NOTE", h.say("neden").second.text())
    }

    @Test
    fun whyDuringTheOneQuestionKeepsTheQuestionOpen() {
        val ask = h.say("Ayşe'yi ara").second
        assertEquals("CHOOSE:Ayşe Örnek|Ayşe Deneme", ask.text())
        val why = h.say("neden soruyorsun").second
        assertEquals(DialogEvent.Replied(2, "ASKED_SEVERAL", expectsAnswer = true), why.event)
        assertEquals("CALL?Ayşe Deneme", h.say("Ayşe Deneme").second.text())
    }

    @Test
    fun whyAfterCannotDoAndWithNothingRecent() {
        assertEquals("NOTHING", h.say("neden").second.text())
        h.say("torunumu göremiyorum")
        assertEquals("CANNOT_DO", h.say("neden").second.text())
        assertEquals("CANNOT_DETAIL", h.say("neden").second.text())
    }

    @Test
    fun whyIsNotPassedToTheLanguageModel() {
        h.say("neden")
        assertTrue(h.modelInputs.isEmpty())
    }

    @Test
    fun explainLastIsTheEntryPointForTheFixedCommand() {
        h.sayAndRun("sesi aç")
        assertEquals("ASKED => VOLUME_DONE:true", h.coordinator.explainLast())
        assertEquals("UNDO_NOTE", h.coordinator.explainLast())
    }

    // --- Wiring ---

    @Test
    fun contextIsOnWhenThePhrasesCanExplain() {
        val base = Harness()
        val coordinator = DecisionCoordinator(DecisionEngine(RuleLayer(listOf(ClarificationRule(base.engine)))), BothPhrases)
        assertEquals(DialogEvent.Replied(1, "NOTHING", expectsAnswer = false), coordinator.decide(1, "neden"))
    }

    @Test
    fun contextIsOffWithPlainPhrases() {
        assertEquals(null, Harness().coordinator.explainLast())
    }
}
