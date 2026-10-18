param([Parameter(Mandatory=$true)][string]$OutputDirectory,[string]$RepositoryRoot=(Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
if(Test-Path -LiteralPath $OutputDirectory) { throw 'OUTPUT_EXISTS_NO_OVERWRITE' }
$repo=(Resolve-Path -LiteralPath $RepositoryRoot).Path
& git -C $repo merge-base --is-ancestor $script:LanSourceSha HEAD
if($LASTEXITCODE -ne 0) { throw 'TASK1_SYNCED_SOURCE_REQUIRED' }
& git -C $repo diff --quiet $script:LanSourceSha -- client/src protocol/src server/src client/pom.xml server/pom.xml protocol/pom.xml pom.xml
if($LASTEXITCODE -ne 0) { throw 'RUNTIME_CHANGED_CORRECTIVE_REVIEW_REQUIRED' }
Test-LanFile $repo 'client/target/client-0.1.0-SNAPSHOT-all.jar' $script:LanClientHash
Test-LanFile $repo 'server/target/server-0.1.0-SNAPSHOT.jar' $script:LanServerHash
$files=@('client/target/client-0.1.0-SNAPSHOT-all.jar','server/target/server-0.1.0-SNAPSHOT.jar',
    'scripts/start-monitoring-server.ps1','scripts/start-monitoring-client.ps1')
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File -Filter '*.ps1' | ForEach-Object { 'scripts/lan-t3c4/'+$_.Name })
$files+=@(Get-ChildItem -LiteralPath (Join-Path $repo 'docs/templates/t3c4-lan') -File | ForEach-Object { 'docs/templates/t3c4-lan/'+$_.Name })
$files+=@('docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md')
$classes=@(Get-ChildItem -LiteralPath (Join-Path $repo 'server/target/test-classes/vn/edu/toeic/client') -Filter 'LiveCandidateApp*.class')
if($classes.Count -ne 4) { throw 'TASK1_TEST_SUPPORT_CLASSES_REQUIRED' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
foreach($rel in $files) {
    $target=Join-Path $OutputDirectory $rel
    New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $rel) -Destination $target
}
foreach($file in $classes) {
    $target=Join-Path $OutputDirectory ('test-support/vn/edu/toeic/client/'+$file.Name)
    New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
$base=(Resolve-Path -LiteralPath $OutputDirectory).Path
$entries=@(Get-ChildItem -LiteralPath $base -Recurse -File | Sort-Object FullName | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($base.Length+1).Replace('\','/');sha256=Get-LanHash $_.FullName}
})
Write-LanJson (Join-Path $base 'artifact-manifest.json') ([ordered]@{
    schema='t3c4-physical-bundle-v1';sourceSha=$script:LanSourceSha;runtimeSourceSha=$script:LanRuntimeSourceSha
    clientJarSha256=$script:LanClientHash;serverJarSha256=$script:LanServerHash
    createdUtc=[DateTime]::UtcNow.ToString('o');defaultMode='FULL';javaRequired=21
    testSupport='SIMULATED faults only; never use instrumented child for physical F proof'
    credentialsIncluded=$false;lanAcceptance='NOT RUN';humanBReview='PENDING';files=$entries
})
$null=Get-LanBundle $base
$zip=$base+'.zip'; if(Test-Path -LiteralPath $zip) { throw 'ZIP_EXISTS_NO_OVERWRITE' }
Compress-Archive -LiteralPath $base -DestinationPath $zip
Write-LanJson ($zip+'.sha256.json') ([ordered]@{sourceSha=$script:LanSourceSha;zipSha256=Get-LanHash $zip;manifestSha256=Get-LanHash (Join-Path $base 'artifact-manifest.json');lan='NOT RUN'})
Write-Output 'BUNDLE_READY_HASHES_VERIFIED; no server/client/network fault executed; LAN NOT RUN.'
