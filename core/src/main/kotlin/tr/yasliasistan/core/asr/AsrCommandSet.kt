package tr.yasliasistan.core.asr

/**
 * The 40 Turkish test commands of the speech-recognition measurement (G-13/G-14). All names,
 * numbers and contents are made up (no personal data). The same list is printed for reading aloud
 * in docs/olcum/komut-listesi.md; the ids name the recorded files (k01.wav ...).
 *
 * Mix: short app commands, calls/messages with names (apostrophe suffixes), settings, numbers and
 * times (to exercise number normalization), Guide-mode questions, and the fixed control words.
 */
object AsrCommandSet {

    data class Command(val id: String, val text: String)

    val COMMANDS: List<Command> = listOf(
        "Ayarları aç",
        "WhatsApp'ı aç",
        "Kamerayı aç",
        "Galeriyi aç ve son fotoğrafı göster",
        "Ayşe'yi ara",
        "Mehmet'e mesaj gönder",
        "Kızıma mesaj yaz, akşam yemeğe geliyorum de",
        "Oğlumu görüntülü ara",
        "Fatma Teyze'ye sesli mesaj gönder",
        "Son gelen mesajı oku",
        "Ekranın parlaklığını biraz artır lütfen",
        "Yazıları büyüt",
        "Sesi kıs",
        "Zil sesini aç",
        "Wi-Fi'yi kapat",
        "Bluetooth'u aç",
        "Fener'i yak",
        "Yarın sabah yedide alarm kur",
        "Saat on beş otuzda ilacımı hatırlat",
        "Beş dakika sonra beni uyar",
        "Bugün hava nasıl olacak",
        "Saat kaç",
        "Bugün günlerden ne",
        "Sıfır beş yüz elli beş, yüz on bir, yirmi iki, otuz üç numarayı ara",
        "Telefonumun şarjı yüzde kaç",
        "Ali'nin numarasını kaydet",
        "Rehberde Zeynep'i bul",
        "Son aramaları göster",
        "Cevapsız aramaları göster",
        "WhatsApp'ta Hasan'a fotoğraf gönder",
        "Bu nasıl yapılır, bana göster",
        "Ben yapayım, sen bana yol göster",
        "Sen yap",
        "Bu düğme ne işe yarıyor",
        "Neden bunu yaptın",
        "Baştan anlat",
        "Tamam, hatırladım",
        "Evet, onaylıyorum",
        "Hayır, vazgeç",
        "Dur",
    ).mapIndexed { index, text -> Command("k" + (index + 1).toString().padStart(2, '0'), text) }
}
