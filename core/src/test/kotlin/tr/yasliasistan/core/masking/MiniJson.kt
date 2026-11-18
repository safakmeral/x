package tr.yasliasistan.core.masking

/**
 * Tiny JSON reader for test fixtures (objects -> LinkedHashMap, arrays -> List, numbers -> Long/Double).
 * Avoids adding a JSON library to `:core` only for reading the masking corpus.
 */
internal class MiniJson private constructor(private val s: String) {
    private var i = 0

    private fun value(): Any? {
        ws()
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> { expect("true"); true }
            'f' -> { expect("false"); false }
            'n' -> { expect("null"); null }
            else -> if (c == '-' || c.isDigit()) num() else error("Unexpected '$c' at $i")
        }
    }

    private fun obj(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        i++; ws()
        if (s[i] == '}') { i++; return m }
        while (true) {
            ws(); val k = str(); ws(); check(s[i++] == ':')
            m[k] = value(); ws()
            when (s[i++]) { ',' -> continue; '}' -> return m; else -> error("Bad object at $i") }
        }
    }

    private fun arr(): List<Any?> {
        val l = ArrayList<Any?>()
        i++; ws()
        if (s[i] == ']') { i++; return l }
        while (true) {
            l += value(); ws()
            when (s[i++]) { ',' -> continue; ']' -> return l; else -> error("Bad array at $i") }
        }
    }

    private fun str(): String {
        check(s[i++] == '"')
        val sb = StringBuilder()
        while (true) {
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> when (val e = s[i++]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                    'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> sb.append(e)
                }
                else -> sb.append(c)
            }
        }
    }

    private fun num(): Any {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        val t = s.substring(start, i)
        return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
    }

    private fun expect(word: String) { check(s.startsWith(word, i)); i += word.length }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    companion object {
        fun parse(text: String): Any? = MiniJson(text).value()
    }
}
