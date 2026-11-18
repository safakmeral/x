package tr.yasliasistan.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the privacy notice texts (G-30): every NFG-81 item and every recipient is present,
 * and any text change forces a conscious version bump (users must be asked again).
 */
class PrivacyTextTest {

    private val strings: Map<String, String> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val noticeTexts get() = strings.filterKeys { it.startsWith("privacy_") || it.startsWith("a11y_disclosure_") }

    private fun details(): String = strings.filterKeys { it.startsWith("privacy_d_") }.values.joinToString("\n")

    @Test
    fun detailsCoverEveryRequiredItem() {
        val text = details()
        listOf(
            "Veri sorumlusu", "%1\$s", "%2\$s", // controller identity (filled from config)
            "gizlenmiş komut yazısı", // only masked text leaves the device
            "Google", "Gemini", "Groq", "Cloudflare", "ABD", "Avrupa Ekonomik Alanı", // recipients, countries
            "ürünlerini geliştirmek", "okuyabilir", // free tier: product use and human review
            "açık rıza", "madde 5 ve 9", "tek başına yeterli sayılmıyor", // legal basis, honestly
            "30 gün", "7 gün", "180 gün", // retention
            "madde 11", "geri alabilirsiniz", // rights and withdrawal
            "kusursuz değildir", // residual masking risk
        ).forEach { assertTrue("missing: $it", text.contains(it)) }
    }

    @Test
    fun accessibilityExplanationIsPartOfTheNotice() {
        listOf("a11y_disclosure_why", "a11y_disclosure_reads", "a11y_disclosure_not", "a11y_disclosure_data")
            .forEach { assertTrue(it, strings.containsKey(it)) }
    }

    @Test
    fun acceptAndRefuseAreBothPresentAndNeutral() {
        assertEquals("Kabul ediyorum", strings["privacy_accept"])
        assertEquals("Kabul etmiyorum", strings["privacy_refuse"])
    }

    @Test
    fun noBlanketPromises() {
        val all = noticeTexts.values.joinToString("\n").lowercase()
        listOf("asla", "hiçbir veri", "%100", "tamamen güvenli").forEach {
            assertTrue("overclaim: $it", !all.contains(it))
        }
    }

    /**
     * If this fails you changed the notice text. Bump PrivacyNotice.TEXT_VERSION and TEXT_DATE when
     * the meaning changed (everyone is asked again), then paste the new fingerprint here.
     */
    @Test
    fun textChangesRequireVersionBump() {
        val canonical = noticeTexts.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val fingerprint = digest.joinToString("") { "%02x".format(it) }
        assertEquals("v1:$FINGERPRINT_V1", "v${PrivacyNotice.TEXT_VERSION}:$fingerprint")
    }

    private companion object {
        const val FINGERPRINT_V1 = "f08ab69b6030ecc919c6546509bc1f6b0b6f1b10daf8fef0341346beab32f18b"
    }
}
