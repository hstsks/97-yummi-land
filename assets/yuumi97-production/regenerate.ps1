[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$nodeExe = (Get-Command node -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$generationSteps = @(
    'sources/statue.mjs', 'sources/casino.mjs', 'sources/relics.mjs',
    'sources/environment.mjs', 'sources/housing.mjs', 'sources/class-weapons.mjs',
    'sources/landmarks.mjs', 'sources/hero-relics.mjs', 'sources/skill-vfx.mjs',
    'sources/monster-rigs.mjs', 'sources/icons.mjs', 'sources/hero-icons.mjs',
    'sources/item-definitions.mjs', 'sources/review-catalog.mjs', 'tools/validate-assets.mjs',
    'tools/check-foundry-clearance.mjs', 'tools/check-astral-clearance.mjs', 'tools/check-void-clearance.mjs'
)
foreach ($relative in $generationSteps) {
    $step = Join-Path $PSScriptRoot $relative
    if (-not (Test-Path -LiteralPath $step -PathType Leaf)) { throw "Missing production input: $step" }
}
foreach ($relative in $generationSteps) {
    $step = Join-Path $PSScriptRoot $relative
    Write-Host "[Assets] $relative" -ForegroundColor Cyan
    & $nodeExe $step
    if ($LASTEXITCODE -ne 0) { throw "Asset generation failed: $relative (exit $LASTEXITCODE)" }
}
Write-Host '[Assets] Generation and validation completed. No packaging, Java build or deployment.' -ForegroundColor Green
