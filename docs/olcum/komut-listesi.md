# Konuşma tanıma ölçümü: 40 test komutu

Kaynak: `core/src/main/kotlin/tr/yasliasistan/core/asr/AsrCommandSet.kt` (bu liste ile aynı; değişirse ikisi birlikte değişir).
Tüm adlar, sayılar ve içerikler **uydurmadır**; kişisel veri yoktur. Sayılar bilerek yazıyla verilmiştir (insan nasıl okursa TTS de öyle okusun; ölçüm aracı "beş" ile "5"i Türkçe düzeyde aynı sayar).

**Birincil veri (17.11.2026 kararı):** Öğrenci kendi sesiyle kayıt yapmayacak. Ölçüm aracı bu 40 komutu telefonun **cihaz içi Türkçe TTS sesiyle** sentezler. Bu ses gerçek, özellikle yaşlı konuşmasını temsil etmez (bkz. `docs/olcum/OLCUM-ARACI.md` "Sınırlar" ve `docs/SAPMALAR.md`). Aşağıdaki kayıt talimatı, ileride gerçek kayıt yapılırsa diye korunmuştur.

| No | Komut |
|---|---|
| k01 | Ayarları aç |
| k02 | WhatsApp'ı aç |
| k03 | Kamerayı aç |
| k04 | Galeriyi aç ve son fotoğrafı göster |
| k05 | Ayşe'yi ara |
| k06 | Mehmet'e mesaj gönder |
| k07 | Kızıma mesaj yaz, akşam yemeğe geliyorum de |
| k08 | Oğlumu görüntülü ara |
| k09 | Fatma Teyze'ye sesli mesaj gönder |
| k10 | Son gelen mesajı oku |
| k11 | Ekranın parlaklığını biraz artır lütfen |
| k12 | Yazıları büyüt |
| k13 | Sesi kıs |
| k14 | Zil sesini aç |
| k15 | Wi-Fi'yi kapat |
| k16 | Bluetooth'u aç |
| k17 | Fener'i yak |
| k18 | Yarın sabah yedide alarm kur |
| k19 | Saat on beş otuzda ilacımı hatırlat |
| k20 | Beş dakika sonra beni uyar |
| k21 | Bugün hava nasıl olacak |
| k22 | Saat kaç |
| k23 | Bugün günlerden ne |
| k24 | Sıfır beş yüz elli beş, yüz on bir, yirmi iki, otuz üç numarayı ara |
| k25 | Telefonumun şarjı yüzde kaç |
| k26 | Ali'nin numarasını kaydet |
| k27 | Rehberde Zeynep'i bul |
| k28 | Son aramaları göster |
| k29 | Cevapsız aramaları göster |
| k30 | WhatsApp'ta Hasan'a fotoğraf gönder |
| k31 | Bu nasıl yapılır, bana göster |
| k32 | Ben yapayım, sen bana yol göster |
| k33 | Sen yap |
| k34 | Bu düğme ne işe yarıyor |
| k35 | Neden bunu yaptın |
| k36 | Baştan anlat |
| k37 | Tamam, hatırladım |
| k38 | Evet, onaylıyorum |
| k39 | Hayır, vazgeç |
| k40 | Dur |

(k24'teki numara geçersiz bir örnek aralıktır.)

## İleride gerçek kayıt yapılırsa (isteğe bağlı)

Yaşlı katılımcıdan kayıt **alınmaz** (etik onay yok, NFG-64). Yalnız gönüllü bir yetişkin, kendi açık izniyle.

1. Telefonun ses kaydedicisiyle ya da bilgisayarda **WAV, 16 bit** kaydedin (16 kHz ideal; başka hızlar araç tarafından dönüştürülür). Dosya adı komut numarası: `k01.wav` ... `k40.wav`.
2. Her komuttan önce ~0,5 sn, sonra ~1 sn sessiz bekleyin; telefon ağızdan 20-30 cm uzakta, normal konuşma hızında.
3. **Sessiz klipler:** aynı odada kimse konuşmazken 3 sn kayıt, 5 tane: `s01.wav` ... `s05.wav` (referans metni boş).
4. **TV gürültülü klipler:** televizyon ~2 m uzakta normal seste açıkken aynı komutlar: `k01g.wav` ... (koşul `gurultulu`).
5. Aynı klasöre `manifest.tsv` yazın (sekmeyle ayrılmış; `#` ile başlayan satır açıklamadır):
   ```
   k01	k01.wav	temiz	Ayarları aç
   k01g	k01g.wav	gurultulu	Ayarları aç
   s01	s01.wav	sessiz
   ```
6. Çalıştırma: `.\tools\asr-olcum.ps1 -Motorlar "system,tiny,base,small" -Veri klasor -KayitKlasoru C:\kayitlar`. Betik kayıtları telefona yalnız ölçüm süresince koyar, bitince telefondan siler. Kayıtlar depoya **eklenmez** (`.gitignore`); WER hesaplandıktan sonra bilgisayardan da silinmesi önerilir (V-15: en geç 7 gün).
