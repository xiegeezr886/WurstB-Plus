# Launch every built 1.5.1 jar in its .test instance and verify NanoVG.
# ASCII only: Windows PowerShell 5.1 reads BOM-less files as ANSI.
param(
    [string]$Band = '',
    [string]$Only = '',
    [string]$JarDir = '',
    [int]$SettleSeconds = 30,
    [int]$TimeoutSeconds = 300,
    [switch]$SkipPassed
)

$ErrorActionPreference = 'Stop'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. (Join-Path $PSScriptRoot 'PortConfig.ps1')

$repo = $script:RepoRoot
$jarDir = if ($JarDir) { $JarDir } else { Join-Path $repo '_recon\port\jars' }
$evidence = Join-Path $repo '_recon\port\evidence\nanovg'
$outDir = Join-Path $repo '_recon\port\test-matrix'
$runner = Join-Path $repo 'scripts\run-version-tests.ps1'
$reportPath = Join-Path $repo '_recon\port\test-report.tsv'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

if (-not (Test-Path -LiteralPath $jarDir)) {
    throw "jar directory missing: $jarDir"
}

# run-version-tests discovers JDKs through these environment variables.
$env:WURSTBPLUS_JAVA17 = $script:Jdk17
$env:WURSTBPLUS_JAVA21 = $script:Jdk21
$env:WURSTBPLUS_JAVA25 = $script:Jdk25

$projects = @(Assert-PortTargets)
if ($Only) { $projects = @($projects | Where-Object { $_.RelativePath -like $Only }) }
if ($Band) {
    $projects = @($projects | Where-Object {
        if ($Band -eq '17') { $_.Jdk -like '*17*' }
        elseif ($Band -eq '21') { $_.Jdk -like '*21*' }
        elseif ($Band -eq '25') { $_.Jdk -like '*25*' }
        else { $true }
    })
}

$previous = @()
if ($SkipPassed -and (Test-Path -LiteralPath $reportPath)) {
    $previous = @(Import-Csv -LiteralPath $reportPath -Delimiter "`t" -Encoding UTF8)
}

function Find-BuiltJar($project) {
    $pattern = '-' + [regex]::Escape($project.Loader) + '-' +
        [regex]::Escape($project.McVersion) + '(?:-[A-Za-z0-9]+)?\.jar$'
    $candidates = @(Get-ChildItem -LiteralPath $jarDir -File -Filter '*.jar' |
        Where-Object { $_.Name -match $pattern -and $_.Name -notmatch '-(sources|javadoc|dev|slim)\.jar$' })
    if ($candidates.Count -eq 0) { return $null }
    return ($candidates | Sort-Object Length -Descending | Select-Object -First 1)
}

function Get-InstanceDir($mc, $loader) {
    $versionsRoot = Join-Path $repo '.test\versions'
    foreach ($d in Get-ChildItem -LiteralPath $versionsRoot -Directory) {
        if ($d.Name -notmatch "^$([regex]::Escape($mc))-") { continue }
        if ($loader -eq 'NeoForge' -and $d.Name -notmatch "(?i)^$([regex]::Escape($mc))-neoforge[-_]") { continue }
        if ($loader -eq 'Fabric' -and $d.Name -notmatch "(?i)^$([regex]::Escape($mc))-fabric[ _-]") { continue }
        if ($loader -eq 'Forge' -and $d.Name -notmatch "(?i)^$([regex]::Escape($mc))-forge[-_]") { continue }
        return $d.FullName
    }
    return $null
}

$rows = @()
$merged = @{}
if (Test-Path -LiteralPath $reportPath) {
    foreach ($r in @(Import-Csv -LiteralPath $reportPath -Delimiter "`t" -Encoding UTF8)) {
        $merged[$r.Project] = $r
    }
}
foreach ($p in $projects) {
    if ($SkipPassed) {
        $prev = @($previous | Where-Object { $_.Project -eq $p.RelativePath -and $_.Status -eq 'PASS' })
        if ($prev.Count -gt 0) {
            $rows += $prev[0]
            Write-Output ("{0,-28} SKIP (PASS)" -f $p.RelativePath)
            continue
        }
    }

    $jar = Find-BuiltJar $p
    $instance = Get-InstanceDir $p.McVersion $p.Loader
    $row = [ordered]@{
        Project = $p.RelativePath
        Loader  = $p.Loader
        MC      = $p.McVersion
        Jar     = if ($jar) { $jar.Name } else { '' }
        Status  = ''
        Note    = ''
    }

    if (-not $jar) {
        $row.Status = 'NO-JAR'
        $row.Note = 'build the jar first (Build-PortMatrix.ps1)'
        $rows += [pscustomobject]$row
        Write-Output ("{0,-28} NO-JAR" -f $p.RelativePath)
        continue
    }
    if (-not $instance) {
        $row.Status = 'NO-INSTANCE'
        $row.Note = 'no .test/versions instance'
        $rows += [pscustomobject]$row
        Write-Output ("{0,-28} NO-INSTANCE" -f $p.RelativePath)
        continue
    }
    $world = Join-Path $instance 'saves\WurstSmokeFresh\level.dat'
    if (-not (Test-Path -LiteralPath $world)) {
        $row.Status = 'NO-WORLD'
        $row.Note = 'WurstSmokeFresh missing in instance'
        $rows += [pscustomobject]$row
        Write-Output ("{0,-28} NO-WORLD" -f $p.RelativePath)
        continue
    }

    # Reuse the Fabric dev world for this exact MC version. The flat xray-test
    # world avoids the Forge 1.21.4 LootModifierManager leaf-tick bug and the
    # 26.x "upgrade this world?" dialogs.
    $saves = Join-Path $repo ('fabric\versions\' + $p.McVersion + '\run\saves')
    $devWorld = $null
    foreach ($candidate in @('xray-test', 'WurstSmokeFresh')) {
        $path = Join-Path $saves $candidate
        if (Test-Path -LiteralPath (Join-Path $path 'level.dat')) {
            $devWorld = $path
            break
        }
    }
    if ($devWorld) {
        $destWorld = Join-Path $instance 'saves\WurstSmokeFresh'
        if (Test-Path -LiteralPath $destWorld) {
            Remove-Item -LiteralPath $destWorld -Recurse -Force
        }
        Copy-Item -LiteralPath $devWorld -Destination $destWorld -Recurse -Force
    }

    $log = Join-Path $outDir ($p.RelativePath.Replace('/', '__') + '.log')
    $invoke = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner,
        '-DownloadDir', $jarDir,
        '-Version', $p.McVersion,
        '-Loader', $p.Loader,
        '-QuickPlayWorld', 'WurstSmokeFresh',
        '-SettleSeconds', $SettleSeconds,
        '-TimeoutSeconds', $TimeoutSeconds,
        '-VerifyNanoVg',
        '-ScreenshotDir', $evidence,
        '-Quiet'
    )
    if ($p.McVersion -like '26.*') {
        $invoke += @('-GraphicsBackend', 'opengl', '-VisibleWindow')
    }

    Write-Output ("=== {0} ({1} {2}) ===" -f $p.RelativePath, $p.Loader, $p.McVersion)
    $output = & powershell @invoke 2>&1 | Tee-Object -FilePath $log
    $status = 'FAIL'
    $note = ''
    $hit = @($output | Where-Object { $_ -match '^\s*\[(PASS|FAIL|TIMEOUT|ERROR)\]' })
    if ($hit.Count -gt 0) {
        if ($hit[-1] -match '\[PASS\]') { $status = 'PASS' }
        elseif ($hit[-1] -match '\[TIMEOUT\]') { $status = 'TIMEOUT' }
        elseif ($hit[-1] -match '\[ERROR\]') { $status = 'ERROR' }
        $note = ($hit[-1] -replace '^\s*\[[A-Z]+\]\s*', '')
    } else {
        $note = 'runner produced no status line'
    }
    $row.Status = $status
    $row.Note = $note
    $rows += [pscustomobject]$row
    Write-Output ("{0,-28} {1}" -f $p.RelativePath, $status)
}

foreach ($r in $rows) { $merged[$r.Project] = $r }
$final = @($merged.Values | Sort-Object Project)
$final | Export-Csv -LiteralPath $reportPath -NoTypeInformation -Delimiter "`t" -Encoding UTF8
$pass = @($final | Where-Object { $_.Status -eq 'PASS' }).Count
$fail = @($final | Where-Object { $_.Status -ne 'PASS' }).Count
Write-Output ("PASS={0} OTHER={1} TOTAL={2} report={3}" -f $pass, $fail, $final.Count, $reportPath)
if ($fail -gt 0) { exit 1 }
