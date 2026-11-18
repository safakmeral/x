package tr.yasliasistan.app.privacy

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tr.yasliasistan.app.R
import tr.yasliasistan.app.ui.BodyText
import tr.yasliasistan.app.ui.PageTitle
import tr.yasliasistan.app.ui.PrimaryButton
import tr.yasliasistan.app.ui.ReadAloudButton
import tr.yasliasistan.app.ui.ReadAloudTextButton
import tr.yasliasistan.app.ui.ScreenColumn
import tr.yasliasistan.app.ui.SecondaryButton
import tr.yasliasistan.app.ui.SectionTitle
import tr.yasliasistan.core.consent.ConsentPurpose
import tr.yasliasistan.core.consent.ConsentStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Short notice with one card per separate consent (NFG-56, NFG-57, NFG-77). */
@Composable
fun ConsentScreen(
    status: Map<ConsentPurpose, ConsentStatus>,
    decidedAt: (ConsentPurpose) -> Long?,
    onAccept: (ConsentPurpose) -> Unit,
    onRefuse: (ConsentPurpose) -> Unit,
    onDetails: () -> Unit,
    onDone: () -> Unit,
    onDeleteAll: () -> Unit = {},
) {
    var askDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ScreenColumn {
        PageTitle(stringResource(R.string.privacy_title))
        ReadAloudButton(PrivacyTexts.intro.drop(1))
        BodyText(stringResource(R.string.privacy_intro))
        SectionTitle(stringResource(R.string.privacy_stays_title))
        BodyText(stringResource(R.string.privacy_stays))
        ConsentPurpose.entries.forEach { purpose ->
            ConsentCard(
                purpose = purpose,
                status = status[purpose] ?: ConsentStatus.NOT_ASKED,
                decidedAt = decidedAt(purpose),
                onAccept = { onAccept(purpose) },
                onRefuse = { onRefuse(purpose) },
            )
        }
        BodyText(stringResource(R.string.privacy_change_later))
        SecondaryButton(stringResource(R.string.privacy_details_button), onDetails)
        // Data right: one clear button, a plain-Turkish question first (no accidental deletion).
        SectionTitle(stringResource(R.string.data_delete_title))
        if (!askDelete) {
            BodyText(stringResource(R.string.data_delete_what))
            SecondaryButton(stringResource(R.string.data_delete_button)) { askDelete = true }
        } else {
            BodyText(stringResource(R.string.data_delete_ask))
            PrimaryButton(stringResource(R.string.data_delete_yes)) {
                askDelete = false
                onDeleteAll()
            }
            SecondaryButton(stringResource(R.string.data_delete_no)) { askDelete = false }
        }
        PrimaryButton(stringResource(R.string.privacy_done), onDone)
    }
}

/**
 * One consent. Accept and refuse use the very same button style (no dark pattern); nothing is
 * pre-selected. When granted, the refuse button becomes a one-step "İzni geri al" (NFG-78).
 */
@Composable
private fun ConsentCard(
    purpose: ConsentPurpose,
    status: ConsentStatus,
    decidedAt: Long?,
    onAccept: () -> Unit,
    onRefuse: () -> Unit,
) {
    val title = PrivacyTexts.purposeTitle(purpose)
    val body = PrivacyTexts.purposeBody(purpose)
    SectionTitle(stringResource(title))
    ReadAloudButton(listOf(title) + body)
    body.forEach { BodyText(stringResource(it)) }
    BodyText(statusText(status, decidedAt))
    val selected = stringResource(R.string.privacy_selected_suffix)
    val acceptLabel = stringResource(R.string.privacy_accept) +
        if (status == ConsentStatus.GRANTED) selected else ""
    val refuseLabel = when (status) {
        ConsentStatus.GRANTED -> stringResource(R.string.privacy_revoke)
        ConsentStatus.REFUSED -> stringResource(R.string.privacy_refuse) + selected
        else -> stringResource(R.string.privacy_refuse)
    }
    SecondaryButton(acceptLabel, onAccept)
    SecondaryButton(refuseLabel, onRefuse)
}

@Composable
private fun statusText(status: ConsentStatus, decidedAt: Long?): String = when (status) {
    ConsentStatus.GRANTED ->
        stringResource(R.string.privacy_status_granted, decidedAt?.let(::formatDate) ?: "")
    ConsentStatus.REFUSED -> stringResource(R.string.privacy_status_refused)
    ConsentStatus.NOT_ASKED -> stringResource(R.string.privacy_status_not_asked)
    ConsentStatus.OUTDATED -> stringResource(R.string.privacy_status_outdated)
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("tr")).format(Date(epochMillis))

/** Full notice with every NFG-81 item, in large text, readable aloud. */
@Composable
fun PrivacyDetailsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenColumn {
        PageTitle(stringResource(R.string.privacy_details_title))
        BodyText(versionText(context))
        ReadAloudTextButton { PrivacyTexts.details.joinToString("\n") { detailText(context, it) } }
        PrivacyTexts.details.forEach { id ->
            val text = detailText(context, id)
            if (id in PrivacyTexts.headings) SectionTitle(text) else BodyText(text)
        }
        PrimaryButton(stringResource(R.string.privacy_back), onBack)
    }
}

private fun versionText(context: Context): String = context.getString(
    R.string.privacy_details_version, PrivacyNotice.TEXT_VERSION, PrivacyNotice.TEXT_DATE,
)

private fun detailText(context: Context, id: Int): String =
    if (id == R.string.privacy_d_controller) {
        val controller = PrivacyNotice.dataController
        context.getString(id, controller.name, controller.contact)
    } else {
        context.getString(id)
    }
