package tr.yasliasistan.core.masking

/** Special-category terms (KVKK m.6, docs/MASKELEME.md §5.7): adjacent terms form one OZEL span. */
internal object SensitiveTermDetector {

    fun detect(ctx: MaskContext): List<Span> {
        val toks = ctx.tokens
        val marked = BooleanArray(toks.size)
        for (i in toks.indices) {
            if (ctx.dict.isSensitiveWord(toks[i].fold)) marked[i] = true
            for (phrase in ctx.dict.sensitivePhrases) {
                if (matchesPhrase(toks, i, phrase)) for (k in phrase.indices) marked[i + k] = true
            }
        }
        val out = ArrayList<Span>()
        var i = 0
        while (i < toks.size) {
            if (!marked[i]) { i++; continue }
            var j = i
            while (j + 1 < toks.size && marked[j + 1] && !toks[j].hasTrailingPunctuation) j++
            val value = ctx.surface(i, toks[j].coreEnd)
            out += Span(toks[i].coreStart, toks[j].coreEnd, MaskCategory.OZEL, value, TurkishFold.lower(value))
            i = j + 1
        }
        return out
    }

    /** Each phrase element is a word prefix ("şeker" "hastal" matches "şeker hastalığım"). */
    fun matchesPhrase(toks: List<Token>, i: Int, phrase: List<String>): Boolean =
        i + phrase.size <= toks.size && phrase.indices.all { k -> toks[i + k].foldJoined.startsWith(phrase[k]) }
}

/** Verification codes and secrets (§5.9, FG-79): any hit blocks the whole request. */
internal object SecretDetector {

    fun detect(ctx: MaskContext): BlockReason? {
        val toks = ctx.tokens
        val dict = ctx.dict
        for (i in toks.indices) {
            if (dict.secretPhrases.any { SensitiveTermDetector.matchesPhrase(toks, i, it) }) {
                return BlockReason.DOGRULAMA_KODU
            }
        }
        val numbers = numberPositions(ctx)
        fun digitsNear(i: Int) = numbers.any { (tokenIndex, digits) ->
            digits >= dict.settings.secretMinDigits && tokenIndex in (i - WINDOW)..(i + WINDOW) && tokenIndex != i
        } || ownDigits(toks[i]) >= dict.settings.secretMinDigits
        for (i in toks.indices) {
            val w = toks[i].foldJoined
            if (dict.secretWithDigits.any { w.startsWith(it) } && digitsNear(i)) return BlockReason.GIZLI_BILGI
        }
        for (i in toks.indices) {
            val w = toks[i].foldJoined
            if (dict.codeWithDigits.none { w.startsWith(it) }) continue
            val excepted = dict.codeExceptions.any { ex ->
                val start = i - (ex.size - 1)
                start >= 0 && SensitiveTermDetector.matchesPhrase(toks, start, ex)
            }
            if (!excepted && digitsNear(i)) return BlockReason.DOGRULAMA_KODU
        }
        return null
    }

    private const val WINDOW = 3
    private val DIGIT_GROUPS = Regex("\\d+(?:[ .\\-/]\\d+)*")

    private fun ownDigits(t: Token) = t.core.count { it.isDigit() }

    /** (token index, digit count) of every written digit group and spoken-number run. */
    private fun numberPositions(ctx: MaskContext): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        ctx.tokens.forEach { t -> ownDigits(t).takeIf { it > 0 }?.let { out += t.index to it } }
        // Written groups such as "0599 000" span several tokens: count the whole run at its first token.
        DIGIT_GROUPS.findAll(ctx.text).forEach { m ->
            val idx = ctx.tokenized.tokenAt(m.range.first)
            if (idx >= 0) out += idx to m.value.count { it.isDigit() }
        }
        StructuralDetector.numberWordRuns(ctx).forEach { run ->
            val digits = SpokenNumbers.toDigits(run.map { it.second.value }).length
            out += run.first().first.index to digits
        }
        return out
    }
}
