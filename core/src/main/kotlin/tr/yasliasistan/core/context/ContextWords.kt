package tr.yasliasistan.core.context

/**
 * Word lists for context commands (PLAN G-39). Tokens are normalized with TurkishText
 * (lower case, ASCII-folded: "şunu" -> "sunu", "niçin" -> "nicin").
 */
internal object ContextWords {

    // --- "Neden?" (FG-15) ---

    private val whyWords = setOf("neden", "niye", "nicin", "nedenini", "niyesini", "sebep", "sebebi", "sebebini")

    /** Words that may stand next to "neden" in a question about the last answer. */
    private val whyCompanions = setOf(
        "peki", "ya", "ama", "acaba", "bu", "bunu", "o", "onu", "sunu", "boyle", "oyle", "ki", "mi", "mu",
        "sen", "siz", "ne", "nedir", "soyle", "soyler", "misin", "misiniz", "acikla", "aciklar", "lutfen",
        "yaptin", "yaptiniz", "sordun", "sordunuz", "soruyorsun", "soruyorsunuz", "actin", "actiniz",
        "dedin", "dediniz", "sectin", "sectiniz", "oldu", "olmadi", "yapmadin", "yapamadin", "yapamiyorsun",
        "acildi", "kistin", "artirdin",
    )

    fun isWhy(tokens: List<String>): Boolean =
        tokens.any { it in whyWords } && tokens.all { it in whyWords || it in whyCompanions }

    /** "Biraz daha anlat", "ayrıntılı anlat", "açıklar mısın": the second layer. */
    fun isDetail(tokens: List<String>): Boolean {
        if (tokens.isEmpty() || tokens.size > 5) return false
        val tell = tokens.any { it.startsWith("anlat") }
        val more = tokens.any { it == "daha" || it.startsWith("ayrinti") || it.startsWith("detay") }
        val explain = tokens.any { it.startsWith("acikla") }
        return (tell && more) || explain || (more && tokens.any { it.startsWith("acik") })
    }

    // --- Repeat ("tekrar", "aynısını yap") ---

    private val politeness = setOf("lutfen", "hadi", "haydi", "efendim", "bir", "sunu", "bunu", "onu", "misin", "misiniz")
    private val againWords = setOf("tekrar", "yine", "gene", "daha", "aynisini", "ayni", "seyi", "sey", "tekrarla", "tekrarlar", "tekrarlayin", "tekrarlasana")
    private val doWords = setOf("yap", "yapar", "yapin", "yapiniz", "yapsana", "yapabilir", "yapabilirmisin", "et", "eder", "edin", "etsene")

    /**
     * The whole command only asks to do the last thing again. "tekrar söyle" (repeat the sentence,
     * G-17) and "yapma" are not repeat requests.
     */
    fun isRepeat(tokens: List<String>): Boolean {
        val rest = tokens.filterNot { it in politeness }
        if (rest.isEmpty() || rest.any { it !in againWords && it !in doWords }) return false
        if (rest.none { it in againWords }) return false
        // "daha" alone is "more", not "again"; it needs "bir" ("bir daha").
        val onlyDaha = rest.filter { it in againWords }.all { it == "daha" }
        return !onlyDaha || "bir" in tokens
    }

    // --- References (FG-08) ---

    /** "bu / şu" forms that point at something on the screen. */
    val demonstratives = setOf("bu", "bunu", "buna", "su", "sunu", "suna")

    /** "o / onu / ona / tekrar / aynı" forms that point back at the previous command. */
    val backReferences = setOf("o", "onu", "ona", "tekrar", "yine", "gene", "ayni", "daha")

    fun isAppNoun(token: String) = token.startsWith("uygulama")
}
