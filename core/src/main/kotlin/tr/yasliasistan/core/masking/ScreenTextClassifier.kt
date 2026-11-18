package tr.yasliasistan.core.masking

import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.LabelSource
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.TextKind

/**
 * Masking policy of one piece of screen text (docs/MASKELEME.md §6):
 * - UI: the app's own label; kept, but numbers, addresses, contact names and special terms are masked (ARAYUZ).
 * - CONTENT: list items, notifications, settings summaries, titles; whole-text rule with the status exception (LISTE/BILDIRIM/OZET).
 * - INPUT: what someone typed into a field; whole-text rule without the status exception (DUZENLENEBILIR).
 * - SECRET: password field; its content never leaves the device (PAROLA).
 */
internal enum class ScreenTextClass { UI, CONTENT, INPUT, SECRET }

internal data class ScreenPart(val cls: ScreenTextClass, val text: String)

/**
 * Maps a [ScreenElement] to policy classes. Only text known to be interface text is UI; everything
 * whose author is unknown is CONTENT (default deny, §1.3).
 */
internal object ScreenTextClassifier {

    /** Separator the screen compressor uses when it merges child texts into one row label. */
    private const val MERGE_SEPARATOR = ", "

    private val TOGGLE_ROLES = setOf(
        ElementRole.SWITCH, ElementRole.CHECKBOX, ElementRole.RADIO, ElementRole.TAB, ElementRole.SLIDER,
    )

    fun parts(e: ScreenElement, lexicon: ScreenUiLexicon): List<ScreenPart> {
        if (e.secret || e.role == ElementRole.PASSWORD_FIELD) return listOf(ScreenPart(ScreenTextClass.SECRET, ""))
        val label = e.label ?: return emptyList()
        if (e.labelSource == LabelSource.HINT) return listOf(ScreenPart(ScreenTextClass.UI, label))
        if (e.editable || e.textKind == TextKind.USER_INPUT) return listOf(ScreenPart(ScreenTextClass.INPUT, label))
        if (e.textKind == TextKind.UI_LABEL && e.labelSource != LabelSource.CHILD_TEXT &&
            (e.packageName in lexicon.trustedUiPackages || e.role in TOGGLE_ROLES || lexicon.isUiPhrase(label))
        ) {
            return listOf(ScreenPart(ScreenTextClass.UI, label))
        }
        // Merged row texts ("Wi-Fi, Ev ağı") are judged part by part; other texts as a whole.
        val pieces = if (e.labelSource == LabelSource.CHILD_TEXT) label.split(MERGE_SEPARATOR) else listOf(label)
        return pieces.map { p ->
            ScreenPart(if (lexicon.isUiPhrase(p)) ScreenTextClass.UI else ScreenTextClass.CONTENT, p)
        }
    }

    /** Joins masked parts back the way the compressor joined them. */
    fun join(parts: List<String>) = parts.joinToString(MERGE_SEPARATOR)
}
