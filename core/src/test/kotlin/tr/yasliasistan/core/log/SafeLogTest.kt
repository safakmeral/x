package tr.yasliasistan.core.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLogTest {

    private val lines = mutableListOf<String>()
    private val sink = SafeLog.Sink { level, tag, message -> lines += "$level/$tag: $message" }

    @After
    fun reset() = SafeLog.install(sink = null, allowContent = false)

    @Test
    fun contentIsNotWrittenNorEvaluatedWhenDisabled() {
        SafeLog.install(sink, allowContent = false)
        var evaluated = false

        SafeLog.content("Test") { evaluated = true; "Ayşe Test'i ara" }

        assertFalse(evaluated)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun contentIsWrittenWhenEnabled() {
        SafeLog.install(sink, allowContent = true)

        SafeLog.content("Test") { "transcript" }

        assertEquals(listOf("DEBUG/Test: transcript"), lines)
    }

    @Test
    fun eventIsAlwaysWritten() {
        SafeLog.install(sink, allowContent = false)

        SafeLog.event("Test", "state=LISTENING")

        assertEquals(listOf("INFO/Test: state=LISTENING"), lines)
    }

    @Test
    fun noSinkMeansNoCrash() {
        SafeLog.install(sink = null, allowContent = true)

        SafeLog.event("Test", "x")
        SafeLog.content("Test") { "y" }

        assertTrue(lines.isEmpty())
    }
}
