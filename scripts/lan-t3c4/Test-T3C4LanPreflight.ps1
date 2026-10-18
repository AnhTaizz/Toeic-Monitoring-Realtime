param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][ValidateSet('MACHINE_A','MACHINE_B')][string]$Machine,
    [Parameter(Mandatory=$true)][string]$BundleDirectory,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][string]$JavaHome
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$origin=Get-LanOrigin $ServerUrl
$null=Get-LanBundle $BundleDirectory
if(-not (Test-Path -LiteralPath (Join-Path $RunDirectory 'run.json'))) { throw 'INITIALIZE_RUN_FIRST' }
$null=Get-LanJava $JavaHome
$addresses=@(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Select-Object -ExpandProperty IPAddress)
$isLocal=$addresses -contains $origin.Host
if(($Machine -eq 'MACHINE_A' -and -not $isLocal) -or ($Machine -eq 'MACHINE_B' -and $isLocal)) { throw 'TOPOLOGY_LOCAL_REMOTE_MISMATCH' }
$tcp=[Net.Sockets.TcpClient]::new()
try {
    $task=$tcp.ConnectAsync($origin.Host,$origin.Port)
    if(-not $task.Wait(5000) -or -not $tcp.Connected) { throw 'LAN_TCP_UNREACHABLE' }
} catch { throw 'LAN_TCP_UNREACHABLE_NO_FIREWALL_CHANGED' } finally { $tcp.Dispose() }
$reply=Invoke-LanHttp $origin '/api/v1/auth/me'
if($reply.status -ne 401) { throw 'AUTH_LISTENER_EXPECTED_401_NO_DB_HEALTH_CLAIM' }
$listenerOwned=$null; $firewallReadable=$false
if($Machine -eq 'MACHINE_A') {
    $listeners=@(Get-NetTCPConnection -State Listen -LocalPort $origin.Port -ErrorAction SilentlyContinue)
    $listenerOwned=@($listeners | Where-Object { $_.LocalAddress -in @($origin.Host,'0.0.0.0','::') }).Count -gt 0
    if(-not $listenerOwned) { throw 'LAN_LISTENER_NOT_BOUND' }
    try { $null=Get-NetFirewallProfile -ErrorAction Stop; $firewallReadable=$true } catch { }
}
$physicalUp=@(Get-NetAdapter -Physical -ErrorAction SilentlyContinue | Where-Object Status -eq 'Up').Count
Write-LanJson (Join-Path $RunDirectory ('preflight-'+$Machine+'.json')) ([ordered]@{
    schema='t3c4-preflight-v1';machine=$Machine;sourceSha=$script:LanSourceSha
    manifestSha256=Get-LanHash (Join-Path $BundleDirectory 'artifact-manifest.json')
    clientJarSha256=$script:LanClientHash;serverJarSha256=$script:LanServerHash
    utc=[DateTime]::UtcNow.ToString('o');serverAlias='MACHINE_A';port=$origin.Port
    addressIsLocal=$isLocal;tcpReachable=$true;unauthenticatedStatus=$reply.status
    physicalAdaptersUp=$physicalUp;listenerBound=$listenerOwned;firewallReadable=$firewallReadable
    firewallChanged=$false;adapterChanged=$false;databaseHealth='NOT VERIFIED'
    result='TRANSPORT_PREFLIGHT_ONLY';twoPhysicalMachines='REQUIRES HUMAN ATTESTATION';lanAcceptance='NOT RUN'
})
Write-Output 'PREFLIGHT_REACHABLE; artifact hashes match; DB/WS/physical cable loss NOT VERIFIED.'
