[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$PostgresBin,
 [Parameter(Mandatory=$true)][string]$Database,
 [Parameter(Mandatory=$true)][string]$ServerJar,
 [Parameter(Mandatory=$true)][string]$JavaExe,
 [Parameter(Mandatory=$true)][string]$ImportJson,
 [Parameter(Mandatory=$true)][string]$ExamId,
 [Parameter(Mandatory=$true)][string]$AudioRoot,
 [Parameter(Mandatory=$true)][string]$ImageRoot,
 [Parameter(Mandatory=$true)][string]$Destination,
 [Parameter(Mandatory=$true)][string]$BackendCommit,
 [string]$HostName='127.0.0.1',
 [Parameter(Mandatory=$true)][int]$Port,
 [Parameter(Mandatory=$true)][string]$UserName
)
. (Join-Path $PSScriptRoot 'exam-seed-common.ps1')
Assert-SeedDatabase $Database $HostName $Port;Assert-SeedExam $ExamId
Assert-SeedNewExternal $Destination
if($BackendCommit -notmatch '^[a-f0-9]{40}$'){throw 'Exact backend commit SHA required.'}
if([string]::IsNullOrWhiteSpace($env:TOEIC_SEED_PASSWORD)){throw 'Set TOEIC_SEED_PASSWORD privately for this isolated TEST server.'}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$classpathFile=Join-Path $repo 'tools/exam-dataset/target/runtime-classpath.txt'
if(!(Test-Path $classpathFile)){throw 'Build exam-dataset and runtime classpath first.'}
$cp=(Join-Path $repo 'tools/exam-dataset/target/classes')+';'+[IO.File]::ReadAllText($classpathFile).Trim()
$inputData=[IO.File]::ReadAllText($ImportJson)|ConvertFrom-Json
if($inputData.schemaVersion -ne 2 -or $inputData.exam.examId -cne $ExamId){throw 'Import JSON identity/schema mismatch.'}
$psql=Resolve-SeedPg $PostgresBin 'psql';$connection=Seed-Connection $Database $HostName $Port $UserName
# Start an owned backend process with explicit TEST DB settings, never reuse a possibly shared HTTP endpoint.
$work=Join-Path $repo ('tools/exam-dataset/target/seed-export-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$httpPort=Seed-FreePort;$base='http://127.0.0.1:'+$httpPort
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=$JavaExe;$info.Arguments='-jar "'+([IO.Path]::GetFullPath($ServerJar))+'"';$info.WorkingDirectory=$work
$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$settings=@{DB_HOST=$HostName;DB_PORT=[string]$Port;DB_NAME=$Database;DB_USER=$UserName;DB_PASSWORD=$env:PGPASSWORD;TOEIC_SEED_PASSWORD=$env:TOEIC_SEED_PASSWORD;SERVER_ADDRESS='127.0.0.1';SERVER_PORT=[string]$httpPort;TOEIC_AUDIO_ROOT=([IO.Path]::GetFullPath($AudioRoot));TOEIC_IMAGE_ROOT=([IO.Path]::GetFullPath($ImageRoot));MONITORING_DELTA_ENABLED='false'}
foreach($key in $settings.Keys){$info.EnvironmentVariables[$key]=$settings[$key]}
if([string]::IsNullOrWhiteSpace($env:PGPASSWORD)){throw 'Set PGPASSWORD privately for the isolated TEST backend connection.'}
$p=New-Object Diagnostics.Process;$p.StartInfo=$info;$started=$false
try{
 [void]$p.Start();$started=$true;$stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
 $token=$null;$deadline=[DateTime]::UtcNow.AddSeconds(90)
 while([DateTime]::UtcNow -lt $deadline){
  if($p.HasExited){throw 'Owned TEST backend exited; inspect private local log.'}
  try{$body=@{username='proctor1';password=$env:TOEIC_SEED_PASSWORD;requestId=[Guid]::NewGuid().ToString()}|ConvertTo-Json;$login=Invoke-RestMethod -Method POST -Uri ($base+'/api/v1/auth/login') -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 3;$token=$login.token;break}catch{Start-Sleep -Milliseconds 250}
 }
 if(!$token){throw 'Owned TEST backend did not become ready.'}
 $before=Seed-Query $psql $connection ("SELECT count(*) FROM public.exams WHERE exam_id='$ExamId'")
 if($before -ne '0'){throw 'Source examId exists; create a fresh seed TEST database, no overwrite.'}
 $headers=@{Authorization=('Bearer '+$token)}
 try{$import=Invoke-WebRequest -UseBasicParsing -Method POST -Uri ($base+'/api/v2/exams/import') -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([IO.File]::ReadAllBytes($ImportJson))}catch{throw 'Backend V2 refused import; incomplete ETS remains BLOCKED, no seed exported.'}
 if($import.StatusCode -ne 201){throw 'Only a real HTTP201 import may produce a seed.'}
 $catalog=Invoke-RestMethod -Uri ($base+'/api/v2/exams') -Headers $headers
 $item=@($catalog.items|Where-Object {$_.examId -ceq $ExamId})
 if($item.Count -ne 1 -or !$item[0].selectable -or @($item[0].blockedReasons).Count){throw 'Imported exam must be selectable with no blockedReasons.'}
 # Preserve PostgreSQL JSON arrays directly. Windows PowerShell 5.1 can decorate arrays
 # passed through Add-Member/ConvertTo-Json as {value:...,Count:...}.
 $dataJson=Seed-Query $psql $connection (Seed-DataSql $ExamId)
 $schemaJson=Seed-Query $psql $connection (Seed-SchemaSql)
 $snapshot=Join-Path $work 'exam-data.json'
 $metadata=',"validation":"V2_HTTP_201_AND_SELECTABLE","schemaColumns":'+$schemaJson+',"backendCommit":"'+$BackendCommit+'","backendJarSha256":"'+(Seed-Hash $ServerJar)+'"}'
 [IO.File]::WriteAllText($snapshot,$dataJson.Substring(0,$dataJson.Length-1)+$metadata,(New-Object Text.UTF8Encoding($false)))
 $run=Invoke-SeedNative $JavaExe @('-cp',$cp,'vn.edu.toeic.dataset.PortableExamSeed','create','--snapshot',$snapshot,'--audio-root',([IO.Path]::GetFullPath($AudioRoot)),'--image-root',([IO.Path]::GetFullPath($ImageRoot)),'--scripts',$PSScriptRoot,'--destination',([IO.Path]::GetFullPath($Destination)))
 if($run.ExitCode -ne 0){[IO.File]::WriteAllText((Join-Path $work 'pack-private.log'),$run.Output+$run.Error);throw 'Seed pack creation refused; inspect private log.'}
 Write-Output ('PORTABLE_SEED_CREATE_PASS exam='+$ExamId+' HTTP201/selectable questions='+$item[0].totalQuestions)
}finally{
 if($started){if(!$p.HasExited){$p.Kill();$p.WaitForExit()};[IO.File]::WriteAllText((Join-Path $work 'backend-private.log'),$stdout.Result+$stderr.Result,(New-Object Text.UTF8Encoding($false)))}
 $p.Dispose()
}
