[CmdletBinding()]
param(
    [Parameter(Mandatory)][int]$GameProcessId,
    [string]$PrismData = "$env:APPDATA\PrismLauncher",
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$InstanceId
)
$ErrorActionPreference = 'Stop'
$logDir = Join-Path $PrismData 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
Start-Transcript -LiteralPath (Join-Path $logDir 't3craft-update.log') -Append | Out-Null
try {
    # Wait for the original Minecraft process; its world locks are checked again by the installer.
    $deadline = (Get-Date).AddMinutes(5)
    while (Get-Process -Id $GameProcessId -ErrorAction SilentlyContinue) {
        if ((Get-Date) -gt $deadline) { throw 'Minecraft did not close within five minutes. Close it and run the installer again.' }
        Start-Sleep -Seconds 2
    }
    & (Join-Path $PSScriptRoot 'install-release.ps1') -PrismData $PrismData -InstanceId $InstanceId -Launch
} finally { Stop-Transcript | Out-Null }
