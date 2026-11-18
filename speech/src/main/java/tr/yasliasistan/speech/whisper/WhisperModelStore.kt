package tr.yasliasistan.speech.whisper

import android.content.Context
import java.io.File

/**
 * Finds Whisper model files (ggml format) in the app's private folders. The model is chosen at
 * run time by file name, so tiny/base/small (and quantized versions) can be compared (DEG-14).
 *
 * Search order:
 *  1. internal `files/whisper/` (where G-15 will place the shipped or downloaded model);
 *  2. app-specific external `Android/data/<package>/files/whisper/` (measurement models pushed
 *     with `adb push`, V-21). No storage permission is needed for either folder.
 * Nothing is downloaded here (G-15).
 */
class WhisperModelStore(private val dirs: List<File>) {

    /** The model file if it is installed, else null. Unsafe names (paths) are rejected. */
    fun find(fileName: String): File? {
        if (!isSafeFileName(fileName)) return null
        return dirs.asSequence()
            .map { File(it, fileName) }
            .firstOrNull { it.isFile && it.length() >= MIN_MODEL_BYTES }
    }

    /** File names of all installed models, without duplicates, sorted by name. */
    fun installed(): List<String> = dirs.asSequence()
        .flatMap { dir -> dir.listFiles()?.asSequence() ?: emptySequence() }
        .filter { it.isFile && it.name.endsWith(".bin") && isSafeFileName(it.name) && it.length() >= MIN_MODEL_BYTES }
        .map { it.name }
        .distinct()
        .sorted()
        .toList()

    companion object {
        const val FOLDER = "whisper"

        /** Smallest real ggml model (tiny, quantized) is ~30 MB; anything below 1 MB is a broken copy. */
        const val MIN_MODEL_BYTES = 1_000_000L

        private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")

        fun isSafeFileName(name: String): Boolean = safeName.matches(name) && ".." !in name

        fun forContext(context: Context): WhisperModelStore = WhisperModelStore(
            listOfNotNull(File(context.filesDir, FOLDER), context.getExternalFilesDir(FOLDER)),
        )
    }
}

/** Official whisper.cpp model file names (huggingface.co/ggerganov/whisper.cpp, MIT). */
object WhisperModels {
    const val TINY = "ggml-tiny.bin"
    const val BASE = "ggml-base.bin"
    const val SMALL = "ggml-small.bin"
    const val TINY_Q5_1 = "ggml-tiny-q5_1.bin"
    const val BASE_Q5_1 = "ggml-base-q5_1.bin"
    const val SMALL_Q5_1 = "ggml-small-q5_1.bin"
}
