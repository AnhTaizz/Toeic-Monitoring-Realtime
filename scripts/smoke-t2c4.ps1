# REAL Java children/parent nanoTime/ProcessHandle, isolated TEST policy. No DB/network/E1/E2.
param([string]$JavaHome=$env:JAVA_HOME,[int]$PollMillis=500,[long]$Seed=1234,[int]$PerDuration=10,[int]$MaxConcurrent=2)
$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $javaCommand=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
    $jar='client/target/client-0.1.0-SNAPSHOT-all.jar'
    if(-not (Test-Path $jar)){throw 'Run mvn package first.'}
    $output=Join-Path (Get-Location) ('client/target/t2c4-smoke/'+[Guid]::NewGuid().ToString())
    New-Item -ItemType Directory $output | Out-Null
    $run=Join-Path $output 'run';$log=Join-Path $output 'cli.txt'
    $head=(git rev-parse HEAD).Trim()
    function Run-Cli([int]$Expected,[string[]]$TaskArgs) {
        $lines=& $javaCommand '-Duser.timezone=UTC' '-cp' $jar 'vn.edu.toeic.client.monitoring.harness.HarnessCli' @TaskArgs
        $actual=$LASTEXITCODE
        $lines | Tee-Object -FilePath $log -Append | Write-Output
        if($actual -ne $Expected){throw "Unexpected harness exit: expected $Expected, actual $actual"}
    }
    Run-Cli 0 @('run','--output-dir',$run,'--poll-ms',"$PollMillis",'--seed',"$Seed",'--per-duration',"$PerDuration",'--max-concurrent',"$MaxConcurrent",'--source-sha',$head)
    $metadata=Get-Content -Raw -Encoding UTF8 (Join-Path $run 'metadata.json') | ConvertFrom-Json
    $report=Get-Content -Raw -Encoding UTF8 (Join-Path $run 'join.json') | ConvertFrom-Json
    if($metadata.sourceLabel -ne 'REAL' -or $metadata.policyVersion -ne 'test-owned-process-v1' -or -not $metadata.resourcesCleaned -or $report.rows.Count -ne 3*$PerDuration){throw 'Run metadata/cleanup/count invalid.'}
    foreach($target in @(200,800,3000)){if(@($report.rows | Where-Object {$_.targetMillis -eq $target}).Count -ne $PerDuration){throw 'Missing planned duration group.'}}
    Run-Cli 0 @('join','--input-dir',$run,'--output',(Join-Path $run 'joined-again.json'))
    if((Get-FileHash (Join-Path $run 'join.json')).Hash -ne (Get-FileHash (Join-Path $run 'joined-again.json')).Hash){throw 'Rejoin differs.'}
    'PASS reread ground truth/C3 trace/manifest and rejoin byte-identical; no requirement all children observed' | Tee-Object -FilePath $log -Append | Write-Output
    $bad=Join-Path $output 'tampered-run'
    Copy-Item -LiteralPath $run -Destination $bad -Recurse
    $trace=Join-Path $bad 'observations.jsonl'
    $text=[Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($trace)).Replace('test-owned-process-v1','invalid-test-policy')
    [IO.File]::WriteAllText($trace,$text,[Text.UTF8Encoding]::new($false))
    Run-Cli 2 @('join','--input-dir',$bad,'--output',(Join-Path $bad 'invalid-join.json'))
    $invalid=Get-Content -Raw -Encoding UTF8 (Join-Path $bad 'invalid-join.json') | ConvertFrom-Json
    if($invalid.notObserved -ne 0 -or $invalid.inconclusive -ne 3*$PerDuration){throw 'Broken trace was incorrectly reported as missed.'}
    'PASS corrupt trace rejected as inconclusive, not definite miss' | Tee-Object -FilePath $log -Append | Write-Output
    $manifest=@(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique |
        Where-Object {$_ -match '\.(java|ps1|py|properties|xml|sql|jsonl|json)$'} | ForEach-Object {
            [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}
        })
    [ordered]@{
        sourceHead=$head;branch=(git branch --show-current).Trim();workingTree=@(git status --porcelain)
        utc=[DateTime]::UtcNow.ToString('o');machine='LOCAL-WINDOWS-HARNESS-01';os=[Environment]::OSVersion.VersionString
        seed=$Seed;pollMillis=$PollMillis;perDuration=$PerDuration;maxConcurrent=$MaxConcurrent;fullC4Acceptance=($PerDuration -ge 10)
        source='REAL Windows ProcessHandle/production collector';workload='REAL controlled Java children; TEST owned-root identity policy'
        gate='SIMULATED candidate context';network='NOT RUN';e1e2='NOT RUN';humanBReview='NOT RUN'
        clientJarSha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant();sourceManifest=$manifest
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $output 'verification-metadata.json')
    Write-Output "PASS T2-C4 artifact/identity/clock/cleanup smoke; artifacts: $output"
} finally {Pop-Location}
