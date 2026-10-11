# Observation record/replay only: no shared DB, no authentication, no benchmark.
param([string]$JavaHome=$env:JAVA_HOME,[switch]$RealEdge)
$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $javaCommand=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
    $jar='client/target/client-0.1.0-SNAPSHOT-all.jar'
    if(-not (Test-Path $jar)){throw 'Run mvn package first.'}
    $output=Join-Path (Get-Location) ('client/target/t2c3-smoke/'+[Guid]::NewGuid().ToString())
    New-Item -ItemType Directory $output | Out-Null
    $log=Join-Path $output 'cli.txt'
    function Run-Cli([int]$Expected,[string[]]$TaskArgs) {
        $lines=& $javaCommand '-Duser.timezone=UTC' '-cp' $jar 'vn.edu.toeic.client.monitoring.trace.TraceCli' @TaskArgs
        $actual=$LASTEXITCODE
        $lines | Tee-Object -FilePath $log -Append | Write-Output
        if($actual -ne $Expected){throw "Unexpected CLI exit: expected $Expected, actual $actual"}
    }
    $fixture='client/src/test/resources/monitoring-traces/handwritten-v1.jsonl'
    $cases=@(
        @{Name='clean';Args=@()},
        @{Name='duplicate';Args=@('--duplicate','100')},
        @{Name='drop-middle';Args=@('--drop-full-at','2')},
        @{Name='drop-first';Args=@('--drop-full-at','1')},
        @{Name='mixed';Args=@('--seed','1234','--duplicate','35','--drop','20','--reorder','60','--reconnect-at','3')},
        @{Name='repeat';Args=@('--seed','1234','--duplicate','35','--drop','20','--reorder','60','--reconnect-at','3')}
    )
    foreach($case in $cases){
        Write-Output ("CASE "+$case.Name)
        Run-Cli 0 (@('replay','--input',$fixture,'--output',(Join-Path $output ($case.Name+'.json')))+$case.Args)
    }
    if((Get-FileHash (Join-Path $output 'mixed.json')).Hash -ne (Get-FileHash (Join-Path $output 'repeat.json')).Hash){throw 'Seed replay differs.'}
    'PASS same input/config/seed: byte-identical reports' | Tee-Object -FilePath $log -Append | Write-Output
    Run-Cli 1 @('replay','--input',$fixture,'--output',(Join-Path $output 'mutation.json'),'--mutate-full-at','2')
    $bad=Join-Path $output 'corrupt.jsonl'
    $bytes=[IO.File]::ReadAllBytes($fixture)
    $text=[Text.Encoding]::UTF8.GetString($bytes).Replace('"pollMillis":100','"pollMillis":101')
    [IO.File]::WriteAllText($bad,$text,[Text.UTF8Encoding]::new($false))
    Run-Cli 2 @('replay','--input',$bad,'--output',(Join-Path $output 'must-not-exist.json'))
    $record=Join-Path $output 'process-handle.jsonl'
    Run-Cli 0 @('record','--output',$record,'--scans','6','--poll-ms','100')
    Run-Cli 0 @('replay','--input',$record,'--output',(Join-Path $output 'process-handle.json'))
    if($RealEdge){
        $edgeTrace=Join-Path $output 'owned-edge.jsonl'
        & $javaCommand '-Duser.timezone=UTC' '-cp' ('client/target/test-classes;'+$jar) 'vn.edu.toeic.client.monitoring.trace.TraceCaptureSmoke' $edgeTrace |
            Tee-Object -FilePath $log -Append | Write-Output
        if($LASTEXITCODE -ne 0){throw 'Owned Edge capture failed.'}
        Run-Cli 0 @('replay','--input',$edgeTrace,'--output',(Join-Path $output 'owned-edge.json'))
        Run-Cli 0 @('replay','--input',$edgeTrace,'--output',(Join-Path $output 'owned-edge-seeded.json'),'--seed','1234','--duplicate','20','--drop','10','--reorder','35','--reconnect-at','3')
    }
    $manifest=@(git ls-files --cached --others --exclude-standard -- client protocol server scripts | Sort-Object -Unique |
        Where-Object {$_ -match '\.(java|ps1|py|properties|xml|sql|jsonl|json)$'} | ForEach-Object {
            [ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}
        })
    $versionProcess=New-Object System.Diagnostics.Process
    $versionProcess.StartInfo.FileName=$javaCommand
    $versionProcess.StartInfo.Arguments='-version'
    $versionProcess.StartInfo.UseShellExecute=$false
    $versionProcess.StartInfo.CreateNoWindow=$true
    $versionProcess.StartInfo.RedirectStandardError=$true
    [void]$versionProcess.Start();$javaVersion=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit();$versionProcess.Dispose()
    [ordered]@{
        sourceHead=(git rev-parse HEAD).Trim();branch=(git branch --show-current).Trim();workingTree=@(git status --porcelain)
        utc=[DateTime]::UtcNow.ToString('o');machine='LOCAL-WINDOWS-TEST-01';os=[Environment]::OSVersion.VersionString
        java=$javaVersion;seed=1234;realEdge=[bool]$RealEdge
        workload='MOCK fixture + REAL ProcessHandle; replay production encoders/reducer; faults/ACK/gate SIMULATED'
        network='NOT RUN in this replay';humanReview='NOT RUN';ownerBReview='NOT RUN';e1e2='NOT MEASURED'
        clientJarSha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant();sourceManifest=$manifest
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $output 'metadata.json')
    Write-Output "PASS T2-C3 smoke; artifacts: $output"
} finally {Pop-Location}
