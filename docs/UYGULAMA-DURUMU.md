# Yaşlı Telefon Asistanı: Uygulamanın Durumu, Gizlilik ve Veri Güvenliği

Durum tarihi: 18.11.2026. Test cihazı: Samsung Galaxy A15 (Android 16). Bu belge yalnızca **kodda yapılmış ve doğrulanmış** şeyleri anlatır; yapılmayanlar ve doğrulanmayanlar ayrıca, açıkça yazılmıştır.

## 1. Uygulama nedir?

Yaşlı kullanıcıların telefonlarını kullanmasına yardım eden, yalnızca Android'de çalışan bir asistandır. Ekranın kenarında büyük bir **"Konuş" baloncuğu** durur. Kullanıcı baloncuğa dokunur, ne istediğini söyler (ya da yazar) ve asistan iki biçimde yardım eder:

- **"Sen yap" modu:** Asistan işi kullanıcının yerine yapar.
- **"Ben yapayım" modu (öğretmen modu):** Asistan hiçbir şeye basmaz; ekranı izler, sıradaki düğmeyi **sarı halkayla** gösterir ve ne yapılacağını sesle söyler. Kullanıcı kendisi basar.

## 2. Bugün çalışan özellikler

Aşağıdaki tabloda "Telefonda denendi" sütunu, öğrencinin kendi telefonunda gerçekten denediği anlamına gelir. Diğerleri yazılmış ve otomatik testlerden geçmiştir, ama telefonda gözle doğrulanmamıştır.

| Özellik | Ne yapıyor | Telefonda denendi |
|---|---|---|
| Yüzen baloncuk ve **Dur** düğmesi | Sürüklenir, kenara yapışır; Dur her an işi keser | Evet |
| Sesle komut | Türkçe, telefonun içinde çevrimdışı tanıma (Android 12+) | Evet |
| Yazarak komut | "Yazarak söyle" düğmesi | Hayır |
| Sesli yanıt | Telefonun çevrimdışı Türkçe sesi; her cümle ekranda büyük yazıyla da görünür | Evet |
| Sesli ve düğmeli **Evet / Hayır** onayı | Riskli işlerden önce sorulur; sessizlik "hayır" sayılır | Evet |
| **"Sen yap": WhatsApp'tan fotoğraf gönder** | Kullanıcı fotoğrafları seçer, asistan WhatsApp'ta kişinin sohbetini açar ve Gönder'e basar | Evet |
| **"Ben yapayım": WhatsApp'tan fotoğraf göndermeyi öğret** | Ana ekrandan başlayıp klasör/uygulama listesi, WhatsApp, kişi, ataş, galeri, fotoğraf seçimi ve Gönder adımlarını halka ve sesle gösterir | Evet (üç tur deneme ve düzeltme) |
| Rehberden kişi bulma | "Şafak'a", "Ayşe'yi" gibi ek almış adlar, akrabalık sözcükleri ("kızıma"); rehberde tek eşleşme varsa **hiç soru sormaz** | Kısmen |
| Arama ve mesaj | Yalnızca standart arama / mesaj ekranını açar; aramayı ya da mesajı kullanıcı başlatır | Hayır |
| Ayarlar, ses, parlaklık, yazı boyutu, uygulama açma | Telefon içi kurallarla, soru sormadan | Hayır |
| "Neden?" açıklaması, "tekrar söyle", "yavaş konuş", bağlam ("onu da ara") | Katmanlı sade açıklama ve kısa süreli bağlam | Hayır |

Her şeyin arkasında **otomatik test** vardır (bilgisayarda yüzlerce birim testi; ekran maskeleme, kişi eşleştirme, eylem yürütücü, diyalog akışı vb.).

## 3. Henüz çalışmayanlar ve sınırlar

- **Yapay zekâ ile serbest komut (bulut) bağlı değil.** Vekil sunucu ve istemci yazıldı, 47 sunucu testi geçti; ancak Gemini/Groq anahtarları ve Cloudflare hesabı alınmadığı için bulut kapalıdır. Yapay zekânın planını telefonda adım adım uygulayan parça da henüz yazılmadı. Şu an her komut telefonun içindeki kurallarla çözülür; kuralların bilmediği komutta asistan "Bunu henüz yapamıyorum" der.
- **Asistan konuşurken sözünü sesle kesme** (araya girme) Galaxy A15'te asistanın sesini kestiği için kapatıldı. Kod ve testler durur. Dur düğmesi her zaman çalışır. (Gerekçe: `docs/SAPMALAR.md`.)
- **Yapılmamış plan görevleri:** görev belleği (kayıtlı görevleri tekrar oynatma), aile ayarları, araştırma ses kaydı kipi, ilk kurulum sihirbazı, hız ve bellek ölçümleri.
- **Whisper karşılaştırması** (hocanın istediği tiny/base/small tablosu): ölçüm aracı hazır, telefonda koşulmadı. Telefonun kendi tanıyıcısı varsayılan; Whisper yalnızca Android 10-11 için yedektir.
- **Telefonda kullanıcı testi yapılmadı.** Özellikle yaşlı katılımcılarla hiçbir test yapılmamıştır; testler proje sonunda öğrencinin kendisi tarafından yapılacaktır.
- **Uygulama şu an bir geliştirme (debug) sürümüdür:** yalnızca APK olarak dağıtılır; Play Store yayını hedeflenmemiştir.

## 4. Gizlilik ve veri güvenliği

### 4.1 Temel ilke

Uygulama, Play Store'da yayımlanacakmış gibi KVKK'ya uygun tasarlanmıştır. İki ilke vardır: **(1) ham ses ve ekran görüntüsü telefondan asla çıkmaz; (2) buluta gidecek metinde kişisel veri kalmaz** (cihaz içi maskeleme). Rıza ve aydınlatma bunun üstüne ek katmandır, tek dayanak değildir. Gerekçe: KVKK m.9, 1.9.2024'ten beri açık rızayı yalnızca arızi (tek seferlik) yurt dışı aktarımlarda kabul eder; sürekli bulut çağrısı bu kapsama girmez.

### 4.2 Şu an buluta ne gidiyor?

**Hiçbir şey.** Bulut kapalıdır. Uygulamanın internet izni yalnızca bir amaç içindir: gerekirse Whisper modelini resmî model deposundan (huggingface.co) bir kez indirmek. Tüm ağ bağlantıları tek bir sınıftan (`HttpGateway`) geçer ve yalnızca izin verilen adreslere, yalnızca HTTPS ile gider; bunu bir test denetler. Ses ve söylenenler ağa gitmez.

### 4.3 Bulut açıldığında (kod hazır) güvenceler

1. **Rıza kapısı:** Kullanıcının ayrı ve açık rızası olmadan hiçbir istek oluşmaz. Varsayılan "izin yok"tur; rıza tek dokunuşla geri alınır; metin sürümü değişirse yeniden sorulur.
2. **Cihaz içi maskeleme:** Ad, telefon, e-posta, T.C. kimlik no, IBAN, kart, adres, uzun sayılar, mesaj içerikleri ve sağlık gibi özel nitelikli veriler "KİŞİ_1" gibi yer tutuculara çevrilir. Yer tutucu–gerçek değer eşlemesi yalnızca telefonda, bellekte kalır ve yanıt geldiğinde geri çözülür. Şifre alanlarının içeriği hiç gönderilmez.
3. **Son denetim:** Gönderimden hemen önce komut ve ekran özeti yeniden taranır; bir şey yakalanırsa **istek hiç oluşturulmaz**. Ekranda doğrulama kodu ya da şifre görülürse istek yine oluşturulmaz.
4. **Tür düzeyinde koruma:** İstemci yalnızca denetimden geçmiş (`AuditedRequest`) veri kabul eder; maskelenmemiş metin istemciye verilemez.
5. **Vekil sunucu:** API anahtarları uygulamada yok, yalnızca sunucudadır. Sunucu istek ve yanıt gövdesini hiçbir yere kaydetmez (günlük kapalı, bunu bir test denetler); IP ve başlıkları sağlayıcıya iletmez.
6. **Sağlayıcılar:** Önce Gemini (ücretsiz katmanda gönderilenler ürün geliştirmede kullanılabilir, insan inceleyebilir, AEA dışında; Google'ın kendi şartı da kişisel veri göndermemeyi söyler: bu yüzden maskeleme zorunludur), yedek olarak Groq (varsayılan saklamaz; Zero Data Retention açılacak).
7. **Kullanım sınırı yoktur:** Yaşlı kullanıcıya görünen hiçbir limit konmamıştır; kota biterse yedek sağlayıcıya, o da biterse telefon içi özelliklere geçilir ve durum sade bir cümleyle söylenir.

### 4.4 Telefonda tutulan veriler

| Veri | Nerede / ne kadar | Not |
|---|---|---|
| Rehber | Yalnızca kişi kimliği ve ad, **yalnızca bellekte** | Numara okunmaz; diske yazılmaz; izin geri alınırsa silinir |
| Ekran içeriği | Yalnızca bellekte, görev açıkken | Ekran görüntüsü alınmaz; şifre ve hassas alanların metni hiç okunmaz |
| Ses | Tanıma telefonda yapılır; kalıcı kayıt yoktur | Mikrofon yalnızca baloncuğa dokununca açılır, açıkken görünür gösterge vardır |
| Ölçüm kayıtları | Telefonun özel alanında, 30 gün sonra silinir | Yalnızca süre/sayı/kod; **metin alanı yoktur** (tür düzeyinde garanti, testli) |
| Fotoğraflar | Uygulama galeriye erişmez | Sistem fotoğraf seçicisi kullanılır; yalnızca kullanıcının seçtikleri o gönderim için paylaşılır |
| Hata ayıklama günlükleri | Yayın (release) sürümünde koddan silinir | Kişisel veri içerebilecek hiçbir günlük yazılmaz |
| Yedekleme | Kapalı | Veri buluta ya da başka telefona yedeklenmez |

### 4.5 İzinler (az ve gerekçeli)

Uygulama yalnızca şu izinleri ister: **mikrofon** (sesle komut), **rehber** (isteğe bağlı; yalnızca kişi gerektiren komutta, gerekçe önce söylenerek), **internet** ve **ağ durumu** (yalnızca model indirme / ileride vekil sunucu), ve kullanıcının Ayarlar'da kendisinin açtığı **erişilebilirlik hizmeti**. Her izin bir testle denetlenir; listedeki dışında izin eklenirse derleme testi başarısız olur. Kesinlikle istenmeyenler: SMS, bildirim dinleme, "üstte göster", tüm uygulamaları listeleme, geniş depolama erişimi.

**Erişilebilirlik hizmeti** (en hassas izin): Önce uygulamanın içinde ne yapıp ne yapmadığı sade dille anlatılır; kullanıcı onaylamadan Ayarlar açılmaz. Hizmet yalnızca bir görev ya da rehber çalışırken ekranı okur, aksi halde hiçbir şey okumaz. Asistanın kendi pencereleri, şifre alanları ve klavye okunmaz.

### 4.6 Güvenlik önlemleri

- **Risk dereceli onay:** Uygulama açma, ses gibi geri alınabilir işler soru sormadan yapılır; arama ve mesaj orta, başkasına bir şey **gönderme** yüksek risktir ve önce onay ister. Anlaşılmayan ya da sessiz yanıt "hayır" sayılır; "olmaz, vazgeç" her zaman "hayır"dır.
- **İzinli uygulama listesi:** "Sen yap" modunda asistan yalnızca Ayarlar, WhatsApp, Galeri/Fotoğraflar, Kamera, Telefon ve Mesajlar'a dokunabilir. **Banka dahil diğer her uygulama varsayılan olarak reddedilir.**
- **Her adımdan önce doğrulama:** Asistan hedef düğmenin hâlâ aynı yerde ve aynı olduğunu denetler; emin değilse hiçbir şeye basmaz. Beklenmeyen bir sayfaya düşerse "geri" ile döner ve sorar.
- **Şifre ve hassas alanlara hiçbir eylem uygulanmaz.**
- **Dur** her zaman, her adımı anında durdurur.
- **İstem enjeksiyonuna karşı:** Ekrandaki yazı "veri"dir, talimat değildir; model yanıtı katı bir şemadan geçer, şemaya uymayan ya da ekranda olmayan bir öğeye işaret eden plan tümüyle reddedilir.

### 4.7 Açık gizlilik riskleri ve eksikler (dürüst liste)

1. **Maskeleme sözlüğü test setinden üretildi.** Test setinde %100 başarı, maskelemenin hiç görmediği cümlelerde de aynı başarıyı kanıtlamaz. Planlanan bağımsız ("kör küme") sınama henüz yapılmadı. Bilinen en önemli açık: rehberde olmayan bir adın kısa bir cümlede geçmesi ("dün Zeynep geldi"); buna karşı emin olunamayan içeriği gizleme ve gönderimden önce son denetim önlemleri vardır. 11 artık risk `docs/MASKELEME.md`'de listelidir.
2. **Aydınlatma metni henüz onaylanmadı.** Veri sorumlusu öğrencinin kendisidir; ad ve iletişim adresi yer tutucudadır ve yayından önce doldurulmalıdır. Metnin öğrenci tarafından okunup onaylanması gerekir.
3. **"Tüm verilerimi sil"** düğmesi "Gizlilik ve izinler" ekranındadır (iki adımlı: önce "Emin misiniz?" sorulur). Ölçüm kayıtlarını, bulut isteklerinin maskeli kopyalarını, tüm rıza kayıtlarını ve kayıtlı sunucu bilgilerini siler. Telefonda henüz denenmemiştir.
4. **Gemini ücretsiz katmanının kotası ve Groq'un güncel koşulları** resmî sayfalardan tam doğrulanamadı; Cloudflare ücretsiz planında günlük içeriğinin kaydedilip kaydedilmediği kurulumda panelden kapatılacak ve kontrol edilecektir.
5. **KVKK m.9** değerlendirmesi hukuk bürosu yayınları ve resmî duyuruya dayanır; resmî bir hukuki görüş değildir. Vekil sunucuyu barındıran Cloudflare de yurt dışı bir alıcıdır ve aydınlatma metninde anılmalıdır.
6. **Vekil sunucu erişim kodu** uygulamada saklı paylaşılan bir gizlidir; kötüye kullanıma karşı yalnızca dakikada 20 istek sınırı vardır. Güçlü bir kimlik doğrulama ücretsiz planda mümkün değildir.
7. **Aile üyesi (PIN'i bilen) aynı telefonda** kayıtlı verileri görebilir; aile ayarları henüz yapılmadı.

## 5. Belgelerden bilinçli sapmalar

Belgelerdeki bir yöntemden ayrıldığımız her yer gerekçesiyle `docs/SAPMALAR.md` dosyasındadır. Hocayla çelişen bir sapma yoktur (uyum denetimi UD-1, `docs/denetim/UD-1.md`). En önemlileri: yurt dışı bulut dayanağının "açık rıza"dan "buluta kişisel veri gitmemesi"ne kaydırılması; dil modelinin eylem planına ek olarak tek bir netleştirme sorusu da üretebilmesi; Play'e uyumlu ayrı yürütücü yapılmaması (APK dağıtımı); düşük riskli işlerde onay sorulmaması (risk tablosuna uyarak); araya girmenin cihazda kapatılması.

## 6. Sonraki adımlar

1. Gemini/Groq anahtarlarını ve Cloudflare hesabını kurmak (`docs/KURULUM-SON.md`, yaklaşık 20-30 dakika), ardından yapay zekâ planlamasını bağlamak.
2. İlk kurulum sihirbazı, görev belleği, aile ayarları.
3. Maskeleme için bağımsız sınama, Whisper karşılaştırma ölçümü, performans ve Samsung pil kısıtlarına dayanıklılık.
4. Aydınlatma metninin onaylanması ve veri sorumlusu bilgilerinin doldurulması.
