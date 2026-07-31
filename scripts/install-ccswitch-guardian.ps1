<# 
  注册 CC Switch Guardian 为开机自启任务
  以管理员权限运行此脚本即可
#>

$taskName = "CCSwitchGuardian"
$scriptPath = Join-Path $PSScriptRoot "ccswitch-guardian.ps1"

# 删除旧任务（如果存在）
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue

# 创建触发器：用户登录时启动
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME

# 创建操作：静默运行 PowerShell
$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$scriptPath`""

# 设置：允许按需运行、不唤醒电脑、最高权限
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RunOnlyIfNetworkAvailable `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1)

# 注册任务
Register-ScheduledTask `
    -TaskName $taskName `
    -Trigger $trigger `
    -Action $action `
    -Settings $settings `
    -Description "CC Switch Proxy Guardian - auto-start proxy for Codex CLI" `
    -RunLevel Highest

Write-Host "Task '$taskName' registered successfully!" -ForegroundColor Green
Write-Host "It will start automatically when you log in." -ForegroundColor Cyan
Write-Host ""
Write-Host "To start now:    Start-ScheduledTask -TaskName '$taskName'" -ForegroundColor Yellow
Write-Host "To stop:         Stop-ScheduledTask -TaskName '$taskName'" -ForegroundColor Yellow
Write-Host "To remove:       Unregister-ScheduledTask -TaskName '$taskName'" -ForegroundColor Yellow
