# Development smoke only. Load local DB/seed variables without printing credentials.
# Does not reset the database; the Java harness removes only its own login sessions.
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
    $serverJar = 'server/target/server-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path $serverJar)) { throw 'Run mvn package first.' }
    # Expose only the executable harness, never MOCK @Configuration/controllers
    # from the unit-test classpath to production component scanning.
    $smokeClasses = 'server/target/smoke-classes/vn/edu/toeic/server/auth'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/auth/PostgresWebSocketSmoke*.class' $smokeClasses -Force
    $stdout = Join-Path $projectRoot 'server/target/a2-smoke.stdout.txt'
    $stderr = Join-Path $projectRoot 'server/target/a2-smoke.stderr.txt'
    $javaArguments = @('-Duser.timezone=UTC', '-Dloader.path=server/target/smoke-classes',
        '-Dloader.main=vn.edu.toeic.server.auth.PostgresWebSocketSmoke', '-cp', $serverJar,
        'org.springframework.boot.loader.launch.PropertiesLauncher')
    $process = Start-Process -FilePath $javaCommand -ArgumentList $javaArguments -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Get-Content $stdout
    Get-Content $stderr
    if ($process.ExitCode -ne 0) { throw 'A2 PostgreSQL smoke failed. No credential diagnostics are printed.' }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    Pop-Location
}
