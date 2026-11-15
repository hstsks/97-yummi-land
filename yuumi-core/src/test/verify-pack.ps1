param([string]$RootDir = (Join-Path $PSScriptRoot '..\..\..'))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$RootDir=[IO.Path]::GetFullPath($RootDir)
$source=Get-Content -LiteralPath (Join-Path $RootDir 'yuumi-core\src\main\java\kr\yuumi97\core\items\YuumiItems.java') -Raw
$catalog=[regex]::Match($source,'V3_IDS = java\.util\.List\.of\(([\s\S]*?)\);').Groups[1].Value
$ids=@([regex]::Matches($catalog,'"([a-z0-9_]+)"') | ForEach-Object {$_.Groups[1].Value})
if($ids.Count -ne 30){throw 'Expected 30 custom item IDs'}
$ids += @('hat_yuumi_ears','plushie_yuumi')
function Read-Entry($zip,[string]$name) {
  $entry=$zip.GetEntry($name)
  if($null -eq $entry){throw "Missing resource $name"}
  $stream=$entry.Open();$memory=New-Object IO.MemoryStream
  try {$stream.CopyTo($memory);return ,$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}
}
foreach($relative in @('yuumi-core\build\fallback-verification\server\resourcepack\Yuumi97Pack.zip','server\resourcepack\Yuumi97Pack.zip')) {
  $path=Join-Path $RootDir $relative
  $zip=[IO.Compression.ZipFile]::OpenRead($path)
  try {
    foreach($id in $ids) {
      $png=Read-Entry $zip "assets/yuumi97/textures/item/$id.png"
      if($png[0] -ne 137 -or $png[1] -ne 80){throw "Invalid PNG $id"}
      $definition=[Text.Encoding]::UTF8.GetString((Read-Entry $zip "assets/yuumi97/items/$id.json")) | ConvertFrom-Json
      if($null -eq $definition.model){throw "Missing item model $id"}
      [void](Read-Entry $zip "assets/yuumi97/models/item/$id.json")
      if($relative.StartsWith('yuumi-core')) {
        if($png[19] -ne 16 -or $png[23] -ne 16){throw "Fallback must be 16x16: $id"}
      }
    }
    foreach($entry in $zip.Entries) {
      if(-not $entry.FullName.EndsWith('.json')){continue}
      $raw=[Text.Encoding]::UTF8.GetString((Read-Entry $zip $entry.FullName))
      [void]($raw | ConvertFrom-Json)
      foreach($match in [regex]::Matches($raw,'"(?:model|parent)"\s*:\s*"yuumi97:([^"#]+)"')) {
        [void](Read-Entry $zip ('assets/yuumi97/models/'+$match.Groups[1].Value+'.json'))
      }
      if($entry.FullName.Contains('/models/')) {
        foreach($match in [regex]::Matches($raw,'"yuumi97:((?:prop|item|gui)/[^"#]+)"')) {
          [void](Read-Entry $zip ('assets/yuumi97/textures/'+$match.Groups[1].Value+'.png'))
        }
      }
    }
    if(-not $relative.StartsWith('yuumi-core')) {
      $overrides=Join-Path $RootDir 'resourcepack-gen\overrides'
      foreach($file in Get-ChildItem -LiteralPath $overrides -File -Recurse) {
        if($file.Name -eq 'README.md' -or $file.Name -eq '.gitkeep'){continue}
        $name=$file.FullName.Substring($overrides.Length+1).Replace('\','/')
        $actual=Read-Entry $zip $name;$expected=[IO.File]::ReadAllBytes($file.FullName)
        if([Convert]::ToBase64String($actual) -cne [Convert]::ToBase64String($expected)){throw "Overlay differs (possibly concurrent edit): $name"}
      }
    }
    Write-Output "PACK_CHECK_PASS: $relative; 30 new + 2 legacy reward definitions, models and PNGs; all JSON references; override bytes preserved"
  } finally {$zip.Dispose()}
}
$actualHash=(Get-FileHash -LiteralPath (Join-Path $RootDir 'server\resourcepack\Yuumi97Pack.zip') -Algorithm SHA1).Hash.ToLowerInvariant()
$recorded=(Get-Content -LiteralPath (Join-Path $RootDir 'server\resourcepack\Yuumi97Pack.sha1') -Raw).Trim()
if($actualHash -ne $recorded){throw 'Resource pack SHA-1 mismatch'}
Write-Output "PACK_SHA1_PASS: $actualHash"
