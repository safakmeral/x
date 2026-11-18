package tr.yasliasistan.app.session

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.yasliasistan.core.decision.InstalledApp

class CachedAppsSourceTest {

    private var now = 0L
    private var loads = 0
    private val cache = CachedAppsSource(
        source = { loads++; listOf(InstalledApp("Ayarlar", "p", "a")) },
        now = { now },
        ttlMs = 1_000,
    )

    @Test
    fun secondCallWithinTtlUsesTheCache() {
        cache.launchableApps()
        now = 999
        cache.launchableApps()
        assertEquals(1, loads)
    }

    @Test
    fun reloadsAfterTtlSoNewAppsAreSeen() {
        cache.launchableApps()
        now = 1_000
        cache.launchableApps()
        assertEquals(2, loads)
    }

    @Test
    fun optionsAreJoinedWithoutQuestionParticles() {
        assertEquals("A", ResourceTexts.joinOptions(listOf("A"), "ya da"))
        assertEquals("A ya da B", ResourceTexts.joinOptions(listOf("A", "B"), "ya da"))
        assertEquals("A, B ya da C", ResourceTexts.joinOptions(listOf("A", "B", "C"), "ya da"))
    }
}
