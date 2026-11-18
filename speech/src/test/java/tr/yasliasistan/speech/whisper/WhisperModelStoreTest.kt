package tr.yasliasistan.speech.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class WhisperModelStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun model(dir: File, name: String, bytes: Long = WhisperModelStore.MIN_MODEL_BYTES): File {
        dir.mkdirs()
        return File(dir, name).also { RandomAccessFile(it, "rw").use { f -> f.setLength(bytes) } }
    }

    @Test
    fun internalFolderWinsOverExternal() {
        val internal = tmp.newFolder("internal")
        val external = tmp.newFolder("external")
        model(external, WhisperModels.TINY)
        val inInternal = model(internal, WhisperModels.TINY)
        assertEquals(inInternal, WhisperModelStore(listOf(internal, external)).find(WhisperModels.TINY))
    }

    @Test
    fun adbPushedModelInExternalFolderIsFound() {
        val external = tmp.newFolder("external")
        val pushed = model(external, WhisperModels.BASE)
        val store = WhisperModelStore(listOf(File(tmp.root, "missing"), external))
        assertEquals(pushed, store.find(WhisperModels.BASE))
        assertNull(store.find(WhisperModels.SMALL))
    }

    @Test
    fun brokenTinyFileIsIgnored() {
        val dir = tmp.newFolder("m")
        model(dir, WhisperModels.TINY, bytes = 10)
        assertNull(WhisperModelStore(listOf(dir)).find(WhisperModels.TINY))
    }

    @Test
    fun pathTraversalNamesAreRejected() {
        val dir = tmp.newFolder("m")
        model(tmp.root, "secret.bin")
        val store = WhisperModelStore(listOf(dir))
        assertNull(store.find("../secret.bin"))
        assertFalse(WhisperModelStore.isSafeFileName("a/b.bin"))
        assertFalse(WhisperModelStore.isSafeFileName("..bin"))
        assertFalse(WhisperModelStore.isSafeFileName(""))
        assertTrue(WhisperModelStore.isSafeFileName(WhisperModels.SMALL_Q5_1))
    }

    @Test
    fun installedListsModelsOnceSorted() {
        val a = tmp.newFolder("a")
        val b = tmp.newFolder("b")
        model(a, WhisperModels.TINY)
        model(b, WhisperModels.TINY)
        model(b, WhisperModels.BASE)
        File(b, "notes.txt").writeText("x")
        assertEquals(listOf(WhisperModels.BASE, WhisperModels.TINY), WhisperModelStore(listOf(a, b)).installed())
    }
}
