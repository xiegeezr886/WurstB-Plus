param(
    [Parameter(Mandatory = $true)][string]$InputJar,
    [string]$OutputJar = "",
    [string]$Config = "",
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")
$repo = Get-WurstbProjectRoot
$inputFile = (Resolve-Path -LiteralPath $InputJar).Path
if ([string]::IsNullOrWhiteSpace($OutputJar)) {
    $OutputJar = Join-Path (Split-Path $inputFile -Parent) `
        (([IO.Path]::GetFileNameWithoutExtension($inputFile)) + "-protected.jar")
}
$outputFile = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputJar)
if ([string]::IsNullOrWhiteSpace($Config)) {
    $Config = Join-Path $repo "tools/protector/protection.properties"
}
$configFile = (Resolve-Path -LiteralPath $Config).Path
foreach ($path in @($outputFile, "$outputFile.mapping.tsv", "$outputFile.report.txt")) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite: $path" }
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = Get-WurstbJdkHome -McVersion "1.20.1" -Quiet
    $portableRoot = Join-Path $repo ".dsh-tmp/toolchains"
    if (-not $JavaHome -and (Test-Path -LiteralPath $portableRoot)) {
        $portableJdk = Get-ChildItem -LiteralPath $portableRoot -Directory |
            Where-Object { (Get-WurstbJavaMajor $_.FullName) -eq 17 } |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($portableJdk) { $JavaHome = $portableJdk.FullName }
    }
}
if (-not (Test-WurstbJdkHome $JavaHome)) {
    throw "JDK 17 is required to run the repository Gradle wrapper. Pass -JavaHome or set WURSTBPLUS_JAVA17."
}
if ((Get-WurstbJavaMajor $JavaHome) -notin @(17, 21)) {
    throw "Use JDK 17 or 21 for Gradle 8.11. The protector can process Java 17/21/25 jars."
}
$savedJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & (Join-Path $repo "gradlew.bat") -p (Join-Path $repo "tools/protector") `
        test protectJar "-Pinput=$inputFile" "-Poutput=$outputFile" "-Pconfig=$configFile" `
        --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Protector failed (exit $LASTEXITCODE); see Gradle output." }
} finally {
    $env:JAVA_HOME = $savedJavaHome
}
