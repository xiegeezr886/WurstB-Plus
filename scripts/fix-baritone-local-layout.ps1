# 补齐本地仓库里缺失的 baritone-forge / baritone-neoforge 目录
# （各 Forge/NeoForge 工程请求的 group 是 baritone-forge / baritone-neoforge，
#   而早期 install 脚本误写成了 baritone-api-forge / baritone-api-neoforge。）
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$bm = Join-Path $root "baritone-maven\baritone"

# 坐标 -> 该坐标下应存在的 (源目录, 源artifact basename, 目标目录, 目标artifact basename)
$jobs = @(
    @{ coord = "1.14.0-1.21.5";  src = "baritone-api-forge";    srcName = "baritone-api-forge";    dst = "baritone-forge";    dstName = "baritone-forge" },
    @{ coord = "1.15.0-1.21.6";  src = "baritone-api-forge";    srcName = "baritone-api-forge";    dst = "baritone-forge";    dstName = "baritone-forge" },
    @{ coord = "1.15.0-1.21.8";  src = "baritone-api-forge";    srcName = "baritone-api-forge";    dst = "baritone-forge";    dstName = "baritone-forge" },
    @{ coord = "1.16.0-1.21.9";  src = "baritone-api-forge";    srcName = "baritone-api-forge";    dst = "baritone-forge";    dstName = "baritone-forge" },
    @{ coord = "1.16.0-1.21.10"; src = "baritone-api-forge";    srcName = "baritone-api-forge";    dst = "baritone-forge";    dstName = "baritone-forge" },
    @{ coord = "1.14.0-1.21.5";  src = "baritone-api-neoforge"; srcName = "baritone-api-neoforge"; dst = "baritone-neoforge"; dstName = "baritone-neoforge" },
    @{ coord = "1.15.0-1.21.6";  src = "baritone-api-neoforge"; srcName = "baritone-api-neoforge"; dst = "baritone-neoforge"; dstName = "baritone-neoforge" },
    @{ coord = "1.15.0-1.21.7";  src = "baritone-api-neoforge"; srcName = "baritone-api-neoforge"; dst = "baritone-neoforge"; dstName = "baritone-neoforge" },
    @{ coord = "1.15.0-1.21.8";  src = "baritone-api-neoforge"; srcName = "baritone-api-neoforge"; dst = "baritone-neoforge"; dstName = "baritone-neoforge" },
    @{ coord = "1.16.0-1.21.9";  src = "baritone-api-neoforge"; srcName = "baritone-api-neoforge"; dst = "baritone-neoforge"; dstName = "baritone-neoforge" },
    @{ coord = "1.16.0-1.21.10"; src = "baritone-api-neoforge"; srcName = "baritone-api-neoforge"; dst = "baritone-neoforge"; dstName = "baritone-neoforge" }
)

$done = 0
$skip = 0
foreach ($j in $jobs) {
    $srcJar = Join-Path $bm "$($j.src)\$($j.coord)\$($j.srcName)-$($j.coord).jar"
    $dstDir = Join-Path $bm "$($j.dst)\$($j.coord)"
    $dstJar = Join-Path $dstDir "$($j.dstName)-$($j.coord).jar"

    if (-not (Test-Path $srcJar)) { Write-Host "  源缺失，跳过: $srcJar"; $skip++; continue }
    if (Test-Path $dstJar) { Write-Host "  已存在: $($j.dst)/$($j.coord)"; $skip++; continue }

    New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    Copy-Item $srcJar $dstJar -Force

    $pom = @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>baritone</groupId>
  <artifactId>$($j.dstName)</artifactId>
  <version>$($j.coord)</version>
  <packaging>jar</packaging>
</project>
"@
    [System.IO.File]::WriteAllText((Join-Path $dstDir "$($j.dstName)-$($j.coord).pom"), $pom, (New-Object System.Text.UTF8Encoding($false)))

    $h = (Get-FileHash $dstJar -Algorithm SHA256).Hash.ToLower().Substring(0, 16)
    Write-Host "  补齐 $($j.dst)/$($j.coord)  sha256=$h..." -ForegroundColor Green
    $done++
}

Write-Host ""
Write-Host "补齐 $done 个，跳过 $skip 个。"
