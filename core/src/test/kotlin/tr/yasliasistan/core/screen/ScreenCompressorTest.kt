package tr.yasliasistan.core.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCompressorTest {

    private val compressor = ScreenCompressor()

    private fun settings(ownIds: Set<Int> = emptySet()) =
        compressor.compress(ScreenFixtures.load("settings.json"), ScreenFixtures.filter(ownIds))

    private fun login() = compressor.compress(ScreenFixtures.load("login.json"), ScreenFixtures.filter())

    @Test
    fun settingsTreeIsCompressedToNumberedMeaningfulElements() {
        val snap = settings()
        val summary = snap.elements.map { "${it.id}:${it.role}:${it.label}" }
        assertEquals(
            listOf(
                "1:TEXT:Ayarlar",
                "2:ICON_BUTTON:Ara",
                "3:ICON_BUTTON:null",
                "4:LIST:null",
                "5:LIST_ITEM:Bağlantılar, Wi-Fi • Bluetooth",
                "6:LIST_ITEM:Ekran",
                "7:SWITCH:null",
            ),
            summary,
        )
        assertFalse(snap.truncated)
    }

    @Test
    fun elementsKeepStateBoundsAndWindow() {
        val snap = settings()
        val row = snap.element(5)!!
        assertTrue(row.clickable && row.inListItem)
        assertEquals(LabelSource.CHILD_TEXT, row.labelSource)
        assertEquals(Bounds(0, 400, 1080, 600), row.bounds)
        assertEquals(7, row.windowId)
        assertEquals("com.android.settings", row.packageName)
        assertTrue(snap.element(7)!!.checked)
        assertTrue(snap.element(4)!!.scrollable)
    }

    @Test
    fun unlabeledIconIsMarked() {
        val snap = settings()
        assertTrue(snap.element(3)!!.unlabeledIcon)
        assertFalse(snap.element(2)!!.unlabeledIcon)
        // A switch without a label has a role of its own; it is not an "unlabeled icon".
        assertFalse(snap.element(7)!!.unlabeledIcon)
        assertEquals(1, snap.unlabeledIconCount)
    }

    @Test
    fun invisibleAndDecorativeNodesAreDropped() {
        val labels = settings().elements.mapNotNull { it.label }
        assertFalse(labels.any { it.contains("Gizli eski") })
        assertFalse(settings().elements.any { it.role == ElementRole.IMAGE })
    }

    @Test
    fun ownOverlayWindowIsExcludedById() {
        val snap = settings(ownIds = setOf(99))
        assertFalse(snap.windows.any { it.id == 99 })
        assertFalse(snap.elements.any { it.label == "Dur" })
    }

    @Test
    fun ownOverlayWindowIsExcludedByTitleOrPackageWithoutId() {
        val snap = settings()
        assertFalse(snap.elements.any { it.label == "Dur" || it.windowId == 99 })
        val byPackageOnly = ScreenFixtures.load("settings.json").map { if (it.id == 99) it.copy(title = null) else it }
        assertFalse(compressor.compress(byPackageOnly, ScreenFixtures.filter()).elements.any { it.windowId == 99 })
    }

    @Test
    fun keyboardWindowIsNeverRead() {
        val snap = settings()
        assertFalse(snap.elements.any { it.label == "klavyeOnerisi" })
        assertEquals(listOf(7), snap.windows.map { it.id })
    }

    @Test
    fun passwordAndSensitiveTextNeverReachesTheModel() {
        val snap = login()
        val dump = snap.toString()
        for (secret in listOf("Sifre123!", "Gizli456", "4321", "4111111111111111")) {
            assertFalse("leaked $secret", dump.contains(secret))
        }
        val secrets = snap.elements.filter { it.secret }
        assertEquals(4, secrets.size)
        secrets.forEach {
            assertEquals(ElementRole.PASSWORD_FIELD, it.role)
            assertNull(it.label)
            assertEquals(TextKind.NONE, it.textKind)
            assertFalse(it.unlabeledIcon)
        }
    }

    @Test
    fun textKindHintsSeparateUiLabelsFromUserContent() {
        val login = login().elements
        assertEquals(TextKind.USER_INPUT, login.first { it.label == "ali@example.com" }.textKind)
        assertEquals(TextKind.UI_LABEL, login.first { it.label == "Adınız" }.textKind)
        assertEquals(TextKind.UI_LABEL, login.first { it.label == "Giriş yap" }.textKind)
        val settings = settings().elements
        assertEquals(TextKind.LIST_CONTENT, settings.first { it.id == 5 }.textKind)
        assertEquals(TextKind.OTHER_TEXT, settings.first { it.label == "Ayarlar" }.textKind)
        assertEquals(TextKind.UI_LABEL, settings.first { it.label == "Ara" }.textKind)
    }

    @Test
    fun childTextRepeatingTheControlLabelIsNotDuplicated() {
        val labels = login().elements.mapNotNull { it.label }
        assertEquals(1, labels.count { it == "Kayıt ol" })
        assertEquals(1, labels.count { it == "Giriş yap" })
    }

    @Test
    fun elementLimitTruncates() {
        val rows = (0 until 50).map { i ->
            RawNode(className = "android.widget.Button", text = "B$i", clickable = true, bounds = Bounds(0, i * 10, 100, i * 10 + 10))
        }
        val window = RawWindow(1, WindowKind.APPLICATION, packageName = "x", root = RawNode(bounds = Bounds(0, 0, 100, 500), children = rows))
        val snap = ScreenCompressor(ScreenLimits(maxElements = 10)).compress(listOf(window), ScreenFixtures.filter())
        assertEquals(10, snap.elements.size)
        assertTrue(snap.truncated)
        assertEquals((1..10).toList(), snap.elements.map { it.id })
    }

    @Test
    fun depthLimitTruncates() {
        var node = RawNode(className = "android.widget.Button", text = "Derin", clickable = true, bounds = Bounds(0, 0, 10, 10))
        repeat(10) { node = RawNode(bounds = Bounds(0, 0, 10, 10), children = listOf(node)) }
        val window = RawWindow(1, WindowKind.APPLICATION, packageName = "x", root = node)
        val snap = ScreenCompressor(ScreenLimits(maxDepth = 5)).compress(listOf(window), ScreenFixtures.filter())
        assertTrue(snap.elements.isEmpty())
        assertTrue(snap.truncated)
    }

    @Test
    fun longLabelsAreCut() {
        val long = "a".repeat(500)
        val window = RawWindow(
            1, WindowKind.APPLICATION, packageName = "x",
            root = RawNode(className = "android.widget.TextView", text = long, bounds = Bounds(0, 0, 10, 10)),
        )
        val label = ScreenCompressor(ScreenLimits(maxLabelChars = 20)).compress(listOf(window), ScreenFixtures.filter()).elements[0].label!!
        assertEquals(20, label.length)
        assertTrue(label.endsWith("…"))
    }
}
