<#
Build lai TOEIC Server tu code hien tai va thay container "server" trong docker-compose.yml (profile server).

  1. mvn package module server bang JDK 21 (bo qua neu truyen -Jar).
  2. docker build image toeic-server:local tu scripts/server-image; image cu duoc gan tag toeic-server:prev.
  3. docker compose --profile server up -d server: cau hinh lay tu .env (DB, PIN key, thu muc media).
  4. Doi server tra 401 cho mot lan login sai. Khong len thi chay lai image cu.

-Rollback bo qua buoc 1-2 va chay lai image toeic-server:prev (ban cua lan deploy truoc).
Script khong in mat khau; .env khong bao gio duoc commit.
#>
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Jar,
    [int]$StartTimeoutSec = 120,
    [switch]$Rollback
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$contextDir = Join-Path $PSScriptRoot 'server-image'
# Ten image va container phai khop docker-compose.yml.
$image = 'toeic-server'
$container = 'toeic-server'

function Invoke-Native([string]$what, [scriptblock]$command) {
    & $command
    if ($LASTEXITCODE -ne 0) { throw "$what that bai (exit $LASTEXITCODE)." }
}

function Test-Image([string]$name) { return @(docker images -q $name).Count -gt 0 }

# Server cua du an tra 401 cho login sai; 401 nghia la Spring, Flyway va DB deu da san sang.
function Test-ServerUp {
    try {
        $request = [Net.HttpWebRequest]::Create("http://127.0.0.1:$Port/api/v1/auth/login")
        $request.Method = 'POST'; $request.ContentType = 'application/json'; $request.Timeout = 3000
        $body = [Text.Encoding]::UTF8.GetBytes('{"requestId":"00000000-0000-4000-8000-000000000001","username":"redeploy-probe","password":"x"}')
        $stream = $request.GetRequestStream(); $stream.Write($body, 0, $body.Length); $stream.Close()
        $request.GetResponse().Close()
        return $false
    } catch {
        $failure = $_.Exception
        if ($failure -is [System.Management.Automation.MethodInvocationException]) { $failure = $failure.InnerException }
        return ($failure.Response -and [int]$failure.Response.StatusCode -eq 401)
    }
}

function Start-ServerAndWait {
    # Bat DB truoc (khong tao lai neu dang chay), roi chi tao lai server: --force-recreate khong kem
    # --no-deps se tao lai ca container DB.
    Invoke-Native 'docker compose up db' { docker compose up -d --wait db }
    Invoke-Native 'docker compose up server' { docker compose --profile server up -d --no-build --no-deps --force-recreate server }
    Write-Host "Doi server tra loi tren port $Port..."
    $deadline = (Get-Date).AddSeconds($StartTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-ServerUp) { return $true }
        if ((docker inspect $container --format '{{.State.Running}}') -ne 'true') { return $false }
        Start-Sleep -Seconds 2
    }
    return $false
}

$oldJavaHome = $env:JAVA_HOME
Push-Location $repo
try {
    # .env la nguon cau hinh duy nhat; compose khong bat buoc cac bien nay de lenh chi chay DB van dung duoc.
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $repo '.env')) {
        if ($line -match '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim().Trim('"') }
    }
    foreach ($key in 'DB_PASSWORD', 'TOEIC_ROOM_PIN_KEY', 'TOEIC_AUDIO_ROOT', 'TOEIC_IMAGE_ROOT') {
        if (-not $settings[$key]) { throw "Thieu $key trong .env." }
    }
    foreach ($key in 'TOEIC_AUDIO_ROOT', 'TOEIC_IMAGE_ROOT') {
        $path = $settings[$key]
        if (-not [IO.Path]::IsPathRooted($path)) { $path = Join-Path $repo $path }
        if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Khong thay thu muc $key`: $path" }
    }
    # Cung mac dinh voi docker-compose.yml.
    $Port = 8080
    if ($settings['SERVER_PORT']) { $Port = [int]$settings['SERVER_PORT'] }

    # Container tao bang "docker run" (truoc khi server vao compose) phai xoa tay mot lan.
    # PowerShell 5.1 bo dau " khi truyen cho lenh ngoai, nen template docker dung chuoi `...` cua Go.
    $project = @(docker ps -a --filter "name=^/$container$" --format '{{.Label `com.docker.compose.project`}}')
    if ($project.Count -gt 0 -and -not $project[0]) {
        throw "Container $container khong thuoc compose. Chay 'docker rm -f $container' roi chay lai script."
    }

    if ($Rollback) {
        if (-not (Test-Image "${image}:prev")) { throw "Khong co image ${image}:prev de quay lai." }
        Invoke-Native 'Gan tag image cu' { docker tag "${image}:prev" "${image}:local" }
    } else {
        # 1. Jar
        $labels = @()
        if (-not $Jar) {
            if (-not $JavaHome -or -not (Select-String -LiteralPath (Join-Path $JavaHome 'release') -Pattern '^JAVA_VERSION="21\.' -Quiet)) {
                throw 'Can JDK 21: truyen -JavaHome <thu muc JDK 21>.'
            }
            $env:JAVA_HOME = $JavaHome
            Write-Host 'Build server...'
            & mvn -q -pl server -am package -DskipTests
            if ($LASTEXITCODE -ne 0) {
                throw 'Build server that bai. Neu VS Code dang mo repo, Java extension co the ghi de target/: dong VS Code hoac build o noi khac roi truyen -Jar.'
            }
            $commit = (git rev-parse --short HEAD)
            $dirty = [bool](git status --porcelain --untracked-files=no -- server protocol windows-observer pom.xml)
            $labels = @('--label', "toeic.commit=$commit", '--label', "toeic.dirty=$dirty")
            $Jar = Join-Path $repo 'server\target\server-0.1.0-SNAPSHOT.jar'
        }
        if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) { throw "Khong thay jar: $Jar" }

        # 2. Image
        Copy-Item -LiteralPath $Jar -Destination (Join-Path $contextDir 'server.jar') -Force
        if (Test-Image "${image}:local") { Invoke-Native 'Gan tag image cu' { docker tag "${image}:local" "${image}:prev" } }
        $buildArgs = @('build', '-t', "${image}:local") + $labels + @($contextDir)
        Invoke-Native 'docker build' { docker @buildArgs }
    }

    # 3-4. Chay va kiem tra; loi thi quay ve image truoc
    if (-not (Start-ServerAndWait)) {
        docker logs --tail 40 $container
        if ($Rollback -or -not (Test-Image "${image}:prev")) { throw 'Server khong khoi dong duoc (log o tren).' }
        Write-Host 'Server moi khong len; chay lai image truoc...' -ForegroundColor Yellow
        Invoke-Native 'Gan tag image cu' { docker tag "${image}:prev" "${image}:local" }
        if (-not (Start-ServerAndWait)) { throw 'Ca image moi va image truoc deu khong khoi dong duoc (xem docker logs toeic-server).' }
        throw 'Server moi khong khoi dong duoc (log o tren); dang chay lai image truoc.'
    }
    $built = docker inspect "${image}:local" --format '{{index .Config.Labels `toeic.commit`}}'
    Write-Host "Server da chay: http://127.0.0.1:$Port (image ${image}:local, commit $built)." -ForegroundColor Green
    if (-not $Rollback) { Write-Host 'Quay lai ban truoc: .\scripts\redeploy-server.ps1 -Rollback' }
} finally {
    $env:JAVA_HOME = $oldJavaHome
    Pop-Location
}
