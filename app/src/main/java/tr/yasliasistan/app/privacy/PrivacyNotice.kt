package tr.yasliasistan.app.privacy

import tr.yasliasistan.app.BuildConfig
import tr.yasliasistan.core.consent.DataControllerInfo

/**
 * Version of the privacy notice and consent texts (strings named `privacy_*` and the reused
 * `a11y_disclosure_*`). Bump [TEXT_VERSION] and [TEXT_DATE] whenever their meaning changes:
 * every earlier consent then counts as outdated and is asked again. PrivacyTextVersionTest fails
 * when the texts change without updating its fingerprint.
 */
object PrivacyNotice {
    const val TEXT_VERSION = 1
    const val TEXT_DATE = "17.11.2026"

    /** Data controller identity from app/veri-sorumlusu.properties (via BuildConfig). */
    val dataController: DataControllerInfo
        get() = DataControllerInfo(
            name = BuildConfig.DATA_CONTROLLER_NAME.ifBlank { DataControllerInfo.NAME_PLACEHOLDER },
            contact = BuildConfig.DATA_CONTROLLER_CONTACT.ifBlank { DataControllerInfo.CONTACT_PLACEHOLDER },
        )
}
