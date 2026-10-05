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
    因为内嵌 jar 自己声明的 minecraft 版本不可信（历史上出现过度版本伪包）。

.PARAMETER Version
    官方 Baritone 版本，例如 1.15.0

.PARAMETER MinecraftVersion
    目标 MC 版本，例如 1.21.7。会写进坐标与目录名。

.PARAMETER Loaders
    要安装的加载器变体，默认全部（fabric / forge / neoforge）。
    官方只有部分版本提供 neoforge 变体，缺失的会跳过并提示。

.EXAMPLE
    pwsh scripts/install-baritone.ps1 -Version 1.15.0 -MinecraftVersion 1.21.7
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Version,
    [Parameter(Mandatory = $true)][string] $MinecraftVersion,
    [ValidateSet("fabric", "forge", "neoforge")][string[]] $Loaders = @("fabric", "forge", "neoforge")
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 官方发布版 SHA256 白名单。
# 新增版本时从对应 Release 的 checksums.txt 取官方值填进来。
# ---------------------------------------------------------------------------
$trusted = @{
    "1.15.0" = @{
        # 官方声明支持 Minecraft 1.21.6 / 1.21.7 / 1.21.8
        "fabric" = "c58ef35a133b6ffce96a74682138ac2ee818cbc063b7c62671db9f9d7d783ebb"
        "forge"  = "82b360a295c4ca12ea18750b75cf818b1250f76951f31c18f7fe2f0b4952649c"
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$targetCoord = "$Version-$MinecraftVersion"

if (-not $trusted.ContainsKey($Version)) {
    Write-Host "错误：版本 $Version 不在 SHA256 白名单中。" -ForegroundColor Red
    Write-Host "请先从 https://github.com/cabaletta/baritone/releases/tag/v$Version 的 checksums.txt" -ForegroundColor Yellow
    Write-Host "取官方 SHA256，并加入本脚本的 \$trusted 表。" -ForegroundColor Yellow
    exit 1
}

$artifactIds = @{
    "fabric"   = "baritone-api-fabric"
    "forge"    = "baritone-api-forge"
    "neoforge" = "baritone-api-neoforge"
}

$installed = 0
$skipped = 0

foreach ($loader in $Loaders) {
    $allowed = $trusted[$Version][$loader]
    if (-not $allowed) {
        Write-Host ("[{0}] 官方 {1} 未提供该变体，跳过。" -f $loader, $Version) -ForegroundColor Yellow
        $skipped++
        continue
    }

    $artifactId = $artifactIds[$loader]
    $fileName = "$artifactId-$Version.jar"
    $url = "https://github.com/cabaletta/baritone/releases/download/v$Version/$fileName"

    $destDir = Join-Path $repoRoot "baritone-maven\baritone\$artifactId\$targetCoord"
    $destJar = Join-Path $destDir "$artifactId-$targetCoord.jar"
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

    # 已存在且指纹正确 -> 直接通过（可离线、可重复执行）
    if (Test-Path $destJar) {
        $have = (Get-FileHash $destJar -Algorithm SHA256).Hash.ToLower()
        if ($have -eq $allowed.ToLower()) {
            Write-Host ("[{0}] 已存在且指纹正确，跳过下载：{1}-{2}.jar" -f $loader, $artifactId, $targetCoord) -ForegroundColor Green
            $installed++
            continue
        }
        Write-Host ("[{0}] 已存在但指纹不符，将重新下载。" -f $loader) -ForegroundColor Yellow
    }

    Write-Host ("[{0}] 下载 {1}" -f $loader, $fileName)
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) $fileName
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    # 优先官方直连；失败则走系统代理（本地 127.0.0.1:7897 之类）
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
        exit 1
    }

    $actual = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $allowed.ToLower()) {
        Write-Host "  SHA256 校验失败，拒绝安装！" -ForegroundColor Red
        Write-Host "    期望: $($allowed.ToLower())"
        Write-Host "    实际: $actual"
        exit 1
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
  <artifactId>$artifactId</artifactId>
  <version>$targetCoord</version>
  <packaging>jar</packaging>
</project>
"@
    $pomPath = Join-Path $destDir "$artifactId-$targetCoord.pom"
    [System.IO.File]::WriteAllText($pomPath, $pom, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host ("  已安装 -> {0}" -f ($destJar.Replace($repoRoot + "\", ""))) -ForegroundColor Green
    Write-Host ("  SHA256  {0}" -f $actual)
    $installed++
}

Write-Host ""
Write-Host "完成：安装 $installed 个，跳过 $skipped 个。" -ForegroundColor Green
Write-Host "对应坐标：baritone:baritone-api-<loader>:$targetCoord"
Write-Host ""
Write-Host "下一步：在工程 gradle.properties 设置 baritone_version=$targetCoord，然后构建并校验："
Write-Host "  .\gradlew.bat clean build" 
Write-Host "  .\scripts\verify-embedded-baritone.ps1 -ProjectDir <工程目录>"
