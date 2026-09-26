param(
	[string]$ProjectRoot = "",
	[switch]$WhatIf,
	[switch]$SelfTest,
	[string[]]$Versions = @(),
	# A fresh clone legitimately has no baritone-maven/ (it is gitignored), so the
	# caller can choose to treat "unresolved" as a warning instead of an error.
	[switch]$AllowUnresolved
)

# Makes every bundled Baritone declare the Minecraft version of the project that
# ships it.
#
# The loader validates the nested mod's own `minecraft` dependency. A matching
# declaration is necessary, but cannot make an incompatible binary work on a
# different Minecraft version. Missing version-specific jars must be built from
# matching sources, not manufactured by copying another version's jar.
#
# A jar that already admits the target MC is left untouched. Existing private
# version-specific jars can have their declaration corrected. Missing jars are
# reported as errors; this script never creates cross-version binary copies.
#
# Idempotent: re-running changes nothing.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
	$ProjectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
} else {
	$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}
$replaceEntryScript = Join-Path $scriptRoot "replace-jar-entry.ps1"
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ------------------------------------------------------------- version logic
function Compare-McVersion([string]$a, [string]$b) {
	$pa = @($a -split '\.'); $pb = @($b -split '\.')
	for ($i = 0; $i -lt [Math]::Max($pa.Count, $pb.Count); $i++) {
		$va = 0; $vb = 0
		if ($i -lt $pa.Count -and $pa[$i] -match '^\d+$') { $va = [int]$pa[$i] }
		if ($i -lt $pb.Count -and $pb[$i] -match '^\d+$') { $vb = [int]$pb[$i] }
		if ($va -ne $vb) { return [Math]::Sign($va - $vb) }
	}
	return 0
}

# One Fabric version predicate: "~1.21.8", ">=1.20 <=1.20.1", "1.21.*", "*", "1.21.8".
function Test-FabricTerm([string]$term, [string]$mc) {
	$t = $term.Trim()
	if ([string]::IsNullOrWhiteSpace($t) -or $t -eq '*') { return $true }

	# Comparator chain, e.g. ">=1.20 <=1.20.1".
	if ($t -match '^[<>]') {
		foreach ($part in @($t -split '\s+' | Where-Object { $_ })) {
			$m = [regex]::Match($part, '^(>=|<=|>|<|=)?\s*(.+)$')
			if (-not $m.Success) { continue }
			$c = Compare-McVersion $mc $m.Groups[2].Value.Trim()
			switch ($m.Groups[1].Value) {
				'>=' { if ($c -lt 0) { return $false } }
				'<=' { if ($c -gt 0) { return $false } }
				'>' { if ($c -le 0) { return $false } }
				'<' { if ($c -ge 0) { return $false } }
				default { if ($c -ne 0) { return $false } }
			}
		}
		return $true
	}

	# Wildcard, e.g. "1.21.*" / "1.21.x".
	# Verified against fabric-loader 0.19.5: "26.1.*" admits 26.1, 26.1.1, 26.1.2
	# but not 26.2 -- so the prefix without its trailing dot also matches.
	if ($t -match '\*' -or $t -match '\.x$') {
		$prefix = ($t -replace '(\*|x)$', '')
		return (($mc -eq $prefix.TrimEnd('.')) -or $mc.StartsWith($prefix))
	}

	# Tilde / caret, e.g. "~1.21.8" -> >=1.21.8 <1.22.0 ; "^1.2.3" -> >=1.2.3 <2.0.0
	if ($t.StartsWith('~') -or $t.StartsWith('^')) {
		$base = $t.Substring(1)
		$parts = @($base -split '\.')
		if ($t.StartsWith('~') -and $parts.Count -ge 3) { $hi = "$($parts[0]).$([int]$parts[1] + 1).0" }
		elseif ($t.StartsWith('~') -and $parts.Count -eq 2) { $hi = "$($parts[0]).$([int]$parts[1] + 1).0" }
		else { $hi = "$([int]$parts[0] + 1).0.0" }
		return ((Compare-McVersion $mc $base) -ge 0) -and ((Compare-McVersion $mc $hi) -lt 0)
	}

	return (Compare-McVersion $mc $t) -eq 0
}

# A whole Fabric spec: either a JSON array (OR of terms) or a single term.
function Test-FabricSpec([string]$spec, [string]$mc) {
	$s = $spec.Trim()
	if ($s.StartsWith('[')) {
		$inner = $s.Substring(1, $s.Length - 2)
		foreach ($term in @($inner -split ',')) {
			$t = $term.Trim().Trim('"').Trim("'").Trim()
			if (Test-FabricTerm $t $mc) { return $true }
		}
		return $false
	}
	return (Test-FabricTerm ($s.Trim('"').Trim("'")) $mc)
}

# A Forge/NeoForge Maven range: "[1.21.11]", "[1.20,1.20.1]", "[1.21,1.22)", "[1.21.11,)".
function Test-ForgeSpec([string]$spec, [string]$mc) {
	$s = $spec.Trim()
	if ([string]::IsNullOrWhiteSpace($s)) { return $false }
	if ($s.StartsWith('[') -or $s.StartsWith('(')) {
		# A NeoForge wildcard such as "[26.1,)" is a plain range; "[26.1.*]" is not used.
		$loInc = $s.StartsWith('[')
		$hiInc = $s.EndsWith(']')
		$body = @($s.Substring(1, $s.Length - 2) -split ',' | ForEach-Object { $_.Trim() })
		if ($body.Count -eq 1) { return (Compare-McVersion $mc $body[0]) -eq 0 }
		if ($body[0]) {
			$lo = Compare-McVersion $mc $body[0]
			if ($loInc) { if ($lo -lt 0) { return $false } } else { if ($lo -le 0) { return $false } }
		}
		if ($body.Count -gt 1 -and $body[1]) {
			$hi = Compare-McVersion $mc $body[1]
			if ($hiInc) { if ($hi -gt 0) { return $false } } else { if ($hi -ge 0) { return $false } }
		}
		return $true
	}
	if ($s -match '\*' -or $s -match '\.x$') {
		$prefix = ($s -replace '(\*|x)$', '')
		return (($mc -eq $prefix.TrimEnd('.')) -or $mc.StartsWith($prefix))
	}
	if ($s -match '^[<>]') { return (Test-FabricTerm $s $mc) }
	return (Compare-McVersion $mc $s) -eq 0
}

function Test-McAdmitted([string]$spec, [string]$mc, [string]$flavor) {
	if ([string]::IsNullOrWhiteSpace($spec)) { return $false }
	if ($flavor -eq 'fabric') { return (Test-FabricSpec $spec $mc) }
	return (Test-ForgeSpec $spec $mc)
}

# The raw `minecraft` dependency spec inside a loader metadata file.
function Get-McSpec([string]$text, [string]$entry) {
	if ($entry -eq "fabric.mod.json") {
		$m = [regex]::Match($text, '"minecraft"\s*:\s*(\[[^\]]*\]|"[^"]*")')
		return $(if ($m.Success) { $m.Groups[1].Value } else { $null })
	}
	$modId = [regex]::Match($text, 'modId\s*=\s*"minecraft"')
	if (-not $modId.Success) { return $null }
	$vr = [regex]::Match($text.Substring($modId.Index), 'versionRange\s*=\s*"([^"]*)"')
	return $(if ($vr.Success) { $vr.Groups[1].Value } else { $null })
}

# Rewrite the declared minecraft version to exactly this MC version.
function Set-DeclaredMc([string]$text, [string]$entry, [string]$mc) {
	if ($entry -eq "fabric.mod.json") {
		$pattern = '"minecraft"\s*:\s*(\[[^\]]*\]|"[^"]*")'
		if (-not [regex]::IsMatch($text, $pattern)) { throw "no minecraft dependency in fabric.mod.json" }
		return [regex]::Replace($text, $pattern, ('"minecraft": ["' + $mc + '"]'), 1)
	}
	$modId = [regex]::Match($text, 'modId\s*=\s*"minecraft"')
	if (-not $modId.Success) { throw "no minecraft dependency in mods.toml" }
	$vr = [regex]::Match($text.Substring($modId.Index), 'versionRange\s*=\s*"[^"]*"')
	if (-not $vr.Success) { throw "no versionRange after the minecraft modId" }
	$abs = $modId.Index + $vr.Index
	$replacement = 'versionRange="[' + $mc + ']"'
	return $text.Substring(0, $abs) + $replacement + $text.Substring($abs + $vr.Length)
}

# ------------------------------------------------------------------ self test
if ($SelfTest) {
	$cases = @(
		@('["1.21.11"]', 'fabric', '1.21.11', $true), @('["1.21.11"]', 'fabric', '1.21.8', $false),
		@('["1.21","1.21.1"]', 'fabric', '1.21', $true), @('["1.21","1.21.1"]', 'fabric', '1.21.1', $true),
		@('["1.21","1.21.1"]', 'fabric', '1.21.2', $false),
		@('["26.1.*"]', 'fabric', '26.1', $true), @('["26.1.*"]', 'fabric', '26.1.1', $true),
		@('["26.1.*"]', 'fabric', '26.1.2', $true), @('["26.1.*"]', 'fabric', '26.2', $false),
		@('">=1.20 <=1.20.1"', 'fabric', '1.20.1', $true), @('">=1.20 <=1.20.1"', 'fabric', '1.20.2', $false),
		@('"~1.21.8"', 'fabric', '1.21.8', $true), @('"~1.21.8"', 'fabric', '1.21.9', $true),
		@('"~1.21.6"', 'fabric', '1.21.7', $true),
		@('"*"', 'fabric', '1.21.8', $true),
		@('[1.21.11]', 'forge', '1.21.11', $true), @('[1.21.11]', 'forge', '1.21.8', $false),
		@('[1.20,1.20.1]', 'forge', '1.20.1', $true), @('[1.20,1.20.1]', 'forge', '1.20.2', $false),
		@('[1.21, 1.21.1]', 'forge', '1.21.1', $true), @('[1.21, 1.21.1]', 'forge', '1.21.2', $false),
		@('[26.1, 26.1.2]', 'forge', '26.1.1', $true), @('[26.1, 26.1.2]', 'forge', '26.2', $false),
		@('[1.21.10,1.21.10)', 'forge', '1.21.10', $false),
		@('[1.21.10,1.21.11)', 'forge', '1.21.10', $true),
		@('[1.21.11,1.21.12)', 'forge', '1.21.11', $true),
		@('[26.1,26.2)', 'forge', '26.1.1', $true), @('[26.1.1,26.1.1)', 'forge', '26.1.1', $false)
	)
	$bad = 0
	foreach ($c in $cases) {
		$got = Test-McAdmitted $c[0] $c[2] $c[1]
		if ($got -ne $c[3]) { Write-Host ("FAIL {0} {1} mc={2} expected={3} got={4}" -f $c[0], $c[1], $c[2], $c[3], $got) -ForegroundColor Red; $bad++ }
	}
	# Round-trip: write then read back.
	$fab = '{ "depends": { "fabricloader": ">=0.14", "minecraft": ["1.21.11"] } }'
	$w = Set-DeclaredMc $fab "fabric.mod.json" "1.21.8"
	if (-not (Test-McAdmitted (Get-McSpec $w "fabric.mod.json") "1.21.8" "fabric")) { Write-Host "FAIL fabric round-trip" -ForegroundColor Red; $bad++ }
	$toml = "[[dependencies.baritoe]]`nmodId=`"minecraft`"`nmandatory=true`nversionRange=`"[1.21.11]`"`nordering=`"NONE`"`n"
	$w2 = Set-DeclaredMc $toml "META-INF/mods.toml" "1.21.8"
	if (-not (Test-McAdmitted (Get-McSpec $w2 "META-INF/mods.toml") "1.21.8" "forge")) { Write-Host "FAIL forge round-trip" -ForegroundColor Red; $bad++ }
	if ($w2 -notmatch 'ordering="NONE"') { Write-Host "FAIL forge round-trip damaged following keys" -ForegroundColor Red; $bad++ }
	Write-Host "$(if ($bad -eq 0) { 'self-test PASSED' } else { "self-test FAILED ($bad)" })"
	if ($bad -gt 0) { exit 1 }
	return
}

# ---------------------------------------------------------------- discovery
function Read-Text([string]$path) {
	if (Test-Path -LiteralPath $path) { return [System.IO.File]::ReadAllText($path) }
	return $null
}

function Get-JarEntryText([string]$jar, [string]$entry) {
	$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
	try {
		$e = $zip.GetEntry($entry)
		if (-not $e) { return $null }
		$reader = New-Object System.IO.StreamReader($e.Open())
		try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
	} finally { $zip.Dispose() }
}

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ("wurstb-baritone-fix-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null

function Set-JarEntryText([string]$jar, [string]$entry, [string]$text) {
	$tmp = Join-Path $scratch ([guid]::NewGuid().ToString('N') + ".entry")
	[System.IO.File]::WriteAllText($tmp, $text, $utf8)
	try { & $replaceEntryScript -JarPath $jar -EntryToReplace $entry -ContentFile $tmp -NoBackup | Out-Null }
	finally { if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force } }
}

$trees = @(
	@{ Dir = "versions"; Loader = "forge" },
	@{ Dir = "fabric\versions"; Loader = "fabric" },
	@{ Dir = "neoforge\versions"; Loader = "neoforge" }
)
$entries = @()

foreach ($tree in $trees) {
	$base = Join-Path $ProjectRoot $tree.Dir
	if (-not (Test-Path -LiteralPath $base)) { continue }
	foreach ($proj in Get-ChildItem -LiteralPath $base -Directory) {
		$buildGradle = Join-Path $proj.FullName "build.gradle"
		if (-not (Test-Path -LiteralPath $buildGradle)) { continue }
		$gradleProps = Read-Text (Join-Path $proj.FullName "gradle.properties")
		if (-not $gradleProps) { continue }
		$mcMatch = [regex]::Match($gradleProps, '(?m)^\s*minecraft_version\s*=\s*(.+?)\s*$')
		if (-not $mcMatch.Success) { continue }
		$mc = $mcMatch.Groups[1].Value.Trim()
		if ($Versions.Count -gt 0 -and $mc -notin $Versions) { continue }
		$text = Read-Text $buildGradle

		$maven = [regex]::Match($text, '["'']baritone:([a-z\-]+):([^"'']+)["'']')
		$repoM = [regex]::Match($text, 'name\s*=\s*"BundledBaritone"\s*\r?\n\s*url\s*=\s*uri\("([^"]+)"\)')
		$filesM = [regex]::Match($text, 'files\(\s*["'']([^"'']*baritone[^"'']*\.jar)["'']\s*\)')
		$fromM = [regex]::Match($text, 'from\(\s*["'']([^"'']*baritone[^"'']*\.jar)["'']\s*\)')

		$artifact = $null; $version = $null; $srcJar = $null; $repoRoot = $null; $style = "unresolved"
		if ($maven.Success) { $artifact = $maven.Groups[1].Value; $version = $maven.Groups[2].Value }

		if ($maven.Success -and $repoM.Success) {
			$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $proj.FullName $repoM.Groups[1].Value))
			$candidate = Join-Path $repoRoot ("baritone\{0}\{1}\{0}-{1}.jar" -f $artifact, $version)
			if (Test-Path -LiteralPath $candidate) {
				$srcJar = $candidate; $style = "maven"
			} else {
				# The declared coordinate may be a per-MC copy that does not exist yet
				# (baritone-maven/ is gitignored, so a fresh clone has only the base
				# artifact). Fall back to the base jar; $version keeps the coordinate
				# so the copy can be rebuilt from it.
				$baseVersion = $version -replace '-mc\d[\d.]*$', ''
				$baseJar = Join-Path $repoRoot ("baritone\{0}\{1}\{0}-{1}.jar" -f $artifact, $baseVersion)
				if (Test-Path -LiteralPath $baseJar) { $srcJar = $baseJar; $style = "maven" }
			}
		}
		if (-not $srcJar -and $maven.Success) {
			foreach ($candidate in @(
					(Join-Path $proj.FullName ("{0}-{1}.jar" -f $artifact, $version)),
					(Join-Path $proj.FullName ("libs\{0}-{1}.jar" -f $artifact, $version)),
					(Join-Path $ProjectRoot ("{0}-{1}.jar" -f $artifact, $version)),
					(Join-Path $ProjectRoot ("{0}-{1}.jar" -f $artifact, ($version -replace '-mc\d[\d.]*$', ''))))) {
				if (Test-Path -LiteralPath $candidate) { $srcJar = $candidate; $style = "flatdir"; $repoRoot = $null; break }
			}
		}
		if (-not $srcJar -and $filesM.Success) {
			$srcJar = [System.IO.Path]::GetFullPath((Join-Path $proj.FullName $filesM.Groups[1].Value))
			$artifact = [System.IO.Path]::GetFileNameWithoutExtension($srcJar); $version = $null; $style = "files"
		}
		if (-not $srcJar -and $fromM.Success) {
			$srcJar = [System.IO.Path]::GetFullPath((Join-Path $proj.FullName $fromM.Groups[1].Value))
			$artifact = [System.IO.Path]::GetFileNameWithoutExtension($srcJar); $version = $null; $style = "from"
		}
		if ($srcJar -and -not (Test-Path -LiteralPath $srcJar)) {
			# A hardcoded path may name a per-MC copy that a fresh clone lacks; retry
			# against the base file name in the same directory.
			$dir = Split-Path -Parent $srcJar
			$leaf = Split-Path -Leaf $srcJar
			$baseLeaf = $leaf -replace '-mc\d[\d.]*(\.jar)$', '$1'
			$baseCandidate = Join-Path $dir $baseLeaf
			if ($baseLeaf -ne $leaf -and (Test-Path -LiteralPath $baseCandidate)) { $srcJar = $baseCandidate }
		}
		if ($srcJar -and -not (Test-Path -LiteralPath $srcJar)) { $style = "unresolved" }

		# Loader metadata entry, with a fallback: NeoForge 1.20.x-1.21.2 bundle the
		# *Forge* artifact, which carries META-INF/mods.toml rather than the
		# NeoForge-specific file name.
		$entryCandidates = switch ($tree.Loader) {
			"fabric" { @("fabric.mod.json") }
			"forge" { @("META-INF/mods.toml", "META-INF/neoforge.mods.toml") }
			default { @("META-INF/neoforge.mods.toml", "META-INF/mods.toml") }
		}
		$entryName = $null
		if ($srcJar -and $style -ne "unresolved") {
			foreach ($candidate in $entryCandidates) {
				if (Get-JarEntryText $srcJar $candidate) { $entryName = $candidate; break }
			}
		}

		$entries += @{
			Proj = $proj.FullName; Rel = "$($tree.Dir)\$($proj.Name)"; Mc = $mc; Loader = $tree.Loader
			Style = $style; Artifact = $artifact; Version = $version; SrcJar = $srcJar
			RepoRoot = $repoRoot; EntryName = $entryName; EntryCandidates = $entryCandidates
			BuildGradle = $buildGradle
			FromPath = $(if ($fromM.Success) { $fromM.Groups[1].Value } else { $null })
		}
	}
}

# ---------------------------------------------------------------- apply
$updated = 0; $skipped = 0; $failed = 0; $unresolved = 0
foreach ($e in $entries) {
	if ($e.Style -eq "unresolved") {
		Write-Host ("[{0}] SKIP: could not resolve the bundled Baritone jar" -f $e.Rel) -ForegroundColor DarkYellow
		$unresolved++; continue
	}
	if (-not $e.EntryName) {
		Write-Host ("[{0}] SKIP: no loader metadata found in {1}" -f $e.Rel, (Split-Path -Leaf $e.SrcJar)) -ForegroundColor DarkYellow
		$unresolved++; continue
	}
	try {
		# Decide which artifact this project should depend on. The canonical base
		# jar is never modified, so basing the decision on ITS declaration is stable
		# across runs: a project keeps the shared base artifact if that artifact
		# already admits its MC version, and otherwise gets a private per-MC copy.
		$baseVersion = $e.Version -replace '-mc\d[\d.]*$', ''
		$newVersion = $e.Version
		$targetJar = $e.SrcJar
		if ($e.RepoRoot) {
			$baseJar = Join-Path $e.RepoRoot ("baritone\{0}\{1}\{0}-{1}.jar" -f $e.Artifact, $baseVersion)
			$baseAdmits = (Test-Path -LiteralPath $baseJar) -and
				(Test-McAdmitted (Get-McSpec (Get-JarEntryText $baseJar $e.EntryName) $e.EntryName) $e.Mc $e.Loader)
			if ($baseAdmits) {
				$newVersion = $baseVersion
				$targetJar = $baseJar
			} else {
				$newVersion = "$baseVersion-mc$($e.Mc)"
				$targetJar = Join-Path $e.RepoRoot ("baritone\{0}\{1}\{0}-{1}.jar" -f $e.Artifact, $newVersion)
			}
		} elseif ($e.Style -eq 'flatdir' -and $e.Loader -eq 'forge') {
			# FlatDir artifacts may live in project libs/ rather than the root.
			$baseJar = Join-Path (Split-Path -Parent $e.SrcJar) ("{0}-{1}.jar" -f $e.Artifact, $baseVersion)
			if (-not (Test-Path -LiteralPath $baseJar)) { throw "flatDir base jar is missing: $baseJar" }
			$baseAdmits = Test-McAdmitted (Get-McSpec (Get-JarEntryText $baseJar $e.EntryName) $e.EntryName) $e.Mc $e.Loader
			$newVersion = if ($baseAdmits) { $baseVersion } else { "$baseVersion-mc$($e.Mc)" }
			$targetJar = if ($baseAdmits) { $baseJar } else {
				Join-Path (Split-Path -Parent $baseJar) ("{0}-{1}.jar" -f $e.Artifact, $newVersion)
			}
		}

		$needsCopy = -not (Test-Path -LiteralPath $targetJar)
		if ($needsCopy) {
			throw "missing $targetJar; build a Baritone jar for Minecraft $($e.Mc) instead of copying a different version's binary"
		}

		$declaredNow = Get-McSpec (Get-JarEntryText $targetJar $e.EntryName) $e.EntryName
		$needsDecl = -not (Test-McAdmitted $declaredNow $e.Mc $e.Loader)

		# A stale reference is anything that does not already name the intended
		# version (build script coordinate, hardcoded copy path, jarjar manifest).
		$needsRef = $false
		if ($e.RepoRoot -or ($e.Style -eq 'flatdir' -and $e.Loader -eq 'forge')) {
			$bgNow = [System.IO.File]::ReadAllText($e.BuildGradle)
			if ($bgNow -notmatch [regex]::Escape("baritone:$($e.Artifact):$newVersion")) { $needsRef = $true }
			if ($e.Style -eq 'flatdir' -and $newVersion -ne $baseVersion -and
				$bgNow -notmatch [regex]::Escape('version: "[' + $newVersion + ']"')) { $needsRef = $true }
			$jjNow = Join-Path $e.Proj "src\main\resources\META-INF\jarjar\metadata.json"
			if (Test-Path -LiteralPath $jjNow) {
				$jjText = [System.IO.File]::ReadAllText($jjNow)
				if ($jjText -notmatch [regex]::Escape($e.Artifact + '-' + $newVersion + '.jar"')) { $needsRef = $true }
			}
		}

		if ($WhatIf) {
			if ($needsDecl -or $needsRef) {
				Write-Host ("[{0}] WOULD declare minecraft=[{1}] in {2} -> {3}" -f $e.Rel, $e.Mc, $e.EntryName, (Split-Path -Leaf $targetJar))
				$updated++
			} else { $skipped++ }
			continue
		}

		$changed = $false

		if ($needsDecl) {
			$current = Get-JarEntryText $targetJar $e.EntryName
			Set-JarEntryText $targetJar $e.EntryName (Set-DeclaredMc $current $e.EntryName $e.Mc)
			# Verify what we just wrote before touching any references.
			$check = Get-McSpec (Get-JarEntryText $targetJar $e.EntryName) $e.EntryName
			if (-not (Test-McAdmitted $check $e.Mc $e.Loader)) { throw "post-write verification failed (declares $check)" }
			$changed = $true
		}

		if ($needsRef) {
			$bg = [System.IO.File]::ReadAllText($e.BuildGradle)
			$bg = $bg.Replace("baritone:$($e.Artifact):$($e.Version)", "baritone:$($e.Artifact):$newVersion")
			if ($e.Style -eq 'flatdir' -and $newVersion -ne $baseVersion) {
				$bg = [regex]::Replace($bg, '(?s)(jarJar\(group:\s*"baritone",\s*name:\s*"' + [regex]::Escape($e.Artifact) + '"\s*,\s*version:\s*)"[^"]+"', ('$1"[' + $newVersion + ']"'))
			}
			if ($e.FromPath) {
				$newRel = $e.FromPath -replace [regex]::Escape($e.Version), $newVersion
				$bg = $bg.Replace($e.FromPath, $newRel)
			}
			[System.IO.File]::WriteAllText($e.BuildGradle, $bg, $utf8)
			$changed = $true

			$jjPath = Join-Path $e.Proj "src\main\resources\META-INF\jarjar\metadata.json"
			if (Test-Path -LiteralPath $jjPath) {
				# Text surgery rather than a JSON round-trip: ConvertTo-Json would
				# reformat the whole file and drop the trailing newline, producing a
				# large cosmetic diff. Each replacement targets one field and is a
				# no-op once applied, so re-runs stay clean.
				$jj = [System.IO.File]::ReadAllText($jjPath)
				# No leading quote on the first pattern: "path" reads
				# "META-INF/jarjar/baritone-forge-<ver>.jar", so the filename is
				# preceded by a slash, not a quote. Replacing the longest, most
				# specific form first keeps the later, shorter patterns from
				# rewriting it twice.
				$jj = $jj.Replace($e.Artifact + '-' + $baseVersion + '.jar"', $e.Artifact + '-' + $newVersion + '.jar"')
				$jj = $jj.Replace('"' + $baseVersion + '"', '"' + $newVersion + '"')
				$jj = $jj.Replace('[' + $baseVersion + ',', '[' + $newVersion + ',')
				[System.IO.File]::WriteAllText($jjPath, $jj, $utf8)
			}
		}

		if ($changed) {
			Write-Host ("[{0}] {1} minecraft -> [{2}]  ({3})" -f $e.Rel, $e.EntryName, $e.Mc, (Split-Path -Leaf $targetJar)) -ForegroundColor Green
			$updated++
		} else { $skipped++ }
	} catch {
		Write-Host ("[{0}] FAIL: {1}" -f $e.Rel, $_.Exception.Message) -ForegroundColor Red
		$failed++
	}
}

if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
Write-Host ""
Write-Host "updated: $updated  already-correct: $skipped  unresolved: $unresolved  failed: $failed"
if ($failed -gt 0) { exit 1 }
# Unresolved entries mean the bundled jar could not be found at all - on a fresh
# clone that is a real problem (the artifact is missing, not already correct), so
# do not report it as a clean run.
if ($unresolved -gt 0) {
	Write-Host "  $unresolved project(s) could not be resolved; their bundled Baritone is missing or unresolvable." -ForegroundColor DarkYellow
	if (-not $AllowUnresolved) { exit 1 }
	Write-Host "  (-AllowUnresolved set: continuing)" -ForegroundColor DarkYellow
}
