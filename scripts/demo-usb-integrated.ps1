param(
    [ValidateSet('Mock','Real')][string]$Mode='Mock',
    [switch]$ReadUsbStorage,
    [ValidateSet('MSFT_DISK','WIN32_DISKDRIVE')][string]$Provider='MSFT_DISK',
    [ValidateRange(1,120)][int]$DurationSeconds=15,
    [ValidateRange(250,10000)][int]$CadenceMillis=1000,
    [ValidateRange(100,10000)][int]$QueryTimeoutMillis=5000,
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$MavenCommand='mvn.cmd',
    [string]$MavenRepository,
    [string]$OutputDirectory,
    [switch]$Timeline,
    [switch]$TimelineIdentityOnly,
    [switch]$AlertRules,
    [switch]$Integrated,
    [ValidateRange(0,120)][int]$ViewSeconds=20,
    [switch]$Desktop
)
$ErrorActionPreference='Stop'
$repo=if($PSScriptRoot){[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))}else{[IO.Path]::GetFullPath((Get-Location).Path)}
if(-not(Test-Path -LiteralPath (Join-Path $repo 'pom.xml'))){throw 'Run from repository root'}
if($Desktop -and ($ReadUsbStorage -or $Mode -ne 'Mock' -or $Integrated -or $TimelineIdentityOnly)){throw 'Desktop uses real existing observers, no USB source or synthetic integrated fixture; use -Desktop alone'}
if($AlertRules -and (($Mode -ne 'Mock' -and -not $Integrated) -or -not $Timeline)){throw 'Alert demo requires MOCK timeline or explicit integrated TEST'}
if($Integrated -and (-not $AlertRules -or -not $Timeline -or $TimelineIdentityOnly)){throw 'Integrated TEST requires timeline and alert rules'}
if($Mode -eq 'Real' -and -not $ReadUsbStorage){throw 'REAL source requires explicit -ReadUsbStorage'}
if($Mode -eq 'Mock' -and $ReadUsbStorage){throw 'Do not mix REAL permission with MOCK mode'}
if(-not $MavenRepository){$MavenRepository=Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository'}
$runId='usb-'+[Guid]::NewGuid().ToString('N').Substring(0,16)
if(-not $OutputDirectory){$OutputDirectory=Join-Path $repo ('target\usb-integrated-'+$runId)}
$out=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $out){throw 'Refuse existing output directory'}
if(-not $out.StartsWith(($repo+'\target\'),[StringComparison]::OrdinalIgnoreCase)){throw 'Output must be a new folder below this checkout target'}
$maven=(Get-Command $MavenCommand -ErrorAction Stop).Source
foreach($path in @($repo,$out,$maven,$MavenRepository,$PostgresBin,$JavaHome)){if(-not $path -or $path -match '["&|<>%^\r\n]'){throw 'Invalid local tool/path'}}
foreach($exe in @('initdb.exe','postgres.exe','pg_ctl.exe','createdb.exe','psql.exe')){if(-not(Test-Path -LiteralPath (Join-Path $PostgresBin $exe))){throw ('Missing '+$exe)}}
if(-not(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))){throw 'Java21 required'}
New-Item -ItemType Directory -Path $out|Out-Null
$utf8=New-Object Text.UTF8Encoding($false)
$saved=@{};$started=$false;$mavenProcess=$null;$exitCode=1;$failedType=$null;$cleanup=@{};$start=[DateTime]::UtcNow.ToString('o')
$names=@('JAVA_HOME','PATH','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGPASSWORD','TOEIC_SEED_PASSWORD','TOEIC_PG_INTEGRATION','TOEIC_PROCTOR_GUI','TOEIC_PACKAGED_CLIENT','TOEIC_INSTALLED_CLIENT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','TOEIC_USB_TEST_RUN_ID','TOEIC_USB_TEST_OUTPUT','TOEIC_USB_TEST_SOURCE','TOEIC_USB_REAL_CIM','TOEIC_USB_PROVIDER','TOEIC_USB_DURATION_SECONDS','TOEIC_USB_CADENCE_MILLIS','TOEIC_USB_TIMEOUT_MILLIS','TOEIC_MON09_GUI','TOEIC_MON09_VIEW_SECONDS','TOEIC_MON08_TEST','TOEIC_MON_INTEGRATED')
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
$env:DB_HOST='127.0.0.1';$env:DB_PORT=[string](Get-Random -Minimum 30000 -Maximum 39000);$env:DB_NAME='toeic_usb_test_'+$runId.Substring(4);$env:DB_USER='usb_demo_test'
$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N');$env:PGPASSWORD=$env:DB_PASSWORD;$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:TOEIC_PG_INTEGRATION='true';$env:TOEIC_USB_TEST_RUN_ID=$runId;$env:TOEIC_USB_TEST_OUTPUT=$out
$env:TOEIC_USB_REAL_CIM=if($Mode -eq 'Real'){'true'}else{'false'};$env:TOEIC_USB_PROVIDER=$Provider
$env:TOEIC_USB_DURATION_SECONDS=[string]$DurationSeconds;$env:TOEIC_USB_CADENCE_MILLIS=[string]$CadenceMillis;$env:TOEIC_USB_TIMEOUT_MILLIS=[string]$QueryTimeoutMillis
foreach($name in @('TOEIC_PROCTOR_GUI','TOEIC_PACKAGED_CLIENT','TOEIC_INSTALLED_CLIENT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
$env:TOEIC_MON08_TEST=if($AlertRules){'true'}else{'false'}
$env:TOEIC_MON_INTEGRATED=if($Integrated){'true'}else{'false'}
$env:TOEIC_MON09_GUI=if($Timeline){'true'}else{'false'};$env:TOEIC_MON09_VIEW_SECONDS=[string]$ViewSeconds
$pgdata=Join-Path $out 'pgdata'
function Native([string]$exe,[string]$arguments,[string]$label,[string]$inputText,[switch]$NoCapture){
    $process=New-Object Diagnostics.Process
    $process.StartInfo.FileName=$exe;$process.StartInfo.Arguments=$arguments;$process.StartInfo.WorkingDirectory=$repo
    $process.StartInfo.UseShellExecute=$false;$process.StartInfo.CreateNoWindow=$true
    $process.StartInfo.RedirectStandardOutput=-not $NoCapture;$process.StartInfo.RedirectStandardError=-not $NoCapture;$process.StartInfo.RedirectStandardInput=-not $NoCapture
    [void]$process.Start()
    if(-not $NoCapture){$o=$process.StandardOutput.ReadToEndAsync();$e=$process.StandardError.ReadToEndAsync();if($inputText){$process.StandardInput.Write($inputText)};$process.StandardInput.Close()}
    $process.WaitForExit()
    # Never persist a plaintext bootstrap password, including unexpected command diagnostics.
    if(-not $NoCapture){
        $stdout=$o.Result.Replace($env:DB_PASSWORD,'<redacted>').Replace($env:TOEIC_SEED_PASSWORD,'<redacted>')
        $stderr=$e.Result.Replace($env:DB_PASSWORD,'<redacted>').Replace($env:TOEIC_SEED_PASSWORD,'<redacted>')
        [IO.File]::WriteAllText((Join-Path $out ($label+'.private.log')),$stdout,$utf8)
        [IO.File]::WriteAllText((Join-Path $out ($label+'.private.err')),$stderr,$utf8)
    }else{[IO.File]::WriteAllText((Join-Path $out ($label+'-receipt.json')),(@{exitCode=$process.ExitCode;capture=$false}|ConvertTo-Json),$utf8)}
    return $process.ExitCode
}
Push-Location $repo
try{
    $source=(& git.exe rev-parse HEAD).Trim();if($LASTEXITCODE -ne 0){throw 'Cannot pin source'};$env:TOEIC_USB_TEST_SOURCE=$source
    $sourceDirty=[bool](& git.exe status --porcelain)
    Write-Output ('USB '+$Mode+' TEST run='+$runId+' source='+$source+' output='+$out)
    if($Mode -eq 'Real'){Write-Output ('EXPLICIT REAL CIM: provider='+$Provider+' duration='+$DurationSeconds+'s delay='+$CadenceMillis+'ms queryTimeout='+$QueryTimeoutMillis+'ms; only minimal storage classification, no files/serial; TEST server only.')}
    [IO.File]::WriteAllText((Join-Path $out 'config.json'),(@{mode=$Mode;provider=$Provider;durationSeconds=$DurationSeconds;cadenceDelayMillis=$CadenceMillis;queryTimeoutMillis=$QueryTimeoutMillis;dbPort=[int]$env:DB_PORT;sourceSha=$source;sourceDirty=$sourceDirty;timeline=[bool]$Timeline;alertRules=[bool]$AlertRules;integrated=[bool]$Integrated;identityOnly=[bool]$TimelineIdentityOnly;viewSeconds=$ViewSeconds}|ConvertTo-Json),$utf8)
    # Offline bootstrap has no listening socket. Password travels only over process stdin.
    if((Native (Join-Path $PostgresBin 'initdb.exe') ('-D "'+$pgdata+'" -U usb_demo_test --auth=trust --no-locale -E UTF8') 'initdb' '') -ne 0){throw 'Owned initdb failed'}
    if((Native (Join-Path $PostgresBin 'postgres.exe') ('--single -D "'+$pgdata+'" template1') 'offline-password' ("SET password_encryption='scram-sha-256';`nALTER ROLE usb_demo_test PASSWORD '"+$env:DB_PASSWORD+"';`n")) -ne 0){throw 'Offline password initialization failed'}
    $hba=Join-Path $pgdata 'pg_hba.conf';$hbaText=[IO.File]::ReadAllText($hba);$hbaText=[regex]::Replace($hbaText,'(?m)^(?!\s*#)(.*\s)trust\s*$','$1scram-sha-256');[IO.File]::WriteAllText($hba,$hbaText,$utf8)
    if([regex]::IsMatch($hbaText,'(?m)^(?!\s*#).*\strust\s*$')){throw 'Refuse network trust authentication'}
    if((Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -l "'+$out+'\postgres.private.log" -o "-h 127.0.0.1 -p '+$env:DB_PORT+'" -w start') 'pg-start' '' -NoCapture) -ne 0){throw 'Owned cluster start failed'};$started=$true
    if((Native (Join-Path $PostgresBin 'createdb.exe') ('-h 127.0.0.1 -p '+$env:DB_PORT+' -U usb_demo_test -T template0 -E UTF8 '+$env:DB_NAME) 'createdb' '') -ne 0){throw 'Owned TEST database creation failed'}
    if($Desktop){
        & (Join-Path $PSScriptRoot 'monitoring-desktop-session.ps1') -Repository $repo -OutputDirectory $out -JavaHome $JavaHome -MavenCommand $maven -MavenRepository $MavenRepository -SourceSha $source
        $exitCode=0
    }else{
    $methods=if($Mode -eq 'Real'){'realCimReadinessCommitAckAndHistory'}else{'mockCoordinatorTimelineCommitAckLossAndPagedHistory+actualRevokeCancelsPendingSourceAndCannotFlushNewEvents+businessDeadlineStopsCoordinatorUsingRealScopedHeartbeat+coordinatorStatusCommitFaultRollsBackThenRetriesSameMessage'}
    $tests='UsbIntegratedDemoPostgresTest#'+$methods
    if($Timeline){$methods=if($AlertRules){'experimentalAlertsReachDashboard'}elseif($TimelineIdentityOnly){'candidateObserversReachAuthorizedLane'}elseif($Mode -eq 'Real'){'realCimStatusReachesDashboard'}else{'mixedMockDeliveryReachesTwoAttemptDashboard'};$tests='MonitoringTimelinePostgresTest#'+$methods}
    if($Integrated){$tests='MonitoringTimelinePostgresTest#allExistingSourcesShareRunAndReachHistoryTimelineAndAlerts+candidateObserversReachAuthorizedLane+productionObserversRetireOnTerminalAndRevoke+applicationControlsStopRestartLogoutAndSwitchAccount'}
    $mavenProcess=New-Object Diagnostics.Process;$mavenProcess.StartInfo.FileName=$env:ComSpec
    $mavenProcess.StartInfo.Arguments='/d /s /c ""'+$maven+'" --batch-mode --no-transfer-progress -Duser.timezone=UTC -Dmaven.repo.local="'+$MavenRepository+'" -pl server -am -Dtest='+$tests+' -Dsurefire.failIfNoSpecifiedTests=false test"'
    $mavenProcess.StartInfo.WorkingDirectory=$repo;$mavenProcess.StartInfo.UseShellExecute=$false;$mavenProcess.StartInfo.CreateNoWindow=$true;$mavenProcess.StartInfo.RedirectStandardOutput=$true;$mavenProcess.StartInfo.RedirectStandardError=$true
    [void]$mavenProcess.Start();$stdout=$mavenProcess.StandardOutput.ReadToEndAsync();$stderr=$mavenProcess.StandardError.ReadToEndAsync();$mavenProcess.WaitForExit();$exitCode=$mavenProcess.ExitCode
    [IO.File]::WriteAllText((Join-Path $out 'maven.private.log'),$stdout.Result.Replace($env:DB_PASSWORD,'<redacted>').Replace($env:TOEIC_SEED_PASSWORD,'<redacted>'),$utf8)
    [IO.File]::WriteAllText((Join-Path $out 'maven.private.err'),$stderr.Result.Replace($env:DB_PASSWORD,'<redacted>').Replace($env:TOEIC_SEED_PASSWORD,'<redacted>'),$utf8)
    $reports=Join-Path $repo 'server\target\surefire-reports';$xml=Join-Path $reports 'TEST-vn.edu.toeic.server.monitoring.signal.UsbIntegratedDemoPostgresTest.xml'
    if($Timeline){$xml=Join-Path $reports 'TEST-vn.edu.toeic.server.monitoring.signal.MonitoringTimelinePostgresTest.xml'}
    if((Test-Path -LiteralPath $xml) -and (Get-Item -LiteralPath $xml).LastWriteTimeUtc -ge [DateTime]::Parse($start).ToUniversalTime()){
        $archive=Join-Path $out 'reports.private';New-Item -ItemType Directory -Path $archive|Out-Null;Copy-Item -LiteralPath $xml -Destination $archive
    }
    if($exitCode -ne 0){throw 'Maven TEST failed; private logs preserved'}
    }
}catch{$failedType=$_.Exception.GetType().FullName;[IO.File]::WriteAllText((Join-Path $out 'runner-error.private.txt'),($_.ToString().Replace($env:DB_PASSWORD,'<redacted>').Replace($env:TOEIC_SEED_PASSWORD,'<redacted>')),$utf8);Write-Output ('TEST failed: '+$failedType+'; inspect local logs')}
finally{
    if($mavenProcess -and -not $mavenProcess.HasExited){[IO.File]::WriteAllText((Join-Path $out 'stop.request'),'STOP',$utf8);[void]$mavenProcess.WaitForExit(15000);$cleanup.mavenStopped=$mavenProcess.HasExited}
    if($started -or (Test-Path -LiteralPath (Join-Path $pgdata 'postmaster.pid'))){
        try{
            $code=Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$env:DB_PORT+' -U usb_demo_test -d '+$env:DB_NAME+' -At -c "SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE ''pg_%'' AND nspname NOT IN (''public'',''information_schema'');"') 'schema-count' ''
            if($code -eq 0){$cleanup.ownedSchemasRemaining=[int]([IO.File]::ReadAllText((Join-Path $out 'schema-count.private.log')).Trim())}
            if($Desktop){$cleanup.testDatabaseDropExit=Native (Join-Path $PostgresBin 'dropdb.exe') ('-h 127.0.0.1 -p '+$env:DB_PORT+' -U usb_demo_test '+$env:DB_NAME) 'drop-test-db' ''}
        }finally{$cleanup.clusterStopExit=Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -w stop -m fast') 'pg-stop' ''}
    }
    $cleanup.postmasterPidAbsent=-not(Test-Path -LiteralPath (Join-Path $pgdata 'postmaster.pid'))
    $summary=Join-Path $out 'demo-summary.json'
    if(Test-Path -LiteralPath (Join-Path $out 'stop.request')){$exitCode=130}
    $result=@{sourceSha=$source;sourceDirty=$sourceDirty;runId=$runId;mode=$Mode;timeline=[bool]$Timeline;alertRules=[bool]$AlertRules;integrated=[bool]$Integrated;identityOnly=[bool]$TimelineIdentityOnly;exitCode=$exitCode;runnerErrorType=$failedType;startUtc=$start;endUtc=[DateTime]::UtcNow.ToString('o');cleanup=$cleanup;hardware='0/12 NOT_RUN';summaryPresent=(Test-Path -LiteralPath $summary)}
    [IO.File]::WriteAllText((Join-Path $out 'result.json'),($result|ConvertTo-Json -Depth 5),$utf8)
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')};Pop-Location
}
Write-Output ($result|ConvertTo-Json -Depth 5)
if($failedType -or $exitCode -ne 0 -or $cleanup.clusterStopExit -ne 0 -or $cleanup.ownedSchemasRemaining -ne 0){throw 'USB TEST demo incomplete/failed; preserve output'}
