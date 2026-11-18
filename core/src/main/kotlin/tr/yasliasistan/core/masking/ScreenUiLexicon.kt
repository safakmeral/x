package tr.yasliasistan.core.masking

/**
 * What the screen masker may treat as the app's own interface text (docs/MASKELEME.md §6, PLAN G-32).
 *
 * - [phrases]: interface texts known to be written by an app (Settings menu titles, tabs, common
 *   buttons). Only nouns and commands that never identify a person; reply words such as "Tamam"
 *   or "Merhaba" are left out on purpose, because they are also typical message bodies.
 * - [trustedUiPackages]: apps whose control labels (buttons, switches outside lists) are trusted as
 *   interface text. In every other app a control label must be a known phrase or a toggle-like
 *   control, otherwise it is masked as content: chat titles there are clickable texts too.
 *
 * Everything not covered here is masked: unnecessary masking is preferred to missing masking.
 */
data class ScreenUiLexicon(
    val phrases: List<String>,
    val trustedUiPackages: Set<String>,
) {
    private val folded: Set<String> = phrases.mapTo(HashSet()) { key(it) }

    internal fun isUiPhrase(text: String) = key(text) in folded

    companion object {
        private val SPACES = Regex("\\s+")

        internal fun key(text: String) = TurkishFold.fold(text.trim().trimEnd('.', ':', '…')).replace(SPACES, " ")

        val DEFAULT = ScreenUiLexicon(
            phrases = listOf(
                // Settings (Samsung One UI and stock Android, Turkish).
                "Ayarlar", "Bağlantılar", "Bağlı cihazlar", "Modlar ve Rutinler", "Sesler ve titreşim",
                "Ses ve titreşim", "Bildirimler", "Ekran", "Pil", "Pil ve cihaz bakımı", "Duvar kağıdı ve stil",
                "Temalar", "Ana ekran", "Kilit ekranı", "Kilit ekranı ve AOD", "Güvenlik ve gizlilik", "Konum",
                "Güvenlik ve acil durum", "Hesaplar ve yedekleme", "Gelişmiş özellikler", "Dijital denge",
                "Dijital denge ve ebeveyn denetimleri", "Cihaz bakımı", "Uygulamalar", "Genel yönetim",
                "Erişilebilirlik", "Yazılım güncelleme", "Telefon hakkında", "İpuçları ve kullanım kılavuzu",
                "Wi-Fi", "Bluetooth", "Mobil veri", "Uçak modu", "Mobil ağlar", "Veri kullanımı", "SIM yöneticisi",
                "Mobil Hotspot ve Bağlantı Paylaşımı", "NFC ve temassız ödemeler", "Ses düzeyi", "Titreşim",
                "Zil sesi", "Bildirim sesi", "Sistem sesi", "Ses modu", "Sessiz", "Parlaklık", "Uyarlanır parlaklık",
                "Yazı tipi boyutu ve stili", "Yazı tipi boyutu", "Ekran yakınlaştırma", "Ekran zaman aşımı",
                "Karanlık mod", "Açık mod", "Göz konforu kalkanı", "Dil", "Dil ve giriş", "Tarih ve saat",
                "Klavye", "Sıfırla", "Yazılım bilgileri", "Durum bilgileri", "Pil bilgileri", "Görüş", "İşitme",
                "Etkileşim ve beceri", "Yüksek kontrast", "Büyütme", "Şifre ve güvenlik", "Kullanıcılar",
                "Depolama", "Bellek", "Rahatsız etmeyin", "Gizlilik", "İzin yöneticisi", "Varsayılan uygulamalar",
                "Cihaz adı", "Telefon adı", "Model adı", "Model numarası", "Seri numarası", "Telefon numarası",
                "Android sürümü", "One UI sürümü", "Kullanılabilir ağlar", "Mevcut ağ", "Kayıtlı ağlar",
                // Common tabs, titles and buttons of the allowed apps (V-14).
                "Sohbetler", "Durum", "Aramalar", "Topluluklar", "Güncellemeler", "Kişiler", "Favoriler",
                "Son aramalar", "Tuş takımı", "Albümler", "Resimler", "Hikayeler", "Alarm", "Alarm ekle",
                "Dünya saati", "Kronometre", "Zamanlayıcı", "Ara", "Arama", "Gönder", "Mesaj yazın", "Mesaj",
                "Mesajlar", "Yeni sohbet", "Yeni mesaj", "Yeni kişi", "Kişi ekle", "Paylaş", "Sil", "Düzenle",
                "Kaydet", "İptal", "Geri", "Kapat", "Ekle", "Daha fazla seçenek", "Diğer seçenekler",
                "Gezinme menüsü", "Yukarı git", "Fotoğraf", "Video", "Telefon", "Kamera", "Galeri", "Saat",
                "Hava durumu", "Sesli arama", "Görüntülü arama", "Ekli dosya", "Emoji", "Sesli mesaj",
            ),
            trustedUiPackages = setOf(
                "com.android.settings",
                "com.sec.android.app.clockpackage", "com.google.android.deskclock",
                "com.sec.android.app.camera", "com.google.android.GoogleCamera",
                "com.sec.android.daemonapp",
            ),
        )
    }
}
