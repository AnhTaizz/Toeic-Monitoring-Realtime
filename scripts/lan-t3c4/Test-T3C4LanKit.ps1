param([Parameter(Mandatory=$true)][string]$OutputDirectory,[Parameter(Mandatory=$true)][string]$JavaHome)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
if(Test-Path -LiteralPath $OutputDirectory) { throw 'TEST_OUTPUT_EXISTS' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$checks=[Collections.Generic.List[object]]::new()
function Check([string]$Name,[bool]$Ok) {
    $checks.Add([ordered]@{name=$Name;result=if($Ok){'PASS'}else{'FAIL'}})
    if(-not $Ok) { throw ('KIT_CHECK_FAILED_'+$Name) }
}
foreach($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $tokens=$null; $errors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
    Check ('parse-'+$file.Name) ($errors.Count -eq 0)
}
foreach($bad in @('http://localhost:8080','http://127.0.0.1:8080','http://0.0.0.0:8080','http://169.254.1.2','http://224.0.0.1','http://user:secret@203.0.113.1','http://203.0.113.1/api','http://203.0.113.1/?token=secret')) {
    $rejected=$false; try { $null=Get-LanOrigin $bad } catch { $rejected=$true }
    Check 'reject-unsafe-origin' $rejected
}
Check 'accept-MOCK-documentation-origin-no-network' ((Get-LanOrigin 'http://203.0.113.1:8080').Port -eq 8080)
Check 'canonical-map-order' ((Get-LanCanonicalHash ([ordered]@{b=2;a=@(1,2)})) -ceq (Get-LanCanonicalHash ([ordered]@{a=@(1,2);b=2})))
Check 'canonical-distinct-answers' ((Get-LanCanonicalHash @{q='A'}) -cne (Get-LanCanonicalHash @{q='B'}))
Check 'canonical-null-vs-empty' ((Get-LanCanonicalHash $null) -cne (Get-LanCanonicalHash @()))
$object='{"list":[],"token":"DO_NOT_PUBLISH"}' | ConvertFrom-Json
Check 'empty-array-property-preserved' ((Get-LanCanonicalHash (Get-LanProperty $object 'list')) -ceq (Get-LanCanonicalHash @()))
Check 'missing-property-null' ($null -eq (Get-LanProperty $object 'absent'))
$file=Join-Path $OutputDirectory 'write-once.json'; Write-LanJson $file @{lan='NOT RUN'}
$hash=Get-LanHash $file; $rejected=$false; try { Write-LanJson $file @{lan='PASS'} } catch { $rejected=$true }
Check 'refuse-overwrite-preserve-evidence' ($rejected -and (Get-LanHash $file) -ceq $hash)
Test-LanFile $OutputDirectory 'write-once.json' $hash
$rejected=$false; try { Test-LanFile $OutputDirectory '../escape.json' $hash } catch { $rejected=$true }
Check 'reject-path-traversal' $rejected
$rejected=$false; try { Test-LanFile $OutputDirectory 'write-once.json' ('0'*64) } catch { $rejected=$true }
Check 'reject-hash-tamper' $rejected
$null=Get-LanJava $JavaHome; Check 'native-JDK21-check-no-stderr-failure' $true
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$cases=@(Import-Csv -LiteralPath (Join-Path $repo 'docs/templates/t3c4-lan/cases.csv'))
Check 'checklist-initially-NOT-RUN' ($cases.Count -ge 39 -and @($cases | Where-Object { $_.'PASS/FAIL' -ne 'NOT RUN' -or $_.Reviewer -ne 'PENDING' }).Count -eq 0)
Check 'all-F-cases-present' (@($cases | Where-Object Case -Match '^F\d\d$').Count -eq 12)
# MOCK byte files exercise the exported pins; these are never executable JAR evidence.
$mock=Join-Path $OutputDirectory 'mock-bundle'
New-Item -ItemType Directory -Path (Join-Path $mock 'client/target'),(Join-Path $mock 'server/target') | Out-Null
[IO.File]::WriteAllText((Join-Path $mock 'client/target/client-0.1.0-SNAPSHOT-all.jar'),'MOCK CLIENT')
[IO.File]::WriteAllText((Join-Path $mock 'server/target/server-0.1.0-SNAPSHOT.jar'),'MOCK SERVER')
$common=Join-Path $mock 'Common.ps1'; Copy-Item (Join-Path $PSScriptRoot 'Common.ps1') $common
$originalSha=$script:LanSourceSha
$script:LanSourceSha='a'*40; $script:LanRuntimeSourceSha='b'*40
$script:LanClientHash=Get-LanHash (Join-Path $mock 'client/target/client-0.1.0-SNAPSHOT-all.jar')
$script:LanServerHash=Get-LanHash (Join-Path $mock 'server/target/server-0.1.0-SNAPSHOT.jar')
Write-LanPinnedCommon $common
Write-LanJson (Join-Path $mock 'artifact-manifest.json') @{
    schema='t3c4-physical-bundle-v1';sourceSha=$script:LanSourceSha;runtimeSourceSha=$script:LanRuntimeSourceSha
    clientJarSha256=$script:LanClientHash;serverJarSha256=$script:LanServerHash
    files=@(@{path='Common.ps1';sha256=Get-LanHash $common})
}
. (Join-Path $PSScriptRoot 'Common.ps1')
Check 'source-default-pins-unchanged' ($script:LanSourceSha -ceq $originalSha)
$rejected=$false; try { $null=Get-LanBundle $mock } catch { $rejected=$true }
Check 'legacy-rejects-unrelated-MOCK-bundle' $rejected
. $common
Check 'export-pins-accept-own-MOCK-manifest' ((Get-LanBundle $mock).sourceSha -ceq ('a'*40))
[IO.File]::AppendAllText((Join-Path $mock 'client/target/client-0.1.0-SNAPSHOT-all.jar'),'TAMPER')
$rejected=$false; try { $null=Get-LanBundle $mock } catch { $rejected=$true }
Check 'export-rejects-MOCK-artifact-tamper' $rejected
. (Join-Path $PSScriptRoot 'Common.ps1')
Write-LanJson (Join-Path $OutputDirectory 'validation.json') ([ordered]@{
    sourceSha=$script:LanSourceSha;type='KIT VALIDATION ONLY';fixture='MOCK origins/maps/files; no network';checks=$checks.ToArray()
    lan='NOT RUN';physicalCasesExecuted=0;humanBReview='PENDING';protocolProbeRuntime='NOT RUN'
})
Write-Output ('KIT_VALIDATION_PASS '+$checks.Count+'; no network or LAN acceptance executed.')
