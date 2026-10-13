<# Install a verified prebuilt release; never build sources or replace saved worlds. #>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$PrismData = "$env:APPDATA\PrismLauncher",
    [ValidatePattern('^$|^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$InstanceId = '',
    [string]$PackagePath,
    [ValidatePattern('^$|^[a-fA-F0-9]{64}$')][string]$ExpectedSha256 = '',
    [ValidatePattern('^latest$|^v[0-9]+\.[0-9]+\.[0-9]+$')][string]$Release = 'latest',
    [switch]$Launch,
    [switch]$NewInstance
)
$ErrorActionPreference = 'Stop'
Import-Module Microsoft.PowerShell.Utility -ErrorAction Stop
Add-Type -AssemblyName System.IO.Compression.FileSystem
$PrismData = [IO.Path]::GetFullPath($PrismData)
$temporary = $null
$locks = @()
function Read-ZipJson($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if (-not $entry) { throw "Package is missing $Name." }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
}
function Get-T3Hash([string]$Path) {
    # Windows PowerShell's Get-FileHash inherits WhatIf into its internal reads.
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $hasher.Dispose() }
}
function Assert-Hash([string]$Path, [string]$Expected) {
    if ((Get-T3Hash $Path) -ne $Expected) { throw 'Package checksum mismatch. Installation has not been changed.' }
}
function Get-ZipHash($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if (-not $entry) { throw "Package is missing $Name." }
    $stream = $entry.Open()
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $hasher.Dispose() }
}
function Copy-Verified([string]$Source, [string]$Destination) {
    Copy-Item -LiteralPath $Source -Destination $Destination -Recurse -Force
    $copyRoot = if ((Get-Item -LiteralPath $Source).PSIsContainer) { Join-Path $Destination (Split-Path $Source -Leaf) } else { $Destination }
    if ((Get-Item -LiteralPath $Source).PSIsContainer) {
        foreach ($file in Get-ChildItem -LiteralPath $Source -File -Recurse -Force) {
            Assert-Hash (Join-Path $copyRoot $file.FullName.Substring($Source.Length + 1)) (Get-T3Hash $file.FullName)
        }
    } else { Assert-Hash $copyRoot (Get-T3Hash $Source) }
}
function Get-ManagedMods([string]$Mods, [string]$IncomingVersion) {
    if (-not (Test-Path -LiteralPath $Mods)) { return }
    foreach ($file in Get-ChildItem -LiteralPath $Mods -File -Filter '*.jar') {
        $jar = $null
        $installedMod = $null
        try {
            $jar = [IO.Compression.ZipFile]::OpenRead($file.FullName)
            $installedMod = Read-ZipJson $jar 'fabric.mod.json'
            if ($installedMod.id -eq 'agentcraft' -and [version]$installedMod.version -gt [version]$IncomingVersion) { throw 'A newer T3Craft is installed. Downgrading is refused to protect saved data.' }
            if ($installedMod.id -in @('agentcraft', 'fabric-api')) { $file }
        }
        catch { if ($installedMod.id -in @('agentcraft', 'fabric-api') -or $file.Name -match '^(t3craft-|agentcraft-|fabric-api-)') { throw } }
        finally { if ($jar) { $jar.Dispose() } }
    }
}
try {
    # The standalone installer carries the same verified Prism package as the release.
    # An explicit -Release still selects the published feed for future updates.
    $bundledRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $bundledPackage = Join-Path $bundledRoot 'T3Craft-Prism.zip'
    $bundledChecksums = Join-Path $bundledRoot 'SHA256SUMS.txt'
    if (-not $PackagePath -and -not $PSBoundParameters.ContainsKey('Release') -and (Test-Path -LiteralPath $bundledPackage)) {
        if (-not (Test-Path -LiteralPath $bundledChecksums)) { throw 'Bundled package is missing its checksum manifest.' }
        $hashLine = @(Get-Content -LiteralPath $bundledChecksums | Where-Object { $_ -match '^([a-fA-F0-9]{64})  T3Craft-Prism\.zip$' })
        if ($hashLine.Count -ne 1) { throw 'Bundled checksum manifest is invalid.' }
        $PackagePath = $bundledPackage
        $ExpectedSha256 = $hashLine[0].Substring(0, 64)
    }
    if (-not $PackagePath) {
        if ($WhatIfPreference) { $null = $PSCmdlet.ShouldProcess($PrismData, "Download verified $Release release and create or back up/update its T3Craft instance"); return }
        $releaseEndpoint = if ($Release -eq 'latest') { 'latest' } else { "tags/$Release" }
        $releaseInfo = Invoke-RestMethod "https://api.github.com/repos/70869/t3craft/releases/$releaseEndpoint"
        if ($releaseInfo.draft -or $releaseInfo.prerelease) { throw 'Choose a stable published release.' }
        $assets = @($releaseInfo.assets | Where-Object name -in @('T3Craft-Prism.zip', 'SHA256SUMS.txt'))
        if ($assets.Count -ne 2) { throw 'This release has no verified installer package. Use the Prism ZIP on the release page or publish the new release assets first.' }
        $temporary = Join-Path ([IO.Path]::GetTempPath()) ('t3craft-install-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $temporary | Out-Null
        foreach ($asset in $assets) {
            if ($asset.browser_download_url -notlike 'https://github.com/70869/t3craft/releases/download/*') { throw 'Unexpected release download origin.' }
            Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile (Join-Path $temporary $asset.name)
        }
        $PackagePath = Join-Path $temporary 'T3Craft-Prism.zip'
        $hashLine = @(Get-Content -LiteralPath (Join-Path $temporary 'SHA256SUMS.txt') | Where-Object { $_ -match '^([a-fA-F0-9]{64})  T3Craft-Prism\.zip$' })
        if ($hashLine.Count -ne 1) { throw 'Release checksum manifest is invalid.' }
        $ExpectedSha256 = $hashLine[0].Substring(0, 64)
    }
    $PackagePath = [IO.Path]::GetFullPath($PackagePath)
    if ($ExpectedSha256) { Assert-Hash $PackagePath $ExpectedSha256 }
    $archive = [IO.Compression.ZipFile]::OpenRead($PackagePath)
    try {
        $info = Read-ZipJson $archive 'compatibility.json'
        if ($info.schemaVersion -ne 1 -or $info.mod.id -ne 'agentcraft' -or $info.loader.id -ne 'fabric' -or ($info.t3Protocols -join ',') -ne '1,2') { throw 'Unsupported T3Craft package.' }
        foreach ($fileName in @($info.mod.file, $info.fabricApi.file)) {
            if ($fileName -notmatch '^[A-Za-z0-9_.+\-]+\.jar$') { throw 'Unsafe package filename.' }
        }
        $allowed = @('mmc-pack.json', 'instance.cfg', 'NOTES.txt', 'compatibility.json', "minecraft/mods/$($info.mod.file)", "minecraft/mods/$($info.fabricApi.file)")
        if ($info.worldTemplate) {
            if ((Get-ZipHash $archive 'world-template.json') -ne $info.worldTemplate.manifestSha256) { throw 'World manifest checksum mismatch.' }
            $template = Read-ZipJson $archive 'world-template.json'
            if ($template.schemaVersion -ne 1 -or $template.minecraft -ne $info.minecraft -or $template.worldName -ne 'AgentCraft HQ' -or $template.layout -ne 'modern' -or $template.sourceSavedAt -ne $info.worldTemplate.sourceSavedAt -or $template.layoutRevision -ne $info.worldTemplate.layoutRevision) { throw 'World template disagrees with the release target.' }
            $worldFiles = @($template.files.PSObject.Properties)
            $required = @('level.dat', 'agentcraft-anchors.json', 'agentcraft-hq-plan.dat', 'agentcraft-world.json', 'data/minecraft/world_gen_settings.dat')
            if (@($required | Where-Object { $_ -notin $worldFiles.Name }).Count -or -not @($worldFiles | Where-Object Name -Match '/region/').Count) { throw 'Incomplete world template.' }
            $allowed += 'world-template.json'
            foreach ($file in $worldFiles) {
                $static = @('level.dat', 'agentcraft-anchors.json', 'agentcraft-hq-plan.dat', 'agentcraft-world.json', 'data/fabric/registry_entries.dat', 'data/minecraft/game_rules.dat', 'data/minecraft/weather.dat', 'data/minecraft/world_clocks.dat', 'data/minecraft/world_gen_settings.dat')
                if (($file.Name -cnotin $static -and $file.Name -cnotmatch '^dimensions/minecraft/(overworld|the_nether|the_end)/data/minecraft/world_border\.dat$' -and $file.Name -cnotmatch '^dimensions/minecraft/(overworld|the_nether|the_end)/(region|poi)/r\.-?\d+\.-?\d+\.mca$') -or $file.Value -cnotmatch '^[a-f0-9]{64}$') { throw 'World template contains a private or unsafe file.' }
                $name = 'minecraft/saves/AgentCraft HQ/' + $file.Name
                if ((Get-ZipHash $archive $name) -ne $file.Value) { throw 'World template file checksum mismatch.' }
                $allowed += $name
            }
        }
        $entries = @($archive.Entries | ForEach-Object { $_.FullName })
        if ($entries.Count -ne $allowed.Count -or @($entries | Select-Object -Unique).Count -ne $allowed.Count -or @($entries | Where-Object { $_ -notin $allowed }).Count) { throw 'Package contains unexpected or duplicate files.' }
        $pack = Read-ZipJson $archive 'mmc-pack.json'
        if ($pack.formatVersion -ne 1 -or @($pack.components).Count -ne 2 -or -not ($pack.components | Where-Object { $_.uid -eq 'net.minecraft' -and $_.version -eq $info.minecraft }) -or -not ($pack.components | Where-Object { $_.uid -eq 'net.fabricmc.fabric-loader' -and $_.version -eq $info.loader.version })) { throw 'Package version declarations disagree.' }
        if (-not $InstanceId) { $InstanceId = "T3Craft-$($info.minecraft)-Native" }
        if ($InstanceId -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]*$') { throw 'Unsafe instance identifier.' }
        $instanceDir = 'instances'
        $cfg = Join-Path $PrismData 'prismlauncher.cfg'
        if (Test-Path -LiteralPath $cfg) {
            $line = Get-Content -LiteralPath $cfg | Where-Object { $_ -match '^InstanceDir=' } | Select-Object -First 1
            if ($line) { $instanceDir = $line.Substring(12) }
        }
        $instancesRoot = [IO.Path]::GetFullPath((Join-Path $PrismData $instanceDir))
        $destination = [IO.Path]::GetFullPath((Join-Path $instancesRoot $InstanceId))
        if (-not $destination.StartsWith($instancesRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Instance path escaped Prism.' }
        $existing = Test-Path -LiteralPath $destination
        if ($existing -and $NewInstance) { throw 'That instance already exists. Use a different InstanceId for a new installation.' }
        $game = Join-Path $destination 'minecraft'
        if ($existing) {
            if (Test-Path -LiteralPath (Join-Path $destination '.minecraft')) { $game = Join-Path $destination '.minecraft' }
            $installedPack = Get-Content -LiteralPath (Join-Path $destination 'mmc-pack.json') -Raw | ConvertFrom-Json
            if (-not ($installedPack.components | Where-Object { $_.uid -eq 'net.minecraft' -and $_.version -eq $info.minecraft }) -or -not ($installedPack.components | Where-Object { $_.uid -eq 'net.fabricmc.fabric-loader' -and [version]$_.version -ge [version]$info.loader.version })) { throw 'Existing instance needs the matching Minecraft version and supported Fabric loader. Create a separate instance; your world has not been changed.' }
            $mods = Join-Path $game 'mods'
            $oldMods = @(Get-ManagedMods $mods $info.mod.version)
            $busy = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Replace('/', '\').IndexOf($game, [StringComparison]::OrdinalIgnoreCase) -ge 0 }
            if ($busy) { throw 'Close this Minecraft instance before updating.' }
            $saves = Join-Path $game 'saves'
            if (Test-Path -LiteralPath $saves) {
                foreach ($lockFile in Get-ChildItem -LiteralPath $saves -Filter session.lock -File -Recurse) {
                    try { $locks += [IO.File]::Open($lockFile.FullName, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read) }
                    catch { throw 'A saved world is in use. Close Minecraft before updating.' }
                }
            }
        }
        $prismExe = Join-Path $env:LOCALAPPDATA 'Programs\PrismLauncher\prismlauncher.exe'
        if (Test-Path -LiteralPath (Join-Path $PrismData 'prismlauncher.exe')) { $prismExe = Join-Path $PrismData 'prismlauncher.exe' }
        if ($Launch -and -not (Test-Path -LiteralPath $prismExe)) {
            $command = Get-Command prismlauncher -ErrorAction SilentlyContinue
            if (-not $command) { throw 'Install Prism Launcher from https://prismlauncher.org/download/ first.' }
            $prismExe = $command.Source
        }
        $action = if ($existing) { 'Verify a backup of every save and current mods/config, then update only T3Craft and Fabric API' } else { 'Create a new T3Craft instance with Prism-managed Java' }
        if (-not $PSCmdlet.ShouldProcess($destination, $action)) { return }
        if (-not $temporary) { $temporary = Join-Path ([IO.Path]::GetTempPath()) ('t3craft-install-' + [guid]::NewGuid().ToString('N')); New-Item -ItemType Directory -Path $temporary | Out-Null }
        $stage = Join-Path $temporary 'instance'
        New-Item -ItemType Directory -Path $stage | Out-Null
        foreach ($entry in $archive.Entries) {
            $target = Join-Path $stage $entry.FullName
            New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
        }
        Assert-Hash (Join-Path $stage "minecraft/mods/$($info.mod.file)") $info.mod.sha256
        Assert-Hash (Join-Path $stage "minecraft/mods/$($info.fabricApi.file)") $info.fabricApi.sha256
        foreach ($artifact in @(@{ name=$info.mod.file; id='agentcraft'; version=$info.mod.version }, @{ name=$info.fabricApi.file; id='fabric-api'; version=$info.fabricApi.version })) {
            $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $stage "minecraft/mods/$($artifact.name)"))
            try {
                $meta = Read-ZipJson $jar 'fabric.mod.json'
                if ($meta.id -ne $artifact.id -or $meta.version -ne $artifact.version) { throw 'Bundled mod metadata does not match the release.' }
                if ($artifact.id -eq 'agentcraft' -and ($meta.depends.minecraft -ne $info.minecraft -or $meta.depends.fabricloader -ne ">=$($info.loader.version)" -or $meta.depends.java -ne ">=$($info.javaMinimum)" -or $meta.depends.'fabric-api' -ne ">=$($info.fabricApi.version)")) { throw 'Bundled mod requirements disagree with the package.' }
            } finally { $jar.Dispose() }
        }
        $settings = Get-Content -LiteralPath (Join-Path $stage 'instance.cfg') -Raw
        if ($settings -match '(?m)^JavaPath=' -or $settings -notmatch '(?m)^OverrideJavaLocation=false\r?$' -or $settings -notmatch '(?m)^AutomaticJava=true\r?$') { throw 'Release contains a machine-specific Java setting.' }
        Write-Output "T3Craft $($info.mod.version) / Minecraft $($info.minecraft) / Fabric $($info.loader.version) / Java $($info.javaMinimum)+"
        if ($existing) {
            $backup = Join-Path $PrismData ("backups/t3craft/$InstanceId/" + (Get-Date -Format yyyyMMdd-HHmmss) + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
            New-Item -ItemType Directory -Path $backup | Out-Null
            foreach ($name in @('saves', 'mods', 'config')) {
                $source = Join-Path $game $name
                if (Test-Path -LiteralPath $source) { Copy-Verified $source $backup }
            }
            foreach ($name in @('instance.cfg', 'mmc-pack.json')) { Copy-Verified (Join-Path $destination $name) (Join-Path $backup $name) }
            New-Item -ItemType Directory -Force -Path $mods | Out-Null
            try {
                foreach ($file in $oldMods) { Remove-Item -LiteralPath $file.FullName }
                Copy-Item -LiteralPath (Join-Path $stage "minecraft/mods/$($info.mod.file)"), (Join-Path $stage "minecraft/mods/$($info.fabricApi.file)") -Destination $mods
            } catch {
                foreach ($name in @($info.mod.file, $info.fabricApi.file)) { $failed = Join-Path $mods $name; if (Test-Path -LiteralPath $failed) { Remove-Item -LiteralPath $failed } }
                foreach ($file in $oldMods) { Copy-Item -LiteralPath (Join-Path $backup "mods/$($file.Name)") -Destination $file.FullName -Force }
                throw
            }
            Write-Output "Verified backup: $backup"
        } else { New-Item -ItemType Directory -Force -Path $instancesRoot | Out-Null; Copy-Item -LiteralPath $stage -Destination $destination -Recurse }
        $helpers = Join-Path $game 'config/t3craft-installer'
        New-Item -ItemType Directory -Force -Path $helpers | Out-Null
        foreach ($name in @('install-release.ps1', 'update-release-after-close.ps1')) {
            $source = Join-Path $PSScriptRoot $name
            $target = Join-Path $helpers $name
            if ([IO.Path]::GetFullPath($source) -ne [IO.Path]::GetFullPath($target)) { Copy-Item -LiteralPath $source -Destination $target -Force }
        }
        $updater = @{ updaterScript = (Join-Path $helpers 'update-release-after-close.ps1'); prismData = $PrismData; instanceId = $InstanceId } | ConvertTo-Json
        [IO.File]::WriteAllText((Join-Path $game 'config/t3craft-updater.json'), $updater, [Text.UTF8Encoding]::new($false))
        Write-Output 'In Prism Settings > Java, enable Autodetect Java version and Auto-download Mojang Java, or select the required Java version manually.'
        Write-Output "Ready: $InstanceId. Sign in through Prism, keep T3 Code running, and paste a pairing link in the game's Connections screen."
    } finally { $archive.Dispose() }
    foreach ($lock in $locks) { $lock.Dispose() }; $locks = @()
    if ($Launch) { $quotedData = '"' + ($PrismData -replace '(\\+)$', '$1$1') + '"'; Start-Process -FilePath $prismExe -ArgumentList @('--dir', $quotedData, '--launch', $InstanceId) -WindowStyle Hidden | Out-Null }
} finally {
    foreach ($lock in $locks) { $lock.Dispose() }
    if ($temporary) {
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        $resolved = [IO.Path]::GetFullPath($temporary)
        if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -match '^t3craft-install-[a-f0-9]{32}$') { Remove-Item -LiteralPath $resolved -Recurse -Force }
    }
}
