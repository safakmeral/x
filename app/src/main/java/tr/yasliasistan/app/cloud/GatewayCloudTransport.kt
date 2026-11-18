package tr.yasliasistan.app.cloud

import tr.yasliasistan.core.Defaults
import tr.yasliasistan.core.cloud.CloudTransport
import tr.yasliasistan.core.cloud.TransportResult
import tr.yasliasistan.speech.net.BlockedAddressException
import tr.yasliasistan.speech.net.HttpPoster
import java.io.IOException
import java.io.InterruptedIOException

/**
 * The cloud client's transport over `HttpGateway` (allowlist + HTTPS only, NetworkAccessTest).
 * One attempt with short timeouts (Defaults); every exception becomes a safe result.
 */
class GatewayCloudTransport(
    private val poster: HttpPoster,
    private val connectTimeoutMs: Int = Defaults.CLOUD_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = Defaults.CLOUD_READ_TIMEOUT_MS,
) : CloudTransport {

    override fun post(url: String, bearer: String, jsonBody: String): TransportResult = try {
        val r = poster.postJson(url, bearer, jsonBody.toByteArray(Charsets.UTF_8), connectTimeoutMs, readTimeoutMs)
        TransportResult.Response(r.code, r.body, r.retryAfterSeconds)
    } catch (e: BlockedAddressException) {
        TransportResult.Blocked
    } catch (e: InterruptedIOException) {
        TransportResult.Timeout // SocketTimeoutException is an InterruptedIOException
    } catch (e: IOException) {
        TransportResult.NetworkError
    } catch (e: RuntimeException) {
        TransportResult.NetworkError
    }
}
