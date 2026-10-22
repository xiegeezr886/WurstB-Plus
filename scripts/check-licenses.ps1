#requires -Version 5.1
<#
.SYNOPSIS
  Verify that every project's META-INF/licenses/ covers the third-party
  components it actually bundles.

.DESCRIPTION
  Sources of truth:
    * build.gradle declares which dependencies end up in the artifact
      (Fabric `include`, Forge `allJar`, NeoForge `jarJar`);
    * the bundled Baritone jar tells us whether nether-pathfinder is
      nested (META-INF/jars/nether-pathfinder-*.jar) or shaded
      (dev/babbaj/pathfinder/*.class).

  For each component that is distributed, require:
    * src/main/resources/META-INF/licenses/<artifact>/ exists;
    * ATTRIBUTION.txt exists inside it;
    * a licence text file exists too, except for nether-pathfinder
      (upstream declares no licence, which is recorded as a warning).

  Any missing item exits non-zero so CI can gate on it.

.NOTES
  Runs on Windows PowerShell 5.1 and pwsh 7 (the latter on ubuntu CI).
#>
param(
	[switch]$Quiet
)

$ErrorActionPreference = 'Stop'
try {
	Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
} catch {
	# pwsh 7 already ships ZipFile.
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

function Join-RepoPath([string]$base, [string[]]$parts)
{
	$path = $base
	foreach($part in $parts)
	{
		$path = Join-Path $path $part
	}
	return $path
}

function Get-ProjectPaths
{
	$projects = New-Object System.Collections.Generic.List[string]
	$projects.Add('.')
	$projects.Add('fabric')
	$projects.Add('neoforge')

	foreach($parent in @('versions', 'fabric/versions', 'neoforge/versions'))
	{
		$parentPath = Join-RepoPath $repoRoot ($parent -split '/')
		if(Test-Path -LiteralPath $parentPath)
		{
			Get-ChildItem -LiteralPath $parentPath -Directory | ForEach-Object {
				$projects.Add($_.FullName.Substring($repoRoot.Length + 1))
			}
		}
	}

	return $projects
}

function Get-GradleProperties([string]$projectPath)
{
	$props = @{}
	$file = Join-Path $projectPath 'gradle.properties'
	if(Test-Path -LiteralPath $file)
	{
		foreach($line in Get-Content -LiteralPath $file)
		{
			if($line -match '=' -and $line -notmatch '^\s*#')
			{
				$key, $value = $line -split '=', 2
				$props[$key.Trim()] = $value.Trim()
			}
		}
	}
	return $props
}

function Resolve-BaritoneJar([string]$projectPath, [string]$buildText, $props)
{
	if($buildText -match 'libs/(baritone[^"''\s]+\.jar)')
	{
		$candidate = Join-RepoPath $projectPath @('libs', $Matches[1])
		if(Test-Path -LiteralPath $candidate)
		{
			return $candidate
		}
	}

	if($buildText -notmatch 'baritone:(baritone-[a-z0-9-]+):([^"''\s\)]+)')
	{
		return $null
	}

	$artifact = $Matches[1]
	$version = $Matches[2]
	if($version -match '\$\{project\.([a-zA-Z_]+)\}')
	{
		$version = $props[$Matches[1]]
	}
	if([string]::IsNullOrWhiteSpace($version))
	{
		return $null
	}

	$file = "$artifact-$version.jar"
	$repositories = New-Object System.Collections.Generic.List[string]

	# Honour the repository the build actually declares (relative to the project).
	if($buildText -match 'url\s*=\s*uri\("([^"]*baritone-maven[^"]*)"\)')
	{
		$repositories.Add((Join-Path $projectPath $Matches[1]))
	}
	$repositories.Add((Join-RepoPath $repoRoot @('baritone-maven')))
	$repositories.Add((Join-RepoPath $repoRoot @('fabric', 'baritone-maven')))

	$candidates = @(
		(Join-RepoPath $projectPath @('libs', $file)),
		(Join-RepoPath $projectPath @($file)),
		(Join-RepoPath $repoRoot @($file))
	)
	foreach($repository in $repositories)
	{
		$candidates += (Join-RepoPath $repository @('baritone', $artifact, $version, $file))
	}

	foreach($candidate in $candidates)
	{
		if(Test-Path -LiteralPath $candidate)
		{
			return $candidate
		}
	}
	return $null
}

function Test-BaritoneEmbedsNether([string]$jarPath)
{
	$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		foreach($entry in $zip.Entries)
		{
			# Some jars were built on Windows and use backslashes in entry names.
			$name = $entry.FullName.Replace('\', '/')
			if($name -match '(?i)^META-INF/jars/nether-pathfinder' -or
				$name -match '(?i)^dev/babbaj/pathfinder/.*\.class$')
			{
				return $true
			}
		}
	}
	finally
	{
		$zip.Dispose()
	}
	return $false
}

function Get-ExpectedComponents([string]$projectPath)
{
	$buildFile = Join-Path $projectPath 'build.gradle'
	$buildText = Get-Content -Raw -LiteralPath $buildFile
	$props = Get-GradleProperties $projectPath
	$components = New-Object System.Collections.Generic.List[string]

	if($buildText -match 'Java-WebSocket')
	{
		$components.Add('Java-WebSocket')
	}
	if($buildText -match 'netty-codec-socks')
	{
		$components.Add('netty-codec-socks')
	}
	if($buildText -match 'netty-handler-proxy')
	{
		$components.Add('netty-handler-proxy')
	}
	if($buildText -match 'baritone:(baritone-[a-z0-9-]+):')
	{
		$components.Add($Matches[1])
	}

	# MixinExtras is distributed only when jarJar'd; Fabric's compileOnly does not count.
	if($buildText -match '(?s)jarJar[^;]{0,200}mixinextras')
	{
		$components.Add('mixinextras-forge')
	}

	$baritoneJar = Resolve-BaritoneJar $projectPath $buildText $props
	if($baritoneJar)
	{
		if(Test-BaritoneEmbedsNether $baritoneJar)
		{
			$components.Add('nether-pathfinder')
		}
	}
	else
	{
		$warnings.Add("$projectPath : cannot resolve bundled Baritone jar; nether-pathfinder check skipped")
	}

	return $components
}

function Test-LicenseComponent([string]$projectPath, [string]$component)
{
	if($projectPath -eq '.')
	{
		$base = Join-RepoPath $repoRoot @('src', 'main', 'resources', 'META-INF', 'licenses')
	}
	else
	{
		$base = Join-RepoPath $projectPath @('src', 'main', 'resources', 'META-INF', 'licenses')
	}

	$componentDir = Join-Path $base $component
	if(!(Test-Path -LiteralPath $componentDir))
	{
		$failures.Add("$projectPath : missing META-INF/licenses/$component/")
		return
	}

	$attribution = Join-Path $componentDir 'ATTRIBUTION.txt'
	if(!(Test-Path -LiteralPath $attribution))
	{
		$failures.Add("$projectPath : $component/ATTRIBUTION.txt missing")
	}

	if($component -ne 'nether-pathfinder')
	{
		$texts = Get-ChildItem -LiteralPath $componentDir -File |
			Where-Object { $_.Name -ne 'ATTRIBUTION.txt' }
		if($texts.Count -eq 0)
		{
			$failures.Add("$projectPath : $component has no licence text")
		}
	}
}

$projects = Get-ProjectPaths
foreach($project in $projects)
{
	$projectPath = if($project -eq '.') { $repoRoot } else { Join-RepoPath $repoRoot ($project -split '[\\/]') }
	$components = Get-ExpectedComponents $projectPath

	foreach($component in ($components | Sort-Object -Unique))
	{
		Test-LicenseComponent $project $component
	}

	if(!$Quiet)
	{
		Write-Host ("{0,-28} {1}" -f $project, (($components | Sort-Object -Unique) -join ', '))
	}
}

foreach($warning in $warnings)
{
	Write-Host "WARN  $warning"
}

if($failures.Count -gt 0)
{
	Write-Host ''
	foreach($failure in $failures)
	{
		Write-Host "FAIL  $failure"
	}
	Write-Host ''
	Write-Host "license check failed: $($failures.Count) problem(s) across $($projects.Count) projects"
	exit 1
}

Write-Host ''
Write-Host "license check passed: $($projects.Count) projects, all bundled dependencies declared"
if($warnings.Count -gt 0)
{
	Write-Host "($($warnings.Count) warning(s))"
}
exit 0
