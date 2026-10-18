param([Parameter(Mandatory)][string]$OutName,[string]$AppVersion='0.3.10')
$ErrorActionPreference='Stop'
# Derived from target/integration-final-20261017/control/pipeline.ps1 (RC 0.3.9); only paths and version differ.
$base='E:\Project\Toeic-Monitoring-Realtime\target\stabilize-20261017'
$root='E:\Project\Toeic-Monitoring-Realtime\target\integration-final-20261017\worktree';$out=Join-Path $base $OutName
if(Test-Path $out){throw "Output $out exists; use a new OutName"}
New-Item -ItemType Directory -Force $out | Out-Null
Set-Location $root
$env:JAVA_HOME='E:\Java\Jav\jdk-21';$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$env:PATH="$env:JAVA_HOME\bin;E:\Git\cmd;E:\Java\Jav\apache-maven-3.9.11\bin;"+$env:PATH
$env:TEMP=Join-Path $out 'tmp';$env:TMP=$env:TEMP;New-Item -ItemType Directory -Force $env:TEMP | Out-Null
$env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$env:TEMP -Duser.timezone=UTC"
$env:TOEIC_INSTALLED_CLIENT=$null;$env:TOEIC_PACKAGED_CLIENT=$null;$env:TOEIC_PG_INTEGRATION='true';$env:TOEIC_PROCTOR_GUI='true'
$source=(& git rev-parse HEAD).Trim();$allRuns=@();$pg='E:\PostgreSQL\18\bin';$pgStarted=$false
function Native($file,$arguments,$label){
 $start=[DateTime]::UtcNow.ToString('o')
 $p=[Diagnostics.Process]::new()
 $p.StartInfo.FileName=$file;$p.StartInfo.Arguments=($arguments -join ' ')
 $p.StartInfo.WorkingDirectory=$root;$p.StartInfo.UseShellExecute=$false;$p.StartInfo.CreateNoWindow=$true
 $capture=($label -ne 'pg-start')
 $p.StartInfo.RedirectStandardOutput=$capture;$p.StartInfo.RedirectStandardError=$capture
 [void]$p.Start();if($capture){$stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()}
 $p.WaitForExit()
 if($capture){[IO.File]::WriteAllText("$out\$label.out.log",$stdout.Result,[Text.UTF8Encoding]::new($false))
 [IO.File]::WriteAllText("$out\$label.err.log",$stderr.Result,[Text.UTF8Encoding]::new($false))}
 $r=@{label=$label;source=$source;startUtc=$start;endUtc=[DateTime]::UtcNow.ToString('o');exitCode=$p.ExitCode}
 $r|ConvertTo-Json|Set-Content -Encoding UTF8 "$out\$label-run.json"
 $script:allRuns+=,$r;$script:allRuns|ConvertTo-Json -Depth 5|Set-Content -Encoding UTF8 "$out\RUNS.json"
 Write-Output "$label exit=$($p.ExitCode)"
 return $p.ExitCode
}
function ArchiveReports($label){
 $dest=Join-Path $out "$label-reports";New-Item -ItemType Directory -Force $dest|Out-Null
 foreach($module in @('protocol','client','server','monitoring-spike')){
  $reports=Join-Path $root "$module\target\surefire-reports"
  if(Test-Path $reports){Copy-Item $reports (Join-Path $dest $module) -Recurse}
 }
}
function Maven($goals,$label){
 $command='"E:\Java\Jav\apache-maven-3.9.11\bin\mvn.cmd" -B -ntp -o "-Dmaven.repo.local='+$env:USERPROFILE+'\.m2\repository" '+$goals
 $result=@(Native "$env:SystemRoot\System32\cmd.exe" @('/d','/s','/c',('"'+$command+'"')) $label)
 ArchiveReports $label
 if($result[-1] -ne 0){throw "$label failed; inspect preserved logs"}
}
try {
 $probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$probe.Start();$port=$probe.LocalEndpoint.Port;$probe.Stop()
 $result=@(Native "$pg\initdb.exe" @('-D',"$out\pgdata",'-U','integration_test','-A','trust','--encoding=UTF8','--locale=C') 'initdb')
 if($result[-1] -ne 0){throw 'initdb failed'}
 Add-Content "$out\pgdata\postgresql.conf" "`nlisten_addresses='127.0.0.1'`nport=$port"
 $result=@(Native "$pg\pg_ctl.exe" @('-D',"$out\pgdata",'-l',"$out\postgres.log",'-w','start') 'pg-start')
 if($result[-1] -ne 0){throw 'PG start failed'};$pgStarted=$true
 $env:DB_HOST='127.0.0.1';$env:DB_PORT="$port";$env:DB_NAME='postgres';$env:DB_USER='integration_test';$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N');$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N')
 @{source=$source;java='Oracle 21.0.8';maven='3.9.11';postgres='18.1';host='loopback owned TEST';port=$port;delta='FULL default unchanged';labels='REAL PG/JavaFX/HTTP/WS, MOCK fixtures, SIMULATED faults; LAN/E2 NOT RUN'}|ConvertTo-Json|Set-Content -Encoding UTF8 "$out\environment.json"
 Maven 'clean test' 'clean'
 Maven 'package' 'package'
 foreach($mode in @('flow','concurrency','gui','full','events')){
  $main=@{flow='vn.edu.toeic.client.exam.ExamFlowPostgresSmoke';gui='vn.edu.toeic.client.integration.ReadingListeningPostgresGuiSmoke';concurrency='vn.edu.toeic.server.exam.ExamConcurrencyPostgresSmoke';full='vn.edu.toeic.server.monitoring.FullSnapshotSmoke';events='vn.edu.toeic.server.monitoring.LateEventsAndGapsSmoke'}[$mode]
  $pkg=($main -replace '\.[^.]+$','') -replace '\.','/';$simple=($main -split '\.')[-1]
  $classes=Join-Path $out "smoke-classes/$pkg";New-Item -ItemType Directory -Force $classes|Out-Null
  if($mode -in @('full','events','concurrency')){Get-ChildItem "server/target/test-classes/$pkg" -Filter "$simple*.class"|Copy-Item -Destination $classes}
  # Stabilization 17/10: C1/C2 also need the shared cleanup reporter (run final-4fbc538 hung without it).
  if($mode -in @('full','events')){Get-ChildItem "server/target/test-classes/$pkg" -Filter "SmokeCleanup*.class"|Copy-Item -Destination $classes}
  $pgJar=Get-ChildItem "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql" -Recurse -Filter 'postgresql-*.jar'|Select-Object -Last 1 -ExpandProperty FullName
  $cp="client/target/test-classes;client/target/client-0.1.0-SNAPSHOT-all.jar;$pgJar"
  $argsList=@('-Duser.timezone=UTC','-Dtoeic.realtime.heartbeatMillis=200',"-Dintegration.output=$out/gui",'-cp',$cp,$main)
  if($mode -in @('full','events')){
   $runId='INTEGRATION-'+$mode+'-'+[Guid]::NewGuid().ToString('N')
   $argsList=@('-Duser.timezone=UTC','-Dtoeic.realtime.heartbeatMillis=200','-Dtoeic.measurement.enabled=true',"-Dtoeic.measurement.runId=$runId","-Dtoeic.measurement.directory=$out/$mode-raw",'-Dtoeic.measurement.workloadLabel=MIXED','-Dtoeic.measurement.faultLabel=MOCK',"-Dloader.path=$out/smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar","-Dloader.main=$main",'-cp','server/target/server-0.1.0-SNAPSHOT.jar','org.springframework.boot.loader.launch.PropertiesLauncher','--gui')
  }
  if($mode -eq 'concurrency'){$argsList=@("-Dloader.path=$out/smoke-classes","-Dloader.main=$main",'-cp','server/target/server-0.1.0-SNAPSHOT.jar','org.springframework.boot.loader.launch.PropertiesLauncher')}
  $result=@(Native "$env:JAVA_HOME\bin\java.exe" $argsList $mode)
  if($result[-1] -ne 0){Write-Output "$mode FAIL retained; continue independent checks"}
 }
 $tools='E:\Project\toeic-pr39-qa-20261016\jpackage-out\pr39-qa-20261016\tools'
 if((Test-Path "$tools\wix\light.exe") -and (Test-Path "$tools\sdk\bin\10.0.26100.0\x64\mt.exe")){
  $start=[DateTime]::UtcNow.ToString('o')
  try {
   $msi=& ./scripts/package-windows.ps1 -JavaHome $env:JAVA_HOME -WixHome "$tools\wix" -ManifestTool "$tools\sdk\bin\10.0.26100.0\x64\mt.exe" -AppVersion $AppVersion -Offline 6> "$out\msi-build.host.log"
   [IO.File]::WriteAllText("$out\msi-path.txt",[string]$msi,[Text.UTF8Encoding]::new($false))
   @{source=$source;version=$AppVersion;startUtc=$start;endUtc=[DateTime]::UtcNow.ToString('o');status='BUILT';installer=[string]$msi}|ConvertTo-Json|Set-Content -Encoding UTF8 "$out\msi-build.json"
   # Own temporary TEST product only; script refuses any existing user installation.
   $image=Split-Path ((Get-ChildItem 'jpackage-out' -Filter 'build-info.json' -Recurse | Where-Object {$_.FullName -match '\\app\\build-info.json$'}|Sort-Object LastWriteTime|Select-Object -Last 1).FullName)
   $env:TOEIC_PACKAGED_CLIENT=Split-Path $image
   & ./scripts/smoke-windows-installer.ps1 -Installer ([string]$msi) -JavaHome $env:JAVA_HOME -FullSuite *> "$out\installed.out.log"
   ArchiveReports 'installed'
   @{source=$source;status='PASS';endUtc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -Encoding UTF8 "$out\installed-run.json"
  } catch {
   ArchiveReports 'installed-attempt'
   @{source=$source;status='FAIL_OR_BLOCKED';error=$_.Exception.Message;endUtc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -Encoding UTF8 "$out\msi-error.json"
   Write-Output 'MSI stage did not complete; inspect sanitized metadata and preserved logs'
  }
 } else {Write-Output 'MSI_REBUILD_REQUIRED toolchain unavailable'}
} finally {
 if($pgStarted){
  $env:PGPASSWORD=$env:DB_PASSWORD
  $query='SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE ''pg_%'' AND nspname NOT IN (''public'',''information_schema'');'
  Native "$pg\psql.exe" @('-h','127.0.0.1','-p',"$port",'-U','integration_test','-d','postgres','-At','-c',('"'+$query+'"')) 'schema-count'|Out-Host
  Native "$pg\pg_ctl.exe" @('-D',"$out\pgdata",'-w','-m','fast','stop') 'pg-stop'|Out-Host
 }
 foreach($key in @('DB_PASSWORD','TOEIC_SEED_PASSWORD','PGPASSWORD')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
 Write-Output 'Pipeline ended; owned resources audited, no shared DB operations'
}
