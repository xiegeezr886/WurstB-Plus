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

# Replaces one entry inside a JAR.
#
# Why this doesn't use [System.IO.Compression.ZipArchiveMode]::Update
# ---------------------------------------------------------------
# Update mode rewrites the archive in a shape that java.util.zip.ZipInputStream
# cannot read back. Concretely, for the rewritten entry it leaves the
# "data descriptor" flag (general purpose bit 3) SET while ALSO filling in the
# compressed/uncompressed sizes in the local file header, and then writes no
# actual data descriptor. Those two things contradict each other:
#
#   correct form A: bit3 set   + local sizes 0   + descriptor after the data
#   correct form B: bit3 clear + local sizes set + no descriptor
#   Update mode  : bit3 set   + local sizes set + no descriptor   <- malformed
#
# ZipInputStream trusts bit3 and, after the entry data, reads the next 16 bytes
# looking for the descriptor signature. Because there is no descriptor, it eats
# the following entry's local header, takes its bytes 8..11 as the entry size and
# throws, e.g.
#
#   java.util.zip.ZipException: invalid entry size (expected 8 but got 181 bytes)
#       at java.util.zip.ZipInputStream.readEnd(ZipInputStream.java:399)
#
# That is exactly how ForgeGradle's SrgMcpRenamer reads jars when resolving a
# fg.deobf(...) dependency, so any project using fg.deobf on a rewritten jar
# fails to resolve its dependencies and cannot build at all.
#
# The fix is to repack the whole archive with ZipArchiveMode::Create, which
# emits the correct form B (verified: the first local header comes out as
# flags=0x0000 csize=118 usize=181 on the same jar). Entry order, names,
# timestamps and per-entry content are preserved; only the container is rebuilt.

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
$bytes = [System.Text.Encoding]::UTF8.GetBytes($content)
$temp = "$jar.$([guid]::NewGuid().ToString('N')).tmp"
$backup = $null
$replaced = $false

try {
    $src = $null
    $dst = $null
    try {
        $src = [System.IO.Compression.ZipFile]::OpenRead($jar)
        $dst = [System.IO.Compression.ZipFile]::Open(
            $temp, [System.IO.Compression.ZipArchiveMode]::Create)

        foreach ($entry in $src.Entries) {
            $target = ($entry.FullName -eq $entryName)
            if ($target -and $entry.FullName.EndsWith('/')) {
                throw "Refusing to replace '$entryName': it is a directory entry"
            }

            $new = $dst.CreateEntry($entry.FullName,
                [System.IO.Compression.CompressionLevel]::Optimal)
            # ZIP stores times at 2-second resolution; clamp before assigning or
            # .NET throws for pre-1980 / out-of-range values.
            $stamp = $entry.LastWriteTime
            if ($stamp.Year -lt 1980) { $stamp = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero) }
            $new.LastWriteTime = $stamp

            $in = $entry.Open()
            $out = $new.Open()
            try {
                if ($target) {
                    $out.Write($bytes, 0, $bytes.Length)
                    $replaced = $true
                } else {
                    $in.CopyTo($out)
                }
            } finally {
                $out.Dispose()
                $in.Dispose()
            }
        }

        if (-not $replaced) {
            $new = $dst.CreateEntry($entryName,
                [System.IO.Compression.CompressionLevel]::Optimal)
            $out = $new.Open()
            try { $out.Write($bytes, 0, $bytes.Length) } finally { $out.Dispose() }
            $replaced = $true
        }
    } finally {
        if ($dst) { $dst.Dispose() }
        if ($src) { $src.Dispose() }
    }

    # Sanity check: the repacked file must be openable and must contain the
    # entry we just wrote. This catches a truncated/partial rewrite before we
    # overwrite the original.
    $check = [System.IO.Compression.ZipFile]::OpenRead($temp)
    try {
        $found = $null
        foreach ($e in $check.Entries) { if ($e.FullName -eq $entryName) { $found = $e } }
        if (-not $found) { throw "repack verification failed: '$entryName' missing" }
        $reader = New-Object System.IO.StreamReader($found.Open(), [System.Text.Encoding]::UTF8)
        try { $roundTrip = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($roundTrip -ne $content) {
            throw "repack verification failed: '$entryName' content differs after rewrite"
        }
    } finally {
        $check.Dispose()
    }

    if (-not $NoBackup) {
        $backup = "$jar.$(Get-Date -Format 'yyyyMMdd-HHmmss').bak"
        Copy-Item -LiteralPath $jar -Destination $backup -Force
    }
    Move-Item -LiteralPath $temp -Destination $jar -Force
    Write-Output "replaced: $entryName"
    if ($backup) { Write-Output "backup: $backup" }
} finally {
    if (Test-Path -LiteralPath $temp) {
        Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
    }
}
