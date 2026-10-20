# Reuses the isolated process/cluster runner pattern from verify-v2-c-base03.ps1; BASE03 files are unchanged.
param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$MavenCommand='mvn.cmd',
    [string]$PythonCommand='python',
    [Parameter(Mandatory=$true)][string]$Base03BackupReceipt
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source=(& git.exe -C $repo rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0){throw 'Cannot pin source'}
if(@(& git.exe -C $repo status --porcelain --untracked-files=no).Count){throw 'Tracked worktree must be clean before verification'}
$backup=Get-Content -LiteralPath $Base03BackupReceipt -Raw -Encoding UTF8|ConvertFrom-Json
if(-not $backup.allChecksumsVerified -or -not $backup.gitBundleVerified -or $backup.sourceSha -ne '9a97cab646af40e45871cb76ffbfcfcb861f2954'){throw 'Verified BASE03 backup required'}
if((Get-FileHash -LiteralPath (Join-Path $backup.archivePath 'archive-manifest.json')).Hash.ToLowerInvariant() -cne $backup.manifestSha256){throw 'Backup manifest mismatch'}
& git.exe -C $repo diff --quiet 9a97cab646af40e45871cb76ffbfcfcb861f2954 -- client/src/main server/src/main protocol/src/main client/pom.xml server/pom.xml protocol/pom.xml pom.xml
if($LASTEXITCODE -ne 0){throw 'Production changed: BASE03 reuse requires new baseline review'}
foreach($path in @($repo,$JavaHome,$PostgresBin)){
    if($path -match '["&|<>%^\r\n]'){throw 'Unsupported tool path'}
}
foreach($exe in @('initdb.exe','pg_ctl.exe','createdb.exe','psql.exe')){
    if(-not(Test-Path -LiteralPath (Join-Path $PostgresBin $exe))){throw ('Missing '+$exe)}
}
$maven=(Get-Command $MavenCommand -ErrorAction Stop).Source
$python=(Get-Command $PythonCommand -ErrorAction Stop).Source
if($maven -match '["&|<>%^\r\n]'){throw 'Unsupported Maven path'}
$envFile=Join-Path $repo '.env'
if(Test-Path -LiteralPath $envFile){throw 'Refuse existing .env; use a dedicated worktree without local credentials'}
$runId='MON01-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $repo ('target\mon01-runs\'+$runId)
$public=Join-Path $repo ('evidence\v2-c-mon01\'+$runId)
foreach($folder in @($out,$public)){
    if(Test-Path -LiteralPath $folder){throw 'Run exists; no overwrite'}
    New-Item -ItemType Directory -Path $folder -Force|Out-Null
}
$utf8=New-Object Text.UTF8Encoding($false)
$steps=New-Object Collections.ArrayList
$cleanup=[ordered]@{ownedSchemasRemaining=$null;clusterStopExit=$null;privateEnvRemoved=$false;passwordFileRemoved=$false}
$saved=@{}
$envNames=@('JAVA_HOME','PATH','TEMP','TMP','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGPASSWORD','TOEIC_SEED_PASSWORD','TOEIC_PG_INTEGRATION','TOEIC_PROCTOR_GUI','TOEIC_INSTALLED_CLIENT','TOEIC_PACKAGED_CLIENT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','PYTHONDONTWRITEBYTECODE')
foreach($name in $envNames){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$started=$false;$envCreated=$false;$primaryFailure=$null
$pgdata=Join-Path $out 'pgdata'
$passwordFile=Join-Path $out 'password.private'
$temp=Join-Path $out 'temp'
New-Item -ItemType Directory -Path $temp|Out-Null
$env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
$env:TEMP=$temp;$env:TMP=$temp;$env:PYTHONDONTWRITEBYTECODE='1'
$env:DB_HOST='127.0.0.1';$env:DB_USER='mon01_test';$env:DB_NAME='toeic_mon01_test'
$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N');$env:PGPASSWORD=$env:DB_PASSWORD
$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:TOEIC_PG_INTEGRATION=$null;$env:TOEIC_PROCTOR_GUI=$null
$env:TOEIC_INSTALLED_CLIENT=$null;$env:TOEIC_PACKAGED_CLIENT=$null
$env:JAVA_TOOL_OPTIONS=('-Duser.timezone=UTC -Dtoeic.realtime.delta-experimental=false -Djava.io.tmpdir='+$temp)
$env:JDK_JAVA_OPTIONS=$null;$env:_JAVA_OPTIONS=$null
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop();$env:DB_PORT=[string]$port
Set-Location $repo

function Native([string]$exe,[string]$arguments,[string]$label,[int]$seconds=600,[switch]$NoCapture){
    $p=New-Object Diagnostics.Process
    $p.StartInfo.FileName=$exe;$p.StartInfo.Arguments=$arguments
    $p.StartInfo.WorkingDirectory=$repo;$p.StartInfo.UseShellExecute=$false;$p.StartInfo.CreateNoWindow=$true
    $p.StartInfo.RedirectStandardOutput=-not $NoCapture;$p.StartInfo.RedirectStandardError=-not $NoCapture
    $begin=[DateTime]::UtcNow.ToString('o');[void]$p.Start()
    $ownedPid=$p.Id
    if(-not $NoCapture){$stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()}
    $timedOut=-not $p.WaitForExit($seconds*1000)
    if($timedOut){
        # Only the process tree created above belongs to this step.
        & "$env:SystemRoot\System32\taskkill.exe" /PID $ownedPid /T /F *> (Join-Path $out ($label+'-timeout.private.txt'))
        $p.WaitForExit()
    }
    $code=if($timedOut){124}else{$p.ExitCode}
    if(-not $NoCapture){
        [IO.File]::WriteAllText((Join-Path $out ($label+'.private.stdout.txt')),$stdout.Result,$utf8)
        [IO.File]::WriteAllText((Join-Path $out ($label+'.private.stderr.txt')),$stderr.Result,$utf8)
    }
    [void]$steps.Add([ordered]@{name=$label;command=([IO.Path]::GetFileName($exe)+' '+$arguments);startUtc=$begin;endUtc=[DateTime]::UtcNow.ToString('o');exitCode=$code;timedOut=$timedOut;status=$(if($code -eq 0){'PASS'}else{'FAIL'});rawStdout=('target/mon01-runs/'+$runId+'/'+$label+'.private.stdout.txt');rawStderr=('target/mon01-runs/'+$runId+'/'+$label+'.private.stderr.txt')})
    $p.Dispose()
    Write-Output ('STEP '+$label+' exit='+$code)
    return $code
}
function Run([string]$exe,[string]$arguments,[string]$label,[int]$seconds=600,[switch]$NoCapture){
    $result=@(Native $exe $arguments $label $seconds -NoCapture:$NoCapture)
    $code=[int]$result[-1]
    $result|Select-Object -SkipLast 1|Write-Host
    if($code -ne 0){throw ('Step failed: '+$label+'; private logs retained')}
}
function Maven([string]$goals,[string]$label){
    $args='/d /s /c ""'+$maven+'" -o -B --no-transfer-progress "-Dmaven.repo.local='+$env:USERPROFILE+'/.m2/repository" '+$goals+'"'
    Run $env:ComSpec $args $label 1200
}
function Smoke([string]$script,[string]$label,[string]$extra=''){
    Run "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $repo ('scripts\'+$script))+'" -JavaHome "'+$JavaHome+'" '+$extra) $label 900
}
function Capture-Tests([string]$label){
    $rows=@();$xmlHashes=@()
    foreach($module in @('protocol','client','server','monitoring-spike')){
        $folder=Join-Path $repo ($module+'\target\surefire-reports')
        if(-not(Test-Path -LiteralPath $folder)){continue}
        $archive=Join-Path $out ('reports.private\'+$label+'\'+$module)
        New-Item -ItemType Directory -Path $archive -Force|Out-Null
        foreach($file in Get-ChildItem -LiteralPath $folder -Filter 'TEST-*.xml'){
            if($file.LastWriteTimeUtc -lt $script:testStartUtc){continue}
            Copy-Item -LiteralPath $file.FullName -Destination $archive
            [xml]$xml=[IO.File]::ReadAllText($file.FullName)
            foreach($case in $xml.testsuite.testcase){
                $status=if($case.failure){'FAIL'}elseif($case.error){'ERROR'}elseif($case.skipped){'SKIP'}else{'PASS'}
                $rows+=@{module=$module;class=[string]$case.classname;name=[string]$case.name;status=$status;seconds=[string]$case.time}
            }
            $xmlHashes+=@{module=$module;file=$file.Name;sha256=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
        }
    }
    $data=[ordered]@{sourceSha=$source;runId=$runId;step=$label;fixture='MOCK/model/loopback HTTP focused; real PostgreSQL/GUI recorded separately; not PHYSICAL';cases=$rows;xmlHashes=$xmlHashes}
    [IO.File]::WriteAllText((Join-Path $public ($label+'.json')),($data|ConvertTo-Json -Depth 8),$utf8)
}
try{
    [IO.File]::WriteAllText($passwordFile,$env:DB_PASSWORD,[Text.Encoding]::ASCII)
    Run (Join-Path $PostgresBin 'initdb.exe') ('-D "'+$pgdata+'" -U mon01_test --auth=scram-sha-256 --pwfile="'+$passwordFile+'" --no-locale -E UTF8') 'initdb'
    Remove-Item -LiteralPath $passwordFile
    # Windows postgres inherits even pg_ctl's redirected handles. Its own log is authoritative.
    Run (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -l "'+$out+'\postgres.private.txt" -o "-h 127.0.0.1 -p '+$port+'" -w start') 'pg-start' -NoCapture
    $started=$true
    Run (Join-Path $PostgresBin 'createdb.exe') ('-h 127.0.0.1 -p '+$port+' -U mon01_test -T template0 -E UTF8 '+$env:DB_NAME) 'createdb'
    Run (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U mon01_test -d '+$env:DB_NAME+' -At -c "SELECT version();"') 'postgres-version'
    Run (Join-Path $JavaHome 'bin\java.exe') '-version' 'java-version'
    Run $env:ComSpec ('/d /s /c ""'+$maven+'" --version"') 'maven-version'
    Run $python '--version' 'python-version'
    [IO.File]::WriteAllText($envFile,('DB_HOST=127.0.0.1'+"`nDB_PORT="+$port+"`nDB_NAME="+$env:DB_NAME+"`nDB_USER="+$env:DB_USER+"`nDB_PASSWORD="+$env:DB_PASSWORD+"`nTOEIC_SEED_PASSWORD="+$env:TOEIC_SEED_PASSWORD+"`n"),$utf8)
    $envCreated=$true
    $focused='DashboardModelTest,DashboardControllerTest,FullStateDashboardTest,LateEventsAndGapsTest,MonitoringJsonTest,MonitoringApiClientTest'
    $script:testStartUtc=[DateTime]::UtcNow
    try{Maven ('-pl protocol,client,server -am -Dtest='+$focused+' -Dsurefire.failIfNoSpecifiedTests=false test') 'mon01-dashboard-focused'}finally{Capture-Tests 'mon01-dashboard-focused'}
    Maven '-DskipTests package' 'package-for-mon01-smoke'
    # Existing C2 script copies only its harness; include the existing test cleanup support.
    $support=Join-Path $repo 'server\target\t2c2-smoke-classes\vn\edu\toeic\server\monitoring'
    New-Item -ItemType Directory -Path $support -Force|Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/SmokeCleanup*.class' $support
    Smoke 'smoke-t2c2.ps1' 'mon01-late-presence-gui' '-Gui'
}catch{
    $primaryFailure=$_.Exception.GetType().FullName
    [IO.File]::WriteAllText((Join-Path $out 'runner-error.private.txt'),[string]$_,$utf8)
}finally{
    if($started){
        try{
            $result=@(Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U mon01_test -d '+$env:DB_NAME+' -At -c "SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE ''pg_%'' AND nspname NOT IN (''public'',''information_schema'');"') 'schema-count')
            if([int]$result[-1] -eq 0){$cleanup.ownedSchemasRemaining=[int]([IO.File]::ReadAllText((Join-Path $out 'schema-count.private.stdout.txt')).Trim())}
        }finally{
            $result=@(Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -w stop -m fast') 'pg-stop')
            $cleanup.clusterStopExit=[int]$result[-1]
        }
    }
    if($envCreated -and (Test-Path -LiteralPath $envFile)){Remove-Item -LiteralPath $envFile}
    if(Test-Path -LiteralPath $passwordFile){Remove-Item -LiteralPath $passwordFile}
    $cleanup.privateEnvRemoved=-not(Test-Path -LiteralPath $envFile)
    $cleanup.passwordFileRemoved=-not(Test-Path -LiteralPath $passwordFile)
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
$data=[ordered]@{schema='mon01-verification-v1';runId=$runId;sourceSha=$source;baseSha='9a97cab646af40e45871cb76ffbfcfcb861f2954';branch=(& git.exe -C $repo branch --show-current).Trim();os=[Environment]::OSVersion.VersionString;topology='One Windows host, owned PostgreSQL SCRAM cluster/database, loopback HTTP/WS and proctor JavaFX; no physical LAN';database='toeic_mon01_test';port=$port;steps=@($steps);cleanup=$cleanup;runnerErrorType=$primaryFailure;backupArchiveId=$backup.archiveId;backupManifestSha256=$backup.manifestSha256;base02Approval='PENDING';reusedBase03RunIds=@('BASE03-20261019T095202Z-66b7cc19','BASE03-20261019T095950Z-e51076ac');physicalLan='NOT_RUN';bReview='PENDING';mon01Status=$(if($primaryFailure){'TECHNICAL_FAILURE_REQUIRES_REVIEW'}else{'TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW'});deltaCorrectnessGate='NOT_DECIDED';fullDefaultPreserved='YES';e2Allowed='NO'}
[IO.File]::WriteAllText((Join-Path $out 'run-metadata.original.json'),($data|ConvertTo-Json -Depth 10),$utf8)
foreach($step in $data.steps){$step.command=$step.command.Replace($env:USERPROFILE,'<USER_HOME>').Replace($repo,'<WORKTREE>');if($step.name -eq 'pg-start'){$step.rawStdout=$null;$step.rawStderr=$null}}
$data.metadataDerivation=@{rawPath=('target/mon01-runs/'+$runId+'/run-metadata.original.json');rawSha256=(Get-FileHash -LiteralPath (Join-Path $out 'run-metadata.original.json')).Hash.ToLowerInvariant();transformation='Redact user-home/worktree command paths only; original retained; pg-start uses postgres.private.txt'}
[IO.File]::WriteAllText((Join-Path $public 'run.json'),($data|ConvertTo-Json -Depth 10),$utf8)
Write-Output ('MON01 runId='+$runId+' source='+$source+' evidence='+$public)
Write-Output ($cleanup|ConvertTo-Json -Compress)
if($primaryFailure -or $cleanup.clusterStopExit -ne 0 -or $cleanup.ownedSchemasRemaining -ne 0){throw 'MON01 verification has a failed step or incomplete cleanup; inspect retained evidence'}
