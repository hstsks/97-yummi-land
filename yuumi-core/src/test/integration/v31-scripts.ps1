# Run: powershell.exe -NoProfile -ExecutionPolicy Bypass -File yuumi-core\src\test\integration\v31-scripts.ps1
# Builds a tiny mock console executable inside core/build; never starts Paper or invokes build-all's main body.
$ErrorActionPreference='Stop'
[Console]::InputEncoding=New-Object Text.UTF8Encoding($false)
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=New-Object Text.UTF8Encoding($false)
$CoreDir=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$RepoDir=Split-Path -Parent $CoreDir
$SourceScripts=Join-Path $RepoDir 'scripts'
$BuildDir=Join-Path $CoreDir 'build'
$FixtureBase=Join-Path $BuildDir 'script-checks'
$FixtureRoot=Join-Path $FixtureBase ('Yuumi97V31-ScriptTest-'+[Guid]::NewGuid().ToString('N'))
$SummaryDir=Join-Path $BuildDir 'verification-v31'
[void][IO.Directory]::CreateDirectory($SummaryDir)
$SummaryLog=Join-Path $SummaryDir 'scripts-checks.log'
$LogEncoding=New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($SummaryLog,('Script fixtures: mock processes only; no real Paper/build/deployment'+[Environment]::NewLine),(New-Object Text.UTF8Encoding($true)))
function Write-TestLog([string]$Message){
 Write-Host $Message
 [IO.File]::AppendAllText($SummaryLog,$Message+[Environment]::NewLine,$LogEncoding)
}
try {
 if($PSVersionTable.PSVersion.Major-ne5-or$PSVersionTable.PSVersion.Minor-ne1){throw 'Run this suite with Windows PowerShell 5.1, not pwsh.'}
 $ExpectedPrefix=[IO.Path]::GetFullPath($FixtureBase)+[IO.Path]::DirectorySeparatorChar
 if(-not [IO.Path]::GetFullPath($FixtureRoot).StartsWith($ExpectedPrefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe fixture path'}
 [void][IO.Directory]::CreateDirectory($FixtureRoot)
 Write-TestLog ('Windows PowerShell '+$PSVersionTable.PSVersion+'; fixture '+$FixtureRoot)
$Ps51=Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$Utf8=New-Object Text.UTF8Encoding($true)
$FixtureScripts=Join-Path $FixtureRoot 'scripts'
$FixtureServer=Join-Path $FixtureRoot 'server'
foreach($Dir in @($FixtureScripts,(Join-Path $FixtureServer 'logs'),(Join-Path $FixtureRoot 'runtime\jdk-21\bin'))){[void][IO.Directory]::CreateDirectory($Dir)}
$Probe=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
$Probe.Start();$FixturePort=$Probe.LocalEndpoint.Port;$Probe.Stop()
foreach($Name in @('start-server.ps1','verify-boot.ps1','backup-world.ps1')){
 $Content=[IO.File]::ReadAllText((Join-Path $SourceScripts $Name),[Text.Encoding]::UTF8).Replace('25565',[string]$FixturePort)
 [IO.File]::WriteAllText((Join-Path $FixtureScripts $Name),$Content,$Utf8)
}
[IO.File]::WriteAllText((Join-Path $FixtureServer 'paper.jar'),'fixture-only')
[IO.File]::WriteAllText((Join-Path $FixtureScripts 'bootstrap-runtime.ps1'),"throw 'Unexpected bootstrap side effect'", $Utf8)
$Source=@"
using System;using System.IO;using System.Text;using System.Threading;
public class ScriptFixtureJvm {
 public static int Main(string[] args) {
  string cwd=Environment.CurrentDirectory;
  if(cwd.IndexOf("Yuumi97V31-ScriptTest-",StringComparison.OrdinalIgnoreCase)<0) return 90;
  string file=Path.Combine(cwd,"mock-mode.txt"),mode=File.Exists(file)?File.ReadAllText(file).Trim():"pass";
  File.WriteAllText(Path.Combine(cwd,"mock-pid.txt"),System.Diagnostics.Process.GetCurrentProcess().Id.ToString());
  if(mode=="early-nonzero") return 23;
  if(mode=="cycle"){string n=Path.Combine(cwd,"mock-cycle.txt");int c=File.Exists(n)?int.Parse(File.ReadAllText(n)):0;File.WriteAllText(n,(c+1).ToString());return c==0?0:23;}
  Directory.CreateDirectory(Path.Combine(cwd,"logs"));string log=Path.Combine(cwd,"logs","latest.log");
  Action<string> emit=delegate(string s){File.AppendAllText(log,s+Environment.NewLine,new UTF8Encoding(false));Console.WriteLine(s);};
  Console.OutputEncoding=new UTF8Encoding(false);emit("[FIXTURE ONLY] \uC720\uBBF8 \uD55C\uAE00 UTF8");
  if(mode!="no-ready")emit("[FIXTURE ONLY] Done (0.1s)! For help, type help");
  if(mode!="no-hub")emit("V31_HUB_READY");
  bool blue=mode=="blue-delayed"||mode=="blue-never";int blueLoaded=0;
  if(blue){emit("[BlueMap] Loading resources...");
   if(mode=="blue-delayed"){new Thread(delegate(){Thread.Sleep(1200);Interlocked.Exchange(ref blueLoaded,1);emit("[BlueMap] Loaded!");}).Start();}
  }
  var input=new StreamReader(Console.OpenStandardInput(),new UTF8Encoding(false),false);
  bool first=true;string line;while((line=input.ReadLine())!=null){
   if(first){File.WriteAllText(Path.Combine(cwd,"mock-first-input.hex"),BitConverter.ToString(new UTF8Encoding(false).GetBytes(line)));first=false;}
   if(line.Length>0&&line[0]=='\uFEFF'){emit("[ERROR] UTF8 BOM reached first console command");return 25;}
   if(line=="yuumi verify-v3"){
    if(mode=="fail-check")emit("V31_RUNTIME_CHECKS FAILED: fixture");
    else{emit("V3_RUNTIME_CHECKS PASS");if(mode!="old-only")emit("V31_RUNTIME_CHECKS PASS");}
    if(mode=="log-error")emit("[ERROR] fixture rejection test");
   }
   if(line=="stop"){if(mode=="hang-stop")Thread.Sleep(15000);if(blue&&Interlocked.CompareExchange(ref blueLoaded,0,0)==0){emit("[ERROR] stopped BlueMap during asynchronous loading");return 26;}emit("Stopping server");return 0;}
  }return 0;
 }
}
"@
if (-not (Test-Path -LiteralPath (Join-Path $FixtureRoot 'runtime\jdk-21\bin\java.exe'))) { Add-Type -TypeDefinition $Source -OutputAssembly (Join-Path $FixtureRoot 'runtime\jdk-21\bin\java.exe') -OutputType ConsoleApplication }
function Check([bool]$Passed,[string]$Message){if(-not $Passed){throw "CHECK FAILED: $Message"};Write-TestLog "CHECK PASS: $Message"}
function Invoke-Fixture([string]$Name,[string]$CommandArguments='', [string]$InputText=''){
 $Psi=New-Object Diagnostics.ProcessStartInfo;$Psi.FileName=$Ps51
 $Psi.Arguments='-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $FixtureScripts $Name)+'" '+$CommandArguments
 $Psi.WorkingDirectory=$FixtureRoot;$Psi.UseShellExecute=$false;$Psi.CreateNoWindow=$true
 $Psi.RedirectStandardOutput=$true;$Psi.RedirectStandardError=$true;$Psi.RedirectStandardInput=$true
 $Psi.StandardOutputEncoding=New-Object Text.UTF8Encoding($false);$Psi.StandardErrorEncoding=New-Object Text.UTF8Encoding($false)
 $P=New-Object Diagnostics.Process;$P.StartInfo=$Psi;[void]$P.Start()
 $O=$P.StandardOutput.ReadToEndAsync();$E=$P.StandardError.ReadToEndAsync()
 if($InputText-ne''){$P.StandardInput.WriteLine($InputText);$P.StandardInput.Flush()}
 if(-not $P.WaitForExit(30000)){$P.Kill();[void]$P.WaitForExit(10000);throw "Fixture child timeout: $Name"}
 $Result=[pscustomobject]@{Code=$P.ExitCode;Text=$O.GetAwaiter().GetResult();Error=$E.GetAwaiter().GetResult()};$P.Dispose();return $Result
}
$Sentinel=Join-Path $FixtureServer 'logs\latest.log'
[IO.File]::WriteAllText($Sentinel,'GUARD-SENTINEL')
$Lease=[IO.File]::Open((Join-Path $FixtureServer '.yuumi-server.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{foreach($Name in @('start-server.ps1','verify-boot.ps1')){
 $R=Invoke-Fixture $Name;Check ($R.Code-ne0) "$Name rejects shared lock"
 Check ([IO.File]::ReadAllText($Sentinel)-eq'GUARD-SENTINEL') "$Name preserves locked log"
}}finally{$Lease.Dispose()}
$Listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,$FixturePort);$Listener.Start()
try{foreach($Name in @('start-server.ps1','verify-boot.ps1')){
 $R=Invoke-Fixture $Name;Check ($R.Code-ne0) "$Name rejects occupied port"
 Check ([IO.File]::ReadAllText($Sentinel)-eq'GUARD-SENTINEL') "$Name preserves occupied log"
}}finally{$Listener.Stop()}
[IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),'pass')
$R=Invoke-Fixture 'verify-boot.ps1' '-StartupTimeoutSeconds 5 -CheckTimeoutSeconds 5 -ShutdownTimeoutSeconds 2'
Check ($R.Code-eq0) ('mock hub+V3+V31+graceful stop accepted: '+$R.Error)
Check ($R.Text-match'V31_RUNTIME_CHECKS PASS') 'current UTF8 log captured'
Check ([IO.File]::ReadAllText((Join-Path $FixtureServer 'mock-first-input.hex'))-eq'70-6C-75-67-69-6E-73') 'first native console command is plugins without UTF8 preamble'
$VerifierCopy=Join-Path $FixtureScripts 'verify-boot.ps1'
$VerifierGood=[IO.File]::ReadAllText($VerifierCopy,[Text.Encoding]::UTF8)
$VerifierBad=$VerifierGood.Replace('$Utf8NoBom = New-Object Text.UTF8Encoding($false)','$Utf8NoBom = [Text.Encoding]::UTF8')
[IO.File]::WriteAllText($VerifierCopy,$VerifierBad,$Utf8)
$R=Invoke-Fixture 'verify-boot.ps1' '-StartupTimeoutSeconds 5 -CheckTimeoutSeconds 5 -ShutdownTimeoutSeconds 2'
Check ($R.Code-ne0-and[IO.File]::ReadAllText((Join-Path $FixtureServer 'mock-first-input.hex')).StartsWith('EF-BB-BF')) 'regression fixture detects former UTF8-BOM console bug'
[IO.File]::WriteAllText($VerifierCopy,$VerifierGood,$Utf8)
$FixturePlugins=Join-Path $FixtureServer 'plugins'
[void][IO.Directory]::CreateDirectory($FixturePlugins)
[IO.File]::WriteAllText((Join-Path $FixturePlugins 'BlueMap-fixture.jar'),'mock only')
[IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),'blue-delayed')
$R=Invoke-Fixture 'verify-boot.ps1' '-StartupTimeoutSeconds 5 -CheckTimeoutSeconds 5 -ShutdownTimeoutSeconds 2 -BlueMapTimeoutSeconds 5'
Check ($R.Code-eq0) ('verifier waits for actual delayed BlueMap Loaded marker: '+$R.Error)
[IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),'blue-never')
$R=Invoke-Fixture 'verify-boot.ps1' '-StartupTimeoutSeconds 5 -CheckTimeoutSeconds 5 -ShutdownTimeoutSeconds 2 -BlueMapTimeoutSeconds 1'
Check ($R.Code-ne0) 'missing BlueMap readiness is a bounded failure, never an ignored exception'
Remove-Item -LiteralPath (Join-Path $FixturePlugins 'BlueMap-fixture.jar') -Force
foreach($Mode in @('fail-check','old-only','no-hub','log-error','no-ready','hang-stop')){
 [IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),$Mode)
 $R=Invoke-Fixture 'verify-boot.ps1' '-StartupTimeoutSeconds 1 -CheckTimeoutSeconds 1 -ShutdownTimeoutSeconds 1'
 Check ($R.Code-ne0) "verifier rejects $Mode"
 $MockPid=[int][IO.File]::ReadAllText((Join-Path $FixtureServer 'mock-pid.txt'))
 Check ($null-eq(Get-Process -Id $MockPid -ErrorAction SilentlyContinue)) "$Mode child cleaned up"
}
[IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),'pass')
$R=Invoke-Fixture 'start-server.ps1' '-Once' 'stop';Check ($R.Code-eq0) ('Once normal exit: '+$R.Text+$R.Error)
Check (@(Get-ChildItem -LiteralPath (Join-Path $FixtureRoot 'backups') -Filter '*.zip' -ErrorAction SilentlyContinue).Count-eq0) 'Once keeps no-backup behavior'
[IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),'early-nonzero')
$R=Invoke-Fixture 'start-server.ps1';Check ($R.Code-eq23) 'abnormal JVM stops without retry'
Check (-not(Test-Path -LiteralPath (Join-Path $FixtureRoot 'backups'))) 'abnormal JVM creates no backup'
foreach($Relative in @('world','world_nether','world_the_end','yuumi_hub','plugins\YuumiLandCore\vaults')){
 $Dir=Join-Path $FixtureServer $Relative;[void][IO.Directory]::CreateDirectory($Dir);[IO.File]::WriteAllText((Join-Path $Dir 'sentinel.txt'),$Relative)
}
$R=Invoke-Fixture 'backup-world.ps1';Check ($R.Code-eq0) ('fixture backup succeeds: '+$R.Text+$R.Error)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip=Get-ChildItem -LiteralPath (Join-Path $FixtureRoot 'backups') -Filter '*.zip'|Select-Object -First 1
$Archive=[IO.Compression.ZipFile]::OpenRead($Zip.FullName)
try{$Entries=@($Archive.Entries|ForEach-Object{$_.FullName.Replace('\','/')})
 Check ($Entries-contains'yuumi_hub/sentinel.txt') 'backup includes hub plots'
 Check ($Entries-contains'plugins/YuumiLandCore/vaults/sentinel.txt') 'backup includes private vaults'
}finally{$Archive.Dispose()}
[IO.File]::WriteAllText((Join-Path $FixtureServer 'mock-mode.txt'),'cycle')
$R=Invoke-Fixture 'start-server.ps1';Check ($R.Code-eq23) 'normal JVM backs up and restarts before next abnormal stop'
Check (([int][IO.File]::ReadAllText((Join-Path $FixtureServer 'mock-cycle.txt')))-eq2) 'exactly two mock launches'
Check (@(Get-ChildItem -LiteralPath (Join-Path $FixtureRoot 'backups') -Filter '*.zip').Count-eq2) 'normal restart adds one unique ZIP'
$BackupLease=[IO.File]::Open((Join-Path $FixtureRoot 'backups\.yuumi-backup.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{$R=Invoke-Fixture 'backup-world.ps1';Check ($R.Code-ne0) 'concurrent backup rejected'}finally{$BackupLease.Dispose()}
$R=Invoke-Fixture 'backup-world.ps1' '-KeepCount 1';Check ($R.Code-eq0) 'retention backup succeeds'
Check (@(Get-ChildItem -LiteralPath (Join-Path $FixtureRoot 'backups') -Filter '*.zip').Count-eq1) 'retention keeps requested completed snapshots'


$Files=@(Get-ChildItem -LiteralPath $SourceScripts -File -Filter '*.ps1')
Check ($Files.Count-eq10) 'exactly ten production PS1 scripts'
foreach($File in $Files){
 $Bytes=[IO.File]::ReadAllBytes($File.FullName)
 Check ($Bytes.Length-ge3-and$Bytes[0]-eq239-and$Bytes[1]-eq187-and$Bytes[2]-eq191) ($File.Name+' has UTF8 BOM')
 [void](New-Object Text.UTF8Encoding($false,$true)).GetString($Bytes)
 $Tokens=$null;$ParseErrors=$null
 [void][Management.Automation.Language.Parser]::ParseFile($File.FullName,[ref]$Tokens,[ref]$ParseErrors)
 Check (@($ParseErrors).Count-eq0) ($File.Name+' parses in Windows PowerShell5.1')
}
$Bat=[IO.File]::ReadAllBytes((Join-Path $RepoDir 'START_SERVER.bat'))
Check (@($Bat|Where-Object{$_-ge128}).Count-eq0) 'production BAT is pure ASCII'
Check ([IO.File]::ReadAllLines((Join-Path $RepoDir 'START_SERVER.bat')).Length-eq4) 'production BAT has four commands'
$Ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $SourceScripts 'build-all.ps1'),[ref]$null,[ref]$null)
$SnapshotFunction=$Ast.Find({param($Node)$Node-is[Management.Automation.Language.FunctionDefinitionAst]-and$Node.Name-eq'Get-ReadOnlyAssetSnapshot'},$true)
. ([scriptblock]::Create($SnapshotFunction.Extent.Text))
$RootDir=$FixtureRoot
foreach($Relative in @('assets\yuumi97-production','resourcepack-gen\overrides')){
 $Dir=Join-Path $FixtureRoot $Relative;[void][IO.Directory]::CreateDirectory($Dir);[IO.File]::WriteAllText((Join-Path $Dir 'fixture.txt'),'same')
}
$A=@(Get-ReadOnlyAssetSnapshot);$B=@(Get-ReadOnlyAssetSnapshot)
Check (@(Compare-Object $A $B).Count-eq0) 'stable read-only asset snapshots match'
[IO.File]::WriteAllText((Join-Path $FixtureRoot 'resourcepack-gen\overrides\fixture.txt'),'changed')
Check (@(Compare-Object $A @(Get-ReadOnlyAssetSnapshot)).Count-ne0) 'changed asset bytes are detected in fixture'
Write-TestLog 'SCRIPT_FIXTURE_CHECKS PASS (mock processes only; no real Paper/build/deployment)'
Write-TestLog ('Fixture evidence: '+$FixtureRoot)
Write-TestLog ('Summary: '+$SummaryLog)
exit 0
} catch {
 Write-TestLog ('SCRIPT_FIXTURE_CHECKS FAILED: '+$_.Exception.Message)
 exit 1
}