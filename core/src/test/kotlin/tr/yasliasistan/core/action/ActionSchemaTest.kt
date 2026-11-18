package tr.yasliasistan.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.action.ActionFixtures.home
import tr.yasliasistan.core.action.ActionFixtures.settingsApp

class ActionSchemaTest {

    private val apps = listOf(settingsApp)

    private fun parse(wire: WireAction) = ActionSchema.parse(wire, home, apps)

    private fun error(wire: WireAction) = (parse(wire) as WireResult.Invalid).error

    private fun action(wire: WireAction) = (parse(wire) as WireResult.Ok).action

    @Test
    fun everyKindHasAUniqueWireName() {
        val names = ActionKind.entries.map { it.wireName }
        assertEquals(names.size, names.toSet().size)
        assertEquals(1, ActionSchema.VERSION)
    }

    @Test
    fun tapResolvesTheElementOfThePlanningScreen() {
        val tap = action(WireAction("tap", element = 2)) as ScreenAction.Tap
        assertEquals(ElementRef.of(home.element(2)!!), tap.target)
    }

    @Test
    fun unknownKindsAndElementsAreRejected() {
        assertEquals(WireError.UNKNOWN_KIND, error(WireAction("click_xy")))
        assertEquals(WireError.MISSING_ELEMENT, error(WireAction("tap")))
        assertEquals(WireError.UNKNOWN_ELEMENT, error(WireAction("tap", element = 99)))
    }

    @Test
    fun typingRules() {
        assertEquals(WireError.SECRET_FIELD, error(WireAction("type", element = 5, text = "1234")))
        assertEquals(WireError.NOT_EDITABLE, error(WireAction("type", element = 2, text = "x")))
        assertEquals(WireError.BAD_TEXT, error(WireAction("type", element = 4, text = " ")))
        assertEquals(WireError.BAD_TEXT, error(WireAction("type", element = 4, text = "a".repeat(501))))
        assertTrue(action(WireAction("type", element = 4, text = "ses")) is ScreenAction.TypeText)
    }

    @Test
    fun tapOnSecretFieldIsRejected() {
        assertEquals(WireError.SECRET_FIELD, error(WireAction("tap", element = 5)))
    }

    @Test
    fun scrollDefaultsToMediumAndMayOmitTheElement() {
        val scroll = action(WireAction("scroll", direction = "down")) as ScreenAction.Scroll
        assertEquals(ScrollLength.MEDIUM, scroll.length)
        assertEquals(null, scroll.target)
        assertEquals(WireError.BAD_DIRECTION, error(WireAction("scroll", direction = "diagonal")))
        assertEquals(WireError.BAD_LENGTH, error(WireAction("scroll", direction = "up", length = "huge")))
    }

    @Test
    fun appsWaitsAndReasonsAreChecked() {
        assertEquals(ScreenAction.OpenApp(settingsApp), action(WireAction("open_app", app = settingsApp.packageName)))
        assertEquals(WireError.UNKNOWN_APP, error(WireAction("open_app", app = "com.example.unknown")))
        assertEquals(WireError.BAD_WAIT, error(WireAction("wait", waitMs = 60_000)))
        assertEquals(WireError.BAD_WAIT, error(WireAction("wait")))
        assertEquals(WireError.BAD_REASON, error(WireAction("hand_over", reason = "bored")))
    }

    @Test
    fun everyActionSurvivesARoundTrip() {
        val actions = listOf(
            action(WireAction("tap", element = 2)),
            action(WireAction("long_press", element = 3)),
            action(WireAction("scroll", element = 1, direction = "left", length = "long")),
            action(WireAction("type", element = 4, text = "ses")),
            ScreenAction.Back, ScreenAction.Home, ScreenAction.Done,
            ScreenAction.OpenApp(settingsApp),
            ScreenAction.Wait(500),
            ScreenAction.HandOver(HandOverReason.SECRET_FIELD),
        )
        assertEquals(ActionKind.entries.toSet(), actions.map { it.kind }.toSet())
        for (a in actions) assertEquals(a, action(ActionSchema.toWire(a)))
    }

    @Test
    fun typedTextIsNotInToString() {
        val type = action(WireAction("type", element = 4, text = "gizli metin"))
        assertFalse(type.toString().contains("gizli"))
        assertFalse(WireAction("type", element = 4, text = "gizli metin").toString().contains("gizli"))
        assertFalse(ElementRef.of(home.element(2)!!).toString().contains("Ekran"))
    }
}
