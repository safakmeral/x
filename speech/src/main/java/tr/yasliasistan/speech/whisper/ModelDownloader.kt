package tr.yasliasistan.speech.whisper

import tr.yasliasistan.speech.net.HttpFetcher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** How a model download ended. */
enum class DownloadResult {
    DONE,

    /** No connection, server error or the connection broke; the part kept so far is resumed next time. */
    NETWORK,

    /** The file did not match the official checksum; it was deleted. */
    CHECKSUM,

    /** Not enough free storage. */
    NO_SPACE,

    CANCELLED,
}

/**
 * Downloads a Whisper model into the app-private model folder (V-21, G-15). Resumable: bytes are
 * written to "<name>.part" and the next attempt continues with an HTTP Range request. The file is
 * installed (renamed) only after its size and SHA-256 match [WhisperModelSpec]. Blocking; call it
 * off the main thread. Pure JVM (network behind [HttpFetcher]).
 */
class ModelDownloader(private val http: HttpFetcher, private val dir: File) {

    fun isInstalled(spec: WhisperModelSpec): Boolean = File(dir, spec.fileName).let { it.isFile && it.length() == spec.bytes }

    /** Bytes already downloaded from an earlier, interrupted attempt. */
    fun partialBytes(spec: WhisperModelSpec): Long = partFile(spec).let { if (it.isFile) it.length() else 0L }

    /**
     * [onProgress] gets (downloaded, total) bytes now and then; [cancelled] is polled while copying.
     */
    fun download(
        spec: WhisperModelSpec,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelled: () -> Boolean = { false },
    ): DownloadResult {
        if (isInstalled(spec)) return DownloadResult.DONE
        if (!WhisperModelStore.isSafeFileName(spec.fileName)) return DownloadResult.CHECKSUM
        dir.mkdirs()
        val part = partFile(spec)
        var have = partialBytes(spec)
        if (have > spec.bytes) {
            part.delete()
            have = 0
        }
        if (have < spec.bytes) {
            if (dir.usableSpace < spec.bytes - have + SPACE_MARGIN_BYTES) return DownloadResult.NO_SPACE
            val fetched = fetch(spec, part, have, onProgress, cancelled)
            if (fetched != null) return fetched
        }
        if (part.length() != spec.bytes) {
            if (part.length() > spec.bytes) part.delete()
            return DownloadResult.NETWORK
        }
        if (!sha256(part).equals(spec.sha256, ignoreCase = true)) {
            part.delete()
            return DownloadResult.CHECKSUM
        }
        val target = File(dir, spec.fileName)
        target.delete()
        return if (part.renameTo(target)) DownloadResult.DONE else DownloadResult.NETWORK
    }

    /** Copies the response into [part]; returns a final result, or null when the copy ran to the end. */
    private fun fetch(
        spec: WhisperModelSpec,
        part: File,
        start: Long,
        onProgress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
    ): DownloadResult? = try {
        http.get(spec.url, start).use { response ->
            val append = when (response.code) {
                HTTP_PARTIAL -> start > 0
                HTTP_OK -> false // the server ignored the range: start over
                HTTP_RANGE_NOT_SATISFIABLE -> {
                    part.delete()
                    return DownloadResult.NETWORK
                }
                else -> return DownloadResult.NETWORK
            }
            var have = if (append) start else 0L
            onProgress(have, spec.bytes)
            var lastReport = have
            FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    if (cancelled()) return DownloadResult.CANCELLED
                    val n = response.body.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    have += n
                    if (have > spec.bytes) break // more than the official size: rejected below
                    if (have - lastReport >= PROGRESS_STEP_BYTES) {
                        lastReport = have
                        onProgress(have, spec.bytes)
                    }
                }
            }
            onProgress(have, spec.bytes)
            null
        }
    } catch (e: IOException) {
        DownloadResult.NETWORK // what was written stays in the .part file and is resumed next time
    }

    private fun partFile(spec: WhisperModelSpec) = File(dir, spec.fileName + PART_SUFFIX)

    companion object {
        const val PART_SUFFIX = ".part"
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 1_000_000L
        private const val SPACE_MARGIN_BYTES = 50_000_000L

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
