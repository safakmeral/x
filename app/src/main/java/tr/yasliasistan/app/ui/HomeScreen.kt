package tr.yasliasistan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.cloud.CloudSetupEntry
import tr.yasliasistan.app.listen.ListenSetupEntry
import tr.yasliasistan.app.privacy.PrivacyEntry

/** Home: greets the user and shows whether the accessibility service is on. */
@Composable
fun HomeScreen(serviceConnected: Boolean, onStartSetup: () -> Unit) {
    ScreenColumn {
        PageTitle(stringResource(R.string.home_title))
        BodyText(stringResource(R.string.home_body))
        if (serviceConnected) {
            SectionTitle(stringResource(R.string.home_ready))
        } else {
            BodyText(stringResource(R.string.home_needs_permission))
            PrimaryButton(stringResource(R.string.home_start_setup), onStartSetup)
        }
        VoiceSection()
        ListenSetupEntry()
        PrivacyEntry()
        CloudSetupEntry()
    }
}
