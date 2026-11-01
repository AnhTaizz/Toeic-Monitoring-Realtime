<#
Build lai TOEIC Server tu code hien tai va thay container Docker dang chay, giu nguyen cau hinh.

  1. mvn package module server bang JDK 21 (bo qua neu truyen -Jar).
  2. docker build image <Image>:local tu scripts/server-image/Dockerfile; image cu duoc gan tag <Image>:prev.
  3. Lay bien DB_*/TOEIC_* va thu muc media tu container dang chay, tao container moi cung network va port.
  4. Doi server tra 401 cho mot lan login sai. Khong len thi xoa container moi va chay lai container cu.

-Rollback bo qua buoc 1-2 va chay lai image <Image>:prev (ban cua lan deploy truoc).
Lan dau (chua co container) phai truyen -EnvFile, vi du .env, de lay DB, PIN key, Admin va thu muc media.
Mat khau chi nam trong file env tam va bi xoa khi script ket thuc; script khong in mat khau.
#>
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Jar,
    [string]$EnvFile,
    [string]$Container = 'toeic-server',
    [string]$Image = 'toeic-server',
    [string]$Network = 'toeic-monitoring-realtime_default',
    [ValidateRange(1,65535)][int]$Port = 8080,
    [int]$StartTimeoutSec = 120,
    [switch]$Rollback
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$backup = "$Container-prev"

function Invoke-Native([string]$what, [scriptblock]$command) {
    & $command
    if ($LASTEXITCODE -ne 0) { throw "$what that bai (exit $LASTEXITCODE)." }
}

# Khong dung 2>$null voi lenh ngoai: tren PowerShell 5.1 no bien stderr thanh loi va dung script.
function Test-Container([string]$name) {
    return @(docker ps -a --filter "name=^/$name$" --format '{{.Names}}') -contains $name
}

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

function Resolve-HostPath([string]$path) {
    if (-not [IO.Path]::IsPathRooted($path)) { $path = Join-Path $repo $path }
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Khong thay thu muc media: $path" }
    return (Resolve-Path -LiteralPath $path).Path
}

$oldJavaHome = $env:JAVA_HOME
$context = Join-Path ([IO.Path]::GetTempPath()) ('toeic-server-image-' + [guid]::NewGuid().ToString('N'))
$envPath = Join-Path $context 'server.env'
try {
    # -Rollback: khong build, chay lai image <Image>:prev cua lan deploy truoc.
    $runImage = "${Image}:local"
    if ($Rollback) {
        $runImage = "${Image}:prev"
        if (@(docker images -q $runImage).Count -eq 0) { throw "Khong co image $runImage de quay lai." }
    }

    # 1. Jar
    $labels = @()
    if (-not $Rollback -and -not $Jar) {
        if (-not $JavaHome -or -not (Select-String -LiteralPath (Join-Path $JavaHome 'release') -Pattern '^JAVA_VERSION="21\.' -Quiet)) {
            throw 'Can JDK 21: truyen -JavaHome <thu muc JDK 21>.'
        }
        $env:JAVA_HOME = $JavaHome
        Push-Location $repo
        try {
            Write-Host 'Build server...'
            & mvn -q -pl server -am package -DskipTests
            if ($LASTEXITCODE -ne 0) {
                throw 'Build server that bai. Neu VS Code dang mo repo, Java extension co the ghi de target/: dong VS Code hoac build o noi khac roi truyen -Jar.'
            }
            $commit = (git rev-parse --short HEAD)
            $dirty = [bool](git status --porcelain --untracked-files=no -- server protocol windows-observer pom.xml)
            $labels = @('--label', "toeic.commit=$commit", '--label', "toeic.dirty=$dirty")
        } finally { Pop-Location }
        $Jar = Join-Path $repo 'server\target\server-0.1.0-SNAPSHOT.jar'
    }
    New-Item -ItemType Directory -Path $context | Out-Null
    if (-not $Rollback) {
        if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) { throw "Khong thay jar: $Jar" }

        # 2. Image
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'server-image\Dockerfile') -Destination $context
        Copy-Item -LiteralPath $Jar -Destination (Join-Path $context 'server.jar')
        if (@(docker images -q "${Image}:local").Count -gt 0) {
            Invoke-Native 'Gan tag image cu' { docker tag "${Image}:local" "${Image}:prev" }
        }
        $buildArgs = @('build', '-t', "${Image}:local") + $labels + @($context)
        Invoke-Native 'docker build' { docker @buildArgs }
    }

    # 3. Cau hinh: tu -EnvFile neu co, khong thi tu container dang chay
    $hadOld = Test-Container $Container
    $settings = [ordered]@{}
    if ($EnvFile) {
        foreach ($line in Get-Content -LiteralPath $EnvFile) {
            if ($line -match '^\s*((DB|TOEIC)_[A-Z0-9_]*)\s*=(.*)$') { $settings[$Matches[1]] = $Matches[3].Trim().Trim('"') }
        }
        $audio = $settings['TOEIC_AUDIO_ROOT']; $images = $settings['TOEIC_IMAGE_ROOT']
    } elseif ($hadOld) {
        foreach ($line in docker inspect $Container --format '{{range .Config.Env}}{{println .}}{{end}}') {
            if ($line -match '^((DB|TOEIC)_[A-Z0-9_]*)=(.*)$') { $settings[$Matches[1]] = $Matches[3] }
        }
        foreach ($line in docker inspect $Container --format '{{range .Mounts}}{{.Destination}}|{{.Source}}{{println}}{{end}}') {
            if ($line -match '^/data/audio\|(.+)$') { $audio = $Matches[1] }
            if ($line -match '^/data/images\|(.+)$') { $images = $Matches[1] }
        }
    } else {
        throw "Chua co container ${Container}: truyen -EnvFile (vi du .env) de lay cau hinh."
    }
    if (-not $audio -or -not $images) { throw 'Thieu thu muc audio/image trong cau hinh.' }
    $audio = Resolve-HostPath $audio
    $images = Resolve-HostPath $images
    # Trong network compose, DB la service "db" o port 5432; media duoc mount vao /data.
    $settings['DB_HOST'] = 'db'; $settings['DB_PORT'] = '5432'
    $settings['TOEIC_AUDIO_ROOT'] = '/data/audio'; $settings['TOEIC_IMAGE_ROOT'] = '/data/images'
    # .NET ghi UTF-8 khong BOM; docker --env-file doc sai dong dau neu co BOM.
    [IO.File]::WriteAllLines($envPath, [string[]]@($settings.Keys | ForEach-Object { "$_=$($settings[$_])" }))

    # 4. Thay container, giu container cu de tra lai neu container moi khong len
    if (Test-Container $backup) { Invoke-Native 'Xoa container du phong cu' { docker rm -f $backup } }
    if ($hadOld) {
        Invoke-Native 'Dung container cu' { docker stop $Container }
        Invoke-Native 'Doi ten container cu' { docker rename $Container $backup }
    }
    $runArgs = @('run', '-d', '--name', $Container, '--restart', 'unless-stopped',
        '--network', $Network, '--network-alias', 'server',
        '-p', "127.0.0.1:${Port}:8080", '--env-file', $envPath,
        '-v', "${audio}:/data/audio", '-v', "${images}:/data/images", $runImage)
    Invoke-Native 'docker run' { docker @runArgs }

    Write-Host "Doi server tra loi tren port $Port..."
    $deadline = (Get-Date).AddSeconds($StartTimeoutSec)
    $up = $false
    while ((Get-Date) -lt $deadline) {
        if (Test-ServerUp) { $up = $true; break }
        if ((docker inspect $Container --format '{{.State.Running}}') -ne 'true') { break }
        Start-Sleep -Seconds 2
    }

    if (-not $up) {
        docker logs --tail 40 $Container
        Invoke-Native 'Xoa container moi' { docker rm -f $Container }
        if ($hadOld) {
            Invoke-Native 'Tra ten container cu' { docker rename $backup $Container }
            Invoke-Native 'Chay lai container cu' { docker start $Container }
            throw 'Server moi khong khoi dong duoc (log o tren); da chay lai container cu.'
        }
        throw 'Server moi khong khoi dong duoc (log o tren).'
    }
    if ($hadOld) { Invoke-Native 'Xoa container cu' { docker rm $backup } }
    # PowerShell 5.1 bo dau " khi truyen cho lenh ngoai, nen template dung chuoi `...` cua Go.
    $built = docker inspect $runImage --format '{{index .Config.Labels `toeic.commit`}}'
    Write-Host "Server da chay: http://127.0.0.1:$Port (image $runImage, commit $built)." -ForegroundColor Green
    if (-not $Rollback) { Write-Host "Quay lai ban truoc: .\scripts\redeploy-server.ps1 -Rollback" }
} finally {
    $env:JAVA_HOME = $oldJavaHome
    if (Test-Path -LiteralPath $context) { Remove-Item -LiteralPath $context -Recurse -Force }
}
