# Compile every 1.5.1 target and write a PASS/FAIL matrix.
# ASCII only.
param(
    [string]$Band = '',
    [string]$Only = ''
)

$ErrorActionPreference = 'Stop'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. (Join-Path $PSScriptRoot 'PortConfig.ps1')

$repo = $script:RepoRoot
$init = Join-Path $repo 'gradle\init-mirrors.gradle'
$outDir = Join-Path $repo '_recon\port\compile-matrix'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

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

$rows = @()
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
foreach ($p in $projects) {
    $env:JAVA_HOME = $p.Jdk
    $env:JAVA_TOOL_OPTIONS = ''
    $name = $p.RelativePath.Replace('/', '__')
    $log = Join-Path $outDir ($name + '.log')
    Push-Location $p.FullPath
    try {
        & .\gradlew.bat compileJava --init-script $init --console=plain --no-configuration-cache 2>&1 |
            Out-File -FilePath $log -Encoding UTF8
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $status = if ($code -eq 0) { 'PASS' } else { 'FAIL' }
    $rows += [pscustomobject]@{
        Project = $p.RelativePath
        Loader = $p.Loader
        MC = $p.McVersion
        Status = $status
        Exit = $code
    }
    Write-Output ("{0,-28} {1,-5} {2}" -f $p.RelativePath, $status, $p.McVersion)
}

$ErrorActionPreference = $oldEap
$rows | Export-Csv -LiteralPath (Join-Path $outDir ('matrix-' + ($Band, 'all')[$Band -eq ''] + '.csv')) -NoTypeInformation -Encoding UTF8
$pass = @($rows | Where-Object { $_.Status -eq 'PASS' }).Count
$fail = @($rows | Where-Object { $_.Status -eq 'FAIL' }).Count
Write-Output ("PASS={0} FAIL={1} TOTAL={2}" -f $pass, $fail, $rows.Count)
