@echo off
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install.ps1" -Launch
) else (
  pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install.ps1" -Launch
)
if errorlevel 1 pause
