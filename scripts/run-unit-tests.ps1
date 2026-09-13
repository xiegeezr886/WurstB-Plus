<#
  Run the Forge 1.20.1 (v1.6) unit test suite, optionally offline.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\run-unit-tests.ps1
    powershell -ExecutionPolicy Bypass -File scripts\run-unit-tests.ps1 -Offline
    powershell -ExecutionPolicy Bypass -File scripts\run-unit-tests.ps1 -Class net.wurstclient.music.NeteaseCloudApiTest
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$Class = "",
    [switch]$Offline,
    [switch]$NoDaemon
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
$ProjectRoot = Get-WurstbProjectRoot $ProjectRoot

$jdk = Get-WurstbJdkHome "1.20.1"
if (-not $jdk) {
    throw "JDK 17 is missing. Set WURSTBPLUS_JAVA17 or install Microsoft/Temurin JDK 17."
}

$gradlew = Join-Path $ProjectRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "gradlew.bat not found in $ProjectRoot"
}

$env:JAVA_HOME = $jdk
$taskArgs = @("--console=plain")
if ($NoDaemon) { $taskArgs += "--no-daemon" }
if ($Offline) { $taskArgs += "--offline" }
$taskArgs += "test"
if (-not [string]::IsNullOrWhiteSpace($Class)) {
    $taskArgs += "--tests"
    $taskArgs += $Class
}

Write-Host "JAVA_HOME=$jdk"
Write-Host ("gradlew {0}" -f ($taskArgs -join " "))
Push-Location $ProjectRoot
try {
    & $gradlew @taskArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

$report = Join-Path $ProjectRoot "build\reports\tests\test\index.html"
if (Test-Path -LiteralPath $report) {
    Write-Host "HTML report: $report"
}
if ($code -ne 0) { exit $code }
exit 0
