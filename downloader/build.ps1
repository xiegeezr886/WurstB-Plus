param(
    [ValidateSet('win-x64', 'win-arm64')][string]$Runtime = 'win-x64',
    [string]$DotNet = 'dotnet',
    [string]$OutputDirectory,
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
$project = Join-Path $PSScriptRoot 'WurstBPlus.Downloader.csproj'
$config = Join-Path $PSScriptRoot 'NuGet.Config'
Push-Location (Join-Path $PSScriptRoot 'Frontend')
try {
    & pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency install failed.' }
    & pnpm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }
if (-not $SkipTests) {
    & $DotNet run --project (Join-Path $PSScriptRoot 'Tests/WurstBPlus.Downloader.Tests.csproj') -c Release "-p:RestoreConfigFile=$config"
    if ($LASTEXITCODE -ne 0) { throw 'Downloader tests failed.' }
}
$output = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $PSScriptRoot "artifacts/release/$Runtime" }
$payloadDirectory = Join-Path $PSScriptRoot "artifacts/payload/$Runtime"
New-Item -ItemType Directory -Force -Path $output | Out-Null
& $DotNet publish $project -c Release -r $Runtime --self-contained true -p:PublishSingleFile=false -p:DebugType=None -p:DebugSymbols=false "-p:RestoreConfigFile=$config" -o $payloadDirectory
if ($LASTEXITCODE -ne 0) { throw 'Downloader publish failed.' }
$archive = Join-Path $PSScriptRoot "artifacts/payload/$Runtime.zip"
Compress-Archive -Path (Join-Path $payloadDirectory '*') -DestinationPath $archive -Force
$hashFile = Join-Path $PSScriptRoot "artifacts/payload/$Runtime.sha256"
[IO.File]::WriteAllText($hashFile, (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant(), [Text.Encoding]::ASCII)
$framework = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319'
$compiler = Join-Path $framework 'csc.exe'
& $compiler /nologo /target:winexe /platform:anycpu /optimize+ "/out:$output/WDownloader.exe" "/win32manifest:$PSScriptRoot/app.manifest" "/win32icon:$PSScriptRoot/Assets/WDownloader.ico" "/resource:$archive,Payload" "/resource:$hashFile,PayloadHash" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "/reference:$framework/System.IO.Compression.dll" (Join-Path $PSScriptRoot 'Bootstrap/Program.cs') (Join-Path $PSScriptRoot 'LoadingShape.cs')
if ($LASTEXITCODE -ne 0) { throw 'Single EXE bootstrap build failed.' }
Write-Host "Ready: $output/WDownloader.exe"
