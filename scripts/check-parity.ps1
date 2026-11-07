<#
.SYNOPSIS
    v1.5.1 全版本行为对齐闸门：注册表 + 渲染入口。

.DESCRIPTION
    以 fabric 1.20.1 为基线，逐个核对 66 个 v1.5.1 工程的
      * HackList / OtfList / CmdList 注册项
      * hud2/mods 与 hud2/nanovg 的类集合
      * HUD / ClickGUI 的 NanoVG 渲染入口
    与基线不一致、且没有在 scripts/parity-allowlist.json 里登记的差异一律报错。

    版本能力（重锤/长矛/属性交换、1.21.6+ 的 WurstOptions 与 NanoVgBlitter、
    1.21.11 三端统一的 BetterTab/BetterTooltips/NoStatusEffects）必须先在白名单里
    登记原因，否则 CI 会拦下。

.PARAMETER Check
    任何漂移都以退出码 1 结束（CI 用）。

.PARAMETER Quiet
    只输出漂移与总结，不打印逐工程 OK。

.EXAMPLE
    pwsh -File scripts/check-parity.ps1
    pwsh -File scripts/check-parity.ps1 -Check
#>
param(
	[switch] $Check,
	[switch] $Quiet
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$allowPath = Join-Path (Join-Path $root "scripts") "parity-allowlist.json"

if(-not (Test-Path -LiteralPath $allowPath)) {
	throw "missing allowlist: $allowPath"
}

$allow = Get-Content -LiteralPath $allowPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Join-Parts {
	param([string] $Base, [string[]] $Parts)
	return [System.IO.Path]::Combine([string[]](@($Base) + $Parts))
}

function Get-Fields {
	param([string] $Path)
	if(-not (Test-Path -LiteralPath $Path)) { return @() }
	$names = New-Object System.Collections.Generic.List[string]
	foreach($hit in (Select-String -LiteralPath $Path -Pattern 'public final\s+[\w\.]+\s+(\w+)\s*=' -AllMatches)) {
		foreach($m in $hit.Matches) { [void]$names.Add($m.Groups[1].Value) }
	}
	return $names.ToArray()
}

function Get-Basenames {
	param([string] $Dir)
	if(-not (Test-Path -LiteralPath $Dir)) { return @() }
	return @(Get-ChildItem -LiteralPath $Dir -File -Filter *.java | ForEach-Object { $_.BaseName })
}

function Get-AllowEntry {
	param([string] $Mc)
	$prop = $allow.versions.PSObject.Properties[$Mc]
	if($prop) { return $prop.Value }
	return $null
}

function Get-Added {
	param($Entry, [string] $Name)
	if($null -eq $Entry) { return @() }
	$prop = $Entry.PSObject.Properties[$Name]
	if($prop -and $null -ne $prop.Value) { return @($prop.Value) }
	return @()
}

# ---------------------------------------------------------------- projects

$projects = New-Object System.Collections.Generic.List[object]

function Add-Project {
	param([string] $Name, [string] $Mc, [string] $Path, [string] $Loader)
	[void]$projects.Add([pscustomobject]@{ Name = $Name; Mc = $Mc; Path = $Path; Loader = $Loader })
}

$fabricRoot = Join-Path $root "fabric"
Add-Project "fabric" "1.20.1" $fabricRoot "fabric"
foreach($d in (Get-ChildItem -LiteralPath (Join-Path $fabricRoot "versions") -Directory)) {
	Add-Project ("fabric/" + $d.Name) $d.Name $d.FullName "fabric"
}

$neoRoot = Join-Path $root "neoforge"
Add-Project "neoforge" "1.20.1" $neoRoot "neoforge"
foreach($d in (Get-ChildItem -LiteralPath (Join-Path $neoRoot "versions") -Directory)) {
	Add-Project ("neoforge/" + $d.Name) $d.Name $d.FullName "neoforge"
}

foreach($d in (Get-ChildItem -LiteralPath (Join-Path $root "versions") -Directory)) {
	Add-Project ("versions/" + $d.Name) $d.Name $d.FullName "forge"
}

$baselineName = [string]$allow.baseline
$baseline = $projects | Where-Object { $_.Name -eq $baselineName } | Select-Object -First 1
if(-not $baseline) { throw "baseline project not found: $baselineName" }

$baseJava = Join-Parts $baseline.Path @("src", "main", "java", "net", "wurstclient")
$baseHacks = Get-Fields (Join-Parts $baseJava @("hack", "HackList.java"))
$baseOtfs = Get-Fields (Join-Parts $baseJava @("other_feature", "OtfList.java"))
$baseCmds = Get-Fields (Join-Parts $baseJava @("command", "CmdList.java"))
$baseMods = Get-Basenames (Join-Parts $baseJava @("hud2", "mods"))
$baseNvg = Get-Basenames (Join-Parts $baseJava @("hud2", "nanovg"))

if($baseHacks.Count -eq 0) { throw "baseline HackList is empty: $($baseline.Path)" }

$drift = New-Object System.Collections.Generic.List[object]

function Test-Set {
	param([string] $Project, [string] $Dimension, [string[]] $Expected, [string[]] $Actual)
	$missing = @($Expected | Where-Object { $Actual -notcontains $_ })
	$extra = @($Actual | Where-Object { $Expected -notcontains $_ })
	if($missing.Count -eq 0 -and $extra.Count -eq 0) { return }
	[void]$drift.Add([pscustomobject]@{
		Project = $Project
		Dimension = $Dimension
		Missing = ($missing -join ",")
		Extra = ($extra -join ",")
	})
}

$renderDrift = New-Object System.Collections.Generic.List[object]

foreach($p in $projects) {
	if($p.Name -eq $baseline.Name) { continue }

	$java = Join-Parts $p.Path @("src", "main", "java", "net", "wurstclient")
	$entry = Get-AllowEntry $p.Mc

	$expectedHacks = @(@($baseHacks) + @(Get-Added $entry "hacks") | Sort-Object -Unique)
	$expectedOtfs = @(@($baseOtfs) + @(Get-Added $entry "otfs") | Sort-Object -Unique)
	$expectedNvg = @(@($baseNvg) + @(Get-Added $entry "nanovg") | Sort-Object -Unique)

	Test-Set $p.Name "hacks" $expectedHacks (Get-Fields (Join-Parts $java @("hack", "HackList.java")))
	Test-Set $p.Name "otfs" $expectedOtfs (Get-Fields (Join-Parts $java @("other_feature", "OtfList.java")))
	Test-Set $p.Name "cmds" $baseCmds (Get-Fields (Join-Parts $java @("command", "CmdList.java")))
	Test-Set $p.Name "hudMods" $baseMods (Get-Basenames (Join-Parts $java @("hud2", "mods")))
	Test-Set $p.Name "nanoVG" $expectedNvg (Get-Basenames (Join-Parts $java @("hud2", "nanovg")))

	# 渲染入口：HUD 与 ClickGUI 必须走 NanoVG。
	$nvgDir = Join-Parts $java @("hud2", "nanovg")
	$nvgContext = Join-Parts $nvgDir @("NanoVGContext.java")
	$nvgUi = Join-Parts $nvgDir @("NanoVGUI.java")
	if(-not (Test-Path -LiteralPath $nvgContext)) {
		[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "missing hud2/nanovg/NanoVGContext.java" })
	}
	if(-not (Test-Path -LiteralPath $nvgUi)) {
		[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "missing hud2/nanovg/NanoVGUI.java" })
	}

	$hudManager = Join-Parts $java @("hud2", "HudManager.java")
	if(-not (Select-String -LiteralPath $hudManager -Pattern 'NanoVGContext' -Quiet)) {
		[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "HudManager does not use NanoVGContext" })
	}

	$clickGui = Join-Parts $java @("clickgui2", "ClickGuiScreen.java")
	if(-not (Select-String -LiteralPath $clickGui -Pattern 'NanoVGUI\.pass' -Quiet)) {
		[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "ClickGuiScreen does not call NanoVGUI.pass" })
	}

	# 1.20.1 fabric 的 NanoVG 接入面：HUD、ClickGUI、HUD 编辑器、标题界面。
	foreach($relFile in @("gui/title/WurstTitleMenu.java",
		"gui/ButtonStyleRenderer.java", "gui/MainMenuBackground.java",
		"hud/IngameHUD.java")) {
		$f = Join-Parts $java ($relFile -split "/")
		if(-not (Test-Path -LiteralPath $f)) {
			[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "missing $relFile" })
		} elseif(-not (Select-String -LiteralPath $f -Pattern 'NanoVG' -Quiet)) {
			[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "$relFile is not on NanoVG" })
		}
	}

	# 活动的 GUI 代码不允许再走原版 GuiGraphics 绘制（圆角必须走矢量实现）。
	foreach($guiDir in @("clickgui2", "hud2")) {
		$dirPath = Join-Parts $java @($guiDir)
		if(-not (Test-Path -LiteralPath $dirPath)) { continue }
		$vanilla = @(Get-ChildItem -LiteralPath $dirPath -Recurse -File -Filter *.java |
			Where-Object { $_.FullName -notmatch '\.disabled' } |
			Select-String -Pattern '\bgraphics\.(fill|blit|drawString|drawCenteredString)\b' -List)
		if($vanilla.Count -gt 0) {
			[void]$renderDrift.Add([pscustomobject]@{ Project = $p.Name; Check = "$guiDir has $($vanilla.Count) file(s) drawing via GuiGraphics" })
		}
	}
}

# ---------------------------------------------------------------- output

$total = $drift.Count + $renderDrift.Count

if(-not $Quiet) {
	Write-Host ("baseline: {0} ({1} hacks, {2} otfs, {3} cmds, {4} hud mods, {5} nanovg files)" -f `
		$baselineName, $baseHacks.Count, $baseOtfs.Count, $baseCmds.Count, $baseMods.Count, $baseNvg.Count)
	Write-Host ("projects checked: {0} (root Forge 1.20.1 / v1.6.0 excluded)" -f ($projects.Count - 1))
}

foreach($d in $drift) {
	Write-Host ("DRIFT {0} [{1}] missing=[{2}] extra=[{3}]" -f $d.Project, $d.Dimension, $d.Missing, $d.Extra)
}
foreach($d in $renderDrift) {
	Write-Host ("DRIFT {0} [render] {1}" -f $d.Project, $d.Check)
}

if($total -eq 0) {
	Write-Host "parity ok: all v1.5.1 projects match the baseline plus the allowlist."
	exit 0
}

Write-Host ("parity drift: {0} item(s)." -f $total)
if($Check) { exit 1 }
exit 0
