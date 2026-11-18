package tr.yasliasistan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import tr.yasliasistan.app.accessibility.AccessibilityServiceStatus
import tr.yasliasistan.app.accessibility.AccessibilitySetupController
import tr.yasliasistan.app.accessibility.SetupEvent
import tr.yasliasistan.app.accessibility.SetupStep
import tr.yasliasistan.app.accessibility.SystemSettingsNavigator
import tr.yasliasistan.app.ui.AccessibilityDisclosureScreen
import tr.yasliasistan.app.ui.AccessibilityWaitingScreen
import tr.yasliasistan.app.ui.HomeScreen
import tr.yasliasistan.app.ui.theme.AssistantTheme

class MainActivity : ComponentActivity() {

    private val navigator by lazy { SystemSettingsNavigator(this) }
    private lateinit var controller: AccessibilitySetupController
    private val step = mutableStateOf(SetupStep.INTRO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val restored = savedInstanceState?.getString(KEY_STEP)
            ?.let { name -> SetupStep.entries.firstOrNull { it.name == name } }
            ?: SetupStep.INTRO
        controller = AccessibilitySetupController(navigator, restored)
        step.value = restored

        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                val connected by AccessibilityServiceStatus.connected.collectAsState()
                val current by step
                when {
                    connected -> HomeScreen(serviceConnected = true, onStartSetup = {})
                    current == SetupStep.DISCLOSURE -> {
                        BackHandler { send(SetupEvent.DECLINE) }
                        AccessibilityDisclosureScreen(
                            onAccept = { send(SetupEvent.ACCEPT) },
                            onDecline = { send(SetupEvent.DECLINE) },
                        )
                    }
                    current == SetupStep.WAITING_FOR_USER -> {
                        BackHandler { send(SetupEvent.DECLINE) }
                        AccessibilityWaitingScreen(
                            onOpenSettingsAgain = { send(SetupEvent.OPEN_SETTINGS_AGAIN) },
                            onOpenAppInfo = navigator::openAppInfo,
                            onCancel = { send(SetupEvent.DECLINE) },
                        )
                    }
                    else -> HomeScreen(
                        serviceConnected = false,
                        onStartSetup = { send(SetupEvent.START_SETUP) },
                    )
                }
            }
        }
    }

    private fun send(event: SetupEvent) {
        step.value = controller.onEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_STEP, controller.step.name)
    }

    private companion object {
        const val KEY_STEP = "a11y_setup_step"
    }
}
