param([string]$Memory = "8G", [switch]$Once)

$Utf8NoBom = New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom
try { $Host.UI.RawUI.WindowTitle = "[유미의 97랜드] 마인크래프트 자율 운영 서버" } catch { }
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$JavaExe = Join-Path $RootDir "runtime\jdk-21\bin\java.exe"
$ServerDir = Join-Path $RootDir "server"
$ServerLease = $null
$LocationPushed = $false
function Test-GamePortListening {
    try {
        foreach ($Endpoint in [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()) {
            if ($Endpoint.Port -eq 25565) { return $true }
        }
        return $false
    } catch {
        # Sandboxes may deny network-table queries. Exclusive bind probes need no connection or admin rights.
        foreach ($Address in @([Net.IPAddress]::Any, [Net.IPAddress]::IPv6Any)) {
            $Socket = New-Object Net.Sockets.Socket($Address.AddressFamily, [Net.Sockets.SocketType]::Stream, [Net.Sockets.ProtocolType]::Tcp)
            try {
                $Socket.ExclusiveAddressUse = $true
                $Socket.Bind((New-Object Net.IPEndPoint($Address, 25565)))
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
function Write-AlreadyRunning {
    Write-Host "[경고] 이미 유미의 97랜드 서버(포트 25565)가 실행 중입니다! 기존 서버 콘솔 창을 확인해 주세요." -ForegroundColor Yellow
}
try {
    if (-not (Test-Path -LiteralPath $ServerDir -PathType Container)) { throw "Server directory not found: $ServerDir" }
    try {
        $ServerLease = [IO.File]::Open((Join-Path $ServerDir ".yuumi-server.lock"), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] {
        Write-Host "[경고] 다른 서버 실행기 또는 부팅 검증이 이미 이 서버를 사용 중입니다. 기존 콘솔 창을 확인해 주세요." -ForegroundColor Yellow
        exit 1
    }
    if (Test-GamePortListening) { Write-AlreadyRunning; exit 1 }
    if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) {
        Write-Host "[!] Portable JDK 21 not found. Running bootstrap-runtime.ps1..." -ForegroundColor Yellow
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bootstrap-runtime.ps1")
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) { throw "Portable runtime bootstrap failed." }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $ServerDir "paper.jar") -PathType Leaf)) { throw "Paper jar not found. Run bootstrap-runtime.ps1 first." }
    $JvmArgs = @(
        "-Xms$Memory", "-Xmx$Memory", "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M", "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs", "-Daikars.new.flags=true", "-jar", "paper.jar", "--nogui"
    )
    Push-Location -LiteralPath $ServerDir
    $LocationPushed = $true
    while ($true) {
        if (Test-GamePortListening) { Write-AlreadyRunning; exit 1 }
        Write-Host ""
        Write-Host "==========================================================" -ForegroundColor Magenta
        Write-Host "  [유미의 97랜드] 서버를 시작합니다! (할당 RAM: $Memory)" -ForegroundColor Yellow
        Write-Host "  종료하려면 콘솔에 'stop' 입력 (강제 창 닫기 지양)" -ForegroundColor Gray
        Write-Host "==========================================================" -ForegroundColor Magenta
        & $JavaExe @JvmArgs
        $ServerExitCode = $LASTEXITCODE
        if ($ServerExitCode -ne 0) {
            Write-Host "[오류] 서버가 비정상 종료되었습니다 (Exit Code: $ServerExitCode). 로그를 확인해 주세요. 자동 재기동과 백업을 중단합니다." -ForegroundColor Red
            exit $ServerExitCode
        }
        if ($Once) { break }
        if (Test-GamePortListening) { Write-AlreadyRunning; exit 1 }
        Write-Host ""
        Write-Host "[안내] 서버가 종료되었습니다. 완전히 끄려면 5초 안에 Ctrl+C를 누르세요 (미입력 시 백업 후 자동 재기동)..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5
        if (Test-GamePortListening) { Write-AlreadyRunning; exit 1 }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "backup-world.ps1")
        if ($LASTEXITCODE -ne 0) { throw "World backup failed. Automatic restart was stopped." }
    }
} catch {
    Write-Host "[오류] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($LocationPushed) { Pop-Location }
    if ($null -ne $ServerLease) { $ServerLease.Dispose() }
}
