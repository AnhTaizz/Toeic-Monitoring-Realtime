param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $OutputDirectory){throw 'Use a new owned TEST output directory.'}
[void](New-Item -ItemType Directory -Path $OutputDirectory)
$run=[Guid]::NewGuid().ToString('N').Substring(0,12)
$exam='B39-E-'+$run;$session='B39-S-'+$run
$audio=Join-Path $OutputDirectory $exam;[void](New-Item -ItemType Directory -Path $audio)
$rate=8000;$seconds=120;$size=$rate*$seconds*2
$stream=[IO.File]::Create((Join-Path $audio 'qa-listening.wav'));$writer=[IO.BinaryWriter]::new($stream)
try {
 foreach($chunk in @('RIFF')){$writer.Write([Text.Encoding]::ASCII.GetBytes($chunk))}
 $writer.Write([int](36+$size));$writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '));$writer.Write([int]16)
 $writer.Write([int16]1);$writer.Write([int16]1);$writer.Write([int]$rate);$writer.Write([int]($rate*2));$writer.Write([int16]2);$writer.Write([int16]16)
 $writer.Write([Text.Encoding]::ASCII.GetBytes('data'));$writer.Write([int]$size)
 for($i=0;$i -lt ($rate*$seconds);$i++){ $sample=if(($i % ($rate*6)) -lt ($rate*3)){[int16](1200*[Math]::Sin(2*[Math]::PI*440*$i/$rate))}else{[int16]0};$writer.Write([int16]$sample) }
} finally {$writer.Dispose();$stream.Dispose()}
$questions=@()
foreach($section in @('LISTENING','READING')){
 $listening=$section -eq 'LISTENING';$id=if($listening){'L1'}else{'R1'}
 $questions+=@{questionId=$id;section=$section;part=$(if($listening){1}else{5});groupId=$null;passageText=$null;audioFile=$(if($listening){'qa-listening.wav'}else{$null});prompt=('TEST '+$id+' - choose A');correctOption='A';orderIndex=$(if($listening){1}else{2});options=@(@{optionId='A';optionText='TEST A';orderIndex=1},@{optionId='B';optionText='TEST B';orderIndex=2})}
}
$fixture=@{examId=$exam;title='TEST B39 LAN';description='Synthetic QA fixture; not TOEIC production content or hearing evidence';questions=$questions}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'exam.json'),($fixture|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$manifest=@{runId=$run;examId=$exam;sessionId=$session;candidate1Attempt=($session+'-candidate1');candidate2Attempt=($session+'-candidate2');audioSha256=(Get-FileHash -LiteralPath (Join-Path $audio 'qa-listening.wav')).Hash.ToLowerInvariant();status='TEST_DATA_ONLY_PHYSICAL_LAN_NOT_RUN'}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-data.json'),($manifest|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$manifest|ConvertTo-Json
