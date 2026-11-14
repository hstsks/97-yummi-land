param(
    [int]$KeepCount = 10
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$RootDir = Split-Path -Parent $PSScriptRoot
$ServerDir = Join-Path $RootDir "server"
$BackupDir = Join-Path $RootDir "backups"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$StageDir = Join-Path $BackupDir "_tmp_$Timestamp"
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null

$Targets = @("world", "world_nether", "world_the_end", "plugins\YuumiLandCore")
$CopiedAny = $false

foreach ($Rel in $Targets) {
    $Src = Join-Path $ServerDir $Rel
    if (Test-Path $Src) {
        $Dst = Join-Path $StageDir $Rel
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Dst) | Out-Null
        Copy-Item -Path $Src -Destination $Dst -Recurse -Force -ErrorAction SilentlyContinue
        $CopiedAny = $true
    }
}

if ($CopiedAny) {
    $ZipPath = Join-Path $BackupDir "Yuumi97Land-$Timestamp.zip"
    [System.IO.Compression.ZipFile]::CreateFromDirectory($StageDir, $ZipPath)
    Write-Host "[Backup] Saved snapshot -> $ZipPath" -ForegroundColor Green
}
Remove-Item -Recurse -Force $StageDir -ErrorAction SilentlyContinue

# Prune older backups beyond $KeepCount
$AllZips = Get-ChildItem -Path $BackupDir -Filter "Yuumi97Land-*.zip" | Sort-Object LastWriteTime -Descending
if ($AllZips.Count -gt $KeepCount) {
    $AllZips | Select-Object -Skip $KeepCount | ForEach-Object {
        Remove-Item -Force $_.FullName
        Write-Host "[Backup] Pruned old backup: $($_.Name)" -ForegroundColor DarkGray
    }
}
