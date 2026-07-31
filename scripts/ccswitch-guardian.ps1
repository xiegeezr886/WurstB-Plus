<# 
  CC Switch Proxy Guardian - OpenAI Codex CLI 专用
  功能:
    1. 检测 CC Switch 代理是否在线 (127.0.0.1:15721)
    2. 如果离线，尝试启动 cc-switch proxy serve --takeover codex
    3. 配置 Codex CLI 环境变量
    4. 静默守护，日志写入文件
  用法:
    - 直接运行: .\ccswitch-guardian.ps1
    - 开机自启: 添加到 Task Scheduler (见下方注释)
#>

param(
    [int]$CheckIntervalSeconds = 30,
    [string]$LogFile = "$env:USERPROFILE\.ccswitch-guardian.log",
    [int]$Port = 15721
)

$ErrorActionPreference = "SilentlyContinue"
$MaxLogSize = 1MB

# --- 日志函数 ---
function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$ts] $Message"
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
    # 日志超过 1MB 自动轮转
    if ((Get-Item $LogFile -ErrorAction SilentlyContinue).Length -gt $MaxLogSize) {
        $backup = $LogFile -replace '\.log$', ('_{0}.log' -f (Get-Date -Format "yyyyMMdd_HHmmss"))
        Move-Item $LogFile $backup -Force
        Add-Content -Path $LogFile -Value "[$ts] Log rotated" -Encoding UTF8
    }
}

# --- 检测代理是否在线 ---
function Test-ProxyAlive {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $result = $tcp.BeginConnect("127.0.0.1", $Port, $null, $null)
        $wait = $result.AsyncWaitHandle.WaitOne(2000, $false)
        if ($wait -and $tcp.Connected) {
            $tcp.EndConnect($result)
            $tcp.Close()
            return $true
        }
        $tcp.Close()
        return $false
    } catch {
        return $false
    }
}

# --- 查找 cc-switch 可执行文件 ---
function Find-ccswitch {
    # 1. PATH 中
    $inPath = Get-Command cc-switch -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }
    # 2. 常见安装路径
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\cc-switch\cc-switch.exe",
        "$env:ProgramFiles\cc-switch\cc-switch.exe",
        "$env:APPDATA\cc-switch\cc-switch.exe",
        "$env:USERPROFILE\AppData\Local\Programs\cc-switch\cc-switch.exe",
        "C:\Windows\System32\cc-switch.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    return $null
}

# --- 配置 Codex CLI 环境变量 ---
function Set-CodexProxy {
    $baseUrl = "http://127.0.0.1:${Port}"
    # Codex CLI 使用 OPENAI_BASE_URL 或 OPENAI_API_BASE
    [Environment]::SetEnvironmentVariable("OPENAI_BASE_URL", $baseUrl, "Process")
    [Environment]::SetEnvironmentVariable("OPENAI_API_BASE", $baseUrl, "Process")
    [Environment]::SetEnvironmentVariable("OPENAI_BASE_URL", $baseUrl, "User")
    [Environment]::SetEnvironmentVariable("OPENAI_API_BASE", $baseUrl, "User")
    Write-Log "Set OPENAI_BASE_URL=$baseUrl (Process + User)"
}

# --- 主循环 ---
Write-Log "=== CC Switch Guardian started (port $Port, interval ${CheckIntervalSeconds}s) ==="

$ccswitchPath = Find-ccswitch
if (-not $ccswitchPath) {
    Write-Log "WARNING: cc-switch not found in PATH or common locations"
    Write-Log "Searched: PATH, LocalAppData, ProgramFiles, AppData"
}

$proxyProcess = $null

while ($true) {
    $alive = Test-ProxyAlive

    if ($alive) {
        # 代理在线，确保环境变量正确
        $currentUrl = [Environment]::GetEnvironmentVariable("OPENAI_BASE_URL", "Process")
        if ($currentUrl -ne "http://127.0.0.1:${Port}") {
            Set-CodexProxy
        }
        # 静默等待
        Start-Sleep -Seconds $CheckIntervalSeconds
        continue
    }

    Write-Log "Proxy offline on port $Port"

    # 代理离线，尝试启动
    if (-not $ccswitchPath) {
        $ccswitchPath = Find-ccswitch
        if (-not $ccswitchPath) {
            Write-Log "ERROR: cc-switch still not found, retry in 60s"
            Start-Sleep -Seconds 60
            continue
        }
    }

    Write-Log "Starting cc-switch proxy serve --takeover codex ..."
    try {
        # 以隐藏窗口方式启动
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $ccswitchPath
        $psi.Arguments = "proxy serve --takeover codex"
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $proxyProcess = [System.Diagnostics.Process]::Start($psi)

        # 等待 5 秒让代理启动
        Start-Sleep -Seconds 5

        if ($proxyProcess.HasExited) {
            $stderr = $proxyProcess.StandardError.ReadToEnd()
            Write-Log "ERROR: cc-switch exited immediately (code $($proxyProcess.ExitCode)): $stderr"
            Start-Sleep -Seconds 30
            continue
        }

        Write-Log "cc-switch started (PID: $($proxyProcess.Id))"
        Set-CodexProxy

        # 等待代理真正在线
        $retries = 0
        while ($retries -lt 10) {
            if (Test-ProxyAlive) {
                Write-Log "Proxy is now online on port $Port"
                break
            }
            Start-Sleep -Seconds 2
            $retries++
        }

        if ($retries -ge 10) {
            Write-Log "WARNING: Proxy did not come online after 10 retries"
        }

    } catch {
        Write-Log "ERROR starting cc-switch: $_"
    }

    Start-Sleep -Seconds $CheckIntervalSeconds
}
