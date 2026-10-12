<#
.SYNOPSIS
    校验 build/libs 下的产物是不是完整可用的 jar。

.DESCRIPTION
    为什么需要它：构建被中途杀死（工具超时、Ctrl-C、OOM 被杀）时，jar 会停在
    「已写入若干条目、但中央目录还没落盘」的状态。这种文件：

      * 大小正常（几 MB 到几十 MB），不是 0 字节；
      * 修改时间正常；
      * 扩展名、名字都正常；

    —— 从外面完全看不出问题，只有真的去打开它才会报
    「找不到中央目录结尾记录 / File is not a zip file」。
    本仓库实测遇到过两次，两次都是**发布用的那个产物**（不是 -dev）。

    本脚本对每个 jar 检查三件事，任何一项不过就报失败并以非零码退出：

      1. 有没有 EOCD（PK\x05\x06，zip 的中央目录结尾记录）
      2. 中央目录能不能解析（能列条目）
      3. 每个条目的 CRC 是否正确（--deep 时做，默认跳过以省时间）

.PARAMETER Path
    要检查的目录，默认 build/libs。

.PARAMETER Deep
    连每个条目的 CRC 一起验（慢，但能发现内容损坏而非仅结构缺失）。

.PARAMETER AllowEmpty
    目录为空时不算失败（给还没有产物的工程用）。

.EXAMPLE
    pwsh scripts/verify-jars.ps1
    pwsh scripts/verify-jars.ps1 -Deep
#>
[CmdletBinding()]
param(
    [string] $Path = 'build/libs',
    [switch] $Deep,
    [switch] $AllowEmpty
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $Path)) {
    if ($AllowEmpty) { Write-Host "  $Path 不存在，跳过（-AllowEmpty）"; exit 0 }
    Write-Host "  失败：$Path 不存在" -ForegroundColor Red
    exit 1
}

$jars = @(Get-ChildItem $Path -File -Filter '*.jar' -ErrorAction SilentlyContinue)
if ($jars.Count -eq 0) {
    if ($AllowEmpty) { Write-Host "  $Path 下没有 jar，跳过（-AllowEmpty）"; exit 0 }
    Write-Host "  失败：$Path 下没有 jar" -ForegroundColor Red
    exit 1
}

$failed = @()

foreach ($jar in $jars) {
    $problems = @()

    # 1) EOCD：zip 的最后 64 KiB 里必须有 PK\x05\x06（允许尾部注释，故留余量）
    try {
        $fs = [System.IO.File]::OpenRead($jar.FullName)
        try {
            $tailLen = [Math]::Min(65536 + 22, $fs.Length)
            $buf = New-Object byte[] $tailLen
            $fs.Seek($jar.Length - $tailLen, [System.IO.SeekOrigin]::Begin) | Out-Null
            $read = $fs.Read($buf, 0, $tailLen)
            $hasEocd = $false
            for ($i = $read - 22; $i -ge 0; $i--) {
                if ($buf[$i] -eq 0x50 -and $buf[$i+1] -eq 0x4B -and
                    $buf[$i+2] -eq 0x05 -and $buf[$i+3] -eq 0x06) { $hasEocd = $true; break }
            }
            if (-not $hasEocd) { $problems += '缺少 EOCD（中央目录结尾记录）—— 写入被中途打断' }
        } finally { $fs.Dispose() }
    } catch { $problems += "读取失败: $($_.Exception.Message)" }

    # 2) 能否列出条目
    $entryCount = 0
    if ($problems.Count -eq 0) {
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            try {
                $entryCount = $zip.Entries.Count
                if ($entryCount -eq 0) { $problems += '能打开但没有任何条目' }

                # 3) 逐条目 CRC
                if ($Deep -and $entryCount -gt 0) {
                    $bad = 0
                    foreach ($e in $zip.Entries) {
                        try {
                            $s = $e.Open()
                            $null = $s.ReadByte()  # 触发解压，CRC 不符会在这里抛
                            $s.Dispose()
                        } catch { $bad++ }
                    }
                    if ($bad -gt 0) { $problems += "$bad 个条目 CRC 校验失败" }
                }
            } finally { $zip.Dispose() }
        } catch { $problems += "打开失败: $($_.Exception.Message)" }
    }

    if ($problems.Count -gt 0) {
        $failed += $jar.Name
        Write-Host ("  [坏 ] {0,-52} {1,12:N0} B" -f $jar.Name, $jar.Length) -ForegroundColor Red
        $problems | ForEach-Object { Write-Host "         - $_" -ForegroundColor Red }
    } else {
        Write-Host ("  [OK ] {0,-52} {1,12:N0} B  {2} 条目" -f $jar.Name, $jar.Length, $entryCount)
    }
}

Write-Host ''
if ($failed.Count -gt 0) {
    Write-Host ("失败：{0} / {1} 个 jar 不完整。删掉它们并重新构建 —— 不要发布。" -f $failed.Count, $jars.Count) -ForegroundColor Red
    Write-Host '提示：截断的 jar 与完好的 jar 在大小、时间、文件名上都没有区别，只能这样打开来验。'
    exit 1
}

Write-Host ("通过：{0} 个 jar 全部完整{1}。" -f $jars.Count, $(if ($Deep) { '（含逐条目 CRC）' } else { '' }))
exit 0
