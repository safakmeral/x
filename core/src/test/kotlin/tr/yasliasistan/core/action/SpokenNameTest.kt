package tr.yasliasistan.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.core.action.ActionFixtures.el
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.TextKind

/** G-22 / V-23: which element names a narration may say aloud. */
class SpokenNameTest {

    private fun named(label: String?, kind: TextKind, secret: Boolean = false) =
        el(1, label, Bounds(0, 0, 10, 10), secret = secret).copy(textKind = kind)

    @Test
    fun appLabelsAreSpoken() {
        assertEquals("Ekran", SpokenName.of(named(" Ekran ", TextKind.UI_LABEL)))
    }

    @Test
    fun shortListNameIsSpokenWithoutTheRestOfTheRow() {
        assertEquals("Ayşe Test", SpokenName.of(named("Ayşe Test, Yarın geliyorum", TextKind.LIST_CONTENT)))
    }

    @Test
    fun longListTextLooksLikeAMessageAndIsNotSpoken() {
        assertNull(SpokenName.of(named("Akşam yemeğe gel bekliyoruz", TextKind.LIST_CONTENT)))
    }

    @Test
    fun numbersAreNeverSpoken() {
        assertNull(SpokenName.of(named("0555 000 00 01", TextKind.LIST_CONTENT)))
        assertNull(SpokenName.of(named("Kod 1234", TextKind.UI_LABEL)))
    }

    @Test
    fun typedOtherSecretAndMissingTextsAreNotSpoken() {
        assertNull(SpokenName.of(named("merhaba", TextKind.USER_INPUT)))
        assertNull(SpokenName.of(named("Bir başlık", TextKind.OTHER_TEXT)))
        assertNull(SpokenName.of(named("Ekran", TextKind.UI_LABEL, secret = true)))
        assertNull(SpokenName.of(named(null, TextKind.NONE)))
        assertNull(SpokenName.of(null))
    }
}
