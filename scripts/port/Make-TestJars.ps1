# Build _recon/port/jars-test from the production jars.
# Forge/NeoForge copies have the nested Baritone jar removed; the bundled
# Baritone builds crash on some Forge versions and NanoVG does not need them.
# ASCII only.
param(
    [string]$Src = '_recon\port\jars',
    [string]$Dst = '_recon\port\jars-test'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$srcDir = (Resolve-Path $Src).Path
New-Item -ItemType Directory -Force -Path $Dst | Out-Null
$dstDir = (Resolve-Path $Dst).Path

$copied = 0
$stripped = 0
foreach ($jar in Get-ChildItem -LiteralPath $srcDir -File -Filter '*.jar') {
    $outPath = Join-Path $dstDir $jar.Name
    $tmpPath = $outPath + '.tmp'
    $isForge = ($jar.Name -match 'Forge' -and $jar.Name -notmatch 'Fabric')
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    $baritone = @($zip.Entries | Where-Object {
        $_.FullName -match '^META-INF/jarjar/baritone-.*\.jar$'
    })
    if (-not $isForge -or $baritone.Count -eq 0) {
        $zip.Dispose()
        Copy-Item -LiteralPath $jar.FullName -Destination $outPath -Force
        $copied++
        continue
    }

    if (Test-Path -LiteralPath $tmpPath) { Remove-Item -LiteralPath $tmpPath -Force }
    $out = [System.IO.Compression.ZipFile]::Open(
        $tmpPath, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($entry.FullName -match '^META-INF/jarjar/baritone-.*\.jar$') {
                continue
            }
            $newEntry = $out.CreateEntry($entry.FullName,
                [System.IO.Compression.CompressionLevel]::Optimal)
            $in = $entry.Open()
            try {
                if ($entry.FullName -eq 'META-INF/jarjar/metadata.json') {
                    $reader = New-Object System.IO.StreamReader($in)
                    $text = $reader.ReadToEnd()
                    $reader.Dispose()
                    try {
                        $json = $text | ConvertFrom-Json
                        $json.jars = @($json.jars | Where-Object {
                            $_.identifier.artifact -notmatch 'baritone'
                        })
                        $text = $json | ConvertTo-Json -Depth 20
                    } catch {}
                    $writer = New-Object System.IO.StreamWriter(
                        $newEntry.Open(), (New-Object System.Text.UTF8Encoding($false)))
                    $writer.Write($text)
                    $writer.Dispose()
                } else {
                    $os = $newEntry.Open()
                    $in.CopyTo($os)
                    $os.Dispose()
                }
            } finally {
                $in.Dispose()
            }
        }
    } finally {
        $out.Dispose()
        $zip.Dispose()
    }
    Move-Item -LiteralPath $tmpPath -Destination $outPath -Force
    $stripped++
}
Write-Output ("copied={0} stripped={1} total={2}" -f
    $copied, $stripped, (Get-ChildItem -LiteralPath $dstDir -File |
        Measure-Object).Count)
