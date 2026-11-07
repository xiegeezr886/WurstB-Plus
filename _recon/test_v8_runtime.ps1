$ErrorActionPreference = 'Continue'
$repo = 'C:\Users\ui863\Documents\trae_projects\VAP'
$proj = Join-Path $repo 'fabric\versions\1.21.11'
$port = 26666
$headers = @{'X-Wurst-Token' = 'soartest'}
$logPath = Join-Path $proj 'run\logs\latest.log'
$out = Join-Path $env:TEMP 'v8-runtime-gradle.log'
$result = Join-Path $repo '_recon\v8-runtime-report.txt'

function Clients {
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and
        $_.CommandLine.Contains($proj)
    }
}
function KillClients {
    foreach ($p in Clients) {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
}
function Ping {
    try {
        return (Invoke-RestMethod -Uri "http://127.0.0.1:$port/ping" `
            -Headers $headers -TimeoutSec 2) -eq 'ok'
    } catch { return $false }
}
function Enable($name) {
    $u = "http://127.0.0.1:$port/hacks/enable?name=" + [uri]::EscapeDataString($name)
    try {
        $r = Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 20
        return "$name ok=$($r.ok) enabled=$($r.enabled) error=$($r.error)"
    } catch {
        return "$name EXCEPTION $($_.Exception.Message)"
    }
}

KillClients
Start-Sleep -Seconds 2
$bat = Join-Path $env:TEMP 'v8-runtime-launch.bat'
@(
    '@echo off',
    'set "JAVA_HOME=C:\Users\ui863\.jdks\jdk21.0.12_12"',
    'set "PATH=%JAVA_HOME%\bin;%PATH%"',
    ('cd /d "' + $proj + '"'),
    ('call .\gradlew.bat runClient "-PspWorld=xray-test" "-PspLog=%TEMP%\v8-runtime-sp.json" --offline --console=plain > "' + $out + '" 2>&1')
) | Set-Content -Encoding ASCII $bat
Start-Process -FilePath $env:ComSpec -ArgumentList '/c', ('"' + $bat + '"') -WindowStyle Hidden | Out-Null

$lines = @("start $(Get-Date -Format s)")
$deadline = (Get-Date).AddSeconds(300)
while ((Get-Date) -lt $deadline -and -not (Ping)) { Start-Sleep -Seconds 3 }
$lines += "ping=$(Ping)"
if (Ping) {
    Start-Sleep -Seconds 8
    $all = Invoke-RestMethod -Uri "http://127.0.0.1:$port/hacks" -Headers $headers -TimeoutSec 20
    $names = @($all | ForEach-Object { $_.name })
    $lines += "registered=$($names.Count)"
    $lines += "present=" + ((@('AutoGG','ForceMainHand','JumpResetIndicator','PitchDisplay','SnapTap','YawDisplay') | Where-Object { $names -contains $_ }) -join ',')
    $lines += "missing=" + ((@('AutoGG','ForceMainHand','JumpResetIndicator','PitchDisplay','SnapTap','YawDisplay') | Where-Object { $names -notcontains $_ }) -join ',')
    foreach ($name in @('AutoGG','ForceMainHand','JumpResetIndicator','PitchDisplay','SnapTap','YawDisplay')) {
        $lines += (Enable $name)
        Start-Sleep -Milliseconds 700
    }
    Start-Sleep -Seconds 3
    try {
        $err = Invoke-RestMethod -Uri "http://127.0.0.1:$port/logs?lines=600&errorsOnly=1" -Headers $headers -TimeoutSec 20
        $lines += '--- errors ---'
        $lines += "$err"
    } catch {
        $lines += "log query failed: $($_.Exception.Message)"
    }
}
try {
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$port/hacks/disableAll" `
        -Headers $headers -TimeoutSec 30 | Out-Null
    Start-Sleep -Seconds 2
} catch {}
$lines += '--- latest.log filtered ---'
if (Test-Path $logPath) {
    $lines += (Get-Content $logPath | Select-String -Pattern 'ERROR|Exception|InvalidInjection|Mixin apply|SnapTap|ForceMainHand|JumpReset|PitchDisplay|YawDisplay|AutoGG' | Select-Object -Last 40 | ForEach-Object { $_.Line })
}
$lines | Set-Content -Encoding UTF8 $result
KillClients
Get-Content $result
