<#
  Demo EX-06 với dữ liệu MOCK (fixture EX-02 tự tạo, CC0). KHÔNG dùng cho dữ liệu thật.
  Server phải đang chạy với media root trỏ vào fixture:
    $env:TOEIC_AUDIO_ROOT = "<repo>\docs\plan-v2\fixtures\ex02\assets\audio"
    $env:TOEIC_IMAGE_ROOT = "<repo>\docs\plan-v2\fixtures\ex02\assets\images"

  Bước 1 (tạo ca):   .\scripts\demo-ex06-v2.ps1
  Bước 2 (bắt đầu):  .\scripts\demo-ex06-v2.ps1 -Start -SessionId <id in ra ở bước 1>
  Chỉ import đề MOCK (tạo ca + START làm trên UI giám thị V2): .\scripts\demo-ex06-v2.ps1 -ImportOnly

  Trạng thái: luồng tương đương đã chạy bằng curl + test E2E trên Linux; script PowerShell này NOT RUN trên Windows.
#>
[CmdletBinding()]
param(
    [string]$ServerUrl = "http://127.0.0.1:8080",
    [string]$Candidate = "candidate1",
    [string]$Proctor = "proctor1",
    [string]$Password = $env:TOEIC_SEED_PASSWORD,
    [switch]$Start,
    [switch]$ImportOnly,
    [string]$SessionId = ""
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($Password)) { throw "Đặt TOEIC_SEED_PASSWORD (mật khẩu tài khoản MOCK) trước khi chạy." }
$base = $ServerUrl.TrimEnd('/')

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Token, $Body)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers; ContentType = "application/json; charset=utf-8" }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
        $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}

function Get-Login {
    param([string]$User)
    return Invoke-Api POST "/api/v1/auth/login" $null @{ username = $User; password = $Password; requestId = [guid]::NewGuid().ToString() }
}

$proctorLogin = Get-Login $Proctor
$token = $proctorLogin.token

if (-not $Start) {
    $fixture = Join-Path $PSScriptRoot "..\docs\plan-v2\fixtures\ex02\import.mock.json"
    try {
        $imported = Invoke-Api POST "/api/v2/exams/import" $token ([System.IO.File]::ReadAllText((Resolve-Path $fixture), [System.Text.Encoding]::UTF8))
        Write-Host "Đã import đề MOCK: $($imported.examId) ($($imported.totalQuestions) câu)"
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -eq 409) { Write-Host "Đề MOCK đã có trong DB (409 CONFLICT) - dùng lại." } else { throw }
    }
    if ($ImportOnly) {
        Write-Host "Xong. Tạo ca và START trên client giám thị: Điều khiển ca → tab 'Ca PROCTORED_V2'."
        return
    }
    $catalog = Invoke-Api GET "/api/v2/exams" $token $null
    $item = $catalog.items | Where-Object { $_.examId -eq "mock-ex02-200" }
    if (-not $item -or -not $item.selectable) { throw "Đề MOCK chưa selectable; kiểm TOEIC_AUDIO_ROOT/TOEIC_IMAGE_ROOT của server." }
    $candidateId = (Get-Login $Candidate).user.id
    $session = Invoke-Api POST "/api/v2/sessions" $token @{
        examId = "mock-ex02-200"; durationMinutes = 120
        candidateUserIds = @($candidateId); proctorUserIds = @($proctorLogin.user.id)
    }
    Write-Host ""
    Write-Host "Ca PROCTORED_V2: $($session.sessionId)"
    Write-Host "Lượt thi của ${Candidate}: $($session.attempts[0].attemptId)"
    Write-Host "Bây giờ mở client, đăng nhập $Candidate, chọn lượt thi trên rồi bấm vào phòng thi."
    Write-Host "Khi lobby báo đủ Audio + Ảnh READY, chạy:  .\scripts\demo-ex06-v2.ps1 -Start -SessionId $($session.sessionId)"
    return
}

if ([string]::IsNullOrWhiteSpace($SessionId)) { throw "Cần -SessionId." }
$readiness = Invoke-Api GET "/api/v2/sessions/$SessionId/readiness" $token $null
foreach ($a in $readiness.attempts) {
    Write-Host ("{0}: audio={1} visual={2} {3}" -f $a.attemptId, $a.audioReady, $a.visualReady, ($a.blockedReasons -join ","))
}
if (-not $readiness.allReady) { throw "Chưa đủ READY; server sẽ từ chối START (409). Hoàn tất chuẩn bị trên client rồi chạy lại." }
$manifest = (Invoke-Api GET "/api/v1/sessions/$SessionId/audio/readiness" $token $null).manifestId
$started = Invoke-Api POST "/api/v1/sessions/$SessionId/listening/start" $token @{ requestId = [guid]::NewGuid().ToString(); manifestId = $manifest }
Write-Host "START OK: startId=$($started.startId) startedAt=$($started.startedAt)"
