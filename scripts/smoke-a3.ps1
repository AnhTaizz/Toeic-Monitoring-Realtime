# REAL PostgreSQL + HTTP + WebSocket; owns temporary TEST schemas, never resets development data.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousValues = @{}
Push-Location $projectRoot
try {
    if (-not (Test-Path '.env')) { throw 'Local .env is required for PostgreSQL smoke.' }
    foreach ($line in Get-Content '.env') {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key = $Matches[1]
            $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    $serverJar = 'server/target/server-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path $serverJar)) { throw 'Run mvn package first.' }
    # A separate loader directory prevents MOCK unit-test configurations being component-scanned.
    $smokeClasses = 'server/target/a3-smoke-classes/vn/edu/toeic/server/monitoring'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/MonitoringPostgresSmoke*.class' $smokeClasses -Force
    & $javaCommand '-Duser.timezone=UTC' '-Dloader.path=server/target/a3-smoke-classes' `
        '-Dloader.main=vn.edu.toeic.server.monitoring.MonitoringPostgresSmoke' '-cp' $serverJar `
        'org.springframework.boot.loader.launch.PropertiesLauncher'
    if ($LASTEXITCODE -ne 0) { throw 'A3 PostgreSQL smoke failed. Credentials are never printed.' }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    Pop-Location
}
