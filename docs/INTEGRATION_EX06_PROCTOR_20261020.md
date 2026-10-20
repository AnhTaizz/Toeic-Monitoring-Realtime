# Tích hợp EX-06 (TOEIC Room) + Proctor redesign + PROCTORED_V2 — 20/10/2026

**Trạng thái: code + test local PASS, chưa review, chưa merge, chưa DONE.** Không đổi tracker, BACKLOG hay human approval.

- **Nhánh:** `integrate/ex06-proctor-20261020`, gồm:
  - base EX-06 `448f58e`;
  - merge Proctor UI `1359dba` (`feat/ui-proctor-redesign-20261020`), giữ lịch sử, không rebase.
  - Hai nhánh nguồn không bị sửa.
- **Chuỗi phụ thuộc chưa merge vào `main`:** EX-06 → EX-07 → EX-05 → EX-03.
- **Dữ liệu kiểm thử:** fixture **MOCK** `mock-ex02-200`, không phải đề thật.

## 1. Checkpoint

| CP | Commit | Nội dung |
|---|---|---|
| CP1 | `7c53613` merge, `5c8e4a4` | Merge không xung đột (không trùng file). Launcher: role PROCTOR → `ProctorRedesignView.forLogin`, scene 1366×768, đúng 3 thay đổi trong `docs/ui-proctor-redesign/INTEGRATION_HANDOFF.md`. Candidate vẫn đi `RichEntry.probe` → `RichAttemptView`/legacy |
| CP2 | `f002bc1` | Panel giám thị V2: `ProctorV2Api`, `ProctorV2ApiClient`, `ProctorV2Controller`, `ProctorV2SessionPane`, `ProctorSessionControls` |
| CP3 | `182f5f9`, `64b7c5f` | E2E server thật (`ProctorV2E2eGuiTest`), CSS tab có scope riêng, bằng chứng trong `evidence/integrate-ex06-proctor-20261020/` |
| CP4/CP5 | (commit chứa file này) | `mvn clean verify`, ghi nhận Windows NOT RUN, audit Flyway, runbook, tài liệu |

## 2. Panel "Điều khiển ca" → tab "Ca PROCTORED_V2"

`ProctorRedesignView.forLogin` tạo `sessionControls` là `ProctorSessionControls` (một TabPane):
- tab 1 là panel V2 mới;
- tab 2 là `ProctorSessionView` V1 cũ, giữ nguyên.

Record `Wiring` và các `Section` không đổi; test fixture của Agent 2 vẫn dựng `Wiring` như trước. Đóng view hoặc đăng xuất sẽ đóng cả hai tab.

| Bước | Endpoint (đã có, không đổi contract) | Quy tắc client |
|---|---|---|
| Catalog | `GET /api/v2/exams?limit=20[&afterExamId=]` | Phân trang bằng `nextAfterExamId` của server. Hiện mọi đề kèm `contentValidation`, `mediaVerification`, `blockedReasons`. Chỉ đề `selectable=true` và không có lý do chặn mới bật "Tạo ca". Client từ chối response có `selectable=true` nhưng vẫn mang lý do chặn. Không hardcode mã đề |
| Tạo ca | `POST /api/v2/sessions` (201) | Giám thị đang đăng nhập luôn nằm trong `proctorUserIds`. Không có API danh sách user nên phải nhập **ID số** (UI ghi rõ hạn chế này); client kiểm số dương, trùng, vừa là thí sinh vừa là giám thị, thời lượng 1–240. `sessionId`/`attemptId` do server sinh. V2 create không có `requestId`, nên khi mất phản hồi client báo "không rõ đã tạo chưa" và **không tự tạo lại** |
| READY | `GET /api/v2/sessions/{id}/readiness` | Mỗi lượt: audio, ảnh Part 1, lease, lý do chặn. Mỗi lúc chỉ một request READY đang chạy (bấm lặp bị bỏ qua). Tự đọc lại mỗi 3 s (`toeic.v2.readyPollMillis`), tối đa 600 lần. Dừng sau 5 lỗi liên tiếp, khi gặp 401/403/404, sau khi START, khi đóng view. Tạm dừng khi rời trang hoặc sang tab V1, quay lại thì đọc ngay. Có ô nhập `sessionId` có sẵn (không có danh sách ca giả). Response tự mâu thuẫn (`allReady` ≠ các dòng) bị từ chối |
| START | `GET /api/v1/sessions/{id}/audio/readiness` (lấy `manifestId`), rồi `POST /api/v1/sessions/{id}/listening/start` | Nút chỉ bật khi có snapshot `allReady=true` **cùng ca**, đọc trong 8 s gần nhất. Có hộp xác nhận. `requestId` được tạo một lần trước lần gửi đầu. Mất ACK hoặc 5xx thì giữ nguyên `requestId` + `manifestId`; "Thử lại START" gửi lại đúng request đó (server trả lại `startId` cũ, không start lần hai, không dời deadline). 409 thì bỏ request và đọc lại READY để hiện lý do. Đang có START chưa rõ kết quả thì không cho đổi ca. Chỉ hiển thị `startId`/`startedAt` do server xác nhận. Không sửa deadline, máy giám thị không phát audio, không bỏ qua Visual READY |

**Luồng chạy:**
- HTTP chạy trên 2 worker `toeic-proctor-v2`, hàng đợi 8. Một thread timer riêng; không có I/O trên FX thread.
- Controller phát snapshot bất biến; pane gộp các snapshot rồi render trên FX thread.
- Generation guard bỏ kết quả về muộn sau khi đổi ca hoặc đóng view.
- 401 gọi đường `expired` chung của redesign view.

## 3. Kiểm thử (cloud Linux, OpenJDK 21.0.12.1, JavaFX 21.0.12, Maven 3.9.11, Xvfb, **PostgreSQL 16.15**)

| Test | Kết quả | Loại |
|---|---|---|
| `ProctorV2ControllerTest` | 14/14 PASS | Thuần, API MOCK. Cursor catalog, đề bị chặn, roster, tạo ca mất phản hồi, START cần snapshot mới và đúng ca, mất ACK retry cùng `requestId`, 409, đổi ca khi START treo, 401, pause/resume, không đọc READY trùng, đóng thì bỏ kết quả muộn |
| `ProctorV2ApiClientTest` | 7/7 PASS | HTTP loopback thật, body MOCK. Query `limit`/`afterExamId`, 201, roster khớp, readiness mâu thuẫn, mã lỗi lồng `error.code`, không lộ message server, START qua v1 |
| `ProctorV2SessionPaneGuiTest` | 4/4 PASS | JavaFX Stage thật, API MOCK. Luồng chọn → tạo → READY → START mất ACK → retry; chưa READY; 403; trang trong `ProctorRedesignView`, rời trang thì pause, đăng xuất đóng cả hai tab |
| `ProctorV2E2eGuiTest` | **PASS 3/3** trên `182f5f9` (trước đó 5/5 sau khi sửa race của chính test) | **Server + PostgreSQL 16.15 + WS thật**, fixture MOCK |
| Test có sẵn chạy lại sau merge | RichExamModel 9, RichImageStore 6, VisualReady 9, RichExamViewGui 6, ProctorRedesignGui 12, ProctorSettings 4, RoomFeed 8, RoomModel 8, ProctorSessionApiClient 9, ProctorSessionController 10, ProctorProgressController 5: PASS | |

**Các bước E2E** (log: `evidence/integrate-ex06-proctor-20261020/e2e/e2e-log.txt`):
1. Import fixture qua API.
2. Giám thị đăng nhập; `ProctorRedesignView` thật, tab V2.
3. Chọn đề từ catalog; tạo ca bằng nút. Server trả `SCHEDULED`, START vẫn tắt.
4. Thí sinh đăng nhập sau khi ca được tạo; scope chứa lượt mới; `RichEntry` vào TOEIC Room.
5. Lobby: 4 nút audio thật → "Server: đã xác nhận Audio + Ảnh READY".
6. Panel giám thị tự đọc READY → "Tất cả READY" → START qua hộp xác nhận thật → server trả `startId`.
7. Thí sinh vào 200 câu, Listening phát xong một lần qua player duy nhất.
8. Chọn câu 101 và 147 bằng bảng "Chọn câu"; autosave được server xác nhận `{"q101":"A","q147":"D"}`, revision 2, writer 2.
9. Phòng giám thị (room model) thấy lượt mới.
10. Nộp bài qua hộp xác nhận của phòng thi → `SUBMITTED`, 1/200 câu đúng (Reading 1). Kết quả khớp key MOCK q101=A, q147=C. Giám thị đọc cùng điểm.
11. Đăng xuất: thread `toeic-proctor-v2*` dừng hết.

**Probe âm** (đều đúng kỳ vọng):

| Probe | HTTP |
|---|---|
| START trước READY | 409 |
| GET đề trước START | 409 |
| Token thí sinh gọi catalog/readiness của giám thị | 403 |
| candidate2 đọc lượt của candidate1 | 403 |
| Token sai | 401 |
| Lưu với `writerEpoch` sai | 409, đáp án không đổi |
| Lưu sau khi nộp | 409 |
| START lặp với `requestId` mới | 200, cùng `startId`, `deadlineAt` không đổi |

`mvn clean verify` trên `64b7c5f` (PG16 + `TOEIC_PROCTOR_GUI`/`TOEIC_RICH_GUI` + hai E2E bật, Xvfb):
- `mvn -B -fae clean verify` → **exit 1, BUILD FAILURE**: protocol 45/0; client 744 run/**4 FAIL** đều ở `ControlledProcessHarnessTest` (lỗi sẵn có trên Linux); server **SKIPPED** vì phụ thuộc module client.
- `mvn -B clean verify -Dmaven.test.failure.ignore=true` → exit 0, "BUILD SUCCESS" **chỉ vì cờ bỏ qua lỗi test**: protocol 45/0; client 744/**3 FAIL** (`ControlledProcessHarnessTest`)/5 skip; server 443/**1 FAIL**/3 skip; monitoring-spike 1/0. Trong lượt này `ProctorV2E2eGuiTest`, `RichAttemptE2eGuiTest`, `ProctorRedesignGuiTest` 12/12, `ProctorV2SessionPaneGuiTest` 4/4 đều PASS.
- FAIL server là `ListeningRuntimePostgresTest.proctorControlsLt04RealGuiBeforeAndAfterAutomaticTimeout`: "GUI condition did not complete within 35 seconds" khi chờ READY của AudioPreparationView legacy. Test chỉ chạy khi `TOEIC_PROCTOR_GUI=true` (lượt verify EX-06 không bật nên chưa chạy). Chạy riêng nó: FAIL giống hệt trên nhánh tích hợp, trên base EX-06 `448f58e` **và trên `origin/main` `3bdefe6`**. Vậy lỗi có sẵn trong môi trường Linux/Xvfb/ALSA null này, không do tích hợp. Nhật ký cũ ghi LT04 GUI từng PASS trên Windows. Chưa tìm nguyên nhân; cần B/C chạy lại trên Windows.

## 4. Khác biệt so với mockup (có chủ đích hoặc còn tồn)

| Màn | Khác biệt |
|---|---|
| Proctor "Điều khiển ca" | Không có mockup cho trang này. Panel V2 dùng card/badge/bảng của redesign. Tab V1 vẫn là giao diện tối cũ của B4 bên trong khung sáng |
| Proctor topbar | Khi giám thị có nhiều ca, ô thời gian hiển thị "Nhiều ca" (hành vi có sẵn của redesign, server không có API liệt kê ca) |
| Proctor catalog/READY | Mã lượt hiện nguyên UUID; không có tên thí sinh vì readiness không trả username, không tự ghép |
| Kết quả thí sinh | `ScoreResultDialog` legacy (nền tối), chưa theo style TOEIC Room |
| Login, trang chủ thí sinh | Vẫn giao diện cũ (ngoài phạm vi EX-06/redesign) |
| TOEIC Room 7 Part | Như `docs/EX06_JAVAFX_RICH_RENDERER.md` §2 |
| Proctor 7 màn | Như `docs/ui-proctor-redesign/VERIFICATION.md` (logo chữ T, layout 1366/1440, trạng thái backend thật thay cho nhãn mockup) |

## 5. Runbook Windows chỉ dùng UI (dữ liệu MOCK) — **NOT RUN trên Windows**

Trên Linux, luồng tương đương đã chạy bằng `ProctorV2E2eGuiTest`. Chưa ai chạy runbook này trên Windows.

```powershell
# 0. DB: tạo .env từ .env.example, rồi
docker compose up -d --wait
mvn package
# 1. Terminal 1 — server (nạp DB_* từ .env vào terminal trước)
$env:TOEIC_SEED_PASSWORD = "<mật khẩu MOCK của bạn>"
$env:TOEIC_AUDIO_ROOT = "$PWD\docs\plan-v2\fixtures\ex02\assets\audio"
$env:TOEIC_IMAGE_ROOT = "$PWD\docs\plan-v2\fixtures\ex02\assets\images"
java -jar server\target\server-0.1.0-SNAPSHOT.jar
# 2. Terminal 2 — import đề MOCK một lần (panel V2 chưa có nút import rich)
.\scripts\demo-ex06-v2.ps1 -ServerUrl http://127.0.0.1:8080 -ImportOnly
# 3. Terminal 3 và 4 — hai cửa sổ client
mvn -pl client -am javafx:run
```

1. **Cửa sổ giám thị**, đăng nhập `proctor1`:
   - Mở "Điều khiển ca", tab "Ca PROCTORED_V2 (200 câu)".
   - Chọn đề `mock-ex02-200`; phải thấy badge "Dùng được".
   - Nhập ID thí sinh. Trên DB mới, seed là `candidate1`=1, `candidate2`=2, `proctor1`=3. Nếu không chắc, kiểm bằng `SELECT id, username FROM user_accounts`.
   - Bấm "Tạo ca PROCTORED_V2". Panel hiện mã ca và tự theo dõi READY.
2. **Cửa sổ thí sinh**, đăng nhập `candidate1` **sau** bước 1:
   - Chọn lượt vừa tạo, vào phòng thi.
   - Bấm lần lượt "Tải / chuẩn bị lại audio" → "Thử loa" → "Tôi nghe được" → "Xác nhận sẵn sàng".
   - Chờ đủ 4 dấu ✓.
3. **Giám thị:** chờ badge "Tất cả READY", bấm "START ca", xác nhận OK. Panel hiện `startId` do server trả. Không cần lệnh PowerShell để START.
4. **Thí sinh:**
   - Listening tự phát một lần.
   - Chọn đáp án. Dùng "Chọn câu" để đi tới Part 5–7 sau khi Listening xong.
   - Bấm "Nộp bài" → Yes → xem hộp kết quả.
5. **Giám thị:** Tổng quan/Thí sinh hiển thị tiến độ và trạng thái đã nộp.

Mỗi lần demo cần tạo một ca mới. Nếu "Tạo ca" báo lỗi mạng, đừng bấm lại ngay: kiểm Tổng quan/DB trước.

## 6. Audit Flyway (chỉ đề xuất — **không sửa migration, không bật `outOfOrder`**)

**Hiện trạng:**
- `main`: V1–V12.
- Chuỗi EX (EX-03/EX-07 → nhánh này): thêm `V14__rich_import_contract`, `V15__proctored_v2_preparation`.
- MON-07 (`feat/v2-c-mon07-signal-pipeline`, PR draft): `V13__experimental_monitoring_signals`.
- DB E2E ở đây áp V1–V12, V14, V15.

**Rủi ro:** DB nào đã có V14/V15 mà sau đó nhận V13 thì Flyway (mặc định `outOfOrder=false`) báo "resolved migration not applied", và server **không khởi động**. DB mới chạy cả ba theo thứ tự thì không lỗi.

**Schema:** V13 chỉ tạo `monitoring_signal_counters` và `monitoring_signal_events` (FK tới `monitoring_attempts`). V14/V15 chỉ đổi bảng đề/ca/readiness. Không đụng cùng cột nên thứ tự áp không ảnh hưởng schema.

**Phương án an toàn** (A và C chọn; cần review riêng):
1. **Đổi số MON-07 thành V16** trước khi merge, vì PR còn draft. DB đã có V14/V15 chạy tiếp bình thường. DB dev nào đã áp V13 của MON-07 thì reset (`scripts/reset-db.ps1 -Force`, dữ liệu MOCK).
2. **Merge MON-07 (V13) vào `main` trước chuỗi EX.** DB mới áp V13→V14→V15 đúng thứ tự. Mọi DB dev đã có V14/V15 phải reset trước khi chạy bản có V13.
3. Đổi số V14/V15 của chuỗi EX: **không khuyến nghị**, vì đã nằm trên nhiều nhánh/PR và nhiều DB dev.

Không nên dùng `outOfOrder=true`, `repair`, hay sửa checksum để che lệch thứ tự.

## 7. Chưa chạy / chờ

- **NOT RUN:**
  - Windows: GUI, `jpackage`/MSI, runbook mục 5, `demo-ex06-v2.ps1 -ImportOnly`.
  - LAN với hai máy.
  - PostgreSQL 18.
  - Nghe bằng loa thật (ở đây dùng ALSA null).
  - Token hết hạn theo thời gian thật (mới thử token sai → 401).
  - Mất mạng/reconnect giữa bài trong luồng tích hợp. Phần lobby đã có test đơn vị trong EX-06.
  - Lỗi audio/ảnh trên server thật. Đã có test đơn vị: `RichImageStoreTest` với `corrupt.png`, `AudioPreparationTest`.
- **P1:**
  - Panel V2 chưa có import rich exam; vẫn import qua script/API.
  - Chưa có API danh sách user/ca, nên phải nhập ID số.
  - Takeover/reclaim sau START chưa tạo lại audio view theo writer mới (tồn từ EX-06).
- **P2:** Tab V1 vẫn là giao diện tối; `ScoreResultDialog` cũ.
- **Flaky:** `ProctorRedesignGuiTest.settingsApplyInSessionSaveOnlyWhenWrittenAndRestoreDefaults` FAIL 1 lần khi chạy cả suite client (lookup `#settings-density-compact` trả null). Chạy riêng thì PASS 3/3; nguyên nhân chưa rõ. Test này của Agent 2, không sửa ở đây.
- `ControlledProcessHarnessTest`: 3–4 FAIL (số lượng đổi giữa các lượt) đã có từ trước trên Linux, không thuộc tích hợp này.
- `proctorControlsLt04RealGui…` (server, GUI legacy B4): FAIL cả trên `main`, xem §3.
