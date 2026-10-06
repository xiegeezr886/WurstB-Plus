<#
.SYNOPSIS
    把官方 Baritone 发布版安装到本地 Maven 仓库 baritone-maven/。

.DESCRIPTION
    各平台工程通过本地仓库坐标引用内嵌的 Baritone，例如：

        include implementation("baritone:baritone-api-fabric:1.15.0-1.21.7")

    坐标约定：<官方版本>-<目标MC版本>

    baritone-maven/ 不入 git（积压产物可达数百 MB），所以 clone 仓库后
    必须先跑本脚本，否则构建会报 "Could not find baritone:baritone-api-fabric:..."。

    脚本只接受官方 GitHub Release 的产物，并按 SHA256 白名单强校验——这一点很重要，
    因为内嵌 jar 自己声明的 minecraft 版本不可信（历史上出现过度版本伪包：
    1.17.0-1.21.11-mc1.21.7 的 fabric.mod.json 同样写着 "minecraft": ["1.21.7"]，
    但代码是按 1.21.11 编译的，一装就崩）。

.PARAMETER Coordinate
    完整坐标，形如 1.15.0-1.21.7。必须已登记在下面的 $trusted 表中。

.PARAMETER Loaders
    要安装的加载器变体，默认全部（fabric / forge / neoforge）。

.EXAMPLE
    pwsh scripts/install-baritone.ps1 -Coordinate 1.15.0-1.21.7
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Coordinate,
    [ValidateSet("fabric", "forge", "neoforge")][string[]] $Loaders = @("fabric", "forge", "neoforge")
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 官方产物 SHA256 白名单：坐标 -> 加载器 -> 摘要
#
# 摘要取自各 Release 页面的 asset digest（GitHub 计算）；每个版本的注释注明它
# 自述支持哪些 MC 版本（读 jar 内 fabric.mod.json 的 depends.minecraft 得到）。
#
# 同一份官方产物会在多个坐标下复用（例如 1.15.0 同时供 1.21.6 / 1.21.7 / 1.21.8），
# 这是有意为之：坐标里的 MC 版本表示"本工程的目标版本"，产物本身是同一个。
# ---------------------------------------------------------------------------
$trusted = @{
    # ---- Baritone 1.13.1 —— 支持 1.21.4 ----
    "1.13.1-1.21.4" = @{
        "fabric"   = "bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05"
        "forge"    = "f919658fe5315de8119d66a0418d63d7a08f5a57d23cd3756154ed8b2b7239e5"
        "neoforge" = "9ca745e381afe034b8d65d1dfbfdbb5ea9f7af8ce648010bb0b5ed0f4aaecc66"
    }

    # ---- Baritone 1.12.0 —— 支持 1.21.2 / 1.21.3 ----
    "1.12.0-1.21.3" = @{
        "fabric"   = "b3b36aa3d74c4df053d147ee9254b70c15f4d1e5e11a2766141a146eea3bd60b"
        "forge"    = "159d83fa657c87a513aecddc4fb6b4d12ce7809a6b1cf6072056974b63e8dca0"
        "neoforge" = "2c2fdc342bfe6ba5bd5fe9656188e27e3ea89371c77163f05050807e59ee443c"
    }

    # ---- Baritone 1.14.0 —— 支持 1.21.5 ----
    "1.14.0-1.21.5" = @{
        "fabric"   = "e7c84733fa7c86d15743866cdd46e46eb27ce8285b1a21fbc95113c6a4d0b9d8"
        "forge"    = "4566178acf08c5fe636b1999ef2f7d08fcf1436b6783412d0dd4363c183203e9"
        "neoforge" = "6f8e3dd3fb38136ad62983459a905d7d4bf560e384c4f94ebeae591a82e87584"
    }

    # ---- Baritone 1.15.0 —— 支持 1.21.6 / 1.21.7 / 1.21.8 ----
    "1.15.0-1.21.6" = @{
        "fabric"   = "c58ef35a133b6ffce96a74682138ac2ee818cbc063b7c62671db9f9d7d783ebb"
        "forge"    = "82b360a295c4ca12ea18750b75cf818b1250f76951f31c18f7fe2f0b4952649c"
        "neoforge" = "3485b8a51d791a27a142d4a1fa21699433e210385ae48445b507f8dadaf29425"
    }
    "1.15.0-1.21.7" = @{
        "fabric"   = "c58ef35a133b6ffce96a74682138ac2ee818cbc063b7c62671db9f9d7d783ebb"
        "forge"    = "82b360a295c4ca12ea18750b75cf818b1250f76951f31c18f7fe2f0b4952649c"
        "neoforge" = "3485b8a51d791a27a142d4a1fa21699433e210385ae48445b507f8dadaf29425"
    }
    "1.15.0-1.21.8" = @{
        "fabric"   = "c58ef35a133b6ffce96a74682138ac2ee818cbc063b7c62671db9f9d7d783ebb"
        "forge"    = "82b360a295c4ca12ea18750b75cf818b1250f76951f31c18f7fe2f0b4952649c"
        "neoforge" = "3485b8a51d791a27a142d4a1fa21699433e210385ae48445b507f8dadaf29425"
    }

    # ---- Baritone 1.16.0 —— 支持 1.21.9 / 1.21.10 ----
    "1.16.0-1.21.9" = @{
        "fabric"   = "7a8c2d08fb6e66b2bc32dd6d78570c798186eed114e6e88f692af4e3742a2b46"
        "forge"    = "da20fb533e355f702aabe23d2fd6ea6e6eff6b1bc330bb89422cd6b282afa475"
        "neoforge" = "a4d3777ac052b6d691638b12ed1d2fe104ac43d69d9e8b0b259c48e0abdc3df7"
    }
    "1.16.0-1.21.10" = @{
        "fabric"   = "7a8c2d08fb6e66b2bc32dd6d78570c798186eed114e6e88f692af4e3742a2b46"
        "forge"    = "da20fb533e355f702aabe23d2fd6ea6e6eff6b1bc330bb89422cd6b282afa475"
        "neoforge" = "a4d3777ac052b6d691638b12ed1d2fe104ac43d69d9e8b0b259c48e0abdc3df7"
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$targetCoord = $Coordinate

if (-not $trusted.ContainsKey($Coordinate)) {
    Write-Host "错误：坐标 $Coordinate 不在 SHA256 白名单中。" -ForegroundColor Red
    Write-Host "请先从 https://github.com/cabaletta/baritone/releases 取官方摘要并登记到本脚本的 \$trusted 表。" -ForegroundColor Yellow
    exit 1
}

# 官方版本号 = 坐标第一段，例如 1.15.0-1.21.7 -> 1.15.0
$officialVersion = ($Coordinate -split '-')[0]

# 下载用的官方 asset 名
$artifactIds = @{
    "fabric"   = "baritone-api-fabric"
    "forge"    = "baritone-api-forge"
    "neoforge" = "baritone-api-neoforge"
}

# 本地仓库里用的坐标 artifactId —— 必须与各工程 build.gradle 请求的 group 一致：
#   fabric   -> baritone-api-fabric
#   neoforge -> baritone-neoforge
#   forge    -> baritone-forge        （注意：不是 baritone-api-forge；
#                                      历史上 1.21.7 是靠手工建的 baritone-forge 目录才对上）
$localArtifactIds = @{
    "fabric"   = "baritone-api-fabric"
    "forge"    = "baritone-forge"
    "neoforge" = "baritone-neoforge"
}

$installed = 0
$skipped = 0
$failed = 0

foreach ($loader in $Loaders) {
    $allowed = $trusted[$Coordinate][$loader]
    if (-not $allowed) {
        Write-Host ("[{0}] 白名单未登记该变体，跳过：{1}" -f $loader, $Coordinate) -ForegroundColor Yellow
        $skipped++
        continue
    }
    if ($allowed.Length -ne 64) {
        Write-Host ("[{0}] 白名单里的摘要还没填好（占位符），跳过。请到 Release 页面取真实 sha256。" -f $loader) -ForegroundColor Red
        $failed++
        continue
    }

    $artifactId = $artifactIds[$loader]
    $fileName = "$artifactId-$officialVersion.jar"
    $url = "https://github.com/cabaletta/baritone/releases/download/v$officialVersion/$fileName"

    $localId = $localArtifactIds[$loader]
    $destDir = Join-Path $repoRoot "baritone-maven\baritone\$localId\$targetCoord"
    $destJar = Join-Path $destDir "$localId-$targetCoord.jar"
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

    # 已存在且指纹正确 -> 直接通过（可离线、可重复执行）
    if (Test-Path $destJar) {
        $have = (Get-FileHash $destJar -Algorithm SHA256).Hash.ToLower()
        if ($have -eq $allowed.ToLower()) {
            Write-Host ("[{0}] 已存在且指纹正确，跳过下载：{1}-{2}.jar" -f $loader, $localId, $targetCoord) -ForegroundColor Green
            $installed++
            continue
        }
        Write-Host ("[{0}] 已存在但指纹不符，将重新下载。" -f $loader) -ForegroundColor Yellow
    }

    Write-Host ("[{0}] 下载 {1}" -f $loader, $fileName)
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) $fileName
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    & curl.exe -sS -L --fail --max-time 300 --noproxy "*" -o $tmp $url 2>$null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tmp)) {
        Write-Host "  直连失败，尝试走系统代理…" -ForegroundColor Yellow
        & curl.exe -sS -L --fail --max-time 300 -o $tmp $url 2>$null
    }
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tmp)) {
        Write-Host ("  下载失败：{0}" -f $url) -ForegroundColor Red
        Write-Host "  可手动下载该文件，然后放到：" -ForegroundColor Yellow
        Write-Host ("    {0}" -f $destJar) -ForegroundColor Yellow
        Write-Host "  再重新执行本脚本（会校验指纹并跳过下载）。" -ForegroundColor Yellow
        $failed++
        continue
    }

    $actual = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $allowed.ToLower()) {
        Write-Host "  SHA256 校验失败，拒绝安装！" -ForegroundColor Red
        Write-Host "    期望: $($allowed.ToLower())"
        Write-Host "    实际: $actual"
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        $failed++
        continue
    }

    Copy-Item $tmp $destJar -Force
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue

    $pom = @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>baritone</groupId>
  <artifactId>$localId</artifactId>
  <version>$targetCoord</version>
  <packaging>jar</packaging>
</project>
"@
    $pomPath = Join-Path $destDir "$localId-$targetCoord.pom"
    [System.IO.File]::WriteAllText($pomPath, $pom, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host ("  已安装 -> {0}" -f ($destJar.Replace($repoRoot + "\", ""))) -ForegroundColor Green
    Write-Host ("  SHA256  {0}" -f $actual)
    $installed++
}

Write-Host ""
Write-Host "完成：安装 $installed 个，跳过 $skipped 个，失败 $failed 个。" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "对应坐标：baritone:baritone-api-<loader>:$targetCoord"
Write-Host ""
Write-Host "下一步：在工程 gradle.properties 设置 baritone_version=$targetCoord，然后构建并校验："
Write-Host "  .\gradlew.bat clean allJar"
Write-Host "  .\scripts\verify-embedded-baritone.ps1 -ProjectDir <工程目录>"

if ($failed -gt 0) { exit 1 }
