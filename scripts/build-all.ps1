<#
  Build all 15 WurstB+ Plus release artifacts (5 MC versions x Forge/NeoForge/Fabric).
  Each artifact is produced by its own Gradle project and lands in that project's build/libs.
  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 1.21.11
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Loader NeoForge
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Skip 26.2
#>

[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$Version = "",
    [string]$Loader = "",
    [string[]]$Skip = @(),
    [switch]$Clean,
    [switch]$PublishToDownload,
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
    } elseif ($mcVersion -eq "1.21.1" -or $mcVersion -eq "1.21.11") {
        $envVar = "WURSTBPLUS_JAVA21"
        $default = "C:\Program Files\Java\jdk-21"
    } elseif ($mcVersion -eq "26.1.2" -or $mcVersion -eq "26.2") {
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
    @{ Name = "Forge 1.21.11";   Dir = "versions\1.21.11";         MC = "1.21.11"; Tasks = @("allJar", "test");                     Out = @("build\libs\WurstB+ Plus-v1.5.0-Forge-1.21.11.jar"); Baritone = "META-INF/jarjar/baritone-forge-1.17.0-1.21.11.jar" },
    @{ Name = "Forge 26.1.2";    Dir = "versions\26.1.2";          MC = "26.1.2"; Tasks = @("allJar");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-Forge-26.1.2.jar") },
    @{ Name = "Forge 26.2";      Dir = "versions\26.2";            MC = "26.2";   Tasks = @("allJar", "test");                     Out = @("build\libs\WurstB+ Plus-v1.5.0-Forge-26.2.jar"); Baritone = "META-INF/jarjar/baritone-forge-1.18.0-26.2.jar" },
    @{ Name = "NeoForge 1.20.1"; Dir = "neoforge";                 MC = "1.20.1"; Tasks = @("jarJar");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar") },
    @{ Name = "NeoForge 1.21.1"; Dir = "neoforge\versions\1.21.1"; MC = "1.21.1"; Tasks = @("jar");                                Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar") },
    @{ Name = "NeoForge 1.21.11";Dir = "neoforge\versions\1.21.11";MC = "1.21.11"; Tasks = @("build");                             Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar"); Baritone = "META-INF/jarjar/baritone-neoforge-1.17.0-1.21.11.jar" },
    @{ Name = "NeoForge 26.1.2"; Dir = "neoforge\versions\26.1.2"; MC = "26.1.2"; Tasks = @("jar");                                Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar") },
    @{ Name = "NeoForge 26.2";   Dir = "neoforge\versions\26.2";   MC = "26.2";   Tasks = @("build");                              Out = @("build\libs\WurstB+ Plus-v1.5.0-NeoForge-26.2.jar"); Baritone = "META-INF/jarjar/baritone-neoforge-1.18.0-26.2.jar" },
    @{ Name = "Fabric 1.20.1";   Dir = "fabric";                   MC = "1.20.1"; Tasks = @("build"); Args = @("-x", "test");       Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-1.20.1.jar") },
    @{ Name = "Fabric 1.21.1";   Dir = "fabric\versions\1.21.1";   MC = "1.21.1"; Tasks = @("build"); Args = @("-x", "test");       Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-1.21.1.jar") },
    @{ Name = "Fabric 1.21.11";  Dir = "fabric\versions\1.21.11";  MC = "1.21.11"; Tasks = @("build");                             Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-1.21.11.jar"); Baritone = "META-INF/jars/baritone-api-fabric-1.17.0-1.21.11.jar" },
    @{ Name = "Fabric 26.1.2";   Dir = "fabric\versions\26.1.2";   MC = "26.1.2"; Tasks = @("build"); Args = @("-x", "test");       Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-26.1.2.jar") },
    @{ Name = "Fabric 26.2";     Dir = "fabric\versions\26.2";     MC = "26.2";   Tasks = @("build");                              Out = @("build\libs\WurstB+ Plus-1.5.0-Fabric-26.2.jar"); Baritone = "META-INF/jars/baritone-api-fabric-1.18.0-26.2.jar" }
)

# ---------- main ----------
$selectedProjects = @($projects | Where-Object {
    $project = $_
    $skipMatch = @($Skip | Where-Object { $project.Name -match $_ }).Count -gt 0
    $projectLoader = ($project.Name -split " ", 2)[0]
    (-not $Version -or $project.MC -eq $Version) -and
    (-not $Loader -or $projectLoader -ieq $Loader) -and
    (-not $skipMatch)
})

if (@($selectedProjects | Where-Object { $_.MC -eq "26.2" }).Count -gt 0) {
    $baritonePatch = Join-Path $ProjectRoot "scripts\patch-baritone-26.2.ps1"
    Write-Info "Patching Baritone 26.2 compatibility artifacts..."
    & $baritonePatch -ProjectRoot $ProjectRoot
    if ($LASTEXITCODE -ne 0) { throw "Baritone 26.2 compatibility patch failed" }
}

Write-Info "========== WurstB+ Plus build-all ($($selectedProjects.Count) artifacts) =========="
$report = @()
$failed = $false

function Test-EmbeddedBaritone($artifact, $entryName, $mcVersion) {
    if (-not $entryName) { return @{ Passed = $true; Note = "not required" } }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = $null
    $nestedArchive = $null
    $buffer = $null
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($artifact)
        $entry = $archive.GetEntry($entryName)
        if (-not $entry) { return @{ Passed = $false; Note = "missing $entryName" } }

        $buffer = New-Object System.IO.MemoryStream
        $stream = $entry.Open()
        try { $stream.CopyTo($buffer) } finally { $stream.Dispose() }
        $buffer.Position = 0
        $nestedArchive = New-Object System.IO.Compression.ZipArchive($buffer, [System.IO.Compression.ZipArchiveMode]::Read, $true)
        if (-not $nestedArchive.GetEntry("baritone/api/BaritoneAPI.class")) {
            return @{ Passed = $false; Note = "Baritone API class missing from $entryName" }
        }
        if ($mcVersion -eq "26.2") {
            $compatibilityEntries = @(
                "baritone/api/utils/LegacyTuple.class",
                "baritone/api/utils/LegacyTesselator.class",
                "baritone/api/utils/LegacyRenderPipelineBuilder.class",
                "baritone/api/utils/LegacyRenderType.class"
            )
            $missingCompatibility = @($compatibilityEntries | Where-Object {
                -not $nestedArchive.GetEntry($_)
            })
            if ($missingCompatibility.Count -gt 0) {
                return @{ Passed = $false; Note = "Baritone compatibility classes missing: $($missingCompatibility -join ', ')" }
            }

            $manifestEntry = $nestedArchive.GetEntry("META-INF/MANIFEST.MF")
            if (-not $manifestEntry) {
                return @{ Passed = $false; Note = "Baritone manifest missing from $entryName" }
            }
            $manifestStream = $manifestEntry.Open()
            try {
                $reader = New-Object System.IO.StreamReader($manifestStream)
                try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
            } finally {
                $manifestStream.Dispose()
            }
            if ($manifest -notmatch "MixinConfigs: mixins\.baritone\.json" -or
                $manifest -notmatch "MixinConnector: baritone\.launch\.BaritoneMixinConnector") {
                return @{ Passed = $false; Note = "Baritone Mixin manifest attributes missing from $entryName" }
            }
        }
        $compatibilityNote = if ($mcVersion -eq "26.2") { "; 26.2 compatibility verified" } else { "" }
        return @{ Passed = $true; Note = "$entryName ($($entry.Length) bytes)$compatibilityNote" }
    } catch {
        return @{ Passed = $false; Note = "Baritone archive check failed: $($_.Exception.Message)" }
    } finally {
        if ($nestedArchive) { $nestedArchive.Dispose() }
        if ($buffer) { $buffer.Dispose() }
        if ($archive) { $archive.Dispose() }
    }
}

function Test-CoreClasses($artifact, $mcVersion, $loader) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = $null
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($artifact)
        $required = @("net/wurstclient/WurstClient.class")
        if ($mcVersion -in @("1.21.11", "26.2") -and $loader -eq "Forge") {
            $required += @(
                "net/wurstclient/mixin/WurstMixinConfigPlugin.class",
                "net/wurstclient/mixin/AbstractSignEditScreenMixin.class"
            )
        }
        $missing = @($required | Where-Object { -not $archive.GetEntry($_) })
        if ($missing.Count -gt 0) {
            return @{ Passed = $false; Note = "core classes missing: $($missing -join ', ')" }
        }
        return @{ Passed = $true; Note = "core classes verified" }
    } catch {
        return @{ Passed = $false; Note = "core class check failed: $($_.Exception.Message)" }
    } finally {
        if ($archive) { $archive.Dispose() }
    }
}

foreach ($p in $projects) {
    if ($Version -and $p.MC -ne $Version) { continue }
    $projectLoader = ($p.Name -split " ", 2)[0]
    if ($Loader -and $projectLoader -ine $Loader) { continue }
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
    # ForgeGradle's mavenizer can run under a different bundled JDK. Using the
    # Windows root store keeps HTTPS downloads working behind a local TLS proxy.
    $env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"

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
    $projectLoader = ($p.Name -split " ", 2)[0]
    $coreCheck = Test-CoreClasses $artifact $p.MC $projectLoader
    if (-not $coreCheck.Passed) {
        Write-Host "[$($p.Name)] FAIL: $($coreCheck.Note)" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "FAIL"; Note = $coreCheck.Note; Elapsed = $elapsed }
        $failed = $true
        continue
    }
    $baritoneCheck = Test-EmbeddedBaritone $artifact $p.Baritone $p.MC
    if (-not $baritoneCheck.Passed) {
        Write-Host "[$($p.Name)] FAIL: $($baritoneCheck.Note)" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "FAIL"; Note = $baritoneCheck.Note; Elapsed = $elapsed }
        $failed = $true
        continue
    }

    if ($PublishToDownload) {
        $downloadDir = Join-Path $ProjectRoot "download"
        New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
        Copy-Item -LiteralPath $artifact -Destination (Join-Path $downloadDir (Split-Path $artifact -Leaf)) -Force
    }

    $sizeMb = [math]::Round((Get-Item -LiteralPath $artifact).Length / 1MB, 1)
    Write-Host "[$($p.Name)] OK: $($p.Out[0]) ($sizeMb MB); Baritone: $($baritoneCheck.Note)" -ForegroundColor Green
    $report += @{ Name = $p.Name; Status = "PASS"; Note = "$($p.Out[0]); Baritone: $($baritoneCheck.Note)"; Elapsed = $elapsed }
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
