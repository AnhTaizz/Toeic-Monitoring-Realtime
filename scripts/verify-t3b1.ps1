param([string]$JavaHome = $env:JAVA_HOME, [switch]$Postgres, [switch]$PackageSmoke, [ValidateRange(0,65535)][int]$DatabasePort = 0)
$ErrorActionPreference = 'Stop'
$previous = @{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach ($key in @('JAVA_HOME','PATH','TOEIC_PG_INTEGRATION','TOEIC_AUDIO_SMOKE_OUTPUT','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_SEED_PASSWORD')) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    $env:JAVA_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
    if ($Postgres) {
        foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
            if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
                [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
            }
        }
        # Match application.properties and the existing A1/A2 smoke scripts when .env omits development defaults.
        $defaults = @{ DB_HOST = '127.0.0.1'; DB_PORT = '5432'; DB_NAME = 'toeic'; DB_USER = 'toeic'; TOEIC_SEED_PASSWORD = 'ChangeMe123!' }
        foreach ($key in $defaults.Keys) {
            if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
                [Environment]::SetEnvironmentVariable($key, $defaults[$key], 'Process')
            }
        }
        if ($DatabasePort -gt 0) { $env:DB_PORT = [string]$DatabasePort }
        $env:TOEIC_PG_INTEGRATION = 'true'
    }
    $output = Join-Path (Get-Location) ('client/target/t3b1-verification/' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $output | Out-Null
    # Windows PowerShell 5.1 wraps native stderr as ErrorRecord. Fault-injection tests intentionally write stderr.
    # Judge Maven by its process exit code, not by the presence of those diagnostics.
    $ErrorActionPreference = 'Continue'
    try {
        & mvn -B -o "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" package *> (Join-Path $output 'maven-package.txt')
        $mavenExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop' }
    Get-Content -LiteralPath (Join-Path $output 'maven-package.txt') -Tail 25
    if ($mavenExit -ne 0) { throw "Maven package failed. Log: $output" }
    if ($PackageSmoke) {
        & "$PSScriptRoot/smoke-t3b1-package.ps1" -JavaHome $JavaHome -OutputDirectory $output
    }
    Write-Output "Evidence output: $output"
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    Pop-Location
}
