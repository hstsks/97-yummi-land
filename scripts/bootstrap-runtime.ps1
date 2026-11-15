$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$RootDir = Split-Path -Parent $PSScriptRoot
$RuntimeDir = Join-Path $RootDir "runtime"
$ServerDir = Join-Path $RootDir "server"
New-Item -ItemType Directory -Force -Path $RuntimeDir, $ServerDir, (Join-Path $ServerDir "plugins") | Out-Null

# 1. Portable Temurin JDK 21
$JdkDir = Join-Path $RuntimeDir "jdk-21"
if (-not (Test-Path (Join-Path $JdkDir "bin\java.exe"))) {
    Write-Host "[1/3] Downloading Portable Eclipse Temurin JDK 21..." -ForegroundColor Cyan
    $JdkZip = Join-Path $RuntimeDir "temurin21.zip"
    $JdkUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    curl.exe -L -sS -o $JdkZip $JdkUrl
    Write-Host "Extracting JDK 21..." -ForegroundColor Cyan
    $ExtractTmp = Join-Path $RuntimeDir "jdk-tmp"
    if (Test-Path $ExtractTmp) { Remove-Item -Recurse -Force $ExtractTmp }
    Expand-Archive -Path $JdkZip -DestinationPath $ExtractTmp -Force
    $InnerFolder = Get-ChildItem -Path $ExtractTmp -Directory | Select-Object -First 1
    if (Test-Path $JdkDir) { Remove-Item -Recurse -Force $JdkDir }
    Move-Item -Path $InnerFolder.FullName -Destination $JdkDir
    Remove-Item -Recurse -Force $ExtractTmp, $JdkZip
    Write-Host "JDK 21 installed at $JdkDir" -ForegroundColor Green
} else {
    Write-Host "[1/3] JDK 21 already present." -ForegroundColor Green
}

# 2. Portable Gradle 8.12
$GradleDir = Join-Path $RuntimeDir "gradle"
if (-not (Test-Path (Join-Path $GradleDir "bin\gradle.bat"))) {
    Write-Host "[2/3] Downloading Gradle 8.12..." -ForegroundColor Cyan
    $GradleZip = Join-Path $RuntimeDir "gradle.zip"
    curl.exe -L -sS -o $GradleZip "https://services.gradle.org/distributions/gradle-8.12-bin.zip"
    $ExtractTmp = Join-Path $RuntimeDir "gradle-tmp"
    if (Test-Path $ExtractTmp) { Remove-Item -Recurse -Force $ExtractTmp }
    Expand-Archive -Path $GradleZip -DestinationPath $ExtractTmp -Force
    $InnerFolder = Get-ChildItem -Path $ExtractTmp -Directory | Select-Object -First 1
    if (Test-Path $GradleDir) { Remove-Item -Recurse -Force $GradleDir }
    Move-Item -Path $InnerFolder.FullName -Destination $GradleDir
    Remove-Item -Recurse -Force $ExtractTmp, $GradleZip
    Write-Host "Gradle installed at $GradleDir" -ForegroundColor Green
} else {
    Write-Host "[2/3] Gradle already present." -ForegroundColor Green
}

# 3. Paper 1.21.4 latest build (Fill v3 API)
$PaperJar = Join-Path $ServerDir "paper.jar"
if (-not (Test-Path $PaperJar)) {
    Write-Host "[3/3] Querying PaperMC Fill v3 API for 1.21.4 latest build..." -ForegroundColor Cyan
    $BuildInfo = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds/latest" -Headers @{ "User-Agent" = "Yuumi97Land-Builder/1.0" }
    $BuildNum = $BuildInfo.id
    $DownloadObj = $BuildInfo.downloads.'server:default'
    $FileName = $DownloadObj.name
    $PaperUrl = $DownloadObj.url
    Write-Host "Downloading Paper 1.21.4 build #$BuildNum ($FileName)..." -ForegroundColor Cyan
    curl.exe -L -sS -o $PaperJar $PaperUrl
    Write-Host "Paper 1.21.4 downloaded to $PaperJar" -ForegroundColor Green
} else {
    Write-Host "[3/3] Paper 1.21.4 already present." -ForegroundColor Green
}

& (Join-Path $JdkDir "bin\java.exe") -version
