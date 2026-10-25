# Báo cáo phát hành — TOEIC Monitor (release candidate)

Ngày 25/10/2026. Nhánh `integrate/final-toeic-release`, PR vào `main` để owner review. **Chưa merge main, chưa gắn tag release.**

**Trạng thái: CONDITIONALLY READY.** Code release đạt full regression và các gate tự động bắt buộc; còn ba phần nghiệm thu thủ công NOT TESTED trên bản tích hợp (LAN hai máy, nghe audio bằng người, cắm/rút USB vật lý trên chính commit này) và một test monitoring tích hợp chập chờn đã biết (mục 6).

## 1. Commit

| Mục | SHA |
|---|---|
| Base `origin/main` | `acc1f82` (Merge PR #106) |
| **Code đã kiểm thử (full regression)** | **`f982aac`** |
| Bundle portable (`sourceCommit`) | `5a1af3e` — so với `f982aac` chỉ khác tài liệu, launcher/đóng gói và một test chẩn đoán; không đổi code production Java |
| Checkpoint ACCOUNT_PIN được tích hợp | `ff6aacf` |
| Nhánh USB/monitoring của Công Lâm | tới `e5c783f` |
| Backup trước tích hợp | tag `backup/account-pin-ff6aacf-20261025`, `backup/main-acc1f82-20261025`; ref `refs/backup/account-pin-wip-20261025` |

Commit sau `f982aac`: `3fc6180` (tài liệu), `15384ed` (sửa smoke script), `ba4725f` (chẩn đoán trong test, assertion không đổi), `5a1af3e` (mã thoát và thông báo lỗi của launcher), cùng các commit tài liệu của báo cáo này. `git diff f982aac HEAD` trên `*.java` chỉ có `MonitoringTimelinePostgresTest.java`.

## 2. Nội dung đã tích hợp

| Nguồn | Tác giả | Cách đưa vào | Nội dung |
|---|---|---|---|
| `feature/account-pin-20261024` `ff6aacf` | AnhTaizz | merge `f0fd664` | Admin quản lý tài khoản (V20), phòng PIN chung 5 số ACCOUNT_PIN (V19, V21), START khi chưa đủ READY, evidence |
| `feat/usb-app-integration-20261024` tới `e5c783f` | Công Lâm | merge `09e0f3f`, `161c586`, `f982aac` | Module `windows-observer` (CIM), USB/focus/cửa sổ trong luồng thi, tóm tắt tín hiệu cho giám thị, `process-policy-v4` (9 tên), USB có sẵn khi bắt đầu hiển thị riêng |
| `test/a-qa01-regression-20261024` `682358e` | Djicz | cherry-pick `e188153` rồi revert `5c7a7b9` | Bị ACCOUNT_PIN thay thế (xem mục 3). PR #107 giữ riêng |
| Tích hợp phát hành | AnhTaizz | `cbbd251`, `dd00e1a`, `17f94c6`, `05822d8`, `5a1af3e` | Ẩn lối vào legacy sau cờ `TOEIC_UI_LEGACY`; launcher portable; bật USB/tín hiệu trong launcher; kiểm USB trong E2E ACCOUNT_PIN; tài liệu |

Không tích hợp: các nhánh cũ đã nằm trong main hoặc bị thay thế (`feat/javafx-exam-client-20261013`, `test/javafx-full-ui-testing`, `feat/t1-b4-…`, `feat/c-t1-c1-…`, `feat/t3-b3-…`, `integrate/ui-and-test-fixes-20261021`), nhánh chỉ có tài liệu review (`review/*-20261018`, `docs/stage3-reconciliation-20261018`), `feat/ex01-html-pipeline-20261020` (công cụ dữ liệu, thay bằng adapter STUDY4 EX-08 đã có trên main), `feat/javafx-listening-part3-20261019` (giao diện Part 3 cho luồng V1 legacy).

## 3. Conflict và cách xử lý

| Vị trí | Cách xử lý |
|---|---|
| `.env.example`, `.gitattributes`, `scripts/start-monitoring-server.ps1` (ACCOUNT_PIN × main) | Giữ cả hai phía |
| `ProctorV2SessionPaneGuiTest` (QA01 × ACCOUNT_PIN, rồi USB × ACCOUNT_PIN) | Không conflict văn bản nhưng **xung đột ngữ nghĩa**: fix chờ `#v2-candidates` là `TextField`, trên ACCOUNT_PIN node này là `TableView` và test đã chờ danh sách thí sinh. Lượt full `dd00e1a` FAIL → revert; ở merge USB giữ bản ACCOUNT_PIN |
| `CandidateManagementGuiPostgresTest` | Lượt full `5c7a7b9` 2 ERROR do ẩn tab V2 cũ (lỗi của bước ẩn legacy) → test bật cờ legacy tường minh |
| `MonitoringTimelinePostgresTest` | Sau ACCOUNT_PIN, thí sinh đăng nhập vào màn PIN trước; test bật cờ legacy và đi qua "Ca thi được phân công trước đây" |
| `README.md` (USB × release) | Giữ README phát hành; phần nhánh USB sửa README cũ được áp vào `docs/archive/README-LICH-SU.md` |
| `docs/PROTOCOL.md`, `docs/NHAT_KY.md`, `docs/TIEN_DO.md` | Giữ mọi mục của hai phía |

`ProctorRedesignView.forLogin` tự merge, đã đọc lại: giữ cả `LegacyUi`/ACCOUNT_PIN và `signalsApi` mới. Không sửa migration cũ; V1–V18 giữ nguyên checksum.

## 4. Kết quả kiểm thử

Môi trường: Windows 11 (NT 10.0.26200), Oracle JDK 21.0.8, Maven 3.9.11, PostgreSQL 18.1 cụm cô lập tự dựng/tự dọn (0 schema còn lại, `pg_ctl stop` = 0 ở mọi lượt dưới đây). Fixture tự soạn ghi `MOCK`; STUDY4 là dữ liệu nội bộ, không phát hành.

### 4.1 Full regression trên `f982aac`

`scripts/verify-room-ux.ps1 -Mode Full -AllGui`, run `room-ux-20261025T091048Z-b3770db6`, 09:10:59–09:38:05 UTC:

| | PASS | FAIL | ERROR | SKIP |
|---|---:|---:|---:|---:|
| protocol + client + server + monitoring-spike | **1791** | 0 | 0 | 23 |
| windows-observer (cùng lượt Maven, runner không tổng hợp) | 22 | 0 | 0 | 0 |

Trong đó PASS: E2E ACCOUNT_PIN `AccountPinFlowPostgresGuiTest` 3/3 (Admin tạo tài khoản → Proctor tạo phòng → 2 Candidate JOIN, READY 2/1/0 → START → Listening/Reading → autosave → reconnect không reset deadline/writer → Submit; truy cập chéo attempt 403; USB CIM thật ghi đúng attempt và không lặp event khi thiết bị không đổi), `AccountPinPostgresTest` 17, `AdminAccountPostgresTest` 21, `AdminAccountGuiTest` 5, `RoomAdmissionPostgresTest` 20, `RoomTokenAuthorizationTest` 15, `ExamRuntimePostgresTest` 38, `ListeningRuntimePostgresTest` 16, `SignalPipelinePostgresTest` 32, `AuthenticatedNetworkTest` 36, `SessionAuthenticationTest` 18, các test policy process v4 (`ProcessCatalogTest` 9, `ProcessTracePolicyCompatibilityTest` 5, `MonitoringStateReducerTest` 6, `FullSnapshotPayloadTest` 6, `DeltaSnapshotPayloadTest` 3).

23 SKIP và lý do: 14 ca monitoring/USB tích hợp chỉ chạy qua runner riêng (mục 4.2); `study4RealTwoHundredQuestionAcceptance` chạy qua runner EX-08 (mục 4.3); 2 ca app đã cài/app-image; `Phase4RoomBenchmarkTest`; 2 ca symlink cần quyền tạo link; `RichImagePostgresTest.realReservedV13…`; `RichAttemptE2eGuiTest` và `ProctorV2E2eGuiTest` cần Server bên ngoài (`TOEIC_INTEGRATION_E2E_URL`).

Lịch sử các lượt trước trên nhánh này (giữ nguyên, không thay cho kết quả trên): `e188153` 1662 PASS/48 SKIP; `dd00e1a` **1 FAIL** (mục 3); `5c7a7b9` **2 ERROR** (mục 3); `17f94c6` 1756 PASS/23 SKIP. Lượt trên `05822d8` bị dừng giữa chừng theo yêu cầu, không có kết quả.

### 4.2 Monitoring/USB — runner của C (`scripts/demo-usb-integrated.ps1`), code `f982aac`

| Chế độ | Ca chạy | Kết quả |
|---|---|---|
| Mock + Timeline + AlertRules + Integrated | 4 ca, gồm `productionObserversRetireOnTerminalAndRevoke`, `applicationControlsStopRestartLogoutAndSwitchAccount` | Lượt đầu **1 FAIL** (retire, SUBMIT); xem mục 6 |
| Mock + Timeline | `mixedMockDeliveryReachesTwoAttemptDashboard` | 1/1 PASS |
| Mock + Timeline + AlertRules | `experimentalAlertsReachDashboard` | 1/1 PASS |
| Mock | `UsbIntegratedDemoPostgresTest` 4 ca | 4/4 PASS |
| Real CIM | `realCimReadinessCommitAckAndHistory` | 1/1 PASS |
| Real CIM + Timeline | `manualMonitoringStartsUsbFocusWindowWithProcess`, `productionUsbObserverReachesDashboard` | 2/2 PASS |

USB trong runner là MOCK hoặc CIM thật không thay đổi thiết bị (máy kiểm thử thấy 0 USB). `realCimStatusReachesDashboard` không được chế độ nào của runner chọn: **NOT RUN**. USB có sẵn khi bắt đầu hiển thị "USB có sẵn khi bắt đầu giám sát", khác với "Quan sát cắm USB"/"Quan sát rút USB" (`SignalFeedProjectionTest` 6, `SignalFeedGuiTest` 3 PASS).

### 4.3 Đề thật — EX-08 với STUDY4_6854

`scripts/verify-ex08-postgres.ps1`, run `ex08-20261025T100140Z-43b30551`: **1/1 PASS**. Payload do `tools/study4-adapter` sinh từ `STUDY4_6854`: 200 câu (6/25/39/30/30/16/54), 73 nhóm, 54 audio, 11 ảnh, SHA-256 `d47aad14e86b6929cec0925ce57d78f295872f6d610c82b1715b5a26966e5349`; toàn bộ media tải qua route có xác thực, server chấm bài. Trường `fixture` trong `result.json` của runner là chuỗi cố định "STUDY4_6852"; dataset thực tế xác định bằng `importSha256` ở trên. Không dùng: STUDY4_6852 (thiếu `client_exam.json`), STUDY4_6853 (adapter từ chối đúng: `62-64_updated_resized.jpg` là WebP mang đuôi .jpg). Dữ liệu và media không có trong repository hay bản phát hành.

### 4.4 Bản portable (`5a1af3e`)

| Smoke | Kết quả |
|---|---|
| Client, bản sao trong `Kiểm thử có dấu và khoảng trắng`: URL hợp lệ/không hợp lệ, cửa sổ đăng nhập thật từ runtime đóng gói, thoát sạch, audio JavaFX (`AudioPackageSmoke`) | 9/9 PASS |
| Client, đường dẫn ASCII | 8/8 PASS |
| Server, DB mới, bản sao trong `Kiểm thử bản phát hành`, media trong `Media đề thi`: `server.env`, 5 ca cấu hình sai bị từ chối, V19–V21, Admin bootstrap, cổng bị chiếm, PostgreSQL không kết nối được, dòng `Server READY` | 14/14 PASS |
| Server, **nâng cấp DB do Server main `acc1f82` (V18) tạo** → V21, kèm import STUDY4_6854 từ thư mục media có dấu (200 câu, `VERIFIED_AT_IMPORT`, selectable) và giữ nguyên tài khoản cũ (so digest id/username/role/hash) | 18/18 PASS |
| ZIP giải nén vào `Giải nén`: Client `-ValidateOnly` exit 0; Server thiếu `server.env` exit 1, một dòng `ERROR:` | PASS |

Server và Client chứa cùng bytecode `ProcessCatalog` (policy v1–v4). JAR trong bundle giống từng byte JAR do full regression `f982aac` build.

Chuỗi nâng cấp có dữ liệu: V18→V19 (`RoomAdmissionPostgresTest`: account, token, exam, session, attempt với `answers_json`/`writer_epoch`/`saved_revision`, checksum V1–V18), V19→V20 (`AdminAccountPostgresTest`), V20→V21 (`AccountPinPostgresTest`: account, session, attempt, member, checksum) đều PASS trong 4.1.

### 4.5 Chưa kiểm thử

| Mục | Trạng thái |
|---|---|
| LAN hai máy Windows thật | NOT TESTED |
| Người nghe audio bằng loa/tai nghe | NOT TESTED (audio smoke là xác nhận tự động) |
| Cắm/rút USB vật lý trên commit tích hợp, luồng ACCOUNT_PIN | NOT TESTED (xem mục 5) |
| MSI/installer, máy sạch thứ hai | NOT TESTED |
| Đo lại E1/E2 với `process-policy-v4` | NOT TESTED (kết quả cũ thuộc policy v1) |

## 5. Bằng chứng USB vật lý (từ nhánh của Công Lâm, không phải commit tích hợp)

- `evidence/usb-hardware-20261024/REPORT.md`: 24/10/2026 20:52–20:54 (UTC+7), máy `USB-TEST`, Windows 11, Java 21.0.10, PostgreSQL 18.3, source `acc1f82` (+`AGENTS.md` chưa track). Runner REAL CIM, provider MSFT_DISK: 86 scan, đúng một `USB_STORAGE_CONNECTED` (0→1) lúc 20:53:54.556761, 44 scan sau không lặp; COMMIT/ACK/history PASS. Báo cáo tự ghi: chưa kiểm REMOVED/PRESENT_AT_START, không kiểm GUI.
- `evidence/usb-8080-fix-20261024/REPORT.md`: 24/10/2026, app thật trên nhánh USB (working tree chưa commit, base `acc1f82`), luồng ca V1 với đề MOCK một câu; người dùng xác nhận thấy trên app giám thị `USB_STORAGE_REMOVED` 22:25:23.057301 và `USB_STORAGE_CONNECTED` 22:25:34.612962.

Hai bằng chứng trên chứng minh đường CIM → WebSocket → PostgreSQL → giao diện giám thị với thiết bị thật trên code của C; chúng không chứng nhận commit `f982aac` hay luồng ACCOUNT_PIN. Trên commit tích hợp, USB được kiểm bằng CIM thật không đổi thiết bị và bằng MOCK (mục 4.1, 4.2).

## 6. Hạn chế và vấn đề đã biết

- **Test chập chờn `productionObserversRetireOnTerminalAndRevoke`** (owner C): 3 FAIL / 17 lượt runner trên code `f982aac` (2/13 không tải, 1/4 khi đặt tải CPU nhân tạo). Lượt FAIL có chẩn đoán: session process đã dừng đúng do server trả `FORBIDDEN` sau SUBMIT; observer typed (đóng qua `Platform.runLater`) vẫn `active` khi hết 10 giây chờ, cùng lúc các test GUI khác báo luồng FX bị chậm. Server vẫn từ chối dữ liệu của attempt đã nộp; không thấy sai dữ liệu. Chưa xác định chắc chắn nguyên nhân; không đổi assertion hay thời gian chờ. Commit `ba4725f` thêm thông tin chẩn đoán cho lần lỗi sau.
- Giám sát tiến trình chỉ quan sát đúng 9 tên của `process-policy-v4`; không phải mọi tiến trình Windows. Tiến trình sống ngắn hơn chu kỳ poll có thể bị bỏ lỡ. Server cũ không hiểu v4: phải chạy Server và Client cùng bản.
- USB: chỉ USB lưu trữ, polling có thể bỏ sót cắm/rút quá nhanh; không đọc nội dung/serial, không chặn thiết bị. Lỗi đọc nguồn hiển thị UNKNOWN/ERROR, không được hiểu là "đã rút".
- Giao diện legacy (mã phòng + PIN cá nhân, ca V1, tab V2 cũ) bị ẩn mặc định, backend và test vẫn giữ; `TOEIC_UI_LEGACY=true` chỉ dùng cho hồi quy.
- Bundle chưa ký số. Đóng gói cần Windows SDK `mt.exe` (dùng bản 10.0.26100.0 có chữ ký Microsoft).
- Đề STUDY4 là dữ liệu nội bộ được người cung cấp cho phép dùng trong dự án; không phân phối lại.

## 7. Artifact

| | |
|---|---|
| File | `toeic-monitor-portable-5a1af3e.zip` (66 097 678 byte) |
| SHA-256 | `0a8d5ab55467a6a25fdfe16768f1889aeb5c4c46f9ae9b2fdafe47b8cbd2313d` |
| Nơi lưu bàn giao | `E:\Project\TOEIC-Release\` (chỉ ZIP, `.sha256`, `READ-ME.txt`); sẽ đính kèm GitHub Release khi owner merge và gắn tag |
| Nội dung | `Start-Server.bat`, `Start-Client.bat`, `start-release.ps1`, `server.env.example` (giá trị bí mật để trống), `READ-ME.txt`, `server.jar`, `client.jar`, `runtime\` Java 21, `artifact-manifest.json` (SHA-256 từng file, `containsCredentials=false`, không có patch chưa commit) |

Hướng dẫn chạy: `READ-ME.txt` trong bundle và [../DEMO-RUNBOOK.md](../DEMO-RUNBOOK.md).

## 8. Đề xuất dọn dẹp (chưa thực hiện)

Sau khi owner merge PR: có thể đóng PR #53–#56 (tài liệu review 18/10), #76, #78, #79 (draft EX-06/EX-07 đã nằm trong main); PR #107 (QA01) chờ Djicz quyết định vì fix test trong đó đã bị ACCOUNT_PIN thay thế. Nhánh của thành viên giữ nguyên. Artifact cũ để ngoài thư mục bàn giao, chưa xóa: `C:\Users\nguye\AppData\Local\Temp\toeic-account-pin-verification\distribution\`, `E:\Toeic-Workspace-Backups\` (các bản release trước), bản `3fc6180` tại `E:\Toeic-Workspace-Backups\final-integration-20261025\superseded-artifacts\`. Worktree tạm `E:\Project\toeic-final-verify`, `E:\Project\toeic-main-v18` có thể gỡ bằng `git worktree remove` khi không cần chạy lại.
