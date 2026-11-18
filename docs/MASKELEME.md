# Maskeleme Belirtimi (G-03)

Sürüm 1, 17.11.2026. Kapsadığı gereksinimler: DOĞ-02, NFG-46, NFG-50, NFG-51, NFG-71, NFG-79, FG-79. Kodlayacak görevler: G-31 (komut metni), G-32 (ekran özeti ve son denetim).
Test korpusu: `core/src/test/resources/mask-corpus.json` (201 örnek). Korpusu üreten ve denetleyen betik: `tools/mask_corpus_gen.py` (`python tools/mask_corpus_gen.py core/src/test/resources/mask-corpus.json`). Korpustaki sözlükler ve ayarlar (`sozlukler`, `ayarlar`) bu belgenin **bağlayıcı parçasıdır**; G-31/G-32 aynı listeleri kullanır (ürün kodunda `main/resources` altına kopyalanabilir).

---

## 1. Amaç ve temel varsayımlar

1. **Hedef:** Buluta giden metinde kişiyi doğrudan tanımlayan veri kalmaması. Yurt dışı aktarımda dayanak açık rıza değil, kişisel verinin cihazdan hiç çıkmamasıdır (SAPMALAR, 17.11.2026; KVKK m.9 açık rızayı yalnız arızi aktarımda kabul eder).
2. **Maskeli metin yine kişisel veri sayılır (DOĞ-02).** Yer tutuculu metin bağlam taşır ("`[KISI_1]` amcama", saat, uygulama). Bu nedenle maskeli metin "anonim" diye anılmaz: TLS ile, yalnız gerekli alanlarla gider (NFG-46), denetim kopyası 7 gün saklanır (V-15), aydınlatma metninde yer alır, bulut rızası ek katman olarak kalır. Maskeleme KVKK m.9 riskini çok azaltır ama hukuki değerlendirmeyi (S-01) ortadan kaldırmaz; karar öğrencinindir.
3. **Varsayılan reddet:** Sınıfı bilinmeyen ekran içeriği maskelenir; emin olunamayan istek gönderilmez (NFG-79). Fazla maskeleme (gereksiz yer tutucu) kabul edilir, eksik maskeleme kabul edilmez.
4. **Hız:** Yalnız düzenli ifade, sözlük (hash kümesi) ve rehber dizini; model yok. Bütçe Galaxy A15'te komut başına ≤ 5 ms, 200 düğümlük ekran başına ≤ 30 ms; 2.000 kişilik rehber dizini < 1 MB, rehber değişince yeniden kurulur. G-31/G-32 JVM'de kaba bir süre testi içerir, G-54 cihazda ölçer.
5. **Eşleme tablosu** (yer tutucu ↔ gerçek değer) yalnız bellekte, görev (task) boyunca tutulur; görev bitince silinir. Diske, günlüğe, buluta, denetim kopyasına yazılmaz. Günlüğe yalnız kategori sayıları yazılabilir (`SafeLog`).

## 2. Yer tutucular

Biçim `[KATEGORI_n]`, `n` her kategoride 1'den başlar, önce komut sonra ekran düğümleri sırasıyla, soldan sağa ilk görünüşe göre verilir.

| Yer tutucu | Ne | Kaynak |
|---|---|---|
| `[KISI_n]` | Kişi adı (rehber eşleşmesi ya da bağlamdan çıkarılan ad) | §5.4, §5.5 |
| `[TEL_n]` | Telefon numarası | §5.2 |
| `[EPOSTA_n]` | E-posta (yazılı ya da sözlü) | §5.2 |
| `[TCKN_n]` | T.C. kimlik no (sağlama geçerli) | §5.2 |
| `[IBAN_n]` | TR IBAN (mod-97 geçerli) | §5.2 |
| `[KART_n]` | Kart no (Luhn geçerli) | §5.2 |
| `[ADRES_n]` | Açık adres ya da koordinat (konum) | §5.3 |
| `[SAYI_n]` | Diğer 4+ haneli rakam dizileri, tam tarih, plaka, 3+ sözcüklü yazıyla sayı, geçersiz sağlamalı numaralar | §5.1, §5.2 |
| `[METIN_n]` | Serbest metin: mesaj gövdesi, dikte içeriği, liste içeriği, kullanıcı girişi, bağlantı (URL) | §5.6, §6 |
| `[OZEL_n]` | Özel nitelikli veri terimi (KVKK m.6: sağlık, din, sendika/dernek, ceza vb.) | §5.7 |

Parola alanının içeriği yer tutucuya bile dönüşmez: boş dize olarak gider (§6).

**Ek (hal eki) kuralı:** Yer tutucudan sonra gelen ek, adın ünlü uyumunu sızdırmamak için sabit bir biçime çevrilir: belirtme `'i`, yönelme `'e`, bulunma `'de`, ayrılma `'den`, ilgi `'in`, vasıta `'le`; iyelik/küçültme gibi diğer ekler atılır. Ek listesi: `sozlukler.ekler`. Örnek: "Osman'a" → `[KISI_1]'e`, "ayşeyi" → `[KISI_1]'i`, "mehmet amcama" → `[KISI_1] amcama`.

**Tutarlılık kuralı:** Aynı değer aynı görevde hep aynı yer tutucuyu alır (komut ve ekran arasında da). Anahtar: kişi için rehber kimliği (tek eşleşmede), değilse adın küçük harfe çevrilmiş hâli; telefon için ülke kodu ve baştaki 0 atılmış 10 hane; IBAN/kart/TCKN için yalnız rakam ve harfler; diğerleri için Türkçe küçük harf + tek boşluk. Böylece dil modeli "`[KISI_1]` satırına dokun" diyebilir.

**Yer tutucu sahteciliği:** Girdideki `[` ve `]` maskelemeden önce `(` ve `)` yapılır; ekrandan ya da kullanıcıdan gelen metin sahte yer tutucu üretemez.

## 3. Ön işleme (komut ve ekran metni)

1. Unicode NFC, boşlukları tekleştirme, köşeli parantez değişimi.
2. Sözcüklere bölme boşlukla; sözcük sonundaki `. , ! ? ;` eşleştirmede yok sayılır, çıktıda korunur. Kesme işaretleri (`' ’ ‘ ``) tek biçime indirilir.
3. Eşleştirme biçimleri: Türkçe küçük harf (`I→ı`, `İ→i`) ve **katlanmış** biçim (`ş→s, ğ→g, ç→c, ö→o, ü→u, ı→i`). Konuşma tanıma çıktısında büyük harf, noktalama ve kesme işareti olmayabilir ("ayşeyi ara"); yazıyla komutta Türkçe harf olmayabilir ("sukruyu ara"). Tüm sözlük ve rehber eşleşmeleri iki biçimde de denenir.
4. **Bilinen sözcük:** `sozlukler.komut` listesinde tam olarak bulunan ya da bu listedeki en az 4 harfli bir girdiyle başlayan sözcük; akrabalık/hitap sözcüğü + ek; sayı sözcüğü; rakamla başlayan sözcük; yer tutucu. Diğerleri "bilinmeyen"dir. Bağlam kuralları (§5.5) ve niyet kipi (§5.8) bu ayrımı kullanır.

## 4. İşlem sırası ve çakışma

Her dedektör metinde aralık (span) işaretler; numaralama en sonda yapılır.

1. **Gizli bilgi kontrolü** (§5.9) → eşleşirse tüm istek ENGELLE.
2. Yazıyla sayıların rakama çevrilmesi (§5.1).
3. Yapısal desenler (§5.2), adres (§5.3).
4. Yalnız komutta: dikte içeriği (§5.6).
5. Rehber eşleşmesi (§5.4).
6. Yalnız komutta: bağlam kuralları (§5.5).
7. Özel nitelikli terimler (§5.7).
8. Yalnız komutta: uzun anlatı → niyet kipi (§5.8).
9. Çakışma: **uzun aralık kazanır**; eşitse öncelik EPOSTA > IBAN > KART > TCKN > TEL > ADRES > METIN > KISI > OZEL > SAYI. Bitişik iki OZEL aralığı birleşir.
10. Numaralama, eşleme tablosu, **son denetim** (§7).

## 5. Kategoriler ve kurallar

### 5.1 Rakam dizileri ve yazıyla sayılar
- Rakam grupları arada tek bir ayraç (`boşluk . - / ( )`, baştaki `+`) varsa tek dizi sayılır: "0599 000 00 01", "12.03.1950", "12/27". İki nokta (`14:30`) ayraç değildir, saat korunur.
- Yapısal desene (§5.2) uymayan, **4 ve daha uzun** dizi → `[SAYI_n]` (doğum yılı, müşteri no, tarih, kartın son 4 hanesi). 1-3 haneli sayılar kalır ("saat 7", "sesi 15", "500 lira", "112").
- Plaka (`\d{2} [A-ZÇĞİÖŞÜ]{1,3} \d{2,4}`) → `[SAYI_n]`.
- **Yazıyla sayılar** (`sozlukler.sayi_sozcukleri`): ardışık sayı sözcükleri açgözlü çözülür; mevcut sayıyı sürdüremeyen sözcük yeni basamak grubu başlatır, "sıfır" tek başına bir gruptur. "sıfır beş yüz doksan dokuz sıfır sıfır sıfır sıfır sıfır on sekiz" → `05990000018`. Çıkan rakamlar §5.2'ye girer. Aralık **3+ sözcükse** ya da 2 sözcükten **4+ hane** çıkıyorsa maskelenir ("üç yüz elli" → `[SAYI_1]`, "iki bin" → `[SAYI_1]`); 1-2 sözcük ve 3 haneye kadar kalır ("beş dakika", "on iki kere"). Son sayı sözcüğündeki ek (`beşe`) §2 ek kuralıyla taşınır.

### 5.2 Yapısal desenler
| Kategori | Kural |
|---|---|
| EPOSTA (yazılı) | `[\w.+-]+@[\w-]+(\.[\w-]+)+` |
| EPOSTA (sözlü) | "et/at" + alan adı sözcüğü + "nokta" + `eposta_tld` (tekrarlanabilir: "nokta com nokta tr"). Aralık "et/at"tan önce bilinen sözcüğe ya da başlangıca kadar en çok 4 sözcük sola uzar: "ayşe nokta test et example nokta com" → `[EPOSTA_1]` |
| URL | `(https?://|www\.)\S+` → `[METIN_n]` |
| IBAN | `TR` + 24 rakam (boşluklu ya da bitişik, büyük/küçük harf) ve mod-97 geçerli → `[IBAN_n]`; sağlama geçersizse `[SAYI_n]` |
| KART | 13-19 rakam ve Luhn geçerli → `[KART_n]`; geçersizse `[SAYI_n]` |
| TCKN | 11 rakam, ilk ≠ 0, iki sağlama basamağı geçerli → `[TCKN_n]`; geçersizse `[SAYI_n]` |
| TEL | Rakamlardan `0090`/`+90`/`90` (12 hanede) ve baştaki `0` atılınca kalan 10 hane 2/3/4/5 ile başlıyorsa; ya da `444` + 4 hane → `[TEL_n]` |
| Koordinat | `-?\d{1,3}\.\d{3,}, ?-?\d{1,3}\.\d{3,}` → `[ADRES_n]` |

Sağlama algoritmaları yalnız etiketi seçer; dizi her durumda maskelenir.

### 5.3 Adres
- Çapa: `sozlukler.adres.anahtarlar` (mahalle, sokak, cadde, apartman, site, köy, kısaltmaları).
- Sola: çapadan önce, bilinen sözcüğe/yer tutucuya/başlangıca kadar en çok 3 bilinmeyen sözcük ("Deneme Mahallesi", "Test Sokak").
- Sağa: sonraki çapalar, bir çapadan hemen önceki bilinmeyen sözcük ("Papatya Sokak"), numara göstergeleri (`no`, `numara`, `kat`, `daire`, `No:5`, `D:4`), rakam ve sayı sözcükleri, `/` içeren sözcük ("Kadıköy/İstanbul").
- Tüm aralık → `[ADRES_n]`; içindeki posta kodu, numara vb. aralığa dahildir.
- İl/ilçe adı tek başına maskelenmez (tek başına kimseyi belirlemez, görevde gerekebilir). Bkz. artık risk AR-8.

### 5.4 Rehber eşleşmesi (kişi adları)
- Eşleşme birimleri: kişinin **tam görünen adı** (çok sözcüklü) ve **ilk adı**. Soyadı tek başına eşleşmez (sık kullanılan sözcüklere çarpar; hitapla kullanımı §5.5 yakalar).
- Sözcük = ad, ya da ad + `sozlukler.ekler` içindeki bir ek ("ayşeyi", "elifin", "ibrahimle", "Ayşe Test'in"). Ek ünlüyle başlıyor ve ad p/ç/t/k ile bitiyorsa yumuşamış biçim de denenir ("zeynebi", "mehmede").
- En uzun eşleşme kazanır ("ali testçinin" → Ali Testçi, "ali" değil).
- İlk ad birden çok kişiye uyuyorsa ("aliyi" → Ali Test ve Ali Testçi) anahtar sözün kendisidir, eşleme değeri baş harfi büyük yazılmış söz ("Ali"); ekrandaki her kişi kendi yer tutucusunu alır. Netleştirme cihazda yapılır (G-36).
- Rehberdeki ad sözlükte de olan bir sözcükse ("Deniz", "Can") yine maskelenir: "deniz fotoğraflarını göster" → `[KISI_1] fotoğraflarını göster`; "canım" → `[KISI_1]`. Bu bilinçli **fazla maskelemedir**; kullanıcı onayı (FG-07) yanlış anlamayı yakalar.
- Rehber izni yoksa bu adım atlanır; §5.5 ve ekran kuralları (§6) yine çalışır.

### 5.5 Bağlam kuralları (yalnız komut; rehberde olmayan adlar)
1. **Hitap/akrabalık:** `hitap` sözcüğünden (hanım, bey, amca, teyze, abla…, ekli hâliyle) hemen önceki en çok 2 bilinmeyen sözcük → `[KISI_n]`, ek hitap sözcüğünde kalır: "gülten test hanıma" → `[KISI_1] hanıma`. Akrabalık sözcüğünün kendisi ("annemi", "kızıma") maskelenmez; tek başına kimseyi belirlemez ve görev için gereklidir.
2. **Kişi yuvası:** `kisi_yuvasi_tetikleyicileri` sözcüğünden (ara, görüntülü, mesaj, numarası…) hemen önceki en çok 2 bilinmeyen sözcük → `[KISI_n]`. Ek, kesme işareti varsa ondan, yoksa tetikleyiciden belirlenir (ara → `'i`, mesaj → `'e`, numarası → `'in`). Kesme işareti yoksa ad ile ek ayrılamaz: eşleme değeri ekli yüzey biçimdir ("Selin Teste") ve korpusta `ekli` listesinde işaretlenir; geri çözmede yer tutucudan sonraki ek atılır.
3. **Büyük harf (yalnız yazıyla komut):** Cümle başı olmayan, büyük harfle başlayan bilinmeyen sözcük(ler) → `[KISI_n]`; bitişikteki büyük harfli cümle başı sözcüğü de dahil edilir.

### 5.6 Dikte içeriği ve serbest metin (komut)
- "**diye**" ve ardından en çok 2 sözcük içinde dikte fiili (mesaj, yaz, at, gönder, yolla, söyle, de): "diye"den önceki sözcükler, sola doğru bir alıcı aralığına (rehber/bağlam adı), yer tutucuya ya da başlangıca kadar → `[METIN_n]`. "ayşeye yarın geliyorum diye mesaj at" → `[KISI_1]'e [METIN_1] diye mesaj at`.
- "**de ki / yaz ki / söyle ki / şunu yaz / şöyle yaz**" ya da "mesaj:/yaz:/not:" : işaretten sonrası sona kadar → `[METIN_n]`.
- İçerik aralığı içindeki ad, numara, sağlık terimi aralıkla birlikte tek `[METIN_n]` olur.

### 5.7 Özel nitelikli veri (KVKK m.6, NFG-71)
- `ozel_terimler.tek`: sözcük bu kökle başlıyorsa (kök ≥ 4 harf; daha kısaysa tam eşleşme); `ozel_terimler.obek`: çok sözcüklü öbekler ("şeker hastalığım", "kalp krizi", "kan tahlili"). Bitişik terimler tek `[OZEL_n]`; ek ayrılmaz, sözcük bütünüyle gider: "tansiyon ilacımı hatırlat" → `[OZEL_1] hatırlat`.
- Kapsam: hastalık, ilaç, tahlil, tedavi, uzmanlık dalı (kardiyoloji, psikiyatri…), gebelik, genetik; din/inanç (namaz, oruç, cami, kilise…); sendika, dernek, vakıf; ceza/sabıka.
- Bilinçli dışarıda bırakılanlar: genel kurum adları (hastane, eczane, doktor), "parmak izi" gibi ayar etiketleri. Gerekçe: görev için gerekli, tek başına kişinin sağlık durumunu göstermez. Etnik köken, siyasi görüş, cinsel hayat için güvenilir sözlük kurulamaz → AR-6.

### 5.8 Uzun anlatı → niyet kipi (güvenli varsayılan)
Komut **15 sözcükten uzunsa** (`ayarlar.niyet_esigi_sozcuk`), rehberde olmayan bir adın bağlam ipucu olmadan geçme olasılığı yükselir. Bu durumda yukarıdaki kurallardan sonra **her bilinmeyen sözcük** `[METIN_n]` olur (bitişikler birleşir); buluta yalnız bilinen komut sözcükleri, yer tutucular ve işlev sözcükleri gider. Çıktıda `kip: "NIYET"`. Örnek: "… dün akşam komşumuz gülten test geldi telefonumu kurcaladı sesi kısıldı galiba sesi açar mısın" → "… komşumuz [METIN_1] telefonumu [METIN_2] sesi [METIN_3] sesi açar mısın". Eşik ayardır; G-55 gereksiz kalite kaybını ölçer.

### 5.9 Doğrulama kodu ve gizli bilgi (FG-79) → ENGELLE
Komutta ya da herhangi bir ekran düğümünde (ARAYUZ dahil):
1. `gizli.tek_basina` öbeği ("doğrulama kodu", "tek kullanımlık", "kimseyle paylaşmayın") → rakam olmasa da **ENGELLE / DOGRULAMA_KODU**.
2. `rakamla_gizli_bilgi` (şifre, parola, pin, cvv, cvc) ile başlayan sözcük + 3+ haneli rakam dizisi (yazıyla sayılar dahil) → **ENGELLE / GIZLI_BILGI**.
3. `rakamla_dogrulama_kodu` (kod, otp) + 3+ haneli dizi → **ENGELLE / DOGRULAMA_KODU**. "posta kodu" hariç.
ENGELLE: istek (komut + ekran) hiç oluşturulmaz; asistan yalnız cihazda uyarır / yönlendirir (ÇEL-01). Rakam yoksa "Şifre" gibi ayar etiketleri engel değildir.

## 6. Ekran ögesi sınıfına göre politika (G-32)

Ham erişilebilirlik düğümünün sınıfa ayrılması G-19/G-32'nin işidir; korpus sınıfı `rol` alanında doğrudan verir.

| Rol | Ne | Politika |
|---|---|---|
| `ARAYUZ` | Uygulamanın kendi etiketi: düğme, menü, sekme, başlık, ipucu metni, Ayarlar menü başlıkları | Metin kalır, ama §5.1-5.4, §5.7, §5.9 dedektörleri çalışır: "Hesap: ayse.test@example.com" → "Hesap: [EPOSTA_1]" |
| `LISTE` | İçerik uygulamalarında (Mesajlar, WhatsApp, Telefon, Galeri, Kişiler) liste ve sohbet ögeleri, sohbet başlığı | **Bütün metin** kuralı (aşağıda) |
| `BILDIRIM` | Bildirim başlığı/gövdesi | Bütün metin kuralı |
| `OZET` | Ayarlar alt satırı (Wi-Fi adı, cihaz adı, hesap) | Bütün metin kuralı |
| `DUZENLENEBILIR` | Kullanıcının yazdığı alan içeriği | Bütün metin kuralı, ama durum sözcüğü istisnası yok |
| `PAROLA` | `isPassword` alanı | İçerik hiç gitmez: `""` |

**Bütün metin kuralı:** (1) boşsa `""`; (2) metnin tamamı bir rehber adıysa `[KISI_n]`; (3) metnin tamamını tek bir yapısal/adres aralığı kaplıyorsa o kategori (`[TEL_n]`, `[ADRES_n]`…); (4) yalnız `LISTE/BILDIRIM/OZET` için metin `durum` listesinde, saat (`hh:mm`), gün adı ya da ≤3 haneli sayıysa kalır; (5) aksi hâlde bütünüyle `[METIN_n]`. Kısmi eşleşme ("Iban TR… Ayşe Test") bile tek `[METIN_n]` olur.
Uygulama politikası: bilinmeyen uygulamada liste ögesi varsayılan olarak `LISTE`'dir (içerik). İzinli liste dışındaki uygulamaların adı ve ekranı hiç gönderilmez (NFG-71, V-14). Ekran görüntüsü hiç alınmaz (NFG-51).

## 7. Son denetim

Gidecek her dize (komut, düğümler, varsa ek alanlar) gönderimden hemen önce yeniden taranır; yer tutucular saydam kabul edilir. Aşağıdakilerden biri bulunursa **ENGELLE** (`MaskingRejected`), istek gönderilmez, neden kodu (içerik değil) yerelde sayılır. İlk eşleşen neden raporlanır:
`DOGRULAMA_KODU`, `GIZLI_BILGI` (§5.9) → `EPOSTA` → `IBAN` → `KART` → `TCKN` → `TEL` → `ADRES` → `REHBER_ADI` (rehberdeki ilk ad ya da tam ad, ekli/katlanmış hâliyle) → `OZEL_TERIM` → `YAZIYLA_SAYI` (3+ sayı sözcüğü) → `RAKAM_DIZISI` (4+ hane) → `BILINMEYEN_YER_TUTUCU` (eşleme tablosunda olmayan ya da biçimi bozuk `[...]`).
Denetim, maskeleyicideki dedektörlerin aynısını çalıştırır; bağlam kuralları ve niyet kipi denetimde yoktur.

## 8. Geri çözme (yanıt cihazda)

- Yanıttaki her `[KAT_n]` eşleme tablosundan geri çevrilir. Tabloda olmayan ya da bozuk yer tutucu (`[KISI_7]`, `[KISI]`) → yanıt **RED**, eylem üretilmez (G-35).
- Yer tutucudan sonra gelen ek (sabit biçimde ya da modelin yazdığı `'yi`, `'yle` gibi `ekler` listesindeki herhangi biri) hal ekine çevrilir, sonra gerçek değere Türkçe ünlü uyumu, kaynaştırma (y/n) ve sertleşme (fıstıkçı şahap: d→t) ile yeniden kurulur: "Osman Test" + `'den` → "Osman Test'ten"; "0599 000 00 14" + `'i` → "…14'ü" (son grup okunuşu: on dört); "…No 12" + `'e` → "…12'ye". Rakamla biten değerde son rakam grubu okunur; baştaki sıfırlı grup rakam rakam okunur.
- `ekli` işaretli değerlerde yer tutucudan sonraki ek atılır (değer eki zaten içerir).
- Sesli okumada V-23 geçerlidir: mesaj içeriği (`METIN`) ve numaralar varsayılan olarak sesli okunmaz.

## 9. Korpus biçimi (`mask-corpus.json`)

Üst düzey: `surum`, `uydurma_deger_araliklari`, `ayarlar`, `rehber` (`id`, `ad`, `telefonlar`), `sozlukler`, `ornekler`. Örnek türleri:

| `tur` | Girdi | `beklenen` |
|---|---|---|
| `komut` | `kaynak` (`ses`/`yazi`), `girdi` | `sonuc` (`GONDER`/`ENGELLE`), `kip` (`TAM`/`NIYET`), `cikti`, `esleme`, isteğe bağlı `ekli`; ya da `neden` |
| `ekran` | `paket`, `dugumler[{rol, metin}]` | `sonuc`, `dugumler` (aynı sırada çıktı), `esleme` |
| `birlesik` | komut + ekran | `cikti` + `dugumler` (ortak numaralama) |
| `geri_cozme` | `esleme`, `yanit`, `ekli` | `TAMAM` + `metin` ya da `RED` + `neden` |
| `son_denetim` | `giden[]`, `esleme` | `GONDER` ya da `ENGELLE` + `neden` |

`kategoriler` etiketleri: KISI, TEL, EPOSTA, TCKN, IBAN, KART, ADRES, SAYI, METIN, GIRIS, OZEL (plan kategorileri, her biri ≥10) ve KOD, PAROLA, YAZIYLA_SAYI, TUTARLILIK, NIYET, NEGATIF, ASIRI_MASKE, GERI_COZME, SON_DENETIM.
G-31 kabulü: `komut` örneklerinin (ve `birlesik` örneklerinin komut kısmının) %100'ü `cikti` ve `esleme` ile birebir aynı. Sızıntı testi: hiçbir `esleme` değeri çıktıda geçmez (betik bunu üretimde de denetler). G-32: `ekran`, `birlesik`, `son_denetim`. Geri çözme testleri G-31/G-35.

**Uydurma değerler:** Tüm kişiler "Test" soyadlı/ekli; telefonlar `0599 000 xx xx` (tahsis edilmemiş GSM öneki) ve `0212 000 xx xx` (0 ile başlayan abone no geçersiz); TCKN `1000000xxxx` sağlamalı test dizisi; IBAN banka kodu `00000`; kartlar `4000 0000 0000 0xxx` test BIN'i; e-posta `example.com/org/net` (RFC 2606); adresler "Deneme/Örnek/Lale/Papatya". Not: sağlaması geçerli herhangi bir 11 haneli dizinin gerçek bir TCKN'ye denk gelme olasılığı sıfır değildir; bu yüzden bilinen test dizisi seçildi ve değer hiçbir kişiyle ilişkilendirilmedi.

## 10. Artık riskler (maskelemenin garanti veremediği durumlar)

| No | Risk | Örnek | Güvenli varsayılan / azaltma |
|---|---|---|---|
| AR-1 | Rehberde olmayan, bağlam ipucu olmayan ad (konuşmada büyük harf yok) | "dün zeynep geldi sesi aç" | Kısa komutta sızabilir (tek başına ilk ad, başka tanımlayıcı yok). Uzun anlatıda niyet kipi (§5.8) bilinmeyen tüm sözcükleri maskeler. İleride (plan onayıyla) TÜİK'in yayımladığı yaygın ad listesi eklenebilir; şimdilik eklenmedi. |
| AR-2 | Konuşma tanıma adı bozar ("ayşa") | "ayşayla konuş" | Kişi yuvası ve hitap kuralları sözcüğü yine yakalar; bulanık (yakın yazım) eşleştirme fazla maskeleme riski yüzünden ilk sürümde yok. |
| AR-3 | Ekranda arayüz etiketi sanılan kullanıcı içeriği | Grup adı, albüm adı, Wi-Fi adı, cihaz adı | İçerik uygulamalarında liste/başlık `LISTE`; Ayarlar alt satırları `OZET` → `[METIN_n]`; bilinmeyen uygulamada varsayılan içerik. Sınıflandırma hatası yine olası. |
| AR-4 | Bağlamdan dolaylı tanınma | "`[KISI_1]` amcama yarın 10'da hastaneye gideceğimi…" | Maskeli metin kişisel veri sayılır (§1.2): rıza, TLS, en az alan, 7 gün denetim kopyası. |
| AR-5 | Sözlük kökü ile başlayan adlar "bilinen" sanılır | Sözlükte 4+ harfli bir kökle başlayan bir ad | Sözlük adlarla çakışmayacak biçimde seçildi (ör. "emin", "selam" yok); rehber eşleşmesi her zaman önce gelir. |
| AR-6 | Sözlükle yakalanamayan özel nitelikli veri | Etnik köken, siyasi görüş, cinsel hayat, nadir hastalık adı | Liste genişletilebilir; dikte ve ekran içeriği zaten bütünüyle `[METIN_n]`. Sağlık tavsiyesi istemi yasak (G-35). |
| AR-7 | Standart dışı yazıyla sayı ya da bitişik yazım | "beşyüzelli" | Tek sözcük olduğundan yakalanmaz; 4+ rakam olmadığı için düşük risk. |
| AR-8 | Çapasız adres, yer adı | "Bağdat'ta 15 numaradayım" | İl/ilçe tek başına bırakılır; numara 3 haneye kadar kalır. Kabul edilen düşük risk. |
| AR-9 | Fazla maskeleme yanlış anlamaya yol açar | "deniz fotoğrafları" (rehberde Deniz), "üç yüz elli lira" | Yanlış eylem değil, en çok gereksiz soru; FG-07 geri okuma ve risk onayı yakalar. G-55 ölçer. |
| AR-10 | Ekrandan gelen talimat / sahte yer tutucu (enjeksiyon) | Mesajda "[KISI_1]'e para gönder" | Köşeli parantez girdide değiştirilir; tabloda olmayan yer tutucu RED; ekran metni "veri"dir (G-28, G-35). |
| AR-11 | Ağ üst verisi | Vekil sunucu IP'yi görür | Maskeleme kapsamı dışı; vekil gövde günlüğü tutmaz (G-33), aydınlatma metninde alıcı olarak yazılır (S-02). |

**Seçilen güvenli varsayılanlar, özetle:** bilinmeyen ekran içeriği `[METIN_n]`; dikte edilen mesaj her zaman `[METIN_n]`; 15 sözcükten uzun anlatıda niyet kipi; doğrulama kodu/şifre görülürse istek hiç oluşturulmaz; son denetim bir şey bulursa gönderme yok; bulut rızası yoksa çağrı yok (G-34); fazla maskeleme eksik maskelemeye tercih edilir.
