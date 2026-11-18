package tr.yasliasistan.core.masking

/**
 * Open addresses (docs/MASKELEME.md §5.3): an anchor word (mahalle, sokak, cadde ...) plus up to
 * three unknown words on its left and numbers, markers and further anchors on its right.
 */
internal object AddressDetector {

    fun detect(ctx: MaskContext): List<Span> {
        val out = ArrayList<Span>()
        val toks = ctx.tokens
        var i = 0
        while (i < toks.size) {
            if (!isAnchor(ctx, toks[i])) { i++; continue }
            val first = leftEdge(ctx, i)
            var last = i
            var fused: String? = null
            var stemEnd = toks[i].baseEnd
            var k = i
            while (!closes(ctx, toks[k]) && k + 1 < toks.size) {
                val next = toks[k + 1]
                val hit = ctx.numberWord(next)
                val continues = isAnchor(ctx, next) || isNumberMarker(ctx, next) || hit != null ||
                    next.core.firstOrNull()?.isDigit() == true || next.core.contains('/') ||
                    (!ctx.isKnown(next) && k + 2 < toks.size && isAnchor(ctx, toks[k + 2]))
                if (!continues) break
                k++
                last = k
                stemEnd = hit?.stemEnd ?: next.baseEnd
                fused = hit?.fusedSuffix
                if (fused != null) break
            }
            val value = ctx.surface(first, stemEnd)
            out += ctx.span(toks[first].coreStart, stemEnd, MaskCategory.ADRES, value, TurkishFold.lower(value), fused)
            i = last + 1
        }
        return out
    }

    /**
     * A token that ends the address: a written ending or punctuation. The dot of an abbreviation
     * ("Mah.", "Cad.") or of an ordinal number ("1. Sokak") does not end it.
     */
    private fun closes(ctx: MaskContext, t: Token): Boolean {
        if (t.baseEnd < t.coreEnd) return true
        if (!t.hasTrailingPunctuation) return false
        return !isDottedPart(ctx, t)
    }

    private fun isDottedPart(ctx: MaskContext, t: Token): Boolean {
        if (t.end != t.coreEnd + 1 || !t.raw.endsWith(".")) return false
        val rawFold = TurkishFold.fold(t.raw)
        return rawFold in ctx.dict.addressAnchors || rawFold in ctx.dict.addressNumberMarkers ||
            (t.core.isNotEmpty() && t.core.all { it.isDigit() } && t.core.length <= 3)
    }

    private fun leftEdge(ctx: MaskContext, anchor: Int): Int {
        var first = anchor
        var k = anchor - 1
        while (k >= 0 && anchor - k <= ctx.dict.settings.addressMaxLeftWords) {
            val t = ctx.tokens[k]
            if (closes(ctx, t) || ctx.isKnown(t)) break
            first = k; k--
        }
        return first
    }

    private fun isAnchor(ctx: MaskContext, t: Token): Boolean {
        val rawFold = TurkishFold.fold(t.raw)
        return t.fold in ctx.dict.addressAnchors || rawFold in ctx.dict.addressAnchors
    }

    /** "no", "kat", "daire", "No:5", "D:4" ... */
    private fun isNumberMarker(ctx: MaskContext, t: Token): Boolean {
        val rawFold = TurkishFold.fold(t.raw)
        if (t.fold in ctx.dict.addressNumberMarkers || rawFold in ctx.dict.addressNumberMarkers) return true
        val colon = rawFold.indexOf(':')
        return colon > 0 && rawFold.substring(0, colon + 1) in ctx.dict.addressNumberMarkers
    }
}
