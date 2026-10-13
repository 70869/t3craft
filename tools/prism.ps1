<#
.SYNOPSIS
  Package the built mod as a Prism import ZIP; optionally install a NEW dedicated instance.
.EXAMPLE
  tools\prism.ps1 -Install
#>
[CmdletBinding()]
param(
    [string]$PrismData = "$env:APPDATA\PrismLauncher",
    [string]$JavaHome,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$InstanceId = 'T3Craft-26.3-Native',
    [switch]$Install,
    [switch]$Update
)
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 does not load the archive mode enum through the
# FileSystem assembly alone; load both assemblies before opening the ZIP.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$props = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $taskRoot 'mod\gradle.properties')))
$modJar = Join-Path $taskRoot "mod\build\libs\$($props.archives_base_name)-$($props.mod_version).jar"
if (-not (Test-Path -LiteralPath $modJar)) { throw "Build the mod first: mod\gradlew.bat build (missing $modJar)" }
if (-not $JavaHome) {
    $taskJdk = Get-ChildItem -LiteralPath (Join-Path $taskRoot '.local\java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskJdk) { $JavaHome = $taskJdk.FullName } else { $JavaHome = $env:JAVA_HOME }
}
$javaExe = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) { throw 'Supply -JavaHome pointing at a Java 25+ JDK.' }
$javaStartInfo = [Diagnostics.ProcessStartInfo]::new()
$javaStartInfo.FileName = $javaExe
$javaStartInfo.Arguments = '-version'
$javaStartInfo.UseShellExecute = $false
$javaStartInfo.RedirectStandardOutput = $true
$javaStartInfo.RedirectStandardError = $true
$javaProcess = [Diagnostics.Process]::new()
try {
    $javaProcess.StartInfo = $javaStartInfo
    if (-not $javaProcess.Start()) { throw 'Could not start the selected Java executable.' }
    $javaStdout = $javaProcess.StandardOutput.ReadToEnd()
    $javaStderr = $javaProcess.StandardError.ReadToEnd()
    $javaProcess.WaitForExit()
    $javaExitCode = $javaProcess.ExitCode
    $javaVersion = "$javaStderr`n$javaStdout"
} finally {
    $javaProcess.Dispose()
}
if ($javaExitCode -ne 0 -or $javaVersion -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 25) { throw 'This instance requires Java 25+.' }
$stage = Join-Path $taskRoot "artifacts\prism\$InstanceId"
$gameDir = Join-Path $stage 'minecraft'
New-Item -ItemType Directory -Force (Join-Path $gameDir 'mods') | Out-Null
Copy-Item -LiteralPath $modJar -Destination (Join-Path $gameDir 'mods') -Force
$apiVersion = $props.fabric_api_version
$apiName = "fabric-api-$apiVersion.jar"
$apiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$apiVersion/$apiName"
$apiPath = Join-Path $gameDir "mods\$apiName"
Invoke-WebRequest -UseBasicParsing -Uri $apiUrl -OutFile $apiPath
$checksumContent = (Invoke-WebRequest -UseBasicParsing -Uri "$apiUrl.sha1").Content
$expectedSha1 = $(if ($checksumContent -is [string]) { $checksumContent } else { [Text.Encoding]::UTF8.GetString($checksumContent) }).Trim()
if ((Get-FileHash -LiteralPath $apiPath -Algorithm SHA1).Hash.ToLowerInvariant() -ne $expectedSha1.ToLowerInvariant()) { throw 'Fabric API checksum mismatch.' }
$utf8 = [Text.UTF8Encoding]::new($false)
$pack = @{ formatVersion = 1; components = @(
    @{ uid = 'net.minecraft'; version = $props.minecraft_version; important = $true },
    @{ uid = 'net.fabricmc.fabric-loader'; version = $props.loader_version }
) }
[IO.File]::WriteAllText((Join-Path $stage 'mmc-pack.json'), ($pack | ConvertTo-Json -Depth 5), $utf8)
$prismJava = (Join-Path $JavaHome 'bin\javaw.exe').Replace('\', '/')
$settings = @"
[General]
ConfigVersion=1.2
InstanceType=OneSix
name=T3Craft $($props.minecraft_version) Native
iconKey=crafting_table
OverrideJavaLocation=true
OverrideJavaArgs=true
AutomaticJava=false
JavaPath=$prismJava
JvmArgs=-Djava.net.preferIPv4Stack=true -Dagentcraft.dev=0 -Dagentcraft.mute=0 -Dagentcraft.focus=1 -Dagentcraft.autoworld=1
OverrideMemory=true
MinMemAlloc=1024
MaxMemAlloc=4096
"@
[IO.File]::WriteAllText((Join-Path $stage 'instance.cfg'), $settings, $utf8)
[IO.File]::WriteAllText((Join-Path $stage 'NOTES.txt'), 'No Node or Foreman process required. Open T3 Code, launch Minecraft, and press ` to pair in Connections. Existing T3 chats are the only history source. New chat is an explicit action. See docs/USER-GUIDE.md.', $utf8)
$zip = Join-Path $taskRoot "artifacts\prism\$InstanceId.zip"
$portable = $settings.Replace('OverrideJavaLocation=true', 'OverrideJavaLocation=false').Replace('AutomaticJava=false', 'AutomaticJava=true') -replace '(?m)^JavaPath=.*\r?\n', ''
[IO.File]::WriteAllText((Join-Path $stage 'instance.cfg'), $portable, $utf8)
$compatibility = @{ schemaVersion = 1; minecraft = $props.minecraft_version; javaMinimum = [int]$props.java_version;
    loader = @{ id = 'fabric'; version = $props.loader_version }; t3Protocols = @(1, 2);
    mod = @{ id = 'agentcraft'; version = $props.mod_version; file = (Split-Path $modJar -Leaf); sha256 = (Get-FileHash -LiteralPath $modJar -Algorithm SHA256).Hash.ToLowerInvariant() };
    fabricApi = @{ version = $apiVersion; file = $apiName; sha256 = (Get-FileHash -LiteralPath $apiPath -Algorithm SHA256).Hash.ToLowerInvariant() }
}
[IO.File]::WriteAllText((Join-Path $stage 'compatibility.json'), ($compatibility | ConvertTo-Json -Depth 5), $utf8)
# Package an explicit allowlist, so an old staging directory cannot leak configs/saves.
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip }
$archive = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($name in @('mmc-pack.json', 'instance.cfg', 'NOTES.txt', 'compatibility.json')) {
        $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $stage $name), $name)
    }
    $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $modJar, ('minecraft/mods/' + (Split-Path $modJar -Leaf)))
    $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $apiPath, ('minecraft/mods/' + $apiName))
} finally { $archive.Dispose() }
[IO.File]::WriteAllText((Join-Path $stage 'instance.cfg'), $settings, $utf8)
Write-Output "Import ZIP: $zip"
if ($Install) {
    $prismCfg = Join-Path $PrismData 'prismlauncher.cfg'
    $instanceDir = 'instances'
    if (Test-Path -LiteralPath $prismCfg) {
        $line = Get-Content -LiteralPath $prismCfg | Where-Object { $_ -match '^InstanceDir=' } | Select-Object -First 1
        if ($line) { $instanceDir = $line.Substring('InstanceDir='.Length) }
    }
    $instancesRoot = [IO.Path]::GetFullPath((Join-Path $PrismData $instanceDir))
    $destination = [IO.Path]::GetFullPath((Join-Path $instancesRoot $InstanceId))
    if (-not $destination.StartsWith($instancesRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Instance path escaped the Prism instances directory.' }
    if (Test-Path -LiteralPath $destination) {
        if (-not $Update) { throw "Instance already exists: $destination. Use -Update to replace only T3Craft's mod jars." }
        $existingPack = Get-Content -LiteralPath (Join-Path $destination 'mmc-pack.json') -Raw | ConvertFrom-Json
        if (-not ($existingPack.components | Where-Object { $_.uid -eq 'net.minecraft' -and $_.version -eq $props.minecraft_version }) -or -not ($existingPack.components | Where-Object { $_.uid -eq 'net.fabricmc.fabric-loader' -and [version]$_.version -ge [version]$props.loader_version })) { throw 'This instance does not match the required Minecraft/Fabric profile.' }
        $nativeGame = Join-Path $destination 'minecraft'
        if (Test-Path -LiteralPath (Join-Path $destination '.minecraft')) { $nativeGame = Join-Path $destination '.minecraft' }
        $busy = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Replace('/', '\').IndexOf($nativeGame, [StringComparison]::OrdinalIgnoreCase) -ge 0 }
        if ($busy) { throw 'Close this Minecraft instance before updating its mod jar.' }
        $nativeMods = Join-Path $nativeGame 'mods'
        foreach ($old in Get-ChildItem -LiteralPath $nativeMods -File | Where-Object Name -match '^(t3craft-[0-9]|agentcraft-[0-9]|fabric-api-)') { Remove-Item -LiteralPath $old.FullName }
        Copy-Item -LiteralPath $modJar, $apiPath -Destination $nativeMods -Force
        $installedCfg = Join-Path $destination 'instance.cfg'
        $installedSettings = [IO.File]::ReadAllText($installedCfg)
        $oldDefaultName = 'name=T3Craft ' + $props.minecraft_version
        if ($installedSettings -match ('(?m)^' + [regex]::Escape($oldDefaultName) + '\r?$')) {
            $installedSettings = $installedSettings -replace ('(?m)^' + [regex]::Escape($oldDefaultName) + '\r?$'), ($oldDefaultName + ' Native')
            [IO.File]::WriteAllText($installedCfg, $installedSettings, $utf8)
        }
    } else {
        New-Item -ItemType Directory -Force -Path (Join-Path $destination 'minecraft/mods') | Out-Null
        foreach ($name in @('mmc-pack.json', 'instance.cfg', 'NOTES.txt', 'compatibility.json')) { Copy-Item -LiteralPath (Join-Path $stage $name) -Destination (Join-Path $destination $name) }
        Copy-Item -LiteralPath $modJar, $apiPath -Destination (Join-Path $destination 'minecraft/mods')
    }
    Write-Output "Prism instance ready: $destination"
}
