param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Z0-9-]{1,48}$')][string]$Checkpoint,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_.:-]{1,128}$')][string]$AttemptA,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_.:-]{1,128}$')][string]$AttemptB,
    [Management.Automation.PSCredential]$Credential
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$origin=Get-LanOrigin $ServerUrl
if(-not (Test-Path -LiteralPath (Join-Path $RunDirectory 'run.json'))) { throw 'INITIALIZE_RUN_FIRST' }
if($AttemptA -ceq $AttemptB) { throw 'TWO_DISTINCT_ATTEMPTS_REQUIRED' }
if(-not $Credential) { $Credential=Get-Credential -Message 'Assigned TEST proctor; credentials remain in memory' }
$session=Get-LanLogin $origin $Credential
if($session.user.role -cne 'PROCTOR') { throw 'ASSIGNED_PROCTOR_REQUIRED' }
$token=$session.token; $session=$null; $Credential=$null
try {
    $roster=Invoke-LanHttp $origin '/api/v1/monitoring/attempts' 'GET' $null $token
    $rows=@()
    foreach($item in @(@{alias='CANDIDATE_A';id=$AttemptA},@{alias='CANDIDATE_B';id=$AttemptB})) {
        $id=[Uri]::EscapeDataString($item.id); $base='/api/v1/monitoring/attempts/'+$id
        $state=Invoke-LanHttp $origin ($base+'/state') 'GET' $null $token
        $events=Invoke-LanHttp $origin ($base+'/events') 'GET' $null $token
        $gaps=Invoke-LanHttp $origin ($base+'/gaps') 'GET' $null $token
        $interruptions=Invoke-LanHttp $origin ($base+'/interruptions') 'GET' $null $token
        $exam=Invoke-LanHttp $origin ('/api/v1/attempts/status?attemptId='+$id) 'GET' $null $token
        $presence=@()
        if($roster.status -eq 200) { $presence=@($roster.data.attempts | Where-Object attemptId -CEQ $item.id) }
        $s=$state.data; $e=$exam.data
        $eventRows=@(); if($events.status -eq 200) {
            $eventRows=@($events.data.events | ForEach-Object {
                [ordered]@{eventId=$_.eventId;businessDigest=Get-LanCanonicalHash ([ordered]@{processName=$_.processName;metadataQuality=$_.metadataQuality;policyVersion=$_.policyVersion;observedAt=$_.observedAt});deliveryStatus=$_.deliveryStatus;receivedAt=$_.receivedAt}
            })
        }
        $gapRows=@(); if($gaps.status -eq 200) {
            $gapRows=@($gaps.data.gaps | ForEach-Object { [ordered]@{gapId=$_.gapId;collectorSessionId=$_.collectorSessionId;reason=$_.reason;droppedCount=$_.droppedCount;firstDroppedAt=$_.firstDroppedAt;lastDroppedAt=$_.lastDroppedAt;receivedAt=$_.receivedAt} })
        }
        $history=@(); if($interruptions.status -eq 200) {
            $history=@($interruptions.data.interruptions | ForEach-Object { [ordered]@{gapId=$_.gapId;reason=$_.reason;lastSeenAt=$_.lastSeenAt;timeoutDetectedAt=$_.timeoutDetectedAt;recoveredAt=$_.recoveredAt} })
        }
        $p=$null; if($presence.Count -eq 1) { $p=$presence[0] }
        $rows+=[ordered]@{
            candidate=$item.alias;http=[ordered]@{roster=$roster.status;state=$state.status;events=$events.status;gaps=$gaps.status;interruptions=$interruptions.status;exam=$exam.status}
            state=[ordered]@{serverInstanceId=Get-LanProperty $s 'serverInstanceId';syncEpoch=Get-LanProperty $s 'syncEpoch';collectorSessionId=Get-LanProperty $s 'collectorSessionId';revision=Get-LanProperty $s 'revision';sequence=Get-LanProperty $s 'sequence';status=Get-LanProperty $s 'status';receivedAt=Get-LanProperty $s 'receivedAt';processDigest=Get-LanCanonicalHash (Get-LanProperty $s 'processes')}
            presence=[ordered]@{status=Get-LanProperty $p 'status';reason=Get-LanProperty $p 'reason';revision=Get-LanProperty $p 'revision';lastSeenAt=Get-LanProperty $p 'lastSeenAt';timeoutDetectedAt=Get-LanProperty $p 'timeoutDetectedAt'}
            exam=[ordered]@{writerEpoch=Get-LanProperty $e 'writerEpoch';savedRevision=Get-LanProperty $e 'savedRevision';state=Get-LanProperty $e 'state';deadlineAt=Get-LanProperty $e 'deadlineAt';submittedAt=Get-LanProperty $e 'submittedAt';listeningInterrupted=Get-LanProperty $e 'listeningInterrupted';committedAnswersDigest=Get-LanCanonicalHash (Get-LanProperty $e 'answers')}
            events=$eventRows;gaps=$gapRows;interruptions=$history
        }
    }
    Write-LanJson (Join-Path $RunDirectory ('checkpoints/'+$Checkpoint+'.json')) ([ordered]@{
        schema='t3c4-checkpoint-v1';sourceSha=$script:LanSourceSha;machine='MACHINE_A'
        checkpoint=$Checkpoint;capturedUtc=[DateTime]::UtcNow.ToString('o')
        evidence='REAL_HTTP_ONLY; UI, physical link, client ACK and SQL durability need separate evidence'
        credentialsStored=$false;attemptNamesStored=$false;assertions='NOT EVALUATED';candidates=$rows
    })
    Write-Output 'CHECKPOINT_CAPTURED_NO_ACCEPTANCE_ASSERTED; HTTP status per endpoint must be reviewed.'
} finally { $token=$null }
