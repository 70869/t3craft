@echo off
set "T3CRAFT_INSTALL_SCRIPT=%~dp0tools\install-release.ps1"
rem A source checkout keeps its local build/install workflow; the release ZIP has no mod sources.
if exist "%~dp0mod\build.gradle" set "T3CRAFT_INSTALL_SCRIPT=%~dp0tools\install.ps1"
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%T3CRAFT_INSTALL_SCRIPT%" -Launch
) else (
  pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%T3CRAFT_INSTALL_SCRIPT%" -Launch
)
if errorlevel 1 pause
