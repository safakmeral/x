package tr.yasliasistan.core.consent

/**
 * Holds the user's consent decisions. Every purpose defaults to "no" (not pre-ticked); a decision
 * counts only for the notice text version it was given for. Changes take effect in memory first,
 * so a revocation stops cloud calls at once, then they are written to [store].
 */
class ConsentManager(
    private val store: ConsentStore,
    private val currentTextVersion: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloudTransferGate {

    private val lock = Any()
    private val records: MutableMap<ConsentPurpose, ConsentRecord> =
        store.load().associateBy { it.purpose }.toMutableMap()
    private var listener: ((Map<ConsentPurpose, ConsentStatus>) -> Unit)? = null

    fun status(purpose: ConsentPurpose): ConsentStatus = synchronized(lock) {
        val record = records[purpose] ?: return ConsentStatus.NOT_ASKED
        when {
            record.textVersion != currentTextVersion -> ConsentStatus.OUTDATED
            record.decision == ConsentDecision.GRANTED -> ConsentStatus.GRANTED
            else -> ConsentStatus.REFUSED
        }
    }

    fun isGranted(purpose: ConsentPurpose): Boolean = status(purpose) == ConsentStatus.GRANTED

    override fun cloudTransferAllowed(): Boolean = isGranted(ConsentPurpose.CLOUD_TRANSFER)

    /** The stored record (time, version) for display in "Verilerim"; null if never answered. */
    fun record(purpose: ConsentPurpose): ConsentRecord? = synchronized(lock) { records[purpose] }

    fun snapshot(): Map<ConsentPurpose, ConsentStatus> =
        ConsentPurpose.entries.associateWith { status(it) }

    /** True while any purpose is unanswered or was answered for an older text. */
    fun needsPrompt(): Boolean = snapshot().values.any {
        it == ConsentStatus.NOT_ASKED || it == ConsentStatus.OUTDATED
    }

    fun grant(purpose: ConsentPurpose) = decide(purpose, ConsentDecision.GRANTED)

    fun refuse(purpose: ConsentPurpose) = decide(purpose, ConsentDecision.REFUSED)

    /** One-step withdrawal from the settings (NFG-78); same as refusing, with a new time stamp. */
    fun revoke(purpose: ConsentPurpose) = refuse(purpose)

    /** "Tüm verilerimi sil" (NFG-82, G-50): forget every decision; everything is "no" again. */
    fun clearAll() {
        synchronized(lock) {
            records.clear()
            store.clear()
        }
        notifyListener()
    }

    fun setListener(listener: ((Map<ConsentPurpose, ConsentStatus>) -> Unit)?) {
        synchronized(lock) { this.listener = listener }
    }

    private fun decide(purpose: ConsentPurpose, decision: ConsentDecision) {
        val record = ConsentRecord(purpose, decision, clock(), currentTextVersion)
        synchronized(lock) {
            records[purpose] = record
            store.save(record)
        }
        notifyListener()
    }

    private fun notifyListener() {
        val current = synchronized(lock) { listener } ?: return
        current(snapshot())
    }
}
