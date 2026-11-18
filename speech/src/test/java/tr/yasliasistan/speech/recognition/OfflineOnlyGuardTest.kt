package tr.yasliasistan.speech.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * NFG-16/17/48: the online recognizer must never be created anywhere in :speech, and audio
 * must never be written to a file. A source scan keeps this true as the module grows.
 */
class OfflineOnlyGuardTest {

    private val sources: List<File> by lazy {
        val root = File("src/main")
        assertTrue("Run from the :speech module directory", root.isDirectory)
        root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList()
    }

    private fun filesContaining(regex: Regex): List<String> =
        sources.filter { regex.containsMatchIn(it.readText()) }.map { it.name }

    @Test
    fun onlineRecognizerIsNeverCreated() {
        assertEquals(emptyList<String>(), filesContaining(Regex("""\bcreateSpeechRecognizer\s*\(""")))
    }

    @Test
    fun audioIsNeverRecordedToFiles() {
        val fileOutputs = Regex("""EXTRA_AUDIO_SOURCE|EXTRA_SEGMENTED_SESSION|MediaRecorder|FileOutputStream|openFileOutput""")
        // G-15: ModelDownloader writes the downloaded Whisper MODEL file (never audio); it has no
        // access to the microphone or to audio buffers. Any other file writer here fails the test.
        val allowedModelWriters = listOf("ModelDownloader.kt")
        assertEquals(emptyList<String>(), filesContaining(fileOutputs) - allowedModelWriters)
    }
}
