package tr.yasliasistan.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.metrics.MetricKind
import tr.yasliasistan.core.metrics.MetricOutcome
import tr.yasliasistan.core.metrics.MetricRecord
import tr.yasliasistan.core.metrics.MetricsCsv
import tr.yasliasistan.core.metrics.MetricsRetention.DAY_MS
import java.lang.reflect.Modifier
import java.util.concurrent.Executor

class MetricsStoreTest {

    private val dao = FakeDao()
    private val queued = mutableListOf<Runnable>()
    private var now = 100 * DAY_MS
    private val store = MetricsStore({ dao }, appVersion = 7, executor = Executor { queued += it }, wallClock = { now })

    private fun runQueued() {
        while (queued.isNotEmpty()) queued.removeAt(0).run()
    }

    private fun start() = MetricRecord(MetricKind.SESSION_START, now, outcome = MetricOutcome.VOICE)
    private fun tap(ms: Long) = MetricRecord(MetricKind.TAP_TO_LISTENING, now, ms)

    @Test
    fun recordingNeverTouchesTheDatabaseOnTheCallersThread() {
        store.record(tap(10))
        assertTrue(dao.rows.isEmpty())
        runQueued()
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun sessionsAreNumberedAnonymouslyContinuingFromStoredMaximum() {
        dao.rows += MetricEntity.of(4, 7, start())
        listOf(start(), tap(1), start(), tap(2)).forEach(store::record)
        runQueued()
        assertEquals(listOf(4, 5, 5, 6, 6), dao.rows.map { it.session })
        assertTrue(dao.rows.all { it.appVersion == 7 })
    }

    @Test
    fun expiredRecordsAreDeletedAutomatically() {
        dao.rows += MetricEntity.of(1, 7, MetricRecord(MetricKind.TAP_TO_LISTENING, now - 31 * DAY_MS, 5))
        dao.rows += MetricEntity.of(1, 7, MetricRecord(MetricKind.TAP_TO_LISTENING, now - 29 * DAY_MS, 6))
        store.record(tap(7))
        runQueued()
        assertEquals(listOf(6L, 7L), dao.rows.map { it.durationMs })
    }

    @Test
    fun pruningRunsAgainAfterTheInterval() {
        store.record(tap(1))
        runQueued()
        now += 40 * DAY_MS
        store.record(tap(2))
        runQueued()
        assertEquals(listOf(2L), dao.rows.map { it.durationMs })
    }

    @Test
    fun deleteAllRemovesEverythingAndRestartsNumbering() {
        listOf(start(), tap(1)).forEach(store::record)
        var done = false
        store.deleteAll { done = true }
        store.record(start())
        runQueued()
        assertTrue(done)
        assertEquals(listOf(1), dao.rows.map { it.session })
    }

    @Test
    fun exportGivesCsvOfAllRows() {
        listOf(start(), tap(42)).forEach(store::record)
        var csv = ""
        var count = -1
        store.exportCsv { text, rows -> csv = text; count = rows }
        runQueued()
        assertEquals(2, count)
        val lines = csv.trimEnd().lines()
        assertEquals(MetricsCsv.HEADER.joinToString(","), lines[0])
        assertEquals("1,7,${now},TAP_TO_LISTENING,42,,,,,", lines[2])
    }

    @Test
    fun storageErrorsNeverCrashTheCaller() {
        dao.fail = true
        store.record(tap(1))
        runQueued()
    }

    @Test
    fun entityHasOnlyNumberAndEnumColumns() {
        val numberTypes = setOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Int::class.javaObjectType, Long::class.javaObjectType)
        val fields = MetricEntity::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        assertTrue(fields.isNotEmpty())
        fields.forEach { assertTrue("${it.name}: ${it.type.simpleName}", it.type in numberTypes || it.type.isEnum) }
    }

    private class FakeDao : MetricDao {
        val rows = mutableListOf<MetricEntity>()
        var fail = false
        override fun insert(entity: MetricEntity) {
            if (fail) throw IllegalStateException("disk full")
            rows += entity
        }
        override fun maxSession(): Int? = rows.maxOfOrNull { it.session }
        override fun deleteOlderThan(cutoffMs: Long): Int {
            if (fail) throw IllegalStateException("disk full")
            val before = rows.size
            rows.removeAll { it.atMs < cutoffMs }
            return before - rows.size
        }
        override fun deleteAll(): Int = rows.size.also { rows.clear() }
        override fun all(): List<MetricEntity> = rows.toList()
    }
}
