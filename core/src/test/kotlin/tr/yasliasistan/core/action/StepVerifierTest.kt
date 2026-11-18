package tr.yasliasistan.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tr.yasliasistan.core.action.ActionFixtures.SETTINGS
import tr.yasliasistan.core.action.ActionFixtures.display
import tr.yasliasistan.core.action.ActionFixtures.displayPage
import tr.yasliasistan.core.action.ActionFixtures.el
import tr.yasliasistan.core.action.ActionFixtures.home
import tr.yasliasistan.core.action.ActionFixtures.screen
import tr.yasliasistan.core.action.ActionFixtures.settingsApp
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ElementRole

/** G-22 (FG-24): comparing the screen after a step with what the step expected. */
class StepVerifierTest {

    private val launcher = screen(el(1, "Saat", Bounds(0, 0, 100, 100), pkg = "com.example.launcher"), pkg = "com.example.launcher")
    private val parlaklik = StepExpectation(element = ElementExpect("Parlaklık"))

    @Test
    fun expectedElementOnTheNewPageMatches() {
        assertEquals(StepVerdict.MATCH, StepVerifier.verify(parlaklik, home, displayPage, changed = true))
    }

    @Test
    fun roleMustMatchWhenGiven() {
        val asSwitch = StepExpectation(element = ElementExpect("Parlaklık", ElementRole.SWITCH))
        assertEquals(StepVerdict.UNEXPECTED_PAGE, StepVerifier.verify(asSwitch, home, displayPage, changed = true))
    }

    @Test
    fun missingElementOnAnUnchangedScreenIsNoReaction() {
        assertEquals(StepVerdict.NO_REACTION, StepVerifier.verify(parlaklik, home, home, changed = false))
    }

    @Test
    fun missingElementOnAnotherPageIsUnexpected() {
        val other = screen(el(1, "Bluetooth", Bounds(0, 300, 1080, 420)))
        assertEquals(StepVerdict.UNEXPECTED_PAGE, StepVerifier.verify(parlaklik, home, other, changed = true))
    }

    @Test
    fun otherAppInFrontIsUnexpected() {
        val stay = StepExpectation(packageName = SETTINGS)
        assertEquals(StepVerdict.UNEXPECTED_PAGE, StepVerifier.verify(stay, home, launcher, changed = true))
    }

    @Test
    fun appThatDidNotOpenIsNoReaction() {
        val open = StepExpectation(packageName = SETTINGS)
        assertEquals(StepVerdict.NO_REACTION, StepVerifier.verify(open, launcher, launcher, changed = false))
    }

    @Test
    fun requiredChangeThatDidNotHappenIsNoReaction() {
        val change = StepExpectation(screenChanges = true)
        assertEquals(StepVerdict.NO_REACTION, StepVerifier.verify(change, home, home, changed = false))
        assertEquals(StepVerdict.MATCH, StepVerifier.verify(change, home, displayPage, changed = true))
    }

    @Test
    fun derivedExpectationsFollowTheAction() {
        assertEquals(SETTINGS, StepExpectation.derive(ScreenAction.OpenApp(settingsApp), launcher).packageName)
        assertNull(StepExpectation.derive(ScreenAction.Home, home).packageName)
        assertEquals(SETTINGS, StepExpectation.derive(ScreenAction.Tap(ElementRef.of(display)), home).packageName)
    }

    @Test
    fun expectationNeverPrintsTheLabel() {
        assertEquals(false, parlaklik.toString().contains("Parlaklık"))
    }
}
