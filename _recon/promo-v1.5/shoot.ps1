#requires -Version 5.1
# Launch the v1.5.0 Fabric 1.21.11 client twice and capture real screenshots.
# Run as one long-lived PowerShell process so the child client is not killed.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W32 {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr w, IntPtr l);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    public const uint SWP_NOMOVE = 0x2, SWP_NOZORDER = 0x4;
    public const uint PW_RENDERFULLCONTENT = 0x2;
}
"@

$repo = 'C:\Users\ui863\Documents\trae_projects\VAP'
$proj = Join-Path $repo 'fabric\versions\1.21.11'
$shots = Join-Path $repo '_recon\promo-v1.5\shots'
New-Item -ItemType Directory -Path $shots -Force | Out-Null
$token = 'promov15'
$port = 26511
$headers = @{'X-Wurst-Token' = $token}
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.jdks\jdk21.0.12_12'

$logPath = Join-Path $proj 'run\logs\latest.log'

function Get-GameWindow {
    foreach ($p in (Get-Process javaw, java -ErrorAction SilentlyContinue)) {
        if ($p.MainWindowHandle -ne 0 -and $p.MainWindowTitle -match 'Minecraft') {
            return $p.MainWindowHandle
        }
    }
    return [IntPtr]::Zero
}

function Wait-Ping([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri "http://127.0.0.1:$port/ping" `
                -Headers $headers -TimeoutSec 2
            if ($r -eq 'ok') { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Start-Client([bool]$quickPlay) {
    $before = if (Test-Path $logPath) { (Get-Item $logPath).Length } else { 0 }
    $out = Join-Path $env:TEMP 'promo-run.out.log'
    $err = Join-Path $env:TEMP 'promo-run.err.log'
    if ($quickPlay) {
        $args = '/c,"C:\Users\ui863\Documents\trae_projects\VAP\_recon\promo-v1.5\launch-online.bat"'
    } else {
        $args = '/c,"C:\Users\ui863\Documents\trae_projects\VAP\_recon\promo-v1.5\launch-title.bat"'
    }
    $p = Start-Process -FilePath 'cmd.exe' -ArgumentList $args `
        -WorkingDirectory $repo -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $out -RedirectStandardError $err
    return @{ Proc = $p; Offset = $before; Out = $out; Err = $err }
}

function Wait-World([long]$offset, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $logPath) {
            $fs = [IO.File]::Open($logPath, 'Open', 'Read', 'ReadWrite')
            try {
                $sr = New-Object IO.StreamReader($fs, [Text.Encoding]::UTF8)
                $text = $sr.ReadToEnd()
            } finally { $sr.Dispose(); $fs.Dispose() }
            if ($text.Length -gt $offset) { $text = $text.Substring([int]$offset) }
            if ($text -match 'logged in with entity id|joined the game') {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Resize-Game([IntPtr]$hwnd) {
    $r = New-Object W32+RECT
    [void][W32]::GetWindowRect($hwnd, [ref]$r)
    [void][W32]::SetWindowPos($hwnd, [IntPtr]::Zero, 30, 20, 1936, 1119,
        [W32]::SWP_NOZORDER)
    Start-Sleep -Milliseconds 1500
}

function Save-Shot([IntPtr]$hwnd, [string]$path) {
    $r = New-Object W32+RECT
    [void][W32]::GetWindowRect($hwnd, [ref]$r)
    $w = $r.Right - $r.Left
    $h = $r.Bottom - $r.Top
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc()
    $ok = [W32]::PrintWindow($hwnd, $hdc, [W32]::PW_RENDERFULLCONTENT)
    $g.ReleaseHdc($hdc)
    $g.Dispose()
    if (-not $ok) { $bmp.Dispose(); throw 'PrintWindow failed' }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("saved {0} {1}x{2}" -f $path, $w, $h)
}

function Enable-Hack([string]$name) {
    $u = "http://127.0.0.1:$port/hacks/enable?name=" + [uri]::EscapeDataString($name)
    try { Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30 | Out-Null }
    catch { Write-Host ("enable {0} failed: {1}" -f $name, $_.Exception.Message) }
    Start-Sleep -Milliseconds 1200
}

function Disable-Hack([string]$name) {
    $u = "http://127.0.0.1:$port/hacks/disable?name=" + [uri]::EscapeDataString($name)
    try { Invoke-RestMethod -Method Post -Uri $u -Headers $headers -TimeoutSec 30 | Out-Null }
    catch {}
    Start-Sleep -Milliseconds 800
}

function Stop-Client([IntPtr]$hwnd) {
    [void][W32]::PostMessage($hwnd, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)
    Start-Sleep -Seconds 6
    foreach ($p in (Get-Process javaw -ErrorAction SilentlyContinue)) {
        if ($p.MainWindowTitle -match 'Minecraft') { Stop-Process -Id $p.Id -Force }
    }
    Start-Sleep -Seconds 2
}

# ---------------- pass 1: title screen ----------------
Write-Host '== pass 1: title =='
$c = Start-Client $false
if (-not (Wait-Ping 300)) { throw 'control server did not start (title)' }
Start-Sleep -Seconds 10
$hwnd = Get-GameWindow
if ($hwnd -eq [IntPtr]::Zero) { throw 'game window not found (title)' }
Resize-Game $hwnd
Start-Sleep -Seconds 2
Save-Shot $hwnd (Join-Path $shots 'raw_title.png')
Stop-Client $hwnd

# ---------------- pass 2: in world ----------------
Write-Host '== pass 2: world =='
$c = Start-Client $true
if (-not (Wait-Ping 300)) { throw 'control server did not start (world)' }
if (-not (Wait-World $c.Offset 180)) { throw 'world did not load' }
Start-Sleep -Seconds 12
$hwnd = Get-GameWindow
if ($hwnd -eq [IntPtr]::Zero) { throw 'game window not found (world)' }
Resize-Game $hwnd
Start-Sleep -Seconds 3
Save-Shot $hwnd (Join-Path $shots 'raw_world.png')

Enable-Hack 'X-Ray'
Save-Shot $hwnd (Join-Path $shots 'raw_xray.png')
Disable-Hack 'X-Ray'

Enable-Hack 'ClickGUI'
Start-Sleep -Seconds 2
Save-Shot $hwnd (Join-Path $shots 'raw_clickgui.png')

Enable-Hack 'Navigator'
Start-Sleep -Seconds 2
Save-Shot $hwnd (Join-Path $shots 'raw_navigator.png')

Stop-Client $hwnd
Write-Host 'all shots done'
