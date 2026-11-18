package tr.yasliasistan.core.asr

/**
 * Data-folder index for recorded clips (G-13): `manifest.tsv`, one clip per line,
 * `id <TAB> file.wav <TAB> condition <TAB> reference text`. Lines starting with '#' are comments;
 * an empty reference marks a clip without speech. The same format is written when the tool exports
 * its synthesized clips, so a folder of human recordings can be measured the same way.
 */
object AsrManifest {

    data class Entry(val id: String, val file: String, val condition: String, val reference: String)

    private val safeFile = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}\\.wav$", RegexOption.IGNORE_CASE)

    /** Parses [text]; returns entries and a list of human-readable problems (bad lines are skipped). */
    fun parse(text: String): Pair<List<Entry>, List<String>> {
        val entries = mutableListOf<Entry>()
        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val cells = line.split('\t')
            val number = index + 1
            when {
                cells.size < 3 -> problems += "satır $number: en az 3 sütun gerekli (id, dosya, koşul)"
                cells[0].isBlank() -> problems += "satır $number: id boş"
                !safeFile.matches(cells[1]) -> problems += "satır $number: dosya adı geçersiz (yalnız .wav, klasör yok)"
                !seen.add(cells[0]) -> problems += "satır $number: id tekrar ediyor"
                else -> entries += Entry(cells[0], cells[1], cells[2].ifBlank { "kayit" }, cells.getOrElse(3) { "" }.trim())
            }
        }
        return entries to problems
    }

    fun format(entries: List<Entry>): String = buildString {
        append("# id\tdosya\tkosul\treferans\n")
        entries.forEach { e ->
            val reference = e.reference.replace('\t', ' ').replace('\n', ' ')
            append(e.id).append('\t').append(e.file).append('\t').append(e.condition).append('\t').append(reference).append('\n')
        }
    }
}
