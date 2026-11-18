package tr.yasliasistan.app.accessibility

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import tr.yasliasistan.app.R
import tr.yasliasistan.core.log.SafeLog

/** Real navigator. Only [AccessibilitySetupController] calls [openAccessibilitySettings]. */
class SystemSettingsNavigator(private val activity: Activity) : SettingsNavigator {

    override fun openAccessibilitySettings() {
        val component = ComponentName(activity, AssistantAccessibilityService::class.java)
            .flattenToString()
        // The fragment args ask Settings to scroll to / highlight our service where supported.
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component) })
        start(intent)
    }

    /** App info page, where "Allow restricted settings" lives on Android 13+ (TK-15). */
    fun openAppInfo() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
        )
        start(intent)
    }

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            SafeLog.event(TAG, "settings activity not found", SafeLog.Level.WARN)
            Toast.makeText(activity, R.string.a11y_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TAG = "A11ySetup"
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
