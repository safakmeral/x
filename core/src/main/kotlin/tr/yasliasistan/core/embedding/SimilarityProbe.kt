package tr.yasliasistan.core.embedding

/**
 * G-43 check of whether a similarity threshold separates "same intent" from "different intent"
 * command pairs (file format: label<TAB>a<TAB>b, label AYNI or FARKLI, '#' comments).
 * Used by the device benchmark; contains only fixed test sentences, never user text.
 */
object SimilarityProbe {

    data class CommandPair(val same: Boolean, val a: String, val b: String)

    data class Scored(val pair: CommandPair, val cosine: Float)

    data class Summary(
        val pairs: Int,
        val sameMin: Float,
        val sameMean: Float,
        val differentMax: Float,
        val differentMean: Float,
        /** Share of pairs classified correctly by "cosine >= threshold means same". */
        val accuracyAtDefault: Double,
        val bestThreshold: Float,
        val bestAccuracy: Double,
        /** Lowest threshold at which no different-intent pair is accepted, and how many same pairs then pass. */
        val safeThreshold: Float,
        val sameAcceptedAtSafe: Int,
    )

    fun parse(tsv: String): List<CommandPair> = tsv.lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val cols = line.split('\t')
            require(cols.size == 3) { "expected 3 columns" }
            val same = when (cols[0]) {
                "AYNI" -> true
                "FARKLI" -> false
                else -> throw IllegalArgumentException("label must be AYNI or FARKLI")
            }
            CommandPair(same, cols[1], cols[2])
        }
        .toList()

    fun score(pairs: List<CommandPair>, embed: (String) -> FloatArray): List<Scored> {
        val cache = HashMap<String, FloatArray>()
        return pairs.map { p ->
            val a = cache.getOrPut(p.a) { embed(p.a) }
            val b = cache.getOrPut(p.b) { embed(p.b) }
            Scored(p, VectorMath.cosine(a, b))
        }
    }

    fun summarize(scored: List<Scored>, defaultThreshold: Float): Summary {
        require(scored.any { it.pair.same } && scored.any { !it.pair.same }) { "need both labels" }
        val same = scored.filter { it.pair.same }.map { it.cosine }
        val different = scored.filter { !it.pair.same }.map { it.cosine }
        fun accuracy(t: Float) = scored.count { (it.cosine >= t) == it.pair.same }.toDouble() / scored.size
        val candidates = scored.map { it.cosine } + defaultThreshold
        val best = candidates.maxWith(compareBy<Float> { accuracy(it) }.thenBy { it })
        val safe = Math.nextUp(different.max())
        return Summary(
            pairs = scored.size,
            sameMin = same.min(),
            sameMean = same.average().toFloat(),
            differentMax = different.max(),
            differentMean = different.average().toFloat(),
            accuracyAtDefault = accuracy(defaultThreshold),
            bestThreshold = best,
            bestAccuracy = accuracy(best),
            safeThreshold = safe,
            sameAcceptedAtSafe = same.count { it >= safe },
        )
    }
}
