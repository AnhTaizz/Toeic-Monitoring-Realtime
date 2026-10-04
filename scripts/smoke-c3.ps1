# REAL PostgreSQL/HTTP/WS/B2/C3/Windows process. TEST schema only; no shared DB reset.
param([string]$JavaHome = $env:JAVA_HOME, [string]$DemoExecutable = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousValues = @{}
Push-Location $projectRoot
try {
    if (-not (Test-Path '.env')) { throw 'Local .env is required.' }
    foreach ($line in Get-Content '.env') {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key = $Matches[1]
            $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    if (-not (Test-Path 'server/target/server-0.1.0-SNAPSHOT.jar') -or -not (Test-Path 'client/target/client-0.1.0-SNAPSHOT-all.jar')) { throw 'Run mvn package first.' }
    $smokeClasses = 'server/target/c3-smoke-classes/vn/edu/toeic/server/monitoring'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'server/target/test-classes/vn/edu/toeic/server/monitoring/MonitoringDeliverySmoke*.class' $smokeClasses -Force
    if (-not $DemoExecutable -and ${env:ProgramFiles(x86)}) {
        $edge = Join-Path ${env:ProgramFiles(x86)} 'Microsoft/Edge/Application/msedge.exe'
        if (Test-Path $edge) { $DemoExecutable = $edge }
    }
    if (-not $DemoExecutable) { throw 'Owned Windows Edge executable unavailable; real process demo NOT RUN.' }
    & $javaCommand '-Duser.timezone=UTC' `
        '-Dloader.path=server/target/c3-smoke-classes,client/target/client-0.1.0-SNAPSHOT-all.jar' `
        '-Dloader.main=vn.edu.toeic.server.monitoring.MonitoringDeliverySmoke' '-cp' 'server/target/server-0.1.0-SNAPSHOT.jar' `
        'org.springframework.boot.loader.launch.PropertiesLauncher' $DemoExecutable
    if ($LASTEXITCODE -ne 0) { throw 'C3 integration smoke failed; no credential/private metadata printed.' }
} finally {
    foreach ($key in $previousValues.Keys) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
    Pop-Location
}
