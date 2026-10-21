# Internal desktop branch of demo-mon08-alerts.ps1; provisioning/teardown stay in the existing runner.
param([string]$Repository,[string]$OutputDirectory,[string]$JavaHome,[string]$MavenCommand,[string]$MavenRepository,[string]$SourceSha)
$ErrorActionPreference='Stop'
$out=$OutputDirectory;$utf8=[Text.UTF8Encoding]::new($false)
$apps=@();$server=$null;$notes=$null;$workloads=@();$closedNormally=$false
function WriteJson($name,$value){[IO.File]::WriteAllText((Join-Path $out $name),($value|ConvertTo-Json -Depth 14),$utf8)}
function StartOwned($exe,$arguments,$label,$environment=@{},[switch]$Visible){
    $p=[Diagnostics.Process]::new();$p.StartInfo.FileName=$exe;$p.StartInfo.Arguments=$arguments
    $p.StartInfo.WorkingDirectory=$Repository;$p.StartInfo.UseShellExecute=$false;$p.StartInfo.CreateNoWindow=-not $Visible
    $p.StartInfo.RedirectStandardOutput=$true;$p.StartInfo.RedirectStandardError=$true
    # Desktop children never inherit DB credentials or parent Java injection options.
    foreach($key in @('DB_PASSWORD','PGPASSWORD','TOEIC_SEED_PASSWORD','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){$p.StartInfo.EnvironmentVariables.Remove($key)}
    foreach($key in $environment.Keys){$p.StartInfo.EnvironmentVariables[$key]=[string]$environment[$key]}
    [void]$p.Start()
    return @{process=$p;stdout=$p.StandardOutput.ReadToEndAsync();stderr=$p.StandardError.ReadToEndAsync();label=$label}
}
function FinishLog($owned){
    if($owned -and $owned.process.HasExited){
        foreach($stream in @('stdout','stderr')){
            $content=$owned[$stream].Result.Replace($env:DB_PASSWORD,'<redacted>').Replace($env:TOEIC_SEED_PASSWORD,'<redacted>')
            [IO.File]::WriteAllText((Join-Path $out ($owned.label+'.'+$stream+'.private.log')),$content,$utf8)
        }
    }
}
function StopOwned($owned){
    if($owned -and -not $owned.process.HasExited){
        [void]$owned.process.CloseMainWindow()
        if(-not $owned.process.WaitForExit(10000)){$owned.process.Kill();[void]$owned.process.WaitForExit(10000)}
    }
    FinishLog $owned
}
function Api($path,$token,$body=$null){
    $args=@{Uri=($origin+'/api/v1/'+$path);TimeoutSec=5;ErrorAction='Stop'}
    if($token){$args.Headers=@{Authorization=('Bearer '+$token)}}
    if($null -ne $body){$args.Method='Post';$args.ContentType='application/json; charset=utf-8';$args.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 12))}
    return Invoke-RestMethod @args
}
function FreePort{$l=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$l.Start();$n=$l.LocalEndpoint.Port;$l.Stop();return $n}
try{
    if((git -C $Repository rev-parse HEAD).Trim() -ne $SourceSha -or (git -C $Repository status --porcelain)){throw 'Desktop requires committed, clean source'}
    $build=StartOwned $env:ComSpec ('/d /s /c ""'+$MavenCommand+'" --batch-mode --no-transfer-progress -Dmaven.repo.local="'+$MavenRepository+'" -DskipTests package"') 'desktop-build' @{JAVA_HOME=$JavaHome}
    if(-not $build.process.WaitForExit(600000)){StopOwned $build;throw 'Build budget exceeded'}
    FinishLog $build
    if($build.process.ExitCode -ne 0){throw 'Build failed; see private log'}
    $client=Join-Path $Repository 'client/target/client-0.1.0-SNAPSHOT-all.jar'
    $serverJar=Join-Path $Repository 'server/target/server-0.1.0-SNAPSHOT.jar'
    $hashes=@{}
    foreach($jar in @($client,$serverJar)){$hashes[(Split-Path $jar -Leaf)]=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()}
    WriteJson 'desktop-build-receipt.json' @{sourceSha=$SourceSha;dirty=$false;jarHashes=$hashes;format='production JAR; two independent JavaFX JVMs';repositoryDefaults='UNCHANGED';demoOverrides=@{signalExperiment=$true;alertExperiment=$true;delta=$false};hardware='USB/LAN NOT_RUN'}
    $port=FreePort;$origin='http://127.0.0.1:'+$port
    $dbEnv=@{DB_HOST=$env:DB_HOST;DB_PORT=$env:DB_PORT;DB_NAME=$env:DB_NAME;DB_USER=$env:DB_USER;DB_PASSWORD=$env:DB_PASSWORD;TOEIC_SEED_PASSWORD=$env:TOEIC_SEED_PASSWORD}
    $server=StartOwned (Join-Path $JavaHome 'bin/java.exe') ('-jar "'+$serverJar+'" --server.address=127.0.0.1 --server.port='+$port+' --logging.level.root=WARN --toeic.monitoring.signal-experiment-enabled=true --toeic.monitoring.alert-experiment-enabled=true --toeic.state.delta-enabled=false') 'desktop-server' $dbEnv
    $limit=[DateTime]::UtcNow.AddSeconds(90);$proctor=$null
    while([DateTime]::UtcNow -lt $limit -and -not $server.process.HasExited){
        try{$proctor=Api 'auth/login' $null @{requestId=[Guid]::NewGuid().ToString();username='proctor1';password=$env:TOEIC_SEED_PASSWORD};break}catch{Start-Sleep -Milliseconds 500}
    }
    if(-not $proctor){throw 'TEST server/login readiness failed'}
    $run='DESKTOP-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
    $exam=$run+'-R';$attempt=$run+'-candidate1'
    $question=@{questionId='R1';section='READING';part=5;prompt='TEST desktop: choose A or B (self-authored fixture)';correctOption='A';orderIndex=1;options=@(@{optionId='A';optionText='TEST A';orderIndex=1},@{optionId='B';optionText='TEST B';orderIndex=2})}
    $null=Api 'exams/import' $proctor.token @{examId=$exam;title='TEST desktop monitoring';description='Self-authored TEST question; not a TOEIC acceptance exam';questions=@($question)}
    $null=Api 'sessions' $proctor.token @{sessionId=$run;examId=$exam;title='TEST desktop monitoring - EXPERIMENTAL';durationSeconds=3600;proctorUsernames=@('proctor1');candidateUsernames=@('candidate1')}
    # Harmless real OS process with the existing allowlisted filename; never a real browser or a violation verdict.
    $testExe=Join-Path $out 'chrome.exe'
    Copy-Item -LiteralPath (Join-Path $env:SystemRoot 'System32/cmd.exe') -Destination $testExe
    foreach($role in @('candidate','proctor')){
        $args='-Dtoeic.server.url='+$origin+' -Dtoeic.monitoring.signal-experiment-enabled=true -Dtoeic.realtime.delta-experimental=false -Duser.home="'+(Join-Path $out ($role+'-profile'))+'" -jar "'+$client+'"'
        $apps+=StartOwned (Join-Path $JavaHome 'bin/javaw.exe') $args $role @{} -Visible
    }
    $text="TEST loopback only - EXPERIMENTAL, no cheating verdict`r`nServer: $origin`r`nCandidate: candidate1`r`nProctor: proctor1`r`nTemporary password (both): $env:TOEIC_SEED_PASSWORD`r`nAttempt: $attempt`r`n`r`n1. Log into the two separate apps. Candidate: select this attempt, Start monitoring, enter exam.`r`n2. Proctor: Process hien tai / Timeline da tin hieu, select this attempt.`r`n3. Open chrome.exe in THIS output folder: harmless TEST cmd process, NOT a browser. Switch focus, minimize/restore candidate, choose an answer.`r`n4. Close TEST process by typing exit. Candidate: exit exam, Stop monitoring, logout. Proctor: logout. Close BOTH apps.`r`nDo not submit exam until history has been checked. USB/LAN NOT_RUN.`r`nBudget: 60 minutes. To stop early create stop.request in this folder. Never share this credential file.`r`n"
    $credentials=Join-Path $out ($run+'-credentials.private.txt');[IO.File]::WriteAllText($credentials,$text,$utf8)
    $notes=Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/notepad.exe') -ArgumentList ('"'+$credentials+'"') -PassThru
    WriteJson 'desktop-ready.json' @{sourceSha=$SourceSha;server=$origin;session=$run;attempt=$attempt;candidatePid=$apps[0].process.Id;proctorPid=$apps[1].process.Id;serverPid=$server.process.Id;testProcess=$testExe;desktop='PENDING_USER_ACTION';credentialsFile=$credentials;expiresUtc=[DateTime]::UtcNow.AddMinutes(60).ToString('o')}
    Write-Output ('DESKTOP READY: '+(Join-Path $out 'desktop-ready.json'))
    $end=[DateTime]::UtcNow.AddMinutes(60)
    while([DateTime]::UtcNow -lt $end -and -not(Test-Path (Join-Path $out 'stop.request')) -and @($apps|Where-Object {-not $_.process.HasExited}).Count -gt 0){
        # Only discover workload processes whose executable is the exact owned TEST file.
        foreach($p in @(Get-Process -Name chrome -ErrorAction SilentlyContinue)){
            try{if($p.Path -eq $testExe -and $p.Id -notin @($workloads|ForEach-Object {$_.Id})){$workloads+=$p}}catch{}
        }
        try{
            $state=Api ('monitoring/attempts/'+$attempt+'/state') $proctor.token
            $events=Api ('monitoring/attempts/'+$attempt+'/events') $proctor.token
            $page=Api ('monitoring/attempts/'+$attempt+'/signals?limit=100') $proctor.token
            $items=@($page.items);$pages=1
            while($page.hasMore -and $pages -lt 20){
                $page=Api ('monitoring/attempts/'+$attempt+'/signals?limit=100&after='+$page.nextAfterSequence+'&through='+$page.throughSequence) $proctor.token
                $items+=@($page.items);$pages++
            }
            $point=@{utc=[DateTime]::UtcNow.ToString('o');state=$state.status;sequence=$state.sequence;processCount=@($state.processes).Count;processEventCount=@($events.events).Count;typedCount=$items.Count;pages=$pages;truncated=[bool]$page.hasMore;typed=$items;testPids=@($workloads|ForEach-Object {$_.Id});candidateExited=$apps[0].process.HasExited;proctorExited=$apps[1].process.HasExited}
            # Raw typed payloads are minimal experiment fields, not question content or process command lines.
            WriteJson 'desktop-observation.private.json' $point
            [IO.File]::AppendAllText((Join-Path $out 'desktop-observations.private.jsonl'),(($point|ConvertTo-Json -Depth 14 -Compress)+[Environment]::NewLine),$utf8)
        }catch{WriteJson 'desktop-api-error.json' @{utc=[DateTime]::UtcNow.ToString('o');errorType=$_.Exception.GetType().Name}}
        Start-Sleep -Seconds 2
    }
    $closedNormally=@($apps|Where-Object {-not $_.process.HasExited}).Count -eq 0
}finally{
    foreach($app in $apps){StopOwned $app}
    foreach($p in $workloads){if(-not $p.HasExited -and $p.Path -eq $testExe){[void]$p.CloseMainWindow();if(-not $p.WaitForExit(3000)){$p.Kill();[void]$p.WaitForExit(3000)}}}
    StopOwned $server
    if($notes -and -not $notes.HasExited){[void]$notes.CloseMainWindow()}
    WriteJson 'desktop-cleanup.json' @{appsClosedBeforeTeardown=$closedNormally;appsAlive=@($apps|Where-Object {-not $_.process.HasExited}).Count;serverAlive=($server -and -not $server.process.HasExited);ownedWorkloadsAlive=@($workloads|Where-Object {-not $_.HasExited}).Count;desktopAcceptance='PENDING_USER_CONFIRMATION';hardware='USB/LAN NOT_RUN'}
}
