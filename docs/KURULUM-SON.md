# Son kurulum: anahtarlar, Cloudflare, veri sorumlusu bilgileri

Bu dosya, uygulamayı teslim etmeden önce **sizin** elle dolduracağınız bilgileri toplar. Diğer görevler (ör. G-33 vekil sunucu, G-34 erişim kodu) kendi bölümlerini buraya ekler.

## 1. Veri sorumlusu bilgileri (G-30, KVKK aydınlatma metni)

Aydınlatma metninde "Veri sorumlusu" bölümü sizin adınızı ve size ulaşılacak adresi gösterir. Bu iki bilgi **yalnız tek bir dosyada** durur:

`app/veri-sorumlusu.properties`

Şu an içinde yer tutucular var:

```
VERI_SORUMLUSU_AD=[VERI_SORUMLUSU_AD]
ILETISIM=[ILETISIM]
```

**Nasıl doldurulur:**
1. Dosyayı Android Studio'da ya da Not Defteri'nde açın (UTF-8 olarak kaydedin; Türkçe harfler sorun olmaz).
2. Köşeli parantezli değerleri silip kendi bilgilerinizi yazın. Örnek:
   ```
   VERI_SORUMLUSU_AD=Ad Soyad
   ILETISIM=ornek.adres@ornek.com
   ```
   İletişim için başvuruların gerçekten ulaşacağı bir e-posta adresi yazın.
3. Kaydedin ve uygulamayı yeniden derleyin. Başka hiçbir dosyayı değiştirmeniz gerekmez.

**Kontrol:** Bir değer boş kalırsa ya da hâlâ `[...]` biçimindeyse metin "tamamlanmamış" sayılır. Release (yayın) derlemesinde Gradle şu uyarıyı yazar:
`UYARI (G-30, KVKK): Aydınlatma metnindeki veri sorumlusu bilgisi eksik ...`
Bu uyarıyı görürseniz dosyayı doldurmadan uygulamayı kimseye vermeyin.

**Metni değiştirmek isterseniz:** Aydınlatma metninin kendisi `app/src/main/res/values/strings.xml` içinde, adı `privacy_` ile başlayan satırlardadır. Anlamını değiştirdiğinizde `PrivacyNotice.kt` içindeki sürüm numarasını bir artırın; uygulama, eski metne verilmiş izinleri geçersiz sayıp kullanıcıya yeniden sorar. (`PrivacyTextTest` testi bunu hatırlatır.)

**Önemli:** Metin, hukuki görüş değildir. Teslimden önce metni okuyup onaylamanız gerekir (G-30 kabul ölçütü).

## 2. Vekil sunucuyu yayına alma (G-33)

Vekil sunucu, telefon ile yapay zekâ (Gemini, yedek Groq) arasındaki küçük aracıdır. Anahtarlarınız **yalnız orada** saklanır; telefona ve koda hiç girmez. Kodu hazır: `proxy/` klasörü. Siz yalnız hesap açıp anahtarları koyacak ve tek komutla yayına alacaksınız.

**Altın kural:** Hiçbir adım para istemez. Kart, ödeme yöntemi, "Upgrade", "Paid", "Billing" yazan bir ekran görürseniz **durun, hiçbir şey girmeyin** ve sorun.

### 2.1 Node.js kurulumu (bilgisayarınızda yoksa)
1. Tarayıcıda **nodejs.org** adresine gidin.
2. **LTS** yazan büyük düğmeye basıp Windows kurulum dosyasını indirin (ücretsiz).
3. Dosyayı açın, her ekranda **Next**, sonda **Install** ve **Finish** deyin.
4. Kontrol: Başlat menüsünde **PowerShell**'i açın, `node --version` yazıp Enter'a basın. `v20` ya da daha büyük bir sayı görmelisiniz.

### 2.2 Gemini anahtarı (Google AI Studio)
1. **aistudio.google.com** adresine Google hesabınızla girin.
2. Sol menüde **Get API key** (API anahtarı al) → **Create API key** düğmesine basın.
3. Çıkan uzun anahtarı kopyalayıp geçici olarak güvendiğiniz bir yere (ör. kâğıda) yazın. **Kimseyle, sohbette bile paylaşmayın.**
4. Kota rakamlarını okuyun: AI Studio'da **Usage** / **Rate limits** ekranında `gemini-3.5-flash-lite` ve `gemini-3.6-flash` satırlarındaki **RPM** (dakikada istek), **TPM** (dakikada jeton) ve **RPD** (günde istek) değerlerini yazıp bana iletin (anahtarı değil, yalnız bu sayıları).
5. **Billing / faturalandırma hesabı asla bağlamayın.**

### 2.3 Groq anahtarı (yedek)
1. **console.groq.com** adresinde ücretsiz hesap açın.
2. **API Keys** → **Create API Key** ile anahtar oluşturup aynı şekilde saklayın.
3. **Settings → Data Controls** ekranında **Zero Data Retention** (sıfır veri saklama) seçeneğini **açın**. Bu, Groq'un gelen istekleri saklamamasını sağlar (ücretsizdir; hesap ayarıdır, kodla açılamaz).
4. **Settings → Limits** ekranında `openai/gpt-oss-120b` ve `openai/gpt-oss-20b` satırlarındaki sayıları bana iletin.
5. Ödeme yöntemi eklemeyin.

### 2.4 Cloudflare hesabı
1. **dash.cloudflare.com/sign-up** adresinde e-posta ve parolayla ücretsiz hesap açın; e-postanıza gelen bağlantıyla doğrulayın.
2. Kart isterse **durun** (resmî sayfada kart istenmediği açıkça yazmıyor). Plan seçimi sorulursa **Free**'yi seçin; **Workers Paid**'i asla seçmeyin.

### 2.5 Erişim kodunu belirleme
1. En az **12 karakterlik**, yalnız **harf ve rakamdan** oluşan bir kod uydurun (boşluk ve virgül olmasın). Örnek biçim: `Kx7mPq2Lw9Zt4` (bunu kullanmayın, kendinizinkini yazın).
2. Bu kodu kimseyle paylaşmayın. Uygulamaya kurulumda siz gireceksiniz.

### 2.6 Cloudflare'e giriş ve anahtarları koyma
1. PowerShell'i açın ve proje klasörünün içindeki `proxy` klasörüne geçin:
   `cd "$HOME\Desktop\muratproje\proxy"`
2. Girişi yapın: `npx wrangler login`
   - İlk seferde "Need to install the following packages: wrangler … Ok to proceed?" sorusuna `y` yazıp Enter'a basın (Cloudflare'in resmî, ücretsiz aracı indirilir).
   - Tarayıcı açılır; **Allow** (İzin ver) düğmesine basın. PowerShell'de "Successfully logged in" yazısını görün.
3. Gizli değişkenleri tek tek girin. Her komuttan sonra araç sizden değeri ister; kopyaladığınız değeri yapıştırıp Enter'a basın (ekranda görünmeyebilir, normaldir):
   - `npx wrangler secret put GEMINI_API_KEY` → Gemini anahtarınız
   - `npx wrangler secret put GROQ_API_KEY` → Groq anahtarınız
   - `npx wrangler secret put ACCESS_CODES` → 2.5'te belirlediğiniz erişim kodu
   - Worker henüz yoksa ilk komutta "create a new Worker?" diye sorabilir: `y` deyin.
4. Anahtarları **hiçbir dosyaya yazmayın**. (Yalnız yerel deneme için `.dev.vars` dosyası kullanılır; o dosya git'e hiç girmez. Normalde gerekmez.)

### 2.7 Yayına alma
1. Aynı PowerShell penceresinde: `npx wrangler deploy`
2. İlk seferde ücretsiz bir `workers.dev` alt alan adı seçmeniz istenebilir (ör. kendi uydurduğunuz kısa bir ad). Seçin.
3. Bitince ekranda şuna benzer bir adres görünür: `https://yasli-asistan-vekil.<alt-adiniz>.workers.dev`. Bu adresi not edin.

### 2.8 Günlükleri (logları) kapalı tutma (gizlilik)
Kod zaten hiçbir şey yazmaz ve ayar dosyasında günlükler kapalıdır; yine de panelden kontrol edin:
1. **dash.cloudflare.com** → sol menü **Compute (Workers)** ya da **Workers & Pages** → `yasli-asistan-vekil`.
2. **Settings** (Ayarlar) → **Observability** bölümü: **Workers Logs** ve **Traces** **Disabled** (kapalı) olmalı. Açıksa kapatın.
3. `npx wrangler tail` komutunu **kullanmayın** (canlı istek izleme).

### 2.9 Deneme isteği (kabul adımı)
PowerShell'de aşağıdakini çalıştırın; `ADRES` yerine 2.7'deki adresi, `KOD` yerine erişim kodunuzu yazın:
```
curl.exe -s -X POST "ADRES/v1/generate" -H "content-type: application/json" -H "authorization: Bearer KOD" -d '{\"v\":1,\"system\":\"Kisa JSON cevap ver.\",\"user\":\"Merhaba de.\",\"schema\":{\"type\":\"object\",\"properties\":{\"geri_okuma\":{\"type\":\"string\"}},\"required\":[\"geri_okuma\"]}}'
```
- Beklenen: `{"v":1,"ok":true,"result":{"geri_okuma":"..."},"provider":"gemini",...}`
- `UNAUTHORIZED`: erişim kodu yanlış yazıldı.
- `UPSTREAM_REJECTED`: büyük olasılıkla bir API anahtarı yanlış; 2.6'daki ilgili `secret put` komutunu yeniden çalıştırın.
- `SERVER_MISCONFIGURED`: bir gizli değişken eksik.
- `QUOTA_EXHAUSTED`: bugünkü ücretsiz kota dolmuş; ertesi gün tekrar deneyin.

### 2.10 Uygulamaya bağlama (G-34)
Adres ve kod **APK'ya gömülmez**; telefonda bir kez elle girilir. Girilmezse internet yardımı tamamen kapalıdır: asistan yalnız telefonun içinde çalışır ve buluta gerekecek bir istekte "internet yardımı bu telefonda henüz kurulmadı" der.
1. Telefonda uygulamayı açın. Ana ekranda aşağı kaydırıp **İnternet yardımı bağlantısı** düğmesine dokunun.
2. **Sunucu adresi** alanına 2.7'deki adresi yazın: `https://yasli-asistan-vekil.<alt-adiniz>.workers.dev` (sonuna `/v1/generate` eklemeyin; uygulama kendisi ekler). Yalnız `https://` ile başlayan ve `.workers.dev` ile biten adres kabul edilir; başka alan adı güvenlik gereği girilemez.
3. **Erişim kodu** alanına 2.5'teki kodu yazın (yazarken nokta olarak görünür, kaydettikten sonra bir daha gösterilmez).
4. **Kaydet**'e basın. "Kaydedildi" yazısını görün; ekranın üstünde "Bağlı sunucu: …" çıkar.
5. İnternet yardımının çalışması için ayrıca **Gizlilik ve izinler** ekranında "bulut/yurt dışı aktarım" iznini vermiş olmalısınız (G-30). İzin yoksa hiçbir istek gönderilmez.
6. Deneme: baloncuğa dokunup kuralların bilmediği bir şey isteyin. İstek giderken baloncukta **İnternet** yazar.
   - "Erişim kodu kabul edilmedi" duyarsanız kod yanlış yazılmıştır; 1-4'ü tekrarlayın.
   - "İnternete şu an ulaşamadım" duyarsanız telefonun internetini ve 2.9'daki denemeyi kontrol edin.
- **Kaldırmak için:** aynı ekranda **Bağlantıyı kaldır**. Kod ve adres telefondan silinir, internet yardımı kapanır.
- **Gizlilik:** adres ve kod yalnız uygulamanın kendi alanında durur, yedeklemeye/telefon taşımaya girmez. Buluta giden her isteğin **maskeli** kopyası telefonda 7 gün tutulur, sonra kendiliğinden silinir (denetim kopyası, FG-91).

### 2.11 Ücret ve sınırlar hakkında bilmeniz gerekenler
- Cloudflare ücretsiz planı günde 100.000 isteğe izin verir; aşılırsa **ücret çıkmaz**, sunucu o gün **Error 1027** döner ve uygulama cihaz içi özelliklerle devam eder.
- Gemini ve Groq kotası dolarsa da ücret çıkmaz (fatura hesabı/ödeme yöntemi bağlamadığınız sürece); uygulama "İnternet yardımı şu an dolu" der.
- **Erişim kodunun sınırı:** Kod telefonda saklanır; çok uğraşan biri telefondan çıkarabilir. Sızarsa başkası sizin ücretsiz kotanızı tüketebilir (para değil, yalnız kota). Şüphelenirseniz: yeni kod belirleyin, `npx wrangler secret put ACCESS_CODES` ile girin, uygulamadaki kodu da değiştirin. Geçiş sırasında iki kodu virgülle birlikte girebilirsiniz (`EskiKod...,YeniKod...`), sonra eskisini kaldırın.
- Sunucu, dakikada 20'den fazla isteği kısa süre reddeder (yalnız kötüye kullanıma karşı; normal konuşma bu sayıya ulaşmaz).
- Model adlarını değiştirmek gerekirse yalnız `proxy/src/config.js` değişir, sonra `npx wrangler deploy`.
