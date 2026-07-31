@echo off
:: CC Switch Guardian - 快速启动
:: 双击运行即可静默守护

cd /d "%~dp0"
start "" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0ccswitch-guardian.ps1"
echo CC Switch Guardian started in background.
timeout /t 3 >nul
