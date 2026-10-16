# Lệnh bàn giao cho hai máy

Các block dành cho Windows PowerShell; chạy trong checkout nhánh QA. Chỉ dùng host/DB/process TEST do owner cấp. Chưa chạy LAN trong phiên này. Không thay port/firewall/dịch vụ dùng chung. Password nhập ngoài Git; không bật transcript khi nhập hoặc in environment/token. Binary/raw ở ignored, không commit.

## A — bundle và cấu hình server TEST

MSI local: `jpackage-out\pr39-qa-20261016\release-0.3.7\ToeicMonitor-0.3.7.msi`; chuyển **cả MSI + .sha256 + build-info.json** sang thư mục TEST máy B qua kênh owner cho phép. Chuyển server JAR đã build từ cùng source sang A; không cần server trên B. Trên cả hai máy:

```powershell
$msi = (Resolve-Path '.\ToeicMonitor-0.3.7.msi').Path
$info = Get-Content '.\build-info.json' -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash.ToLowerInvariant() -ne $info.installerSha256) { throw 'MSI HASH MISMATCH' }
if ($info.sourceCommit -ne '9bfc75ec7c2d91af6f714602727389547c3b3288' -or $info.dirty) { throw 'SOURCE MISMATCH' }
```

A có PostgreSQL TEST riêng: owner tạo **database mới** `qa_lan_<run>` bằng tài khoản được cấp; không dùng database ứng dụng đang chạy, không reset volume/schema public. Xác minh tên/host DB trước khởi động. `CREATE DATABASE` chỉ cho database mới, không có lệnh DROP trong runbook.

```powershell
$run = [Guid]::NewGuid().ToString('N').Substring(0,12)
$raw = Join-Path (Get-Location) ('jpackage-out\lan-owner-'+$run)
$data = Join-Path $raw 'test-data'
.\evidence\b-pr39-acceptance-20261016\lan\New-LanTestData.ps1 -OutputDirectory $data
$manifest = Get-Content (Join-Path $data 'test-data.json') -Raw | ConvertFrom-Json
$env:DB_HOST = Read-Host 'PostgreSQL TEST host'
$env:DB_PORT = Read-Host 'PostgreSQL TEST port'
$env:DB_NAME = Read-Host 'New dedicated qa_lan_ database name already created'
if ($env:DB_NAME -notmatch '^qa_lan_[a-z0-9_]+$') { throw 'Dedicated TEST DB required' }
$dbc = Get-Credential -Message 'Dedicated TEST PostgreSQL account'
$env:DB_USER = $dbc.UserName
$env:DB_PASSWORD = $dbc.GetNetworkCredential().Password
$seed = Get-Credential -UserName 'TEST-seed-password' -Message 'Password for TEST seeded accounts; share privately with B'
$env:TOEIC_SEED_PASSWORD = $seed.GetNetworkCredential().Password
$env:TOEIC_AUDIO_ROOT = (Resolve-Path $data).Path
$env:TOEIC_SERVER_URL = 'http://' + (Read-Host 'Authorized IPv4 LAN address of A') + ':18080'
# Giữ terminal này: Ctrl+C chỉ dừng server TEST do lệnh này tạo.
$jdk = Read-Host 'Absolute path to JDK21 on machine A'
& (Join-Path $jdk 'bin\java.exe') '-Duser.timezone=UTC' -jar .\server\target\server-0.1.0-SNAPSHOT.jar --server.address=0.0.0.0 --server.port=18080 --toeic.state.delta-enabled=false
```

Chỉ máy A cần JDK21 để chạy server; nhập đường dẫn JDK21 thực của máy A. Audio root có `<examId>/qa-listening.wav`, JSON import ở `<data>/exam.json`. Đề và WAV là TEST tổng hợp, không dùng fixture để kết luận chất lượng nội dung TOEIC thật.

## A và B — cài user scope, kiểm listener, khởi động EXE

Kiểm Add/Remove Programs trước: nếu có ToeicMonitor, giữ nguyên và dùng user/máy TEST khác. Chạy MSI bằng Explorer với tài khoản thường; chọn thư mục riêng có dấu/khoảng trắng, lưu ProductCode/path/hash vào raw **riêng**. Không chạy script unattended thay thế bản cài đang có. MSI đã có runtime, B không cần JDK.

```powershell
$server = Read-Host 'Same http://A-LAN-IP:18080 on A and B'
$uri = [Uri]$server
Test-NetConnection -ComputerName $uri.Host -Port $uri.Port
$install = Read-Host 'Exact owned TEST installation directory'
.\scripts\smoke-lan-preflight.ps1 -ServerUrl $server -ClientJar (Join-Path $install 'app\client-0.1.0-SNAPSHOT-all.jar') -DeviceLabel 'B' |
  Out-File '.\lan-preflight-B.json' -Encoding utf8
# Trên A dùng DeviceLabel A và file riêng A; chỉ HTTP401 chứng minh auth gate reachable, chưa chứng minh DB healthy.
$info = Get-Content (Join-Path $install 'app\build-info.json') -Raw | ConvertFrom-Json
if ((Get-FileHash (Join-Path $install 'app\client-0.1.0-SNAPSHOT-all.jar')).Hash.ToLowerInvariant() -ne $info.clientJarSha256) { throw 'JAR HASH MISMATCH' }
$env:TOEIC_SERVER_URL = $server
& (Join-Path $install 'ToeicMonitor.exe')
```

Thứ tự: A mở Proctor `proctor1` và Candidate1 `candidate1` ở hai instance; B mở Candidate2 `candidate2`; password TEST nhập UI riêng. Proctor import `exam.json`, tạo ca theo `test-data.json.sessionId`, examId đúng manifest, candidate usernames `candidate1,candidate2`, proctor `proctor1`, duration1800. Candidate bấm làm mới ca, vào bài; tải/thử loa **nghe thật** rồi READY. Proctor tải READY và xác nhận start trong5giây. Chạy checklist S1–S7; lưu trước/sau attempt cũ khi reorg và writer takeover.

## Thu evidence và kết thúc

Trên mỗi máy tạo raw folder theo runId và A/B; ghi UTC/source/hash/OS/operator/topology, screenshots đã lọc PII, status/HTTP/DB có scope. Không ghi mật khẩu/token, không public PID/process table. Không suy one-way latency từ hai clock chưa đồng bộ. Không chỉnh raw sau khi hash.

Chỉ sau khi xác minh bản cài TEST và ProductCode tương ứng, owner gỡ bản đó trong Settings/Apps hoặc `msiexec /x {EXACT-OWNED-TEST-PRODUCTCODE} /qn /norestart /l*v <OWNED-RAW-UNINSTALL-LOG>`. Không xóa registry/cache/journal thủ công. Đóng app TEST, Ctrl+C đúng server terminal, kiểm owned children/resources. Nếu uninstall fail, giữ raw và ProductCode riêng để xử lý; không xóa bừa.

Điền actual vào TEST_RESULTS; A review QD29/progress/transaction, C review FULL/epoch/gaps/E1-method, B ký nghe/layout/cài-gỡ/LAN khi thực sự thao tác. Giữ PR39 Draft cho đến quyết định owner.
