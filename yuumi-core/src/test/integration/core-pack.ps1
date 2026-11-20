# Reads authored/test archives and immutable overrides. Only the verification log is written.
param([string]$PackPath='', [string]$FallbackPath='', [string]$OverridesPath='')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.Encoding]::UTF8
$OutputEncoding=[Text.Encoding]::UTF8
$CoreDir=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$RepoDir=Split-Path -Parent $CoreDir
$BuildDir=Join-Path $CoreDir 'build'
$OutputDir=Join-Path $BuildDir 'verification-core'
[void][IO.Directory]::CreateDirectory($OutputDir)
$SummaryLog=Join-Path $OutputDir 'pack-checks.log'
$LogEncoding=New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($SummaryLog,'Core pack contract verification'+[Environment]::NewLine,(New-Object Text.UTF8Encoding($true)))
function Write-CheckLog([string]$Message){Write-Host $Message;[IO.File]::AppendAllText($SummaryLog,$Message+[Environment]::NewLine,$LogEncoding)}
function Require([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
if([string]::IsNullOrWhiteSpace($PackPath)){$PackPath=Join-Path $BuildDir 'server-integration\resourcepack\Yuumi97Pack.zip'}
if([string]::IsNullOrWhiteSpace($FallbackPath)){$FallbackPath=Join-Path $OutputDir 'Yuumi97Pack-core-fallback.zip'}
$PackPath=[IO.Path]::GetFullPath($PackPath)
$FallbackPath=[IO.Path]::GetFullPath($FallbackPath)
if([string]::IsNullOrWhiteSpace($OverridesPath)){$OverridesPath=Join-Path $RepoDir 'resourcepack-gen\overrides'}
$Overrides=[IO.Path]::GetFullPath($OverridesPath)
$NewIds=@('weapon_berserker_greatsword','weapon_priest_staff','weapon_arcane_orb_tome','guidebook')
$Vfx=@('vfx_slash_crescent_gold','vfx_slash_crimson_heavy','vfx_titan_greatsword_drop','vfx_slash_crescent_shadow','vfx_shadow_mark_crest','vfx_star_arrow_volley','vfx_astral_bow_sigil','vfx_magic_circle_arcane','vfx_gravity_singularity','vfx_arcane_meteor','vfx_magic_circle_sanctuary','vfx_holy_spear','vfx_golden_fishing_vortex','vfx_feast_cauldron_aura')
$Archive=$null;$Fallback=$null
function Entry-Bytes($Entry){
 $InputStream=$Entry.Open();$Memory=New-Object IO.MemoryStream
 try{$InputStream.CopyTo($Memory);return ,$Memory.ToArray()}finally{$InputStream.Dispose();$Memory.Dispose()}
}
function Bytes-Hash([byte[]]$Bytes){
 $Hasher=[Security.Cryptography.SHA256]::Create()
 try{return [BitConverter]::ToString($Hasher.ComputeHash($Bytes))}finally{$Hasher.Dispose()}
}
function Read-JsonEntry($Entry){
 $InputStream=$Entry.Open();$Reader=New-Object IO.StreamReader($InputStream,(New-Object Text.UTF8Encoding($false,$true)),$true)
 try{return ($Reader.ReadToEnd()|ConvertFrom-Json -ErrorAction Stop)}finally{$Reader.Dispose()}
}
function Custom-ResourcePath([string]$Reference,[string]$Kind){
 if([string]::IsNullOrWhiteSpace($Reference)-or$Reference.StartsWith('#')){return $null}
 $Parts=$Reference-split':',2
 if($Parts.Length-eq1-or$Parts[0]-eq'minecraft'){return $null}
 Require ($Parts[0]-match'^[a-z0-9_.-]+$'-and$Parts[1]-match'^[a-z0-9_./-]+$') ('Invalid resource identifier: '+$Reference)
 if($Kind-eq'model'){return 'assets/'+$Parts[0]+'/models/'+$Parts[1]+'.json'}
 return 'assets/'+$Parts[0]+'/textures/'+$Parts[1]+'.png'
}
function Check-Resource([string]$Reference,[string]$Kind,[string]$From){
 $Path=Custom-ResourcePath $Reference $Kind
 if($null-ne$Path){Require ($Entries.ContainsKey($Path)) ('Missing '+$Kind+' '+$Reference+' referenced by '+$From)}
}
function Walk-References($Node,[string]$From){
 if($null-eq$Node){return}
 if($Node-is[Array]){foreach($Value in $Node){Walk-References $Value $From};return}
 if($Node-isnot[pscustomobject]){return}
 foreach($Property in $Node.PSObject.Properties){
  $Value=$Property.Value
  if($Value-is[string]){
   if($Property.Name-in@('model','parent','base')){Check-Resource $Value 'model' $From}
   elseif($Property.Name-eq'texture'){Check-Resource $Value 'texture' $From}
  }
  if($Property.Name-eq'textures'-and$null-ne$Value){
   foreach($Texture in $Value.PSObject.Properties){if($Texture.Value-is[string]){Check-Resource $Texture.Value 'texture' $From}}
  }
  Walk-References $Value $From
 }
}
function Collect-Aliases($Node,$Aliases){
 if($null-eq$Node){return}
 if($Node-is[Array]){foreach($Value in $Node){Collect-Aliases $Value $Aliases};return}
 if($Node-isnot[pscustomobject]){return}
 foreach($Property in $Node.PSObject.Properties){
  if($Property.Name-eq'texture'-and$Property.Value-is[string]-and$Property.Value.StartsWith('#')){[void]$Aliases.Add($Property.Value.Substring(1))}
  if($Property.Name-eq'textures'-and$null-ne$Property.Value){
   foreach($Texture in $Property.Value.PSObject.Properties){if($Texture.Value-is[string]-and$Texture.Value.StartsWith('#')){[void]$Aliases.Add($Texture.Value.Substring(1))}}
  }
  Collect-Aliases $Property.Value $Aliases
 }
}
function Resolve-Alias([string]$Model,[string]$Alias,$Seen){
 $Visit=$Model+'#'+$Alias
 Require ($Seen.Add($Visit)) ('Texture alias cycle: '+$Visit)
 $Document=$Documents[$Model]
 Require ($null-ne$Document) ('Missing model document: '+$Model)
 if($null-ne$Document.textures){
  $Property=$Document.textures.PSObject.Properties[$Alias]
  if($null-ne$Property){
   $Value=[string]$Property.Value
   if($Value.StartsWith('#')){Resolve-Alias $Model $Value.Substring(1) $Seen;return}
   Check-Resource $Value 'texture' $Model;return
  }
 }
 $Parent=Custom-ResourcePath ([string]$Document.parent) 'model'
 if($null-ne$Parent){Resolve-Alias $Parent $Alias $Seen;return}
 throw ('Unresolved texture alias #'+$Alias+' in '+$Model)
}
try{
 $SafePrefix=[IO.Path]::GetFullPath($BuildDir)+[IO.Path]::DirectorySeparatorChar
 Require ($PackPath.StartsWith($SafePrefix,[StringComparison]::OrdinalIgnoreCase)) 'Authored input must be the isolated test-server pack under yuumi-core/build'
 Require ($FallbackPath.StartsWith($SafePrefix,[StringComparison]::OrdinalIgnoreCase)) 'Fallback fixture must be under yuumi-core/build'
 Require (-not $PackPath.Equals($FallbackPath,[StringComparison]::OrdinalIgnoreCase)) 'Authored and fallback packs must differ'
 Require (Test-Path -LiteralPath $PackPath -PathType Leaf) 'Authored test pack missing; supply the prepared isolated-server ZIP'
 Require (Test-Path -LiteralPath $FallbackPath -PathType Leaf) 'Fallback fixture missing; run the PackFixtures test task first'
 Require (Test-Path -LiteralPath $Overrides -PathType Container) ('Override input directory missing: '+$Overrides)
 Write-CheckLog ('Override comparison input: '+$Overrides)
 $SourceHash=(Get-FileHash -LiteralPath $PackPath -Algorithm SHA256).Hash
 $FallbackHash=(Get-FileHash -LiteralPath $FallbackPath -Algorithm SHA256).Hash
 $Archive=[IO.Compression.ZipFile]::OpenRead($PackPath)
 $Entries=@{};$Documents=@{}
 foreach($Entry in $Archive.Entries){Require (-not $Entries.ContainsKey($Entry.FullName)) ('Duplicate ZIP entry: '+$Entry.FullName);$Entries[$Entry.FullName]=$Entry}
 $OverrideFiles=@(Get-ChildItem -LiteralPath $Overrides -File -Recurse|Where-Object{$_.Name-ine'README.md'-and$_.Name-cne'.gitkeep'})
 Require ($OverrideFiles.Count-gt0) 'No authored override inputs exist'
 $InputHashes=@{}
 foreach($File in $OverrideFiles){
  $Relative=$File.FullName.Substring($Overrides.Length+1).Replace('\','/')
  Require ($Entries.ContainsKey($Relative)) ('Override absent from authored test ZIP: '+$Relative)
  $InputHash=Bytes-Hash ([IO.File]::ReadAllBytes($File.FullName));$InputHashes[$File.FullName]=$InputHash
  Require ($InputHash-eq(Bytes-Hash (Entry-Bytes $Entries[$Relative]))) ('Override differs from authored test ZIP: '+$Relative+'; refresh the test snapshot after asset delivery')
 }
 Write-CheckLog ('PASS: '+$OverrideFiles.Count+' selected override inputs are byte-identical; no historical fixed asset-count assumption')
 foreach($Entry in $Archive.Entries){if($Entry.FullName.EndsWith('.json')-or$Entry.FullName-eq'pack.mcmeta'){$Documents[$Entry.FullName]=Read-JsonEntry $Entry}}
 foreach($Id in $NewIds){
  $Def=$Documents['assets/yuumi97/items/'+$Id+'.json']
  Require ($null-ne$Def-and$Def.model.type-eq'minecraft:select'-and$Def.model.property-eq'minecraft:display_context') ('Missing GUI selector: '+$Id)
  $GuiCases=@($Def.model.cases|Where-Object{@($_.when)-contains'gui'})
  Require ($GuiCases.Count-eq1-and$GuiCases[0].model.type-eq'minecraft:model') ('Invalid GUI branch: '+$Id)
  Require ($Def.model.fallback.type-eq'minecraft:model') ('Missing world/handheld model fallback: '+$Id)
  $GuiPath=Custom-ResourcePath ([string]$GuiCases[0].model.model) 'model'
  Require ($null-ne$GuiPath-and$Documents.ContainsKey($GuiPath)) ('Missing GUI model: '+$Id)
 }
 $Manifest=$Documents['assets/yuumi97/monster_rigs.json']
 Require ($null-ne$Manifest-and$Manifest.schemaVersion-eq1-and$Manifest.unitsPerBlock-eq16) 'Missing/invalid rig manifest convention'
 $Rigs=@($Manifest.rigs.PSObject.Properties)
 Require ($Rigs.Count-eq18) 'Rig manifest must declare eighteen monsters'
 $Parts=New-Object 'Collections.Generic.HashSet[string]'
 foreach($Rig in $Rigs){
  $Definition=$Rig.Value
  Require ($Definition.baseScale-gt0) ('Invalid rig scale: '+$Rig.Name)
  [void]$Parts.Add([string]$Definition.rootModel)
  $Sockets=@($Definition.sockets.PSObject.Properties)
  $Expected=1;if($Rig.Name.StartsWith('boss_')){$Expected=2}
  Require ($Sockets.Count-eq$Expected) ('Incomplete rig sockets: '+$Rig.Name)
  foreach($Socket in $Sockets){Require (@($Socket.Value.offset).Count-eq3) ('Invalid rig socket offset: '+$Rig.Name);[void]$Parts.Add([string]$Socket.Value.model)}
 }
 Require ($Parts.Count-eq42) 'Expected forty-two unique rig part item references'
 foreach($Ref in $Parts){Require ($Ref.StartsWith('yuumi97:')) ('Unsupported rig item namespace: '+$Ref);Require ($Entries.ContainsKey('assets/yuumi97/items/'+$Ref.Substring(8)+'.json')) ('Missing rig part item definition: '+$Ref)}
 foreach($Id in $Vfx){Require ($Entries.ContainsKey('assets/yuumi97/items/'+$Id+'.json')) ('Missing VFX definition: '+$Id)}
 Require ($Entries.ContainsKey('assets/yuumi97/items/prop_wayfinder_arrow.json')) 'Missing wayfinder item definition'
 foreach($Name in $Documents.Keys){if($Name-ne'assets/yuumi97/monster_rigs.json'){Walk-References $Documents[$Name] $Name}}
 foreach($Name in @($Documents.Keys|Where-Object{$_-match'^assets/[^/]+/models/.+\.json$'})){
  $Aliases=New-Object 'Collections.Generic.HashSet[string]';Collect-Aliases $Documents[$Name] $Aliases
  foreach($Alias in $Aliases){Resolve-Alias $Name $Alias (New-Object 'Collections.Generic.HashSet[string]')}
 }
 Write-CheckLog ('PASS: four GUI selectors, eighteen rigs/forty-two parts, fourteen VFX, wayfinder and all custom model/texture/alias references')
 $PngCount=0
 foreach($Entry in $Archive.Entries){if(-not $Entry.FullName.EndsWith('.png')){continue};$Bytes=Entry-Bytes $Entry;$Memory=New-Object IO.MemoryStream(,$Bytes);$Image=$null
  try{$Image=[Drawing.Image]::FromStream($Memory,$false,$true);Require ($Image.Width-gt0-and$Image.Height-gt0) ('Invalid PNG: '+$Entry.FullName)}finally{if($null-ne$Image){$Image.Dispose()};$Memory.Dispose()};$PngCount++
 }
 Write-CheckLog ('PASS: '+$PngCount+' authored PNG files decode')
 $Fallback=[IO.Compression.ZipFile]::OpenRead($FallbackPath);$FallbackEntries=@{}
 foreach($Entry in $Fallback.Entries){Require (-not $FallbackEntries.ContainsKey($Entry.FullName)) ('Duplicate fallback ZIP entry: '+$Entry.FullName);$FallbackEntries[$Entry.FullName]=$Entry}
 Require (-not $FallbackEntries.ContainsKey('assets/yuumi97/monster_rigs.json')) 'Fallback still loads the authored rig manifest'
 foreach($Id in $Vfx){Require (-not $FallbackEntries.ContainsKey('assets/yuumi97/items/'+$Id+'.json')) ('Fallback still exposes VFX '+$Id)}
 foreach($Ref in $Parts){Require (-not $FallbackEntries.ContainsKey('assets/yuumi97/items/'+$Ref.Substring(8)+'.json')) ('Fallback still exposes rig '+$Ref)}
 Require (-not $FallbackEntries.ContainsKey('assets/yuumi97/items/prop_wayfinder_arrow.json')) 'Fallback still exposes the authored wayfinder'
 foreach($Id in $NewIds){
  $Texture='assets/yuumi97/textures/item/'+$Id+'.png';Require ($FallbackEntries.ContainsKey($Texture)) ('Missing reflected fallback PNG '+$Id)
  $Memory=New-Object IO.MemoryStream(,(Entry-Bytes $FallbackEntries[$Texture]));$Image=$null
  try{$Image=[Drawing.Image]::FromStream($Memory,$false,$true);Require ($Image.Width-eq16-and$Image.Height-eq16) ('Fallback dot is not 16x16: '+$Id)}finally{if($null-ne$Image){$Image.Dispose()};$Memory.Dispose()}
  $Model=Read-JsonEntry $FallbackEntries['assets/yuumi97/models/item/'+$Id+'.json']
  Require ($Model.parent-in@('minecraft:item/generated','minecraft:item/handheld')-and$Model.textures.layer0-eq('yuumi97:item/'+$Id)) ('Fallback is not a flat base model: '+$Id)
  if($Id-eq'weapon_arcane_orb_tome'){Require (-not $FallbackEntries.ContainsKey('assets/yuumi97/items/'+$Id+'.json')) 'New orb must be absent to test legacy arcane fallback'}
  else{$Def=Read-JsonEntry $FallbackEntries['assets/yuumi97/items/'+$Id+'.json'];Require ($Def.model.type-eq'minecraft:model'-and$Def.model.model-eq('yuumi97:item/'+$Id)) ('Fallback item selector remains authored: '+$Id)}
 }
 Require ($FallbackEntries.ContainsKey('assets/yuumi97/items/weapon_arcane_grimoire.json')) 'Legacy arcane fallback missing'
 foreach($Entry in $Archive.Entries){
  $Name=$Entry.FullName
  $Changed=$Name-eq'assets/yuumi97/monster_rigs.json'-or$Name-eq'assets/yuumi97/items/prop_wayfinder_arrow.json'
  foreach($Id in $Vfx){if($Name-eq('assets/yuumi97/items/'+$Id+'.json')){$Changed=$true}}
  foreach($Ref in $Parts){if($Name-eq('assets/yuumi97/items/'+$Ref.Substring(8)+'.json')){$Changed=$true}}
  foreach($Id in $NewIds){if($Name-eq('assets/yuumi97/items/'+$Id+'.json')-or$Name-eq('assets/yuumi97/models/item/'+$Id+'.json')-or$Name-eq('assets/yuumi97/textures/item/'+$Id+'.png')){$Changed=$true}}
  if(-not $Changed){Require ($FallbackEntries.ContainsKey($Name)-and(Bytes-Hash (Entry-Bytes $Entry))-eq(Bytes-Hash (Entry-Bytes $FallbackEntries[$Name]))) ('Fallback modified a retained entry: '+$Name)}
 }
 foreach($File in $OverrideFiles){Require ($InputHashes[$File.FullName]-eq(Bytes-Hash ([IO.File]::ReadAllBytes($File.FullName)))) ('Asset changed concurrently during verification: '+$File.FullName)}
 Require ($SourceHash-eq(Get-FileHash -LiteralPath $PackPath -Algorithm SHA256).Hash) 'Authored test ZIP changed during verification'
 Require ($FallbackHash-eq(Get-FileHash -LiteralPath $FallbackPath -Algorithm SHA256).Hash) 'Fallback fixture ZIP changed during verification'
 Write-CheckLog 'PASS: build-only fallback archive has reflected dots and absent authored capabilities; retained entries are byte-identical'
 Write-CheckLog ('Authored test pack: '+$PackPath)
 Write-CheckLog ('Fallback test pack: '+$FallbackPath)
 Write-CheckLog 'CORE_PACK_CHECKS PASS'
 exit 0
}catch{Write-CheckLog ('CORE_PACK_CHECKS FAILED: '+$_.Exception.Message);exit 1}
finally{if($null-ne$Fallback){$Fallback.Dispose()};if($null-ne$Archive){$Archive.Dispose()}}
