param([switch]$Generate)
# MOCK design oracle only. No production HTTP, DTO, SQL, player or START is exercised.
$ErrorActionPreference = 'Stop'
$fixtureRoot = $PSScriptRoot
$utf8 = New-Object Text.UTF8Encoding($false)
. (Join-Path $fixtureRoot 'visual-design.ps1')
function WriteJson($name, $value) {
    if ($name -in @('import.mock.json','candidate.mock.json')) {
        # One array element per line keeps the full 200Q examples reviewable.
        $lines=New-Object 'System.Collections.Generic.List[string]'
        $lines.Add('{'); $keys=@($value.Keys)
        for ($k=0;$k -lt $keys.Count;$k++) {
            $key=$keys[$k]; $ending=if ($k -lt $keys.Count-1) {','} else {''}
            if ($value[$key] -is [array]) {
                $lines.Add('  "'+$key+'": ['); $items=@($value[$key])
                for ($j=0;$j -lt $items.Count;$j++) {
                    $comma=if ($j -lt $items.Count-1) {','} else {''}
                    $lines.Add('    '+(ConvertTo-Json -InputObject $items[$j] -Depth 60 -Compress)+$comma)
                }
                $lines.Add('  ]'+$ending)
            } else { $lines.Add('  "'+$key+'": '+(ConvertTo-Json -InputObject $value[$key] -Depth 60 -Compress)+$ending) }
        }
        $lines.Add('}')
        [IO.File]::WriteAllText((Join-Path $fixtureRoot $name), (($lines -join "`n")+"`n"), $utf8)
        return
    }
    [IO.File]::WriteAllText((Join-Path $fixtureRoot $name), (($value | ConvertTo-Json -Depth 60) + "`n"), $utf8)
}
function ReadJson($name) { Get-Content -LiteralPath (Join-Path $fixtureRoot $name) -Raw -Encoding UTF8 | ConvertFrom-Json }
function HashFile($path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function TextBlock($id, $order, $text) {
    [ordered]@{blockId=$id;type='TEXT';orderIndex=$order;text=$text;mediaId=$null;mediaType=$null}
}
function MediaBlock($id, $order, $type, $media) {
    [ordered]@{blockId=$id;type=$type;orderIndex=$order;text=$null;mediaId=$media;mediaType=$type}
}
if ($Generate) {
    $examId = 'mock-ex02-200'
    $audioDir = Join-Path $fixtureRoot "assets/audio/$examId"
    $imageDir = Join-Path $fixtureRoot "assets/images/$examId"
    New-Item -ItemType Directory -Force -Path $audioDir,$imageDir | Out-Null
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'assets/source.txt'), "MOCK EX02 self-authored prompts/options/keys; no third-party TOEIC content. CC0-1.0.`n", $utf8)
    # A geometric 8x8 bitmap authored here, never copied from an exam.
    Add-Type -AssemblyName System.Drawing
    $bitmap = New-Object Drawing.Bitmap(8,8)
    try {
        for ($x=0;$x -lt 8;$x++) { for ($y=0;$y -lt 8;$y++) {
            $color = if (($x+$y)%2 -eq 0) { [Drawing.Color]::Blue } else { [Drawing.Color]::White }
            $bitmap.SetPixel($x,$y,$color)
        }}
        $bitmap.Save((Join-Path $imageDir 'mock-grid.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $bitmap.Dispose() }
    [IO.File]::WriteAllBytes((Join-Path $imageDir 'corrupt.png'), [Text.Encoding]::ASCII.GetBytes('MOCK intentionally invalid PNG bytes'))
    # PCM mono, 8kHz, 16bit, 80 silent samples = 10ms. Not a hearing test.
    $stream = New-Object IO.MemoryStream
    $writer = New-Object IO.BinaryWriter($stream)
    foreach ($s in @('RIFF')) { $writer.Write([Text.Encoding]::ASCII.GetBytes($s)) }
    $writer.Write([int]196); $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
    $writer.Write([int]16); $writer.Write([int16]1); $writer.Write([int16]1)
    $writer.Write([int]8000); $writer.Write([int]16000); $writer.Write([int16]2); $writer.Write([int16]16)
    $writer.Write([Text.Encoding]::ASCII.GetBytes('data')); $writer.Write([int]160)
    for ($i=0;$i -lt 80;$i++) { $writer.Write([int16]0) }
    $wav = $stream.ToArray(); $writer.Dispose(); $stream.Dispose()
    $imagePath = Join-Path $imageDir 'mock-grid.png'
    $media = @([ordered]@{mediaId='img-grid';mediaType='IMAGE';sourceRef='self';sourceUrl=$null;
        storageStatus='LOCAL_VERIFIED';localFile='mock-grid.png';mimeType='image/png';sizeBytes=(Get-Item $imagePath).Length;
        sha256=(HashFile $imagePath);widthPx=8;heightPx=8;durationMs=$null;altText='MOCK blue and white grid'})
    foreach($partImage in @(4,7)) {
        $file="mock-part$partImage.png";[IO.File]::Copy($imagePath,(Join-Path $imageDir $file),$true)
        $media += [ordered]@{mediaId="img-p$partImage-only";mediaType='IMAGE';sourceRef='self';sourceUrl=$null;
            storageStatus='LOCAL_VERIFIED';localFile=$file;mimeType='image/png';sizeBytes=(Get-Item $imagePath).Length;
            sha256=(HashFile $imagePath);widthPx=8;heightPx=8;durationMs=$null;altText="MOCK Part $partImage only image"}
    }
    $groups = @(); $questions = @()
    $counts = @(6,25,39,30,30,16,54)
    $number = 1
    for ($part=1;$part -le 7;$part++) {
        $section = if ($part -le 4) { 'LISTENING' } else { 'READING' }
        $groupSize = switch ($part) { 3 {3} 4 {3} 6 {4} 7 {3} default {1} }
        for ($offset=0;$offset -lt $counts[$part-1];$offset += $groupSize) {
            $first = $number; $groupId = if ($part -eq 5) { $null } else { 'g{0:d3}' -f $first }
            $groupBlocks = @(); $documents = @()
            if ($part -le 4) {
                $file = '{0:d3}-{1}.wav' -f $first,$groupId
                $audioPath = Join-Path $audioDir $file; [IO.File]::WriteAllBytes($audioPath,$wav)
                $mediaId = 'aud-{0:d3}' -f $first
                $media += [ordered]@{mediaId=$mediaId;mediaType='AUDIO';sourceRef='self';sourceUrl=$null;
                    storageStatus='LOCAL_VERIFIED';localFile=$file;mimeType='audio/wav';sizeBytes=$wav.Length;
                    sha256=(HashFile $audioPath);widthPx=$null;heightPx=$null;durationMs=10;altText=$null}
                $groupBlocks += MediaBlock "$groupId-audio" 1 'AUDIO' $mediaId
                if ($part -eq 4 -and $offset -eq 0) { $groupBlocks += MediaBlock "$groupId-image" 2 'IMAGE' 'img-p4-only' }
            }
            if ($part -eq 6) {
                $documents += [ordered]@{documentId='passage';orderIndex=1;title='MOCK passage';
                    blocks=@((TextBlock "$groupId-passage" 1 'MOCK: Complete the four blanks [1] [2] [3] [4].'))}
            }
            if ($part -eq 7) {
                $documents += [ordered]@{documentId='email';orderIndex=1;title='MOCK Email';
                    blocks=@((TextBlock "$groupId-email" 1 'MOCK Email: The meeting starts at nine.'))}
                if ($offset -eq 0) {
                    $documents += [ordered]@{documentId='notice';orderIndex=2;title='MOCK Notice';
                        blocks=@((TextBlock "$groupId-notice" 2 'MOCK Notice: Use the blue entrance.'),
                                 (MediaBlock "$groupId-notice-image" 3 'IMAGE' 'img-p7-only'))}
                    $documents += [ordered]@{documentId='reply';orderIndex=3;title='MOCK Reply';
                        blocks=@((TextBlock "$groupId-reply" 4 'MOCK Reply: I will arrive before nine.'))}
                }
            }
            if ($groupId) { $groups += [ordered]@{groupId=$groupId;section=$section;part=$part;
                title="MOCK Part $part group $first";orderIndex=$first;mappingStatus='VERIFIED';documents=$documents;blocks=$groupBlocks} }
            for ($member=0;$member -lt $groupSize;$member++) {
                $questionId = 'q{0:d3}' -f $number
                $blocks = @((TextBlock "$questionId-prompt" 1 "MOCK Part $part question $number. Select the labelled example."))
                if ($part -eq 1) { $blocks += MediaBlock "$questionId-image" 2 'IMAGE' 'img-grid' }
                $options = @(); $letters = if ($part -eq 2) { @('A','B','C') } else { @('A','B','C','D') }
                for ($o=0;$o -lt $letters.Count;$o++) {
                    $text = if ($part -le 2) { $null } else { "MOCK example $($letters[$o])" }
                    $options += [ordered]@{optionId=$letters[$o];orderIndex=$o+1;text=$text}
                }
                $questions += [ordered]@{questionId=$questionId;section=$section;part=$part;orderIndex=$number;
                    groupId=$groupId;sourceRef='self';sourceQuestionNumber=$number;answerKeyStatus='VERIFIED';
                    blocks=$blocks;options=$options;scoring=[ordered]@{correctOption=$letters[($number-1)%$letters.Count]}}
                $number++
            }
        }
    }
    $request = [ordered]@{schemaVersion=2;exam=[ordered]@{examId=$examId;title='MOCK EX02 200 questions';description='Self-authored contract fixture, not production data.';profile='TOEIC_200'};
        sources=@([ordered]@{sourceRef='self';sourceName='MOCK EX02 self-authored';sourcePath='assets/source.txt';sourceUrl=$null;
            copyrightNotice='CC0-1.0';permissionStatus='DOCUMENTED';permissionConfirmedOn='2026-10-19';
            permissionNote='Generated here; no third-party content.';licenseDocument='README.md: self-authored fixture CC0-1.0';
            sourceSha256=(HashFile (Join-Path $fixtureRoot 'assets/source.txt'));manifestSha256=$null;
            answerKeyStatus='VERIFIED';groupMappingStatus='VERIFIED';optionContentStatus='VERIFIED';mediaValidationStatus='VERIFIED'});
        groups=$groups;media=$media;questions=$questions}
    WriteJson 'import.mock.json' $request
    $visual=WriteVisualFixtures $request
    # Explicit allowlist projection, never clone the import object into candidate data.
    $candidateGroups = @($groups | ForEach-Object { [ordered]@{groupId=$_.groupId;section=$_.section;part=$_.part;title=$_.title;orderIndex=$_.orderIndex;documents=$_.documents;blocks=$_.blocks} })
    $candidateQuestions = @($questions | ForEach-Object { [ordered]@{questionId=$_.questionId;section=$_.section;part=$_.part;orderIndex=$_.orderIndex;groupId=$_.groupId;blocks=$_.blocks;options=$_.options} })
    $candidateMedia = @($media | ForEach-Object {
        [ordered]@{mediaId=$_.mediaId;mediaType=$_.mediaType;mimeType=$_.mimeType;sizeBytes=$_.sizeBytes;sha256=$_.sha256;
            widthPx=$_.widthPx;heightPx=$_.heightPx;durationMs=$_.durationMs;altText=$_.altText;
            imagePath=$(if ($_.mediaType -eq 'IMAGE') { "/api/v2/attempts/mock-attempt/images/$($_.mediaId)" } else {$null});
            audioFileName=$(if ($_.mediaType -eq 'AUDIO') {$_.localFile} else {$null})}
    })
    WriteJson 'candidate.mock.json' ([ordered]@{schemaVersion=2;attemptId='mock-attempt';sessionId='mock-session';examId=$examId;
        title=$request.exam.title;description=$request.exam.description;profile='TOEIC_200';contractVersion='PROCTORED_V2';
        startedAt='2026-10-19T01:00:00Z';deadlineAt='2026-10-19T03:00:00Z';visualManifestId=$visual.manifestId;groups=$candidateGroups;questions=$candidateQuestions;media=$candidateMedia})
    WriteJson 'preparation.mock.json' ([ordered]@{schemaVersion=2;attemptId='mock-attempt';sessionId='mock-session';examId=$examId;title=$request.exam.title;
        contractVersion='PROCTORED_V2';sessionState='SCHEDULED';attemptState='ACTIVE';durationMinutes=120;startedAt=$null;deadlineAt=$null;
        listeningRequired=$true;audioManifestPath='/api/v1/attempts/mock-attempt/audio/manifest';visualManifest=$visual})
    WriteJson 'catalog.mock.json' ([ordered]@{schemaVersion=2;items=@(
        [ordered]@{examId=$examId;title=$request.exam.title;contentVersion=2;profile='TOEIC_200';totalQuestions=200;importStatus='IMPORTED';contentValidation='VERIFIED';mediaVerification='VERIFIED_AT_IMPORT';selectable=$true;requiresMediaRecheck=$true;blockedReasons=@()},
        [ordered]@{examId='mock-legacy';title='MOCK unverified legacy';contentVersion=1;profile=$null;totalQuestions=200;importStatus='IMPORTED';contentValidation='UNVERIFIED';mediaVerification='UNVERIFIED';selectable=$false;requiresMediaRecheck=$true;blockedReasons=@('LEGACY_CONTENT','CONTENT_UNVERIFIED','MEDIA_UNVERIFIED')}
    );nextAfterExamId=$null})
    WriteJson 'import-response.mock.json' ([ordered]@{schemaVersion=2;examId=$examId;importStatus='IMPORTED';contentValidation='VERIFIED';mediaVerification='VERIFIED_AT_IMPORT';totalQuestions=200;totalGroups=$groups.Count;totalMedia=$media.Count})
    WriteJson 'render-expected.mock.json' ([ordered]@{label='MOCK';part3Group='g032';part3Questions=@('q032','q033','q034');part3Audio='aud-032';
        part6Group='g131';part6Questions=@('q131','q132','q133','q134');part7Group='g147';
        documentIds=@('email','notice','reply');documentBlockIds=@('g147-email','g147-notice','g147-notice-image','g147-reply');
        playlist=@($media | Where-Object mediaType -eq 'AUDIO' | Sort-Object { $_.localFile } | ForEach-Object localFile);
        prefetchSlotIds=@('p1-1','p1-2','p1-3','p1-4','p1-5','p1-6');uniquePart1Images=1;
        visualPlaybackOrder=@('IMAGE_ATTACHED','LAYOUT_DONE','VISIBLE_PULSE_1','VISIBLE_PULSE_2','PLAY_CALLED');postStartBudgetMillis=3000;heartbeatTimeoutMillis=6000})
}
function Require($condition,$reason) { if (-not $condition) { throw $reason } }
function Unique($values,$reason) { Require (@($values | Sort-Object -Unique).Count -eq @($values).Count) $reason }
function RequireSequence($values,$reason) {
    $n=1; foreach ($v in $values) { Require ($v -eq $n) $reason; $n++ }
}
function AllBlocks($exam) {
    @($exam.questions | ForEach-Object blocks) + @($exam.groups | ForEach-Object blocks) + @($exam.groups | ForEach-Object documents | ForEach-Object blocks)
}
function ValidateDesign($exam) {
    Require ($exam.schemaVersion -eq 2 -and $exam.exam.profile -eq 'TOEIC_200') 'VERSION'
    Require ($exam.questions.Count -eq 200) 'COUNT'
    Unique @($exam.questions.questionId) 'DUPLICATE_QUESTION'
    Unique @($exam.groups.groupId) 'DUPLICATE_GROUP'
    Unique @($exam.media.mediaId) 'DUPLICATE_MEDIA'
    $blocks=@(AllBlocks $exam); Unique @($blocks.blockId) 'DUPLICATE_BLOCK'
    RequireSequence @($exam.questions.orderIndex) 'QUESTION_ORDER'
    $partCounts=@(6,25,39,30,30,16,54)
    for ($p=1;$p -le 7;$p++) { Require (@($exam.questions | Where-Object part -eq $p).Count -eq $partCounts[$p-1]) 'PART_COUNT' }
    $groupMap=@{}; foreach ($g in $exam.groups) { $groupMap[$g.groupId]=$g }
    $mediaMap=@{}; foreach ($m in $exam.media) { $mediaMap[$m.mediaId]=$m }
    foreach ($s in $exam.sources) {
        Require ($s.answerKeyStatus -eq 'VERIFIED' -and $s.groupMappingStatus -eq 'VERIFIED' -and $s.mediaValidationStatus -eq 'VERIFIED') 'SOURCE_UNVERIFIED'
        Require ($s.optionContentStatus -eq 'VERIFIED') 'OPTIONS_UNVERIFIED'
        Require ($s.permissionStatus -in @('USER_CONFIRMED','DOCUMENTED','LICENSED')) 'PERMISSION'
    }
    foreach ($q in $exam.questions) {
        $section=if ($q.part -le 4) {'LISTENING'} else {'READING'}
        Require ($q.section -eq $section) 'SECTION_PART'
        Require ($q.sourceRef -in @($exam.sources.sourceRef)) 'SOURCE_REFERENCE'
        Require ($q.answerKeyStatus -eq 'VERIFIED') 'KEY_UNVERIFIED'
        if ($q.part -eq 5) { Require ($null -eq $q.groupId) 'QUESTION_GROUP' }
        else { Require ($groupMap.ContainsKey($q.groupId) -and $groupMap[$q.groupId].part -eq $q.part) 'QUESTION_GROUP' }
        $expectedOptions=if ($q.part -eq 2) {3} else {4}
        Require ($q.options.Count -eq $expectedOptions) 'OPTION_COUNT'
        RequireSequence @($q.options.orderIndex) 'OPTION_ORDER'
        Require (($q.options.optionId -join '') -eq $(if ($q.part -eq 2) {'ABC'} else {'ABCD'})) 'OPTION_IDS'
        Require ($q.scoring.correctOption -in @($q.options.optionId)) 'OPTION_KEY'
        if ($q.part -ge 3) { foreach ($o in $q.options) { Require (-not [string]::IsNullOrWhiteSpace($o.text)) 'OPTION_TEXT' } }
        RequireSequence @($q.blocks.orderIndex) 'BLOCK_ORDER'
        Require (@($q.blocks | Where-Object type -eq 'TEXT').Count -gt 0) 'QUESTION_PROMPT'
        if ($q.part -eq 1) { Require (@($q.blocks | Where-Object type -eq 'IMAGE').Count -eq 1) 'PART1_IMAGE' }
    }
    foreach ($g in $exam.groups) {
        $members=@($exam.questions | Where-Object groupId -eq $g.groupId)
        Require ($members.Count -gt 0 -and $g.mappingStatus -eq 'VERIFIED') 'GROUP_MAPPING'
        Require ($g.orderIndex -eq $members[0].orderIndex) 'GROUP_ORDER'
        for ($i=1;$i -lt $members.Count;$i++) { Require ($members[$i].orderIndex -eq $members[$i-1].orderIndex+1) 'GROUP_CONTIGUITY' }
        $expected= switch ($g.part) {1 {1} 2 {1} 3 {3} 4 {3} 6 {4} 7 {$members.Count} }
        Require ($members.Count -eq $expected -and $members.Count -le 5) 'GROUP_SIZE'
        if ($g.part -le 4) {
            Require ($g.documents.Count -eq 0 -and @($g.blocks | Where-Object type -eq 'AUDIO').Count -eq 1) 'LISTENING_GROUP'
            RequireSequence @($g.blocks.orderIndex) 'BLOCK_ORDER'
            $audio=@($g.blocks | Where-Object type -eq 'AUDIO')[0]
            if ($mediaMap.ContainsKey($audio.mediaId)) { Require ($mediaMap[$audio.mediaId].localFile.StartsWith(('{0:d3}-' -f [int]$g.orderIndex))) 'AUDIO_ORDER' }
        } else {
            Require ($g.blocks.Count -eq 0 -and $g.documents.Count -ge 1 -and $g.documents.Count -le 3) 'DOCUMENT_BOUNDARY'
            if ($g.part -eq 6) { Require ($g.documents.Count -eq 1) 'DOCUMENT_BOUNDARY' }
            Unique @($g.documents.documentId) 'DOCUMENT_BOUNDARY'
            RequireSequence @($g.documents.orderIndex) 'DOCUMENT_BOUNDARY'
            RequireSequence @($g.documents | ForEach-Object blocks | ForEach-Object orderIndex) 'DOCUMENT_BOUNDARY'
            foreach ($d in $g.documents) { Require (@($d.blocks | Where-Object type -eq 'TEXT').Count -ge 1) 'DOCUMENT_BOUNDARY' }
        }
    }
    foreach ($b in $blocks) {
        Require ($b.type -in @('TEXT','IMAGE','AUDIO')) 'BLOCK_DISCRIMINATOR'
        if ($b.type -eq 'TEXT') { Require (-not [string]::IsNullOrWhiteSpace($b.text) -and $null -eq $b.mediaId -and $null -eq $b.mediaType) 'BLOCK_PAYLOAD' }
        else {
            Require ($null -ne $b.mediaType -and $b.mediaType -eq $b.type) 'NULL_DISCRIMINATOR'
            Require ($mediaMap.ContainsKey($b.mediaId)) 'MEDIA_REFERENCE'
            Require ($mediaMap[$b.mediaId].mediaType -eq $b.type -and $null -eq $b.text) 'MEDIA_TYPE'
        }
    }
    foreach ($m in $exam.media) {
        Require ($m.storageStatus -eq 'LOCAL_VERIFIED') 'MEDIA_UNVERIFIED'
        Require ($m.localFile -match '^[A-Za-z0-9][A-Za-z0-9._-]*$' -and -not $m.localFile.Contains('..')) 'UNSAFE_PATH'
        $kind=if ($m.mediaType -eq 'IMAGE') {'images'} else {'audio'}
        $path=Join-Path $fixtureRoot "assets/$kind/$($exam.exam.examId)/$($m.localFile)"
        Require (Test-Path -LiteralPath $path -PathType Leaf) 'MISSING_FILE'
        Require ((HashFile $path) -eq $m.sha256 -and (Get-Item $path).Length -eq $m.sizeBytes) 'CORRUPT_FILE'
        Require ($m.sourceRef -in @($exam.sources.sourceRef)) 'SOURCE_REFERENCE'
    }
}
function CandidateSafe($value) {
    if ($null -eq $value -or $value -is [string] -or $value.GetType().IsPrimitive) { return }
    if ($value -is [System.Collections.IEnumerable] -and $value -isnot [pscustomobject]) { foreach ($v in $value) { CandidateSafe $v }; return }
    foreach ($prop in $value.PSObject.Properties) {
        Require ($prop.Name -notmatch '(?i)correctOption|scoring|answerKey|sourceRef|sourceId|sourceUrl|sourcePath|sourceQuestion|localFile|explanation|credential|token|permission|license') 'CANDIDATE_LEAK'
        CandidateSafe $prop.Value
    }
}
function DesignAccess($role,$owner,$assigned,$revoked,$operation,$state) {
    if ($revoked) { return 401 }
    if ($operation -eq 'START') {
        if ($role -ne 'PROCTOR' -or -not $assigned) { return 403 }
        if ($state -eq 'CLOSED') { return 409 }; return 200
    }
    if ($role -ne 'CANDIDATE' -or -not $owner) { return 403 }
    if ($operation -eq 'TAKEOVER' -and $state -eq 'SCHEDULED') { return 200 }
    if ($operation -eq 'IMAGE' -and $state -eq 'SCHEDULED') {return 403} # Existing access case now targets Part7-only, not prefetch Part1.
    if ($operation -in @('EXAM','SAVE','SUBMIT','IMAGE') -and $state -ne 'IN_PROGRESS') { return 409 }
    return 200
}
$exam=ReadJson 'import.mock.json'; ValidateDesign $exam
$candidate=ReadJson 'candidate.mock.json'; CandidateSafe $candidate
Require ($candidate.questions.Count -eq 200) 'CANDIDATE_COUNT'
$render=ReadJson 'render-expected.mock.json'
Require ((@($exam.questions | Where-Object groupId -eq $render.part3Group).questionId -join ',') -eq ($render.part3Questions -join ',')) 'PART3_RENDER'
Require ((@($exam.questions | Where-Object groupId -eq $render.part6Group).questionId -join ',') -eq ($render.part6Questions -join ',')) 'PART6_RENDER'
$part7=@($candidate.groups | Where-Object groupId -eq $render.part7Group)[0]
Require (($part7.documents.documentId -join ',') -eq ($render.documentIds -join ',')) 'PART7_RENDER'
Require ((@($part7.documents | ForEach-Object blocks | ForEach-Object blockId) -join ',') -eq ($render.documentBlockIds -join ',')) 'PART7_BLOCK_RENDER'
$audio=@($exam.media | Where-Object mediaType -eq 'AUDIO' | Sort-Object { $_.localFile })
Require ($audio.Count -eq 54 -and ($audio.localFile -join ',') -eq ($render.playlist -join ',')) 'PLAYLIST'
$cases=ReadJson 'negative.mock.json'; $results=@()
foreach ($case in $cases.cases) {
    $copy=($exam | ConvertTo-Json -Depth 60 | ConvertFrom-Json)
    $projection=($candidate | ConvertTo-Json -Depth 60 | ConvertFrom-Json)
    switch ($case.id) {
        'duplicate-question' {$copy.questions[1].questionId=$copy.questions[0].questionId}
        'duplicate-group' {$copy.groups[1].groupId=$copy.groups[0].groupId}
        'duplicate-block' {$copy.questions[1].blocks[0].blockId=$copy.questions[0].blocks[0].blockId}
        'duplicate-media' {$copy.media[1].mediaId=$copy.media[0].mediaId}
        'bad-order' {$copy.questions[1].orderIndex=1}
        'bad-part-section' {$copy.questions[0].section='READING'}
        'foreign-group' {$copy.questions[0].groupId='foreign'}
        'missing-media' {$copy.questions[0].blocks[1].mediaId='missing'}
        'foreign-media' {$copy.questions[0].blocks[1].mediaId='other-exam-image'}
        'bad-option-key' {$copy.questions[0].scoring.correctOption='Z'}
        'null-discriminator' {$copy.questions[0].blocks[1].mediaType=$null}
        'unverified-source' {$copy.sources[0].answerKeyStatus='UNVERIFIED'}
        'unverified-options' {$copy.sources[0].optionContentStatus='UNVERIFIED'}
        'bad-document-boundary' {$g=@($copy.groups | Where-Object groupId -eq 'g147')[0];$g.documents[1].documentId='email'}
        'bad-document-order' {$g=@($copy.groups | Where-Object groupId -eq 'g147')[0];$g.documents[1].blocks[0].orderIndex=1}
        'candidate-key-leak' {$projection.questions[0] | Add-Member -NotePropertyName correctOption -NotePropertyValue 'A'}
        'unsafe-path' {$copy.media[0].localFile='../outside.png'}
        'missing-file' {$copy.media[0].localFile='missing.png'}
        'corrupt-file' {$copy.media[0].localFile='corrupt.png'}
        default {throw "Unknown negative case $($case.id)"}
    }
    $reason='NO_REJECTION'
    try {
        if ($case.id -eq 'candidate-key-leak') { CandidateSafe $projection }
        else { ValidateDesign $copy }
    } catch { $reason=$_.Exception.Message }
    Require ($reason -eq $case.expectedReason) "Wrong rejection $($case.id): $reason"
    $results += [ordered]@{id=$case.id;status='PASS';reason=$reason;scope='MOCK_DESIGN_ONLY'}
}
foreach ($case in $cases.accessCases) {
    $status=DesignAccess $case.role $case.owner $case.assigned $case.revoked $case.operation $case.sessionState
    Require ($status -eq $case.expectedStatus) "Access design mismatch $($case.id)"
    $results += [ordered]@{id=$case.id;status='PASS';httpDesign=$status;scope='MOCK_DESIGN_ONLY'}
}
$prep=ReadJson 'preparation.mock.json'; Require ($null -eq $prep.deadlineAt -and $prep.sessionState -eq 'SCHEDULED') 'PREPARATION'
$catalog=ReadJson 'catalog.mock.json'; Require ($catalog.items[0].selectable -and -not $catalog.items[1].selectable -and $catalog.items[1].totalQuestions -eq 200) 'CATALOG'
$visualResults=@(InvokeVisualChecks $exam $candidate $render)
$summary=[ordered]@{label='MOCK';scope='Design fixtures only; NOT production validation, HTTP, PostgreSQL, GUI, hearing or START runtime';
    baseline='1d35b53729abade7b460d35ab07d042b6f9f3571';positive='PASS';questions=$exam.questions.Count;groups=$exam.groups.Count;
    media=$exam.media.Count;audioFiles=$audio.Count;negativeAndAccessCases=$results.Count;results=$results;
    visualPositiveCases=9;visualNegativeCases=20;totalDesignCases=($results.Count+$visualResults.Count);visualManifestId=(ReadJson 'visual-manifest.mock.json').manifestId;
    importSha256=(HashFile (Join-Path $fixtureRoot 'import.mock.json'));candidateSha256=(HashFile (Join-Path $fixtureRoot 'candidate.mock.json'));
    runtime='NOT RUN';postgres='NOT RUN';javafx='NOT RUN';hearing='NOT RUN';designApproval='TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE';approvalSource='User-provided confirmation from the project team representative, 2026-10-19.';githubApproval='NOT CLAIMED'}
WriteJson 'verification.mock.json' $summary
Write-Output ([pscustomobject]$summary | Select-Object label,positive,questions,groups,media,audioFiles,negativeAndAccessCases,visualPositiveCases,visualNegativeCases,totalDesignCases,runtime,designApproval | ConvertTo-Json)
