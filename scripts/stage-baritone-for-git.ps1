# 把各平台工程内嵌 Baritone 所需的产物登记进 git，
# 让 clone 之后无需先跑 scripts/install-baritone.ps1 就能构建。
#
# 做法：扫描全部平台工程的 build.gradle，解析它们引用的
#   baritone:baritone-<loader>:<坐标>
# 坐标可能是硬编码，也可能写作 ${project.baritone_version}（此时取 gradle.properties），
# 然后把对应的 jar 与 pom 用 git add -f 登记（*.jar 被 .gitignore 的 `*.jar` 规则覆盖，
# 必须 -f）。
#
# 排除：*.rejected（历史伪版本）、*.pre-26.2-compat / *.bak-JAVA25 等本地备份。
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$platforms = @(
    @{ dir = "fabric\versions";   art = "baritone-api-fabric" },
    @{ dir = "versions";          art = "baritone-forge" },
    @{ dir = "neoforge\versions"; art = "baritone-neoforge" }
)

$wanted = @{}   # 相对路径 -> $true

foreach ($p in $platforms) {
    if (-not (Test-Path $p.dir)) { continue }
    foreach ($proj in (Get-ChildItem $p.dir -Directory)) {
        $bg = Join-Path $proj.FullName "build.gradle"
        if (-not (Test-Path $bg)) { continue }
        $txt = Get-Content $bg -Raw -Encoding UTF8

        $coords = New-Object System.Collections.Generic.HashSet[string]

        # 形如 baritone-forge:1.12.0-1.21.3
        foreach ($m in [regex]::Matches($txt,
            [regex]::Escape($p.art) + ':([0-9][0-9.]*-[0-9][0-9.]*)')) {
            [void]$coords.Add($m.Groups[1].Value)
        }

        # 形如 baritone-forge:${project.baritone_version}
        if ($txt -match ([regex]::Escape($p.art) + ':\$\{project\.baritone_version\}')) {
            $gp = Join-Path $proj.FullName "gradle.properties"
            if (Test-Path $gp) {
                $v = Select-String -Path $gp -Pattern '^baritone_version=(.+)$' -Encoding UTF8 |
                    Select-Object -First 1
                if ($v) { [void]$coords.Add($v.Matches[0].Groups[1].Value.Trim()) }
            }
        }

        # 显式 from(".../baritone-<loader>/<坐标>/...")
        foreach ($m in [regex]::Matches($txt,
            'baritone-maven/baritone/' + [regex]::Escape($p.art) + '/([0-9][0-9.]*-[0-9][0-9.]*)/')) {
            [void]$coords.Add($m.Groups[1].Value)
        }

        foreach ($c in $coords) {
            foreach ($ext in @("jar", "pom")) {
                $rel = "baritone-maven/baritone/$($p.art)/$c/$($p.art)-$c.$ext"
                if (Test-Path $rel) { $wanted[$rel] = $true }
                else { Write-Host ("  缺文件（请先跑 install-baritone.ps1）: {0}" -f $rel) -ForegroundColor Yellow }
            }
        }
    }
}

if ($wanted.Count -eq 0) { Write-Host "没有解析到任何坐标，请检查脚本。" -ForegroundColor Red; exit 1 }

$added = 0
$bytes = 0
foreach ($rel in ($wanted.Keys | Sort-Object)) {
    git add -f -- $rel
    if ($LASTEXITCODE -eq 0) {
        $added++
        $bytes += (Get-Item $rel).Length
    }
}

Write-Host ""
Write-Host ("已登记 $added 个文件，合计 {0:N2} MB" -f ($bytes / 1MB)) -ForegroundColor Green
