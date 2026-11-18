package tr.yasliasistan.core.contacts

import tr.yasliasistan.core.text.TurkishText

/**
 * Word rules for finding a person in the address book (PLAN G-36, FG-101). All words are
 * normalized with [TurkishText] (lower case, ASCII-folded), so "AYŞE", "ayşe" and "ayse" are equal.
 */
internal object ContactWords {

    /** Kinship and address words, canonical form -> stems that take endings ("oğlum" = ogl+um). */
    private val kinship: Map<String, List<String>> = mapOf(
        "kiz" to listOf("kiz"),
        "ogul" to listOf("ogul", "ogl"),
        "anne" to listOf("anne", "anane", "ana"),
        "baba" to listOf("baba"),
        "anneanne" to listOf("anneanne"),
        "babaanne" to listOf("babaanne", "babanne"),
        "dede" to listOf("dede"),
        "nine" to listOf("nine"),
        "abla" to listOf("abla"),
        "abi" to listOf("abi", "agabey", "abey"),
        "kardes" to listOf("kardes"),
        "amca" to listOf("amca"),
        "dayi" to listOf("dayi"),
        "teyze" to listOf("teyze"),
        "hala" to listOf("hala"),
        "eniste" to listOf("eniste"),
        "yenge" to listOf("yenge"),
        "torun" to listOf("torun"),
        "gelin" to listOf("gelin"),
        "damat" to listOf("damat", "damad"),
        "kuzen" to listOf("kuzen"),
        "yegen" to listOf("yegen"),
        "es" to listOf("es"),
        "kari" to listOf("kari"),
        "koca" to listOf("koca"),
        "bey" to listOf("bey"),
        "hanim" to listOf("hanim"),
    )

    /** Short stems that collide with given names ("Esin", "Beyda") count only with "my": "eşim". */
    private val needsPossessive = setOf("es", "bey", "ana")

    /** Stems that are not words on their own ("oğl-um", "damad-ı"). */
    private val needsEnding = setOf("ogl", "damad")

    /** "my"/"our" endings after a consonant or a vowel (folded: ım/im -> im, um/üm -> um). */
    private val possessives = listOf("imiz", "umuz", "miz", "muz", "im", "um", "m")

    private val stemToCanonical: Map<String, String> =
        kinship.flatMap { (canonical, stems) -> stems.map { it to canonical } }.toMap()

    /**
     * Words that also appear in commands ("ara", "mesaj", "lütfen", "şu"). They never select a
     * person on their own; they only add to a person already found by another word, so "Ayşe'yi
     * ara" never offers a contact called "Ara", but "Ahmet Acar'ı ara" still finds Ahmet Acar.
     */
    private val weakWords = setOf(
        "lutfen", "bana", "benim", "bizim", "bir", "icin", "hemen", "simdi", "su", "sunu", "suna", "o", "onu", "ona",
        "bu", "bunu", "buna", "de", "da", "ki", "mi", "mu", "misin", "musun", "misiniz", "musunuz", "hadi", "haydi",
        "ve", "ile", "olan", "olani", "olana", "sey", "kisi", "kisiyi", "kisiye",
        "mesaj", "mesaji", "mesajla", "telefon", "telefonla", "telefonu", "whatsapp", "whatsapptan", "whatsappla",
        "sms", "arama", "aramasi", "goruntulu", "sesli", "numara", "numarasi", "numarasini", "merhaba", "selam",
    )

    private val weakVerbStems = listOf("ara", "yaz", "gonder", "yolla", "at", "ac", "konus", "bagla", "cevir", "ilet", "soyle", "sor")

    private val verbEndings = setOf(
        "", "r", "ar", "er", "in", "yin", "iniz", "yiniz", "sana", "sene", "saniza", "senize",
        "abilir", "ebilir", "yabilir", "abilirmisin", "ebilirmisin", "yabilirmisin",
        "abilirmisiniz", "ebilirmisiniz", "yabilirmisiniz", "armisin", "ermisin", "rmisin",
        "mak", "mek", "alim", "elim", "yalim", "ip", "yip", "iver", "sin", "ayim", "eyim", "yayim",
    )

    fun isWeak(token: String): Boolean =
        token in weakWords || token.all { it.isDigit() } ||
            weakVerbStems.any { token.startsWith(it) && token.substring(it.length) in verbEndings }

    /** True for a spoken kinship/address word ("kızıma", "teyzeyi"); G-37 does not treat it as unknown. */
    fun isKinship(token: String): Boolean = kinshipOf(token, allowCase = true) != null

    /** Words of a saved name; kinship words in nominative or "my" form become canonical ("Kızım" -> kiz). */
    fun nameWords(displayName: String): List<String> =
        TurkishText.tokens(displayName.replace(apostrophes, "")).map { kinshipOf(it, allowCase = false) ?: it }

    /**
     * Index words a spoken token may stand for: its kinship meaning, the token itself, and every
     * stem + Turkish case ending split, also with a softened last consonant ("zeynebi" -> zeynep).
     */
    fun candidates(token: String, isKnown: (String) -> Boolean): Set<String> {
        val result = LinkedHashSet<String>(4)
        kinshipOf(token, allowCase = true)?.let { if (isKnown(it)) result += it }
        for (end in token.length downTo 1) {
            val stem = token.substring(0, end)
            if (!TurkishText.isStemWithSuffix(token, stem)) continue
            val rest = token.substring(end)
            val soft = if (rest.isNotEmpty() && rest[0] in VOWELS) hardened(stem) else null
            val found = listOfNotNull(stem, soft).filter(isKnown)
            if (found.isNotEmpty()) {
                // Longest stem only: "esin" is the name Esin, not "eş" + "in".
                result += found
                break
            }
        }
        return result
    }

    /** Stems a token may come from when its ending is a case ending (used by the fuzzy pass). */
    fun stems(token: String): List<String> =
        (token.length downTo 1).map { token.substring(0, it) }.filter { TurkishText.isStemWithSuffix(token, it) }

    /**
     * Canonical kinship word for [token], or null. Saved names ([allowCase] false) may be bare or
     * "my" forms ("Annem"); spoken tokens may also carry a case ending ("amcama", "teyzeye").
     */
    private fun kinshipOf(token: String, allowCase: Boolean): String? {
        for ((stem, canonical) in stemToCanonical) {
            if (!token.startsWith(stem)) continue
            val rest = token.substring(stem.length)
            if (rest.isEmpty()) {
                if (stem !in needsPossessive && stem !in needsEnding) return canonical
                continue
            }
            for (p in possessives) {
                if (!rest.startsWith(p) || !fitsVowel(stem, p)) continue
                val ending = rest.substring(p.length)
                if (ending.isEmpty() || (allowCase && isCaseEnding(ending))) return canonical
            }
            if (allowCase && stem !in needsPossessive && isCaseEnding(rest) && (stem !in needsEnding || rest[0] in VOWELS)) {
                return canonical
            }
        }
        return null
    }

    private fun isCaseEnding(ending: String) = TurkishText.isStemWithSuffix("x$ending", "x")

    /** "m" only after a vowel, "im"/"um" only after a consonant: "annem", "kızım", not "kızm". */
    private fun fitsVowel(stem: String, possessive: String): Boolean {
        val endsInVowel = stem.last() in VOWELS
        return if (possessive.startsWith("m")) endsInVowel else !endsInVowel
    }

    private fun hardened(stem: String): String? = when (stem.lastOrNull()) {
        'b' -> stem.dropLast(1) + "p"
        'd' -> stem.dropLast(1) + "t"
        'g' -> stem.dropLast(1) + "k"
        else -> null
    }

    private const val VOWELS = "aeiou"
    private val apostrophes = Regex("['’‘`´]")
}
