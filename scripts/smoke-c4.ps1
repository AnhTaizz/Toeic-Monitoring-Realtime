# REAL isolated PG/HTTP/WS/B2 candidate+proctor; MOCK event source; SIMULATED delivery ACK suppression.
param([string]$JavaHome = $env:JAVA_HOME, [string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousValues = @{}
Push-Location $projectRoot
try {
    if (-not (Test-Path '.env')) { throw 'Local .env is required; its contents are never copied to evidence.' }
    foreach ($line in Get-Content -Encoding utf8 '.env') {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key = $Matches[1]; $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    $serverJar = 'server/target/server-0.1.0-SNAPSHOT.jar'
    $clientJar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
    if (-not (Test-Path $serverJar) -or -not (Test-Path $clientJar)) { throw 'Run mvn package first.' }
    $sourceSha = (& git rev-parse HEAD).Trim()
    if ($sourceSha -notmatch '^[a-f0-9]{40}$') { throw 'Source SHA unavailable.' }
    $sourceDirty = [bool](& git status --porcelain --untracked-files=no)
    $runId = 'C4-' + [Guid]::NewGuid().ToString()
    $output = Join-Path $projectRoot ('server/target/c4-runtime/' + $runId)
    $raw = Join-Path $output 'raw'
    New-Item -ItemType Directory -Force $raw | Out-Null
    $smokeClasses = 'server/target/c4-smoke-classes/vn/edu/toeic/server/monitoring'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/MonitoringMeasurementSmoke*.class' $smokeClasses -Force
    $sourceFiles = @(& rg --files protocol/src client/src server/src scripts | Sort-Object)
    $manifest = @($sourceFiles | Where-Object { $_ -notmatch '(__pycache__|\.pyc$)' } | ForEach-Object {
        [ordered]@{ file = $_.Replace('\','/'); sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() }
    })
    $metadata = [ordered]@{
        schemaVersion = 'monitoring-measurement-v1'; runId = $runId; sourceSha = $sourceSha; sourceDirty = $sourceDirty
        startedAt = [DateTime]::UtcNow.ToString('o'); os = [Environment]::OSVersion.VersionString
        workloadLabel = 'MIXED'; faultLabel = 'SIMULATED'; transport = 'REAL PG/HTTP/WS/B2 candidate/proctor/dashboard model; one JVM with separate recorder clock domains'
        processSource = 'REAL Windows ProcessHandle collector scan; independent MOCK snapshots cause measured event/overflow'
        fault = 'Suppress first event ACK at C3 delivery observer AFTER B2 received/accepted it; no actual network ACK loss'
        pollMillis = 200; heartbeatMillis = 200; presenceTimeoutMillis = 1200; presenceScanMillis = 25
        delivery = @{ eventCapacity = 4; overflowCapacity = 1; inFlight = 1; ackTimeoutMillis = 250; maxAttempts = 5; initialBackoffMillis = 75; maxBackoffMillis = 300; pumpMillis = 15; maxObservedProcesses = 10000 }
        policyVersion = 'process-policy-v1'; recorder = @{ queueCapacity = 1024; flushMillis = 2000; overflow = 'DROP_NEW' }
        command = 'mvn package; powershell -File scripts/smoke-c4.ps1; python scripts/summarize-monitoring.py <runtime>/raw --output <runtime>/summary'
        build = @{ serverJarSha256 = (Get-FileHash $serverJar).Hash.ToLowerInvariant(); clientJarSha256 = (Get-FileHash $clientJar).Hash.ToLowerInvariant(); serverBuiltAtUtc = (Get-Item $serverJar).LastWriteTimeUtc.ToString('o') }
        sourceFiles = $manifest
    }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText((Join-Path $output 'run-metadata.json'), (($metadata | ConvertTo-Json -Depth 8) -replace "`r`n","`n") + "`n", $utf8)
    & $javaCommand '-Duser.timezone=UTC' '-Dtoeic.measurement.enabled=true' "-Dtoeic.measurement.runId=$runId" `
        "-Dtoeic.measurement.directory=$raw" "-Dtoeic.measurement.sourceSha=$sourceSha" `
        '-Dtoeic.measurement.workloadLabel=MIXED' '-Dtoeic.measurement.faultLabel=SIMULATED' `
        '-Dloader.path=server/target/c4-smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar' `
        '-Dloader.main=vn.edu.toeic.server.monitoring.MonitoringMeasurementSmoke' '-cp' $serverJar `
        'org.springframework.boot.loader.launch.PropertiesLauncher'
    if ($LASTEXITCODE -ne 0) { throw 'C4 integration failed; raw artifacts kept in ignored target for diagnosis.' }
    & $Python 'scripts/summarize-monitoring.py' $raw '--output' (Join-Path $output 'summary')
    if ($LASTEXITCODE -ne 0) { throw 'C4 raw log incomplete; summary does not repair counters.' }
    Write-Output ('C4 artifacts: server/target/c4-runtime/' + $runId)
} finally {
    foreach ($key in $previousValues.Keys) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
    Pop-Location
}
