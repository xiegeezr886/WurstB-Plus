# Batch test WurstB+ jars in download/ against .test/versions instances
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 1.21.1 -Loader NeoForge
#   powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -TimeoutSeconds 300 -KeepOldJars

param(
    [string]$ProjectRoot = "",
    [string]$VersionsRoot = ".test\versions",
    [string]$DownloadDir = "download",
    [string]$LibrariesDir = ".test\libraries",
    [string]$AssetsDir = ".test\assets",
    [string]$NativesBase = ".test\natives",
    [string]$OutBase = ".test\out",
    [string]$ReportDir = ".test",
    [string]$Version = "",
    [string]$Loader = "",
    [ValidateRange(30, 3600)]
    [int]$TimeoutSeconds = 240,
    [ValidateRange(0, 600)]
    [int]$SettleSeconds = 20,
    [switch]$KeepOldJars,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

# Resolve defaults from the repository instead of the caller's current directory.
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
} else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

function Resolve-ExistingPath($path, $description) {
    $candidate = if ([System.IO.Path]::IsPathRooted($path)) {
        $path
    } else {
        Join-Path $ProjectRoot $path
    }
    if (-not (Test-Path -LiteralPath $candidate)) {
        throw "$description does not exist: $candidate"
    }
    return (Resolve-Path -LiteralPath $candidate).Path
}

function Resolve-OutputPath($path) {
    $candidate = if ([System.IO.Path]::IsPathRooted($path)) {
        $path
    } else {
        Join-Path $ProjectRoot $path
    }
    New-Item -ItemType Directory -Path $candidate -Force | Out-Null
    return (Resolve-Path -LiteralPath $candidate).Path
}

$VersionsRoot = Resolve-ExistingPath $VersionsRoot "Versions root"
$DownloadDir = Resolve-ExistingPath $DownloadDir "Download directory"
$LibrariesDir = Resolve-ExistingPath $LibrariesDir "Libraries directory"
$AssetsDir = Resolve-ExistingPath $AssetsDir "Assets directory"
$NativesBase = Resolve-OutputPath $NativesBase
$OutBase = Resolve-OutputPath $OutBase
$ReportDir = Resolve-OutputPath $ReportDir

function Write-Info($msg) { if (-not $Quiet) { Write-Host $msg } }

# ---------- JDK selection ----------
$JDKs = @{
    "1.20.1" = if ($env:WURSTBPLUS_JAVA17) { $env:WURSTBPLUS_JAVA17 } else { "C:\Program Files\Java\jdk-17" }
    "1.21.1" = if ($env:WURSTBPLUS_JAVA21) { $env:WURSTBPLUS_JAVA21 } else { "C:\Program Files\Java\jdk-21" }
    "26.1.2" = if ($env:WURSTBPLUS_JAVA25) { $env:WURSTBPLUS_JAVA25 } else { "C:\Program Files\Java\jdk-25.0.4" }
}

function Get-Java($mcVersion) {
    $dir = $JDKs[$mcVersion]
    if (-not $dir) { throw "Unknown MC version $mcVersion" }
    $java = Join-Path $dir "bin\java.exe"
    if (-not (Test-Path $java)) { throw "JDK not found: $java" }
    return $java
}

# ---------- match download jar to test instance ----------
function Get-JarVersionInfo($fileName) {
    if ($fileName -match "-(sources|javadoc|dev|slim)\.jar$") { return $null }
    if ($fileName -notmatch "-(Fabric|Forge|NeoForge)-(\d+\.\d+(?:\.\d+)?)") { return $null }
    return @{
        Loader = $Matches[1]
        MC = $Matches[2]
    }
}

function Find-InstanceDir($mc, $loader) {
    $dirs = Get-ChildItem -LiteralPath $VersionsRoot -Directory
    foreach ($d in $dirs) {
        if ($d.Name -notmatch "^$([regex]::Escape($mc))-") { continue }
        if ($loader -eq "NeoForge" -and $d.Name -notmatch "NeoForge") { continue }
        if ($loader -eq "Fabric" -and $d.Name -notmatch "Fabric") { continue }
        if ($loader -eq "Forge" -and $d.Name -notmatch "Forge_") { continue }
        return $d.FullName
    }
    return $null
}

# ---------- version json parsing ----------
function Test-RuleMatch($rule, $osName, $osArch, $features) {
    if ($rule.os) {
        if ($rule.os.name -and $rule.os.name -ne $osName) { return $false }
        if ($rule.os.arch -and $rule.os.arch -ne $osArch) { return $false }
        if ($rule.os.version -and ([Environment]::OSVersion.Version.ToString() -notmatch [string]$rule.os.version)) {
            return $false
        }
    }
    if ($rule.features) {
        foreach ($feature in $rule.features.PSObject.Properties) {
            $actual = $false
            if ($features.ContainsKey($feature.Name)) {
                $actual = [bool]$features[$feature.Name]
            }
            if ($actual -ne [bool]$feature.Value) { return $false }
        }
    }
    return $true
}

function Test-Rules($rules, $osName, $osArch, $features) {
    if (-not $rules -or @($rules).Count -eq 0) { return $true }

    # Launcher rules use the last matching rule; a ruleset defaults to denied.
    $allowed = $false
    foreach ($rule in @($rules)) {
        if (Test-RuleMatch $rule $osName $osArch $features) {
            $allowed = ([string]$rule.action -eq "allow")
        }
    }
    return $allowed
}

function Get-LibraryJar($lib, $libDir) {
    if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.path) {
        return Join-Path $libDir ($lib.downloads.artifact.path -replace "/", "\")
    }
    $parts = $lib.name -split ":"
    if ($parts.Count -lt 3) { return $null }
    $group = $parts[0]; $artifact = $parts[1]; $version = $parts[2]
    $classifier = if ($parts.Count -ge 4) { $parts[3] } else { $null }
    $p = "$($group -replace '\.', '\')\$artifact\$version\$artifact-$version"
    if ($classifier) { $p += "-$classifier" }
    $p += ".jar"
    return Join-Path $libDir $p
}

function Get-NativeLibrary($lib, $libDir, $osName, $osArch) {
    if (-not $lib.natives) { return $null }

    $nativeProperty = $lib.natives.PSObject.Properties[$osName]
    if (-not $nativeProperty) { return $null }
    $classifier = [string]$nativeProperty.Value
    $classifier = $classifier.Replace('${arch}', $osArch)

    if ($lib.downloads -and $lib.downloads.classifiers) {
        $downloadProperty = $lib.downloads.classifiers.PSObject.Properties[$classifier]
        if ($downloadProperty -and $downloadProperty.Value.path) {
            return Join-Path $libDir ($downloadProperty.Value.path -replace '/', '\')
        }
    }

    $parts = $lib.name -split ':'
    if ($parts.Count -lt 3) { return $null }
    $groupPath = $parts[0] -replace '\.', '\'
    return Join-Path $libDir "$groupPath\$($parts[1])\$($parts[2])\$($parts[1])-$($parts[2])-$classifier.jar"
}

function Expand-Args($argsList, $vars, $osName, $osArch, $features) {
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($item in @($argsList)) {
        if ($item -is [System.Collections.IDictionary] -or $item.GetType().Name -eq "PSCustomObject") {
            if (-not (Test-Rules $item.rules $osName $osArch $features)) { continue }
            $vals = @($item.value) | Where-Object { $_ }
        } else {
            $vals = @($item)
        }
        foreach ($v in $vals) {
            $expanded = [string]$v
            foreach ($key in $vars.Keys) {
                $expanded = $expanded.Replace("`${$key}", [string]$vars[$key])
            }
            if ($expanded -match "\$\{[^}]+\}") { continue }
            $out.Add($expanded)
        }
    }
    return @($out | Where-Object { $_ -and $_.Trim() -ne "" -and $_ -notmatch "--quickPlay" })
}

function Get-NativeArch {
    $arch = if ($env:PROCESSOR_ARCHITEW6432) { $env:PROCESSOR_ARCHITEW6432 } else { $env:PROCESSOR_ARCHITECTURE }
    if ($arch -in @("AMD64", "ARM64")) { return "64" }
    return "32"
}

function Stop-TrackedTestProcess($pidFile, $instanceDir) {
    if (-not (Test-Path -LiteralPath $pidFile)) { return }
    $previousPid = 0
    $pidText = (Get-Content -LiteralPath $pidFile -Raw -ErrorAction SilentlyContinue).Trim()
    if ([int]::TryParse($pidText, [ref]$previousPid)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$previousPid" -ErrorAction SilentlyContinue
        if ($process -and $process.CommandLine -and
            $process.CommandLine.IndexOf($instanceDir, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            Stop-Process -Id $previousPid -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

function Get-RecentLogText($paths) {
    $parts = foreach ($path in $paths) {
        if (Test-Path -LiteralPath $path) {
            (Get-Content -LiteralPath $path -Tail 300 -ErrorAction SilentlyContinue) -join "`n"
        }
    }
    return ($parts -join "`n")
}

# ---------- test one version ----------
function Test-Version($jarPath, $instanceDir) {
    $name = Split-Path $instanceDir -Leaf
    $jsonPath = Join-Path $instanceDir "$name.json"
    $clientJar = Join-Path $instanceDir "$name.jar"

    if (-not (Test-Path $jsonPath)) { return @{ Status = "ERROR"; Note = "missing $name.json" } }
    if (-not (Test-Path $clientJar)) { return @{ Status = "ERROR"; Note = "missing client $name.jar" } }

    $j = Get-Content -LiteralPath $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $mcVersion = if ($name -match "^(\d+\.\d+(?:\.\d+)?)") { $Matches[1] } else { "1.20.1" }

    $java = Get-Java $mcVersion
    $osName = "windows"
    $osArch = Get-NativeArch

    # 1. build classpath
    $classpath = New-Object System.Collections.Generic.List[string]
    $features = @{
        has_custom_resolution = $false
        is_demo_user = $false
    }
    $nativeLibraries = New-Object System.Collections.Generic.List[object]
    foreach ($lib in @($j.libraries)) {
        if (-not (Test-Rules $lib.rules $osName $osArch $features)) { continue }
        if ($lib.natives) {
            $nativeJar = Get-NativeLibrary $lib $LibrariesDir $osName $osArch
            if (-not $nativeJar) { continue }
            if (-not (Test-Path -LiteralPath $nativeJar)) {
                return @{ Status = "ERROR"; Note = "missing native dependency: $($lib.name) [$nativeJar]" }
            }
            $nativeLibraries.Add([PSCustomObject]@{
                Path = $nativeJar
                Excludes = @($lib.extract.exclude)
            })
            continue
        }
        $jar = Get-LibraryJar $lib $LibrariesDir
        if (-not $jar) { continue }
        if (Test-Path $jar) { $classpath.Add($jar) }
        else { return @{ Status = "ERROR"; Note = "missing dependency: $($lib.name)" } }
    }
    $classpath.Add($clientJar)
    $cp = @($classpath | Select-Object -Unique) -join ";"

    # 2. extract natives
    $nativesDir = Join-Path $NativesBase $name
    if (Test-Path $nativesDir) { Remove-Item -LiteralPath $nativesDir -Recurse -Force }
    New-Item -ItemType Directory -Path $nativesDir -Force | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($native in $nativeLibraries) {
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($native.Path)
            foreach ($e in $zip.Entries) {
                if ($e.FullName -match "^META-INF/") { continue }
                if ($e.FullName -match "/$") { continue }
                $excluded = $false
                foreach ($prefix in @($native.Excludes)) {
                    if ($prefix -and ($e.FullName -eq $prefix -or $e.FullName.StartsWith("$prefix/"))) {
                        $excluded = $true
                        break
                    }
                }
                if ($excluded) { continue }
                $relative = $e.FullName -replace "/", "\"
                if ([System.IO.Path]::IsPathRooted($relative) -or $relative -match "(^|\\)\.\.(\\|$)") {
                    throw "Unsafe native archive entry: $($e.FullName)"
                }
                $dest = Join-Path $nativesDir $relative
                $rootWithSeparator = $nativesDir.TrimEnd('\') + '\'
                if (-not ([System.IO.Path]::GetFullPath($dest)).StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
                    throw "Native archive entry escapes extraction directory: $($e.FullName)"
                }
                New-Item -ItemType Directory -Path (Split-Path $dest) -Force | Out-Null
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
            }
            $zip.Dispose()
        } catch {
            return @{ Status = "ERROR"; Note = "natives extract failed: $($_.Exception.Message)" }
        }
    }

    # 3. variable table
    $vars = @{
        auth_player_name    = "TestRunner"
        version_name        = $name
        game_directory      = $instanceDir
        assets_root         = (Resolve-Path $AssetsDir).Path
        assets_index_name   = $j.assetIndex.id
        auth_uuid           = "00000000000000000000000000000000"
        auth_access_token   = "0"
        clientid            = "test-runner"
        auth_xuid           = "0"
        user_type           = "legacy"
        version_type        = "release"
        resolution_width    = "854"
        resolution_height   = "480"
        natives_directory   = $nativesDir
        classpath           = $cp
        library_directory   = (Resolve-Path $LibrariesDir).Path
        classpath_separator = ";"
        launcher_name       = "TestRunner"
        launcher_version    = "1"
    }

    # 4. assemble args
    $jvmArgs = Expand-Args $j.arguments.jvm $vars $osName $osArch $features
    $gameArgs = Expand-Args $j.arguments.game $vars $osName $osArch $features

    # Jars listed on the module path (-p) must NOT also be on -cp (JPMS error)
    for ($i = 0; $i -lt $jvmArgs.Count; $i++) {
        if ($jvmArgs[$i] -in @("-p", "--module-path") -and $i + 1 -lt $jvmArgs.Count) {
            $moduleJars = @($jvmArgs[$i + 1] -split ";" | ForEach-Object { $_ -replace "/", "\" })
            $cpIdx = [Array]::IndexOf($jvmArgs, "-cp")
            if ($cpIdx -ge 0 -and $cpIdx + 1 -lt $jvmArgs.Count) {
                $cpJars = @($jvmArgs[$cpIdx + 1] -split ";")
                $filtered = $cpJars | Where-Object { $moduleJars -notcontains ($_ -replace "/", "\") }
                $jvmArgs[$cpIdx + 1] = $filtered -join ";"
            }
        }
    }

    # Start-Process does not auto-quote arguments containing spaces
    $jvmArgs = @($jvmArgs | ForEach-Object { if ($_ -match " " -and $_ -notmatch '^"') { '"' + $_ + '"' } else { $_ } })
    $gameArgs = @($gameArgs | ForEach-Object { if ($_ -match " " -and $_ -notmatch '^"') { '"' + $_ + '"' } else { $_ } })

    # 5. deploy mod
    $modsDir = Join-Path $instanceDir "mods"
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    foreach ($old in @(Get-ChildItem -LiteralPath $modsDir -Filter "WurstB*.jar" -ErrorAction SilentlyContinue)) {
        if (-not $KeepOldJars) {
            Rename-Item -LiteralPath $old.FullName -NewName "$($old.BaseName).backup-$stamp.jar.disabled" -Force
        }
    }
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $modsDir (Split-Path $jarPath -Leaf)) -Force

    # 6. launch only the process tracked by this script instance
    $outDir = Join-Path $OutBase $name
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    $pidFile = Join-Path $outDir "launch.pid"
    Stop-TrackedTestProcess $pidFile $instanceDir
    $stdout = Join-Path $outDir "stdout.log"
    $stderr = Join-Path $outDir "stderr.log"
    $latestLog = Join-Path $instanceDir "logs\latest.log"
    if (Test-Path $latestLog) { Remove-Item -LiteralPath $latestLog -Force -ErrorAction SilentlyContinue }

    $argList = @("-Xmx4G") + $jvmArgs + @($j.mainClass) + $gameArgs

    Write-Info "  [launch] $($j.mainClass) args... (pid tracking)"
    $p = Start-Process -FilePath $java -ArgumentList $argList -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
        -WindowStyle Minimized
    $p.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII

    # 7. poll for result
    $crashDir = Join-Path $instanceDir "crash-reports"
    $crashSnap = @(Get-ChildItem -LiteralPath $crashDir -Filter "*.txt" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    $start = Get-Date
    $phase1At = $null
    $phase1 = $false
    $result = $null

    while ((Get-Date) - $start -lt (New-TimeSpan -Seconds $TimeoutSeconds)) {
        if ($p.HasExited) {
            $result = @{ Status = "FAIL"; Note = "process exited early, code $($p.ExitCode)" }
            break
        }
        $newCrash = @(Get-ChildItem -LiteralPath $crashDir -Filter "*.txt" -ErrorAction SilentlyContinue |
            Where-Object { $crashSnap -notcontains $_.FullName })
        if ($newCrash.Count -gt 0) {
            $note = "crash report: $($newCrash[0].Name)"
            $head = Get-Content -LiteralPath $newCrash[0].FullName -TotalCount 12 -ErrorAction SilentlyContinue
            if ($head) {
                $desc = $head | Where-Object { $_ -match "Description|Exception|Mod loading|IllegalAccess" }
                if ($desc) { $note += " | " + (($desc | Select-Object -First 2) -join " ") }
            }
            $result = @{ Status = "FAIL"; Note = $note }
            break
        }
        if ((Test-Path $latestLog) -or (Test-Path $stdout)) {
            $log = Get-RecentLogText @($stdout, $stderr, $latestLog)
            if ($log -match "FATAL|Mod Loading has failed") {
                $result = @{ Status = "FAIL"; Note = "FATAL / mod loading failure in log" }
                break
            }
            if (-not $phase1 -and $log -match "Backend library|Reloading ResourceManager|Minecraft version") {
                $phase1 = $true
                $phase1At = Get-Date
                Write-Info "  [progress] past early startup, waiting to settle..."
            }
        }
        $p.Refresh()
        $windowReady = ($p.MainWindowHandle -ne [IntPtr]::Zero)
        if ($phase1 -and $windowReady -and (Get-Date) - $phase1At -gt (New-TimeSpan -Seconds $SettleSeconds)) {
            $result = @{ Status = "PASS"; Note = "startup completed and Minecraft window is present" }
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $result) {
        $result = @{ Status = "TIMEOUT"; Note = "did not reach main menu within ${TimeoutSeconds}s" }
    }

    if (-not $p.HasExited) {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue

    $result.Elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    return $result
}

# ---------- main ----------
Write-Info "========== WurstB+ batch version test =========="
$report = @()
$jars = @(Get-ChildItem -LiteralPath $DownloadDir -Filter "*.jar" | Where-Object { Get-JarVersionInfo $_.Name })

if ($jars.Count -eq 0) {
    Write-Host "no testable jars in download/"
    exit 1
}

foreach ($jar in $jars) {
    $info = Get-JarVersionInfo $jar.Name
    if ($Version -and $info.MC -ne $Version) { continue }
    if ($Loader -and $info.Loader -ne $Loader) { continue }

    $instanceDir = Find-InstanceDir $info.MC $info.Loader
    if (-not $instanceDir) {
        Write-Host "[$($jar.Name)] ERROR: no matching instance found"
        $report += @{ Jar = $jar.Name; Version = $info.MC; Loader = $info.Loader; Status = "ERROR"; Note = "no instance"; Elapsed = "-" }
        continue
    }

    Write-Host "[$($jar.Name)] -> $((Split-Path $instanceDir -Leaf))"
    $r = Test-Version $jar.FullName $instanceDir
    $r.Jar = $jar.Name
    $r.Version = $info.MC
    $r.Loader = $info.Loader
    $report += $r

    $color = switch ($r.Status) { "PASS" { "Green" } "FAIL" { "Red" } "TIMEOUT" { "Yellow" } default { "Cyan" } }
    Write-Host ("  [{0}] {1} ({2}s) {3}" -f $r.Status, $r.Note, $r.Elapsed, $r.Jar) -ForegroundColor $color
    Write-Host ""
}

# ---------- report ----------
Write-Host "========== summary =========="
$report | ForEach-Object {
    Write-Host ("{0,-12} {1,-10} {2,-8} {3}s  {4}" -f $_.Status, $_.Loader, $_.Version, $_.Elapsed, $_.Jar)
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportFile = Join-Path $ReportDir "report-$stamp.txt"
$report | ForEach-Object {
    "Jar: $($_.Jar)`n  Loader: $($_.Loader)  MC: $($_.Version)`n  Status: $($_.Status)  Elapsed: $($_.Elapsed)s`n  Note: $($_.Note)"
} | Set-Content -LiteralPath $reportFile -Encoding UTF8
Write-Host "report saved: $reportFile"

$failed = @($report | Where-Object { $_.Status -ne "PASS" })
if ($failed.Count -gt 0) { exit 1 } else { exit 0 }
