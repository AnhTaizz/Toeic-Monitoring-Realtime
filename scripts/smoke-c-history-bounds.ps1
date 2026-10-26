# REAL PostgreSQL + production HTTP; owns and removes one temporary TEST schema.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$previousValues = @{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Local .env is required for PostgreSQL history smoke.' }
    foreach ($key in @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD', 'TOEIC_SEED_PASSWORD', 'TOEIC_SEED_DEMO_ACCOUNTS', 'JAVA_HOME', 'PATH', 'JAVA_TOOL_OPTIONS', 'TOEIC_PG_INTEGRATION')) {
        $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD|TOEIC_SEED_DEMO_ACCOUNTS)\s*=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
    $defaults = @{ DB_HOST = '127.0.0.1'; DB_PORT = '5432'; DB_NAME = 'toeic'; DB_USER = 'toeic'; TOEIC_SEED_PASSWORD = 'ChangeMe123!' }
    foreach ($key in $defaults.Keys) {
        if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
            [Environment]::SetEnvironmentVariable($key, $defaults[$key], 'Process')
        }
    }
    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
        $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    }
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' -Duser.timezone=UTC').Trim()
    $env:TOEIC_PG_INTEGRATION = 'true'
    & mvn -B -o "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -pl server -am test `
        '-Dtest=MonitoringHistoryBoundsPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false'
    if ($LASTEXITCODE -ne 0) { throw 'C history boundary PostgreSQL/HTTP test failed.' }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    Pop-Location
}
