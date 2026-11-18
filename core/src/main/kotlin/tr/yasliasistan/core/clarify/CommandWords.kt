package tr.yasliasistan.core.clarify

/**
 * Word lists of the known intents (PLAN G-37). Tokens are normalized with TurkishText
 * (lower case, ASCII-folded, apostrophe endings removed: "Ayşe'yi" -> "ayse").
 */
internal object CommandWords {

    /** Endings after a consonant-final verb stem ("yaz-ar", "gönder-in", "at-abilir misin"). */
    private val consonantEndings = setOf(
        "", "ar", "er", "ir", "in", "iniz", "sana", "sene", "saniza", "senize",
        "abilir", "ebilir", "abilirmisin", "ebilirmisin", "abilirmisiniz", "ebilirmisiniz",
        "armisin", "ermisin", "irmisin", "armisiniz", "ermisiniz", "irmisiniz",
        "mak", "mek", "alim", "elim", "iver", "iverin", "ayim", "eyim", "sin", "siniz",
    )

    /** Endings after a vowel-final verb stem ("ara-r", "ara-yın", "yolla-yabilir"). */
    private val vowelEndings = setOf(
        "", "r", "yin", "yiniz", "sana", "sene", "saniza", "senize",
        "yabilir", "yebilir", "yabilirmisin", "yebilirmisin", "yabilirmisiniz", "yebilirmisiniz",
        "rmisin", "rmisiniz", "mak", "mek", "yalim", "yelim", "yiver", "yiverin", "yayim", "yeyim", "sin", "siniz",
    )

    private fun isVerb(token: String, stems: List<String>): Boolean = stems.any { stem ->
        token.startsWith(stem) && token.substring(stem.length) in (if (stem.last() in VOWELS) vowelEndings else consonantEndings)
    }

    fun isCallVerb(token: String) = isVerb(token, listOf("ara"))

    fun isMessageVerb(token: String) = isVerb(token, listOf("yaz", "gonder", "yolla", "at", "cek", "ilet"))

    private val messageNouns = setOf("mesaj", "mesaji", "mesajini", "mesajla", "ileti", "iletisi", "kisa")

    fun isMessageNoun(token: String) = token in messageNouns

    /** "fotoğraf", "fotoğrafı", "foto", "resim", "resmi", "resmimi" (demo: send a photo). */
    fun isPhotoNoun(token: String) =
        token.startsWith("fotograf") || token.startsWith("foto") || token.startsWith("resim") || token.startsWith("resmi")

    /** "son fotoğrafı", "en son çektiğim resmi", "galeriden": words around the photo, never a name. */
    private val photoFillers = setOf("son", "sonu", "en", "cektigim", "cektigimi", "galeri", "galeriden", "yeni", "yeniden")

    fun isPhotoFiller(token: String) = token in photoFillers

    /** "Ben yapayım" mode words: "göster", "nasıl", "öğret", "ben yapayım/basayım", "yol göster". */
    fun isGuideWord(token: String) =
        token.startsWith("goster") || token == "nasil" || token.startsWith("ogret") ||
            token == "yapayim" || token == "basayim" || token == "kendim"

    /** Send verbs also in passive/question forms ("gönderilir", "gönderebilirim"). */
    fun isSendStem(token: String) =
        token.startsWith("gonder") || token.startsWith("yolla") || token.startsWith("atma") || token == "atayim" || token == "atar"

    fun isWhatsApp(token: String) = listOf("whatsap", "watsap", "vatsap", "wasap", "vatsap").any(token::startsWith)

    fun isBusiness(token: String) = token.startsWith("business") || token.startsWith("biznis")

    fun isSms(token: String) = token.startsWith("sms") || token.startsWith("mesajlar")

    /** Words that are never part of a person's name in a call/message command. */
    private val fillers = setOf(
        "lutfen", "bana", "benim", "bizim", "bir", "icin", "hemen", "simdi", "su", "sunu", "suna", "o", "onu", "ona",
        "bu", "bunu", "buna", "de", "da", "ki", "mi", "mu", "misin", "musun", "misiniz", "musunuz", "hadi", "haydi",
        "ve", "ile", "olan", "olani", "olana", "kisi", "kisiyi", "kisiye", "acaba", "efendim", "rehberden",
        "rehberdeki", "telefon", "telefonla", "telefondan", "telefonu", "telefonunu", "numara", "numarasi",
        "numarasini", "numarasina", "uzerinden", "araciligiyla", "tekrar", "yeniden",
    )

    fun isFiller(token: String) = token in fillers

    /** Kinds of video/voice call through an app; not handled by rules (left to the language model). */
    fun isAppCallWord(token: String) = token.startsWith("goruntulu") || isWhatsApp(token)

    // --- Settings ---

    private val volumeNouns = setOf("ses", "sesi", "sesini", "sesin", "sesler", "sesleri", "seslerini")

    fun isVolumeNoun(token: String) = token in volumeNouns

    fun isBrightnessNoun(token: String) = token == "parlaklik" || token.startsWith("parlaklig")

    private val textNouns = setOf("yazi", "yazilar", "yazilari", "yaziyi", "yazilarin", "yazinin", "harf", "harfler", "harfleri")

    fun isTextNoun(token: String) = token in textNouns

    fun isSizeWord(token: String) = isVerb(token, listOf("buyut", "kucult")) || token.startsWith("boyut")

    /** Direction: true = more, false = less, null = not said. */
    fun direction(tokens: List<String>): Boolean? {
        val up = tokens.any { isVerb(it, listOf("ac", "artir", "yukselt", "cogalt", "buyut")) }
        val down = tokens.any { isVerb(it, listOf("kis", "azalt", "dusur", "alcalt", "kucult")) }
        return when {
            up == down -> null
            else -> up
        }
    }

    /** "Kapat" (mute / switch off) is not a volume step; left to later layers. */
    fun isOffVerb(token: String) = isVerb(token, listOf("kapat", "sustur"))

    // --- Weather ---

    fun isWeather(tokens: List<String>): Boolean {
        val i = tokens.indexOf("hava")
        if (i < 0) return false
        val next = tokens.getOrNull(i + 1) ?: return false
        return next.startsWith("durum") || next.startsWith("nasil") || next.startsWith("tahmin")
    }

    fun isWeatherApp(label: List<String>) = label.any { it.startsWith("hava") || it.startsWith("weather") }

    private const val VOWELS = "aeiou"
}
