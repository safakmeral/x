package tr.yasliasistan.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrRunConfigTest {

    @Test
    fun defaultsMeasureTinyOnTtsClips() {
        val c = AsrRunConfig.parse(emptyMap())
        assertEquals(listOf("ggml-tiny.bin"), c.engines)
        assertTrue(c.useTts)
        assertFalse(c.useFolder)
        assertEquals(40, c.limit)
        assertEquals(listOf(AsrRunConfig.WhisperKnobs(0, 0, 1)), c.whisperSettings())
        assertTrue(c.problems.isEmpty())
    }

    @Test
    fun parsesEnginesAndSettingSweeps() {
        val c = AsrRunConfig.parse(
            mapOf(
                "engines" to "system, tiny,base-q5_1,ggml-small.bin",
                "data" to "hepsi",
                "limit" to "5",
                "audioCtx" to "0,768",
                "threads" to "4",
                "beam" to "1,5",
                "conditions" to "temiz,sessiz",
                "run" to "deneme-1",
            ),
        )
        assertEquals(listOf("system", "ggml-tiny.bin", "ggml-base-q5_1.bin", "ggml-small.bin"), c.engines)
        assertTrue(c.useTts && c.useFolder)
        assertEquals(5, c.limit)
        assertEquals(4, c.whisperSettings().size)
        assertEquals(listOf("temiz", "sessiz"), c.conditions)
        assertEquals("deneme-1", c.run)
        assertTrue(c.problems.isEmpty())
    }

    @Test
    fun badValuesFallBackAndAreReported() {
        val c = AsrRunConfig.parse(
            mapOf("engines" to "../x", "limit" to "999", "beam" to "abc", "dataDir" to "../../sdcard", "data" to "bulut"),
        )
        assertEquals(listOf("ggml-tiny.bin"), c.engines)
        assertEquals(40, c.limit)
        assertEquals(listOf(1), c.beams)
        assertEquals("asr-veri", c.dataDir)
        assertEquals(5, c.problems.size)
    }

    @Test
    fun commandSetHasFortyUniqueMadeUpCommands() {
        val commands = AsrCommandSet.COMMANDS
        assertEquals(40, commands.size)
        assertEquals(40, commands.map { it.id }.toSet().size)
        assertEquals("k01", commands.first().id)
        assertEquals("k40", commands.last().id)
        // No digits: every number is spoken as words, so the TTS reads it the way a person would.
        assertTrue(commands.none { c -> c.text.any { it.isDigit() } })
    }
}
