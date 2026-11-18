package tr.yasliasistan.core.embedding

import kotlin.math.sqrt

/** Small vector helpers for embeddings (no external library). */
object VectorMath {

    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "dimension mismatch: ${a.size} vs ${b.size}" }
        var sum = 0.0
        for (i in a.indices) sum += a[i].toDouble() * b[i]
        return sum.toFloat()
    }

    /** Cosine similarity; for vectors from [TextEmbedder.embed] this is the dot product. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        val norms = norm(a) * norm(b)
        return if (norms == 0.0) 0f else (dot(a, b) / norms).toFloat()
    }

    fun norm(a: FloatArray): Double {
        var sum = 0.0
        for (x in a) sum += x.toDouble() * x
        return sqrt(sum)
    }

    /** Returns a new unit-length copy; an all-zero vector stays zero. */
    fun l2Normalize(a: FloatArray): FloatArray {
        val n = norm(a)
        if (n == 0.0) return a.copyOf()
        return FloatArray(a.size) { (a[it] / n).toFloat() }
    }

    /**
     * Average of the [tokens] rows of a row-major [hidden] matrix (mean pooling, as in the model's
     * sentence-transformers config). A single unpadded sequence has an all-ones attention mask.
     */
    fun meanPool(hidden: FloatArray, tokens: Int, dimension: Int): FloatArray {
        require(tokens > 0 && hidden.size == tokens * dimension) { "bad hidden state shape" }
        val sum = DoubleArray(dimension)
        for (t in 0 until tokens) {
            val base = t * dimension
            for (d in 0 until dimension) sum[d] += hidden[base + d].toDouble()
        }
        return FloatArray(dimension) { (sum[it] / tokens).toFloat() }
    }
}
