<#
  Build all 15 WurstB+ Plus release artifacts (5 MC versions x Forge/NeoForge/Fabric).
  Each artifact is produced by its own Gradle project and lands in that project's build/libs.
  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 1.21.11
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Loader NeoForge
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Skip 26.2
    powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Offline
#>

[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$Version = "",
    [string]$Loader = "",
    [string[]]$Skip = @(),
    [switch]$Clean,
    [switch]$Offline,
    [switch]$PublishToDownload,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Get-WurstbProjectRoot
} else {
    $ProjectRoot = Get-WurstbProjectRoot $ProjectRoot
}

function Write-Info($msg) { if (-not $Quiet) { Write-Host $msg } }

# ---------- project table ----------
$projects = @(
    @{ Name = "Forge 1.20.1";    Dir = "";                          MC = "1.20.1"; Tasks = @("jarJar", "test");                     Out = @("build\libs\WurstB+ Plus-v1.6.0-Forge-1.20.1.jar"); V16 = $true },
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

# The per-MC Baritone copies live in baritone-maven/, which .gitignore excludes,
# so they do not survive a clone. This step is idempotent and cheap: it recreates
# whatever is missing and leaves correct artifacts alone. A fresh clone has no
# baritone-maven/ at all, in which case the jars must be rebuilt from source
# (see docs/PORTING-NEW-VERSIONS.md) - hence -AllowUnresolved, so a missing local
# Maven repo is a warning here rather than a hard failure.
$baritoneMcFix = Join-Path $ProjectRoot "scripts\fix-baritone-mc-declaration.ps1"
Write-Info "Ensuring bundled Baritone declares each project's Minecraft version..."
& $baritoneMcFix -ProjectRoot $ProjectRoot -AllowUnresolved
if ($LASTEXITCODE -ne 0) { throw "Baritone Minecraft declaration fix failed" }

Write-Info "========== WurstB+ Plus build-all ($($selectedProjects.Count) artifacts) =========="
$report = @()
$failed = $false

function Test-McRangeAdmits($rangeSpec, $mcVersion) {
    # Does a declared Minecraft dependency admit this MC version?
    # Handles Maven ranges ([1.21.8], [1.20,1.20.1], [1.21.11,1.22)) and Fabric
    # predicates (["1.21.8"], ["26.1.*"], "~1.21.8", ">=1.20 <=1.20.1").
    # A bundled Baritone whose own declaration rejects the host MC version makes
    # the loader refuse to start, so this is checked on every artifact.
    if ([string]::IsNullOrWhiteSpace($rangeSpec)) { return $false }

    $compare = {
        param($a, $b)
        $pa = @($a -split '\.'); $pb = @($b -split '\.')
        for ($i = 0; $i -lt [Math]::Max($pa.Count, $pb.Count); $i++) {
            $va = 0; $vb = 0
            if ($i -lt $pa.Count -and $pa[$i] -match '^\d+$') { $va = [int]$pa[$i] }
            if ($i -lt $pb.Count -and $pb[$i] -match '^\d+$') { $vb = [int]$pb[$i] }
            if ($va -ne $vb) { return [Math]::Sign($va - $vb) }
        }
        return 0
    }

    $terms = @()
    $s = $rangeSpec.Trim()
    if ($s.StartsWith('[')) {
        # JSON array of Fabric predicates, or a single Maven range.
        $inner = $s.Substring(1, $s.Length - 2)
        if ($inner -match '"') {
            foreach ($t in @($inner -split ',')) { $terms += $t.Trim().Trim('"').Trim("'").Trim() }
        } else {
            $terms += $s
        }
    } else {
        $terms += $s.Trim('"').Trim("'")
    }

    foreach ($term in $terms) {
        if ([string]::IsNullOrWhiteSpace($term) -or $term -eq '*') { return $true }

        if ($term.StartsWith('[') -or $term.StartsWith('(')) {
            $loInc = $term.StartsWith('['); $hiInc = $term.EndsWith(']')
            $body = @($term.Substring(1, $term.Length - 2) -split ',' | ForEach-Object { $_.Trim() })
            if ($body.Count -eq 1) {
                if ((& $compare $mcVersion $body[0]) -eq 0) { return $true }
                continue
            }
            $ok = $true
            if ($body[0]) {
                $lo = & $compare $mcVersion $body[0]
                if ($loInc) { if ($lo -lt 0) { $ok = $false } } else { if ($lo -le 0) { $ok = $false } }
            }
            if ($ok -and $body.Count -gt 1 -and $body[1]) {
                $hi = & $compare $mcVersion $body[1]
                if ($hiInc) { if ($hi -gt 0) { $ok = $false } } else { if ($hi -ge 0) { $ok = $false } }
            }
            if ($ok) { return $true }
            continue
        }

        if ($term -match '\*' -or $term -match '\.x$') {
            $prefix = ($term -replace '(\*|x)$', '')
            if ($mcVersion -eq $prefix.TrimEnd('.') -or $mcVersion.StartsWith($prefix)) { return $true }
            continue
        }

        if ($term.StartsWith('~') -or $term.StartsWith('^')) {
            $base = $term.Substring(1)
            $parts = @($base -split '\.')
            if ($term.StartsWith('~') -and $parts.Count -ge 2) { $hi = "$($parts[0]).$([int]$parts[1] + 1).0" }
            else { $hi = "$([int]$parts[0] + 1).0.0" }
            if ((& $compare $mcVersion $base) -ge 0 -and (& $compare $mcVersion $hi) -lt 0) { return $true }
            continue
        }

        if ($term -match '^[<>]') {
            $ok = $true
            foreach ($part in @($term -split '\s+' | Where-Object { $_ })) {
                $m = [regex]::Match($part, '^(>=|<=|>|<|=)?\s*(.+)$')
                if (-not $m.Success) { continue }
                $c = & $compare $mcVersion $m.Groups[2].Value.Trim()
                switch ($m.Groups[1].Value) {
                    '>=' { if ($c -lt 0) { $ok = $false } }
                    '<=' { if ($c -gt 0) { $ok = $false } }
                    '>' { if ($c -le 0) { $ok = $false } }
                    '<' { if ($c -ge 0) { $ok = $false } }
                    default { if ($c -ne 0) { $ok = $false } }
                }
            }
            if ($ok) { return $true }
            continue
        }

        if ((& $compare $mcVersion $term) -eq 0) { return $true }
    }
    return $false
}

function Test-HackNameIdentity($projectDir) {
    # A Hack's constructor argument is an identifier (registration key, keybind
    # target, save key, and the basis of its translation keys), not just a label.
    # A non-ASCII name derives keys like "hack.name.<cjk>" that can never match
    # the English keys in the translation files, silently losing the text.
    # Also asserts the translation table and the classes describe the same set.
    $hackDir = Join-Path $projectDir "src\main\java\net\wurstclient\hacks"
    $jsonPath = Join-Path $projectDir "src\main\resources\assets\wurst\translations\zh_cn_names.json"
    if (-not (Test-Path -LiteralPath $hackDir) -or -not (Test-Path -LiteralPath $jsonPath)) {
        return @{ Passed = $true; Note = "not applicable" }
    }

    $names = New-Object System.Collections.Generic.List[string]
    foreach ($file in Get-ChildItem -LiteralPath $hackDir -Recurse -File -Filter "*Hack.java") {
        $text = [System.IO.File]::ReadAllText($file.FullName)
        $m = [regex]::Match($text, 'super\(\s*"([^"]+)"\s*\)')
        if ($m.Success) { $names.Add($m.Groups[1].Value) }
    }
    if ($names.Count -eq 0) { return @{ Passed = $true; Note = "no hack names found" } }

    $nonAscii = @($names | Where-Object { $_ -match '[^\x00-\x7F]' })
    if ($nonAscii.Count -gt 0) {
        return @{ Passed = $false; Note = "non-ASCII hack identifier(s): $($nonAscii -join ', ')" }
    }

    $keys = @{}
    foreach ($n in $names) { $keys["hack.name." + $n.ToLower()] = $true }

    try {
        $json = [System.IO.File]::ReadAllText($jsonPath) | ConvertFrom-Json
    } catch {
        return @{ Passed = $false; Note = "zh_cn_names.json is not valid JSON" }
    }
    $jsonKeys = @($json.PSObject.Properties.Name)

    $dead = @($jsonKeys | Where-Object { -not $keys.ContainsKey($_) })
    $missing = @($keys.Keys | Where-Object { $jsonKeys -notcontains $_ })
    if ($dead.Count -gt 0 -or $missing.Count -gt 0) {
        $parts = @()
        if ($dead.Count -gt 0) { $parts += "unreachable keys: $($dead -join ', ')" }
        if ($missing.Count -gt 0) { $parts += "untranslated hacks: $($missing -join ', ')" }
        return @{ Passed = $false; Note = ($parts -join '; ') }
    }

    # The size assertion in WurstCnNamesTest must track the resource.
    $testPath = Join-Path $projectDir "src\test\java\net\wurstclient\WurstCnNamesTest.java"
    if (Test-Path -LiteralPath $testPath) {
        $t = [System.IO.File]::ReadAllText($testPath)
        $tm = [regex]::Match($t, 'assertEquals\((\d+),\s*names\.size\(\)\)')
        if ($tm.Success -and [int]$tm.Groups[1].Value -ne $jsonKeys.Count) {
            return @{ Passed = $false; Note = "WurstCnNamesTest asserts $($tm.Groups[1].Value) but the resource has $($jsonKeys.Count) keys" }
        }
    }

    return @{ Passed = $true; Note = "$($jsonKeys.Count) names, all reachable" }
}

function Get-NestedBaritoneMcSpec($nestedArchive) {
    # The bundled Baritone's own Minecraft dependency, whichever loader file it
    # ships. Returns $null when the archive declares no Minecraft dependency.
    $readEntry = {
        param($name)
        $e = $nestedArchive.GetEntry($name)
        if (-not $e) { return $null }
        $s = $e.Open()
        try {
            $r = New-Object System.IO.StreamReader($s)
            try { return $r.ReadToEnd() } finally { $r.Dispose() }
        } finally { $s.Dispose() }
    }
    $fabric = & $readEntry "fabric.mod.json"
    if ($fabric) {
        $m = [regex]::Match($fabric, '"minecraft"\s*:\s*(\[[^\]]*\]|"[^"]*")')
        if ($m.Success) { return $m.Groups[1].Value }
    }
    foreach ($tomlName in @("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
        $toml = & $readEntry $tomlName
        if (-not $toml) { continue }
        $modId = [regex]::Match($toml, 'modId\s*=\s*"minecraft"')
        if (-not $modId.Success) { continue }
        $vr = [regex]::Match($toml.Substring($modId.Index), 'versionRange\s*=\s*"([^"]*)"')
        if ($vr.Success) { return $vr.Groups[1].Value }
    }
    return $null
}

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

        # The nested mod's own `minecraft` dependency must admit the host version,
        # otherwise the loader rejects the bundle at startup.
        $mcSpec = Get-NestedBaritoneMcSpec $nestedArchive
        if (-not $mcSpec) {
            return @{ Passed = $false; Note = "bundled Baritone declares no minecraft dependency" }
        }
        if (-not (Test-McRangeAdmits $mcSpec $mcVersion)) {
            return @{ Passed = $false; Note = "bundled Baritone declares minecraft $mcSpec, which excludes $mcVersion" }
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
        return @{ Passed = $true; Note = "$entryName ($($entry.Length) bytes); declares minecraft $mcSpec$compatibilityNote" }
    } catch {
        return @{ Passed = $false; Note = "Baritone archive check failed: $($_.Exception.Message)" }
    } finally {
        if ($nestedArchive) { $nestedArchive.Dispose() }
        if ($buffer) { $buffer.Dispose() }
        if ($archive) { $archive.Dispose() }
    }
}

function Test-CoreClasses($artifact, $mcVersion, $loader, $isV16) {
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
        if ($isV16) {
            $required += @(
                "net/wurstclient/music/NeteaseCloudApi.class",
                "net/wurstclient/music/apple/AppleTimeline.class",
                "net/wurstclient/compose/AnimFloat.class",
                "net/wurstclient/render/skia/SkikoNatives.class",
                "net/wurstclient/gui/visual/VisualTheme.class",
                "assets/wurst/skiko/skiko-windows-x64.dll"
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

    $jdkHome = Get-WurstbJdkHome $p.MC
    if (-not $jdkHome) {
        Write-Host "[$($p.Name)] ERROR: JDK not found for MC $($p.MC)" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "ERROR"; Note = "missing JDK for MC $($p.MC)"; Elapsed = "-" }
        $failed = $true
        continue
    }

    $env:JAVA_HOME = $jdkHome
    # ForgeGradle's mavenizer can run under a different bundled JDK. Using the
    # Windows root store keeps HTTPS downloads working behind a local TLS proxy.
    $env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"

    $taskArgs = @("--no-daemon", "--console=plain")
    if ($Offline) { $taskArgs += "--offline" }
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
    $coreCheck = Test-CoreClasses $artifact $p.MC $projectLoader $p.V16
    if (-not $coreCheck.Passed) {
        Write-Host "[$($p.Name)] FAIL: $($coreCheck.Note)" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "FAIL"; Note = $coreCheck.Note; Elapsed = $elapsed }
        $failed = $true
        continue
    }
    $identityCheck = Test-HackNameIdentity $projDir
    if (-not $identityCheck.Passed) {
        Write-Host "[$($p.Name)] FAIL: $($identityCheck.Note)" -ForegroundColor Red
        $report += @{ Name = $p.Name; Status = "FAIL"; Note = $identityCheck.Note; Elapsed = $elapsed }
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
