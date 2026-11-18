package tr.yasliasistan.core.masking

/** Typed view of core/src/test/resources/mask-corpus.json (docs/MASKELEME.md §9). */
@Suppress("UNCHECKED_CAST")
internal object MaskCorpus {
    val root: Map<String, Any?> by lazy {
        val text = MaskCorpus::class.java.getResourceAsStream("/mask-corpus.json")!!
            .readBytes().toString(Charsets.UTF_8)
        MiniJson.parse(text) as Map<String, Any?>
    }

    val examples: List<Map<String, Any?>> get() = root["ornekler"] as List<Map<String, Any?>>
    val dictionaries: Map<String, Any?> get() = root["sozlukler"] as Map<String, Any?>
    val settings: Map<String, Any?> get() = root["ayarlar"] as Map<String, Any?>

    val contacts: List<ContactName> by lazy {
        (root["rehber"] as List<Map<String, Any?>>).map { ContactName(it["id"] as String, it["ad"] as String) }
    }
    val contactPhones: List<String> by lazy {
        (root["rehber"] as List<Map<String, Any?>>).flatMap { it["telefonlar"] as List<String> }
    }
    val contactIndex: ContactIndex by lazy { ContactIndex.build(contacts) }

    fun Map<String, Any?>.str(key: String) = this[key] as String
    fun Map<String, Any?>.obj(key: String) = this[key] as Map<String, Any?>
    fun Map<String, Any?>.strMap(key: String) = (this[key] as Map<String, Any?>?).orEmpty().mapValues { it.value as String }
    fun Map<String, Any?>.strList(key: String) = (this[key] as List<Any?>?).orEmpty().map { it as String }

    /** Command examples: plain `komut` and the command part of `birlesik` (G-31 scope). */
    fun commandCases(): List<Map<String, Any?>> = examples.filter {
        it["tur"] == "komut" || (it["tur"] == "birlesik" && it.obj("beklenen")["sonuc"] == "GONDER")
    }

    fun source(e: Map<String, Any?>) = if (e["kaynak"] == "yazi") InputSource.TYPED else InputSource.SPEECH

    val placeholderRegex = Regex("\\[[A-Z]+_\\d+\\]")
}
