# B2 dev smoke: production Spring process + B adapter + PostgreSQL Docker.
# Không reset DB; chỉ stop process và xóa session do harness tạo. Yêu cầu Docker Desktop.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousValues = @{}
Push-Location $projectRoot
try {
    $envFile = Join-Path $projectRoot '.env'
    if (-not (Test-Path $envFile)) { throw 'Local .env is required for PostgreSQL smoke.' }
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key = $Matches[1]
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    if (-not (Test-Path 'server/target/server-0.1.0-SNAPSHOT.jar') -or
        -not (Test-Path 'client/target/client-0.1.0-SNAPSHOT-all.jar')) { throw 'Run mvn package first.' }
    # Chỉ expose executable harness, không load các test MOCK vào runtime.
    $smokeClasses = 'client/target/smoke-classes/vn/edu/toeic/client/realtime'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'client/target/test-classes/vn/edu/toeic/client/realtime/RealtimePostgresSmoke*.class' $smokeClasses -Force
    $stdout = Join-Path $projectRoot 'client/target/b2-smoke.stdout.txt'
    $stderr = Join-Path $projectRoot 'client/target/b2-smoke.stderr.txt'
    $javaArguments = @('-Duser.timezone=UTC', '-cp',
        '"client/target/client-0.1.0-SNAPSHOT-all.jar;client/target/smoke-classes"',
        'vn.edu.toeic.client.realtime.RealtimePostgresSmoke')
    $process = Start-Process -FilePath $javaCommand -ArgumentList $javaArguments -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Get-Content $stdout
    Get-Content $stderr
    if ($process.ExitCode -ne 0) { throw 'B2 real integration failed. No credential diagnostics are printed.' }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    Pop-Location
}
