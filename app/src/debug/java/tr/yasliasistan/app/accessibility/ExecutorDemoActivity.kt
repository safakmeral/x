package tr.yasliasistan.app.accessibility

import android.app.Activity
import android.os.Bundle
import tr.yasliasistan.app.session.PackageManagerAppsSource
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.action.ElementExpect
import tr.yasliasistan.core.action.ElementRef
import tr.yasliasistan.core.action.ScreenAction
import tr.yasliasistan.core.action.StepExpectation
import tr.yasliasistan.core.action.StepPlanner
import tr.yasliasistan.core.action.TaskStep
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.screen.ElementLocator
import tr.yasliasistan.core.screen.LookupResult

/**
 * DEBUG BUILDS ONLY (src/debug): G-20..G-23 device check. Runs a fixed 3-step task in Settings:
 * open Settings -> tap "Ekran" (found by name on the live screen, scrolling down if needed) ->
 * back. Each step shows its dot/arrow (G-21), is checked and narrated (G-22). Leaving Settings by
 * hand while it runs must stop the task with one spoken question. Needs the accessibility service
 * on. Results are logged as codes only (tag ScreenActions).
 *
 *   adb shell am start -n tr.yasliasistan.app/.accessibility.ExecutorDemoActivity
 */
class ExecutorDemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runner = ScreenActions.runner
        if (runner == null) {
            SafeLog.event(TAG, "accessibility service is off")
        } else {
            val apps = PackageManagerAppsSource(applicationContext)
            val openSettings = StepPlanner {
                apps.launchableApps().firstOrNull { it.packageName == SETTINGS }?.let(ScreenAction::OpenApp)
            }
            val tapDisplay = StepPlanner { screen ->
                (ElementLocator().find(screen, DISPLAY_LABEL) as? LookupResult.Found)
                    ?.let { ScreenAction.Tap(ElementRef.of(it.element)) }
            }
            val steps = listOf(
                TaskStep(openSettings, explore = null),
                TaskStep(tapDisplay, StepExpectation(screenChanges = true), inPackage = SETTINGS, name = DISPLAY_LABEL),
                TaskStep(
                    StepPlanner.fixed(ScreenAction.Back),
                    StepExpectation(element = ElementExpect(DISPLAY_LABEL)),
                    inPackage = SETTINGS,
                    explore = null,
                ),
            )
            runner.runTaskDebug(steps) { outcome ->
                SafeLog.event(TAG, "demo finished: $outcome")
                outcome.message?.let(AssistantVoice::sayNext)
            }
        }
        finish()
    }

    private companion object {
        const val TAG = "ExecutorDemo"
        const val SETTINGS = "com.android.settings"
        const val DISPLAY_LABEL = "Ekran"
    }
}
