param(
	[string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
	$ProjectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
} else {
	$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$jdk = if ($env:WURSTBPLUS_JAVA25) {
	$env:WURSTBPLUS_JAVA25
} else {
	"C:\Program Files\Java\jdk-25.0.4"
}
$javac = Join-Path $jdk "bin\javac.exe"
$java = Join-Path $jdk "bin\java.exe"
$asmRoot = Join-Path $ProjectRoot ".test\libraries\org\ow2\asm"
$asm = Join-Path $asmRoot "asm\9.10.1\asm-9.10.1.jar"
$asmCommons = Join-Path $asmRoot "asm-commons\9.10.1\asm-commons-9.10.1.jar"
$minecraftCandidates = New-Object System.Collections.Generic.List[string]
$minecraftCandidates.Add((Join-Path $env:USERPROFILE ".gradle\caches\fabric-loom\26.2\minecraft-merged.jar"))
$forgeCache = Join-Path $env:USERPROFILE ".gradle\caches\minecraftforge\forgegradle\mavenizer\caches\forge\net\minecraftforge\forge"
if (Test-Path -LiteralPath $forgeCache) {
	Get-ChildItem -LiteralPath $forgeCache -Directory -Filter "26.2-*" |
		ForEach-Object {
			$minecraftCandidates.Add((Join-Path $_.FullName "official\26.2\recompiled.jar"))
		}
}
$minecraft = $minecraftCandidates | Where-Object { Test-Path -LiteralPath $_ } |
	Select-Object -First 1
if (-not $minecraft) {
	throw "Minecraft 26.2 compile jar is missing. Run a 26.2 Gradle setup once."
}
$toolRoot = Join-Path $ProjectRoot "_tools\baritone-26.2-compat"
$classes = Join-Path $toolRoot "build\classes"

foreach ($required in @($javac, $java, $asm, $asmCommons, $minecraft)) {
	if (-not (Test-Path -LiteralPath $required)) {
		throw "Missing compatibility-patcher dependency: $required"
	}
}

New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = @(
	(Join-Path $toolRoot "src\baritone\api\utils\LegacyTuple.java")
	(Join-Path $toolRoot "src\baritone\api\utils\LegacyTesselator.java")
	(Join-Path $toolRoot "src\baritone\api\utils\LegacyRenderPipelineBuilder.java")
	(Join-Path $toolRoot "src\baritone\api\utils\LegacyRenderType.java")
	(Join-Path $toolRoot "src\tools\BaritoneCompatibilityPatcher.java")
)
& $javac --release 21 -cp "$asm;$asmCommons;$minecraft" -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "Compatibility patcher compilation failed" }

$legacyTuple = Join-Path $classes "baritone\api\utils\LegacyTuple.class"
$legacyTesselator = Join-Path $classes "baritone\api\utils\LegacyTesselator.class"
$pipelineBuilderCompat = Join-Path $classes "baritone\api\utils\LegacyRenderPipelineBuilder.class"
$renderTypeCompat = Join-Path $classes "baritone\api\utils\LegacyRenderType.class"
$patcherClassPath = "$classes;$asm;$asmCommons"
$artifacts = @(
	"baritone-maven\baritone\baritone-api-fabric\1.18.0-26.2\baritone-api-fabric-1.18.0-26.2.jar",
	"baritone-maven\baritone\baritone-forge\1.18.0-26.2\baritone-forge-1.18.0-26.2.jar",
	"baritone-maven\baritone\baritone-neoforge\1.18.0-26.2\baritone-neoforge-1.18.0-26.2.jar"
)

foreach ($relativePath in $artifacts) {
	$artifact = Join-Path $ProjectRoot $relativePath
	if (-not (Test-Path -LiteralPath $artifact)) {
		throw "Baritone artifact not found: $artifact"
	}
	$backup = "$artifact.pre-26.2-compat"
	if (-not (Test-Path -LiteralPath $backup)) {
		Copy-Item -LiteralPath $artifact -Destination $backup
	}
	$temp = "$artifact.tmp"
	& $java -cp $patcherClassPath tools.BaritoneCompatibilityPatcher `
		$backup $temp $legacyTuple $legacyTesselator $pipelineBuilderCompat `
		$renderTypeCompat
	if ($LASTEXITCODE -ne 0) { throw "Failed to patch $artifact" }
	Move-Item -LiteralPath $temp -Destination $artifact -Force
	Write-Host "Patched: $relativePath"
}
