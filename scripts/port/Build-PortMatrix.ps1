# Build every 1.5.1 target with its production task and validate the jar.
# ASCII only: Windows PowerShell 5.1 reads BOM-less files as ANSI.
param(
    [string]$Band = '',
    [string]$Only = '',
    [switch]$SkipExisting,
    [switch]$Clean,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. (Join-Path $PSScriptRoot 'PortConfig.ps1')

$repo = $script:RepoRoot
$init = Join-Path $repo 'gradle\init-mirrors.gradle'
$outDir = Join-Path $repo '_recon\port\build-matrix'
$jarDir = Join-Path $repo '_recon\port\jars'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null

$projects = @(Assert-PortTargets)
if ($Only) {
    $projects = @($projects | Where-Object { $_.RelativePath -like $Only })
}
if ($Band) {
    $projects = @($projects | Where-Object {
        if ($Band -eq '17') { $_.Jdk -like '*17*' }
        elseif ($Band -eq '21') { $_.Jdk -like '*21*' }
        elseif ($Band -eq '25') { $_.Jdk -like '*25*' }
        else { $true }
    })
}

function Find-ProductionJar([string]$buildDir, [string]$loader, [string]$mc) {
    $libs = Join-Path $buildDir 'libs'
    if (-not (Test-Path $libs)) { return $null }
    $candidates = @(Get-ChildItem -LiteralPath $libs -File -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|dev|slim)\.jar$' })
    if ($candidates.Count -eq 0) { return $null }
    $pattern = '-' + [regex]::Escape($loader) + '-' + [regex]::Escape($mc) +
        '(?:-[A-Za-z0-9]+)?\.jar$'
    $named = @($candidates | Where-Object { $_.Name -match $pattern })
    if ($named.Count -gt 0) { $candidates = $named }
    return ($candidates | Sort-Object Length -Descending | Select-Object -First 1)
}

function Test-ProductionJar([string]$path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        $names = @($zip.Entries | ForEach-Object { $_.FullName })
        $mods = @($names | Where-Object { $_ -like 'net/wurstclient/hud2/mods/*.class' })
        $fonts = @($names | Where-Object { $_ -like 'assets/wurstpenguin/nanovg/fonts/*' })
        $natives = @($names | Where-Object {
            $_ -match '(^|/)(lwjgl_nanovg\.dll|liblwjgl_nanovg\.so|liblwjgl_nanovg\.dylib)$'
        })
        $oldHud = @($names | Where-Object { $_ -like 'net/wurstclient/hud2/elements/*' })
        $rootModule = @($names | Where-Object { $_ -eq 'module-info.class' })
        $required = @(
            'net/wurstclient/hud2/HudManager.class',
            'net/wurstclient/hud2/nanovg/NanoVGContext.class',
            'net/wurstclient/hud2/nanovg/NanoVGUI.class'
        )
        $missing = @($required | Where-Object { $names -notcontains $_ })
        $ok = ($missing.Count -eq 0) -and ($mods.Count -ge 40) `
            -and ($fonts.Count -ge 4) -and ($natives.Count -ge 6) `
            -and ($oldHud.Count -eq 0) -and ($rootModule.Count -eq 0)
        return [pscustomobject]@{
            Ok       = $ok
            Mods     = $mods.Count
            Fonts    = $fonts.Count
            Natives  = $natives.Count
            OldHud   = $oldHud.Count
            RootMod  = $rootModule.Count
            Missing  = ($missing -join ',')
        }
    } finally {
        $zip.Dispose()
    }
}

$rows = @()
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
foreach ($p in $projects) {
    $env:JAVA_HOME = $p.Jdk
    $env:JAVA_TOOL_OPTIONS = ''
    $name = $p.RelativePath.Replace('/', '__')
    $log = Join-Path $outDir ($name + '.log')
    Write-Output ("=== {0} [{1} {2} task={3}] ===" -f
        $p.RelativePath, $p.Loader, $p.McVersion, $p.BuildTask)

    if ($DryRun) {
        $rows += [pscustomobject]@{
            Project = $p.RelativePath; Loader = $p.Loader; MC = $p.McVersion
            Task = $p.BuildTask; Status = 'DRY'; Jar = ''
            Mods = ''; Fonts = ''; Natives = ''; Detail = ''
        }
        continue
    }

    Push-Location $p.FullPath
    try {
        $tasks = if ($Clean) { @('clean', $p.BuildTask) } else { @($p.BuildTask) }
        & .\gradlew.bat @tasks --init-script $init --console=plain `
            --no-configuration-cache 2>&1 | Out-File -FilePath $log -Encoding UTF8
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    $jar = Find-ProductionJar (Join-Path $p.FullPath 'build') $p.Loader $p.McVersion
    $status = if ($code -ne 0) { 'BUILD-FAIL' } elseif ($null -eq $jar) { 'NO-JAR' } else { 'CHECK' }
    $check = $null
    $copied = ''
    if ($code -eq 0 -and $null -ne $jar) {
        $check = Test-ProductionJar $jar.FullName
        $copied = Join-Path $jarDir $jar.Name
        if ($SkipExisting -and (Test-Path $copied)) {
            # keep existing artifact
        } else {
            Copy-Item -LiteralPath $jar.FullName -Destination $copied -Force
        }
        $status = if ($check.Ok) { 'PASS' } else { 'JAR-INVALID' }
    }

    $rows += [pscustomobject]@{
        Project = $p.RelativePath
        Loader  = $p.Loader
        MC      = $p.McVersion
        Task    = $p.BuildTask
        Status  = $status
        Jar     = if ($jar) { $jar.Name } else { '' }
        Mods    = if ($check) { $check.Mods } else { '' }
        Fonts   = if ($check) { $check.Fonts } else { '' }
        Natives = if ($check) { $check.Natives } else { '' }
        Detail  = if ($check) {
            ((@(
                if ($check.Missing) { 'missing=' + $check.Missing } else { $null }
                if ($check.OldHud -gt 0) { 'oldHud=' + $check.OldHud } else { $null }
                if ($check.RootMod -gt 0) { 'rootModule' } else { $null }
            ) | Where-Object { $_ }) -join ';')
        } else { '' }
    }
    Write-Output ("{0,-28} {1,-11} {2} mods={3} fonts={4} natives={5}" -f
        $p.RelativePath, $status, $p.McVersion,
        $(if ($check) { $check.Mods } else { '-' }),
        $(if ($check) { $check.Fonts } else { '-' }),
        $(if ($check) { $check.Natives } else { '-' }))
}

$ErrorActionPreference = $oldEap
$report = Join-Path $repo '_recon\port\build-report.tsv'
$rows | Export-Csv -LiteralPath $report -NoTypeInformation -Delimiter "`t" -Encoding UTF8
$pass = @($rows | Where-Object { $_.Status -eq 'PASS' }).Count
$fail = @($rows | Where-Object { $_.Status -ne 'PASS' }).Count
Write-Output ("PASS={0} OTHER={1} TOTAL={2} report={3}" -f $pass, $fail, $rows.Count, $report)
