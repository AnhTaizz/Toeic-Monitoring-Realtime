# REAL CLI; MOCK handwritten data / optional REAL ProcessHandle trace; ACK/network/epoch SIMULATED.
param([string]$JavaHome = $env:JAVA_HOME, [switch]$RealScan)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    $jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Run mvn package first.' }
    $cli = 'vn.edu.toeic.client.monitoring.trace.TraceCli'
    $runId = [Guid]::NewGuid().ToString()
    $output = 'client/target/t3c1-smoke/' + $runId
    New-Item -ItemType Directory -Path $output | Out-Null
    $fixture = 'client/src/test/resources/monitoring-traces/delta-handwritten-v1.jsonl'
    function Invoke-Replay([string]$Name, [string]$InputTrace, [string[]]$Options, [int]$ExpectedExit = 0) {
        $target = Join-Path $output ($Name + '.json')
        & $javaCommand -cp $jar $cli replay --input $InputTrace --output $target @Options
        if ($LASTEXITCODE -ne $ExpectedExit) { throw "Unexpected exit for $Name : $LASTEXITCODE expected $ExpectedExit" }
        Write-Output "CLI $Name exit=$LASTEXITCODE expected=$ExpectedExit"
    }
    function Compare-Modes([string]$Prefix) {
        $full = Get-Content -Raw -LiteralPath (Join-Path $output ($Prefix + '-full.json')) | ConvertFrom-Json
        $delta = Get-Content -Raw -LiteralPath (Join-Path $output ($Prefix + '-delta.json')) | ConvertFrom-Json
        if ($full.status -ne 'PASS' -or $delta.status -ne 'PASS') { throw 'Positive replay must PASS.' }
        if (($full.businessEvents | ConvertTo-Json -Depth 15 -Compress) -ne ($delta.businessEvents | ConvertTo-Json -Depth 15 -Compress) -or
            ($full.deliveredEvents | ConvertTo-Json -Depth 15 -Compress) -ne ($delta.deliveredEvents | ConvertTo-Json -Depth 15 -Compress)) { throw 'Event IDs/content differ across modes.' }
        if ($full.observationChecks.Count -ne $delta.observationChecks.Count) { throw 'Observation coverage differs.' }
        for ($index = 0; $index -lt $full.observationChecks.Count; $index++) {
            if (($full.observationChecks[$index].actualState.processes | ConvertTo-Json -Depth 10 -Compress) -ne
                ($delta.observationChecks[$index].actualState.processes | ConvertTo-Json -Depth 10 -Compress)) { throw 'Canonical process/metadata mismatch across modes.' }
        }
        Write-Output "COMPARE $Prefix canonical processes+metadata and complete event IDs/content PASS; sequence may differ."
    }
    Invoke-Replay 'fixture-full' $fixture @()
    Invoke-Replay 'fixture-delta' $fixture @('--mode','delta')
    Compare-Modes 'fixture'
    Invoke-Replay 'reconnect-full' $fixture @('--duplicate','100','--reconnect-at','3')
    Invoke-Replay 'reconnect-delta' $fixture @('--mode','delta','--duplicate','100','--reconnect-at','3')
    Compare-Modes 'reconnect'
    Invoke-Replay 'negative-delta' $fixture @('--mode','delta','--mutate-delta-at','2') 1
    Invoke-Replay 'negative-full' $fixture @('--mutate-full-at','2') 1
    # T3-C3 now checks acceptance faults; baseline ACK enumeration is still SIMULATED.
    Invoke-Replay 'delta-acceptance-faults' $fixture @('--mode','delta','--drop','10')
    $hand = 'client/src/test/resources/monitoring-traces/handwritten-v1.jsonl'
    Invoke-Replay 'legacy-full-faults' $hand @('--seed','1234','--duplicate','20','--drop','10','--reorder','35','--reconnect-at','3')
    $testTrace = 'evidence/t2-c4/raw/observations.jsonl'
    if (-not (Test-Path -LiteralPath $testTrace)) { throw 'TEST harness trace required for schema refusal.' }
    Invoke-Replay 'test-schema-refused' $testTrace @('--mode','delta') 2
    if ($RealScan) {
        $real = Join-Path $output 'real-processhandle.jsonl'
        & $javaCommand -cp $jar $cli record --output $real --scans 6 --poll-ms 100
        if ($LASTEXITCODE -ne 0) { throw 'REAL trace not COMPLETE.' }
        Invoke-Replay 'real-full' $real @()
        Invoke-Replay 'real-delta' $real @('--mode','delta')
        Compare-Modes 'real'
    }
    $sourceManifest = @(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique |
        Where-Object { $_ -match '\.(java|ps1|py|properties|xml|jsonl)$' } | ForEach-Object {
            [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}
        })
    $versionProcess = New-Object System.Diagnostics.Process
    $versionProcess.StartInfo.FileName=$javaCommand
    $versionProcess.StartInfo.Arguments='-version'
    $versionProcess.StartInfo.UseShellExecute=$false
    $versionProcess.StartInfo.CreateNoWindow=$true
    $versionProcess.StartInfo.RedirectStandardError=$true
    [void]$versionProcess.Start();$javaVersion=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit();$versionProcess.Dispose()
    $metadata = [ordered]@{
        runId=$runId;utc=[DateTime]::UtcNow.ToString('o');sourceHead=(git rev-parse HEAD).Trim()
        branch=(git branch --show-current).Trim();workingTree=@(git status --porcelain)
        os=[Environment]::OSVersion.VersionString;machine='local-windows-anonymous';realScan=[bool]$RealScan
        workload='MOCK fixture; optional REAL ProcessHandle trace';network='SIMULATED';encoderAck='SIMULATED'
        productionDelta='DISABLED';gate='NOT RUN';humanReview='NOT RUN';reconciliationMillis=30000
        command='powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c1.ps1 -JavaHome <JDK21> [-RealScan]'
        javaVersion=$javaVersion
        clientJarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant();sourceManifest=$sourceManifest
    }
    $metadata | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $output 'metadata.json')
    Write-Output "T3-C1 CLI PASS artifacts=$output; no production delta, MT gate or E1/E2 claim."
} finally { Pop-Location }
