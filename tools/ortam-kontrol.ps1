<#
.SYNOPSIS
  Gelistirme ortamini kontrol eder (G-01). Hicbir sey kurmaz, hicbir ayari degistirmez.
.DESCRIPTION
  JDK 17/21 (oncelik Android Studio JBR), Android SDK, platform-tools/adb, build-tools,
  SDK platformu, NDK, CMake, lisans ve `adb devices` kontrol eder. Her kalem icin TAMAM/EKSIK yazar.
  -CihazBilgisi ile bagli telefondan yalniz OKUMA yapar (surum, RAM, konusma tanima, TTS).
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\ortam-kontrol.ps1 -CihazBilgisi
#>
param(
    [switch] $CihazBilgisi,
    [int] $HedefSdk = 36
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
. (Join-Path $PSScriptRoot 'ortam-fonksiyonlar.ps1')

$script:EksikSayisi = 0

function Write-Kalem {
    param([string] $Ad, [bool] $Tamam, [string] $Ayrinti, [string] $Cozum = '')
    if ($Tamam) {
        Write-Host ("[TAMAM] {0}: {1}" -f $Ad, $Ayrinti) -ForegroundColor Green
    } else {
        $script:EksikSayisi++
        Write-Host ("[EKSİK] {0}: {1}" -f $Ad, $Ayrinti) -ForegroundColor Yellow
        if ($Cozum) { Write-Host ("        Çözüm: {0}" -f $Cozum) }
    }
}

function Get-JdkAdaylari {
    # Ordered: Android Studio JBR first, then JAVA_HOME, then other installed JDKs.
    $paths = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        $env:JAVA_HOME
    )
    foreach ($root in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path $root) { $paths += (Get-ChildItem $root -Directory | ForEach-Object FullName) }
    }
    $seen = @{}
    foreach ($p in $paths) {
        if (-not $p -or $seen.ContainsKey($p)) { continue }
        $seen[$p] = $true
        $release = Join-Path $p 'release'
        if ((Test-Path (Join-Path $p 'bin\java.exe')) -and (Test-Path $release)) {
            [pscustomobject]@{ Path = $p; Major = (Get-JavaMajorFromRelease (Get-Content $release)) }
        }
    }
}

function Get-AltKlasorler {
    param([string] $Path)
    if (Test-Path $Path) { return @(Get-ChildItem $Path -Directory | ForEach-Object Name) }
    return @()
}

Write-Host "== Geliştirme ortamı kontrolü (yalnız okuma) =="

# 1) JDK
$adaylar = @(Get-JdkAdaylari)
$jdk = Select-UygunJdk -Candidates $adaylar
foreach ($a in $adaylar) { Write-Host ("        Bulunan JDK: {0} (sürüm {1})" -f $a.Path, $a.Major) }
if ($jdk) {
    Write-Kalem 'JDK (17/21)' $true ("{0} (sürüm {1})" -f $jdk.Path, $jdk.Major)
    $javaHomeOk = ($env:JAVA_HOME -and ((Resolve-Path $env:JAVA_HOME -ErrorAction SilentlyContinue).Path -eq (Resolve-Path $jdk.Path).Path))
    Write-Kalem 'JAVA_HOME' $javaHomeOk ("şu an: '{0}'" -f $env:JAVA_HOME) ("JAVA_HOME'u '{0}' yapın (docs/KURULUM.md)." -f $jdk.Path)
} else {
    Write-Kalem 'JDK (17/21)' $false 'Uygun JDK yok (JDK 25 Android Gradle eklentisi için kullanılmaz).' 'Android Studio''yu kurun; içindeki JBR kullanılır.'
}

# 2) Android SDK
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$sdkVar = Test-Path $sdk
Write-Kalem 'Android SDK' $sdkVar $sdk 'Android Studio → SDK Manager ile SDK''yı kurun.'
Write-Kalem 'ANDROID_HOME' ([bool]$env:ANDROID_HOME) ("şu an: '{0}'" -f $env:ANDROID_HOME) ("ANDROID_HOME'u '{0}' yapın (docs/KURULUM.md). Zorunlu değil: proje local.properties ile de bulur." -f $sdk)

$adb = Join-Path $sdk 'platform-tools\adb.exe'
Write-Kalem 'platform-tools (adb)' (Test-Path $adb) $adb 'SDK Manager → SDK Tools → Android SDK Platform-Tools.'

$bt = Get-AltKlasorler (Join-Path $sdk 'build-tools')
Write-Kalem "build-tools (>= $HedefSdk)" (Test-EnAzSurum $bt $HedefSdk) ("bulunan: {0}" -f ($bt -join ', ')) 'SDK Manager → SDK Tools → Android SDK Build-Tools.'

$pf = Get-AltKlasorler (Join-Path $sdk 'platforms')
Write-Kalem "SDK platformu (android-$HedefSdk)" (Test-EnAzSurum $pf $HedefSdk) ("bulunan: {0}" -f ($pf -join ', ')) "SDK Manager → SDK Platforms → Android $HedefSdk."

$ndk = Get-AltKlasorler (Join-Path $sdk 'ndk')
Write-Kalem 'NDK' ($ndk.Count -gt 0) ("bulunan: {0}; en yeni: {1}" -f ($ndk -join ', '), (Get-EnYeniSurum $ndk)) 'SDK Manager → SDK Tools → NDK (Side by side).'

$cmake = Get-AltKlasorler (Join-Path $sdk 'cmake')
Write-Kalem 'CMake' ($cmake.Count -gt 0) ("bulunan: {0}" -f ($cmake -join ', ')) 'SDK Manager → SDK Tools → CMake.'

Write-Kalem 'SDK lisansı' (Test-Path (Join-Path $sdk 'licenses\android-sdk-license')) 'android-sdk-license' 'Android Studio''da SDK Manager''ı bir kez açıp lisansı kabul edin.'

# 3) Phone
$hazirCihaz = $null
if (Test-Path $adb) {
    $cihazlar = ConvertFrom-AdbDevices (& $adb devices -l 2>$null)
    if ($cihazlar.Count -eq 0) {
        Write-Kalem 'Telefon (adb)' $false 'Bağlı cihaz yok.' 'Telefonu USB ile bağlayın, USB hata ayıklamayı açın, telefondaki "İzin ver" sorusunu onaylayın (docs/KURULUM.md).'
    }
    foreach ($c in $cihazlar) {
        switch ($c.State) {
            'device' { Write-Kalem 'Telefon (adb)' $true ("{0} {1}" -f $c.Serial, $c.Model); if (-not $hazirCihaz) { $hazirCihaz = $c } }
            'unauthorized' { Write-Kalem 'Telefon (adb)' $false ("{0}: yetki bekliyor" -f $c.Serial) 'Telefon ekranındaki "USB hata ayıklamaya izin ver" sorusuna "İzin ver" deyin.' }
            default { Write-Kalem 'Telefon (adb)' $false ("{0}: durum '{1}'" -f $c.Serial, $c.State) 'Kabloyu çıkarıp takın; olmazsa "adb kill-server" çalıştırın.' }
        }
    }
}

# 4) Optional read-only device report
if ($CihazBilgisi -and $hazirCihaz) {
    $s = @('-s', $hazirCihaz.Serial)
    function Invoke-Kabuk {
        # Read-only shell command; stderr is ignored so one failing query does not stop the report.
        param([string] $Komut)
        $ErrorActionPreference = 'Continue'
        & $adb @s shell $Komut 2>$null
    }
    Write-Host "`n== Telefon bilgisi (yalnız okuma) =="
    Write-Host ("Model       : {0} {1}" -f (Invoke-Kabuk 'getprop ro.product.manufacturer'), (Invoke-Kabuk 'getprop ro.product.model'))
    Write-Host ("Android     : {0} (API {1})" -f (Invoke-Kabuk 'getprop ro.build.version.release'), (Invoke-Kabuk 'getprop ro.build.version.sdk'))
    Write-Host ("One UI      : {0}" -f (Invoke-Kabuk 'getprop ro.build.version.oneui'))
    Write-Host ("RAM         : {0} GB (MemTotal)" -f (Get-MemTotalGb (Invoke-Kabuk 'cat /proc/meminfo')))
    Write-Host ("Dil/bölge   : {0}" -f (Invoke-Kabuk 'getprop persist.sys.locale'))
    Write-Host ("Varsayılan tanıyıcı : {0}" -f (Invoke-Kabuk 'settings get secure voice_recognition_service'))
    Write-Host ("Tanıma servisleri   : {0}" -f ((Get-PaketAdlari (Invoke-Kabuk 'pm query-services --user 0 --brief -a android.speech.RecognitionService')) -join ', '))
    Write-Host ("Varsayılan TTS      : {0} / dil: {1}" -f (Invoke-Kabuk 'settings get secure tts_default_synth'), (Invoke-Kabuk 'settings get secure tts_default_locale'))
    Write-Host ("TTS motorları       : {0}" -f ((Get-PaketAdlari (Invoke-Kabuk 'pm query-services --user 0 --brief -a android.intent.action.TTS_SERVICE')) -join ', '))
    # Only speech/TTS/accessibility packages are printed; the full app list is personal data.
    $ilgili = Select-IlgiliPaketler (Get-PaketAdlari (Invoke-Kabuk 'pm list packages --user 0'))
    Write-Host ("İlgili paketler     : {0}" -f ($ilgili -join ', '))
    $samsungTr = @($ilgili | Where-Object { $_ -match '^com\.samsung\.SMT\.lang_tr' })
    Write-Host ("Samsung TTS Türkçe ses paketi: {0}" -f $(if ($samsungTr.Count -gt 0) { $samsungTr -join ', ' } else { 'YOK' }))
    Write-Host "Not: Türkçe çevrimdışı tanıma ve Türkçe TTS sesinin varlığı adb ile kesin okunamaz; uygulama içinden (G-07, G-08) API ile doğrulanır."
} elseif ($CihazBilgisi) {
    Write-Host "`nTelefon bilgisi okunamadı: hazır cihaz yok."
}

Write-Host ''
if ($script:EksikSayisi -eq 0) {
    Write-Host 'SONUÇ: Tüm kalemler TAMAM.' -ForegroundColor Green
} else {
    Write-Host ("SONUÇ: {0} kalem EKSİK (yukarıdaki çözümlere bakın)." -f $script:EksikSayisi) -ForegroundColor Yellow
}
exit $script:EksikSayisi
