package tr.yasliasistan.app.metrics

import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.metrics.MetricKind
import tr.yasliasistan.core.metrics.MetricRecord
import tr.yasliasistan.core.metrics.MetricRow
import tr.yasliasistan.core.metrics.MetricsCsv
import tr.yasliasistan.core.metrics.MetricsRetention
import tr.yasliasistan.core.metrics.MetricsSink
import java.util.concurrent.Executor

/**
 * On-device metric log (PLAN G-18; FG-90, NFG-59, V-15).
 *
 * - [record] returns at once; all database work runs on one low-priority background thread, in order,
 *   so the dialog never waits for storage.
 * - Records older than the retention period (30 days) are deleted when the database is first used and
 *   then at most every [pruneEveryMs] while the app records.
 * - [deleteAll] removes everything (for "Tüm verilerimi sil", G-50).
 * - Data never leaves the device: no network code here; the CSV export exists only in debug builds.
 */
class MetricsStore(
    private val dao: () -> MetricDao,
    private val appVersion: Int,
    private val executor: Executor,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val pruneEveryMs: Long = PRUNE_EVERY_MS,
) : MetricsSink {

    // Touched only on the executor thread.
    private var session = UNKNOWN
    private var lastPruneAt = Long.MIN_VALUE

    override fun record(record: MetricRecord) = executor.execute { safely { store(record) } }

    /** Deletes every metric; [done] runs on the background thread. */
    fun deleteAll(done: () -> Unit = {}) = executor.execute {
        safely {
            dao().deleteAll()
            session = 0
            SafeLog.event(TAG, "all metrics deleted")
        }
        done()
    }

    /** Deletes expired records now (also done automatically). */
    fun pruneNow() = executor.execute { safely { prune(wallClock()) } }

    /** Debug export: hands the CSV text to [deliver] on the background thread. */
    fun exportCsv(deliver: (csv: String, rows: Int) -> Unit) = executor.execute {
        val rows: List<MetricRow> = try {
            prune(wallClock())
            dao().all().map { it.toRow() }
        } catch (e: RuntimeException) {
            SafeLog.event(TAG, "export failed: ${e.javaClass.simpleName}", SafeLog.Level.WARN)
            emptyList()
        }
        deliver(MetricsCsv.format(rows), rows.size)
    }

    private fun store(record: MetricRecord) {
        val now = wallClock()
        if (lastPruneAt == Long.MIN_VALUE || now - lastPruneAt >= pruneEveryMs) prune(now)
        if (session == UNKNOWN) session = dao().maxSession() ?: 0
        if (record.kind == MetricKind.SESSION_START) session++
        dao().insert(MetricEntity.of(session, appVersion, record))
    }

    private fun prune(now: Long) {
        lastPruneAt = now
        val removed = dao().deleteOlderThan(MetricsRetention.cutoff(now))
        if (removed > 0) SafeLog.event(TAG, "expired metrics deleted: $removed")
    }

    /** A metric must never crash the assistant; failures are logged without content. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            SafeLog.event(TAG, "metric store error: ${e.javaClass.simpleName}", SafeLog.Level.WARN)
        }
    }

    companion object {
        const val PRUNE_EVERY_MS: Long = 6L * 60 * 60 * 1000
        private const val UNKNOWN = -1
        private const val TAG = "Metrics"
    }
}
