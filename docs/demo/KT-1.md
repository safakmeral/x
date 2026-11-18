# KT-1 demosu: "Ayarları aç"

Amaç: baloncuk + ses + karar + sesli yanıt + uygulama açma akışını uçtan uca göstermek ve ekran kaydı almak (DEG-30, hoca: "sonraki teslimde çalışan iskeletten ekran görüntüsü/kısa video").

## Hazırlık (bir kez)

1. Uygulamayı açın, "İzni aç" düğmesine basın, bilgilendirmeyi okuyup kabul edin, Ayarlar'da "Telefon Asistanı"nı açın (gri ise: Uygulama ayarları → sağ üst üç nokta → "Kısıtlı ayarlara izin ver").
2. Uygulamada "Sesli komutu hazırla" → "Mikrofona izin ver" → "Uygulama kullanılırken izin ver". Türkçe dil paketi "hazır" görünmeli.
3. "Sesim" bölümünde Türkçe sesin hazır olduğunu görün (yoksa "Türkçe sesi indir").
4. İsterseniz uçak modunu açın: tanıma ve ses tamamen telefonda çalışır.

## Ekran kaydı

Bildirim panelini aşağı çekin → "Ekran kaydedici" → "Medya sesleri" seçin → Başlat. (Samsung'un kendi kaydedicisi, ücretsiz.) Kayıtta kişisel bildirim görünmemesi için önce "Rahatsız etmeyin"i açın.

## Senaryolar

| # | Ne yaparsınız | Beklenen |
|---|---|---|
| 1 | Ana ekranda baloncuğa ("Konuş") dokunun, "Ayarları aç" deyin | Baloncuk sarı "Dinliyorum" olur; altta "Yazarak söyle" ve "Dur" çıkar. Sustuktan kısa süre sonra "Ayarlar uygulamasını açıyorum." denir, Ayarlar açılır, ardından "Ayarlar açıldı." denir. Her cümle baloncuğun yanında yazıyla da görünür. |
| 2 | Baloncuğa dokunun, "Haritaları aç" deyin | "Google Haritalar uygulamasını açayım mı?" sorulur. "Evet" deyin → Haritalar açılır. (Yanıt vermezseniz 10 sn sonra "Tamam, yapmıyorum.") Telefondaki adı tam "Haritalar" ise kesin eşleşmedir, soru sorulmadan açılır; kısmi eşleşmeyi görmek için uygulamanın adının yalnız bir kısmını söyleyin. |
| 3 | Baloncuğa dokunun, sonra "Yazarak söyle"ye dokunun, "Ayarları aç" yazıp "Gönder" | Mikrofon kapanır, yazma ekranı açılır; gönderince 1. senaryodaki gibi Ayarlar açılır. |
| 4 | Baloncuğa dokunun, hiçbir şey söylemeyin | Birkaç saniye sonra "Sizi duyamadım. Tekrar denemek için "Konuş" düğmesine dokunun." Tek dokunuşla yeniden dinler (FG-05). |
| 5 | Baloncuğa dokunun, konuşurken ya da asistan konuşurken "Dur"a basın | Mikrofon hemen kapanır, ses kesilir, "Durdum." denir, hiçbir şey açılmaz. |
| 6 | "Bana şarkı söyle" deyin | "Bunu henüz yapamıyorum." |
| 7 | Dinlerken baloncuğa ikinci kez dokunun | "Bitirdim" anlamına gelir; beklemeden komut işlenir. |

## Gecikme ölçümü (geliştirici)

Bilgisayarda `adb logcat -s Latency` çalıştırın. Her komutta şu satırlar çıkar (içerik yok, yalnız süre):
- `tap->listening N ms` (hedef ≤ 1000, V-06)
- `command->first sentence queued N ms`, `command->speech started N ms` (hedef ≤ 2000)
- `command->app opened N ms`

"command" anı, tanıyıcının son metni verdiği andır; kullanıcının sustuğu an değil (tanıyıcının kendi susma algısı bunun önüne eklenir).
