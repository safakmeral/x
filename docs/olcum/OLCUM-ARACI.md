# Konuşma tanıma ölçüm aracı (G-13)

Hocanın istediği Whisper tiny/base/small hız tablosu (DEG-14) ve motor seçimi denemesi (DEG-32, NFG-75) için. Araç yalnız **test APK'sında** vardır (`speech/src/androidTest/.../asr/`); kullanıcıya giden uygulamada yoktur.

**Durum (17.11.2026):** Araç, birim testleri (`:core` `asr` paketi) ve test APK'sı derlemesi tamam. **Cihaz koşusu yapılmadı: telefon bağlı değildi.** Bu yüzden henüz hiçbir motor için ölçülmüş sonuç yok; yerleşik tanıyıcının dosyadan beslenip beslenemeyeceği (EXTRA_AUDIO_SOURCE, izin) da telefonda ilk koşuda görülecek. İlk doğrulama için tek komut: `.\tools\asr-olcum.ps1 -Motorlar "system,tiny" -Limit 3 -Sessiz 2`

## Çalıştırma

Telefon USB ile bağlı, USB hata ayıklama açık olmalı. Proje klasöründe PowerShell:

```
.\tools\asr-olcum.ps1 -Motorlar tiny -Limit 5                       # kısa deneme
.\tools\asr-olcum.ps1 -Motorlar "system,tiny,base,small" -ModelKlasoru C:\modeller   # G-14 tam koşu
.\tools\asr-olcum.ps1 -Motorlar tiny -AudioCtx "0,768" -Beam "1,5" -Threads "2,4"    # ayar taraması
```

Betik: test APK'sını derler, **kaldırmadan** kurar (telefondaki modeller silinmesin diye; `connectedDebugAndroidTest` kaldırıp kurduğu için kullanılmaz), istenirse modelleri telefona yollar, ölçümü çalıştırır, sonuçları `docs/olcum/sonuclar/<çalıştırma>/` altına çeker. İlerleme: `adb logcat -s G13Asr`.

Parametreler: `-Motorlar` (`system`, `tiny`, `base`, `small`, `tiny-q5_1` ya da dosya adı), `-Veri` (`tts` | `klasor` | `hepsi`), `-Limit` (komut sayısı, en çok 40), `-Kosullar` (`temiz,gurultulu,sessiz`), `-Sessiz` (sessiz klip sayısı), `-Snr` (gürültülü klipte dB), `-AudioCtx`, `-Threads` (0 = cihaza göre), `-Beam` (virgülle birden çok değer = tarama), `-KlipleriDisaAktar` (sentezlenen klipleri WAV + `manifest.tsv` olarak telefona yazar; klasör kipini sınamak için).

Modeller (resmî kaynak, MIT): `https://huggingface.co/ggerganov/whisper.cpp` → `ggml-tiny.bin` (75 MB), `ggml-base.bin` (142 MB), `ggml-small.bin` (466 MB). Telefonda yeri: `Android/data/tr.yasliasistan.speech.test/files/whisper/`. `speech/src/androidTest/assets/whisper/` içine konan model (git dışı) APK'ya girer ve ilk koşuda oraya kopyalanır.

## Veri

1. **Birincil: TTS ile sentez.** `AsrCommandSet` içindeki 40 uydurma komut telefonun çevrimdışı Türkçe sesiyle (Google TTS) sentezlenir. Koşullar:
   - `temiz`: komut + önce 0,5 sn, sonra 1 sn sessizlik + çok hafif oda gürültüsü (-60 dBFS).
   - `gurultulu`: aynı + arka planda üç başka komut, perdesi kaydırılarak (farklı ses gibi) üst üste, **SNR 5 dB**. Gerçek TV gürültüsü yerine bir benzetimdir (NFG-21).
   - `sessiz`: yalnız oda gürültüsü (-65/-55/-45 dBFS, 2-4 sn), referans boş: halüsinasyon ölçümü.
2. **İsteğe bağlı: klasör.** `manifest.tsv` + WAV (biçim: `komut-listesi.md`). Gerçek insan kaydı gelirse aynı araç, aynı ölçütlerle ölçer.
3. **Açık lisanslı gerçek konuşma (Mozilla Common Voice Türkçe, CC0):** indirilmedi. Veri seti indirmek için Mozilla'da hesap/e-posta ve koşul onayı gerekiyor; bu, kişisel bilgi göndermeden yapılamaz ve öğrencinin kendi kararını ister. Öğrenci isterse kendi hesabıyla indirip klipleri `manifest.tsv` biçimine çevirerek klasör kipinde ölçebilir (lisans `THIRD_PARTY_NOTICES.md`'ye eklenmeli, ses dosyaları depoya konmamalı). Common Voice da yaşlı konuşmasını özel olarak temsil etmez.

## Ölçütler

| Sütun | Anlamı |
|---|---|
| WER ham / noktalama / Türkçe | Kelime hata oranı, 3 normalleştirme düzeyi (DEG-14): **ham** = yazıldığı gibi; **noktalama** = Türkçe küçük harf, noktalama yok, kesme işareti birleşik; **Türkçe** = ek olarak ş→s gibi harf katlama, sayı sözcükleri rakama ("beş" = 5), rakamdan sonraki ek atılır ("7'de" = 7), komşu rakam grupları birleşir (telefon numarası). Özet satırında WER = toplam hata / toplam referans kelime |
| CER | Karakter hata oranı (Türkçe düzey) |
| süre (sn/ifade) | Whisper: sesin tamamı verildikten metin çıkana dek çözme süresi. Yerleşik tanıyıcı: son ses baytı verildikten sonuç gelene dek. İkisi de "kullanıcı sustuktan sonra bekleme"ye karşılık gelir; Whisper'da konuşma sonu algılama (VAD) beklemesi ayrıca eklenir |
| RTF | Gerçek zaman oranı = toplam süre / toplam ses süresi (1'in altı = sesten hızlı) |
| model yükleme | Whisper modelinin belleğe yüklenme süresi (ilk komutta bir kez) |
| tepe bellek | Ölçüm sırasında test sürecinin en yüksek PSS'i (Whisper modeli dahil). Yerleşik tanıyıcı başka bir süreçte çalıştığı için onun belleği ölçülemez |
| halüsinasyon | Sessiz klipte metin üretilen klip / sessiz klip |
| döngü | Aynı 1-6 kelimelik öbeğin art arda en az 3 kez tekrarlandığı çıktı / klip |
| vad | `kesildi` = uygulamanın konuşma algılayıcısı konuşma bölümünü kesti (canlı kullanımdaki gibi); `reddetti` = konuşma bulamadı, klip bütün verildi (modelin kendi halüsinasyonunu görmek için) |

Dosyalar: `sonuclar.csv` (klip başına satır, makine okunur), `ozet.csv`, `ozet.md` (tablo), `bilgi.txt` (cihaz, TTS sesi, ayarlar, sorunlar).

## Sınırlar (rapora aynen geçmeli)

- **TTS sesi gerçek konuşma değildir.** Tek bir sentetik ses, temiz telaffuz, sabit hız, yaşlı konuşmasının özellikleri (titreme, yavaşlık, duraksama, ağız/diş protezi etkisi, lehçe) yok. Bu yüzden ölçülen WER, gerçek yaşlı kullanıcıdaki WER'in **alt sınırı** gibi okunmalı; motorların **birbirine göre** hız ve doğruluk sıralaması ve hız tablosu (DEG-14) için kullanılabilir. NFG-19'daki "yaşlı konuşmasında WER" ve "aksan/lehçe çeşitliliği" bu veriyle **karşılanmaz**.
- Gürültülü koşul TV değil, sentetik konuşma karışımıdır.
- TTS sesi Google'ın modelinden gelir; Google'ın tanıyıcısı aynı şirketin sentezine yatkın olabilir (yerleşik tanıyıcı lehine olası yanlılık).
- Yerleşik tanıyıcıya ses dosyadan `EXTRA_AUDIO_SOURCE` (API 33+) ile verilir; tanıyıcı bunu desteklemezse ya da izin isterse nedeni `bilgi.txt` ve `error` sütununda yazılır.

## Gizlilik

Ölçüm verisinde kişisel veri yok: komutlar uydurma, ses telefonun sentez sesi. Sesler yalnız bellekte işlenir; dışa aktarma yalnız istenirse ve yalnız sentez klipleri için yapılır. İnsan kaydı kullanılırsa betik onları ölçümden sonra telefondan siler; kayıtlar depoya konmaz (`.gitignore`).
