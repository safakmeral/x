# Proje Vizyonu (öğrencinin kendi anlatımından özet)

## Sorun
Yaşlılar telefon kullanırken zorlanıyor: mesaj göndermekten, yanlış bir şey yapmaktan korkuyor, bir şeyin nerede olduğunu bulamıyorlar. Örnek: bir uygulamaya her girişte isim ve telefon numarası yazmak, "SMS gönder"e basmak, bildirimi aşağı çekip kodu okumak ve kodu girmek gerekiyor. Yaşlı baba bunu yapamıyor ve her seferinde çocuğundan yardım istiyor.

## Çözüm
- Ekranda her yere taşınabilen, yüzen bir daire var (Shazam'daki mavi daire gibi). Dokunulunca uygulama dinlemeye başlar. Kullanıcı derdini söyler, örneğin "Kızıma fotoğraf gönderemiyorum."
- **Mod 1, Yap (hızlı):** Uygulama adımları kendisi yapar. Örneğin ana ekranda kaydırarak WhatsApp'ı bulur, açar, kızının sohbetine girer, eki seçer ve galeriyi açar. Sonra "Göndermek istediğin fotoğrafı seç, daha fazlası için aşağı kaydır" der. Yaptığı her şeyi ekranda gösterir: kaydırmalar için kırmızı bir iz (Fruit Ninja'daki gibi), dokunuşlar için kırmızı nokta. Ne yaptığını sesli anlatır: "WhatsApp'ı arıyorum… WhatsApp'a dokundum." Amaç eğitici olması.
- **Mod 2, Rehber:** Uygulama ekrana kendisi dokunmaz. Ekranı izler, kullanıcıya sesli ve görsel olarak adım adım ne yapacağını söyler. Örnekler: "WhatsApp'ı bulmak için ekranı kaydır" (kırmızı kaydırma işaretiyle), "WhatsApp'a dokun" (kırmızı daire ile), "Aşağı yukarı kaydır ya da kızının adını arama kutusuna yaz". Kullanıcı işi kendisi yaptığı için daha iyi öğrenir.
- Renkler, arayüz ve modeller henüz kararlaştırılmadı. Kararlar kaynaklara dayanmalı.

## Ek fikir 1: Görev hafızası (öğrencinin talebi)
- "Ahmet'i ara" gibi bir görev ilk kez geldiğinde yapay zekâ kullanılır. Başarılı adım dizisi kaydedilir.
- Aynı istek tekrar geldiğinde yapay zekâya sorulmaz, kayıtlı adımlar doğrudan uygulanır.
- Ekran beklenenden farklıysa (uygulama güncellenmişse, bir açılır pencere çıkmışsa) ya da kullanıcı araya girip durumu değiştirirse yapay zekâ yeniden devreye girer.
- Hızlı ve optimize olmalı.

## Ek fikir 2: Hız ve araya girme (öğrencinin talebi)
- Uygulama kesinlikle çok hızlı olmalı. Bir şey istendiğinde 5–10 saniye beklenmemeli.
- Konuşma Siri'nin konuşma modu gibi insan gibi ve anlık olmalı.
- Yaşlı kullanıcı her an araya girebilmeli:
  - "Baştan anlat" derse süreç anında baştan anlatılmalı.
  - "Tamam, hatırladım" derse uygulama "Tamam, kapatıyorum" deyip durabilmeli.

## Kısıtlar
- Sadece Android. Önce APK olarak yerelde hocaya gösterilecek. Ama mimari Play Store'da yayınlanacakmış gibi kurulacak, KVKK ve gizlilik eksiksiz olacak.
- Bütçe sıfır. Sadece ücretsiz katmanlar kullanılacak. Deneme süreli ya da kredili hizmetler kullanılmayacak, kullanılması gerekirse öğrenciye sorulacak.
- Kodlama Claude Pro aboneliğiyle Claude Code üzerinden yapılıyor. Bu abonelik uygulamanın çalışma anında yapay zekâ olarak kullanılamaz.
