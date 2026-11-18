package tr.yasliasistan.core.masking

/** Check digits and digit-string classification (docs/MASKELEME.md §5.2). */
internal object NumberRules {

    fun isValidTckn(d: String): Boolean {
        if (d.length != 11 || d[0] == '0' || !d.all { it.isDigit() }) return false
        val n = d.map { it - '0' }
        val odd = n[0] + n[2] + n[4] + n[6] + n[8]
        val even = n[1] + n[3] + n[5] + n[7]
        val c10 = ((odd * 7 - even) % 10 + 10) % 10
        val c11 = (n.take(10).sum()) % 10
        return n[9] == c10 && n[10] == c11
    }

    fun isValidLuhn(d: String): Boolean {
        if (d.isEmpty() || !d.all { it.isDigit() }) return false
        var sum = 0
        d.reversed().forEachIndexed { i, ch ->
            var v = ch - '0'
            if (i % 2 == 1) { v *= 2; if (v > 9) v -= 9 }
            sum += v
        }
        return sum % 10 == 0
    }

    /** [compact] = "TR" + 24 digits, upper case, no spaces. */
    fun isValidIban(compact: String): Boolean {
        if (compact.length != 26) return false
        val rearranged = compact.substring(4) + compact.substring(0, 4)
        var rem = 0
        for (c in rearranged) {
            val part = if (c.isDigit()) (c - '0').toString() else (c.uppercaseChar() - 'A' + 10).toString()
            for (p in part) rem = (rem * 10 + (p - '0')) % 97
        }
        return rem == 1
    }

    /** Turkish phone number reduced to its 10 national digits, or null. */
    fun phoneKey(d: String): String? {
        if (d.length == 7 && d.startsWith("444")) return d
        val national = when {
            d.length == 14 && d.startsWith("0090") -> d.substring(4)
            d.length == 12 && d.startsWith("90") -> d.substring(2)
            d.length == 11 && d.startsWith("0") -> d.substring(1)
            d.length == 10 -> d
            else -> return null
        }
        return if (national[0] in '2'..'5') national else null
    }

    /** Category of a digit string of a number (written or spoken); null when it stays visible. */
    fun classify(digits: String, minRun: Int): Pair<MaskCategory, String>? {
        phoneKey(digits)?.let { return MaskCategory.TEL to it }
        if (digits.length == 11 && digits[0] != '0') {
            return if (isValidTckn(digits)) MaskCategory.TCKN to digits else MaskCategory.SAYI to digits
        }
        if (digits.length in 13..19 && isValidLuhn(digits)) return MaskCategory.KART to digits
        return if (digits.length >= minRun) MaskCategory.SAYI to digits else null
    }
}

/**
 * Spoken numbers (§5.1): consecutive number words are resolved greedily; a word that cannot
 * continue the current number starts a new digit group, "sıfır" is always a group of its own.
 * "sıfır beş yüz doksan dokuz" -> "0" + "599".
 */
internal object SpokenNumbers {
    private enum class Last { NONE, UNIT, TEN, HUNDRED, THOUSAND, MILLION }

    fun toDigits(values: List<Long>): String {
        val out = StringBuilder()
        var big = 0L
        var small = 0L
        var last = Last.NONE
        var open = false
        fun flush() {
            if (open) out.append(big + small)
            big = 0; small = 0; last = Last.NONE; open = false
        }
        for (v in values) {
            when {
                v == 0L -> { flush(); out.append('0') }
                v < 10 -> {
                    if (last == Last.UNIT) flush()
                    small += v; last = Last.UNIT
                }
                v < 100 -> {
                    if (last == Last.UNIT || last == Last.TEN) flush()
                    small += v; last = Last.TEN
                }
                v == 100L -> {
                    val canMultiply = last == Last.UNIT && small < 10
                    if (!(last == Last.NONE || last == Last.THOUSAND || last == Last.MILLION || canMultiply)) flush()
                    small = if (last == Last.UNIT && small < 10) small * 100 else small + 100
                    last = Last.HUNDRED
                }
                v == 1000L -> {
                    if (last == Last.THOUSAND || (big % 1_000_000L) != 0L) flush()
                    big += (if (small == 0L) 1 else small) * 1000; small = 0; last = Last.THOUSAND
                }
                else -> {
                    if (big != 0L || last == Last.MILLION) flush()
                    big += (if (small == 0L) 1 else small) * v; small = 0; last = Last.MILLION
                }
            }
            if (v != 0L) open = true
        }
        flush()
        return out.toString()
    }
}
