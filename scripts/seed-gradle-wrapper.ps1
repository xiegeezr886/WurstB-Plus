<#
  Seed ~/.gradle/wrapper/dists from the workspace tools/ Gradle distributions
  and copy a complete gradle-wrapper.jar into every project that is missing one.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\seed-gradle-wrapper.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$ToolsRoot = ""
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
$ProjectRoot = Get-WurstbProjectRoot $ProjectRoot

if ([string]::IsNullOrWhiteSpace($ToolsRoot)) {
    $candidates = @(
        (Join-Path $ProjectRoot "tools"),
        (Join-Path (Split-Path $ProjectRoot -Parent) "tools")
    )
    $ToolsRoot = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $ToolsRoot) {
    throw "tools/ directory not found next to the project. Pass -ToolsRoot."
}

$distsRoot = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
New-Item -ItemType Directory -Path $distsRoot -Force | Out-Null

Write-Host "Seeding Gradle wrapper distributions from $ToolsRoot"
foreach ($version in $script:WurstbWrapperDists.Keys) {
    $hash = $script:WurstbWrapperDists[$version]
    $dst = Join-Path $distsRoot "gradle-$version-bin\$hash"
    $zip = Join-Path $ToolsRoot "gradle-$version-bin.zip"
    $inner = Join-Path $ToolsRoot "gradle-$version"
    if (-not (Test-Path -LiteralPath $zip) -and -not (Test-Path -LiteralPath $inner)) {
        Write-Host ("  gradle-{0,-8} SKIP (no zip or unpacked dist)" -f $version) -ForegroundColor Yellow
        continue
    }
    New-Item -ItemType Directory -Path $dst -Force | Out-Null
    Get-ChildItem $dst -Filter "*.part" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem $dst -Filter "*.lck" -ErrorAction SilentlyContinue | Remove-Item -Force
    if (Test-Path -LiteralPath $zip) {
        Copy-Item -LiteralPath $zip -Destination (Join-Path $dst "gradle-$version-bin.zip") -Force
    }
    if (Test-Path -LiteralPath $inner) {
        $unpacked = Join-Path $dst "gradle-$version"
        if (Test-Path -LiteralPath $unpacked) {
            Remove-Item -LiteralPath $unpacked -Recurse -Force
        }
        Copy-Item -LiteralPath $inner -Destination $unpacked -Recurse -Force
        Set-Content -Path (Join-Path $dst "gradle-$version-bin.zip.ok") -Value "" -NoNewline
    }
    $ready = (Test-Path (Join-Path $dst "gradle-$version\bin\gradle.bat")) -and
        (Test-Path (Join-Path $dst "gradle-$version-bin.zip.ok"))
    Write-Host ("  gradle-{0,-8} ready={1}" -f $version, $ready)
}

$goodJar = $null
foreach ($project in Get-WurstbGradleProjects $ProjectRoot) {
    $jar = Get-WurstbWrapperJarPath $ProjectRoot $project.Dir
    if ((Test-Path -LiteralPath $jar) -and ((Get-Item -LiteralPath $jar).Length -ge 40000)) {
        $goodJar = $jar
        break
    }
}
if (-not $goodJar) {
    throw "No complete gradle-wrapper.jar found to copy."
}

Write-Host "Copying wrapper jar from $goodJar"
$missing = 0
foreach ($project in Get-WurstbGradleProjects $ProjectRoot) {
    $jar = Get-WurstbWrapperJarPath $ProjectRoot $project.Dir
    $dir = Split-Path $jar -Parent
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    if (-not (Test-Path -LiteralPath $jar) -or ((Get-Item -LiteralPath $jar).Length -lt 40000)) {
        Copy-Item -LiteralPath $goodJar -Destination $jar -Force
        $missing++
        Write-Host ("  restored {0}" -f $jar.Substring($ProjectRoot.Length))
    }
}
Write-Host ("wrapper jars restored: {0}" -f $missing)
