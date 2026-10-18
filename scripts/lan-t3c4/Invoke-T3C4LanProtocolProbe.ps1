param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_.:-]{1,128}$')][string]$Attempt,
    [Parameter(Mandatory=$true)][switch]$DedicatedTestAttempt,
    [Management.Automation.PSCredential]$CandidateCredential,
    [Management.Automation.PSCredential]$ProctorCredential
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
if(-not $DedicatedTestAttempt) { throw 'DEDICATED_TEST_ATTEMPT_NO_OTHER_COLLECTOR_REQUIRED' }
if(-not (Test-Path -LiteralPath (Join-Path $RunDirectory 'run.json'))) { throw 'INITIALIZE_RUN_FIRST' }
$origin=Get-LanOrigin $ServerUrl
if(-not $CandidateCredential) { $CandidateCredential=Get-Credential -Message 'Dedicated TEST candidate (close its other collectors first)' }
if(-not $ProctorCredential) { $ProctorCredential=Get-Credential -Message 'Assigned TEST proctor; read-only oracle' }
$c=Get-LanLogin $origin $CandidateCredential; $p=Get-LanLogin $origin $ProctorCredential
if($c.user.role -cne 'CANDIDATE' -or $p.user.role -cne 'PROCTOR') { throw 'TEST_ROLES_REQUIRED' }
$token=$c.token; $readToken=$p.token; $c=$null; $p=$null; $CandidateCredential=$null; $ProctorCredential=$null
$rows=[Collections.Generic.List[object]]::new(); $ws=$null
function Connect-Probe {
    $socket=[Net.WebSockets.ClientWebSocket]::new()
    $socket.Options.SetRequestHeader('Authorization','Bearer '+$token)
    $builder=[UriBuilder]::new($origin); $builder.Scheme=if($origin.Scheme -eq 'https'){'wss'}else{'ws'}; $builder.Path='/ws/v1/realtime'
    $cancel=[Threading.CancellationTokenSource]::new(10000)
    try { $socket.ConnectAsync($builder.Uri,$cancel.Token).GetAwaiter().GetResult() | Out-Null }
    catch { $socket.Dispose(); throw 'WS_CONNECT_FAILED_NO_URL_LOGGED' }
    finally { $cancel.Dispose() }
    return $socket
}
function Frame([string]$Type,$Payload) {
    $id=[Guid]::NewGuid().ToString()
    return [ordered]@{protocolVersion='v0';type=$Type;messageId=$id;requestId=$id;attemptId=$Attempt;traceId=[Guid]::NewGuid().ToString();payload=$Payload}
}
function Exchange($Message) {
    $bytes=[Text.Encoding]::UTF8.GetBytes(($Message | ConvertTo-Json -Depth 20 -Compress))
    $cancel=[Threading.CancellationTokenSource]::new(10000)
    try {
        $segment=[ArraySegment[byte]]::new($bytes)
        $ws.SendAsync($segment,[Net.WebSockets.WebSocketMessageType]::Text,$true,$cancel.Token).GetAwaiter().GetResult() | Out-Null
        while($true) {
            $buffer=New-Object byte[] 16384; $memory=[IO.MemoryStream]::new()
            try {
                do {
                    $part=$ws.ReceiveAsync([ArraySegment[byte]]::new($buffer),$cancel.Token).GetAwaiter().GetResult()
                    if($part.MessageType -ne [Net.WebSockets.WebSocketMessageType]::Text) { throw 'WS_TEXT_REQUIRED' }
                    $memory.Write($buffer,0,$part.Count)
                    if($memory.Length -gt 1048576) { throw 'WS_FRAME_LIMIT' }
                } while(-not $part.EndOfMessage)
                $reply=[Text.Encoding]::UTF8.GetString($memory.ToArray()) | ConvertFrom-Json
            } finally { $memory.Dispose() }
            if((Get-LanProperty $reply 'requestId') -ceq $Message.requestId) { return $reply }
        }
    } catch { throw 'WS_EXCHANGE_FAILED_NO_PAYLOAD_LOGGED' } finally { $cancel.Dispose() }
}
function Assert-Probe([string]$Case,[bool]$Condition) {
    $rows.Add([ordered]@{case=$Case;result=if($Condition){'PASS'}else{'FAIL'};evidence='REAL transport; MOCK observations / SIMULATED replay';sourceSha=$script:LanSourceSha})
    if(-not $Condition) { throw ('PROBE_ASSERTION_'+$Case) }
}
function State {
    $reply=Invoke-LanHttp $origin ('/api/v1/monitoring/attempts/'+[Uri]::EscapeDataString($Attempt)+'/state') 'GET' $null $readToken
    if($reply.status -ne 200) { throw 'ORACLE_STATE_NOT_200' }; return $reply.data
}
function StateDigest($s) { return Get-LanCanonicalHash @($s.serverInstanceId,$s.syncEpoch,$s.sequence,$s.revision,$s.receivedAt,$s.processes) }
function Full([long]$Sequence,$Processes=@()) { return @{collectorSessionId=$collector;syncEpoch=$epoch;sequence=$Sequence;policyVersion='process-policy-v1';processes=@($Processes)} }
function Delta([long]$Base,$Added=@(),$Removed=@()) { return @{collectorSessionId=$collector;syncEpoch=$epoch;baseSequence=$Base;sequence=$Base+1;policyVersion='process-policy-v1';added=@($Added);removed=@($Removed)} }
function Accepted($r) { return ($r.type -ceq 'ACK' -and $r.payload.status -ceq 'ACCEPTED') }
function Rejected($r,[string]$Code) { return ($r.type -ceq 'ERROR' -and $r.payload.code -ceq $Code) }
$collector='TEST-probe-'+[Guid]::NewGuid().ToString(); $epoch=$null; $failure=$false
try {
    $ws=Connect-Probe
    $open=Exchange (Frame 'MONITORING_OPEN' @{collectorSessionId=$collector})
    Assert-Probe 'OPEN' (Accepted $open); $epoch=$open.payload.syncEpoch
    $edge=@{pid=900001;processName='msedge.exe';startInstant='2026-10-18T00:00:00Z';metadataQuality='COMPLETE'}
    Assert-Probe 'FULL1' (Accepted (Exchange (Frame 'MONITORING_FULL' (Full 1 @($edge)))))
    $d2=Frame 'MONITORING_DELTA' (Delta 1 @() @(@{pid=900001;startInstant=$edge.startInstant}))
    Assert-Probe 'DELTA2' (Accepted (Exchange $d2)); $before=State
    Assert-Probe 'MT02-STATE-DUPLICATE' ((Accepted (Exchange $d2)) -and (StateDigest (State)) -ceq (StateDigest $before))
    $conflict=$d2 | ConvertTo-Json -Depth 20 | ConvertFrom-Json; $conflict.payload.removed=@(); $conflict.payload.added=@($edge)
    Assert-Probe 'MT02-ID-CONFLICT' (Rejected (Exchange $conflict) 'CONFLICT')
    $resync=Exchange (Frame 'MONITORING_DELTA' (Delta 3))
    $unsynced=State
    Assert-Probe 'MT03-MISSING-BASE' ($resync.type -ceq 'MONITORING_RESYNC' -and $resync.payload.reason -ceq 'BASE_MISSING' -and $unsynced.status -ceq 'UNSYNCED' -and $unsynced.sequence -eq 2 -and (Get-LanCanonicalHash $unsynced.processes) -ceq (Get-LanCanonicalHash $before.processes))
    Assert-Probe 'FULL-RESYNC' (Accepted (Exchange (Frame 'MONITORING_FULL' (Full 20 @($edge)))))
    $oldDelta=Frame 'MONITORING_DELTA' (Delta 10)
    $oldEpoch=$epoch
    $ws.Dispose(); $ws=Connect-Probe
    $collector='TEST-probe-'+[Guid]::NewGuid().ToString()
    $open=Exchange (Frame 'MONITORING_OPEN' @{collectorSessionId=$collector}); Assert-Probe 'REOPEN' (Accepted $open)
    $epoch=$open.payload.syncEpoch
    Assert-Probe 'FRESH-FULL1' (Accepted (Exchange (Frame 'MONITORING_FULL' (Full 1))))
    Assert-Probe 'FRESH-FULL20' (($epoch -cne $oldEpoch) -and (Accepted (Exchange (Frame 'MONITORING_FULL' (Full 20 @($edge))))))
    $before=State
    Assert-Probe 'MT04-OLD-EPOCH-DELTA' ((Rejected (Exchange $oldDelta) 'STALE') -and (StateDigest (State)) -ceq (StateDigest $before))
    Assert-Probe 'MT05-OLD-COLLECTOR' ((Rejected (Exchange $d2) 'STALE') -and (StateDigest (State)) -ceq (StateDigest $before))
    $low=Exchange (Frame 'MONITORING_FULL' (Full 19))
    Assert-Probe 'MT06-LOW-SEQUENCE' ((Rejected $low 'STALE') -and (StateDigest (State)) -ceq (StateDigest $before))
    Assert-Probe 'MT06-SAME-SEQUENCE-CONFLICT' ((Rejected (Exchange (Frame 'MONITORING_FULL' (Full 20))) 'CONFLICT') -and (StateDigest (State)) -ceq (StateDigest $before))
} catch { $failure=$true; Write-Output 'PROBE_FAILED; inspect redacted result; no raw response or credential logged.' }
finally {
    if($null -ne $ws) {
        if($ws.State -eq [Net.WebSockets.WebSocketState]::Open -and $epoch) {
            try { $null=Exchange (Frame 'MONITORING_CLOSE' @{collectorSessionId=$collector;syncEpoch=$epoch}) } catch { }
        }
        $ws.Dispose()
    }
    $token=$null; $readToken=$null
    Write-LanJson (Join-Path $RunDirectory ('SIMULATED-protocol-'+[Guid]::NewGuid().ToString()+'.json')) ([ordered]@{
        sourceSha=$script:LanSourceSha;mockObservations=$true;simulatedFaults=$true;physicalLinkLoss=$false
        result=if($failure){'FAIL'}else{'PASS SUBSET ONLY'};lan='NOT RUN';rows=@($rows.ToArray())
        notCovered=@('MT02 event commit/rollback','MT05 server restart','MT06 three-trace full/delta event oracle','physical F','GUI parser/callback')
    })
}
if($failure) { throw 'PROTOCOL_PROBE_FAILED' }
Write-Output 'PROBE_SUBSET_COMPLETE; not physical LAN acceptance; remaining MT evidence required.'
