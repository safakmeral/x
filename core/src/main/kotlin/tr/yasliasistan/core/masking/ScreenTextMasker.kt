package tr.yasliasistan.core.masking

/**
 * Masks screen texts into the task's shared placeholder table (docs/MASKELEME.md §6), so a value
 * seen in the command and on screen gets the same placeholder.
 */
internal class ScreenTextMasker(
    private val dict: MaskingDictionary,
    private val contacts: ContactIndex,
    private val table: PlaceholderTable,
) {
    /** Result of one text: the masked string, or the reason the whole request must stop. */
    sealed interface Outcome {
        data class Text(val value: String, val categories: List<MaskCategory>) : Outcome
        data class Block(val reason: BlockReason) : Outcome
    }

    private val time = Regex("\\d{1,2}:\\d{2}")
    private val shortNumber = Regex("\\d{1,3}")
    private val statusWords = dict.lexicon.statusWords.mapTo(HashSet()) { TurkishFold.lower(it) }

    /**
     * Checks every part for a verification code or secret first (§5.9: one hit stops the whole
     * screen), then masks. Nothing is written to the table when the screen is blocked.
     */
    fun maskAll(parts: List<ScreenPart>): List<Outcome> {
        val contexts = parts.map { if (it.cls == ScreenTextClass.SECRET) null else context(it.text) }
        contexts.forEach { ctx -> ctx?.let { SecretDetector.detect(it) }?.let { return listOf(Outcome.Block(it)) } }
        return parts.mapIndexed { i, part ->
            val ctx = contexts[i]
            when {
                ctx == null -> Outcome.Text("", emptyList())
                ctx.tokens.isEmpty() -> Outcome.Text("", emptyList())
                part.cls == ScreenTextClass.UI -> maskUi(ctx)
                else -> maskWhole(ctx, keepStatus = part.cls == ScreenTextClass.CONTENT)
            }
        }
    }

    private fun context(text: String) = MaskContext(TokenizedText(TokenizedText.preprocess(text)), dict)

    /** ARAYUZ: the label stays, detected values inside it are replaced (§5.1-5.4, §5.7). */
    private fun maskUi(ctx: MaskContext): Outcome {
        val structural = StructuralDetector.detect(ctx) + AddressDetector.detect(ctx)
        val names = ContactDetector.detect(ctx, contacts)
        val sensitive = SensitiveTermDetector.detect(ctx)
        ctx.accept(structural + names + sensitive)
        return Outcome.Text(render(ctx, ctx.accepted), ctx.accepted.map { it.category })
    }

    /** "Bütün metin kuralı" of §6: one placeholder for the whole text unless it is harmless. */
    private fun maskWhole(ctx: MaskContext, keepStatus: Boolean): Outcome {
        wholeSpan(ctx, ContactDetector.detect(ctx, contacts))?.let { return Outcome.Text(render(ctx, listOf(it)), listOf(it.category)) }
        val structural = wholeSpan(ctx, StructuralDetector.detect(ctx) + AddressDetector.detect(ctx))
        if (structural != null && structural.category != MaskCategory.METIN) {
            return Outcome.Text(render(ctx, listOf(structural)), listOf(structural.category))
        }
        if (keepStatus && isHarmless(ctx.text)) return Outcome.Text(ctx.text, emptyList())
        val all = Span(0, ctx.text.length, MaskCategory.METIN, ctx.text, TurkishFold.lower(ctx.text))
        return Outcome.Text(render(ctx, listOf(all)), listOf(MaskCategory.METIN))
    }

    /** The single accepted span that covers every word of the text, if there is one. */
    private fun wholeSpan(ctx: MaskContext, candidates: List<Span>): Span? {
        if (candidates.isEmpty()) return null
        ctx.accepted.clear()
        ctx.accept(candidates)
        val only = ctx.accepted.singleOrNull()
        ctx.accepted.clear()
        only ?: return null
        val first = ctx.tokens.first()
        val last = ctx.tokens.last()
        return only.takeIf { it.start <= first.coreStart && it.end >= last.coreEnd }
    }

    /** Status word, clock time, day name (in the status list) or a number of up to 3 digits (§6.4). */
    private fun isHarmless(text: String): Boolean {
        val t = text.trim()
        return TurkishFold.lower(t) in statusWords || time.matches(t) || shortNumber.matches(t)
    }

    private fun render(ctx: MaskContext, spans: List<Span>) = renderSpans(ctx.text, spans, table)
}

/** Replaces [spans] in [text] with their placeholders (numbered left to right) and fixed endings. */
internal fun renderSpans(text: String, spans: List<Span>, table: PlaceholderTable): String {
    val sb = StringBuilder()
    var pos = 0
    for (s in spans.sortedBy { it.start }) {
        sb.append(text, pos, s.start)
        sb.append(table.placeholderFor(s.category, s.key, s.value, s.valueHasSuffix))
        sb.append(s.caseClass)
        pos = s.end
    }
    sb.append(text, pos, text.length)
    return sb.toString()
}
