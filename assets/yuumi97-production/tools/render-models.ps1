param(
    [string[]]$ModelIds,
    [string]$BlenderPath,
    [ValidateSet('AUTO', 'CPU', 'GPU')][string]$Device = 'CPU',
    [switch]$SkipPrepare,
    [switch]$NoContexts,
    [switch]$Rigs,
    [ValidatePattern('^[a-z0-9_-]+$')][string]$BatchName = 'current'
)
$ErrorActionPreference = 'Stop'
$productionRoot = Split-Path -Parent $PSScriptRoot
$rendererPath = Join-Path $PSScriptRoot 'render-models.py'
if ($Rigs) { $rendererPath = Join-Path $PSScriptRoot 'render-rigs.py' }
$reviewRoot = Join-Path $productionRoot 'review'
$logRoot = Join-Path $reviewRoot 'render-logs'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
if (-not $BlenderPath) {
    $blenderCommand = Get-Command blender -ErrorAction SilentlyContinue
    if ($blenderCommand) {
        $BlenderPath = $blenderCommand.Source
    } else {
        $blenderRoot = Join-Path $env:ProgramFiles 'Blender Foundation'
        $candidate = Get-ChildItem -LiteralPath $blenderRoot -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'blender.exe' } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($candidate) { $BlenderPath = $candidate }
    }
}
if (-not $BlenderPath -or -not (Test-Path -LiteralPath $BlenderPath)) {
    throw 'Blender not found. Supply its actual executable with -BlenderPath.'
}
if (-not $ModelIds -or $ModelIds.Count -eq 0) {
    $contractPath = Join-Path $productionRoot 'sources\asset-contract.mjs'
    $catalogJson = & node --input-type=module -e "import {pathToFileURL} from 'node:url'; const m=await import(pathToFileURL(process.argv[1])); process.stdout.write(JSON.stringify(process.argv[2]==='rigs'?m.monsterCatalog.map(x=>x.id):m.singleReviewModels));" $contractPath $(if ($Rigs) { 'rigs' } else { 'models' })
    if ($LASTEXITCODE -ne 0) { throw 'Could not read production model catalog.' }
    $ModelIds = @(($catalogJson | ConvertFrom-Json) | ForEach-Object { [string]$_ })
}
foreach ($modelId in $ModelIds) {
    if ($modelId -notmatch '^[a-z0-9_]+$') { throw "Invalid model ID: $modelId" }
}
function Invoke-BlenderRun {
    param([string[]]$Arguments, [string]$LogPath)
    # Windows PowerShell treats native stderr warnings as ErrorRecords. Keep
    # those in the log, and use Blender's actual exit code as the failure gate.
    $ErrorActionPreference = 'Continue'
    & $BlenderPath @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $LogPath
    if ($LASTEXITCODE -ne 0) { throw "Blender failed with exit $LASTEXITCODE. See $LogPath" }
}
# Exclusive handle prevents overlapping review batches, including GPU jobs.
$lockPath = Join-Path $reviewRoot 'render.lock'
$renderLock = $null
try {
    $renderLock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
    throw 'Another renderer holds the shared lock. Wait for that batch to finish.'
}
try {
    if (-not $SkipPrepare) {
        $prepareArgs = @('-b', '--factory-startup', '--python-exit-code', '1', '--python', $rendererPath, '--', '--prepare', '--device', $Device) + $ModelIds
        Invoke-BlenderRun -Arguments $prepareArgs -LogPath (Join-Path $logRoot ("prepare-" + $BatchName + '.log'))
    }
    foreach ($modelId in $ModelIds) {
        # Always one saved model per fresh Blender process; no background renderer.
        $renderArgs = @('-b', '--factory-startup', '--python-exit-code', '1', '--python', $rendererPath, '--', '--render-saved', '--device', $Device)
        if ($NoContexts) { $renderArgs += '--no-contexts' }
        $renderArgs += $modelId
        Invoke-BlenderRun -Arguments $renderArgs -LogPath (Join-Path $logRoot ($modelId + '.log'))
        $folder = if ($Rigs) { 'rigs' } else { 'models' }
        $views = if ($Rigs) { @('front', 'side', 'rear', 'threequarter', 'windup-minus60', 'windup-minus60-side', 'strike-plus55') } else { @('front', 'side', 'rear', 'threequarter', 'top') }
        foreach ($viewName in $views) {
            $imagePath = Join-Path $reviewRoot "$folder\$modelId\$viewName.png"
            if (-not (Test-Path -LiteralPath $imagePath)) { throw "Missing review image: $imagePath" }
        }
    }
    Write-Output ("Completed serial Blender review for {0} models. No build or deployment invoked." -f $ModelIds.Count)
} finally {
    if ($renderLock) { $renderLock.Dispose() }
}
