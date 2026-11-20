param([switch]$RefreshData, [switch]$WithAuthoredPack)
$ErrorActionPreference='Stop'
$CoreDir=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$RepoDir=Split-Path -Parent $CoreDir
$TestServer=[IO.Path]::GetFullPath((Join-Path $CoreDir 'build\server-integration'))
$ExpectedPrefix=[IO.Path]::GetFullPath((Join-Path $CoreDir 'build'))+[IO.Path]::DirectorySeparatorChar
function Assert-BuildOutput([string]$Path){
 $Resolved=[IO.Path]::GetFullPath($Path)
 if(-not $Resolved.StartsWith($ExpectedPrefix,[StringComparison]::OrdinalIgnoreCase)){throw ('Unsafe build output: '+$Resolved)}
 for($Parent=$Resolved;$Parent-and$Parent.StartsWith($CoreDir,[StringComparison]::OrdinalIgnoreCase);$Parent=Split-Path -Parent $Parent){
  $Existing=Get-Item -LiteralPath $Parent -ErrorAction SilentlyContinue
  if($null-ne$Existing-and($Existing.Attributes-band[IO.FileAttributes]::ReparsePoint)){throw ('Build output must not redirect through a link: '+$Parent)}
  if($Parent.Equals($CoreDir,[StringComparison]::OrdinalIgnoreCase)){break}
 }
}
function Set-EmfLocalProfileFixture([string]$Production,[string]$TestServer,[string]$Evidence){
 # EMF's bundled KasaiSora head performs a Mojang lookup on every fresh process.
 # Only the disposable copy uses an existing local head texture; fish rules remain unchanged.
 $Source=Join-Path $Production 'plugins\EvenMoreFish\rarities\legendary.yml'
 if(-not(Test-Path -LiteralPath $Source)){throw 'Installed EMF legendary config is required for the local profile fixture'}
 $Target=Join-Path $TestServer 'plugins\EvenMoreFish\rarities\legendary.yml'
 $Proof=Join-Path $Evidence 'emf-local-profile-fixture.json'
 Assert-BuildOutput $Target
 Assert-BuildOutput $Proof
 $SourceBytes=[IO.File]::ReadAllBytes($Source)
 $Utf8=New-Object Text.UTF8Encoding($false,$true)
 $SourceText=$Utf8.GetString($SourceBytes)
 $Textures=[regex]::Matches($SourceText,'(?m)^[ \t]+head-64:[ \t]*([A-Za-z0-9+/=]+)[ \t]*\r?$')
 if($Textures.Count-eq0){throw 'EMF profile fixture has no local head texture to reuse'}
 $Texture=$Textures[$Textures.Count-1].Groups[1].Value
 $TextureJson=$Utf8.GetString([Convert]::FromBase64String($Texture))|ConvertFrom-Json
 if($TextureJson.textures.SKIN.url-notmatch'^https?://textures\.minecraft\.net/texture/[0-9a-f]+$'){throw 'Invalid native EMF head texture fixture'}
 $Before=$SourceBytes
 if(Test-Path -LiteralPath $Target){$Before=[IO.File]::ReadAllBytes($Target)}
 $Text=$Utf8.GetString($Before)
 $Uuid='916b1144-e589-4af7-8d4b-79aab81f614f'
 $Pattern=New-Object Text.RegularExpressions.Regex('(?m)^([ \t]+)head-uuid:[ \t]*'+$Uuid+'[ \t]*(\r?)$')
 $Matches=$Pattern.Matches($Text)
 if($Matches.Count-gt1){throw 'Expected only the single bundled EMF unresolved profile'}
 $Changed=$Matches.Count-eq1
 if($Changed){$Text=$Pattern.Replace($Text,('${1}head-64: '+$Texture+'${2}'))}
 elseif($Text-notmatch('(?m)^[ \t]+head-64:[ \t]*'+[regex]::Escape($Texture)+'[ \t]*\r?$')){throw 'EMF local profile fixture neither matches the UUID nor its normalized texture'}
 if($Text-match('head-uuid:[ \t]*'+$Uuid)){throw 'Unresolved fixture profile remains'}
 $After=$Utf8.GetBytes($Text)
 [void][IO.Directory]::CreateDirectory((Split-Path -Parent $Target))
 if($Changed-or-not(Test-Path -LiteralPath $Target)){[IO.File]::WriteAllBytes($Target,$After)}
 $Sha=[Security.Cryptography.SHA256]::Create()
 try{
  $Result=[ordered]@{testOnly=$true;uuid=$Uuid;source=$Source;target=$Target;sourceSha256=([BitConverter]::ToString($Sha.ComputeHash($SourceBytes))).Replace('-','').ToLowerInvariant();beforeSha256=([BitConverter]::ToString($Sha.ComputeHash($Before))).Replace('-','').ToLowerInvariant();afterSha256=([BitConverter]::ToString($Sha.ComputeHash($After))).Replace('-','').ToLowerInvariant();textureSha256=([BitConverter]::ToString($Sha.ComputeHash($Utf8.GetBytes($Texture)))).Replace('-','').ToLowerInvariant();changed=$Changed;note='Only KasaiSora head appearance uses an existing local EMF texture. Headless gameplay checks do not certify its original skin. Production and ERROR verification remain unchanged.'}
  [IO.File]::WriteAllText($Proof,($Result|ConvertTo-Json -Depth 4),(New-Object Text.UTF8Encoding($true)))
 }finally{$Sha.Dispose()}
 Write-Host ('CORE_EMF_LOCAL_PROFILE_FIXTURE: '+$Uuid+'; changed='+$Changed+'; proof='+$Proof)
}
Assert-BuildOutput $TestServer
if((Get-Item -LiteralPath $TestServer -ErrorAction SilentlyContinue).Attributes-band[IO.FileAttributes]::ReparsePoint){throw 'Test server must not redirect to another directory'}
$PortProbe=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,25567)
try{$PortProbe.Start()}finally{$PortProbe.Stop()}
$Production=Join-Path $RepoDir 'server'
$Evidence=Join-Path $CoreDir 'build\verification-core'
Assert-BuildOutput $Evidence
[void][IO.Directory]::CreateDirectory($Evidence)
foreach($Relative in @('plugins','plugins\YuumiLandCore','resourcepack','config')){[void][IO.Directory]::CreateDirectory((Join-Path $TestServer $Relative))}
foreach($Name in @('paper.jar','spigot.yml','bukkit.yml')){
 $Source=Join-Path $Production $Name
 if(Test-Path -LiteralPath $Source){Copy-Item -LiteralPath $Source -Destination (Join-Path $TestServer $Name) -Force}
}
foreach($Name in @('cache','libraries','versions')){
 if(-not(Test-Path -LiteralPath (Join-Path $TestServer $Name))){Copy-Item -LiteralPath (Join-Path $Production $Name) -Destination (Join-Path $TestServer $Name) -Recurse}
}
foreach($Pattern in @('VaultUnlocked*.jar','EssentialsX*.jar','AuraSkills*.jar','TAB-*.jar','WorldEdit*.jar','EvenMoreFish*.jar')){
 foreach($File in Get-ChildItem -LiteralPath (Join-Path $Production 'plugins') -Filter $Pattern -File){Copy-Item -LiteralPath $File.FullName -Destination (Join-Path $TestServer 'plugins') -Force}
}
Set-EmfLocalProfileFixture $Production $TestServer $Evidence
$DataSource=Join-Path $Production 'plugins\YuumiLandCore\data.yml'
if($RefreshData-or-not(Test-Path -LiteralPath (Join-Path $TestServer 'plugins\YuumiLandCore\data.yml'))){
 Copy-Item -LiteralPath $DataSource -Destination (Join-Path $TestServer 'plugins\YuumiLandCore\data.yml') -Force
 Copy-Item -LiteralPath $DataSource -Destination (Join-Path $Evidence 'baseline-data.yml') -Force
 $Vaults=Join-Path $Production 'plugins\YuumiLandCore\vaults'
 if(Test-Path -LiteralPath $Vaults){Copy-Item -LiteralPath $Vaults -Destination (Join-Path $TestServer 'plugins\YuumiLandCore') -Recurse -Force}
}
Copy-Item -LiteralPath (Join-Path $CoreDir 'build\libs\YuumiLandCore.jar') -Destination (Join-Path $TestServer 'plugins\YuumiLandCore.jar') -Force
Copy-Item -LiteralPath (Join-Path $CoreDir 'build\integration-probe\CoreProbe.jar') -Destination (Join-Path $TestServer 'plugins\CoreProbe.jar') -Force
$Properties=@'
server-ip=127.0.0.1
server-port=25567
online-mode=false
enforce-secure-profile=false
level-name=world
view-distance=4
simulation-distance=4
max-players=12
spawn-protection=0
allow-flight=true
enable-command-block=false
enable-rcon=false
enable-query=false
sync-chunk-writes=true
motd=Disposable core integration tests
'@
[IO.File]::WriteAllText((Join-Path $TestServer 'server.properties'),$Properties,(New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText((Join-Path $TestServer 'eula.txt'),'eula=true',(New-Object Text.UTF8Encoding($false)))
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$PackSource=Join-Path $Production 'resourcepack\Yuumi97Pack.zip'
$PackTarget=Join-Path $TestServer 'resourcepack\Yuumi97Pack.zip'
Copy-Item -LiteralPath $PackSource -Destination $PackTarget -Force
if($WithAuthoredPack){
 # Each authored file is read once; the exact same bytes feed the snapshot and disposable ZIP.
 # The unique snapshot is immutable input for the later verifier while Astra keeps tuning live assets.
 $Overrides=Join-Path $RepoDir 'resourcepack-gen\overrides'
 $Snapshot=[IO.Path]::GetFullPath((Join-Path $Evidence ('overrides-snapshot-'+[Guid]::NewGuid().ToString('N'))))
 $SnapshotMarker=Join-Path $Evidence 'overrides-snapshot-path.txt'
 Assert-BuildOutput $Snapshot
 Assert-BuildOutput $SnapshotMarker
 Assert-BuildOutput $PackTarget
 [void][IO.Directory]::CreateDirectory($Snapshot)
 $SnapshotPrefix=$Snapshot+[IO.Path]::DirectorySeparatorChar
 $SnapshotCount=0
 $Archive=[IO.Compression.ZipFile]::Open($PackTarget,[IO.Compression.ZipArchiveMode]::Update)
 try{foreach($File in Get-ChildItem -LiteralPath $Overrides -File -Recurse){
  if($File.Name-in@('README.md','.gitkeep')){continue}
  $Relative=$File.FullName.Substring($Overrides.Length+1).Replace('\','/')
  $SnapshotFile=[IO.Path]::GetFullPath((Join-Path $Snapshot $Relative.Replace('/','\')))
  if(-not $SnapshotFile.StartsWith($SnapshotPrefix,[StringComparison]::OrdinalIgnoreCase)){throw ('Unsafe snapshot member: '+$Relative)}
  [byte[]]$Bytes=[IO.File]::ReadAllBytes($File.FullName)
  [void][IO.Directory]::CreateDirectory((Split-Path -Parent $SnapshotFile))
  [IO.File]::WriteAllBytes($SnapshotFile,$Bytes)
  $Old=$Archive.GetEntry($Relative);if($null-ne$Old){$Old.Delete()}
  $Entry=$Archive.CreateEntry($Relative,[IO.Compression.CompressionLevel]::Optimal)
  $EntryStream=$Entry.Open()
  try{$EntryStream.Write($Bytes,0,$Bytes.Length)}finally{$EntryStream.Dispose()}
  $SnapshotCount++
 }}finally{$Archive.Dispose()}
 [IO.File]::WriteAllText($SnapshotMarker,$Snapshot,(New-Object Text.UTF8Encoding($false)))
 Write-Host ('CORE_OVERRIDE_SNAPSHOT: '+$Snapshot+'; files='+$SnapshotCount)
}
$PackHash=(Get-FileHash -LiteralPath $PackTarget -Algorithm SHA1).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $TestServer 'resourcepack\Yuumi97Pack.sha1'),$PackHash)
Write-Host ('CORE_TEST_SERVER prepared: '+$TestServer+'; authored pack='+$WithAuthoredPack)
