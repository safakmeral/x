package tr.yasliasistan.core.llm

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.action.ScreenAction
import tr.yasliasistan.core.cloud.CloudClient
import tr.yasliasistan.core.cloud.CloudFixtures
import tr.yasliasistan.core.cloud.FakeTransport
import tr.yasliasistan.core.cloud.SwitchGate
import tr.yasliasistan.core.cloud.TransportResult
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.DecisionEngine
import tr.yasliasistan.core.decision.DecisionSource
import tr.yasliasistan.core.decision.Rule
import tr.yasliasistan.core.decision.RuleLayer
import tr.yasliasistan.core.masking.MaskingSession
import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.TextKind

/** G-34 + G-35 end to end on the device side: mask → client (fake HTTP) → parse → unmask → decision. */
class LanguageModelLayerTest {

    private val gate = SwitchGate()
    private val plans = mutableListOf<PlannedTask>()
    private var snapshot: ScreenSnapshot? = CloudFixtures.settingsHome

    private object Phrases : LlmPhrases {
        override fun noConsent() = "IZIN_YOK"
        override fun notConfigured() = "KURULU_DEGIL"
        override fun quotaExhausted() = "DOLU"
        override fun unavailable() = "ULASAMADIM"
        override fun unauthorized() = "KOD"
        override fun privateData() = "GIZLI"
        override fun notUnderstood() = "ANLAMADIM"
        override fun planNotRunnable() = "PLAN_YOK"
    }

    private fun layer(transport: FakeTransport) = LanguageModelLayer(
        client = CloudClient(gate, { CloudFixtures.config }, transport),
        phrases = Phrases,
        newMasking = { MaskingSession(CloudFixtures.contacts) },
        screen = { snapshot },
        scope = { CloudFixtures.scope },
        installedApps = { CloudFixtures.apps },
        onPlan = { plans += it; Decision.Say("PLAN_ALINDI") },
    )

    private fun result(json: String) = FakeTransport { FakeTransport.ok(json) }

    private fun userText(t: FakeTransport, i: Int = 0) = (t.requests[i].body["user"] as JsonPrimitive).content

    @Test
    fun planIsValidatedAgainstTheScreenAndHandedOver() {
        val transport = FakeTransport()
        assertEquals(Decision.Say("PLAN_ALINDI"), layer(transport).decide(Command("ekran ayarlarını aç")))
        val step = plans.single().steps.single()
        assertTrue(step.action is ScreenAction.Tap)
        assertEquals(2, (step.action as ScreenAction.Tap).target.id)
        assertEquals("Ekran ayarları açık", step.expected)
    }

    @Test
    fun noConsentSaysSoAndSendsNothing() {
        gate.allowed = false
        val transport = FakeTransport()
        assertEquals(Decision.Say("IZIN_YOK"), layer(transport).decide(Command("Ayşe Test'e merhaba yaz")))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun engineUsesTheLayerOnlyWhenRulesCannotAnswer() {
        val transport = FakeTransport()
        val ruleAnswer = Decision.Say("KURAL")
        val engine = DecisionEngine(rules = RuleLayer(listOf(Rule { c -> if ("kural" in c.raw) ruleAnswer else null })), languageModel = layer(transport))
        assertEquals(DecisionSource.RULE, engine.resolve(Command("kural ile çöz")).source)
        assertEquals(0, transport.requests.size)
        assertEquals(DecisionSource.LLM, engine.resolve(Command("ekranı aç")).source)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun placeholdersAreRestoredOnTheDeviceForTypedText() {
        snapshot = CloudFixtures.screen(
            CloudFixtures.el(1, "Mesaj yazın", pkg = CloudFixtures.WHATSAPP, role = ElementRole.TEXT_FIELD, editable = true),
            CloudFixtures.el(2, "Gönder", pkg = CloudFixtures.WHATSAPP, role = ElementRole.ICON_BUTTON),
            pkg = CloudFixtures.WHATSAPP,
        )
        val plan = """{"geri_okuma":"[KISI_1]'e merhaba yazayım mı?","netlestirme_sorusu":null,"yapamam":null,"guven":"yuksek",""" +
            """"adimlar":[{"eylem":"type","oge":1,"yon":null,"uzunluk":null,"metin":"merhaba","uygulama":null,""" +
            """"bekleme_ms":null,"neden":null,"beklenen_durum":"Metin yazıldı"}]}"""
        val transport = result(plan)
        layer(transport).decide(Command("Ayşe Test'e merhaba yaz"))
        val sent = userText(transport)
        assertTrue(sent, "[KISI_1]" in sent)
        assertFalse(sent, "Ayşe" in sent)
        assertEquals("Ayşe Test'e merhaba yazayım mı?", plans.single().readback)
        assertEquals("merhaba", (plans.single().steps.single().action as ScreenAction.TypeText).text)
    }

    @Test
    fun unknownPlaceholderOrElementRejectsTheWholeAnswer() {
        val unknown = """{"geri_okuma":"[KISI_7]'yi arayayım mı?","netlestirme_sorusu":null,"yapamam":null,"guven":"yuksek",""" +
            """"adimlar":[{"eylem":"back","oge":null,"yon":null,"uzunluk":null,"metin":null,"uygulama":null,""" +
            """"bekleme_ms":null,"neden":null,"beklenen_durum":"x"}]}"""
        assertEquals(Decision.Say("ANLAMADIM"), layer(result(unknown)).decide(Command("ekranı aç")))

        // Element 4 belongs to an app outside the allow list: it was never sent, so it cannot be targeted.
        snapshot = CloudFixtures.screen(CloudFixtures.el(1, "Ekran"), CloudFixtures.el(2, "Ses"), CloudFixtures.el(3, "Ara"),
            CloudFixtures.el(4, "Not", pkg = CloudFixtures.OTHER))
        val hidden = FakeTransport.PLAN_RESULT.replace("\"oge\":2", "\"oge\":4")
        assertEquals(Decision.Say("ANLAMADIM"), layer(result(hidden)).decide(Command("notu aç")))

        val missing = FakeTransport.PLAN_RESULT.replace("\"oge\":2", "\"oge\":99")
        assertEquals(Decision.Say("ANLAMADIM"), layer(result(missing)).decide(Command("ekranı aç")))

        val otherApp = FakeTransport.PLAN_RESULT.replace("\"eylem\":\"tap\",\"oge\":2", "\"eylem\":\"open_app\",\"oge\":null")
            .replace("\"uygulama\":null", "\"uygulama\":\"${CloudFixtures.OTHER}\"")
        assertEquals(Decision.Say("ANLAMADIM"), layer(result(otherApp)).decide(Command("notlarımı aç")))
        assertTrue(plans.isEmpty())
    }

    @Test
    fun onlyAllowListedAppsAreOffered() {
        val transport = FakeTransport()
        layer(transport).decide(Command("ekranı aç"))
        val sent = userText(transport)
        assertTrue(CloudFixtures.WHATSAPP in sent)
        assertFalse(CloudFixtures.OTHER in sent)
        assertFalse("Notlarım" in sent)
    }

    @Test
    fun oneQuestionThenTheAnswerGoesBackWithoutAnotherQuestion() {
        val question = """{"geri_okuma":"","netlestirme_sorusu":"Kime yazayım?","yapamam":null,"guven":"dusuk","adimlar":[]}"""
        val transport = FakeTransport { if (it == 1) FakeTransport.ok(question) else FakeTransport.ok(FakeTransport.PLAN_RESULT) }
        val ask = layer(transport).decide(Command("mesaj yaz")) as Decision.Ask
        assertEquals("Kime yazayım?", ask.text)
        assertEquals(1, transport.requests.size)

        assertEquals(Decision.Say("PLAN_ALINDI"), ask.pending.answer(Command("Ayşe Test'e")))
        assertEquals(2, transport.requests.size)
        val second = userText(transport, 1)
        assertTrue(second, "Yeni soru sorma" in second)
        assertTrue(second, "Kime yazayım?" in second && "[KISI_1]" in second)
        assertFalse(second, "Ayşe" in second)
    }

    @Test
    fun aSecondQuestionIsNeverAsked() {
        val question = """{"geri_okuma":"","netlestirme_sorusu":"Kime?","yapamam":null,"guven":"dusuk","adimlar":[]}"""
        val transport = result(question)
        val ask = layer(transport).decide(Command("mesaj yaz")) as Decision.Ask
        assertEquals(Decision.Say("ANLAMADIM"), ask.pending.answer(Command("bilmiyorum")))
    }

    @Test
    fun cannotDoReasonIsSpoken() {
        val health = """{"geri_okuma":"","netlestirme_sorusu":null,"yapamam":"Sağlık konusunda tavsiye veremem, lütfen doktorunuza danışın.",""" +
            """"guven":"yuksek","adimlar":[]}"""
        assertEquals(
            Decision.Say("Sağlık konusunda tavsiye veremem, lütfen doktorunuza danışın."),
            layer(result(health)).decide(Command("hangi ilacı içeyim")),
        )
    }

    @Test
    fun quotaAndErrorsEndSafelyWithASentence() {
        val quota = FakeTransport { FakeTransport.error(503, "QUOTA_EXHAUSTED", onDevice = true) }
        val l = layer(quota)
        assertEquals(Decision.Say("DOLU"), l.decide(Command("ekranı aç")))
        assertEquals(Decision.Say("DOLU"), l.decide(Command("sesi aç")))
        assertEquals("no retry, no second call", 1, quota.requests.size)

        assertEquals(Decision.Say("ULASAMADIM"), layer(FakeTransport { TransportResult.Timeout }).decide(Command("ekranı aç")))
        assertEquals(Decision.Say("KOD"), layer(FakeTransport { FakeTransport.error(401, "UNAUTHORIZED") }).decide(Command("ekranı aç")))
        assertEquals(Decision.Say("ANLAMADIM"), layer(result("""{"cevap":"tamam"}""")).decide(Command("ekranı aç")))
        assertTrue(plans.isEmpty())
    }

    @Test
    fun secretsOnScreenOrInCommandBlockTheRequest() {
        snapshot = CloudFixtures.screen(
            CloudFixtures.el(1, "Doğrulama kodunuz: 482915", kind = TextKind.OTHER_TEXT),
        )
        val transport = FakeTransport()
        assertEquals(Decision.Say("GIZLI"), layer(transport).decide(Command("ekranı aç")))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun withoutScreenOnlyTheCommandIsSent() {
        snapshot = null
        val cannot = """{"geri_okuma":"","netlestirme_sorusu":null,"yapamam":"Yapamam.","guven":"yuksek","adimlar":[]}"""
        val transport = result(cannot)
        assertEquals(Decision.Say("Yapamam."), layer(transport).decide(Command("bana fıkra anlat")))
        assertFalse("<ekran" in userText(transport))
    }
}
