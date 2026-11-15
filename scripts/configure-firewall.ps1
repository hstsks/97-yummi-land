#Requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'

$RootDir = Split-Path -Parent $PSScriptRoot
$JavaExe = Join-Path $RootDir 'runtime\jdk-21\bin\java.exe'
if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) {
    throw "Portable Java was not found: $JavaExe"
}

# Only the server's portable Java process may receive traffic on these ports.
# The current Ethernet connection uses the Private Windows network profile.
$Rules = @(
    @{ Name = 'Yuumi97Land-Game-TCP'; Label = 'Yuumi97Land game'; Protocol = 'TCP'; Port = 25565 },
    @{ Name = 'Yuumi97Land-Pack-TCP'; Label = 'Yuumi97Land resource pack'; Protocol = 'TCP'; Port = 25566 },
    @{ Name = 'Yuumi97Land-Voice-UDP'; Label = 'Yuumi97Land voice chat'; Protocol = 'UDP'; Port = 24454 }
)

foreach ($Rule in $Rules) {
    $Existing = Get-NetFirewallRule -Name $Rule.Name -ErrorAction SilentlyContinue
    if ($Existing) {
        $Existing | Set-NetFirewallRule -Enabled True -Direction Inbound -Action Allow -Profile Private
        $Existing | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol $Rule.Protocol -LocalPort $Rule.Port -RemotePort Any
        $Existing | Get-NetFirewallApplicationFilter | Set-NetFirewallApplicationFilter -Program $JavaExe
    } else {
        New-NetFirewallRule -Name $Rule.Name -DisplayName $Rule.Label -Group 'Yuumi97Land' `
            -Enabled True -Direction Inbound -Action Allow -Profile Private `
            -Program $JavaExe -Protocol $Rule.Protocol -LocalPort $Rule.Port | Out-Null
    }
}

Get-NetFirewallRule -Group 'Yuumi97Land' | Select-Object DisplayName, Enabled, Profile
