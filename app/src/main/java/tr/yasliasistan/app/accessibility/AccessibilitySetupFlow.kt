package tr.yasliasistan.app.accessibility

/**
 * Steps of the accessibility-permission setup (NFG-65).
 * The system settings may only be opened after the user has seen the disclosure
 * and pressed the explicit consent button.
 */
enum class SetupStep {
    /** Home: the service is off; the user can start the setup. */
    INTRO,

    /** In-app disclosure (what is read / not read, and why) with an explicit consent button. */
    DISCLOSURE,

    /** Consent given and settings opened; waiting for the user to switch the service on. */
    WAITING_FOR_USER,
}

enum class SetupEvent {
    START_SETUP,
    ACCEPT,
    DECLINE,
    OPEN_SETTINGS_AGAIN,
}

/** Side effects the UI must perform. */
enum class SetupEffect {
    OPEN_ACCESSIBILITY_SETTINGS,
}

data class SetupTransition(val step: SetupStep, val effect: SetupEffect? = null)

/**
 * Pure state machine for the setup screens. It is the single place that can request
 * [SetupEffect.OPEN_ACCESSIBILITY_SETTINGS], and it does so only from the disclosure
 * (on ACCEPT) or after consent was already given in this flow (WAITING_FOR_USER).
 */
object AccessibilitySetupFlow {

    fun reduce(step: SetupStep, event: SetupEvent): SetupTransition = when (step) {
        SetupStep.INTRO -> when (event) {
            SetupEvent.START_SETUP -> SetupTransition(SetupStep.DISCLOSURE)
            // Never jump to settings without the disclosure; show it instead.
            SetupEvent.OPEN_SETTINGS_AGAIN -> SetupTransition(SetupStep.DISCLOSURE)
            SetupEvent.ACCEPT, SetupEvent.DECLINE -> SetupTransition(SetupStep.INTRO)
        }

        SetupStep.DISCLOSURE -> when (event) {
            SetupEvent.ACCEPT ->
                SetupTransition(SetupStep.WAITING_FOR_USER, SetupEffect.OPEN_ACCESSIBILITY_SETTINGS)
            SetupEvent.DECLINE -> SetupTransition(SetupStep.INTRO)
            SetupEvent.START_SETUP, SetupEvent.OPEN_SETTINGS_AGAIN ->
                SetupTransition(SetupStep.DISCLOSURE)
        }

        SetupStep.WAITING_FOR_USER -> when (event) {
            SetupEvent.OPEN_SETTINGS_AGAIN ->
                SetupTransition(SetupStep.WAITING_FOR_USER, SetupEffect.OPEN_ACCESSIBILITY_SETTINGS)
            SetupEvent.DECLINE -> SetupTransition(SetupStep.INTRO)
            SetupEvent.START_SETUP -> SetupTransition(SetupStep.DISCLOSURE)
            SetupEvent.ACCEPT -> SetupTransition(SetupStep.WAITING_FOR_USER)
        }
    }
}

/** Opens the system accessibility settings. Implemented by the activity, faked in tests. */
fun interface SettingsNavigator {
    fun openAccessibilitySettings()
}

/** Holds the current step and runs effects; the only caller of [SettingsNavigator]. */
class AccessibilitySetupController(
    private val navigator: SettingsNavigator,
    initial: SetupStep = SetupStep.INTRO,
) {
    var step: SetupStep = initial
        private set

    fun onEvent(event: SetupEvent): SetupStep {
        val transition = AccessibilitySetupFlow.reduce(step, event)
        step = transition.step
        if (transition.effect == SetupEffect.OPEN_ACCESSIBILITY_SETTINGS) {
            navigator.openAccessibilitySettings()
        }
        return step
    }
}
