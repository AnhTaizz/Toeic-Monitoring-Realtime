# Báo cáo phát hành — TOEIC Monitor (release candidate)

Ngày 25/10/2026. Nhánh `integrate/final-toeic-release`, PR #108 vào `main` để owner review. **Chưa merge main, chưa gắn tag release.**

**Trạng thái đề xuất: READY FOR MERGE** (owner quyết định). Hai điểm của review cuối trên `5a93b6d` đã sửa (mục 2) và mọi gate tự động bắt buộc đạt trên commit code cuối `5173462` (mục 5). Phần nghiệm thu thủ công vẫn NOT TESTED trên bản tích hợp, như trước: LAN hai máy, người nghe audio, cắm/rút USB vật lý trên chính commit này (mục 5.6). Hạn chế còn lại ghi ở mục 7.

## 1. Commit

| Mục | SHA |
|---|---|
| Base `origin/main` | `acc1f82` (Merge PR #106) |
| HEAD được review cuối | `5a93b6d` |
| Sửa review: dừng giám sát | `8bea80e` |
| Sửa review: tài khoản demo | `5173462` |
| **Code đã kiểm thử (full regression) và `sourceCommit` của bundle** | **`5173462`** |
| Code của lượt release candidate trước | `f982aac` (bundle `5a1af3e`) |
| Checkpoint ACCOUNT_PIN được tích hợp | `ff6aacf` |
| Nhánh USB/monitoring của Công Lâm | tới `e5c783f` |
| Backup trước tích hợp | tag `backup/account-pin-ff6aacf-20261025`, `backup/main-acc1f82-20261025`; ref `refs/backup/account-pin-wip-20261025` |

Sau `5173462` chỉ có commit tài liệu của báo cáo này.

## 2. Sửa theo review cuối PR #108

### 2.1 Dừng giám sát khi quyền giám sát chấm dứt (`8bea80e`)

**Nguyên nhân đã xác định.** Sau SUBMIT, TIMEOUT hoặc token bị thu hồi, Server từ chối message giám sát kế tiếp. FULL snapshot (làm mới mỗi 2 s) và HEARTBEAT (mỗi 2 s) chạy độc lập. Nếu FULL bị từ chối trước, process session dừng và nhả binding heartbeat, nên không còn heartbeat nào bị từ chối; `RealtimeClient` chỉ phát `MonitoringAccessRejection` cho heartbeat bị từ chối. Kênh tín hiệu typed (focus/cửa sổ/màn hình/USB) vì vậy không nhận được tín hiệu dừng: trong app nó chỉ đóng khi callback trạng thái process (qua `Platform.runLater`) được JavaFX xử lý; trong test nó không bao giờ đóng. Đây là thiếu tín hiệu, không phải chỉ do JavaFX bận. Bằng chứng: trên code cũ `5a93b6d`, các lượt FAIL ghi `process=FORBIDDEN`, `typed active=true`, `connection=CONNECTED` và `stoppedWithinExtra30s=false` (vẫn active 30 s sau khi hết hạn chờ 10 s).

**Cách sửa.**
- `RealtimeClient`: Server trả FORBIDDEN/UNAUTHORIZED cho request của làn process (`MONITORING_FULL/DELTA/SYNC_*`, `PROCESS_OBSERVED`, `MONITORING_GAP`) thuộc **đúng collector đang bind** cũng là một access rejection, giống heartbeat bị từ chối. Collector cũ hoặc lease đã nhả không dừng được phiên mới. Không đổi wire/message.
- `CandidateExamSignalMonitoring.close()` an toàn trên mọi luồng: run, delivery, status emitter, USB (`requestStop`, không chờ) và HTTP client dừng ngay trên luồng gọi; chỉ việc gỡ listener/timeline JavaFX được đưa sang luồng FX (cho đến lúc đó adapter thấy run đã đóng và bỏ input). Callback rejection và `FAILED` gọi `close()` trực tiếp. `close()` không khóa đối tượng, tránh deadlock thứ tự khóa với `configureUsb` (khóa coordinator rồi hỏi transport). Một `configureActivity` chạy muộn trên FX sau khi run đã đóng được bỏ qua an toàn.
- Không tăng timeout, không đổi assertion. Test tích hợp nay **giữ luồng JavaFX bị chặn suốt thao tác SUBMIT/TIMEOUT/revoke**, nên mọi phụ thuộc vào FX sẽ FAIL ở mọi lượt.

**Kiểm chứng.**

| Kiểm thử | Kết quả |
|---|---|
| `RealtimeClientTest#processFullDenialOfCurrentCollectorIsMonitoringRejectionButOldCollectorIsNot` | FAIL trên code cũ (không có rejection), PASS sau sửa |
| `CandidateExamSignalCloseGuiTest` (đóng từ luồng không phải FX khi luồng FX bị chặn) | PASS. Ghi chú: `close()` cũ gọi trực tiếp cũng qua test này; test chỉ chứng minh `close()` mới không treo/không lỗi ngoài FX, còn bằng chứng phân biệt là hai dòng trên và dưới |
| `productionObserversRetireOnTerminalAndRevoke`, runner Integrated, `8bea80e` | 13/13 PASS |
| như trên, `5173462`, 14 lượt không tải + 6 lượt tải CPU nhân tạo | **20/20 PASS** |
| như trên, **code cũ `5a93b6d`**, cùng 6 lượt tải CPU | **3/6 FAIL** (nguyên nhân như trên) |
| Module client (realtime + monitoring) | 414/414 PASS |

### 2.2 Tài khoản demo chỉ tạo khi bật tường minh (`5173462`)

- `toeic.seed.demo-accounts` (`TOEIC_SEED_DEMO_ACCOUNTS`) mặc định `false`. Server không còn mật khẩu seed mặc định (`ChangeMe123!` đã bỏ khỏi `application.properties`); bật cờ mà thiếu `TOEIC_SEED_PASSWORD` thì Server dừng khi khởi động, không tạo gì. `createIfAbsent` không đổi hay xóa tài khoản đã có. Admin bootstrap không đổi.
- `start-release.ps1` không còn bắt buộc `TOEIC_SEED_PASSWORD` và luôn truyền `--toeic.seed.demo-accounts=false`, nên biến môi trường còn sót không bật được tài khoản demo trong bản phát hành. `server.env.example`, `READ-ME.txt`, README, DEMO-RUNBOOK cập nhật: DB mới chỉ có Admin bootstrap; Admin tạo Giám thị/Thí sinh.
- Test fixture bật cờ tường minh: system property surefire trong `server/pom.xml` (chỉ JVM test), server con của `Phase4RoomBenchmarkTest`, allowlist `.env` của các script dev, `.env.example`, phiên desktop TEST.

| Kiểm thử | Kết quả |
|---|---|
| `SeedDataInitializerTest` (mặc định tắt; bật mà thiếu mật khẩu → lỗi, không insert; bật → chỉ `createIfAbsent`) | 3/3 PASS |
| `SeedAccountsPostgresTest`: cùng một schema khởi động 4 lần — mặc định phát hành (chỉ Admin, `proctor1` 401) → bật cờ (tạo 3 tài khoản) → tắt lại (digest id/username/role/enabled/hash không đổi, vẫn đăng nhập được) → bật với mật khẩu khác (digest không đổi, mật khẩu mới 401) | 1/1 PASS |
| Fixture dùng tài khoản demo: `AccountPinPostgresTest` 17, `AdminAccountPostgresTest` 21, `CandidateManagementPostgresTest` 26, `RoomAdmissionPostgresTest` 20, `AdminBootstrapTest` 4, `SessionAuthenticationTest` 18 | 110/110 PASS (run `room-ux-20261025T114042Z-cc921bf0`, working tree trùng nội dung `5173462`) |
| Smoke Server phát hành, DB mới, có `TOEIC_SEED_DEMO_ACCOUNTS=true` cố ý để sót trong môi trường | `fresh-release-database-has-no-demo-accounts` PASS: chỉ `release-admin:ADMIN`, `proctor1` 401 |
| Smoke nâng cấp DB do Server main V18 tạo | `upgrade-keeps-existing-demo-accounts` PASS và `upgrade-preserves-existing-accounts` PASS (digest không đổi) |

## 3. Nội dung đã tích hợp

| Nguồn | Tác giả | Cách đưa vào | Nội dung |
|---|---|---|---|
| `feature/account-pin-20261024` `ff6aacf` | AnhTaizz | merge `f0fd664` | Admin quản lý tài khoản (V20), phòng PIN chung 5 số ACCOUNT_PIN (V19, V21), START khi chưa đủ READY, evidence |
| `feat/usb-app-integration-20261024` tới `e5c783f` | Công Lâm | merge `09e0f3f`, `161c586`, `f982aac` | Module `windows-observer` (CIM), USB/focus/cửa sổ trong luồng thi, tóm tắt tín hiệu cho giám thị, `process-policy-v4` (9 tên), USB có sẵn khi bắt đầu hiển thị riêng |
| `test/a-qa01-regression-20261024` `682358e` | Djicz | cherry-pick `e188153` rồi revert `5c7a7b9` | Bị ACCOUNT_PIN thay thế (xem mục 4). PR #107 giữ riêng |
| Tích hợp phát hành | AnhTaizz | `cbbd251`, `dd00e1a`, `17f94c6`, `05822d8`, `5a1af3e`, `8bea80e`, `5173462` | Ẩn lối vào legacy sau cờ `TOEIC_UI_LEGACY`; launcher portable; bật USB/tín hiệu trong launcher; kiểm USB trong E2E ACCOUNT_PIN; hai sửa review cuối; tài liệu |

Không tích hợp: các nhánh cũ đã nằm trong main hoặc bị thay thế (`feat/javafx-exam-client-20261013`, `test/javafx-full-ui-testing`, `feat/t1-b4-…`, `feat/c-t1-c1-…`, `feat/t3-b3-…`, `integrate/ui-and-test-fixes-20261021`), nhánh chỉ có tài liệu review (`review/*-20261018`, `docs/stage3-reconciliation-20261018`), `feat/ex01-html-pipeline-20261020` (công cụ dữ liệu, thay bằng adapter STUDY4 EX-08 đã có trên main), `feat/javafx-listening-part3-20261019` (giao diện Part 3 cho luồng V1 legacy).

Hai sửa review chạm phần của vai khác: `RealtimeClient`/`CandidateExamSignalMonitoring` (B/C, contract rejection phía client — không đổi wire), `SeedDataInitializer`/launcher (A/B). Cần owner tương ứng review.

## 4. Conflict và cách xử lý

| Vị trí | Cách xử lý |
|---|---|
| `.env.example`, `.gitattributes`, `scripts/start-monitoring-server.ps1` (ACCOUNT_PIN × main) | Giữ cả hai phía |
| `ProctorV2SessionPaneGuiTest` (QA01 × ACCOUNT_PIN, rồi USB × ACCOUNT_PIN) | Không conflict văn bản nhưng **xung đột ngữ nghĩa**: fix chờ `#v2-candidates` là `TextField`, trên ACCOUNT_PIN node này là `TableView` và test đã chờ danh sách thí sinh. Lượt full `dd00e1a` FAIL → revert; ở merge USB giữ bản ACCOUNT_PIN |
| `CandidateManagementGuiPostgresTest` | Lượt full `5c7a7b9` 2 ERROR do ẩn tab V2 cũ (lỗi của bước ẩn legacy) → test bật cờ legacy tường minh |
| `MonitoringTimelinePostgresTest` | Sau ACCOUNT_PIN, thí sinh đăng nhập vào màn PIN trước; test bật cờ legacy và đi qua "Ca thi được phân công trước đây" |
| `README.md` (USB × release) | Giữ README phát hành; phần nhánh USB sửa README cũ được áp vào `docs/archive/README-LICH-SU.md` |
| `docs/PROTOCOL.md`, `docs/NHAT_KY.md`, `docs/TIEN_DO.md` | Giữ mọi mục của hai phía |

`ProctorRedesignView.forLogin` tự merge, đã đọc lại: giữ cả `LegacyUi`/ACCOUNT_PIN và `signalsApi` mới. Không sửa migration cũ; V1–V18 giữ nguyên checksum.

## 5. Kết quả kiểm thử trên `5173462`

Môi trường: Windows 11 (NT 10.0.26200), Oracle JDK 21.0.8, Maven 3.9.11, PostgreSQL 18.1 cụm cô lập tự dựng/tự dọn (0 schema còn lại, `pg_ctl stop` = 0 ở mọi lượt dưới đây). Fixture tự soạn ghi `MOCK`; STUDY4 là dữ liệu nội bộ, không phát hành. Kiểm thử không đọc hay ghi DB phát triển của người dùng.

### 5.1 Full regression

`scripts/verify-room-ux.ps1 -Mode Full -AllGui`, run `room-ux-20261025T124324Z-b350da46`, 12:43:30–13:04:39 UTC:

| | PASS | FAIL | ERROR | SKIP |
|---|---:|---:|---:|---:|
| protocol + client + server + monitoring-spike | **1797** | 0 | 0 | 23 |
| windows-observer (cùng lượt Maven, runner không tổng hợp) | 22 | 0 | 0 | 0 |

So với `f982aac` (1791) có thêm đúng 6 test mới (mục 2). PASS gồm E2E ACCOUNT_PIN `AccountPinFlowPostgresGuiTest` 3/3 (Admin tạo tài khoản → Proctor tạo phòng → 2 Candidate JOIN, READY 2/1/0 → START → Listening/Reading → autosave → reconnect không reset deadline/writer → Submit; truy cập chéo attempt 403; USB CIM thật ghi đúng attempt, không lặp event khi thiết bị không đổi), `AccountPinPostgresTest` 17, `AdminAccountPostgresTest` 21, `AdminAccountGuiTest` 5, `AdminBootstrapTest` 4, `SeedAccountsPostgresTest` 1, `SeedDataInitializerTest` 3, `RealtimeClientTest` 77, `CandidateExamSignalCloseGuiTest` 1.

23 SKIP giữ đúng tập như lượt trước: 14 ca monitoring/USB tích hợp chạy qua runner riêng (5.2); `study4RealTwoHundredQuestionAcceptance` qua runner EX-08 (5.3); 2 ca app đã cài/app-image; `Phase4RoomBenchmarkTest`; 2 ca symlink cần quyền tạo link; `RichImagePostgresTest.realReservedV13…`; `RichAttemptE2eGuiTest` và `ProctorV2E2eGuiTest` cần Server bên ngoài.

Migration: V19–V21 áp đúng trên DB mới và trên DB V18 có dữ liệu (`RoomAdmissionPostgresTest`, `AdminAccountPostgresTest`, `AccountPinPostgresTest` kiểm checksum V1–V18; smoke 5.4). Hai sửa review không thêm migration.

Lịch sử các lượt trước trên nhánh (giữ nguyên): `e188153` 1662 PASS/48 SKIP; `dd00e1a` **1 FAIL**; `5c7a7b9` **2 ERROR**; `17f94c6` 1756 PASS/23 SKIP; `f982aac` 1791 PASS/23 SKIP. Lượt `05822d8` bị dừng giữa chừng, không có kết quả.

### 5.2 Monitoring/USB — runner của C (`scripts/demo-usb-integrated.ps1`)

| Chế độ | Ca chạy | Kết quả |
|---|---|---|
| Mock + Timeline + AlertRules + Integrated | `allExistingSourcesShareRunAndReachHistoryTimelineAndAlerts`, `candidateObserversReachAuthorizedLane`, `productionObserversRetireOnTerminalAndRevoke`, `applicationControlsStopRestartLogoutAndSwitchAccount` | Không tải: **14/14 lượt PASS cả 4 ca**. Tải CPU nhân tạo: xem dưới |
| Mock + Timeline | `mixedMockDeliveryReachesTwoAttemptDashboard` | 1/1 PASS |
| Mock + Timeline + AlertRules | `experimentalAlertsReachDashboard` | 1/1 PASS |
| Mock | `UsbIntegratedDemoPostgresTest` 4 ca | 4/4 PASS |
| Real CIM | `realCimReadinessCommitAckAndHistory` | 1/1 PASS |
| Real CIM + Timeline | `manualMonitoringStartsUsbFocusWindowWithProcess`, `productionUsbObserverReachesDashboard` | 2/2 PASS |

**Integrated dưới tải CPU nhân tạo** (mọi lõi trừ một chạy vòng lặp bận, 6 lượt mỗi bên): trên `5173462` ca retire PASS 6/6, nhưng ba ca khác lỗi ở 4/6 lượt; trên code cũ `5a93b6d` cùng tải, ca retire FAIL 3/6 và ba ca khác cũng lỗi ở 4/6 lượt. Lỗi của ba ca khác giống nhau ở hai bên: `fx(...)` của harness test hết hạn 8 s khi khởi động app/tạo cửa sổ, một nút chưa có trên giao diện (`lookup` null), teardown không dọn được worker vì `closeGui` cũng hết hạn. Đây là hạn chế đã có của harness GUI khi máy quá tải, không phải do hai sửa review; không nâng timeout để che. Ngoài ra lượt 13 của vòng `8bea80e` có một lỗi cùng kiểu ở `applicationControlsStopRestartLogoutAndSwitchAccount` khi chạy trùng lúc một lượt Maven khác.

USB trong runner là MOCK hoặc CIM thật không đổi thiết bị (máy kiểm thử thấy 0 USB). `realCimStatusReachesDashboard` không được chế độ nào của runner chọn: **NOT RUN**.

### 5.3 Đề thật — EX-08 với STUDY4_6854

`scripts/verify-ex08-postgres.ps1` trên `5173462`, payload SHA-256 `d47aad14e86b6929cec0925ce57d78f295872f6d610c82b1715b5a26966e5349` (200 câu 6/25/39/30/30/16/54, 73 nhóm, 54 audio, 11 ảnh):

| Run | Media root | Kết quả |
|---|---|---|
| `ex08-20261025T131211Z-1ed685f7` | bản sao media trong thư mục tạm của phiên | **1 FAIL**: import trả 409 `INVALID_STATE` ("Media local thiếu, không an toàn hoặc không khớp nội dung") |
| `ex08-20261025T131435Z-cc0d6874` | media gốc của adapter | 1/1 PASS |
| `ex08-20261025T131605Z-fc9fd1c5` | **cùng** bản sao của lượt FAIL | 1/1 PASS |

Bản sao giống từng byte (SHA-256 toàn bộ 67 file), không có junction/reparse point, đường dẫn dài nhất 173 ký tự, và kiểm tra real-path của validator chạy riêng không thấy sai lệch. Nguyên nhân lượt FAIL **chưa xác định**; giả thuyết: định danh file (thời gian sửa/tạo, file key) đổi trong lúc Server đọc, có thể do phần mềm quét, và validator từ chối đúng thiết kế (fail-closed, có thể import lại). Không đổi code vì chưa có bằng chứng lỗi sản phẩm.

### 5.4 Bản portable (`sourceCommit` `5173462`)

Đóng gói bằng `mt.exe` Windows SDK 10.0.26100.0 (chữ ký Microsoft hợp lệ). `server.jar`/`client.jar` trong bundle giống từng byte JAR do full regression 5.1 build.

| Smoke | Kết quả |
|---|---|
| Client, bản sao trong `Kiểm thử có dấu và khoảng trắng`: URL hợp lệ/không hợp lệ, cửa sổ đăng nhập thật từ runtime đóng gói, thoát sạch, audio JavaFX | 9/9 PASS |
| Client, đường dẫn ASCII | 8/8 PASS |
| Server, DB mới, bản sao trong `Kiểm thử bản phát hành`, media trong `Media đề thi`: `server.env` không có `TOEIC_SEED_PASSWORD`, 5 ca cấu hình sai bị từ chối, V19–V21, Admin bootstrap, **không có tài khoản demo dù môi trường có `TOEIC_SEED_DEMO_ACCOUNTS=true`**, Admin tạo Giám thị và đăng nhập, cổng bị chiếm, PostgreSQL không kết nối được, dòng `Server READY` | 15/15 PASS |
| Server, **nâng cấp DB do Server main `acc1f82` (V18) tạo** → V21, tài khoản cũ vẫn dùng được và digest không đổi, Giám thị do Admin tạo import STUDY4_6854 từ thư mục media có dấu (200 câu, selectable) | 19/19 PASS |
| ZIP giải nén vào `Giải nén`: Client `-ValidateOnly` exit 0; Server thiếu `server.env` exit 1, một dòng `ERROR:` | PASS |

### 5.5 CI

GitHub Actions `PR CI` trên HEAD mới của PR #108: kết quả ghi trong mô tả/bình luận PR sau khi chạy xong (CI không có PostgreSQL nên các test PostgreSQL/GUI SKIP ở đó; kết quả PostgreSQL/GUI là của 5.1).

### 5.6 Chưa kiểm thử

| Mục | Trạng thái |
|---|---|
| LAN hai máy Windows thật | NOT TESTED |
| Người nghe audio bằng loa/tai nghe | NOT TESTED (audio smoke là xác nhận tự động) |
| Cắm/rút USB vật lý trên commit tích hợp, luồng ACCOUNT_PIN | NOT TESTED (xem mục 6) |
| MSI/installer, máy sạch thứ hai | NOT TESTED |
| Đo lại E1/E2 với `process-policy-v4` | NOT TESTED (kết quả cũ thuộc policy v1) |

## 6. Bằng chứng USB vật lý (từ nhánh của Công Lâm, không phải commit tích hợp)

- `evidence/usb-hardware-20261024/REPORT.md`: 24/10/2026 20:52–20:54 (UTC+7), máy `USB-TEST`, Windows 11, Java 21.0.10, PostgreSQL 18.3, source `acc1f82` (+`AGENTS.md` chưa track). Runner REAL CIM, provider MSFT_DISK: 86 scan, đúng một `USB_STORAGE_CONNECTED` (0→1) lúc 20:53:54.556761, 44 scan sau không lặp; COMMIT/ACK/history PASS. Báo cáo tự ghi: chưa kiểm REMOVED/PRESENT_AT_START, không kiểm GUI.
- `evidence/usb-8080-fix-20261024/REPORT.md`: 24/10/2026, app thật trên nhánh USB (working tree chưa commit, base `acc1f82`), luồng ca V1 với đề MOCK một câu; người dùng xác nhận thấy trên app giám thị `USB_STORAGE_REMOVED` 22:25:23.057301 và `USB_STORAGE_CONNECTED` 22:25:34.612962.

Hai bằng chứng trên chứng minh đường CIM → WebSocket → PostgreSQL → giao diện giám thị với thiết bị thật trên code của C; chúng không chứng nhận commit `5173462` hay luồng ACCOUNT_PIN. Trên commit tích hợp, USB được kiểm bằng CIM thật không đổi thiết bị và bằng MOCK (5.1, 5.2).

## 7. Hạn chế và vấn đề đã biết

- Harness GUI của `MonitoringTimelinePostgresTest` (owner C) không ổn định khi máy quá tải CPU: `fx(...)` hết hạn 8 s (5.2). Có cả trước và sau hai sửa review; khi máy không tải, 14/14 lượt PASS.
- Một lượt EX-08 bị từ chối import media mà nguyên nhân chưa xác định (5.3); hai lượt chạy lại PASS.
- Giám sát tiến trình chỉ quan sát đúng 9 tên của `process-policy-v4`; không phải mọi tiến trình Windows. Tiến trình sống ngắn hơn chu kỳ poll có thể bị bỏ lỡ. Server cũ không hiểu v4: phải chạy Server và Client cùng bản.
- USB: chỉ USB lưu trữ, polling có thể bỏ sót cắm/rút quá nhanh; không đọc nội dung/serial, không chặn thiết bị. Lỗi đọc nguồn hiển thị UNKNOWN/ERROR, không được hiểu là "đã rút".
- Sau khi quyền giám sát chấm dứt, việc gỡ listener JavaFX vẫn chờ luồng FX; trong khoảng đó input bị bỏ, không được gửi.
- Bản phát hành không tạo tài khoản demo: DB mới cần `TOEIC_ADMIN_BOOTSTRAP_USERNAME/PASSWORD` ở lần chạy đầu, nếu không sẽ không có tài khoản nào đăng nhập được (launcher in nhắc nhở).
- Giao diện legacy (mã phòng + PIN cá nhân, ca V1, tab V2 cũ) bị ẩn mặc định, backend và test vẫn giữ; `TOEIC_UI_LEGACY=true` chỉ dùng cho hồi quy.
- Bundle chưa ký số. Đóng gói cần Windows SDK `mt.exe` (dùng bản 10.0.26100.0 có chữ ký Microsoft).
- Đề STUDY4 là dữ liệu nội bộ được người cung cấp cho phép dùng trong dự án; không phân phối lại.

## 8. Artifact

| | |
|---|---|
| File | `toeic-monitor-portable-5173462.zip` (66 098 525 byte) |
| SHA-256 | `cd2b6e2bbd95c771b122cd05d6443bdbe279ea6111630c2f9fd64acac1adc1f8` |
| `server.jar` / `client.jar` SHA-256 | `703bbbde12be51b54e6d76767c2a774f9750fc89e545cbc6eabe865906b0bf7f` / `b36c246fffff3ca84ac54bab6079425a039a93ac8a8f655b97d20527dff58cd4` |
| Nơi lưu bàn giao | `E:\Project\TOEIC-Release\` (ZIP, `.sha256`, `READ-ME.txt`); bản `5a1af3e` cũ chuyển vào `E:\Project\TOEIC-Release\superseded\`. Sẽ đính kèm GitHub Release khi owner merge và gắn tag |
| Nội dung | `Start-Server.bat`, `Start-Client.bat`, `start-release.ps1`, `server.env.example` (giá trị bí mật để trống, không còn `TOEIC_SEED_PASSWORD`), `READ-ME.txt`, `server.jar`, `client.jar`, `runtime\` Java 21, `artifact-manifest.json` (SHA-256 từng file, `containsCredentials=false`) |

Hướng dẫn chạy: `READ-ME.txt` trong bundle và [../DEMO-RUNBOOK.md](../DEMO-RUNBOOK.md).

## 9. Đề xuất dọn dẹp (chưa thực hiện)

Sau khi owner merge PR: có thể đóng PR #53–#56 (tài liệu review 18/10), #76, #78, #79 (draft EX-06/EX-07 đã nằm trong main); PR #107 (QA01) chờ Djicz quyết định vì fix test trong đó đã bị ACCOUNT_PIN thay thế. Nhánh của thành viên giữ nguyên. Artifact cũ để ngoài thư mục bàn giao, chưa xóa: `C:\Users\nguye\AppData\Local\Temp\toeic-account-pin-verification\distribution\`, `E:\Toeic-Workspace-Backups\` (các bản release trước), `E:\Project\TOEIC-Release\superseded\`. Worktree tạm `E:\Project\toeic-final-verify`, `E:\Project\toeic-main-v18` có thể gỡ bằng `git worktree remove` khi không cần chạy lại.
