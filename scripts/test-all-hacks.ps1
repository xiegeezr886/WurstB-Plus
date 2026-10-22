#requires -Version 5.1
<#
.SYNOPSIS
  Launch one Fabric project per MC version, enable every hack through the
  TestControlServer, let it run, and record enabled count + recent errors.

  Requires the TestControlServer (already built into all projects). This
  script only creates the per-project test-control.properties, launches
  runClient, drives the HTTP API, then closes the client.

.PARAMETER StartIndex
  First target index (0-based) in the 23-version list.

.PARAMETER Count
  Number of targets to run from StartIndex.

.PARAMETER SettleSeconds
  Seconds to keep all hacks enabled before collecting results.

.PARAMETER Results
  TSV output path.
#>
param(
	[int]$StartIndex = 0,
	[int]$Count = 23,
	[int]$PortBase = 26100,
	[int]$SettleSeconds = 30,
	[int]$WorldSettleSeconds = 8,
	[int]$BootTimeoutSeconds = 180,
	[switch]$Online,
	[string]$Results = "download\all-hacks-results.tsv"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$token = 'allhackstest'

$targets = @(
	@{ Project = 'fabric'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.20.2'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.20.3'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.20.4'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.20.5'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.20.6'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.1'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.2'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.3'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.4'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.5'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.6'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.7'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.8'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.9'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.10'; World = 'xray-test' },
	@{ Project = 'fabric\versions\1.21.11'; World = 'xray-test' },
	@{ Project = 'fabric\versions\26.1'; World = 'xray-test' },
	@{ Project = 'fabric\versions\26.1.1'; World = 'xray-test' },
	@{ Project = 'fabric\versions\26.1.2'; World = 'xray-test' },
	@{ Project = 'fabric\versions\26.2'; World = 'xray-test' },
	@{ Project = 'fabric\versions\26.3'; World = 'xray-test' }
)

$resultsPath = Join-Path $repoRoot $Results
$resultsDir = Split-Path $resultsPath -Parent
if(!(Test-Path $resultsDir))
{
	New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
}
if(!(Test-Path $resultsPath))
{
	"project`tstatus`tenabled`tfailed`terrors`tseconds" |
		Out-File -FilePath $resultsPath -Encoding utf8
}

function Wait-ForPort([int]$port, [int]$timeoutSeconds, $launcher)
{
	$deadline = (Get-Date).AddSeconds($timeoutSeconds)
	while((Get-Date) -lt $deadline)
	{
		if($launcher -and $launcher.HasExited -and $launcher.ExitCode -ne 0)
		{
			return $false
		}
		try
		{
			$r = Invoke-RestMethod -Uri "http://127.0.0.1:$port/ping" `
				-Headers @{'X-Wurst-Token' = $token} -TimeoutSec 2
			if($r -eq 'ok')
			{
				return $true
			}
		}catch
		{
		}
		Start-Sleep -Seconds 2
	}
	return $false
}

function Read-SharedText([string]$path)
{
	try
	{
		$stream = [IO.File]::Open($path, [IO.FileMode]::Open,
			[IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
		try
		{
			$reader = New-Object IO.StreamReader($stream,
				[Text.Encoding]::UTF8)
			return $reader.ReadToEnd()
		}finally
		{
			$reader.Dispose()
			$stream.Dispose()
		}
	}catch
	{
		return ""
	}
}

function Wait-ForWorld([string]$logPath, [int]$timeoutSeconds,
	[long]$offset)
{
	$deadline = (Get-Date).AddSeconds($timeoutSeconds)
	while((Get-Date) -lt $deadline)
	{
		if(Test-Path $logPath)
		{
			$length = (Get-Item $logPath).Length
			$from = if($length -lt $offset) { 0 } else { $offset }
			$text = Read-SharedText $logPath
			if($from -lt $text.Length)
			{
				$text = $text.Substring([int]$from)
			}else
			{
				$text = ""
			}
			# "logged in with entity id" is locale-independent (the English
			# "joined the game" is localized, e.g. to "加入了游戏").
			if($text -match 'logged in with entity id|joined the game|加入了游戏|Teleported')
			{
				return $true
			}
		}
		Start-Sleep -Seconds 2
	}
	return $false
}

function Test-FatalLog([string]$logPath)
{
	if(!(Test-Path $logPath))
	{
		return $null
	}
	$text = Read-SharedText $logPath
	$patterns = 'FATAL|Mod Loading has failed|Mixin apply failed|' +
		'MixinTransformerError|InvalidInjectionException|' +
		'InvalidAccessorException|Exception in thread "main"'
	$match = [regex]::Match($text, $patterns)
	if($match.Success)
	{
		return $match.Value
	}
	return $null
}

function Close-TestClient([int]$port)
{
	try
	{
		$connection = Get-NetTCPConnection -LocalPort $port -State Listen `
			-ErrorAction SilentlyContinue | Select-Object -First 1
		if($connection)
		{
			$game = Get-Process -Id $connection.OwningProcess `
				-ErrorAction SilentlyContinue
			if($game)
			{
				[void]$game.CloseMainWindow()
				if(!$game.WaitForExit(15000))
				{
					Stop-Process -Id $game.Id -Force
				}
			}
		}
	}catch
	{
	}
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportPath = Join-Path $repoRoot ".test\report-all-hacks-$stamp.txt"
$reportDir = Split-Path $reportPath -Parent
if(!(Test-Path $reportDir))
{
	New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}
$summary = New-Object System.Collections.Generic.List[string]

$end = [Math]::Min($targets.Count, $StartIndex + $Count)
for($i = $StartIndex; $i -lt $end; $i++)
{
	$target = $targets[$i]
	$project = Join-Path $repoRoot $target.Project
	$port = $PortBase + $i + 1
	$startedAt = Get-Date
	$properties = Join-Path $project 'run\wurst\test-control.properties'
	$propertiesDir = Split-Path $properties -Parent
	if(!(Test-Path $propertiesDir))
	{
		New-Item -ItemType Directory -Path $propertiesDir -Force | Out-Null
	}
	"port=$port`ntoken=$token`n" |
		Out-File -FilePath $properties -Encoding ascii

	$logPath = Join-Path $project 'run\logs\latest.log'
	$logOffset = if(Test-Path $logPath) { (Get-Item $logPath).Length } else { 0 }
	$crashDir = Join-Path $project 'run\crash-reports'
	$oldCrashes = @()
	if(Test-Path $crashDir)
	{
		$oldCrashes = @(Get-ChildItem $crashDir -File | ForEach-Object {$_.Name})
	}

	$jdk = if($target.Project -match '(^|[\\/])26\.')
	{
		Join-Path $env:USERPROFILE '.jdks\jdk25.0.4_10'
	}else
	{
		Join-Path $env:USERPROFILE '.jdks\jdk21.0.12_12'
	}
	if(Test-Path $jdk)
	{
		$env:JAVA_HOME = $jdk
	}

	$stdout = Join-Path $env:TEMP ("allhacks-" + ($i + 1) + ".out.log")
	$stderr = Join-Path $env:TEMP ("allhacks-" + ($i + 1) + ".err.log")
	$spLog = Join-Path $env:TEMP ("allhacks-sp-" + ($i + 1) + ".json")
	$bat = Join-Path $env:TEMP ("allhacks-" + ($i + 1) + ".bat")
	$gradlew = Join-Path $project 'gradlew.bat'
	$offlineArg = if($Online) { '' } else { ' --offline' }
	$batLines = @(
		'@echo off',
		('cd /d "' + $project + '"'),
		('call "' + $gradlew + '" runClient "-PspWorld=' +
			$target.World + '" "-PspLog=' + $spLog +
			'"' + $offlineArg + ' --console=plain')
	)
	[IO.File]::WriteAllLines($bat, $batLines,
		(New-Object Text.UTF8Encoding($false)))
	$process = Start-Process -FilePath 'cmd.exe' `
		-ArgumentList '/c', ('"' + $bat + '"') `
		-WorkingDirectory $project -WindowStyle Hidden -PassThru `
		-RedirectStandardOutput $stdout -RedirectStandardError $stderr

	$status = 'ERROR'
	$enabled = 0
	$failed = 0
	$errors = 0
	$note = ''
	try
	{
		if(!(Wait-ForPort $port $BootTimeoutSeconds $process))
		{
			$note = 'test control port did not start; ' +
				((Get-Content $stderr -Tail 1 -ErrorAction SilentlyContinue) -join '')
		}else
		{
			# Clear hacks persisted by an earlier run so the world starts with
			# a clean state (a leftover PacketLogger can flood the join).
			try
			{
				Invoke-RestMethod -Method Post `
					-Uri "http://127.0.0.1:$port/hacks/disableAll" `
					-Headers @{'X-Wurst-Token' = $token} -TimeoutSec 30 `
					| Out-Null
			}catch
			{
			}

			$worldReady = Wait-ForWorld $logPath $BootTimeoutSeconds $logOffset
			if($worldReady)
			{
				# Let terrain/chunk loading finish so enableAll doesn't fight the
				# render thread while the world is still being set up.
				Start-Sleep -Seconds $WorldSettleSeconds
				$enableResult = Invoke-RestMethod -Method Post `
					-Uri "http://127.0.0.1:$port/hacks/enableAll" `
					-Headers @{'X-Wurst-Token' = $token} -TimeoutSec 180
				$failed = $enableResult.failed
				Start-Sleep -Seconds $SettleSeconds

				$hacks = Invoke-RestMethod -Uri "http://127.0.0.1:$port/hacks" `
					-Headers @{'X-Wurst-Token' = $token} -TimeoutSec 10
				$enabled = @($hacks | Where-Object {$_.enabled}).Count
				$logs = Invoke-RestMethod `
					-Uri "http://127.0.0.1:$port/logs?lines=1000&errorsOnly=1" `
					-Headers @{'X-Wurst-Token' = $token} -TimeoutSec 10
				$errorLines = @($logs -split "`n" | Where-Object {$_.Trim()})
				$errors = $errorLines.Count
			}

			$newCrashes = @()
			if(Test-Path $crashDir)
			{
				$newCrashes = @(Get-ChildItem $crashDir -File |
					Where-Object {$oldCrashes -notcontains $_.Name} |
					ForEach-Object {$_.Name})
			}
			$fatal = Test-FatalLog $logPath

			if(!$worldReady)
			{
				$status = 'TIMEOUT'
				$note = 'world not loaded; hacks were NOT enabled'
			}elseif($newCrashes.Count -gt 0)
			{
				$status = 'FAIL'
				$note = 'new crash report: ' + ($newCrashes -join ',')
			}elseif($fatal)
			{
				$status = 'FAIL'
				$note = 'fatal log: ' + $fatal
			}elseif($errors -gt 0)
			{
				$status = 'FAIL'
				$note = 'error log lines: ' + $errors
			}else
			{
				$status = 'PASS'
				$note = 'world loaded; all hacks enabled'
			}
		}
	}catch
	{
		$status = 'ERROR'
		$note = $_.Exception.Message
	}finally
	{
		Close-TestClient $port
		Start-Sleep -Seconds 2
		Remove-Item -LiteralPath $properties -Force -ErrorAction SilentlyContinue
		Remove-Item -LiteralPath $bat -Force -ErrorAction SilentlyContinue
		$process.WaitForExit(30000) | Out-Null
	}

	$seconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds)
	$line = "$($target.Project)`t$status`t$enabled`t$failed`t$errors`t$seconds`t$note"
	$line | Out-File -FilePath $resultsPath -Encoding utf8 -Append
	$line | Out-File -FilePath (Join-Path $repoRoot '.test\all-hacks-progress.txt') `
		-Encoding utf8 -Append
	$summary.Add($line)
	Write-Host ("[{0}] {1} status={2} enabled={3} failed={4} errors={5} ({6}s) {7}" -f `
		($i + 1), $target.Project, $status, $enabled, $failed, $errors,
		$seconds, $note)
}

$report = New-Object System.Collections.Generic.List[string]
$report.Add("All-hacks verification, " + $stamp)
$report.Add("Targets: " + $StartIndex + ".." + ($end - 1) +
	" of " + $targets.Count)
$report.Add("Settle: " + $SettleSeconds + "s, boot timeout: " +
	$BootTimeoutSeconds + "s")
$report.Add("")
$report.Add("project`tstatus`tenabled`tfailed`terrors`tseconds`tnote")
foreach($line in $summary)
{
	$report.Add($line)
}
$report.Add("")
$counts = $summary | ForEach-Object {($_ -split "`t")[1]} |
	Group-Object | Sort-Object Name
foreach($group in $counts)
{
	$report.Add(($group.Name + ": " + $group.Count))
}
[IO.File]::WriteAllLines($reportPath, $report,
	(New-Object Text.UTF8Encoding($false)))
Write-Host ("Report: " + $reportPath)
