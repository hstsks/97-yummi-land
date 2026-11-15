$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$RootDir = Split-Path -Parent $PSScriptRoot
$ServerDir = Join-Path $RootDir "server"
$PluginsDir = Join-Path $ServerDir "plugins"
$DatapacksDir = Join-Path $ServerDir "world\datapacks"
$StructPacksDir = Join-Path $RootDir "assets\structure-packs"
New-Item -ItemType Directory -Force -Path $PluginsDir, $DatapacksDir, $StructPacksDir | Out-Null

$Headers = @{ "User-Agent" = "Yuumi97Land-Builder/1.0 (contact@localhost)" }

function Get-ModrinthAsset {
    param(
        [string]$Slug,
        [string[]]$Loaders,
        [string[]]$PreferredVersions,
        [string]$OutDir,
        [string]$PrefixName
    )
    $Existing = Get-ChildItem -Path $OutDir -Filter "$PrefixName*" -ErrorAction SilentlyContinue
    if ($Existing) {
        Write-Host "  [SKIP] $PrefixName already downloaded ($($Existing[0].Name))" -ForegroundColor DarkGray
        return
    }

    Write-Host "  [FETCH] Querying Modrinth for '$Slug'..." -ForegroundColor Cyan
    try {
        $Versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$Slug/version" -Headers $Headers
    } catch {
        Write-Warning "  [WARN] Could not query Modrinth slug '$Slug': $($_.Exception.Message)"
        return
    }

    $Chosen = $null
    foreach ($TargetVer in $PreferredVersions) {
        $Match = $Versions | Where-Object {
            ($_.game_versions -contains $TargetVer) -and
            (($_.loaders | Where-Object { $Loaders -contains $_ }).Count -gt 0)
        } | Select-Object -First 1
        if ($Match) {
            $Chosen = $Match
            break
        }
    }

    if (-not $Chosen) {
        Write-Warning "  [WARN] No matching version found for '$Slug' (Loaders: $($Loaders -join ','))."
        return
    }

    $PrimaryFile = $Chosen.files | Where-Object { $_.primary -eq $true } | Select-Object -First 1
    if (-not $PrimaryFile) { $PrimaryFile = $Chosen.files[0] }

    $Ext = [System.IO.Path]::GetExtension($PrimaryFile.filename)
    $DestFile = Join-Path $OutDir "$PrefixName-$($Chosen.version_number)$Ext"
    Write-Host "  [DOWN] $($PrimaryFile.filename) -> $(Split-Path -Leaf $DestFile)" -ForegroundColor Green
    curl.exe -L -sS -o $DestFile $PrimaryFile.url
}

Write-Host "`n=== [1/3] Downloading World & Dungeon Datapacks (1.21.4) ===" -ForegroundColor Yellow
$DpLoaders = @("datapack")
$DpVers = @("1.21.4", "1.21.3", "1.21.1", "1.21")
Get-ModrinthAsset -Slug "terralith" -Loaders $DpLoaders -PreferredVersions $DpVers -OutDir $DatapacksDir -PrefixName "Terralith"
Get-ModrinthAsset -Slug "structory" -Loaders $DpLoaders -PreferredVersions $DpVers -OutDir $DatapacksDir -PrefixName "Structory"
Get-ModrinthAsset -Slug "dungeons-and-taverns" -Loaders $DpLoaders -PreferredVersions $DpVers -OutDir $DatapacksDir -PrefixName "DungeonsAndTaverns"
Get-ModrinthAsset -Slug "incendium" -Loaders $DpLoaders -PreferredVersions $DpVers -OutDir $DatapacksDir -PrefixName "Incendium"
Get-ModrinthAsset -Slug "nullscape" -Loaders $DpLoaders -PreferredVersions $DpVers -OutDir $DatapacksDir -PrefixName "Nullscape"

# Hub Architectural Asset Pack (Towns and Towers CC-BY-NC-SA-4.0 .nbt structures for yuumi_hub hybrid builder)
Get-ModrinthAsset -Slug "towns-and-towers" -Loaders $DpLoaders -PreferredVersions $DpVers -OutDir $StructPacksDir -PrefixName "TownsAndTowers"

Write-Host "`n=== [2/3] Downloading Open-Source Paper 1.21.4 Plugins (Modrinth) ===" -ForegroundColor Yellow
$PluginLoaders = @("paper", "purpur", "spigot", "bukkit")
$StrictVers = @("1.21.4")
$CompatVers = @("1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21")

# Economy & Permissions (v2.0: QuickShop-Hikari removed in favor of NPC Market + Face-to-Face Safe Trade)
Remove-Item -Force (Join-Path $PluginsDir "QuickShop-Hikari*.jar") -ErrorAction SilentlyContinue
Get-ModrinthAsset -Slug "vaultunlocked" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "VaultUnlocked"
Get-ModrinthAsset -Slug "essentialsx" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "EssentialsX"

# RPG & Lifestyle
Get-ModrinthAsset -Slug "auraskills" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "AuraSkills"
Get-ModrinthAsset -Slug "evenmorefish" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "EvenMoreFish"
Get-ModrinthAsset -Slug "breweryx" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "BreweryX"

# Protection, World & Admin (Pin WorldEdit to 7.3.x Java 21 series for Paper 1.21.4)
$WeExisting = Get-ChildItem -Path $PluginsDir -Filter "WorldEdit*" -ErrorAction SilentlyContinue
if (-not $WeExisting) {
    $WeVers = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/worldedit/version" -Headers $Headers
    $WePick = $WeVers | Where-Object { ($_.game_versions -contains "1.21.4") -and ($_.version_number -like "7.3.*") -and ($_.loaders -contains "bukkit" -or $_.loaders -contains "paper") } | Select-Object -First 1
    $WeFile = $WePick.files | Where-Object { $_.filename -like "worldedit-bukkit*" } | Select-Object -First 1
    curl.exe -L -sS -o (Join-Path $PluginsDir "WorldEdit-$($WePick.version_number).jar") $WeFile.url
    Write-Host "  [DOWN] $($WeFile.filename) -> WorldEdit-$($WePick.version_number).jar" -ForegroundColor Green
}
Get-ModrinthAsset -Slug "worldguard" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "WorldGuard"
Get-ModrinthAsset -Slug "coreprotect" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "CoreProtect"
Get-ModrinthAsset -Slug "chunky" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "Chunky"

# Social, Voice, Web Map & UI
Get-ModrinthAsset -Slug "simple-voice-chat" -Loaders @("paper", "bukkit", "spigot") -PreferredVersions $StrictVers -OutDir $PluginsDir -PrefixName "SimpleVoiceChat"
Get-ModrinthAsset -Slug "gsit" -Loaders $PluginLoaders -PreferredVersions $StrictVers -OutDir $PluginsDir -PrefixName "GSit"
Get-ModrinthAsset -Slug "bluemap" -Loaders @("paper", "spigot") -PreferredVersions $StrictVers -OutDir $PluginsDir -PrefixName "BlueMap"
Get-ModrinthAsset -Slug "tab-was-taken" -Loaders $PluginLoaders -PreferredVersions $CompatVers -OutDir $PluginsDir -PrefixName "TAB"

Write-Host "`n=== [3/3] Downloading LuckPerms (Official Metadata API) ===" -ForegroundColor Yellow
$LpExisting = Get-ChildItem -Path $PluginsDir -Filter "LuckPerms*" -ErrorAction SilentlyContinue
if (-not $LpExisting) {
    $LpMeta = Invoke-RestMethod -Uri "https://metadata.luckperms.net/data/all" -Headers $Headers
    $LpUrl = $LpMeta.downloads.bukkit
    $LpDest = Join-Path $PluginsDir "LuckPerms-Bukkit.jar"
    Write-Host "  [DOWN] LuckPerms -> LuckPerms-Bukkit.jar" -ForegroundColor Green
    curl.exe -L -sS -o $LpDest $LpUrl
} else {
    Write-Host "  [SKIP] LuckPerms already downloaded." -ForegroundColor DarkGray
}

Write-Host "`nEcosystem download finished!" -ForegroundColor Cyan
