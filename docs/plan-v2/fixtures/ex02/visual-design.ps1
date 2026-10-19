# Self-authored MOCK design helpers only. No backend/JavaFX implementation.
function VisualHash($manifest) {
    $stream=New-Object IO.MemoryStream
    try {
        function WriteVisualUtf([string]$value) {
            # Fixture identifiers/MIME are ASCII: Java modified UTF-8 equals UTF-8 here.
            if ($value -match '[^\x00-\x7f]') {throw 'Fixture manifest hash expects ASCII identifiers'}
            $bytes=[Text.Encoding]::UTF8.GetBytes($value)
            $stream.WriteByte([byte](($bytes.Length -shr 8) -band 255));$stream.WriteByte([byte]($bytes.Length -band 255))
            $stream.Write($bytes,0,$bytes.Length)
        }
        function WriteVisualInt([int]$value) {
            $bytes=[BitConverter]::GetBytes($value);if([BitConverter]::IsLittleEndian){[array]::Reverse($bytes)};$stream.Write($bytes,0,4)
        }
        function WriteVisualLong([long]$value) {
            $bytes=[BitConverter]::GetBytes($value);if([BitConverter]::IsLittleEndian){[array]::Reverse($bytes)};$stream.Write($bytes,0,8)
        }
        WriteVisualUtf 'EX02-P1-V1';WriteVisualUtf $manifest.examId;WriteVisualUtf $manifest.sessionId;WriteVisualUtf $manifest.attemptId
        WriteVisualInt $manifest.contentVersion;WriteVisualLong $manifest.writerEpoch;WriteVisualUtf $manifest.preparationEpoch
        WriteVisualInt $manifest.slots.Count
        foreach($slot in $manifest.slots) {
            WriteVisualUtf $slot.slotId;WriteVisualInt $slot.position;WriteVisualUtf $slot.mediaId;WriteVisualLong $slot.sizeBytes
            WriteVisualUtf $slot.sha256;WriteVisualUtf $slot.mimeType;WriteVisualInt $slot.widthPx;WriteVisualInt $slot.heightPx
        }
        $sha=[Security.Cryptography.SHA256]::Create()
        try {return ([BitConverter]::ToString($sha.ComputeHash($stream.ToArray()))).Replace('-','').ToLowerInvariant()} finally {$sha.Dispose()}
    } finally {$stream.Dispose()}
}
function NewVisualManifest($exam,[string]$attempt,[string]$session,[long]$writer,[string]$epoch) {
    $assets=@{};foreach($asset in $exam.media){$assets[$asset.mediaId]=$asset}
    $slots=@();$position=0
    foreach($question in @($exam.questions|Where-Object {$_.part -eq 1}|Sort-Object {$_.orderIndex})) {
        $position++;$blocks=@($question.blocks|Where-Object {$_.type -eq 'IMAGE'})
        if($blocks.Count -ne 1){throw 'Part1 fixture must have exactly one image per question'}
        $asset=$assets[$blocks[0].mediaId]
        if($null -eq $asset -or $asset.mediaType -ne 'IMAGE' -or $asset.storageStatus -ne 'LOCAL_VERIFIED'){throw 'Unverified Part1 fixture asset'}
        $slots += [ordered]@{slotId="p1-$position";position=$position;mediaId=$asset.mediaId;downloadPath='';sha256=$asset.sha256;
            sizeBytes=$asset.sizeBytes;mimeType=$asset.mimeType;widthPx=$asset.widthPx;heightPx=$asset.heightPx}
    }
    $manifest=[ordered]@{schemaVersion=2;manifestId='';attemptId=$attempt;sessionId=$session;examId=$exam.exam.examId;
        contentVersion=2;writerEpoch=$writer;preparationEpoch=$epoch;slots=$slots}
    $manifest.manifestId=VisualHash $manifest
    foreach($slot in $slots){$slot.downloadPath="/api/v2/attempts/$attempt/images/$($slot.mediaId)?visualManifestId=$($manifest.manifestId)"}
    return $manifest
}
function VisualVerifiedAssets($manifest) {
    $seen=@{};$result=@()
    foreach($slot in $manifest.slots){if(-not $seen.ContainsKey($slot.mediaId)){
        $seen[$slot.mediaId]=$true;$result += [ordered]@{mediaId=$slot.mediaId;sha256=$slot.sha256;sizeBytes=$slot.sizeBytes;widthPx=$slot.widthPx;heightPx=$slot.heightPx}
    }}
    return $result
}
function WriteVisualFixtures($exam) {
    $manifest=NewVisualManifest $exam 'mock-attempt' 'mock-session' 2 '11111111-1111-4111-8111-111111111111'
    $peer=NewVisualManifest $exam 'mock-attempt-2' 'mock-session' 2 '22222222-2222-4222-8222-222222222222'
    WriteJson 'visual-manifest.mock.json' $manifest
    WriteJson 'visual-ready-request.mock.json' ([ordered]@{requestId='mock-visual-ready';attemptId=$manifest.attemptId;writerEpoch=2;
        preparationEpoch=$manifest.preparationEpoch;manifestId=$manifest.manifestId;action='ATTEST';firstSlotId='p1-1';verifiedAssets=@(VisualVerifiedAssets $manifest)})
    WriteJson 'visual-ready-response.mock.json' ([ordered]@{schemaVersion=2;requestId='mock-visual-ready';attemptId=$manifest.attemptId;writerEpoch=2;
        preparationEpoch=$manifest.preparationEpoch;manifestId=$manifest.manifestId;state='READY';readyAt='2026-10-19T00:59:59Z';leaseExpiresAt='2026-10-19T01:00:05Z'})
    $rows=@();foreach($m in @($manifest,$peer)){$rows += [ordered]@{attemptId=$m.attemptId;writerEpoch=2;preparationEpoch=$m.preparationEpoch;
        audioReady=$true;visualReady=$true;visualManifestId=$m.manifestId;visualLeaseExpiresAt='2026-10-19T01:00:05Z';blockedReasons=@()}}
    WriteJson 'visual-readiness.mock.json' ([ordered]@{schemaVersion=2;sessionId='mock-session';readAt='2026-10-19T00:59:59Z';allReady=$true;attempts=$rows})
    return $manifest
}
function VisualPrefetchSafe($value) {
    if($null -eq $value -or $value -is [string] -or $value.GetType().IsPrimitive){return}
    if($value -is [Collections.IEnumerable] -and $value -isnot [pscustomobject]){foreach($v in $value){VisualPrefetchSafe $v};return}
    foreach($property in $value.PSObject.Properties){
        Require ($property.Name -notmatch '(?i)question|prompt|option|answer|scoring|passage|source|localFile|altText|credential|token') 'PREFETCH_LEAK'
        VisualPrefetchSafe $property.Value
    }
}
function VisualAccess($exam,$manifest,$role,$owner,$revoked,$state,$operation,$mediaId,$manifestId,$verified=$true) {
    if($revoked){return 401};if($role -ne 'CANDIDATE' -or -not $owner){return 403}
    if($state -eq 'CLOSED'){return 409}
    if($operation -eq 'EXAM_V1'){return 409}
    if($operation -in @('EXAM','OPTIONS','ANSWERS') -and $state -eq 'SCHEDULED'){return 409}
    if($operation -eq 'IMAGE') {
        $asset=@($exam.media|Where-Object {$_.mediaId -eq $mediaId})
        if($asset.Count -ne 1 -or -not $verified -or $asset[0].mediaType -ne 'IMAGE' -or $asset[0].storageStatus -ne 'LOCAL_VERIFIED'){return 403}
        if($state -eq 'SCHEDULED') {
            if($mediaId -notin @($manifest.slots.mediaId)){return 403}
            if([string]::IsNullOrWhiteSpace($manifestId)){return 400}
            if($manifestId -ne $manifest.manifestId){return 409}
        }
    }
    return 200
}
function NewVisualModel($manifest) {
    [pscustomobject]@{session='SCHEDULED';writer=2;epoch=$manifest.preparationEpoch;manifest=$manifest.manifestId;
        audio=$true;visual=$true;peerAudio=$true;peerVisual=$true;now=1000L;lease=6000L;
        startedAt=$null;deadline=$null;startId=$null;authorizations=0;answers='{}';revision=0;
        revoked=$false;owner=$true;assigned=$true;validFiles=$true;connected=$true;receipts=@{};
        sceneShowing=$false;decoded=$false;layout=$false;visiblePulses=0;mediaOnReady=$false;dto=$false;
        elapsed=0;plays=0;interrupted=$false;trace=(New-Object 'Collections.Generic.List[string]')}
}
function StartVisualModel($model) {
    if($model.revoked){return 401};if(-not $model.assigned){return 403};if($model.session -eq 'CLOSED'){return 409}
    if($model.session -eq 'IN_PROGRESS'){return 200}
    if(-not ($model.audio -and $model.visual -and $model.peerAudio -and $model.peerVisual -and $model.validFiles -and $model.connected) -or $model.now -ge $model.lease){return 409}
    $model.session='IN_PROGRESS';$model.startedAt=$model.now;$model.deadline=$model.now+7200000;$model.startId='MOCK-one-start';$model.authorizations++
    return 200
}
function AttestVisualModel($model,$request,$manifest) {
    if($model.revoked){return '401 UNAUTHORIZED'};if(-not $model.owner){return '403 FORBIDDEN'}
    if($model.writer -ne $request.writerEpoch){return '409 STALE'}
    if($model.session -ne 'SCHEDULED'){return '409 INVALID_STATE'}
    if($model.epoch -ne $request.preparationEpoch -or $model.manifest -ne $request.manifestId){return '409 CONFLICT'}
    if(-not $model.audio -or -not $model.validFiles -or ($model.visual -and $model.now -ge $model.lease)){return '409 INVALID_STATE'}
    $canonical=([ordered]@{action=$request.action;firstSlotId=$request.firstSlotId;assets=@($request.verifiedAssets|Sort-Object {$_.mediaId})}|ConvertTo-Json -Depth 10 -Compress)
    if($model.receipts.ContainsKey($request.requestId)){
        if($model.receipts[$request.requestId] -ne $canonical){return '409 CONFLICT'};return '200 READY'
    }
    $expected=@(VisualVerifiedAssets $manifest)
    $actual=@($request.verifiedAssets)
    if($request.firstSlotId -ne 'p1-1' -or $actual.Count -ne $expected.Count -or @($actual.mediaId|Sort-Object -Unique).Count -ne $actual.Count){return '400 INVALID_INPUT'}
    foreach($entry in $actual){$e=@($expected|Where-Object {$_.mediaId -eq $entry.mediaId});if($e.Count -ne 1 -or $entry.sha256 -ne $e[0].sha256 -or $entry.sizeBytes -ne $e[0].sizeBytes -or $entry.widthPx -ne $e[0].widthPx -or $entry.heightPx -ne $e[0].heightPx){return '400 INVALID_INPUT'}}
    $model.visual=$true;$model.lease=$model.now+6000;$model.receipts[$request.requestId]=$canonical
    return '200 READY'
}
function InvalidateVisualModel($model) {
    $model.epoch='33333333-3333-4333-8333-333333333333';$model.audio=$false;$model.visual=$false;$model.lease=0
}
function TryVisualPlay($model) {
    if($model.session -ne 'IN_PROGRESS' -or $model.interrupted -or $model.plays -gt 0){return}
    if($model.elapsed -ge 3000 -or -not $model.connected){$model.interrupted=$true;return}
    if($model.dto -and $model.sceneShowing -and $model.decoded -and $model.layout -and $model.visiblePulses -ge 2 -and $model.mediaOnReady){
        $model.trace.Add('PLAY_CALLED');$model.plays++
    }
}
function InvokeVisualChecks($exam,$candidate,$render) {
    $manifest=ReadJson 'visual-manifest.mock.json';$request=ReadJson 'visual-ready-request.mock.json'
    $prep=ReadJson 'preparation.mock.json';$ack=ReadJson 'visual-ready-response.mock.json';$roster=ReadJson 'visual-readiness.mock.json'
    Require ($manifest.manifestId -eq (VisualHash $manifest)) 'VISUAL_HASH'
    Require ((NewVisualManifest $exam $manifest.attemptId $manifest.sessionId 3 $manifest.preparationEpoch).manifestId -ne $manifest.manifestId) 'VISUAL_WRITER_HASH'
    Require ((NewVisualManifest $exam $manifest.attemptId $manifest.sessionId 2 '33333333-3333-4333-8333-333333333333').manifestId -ne $manifest.manifestId) 'VISUAL_EPOCH_HASH'
    $derived=NewVisualManifest $exam $manifest.attemptId $manifest.sessionId $manifest.writerEpoch $manifest.preparationEpoch
    Require (($derived|ConvertTo-Json -Depth 40 -Compress) -eq ($manifest|ConvertTo-Json -Depth 40 -Compress)) 'VISUAL_GRAPH_ALLOWLIST'
    Require ($manifest.slots.Count -eq 6 -and @($manifest.slots.mediaId|Sort-Object -Unique).Count -eq 1) 'VISUAL_REUSE'
    RequireSequence @($manifest.slots.position) 'VISUAL_POSITION'
    Require (($prep.visualManifest|ConvertTo-Json -Depth 40 -Compress) -eq ($manifest|ConvertTo-Json -Depth 40 -Compress)) 'PREPARATION_MANIFEST'
    Require ($candidate.visualManifestId -eq $manifest.manifestId -and $request.manifestId -eq $manifest.manifestId -and $ack.manifestId -eq $manifest.manifestId) 'VISUAL_BINDING'
    Require ($request.verifiedAssets.Count -eq 1 -and $roster.attempts.Count -eq 2 -and $roster.allReady) 'VISUAL_ROSTER'
    foreach($row in $roster.attempts){
        $expected=NewVisualManifest $exam $row.attemptId $roster.sessionId $row.writerEpoch $row.preparationEpoch
        Require ($row.audioReady -and $row.visualReady -and $row.visualManifestId -eq $expected.manifestId -and [DateTimeOffset]::Parse($row.visualLeaseExpiresAt) -gt [DateTimeOffset]::Parse($roster.readAt)) 'VISUAL_ROSTER_DERIVED'
    }
    VisualPrefetchSafe $prep;VisualPrefetchSafe $manifest
    # Real local size/hash/PNG decode; .NET bitmap is not a JavaFX GUI rendering test.
    Add-Type -AssemblyName System.Drawing
    foreach($slot in $manifest.slots){
        $asset=@($exam.media|Where-Object {$_.mediaId -eq $slot.mediaId})[0]
        $path=Join-Path $fixtureRoot "assets/images/$($exam.exam.examId)/$($asset.localFile)"
        Require ((HashFile $path) -eq $slot.sha256 -and (Get-Item $path).Length -eq $slot.sizeBytes) 'VISUAL_FILE'
        $bitmap=[Drawing.Image]::FromFile($path)
        try{Require ($bitmap.Width -eq $slot.widthPx -and $bitmap.Height -eq $slot.heightPx) 'VISUAL_DIMENSIONS'}finally{$bitmap.Dispose()}
    }
    $cases=ReadJson 'visual-cases.mock.json';$results=@()
    foreach($case in $cases.cases){
        $m=NewVisualModel $manifest;$r=($request|ConvertTo-Json -Depth 30|ConvertFrom-Json)
        switch($case.id){
            'P01' {Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'PREPARATION' '' '') -eq 200) 'P01';VisualPrefetchSafe $prep}
            'P02' {Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'IMAGE' 'img-grid' $manifest.manifestId) -eq 200) 'P02'}
            'P03' {Require ($manifest.slots[0].mimeType -eq 'image/png' -and $request.verifiedAssets.Count -eq 1) 'P03'}
            'P04' {$m.visual=$false;Require ((AttestVisualModel $m $r $manifest) -eq '200 READY') 'P04a';$lease=$m.lease;$m.now+=1000;
                Require ((AttestVisualModel $m $r $manifest) -eq '200 READY' -and $m.lease -eq $lease) 'P04-idempotent-no-renew';
                $r.requestId='mock-visual-renew';Require ((AttestVisualModel $m $r $manifest) -eq '200 READY' -and $m.lease -gt $lease) 'P04-renew';
                $m.now=$m.lease;Require ((AttestVisualModel $m $r $manifest) -eq '409 INVALID_STATE') 'P04-expired-no-resurrect'}
            'P05' {$m.visual=$false;Require ((StartVisualModel $m) -eq 409) 'P05a';$null=AttestVisualModel $m $r $manifest;Require ((StartVisualModel $m) -eq 200) 'P05b'}
            'P06' {Require ((StartVisualModel $m) -eq 200 -and $m.authorizations -eq 1) 'P06'}
            'P07' {Require ($null -eq $m.deadline -and $null -eq $m.startedAt) 'P07a';$null=StartVisualModel $m;Require ($m.startedAt -eq 1000 -and $m.deadline -eq 7201000) 'P07b'}
            'P08' {$null=StartVisualModel $m;$m.dto=$true;$m.sceneShowing=$true;$m.decoded=$true;$m.layout=$true;$m.mediaOnReady=$true;
                foreach($event in @('IMAGE_ATTACHED','LAYOUT_DONE','VISIBLE_PULSE_1','VISIBLE_PULSE_2')){$m.trace.Add($event);if($event -like 'VISIBLE_PULSE*'){$m.visiblePulses++};TryVisualPlay $m}
                Require (($m.trace -join ',') -eq ($render.visualPlaybackOrder -join ',')) 'P08'}
            'P09' {$repo=[IO.Path]::GetFullPath((Join-Path $fixtureRoot '../../../..'));$legacy=Get-Content -Raw -Encoding UTF8 (Join-Path $repo 'docs/QUYET_DINH.md');
                $section=[regex]::Match($legacy.Replace("`r`n","`n"),'(?ms)^## QD-21.*?(?=^## |\z)').Value;$sha=[Security.Cryptography.SHA256]::Create();
                try{$hash=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($section)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()};
                Require ($hash -eq 'bcbe781461261464c11d27bed1e52108ea3c5dc5f3e2ffa54e454053f6177ad0') 'P09-policy-unchanged'}
            'N01' {Require ((VisualAccess $exam $manifest 'CANDIDATE' $false $false 'SCHEDULED' 'IMAGE' 'img-grid' $manifest.manifestId) -eq 403) 'N01'}
            'N02' {Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $true 'SCHEDULED' 'IMAGE' 'img-grid' $manifest.manifestId) -eq 401) 'N02'}
            'N03' {foreach($op in @('EXAM','OPTIONS','ANSWERS')){Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' $op '' '') -eq 409) 'N03'}}
            'N04' {Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'IMAGE' 'img-p7-only' $manifest.manifestId) -eq 403) 'N04'}
            'N05' {Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'IMAGE' 'img-p4-only' $manifest.manifestId) -eq 403) 'N05'}
            'N06' {foreach($id in @('foreign-image','aud-001')){Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'IMAGE' $id $manifest.manifestId) -eq 403) 'N06'};
                Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'IMAGE' 'img-grid' $manifest.manifestId $false) -eq 403) 'N06-unverified'}
            'N07' {$m.validFiles=$false;Require ((AttestVisualModel $m $r $manifest) -eq '409 INVALID_STATE' -and (StartVisualModel $m) -eq 409) 'N07'}
            'N08' {$m.decoded=$false;$m.visual=$false;Require ((StartVisualModel $m) -eq 409) 'N08a';$bad=Join-Path $fixtureRoot "assets/images/$($exam.exam.examId)/corrupt.png";$decoded=$false;try{$b=[Drawing.Image]::FromFile($bad);$b.Dispose();$decoded=$true}catch{};Require (-not $decoded) 'N08b'}
            'N09' {$r.writerEpoch=1;Require ((AttestVisualModel $m $r $manifest) -eq '409 STALE') 'N09'}
            'N10' {$r.manifestId=('0'*64);Require ((AttestVisualModel $m $r $manifest) -eq '409 CONFLICT' -and (VisualAccess $exam $manifest 'CANDIDATE' $true $false 'SCHEDULED' 'IMAGE' 'img-grid' $r.manifestId) -eq 409) 'N10'}
            'N11' {$m.peerVisual=$false;Require ((StartVisualModel $m) -eq 409 -and $null -eq $m.startedAt -and $null -eq $m.deadline -and $null -eq $m.startId -and $m.authorizations -eq 0 -and $m.revision -eq 0 -and $m.writer -eq 2) 'N11'}
            'N12' {$m.writer++;InvalidateVisualModel $m;Require ((StartVisualModel $m) -eq 409 -and (AttestVisualModel $m $r $manifest) -eq '409 STALE') 'N12'}
            'N13' {$null=StartVisualModel $m;$deadline=$m.deadline;$id=$m.startId;$m.visual=$false;Require ((StartVisualModel $m) -eq 200 -and $m.deadline -eq $deadline -and $m.startId -eq $id -and $m.authorizations -eq 1) 'N13'}
            'N14' {$null=StartVisualModel $m;$deadline=$m.deadline;$m.interrupted=$true;TryVisualPlay $m;Require ($m.plays -eq 0 -and $m.deadline -eq $deadline -and $m.authorizations -eq 1) 'N14'}
            'N15' {foreach($header in @('If-None-Match','If-Modified-Since')){Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $true 'SCHEDULED' 'IMAGE' 'img-grid' $manifest.manifestId) -eq 401) "N15-$header"}}
            'N16' {$m.revoked=$true;Require ((AttestVisualModel $m $r $manifest) -eq '401 UNAUTHORIZED') 'N16'}
            'N17' {foreach($state in @('SCHEDULED','IN_PROGRESS')){Require ((VisualAccess $exam $manifest 'CANDIDATE' $true $false $state 'EXAM_V1' '' '') -eq 409) 'N17'} }
            'N18' {$null=StartVisualModel $m;$m.dto=$true;$m.decoded=$true;$m.mediaOnReady=$true;TryVisualPlay $m;Require ($m.plays -eq 0) 'N18'}
            'N19' {$null=StartVisualModel $m;$m.elapsed=3000;$deadline=$m.deadline;TryVisualPlay $m;Require ($m.plays -eq 0 -and $m.interrupted -and $m.deadline -eq $deadline) 'N19'}
            'N20' {InvalidateVisualModel $m;Require ((AttestVisualModel $m $r $manifest) -eq '409 CONFLICT' -and (StartVisualModel $m) -eq 409) 'N20'}
            default{throw "Unknown visual design case $($case.id)"}
        }
        $results += [ordered]@{id=$case.id;description=$case.description;status='PASS';scope='MOCK_DESIGN_ONLY';runtime='NOT RUN'}
    }
    Require ($results.Count -eq 29 -and @($results|Where-Object {$_.id -like 'P*'}).Count -eq 9) 'VISUAL_CASE_COVERAGE'
    WriteJson 'visual-verification.mock.json' ([ordered]@{label='MOCK';scope='Design models plus real local PNG size/hash/.NET decode; NOT HTTP, PostgreSQL, WS, JavaFX GUI or physical proof';
        positive=9;negative=20;status='PASS';part1Slots=6;uniquePart1Assets=1;manifestId=$manifest.manifestId;cases=$results;gui='NOT RUN';runtime='NOT IMPLEMENTED'})
    return $results
}
