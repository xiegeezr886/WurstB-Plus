param([string]$World = 'soar7-hud-test')
$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\ui863\Documents\trae_projects\VAP'
$proj = Join-Path $repo 'fabric\versions\1.21.11'
$port = 26666
$headers = @{'X-Wurst-Token' = 'soartest'}
$base = "http://127.0.0.1:$port"
$env:JAVA_HOME = 'C:\Users\ui863\.jdks\jdk21.0.12_12'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

function Stop-Client {
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and
        $_.CommandLine.Contains($proj)
    } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
function Wait-Ping([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try { if ((Invoke-RestMethod "$base/ping" -Headers $headers -TimeoutSec 2) -eq 'ok') { return $true } } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}
function Hud { Invoke-RestMethod "$base/hud" -Headers $headers -TimeoutSec 20 }
function SetHud([string]$id, [bool]$on) {
    Invoke-RestMethod -Method Post -Headers $headers -TimeoutSec 30 `
        -Uri ("$base/hud/toggle?id=" + [uri]::EscapeDataString($id) + "&enabled=" + $on.ToString().ToLowerInvariant())
}

Stop-Client
Start-Sleep -Seconds 2
$spLog = Join-Path $env:TEMP 'hud-sizes-sp.json'
$arg = "runClient `"-PspWorld=$World`" `"-PspLog=$spLog`" --offline --console=plain"
$bat = Join-Path $env:TEMP 'hud-sizes-launch.bat'
@('@echo off', ('cd /d "' + $proj + '"'),
  ('call .\gradlew.bat ' + $arg + ' > "' + (Join-Path $env:TEMP 'hud-sizes-gradle.log') + '" 2>&1')) |
    Set-Content -Encoding ASCII $bat
Start-Process -FilePath $env:ComSpec -ArgumentList '/c', ('"' + $bat + '"') -WindowStyle Hidden | Out-Null
if (-not (Wait-Ping 600)) { Stop-Client; Write-Output 'no ping'; exit 1 }
Start-Sleep -Seconds 6

$safe = @('hack.pitchdisplay', 'hack.yawdisplay', 'hack.cpsdisplay',
    'hack.combocounter', 'hack.armorstatus', 'hack.potionstatus',
    'hack.keystrokes', 'hack.scoreboard')
foreach ($id in $safe) { SetHud $id $true | Out-Null }
Start-Sleep -Seconds 3
$hud = Hud
$rows = $hud | Where-Object { $_.enabled } |
    Sort-Object -Property width -Descending |
    Select-Object id, name, x, y, scale, width, height
$rows | Format-Table -AutoSize | Out-String -Width 200 | Write-Output

foreach ($id in $safe) { SetHud $id $false | Out-Null }
Invoke-RestMethod -Method Post -Uri "$base/hacks/disableAll" -Headers $headers -TimeoutSec 60 | Out-Null
Start-Sleep -Seconds 1
Stop-Client
