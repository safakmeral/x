package tr.yasliasistan.app.embedding

import android.content.Context
import android.os.SystemClock
import tr.yasliasistan.core.embedding.E5TextEmbedder
import tr.yasliasistan.core.embedding.LazyTextEmbedder
import tr.yasliasistan.core.embedding.TextEmbedder
import tr.yasliasistan.core.embedding.XlmRobertaTokenizer
import tr.yasliasistan.core.log.SafeLog
import java.io.File

/**
 * Entry point for the task memory (G-44): the on-device [TextEmbedder], or null while the model is
 * not downloaded (then the decision engine skips the recipe layer). The model loads on the first
 * embed and should be [LazyTextEmbedder.release]d when the assistant session ends (4 GB RAM).
 * Only timings are logged, never text.
 */
object OnDeviceEmbedder {

    fun create(context: Context): LazyTextEmbedder? = fromDir(EmbeddingModelFiles.dir(context))

    fun fromDir(dir: File, threads: Int = OnnxTokenEncoder.DEFAULT_THREADS): LazyTextEmbedder? {
        if (!EmbeddingModelFiles.isInstalled(dir)) return null
        return LazyTextEmbedder(E5TextEmbedder.DIMENSION) { load(dir, threads) }
    }

    fun load(dir: File, threads: Int): TextEmbedder {
        val start = SystemClock.elapsedRealtime()
        val tokenizer = XlmRobertaTokenizer.load(File(dir, EmbeddingModelFiles.TOKENIZER.fileName))
        val encoder = OnnxTokenEncoder(File(dir, EmbeddingModelFiles.MODEL.fileName), threads)
        SafeLog.event(TAG, "embedder loaded in ${SystemClock.elapsedRealtime() - start} ms")
        return E5TextEmbedder(tokenizer, encoder)
    }

    private const val TAG = "Embedding"
}
