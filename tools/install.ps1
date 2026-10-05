[CmdletBinding()]
param(
    [string]$PrismData = "$env:APPDATA\PrismLauncher",
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$InstanceId = 'T3Craft-26.3-Native',
    [switch]$Launch,
    [switch]$SkipPair,
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$PrismData = [IO.Path]::GetFullPath($PrismData)
$prismExe = "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe"
if (Test-Path -LiteralPath (Join-Path $PrismData 'prismlauncher.exe')) { $prismExe = Join-Path $PrismData 'prismlauncher.exe' }
if (-not (Test-Path -LiteralPath $prismExe)) {
    $prismCommand = Get-Command prismlauncher -ErrorAction SilentlyContinue
    if ($prismCommand) { $prismExe = $prismCommand.Source }
    elseif ($Launch) { throw 'Install Prism Launcher from https://prismlauncher.org first.' }
}
$javaDir = Join-Path $taskRoot '.local\java'
$taskJdk = Get-ChildItem -LiteralPath $javaDir -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (-not $taskJdk) {
    Write-Output 'Downloading Java 25 from Adoptium (checksum verified)…'
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $assets[0].binary.package
    New-Item -ItemType Directory -Force $javaDir | Out-Null
    $archive = Join-Path $javaDir $package.name
    Invoke-WebRequest -Uri $package.link -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum.ToLowerInvariant()) { throw 'Java download checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $javaDir -Force
    Remove-Item -LiteralPath $archive
    $taskJdk = Get-ChildItem -LiteralPath $javaDir -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
}
$props = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $taskRoot 'mod\gradle.properties')))
$jar = Join-Path $taskRoot "mod\build\libs\$($props.archives_base_name)-$($props.mod_version).jar"
$sourceChanged = $false
if (Test-Path -LiteralPath $jar) {
    $buildInputs = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'mod\src') -File -Recurse)
    $buildInputs += Get-Item -LiteralPath (Join-Path $taskRoot 'mod\build.gradle'), (Join-Path $taskRoot 'mod\gradle.properties'), (Join-Path $taskRoot 'LICENSE'), (Join-Path $taskRoot 'THIRD-PARTY-NOTICES.md')
    $buildInputs += Get-ChildItem -LiteralPath (Join-Path $taskRoot 'licenses') -File
    $sourceChanged = [bool]($buildInputs | Where-Object LastWriteTimeUtc -gt (Get-Item -LiteralPath $jar).LastWriteTimeUtc | Select-Object -First 1)
}
if ($Build -or $sourceChanged -or -not (Test-Path -LiteralPath $jar)) {
    $env:JAVA_HOME = $taskJdk.FullName
    $env:GRADLE_USER_HOME = Join-Path $taskRoot '.gradle-home'
    $env:JAVA_OPTS = '-Djava.net.preferIPv4Stack=true'
    & (Join-Path $taskRoot 'mod\gradlew.bat') -p (Join-Path $taskRoot 'mod') build
    if ($LASTEXITCODE -ne 0) { throw 'Mod build failed.' }
}
& (Join-Path $PSScriptRoot 'prism.ps1') -Install -Update -PrismData $PrismData -InstanceId $InstanceId -JavaHome $taskJdk.FullName
$instanceDir = 'instances'
$prismConfig = Join-Path $PrismData 'prismlauncher.cfg'
if (Test-Path -LiteralPath $prismConfig) { $line = Get-Content -LiteralPath $prismConfig | Where-Object { $_ -match '^InstanceDir=' } | Select-Object -First 1; if ($line) { $instanceDir = $line.Substring(12) } }
$config = Join-Path (Join-Path (Join-Path $PrismData $instanceDir) $InstanceId) 'minecraft\config\t3craft.json'
if (-not $SkipPair -and -not (Test-Path -LiteralPath $config)) {
    try { & (Join-Path $PSScriptRoot 'pair-t3.ps1') -ConfigPath $config }
    catch { Write-Warning 'Automatic local pairing could not finish. Press ` in Minecraft and paste a fresh link from T3 Settings > Connections.' }
}
Write-Output "Ready: $InstanceId. Open T3 Code while playing. Press the backtick key for your chats."
if ($Launch) {
    # Start-Process joins arguments on Windows; preserve spaces and trailing backslashes in the data path.
    $quotedPrismData = '"' + ($PrismData -replace '(\\+)$', '$1$1') + '"'
    Start-Process -FilePath $prismExe -ArgumentList @('--dir', $quotedPrismData, '--launch', $InstanceId) -WindowStyle Hidden | Out-Null
}
