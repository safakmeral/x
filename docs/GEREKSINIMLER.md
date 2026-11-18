# Yaşlı Telefon Asistanı: Gereksinimler (tek doğru kaynak)

Sürüm: 2026-11-17 (Aşama 1) + aynı gün öğrenci kararları işlendi (Rev.2). Hazırlayan: uyum-analisti.
Öncelik sırası: hoca > öğrenci > makaleler. Çelişkiler Bölüm 6'dadır, silinmemiştir.
Etiket: `[ÖĞRENCİ-KARAR 17.11.2026 (n)]` = öğrencinin 17.11.2026'da verdiği 6 karardan n. olanı. Karar günlüğü ve doğrulama maddeleri Ek D'dedir. Haftalık takvim sembolik, bağlayıcı değildir; kullanıcı testleri en sonda öğrenci tarafından uygun kişilerle yapılır (test katılımcı maddeleri DEĞİŞMEDİ). [ÖĞRENCİ-KARAR 17.11.2026 (6)]

## Etiketler ve öncelikler
- `[HOCA]` = `hoca-geribildirim-hafta2.txt`. `[ÖĞRENCİ: ...]` = `tam-degerlendirme.txt` (Şekil 1 = akış, Şekil 2 = mimari, Tablo 2-6, § numarası), `literatur-arsivi.txt`, `docs/PROJE-VIZYONU.md` ("vizyon"), diyagramlar. `[MAKALE-NN]` = `docs/arastirma/notlar/NN_*.md`. `[ÇIKARIM]` = kaynakta açıkça yok, dayanağı satırda yazılı; hepsi Ek B'de listelidir.
- Öncelik: **Zorunlu** (olmazsa proje hocaya/KVKK'ya aykırı ya da çekirdek akış çalışmaz), **Olmalı**, **Olabilir**.
- `[PARA?]` = ücret doğurabilecek madde (Ek C).
- Makale numaraları: 01 EasyAsk, 02 Yu-Nav Nudge, 03 AgeMate, 04 WePilot, 05 ExplorAR, 06 Sharifi, 07 Liu-derleme, 08 AppAgent, 09 AutoDroid, 10 Mobile-Agent-v2, 11 AndroidWorld, 12 ElderBench, 13 KG-RAG, 14 Xu-RAG-GUI, 15 AWM, 16 MobileSafetyBench, 17 Pop-up saldırıları, 18 Leaky Thoughts, 19 Kaliappan, 20 van Dijk-ASR, 21 Mahmood, 22 Irfan, 23 An, 24 Mathur-a, 25 Mathur-b.

---

## 1. Kapsam ve hedef kullanıcı
- Ürün: yalnızca Android'de çalışan, yaşlıların telefon kullanmasına yardım eden sesli asistan; her uygulamanın üstünde yüzen baloncuk + ses. [ÖĞRENCİ: vizyon]
- Hedef kullanıcı: 65 yaş ve üzeri, Türkçe konuşan, kendi akıllı telefonunu kullanan kişi; yetkilendirdiği bir aile üyesi yalnızca ayarları yönetir. [ÖĞRENCİ: Tablo 4, §4.6]
- İki mod: **"Sen yap"** (asistan dokunur, yaşlı izler; vizyondaki "Yap/Mod 1") ve **"Ben yapayım"** (asistan vurgular ve yönlendirir, dokunmayı yaşlı yapar; vizyondaki "Rehber/Mod 2"). [ÖĞRENCİ: §4.2, vizyon] [HOCA: "Sen yap" terimini kullanıyor] Arayüzdeki yaşlıya dönük adlar açık karar (TK-13).
- Dağıtım: yalnızca APK/demo yeterlidir; Play'e uyumlu ayrı yürütücü gerekmez. Buna rağmen KVKK ve gizlilik gereksinimleri aynen zorunludur (uygulama yayımlanacakmış gibi ele alınır). [ÖĞRENCİ: vizyon, §5.5] [ÖĞRENCİ-KARAR 17.11.2026 (1)]
- Bütçe sıfır: yalnızca ücretsiz katman, süreli deneme/kredi yok; Claude Pro aboneliği çalışma anında yapay zekâ olarak kullanılamaz. [ÖĞRENCİ: vizyon]
- Dil: Türkçe (arayüz, ses, aydınlatma). [ÖĞRENCİ: Tablo 4; Giriş] Türkçe yaşlı konuşmasını inceleyen kaynak yoktur. [ÖĞRENCİ: §1]

---

## 2. İşlevsel gereksinimler (FG)

### 2.A Çağırma, ses ve anlama (her iki mod)
- **FG-01** (Zorunlu) Yüzen baloncuk her uygulamanın üstünde görünür ve ekranda sürüklenebilir. [ÖĞRENCİ: vizyon] [MAKALE-01]
- **FG-02** (Zorunlu) Baloncuğa dokunulunca asistan dinlemeye başlar; mikrofon yalnızca bu andan sonra açıktır. [ÖĞRENCİ: vizyon] [ÇIKARIM: sürekli dinleme KVKK ölçülülük ilkesine aykırı olur]
- **FG-03** (Zorunlu) Türkçe sesli komut yazıya çevrilir. [ÖĞRENCİ: Şekil 1 adım 1]
- **FG-04** (Olmalı) Komut yazarak da girilebilir (tanıma hatasına karşı). [ÖĞRENCİ: §8] [MAKALE-02]
- **FG-05** (Olmalı) Dinleme penceresi kullanıcı konuşurken kapanmaz, susma süresi yaşlı konuşmasına göre ayarlıdır ve yeniden deneme tek dokunuştur. [MAKALE-02: 1,5 sn zaman aşımı hatalı çağrıya yol açtı] [MAKALE-04: 9 sn susma, 60 sn üst sınır]
- **FG-06** (Zorunlu) Komut gerçekten belirsizse asistan tek kısa soru sorar (tur başına en çok bir soru). Netleştirme sırası ve kuralları FG-100..FG-102'dedir. [ÖĞRENCİ: Şekil 1 adım 2, AS1] [MAKALE-12, 06, 21] [ÖĞRENCİ-KARAR 17.11.2026 (3)]
- **FG-07** (Zorunlu) Asistan anladığını tek cümleyle geri okur; yaşlı onaylamadan işleme başlamaz. [ÖĞRENCİ: Şekil 1 adım 2, §4.1] [MAKALE-20, 12]
- **FG-08** (Olmalı) "Bu/şu" gibi göndermeler odaktaki öge ve açık uygulama bağlamıyla çözülür. [MAKALE-12, 01]
- **FG-09** (Olmalı) Kısa/dolaylı ifadeler ("sesi çok az") ilgili ayar eylemine eşlenir. [MAKALE-12]
- **FG-10** (Olabilir) Kullanıcı belirsiz bir öğeye dokunarak soruya bağlam ekleyebilir. [MAKALE-01]
- **FG-11** (Zorunlu) Acil durdurma düğmesi her aşamada ekrandadır ve tek dokunuşla çalışır. [ÖĞRENCİ: Şekil 1, Şekil 2] [MAKALE-03]
- **FG-12** (Zorunlu) Kullanıcı "baştan anlat" derse anlatım anında baştan başlar. [ÖĞRENCİ: vizyon ek fikir 2]
- **FG-13** (Zorunlu) Kullanıcı "tamam, hatırladım" derse asistan "Tamam, kapatıyorum" diyerek durur. [ÖĞRENCİ: vizyon ek fikir 2]
- **FG-14** (Zorunlu) Asistan konuşurken kullanıcı konuşmaya başlarsa asistan susar ve dinler (araya girme). [ÖĞRENCİ: vizyon ek fikir 2] [MAKALE-21, 25]
- **FG-15** (Olmalı) "Neden?" sorusuna katmanlı yanıt verilir: önce tek satır, istenirse gerekçe. [MAKALE-25, 21]
- **FG-16** (Zorunlu) Görev tamamlanınca asistan sesle bildirir. [ÖĞRENCİ: Şekil 1]
- **FG-17** (Zorunlu) Beklerken sesli/görsel ilerleme bilgisi verilir. [ÖĞRENCİ: Tablo 2 gecikme satırı] [MAKALE-03]

### 2.B "Sen yap" modu
- **FG-20** (Zorunlu) Asistan adımları erişilebilirlik hizmetiyle kullanıcı adına uygular (dokunma, kaydırma, metin yazma). [ÖĞRENCİ: §4.2, Şekil 1 adım 7, Şekil 2] [MAKALE-03]
- **FG-21** (Zorunlu) Her dokunuş ekranda bir nokta, her kaydırma bir iz olarak gösterilir (renk/biçim açık karar TK-08). [ÖĞRENCİ: vizyon]
- **FG-22** (Zorunlu) Her adım sesle anlatılır ("WhatsApp'a dokundum"); eğitici olması amaçtır. [ÖĞRENCİ: vizyon] [MAKALE-25: önce tek satır]
- **FG-23** (Zorunlu) Durdurma komutu gelince bir sonraki eylem başlamaz ve süren hareket kesilir. [ÖĞRENCİ: §4.2 "her an durdurabilir", Şekil 1] [MAKALE-03]
- **FG-24** (Zorunlu) Her eylemden sonra ekranın beklenen durumda olduğu doğrulanır; değilse asistan durur, tek soru sorar ve ekranı yeniden okur. [ÖĞRENCİ: Şekil 1 adım 8] [MAKALE-10]
- **FG-25** (Olmalı) Beklenmeyen sayfaya götüren eylem geri alınır ve geçmişe yazılmaz. [MAKALE-10]
- **FG-26** (Zorunlu) Kullanıcı seçimi gereken noktada (ör. hangi fotoğraf) asistan durur ve işi kullanıcıya devreder. [ÖĞRENCİ: vizyon]
- **FG-27** (Olmalı) Görev adım bütçesi aşılırsa asistan durur ve kullanıcıya devreder. [MAKALE-08: 10 adım] [MAKALE-11: insanın ~2 katı] [ÇIKARIM: ajanlar sık başarısız olur, devir şart]
- **FG-28** (Olmalı) Gizli/kaydırmayla ulaşılan (ör. yatay kaydırma) ögeler için açık kaydırma-keşif davranışı vardır. [MAKALE-11]

### 2.C "Ben yapayım" modu
- **FG-30** (Zorunlu) Asistan ekrana dokunmaz; doğru ögeyi vurgular ve sesle yönlendirir, dokunmayı yaşlı yapar. [ÖĞRENCİ: §4.2, Şekil 1 adım 7, vizyon]
- **FG-31** (Zorunlu) Kaydırma gereken yerde kaydırma yönü iz/ok ile gösterilir. [ÖĞRENCİ: vizyon]
- **FG-32** (Olmalı) Adım yönlendirmede yalnızca tek sonraki öge vurgulanır; "X nerede?" sorusunda en çok 3 aday vurgulanır. [MAKALE-05] [MAKALE-02]
- **FG-33** (Olmalı) Vurgu çevredeki ögeleri örtmez; bağlam görünür kalır. [MAKALE-02: HC/WZ görüşü]
- **FG-34** (Olmalı) Yanlış ögeye dokunulursa titreşimli ve sesli uyarı verilir, doğru öge yeniden gösterilir. [MAKALE-04, 03, 05]
- **FG-35** (Olmalı) Rehberlik ayrıntısı üç düzeyde ayarlanır ve kullanıcı öğrendikçe azalır. [ÖĞRENCİ: §4.2] [MAKALE-03]
- **FG-36** (Olmalı) Kullanıcı bir süre hareketsiz kalırsa asistan yardım önerir; süre ayarlanabilir. [MAKALE-05: 20 sn] [MAKALE-02: 13 sn]
- **FG-37** (Olabilir) "Yardım" istenince doğru öge/kurtarma yolu gösterilir; istenmeden gösterilmez. [MAKALE-05]
- **FG-38** (Olmalı) Yalnızca simgeyle gösterilen düğmeler (≡, ⋮) sözcüklerle tarif edilir. [MAKALE-04]
- **FG-39** (Olabilir) Kullanıcı yönlendirme sırasında "sen yap" diyerek moda geçebilir ve tersi. [MAKALE-04: 11/12 adım adım, 8 kişi tek tıkla da seçti] [ÇIKARIM]

### 2.D Görev belleği ve üç katmanlı karar
- **FG-40** (Zorunlu) Karar sırası: kural, sonra kayıtlı görev tarifi, en son dil modeli. [ÖĞRENCİ: Şekil 1 adım 4, Şekil 2, §4.3]
- **FG-41** (Zorunlu) İlk kez gelen görev dil modeliyle çözülür. [ÖĞRENCİ: vizyon ek fikir 1]
- **FG-42** (Zorunlu) Başarılı adım dizisi kütüphaneye yalnızca kullanıcının onayıyla eklenir. [ÖĞRENCİ: §4.3] [HOCA: gelecek çalışma 1, 4: insanın onayladığı/tanımladığı tarif]
- **FG-43** (Zorunlu) Aynı istek tekrar gelince kayıtlı adımlar dil modeline sorulmadan uygulanır. [ÖĞRENCİ: vizyon ek fikir 1] [MAKALE-09, 07, 15]
- **FG-44** (Zorunlu) Kayıtlı tarif anlamsal (vektör) benzerlikle bulunur; eşik altında dil modeline düşülür. [ÖĞRENCİ: Şekil 2, §4.3] [MAKALE-13, 04, 01]
- **FG-45** (Olmalı) Tarifler değişken parametrelidir (kişi adı, dosya vb.). [MAKALE-15, 08]
- **FG-46** (Zorunlu) Tarif oynatılırken her adımda ekran beklenenle karşılaştırılır; uyuşmazlıkta (uygulama güncellemesi, açılır pencere, kullanıcı müdahalesi) dil modeli yeniden devreye girer. [ÖĞRENCİ: vizyon ek fikir 1] [MAKALE-15, 05]
- **FG-47** (Zorunlu) Kayıtlı tarif de risk sınıflandırmasından ve onaydan muaf değildir. [ÖĞRENCİ: Şekil 1 adım 5-6] [MAKALE-15: sabit yol dinamik değişiklikte yanlış yürür]
- **FG-48** (Zorunlu) Kullanıcı kayıtlı görevlerini listeleyip silebilir. [ÖĞRENCİ: §4.6, Şekil 2] [MAKALE-22]
- **FG-49** (Olmalı) Ekranla uyuşmayan/başarısız tarif işaretlenir ve kullanılmaz. [MAKALE-15, 10]
- **FG-50** (Zorunlu) Tarif kişisel içerik (mesaj metni, parola, doğrulama kodu) saklamaz. [ÖĞRENCİ: §5.4] [ÇIKARIM: tarif verisi KVKK ölçülülüğü; MAKALE-13 notu giriş gerektiren akışların saklanmamasını uyarır]
- **FG-51** (Olabilir) Sık görevler (kişi arama, mesaj yazma, ayar, hava durumu) için önceden hazır kural/tarif gelir. [ÖĞRENCİ: Tablo 4 görev örnekleri] [ÇIKARIM: soğuk başlangıç gecikmesi]

### 2.E Aile ayarları ve ilk kurulum
- **FG-60** (Zorunlu) Asistan yalnızca aile ayarlarındaki izinli uygulamalarda işlem yapar. [ÖĞRENCİ: Şekil 1, Şekil 2]
- **FG-61** (Zorunlu) Yetkili aile üyesi yalnızca ayarları yönetir; görev kayıtlarını/içeriği göremez. [ÖĞRENCİ: §4.6]
- **FG-62** (Olmalı) Aile ayarlarını değiştirmek kimlik doğrulaması (ör. PIN) ister. [MAKALE-04: yardımcının kimliği güvenlik kaygısı]
- **FG-63** (Olmalı) Rehberlik düzeyi, konuşma hızı ve yazı boyutu ayarlanabilir. [MAKALE-03, 22, 05]
- **FG-64** (Zorunlu) İlk açılışta sade, sesli kurulum: izinler, rıza, iki modun tanıtımı. [ÖĞRENCİ: §5.5] [MAKALE-23: uygulama içi öğretici]

### 2.F Risk derecesi, onay ve koruma
- **FG-70** (Zorunlu) Her planlanan eylem dil modelinden bağımsız, kural tabanlı bir sınıflandırıcıyla Düşük/Orta/Yüksek diye derecelenir. [ÖĞRENCİ: Şekil 1 adım 5, Tablo 3, Şekil 2]
- **FG-71** (Zorunlu) Düşük risk: asistan ne yapacağını anlatır ve yapar. [ÖĞRENCİ: Tablo 3]
- **FG-72** (Zorunlu) Orta risk (mesaj gönderme, arama başlatma, form doldurma): yapacağını gösterir, onay bekler; onay gelmezse yapmaz. [ÖĞRENCİ: Tablo 3]
- **FG-73** (Zorunlu) Yüksek risk (para/ödeme, silme, parola/hesap değişikliği, doğrulama kodu ekranı): durur ve sorar; sessizlik "hayır" sayılır. [ÖĞRENCİ: Tablo 3, Şekil 1 adım 6] [MAKALE-16: danışma güvenli davranıştır]
- **FG-74** (Zorunlu) Dil modelinin önerdiği eylem telefonda yeniden risk sınıflandırmasından geçer. [ÖĞRENCİ: Şekil 1 notu, §5.3] [MAKALE-16: ajanda risk fark etme %36]
- **FG-75** (Olmalı) Türkçe tehlikeli-eylem sözcük listesi (sil, öde, gönder vb.) ve ekran uyarı metinleri sınıflandırıcıya girdi olur. [MAKALE-03, 09] [ÇIKARIM: Türkçe karşılıklar]
- **FG-76** (Olmalı) Riskli adımda belirgin uyarı ve açık onay düğmesi/sözü gerekir; yanlış dokunuş onay sayılmaz. [MAKALE-03]
- **FG-77** (Zorunlu) Gizlilik gerektiren adımlarda (parola, ödeme, kişisel bilgi girişi) otomasyon durur, kullanıcı kendisi girer. [ÖĞRENCİ: Tablo 3] [MAKALE-04]
- **FG-78** (Zorunlu) Banka uygulamaları baştan engellidir; parola alanları okunmaz ve atlanır. [ÖĞRENCİ: Şekil 1, Şekil 2, Tablo 3]
- **FG-79** (Zorunlu) Ekranda doğrulama kodu ya da "kimseyle paylaşmayın" ifadesi görülürse hiçbir işlem yapılmaz, yalnızca kullanıcı uyarılır; rakam dizileri maskelenir, modele gitmez, günlüğe yazılmaz. [ÖĞRENCİ: §5.4] [HOCA: Hafta-1 madde 5 ✅]
- **FG-80** (Zorunlu) Beklenmedik açılır pencere/üst katman görülürse işlem durur ve kullanıcıya "bu görevinizin parçası değil" denir. [ÖĞRENCİ: Şekil 2 notu] [MAKALE-17]
- **FG-81** (Olabilir) Dolandırıcılık biçimli ekran/mesaj ifadelerinde (virüs, hediye) uyarı verilir. [MAKALE-19, 17]
- **FG-82** (Olmalı) Düşük güvenli plan eyleme dönmez; kullanıcıya sorulur (yüzde gösterilmeden). [MAKALE-19] [MAKALE-25]
- **FG-83** (Olmalı) Asistan sağlık/tıbbi tavsiye vermez ve acil durum aracı olarak sunulmaz. [MAKALE-23, 19, 21]

### 2.G Ölçüm altyapısı
- **FG-90** (Olmalı) Yerel, kişisel içeriksiz metrik günlüğü tutulur: adım süresi, çözen katman, dil modeli çağrı sayısı, yanlış dokunuş, durdurma, onay sonucu. [ÖĞRENCİ: Tablo 2] [MAKALE-09, 12]
- **FG-91** (Olmalı) Buluta giden her isteğin maskelenmiş kopyası denetim için yerelde kısa süre tutulur. [ÖĞRENCİ: Tablo 2 "günlük denetimi"]
- **FG-92** (Olabilir) Hata kayıtları nedene göre etiketlenir (niyet, planlama, çevre, yürütme). [MAKALE-12, 11]

### 2.H Netleştirme ve rızasız mod (17.11.2026 kararları)
- **FG-100** (Zorunlu) Netleştirme sırası: önce cihazdaki gerçek veriye (rehber, kurulu uygulamalar, ayarlar, ekran ağacı) bakan kurallar; kural yetmezse dil modeli. Dil modeli, eylem planı isteğiyle aynı çağrıda dönen "netleştirme sorusu" alanıyla soru üretir; ek tur/ek çağrı yoktur. Tek netleştirme sorusu sınırı korunur. [ÖĞRENCİ-KARAR 17.11.2026 (3)] [ÖĞRENCİ: Şekil 1 adım 2, Şekil 2, AS1]
- **FG-101** (Zorunlu) Soru yalnızca gerçek belirsizlik varsa sorulur. Rehberde "Ayşe" ile eşleşen tek kişi varsa "hangi Ayşe?" ASLA sorulmaz; birden çok eşleşmede seçenekler adlarıyla sesli okunur. [ÖĞRENCİ-KARAR 17.11.2026 (3)] [MAKALE-21: tek soru ilkesi]
- **FG-102** (Zorunlu) Gereksiz soru bir hata sayılır; "gereksiz soru oranı" ölçülür ve sınanır (DEG-31). [ÖĞRENCİ-KARAR 17.11.2026 (3)]
- **FG-103** (Zorunlu) Bulut rızası verilmemişse/geri alınmışsa uygulama yalnızca cihaz içi özelliklerle çalışır (kural, kayıtlı tarif, cihaz içi konuşma tanıma); dil modeli çağrılmaz, bulut gerektiren istekte asistan bunu sesle söyler. [ÖĞRENCİ-KARAR 17.11.2026 (2)] [ÖĞRENCİ: Tablo 5]

---

## 3. İşlevsel olmayan gereksinimler (NFG)

### 3.1 Erişilebilirlik ve yaşlıya uygun arayüz
- **NFG-01** (Zorunlu) Asistanın kendi arayüzünde büyük/ayarlanabilir yazı ve yüksek kontrast vardır. [MAKALE-05: DI2] [MAKALE-23] [ÇIKARIM]
- **NFG-02** (Zorunlu) Asistanın kendi düğmeleri metin etiketlidir; yalnızca simgeyle bırakılmaz. [MAKALE-04: simgeli düğmeler ana hata kaynağı]
- **NFG-03** (Olmalı) Dokunma hedefleri büyüktür. [ÇIKARIM: Android erişilebilirlik yönergesi; yaşlı kullanıcı]
- **NFG-04** (Zorunlu) Çıktı hem sesli hem görsel verilir. [ÖĞRENCİ: vizyon] [MAKALE-02: yalnızca görsel çıktı sınırlılık]
- **NFG-05** (Zorunlu) Cümleler kısa, soru tek, yüzde/olasılık yok. [ÖĞRENCİ: §4.5] [MAKALE-21, 25]
- **NFG-06** (Zorunlu) Kişisel bilgi istenmeden önce nedeni söylenir. [ÖĞRENCİ: §4.5] [MAKALE-21]
- **NFG-07** (Zorunlu) Son söz kullanıcıdadır; asistan kullanıcının kararını sessizce geçersiz kılmaz. [ÖĞRENCİ: Şekil 1] [MAKALE-21]
- **NFG-08** (Olmalı) Ton rutinde sıcak, riskli/acil durumda sade ve net olur. [ÖĞRENCİ: §4.5] [MAKALE-24, 25]
- **NFG-09** (Olmalı) Açıklamalar o anki ekrana dayanır ("Gönder düğmesini görüyorum"). [MAKALE-24, 25]
- **NFG-10** (Olmalı) Aynı kavram için tutarlı terim/metafor kullanılır (kullanıcı başına). [MAKALE-04]
- **NFG-11** (Olmalı) Tek yanıt/tek seçenek sunulur. [MAKALE-01, 06]
- **NFG-12** (Olmalı) Hata geri bildirimi yumuşak ses ve titreşimle verilir. [MAKALE-05: DI3] [MAKALE-04]
- **NFG-13** (Olabilir) Asistanın kendi arayüzü TalkBack ile uyumludur. [ÇIKARIM: erişilebilirlik]
- **NFG-14** (Olabilir) Dil, kullanıcıyı "yardıma muhtaç" değil yetkin gösterir (damgalanma azaltma). [MAKALE-23]
- **NFG-15** (Olmalı) Başkalarının duyabileceği ortamda kişisel ayrıntılar sesli okunmaz (ayarla). [MAKALE-25, 21]

### 3.2 Ses
- **NFG-16** (Zorunlu) Konuşma tanıma cihaz üzerinde çalışır; ses buluta gönderilmez. [ÖĞRENCİ: Şekil 2, Tablo 5]
- **NFG-17** (Zorunlu) Android yerleşik tanıyıcının çevrimiçi modu çalışma sırasında kullanılmaz. [ÖĞRENCİ: Tablo 5]
- **NFG-18** (Zorunlu) Konuşma yokken/yalnızca gürültüde komut üretilmez; uydurma transkript, geri okuma ve onay olmadan eylem tetiklemez. [MAKALE-20: halüsinasyon] [ÖĞRENCİ: §4.1]
- **NFG-19** (Olmalı) Türkçe yaşlı konuşmasında kelime hata oranı ve hız ölçülür; aksan/lehçe çeşitliliği test edilir. [ÖĞRENCİ: §4.1, Tablo 2] [HOCA] [MAKALE-20, 03, 23]
- **NFG-20** (Olmalı) Sesli yanıt Türkçedir ve yavaşlatılabilir. [MAKALE-22: %80 hız] [MAKALE-04]
- **NFG-21** (Olmalı) Arka plan gürültüsü (TV, telefon görüşmesi) altında test edilir. [MAKALE-20]

### 3.3 Gecikme
- **NFG-22** (Zorunlu) Yanıt anlık hissettirmelidir; kullanıcı 5-10 sn bekletilmez. [ÖĞRENCİ: vizyon ek fikir 2]
- **NFG-23** (Olmalı) Gecikme için sayısal eşikler tanımlanır ve ölçülür. Önerilen başlangıç: dokunma→dinleme ≤1 sn, komut bitişi→ilk sesli geri bildirim ≤2 sn, bekleme >5 sn ise sesli bilgi. [ÇIKARIM: vizyonun "5-10 sn olmamalı" ifadesinin sınanabilir hâli; öğrenci onayı gerekir, TK-09]
- **NFG-24** (Zorunlu) Dil modeli yalnızca kural ve kayıtlı tarif sonuç vermediğinde çağrılır; netleştirme için ayrı çağrı yapılmaz (FG-100). [ÖĞRENCİ: Şekil 2, Tablo 2] [MAKALE-09, 07] [ÖĞRENCİ-KARAR 17.11.2026 (3)]
- **NFG-25** (Zorunlu) Dil modeli çağrıları günlük bütçe/kota ile sınırlıdır; kota dolarsa asistan bunu sesle söyler ve kayıtlı görevlerle sürer. [ÖĞRENCİ: §8] [PARA?]
- **NFG-26** (Olmalı) Kayıtlı görevler internetsiz çalışır. [ÖĞRENCİ: vizyon ek fikir 1] [MAKALE-23]
- **NFG-27** (Zorunlu) Gecikme hedef telefonda adım ve görev başına, dil modeli/gömme/yürütme ayrı ayrı ölçülür. [ÖĞRENCİ: Tablo 2] [MAKALE-09, 11, 20]
- **NFG-28** (Olmalı) Rutin adımlarda uzun akıl yürütme modları kullanılmaz ya da sınırlanır (gecikme ve sızıntı). [MAKALE-16: o1 18,3-25,6 sn] [MAKALE-18]

### 3.4 Güvenilirlik
- **NFG-29** (Zorunlu) Ekran birincil olarak erişilebilirlik ağacından okunur. [ÖĞRENCİ: Şekil 1 adım 3, Tablo 2] [MAKALE-11]
- **NFG-30** (Olmalı) Hedef öge ağaçta bulunamazsa asistan kullanıcıya sorar; bulunamama oranı ölçülür. [ÖĞRENCİ: Tablo 2] [MAKALE-04]
- **NFG-31** (Zorunlu) Model çıktısı yalnızca şemalı eylem olarak kabul edilir; ayrıştırılamayan çıktı eylem üretmez ve akıl yürütme metni seslendirilmez. [MAKALE-18, 03]
- **NFG-32** (Zorunlu) Ağ/sunucu/kota hatasında asistan güvenle durur, sesle bilgi verir, yarım eylem bırakmaz. [ÇIKARIM: dil modeli bulutta, ağ güvenilmez]
- **NFG-33** (Zorunlu) En az iki farklı marka telefonda test edilir; birincil test cihazı Samsung Galaxy A15'tir (ikinci marka SS-09). [ÖĞRENCİ: §8] [MAKALE-11] [ÖĞRENCİ-KARAR 17.11.2026 (5)]
- **NFG-34** (Olmalı) Erişilebilirlik servisi kapanırsa kullanıcı sesle bilgilendirilir. [ÇIKARIM: servis sistem tarafından durdurulabilir]
- **NFG-35** (Olmalı) Uygulama sürümü değişince kayıtlı tarif yeniden doğrulanır. [ÖĞRENCİ: vizyon] [MAKALE-05]
- **NFG-36** (Olabilir) Ölçümler çok tohumla/düşük sıcaklıkla tekrarlanabilir yapılır. [MAKALE-11, 09]

### 3.5 Güvenlik
- **NFG-37** (Zorunlu) Ekrandaki metin komut değil veri sayılır. [ÖĞRENCİ: Şekil 1 notu, Şekil 2, §4.4] [MAKALE-16, 17, 07]
- **NFG-38** (Zorunlu) Her dokunuştan önce hedef ögenin planlanan adıma ait olduğu denetlenir. [ÖĞRENCİ: §4.4] [MAKALE-17]
- **NFG-39** (Zorunlu) Eylem hedefinin tek kaynağı kullanıcının onayladığı komuttur. [ÖĞRENCİ: Şekil 1 adım 2, §4.4] [MAKALE-17, 16]
- **NFG-40** (Olmalı) Asistanın kendi pencereleri (baloncuk, iz, nokta) gözlemden dışlanır. [MAKALE-17]
- **NFG-41** (Zorunlu) API anahtarı telefonda tutulmaz; dil modeline yalnızca vekil sunucu üzerinden erişilir. [ÖĞRENCİ: Şekil 2] [PARA?]
- **NFG-42** (Olmalı) Vekil sunucu yetkisiz/aşırı kullanımı sınırlar. [ÇIKARIM: ücretsiz kota kötüye kullanımla tükenir]
- **NFG-43** (Zorunlu) Bulut iletişimi şifrelidir (TLS); yerel veriler uygulamaya özel depolamadadır. [ÇIKARIM: KVKK m.12 veri güvenliği]
- **NFG-44** (Zorunlu) Düşünce izi saklanmaz, günlüğe yazılmaz ve kullanıcıya/buluta aktarılmaz. [ÖĞRENCİ: §4.4] [MAKALE-18]
- **NFG-45** (Olmalı) Sahte açılır pencere ve istem-enjeksiyonu test seti (Türkçe "virüs tespit edildi", "hediye kazandınız") vardır. [ÖĞRENCİ: Tablo 2] [MAKALE-17, 16] [HOCA: Güz 4-5]
- **NFG-46** (Zorunlu) Dil modeline yalnızca o görev için gerekli alanlar gider. [ÖĞRENCİ: §5.3] [MAKALE-18, 09]

### 3.6 Gizlilik, KVKK ve Play politikası
- **NFG-47** (Zorunlu) Uygulama Play'de yayımlanacakmış gibi KVKK (6698) ve Play politikalarına göre tasarlanır; yalnız APK dağıtılacak olsa da bu gereksinim ve tüm SEC/NFG-47..NFG-71, NFG-76..NFG-81 maddeleri aynen geçerlidir. [ÖĞRENCİ: vizyon kısıtlar, §5.5] [ÖĞRENCİ-KARAR 17.11.2026 (1)]
- **NFG-48** (Zorunlu) Ses cihazda işlenir ve hiçbir koşulda buluta çıkmaz. [ÖĞRENCİ: Şekil 2, Tablo 5]
- **NFG-49** (Zorunlu) Normal kullanımda ses kaydı saklanmaz; işlendikten sonra silinir. [ÇIKARIM: ölçülülük, KVKK m.4; ÖĞRENCİ Şekil 2 "ses kaydı yalnızca bu bölgede işlenir"]
- **NFG-50** (Zorunlu) Buluta yalnızca maskelenmiş metin özeti gider; ad, telefon, e-posta maskelenir; kimlik no, IBAN, kart no ve adres de maskelenir. [ÖĞRENCİ: Şekil 1 adım 4, Şekil 2, Tablo 5] [MAKALE-09: ad/telefon/e-posta] [ÇIKARIM: diğer kategoriler]
- **NFG-51** (Zorunlu) Ekran görüntüsü buluta gönderilmez. [ÖĞRENCİ: Şekil 2 "maskeli özet"] [MAKALE-03: dış API'ye ekran görüntüsü gizlilik riski] (doğrulama için SS-05)
- **NFG-52** (Zorunlu) Parola alanları hiç okunmaz. [ÖĞRENCİ: Şekil 1, Şekil 2]
- **NFG-53** (Zorunlu) Sürekli ekran izleme yoktur; ekran yalnızca etkin görev sırasında okunur. [ÇIKARIM: KVKK m.4] [MAKALE-18: yalnızca gerekeni paylaş]
- **NFG-54** (Zorunlu) Yerel veriler (ayarlar, günlük, kayıtlı tarifler) cihazda durur; kullanıcı görüp silebilir. [ÖĞRENCİ: Şekil 2, §4.6] [MAKALE-22, 23]
- **NFG-55** (Olmalı) Kullanıcı sesle "unut/sil" diyebilir. [MAKALE-22]
- **NFG-56** (Zorunlu) İlk kullanımda sade Türkçe aydınlatma metni (KVKK m.10) ve açık rıza (m.5) alınır; amaçlar (bulut dil modeli, tarif kaydı, araştırma ses kaydı) ayrı onaylanır ve istenen an geri çekilebilir. [ÖĞRENCİ: Tablo 5] [MAKALE-21: her paylaşım için onay] [MAKALE-23]
- **NFG-57** (Zorunlu) Aydınlatma metni büyük yazıyla gösterilir ve sesle okunabilir; yurt dışına aktarım (bulut dil modeli) ve alıcı belirtilir. [ÖĞRENCİ: Tablo 5 (m.9)] [MAKALE-23: doğal konuşmayla bildirim]
- **NFG-58** (Olmalı) Veri buluta giderken görünür bir gösterge çıkar. [MAKALE-23]
- **NFG-59** (Zorunlu) Her veri türü için saklama süresi tanımlıdır ve otomatik silinir (süre değerleri açık, SS-07). [ÖĞRENCİ: Tablo 5]
- **NFG-60** (Zorunlu) İlgili kişi hakları (görme, silme, rızayı geri çekme) uygulanabilir. [ÖĞRENCİ: Tablo 5] [MAKALE-22]
- **NFG-61** (Zorunlu) Araştırma verisi kodludur; ad/iletişim ayrı tutulur; ses kaydı yalnızca ayrı izinle, katılımcının telefonunda şifreli saklanır ve kelime hata oranı hesaplanıp kontrol edildikten sonra silinir. [ÖĞRENCİ: Tablo 5]
- **NFG-62** (Zorunlu) Çalışmada gerçek hesap, kişisel içerik ve gerçek banka/ödeme işlemi kullanılmaz; test telefonu ve kurgusal içerik kullanılır. [ÖĞRENCİ: Tablo 4, Tablo 5]
- **NFG-63** (Zorunlu) Savunmasız grup önlemleri uygulanır: baskısız gönüllülük, yanında yakın, yorgunlukta ara, destek bilgisi. [ÖĞRENCİ: Tablo 5] [HOCA: 40 dk yük uyarısı]
- **NFG-64** (Zorunlu) Etik karar/onay netleşmeden yaşlı katılımcıdan tez verisi toplanmaz. [ÖĞRENCİ: §6.2] [HOCA: Esas sorun 1]
- **NFG-65** (Zorunlu) Erişilebilirlik izni istenmeden önce, menüye girmeden görülen bir uygulama içi bilgilendirme ve açık onay vardır. [ÖĞRENCİ: §5.5] [HOCA: Play notu yerinde]
- **NFG-66** (Zorunlu) Play'e çıkılırsa API 31+ erişilebilirlik beyan formu doldurulur. [ÖĞRENCİ: §5.5]
- **NFG-67** (Zorunlu) SMS ve bildirim erişimi izni istenmez; SMS doğrulama kodu okunmaz. [ÖĞRENCİ: §5.4, §5.5] [HOCA: ✅]
- **NFG-68** (Olmalı) Çekirdek akış için gereksiz hiçbir izin istenmez; erişilebilirlik verisi yalnızca asistan işlevi için kullanılır. [ÇIKARIM: Play erişilebilirlik politikası ruhu; Google sayfasıyla doğrulanmalı]
- **NFG-69** (Olmalı) Play yayını için gizlilik politikası bağlantısı ve Veri Güvenliği formu hazırdır. [ÇIKARIM: Play genel gereği; doğrulanmalı]
- **NFG-70** (Olabilir, PLANLANMADI) "Sen yap" yürütücüsü iki bileşene ayrılır: dil modeli planlı (yalnız APK) ve insan-onaylı deterministik tarif yürütücüsü; Play sürümünde birincisi kapatılabilir. Öğrenci kararıyla ayrı Play-uyumlu yürütücü yapılmaz; hocanın bu talebi "Zorunlu değil"dir, bu yüzden çelişki sayılmadı (Ek A'da işaretli). [ÖĞRENCİ: §5.5] [HOCA: gelecek çalışma 1] [ÖĞRENCİ-KARAR 17.11.2026 (1)]
- **NFG-71** (Olmalı) Ekrandaki üçüncü kişi verisi ve özel nitelikli içerik (ör. sağlık; KVKK m.6) ölçülülük ilkesiyle maskelenir. [ÇIKARIM: ekran içeriği önceden kestirilemez]

### 3.7 Cihaz, konuşma tanıma ve bulut/KVKK ekleri (17.11.2026 kararları)
- **NFG-72** (Zorunlu) Uygulama her Android telefonda iyi çalışır; düşük RAM/işlemciye göre tasarlanır (hafif modeller, ağır işlemin arka plana alınması, bellek tavanı ölçülür). Birincil test/hedef cihaz Samsung Galaxy A15 (orta-alt seviye). [ÖĞRENCİ-KARAR 17.11.2026 (5)] [MAKALE-09, 20: telefonda gecikme/boyut]
- **NFG-73** (Zorunlu) Üretici pil/arka plan kısıtlamalarına (ör. Samsung One UI uyku modu, "kullanılmayan uygulamalar") dayanıklılık: servis öldürülürse tespit, kullanıcıya sesle bildirim ve pil muafiyeti ayarına yönlendirme; Galaxy A15'te uzun süre bekleme sonrası baloncuğun ve servisin ayakta kaldığı sınanır. [ÖĞRENCİ-KARAR 17.11.2026 (5)] (bkz. NFG-34)
- **NFG-74** (Zorunlu) Cihaz içi büyük dil modeli (ör. Gemini Nano) kullanılmaz; yalnız üst seviye telefonlarda bulunur. Cihaz içi yalnız küçük bileşenler (konuşma tanıma, gömme, kural) çalışır. [ÖĞRENCİ-KARAR 17.11.2026 (2)]
- **NFG-75** (Zorunlu) Konuşma tanıma motoru, cihaz içi Whisper (tiny/base/small) ile Android'in çevrimdışı yerleşik tanıyıcısının küçük bir denemede ölçülmesiyle seçilir; ölçüt: Türkçe doğruluk + Galaxy A15'te gecikme. Yerleşik tanıyıcı seçilse bile yalnız çevrimdışı modda çalışır (NFG-17). [ÖĞRENCİ-KARAR 17.11.2026 (4)] [HOCA: Whisper tiny/base/small] [ÖĞRENCİ: Tablo 5]
- **NFG-76** (Zorunlu) Bulut dil modeli Gemini API ücretsiz katmanıdır (yedek: Groq ücretsiz katmanı). Ücretsiz katmanda gönderilen veri model eğitiminde kullanılabilir ve alıcı Türkiye'de/AEA'da değildir; bu iki olgu aydınlatma metninde sade dille açıkça yazılır. Kota ve veri koşulları kodlamadan önce resmî sayfalardan doğrulanır (DOĞ-01). [ÖĞRENCİ-KARAR 17.11.2026 (2)] [ÖĞRENCİ: Şekil 2 "Gemini Flash"] [PARA?]
- **NFG-77** (Zorunlu) İlk açılışta sade dilli aydınlatma metni gösterilir ve yurt dışına aktarım için ayrı, açık, önceden işaretsiz rıza alınır; diğer rızalarla (tarif kaydı, araştırma ses kaydı, erişilebilirlik) birleştirilmez. [ÖĞRENCİ-KARAR 17.11.2026 (2)] [ÖĞRENCİ: Tablo 5] (bkz. NFG-56, NFG-57)
- **NFG-78** (Zorunlu) Yurt dışı aktarım rızası verilmezse veya sonradan geri alınırsa uygulama yalnızca cihaz içi özelliklerle çalışır (FG-103); geri alma ayarlardan tek adımda yapılır, bulut çağrıları o anda durur ve rıza kaydı (zaman, sürüm) yerelde tutulur. [ÖĞRENCİ-KARAR 17.11.2026 (2)] [ÖĞRENCİ: Tablo 5]
- **NFG-79** (Zorunlu) Kişisel veri maskeleme cihazda, bulut çağrısından önce yapılır; maskeleme başarısız/belirsizse istek gönderilmez (güvenli tarafta dur). Ham ses ve ekran görüntüsü cihazdan çıkmaz. [ÖĞRENCİ-KARAR 17.11.2026 (2)] [ÖĞRENCİ: Şekil 2, Tablo 5] (bkz. NFG-48, NFG-50, NFG-51)
- **NFG-80** (Zorunlu) Yedek sağlayıcı (Groq) kullanılırsa aynı rıza, maskeleme ve veri-minimizasyonu kuralları geçerlidir; aydınlatma metni her iki alıcıyı adıyla belirtir, yedeğe geçiş rıza kapsamı dışına çıkmaz. [ÖĞRENCİ-KARAR 17.11.2026 (2)]
- **NFG-81** (Zorunlu) Aydınlatma metni asgari şunları içerir: veri sorumlusu kimliği, işleme amacı, aktarılan veri (yalnız maskeli metin), alıcılar ve ülke, hukuki sebep (açık rıza), saklama süresi, haklar ve geri alma yolu, erişilebilirlik hizmeti kullanım açıklaması (neyi okur/okumaz, neden). [ÖĞRENCİ-KARAR 17.11.2026 (6)] [ÖĞRENCİ: Tablo 5, §5.5] (bkz. NFG-56, NFG-65; veri sorumlusu için SS-13)
- **NFG-82** (Zorunlu) "Tüm verilerimi sil" tek yerden çalışır: kayıtlı tarifler, günlükler, rıza kaydı, denetim kopyaları; saklama süreleri dolunca otomatik silme çalışır. [ÖĞRENCİ-KARAR 17.11.2026 (6)] [ÖĞRENCİ: Tablo 5] (bkz. NFG-54, NFG-59, NFG-60)

---

## 4. Kapsam dışı
- **KD-01** SMS doğrulama kodunun okunması/otomatik girilmesi ve bildirim erişimi. [ÖĞRENCİ: §5.4] [HOCA: ✅]
- **KD-02** Banka/ödeme uygulamalarında asistan işlemi (baştan engelli). [ÖĞRENCİ: Tablo 3]
- **KD-03** iOS ve diğer platformlar. [ÖĞRENCİ: vizyon]
- **KD-04** Play Store'da fiilî yayın; yalnızca APK. Yayın ayrıca değerlendirilir. [ÖĞRENCİ: §5.5, §8] [HOCA] [ÖĞRENCİ-KARAR 17.11.2026 (1)]
- **KD-12** Play'e uyumlu ayrı "Sen yap" yürütücüsü (NFG-70). [ÖĞRENCİ-KARAR 17.11.2026 (1)] (hoca: "Zorunlu değil")
- **KD-13** Cihaz içi büyük dil modeli (ör. Gemini Nano). [ÖĞRENCİ-KARAR 17.11.2026 (2)]
- **KD-05** Uzaktan aile görev tarifi gönderme (WePilot benzeri aile katmanı genişletmesi). [HOCA: gelecek çalışma 4]
- **KD-06** Türkçe yaşlı komut veri kümesinin yayımı (200-300 komut). [HOCA: gelecek çalışma 2]
- **KD-07** Sağlık/ilaç hatırlatma, arkadaşlık/sohbet robotu, acil durum çağrısı. [MAKALE-21, 22, 23] [ÇIKARIM: projenin tanımı dışında]
- **KD-08** Kullanıcının gerçek telefonunda otonom uygulama keşfi. [MAKALE-08] [MAKALE-13: 1-8 saat/uygulama]
- **KD-09** Çok parmaklı/düzensiz hareketler. [MAKALE-08]
- **KD-10** AR/başlık donanımı. [MAKALE-05]
- **KD-11** Ek bir kullanıcı çalışması olmadan uzun dönem (>2 ay) kalıcılık iddiası. [HOCA: ExplorAR düzeltmesi] [MAKALE-23]

---

## 5. Değerlendirme ve başarı ölçütleri

Araştırma soruları: AS1 (tek netleştirme sorusu görev başarısını/niyet anlamayı artırıyor mu), AS2 ("Sen yap" ve "Ben yapayım" kiplerinde yardımsız tamamlama), AS3 (risk dereceli onay akışı istenmeyen işlemi azaltıyor mu, istemler anlaşılıyor mu). [ÖĞRENCİ: §1.1] Seçilen etik yol (A/B) belgenin başına AS'lerin altına tek cümleyle yazılacaktır. [HOCA] Hocanın ve makalelerin eşik değeri vermediği ölçütlerde eşik öğrenciden istenir (Bölüm 8).

- **DEG-01** (Zorunlu) Etik yol kararı (A: kurul başvurusu / B: sistem düzeyi değerlendirme) yazılı notlanır ve Tablo 6'ya işlenir. [HOCA: Esas sorun 1] [ÖĞRENCİ: §6.2]
- **DEG-02** (Zorunlu) AS1 ölçümleri: görev başarısı, niyetin doğru anlaşılma oranı, ek tur sayısı; alt ölçüm Türkçe yaşlı konuşmasında kelime hata oranı. [ÖĞRENCİ: Tablo 2, §1.1] [MAKALE-06, 12]
- **DEG-03** (Zorunlu) AS1 ayrı kısa blok olarak tasarlanır: her katılımcıya 2-3 eksik komut, bir kez netleştirmeli bir kez netleştirmesiz; ya da kaydedilmiş komutlar üzerinde çevrim dışı. Yol B'de STAQ benzeri Türkçe komut setinde niyet doğruluğu ölçülür. [HOCA: Esas sorun 1-2] [MAKALE-06]
- **DEG-04** (Zorunlu) AS2 ölçümleri: yardımsız tamamlama oranı, süre, hata sayısı, kip tercihi. [ÖĞRENCİ: Tablo 2, Tablo 4] [MAKALE-05, 04]
- **DEG-05** (Zorunlu) AS2'de ölçülen şey "öğrenmenin hemen ardından yardımsız tamamlama" olarak adlandırılır; uzun süreli kalıcılık iddia edilmez. [HOCA: küçük düzeltmeler] [MAKALE-05]
- **DEG-06** (Zorunlu) AS3 ölçümleri: riskli görev setinde yanlışlıkla gerçekleşen işlem oranı; onay istemlerinin anlaşılırlığı (5'li ölçek). [ÖĞRENCİ: Tablo 2, Tablo 4] [HOCA: Yol B örneği] [MAKALE-16]
- **DEG-07** (Zorunlu) AS3 senaryoları kurgusal ve kısadır; katılımcı başına en çok 2 senaryo. [HOCA: Esas sorun 2]
- **DEG-08** (Zorunlu) Her oturum için dakika dakika akış (karşılama, alıştırma, görevler, anket, mola) yazılır; toplam 40 dk'yı aşarsa görev sayısı azaltılır. [HOCA: Esas sorun 2] [ÖĞRENCİ: Tablo 4: 2 oturum x ~40 dk]
- **DEG-09** (Zorunlu) Pilotla süre ölçülür: 1-2 genç yetişkinle süre pilotu; 40 dk aşılırsa görev azaltılır. [HOCA: Bahar 1-2] [ÖĞRENCİ: Tablo 6: 2-3 yaşlı pilot]
- **DEG-10** (Zorunlu) Katılımcı: 65+, Türkçe, kendi telefonu; hedef 16, alt sınır 12; denekler içi, sıra dengeli; iki oturum arasında en az bir gün. [ÖĞRENCİ: Tablo 4]
- **DEG-11** (Zorunlu) Görevler: kip başına 4 (kişi arama, mesaj yazma, ayar, hava durumu); test telefonu/hesabı; gerçek banka işlemi yok. [ÖĞRENCİ: Tablo 4]
- **DEG-12** (Zorunlu) SUS kullanılır; kaynağı Brooke (1996), yorumu Bangor vd. (2008); kullanılan Türkçe sürüm ve kaynağı belirtilir. [HOCA: önerilen kaynaklar] [MAKALE-21: SUS 85]
- **DEG-13** (Olmalı) Çözümleme: betimsel + parametrik olmayan testler; yaş ve telefon deneyimi ayrı raporlanır; görev süresinin ortalaması yanında SD (kararlılık) da verilir. [ÖĞRENCİ: Tablo 4] [MAKALE-04]
- **DEG-14** (Zorunlu) Whisper sürümleri telefonda hız (ve kelime hata oranı) tablosuyla karşılaştırılır: tiny, base, small. [HOCA: yol haritası Güz 2-3] [ÖĞRENCİ: Tablo 2, §4.1] [MAKALE-20] Ölçüt ayrıntısı: 2-3 normalizasyon düzeyi, halüsinasyon ve döngü oranı, saniye/ifade (cihazda). [MAKALE-20]
- **DEG-15** (Zorunlu) Güvenlik testlerinde kullanılan riskli görev seti AS3 senaryolarıyla aynıdır. [HOCA: Güz 4-5]
- **DEG-16** (Zorunlu) Sahte açılır pencere testi: yanlış tıklama oranı (adım başına ASR ve görev başına TASR) ve görev başarısı. [ÖĞRENCİ: Tablo 2] [MAKALE-17]
- **DEG-17** (Olmalı) Enjeksiyon testi: ekrana yerleştirilmiş talimatın yok sayılma oranı. [MAKALE-16, 18]
- **DEG-18** (Olmalı) Düşük riskli görevi tamamlama ve yüksek riskli görevde onay isteme oranı birlikte raporlanır (aşırı ret ölçülür). [MAKALE-16]
- **DEG-19** (Zorunlu) Katman başına çözülen görev oranı, dil modeli çağrı sayısı/maliyeti, adım başına ortalama süre, beklerken görevi bırakan oran. [ÖĞRENCİ: Tablo 2]
- **DEG-20** (Olmalı) Hedef ögenin ağaçta bulunamadığı ekran oranı. [ÖĞRENCİ: Tablo 2]
- **DEG-21** (Zorunlu) Buluta giden isteklerde maskesiz kişisel veri sayısı (günlük denetimi), gizlilik rahatlığı (5'li) ve silme isteği testi. [ÖĞRENCİ: Tablo 2]
- **DEG-22** (Olmalı) Türkçe komut setinde dil kategorisine göre başarı; ham komut ile yeniden ifade edilmiş komut karşılaştırması (normalizasyon ablasyonu). [MAKALE-12, 06]
- **DEG-23** (Olmalı) Paraphrase doğruluğu (doğru/kısmen/yanlış), iki değerlendirici ve kappa; anlaşılma testi için ikinci grup. [MAKALE-06]
- **DEG-24** (Olmalı) Başarı oranı en az 3 tohumla ortalama ve varyansla; insan (yaşlı) taban çizgisi. [MAKALE-11]
- **DEG-25** (Olmalı) Adım verimliliği (en iyi yola göre) ve hata taksonomisi (niyet/planlama/çevre/yürütme). [MAKALE-07, 12]
- **DEG-26** (Olmalı) Katman/bellek ablasyonu ve öğrenme eğrisi (ilk k görev); tarif isabet oranı; ilk eyleme kadar süre (bellekli/belleksiz). [MAKALE-10, 15, 13, 09]
- **DEG-27** (Olabilir) Açıklama yararlılığı (5'li) risk katmanına göre; "neden?" sayısı; gizlilik anlayış kontrolü (kullanıcı neyin nerede saklandığını söyleyebiliyor mu). [MAKALE-25, 23, 24]
- **DEG-28** (Olabilir) Güven göstergeleri: "ısrar/override" oranı, durdurma düğmesi kullanımı, yanlış dokunuş sonrası toparlanma süresi. [MAKALE-03, 04]
- **DEG-29** (Zorunlu) Kullanıcı çalışması etik onaya bağlıdır; onay yoksa Yol B uygulanır ve kullanıcı çalışması sınırlılık olarak raporlanır. [ÖĞRENCİ: §6.2, §8] [HOCA]
- **DEG-30** (Zorunlu) Bir sonraki teslimde çalışan Android iskeletinden ekran görüntüsü/kısa video sunulur. [HOCA: sonraki teslim]
- **DEG-31** (Zorunlu) Gereksiz soru testi: rehberde tek eşleşme olan adla komut verildiğinde soru sorulmaz (hedef: 0 gereksiz soru); çoklu eşleşmede seçenekler adlarıyla okunur; gerçekten eksik komutta tek soru sorulur. Gereksiz soru oranı raporlanır. [ÖĞRENCİ-KARAR 17.11.2026 (3)] [ÖĞRENCİ: Tablo 2 AS1]
- **DEG-32** (Zorunlu) Konuşma tanıma seçim denemesi: Whisper tiny/base/small ile Android çevrimdışı yerleşik tanıyıcı aynı Türkçe komut kümesinde Galaxy A15'te karşılaştırılır (kelime hata oranı + saniye/ifade); sonuç NFG-75'e göre motor seçimini gerekçelendirir. Whisper hız tablosu (DEG-14) seçimden bağımsız olarak hoca için üretilir. [ÖĞRENCİ-KARAR 17.11.2026 (4)] [HOCA: Güz 2-3]
- **DEG-33** (Zorunlu) Rıza testi: rıza yokken/geri alındıktan sonra ağ izlemede buluta sıfır istek; rıza varken buluta giden isteklerde maskesiz kişisel veri sayısı sıfır; ham ses ve ekran görüntüsü hiç gitmez. [ÖĞRENCİ-KARAR 17.11.2026 (2)] [ÖĞRENCİ: Tablo 2 günlük denetimi]
- **DEG-34** (Olmalı) Galaxy A15'te bellek, ilk yanıt gecikmesi ve uzun bekleme sonrası servis sağkalımı (One UI pil kısıtı) ölçülür. [ÖĞRENCİ-KARAR 17.11.2026 (5)] (NFG-72, NFG-73)

---

## 6. Çelişkiler ve çözümleri

- **ÇEL-01 Doğrulama kodu (OTP).** Vizyondaki motivasyon örneği "bildirimi aşağı çek, kodu oku, kodu gir"; Hafta-1 revize belgesi §5.4 bu özelliği kapsamdan çıkardı, hoca onayladı (✅). Kazanan: [HOCA] + [ÖĞRENCİ: §5.4]. Etki: FG-79, NFG-67, KD-01. Vizyondaki örnek, asistanın kodu okumadan "kodu şurada göreceksin, dokun" biçiminde yönlendirdiği "Ben yapayım" akışına uyarlanır.
- **ÇEL-02 "Sen yap hızlı ve kendiliğinden" ve risk onayı.** Vizyon, asistanın her adımı kendisinin yaptığını söyler; Tablo 3 mesaj/aramayı Orta (onay bekler), para/silme/parolayı Yüksek (dur ve sor) yapar. Kazanan: Tablo 3 (hocanın "risk dereceleri yerinde" dediği belge). Etki: FG-72, FG-73, FG-47.
- **ÇEL-03 Görev kaydı otomatik mi, onaylı mı.** Vizyon: başarılı dizi kaydedilir. §4.3: yalnızca kullanıcı onayıyla. Hoca ayrıca Play uyumu için insan-onaylı tarifi öneriyor. Kazanan: [ÖĞRENCİ: §4.3] + [HOCA]. Etki: FG-42, NFG-70.
- **ÇEL-04 Hız hedefi ve kanıtlar.** Vizyon: 5-10 sn kabul edilemez, Siri gibi anlık. Makaleler tersini gösterir: eylemler arası ~20 sn [MAKALE-03], GUI ajanı 6,4-8,0 sn/tur [MAKALE-12], görev başına 3,9 dk [MAKALE-11], adım gecikmesinin %42-87'si dil modeli [MAKALE-09], o1 18-26 sn [MAKALE-16], Whisper large-v3 3,41 sn (M1) [MAKALE-20]. Kazanan: öğrencinin hedefi gereksinim olarak kalır; kanıt "teknik risk"tir. Çare: üç katmanlı karar (FG-40), sesli bekleme (FG-17), sayısal eşikler (NFG-23, TK-09). Sayısal hedef kaynaklarda yoktur.
- **ÇEL-05 Whisper karşılaştırma kümesi.** Hoca: tiny, base, small. Öğrenci (§4.1, Tablo 2): small, medium, turbo. Makale: en iyi large-v3 (WER 0,12), small 0,26 (Hollandaca, MacBook M1). Kazanan: [HOCA]; medium/turbo telefonda çalışabiliyorsa isteğe bağlı eklenir. Etki: DEG-14, TK-02.
- **ÇEL-06 Mod adları.** Vizyon: "Yap" / "Rehber". Revize belge: "Sen yap" / "Ben yapayım" (§1, §4.2). Hoca: "Sen yap". Kazanan: [HOCA] + [ÖĞRENCİ: belge]; bu belgede bu adlar kullanılır. Yaşlıya görünen etiket açık karardır (TK-13). Not: "Ben yapayım" kullanıcı bakışıyla adlandırılmıştır (dokunmayı yaşlı yapar).
- **ÇEL-07 ExplorAR bulgusu.** Belge §3.1/§4.2: "adım adım izletmek öğrenmeyi hızlandırıyor ama kalıcılığı azaltıyor". Hoca: ExplorAR kalıcılığı ölçmedi, öğrenmeden hemen sonra yardımsız tamamlamayı ölçtü (AR talimatı 34,4 sn, ExplorAR 18,3 sn). Kazanan: [HOCA]; [MAKALE-05] aynı yönde. Etki: DEG-05, KD-11. Vizyondaki "Rehber modunda kullanıcı kendisi yaptığı için daha iyi öğrenir" varsayımı da kanıtlı değil, AS2 hipotezi olarak kalır [MAKALE-05, 04].
- **ÇEL-08 Pilot kapsamı.** Hoca: Bahar 1-2 pilotu 1-2 genç yetişkinle süre ölçmek için. Tablo 6: Bahar 2'de 2-3 yaşlı pilot. Kazanan: [HOCA] süre pilotu için; yaşlı pilot (etik onaya bağlı) ayrıca sürebilir. Etki: DEG-09.
- **ÇEL-09 Netleştirme sayısı.** Öğrenci: tek kısa soru. Makale: en çok 3 takip sorusu [MAKALE-06]. Kazanan: [ÖĞRENCİ] (+ [MAKALE-21] tek soru ilkesi). Etki: FG-06.
- **ÇEL-10 Dil modeli "yalnızca eylem planı" ve netleştirme.** Şekil 2: dil modeli yalnızca eylem planı verir; fakat Şekil 1 adım 2 netleştirme/yeniden ifade ister ve makaleler bunu dil modeliyle yapar [MAKALE-06, 12]. ÇÖZÜLDÜ (17.11.2026): Şekil 2'deki "dil modeli yalnızca eylem planı" öğrencinin kendi belgesidir; öğrenci bunu "eylem planı veya netleştirme sorusu" olarak genişletmeyi onayladı. Netleştirme önce cihaz verisine bakan kurallarla, yetmezse aynı çağrıda dönen "netleştirme sorusu" alanıyla yapılır; ek tur yok. Kazanan: [ÖĞRENCİ-KARAR 17.11.2026 (3)] (öğrenci belgesini kendisi güncelledi; hoca çelişmiyor). Etki: FG-100..FG-102, NFG-24, TK-06, ÇEL-09 (tek soru sınırı korunur).
- **ÇEL-15 Play uyumlu yürütücü.** Hocanın "gelecek çalışma 1" önerisi (insan-onaylı tarif, Play uyumu) vs öğrencinin "yalnız APK/demo yeter" kararı. Hoca bunu "Zorunlu değil" diye işaretlemişti, dolayısıyla açık bir zorunlu talep ihlal edilmedi. Kazanan: [ÖĞRENCİ-KARAR 17.11.2026 (1)]; KVKK/gizlilik değişmez. Etki: NFG-70 (planlanmadı), KD-12, TK-11.
- **ÇEL-16 Konuşma tanıma motoru.** Hoca: Whisper tiny/base/small hız tablosu. Öğrenci kararı: Whisper ile yerleşik çevrimdışı tanıyıcı denemede ölçülür, biri seçilir. Hocanın tablo talebi korunur (DEG-14 Zorunlu); yerleşik tanıyıcı kazanırsa bile Whisper tablosu hazırlanır. Etki: NFG-75, DEG-32, TK-02.
- **ÇEL-11 Ekran girdisi.** AgeMate/AppAgent ekran görüntüsü+XML gönderir [MAKALE-03, 08]; öğrenci yalnızca maskeli özet ve erişilebilirlik ağacı. Kazanan: [ÖĞRENCİ] (+ [MAKALE-11] ağaç girdisi daha iyi). Etki: NFG-29, NFG-51. Simge-only düğmelerde ağaç etiketi eksik kalabilir [MAKALE-04], bkz. TK-04.
- **ÇEL-12 Vurgu sayısı.** [MAKALE-02] en çok 3 aday; [MAKALE-05] tek sonraki öge. Bağlama göre ayrıldı (FG-32). Üst kaynak yok, [ÇIKARIM].
- **ÇEL-13 İki mimari diyagram sürümü.** `mimari-diyagram.png` ile Şekil 2 (`tam-degerlendirme-gorsel-2.png`) farklı: birincisinde erişilebilirlik kutusunda "parola alanını atlar", altlıkta "risk yeniden sınıflandırması"; Şekil 2'de "kaydırır, metin yazar", "SQLite: ayarlar, günlük, tarifler", "acil durdurma", "pop-up'ta işlem durur", "yalnız eylem planı". Çelişen bir karar yok; birleşimi alındı. Hoca Şekil 2'yi doğruladı, esas odur.
- **ÇEL-17 FG-07 (her işte geri okuma + onay) ve FG-71 (Düşük risk: anlat ve yap).** Her ikisi öğrenci belgesindendir (Şekil 1 adım 2/§4.1 ve Tablo 3). Kazanan: Tablo 3 (hocanın "risk dereceleri yerinde" dediği belge; ÇEL-02 ile aynı yön): kesin eşleşen Düşük riskli işte ("Ayarları aç") onay sorulmaz, "X uygulamasını açıyorum" denir ve yapılır; Orta/Yüksek risk ve kısmi eşleşmede FG-07 aynen geçerli. SINIR (UD-1): NFG-18 ("uydurma transkript … onay olmadan eylem tetiklemez") bu kısayolla gerilir; ses tanıma ara sonucundan gelen metin onaysız eyleme dönüşmemeli (UD-1 Ö-1). Hocayla çelişki yok. Etki: FG-07, FG-71, NFG-18, SAPMALAR 17.11.2026 (G-10 satırı).
- **ÇEL-14 Kaynaklar arası sayısal not.** Belge §3.1 Sharifi için "çözümlerin %94,7'si uygulanabilir" der; not (MAKALE-06) %81,6 "evet" + %13,1 "belki" (=%94,7) verir. Çelişki değil, kapsam farkı; ölçüt/taban çizgisi yazılırken ayrıştırılmış değer kullanılmalıdır. Ayrıca MAKALE-15 notu AWM'yi arXiv 2024 olarak, belge ICML 2025 olarak anar; tarih farkı yalnızca kaynakça meselesidir. Belgenin AutoDroid "%51,7" sayısını hoca PDF'le doğruladı; MAKALE-09 notundaki dolar bazlı maliyet farkı ayrı bir ölçüttür, değişiklik gerekmez.

---

## 7. Açık teknik kararlar
Durum: KARAR VERİLDİ (17.11.2026): TK-01, TK-02 (yöntem), TK-06, TK-11, TK-15 (cihaz). Diğerleri açık; her biri için "Önerilen varsayılan" plan aşamasında toplu onaya sunulur (öğrenci onaylamadan bağlayıcı değildir).

- **TK-01 [KARAR VERİLDİ 17.11.2026 (2): melez mimari; bulut = Gemini API ücretsiz katmanı, yedek Groq ücretsiz katmanı; yalnız maskeli metin gider; ham ses/ekran görüntüsü çıkmaz; cihaz içi LLM yok (KD-13); ayrı yurt dışı rızası şart (NFG-76..NFG-80). Açık kalan: kota/veri koşulu doğrulaması DOĞ-01, vekil sunucu TK-07].** Çalışma anındaki dil modeli. Kısıtlar: sıfır bütçe (kota, hız sınırı, ücretsiz katman veri-kullanım koşulları doğrulanmalı), Claude Pro kullanılamaz, KVKK m.9 yurt dışı aktarım, maskeli metin, düşük gecikme. Belge/Şekil 2 "Gemini Flash" yazıyor; bunun kesinliği açık. Kanıt: en iyi ElderBench sonucu bulut Gemini-3-Flash %49,80 ve hiçbir model %50'yi geçmedi [12]; Qwen3-VL-Flash 3,57 sn/tur, GUI ajanları 6,4-8,0 sn [12]; AutoDroid'te cihaz içi 7B model %41,1 tamamlama/%57,7 eylem doğruluğu, bulut GPT-4 %71,3/%90,9, LLM gecikmenin %77,6-87,1'i [09]; Türkçe ölçüm yok (Çince/İngilizce) [12, 10]; akıl yürütme modeli gecikme ve sızıntı artırır [16, 18]. Seçenekler: ücretsiz bulut katmanı, cihaz içi küçük model, melez.
- **TK-02 [KARAR VERİLDİ 17.11.2026 (4): yöntem. Whisper (tiny/base/small) ile Android çevrimdışı yerleşik tanıyıcı küçük denemede ölçülüp seçilecek; ölçüt Türkçe doğruluk + Galaxy A15 gecikmesi (NFG-75, DEG-32). Sonuç denemeden sonra].** Konuşma tanıma. Kısıtlar: cihazda, buluta ses yok [Tablo 5]; çevrimiçi Android tanıyıcı çalışma sırasında yasak; hoca tiny/base/small hız tablosu istiyor. Kanıt [20] (Hollandaca, MacBook M1, geriye göre WER): large-v3 0,12 (3,41 sn), turbo 0,16 (1,43 sn), small 0,26 (1,11 sn); ince ayarlı modeller kötü (0,40-0,49); halüsinasyon güveni en çok zedeler. Telefonda süre ve Türkçe doğruluk bilinmiyor. Seçenekler: Whisper boyutları, yerleşik çevrimdışı tanıyıcı.
- **TK-03 Sesli yanıt (TTS).** Kısıtlar: Türkçe, ücretsiz, çevrimdışı tercih, yavaşlatılabilir. Kanıt: WePilot Android TTS kullandı [04]; robotik/duygusuz ses şikâyeti [22]; tek yapay ses sınırlılığı [24]. **Önerilen varsayılan:** Android'in yerleşik Türkçe TextToSpeech sesi (çevrimdışı ses paketi), hız ayarlı (%80'e kadar yavaşlatma) [04, 22].
- **TK-04 Ekran girdisi.** Kısıtlar: parola alanı okunmaz, ekran görüntüsü buluta gitmez [Şekil 2]. Kanıt: ağaç %30,6, ekran işaretleme %25,4 [11]; simge-only düğmeler hata kaynağı [04]; tam ağaç ~40k token olabilir, sıkıştırma gerekir [09]. Seçenekler: yalnız ağaç; ağaç + yerel OCR/simge betimi. **Önerilen varsayılan:** yalnız erişilebilirlik ağacı (sıkıştırılmış), ekran görüntüsü/OCR yok; etiketsiz simge düğmelerde asistan emin olmadığını söyler ve kullanıcıya sorar [11, 04].
- **TK-05 Görev belleği.** Sabit: yerel vektör dizini + SQLite [Şekil 2]. Açık: gömme modeli (Türkçe, telefona sığan, ücretsiz), tarif biçimi (adım listesi/ağaç), eşleşme eşiği. Kısıtlar: onaylı kayıt, kişisel içeriksiz, silinebilir. Kanıt: gösterimle öğrenme %84,4 [08]; kısayolla %38,02 adım tasarrufu [09]; KG-RAG %66,94→%75,8 [13]; AWM ilk 0-40 örnekte doygunluk, dinamik pop-up'ta yanılır [15]; keşif 1-8 saat/uygulama [13]; makalelerin gömme modelleri (E5, bge-large, Instructor-XL) telefon için büyük. **Önerilen varsayılan:** küçük çok dilli gömme modeli (E5-small sınıfı, kuantize, cihazda; Türkçe başarımı ve boyutu A15'te doğrulanır), tarif = parametreli JSON adım listesi, SQLite'ta; eşik başlangıçta 0,80, pilot veriyle ayarlanır [13, 15].
- **TK-06 [KARAR VERİLDİ 17.11.2026 (3): önce cihaz verisine bakan kurallar, yetmezse eylem planı çağrısındaki "netleştirme sorusu" alanı; ek tur yok; tek eşleşmede soru yok (FG-100..FG-102, DEG-31)].** Netleştirme/yeniden ifade nerede çalışır. Kısıtlar: Şekil 2'de dil modeli yalnız eylem planı; ses buluta çıkmaz; tek soru; geri okuma. Kanıt: komut normalleştirme 37→61 [12], yeniden ifade doğruluk %35→%69 [06]; ikisi de dil modeliyle. Seçenekler: kural/şablon, cihaz içi küçük model, bulutta yalnız maskeli metin.
- **TK-07 Vekil sunucu barındırma.** Kısıtlar: sıfır maliyet, anahtar telefonda yok, kimlik/oran sınırı, günlükte kişisel veri yok, veri konumu (KVKK m.9), demo süresince erişilebilirlik. Kaynaklarda öneri yok. Seçenekler: ücretsiz sunucusuz katman, üniversite sunucusu, demo için yerel bilgisayar. **Önerilen varsayılan:** kart istemeyen ücretsiz sunucusuz katmanda küçük vekil (anahtar yalnız sunucuda, cihaz başına oran sınırı, günlükte içerik yok); koşullar DOĞ-01'de doğrulanır, olmazsa demo için öğrencinin bilgisayarı [NFG-41, NFG-42; kaynakta öneri yok].
- **TK-08 Kaplama (iz/nokta) biçimi ve renk.** Vizyon kırmızı iz/nokta öneriyor, renkler "kararlaştırılmadı". Kanıt: bağlamlı vurgu ve ağırlıklı yakınlaştırma çıplak çerçeveden iyi [02]; ExplorAR'da yeşil=doğru, kırmızı=yanlış [05] (kırmızı "hata" çağrışımı yapabilir); yüksek kontrast isteği [05]; vurgu çevreyi örtebilir [02]. Kısıt: kendi pencereleri gözlem dışı [17]. **Önerilen varsayılan:** yüksek kontrastlı sarı halka + koyu kenar, kaydırma için ok izi; kırmızı "hata" için ayrılır, doğru/yanlış renk + titreşim ile ayrılır [02, 05].
- **TK-09 Gecikme bütçesi.** Sayısal hedef yok (NFG-23 önerisi onay bekler). Kanıt ÇEL-04'te. Seçenekler: adım başına eşik, ilk geri bildirim eşiği, çevrim içi/dışı ayrı hedef. **Önerilen varsayılan:** NFG-23 eşikleri (dokunma→dinleme ≤1 sn, ilk geri bildirim ≤2 sn, >5 sn'de sesli bilgi) A15 ölçümüyle revize edilir; kayıtlı tarifler çevrimdışı hızlı yol [09, 12].
- **TK-10 Risk sınıflandırıcı gösterimi.** Kısıt: dil modelinden bağımsız, kural tabanlı [Tablo 3]. Açık: uygulama/öge/sözcük kuralları, Türkçe tehlikeli sözcük listesi, onay zaman aşımı. Kanıt: ajanda risk fark etme %36 (GPT-4o) [16]; LLM tabanlı riskli eylem tespiti P %75,0/R %80,5 [09]; yüksek riskte ret oranı hiçbir ajanda %70'i geçmedi [16]. **Önerilen varsayılan:** kural tablosu = uygulama (banka engelli) + ögenin türü/etiketi + Türkçe tehlikeli sözcük listesi (sil, öde, gönder...); belirsizde bir üst risk; onay zaman aşımı 10 sn, sessizlik = hayır [Tablo 3, 04, 16].
- **TK-11 [KARAR VERİLDİ 17.11.2026 (1): tek yürütücü, yalnız APK/demo; Play-uyumlu ayrı yürütücü yok (KD-12); KVKK/gizlilik aynen zorunlu].** "Sen yap" yürütme biçimi ve Play. Kısıt: Play, dil modelinin eylemi kendi planlayıp yürütmesini yasaklıyor, insan-tanımlı sabit betiği serbest bırakıyor; şimdilik yalnız APK [§5.5, HOCA]. Seçenekler: tek yürütücü (APK), iki yürütücü (NFG-70). Kanıt: AgeMate, WePilot "tek tıkla" kipini sunuyor ama Play uyumu tartışmıyor [03, 04].
- **TK-12 Aile ayarları mekanizması.** Aynı cihazda PIN'li ekran mı, uzaktan mı? Uzaktan sunucu, kimlik doğrulama, KVKK ve maliyet getirir; uzaktan görev tarifi hocanın "gelecek" listesinde (KD-05). Kanıt: yardımcı kimliği doğrulama kaygısı [04]; aile gözetimi kaygısı [22]. **Önerilen varsayılan:** aynı cihazda PIN'li ayar ekranı; uzaktan yönetim kapsam dışı (KD-05), sunucu/maliyet/KVKK yükü yok [04, 22].
- **TK-13 Yaşlıya görünen ad/ses kimliği.** "Sen yap/Ben yapayım" ya da vizyondaki "Yap/Rehber" ya da başkası; asistanın adı, ses cinsiyeti, ton. Kanıt: tona karşı duyarlılık [24, 25]; damgalanma [23]; öneri: aday adlar kullanıcı çalışmasında anlaşılırlık için sınanır. **Önerilen varsayılan:** "Sen yap" / "Ben yapayım" (hoca ve revize belge terimi), varsayılan "Ben yapayım" (daha düşük risk); tek sıcak, sade, yetkin gösteren ton; ses cinsiyeti ayarlanabilir [HOCA, 24, 25, 23].
- **TK-14 Eylem uzayı ve öge hedefleme.** Kanıt: LLM koordinat vermede zayıf, numaralı öge kimliği çözüm [08]; AndroidWorld eylem kümesi [11]; vizyon kaydırma izi istiyor. Seçenekler: öge kimliği + yön/uzunluk (kısa/orta/uzun), koordinat yedeği. **Önerilen varsayılan:** öge kimliği + kaydırma yönü/uzunluğu (kısa/orta/uzun); koordinat yalnız yedek [08, 11].
- **TK-15 [KARAR VERİLDİ 17.11.2026 (5): birincil test cihazı Samsung Galaxy A15; her Android telefonda iyi çalışma, düşük RAM/CPU tasarımı, One UI pil kısıtı dayanıklılığı (NFG-72, NFG-73). Açık kalan: Android sürüm aralığı].** Android sürüm hedefi ve APK kurulumu. Android sürümlerine göre erişilebilirlik servisi ve kaplama izinleri değişir; APK ile yüklenen uygulamalarda erişilebilirlik izni ek adım gerektirebilir (kısıtlı ayarlar). Kaynaklarda yok; hedef cihazlarda doğrulanmalı [ÇIKARIM]. Kanıt: Android sürümleri arasında başarı farkı %28,4 vs %30,6 [11]. **Önerilen varsayılan (sürüm aralığı):** A15'in sürümü (Android 14+) hedef, alt sınır Android 10+; kısıtlı ayarlar adımı kurulum rehberinde sesli anlatılır [ÇIKARIM; cihazda doğrulanır].
- **TK-16 Dinleme sonu ve onay zaman aşımı.** "Sessizlik = hayır" için bekleme süresi ve konuşma sonu algılama. Kanıt: 1,5 sn çok kısa [02]; 9 sn/60 sn [04]; ek karar kaynaklarda yok. **Önerilen varsayılan:** onay bekleme 10 sn (sessizlik = hayır), dinlemede susma ~3-4 sn/üst sınır 60 sn; komutlar "dur", "baştan anlat", "tamam, hatırladım" sabit sözcük; A15'te ayarlanır [02, 04].
- **TK-17 Değerlendirme altyapısı.** Emülatör mü gerçek cihaz mı, STAQ benzeri Türkçe komut seti nasıl üretilecek (gerçek örneklerden, stereotipsiz, uzman doğrulamalı) [06], ElderBench tarzı çevrim dışı görev grafı [12]. Kısıt: ücretsiz araçlar. **Önerilen varsayılan:** Galaxy A15 + ücretsiz Android Studio emülatörü; ~100 komutluk Türkçe komut seti öğrenci tarafından, gerçek örneklerden, çevrim dışı kayıtlı olarak üretilir; görev grafı elle [06, 12].

---

## 8. Belirsizlikler / hocaya sorulması gerekenler

### 8.1 Hocaya (danışman) sorulacaklar
- **HS-01** Etik yol: A mı, B mi? Güz 0 penceresi (4-15 Kasım) geçti; yazılı karar notu var mı? [HOCA: Esas sorun 1]
- **HS-02** İnsan katılımcılı çalışma için bitirme kapsamında etik onay zorunlu mu, hangi kurul, takvim ne? Genç yetişkin gönüllülerle geliştirme testleri için onay gerekir mi? [ÖĞRENCİ: §6.2]
- **HS-03** Ses kaydı toplama (kelime hata oranı için) ve saklama süresi hangi koşulda onaylanır? [ÖĞRENCİ: Tablo 5] (KVKK m.9 / "üniversitenin kabul ettiği dayanak" sorusu hocaya sorulacaklardan ÇIKARILDI; yerine NFG-76..NFG-82 somut gereksinimleri var. [ÖĞRENCİ-KARAR 17.11.2026 (6)])
- ~~**HS-04**~~ KARARA BAĞLANDI: "Sen yap" yalnız APK/demo; Play-uyumlu sürüm beklenmiyor (hoca bunu zorunlu tutmamıştı). [ÖĞRENCİ-KARAR 17.11.2026 (1)]
- **HS-05** Whisper karşılaştırması: tiny/base/small yeterli mi, medium/turbo çıkarılsın mı? Birincil telefon Galaxy A15 (karar 5). [HOCA] [ÖĞRENCİ: Tablo 2]
- **HS-06** AS1 için canlı katılımcı bloğu mu, çevrim dışı kayıtlı komutlar mı? [HOCA: Esas sorun 2]
- **HS-07** Türkçe SUS sürümü için tercih edilen kaynak var mı? [HOCA: önerilen kaynaklar]
- **HS-08** Sayısal başarı eşikleri (gecikme, niyet doğruluğu, AS3 yanlış işlem oranı) için beklenti var mı? Kaynaklarda eşik yok.
- **HS-09** Ara rapor (26 Aralık-10 Ocak) ve sonraki teslimde (ekran görüntüsü/video) tarih ve biçim.

### 8.2 Öğrenciye (yalnızca öğrenci karar verebilir)
- ~~**SS-01**~~ KARARA BAĞLANDI (TK-06, FG-100). [ÖĞRENCİ-KARAR 17.11.2026 (3)]
- **SS-02** Yaşlıya görünen mod adları ve varsayılan mod hangisi (TK-13)?
- **SS-03** Gecikme için sayısal hedef ne (NFG-23, TK-09)?
- **SS-04** (KISMEN KARARA BAĞLANDI: Gemini ücretsiz + Groq yedek, rıza şartıyla) Resmî sayfalardaki kota/veri koşulları doğrulanınca hâlâ kabul edilebilir mi? (DOĞ-01)
- **SS-05** Ekran görüntüsü (görsel girdi) hiçbir koşulda buluta gitmeyecek mi (NFG-51)? Simge-only düğmeler için ne yapılacak (TK-04)?
- **SS-06** İzinli uygulama varsayılan listesi ve Türkçe test uygulamaları hangileri (WhatsApp, Telefon, Ayarlar, Galeri...)? Kişi bulma rehber izni gerektirir mi, yoksa yalnızca ekrandan mı (NFG-68)?
- **SS-07** Saklama süreleri: görev günlüğü, kayıtlı tarif, aile ayarları, denetim günlüğü (NFG-59)?
- **SS-08** Aile ayarları aynı cihazda mı, uzaktan mı (TK-12)?
- **SS-09** Galaxy A15 birincil cihaz (karar 5). İkinci marka test telefonu var mı? Yoksa ücretsiz temin yolu (NFG-33, Ek C).
- ~~**SS-10**~~ KARARA BAĞLANDI: Play yayını yok, ücret doğmaz. [ÖĞRENCİ-KARAR 17.11.2026 (1)]
- ~~**SS-11**~~ KISMEN KARARA BAĞLANDI (PLAN V-12, onay 17.11.2026): onay bekleme 10 sn, susma 3,5 sn, üst sınır 60 sn; sabit sözcükler "dur", "baştan anlat", "tamam, hatırladım", "evet/onaylıyorum", "hayır/vazgeç". KALAN (UD-1): kodda evet/hayır sözcük kümesi V-12'den geniş ("tamam", "peki", "yap", "olur", "onay" = evet); öğrenci ya V-12'ye daraltmayı ya da genişletmeyi SAPMALAR'a yazmayı seçmeli. "baştan anlat" / "tamam, hatırladım" KT-3 (G-17) işi, henüz yok. [PLAN V-12] [UD-1]
- **SS-12** Kayıtlı tarife ekleme onayı hangi cümleyle istenecek, ne kadar sık (FG-42)? Vizyonun "ilk kez yapılan görev" akışı bununla sürtünme yaratmamalı.
- ~~**SS-13**~~ KARARA BAĞLANDI (17.11.2026, PLAN §2): veri sorumlusu öğrencinin kendisi. KALAN: ad ve iletişim adresi öğrenciden G-30'da istenecek; gelene kadar aydınlatma metninde açıkça işaretli yer tutucu kalır, yer tutucu doluyken metin "tamamlandı" sayılmaz (NFG-81). [PLAN §2] [UD-1]

---

## Ek A. Hocanın açık talepleri → karşılayan gereksinimler
Belge işi = uygulama gereksinimi değil, rapor/teslim işidir.
- [ ] Etik yol kararı (A/B) yazılı not; AS'lerin altına tek cümle; Tablo 6 güncelle (belge işi) → DEG-01, DEG-29, NFG-64, HS-01
- [ ] Yol A ise protokol, onam formu, aydınlatma taslağı Güz 1 sonu; kurul tarihleri (belge işi) → NFG-56, NFG-57, NFG-61, NFG-63
- [ ] Yol B ise AS1-AS3 sistem düzeyinde yeniden yaz: AS3 yanlış işlem oranı, AS1 STAQ benzeri komut seti → DEG-02, DEG-03, DEG-06, DEG-15, DEG-16, DEG-22
- [ ] Tablo 4: dakika dakika oturum akışı, toplam ≤40 dk → DEG-08, NFG-63
- [ ] Tablo 4: AS1 için ayrı desen satırı; 2-3 eksik komut, netleştirmeli/siz veya çevrim dışı → DEG-03, FG-06, FG-07
- [ ] AS3 senaryoları kurgusal, kısa, katılımcı başına ≤2 → DEG-07
- [ ] Bahar 1-2 pilot: 1-2 genç yetişkinle süre ölçümü → DEG-09
- [ ] ExplorAR cümlesi düzeltmesi (Bölüm 3, 4.2; belge işi) → ÇEL-07, DEG-05, KD-11
- [ ] Xu vd. DOI/sayfa, Li vd. sayfa aralığı, AI beyanında Şekil 2 üretim notu (belge işi) → kapsam dışı (uygulama gereksinimi yok)
- [ ] Radford 2023, Brooke 1996, Bangor 2008 incelendi/eklendi; Türkçe SUS kaynağı → DEG-12, DEG-14, HS-07
- [ ] Whisper sürümleri telefonda hız ölçüm tablosu (Güz 2-3) → DEG-14, DEG-32, NFG-19, NFG-75, TK-02, ÇEL-05, ÇEL-16 (motor seçimi sonucundan bağımsız hazırlanır)
- [ ] Güvenlik testlerinde riskli görev seti = AS3 senaryoları (Güz 4-5) → DEG-15, DEG-16, NFG-45
- [ ] Ara rapor taslağına belgenin güncel sürümü (belge işi) → HS-09
- [ ] Sonraki teslim: etik kararı notu, güncel Tablo 4, Android iskeletinden ekran görüntüsü/video → DEG-01, DEG-08, DEG-30
- [ ] (Zorunlu değil) Play uyumlu "Sen yap" sürümü → NFG-70, TK-11, FG-42. YAPILMAYACAK (öğrenci kararı 17.11.2026 (1)); hoca zorunlu tutmadığı için çelişki yok; hocaya raporda gerekçe yazılmalı.
- [ ] (Zorunlu değil) Türkçe yaşlı komut veri kümesi → KD-06, DEG-22
- [ ] (Zorunlu değil) Whisper telefonda Türkçe başarım bağımsız bulgu → DEG-14
- [ ] (Zorunlu değil) Aile katmanı genişletmesi → KD-05, TK-12, FG-61, FG-62
- [ ] (Zorunlu değil) Yayın hedefi → kapsam dışı

## Ek D. Karar günlüğü ve doğrulama maddeleri (17.11.2026)
Kararlar: (1) TK-11 yalnız APK, KVKK aynen; (2) TK-01 melez, Gemini ücretsiz + Groq yedek, yurt dışı rıza; (3) TK-06 kural önce, tek aramalı netleştirme; (4) TK-02 Whisper/yerleşik denemeyle seçim; (5) TK-15 Galaxy A15, her Android; (6) takvim sembolik, test katılımcı maddeleri değişmedi, KVK-birimi sorusu kaldırıldı. Hoca ile çelişen karar yok.
- **DOĞ-01** (kodlamadan önce, resmî sayfalardan) Gemini API ücretsiz katmanı: günlük/dakikalık kota, gönderilen verinin eğitimde kullanımı, veri işleme bölgesi; Groq ücretsiz katmanı için aynıları; kart/ücret istemediği. Sonuç NFG-25, NFG-76, NFG-80 ve aydınlatma metnine işlenir. [ÖĞRENCİ-KARAR 17.11.2026 (2)] [PARA?]
  - DURUM (UD-1): KISMEN KAPANDI, `docs/DOGRULAMALAR.md` ile. DOĞRULANDI: ücretsiz modeller ve adları, verinin üründe kullanılabileceği/insan incelemesi, işleme yeri, Türkiye'de kullanılabilirlik, Groq verisi, Cloudflare 100.000 istek/gün, KVKK m.9 (1.9.2024'ten beri açık rıza yalnız arızi aktarımda). AÇIK (DOĞRULANAMADI): Gemini RPM/TPM/RPD rakamları (yalnız AI Studio hesabında, öğrenci iletecek); anahtar/hesap açarken kart istenmediği (Gemini, Groq, Cloudflare); Groq kesin ücretsiz rakamları; Cloudflare Workers Logs gövde içeriği; ücretsiz katmanda standart sözleşme imzalanabilirliği. ÖĞRENCİ KARARI BEKLEYEN: S-01 ("asıl güvence maskeleme, rıza ek katman"). Not: Gemini ücretsiz koşulları "kişisel bilgi göndermeyin" der; maskeli metin yine kişisel veri sayılabilir (m.3/1-b), bu yüzden KT-6 öncesi bulut hattı kapalı kalır.
- **DOĞ-02** (kodlamadan önce) Cihaz içi maskelemenin kapsamı: ad, telefon, e-posta, kimlik no, IBAN, kart, adres (NFG-50); maskeli metnin "anonim" değil kişisel veri olabileceği varsayılır.
  - DURUM (UD-1): KAPSAM YAZILDI (`docs/MASKELEME.md`, 201 örneklik korpus), kod var (G-31) ama BAĞIMSIZ KANIT YOK: sözlük korpustan üretildi, korpus başarısı geliştirme kümesi başarısıdır. Gerçek maskeleme başarısı için bağımsız sınama gerekir (bkz. `docs/denetim/UD-1.md` Bölüm 5). Maskeleme henüz uygulamaya bağlı değil; buluta giden hiçbir şey yok.
- **Analist önerisi (kaynaksız, öğrenci onayı gerekir):** KVKK m.9, 2024 değişikliğinden sonra açık rızayı yalnızca arızi (tekrarlanmayan) aktarımlar için dayanak sayıyor olabilir; asistanın sürekli bulut çağrıları bu duruma uymayabilir. Resmî metin (KVKK Kurumu/Resmî Gazete) kontrol edilmeli; sonuç NFG-76..NFG-78 tasarımını etkileyebilir. Bu, hocaya değil öğrencinin teknik/hukuki araştırmasına bırakılmıştır (karar 6).
- Aydınlatma ve rıza ekranları (NFG-77, NFG-81) için sade Türkçe metin taslağı plan görevi olarak yazılmalı; SS-13 çözülmeden tamamlanamaz.

## Ek B. [ÇIKARIM] kaynaklı gereksinimler (analist önerileri)
FG-02, FG-27, FG-39, FG-50, FG-51, FG-75; NFG-01, NFG-03, NFG-13, NFG-23, NFG-32, NFG-34, NFG-42, NFG-43, NFG-49, NFG-50 (kısmen), NFG-53, NFG-68, NFG-69, NFG-71; ÇEL-12; TK-15. Her birinin dayanağı satırında yazılıdır; öğrenci onayı olmadan bağlayıcı sayılmamalıdır.

## Ek C. Maliyet bayrakları ([PARA?])
- Dil modeli API'si: ücretsiz katman kotası/hız sınırı/veri koşulları; kota dolunca asistanın davranışı (NFG-25, TK-01). Ücretli plana geçiş yapılmaz, öğrenciye sorulur.
- Vekil sunucu barındırma (NFG-41, TK-07): yalnızca ücretsiz katman; kart bilgisi isteyen deneme/kredi kullanılmaz.
- Play Store yayını: yapılmayacak (öğrenci kararı 17.11.2026 (1)); ücret doğmaz.
- Gemini/Groq ücretsiz katmanı: kota dolunca ücretli plana geçilmez; asistan yalnız cihaz içi modda sürer (NFG-25, FG-103).
- İki markalı test telefonu (NFG-33): cihaz yoksa maliyet doğar (SS-09).
- Kullanıcı çalışması: katılımcı ulaşımı/ikram/ses ekipmanı maliyeti hocayla netleştirilmeli (Tablo 4 kanalları). [ÇIKARIM]
- Whisper/gömme/küçük model dosyaları ve çizim (draw.io) ücretsizdir; paralı çalışma anı API'si yoktur.
