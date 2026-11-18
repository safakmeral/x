package tr.yasliasistan.core.dialog

import tr.yasliasistan.core.text.TurkishText

/** Fixed spoken commands (PLAN G-17, V-12/TK-16; FG-12, FG-13, FG-23). */
enum class VoiceCommand {
    /** "Dur": stops whatever runs (FG-23). Highest priority. */
    STOP,

    /** "Tamam, hatırladım": "Tamam, kapatıyorum." and stop (FG-13). */
    REMEMBERED,

    /** "Baştan anlat": the explanation / guidance starts again from its first step (FG-12). */
    RESTART,

    /** "Tekrar söyle": the last sentence again. */
    REPEAT,

    /** "Neden": the layered explanation of G-39 (FG-15). */
    WHY,

    /** "Yavaş konuş": slow speech rate (G-08 setting) and the last sentence again (docs/SAPMALAR.md). */
    SLOWER,
}

/**
 * On-device matcher for [VoiceCommand] (no cloud, before any rule). A command is recognized only when
 * the whole utterance is the command, so "durumu söyle" or "annemi ara" never stop anything.
 * Polite fillers ("lütfen", "biraz") are ignored; words of six or more letters may differ by one
 * letter (recognizer slips such as "hatırladın"); shorter words must match exactly.
 * "Tekrar et" is not here: it means "do it again" (G-39). Never logs the text.
 */
object VoiceCommands {

    private val fillers = setOf(
        "lutfen", "hadi", "haydi", "bir", "biraz", "sen", "siz", "ben", "artik", "ya", "simdi",
        "misin", "musun", "misiniz", "musunuz",
    )

    // ASCII-folded phrases (TurkishText) after the fillers are removed.
    private val table: Map<VoiceCommand, List<String>> = mapOf(
        VoiceCommand.STOP to listOf(
            "dur", "dur dur", "durdur", "durun", "dursana", "sus", "yeter", "kes", "vazgec", "vazgectim",
            "iptal", "iptal et", "tamam dur",
        ),
        VoiceCommand.REMEMBERED to listOf(
            "tamam hatirladim", "hatirladim", "hatirladim tamam", "tamam hatirladim tesekkurler",
            "tamam hatirladim tesekkur ederim", "tamam anladim hatirladim",
        ),
        VoiceCommand.RESTART to listOf(
            "bastan anlat", "en bastan anlat", "bastan basla", "en bastan", "bastan al", "bastan soyle",
            "yeniden anlat", "bastan anlatir",
        ),
        VoiceCommand.REPEAT to listOf(
            "tekrar soyle", "tekrar soyler", "tekrarla", "tekrarlar", "daha soyle", "tekrar soyleyin",
            "ne dedin", "ne dediniz", "anlamadim", "duyamadim", "duymadim", "efendim",
        ),
        VoiceCommand.WHY to listOf("neden", "niye", "nicin", "neden ki", "niye ki", "peki neden", "ama neden"),
        VoiceCommand.SLOWER to listOf(
            "yavas konus", "yavas", "daha yavas", "daha yavas konus", "yavas soyle", "yavas konusur",
            "yavas konusun", "daha yavas konusur", "yavas oku",
        ),
    )

    private val entries: List<Pair<VoiceCommand, List<String>>> =
        table.flatMap { (command, phrases) -> phrases.map { command to it.split(' ') } }

    fun match(text: String): VoiceCommand? {
        val words = TurkishText.tokens(text).filterNot { it in fillers }
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        return entries.firstOrNull { (_, phrase) -> sameWords(words, phrase) }?.first
    }

    private fun sameWords(heard: List<String>, phrase: List<String>): Boolean =
        heard.size == phrase.size && heard.indices.all { near(heard[it], phrase[it]) }

    private fun near(a: String, b: String): Boolean {
        if (a == b) return true
        if (minOf(a.length, b.length) < MIN_FUZZY_LENGTH) return false
        return withinOneEdit(a, b)
    }

    /** Levenshtein distance <= 1. */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        val (short, long) = if (a.length <= b.length) a to b else b to a
        var i = 0
        while (i < short.length && short[i] == long[i]) i++
        if (i == short.length) return true
        return if (short.length == long.length) {
            short.substring(i + 1) == long.substring(i + 1)
        } else {
            short.substring(i) == long.substring(i + 1)
        }
    }

    private const val MAX_WORDS = 5
    private const val MIN_FUZZY_LENGTH = 6
}
