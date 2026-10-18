param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][string]$BundleDirectory,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][switch]$DedicatedTestAttempt,
    [Management.Automation.PSCredential]$Credential
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
if(-not $DedicatedTestAttempt) { throw 'DEDICATED_TEST_ATTEMPT_REQUIRED' }
$origin=Get-LanOrigin $ServerUrl
$null=Get-LanBundle $BundleDirectory
$java=Get-LanJava $JavaHome
if(-not (Test-Path -LiteralPath (Join-Path $RunDirectory 'run.json'))) { throw 'INITIALIZE_RUN_FIRST' }
if(-not $Credential) { $Credential=Get-Credential -Message 'Dedicated TEST candidate; SIMULATED fault console, not physical F' }
$directory=Join-Path $RunDirectory ('private-simulated-'+[Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $directory | Out-Null
$bundle=(Resolve-Path -LiteralPath $BundleDirectory).Path
$cp=(Join-Path $bundle 'client/target/client-0.1.0-SNAPSHOT-all.jar')+';'+(Join-Path $bundle 'test-support')
$oldUser=$env:TEST_GATE_USERNAME; $oldPassword=$env:TEST_GATE_PASSWORD
try {
    $env:TEST_GATE_USERNAME=$Credential.UserName; $env:TEST_GATE_PASSWORD=$Credential.GetNetworkCredential().Password
    Write-Output 'SIMULATED ONLY. stdin JSON actions; GATE_REPLY/raw screenshot may contain private TEST names. Do not publish raw.'
    & $java "-Duser.home=$directory" "-Dtoeic.server.url=$($origin.GetLeftPart([UriPartial]::Authority))" `
        "-Dtoeic.test.gate.output=$directory" '-Dtoeic.test.gate.instrumented=true' '-Dtoeic.realtime.delta-experimental=true' `
        '-cp' $cp 'vn.edu.toeic.client.LiveCandidateApp'
    if($LASTEXITCODE -ne 0) { throw 'FAULT_CHILD_EXIT_NONZERO' }
} finally {
    $env:TEST_GATE_USERNAME=$oldUser; $env:TEST_GATE_PASSWORD=$oldPassword; $Credential=$null
}
