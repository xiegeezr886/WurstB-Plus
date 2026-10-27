# Port configuration for the Fabric 1.20.1 -> 65 x v1.5.1 sync.
# ASCII only: Windows PowerShell 5.1 reads BOM-less files as ANSI.
$ErrorActionPreference = 'Stop'

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

$script:Jdk17 = 'C:\Users\ui863\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
$script:Jdk21 = 'C:\Users\ui863\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$script:Jdk25 = 'C:\Users\ui863\.jdks\jdk25.0.4_10'

function Get-McVersionOf([string]$ProjectPath) {
    $props = Join-Path $ProjectPath 'gradle.properties'
    if (-not (Test-Path $props)) { return $null }
    foreach ($line in Get-Content -LiteralPath $props -Encoding UTF8) {
        if ($line -match '^\s*minecraft_version\s*=\s*(.+?)\s*$') {
            return $Matches[1]
        }
    }
    return $null
}

function Get-ModVersionOf([string]$ProjectPath) {
    $props = Join-Path $ProjectPath 'gradle.properties'
    if (-not (Test-Path $props)) { return $null }
    foreach ($line in Get-Content -LiteralPath $props -Encoding UTF8) {
        if ($line -match '^\s*mod_version\s*=\s*(.+?)\s*$') {
            return $Matches[1]
        }
    }
    return $null
}

function Get-PortFamily([string]$McVersion) {
    if ($McVersion -like '26.*') { return 'native' }
    if ($McVersion -in @('1.21.6', '1.21.7', '1.21.8')) { return 'wrapper_old' }
    if ($McVersion -in @('1.21.9', '1.21.10', '1.21.11')) { return 'wrapper_event' }
    return 'legacy'
}

function Get-PortJdk([string]$McVersion) {
    $v = [version]$McVersion
    if ($v -le [version]'1.20.4') { return $script:Jdk17 }
    if ($v -le [version]'1.21.11') { return $script:Jdk21 }
    return $script:Jdk25
}

function Get-PortNanoVg([string]$McVersion) {
    $v = [version]$McVersion
    if ($v -le [version]'1.20.4') { return '3.3.2' }
    if ($v -le [version]'1.21.11') { return '3.3.3' }
    if ($v -le [version]'26.2') { return '3.4.1' }
    return '3.4.3'
}

function Get-PortBuildTask([string]$Loader, [string]$McVersion, [string]$RelativePath) {
    if ($Loader -eq 'Fabric') {
        if ($McVersion -like '26.*') { return 'jar' }
        return 'remapJar'
    }
    if ($Loader -eq 'Forge') {
        if ([version]$McVersion -le [version]'1.21.1') { return 'jarJar' }
        return 'allJar'
    }
    # NeoForge root 1.20.1 is actually ForgeGradle.
    if ($RelativePath -eq 'neoforge') { return 'jarJar' }
    return 'build'
}

function Get-PortProjects {
    $projects = New-Object System.Collections.Generic.List[object]
    $roots = @(
        @{ Path = 'fabric'; Loader = 'Fabric'; IncludeVersions = $true },
        @{ Path = 'versions'; Loader = 'Forge'; IncludeVersions = $true },
        @{ Path = 'neoforge'; Loader = 'NeoForge'; IncludeVersions = $true }
    )

    foreach ($root in $roots) {
        $rootPath = Join-Path $script:RepoRoot $root.Path
        if (-not (Test-Path $rootPath)) { continue }

        # Root project, except the Forge 1.20.1 v1.6.0 source tree.
        if ($root.Path -ne 'versions') {
            $mc = Get-McVersionOf $rootPath
            $mod = Get-ModVersionOf $rootPath
            if ($root.Path -eq 'fabric') {
                # fabric/ is the source tree, not a sync target.
            } elseif ($null -ne $mc) {
                $projects.Add([pscustomobject]@{
                    RelativePath = $root.Path
                    FullPath     = $rootPath
                    Loader       = $root.Loader
                    McVersion    = $mc
                    ModVersion   = $mod
                    Family       = Get-PortFamily $mc
                    Jdk          = Get-PortJdk $mc
                    NanoVg       = Get-PortNanoVg $mc
                    BuildTask    = Get-PortBuildTask $root.Loader $mc $root.Path
                    IsSource     = $false
                })
            }
        }

        if ($root.Path -eq 'versions') {
            $versionDirs = @(Get-ChildItem -LiteralPath $rootPath -Directory |
                Where-Object { $_.Name -match '^\d' } | Sort-Object Name)
            $relPrefix = 'versions'
        } else {
            $versionsPath = Join-Path $rootPath 'versions'
            if (-not (Test-Path $versionsPath)) { continue }
            $versionDirs = @(Get-ChildItem -LiteralPath $versionsPath -Directory |
                Sort-Object Name)
            $relPrefix = "$($root.Path)/versions"
        }

        foreach ($dir in $versionDirs) {
            $mc = Get-McVersionOf $dir.FullName
            if ($null -eq $mc) { continue }
            $rel = "$relPrefix/$($dir.Name)"
            $projects.Add([pscustomobject]@{
                RelativePath = $rel
                FullPath     = $dir.FullName
                Loader       = $root.Loader
                McVersion    = $mc
                ModVersion   = Get-ModVersionOf $dir.FullName
                Family       = Get-PortFamily $mc
                Jdk          = Get-PortJdk $mc
                NanoVg       = Get-PortNanoVg $mc
                BuildTask    = Get-PortBuildTask $root.Loader $mc $rel
                IsSource     = $false
            })
        }
    }

    return $projects
}

function Assert-PortTargets {
    $projects = @(Get-PortProjects)
    $wrong = @($projects | Where-Object { $_.ModVersion -notlike '*1.5.1*' })
    if ($wrong.Count -gt 0) {
        throw "Unexpected mod_version in: $($wrong.RelativePath -join ', ')"
    }
    if ($projects.Count -ne 65) {
        throw "Expected 65 v1.5.1 targets, found $($projects.Count)."
    }
    return $projects
}
