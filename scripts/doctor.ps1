<#
  Diagnose the local toolchain: JDKs, Gradle wrappers, and v1.6 unit-test sources.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\doctor.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
$ProjectRoot = Get-WurstbProjectRoot $ProjectRoot

Write-Host "========== WurstB+ Plus doctor =========="
Write-Host "project: $ProjectRoot"
Write-Host ""

Write-Host "-- JDKs --"
$jdks = Find-WurstbJdks
if ($jdks.Count -eq 0) {
    Write-Host "  none found in standard install locations" -ForegroundColor Yellow
} else {
    $jdks | ForEach-Object { Write-Host ("  Java {0,-3} {1}" -f $_.Major, $_.JdkHome) }
}
foreach ($mc in @("1.20.1", "1.21.1", "1.21.11", "26.1.2", "26.2", "26.3")) {
    $jdkForMc = Get-WurstbJdkHome $mc -Quiet
    $status = if ($jdkForMc) { $jdkForMc } else { "MISSING" }
    Write-Host ("  MC {0,-7} -> {1}" -f $mc, $status)
}

Write-Host ""
Write-Host "-- Gradle wrapper dists --"
$distsRoot = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
foreach ($version in $script:WurstbWrapperDists.Keys) {
    $hash = $script:WurstbWrapperDists[$version]
    $dst = Join-Path $distsRoot "gradle-$version-bin\$hash"
    $ready = (Test-Path (Join-Path $dst "gradle-$version\bin\gradle.bat")) -and
        (Test-Path (Join-Path $dst "gradle-$version-bin.zip.ok"))
    Write-Host ("  gradle-{0,-8} ready={1}" -f $version, $ready)
}

Write-Host ""
Write-Host "-- Project wrappers --"
$missingJars = 0
foreach ($project in Get-WurstbGradleProjects $ProjectRoot) {
    $dir = if ([string]::IsNullOrWhiteSpace($project.Dir)) { $ProjectRoot } else { Join-Path $ProjectRoot $project.Dir }
    $gradlew = Join-Path $dir "gradlew.bat"
    $jar = Get-WurstbWrapperJarPath $ProjectRoot $project.Dir
    $okGradlew = Test-Path -LiteralPath $gradlew
    $okJar = (Test-Path -LiteralPath $jar) -and ((Get-Item -LiteralPath $jar).Length -ge 40000)
    if (-not $okJar) { $missingJars++ }
    $mark = if ($okGradlew -and $okJar) { "OK" } else { "MISSING" }
    Write-Host ("  {0,-18} wrapper={1}" -f $project.Name, $mark)
}

Write-Host ""
Write-Host "-- Root unit tests --"
$testRoot = Join-Path $ProjectRoot "src\test\java"
$testFiles = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter "*Test.java" -ErrorAction SilentlyContinue)
Write-Host ("  test classes: {0}" -f $testFiles.Count)
$v16 = @(
    "net\wurstclient\music\NeteaseCloudApiTest.java"
    "net\wurstclient\music\LyricParserTest.java"
    "net\wurstclient\music\apple\LyricWordSplitterTest.java"
    "net\wurstclient\music\apple\SpringTest.java"
    "net\wurstclient\music\apple\AppleTimelineTest.java"
    "net\wurstclient\music\apple\AppleLayoutTest.java"
    "net\wurstclient\compose\AnimFloatTest.java"
    "net\wurstclient\compose\ModuleColorsTest.java"
)
$missingTests = @($v16 | Where-Object { -not (Test-Path -LiteralPath (Join-Path $testRoot $_)) })
if ($missingTests.Count -eq 0) {
    Write-Host "  v1.6 baseline tests present"
} else {
    Write-Host ("  missing v1.6 tests: {0}" -f ($missingTests -join ", ")) -ForegroundColor Yellow
}

Write-Host ""
if ($missingJars -gt 0) {
    Write-Host "Run scripts\seed-gradle-wrapper.ps1 to restore wrapper jars / dists." -ForegroundColor Yellow
    exit 1
}
Write-Host "doctor: OK"
exit 0
