param(
    [ValidateRange(1, 3600)][int]$StartupTimeoutSeconds = 180,
    [ValidateRange(1, 3600)][int]$CheckTimeoutSeconds = 120,
    [ValidateRange(1, 600)][int]$ShutdownTimeoutSeconds = 45,
    [ValidateRange(1, 3600)][int]$BlueMapTimeoutSeconds = 300,
    [string]$Memory = "4G",
    [string]$ServerDirectory = "",
    [ValidateRange(1024,65535)][int]$GamePort = 25565,
    [switch]$UpdatePermissions,
    [string]$ClientScript = "",
    [ValidateRange(1,3600)][int]$ClientTimeoutSeconds = 900
)
$ErrorActionPreference = "Stop"
$Utf8NoBom = New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom
$RootDir = Split-Path -Parent $PSScriptRoot
$JavaExe = Join-Path $RootDir "runtime\jdk-21\bin\java.exe"
$ServerDir = if ([string]::IsNullOrWhiteSpace($ServerDirectory)) { Join-Path $RootDir "server" } else { [IO.Path]::GetFullPath($ServerDirectory) }
$LatestLog = Join-Path $ServerDir "logs\latest.log"
$ServerLease = $null
$Proc = $null
$Started = $false
$StopSent = $false
$ForcedKill = $false
$Ready = $false
$ChecksPassed = $false
$Failure = $null
$OutputTask = $null
$ErrorTask = $null
$ExitCode = $null
function Test-GamePortListening {
    try {
        foreach ($Endpoint in [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()) {
            if ($Endpoint.Port -eq $GamePort) { return $true }
        }
        return $false
    } catch {
        # Sandboxes may deny network-table queries. Exclusive bind probes need no connection or admin rights.
        foreach ($Address in @([Net.IPAddress]::Any, [Net.IPAddress]::IPv6Any)) {
            $Socket = New-Object Net.Sockets.Socket($Address.AddressFamily, [Net.Sockets.SocketType]::Stream, [Net.Sockets.ProtocolType]::Tcp)
            try {
                $Socket.ExclusiveAddressUse = $true
                $Socket.Bind((New-Object Net.IPEndPoint($Address, $GamePort)))
            } catch {
                $Cause = $_.Exception.GetBaseException()
                if ($Cause -is [Net.Sockets.SocketException]) {
                    if ($Cause.SocketErrorCode -in @([Net.Sockets.SocketError]::AddressAlreadyInUse, [Net.Sockets.SocketError]::AccessDenied)) { return $true }
                    if ($Cause.SocketErrorCode -in @([Net.Sockets.SocketError]::AddressFamilyNotSupported, [Net.Sockets.SocketError]::NetworkUnreachable, [Net.Sockets.SocketError]::AddressNotAvailable)) { continue }
                }
                throw
            } finally { $Socket.Dispose() }
        }
        return $false
    }
}
function Test-VerificationErrors([string]$Log) {
    # Restricted Windows environments cannot expose hardware performance counters.
    # Keep every application error/stack trace actionable; exclude only OSHI WARN lines.
    $Relevant = (($Log -split "\r?\n") | Where-Object { $_ -notmatch '\[oshi\.[^]]+\]' }) -join [Environment]::NewLine
    return $Relevant -match '(?im)\bERROR\b|\bSEVERE\b|^[a-zA-Z_$][\w.$]*(?:Exception|Error)(?::|\s)|generated an exception|CORE_RUNTIME_CHECKS FAILED'
}
function Read-VerificationLog {
    if (Test-Path -LiteralPath $LatestLog -PathType Leaf) { return [string](Get-Content -LiteralPath $LatestLog -Raw -Encoding UTF8 -ErrorAction SilentlyContinue) }
    return ""
}
try {
    if (-not (Test-Path -LiteralPath $ServerDir -PathType Container)) { throw "Server directory not found: $ServerDir" }
    try {
        $ServerLease = [IO.File]::Open((Join-Path $ServerDir ".yuumi-server.lock"), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] { throw "Another server launcher or verification already owns this server. Existing logs were left untouched." }
    if (Test-GamePortListening) { throw "Port $GamePort is already listening. Check the existing console; no server or log was changed." }
    if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) { throw "Portable Java 21 not found: $JavaExe" }
    if (-not (Test-Path -LiteralPath (Join-Path $ServerDir "paper.jar") -PathType Leaf)) { throw "Paper 1.21.4 jar not found." }
    # Preserve the old log; stale Done/PASS messages must not satisfy this verification.
    if (Test-Path -LiteralPath $LatestLog -PathType Leaf) {
        $PreviousName = "verify-previous-" + (Get-Date -Format "yyyyMMdd-HHmmssfff") + "-" + [Guid]::NewGuid().ToString("N").Substring(0, 8) + ".log"
        Move-Item -LiteralPath $LatestLog -Destination (Join-Path (Split-Path -Parent $LatestLog) $PreviousName) -ErrorAction Stop
    }
    if (Test-GamePortListening) { throw "Port $GamePort became busy before launch. Verification was cancelled." }
    $Psi = New-Object Diagnostics.ProcessStartInfo
    $Psi.FileName = $JavaExe
    $Psi.WorkingDirectory = $ServerDir
    $Psi.Arguments = "-Xms$Memory -Xmx$Memory -Dterminal.jline=false -Dterminal.ansi=false -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar paper.jar --nogui"
    $Psi.UseShellExecute = $false
    $Psi.CreateNoWindow = $true
    $Psi.RedirectStandardInput = $true
    $Psi.RedirectStandardOutput = $true
    $Psi.RedirectStandardError = $true
    # StandardInputEncoding is not available in Windows PowerShell 5.1 / .NET Framework.
    $Psi.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $Psi.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    $Proc = New-Object Diagnostics.Process
    $Proc.StartInfo = $Psi
    Write-Host "[VerifyBoot] Starting portable Paper 1.21.4; checking core hub, dealer visibility, training dummies and dungeon..." -ForegroundColor Cyan
    [void]$Proc.Start()
    $Started = $true
    $OutputTask = $Proc.StandardOutput.ReadToEndAsync()
    $ErrorTask = $Proc.StandardError.ReadToEndAsync()
    $Deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $Deadline) {
        if ($Proc.HasExited) { throw "Server exited before readiness (code $($Proc.ExitCode))." }
        $Log = Read-VerificationLog
        if ($Log -match "Done \([0-9.,]+s\)! For help") { $Ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $Ready) { throw "Timed out waiting for Paper readiness." }
    $HubReady = $false
    $Deadline = [DateTime]::UtcNow.AddSeconds($CheckTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $Deadline) {
        if ($Proc.HasExited) { throw "Server exited before the core hub was ready." }
        $Log = Read-VerificationLog
        if ((Test-VerificationErrors $Log)) { throw "Server logged an error while preparing the core hub." }
        if ($Log -match "CORE_HUB_READY") { $HubReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $HubReady) { throw "Timed out waiting for CORE_HUB_READY." }
    $BlueMapInstalled = @(Get-ChildItem -LiteralPath (Join-Path $ServerDir "plugins") -File -Filter "BlueMap*.jar" -ErrorAction SilentlyContinue).Count -gt 0
    if ($BlueMapInstalled) {
        # Paper's Done marker precedes BlueMap's asynchronous resource load. Its own Loaded! marker
        # follows Plugin.load()/isLoaded(); wait before dispatching commands and requesting shutdown.
        Write-Host "[VerifyBoot] Waiting for BlueMap asynchronous resource loading to finish..." -ForegroundColor Cyan
        $BlueMapReady = $false
        $Deadline = [DateTime]::UtcNow.AddSeconds($BlueMapTimeoutSeconds)
        while ([DateTime]::UtcNow -lt $Deadline) {
            if ($Proc.HasExited) { throw "Server exited before BlueMap finished loading." }
            $Log = Read-VerificationLog
            if ((Test-VerificationErrors $Log)) { throw "Server logged an error while waiting for BlueMap readiness." }
            if ($Log -match "(?m)\[BlueMap\]\s+Loaded!\s*$") { $BlueMapReady = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $BlueMapReady) { throw "Timed out waiting for BlueMap Loaded!; refusing an early verification shutdown." }
    }
    $Commands = @(
        "plugins",
        "lp group default permission set essentials.spawn false",
        "lp group default permission set essentials.sethome false",
        "lp group default permission set essentials.sethome.multiple false",
        "lp group default permission set essentials.home false",
        "lp group default permission set essentials.delhome false",
        "lp group default permission set essentials.tpa false",
        "lp group default permission set essentials.tpaccept false",
        "lp group default permission set essentials.tpdeny false",
        "lp group default permission set essentials.back false",
        "lp group default permission unset quickshop.create.sell",
        "lp group default permission unset quickshop.create.buy",
        "lp group default permission unset quickshop.create.changeprice",
        "lp group default permission unset quickshop.use",
        "lp group default permission set essentials.msg true",
        "lp group default permission set essentials.balance true",
        "lp group default permission set gsit.sit true",
        "lp group default permission set gsit.sitclick true",
        "lp group default permission set gsit.lay true",
        "lp group default permission set gsit.crawl true",
        "lp group default permission set evenmorefish.use true",
        "yuumi verify-core"
    )
    if (-not $UpdatePermissions) { $Commands = @("plugins", "yuumi verify-core") }
    foreach ($Command in $Commands) {
        if ($Proc.HasExited) { throw "Server exited while dispatching verification commands." }
        $Proc.StandardInput.WriteLine($Command)
        $Proc.StandardInput.Flush()
        Start-Sleep -Milliseconds 100
    }
    $Deadline = [DateTime]::UtcNow.AddSeconds($CheckTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $Deadline) {
        if ($Proc.HasExited) { throw "Server exited before core checks completed." }
        $Log = Read-VerificationLog
        if ($Log -match "CORE_RUNTIME_CHECKS FAILED") { throw "The core runtime checks reported failure." }
        if ($Log -match "CORE_RUNTIME_CHECKS PASS") { $ChecksPassed = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ChecksPassed) { throw "Timed out waiting for CORE_RUNTIME_CHECKS PASS." }
    if (-not [string]::IsNullOrWhiteSpace($ClientScript)) {
        $ExpectedTestServer = [IO.Path]::GetFullPath((Join-Path $RootDir "yuumi-core\build\server-integration"))
        if ([IO.Path]::GetFullPath($ServerDir) -ne $ExpectedTestServer) { throw "Client probes are restricted to the disposable server." }
        $ClientPath = [IO.Path]::GetFullPath($ClientScript)
        if (-not (Test-Path -LiteralPath $ClientPath -PathType Leaf)) { throw "Client script not found: $ClientPath" }
        $ClientInfo = New-Object Diagnostics.ProcessStartInfo
        $ClientInfo.FileName = (Get-Command node.exe -ErrorAction Stop).Source
        $ClientInfo.Arguments = '"' + $ClientPath + '"'
        $ClientInfo.WorkingDirectory = Join-Path $RootDir 'yuumi-core'
        $ClientInfo.UseShellExecute = $false
        $ClientInfo.CreateNoWindow = $true
        $ClientInfo.RedirectStandardOutput = $true
        $ClientInfo.RedirectStandardError = $true
        $ClientInfo.EnvironmentVariables['NODE_PATH'] = Join-Path $RootDir 'yuumi-core\build\integration-client\node_modules'
        $Client = New-Object Diagnostics.Process
        $Client.StartInfo = $ClientInfo
        try {
            [void]$Client.Start()
            $ClientOut = $Client.StandardOutput.ReadToEndAsync()
            $ClientErr = $Client.StandardError.ReadToEndAsync()
            $Deadline = [DateTime]::UtcNow.AddSeconds($ClientTimeoutSeconds)
            while (-not $Client.WaitForExit(1000)) {
                if ($Proc.HasExited) { throw "Paper exited during client checks." }
                if ([DateTime]::UtcNow -ge $Deadline) { throw "Client checks timed out." }
            }
            $ClientOutput = $ClientOut.GetAwaiter().GetResult() + [Environment]::NewLine + $ClientErr.GetAwaiter().GetResult()
            $ClientLog = Join-Path $RootDir 'yuumi-core\build\verification-core\client-console.log'
            [IO.File]::WriteAllText($ClientLog,$ClientOutput,$Utf8NoBom)
            Write-Host $ClientOutput
            if ($Client.ExitCode -ne 0) { throw "Client checks failed (exit $($Client.ExitCode)). See $ClientLog" }
        } finally {
            if (-not $Client.HasExited) { $Client.Kill(); [void]$Client.WaitForExit(10000) }
            $Client.Dispose()
        }
    }
} catch {
    $Failure = $_.Exception.Message
} finally {
    if ($Started) {
        try {
            if (-not $Proc.HasExited) {
                Write-Host "[VerifyBoot] Sending graceful stop..." -ForegroundColor Cyan
                $Proc.StandardInput.WriteLine("stop")
                $Proc.StandardInput.Flush()
                $StopSent = $true
                if (-not $Proc.WaitForExit($ShutdownTimeoutSeconds * 1000)) {
                    $ForcedKill = $true
                    $Proc.Kill()
                    [void]$Proc.WaitForExit(10000)
                }
            }
            if ($Proc.HasExited) { $ExitCode = $Proc.ExitCode }
        } catch {
            if ($null -eq $Failure) { $Failure = "Failed to stop the owned verification process: " + $_.Exception.Message }
            try { if (-not $Proc.HasExited) { $ForcedKill = $true; $Proc.Kill(); [void]$Proc.WaitForExit(10000) } } catch { }
        }
    }
}
try {
$CombinedLog = ""
if ($Started) { $CombinedLog = Read-VerificationLog }
if ($Started -and $Proc.HasExited) {
    if ($null -ne $OutputTask) { $CombinedLog += [Environment]::NewLine + $OutputTask.GetAwaiter().GetResult() }
    if ($null -ne $ErrorTask) { $CombinedLog += [Environment]::NewLine + $ErrorTask.GetAwaiter().GetResult() }
}
Write-Host ""
Write-Host "=== FINAL VERIFICATION LOG SUMMARY ===" -ForegroundColor Yellow
foreach ($Line in ($CombinedLog -split "\r?\n")) {
    if ($Line -match "Enabling |YuumiLandCore|HubWorldManager|DungeonManager|CORE_RUNTIME_CHECKS|\[BlueMap\].*Loaded!|Done \(|Stopping server|ERROR|Exception|SEVERE") { Write-Host $Line }
}
if ($ForcedKill) { $Failure = "Server required a forced kill; graceful shutdown failed." }
if ($Started -and (-not $StopSent -or $ExitCode -ne 0)) {
    if ($null -eq $Failure) { $Failure = "Server did not exit gracefully with code 0." }
}
if ($Started -and (Test-VerificationErrors $CombinedLog)) {
    if ($null -eq $Failure) { $Failure = "Server logs contain application errors or exceptions." }
}
if ($null -ne $Proc) { $Proc.Dispose() }
if ($null -ne $Failure -or -not $Ready -or -not $ChecksPassed) {
    [Console]::Error.WriteLine("[VerifyBoot] FAILED: " + $Failure)
    exit 1
}
Write-Host "[VerifyBoot] PASS: core runtime checks, clean logs and graceful exit code 0." -ForegroundColor Green
exit 0
} finally {
    if ($null -ne $ServerLease) { $ServerLease.Dispose() }
}
