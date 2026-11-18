# Pure helper functions for ortam-kontrol.ps1 (unit-tested in ortam-kontrol.Tests.ps1).
# Read-only: nothing here changes the PC or the phone.

# Supported JDK majors for the Android Gradle Plugin. JDK 25 is intentionally excluded.
$script:UygunJdkSurumleri = @(17, 21)

function Get-JavaMajorFromRelease {
    # Parses the JAVA_VERSION line of a JDK "release" file; returns 0 if absent.
    param([string[]] $Lines)
    foreach ($line in $Lines) {
        if ($line -match '^JAVA_VERSION="?(\d+)(\.(\d+))?') {
            $first = [int]$Matches[1]
            # Old style "1.8.0" means major 8.
            if ($first -eq 1 -and $Matches[3]) { return [int]$Matches[3] }
            return $first
        }
    }
    return 0
}

function Test-UygunJdk {
    param([int] $Major)
    return $script:UygunJdkSurumleri -contains $Major
}

function Select-UygunJdk {
    # Candidates: ordered list of @{ Path; Major }. Returns the first supported one or $null.
    param([object[]] $Candidates)
    foreach ($c in $Candidates) {
        if (Test-UygunJdk -Major $c.Major) { return $c }
    }
    return $null
}

function ConvertFrom-AdbDevices {
    # Parses `adb devices -l` output into objects with Serial, State, Model.
    param([string[]] $Lines)
    $result = @()
    foreach ($line in $Lines) {
        $t = $line.Trim()
        if (-not $t -or $t -like 'List of devices*' -or $t.StartsWith('*')) { continue }
        $parts = $t -split '\s+'
        if ($parts.Count -lt 2) { continue }
        $model = ''
        foreach ($p in $parts) { if ($p -like 'model:*') { $model = $p.Substring(6) } }
        $result += [pscustomobject]@{ Serial = $parts[0]; State = $parts[1]; Model = $model }
    }
    return ,$result
}

function Get-SurumAnahtari {
    # Turns "36.1.0" or "27.1.12297006" into a sortable [version]; invalid names give 0.0.
    param([string] $Name)
    $v = $null
    if ([version]::TryParse($Name, [ref]$v)) { return $v }
    if ($Name -match '^android-(\d+)(\.(\d+))?$') {
        $minor = 0
        if ($Matches[3]) { $minor = [int]$Matches[3] }
        return [version]::new([int]$Matches[1], $minor)
    }
    return [version]::new(0, 0)
}

function Get-EnYeniSurum {
    param([string[]] $Names)
    if (-not $Names -or $Names.Count -eq 0) { return $null }
    return ($Names | Sort-Object { Get-SurumAnahtari $_ } -Descending | Select-Object -First 1)
}

function Test-EnAzSurum {
    # True if any name has a major version >= Minimum.
    param([string[]] $Names, [int] $Minimum)
    foreach ($n in $Names) {
        if ((Get-SurumAnahtari $n).Major -ge $Minimum) { return $true }
    }
    return $false
}

function Get-MemTotalGb {
    # Parses /proc/meminfo "MemTotal: 3750000 kB" into GB (1 decimal).
    param([string[]] $Lines)
    foreach ($line in $Lines) {
        if ($line -match '^MemTotal:\s+(\d+)\s*kB') {
            return [math]::Round([double]$Matches[1] / 1024 / 1024, 1)
        }
    }
    return 0
}

function Get-PaketAdlari {
    # Parses `pm list packages` / `query-services` style lines and returns package names.
    param([string[]] $Lines)
    $names = @()
    foreach ($line in $Lines) {
        if ($line -match 'package:([\w\.]+)') { $names += $Matches[1] }
        elseif ($line -match '([a-zA-Z][\w]*(\.[\w]+)+)/[\w\.\$]+') { $names += $Matches[1] }
    }
    return ,@($names | Select-Object -Unique)
}

function Select-IlgiliPaketler {
    # Keeps only speech, TTS and accessibility packages (other installed apps are never printed).
    param([string[]] $Names)
    $desen = '^(com\.google\.android\.googlequicksearchbox|com\.google\.android\.tts|com\.google\.android\.as|com\.samsung\.SMT(\..*)?|com\.google\.android\.apps\.accessibility\..*|com\.samsung\.android\.accessibility\..*|com\.google\.android\.marvin\.talkback|com\.google\.audio\.hearing\..*|com\.samsung\.android\.bixby\..*|com\.samsung\.android\.intellivoiceservice)$'
    return ,@($Names | Where-Object { $_ -cmatch $desen })
}
