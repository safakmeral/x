# Üçüncü taraf lisans notları

Uygulamada kullanılan ya da uygulamayla birlikte dağıtılan dış kod ve modeller. Hepsi ücretsiz ve açık lisanslıdır.

| Bileşen | Sürüm | Nerede | Lisans | Kaynak |
|---|---|---|---|---|
| whisper.cpp (ggml dahil) | v1.9.5 (`d1be6fd`) | `speech/third_party/whisper.cpp/` (kaynak, uygulamaya derlenir) | MIT, Copyright (c) 2023-2026 The ggml authors | https://github.com/ggml-org/whisper.cpp |
| Whisper model ağırlıkları (ggml biçimi: tiny/base/small) | - | Kaynak ağacında yok; ölçüm için `adb` ile, sonra G-15'e göre APK içinde ya da ilk kurulumda indirilerek | MIT, Copyright (c) 2022 OpenAI | https://huggingface.co/ggerganov/whisper.cpp (dönüştürülmüş), https://github.com/openai/whisper (asıl) |
| ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android`) | 1.31.1 | Maven Central'dan, uygulamaya bağlanır (G-43, görev belleği gömme modeli) | MIT, Copyright (c) Microsoft Corporation | https://github.com/microsoft/onnxruntime (içindeki üçüncü taraf notları: `ThirdPartyNotices.txt`) |
| multilingual-e5-small gömme modeli (int8 ONNX `model_qint8_avx512_vnni.onnx` + `sentencepiece.bpe.model`) | commit `614241f6` | Kaynak ağacında ve APK'da yok; ilk gerektiğinde huggingface.co'dan indirilir (G-43), geliştirmede `tools/gomme-model-indir.ps1` ile `models/gomme/` (git dışı) | MIT (model kartı), Liang Wang ve ark. (intfloat), Microsoft; belirteçleyici sözlüğü XLM-RoBERTa'dan (MIT) | https://huggingface.co/intfloat/multilingual-e5-small |

MIT lisansı, kodun ve modelin ücretsiz kullanımına, değiştirilmesine ve dağıtılmasına izin verir; tek koşul telif ve lisans metninin dağıtımda korunmasıdır. Lisans metni: `speech/third_party/whisper.cpp/LICENSE` (ggml de aynı lisansla bu depodadır). Model dağıtıldığında (G-15) OpenAI Whisper MIT metni de uygulamanın "Lisanslar" bölümüne eklenmelidir. Aynı biçimde ONNX Runtime ve multilingual-e5-small MIT metinleri de (G-43) "Lisanslar" bölümüne eklenmelidir.
