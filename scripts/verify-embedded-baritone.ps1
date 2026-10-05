<#
.SYNOPSIS
    校验构建产物里内嵌的 Baritone 是否与工程的目标 Minecraft 版本一致。

.DESCRIPTION
    背景：Baritone 的 Mixin 目标使用 Fabric intermediary 方法名（method_XXXXX），
    该编号在每个 Minecraft 版本都会重新分配。若把为其他 MC 版本构建的 Baritone
    内嵌进来，Mixin 会在 APPLY 阶段找不到目标方法；又因为 Baritone 的 mixin 配置是
    "required": true，结果是游戏在窗口出现前直接崩溃，而且加载器不会给出
    "版本不兼容"的提示。

    历史事故：fabric/versions/1.21.7 曾内嵌为 MC 1.21.11 构建的 Baritone 1.17.0
    （坐标 1.17.0-1.21.11-mc1.21.7），在 1.21.7 上必然启动崩溃。

    本脚本读取产物的内嵌清单，找到 Baritone，再打开那个内嵌 jar 读它自己声明的
    minecraft 版本约束，与工程的 minecraft_version 比对。
    - Fabric : 清单在 fabric.mod.json 的 jars[].file
    - Forge  : 清单在 META-INF/jarjar/metadata.json 的 jars[].path
    - NeoForge: 同 Forge

.PARAMETER ProjectDir
    平台工程目录，例如 fabric/versions/1.21.7

.PARAMETER JarPath
    可选。直接指定要校验的产物；不指定则在 build/libs 下自动查找最新主产物。

.EXAMPLE
    pwsh scripts/verify-embedded-baritone.ps1 -ProjectDir fabric/versions/1.21.7
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ProjectDir,
    [string] $JarPath
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-EntryText {
    param($Zip, [string] $Name)
    $e = $Zip.Entries | Where-Object { $_.FullName -eq $Name } | Select-Object -First 1
    if (-not $e) { return $null }
    $r = New-Object System.IO.StreamReader($e.Open())
    try { return $r.ReadToEnd() } finally { $r.Close() }
}

function Get-EntryBytes {
    param($Zip, [string] $Name)
    $e = $Zip.Entries | Where-Object { $_.FullName -eq $Name } | Select-Object -First 1
    if (-not $e) { return $null }
    $ms = New-Object System.IO.MemoryStream
    $s = $e.Open()
    try { $s.CopyTo($ms) } finally { $s.Close() }
    return $ms.ToArray()
}

function Test-McDeclared {
    <#
      判断声明的 MC 版本约束是否涵盖目标版本。支持：
        Forge/NeoForge 区间 : [1.21.6,1.21.8] / [1.21.6,1.21.8) / [1.21.7,)
        Fabric 波浪号       : ~1.21.7   (等价于 >=1.21.7 且 <1.22)
        Fabric 纯版本号      : 1.21.6 / 1.21.7（逐项调用，做等值比较）
    #>
    param([string] $Declared, [string] $Target)

    if (-not $Declared) { return $false }
    $d = $Declared.Trim()

    function ConvertTo-Ver([string] $v) {
        $clean = $v.Trim() -replace '[^0-9.].*$', ''
        $nums = @()
        foreach ($p in ($clean -split '\.')) { if ($p -ne "") { $nums += [int]$p } }
        while ($nums.Count -lt 3) { $nums += 0 }
        return ,$nums
    }
    function Compare-Ver($a, $b) {
        for ($i = 0; $i -lt 3; $i++) {
            if ($a[$i] -lt $b[$i]) { return -1 }
            if ($a[$i] -gt $b[$i]) { return 1 }
        }
        return 0
    }

    $t = ConvertTo-Ver $Target

    if ($d -match '^([\[\(])\s*([0-9][0-9.]*)?\s*,\s*([0-9][0-9.]*)?\s*([\]\)])$') {
        $loInc = $Matches[1] -eq '['
        $lo = $Matches[2]; $hi = $Matches[3]; $hiInc = $Matches[4] -eq ']'
        if ($lo) {
            $c = Compare-Ver $t (ConvertTo-Ver $lo)
            if ($c -lt 0) { return $false }
            if ($c -eq 0 -and -not $loInc) { return $false }
        }
        if ($hi) {
            $c = Compare-Ver $t (ConvertTo-Ver $hi)
            if ($c -gt 0) { return $false }
            if ($c -eq 0 -and -not $hiInc) { return $false }
        }
        return $true
    }

    if ($d -match '^~\s*([0-9][0-9.]*)$') {
        $base = ConvertTo-Ver $Matches[1]
        if ((Compare-Ver $t $base) -lt 0) { return $false }
        $upper = @($base[0], $base[1] + 1, 0)
        if ((Compare-Ver $t $upper) -ge 0) { return $false }
        return $true
    }

    if ($d -match '^[0-9][0-9.]*$') {
        return (Compare-Ver $t (ConvertTo-Ver $d)) -eq 0
    }

    return $d -match [regex]::Escape($Target)
}

function Fail {
    param([string] $Message)
    Write-Host ""
    Write-Host "  [失败] $Message" -ForegroundColor Red
    exit 1
}

# ---- 1. 读工程配置 ---------------------------------------------------------
$ProjectDir = (Resolve-Path $ProjectDir).Path
$propsPath = Join-Path $ProjectDir "gradle.properties"
if (-not (Test-Path $propsPath)) { Fail "找不到 $propsPath" }

$props = @{}
Get-Content $propsPath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.+?)\s*$') { $props[$Matches[1]] = $Matches[2] }
}
$projectMc = $props["minecraft_version"]
if (-not $projectMc) { Fail "gradle.properties 里没有 minecraft_version" }

Write-Host "工程目录      : $ProjectDir"
Write-Host "工程 MC 版本  : $projectMc"

# ---- 2. 定位产物 -----------------------------------------------------------
if (-not $JarPath) {
    $libs = Join-Path $ProjectDir "build\libs"
    if (-not (Test-Path $libs)) { Fail "找不到 build\libs，请先执行构建" }
    $c = Get-ChildItem $libs -Filter "*.jar" |
        Where-Object { $_.Name -notmatch "sources|javadoc|-dev" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $c) { Fail "build\libs 下没有找到产物 jar" }
    $JarPath = $c.FullName
}
$JarPath = (Resolve-Path $JarPath).Path
Write-Host "产物          : $(Split-Path $JarPath -Leaf)"
Write-Host ""

# ---- 3. 解析内嵌清单 -------------------------------------------------------
$outer = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
$checked = 0
$failed = 0
$warned = 0

# ---------------------------------------------------------------------------
# 官方 Baritone 产物指纹白名单（SHA256 -> 说明）。
#
# 为什么必须有这一层：内嵌 jar 自己声明的 minecraft 版本 **不可信**。
# 本项目历史上就吃过这个亏——`1.17.0-1.21.11-mc1.21.7` 那个包里，
# fabric.mod.json 照样写着 "minecraft": ["1.21.7"]，但代码是按 1.21.11 编译的，
# 于是任何只比对元数据的检查都会放它过去，而游戏启动时必然 Mixin 崩溃。
#
# 因此判定分两关：
#   第一关——摘要必须命中本表（唯一可信的"来源"判据）
#   第二关——该产物自述的 MC 约束必须涵盖工程的 minecraft_version
#
# 摘要取自各 Release 页面的 asset digest；同一份产物可在多个坐标下复用
# （1.15.0 同时供 1.21.6/1.21.7/1.21.8），所以这里按摘要而不是按版本号索引。
# ---------------------------------------------------------------------------
$trustedDigests = @{
    # --- Baritone 1.12.0（官方支持 1.21.2 / 1.21.3）---
    "b3b36aa3d74c4df053d147ee9254b70c15f4d1e5e11a2766141a146eea3bd60b" = "1.12.0 fabric"
    "159d83fa657c87a513aecddc4fb6b4d12ce7809a6b1cf6072056974b63e8dca0" = "1.12.0 forge"
    "2c2fdc342bfe6ba5bd5fe9656188e27e3ea89371c77163f05050807e59ee443c" = "1.12.0 neoforge"
    # --- Baritone 1.13.1（官方支持 1.21.4）---
    "bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05" = "1.13.1 fabric"
    "f919658fe5315de8119d66a0418d63d7a08f5a57d23cd3756154ed8b2b7239e5" = "1.13.1 forge"
    "9ca745e381afe034b8d65d1dfbfdbb5ea9f7af8ce648010bb0b5ed0f4aaecc66" = "1.13.1 neoforge"

    # --- Baritone 1.14.0（官方支持 1.21.5）---
    "e7c84733fa7c86d15743866cdd46e46eb27ce8285b1a21fbc95113c6a4d0b9d8" = "1.14.0 fabric"
    "4566178acf08c5fe636b1999ef2f7d08fcf1436b6783412d0dd4363c183203e9" = "1.14.0 forge"
    "6f8e3dd3fb38136ad62983459a905d7d4bf560e384c4f94ebeae591a82e87584" = "1.14.0 neoforge"

    # --- Baritone 1.15.0（官方支持 1.21.6 / 1.21.7 / 1.21.8）---
    "c58ef35a133b6ffce96a74682138ac2ee818cbc063b7c62671db9f9d7d783ebb" = "1.15.0 fabric"
    "82b360a295c4ca12ea18750b75cf818b1250f76951f31c18f7fe2f0b4952649c" = "1.15.0 forge"
    "3485b8a51d791a27a142d4a1fa21699433e210385ae48445b507f8dadaf29425" = "1.15.0 neoforge"

    # --- Baritone 1.16.0（官方支持 1.21.9 / 1.21.10）---
    "7a8c2d08fb6e66b2bc32dd6d78570c798186eed114e6e88f692af4e3742a2b46" = "1.16.0 fabric"
    "da20fb533e355f702aabe23d2fd6ea6e6eff6b1bc330bb89422cd6b282afa475" = "1.16.0 forge"
    "a4d3777ac052b6d691638b12ed1d2fe104ac43d69d9e8b0b259c48e0abdc3df7" = "1.16.0 neoforge"
}

try {
    $innerPaths = @()

    # Fabric: fabric.mod.json -> jars[].file
    $fmj = Get-EntryText $outer "fabric.mod.json"
    if ($fmj) {
        try {
            $j = $fmj | ConvertFrom-Json
            foreach ($x in @($j.jars)) { if ($x.file) { $innerPaths += [string]$x.file } }
        } catch { }
    }

    # Forge / NeoForge: META-INF/jarjar/metadata.json -> jars[].path
    $metaText = Get-EntryText $outer "META-INF/jarjar/metadata.json"
    if ($metaText) {
        try {
            $m = $metaText | ConvertFrom-Json
            foreach ($x in @($m.jars)) {
                $p = [string]$x.path
                if ($p) {
                    if ($p -notlike "META-INF/jarjar/*") { $p = "META-INF/jarjar/" + ($p -replace '^/','') }
                    $innerPaths += $p
                }
            }
        } catch { }
    }

    if ($innerPaths.Count -eq 0) { Fail "产物里没有找到任何内嵌依赖清单（Fabric 的 fabric.mod.json.jars 或 Forge 的 META-INF/jarjar/metadata.json）" }

    $baritonePaths = @($innerPaths | Where-Object { $_ -match "baritone" })
    if ($baritonePaths.Count -eq 0) {
        Fail "产物里没有内嵌 Baritone。若这是有意为之请忽略；否则说明 include/jarJar 配置丢了。"
    }

    Write-Host "内嵌依赖共 $($innerPaths.Count) 个，其中 Baritone $($baritonePaths.Count) 个："
    Write-Host ""

    foreach ($ip in $baritonePaths) {
        $bytes = Get-EntryBytes $outer $ip
        if (-not $bytes) { Fail "清单指向的内嵌 jar 在产物中不存在: $ip" }

        $tmp = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), [System.IO.Path]::GetRandomFileName() + ".jar")
        [System.IO.File]::WriteAllBytes($tmp, $bytes)
        try {
            $inner = [System.IO.Compression.ZipFile]::OpenRead($tmp)
            try {
                $modVer = "(未知)"
                $declared = @()

                $ifmj = Get-EntryText $inner "fabric.mod.json"
                if ($ifmj) {
                    $ij = $ifmj | ConvertFrom-Json
                    $modVer = [string]$ij.version
                    foreach ($d in @($ij.depends.minecraft)) { $declared += [string]$d }
                } else {
                    $toml = Get-EntryText $inner "META-INF/neoforge.mods.toml"
                    if (-not $toml) { $toml = Get-EntryText $inner "META-INF/mods.toml" }
                    if ($toml) {
                        $mv = [regex]::Match($toml, '(?m)^\s*version\s*=\s*"([^"]+)"')
                        if ($mv.Success) { $modVer = $mv.Groups[1].Value }
                        foreach ($mm in [regex]::Matches($toml, 'modId\s*=\s*"minecraft"[\s\S]{0,200}?versionRange\s*=\s*"([^"]+)"')) {
                            $declared += $mm.Groups[1].Value
                        }
                    }
                }

                $declaredText = if ($declared.Count) { ($declared -join ", ") } else { "(未声明)" }
                $digest = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
                Write-Host "  ├─ 条目      : $ip"
                Write-Host "  ├─ 自述版本  : $modVer"
                Write-Host "  ├─ 声明 MC   : $declaredText"
                Write-Host "  ├─ SHA-256   : $digest"

                # ---- 第一关：指纹白名单（唯一可信的"来源"判据） ----
                $trustedLabel = $trustedDigests[$digest]

                if (-not $trustedLabel) {
                    Write-Host "  └─ 结论      : ✘ 指纹不在官方白名单中！产物来源不明或已被篡改。" -ForegroundColor Red
                    Write-Host "                 元数据自述的 MC 版本不可信——历史上伪包 1.17.0-1.21.11-mc1.21.7" -ForegroundColor Yellow
                    Write-Host "                 照样声明 minecraft 1.21.7，但装上去必然 Mixin 崩溃。" -ForegroundColor Yellow
                    Write-Host "                 如确认这是官方产物，请把它的 SHA256 加入脚本的 `$trustedDigests。" -ForegroundColor Yellow
                    $failed++
                } else {
                    Write-Host "  ├─ 来源      : ✔ 官方产物（$trustedLabel）" -ForegroundColor Green
                    # ---- 第二关：该产物是否声明支持本工程的 MC 版本 ----
                    $ok = $false
                    foreach ($d in $declared) {
                        if (Test-McDeclared -Declared $d -Target $projectMc) { $ok = $true }
                    }
                    if ($ok) {
                        Write-Host "  └─ 结论      : ✔ 官方产物，且声明支持工程 MC 版本 $projectMc" -ForegroundColor Green
                    } else {
                        Write-Host "  └─ 结论      : ✘ 官方产物，但未声明支持 $projectMc（声明: $declaredText）" -ForegroundColor Red
                        $failed++
                    }
                }
                $checked++
                Write-Host ""
            } finally { $inner.Dispose() }
        } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
    }
} finally { $outer.Dispose() }

if ($failed -gt 0) {
    Fail "有 $failed 个内嵌 Baritone 未通过校验（指纹不在官方白名单 / 未声明支持本工程 MC 版本）。这会导致 Mixin 注入失败、游戏在启动时崩溃，构建被拒绝。"
}

Write-Host "校验通过：$checked 个内嵌 Baritone 均为官方产物且声明支持工程 MC 版本（$projectMc）。" -ForegroundColor Green
exit 0
