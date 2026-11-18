package tr.yasliasistan.core.contacts

import tr.yasliasistan.core.masking.ContactName
import tr.yasliasistan.core.text.TurkishText

/**
 * Finds the person a command talks about (PLAN G-36, FG-101). Built once per address-book
 * snapshot and kept only in memory; a lookup is a few hash lookups per spoken word.
 *
 * Rules, in order:
 * 1. Every spoken word is compared with every word of every saved name, after Turkish
 *    normalization, case-ending removal ("Ayşe'yi", "ayşeye", "Zeynebi") and kinship mapping
 *    ("kızıma" = "Kızım", "Mehmet amcama" = "Mehmet Amca").
 * 2. The people with the most matching name words win ("Ayşe Yılmaz" beats "Ayşe Demir" when both
 *    words are said). On a tie, people matched on their first name beat those matched only on a
 *    later word ("Ayşe teyze" -> Ayşe Yılmaz, not Fatma Teyze).
 * 3. One person left -> [ContactMatch.One], never a "which one?" question. Two or three ->
 *    [ContactMatch.Several] (read out by name). More -> [ContactMatch.TooMany].
 * 4. Only when nothing matched, a one-letter speech-recognition slip is forgiven for longer
 *    names ("Hüsein" -> Hüseyin); such a result is marked inexact.
 */
class ContactMatcher private constructor(
    private val entries: List<Entry>,
    private val wordIndex: Map<String, IntArray>,
    private val wordsByLength: Map<Int, List<String>>,
) {
    private class Entry(val contact: ContactName, val words: List<String>, val nameKey: String)

    val size: Int get() = entries.size

    fun match(spoken: String): ContactMatch {
        val tokens = TurkishText.tokens(spoken)
        val strong = tokens.filterNot(ContactWords::isWeak)
        val weak = tokens.filter(ContactWords::isWeak)
        val masks = HashMap<Int, Long>()
        val strongWords = strong.map { ContactWords.candidates(it, wordIndex::containsKey) }
        strongWords.forEach { words -> words.forEach { addWord(masks, it, onlyExisting = false) } }
        var approximate = false
        if (masks.isEmpty()) {
            strong.forEach { token -> fuzzyWords(token).forEach { addWord(masks, it, onlyExisting = false) } }
            approximate = masks.isNotEmpty()
        }
        if (masks.isEmpty()) return ContactMatch.None
        weak.forEach { token ->
            ContactWords.candidates(token, wordIndex::containsKey).forEach { addWord(masks, it, onlyExisting = true) }
        }
        val best = pickBest(masks)
        val people = best.distinctBy { entries[it].nameKey }
        return when {
            people.size == 1 -> {
                val entry = entries[people.single()]
                val allSaid = strongWords.all { words -> words.any { it in entry.words } }
                ContactMatch.One(entry.contact, exact = !approximate && allSaid)
            }
            people.size <= MAX_SPOKEN_OPTIONS -> ContactMatch.Several(people.map { entries[it].contact })
            else -> ContactMatch.TooMany(people.size)
        }
    }

    private fun addWord(masks: HashMap<Int, Long>, word: String, onlyExisting: Boolean) {
        val ids = wordIndex[word] ?: return
        for (id in ids) {
            val current = masks[id]
            if (onlyExisting && current == null) continue
            val words = entries[id].words
            var bits = current ?: 0L
            for (k in words.indices) if (k < 64 && words[k] == word) bits = bits or (1L shl k)
            masks[id] = bits
        }
    }

    private fun pickBest(masks: Map<Int, Long>): List<Int> {
        val top = masks.values.maxOf(java.lang.Long::bitCount)
        val tied = masks.filterValues { java.lang.Long.bitCount(it) == top }
        val byFirstName = tied.filterValues { it and 1L != 0L }
        return (byFirstName.ifEmpty { tied }).keys.sorted()
    }

    /** Saved-name words within one edit of a stem of [token]; only for words of 5+ letters. */
    private fun fuzzyWords(token: String): Set<String> {
        val found = LinkedHashSet<String>()
        for (stem in ContactWords.stems(token)) {
            if (stem.length < FUZZY_MIN_LENGTH) continue
            for (len in stem.length - 1..stem.length + 1) {
                wordsByLength[len]?.forEach { if (withinOneEdit(stem, it)) found += it }
            }
        }
        return found
    }

    companion object {
        /** FG-101 / student decision: at most three names are read out as options. */
        const val MAX_SPOKEN_OPTIONS = 3
        private const val FUZZY_MIN_LENGTH = 5

        fun build(contacts: List<ContactName>): ContactMatcher {
            val entries = contacts.mapNotNull { c ->
                val words = ContactWords.nameWords(c.displayName)
                if (words.isEmpty()) null else Entry(c, words, words.joinToString(" "))
            }
            val index = HashMap<String, MutableList<Int>>()
            entries.forEachIndexed { id, e -> e.words.distinct().forEach { index.getOrPut(it) { ArrayList(2) } += id } }
            val wordIndex = index.mapValues { it.value.toIntArray() }
            val byLength = wordIndex.keys.filter { it.length >= FUZZY_MIN_LENGTH }.groupBy { it.length }
            return ContactMatcher(entries, wordIndex, byLength)
        }

        val EMPTY: ContactMatcher = build(emptyList())

        /** Levenshtein distance <= 1, without building a matrix. */
        internal fun withinOneEdit(a: String, b: String): Boolean {
            if (a == b) return true
            val diff = a.length - b.length
            if (diff > 1 || diff < -1) return false
            val (s, l) = if (a.length <= b.length) a to b else b to a
            var i = 0
            var j = 0
            var edits = 0
            while (i < s.length && j < l.length) {
                if (s[i] == l[j]) { i++; j++; continue }
                if (++edits > 1) return false
                if (s.length == l.length) i++
                j++
            }
            return edits + (l.length - j) + (s.length - i) <= 1
        }
    }
}

/** Result of [ContactMatcher.match]. toString shows ids only: names are personal data. */
sealed interface ContactMatch {
    /** Exactly one person: act without asking which (FG-101). [exact] false = a word was unmatched or fuzzy. */
    data class One(val contact: ContactName, val exact: Boolean) : ContactMatch

    /** Two or three people; read their names and let the user pick. */
    data class Several(val options: List<ContactName>) : ContactMatch

    /** More people than can be read out; ask once for more of the name. */
    data class TooMany(val count: Int) : ContactMatch

    data object None : ContactMatch
}
