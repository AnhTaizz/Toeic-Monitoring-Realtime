[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$PostgresBin,
 [Parameter(Mandatory=$true)][string]$JavaExe,
 [Parameter(Mandatory=$true)][string]$ServerJar,
 [Parameter(Mandatory=$true)][string]$BackendCommit,
 [Parameter(Mandatory=$true)][string]$ExternalRoot
)
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $repo 'scripts/exam-seed-common.ps1')
$runId=[Guid]::NewGuid().ToString('N').Substring(0,12)
$work=Join-Path $PSScriptRoot ('target/portable-seed-'+$runId)
$external=Join-Path $ExternalRoot ('portable-seed-'+$runId)
Assert-SeedNewExternal $external
New-Item -ItemType Directory -Path $work,$external | Out-Null
$pack=Join-Path $external 'mock-ex02-200'
$fixture=Join-Path $work 'source-input/docs/plan-v2/fixtures/ex02'
New-Item -ItemType Directory -Path (Split-Path -Parent $fixture) -Force|Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'docs/plan-v2/fixtures/ex02') -Destination $fixture -Recurse
Copy-Item -LiteralPath (Join-Path $repo 'docs/QUYET_DINH.md') -Destination (Join-Path $work 'source-input/docs/QUYET_DINH.md')
& (Join-Path $fixture 'verify-design.ps1') -Generate | Out-Null
$jar=Join-Path $work 'backend.jar';Copy-Item -LiteralPath $ServerJar -Destination $jar
$beforePassword=$env:PGPASSWORD;$beforeSeed=$env:TOEIC_SEED_PASSWORD
$env:PGPASSWORD='Seed!'+[Guid]::NewGuid().ToString('N');$env:TOEIC_SEED_PASSWORD='Seed!'+[Guid]::NewGuid().ToString('N')
$user='portable_seed_test';$psql=Resolve-SeedPg $PostgresBin 'psql';$pgctl=Resolve-SeedPg $PostgresBin 'pg_ctl'
$clusters=New-Object 'System.Collections.Generic.List[object]';$servers=New-Object 'System.Collections.Generic.List[object]'
function PgCtl([string[]]$Arguments){$info=New-Object Diagnostics.ProcessStartInfo;$info.FileName=$pgctl;$info.Arguments=(@($Arguments|ForEach-Object {'"'+$_+'"'}) -join ' ');$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$p=New-Object Diagnostics.Process;$p.StartInfo=$info;try{[void]$p.Start();$p.WaitForExit();if($p.ExitCode){throw 'Owned pg_ctl failed.'}}finally{$p.Dispose()}}
function Start-Cluster([string]$Name){
 $path=Join-Path $work ($Name+'-pgdata');$port=Seed-FreePort;$pw=Join-Path $work ($Name+'-password.tmp');[IO.File]::WriteAllText($pw,$env:PGPASSWORD)
 try{$r=Invoke-SeedNative (Resolve-SeedPg $PostgresBin 'initdb') @('-D',$path,'--username',$user,'--pwfile',$pw,'--auth=scram-sha-256','--encoding=UTF8','--no-locale');if($r.ExitCode){throw 'Owned TEST initdb failed.'}}finally{Remove-Item -LiteralPath $pw}
 PgCtl @('-D',$path,'-l',(Join-Path $work ($Name+'-postgres-private.log')),'-o',('-h 127.0.0.1 -p '+$port),'-w','start')
 $c=[pscustomobject]@{Path=$path;Port=$port;Running=$true};$clusters.Add($c);return $c
}
function Stop-Cluster($Cluster){if($Cluster.Running){PgCtl @('-D',$Cluster.Path,'-m','fast','-w','stop');$Cluster.Running=$false}}
function Q($Cluster,[string]$Database,[string]$Sql){return Seed-Query $psql (Seed-Connection $Database '127.0.0.1' $Cluster.Port $user) $Sql}
function Start-Server($Cluster,[string]$Database,[string]$MediaRoot){
 $port=Seed-FreePort;$info=New-Object Diagnostics.ProcessStartInfo;$info.FileName=$JavaExe;$info.Arguments='-jar "'+$jar+'"';$info.WorkingDirectory=$work;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $settings=@{DB_HOST='127.0.0.1';DB_PORT=[string]$Cluster.Port;DB_NAME=$Database;DB_USER=$user;DB_PASSWORD=$env:PGPASSWORD;TOEIC_SEED_PASSWORD=$env:TOEIC_SEED_PASSWORD;SERVER_ADDRESS='127.0.0.1';SERVER_PORT=[string]$port;TOEIC_AUDIO_ROOT=(Join-Path $MediaRoot 'audio-resources');TOEIC_IMAGE_ROOT=(Join-Path $MediaRoot 'image-resources')}
 foreach($key in $settings.Keys){$info.EnvironmentVariables[$key]=$settings[$key]}
 $p=New-Object Diagnostics.Process;$p.StartInfo=$info;[void]$p.Start();$s=[pscustomobject]@{Process=$p;Output=$p.StandardOutput.ReadToEndAsync();Error=$p.StandardError.ReadToEndAsync();Base=('http://127.0.0.1:'+$port);Token=$null;UserId=0;Stopped=$false;Name=$Database+'-'+$port};$servers.Add($s)
 $deadline=[DateTime]::UtcNow.AddSeconds(90)
 while([DateTime]::UtcNow -lt $deadline){if($p.HasExited){throw 'Owned TEST backend exited.'};try{$body=@{username='proctor1';password=$env:TOEIC_SEED_PASSWORD;requestId=[Guid]::NewGuid().ToString()}|ConvertTo-Json;$login=Invoke-RestMethod -Method POST -Uri ($s.Base+'/api/v1/auth/login') -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 3;$s.Token=$login.token;$s.UserId=$login.user.id;return $s}catch{Start-Sleep -Milliseconds 250}}
 throw 'Owned TEST backend startup timed out.'
}
function Stop-Server($Server){if(!$Server.Stopped){if(!$Server.Process.HasExited){$Server.Process.Kill();$Server.Process.WaitForExit()};[IO.File]::WriteAllText((Join-Path $work ($Server.Name+'-backend-private.log')),$Server.Output.Result+$Server.Error.Result);$Server.Process.Dispose();$Server.Stopped=$true}}
function Protected-Fingerprints($Cluster,[string]$Database){
 $tables=Q $Cluster $Database "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('exams','exam_sources','exam_question_groups','exam_media_assets','exam_questions','exam_options','exam_content_blocks')"
 $result=[ordered]@{}
 foreach($table in $tables.Split(',')){$result[$table]=Q $Cluster $Database ("SELECT count(*)::text||':'||md5(COALESCE(string_agg(row_to_json(t)::text,E'\n' ORDER BY row_to_json(t)::text),'')) FROM public.$table t")};return $result
}
function Expect-Refused([scriptblock]$Action,[string]$Message){try{& $Action|Out-Null}catch{if($_.Exception.Message -like ('*'+$Message+'*')){return};throw};throw 'Expected refusal missing.'}
try{
 $source=Start-Cluster 'source';$sourceDb='toeic_seed_source_test_'+$runId
 [void](Q $source 'postgres' ('CREATE DATABASE "'+$sourceDb+'" TEMPLATE template0 ENCODING ''UTF8'''))
 & (Join-Path $repo 'scripts/create-exam-seed.ps1') -PostgresBin $PostgresBin -Database $sourceDb -ServerJar $jar -JavaExe $JavaExe -ImportJson (Join-Path $fixture 'import.mock.json') -ExamId 'mock-ex02-200' -AudioRoot (Join-Path $fixture 'assets/audio') -ImageRoot (Join-Path $fixture 'assets/images') -Destination $pack -BackendCommit $BackendCommit -Port $source.Port -UserName $user
 $sourceData=Q $source $sourceDb (Seed-DataSql 'mock-ex02-200')
 $packManifest=Seed-VerifyPack $pack
 $sql=[IO.File]::ReadAllText((Join-Path $pack 'seed.sql'))
 $writes=@([regex]::Matches($sql,'(?m)^INSERT INTO public\.([a-z_]+)')|ForEach-Object {$_.Groups[1].Value}|Sort-Object -Unique)
 if(($writes -join ',') -cne (($script:SeedTables|Sort-Object) -join ',')){throw 'Seed SQL must write only the seven exam tables.'}
 $sourceVersion=Q $source $sourceDb 'SHOW server_version_num'
 Stop-Cluster $source
 # Delete only the generated MOCK source copy after successful pack verification; original repository/ETS untouched.
 Remove-Item -LiteralPath (Join-Path $work 'source-input') -Recurse -Force
 if(Test-Path -LiteralPath $fixture){throw 'MOCK source input must be unavailable.'}
 $target=Start-Cluster 'target';$targetDb='toeic_seed_target_test_'+$runId
 [void](Q $target 'postgres' ('CREATE DATABASE "'+$targetDb+'" TEMPLATE template0 ENCODING ''UTF8'''))
 $restoredRoot=Join-Path $external 'restored-media'
 $bootstrap=Start-Server $target $targetDb $restoredRoot;$targetProctor=$bootstrap.UserId;Stop-Server $bootstrap
 # Surrogate IDs deliberately differ. The seed must allocate new identities and resolve source FKs by checksum.
 foreach($table in @('exam_sources','exam_questions','exam_options')){[void](Q $target $targetDb ("SELECT setval(pg_get_serial_sequence('public.$table','id'),10000,false)"))}
 $protectedBefore=Protected-Fingerprints $target $targetDb
 $restore=@{Pack=$pack;PostgresBin=$PostgresBin;Database=$targetDb;ConfirmDatabase=$targetDb;ProctorUserId=$targetProctor;Destination=$restoredRoot;Port=$target.Port;UserName=$user}
 $wrongRole=$restore.Clone();$wrongRole.ProctorUserId=[long](Q $target $targetDb "SELECT min(id) FROM public.user_accounts WHERE role='CANDIDATE'");$wrongRole.Destination=Join-Path $external 'candidate-refused'
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @wrongRole} 'PROCTOR'
 # Fresh PowerShell, pack working directory, and PATH without Java/Git/Maven.
 $shell=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
 $oldPath=$env:PATH
 try{
  $env:PATH=Join-Path $env:SystemRoot 'System32'
  $offline=Invoke-SeedNative $shell @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $pack 'restore-exam-seed.ps1'),'-PostgresBin',$PostgresBin,'-Database',$targetDb,'-ConfirmDatabase',$targetDb,'-ProctorUserId',[string]$targetProctor,'-Destination',$restoredRoot,'-Port',[string]$target.Port,'-UserName',$user) $pack
  if($offline.ExitCode -ne 0){[IO.File]::WriteAllText((Join-Path $work 'offline-restore-private.log'),$offline.Output+$offline.Error);throw 'Standalone pack restore failed.'}
  Write-Output $offline.Output.Trim()
 }finally{$env:PATH=$oldPath}
 $targetData=Q $target $targetDb (Seed-DataSql 'mock-ex02-200')
 if($sourceData -cne $targetData){throw 'Exam/scoring/source/group/document/media data changed after portable restore.'}
 $protectedAfter=Protected-Fingerprints $target $targetDb
 foreach($table in $protectedBefore.Keys){if($protectedBefore[$table] -cne $protectedAfter[$table]){throw "Non-exam table changed during restore: $table"}}
 $other=Q $target $targetDb "SELECT min(id) FROM public.exam_sources"
 if([long]$other -lt 10000){throw 'Target identities were not remapped.'}
 $duplicate=$restore.Clone();$duplicate.Destination=Join-Path $external 'duplicate-refused'
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @duplicate} 'already exists'
 if(Test-Path $duplicate.Destination){throw 'Duplicate refusal wrote destination.'}
 $wrong=$restore.Clone();$wrong.Destination=Join-Path $external 'wrong-confirm';$wrong.ConfirmDatabase='wrong'
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @wrong} 'confirmation'
 $existing=$restore.Clone();$existing.Destination=$restoredRoot
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @existing} 'exists'
 $badPack=Join-Path $external 'corrupt-negative-pack';Copy-Item -LiteralPath $pack -Destination $badPack -Recurse
 $asset=@($packManifest.files|Where-Object {$_.path -like 'audio-resources/*'})[0]
 [IO.File]::AppendAllText((Join-Path $badPack $asset.path),'MOCK corruption')
 $bad=$restore.Clone();$bad.Pack=$badPack;$bad.Destination=Join-Path $external 'corrupt-refused'
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @bad} 'checksum'
 if(Test-Path $bad.Destination){throw 'Corrupt pack wrote destination.'}
 $emptyDb='toeic_seed_empty_test_'+$runId;[void](Q $target 'postgres' ('CREATE DATABASE "'+$emptyDb+'" TEMPLATE template0 ENCODING ''UTF8'''))
 $noSchema=$restore.Clone();$noSchema.Database=$emptyDb;$noSchema.ConfirmDatabase=$emptyDb;$noSchema.Destination=Join-Path $external 'schema-refused'
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @noSchema} 'schema differs'
 $nonTest=$restore.Clone();$nonTest.Database='production';$nonTest.ConfirmDatabase='production'
 Expect-Refused {& (Join-Path $pack 'restore-exam-seed.ps1') @nonTest} 'toeic_'
 if((Q $target $targetDb (Seed-DataSql 'mock-ex02-200')) -cne $targetData){throw 'Negative checks changed exam data.'}
 $app=Start-Server $target $targetDb $restoredRoot
 $catalog=Invoke-RestMethod -Uri ($app.Base+'/api/v2/exams') -Headers @{Authorization=('Bearer '+$app.Token)}
 $item=@($catalog.items|Where-Object {$_.examId -ceq 'mock-ex02-200'})
 if($item.Count -ne 1 -or !$item[0].selectable -or @($item[0].blockedReasons).Count){throw 'Restored catalog is not selectable.'}
 $documents=Q $target $targetDb "SELECT count(DISTINCT (group_id,document_id)) FROM public.exam_content_blocks WHERE exam_id='mock-ex02-200' AND document_id IS NOT NULL"
 $mediaCount=0
 foreach($entry in $packManifest.files|Where-Object {$_.path -match '^(audio-resources|image-resources)/'}){if((Seed-Hash (Join-Path $restoredRoot $entry.path)) -cne $entry.sha256){throw 'Restored media hash mismatch.'};$mediaCount++}
 $report=[ordered]@{scope='MOCK_ONLY_PORTABLE_EXAM_SEED';runId=$runId;backendCommit=$BackendCommit;backendJarSha256=(Seed-Hash $jar);sourcePostgresVersion=$sourceVersion;targetPostgresVersion=(Q $target $targetDb 'SHOW server_version_num');sourceClusterStoppedBeforeRestore=$true;sourcePort=$source.Port;targetPort=$target.Port;sourceInputDeleted=$true;seedPackHasHtml=(@(Get-ChildItem $pack -Recurse -Filter '*.html').Count -gt 0);restoreScriptFromPack=$true;freshPowerShellWithPackWorkingDirectory=$true;pathExcludesJavaGitMaven=$true;sourceImportHttpStatus=201;htmlOrWebsiteUsedForRestore=$false;onlyExamTablesWritten=$writes;counts=$packManifest.counts;documents=[int]$documents;allExamDataEqual=$true;targetSourceIdsStartAt=[long]$other;nonExamTableFingerprintsBefore=$protectedBefore;nonExamTableFingerprintsAfter=$protectedAfter;catalog=@{examId='mock-ex02-200';selectable=$true;blockedReasons=@()};mediaCount=$mediaCount;seedSqlSha256=(Seed-Hash (Join-Path $pack 'seed.sql'));manifestSha256=(Seed-Hash (Join-Path $pack 'seed-manifest.json'));negativeChecks=@('candidate PROCTOR refused before writes','duplicate examId refused before writes','wrong confirmation refused','existing media destination refused','corrupt media refused before writes','missing Flyway schema refused','non-test database refused');result='PORTABLE_EXAM_SEED_PASS';ets='BLOCKED_NOT_EXPORTED'}
 Seed-WriteJson (Join-Path $external 'REPORT.json') $report
 Seed-WriteJson (Join-Path $work 'REPORT.json') $report
 Write-Output ('PORTABLE_EXAM_SEED_PASS run='+$runId+' pack='+$pack)
}finally{
 foreach($s in $servers){Stop-Server $s}
 foreach($c in $clusters){Stop-Cluster $c}
 $env:PGPASSWORD=$beforePassword;$env:TOEIC_SEED_PASSWORD=$beforeSeed
}
