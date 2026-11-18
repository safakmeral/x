package tr.yasliasistan.app.privacy

import tr.yasliasistan.app.R
import tr.yasliasistan.core.consent.ConsentPurpose

/** Which strings make up each part of the notice; shared by the screens and read-aloud. */
object PrivacyTexts {

    val intro = listOf(
        R.string.privacy_title, R.string.privacy_intro,
        R.string.privacy_stays_title, R.string.privacy_stays,
    )

    fun purposeTitle(purpose: ConsentPurpose): Int = when (purpose) {
        ConsentPurpose.CLOUD_TRANSFER -> R.string.privacy_cloud_title
        ConsentPurpose.RECIPE_MEMORY -> R.string.privacy_recipe_title
        ConsentPurpose.RESEARCH_AUDIO -> R.string.privacy_research_title
    }

    fun purposeBody(purpose: ConsentPurpose): List<Int> = when (purpose) {
        ConsentPurpose.CLOUD_TRANSFER -> listOf(
            R.string.privacy_cloud_body, R.string.privacy_cloud_where,
            R.string.privacy_cloud_risk, R.string.privacy_cloud_no,
        )
        ConsentPurpose.RECIPE_MEMORY -> listOf(R.string.privacy_recipe_body)
        ConsentPurpose.RESEARCH_AUDIO -> listOf(R.string.privacy_research_body)
    }

    /** Long notice (NFG-81). Ids with format arguments are filled by the details screen. */
    val details = listOf(
        R.string.privacy_d_controller_title, R.string.privacy_d_controller,
        R.string.privacy_d_data_title, R.string.privacy_d_data_device, R.string.privacy_d_data_cloud,
        R.string.privacy_d_purpose_title, R.string.privacy_d_purpose,
        R.string.privacy_d_recipients_title, R.string.privacy_d_recipient_google,
        R.string.privacy_d_recipient_groq, R.string.privacy_d_recipient_cloudflare,
        R.string.privacy_d_legal_title, R.string.privacy_d_legal,
        R.string.privacy_d_risks_title, R.string.privacy_d_risks,
        R.string.privacy_d_retention_title, R.string.privacy_d_retention,
        R.string.privacy_d_a11y_title, R.string.a11y_disclosure_why, R.string.a11y_disclosure_reads,
        R.string.a11y_disclosure_not, R.string.a11y_disclosure_data,
        R.string.privacy_d_rights_title, R.string.privacy_d_rights, R.string.privacy_d_withdraw,
    )

    val headings = setOf(
        R.string.privacy_d_controller_title, R.string.privacy_d_data_title,
        R.string.privacy_d_purpose_title, R.string.privacy_d_recipients_title,
        R.string.privacy_d_legal_title, R.string.privacy_d_risks_title,
        R.string.privacy_d_retention_title, R.string.privacy_d_a11y_title,
        R.string.privacy_d_rights_title,
    )
}
