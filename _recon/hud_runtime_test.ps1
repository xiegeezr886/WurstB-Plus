param(
    [int]$StartupSeconds = 600,
    [string]$World = 'soar7-hud-test'
)

$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\ui863\Documents\trae_projects\VAP'
$proj = Join-Path $repo 'fabric\versions\1.21.11'
$port = 26666
$token = 'soartest'
$headers = @{'X-Wurst-Token' = $token}
$base = "http://127.0.0.1:$port"
$log = Join-Path $env:TEMP 'hud-runtime-gradle.log'
$report = Join-Path $repo '_recon\hud-runtime-report.txt'
$shots = Join-Path $repo '_recon\shots'
New-Item -ItemType Directory -Force -Path $shots | Out-Null
$env:JAVA_HOME = 'C:\Users\ui863\.jdks\jdk21.0.12_12'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

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
            $r = Invoke-RestMethod -Uri "$base/ping" -Headers $headers -TimeoutSec 2
            if ($r -eq 'ok') { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-Errors {
    try {
        return Invoke-RestMethod -Uri "$base/logs?lines=120&errorsOnly=1" `
            -Headers $headers -TimeoutSec 10
    } catch {
        return "log query failed: $($_.Exception.Message)"
    }
}

function Get-Hud {
    return Invoke-RestMethod -Uri "$base/hud" -Headers $headers -TimeoutSec 20
}

function Post-Hud([string]$action) {
    return Invoke-RestMethod -Method Post -Uri "$base/hud/$action" `
        -Headers $headers -TimeoutSec 30
}

function Set-Hud([string]$id, [bool]$enabled) {
    $u = "$base/hud/toggle?id=" + [uri]::EscapeDataString($id) +
        "&enabled=" + $enabled.ToString().ToLowerInvariant()
    return Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30
}

function Move-Hud([string]$id, [int]$x, [int]$y) {
    $u = "$base/hud/move?id=" + [uri]::EscapeDataString($id) +
        "&x=$x&y=$y"
    return Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30
}

function Scale-Hud([string]$id, [double]$scale) {
    $u = "$base/hud/scale?id=" + [uri]::EscapeDataString($id) +
        "&scale=" + $scale.ToString([Globalization.CultureInfo]::InvariantCulture)
    return Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30
}

function Set-Theme([string]$name) {
    $u = "$base/hud/theme?name=" + [uri]::EscapeDataString($name)
    return Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30
}

function Save-Screenshot([string]$name) {
    $u = "$base/screenshot?name=" + [uri]::EscapeDataString($name)
    $r = Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 90
    if (-not $r.ok) { return $null }
    $path = Join-Path $shots ($name + '.png')
    Copy-Item -LiteralPath $r.file -Destination $path -Force
    return $path
}

$lines = New-Object System.Collections.Generic.List[string]
Stop-Client
Start-Sleep -Seconds 2

$spLog = Join-Path $env:TEMP 'hud-runtime-sp.json'
$arg = "runClient `"-PspWorld=$World`" `"-PspLog=$spLog`" --offline --console=plain"
$bat = Join-Path $env:TEMP 'hud-runtime-launch.bat'
@(
    '@echo off',
    ('cd /d "' + $proj + '"'),
    ('call .\gradlew.bat ' + $arg + ' > "' + $log + '" 2>&1')
) | Set-Content -Encoding ASCII $bat
Start-Process -FilePath $env:ComSpec -ArgumentList '/c', ('"' + $bat + '"') `
    -WindowStyle Hidden | Out-Null

$lines.Add("started $(Get-Date -Format s) save=$World")
if (-not (Wait-Ping $StartupSeconds)) {
    $lines.Add('FAIL: control server did not start')
    $lines.Add((Get-Content $log -Tail 120 -ErrorAction SilentlyContinue) -join "`n")
    $lines | Set-Content -Encoding UTF8 $report
    Stop-Client
    Write-Output 'FAIL: control server did not start'
    exit 1
}

$lines.Add('control server ready')
$logPath = Join-Path $proj 'run\logs\latest.log'
$deadline = (Get-Date).AddSeconds(240)
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
Start-Sleep -Seconds 4

$hud = Get-Hud
$hackEls = @($hud | Where-Object { $_.hack })
$lines.Add("hud mods=$($hud.Count) hackMods=$($hackEls.Count)")
$builtins = @($hud | Where-Object { -not $_.hack })
$lines.Add('builtins: ' + (($builtins | ForEach-Object { $_.id + '=' + $_.enabled }) -join ', '))

$startupErr = Get-Errors
if ($startupErr -and "$startupErr".Trim().Length -gt 0) {
    $lines.Add('startup errors:')
    $lines.Add("$startupErr")
}

$safe = @('hack.pitchdisplay', 'hack.yawdisplay', 'hack.cpsdisplay',
    'hack.combocounter', 'hack.armorstatus', 'hack.potionstatus',
    'hack.keystrokes', 'hack.scoreboard')
$places = @{
    'hack.pitchdisplay' = @(40, 250)
    'hack.yawdisplay' = @(40, 280)
    'hack.cpsdisplay' = @(40, 310)
    'hack.combocounter' = @(40, 340)
    'hack.armorstatus' = @(40, 370)
    'hack.potionstatus' = @(300, 250)
    'hack.keystrokes' = @(500, 250)
    'hack.scoreboard' = @(300, 350)
}

foreach ($id in $safe) {
    $r = Set-Hud $id $true
    Start-Sleep -Milliseconds 250
    if (-not $r.ok) { $lines.Add("ENABLE-FAIL $id") }
    $xy = $places[$id]
    Move-Hud $id $xy[0] $xy[1] | Out-Null
}
Scale-Hud 'hack.pitchdisplay' 1.3 | Out-Null
Scale-Hud 'hack.keystrokes' 0.9 | Out-Null
Set-Theme 'normal' | Out-Null
Post-Hud 'open' | Out-Null
Start-Sleep -Seconds 3
$shot = Save-Screenshot 'soar7_editor_normal'
$lines.Add("screenshot $shot")
$err = Get-Errors
if ($err -and "$err".Trim().Length -gt 0) {
    $lines.Add('errors after 8 previews:')
    $lines.Add("$err")
}

$hud = Get-Hud
$enabledHacks = @($hud | Where-Object { $_.hack -and $_.enabled })
$lines.Add("safe enabled=$($enabledHacks.Count): " +
    (($enabledHacks | ForEach-Object { $_.id }) -join ', '))

Set-Theme 'vanilla' | Out-Null
Start-Sleep -Seconds 1
Save-Screenshot 'soar7_editor_vanilla' | Out-Null
Set-Theme 'outline' | Out-Null
Start-Sleep -Seconds 1
Save-Screenshot 'soar7_editor_outline' | Out-Null
Set-Theme 'normal' | Out-Null

# Enable every ported HUD mod to exercise the complete adapter list.
foreach ($el in $hackEls) {
    try {
        $r = Set-Hud $el.id $true
        if (-not $r.ok) { $lines.Add("ALL-ENABLE-FAIL $($el.id)") }
    } catch {
        $lines.Add("ALL-ENABLE-EXCEPTION $($el.id): $($_.Exception.Message)")
    }
}
Start-Sleep -Seconds 5
$hud = Get-Hud
$hackEls = @($hud | Where-Object { $_.hack })
$hackEnabled = @($hackEls | Where-Object { $_.hackEnabled })
$lines.Add("all hackEnabled=$($hackEnabled.Count)/$($hackEls.Count)")
$err = Get-Errors
if ($err -and "$err".Trim().Length -gt 0) {
    $lines.Add('errors after enabling all:')
    $lines.Add("$err")
}
$shot = Save-Screenshot 'soar7_editor_all'
$lines.Add("screenshot $shot")

Post-Hud 'close' | Out-Null
Start-Sleep -Seconds 2
$shot = Save-Screenshot 'soar7_ingame'
$lines.Add("screenshot $shot")
$err = Get-Errors
if ($err -and "$err".Trim().Length -gt 0) {
    $lines.Add('errors after close:')
    $lines.Add("$err")
}

foreach ($el in $hackEls) {
    try { Set-Hud $el.id $false | Out-Null } catch {}
}
try {
    Invoke-RestMethod -Method Post -Uri "$base/hacks/disableAll" `
        -Headers $headers -TimeoutSec 60 | Out-Null
} catch {}
Start-Sleep -Seconds 2
$hud = Get-Hud
$still = @($hud | Where-Object { $_.hack -and $_.enabled })
$lines.Add("still enabled after cleanup=$($still.Count)")

$lines.Add('done')
$lines | Set-Content -Encoding UTF8 $report
Stop-Client
Write-Output ("report: " + $report)
Write-Output (($lines | Select-Object -First 40) -join "`n")
