# 设置本机构建环境（仅 DSH 沙箱需要 zipfs 补丁）。
# 用法：. .\scripts\build-env.ps1   （点号执行，变量才会留在当前会话）

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent

$patch = Join-Path $repo 'tools\build-env\zipfs-patch.jar'
if (-not (Test-Path $patch)) {
    throw "缺少 $patch"
}

$env:JAVA_TOOL_OPTIONS = "--patch-module=jdk.zipfs=$patch"

# 仓库默认的 Gradle 缓存与便携 JDK（存在才覆盖，避免影响正常机器）
$gradleHome = Join-Path $repo '.dsh-tmp\gradle-home'
if (Test-Path $gradleHome) { $env:GRADLE_USER_HOME = $gradleHome }

$jdks = @()
foreach ($name in 'zulu17.70.113-ca-jdk17.0.21.0.3-win_x64',
                'zulu21.54.109-ca-jdk21.0.13.0.3-win_x64',
                'zulu25.38.109-ca-jdk25.0.5.0.3-win_x64') {
    $p = Join-Path $repo ".dsh-tmp\toolchains\$name"
    if (Test-Path $p) { $jdks += $p }
}
if ($jdks.Count -gt 0) {
    # Gradle 9 的 toolchain 自动探测找不到 .dsh-tmp 下的 JDK，显式告诉它
    $env:JAVA_TOOL_OPTIONS += " -Dorg.gradle.java.installations.paths=" + ($jdks -join ',')
}

if (-not $env:WURSTBPLUS_JAVA17) {
    $jdk17 = Join-Path $repo '.dsh-tmp\toolchains\zulu17.70.113-ca-jdk17.0.21.0.3-win_x64'
    if (Test-Path $jdk17) { $env:WURSTBPLUS_JAVA17 = $jdk17 }
}

Write-Host "JAVA_TOOL_OPTIONS=$env:JAVA_TOOL_OPTIONS"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "WURSTBPLUS_JAVA17=$env:WURSTBPLUS_JAVA17"
