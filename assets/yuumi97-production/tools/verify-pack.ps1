$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$rootDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$pack = Join-Path $rootDir 'server\resourcepack\Yuumi97Pack.zip'
$expectedHash = (Get-Content -LiteralPath (Join-Path $rootDir 'server\resourcepack\Yuumi97Pack.sha1') -Raw).Trim()
$actualHash = (Get-FileHash -LiteralPath $pack -Algorithm SHA1).Hash.ToLowerInvariant()
if ($expectedHash -ne $actualHash) { throw 'Pack SHA-1 mismatch' }
$zip = [System.IO.Compression.ZipFile]::OpenRead($pack)
try {
    $names = @($zip.Entries | ForEach-Object FullName)
    if (($names | Select-Object -Unique).Count -ne $names.Count) { throw 'Duplicate ZIP entry' }
    $itemEntries = @($zip.Entries | Where-Object FullName -Like 'assets/yuumi97/items/*.json')
    if ($itemEntries.Count -ne 33) { throw "Expected 33 item definitions, found $($itemEntries.Count)" }
    function Read-ZipText($entry) {
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    function Check-ItemNode($node) {
        if ($node.type -eq 'minecraft:model') {
            $target = 'assets/' + $node.model.Replace(':','/models/') + '.json'
            if (-not $zip.GetEntry($target)) { throw "Missing item model: $target" }
        } elseif ($node.type -eq 'minecraft:select') {
            foreach ($case in $node.cases) { Check-ItemNode $case.model }
            Check-ItemNode $node.fallback
        } else { throw "Unexpected item model type: $($node.type)" }
    }
    foreach ($entry in $itemEntries) { Check-ItemNode ((Read-ZipText $entry | ConvertFrom-Json).model) }
    $format = (Read-ZipText ($zip.GetEntry('pack.mcmeta')) | ConvertFrom-Json).pack.pack_format
    if ($format -ne 46) { throw 'Wrong resource-pack format' }
    $overrideRoot = Join-Path $rootDir 'resourcepack-gen\overrides'
    $overrideCount = 0
    foreach ($file in Get-ChildItem -LiteralPath $overrideRoot -File -Recurse) {
        if ($file.Name -in @('README.md','.gitkeep')) { continue }
        $relative = $file.FullName.Substring($overrideRoot.Length + 1).Replace('\','/')
        $entry = $zip.GetEntry($relative)
        if (-not $entry) { throw "Override absent from ZIP: $relative" }
        $stream = $entry.Open()
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { $packedHash = [Convert]::ToHexString($sha.ComputeHash($stream)) }
        finally { $sha.Dispose(); $stream.Dispose() }
        if ($packedHash -ne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) { throw "Stale/different override: $relative" }
        $overrideCount++
    }
    $builtJar = (Get-FileHash -LiteralPath (Join-Path $rootDir 'yuumi-core\build\libs\YuumiLandCore.jar') -Algorithm SHA256).Hash
    $deployedJar = (Get-FileHash -LiteralPath (Join-Path $rootDir 'server\plugins\YuumiLandCore.jar') -Algorithm SHA256).Hash
    if ($builtJar -ne $deployedJar) { throw 'Deployed JAR differs from build output' }
    $result = [ordered]@{status='PASS'; timestamp=(Get-Date).ToString('o'); packFormat=$format; sha1=$actualHash; zipBytes=(Get-Item -LiteralPath $pack).Length; zipEntries=$names.Count; itemDefinitions=$itemEntries.Count; verifiedOverrideFiles=$overrideCount; jarSha256=$deployedJar; limitations='CRC is not independently recomputed; ZIP entries are decompressed and all override contents SHA-256 compared.'}
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $rootDir 'assets\yuumi97-production\review\pack-validation.json') -Encoding utf8
    $result | ConvertTo-Json
} finally { $zip.Dispose() }
