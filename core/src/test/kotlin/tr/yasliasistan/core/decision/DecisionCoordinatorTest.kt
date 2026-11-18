package tr.yasliasistan.core.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.core.decision.rules.OpenAppRule
import tr.yasliasistan.core.dialog.DialogEvent
import tr.yasliasistan.core.risk.Risk
import tr.yasliasistan.core.risk.RiskAssessor

/** Test wording; the app supplies the real Turkish sentences from strings.xml. */
private object TestPhrases : DecisionPhrases {
    override fun openingApp(label: String) = "$label uygulamasını açıyorum."
    override fun confirmOpenApp(label: String) = "$label uygulamasını açayım mı?"
    override fun chooseApp(labels: List<String>) = "Hangisi? " + labels.joinToString(", ")
    override fun tooManyMatches() = "Birden çok uygulama var. Adını tam söyler misiniz?"
    override fun cannotDoYet() = "Bunu henüz yapamıyorum."
    override fun cancelled() = "Tamam, yapmıyorum."
}

class DecisionCoordinatorTest {

    private val settings = app("Ayarlar", "com.android.settings")
    private val whatsappBusiness = app("WhatsApp Business", "com.whatsapp.w4b")
    private val whatsappWeb = app("WhatsApp Web", "web.app")
    private val maps = app("Google Haritalar", "com.google.android.apps.maps")
    private var apps = listOf(settings, whatsappBusiness, whatsappWeb, maps)

    private fun coordinator(risk: RiskAssessor = DefaultRiskAssessor) =
        DecisionCoordinator(DecisionEngine(RuleLayer(listOf(OpenAppRule { apps }))), TestPhrases, risk)

    @Test
    fun singleExactMatchActsWithoutAnyQuestion() {
        val c = coordinator()
        assertEquals(DialogEvent.Proceeding(7, "Ayarlar uygulamasını açıyorum."), c.decide(7, "ayarları aç"))
        assertEquals(Action.OpenApp(settings), c.takeAction(7))
        assertNull("an action is handed out once", c.takeAction(7))
    }

    @Test
    fun uncertainMatchAsksForConfirmation() {
        val c = coordinator()
        assertEquals(
            DialogEvent.Understood(3, "Google Haritalar uygulamasını açayım mı?", lowRisk = true),
            c.decide(3, "haritaları aç"),
        )
        assertEquals(Action.OpenApp(maps), c.takeAction(3))
    }

    /** UD-1 Ö-1, NFG-18: a partial-only transcript never acts without reading back what was heard. */
    @Test
    fun partialOnlyTranscriptAlwaysAsks() {
        val c = coordinator()
        assertEquals(
            DialogEvent.Understood(4, "Ayarlar uygulamasını açayım mı?", lowRisk = true),
            c.decide(4, "ayarları aç", partialOnly = true),
        )
        assertEquals(Action.OpenApp(settings), c.takeAction(4))
    }

    @Test
    fun partialOnlyChoiceAnswerAlsoAsks() {
        val c = coordinator()
        c.decide(1, "whatsapp aç")
        assertEquals(
            DialogEvent.Understood(2, "WhatsApp Web uygulamasını açayım mı?", lowRisk = true),
            c.decide(2, "web olanı", partialOnly = true),
        )
    }

    @Test
    fun higherRiskAlwaysAsksForConfirmation() {
        val c = coordinator { Risk.MEDIUM }
        assertEquals(DialogEvent.Understood(1, "Ayarlar uygulamasını açayım mı?", lowRisk = false), c.decide(1, "ayarları aç"))
    }

    @Test
    fun noMatchSaysItCannotDoItYet() {
        val c = coordinator()
        assertEquals(DialogEvent.Replied(2, "Bunu henüz yapamıyorum.", expectsAnswer = false), c.decide(2, "tiktok aç"))
        assertNull(c.takeAction(2))
    }

    @Test
    fun severalMatchesAreOfferedByNameAndAnswerByNameIsResolved() {
        val c = coordinator()
        assertEquals(
            DialogEvent.Replied(1, "Hangisi? WhatsApp Business, WhatsApp Web", expectsAnswer = true),
            c.decide(1, "whatsapp aç"),
        )
        // The user chose explicitly, so the low-risk action runs without another question.
        assertEquals(DialogEvent.Proceeding(2, "WhatsApp Web uygulamasını açıyorum."), c.decide(2, "web olanı"))
        assertEquals(Action.OpenApp(whatsappWeb), c.takeAction(2))
    }

    @Test
    fun choiceCanBeAnsweredWithOrdinal() {
        val c = coordinator()
        c.decide(1, "whatsapp aç")
        assertEquals(DialogEvent.Proceeding(2, "WhatsApp Business uygulamasını açıyorum."), c.decide(2, "birincisi"))
    }

    @Test
    fun choiceCanBeRejected() {
        val c = coordinator()
        c.decide(1, "whatsapp aç")
        assertEquals(DialogEvent.Replied(2, "Tamam, yapmıyorum.", expectsAnswer = false), c.decide(2, "vazgeç"))
        assertNull(c.takeAction(2))
    }

    @Test
    fun unrelatedAnswerIsTreatedAsNewCommand() {
        val c = coordinator()
        c.decide(1, "whatsapp aç")
        assertEquals(DialogEvent.Proceeding(2, "Ayarlar uygulamasını açıyorum."), c.decide(2, "ayarları aç"))
    }

    @Test
    fun resetForgetsPendingChoice() {
        val c = coordinator()
        c.decide(1, "whatsapp aç")
        c.reset()
        assertEquals(DialogEvent.Replied(2, "Bunu henüz yapamıyorum.", expectsAnswer = false), c.decide(2, "birincisi"))
    }

    @Test
    fun tooManyMatchesAsksForTheFullName() {
        apps = (1..4).map { app("Not $it", "notes.$it") }
        val c = coordinator()
        assertEquals(
            DialogEvent.Replied(1, "Birden çok uygulama var. Adını tam söyler misiniz?", expectsAnswer = true),
            c.decide(1, "not aç"),
        )
        assertEquals(DialogEvent.Proceeding(2, "Not 3 uygulamasını açıyorum."), c.decide(2, "not 3"))
    }
}
