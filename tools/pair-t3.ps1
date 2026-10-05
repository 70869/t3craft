[CmdletBinding()]
param(
    [string]$ConfigPath = "$env:APPDATA\PrismLauncher\instances\T3Craft-26.3-Native\minecraft\config\t3craft.json",
    [string]$T3Home = "$env:USERPROFILE\.t3",
    [string]$T3Executable,
    [string]$PairingLink
)
$ErrorActionPreference = 'Stop'
if (-not $PairingLink) {
    if (-not $T3Executable) {
        $T3Executable = @("$env:LOCALAPPDATA\Programs\t3code\T3 Code (Nightly).exe", "$env:LOCALAPPDATA\Programs\t3code\T3 Code.exe") | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (-not $T3Executable) { throw 'Open T3 Settings > Connections, create a pairing link, and pass -PairingLink.' }
    $runtimePath = Join-Path $T3Home 'userdata\server-runtime.json'
    if (-not (Test-Path -LiteralPath $runtimePath)) { throw 'Open T3 Code before pairing.' }
    $runtime = Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json
    $base = [Uri]$runtime.origin
    if ($base.Host -notin @('127.0.0.1', 'localhost', '::1')) { throw 'Automatic pairing requires a local T3 environment.' }
    $descriptor = Invoke-RestMethod -Uri "$($runtime.origin)/.well-known/t3/environment" -TimeoutSec 5
    if ($descriptor.orchestrationProtocolVersion -ne 2) { throw 'Use T3 Code with orchestration protocol 2.' }
    $entry = Join-Path (Split-Path $T3Executable) 'resources\server.asar\apps\server\dist\bin.mjs'
    $priorElectron = $env:ELECTRON_RUN_AS_NODE
    try {
        $env:ELECTRON_RUN_AS_NODE = '1'
        # Supported T3 CLI issuance: create a new client grant, never recover another client's token.
        $taskOutput = [IO.Path]::GetTempFileName()
        $taskError = [IO.Path]::GetTempFileName()
        try {
            $cli = Start-Process -FilePath $T3Executable -ArgumentList @(('"{0}"' -f $entry), 'auth', 'pairing', 'create', '--base-dir', ('"{0}"' -f $T3Home), '--label', 'T3Craft', '--ttl', '5m', '--json') -WindowStyle Hidden -RedirectStandardOutput $taskOutput -RedirectStandardError $taskError -Wait -PassThru
            if ($cli.ExitCode -ne 0) { throw "T3 pairing CLI failed with exit $($cli.ExitCode). Check the installed T3 version." }
            $credential = Get-Content -LiteralPath $taskOutput -Raw | ConvertFrom-Json
        } finally {
            Remove-Item -LiteralPath $taskOutput, $taskError -Force
        }
        if (-not $credential.credential) { throw 'T3 did not return a pairing credential.' }
        $PairingLink = "$($runtime.origin)/pair#token=$([Uri]::EscapeDataString($credential.credential))"
    } finally { $env:ELECTRON_RUN_AS_NODE = $priorElectron }
}
$link = [Uri]$PairingLink
$fragment = $link.Fragment.TrimStart('#')
$tokenField = $fragment.Split('&') | Where-Object { $_.StartsWith('token=') } | Select-Object -First 1
if (-not $tokenField -or $link.Scheme -notin @('http', 'https') -or $link.UserInfo) { throw 'Use the complete T3 pairing URL, including #token=.' }
$origin = $link.GetLeftPart([UriPartial]::Authority)
if ($link.Scheme -eq 'http' -and $link.Host -notin @('127.0.0.1', 'localhost', '::1')) { throw 'Remote pairing requires HTTPS.' }
$scope = 'orchestration:read orchestration:operate'
$form = @{
    grant_type = 'urn:ietf:params:oauth:grant-type:token-exchange'
    subject_token = [Uri]::UnescapeDataString($tokenField.Substring(6))
    subject_token_type = 'urn:t3:params:oauth:token-type:environment-bootstrap'
    requested_token_type = 'urn:ietf:params:oauth:token-type:access_token'
    scope = $scope; client_label = 'T3Craft'; client_device_type = 'desktop'
}
# T3's form decoder expects %20, not +, for spaces.
$body = ($form.GetEnumerator() | ForEach-Object { [Uri]::EscapeDataString($_.Key) + '=' + [Uri]::EscapeDataString($_.Value) }) -join '&'
try { $session = Invoke-RestMethod -Uri "$origin/oauth/token" -Method Post -ContentType 'application/x-www-form-urlencoded' -Body $body -TimeoutSec 15 } catch { throw 'T3 pairing failed. Create a fresh link in Settings > Connections.' }
if (-not $session.access_token) { throw 'T3 returned an invalid pairing response.' }
$descriptor = Invoke-RestMethod -Uri "$origin/.well-known/t3/environment" -TimeoutSec 5
$config = @{ environments = @(); threadId = $null; books = $false; agentCommands = $false }
if (Test-Path -LiteralPath $ConfigPath) {
    $existing = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    foreach ($property in $existing.PSObject.Properties) { $config[$property.Name] = $property.Value }
}
$config.environments = @($config.environments | Where-Object { $_.baseUrl.TrimEnd('/') -ne $origin }) + @(@{ label = $descriptor.label; baseUrl = $origin; accessToken = $session.access_token })
$directory = Split-Path $ConfigPath
New-Item -ItemType Directory -Force $directory | Out-Null
$temp = Join-Path $directory ([IO.Path]::GetRandomFileName())
[IO.File]::WriteAllText($temp, ($config | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = [Security.AccessControl.FileSecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
    Set-Acl -LiteralPath $temp -AclObject $acl
}
Move-Item -LiteralPath $temp -Destination $ConfigPath -Force
Write-Output "Paired T3Craft with $($descriptor.label). Saved private client configuration. No chats were created or messages sent."
