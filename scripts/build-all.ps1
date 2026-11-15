$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$JdkDir = Join-Path $RootDir "runtime\jdk-21"
$JavaExe = Join-Path $JdkDir "bin\java.exe"
$GradleBat = Join-Path $RootDir "runtime\gradle\bin\gradle.bat"
$CoreDir = Join-Path $RootDir "yuumi-core"
$PluginsDir = Join-Path $RootDir "server\plugins"
function Get-ReadOnlyAssetSnapshot {
    $Records = New-Object 'System.Collections.Generic.List[string]'
    foreach ($Relative in @("assets\yuumi97-production", "resourcepack-gen\overrides")) {
        $Directory = Join-Path $RootDir $Relative
        if (-not (Test-Path -LiteralPath $Directory -PathType Container)) { $Records.Add("MISSING|$Relative"); continue }
        $Records.Add("ROOT|$Relative")
        foreach ($File in @(Get-ChildItem -LiteralPath $Directory -File -Recurse | Sort-Object FullName)) {
            $Hash = Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256 -ErrorAction Stop
            $Records.Add($File.FullName.Substring($RootDir.Length) + "|" + $Hash.Hash)
        }
    }
    return $Records.ToArray()
}
$env:JAVA_HOME = $JdkDir
$env:GRADLE_USER_HOME = Join-Path $RootDir "runtime\gradle-user-home"
$env:PATH = "$JdkDir\bin;$env:PATH"
if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf) -or -not (Test-Path -LiteralPath $GradleBat -PathType Leaf)) {
    throw "Portable JDK 21 and Gradle 8.12 are required. Run scripts\bootstrap-runtime.ps1 first."
}
$AssetsBefore = @(Get-ReadOnlyAssetSnapshot)
Write-Host "=== [1/2] Building Yuumi97Pack.zip Resource Pack ===" -ForegroundColor Cyan
& $JavaExe "-Dfile.encoding=UTF-8" (Join-Path $RootDir "resourcepack-gen\PackBuilder.java") $RootDir
if ($LASTEXITCODE -ne 0) { throw "Resource pack build failed!" }
Write-Host ""
Write-Host "=== [2/2] Compiling YuumiLandCore.jar (Paper 1.21.4) ===" -ForegroundColor Cyan
Push-Location -LiteralPath $CoreDir
try {
    & $GradleBat jar --no-daemon "-Dorg.gradle.java.home=$JdkDir"
    if ($LASTEXITCODE -ne 0) { throw "Gradle compilation failed!" }
} finally { Pop-Location }
$AssetsAfter = @(Get-ReadOnlyAssetSnapshot)
if (@(Compare-Object -ReferenceObject $AssetsBefore -DifferenceObject $AssetsAfter).Count -ne 0) {
    throw "Read-only assets changed during the build, possibly from Astra's concurrent work. Deployment was skipped; retry when asset work is stable."
}
Write-Host "[Assets] Before/after SHA-256 snapshots match; production assets and overrides were preserved." -ForegroundColor Green
$BuiltJar = Join-Path $CoreDir "build\libs\YuumiLandCore.jar"
$TargetJar = Join-Path $PluginsDir "YuumiLandCore.jar"
Copy-Item -LiteralPath $BuiltJar -Destination $TargetJar -Force -ErrorAction Stop
Write-Host "[SUCCESS] Deployed YuumiLandCore.jar -> $TargetJar" -ForegroundColor Green