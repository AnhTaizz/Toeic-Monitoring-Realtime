# One Windows host, REAL production Application UIs/ProcessHandle/Edge/Spring/PG/HTTP/WS.
# TEST ACK observer/base/source/DB faults SIMULATED; no second-machine LAN/human gate/E1/E2 claim.
param([string]$JavaHome=$env:JAVA_HOME,[string]$Python='python')
$ErrorActionPreference='Stop';$previous=@{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key=$Matches[1];$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$Matches[2].Trim().Trim('"').Trim("'"),'Process')
        }
    }
    $java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
    $versionProcess=New-Object System.Diagnostics.Process
    $versionProcess.StartInfo.FileName=$java;$versionProcess.StartInfo.Arguments='-version'
    $versionProcess.StartInfo.UseShellExecute=$false;$versionProcess.StartInfo.CreateNoWindow=$true;$versionProcess.StartInfo.RedirectStandardError=$true
    [void]$versionProcess.Start();$javaVersion=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit();$versionProcess.Dispose()
    $classes='server/target/t3c4-smoke-classes'
    foreach($path in @('vn/edu/toeic/server/monitoring','vn/edu/toeic/client')){New-Item -ItemType Directory -Force (Join-Path $classes $path) | Out-Null}
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/DeltaLiveGateSmoke*.class' (Join-Path $classes 'vn/edu/toeic/server/monitoring') -Force
    Copy-Item 'server/target/test-classes/vn/edu/toeic/client/LiveCandidateApp*.class' (Join-Path $classes 'vn/edu/toeic/client') -Force
    $runId='T3C4-'+[Guid]::NewGuid().ToString();$output=Join-Path (Get-Location) ('server/target/t3c4-live/'+$runId);$raw=Join-Path $output 'raw'
    New-Item -ItemType Directory -Force $raw | Out-Null
    $sourceSha=git rev-parse HEAD
    $manifest=@(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique |
        Where-Object {$_ -match '\.(java|ps1|py|properties|xml|json|jsonl)$' -and (Test-Path -LiteralPath $_)} |
        ForEach-Object {[ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant()}})
    $metadata=[ordered]@{schemaVersion='t3c4-local-metadata-v1';runId=$runId;sourceSha=$sourceSha;branch=(git branch --show-current);workingTree=@(git status --porcelain);
        command='powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c4.ps1 -JavaHome <JDK21>';java=$javaVersion;os=[Environment]::OSVersion.VersionString;machine='LOCAL_WINDOWS_ANONYMOUS';
        topology='One Windows host; existing PG + own TEST Spring + two candidate child JVMs + actual proctor Application; LOOPBACK not LAN';
        source='REAL ProcessHandle/owned headless Edge; default factory normal; TEST instrumented factory for explicit faults only';fault='SIMULATED ACK observer suppression/base-seq/source wait-exception/TEST DB triggers';
        candidateModes='DELTA explicit opt-in with correlated capability; other candidate FULL opt-in off; disabled server fallback FULL';
        pollMillis=1000;refreshMillis=2000;fullMillis=30000;stateStaleMillis=6000;testTtlMillis=12000;productionTtlMillis=300000;heartbeatMillis=250;ackMillis=1500;maxAttempts=5;
        eventBackoff='normal peer production defaults; instrumented peer TEST100/400ms to exercise exhaustion';
        seed='NOT_USED; deterministic owned fault toggles';humanBReview='NOT RUN';lanSecondMachine='NOT RUN / environment not provided';gate='PREPARATION ONLY;20Oct decision not made';e1e2='NOT RUN';
        clientJarSha256=(Get-FileHash 'client/target/client-0.1.0-SNAPSHOT-all.jar').Hash.ToLowerInvariant();serverJarSha256=(Get-FileHash 'server/target/server-0.1.0-SNAPSHOT.jar').Hash.ToLowerInvariant();sourceManifest=$manifest}
    [IO.File]::WriteAllText((Join-Path $output 'metadata.json'),($metadata | ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
    $gateJavaArgs=@('-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Duser.timezone=UTC',"-Duser.home=$output",'-Dprism.order=sw','-Dtoeic.realtime.delta-experimental=true','-Dtoeic.realtime.heartbeatMillis=250',
        '-Dtoeic.measurement.enabled=true',"-Dtoeic.measurement.runId=$runId","-Dtoeic.measurement.sourceSha=$sourceSha","-Dtoeic.measurement.directory=$raw",
        '-Dtoeic.measurement.workloadLabel=REAL','-Dtoeic.measurement.faultLabel=SIMULATED',"-Dtoeic.test.gate.output=$output",
        "-Dtoeic.test.gate.clientJar=$((Resolve-Path 'client/target/client-0.1.0-SNAPSHOT-all.jar').Path)","-Dtoeic.test.gate.classes=$((Resolve-Path $classes).Path)",
        "-Dloader.path=$classes,client/target/client-0.1.0-SNAPSHOT-all.jar",'-Dloader.main=vn.edu.toeic.server.monitoring.DeltaLiveGateSmoke',
        '-cp','server/target/server-0.1.0-SNAPSHOT.jar','org.springframework.boot.loader.launch.PropertiesLauncher')
    & $java @gateJavaArgs
    $smokeExit=$LASTEXITCODE
    Write-Output "T3-C4 LOCAL artifacts: $output exit=$smokeExit; human gate/LAN not confirmed."
    # Owned hard-kill may leave raw without END; retain it and report incompleteness, never edit it to PASS.
    $merged=Join-Path $output 'raw-merged';New-Item -ItemType Directory $merged | Out-Null
    Get-ChildItem -LiteralPath $output -Recurse -File -Filter '*.jsonl' | Where-Object {$_.Directory.Name -eq 'raw'} | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $merged $_.Name)}
    & $Python scripts/summarize-monitoring.py $merged --output (Join-Path $output 'measurement-summary')
    Write-Output "Measurement summary exit=$LASTEXITCODE; hard-killed endpoint is expected INCOMPLETE, retained raw."
    if($smokeExit -ne 0){throw 'C4 local smoke failed; keep artifacts. Credentials/cause omitted.'}
} finally {foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')};Pop-Location}
