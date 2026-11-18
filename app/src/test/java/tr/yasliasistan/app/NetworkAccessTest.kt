package tr.yasliasistan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * G-15 (replaces UD-1's "no INTERNET permission" check): the app may reach the network only
 * through speech/net/HttpGateway.kt, which allows only NetworkAllowlist hosts (unit-tested in
 * :speech). Any other network API in the app's own code (main sources of :app, :speech, :core)
 * fails this test, so a new connection cannot slip past the allowlist unnoticed.
 */
class NetworkAccessTest {

    private val networkApis = Regex(
        """\bopenConnection\b|\bHttps?URLConnection\b|java\.net\.(URL|Socket|ServerSocket|DatagramSocket)\b|""" +
            """\bokhttp3\b|\bDownloadManager\b|\bWebView\b|\bopenStream\s*\(|\bio\.ktor\b|\bRetrofit\b""",
    )

    private val gateway = "speech/src/main/java/tr/yasliasistan/speech/net/HttpGateway.kt"

    private val projectRoot: File by lazy {
        // Gradle runs unit tests from the module directory (app/).
        val root = File("..").canonicalFile
        assertTrue("Run through Gradle from the :app module", File(root, "settings.gradle.kts").isFile)
        root
    }

    private fun sources(): List<File> = listOf("app", "speech", "core").flatMap { module ->
        File(projectRoot, "$module/src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .toList()
    }

    private fun relative(file: File) = file.relativeTo(projectRoot).invariantSeparatorsPath

    @Test
    fun onlyTheGatewayUsesNetworkApis() {
        val users = sources().filter { networkApis.containsMatchIn(it.readText()) }.map(::relative)
        assertEquals(listOf(gateway), users)
    }

    @Test
    fun gatewayChecksTheAllowlistBeforeConnecting() {
        val text = File(projectRoot, gateway).readText()
        val check = text.indexOf("NetworkAllowlist.isAllowed(current)")
        val connect = text.indexOf("openConnection()")
        assertTrue("allowlist check missing", check >= 0)
        assertTrue("allowlist must be checked before connecting", check in 0 until connect)
        assertTrue("redirects must not be followed blindly", "instanceFollowRedirects = false" in text)
        // G-34: every connection (GET downloads and the proxy POST) is preceded by an allowlist check.
        val connects = Regex("""openConnection\(\)""").findAll(text).count()
        val checks = Regex("""NetworkAllowlist\.isAllowed\(""").findAll(text).count()
        assertTrue("each openConnection needs its own allowlist check", connects in 1..checks)
        val post = text.substringAfter("override fun postJson(")
        assertTrue("POST checks the allowlist first", post.indexOf("NetworkAllowlist.isAllowed(url)") in 0 until post.indexOf("openConnection()"))
        assertTrue("POST never follows redirects", "instanceFollowRedirects = false" in post)
    }

    @Test
    fun cleartextIsOffInTheNetworkSecurityConfig() {
        val config = File(projectRoot, "app/src/main/res/xml/network_security_config.xml").readText()
        assertTrue("""cleartextTrafficPermitted="false"""" in config)
        assertTrue("user-installed CAs must not be trusted", """src="user"""" !in config)
    }
}
