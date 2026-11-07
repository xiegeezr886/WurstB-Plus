param(
    [Parameter(Mandatory=$true)][string]$Project,
    [Parameter(Mandatory=$true)][string]$Jdk,
    [int]$WaitSeconds = 180
)

$ErrorActionPreference = 'Stop'
$proj = (Resolve-Path $Project).Path
$log = Join-Path $proj 'run\logs\latest.log'
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"

function Get-Clients {
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and
        $_.CommandLine.Contains($proj)
    }
}

foreach ($p in Get-Clients) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2
$out = Join-Path $env:TEMP ('smoke-' + (Split-Path $proj -Leaf) + '.log')
$bat = Join-Path $env:TEMP ('smoke-' + (Split-Path $proj -Leaf) + '.bat')
@(
    '@echo off',
    ('set "JAVA_HOME=' + $Jdk + '"'),
    ('set "PATH=%JAVA_HOME%\bin;%PATH%"'),
    ('cd /d "' + $proj + '"'),
    ('call .\gradlew.bat runClient --offline --console=plain > "' + $out + '" 2>&1')
) | Set-Content -Encoding ASCII $bat

Start-Process -FilePath $env:ComSpec -ArgumentList '/c', ('"' + $bat + '"') `
    -WindowStyle Hidden | Out-Null

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    if (Test-Path $log) {
        $t = Get-Content $log -Raw -ErrorAction SilentlyContinue
        if ($t -match 'Backend library|OpenAL initialized|Narrator library|Created:') {
            $ready = $true
            break
        }
    }
    Start-Sleep -Seconds 3
}
Start-Sleep -Seconds 12

$result = @()
$result += "project=$proj ready=$ready"
if (Test-Path $log) {
    $matches = Get-Content $log | Select-String -Pattern `
        'Mixin apply|InjectionError|InvalidInjection|MixinTransformerError|MixinApplyError|NoSuchMethodError.*wurst|Exception.*wurstclient\.mixin'
    $result += ("mixin_errors=" + @($matches).Count)
    $result += @($matches | Select-Object -First 20 | ForEach-Object { $_.Line })
} else {
    $result += 'no latest.log'
}
if (Test-Path $out) {
    $result += '--- launcher tail ---'
    $result += (Get-Content $out -Tail 20)
}
$result | Set-Content -Encoding UTF8 (Join-Path $env:TEMP ('smoke-' + (Split-Path $proj -Leaf) + '-result.txt'))

foreach ($p in Get-Clients) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
$result | ForEach-Object { Write-Output $_ }
