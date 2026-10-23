# Handoff ngắn cho B/C — MSI và LAN thật

**Chưa nghiệm thu máy thứ hai.** Main f864034, PR47 HEAD3044e5b chưa merge; C/B review và corrective cache P1 còn thiếu. Các lệnh dưới là chuẩn bị/handoff, **chưa chạy build/install/LAN trong Task2**. Không thao tác DB dev, tắt firewall hoặc xóa profile/journal đang thi.

## 1. Chốt source và build MSI khi gate đã đủ

C xác nhận review đúng HEAD PR47; B xử lý audio publication P1. Đọc lại CI/mergeable/contract và chỉ merge với SHA guard khi đủ điều kiện. Không chạy lệnh build bên dưới như bản final trước đó.

Trên máy build Windows x64: JDK21, Maven, WiX3 candle/light và SDK mt.exe. Hiện JDK/jpackage có; WiX/mt chưa tìm thấy. Cách lấy tool chính thức và hash lịch sử nằm ở [WINDOWS_INSTALLER](../../docs/WINDOWS_INSTALLER.md); không coi hash lịch sử là kiểm chứng tool mới. Không dùng WiX4/5 để né JDK21.

Tạo checkout sạch ngoài mọi target và ghi full final source SHA:
```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'FETCH_FAILED' }
$finalSource=(git rev-parse origin/main).Trim()
git merge-base --is-ancestor 3044e5bc963f7ac9d8cdc4d8410696c301080534 $finalSource
if ($LASTEXITCODE -ne 0) { throw 'PR47_NOT_IN_MAIN_STOP' }
$releaseTree=Read-Host 'Thu muc checkout moi, ngoai target'
if (Test-Path -LiteralPath $releaseTree) { throw 'RELEASE_TREE_EXISTS_STOP' }
git worktree add --detach $releaseTree $finalSource
if ($LASTEXITCODE -ne 0) { throw 'WORKTREE_FAILED' }
Set-Location -LiteralPath $releaseTree
if (git status --porcelain) { throw 'DIRTY_SOURCE_STOP' }
$jdk=Read-Host 'JDK21 x64 directory'
$wix=Read-Host 'WiX3 directory chua candle.exe va light.exe'
$mt=Read-Host 'Duong dan Windows SDK mt.exe'
$releaseVersion=Read-Host 'Version moi chua su dung, vi du 0.3.11'
if ([version]$releaseVersion -le [version]'0.3.10') { throw 'VERSION_MUST_INCREASE' }
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -JavaHome $jdk -WixHome $wix -ManifestTool $mt -AppVersion $releaseVersion -PreflightOnly
if ($LASTEXITCODE -ne 0) { throw 'PREFLIGHT_FAILED_STOP' }
```

Kiểm version chưa dùng trong release hiện hành/branch/tags; script giữ giới hạn MSI255.255.65535 và upgrade UUID, không auto-update. 0.3.11 chỉ là đề xuất kế tiếp0.3.10, không tự cấp version nếu người khác đã phát hành. Package clean protocol/client, skipTests: không gọi đây là lượt test.

```powershell
$packageOutput=@(& powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -JavaHome $jdk -WixHome $wix -ManifestTool $mt -AppVersion $releaseVersion)
if ($LASTEXITCODE -ne 0) { throw 'PACKAGE_FAILED_STOP' }
$msi=[string]$packageOutput[-1]
if (-not (Test-Path -LiteralPath $msi -PathType Leaf)) { throw 'MSI_NOT_FOUND' }
$releaseDir=Split-Path -Parent $msi
$metadata=Get-Content -LiteralPath (Join-Path $releaseDir 'build-info.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($metadata.sourceCommit -cne $finalSource -or $metadata.dirty -or $metadata.version -ne $releaseVersion -or $metadata.monitoringDefault -ne 'FULL') { throw 'METADATA_MISMATCH_STOP' }
if ((Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash.ToLowerInvariant() -cne $metadata.installerSha256) { throw 'MSI_HASH_MISMATCH' }
$lines=@()
foreach ($name in @((Split-Path -Leaf $msi),'build-info.json')) {
    $hash=(Get-FileHash -LiteralPath (Join-Path $releaseDir $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    $lines+=('{0}  {1}' -f $hash,$name)
}
[IO.File]::WriteAllLines((Join-Path $releaseDir 'SHA256SUMS.txt'),[string[]]$lines,[Text.Encoding]::ASCII)
```

Giữ build-info ngoài MSI và trong image; script kiểm JavaFX Media/Controls, natives glass/jfxmedia, lọc TEST agent/.env và SHA JAR. Dùng image của đúng package (không app-image cũ) để kiểm runtime/release21; mt.exe trích manifest EXE bằng -inputresource:<exe>;#1 rồi xác nhận UTF-8/asInvoker/DPI. Đây là kiểm build, vẫn cần MSI thực cài ra đúng byte để nghiệm thu.

Installed smoke có sẵn chỉ chạy bằng user thường, **DB PostgreSQL TEST riêng đã cấp** (cấu hình process, không dựa nhầm .env dev), chưa cài ToeicMonitor trên user đó:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-windows-installer.ps1 -Installer $msi -JavaHome $jdk -FullSuite
if ($LASTEXITCODE -ne 0) { throw 'INSTALLED_SMOKE_FAILED_KEEP_LOGS' }
```
Đọc script trước: có thể đọc .env; checkout release mới không copy .env dev sang. Ghi source/version/manifest/hash/build-info/install log và expected/actual. Giữ MSI0.3.10, không ghi đè. Audio silent fixture/auto-confirm trong automation là MOCK/SIMULATED; không thay người nghe.

## 2. Máy Windows B khác thật — MSI trước, không cần repository/JDK/IDE

Nhận MSI, build-info.json và SHA256SUMS cùng hash độc lập từ C/người giữ build. Khi chưa có final artifact, giữ SECOND_MACHINE_NOT_RUN, không cài app-image để đánh PASS.

```powershell
$msi=Read-Host 'Duong dan MSI da nhan'
$expectedHash=Read-Host 'SHA256 doc lap do C cung cap'
$expectedSource=Read-Host 'Full final main SHA da duoc C xac nhan'
if ((Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expectedHash.ToLowerInvariant()) { throw 'HASH_MISMATCH_STOP' }
$meta=Get-Content -LiteralPath (Join-Path (Split-Path $msi) 'build-info.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($meta.sourceCommit -cne $expectedSource -or $meta.dirty -or $meta.monitoringDefault -ne 'FULL') { throw 'SOURCE_MISMATCH_STOP' }
where.exe java
# Neu khong co Java tren PATH, ghi quan sat do; can ghi ro may co JDK/IDE hay khong.
# Cai MSI bang tai khoan thuong qua giao dien, duong dan co dau/space.
```

B điền từng mục với SHA, alias máy, OS, người chạy, UTC+7, expected/actual, evidence và nhãn REAL/MOCK/SIMULATED. Mặc định mọi mục dưới **NOT RUN**:
1. Hash MSI/metadata/SHA256SUMS khớp nguồn tin độc lập; user thường cài được, không cần IDE/JDK ngoài.
2. Đường dẫn cài có dấu/khoảng trắng; child JVM dùng runtime/bin/server/jvm.dll trong install, không dựa Java máy build.
3. Login CANDIDATE và PROCTOR qua IP LAN thật. Quyền server/assignment đúng.
4. Reading: mở đề, chọn/lưu/nộp, đối chiếu đáp án đã ACK và kết quả.
5. Listening: audio đúng checksum, thử loa **người thật nghe**, READY/start; lặp start không replay.
6. Mất LAN vật lý lúc đang phát: audio dừng/khóa; reconnect/relaunch không autoplay/resume/replay; cờ server và answers đã ACK giữ.
7. Monitoring **FULL**, proctor thấy state/presence/history đúng; proctor không chạy collector.
8. Cache ngoài install dưới LOCALAPPDATA; không xóa journal để nghe lại, không ghi install directory.
9. Đóng app hết wrapper/JVM/worker; uninstall gỡ đúng bản TEST, không xóa DB hoặc journal người dùng. Ghi file còn lại/thất bại nếu có.

Đối chiếu [ma trận B](../../docs/ACCEPTANCE_B_MANUAL_MATRIX.md). Nếu dùng chính máy B cho kit PR52 cần JDK21, hoàn tất và ghi bằng chứng MSI không phụ thuộc JDK **trước** khi bổ sung tool TEST được owner cho phép; ghi thay đổi môi trường rõ ràng. Kit dùng JAR/harness có JDK không thay proof MSI máy không JDK.

## 3. Reuse PR52: nhận artifact, preflight A/B, nhóm F

Runbook duy nhất: [PR52 T3_C4_PHYSICAL_LAN_ACCEPTANCE](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/c58931e7567b4ff21315a5de3d93efb03d9c0581/docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md).
[41 cases](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/c58931e7567b4ff21315a5de3d93efb03d9c0581/docs/templates/t3c4-lan/cases.csv) và9 scripts/4 templates giữ nguyên; không tạo lại checklist/kit. Source3044e5b, runtime3d93820, manifest hash613b72d4987d08da18c272ddeaab12c1afa3f01c3d1dd6f05a644ff8a5aa1730.

ZIP chưa tìm lại; hỏi người giữ build/copy riêng. **Không chạy bước còn lại khi thiếu ZIP đúng hash.** Trên cả A và B:
```powershell
$zip=Read-Host 'ZIP PR52 nguyen ban da nhan'
$expectedZip='5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a'
if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expectedZip) { throw 'ZIP_MISMATCH_STOP' }
```

Tiếp tục đúng lệnh extract/Get-LanBundle/Initialize/Test-T3C4LanPreflight/Start-T3C4LanClient của runbook. Không sửa ZIP/runbook trong ZIP để giữ hash cũ. Chạy FULL trước, DELTA experimental ở lượt riêng. Máy A: Spring + PostgreSQL TEST + proctor + candidate A; máy B: candidate B. DB chỉ A; B không nhận credential DB. Không dùng wrapper tự đọc .env dev để chạy server TEST; runbook cấu hình process TEST và gọi Java trực tiếp.

F bắt buộc: trước mất mạng ghi state/answers đã commit cả A/B; B rút LAN hoặc owner tắt Wi-Fi thực và loại route dự phòng; A tiếp tục save, proctor thấy B UNKNOWN/STALE và giữ last-known. B nối lại: ghi re-auth/OPEN/epoch/FULL mới; không false ACK, không mất answer đã ACK; Reading và Listening chạy hai attempt mới riêng; Listening không tự phát lại. F12/G kiểm stop/logout/worker/CLOSE và cleanup đúng PID/server/schema/audio của lượt sở hữu. Giữ checkpoint UI/HTTP/WS/SQL theo runbook; không trừ thời gian giữa hai máy chưa đồng bộ để suy latency.

Mỗi case phải có source/runtime/ZIP/JAR SHA, alias A/B, người thực hiện, thời điểm, expected/actual và nhãn. Fault console/probe là MOCK/SIMULATED dù transport LAN thật, không thay F vật lý. Người B/C tự ký review.json; chưa chạy giữ **0 PASS /0 FAIL /41 NOT RUN**, B/C PENDING.

## 4. Owner C5 và PR52 sequencing

Owner PR51 có thể lấy [patch C5](C5_PR51.patch) sau khi kiểm HEAD vẫn b057b0f và worktree sạch. Kiểm lại bằng git apply --check trước apply; không force-push, không ghi đè thay đổi mới. Patch chỉ chỉnh báo cáo hiện có; B review mới được ACCEPTED.

PR52 chỉ retarget main sau PR47 MERGED; kiểm main có ancestor3044, diff chỉ handoff, CI đúng HEAD và runbook/artifact tương ứng final source. Chưa xảy ra nên giữ base hiện tại. Đề xuất FULL/DELTA nằm ở [decision draft](MODE_DECISION_DRAFT.md), C xác nhận và B review; không agent approval.
