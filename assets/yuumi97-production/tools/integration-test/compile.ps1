$ErrorActionPreference = 'Stop'
$workspace = 'E:\MinGem'
$jdkBin = Join-Path $workspace 'runtime\jdk-21\bin'
$buildDir = Join-Path $PSScriptRoot 'build'
$classesDir = Join-Path $buildDir 'classes'
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
$cacheRoot = Join-Path $workspace 'runtime\gradle-user-home\caches\modules-2\files-2.1'
$jars = @(Get-ChildItem -LiteralPath $cacheRoot -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue)
if (-not $jars) { $jars = @(Get-ChildItem -LiteralPath (Join-Path $workspace 'server\libraries') -Recurse -File -Filter '*.jar') }
if (-not $jars) { throw 'Portable Paper dependency jars were not found.' }
$classPath = ($jars.FullName | ForEach-Object { $_.Replace('\', '/') }) -join ';'
$sourcePath = (Join-Path $PSScriptRoot 'YuumiAssetIntegrationTest.java').Replace('\', '/')
$classOutput = $classesDir.Replace('\', '/')
$argFile = Join-Path $buildDir 'javac.args'
$arguments = @('--release', '21', '-encoding', 'UTF-8', '-classpath', ('"' + $classPath + '"'), '-d', ('"' + $classOutput + '"'), ('"' + $sourcePath + '"')) -join "`n"
[System.IO.File]::WriteAllText($argFile, $arguments + "`n", [System.Text.UTF8Encoding]::new($false))
& (Join-Path $jdkBin 'javac.exe') ('@' + $argFile)
if ($LASTEXITCODE -ne 0) { throw "Integration test compilation failed: $LASTEXITCODE" }
$distDir = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Path $distDir -Force | Out-Null
$jarPath = Join-Path $distDir 'YuumiAssetIntegrationTest.jar'
& (Join-Path $jdkBin 'jar.exe') --create --file $jarPath -C $classesDir . -C $PSScriptRoot plugin.yml
if ($LASTEXITCODE -ne 0) { throw "Integration test JAR creation failed: $LASTEXITCODE" }
Write-Output $jarPath
