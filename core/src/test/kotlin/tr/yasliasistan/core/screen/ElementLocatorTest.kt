package tr.yasliasistan.core.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.metrics.ScreenMetrics

class ElementLocatorTest {

    private var misses = 0
    private val locator = ElementLocator(object : ScreenMetrics {
        override fun elementNotFound() {
            misses++
        }
    })
    private val settings = ScreenCompressor().compress(ScreenFixtures.load("settings.json"), ScreenFixtures.filter())

    @Test
    fun findsByNormalizedTurkishName() {
        val found = locator.find(settings, "bağlantılar") as LookupResult.Found
        assertEquals(5, found.element.id)
        assertEquals(2, (locator.find(settings, "ARA") as LookupResult.Found).element.id)
        assertEquals(0, misses)
    }

    @Test
    fun missIsCountedAndReportsUnlabeledIcons() {
        val result = locator.find(settings, "Paylaş") as LookupResult.NotFound
        assertEquals(1, result.unlabeledIcons)
        assertEquals(1, misses)
    }

    @Test
    fun severalMatchesAreAmbiguous() {
        val rows = listOf("Ahmet Yılmaz", "Ahmet Kaya").mapIndexed { i, name ->
            RawNode(className = "android.widget.Button", text = name, clickable = true, bounds = Bounds(0, i * 10, 10, i * 10 + 10))
        }
        val snap = ScreenCompressor().compress(
            listOf(RawWindow(1, WindowKind.APPLICATION, packageName = "x", root = RawNode(bounds = Bounds(0, 0, 10, 20), children = rows))),
            ScreenFixtures.filter(),
        )
        assertEquals(2, (locator.find(snap, "Ahmet") as LookupResult.Ambiguous).candidates.size)
        assertTrue(locator.find(snap, "Ahmet Kaya") is LookupResult.Found)
    }

    @Test
    fun passwordFieldsNeverMatchByText() {
        val login = ScreenCompressor().compress(ScreenFixtures.load("login.json"), ScreenFixtures.filter())
        assertTrue(locator.find(login, "Sifre123!") is LookupResult.NotFound)
    }
}
