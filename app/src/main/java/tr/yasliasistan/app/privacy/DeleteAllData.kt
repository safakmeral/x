package tr.yasliasistan.app.privacy

import tr.yasliasistan.app.cloud.AppCloud
import tr.yasliasistan.app.metrics.AppMetrics
import tr.yasliasistan.core.log.SafeLog

/**
 * "Tüm verilerimi sil" (KVKK data subject right, FG/NFG data rights): removes everything the app
 * keeps about the user on this phone in one step:
 * - the measurement log (only numbers and codes, 30 days),
 * - the masked copies of cloud requests (7 days),
 * - all consent records (every consent is withdrawn; the app asks again),
 * - the saved proxy address and access code.
 * Memory-only data (contacts, screen, conversation context) is dropped when the session ends and
 * is never written to disk, so there is nothing else to delete. Safe to call from the main thread.
 */
object DeleteAllData {
    fun run(done: () -> Unit = {}) {
        try {
            AppCloud.auditCopies.clear()
        } catch (e: UninitializedPropertyAccessException) {
            SafeLog.event(TAG, "no audit store yet")
        }
        AppCloud.forgetSetup()
        // A running guide or task must not keep working with data that was just deleted.
        tr.yasliasistan.app.device.PhotoGuideTask.stop()
        AppConsents.clearAll()
        AppMetrics.store.deleteAll {
            SafeLog.event(TAG, "all local data deleted")
            done()
        }
    }

    private const val TAG = "DeleteAllData"
}
