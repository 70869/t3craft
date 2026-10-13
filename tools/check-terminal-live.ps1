[CmdletBinding()]
param([string]$T3Executable)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if (-not $T3Executable) {
    $T3Executable = @("$env:LOCALAPPDATA\Programs\t3code\T3 Code (Nightly).exe", "$env:LOCALAPPDATA\Programs\t3code\T3 Code.exe") | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $T3Executable) { throw 'Install T3 Code to run the isolated real-shell check.' }
$entry = Join-Path (Split-Path $T3Executable) 'resources\server.asar\apps\server\dist\bin.mjs'
if (-not (Test-Path -LiteralPath (Join-Path (Split-Path $T3Executable) 'resources\server.asar'))) { throw 'This T3 package does not include the server CLI.' }
$qaRoot = Join-Path $root ('.local\terminal-live-qa\' + [Guid]::NewGuid().ToString('N'))
$workspace = Join-Path $qaRoot 'workspace'
$serverHome = Join-Path $qaRoot 'home'
New-Item -ItemType Directory -Force $workspace | Out-Null
$port = $null
foreach ($candidate in 29316..29399) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $candidate)
    try { $listener.Start(); $port = $candidate; break } catch {} finally { $listener.Stop() }
}
if (-not $port) { throw 'No disposable terminal QA port is free.' }
$origin = "http://127.0.0.1:$port"
[IO.File]::WriteAllText((Join-Path $qaRoot 't3craft-terminal-qa.marker'), $origin)
$grant = Join-Path $qaRoot 'grant.json'
$priorElectron = $env:ELECTRON_RUN_AS_NODE
$priorJava = $env:JAVA_HOME
$priorGradle = $env:GRADLE_USER_HOME
$server = $null
try {
    $env:ELECTRON_RUN_AS_NODE = '1'
    $server = Start-Process -FilePath $T3Executable -ArgumentList @(('"{0}"' -f $entry), 'start', '--mode', 'web', '--host', '127.0.0.1', '--port', $port, '--base-dir', ('"{0}"' -f $serverHome), '--no-browser', ('"{0}"' -f $workspace)) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $qaRoot 'server-out.txt') -RedirectStandardError (Join-Path $qaRoot 'server-error.txt') -PassThru
    $ready = $false
    for ($i = 0; $i -lt 100; $i++) {
        if ($server.HasExited) { throw "Isolated T3 exited with code $($server.ExitCode)." }
        try { $metadata = Invoke-RestMethod "$origin/.well-known/t3/environment" -TimeoutSec 1; $ready = $true; break } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready -or $metadata.orchestrationProtocolVersion -ne 2) { throw 'Isolated protocol-2 server did not start.' }
    $cli = Start-Process -FilePath $T3Executable -ArgumentList @(('"{0}"' -f $entry), 'auth', 'pairing', 'create', '--base-dir', ('"{0}"' -f $serverHome), '--label', 'T3Craft-QA', '--ttl', '5m', '--json') -WindowStyle Hidden -RedirectStandardOutput $grant -RedirectStandardError (Join-Path $qaRoot 'grant-error.txt') -Wait -PassThru
    if ($cli.ExitCode -ne 0) { throw 'Isolated pairing grant failed.' }
    $jdk = Get-ChildItem (Join-Path $root '.local\java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    $env:GRADLE_USER_HOME = Join-Path $root '.gradle-home'
    & (Join-Path $root 'mod\gradlew.bat') -p (Join-Path $root 'mod') terminalLiveCheck "-PterminalGrant=$grant" "-PterminalOrigin=$origin" "-PterminalWorkspace=$workspace" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Real isolated terminal check failed.' }
    Write-Output 'PASS disposable T3 server stopped; the active desktop workspace was untouched.'
} finally {
    if ($server -and -not $server.HasExited) { Stop-Process -Id $server.Id -Force }
    if (Test-Path -LiteralPath $grant) { Remove-Item -LiteralPath $grant -Force }
    $env:ELECTRON_RUN_AS_NODE = $priorElectron
    $env:JAVA_HOME = $priorJava
    $env:GRADLE_USER_HOME = $priorGradle
}
