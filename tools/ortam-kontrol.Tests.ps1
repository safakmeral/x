# Pester 3.4 tests (built into Windows PowerShell 5.1).
# Run: powershell -ExecutionPolicy Bypass -Command "Invoke-Pester tools\ortam-kontrol.Tests.ps1"

. (Join-Path $PSScriptRoot 'ortam-fonksiyonlar.ps1')

Describe 'Get-JavaMajorFromRelease' {
    It 'reads modern versions' {
        Get-JavaMajorFromRelease @('IMPLEMENTOR="X"', 'JAVA_VERSION="21.0.10"') | Should Be 21
        Get-JavaMajorFromRelease @('JAVA_VERSION="25.0.3"') | Should Be 25
        Get-JavaMajorFromRelease @('JAVA_VERSION="17"') | Should Be 17
    }
    It 'reads legacy 1.8 as 8' {
        Get-JavaMajorFromRelease @('JAVA_VERSION="1.8.0_402"') | Should Be 8
    }
    It 'returns 0 when missing' {
        Get-JavaMajorFromRelease @('FOO=bar') | Should Be 0
    }
}

Describe 'Select-UygunJdk' {
    It 'skips JDK 25 and picks the first 17/21 in order' {
        $c = @(
            [pscustomobject]@{ Path = 'jdk25'; Major = 25 },
            [pscustomobject]@{ Path = 'jbr'; Major = 21 },
            [pscustomobject]@{ Path = 'jdk17'; Major = 17 }
        )
        (Select-UygunJdk $c).Path | Should Be 'jbr'
    }
    It 'returns null when nothing fits' {
        Select-UygunJdk @([pscustomobject]@{ Path = 'jdk25'; Major = 25 }) | Should BeNullOrEmpty
    }
}

Describe 'ConvertFrom-AdbDevices' {
    It 'returns empty for no devices' {
        (ConvertFrom-AdbDevices @('List of devices attached', '')).Count | Should Be 0
    }
    It 'parses ready and unauthorized devices' {
        $lines = @(
            '* daemon started successfully',
            'List of devices attached',
            'R58W12345AB            device usb:1-1 product:a15xx model:SM_A155F device:a15 transport_id:1',
            'R58W99999ZZ            unauthorized usb:1-2 transport_id:2'
        )
        $d = ConvertFrom-AdbDevices $lines
        $d.Count | Should Be 2
        $d[0].State | Should Be 'device'
        $d[0].Model | Should Be 'SM_A155F'
        $d[1].State | Should Be 'unauthorized'
    }
}

Describe 'Version helpers' {
    It 'finds the newest NDK' {
        Get-EnYeniSurum @('27.1.12297006', '30.0.16248370') | Should Be '30.0.16248370'
    }
    It 'checks minimum build-tools and platform' {
        Test-EnAzSurum @('35.0.0', '36.1.0') 36 | Should Be $true
        Test-EnAzSurum @('34.0.0') 36 | Should Be $false
        Test-EnAzSurum @('android-36', 'android-36.1') 36 | Should Be $true
        Test-EnAzSurum @() 36 | Should Be $false
    }
}

Describe 'Device parsing' {
    It 'converts MemTotal to GB' {
        Get-MemTotalGb @('MemTotal:        3784512 kB', 'MemFree: 1 kB') | Should Be 3.6
    }
    It 'extracts package names from pm and query-services output' {
        $p = Get-PaketAdlari @('package:com.google.android.tts', 'package:com.samsung.SMT', '  0  com.google.android.as/com.google.android.apps.miphone.aiai.app.RecognitionService')
        $p | Should Be @('com.google.android.tts', 'com.samsung.SMT', 'com.google.android.as')
    }
    It 'keeps only speech/TTS/accessibility packages, never other apps' {
        $all = Get-PaketAdlari @('package:com.whatsapp', 'package:com.example.bank', 'package:com.samsung.SMT.lang_tr_tr_f00', 'package:com.google.android.tts', 'package:com.google.android.apps.accessibility.voiceaccess')
        $r = Select-IlgiliPaketler $all
        $r | Should Be @('com.samsung.SMT.lang_tr_tr_f00', 'com.google.android.tts', 'com.google.android.apps.accessibility.voiceaccess')
        ($r -contains 'com.whatsapp') | Should Be $false
    }
}
