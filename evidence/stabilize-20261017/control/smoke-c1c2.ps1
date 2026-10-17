param([Parameter(Mandatory)][ValidateSet('workspace','windows')][string]$TempKind,
      [Parameter(Mandatory)][string]$Label,
      [string[]]$Modes=@('full','events'))
# C1 FULL / C2 late-gap smokes on the owned dev PostgreSQL cluster. Same command lines as the RC pipeline.
$ErrorActionPreference='Stop'
$base='E:\Project\Toeic-Monitoring-Realtime\target\stabilize-20261017'
$root='E:\Project\Toeic-Monitoring-Realtime\target\integration-final-20261017\worktree'
$out=Join-Path $base "runs\$Label"; New-Item -ItemType Directory -Force $out | Out-Null
Set-Location $root
$env:JAVA_HOME='E:\Java\Jav\jdk-21'
if($TempKind -eq 'workspace'){ $tmp=Join-Path $out 'tmp' } else { $tmp=Join-Path ([Environment]::GetEnvironmentVariable('TEMP','User')) "toeic-stabilize-$Label" }
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:TEMP=$tmp;$env:TMP=$tmp;$env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$tmp -Duser.timezone=UTC"
$env:DB_HOST='127.0.0.1';$env:DB_PORT=(Get-Content (Join-Path $base 'pgdev.port')).Trim();$env:DB_NAME='postgres';$env:DB_USER='integration_test';$env:DB_PASSWORD='unused-trust-auth'
$source=(& git rev-parse HEAD).Trim(); $dirty=(& git status --porcelain).Count
$results=@()
foreach($mode in $Modes){
  $main=@{full='vn.edu.toeic.server.monitoring.FullSnapshotSmoke';events='vn.edu.toeic.server.monitoring.LateEventsAndGapsSmoke'}[$mode]
  $pkg=($main -replace '\.[^.]+$','') -replace '\.','/';$simple=($main -split '\.')[-1]
  $classes=Join-Path $out "smoke-classes/$pkg";New-Item -ItemType Directory -Force $classes|Out-Null
  Get-ChildItem "server/target/test-classes/$pkg" -Filter "$simple*.class"|Copy-Item -Destination $classes
  Get-ChildItem "server/target/test-classes/$pkg" -Filter "SmokeCleanup*.class"|Copy-Item -Destination $classes
  $runId='STABILIZE-'+$mode+'-'+[Guid]::NewGuid().ToString('N')
  $args=@('-Duser.timezone=UTC','-Dtoeic.realtime.heartbeatMillis=200','-Dtoeic.measurement.enabled=true',"-Dtoeic.measurement.runId=$runId","-Dtoeic.measurement.directory=$out/$mode-raw",'-Dtoeic.measurement.workloadLabel=MIXED','-Dtoeic.measurement.faultLabel=MOCK',"-Dloader.path=$out/smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar","-Dloader.main=$main",'-cp','server/target/server-0.1.0-SNAPSHOT.jar','org.springframework.boot.loader.launch.PropertiesLauncher','--gui')
  $start=[DateTime]::UtcNow.ToString('o')
  $p=Start-Process "$env:JAVA_HOME\bin\java.exe" -ArgumentList $args -NoNewWindow -PassThru -Wait -RedirectStandardOutput "$out\$mode.out.log" -RedirectStandardError "$out\$mode.err.log"
  $results+=@{mode=$mode;exitCode=$p.ExitCode;startUtc=$start;endUtc=[DateTime]::UtcNow.ToString('o')}
  "$mode exit=$($p.ExitCode)"
}
$leftEdge=@(Get-ChildItem $tmp -Directory -Filter 'toeic-t2c1-edge-*' -ErrorAction SilentlyContinue).Count
@{source=$source;dirtyEntries=$dirty;tempKind=$TempKind;tempIsWorkspace=($TempKind -eq 'workspace');results=$results;leftoverEdgeProfiles=$leftEdge;labels='REAL PG/Spring/HTTP/WS/Edge/JavaFX; process sets MOCK; faults SIMULATED; LAN NOT RUN'}|ConvertTo-Json -Depth 4|Set-Content -Encoding UTF8 "$out\run.json"
"leftover edge profiles=$leftEdge"
if($TempKind -eq 'windows' -and (Test-Path $tmp)){ Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue }
