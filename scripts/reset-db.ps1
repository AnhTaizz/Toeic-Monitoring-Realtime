param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot 'docker-compose.yml'

if (-not $Force) {
    throw 'Lenh nay xoa volume toeic-pgdata. Chay lai voi -Force neu that su muon reset DB phat trien.'
}

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Khong tim thay docker-compose.yml tai $repositoryRoot"
}

Push-Location $repositoryRoot
try {
    $volumes = @(docker compose config --volumes)
    if ($LASTEXITCODE -ne 0) {
        throw 'Khong doc duoc cau hinh Docker Compose.'
    }
    if ($volumes.Count -ne 1 -or $volumes[0].Trim() -ne 'toeic-pgdata') {
        throw "Tu choi reset vi compose khong chi chua volume toeic-pgdata: $($volumes -join ', ')"
    }

    docker compose down -v
    if ($LASTEXITCODE -ne 0) {
        throw 'Khong xoa duoc DB phat trien.'
    }

    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) {
        throw 'Khong khoi tao lai duoc PostgreSQL.'
    }

    Write-Host 'PostgreSQL sach da san sang. Khoi dong server de Flyway tao schema.'
} finally {
    Pop-Location
}
