<#
.SYNOPSIS
  Back up the installed HQ, install the current mod, and upgrade to the modern campus on next launch.
.EXAMPLE
  .\tools\update-hq.ps1 -Launch
.EXAMPLE
  .\tools\update-hq.ps1 -PrismData 'D:\Games\Prism' -InstanceId T3Craft-26.3-Native -WhatIf
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$PrismData = "$env:APPDATA\PrismLauncher",
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$InstanceId = 'T3Craft-26.3-Native',
    [switch]$Launch,
    [switch]$ModOnly
)
$ErrorActionPreference = 'Stop'
$PrismData = [IO.Path]::GetFullPath($PrismData)
$instanceDir = 'instances'
$prismConfig = Join-Path $PrismData 'prismlauncher.cfg'
if (Test-Path -LiteralPath $prismConfig) {
    $line = Get-Content -LiteralPath $prismConfig | Where-Object { $_ -match '^InstanceDir=' } | Select-Object -First 1
    if ($line) { $instanceDir = $line.Substring(12) }
}
$instancesRoot = [IO.Path]::GetFullPath((Join-Path $PrismData $instanceDir))
$instancePath = [IO.Path]::GetFullPath((Join-Path $instancesRoot $InstanceId))
if (-not $instancePath.StartsWith($instancesRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Instance path escaped the Prism instances directory.' }
$gamePath = Join-Path $instancePath 'minecraft'
if (Test-Path -LiteralPath (Join-Path $instancePath '.minecraft')) { $gamePath = Join-Path $instancePath '.minecraft' }
$worldPath = [IO.Path]::GetFullPath((Join-Path $gamePath 'saves\AgentCraft HQ'))
$busy = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object {
    $_.CommandLine -and ($_.CommandLine.Replace('/', '\').IndexOf($gamePath, [StringComparison]::OrdinalIgnoreCase) -ge 0)
}
if ($busy) { throw 'Close Minecraft first, then run Update-T3Craft.cmd again. Your open world has not been changed.' }
$hasWorld = Test-Path -LiteralPath (Join-Path $worldPath 'level.dat')
if (Test-Path -LiteralPath $worldPath) {
    if (-not $hasWorld) { throw "The HQ folder has no level.dat: $worldPath. Resolve this before updating." }
    if (-not $ModOnly) {
        $anchorsPath = Join-Path $worldPath 'agentcraft-anchors.json'
        if (-not (Test-Path -LiteralPath $anchorsPath) -or -not (Test-Path -LiteralPath (Join-Path $worldPath 'agentcraft-hq-plan.dat'))) {
            throw 'This world has no recorded HQ layout/plan. An automatic upgrade cannot preserve edits. The world has not been changed.'
        }
        $layout = Get-Content -LiteralPath $anchorsPath -Raw | ConvertFrom-Json
        if ($layout.layout -notin @('studio', 'modern')) { throw 'The saved HQ is not a studio or modern layout. The world has not been changed.' }
    }
    $lockPath = Join-Path $worldPath 'session.lock'
    if (Test-Path -LiteralPath $lockPath) {
        try { $lockProbe = [IO.File]::Open($lockPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None); $lockProbe.Dispose() }
        catch { throw 'The HQ world is locked. Close Minecraft before updating.' }
    }
}
$prismExe = "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe"
if (Test-Path -LiteralPath (Join-Path $PrismData 'prismlauncher.exe')) { $prismExe = Join-Path $PrismData 'prismlauncher.exe' }
if ($Launch -and -not (Test-Path -LiteralPath $prismExe)) {
    $prismCommand = Get-Command prismlauncher -ErrorAction SilentlyContinue
    if ($prismCommand) { $prismExe = $prismCommand.Source }
    else { throw 'Prism Launcher was not found. Install it or run this script without -Launch.' }
}
$updateDescription = if ($ModOnly) { 'Back up the HQ and current T3Craft jar, then install the latest mod without queuing a world rebuild' } else { 'Back up the HQ and current T3Craft jar, install the latest build, and queue the modern campus upgrade' }
if (-not $PSCmdlet.ShouldProcess($instancePath, $updateDescription)) { return }
$backupRoot = [IO.Path]::GetFullPath((Join-Path $PrismData "backups\t3craft\$InstanceId"))
$backupPath = [IO.Path]::GetFullPath((Join-Path $backupRoot ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))))
if (-not $backupPath.StartsWith($backupRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Backup path escaped its destination.' }
New-Item -ItemType Directory -Path $backupPath | Out-Null
if ($hasWorld) {
    Write-Output "Backing up the complete HQ world to $backupPath"
    Copy-Item -LiteralPath $worldPath -Destination $backupPath -Recurse -Force
    $backupWorld = Join-Path $backupPath 'AgentCraft HQ'
    # Verify every copied world file before modifying the installation or queuing a rebuild.
    foreach ($sourceFile in Get-ChildItem -LiteralPath $worldPath -Recurse -File -Force) {
        $relativePath = $sourceFile.FullName.Substring($worldPath.Length + 1)
        $copiedFile = Join-Path $backupWorld $relativePath
        if (-not (Test-Path -LiteralPath $copiedFile) -or (Get-FileHash -LiteralPath $sourceFile.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $copiedFile -Algorithm SHA256).Hash) {
            throw "World backup verification failed at $relativePath. No upgrade was queued."
        }
    }
}
$modsPath = Join-Path $gamePath 'mods'
if (Test-Path -LiteralPath $modsPath) {
    $backupMods = Join-Path $backupPath 'mods'
    New-Item -ItemType Directory -Path $backupMods | Out-Null
    Get-ChildItem -LiteralPath $modsPath -File | Where-Object Name -match '^(t3craft-[0-9]|agentcraft-[0-9]|fabric-api-)' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $backupMods
        if ((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath (Join-Path $backupMods $_.Name) -Algorithm SHA256).Hash) { throw 'Mod backup verification failed. Installation was not changed.' }
    }
}
& (Join-Path $PSScriptRoot 'install.ps1') -PrismData $PrismData -InstanceId $InstanceId -SkipPair -Build
$modConfigDir = Join-Path $gamePath 'config'
New-Item -ItemType Directory -Force -Path $modConfigDir | Out-Null
$updater = Join-Path $modConfigDir 't3craft-updater.json'
$metadata = @{ repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')); prismData = $PrismData; instanceId = $InstanceId } | ConvertTo-Json
[IO.File]::WriteAllText($updater, $metadata, [Text.UTF8Encoding]::new($false))
if ($ModOnly) { Write-Output 'Mod updated. No world upgrade was queued.' } elseif ($hasWorld) {
    [IO.File]::WriteAllText((Join-Path $worldPath 't3craft-upgrade-modern'), "modern`n", [Text.UTF8Encoding]::new($false))
    Write-Output 'Modern campus upgrade queued. It will run once when Minecraft opens the HQ.'
} else { Write-Output 'Your first HQ world will be created with the modern campus.' }
Write-Output "Backup: $backupPath"
Write-Output 'Your shaders, settings and T3 pairing are preserved.'
if ($Launch) {
    $quotedPrismData = '"' + ($PrismData -replace '(\\+)$', '$1$1') + '"'
    Start-Process -FilePath $prismExe -ArgumentList @('--dir', $quotedPrismData, '--launch', $InstanceId) -WindowStyle Hidden | Out-Null
}
