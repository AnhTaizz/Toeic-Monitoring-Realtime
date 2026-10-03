$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Chua co .env. Chep .env.example thanh .env va dien cau hinh DB.'
}
foreach ($line in Get-Content -LiteralPath $envFile) {
    $entry = $line.Trim()
    if ($entry -eq '' -or $entry.StartsWith('#')) { continue }
    $parts = $entry.Split('=', 2)
    if ($parts.Length -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$') {
        throw 'Dong cau hinh .env khong hop le.'
    }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}
$serverJar = Join-Path $projectRoot 'server/target/server-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $serverJar)) {
    throw 'Chua co JAR server. Chay mvn package o thu muc goc truoc.'
}
Push-Location $projectRoot
try {
    # PostgreSQL co the khong nhan alias timezone cua Windows/JVM (Asia/Saigon).
    # Chi dat timezone cho tien trinh server; khong doi timezone may nguoi dung.
    & java '-Duser.timezone=UTC' -jar $serverJar
    if ($LASTEXITCODE -ne 0) { throw "Server thoat voi ma $LASTEXITCODE" }
} finally {
    Pop-Location
}
