param(
    [int]$StartupSeconds = 300,
    [switch]$World
)

$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\ui863\Documents\trae_projects\VAP'
$proj = Join-Path $repo 'fabric\versions\1.21.11'
$port = 26666
$token = 'soartest'
$headers = @{'X-Wurst-Token' = $token}
$log = Join-Path $env:TEMP 'soar-runtime-gradle.log'
$report = Join-Path $repo '_recon\soar-runtime-report.txt'
$env:JAVA_HOME = 'C:\Users\ui863\.jdks\jdk21.0.12_12'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$hacks = @(
    'AutoGG', 'ForceMainHand', 'JumpResetIndicator', 'PitchDisplay',
    'SnapTap', 'YawDisplay',
    'ChunkAnimator', 'ClearGlass', 'ClearWater', 'ColorSaturation',
    'CustomHeldItems', 'DamageTilt', 'DamageTint', 'EntityCulling',
    'FPSSpoofer', 'GlintColor', 'HitColor', 'HitDelayFix', 'ItemPhysics',
    'Items2D', 'MinimalDamageShake', 'MoBends', 'MotionBlur',
    'OldAnimations', 'OverlayEditor', 'ParticleCustomizer', 'RawInput',
    'Rearview', 'Skin3D', 'SkinProtect', 'SlowSwing', 'SoundModifier',
    'SoundSubtitles', 'ViaVersion', 'WaveyCapes'
)

function Get-ClientProcesses {
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and
        $_.CommandLine.Contains($proj)
    }
}

function Stop-Client {
    foreach ($p in Get-ClientProcesses) {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Get-Process javaw, java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -match 'Minecraft' } |
        ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
}

function Wait-Ping([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri "http://127.0.0.1:$port/ping" `
                -Headers $headers -TimeoutSec 2
            if ($r -eq 'ok') { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-Errors {
    try {
        return Invoke-RestMethod -Uri "http://127.0.0.1:$port/logs?lines=80&errorsOnly=1" `
            -Headers $headers -TimeoutSec 10
    } catch {
        return "log query failed: $($_.Exception.Message)"
    }
}

function Post-Hack([string]$action, [string]$name) {
    $u = "http://127.0.0.1:$port/hacks/${action}?name=" + [uri]::EscapeDataString($name)
    return Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30
}

Stop-Client
Start-Sleep -Seconds 2
$before = if (Test-Path (Join-Path $proj 'run\logs\latest.log')) {
    (Get-Item (Join-Path $proj 'run\logs\latest.log')).Length
} else { 0 }

$arg = if ($World) {
    'runClient "-PspWorld=xray-test" "-PspLog=%TEMP%\soar-runtime-sp.json" --offline --console=plain'
} else {
    'runClient --offline --console=plain'
}
$bat = Join-Path $env:TEMP 'soar-runtime-launch.bat'
@(
    '@echo off',
    ('cd /d "' + $proj + '"'),
    ('call .\gradlew.bat ' + $arg + ' > "' + $log + '" 2>&1')
) | Set-Content -Encoding ASCII $bat

Start-Process -FilePath $env:ComSpec -ArgumentList '/c', ('"' + $bat + '"') `
    -WindowStyle Hidden | Out-Null

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("started $(Get-Date -Format s) world=$World")

if (-not (Wait-Ping $StartupSeconds)) {
    $lines.Add('FAIL: control server did not start')
    $lines.Add((Get-Content $log -Tail 80 -ErrorAction SilentlyContinue) -join "`n")
    $lines | Set-Content -Encoding UTF8 $report
    Stop-Client
    Write-Output 'FAIL: control server did not start'
    exit 1
}

$lines.Add('control server ready')
Start-Sleep -Seconds 6
$errors = Get-Errors
if ($errors -and "$errors".Trim().Length -gt 0) {
    $lines.Add('startup errors:')
    $lines.Add("$errors")
}

if ($World) {
    $logPath = Join-Path $proj 'run\logs\latest.log'
    $deadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $logPath) {
            $fs = [IO.File]::Open($logPath, 'Open', 'Read', 'ReadWrite')
            try {
                $sr = New-Object IO.StreamReader($fs, [Text.Encoding]::UTF8)
                $text = $sr.ReadToEnd()
            } finally { $sr.Dispose(); $fs.Dispose() }
            if ($text -match 'joined the game|logged in with entity id') {
                $lines.Add('world loaded')
                break
            }
        }
        Start-Sleep -Seconds 2
    }
    Start-Sleep -Seconds 5
}

$all = Invoke-RestMethod -Uri "http://127.0.0.1:$port/hacks" -Headers $headers -TimeoutSec 20
$names = @($all | ForEach-Object { $_.name })
$missing = @($hacks | Where-Object { $names -notcontains $_ })
$lines.Add("registered=$($names.Count) missing=$($missing -join ',')")

foreach ($name in $hacks) {
    try {
        $r = Post-Hack 'enable' $name
        if (-not $r.ok) {
            $lines.Add("ENABLE-FAIL $name : $($r.error)")
            continue
        }
        Start-Sleep -Milliseconds 900
        $err = Get-Errors
        if ($err -and "$err".Trim().Length -gt 0) {
            $lines.Add("ERRORS after $name :")
            $lines.Add("$err")
        }
        Post-Hack 'disable' $name | Out-Null
        Start-Sleep -Milliseconds 300
    } catch {
        $lines.Add("ENABLE-EXCEPTION $name : $($_.Exception.Message)")
    }
}

# Enable the whole set at once to exercise interactions.
foreach ($name in $hacks) {
    try { Post-Hack 'enable' $name | Out-Null } catch {}
}
Start-Sleep -Seconds 5
$err = Get-Errors
if ($err -and "$err".Trim().Length -gt 0) {
    $lines.Add('ERRORS after enabling all:')
    $lines.Add("$err")
}
foreach ($name in $hacks) {
    try { Post-Hack 'disable' $name | Out-Null } catch {}
}

$lines.Add('done')
$lines | Set-Content -Encoding UTF8 $report
Stop-Client
Write-Output ("report: " + $report)
Write-Output (($lines | Select-Object -First 12) -join "`n")
