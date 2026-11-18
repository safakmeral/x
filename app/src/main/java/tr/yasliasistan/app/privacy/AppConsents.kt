package tr.yasliasistan.app.privacy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tr.yasliasistan.core.consent.CloudTransferGate
import tr.yasliasistan.core.consent.ConsentManager
import tr.yasliasistan.core.consent.ConsentPurpose
import tr.yasliasistan.core.consent.ConsentStatus

/**
 * The app's single consent holder (G-30). The cloud client (G-34) asks [cloudGate] before it
 * builds any request; recipe saving (G-45) and research audio (G-51) ask [isGranted].
 * Initialised in [tr.yasliasistan.app.AssistantApp.onCreate].
 */
object AppConsents {

    private lateinit var manager: ConsentManager

    private val mutableStatus = MutableStateFlow(
        ConsentPurpose.entries.associateWith { ConsentStatus.NOT_ASKED },
    )

    /** Current status of every purpose, for the consent screen and the home screen. */
    val status: StateFlow<Map<ConsentPurpose, ConsentStatus>> = mutableStatus.asStateFlow()

    /** Returns false (no cloud call) until init and a granted, current-version consent. */
    val cloudGate = CloudTransferGate { ::manager.isInitialized && manager.cloudTransferAllowed() }

    fun init(context: Context) {
        if (::manager.isInitialized) return
        manager = ConsentManager(ConsentPrefsStore(context), PrivacyNotice.TEXT_VERSION)
        manager.setListener { mutableStatus.value = it }
        mutableStatus.value = manager.snapshot()
    }

    fun isGranted(purpose: ConsentPurpose): Boolean =
        ::manager.isInitialized && manager.isGranted(purpose)

    /** Date (epoch millis) of the stored decision, for display; null if never answered. */
    fun decidedAt(purpose: ConsentPurpose): Long? = manager.record(purpose)?.atEpochMillis

    fun grant(purpose: ConsentPurpose) = manager.grant(purpose)

    fun refuse(purpose: ConsentPurpose) = manager.refuse(purpose)

    fun revoke(purpose: ConsentPurpose) = manager.revoke(purpose)

    /** For "Tüm verilerimi sil" (G-50). */
    fun clearAll() = manager.clearAll()
}
