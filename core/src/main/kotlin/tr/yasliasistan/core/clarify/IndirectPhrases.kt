package tr.yasliasistan.core.clarify

/** What an indirect complaint asks for (FG-09). */
enum class IndirectKind { VOLUME_UP, VOLUME_DOWN, BRIGHTNESS_UP, BRIGHTNESS_DOWN, TEXT_UP, TEXT_DOWN }

/**
 * Indirect phrase table (PLAN G-39, FG-09): "sesi duyamıyorum" -> turn the volume up, "ekran çok
 * karanlık" -> brightness, "yazılar küçük" -> text size. A match is turned into the direct command
 * ([canonical]) and decided by the ordinary settings rules, so the readback and risk stay the same.
 *
 * Deliberately narrow: a complaint must name the thing (sound, screen, text). "Torunumu
 * göremiyorum" names none of them and stays unmatched (the next layer decides; no guessed action).
 * A negation ("değil") or an explicit direction ("sesi aç") skips the table.
 * Tokens are normalized with TurkishText (lower case, ASCII-folded).
 */
internal object IndirectPhrases {

    fun match(tokens: List<String>): IndirectKind? {
        if (tokens.isEmpty() || "degil" in tokens || CommandWords.direction(tokens) != null) return null
        return text(tokens) ?: brightness(tokens) ?: volume(tokens)
    }

    /** The direct command an indirect phrase stands for. */
    fun canonical(kind: IndirectKind): List<String> = when (kind) {
        IndirectKind.VOLUME_UP -> listOf("sesi", "ac")
        IndirectKind.VOLUME_DOWN -> listOf("sesi", "kis")
        IndirectKind.BRIGHTNESS_UP -> listOf("parlaklik", "artir")
        IndirectKind.BRIGHTNESS_DOWN -> listOf("parlaklik", "azalt")
        IndirectKind.TEXT_UP -> listOf("yazilari", "buyut")
        IndirectKind.TEXT_DOWN -> listOf("yazilari", "kucult")
    }

    // --- Text size (checked first: "ekrandaki yazıları göremiyorum" is about text, not brightness) ---

    private val smallWords = setOf("kucuk", "kucucuk", "ufak", "ufacik", "minik", "incecik")
    private val bigWords = setOf("buyuk", "kocaman", "iri")

    private fun text(tokens: List<String>): IndirectKind? {
        if (tokens.none(CommandWords::isTextNoun)) return null
        return when {
            tokens.any { it in smallWords || isCannotRead(it) || isCannotSee(it) } -> IndirectKind.TEXT_UP
            tokens.any { it in bigWords } -> IndirectKind.TEXT_DOWN
            else -> null
        }
    }

    // --- Brightness ---

    private fun isScreen(token: String) = token.startsWith("ekran")

    private fun isDark(token: String) =
        token.startsWith("karanl") || token.startsWith("karard") || token.startsWith("kararm") ||
            token in setOf("los", "soluk", "kapkara", "gorunmuyor", "gozukmuyor")

    private fun isDazzling(token: String) = token.startsWith("kamas")

    private fun brightness(tokens: List<String>): IndirectKind? {
        val screen = tokens.any(::isScreen)
        return when {
            screen && tokens.any { isDark(it) || isCannotSee(it) } -> IndirectKind.BRIGHTNESS_UP
            screen && tokens.any { it == "parlak" || isDazzling(it) } -> IndirectKind.BRIGHTNESS_DOWN
            tokens.any(::isDazzling) && tokens.any { it.startsWith("goz") } -> IndirectKind.BRIGHTNESS_DOWN
            else -> null
        }
    }

    // --- Volume ---

    private val lowWords = setOf("az", "dusuk", "alcak", "kisik", "zayif", "hafif", "gelmiyor", "cikmiyor")
    private val highWords = setOf("yuksek", "fazla", "gurultulu", "bagiriyor", "sert")

    /** Words allowed next to a bare "duyamıyorum" ("sizi hiç duyamıyorum"); a named person is not. */
    private val hearingCompanions = setOf(
        "seni", "sizi", "hic", "iyi", "pek", "cok", "biraz", "telefonu", "telefonun", "zili", "zil", "ben",
        "artik", "yine", "gene", "maalesef", "ya",
    )

    private fun isCannotHear(token: String) =
        token.startsWith("duyam") || token.startsWith("duymuyor") || token.startsWith("duyulmuyor") ||
            token.startsWith("isitemiyor") || token.startsWith("isitmiyor")

    private fun volume(tokens: List<String>): IndirectKind? {
        val sound = tokens.any(CommandWords::isVolumeNoun)
        return when {
            sound && tokens.any { it in lowWords || isCannotHear(it) } -> IndirectKind.VOLUME_UP
            sound && tokens.any { it in highWords } -> IndirectKind.VOLUME_DOWN
            tokens.any(::isCannotHear) &&
                tokens.all { isCannotHear(it) || it in hearingCompanions || CommandWords.isVolumeNoun(it) } ->
                IndirectKind.VOLUME_UP
            else -> null
        }
    }

    // --- Shared ---

    private fun isCannotRead(token: String) = token.startsWith("okuyam") || token.startsWith("okunmuyor")

    private fun isCannotSee(token: String) =
        token.startsWith("goremiy") || token.startsWith("gormuyor") || token.startsWith("secemiy") ||
            token.startsWith("secilmiyor")
}
