package tr.yasliasistan.core.masking

sealed interface UnmaskResult {
    data class Restored(val text: String) : UnmaskResult {
        override fun toString() = "Restored(length=${text.length})"
    }

    /** The answer named a placeholder that was never sent (or a malformed one): no action. */
    data class Rejected(val reason: BlockReason) : UnmaskResult
}

/**
 * Decodes placeholders in a model answer on the device (docs/MASKELEME.md §8). The ending after
 * a placeholder is rebuilt for the real value with Turkish vowel harmony, buffer letters and
 * consonant hardening: "Osman Test" + 'den -> "Osman Test'ten", "0599 000 00 14" + 'i -> "...14'ü".
 */
internal object ResponseDecoder {
    private val anyBracket = Regex("\\[[^\\[\\]]*\\]")
    private val placeholderWithEnding = Regex("\\[([A-Z]+_\\d+)\\](?:['’]([\\p{L}]+))?")

    fun decode(response: String, table: PlaceholderTable, dict: MaskingDictionary): UnmaskResult {
        if (anyBracket.findAll(response).any { table.valueOf(it.value) == null }) {
            return UnmaskResult.Rejected(BlockReason.BILINMEYEN_YER_TUTUCU)
        }
        val restored = placeholderWithEnding.replace(response) { m ->
            val placeholder = "[" + m.groupValues[1] + "]"
            val value = table.valueOf(placeholder)!!
            val ending = m.groupValues[2]
            when {
                ending.isEmpty() -> value
                table.hasSuffixInValue(placeholder) -> value
                else -> {
                    val cls = dict.caseClassOf(ending)
                    if (cls == null) "$value'$ending" else value + TurkishSuffix.forValue(value, cls)
                }
            }
        }
        return UnmaskResult.Restored(restored)
    }
}

/** Builds a case ending for a value (§8). */
internal object TurkishSuffix {
    private const val BACK = "aıou"
    private const val FRONT = "eiöü"
    private const val HARD = "fstkçşhp"
    private val units = listOf("sıfır", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val tens = listOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")

    /** "'i", "'e", "'de", "'den", "'in", "'le" or "" -> written ending with apostrophe. */
    fun forValue(value: String, caseClass: String): String {
        if (caseClass.isEmpty()) return ""
        val word = TurkishFold.lower(lastSpokenWord(value.trimEnd()))
        val lastVowel = word.lastOrNull { it in BACK || it in FRONT } ?: 'e'
        val endsInVowel = word.lastOrNull()?.let { it in BACK || it in FRONT } ?: false
        val hard = word.lastOrNull()?.let { it in HARD } ?: false
        val a = if (lastVowel in BACK) "a" else "e"
        val i = when (lastVowel) { 'a', 'ı' -> "ı"; 'e', 'i' -> "i"; 'o', 'u' -> "u"; else -> "ü" }
        val d = if (hard) "t" else "d"
        val ending = when (caseClass) {
            "'i" -> (if (endsInVowel) "y" else "") + i
            "'e" -> (if (endsInVowel) "y" else "") + a
            "'de" -> d + a
            "'den" -> d + a + "n"
            "'in" -> (if (endsInVowel) "n" else "") + i + "n"
            "'le" -> (if (endsInVowel) "y" else "") + "l" + a
            else -> return ""
        }
        return "'$ending"
    }

    /** The word a listener hears last: for "... 00 14" that is "dört" (on dört). */
    private fun lastSpokenWord(value: String): String {
        val digits = value.takeLastWhile { it.isDigit() }
        if (digits.isEmpty()) return value.takeLastWhile { it.isLetter() }
        if (digits.length > 1 && digits[0] == '0') return units[digits.last() - '0']
        if (digits.length > 15) return units[digits.last() - '0']
        val n = digits.toLong()
        return when {
            n == 0L -> units[0]
            n % 10 != 0L -> units[(n % 10).toInt()]
            n % 100 != 0L -> tens[((n % 100) / 10).toInt()]
            n % 1000 != 0L -> "yüz"
            n % 1_000_000 != 0L -> "bin"
            n % 1_000_000_000 != 0L -> "milyon"
            else -> "milyar"
        }
    }
}
