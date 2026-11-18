package tr.yasliasistan.core.action

import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.TextKind

/**
 * Sentences of "Sen yap" mode (PLAN G-22/G-23). The app supplies them from strings.xml
 * (Turkish). [name] is a short, speakable element name ([SpokenName]) or null for a generic word.
 * Typed text is never repeated (only "yazdım").
 */
interface StepTexts {
    // One line per finished step (FG-22).
    fun tapped(name: String?): String
    fun longPressed(name: String?): String
    fun typed(name: String?): String
    fun scrolled(direction: ScrollDirection): String
    fun wentBack(): String
    fun wentHome(): String
    fun opened(appName: String): String

    /** G-23: the element is not visible; the assistant scrolls to look for it (FG-28). */
    fun searching(direction: ScrollDirection): String

    // One question when the task stops (FG-24, NFG-30).
    fun screenChanged(): String
    fun notFound(name: String?): String
    fun noReaction(): String
    fun unexpectedPage(undone: Boolean): String

    // Giving the task to the user (FG-26, FG-27).
    fun handOver(reason: HandOverReason): String
    fun budgetExceeded(): String
    fun refused(reason: RefusalReason): String
    fun failed(): String
}

/** Says one narration line; must return at once (sentences queue up, "Dur" silences them). */
fun interface Narrator {
    fun say(text: String)

    companion object {
        val NONE = Narrator { }
    }
}

/**
 * Which element names may be spoken in a narration (V-23: names yes, message content and numbers
 * no; NFG-09: words the user sees on the screen). App labels are spoken; list texts only when they
 * look like a short name (first part, at most 3 words, no digits); typed, secret and other texts
 * never. Everything else is narrated with a generic word.
 */
object SpokenName {
    private const val MAX_CHARS = 40
    private const val MAX_LIST_WORDS = 3
    private const val MAX_LIST_CHARS = 30

    fun of(element: ScreenElement?): String? {
        if (element == null || element.secret || element.unlabeledIcon) return null
        val label = element.label?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (element.textKind) {
            TextKind.UI_LABEL -> label.takeIf { it.length <= MAX_CHARS && !it.any(Char::isDigit) }
            TextKind.LIST_CONTENT -> label.substringBefore(',').trim().takeIf {
                it.isNotEmpty() && it.length <= MAX_LIST_CHARS && !it.any(Char::isDigit) &&
                    it.split(' ').count { w -> w.isNotBlank() } <= MAX_LIST_WORDS
            }
            TextKind.USER_INPUT, TextKind.OTHER_TEXT, TextKind.NONE -> null
        }
    }
}
