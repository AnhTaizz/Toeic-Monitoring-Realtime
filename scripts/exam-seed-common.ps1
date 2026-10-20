$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$script:SeedTables=@('exams','exam_sources','exam_question_groups','exam_media_assets','exam_questions','exam_options','exam_content_blocks')
function Assert-SeedDatabase([string]$Database,[string]$HostName,[int]$Port) {
 if($Database -notmatch '^toeic_[a-z0-9_]*test_[a-z0-9_]+$'){throw 'Only an explicitly named toeic_*test_* database is allowed.'}
 if($HostName -ne '127.0.0.1' -or $Port -lt 1 -or $Port -gt 65535 -or $Port -in @(5433,55434)){throw 'Use an isolated loopback TEST PostgreSQL port, excluding shared ports.'}
}
function Assert-SeedExam([string]$ExamId){if($ExamId -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$'){throw 'Invalid examId.'}}
function Resolve-SeedPg([string]$Bin,[string]$Name){$p=Join-Path $Bin ($Name+'.exe');if(!(Test-Path -LiteralPath $p -PathType Leaf)){throw "Missing PostgreSQL tool: $Name"};return $p}
function Invoke-SeedNative([string]$Command,[string[]]$Arguments,[string]$WorkingDirectory=''){
 $quoted=@($Arguments | ForEach-Object {$v=[regex]::Replace([string]$_,'(\\*)"','$1$1\"');$v=[regex]::Replace($v,'(\\+)$','$1$1');'"'+$v+'"'}) -join ' '
 $info=New-Object Diagnostics.ProcessStartInfo
 $info.EnvironmentVariables['PGCLIENTENCODING']='UTF8'
 if($WorkingDirectory){$info.WorkingDirectory=$WorkingDirectory}
 $info.FileName=$Command;$info.Arguments=$quoted;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $info.StandardOutputEncoding=New-Object Text.UTF8Encoding($false);$info.StandardErrorEncoding=New-Object Text.UTF8Encoding($false)
 $p=New-Object Diagnostics.Process;$p.StartInfo=$info
 try{[void]$p.Start();$out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync();$p.WaitForExit();return [pscustomobject]@{ExitCode=$p.ExitCode;Output=$out.Result;Error=$err.Result}}finally{$p.Dispose()}
}
function Seed-Connection([string]$Database,[string]$HostName,[int]$Port,[string]$UserName){return @('--host',$HostName,'--port',[string]$Port,'--username',$UserName,'--dbname',$Database,'--no-password')}
function Seed-Query([string]$Psql,[string[]]$Connection,[string]$Sql){
 $r=Invoke-SeedNative $Psql ($Connection+@('-X','-At','-v','ON_ERROR_STOP=1','-c',$Sql))
 if($r.ExitCode -ne 0){throw 'TEST PostgreSQL command failed; SQL and connection diagnostics suppressed.'};return $r.Output.Trim()
}
function Seed-Hash([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Seed-WriteJson([string]$Path,$Data){[IO.File]::WriteAllText($Path,($Data|ConvertTo-Json -Depth 100),(New-Object Text.UTF8Encoding($false)))}
function Assert-SeedNewExternal([string]$Path){
 $full=[IO.Path]::GetFullPath($Path)
 if(Test-Path -LiteralPath $full){throw 'Destination exists; no overwrite.'}
 $parent=Split-Path -Parent $full
 while($parent){if(Test-Path -LiteralPath (Join-Path $parent '.git')){throw 'Seed outputs must be outside Git worktrees.'};if((Test-Path -LiteralPath $parent) -and ((Get-Item -LiteralPath $parent -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Output reparse point refused.'};$parent=Split-Path -Parent $parent}
}
function Seed-SchemaSql {
 return "SELECT COALESCE(json_agg(t ORDER BY table_name,ordinal_position),'[]'::json)::text FROM (SELECT table_name,column_name,udt_name,is_nullable,ordinal_position FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('exams','exam_sources','exam_question_groups','exam_media_assets','exam_questions','exam_options','exam_content_blocks')) t"
}
function Seed-DataSql([string]$ExamId){
 Assert-SeedExam $ExamId
 $parts=New-Object 'System.Collections.Generic.List[string]'
 foreach($table in $script:SeedTables){
  $expr='to_jsonb(t)';$from="public.$table t";$order='to_jsonb(t)::text'
  if($table -eq 'exams'){$expr="to_jsonb(t)-'created_by'"}
  elseif($table -eq 'exam_sources'){$expr="to_jsonb(t)-'id'";$order='t.source_sha256'}
  elseif($table -in @('exam_questions','exam_media_assets')){$expr="(to_jsonb(t)-'id'-'source_id')||jsonb_build_object('source_sha256',s.source_sha256)";$from+=" LEFT JOIN public.exam_sources s ON s.exam_id=t.exam_id AND s.id=t.source_id";$order=if($table -eq 'exam_questions'){'t.order_index'}else{'t.media_id'}}
  elseif($table -eq 'exam_options'){$expr="to_jsonb(t)-'id'";$order='t.question_id,t.order_index'}
  elseif($table -eq 'exam_question_groups'){$order='t.order_index'}
  elseif($table -eq 'exam_content_blocks'){$order='t.block_id'}
  $parts.Add("'$table',(SELECT COALESCE(jsonb_agg($expr ORDER BY $order),'[]'::jsonb) FROM $from WHERE t.exam_id='$ExamId')")
 }
 return "SELECT jsonb_build_object('examId','$ExamId','tables',jsonb_build_object("+($parts -join ',')+'))::text'
}
function Seed-FreePort {do{$l=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0);$l.Start();$n=$l.LocalEndpoint.Port;$l.Stop()}while($n -in @(5433,55434));return $n}
function Seed-VerifyPack([string]$Pack){
 $manifestFile=Join-Path $Pack 'seed-manifest.json';$expected=([IO.File]::ReadAllText((Join-Path $Pack 'seed-manifest.sha256'))).Trim()
 if((Seed-Hash $manifestFile) -cne $expected){throw 'Manifest checksum mismatch.'}
 $m=[IO.File]::ReadAllText($manifestFile)|ConvertFrom-Json
 if($m.formatVersion -ne 1 -or $m.producer -cne 'PORTABLE_EXAM_SEED_V1' -or $m.validation -cne 'V2_HTTP_201_AND_SELECTABLE'){throw 'Unsupported/unverified seed pack.'}
 Assert-SeedExam $m.examId
 $allowed=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
 [void]$allowed.Add('seed-manifest.json');[void]$allowed.Add('seed-manifest.sha256')
 foreach($entry in $m.files){
  $relative=[string]$entry.path
  if($relative -match '[\\:]' -or $relative.StartsWith('/') -or @($relative.Split('/')|Where-Object {$_ -in @('','.','..')}).Count){throw 'Unsafe pack path.'}
  if(!$allowed.Add($relative)){throw 'Duplicate pack path.'}
  if($relative -notin @('seed.sql','exam-data.json','restore-exam-seed.ps1','exam-seed-common.ps1') -and $relative -notmatch ('^(audio-resources|image-resources)/'+[regex]::Escape($m.examId)+'/[A-Za-z0-9][A-Za-z0-9._-]*$')){throw 'Unexpected non-exam pack file.'}
  $p=Join-Path $Pack $relative;$item=Get-Item -LiteralPath $p -Force
  if($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $item.Length -ne $entry.sizeBytes -or (Seed-Hash $p) -cne $entry.sha256){throw 'Pack file checksum/size mismatch.'}
 }
 foreach($p in Get-ChildItem -LiteralPath $Pack -Force -Recurse){if($p.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Pack reparse point refused.'};if(!$p.PSIsContainer){$rel=$p.FullName.Substring(([IO.Path]::GetFullPath($Pack)).TrimEnd('\','/').Length+1).Replace('\','/');if(!$allowed.Contains($rel)){throw 'Unlisted pack file.'}}}
 foreach($required in @('seed.sql','exam-data.json','restore-exam-seed.ps1','exam-seed-common.ps1')){if(!$allowed.Contains($required)){throw 'Incomplete seed pack.'}}
 $data=[IO.File]::ReadAllText((Join-Path $Pack 'exam-data.json'))|ConvertFrom-Json
 if($data.examId -cne $m.examId -or $data.validation -cne 'V2_HTTP_201_AND_SELECTABLE'){throw 'Manifest/data identity mismatch.'}
 foreach($table in $script:SeedTables){if(@($data.tables.$table).Count -ne $m.counts.$table){throw 'Manifest/data count mismatch.'}}
 if(@($data.tables.exam_media_assets).Count -ne $m.mediaCount){throw 'Manifest/data media count mismatch.'}
 foreach($asset in $data.tables.exam_media_assets){
  if($asset.exam_id -cne $m.examId -or $asset.storage_status -cne 'LOCAL_VERIFIED' -or $asset.media_type -notin @('AUDIO','IMAGE') -or $asset.local_file -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$' -or $asset.local_file.Contains('..')){throw 'Invalid media metadata.'}
  $kind=if($asset.media_type -ceq 'AUDIO'){'audio-resources'}else{'image-resources'}
  $file=Join-Path $Pack ($kind+'/'+$m.examId+'/'+$asset.local_file)
  if((Seed-Hash $file) -cne $asset.sha256 -or (Get-Item -LiteralPath $file).Length -ne $asset.size_bytes){throw 'Media differs from validated DB metadata.'}
 }
 return $m
}
