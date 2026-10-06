# 批量把伪版本 Baritone 坐标替换为各自对应的官方发布版。
# 用法: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/migrate-baritone-coords.ps1 [-Apply]
param([switch] $Apply)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

# MC 版本 -> 官方 Baritone 坐标
$map = @{
    "1.21.3"  = "1.12.0-1.21.3"    # 官方 v1.12.0 声明支持 1.21.2 / 1.21.3
    "1.21.4"  = "1.13.1-1.21.4"    # 官方 v1.13.1 声明支持 1.21.4
    "1.21.5"  = "1.14.0-1.21.5"    # 官方 v1.14.0 声明支持 1.21.5
    "1.21.6"  = "1.15.0-1.21.6"    # 官方 v1.15.0 声明支持 1.21.6 / 1.21.7 / 1.21.8
    "1.21.7"  = "1.15.0-1.21.7"
    "1.21.8"  = "1.15.0-1.21.8"
    "1.21.9"  = "1.16.0-1.21.9"    # 官方 v1.16.0 声明支持 1.21.9 / 1.21.10
    "1.21.10" = "1.16.0-1.21.10"
}

$fakePattern = '1\.17\.0-1\.21\.11-mc1\.21\.\d+'
$changed = @()
$skipped = @()

foreach ($platform in @("fabric/versions", "versions", "neoforge/versions")) {
    $dir = Join-Path $root ($platform -replace "/", "\")
    if (-not (Test-Path $dir)) { continue }

    foreach ($proj in (Get-ChildItem $dir -Directory)) {
        $mc = $proj.Name
        if (-not $map.ContainsKey($mc)) { continue }

        $coord = $map[$mc]                       # 例 1.15.0-1.21.7
        $official = ($coord -split '-')[0]       # 例 1.15.0

        $bg = Join-Path $proj.FullName "build.gradle"
        $gp = Join-Path $proj.FullName "gradle.properties"
        if (-not (Test-Path $bg)) { continue }

        $bgText = Get-Content $bg -Raw -Encoding UTF8
        if ($bgText -notmatch $fakePattern) {
            $skipped += "$platform/$mc (没有伪坐标)"
            continue
        }

        # ---- 1) gradle.properties: 写入/更新 baritone_version ----
        if (Test-Path $gp) {
            $gpText = Get-Content $gp -Raw -Encoding UTF8
            if ($gpText -match '(?m)^baritone_version=') {
                $gpText = $gpText -replace '(?m)^baritone_version=.*$', "baritone_version=$coord"
            } else {
                $anchor = '(?m)^mod_id=wurstpenguin\s*$'
                if ($gpText -notmatch $anchor) {
                    $skipped += "$platform/$mc (gradle.properties 没有 mod_id 锚点)"
                    continue
                }
                $block = @"
mod_id=wurstpenguin

# 内嵌 Baritone 版本（baritone-maven 本地仓库坐标）。
# 规则：必须是官方声明支持本工程 minecraft_version 的那个发布版。
#   1.12.0 -> 1.21.2/1.21.3   1.13.1 -> 1.21.4    1.14.0 -> 1.21.5
#   1.15.0 -> 1.21.6/1.21.7/1.21.8              1.16.0 -> 1.21.9/1.21.10
# 详见 scripts/install-baritone.ps1 的白名单与 scripts/verify-embedded-baritone.ps1
baritone_version=$coord
"@
                $gpText = $gpText -replace $anchor, $block
            }
            if ($Apply) { [System.IO.File]::WriteAllText($gp, $gpText, (New-Object System.Text.UTF8Encoding($false))) }
        }

        # ---- 2) build.gradle: 依赖行 ----
        $artifactId = switch -Wildcard ($platform) {
            "fabric/versions"   { "baritone-api-fabric" }
            "neoforge/versions" { "baritone-neoforge" }
            default             { "baritone-forge" }
        }

        # fabric 用 include implementation("..."), forge 用 implementation "...", neoforge 用 jarJar(implementation("..."))
        $bgNew = $bgText
        if ($platform -eq "fabric/versions") {
            $bgNew = $bgNew -replace ('include implementation\("baritone:baritone-api-fabric:' + $fakePattern + '"\)'),
                ('include implementation("baritone:baritone-api-fabric:${project.baritone_version}")')
        } elseif ($platform -eq "neoforge/versions") {
            $bgNew = $bgNew -replace ('jarJar\(implementation\("baritone:baritone-neoforge:' + $fakePattern + '"\)\)'),
                ('jarJar(implementation("baritone:baritone-neoforge:${project.baritone_version}"))')
        } else {
            # forge: 依赖行 + allJar 里的硬编码文件路径
            $bgNew = $bgNew -replace ('implementation "baritone:baritone-forge:' + $fakePattern + '"'),
                ('implementation "baritone:baritone-forge:${project.baritone_version}"')
            $bgNew = $bgNew -replace ('baritone-maven/baritone/baritone-forge/' + $fakePattern + '/baritone-forge-' + $fakePattern + '\.jar'),
                ('baritone-maven/baritone/baritone-forge/${project.baritone_version}/baritone-forge-${project.baritone_version}.jar')
            # allJar metadata.json 展开 + 末尾补 helper
            if ($bgNew -match '(?s)from\("src/main/resources/META-INF/jarjar/metadata\.json"\)\s*\{\s*into "META-INF/jarjar"\s*\}') {
                $bgNew = $bgNew -replace '(?s)from\("src/main/resources/META-INF/jarjar/metadata\.json"\)\s*\{\s*into "META-INF/jarjar"\s*\}',
                    @'
from("src/main/resources/META-INF/jarjar/metadata.json") {
        into "META-INF/jarjar"
        expand(baritone_version: project.baritone_version,
               baritone_version_next: baritoneUpperBound(project.baritone_version))
    }
'@
                if ($bgNew -notmatch 'def baritoneUpperBound') {
                    $bgNew = $bgNew.TrimEnd() + @'


/**
 * 由内嵌 Baritone 坐标（形如 1.15.0-1.21.7）推导 JarJar 版本区间的上界，
 * 例如 1.15.0-1.21.7 -> 1.16，得到区间 [1.15.0-1.21.7,1.16)。
 * 只取坐标里第一段数字作为官方版本号。
 */
def baritoneUpperBound(String coord) {
    def m = (coord =~ /^(\d+)\.(\d+)(?:\.(\d+))?/)
    if (!m.find())
        throw new GradleException("baritone_version 格式无法解析: ${coord}")
    return "${m.group(1)}.${(m.group(2) as int) + 1}"
}
'@
                }
            }
        }
        if ($Apply -and $bgNew -ne $bgText) {
            [System.IO.File]::WriteAllText($bg, $bgNew, (New-Object System.Text.UTF8Encoding($false)))
        }

        # ---- 3) Forge: metadata.json 模板化 ----
        if ($platform -eq "versions") {
            $mj = Join-Path $proj.FullName "src\main\resources\META-INF\jarjar\metadata.json"
            if (Test-Path $mj) {
                $mjText = Get-Content $mj -Raw -Encoding UTF8
                $mjText = $mjText -replace ('"range": "\[[^"]*"'), '"range": "[${baritone_version},${baritone_version_next})"'
                $mjText = $mjText -replace '"artifactVersion": "[^"]*"', '"artifactVersion": "${baritone_version}"'
                $mjText = $mjText -replace '"path": "META-INF/jarjar/baritone-forge-[^"]*"', '"path": "META-INF/jarjar/baritone-forge-${baritone_version}.jar"'
                if ($Apply) { [System.IO.File]::WriteAllText($mj, $mjText, (New-Object System.Text.UTF8Encoding($false))) }
            }
        }

        $changed += "$platform/$mc  ->  $coord"
    }
}

Write-Host "=== 待处理/已处理 ==="
$changed | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "=== 跳过 ==="
$skipped | ForEach-Object { Write-Host "  $_" }
Write-Host ""
if ($Apply) { Write-Host "已写入（$($changed.Count) 个工程）。" -ForegroundColor Green }
else { Write-Host "试运行（未写入）。加 -Apply 才真正修改。" -ForegroundColor Yellow }
