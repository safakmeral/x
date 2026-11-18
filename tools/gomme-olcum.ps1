# G-43 [Cihaz]: embedding model measurement on the connected phone, one command.
# Downloads the model if needed (tools\gomme-model-indir.ps1), builds and installs the app and its
# test APK, pushes the model to Android/data/tr.yasliasistan.app/files/gomme/ (once), runs
# EmbeddingBenchmarkTest and copies the numbers to docs\olcum\sonuclar\gomme-<run>\.
#
#   .\tools\gomme-olcum.ps1
#   .\tools\gomme-olcum.ps1 -Threads "2" -Tekrar 5
# Only fixed test sentences are used (app/src/androidTest/assets/gomme/ciftler.tsv); no personal data.
param(
    [string] $Threads = "1,2,4",
    [int] $Tekrar = 3,
    [string] $Calistirma = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [switch] $DerlemeYok
)

$ErrorActionPreference = "Stop"
$kok = Split-Path -Parent $PSScriptRoot
$paket = "tr.yasliasistan.app"
$cihazKok = "/sdcard/Android/data/$paket/files"
$apk = Join-Path $kok "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $kok "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$modeller = Join-Path $kok "models\gomme"

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

& (Join-Path $PSScriptRoot "gomme-model-indir.ps1")

if (-not $DerlemeYok) {
    Write-Host "Uygulama ve test APK'sı derleniyor..."
    & (Join-Path $kok "gradlew.bat") -p $kok :app:assembleDebug :app:assembleDebugAndroidTest -q
    if ($LASTEXITCODE -ne 0) { throw "Derleme başarısız." }
}

Write-Host "Kuruluyor (kaldırmadan; telefondaki model ve ayarlar korunur)..."
& $adb install -r -t $apk | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Uygulama kurulamadı." }
& $adb install -r -t $testApk | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Test APK'sı kurulamadı." }

& $adb shell "mkdir -p $cihazKok/gomme" | Out-Null
foreach ($ad in @("model_qint8_avx512_vnni.onnx", "sentencepiece.bpe.model")) {
    $yerel = Join-Path $modeller $ad
    $boyut = (Get-Item $yerel).Length
    $uzak = (& $adb shell "stat -c %s $cihazKok/gomme/$ad 2>/dev/null")
    if ("$uzak".Trim() -eq "$boyut") { Write-Host "Telefonda zaten var: $ad"; continue }
    Write-Host "Gönderiliyor: $ad"
    & $adb push $yerel "$cihazKok/gomme/$ad" | Out-Host
}

Write-Host "Ölçüm başlıyor (çalıştırma: $Calistirma)..."
& $adb shell am instrument -w -r -e class tr.yasliasistan.app.embedding.EmbeddingBenchmarkTest `
    -e run $Calistirma -e threads $Threads -e repeats "$Tekrar" "$paket.test/androidx.test.runner.AndroidJUnitRunner" |
    Select-String -Pattern "INSTRUMENTATION_STATUS: (stream|stack)|OK \(|FAILURES|Tests run|shortMsg|Model yok" | Out-Host

$hedef = Join-Path $kok "docs\olcum\sonuclar"
New-Item -ItemType Directory -Force $hedef | Out-Null
& $adb pull "$cihazKok/gomme-sonuc/$Calistirma" (Join-Path $hedef "gomme-$Calistirma") | Out-Host
$sonuc = Join-Path $hedef "gomme-$Calistirma\sonuc.txt"
if (Test-Path $sonuc) {
    Get-Content $sonuc -Encoding UTF8 | Out-Host
    Write-Host "Sonuçlar: $(Split-Path $sonuc). Bu sayıları docs\olcum\gomme.md 'Cihaz' bölümüne yazın."
} else {
    Write-Host "Sonuç bulunamadı. Ayrıntı: adb logcat -d -s TestRunner"
}
