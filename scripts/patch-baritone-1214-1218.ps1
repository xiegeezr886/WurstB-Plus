param(
    [string]$AsmJar = "",
    [string]$Java = "C:\Program Files\Java\jdk-21\bin\java.exe"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $AsmJar) {
    $asmCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.10.1'
    $AsmJar = (Get-ChildItem -LiteralPath $asmCache -Recurse -Filter 'asm-9.10.1.jar' |
        Select-Object -First 1).FullName
}
if (-not (Test-Path -LiteralPath $AsmJar)) { throw "ASM 9.10.1 jar not found: $AsmJar" }
if (-not (Test-Path -LiteralPath $Java)) { throw "Java 21 not found: $Java" }

foreach ($loader in @('forge', 'neoforge')) {
    foreach ($mc in @('1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8')) {
        $version = "1.17.0-1.21.11-mc$mc"
        $jar = Join-Path $root "baritone-maven\baritone\baritone-$loader\$version\baritone-$loader-$version.jar"
        if (-not (Test-Path -LiteralPath $jar)) { throw "Baritone jar not found: $jar" }
        $patcher = if ($mc -in @('1.21.4', '1.21.5')) { 'Baritone1214to1215Patcher.java' } else { 'Baritone1216Patcher.java' }
        & $Java --class-path $AsmJar (Join-Path $PSScriptRoot $patcher) $jar
        if ($LASTEXITCODE -ne 0) { throw "Baritone patch failed: $jar" }
        if ($mc -eq '1.21.4') {
            & $Java --class-path $AsmJar (Join-Path $PSScriptRoot 'Baritone1214ClickPatcher.java') $jar
            if ($LASTEXITCODE -ne 0) { throw "Baritone click patch failed: $jar" }
        }
    }
}
