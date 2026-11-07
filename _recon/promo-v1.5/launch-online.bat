@echo off
cd /d C:\Users\ui863\Documents\trae_projects\VAP\fabric\versions\1.21.11
call .\gradlew.bat runClient "-PspWorld=xray-test" "-PspLog=%TEMP%\promo-sp.json" --offline --console=plain
