# REAL Spring/PG/HTTP/WS; experimental delta explicit, MOCK process readings, SIMULATED bad-order injection.
param([string]$JavaHome = $env:JAVA_HOME, [string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$previousValues=@{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD|TOEIC_SEED_DEMO_ACCOUNTS)\s*=(.*)$') {
            $key=$Matches[1];$previousValues[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
            [Environment]::SetEnvironmentVariable($key,$Matches[2].Trim().Trim('"').Trim("'"),'Process')
        }
    }
    $javaCommand=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
    $classes='server/target/t3c2-smoke-classes/vn/edu/toeic/server/monitoring'
    New-Item -ItemType Directory -Force $classes | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/DeltaStateSmoke*.class' $classes -Force
    $runId='T3C2-'+[Guid]::NewGuid().ToString();$output='server/target/t3c2-runtime/'+$runId;$raw=Join-Path $output 'raw'
    New-Item -ItemType Directory -Force $raw | Out-Null
    $manifest=@(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique | Where-Object { $_ -match '\.(java|ps1|py|properties|xml|jsonl)$' -and (Test-Path -LiteralPath $_) } | ForEach-Object { [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()} })
    $sourceSha=(git rev-parse HEAD).Trim()
    $versionProcess=New-Object System.Diagnostics.Process
    $versionProcess.StartInfo.FileName=$javaCommand;$versionProcess.StartInfo.Arguments='-version'
    $versionProcess.StartInfo.UseShellExecute=$false;$versionProcess.StartInfo.CreateNoWindow=$true;$versionProcess.StartInfo.RedirectStandardError=$true
    [void]$versionProcess.Start();$javaVersion=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit();$versionProcess.Dispose()
    [ordered]@{java=$javaVersion;seed='NOT_USED; deterministic future-base injection';command='powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c2.ps1 -JavaHome <JDK21>';runId=$runId;utc=[DateTime]::UtcNow.ToString('o');sourceHead=$sourceSha;workingTree=@(git status --porcelain);machine='local-windows-anonymous';os=[Environment]::OSVersion.VersionString;sourceManifest=$manifest
        processReadings='MOCK';transport='REAL Spring/PostgreSQL/HTTP/WS/production client adapter';fault='SIMULATED future base injection';gate='NOT RUN';humanReview='NOT RUN';pollMillis=100;heartbeatMillis=100;refreshMillis=200;reconciliationMillis=30000;stateStaleMillis=1200;stateTtlMillis=4000
        clientJarSha256=(Get-FileHash 'client/target/client-0.1.0-SNAPSHOT-all.jar' -Algorithm SHA256).Hash.ToLowerInvariant();serverJarSha256=(Get-FileHash 'server/target/server-0.1.0-SNAPSHOT.jar' -Algorithm SHA256).Hash.ToLowerInvariant()
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $output 'metadata.json')
    & $javaCommand '-Duser.timezone=UTC' '-Dtoeic.realtime.heartbeatMillis=100' '-Dtoeic.realtime.delta-experimental=true' '-Dtoeic.delta.refreshMillis=200' `
        '-Dtoeic.measurement.enabled=true' "-Dtoeic.measurement.runId=$runId" "-Dtoeic.measurement.sourceSha=$sourceSha" "-Dtoeic.measurement.directory=$((Join-Path (Get-Location) $raw))" `
        '-Dtoeic.measurement.workloadLabel=MOCK' '-Dtoeic.measurement.faultLabel=SIMULATED' `
        '-Dloader.path=server/target/t3c2-smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar' `
        '-Dloader.main=vn.edu.toeic.server.monitoring.DeltaStateSmoke' -cp server/target/server-0.1.0-SNAPSHOT.jar 'org.springframework.boot.loader.launch.PropertiesLauncher'
    if($LASTEXITCODE -ne 0){throw 'T3-C2 smoke failed; credentials/cause omitted.'}
    & $Python scripts/summarize-monitoring.py $raw --output (Join-Path $output 'summary')
    if($LASTEXITCODE -ne 0){throw 'Measurement incomplete.'}
    $summary=Get-Content -Raw -LiteralPath (Join-Path $output 'summary.json') | ConvertFrom-Json
    $types=@($summary.rows | Select-Object -ExpandProperty messageType -Unique)
    if($types -notcontains 'MONITORING_DELTA' -or $types -notcontains 'MONITORING_RESYNC'){throw 'New message types missing from measurement.'}
    Write-Output 'PASS measurement DELTA/RESYNC classified; no added delivery counters or double-counted ACK bytes.'
    Write-Output "T3-C2 artifacts: $output"
} finally {foreach($key in $previousValues.Keys){[Environment]::SetEnvironmentVariable($key,$previousValues[$key],'Process')};Pop-Location}
