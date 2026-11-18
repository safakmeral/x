# -*- coding: utf-8 -*-
"""Generates core/src/test/resources/mask-corpus.json for G-03 and validates it.
All personal-looking values are fictional ("Test" names, 0599 000 xx xx phones,
bank code 00000 IBANs, 4000 0000 test-BIN cards, 1000000xxxx TCKNs)."""
import json, re, sys, collections

OUT = sys.argv[1]

# ---------- fake value generators ----------
def tckn(base9):
    d = [int(c) for c in base9]
    d10 = ((d[0]+d[2]+d[4]+d[6]+d[8])*7 - (d[1]+d[3]+d[5]+d[7])) % 10
    d11 = (sum(d) + d10) % 10
    return base9 + str(d10) + str(d11)

def tckn_valid(s):
    if not re.fullmatch(r"[1-9]\d{10}", s): return False
    return tckn(s[:9]) == s

def iban(acct16):
    bban = "00000" + "0" + acct16
    num = bban + "2927" + "00"
    chk = 98 - int(num) % 97
    raw = "TR%02d" % chk + bban
    return " ".join(raw[i:i+4] for i in range(0, len(raw), 4))

def iban_valid(s):
    s = s.replace(" ", "").upper()
    if not re.fullmatch(r"TR\d{24}", s): return False
    r = s[4:] + s[:4]
    r = "".join(str(ord(c)-55) if c.isalpha() else c for c in r)
    return int(r) % 97 == 1

def luhn_ok(num):
    ds = [int(c) for c in num][::-1]
    t = 0
    for i, x in enumerate(ds):
        if i % 2 == 1:
            x *= 2
            if x > 9: x -= 9
        t += x
    return t % 10 == 0

def card(n3):
    p = "400000000000" + n3
    for c in "0123456789":
        if luhn_ok(p + c):
            s = p + c
            return " ".join(s[i:i+4] for i in range(0, 16, 4))

WORDS = ["sıfır","bir","iki","üç","dört","beş","altı","yedi","sekiz","dokuz"]
def spell_digits(s):
    return " ".join(WORDS[int(c)] for c in s if c.isdigit())

TC = {i: tckn("1000000%02d" % i) for i in range(1, 12)}
IB = {i: iban("%016d" % i) for i in range(1, 12)}
KR = {i: card("%03d" % i) for i in range(1, 12)}
for v in TC.values(): assert tckn_valid(v)
for v in IB.values(): assert iban_valid(v), v
for v in KR.values(): assert luhn_ok(v.replace(" ", ""))
BAD_TC = "12345678901"
assert not tckn_valid(BAD_TC)
BAD_IBAN = "TR00 0000 0000 0000 0000 0000 01"
assert not iban_valid(BAD_IBAN)
BAD_CARD = "4000 0000 0000 0001"
assert not luhn_ok(BAD_CARD.replace(" ", ""))

def subst(s):
    s = re.sub(r"\$TCW(\d+)", lambda m: spell_digits(TC[int(m.group(1))]), s)
    s = re.sub(r"\$KRW(\d+)", lambda m: spell_digits(KR[int(m.group(1))]), s)
    s = re.sub(r"\$TC(\d+)", lambda m: TC[int(m.group(1))], s)
    s = re.sub(r"\$IBc(\d+)", lambda m: IB[int(m.group(1))].replace(" ", ""), s)
    s = re.sub(r"\$IBl(\d+)", lambda m: IB[int(m.group(1))].replace(" ", "").lower(), s)
    s = re.sub(r"\$IB(\d+)", lambda m: IB[int(m.group(1))], s)
    s = re.sub(r"\$KRc(\d+)", lambda m: KR[int(m.group(1))].replace(" ", ""), s)
    s = re.sub(r"\$KR(\d+)", lambda m: KR[int(m.group(1))], s)
    s = s.replace("$BADTC", BAD_TC).replace("$BADIBAN", BAD_IBAN).replace("$BADCARD", BAD_CARD)
    return s

# ---------- contacts ----------
REHBER = [
    ("c1", "Ayşe Test", "0599 000 00 01"), ("c2", "Mehmet Test", "0599 000 00 02"),
    ("c3", "Zeynep Test", "0599 000 00 03"), ("c4", "Osman Test", "0599 000 00 04"),
    ("c5", "Elif Test", "0599 000 00 05"), ("c6", "Hasan Test", "0599 000 00 06"),
    ("c7", "Deniz Test", "0599 000 00 07"), ("c8", "Ali Test", "0599 000 00 08"),
    ("c9", "Ali Testçi", "0599 000 00 09"), ("c10", "Hatice Test", "0599 000 00 10"),
    ("c11", "Can Test", "0599 000 00 11"), ("c12", "Emine Test", "0599 000 00 12"),
    ("c13", "İbrahim Test", "0599 000 00 13"), ("c14", "Şükrü Test", "0599 000 00 14"),
]
CNAME = {cid: ad for cid, ad, _ in REHBER}

# ---------- dictionaries (authoritative for G-31/G-32 tests) ----------
SOZLUK = {
  "komut": sorted(set("""
ara arar arama aramak arasana arayın aray aradı aramış aç açar açık açıl açsana kapat kapa kıs kısar artır azalt yükselt büyüt küçült
göster bul gönder yolla yaz yazar oku okur sil ekle kaydet kayded kayıt kur kurar ayarla değiştir dinle çal çek paylaş gir git dön geri
bak bakar söyle hatırlat yap yapar et ver al at bas dokun seç kaldır indir güncelle bağlan bağla aktar ilet tuşla sorgula kopyala kontrol
mesaj sms whatsapp telefon numara rehber kişi fotoğraf resim video kamera galeri alarm saat saatini hatırlatıcı takvim ayar ses sesi zil
titreşim ekran parlaklık yazı wifi bluetooth internet şarj konum harita hava durum uygulama bildirim görüntülü sesli ana sayfa menü uçak
mod e-posta eposta posta mail adres hesap alıcı son cevapsız gelen giden liste uyku gece sabah akşam öğle yarın bugün dün hafta
pazartesi salı çarşamba perşembe cuma cumartesi pazar dakika saniye kere kez defa lütfen bana beni benim ben sen sana seni onu ona
bunu şunu bu şu o bir biri ve ile için gibi daha çok az biraz nasıl ne neydi nerede neden niye hangi kim kimin kimi mi mı mu mü misin
mısın musun değil var yok tamam evet hayır şimdi hemen sonra önce en yakın büyük küçük yeni eski tüm hepsi her de da ki diye ama hem
ya veya oraya buraya yukarı aşağı sağ sol üst alt düğme tuş komşu eczane hastane doktor taksi polis ambulans itfaiye acil bakkal market
kapıcı kimlik tc merkez poliklinik randevu sonuç kargo evim tarif link adı şöyle merhaba acaba olarak forma kutu ilk yardım çağır lira oraya
""".split())),
  "akrabalik": ["anne","baba","oğul","oğl","kız","torun","gelin","damat","eş","babaanne","anneanne","kardeş"],
  "hitap": ["hanım","bey","abla","abi","ağabey","amca","dayı","teyze","hala","enişte","yenge","dede","nine","hoca","usta","efendi"],
  "ekler": {
    "'i": "ı i u ü yı yi yu yü nı ni nu nü mı mi mu mü ımı imi umu ümü sını sini sunu sünü leri ları gili".split(),
    "'e": "a e ya ye na ne ma me ıma ime uma üme sına sine suna süne lere lara gile".split(),
    "'de": "da de ta te nda nde mda mde ımda imde umda ümde lerde larda gilde".split(),
    "'den": "dan den tan ten ndan nden mdan mden ımdan imden umdan ümden lerden lardan gilden".split(),
    "'in": "ın in un ün nın nin nun nün mın min mun mün ımın imin umun ümün sının sinin lerin ların gilin".split(),
    "'le": "la le yla yle ıyla iyle uyla üyle mla mle ımla imle umla ümle lerle larla gille".split(),
    "": "m ım im um üm cığım ciğim cuğum cüğüm cım cim cum cüm gil giller ler lar lerim larım sı si su sü mız miz".split(),
  },
  "yumusama": {"p": "b", "ç": "c", "t": "d", "k": "ğ"},
  "kisi_yuvasi_tetikleyicileri": {
    "'i": ["ara","arar","arasana","arayın","arayabilir","aramak","görüntülü"],
    "'e": ["mesaj","sms"],
    "'in": ["numarası","numarasını"],
  },
  "dikte": {
    "once_isaretleri": ["diye"],
    "sonra_isaretleri": ["de ki","yaz ki","söyle ki","şunu yaz","şöyle yaz"],
    "iki_nokta_sozcukleri": ["mesaj","yaz","de","söyle","not"],
    "fiiller": ["mesaj","yaz","at","gönder","yolla","söyle","de","sms","whatsapp"],
  },
  "adres": {
    "anahtarlar": ["mahalle","mahallesi","mah","mah.","sokak","sokağı","sokağa","sok","sok.","sk","sk.","cadde","caddesi",
                   "cad","cad.","cd","cd.","bulvar","bulvarı","apartman","apartmanı","apt","apt.","site","sitesi","blok","bloğu","köy","köyü"],
    "numara_gostergeleri": ["no","no:","numara","kat","daire","d:","daire:","blok"],
  },
  "sayi_sozcukleri": {"sıfır":0,"bir":1,"iki":2,"üç":3,"dört":4,"beş":5,"altı":6,"yedi":7,"sekiz":8,"dokuz":9,
                      "on":10,"yirmi":20,"otuz":30,"kırk":40,"elli":50,"altmış":60,"yetmiş":70,"seksen":80,"doksan":90,
                      "yüz":100,"bin":1000,"milyon":1000000},
  "ozel_terimler": {
    "tek": ["ilaç","ilac","tansiyon","insülin","insulin","diyabet","kolesterol","kalp","kanser","kemoterapi","diyaliz","astım",
            "alzheimer","demans","parkinson","felç","depresyon","antidepresan","psikiyatri","onkoloji","kardiyoloji","nöroloji",
            "üroloji","jinekoloji","tahlil","tomografi","ameliyat","reçete","hastalık","hastalığ","hastayım","hepatit","gebelik",
            "hamile","genetik","namaz","oruç","oruc","cami","kilise","sinagog","cemevi","tarikat","mezhep","imsakiye","sahur",
            "iftar","kandil","sendika","dernek","vakıf","sabıka","cezaevi","mahkeme","hapis"],
    "obek": [["şeker","hastal"],["kalp","kriz"],["kan","tahlil"],["engelli","rapor"],["işitme","cihaz"]],
  },
  "gizli": {
    "tek_basina": [["doğrulama","kod"],["tek","kullanımlık"],["kimseyle","paylaşma"],["kimse","ile","paylaşma"]],
    "rakamla_gizli_bilgi": ["şifre","parola","pin","cvv","cvc"],
    "rakamla_dogrulama_kodu": ["kod","otp"],
    "haric": [["posta","kod"]],
  },
  "durum": ["Açık","Kapalı","Cevapsız","Gelen","Giden","Fotoğraf","Video","Sesli mesaj","Okundu","Dün","Bugün","Pazartesi",
            "Salı","Çarşamba","Perşembe","Cuma","Cumartesi","Pazar","Bağlı değil","Bağlı","Varsayılan","Otomatik","Yok"],
  "eposta_tld": ["com","net","org","tr","edu","gov","info","co"],
}
AYARLAR = {"niyet_esigi_sozcuk": 15, "en_kisa_rakam_dizisi": 4, "sayi_sozcugu_esigi": 3,
           "baglam_en_fazla_sozcuk": 2, "adres_sola_en_fazla": 3, "eposta_sola_en_fazla": 4,
           "sozluk_onek_en_kisa": 4, "gizli_rakam_en_kisa": 3}

# ---------- examples ----------
# komut: (id, tags, kaynak, girdi, template | ("ENGELLE", neden), extra)
K = []
def komut(i, tags, src, inp, out, **kw): K.append(("komut", i, tags, src, inp, out, kw))
def ekran(i, tags, pkg, nodes, **kw): K.append(("ekran", i, tags, pkg, nodes, None, kw))
def birlesik(i, tags, src, cmd, cmd_out, pkg, nodes, **kw): K.append(("birlesik", i, tags, src, (cmd, pkg, nodes), cmd_out, kw))

WA, SMS, TELA, AYAR, SAAT, GAL, KISIA = ("com.whatsapp", "com.samsung.android.messaging", "com.samsung.android.dialer",
    "com.android.settings", "com.sec.android.app.clockpackage", "com.sec.android.gallery3d", "com.samsung.android.app.contacts")

# --- KISI ---
komut("K-001", ["KISI"], "ses", "ayşeyi ara", "{KISI@c1}'i ara")
komut("K-002", ["KISI"], "ses", "mehmede mesaj at", "{KISI@c2}'e mesaj at")
komut("K-003", ["KISI"], "ses", "zeynebi görüntülü ara", "{KISI@c3}'i görüntülü ara")
komut("K-004", ["KISI"], "yazi", "Osman'a WhatsApp'tan mesaj gönder", "{KISI@c4}'e WhatsApp'tan mesaj gönder")
komut("K-005", ["KISI"], "ses", "elifin numarasını göster", "{KISI@c5}'in numarasını göster")
komut("K-006", ["KISI"], "ses", "hasan testi ara", "{KISI@c6}'i ara")
komut("K-007", ["KISI"], "ses", "mehmet amcama mesaj yaz", "{KISI@c2} amcama mesaj yaz")
komut("K-008", ["KISI"], "ses", "aliyi ara", "{KISI:Ali}'i ara")
komut("K-009", ["KISI"], "yazi", "sukruyu ara", "{KISI@c14}'i ara")
komut("K-010", ["KISI"], "ses", "ibrahimle görüntülü konuşmak istiyorum", "{KISI@c13}'le görüntülü konuşmak istiyorum")
komut("K-011", ["KISI"], "ses", "gülten test hanıma mesaj at", "{KISI:Gülten Test} hanıma mesaj at")
komut("K-012", ["KISI"], "ses", "selin teste mesaj at", "{KISI~:Selin Teste}'e mesaj at")
komut("K-013", ["KISI"], "yazi", "Yarın Gülten Test ile buluşacağım, hatırlatıcı kur", "Yarın {KISI:Gülten Test} ile buluşacağım, hatırlatıcı kur")
komut("K-014", ["KISI", "ASIRI_MASKE"], "ses", "deniz fotoğraflarını göster", "{KISI@c7} fotoğraflarını göster")
komut("K-015", ["KISI", "ASIRI_MASKE"], "ses", "canım kızıma mesaj at", "{KISI@c11} kızıma mesaj at")
komut("K-016", ["KISI"], "ses", "hatice teyzemi ara", "{KISI@c10} teyzemi ara")
komut("K-017", ["KISI"], "yazi", "Emine Test'i ara", "{KISI@c12}'i ara")
komut("K-018", ["KISI"], "ses", "ali testçinin numarasını oku", "{KISI@c9}'in numarasını oku")
komut("K-019", ["KISI"], "ses", "kerem test beyi ara", "{KISI:Kerem Test} beyi ara")
komut("K-020", ["KISI"], "ses", "nurten testi ara", "{KISI~:Nurten Testi}'i ara")
komut("K-021", ["KISI"], "yazi", "Ayşe Test'in doğum gününü takvime ekle", "{KISI@c1}'in doğum gününü takvime ekle")

# --- TEL ---
komut("T-001", ["TEL"], "yazi", "0599 000 00 15'i ara", "{TEL:0599 000 00 15}'i ara")
komut("T-002", ["TEL"], "ses", "05990000016 numarasını ara", "{TEL:05990000016} numarasını ara")
komut("T-003", ["TEL"], "yazi", "+90 599 000 00 17 numarasına mesaj gönder", "{TEL:+90 599 000 00 17} numarasına mesaj gönder")
komut("T-004", ["TEL", "YAZIYLA_SAYI"], "ses", "sıfır beş yüz doksan dokuz sıfır sıfır sıfır sıfır sıfır on sekiz ara",
      "{TEL:sıfır beş yüz doksan dokuz sıfır sıfır sıfır sıfır sıfır on sekiz} ara")
komut("T-005", ["TEL"], "yazi", "(0212) 000 00 19 numarasını ara", "{TEL:(0212) 000 00 19} numarasını ara")
komut("T-006", ["TEL"], "yazi", "0599-000-00-20 kimin numarası", "{TEL:0599-000-00-20} kimin numarası")
ekran("T-007", ["TEL"], TELA, [("ARAYUZ", "Son aramalar", "Son aramalar"), ("LISTE", "0599 000 00 21", "{TEL:0599 000 00 21}"),
      ("LISTE", "Cevapsız", "Cevapsız"), ("LISTE", "10:05", "10:05")])
ekran("T-008", ["TEL", "KISI"], KISIA, [("LISTE", "Ayşe Test", "{KISI@c1}"), ("LISTE", "0599 000 00 01", "{TEL:0599 000 00 01}"),
      ("ARAYUZ", "Ara", "Ara")])
ekran("T-009", ["TEL", "METIN"], SMS, [("BILDIRIM", "0599 000 00 22", "{TEL:0599 000 00 22}"),
      ("BILDIRIM", "Kargonuz yola çıktı", "{METIN:Kargonuz yola çıktı}")])
komut("T-010", ["TEL", "KISI"], "ses", "ayşenin numarası 0599 000 00 01 mi", "{KISI@c1}'in numarası {TEL:0599 000 00 01} mi")
ekran("T-011", ["TEL"], KISIA, [("ARAYUZ", "Yeni numara: 0599 000 00 23", "Yeni numara: {TEL:0599 000 00 23}")])
komut("T-012", ["TEL"], "yazi", "0090 599 000 00 24'ü rehbere ekle", "{TEL:0090 599 000 00 24}'i rehbere ekle")
komut("T-013", ["TEL", "YAZIYLA_SAYI"], "ses", "sıfır iki yüz on iki sıfır sıfır sıfır sıfır sıfır yirmi altı ara",
      "{TEL:sıfır iki yüz on iki sıfır sıfır sıfır sıfır sıfır yirmi altı} ara")

# --- EPOSTA ---
komut("P-001", ["EPOSTA"], "yazi", "ayse.test@example.com adresine e-posta gönder", "{EPOSTA:ayse.test@example.com} adresine e-posta gönder")
komut("P-002", ["EPOSTA"], "ses", "ayşe nokta test et example nokta com adresine mail at",
      "{EPOSTA:ayşe nokta test et example nokta com} adresine mail at")
ekran("P-003", ["EPOSTA"], AYAR, [("ARAYUZ", "Hesap: ayse.test@example.com", "Hesap: {EPOSTA:ayse.test@example.com}")])
ekran("P-004", ["EPOSTA", "GIRIS"], AYAR, [("DUZENLENEBILIR", "mehmet.test@example.org", "{EPOSTA:mehmet.test@example.org}")])
ekran("P-005", ["EPOSTA"], AYAR, [("ARAYUZ", "Hesaplar", "Hesaplar"), ("OZET", "osman.test@example.net", "{EPOSTA:osman.test@example.net}")])
komut("P-006", ["EPOSTA"], "yazi", "E-postamı elif.test@example.com olarak değiştir", "E-postamı {EPOSTA:elif.test@example.com} olarak değiştir")
komut("P-007", ["EPOSTA"], "ses", "hasan test et example nokta com nokta tr adresine mail gönder",
      "{EPOSTA:hasan test et example nokta com nokta tr} adresine mail gönder")
birlesik("P-008", ["EPOSTA", "TUTARLILIK", "GIRIS"], "yazi", "zeynep.test@example.com adresine yaz",
         "{EPOSTA:zeynep.test@example.com} adresine yaz", WA,
         [("ARAYUZ", "Alıcı", "Alıcı"), ("DUZENLENEBILIR", "zeynep.test@example.com", "{EPOSTA:zeynep.test@example.com}")])
ekran("P-009", ["EPOSTA", "METIN"], SMS, [("BILDIRIM", "Yeni e-posta: ali.test@example.com", "{METIN:Yeni e-posta: ali.test@example.com}")])
komut("P-010", ["EPOSTA"], "yazi", "Bana gelen postayı info.test@example.org'a ilet", "Bana gelen postayı {EPOSTA:info.test@example.org}'e ilet")

# --- TCKN ---
komut("C-001", ["TCKN"], "yazi", "Kimlik numaram $TC1, forma yaz", "Kimlik numaram {TCKN:$TC1}, forma yaz")
komut("C-002", ["TCKN"], "ses", "tc kimlik numaram $TC2", "tc kimlik numaram {TCKN:$TC2}")
komut("C-003", ["TCKN", "YAZIYLA_SAYI"], "ses", "kimlik numaram $TCW3", "kimlik numaram {TCKN:$TCW3}")
ekran("C-004", ["TCKN", "GIRIS"], WA, [("DUZENLENEBILIR", "$TC4", "{TCKN:$TC4}")])
ekran("C-005", ["TCKN"], AYAR, [("ARAYUZ", "T.C. Kimlik No: $TC5", "T.C. Kimlik No: {TCKN:$TC5}")])
ekran("C-006", ["TCKN", "METIN"], SMS, [("BILDIRIM", "Randevunuz alındı. T.C. $TC6", "{METIN:Randevunuz alındı. T.C. $TC6}")])
komut("C-007", ["TCKN"], "yazi", "$TC7 numarasıyla e-devlete gir", "{TCKN:$TC7} numarasıyla e-devlete gir")
komut("C-008", ["TCKN", "SAYI"], "yazi", "Bu numara $BADTC ne?", "Bu numara {SAYI:$BADTC} ne?")
komut("C-009", ["TCKN"], "ses", "annemin kimlik numarası $TC8", "annemin kimlik numarası {TCKN:$TC8}")
birlesik("C-010", ["TCKN", "GIRIS"], "ses", "kimlik numaramı bu kutuya yaz $TC9", "kimlik numaramı bu kutuya yaz {TCKN:$TC9}", WA,
         [("ARAYUZ", "T.C. Kimlik Numarası", "T.C. Kimlik Numarası"), ("DUZENLENEBILIR", "", "")])
ekran("C-011", ["TCKN"], SMS, [("LISTE", "$TC10", "{TCKN:$TC10}")])

# --- IBAN ---
komut("I-001", ["IBAN"], "yazi", "$IB1 numaralı hesaba 500 lira gönder", "{IBAN:$IB1} numaralı hesaba 500 lira gönder")
komut("I-002", ["IBAN"], "yazi", "$IBc2 ibanını kızıma gönder", "{IBAN:$IBc2} ibanını kızıma gönder")
ekran("I-003", ["IBAN", "GIRIS"], WA, [("DUZENLENEBILIR", "$IB3", "{IBAN:$IB3}")])
ekran("I-004", ["IBAN"], WA, [("ARAYUZ", "IBAN: $IB4", "IBAN: {IBAN:$IB4}")])
ekran("I-005", ["IBAN", "METIN"], WA, [("LISTE", "Iban $IB5 Ayşe Test", "{METIN:Iban $IB5 Ayşe Test}")])
komut("I-006", ["IBAN", "SAYI"], "yazi", "$BADIBAN hesabını kontrol et", "{SAYI:$BADIBAN} hesabını kontrol et")
komut("I-007", ["IBAN"], "yazi", "ibanım $IBl6", "ibanım {IBAN:$IBl6}")
birlesik("I-008", ["IBAN", "TUTARLILIK", "GIRIS"], "yazi", "$IB7 hesabına gönder", "{IBAN:$IB7} hesabına gönder", WA,
         [("DUZENLENEBILIR", "$IBc7", "{IBAN:$IBc7|$IB7}")])
ekran("I-009", ["IBAN"], AYAR, [("OZET", "$IB8", "{IBAN:$IB8}")])
komut("I-010", ["IBAN", "KISI"], "yazi", "Hesap: $IB9 Alıcı: Elif Test", "Hesap: {IBAN:$IB9} Alıcı: {KISI@c5}")
ekran("I-011", ["IBAN", "METIN"], SMS, [("BILDIRIM", "$IB10 hesabınıza 1.250 TL yatırıldı", "{METIN:$IB10 hesabınıza 1.250 TL yatırıldı}")])

# --- KART ---
komut("R-001", ["KART"], "yazi", "Kart numaram $KR1", "Kart numaram {KART:$KR1}")
ekran("R-002", ["KART", "GIRIS"], WA, [("DUZENLENEBILIR", "$KR2", "{KART:$KR2}")])
ekran("R-003", ["KART", "GIRIS"], WA, [("DUZENLENEBILIR", "$KRc3", "{KART:$KRc3}")])
komut("R-004", ["KART", "SAYI"], "yazi", "$KR4 son kullanma 12/27", "{KART:$KR4} son kullanma {SAYI:12/27}")
komut("R-005", ["KART", "KOD"], "yazi", "$KR5 cvv 123", ("ENGELLE", "GIZLI_BILGI"))
komut("R-006", ["KART", "YAZIYLA_SAYI"], "ses", "kart numaram $KRW6", "kart numaram {KART:$KRW6}")
komut("R-007", ["KART", "SAYI"], "yazi", "$BADCARD kartını kaydet", "{SAYI:$BADCARD} kartını kaydet")
ekran("R-008", ["KART", "SAYI"], AYAR, [("ARAYUZ", "Kart •••• 4821", "Kart •••• {SAYI:4821}")])
ekran("R-009", ["KART", "METIN"], WA, [("LISTE", "Kartım $KR7 bunu kaydet", "{METIN:Kartım $KR7 bunu kaydet}")])
birlesik("R-010", ["KART", "TUTARLILIK", "GIRIS"], "yazi", "$KR8 numaralı kartı ekle", "{KART:$KR8} numaralı kartı ekle", WA,
         [("DUZENLENEBILIR", "$KR8", "{KART:$KR8}")])
komut("R-011", ["KART"], "yazi", "$KR9'u kaydet", "{KART:$KR9}'i kaydet")

# --- ADRES ---
komut("A-001", ["ADRES", "YAZIYLA_SAYI"], "ses", "deneme mahallesi papatya sokak numara on iki adresine taksi çağır",
      "{ADRES:deneme mahallesi papatya sokak numara on iki} adresine taksi çağır")
komut("A-002", ["ADRES"], "yazi", "Test Sokak No:5 Daire:3 adresine git", "{ADRES:Test Sokak No:5 Daire:3} adresine git")
ekran("A-003", ["ADRES"], WA, [("LISTE", "Deneme Mah. Lale Sk. No:12 D:4 Kadıköy/İstanbul",
      "{ADRES:Deneme Mah. Lale Sk. No:12 D:4 Kadıköy/İstanbul}")])
komut("A-004", ["ADRES"], "yazi", "Evim Örnek Caddesi 25 numara, oraya nasıl giderim",
      "Evim {ADRES:Örnek Caddesi 25 numara}, oraya nasıl giderim")
komut("A-005", ["ADRES"], "yazi", "41.00001, 29.00001 konumuna git", "{ADRES:41.00001, 29.00001} konumuna git")
ekran("A-006", ["ADRES"], WA, [("ARAYUZ", "Teslimat adresi: Deneme Mahallesi Test Sokak No 7",
      "Teslimat adresi: {ADRES:Deneme Mahallesi Test Sokak No 7}")])
komut("A-007", ["ADRES", "YAZIYLA_SAYI"], "ses", "test apartmanı kat üç daire beşe kargo gönder",
      "{ADRES:test apartmanı kat üç daire beş}'e kargo gönder")
ekran("A-008", ["ADRES", "GIRIS"], WA, [("DUZENLENEBILIR", "Lale Sokak No 3", "{ADRES:Lale Sokak No 3}")])
ekran("A-009", ["ADRES"], AYAR, [("ARAYUZ", "Ev adresi", "Ev adresi"), ("OZET", "Papatya Mah. 1. Sokak No 9", "{ADRES:Papatya Mah. 1. Sokak No 9}")])
birlesik("A-010", ["ADRES", "GIRIS"], "ses", "evime taksi çağır", "evime taksi çağır", WA,
         [("ARAYUZ", "Nereye?", "Nereye?"), ("DUZENLENEBILIR", "Örnek Cad. No 14", "{ADRES:Örnek Cad. No 14}")])
komut("A-011", ["ADRES"], "yazi", "Kargo Deneme Köyü 12'ye gelsin", "Kargo {ADRES:Deneme Köyü 12}'e gelsin")

# --- SAYI ---
komut("S-001", ["SAYI"], "ses", "müşteri numaram 77889900", "müşteri numaram {SAYI:77889900}")
komut("S-002", ["SAYI"], "yazi", "Doğum tarihim 12.03.1950", "Doğum tarihim {SAYI:12.03.1950}")
komut("S-003", ["SAYI"], "ses", "1950 doğumluyum", "{SAYI:1950} doğumluyum")
komut("S-004", ["SAYI"], "yazi", "99 TST 001 plakalı aracı sorgula", "{SAYI:99 TST 001} plakalı aracı sorgula")
ekran("S-005", ["SAYI"], WA, [("ARAYUZ", "Sipariş no: 556677", "Sipariş no: {SAYI:556677}")])
komut("S-006", ["SAYI"], "yazi", "Abone numaram 4455-6677", "Abone numaram {SAYI:4455-6677}")
komut("S-007", ["SAYI", "YAZIYLA_SAYI"], "ses", "abone numaram iki bin yüz on iki", "abone numaram {SAYI:iki bin yüz on iki}")
komut("S-008", ["SAYI", "YAZIYLA_SAYI", "ASIRI_MASKE"], "ses", "üç yüz elli lira", "{SAYI:üç yüz elli} lira")
komut("S-009", ["SAYI"], "yazi", "4821'i tuşla", "{SAYI:4821}'i tuşla")
ekran("S-010", ["SAYI"], WA, [("LISTE", "12.03.2024", "{SAYI:12.03.2024}")])
komut("S-011", ["SAYI", "ASIRI_MASKE"], "ses", "2025 fotoğraflarını göster", "{SAYI:2025} fotoğraflarını göster")

# --- METIN ---
komut("M-001", ["METIN", "KISI"], "ses", "ayşeye yarın geliyorum diye mesaj at", "{KISI@c1}'e {METIN:yarın geliyorum} diye mesaj at")
komut("M-002", ["METIN", "KISI"], "ses", "elife de ki akşam yemeğe gel", "{KISI@c5}'e de ki {METIN:akşam yemeğe gel}")
komut("M-003", ["METIN", "KISI", "OZEL"], "yazi", "Mehmet'e mesaj: İlaçlarını almayı unutma",
      "{KISI@c2}'e mesaj: {METIN:İlaçlarını almayı unutma}")
ekran("M-004", ["METIN", "KISI"], WA, [("ARAYUZ", "Sohbetler", "Sohbetler"), ("LISTE", "Ayşe Test", "{KISI@c1}"),
      ("LISTE", "Yarın pazara gidelim mi?", "{METIN:Yarın pazara gidelim mi?}"), ("LISTE", "14:32", "14:32"),
      ("LISTE", "Mehmet Test", "{KISI@c2}"), ("LISTE", "Fotoğraf", "Fotoğraf"), ("LISTE", "Dün", "Dün")])
ekran("M-005", ["METIN", "KISI", "GIRIS"], SMS, [("LISTE", "Zeynep Test", "{KISI@c3}"),
      ("LISTE", "Bugün eve geç geleceğim", "{METIN:Bugün eve geç geleceğim}"), ("LISTE", "Tamam", "{METIN:Tamam}"),
      ("DUZENLENEBILIR", "", ""), ("ARAYUZ", "Mesaj yazın", "Mesaj yazın"), ("ARAYUZ", "Gönder", "Gönder")])
ekran("M-006", ["METIN", "KISI"], WA, [("BILDIRIM", "Hasan Test", "{KISI@c6}"), ("BILDIRIM", "Akşam maç var, gel", "{METIN:Akşam maç var, gel}")])
komut("M-007", ["METIN", "KISI"], "ses", "osmana selam söyle diye yaz", "{KISI@c4}'e {METIN:selam söyle} diye yaz")
komut("M-008", ["METIN"], "ses", "yarın doktora gideceğim diye kızıma mesaj gönder", "{METIN:yarın doktora gideceğim} diye kızıma mesaj gönder")
komut("M-009", ["METIN"], "yazi", "Şu linki aç https://example.com/davet?ad=test", "Şu linki aç {METIN:https://example.com/davet?ad=test}")
ekran("M-010", ["METIN"], GAL, [("ARAYUZ", "Albümler", "Albümler"), ("LISTE", "Torunumun düğünü", "{METIN:Torunumun düğünü}")])
birlesik("M-011", ["METIN", "TUTARLILIK", "KISI"], "ses", "ayşeye tamam geliyorum diye yaz", "{KISI@c1}'e {METIN:tamam geliyorum} diye yaz", WA,
         [("LISTE", "Ayşe Test", "{KISI@c1}"), ("LISTE", "tamam geliyorum", "{METIN:tamam geliyorum}")])
ekran("M-012", ["METIN", "OZEL"], SAAT, [("ARAYUZ", "Alarm ekle", "Alarm ekle"), ("LISTE", "08:00", "08:00"),
      ("LISTE", "Tansiyon ilacı", "{METIN:Tansiyon ilacı}")])
ekran("M-013", ["METIN"], AYAR, [("ARAYUZ", "Wi-Fi", "Wi-Fi"), ("OZET", "Ev_Agi_5G", "{METIN:Ev_Agi_5G}"),
      ("ARAYUZ", "Telefon adı", "Telefon adı"), ("OZET", "Gülsen Test'in Galaxy A15", "{METIN:Gülsen Test'in Galaxy A15}")])

# --- GIRIS (kullanici girisi / parola) ---
ekran("G-001", ["GIRIS", "METIN"], WA, [("DUZENLENEBILIR", "Merhaba Ayşe nasılsın", "{METIN:Merhaba Ayşe nasılsın}")])
ekran("G-002", ["GIRIS", "NEGATIF"], WA, [("ARAYUZ", "Ara", "Ara"), ("DUZENLENEBILIR", "", "")])
ekran("G-003", ["GIRIS", "PAROLA"], AYAR, [("ARAYUZ", "Parola", "Parola"), ("PAROLA", "••••••", "")])
ekran("G-004", ["GIRIS", "PAROLA"], AYAR, [("PAROLA", "gizli123", "")])
ekran("G-005", ["GIRIS", "KISI"], SMS, [("ARAYUZ", "Alıcı", "Alıcı"), ("DUZENLENEBILIR", "Ayşe Test", "{KISI@c1}")])
ekran("G-006", ["GIRIS", "TEL"], TELA, [("DUZENLENEBILIR", "0599 000 00 25", "{TEL:0599 000 00 25}")])
ekran("G-007", ["GIRIS", "METIN"], SMS, [("DUZENLENEBILIR", "Tamam", "{METIN:Tamam}")])
ekran("G-008", ["GIRIS", "METIN", "OZEL"], WA, [("DUZENLENEBILIR", "kardiyoloji randevusu", "{METIN:kardiyoloji randevusu}")])
birlesik("G-009", ["GIRIS", "KISI"], "ses", "bu kutuya adımı yaz", "bu kutuya adımı yaz", WA,
         [("ARAYUZ", "Ad Soyad", "Ad Soyad"), ("DUZENLENEBILIR", "Elif Test", "{KISI@c5}")])
ekran("G-010", ["GIRIS", "PAROLA", "KOD"], AYAR, [("ARAYUZ", "Şifre", "Şifre"), ("PAROLA", "••••", ""), ("ARAYUZ", "Giriş yap", "Giriş yap")])
ekran("G-011", ["GIRIS", "METIN"], WA, [("DUZENLENEBILIR", "Akşam 7'de gel", "{METIN:Akşam 7'de gel}")])

# --- OZEL ---
komut("O-001", ["OZEL"], "ses", "tansiyon ilacımı hatırlat", "{OZEL:tansiyon ilacımı} hatırlat")
komut("O-002", ["OZEL"], "ses", "insülin saatimi kur", "{OZEL:insülin} saatimi kur")
komut("O-003", ["OZEL"], "ses", "kardiyoloji randevumu göster", "{OZEL:kardiyoloji} randevumu göster")
komut("O-004", ["OZEL"], "ses", "namaz vakitlerini göster", "{OZEL:namaz} vakitlerini göster")
komut("O-005", ["OZEL"], "ses", "kanser tahlil sonuçlarımı aç", "{OZEL:kanser tahlil} sonuçlarımı aç")
komut("O-006", ["OZEL"], "ses", "depresyon ilacımın adı neydi", "{OZEL:depresyon ilacımın} adı neydi")
komut("O-007", ["OZEL"], "ses", "diyaliz merkezini ara", "{OZEL:diyaliz} merkezini ara")
komut("O-008", ["OZEL"], "ses", "oruç saatlerini göster", "{OZEL:oruç} saatlerini göster")
komut("O-009", ["OZEL"], "yazi", "Kalp krizi geçirenlere ilk yardım videosu aç", "{OZEL:Kalp krizi} geçirenlere ilk yardım videosu aç")
komut("O-010", ["OZEL"], "yazi", "Sendika toplantısını takvime ekle", "{OZEL:Sendika} toplantısını takvime ekle")
komut("O-011", ["OZEL"], "ses", "şeker hastalığım için hatırlatıcı kur", "{OZEL:şeker hastalığım} için hatırlatıcı kur")
komut("O-012", ["OZEL"], "ses", "psikiyatri polikliniğini ara", "{OZEL:psikiyatri} polikliniğini ara")

# --- KOD / gizli bilgi (ENGELLE) ---
ekran("D-001", ["KOD"], SMS, [("BILDIRIM", "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın.", None)], engel="DOGRULAMA_KODU")
birlesik("D-002", ["KOD"], "ses", "gelen kodu oku", None, SMS, [("BILDIRIM", "Giriş kodunuz 5521", None)], engel="DOGRULAMA_KODU")
komut("D-003", ["KOD", "YAZIYLA_SAYI"], "ses", "kod dört sekiz iki bir gir", ("ENGELLE", "DOGRULAMA_KODU"))
komut("D-004", ["KOD"], "yazi", "Şifrem 1234, gir", ("ENGELLE", "GIZLI_BILGI"))
ekran("D-005", ["KOD"], SMS, [("LISTE", "123456 tek kullanımlık şifrenizdir", None)], engel="DOGRULAMA_KODU")
ekran("D-006", ["KOD"], SMS, [("ARAYUZ", "Bu kodu kimseyle paylaşmayın", None)], engel="DOGRULAMA_KODU")
komut("D-007", ["KOD", "YAZIYLA_SAYI"], "ses", "pin kodum bir iki üç dört", ("ENGELLE", "GIZLI_BILGI"))
ekran("D-008", ["KOD"], WA, [("BILDIRIM", "WhatsApp kodunuz: 112-233", None)], engel="DOGRULAMA_KODU")
komut("D-009", ["KOD"], "yazi", "kartın cvv'si 321", ("ENGELLE", "GIZLI_BILGI"))
ekran("D-010", ["KOD", "NEGATIF"], AYAR, [("ARAYUZ", "Şifre ve güvenlik", "Şifre ve güvenlik")])
ekran("D-011", ["KOD"], WA, [("ARAYUZ", "Doğrulama kodunu girin", None), ("DUZENLENEBILIR", "", None)], engel="DOGRULAMA_KODU")
komut("D-012", ["KOD"], "yazi", "dogrulama kodu 7788 geldi", ("ENGELLE", "DOGRULAMA_KODU"))

# --- TUTARLILIK ---
birlesik("U-001", ["TUTARLILIK", "KISI"], "ses", "ayşeyi ara", "{KISI@c1}'i ara", KISIA,
         [("ARAYUZ", "Kişiler", "Kişiler"), ("LISTE", "Ayşe Test", "{KISI@c1}"), ("LISTE", "Mehmet Test", "{KISI@c2}"),
          ("LISTE", "Ali Test", "{KISI@c8}")])
birlesik("U-002", ["TUTARLILIK", "KISI"], "ses", "aliyi ara", "{KISI:Ali}'i ara", KISIA,
         [("LISTE", "Ali Test", "{KISI@c8}"), ("LISTE", "Ali Testçi", "{KISI@c9}")])
birlesik("U-003", ["TUTARLILIK", "TEL", "KISI"], "yazi", "0599 000 00 02 numarasını kaydet", "{TEL:0599 000 00 02} numarasını kaydet", KISIA,
         [("LISTE", "Mehmet Test", "{KISI@c2}"), ("LISTE", "0599 000 00 02", "{TEL:0599 000 00 02}")])
birlesik("U-004", ["TUTARLILIK", "TEL"], "yazi", "05990000003'ü ara", "{TEL:05990000003}'i ara", TELA,
         [("LISTE", "+90 599 000 00 03", "{TEL:+90 599 000 00 03|05990000003}")])
birlesik("U-005", ["TUTARLILIK", "KISI", "METIN", "GIRIS"], "ses", "hasana mesaj at", "{KISI@c6}'e mesaj at", WA,
         [("LISTE", "Hasan Test", "{KISI@c6}"), ("LISTE", "Merhaba", "{METIN:Merhaba}"), ("DUZENLENEBILIR", "Merhaba", "{METIN:Merhaba}")])
birlesik("U-006", ["TUTARLILIK", "KISI", "METIN"], "ses", "zeynebe mesaj at", "{KISI@c3}'e mesaj at", WA,
         [("LISTE", "Zeynep Test", "{KISI@c3}"), ("LISTE", "Görüşürüz", "{METIN:Görüşürüz}"), ("LISTE", "Elif Test", "{KISI@c5}"),
          ("LISTE", "Görüşürüz", "{METIN:Görüşürüz}")])

# --- NIYET (long narration) ---
komut("N-001", ["NIYET", "KISI"], "ses",
      "şimdi şöyle bir durum var dün akşam komşumuz gülten test geldi telefonumu kurcaladı sesi kısıldı galiba sesi açar mısın",
      "şimdi şöyle bir durum var dün akşam komşumuz {METIN:gülten test geldi} telefonumu {METIN:kurcaladı} sesi {METIN:kısıldı galiba} sesi açar mısın",
      kip="NIYET")
komut("N-002", ["NIYET", "KISI"], "ses",
      "kızım bak bana dün mehmet test diye biri aradı numarası kayıtlı değil onu rehbere kaydeder misin acaba",
      "kızım bak bana dün {KISI@c2} diye biri aradı numarası kayıtlı değil onu rehbere kaydeder misin acaba", kip="NIYET")
komut("N-003", ["NIYET", "KISI"], "ses",
      "dün pazarda karşılaştığım nurhan test hanımın verdiği tarifi kaydettim onu bulamıyorum galeride olabilir mi bakar mısın",
      "dün pazarda {METIN:karşılaştığım} {KISI:Nurhan Test} hanımın {METIN:verdiği} tarifi kaydettim onu {METIN:bulamıyorum} galeride {METIN:olabilir} mi bakar mısın",
      kip="NIYET")
komut("N-004", ["NIYET", "KISI"], "yazi",
      "Merhaba, ben Gülsen Test, bugün torunum bana bir video gönderdi ama açılmıyor, nasıl açarım bunu söyler misin",
      "Merhaba, ben {KISI:Gülsen Test}, bugün torunum bana bir video gönderdi ama açılmıyor, nasıl açarım bunu söyler misin",
      kip="NIYET")

# --- NEGATIF (nothing to mask) ---
for i, (src, t) in enumerate([("ses", "ayarları aç"), ("ses", "sesi biraz aç"), ("ses", "wifiyi kapat"),
        ("yazi", "saat 7'de alarm kur"), ("ses", "parlaklığı artır"), ("ses", "son fotoğrafı göster"),
        ("ses", "yazıları büyüt"), ("ses", "hava durumunu söyle"), ("yazi", "112'yi ara"), ("ses", "eczaneyi ara"),
        ("ses", "annemi ara"), ("ses", "beş dakika sonra hatırlat"), ("yazi", "saat 14:30'da alarm kur"),
        ("ses", "sesi 15 yap"), ("ses", "babamı görüntülü ara"), ("ses", "on iki kere çalsın")], start=1):
    tags = ["NEGATIF"] + (["YAZIYLA_SAYI"] if t in ("beş dakika sonra hatırlat", "on iki kere çalsın") else [])
    komut("X-%03d" % i, tags, src, t, t)
ekran("X-017", ["NEGATIF"], AYAR, [("ARAYUZ", t, t) for t in ["Ayarlar", "Bağlantılar", "Ses ve titreşim", "Bildirimler", "Ekran"]]
      + [("OZET", "Açık", "Açık"), ("OZET", "Kapalı", "Kapalı")])

# --- GERI_COZME ---
GC = [
  ("Y-001", {"[KISI_1]": "Ayşe Test"}, [], "[KISI_1]'i arıyorum.", "Ayşe Test'i arıyorum."),
  ("Y-002", {"[KISI_1]": "Osman Test"}, [], "[KISI_1]'e mesaj yazayım mı?", "Osman Test'e mesaj yazayım mı?"),
  ("Y-003", {"[KISI_1]": "Ali"}, [], "[KISI_1]'in numarası iki kişide kayıtlı.", "Ali'nin numarası iki kişide kayıtlı."),
  ("Y-004", {"[TEL_1]": "0599 000 00 14"}, [], "[TEL_1]'i arayayım mı?", "0599 000 00 14'ü arayayım mı?"),
  ("Y-005", {"[TEL_1]": "0599 000 00 10"}, [], "[TEL_1]'e mesaj gönderiyorum.", "0599 000 00 10'a mesaj gönderiyorum."),
  ("Y-006", {"[ADRES_1]": "Deneme Mahallesi Papatya Sokak No 12"}, [], "[ADRES_1]'e taksi çağırıyorum.",
   "Deneme Mahallesi Papatya Sokak No 12'ye taksi çağırıyorum."),
  ("Y-007", {"[KISI_1]": "Ayşe Test", "[METIN_1]": "Yarın geliyorum"}, [], "[KISI_1]'e \"[METIN_1]\" yazıyorum.",
   "Ayşe Test'e \"Yarın geliyorum\" yazıyorum."),
  ("Y-008", {"[KISI_1]": "Ayşe Test"}, [], "[KISI_7]'yi arıyorum.", None),
  ("Y-009", {"[KISI_1]": "Hasan Test"}, [], "[KISI_1]'yle görüntülü arama başlatıyorum.", "Hasan Test'le görüntülü arama başlatıyorum."),
  ("Y-010", {"[KISI_1]": "Selin Teste"}, ["[KISI_1]"], "[KISI_1]'e mesaj gönderiyorum.", "Selin Teste mesaj gönderiyorum."),
  ("Y-011", {"[KISI_1]": "Osman Test"}, [], "[KISI_1]'den gelen mesajı okuyorum.", "Osman Test'ten gelen mesajı okuyorum."),
  ("Y-012", {"[KISI_1]": "Ayşe Test"}, [], "[KISI]'yi ara", None),
  ("Y-013", {"[KISI_1]": "Zeynep Test", "[KISI_2]": "Elif Test"}, [], "[KISI_2] mi [KISI_1] mi?", "Elif Test mi Zeynep Test mi?"),
]

# --- SON_DENETIM ---
SD = [
  ("Z-001", ["[KISI_1]'i ara"], {"[KISI_1]": "Ayşe Test"}, "GONDER", None),
  ("Z-002", ["Ayşe'yi ara"], {}, "ENGELLE", "REHBER_ADI"),
  ("Z-003", ["0599 000 00 01 numarasını ara"], {}, "ENGELLE", "TEL"),
  ("Z-014", ["müşteri no 77889900"], {}, "ENGELLE", "RAKAM_DIZISI"),
  ("Z-004", ["ayse.test@example.com adresine yaz"], {}, "ENGELLE", "EPOSTA"),
  ("Z-005", ["[KISI_2]'yi ara"], {"[KISI_1]": "Ayşe Test"}, "ENGELLE", "BILINMEYEN_YER_TUTUCU"),
  ("Z-006", ["sıfır beş yüz doksan dokuz ara"], {}, "ENGELLE", "YAZIYLA_SAYI"),
  ("Z-007", ["tansiyon ilacımı hatırlat"], {}, "ENGELLE", "OZEL_TERIM"),
  ("Z-008", ["Sohbetler", "[KISI_1]", "[METIN_1]", "14:32"], {"[KISI_1]": "Ayşe Test", "[METIN_1]": "Yarın pazara gidelim mi?"}, "GONDER", None),
  ("Z-009", ["Doğrulama kodunuz [SAYI_1]"], {"[SAYI_1]": "482913"}, "ENGELLE", "DOGRULAMA_KODU"),
  ("Z-010", ["saat 14:30'da [KISI_1]'e hatırlat"], {"[KISI_1]": "Elif Test"}, "GONDER", None),
  ("Z-011", ["Deneme Mahallesi Papatya Sokak adresine git"], {}, "ENGELLE", "ADRES"),
  ("Z-012", ["$IB1"], {}, "ENGELLE", "IBAN"),
  ("Z-013", ["Hesap [IBAN_1], alıcı Mehmet"], {"[IBAN_1]": "$IB2"}, "ENGELLE", "REHBER_ADI"),
]

# ---------- template rendering ----------
TPL = re.compile(r"\{(KISI@(c\d+)|([A-Z]+)(~?):([^{}|]+)(?:\|([^{}]+))?)\}")

def tr_lower(s):
    return s.replace("I", "ı").replace("İ", "i").lower()

def key_for(cat, val):
    v = tr_lower(val)
    if cat == "TEL":
        d = re.sub(r"\D", "", v) if re.search(r"\d", v) else words_to_digits(v)
        if d.startswith("0090"): d = d[4:]
        elif d.startswith("90") and len(d) == 12: d = d[2:]
        elif d.startswith("0"): d = d[1:]
        return d
    if cat in ("IBAN", "KART", "TCKN"):
        return re.sub(r"[^0-9a-z]", "", v)
    return v

NUMW = SOZLUK["sayi_sozcukleri"]
def words_to_digits(text):
    # Mirrors MASKELEME.md §5.1: greedy Turkish number parse; a word that cannot
    # continue the current number starts a new digit group; "sıfır" is its own group.
    groups, cur = [], None
    def can_continue(cur, n):
        if n == 100: return (1 <= cur <= 9) or (cur >= 1000 and cur % 1000 == 0)
        if n == 1000: return cur < 1000
        if n == 1000000: return cur < 1000
        if 10 <= n < 100: return cur >= 100 and cur % 100 == 0
        if 1 <= n <= 9: return cur >= 10 and cur % 10 == 0
        return False
    for w in text.split():
        n = NUMW[w]
        if n == 0:
            if cur is not None: groups.append(cur)
            groups.append(0); cur = None
        elif cur is None:
            cur = n
        elif can_continue(cur, n):
            cur = cur * n if (n >= 100 and cur < n and not (n == 100 and cur >= 1000)) else cur + n
        else:
            groups.append(cur); cur = n
    if cur is not None: groups.append(cur)
    return "".join(str(g) for g in groups)

class Numberer:
    def __init__(self):
        self.counters = collections.Counter(); self.keys = {}; self.esleme = {}; self.ekli = []
    def ph(self, cat, key, value, ekli=False):
        k = (cat, key)
        if k not in self.keys:
            self.counters[cat] += 1
            p = "[%s_%d]" % (cat, self.counters[cat])
            self.keys[k] = p; self.esleme[p] = value
            if ekli: self.ekli.append(p)
        return self.keys[k]
    def render(self, tpl):
        def rep(m):
            if m.group(2):
                return self.ph("KISI", m.group(2), CNAME[m.group(2)])
            cat, ekli, val, keysrc = m.group(3), m.group(4) == "~", m.group(5), m.group(6)
            if cat == "KISI" and not keysrc:
                # Non-contact mention: key is the folded surface, value title-cased surface.
                k = tr_lower(val)
                # A mention that is the first name of a contact maps to that contact if unique; handled by template authoring.
                return self.ph("KISI", k, val, ekli)
            return self.ph(cat, key_for(cat, keysrc or val), val, ekli)
        return TPL.sub(rep, tpl)

ornekler = []
for kind, i, tags, a, b, out, kw in K:
    e = {"id": i, "tur": kind, "kategoriler": tags}
    n = Numberer()
    if kind == "komut":
        e["kaynak"] = a; e["girdi"] = subst(b)
        if isinstance(out, tuple):
            e["beklenen"] = {"sonuc": "ENGELLE", "neden": out[1]}
        else:
            e["beklenen"] = {"sonuc": "GONDER", "kip": kw.get("kip", "TAM"), "cikti": n.render(subst(out)), "esleme": n.esleme}
    elif kind == "ekran":
        e["paket"] = a; e["dugumler"] = [{"rol": r, "metin": subst(t)} for r, t, _ in b]
        if kw.get("engel"):
            e["beklenen"] = {"sonuc": "ENGELLE", "neden": kw["engel"]}
        else:
            e["beklenen"] = {"sonuc": "GONDER", "dugumler": [n.render(subst(x)) for _, _, x in b], "esleme": n.esleme}
    else:
        cmd, pkg, nodes = b
        e["kaynak"] = a; e["girdi"] = subst(cmd); e["paket"] = pkg
        e["dugumler"] = [{"rol": r, "metin": subst(t)} for r, t, _ in nodes]
        if kw.get("engel"):
            e["beklenen"] = {"sonuc": "ENGELLE", "neden": kw["engel"]}
        else:
            c = n.render(subst(out))
            ds = [n.render(subst(x)) for _, _, x in nodes]
            e["beklenen"] = {"sonuc": "GONDER", "kip": "TAM", "cikti": c, "dugumler": ds, "esleme": n.esleme}
    if n.ekli: e["beklenen"]["ekli"] = n.ekli
    ornekler.append(e)

for i, es, ekli, yanit, bek in GC:
    e = {"id": i, "tur": "geri_cozme", "kategoriler": ["GERI_COZME"], "esleme": es, "yanit": yanit}
    if ekli: e["ekli"] = ekli
    e["beklenen"] = {"sonuc": "TAMAM", "metin": bek} if bek else {"sonuc": "RED", "neden": "BILINMEYEN_YER_TUTUCU"}
    ornekler.append(e)
for i, giden, es, sonuc, neden in SD:
    e = {"id": i, "tur": "son_denetim", "kategoriler": ["SON_DENETIM"], "giden": [subst(g) for g in giden],
         "esleme": {k: subst(v) for k, v in es.items()}, "beklenen": {"sonuc": sonuc}}
    if neden: e["beklenen"]["neden"] = neden
    ornekler.append(e)

doc = {
  "surum": 1,
  "aciklama": "G-03 maskeleme test korpusu. Kurallar: docs/MASKELEME.md. Tüm kişi, numara, adres ve hesap değerleri UYDURMADIR.",
  "uydurma_deger_araliklari": {
    "kisi": "Yalnız 'Test' soyadlı/ekli uydurma kişiler",
    "telefon": "0599 000 xx xx (tahsis edilmemiş GSM öneki) ve 0212 000 xx xx (0 ile başlayan abone numarası geçersizdir)",
    "tckn": "1000000xxxx dizisi, sağlama basamakları geçerli, test amaçlı",
    "iban": "TR + banka kodu 00000 (hiçbir bankaya ait değil), mod-97 geçerli",
    "kart": "4000 0000 0000 0xxx test BIN aralığı, Luhn geçerli",
    "eposta": "example.com / example.org / example.net (RFC 2606 ayrılmış alan adları)",
    "adres": "Deneme/Örnek/Test/Lale/Papatya adlı uydurma yerler; koordinatlar uydurma",
  },
  "ayarlar": AYARLAR,
  "rehber": [{"id": cid, "ad": ad, "telefonlar": [tel]} for cid, ad, tel in REHBER],
  "sozlukler": SOZLUK,
  "ornekler": ornekler,
}

# ---------- validation ----------
errors, warns = [], []
ids = [e["id"] for e in ornekler]
dup = [x for x, c in collections.Counter(ids).items() if c > 1]
if dup: errors.append("duplicate ids %s" % dup)
PH = re.compile(r"\[[A-ZÇĞİÖŞÜ]+_\d+\]")
def outputs(e):
    b = e["beklenen"]; o = []
    if "cikti" in b: o.append(b["cikti"])
    o += b.get("dugumler", [])
    return o
def digit_runs(s):
    s = PH.sub(" ", s)
    s = re.sub(r"(?<=\d)[ .\-/()]{1}(?=\d)", "", s)
    return [m for m in re.findall(r"\d+", s) if len(m) >= 4]
for e in ornekler:
    b = e["beklenen"]
    if b.get("sonuc") != "GONDER" or e["tur"] in ("son_denetim",): continue
    outs = outputs(e)
    used = set(PH.findall(" ".join(outs)))
    if used != set(b["esleme"].keys()):
        errors.append("%s placeholder/esleme mismatch %s vs %s" % (e["id"], used, set(b["esleme"])))
    for s in outs:
        if digit_runs(s): errors.append("%s digit run left: %s" % (e["id"], s))
    low = tr_lower(" ".join(outs))
    for p, v in b["esleme"].items():
        if tr_lower(v) in low: errors.append("%s leak %s=%s" % (e["id"], p, v))
        for w in re.findall(r"\w+", tr_lower(v)):
            if len(w) >= 4 and w not in ("test", "teste", "testi", "testçi") and not w.isdigit() and re.search(r"\b" + re.escape(w) + r"\b", low):
                warns.append("%s word '%s' of %s still in output" % (e["id"], w, p))
for e in ornekler:
    if e["tur"] == "komut" and "kategoriler" in e and "TEL" in e["kategoriler"] and e["beklenen"]["sonuc"] == "GONDER":
        for p, v in e["beklenen"]["esleme"].items():
            if p.startswith("[TEL"):
                d = key_for("TEL", v)
                if not (d.startswith("599000") or d.startswith("212000")): errors.append("%s phone not in fake range %s" % (e["id"], d))
# category counts
cnt = collections.Counter(t for e in ornekler for t in e["kategoriler"])
PLAN_CATS = ["KISI", "TEL", "EPOSTA", "TCKN", "IBAN", "KART", "ADRES", "SAYI", "METIN", "GIRIS", "OZEL"]
for c in PLAN_CATS:
    if cnt[c] < 10: errors.append("category %s only %d" % (c, cnt[c]))
if len(ornekler) < 150: errors.append("only %d examples" % len(ornekler))
# all contact names contain Test
for cid, ad, _ in REHBER:
    if "Test" not in ad: errors.append("contact without Test: " + ad)
# number-word sanity for TEL spoken
assert words_to_digits("sıfır beş yüz doksan dokuz sıfır sıfır sıfır sıfır sıfır on sekiz") == "05990000018"
assert words_to_digits("sıfır iki yüz on iki sıfır sıfır sıfır sıfır sıfır yirmi altı") == "02120000026"
assert words_to_digits("iki bin yüz on iki") == "2112", words_to_digits("iki bin yüz on iki")
assert tckn_valid(re.sub(r"\D", "", "".join(str(WORDS.index(w)) for w in spell_digits(TC[3]).split())))

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(doc, f, ensure_ascii=False, indent=1)
    f.write("\n")
print("examples:", len(ornekler))
print("counts:", dict(sorted(cnt.items())))
print("TC:", TC[1], TC[3], "IB:", IB[1], "KR:", KR[1])
for w in warns: print("WARN", w)
for x in errors: print("ERROR", x)
sys.exit(1 if errors else 0)
