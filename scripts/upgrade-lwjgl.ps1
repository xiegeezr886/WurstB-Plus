# Upgrade LWJGL native DLLs for a selected Minecraft instance.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Container })]
    [string]$NativesDir,
    [string]$Version = "3.3.4",
    [string]$BackupDir = "",
    [switch]$SkipChecksum,
    [switch]$AllowRunningGame
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Add-Type -AssemblyName System.IO.Compression.FileSystem

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Invalid LWJGL version: $Version"
}

$resolvedNativesDir = (Resolve-Path -LiteralPath $NativesDir).Path
$instanceName = (Split-Path -Leaf $resolvedNativesDir) -replace '-natives$', ''

if (-not $AllowRunningGame) {
    $runningGames = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -in @('java.exe', 'javaw.exe') -and
            $_.CommandLine -and
            $_.CommandLine.IndexOf($instanceName, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
        })
    if ($runningGames.Count -gt 0) {
        throw "Minecraft instance '$instanceName' appears to be running. Close it first or pass -AllowRunningGame."
    }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($BackupDir)) {
    $BackupDir = Join-Path (Split-Path -Parent $resolvedNativesDir) "lwjgl-backup-$timestamp"
} elseif (-not [System.IO.Path]::IsPathRooted($BackupDir)) {
    $BackupDir = Join-Path (Get-Location) $BackupDir
}
$backupFullPath = [System.IO.Path]::GetFullPath($BackupDir).TrimEnd('\')
$nativeFullPath = $resolvedNativesDir.TrimEnd('\')
if ($backupFullPath.Equals($nativeFullPath, [System.StringComparison]::OrdinalIgnoreCase) -or
    $backupFullPath.StartsWith($nativeFullPath + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "BackupDir must not be the natives directory or one of its children: $BackupDir"
}

$modules = @(
    "lwjgl",
    "lwjgl-glfw",
    "lwjgl-jemalloc",
    "lwjgl-openal",
    "lwjgl-opengl",
    "lwjgl-stb",
    "lwjgl-tinyfd",
    "lwjgl-freetype"
)
$tempDir = Join-Path $env:TEMP "wurstbplus-lwjgl-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$stagingDir = Join-Path $tempDir "natives"
New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null
$backupCreated = $false
$updated = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)

function Get-Checksum($archivePath, $url) {
    foreach ($algorithm in @('SHA256', 'SHA1')) {
        try {
            $response = Invoke-WebRequest -Uri "$url.$($algorithm.ToLowerInvariant())" -UseBasicParsing -TimeoutSec 30
            $expected = ([string]$response.Content) -replace '\s+', ''
            if ($expected -notmatch '^[0-9a-fA-F]{40,64}$') { continue }
            $actual = (Get-FileHash -LiteralPath $archivePath -Algorithm $algorithm).Hash
            return [PSCustomObject]@{ Algorithm = $algorithm; Expected = $expected.ToLowerInvariant(); Actual = $actual.ToLowerInvariant() }
        } catch {
            continue
        }
    }
    return $null
}

try {
    Write-Host "Downloading LWJGL $Version native archives..." -ForegroundColor Cyan
    $archives = New-Object System.Collections.Generic.List[string]
    foreach ($module in $modules) {
        $fileName = "$module-$Version-natives-windows.jar"
        $url = "https://repo1.maven.org/maven2/org/lwjgl/$module/$Version/$fileName"
        $archivePath = Join-Path $tempDir $fileName
        Write-Host "  $fileName"
        Invoke-WebRequest -Uri $url -OutFile $archivePath -UseBasicParsing -TimeoutSec 120
        if (-not (Test-Path -LiteralPath $archivePath) -or (Get-Item -LiteralPath $archivePath).Length -lt 100) {
            throw "Downloaded archive is empty or invalid: $url"
        }
        if (-not $SkipChecksum) {
            $checksum = Get-Checksum $archivePath $url
            if (-not $checksum) {
                throw "No usable checksum was published for $fileName. Use -SkipChecksum only if this is intentional."
            }
            if ($checksum.Expected -ne $checksum.Actual) {
                throw "Checksum mismatch for $fileName ($($checksum.Algorithm))."
            }
        }
        $archives.Add($archivePath)
    }

    Write-Host "Extracting verified DLLs to a staging directory..." -ForegroundColor Cyan
    foreach ($archivePath in $archives) {
        $zip = $null
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
            $archiveDlls = @($zip.Entries | Where-Object { $_.Name -like '*.dll' -and $_.FullName -notmatch '/' })
            if ($archiveDlls.Count -eq 0) { throw "No root DLL found in $archivePath" }
            foreach ($entry in $archiveDlls) {
                $staged = Join-Path $stagingDir $entry.Name
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $staged, $true)
                $updated.Add($entry.Name) | Out-Null
            }
        } finally {
            if ($zip) { $zip.Dispose() }
        }
    }
    if ($updated.Count -eq 0) { throw "No native DLLs were extracted." }

    New-Item -ItemType Directory -Path $resolvedNativesDir -Force | Out-Null
    foreach ($staged in Get-ChildItem -LiteralPath $stagingDir -Filter '*.dll' -File) {
        $destination = Join-Path $resolvedNativesDir $staged.Name
        if (Test-Path -LiteralPath $destination) {
            if (-not $backupCreated) {
                New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
                $backupCreated = $true
            }
            Copy-Item -LiteralPath $destination -Destination (Join-Path $BackupDir $staged.Name) -Force
        }
        Copy-Item -LiteralPath $staged.FullName -Destination $destination -Force
    }
} finally {
    if (Test-Path -LiteralPath $tempDir) {
        Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Done. Updated $($updated.Count) DLL(s)." -ForegroundColor Green
if ($backupCreated) { Write-Host "Backup: $BackupDir" -ForegroundColor Yellow }
