# Vekil sunucu (G-33)

Uygulama ile dil modelleri (Gemini, yedek Groq) arasında duran küçük Cloudflare Worker. API anahtarları **yalnız burada**, Cloudflare'in gizli değişkenlerinde durur; APK'da ve depoda yoktur.

**Yayına alma adımları (sade Türkçe, numaralı):** `docs/KURULUM-SON.md` → Bölüm 2.

## Klasör

| Dosya | İş |
|---|---|
| `src/config.js` | **Tek ayar noktası:** model zinciri, zaman aşımları, hız sınırı, boyut sınırları |
| `src/handler.js` | Sıra: yol → erişim kodu → hız sınırı → gövde denetimi → model zinciri |
| `src/chain.js` | Zincir ve yedeğe geçiş kuralları |
| `src/providers/` | Gemini ve Groq istek biçimleri |
| `src/validate.js`, `src/auth.js`, `src/rateLimit.js`, `src/http.js` | Denetim, erişim kodu, hız sınırı, yanıt biçimi |
| `wrangler.toml` | Worker ayarı; gözlemlenebilirlik ve çağrı günlükleri **kapalı** |
| `.dev.vars.example` | Yerel deneme için örnek (gerçek `.dev.vars` git'e girmez) |
| `test/` | `node --test` birim testleri (sağlayıcılar sahte, ağ yok) |

Bağımlılık yok. Test: `proxy` klasöründe `node --test` (Node 20+).

## Sözleşme (v1)

`POST /v1/generate`
Başlıklar: `Content-Type: application/json`, `Authorization: Bearer <erişim kodu>`

İstek gövdesi (yalnız bu dört alan; başka alan, resim, dosya, base64 → ret):
```json
{ "v": 1, "system": "<istem şablonu>", "user": "<maskeli komut + maskeli ekran özeti>", "schema": { "type": "object" } }
```
- `system` ≤ 8000, `user` ≤ 16000 karakter, `schema` (JSON Schema nesnesi) ≤ 8 KB, tüm gövde ≤ 32 KB.
- İstem şablonu ve yanıt şeması G-35'te uygulamada belirlenir; vekil bunları değiştirmeden taşır, yalnız sıcaklık 0, en düşük düşünme ve "yanıt bu şemaya uygun JSON olsun" ayarını kendisi ekler.

Başarılı yanıt (200):
```json
{ "v": 1, "ok": true, "result": { }, "provider": "gemini", "model": "gemini-3.5-flash-lite", "attempts": 1 }
```
`result` modelin ürettiği JSON nesnesidir (düşünce/akıl yürütme metni atılmış). Şemaya uygunluğu uygulamadaki katı ayrıştırıcı (G-35) yeniden denetler.

Hata yanıtı: `{ "v": 1, "ok": false, "error": "<KOD>", "on_device"?: true, "field"?: "...", "retry_after_s"?: n }`

| Kod | HTTP | Anlamı | Uygulama ne yapar (G-34) |
|---|---|---|---|
| `UNAUTHORIZED` | 401 | Erişim kodu yok/yanlış | Ayarlarda kodu sor |
| `RATE_LIMITED` | 429 | Dakikada 20 istek aşıldı (`Retry-After`) | Kısa bekle |
| `INVALID_REQUEST`, `UNSUPPORTED_VERSION`, `BINARY_NOT_ALLOWED` | 400 | Gövde sözleşmeye uymuyor | Programlama hatası; buluta gitme |
| `PAYLOAD_TOO_LARGE` / `UNSUPPORTED_MEDIA_TYPE` | 413 / 415 | Çok büyük / JSON değil | Aynı |
| `NOT_FOUND` / `METHOD_NOT_ALLOWED` | 404 / 405 | Yanlış adres/yöntem | Aynı |
| `QUOTA_EXHAUSTED` | 503 | Zincirdeki **tüm** modellerin ücretsiz kotası dolu | "İnternet yardımı şu an dolu" + cihaz içi kip, yeniden deneme yok |
| `PROVIDERS_UNAVAILABLE` | 503 | Sağlayıcı hatası/zaman aşımı/geçersiz JSON | Güvenli durma + cihaz içi kip |
| `UPSTREAM_REJECTED` | 502 | Sağlayıcı isteği reddetti (400/401/403…; ör. anahtar yanlış) | Cihaz içi kip |
| `SERVER_MISCONFIGURED` | 500 | Gizli değişken eksik | Cihaz içi kip |

`on_device: true` olan her kodda uygulama cihaz içi özelliklerle (kurallar + kayıtlı görevler) sürer.

## Model zinciri (V-22, `docs/DOGRULAMALAR.md` §5)

1. `gemini-3.5-flash-lite` (thinkingLevel minimal) → 2. `gemini-3.6-flash` (minimal) → 3. Groq `openai/gpt-oss-120b` (reasoning_effort low, include_reasoning false) → 4. `openai/gpt-oss-20b` → cihaz içi.
Sonrakine **yalnız** 429, 5xx, zaman aşımı, ağ hatası ya da geçersiz/boş JSON'da geçilir. Diğer 4xx zinciri durdurur (`UPSTREAM_REJECTED`). Anahtarı girilmemiş sağlayıcı atlanır. Zaman aşımı: deneme başına 6 sn, toplam 15 sn. Akış (streaming) yok: katı ayrıştırıcı yanıtın tamamını ister.

## Koruma ve gizlilik

- **Erişim kodu:** uygulamaya kurulumda girilen paylaşılan gizli (APK'ya gömülmez). Sınırı: telefondan çıkarılabilir; kod sızarsa başkası sizin ücretsiz kotanızı harcayabilir (para çıkmaz, yalnız kota biter). Önlem: kodu değiştirip yeniden `secret put` (virgülle iki kod girerek geçiş yapılabilir). Kişi/cihaz kimliği yoktur.
- **Hız sınırı:** erişim kodu başına dakikada 20 istek, Worker belleğinde kayan pencere. Cloudflare birden çok örnek çalıştırabildiği için sınır **yaklaşıktır** (yalnız kötüye kullanıma karşı). KV (ücretsiz: günde 1.000 yazma) ve Durable Objects kullanılmaz; Rate Limiting bağlamasının ücretsiz planda olduğu doğrulanamadı.
- **Günlük yok:** kodda `console` yok (test denetler); `wrangler.toml`'da gözlemlenebilirlik ve çağrı günlükleri kapalı. Sağlayıcı hata metni istemciye geçmez.
- **Ek tanımlayıcı yok:** sağlayıcıya yalnız doğrulanmış `system`, `user`, `schema` ve model ayarları gider; istemcinin IP'si, başlıkları (User-Agent vb.) iletilmez (test denetler).
- **Ücret:** ücretsiz planda günlük 100.000 istek aşılırsa Cloudflare ücret almaz, **Error 1027** döner (uygulama bunu sunucu hatası sayar → cihaz içi kip).
