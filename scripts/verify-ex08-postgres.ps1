param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$Import,
    [Parameter(Mandatory=$true)][string]$AudioRoot,
    [Parameter(Mandatory=$true)][string]$ImageRoot,
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$MavenCommand='mvn.cmd',
    [string]$OutputRoot
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $OutputRoot){$OutputRoot=Join-Path $repo 'target\ex08-verification'}
if(-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))){throw 'Java 21 path required'}
$maven=(Get-Command $MavenCommand -ErrorAction Stop).Source
$Import=[IO.Path]::GetFullPath($Import);$AudioRoot=[IO.Path]::GetFullPath($AudioRoot);$ImageRoot=[IO.Path]::GetFullPath($ImageRoot)
foreach($path in @($maven,$repo,$Import,$AudioRoot,$ImageRoot)){if($path -match '["&|<>%^\r\n]'){throw 'Unsupported shell characters in a required path'}}
if(-not(Test-Path -LiteralPath $Import -PathType Leaf)){throw 'Missing rich-import.json'}
foreach($path in @($AudioRoot,$ImageRoot)){if(-not(Test-Path -LiteralPath $path -PathType Container)){throw ('Missing media root: '+$path)}}
foreach($exe in @('initdb.exe','pg_ctl.exe','createdb.exe','psql.exe')){if(-not(Test-Path (Join-Path $PostgresBin $exe))){throw ('Missing '+$exe)}}

$runId='ex08-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $OutputRoot $runId
if(Test-Path -LiteralPath $out){throw 'Refuse to overwrite a run'}
New-Item -ItemType Directory -Force -Path $out|Out-Null
$utf8=New-Object Text.UTF8Encoding($false)
$names=@('JAVA_HOME','PATH','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGPASSWORD','TOEIC_SEED_PASSWORD','TOEIC_PG_INTEGRATION','TOEIC_STUDY4_IMPORT','TOEIC_STUDY4_AUDIO_ROOT','TOEIC_STUDY4_IMAGE_ROOT','JAVA_TOOL_OPTIONS')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N');$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N');$env:PGPASSWORD=$env:DB_PASSWORD
$env:DB_HOST='127.0.0.1';$env:DB_USER='ex08_test';$env:DB_NAME='toeic_ex08_test';$env:TOEIC_PG_INTEGRATION='true'
$env:TOEIC_STUDY4_IMPORT=$Import;$env:TOEIC_STUDY4_AUDIO_ROOT=$AudioRoot;$env:TOEIC_STUDY4_IMAGE_ROOT=$ImageRoot
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC'
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop();$env:DB_PORT=[string]$port
$pwfile=Join-Path $out 'password.private';[IO.File]::WriteAllText($pwfile,$env:DB_PASSWORD,[Text.Encoding]::ASCII)
$pgdata=Join-Path $out 'pgdata';$started=$false;$exitCode=1;$cleanup=@{};$runtimeError=$null;$start=$null;$end=$null
Set-Location $repo
$source=(& git.exe -C $repo rev-parse HEAD).Trim();if($LASTEXITCODE -ne 0){throw 'Cannot pin source SHA'}

function Native([string]$exe,[string]$arguments,[string]$name,[switch]$NoCapture){
    $process=New-Object Diagnostics.Process
    $process.StartInfo.FileName=$exe;$process.StartInfo.Arguments=$arguments;$process.StartInfo.WorkingDirectory=$repo
    $process.StartInfo.UseShellExecute=$false;$process.StartInfo.CreateNoWindow=$true
    $process.StartInfo.RedirectStandardOutput=-not $NoCapture;$process.StartInfo.RedirectStandardError=-not $NoCapture
    [void]$process.Start()
    if(-not $NoCapture){$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()}
    $process.WaitForExit()
    if(-not $NoCapture){[IO.File]::WriteAllText((Join-Path $out ($name+'.private.log')),$stdout.Result,$utf8);[IO.File]::WriteAllText((Join-Path $out ($name+'.private.err')),$stderr.Result,$utf8)}
    return $process.ExitCode
}

try{
    if((Native (Join-Path $PostgresBin 'initdb.exe') ('-D "'+$pgdata+'" -U ex08_test --auth=scram-sha-256 --pwfile="'+$pwfile+'" --no-locale -E UTF8') 'initdb') -ne 0){throw 'initdb failed; private logs preserved'}
    Remove-Item -LiteralPath $pwfile
    if((Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -l "'+$out+'\postgres.private.log" -o "-h 127.0.0.1 -p '+$port+'" -w start') 'pg-start' -NoCapture) -ne 0){throw 'Test cluster start failed'}
    $started=$true
    if((Native (Join-Path $PostgresBin 'createdb.exe') ('-h 127.0.0.1 -p '+$port+' -U ex08_test -T template0 -E UTF8 '+$env:DB_NAME) 'createdb') -ne 0){throw 'Test database creation failed'}
    [void](Native (Join-Path $JavaHome 'bin\java.exe') '-version' 'java-version')
    [void](Native $env:ComSpec ('/d /s /c ""'+$maven+'" --version"') 'maven-version')
    [void](Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U ex08_test -d '+$env:DB_NAME+' -At -c "SELECT version();"') 'postgres-version')
    $goals='-pl server -am -Dtest=RichSessionLifecyclePostgresTest#study4RealTwoHundredQuestionAcceptance -Dsurefire.failIfNoSpecifiedTests=false test'
    $command='/d /s /c ""'+$maven+'" --batch-mode --no-transfer-progress '+$goals+'"'
    $start=[DateTime]::UtcNow.ToString('o');$exitCode=Native $env:ComSpec $command 'maven';$end=[DateTime]::UtcNow.ToString('o')
}catch{
    $runtimeError=$_.Exception.GetType().FullName;[IO.File]::WriteAllText((Join-Path $out 'runner-error.private.txt'),[string]$_,$utf8)
}finally{
    if($started){try{$code=Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$port+' -U ex08_test -d '+$env:DB_NAME+' -At -c "SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE ''pg_%'' AND nspname NOT IN (''public'',''information_schema'');"') 'schema-count';if($code -eq 0){$cleanup.ownedSchemasRemaining=[int]([IO.File]::ReadAllText((Join-Path $out 'schema-count.private.log')).Trim())}}finally{$cleanup.clusterStopExit=Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$pgdata+'" -w stop -m fast') 'pg-stop'}}
    if(Test-Path -LiteralPath $pwfile){Remove-Item -LiteralPath $pwfile}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}

$cases=@();$reports=@();$totals=@{tests=0;passed=0;failures=0;errors=0;skipped=0}
$folder=Join-Path $repo 'server\target\surefire-reports'
if(Test-Path -LiteralPath $folder){
    $dest=Join-Path $out 'reports.private\server';New-Item -ItemType Directory -Force -Path $dest|Out-Null
    foreach($file in Get-ChildItem -LiteralPath $folder -Filter 'TEST-*.RichSessionLifecyclePostgresTest.xml'){
        Copy-Item -LiteralPath $file.FullName -Destination $dest;[xml]$xml=[IO.File]::ReadAllText($file.FullName);$suite=$xml.testsuite
        $totals.tests += [int]$suite.tests;$totals.failures += [int]$suite.failures;$totals.errors += [int]$suite.errors;$totals.skipped += [int]$suite.skipped
        foreach($case in $suite.testcase){$state=if($case.failure){'FAIL'}elseif($case.error){'ERROR'}elseif($case.skipped){'SKIP'}else{'PASS'};$cases+=@{class=[string]$case.classname;name=[string]$case.name;status=$state;seconds=[string]$case.time}}
        $reports+=@{file=$file.Name;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
    }
}
$totals.passed=$totals.tests-$totals.failures-$totals.errors-$totals.skipped
$result=[ordered]@{sourceSha=$source;runId=$runId;os=[Environment]::OSVersion.VersionString;java=([IO.File]::ReadAllLines((Join-Path $out 'java-version.private.err'))|Where-Object{$_ -match '^(java|openjdk) version '}|Select-Object -First 1);maven=([IO.File]::ReadAllLines((Join-Path $out 'maven-version.private.log'))|Select-Object -First 1);postgres=([IO.File]::ReadAllText((Join-Path $out 'postgres-version.private.log')).Trim());database='toeic_ex08_test';fixture='STUDY4_6852 internal-only audited dataset; real media/PostgreSQL/Flyway/HTTP';importSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Import).Hash.ToLowerInvariant();command=('mvn --batch-mode --no-transfer-progress '+$goals);startUtc=$start;endUtc=$end;exitCode=$exitCode;runnerErrorType=$runtimeError;totals=$totals;cases=$cases;xmlHashes=$reports;cleanup=$cleanup;packagedWindowsSmoke='NOT RUN';humanListeningReview='NOT RUN'}
[IO.File]::WriteAllText((Join-Path $out 'result.json'),(($result|ConvertTo-Json -Depth 8).Replace("`r`n","`n")+"`n"),$utf8)
Write-Output ('Output: '+$out);Write-Output (@{sourceSha=$source;exitCode=$exitCode;totals=$totals;cleanup=$cleanup;runnerErrorType=$runtimeError}|ConvertTo-Json -Depth 4)
if($runtimeError){throw 'Runner failed; inspect private logs, do not publish environment or credentials'}
exit $exitCode
