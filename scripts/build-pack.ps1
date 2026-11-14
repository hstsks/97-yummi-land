$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$JavaExe = Join-Path $RootDir "runtime\jdk-21\bin\java.exe"
$PackBuilderSrc = Join-Path $RootDir "resourcepack-gen\PackBuilder.java"

Write-Host "Building Yuumi97Pack.zip resource pack..." -ForegroundColor Cyan
& $JavaExe "-Dfile.encoding=UTF-8" $PackBuilderSrc $RootDir
if ($LASTEXITCODE -ne 0) { throw "PackBuilder failed with exit code $LASTEXITCODE" }
Write-Host "Done!" -ForegroundColor Green
