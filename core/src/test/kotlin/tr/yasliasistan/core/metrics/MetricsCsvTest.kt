package tr.yasliasistan.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import tr.yasliasistan.core.decision.DecisionSource

class MetricsCsvTest {

    @Test
    fun headerAndRowsUseFixedColumnsAndEmptyCells() {
        val rows = listOf(
            MetricRow(3, 7, MetricRecord(MetricKind.TAP_TO_LISTENING, atMs = 1000, durationMs = 420)),
            MetricRow(3, 7, MetricRecord(MetricKind.DECISION, 2000, 35, DecisionSource.RULE, MetricOutcome.ACT)),
            MetricRow(3, 7, MetricRecord(MetricKind.ERROR, 3000, errorCode = ErrorCode.APP_LAUNCH_FAILED)),
            MetricRow(3, 7, MetricRecord(MetricKind.TASK_END, 4000, 9000, outcome = MetricOutcome.COMPLETED, count = 1)),
        )
        val expected = """
            session,app_version,at_ms,kind,duration_ms,source,outcome,failure_cause,error_code,count
            3,7,1000,TAP_TO_LISTENING,420,,,,,
            3,7,2000,DECISION,35,RULE,ACT,,,
            3,7,3000,ERROR,,,,EXECUTION,APP_LAUNCH_FAILED,
            3,7,4000,TASK_END,9000,,COMPLETED,,,1
        """.trimIndent() + "\n"
        assertEquals(expected, MetricsCsv.format(rows))
    }

    @Test
    fun emptyExportHasOnlyTheHeader() {
        assertEquals(MetricsCsv.HEADER.joinToString(",") + "\n", MetricsCsv.format(emptyList()))
    }

    @Test
    fun noEnumNameNeedsCsvQuoting() {
        val names = MetricKind.entries + MetricOutcome.entries + ErrorCode.entries + FailureCause.entries + DecisionSource.entries
        names.forEach { value ->
            val text = value.toString()
            assertFalse(text, text.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        }
    }

    @Test
    fun everyLineHasAsManyCellsAsTheHeader() {
        val row = MetricRow(1, 1, MetricRecord(MetricKind.SESSION_START, 0, outcome = MetricOutcome.VOICE))
        assertEquals(MetricsCsv.HEADER.size, MetricsCsv.line(row).split(',').size)
    }

    @Test
    fun retentionCutoffIsThirtyDays() {
        val now = 100L * MetricsRetention.DAY_MS
        assertEquals(70L * MetricsRetention.DAY_MS, MetricsRetention.cutoff(now))
    }
}
