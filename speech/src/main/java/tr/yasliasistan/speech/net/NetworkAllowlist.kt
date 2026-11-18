package tr.yasliasistan.speech.net

import tr.yasliasistan.core.cloud.ProxyAddress
import java.net.URI

/**
 * The only addresses the app may contact (G-15, KVKK): the official model downloads on Hugging
 * Face and its download servers (hf.co), and (G-34) the one cloud proxy host the student entered,
 * which must be a Cloudflare `workers.dev` host ([ProxyAddress.isAllowedHost]); no free domain.
 * HTTPS only. Audio and recognized text never go to any of these (NFG-48); the proxy receives only
 * masked, audited text (G-31/G-32).
 */
object NetworkAllowlist {

    private val exactHosts = setOf("huggingface.co")
    private val hostSuffixes = listOf(".huggingface.co", ".hf.co")

    @Volatile
    private var proxyHost: String? = null

    /**
     * Sets (or with null removes) the proxy host from the cloud settings. Returns false and keeps
     * the proxy closed when [host] is not a `workers.dev` host.
     */
    fun setProxyHost(host: String?): Boolean {
        val normalized = host?.lowercase()
        if (normalized != null && !ProxyAddress.isAllowedHost(normalized)) {
            proxyHost = null
            return false
        }
        proxyHost = normalized
        return true
    }

    fun isAllowed(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return host in exactHosts || hostSuffixes.any { host.endsWith(it) } || host == proxyHost
    }
}
