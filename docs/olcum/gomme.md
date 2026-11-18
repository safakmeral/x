# G-43 Gömme modeli ölçümü (görev belleği, TK-05 / V-03)

Tarih: 17.11.2026. Gereksinimler: FG-44, NFG-72, NFG-27, NFG-74, TK-05.

## 1. Seçilen model ve çalışma zamanı

| Konu | Değer |
|---|---|
| Model | `intfloat/multilingual-e5-small` (V-03), 12 katman, 384 boyutlu vektör, ~100 dil (Türkçe dahil) |
| Dosya | `onnx/model_qint8_avx512_vnni.onnx` (yazarların yayımladığı dinamik int8 nicemleme), **118,3 MB**; sözlük `sentencepiece.bpe.model` **5,1 MB** |
| Kaynak | huggingface.co resmî depo, commit `614241f622f53c4eeff9890bdc4f31cfecc418b3` sabit; SHA-256 `dd476dd0…2d88` (model), `cfc8146a…2865` (sözlük) |
| Lisans | MIT (model), ONNX Runtime MIT. `THIRD_PARTY_NOTICES.md` |
| Çalışma zamanı | ONNX Runtime Android 1.31.1 (izin listesinde). Yerel kitaplıklar 16 KB sayfa hizalı (ELF LOAD hizası 16384, dört ABI'de denetlendi) |
| Belirteçleyici | Kotlin'de (`:core`, `XlmRobertaTokenizer`): sözlük doğrudan `sentencepiece.bpe.model`'den okunur, Unigram Viterbi. ONNX Runtime Extensions **gerekmedi** |
| Girdi | `"query: " + maskeli komut`, en çok 64 belirteç; ortalama havuzlama + L2 normalleştirme; kosinüs = iç çarpım |
| Dağıtım | APK'ya gömülmez (118 MB > V-21'in 60 MB sınırı); ilk gerektiğinde huggingface.co'dan (zaten izinli alan) indirilir, `noBackupFilesDir/gomme/`, boyut + SHA-256 tutmazsa silinir, `.part` ile sürdürülür |

Daha küçük çok dilli seçenek aranırken bakılanlar: aynı modelin diğer resmî/yaygın nicemlemeleri (Xenova `model_int8` 118 MB, `q4` 399 MB, `fp16` 235 MB) daha küçük değil; çünkü ağırlığın ~%80'i 250.002 belirteçlik sözcük tablosu. 50 MB altına inmenin tek yolu sözlüğü Türkçeye budamak (türetilmiş model, resmî kaynaktan indirilemez, APK'ya gömülür); A15 ölçümü kabul edilemez çıkarsa öğrenci onayıyla değerlendirilir (bkz. §5).

## 2. Doğruluk denetimleri (masaüstü, tekrarlanabilir)

- **Belirteç kimlikleri referansla aynı (kabul ölçütü):** `XlmRobertaReferenceTest`, 77 girdi (çift dosyasındaki tüm komutlar `query: ` önekiyle + Türkçe büyük/küçük İ/ı, emoji, birleşik karakter, tam genişlik, bilinmeyen karakter, fazla boşluk, boş metin). Referans: Hugging Face `tokenizers` 0.23.3 (`tools/gomme-referans.py`). Sonuç: **77/77 aynı**.
- **Havuzlama:** gerçek model çıktısından (`[KISI_1] ara`) Kotlin ortalama havuzlama + normalleştirme, Python sonucuyla 1e-5 içinde aynı (`EmbedderTest`).
- **int8 ↔ fp32 (470 MB) karşılaştırması:** aynı metnin iki vektörü arası kosinüs en düşük 0,994 (ort. 0,997); çift benzerliklerindeki fark en çok 0,017 (ort. 0,003). Nicemleme kaliteyi anlamlı bozmuyor.

## 3. Masaüstü ölçümü (Windows 11 dizüstü, x86-64, Python ONNX Runtime 1.32.0)

Telefon olmadığı için yalnız yön göstericidir; A15 (Helio G99) birkaç kat yavaş olabilir.

| İş parçacığı | Oturum yükleme | İlk sorgu | Sorgu medyan | p95 |
|---|---|---|---|---|
| 1 | 556 ms | 15,0 ms | 6,2 ms | 9,0 ms |
| 2 | 362 ms | 5,4 ms | 4,6 ms | 6,5 ms |
| 4 | 341 ms | 5,0 ms | 4,2 ms | 6,1 ms |

(46 farklı komut × 3 tekrar, belirteçleme dahil; makine boştayken. Derleme sürerken yapılan ikinci koşuda 2 iş parçacığı medyanı 7,5 ms.)

Bellek (masaüstü, tek oturum, 2 iş parçacığı): oturum yüklenince çalışma kümesi ~+137 MB, yükleme sırasında geçici tepe ~+210 MB; oturum kapatılınca geri verilir. Kotlin sözlük (250.002 parça, HashMap) JVM'de ayrıca yüklenir; masaüstü JVM'de yükleme 158 ms, yığın ~29 MB (`XlmRobertaReferenceTest` çıktısı, `G-43 JVM tokenizer:` satırı). Cihaz PSS'i §5'teki betikle ölçülecek.

## 4. Türkçe benzerlik denemesi ve 0,80 eşiği

Veri: `app/src/androidTest/assets/gomme/ciftler.tsv`, 45 çift (25 aynı niyet, 20 farklı niyet; çoğu ortak sözcüklü, maskeli `[KISI_1]` biçimleri dahil).

| Ölçü | Değer |
|---|---|
| Aynı niyet: en düşük / ortalama | 0,855 / 0,933 |
| Farklı niyet: en yüksek / ortalama | **0,940** / 0,913 |
| 0,80 eşiğinde doğruluk | **%55,6** (20 farklı niyetin **20'si** "aynı" sayılıyor; tümü 0,80 üstü) |
| En iyi tek eşik | 0,942, doğruluk %71 |
| Hiç yanlış kabul olmayan eşik | > 0,940 → aynı niyetlerin 12/25'i yakalanır |

Örnek yanlış kabuller: "WhatsApp'ı aç" / "WhatsApp'ı kapat" 0,940; "Bluetooth'u aç" / "kapat" 0,940; "Alarm kur" / "Alarmı sil" 0,934; "[KISI_1] ara" / "[KISI_1]'e mesaj at" 0,921. Bu model benzerlikleri dar bir aralığa (≈0,85-0,99) sıkıştırır (model kartı da bunu söyler); karşıt eylemler aynı nesneyi paylaştığı için çok yakın çıkar.

**Sonuç:** 0,80 eşiği bu model için **anlamsız** (her şeyi eşler; yanlış tarif yürütme riski). Öneri (karar ana oturum/öğrenci, `docs/SAPMALAR.md`):
1. Eşik **0,95** (güvenli taraf: yanlış tarif yerine dil modeline düşmek daha iyi; bu veride yanlış kabul 0, aynı niyetlerin 10/25'i yakalanır, en çok kazanç aynı söyleyişin tekrarında).
2. G-44'te eşiğe ek olarak **eylem sözcüğü denetimi**: tarifin eylem fiili (aç/kapat, ara/mesaj, kur/sil, oku/sil...) yeni komutla çelişirse eşleşme reddedilir. Yalnız vektöre güvenmek "aç"↔"kapat" ayrımını yapamıyor.
3. Eşik, öğrencinin ~100 komutluk setiyle (V-13) G-44/G-55'te yeniden ayarlanır.

## 5. [Cihaz] Galaxy A15 ölçümü: YAPILMADI

Telefon bağlı değildi ve bu görevde ara cihaz testi yapılmadı. Tek komutla ölçüm hazır:

```
.\tools\gomme-olcum.ps1            # 1/2/4 iş parçacığı, 3 tekrar
```

Betik: modeli indirir (gerekirse) → uygulama + test APK'sını derler/kurar → modeli `Android/data/tr.yasliasistan.app/files/gomme/`e bir kez gönderir → `EmbeddingBenchmarkTest`'i koşar → sonuçları `docs/olcum/sonuclar/gomme-<zaman>/sonuc.txt` ve `ciftler.tsv`ye çeker. Ölçülenler: model/sözlük boyutu, sözlük yükleme süresi ve Java yığını, oturum yükleme (soğuk), ilk sorgu, sorgu medyan/p95/en çok (her iş parçacığı sayısı için), süreç PSS başlangıç/tepe/kapatma sonrası, çift benzerlik özeti.

| Ölçü | A15 değeri |
|---|---|
| Boyut (model + sözlük) | 118,3 + 5,1 MB (dosya) |
| İlk yükleme | yapılmadı |
| Sorgu gecikmesi (medyan / p95) | yapılmadı |
| Tepe bellek (PSS artışı) | yapılmadı |

Kabul için kaba sınırlar (öneri): sorgu medyanı ≤ 100 ms, ilk yükleme ≤ 3 sn (oturum başında arka planda yüklenebilir), PSS artışı ≤ 250 MB. Aşılırsa: (a) model oturum başında değil yalnız kural katmanı çözemezse yüklenir, (b) sözlük budanmış Türkçe model (öğrenci onayı), (c) normalleştirilmiş niyet + boşluk sözcüksel eşleşmesi (PLAN risk tablosu).

## 6. APK boyutuna etkisi

- Model APK'da değil (indirilir): 0 MB.
- ONNX Runtime yerel kitaplığı APK'ya **sıkıştırılmadan** girer (minSdk 29, 16 KB hizalı doğrudan yükleme): dört ABI toplam **133,5 MB** (arm64-v8a 34,2; armeabi-v7a 24,2; x86 40,7; x86_64 40,8 MB). Debug APK 57 MB → **198 MB** oldu.
- Telefona inen gerçek boyut tek ABI'dir (A15: arm64, ~34 MB). Öneri (G-58, karar gerekiyor): Play'e AAB ile (ABI'ye göre bölünür) ya da yan yükleme için `splits { abi }` ile ABI başına APK. armeabi-v7a düşürülmemeli (32 bit ucuz telefonlar, NFG-72). Daha küçük seçenek: yalnız gereken operatörlerle ONNX Runtime'ı kaynaktan derlemek (ücretsiz, ~birkaç MB; ama derleme zahmetli) — şimdilik önerilmiyor.

## 7. Gizlilik

Gömme yalnız cihazda hesaplanır; metin ağa gitmez (ağ yalnız `HttpGateway` üzerinden ve yalnız model indirmesi için, `NetworkAccessTest`). Günlüğe yalnız süre yazılır (`SafeLog.event`). Ölçüm yalnız sabit test cümleleriyle yapılır.
