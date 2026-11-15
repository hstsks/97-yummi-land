# Run with Windows PowerShell 5.1. Reads the production ZIP/overrides; writes only core/build/verification-v31.
param([int]$ExpectedOverrideCount=175)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.Encoding]::UTF8
$OutputEncoding=[Text.Encoding]::UTF8
$CoreDir=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$RepoDir=Split-Path -Parent $CoreDir
$OutputDir=Join-Path $CoreDir 'build\verification-v31'
[void][IO.Directory]::CreateDirectory($OutputDir)
$SummaryLog=Join-Path $OutputDir 'pack-checks.log'
$LogEncoding=New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($SummaryLog,'v3.1 pack verification (production inputs read-only)'+[Environment]::NewLine,(New-Object Text.UTF8Encoding($true)))
function Write-CheckLog([string]$Message){Write-Host $Message;[IO.File]::AppendAllText($SummaryLog,$Message+[Environment]::NewLine,$LogEncoding)}
function Require([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$PackPath=Join-Path $RepoDir 'server\resourcepack\Yuumi97Pack.zip'
$Overrides=Join-Path $RepoDir 'resourcepack-gen\overrides'
$FallbackPath=Join-Path $OutputDir 'Yuumi97Pack-v31-fallback.zip'
$NewIds=@('prop_casino_marquee','prop_casino_chandelier','prop_dealer_throne','prop_plaza_clock_face','prop_training_dummy','prop_sakura_lantern_arch')
$Removed=@($NewIds|ForEach-Object{'assets/yuumi97/items/'+$_+'.json'})
$Archive=$null
$Fallback=$null
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
 Require (Test-Path -LiteralPath $PackPath -PathType Leaf) 'Built server resource ZIP missing; run build-all first.'
 $Archive=[IO.Compression.ZipFile]::OpenRead($PackPath)
 $Entries=@{};$Documents=@{}
 foreach($Entry in $Archive.Entries){
  Require (-not $Entries.ContainsKey($Entry.FullName)) ('Duplicate ZIP entry: '+$Entry.FullName)
  $Entries[$Entry.FullName]=$Entry
 }
 # Match PackBuilder's explicit documentation exclusions; these are not pack inputs.
 $OverrideFiles=@(Get-ChildItem -LiteralPath $Overrides -File -Recurse|Where-Object{$_.Name-ine'README.md'-and$_.Name-cne'.gitkeep'})
 Require ($OverrideFiles.Count-eq$ExpectedOverrideCount) ('Expected '+$ExpectedOverrideCount+' override inputs; found '+$OverrideFiles.Count)
 $InputHashes=@{}
 foreach($File in $OverrideFiles){
  $Relative=$File.FullName.Substring($Overrides.Length+1).Replace('\','/')
  Require ($Entries.ContainsKey($Relative)) ('Override absent from merged ZIP: '+$Relative)
  $InputHash=Bytes-Hash ([IO.File]::ReadAllBytes($File.FullName));$InputHashes[$File.FullName]=$InputHash
  Require ($InputHash-eq(Bytes-Hash (Entry-Bytes $Entries[$Relative]))) ('Override bytes changed during merge: '+$Relative)
 }
 Write-CheckLog ('PASS: '+$OverrideFiles.Count+' override inputs are byte-identical in the merged ZIP (including GUI/3D definitions).')
 foreach($Id in $NewIds){
  Require ($Entries.ContainsKey('assets/yuumi97/items/'+$Id+'.json')) ('Missing v3.1 item definition: '+$Id)
 }
 foreach($Entry in $Archive.Entries){
  if($Entry.FullName.EndsWith('.json')-or$Entry.FullName-eq'pack.mcmeta'){$Documents[$Entry.FullName]=Read-JsonEntry $Entry}
 }
 foreach($Name in $Documents.Keys){Walk-References $Documents[$Name] $Name}
 foreach($Name in @($Documents.Keys|Where-Object{$_-match'^assets/[^/]+/models/.+\.json$'})){
  $Aliases=New-Object 'Collections.Generic.HashSet[string]'
  Collect-Aliases $Documents[$Name] $Aliases
  foreach($Alias in $Aliases){Resolve-Alias $Name $Alias (New-Object 'Collections.Generic.HashSet[string]')}
 }
 Write-CheckLog ('PASS: all '+$Documents.Count+' JSON documents parse; custom model/parent/texture references and texture aliases resolve; six new definitions exist.')
 $PngCount=0
 foreach($Entry in $Archive.Entries){
  if(-not $Entry.FullName.EndsWith('.png')){continue}
  $Bytes=Entry-Bytes $Entry
  Require ($Bytes.Length-ge24-and$Bytes[0]-eq137-and$Bytes[1]-eq80-and$Bytes[2]-eq78-and$Bytes[3]-eq71-and$Bytes[4]-eq13-and$Bytes[5]-eq10-and$Bytes[6]-eq26-and$Bytes[7]-eq10) ('Invalid PNG header: '+$Entry.FullName)
  $Memory=New-Object IO.MemoryStream(,$Bytes);$Image=$null;$Bitmap=$null
  try{
   $Image=[Drawing.Image]::FromStream($Memory,$false,$true)
   Require ($Image.Width-gt0-and$Image.Height-gt0) ('Invalid PNG dimensions: '+$Entry.FullName)
   $Bitmap=New-Object Drawing.Bitmap($Image);[void]$Bitmap.GetPixel($Bitmap.Width-1,$Bitmap.Height-1)
  }finally{if($null-ne$Bitmap){$Bitmap.Dispose()};if($null-ne$Image){$Image.Dispose()};$Memory.Dispose()}
  $PngCount++
 }
 Write-CheckLog ('PASS: all '+$PngCount+' PNG entries decode with positive dimensions and readable pixels.')
 foreach($File in $OverrideFiles){Require ($InputHashes[$File.FullName]-eq(Bytes-Hash ([IO.File]::ReadAllBytes($File.FullName)))) ('Override changed concurrently while checking: '+$File.Name)}
 $SafePrefix=[IO.Path]::GetFullPath((Join-Path $CoreDir 'build'))+[IO.Path]::DirectorySeparatorChar
 Require ([IO.Path]::GetFullPath($FallbackPath).StartsWith($SafePrefix,[StringComparison]::OrdinalIgnoreCase)) 'Unsafe fallback output path'
 [IO.File]::Copy($PackPath,$FallbackPath,$true)
 $Fallback=[IO.Compression.ZipFile]::Open($FallbackPath,[IO.Compression.ZipArchiveMode]::Update)
 foreach($Name in $Removed){$Entry=$Fallback.GetEntry($Name);Require ($null-ne$Entry) ('Fallback source definition missing: '+$Name);$Entry.Delete()}
 $Fallback.Dispose();$Fallback=[IO.Compression.ZipFile]::OpenRead($FallbackPath)
 Require ($Fallback.Entries.Count-eq$Archive.Entries.Count-6) 'Fallback copy must remove exactly six entries'
 foreach($Entry in $Archive.Entries){
  $Copy=$Fallback.GetEntry($Entry.FullName)
  if($Removed-contains$Entry.FullName){Require ($null-eq$Copy) ('Fallback definition was not removed: '+$Entry.FullName)}
  else{Require ($null-ne$Copy-and(Bytes-Hash (Entry-Bytes $Entry))-eq(Bytes-Hash (Entry-Bytes $Copy))) ('Fallback changed a retained entry: '+$Entry.FullName)}
 }
 Write-CheckLog 'PASS: fallback COPY removes only six v3.1 item definitions; every retained model/texture/entry is byte-identical.'
 Write-CheckLog ('Fallback pack: '+$FallbackPath)
 Write-CheckLog ('Source pack: '+$PackPath)
 Write-CheckLog 'V31_PACK_CHECKS PASS'
 exit 0
}catch{
 Write-CheckLog ('V31_PACK_CHECKS FAILED: '+$_.Exception.Message)
 exit 1
}finally{
 if($null-ne$Fallback){$Fallback.Dispose()}
 if($null-ne$Archive){$Archive.Dispose()}
}
