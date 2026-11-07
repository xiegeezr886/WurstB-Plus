#requires -Version 5.1
# Capture the running Minecraft window with PrintWindow (RENDERFULLCONTENT).
# Usage:
#   powershell -File capture.ps1 -Out shot.png -Width 1920 -Height 1080
#   powershell -File capture.ps1 -Out shot.png -Key 0xA1 -WaitMs 1200
param(
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$Width = 0,
    [int]$Height = 0,
    [int]$Key = 0,
    [int]$WaitMs = 900
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32Cap {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    public const uint SWP_NOMOVE = 0x2, SWP_NOZORDER = 0x4, SWP_NOACTIVATE = 0x10;
    public const uint PW_RENDERFULLCONTENT = 0x2;
    public const uint KEYEVENTF_KEYUP = 0x2;
}
"@

function Get-GameWindow {
    $procs = Get-Process javaw, java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 }
    foreach ($p in $procs) {
        if ($p.MainWindowTitle -match 'Minecraft') { return $p.MainWindowHandle }
    }
    return [IntPtr]::Zero
}

$hwnd = Get-GameWindow
if ($hwnd -eq [IntPtr]::Zero) { throw 'Minecraft window not found' }

if ($Width -gt 0 -and $Height -gt 0) {
    $r = New-Object Win32Cap+RECT
    [void][Win32Cap]::GetWindowRect($hwnd, [ref]$r)
    $bw = $r.Right - $r.Left - 16
    $bh = $r.Bottom - $r.Top - 39
    $dx = $Width - $bw
    $dy = $Height - $bh
    [void][Win32Cap]::SetWindowPos($hwnd, [IntPtr]::Zero, 40, 40,
        ($r.Right - $r.Left + $dx), ($r.Bottom - $r.Top + $dy),
        [Win32Cap]::SWP_NOZORDER)
    Start-Sleep -Milliseconds 900
}

[void][Win32Cap]::SetForegroundWindow($hwnd)
if ($Key -ne 0) {
    Start-Sleep -Milliseconds 400
    # Post both WM_KEYDOWN/WM_KEYUP and the low-level event; one of them lands.
    [void][Win32Cap]::PostMessage($hwnd, 0x100, [IntPtr]$Key, [IntPtr]0)
    Start-Sleep -Milliseconds 80
    [void][Win32Cap]::PostMessage($hwnd, 0x101, [IntPtr]$Key, [IntPtr]0)
    [Win32Cap]::keybd_event([byte]$Key, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 90
    [Win32Cap]::keybd_event([byte]$Key, 0, [Win32Cap]::KEYEVENTF_KEYUP, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds $WaitMs
}

$r = New-Object Win32Cap+RECT
[void][Win32Cap]::GetWindowRect($hwnd, [ref]$r)
$w = $r.Right - $r.Left
$h = $r.Bottom - $r.Top
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
$ok = [Win32Cap]::PrintWindow($hwnd, $hdc, [Win32Cap]::PW_RENDERFULLCONTENT)
$g.ReleaseHdc($hdc)
$g.Dispose()
if (-not $ok) {
    $bmp.Dispose()
    throw 'PrintWindow failed'
}
$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("saved {0} {1}x{2}" -f $Out, $w, $h)
