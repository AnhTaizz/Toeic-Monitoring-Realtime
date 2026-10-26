# Internal desktop branch of demo-mon08-alerts.ps1; provisioning/teardown stay in the existing runner.
param([string]$Repository,[string]$OutputDirectory,[string]$JavaHome,[string]$MavenCommand,[string]$MavenRepository,[string]$SourceSha,[switch]$ReadUsbStorage,[int]$CadenceMillis=1000,[int]$QueryTimeoutMillis=5000)
$ErrorActionPreference='Stop'
$out=$OutputDirectory;$utf8=[Text.UTF8Encoding]::new($false)
$apps=@();$server=$null;$notes=$null;$workloads=@();$closedNormally=$false;$script:forcedShutdown=$false
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
        if(-not $owned.process.WaitForExit(10000)){$script:forcedShutdown=$true;$owned.process.Kill();[void]$owned.process.WaitForExit(10000)}
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
function SourceFiles {
    $files=@(Get-Item -LiteralPath (Join-Path $Repository 'pom.xml'))
    foreach($module in @('client','server','protocol','monitoring-spike','windows-observer')){
        $files+=Get-Item -LiteralPath (Join-Path $Repository ($module+'/pom.xml'))
        $files+=Get-ChildItem -LiteralPath (Join-Path $Repository ($module+'/src')) -File -Recurse
    }
    $files+=Get-ChildItem -LiteralPath (Join-Path $Repository 'scripts') -Filter '*.ps1' -File
    return @($files|Sort-Object FullName|ForEach-Object {[ordered]@{path=$_.FullName.Substring($Repository.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
}
try{
    if((git -C $Repository rev-parse HEAD).Trim() -ne $SourceSha){throw 'Source HEAD changed before desktop build'}
    $sourceDirty=[bool](git -C $Repository status --porcelain)
    $sourceBefore=SourceFiles
    $sourceJson=$sourceBefore|ConvertTo-Json -Depth 4 -Compress
    WriteJson 'desktop-source-receipt.json' @{sourceSha=$SourceSha;sourceDirty=$sourceDirty;files=$sourceBefore}
    # A unique TEST artifact avoids overwriting a server JAR already in use by another local session.
    $desktopServerName='server-desktop-'+[Guid]::NewGuid().ToString('N').Substring(0,12)
    $build=StartOwned $env:ComSpec ('/d /s /c ""'+$MavenCommand+'" --batch-mode --no-transfer-progress -Dmaven.repo.local="'+$MavenRepository+'" -Dtoeic.server.artifact-name='+$desktopServerName+' -DskipTests package"') 'desktop-build' @{JAVA_HOME=$JavaHome}
    if(-not $build.process.WaitForExit(600000)){StopOwned $build;throw 'Build budget exceeded'}
    FinishLog $build
    if($build.process.ExitCode -ne 0){throw 'Build failed; see private log'}
    if(($sourceJson -ne ((SourceFiles)|ConvertTo-Json -Depth 4 -Compress)) -or (git -C $Repository rev-parse HEAD).Trim() -ne $SourceSha){throw 'Source changed during desktop build; do not present mismatched app'}
    $client=Join-Path $Repository 'client/target/client-0.1.0-SNAPSHOT-all.jar'
    $serverJar=Join-Path $Repository ('server/target/'+$desktopServerName+'.jar')
    $hashes=@{}
    foreach($jar in @($client,$serverJar)){$hashes[(Split-Path $jar -Leaf)]=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()}
    WriteJson 'desktop-build-receipt.json' @{sourceSha=$SourceSha;dirty=$sourceDirty;sourceInventory='desktop-source-receipt.json';jarHashes=$hashes;format='production JAR; two independent JavaFX JVMs';repositoryDefaults='UNCHANGED';demoOverrides=@{signalExperiment=$true;alertExperiment=$true;delta=$false;candidateUsb=[bool]$ReadUsbStorage};hardware='PENDING_USER_CONFIRMATION'}
    $port=FreePort;$origin='http://127.0.0.1:'+$port
    $dbEnv=@{DB_HOST=$env:DB_HOST;DB_PORT=$env:DB_PORT;DB_NAME=$env:DB_NAME;DB_USER=$env:DB_USER;DB_PASSWORD=$env:DB_PASSWORD;TOEIC_SEED_PASSWORD=$env:TOEIC_SEED_PASSWORD;TOEIC_SEED_DEMO_ACCOUNTS='true'}
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
        # Both login windows support the feature; the server-authenticated CANDIDATE flow owns the source.
        # This also works when the user logs the two roles in the opposite window order.
        if($ReadUsbStorage){
            $args='-Dtoeic.monitoring.usb-enabled=true -Dtoeic.monitoring.usb-cadence-millis='+$CadenceMillis+' -Dtoeic.monitoring.usb-query-timeout-millis='+$QueryTimeoutMillis+' '+$args
        }
        $apps+=StartOwned (Join-Path $JavaHome 'bin/javaw.exe') $args $role @{} -Visible
    }
    $text="LƯỢT TEST CỤC BỘ — HAI ỨNG DỤNG THẬT`r`nMáy chủ: $origin`r`nThí sinh: candidate1`r`nGiám thị: proctor1`r`nMật khẩu: dùng mật khẩu phát triển MOCK đã công bố trong AGENTS.md.`r`nLượt thi: $attempt`r`n`r`n"+
        "1. Đăng nhập hai cửa sổ. Thí sinh: chọn lượt thi, bấm Bắt đầu giám sát rồi Vào phòng thi & làm bài.`r`n"+
        "2. Giám thị: mở Timeline đa tín hiệu trong menu trái, chọn đúng lượt thi; cuộn xuống bảng lịch sử dưới nhóm cảnh báo.`r`n"+
        "3. Kiểm tra process/focus/window và chọn một đáp án. Nguồn process TEST tùy chọn là chrome.exe trong thư mục output (cmd vô hại, không phải trình duyệt).`r`n"+
        "4. Xem xong: thí sinh Thoát màn thi, đăng xuất; giám thị đăng xuất; đóng cả hai cửa sổ để dọn DB thử nghiệm.`r`n`r`nKhông nộp bài trước khi xem xong lịch sử. Tự dừng tối đa 60 phút; có thể đặt stop.request trong thư mục output để kết thúc sớm.`r`n"
    if($ReadUsbStorage){
        $text+="`r`nUSB THẬT ĐÃ BẬT TRONG APP THÍ SINH:`r`nĐọc thông báo USB ở trang chủ trước khi vào phòng thi. Giám thị chọn nguồn WINDOWS_USB_STORAGE (hoặc Tất cả nguồn).`r`nGiữ USB cắm để xem USB_STORAGE_PRESENT_AT_START. Rút USB, đợi 10 giây: USB_STORAGE_REMOVED. Cắm lại, đợi 10 giây: USB_STORAGE_CONNECTED.`r`nChỉ quan sát loại USB lưu trữ, không đọc file/serial. Các dòng là quan sát, không phải kết luận gian lận.`r`n"
    }
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
    WriteJson 'desktop-cleanup.json' @{appsClosedBeforeTeardown=$closedNormally;appsAlive=@($apps|Where-Object {-not $_.process.HasExited}).Count;serverAlive=($server -and -not $server.process.HasExited);ownedWorkloadsAlive=@($workloads|Where-Object {-not $_.HasExited}).Count;forcedShutdown=$script:forcedShutdown;usbChildCleanup=if($script:forcedShutdown){'NOT_VERIFIED_AFTER_FORCED_JVM_EXIT'}else{'NORMAL_OWNER_SHUTDOWN'};desktopAcceptance='PENDING_USER_CONFIRMATION';hardware='PENDING_USER_CONFIRMATION'}
}
