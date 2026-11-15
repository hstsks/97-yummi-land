param([ValidateRange(1, 2147483647)][int]$KeepCount = 10)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$RootDir = Split-Path -Parent $PSScriptRoot
$ServerDir = Join-Path $RootDir "server"
$BackupDir = Join-Path $RootDir "backups"
[void][IO.Directory]::CreateDirectory($BackupDir)
$BackupPrefix = [IO.Path]::GetFullPath($BackupDir).TrimEnd([char]92, [char]47) + [IO.Path]::DirectorySeparatorChar
$BackupLease = $null
$StageDir = $null
$BackupComplete = $false
function Assert-BackupChildPath([string]$Candidate) {
    $Resolved = [IO.Path]::GetFullPath($Candidate)
    if (-not $Resolved.StartsWith($BackupPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Backup path escaped its workspace: $Resolved" }
    return $Resolved
}
try {
    try {
        $BackupLease = [IO.File]::Open((Join-Path $BackupDir ".yuumi-backup.lock"), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] { throw "Another backup is already running, or the backup lock cannot be acquired." }
    $Timestamp = Get-Date -Format "yyyyMMdd-HHmmssfff"
    $Token = [Guid]::NewGuid().ToString("N").Substring(0, 8)
    $StageDir = Assert-BackupChildPath (Join-Path $BackupDir "_tmp_$Timestamp-$Token")
    $ZipPath = Assert-BackupChildPath (Join-Path $BackupDir "Yuumi97Land-$Timestamp-$Token.zip")
    $PartialPath = Assert-BackupChildPath ($ZipPath + ".partial")
    [void][IO.Directory]::CreateDirectory($StageDir)
    $Targets = @("world", "world_nether", "world_the_end", "yuumi_hub", "plugins\YuumiLandCore")
    $CopiedAny = $false
    foreach ($Relative in $Targets) {
        $Source = Join-Path $ServerDir $Relative
        if (Test-Path -LiteralPath $Source -PathType Container) {
            $Destination = Assert-BackupChildPath (Join-Path $StageDir $Relative)
            [void][IO.Directory]::CreateDirectory((Split-Path -Parent $Destination))
            Copy-Item -LiteralPath $Source -Destination $Destination -Recurse -Force -ErrorAction Stop
            $CopiedAny = $true
        }
    }
    if (-not $CopiedAny) { throw "No server worlds or YuumiLandCore data were found; no backup was created." }
    [IO.Compression.ZipFile]::CreateFromDirectory($StageDir, $PartialPath)
    Move-Item -LiteralPath $PartialPath -Destination $ZipPath -ErrorAction Stop
    $BackupComplete = $true
    Write-Host "[Backup] Saved snapshot -> $ZipPath" -ForegroundColor Green
    $AllZips = @(Get-ChildItem -LiteralPath $BackupDir -File -Filter "Yuumi97Land-*.zip" | Sort-Object LastWriteTimeUtc, Name -Descending)
    foreach ($Old in @($AllZips | Select-Object -Skip $KeepCount)) {
        $OldPath = Assert-BackupChildPath $Old.FullName
        Remove-Item -LiteralPath $OldPath -Force -ErrorAction Stop
        Write-Host "[Backup] Pruned old backup: $($Old.Name)" -ForegroundColor DarkGray
    }
} catch {
    Write-Host "[Backup] FAILED: $($_.Exception.Message)" -ForegroundColor Red
    if ($null -ne $StageDir -and (Test-Path -LiteralPath $StageDir)) { Write-Host "[Backup] Incomplete staging files were retained for inspection: $StageDir" -ForegroundColor Yellow }
    exit 1
} finally {
    try {
        if ($BackupComplete -and $null -ne $StageDir -and (Test-Path -LiteralPath $StageDir)) {
            $SafeStageDir = Assert-BackupChildPath $StageDir
            Remove-Item -LiteralPath $SafeStageDir -Recurse -Force -ErrorAction Stop
        }
    } finally {
        if ($null -ne $BackupLease) { $BackupLease.Dispose() }
    }
}