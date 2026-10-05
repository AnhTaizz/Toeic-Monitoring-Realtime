# REAL PostgreSQL 18 + HTTP verification for Task T2-A1 (Exam Import & Session Management)
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
    
    $smokeClasses = 'server/target/t2a1-smoke-classes/vn/edu/toeic/server/exam'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/exam/ExamPostgresSmoke*.class' $smokeClasses -Force
    & $javaCommand '-Duser.timezone=UTC' '-Dloader.path=server/target/t2a1-smoke-classes' `
        '-Dloader.main=vn.edu.toeic.server.exam.ExamPostgresSmoke' '-cp' $serverJar `
        'org.springframework.boot.loader.launch.PropertiesLauncher'
    if ($LASTEXITCODE -ne 0) { throw 'T2-A1 PostgreSQL smoke failed.' }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    Pop-Location
}
