package tr.yasliasistan.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * G-19 (NFG-29, NFG-53): the service may read window content, with the narrowest settings.
 * G-20 (FG-20): and perform gestures; nothing else.
 */
class AccessibilityServiceConfigTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val config: Element by lazy {
        // Unit tests run with the module directory as working directory.
        val file = File("src/main/res/xml/accessibility_service_config.xml")
        assertTrue("Config not found: ${file.absolutePath}", file.isFile)
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun attr(name: String) = config.getAttributeNS(androidNs, name)

    @Test
    fun canRetrieveWindowContent() {
        assertEquals("true", attr("canRetrieveWindowContent"))
    }

    /** G-20 (FG-20): "Sen yap" needs gestures as the fallback for taps and scrolls. */
    @Test
    fun canPerformGestures() {
        assertEquals("true", attr("canPerformGestures"))
    }

    @Test
    fun subscribesOnlyToWindowChangeEvents() {
        assertEquals(
            setOf("typeWindowStateChanged", "typeWindowContentChanged", "typeWindowsChanged"),
            attr("accessibilityEventTypes").split('|').toSet(),
        )
    }

    @Test
    fun onlyInteractiveWindowsFlag() {
        assertEquals("flagRetrieveInteractiveWindows", attr("accessibilityFlags"))
    }

    @Test
    fun requestsNoExtraCapabilities() {
        for (name in listOf(
            "canRequestFilterKeyEvents",
            "canRequestTouchExplorationMode",
            "canTakeScreenshot",
            "canRequestFingerprintGestures",
            "packageNames",
        )) {
            assertEquals(name, "", attr(name))
        }
    }
}
