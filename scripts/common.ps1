# Shared helpers for WurstB+ Plus development scripts.
# Dot-source from other scripts in this directory:
#   . (Join-Path $PSScriptRoot "common.ps1")

$script:WurstbJdkMajor = @{
    "1.20.1"  = 17
    "1.21.1"  = 21
    "1.21.11" = 21
    "26.1.2"  = 25
    "26.2"    = 25
}

$script:WurstbJdkEnv = @{
    17 = "WURSTBPLUS_JAVA17"
    21 = "WURSTBPLUS_JAVA21"
    25 = "WURSTBPLUS_JAVA25"
}

$script:WurstbWrapperDists = [ordered]@{
    "8.11"   = "c4te04g51qsyw1bxcb929u7br"
    "8.14.4" = "92wwslzcyst3phie3o264zltu"
    "9.4.1"  = "arn2x92ynaizyzdaamcbpbhtj"
    "9.6.0"  = "42k10rwplmzkhuboz9kdazi7s"
}

function Get-WurstbProjectRoot {
    param([string]$ProjectRoot = "")
    if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
        return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    }
    return (Resolve-Path -LiteralPath $ProjectRoot).Path
}

function Test-WurstbJdkHome {
    param([string]$JdkHome)
    if ([string]::IsNullOrWhiteSpace($JdkHome)) { return $false }
    return Test-Path -LiteralPath (Join-Path $JdkHome "bin\java.exe")
}

function Get-WurstbJavaMajorFromName {
    param([string]$JdkHome)
    $leaf = Split-Path $JdkHome -Leaf
    if ($leaf -match '(?:jdk-?|temurin-|zulu-|microsoft-)(\d+)') {
        return [int]$Matches[1]
    }
    if ($leaf -match '^(\d+)') {
        return [int]$Matches[1]
    }
    return $null
}

function Get-WurstbJavaMajor {
    param([string]$JdkHome)
    if (-not (Test-WurstbJdkHome $JdkHome)) { return $null }
    try {
        $java = Join-Path $JdkHome "bin\java.exe"
        $output = & $java -version 2>&1 | Out-String
        if ($output -match 'version "(\d+)') {
            return [int]$Matches[1]
        }
    } catch {
        # Antivirus may intercept java.exe; fall back to the folder name.
    }
    return Get-WurstbJavaMajorFromName $JdkHome
}

function Get-WurstbJdkSearchRoots {
    @(
        "C:\Program Files\Microsoft"
        "C:\Program Files\Java"
        "C:\Program Files\Eclipse Adoptium"
        "C:\Program Files\Amazon Corretto"
        "C:\Program Files\Zulu"
        "C:\Program Files\Temurin"
        "${env:LOCALAPPDATA}\Programs\Eclipse Adoptium"
        "${env:USERPROFILE}\.jdks"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
}

function Find-WurstbJdks {
    $found = New-Object System.Collections.Generic.List[object]
    $seen = New-Object "System.Collections.Generic.HashSet[string]"
    foreach ($root in Get-WurstbJdkSearchRoots) {
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $jdkPath = $_.FullName
                if ($jdkPath -match '\\latest$') { return }
                if (-not (Test-WurstbJdkHome $jdkPath)) { return }
                if (-not $seen.Add($jdkPath.ToLowerInvariant())) { return }
                $major = Get-WurstbJavaMajor $jdkPath
                if ($null -eq $major) { return }
                $found.Add([pscustomobject]@{ JdkHome = $jdkPath; Major = $major })
            }
    }
    return $found
}

function Get-WurstbJdkHome {
    param(
        [Parameter(Mandatory = $true)]
        [string]$McVersion,
        [switch]$Quiet
    )
    $major = $script:WurstbJdkMajor[$McVersion]
    if (-not $major) {
        throw "Unknown Minecraft version for JDK lookup: $McVersion"
    }

    $envName = $script:WurstbJdkEnv[$major]
    $override = [Environment]::GetEnvironmentVariable($envName)
    if (Test-WurstbJdkHome $override) { return $override }

    $javaHome = $env:JAVA_HOME
    if (Test-WurstbJdkHome $javaHome) {
        $homeMajor = Get-WurstbJavaMajor $javaHome
        if ($homeMajor -eq $major) { return $javaHome }
    }

    $match = Find-WurstbJdks |
        Where-Object { $_.Major -eq $major } |
        Sort-Object JdkHome -Descending |
        Select-Object -First 1
    if ($match) { return $match.JdkHome }

    $legacy = switch ($major) {
        17 { "C:\Program Files\Java\jdk-17" }
        21 { "C:\Program Files\Java\jdk-21" }
        25 { "C:\Program Files\Java\jdk-25.0.4" }
    }
    if (Test-WurstbJdkHome $legacy) { return $legacy }

    if (-not $Quiet) {
        Write-Host "No JDK $major found for MC $McVersion. Set $envName or install a matching JDK." -ForegroundColor Yellow
    }
    return $null
}

function Get-WurstbGradleProjects {
    param([string]$ProjectRoot)
    @(
        @{ Name = "Forge 1.20.1";     Dir = "";                           MC = "1.20.1";  Version = "v1.6.0"; Loader = "Forge";    Wrapper = "8.11";   Artifact = "WurstB+ Plus-v1.6.0-Forge-1.20.1.jar" }
        @{ Name = "Forge 1.21.1";     Dir = "versions\1.21.1";            MC = "1.21.1";  Version = "v1.5.0"; Loader = "Forge";    Wrapper = "8.11";   Artifact = "WurstB+ Plus-v1.5.0-Forge-1.21.1.jar" }
        @{ Name = "Forge 1.21.11";    Dir = "versions\1.21.11";           MC = "1.21.11"; Version = "v1.5.0"; Loader = "Forge";    Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-Forge-1.21.11.jar" }
        @{ Name = "Forge 26.1.2";     Dir = "versions\26.1.2";            MC = "26.1.2";  Version = "v1.5.0"; Loader = "Forge";    Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-Forge-26.1.2.jar" }
        @{ Name = "Forge 26.2";       Dir = "versions\26.2";              MC = "26.2";    Version = "v1.5.0"; Loader = "Forge";    Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-Forge-26.2.jar" }
        @{ Name = "NeoForge 1.20.1";  Dir = "neoforge";                   MC = "1.20.1";  Version = "v1.5.0"; Loader = "NeoForge"; Wrapper = "8.14.4"; Artifact = "WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar" }
        @{ Name = "NeoForge 1.21.1";  Dir = "neoforge\versions\1.21.1";   MC = "1.21.1";  Version = "v1.5.0"; Loader = "NeoForge"; Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar" }
        @{ Name = "NeoForge 1.21.11"; Dir = "neoforge\versions\1.21.11";  MC = "1.21.11"; Version = "v1.5.0"; Loader = "NeoForge"; Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar" }
        @{ Name = "NeoForge 26.1.2";  Dir = "neoforge\versions\26.1.2";   MC = "26.1.2";  Version = "v1.5.0"; Loader = "NeoForge"; Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar" }
        @{ Name = "NeoForge 26.2";    Dir = "neoforge\versions\26.2";     MC = "26.2";    Version = "v1.5.0"; Loader = "NeoForge"; Wrapper = "9.4.1";  Artifact = "WurstB+ Plus-v1.5.0-NeoForge-26.2.jar" }
        @{ Name = "Fabric 1.20.1";    Dir = "fabric";                     MC = "1.20.1";  Version = "v1.5.0"; Loader = "Fabric";   Wrapper = "8.11";   Artifact = "WurstB+ Plus-1.5.0-Fabric-1.20.1.jar" }
        @{ Name = "Fabric 1.21.1";    Dir = "fabric\versions\1.21.1";     MC = "1.21.1";  Version = "v1.5.0"; Loader = "Fabric";   Wrapper = "8.11";   Artifact = "WurstB+ Plus-1.5.0-Fabric-1.21.1.jar" }
        @{ Name = "Fabric 1.21.11";   Dir = "fabric\versions\1.21.11";    MC = "1.21.11"; Version = "v1.5.0"; Loader = "Fabric";   Wrapper = "9.6.0";  Artifact = "WurstB+ Plus-1.5.0-Fabric-1.21.11.jar" }
        @{ Name = "Fabric 26.1.2";    Dir = "fabric\versions\26.1.2";     MC = "26.1.2";  Version = "v1.5.0"; Loader = "Fabric";   Wrapper = "9.6.0";  Artifact = "WurstB+ Plus-1.5.0-Fabric-26.1.2.jar" }
        @{ Name = "Fabric 26.2";      Dir = "fabric\versions\26.2";       MC = "26.2";    Version = "v1.5.0"; Loader = "Fabric";   Wrapper = "9.6.0";  Artifact = "WurstB+ Plus-1.5.0-Fabric-26.2.jar" }
    )
}

function Get-WurstbWrapperJarPath {
    param(
        [string]$ProjectRoot,
        [string]$Dir
    )
    $projectDir = if ([string]::IsNullOrWhiteSpace($Dir)) { $ProjectRoot } else { Join-Path $ProjectRoot $Dir }
    return Join-Path $projectDir "gradle\wrapper\gradle-wrapper.jar"
}
