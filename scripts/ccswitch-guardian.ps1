<#
  CC Switch Proxy Guardian
  Monitors a local CC Switch listener and optionally starts it when unavailable.
  The guardian does not change Codex's user environment unless explicitly asked.
#>

[CmdletBinding()]
param(
    [ValidateRange(5, 3600)]
    [int]$CheckIntervalSeconds = 30,
    [string]$LogFile = "$env:USERPROFILE\.ccswitch-guardian.log",
    [ValidateRange(1, 65535)]
    [int]$Port = 15721,
    [string]$CcSwitchPath = "",
    [string]$ProxyArguments = "proxy serve --takeover codex",
    [string]$ApiBaseUrl = "",
    [switch]$UseLocalProxy,
    [switch]$PersistUserEnvironment,
    [bool]$StartProxy = $true
)

$ErrorActionPreference = "Stop"
$MaxLogSize = 1MB
$script:LastUnverifiedWarning = $false

$logParent = Split-Path -Parent $LogFile
if ($logParent) { New-Item -ItemType Directory -Path $logParent -Force | Out-Null }

function Write-Log {
    param([string]$Message)
    try {
        $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $line = "[$ts] $Message"
        Add-Content -Path $LogFile -Value $line -Encoding UTF8
        $logItem = Get-Item -LiteralPath $LogFile -ErrorAction SilentlyContinue
        if ($logItem -and $logItem.Length -gt $MaxLogSize) {
            $backup = $LogFile -replace '\.log$', ('_{0}.log' -f (Get-Date -Format "yyyyMMdd_HHmmss"))
            Move-Item -LiteralPath $LogFile -Destination $backup -Force
            Add-Content -Path $LogFile -Value "[$ts] Log rotated" -Encoding UTF8
        }
    } catch {
        Write-Warning "Unable to write guardian log: $($_.Exception.Message)"
    }
}

function Find-CcSwitch {
    if ($CcSwitchPath) {
        if (-not (Test-Path -LiteralPath $CcSwitchPath -PathType Leaf)) {
            throw "Configured cc-switch executable does not exist: $CcSwitchPath"
        }
        return (Resolve-Path -LiteralPath $CcSwitchPath).Path
    }

    $inPath = Get-Command cc-switch -ErrorAction SilentlyContinue
    if ($inPath) {
        if ($inPath.Path) { return $inPath.Path }
        if ($inPath.Source) { return $inPath.Source }
    }
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\cc-switch\cc-switch.exe",
        "$env:ProgramFiles\cc-switch\cc-switch.exe",
        "$env:APPDATA\cc-switch\cc-switch.exe",
        "$env:USERPROFILE\AppData\Local\Programs\cc-switch\cc-switch.exe",
        "C:\Windows\System32\cc-switch.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Test-ProxyAlive {
    $tcp = $null
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $async = $tcp.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(2000, $false)) { return $false }
        $tcp.EndConnect($async)
        return $tcp.Connected
    } catch {
        return $false
    } finally {
        if ($tcp) { $tcp.Dispose() }
    }
}

function Set-CodexProxy {
    $target = $ApiBaseUrl
    if ($UseLocalProxy) { $target = "http://127.0.0.1:${Port}" }
    if ([string]::IsNullOrWhiteSpace($target)) {
        Write-Log "Leaving OPENAI_BASE_URL unchanged; pass -UseLocalProxy or -ApiBaseUrl to configure it."
        return
    }
    $uri = $null
    if (-not [System.Uri]::TryCreate($target, [System.UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -notin @("http", "https")) {
        throw "ApiBaseUrl must be an absolute http or https URL: $target"
    }
    $currentProcess = [Environment]::GetEnvironmentVariable("OPENAI_BASE_URL", "Process")
    $currentUser = [Environment]::GetEnvironmentVariable("OPENAI_BASE_URL", "User")
    if ($currentProcess -eq $target -and (-not $PersistUserEnvironment -or $currentUser -eq $target)) {
        return
    }
    [Environment]::SetEnvironmentVariable("OPENAI_BASE_URL", $target, "Process")
    [Environment]::SetEnvironmentVariable("OPENAI_API_BASE", $target, "Process")
    if ($PersistUserEnvironment) {
        [Environment]::SetEnvironmentVariable("OPENAI_BASE_URL", $target, "User")
        [Environment]::SetEnvironmentVariable("OPENAI_API_BASE", $target, "User")
        Write-Log "Persisted Codex API base URL at user scope: $target"
    } else {
        Write-Log "Set API base URL for guardian process only: $target"
    }
}

Write-Log "=== CC Switch Guardian started (port $Port, interval ${CheckIntervalSeconds}s, start=$StartProxy) ==="
$ccswitchPath = Find-CcSwitch
if (-not $ccswitchPath -and $StartProxy) {
    Write-Log "WARNING: cc-switch was not found; guardian will keep monitoring and retry discovery."
}

$proxyProcess = $null
$nextStart = Get-Date
$failureCount = 0

while ($true) {
    try {
        if (Test-ProxyAlive) {
            if ($failureCount -gt 0) {
                Write-Log "Proxy is online again."
                $failureCount = 0
            }
            if ($UseLocalProxy -or $ApiBaseUrl) { Set-CodexProxy }
            Start-Sleep -Seconds $CheckIntervalSeconds
            continue
        }

        if (-not $StartProxy) {
            Write-Log "Proxy is offline; auto-start is disabled."
            Start-Sleep -Seconds $CheckIntervalSeconds
            continue
        }

        if ($proxyProcess -and -not $proxyProcess.HasExited) {
            Write-Log "Proxy process is still running but its port is unavailable; waiting before retry."
            Start-Sleep -Seconds $CheckIntervalSeconds
            continue
        }
        if ((Get-Date) -lt $nextStart) {
            Start-Sleep -Seconds ([Math]::Min($CheckIntervalSeconds, [int][Math]::Ceiling(($nextStart - (Get-Date)).TotalSeconds)))
            continue
        }

        if (-not $ccswitchPath) { $ccswitchPath = Find-CcSwitch }
        if (-not $ccswitchPath) {
            Write-Log "ERROR: cc-switch not found; retrying in 60 seconds."
            $nextStart = (Get-Date).AddSeconds(60)
            continue
        }

        Write-Log "Starting cc-switch with configured arguments."
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $ccswitchPath
        $psi.Arguments = $ProxyArguments
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        # Do not redirect streams without asynchronous readers: a verbose proxy can deadlock.
        $proxyProcess = [System.Diagnostics.Process]::Start($psi)
        Set-CodexProxy
        Start-Sleep -Seconds 5

        if ($proxyProcess.HasExited) {
            Write-Log "ERROR: cc-switch exited immediately with code $($proxyProcess.ExitCode)."
            $failureCount++
            $delay = [Math]::Min(300, 30 * [Math]::Pow(2, [Math]::Min($failureCount - 1, 3)))
            $nextStart = (Get-Date).AddSeconds($delay)
        } elseif (Test-ProxyAlive) {
            Write-Log "Proxy is online (PID $($proxyProcess.Id))."
            $failureCount = 0
        } else {
            Write-Log "WARNING: cc-switch started but port $Port is not responding yet."
            $nextStart = (Get-Date).AddSeconds(30)
        }
    } catch {
        Write-Log "ERROR: $($_.Exception.Message)"
        $failureCount++
        $nextStart = (Get-Date).AddSeconds([Math]::Min(300, 30 * $failureCount))
    }
    Start-Sleep -Seconds $CheckIntervalSeconds
}
