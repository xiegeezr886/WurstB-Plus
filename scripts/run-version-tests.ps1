# Batch test WurstB+ jars in download/ against .test/versions instances
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 1.21.11 -Loader NeoForge
#   powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 26.2 -QuickPlayWorld WurstSmokeFresh -BaritoneCommand '#goto 0 88 0'

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
    [string]$QuickPlayWorld = "",
    [string]$BaritoneCommand = "",
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

function Get-BaritoneResponsePattern($command) {
    $normalized = ([string]$command).Trim().ToLowerInvariant()
    if ($normalized -match '^#?help(?:\s|$)') {
        return 'All Baritone commands \(clickable\):'
    }
    if ($normalized -match '^#?(goto|surface|waypoints?\s+goto)(?:\s|$)') {
        return 'Going to:'
    }
    if ($normalized -match '^#?(stop|cancel|c)(?:\s|$)') {
        return 'ok (?:force )?canceled'
    }
    if ($normalized -match '^#?(goal|axis|invert|thisway|tunnel)(?:\s|$)') {
        return 'Goal:'
    }
    return '(?i)\[Baritone\].*(success|done|ok|goal|going to|canceled)'
}

# ---------- JDK selection ----------
$JDKs = @{
    "1.20.1" = if ($env:WURSTBPLUS_JAVA17) { $env:WURSTBPLUS_JAVA17 } else { "C:\Program Files\Java\jdk-17" }
    "1.21.1" = if ($env:WURSTBPLUS_JAVA21) { $env:WURSTBPLUS_JAVA21 } else { "C:\Program Files\Java\jdk-21" }
    "1.21.11" = if ($env:WURSTBPLUS_JAVA21) { $env:WURSTBPLUS_JAVA21 } else { "C:\Program Files\Java\jdk-21" }
    "26.1.2" = if ($env:WURSTBPLUS_JAVA25) { $env:WURSTBPLUS_JAVA25 } else { "C:\Program Files\Java\jdk-25.0.4" }
    "26.2" = if ($env:WURSTBPLUS_JAVA25) { $env:WURSTBPLUS_JAVA25 } else { "C:\Program Files\Java\jdk-25.0.4" }
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
    return @($out | Where-Object { $_ -and $_.Trim() -ne "" })
}

function Move-NamedArgumentPairsFirst($argsList, $optionNames) {
    $front = New-Object System.Collections.Generic.List[string]
    $rest = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $argsList.Count; $i++) {
        $argument = [string]$argsList[$i]
        if ($optionNames -contains $argument) {
            $front.Add($argument)
            if ($i + 1 -lt $argsList.Count) {
                $front.Add([string]$argsList[++$i])
            }
        } else {
            $rest.Add($argument)
        }
    }
    return @($front) + @($rest)
}

function Add-RawArgumentSeparatorAfterNamedPairs($argsList, $optionNames) {
    $splitAt = 0
    while ($splitAt + 1 -lt $argsList.Count -and
        $optionNames -contains [string]$argsList[$splitAt]) {
        $splitAt += 2
    }

    $result = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $splitAt; $i++) { $result.Add([string]$argsList[$i]) }
    $result.Add("--")
    for ($i = $splitAt; $i -lt $argsList.Count; $i++) { $result.Add([string]$argsList[$i]) }
    return @($result)
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
            (Get-Content -LiteralPath $path -Tail 3000 -ErrorAction SilentlyContinue) -join "`n"
        }
    }
    return ($parts -join "`n")
}

function Set-MinecraftOption($instanceDir, $name, $value) {
    $optionsPath = Join-Path $instanceDir "options.txt"
    $lines = if (Test-Path -LiteralPath $optionsPath) {
        @(Get-Content -LiteralPath $optionsPath -Encoding UTF8)
    } else {
        @()
    }
    $updated = $false
    $lines = @($lines | ForEach-Object {
        if ($_ -match "^$([regex]::Escape($name)):") {
            $updated = $true
            "${name}:${value}"
        } else {
            $_
        }
    })
    if (-not $updated) { $lines += "${name}:${value}" }
    $lines | Set-Content -LiteralPath $optionsPath -Encoding UTF8
}

function Initialize-TestOptions($instanceDir) {
    $optionsPath = Join-Path $instanceDir "options.txt"
    $backupPath = Join-Path $instanceDir "options.pre-test.txt"
    $versionLine = ""
    if (Test-Path -LiteralPath $optionsPath) {
        if (-not (Test-Path -LiteralPath $backupPath)) {
            Copy-Item -LiteralPath $optionsPath -Destination $backupPath
        }
        $versionLine = @(Get-Content -LiteralPath $optionsPath -Encoding UTF8 |
            Where-Object { $_ -match '^version:\d+$' } | Select-Object -First 1)
    }

    $lines = @()
    if ($versionLine) { $lines += $versionLine }
    $lines += @(
        "onboardAccessibility:false",
        "narrator:0",
        "lang:en_us"
    )
    [System.IO.File]::WriteAllLines($optionsPath, $lines,
        (New-Object System.Text.UTF8Encoding($false)))
}

function Ensure-WurstTestWindow {
if (-not ("WurstTestWindow" -as [type])) {
        Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class WurstTestWindow {
    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT {
        public uint type;
        public InputUnion data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion {
        [FieldOffset(0)] public KEYBDINPUT keyboard;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT {
        public ushort virtualKey;
        public ushort scanCode;
        public uint flags;
        public uint time;
        public UIntPtr extraInfo;
    }

    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern IntPtr SetFocus(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, IntPtr processId);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint attach, uint attachTo, bool value);
    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint count, INPUT[] inputs, int size);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool PostMessage(IntPtr hWnd, uint message, UIntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern uint MapVirtualKey(uint code, uint mapType);

    public static bool ForceForeground(IntPtr hWnd) {
        uint currentThread = GetCurrentThreadId();
        uint targetThread = GetWindowThreadProcessId(hWnd, IntPtr.Zero);
        IntPtr foreground = GetForegroundWindow();
        uint foregroundThread = foreground == IntPtr.Zero
            ? 0 : GetWindowThreadProcessId(foreground, IntPtr.Zero);
        bool targetAttached = targetThread != 0 && targetThread != currentThread
            && AttachThreadInput(currentThread, targetThread, true);
        bool foregroundAttached = foregroundThread != 0
            && foregroundThread != currentThread && foregroundThread != targetThread
            && AttachThreadInput(currentThread, foregroundThread, true);
        try {
            ShowWindow(hWnd, 9);
            BringWindowToTop(hWnd);
            SetForegroundWindow(hWnd);
            SetFocus(hWnd);
            return GetForegroundWindow() == hWnd;
        } finally {
            if (foregroundAttached)
                AttachThreadInput(currentThread, foregroundThread, false);
            if (targetAttached)
                AttachThreadInput(currentThread, targetThread, false);
        }
    }

    public static bool SendVirtualKey(ushort virtualKey) {
        INPUT[] inputs = new INPUT[2];
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].data.keyboard.virtualKey = virtualKey;
        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].data.keyboard.virtualKey = virtualKey;
        inputs[1].data.keyboard.flags = KEYEVENTF_KEYUP;
        return SendInput(2, inputs, Marshal.SizeOf(typeof(INPUT))) == 2;
    }

    public static bool SendUnicodeText(string text) {
        foreach (char character in text) {
            INPUT[] inputs = new INPUT[2];
            inputs[0].type = INPUT_KEYBOARD;
            inputs[0].data.keyboard.scanCode = character;
            inputs[0].data.keyboard.flags = KEYEVENTF_UNICODE;
            inputs[1].type = INPUT_KEYBOARD;
            inputs[1].data.keyboard.scanCode = character;
            inputs[1].data.keyboard.flags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;
            if (SendInput(2, inputs, Marshal.SizeOf(typeof(INPUT))) != 2)
                return false;
            System.Threading.Thread.Sleep(35);
        }
        return true;
    }

    private static bool PostVirtualKey(IntPtr hWnd, ushort virtualKey) {
        uint scanCode = MapVirtualKey(virtualKey, 0);
        IntPtr keyDownData = new IntPtr(1L | ((long)scanCode << 16));
        IntPtr keyUpData = new IntPtr(1L | ((long)scanCode << 16)
            | (1L << 30) | (1L << 31));
        return PostMessage(hWnd, 0x0100, new UIntPtr(virtualKey), keyDownData)
            && PostMessage(hWnd, 0x0101, new UIntPtr(virtualKey), keyUpData);
    }

    public static bool PostText(IntPtr hWnd, string text) {
        if (!PostVirtualKey(hWnd, 0x54))
            return false;
        System.Threading.Thread.Sleep(750);
        foreach (char character in text) {
            if (!PostMessage(hWnd, 0x0102, new UIntPtr(character), IntPtr.Zero))
                return false;
            System.Threading.Thread.Sleep(35);
        }
        System.Threading.Thread.Sleep(250);
        return PostVirtualKey(hWnd, 0x0D);
    }
}
"@
    }
}

function Send-GameText($process, $text) {
            Ensure-WurstTestWindow

    $process.Refresh()
    if ($process.MainWindowHandle -eq [IntPtr]::Zero) { return $false }
    $sent = $false
    $focused = $false
    for ($attempt = 0; $attempt -lt 3 -and -not $focused; $attempt++) {
        $focused = [WurstTestWindow]::ForceForeground($process.MainWindowHandle)
        if (-not $focused) { Start-Sleep -Milliseconds 500 }
    }
    if (-not $focused) {
        $sent = [WurstTestWindow]::PostText($process.MainWindowHandle, $text)
    } else {
        Start-Sleep -Milliseconds 750
        if ([WurstTestWindow]::SendVirtualKey(0x54)) {
            Start-Sleep -Milliseconds 1000
            if ([WurstTestWindow]::SendUnicodeText($text)) {
                Start-Sleep -Milliseconds 250
                $sent = [WurstTestWindow]::SendVirtualKey(0x0D)
            }
        }
        if (-not $sent) {
            $sent = [WurstTestWindow]::PostText($process.MainWindowHandle, $text)
        }
    }
    # 发送完成立即最小化窗口：MC 窗口获得焦点会 grab 鼠标，
    # 最小化后失焦释放鼠标，避免测试期间系统操作被接管。
    if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
        [WurstTestWindow]::ShowWindow($process.MainWindowHandle, 6) | Out-Null
    }
    return $sent
}

function Test-EmbeddedBaritone($jarPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = $null
    $nestedArchive = $null
    $buffer = $null
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
        $entry = $archive.Entries | Where-Object {
            $_.FullName -match '^META-INF/(jars|jarjar)/.*baritone.*\.jar$'
        } | Select-Object -First 1
        if (-not $entry) { return @{ Passed = $false; Note = "nested Baritone jar is missing" } }

        $buffer = New-Object System.IO.MemoryStream
        $stream = $entry.Open()
        try { $stream.CopyTo($buffer) } finally { $stream.Dispose() }
        $buffer.Position = 0
        $nestedArchive = New-Object System.IO.Compression.ZipArchive($buffer, [System.IO.Compression.ZipArchiveMode]::Read, $true)
        if (-not $nestedArchive.GetEntry("baritone/api/BaritoneAPI.class")) {
            return @{ Passed = $false; Note = "Baritone API class is missing from $($entry.FullName)" }
        }
        return @{ Passed = $true; Note = $entry.FullName }
    } catch {
        return @{ Passed = $false; Note = "Baritone archive check failed: $($_.Exception.Message)" }
    } finally {
        if ($nestedArchive) { $nestedArchive.Dispose() }
        if ($buffer) { $buffer.Dispose() }
        if ($archive) { $archive.Dispose() }
    }
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
    $requireBaritone = $mcVersion -in @("1.21.11", "26.2")
    $baritonePackage = Test-EmbeddedBaritone $jarPath
    if ($requireBaritone -and -not $baritonePackage.Passed) {
        return @{ Status = "FAIL"; Note = $baritonePackage.Note; Elapsed = 0 }
    }
    if ($QuickPlayWorld) {
        $worldPath = Join-Path $instanceDir "saves\$QuickPlayWorld"
        if (-not (Test-Path -LiteralPath (Join-Path $worldPath "level.dat"))) {
            return @{ Status = "ERROR"; Note = "quick-play world is missing: $worldPath"; Elapsed = 0 }
        }
        New-Item -ItemType Directory -Path (Join-Path $instanceDir "quickPlay") -Force | Out-Null
    }

    $java = Get-Java $mcVersion
    $osName = "windows"
    $osArch = Get-NativeArch

    # 1. build classpath
    $classpath = New-Object System.Collections.Generic.List[string]
    $features = @{
        has_custom_resolution = $false
        is_demo_user = $false
        has_quick_plays_support = [bool]$QuickPlayWorld
        is_quick_play_singleplayer = [bool]$QuickPlayWorld
        is_quick_play_multiplayer = $false
        is_quick_play_realms = $false
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
        quickPlayPath       = (Join-Path $instanceDir "quickPlay\log.json")
        quickPlaySingleplayer = $QuickPlayWorld
    }

    # 4. assemble args
    $jvmArgs = Expand-Args $j.arguments.jvm $vars $osName $osArch $features
    $gameArgs = Expand-Args $j.arguments.game $vars $osName $osArch $features

    # Forge and NeoForge consume their own launch options before forwarding
    # the remaining arguments to Minecraft. Put those pairs first so launcher
    # feature arguments such as quick play reach Minecraft unchanged.
    $loaderGameOptions = @(
        "--launchTarget",
        "--fml.neoForgeVersion",
        "--fml.mcVersion",
        "--fml.neoFormVersion"
    )
    $gameArgs = Move-NamedArgumentPairsFirst $gameArgs $loaderGameOptions
    if ($QuickPlayWorld -and $j.mainClass -eq "net.neoforged.fml.startup.Client") {
        $gameArgs = Add-RawArgumentSeparatorAfterNamedPairs $gameArgs $loaderGameOptions
    }

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
    if ($QuickPlayWorld) {
        Initialize-TestOptions $instanceDir
    }

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
    $argList | Set-Content -LiteralPath (Join-Path $outDir "launch-args.txt") -Encoding UTF8

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
    $wurstSeen = $false
    $baritoneSeen = $false
    $worldLoaded = $false
    $worldLoadedAt = $null
    $commandSent = $false
    $commandSentAt = $null
    $commandResponseSeen = $false
    $commandResponsePattern = Get-BaritoneResponsePattern $BaritoneCommand
    $result = $null

    # 预加载窗口工具类型（轮询循环会调用 ShowWindow 压制窗口弹出）
    Ensure-WurstTestWindow

    while ((Get-Date) - $start -lt (New-TimeSpan -Seconds $TimeoutSeconds)) {
        if ($p.HasExited) {
            $result = @{ Status = "FAIL"; Note = "process exited early, code $($p.ExitCode)" }
            break
        }
        # 周期压制：MC 窗口若自行弹出前台会 grab 鼠标，
        # 每轮强制最小化，保证测试期间系统操作不被接管。
        if ($p.MainWindowHandle -ne [IntPtr]::Zero) {
            [WurstTestWindow]::ShowWindow($p.MainWindowHandle, 6) | Out-Null
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
            if ($log -match "Mixin apply failed|MixinTransformerError|NoSuchFieldException: chatSession|ClientPacketListenerAccessor cannot be cast") {
                $result = @{ Status = "FAIL"; Note = "runtime Mixin / mapped-field failure in log" }
                break
            }
            if ($log -match "Starting WurstB\+ Plus|WurstB\+ Plus .*\(wurstpenguin\)|wurstpenguin [v\d]") {
                $wurstSeen = $true
            }
            if ($log -match "(?i)(\[Baritone\]|Baritone\s+1\.|baritoe\s+\(|mod/baritoe)") {
                $baritoneSeen = $true
            }
            if (-not $worldLoaded -and $log -match "(?i)(logged in with entity id|joined the game)") {
                $worldLoaded = $true
                $worldLoadedAt = Get-Date
                Write-Info "  [progress] world loaded"
            }
            if ($commandSent -and $BaritoneCommand -and
                $log -match $commandResponsePattern) {
                $commandResponseSeen = $true
            }
            if (-not $phase1 -and $log -match "Backend library|Reloading ResourceManager|Minecraft version") {
                $phase1 = $true
                $phase1At = Get-Date
                Write-Info "  [progress] past early startup, waiting to settle..."
            }
        }
        $p.Refresh()
        $windowReady = ($p.MainWindowHandle -ne [IntPtr]::Zero)
        if ($QuickPlayWorld -and $worldLoaded -and $BaritoneCommand -and -not $commandSent -and
            (Get-Date) - $worldLoadedAt -gt (New-TimeSpan -Seconds 8)) {
            if (-not (Send-GameText $p $BaritoneCommand)) {
                $result = @{ Status = "FAIL"; Note = "could not send Baritone command to Minecraft window" }
                break
            }
            $commandSent = $true
            $commandSentAt = Get-Date
            Write-Info "  [progress] sent Baritone command: $BaritoneCommand"
        }
        if ($QuickPlayWorld -and $worldLoaded -and $wurstSeen -and $baritoneSeen) {
            if (-not $BaritoneCommand -and (Get-Date) - $worldLoadedAt -gt (New-TimeSpan -Seconds $SettleSeconds)) {
                $result = @{ Status = "PASS"; Note = "world loaded; WurstB+ Plus and Baritone loaded" }
                break
            }
            if ($BaritoneCommand -and $commandResponseSeen) {
                $result = @{ Status = "PASS"; Note = "world loaded; Baritone command succeeded: $BaritoneCommand" }
                break
            }
        }
        if ($commandSent -and -not $commandResponseSeen -and
            (Get-Date) - $commandSentAt -gt (New-TimeSpan -Seconds 30)) {
            $result = @{ Status = "FAIL"; Note = "Baritone command produced no expected response: $BaritoneCommand" }
            break
        }
        if ($phase1 -and $windowReady -and (Get-Date) - $phase1At -gt (New-TimeSpan -Seconds $SettleSeconds)) {
            if ($QuickPlayWorld) {
                Start-Sleep -Seconds 2
                continue
            }
            if ($wurstSeen -and (-not $requireBaritone -or $baritoneSeen)) {
                $result = @{ Status = "PASS"; Note = "startup completed; WurstB+ Plus and Baritone loaded; package $($baritonePackage.Note)" }
                break
            }
            if ((Get-Date) - $phase1At -gt (New-TimeSpan -Seconds ($SettleSeconds + 30))) {
                $missingRuntime = @()
                if (-not $wurstSeen) { $missingRuntime += "WurstB+ Plus" }
                if ($requireBaritone -and -not $baritoneSeen) { $missingRuntime += "Baritone" }
                $result = @{ Status = "FAIL"; Note = "runtime markers missing: $($missingRuntime -join ', ')" }
                break
            }
        }
        Start-Sleep -Seconds 2
    }

    if (-not $result) {
        $timeoutTarget = if ($QuickPlayWorld) { "load quick-play world" } else { "reach main menu" }
        $result = @{ Status = "TIMEOUT"; Note = "did not $timeoutTarget within ${TimeoutSeconds}s" }
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
