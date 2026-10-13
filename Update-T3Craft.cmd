@echo off
cd /d "%~dp0"
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\update-hq.ps1" -ModOnly -Launch
) else (
  pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\update-hq.ps1" -ModOnly -Launch
)
if errorlevel 1 (
  echo.
  echo Update did not finish. Read the error above.
  pause
)
