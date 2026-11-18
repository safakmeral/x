package tr.yasliasistan.app.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.BodyText
import tr.yasliasistan.app.ui.PageTitle
import tr.yasliasistan.app.ui.PrimaryButton
import tr.yasliasistan.app.ui.ScreenColumn
import tr.yasliasistan.app.ui.SecondaryButton
import tr.yasliasistan.app.ui.theme.AssistantTheme
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.log.SafeLog

/** Address-book permission as this screen sees it. Pure logic. */
enum class ContactsPermissionAdvice {
    GRANTED,

    /** Not granted: show our reason, then the system dialog (NFG-06). */
    ASK,

    /** Denied with "don't ask again": only the app settings page can change it. */
    OPEN_APP_SETTINGS;

    companion object {
        fun from(granted: Boolean, askedBefore: Boolean, rationaleAllowed: Boolean) = when {
            granted -> GRANTED
            askedBefore && !rationaleAllowed -> OPEN_APP_SETTINGS
            else -> ASK
        }
    }
}

/**
 * Asks for READ_CONTACTS at the moment a command needs a person (PLAN G-36, V-14: optional).
 * Our plain-Turkish reason comes first (NFG-06); the system dialog opens only on the user's tap.
 * "Şimdi değil" just closes: the rest of the app keeps working and people are looked for on the
 * screen instead. Closes by itself once the permission is granted.
 */
class ContactsPermissionActivity : ComponentActivity() {

    private val advice = mutableStateOf(ContactsPermissionAdvice.ASK)
    private var askedBefore = false

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedBefore = true
        SafeLog.event(TAG, "contacts permission granted=$granted")
        // G-37: a refusal is said plainly, so a user who cannot read the screen also knows.
        if (!granted) AssistantVoice.sayNow(getString(R.string.contacts_no_permission))
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedBefore = savedInstanceState?.getBoolean(KEY_ASKED) ?: false
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                ContactsPermissionScreen(
                    advice = advice.value,
                    ask = { request.launch(Manifest.permission.READ_CONTACTS) },
                    openSettings = ::openAppSettings,
                    close = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh() // the user may come back from Settings with the permission changed
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ASKED, askedBefore)
    }

    private fun refresh() {
        advice.value = ContactsPermissionAdvice.from(
            granted = hasContactsPermission(this),
            askedBefore = askedBefore,
            rationaleAllowed = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS),
        )
        if (advice.value == ContactsPermissionAdvice.GRANTED) {
            Toast.makeText(this, R.string.contacts_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            SafeLog.event(TAG, "app settings screen missing", SafeLog.Level.WARN)
            Toast.makeText(this, R.string.voice_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "ContactsPermission"
        private const val KEY_ASKED = "asked_for_contacts"

        /** Opens this screen from the assistant service (no activity of its own there). */
        fun start(context: Context) {
            val intent = Intent(context, ContactsPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }
    }
}

@Composable
private fun ContactsPermissionScreen(
    advice: ContactsPermissionAdvice,
    ask: () -> Unit,
    openSettings: () -> Unit,
    close: () -> Unit,
) {
    ScreenColumn {
        PageTitle(stringResource(R.string.contacts_permission_title))
        BodyText(stringResource(R.string.contacts_permission_reason))
        BodyText(stringResource(R.string.contacts_permission_privacy))
        when (advice) {
            ContactsPermissionAdvice.ASK -> PrimaryButton(stringResource(R.string.contacts_permission_allow), ask)
            ContactsPermissionAdvice.OPEN_APP_SETTINGS -> {
                BodyText(stringResource(R.string.contacts_permission_denied_steps))
                PrimaryButton(stringResource(R.string.voice_open_app_settings), openSettings)
            }
            ContactsPermissionAdvice.GRANTED -> Unit
        }
        SecondaryButton(stringResource(R.string.contacts_permission_not_now), close)
    }
}
