param(
    [string]$Memory = "8G",
    [switch]$Once
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$RootDir = Split-Path -Parent $PSScriptRoot
$JavaExe = Join-Path $RootDir "runtime\jdk-21\bin\java.exe"
$ServerDir = Join-Path $RootDir "server"

if (-not (Test-Path $JavaExe)) {
    Write-Host "[!] Portable JDK 21 not found. Running bootstrap-runtime.ps1..." -ForegroundColor Yellow
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bootstrap-runtime.ps1")
}

# Official PaperMC / Aikar's G1GC Flags tuned for 8-player 32GB PC Co-Hosting
$JvmArgs = @(
    "-Xms$Memory",
    "-Xmx$Memory",
    "-Dfile.encoding=UTF-8",
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:+AlwaysPreTouch",
    "-XX:G1NewSizePercent=30",
    "-XX:G1MaxNewSizePercent=40",
    "-XX:G1HeapRegionSize=8M",
    "-XX:G1ReservePercent=20",
    "-XX:G1HeapWastePercent=5",
    "-XX:G1MixedGCCountTarget=4",
    "-XX:InitiatingHeapOccupancyPercent=15",
    "-XX:G1MixedGCLiveThresholdPercent=90",
    "-XX:G1RSetUpdatingPauseTimePercent=5",
    "-XX:SurvivorRatio=32",
    "-XX:+PerfDisableSharedMem",
    "-XX:MaxTenuringThreshold=1",
    "-Dusing.aikars.flags=https://mcflags.emc.gs",
    "-Daikars.new.flags=true",
    "-jar",
    "paper.jar",
    "--nogui"
)

Push-Location $ServerDir
try {
    while ($true) {
        Write-Host ""
        Write-Host "==========================================================" -ForegroundColor Magenta
        Write-Host "  🐱 [유미의 97랜드] 서버를 시작합니다! (할당 RAM: $Memory)" -ForegroundColor Yellow
        Write-Host "  종료하려면 콘솔에 'stop' 입력 (강제 창 닫기 지양)" -ForegroundColor Gray
        Write-Host "==========================================================" -ForegroundColor Magenta
        Write-Host ""

        & $JavaExe @JvmArgs

        if ($Once) { break }

        Write-Host "`n[안내] 서버가 종료되었습니다. 완전히 끄려면 5초 안에 Ctrl+C를 누르세요 (미입력 시 자동 재기동 및 백업)..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "backup-world.ps1")
    }
} finally {
    Pop-Location
}
