[CmdletBinding()]
param(
    [switch]$Build
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProductionDir = $PSScriptRoot
$WorkspaceDir = Split-Path -Parent (Split-Path -Parent $ProductionDir)
$NodeCommand = Get-Command node -CommandType Application -ErrorAction Stop | Select-Object -First 1
$NodeExe = $NodeCommand.Source
$SourceDir = Join-Path $ProductionDir 'sources'
$ToolsDir = Join-Path $ProductionDir 'tools'
$OverrideNamespace = Join-Path $WorkspaceDir 'resourcepack-gen\overrides\assets\yuumi97'
$BuildScript = Join-Path $WorkspaceDir 'scripts\build-all.ps1'

$GenerationSteps = @(
    (Join-Path $SourceDir 'statue.mjs'),
    (Join-Path $SourceDir 'casino.mjs'),
    (Join-Path $SourceDir 'relics.mjs'),
    (Join-Path $SourceDir 'icons.mjs'),
    (Join-Path $SourceDir 'item-definitions.mjs'),
    (Join-Path $ToolsDir 'validate-assets.mjs')
)

# Check inputs before regenerating any files. Generators use their own source paths,
# so this script can be invoked from any working directory.
foreach ($StepPath in $GenerationSteps) {
    if (-not (Test-Path -LiteralPath $StepPath -PathType Leaf)) {
        throw "Required generator or validator is missing: $StepPath"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $ToolsDir 'asset-kit.mjs') -PathType Leaf)) {
    throw "Required shared tool is missing: $(Join-Path $ToolsDir 'asset-kit.mjs')"
}
if ($Build) {
    foreach ($RequiredBuildPath in @(
        $BuildScript,
        (Join-Path $WorkspaceDir 'runtime\jdk-21\bin\java.exe'),
        (Join-Path $WorkspaceDir 'runtime\gradle\bin\gradle.bat')
    )) {
        if (-not (Test-Path -LiteralPath $RequiredBuildPath -PathType Leaf)) {
            throw "Required portable build input is missing: $RequiredBuildPath"
        }
    }
    $PowerShellCommand = Get-Command powershell.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1
}

# The item definition writer expects these directories; create them for a fresh checkout.
foreach ($Subdirectory in @('items', 'models\gui')) {
    New-Item -ItemType Directory -Path (Join-Path $OverrideNamespace $Subdirectory) -Force | Out-Null
}

foreach ($StepPath in $GenerationSteps) {
    Write-Host "[Assets] Running $(Split-Path -Leaf $StepPath)" -ForegroundColor Cyan
    & $NodeExe $StepPath
    if ($LASTEXITCODE -ne 0) {
        throw "Asset step failed with exit code ${LASTEXITCODE}: $StepPath"
    }
}

if ($Build) {
    $HadGradleUserHome = Test-Path Env:\GRADLE_USER_HOME
    $PreviousGradleUserHome = $env:GRADLE_USER_HOME
    try {
        $env:GRADLE_USER_HOME = Join-Path $WorkspaceDir 'runtime\gradle-user-home'
        Write-Host '[Assets] Building resource pack and deploying YuumiLandCore.jar.' -ForegroundColor Cyan
        & $PowerShellCommand.Source -NoProfile -ExecutionPolicy Bypass -File $BuildScript
        if ($LASTEXITCODE -ne 0) {
            throw "build-all.ps1 failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        if ($HadGradleUserHome) {
            $env:GRADLE_USER_HOME = $PreviousGradleUserHome
        }
        else {
            Remove-Item Env:\GRADLE_USER_HOME -ErrorAction SilentlyContinue
        }
    }
    Write-Host '[Assets] Generation, validation and build completed. Server restart is not performed by this script.' -ForegroundColor Green
}
else {
    Write-Host '[Assets] Generation and validation completed. Add -Build to package and deploy.' -ForegroundColor Green
}
