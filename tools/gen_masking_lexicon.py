import json, sys

corpus, out = sys.argv[1], sys.argv[2]
d = json.load(open(corpus, encoding='utf-8'))
s = d['sozlukler']; a = d['ayarlar']

def q(x): return json.dumps(x, ensure_ascii=False)
def lst(xs, indent='        '):
    lines, cur = [], ''
    for x in xs:
        item = q(x) + ', '
        if len(cur) + len(item) > 100:
            lines.append(indent + cur.rstrip()); cur = ''
        cur += item
    if cur: lines.append(indent + cur.rstrip())
    return 'listOf(\n' + '\n'.join(lines) + '\n    )'
def lstl(xss): return 'listOf(' + ', '.join('listOf(' + ', '.join(q(x) for x in xs) + ')' for xs in xss) + ')'
def mapl(m): return 'mapOf(\n' + '\n'.join('        %s to %s,' % (q(k), 'listOf(' + ', '.join(q(x) for x in v) + ')') for k, v in m.items()) + '\n    )'

body = f'''package tr.yasliasistan.core.masking

// GENERATED from core/src/test/resources/mask-corpus.json ("sozlukler" + "ayarlar"),
// which docs/MASKELEME.md declares binding. Regenerate with
// `python tools/gen_masking_lexicon.py core/src/test/resources/mask-corpus.json <this file>`.
// Do not edit by hand: MaskingLexiconTest fails
// when this file and the corpus drift apart.

/** The default Turkish lexicon of docs/MASKELEME.md. */
internal val DefaultMaskingLexicon = MaskingLexicon(
    commandWords = {lst(s['komut'])},
    kinship = {lst(s['akrabalik'])},
    honorifics = {lst(s['hitap'])},
    suffixes = {mapl(s['ekler'])},
    softening = mapOf({', '.join("'%s' to '%s'" % (k, v) for k, v in s['yumusama'].items())}),
    personSlotTriggers = {mapl(s['kisi_yuvasi_tetikleyicileri'])},
    dictationBefore = {lst(s['dikte']['once_isaretleri'])},
    dictationAfter = {lst(s['dikte']['sonra_isaretleri'])},
    dictationColonWords = {lst(s['dikte']['iki_nokta_sozcukleri'])},
    dictationVerbs = {lst(s['dikte']['fiiller'])},
    addressAnchors = {lst(s['adres']['anahtarlar'])},
    addressNumberMarkers = {lst(s['adres']['numara_gostergeleri'])},
    numberWords = mapOf(
{chr(10).join('        %s to %dL,' % (q(k), v) for k, v in s['sayi_sozcukleri'].items())}
    ),
    sensitiveRoots = {lst(s['ozel_terimler']['tek'])},
    sensitivePhrases = {lstl(s['ozel_terimler']['obek'])},
    secretPhrases = {lstl(s['gizli']['tek_basina'])},
    secretWithDigits = {lst(s['gizli']['rakamla_gizli_bilgi'])},
    codeWithDigits = {lst(s['gizli']['rakamla_dogrulama_kodu'])},
    codeExceptions = {lstl(s['gizli']['haric'])},
    statusWords = {lst(s['durum'])},
    emailTlds = {lst(s['eposta_tld'])},
    settings = MaskingSettings(
        intentModeWordThreshold = {a['niyet_esigi_sozcuk']},
        minDigitRun = {a['en_kisa_rakam_dizisi']},
        numberWordThreshold = {a['sayi_sozcugu_esigi']},
        contextMaxWords = {a['baglam_en_fazla_sozcuk']},
        addressMaxLeftWords = {a['adres_sola_en_fazla']},
        emailMaxLeftWords = {a['eposta_sola_en_fazla']},
        dictionaryPrefixMinLength = {a['sozluk_onek_en_kisa']},
        secretMinDigits = {a['gizli_rakam_en_kisa']},
    ),
)
'''
open(out, 'w', encoding='utf-8', newline='\n').write(body)
print('ok', s['yumusama'])
