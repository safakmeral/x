# Bulut ve Hukuk Doğrulamaları (G-02, DOĞ-01)

Erişim tarihi (tüm kaynaklar): **17.11.2026**. Yalnız resmî sayfalar kullanıldı; üçüncü taraf blog rakamı yok.
İşaretler: `[DOĞRULANDI]` resmî sayfada açıkça yazıyor · `[DOĞRULANAMADI]` resmî sayfada yok ya da yalnız hesaba girişte görünüyor · `[PARA?]` para doğurabilecek koşul.
Alıntılar sayfadaki özgün dilindedir (İngilizce ya da Türkçe).

---

## 1. Google Gemini API (birincil sağlayıcı)

Not: `ai.google.dev/gemini-api/docs/*` adresleri artık `aistudio.google.com/docs/*` adresine kalıcı yönlendiriliyor (301).

### 1.1 Ücretsiz katmandaki metin modelleri `[DOĞRULANDI]`
Kaynak: https://aistudio.google.com/docs/pricing , https://aistudio.google.com/docs/models

| Model kodu | Durum | Ücretsiz katman | Düşünme (thinking) | Not |
|---|---|---|---|---|
| `gemini-3.5-flash-lite` | Stable | Var | Varsayılan "On (minimal)"; düzeyler minimal/low/medium/high | "Our fastest, most cost-effective 3.5 model for high-throughput execution." |
| `gemini-3.6-flash` | Stable | Var | Varsayılan "On (medium)"; **minimal** seçilebilir | "balancing speed and multimodal capabilities across general agentic and everyday tasks" |
| `gemini-3.8-flash` | Stable | Var | Varsayılan medium; **minimal yok** (en düşük low) | Ajan/karmaşık iş odaklı; bizim için gereksiz yavaş |
| `gemini-3.1-flash-lite` | Stable | Var | Belgede yok | Eski nesil |
| `gemini-4-hydrogen` | Belirtilmemiş | Var | Varsayılan high; **minimal yok** | Kodlama/ajan modeli; düşünme kapatılamıyor |
| `gemini-2.5-flash` | **Deprecated** | Var | — | 1.12.2026'da `gemini-3.5-flash-lite`'a yönlendirilecek: **kullanılmaz** |
| `gemini-3-flash-preview` | Preview | Var | — | Önizleme: **kullanılmaz** |

Düşünme kaynağı: https://aistudio.google.com/docs/thinking. Alıntı: minimal = "Near-off thinking, lowest latency"; "Not supported on Gemini 4 models or `gemini-3.8-flash`." Tam kapatma anahtarı belgede yok; en yakını `minimal` (NFG-28 için yeterli).
"latest" takma adları (ör. `gemini-flash-latest`) 2 hafta haberle değişebiliyor: vekilde **sürümlü ad** sabitlenir.

### 1.2 Kota (dakika/gün) `[DOĞRULANAMADI]`
Kaynak: https://aistudio.google.com/docs/rate-limits
- Sayfa model başına ücretsiz kota tablosu vermiyor: "Rate limits depend on a variety of factors (such as your usage tier) and can be viewed in Google AI Studio." Yani rakam **yalnız hesaba girişte** görünür. **Öğrenci okuyup iletmeli** (Bölüm 6).
- `[DOĞRULANDI]` "Requests per day (RPD) quotas reset at midnight Pacific time." (Türkiye saatiyle 10:00/11:00 civarı sıfırlanır.)
- `[DOĞRULANDI]` "Rate limits are applied per project, not per API key." Ayrı anahtar açmak kotayı artırmaz.
- `[DOĞRULANDI]` Kota model başına ayrı tutulduğundan farklı Gemini modellerini zincirlemek toplam ücretsiz kapasiteyi artırır (sayfa sınırları model satırlarıyla veriyor; tek örnek: Gemini Decision 1 için 200 RPM / 10.000 RPD).
- `[DOĞRULANDI]` Ücretsiz katman koşulu: "Active project"; "some projects need verification" (doğrulamanın ne istediği yazmıyor: `[DOĞRULANAMADI]` `[PARA?]`, kart isterse durulur).
- `[DOĞRULANDI]` `[PARA?]` Tier 1 için "Set up and link an active billing account." Faturalandırma **bağlanmazsa** ücretli katmana geçilmez. Fatura hesabı asla bağlanmayacak.

### 1.3 Kart isteği `[DOĞRULANAMADI]`
Fiyat sayfası ücretsiz katman için kart/ödeme sözü etmiyor; yalnız Tier 1'in fatura hesabı istediği yazıyor. Kart istenmediği resmî metinde açıkça yazmıyor. Öğrenci anahtar alırken kart ekranı görürse durur.

### 1.4 Veri kullanımı ve işleme yeri `[DOĞRULANDI]`
Kaynak: https://aistudio.google.com/docs/terms ("Effective March 23, 2026"), https://aistudio.google.com/docs/pricing
- Ücretsiz (Unpaid) katman: "Used to improve our products: Yes" (tüm ücretsiz modellerde).
- "Google uses the content you submit to the Services and any generated responses to provide, improve, and develop" …
- **İnsan inceleyici:** "human reviewers may read, annotate, and process your API input and output."
- **Kritik:** "Do not submit sensitive, confidential, or personal information to the Unpaid Services." Bu, projenin "buluta kişisel veri gitmez" (cihaz içi maskeleme) kararını **sağlayıcı koşulu olarak da zorunlu** kılar.
- İşleme yeri: "This data may be stored transiently or cached in any country in which Google or its agents maintain facilities." Türkiye'de/AEA'da olduğu güvencesi yok.
- Ücretli katmanda eğitim yok ("Google doesn't use your prompts … or responses to improve our products"), ama ücretli olduğu için kapsam dışı `[PARA?]`.
- Yaş: "You must be 18 years of age or older to use the APIs." (API'yi kullanan öğrenci; sorun yok.)
- AEA/BK/İsviçre: "You may use only Paid Services when making API Clients available to users in the European Economic Area" (BK ve İsviçre de aynı cümlede). **Türkiye AEA'da değil**, bu kısıt bizi bağlamaz; ama uygulama AEA'daki kullanıcılara dağıtılamaz (yalnız Türkiye'de dağıtım).

### 1.5 Türkiye'de kullanılabilirlik `[DOĞRULANDI]`
Kaynak: https://aistudio.google.com/docs/available-regions. "The Gemini API and Google AI Studio are available in the following countries and territories" listesinde "Türkiye" var.

### 1.6 Türkçe kalitesi `[DOĞRULANAMADI]`
Resmî sayfalarda model başına Türkçe başarımı yok. Seçim G-33/G-55'te kendi Türkçe komut setimizle ölçülerek kesinleşir (aşağıdaki öneri bir başlangıç noktasıdır).

---

## 2. Groq (yedek sağlayıcı)

### 2.1 Modeller `[DOĞRULANDI]`
Kaynak: https://console.groq.com/docs/models
- Production: `llama-3.1-8b-instant`, `llama-3.3-70b-versatile`, `openai/gpt-oss-120b`, `openai/gpt-oss-20b`.
- Preview: `qwen/qwen3.8-27b`, `minimaxai/minimax-m2.7`, `meta-models/muse-spark-open`, `openai/gpt-oss-safeguard-20b`. Önizleme: "should not be used in production environments as they may be discontinued at short notice" → **kullanılmaz**.
- Akıl yürütme (https://console.groq.com/docs/reasoning): gpt-oss için `reasoning_effort` = low/medium/high; low = "The model will use a small number of reasoning tokens." Tam kapatma yok; `include_reasoning: false` yalnız yanıttan çıkarır.

### 2.2 Ücretsiz kota (kısmen doğrulandı)
Kaynak: https://console.groq.com/docs/rate-limits (sayfa kaynağında varsayılan seçili sekme "Free Plan Limits").

| Model | RPM | RPD | TPM | TPD |
|---|---|---|---|---|
| `openai/gpt-oss-120b` | 30 | 1K | 8K | 200K |
| `openai/gpt-oss-20b` | 30 | 1K | 8K | 200K |
| `qwen/qwen3.8-27b` (preview) | 30 | 1K | 8K | 200K |

- Uyarı: aynı sayfada "the limits shown below are the base limits for the Developer plan" cümlesi de var; sayfa kendi içinde çelişkili. Ayrıca "there may be exceptions … You can view the current, exact rate limits for your organization on the limits page". Kesin rakam **hesapta** (Limits sayfası) `[DOĞRULANAMADI]`; öğrenci okuyup iletmeli.
- `llama-3.3-70b-versatile` ve `llama-3.1-8b-instant` tabloda yok; ücretsiz planda olup olmadıkları `[DOĞRULANAMADI]`.
- `[DOĞRULANDI]` "Rate limits apply at the organization level, not individual users." Aşılınca "`429 Too Many Requests`", `retry-after` başlığıyla.

### 2.3 Kart `[DOĞRULANDI kısmen]` `[PARA?]`
Kaynak: https://console.groq.com/docs/billing-faqs. "To upgrade from the Free tier to the Developer tier, you'll need to provide a valid payment method." Ücretsiz katmanın kart istemediği açıkça yazmıyor `[DOĞRULANAMADI]`; ödeme yöntemi eklenmezse ücretli plana geçilmez. Ödeme yöntemi asla eklenmeyecek.

### 2.4 Veri `[DOĞRULANDI]`
- https://console.groq.com/docs/your-data: "By default, Groq does not retain customer data for inference requests." Sorun giderme/kötüye kullanım için günlük: "These logs are retained for up to 30 days, unless legally required to retain longer." **Zero Data Retention:** "All customers may enable Zero Data Retention (ZDR)" (Data Controls ayarı, ücretsiz). Saklanan veri: "All customer data is retained in Google Cloud Platform (GCP) buckets located in the United States."
- https://console.groq.com/docs/legal/services-agreement ("Last Modified: June 22, 2026"): §4.2 "Groq is not permitted to use Inputs or Outputs for training or fine-tuning any AI Model Services" (izin verilmedikçe). §2 "You must be 18 years of age or older". §6.2 müşteri "has obtained all necessary consents and provided all required notices".
- İşleme (hesaplama) bölgesi: `[DOĞRULANAMADI]` (DPA'ya atıf var, metin sayfada yok). Aydınlatmada "ABD başta olmak üzere yurt dışı" denir.

---

## 3. Cloudflare Workers (vekil sunucu)

Kaynaklar: https://developers.cloudflare.com/workers/platform/limits/ , https://developers.cloudflare.com/workers/platform/pricing/

| Kalem | Ücretsiz plan | Durum |
|---|---|---|
| İstek | "daily request limit of 100,000 requests, resetting at midnight UTC" | DOĞRULANDI |
| CPU | 10 ms / istek (ağ beklemesi CPU'ya sayılmaz) | DOĞRULANDI |
| Süre | "There is no hard limit on duration for HTTP-triggered Workers"; alt istekte süre sınırı yok | DOĞRULANDI |
| Alt istek | 50 / çağrı; aynı anda en çok 6 bekleyen bağlantı | DOĞRULANDI |
| Gizli değişken | 64 / Worker (gizli + düz), her biri ≤ 5 KB; "encrypted text values", ayarlandıktan sonra gösterilmez, `npx wrangler secret put <KEY>` (https://developers.cloudflare.com/workers/configuration/secrets/) | DOĞRULANDI (ücretsiz planda kullanılabildiği açıkça yazmıyor, sınır tablosunda Free sütunu var) |
| KV | okuma 100.000/gün, yazma 1.000/gün, silme 1.000/gün, listeleme 1.000/gün, 1 GB; aşılınca "further operations of that type will fail with an error" | DOĞRULANDI |
| Aşım | Günlük istek aşımında **Error 1027** (ücret değil) | DOĞRULANDI |
| Ücretli plan | Workers Paid "$5 monthly minimum" | `[PARA?]` seçilmeyecek |
| Kart | Hesap açma sayfası yalnız e-posta + parola + e-posta doğrulaması anlatıyor; kart sözü yok, "kart istemez" de demiyor (https://developers.cloudflare.com/fundamentals/account/create-account/) | `[DOĞRULANAMADI]` |
| Rate Limiting binding | Var; süre "Must be either 10 or 60"; "apply separately in each Cloudflare location", "eventually consistent" (https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/). Ücretsiz planda olup olmadığı yazmıyor | `[DOĞRULANAMADI]` |
| Günlük (Workers Logs) | Yeni Worker'larda **varsayılan açık**; her çağrı günlüğü "details such as the Request, Response, and related metadata"; gövde içerip içermediği yazmıyor; ücretsizde 3 gün saklama, 200.000 olay/gün; `[observability.logs] invocation_logs = false` ile kapatılır (https://developers.cloudflare.com/workers/observability/logs/workers-logs/) | DOĞRULANDI (gövde kısmı DOĞRULANAMADI) |

Sonuç: Vekil için ücretsiz plan fazlasıyla yeter (günde 300 istek, 100.000 sınırının %0,3'ü). 10 ms CPU sınırı nedeniyle vekil ağır iş yapmaz: yalnız erişim kodu kontrolü, hız sınırı, isteği iletme, yanıtı geçirme.

---

## 4. KVKK m.9 (yurt dışına aktarım) `[DOĞRULANDI]`

Kaynak: 6698 sayılı Kanun güncel metni, https://www.mevzuat.gov.tr/mevzuatmetin/1.5.6698.pdf ; İngilizce çeviri https://www.kvkk.gov.tr/Icerik/6649/Personal-Data-Protection-Law ; Yönetmelik, Resmî Gazete 10.7.2024 Sayı 32598, https://www.resmigazete.gov.tr/eskiler/2024/07/20240710-2.htm

- m.9 "(Değişik:2/3/2024-7499/34 md.)". Geçici m.3: eski birinci fıkra "1/9/2024 tarihine kadar uygulanmaya devam olunur." → ön bulgu doğru: **1.9.2024'ten beri** eski "açık rıza ile aktarım" yolu genel yol değil.
- m.9/1: aktarım, m.5/6 şartı + **yeterlilik kararı** ile. m.9/4: yeterlilik yoksa **uygun güvenceler** (bağlayıcı şirket kuralları, Kurul'un ilan ettiği **standart sözleşme** vb.).
- m.9/6: "yeterlilik kararının bulunmaması ve dördüncü fıkrada öngörülen uygun güvencelerden herhangi birinin sağlanamaması durumunda, **arızi olmak kaydıyla** sadece aşağıdaki hallerden birinin varlığı halinde yurt dışına kişisel veri aktarabilir: a) İlgili kişinin, muhtemel riskler hakkında bilgilendirilmesi kaydıyla, aktarıma açık rıza vermesi."
- Yönetmelik m.16/1: "Düzenli olmayan, tek veya birkaç sefer gerçekleşen, süreklilik arz etmeyen ve olağan faaliyet akışı içinde bulunmayan aktarımlar arızi niteliktedir."
- m.3/1-a: "Açık rıza: Belirli bir konuya ilişkin, bilgilendirilmeye dayanan ve özgür iradeyle açıklanan rızayı"; m.3/1-b anonim hâle getirme: "başka verilerle eşleştirilerek dahi hiçbir surette kimliği belirli veya belirlenebilir bir gerçek kişiyle ilişkilendirilemeyecek hâle getirilmesi".

**Değerlendirme (karar değil, S-01 öğrencinindir):** Asistanın her komutta buluta gitmesi "olağan faaliyet akışı içinde" ve süreklidir; bu yüzden m.9/6-a'daki açık rıza, kişisel veri aktarımı için **tek başına dayanak olamaz**. Ücretsiz katmanda Google/Groq ile standart sözleşme imzalamak (m.9/4-c) da bireysel geliştirici için mümkün görünmüyor `[DOĞRULANAMADI]`. Bu, SAPMALAR'daki 17.11.2026 kararını (asıl güvence: buluta kişisel veri gitmemesi; rıza ek katman) resmî metinle **doğruluyor**. Dikkat: maskeli metin yer tutucu eşlemesi cihazda kalsa bile hukuken "anonim" sayılmayabilir (m.3/1-b ölçütü çok sıkı); bu yüzden maskeleme son denetimi sıkı tutulmalı ve rıza katmanı korunmalıdır. Gemini koşulları da ("Do not submit … personal information to the Unpaid Services") aynı yönde.

---

## 5. Gerçekçi kullanımda kota hesabı ve model önerisi

Varsayım: tek kullanıcı, günde 100-300 dil modeli isteği (kural ve görev belleği önce denendiği için gerçek sayı daha düşük olmalı), istek başına ~1.500-3.000 jeton (maskeli komut + sıkıştırılmış ekran özeti + kısa yanıt).

- **Groq yedeği:** RPD 1.000 yeter, ama **TPD 200K bağlayıcı:** 200.000 / 2.000 ≈ **100 istek/gün**, 3.000 jetonla ≈ 65. TPM 8K: dakikada ~3-4 istek. Yani Groq tek başına 300/gün taşımaz; yedek olarak uygundur. Ekran özetini kısa tutmak (jeton bütçesi) Groq kapasitesini doğrudan artırır.
- **Gemini:** kota rakamı doğrulanamadı (yalnız AI Studio'da). Kota model başına olduğundan iki Gemini modeli zincirlenirse kapasite toplanır. Öğrenci rakamları iletince bu bölüm güncellenir.
- **Cloudflare:** 100.000/gün; sorun yok.

**Önerilen birincil model:** `gemini-3.5-flash-lite`, `thinking_level: minimal`, sıcaklık 0. Gerekçe: Stable; en hızlı ("fastest"); varsayılanı zaten minimal düşünme (NFG-28, öncelik ilkesi "az bekletme"). Bizim işimiz kısa JSON eylem planı/tek soru; büyük model gereksiz.
**Kalite yükseltmesi (aynı sağlayıcı):** `gemini-3.6-flash`, `thinking_level: minimal`. Flash-Lite G-55 Türkçe ölçümünde yetersiz kalırsa birincil bu olur.

**Yedek zinciri (vekilde, sırayla; yalnız 429/5xx/zaman aşımı ya da geçersiz JSON'da bir sonrakine geçer):**
1. `gemini-3.5-flash-lite` (minimal)
2. `gemini-3.6-flash` (minimal) — ayrı model kotası
3. Groq `openai/gpt-oss-120b` (`reasoning_effort: low`, `include_reasoning: false`)
4. Groq `openai/gpt-oss-20b` (aynı ayar) — tabloda kendi kota satırı var
5. Cihaz içi (kurallar + görev belleği) ve dürüst sesli bilgilendirme (FG-103, V-19)

Kullanılmayacaklar: `gemini-2.5-*` (kullanımdan kalkıyor), `*-preview`, `*-latest` takma adları, `gemini-3.8-flash` ve `gemini-4-hydrogen` (düşünme minimal'e inmiyor → gecikme), Groq önizleme modelleri.

**Vekil hız sınırı (V-19):** dakikada 20 istek/erişim kodu korunabilir (Groq 30 RPM'in altında; insan kullanımını kesmez). Rate Limiting binding ücretsiz planda yoksa ya da çalışmazsa Worker içinde bellek içi yaklaşık sayaç kullanılır (KV kullanılmaz: günlük 1.000 yazma sınırı).

---

## 6. Doğrulanamayanlar (özet)
1. Gemini ücretsiz katman model başına RPM/TPM/RPD rakamları (yalnız AI Studio hesabında).
2. Gemini "some projects need verification" adımının ne istediği; anahtar alırken kart istenmediği.
3. Groq ücretsiz plan kesin rakamları (sayfa çelişkili; hesapta Limits sayfası); llama modellerinin ücretsiz plandaki durumu; ücretsiz planda kart istenmediği; hesaplama bölgesi.
4. Cloudflare hesap açmada kart istenmediği; Rate Limiting binding'in ücretsiz planda olup olmadığı; Workers Logs'un gövdeyi içerip içermediği (önlem: kapatılır).
5. Resmî sayfalarda Türkçe kalite karşılaştırması (G-55'te ölçülür).
6. Ücretsiz katmanda sağlayıcılarla KVKK standart sözleşmesi imzalanabilirliği.

## 7. Öğrencinin elle yapacakları (hesaba girişte, ücretsiz)
1. AI Studio → API anahtarı alındıktan sonra **Rate limits / Usage** ekranında `gemini-3.5-flash-lite` ve `gemini-3.6-flash` için RPM, TPM, RPD değerlerini yazıp iletin. Kart ya da "doğrulama" için ödeme ekranı çıkarsa durun.
2. Groq konsolu → **Settings → Limits** ekranında `openai/gpt-oss-120b` ve `openai/gpt-oss-20b` değerlerini iletin. **Settings → Data Controls**'ta **Zero Data Retention**'ı açın (ücretsiz). Ödeme yöntemi eklemeyin.
3. Cloudflare hesabını açarken kart istenirse durun. Hiçbir zaman "Workers Paid" seçmeyin.
4. S-01 kararı: Bölüm 4'teki resmî metne göre "asıl güvence maskeleme, rıza ek katman" kararını onaylayın.

## 8. Plana etkiler
- **V-04:** Cloudflare ücretsiz plan uygun (kart koşulu hesap açarken görülecek). `wrangler.toml`'da `[observability.logs] invocation_logs = false`; kod `console.log` ile gövde yazmaz.
- **V-19:** 20 istek/dk korunur; Groq TPD (≈65-100 istek/gün) nedeniyle Gemini zinciri öne konur.
- **V-22:** model adları ve zincir Bölüm 5'teki gibi.
- **Aydınlatma metni (G-30):** Gemini ücretsiz katmanında verinin ürün geliştirmede kullanılabileceği **ve insan inceleyicilerin okuyabileceği**, verinin Google tesislerinin bulunduğu herhangi bir ülkede tutulabileceği; Groq'ta verinin ABD'de (GCP) tutulabileceği, eğitimde kullanılmadığı; her iki alıcı adıyla. Kişisel verinin maskelenerek gönderilmediği açıkça söylenir.
