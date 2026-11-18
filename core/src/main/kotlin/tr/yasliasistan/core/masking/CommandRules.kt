package tr.yasliasistan.core.masking

/** Address-book matches as KISI spans (docs/MASKELEME.md §5.4). */
internal object ContactDetector {

    fun detect(ctx: MaskContext, index: ContactIndex): List<Span> {
        if (index.isEmpty) return emptyList()
        val out = ArrayList<Span>()
        var i = 0
        while (i < ctx.tokens.size) {
            val m = index.matchAt(ctx, i)
            if (m == null) { i++; continue }
            val single = m.contacts.size == 1
            val value: String
            val key: String
            if (single) {
                value = m.contacts[0].displayName
                key = "#" + m.contacts[0].id
            } else {
                // Ambiguous: the spoken word is the key; the device asks which one (G-36).
                value = if (m.matchedFirstNameOnly) m.contacts[0].displayName.trim().split(' ')[0] else m.contacts[0].displayName
                key = TurkishFold.lower(value)
            }
            out += ctx.span(ctx.tokens[m.firstToken].coreStart, m.stemEnd, MaskCategory.KISI, value, key, m.fusedSuffix)
            i = m.lastToken + 1
        }
        return out
    }
}

/** Dictated content (§5.6): everything the user wants written or said becomes one METIN span. */
internal object DictationDetector {

    /** [stoppers]: spans (contacts, numbers) that end a leftward "diye" scan. */
    fun detect(ctx: MaskContext, stoppers: List<Span>): List<Span> {
        val toks = ctx.tokens
        val dict = ctx.dict
        val out = ArrayList<Span>()
        fun blocked(t: Token) = stoppers.any { it.overlaps(t.coreStart, t.coreEnd) }
        fun content(from: Int, to: Int) {
            if (from > to) return
            val value = ctx.surface(from, toks[to].coreEnd)
            out += Span(toks[from].coreStart, toks[to].coreEnd, MaskCategory.METIN, value, TurkishFold.lower(value))
        }
        for (i in toks.indices) {
            // "... diye mesaj at": the words before "diye" back to the recipient.
            if (toks[i].fold in dict.dictationBefore &&
                (i + 1..minOf(i + 2, toks.size - 1)).any { dict.isDictationVerb(toks[it].fold) }
            ) {
                var from = i
                while (from - 1 >= 0 && !blocked(toks[from - 1])) from--
                content(from, i - 1)
            }
            // "de ki ...", "şunu yaz ...": everything after the marker.
            for (marker in dict.dictationAfter) {
                if (i + marker.size <= toks.size && marker.indices.all { toks[i + it].fold == marker[it] }) {
                    content(i + marker.size, toks.size - 1)
                }
            }
            // "mesaj: ...", "not: ...".
            if (toks[i].raw.endsWith(":") && toks[i].fold in dict.dictationColonWords) {
                content(i + 1, toks.size - 1)
            }
        }
        return out
    }
}

/** Names outside the address book, found from context (§5.5). Runs on uncovered words only. */
internal object ContextNameDetector {

    fun detect(ctx: MaskContext, typed: Boolean) {
        honorifics(ctx)
        personSlots(ctx)
        if (typed) capitalized(ctx)
    }

    /** Up to N unknown words right before token [i], nearest first. */
    private fun unknownBefore(ctx: MaskContext, i: Int, allowPrefix: Boolean = true): IntRange? {
        var from = i
        while (from - 1 >= 0 && i - (from - 1) <= ctx.dict.settings.contextMaxWords) {
            val t = ctx.tokens[from - 1]
            if (!ctx.isFreeUnknown(t, allowPrefix) || (from - 1 < i - 1 && t.hasTrailingPunctuation)) break
            from--
        }
        return if (from < i) from until i else null
    }

    /** "gülten test hanıma" -> "[KISI_1] hanıma"; the ending stays on the honorific. */
    private fun honorifics(ctx: MaskContext) {
        for (i in ctx.tokens.indices) {
            val t = ctx.tokens[i]
            if (ctx.isCovered(t) || !ctx.dict.isHonorific(t)) continue
            // A word before "hanım/bey" is almost surely a name: dictionary prefixes do not make
            // it "known" here ("kerem" starts with "kere").
            val range = unknownBefore(ctx, i, allowPrefix = false) ?: continue
            val first = ctx.tokens[range.first]
            val last = ctx.tokens[range.last]
            val value = TurkishFold.titleCase(ctx.surface(range.first, last.baseEnd))
            ctx.accept(listOf(ctx.span(first.coreStart, last.baseEnd, MaskCategory.KISI, value, TurkishFold.lower(value))))
        }
    }

    /** "selin teste mesaj at" -> "[KISI_1]'e mesaj at"; without an apostrophe the value keeps its ending. */
    private fun personSlots(ctx: MaskContext) {
        for (i in ctx.tokens.indices) {
            val t = ctx.tokens[i]
            val cls = ctx.dict.personTriggers[t.fold] ?: continue
            if (ctx.isCovered(t)) continue
            val range = unknownBefore(ctx, i) ?: continue
            val first = ctx.tokens[range.first]
            val last = ctx.tokens[range.last]
            val span = if (last.baseEnd < last.coreEnd) {
                val value = TurkishFold.titleCase(ctx.surface(range.first, last.baseEnd))
                ctx.span(first.coreStart, last.baseEnd, MaskCategory.KISI, value, TurkishFold.lower(value))
            } else {
                val value = TurkishFold.titleCase(ctx.surface(range.first, last.coreEnd))
                Span(first.coreStart, last.coreEnd, MaskCategory.KISI, value, TurkishFold.lower(value), cls, valueHasSuffix = true)
            }
            ctx.accept(listOf(span))
        }
    }

    /** Typed text: capitalised unknown words that do not start a sentence (§5.5.3). */
    private fun capitalized(ctx: MaskContext) {
        val toks = ctx.tokens
        fun sentenceStart(i: Int) = i == 0 || toks[i - 1].endsSentence
        fun candidate(i: Int) = toks[i].startsUpper && ctx.isFreeUnknown(toks[i])
        var i = 0
        while (i < toks.size) {
            if (!candidate(i) || sentenceStart(i)) { i++; continue }
            var from = i
            if (i - 1 >= 0 && sentenceStart(i - 1) && candidate(i - 1) && !toks[i - 1].hasTrailingPunctuation) from = i - 1
            var to = i
            while (!toks[to].hasTrailingPunctuation && toks[to].baseEnd == toks[to].coreEnd &&
                to + 1 < toks.size && candidate(to + 1)) to++
            val last = toks[to]
            val value = ctx.surface(from, last.baseEnd)
            ctx.accept(listOf(ctx.span(toks[from].coreStart, last.baseEnd, MaskCategory.KISI, value, TurkishFold.lower(value))))
            i = to + 1
        }
    }
}

/** Long narration (§5.8): every remaining unknown word becomes METIN; neighbours merge. */
internal object IntentModeRule {

    fun apply(ctx: MaskContext) {
        val toks = ctx.tokens
        val out = ArrayList<Span>()
        var i = 0
        while (i < toks.size) {
            if (!ctx.isFreeUnknown(toks[i])) { i++; continue }
            var j = i
            while (!toks[j].hasTrailingPunctuation && j + 1 < toks.size && ctx.isFreeUnknown(toks[j + 1])) j++
            val value = ctx.surface(i, toks[j].coreEnd)
            out += Span(toks[i].coreStart, toks[j].coreEnd, MaskCategory.METIN, value, TurkishFold.lower(value))
            i = j + 1
        }
        ctx.accept(out)
    }
}
