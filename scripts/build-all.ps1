$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$JdkDir = Join-Path $RootDir "runtime\jdk-21"
$JavaExe = Join-Path $JdkDir "bin\java.exe"
$GradleBat = Join-Path $RootDir "runtime\gradle\bin\gradle.bat"
$CoreDir = Join-Path $RootDir "yuumi-core"
$PluginsDir = Join-Path $RootDir "server\plugins"

$env:JAVA_HOME = $JdkDir
$env:PATH = "$JdkDir\bin;$env:PATH"

Write-Host "=== [1/2] Building Yuumi97Pack.zip Resource Pack ===" -ForegroundColor Cyan
& $JavaExe "-Dfile.encoding=UTF-8" (Join-Path $RootDir "resourcepack-gen\PackBuilder.java") $RootDir
if ($LASTEXITCODE -ne 0) { throw "Resource pack build failed!" }

Write-Host "`n=== [2/2] Compiling YuumiLandCore.jar (Paper 1.21.4) ===" -ForegroundColor Cyan
Push-Location $CoreDir
try {
    & $GradleBat jar --no-daemon "-Dorg.gradle.java.home=$JdkDir"
    if ($LASTEXITCODE -ne 0) { throw "Gradle compilation failed!" }
} finally {
    Pop-Location
}

$BuiltJar = Join-Path $CoreDir "build\libs\YuumiLandCore.jar"
$TargetJar = Join-Path $PluginsDir "YuumiLandCore.jar"
Copy-Item -Path $BuiltJar -Destination $TargetJar -Force
Write-Host "`n[SUCCESS] Deployed YuumiLandCore.jar -> $TargetJar" -ForegroundColor Green
