package tr.yasliasistan.core.cloud

/**
 * One HTTPS POST to the proxy. Implemented in `:app` on top of `speech/.../net/HttpGateway`
 * (the only class allowed to open connections, checked by NetworkAccessTest); tests fake it.
 * Must not retry and must not follow redirects.
 */
fun interface CloudTransport {
    fun post(url: String, bearer: String, jsonBody: String): TransportResult
}

sealed interface TransportResult {
    /** The server answered (any status). [retryAfterSeconds] from the Retry-After header, if any. */
    data class Response(val code: Int, val body: String, val retryAfterSeconds: Long? = null) : TransportResult {
        override fun toString() = "Response(code=$code, chars=${body.length})"
    }

    /** Connect or read timeout. */
    data object Timeout : TransportResult

    /** No network, DNS or TLS failure, connection reset. */
    data object NetworkError : TransportResult

    /** The address is not on the network allowlist; nothing was sent. */
    data object Blocked : TransportResult
}

/** Shown on the bubble while a request is on its way (NFG-58). Called from the worker thread. */
fun interface CloudIndicator {
    fun busy(on: Boolean)

    companion object {
        val NONE = CloudIndicator { }
    }
}
