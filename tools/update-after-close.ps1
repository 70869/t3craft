[CmdletBinding()]
param(
    [Parameter(Mandatory)][int]$GameProcessId,
    [string]$PrismData = "$env:APPDATA\PrismLauncher",
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$InstanceId = 'T3Craft-26.3-Native'
)
$ErrorActionPreference = 'Stop'
$logDir = Join-Path $PrismData 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir 't3craft-update.log'
Start-Transcript -LiteralPath $log -Append | Out-Null
try {
    # Minecraft owns the save until its integrated server has stopped and flushed the world.
    while (Get-Process -Id $GameProcessId -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 2 }
    & (Join-Path $PSScriptRoot 'update-hq.ps1') -PrismData $PrismData -InstanceId $InstanceId -ModOnly -Launch
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "Updater exited with code $LASTEXITCODE." }
}
catch {
    Write-Error $_
    Stop-Transcript | Out-Null
    exit 1
}
Stop-Transcript | Out-Null
