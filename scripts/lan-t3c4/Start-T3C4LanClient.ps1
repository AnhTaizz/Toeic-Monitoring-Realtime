param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][string]$BundleDirectory,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][ValidateSet('PROCTOR','CANDIDATE_A','CANDIDATE_B')][string]$Role,
    [ValidateSet('Full','DeltaExperimental')][string]$Mode='Full'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$origin=Get-LanOrigin $ServerUrl
$null=Get-LanBundle $BundleDirectory
$java=Get-LanJava $JavaHome
if(-not (Test-Path -LiteralPath (Join-Path $RunDirectory 'run.json'))) { throw 'INITIALIZE_RUN_FIRST' }
# Keep this profile across reconnect/relaunch. Deleting it could erase listening interruption markers.
$homeDirectory=Join-Path $RunDirectory ('profiles/'+$Role)
$measurements=Join-Path $RunDirectory ('private-measurements/'+$Role)
New-Item -ItemType Directory -Force -Path $homeDirectory,$measurements | Out-Null
$jar=(Resolve-Path -LiteralPath (Join-Path $BundleDirectory 'client/target/client-0.1.0-SNAPSHOT-all.jar')).Path
$runId='LAN-'+$Role+'-'+[Guid]::NewGuid().ToString()
$opts=@("-Duser.home=$((Resolve-Path -LiteralPath $homeDirectory).Path)","-Dtoeic.server.url=$($origin.GetLeftPart([UriPartial]::Authority))",
    "-Dtoeic.realtime.delta-experimental=$($Mode -eq 'DeltaExperimental')",'-Dtoeic.measurement.enabled=true',
    "-Dtoeic.measurement.directory=$((Resolve-Path -LiteralPath $measurements).Path)","-Dtoeic.measurement.runId=$runId",
    "-Dtoeic.measurement.sourceSha=$script:LanSourceSha",'-Dtoeic.measurement.workloadLabel=REAL','-Dtoeic.measurement.faultLabel=NONE','-jar',$jar)
Write-LanJson (Join-Path $RunDirectory ($runId+'-launch.json')) ([ordered]@{
    sourceSha=$script:LanSourceSha;runtimeSourceSha=$script:LanRuntimeSourceSha;clientJarSha256=$script:LanClientHash
    roleAlias=$Role;requestedMode=$Mode;fault='NONE';credentialEntry='GUI ONLY';lan='NOT RUN'
})
Write-Output 'Login using assigned TEST role in GUI. Keep private logs outside Git. Close only this app.'
& $java @opts
if($LASTEXITCODE -ne 0) { throw 'CLIENT_EXIT_NONZERO_CHECK_PRIVATE_LOG' }
