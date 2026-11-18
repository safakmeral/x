package tr.yasliasistan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the MERGED manifest (app + all libraries) for the variant under test.
 * NFG-67: no SMS or notification access. NFG-68: no unneeded permission.
 * NFG-43: app-private storage only, no backups, no cleartext traffic.
 * Adding a permission means adding it to [allowedPermissions] on purpose, with its reason.
 */
class ManifestPermissionTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** Permissions the app may request today. Extended by later tasks, one by one. */
    private val allowedPermissions = setOf(
        // Declared and used internally by androidx.core for non-exported dynamic receivers.
        "tr.yasliasistan.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        // G-07: voice commands, recognized on the device only; mic opens only after a bubble tap.
        "android.permission.RECORD_AUDIO",
        // G-36 (V-14): optional; names only, in memory, asked when a command needs a person.
        "android.permission.READ_CONTACTS",
        // G-15 (V-21): one-time Whisper model download after consent; HttpGateway + NetworkAllowlist only.
        "android.permission.INTERNET",
        // G-15: Wi-Fi or mobile data? Mobile data use is said before the model download.
        "android.permission.ACCESS_NETWORK_STATE",
    )

    /** Must never appear, whatever later tasks add. */
    private val forbiddenPermissions = setOf(
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
    )

    /**
     * UD-1 K-1, changed on purpose in G-15: INTERNET exists only for the Whisper model download.
     * No other network-related permission may come in; WHERE the app connects is checked by
     * NetworkAccessTest (one gateway class + host allowlist) and cleartext stays off (below).
     */
    @Test
    fun networkPermissionsAreOnlyThoseOfTheModelDownload() {
        val networkish = requestedPermissions().filter {
            it.contains("NETWORK") || it.contains("INTERNET") || it.contains("WIFI")
        }.toSet()
        assertEquals(
            setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            networkish,
        )
    }

    private val manifest: Element by lazy {
        val path = System.getProperty("mergedManifest")
            ?: error("mergedManifest system property missing; run through Gradle")
        val file = File(path)
        assertTrue("Merged manifest not found: $path", file.isFile)
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun requestedPermissions(): Set<String> =
        listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            manifest.elementsByTag(tag).map { it.getAttributeNS(androidNs, "name") }
        }.toSet()

    private fun Element.elementsByTag(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun application(): Element = manifest.elementsByTag("application").single()

    @Test
    fun onlyAllowedPermissionsAreRequested() {
        val unexpected = requestedPermissions() - allowedPermissions
        assertEquals("Unexpected permissions in merged manifest", emptySet<String>(), unexpected)
    }

    @Test
    fun forbiddenPermissionsAreAbsent() {
        val found = requestedPermissions().intersect(forbiddenPermissions)
        assertEquals("Forbidden permissions found", emptySet<String>(), found)
    }

    @Test
    fun noNotificationListenerOrSmsComponents() {
        val bindPermissions = listOf("service", "receiver").flatMap { tag ->
            manifest.elementsByTag(tag).map { it.getAttributeNS(androidNs, "permission") }
        }
        assertTrue(
            "Notification listener service must not exist",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" !in bindPermissions,
        )
        assertTrue(
            "SMS receivers must not exist",
            "android.permission.BROADCAST_SMS" !in bindPermissions,
        )
    }

    @Test
    fun backupAndCleartextAreDisabled() {
        val app = application()
        assertEquals("false", app.getAttributeNS(androidNs, "allowBackup"))
        assertEquals("false", app.getAttributeNS(androidNs, "usesCleartextTraffic"))
    }

    @Test
    fun accessibilityServiceIsGuardedAndNotExported() {
        val service = manifest.elementsByTag("service").single {
            it.getAttributeNS(androidNs, "name").endsWith(".AssistantAccessibilityService")
        }
        assertEquals(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            service.getAttributeNS(androidNs, "permission"),
        )
        assertEquals("false", service.getAttributeNS(androidNs, "exported"))
    }

    /** G-18: the adb CSV export of metrics exists only in debug builds. */
    @Test
    fun metricsExportIsDebugOnly() {
        val path = System.getProperty("mergedManifest") ?: return
        val hasExport = manifest.elementsByTag("activity").any {
            it.getAttributeNS(androidNs, "name").endsWith(".MetricsExportActivity")
        }
        if (path.contains("release", ignoreCase = true)) {
            assertTrue("metrics export must not ship in release", !hasExport)
        }
    }

    @Test
    fun minSdkIs29() {
        val usesSdk = manifest.elementsByTag("uses-sdk").single()
        assertEquals("29", usesSdk.getAttributeNS(androidNs, "minSdkVersion"))
    }
}
