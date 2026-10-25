param([string]$JavaHome = $env:JAVA_HOME, [switch]$FullBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousValues = @{}
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Local .env is required for PostgreSQL smoke.' }
    foreach ($key in @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD', 'TOEIC_SEED_PASSWORD', 'TOEIC_SEED_DEMO_ACCOUNTS')) {
        $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD|TOEIC_SEED_DEMO_ACCOUNTS)\s*=(.*)$') {
            $key = $Matches[1]
            [Environment]::SetEnvironmentVariable($key, $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
    # Same development defaults as application.properties; seed applies only to owned test schemas.
    $defaults = @{ DB_HOST = '127.0.0.1'; DB_PORT = '5432'; DB_NAME = 'toeic'; DB_USER = 'toeic'; TOEIC_SEED_PASSWORD = 'ChangeMe123!' }
    foreach ($key in $defaults.Keys) {
        if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
            [Environment]::SetEnvironmentVariable($key, $defaults[$key], 'Process')
        }
    }
    foreach ($key in @('JAVA_HOME', 'PATH', 'TOEIC_PG_INTEGRATION')) {
        $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
        $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    }
    $env:TOEIC_PG_INTEGRATION = 'true'
    $mavenArguments = @('-B', '-o', "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository")
    if ($FullBuild) { $mavenArguments += 'package' }
    else {
        $mavenArguments += @('-pl', 'server', '-am', 'test',
            '-Dtest=ListeningRuntimePostgresTest,AudioRuntimePostgresTest,ExamRuntimePostgresTest', '-Dsurefire.failIfNoSpecifiedTests=false')
    }
    # Windows PowerShell 5 treats native stderr as an ErrorRecord. Negative tests intentionally write warnings.
    # Maven's exit code decides success; a warning must not abort the entire reactor.
    $nativeErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & mvn @mavenArguments
        $mavenExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativeErrorPreference }
    if ($mavenExitCode -ne 0) { throw 'T3-A2 PostgreSQL smoke or existing exam regression failed.' }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    Pop-Location
}
