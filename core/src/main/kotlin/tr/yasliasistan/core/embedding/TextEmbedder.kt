package tr.yasliasistan.core.embedding

import java.io.Closeable

/**
 * Turns a short (already masked) command into a vector for the task memory (TK-05, FG-44).
 * Runs only on the device: the text never leaves the phone. Implementations are blocking; call
 * them off the main thread. [close] frees the model (V-03: loaded lazily, released after use).
 */
interface TextEmbedder : Closeable {
    /** Length of every vector returned by [embed]. */
    val dimension: Int

    /** L2-normalized vector, so cosine similarity equals the dot product ([VectorMath.cosine]). */
    fun embed(text: String): FloatArray
}

/**
 * The neural network part of an embedder, behind an interface so tokenization and pooling stay
 * in pure Kotlin (unit-tested on the JVM) and the runtime (ONNX Runtime on Android) is swappable.
 */
interface TokenEncoder : Closeable {
    val dimension: Int

    /** Last hidden state for one sequence: [ids].size rows of [dimension] floats, row-major. */
    fun lastHiddenState(ids: LongArray): FloatArray
}
