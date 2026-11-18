# Speech recognition measurement on the connected phone (PLAN G-13/G-14).
# Builds the :speech test APK, installs it WITHOUT uninstalling (so models pushed earlier stay),
# runs AsrBenchmarkTest and copies the results into docs/olcum/sonuclar/<run>/.
#
# Examples:
#   .\tools\asr-olcum.ps1 -Motorlar tiny -Limit 5
#   .\tools\asr-olcum.ps1 -Motorlar "system,tiny,base,small" -ModelKlasoru C:\modeller
#   .\tools\asr-olcum.ps1 -Motorlar tiny -AudioCtx "0,768" -Beam "1,5"
#   .\tools\asr-olcum.ps1 -Motorlar tiny -Veri klasor -KayitKlasoru C:\kayitlar   (manifest.tsv + WAVs)
# Human recordings pushed with -KayitKlasoru are deleted from the phone after the run (V-15).
param(
    [string] $Motorlar = "tiny",
    [ValidateSet("tts", "klasor", "hepsi")] [string] $Veri = "tts",
    [int] $Limit = 40,
    [string] $Kosullar = "temiz,gurultulu,sessiz",
    [int] $Sessiz = 5,
    [int] $Snr = 5,
    [string] $AudioCtx = "0",
    [string] $Threads = "0",
    [string] $Beam = "1",
    [string] $Calistirma = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string] $ModelKlasoru = "",
    [string] $KayitKlasoru = "",
    [switch] $KlipleriDisaAktar,
    [switch] $DerlemeYok
)

$ErrorActionPreference = "Stop"
$kok = Split-Path -Parent $PSScriptRoot
$paket = "tr.yasliasistan.speech.test"
$cihazKok = "/sdcard/Android/data/$paket/files"
$apk = Join-Path $kok "speech\build\outputs\apk\androidTest\debug\speech-debug-androidTest.apk"
$veriKlasoru = "asr-veri"

function Bul-Adb {
    $aday = @()
    if ($env:ANDROID_HOME) { $aday += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }
    if ($env:LOCALAPPDATA) { $aday += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
    foreach ($a in $aday) { if (Test-Path $a) { return $a } }
    $komut = Get-Command adb -ErrorAction SilentlyContinue
    if ($komut) { return $komut.Source }
    throw "adb bulunamadı. Android Studio'nun platform-tools klasörünü kurun (docs/KURULUM.md)."
}

$adb = Bul-Adb
$cihazlar = (& $adb devices) | Where-Object { $_ -match "\tdevice$" }
if (-not $cihazlar) { throw "Telefon bağlı değil ya da USB hata ayıklama izni verilmedi (adb devices boş)." }

if (-not $DerlemeYok) {
    Write-Host "Test APK'sı derleniyor..."
    & (Join-Path $kok "gradlew.bat") -p $kok :speech:assembleDebugAndroidTest -q
    if ($LASTEXITCODE -ne 0) { throw "Derleme başarısız." }
}

Write-Host "Kuruluyor (kaldırmadan; telefondaki modeller korunur)..."
& $adb install -r -t $apk | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Kurulum başarısız." }

# Whisper models: pushed only if given locally and not already on the phone with the same size.
if ($ModelKlasoru) {
    foreach ($m in ($Motorlar -split ",")) {
        $ad = $m.Trim()
        if ($ad -eq "system" -or -not $ad) { continue }
        if (-not $ad.EndsWith(".bin")) { $ad = "ggml-$ad.bin" }
        $yerel = Join-Path $ModelKlasoru $ad
        if (-not (Test-Path $yerel)) { Write-Host "Model yerelde yok, atlandı: $ad"; continue }
        $boyut = (Get-Item $yerel).Length
        $uzak = (& $adb shell "stat -c %s $cihazKok/whisper/$ad 2>/dev/null").Trim()
        if ($uzak -eq "$boyut") { Write-Host "Model telefonda zaten var: $ad"; continue }
        & $adb shell "mkdir -p $cihazKok/whisper" | Out-Null
        Write-Host "Model gönderiliyor: $ad"
        & $adb push $yerel "$cihazKok/whisper/$ad" | Out-Host
    }
}

$kayitGonderildi = $false
if ($KayitKlasoru) {
    if (-not (Test-Path (Join-Path $KayitKlasoru "manifest.tsv"))) { throw "$KayitKlasoru içinde manifest.tsv yok." }
    & $adb shell "rm -rf $cihazKok/$veriKlasoru; mkdir -p $cihazKok/$veriKlasoru" | Out-Null
    Get-ChildItem $KayitKlasoru -File | Where-Object { $_.Extension -in ".wav", ".tsv" } | ForEach-Object {
        & $adb push $_.FullName "$cihazKok/$veriKlasoru/$($_.Name)" | Out-Null
    }
    $kayitGonderildi = $true
}

$arg = @(
    "-e", "asr", "1", "-e", "engines", $Motorlar, "-e", "data", $Veri, "-e", "limit", "$Limit",
    "-e", "conditions", $Kosullar, "-e", "silent", "$Sessiz", "-e", "snr", "$Snr",
    "-e", "audioCtx", $AudioCtx, "-e", "threads", $Threads, "-e", "beam", $Beam,
    "-e", "dataDir", $veriKlasoru, "-e", "run", $Calistirma
)
if ($KlipleriDisaAktar) { $arg += @("-e", "export", "1") }

Write-Host "Ölçüm başlıyor (çalıştırma: $Calistirma). Uzun sürebilir; ilerleme: adb logcat -s G13Asr"
try {
    & $adb shell am instrument -w -r @arg -e class tr.yasliasistan.speech.asr.AsrBenchmarkTest "$paket/androidx.test.runner.AndroidJUnitRunner" |
        Select-String -Pattern "INSTRUMENTATION_STATUS: (stream|stack)|OK \(|FAILURES|Tests run|shortMsg" | Out-Host
} finally {
    if ($kayitGonderildi) {
        & $adb shell "rm -rf $cihazKok/$veriKlasoru" | Out-Null
        Write-Host "Telefona gönderilen kayıtlar silindi."
    }
}

$hedef = Join-Path $kok "docs\olcum\sonuclar"
New-Item -ItemType Directory -Force $hedef | Out-Null
& $adb pull "$cihazKok/asr-olcum/$Calistirma" $hedef | Out-Host
$ozet = Join-Path $hedef "$Calistirma\ozet.md"
if (Test-Path $ozet) {
    Write-Host ""
    Get-Content $ozet -Encoding UTF8 | Out-Host
    Write-Host "Sonuçlar: $(Join-Path $hedef $Calistirma)"
} else {
    Write-Host "Sonuç bulunamadı. Ayrıntı: adb logcat -d -s G13Asr TestRunner"
}
