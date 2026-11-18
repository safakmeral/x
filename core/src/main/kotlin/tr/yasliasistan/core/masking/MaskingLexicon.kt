package tr.yasliasistan.core.masking

/** Numeric settings of docs/MASKELEME.md (corpus "ayarlar"). */
data class MaskingSettings(
    val intentModeWordThreshold: Int,
    val minDigitRun: Int,
    val numberWordThreshold: Int,
    val contextMaxWords: Int,
    val addressMaxLeftWords: Int,
    val emailMaxLeftWords: Int,
    val dictionaryPrefixMinLength: Int,
    val secretMinDigits: Int,
)

/**
 * Word lists of docs/MASKELEME.md (corpus "sozlukler"). Raw lists are kept as written in the
 * specification; detectors use the folded lookup structures built once in [MaskingDictionary].
 */
data class MaskingLexicon(
    val commandWords: List<String>,
    val kinship: List<String>,
    val honorifics: List<String>,
    /** Case class ("'i", "'e", "'de", "'den", "'in", "'le", "" = dropped) -> surface endings. */
    val suffixes: Map<String, List<String>>,
    val softening: Map<Char, Char>,
    /** Case class -> words that follow a person's name ("ara", "mesaj", "numarası"). */
    val personSlotTriggers: Map<String, List<String>>,
    val dictationBefore: List<String>,
    val dictationAfter: List<String>,
    val dictationColonWords: List<String>,
    val dictationVerbs: List<String>,
    val addressAnchors: List<String>,
    val addressNumberMarkers: List<String>,
    val numberWords: Map<String, Long>,
    val sensitiveRoots: List<String>,
    val sensitivePhrases: List<List<String>>,
    val secretPhrases: List<List<String>>,
    val secretWithDigits: List<String>,
    val codeWithDigits: List<String>,
    val codeExceptions: List<List<String>>,
    val statusWords: List<String>,
    val emailTlds: List<String>,
    val settings: MaskingSettings,
) {
    companion object {
        /** The lexicon fixed by docs/MASKELEME.md. */
        fun default(): MaskingLexicon = DefaultMaskingLexicon
    }
}

/** Case classes in a fixed lookup order (first match wins when an ending is ambiguous). */
internal val CASE_CLASSES = listOf("'i", "'e", "'de", "'den", "'in", "'le", "")

/** Folded, hash-based view of a [MaskingLexicon]; built once per lexicon. */
internal class MaskingDictionary(val lexicon: MaskingLexicon) {
    val settings = lexicon.settings

    /**
     * "Known word" lookups (§3.4) in one spelling form. A word written with Turkish letters is
     * compared in lower case only; the folded form is used only for words typed without Turkish
     * letters ("sukruyu"). Otherwise "kısıldı" would fold to "kisildi" and look like "kişi...".
     */
    private inner class KnownWords(private val form: (String) -> String, softening: Map<Char, Char>) {
        private val exact = lexicon.commandWords.mapTo(HashSet()) { form(it) }

        /** Entries usable as prefixes (>= 4 letters), plus softened forms ("poliklinik" -> "polikliniğ"). */
        private val prefixes: Set<String> = buildSet {
            lexicon.commandWords.filter { it.length >= settings.dictionaryPrefixMinLength }.forEach { word ->
                val w = form(word)
                add(w)
                softening[w.last()]?.let { add(w.dropLast(1) + it) }
            }
        }
        private val endings = CASE_CLASSES.flatMap { lexicon.suffixes[it].orEmpty() }.mapTo(HashSet()) { form(it) }
        private val kinship = lexicon.kinship.map { form(it) }
        val honorifics = lexicon.honorifics.map { form(it) }

        fun isStemWithSuffix(w: String, stem: String) =
            w.startsWith(stem) && (w.length == stem.length || w.substring(stem.length) in endings)

        fun isCommandWord(w: String, allowPrefix: Boolean): Boolean {
            if (w in exact) return true
            if (!allowPrefix) return false
            for (len in settings.dictionaryPrefixMinLength..w.length) {
                if (w.substring(0, len) in prefixes) return true
            }
            return false
        }

        fun isKinshipOrHonorific(w: String) = kinship.any { isStemWithSuffix(w, it) } || isHonorific(w)
        fun isHonorific(w: String) = honorifics.any { isStemWithSuffix(w, it) }
    }

    private val lowerWords = KnownWords(TurkishFold::lower, lexicon.softening)
    private val foldedWords = KnownWords(
        TurkishFold::fold,
        lexicon.softening.entries.associate { TurkishFold.foldChar(it.key) to TurkishFold.foldChar(it.value) },
    )

    private fun formsFor(t: Token) = if (t.lower == t.fold) foldedWords else lowerWords

    fun isCommandWord(t: Token, allowPrefix: Boolean = true) =
        formsFor(t).let { it.isCommandWord(if (it === lowerWords) t.lower else t.fold, allowPrefix) }
    fun isKinshipOrHonorific(t: Token) = formsFor(t).let { it.isKinshipOrHonorific(if (it === lowerWords) t.lower else t.fold) }
    fun isHonorific(t: Token) = formsFor(t).let { it.isHonorific(if (it === lowerWords) t.lower else t.fold) }

    private val suffixClassByEnding: Map<String, String> = buildMap {
        for (cls in CASE_CLASSES) {
            lexicon.suffixes[cls].orEmpty().forEach { putIfAbsent(TurkishFold.fold(it), cls) }
        }
    }
    val suffixEndings: Set<String> get() = suffixClassByEnding.keys

    val personTriggers: Map<String, String> = buildMap {
        lexicon.personSlotTriggers.forEach { (cls, words) -> words.forEach { put(TurkishFold.fold(it), cls) } }
    }

    val dictationBefore = lexicon.dictationBefore.mapTo(HashSet()) { TurkishFold.fold(it) }
    val dictationAfter: List<List<String>> = lexicon.dictationAfter.map { p -> p.split(' ').map { TurkishFold.fold(it) } }
    val dictationColonWords = lexicon.dictationColonWords.mapTo(HashSet()) { TurkishFold.fold(it) }
    private val dictationVerbs = lexicon.dictationVerbs.map { TurkishFold.fold(it) }

    val addressAnchors = lexicon.addressAnchors.mapTo(HashSet()) { TurkishFold.fold(it) }
    val addressNumberMarkers = lexicon.addressNumberMarkers.mapTo(HashSet()) { TurkishFold.fold(it) }

    val numberWords: Map<String, Long> = lexicon.numberWords.mapKeys { TurkishFold.fold(it.key) }

    private val sensitiveRoots = lexicon.sensitiveRoots.map { TurkishFold.fold(it) }
    val sensitivePhrases = lexicon.sensitivePhrases.map { p -> p.map { TurkishFold.fold(it) } }
    val secretPhrases = lexicon.secretPhrases.map { p -> p.map { TurkishFold.fold(it) } }
    val secretWithDigits = lexicon.secretWithDigits.map { TurkishFold.fold(it) }
    val codeWithDigits = lexicon.codeWithDigits.map { TurkishFold.fold(it) }
    val codeExceptions = lexicon.codeExceptions.map { p -> p.map { TurkishFold.fold(it) } }
    val emailTlds = lexicon.emailTlds.mapTo(HashSet()) { TurkishFold.fold(it) }

    fun softened(folded: String): String? {
        if (folded.isEmpty()) return null
        val last = folded.last()
        val soft = lexicon.softening.entries.firstOrNull { TurkishFold.foldChar(it.key) == last } ?: return null
        val replacement = TurkishFold.foldChar(soft.value)
        return if (replacement == last) null else folded.dropLast(1) + replacement
    }

    /** Case class of a surface ending ("yi" -> "'i"); null when the ending is unknown. */
    fun caseClassOf(ending: String): String? = suffixClassByEnding[TurkishFold.fold(ending)]

    fun isSuffixOrEmpty(foldedEnding: String) = foldedEnding.isEmpty() || foldedEnding in suffixClassByEnding

    fun isDictationVerb(folded: String) = dictationVerbs.any { verb ->
        folded == verb || (verb.length >= 3 && folded.startsWith(verb))
    }

    /** Sensitive single-word term: prefix match for roots of >= 4 letters, exact otherwise. */
    fun isSensitiveWord(folded: String) = sensitiveRoots.any { root ->
        if (root.length >= 4) folded.startsWith(root) else folded == root
    }
}
