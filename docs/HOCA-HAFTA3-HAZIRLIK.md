# Hafta 3 hazırlığı: durum, teknik anlatım, veri güvenliği (18.11.2026)

Yazıldığı kişi: Şafak (hocaya anlatım için). Ayrıntılı kaynaklar: UYGULAMA-DURUMU.md, SAPMALAR.md, MASKELEME.md, GEREKSINIMLER.md.

## 1. Kullanıcı şu an ne yapabiliyor? (telefonda denendi, Galaxy A15)

Yapay zekâ (bulut) bağlı DEĞİL. Her şey telefonun içindeki kurallarla çalışıyor.

| Komut | Sonuç |
|---|---|
| "Ayarları aç", "hesap makinesini aç" vb. uygulama açma | Çalışıyor (izinli uygulama listesi) |
| "WhatsApp'tan Şafak'a fotoğraf gönder" (Sen yap) | Çalışıyor: onay sorusu, galeri seçici, Gönder'e basar |
| Aynısı "Ben yapayım" (öğretmen kipi) | Çalışıyor: sarı halka + sesli yönlendirme adım adım |
| Mesaj yazıp gönderme | YOK (planlandı, demoya alınmadı) |
| Serbest cümleyle istenen karmaşık iş | YOK, "Bunu henüz yapamıyorum" der (yapay zekâ planlama bağlanmadı) |
| Gizlilik ekranı, ayrı rızalar, "Tüm verilerimi sil" | Çalışıyor |

Test: 1468 otomatik test, 0 hata. Bilinen zayıflık: konuşma tanıma "şafağa" gibi yumuşayan ekleri bazen yanlış duyuyor.

## 2. Bu uygulama bu işi nasıl yapıyor? (hocanın "nasıl" sorusu)

**Ekranı görme ve dokunma: Android Erişilebilirlik Servisi (AccessibilityService).**
Uygulama görüntü okumaz. Android'in her ekran öğesi için verdiği "etiket + konum + tıklanabilir mi" ağacını okur (ScreenReader). Ekran görüntüsü alınmaz. Şifre alanları hiç okunmaz. Dokunma ve kaydırma aynı servisle yapılır (ActionExecutor). Neden: Google Play'in bu amaçla izin verdiği resmi yol; Voice Access ve TalkBack da aynı mekanizmayı kullanır.

**Üç katmanlı karar (hibrit):**
1. Cihaz üstü kurallar: komut kelimeleri, kişi eşleme, "kimi kastettin" netleştirme soruları. Hızlı, çevrimdışı, ücretsiz.
2. Kayıtlı görev tarifleri (hocanın "deterministik otomasyon" önerisi): altyapı var, içi boş.
3. Dil modeli (Gemini → Groq → cihaz): yalnız kural çözemezse, yalnız maskelenmiş metinle. Plan şemalı JSON döner; uygulama her adımı kendi risk tablosundan geçirir. İstemci hazır, planlama döngüsü (G-38) anahtarlar girilince bağlanacak.
Neden hibrit: AutoDroid (%51,7), AppAgent (%84,4) çalışmaları saf modelin hatalı ve pahalı olduğunu gösteriyor; ElderBench yaşlıda belirsiz komutta modelin sormayı unuttuğunu gösteriyor. Kural + netleştirme bunu kapatır.

**Ses:**
- Konuşma tanıma: Android'in çevrimdışı Türkçe tanıyıcısı (Android 12+), yedek olarak Whisper (tiny/base/small, whisper.cpp ile telefonda, NDK). Seçim ve model indirme (SHA-256 doğrulamalı) EnginePolicy'de. Radford vd. (2023) Whisper kaynağı; van Dijk (2025) yaşlı konuşmasında Whisper sonuçlarını verir. Hız/hata ölçümü (hocanın gelecek çalışma 3'ü) telefon bağlıyken `tools\asr-olcum.ps1` ile yapılacak.
- Sesli yanıt: Google TTS çevrimdışı Türkçe sesler.

**Anlama:** kural katmanı + (planlı) multilingual-e5-small gömme modeli (ONNX int8) ile kayıtlı görev eşleme; yanlış eşleşmeyi önlemek için eşik 0,95 + fiil çakışma kontrolü.

**Güvenlik ve risk:** her eylem DÜŞÜK/ORTA/YÜKSEK risk tablosuna girer. Fotoğraf göndermek "Sen yap"ta YÜKSEK (önce sesli onay sorusu), rehberde ORTA. Uygulama izin listesi dışına (bankalar dahil) dokunmaz. Her adımdan önce ekran doğrulanır, beklenmedik ekranda durur. "Dur" ve baloncuk her şeyi keser (pop-up saldırısı %86 ve MobileSafetyBench %70 bulgularına karşı).

**Arayüz:** Kotlin + Jetpack Compose, yüzen baloncuk (overlay), büyük yazı, sarı halka (TouchTraceOverlay). Proxy: Cloudflare Worker (Node testli).

## 3. Veri gizliliği, KVKK: ne yapıyoruz?

Çekirdek ilke: **ham ses, ekran görüntüsü ve kişisel veri telefondan çıkmaz.**

1. **Cihazda kalanlar:** ses kaydı, ekran içeriği, fotoğraflar. Fotoğraf için depolama izni bile istenmez (sistem fotoğraf seçicisi). Şifre/hassas alanlar okunmaz.
2. **Buluta giden tek şey: maskelenmiş metin.** Ad, telefon, TC, IBAN, rakam dizileri, kod vb. maskelenir (MASKELEME.md). Gönderimden hemen önce ikinci bir "son denetim" kapısı (AuditedRequest) ham veri kalmış mı bakar; kalmışsa gönderilmez.
3. **Aktarım:** HTTPS, kendi vekil sunucumuz (Cloudflare Worker). API anahtarı uygulamada tutulmaz, vekilde durur. Erişim kodu + dakikada 20 istek sınırı. Groq'ta sıfır veri saklama, Cloudflare günlükleri kapalı olacak.
4. **Rıza:** her amaç için ayrı, önceden seçili olmayan, "Kabul/Reddet" eşit görünümlü rıza; tek dokunuşla geri alma; aydınlatma metni sürümlü, sesli okunabilir. KVKK m.9 (1.9.2024 değişikliği) sonrası yurt dışı aktarımda ana güvence maskeleme, rıza ek katman.
5. **Silme hakkı:** "Tüm verilerimi sil" iki adımlı onayla rıza, günlük, ölçüm, kurulum bilgisi, aktif görevleri temizler.
6. **Şifreleme:** aktarımda TLS; cihazda tutulan ayarlar Android'in uygulama özel alanında. Soru gelirse: "Şifreleme yerine veriyi hiç göndermeme + maskeleme seçtik; gönderilen metin zaten kimlik içermiyor, ham veri hiç çıkmadığı için şifrelenecek çıkış verisi yok."
7. **Veri sorumlusu:** Şafak Meral (ad ve iletişim yer tutucuları son adımda doldurulacak: app/veri-sorumlusu.properties).
8. **Yapay zekâ kullanım yasağına karşı:** Play politikası modelin eylemi kendi başına yürütmesini yasaklar; bizde model yalnız plan önerir, her adımı insan tanımlı risk tablosu ve onay kontrol eder.

## 4. Hafta 2 geri bildirimi kontrol listesi: durum

| # | Madde | Durum | Not |
|---|---|---|---|
| 1 | Danışman görüşmesi, Yol A/B kararı | SENDE | Hocaya sor; yazılı not al. Şimdilik varsayılan **Yol B** (DEG-29): kullanıcı çalışması katılımcılı yapılmıyor, sınırlılık olarak raporlanır. Kararı belgenin başına AS'lerin altına tek cümleyle ekle |
| 2 | Yol B ise AS2/AS3 sistem düzeyinde yazıldı | Kısmen | Sistemde ölçülebilir: yanlış işlem oranı (riskli set), niyet doğruluğu (komut seti, G-55). Belge cümlesi Şafak'ta |
| 3 | Tablo 4: dakika dakika oturum akışı (≤40 dk) | BELGE İŞİ | Şafak; kod değil |
| 4 | AS1 için ayrı desen | BELGE İŞİ | 2-3 eksik komut, netleştirmeli/netleştirmesiz; ya da çevrimdışı kayıtlı komutlar. Sistemde netleştirme açılıp kapatılabilir ölçüm altyapısı var (G-55) |
| 5 | ExplorAR cümlesi düzeltmesi | BELGE İŞİ | Hocanın önerdiği cümle (34,4 sn karşı 18,3 sn) aynen kullanılabilir |
| 6 | Xu vd. (2025) DOI + sayfa | BELGE İŞİ | ACL Anthology'den |
| 7 | Li vd. (2025) sayfa aralığı | BELGE İŞİ | ACM DL'den |
| 8 | Radford, Brooke, Bangor kaynakları | BELGE İŞİ | Whisper kullanıyoruz, ekle |
| 9 | Yapay zekâ beyanına Şekil 2'nin üretimi | BELGE İŞİ | Hangi araçla çizildi, tek cümle; draw.io'da yeniden kur |
| 10 | Çalışan Android iskeletinden ekran görüntüsü/video | HAZIRLANABİLİR | Telefonda: Ayarlar açma, WhatsApp fotoğraf (Sen yap + Ben yapayım), Gizlilik ekranı. Ekran kaydı al |

Hocanın "gelecek çalışma" önerileri ve bizdeki karşılığı: (1) Play uyumlu Sen yap: görev tarifi altyapısı hazır, (3) Whisper ölçümü: araç hazır, telefonla yapılacak, (4) aile katmanı: planlı, yapılmadı, (2) Türkçe komut veri kümesi: katılımcı gerektirir, kapsam dışı.

## 5. Yapılacaklar
Kod: yapay zekâ planlama bağlama (anahtarlar sonrası), ilk açılış sihirbazı, "kendime gönder", görev hafızası, Whisper ölçümü, maskeleme kör testi, son gizlilik denetimi.
Sende: Gemini + Groq anahtarı, Cloudflare hesabı, veri sorumlusu bilgileri, aydınlatma metni onayı, hocayla Yol A/B görüşmesi.
