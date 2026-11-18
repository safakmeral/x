package tr.yasliasistan.core.metrics

import tr.yasliasistan.core.Defaults

/**
 * CSV export of metric rows (debug builds only, PLAN G-18). Every value is a number or an enum
 * name, so no quoting is needed; empty cells mean "not applicable". Line ends are "\n".
 */
object MetricsCsv {
    val HEADER = listOf(
        "session", "app_version", "at_ms", "kind", "duration_ms",
        "source", "outcome", "failure_cause", "error_code", "count",
    )

    fun format(rows: List<MetricRow>): String = buildString {
        append(HEADER.joinToString(",")).append('\n')
        rows.forEach { append(line(it)).append('\n') }
    }

    fun line(row: MetricRow): String {
        val r = row.record
        return listOf(
            row.session, row.appVersion, r.atMs, r.kind, r.durationMs,
            r.source, r.outcome, r.failureCause, r.errorCode, r.count,
        ).joinToString(",") { it?.toString() ?: "" }
    }
}

/** V-15 / NFG-59: how long metric records are kept. */
object MetricsRetention {
    const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /** Records with an [MetricRecord.atMs] before this are deleted. */
    fun cutoff(nowMs: Long, days: Int = Defaults.METRICS_RETENTION_DAYS): Long = nowMs - days * DAY_MS
}
