param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$JarPath,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$EntryToReplace,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ContentFile,
    [switch]$NoBackup
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$jar = (Resolve-Path -LiteralPath $JarPath).Path
$contentPath = (Resolve-Path -LiteralPath $ContentFile).Path
$entryName = $EntryToReplace.Replace('\', '/')
if ([string]::IsNullOrWhiteSpace($entryName) -or $entryName.StartsWith('/') -or
    $entryName -match '(^|/)\.\.(/|$)') {
    throw "Invalid JAR entry path: $EntryToReplace"
}

$content = [System.IO.File]::ReadAllText($contentPath, [System.Text.Encoding]::UTF8)
$temp = "$jar.$([guid]::NewGuid().ToString('N')).tmp"
$backup = $null

try {
    Copy-Item -LiteralPath $jar -Destination $temp -Force
    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::Open($temp, [System.IO.Compression.ZipArchiveMode]::Update)
        foreach ($entry in @($zip.Entries)) {
            if ($entry.FullName -eq $entryName) { $entry.Delete() }
        }
        $newEntry = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
        $encoding = New-Object System.Text.UTF8Encoding($false)
        $writer = New-Object System.IO.StreamWriter($newEntry.Open(), $encoding)
        try {
            $writer.Write($content)
        } finally {
            $writer.Dispose()
        }
    } finally {
        if ($zip) { $zip.Dispose() }
    }

    if (-not $NoBackup) {
        $backup = "$jar.$(Get-Date -Format 'yyyyMMdd-HHmmss').bak"
        Copy-Item -LiteralPath $jar -Destination $backup -Force
    }
    Copy-Item -LiteralPath $temp -Destination $jar -Force
    Write-Output "replaced: $entryName"
    if ($backup) { Write-Output "backup: $backup" }
} finally {
    if (Test-Path -LiteralPath $temp) {
        Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
    }
}
