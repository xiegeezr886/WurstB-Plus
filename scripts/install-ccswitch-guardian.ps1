<#
  Register the CC Switch guardian as a current-user logon task.
  Existing tasks are never replaced unless -Replace is specified.
#>

[CmdletBinding()]
param(
    [string]$TaskName = "CCSwitchGuardian",
    [switch]$Replace,
    [switch]$UseLocalProxy,
    [string]$ApiBaseUrl = "",
    [switch]$PersistUserEnvironment,
    [switch]$RunWithHighestPrivileges,
    [bool]$StartProxy = $true
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "ccswitch-guardian.ps1"
if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw "Guardian script is missing: $scriptPath"
}

$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) {
    if (-not $Replace) {
        throw "Scheduled task '$TaskName' already exists. Re-run with -Replace to update it."
    }
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

$arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$scriptPath`""
if (-not $StartProxy) { $arguments += " -StartProxy:`$false" }
if ($UseLocalProxy) { $arguments += " -UseLocalProxy" }
if ($PersistUserEnvironment) { $arguments += " -PersistUserEnvironment" }
if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    $escapedApiBaseUrl = $ApiBaseUrl.Replace('"', '`"')
    $arguments += " -ApiBaseUrl `"$escapedApiBaseUrl`""
}

$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RunOnlyIfNetworkAvailable `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew

$registerArgs = @{
    TaskName = $TaskName
    Trigger = $trigger
    Action = $action
    Settings = $settings
    Description = "CC Switch Proxy Guardian for Codex"
}
if ($RunWithHighestPrivileges) { $registerArgs.RunLevel = "Highest" }
Register-ScheduledTask @registerArgs | Out-Null

Write-Host "Task '$TaskName' registered for the current user." -ForegroundColor Green
Write-Host "Replace safely with: powershell -File scripts\install-ccswitch-guardian.ps1 -Replace" -ForegroundColor Cyan
Write-Host "Remove with: Unregister-ScheduledTask -TaskName '$TaskName'" -ForegroundColor Yellow
