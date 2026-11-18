# Geliştirme ortamı kurulumu (G-01)

Hepsi ücretsizdir. Hiçbir adım para ya da deneme sürümü gerektirmez.

## Hızlı kontrol

PowerShell'de proje klasöründe:

```
powershell -ExecutionPolicy Bypass -File tools\ortam-kontrol.ps1 -CihazBilgisi
```

Betik yalnız **okur**; bilgisayarda ya da telefonda hiçbir şeyi kurmaz, silmez, değiştirmez. Her kalem için `TAMAM` ya da `EKSİK` yazar, eksikse ne yapılacağını söyler. `-CihazBilgisi` verilirse bağlı telefondan Android sürümü, RAM, konuşma tanıma ve sesli okuma (TTS) servislerini okur. Telefondaki diğer uygulamaların listesi **yazdırılmaz** (kişisel veri).

Betiğin kendi testleri (Windows'ta hazır gelen Pester ile):

```
powershell -ExecutionPolicy Bypass -Command "Invoke-Pester tools\ortam-kontrol.Tests.ps1"
```

## Hangi Java, neden?

Bilgisayarda iki Java var:

| Java | Yer | Kullanılıyor mu? |
|---|---|---|
| JetBrains Runtime (JBR) 21 | `C:\Program Files\Android\Android Studio\jbr` | **Evet** |
| Eclipse Temurin JDK 25 | `C:\Program Files\Eclipse Adoptium\jdk-25...` | Hayır |

Android Gradle eklentisi (AGP) ve Gradle 8.x, JDK 17 ya da 21 ile sınanmıştır. JDK 25 çok yenidir; Gradle 8.x onunla çalışmaz ya da beklenmedik hatalar verir. Android Studio kendi içinde JBR 21 getirdiği için ayrıca bir şey kurmaya gerek yoktur, Studio güncellenince JBR da güncellenir.

**JAVA_HOME** bu bilgisayarda zaten JBR'ı gösteriyor. Bozulursa:
*Başlat → "ortam değişkenleri" → Hesabınız için ortam değişkenlerini düzenleyin → JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`*. Açık PowerShell pencerelerini kapatıp yeniden açın.

## ANDROID_HOME (isteğe bağlı)

Proje SDK'yı `local.properties` dosyasındaki `sdk.dir` satırından bulur (Android Studio bu dosyayı kendisi yazar), bu yüzden zorunlu değildir. Komut satırı araçları için yine de önerilir:
aynı ekrandan yeni değişken: `ANDROID_HOME` = `C:\Users\<kullanıcı>\AppData\Local\Android\Sdk`.

## Doğrulanan sürümler (17.11.2026)

| Bileşen | Sürüm | Not |
|---|---|---|
| Android Studio | 2025.3.4 | |
| SDK platformu | android-36, android-36.1 | compileSdk/targetSdk 36 (telefon Android 16) |
| Build-tools | 35.0.0, 36.0.0, 36.1.0, 37.0.0 | |
| Platform-tools (adb) | 37.0.1 | |
| NDK | 27.1.12297006 (r27b), 30.0.16248370 (r30) | Hangisinin kullanılacağına whisper.cpp ile G-12 karar verir |
| CMake | 3.22.1, 4.1.2 | |
| Gradle | 8.14.3 | |
| AGP | 8.13.0 | compileSdk 36 ile sorunsuz |
| Kotlin | 2.2.20 | |

**Komut satırı derleme kanıtı:** Geçici (projeye eklenmeyen) bir deneme projesi bu sürümlerle, JBR 21 ile, `minSdk 29 / compileSdk 36 / targetSdk 36` ayarıyla `gradlew assembleDebug testDebugUnitTest` komutundan `BUILD SUCCESSFUL` aldı; APK üretildi ve 1 birim testi geçti. Asıl proje iskeleti G-04'te bu sürümlerle kurulur.

## Telefon (Samsung Galaxy A15)

1. *Ayarlar → Telefon hakkında → Yazılım bilgileri → Yapım numarası*'na 7 kez dokunun.
2. *Ayarlar → Geliştirici seçenekleri → USB hata ayıklama* açık olsun.
3. USB kabloyla bağlayın. Telefonda "USB hata ayıklamaya izin ver?" sorusu çıkar: **"Bu bilgisayara her zaman izin ver"** kutusunu işaretleyip **İzin ver**'e dokunun.

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| Betik "yetki bekliyor" (`unauthorized`) diyor | Telefonun kilidini açın; ekrandaki izin sorusunu onaylayın. Soru çıkmazsa *Geliştirici seçenekleri → USB hata ayıklama yetkilerini iptal et*, kabloyu çıkarıp takın. |
| Hiç cihaz görünmüyor | Kablo yalnız şarj kablosu olabilir; başka kablo deneyin. Telefonda bildirim çubuğundan USB modunu "Dosya aktarımı" yapın. Olmazsa Samsung'un ücretsiz Android USB sürücüsünü kurun. |
| Durum `offline` | `adb kill-server` sonra kabloyu yeniden takın. |
| `Shell does not have permission to access user 150` | Telefonda Güvenli Klasör var; betik bu yüzden yalnız ana kullanıcıyı (`--user 0`) okur. Bir şey yapmanız gerekmez. |
| Türkçe harfler bozuk görünüyor | Betik UTF-8 (BOM'lu) kaydedilmiştir; dosyayı başka düzenleyicide kaydederken kodlamayı değiştirmeyin. |
| Gradle "Unsupported class file major version 69" | Gradle JDK 25 ile çalışmış demektir. JAVA_HOME'u JBR'a çevirin (yukarıda). |
