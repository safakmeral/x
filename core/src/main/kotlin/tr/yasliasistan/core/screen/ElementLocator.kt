package tr.yasliasistan.core.screen

import tr.yasliasistan.core.metrics.ScreenMetrics
import tr.yasliasistan.core.text.TurkishText

/** Result of looking up an element by its visible name. */
sealed interface LookupResult {
    data class Found(val element: ScreenElement) : LookupResult

    /** Several elements match; the assistant asks which one (never guesses). */
    data class Ambiguous(val candidates: List<ScreenElement>) : LookupResult

    /**
     * Nothing matches (NFG-30: the assistant asks the user). [unlabeledIcons] > 0 means the target
     * may be an icon without a label (TK-04): the assistant says it is not sure and asks.
     */
    data class NotFound(val unlabeledIcons: Int) : LookupResult
}

/**
 * Finds an element by name in a [ScreenSnapshot] (Turkish-normalized; exact name first, then
 * names containing every spoken word). Each miss is counted for DEG-20 via [metrics].
 * Password fields never match by text because they carry none.
 */
class ElementLocator(private val metrics: ScreenMetrics = ScreenMetrics.NONE) {

    fun find(snapshot: ScreenSnapshot, name: String, role: ElementRole? = null): LookupResult {
        val wanted = TurkishText.normalize(name)
        val pool = snapshot.elements.filter { it.label != null && (role == null || it.role == role) }
        if (wanted.isNotEmpty()) {
            val exact = pool.filter { TurkishText.normalize(it.label!!) == wanted }
            if (exact.isNotEmpty()) return pick(exact)
            val words = wanted.split(' ')
            val partial = pool.filter { e -> TurkishText.tokens(e.label!!).let { t -> words.all { it in t } } }
            if (partial.isNotEmpty()) return pick(partial)
        }
        metrics.elementNotFound()
        return LookupResult.NotFound(snapshot.unlabeledIconCount)
    }

    private fun pick(matches: List<ScreenElement>): LookupResult {
        // Prefer something the user can act on over a plain text with the same name.
        val actionable = matches.filter { it.actionable && it.enabled }
        val best = actionable.ifEmpty { matches }
        return if (best.size == 1) LookupResult.Found(best[0]) else LookupResult.Ambiguous(best)
    }
}
