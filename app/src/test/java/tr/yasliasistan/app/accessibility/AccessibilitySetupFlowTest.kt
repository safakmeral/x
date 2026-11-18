package tr.yasliasistan.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilitySetupFlowTest {

    private class CountingNavigator : SettingsNavigator {
        var calls = 0
        override fun openAccessibilitySettings() {
            calls++
        }
    }

    @Test
    fun settingsAreNeverOpenedWithoutConsent() {
        val nav = CountingNavigator()
        val controller = AccessibilitySetupController(nav)

        controller.onEvent(SetupEvent.OPEN_SETTINGS_AGAIN)
        controller.onEvent(SetupEvent.START_SETUP)
        controller.onEvent(SetupEvent.OPEN_SETTINGS_AGAIN)
        controller.onEvent(SetupEvent.START_SETUP)
        controller.onEvent(SetupEvent.DECLINE)
        controller.onEvent(SetupEvent.ACCEPT) // ACCEPT on home is not consent: disclosure not shown

        assertEquals(0, nav.calls)
        assertEquals(SetupStep.INTRO, controller.step)
    }

    @Test
    fun noStepOutsideTheConsentPathProducesTheEffect() {
        for (step in SetupStep.values()) {
            for (event in SetupEvent.values()) {
                val effect = AccessibilitySetupFlow.reduce(step, event).effect
                val allowed = (step == SetupStep.DISCLOSURE && event == SetupEvent.ACCEPT) ||
                    (step == SetupStep.WAITING_FOR_USER && event == SetupEvent.OPEN_SETTINGS_AGAIN)
                if (!allowed) assertEquals("$step + $event", null, effect)
            }
        }
    }

    @Test
    fun startSetupShowsDisclosureFirst() {
        val nav = CountingNavigator()
        val controller = AccessibilitySetupController(nav)

        assertEquals(SetupStep.DISCLOSURE, controller.onEvent(SetupEvent.START_SETUP))
        assertEquals(0, nav.calls)
    }

    @Test
    fun acceptingTheDisclosureOpensSettingsOnce() {
        val nav = CountingNavigator()
        val controller = AccessibilitySetupController(nav)

        controller.onEvent(SetupEvent.START_SETUP)
        assertEquals(SetupStep.WAITING_FOR_USER, controller.onEvent(SetupEvent.ACCEPT))
        assertEquals(1, nav.calls)
    }

    @Test
    fun afterConsentSettingsCanBeReopened() {
        val nav = CountingNavigator()
        val controller = AccessibilitySetupController(nav)

        controller.onEvent(SetupEvent.START_SETUP)
        controller.onEvent(SetupEvent.ACCEPT)
        controller.onEvent(SetupEvent.OPEN_SETTINGS_AGAIN)
        assertEquals(2, nav.calls)
    }

    @Test
    fun decliningReturnsHomeWithoutSettings() {
        val nav = CountingNavigator()
        val controller = AccessibilitySetupController(nav)

        controller.onEvent(SetupEvent.START_SETUP)
        assertEquals(SetupStep.INTRO, controller.onEvent(SetupEvent.DECLINE))
        assertEquals(0, nav.calls)
    }

    @Test
    fun cancellingWhileWaitingRequiresNewDisclosure() {
        val nav = CountingNavigator()
        val controller = AccessibilitySetupController(nav)

        controller.onEvent(SetupEvent.START_SETUP)
        controller.onEvent(SetupEvent.ACCEPT)
        controller.onEvent(SetupEvent.DECLINE)
        controller.onEvent(SetupEvent.OPEN_SETTINGS_AGAIN)

        assertEquals(1, nav.calls)
        assertEquals(SetupStep.DISCLOSURE, controller.step)
    }
}
