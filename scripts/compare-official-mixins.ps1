# 抓取官方 Wurst7 各 MC 版本的 wurst.mixins.json，与本地三个加载器工程对比。
# 输出：每版本每加载器「缺少的 mixin」清单。
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$branches = @("1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6",
              "1.21.7","1.21.8","1.21.9","1.21.10","1.21.11")

$outDir = Join-Path $env:TEMP "wurst-official-mixins"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-Official($branch) {
    $dest = Join-Path $outDir "$branch.json"
    if (Test-Path $dest) { return $dest }
    $url = "https://api.github.com/repos/Wurst-Imperium/Wurst7/contents/src/main/resources/wurst.mixins.json?ref=$branch"
    $tmp = Join-Path $outDir "$branch.raw"
    & curl.exe -sS -L --fail --max-time 60 --noproxy "*" -o $tmp $url 2>$null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tmp)) { return $null }
    $json = Get-Content $tmp -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $json.content) { return $null }
    $bytes = [System.Convert]::FromBase64String($json.content)
    [System.IO.File]::WriteAllBytes($dest, $bytes)
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $dest
}

function Read-Entries($path) {
    if (-not $path -or -not (Test-Path $path)) { return $null }
    $j = Get-Content $path -Raw -Encoding UTF8 | ConvertFrom-Json
    $s = New-Object System.Collections.Generic.HashSet[string]
    foreach ($k in @("mixins","client","server")) {
        if ($j.$k) { foreach ($e in $j.$k) { [void]$s.Add($e) } }
    }
    return ,$s
}

function Local-Entries($proj) {
    $cfg = Get-ChildItem "$proj\src\main\resources" -Filter "*.mixins.json" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $cfg) { return $null }
    $j = Get-Content $cfg.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $s = New-Object System.Collections.Generic.HashSet[string]
    foreach ($k in @("mixins","client","server")) {
        if ($j.$k) { foreach ($e in $j.$k) { [void]$s.Add($e) } }
    }
    return ,$s
}

Write-Host "==== 拉取官方清单 ===="
$official = @{}
foreach ($b in $branches) {
    $p = Get-Official $b
    $e = Read-Entries $p
    if ($e) { $official[$b] = $e; Write-Host ("  {0,-9} {1,3} 条" -f $b, $e.Count) }
    else { Write-Host ("  {0,-9} 拉取失败" -f $b) -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "==== 对比：本地相对官方缺少/多出的 mixin ===="

$loaders = @(
    @{ name = "fabric";   dir = "fabric\versions" },
    @{ name = "forge";    dir = "versions" },
    @{ name = "neoforge"; dir = "neoforge\versions" }
)

foreach ($b in $branches) {
    if (-not $official.ContainsKey($b)) { continue }
    $off = $official[$b]
    Write-Host ""
    Write-Host ("######## {0}  (官方 {1} 条) ########" -f $b, $off.Count)
    foreach ($l in $loaders) {
        $proj = Join-Path $l.dir $b
        if (-not (Test-Path $proj)) { continue }
        $loc = Local-Entries $proj
        if (-not $loc) { continue }
        $missing = @($off | Where-Object { -not $loc.Contains($_) }) | Sort-Object
        $extra   = @($loc | Where-Object { -not $off.Contains($_) }) | Sort-Object
        Write-Host ("  [{0}] 本地 {1} 条 | 缺 {2} | 多 {3}" -f $l.name, $loc.Count, $missing.Count, $extra.Count)
        if ($missing.Count) { Write-Host ("      缺: " + ($missing -join ", ")) -ForegroundColor Yellow }
        if ($extra.Count)   { Write-Host ("      多: " + ($extra -join ", ")) -ForegroundColor Cyan }
    }
}
