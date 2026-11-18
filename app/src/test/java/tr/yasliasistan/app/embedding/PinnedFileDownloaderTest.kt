package tr.yasliasistan.app.embedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tr.yasliasistan.speech.net.HttpFetcher
import tr.yasliasistan.speech.net.HttpResponse
import tr.yasliasistan.speech.net.NetworkAllowlist
import tr.yasliasistan.speech.whisper.DownloadResult
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest

class PinnedFileDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val content = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val file = PinnedFile("model.onnx", "https://huggingface.co/x/resolve/abc/model.onnx", content.size.toLong(), sha)

    private class FakeHttp(private val body: (Long) -> HttpResponse) : HttpFetcher {
        val ranges = mutableListOf<Long>()
        override fun get(url: String, fromByte: Long): HttpResponse {
            ranges += fromByte
            return body(fromByte)
        }
    }

    private fun serving(bytes: ByteArray) = FakeHttp { from ->
        val code = if (from > 0) 206 else 200
        HttpResponse(code, bytes.size - from, ByteArrayInputStream(bytes, from.toInt(), bytes.size - from.toInt()))
    }

    @Test
    fun downloadsAndInstallsWhenTheHashMatches() {
        val dir = tmp.newFolder()
        val d = PinnedFileDownloader(serving(content), dir)
        assertEquals(DownloadResult.DONE, d.download(file))
        assertTrue(d.isInstalled(file))
        assertFalse(dir.resolve("model.onnx.part").exists())
    }

    @Test
    fun wrongContentIsDeleted() {
        val dir = tmp.newFolder()
        val bad = content.copyOf().also { it[10] = 99 }
        val d = PinnedFileDownloader(serving(bad), dir)
        assertEquals(DownloadResult.CHECKSUM, d.download(file))
        assertFalse(d.isInstalled(file))
        assertFalse(dir.resolve("model.onnx.part").exists())
    }

    @Test
    fun brokenDownloadResumesWithRange() {
        val dir = tmp.newFolder()
        val failing = FakeHttp { throw IOException("offline") }
        dir.resolve("model.onnx.part").writeBytes(content.copyOf(50_000))
        assertEquals(DownloadResult.NETWORK, PinnedFileDownloader(failing, dir).download(file))
        val http = serving(content)
        assertEquals(DownloadResult.DONE, PinnedFileDownloader(http, dir).download(file))
        assertEquals(listOf(50_000L), http.ranges)
    }

    @Test
    fun stopsAtTheFirstFailure() {
        val dir = tmp.newFolder()
        val other = file.copy(fileName = "vocab.model")
        val http = FakeHttp { HttpResponse(500, 0, ByteArrayInputStream(ByteArray(0))) }
        assertEquals(DownloadResult.NETWORK, PinnedFileDownloader(http, dir).downloadAll(listOf(file, other)))
        assertEquals(1, http.ranges.size)
    }

    @Test
    fun officialFilesArePinnedAndOnTheAllowlist() {
        for (f in EmbeddingModelFiles.ALL) {
            assertTrue(f.url, NetworkAllowlist.isAllowed(f.url))
            assertTrue(EmbeddingModelFiles.REVISION in f.url)
            assertEquals(64, f.sha256.length)
        }
        assertEquals(118, EmbeddingModelFiles.MODEL.megabytes)
    }
}
