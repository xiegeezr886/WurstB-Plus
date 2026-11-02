# Generate the Fabric 1.20.1 -> v1.5.1 port manifest from git.
# ASCII only.
param(
    [string]$Base = 'f560fb35^',
    [string]$OutDir = '_recon/port'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'PortConfig.ps1')

$repo = $script:RepoRoot
Push-Location $repo
try {
    $outPath = Join-Path $repo $OutDir
    New-Item -ItemType Directory -Force -Path $outPath | Out-Null

    $head = (git rev-parse HEAD).Trim()
    $baseSha = (git rev-parse $Base).Trim()

    # Blob hashes for HEAD and base, for idempotency and three-way merge.
    $headMap = @{}
    foreach ($line in git ls-tree -r HEAD -- fabric) {
        if ($line -match '^\d+\s+blob\s+([0-9a-f]+)\t(.+)$') {
            $headMap[$Matches[2]] = $Matches[1]
        }
    }
    $baseMap = @{}
    foreach ($line in git ls-tree -r $Base -- fabric) {
        if ($line -match '^\d+\s+blob\s+([0-9a-f]+)\t(.+)$') {
            $baseMap[$Matches[2]] = $Matches[1]
        }
    }

    function Get-Category([string]$path) {
        if ($path -match '/hud2/nanovg/') { return 'nanovg' }
        if ($path -match '/hud2/mods/') { return 'mods' }
        if ($path -match '/hud2/compat/') { return 'compat' }
        if ($path -match '/hud2/') { return 'hud-core' }
        if ($path -match '/hacks/scaffold/') { return 'scaffold' }
        if ($path -match '/hacks/aura/') { return 'aura' }
        if ($path -match '/hacks/') { return 'hack' }
        if ($path -match '/clickgui2/') { return 'clickgui' }
        if ($path -match '/gui/') { return 'gui' }
        if ($path -match '/mixin/') { return 'mixin' }
        if ($path -match '/resources/') { return 'resource' }
        if ($path -match '/src/test/') { return 'test' }
        if ($path -match 'build\.gradle$') { return 'build' }
        if ($path -match 'gradle\.properties$') { return 'build' }
        return 'core'
    }

    $entries = New-Object System.Collections.Generic.List[object]
    foreach ($line in git diff --name-status --no-renames $Base HEAD -- fabric) {
        if ($line -notmatch '^([AMDT])\t(.+)$') { continue }
        $status = $Matches[1]
        $path = $Matches[2] -replace '\\', '/'

        # Only feature-bearing source/resources + build files. Tests are
        # intentionally excluded from the first sync pass.
        if ($path -notmatch '^fabric/(src/main/|build\.gradle$|gradle\.properties$)') {
            continue
        }
        if ($path -match '/src/test/') { continue }

        $action = switch ($status) {
            'A' { 'A' }
            'M' { 'M' }
            'D' { 'D' }
            'T' { 'M' }
            default { $status }
        }
        $rel = $path.Substring('fabric/'.Length)
        $entries.Add([pscustomobject]@{
            Action     = $action
            SourcePath = $path
            RelPath    = $rel
            Category   = Get-Category $path
            HeadBlob   = if ($headMap.ContainsKey($path)) { $headMap[$path] } else { $null }
            BaseBlob   = if ($baseMap.ContainsKey($path)) { $baseMap[$path] } else { $null }
        })
    }

    $targets = @(Assert-PortTargets)
    $targetView = @()
    foreach ($t in $targets) {
        $targetView += [pscustomobject]@{
            RelativePath = $t.RelativePath
            Loader       = $t.Loader
            McVersion    = $t.McVersion
            Family       = $t.Family
            Jdk          = $t.Jdk
            NanoVg       = $t.NanoVg
            BuildTask    = $t.BuildTask
        }
    }
    $entryView = $entries.ToArray()
    $manifest = [pscustomobject]@{
        BaseCommit = $baseSha
        HeadCommit = $head
        Generated  = (Get-Date).ToString('s')
        Targets    = $targetView
        Entries    = $entryView
    }

    $jsonPath = Join-Path $outPath 'manifest.json'
    $csvPath = Join-Path $outPath 'manifest.csv'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
    $entries | Select-Object Action, Category, RelPath, HeadBlob, BaseBlob |
        Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

    $byAction = $entries | Group-Object Action | Sort-Object Name
    $byCategory = $entries | Group-Object Category | Sort-Object Name
    $summary = @()
    $summary += "base=$($baseSha.Substring(0,8)) head=$($head.Substring(0,8))"
    $summary += "targets=$($targets.Count) entries=$($entries.Count)"
    $summary += ($byAction | ForEach-Object { "action $($_.Name)=$($_.Count)" })
    $summary += ($byCategory | ForEach-Object { "category $($_.Name)=$($_.Count)" })
    $summary | Set-Content -LiteralPath (Join-Path $outPath 'manifest-summary.txt') -Encoding UTF8
    $summary | Write-Output
} finally {
    Pop-Location
}
