package tr.yasliasistan.core.masking

// GENERATED from core/src/test/resources/mask-corpus.json ("sozlukler" + "ayarlar"),
// which docs/MASKELEME.md declares binding. Regenerate with
// `python tools/gen_masking_lexicon.py core/src/test/resources/mask-corpus.json <this file>`.
// Do not edit by hand: MaskingLexiconTest fails
// when this file and the corpus drift apart.

/** The default Turkish lexicon of docs/MASKELEME.md. */
internal val DefaultMaskingLexicon = MaskingLexicon(
    commandWords = listOf(
        "acaba", "acil", "adres", "adı", "aktar", "akşam", "al", "alarm", "alt", "alıcı", "ama",
        "ambulans", "ana", "ara", "aradı", "arama", "aramak", "aramış", "arar", "arasana", "aray",
        "arayın", "artır", "at", "ayar", "ayarla", "az", "azalt", "aç", "açar", "açsana", "açık", "açıl",
        "aşağı", "bak", "bakar", "bakkal", "bana", "bas", "bağla", "bağlan", "ben", "beni", "benim",
        "bildirim", "bir", "biraz", "biri", "bluetooth", "bu", "bugün", "bul", "bunu", "buraya", "büyük",
        "büyüt", "cevapsız", "cuma", "cumartesi", "da", "daha", "dakika", "de", "defa", "değil",
        "değiştir", "dinle", "diye", "doktor", "dokun", "durum", "dön", "dün", "düğme", "e-posta",
        "eczane", "ekle", "ekran", "en", "eposta", "eski", "et", "evet", "evim", "forma", "fotoğraf",
        "galeri", "gece", "gelen", "geri", "gibi", "giden", "gir", "git", "gönder", "görüntülü", "göster",
        "güncelle", "hafta", "hangi", "harita", "hastane", "hatırlat", "hatırlatıcı", "hava", "hayır",
        "hem", "hemen", "hepsi", "her", "hesap", "ile", "ilet", "ilk", "indir", "internet", "itfaiye",
        "için", "kaldır", "kamera", "kapa", "kapat", "kapıcı", "kargo", "kayded", "kaydet", "kayıt",
        "kere", "kez", "ki", "kim", "kimi", "kimin", "kimlik", "kişi", "komşu", "kontrol", "konum",
        "kopyala", "kur", "kurar", "kutu", "küçük", "küçült", "kıs", "kısar", "link", "lira", "liste",
        "lütfen", "mail", "market", "menü", "merhaba", "merkez", "mesaj", "mi", "misin", "mod", "mu",
        "musun", "mü", "mı", "mısın", "nasıl", "ne", "neden", "nerede", "neydi", "niye", "numara", "o",
        "oku", "okur", "olarak", "ona", "onu", "oraya", "parlaklık", "paylaş", "pazar", "pazartesi",
        "perşembe", "poliklinik", "polis", "posta", "randevu", "rehber", "resim", "saat", "saatini",
        "sabah", "salı", "sana", "saniye", "sayfa", "sağ", "sen", "seni", "ses", "sesi", "sesli", "seç",
        "sil", "sms", "sol", "son", "sonra", "sonuç", "sorgula", "söyle", "taksi", "takvim", "tamam",
        "tarif", "tc", "telefon", "titreşim", "tuş", "tuşla", "tüm", "uygulama", "uyku", "uçak", "var",
        "ve", "ver", "veya", "video", "whatsapp", "wifi", "ya", "yakın", "yap", "yapar", "yardım", "yarın",
        "yaz", "yazar", "yazı", "yeni", "yok", "yolla", "yukarı", "yükselt", "zil", "çal", "çarşamba",
        "çağır", "çek", "çok", "önce", "öğle", "üst", "şarj", "şimdi", "şu", "şunu", "şöyle",
    ),
    kinship = listOf(
        "anne", "baba", "oğul", "oğl", "kız", "torun", "gelin", "damat", "eş", "babaanne", "anneanne",
        "kardeş",
    ),
    honorifics = listOf(
        "hanım", "bey", "abla", "abi", "ağabey", "amca", "dayı", "teyze", "hala", "enişte", "yenge",
        "dede", "nine", "hoca", "usta", "efendi",
    ),
    suffixes = mapOf(
        "'i" to listOf("ı", "i", "u", "ü", "yı", "yi", "yu", "yü", "nı", "ni", "nu", "nü", "mı", "mi", "mu", "mü", "ımı", "imi", "umu", "ümü", "sını", "sini", "sunu", "sünü", "leri", "ları", "gili"),
        "'e" to listOf("a", "e", "ya", "ye", "na", "ne", "ma", "me", "ıma", "ime", "uma", "üme", "sına", "sine", "suna", "süne", "lere", "lara", "gile"),
        "'de" to listOf("da", "de", "ta", "te", "nda", "nde", "mda", "mde", "ımda", "imde", "umda", "ümde", "lerde", "larda", "gilde"),
        "'den" to listOf("dan", "den", "tan", "ten", "ndan", "nden", "mdan", "mden", "ımdan", "imden", "umdan", "ümden", "lerden", "lardan", "gilden"),
        "'in" to listOf("ın", "in", "un", "ün", "nın", "nin", "nun", "nün", "mın", "min", "mun", "mün", "ımın", "imin", "umun", "ümün", "sının", "sinin", "lerin", "ların", "gilin"),
        "'le" to listOf("la", "le", "yla", "yle", "ıyla", "iyle", "uyla", "üyle", "mla", "mle", "ımla", "imle", "umla", "ümle", "lerle", "larla", "gille"),
        "" to listOf("m", "ım", "im", "um", "üm", "cığım", "ciğim", "cuğum", "cüğüm", "cım", "cim", "cum", "cüm", "gil", "giller", "ler", "lar", "lerim", "larım", "sı", "si", "su", "sü", "mız", "miz"),
    ),
    softening = mapOf('p' to 'b', 'ç' to 'c', 't' to 'd', 'k' to 'ğ'),
    personSlotTriggers = mapOf(
        "'i" to listOf("ara", "arar", "arasana", "arayın", "arayabilir", "aramak", "görüntülü"),
        "'e" to listOf("mesaj", "sms"),
        "'in" to listOf("numarası", "numarasını"),
    ),
    dictationBefore = listOf(
        "diye",
    ),
    dictationAfter = listOf(
        "de ki", "yaz ki", "söyle ki", "şunu yaz", "şöyle yaz",
    ),
    dictationColonWords = listOf(
        "mesaj", "yaz", "de", "söyle", "not",
    ),
    dictationVerbs = listOf(
        "mesaj", "yaz", "at", "gönder", "yolla", "söyle", "de", "sms", "whatsapp",
    ),
    addressAnchors = listOf(
        "mahalle", "mahallesi", "mah", "mah.", "sokak", "sokağı", "sokağa", "sok", "sok.", "sk", "sk.",
        "cadde", "caddesi", "cad", "cad.", "cd", "cd.", "bulvar", "bulvarı", "apartman", "apartmanı",
        "apt", "apt.", "site", "sitesi", "blok", "bloğu", "köy", "köyü",
    ),
    addressNumberMarkers = listOf(
        "no", "no:", "numara", "kat", "daire", "d:", "daire:", "blok",
    ),
    numberWords = mapOf(
        "sıfır" to 0L,
        "bir" to 1L,
        "iki" to 2L,
        "üç" to 3L,
        "dört" to 4L,
        "beş" to 5L,
        "altı" to 6L,
        "yedi" to 7L,
        "sekiz" to 8L,
        "dokuz" to 9L,
        "on" to 10L,
        "yirmi" to 20L,
        "otuz" to 30L,
        "kırk" to 40L,
        "elli" to 50L,
        "altmış" to 60L,
        "yetmiş" to 70L,
        "seksen" to 80L,
        "doksan" to 90L,
        "yüz" to 100L,
        "bin" to 1000L,
        "milyon" to 1000000L,
    ),
    sensitiveRoots = listOf(
        "ilaç", "ilac", "tansiyon", "insülin", "insulin", "diyabet", "kolesterol", "kalp", "kanser",
        "kemoterapi", "diyaliz", "astım", "alzheimer", "demans", "parkinson", "felç", "depresyon",
        "antidepresan", "psikiyatri", "onkoloji", "kardiyoloji", "nöroloji", "üroloji", "jinekoloji",
        "tahlil", "tomografi", "ameliyat", "reçete", "hastalık", "hastalığ", "hastayım", "hepatit",
        "gebelik", "hamile", "genetik", "namaz", "oruç", "oruc", "cami", "kilise", "sinagog", "cemevi",
        "tarikat", "mezhep", "imsakiye", "sahur", "iftar", "kandil", "sendika", "dernek", "vakıf",
        "sabıka", "cezaevi", "mahkeme", "hapis",
    ),
    sensitivePhrases = listOf(listOf("şeker", "hastal"), listOf("kalp", "kriz"), listOf("kan", "tahlil"), listOf("engelli", "rapor"), listOf("işitme", "cihaz")),
    secretPhrases = listOf(listOf("doğrulama", "kod"), listOf("tek", "kullanımlık"), listOf("kimseyle", "paylaşma"), listOf("kimse", "ile", "paylaşma")),
    secretWithDigits = listOf(
        "şifre", "parola", "pin", "cvv", "cvc",
    ),
    codeWithDigits = listOf(
        "kod", "otp",
    ),
    codeExceptions = listOf(listOf("posta", "kod")),
    statusWords = listOf(
        "Açık", "Kapalı", "Cevapsız", "Gelen", "Giden", "Fotoğraf", "Video", "Sesli mesaj", "Okundu",
        "Dün", "Bugün", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar",
        "Bağlı değil", "Bağlı", "Varsayılan", "Otomatik", "Yok",
    ),
    emailTlds = listOf(
        "com", "net", "org", "tr", "edu", "gov", "info", "co",
    ),
    settings = MaskingSettings(
        intentModeWordThreshold = 15,
        minDigitRun = 4,
        numberWordThreshold = 3,
        contextMaxWords = 2,
        addressMaxLeftWords = 3,
        emailMaxLeftWords = 4,
        dictionaryPrefixMinLength = 4,
        secretMinDigits = 3,
    ),
)
