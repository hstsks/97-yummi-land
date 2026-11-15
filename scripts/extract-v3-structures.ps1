$ErrorActionPreference = 'Stop'
$RootDir = Split-Path -Parent $PSScriptRoot
$SourceZip = Join-Path $RootDir 'assets\structure-packs\TownsAndTowers-1.21.1-datapack.zip'
$TargetDir = Join-Path $RootDir 'yuumi-core\src\main\resources\structures\v3'
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Original Towns and Towers files remain untouched; only these twenty templates are embedded.
$Templates = [ordered]@{
    'jp_gazebo_1.nbt' = 'village/flower_forest_japanese/town_centers/flower_forest_meeting_point_1.nbt'
    'jp_park_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_park_1.nbt'
    'jp_park_2.nbt' = 'village/flower_forest_japanese/houses/flower_forest_park_2.nbt'
    'jp_planter.nbt' = 'village/flower_forest_japanese/flower_forest_planter_1.nbt'
    'jp_lamp.nbt' = 'village/flower_forest_japanese/flower_forest_lamp_1.nbt'
    'jp_gate_1.nbt' = 'village/flower_forest_japanese/flower_forest_gate_1.nbt'
    'jp_small_farm_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_small_farm_1.nbt'
    'jp_shepherd_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_shepherd_1.nbt'
    'jp_fisher_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_fisher_1.nbt'
    'jp_weaponsmith_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_weaponsmith_1.nbt'
    'jp_armorer_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_armorer_1.nbt'
    'jp_cartographer_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_cartographer_1.nbt'
    'jp_leatherworker_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_leatherworker_1.nbt'
    'jp_medium_house_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_medium_house_1.nbt'
    'jp_small_house_1.nbt' = 'village/flower_forest_japanese/houses/flower_forest_small_house_1.nbt'
    'jp_small_house_2.nbt' = 'village/flower_forest_japanese/houses/flower_forest_small_house_2.nbt'
    'jp_temple.nbt' = 'village/flower_forest_japanese/houses/flower_forest_temple_1.nbt'
    'oriental_pagoda.nbt' = 'outpost/towers/exclusives/oriental/base_plate.nbt'
    'harbor_ship.nbt' = 'ships/village_ocean/pieces/small_ship/small_ship_1.nbt'
    'lighthouse_house.nbt' = 'village/beach_lighthouse/side/beach_main_house_1.nbt'
}

$Archive = [System.IO.Compression.ZipFile]::OpenRead($SourceZip)
try {
    foreach ($Pair in $Templates.GetEnumerator()) {
        if ($null -eq $Archive.GetEntry('data/kaisyn/structure/' + $Pair.Value)) {
            throw "Missing source template: $($Pair.Value)"
        }
    }
    [void][System.IO.Directory]::CreateDirectory($TargetDir)
    foreach ($Pair in $Templates.GetEnumerator()) {
        $Entry = $Archive.GetEntry('data/kaisyn/structure/' + $Pair.Value)
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($Entry, (Join-Path $TargetDir $Pair.Key), $true)
    }
    Write-Host "Embedded $($Templates.Count) v3 structure templates."
} finally {
    $Archive.Dispose()
}
