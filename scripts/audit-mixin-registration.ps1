#Requires -Version 5.1
<#
.SYNOPSIS
    Audits mixin registration across all projects and generates a work list.

.DESCRIPTION
    For every project this compares the mixin classes listed in its *.mixins.json
    against the @Mixin-annotated .java files that actually exist in its mixin
    package, and reports two failure modes:

      LISTED BUT NO FILE   the config names a mixin whose class does not exist.
                           Mixin treats this as a hard error -> startup failure.
      FILE BUT NOT LISTED  a real mixin class that nothing registers. It never
                           loads, so whatever it was written to do silently does
                           not happen. This is the failure mode behind
                           "feature X just does nothing" reports.

    Unregistered files are then classified by how much is known about them:

      A  same path exists in the root project AND is registered there.
         Strongest evidence it is a genuine gap.
      B  exists in the root project but is not registered there either.
         Needs a look, but the root project is not a proof of intent.
      C  not present in the root project at all. Version-specific adaptation;
         judging these requires checking the injection point against the real
         jar (see scripts/audit-mixin-injections.py).

    Nothing is modified. This only reads and reports.

.PARAMETER OutputPath
    Markdown report path. Default: docs/MIXIN-REGISTRATION-GAPS.md

.PARAMETER TsvPath
    Machine-readable output for batch work. Default: _recon/mixin-gaps.tsv

.EXAMPLE
    pwsh -File scripts/audit-mixin-registration.ps1
#>
[CmdletBinding()]
param(
    [string]$OutputPath = 'docs/MIXIN-REGISTRATION-GAPS.md',
    [string]$TsvPath = '_recon/mixin-gaps.tsv',
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 注意：本脚本注释不含引号字符。PowerShell 把 ASCII 双引号与中文全角引号
# 都当作字符串定界符，注释里出现会直接解析失败。

if (-not $RepoRoot) {
    if ($PSScriptRoot -and (Test-Path (Join-Path (Split-Path -Parent $PSScriptRoot) 'settings.gradle'))) {
        $RepoRoot = Split-Path -Parent $PSScriptRoot
    }
    else {
        $probe = (Get-Location).Path
        while ($probe -and -not (Test-Path (Join-Path $probe 'settings.gradle'))) {
            $parent = Split-Path -Parent $probe
            if ($parent -eq $probe) { break }
            $probe = $parent
        }
        if (-not $probe) { throw 'Cannot locate the repository root.' }
        $RepoRoot = $probe
    }
}
Set-Location $RepoRoot

function Get-ProjectRoots {
    $list = New-Object System.Collections.Generic.List[string]
    $list.Add('.')
    foreach ($d in @('fabric', 'neoforge')) { if (Test-Path $d) { $list.Add($d) } }
    foreach ($parent in @('versions', 'fabric\versions', 'neoforge\versions')) {
        if (Test-Path $parent) {
            foreach ($dir in (Get-ChildItem $parent -Directory | Sort-Object Name)) {
                $list.Add($dir.FullName.Substring($RepoRoot.Length + 1))
            }
        }
    }
    return $list
}

function Get-ProjectMixins([string]$projRoot) {
    $full = if ($projRoot -eq '.') { $RepoRoot } else { Join-Path $RepoRoot $projRoot }
    $pkgDir = Join-Path $full 'src\main\java\net\wurstclient\mixin'
    $resDir = Join-Path $full 'src\main\resources'

    $actual = @{}
    if (Test-Path $pkgDir) {
        $abs = (Resolve-Path $pkgDir).Path
        foreach ($f in (Get-ChildItem $abs -Recurse -File -Filter *.java)) {
            $rel = $f.FullName.Substring($abs.Length + 1)
            $rel = $rel.Substring(0, $rel.Length - 5)
            # 关键：配置里用 / 分隔子包，而 Windows 路径是 \。不归一化就会把
            # freecam/CameraMixin 与 freecam\CameraMixin 当成两个不同的名字，
            # 于是子包混入在两个方向上都会被误判（登记了说没文件、有文件说没登记）。
            $rel = $rel.Replace('\', '/')
            if ($rel -match 'WurstMixinConfigPlugin') { continue }
            $isMixin = [bool](Select-String -Path $f.FullName -Pattern '@Mixin\(' -Quiet)
            $actual[$rel] = [PSCustomObject]@{ IsMixin = $isMixin; Path = $f.FullName }
        }
    }

    $listed = @{}
    if (Test-Path $resDir) {
        foreach ($j in (Get-ChildItem $resDir -Recurse -File -Filter '*.mixins.json' -ErrorAction SilentlyContinue)) {
            try { $cfg = [System.IO.File]::ReadAllText($j.FullName, [System.Text.Encoding]::UTF8) | ConvertFrom-Json }
            catch { continue }
            foreach ($key in @('mixins', 'client', 'server')) {
                if ($cfg.PSObject.Properties.Name -contains $key) {
                    foreach ($e in $cfg.$key) {
                        if ($e -is [string] -and $e -notmatch 'WurstMixinConfigPlugin') { $listed[$e] = $j.Name }
                    }
                }
            }
        }
    }
    return [PSCustomObject]@{ Actual = $actual; Listed = $listed }
}

# ---- root project as the reference for classification ----
$rootMix = Get-ProjectMixins '.'

$roots = Get-ProjectRoots
$rows = New-Object System.Collections.Generic.List[object]

foreach ($r in $roots) {
    $m = Get-ProjectMixins $r

    foreach ($name in ($m.Listed.Keys | Sort-Object)) {
        if (-not $m.Actual.ContainsKey($name)) {
            $rows.Add([PSCustomObject]@{
                Project = $r; Kind = 'LISTED_NO_FILE'; Name = $name; Class = 'HARD'
            })
        }
    }

    foreach ($name in ($m.Actual.Keys | Sort-Object)) {
        if ($m.Listed.ContainsKey($name)) { continue }
        if (-not $m.Actual[$name].IsMixin) {
            $rows.Add([PSCustomObject]@{ Project = $r; Kind = 'NO_MIXIN_ANNOTATION'; Name = $name; Class = 'INFO' })
            continue
        }
        $class = 'C'
        if ($rootMix.Actual.ContainsKey($name)) {
            $class = if ($rootMix.Listed.ContainsKey($name)) { 'A' } else { 'B' }
        }
        $rows.Add([PSCustomObject]@{ Project = $r; Kind = 'FILE_NOT_LISTED'; Name = $name; Class = $class })
    }
}

# ---- report ----
$hard = @($rows | Where-Object { $_.Kind -eq 'LISTED_NO_FILE' })
$gaps = @($rows | Where-Object { $_.Kind -eq 'FILE_NOT_LISTED' })
$classA = @($gaps | Where-Object { $_.Class -eq 'A' })
$classB = @($gaps | Where-Object { $_.Class -eq 'B' })
$classC = @($gaps | Where-Object { $_.Class -eq 'C' })
$projectsWithGaps = @($gaps | Select-Object -ExpandProperty Project -Unique)

$sb = New-Object System.Text.StringBuilder
function W([string]$s) { [void]$sb.AppendLine($s) }

W '# 混入登记缺口（自动生成）'
W ''
W '> 本文件由 `scripts/audit-mixin-registration.ps1` 生成，请勿手工编辑。'
W '> 重新生成：`pwsh -File scripts/audit-mixin-registration.ps1`'
W ''
W '比对每个工程的 `*.mixins.json` 登记项与 `mixin/` 包里**实际带 `@Mixin` 注解**的 `.java`。'
W '两种失效模式：'
W ''
W '| 模式 | 含义 | 后果 |'
W '| --- | --- | --- |'
W '| **登记了但没有文件** | 配置里写了一个不存在的混入类 | Mixin 视为硬错误 —— **启动失败** |'
W '| **有文件但没登记** | 真实混入类，没有任何配置引用它 | 它永远不加载 —— **功能静默失效**（不崩，就是没反应） |'
W ''
W '## 汇总'
W ''
W '| 指标 | 数量 |'
W '| --- | ---: |'
W ("| 工程总数 | {0} |" -f $roots.Count)
W ("| **登记了但没有文件**（硬错误） | **{0}** |" -f $hard.Count)
W ("| **有文件但没登记**（静默失效） | **{0}** |" -f $gaps.Count)
W ("| 　└ A 类：根工程同名且已登记（最可能是真漏登记） | {0} |" -f $classA.Count)
W ("| 　└ B 类：根工程同名但根工程也没登记 | {0} |" -f $classB.Count)
W ("| 　└ C 类：根工程没有该文件（版本特有，需对 jar 核注入点） | {0} |" -f $classC.Count)
W ("| 受影响工程数 | {0} |" -f $projectsWithGaps.Count)
W ''

if ($hard.Count -gt 0) {
    W '## 一、登记了但没有文件（会启动失败）'
    W ''
    W '| 工程 | 配置项 |'
    W '| --- | --- |'
    foreach ($x in ($hard | Sort-Object Project, Name)) { W ("| `{0}` | `{1}` |" -f $x.Project, $x.Name) }
    W ''
}

W '## 二、有文件但没登记（功能静默失效）'
W ''
W '### A 类 —— 根工程同名且已登记，最可能是真漏登记'
W ''
if ($classA.Count -eq 0) { W '（无）' } else {
    W '| 工程 | 混入类 |'
    W '| --- | --- |'
    foreach ($x in ($classA | Sort-Object Project, Name)) { W ("| `{0}` | `{1}` |" -f $x.Project, $x.Name) }
}
W ''
W '### B 类 —— 根工程同名，但根工程也没登记'
W ''
if ($classB.Count -eq 0) { W '（无）' } else {
    W '| 工程 | 混入类 |'
    W '| --- | --- |'
    foreach ($x in ($classB | Sort-Object Project, Name)) { W ("| `{0}` | `{1}` |" -f $x.Project, $x.Name) }
}
W ''
W '### C 类 —— 根工程没有该文件（按工程聚合）'
W ''
if ($classC.Count -eq 0) { W '（无）' } else {
    W '| 工程 | 未登记混入数 | 混入类 |'
    W '| --- | ---: | --- |'
    foreach ($g in ($classC | Group-Object Project | Sort-Object Name)) {
        $names = ($g.Group | Select-Object -ExpandProperty Name | Sort-Object) -join ' '
        W ("| `{0}` | {1} | {2} |" -f $g.Name, $g.Count, $names)
    }
}
W ''
W '## 三、建议的处理顺序'
W ''
W '1. **先清硬错误**（第一节）。这类是启动就崩，不能留。'
W '2. **A 类逐批登记**：每批之后必须真启动一次（`scripts/probe-smoke-clients.py` 或'
W '   `scripts/run-version-tests.ps1`）。混入是硬注入 —— 注入点对不上不会降级，而是抛错。'
W '3. **C 类不要凭名字登记**：先用 `scripts/audit-mixin-injections.py` /'
W '   `audit-mixin-targets.py` 对着真实 jar 核注入点是否存在，筛完再登记。'
W '4. 每批之后重跑本脚本，确认数量单调下降。'
W ''
W '> **为什么不能一次全登记**：登记一个注入点已不存在的混入，结果不是"功能仍然坏"，'
W '> 而是**客户端起不来**。而 C 类恰恰是版本差异最大、最需要逐个核对的一批。'
W ''

$content = $sb.ToString()
$outFull = Join-Path $RepoRoot $OutputPath
$outDir = Split-Path -Parent $outFull
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
[System.IO.File]::WriteAllText($outFull, $content, (New-Object System.Text.UTF8Encoding($false)))

$tsvFull = Join-Path $RepoRoot $TsvPath
$tsvDir = Split-Path -Parent $tsvFull
if (-not (Test-Path $tsvDir)) { New-Item -ItemType Directory -Path $tsvDir -Force | Out-Null }
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("project`tkind`tclass`tmixin")
foreach ($x in ($rows | Sort-Object Project, Kind, Class, Name)) {
    $lines.Add(("{0}`t{1}`t{2}`t{3}" -f $x.Project, $x.Kind, $x.Class, $x.Name))
}
[System.IO.File]::WriteAllLines($tsvFull, $lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ("Wrote {0}" -f $OutputPath) -ForegroundColor Green
Write-Host ("Wrote {0}" -f $TsvPath) -ForegroundColor Green
Write-Host ("硬错误 {0} / 静默失效 {1}（A {2} / B {3} / C {4}）/ 受影响工程 {5}" -f `
    $hard.Count, $gaps.Count, $classA.Count, $classB.Count, $classC.Count, $projectsWithGaps.Count)
