# Own TEST schema/port/child JVM. Optional actual JavaFX dashboard component verification.
param([string]$JavaHome = $env:JAVA_HOME, [switch]$Gui)
$ErrorActionPreference = 'Stop'
$previousValues = @{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not (Test-Path '.env')) { throw 'Local .env is required.' }
    foreach ($line in Get-Content -Encoding utf8 '.env') {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key = $Matches[1]
            $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    if (-not (Test-Path 'server/target/server-0.1.0-SNAPSHOT.jar') -or -not (Test-Path 'client/target/client-0.1.0-SNAPSHOT-all.jar')) { throw 'Run mvn package first.' }
    $smokeClasses = 'server/target/b3-smoke-classes/vn/edu/toeic/server/monitoring'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/ProctorDashboardSmoke*.class' $smokeClasses -Force
    $taskArgs = @()
    if ($Gui) { $taskArgs += '--gui' }
    & $javaCommand '-Duser.timezone=UTC' '-Dtoeic.dashboard.timezone=Asia/Ho_Chi_Minh' `
        '-Dloader.path=server/target/b3-smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar' `
        '-Dloader.main=vn.edu.toeic.server.monitoring.ProctorDashboardSmoke' '-cp' 'server/target/server-0.1.0-SNAPSHOT.jar' `
        'org.springframework.boot.loader.launch.PropertiesLauncher' @taskArgs
    if ($LASTEXITCODE -ne 0) { throw 'B3 integration failed; no credentials printed.' }
} finally {
    foreach ($key in $previousValues.Keys) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
    Pop-Location
}
