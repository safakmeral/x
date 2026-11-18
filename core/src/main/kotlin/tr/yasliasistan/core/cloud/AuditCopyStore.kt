package tr.yasliasistan.core.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tr.yasliasistan.core.Defaults
import java.io.File

/** One stored copy: when it was sent, which prompt version, and the masked text that left the phone. */
data class AuditCopy(val atEpochMillis: Long, val promptVersion: Int, val maskedUserText: String) {
    override fun toString() = "AuditCopy(at=$atEpochMillis, chars=${maskedUserText.length})"
}

/** Where audit copies go; [NONE] in tests that do not care. */
interface AuditCopySink {
    fun record(copy: AuditCopy)

    companion object {
        val NONE = object : AuditCopySink {
            override fun record(copy: AuditCopy) = Unit
        }
    }
}

/**
 * Local audit copies of outgoing cloud requests (FG-91, V-15: 7 days). Only the **masked** user
 * message is stored (the same bytes that were sent; the system prompt is fixed and identified by
 * its version). One small file per request in an app-private, no-backup directory; files older
 * than the retention are deleted on every write and on [prune]. Nothing is logged.
 *
 * The directory must be app-private (`Context.noBackupFilesDir`), so it never leaves the device.
 */
class AuditCopyStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionMs: Long = Defaults.CLOUD_AUDIT_RETENTION_DAYS * DAY_MS,
) : AuditCopySink {

    private val lock = Any()

    override fun record(copy: AuditCopy) {
        synchronized(lock) {
            prune()
            if (!dir.isDirectory && !dir.mkdirs()) return
            val json = buildJsonObject {
                put("t", copy.atEpochMillis)
                put("sablon", copy.promptVersion)
                put("kullanici", copy.maskedUserText)
            }
            var n = 0
            while (File(dir, nameOf(copy.atEpochMillis, n)).exists()) n++
            File(dir, nameOf(copy.atEpochMillis, n)).writeText(json.toString(), Charsets.UTF_8)
        }
    }

    /** All copies still kept, oldest first (for "Verilerim", G-50). */
    fun list(): List<AuditCopy> = synchronized(lock) {
        prune()
        files().sortedWith(compareBy({ timeOf(it) }, { it.name })).mapNotNull(::read)
    }

    /** Deletes copies older than the retention period. */
    fun prune() = synchronized(lock) {
        val oldest = clock() - retentionMs
        files().filter { (timeOf(it) ?: Long.MIN_VALUE) < oldest }.forEach { it.delete() }
    }

    /** "Tüm verilerimi sil" (G-50). */
    fun clear() = synchronized(lock) { files().forEach { it.delete() } }

    private fun files(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }?.toList().orEmpty()

    private fun timeOf(file: File): Long? = file.name.removeSuffix(SUFFIX).substringBefore('-').toLongOrNull()

    private fun nameOf(time: Long, n: Int) = "$time-${n.toString().padStart(4, '0')}$SUFFIX"

    private fun read(file: File): AuditCopy? = try {
        val o = Json.parseToJsonElement(file.readText(Charsets.UTF_8)) as JsonObject
        AuditCopy(
            (o["t"] as JsonPrimitive).content.toLong(),
            (o["sablon"] as JsonPrimitive).content.toInt(),
            (o["kullanici"] as JsonPrimitive).content,
        )
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val SUFFIX = ".json"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
