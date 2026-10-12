# REAL CLI / MOCK trace observations / REPLAY data faults and ACK enumeration SIMULATED. No network or DB.
param([string]$JavaHome=$env:JAVA_HOME,[switch]$RealScan)
$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $javaCommand=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
    $jar='client/target/client-0.1.0-SNAPSHOT-all.jar'
    if(-not(Test-Path -LiteralPath $jar)){throw 'Run mvn package first.'}
    $cli='vn.edu.toeic.client.monitoring.trace.TraceCli'
    $sourceSha=git rev-parse HEAD
    $runId=[Guid]::NewGuid().ToString();$output='client/target/t3c3-smoke/'+$runId
    New-Item -ItemType Directory -Path $output | Out-Null
    $fixtures='client/src/test/resources/monitoring-traces/t3-c3'
    foreach($name in @('campaign','repeat')) {
        & $javaCommand -cp $jar $cli campaign --fixtures $fixtures --output (Join-Path $output $name) --seed 20261012 --source-sha $sourceSha
        if($LASTEXITCODE -ne 0){throw "Campaign $name failed: $LASTEXITCODE"}
    }
    $first=Join-Path $output 'campaign';$repeat=Join-Path $output 'repeat'
    foreach($file in Get-ChildItem -LiteralPath $first -File) {
        if((Get-FileHash -LiteralPath $file.FullName).Hash -ne (Get-FileHash -LiteralPath (Join-Path $repeat $file.Name)).Hash){throw "Non-deterministic report: $($file.Name)"}
    }
    $summary=Get-Content -Raw -LiteralPath (Join-Path $first 'summary.json') -Encoding UTF8 | ConvertFrom-Json
    if($summary.status -ne 'PASS' -or $summary.cases.Count -ne 72 -or $summary.negativeChecks.Count -ne 4){throw 'Campaign summary incomplete.'}
    function Invoke-Replay([string]$Name,[string]$InputTrace,[string[]]$Options,[int]$ExpectedExit=0) {
        & $javaCommand -cp $jar $cli replay --input $InputTrace --output (Join-Path $output ($Name+'.json')) @Options
        if($LASTEXITCODE -ne $ExpectedExit){throw "Unexpected exit $Name : $LASTEXITCODE expected $ExpectedExit"}
        Write-Output "CLI $Name exit=$LASTEXITCODE expected=$ExpectedExit"
    }
    $low=Join-Path $fixtures 'low-change.jsonl'
    Invoke-Replay 'negative-full-cli' $low @('--mutate-full-at','1') 1
    Invoke-Replay 'negative-delta-cli' $low @('--mode','delta','--mutate-delta-at','2') 1
    Invoke-Replay 'negative-event-cli' $low @('--mode','delta','--mutate-event-at','1') 1
    Invoke-Replay 'invalid-config-refused' $low @('--scenario','clean','--drop','10') 2
    $corrupt=Join-Path $output 'corrupt-copy.jsonl'
    $raw=[IO.File]::ReadAllText((Join-Path (Get-Location) $low)).Replace('"pollMillis":1000','"pollMillis":1001')
    [IO.File]::WriteAllText((Join-Path (Get-Location) $corrupt),$raw,[Text.UTF8Encoding]::new($false))
    Invoke-Replay 'checksum-refused' $corrupt @() 2
    Invoke-Replay 'test-schema-refused' 'evidence/t2-c4/raw/observations.jsonl' @('--mode','delta') 2
    # Check CREATE_NEW/no overwrite using a positive report produced on the first invocation.
    Invoke-Replay 'no-overwrite' $low @()
    Invoke-Replay 'no-overwrite' $low @() 2
    if($RealScan) {
        $real=Join-Path $output 'real-processhandle.jsonl'
        & $javaCommand -cp $jar $cli record --output $real --scans 6 --poll-ms 100
        if($LASTEXITCODE -ne 0){throw 'REAL ProcessHandle trace incomplete.'}
        foreach($mode in @('full','delta')){Invoke-Replay ('real-'+$mode) $real @('--mode',$mode,'--seed','20261012','--scenario','seeded','--duplicate','45','--drop','15','--reorder','65','--reconnect-at','3')}
    }
    $manifest=@(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique |
        Where-Object { $_ -match '\.(java|ps1|py|properties|xml|jsonl|json)$' -and (Test-Path -LiteralPath $_) } |
        ForEach-Object {[ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}})
    $versionProcess=New-Object System.Diagnostics.Process
    $versionProcess.StartInfo.FileName=$javaCommand;$versionProcess.StartInfo.Arguments='-version'
    $versionProcess.StartInfo.UseShellExecute=$false;$versionProcess.StartInfo.CreateNoWindow=$true;$versionProcess.StartInfo.RedirectStandardError=$true
    $versionProcess.Start() | Out-Null;$javaVersion=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit();$versionProcess.Dispose()
    $metadata=[ordered]@{schemaVersion='t3c3-smoke-metadata-v1';runId=$runId;sourceSha=$sourceSha;branch=(git branch --show-current);
        workingTree=@(git status --short);command='powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c3.ps1 -JavaHome <JDK21> [-RealScan]';
        os=[Environment]::OSVersion.VersionString;machine='LOCAL_WINDOWS_REDACTED';jdk=$javaVersion;seed=20261012;realScan=[bool]$RealScan;
        inputProvenance='MOCK_HANDWRITTEN; optional REAL ProcessHandle';transport='REPLAY/SIMULATED acceptance faults, baseline encoder ACK only; no fault-network retry/ACK-loss/resync feedback';
        clientJarSha256=(Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant();sourceManifest=$manifest;
        repeat='all report bytes + summary identical';summarySha256=(Get-FileHash -LiteralPath (Join-Path $first 'summary.json')).Hash.ToLowerInvariant();
        testScope='No live desktop/network/auth/DB/presence/STALE/TTL/gate/E1/E2';dateUtc=[DateTime]::UtcNow.ToString('o')}
    [IO.File]::WriteAllText((Join-Path (Join-Path (Get-Location) $output) 'metadata.json'),($metadata | ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
    Write-Output "T3-C3 PASS output=$output cases=72 negatives=4 deterministic reports; intentional negative CLI exit1/config-checksum-TEST-nooverwrite exit2."
} finally {Pop-Location}
