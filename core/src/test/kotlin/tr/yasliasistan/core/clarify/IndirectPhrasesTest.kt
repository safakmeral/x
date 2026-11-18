package tr.yasliasistan.core.clarify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.core.decision.Action
import tr.yasliasistan.core.decision.Command
import tr.yasliasistan.core.decision.Decision
import tr.yasliasistan.core.decision.SystemScreen
import tr.yasliasistan.core.decision.VolumeChange
import tr.yasliasistan.core.text.TurkishText

/** G-39 / FG-09: indirect phrase table. */
class IndirectPhrasesTest {

    private fun kind(text: String) = IndirectPhrases.match(TurkishText.tokens(text))

    @Test
    fun table() {
        val table = mapOf(
            "Sesi duyamıyorum" to IndirectKind.VOLUME_UP,
            "ses çok az" to IndirectKind.VOLUME_UP,
            "Sesi çok az" to IndirectKind.VOLUME_UP,
            "ses gelmiyor" to IndirectKind.VOLUME_UP,
            "sizi hiç duyamıyorum" to IndirectKind.VOLUME_UP,
            "telefonu duymuyorum" to IndirectKind.VOLUME_UP,
            "ses çok yüksek" to IndirectKind.VOLUME_DOWN,
            "sesi çok fazla" to IndirectKind.VOLUME_DOWN,
            "Ekran çok karanlık" to IndirectKind.BRIGHTNESS_UP,
            "ekran kararmış" to IndirectKind.BRIGHTNESS_UP,
            "ekranı göremiyorum" to IndirectKind.BRIGHTNESS_UP,
            "ekran çok parlak" to IndirectKind.BRIGHTNESS_DOWN,
            "gözlerim kamaşıyor" to IndirectKind.BRIGHTNESS_DOWN,
            "Yazılar küçük" to IndirectKind.TEXT_UP,
            "yazıları okuyamıyorum" to IndirectKind.TEXT_UP,
            "ekrandaki yazıları göremiyorum" to IndirectKind.TEXT_UP,
            "harfler çok küçük" to IndirectKind.TEXT_UP,
            "yazılar çok büyük" to IndirectKind.TEXT_DOWN,
        )
        table.forEach { (text, expected) -> assertEquals(text, expected, kind(text)) }
    }

    @Test
    fun unrelatedOrAmbiguousPhrasesDoNotMatch() {
        listOf(
            "torunumu göremiyorum", // a person, not the screen: next layer decides
            "torunumu duyamıyorum",
            "okuyamıyorum",
            "sesi aç", // explicit direction: the direct rule decides
            "ekran parlak değil", // negation: no guess
            "sesi kapat",
            "dışarısı çok gürültülü",
            "Ayşe'yi ara",
            "",
        ).forEach { assertNull(it, kind(it)) }
    }

    @Test
    fun indirectPhraseBecomesTheSettingsActionWithoutAQuestion() {
        val h = Harness()
        fun act(text: String) = (h.engine.clarify(Command(text)) as ClarificationEngine.Result.Resolved).decision as Decision.Act
        assertEquals(Action.AdjustVolume(VolumeChange.UP), act("Sesi duyamıyorum").action)
        assertEquals(Action.AdjustVolume(VolumeChange.DOWN), act("ses çok yüksek").action)
        assertEquals(Action.OpenSystemScreen(SystemScreen.BRIGHTNESS, true), act("ekran çok karanlık").action)
        assertEquals(Action.OpenSystemScreen(SystemScreen.BRIGHTNESS, false), act("ekran çok parlak").action)
        assertEquals(Action.OpenSystemScreen(SystemScreen.TEXT_SIZE, true), act("yazılar küçük").action)
    }

    @Test
    fun ambiguousComplaintIsLeftToTheNextLayer() {
        assertEquals(ClarificationEngine.Result.Unresolved, Harness().engine.clarify(Command("torunumu göremiyorum")))
    }
}
