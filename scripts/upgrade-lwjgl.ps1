# LWJGL 3.3.4 升级 - 修复 Java 21 运行 Forge 1.20.1 崩溃
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$nativesDir = "D:\.penguin\.minecraft\versions\1.20.1-Forge_47.4.10\1.20.1-Forge_47.4.10-natives"
$tempDir = Join-Path $env:TEMP "lwjgl334"
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

$urls = @(
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl/3.3.4/lwjgl-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-glfw/3.3.4/lwjgl-glfw-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-jemalloc/3.3.4/lwjgl-jemalloc-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-openal/3.3.4/lwjgl-openal-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-opengl/3.3.4/lwjgl-opengl-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-stb/3.3.4/lwjgl-stb-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-tinyfd/3.3.4/lwjgl-tinyfd-3.3.4-natives-windows.jar",
    "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-freetype/3.3.4/lwjgl-freetype-3.3.4-natives-windows.jar"
)

Write-Host "Downloading LWJGL 3.3.4 natives..." -ForegroundColor Cyan
foreach ($url in $urls) {
    $name = Split-Path $url -Leaf
    Write-Host "  $name"
    Invoke-WebRequest -Uri $url -OutFile (Join-Path $tempDir $name) -UseBasicParsing
}

Write-Host ""
Write-Host "Extracting DLLs to $nativesDir" -ForegroundColor Cyan
$count = 0
Get-ChildItem $tempDir -Filter "*.jar" | ForEach-Object {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -like "*.dll") {
            $dest = Join-Path $nativesDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            Write-Host "  Upgraded: $($entry.Name)" -ForegroundColor Green
            $count++
        }
    }
    $zip.Dispose()
}

Remove-Item $tempDir -Recurse -Force
Write-Host ""
Write-Host "Done! $count native DLLs upgraded to 3.3.4." -ForegroundColor Green
