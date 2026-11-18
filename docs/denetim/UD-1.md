# UD-1: KT-1 uyum denetimi (G-04..G-11)

Kapsam: `core` (dialog, decision, masking, text, risk, log), `speech` (recognition, tts), `app/src/main`. Whisper (G-12) ve metrik günlüğü (G-18) denetlenmedi. Cihazda uçtan uca doğrulama yok (bilinen durum), bulgu sayılmadı. Kod okunarak yapıldı; çalıştırılmadı. "doğrulanmadı" = koddan çıkarım, cihazda ya da testte görülmedi.

Sayılar: Kritik 0, Önemli 6, Küçük 9.

## 1. Kapsam tablosu (iddia edilen gereksinimler)

| Gereksinim | Durum | Kanıt |
|---|---|---|
| FG-01 baloncuk, sürükleme | Var | OverlayController.kt:254-271 (TYPE_ACCESSIBILITY_OVERLAY), BubbleView.kt:63-81 |
| FG-02 mikrofon yalnız dokunuştan sonra | Var | VoiceInputCoordinator.kt:41-48; AssistantSession.kt:103 (yalnız dialog `StartListening` efekti). Her soru sonrası otomatik yeni tur oturum içindedir (SAPMALAR satır 15) |
| FG-03 Türkçe komut yazıya | Var (cihazda doğrulanmadı) | OnDeviceSystemEngine.kt:44-76 |
| FG-04 yazarak komut | Var | TypeCommandActivity.kt, AssistantSession.kt:63-78 |
| FG-05 susma süresi, tek dokunuşla yeniden deneme | Var | Defaults.kt:18-21, RecognitionTurn.kt:40-55; hata metinleri "Konuş düğmesine dokunun" (strings.xml:48,59) |
| FG-07 geri okuma + onay | Kısmen (bilinçli sapma, ÇEL-17) | DecisionCoordinator.kt:64-69; bkz. Ö-1, Ö-2 |
| FG-11 Dur her aşamada | Kısmen | OverlayState.kt:17; bkz. K-2 |
| FG-16 bitince sesle bildirim | Var | DialogMachine.kt:248-252, AssistantSession.kt:131 |
| FG-17 bekleme bilgisi | Var | DialogMachine.kt:205-211 (5 sn, sonra 10 sn) |
| FG-40, NFG-24 karar sırası | Var (kural; tarif ve dil modeli boş) | DecisionEngine.kt:35-48 |
| FG-101 tek eşleşmede soru yok, çoklu eşleşmede adlar | Var | OpenAppRule.kt:23-27, DecisionCoordinator.kt:71-79 |
| NFG-16/17/48 tanıma yalnız cihazda, çevrimiçine düşmez | Var | OnDeviceSystemEngine.kt:16-23,45-47,50; RecognizerErrors.kt:23-27 (ağ hatası = kullanılamaz). Varsayılan `SpeechRecognizer.createSpeechRecognizer` hiç çağrılmıyor |
| NFG-18 gürültüde komut yok | Kısmen | TranscriptFilter.kt:18-28 var; bkz. Ö-1 |
| NFG-49 ses saklanmaz | Var | `onBufferReceived` boş (OnDeviceSystemEngine.kt:127); AudioRecord yalnız `whisper` dizininde (denetlenmedi) |
| NFG-65 erişilebilirlik bilgilendirmesi + onay | Var | AccessibilitySetupFlow.kt:38-64 (onaysız yönlendirme yok), strings.xml:14-23 |
| NFG-53 hizmet boşta okumaz | Var | accessibility_service_config.xml:7-11 (`canRetrieveWindowContent=false`, olay türü yok), AssistantAccessibilityService.kt:143-145 boş |
| NFG-67 SMS/bildirim izni yok | Var | AndroidManifest.xml (yalnız RECORD_AUDIO), ManifestPermissionTest.kt:21-41 birleşik manifesti denetler |
| NFG-68 gereksiz izin yok | Var | SYSTEM_ALERT_WINDOW, QUERY_ALL_PACKAGES, INTERNET yok; `<queries>` yalnız LAUNCHER niyeti (manifest:21-26) |
| NFG-01/02/03 büyük yazı, kontrast, metin etiketi, hedef | Var (bkz. 3. bölüm) | Theme.kt:36-42, LargeButtons.kt:16, dimens.xml |
| NFG-04 sesli + yazılı çıktı | Var (TalkBack notu K-8) | AssistantSession.kt:105-108, CaptionView.kt |
| NFG-05 kısa cümle, tek soru, yüzde yok | Var | strings.xml:54-69 |
| NFG-20 Türkçe, yavaşlatılabilir TTS | Var (bkz. Ö-5) | SpeechRate, VoiceSelector |
| NFG-43 yerel veri uygulamaya özel, yedek kapalı | Var | manifest:30-32,38; data_extraction_rules.xml; yalnız SharedPreferences (balon konumu, konuşma hızı) |
| NFG-74 cihaz içi büyük model yok | Var | bağımlılık yok (app/build.gradle.kts:63-75) |
| DEG-30 ekran görüntüsü/video | Beklemede | cihazda demo yok; hocanın "bir sonraki teslim" talebi |

## 2. Bulgular

### Kritik
Yok. Bu aşamada buluta giden bir şey yok: INTERNET izni yok, ağ kodu yok (Grep: OkHttp, URL, Socket, java.net yok), `network_security_config` yalnız TLS. Maskeleme `app` içinde hiçbir yerden çağrılmıyor.

### Önemli

**Ö-1 | NFG-18, FG-07, FG-71 | RecognitionTurn.kt:68,78-80; DecisionCoordinator.kt:64-69**
Düşük riskli, kesin eşleşen komut onaysız çalışır (ÇEL-17). Ses tanıma son sonucu gelmezse ya da `NO_MATCH` olursa son ARA sonuç komut olur (RecognitionTurn.kt:68 ve 78-80). Yani doğrulanmamış, yarım bir transkript doğrudan uygulama açabilir. NFG-18 "uydurma transkript ... geri okuma ve onay olmadan eylem tetiklemez" der; SAPMALAR 23. satır NFG-18'i anmıyor. Etki bugün küçük (yalnız uygulama açma, geri tuşuyla geri alınır); Orta/Yüksek risk onay isteyeceği için asıl risk, aynı ara-sonuç yolunun onay cevabı olarak da kullanılması ("evet" parçası). Düzeltme: ara sonuçtan gelen metni `Heard` yerine "düşük güven" işaretle ve bu durumda `certain=false` say (geri okuma sorusu sor); SAPMALAR 23. satıra NFG-18 notu ekle. Ek: tanıyıcı güven skoru (`CONFIDENCE_SCORES`) varsa eşiğin altında soru sor.

**Ö-2 | FG-07, V-12 | core/dialog/ConfirmationParser.kt:16-19**
EVET kümesi geniş: "tamam", "olur", "peki", "yap", "onay" evet sayılır; V-12 yalnız "evet/onaylıyorum" der, SAPMALAR 22. satır "onay yine yalnız açık 'evet' ile" der. "Peki ne yapıyorsun?" ya da "tamam ama yanlış" evet olur. HAYIR kümesinde "değil", "olmaz", "yanlış", "yapmayın", "yapmasın" yok (bunlar anlaşılmaz sayılır, güvenli ama gereksiz soru). Orta/Yüksek risk eylemleri geldiğinde (KT-5) FG-72/73 ihlaline dönüşür. Düzeltme: evet = {evet, onaylıyorum, onayla} (+ öğrencinin onayladığı ek sözcükler SAPMALAR'a yazılsın); cümle içinde hem evet hem herhangi bir olumsuzlama ("ama", "değil", "yanlış") varsa anlaşılmaz say; HAYIR kümesine eksik sözcükleri ekle; birim test: "peki nasıl", "tamam ama hayır", "olmaz".

**Ö-3 | FG-07, FG-04, NFG-03 (UX) | AssistantSession.kt (ConfirmTapped hiç gönderilmiyor); strings.xml (evet/hayır düğmesi yok)**
Bilinen eksik: onay sorusunda ekranda Evet/Hayır düğmesi yok. Açık bir gereksinimi İHLAL ETMİYOR: onay sesle ya da "Yazarak söyle" ile verilebilir (FG-07, FG-04 karşılanıyor) ve soru yazıyla da görünür (NFG-04). Ancak yanlış tanımada tek çıkış, "Yazarak söyle"yi açıp "evet" yazmak (3 dokunuş, klavye); ses tanıması zayıf olan yaşlı için asıl akış budur. `DialogEvent.ConfirmTapped` çekirdekte hazır ve test edilmiş ama arayüzde kullanılmıyor (ölü yol). Düzeltme: onay beklerken "Dur"un üstünde iki büyük metin düğmesi ("Evet", "Hayır"); tıklama `ConfirmTapped`. Orta/Yüksek risk (KT-5) için neredeyse zorunlu.

**Ö-4 | FG-07, NFG-07 | AssistantSession.kt:63-67; DialogMachine.kt:212-215 (doğrulanmadı)**
"Yazarak söyle"ye basınca mikrofon kapanır ama onay zamanlayıcısı (10 sn) durmaz. Yaşlı kullanıcı onay yazarken 10 sn dolarsa oturum "Tamam, yapmıyorum." diye biter, yazma ekranı açık kalır; sonra gönderilen metin yeni komut sayılır (DialogMachine.kt:65-70). Yazan kullanıcı için "sessizlik = hayır" adil değil. Düzeltme: yazma ekranı açıkken zamanlayıcıyı duraklat (oturuma `TypingStarted/TypingEnded` olayı), ya da yazma süresince bekleme 60 sn. Cihazda denenmeli.

**Ö-5 | NFG-20, V-01, NFG-16 ruhu (metin cihazdan çıkmaz) | AndroidTtsBackend.kt:84-90,180; VoiceSelector.kt:58-71 (doğrulanmadı)**
(a) Yüklü HERHANGİ bir TTS motoru (üçüncü taraf dahil) denenir ve seçilebilir; konuşulan cümleler (şimdi uygulama adı, ileride kişi adı) o motora gider. (b) Ses listesi vermeyen eski motorlarda yalnız `isLanguageAvailable` ile `Ready(engine, null)` döner ve `tts.language = TURKISH` ayarlanır (180. satır); motor ağ gerektiren bir ses seçebilir, çevrimdışılık kanıtlanmaz. Düzeltme: motor listesini Google + telefonun varsayılanı + imzalı sistem motorlarıyla sınırla; `voice == null` ise seslendirme yapma (yalnız yazı); mümkünse `Engine.KEY_FEATURE_NETWORK_SYNTHESIS=false` ve `KEY_FEATURE_NETWORK_RETRIES_COUNT=0` konuşma paketine eklensin.

**Ö-6 | DOĞ-02, NFG-50, NFG-79 | core/masking/DefaultMaskingLexicon.kt:3-10; MaskingLexicon.kt:66-85**
Maskeleme sözlüğü test korpusundan üretildi ve korpus bağlayıcı sayıldı; kod korpusu geçiyor diye başarı kanıtı olamaz (bkz. Bölüm 5). Ayrıca sözlük önek kuralı (>=4 harf, MaskingLexicon.kt:68,84) "bilinen sözcük"ü genişletiyor; bu, sözlük kökü ile başlayan adları kaçırma riski (AR-5; SAPMALAR 32. satırdaki Kerem örneği yalnız "hanım/bey" öncesi için düzeltildi). Doğrulanmadı ama sınanmalı: "Kerem'i ara", "Yenice", "Saatçi", "Ayarcı" gibi sözlük köküyle başlayan ad/soyadlar. Maskeleme henüz bağlı olmadığı için bugün sızıntı yok; KT-6'dan önce bağımsız sınama şart.

### Küçük

**K-1 | NFG-67/68 | ManifestPermissionTest.kt:29-41**
`android.permission.INTERNET` yasaklılar listesinde yok; bu aşamada "buluta hiçbir şey gitmez" ilkesi yalnız izin listesinin darlığına dayanıyor. Düzeltme: INTERNET ve ACCESS_NETWORK_STATE'i KT-6 (G-34) kadar yasaklıya ekle; G-34 bilerek kaldırır.

**K-2 | FG-11 (Zorunlu) | OverlayState.kt:17; DialogState.kt:30-32; SAPMALAR 36. satır**
"Dur" her aşamada görünmeli. (a) Yazma ekranı açıkken gizli: SAPMALAR 36'da gerekçeli, yerine "Vazgeç" (hocayla çelişmez). (b) `Done` durumunda (kapanış cümlesi okunurken) gizli; belgelenmemiş. Konuşmayı kesmek için baloncuğa dokunmak gerekir (yeni dinleme başlatır). Düzeltme: (b) için ya kısa kapanış cümlesinde sorun olmadığını SAPMALAR'a yaz ya da `Done` sırasında Dur'u göster.

**K-3 | NFG-01/03 | overlay/BubbleView.kt:35**
Baloncuk yazısı 14-24 sp otomatik küçülür; "Dinliyorum" 96 dp dairede ~15-16 sp'ye inebilir (diğer yazılar 22-30 sp). Düzeltme: `bubble_size` 112 dp ya da "Dinliyorum" yerine kısa "Dinle"/"Açık"; en küçük boyutu 18 sp yap. Cihazda bakılmalı.

**K-4 | NFG-01/03 | GEREKSINIMLER.md**
Gereksinimlerde sayısal yazı/kontrast/hedef eşiği yok, "büyük", "yüksek kontrast" diyor. Koddaki değerler makul: gövde 22-24 sp, başlık 28-36 sp, düğme >= 72 dp (LargeButtons.kt:16), baloncuk 96 dp, Dur 88 dp, Tür düğmesi 72 dp; kontrast hesabı: beyaz/#0B3D91 ≈ 10:1, #111111/#FFD400 ≈ 13:1, beyaz/#111111 ≈ 18:1, #B00020/beyaz ≈ 7,7:1 (hepsi WCAG AAA 7:1 üstü). Analist önerisi: NFG-01/03'e "gövde >= 20 sp, hedef >= 64 dp, kontrast >= 7:1" yaz (kaynak: [ÇIKARIM]).

**K-5 | NFG-10, NFG-14 | strings.xml:45-47,93,103**
Aynı kavram için "internetsiz" ve "çevrimdışı" karışık ("internetsiz Türkçe", "çevrimdışı yazın"). Tek terim seçin ("internetsiz"). "Dil paketi" ve "paket" tutarlı. İngilizce sızıntı ve suçlayıcı/çocuksu dil bulunmadı ("Wi-Fi", "Google" marka adı). "Sizi duyamadım" ve "Bunu yapamadım" yargısız.

**K-6 | NFG-65, SAPMALAR 26 | strings.xml:19**
Bilgilendirme "Banka uygulamalarına dokunmam" diyor; bugün dokunma yok, doğru. Ancak `DefaultRiskAssessor` (DecisionPhrases.kt:35-37) her başlatıcı uygulamayı, banka dahil, Düşük risk sayıp onaysız açar. Açmak dokunmak değil, ama KT-4 (G-24 banka engeli) gelince söz tutulmalı. Düzeltme: G-24'te banka listesi hem açmayı onaya bağlasın hem dokunmayı engellesin.

**K-7 | NFG-53/KVKK | TypeCommandActivity.kt:77-90 (doğrulanmadı)**
Yazma kutusu klavyenin kişiselleştirilmiş öğrenmesine açık (örn. Gboard kişisel sözlük); yazılan komut ve ad klavyede kalabilir. Düzeltme: `KeyboardOptions(autoCorrectEnabled=false)` ve (Compose'da yoksa) `privateImeOptions="flagNoPersonalizedLearning"` ya da `inputType=textNoSuggestions`; aydınlatma metninde klavyenin telefona ait olduğu belirtilsin.

**K-8 | NFG-04, NFG-13 | CaptionView.kt:33 (doğrulanmadı)**
Altyazı TalkBack'ten gizli (`IMPORTANT_FOR_ACCESSIBILITY_NO`). Türkçe ses yoksa uygulama sesi susar, TalkBack'li kullanıcı yazıyı da duymaz. Düzeltme: TTS `Ready` değilse altyazıyı `liveRegion=polite` ile duyur.

**K-9 | NFG-29/NFG-22 (gecikme izleme) | AssistantSession.kt:200-241**
Gecikme günlüğü içeriksiz ve doğru. Ancak `SafeLog.event` release'te de logcat'e yazar (AssistantApp.kt:32-41; proguard yalnız `content` ve v/d'yi siler). Mevcut `event` çağrıları içeriksiz (tarandı: SafeLog.event kullanımlarının hiçbirinde transkript, ad ya da uygulama adı yok). Risk geleceğe dönük: bir geliştirici `event`'e içerik koyarsa release'te sızar. Düzeltme: `event` mesajlarını yalnız sabit/enum kabul eden bir birim testle koru (örn. `SafeLog.event` çağrılarında `text`, `transcript` değişkeni geçmesin diye basit bir kaynak taraması testi).

## 3. Gizlilik/KVKK özeti (görev 2)

- İzinler: yalnız RECORD_AUDIO (manifest:15), gerekçesi ListenSetup ekranında sade Türkçe ve izinden ÖNCE (strings.xml:82-84). INTERNET yok; `usesCleartextTraffic=false`; `networkSecurityConfig` yalnız sistem sertifikaları.
- Ses ve metin: diske yazılmıyor (dosya/AudioRecord kullanımı yalnız Whisper dizininde, denetim dışı; TTS ısınma dosyası sabit "Merhaba" kelimesi, cacheDir, bitince siliniyor, kişisel veri yok). Transkript yalnız `SafeLog.content` (debug) içinde; release'te R8 siliyor (proguard-rules.pro:5-7). `Log.i/w/e` yalnız `AssistantApp` içinde SafeLog arkasında; başka Log/println/printStackTrace yok.
- Çevrimiçi tanıma yolu yok: yalnız `createOnDeviceSpeechRecognizer`; API 29-30'da "kullanılamaz" döner. Model indirme (`triggerModelDownload`) yalnız dil paketini indirir; ekranda söyleniyor (strings.xml:93).
- Çevrimiçi TTS yolu: Ö-5.
- Erişilebilirlik hizmeti: ekran içeriği okumuyor (yapılandırma + boş `onAccessibilityEvent`).
- Mikrofon: yalnız baloncuk dokunuşuyla başlayan oturumda; açıkken baloncuk sarıya döner ve "Dinliyorum" yazar (BubbleView.kt:49-50,55-57); "Dur" mikrofonu hemen kapatır (VoiceInputCoordinator.kt:57-60). Kurulum ekranındaki deneme düğmesi SAPMALAR 30'da belgeli; `onStop`'ta mikrofon kapanıyor (ListenSetupActivity.kt:74-77).
- Yedekleme kapalı: `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules` bulut ve cihaz aktarımını dışlıyor; ManifestPermissionTest bunu doğruluyor.
- Saklanan veri: yalnız balon konumu ve konuşma hızı (kişisel değil).

## 4. SAPMALAR.md ve hoca talebi (görev 4)

Hoca geri bildirimi (hafta-2) uygulama davranışı için üç açık talep içerir: SMS/doğrulama kodu (kod görülünce işlem yok, rakam maskeleme), Play politikası notu, ve ek talepler (etik karar, Tablo 4, ekran görüntüsü/video). SAPMALAR'daki 30 satırın HİÇBİRİ hocanın açık bir talebiyle çelişmiyor. Satır 11 (Play uyumlu yürütücü yok) hocanın "zorunlu değil" dediği öneriye karşılık gelir; çelişki değil. Satır 18 (kod/şifre varsa bulut isteği hiç oluşturulmaz) hocanın ✅ verdiği §5.4 ile aynı yönde, daha sıkı.

Hocayla değil, ÖĞRENCİ belgeleriyle gerilimi olan satırlar (işaretli):
- Satır 23 (kesin Düşük riskte onaysız): FG-07 (Şekil 1 adım 2, §4.1) ile Tablo 3 çatışması; Tablo 3 seçildi, hoca "risk dereceleri yerinde" demiş. ÇEL-17 olarak GEREKSINIMLER'e işlendi. NFG-18 sınırı için Ö-1.
- Satır 36 (Dur yazma ekranında gizli): FG-11 "her aşamada"; Vazgeç eşdeğer. K-2.
- Satır 30 (deneme düğmesiyle baloncuk dışında mikrofon): FG-02'yi dar yorumun dışına çıkarır, mikrofon yine dokunuşla ve göstergeyle açılıyor; kabul edilebilir.
- Satır 15 (araya girme için oturum boyu mikrofon): kod daha dar (tur başına açılır); FG-14 KT-3'te.
- Satır 26 (bilgilendirme yalnız TalkBack ile okunur): NFG-65'i karşılıyor; NFG-57 sesle okunabilirlik G-30'da.
- SAPMALAR'da OLMAYAN ama kodda olan karar: onay sözcük kümesi V-12'den geniş (Ö-2). SAPMALAR'a satır eklenmeli ya da kod daraltılmalı.

## 5. Maskeleme sözlüğü: korpus bağımsız kanıt değil (görev 5)

Değerlendirme: DefaultMaskingLexicon.kt, korpusun `sozlukler` ve `ayarlar` bölümlerinden üretildi (dosya başı notu) ve korpus aynı kişi/araç (tools/mask_corpus_gen.py) tarafından yazıldı. Korpusu geçmek yalnız "tutarlılık" gösterir (regresyon güvencesi); sözlük kapsamı, ad dağılımı ve Türkçe ek çeşitliliği hakkında hiçbir şey söylemez. SAPMALAR 31-34 satırları da düzeltmelerin korpus beklentisine göre yapıldığını gösteriyor (N-001, K-019, O-012, R-006). Bu geliştirme kümesine uydurma (overfitting) riskidir. Sızıntı oranı bilinmiyor. Hocaya "maskeleme %100" denmemeli; "kendi korpusunda %100, bağımsız kümede şu kadar" denmeli.

Somut bağımsız sınama önerisi (hepsi ücretsiz):
1. Dondur: sözlük ve kod sürümünü etiketle (git yok, klasörü kopyala). Bundan sonra korpus açılmadan değişiklik yapma.
2. Kör küme: 3-5 yakın kişiden (sözlüğü görmeden) her biri "telefona ne söylerdim" diye 60-100 komut yazsın (ad, numara, adres içerenler dahil, günlük ağızla, büyük harfsiz). Etiketlemeyi başka biri yapsın (neyin kişisel veri olduğu). Hedef en az 300 komut; 300 örnekte 0 sızıntı bile %95 güvenle üst sınırı ~%1 verir (3/n kuralı).
3. Ad sızıntı testi (AR-1): açık TÜİK ya da benzeri en yaygın 500 ad ve 200 soyadı, 10 komut kalıbıyla çarpılsın ("X'i ara", "X'e mesaj yaz", "dün X geldi sesi aç"), rehberde YOK, küçük harfle, ek alarak ("Kerem'i", "Saatçi'ye"). Sızan ad oranını raporla; özellikle sözlük köküyle başlayan adlar (Ö-6).
4. Gerçek ses hattı: öğrenci 100 test cümlesini A15'te yerleşik tanıyıcıya okusun; çıkan GERÇEK transkriptleri maskelemeden geçirsin (tanıma hataları AR-2, büyük harf yok, sayı yazımı). Korpus yazıyla üretilmiş "temiz" metin.
5. Sayısal desen testi (özellik tabanlı): rastgele geçerli TCKN, IBAN (mod-97), Luhn kartı, 8 farklı telefon yazımı, aralıklı/yazıyla rakamlar üret; çıktıda 4+ haneli rakam dizisi KALMAMALI. Bu otomatik ve ucuz.
6. Aşırı maskeleme oranı: yaygın günlük komutlarda (ör. açık Türkçe soru/komut metinleri) gereksiz yer tutucu oranı ölçülsün (AR-9; kullanılabilirlik).
7. Raporlama: geliştirme (201 örnek) ve bağımsız küme ayrı tabloda, kategori bazında kaçırma (recall) ve %95 güven aralığı ile. Bağımsız kümede kaçırma varsa kodu düzelt, düzeltmeleri ancak YENİ bir kör kümeyle yeniden sına.
Not: Gemini ücretsiz koşulları "kişisel bilgi göndermeyin" diyor (DOGRULAMALAR §4) ve maskeli metin yine kişisel veri sayılabilir; bu yüzden bağımsız sınama bulut hattını açma ön koşulu olmalı.

## 6. Cihazda özellikle bakılacak riskler

1. Tanıma Android 16'da gerçekten cihaz içi mi (`isOnDeviceRecognitionAvailable`) ve uçak modunda çalışıyor mu; Türkçe paket yoksa `UNKNOWN` ile mikrofon açılıp hata veriyor mu.
2. Arka plandaki uygulama öndeyken (Ayarlar, WhatsApp) hizmetten mikrofon açılıyor mu (Android 11+ arka plan mikrofon kısıtı, ERROR_AUDIO); engellenirse planın yedeği (saydam etkinlik).
3. Mikrofon göstergesi: sarı baloncuk + "Dinliyorum" okunaklı mı; her sorudan sonra otomatik yeni dinleme turunda gösterge yanıp sönmeden görünüyor mu; Dur mikrofonu anında kapatıyor mu (sistem mikrofon noktasından doğrula).
4. TYPE_ACCESSIBILITY_OVERLAY penceresi kenar, çentik, navigasyon çubuğu ve klavye açıkken Dur/Yazarak söyle düğmesini kapatıyor mu; yazma ekranında klavye "Gönder"i örtüyor mu.
5. Yazma ekranı açıkken 10 sn dolunca ne oluyor (Ö-4).
6. TTS: A15'te Samsung motoru bağlanamayınca Google motoruna düşüş, Türkçe ses yoksa sessiz mod ve altyazı; ağ kapalıyken (uçak modu) Türkçe okuma gerçekten çalışıyor mu (Ö-5).
7. Hizmet bir süre boşta kaldıktan sonra (One UI pil kısıtı) baloncuk kalıyor mu (NFG-73, KT-11 ama erken işaret).
8. Hizmet, ayar sayfasından kapatıp açıldığında oturum durumu, mikrofon ve altyazı pencereleri temizleniyor mu (tearDown, AssistantAccessibilityService.kt:160-173).
9. Düşük riskli onaysız açma: gürültülü ortamda ya da televizyon açıkken yanlış uygulama açılıyor mu (Ö-1).
10. Bildirim: TalkBack açıkken baloncuk ve düğmeler Türkçe doğru okunuyor mu (K-8).

## 7. GEREKSINIMLER.md'de yapılan değişiklikler
- SS-13 kapatıldı (veri sorumlusu öğrenci; ad/iletişim G-30'da istenecek, yer tutucu işaretli).
- SS-11 kısmen kapatıldı (V-12 değerleri), sözcük kümesi uyuşmazlığı not edildi.
- DOĞ-01: DOGRULAMALAR.md ile KISMEN KAPANDI olarak işaretlendi, açık kalan maddeler listelendi.
- DOĞ-02: kapsam yazıldı, bağımsız kanıt yok notu eklendi.
- ÇEL-17 eklendi (FG-07 / FG-71, NFG-18 sınırı).

## 8. Düzeltmeler (17.11.2026, gelistirici)

Her düzeltme önce başarısız birim testle, sonra kodla yapıldı. Komut: `gradlew :core:test :speech:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL; core 423, speech 87, app 99 test, 0 hata. Cihazda denenmedi.

| Bulgu | Yapılan | Test |
|---|---|---|
| Ö-1 | Son sonuç gelmeyip ara metin kullanılırsa `SpeechOutcome.Heard.partialOnly=true` (RecognitionTurn); işaret `VoiceInputUi.onHeard` → `DialogEvent.Heard` → `DialogEffect.Decide` → `DecisionCoordinator.decideTraced(partialOnly)` ile taşınır. Ara metin Düşük riskte de eylem yapmaz: duyulan okunur ("X uygulamasını açayım mı?"). Seçenek yanıtında da aynı. Ara metindeki "evet" onay sayılmaz (bir kez yeniden sorulur), "hayır" geçerli. SAPMALAR 23. satıra NFG-18 sınırı yazıldı. Güven skoru (`CONFIDENCE_SCORES`) eklenmedi | RecognitionTurnTest (3 yeni + 1 güncel), DecisionCoordinatorTest `partialOnly*`, DialogConfirmSafetyTest `partialOnly*`, AssistantSessionTest `partialOnlyCommandIsReadBackInsteadOfRun`, VoiceInputCoordinatorTest `partialOnlyMarkIsHandedOn` |
| Ö-2 | ConfirmationParser: EVET = {evet, onaylıyorum, onayla}; YUMUŞAK EVET = {tamam, olur, peki, tabii} yalnız tek başına; olumsuzlar (değil, olmaz, yanlış, yapmayın, yapmasın, açma…) HAYIR; "ama/fakat" + evet ya da fazladan sözcük = anlaşılmaz. Yumuşak evet yalnız Düşük riskte kabul (`Understood.lowRisk`, durumlarda taşınır), Orta/Yüksekte bir kez yeniden sorulur. "yap", "onay" çıkarıldı. SAPMALAR'a gerekçeli satır eklendi; 22. satır notu güncellendi | ConfirmationParserTest ("peki nasıl", "tamam ama hayır", "olmaz" dahil), DialogConfirmSafetyTest `softYes*`, `lowRiskSurvivesTheSecondQuestion` |
| Ö-3 | Onay sorusu okunurken ve beklenirken "Yazarak söyle"nin üstünde iki büyük düğme (`ConfirmButtonsView`): "Evet" koyu mavi/beyaz (~10:1), "Hayır" beyaz/siyah (~18:1), 30 sp, 88 dp yükseklik, en az 150 dp genişlik; metinler `strings.xml` (`confirm_*`). Dokunuş `DialogEvent.ConfirmTapped` gönderir. Yazma ekranı açıkken gizli | AssistantSessionTest `yesNoButtonsShowOnlyDuringTheQuestionAndConfirm`, `noButtonCancels` (görünüm cihazda bakılmalı) |
| Ö-4 | "Yazarak söyle" `DialogEvent.TypingStarted` gönderir; makine onay süresini iptal eder ve yazarken yeni süre/mikrofon başlatmaz; yazılan yanıtla, "Dur"/"Vazgeç" ile ya da oturum bitince yazma durumu kalkar | DialogConfirmSafetyTest `typing*`, AssistantSessionTest `typingAnAnswerIsNotSilence` |
| Ö-5 | Ses listesi vermeyen motor artık hazır sayılmaz (`VoiceStatus.Ready.voiceName` boş olamaz); seçilen ses `setVoice` ile tam ayarlanamazsa motor kullanılmaz, yalnız yazı. Motorlar Google + telefon varsayılanı + telefonla gelen (sistem) motorlarla sınırlı (`VoiceSelector.isTrustedEngine`). Her okuma ve ısınma isteğine `KEY_FEATURE_NETWORK_SYNTHESIS=false`, `KEY_FEATURE_NETWORK_RETRIES_COUNT=0` eklendi. SAPMALAR'a satır eklendi | VoiceSelectorTest `engineWithoutAVoiceListIsNeverReady`, `onlyGoogleTheDefaultOrSystemEnginesAreTrusted` (Android tarafı cihazda, uçak modunda bakılmalı) |
| Ö-6 | Bu görevin dışında (G-56) | - |
| K-1 | INTERNET ve ACCESS_NETWORK_STATE için açık test (`noNetworkPermissionBeforeTheCloudStep`); izin listesi zaten engelliyordu. G-34 bu testi bilerek kaldırır | ManifestPermissionTest |
| K-9 | `SafeLog.event` çağrılarında metin/transkript/ad değişkeni (yalnız `.length` serbest) olmadığını kaynak taramasıyla denetleyen test (app, core, speech) | SafeLogUsageTest |
| K-5 | Değişiklik yok: kalan tek "çevrimdışı", telefonun Ayarlar araması için yazılacak sözcük (voice_manual_steps); değiştirilirse kullanıcı bulamaz | - |
| K-2, K-3, K-6, K-7, K-8 | Açık. K-7: Compose `KeyboardOptions` IME_FLAG_NO_PERSONALIZED_LEARNING bayrağını vermiyor, küçük ve güvenli değil; K-3/K-8 cihazda bakılmalı; K-6 G-24'te; K-2(b) belge kararı | - |
