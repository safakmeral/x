package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tr.yasliasistan.speech.net.HttpFetcher
import tr.yasliasistan.speech.net.HttpResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ModelDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val data = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val spec = WhisperModelSpec("ggml-test.bin", data.size.toLong(), sha256(data))

    /** Fake server: honours Range unless told not to; can break the stream after some bytes. */
    private inner class FakeServer(
        var honourRange: Boolean = true,
        var breakAfter: Int = -1,
        var payload: ByteArray = data,
        var code: Int? = null,
    ) : HttpFetcher {
        val requests = mutableListOf<Long>()

        override fun get(url: String, fromByte: Long): HttpResponse {
            requests += fromByte
            code?.let { return HttpResponse(it, 0, ByteArrayInputStream(ByteArray(0))) }
            val partial = honourRange && fromByte > 0
            val bytes = if (partial) payload.copyOfRange(fromByte.toInt(), payload.size) else payload
            val stream: InputStream = if (breakAfter < 0) ByteArrayInputStream(bytes) else BreakingStream(bytes, breakAfter)
            return HttpResponse(if (partial) 206 else 200, bytes.size.toLong(), stream)
        }
    }

    private class BreakingStream(private val bytes: ByteArray, private val limit: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int {
            if (pos >= limit) throw IOException("connection lost")
            return if (pos < bytes.size) bytes[pos++].toInt() and 0xff else -1
        }
    }

    private fun dir(): File = tmp.root
    private fun installed() = File(dir(), spec.fileName)
    private fun part() = File(dir(), spec.fileName + ModelDownloader.PART_SUFFIX)

    @Test
    fun downloadsVerifiesAndInstalls() {
        val server = FakeServer()
        val downloader = ModelDownloader(server, dir())
        val progress = mutableListOf<Long>()
        assertEquals(DownloadResult.DONE, downloader.download(spec, onProgress = { done, _ -> progress += done }))
        assertArrayEquals(data, installed().readBytes())
        assertFalse(part().exists())
        assertTrue(downloader.isInstalled(spec))
        assertEquals(data.size.toLong(), progress.last())
        assertEquals(listOf(0L), server.requests)
    }

    @Test
    fun alreadyInstalledModelIsNotDownloadedAgain() {
        installed().writeBytes(data)
        val server = FakeServer()
        assertEquals(DownloadResult.DONE, ModelDownloader(server, dir()).download(spec))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun brokenConnectionKeepsThePartAndTheNextAttemptResumes() {
        val server = FakeServer(breakAfter = 100_000)
        val downloader = ModelDownloader(server, dir())
        assertEquals(DownloadResult.NETWORK, downloader.download(spec))
        assertEquals(100_000L, downloader.partialBytes(spec))
        assertFalse(installed().exists())

        server.breakAfter = -1
        assertEquals(DownloadResult.DONE, downloader.download(spec))
        assertEquals(listOf(0L, 100_000L), server.requests)
        assertArrayEquals(data, installed().readBytes())
    }

    @Test
    fun serverIgnoringRangeRestartsFromZero() {
        part().writeBytes(data.copyOfRange(0, 50_000))
        val server = FakeServer(honourRange = false)
        assertEquals(DownloadResult.DONE, ModelDownloader(server, dir()).download(spec))
        assertArrayEquals(data, installed().readBytes())
    }

    @Test
    fun wrongContentIsRejectedAndDeleted() {
        val tampered = data.copyOf().also { it[10] = (it[10] + 1).toByte() }
        val server = FakeServer(payload = tampered)
        val downloader = ModelDownloader(server, dir())
        assertEquals(DownloadResult.CHECKSUM, downloader.download(spec))
        assertFalse(installed().exists())
        assertFalse(part().exists())
    }

    @Test
    fun longerFileThanOfficialIsRejected() {
        val server = FakeServer(payload = data + ByteArray(10))
        assertEquals(DownloadResult.NETWORK, ModelDownloader(server, dir()).download(spec))
        assertFalse(installed().exists())
        assertFalse(part().exists())
    }

    @Test
    fun serverErrorsAreNetworkFailures() {
        assertEquals(DownloadResult.NETWORK, ModelDownloader(FakeServer(code = 404), dir()).download(spec))
        assertEquals(DownloadResult.NETWORK, ModelDownloader(FakeServer(code = 503), dir()).download(spec))
    }

    @Test
    fun unsatisfiableRangeDropsThePart() {
        part().writeBytes(data.copyOfRange(0, 1_000))
        assertEquals(DownloadResult.NETWORK, ModelDownloader(FakeServer(code = 416), dir()).download(spec))
        assertFalse(part().exists())
    }

    @Test
    fun cancelKeepsThePartForLater() {
        var reads = 0
        val downloader = ModelDownloader(FakeServer(), dir())
        assertEquals(DownloadResult.CANCELLED, downloader.download(spec, cancelled = { reads++ >= 2 }))
        assertTrue(downloader.partialBytes(spec) > 0)
        assertFalse(installed().exists())
    }

    @Test
    fun notEnoughSpaceIsReportedBeforeDownloading() {
        val huge = spec.copy(bytes = Long.MAX_VALUE / 2)
        val server = FakeServer()
        assertEquals(DownloadResult.NO_SPACE, ModelDownloader(server, dir()).download(huge))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun catalogUsesPinnedOfficialHttpsAddresses() {
        listOf(WhisperModelCatalog.TINY, WhisperModelCatalog.BASE).forEach {
            assertTrue(it.url.startsWith("https://huggingface.co/ggerganov/whisper.cpp/resolve/${WhisperModelCatalog.REVISION}/"))
            assertTrue(it.url.endsWith(it.fileName))
            assertEquals(64, it.sha256.length)
        }
        assertEquals(148, WhisperModelCatalog.BASE.megabytes)
        assertEquals(78, WhisperModelCatalog.TINY.megabytes)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
