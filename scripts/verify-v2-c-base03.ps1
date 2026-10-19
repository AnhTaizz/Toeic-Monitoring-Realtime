param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$MavenCommand='mvn.cmd',
    [string]$PythonCommand='python',
    [switch]$SkipLiveGui
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source=(& git.exe -C $repo rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0){throw 'Cannot pin source'}
if(@(& git.exe -C $repo status --porcelain --untracked-files=no).Count){throw 'Tracked worktree must be clean before verification'}
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
$runId='BASE03-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $repo ('target\base03-runs\'+$runId)
$public=Join-Path $repo ('evidence\v2-c-base03\'+$runId)
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
$env:DB_HOST='127.0.0.1';$env:DB_USER='base03_test';$env:DB_NAME='toeic_base03_test'
$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N');$env:PGPASSWORD=$env:DB_PASSWORD
$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:TOEIC_PG_INTEGRATION=$null;$env:TOEIC_PROCTOR_GUI=$null
$env:TOEIC_INSTALLED_CLIENT=$null;$env:TOEIC_PACKAGED_CLIENT=$null
$env:JAVA_TOOL_OPTIONS=('-Duser.timezone=UTC -Djava.io.tmpdir='+$temp)
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
    [void]$steps.Add([ordered]@{name=$label;command=([IO.Path]::GetFileName($exe)+' '+$arguments);startUtc=$begin;endUtc=[DateTime]::UtcNow.ToString('o');exitCode=$code;timedOut=$timedOut;status=$(if($code -eq 0){'PASS'}else{'FAIL'});rawStdout=('target/base03-runs/'+$runId+'/'+$label+'.private.stdout.txt');rawStderr=('target/base03-runs/'+$runId+'/'+$label+'.private.stderr.txt')})
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
    $data=[ordered]@{sourceSha=$source;runId=$runId;step=$label;fixture='MOCK/REPLAY except explicitly enabled PostgreSQL tests; not PHYSICAL';cases=$rows;xmlHashes=$xmlHashes}
    [IO.File]::WriteAllText((Join-Path $public ($label+'.json')),($data|ConvertTo-Json -Depth 8),$utf8)
}
try{
    [IO.File]::WriteAllText($passwordFile,$env:DB_PASSWORD,[Text.Encoding]::ASCII)
    Run (Join-Path $PostgresBin 'initdb.exe') ('-D "'+$pgdata+'" -U base03_test --auth=scram-sha-256 --pwfile="'+$passwordFile+'" --no-locale -E UTF8') 'initdb'
    Remove-Item -LiteralPath $passwordFile
    # Windows postgres inherits even pg_ctl's redirected handles. Its own log is authoritative.
    Run (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -l "'+$out+'\postgres.private.txt" -o "-h 127.0.0.1 -p '+$port+'" -w start') 'pg-start' -NoCapture
    $started=$true
    Run (Join-Path $PostgresBin 'createdb.exe') ('-h 127.0.0.1 -p '+$port+' -U base03_test -T template0 -E UTF8 '+$env:DB_NAME) 'createdb'
    Run (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U base03_test -d '+$env:DB_NAME+' -At -c "SELECT version();"') 'postgres-version'
    Run (Join-Path $JavaHome 'bin\java.exe') '-version' 'java-version'
    Run $env:ComSpec ('/d /s /c ""'+$maven+'" --version"') 'maven-version'
    Run $python '--version' 'python-version'
    [IO.File]::WriteAllText($envFile,('DB_HOST=127.0.0.1'+"`nDB_PORT="+$port+"`nDB_NAME="+$env:DB_NAME+"`nDB_USER="+$env:DB_USER+"`nDB_PASSWORD="+$env:DB_PASSWORD+"`nTOEIC_SEED_PASSWORD="+$env:TOEIC_SEED_PASSWORD+"`n"),$utf8)
    $envCreated=$true
    $full='FullSnapshotPayloadTest,EventOriginTest,ProcessPolicyAndSourceTest,ProcessCollectorTest,MonitoringDeliveryTest,FullSnapshotDeliveryTest,FullSnapshotReducerTest,MonitoringStateServiceTest,MonitoringStateMaintenanceTest,MonitoringGapTest,ProcessEventTest,MonitoringPresenceServiceTest,ExamMonitoringIntegrationTest'
    $script:testStartUtc=[DateTime]::UtcNow
    try{Maven ('-pl protocol,client,server -am -Dtest='+$full+' -Dsurefire.failIfNoSpecifiedTests=false test') 'full-focused'}finally{Capture-Tests 'full-focused'}
    Maven '-DskipTests package' 'package-for-smokes'
    # Reuse existing smoke, adding its test-only cleanup helper to the isolated loader directory.
    $support=Join-Path $repo 'server\target\t2c1-smoke-classes\vn\edu\toeic\server\monitoring'
    New-Item -ItemType Directory -Path $support -Force|Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/SmokeCleanup*.class' $support
    Smoke 'smoke-t2c1.ps1' 'full-baseline' '-Gui'
    Smoke 'smoke-a3.ps1' 'event-postgres'
    $delta='DeltaSnapshotPayloadTest,DeltaSnapshotDeliveryTest,CandidateModeSessionTest,DeltaReplayTest,ReplayCampaignTest,HandwrittenTraceTest,ReplayEngineTest,RealtimeClientTest,ProcessCollectorTraceTest,MonitoringDeltaStateTest'
    $script:testStartUtc=[DateTime]::UtcNow
    try{Maven ('-pl protocol,client,server -am -Dtest='+$delta+' -Dsurefire.failIfNoSpecifiedTests=false test') 'delta-focused'}finally{Capture-Tests 'delta-focused'}
    Smoke 'smoke-t3c3.ps1' 'oracle-campaign' '-RealScan'
    Smoke 'smoke-t3c2.ps1' 'delta-ws-pg'
    if(-not $SkipLiveGui){Smoke 'smoke-t3c4.ps1' 'local-live-gate'}
    Run $python '-B -m unittest discover -s scripts/benchmarks/e1 -p test*.py -v' 'python-e1-unit'
    Run $python '-B -m unittest discover -s scripts -p test_summarize_monitoring.py -v' 'python-measurement-unit'
    $env:TOEIC_PG_INTEGRATION='true'
    $script:testStartUtc=[DateTime]::UtcNow
    try{Maven 'package' 'regression-package'}finally{Capture-Tests 'regression-package'}
}catch{
    $primaryFailure=$_.Exception.GetType().FullName
    [IO.File]::WriteAllText((Join-Path $out 'runner-error.private.txt'),[string]$_,$utf8)
}finally{
    if($started){
        try{
            $result=@(Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U base03_test -d '+$env:DB_NAME+' -At -c "SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE ''pg_%'' AND nspname NOT IN (''public'',''information_schema'');"') 'schema-count')
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
$data=[ordered]@{schema='base03-verification-v1';runId=$runId;sourceSha=$source;branch=(& git.exe -C $repo branch --show-current).Trim();os=[Environment]::OSVersion.VersionString;topology='One Windows host, owned PostgreSQL SCRAM cluster/database, loopback HTTP/WS; not physical LAN';database='toeic_base03_test';port=$port;steps=@($steps);cleanup=$cleanup;runnerErrorType=$primaryFailure;skipLiveGui=[bool]$SkipLiveGui;physicalLan='NOT_RUN';bReview='PENDING';deltaCorrectnessGate='NOT_DECIDED';fullDefaultPreserved='YES';e2Allowed='NO'}
[IO.File]::WriteAllText((Join-Path $public 'run.json'),($data|ConvertTo-Json -Depth 10),$utf8)
Write-Output ('BASE03 runId='+$runId+' source='+$source+' evidence='+$public)
Write-Output ($cleanup|ConvertTo-Json -Compress)
if($primaryFailure -or $cleanup.clusterStopExit -ne 0 -or $cleanup.ownedSchemasRemaining -ne 0){throw 'BASE03 verification has a failed step or incomplete cleanup; inspect retained evidence'}
