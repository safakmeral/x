package tr.yasliasistan.core.decision.rules

import tr.yasliasistan.core.decision.InstalledApp
import tr.yasliasistan.core.text.TurkishText

/**
 * Matches the name part of a command against app labels.
 *
 * Words are joined without spaces before comparing, so "whats app" equals "WhatsApp" and
 * "e devleti" equals "e-Devlet" + case ending. Exact (whole label) matches win; only when there is
 * none are partial matches (a run of label words, at least [MIN_PARTIAL_LENGTH] letters) used.
 */
object AppNameMatcher {

    class Result(val apps: List<InstalledApp>, val exact: Boolean)

    private const val MIN_PARTIAL_LENGTH = 3

    /** Words that are not part of an app name ("uygulamasını", "lütfen", question particles). */
    private val fillerWords = setOf(
        "mi", "mu", "misin", "musun", "misiniz", "musunuz", "lutfen", "bana", "benim", "bir", "icin",
        "hemen", "simdi", "su", "sunu", "o", "onu", "bu", "bunu", "de", "da", "ki", "hadi", "haydi", "bakalim",
        "uygulama", "uygulamasi", "uygulamasini", "uygulamasina", "uygulamayi", "uygulamaya",
        "program", "programi", "programini", "olan", "olani", "olanini",
    )

    fun nameKey(tokens: List<String>): String = tokens.filterNot { it in fillerWords }.joinToString("")

    fun match(key: String, apps: List<InstalledApp>): Result {
        if (key.isEmpty()) return Result(emptyList(), exact = false)
        val exact = apps.filter { TurkishText.isStemWithSuffix(key, labelKey(it)) }
        if (exact.isNotEmpty()) return Result(exact, exact = true)
        return Result(apps.filter { app -> partialKeys(app).any { TurkishText.isStemWithSuffix(key, it) } }, exact = false)
    }

    private fun labelKey(app: InstalledApp) = TurkishText.tokens(app.label).joinToString("")

    /** Every contiguous run of label words except the whole label. */
    private fun partialKeys(app: InstalledApp): List<String> {
        val words = TurkishText.tokens(app.label)
        val keys = mutableListOf<String>()
        for (from in words.indices) {
            for (to in from + 1..words.size) {
                if (from == 0 && to == words.size) continue
                val key = words.subList(from, to).joinToString("")
                if (key.length >= MIN_PARTIAL_LENGTH) keys += key
            }
        }
        return keys
    }
}
