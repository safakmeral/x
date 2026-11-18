package tr.yasliasistan.core.consent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentManagerTest {

    private class MemoryStore(initial: List<ConsentRecord> = emptyList()) : ConsentStore {
        val records = initial.associateBy { it.purpose }.toMutableMap()
        override fun load(): List<ConsentRecord> = records.values.toList()
        override fun save(record: ConsentRecord) { records[record.purpose] = record }
        override fun clear() = records.clear()
    }

    private var now = 1_000L
    private fun manager(store: ConsentStore = MemoryStore(), version: Int = 1) =
        ConsentManager(store, currentTextVersion = version, clock = { now })

    @Test
    fun everyPurposeStartsNotAskedAndNotGranted() {
        val m = manager()
        ConsentPurpose.entries.forEach {
            assertEquals(ConsentStatus.NOT_ASKED, m.status(it))
            assertFalse(m.isGranted(it))
        }
        assertFalse(m.cloudTransferAllowed())
    }

    @Test
    fun grantingOnePurposeLeavesTheOthersUntouched() {
        val m = manager()
        m.grant(ConsentPurpose.CLOUD_TRANSFER)
        assertTrue(m.isGranted(ConsentPurpose.CLOUD_TRANSFER))
        assertEquals(ConsentStatus.NOT_ASKED, m.status(ConsentPurpose.RECIPE_MEMORY))
        assertEquals(ConsentStatus.NOT_ASKED, m.status(ConsentPurpose.RESEARCH_AUDIO))

        m.grant(ConsentPurpose.RECIPE_MEMORY)
        assertFalse(m.isGranted(ConsentPurpose.RESEARCH_AUDIO))
    }

    @Test
    fun refusingCloudDoesNotAffectOtherGrants() {
        val m = manager()
        m.grant(ConsentPurpose.RECIPE_MEMORY)
        m.refuse(ConsentPurpose.CLOUD_TRANSFER)
        assertTrue(m.isGranted(ConsentPurpose.RECIPE_MEMORY))
        assertEquals(ConsentStatus.REFUSED, m.status(ConsentPurpose.CLOUD_TRANSFER))
        assertFalse(m.cloudTransferAllowed())
    }

    @Test
    fun revokingTakesEffectImmediatelyAndIsStored() {
        val store = MemoryStore()
        val m = manager(store)
        m.grant(ConsentPurpose.CLOUD_TRANSFER)
        assertTrue(m.cloudTransferAllowed())

        now = 5_000L
        m.revoke(ConsentPurpose.CLOUD_TRANSFER)
        assertFalse(m.cloudTransferAllowed())
        assertEquals(ConsentStatus.REFUSED, m.status(ConsentPurpose.CLOUD_TRANSFER))
        val stored = store.records.getValue(ConsentPurpose.CLOUD_TRANSFER)
        assertEquals(ConsentDecision.REFUSED, stored.decision)
        assertEquals(5_000L, stored.atEpochMillis)
    }

    @Test
    fun recordContainsTimeAndTextVersion() {
        val store = MemoryStore()
        now = 42L
        manager(store, version = 3).grant(ConsentPurpose.RESEARCH_AUDIO)
        val rec = store.records.getValue(ConsentPurpose.RESEARCH_AUDIO)
        assertEquals(ConsentRecord(ConsentPurpose.RESEARCH_AUDIO, ConsentDecision.GRANTED, 42L, 3), rec)
    }

    @Test
    fun grantSurvivesRestartWithSameTextVersion() {
        val store = MemoryStore()
        manager(store).grant(ConsentPurpose.CLOUD_TRANSFER)
        assertTrue(manager(store).cloudTransferAllowed())
    }

    @Test
    fun newTextVersionMakesOldGrantInvalidAndAsksAgain() {
        val store = MemoryStore()
        manager(store, version = 1).grant(ConsentPurpose.CLOUD_TRANSFER)
        val updated = manager(store, version = 2)
        assertEquals(ConsentStatus.OUTDATED, updated.status(ConsentPurpose.CLOUD_TRANSFER))
        assertFalse(updated.cloudTransferAllowed())
        assertTrue(updated.needsPrompt())
    }

    @Test
    fun oldRefusalAlsoNeedsAskingAgainAfterTextChange() {
        val store = MemoryStore()
        manager(store, version = 1).refuse(ConsentPurpose.RECIPE_MEMORY)
        assertEquals(ConsentStatus.OUTDATED, manager(store, version = 2).status(ConsentPurpose.RECIPE_MEMORY))
    }

    @Test
    fun needsPromptOnlyUntilEveryPurposeIsAnswered() {
        val m = manager()
        assertTrue(m.needsPrompt())
        m.grant(ConsentPurpose.CLOUD_TRANSFER)
        m.refuse(ConsentPurpose.RECIPE_MEMORY)
        assertTrue(m.needsPrompt())
        m.refuse(ConsentPurpose.RESEARCH_AUDIO)
        assertFalse(m.needsPrompt())
    }

    @Test
    fun clearAllForgetsEverything() {
        val store = MemoryStore()
        val m = manager(store)
        m.grant(ConsentPurpose.CLOUD_TRANSFER)
        m.clearAll()
        assertFalse(m.cloudTransferAllowed())
        assertEquals(ConsentStatus.NOT_ASKED, m.status(ConsentPurpose.CLOUD_TRANSFER))
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun listenerSeesEveryChange() {
        val m = manager()
        val seen = mutableListOf<Map<ConsentPurpose, ConsentStatus>>()
        m.setListener { seen += it }
        m.grant(ConsentPurpose.CLOUD_TRANSFER)
        m.revoke(ConsentPurpose.CLOUD_TRANSFER)
        assertEquals(ConsentStatus.GRANTED, seen[0][ConsentPurpose.CLOUD_TRANSFER])
        assertEquals(ConsentStatus.REFUSED, seen[1][ConsentPurpose.CLOUD_TRANSFER])
    }

    @Test
    fun corruptStoredPurposeKeysAreIgnoredSafely() {
        assertEquals(null, ConsentPurpose.fromKey("unknown"))
        ConsentPurpose.entries.forEach { assertEquals(it, ConsentPurpose.fromKey(it.key)) }
    }
}
