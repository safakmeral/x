package tr.yasliasistan.speech.net

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** A plain HTTPS GET, behind an interface so download logic is testable without a network. */
fun interface HttpFetcher {
    /** GET [url], asking for bytes from [fromByte] on (Range) when it is above 0. */
    @Throws(IOException::class)
    fun get(url: String, fromByte: Long): HttpResponse
}

class HttpResponse(
    val code: Int,
    /** -1 when the server did not say. */
    val contentLength: Long,
    val body: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            onClose()
        }
    }
}

/** A plain HTTPS POST of a JSON body (G-34 cloud proxy), behind an interface for tests. */
fun interface HttpPoster {
    @Throws(IOException::class)
    fun postJson(url: String, bearer: String, body: ByteArray, connectTimeoutMs: Int, readTimeoutMs: Int): PostResponse
}

/** Status, body text (capped) and the Retry-After header in seconds, if any. */
class PostResponse(val code: Int, val body: String, val retryAfterSeconds: Long?) {
    override fun toString() = "PostResponse(code=$code, chars=${body.length})"
}

/** The address is not on [NetworkAllowlist]; nothing was sent. */
class BlockedAddressException : IOException("address not on the allowlist")

/**
 * THE ONLY CLASS IN THE APP THAT OPENS NETWORK CONNECTIONS (checked by NetworkAccessTest in :app).
 *
 * Every address, including each redirect hop, must pass [NetworkAllowlist] before a connection is
 * made. Requests carry no personal or device data: a fixed generic User-Agent (the Android default
 * names the phone model), no cookies, no cache, no query of our own. Downloads (GET) and the cloud
 * proxy request (POST, G-34: masked and audited text only) are done here; audio and recognized
 * text never pass through this class (NFG-48). A POST never follows a redirect.
 */
class HttpGateway(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpFetcher, HttpPoster {

    override fun get(url: String, fromByte: Long): HttpResponse {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            if (!NetworkAllowlist.isAllowed(current)) throw BlockedAddressException()
            val conn = URL(current).openConnection() as HttpsURLConnection
            conn.instanceFollowRedirects = false // each hop is checked against the allowlist
            conn.useCaches = false
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "identity") // byte ranges must match the file
            if (fromByte > 0) conn.setRequestProperty("Range", "bytes=$fromByte-")
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                conn.disconnect()
                throw e
            }
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                current = URL(URL(current), location ?: throw IOException("redirect without location")).toString()
                return@repeat
            }
            val body = if (code in 200..299) conn.inputStream else conn.errorStream ?: ByteArrayInputStream(ByteArray(0))
            return HttpResponse(code, conn.contentLengthLong, body) { conn.disconnect() }
        }
        throw IOException("too many redirects")
    }

    override fun postJson(url: String, bearer: String, body: ByteArray, connectTimeoutMs: Int, readTimeoutMs: Int): PostResponse {
        if (!NetworkAllowlist.isAllowed(url)) throw BlockedAddressException()
        val conn = URL(url).openConnection() as HttpsURLConnection
        try {
            conn.instanceFollowRedirects = false // a redirect is an error, never followed
            conn.useCaches = false
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.setRequestProperty("User-Agent", POST_USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $bearer")
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { readCapped(it) }.orEmpty()
            val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
            return PostResponse(code, text, retryAfter)
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(input: InputStream): String {
        // InputStream.readNBytes needs API 33; minSdk is 29.
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < MAX_POST_RESPONSE_BYTES) {
            val n = input.read(buffer, 0, minOf(buffer.size, MAX_POST_RESPONSE_BYTES - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_POST_RESPONSE_BYTES = 64 * 1024

        /** Generic on purpose, like [USER_AGENT]: no app version, Android version or phone model. */
        const val POST_USER_AGENT = "assistant-client"

        /** Generic on purpose: no app version, Android version or phone model. */
        const val USER_AGENT = "model-download"
    }
}
