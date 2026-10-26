param(
    [ValidateSet('Benchmark')][string]$Mode='Benchmark',
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$MavenCommand='mvn.cmd',
    [string]$OutputRoot,
    [switch]$AccountPin
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $OutputRoot){$OutputRoot=Join-Path $repo 'target\phase4\benchmark'}
if(-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))){throw 'Java 21 path required'}
$maven=(Get-Command $MavenCommand -ErrorAction Stop).Source
foreach($path in @($maven,$repo)){if($path -match '["&|<>%^\r\n]'){throw 'Unsupported shell characters in tool/repository path'}}
foreach($exe in @('initdb.exe','pg_ctl.exe','createdb.exe','psql.exe')){if(-not(Test-Path (Join-Path $PostgresBin $exe))){throw ('Missing '+$exe)}}
$runId='phase4-benchmark-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $OutputRoot $runId
if(Test-Path -LiteralPath $out){throw 'Refuse to overwrite a run'}
New-Item -ItemType Directory -Force -Path $out|Out-Null
$utf8=New-Object Text.UTF8Encoding($false)
$saved=@{}
foreach($name in @('JAVA_HOME','PATH','PATHEXT','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGPASSWORD','TOEIC_SEED_PASSWORD','TOEIC_PG_INTEGRATION','TOEIC_PROCTOR_GUI','TOEIC_CANDIDATE_GUI','TOEIC_INSTALLED_CLIENT','TOEIC_PACKAGED_CLIENT','JAVA_TOOL_OPTIONS','TOEIC_ROOM_GUI','TOEIC_LOGIN_GUI','TOEIC_PHASE4_BENCHMARK','TOEIC_PHASE4_OUTPUT')){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$env:JAVA_HOME=$JavaHome
$env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:DB_HOST='127.0.0.1';$env:DB_USER='room_admission_test';$env:DB_NAME='toeic_room_admission_test'
$env:PGPASSWORD=$env:DB_PASSWORD
$env:TOEIC_PG_INTEGRATION='true';$env:TOEIC_PROCTOR_GUI=$null
$env:TOEIC_LOGIN_GUI=if($Mode -in @('Gui','Full')){'true'}else{$null}
$env:TOEIC_ROOM_GUI=if($Mode -in @('Gui','Full')){'true'}else{$null}
$env:TOEIC_CANDIDATE_GUI=if($Mode -in @('Gui','Full')){'true'}else{$null}
if($Mode -eq 'Gui'){$env:TOEIC_PROCTOR_GUI='true'}
$env:TOEIC_INSTALLED_CLIENT=$null;$env:TOEIC_PACKAGED_CLIENT=$null
# JAVA_TOOL_OPTIONS prints a banner on every nested java.exe stderr. The real
# child-process diagnostic tests correctly reject that banner as child output.
$env:JAVA_TOOL_OPTIONS=$null
$env:TOEIC_PHASE4_BENCHMARK='true'
$env:TOEIC_PHASE4_OUTPUT=$out
foreach($name in @('TOEIC_ROOM_PIN_KEY','TOEIC_ADMIN_BOOTSTRAP_USERNAME','TOEIC_ADMIN_BOOTSTRAP_PASSWORD')) {
 $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$null,'Process')
}
$saved['TOEIC_BENCHMARK_ACCOUNT_PIN']=$env:TOEIC_BENCHMARK_ACCOUNT_PIN
$env:TOEIC_BENCHMARK_ACCOUNT_PIN=if($AccountPin){'true'}else{$null}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$env:DB_PORT=[string]$port
$pwfile=Join-Path $out 'password.private'
[IO.File]::WriteAllText($pwfile,$env:DB_PASSWORD,[Text.Encoding]::ASCII)
$pgdata=Join-Path $out 'pgdata'
$started=$false;$exitCode=1;$cleanup=@{};$runtimeError=$null
Set-Location $repo
$source=(& git.exe -C $repo rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0){throw 'Cannot pin source SHA'}
function Native([string]$exe,[string]$arguments,[string]$name,[switch]$NoCapture){
    $process=New-Object Diagnostics.Process
    $process.StartInfo.FileName=$exe;$process.StartInfo.Arguments=$arguments
    $process.StartInfo.WorkingDirectory=$repo;$process.StartInfo.UseShellExecute=$false;$process.StartInfo.CreateNoWindow=$true
    $process.StartInfo.RedirectStandardOutput=-not $NoCapture;$process.StartInfo.RedirectStandardError=-not $NoCapture
    [void]$process.Start()
    if(-not $NoCapture){$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()}
    $process.WaitForExit()
    if(-not $NoCapture){
        [IO.File]::WriteAllText((Join-Path $out ($name+'.private.log')),$stdout.Result,$utf8)
        [IO.File]::WriteAllText((Join-Path $out ($name+'.private.err')),$stderr.Result,$utf8)
    }
    return $process.ExitCode
}
try{
    if((Native (Join-Path $PostgresBin 'initdb.exe') ('-D "'+$pgdata+'" -U room_admission_test --auth=scram-sha-256 --pwfile="'+$pwfile+'" --no-locale -E UTF8') 'initdb') -ne 0){throw 'initdb failed; private logs preserved'}
    Remove-Item -LiteralPath $pwfile
    if((Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -l "'+$out+'\postgres.private.log" -o "-h 127.0.0.1 -p '+$port+'" -w start') 'pg-start' -NoCapture) -ne 0){throw 'Test cluster start failed'}
    $started=$true
    if((Native (Join-Path $PostgresBin 'createdb.exe') ('-h 127.0.0.1 -p '+$port+' -U room_admission_test -T template0 -E UTF8 '+$env:DB_NAME) 'createdb') -ne 0){throw 'Test database creation failed'}
    [void](Native (Join-Path $JavaHome 'bin\java.exe') '-version' 'java-version')
    [void](Native $env:ComSpec ('/d /s /c ""'+$maven+'" --version"') 'maven-version')
    [void](Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U room_admission_test -d '+$env:DB_NAME+' -At -c "SELECT version();"') 'postgres-version')
    $selected='Phase4RoomBenchmarkTest'
    $goals='-pl server -am -Dtest='+$selected+' -Dsurefire.failIfNoSpecifiedTests=false test'
    $command='/d /s /c ""'+$maven+'" --batch-mode --no-transfer-progress -Duser.timezone=UTC '+$goals+'"'
    $start=[DateTime]::UtcNow.ToString('o')
    $exitCode=Native $env:ComSpec $command 'maven'
    $end=[DateTime]::UtcNow.ToString('o')
}catch{
    $runtimeError=$_.Exception.GetType().FullName
    [IO.File]::WriteAllText((Join-Path $out 'runner-error.private.txt'),[string]$_,$utf8)
}finally{
    if($started){
        try{
            $code=Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U room_admission_test -d '+$env:DB_NAME+' -At -c "SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE ''pg_%'' AND nspname NOT IN (''public'',''information_schema'');"') 'schema-count'
            if($code -eq 0){$cleanup.ownedSchemasRemaining=[int]([IO.File]::ReadAllText((Join-Path $out 'schema-count.private.log')).Trim())}
        }finally{
            $cleanup.clusterStopExit=Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -w stop -m fast') 'pg-stop'
        }
    }
    if(Test-Path -LiteralPath $pwfile){Remove-Item -LiteralPath $pwfile}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
$cases=@();$reports=@();$totals=@{tests=0;passed=0;failures=0;errors=0;skipped=0}
foreach($module in @('protocol','client','server','monitoring-spike')){
    $folder=Join-Path $repo ($module+'\target\surefire-reports')
    if(-not(Test-Path -LiteralPath $folder)){continue}
    $dest=Join-Path $out ('reports.private\'+$module)
    New-Item -ItemType Directory -Force -Path $dest|Out-Null
    foreach($file in Get-ChildItem -LiteralPath $folder -Filter 'TEST-*.xml'){
        # Only reports produced by this invocation count; stale XML is never evidence.
        if($file.LastWriteTimeUtc -lt [DateTime]::Parse($start).ToUniversalTime()){continue}
        if($Mode -ne 'Full' -and $selected.Split(',') -notcontains ($file.BaseName -replace '^TEST-.*\.','')){continue}
        Copy-Item -LiteralPath $file.FullName -Destination $dest
        [xml]$xml=[IO.File]::ReadAllText($file.FullName)
        $suite=$xml.testsuite
        $totals.tests += [int]$suite.tests;$totals.failures += [int]$suite.failures;$totals.errors += [int]$suite.errors;$totals.skipped += [int]$suite.skipped
        foreach($case in $suite.testcase){
            $state=if($case.failure){'FAIL'}elseif($case.error){'ERROR'}elseif($case.skipped){'SKIP'}else{'PASS'}
            $cases+=@{class=[string]$case.classname;name=[string]$case.name;status=$state;seconds=[string]$case.time}
        }
        $reports+=@{module=$module;file=$file.Name;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
    }
}
$totals.passed=$totals.tests-$totals.failures-$totals.errors-$totals.skipped
$javaVersion=[IO.File]::ReadAllLines((Join-Path $out 'java-version.private.err'))|Where-Object{$_ -match '^(java|openjdk) version '}|Select-Object -First 1
$result=[ordered]@{sourceSha=$source;runId=$runId;mode=$Mode;os=[Environment]::OSVersion.VersionString;java=$javaVersion;maven=([IO.File]::ReadAllLines((Join-Path $out 'maven-version.private.log'))|Select-Object -First 1);postgres=([IO.File]::ReadAllText((Join-Path $out 'postgres-version.private.log')).Trim());database='toeic_room_admission_test';host='127.0.0.1';port=$port;fixture='MOCK self-authored exam content; real PostgreSQL/Flyway/HTTP/WebSocket';command=('mvn --batch-mode --no-transfer-progress -Duser.timezone=UTC '+$goals);startUtc=$start;endUtc=$end;exitCode=$exitCode;runnerErrorType=$runtimeError;totals=$totals;cases=$cases;xmlHashes=$reports;cleanup=$cleanup;guiEvidence=if($Mode -in @('Gui','Full')){'Candidate GUI enabled; see individual cases'}else{'NOT RUN'};manualLanHearing='NOT RUN'}
[IO.File]::WriteAllText((Join-Path $out 'result.json'),(($result|ConvertTo-Json -Depth 8).Replace("`r`n","`n")+"`n"),$utf8)
Write-Output ('Output: '+$out)
Write-Output (@{sourceSha=$source;mode=$Mode;exitCode=$exitCode;totals=$totals;cleanup=$cleanup;runnerErrorType=$runtimeError}|ConvertTo-Json -Depth 4)
if($runtimeError){throw 'Runner failed; inspect private logs, do not publish environment or credentials'}
exit $exitCode
