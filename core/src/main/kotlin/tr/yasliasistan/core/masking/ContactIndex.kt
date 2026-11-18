package tr.yasliasistan.core.masking

/** One address-book entry as seen by the masker; only id and display name are needed. */
data class ContactName(val id: String, val displayName: String) {
    override fun toString() = "ContactName(id=$id)"
}

/**
 * Source of address-book names. The Android implementation (READ_CONTACTS, PLAN G-36) lives in
 * `:app`; without the permission it returns an empty list and name matching is skipped (§5.4).
 */
fun interface ContactNameSource {
    fun contactNames(): List<ContactName>
}

/**
 * Folded index of contact names (§5.4). Build it once and rebuild it when the address book
 * changes; matching is a hash lookup per word plus a short suffix check.
 */
class ContactIndex private constructor(
    private val byFirstName: Map<String, List<Entry>>,
    private val maxWords: Int,
) {
    internal class Entry(val contact: ContactName, val words: List<String>)

    internal data class Match(
        val firstToken: Int,
        val lastToken: Int,
        val stemEnd: Int,
        val fusedSuffix: String?,
        val contacts: List<ContactName>,
        val matchedFirstNameOnly: Boolean,
    )

    val isEmpty: Boolean get() = byFirstName.isEmpty()

    /** Softened first name ("zeyneb") -> first names; built once per dictionary. */
    @Volatile
    private var softenedCache: Pair<MaskingDictionary, Map<String, List<String>>>? = null

    private fun bySoftenedFirstName(ctx: MaskContext): Map<String, List<String>> {
        softenedCache?.takeIf { it.first === ctx.dict }?.let { return it.second }
        val map = HashMap<String, MutableList<String>>()
        for (first in byFirstName.keys) ctx.dict.softened(first)?.let { map.getOrPut(it) { ArrayList() } += first }
        softenedCache = ctx.dict to map
        return map
    }

    /** Longest contact name starting at token [i], or null (§5.4: full name beats first name). */
    internal fun matchAt(ctx: MaskContext, i: Int): Match? {
        val toks = ctx.tokens
        val firstFold = toks[i].fold
        if (firstFold.isEmpty()) return null
        for (n in minOf(maxWords, toks.size - i) downTo 2) {
            val candidates = byFirstName[firstFold].orEmpty().filter { it.words.size == n }
            val hits = candidates.mapNotNull { e ->
                val inner = (0 until n - 1).all { k ->
                    val t = toks[i + k]
                    t.fold == e.words[k] && t.baseEnd == t.coreEnd && !t.hasTrailingPunctuation
                }
                if (!inner) null else wordWithSuffix(ctx, toks[i + n - 1], e.words.last())?.let { e to it }
            }
            if (hits.isNotEmpty()) {
                val (stemEnd, fused) = hits.first().second
                return Match(i, i + n - 1, stemEnd, fused, hits.map { it.first.contact }, false)
            }
        }
        // First name alone: try every split of the word into stem + ending. Candidates are the
        // names (or softened names, "zeyneb") the word starts with: one hash lookup per prefix.
        val t = toks[i]
        val joined = t.foldJoined
        var best: Match? = null
        val candidates = LinkedHashSet<String>()
        for (len in 1..joined.length) {
            val prefix = joined.substring(0, len)
            if (prefix in byFirstName) candidates += prefix
            bySoftenedFirstName(ctx)[prefix]?.let { candidates += it }
        }
        for (first in candidates) {
            val entries = byFirstName.getValue(first)
            val hit = wordWithSuffix(ctx, t, first) ?: continue
            if (best == null || hit.first > best.stemEnd) {
                best = Match(i, i, hit.first, hit.second, entries.map { it.contact }.distinct(), true)
            }
        }
        return best
    }

    /**
     * Matches [word] (folded) against a token: exact, word + ending, or a softened stem before a
     * vowel ending ("zeyneb|i", "mehmed|e"). Returns the stem end and a fused ending, if any.
     */
    private fun wordWithSuffix(ctx: MaskContext, t: Token, word: String): Pair<Int, String?>? {
        val joined = t.foldJoined
        val baseLen = t.baseEnd - t.coreStart
        val stems = listOfNotNull(word, ctx.dict.softened(word))
        for (stem in stems) {
            if (!joined.startsWith(stem)) continue
            val rest = joined.substring(stem.length)
            if (stem != word && (rest.isEmpty() || rest[0] !in VOWELS)) continue
            if (!ctx.dict.isSuffixOrEmpty(rest)) continue
            if (stem.length > baseLen) continue
            if (stem.length == baseLen) return t.baseEnd to null // ending, if any, follows an apostrophe
            val stemEnd = t.coreStart + stem.length
            if (t.baseEnd < t.coreEnd) continue // "Ali'ye" style ending mixed with fused letters
            return stemEnd to ctx.text.substring(stemEnd, t.coreEnd)
        }
        return null
    }

    companion object {
        private const val VOWELS = "aeiou"

        fun build(contacts: List<ContactName>): ContactIndex {
            val map = HashMap<String, MutableList<Entry>>()
            var maxWords = 1
            for (c in contacts) {
                val words = c.displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .map { TurkishFold.fold(it) }
                if (words.isEmpty()) continue
                maxWords = maxOf(maxWords, words.size)
                map.getOrPut(words.first()) { ArrayList() } += Entry(c, words)
            }
            return ContactIndex(map, maxWords)
        }

        fun from(source: ContactNameSource) = build(source.contactNames())

        val EMPTY = build(emptyList())
    }
}
