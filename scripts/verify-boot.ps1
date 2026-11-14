$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$JavaExe = Join-Path $RootDir "runtime\jdk-21\bin\java.exe"
$ServerDir = Join-Path $RootDir "server"
$LatestLog = Join-Path $ServerDir "logs\latest.log"
if (Test-Path $LatestLog) { Remove-Item -Force $LatestLog }

$Psi = New-Object System.Diagnostics.ProcessStartInfo
$Psi.FileName = $JavaExe
$Psi.WorkingDirectory = $ServerDir
$Psi.Arguments = "-Xms4G -Xmx4G -Dfile.encoding=UTF-8 -jar paper.jar --nogui"
$Psi.UseShellExecute = $false
$Psi.RedirectStandardInput = $true
$Psi.RedirectStandardOutput = $true
$Psi.RedirectStandardError = $true

$Proc = New-Object System.Diagnostics.Process
$Proc.StartInfo = $Psi

Write-Host "[VerifyBoot] Starting Paper 1.21.4 server (verifying v2.0 yuumi_hub + .nbt structures + LuckPerms)..." -ForegroundColor Cyan
[void]$Proc.Start()
$OutTask = $Proc.StandardOutput.ReadToEndAsync()
$ErrTask = $Proc.StandardError.ReadToEndAsync()

$Ready = $false
$StartWait = Get-Date
while (-not $Ready -and -not $Proc.HasExited) {
    Start-Sleep -Seconds 2
    if (Test-Path $LatestLog) {
        $Raw = Get-Content -Path $LatestLog -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        if ($Raw -match "Done \([0-9.,]+s\)! For help") {
            $Ready = $true
            break
        }
    }
    $Elapsed = [int]((Get-Date) - $StartWait).TotalSeconds
    if ($Elapsed % 10 -eq 0) {
        Write-Host "  ... booting ($Elapsed s)" -ForegroundColor DarkGray
    }
    if ($Elapsed -gt 180) {
        Write-Warning "Timeout waiting for Ready!"
        break
    }
}

if ($Ready) {
    Write-Host "[VerifyBoot] Server READY! Waiting 8s for yuumi_hub v2.0 city & .nbt stamping, then applying LuckPerms..." -ForegroundColor Green
    Start-Sleep -Seconds 8

    # v2.0 Physical Visit Philosophy: Disable remote /spawn, /home, /tpa, /back warps & legacy quickshop permissions!
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
        "lp group default permission set evenmorefish.use true"
    )
    foreach ($cmd in $Commands) {
        $Proc.StandardInput.WriteLine($cmd)
        Start-Sleep -Milliseconds 150
    }

    Start-Sleep -Seconds 4
    Write-Host "[VerifyBoot] Sending graceful 'stop'..." -ForegroundColor Cyan
    $Proc.StandardInput.WriteLine("stop")
    [void]$Proc.WaitForExit(45000)
}

if (-not $Proc.HasExited) { $Proc.Kill() }

Write-Host "`n=== FINAL VERIFICATION LOG SUMMARY ===" -ForegroundColor Yellow
$Lines = Get-Content -Path $LatestLog -Encoding UTF8
foreach ($l in $Lines) {
    if ($l -match "Enabling |Bukkit Plugins:|Server Plugins|YuumiLandCore|HubWorldManager|Done \(|ERROR|Exception") {
        Write-Host $l
    }
}
