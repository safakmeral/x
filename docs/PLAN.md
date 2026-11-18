# Yaşlı Telefon Asistanı: Uygulama Planı

Sürüm: 2026-11-17 (**öğrenci tarafından onaylandı 17.11.2026**; kalan kararlar ana oturuma devredildi). Tek doğru kaynak: `docs/GEREKSINIMLER.md` (Rev.2). Bu plan gereksinim eklemez; çelişki görürse Bölüm 7'de raporlar.
Çalışma biçimi: `gelistirici` her çağrıda bu plandan **tek görev** (G-xx) uygular. Kilometre taşı (KT) sonunda `uyum-analisti` denetimi (UD-x) yapılır. Takvim yoktur; sıra bağımlılıklara göredir.

**Öncelik ilkesi:** "Hızlı ve etkili deneyim, az bekletme" her tasarım kararında birincil ölçüttür. İki seçenek gizlilik ve güvenlik açısından eşitse kullanıcıyı daha az bekleten seçilir.
**Bulut çağrısını azaltma ilkesi:** Her komutta önce cihaz içi kurallar, sonra görev belleği (kayıtlı tarifler) denenir; dil modeli yalnız bunlar çözemezse çağrılır. Bu hem hız hem ücretsiz kotanın korunması içindir.
**Sapma kuralı:** Belgelerden (hoca geri bildirimi, öğrenci belgeleri, makaleler) bir sapma ya da belgede olmayan bir karar olursa `docs/SAPMALAR.md` tablosuna bir satır eklenir (tarih | değişiklik | neden | koruma | ilgili).

---

## 1. Mimari özeti

**Platform:** Yalnız Android, Kotlin. minSdk 29 (Android 10, alt sınır değişmez). compileSdk ve targetSdk = 36 (Android 16), çünkü birincil cihaz **Samsung Galaxy A15, Android 16, 4 GB RAM**; hedef SDK cihazın sürümüyle aynı olunca Android 16'nın davranış değişiklikleri (kenardan kenara ekran, arka plan kısıtları) geliştirme sırasında doğrudan sınanır. AGP sürümü SDK 36'yı destekleyen kararlı sürümdür (G-01 doğrular). 4 GB RAM nedeniyle bellek bütçesi sıkıdır: ağır modeller tembel yüklenir ve oturum bitince boşaltılır. Cihaz içi büyük dil modeli yok (KD-13).

**Modüller (Gradle):**
| Modül | Tür | İçerik |
|---|---|---|
| `:core` | Saf Kotlin/JVM (Android'siz, hızlı birim testi) | diyalog durum makinesi, karar motoru (kural → kayıtlı tarif → dil modeli), netleştirme kuralları, risk sınıflandırıcı, maskeleme, eylem şeması, yanıt ayrıştırıcı, Türkçe metin normalleştirme, WER hesabı, metrik tipleri |
| `:speech` | Android kitaplığı | `SpeechEngine` arayüzü; `OnDeviceSystemEngine` (Android çevrimdışı tanıyıcı), `WhisperEngine` (whisper.cpp, JNI/NDK); basit enerji tabanlı konuşma algılayıcı (VAD) |
| `:app` | Android uygulaması | erişilebilirlik hizmeti (ekran okuma, dokunma/kaydırma), kaplamalar (baloncuk, Dur düğmesi, nokta/iz, vurgu), TTS, Room veritabanı, bulut istemcisi, Compose ekranları (kurulum, rıza, ayarlar, tarifler, verilerim) |
| `:testapp` | Yalnız hata ayıklama APK'sı | sahte açılır pencere, enjeksiyon, doğrulama kodu, sahte banka ekranları (güvenlik testleri) |
| `proxy/` | Cloudflare Worker (JavaScript, bağımlılıksız) | vekil sunucu: API anahtarı yalnız burada; Gemini → Groq yedeği; erişim kodu + kısa süreli hız sınırı (yalnız kötüye kullanıma karşı) |

**Veri akışı (bir görev):**
1. Baloncuğa dokunma → mikrofon yalnız şimdi açılır → konuşma **cihazda** yazıya çevrilir (ses bellekte işlenir, dosyaya yazılmaz, sonra atılır).
2. Diyalog: asistan anladığını tek cümleyle geri okur, onay bekler (FG-07). "Dur" her an çalışır.
3. Karar motoru: (a) **kurallar** cihaz verisine bakar (rehber, kurulu uygulamalar, ayarlar, ekran ağacı); rehberde tek eşleşme varsa soru yok. (b) **Kayıtlı tarif**: cihazdaki gömme modeliyle benzerlik araması (SQLite). (c) Sonuç yoksa ve **bulut rızası varsa** **dil modeli**: istek cihazda maskelenir, vekil sunucu üzerinden Gemini'ye (yedek Groq) gider; yanıt aynı çağrıda "eylem planı" veya "tek netleştirme sorusu" döner. Rıza yoksa asistan bunu sesle söyler (FG-103).
4. Her eylem, dil modelinden bağımsız **kural tabanlı risk sınıflandırıcıdan** geçer (Düşük/Orta/Yüksek); Orta ve Yüksek onay ister, sessizlik = hayır.
5. "Sen yap": erişilebilirlik hizmeti dokunur/kaydırır; nokta/iz gösterilir, her adım sesle anlatılır, sonra ekran beklenenle karşılaştırılır. "Ben yapayım": asistan dokunmaz, tek ögeyi vurgular, kullanıcının dokunuşunu bekler.
6. Başarılı, dil modeliyle çözülmüş görev kullanıcı onayıyla parametreli tarif olarak kaydedilir (kişisel içeriksiz).

**Cihazda kalan:** ham ses, ham transkript, ham ekran ağacı, rehber, maske eşleme tablosu (yer tutucu ↔ gerçek değer), kayıtlı tarifler, metrik günlüğü, denetim kopyaları, rıza kaydı, ayarlar. Ekran görüntüsü hiç alınmaz.
**Buluta giden (yalnız rıza varsa, TLS ile, vekil sunucu üzerinden):** maskelenmiş komut metni + maskelenmiş, sıkıştırılmış ekran özeti (öge kimliği, rolü, maskeli etiketi) + izinli eylem listesi + etkin uygulamanın paket adı (yalnız izinli listedeki uygulamalar; diğer kurulu uygulamalar gönderilmez, çünkü özel nitelikli veri çıkarımına yol açabilir, NFG-71). Maskeleme belirsiz/başarısızsa istek **gönderilmez**.

**Maskeleme ilkesi (varsayılan reddet):** Komut ve ekran metni yer tutuculara çevrilir: `[KISI_n]`, `[TEL_n]`, `[EPOSTA_n]`, `[TCKN_n]`, `[IBAN_n]`, `[KART_n]`, `[ADRES_n]`, `[SAYI_n]`, `[METIN_n]`. Aynı değer komutta ve ekranda **aynı yer tutucuyu** alır; böylece dil modeli "`[KISI_1]` satırına dokun" diyebilir, gerçek ad hiç çıkmaz. Kaynaklar: rehber eşleşmesi, düzenli ifadeler (Türk telefon biçimleri, TCKN sağlama, IBAN mod-97, kart Luhn, adres anahtar sözcükleri), 4+ haneli rakam dizileri, kullanıcının yazdığı alanlar ve liste içi serbest metinler (mesaj gövdesi, sohbet önizlemesi) bütünüyle `[METIN_n]`. Yalnız uygulamanın kendi arayüz etiketleri (düğme, menü, başlık) maskesiz kalabilir; onlar da son denetimden geçer. Son denetim, gidecek metinde kalan rakam dizisi, rehber adı ya da düzenli ifade eşleşmesi bulursa isteği durdurur. Ayrıntı: `docs/MASKELEME.md` (G-03).

**Bağımlılık izin listesi (tümü ücretsiz/açık kaynak; listede olmayan eklenmez, gerekirse plan güncellenir):** Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization-json, AndroidX (core-ktx, activity-compose, lifecycle, Compose BOM + material3, Room + KSP, WorkManager), OkHttp (+ mockwebserver yalnız testte), whisper.cpp (MIT, sabit sürüm etiketi, kaynak olarak), ONNX Runtime Android (MIT), ONNX Runtime Extensions Android (MIT, yalnız G-43 gerektirirse), JUnit4, kotlinx-coroutines-test, AndroidX Test (junit, runner, uiautomator). Vekil sunucu: harici paket yok (`node:test` ile test), araç olarak `wrangler` (ücretsiz).

---

## 2. Varsayılanlar: ONAYLANDI 17.11.2026

Öğrenci 17.11.2026'da tüm varsayılanları onayladı (tek değişiklik V-19, aşağıda). Değerler tek bir ayar dosyasında (`core/.../Defaults.kt`) toplanır, böylece sonradan kolay değişir. Kalan kararlar ana oturumdadır; her yeni karar `docs/SAPMALAR.md`'ye yazılır.

| No | Karar (kaynak) | Önerilen varsayılan | Dayanak |
|---|---|---|---|
| V-01 | Sesli yanıt (TK-03) | Android yerleşik Türkçe TTS (çevrimdışı ses paketi), hız %80'e kadar yavaşlatılabilir | GEREKSİNİMLER §7 |
| V-02 | Ekran girdisi (TK-04) | Yalnız erişilebilirlik ağacı (sıkıştırılmış); ekran görüntüsü/OCR yok; etiketsiz simge düğmede asistan emin olmadığını söyler ve sorar | §7 |
| V-03 | Görev belleği (TK-05) | `multilingual-e5-small` (int8, ONNX, cihazda); tarif = parametreli JSON adım listesi, Room/SQLite; benzerlik eşiği 0,80; düz kosinüs araması (ayrı vektör veritabanı yok). A15'te çok ağır çıkarsa daha küçük çok dilli model (G-43 karar verir) | §7 + plan |
| V-04 | Vekil sunucu (TK-07) | Cloudflare Workers ücretsiz katmanı (G-02: 100.000 istek/gün, 10 ms CPU, aşımda ücret değil 1027 hatası; kart koşulu resmî sayfada yazmıyor, hesap açarken görülür); anahtar Worker gizli değişkeninde; cihaz kurulumda öğrencinin verdiği **erişim kodunu** girer (APK'ya gömülmez); yalnız kısa süreli hız sınırı (V-19), cihaz başına günlük sınır yok; gövde günlüğe yazılmaz; Workers Logs çağrı günlüğü kapalı (`invocation_logs = false`), KV sayaç için kullanılmaz (1.000 yazma/gün). Olmazsa: demo için öğrencinin bilgisayarında aynı vekil | §7 + plan |
| V-05 | Kaplama biçimi (TK-08) | Vurgu: yüksek kontrastlı sarı halka + koyu kenar; dokunma: aynı renklerde nokta; kaydırma: ok izi; kırmızı yalnız "hata"; doğru/yanlış renk + titreşimle ayrılır | §7 |
| V-06 | Gecikme eşikleri (TK-09, NFG-23) | dokunma→dinleme ≤1 sn; komut bitişi→ilk sesli geri bildirim ≤2 sn; bekleme >5 sn olursa sesli bilgi. A15 ölçümüyle (G-54) gözden geçirilir | §7 |
| V-07 | Risk sınıflandırıcı (TK-10) | Kural tablosu: uygulama (banka engelli) + öge türü/etiketi + Türkçe tehlikeli sözcük listesi; belirsizde bir üst risk; onay zaman aşımı 10 sn, sessizlik = hayır | §7 |
| V-08 | Aile ayarları (TK-12) | Aynı cihazda PIN'li ayar ekranı; uzaktan yönetim yok | §7 |
| V-09 | Mod adları/ses (TK-13) | "Sen yap" / "Ben yapayım"; varsayılan mod "Ben yapayım"; tek sıcak ve sade ton; ses cinsiyeti cihazdaki Türkçe seslere göre ayarlanabilir | §7 |
| V-10 | Eylem uzayı (TK-14) | Öge kimliği + kaydırma yönü/uzunluğu (kısa/orta/uzun); koordinat yalnız yedek | §7 |
| V-11 | Android sürümü (TK-15) | Alt sınır Android 10 (API 29), compileSdk/targetSdk 36 (birincil cihaz Android 16); APK ile kurulumdaki "kısıtlı ayarlar" adımı kurulumda sesli anlatılır | §7 |
| V-12 | Zaman aşımları/komut sözcükleri (TK-16, SS-11) | Onay bekleme 10 sn; dinlemede susma 3,5 sn, üst sınır 60 sn; sabit sözcükler: "dur", "baştan anlat", "tamam, hatırladım", "neden", "evet/onaylıyorum", "hayır/vazgeç" | §7 |
| V-13 | Değerlendirme altyapısı (TK-17) | Galaxy A15 + ücretsiz Android Studio emülatörü; ~100 komutluk Türkçe komut seti öğrenci tarafından yazılır | §7 |
| V-14 | İzinli uygulamalar, rehber izni (SS-06) | Varsayılan izinli: Telefon, Mesajlar, WhatsApp, Ayarlar, Galeri, Kamera, Saat, Hava durumu. Rehber izni isteğe bağlı, nedeni söylenerek istenir (FG-101 için gerekli); verilmezse kişi yalnız ekrandan aranır | **Plan önerisi** (kaynakta değer yok) |
| V-15 | Saklama süreleri (SS-07, NFG-59) | Metrik günlüğü 30 gün; buluta giden isteklerin denetim kopyası 7 gün; kayıtlı tarif kullanıcı silene dek, 180 gün kullanılmazsa otomatik silinir; rıza kaydı "tüm verilerimi sil"e dek; araştırma ses kaydı WER hesaplanınca hemen, en geç 7 gün | **Plan önerisi** |
| V-16 | Tarif kaydetme sorusu (SS-12) | Yalnız dil modeliyle başarıyla bitmiş görevin sonunda, o görev için bir kez: "Bunu bir dahaki sefere daha hızlı yapmam için hatırlayayım mı?" | **Plan önerisi** |
| V-17 | Adım bütçesi (FG-27) | 10 adım, aşılırsa kullanıcıya devir | MAKALE-08 |
| V-18 | Hareketsizlik yardımı (FG-36) | 20 sn (ayarlanabilir) | MAKALE-05 |
| V-19 | Dil modeli kullanımı ve kota (NFG-25) | **Kullanıcıya görünen kullanım sınırı yok** (cihaz başına günlük sınır kaldırıldı); kullanıcı istediği kadar sorabilir. Kötüye kullanım koruması yalnız vekil sunucuda: erişim kodu + kısa süreli hız sınırı (normal bir insanın kullanımını asla kesmeyecek düzeyde, dakikada 20 istek / erişim kodu, G-02'ye göre Groq 30 RPM'in altında). NFG-25'teki bütçe = sağlayıcının ücretsiz kotası: biterse sırayla yedek sağlayıcı (Groq), sonra cihaz içi özellikler (kurallar + görev belleği) ve sade, dürüst sesli bilgilendirme. Bulut çağrısı, önce kural ve görev belleği denenerek azaltılır | **ONAYLANDI (değiştirildi) 17.11.2026**, bkz. SAPMALAR |
| V-20 | Baloncuk penceresi | Erişilebilirlik kaplaması (`TYPE_ACCESSIBILITY_OVERLAY`); ayrıca "diğer uygulamaların üstünde göster" izni istenmez (NFG-68) | **Plan önerisi** |
| V-21 | Whisper model dağıtımı | Seçilen model ~60 MB'tan küçükse APK içinde; büyükse ilk kurulumda Hugging Face'ten bir kez indirme (aydınlatmada belirtilir). Ölçüm için modeller `adb` ile yüklenir | **Plan önerisi** |
| V-22 | Dil modeli ayarı | Sıcaklık 0, düşünme en düşük. Zincir (G-02, `docs/DOGRULAMALAR.md` §5): `gemini-3.5-flash-lite` (thinking_level minimal) → `gemini-3.6-flash` (minimal; G-55'te Flash-Lite Türkçede yetersizse birincil olur) → Groq `openai/gpt-oss-120b` (reasoning_effort low) → `openai/gpt-oss-20b` → cihaz içi. Yalnız 429/5xx/zaman aşımı/geçersiz JSON'da sonrakine geçilir. Önizleme, `-latest` ve kullanımdan kalkan (`gemini-2.5-*`) modeller kullanılmaz. Model adları vekil sunucu ayarındadır, uygulamada değil | NFG-28, NFG-36 |
| V-23 | Sesli okuma gizliliği (NFG-15) | Varsayılan: kişi adları okunur (FG-101 gereği), mesaj içeriği ve numaralar sesli okunmaz, yalnız ekranda gösterilir; ayarla değişir | **Plan önerisi** |

**Veri sorumlusu (SS-13):** öğrencinin kendisi (karar 17.11.2026). Ad ve iletişim adresi aydınlatma metni görevinde (G-30) öğrenciden istenir; gelene kadar metinde açıkça işaretli yer tutucu kalır.
**S-07 kararı:** Araya girme için mikrofon yalnız baloncuğa basılarak başlamış oturum boyunca açıktır (onaylandı 17.11.2026).

---

## 3. Kullanıcının elle yapacağı hazırlıklar

Hepsi ücretsizdir. Para isteyen bir ekran görürseniz durun ve sorun.

**A. Başlamadan önce (G-01'den önce)**
1. **Android Studio'yu kurun** (developer.android.com/studio, ücretsiz). Kurulumda "Standard" seçin. Açıldıktan sonra *More Actions → SDK Manager → SDK Tools* sekmesinde şunları işaretleyip kurun: *Android SDK Build-Tools*, *NDK (Side by side)*, *CMake*, *Android SDK Platform-Tools*. (Not: bilgisayarınızdaki Java 25, Android araçları için fazla yeni olabilir; Android Studio'nun içinde gelen Java'yı kullanacağız, ayrıca bir şey kurmanız gerekmez.)
2. **Telefonda geliştirici modunu açın** (Galaxy A15): *Ayarlar → Telefon hakkında → Yazılım bilgileri → Yapım numarası*'na 7 kez dokunun. Sonra *Ayarlar → Geliştirici seçenekleri → USB hata ayıklama*'yı açın.
3. **Telefonu USB kabloyla bilgisayara bağlayın**, telefonda çıkan "USB hata ayıklamaya izin ver" sorusuna "İzin ver" deyin. Windows telefonu görmezse Samsung'un ücretsiz USB sürücüsünü kurun.
4. Telefonun **Android sürümünü ve RAM miktarını** yazıp bana iletin (*Ayarlar → Telefon hakkında*).

**B. İlk sesli denemeden önce (G-07, G-08)**
5. Telefonda **Türkçe çevrimdışı konuşma tanımayı** indirin: *Ayarlar → Genel yönetim → Dil → ... (ya da Google uygulaması → Ayarlar → Ses → Çevrimdışı konuşma tanıma) → Türkçe*. Yerini bulamazsanız G-07 sırasında birlikte bakarız.
6. **Türkçe sesli okuma paketini** indirin: *Ayarlar → Erişilebilirlik → Metin okuma (TTS) → tercih edilen motor → Ses verilerini yükle → Türkçe*.
7. Uygulamayı ilk kurduğunuzda Android 13+ "kısıtlı ayar" uyarısı verebilir: *Ayarlar → Uygulamalar → (bizim uygulama) → sağ üst üç nokta → Kısıtlı ayarlara izin ver*.

**C. Ses tanıma ölçümünden önce (G-14)**
8. ~~Kendi sesinizle 40 komut kaydedin~~ **Kaldırıldı (17.11.2026, öğrenci kararı):** kayıt yapılmayacak; ölçüm aracı 40 komutu telefonun Türkçe TTS sesiyle sentezler (bkz. G-13, SAPMALAR). Liste ve isteğe bağlı kayıt talimatı: `docs/olcum/komut-listesi.md`. Yaşlı katılımcıdan kayıt **alınmaz** (NFG-64).
9. İsteğe bağlı: Mozilla Common Voice Türkçe (CC0) kendi hesabınızla indirilebilir (e-posta ister); indirilirse klasör kipinde ölçülür.

**D. Bulut bağlantısından önce (G-33, G-34)**
10. **Google AI Studio**'dan (aistudio.google.com) ücretsiz **Gemini API anahtarı** alın. Kart bilgisi isterse durun. Anahtarı kimseyle, bana sohbette bile paylaşmayın; yalnız vekil sunucuya siz gireceksiniz.
    Anahtarı aldıktan sonra AI Studio'daki kota ekranında `gemini-3.5-flash-lite` ve `gemini-3.6-flash` için dakika/gün sınırlarını yazıp bana iletin (G-02: bu rakamlar yalnız hesapta görünüyor). Faturalandırma (billing) hesabı asla bağlamayın.
11. **Groq** (console.groq.com) ücretsiz hesap açıp yedek anahtar alın (aynı kurallar). *Settings → Data Controls*'ta **Zero Data Retention**'ı açın; *Settings → Limits*'teki `openai/gpt-oss-120b` ve `openai/gpt-oss-20b` sınırlarını iletin. Ödeme yöntemi eklemeyin.
12. **Cloudflare** ücretsiz hesap açın (kart isterse durun; resmî sayfa bunu açıkça yazmıyor, G-02) ve **Node.js LTS**'yi kurun (nodejs.org, ücretsiz). Vekil sunucuyu yayına alma komutunu G-33'te adım adım vereceğim; anahtarları o komut size soracak.
13. Uygulamaya girilecek **erişim kodunu** siz belirleyin (en az 12 karakter, kimseyle paylaşmayın).

**E. Test telefonu ve içerik (G-29'dan önce)**
14. Test telefonunda yalnız **uydurma kişiler** kullanın (ör. "Ayşe Test", 0555 000 00 01). Gerçek mesaj, gerçek banka ya da ödeme kullanılmaz (NFG-62). WhatsApp testi için ayrı numara gerekirse **ücret doğabilir** (hat): önce sorun.
15. İkinci marka bir telefon bulabilirseniz (aile/arkadaştan ödünç) haber verin (NFG-33, SS-09).

**F. Kararlar ve metinler**
16. Bölüm 2'deki varsayılanları onaylayın ya da değiştirin; **veri sorumlusu** (siz mi, üniversite mi) ve iletişim adresini yazın (SS-13).
17. ~100 komutluk **Türkçe komut setini** gerçek örneklerden yazın (G-55 için; biçimi G-55 verir).
18. G-02 sonucundaki KVKK m.9 sorusuna (Bölüm 7, S-01) siz karar verin.

---

## 4. Kilometre taşları ve görevler

**Tüm görevler için ortak kabul:** `gradlew` ile derleme ve tüm birim testleri geçer; izin listesi dışı bağımlılık yok; release yapısında transkript, rehber, ekran içeriği günlüğe yazılmaz (`SafeLog` sarmalayıcısı); kullanıcıya görünen metinler `strings.xml`'de Türkçe; `[Cihaz]` ile işaretli adımları gelistirici `adb` ile yapar, telefon bağlı değilse kullanıcıya sade Türkçe adım listesi verir. Büyüklük: K (küçük), O (orta), B (büyük; yine tek çağrıda bitecek kadar).

### KT-0 Hazırlık

#### G-01 Ortam kurulumu ve doğrulama (K)
- **Amaç:** Komut satırından derleme yapılabildiğini kanıtlamak.
- **Yapılacak:** `tools/ortam-kontrol.ps1`: JDK 17/21 bulunur (öncelik Android Studio'nun JBR'ı; JDK 25 Gradle/AGP için kullanılmaz), `ANDROID_HOME`, platform-tools, build-tools, NDK, CMake, `adb devices` kontrolü; eksikleri Türkçe yazar. `docs/KURULUM.md`: hangi JDK'nın neden seçildiği, `JAVA_HOME` ayarı, sorun giderme.
- **Yapılmayacak:** Uygulama kodu, paket kurulumu (kullanıcı yapar).
- **Gereksinim:** NFG-74 dolaylı (araç seçimi), Ek C (ücretsiz araç).
- **Bağımlılık:** Hazırlık A.
- **Kabul:** Betik çalışır, her kalem için TAMAM/EKSİK yazar; telefon bağlıysa cihaz kimliğini listeler.

#### G-02 Bulut ve hukuk doğrulamaları (O)
- **Amaç:** DOĞ-01'i ve vekil sunucu koşullarını kodlamadan önce resmî sayfalardan doğrulamak.
- **Yapılacak:** `docs/DOGRULAMALAR.md` (tarih + bağlantı + alıntı ile): Gemini API ücretsiz katmanı (güncel ücretsiz model adları, dakika/gün kotası, verinin eğitimde kullanımı, işleme bölgesi, Türkiye'de kullanılabilirlik, kart istememesi); Groq ücretsiz katmanı (aynıları); Cloudflare Workers ücretsiz katmanı (istek/gün sınırı, kart, gizli değişken, KV ücretsiz sınırı, istek gövdesi günlüğü); KVKK m.9'un güncel resmî metni (Resmî Gazete/KVKK Kurumu) ve açık rızanın düzenli aktarımda dayanak olup olmadığı. Her kalem için V-04, V-19, V-22 ve aydınlatma metnine etkisi.
- **Yapılmayacak:** Hukuki karar vermek (S-01 öğrenciye kalır), kod.
- **Gereksinim:** DOĞ-01, NFG-25, NFG-76, NFG-80, NFG-41, Ek C.
- **Bağımlılık:** Yok.
- **Kabul:** Her kalemde resmî kaynak bağlantısı ve erişim tarihi var; "doğrulanamadı" olanlar ayrıca listelenmiş; para doğurabilecek her koşul `[PARA?]` ile işaretli.

#### G-03 Maskeleme kapsamı belirtimi ve test korpusu (O)
- **Amaç:** DOĞ-02'yi kapatmak: neyin maskeleneceğini kodlamadan önce sınanabilir biçimde yazmak.
- **Yapılacak:** `docs/MASKELEME.md`: kategoriler (ad, telefon, e-posta, TCKN, IBAN, kart, adres, rakam dizisi, serbest metin/mesaj içeriği, kullanıcı girişi, özel nitelikli içerik), yer tutucu biçimi, tutarlılık kuralı (aynı değer = aynı yer tutucu), ekran ögesi sınıfına göre politika (arayüz etiketi / liste içeriği / düzenlenebilir alan / parola), son denetim kuralları, "maskeli metin yine kişisel veri sayılır" varsayımı. `core/src/test/resources/mask-corpus.json`: en az 150 uydurma örnek (komut ve ekran ögesi) + beklenen çıktı.
- **Yapılmayacak:** Maskeleme kodu (G-31, G-32).
- **Gereksinim:** DOĞ-02, NFG-50, NFG-71, NFG-79, FG-79, NFG-46.
- **Bağımlılık:** Yok.
- **Kabul:** Korpus geçerli JSON; her kategoriden en az 10 örnek; gerçek kişi verisi yok (yalnız "Test" adlı uydurma kişiler ve geçersiz numara aralıkları).

**UD-0** uyum-analisti: G-02 ve G-03 belgelerini gereksinimlere karşı denetler.

### KT-1 İnce dilim: baloncuk + ses + tek basit görev ("Ayarlar'ı aç")

#### G-04 Proje iskeleti ve yapı kuralları (O)
- **Yapılacak:** Gradle çok modüllü proje (`:core`, `:speech`, `:app`), sürüm kataloğu (yalnız izin listesindeki kitaplıklar), minSdk 29, R8 açık release, `SafeLog` (release'te içerik içeren günlükleri derleme anında boşaltır), Türkçe `strings.xml`, yüksek kontrastlı büyük yazılı tema, boş ana ekran. Birim test: birleşik manifestte izin listesi testi (yalnız beklenen izinler; `READ_SMS`, `RECEIVE_SMS`, bildirim dinleyici, `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES` **yok**).
- **Yapılmayacak:** Özellik kodu.
- **Gereksinim:** NFG-01, NFG-43 (uygulamaya özel depolama ilkesi), NFG-67, NFG-68, NFG-74.
- **Bağımlılık:** G-01.
- **Kabul:** `gradlew assembleDebug test` geçer; [Cihaz] APK kurulur, Türkçe ana ekran açılır; manifest izin testi geçer.

#### G-05 Erişilebilirlik bilgilendirmesi ve hizmet iskeleti (O)
- **Yapılacak:** Ayarlar menüsüne gitmeden önce gösterilen büyük yazılı, sesli okunabilir bilgilendirme ve açık onay ekranı (ne okunur/okunmaz, neden); onaydan sonra erişilebilirlik ayarlarına yönlendirme; olay işlemeyen boş `AssistantAccessibilityService` (yalnız bağlanma/ayrılma durumunu bildirir); kısıtlı ayarlar için kısa yönlendirme metni.
- **Yapılmayacak:** Ekran okuma (G-19), aydınlatma/bulut rızası (G-30).
- **Gereksinim:** NFG-65, NFG-53 (hizmet boşta hiçbir şey okumaz), TK-15.
- **Bağımlılık:** G-04.
- **Kabul:** Birim test: onay verilmeden yönlendirme fonksiyonu çağrılmaz. [Cihaz] Bilgilendirme gösterilmeden ayarlara gidilmiyor; hizmet açılınca uygulama "hazır" gösteriyor.

#### G-06 Yüzen baloncuk ve Dur düğmesi (O)
- **Yapılacak:** Hizmetin içinde `TYPE_ACCESSIBILITY_OVERLAY` baloncuk: metin etiketli ("Konuş"), büyük, sürüklenebilir, ekran kenarına sığdırma, konum hatırlama; oturum etkinken görünen metin etiketli büyük "Dur" düğmesi; kendi pencerelerinin kimlikleri kaydedilir (sonra gözlemden dışlanmak için).
- **Gereksinim:** FG-01, FG-11, NFG-02, NFG-03, NFG-40 (hazırlık).
- **Bağımlılık:** G-05.
- **Kabul:** Birim test: sürükleme konumunu ekran sınırına sıkıştırma mantığı. [Cihaz] Baloncuk başka uygulamaların üstünde görünür, sürüklenir, uygulama değişince kalır; "Dur" tek dokunuşla oturumu bitirir.

#### G-07 Konuşma tanıma arayüzü ve yerleşik çevrimdışı tanıyıcı (O)
- **Yapılacak:** `:speech`'te `SpeechEngine` arayüzü (başlat/durdur/sonuç/hata/kullanılabilirlik) ve `OnDeviceSystemEngine` (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, API 31+; API 29-30'da "kullanılamaz" döner, çevrimiçi tanıyıcıya **asla** düşmez); mikrofon izni akışı (nedeni söylenerek); mikrofon yalnız baloncuk dokunuşunda açılır; susma 3,5 sn / üst sınır 60 sn ayarlı; boş/gürültü sonucu komut üretmez; ses dosyaya yazılmaz. Arka planda mikrofon kısıtı A15'te sınanır; engellenirse yedek: dokunuşta saydam etkinlik açıp dinleme.
- **Yapılmayacak:** Whisper (G-12), araya girme (G-16).
- **Gereksinim:** FG-02, FG-03, FG-05, NFG-16, NFG-17, NFG-18, NFG-48, NFG-49.
- **Bağımlılık:** G-06, hazırlık B.
- **Kabul:** Birim test (sahte motor): boş sonuç → komut yok; zaman aşımları; motor kullanılamazsa sesli/görsel uyarı durumu. [Cihaz] Uçak modunda Türkçe komut yazıya dönüyor; başka uygulama öndeyken baloncuktan dinleme çalışıyor.

#### G-08 Sesli yanıt (K)
- **Yapılacak:** `Speaker` arayüzü + Android TTS (Türkçe, hız ayarı, Türkçe ses yoksa yönlendirme), konuşmayı kesme işlevi, her sesli çıktının baloncuk yanında yazılı da gösterilmesi.
- **Gereksinim:** NFG-04, NFG-20, TK-03.
- **Bağımlılık:** G-06.
- **Kabul:** Birim test: kuyruk/kesme mantığı (sahte TTS). [Cihaz] Uçak modunda Türkçe okuma, %80 hızda yavaşlıyor.

#### G-09 Diyalog durum makinesi (O)
- **Yapılacak:** `:core`'da saf Kotlin durum makinesi: boşta → dinliyor → geri okuma → onay bekliyor (evet/hayır sözcükleri, 10 sn sessizlik = hayır) → çalışıyor → bitti (sesli bildirim); her durumda "Dur"; >5 sn bekleyişte "Bakıyorum…" ilerleme olayı; kısa cümle şablonları, yüzde yok.
- **Gereksinim:** FG-07, FG-11, FG-16, FG-17, NFG-05, NFG-07, TK-16.
- **Bağımlılık:** G-04.
- **Kabul:** Birim testler: onaysız çalışma yok, sessizlik = hayır, Dur her durumdan boşta'ya döner, geç sonuçta ilerleme olayı üretilir.

#### G-10 Karar motoru iskeleti ve "uygulama aç" kuralı (O)
- **Yapılacak:** `DecisionEngine` (sıra: kural → kayıtlı tarif → dil modeli; son ikisi bu görevde boş/kapalı yer tutucu), `Rule` arayüzü, Türkçe normalleştirme (ı/i, büyük-küçük, kesme işareti ve ek atma: "WhatsApp'ı", "Ayarları"), başlatıcıdaki uygulama adlarıyla eşleştirme (`<queries>` ile, özel izin yok); tek eşleşme → doğrudan; çok eşleşme → adlarıyla seçenek; eşleşme yok → "Bunu henüz yapamıyorum". Açma, Android başlatma niyetiyle (Düşük risk).
- **Gereksinim:** FG-40, FG-101 (uygulama adı için), NFG-24.
- **Bağımlılık:** G-09.
- **Kabul:** Birim testler: normalleştirme tablosu (≥30 örnek), katman sırası, tek eşleşmede soru yok.

#### G-11 Uçtan uca bağlama, yazıyla komut ve demo (O)
- **Yapılacak:** Baloncuk → tanıma → diyalog → karar → TTS bağlantısı; baloncuk menüsünde "Yazarak söyle" alanı; tek dokunuşla yeniden deneme; `docs/demo/KT-1.md` (demo adımları).
- **Gereksinim:** FG-04, FG-05, FG-16, DEG-30.
- **Bağımlılık:** G-07, G-08, G-10.
- **Kabul:** [Cihaz] Baloncuk → "Ayarları aç" → "Ayarlar uygulamasını açıyorum" (düşük risk + kesin eşleşme: onay sorulmaz, bkz. SAPMALAR 17.11.2026 G-10 satırı) → Ayarlar açılır, "Ayarlar açıldı" denir; kısmi eşleşmede ("haritaları aç") "… açayım mı?" → "evet" → açılır; aynı akış "Yazarak söyle" ile de çalışır; "Dur" her aşamada çalışır; kullanıcı ekran kaydı alır (DEG-30).

**UD-1** uyum-analisti: KT-1 kodunu FG-01..FG-17, NFG-16..18, NFG-65, NFG-67/68'e karşı denetler.

### KT-2 Konuşma tanıma ölçümü (KT-1'den sonra; KT-4 ile paralel yürüyebilir)

#### G-12 whisper.cpp entegrasyonu (B)
- **Yapılacak:** `:speech`'e sabit sürüm etiketli whisper.cpp kaynağı, CMake/NDK yapısı (arm64-v8a), JNI köprüsü, `WhisperEngine : SpeechEngine` (dil sabit "tr", uygulamaya özel klasörden model yükleme, ses yalnız bellekte), enerji tabanlı VAD ile konuşma sonu algılama, model boşaltma (bellek baskısında).
- **Gereksinim:** NFG-16, NFG-48, NFG-49, NFG-75, NFG-72.
- **Bağımlılık:** G-07.
- **Kabul:** Birim test (JVM): VAD ve ses tamponu mantığı. [Cihaz] `adb` ile yüklenen tiny modeli örnek WAV'ı yazıya çeviriyor; uygulama çökmüyor.

#### G-13 Tanıma ölçüm aracı ve WER (O)
- **Yapılacak:** `:core`'da WER/CER (3 normalleştirme düzeyi: ham, noktalama+büyük harf, Türkçe sayı/ek normalleştirme), halüsinasyon (sessiz klipte metin) ve döngü (tekrar) tespiti; yalnız debug yapısında ölçüm ekranı/enstrümanlı test: klasördeki WAV + referans metinleri her motorla çalıştırır, sn/ifade, gerçek-zaman oranı, tepe bellek ölçer, CSV yazar. Yerleşik tanıyıcıya dosya sesi `EXTRA_AUDIO_SOURCE` (API 33+) ile verilir; reddedilirse yöntem raporlanır. `docs/olcum/komut-listesi.md` (kullanıcının okuyacağı 40 komut) ve sessiz/TV gürültülü klip talimatı.
- **Gereksinim:** DEG-14, DEG-32, NFG-19, NFG-21.
- **Bağımlılık:** G-12.
- **Kabul:** WER birim testleri (bilinen örnekler); ölçüm aracı emülatörde örnek klasörle CSV üretir.
- **Güncelleme 17.11.2026 (kayıt yok kararı):** Birincil veri, 40 uydurma komutun telefonun çevrimdışı Türkçe TTS sesiyle sentezlenmesidir (koşullar: temiz, sentetik konuşma karışımıyla gürültülü SNR 5 dB, sessiz). Klasör kipi (`manifest.tsv` + WAV) ileride gelecek insan kayıtları için korunur. **TTS sesi gerçek, özellikle yaşlı konuşmasını temsil etmez**; WER motorların göreli sıralaması ve hız tablosu için kullanılır, gerçek WER'in alt sınırı sayılır. Araç yalnız test APK'sında enstrümanlı test olarak vardır ve doğrudan A15'te çalışır (Whisper x86 emülatörde çalışmaz): `tools/asr-olcum.ps1`, ayrıntı `docs/olcum/OLCUM-ARACI.md`. Bkz. SAPMALAR.

#### G-14 Galaxy A15 ölçüm koşusu ve motor önerisi (O)
- **Yapılacak:** [Cihaz] tiny/base/small (+ çalışıyorsa sıkıştırılmış sürümleri) ve yerleşik tanıyıcıyı kullanıcının kayıtları, gürültülü ve sessiz kliplerle koşar; `docs/olcum/asr-karsilastirma.md`: hoca için Whisper hız tablosu (her durumda), WER (3 düzey), halüsinasyon/döngü oranı, bellek; motor önerisi ve gerekçesi (Türkçe doğruluk + A15 gecikmesi). Karar **öğrenci onayına** sunulur.
- **Gereksinim:** DEG-14, DEG-32, NFG-75, NFG-19, NFG-21, NFG-27.
- **Bağımlılık:** G-13 (hazırlık C kaldırıldı: veri TTS sentezi, bkz. G-13 güncellemesi).
- **Kabul:** Tablo tüm motorlar için doldurulmuş; ölçülemeyen hücrelerin nedeni yazılı; ham ses kayıtları depoya eklenmemiş; raporda "TTS sesi gerçek/yaşlı konuşmayı temsil etmez, NFG-19'un yaşlı konuşması kısmı açık" sınırı açıkça yazılı.

**UD-2** uyum-analisti: ölçüm yöntemi DEG-14/DEG-32/ÇEL-05/ÇEL-16'ya uygun mu.

### KT-3 Doğal konuşma ve araya girme

#### G-15 Seçilen motorun varsayılan yapılması ve model dağıtımı (K)
- **Yapılacak:** Öğrencinin onayladığı motor varsayılan olur; ikincisi yedek (ör. API 29-30'da Whisper); V-21'e göre model dağıtımı (APK içinde ya da ilk kurulumda indirme + bütünlük denetimi); ayarlarda motor bilgisi.
- **Gereksinim:** NFG-75, NFG-17, NFG-72.
- **Bağımlılık:** ~~G-14 + öğrenci onayı~~ → G-13. **Güncelleme 17.11.2026:** G-14 cihaz ölçümü ertelendi; motor kararı öğrencinin devriyle ana oturumca verildi (SAPMALAR 17.11.2026 G-15 satırı): Android 12+ ve çevrimdışı Türkçe varsa sistem tanıyıcısı, yoksa Whisper base (3 GB altı bellekte tiny); model ilk gerektiğinde onayla Hugging Face'ten indirilir (INTERNET izni yalnız bunun için, `HttpGateway` + `NetworkAllowlist`). Seçim tek yerde (`EnginePolicy`); G-14 ileride yapılırsa yalnız onun varsayılanları değişir.
- **Kabul:** Birim test: motor seçim kuralı (API düzeyi + kullanılabilirlik). [Cihaz] Varsayılan motorla G-11 demosu çalışıyor.

#### G-16 Araya girme (O)
- **Yapılacak:** Asistan konuşurken (yalnız etkin oturumda) mikrofon VAD ile dinler; yankı engelleyici (`AcousticEchoCanceler`, varsa) + TTS sesinin kendini tetiklemesine karşı eşik; kullanıcı konuşunca TTS anında susar ve tanıma başlar.
- **Gereksinim:** FG-14, FG-02 (oturum dışında mikrofon kapalı).
- **Bağımlılık:** G-15, G-08.
- **Kabul:** Birim test: durum geçişi (konuşuyor → kullanıcı sesi → susma ≤300 ms hedefi). [Cihaz] Asistan uzun cümle okurken "dur" denince susuyor; oturum bitince mikrofon göstergesi kapanıyor.

#### G-17 Sabit sesli komutlar (O)
- **Yapılacak:** Her durumda yerelde tanınan komutlar: "dur" (süren işi keser), "baştan anlat" (anlatımı/rehberliği ilk adımdan başlatır), "tamam, hatırladım" ("Tamam, kapatıyorum" deyip durur), "neden" (G-39'a olay üretir), "tekrar söyle". Yazım varyantları ve yakın eşleşme tablosu.
- **Gereksinim:** FG-12, FG-13, FG-23, FG-05.
- **Bağımlılık:** G-16, G-09.
- **Kabul:** Birim testler: varyant tablosu, her durumda doğru geçiş. [Cihaz] Üç komut anlatım sırasında çalışıyor.

**UD-3** uyum-analisti: FG-12..FG-14, FG-02 yorumu (oturum içinde dinleme) ve NFG-49.

### KT-4 Ekran okuma ve "Sen yap" yürütücü

#### G-18 Metrik günlüğü altyapısı (K)
- **Yapılacak:** Room tablosu ve `MetricsLogger`: adım süresi, çözen katman, dil modeli çağrı sayısı, tanıma/gömme/dil modeli/yürütme gecikmeleri ayrı, yanlış dokunuş, durdurma, onay sonucu, gereksiz soru, öge bulunamadı, hata nedeni etiketi (niyet/planlama/çevre/yürütme). Kişisel içerik alanı **yok** (tip düzeyinde). Debug'da CSV dışa aktarma.
- **Gereksinim:** FG-90, FG-92, NFG-27, DEG-19, DEG-20.
- **Bağımlılık:** G-11.
- **Kabul:** Birim test: kayıt tipinde serbest metin alanı olmadığını doğrulayan test; CSV biçimi.

#### G-19 Ekran okuyucu ve sıkıştırma (O)
- **Yapılacak:** Yalnız etkin görevde ağaç anlık görüntüsü (boşta olaylar yok sayılır); kendi pencereleri dışlanır; parola alanlarının metni **hiç okunmaz** (yalnız "parola alanı" rolü); Android'den bağımsız `ScreenNode` modeline dönüştürme ve sıkıştırma (görünür, anlamlı ögeler; numaralı öge kimliği; rol, etiket, durum, sınır kutusu); etiketsiz simge düğme işaretlenir.
- **Gereksinim:** NFG-29, NFG-52, NFG-53, NFG-40, FG-78 (parola), NFG-30, TK-04, TK-14.
- **Bağımlılık:** G-18.
- **Kabul:** Birim testler: hazır ağaç örnekleriyle (JSON fikstür) sıkıştırma, parola metninin çıktıda olmaması, kendi penceresinin dışlanması.

#### G-20 Eylem şeması ve yürütücü (O)
- **Yapılacak:** `:core` eylem şeması: dokun(öge), kaydır(öge/yön/uzunluk), yaz(öge, metin), geri, ana ekran, uygulama aç, bekle, devret(neden), bitti. `:app` yürütücü: `performAction`/`dispatchGesture`; her dokunuştan önce hedef ögenin hâlâ planlanan etiket/rol/konumla eşleştiği denetimi; "Dur" ile süren hareket iptali ve sonraki eylemin başlamaması.
- **Gereksinim:** FG-20, FG-23, NFG-38, NFG-39, TK-14.
- **Bağımlılık:** G-19.
- **Kabul:** Birim testler (sahte yürütücü): hedef uyuşmazlığında eylem yok; Dur sonrası kuyruk boş. [Cihaz] Debug ekranından verilen 3 adımlı sabit dizi Ayarlar'da çalışıyor.

#### G-21 Dokunma noktası ve kaydırma izi (K)
- **Yapılacak:** Dokunmaya geçirgen kaplama: her dokunuşta nokta, her kaydırmada ok izi (V-05 renkleri), kısa animasyon; gözlemden dışlanır.
- **Gereksinim:** FG-21, NFG-40, TK-08.
- **Bağımlılık:** G-20.
- **Kabul:** Birim test: iz geometrisi hesabı. [Cihaz] Nokta/iz görünüyor, alttaki uygulama dokunuşları engellenmiyor.

#### G-22 Adım anlatımı, eylem sonrası doğrulama, geri alma (O)
- **Yapılacak:** Her adımda tek satırlık sesli anlatım ("Ayarlar'a dokundum"); her adımın beklenen durumu (paket, ögenin varlığı, ekran değişimi) ve eylem sonrası karşılaştırma; uyuşmazlıkta dur, tek soru, ekranı yeniden oku; beklenmeyen sayfada "geri" ile geri alma ve geçmişe yazmama; öge bulunamazsa sor ve metriğe yaz.
- **Gereksinim:** FG-22, FG-24, FG-25, NFG-09, NFG-30.
- **Bağımlılık:** G-21.
- **Kabul:** Birim testler: sahte ekran dizileriyle uyuşma/uyuşmazlık/geri alma. [Cihaz] Sabit dizi sırasında elle başka ekrana geçilince asistan durup soruyor.

#### G-23 Kaydırma-keşif, kullanıcıya devir, adım bütçesi (O)
- **Yapılacak:** Ögenin görünmediği durumda yönlü kaydırarak arama (üst sınırlı); kullanıcı seçimi gereken noktada ("hangi fotoğraf") durup devretme; adım bütçesi (V-17) aşımında devir.
- **Gereksinim:** FG-26, FG-27, FG-28.
- **Bağımlılık:** G-22.
- **Kabul:** Birim testler: keşif üst sınırı, bütçe aşımı, devir sesli mesajı.

#### G-24 İzinli uygulamalar ve banka engeli (K)
- **Yapılacak:** Her eylemden önce etkin paket izinli listede mi denetimi (V-14 varsayılanı); banka/ödeme engeli: bilinen Türk banka paket listesi + ad anahtar sözcükleri ("Bank", "Banka", "Ödeme" vb.); engelde sesli açıklama, işlem yok.
- **Gereksinim:** FG-60, FG-78, KD-02.
- **Bağımlılık:** G-20.
- **Kabul:** Birim testler: izin dışı ve banka paketlerinde eylem üretilmez.

**UD-4** uyum-analisti: FG-20..FG-28, FG-60, FG-78, NFG-29..30, NFG-38..40, NFG-52..53.

### KT-5 Risk derecesi ve koruma

#### G-25 Kural tabanlı risk sınıflandırıcı (O)
- **Yapılacak:** `:core` `RiskClassifier`: girdi = eylem + hedef öge + uygulama + ekran metinleri; kural tablosu (V-07) + Türkçe tehlikeli sözcük listesi (gönder, ara, sil, öde, satın al, şifre, onayla…) + ekran uyarı ifadeleri; belirsizde bir üst risk. Dil modeli ve tarif eylemleri dahil **tüm** eylemler buradan geçer (tek giriş noktası).
- **Gereksinim:** FG-70, FG-74, FG-75, FG-47, TK-10.
- **Bağımlılık:** G-20.
- **Kabul:** Tablo tabanlı birim testler (≥60 vaka; Tablo 3 örneklerinin hepsi).

#### G-26 Risk dereceli onay akışları ve gizli alan devri (O)
- **Yapılacak:** Düşük: anlat ve yap. Orta: hedefi vurgula + ne yapacağını söyle + açık onay (sözlü "evet/onaylıyorum" ya da büyük etiketli düğme), 10 sn sessizlik = yapma. Yüksek: dur ve sor, iki adımlı onay (yanlış dokunuş onay sayılmaz). Parola/ödeme/kişisel bilgi alanında otomasyon durur, kullanıcı kendisi girer; kişisel bilgi istenmeden önce nedeni söylenir.
- **Gereksinim:** FG-71, FG-72, FG-73, FG-76, FG-77, NFG-06, NFG-07, NFG-08.
- **Bağımlılık:** G-25, G-22.
- **Kabul:** Birim testler: her düzeyde onaysız eylem yok, sessizlik = hayır, tek yanlış dokunuş Yüksek onayı vermez.

#### G-27 Doğrulama kodu ve dolandırıcılık ifadesi koruması (O)
- **Yapılacak:** Ekranda doğrulama kodu bağlamı ("doğrulama kodu", "kimseyle paylaşmayın", "tek kullanımlık şifre" vb.) görülünce: hiçbir işlem yok, yalnız sesli uyarı; rakam dizileri anında maskelenir, günlüğe/modele gitmez. Dolandırıcılık biçimli ifadelerde ("virüs tespit edildi", "hediye kazandınız") uyarı. Vizyon örneği (ÇEL-01) yalnız "Ben yapayım"da kodu okumadan "kod bu bölgede, kendiniz bakın" yönlendirmesi olarak (G-41).
- **Gereksinim:** FG-79, FG-81, NFG-67, KD-01.
- **Bağımlılık:** G-25.
- **Kabul:** Birim testler: Türkçe fikstürlerde işlem engeli, rakamların çıktılarda bulunmaması.

#### G-28 Beklenmedik açılır pencere ve ekran metni = veri (O)
- **Yapılacak:** Plan dışı pencere/üst katman/paket değişimi algılanınca dur ve "Bu, görevinizin parçası değil" de; ekran metni hiçbir yerde komut olarak yorumlanmaz (karar motoru yalnız onaylı komutu hedef kaynağı olarak kullanır); düşük güvenli plan eyleme dönmez, sorulur.
- **Gereksinim:** FG-80, FG-82, NFG-37, NFG-39.
- **Bağımlılık:** G-22, G-25.
- **Kabul:** Birim testler: ekranda "Ayarları sil" yazısı varken komut "Ayarları aç" iken hedef değişmez; beklenmeyen pencerede durma.

#### G-29 Güvenlik test uygulaması ve senaryo seti (O)
- **Yapılacak:** `:testapp` (yalnız debug): sahte "virüs" ve "hediye" açılır pencereleri, ekrana gömülü talimat, doğrulama kodu ekranı, sahte banka ekranı, riskli görev senaryoları (AS3 ile aynı set; kurgusal, kısa). UiAutomator enstrümanlı testleri: yanlış tıklama oranı (adım ve görev başına), enjeksiyonu yok sayma oranı, düşük riskte tamamlama ile yüksek riskte onay isteme oranı. `docs/olcum/guvenlik-testleri.md` sonuç şablonu.
- **Gereksinim:** NFG-45, DEG-15, DEG-16, DEG-17, DEG-18, NFG-62.
- **Bağımlılık:** G-26, G-27, G-28.
- **Kabul:** [Cihaz/emülatör] Enstrümanlı testler koşar ve oranları üretir; sahte ekranlarda hiçbir yüksek riskli eylem gerçekleşmez.

**UD-5** uyum-analisti: FG-70..FG-83, NFG-37..39, DEG-15..18.

### KT-6 Gizlilik, rıza ve güvenli bulut kanalı

#### G-30 Aydınlatma metni ve ayrı rızalar (O)
- **Yapılacak:** Sade Türkçe aydınlatma metni (NFG-81'in tüm kalemleri: veri sorumlusu [SS-13 yer tutucusu], amaç, giden veri = yalnız maskeli metin, alıcılar ve ülkeleri: Google/Gemini, Groq, vekil barındırıcı Cloudflare; ücretsiz katmanda verinin eğitimde kullanılabileceği; hukuki sebep; saklama süreleri; haklar ve geri alma yolu; erişilebilirlik kullanımı). Büyük yazı + sesli okuma. Önceden işaretsiz, **ayrı** rızalar: bulut/yurt dışı aktarım, tarif kaydı, araştırma ses kaydı. Rıza kaydı (zaman, metin sürümü) yerelde; ayarlardan tek adımda geri alma.
- **Yapılmayacak:** Hukuki görüş; S-01 kararı.
- **Gereksinim:** NFG-56, NFG-57, NFG-76, NFG-77, NFG-78, NFG-80, NFG-81, FG-64 (parça).
- **Bağımlılık:** G-02, G-08.
- **Kabul:** Birim testler: rızalar ayrı ve varsayılan kapalı, geri alınca durum anında "yok"; kayıt sürüm içerir. Metin öğrenci onayına sunulur.

#### G-31 Komut metni maskeleme (O)
- **Yapılacak:** `:core` `Masker` (komut metni): rehber adlarıyla eşleşme (rehber verisi arayüzle verilir), düzenli ifadeler (telefon, e-posta, TCKN sağlama, IBAN mod-97, kart Luhn, adres anahtar sözcükleri), 4+ haneli rakam dizileri; tutarlı yer tutucular ve yalnız bellekte duran eşleme tablosu; yanıttaki yer tutucuları cihazda geri çevirme. `MaskedText` tipi yalnız `Masker` tarafından üretilebilir.
- **Gereksinim:** NFG-50, NFG-79, NFG-46, FG-79.
- **Bağımlılık:** G-03.
- **Kabul:** `mask-corpus.json` komut örneklerinin %100'ü beklenen çıktıyla aynı; sızıntı testi: çıktıda korpustaki hiçbir gerçek değer yok.

#### G-32 Ekran özeti maskeleme ve son denetim (O)
- **Yapılacak:** `ScreenNode` listesinden buluta gidecek özet: arayüz etiketleri (son denetimden geçerek) kalır; liste içi serbest metin, düzenlenebilir alan içeriği, mesaj gövdesi `[METIN_n]`; rehberdeki adlar komuttakiyle aynı `[KISI_n]`; özel nitelikli içerik sözcükleri (sağlık vb.) maskelenir; izinli liste dışı uygulama adları çıkarılır. Son denetim: kalan rakam dizisi/rehber adı/düzenli ifade eşleşmesi varsa `MaskingRejected` → istek gönderilmez.
- **Gereksinim:** NFG-50, NFG-51, NFG-71, NFG-79, NFG-46, FG-50 (ilke).
- **Bağımlılık:** G-31, G-19.
- **Kabul:** Korpusun ekran örneklerinde sızıntı 0; bilerek bozulmuş girdide istek reddediliyor.

#### G-33 Vekil sunucu (O)
- **Yapılacak:** `proxy/` Cloudflare Worker: tek uç nokta; erişim kodu doğrulama; yalnız kısa süreli hız sınırı (V-19; normal kullanımı kesmeyecek düzeyde, günlük cihaz sınırı yok); yalnız beklenen JSON alanları (resim/ikili veri/beklenmeyen alan → reddet); Gemini'ye istek (anahtar gizli değişkende, model adı ayarda, düşünme kapalı, sıcaklık 0, JSON şema zorunlu), 429/5xx'te Groq'a geçiş (aynı şema); istek/yanıt gövdesi günlüğe yazılmaz; `node:test` birim testleri (sağlayıcılar sahte). `proxy/README.md`: kullanıcının yayına alma adımları (sade Türkçe).
- **Gereksinim:** NFG-41, NFG-42, NFG-43, NFG-51, NFG-76, NFG-80, NFG-25, TK-07.
- **Bağımlılık:** G-02, hazırlık D.
- **Kabul:** `node --test` geçer: yetkisiz istek reddi, kısa sürede aşırı istek (hız sınırı) reddi, normal tempoda ardışık isteklerin (ör. 1 dakikada 10) hiç reddedilmemesi, resim alanı reddi, Gemini kotası/hatasında Groq'a geçiş, ikisi de bitince istemcinin ayırt edebileceği "kota bitti" yanıtı. [Kullanıcı] Yayına alındıktan sonra `curl` ile tek deneme isteği şema uyumlu yanıt döner.

#### G-34 Bulut istemcisi: rıza kapısı, kota bitişi, hata, gösterge, denetim kopyası (O)
- **Yapılacak:** OkHttp (yalnız HTTPS) istemcisi, yalnız `MaskedText`/maskeli ekran özeti kabul eder; rıza yoksa çağrı **hiç** oluşturulmaz ve "Bu iş için internet yardımı gerekiyor, izin vermediniz" denir; cihazda kullanım sayacı ya da sınırı **yok**; vekil "kota bitti" derse sade, dürüst sesli bilgi ("İnternet yardımı şu an dolu, bildiğim işlerle devam edebilirim") + kurallar ve kayıtlı görevlerle sürme, aynı istek için yeniden çağrı yok; ağ/sunucu hatasında güvenli durma (yarım eylem yok) ve sesli bilgi; istek sırasında baloncukta görünür bulut göstergesi; giden maskeli isteğin denetim kopyası (V-15 süresi).
- **Gereksinim:** FG-103, NFG-25, NFG-32, NFG-43, NFG-58, NFG-78, NFG-79, FG-91, FG-17.
- **Bağımlılık:** G-30, G-32, G-33.
- **Kabul:** MockWebServer testleri: rıza yok/geri alındı → istek sayısı 0; ardışık çok sayıda istek cihazda engellenmez; vekilden "kota bitti" → sesli bilgi metni + cihaz içi kipe geçiş, yeniden deneme yok; zaman aşımı → güvenli durum; denetim kopyası maskeli.

#### G-35 Yanıt şeması, istem şablonu ve ayrıştırıcı (O)
- **Yapılacak:** Şema: `{geri_okuma, netlestirme_sorusu|null, guven: dusuk|yuksek, adimlar:[eylem + beklenen_durum]}`; istem şablonu (ekran metni "veri"dir talimatı, tek soru sınırı, sağlık tavsiyesi yok, kısa cümle); katı ayrıştırıcı: şemaya uymayan çıktı eylem üretmez; akıl yürütme alanı varsa atılır, seslendirilmez, saklanmaz; yer tutucular cihazda geri çevrilir; dönen her eylem G-25'ten geçer.
- **Gereksinim:** NFG-31, NFG-44, NFG-28, NFG-36, NFG-37, FG-74, FG-83, FG-100 (aynı çağrı).
- **Bağımlılık:** G-34, G-25.
- **Kabul:** Birim testler: bozuk/fazla alanlı/akıl yürütme içeren yanıtlar; sağlık sorusu reddi şablonu.

**UD-6** uyum-analisti: NFG-41..NFG-82 gizlilik maddeleri, DEG-33 hazırlığı, aydınlatma metni.

### KT-7 Netleştirme ve dil modeliyle planlama

#### G-36 Rehber erişimi ve kişi eşleştirme (O)
- **Yapılacak:** İsteğe bağlı `READ_CONTACTS` (nedeni söylenerek); Türkçe ad eşleştirme (ek atma: "Ayşe'yi", "Ayşe'ye"; büyük/küçük; ı/i); tek eşleşme → soru yok; çok eşleşme → seçenekleri adlarıyla oku (V-23); eşleşme yok/izin yok → ekrandan arama ya da tek soru. Rehber verisi buluta gitmez.
- **Gereksinim:** FG-101, NFG-06, NFG-68.
- **Bağımlılık:** G-10.
- **Kabul:** Birim testler: tek eşleşme 0 soru, çoklu eşleşme seçenek listesi, izin yok yolu.

#### G-37 Kural tabanlı netleştirme motoru (O)
- **Yapılacak:** Bilinen niyetler (ara, mesaj yaz, uygulama aç, ses/parlaklık/yazı boyutu, hava durumu) için cihaz verisiyle boşluk doldurma; sonuç: çözüldü / tek soru / dil modeline bırak; tur başına en çok bir soru; gereksiz soru metriği.
- **Gereksinim:** FG-06, FG-100, FG-101, FG-102, DEG-31, NFG-05, NFG-11.
- **Bağımlılık:** G-36, G-18.
- **Kabul:** DEG-31 birim test seti: tek eşleşmeli ad → 0 soru; çoklu → adlarla seçenek; gerçekten eksik komut → tam bir soru.

#### G-38 Dil modeliyle planlama döngüsü (B)
- **Yapılacak:** Karar motorunun 3. katmanı: maskeli komut + maskeli ekran özeti → tek çağrı → netleştirme sorusu ya da plan; geri okuma ve onay; adımlar G-22 doğrulamasıyla yürür; uyuşmazlıkta güncel ekranla yeni çağrı (adım bütçesi içinde); düşük güvende sor; "Sen yap" yürütücüsüne bağlantı.
- **Gereksinim:** FG-41, FG-40, FG-06, FG-07, FG-82, NFG-24, FG-100.
- **Bağımlılık:** G-35, G-37, G-23.
- **Kabul:** Birim testler (sahte dil modeli): netleştirme için ek çağrı yok; kural çözdüyse çağrı 0; uyuşmazlıkta yeniden planlama. [Cihaz] Rıza açıkken "Ayşe Test'e WhatsApp'tan merhaba yaz" → plan → Orta risk onayı → yazılır, gönder onaysız basılmaz.

#### G-39 Bağlam, dolaylı ifade ve katmanlı "neden" (O)
- **Yapılacak:** "Bu/şu" → odaktaki öge ve etkin uygulama; dolaylı ifade tablosu ("sesi çok az" → ses aç; "yazılar küçük" → yazı boyutu); "neden?" → önce tek satır, istenirse gerekçe (ekrana dayanarak).
- **Gereksinim:** FG-08, FG-09, FG-15, NFG-09.
- **Bağımlılık:** G-37.
- **Kabul:** Birim testler: ifade tablosu ve gönderme çözümü.

**UD-7** uyum-analisti: FG-06..FG-09, FG-40..41, FG-100..103, NFG-24, DEG-31.

### KT-8 "Ben yapayım" modu

#### G-40 Vurgu kaplaması (O)
- **Yapılacak:** Hedef ögenin çevresinde, ögeyi ve çevresini örtmeyen halka (V-05); adım yönlendirmede tek öge, "X nerede?" sorusunda en çok 3 aday; kaydırma yön oku; dokunmaya geçirgen.
- **Gereksinim:** FG-30, FG-31, FG-32, FG-33, TK-08.
- **Bağımlılık:** G-21.
- **Kabul:** Birim test: halka geometrisi öge kutusunu kapatmıyor. [Cihaz] Vurgu görünür, altındaki öge dokunulabilir.

#### G-41 Rehber yürütücü (B)
- **Yapılacak:** Aynı plan/tarif adımlarını dokunmadan sunar: vurgu + sesli yönlendirme; kullanıcının dokunuşunu erişilebilirlik olaylarıyla izler; doğruysa sonraki adım; yanlışsa yumuşak ses + titreşim ve doğru ögeyi yeniden gösterme; simgeli düğmeleri sözcükle tarif ("sağ üstteki üç nokta"); doğrulama kodu ekranında kodu okumadan yönlendirme (ÇEL-01).
- **Gereksinim:** FG-30, FG-34, FG-38, NFG-12, FG-79.
- **Bağımlılık:** G-40, G-38.
- **Kabul:** Birim testler (sahte olay dizileri): doğru/yanlış dokunuş akışı. [Cihaz] "Ben yapayım" ile Ayarlar'da yazı boyutuna adım adım ulaşılıyor, asistan hiç dokunmuyor.

#### G-42 Rehberlik düzeyleri, hareketsizlik yardımı, mod geçişi (O)
- **Yapılacak:** Üç ayrıntı düzeyi, aynı görevde başarı arttıkça kısalma; V-18 süresince hareketsizlikte yardım önerisi; "yardım" denince doğru öge, istenmeden gösterilmez; görev ortasında "sen yap"/"ben yapayım" geçişi; varsayılan mod V-09.
- **Gereksinim:** FG-35, FG-36, FG-37, FG-39, FG-63 (parça).
- **Bağımlılık:** G-41.
- **Kabul:** Birim testler: düzey azalma kuralı, zamanlayıcı, mod geçişinde adım kaybı yok.

**UD-8** uyum-analisti: FG-30..FG-39, NFG-12.

### KT-9 Görev belleği

#### G-43 Gömme modeli entegrasyonu ve A15 ölçümü (B)
- **Yapılacak:** ONNX Runtime ile V-03 modeli (int8); belirteçleyici (ONNX Runtime Extensions ya da Kotlin'de Unigram; referans belirteç kimlikleri ücretsiz araçla bir kez üretilip test verisi olur); maskeli komuttan vektör; [Cihaz] boyut, ilk yükleme, sorgu gecikmesi, tepe bellek; Türkçe eş anlamlı komut çiftlerinde benzerlik kontrolü; `docs/olcum/gomme.md`. A15'te kabul edilemezse daha küçük model ya da sözcüksel eşleşme önerisi (öğrenci onayı).
- **Gereksinim:** FG-44, NFG-72, NFG-27, NFG-74, TK-05.
- **Bağımlılık:** G-18.
- **Kabul:** Birim test: belirteç kimlikleri referansla aynı; [Cihaz] ölçüm belgesi dolu.

#### G-44 Tarif deposu ve benzerlik araması (O)
- **Yapılacak:** Room: tarif = niyet şablonu (maskeli), parametreler, uygulama paketi + sürüm kodu, adımlar (öge seçicileri: kaynak kimliği, sınıf, etiket şablonu), beklenen durumlar, vektör, başarısızlık işareti, son kullanım. Kişisel içerik alanı yok (yazılan metin, parola, kod saklanmaz; parametre olur). Kosinüs araması, eşik V-03.
- **Gereksinim:** FG-43, FG-44, FG-45, FG-50, NFG-43, NFG-54.
- **Bağımlılık:** G-43.
- **Kabul:** Birim testler: eşik altı → boş; kayıt sırasında metin değerlerinin parametreye çevrildiği; şemada serbest kişisel alan olmadığı.

#### G-45 Tarif kaydetme onayı, listeleme, silme (O)
- **Yapılacak:** Tarif kaydı rızası varsa, dil modeliyle başarıyla biten görevin sonunda V-16 sorusu; "evet" → kayıt. "Kayıtlı görevlerim" ekranı (büyük yazı, sesli okuma), tek tek silme; sesle "bunu unut".
- **Gereksinim:** FG-42, FG-48, NFG-55, NFG-56.
- **Bağımlılık:** G-44, G-30.
- **Kabul:** Birim testler: rıza yoksa soru da kayıt da yok; silinen tarif aramada çıkmaz.

#### G-46 Tarif oynatma ve uyuşmazlıkta dil modeline düşme (B)
- **Yapılacak:** Eşleşen tarif dil modeline sorulmadan oynatılır (internetsiz çalışır); her adımda ekran karşılaştırması; uyuşmazlıkta (güncelleme, açılır pencere, kullanıcı müdahalesi) rıza varsa dil modeli kalan adımlar için devreye girer, yoksa dur ve devret; başarısız tarif işaretlenir, kullanılmaz; uygulama sürüm kodu değişmişse ilk oynatma doğrulamalı (her adımda onay); risk sınıflandırma ve onay aynen uygulanır; "Ben yapayım"da tarif rehber yürütücüyle sunulur.
- **Gereksinim:** FG-43, FG-46, FG-47, FG-49, NFG-26, NFG-35.
- **Bağımlılık:** G-45, G-38, G-41.
- **Kabul:** Birim testler: uyuşmazlıkta katman geçişi, sürüm değişiminde doğrulama kipi, işaretli tarifin atlanması. [Cihaz] Uçak modunda kayıtlı "Ayşe Test'i ara" çalışıyor (arama başlatma Orta risk onayıyla).

#### G-47 Hazır tarifler (O)
- **Yapılacak:** Uygulamayla gelen tarifler: kişi arama (Telefon), mesaj yazma (Mesajlar/WhatsApp), ayar (ses, parlaklık, yazı boyutu), hava durumu; A15 (One UI) ve emülatör (AOSP) için seçiciler; uymazsa normal katman sırasına düşer.
- **Gereksinim:** FG-51, NFG-26, DEG-11 (görev dörtlüsü).
- **Bağımlılık:** G-46.
- **Kabul:** Birim testler: tariflerin şema doğrulaması. [Cihaz] Dört görev A15'te dil modeli çağrısı olmadan tamamlanıyor (metrikte çağrı = 0).

**UD-9** uyum-analisti: FG-40..FG-51, NFG-26, NFG-35, NFG-54.

### KT-10 İlk kurulum, aile ayarları, veri hakları

#### G-48 Aile ayarları (O)
- **Yapılacak:** PIN'li ayar ekranı (PIN tuzlu özetle saklanır): izinli uygulamalar, rehberlik düzeyi, konuşma hızı, yazı boyutu, susma süresi, sesli okuma gizliliği (V-23), kota görünümü. Görev kayıtları/içerik bu ekranda **yok**.
- **Gereksinim:** FG-60, FG-61, FG-62, FG-63, NFG-15, TK-12.
- **Bağımlılık:** G-24, G-42.
- **Kabul:** Birim testler: yanlış PIN'de değişiklik yok; ayar ekranı veri modelinde tarif/günlük erişimi yok.

#### G-49 Sesli ilk kurulum sihirbazı (O)
- **Yapılacak:** Sesli ve büyük yazılı adımlar: karşılama → mikrofon izni → erişilebilirlik bilgilendirmesi (G-05) → kısıtlı ayarlar yardımı → aydınlatma ve ayrı rızalar (G-30) → rehber izni (isteğe bağlı) → pil kısıtı muafiyeti yönlendirmesi → TTS/tanıma paketi denetimi → iki modun kısa tanıtımı → erişim kodu girişi (rıza verildiyse).
- **Gereksinim:** FG-64, NFG-65, NFG-56, NFG-73, TK-15.
- **Bağımlılık:** G-30, G-48.
- **Kabul:** Birim test: adım sırası ve rıza öncesi bulut adımının atlanması. [Cihaz] Temiz kurulumda sihirbaz baştan sona tamamlanıyor.

#### G-50 Veri hakları ve saklama süreleri (O)
- **Yapılacak:** "Verilerim" ekranı (ne saklanıyor, nerede, ne kadar; görme ve silme); tek düğmeyle "Tüm verilerimi sil" (tarifler, günlükler, rıza kaydı, denetim kopyaları, araştırma kayıtları); WorkManager ile V-15 sürelerinde otomatik silme; sesle "verilerimi sil".
- **Gereksinim:** NFG-54, NFG-55, NFG-59, NFG-60, NFG-82.
- **Bağımlılık:** G-45, G-34.
- **Kabul:** Birim testler: tümünü sil sonrası tüm tablolar boş; süresi dolan kayıt silinir.

#### G-51 Araştırma ses kaydı kipi (O)
- **Yapılacak:** Varsayılan kapalı, aile PIN'i + ayrı rıza ile açılır; kayıt Android Keystore anahtarıyla AES-GCM şifreli, uygulamaya özel alanda; katılımcı kodu (ad yok); cihazda WER hesaplanınca hemen, en geç V-15 süresinde silinir. Etik onay olmadan kullanılmayacağı ekranda yazılı.
- **Gereksinim:** NFG-61, NFG-49, NFG-56, NFG-64 (kullanım engeli metni).
- **Bağımlılık:** G-13, G-50.
- **Kabul:** Birim testler: rızasız kayıt yok; şifre çözümü yalnız uygulama içinde; silme sonrası dosya yok.

#### G-52 Metin, ton ve erişilebilirlik gözden geçirmesi (K)
- **Yapılacak:** Tüm `strings.xml` metinleri: kısa cümle, tek soru, yüzde yok, tutarlı terimler, yetkin gösteren dil, riskte sade ton; tüm kendi düğmeler metin etiketli; TalkBack içerik açıklamaları; kontrast denetimi; büyük dokunma hedefleri.
- **Gereksinim:** NFG-01, NFG-02, NFG-03, NFG-05, NFG-08, NFG-10, NFG-13, NFG-14.
- **Bağımlılık:** G-49.
- **Kabul:** Android Lint erişilebilirlik uyarısı 0; birim test: metinlerde "%" ve iki soru işareti yok.

**UD-10** uyum-analisti: FG-60..64, NFG-01..15, NFG-54..61, NFG-82.

### KT-11 Dayanıklılık, ölçüm ve teslim

#### G-53 Servis sağkalımı ve One UI pil kısıtı (O)
- **Yapılacak:** Erişilebilirlik hizmeti kapanınca/öldürülünce tespit (uygulama açılışı + WorkManager periyodik denetim) ve bildirim + uygulama açılınca sesli bilgi; pil muafiyeti ve Samsung "hiç uyumayan uygulamalar" yönlendirmesi; ön plan hizmeti yalnız dinleme sırasında. [Cihaz] A15'te 8+ saat bekleme sonrası baloncuk ve hizmet testi; sonuç `docs/olcum/sagkalim.md`.
- **Gereksinim:** NFG-73, NFG-34, DEG-34.
- **Bağımlılık:** G-49.
- **Kabul:** Birim test: denetim mantığı; [Cihaz] gece boyu testi sonucu kayıtlı.

#### G-54 Performans ve bellek ölçümü (O)
- **Yapılacak:** [Cihaz] A15'te V-06 eşiklerinin ölçümü (dokunma→dinleme, komut sonu→ilk geri bildirim, katman başına süre), modeller yüklüyken tepe bellek; tembel yükleme ve bellek baskısında boşaltma ayarı; `docs/olcum/performans.md` ve eşik önerisi (TK-09 için öğrenciye).
- **Gereksinim:** NFG-22, NFG-23, NFG-27, NFG-72, DEG-19, DEG-34.
- **Bağımlılık:** G-47.
- **Kabul:** Ölçüm belgesi en az 20 tekrarla ortalama ve SD içeriyor; eşik dışı kalemler işaretli.

#### G-55 Çevrimdışı değerlendirme koşucusu (O)
- **Yapılacak:** Komut seti biçimi (`eval/komutlar.csv`: komut, beklenen niyet, beklenen boşluklar, beklenen soru var/yok); JVM koşucusu: kural katmanı ve netleştirme (DEG-31 gereksiz soru oranı), niyet doğruluğu (Yol B için DEG-03 desteği), maskeleme sızıntısı, ham/yeniden ifade karşılaştırması; isteğe bağlı dil modeli koşusu (sabit sıcaklık, 3 tekrar); rapor `docs/olcum/degerlendirme.md`.
- **Gereksinim:** DEG-31, DEG-02/DEG-03 (sistem düzeyi), DEG-19, DEG-22, DEG-24, DEG-25, DEG-26, NFG-36.
- **Bağımlılık:** G-38, G-47, hazırlık F-17.
- **Kabul:** Koşucu örnek 10 satırlık setle rapor üretir; birim testleri geçer.

#### G-56 Uçtan uca gizlilik denetimi (O)
- **Yapılacak:** [Cihaz] Rıza kapalı/geri alınmış iken bir dizi görev → vekil sunucuya gelen istek sayısı 0 (vekil sayacı + yerel ağ günlüğü); rıza açıkken denetim kopyalarının otomatik taranması → maskesiz kişisel veri 0; istek gövdelerinde ses/resim alanı yok; "tüm verilerimi sil" testi. `docs/olcum/gizlilik-denetimi.md`.
- **Gereksinim:** DEG-33, DEG-21, NFG-48, NFG-51, NFG-79, NFG-82.
- **Bağımlılık:** G-50, G-38.
- **Kabul:** Belgede üç ölçüm de 0; sıfır değilse hata kaydı ve düzeltme görevi önerisi.

#### G-57 Uyumluluk geçişi (O)
- **Yapılacak:** Ücretsiz emülatörde Android 10 ve Android 14+ görüntüleriyle enstrümanlı testler; ikinci marka telefon varsa aynı testler; sürüme özgü düzeltmeler (küçük); sonuç tablosu.
- **Gereksinim:** NFG-33, NFG-72, TK-15.
- **Bağımlılık:** G-53.
- **Kabul:** Emülatörde iki sürümde testler geçer; ikinci marka yoksa bu açıkça "eksik" yazılır.

#### G-58 Yayın hazırlığı belgeleri ve release yapısı (K)
- **Yapılacak:** Release APK (R8, `SafeLog` içerik günlüğü yok — derlenmiş APK'da test); manifest izin testi son hâli; Türkçe gizlilik politikası (aydınlatmayla tutarlı; ücretsiz barındırma önerisi GitHub Pages); Play Veri Güvenliği formu ve API 31+ erişilebilirlik beyanı **taslakları** (yayın yapılmayacak, KD-04).
- **Gereksinim:** NFG-47, NFG-66, NFG-67, NFG-68, NFG-69.
- **Bağımlılık:** G-56.
- **Kabul:** Release yapısı derlenir; release APK'da içerik günlük çağrısı bulunmadığını doğrulayan test geçer; taslak belgeler `docs/yayin/` altında.

**UD-11 (son)** uyum-analisti: tüm Zorunlu gereksinimlerin kodla karşılaştırması ve Bölüm 6 tablosunun güncellenmesi.

---

## 5. Riskler ve geri çekilme planları

| Risk | Belirti | Önlem | Geri çekilme |
|---|---|---|---|
| **Gecikme hedefi (NFG-22)** kaçırılır: dil modeli turu 3-8 sn sürebilir (ÇEL-04) | G-54'te komut sonu→ilk geri bildirim >2 sn, görev boyunca uzun sessizlik | Kural ve kayıtlı tarif katmanları dil modelini atlar; ilk sesli geri bildirim (geri okuma) buluttan önce, cihazda; >5 sn'de sesli bilgi; düşünme kapalı küçük model; plan tek çağrıda birden çok adım döner | Sık görevler hazır tariflere (G-47) taşınır; en hızlı ücretsiz model (Flash-Lite sınıfı) seçilir; eşikler ölçümle öğrenciyle yeniden belirlenir (TK-09) |
| **A15'te Whisper yavaş/ağır** | G-14'te small/base gerçek zamandan yavaş, bellek baskısı | Sıkıştırılmış modeller, VAD ile yalnız konuşma bölümünün işlenmesi, model boşaltma | Varsayılan motor yerleşik çevrimdışı tanıyıcı (API 31+), Whisper tiny/base yedek; small yalnız hoca tablosu için ölçülür (DEG-14 yine üretilir) |
| **One UI hizmeti öldürür (NFG-73)** | Uzun bekleme sonrası baloncuk yok | Hizmet erişilebilirlik hizmeti olarak bağlı (sistem korur), pil muafiyeti ve "hiç uyumayan uygulamalar" yönlendirmesi, ağır iş yalnız oturumda | Tespit + bildirim + uygulama açılınca sesli yönlendirme (G-53); yeniden açma tek dokunuş |
| **Ücretsiz kota biter (NFG-25)** | Sağlayıcıdan 429 hataları | Kural/tarif önceliği (bulut çağrısını azaltır), aynı istek için yeniden çağrı yok; kullanıcıya görünen sınır yok, vekilde yalnız kötüye kullanım hız sınırı | Groq yedeği (aynı rıza/maskeleme); o da biterse yalnız cihaz içi kip ve sesli bilgi. **Ücretli plana geçilmez** (Ek C) |
| Ücretsiz katman koşulları değişir (Gemini/Groq/Cloudflare) | G-02 sonrası değişiklik, kart isteme | Model adı/sağlayıcı yalnız vekil ayarında | Vekil öğrencinin bilgisayarına taşınır (demo); sağlayıcı değişirse aydınlatma metni güncellenir ve rıza yeniden alınır |
| Android 14 arka plan mikrofon kısıtı | Baloncuktan dinleme başka uygulama öndeyken açılmıyor | G-07'de erken sınama | Dokunuşta saydam etkinlik açılıp dinleme |
| Türkçe dil modeli başarımı düşük (%50 altı, MAKALE-12) | Yanlış plan, sık uyuşmazlık | Her adım doğrulama, düşük güvende sor, adım bütçesi | Kullanıcıya devir, "Ben yapayım" kipi önerisi, hazır tariflerin artırılması |
| Gömme modeli A15'e ağır | G-43'te gecikme/bellek yüksek | int8, tembel yükleme | Daha küçük model ya da normalleştirilmiş niyet+boşluk sözcüksel eşleşmesi (öğrenci onayıyla) |
| Araya girmede yankı | TTS kendi sesini "konuşma" sanıyor | Yankı engelleyici, eşik, TTS sırasında daha yüksek VAD eşiği | Araya girme yalnız sabit sözcüklerde ("dur") ya da baloncuğa dokunmayla |
| OEM'e göre farklı ekranlar | Hazır tarif seçicileri tutmuyor | Seçici yedekleri (kaynak kimliği → etiket → rol) | Dil modeli katmanına düşme; ikinci marka yoksa sınırlılık olarak raporlanır |

---

## 6. Gereksinim kapsama tablosu

| Gereksinimler | Görev(ler) |
|---|---|
| FG-01, FG-11 | G-06 |
| FG-02, FG-03, FG-05 | G-07, G-11, G-16, G-17 |
| FG-04 | G-11 |
| FG-06, FG-100, FG-101, FG-102 | G-36, G-37, G-38, G-10 |
| FG-07, FG-16, FG-17 | G-09, G-11, G-34, G-38 |
| FG-08, FG-09, FG-15 | G-39 |
| FG-12, FG-13, FG-14 | G-16, G-17 |
| FG-20, FG-23 | G-20, G-17 |
| FG-21 | G-21 |
| FG-22, FG-24, FG-25 | G-22 |
| FG-26, FG-27, FG-28 | G-23 |
| FG-30..FG-34, FG-38 | G-40, G-41 |
| FG-35, FG-36, FG-37, FG-39 | G-42 |
| FG-40, FG-41 | G-10, G-38 |
| FG-42, FG-48 | G-45 |
| FG-43, FG-44, FG-45, FG-50 | G-43, G-44 |
| FG-46, FG-47, FG-49 | G-46, G-25 |
| FG-51 | G-47 |
| FG-60, FG-78 | G-24, G-19 |
| FG-61, FG-62, FG-63 | G-48 |
| FG-64 | G-49, G-30, G-05 |
| FG-70..FG-77 | G-25, G-26, G-35 |
| FG-79, FG-81 | G-27, G-31 |
| FG-80, FG-82 | G-28 |
| FG-83 | G-35 |
| FG-90, FG-92 | G-18 |
| FG-91 | G-34 |
| FG-103 | G-34 |
| NFG-01..03, 05, 08, 10, 13, 14 | G-52, G-04, G-06 |
| NFG-04, NFG-20 | G-08 |
| NFG-06, NFG-07 | G-26, G-36, G-09 |
| NFG-09, NFG-11, NFG-12, NFG-15 | G-22, G-39, G-37, G-41, G-48 |
| NFG-16, 17, 18, 48, 49 | G-07, G-12, G-15, G-16 |
| NFG-19, NFG-21, NFG-75 | G-13, G-14, G-15 |
| NFG-22, NFG-23, NFG-72 | G-54, G-12 |
| NFG-24, NFG-28, NFG-31, NFG-36, NFG-44 | G-10, G-35, G-38 |
| NFG-25, NFG-32, NFG-58 | G-33, G-34 |
| NFG-26, NFG-35 | G-46, G-47 |
| NFG-27 | G-18, G-54 |
| NFG-29, NFG-30, NFG-40, NFG-52, NFG-53 | G-19, G-22, G-06 |
| NFG-33 | G-57 (kısmi, bkz. S-05) |
| NFG-34, NFG-73 | G-53 |
| NFG-37, NFG-38, NFG-39 | G-20, G-28, G-35 |
| NFG-41, NFG-42, NFG-43 | G-33, G-34, G-04 |
| NFG-45 | G-29 |
| NFG-46, NFG-50, NFG-51, NFG-71, NFG-79 | G-03, G-31, G-32, G-34, G-33 |
| NFG-47, NFG-66, NFG-69 | G-58 (+ tüm KVKK görevleri) |
| NFG-54, NFG-55, NFG-59, NFG-60, NFG-82 | G-50, G-45 |
| NFG-56, NFG-57, NFG-76, NFG-77, NFG-78, NFG-80, NFG-81 | G-02, G-30, G-33, G-34 |
| NFG-61 | G-51 |
| NFG-62 | G-29, hazırlık E |
| NFG-65 | G-05, G-49 |
| NFG-67, NFG-68 | G-04, G-58 |
| NFG-74 | Mimari + G-04 bağımlılık listesi |
| DEG-11 (görev dörtlüsü), DEG-14, DEG-32 | G-47, G-13, G-14 |
| DEG-15, DEG-16, DEG-17, DEG-18 | G-29 |
| DEG-19, DEG-20 | G-18, G-54, G-55 |
| DEG-21, DEG-33 | G-56 |
| DEG-30 | G-11 |
| DEG-31 | G-37, G-55 |
| DEG-34 | G-53, G-54 |
| DEG-22..26 (Olmalı) | G-55 |

**Hiçbir görevce karşılanmayan Zorunlu gereksinimler (bilinçli, plan dışı):**
- **Kullanıcı çalışması ve belge işi** (talimat gereği planda yok; öğrenci en sonda yapar): DEG-01, DEG-04..DEG-10, DEG-12, DEG-13, DEG-29, NFG-63, NFG-64 (G-51 yalnız kullanım engeli metni koyar). DEG-02, DEG-03, DEG-06 yalnız **sistem düzeyinde** (Yol B) G-55/G-29 ile desteklenir; katılımcılı kısmı plan dışıdır.
- **Kısmi:** NFG-33 (ikinci marka cihaz yoksa karşılanamaz, S-05). NFG-81 veri sorumlusu kalemi SS-13 gelene dek eksik.
- Zorunlu olmayıp planlanmayan: FG-10 (Olabilir), NFG-70 (Olabilir, öğrenci kararıyla yok), DEG-27, DEG-28 (Olabilir; metrikler G-18'de kısmen var).

---

## 7. Raporlanan sorunlar (çözülmedi, karar gerekli)

- **S-01 KVKK m.9 ve açık rıza:** Ek D'deki analist notuna göre 2024 değişikliğinden sonra açık rıza yalnız arızi aktarımlarda dayanak olabilir; asistanın sürekli bulut çağrısı buna uymayabilir. Bu doğruysa NFG-76..NFG-78'in "rıza ile yurt dışı aktarım" tasarımı hukuken yetersiz kalabilir. G-02 resmî metni getirdi (`docs/DOGRULAMALAR.md` §4): m.9/6-a açık rıza yalnız "arızi olmak kaydıyla" geçerli; Yönetmelik m.16/1 (RG 10.7.2024, 32598) arızi = "süreklilik arz etmeyen ve olağan faaliyet akışı içinde bulunmayan". Analist notu doğrulandı; SAPMALAR'daki "asıl güvence maskeleme, rıza ek katman" kararıyla uyumlu. Ayrıca Gemini ücretsiz katman koşulu kişisel veri gönderilmesini yasaklıyor. Son onay öğrencinindir.
- **S-02 Vekil sunucu da bir alıcıdır:** NFG-80/NFG-81 alıcı olarak Gemini ve Groq'u sayar; ancak maskeli metin vekil barındırıcıdan (Cloudflare, yurt dışı) da geçer. Aydınlatma metnine üçüncü alıcı olarak eklenmesi gerekir (G-30 ekler); gereksinim metninin güncellenmesi önerilir.
- **S-03 Yerleşik tanıyıcının çevrimdışı garantisi yalnız Android 12+ (API 31+):** Android 10-11'de yerleşik tanıyıcı yalnız "çevrimdışı tercih" sunar, garanti etmez; NFG-17/NFG-48 nedeniyle bu sürümlerde yalnız Whisper kullanılabilir. Yerleşik tanıyıcı seçilse bile eski sürümlerde Whisper şarttır (NFG-72 "her telefonda iyi çalışma" için ek yük).
- **S-04 Vekil sunucunun kötüye kullanıma karşı korunması:** Ücretsiz ve güçlü bir cihaz doğrulama yolu yok (Play Integrity, Play Console hesabı gerektirir = ücret). Erişim kodu + oran sınırı yalnız makul düzeyde koruma sağlar (NFG-42 "Olmalı"); demo kapsamında kabul edilebilir.
- **S-05 İkinci marka cihaz (NFG-33, Zorunlu):** SS-09 açık; cihaz yoksa gereksinim karşılanamaz ya da ödünç cihaz gerekir (maliyet riski). Emülatör bir marka sayılmaz.
- **S-06 Aile ayarları ve kayıt gizliliği (FG-61):** Aynı cihazda aile üyesi PIN'siz kullanıcı ekranlarına (kayıtlı görevler) fiziksel olarak erişebilir; uygulama aile ekranında içerik göstermez ama tam ayrım cihaz kilidine bağlıdır.
- **S-07 FG-14 ve FG-02 (ÇÖZÜLDÜ 17.11.2026):** Araya girme için mikrofon yalnız baloncuğa basılarak başlamış oturum boyunca açıktır; öğrenci onayladı (SAPMALAR).
- **S-08 WhatsApp testi:** Test için ayrı numara/hat gerekirse ücret doğabilir; gerçek hesap kullanımı NFG-62'ye aykırıdır.
