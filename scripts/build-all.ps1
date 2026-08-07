<#
  Build all 9 WurstB+ Plus release artifacts (3 MC versions x Forge/NeoForge/Fabric).
  Each artifact is produced by its own Gradle project and lands in that project's build/libs.
  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 1.21.1
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Loader NeoForge
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Skip Test
#>

[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$Version = "",
    [string]$Loader = "",
    [string[]]$Skip = @(),
    [switch]$Clean,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
} else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

function Write-Info($msg) { if (-not $Quiet) { Write-Host $msg } }

# ---------- JDK locations (overridable via env) ----------
function Get-JdkHome($mcVersion) {
    $envVar = ""
    $default = ""
    if ($mcVersion -eq "1.20.1") {
        $envVar = "WURSTBPLUS_JAVA17"
        $default = "C:\Program Files\Java\jdk-17"
    } elseif ($mcVersion -eq "1.21.1") {
        $envVar = "WURSTBPLUS_JAVA21"
        $default = "C:\Program Files\Java\jdk-21"
    } elseif ($mcVersion -eq "26.1.2") {
        $envVar = "WURSTBPLUS_JAVA25"
        $default = "C:\Program Files\Java\jdk-25.0.4"
    }
    $override = [Environment]::GetEnvironmentVariable($envVar)
    if ($override) { return $override }
    return $default
}

function Test-Jdk($jdkHome) {
    return (Test-Path -LiteralPath (Join-Path $jdkHome "bin\java.exe"))
}

# ---------- project table ----------
$projects = @(
    @{ Name = "Forge 1.20.1";    Dir = "";                          MC = "1.20.1"; Tasks = @("jarJar");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-Forge-1.20.1.jar") },
    @{ Name = "Forge 1.21.1";    Dir = "versions\1.21.1";          MC = "1.21.1"; Tasks = @("jarJar");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-Forge-1.21.1.jar") },
    @{ Name = "Forge 26.1.2";    Dir = "versions\26.1.2";          MC = "26.1.2"; Tasks = @("allJar");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-Forge-26.1.2.jar") },
    @{ Name = "NeoForge 1.20.1"; Dir = "neoforge";                 MC = "1.20.1"; Tasks = @("jarJar");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar") },
    @{ Name = "NeoForge 1.21.1"; Dir = "neoforge\versions\1.21.1"; MC = "1.21.1"; Tasks = @("jar");                                Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar") },
    @{ Name = "NeoForge 26.1.2"; Dir = "neoforge\versions\26.1.2"; MC = "26.1.2"; Tasks = @("jar");                                Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar") },
    @{ Name = "Fabric 1.20.1";   Dir = "fabric";                   MC = "1.20.1"; Tasks = @("build"); Args = @("-x", "test");       Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-1.20.1.jar") },
    @{ Name = "Fabric 1.21.1";   Dir = "fabric\versions\1.21.1";   MC = "1.21.1"; Tasks = @("build"); Args = @("-x", "test");       Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-1.21.1.jar") },
    @{ Name = "Fabric 26.1.2";   Dir = "fabric\versions\26.1.2";   MC = "26.1.2"; Tasks = @("build"); Args = @("-x", "test");       Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-26.1.2.jar") }
)

# ---------- main ----------
Write-Info "========== WurstB+ Plus build-all (9 artifacts) =========="
$report = @()
$failed = $false

foreach ($p in $projects) {
    if ($Version -and $p.MC -ne $Version) { continue }
    if ($Loader -and $p.Name -notmatch $Loader) { continue }
    if ($Skip -and ($Skip | Where-Object { $p.Name -match $_ })) { continue }

    $projDir = Join-Path $ProjectRoot $p.Dir
    $gradlew = Join-Path $projDir "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradlew)) {
        Write-Host "[$($p.Name)] ERROR: no gradlew.bat in $projDir" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "ERROR"; Note = "no gradlew.bat"; Elapsed = "-" }
        $failed = $true
        continue
    }

    $jdkHome = Get-JdkHome $p.MC
    if (-not (Test-Jdk $jdkHome)) {
        Write-Host "[$($p.Name)] ERROR: JDK not found: $jdkHome" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "ERROR"; Note = "missing JDK $jdkHome"; Elapsed = "-" }
        $failed = $true
        continue
    }

    $env:JAVA_HOME = $jdkHome
    if ($p.MC -eq "26.1.2") {
        $env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
    } else {
        Remove-Item Env:_JAVA_OPTIONS -ErrorAction SilentlyContinue
    }

    $taskArgs = @("--no-daemon", "--console=plain")
    if ($Clean) { $taskArgs = @("clean") + $taskArgs }
    $taskArgs += $p.Tasks
    if ($p.Args) { $taskArgs += $p.Args }

    Write-Host "[$($p.Name)] building (JDK: $jdkHome, tasks: $($p.Tasks -join ' ')) ..."
    $start = Get-Date
    Push-Location $projDir
    try {
        # No 2>&1 redirection: with ErrorActionPreference=Stop, stderr lines from
        # gradlew.bat (e.g. "_JAVA_OPTIONS" notices) would become terminating errors.
        $null = & $gradlew @taskArgs
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)

    if ($exitCode -ne 0) {
        Write-Host "[$($p.Name)] FAIL: gradle exited with code $exitCode" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "FAIL"; Note = "gradle exit $exitCode"; Elapsed = $elapsed }
        $failed = $true
        continue
    }

    $missing = @($p.Out | Where-Object { -not (Test-Path -LiteralPath (Join-Path $projDir $_)) })
    if ($missing.Count -gt 0) {
        Write-Host "[$($p.Name)] FAIL: artifact not produced: $($missing -join ', ')" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "FAIL"; Note = "missing artifact: $($missing -join ', ')"; Elapsed = $elapsed }
        $failed = $true
        continue
    }

    $artifact = Join-Path $projDir $p.Out[0]
    $sizeMb = [math]::Round((Get-Item -LiteralPath $artifact).Length / 1MB, 1)
    Write-Host "[$($p.Name)] OK: $($p.Out[0]) ($sizeMb MB)" -ForegroundColor Green
    $report += @{ Name = $p.Name; Status = "PASS"; Note = $p.Out[0]; Elapsed = $elapsed }
    Write-Host ""
}

# ---------- summary ----------
Write-Host "========== summary =========="
$report | ForEach-Object {
    Write-Host ("{0,-12} {1,-8} {2}s  {3}" -f $_.Status, $_.Name, $_.Elapsed, $_.Note)
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportFile = Join-Path $ProjectRoot ".test\report-build-$stamp.txt"
New-Item -ItemType Directory -Path (Split-Path $reportFile) -Force | Out-Null
$report | ForEach-Object {
    "Project: $($_.Name)`n  Status: $($_.Status)  Elapsed: $($_.Elapsed)s`n  Note: $($_.Note)"
} | Set-Content -LiteralPath $reportFile -Encoding UTF8
Write-Host "report saved: $reportFile"

if ($failed) { exit 1 } else { exit 0 }
