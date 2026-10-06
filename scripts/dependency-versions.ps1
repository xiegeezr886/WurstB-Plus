#Requires -Version 5.1
<#
.SYNOPSIS
    Keeps dependency versions consistent across all 67 independent Gradle builds.

.DESCRIPTION
    The architecture audit found that the repository holds 67 INDEPENDENT Gradle
    builds, each declaring its own dependency coordinates, with no shared build
    logic, no version catalog and no lockfile.  Bumping one dependency therefore
    meant hand-editing up to 67 build.gradle files, and nothing noticed when a
    project drifted.

    This tool makes gradle/wurstb-versions.properties the single source of truth:

      -Check                    Fail if any build.gradle uses a version that is
                                not declared in the register, or if a declared
                                version is used by no project.  Use in CI.
      -Report                   Print the coordinate/version/project matrix.
      -Bump <g:a> <from> <to>   Preview (or with -Apply, perform) a version bump
                                across every build.gradle that uses <from>.

    Line endings are preserved byte-for-byte: files are read and written as
    UTF-8 text without normalising newlines.  The 67 build.gradle files currently
    mix LF, CRLF and mixed endings, so normalising would rewrite every file
    wholesale and destroy the diff.

.PARAMETER Check
    Verify consistency.  Exit code 1 on any inconsistency.

.PARAMETER Report
    Print the full matrix and any conflicts.

.PARAMETER Bump
    Coordinate to bump, as "group:artifact".

.PARAMETER From
    The version currently in use that should be replaced.

.PARAMETER To
    The replacement version.

.PARAMETER Apply
    With -Bump, actually write the files.  Without it, only preview.

.EXAMPLE
    pwsh -File scripts/dependency-versions.ps1 -Check
    pwsh -File scripts/dependency-versions.ps1 -Report
    pwsh -File scripts/dependency-versions.ps1 -Bump "io.netty:netty-codec-socks" "4.1.82.Final" "4.1.118.Final"
    pwsh -File scripts/dependency-versions.ps1 -Bump "io.netty:netty-codec-socks" "4.1.82.Final" "4.1.118.Final" -Apply
#>
[CmdletBinding()]
param(
    [switch]$Check,
    [switch]$Report,
    [string]$Bump,
    [string]$From,
    [string]$To,
    [switch]$Apply,
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) {
    if ($PSScriptRoot -and (Test-Path (Join-Path (Split-Path -Parent $PSScriptRoot) 'settings.gradle'))) {
        $RepoRoot = Split-Path -Parent $PSScriptRoot
    }
    else {
        $probe = (Get-Location).Path
        while ($probe -and -not (Test-Path (Join-Path $probe 'settings.gradle'))) {
            $parent = Split-Path -Parent $probe
            if ($parent -eq $probe) { break }
            $probe = $parent
        }
        if (-not $probe) { throw 'Cannot locate the repository root. Pass -RepoRoot explicitly.' }
        $RepoRoot = $probe
    }
}
Set-Location $RepoRoot

$RegisterPath = Join-Path $RepoRoot 'gradle\wurstb-versions.properties'
if (-not (Test-Path $RegisterPath)) { throw "Version register not found: $RegisterPath" }

# --- load the register -------------------------------------------------------
$register = @{}
foreach ($line in [System.IO.File]::ReadAllLines($RegisterPath)) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    $i = $t.IndexOf('=')
    if ($i -lt 1) { continue }
    $key = $t.Substring(0, $i).Trim()
    $val = $t.Substring($i + 1).Trim()
    $register[$key] = @($val -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
}

# --- locate the 67 build files ----------------------------------------------
function Get-BuildFiles {
    $list = New-Object System.Collections.Generic.List[string]
    foreach ($rel in @('build.gradle', 'fabric\build.gradle', 'neoforge\build.gradle')) {
        $p = Join-Path $RepoRoot $rel
        if (Test-Path $p) { $list.Add($p) }
    }
    foreach ($grp in @('versions', 'fabric\versions', 'neoforge\versions')) {
        $g = Join-Path $RepoRoot $grp
        if (-not (Test-Path $g)) { continue }
        foreach ($d in Get-ChildItem $g -Directory | Sort-Object Name) {
            $p = Join-Path $d.FullName 'build.gradle'
            if (Test-Path $p) { $list.Add($p) }
        }
    }
    return $list
}
$buildFiles = Get-BuildFiles

# Matches 'group:artifact:version' and 'group:artifact:version:classifier'.
# The version must start with a digit, which excludes ${...} templates,
# version ranges like [0.5.4,) and project(...) references.
$coordRe = [regex]"(?<q>['""])(?<g>[A-Za-z0-9_\.\-]+):(?<a>[A-Za-z0-9_\.\-]+):(?<v>[0-9][^'""\s:]*)(?::(?<c>[A-Za-z0-9_\.\-]+))?(?<q2>['""])"

function Get-Coordinates([string]$path) {
    $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $found = New-Object System.Collections.Generic.List[object]
    foreach ($line in ($raw -split "`n")) {
        $code = $line
        # Strip // comments so commented-out declarations are not counted, but
        # keep it simple: only drop the comment tail when it starts the line's
        # code section, which covers the "// annotationProcessor ..." pattern.
        $ci = $code.IndexOf('//')
        if ($ci -ge 0) {
            $before = $code.Substring(0, $ci)
            if ($before.Trim() -eq '' ) { continue }
            $code = $before
        }
        foreach ($m in $coordRe.Matches($code)) {
            $found.Add([PSCustomObject]@{
                Group = $m.Groups['g'].Value
                Artifact = $m.Groups['a'].Value
                Version = $m.Groups['v'].Value
                Key = "$($m.Groups['g'].Value):$($m.Groups['a'].Value)"
            })
        }
    }
    return $found
}

# --- build the matrix --------------------------------------------------------
$usage = @{}     # key -> @{ version -> List[projectRelativePath] }
$parseErrors = New-Object System.Collections.Generic.List[string]
foreach ($f in $buildFiles) {
    $rel = $f.Substring($RepoRoot.Length + 1).Replace('\', '/')
    foreach ($c in (Get-Coordinates $f)) {
        if (-not $usage.ContainsKey($c.Key)) { $usage[$c.Key] = @{} }
        if (-not $usage[$c.Key].ContainsKey($c.Version)) {
            $usage[$c.Key][$c.Version] = New-Object System.Collections.Generic.List[string]
        }
        $usage[$c.Key][$c.Version].Add($rel)
    }
}

# --- report ------------------------------------------------------------------
if ($Report -or (-not $Check -and -not $Bump)) {
    Write-Host ''
    Write-Host ("Dependency matrix — {0} build files, {1} coordinates" -f $buildFiles.Count, $usage.Count) -ForegroundColor Cyan
    Write-Host ''
    foreach ($key in ($usage.Keys | Sort-Object)) {
        $versions = $usage[$key]
        $declared = if ($register.ContainsKey($key)) { $register[$key] } else { $null }
        $mark = if ($versions.Count -gt 1) { 'CONFLICT' } elseif ($null -eq $declared) { 'UNDECLARED' } else { '' }
        Write-Host ("  {0,-46} {1}" -f $key, $mark) -ForegroundColor $(if ($mark) { 'Yellow' } else { 'Gray' })
        foreach ($v in ($versions.Keys | Sort-Object)) {
            $ok = if ($declared -and $declared -contains $v) { ' ' } else { '!' }
            Write-Host ("    {0} {1,-22} x{2}" -f $ok, $v, $versions[$v].Count)
        }
    }
}

# --- check -------------------------------------------------------------------
if ($Check) {
    $problems = New-Object System.Collections.Generic.List[string]

    foreach ($key in $usage.Keys) {
        if (-not $register.ContainsKey($key)) {
            $problems.Add("UNDECLARED coordinate '$key' is used by $($usage[$key].Values[0].Count) build file(s) but is missing from the register")
            continue
        }
        foreach ($v in $usage[$key].Keys) {
            if ($register[$key] -notcontains $v) {
                $where = ($usage[$key][$v] | Select-Object -First 3) -join ', '
                $problems.Add("UNREGISTERED version '${key}:${v}' used in $($usage[$key][$v].Count) file(s) (e.g. $where); register allows: $($register[$key] -join ', ')")
            }
        }
    }

    foreach ($key in $register.Keys) {
        if (-not $usage.ContainsKey($key)) {
            $problems.Add("STALE register entry '$key' — declared but used by no build file")
            continue
        }
        foreach ($v in $register[$key]) {
            if (-not $usage[$key].ContainsKey($v)) {
                $problems.Add("STALE register version '${key}:${v}' — declared but used by no build file")
            }
        }
    }

    if ($problems.Count -eq 0) {
        Write-Host ''
        Write-Host ("OK: all versions in {0} build files match the register." -f $buildFiles.Count) -ForegroundColor Green
        Write-Host ''
        Write-Host 'Documented, intentional multi-version coordinates:' -ForegroundColor Cyan
        foreach ($key in ($usage.Keys | Sort-Object)) {
            if ($usage[$key].Count -gt 1) {
                Write-Host ("  {0}: {1}" -f $key, (($usage[$key].Keys | Sort-Object) -join ', ')) -ForegroundColor Gray
            }
        }
        exit 0
    }

    Write-Host ''
    Write-Host ("CHECK FAILED — {0} problem(s):" -f $problems.Count) -ForegroundColor Red
    foreach ($p in $problems) { Write-Host "  - $p" -ForegroundColor Red }
    exit 1
}

# --- bump --------------------------------------------------------------------
if ($Bump) {
    if (-not $From -or -not $To) { throw '-Bump requires both -From and -To.' }
    if (-not $register.ContainsKey($Bump)) { throw "'$Bump' is not in the register ($RegisterPath)." }

    $changed = New-Object System.Collections.Generic.List[string]
    $totalHits = 0

    foreach ($f in $buildFiles) {
        $raw = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
        # Only replace inside a quoted coordinate literal for this exact coordinate.
        $pattern = "(?<pre>['""]" + [regex]::Escape($Bump) + ":)" + [regex]::Escape($From) + "(?<post>[^A-Za-z0-9_\.\-])"
        $rx = [regex]$pattern
        $hits = $rx.Matches($raw).Count
        if ($hits -eq 0) { continue }

        $newText = $rx.Replace($raw, '${pre}' + $To + '${post}')
        $rel = $f.Substring($RepoRoot.Length + 1).Replace('\', '/')
        $totalHits += $hits
        $changed.Add("$rel ($hits)")

        if ($Apply) {
            # Preserve the file's exact byte-level newlines and encoding.
            [System.IO.File]::WriteAllText($f, $newText, (New-Object System.Text.UTF8Encoding($false)))
        }
    }

    Write-Host ''
    Write-Host ("{0} {1}: {2} -> {3}" -f $(if ($Apply) { 'Applied' } else { 'Would apply' }), $Bump, $From, $To) -ForegroundColor Cyan
    if ($changed.Count -eq 0) {
        Write-Host "  No build file uses ${Bump}:${From}" -ForegroundColor Yellow
    }
    else {
        foreach ($c in $changed) { Write-Host "  $c" }
        Write-Host ("  {0} replacement(s) in {1} file(s)" -f $totalHits, $changed.Count)
    }
    if (-not $Apply) { Write-Host '  (dry run — pass -Apply to write)' -ForegroundColor Yellow }
    else {
        Write-Host ''
        Write-Host "Now update the register: $Bump = $To" -ForegroundColor Yellow
        Write-Host 'then run -Check again.' -ForegroundColor Yellow
    }
}
