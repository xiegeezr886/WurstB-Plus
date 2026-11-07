#requires -Version 5.1
<#
Launches one project's dev client into a saved world, waits for the world to
load, captures a PrintWindow screenshot, and reports NanoVG HUD status.

Usage:
  powershell -File _recon\port\Test-NanoVgDev.ps1 -Project fabric\versions\26.1 `
      -JavaHome C:\Users\ui863\.jdks\jdk25.0.4_10 -World xray-test `
      -Out _recon\port\evidence\nanovg-26.1.png -GraphicsBackend opengl
#>
param(
    [Parameter(Mandatory = $true)][string]$Project,
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$World = '',
    [string]$GraphicsBackend = '',
    [int]$TimeoutSeconds = 420,
    [int]$SettleSeconds = 8
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$proj = (Resolve-Path (Join-Path $repo $Project)).Path
$outPath = if ([IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $repo $Out }
$outDir = Split-Path -Parent $outPath
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-ProjectProcesses {
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and
        ($_.CommandLine.Contains($proj) -or $_.CommandLine -match 'Minecraft')
    }
}

function Stop-ProjectProcesses {
    foreach ($p in Get-ProjectProcesses) {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Get-Process javaw, java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -match 'Minecraft' } |
        ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
}

function Read-SharedText([string]$path) {
    if (-not (Test-Path $path)) { return '' }
    $fs = [IO.File]::Open($path, 'Open', 'Read', 'ReadWrite')
    try {
        $sr = New-Object IO.StreamReader($fs, [Text.Encoding]::UTF8)
        return $sr.ReadToEnd()
    } finally { $sr.Dispose(); $fs.Dispose() }
}

$latest = Join-Path $proj 'run\logs\latest.log'
$gradleLog = Join-Path $env:TEMP ('nanovg-' + [IO.Path]::GetFileName($proj) + '-gradle.log')

Stop-ProjectProcesses
Start-Sleep -Seconds 2
if (Test-Path $latest) { Remove-Item -LiteralPath $latest -Force -ErrorAction SilentlyContinue }

$env:JAVA_HOME = $JavaHome
$bat = Join-Path $env:TEMP ('nanovg-launch-' + [IO.Path]::GetFileName($proj) + '.bat')
$arg = 'runClient'
if ($World) { $arg += ' "-PspWorld=' + $World + '" "-PspLog=' + (Join-Path $env:TEMP 'sp-result.json') + '"' }
if ($GraphicsBackend) { $arg += ' "-PgraphicsBackend=' + $GraphicsBackend + '"' }
$arg += ' --console=plain'
@(
    '@echo off',
    ('cd /d "' + $proj + '"'),
    ('call .\gradlew.bat ' + $arg + ' > "' + $gradleLog + '" 2>&1')
) | Set-Content -Encoding ASCII $bat

$launcher = Start-Process -FilePath $env:ComSpec -ArgumentList '/c', ('"' + $bat + '"') `
    -WindowStyle Hidden -PassThru

$worldLoaded = $false
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$buffer = ''
while ((Get-Date) -lt $deadline) {
    $buffer = Read-SharedText $latest
    if ($buffer -match 'joined the game|logged in with entity id') {
        $worldLoaded = $true
        break
    }
    if ($launcher.HasExited) { break }
    Start-Sleep -Seconds 3
}

$result = [ordered]@{
    project = $Project
    worldLoaded = $worldLoaded
    nanoVgContext = $false
    hudStarted = $false
    renderFailed = $false
    nanoVgDisabled = $false
    guiPassFailed = $false
    mixinFailed = $false
    crashed = $false
    screenshot = $null
}

if ($worldLoaded) {
    Start-Sleep -Seconds $SettleSeconds
    try {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $repo '_recon\promo-v1.5\capture.ps1') -Out $outPath | Out-Null
        if (Test-Path $outPath) { $result.screenshot = $outPath }
    } catch {
        $result.screenshotError = $_.Exception.Message
    }
    $buffer = Read-SharedText $latest
}

$result.nanoVgContext = ($buffer -match '\[HUD\] NanoVG GL3 context created')
$result.hudStarted = ($buffer -match '\[HUD\] Soar v7 HUD started')
$result.renderFailed = ($buffer -match '\[HUD\] Render failed')
$result.nanoVgDisabled = ($buffer -match '\[HUD\] NanoVG disabled')
$result.guiPassFailed = ($buffer -match '\[GUI\] NanoVG pass failed')
$result.mixinFailed = ($buffer -match 'Mixin apply failed')
$result.crashed = ($buffer -match 'FATAL|crash-reports')

# Last 40 relevant lines for the report.
$result.lines = @($buffer -split "`n" | Where-Object {
    $_ -match '\[HUD\]|\[GUI\] NanoVG|\[WurstTest\]|Mixin apply failed|FATAL|crash'
} | Select-Object -Last 40)

Stop-ProjectProcesses
if (-not $launcher.HasExited) { Stop-Process -Id $launcher.Id -Force -ErrorAction SilentlyContinue }

$result | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 ($outPath -replace '\.png$', '.json')
Write-Output ($result | ConvertTo-Json -Depth 4)
