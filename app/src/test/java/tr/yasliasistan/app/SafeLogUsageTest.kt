package tr.yasliasistan.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UD-1 K-9 (KVKK): `SafeLog.event` also reaches logcat in release builds, so its message must never
 * carry user content. This source scan rejects event calls that interpolate transcripts, typed text,
 * spoken names or utterances. User content belongs in `SafeLog.content`, which R8 strips from release.
 */
class SafeLogUsageTest {

    private val sourceRoots = listOf("src/main", "../core/src/main", "../speech/src/main")

    // Interpolated identifiers that hold user content in this code base; only their length may be logged.
    private val contentPattern = Regex(
        """\$\{?(?:\w+\.)*(text|transcript|spokenName|utterance|readback|announcement|label|contact)\b(?!\.(length|size))""",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun eventLogsCarryNoUserContent() {
        val files = sourceRoots.map(::File).filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertTrue("source files found", files.isNotEmpty())
        val offending = files.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> "SafeLog.event(" in line && contentPattern.containsMatchIn(line) }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList<String>(), offending)
    }
}
