package tr.yasliasistan.app.embedding

import tr.yasliasistan.speech.net.HttpFetcher
import tr.yasliasistan.speech.whisper.DownloadResult
import tr.yasliasistan.speech.whisper.ModelDownloader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads [PinnedFile]s into [dir] through the app's single network class ([HttpFetcher] =
 * HttpGateway, allowlist-checked). Same rules as the Whisper downloader (G-15): resumable
 * "<name>.part" with HTTP Range, installed only when size and SHA-256 match, deleted otherwise.
 * Only model files are downloaded; no user text is ever sent. Blocking; call off the main thread.
 */
class PinnedFileDownloader(private val http: HttpFetcher, private val dir: File) {

    fun isInstalled(file: PinnedFile): Boolean = File(dir, file.fileName).let { it.isFile && it.length() == file.bytes }

    /** Downloads every missing file in order; stops at the first failure. */
    fun downloadAll(files: List<PinnedFile>, cancelled: () -> Boolean = { false }): DownloadResult {
        for (f in files) {
            val result = download(f, cancelled)
            if (result != DownloadResult.DONE) return result
        }
        return DownloadResult.DONE
    }

    fun download(file: PinnedFile, cancelled: () -> Boolean = { false }): DownloadResult {
        if (isInstalled(file)) return DownloadResult.DONE
        if (!safeName.matches(file.fileName)) return DownloadResult.CHECKSUM
        dir.mkdirs()
        val part = File(dir, file.fileName + ModelDownloader.PART_SUFFIX)
        var have = if (part.isFile) part.length() else 0L
        if (have > file.bytes) {
            part.delete()
            have = 0
        }
        if (have < file.bytes) {
            if (dir.usableSpace < file.bytes - have + SPACE_MARGIN_BYTES) return DownloadResult.NO_SPACE
            fetch(file, part, have, cancelled)?.let { return it }
        }
        if (part.length() != file.bytes) {
            if (part.length() > file.bytes) part.delete()
            return DownloadResult.NETWORK
        }
        if (!ModelDownloader.sha256(part).equals(file.sha256, ignoreCase = true)) {
            part.delete()
            return DownloadResult.CHECKSUM
        }
        val target = File(dir, file.fileName)
        target.delete()
        return if (part.renameTo(target)) DownloadResult.DONE else DownloadResult.NETWORK
    }

    /** Copies the response into [part]; null when the copy ran to the end. */
    private fun fetch(file: PinnedFile, part: File, start: Long, cancelled: () -> Boolean): DownloadResult? = try {
        http.get(file.url, start).use { response ->
            val append = when (response.code) {
                206 -> start > 0
                200 -> false // range ignored: start over
                416 -> {
                    part.delete()
                    return DownloadResult.NETWORK
                }
                else -> return DownloadResult.NETWORK
            }
            var have = if (append) start else 0L
            FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (have <= file.bytes) {
                    if (cancelled()) return DownloadResult.CANCELLED
                    val n = response.body.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    have += n
                }
            }
            null
        }
    } catch (e: IOException) {
        DownloadResult.NETWORK
    }

    private companion object {
        const val SPACE_MARGIN_BYTES = 50_000_000L
        val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
    }
}
