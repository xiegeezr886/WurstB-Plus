param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Version,                     # MC 版本，例如 26.2 / 1.21.9 / 1.20.1

    [Parameter(Position = 1)]
    [ValidateSet('forge', 'fabric', 'neoforge')]
    [string]$Loader = 'forge',

    [switch]$Offline,                     # 加 --offline：只用手上缓存，不联网

    [switch]$Server,                      # 启动服务端而不是客户端

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs                  # 其余参数原样转给 Gradle
)

# 真实启动某个版本客户端的入口。
#
#   .\scripts\run-client.ps1 26.2                 # Forge 26.2 客户端
#   .\scripts\run-client.ps1 1.21.9 fabric        # Fabric 1.21.9 客户端
#   .\scripts\run-client.ps1 1.20.1 neoforge -Offline
#
# 会自动带上仓库里的国内镜像初始化脚本（gradle/init-mirrors.gradle）。
# 首次启动要下载该版本的资源文件与依赖，国内走 BMCLAPI / 阿里云镜像。

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

# 仓库布局：1.20.1 的三个加载器是各自的根工程，其余版本在 <加载器>/versions/<版本>
if ($Version -eq '1.20.1') {
    $projectDir = switch ($Loader) {
        'forge'    { $repo }
        'fabric'   { Join-Path $repo 'fabric' }
        'neoforge' { Join-Path $repo 'neoforge' }
    }
} elseif ($Loader -eq 'forge') {
    $projectDir = Join-Path $repo "versions\$Version"
} else {
    $projectDir = Join-Path $repo "$Loader\versions\$Version"
}

if (-not (Test-Path $projectDir)) {
    Write-Host "找不到工程目录：$projectDir" -ForegroundColor Red
    $candidates = @()
    foreach ($d in @('versions', 'fabric\versions', 'neoforge\versions')) {
        $full = Join-Path $repo $d
        if (Test-Path $full) {
            $candidates += (Get-ChildItem $full -Directory).Name
        }
    }
    Write-Host ("可用版本：" + (($candidates | Sort-Object -Unique) -join ', '))
    exit 1
}

$initScript = Join-Path $repo 'gradle\init-mirrors.gradle'
$task = if ($Server) { 'runServer' } else { 'runClient' }

$gradleArgs = @($task, '--init-script', $initScript)
if ($Offline) { $gradleArgs += '--offline' }
if ($ExtraArgs) { $gradleArgs += $ExtraArgs }

Write-Host "工程：$projectDir"
Write-Host "任务：$task"
Write-Host ""

Push-Location $projectDir
try {
    & (Join-Path $projectDir 'gradlew.bat') @gradleArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
