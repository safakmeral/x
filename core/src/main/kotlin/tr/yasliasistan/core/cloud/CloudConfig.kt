package tr.yasliasistan.core.cloud

import java.net.URI

/**
 * Address of the proxy (G-33), entered by the student at the very end of setup
 * (docs/KURULUM-SON.md §2.10). Only `https://<name>.workers.dev` is accepted: the free Cloudflare
 * address the deploy step prints. No other domain can be entered, so a typo or a malicious value
 * can never open the network allowlist to an arbitrary host.
 */
class ProxyAddress private constructor(val host: String) {

    /** The single endpoint of the contract (proxy/README.md). */
    val endpoint: String get() = "https://$host$ENDPOINT_PATH"

    override fun toString() = "ProxyAddress($host)"
    override fun equals(other: Any?) = other is ProxyAddress && other.host == host
    override fun hashCode() = host.hashCode()

    companion object {
        const val ENDPOINT_PATH = "/v1/generate"
        private const val ALLOWED_SUFFIX = ".workers.dev"
        private val LABEL = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")

        /** Parses what the user typed; null when it is not a plain `https` + `.workers.dev` address. */
        fun parse(input: String): ProxyAddress? {
            val text = input.trim().removeSuffix("/")
            val uri = try {
                URI(text)
            } catch (e: Exception) {
                return null
            }
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            if (uri.port != -1 && uri.port != 443) return null
            if (!uri.path.isNullOrEmpty()) return null
            val host = uri.host?.lowercase() ?: return null
            return if (isAllowedHost(host)) ProxyAddress(host) else null
        }

        /** True for `<label>.<label>...workers.dev` with valid DNS labels; used by the network allowlist. */
        fun isAllowedHost(host: String): Boolean {
            if (!host.endsWith(ALLOWED_SUFFIX) || host.length > 253) return false
            val labels = host.removeSuffix(ALLOWED_SUFFIX).split('.')
            return labels.isNotEmpty() && labels.all { LABEL.matches(it) }
        }
    }
}

/**
 * The access code of the proxy (V-04: entered on the device, never built into the APK).
 * At least 12 letters/digits, as docs/KURULUM-SON.md §2.5 asks. Never printed or logged.
 */
class AccessCode private constructor(val value: String) {
    override fun toString() = "AccessCode(<hidden>)"
    override fun equals(other: Any?) = other is AccessCode && other.value == value
    override fun hashCode() = value.hashCode()

    companion object {
        const val MIN_LENGTH = 12
        private const val MAX_LENGTH = 128
        private val ALLOWED = Regex("[A-Za-z0-9]+")

        fun parse(input: String): AccessCode? {
            val text = input.trim()
            if (text.length !in MIN_LENGTH..MAX_LENGTH || !ALLOWED.matches(text)) return null
            return AccessCode(text)
        }
    }
}

/** Both values the cloud needs; without them the cloud is completely off (on-device only). */
data class CloudConfig(val address: ProxyAddress, val accessCode: AccessCode)

/** Where the client reads the current configuration; null = not entered yet. */
fun interface CloudConfigSource {
    fun current(): CloudConfig?
}
