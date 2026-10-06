#Requires -Version 5.1
<#
.SYNOPSIS
    Generates docs/STATUS.md from the actual repository contents.

.DESCRIPTION
    The documentation audit found SEVEN mutually exclusive project counts and SIX
    different test counts across the docs, because every number was hand-written
    and nothing regenerated it.  This script is the single source of truth: it
    reads gradle.properties / build.gradle / the source trees and emits
    docs/STATUS.md, so status numbers can never drift again.

    Run it after any change that alters the project list, versions or test
    counts, then commit the regenerated docs/STATUS.md.

.PARAMETER OutputPath
    Where to write the report.  Default: docs/STATUS.md

.PARAMETER Check
    Do not write anything; exit 1 if the checked-in docs/STATUS.md differs from
    what would be generated.  Use this in CI to stop status drift.

.EXAMPLE
    pwsh -File scripts/generate-status.ps1
    pwsh -File scripts/generate-status.ps1 -Check
#>
[CmdletBinding()]
param(
    [string]$OutputPath = 'docs/STATUS.md',
    [switch]$Check,
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Locate the repository root.  Prefer an explicit -RepoRoot, then the script's
# own directory's parent, then walk up from the current directory looking for
# settings.gradle.  This keeps the script usable when it is dot-sourced or run
# from a pipeline, where $PSScriptRoot is not populated.
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
        if (-not $probe -or -not (Test-Path (Join-Path $probe 'settings.gradle'))) {
            throw 'Cannot locate the repository root (no settings.gradle found). Pass -RepoRoot explicitly.'
        }
        $RepoRoot = $probe
    }
}
Set-Location $RepoRoot

# Project roots, in a stable order.  A project root is any directory that has
# both settings.gradle and build.gradle and is not a vendored/excluded tree.
$ExcludePattern = '\\(build|source|_tools|_artifacts|run|\.test|\.opencode|forge-mdk|baritone-maven|node_modules|logs)\\|\\\.gradle'

function Get-ProjectRoots {
    $roots = New-Object System.Collections.Generic.List[string]

    # Canonical layout.  Listed explicitly so the count is deterministic and a
    # stray directory can never silently change it.
    $candidates = New-Object System.Collections.Generic.List[string]
    $candidates.Add('.')
    foreach ($d in @('fabric', 'neoforge')) {
        if (Test-Path $d) { $candidates.Add($d) }
    }
    foreach ($parent in @('versions', 'fabric\versions', 'neoforge\versions')) {
        if (Test-Path $parent) {
            foreach ($dir in Get-ChildItem $parent -Directory | Sort-Object Name) {
                $candidates.Add($dir.FullName.Substring($RepoRoot.Length + 1))
            }
        }
    }

    foreach ($c in $candidates) {
        $full = if ($c -eq '.') { $RepoRoot } else { Join-Path $RepoRoot $c }
        if ((Test-Path (Join-Path $full 'build.gradle')) -and (Test-Path (Join-Path $full 'settings.gradle'))) {
            $roots.Add($c)
        }
    }
    return $roots
}

function Read-Property([string]$Path, [string]$Key) {
    if (-not (Test-Path $Path)) { return $null }
    $m = Select-String -Path $Path -Pattern "^$([regex]::Escape($Key))=(.*)$" -ErrorAction SilentlyContinue |
         Select-Object -First 1
    if ($m) { return $m.Matches[0].Groups[1].Value.Trim() }
    return $null
}

function Get-GradleVersion([string]$ProjDir) {
    $p = Join-Path $ProjDir 'gradle\wrapper\gradle-wrapper.properties'
    if (-not (Test-Path $p)) { return $null }
    $m = Select-String -Path $p -Pattern 'gradle-([0-9][0-9.]*)-(bin|all)\.zip' -ErrorAction SilentlyContinue |
         Select-Object -First 1
    if ($m) { return $m.Matches[0].Groups[1].Value }
    return $null
}

function Get-Loader([string]$Rel) {
    if ($Rel -in @('fabric')) { return 'Fabric' }
    if ($Rel -like 'fabric\*' -or $Rel -like 'fabric/*') { return 'Fabric' }
    if ($Rel -in @('neoforge')) { return 'NeoForge' }
    if ($Rel -like 'neoforge\*' -or $Rel -like 'neoforge/*') { return 'NeoForge' }
    return 'Forge'
}

function Count-Java([string]$Dir) {
    if (-not (Test-Path -LiteralPath $Dir)) { return 0 }
    return ([System.IO.Directory]::EnumerateFiles($Dir, '*.java', [System.IO.SearchOption]::AllDirectories) |
            Measure-Object).Count
}

function Count-Tests([string]$Dir) {
    if (-not (Test-Path -LiteralPath $Dir)) { return 0 }
    $n = 0
    foreach ($f in [System.IO.Directory]::EnumerateFiles($Dir, '*.java', [System.IO.SearchOption]::AllDirectories)) {
        $n += (Select-String -Path $f -Pattern '^\s*@Test\b' -ErrorAction SilentlyContinue | Measure-Object).Count
    }
    return $n
}

# --- v1.6 feature gate -------------------------------------------------------
# Packages that exist ONLY in the root project (per the architecture audit).
$V16Packages = @(
    'net\wurstclient\clickgui2\component', 'net\wurstclient\music', 'net\wurstclient\twilight',
    'net\wurstclient\background', 'net\wurstclient\compose', 'net\wurstclient\clickgui2\epsilon',
    'net\wurstclient\clickgui2\supersoft', 'net\wurstclient\render\skia', 'net\wurstclient\gui\visual',
    'net\wurstclient\hud2\render', 'net\wurstclient\perimeter', 'net\wurstclient\seed',
    'net\wurstclient\clickgui2\music'
)

Write-Host 'Scanning project roots...' -ForegroundColor Cyan
$projectRoots = Get-ProjectRoots

$rows = New-Object System.Collections.Generic.List[object]
foreach ($rel in $projectRoots) {
    $full = if ($rel -eq '.') { $RepoRoot } else { Join-Path $RepoRoot $rel }
    $gp = Join-Path $full 'gradle.properties'
    $srcJava = Join-Path $full 'src\main\java'
    $testJava = Join-Path $full 'src\test\java'

    $v16Count = 0
    foreach ($pkg in $V16Packages) {
        if (Test-Path -LiteralPath (Join-Path $srcJava $pkg)) { $v16Count++ }
    }

    $rows.Add([PSCustomObject]@{
        Path         = if ($rel -eq '.') { '.' } else { $rel }
        Loader       = (Get-Loader $rel)
        Minecraft    = (Read-Property $gp 'minecraft_version')
        LoaderVer    = (Read-Property $gp 'forge_version')
        Java         = (Read-Property $gp 'java_version')
        Gradle       = (Get-GradleVersion $full)
        ModVersion   = (Read-Property $gp 'mod_version')
        ModId        = (Read-Property $gp 'mod_id')
        Group        = if ((Read-Property $gp 'maven_group')) { Read-Property $gp 'maven_group' }
                       else { Read-Property $gp 'mod_group_id' }
        MainJava     = (Count-Java $srcJava)
        TestClasses  = (Count-Java $testJava)
        TestMethods  = (Count-Tests $testJava)
        V16Packages  = $v16Count
        WrapperJar   = (Test-Path (Join-Path $full 'gradle\wrapper\gradle-wrapper.jar'))
        License      = ((Test-Path (Join-Path $full 'LICENSE.txt')) -or (Test-Path (Join-Path $full 'LICENSE')))
    })
}

# Java toolchain is often only in build.gradle, not gradle.properties.
foreach ($r in $rows) {
    if (-not $r.Java) {
        $full = if ($r.Path -eq '.') { $RepoRoot } else { Join-Path $RepoRoot $r.Path }
        $m = Select-String -Path (Join-Path $full 'build.gradle') `
             -Pattern 'languageVersion\s*=\s*JavaLanguageVersion\.of\((\d+)\)' -ErrorAction SilentlyContinue |
             Select-Object -First 1
        if ($m) { $r.Java = $m.Matches[0].Groups[1].Value }
    }
}

$totalMain = ($rows | Measure-Object -Property MainJava -Sum).Sum
$totalTestClasses = ($rows | Measure-Object -Property TestClasses -Sum).Sum
$totalTests = ($rows | Measure-Object -Property TestMethods -Sum).Sum
$rootRow = $rows | Where-Object { $_.Path -eq '.' }
# @(...) is required: under Set-StrictMode a single-item pipeline result is a
# scalar and has no .Count property.
$withV16 = @($rows | Where-Object { $_.V16Packages -gt 0 }).Count
$uniqueMc = @($rows | Select-Object -ExpandProperty Minecraft -Unique | Sort-Object).Count
$loaders = @($rows | Select-Object -ExpandProperty Loader -Unique | Sort-Object)
$uniqueModVersions = @($rows | Select-Object -ExpandProperty ModVersion -Unique).Count

# --- render ------------------------------------------------------------------
$sb = New-Object System.Text.StringBuilder
function W([string]$s) { [void]$sb.AppendLine($s) }

W '# 项目状态（自动生成）'
W ''
W '> **本文件由 `scripts/generate-status.ps1` 自动生成，请勿手工编辑。**'
W '> 修改后运行 `pwsh -File scripts/generate-status.ps1` 重新生成并提交。'
W '> CI 可用 `-Check` 模式阻止状态漂移。'
W ''
W '架构审计发现文档里同时存在 7 个互斥的工程数、6 个不同的测试数——根因是所有数字都是手写的。'
W '本文件把状态数字变成生成物，从机制上消除漂移。'
W ''
W '## 汇总'
W ''
W '| 指标 | 值 |'
W '| --- | --- |'
W ("| 独立 Gradle 工程 | **{0}** |" -f $rows.Count)
W ("| Minecraft 版本 | **{0}** |" -f $uniqueMc)
W ("| 加载器 | **{0}** |" -f ($loaders -join ' / '))
W ("| `src/main/java` 文件 | **{0:N0}** |" -f $totalMain)
W ("| 测试类 / 测试方法（全部工程合计） | **{0:N0} / {1:N0}** |" -f $totalTestClasses, $totalTests)
W ("| 根工程源码 / 测试类 / 测试方法 | **{0:N0} / {1:N0} / {2:N0}** |" -f $rootRow.MainJava, $rootRow.TestClasses, $rootRow.TestMethods)
W ("| 含 v1.6 子系统的工程 | **{0} / {1}** |" -f $withV16, $rows.Count)
W ("| 含 gradle-wrapper.jar 的工程 | **{0} / {1}** |" -f ($rows | Where-Object { $_.WrapperJar }).Count, $rows.Count)
W ("| 含 LICENSE.txt 的工程 | **{0} / {1}** |" -f ($rows | Where-Object { $_.License }).Count, $rows.Count)
W ("| `mod_version` 不同取值数 | **{0}** |" -f $uniqueModVersions)
W ''
W '## 工程清单'
W ''
W '| 工程 | 加载器 | MC | 加载器版本 | Java | Gradle | mod_version | 组 | 源码 | 测试类 | 测试方法 | v1.6 包 | wrapper | LICENSE |'
W '| --- | --- | --- | --- | ---: | ---: | --- | --- | ---: | ---: | ---: | ---: | :---: | :---: |'
foreach ($r in $rows | Sort-Object @{E={$_.Loader}}, @{E={$_.Minecraft}}) {
    W ("| `{0}` | {1} | {2} | {3} | {4} | {5} | `{6}` | `{7}` | {8} | {9} | {10} | {11}/13 | {12} | {13} |" -f `
        $r.Path, $r.Loader, $r.Minecraft, $r.LoaderVer, $r.Java, $r.Gradle, $r.ModVersion, $r.Group,
        $r.MainJava, $r.TestClasses, $r.TestMethods, $r.V16Packages,
        $(if ($r.WrapperJar) { 'yes' } else { '**NO**' }), $(if ($r.License) { 'yes' } else { '**NO**' }))
}
W ''
W '## 版本一致性'
W ''
W '### 加载器版本取值分布'
W ''
W '| 加载器 | 版本 | 工程数 |'
W '| --- | --- | ---: |'
foreach ($g in $rows | Group-Object Loader, LoaderVer | Sort-Object Name) {
    # Read the values off the grouped rows rather than parsing Group-Object's
    # composite Name: a null LoaderVer yields a Name with no second element.
    $loader = $g.Group[0].Loader
    $ver = $g.Group[0].LoaderVer
    W ("| {0} | `{1}` | {2} |" -f $loader, $ver, $g.Count)
}
W ''
W '### mod_version 取值分布'
W ''
W '| mod_version | 工程数 |'
W '| --- | ---: |'
foreach ($g in $rows | Group-Object ModVersion | Sort-Object Name) {
    W ("| `{0}` | {1} |" -f $g.Name, $g.Count)
}
W ''
W '## v1.6 功能缺口'
W ''
W 'v1.6 新增的 13 个源码包只存在于根工程。以下工程不含任何 v1.6 包：'
W ''
$missingV16 = @($rows | Where-Object { $_.V16Packages -eq 0 } | Select-Object -ExpandProperty Path)
W ("共 **{0}** 个：{1}" -f $missingV16.Count, (($missingV16 | ForEach-Object { "``$_``" }) -join '、'))
W ''
W '> 这是当前最大的功能缺口。README 的版本矩阵宣称支持全部 23 个 MC 版本，'
W '> 但只有根工程 Forge 1.20.1 具备 v1.6 能力。'
W ''

$content = $sb.ToString()

if ($Check) {
    if (-not (Test-Path $OutputPath)) {
        Write-Host "CHECK FAILED: $OutputPath does not exist. Run without -Check to generate it." -ForegroundColor Red
        exit 1
    }
    $existing = [System.IO.File]::ReadAllText((Resolve-Path $OutputPath))
    if ($existing -ne $content) {
        Write-Host "CHECK FAILED: $OutputPath is stale. Re-run scripts/generate-status.ps1 and commit the result." -ForegroundColor Red
        exit 1
    }
    Write-Host "OK: $OutputPath is up to date." -ForegroundColor Green
    exit 0
}

$outFull = Join-Path $RepoRoot $OutputPath
$outDir = Split-Path -Parent $outFull
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
[System.IO.File]::WriteAllText($outFull, $content, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ("Wrote {0}: {1} projects, {2:N0} main java files, {3:N0} test methods." -f `
    $OutputPath, $rows.Count, $totalMain, $totalTests) -ForegroundColor Green
