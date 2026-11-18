package tr.yasliasistan.core.masking

import tr.yasliasistan.core.masking.MaskCorpus.strMap

/**
 * Leak finder shared by the command (G-31) and screen (G-32) leak tests: no secret value of the
 * corpus, nor a word or digit group of it, may appear in an output unless the corpus itself expects
 * that word to stay (e.g. "Merhaba").
 */
internal object CorpusLeaks {

    /** Every value the corpus treats as personal: all tables, contact names and phone numbers. */
    val secrets: Set<String> by lazy {
        val fromTables = MaskCorpus.examples.flatMap { e ->
            (e.strMap("esleme").values + (e["beklenen"] as Map<String, Any?>?).orEmpty().let {
                @Suppress("UNCHECKED_CAST") (it["esleme"] as Map<String, String>?).orEmpty().values
            })
        }
        (fromTables + MaskCorpus.contacts.map { it.displayName } + MaskCorpus.contactPhones).toSet()
    }

    private fun words(s: String): List<String> =
        TurkishFold.fold(s.replace(MaskCorpus.placeholderRegex, " ")).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** Pieces that would identify a value: whole value, words of 4+ letters, digit groups of 3+. */
    private fun pieces(value: String): Set<String> {
        val folded = words(value)
        return buildSet {
            add(folded.joinToString(" "))
            folded.filter { w -> (w.all { it.isLetter() } && w.length >= 4) || (w.all { it.isDigit() } && w.length >= 3) }
                .forEach { add(it) }
            val digits = value.filter { it.isDigit() }
            if (digits.length >= 4) add("#$digits")
        }
    }

    fun find(output: String, allowed: String, secretValues: Set<String> = secrets): List<String> {
        val outWords = words(output)
        val outText = outWords.joinToString(" ")
        val outDigits = "#" + output.replace(MaskCorpus.placeholderRegex, " ").filter { it.isDigit() }
        val allowedWords = words(allowed).toSet()
        val found = ArrayList<String>()
        for (secret in secretValues) for (piece in pieces(secret)) {
            val hit = when {
                piece.startsWith("#") -> outDigits.contains(piece.drop(1))
                piece.contains(' ') -> " $outText ".contains(" $piece ")
                else -> piece in outWords
            }
            val allowedPiece = piece.split(' ').all { it in allowedWords }
            if (hit && !allowedPiece) found += piece
        }
        return found
    }
}
