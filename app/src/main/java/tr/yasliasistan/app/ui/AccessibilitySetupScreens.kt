package tr.yasliasistan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R

/** In-app disclosure shown before any settings screen, with explicit consent (NFG-65). */
@Composable
fun AccessibilityDisclosureScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    ScreenColumn {
        PageTitle(stringResource(R.string.a11y_disclosure_title))
        ReadAloudButton(
            listOf(
                R.string.a11y_disclosure_title, R.string.a11y_disclosure_why,
                R.string.a11y_disclosure_reads_title, R.string.a11y_disclosure_reads,
                R.string.a11y_disclosure_not_title, R.string.a11y_disclosure_not,
                R.string.a11y_disclosure_data, R.string.a11y_disclosure_off,
            ),
        )
        BodyText(stringResource(R.string.a11y_disclosure_why))
        SectionTitle(stringResource(R.string.a11y_disclosure_reads_title))
        BodyText(stringResource(R.string.a11y_disclosure_reads))
        SectionTitle(stringResource(R.string.a11y_disclosure_not_title))
        BodyText(stringResource(R.string.a11y_disclosure_not))
        BodyText(stringResource(R.string.a11y_disclosure_data))
        BodyText(stringResource(R.string.a11y_disclosure_off))
        PrimaryButton(stringResource(R.string.a11y_disclosure_accept), onAccept)
        SecondaryButton(stringResource(R.string.a11y_disclosure_decline), onDecline)
    }
}

/** Shown after consent while the user switches the service on; includes restricted-settings help. */
@Composable
fun AccessibilityWaitingScreen(
    onOpenSettingsAgain: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onCancel: () -> Unit,
) {
    ScreenColumn {
        PageTitle(stringResource(R.string.a11y_waiting_title))
        BodyText(stringResource(R.string.a11y_waiting_steps))
        PrimaryButton(stringResource(R.string.a11y_open_settings_again), onOpenSettingsAgain)
        SectionTitle(stringResource(R.string.a11y_restricted_title))
        BodyText(stringResource(R.string.a11y_restricted_steps))
        SecondaryButton(stringResource(R.string.a11y_open_app_info), onOpenAppInfo)
        SecondaryButton(stringResource(R.string.a11y_cancel), onCancel)
    }
}
