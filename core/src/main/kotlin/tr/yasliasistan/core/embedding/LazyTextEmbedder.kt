package tr.yasliasistan.core.embedding

/**
 * Loads the real embedder on first use and frees it on [release] (plan section 1: heavy models are
 * loaded lazily and released when the session ends, 4 GB RAM). Thread-safe; [create] runs at most
 * once per load. After [release] the next [embed] loads the model again.
 */
class LazyTextEmbedder(
    override val dimension: Int,
    private val create: () -> TextEmbedder,
) : TextEmbedder {

    private var delegate: TextEmbedder? = null

    val isLoaded: Boolean @Synchronized get() = delegate != null

    @Synchronized
    override fun embed(text: String): FloatArray {
        val embedder = delegate ?: create().also { delegate = it }
        return embedder.embed(text)
    }

    /** Frees the model; safe to call when nothing is loaded. */
    @Synchronized
    fun release() {
        delegate?.close()
        delegate = null
    }

    override fun close() = release()
}
