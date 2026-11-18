package tr.yasliasistan.core.embedding

/**
 * multilingual-e5-small sentence embedding (V-03): "query: " + text, tokenize, run the encoder,
 * mean-pool the last hidden state, L2-normalize. Both the stored recipe intent and the new command
 * use the "query: " prefix, as the model card advises for symmetric similarity.
 */
class E5TextEmbedder(
    private val tokenizer: XlmRobertaTokenizer,
    private val encoder: TokenEncoder,
    private val maxTokens: Int = MAX_TOKENS,
) : TextEmbedder {

    override val dimension: Int get() = encoder.dimension

    override fun embed(text: String): FloatArray {
        val ids = tokenizer.encode(QUERY_PREFIX + text, maxTokens)
        val hidden = encoder.lastHiddenState(ids)
        return VectorMath.l2Normalize(VectorMath.meanPool(hidden, ids.size, encoder.dimension))
    }

    override fun close() = encoder.close()

    companion object {
        const val QUERY_PREFIX = "query: "
        const val DIMENSION = 384

        /** Voice commands are short; the cap bounds latency and memory on low-end phones (NFG-72). */
        const val MAX_TOKENS = 64
    }
}
