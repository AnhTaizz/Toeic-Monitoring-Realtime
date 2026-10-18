param([Parameter(Mandatory=$true)][string]$BundleDirectory,[Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$null=Get-LanBundle $BundleDirectory
if(Test-Path -LiteralPath $OutputDirectory) { throw 'RUN_EXISTS_NO_OVERWRITE' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
foreach($name in @('cases.csv','topology.json','review.json')) {
    Copy-Item -LiteralPath (Join-Path $BundleDirectory ('docs/templates/t3c4-lan/'+$name)) -Destination (Join-Path $OutputDirectory $name)
}
New-Item -ItemType Directory -Path (Join-Path $OutputDirectory 'checkpoints') | Out-Null
Write-LanJson (Join-Path $OutputDirectory 'run.json') ([ordered]@{
    schema='t3c4-physical-run-v1';runId=('T3C4-LAN-'+[Guid]::NewGuid().ToString())
    sourceSha=$script:LanSourceSha;runtimeSourceSha=$script:LanRuntimeSourceSha
    manifestSha256=Get-LanHash (Join-Path $BundleDirectory 'artifact-manifest.json')
    createdUtc=[DateTime]::UtcNow.ToString('o');lan='NOT RUN';casesExecuted=0
    humanBReview='PENDING';deltaGate='NOT DECIDED';machineAliases=@('MACHINE_A','MACHINE_B')
})
Write-Output 'RUN_INITIALIZED; all acceptance rows NOT RUN; reviewer must record actual evidence.'
