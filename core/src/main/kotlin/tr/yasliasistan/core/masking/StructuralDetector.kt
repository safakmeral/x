package tr.yasliasistan.core.masking

/**
 * Pattern-based detectors (docs/MASKELEME.md §5.1, §5.2): e-mail (written and spoken), URL,
 * IBAN, card, T.C. number, phone, coordinates, plates, digit runs and spoken numbers.
 */
internal object StructuralDetector {

    private const val NB = "(?<![\\p{L}\\p{N}])"
    private val email = Regex("[\\p{L}\\p{N}_.+-]+@[\\p{L}\\p{N}_-]+(?:\\.[\\p{L}\\p{N}_-]+)+")
    private val url = Regex("(?i)(?:https?://|www\\.)\\S+")
    private val iban = Regex("(?i)${NB}tr\\d{2}(?: ?\\d){22}(?!\\d)")
    private val coordinate = Regex("$NB-?\\d{1,3}\\.\\d{3,}, ?-?\\d{1,3}\\.\\d{3,}(?!\\d)")
    private val plate = Regex("$NB\\d{2} [A-ZÇĞİÖŞÜ]{1,3} \\d{2,4}(?![\\p{L}\\p{N}])")

    /** Digit groups joined by one separator: space . - / ( ) and a leading +. */
    private val digitRun = Regex("$NB\\+?\\(?\\d+(?:(?:[ .\\-/]|\\) ?| ?\\()\\d+)*")
    private const val URL_TRAILING = ".,!?;:)\"'"

    fun detect(ctx: MaskContext): List<Span> {
        val out = ArrayList<Span>()
        val text = ctx.text
        val minRun = ctx.dict.settings.minDigitRun

        email.findAll(text).forEach { m ->
            out += ctx.span(m.range.first, m.range.last + 1, MaskCategory.EPOSTA, m.value, TurkishFold.lower(m.value))
        }
        url.findAll(text).forEach { m ->
            val value = m.value.trimEnd { it in URL_TRAILING }
            out += ctx.span(m.range.first, m.range.first + value.length, MaskCategory.METIN, value, value)
        }
        iban.findAll(text).forEach { m ->
            val compact = m.value.replace(" ", "").uppercase()
            val cat = if (NumberRules.isValidIban(compact)) MaskCategory.IBAN else MaskCategory.SAYI
            out += ctx.span(m.range.first, m.range.last + 1, cat, m.value, compact)
        }
        coordinate.findAll(text).forEach { m ->
            out += ctx.span(m.range.first, m.range.last + 1, MaskCategory.ADRES, m.value, m.value.replace(" ", ""))
        }
        plate.findAll(text).forEach { m ->
            out += ctx.span(m.range.first, m.range.last + 1, MaskCategory.SAYI, m.value, TurkishFold.lower(m.value))
        }
        digitRun.findAll(text).forEach { m ->
            val digits = m.value.filter { it.isDigit() }
            val (cat, key) = NumberRules.classify(digits, minRun) ?: return@forEach
            out += ctx.span(m.range.first, m.range.last + 1, cat, m.value, key)
        }
        out += spokenNumbers(ctx)
        out += spokenEmails(ctx)
        return out
    }

    /** Runs of number words; masked when 3+ words, or 2 words giving 4+ digits (§5.1). */
    private fun spokenNumbers(ctx: MaskContext): List<Span> {
        val out = ArrayList<Span>()
        for (run in numberWordRuns(ctx)) {
            val words = run.size
            val digits = SpokenNumbers.toDigits(run.map { it.second.value })
            val longEnough = words >= ctx.dict.settings.numberWordThreshold ||
                (words >= 2 && digits.length >= ctx.dict.settings.minDigitRun)
            if (!longEnough) continue
            val (cat, key) = NumberRules.classify(digits, 1) ?: continue
            val first = run.first().first
            val lastHit = run.last().second
            val value = ctx.surface(first.index, lastHit.stemEnd)
            out += ctx.span(first.coreStart, lastHit.stemEnd, cat, value, key, lastHit.fusedSuffix)
        }
        return out
    }

    /** Consecutive number-word tokens; a fused or written ending closes the run. */
    fun numberWordRuns(ctx: MaskContext): List<List<Pair<Token, NumberWordHit>>> {
        val runs = ArrayList<List<Pair<Token, NumberWordHit>>>()
        var current = ArrayList<Pair<Token, NumberWordHit>>()
        for (t in ctx.tokens) {
            val hit = ctx.numberWord(t)
            if (hit == null) {
                if (current.isNotEmpty()) runs += current
                current = ArrayList(); continue
            }
            current += t to hit
            if (hit.fusedSuffix != null || t.baseEnd < t.coreEnd || t.hasTrailingPunctuation) {
                runs += current; current = ArrayList()
            }
        }
        if (current.isNotEmpty()) runs += current
        return runs
    }

    /** "ayşe nokta test et example nokta com (nokta tr)" -> one EPOSTA span (§5.2). */
    private fun spokenEmails(ctx: MaskContext): List<Span> {
        val out = ArrayList<Span>()
        val toks = ctx.tokens
        for (i in toks.indices) {
            if (toks[i].fold != "et" && toks[i].fold != "at") continue
            var j = i + 1
            if (j >= toks.size || !toks[j].hasLetter) continue
            var last = -1
            j++
            while (j + 1 < toks.size && toks[j].fold == "nokta" && toks[j + 1].fold in ctx.dict.emailTlds) {
                last = j + 1
                j += 2
            }
            if (last < 0) continue
            var first = i
            var k = i - 1
            while (k >= 0 && i - k <= ctx.dict.settings.emailMaxLeftWords &&
                !ctx.isKnown(toks[k]) && !toks[k].hasTrailingPunctuation) {
                first = k; k--
            }
            val value = ctx.surface(first, toks[last].baseEnd)
            out += ctx.span(toks[first].coreStart, toks[last].baseEnd, MaskCategory.EPOSTA, value, TurkishFold.lower(value))
        }
        return out
    }
}
