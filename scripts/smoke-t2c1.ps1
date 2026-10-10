# REAL Windows ProcessHandle/owned Edge + production PG/HTTP/WS/full/dashboard. Fault sets MOCK.
param([string]$JavaHome = $env:JAVA_HOME, [switch]$Gui, [string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$previousValues = @{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not (Test-Path '.env')) { throw 'Local .env is required.' }
    foreach ($line in Get-Content -Encoding UTF8 '.env') {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key=$Matches[1]; $previousValues[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
            [Environment]::SetEnvironmentVariable($key,$Matches[2].Trim().Trim('"').Trim("'"),'Process')
        }
    }
    $javaCommand=if($JavaHome) {Join-Path $JavaHome 'bin/java.exe'} else {'java'}
    $serverJar='server/target/server-0.1.0-SNAPSHOT.jar'
    if(-not (Test-Path $serverJar) -or -not (Test-Path 'client/target/client-0.1.0-SNAPSHOT-all.jar')) {throw 'Run mvn package first.'}
    $classes='server/target/t2c1-smoke-classes/vn/edu/toeic/server/monitoring'
    New-Item -ItemType Directory -Force $classes | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/FullSnapshotSmoke*.class' $classes -Force
    $runId='T2C1-'+[Guid]::NewGuid().ToString()
    $output=Join-Path (Get-Location) ('server/target/t2c1-runtime/'+$runId)
    $raw=Join-Path $output 'raw'
    New-Item -ItemType Directory -Force $raw | Out-Null
    $sourceManifest=@(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique |
        Where-Object { $_ -match '\.(java|ps1|py|properties|xml)$' } | ForEach-Object {
            [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}
        })
    $versionProcess=New-Object System.Diagnostics.Process
    $versionProcess.StartInfo.FileName=$javaCommand
    $versionProcess.StartInfo.Arguments='-version'
    $versionProcess.StartInfo.UseShellExecute=$false
    $versionProcess.StartInfo.CreateNoWindow=$true
    $versionProcess.StartInfo.RedirectStandardError=$true
    [void]$versionProcess.Start();$javaVersion=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit();$versionProcess.Dispose()
    $metadata=[ordered]@{
        runId=$runId;utc=[DateTime]::UtcNow.ToString('o');sourceHead=(git rev-parse HEAD).Trim()
        branch=(git branch --show-current).Trim();workingTree=@(git status --porcelain)
        java=$javaVersion;os=[Environment]::OSVersion.VersionString
        command='powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 [-Gui] -JavaHome <JDK21>'
        gui=[bool]$Gui;workload='REAL ProcessHandle/owned Edge; MOCK fault process sets';network='REAL localhost HTTP/WS/PostgreSQL'
        heartbeatMillis=200;pollMillis=200;maxProcesses=128;fullQueueCapacity=16;maxPendingFull=1;maxDedupIds=64
        stateStore='RAM, one server JVM';lan='NOT RUN';fullCandidateGui='NOT RUN';humanReview='NOT RUN'
        serverJarSha256=(Get-FileHash -LiteralPath $serverJar -Algorithm SHA256).Hash.ToLowerInvariant()
        clientJarSha256=(Get-FileHash -LiteralPath 'client/target/client-0.1.0-SNAPSHOT-all.jar' -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceManifest=$sourceManifest
    }
    $metadata | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $output 'run-metadata.json')
    $taskArgs=@();if($Gui){$taskArgs+='--gui'}
    & $javaCommand '-Duser.timezone=UTC' '-Dtoeic.dashboard.timezone=Asia/Ho_Chi_Minh' '-Dtoeic.realtime.heartbeatMillis=200' `
        '-Dtoeic.measurement.enabled=true' "-Dtoeic.measurement.runId=$runId" "-Dtoeic.measurement.directory=$raw" `
        '-Dtoeic.measurement.workloadLabel=MIXED' '-Dtoeic.measurement.faultLabel=MOCK' `
        '-Dloader.path=server/target/t2c1-smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar' `
        '-Dloader.main=vn.edu.toeic.server.monitoring.FullSnapshotSmoke' '-cp' $serverJar `
        'org.springframework.boot.loader.launch.PropertiesLauncher' @taskArgs
    if($LASTEXITCODE -ne 0){throw 'T2-C1 integration failed; credentials are never printed.'}
    & $Python 'scripts/summarize-monitoring.py' $raw '--output' (Join-Path $output 'summary')
    if($LASTEXITCODE -ne 0){throw 'T2-C1 measurement incomplete.'}
    Write-Output "T2-C1 artifacts: server/target/t2c1-runtime/$runId"
} finally {
    foreach($key in $previousValues.Keys){[Environment]::SetEnvironmentVariable($key,$previousValues[$key],'Process')}
    Pop-Location
}
