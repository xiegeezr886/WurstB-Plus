# 依赖：先在 _recon/wurst7.git 放一份上游 bare 克隆（blob 过滤即可，约 6 MB）：
#   git clone --bare --filter=blob:none https://github.com/Wurst-Imperium/Wurst7.git _recon/wurst7.git
# 上游 48 个分支覆盖 1.20.1 - 26.4。
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\ui863\Documents\trae_projects\VAP'

# 上游 Wurst7 每个 MC 分支的 wurst.mixins.json = 官方"该登记哪些混入"的权威答案。
# 本地某个混入文件"存在但未登记"，如果在同版本官方里是登记着的，
# 那就不是"设计上不登记"，而是移植时漏了。

$gitDir = (Resolve-Path '_recon\wurst7.git').Path
$outDir = Join-Path (Get-Location).Path '_recon\upstream-mixins'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-UpstreamMixinList([string]$branch) {
    $cache = Join-Path $outDir "$branch.json"
    if (Test-Path $cache) { $cache | Out-Null }
    $raw = & git --git-dir=$gitDir show "${branch}:src/main/resources/wurst.mixins.json" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return $null }
    $txt = ($raw -join "`n")
    try { $j = $txt | ConvertFrom-Json } catch { return $null }
    $set = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($k in @('mixins', 'client', 'server')) {
        if ($j.PSObject.Properties.Name -contains $k) {
            foreach ($e in $j.$k) { if ($e -is [string]) { [void]$set.Add($e) } }
        }
    }
    [System.IO.File]::WriteAllText($cache, ($set -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
    return $set
}

# 本地工程 → MC 版本
$projects = @{}
$projects['.'] = '1.20.1'
$projects['fabric'] = '1.20.1'
$projects['neoforge'] = '1.20.1'
foreach ($grp in @('versions', 'fabric\versions', 'neoforge\versions')) {
    Get-ChildItem $grp -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $projects[$_.FullName.Substring((Get-Location).Path.Length + 1)] = $_.Name
    }
}

$mcVersions = $projects.Values | Sort-Object -Unique
Write-Host ("本地 MC 版本: {0}" -f ($mcVersions -join ', '))

# 拉官方清单
$upstream = @{}
foreach ($v in $mcVersions) {
    $l = Get-UpstreamMixinList $v
    if ($l) { $upstream[$v] = $l; Write-Host ("  {0,-8} 官方登记 {1} 条" -f $v, $l.Count) }
    else { Write-Host ("  {0,-8} 官方无该分支或无配置" -f $v) -ForegroundColor Yellow }
}

Write-Host ''
Write-Host '=== 本地"有文件但未登记"，且同版本官方【登记了】的 ==='
$fixList = New-Object System.Collections.Generic.List[object]

foreach ($p in ($projects.Keys | Sort-Object)) {
    $mc = $projects[$p]
    if (-not $upstream.ContainsKey($mc)) { continue }
    $full = if ($p -eq '.') { (Get-Location).Path } else { Join-Path (Get-Location).Path $p }
    $pkgDir = Join-Path $full 'src\main\java\net\wurstclient\mixin'
    $resDir = Join-Path $full 'src\main\resources'
    if (-not (Test-Path $pkgDir) -or -not (Test-Path $resDir)) { continue }

    $listed = @{}
    foreach ($j in (Get-ChildItem $resDir -Recurse -File -Filter '*.mixins.json' -ErrorAction SilentlyContinue)) {
        try { $cfg = [System.IO.File]::ReadAllText($j.FullName, [System.Text.Encoding]::UTF8) | ConvertFrom-Json } catch { continue }
        foreach ($k in @('mixins', 'client', 'server')) {
            if ($cfg.PSObject.Properties.Name -contains $k) { foreach ($e in $cfg.$k) { if ($e -is [string]) { $listed[$e] = $true } } }
        }
    }

    $abs = (Resolve-Path $pkgDir).Path
    foreach ($f in (Get-ChildItem $abs -Recurse -File -Filter *.java)) {
        $rel = $f.FullName.Substring($abs.Length + 1)
        $rel = $rel.Substring(0, $rel.Length - 5).Replace('\', '/')
        if ($rel -match 'WurstMixinConfigPlugin') { continue }
        if ($listed.ContainsKey($rel)) { continue }
        if (-not (Select-String -Path $f.FullName -Pattern '@Mixin\(' -Quiet)) { continue }
        # 官方（同版本）登记了它吗？
        $upName = $upstream[$mc]
        $hit = $upName.Contains($rel)
        if (-not $hit) {
            # 官方可能用不带子包前缀的名字，再试一次最后一段
            $leaf = ($rel -split '/')[-1]
            $hit = $upName.Contains($leaf)
        }
        if ($hit) {
            $fixList.Add([PSCustomObject]@{ Project = $p; MC = $mc; Mixin = $rel })
        }
    }
}

$fixList | Group-Object MC | Sort-Object Name | ForEach-Object {
    Write-Host ("  {0,-8} {1,4} 条" -f $_.Name, $_.Count)
}
Write-Host ''
Write-Host ("合计可确证该登记的: {0}" -f $fixList.Count)
$fixList | Export-Csv -NoTypeInformation -Encoding UTF8 '_recon\upstream-confirmed-gaps.csv'
Write-Host '已写 _recon\upstream-confirmed-gaps.csv'
