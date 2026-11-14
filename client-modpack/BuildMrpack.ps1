$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem

$RootDir = Split-Path -Parent $PSScriptRoot
$OutDir = $PSScriptRoot
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$Headers = @{ "User-Agent" = "Yuumi97Land-MrpackBuilder/1.0" }
$TargetMcVersion = "1.21.4"

# Query latest Fabric Loader version for 1.21.4
Write-Host "Querying Fabric Meta API for 1.21.4..." -ForegroundColor Cyan
$Loaders = Invoke-RestMethod -Uri "https://meta.fabricmc.net/v2/versions/loader/$TargetMcVersion" -Headers $Headers
$FabricLoaderVer = $Loaders[0].loader.version
Write-Host "Selected Fabric Loader: $FabricLoaderVer" -ForegroundColor Green

# Curated client-side mods for Yuumi's 97Land (Optimization + Shaders + Voice Chat + Fair Minimap + QoL)
$ModSlugs = @(
    "fabric-api",
    "sodium",
    "lithium",
    "ferrite-core",
    "entityculling",
    "iris",
    "simple-voice-chat",
    "jade",
    "appleskin",
    "modmenu",
    "xaeros-minimap-fair"
)

$MrpackFiles = @()

foreach ($Slug in $ModSlugs) {
    Write-Host "  Resolving Modrinth client mod '$Slug'..." -ForegroundColor Cyan
    try {
        $Versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$Slug/version" -Headers $Headers
        $Chosen = $Versions | Where-Object {
            ($_.game_versions -contains $TargetMcVersion) -and ($_.loaders -contains "fabric")
        } | Select-Object -First 1

        if (-not $Chosen) {
            Write-Warning "  [SKIP] No 1.21.4 fabric version for '$Slug'."
            continue
        }

        $FileObj = $Chosen.files | Where-Object { $_.primary -eq $true } | Select-Object -First 1
        if (-not $FileObj) { $FileObj = $Chosen.files[0] }

        $Entry = [ordered]@{
            path = "mods/$($FileObj.filename)"
            hashes = [ordered]@{
                sha1 = $FileObj.hashes.sha1
                sha512 = $FileObj.hashes.sha512
            }
            env = [ordered]@{
                client = "required"
                server = "unsupported"
            }
            downloads = @($FileObj.url)
            fileSize = $FileObj.size
        }
        $MrpackFiles += $Entry
        Write-Host "    + $($FileObj.filename)" -ForegroundColor Green
    } catch {
        Write-Warning "  [WARN] Failed to resolve '$Slug': $($_.Exception.Message)"
    }
}

$IndexObj = [ordered]@{
    formatVersion = 1
    game = "minecraft"
    versionId = "1.0.0"
    name = "유미의 97랜드 (Yuumi's 97Land 공식 클라이언트)"
    summary = "고양이 마을 · 황금 냥냥 카지노 · 근거리 음성채팅 · 최적화 통합 팩 (1.21.4)"
    files = $MrpackFiles
    dependencies = [ordered]@{
        minecraft = $TargetMcVersion
        "fabric-loader" = $FabricLoaderVer
    }
}

$StagingDir = Join-Path $OutDir "_staging"
if (Test-Path $StagingDir) { Remove-Item -Recurse -Force $StagingDir }
New-Item -ItemType Directory -Force -Path $StagingDir, (Join-Path $StagingDir "overrides\resourcepacks") | Out-Null

$IndexJson = $IndexObj | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText((Join-Path $StagingDir "modrinth.index.json"), $IndexJson, (New-Object System.Text.UTF8Encoding($false)))

# Also bundle Yuumi97Pack.zip inside overrides/resourcepacks as an offline backup!
$ServerPack = Join-Path $RootDir "server\resourcepack\Yuumi97Pack.zip"
if (Test-Path $ServerPack) {
    Copy-Item -Path $ServerPack -Destination (Join-Path $StagingDir "overrides\resourcepacks\Yuumi97Pack.zip") -Force
}

$MrpackPath = Join-Path $OutDir "Yuumi97Land-Client-1.21.4.mrpack"
if (Test-Path $MrpackPath) { Remove-Item -Force $MrpackPath }
[System.IO.Compression.ZipFile]::CreateFromDirectory($StagingDir, $MrpackPath)
Remove-Item -Recurse -Force $StagingDir

Write-Host "`n[SUCCESS] Built client modpack: $MrpackPath ($($MrpackFiles.Count) mods)" -ForegroundColor Cyan
