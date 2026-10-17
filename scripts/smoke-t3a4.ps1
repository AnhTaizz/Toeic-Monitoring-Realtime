# T3-A4: reproducible REAL PostgreSQL/HTTP/WS transaction and multi-client regression.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previous = @{}
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Local .env is required for T3-A4.' }
    foreach ($key in @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_SEED_PASSWORD','JAVA_HOME','PATH','TOEIC_PG_INTEGRATION')) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key,'Process')
    }
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2].Trim().Trim('"').Trim("'"),'Process')
        }
    }
    $defaults = @{ DB_HOST='127.0.0.1'; DB_PORT='5432'; DB_NAME='toeic'; DB_USER='toeic'; TOEIC_SEED_PASSWORD='ChangeMe123!' }
    foreach ($key in $defaults.Keys) {
        if (-not [Environment]::GetEnvironmentVariable($key,'Process')) {
            [Environment]::SetEnvironmentVariable($key,$defaults[$key],'Process')
        }
    }
    if ($JavaHome) {
        # A trailing separator escapes the closing quote when Windows PowerShell
        # forwards a path containing spaces to the child smoke process.
        $JavaHome = $JavaHome.Trim().Trim('"').Trim("'").TrimEnd([char[]]@('\','/'))
        $env:JAVA_HOME = $JavaHome
        $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    }
    $env:TOEIC_PG_INTEGRATION = 'true'
    $runId = 'T3A4-' + [Guid]::NewGuid().ToString()
    $output = Join-Path 'server/target/t3a4-runtime' $runId
    $source = (git rev-parse HEAD).Trim()
    $nativeErrorPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5 wraps native stderr (including harmless JVM warnings) as ErrorRecord.
        # The native exit code is authoritative. Clean removes stale compiled test artifacts.
        $ErrorActionPreference = 'Continue'
        & mvn -B -o -pl server -am clean '-DskipTests' package
        $packageExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativeErrorPreference }
    if ($packageExit -ne 0) { throw 'T3-A4 package failed.' }

    New-Item -ItemType Directory -Force $output | Out-Null
    [ordered]@{
        runId=$runId; sourceHead=$source; branch=(git branch --show-current).Trim(); utc=[DateTime]::UtcNow.ToString('o')
        command='powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3a4.ps1 -JavaHome <JDK21>'
        topology='REAL Spring/PostgreSQL/HTTP/WS; two candidates and assigned proctor; loopback single host'
        synchronization='PostgreSQL row lock plus observed pg_stat_activity waiters; no timing-only race claim'
        malformedClient='REAL WebSocket malformed JSON; other candidate heartbeat remains ACKed'
        lanSecondMachine='NOT RUN'; humanReview='NOT RUN'; workingTree=@(git status --porcelain)
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'metadata.json') -Encoding UTF8
    $mavenLog = Join-Path $output 'maven-regression.txt'
    try {
        $ErrorActionPreference = 'Continue'
        & mvn -B -o -pl server -am '-Dtest=ExamRuntimePostgresTest,AuthenticatedNetworkTest' '-Dsurefire.failIfNoSpecifiedTests=false' test 2>&1 | Tee-Object -FilePath $mavenLog -Append
        $regressionExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativeErrorPreference }
    if ($regressionExit -ne 0) { throw 'T3-A4 transaction/network regression failed.' }

    $at01Log = Join-Path $output 'postgres-at01-smoke.txt'
    try {
        $ErrorActionPreference = 'Continue'
        & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2a4.ps1 -JavaHome $JavaHome 2>&1 | Tee-Object -FilePath $at01Log
        $at01Exit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativeErrorPreference }
    if ($at01Exit -ne 0) { throw 'T3-A4 AT01 PostgreSQL smoke failed.' }

    $wsLog = Join-Path $output 'postgres-ws-smoke.txt'
    try {
        $ErrorActionPreference = 'Continue'
        & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a3.ps1 -JavaHome $JavaHome 2>&1 | Tee-Object -FilePath $wsLog
        $smokeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativeErrorPreference }
    if ($smokeExit -ne 0) { throw 'T3-A4 multi-client PostgreSQL/WS smoke failed.' }

    $mavenText = Get-Content -Raw -LiteralPath $mavenLog
    $at01Text = Get-Content -Raw -LiteralPath $at01Log
    $wsText = Get-Content -Raw -LiteralPath $wsLog
    if ($mavenText -notmatch 'save, submit and autonomous timeout wait across deadline') { throw 'Missing three-way transaction evidence.' }
    if ($at01Text -notmatch 'AT01: Cross-Candidate Access Isolation' -or $at01Text -notmatch 'AT01: After submit') { throw 'Missing AT01 cross-attempt evidence.' }
    if ($wsText -notmatch 'malformed/path rejection; other client survives') { throw 'Missing malformed-client isolation evidence.' }
    if ($wsText -notmatch 'four-client isolation') { throw 'Missing multi-client scope/push evidence.' }
    Write-Output "T3-A4 PASS; reproducible logs: $output"
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
    Pop-Location
}
