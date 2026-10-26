<#
Cloudflare Tunnel mode: the TOEIC Server runs on this Windows PC, Clients reach it at the public HTTPS hostname.

  -Action Start   Start the Server (or reuse the project Server already on the port) and the cloudflared
                  connector (unless a cloudflared Windows service or another cloudflared already runs it).
  -Action Status  Check the local Server, the connector and the public hostname; name the likely cause.
  -Action Stop    Stop only the processes this script started (recorded in its state file).

Secrets stay outside Git: the repository .env (database, media, PIN key) and the tunnel token file
(%LOCALAPPDATA%\TOEIC Monitor\cloudflare-tunnel.token by default). Nothing secret is printed.
The script never installs software, changes DNS or edits .env.
#>
param(
    [ValidateSet('Start','Status','Stop')][string]$Action = 'Start',
    [string]$EnvFile,
    [string]$TokenFile,
    [string]$PublicUrl = 'https://toeic.anhtaizz.id.vn',
    [ValidateRange(1,65535)][int]$Port = 8080,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Cloudflared,
    # Also accept LAN Clients on this PC's address; by default only the tunnel (127.0.0.1) reaches the Server.
    [switch]$AllowLan
)
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 defaults to old TLS versions; Cloudflare needs TLS 1.2.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$repo = Split-Path -Parent $PSScriptRoot
$localData = Join-Path $env:LOCALAPPDATA 'TOEIC Monitor'
$stateFile = Join-Path $localData 'cloudflare-managed.json'
if (-not $EnvFile) { $EnvFile = Join-Path $repo '.env' }
if (-not $TokenFile) {
    if ($env:TOEIC_TUNNEL_TOKEN_FILE) { $TokenFile = $env:TOEIC_TUNNEL_TOKEN_FILE } else { $TokenFile = Join-Path $localData 'cloudflare-tunnel.token' }
}
$origin = 'http://127.0.0.1:' + $Port
$serverJar = Join-Path $repo 'server\target\server-0.1.0-SNAPSHOT.jar'
$launcher = Join-Path $PSScriptRoot 'start-release.ps1'

function Say([string]$text, [string]$color = 'Gray') { Write-Host $text -ForegroundColor $color }

# One GET without redirects. Windows PowerShell 5.1 loses error bodies with Invoke-WebRequest, so read them here.
function Http-Get([string]$url, [int]$timeoutMs) {
    $request = [Net.HttpWebRequest]::Create($url)
    $request.Timeout = $timeoutMs; $request.ReadWriteTimeout = $timeoutMs; $request.AllowAutoRedirect = $false
    try { $response = $request.GetResponse() } catch {
        $failure = $_.Exception
        if ($failure -is [System.Management.Automation.MethodInvocationException]) { $failure = $failure.InnerException }
        $response = $failure.Response
        if (-not $response) { return @{ status = 0; body = ''; failure = [string]$failure.Status } }
    }
    try {
        $reader = New-Object IO.StreamReader($response.GetResponseStream())
        try { return @{ status = [int]$response.StatusCode; body = $reader.ReadToEnd(); failure = '' } } finally { $reader.Dispose() }
    } finally { $response.Close() }
}

# Without a token the project Server answers 401 with its own JSON error (same probe as start-release.ps1).
function Is-ProjectAnswer($answer) {
    return $answer.status -eq 401 -and $answer.body -match '"code"\s*:\s*"UNAUTHORIZED"' -and $answer.body -match '"protocolVersion"'
}

# 'project' = this TOEIC Server answers on the port; 'other' = another program holds it; 'free' = nothing listens.
function Probe-Origin {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $connect = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(1000)) { return 'free' }
        $client.EndConnect($connect)
    } catch { return 'free' } finally { $client.Dispose() }
    if (Is-ProjectAnswer (Http-Get ($origin + '/api/v1/auth/me') 3000)) { return 'project' }
    return 'other'
}

# Same request through Cloudflare. Returns a short verdict and the likely cause for the operator.
function Probe-Public {
    $uri = [Uri]$PublicUrl
    # Ask Cloudflare's resolver: some ISP resolvers answer a made-up address for names that do not exist.
    $dnsMissing = 'DNS: chưa có bản ghi cho ' + $uri.Host + '. Thêm Public Hostname cho tunnel trên Cloudflare (bản ghi được tạo tự động) rồi chờ vài phút.'
    if (Get-Command Resolve-DnsName -ErrorAction SilentlyContinue) {
        $records = @(Resolve-DnsName $uri.Host -Server 1.1.1.1 -DnsOnly -ErrorAction SilentlyContinue | Where-Object { $_.Type -in 'A','AAAA','CNAME' })
        if ($records.Count -eq 0) { return @{ ok = $false; text = $dnsMissing } }
    } else {
        try { [void][Net.Dns]::GetHostAddresses($uri.Host) } catch { return @{ ok = $false; text = $dnsMissing } }
    }
    $answer = Http-Get ($PublicUrl.TrimEnd('/') + '/api/v1/auth/me') 10000
    if (Is-ProjectAnswer $answer) { return @{ ok = $true; text = 'Qua Cloudflare tới Server TOEIC: OK (401 khi chưa đăng nhập là đúng).' } }
    $code = $answer.status
    if ($code -eq 0) {
        if ($answer.failure -eq 'TrustFailure' -or $answer.failure -eq 'SecureChannelFailure') { return @{ ok = $false; text = 'TLS: không thiết lập được HTTPS tới ' + $uri.Host + '.' } }
        if ($answer.failure -eq 'NameResolutionFailure') { return @{ ok = $false; text = $dnsMissing } }
        return @{ ok = $false; text = 'Không kết nối được tới ' + $uri.Host + ' (' + $answer.failure + '). Kiểm tra Internet của máy này hoặc DNS chưa cập nhật.' }
    }
    if ($code -eq 530 -or $answer.body -match 'Error 1033') { return @{ ok = $false; text = 'Tunnel offline (HTTP ' + $code + '): connector cloudflared chưa chạy hoặc token không thuộc tunnel của hostname này.' } }
    if ($code -eq 502 -or $code -eq 503 -or $code -eq 504) { return @{ ok = $false; text = 'Tunnel chạy nhưng không vào được origin (HTTP ' + $code + '): Server chưa chạy ở ' + $origin + ' hoặc Service URL sai.' } }
    if ($code -eq 403) { return @{ ok = $false; text = 'HTTP 403 từ Cloudflare: kiểm tra Access/WAF có đang chặn hostname này không.' } }
    return @{ ok = $false; text = 'Hostname trả HTTP ' + $code + ' nhưng không phải Server TOEIC. Kiểm tra Public Hostname trỏ tới HTTP ' + $origin.Replace('http://', '') + '.' }
}

function Find-Cloudflared {
    if ($Cloudflared) { if (Test-Path -LiteralPath $Cloudflared) { return $Cloudflared } else { return $null } }
    $command = Get-Command 'cloudflared.exe' -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($candidate in @((Join-Path ${env:ProgramFiles(x86)} 'cloudflared\cloudflared.exe'), (Join-Path $env:ProgramFiles 'cloudflared\cloudflared.exe'))) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    return $null
}

function Connector-State {
    $service = @(Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'cloudflared*' -and $_.Status -eq 'Running' })
    if ($service.Count -gt 0) { return 'service' }
    if (@(Get-Process -Name 'cloudflared' -ErrorAction SilentlyContinue).Count -gt 0) { return 'process' }
    return 'none'
}

function Read-State {
    if (-not (Test-Path -LiteralPath $stateFile)) { return @() }
    try { return @(Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json) } catch { return @() }
}

function Save-State($entries) {
    if (-not (Test-Path -LiteralPath $localData)) { New-Item -ItemType Directory -Force -Path $localData | Out-Null }
    ConvertTo-Json -InputObject @($entries) | Set-Content -LiteralPath $stateFile -Encoding UTF8
}

# A recorded process is ours only if the same PID still has the same start time (PIDs are reused).
function Owned-Process($entry) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().ToString('o') -eq $entry.started) { return $process }
    return $null
}

function Record([System.Diagnostics.Process]$process, [string]$role) {
    $entries = @(Read-State | Where-Object { (Owned-Process $_) -ne $null })
    $entries += [pscustomobject]@{ role = $role; pid = $process.Id; started = $process.StartTime.ToUniversalTime().ToString('o') }
    Save-State $entries
}

# Values for the Server come from .env; relative media folders are taken relative to the folder of that .env.
function Import-ServerEnvironment {
    if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw ('Thiếu file cấu hình ' + $EnvFile + '. Chép .env.example thành .env và điền DB, media, khóa PIN.') }
    $names = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_AUDIO_ROOT','TOEIC_IMAGE_ROOT','TOEIC_ROOM_PIN_KEY','TOEIC_ADMIN_BOOTSTRAP_USERNAME','TOEIC_ADMIN_BOOTSTRAP_PASSWORD')
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($EnvFile, [Text.Encoding]::UTF8)) {
        if ($line -match '^\s*([A-Z_]+)\s*=(.*)$' -and $names -contains $Matches[1]) { $values[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") }
    }
    if (-not $values['DB_HOST']) { $values['DB_HOST'] = '127.0.0.1' }
    $missing = @('DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_AUDIO_ROOT','TOEIC_IMAGE_ROOT','TOEIC_ROOM_PIN_KEY' | Where-Object { -not $values[$_] })
    if ($missing.Count -gt 0) { throw ('Thiếu trong ' + $EnvFile + ': ' + ($missing -join ', ') + '. Không giá trị nào được tạo hay ghi đè.') }
    foreach ($name in 'TOEIC_AUDIO_ROOT', 'TOEIC_IMAGE_ROOT') {
        $folder = $values[$name]
        if (-not [IO.Path]::IsPathRooted($folder)) { $folder = Join-Path (Split-Path -Parent (Resolve-Path -LiteralPath $EnvFile).ProviderPath) $folder }
        if (-not (Test-Path -LiteralPath $folder -PathType Container)) { throw ($name + ' trỏ tới thư mục không tồn tại. Kiểm tra .env.') }
        $values[$name] = (Resolve-Path -LiteralPath $folder).ProviderPath
    }
    return $values
}

function Start-ProjectServer {
    if (-not (Test-Path -LiteralPath $serverJar)) { throw 'Chưa có server JAR. Build trước: mvn -q package -DskipTests' }
    $values = Import-ServerEnvironment
    $previous = @{}
    try {
        # The Server window inherits these; start-release.ps1 prefers process values and prints none of them.
        foreach ($name in $values.Keys) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process'); [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process') }
        $bind = '127.0.0.1'
        if ($AllowLan) { $bind = '0.0.0.0' }
        $arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $launcher + '" -Role Server -BindAddress ' + $bind + ' -Port ' + $Port + ' -ServerJar "' + $serverJar + '"'
        if ($JavaHome) { $arguments += ' -JavaHome "' + $JavaHome + '"' }
        $window = Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WorkingDirectory $repo -PassThru
    } finally {
        foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
    Record $window 'server'
    Say ('Đang khởi động Server trong cửa sổ riêng (PID ' + $window.Id + '), chờ tối đa 180 giây…')
    $until = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $until) {
        if ($window.HasExited) { throw 'Server dừng khi khởi động. Xem thông báo lỗi trong cửa sổ Server (DB, media, khóa PIN, Java).' }
        if ((Probe-Origin) -eq 'project') { Say ('Server sẵn sàng tại ' + $origin + '.') 'Green'; return }
        Start-Sleep -Seconds 2
    }
    throw ('Server chưa trả lời sau 180 giây tại ' + $origin + '. Xem cửa sổ Server.')
}

function Start-Connector {
    $state = Connector-State
    if ($state -eq 'service') { Say 'cloudflared đang chạy dưới dạng Windows service: không mở thêm connector.' 'Green'; return }
    if ($state -eq 'process') { Say 'Đã có một tiến trình cloudflared đang chạy: không mở thêm bản thứ hai.' 'Yellow'; return }
    $exe = Find-Cloudflared
    if (-not $exe) { throw 'Chưa cài cloudflared. Cài một lần: winget install --id Cloudflare.cloudflared, mở lại cửa sổ rồi chạy lại.' }
    if (-not (Test-Path -LiteralPath $TokenFile -PathType Leaf)) { throw ('Thiếu file token tunnel: ' + $TokenFile + '. Xem docs/deploy/CLOUDFLARE-TUNNEL.md, bước lưu token.') }
    $token = ([IO.File]::ReadAllText($TokenFile)).Trim()
    if ($token -notmatch '^[A-Za-z0-9_=+/.-]{20,}$') { throw ('File token ' + $TokenFile + ' không chứa token hợp lệ (chỉ dán chuỗi token, không dán cả lệnh).') }
    $previous = [Environment]::GetEnvironmentVariable('TUNNEL_TOKEN', 'Process')
    try {
        # cloudflared reads TUNNEL_TOKEN: the token never appears on a command line or in the process list.
        [Environment]::SetEnvironmentVariable('TUNNEL_TOKEN', $token, 'Process')
        $connector = Start-Process -FilePath $exe -ArgumentList 'tunnel --no-autoupdate run' -WindowStyle Minimized -PassThru
    } finally {
        [Environment]::SetEnvironmentVariable('TUNNEL_TOKEN', $previous, 'Process')
        $token = $null
    }
    Record $connector 'cloudflared'
    Say ('Đã mở connector cloudflared (PID ' + $connector.Id + ', cửa sổ thu nhỏ).') 'Green'
}

try {
    switch ($Action) {
        'Start' {
            $originState = Probe-Origin
            if ($originState -eq 'project') { Say ('Server TOEIC đã chạy tại ' + $origin + ': dùng lại, không mở Server thứ hai.') 'Green' }
            elseif ($originState -eq 'other') { throw ('Cổng ' + $Port + ' đang bị chương trình khác dùng (không phải Server TOEIC). Không dừng chương trình nào; giải phóng cổng hoặc dùng -Port.') }
            else { Start-ProjectServer }
            Start-Connector
            Say 'Kiểm tra qua Internet (tối đa 60 giây)…'
            $until = (Get-Date).AddSeconds(60)
            do { $public = Probe-Public; if ($public.ok) { break }; Start-Sleep -Seconds 3 } while ((Get-Date) -lt $until)
            if ($public.ok) { Say ($public.text) 'Green'; Say ('Client dùng địa chỉ: ' + $PublicUrl) 'Green' }
            else { Say ($public.text) 'Yellow'; Say 'Server vẫn chạy trên máy này; xem docs/deploy/CLOUDFLARE-TUNNEL.md, mục chẩn đoán.' 'Yellow' }
        }
        'Status' {
            $originState = Probe-Origin
            switch ($originState) {
                'project' { Say ('Server: đang chạy tại ' + $origin + '.') 'Green' }
                'other' { Say ('Server: cổng ' + $Port + ' bị chương trình khác dùng.') 'Red' }
                default { Say ('Server: chưa chạy tại ' + $origin + '.') 'Yellow' }
            }
            switch (Connector-State) {
                'service' { Say 'Connector: Windows service cloudflared đang chạy.' 'Green' }
                'process' { Say 'Connector: tiến trình cloudflared đang chạy.' 'Green' }
                default { if (Find-Cloudflared) { Say 'Connector: cloudflared chưa chạy.' 'Yellow' } else { Say 'Connector: chưa cài cloudflared.' 'Yellow' } }
            }
            $public = Probe-Public
            if ($public.ok) { Say ('Internet: ' + $public.text) 'Green' } else { Say ('Internet: ' + $public.text) 'Yellow' }
        }
        'Stop' {
            $entries = @(Read-State)
            $stopped = 0
            foreach ($entry in $entries) {
                $process = Owned-Process $entry
                if (-not $process) { continue }
                # /T stops the Java Server started from that window too; nothing else on the machine is touched.
                & taskkill.exe /PID $process.Id /T /F | Out-Null
                Say ('Đã dừng ' + $entry.role + ' (PID ' + $process.Id + ').')
                $stopped++
            }
            if (Test-Path -LiteralPath $stateFile) { Remove-Item -LiteralPath $stateFile }
            if ($stopped -eq 0) { Say 'Không có tiến trình nào do script này mở đang chạy. Server/connector chạy theo cách khác giữ nguyên.' }
        }
    }
    exit 0
} catch {
    Say ('LỖI: ' + $_.Exception.Message) 'Red'
    exit 1
}
