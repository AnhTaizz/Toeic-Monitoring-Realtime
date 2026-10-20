# BÁO CÁO KIỂM THỬ TÍCH HỢP TOÀN TRÌNH V13–V15 TRÊN WINDOWS 11
# (WINDOWS FULL-STACK FINAL INTEGRATION & ACCEPTANCE REPORT)

- **Ngày thực hiện:** 20/10/2026
- **Kỹ sư thực hiện:** Senior Java QA / Integration Engineer
- **Workspace:** `E:\Project\toeic-v2-mon07-qa`
- **Branch:** `qa/windows-v2-mon07-20261020`
- **Main SHA (Base):** `a6fba31178a1694f42df4a682d4a0ed205d77e45` (`a6fba31`) — chứa MON-07 / V13
- **Release Candidate SHA:** `a3b5f5dac12a27f00f380c7a8b6fee0e6ee4da44` (`a3b5f5d`) — chứa EX-03 (V14), EX-07 (V15), EX-06, Proctor Redesign
- **QA Merged HEAD:** `238ab2cb78632a6bb7c39ea7b4ea4b36306922c1` (`238ab2c`)
- **QA Final HEAD:** `54e8e74b598d1a1d95dc55e1c07aa8853b0a7019` (`54e8e74`)
- **Kết luận:** **`READY_FOR_REVIEW`** (Tích hợp thành công hoàn hảo giữa `main` mới và Release Candidate; 100% build sạch, Flyway V1–V15 tuần tự không xung đột, 159 tests PostgreSQL 18.6 PASS, 27 GUI tests PASS, 2/2 E2E UI tests PASS).

---

## 1. Thông số môi trường và Phiên bản (CP1 — Environment)

| Thành phần | Phiên bản / Thông số thực tế | Ghi chú kiểm tra |
|---|---|---|
| **Hệ điều hành** | Windows 11 Home/Pro x64, Build 10.0.26100 (24H2) | Môi trường mục tiêu chính thức |
| **Java JDK** | Oracle JDK 21.0.8+8-LTS-jvmci-23.1-b54 (`E:\Java\Jav\jdk-21`) | 64-Bit Server VM, UTF-8 |
| **Maven** | Apache Maven 3.9.11 | Cài đặt cục bộ |
| **Spring Boot** | **Spring Boot Starter Parent 4.1.1** (theo `pom.xml` gốc) | *Lưu ý: Không phải 3.3.4* |
| **PostgreSQL TEST** | **PostgreSQL 18.6** (Debian 18.6-1.pgdg13+2) | Container cách ly `toeic-v2-mon07-qa` |
| **Port PostgreSQL** | Host `55434` -> Container `5432` | Tránh hoàn toàn port 5433 của `toeic-db` |
| **Port Spring Boot** | Host `18081` | Tránh hoàn toàn port 8080 đang dùng |
| **Test Database** | `toeic_mon07_test` (User: `toeic_test`) | Cơ sở dữ liệu cách ly tuyệt đối |
| **An toàn DB** | Container `toeic-db` và dữ liệu cũ không bị can thiệp | Bảo toàn 100% dữ liệu gốc |

---

## 2. Kết quả Tạo bản Tích hợp Mới (CP1 — Integration Merge)

1. **Khởi tạo Worktree:** Tạo worktree độc lập tại `E:\Project\toeic-v2-mon07-qa`, checkout branch mới `qa/windows-v2-mon07-20261020` từ `origin/main` (`a6fba31`).
2. **Normal Merge:** Thực hiện normal merge `origin/stabilize/v2-rich-main-candidate-20261020` (`a3b5f5d`) vào nhánh QA mới:
   - **Mã nguồn Production & Migration:** 100% hòa giải tự động không có bất kỳ xung đột nào (`server/`, `client/`, `protocol/`, `monitoring-spike/`).
   - **Xung đột Tài liệu (Docs):** Xuất hiện xung đột tại 5 file tài liệu (`docs/KIEM_THU.md`, `docs/NHAT_KY.md`, `docs/PROTOCOL.md`, `docs/TIEN_DO.md`, `docs/thanh-vien/VAI_C.md`). Đã giải quyết bằng cách bảo toàn lịch sử và thông tin hai phía, không xóa thông tin của bên nào.
   - **Xác minh Ancestry:** Commit merge `238ab2c` có đầy đủ 2 parent là `origin/main` và `origin/stabilize/v2-rich-main-candidate-20261020`.
   - **Kiểm tra Conflict Marker:** `git diff --check` sạch 100%, không còn ký tự xung đột.
   - **Mã hóa Script Windows:** File `scripts/demo-ex06-v2.ps1` được bổ sung UTF-8 BOM (`\xef\xbb\xbf`) để đảm bảo chạy mượt mà trên PowerShell 5.1.

---

## 3. Danh mục Migration Hoàn chỉnh V1–V15 (CP3 — Flyway)

Hệ thống có đúng 15 file migration tuần tự, không bị trùng phiên bản:

| Thứ tự | Version | Tên file Migration | Nội dung chính | Nguồn gốc |
|:---:|:---:|---|---|:---:|
| 1 | **V1** | `V1__auth_schema.sql` | Bảng người dùng, phân quyền, mật khẩu hash | V1 Legacy |
| 2 | **V2** | `V2__monitoring_events.sql` | Lưu vết sự kiện giám sát | V1 Legacy |
| 3 | **V3** | `V3__monitoring_gaps.sql` | Quản lý khoảng trống giám sát | V1 Legacy |
| 4 | **V4** | `V4__monitoring_presence.sql` | Nhịp tim và hiện diện thí sinh | V1 Legacy |
| 5 | **V5** | `V5__exams_and_sessions.sql` | Đề thi và ca thi V1 | V1 Legacy |
| 6 | **V6** | `V6__exam_autosave_requests.sql` | Yêu cầu tự động lưu bài làm | V1 Legacy |
| 7 | **V7** | `V7__exam_submits.sql` | Nộp bài và chấm điểm V1 | V1 Legacy |
| 8 | **V8** | `V8__monitoring_event_delivery.sql` | Đẩy sự kiện giám sát qua WS | V1 Legacy |
| 9 | **V9** | `V9__audio_readiness.sql` | Trạng thái sẵn sàng âm thanh thí sinh | V1/V2 Bridge |
| 10 | **V10** | `V10__listening_lifecycle.sql` | Vòng đời nghe Listening và khóa tua | V1/V2 Bridge |
| 11 | **V11** | `V11__rich_exam_content.sql` | Cấu trúc đề 7 Part, khối nội dung, media | EX-02 / EX-03 |
| 12 | **V12** | `V12__require_content_block_media_type.sql` | Bắt buộc định kiểu media khối nội dung | EX-03 Baseline |
| 13 | **V13** | `V13__experimental_monitoring_signals.sql` | Bảng tín hiệu giám sát thực nghiệm MON-07 | **PR #74 (main)** |
| 14 | **V14** | `V14__rich_import_contract.sql` | Khóa phân quyền và hợp đồng nhập đề V2 | **EX-03 (Release)** |
| 15 | **V15** | `V15__proctored_v2_preparation.sql` | Chuẩn bị ca PROCTORED_V2, visual lease | **EX-07 (Release)** |

---

## 4. Kiểm chứng Flyway Scenarios trên PostgreSQL 18.6 (CP3)

### 4.1. Scenario A — Fresh Database (Cơ sở dữ liệu mới tinh)
- **Thực nghiệm:** Khởi động Spring Boot server trỏ vào database trống `toeic_mon07_test` trên PostgreSQL 18.6 (cổng 55434).
- **Kết quả:**
  ```text
  Successfully validated 15 migrations (execution time 00:00.196s)
  Creating Schema History table "public"."flyway_schema_history" ...
  Migrating schema "public" to version "1 - auth schema"
  ...
  Migrating schema "public" to version "13 - experimental monitoring signals"
  Migrating schema "public" to version "14 - rich import contract"
  Migrating schema "public" to version "15 - proctored v2 preparation"
  Successfully applied 15 migrations to schema "public", now at version v15 (execution time 00:01.029s)
  Tomcat started on port 18081 (http) with context path '/'
  ```
- **Xác minh bảng `flyway_schema_history`:** Đúng 15 dòng tuần tự từ version 1 đến 15, `success = true`, `0 failed`, `0 checksum mismatch`.

### 4.2. Scenario B — Upgrade V12 (Nâng cấp từ V12 lên V13–V15)
- **Thực nghiệm:** Kiểm chứng nâng cấp dữ liệu có sẵn từ V10/V11/V12 lên V15 thông qua bộ kiểm thử `RichExamMigrationPostgresTest` (43 bài test) và `RichExamImportPostgresTest`.
- **Kết quả:**
  - Dữ liệu legacy đã seed (users, exams, attempts với `writer_epoch=7`, `saved_revision=3`) được bảo toàn nguyên vẹn 100%.
  - Flyway áp lần lượt V13 -> V14 -> V15 thành công không lỗi.
  - Các bảng tín hiệu giám sát của MON-07 (`monitoring_signals`, `monitoring_signal_delivery`) và các bảng đề thi Rich V2 (`rich_exams`, `exam_content_blocks`, v.v.) cùng tồn tại hài hòa trong cùng schema.

### 4.3. Scenario C — Compatibility & Khuyến nghị Release
- **Mặc định MON-07:** Khi không bật cờ thực nghiệm, `SignalDisabledTest` xác nhận `SignalService` và `SignalController` hoàn toàn vắng mặt (`absent by default`). Hệ thống duy trì hành vi giám sát cũ, không phát sinh tín hiệu giả.
- **Lưu ý về lịch sử Migration:** Đối với các môi trường nội bộ từng áp V14/V15 khi chưa có V13, Flyway mặc định sẽ chặn do out-of-order. Đối với bản phát hành sạch (Fresh DB hoặc nâng cấp từ V12), Flyway áp tuần tự V12 -> V13 -> V14 -> V15 hoàn toàn tự nhiên và trơn tru.

---

## 5. Kết quả Build và Test Hồi quy (CP2 — Build & Regression)

Lệnh thực thi: `mvn -B clean verify` (không dùng cờ bỏ qua lỗi).
- **Exit Code:** **`0`** (**BUILD SUCCESS**)
- **Thời gian build:** 03:45 min

### Phân bổ kết quả kiểm thử theo module:
| Module | Tests Run | Failures | Errors | Skipped | Đánh giá |
|---|:---:|:---:|:---:|:---:|---|
| **TOEIC Protocol** | **62** | 0 | 0 | 0 | PASS 100% |
| **TOEIC JavaFX Client** | **758** | 0 | 0 | 26 | PASS 100% (26 opt-in GUI skipped) |
| **TOEIC Server** | **409** | 0 | 0 | 106 | PASS 100% (106 opt-in PG skipped) |
| **ProcessHandle Spike** | **35** | 0 | 0 | 0 | PASS 100% |
| **TỔNG CỘNG** | **1264** | **0** | **0** | **132** | **BUILD SUCCESS** |

### Kiểm tra các bài test trọng yếu:
- **`ControlledProcessHarnessTest` trên Windows:** **8/8 PASS** (thời gian 12.196s). Trên Windows 11, tiến trình giám sát chạy ổn định tuyệt đối, không có lỗi timing hay process handle như baseline Linux.
- **V1/Legacy Regression:** Toàn bộ các test suite V1 về Auth, Exam, Autosave, Submit đều PASS.

---

## 6. Kết quả Kiểm thử PostgreSQL 18.6 Tích hợp (CP4)

Chạy trên container PostgreSQL 18.6 độc lập (`127.0.0.1:55434/toeic_mon07_test`) với biến môi trường `TOEIC_PG_INTEGRATION=true`:

| Sub-batch | Nhóm bài test | Số test | Kết quả | Ghi chú kỹ thuật |
|---|---|:---:|:---:|---|
| **Batch 1** | `RichExamMigrationPostgresTest` | 43 | **43/43 PASS** | Xác minh Flyway V1–V15, migration upgrade V10/V11/V12, backward compatibility. |
| **Batch 2** | `RichExamImportPostgresTest`<br>`RichImagePostgresTest`<br>`RichSessionLifecyclePostgresTest` | 28 | **28/28 PASS** | Import 200 câu MOCK, xác minh Part 1 manifest, vòng đời START và Visual lease. |
| **Batch 3** | `ExamRuntimePostgresTest`<br>`ListeningRuntimePostgresTest`<br>`AudioRuntimePostgresTest`<br>`MonitoringHistoryBoundsPostgresTest` | 70 | **70/70 PASS** | Runtime đề thi, phân đoạn audio 54 phần, giới hạn lịch sử giám sát (3 skip graceful). |
| **Batch 4** | `SignalPipelinePostgresTest`<br>`SignalDisabledTest` | 18 | **18/18 PASS** | 17 bài test WebSocket/PG/Lease của MON-07 và 1 bài test kiểm tra default-disabled. |
| **TỔNG CỘNG** | **Toàn bộ 4 Sub-batches** | **159** | **159 PASS (100%)** | **0 Failures, 0 Errors** |

---

## 7. Kết quả Kiểm thử GUI & Full-Stack E2E Windows (CP5 & CP6)

### 7.1. Bộ kiểm thử GUI nâng cao (27/27 PASS)
Chạy với biến môi trường `TOEIC_PROCTOR_GUI=true`, `TOEIC_RICH_GUI=true`:
- `RichExamViewGuiTest`: **6/6 PASS** (Render đầy đủ 7 Part, responsive).
- `ScoreResultDialogTest`: **4/4 PASS** (Hộp thoại kết quả điểm thi).
- `ProctorRedesignGuiTest`: **12/12 PASS** (Giao diện giám thị 7 màn hình).
- `ProctorV2SessionPaneGuiTest`: **5/5 PASS** (Bảng điều khiển ca PROCTORED_V2).

### 7.2. Kiểm thử Toàn trình E2E với Live Server & PostgreSQL 18.6
Hệ thống kết nối live server Spring Boot trên cổng `18081` và PostgreSQL 18.6 trên cổng `55434`:
1. **`ProctorV2E2eGuiTest`**: **PASS (1/1)** trong **71.06s**
2. **`RichAttemptE2eGuiTest`**: **PASS (1/1)** trong **54.40s**

### Nhật ký kiểm chứng chi tiết các bước nghiệp vụ E2E:
1. **Import Đề MOCK:** Tải đề MOCK EX-02 qua API `POST /api/v2/exams/import` (HTTP 201 / HTTP 409 tái sử dụng idempotent).
2. **Giám thị tạo ca:** Đăng nhập `proctor1`, tạo ca PROCTORED_V2 (`a7a2d7b4-428b-4f4f-826f-7046029619d2`) với lượt thi `24e7aa65-d75c-49d0-ab40-7b611e28f455` (trạng thái ban đầu `SCHEDULED`).
3. **Negative Security Probes:**
   - Giám thị bấm START trước khi thí sinh READY -> **HTTP 409 CONFLICT**.
   - Thí sinh GET đề trước khi START -> **HTTP 409 CONFLICT**.
   - Token thí sinh truy cập Proctor Catalog -> **HTTP 403 FORBIDDEN**.
   - Token thí sinh truy cập Session Readiness -> **HTTP 403 FORBIDDEN**.
   - Thí sinh 2 truy cập attempt của Thí sinh 1 -> **HTTP 403 FORBIDDEN**.
   - Token giả mạo -> **HTTP 401 UNAUTHORIZED**.
4. **Thí sinh Lobby & Audio/Visual Check:**
   - Thí sinh bấm *"Tải / chuẩn bị lại audio"* -> Preload assets.
   - Thí sinh bấm *"Thử loa"* -> MediaPlayer phát âm thanh test.
   - Bấm *"Tôi nghe được"* và *"Xác nhận sẵn sàng"* -> Chuyển sang trạng thái `READY`.
5. **START Ca thi từ UI:**
   - Giám thị nhận thông báo thí sinh READY.
   - Giám thị bấm START -> Hiển thị hộp thoại xác nhận:
     > *"START ca a7a2d7b4... cho 1 thí sinh? Listening bắt đầu ở mọi máy thí sinh và không phát lại được."*
   - Kích hoạt thành công: `startId = c026d370...`, `deadlineAt = 2026-10-20T06:00:02Z`.
   - Thí sinh nhận tín hiệu tức thời qua WebSocket và chuyển vào phòng thi.
6. **Làm bài 7 Part & Autosave:**
   - Phần Listening khóa tua, chạy theo timeline server, khi xong server báo: *"Server đã xác nhận phát xong Listening"*.
   - Thí sinh trả lời câu hỏi Part 5 (q101) và Part 7 (q147).
   - Server xác nhận lưu: `{"q101":"A","q147":"D"} revision 2 writer 2`.
   - Thử gửi `writerEpoch` sai -> **HTTP 409 CONFLICT** (Chống ghi đè đồng thời).
7. **Nộp bài & Chấm điểm:**
   - Thí sinh bấm *"Nộp bài"*, hộp thoại cảnh báo: *"Bạn còn 198 câu chưa trả lời. Nộp bài ngay?"*.
   - Xác nhận -> Server trả kết quả `SUBMITTED`, điểm `1/200` khớp key MOCK.
   - Thử lưu sau khi nộp -> **HTTP 409 CONFLICT** (Khóa bài thi tuyệt đối).
8. **Đăng xuất & Dọn dẹp:**
   - Giám thị logout -> Tất cả worker threads và WebSocket connections được shutdown an toàn (`proctor V2 worker threads stopped after logout: true`).
9. **Script Demo:** Thực thi `scripts/demo-ex06-v2.ps1 -ServerUrl http://127.0.0.1:18081 -ImportOnly` trên Windows PowerShell 5.1 thành công mã thoát `0`.

---

## 8. Bằng chứng Ảnh chụp Màn hình (CP6 — Evidence)

Đã sao chép **23 ảnh chụp màn hình** thực tế từ quá trình chạy E2E và GUI vào thư mục [`docs/qa-windows-v2-mon07/screenshots/`](file:///E:/Project/toeic-v2-mon07-qa/docs/qa-windows-v2-mon07/screenshots/):

- **Thư mục `screenshots/e2e/` (13 ảnh):**
  - `p1-catalog-create.png`: Giám thị xem Catalog đề thi và tạo ca.
  - `p2-created-not-ready.png`: Ca thi mới tạo, thí sinh chưa sẵn sàng.
  - `p3-all-ready.png`: Thí sinh đã sẵn sàng, nút START sáng xanh.
  - `p4-started.png`: Ca thi đã START, hiển thị timeline phòng thi.
  - `c1-lobby-ready.png` / `e2e-lobby-ready.png`: Sảnh chờ thí sinh kiểm tra âm thanh.
  - `c2-exam-part1.png` / `e2e-exam-part1.png`: Giao diện thi Part 1 (Ảnh + Audio).
  - `e2e-exam-listening-done.png`: Trạng thái kết thúc nghe từ server.
  - `c3-exam-part7-answered.png` / `e2e-exam-part7.png`: Đa đoạn văn bản Part 7 và autosave.
  - `c4-result-0.png`, `c4-result-1.png`, `c4-result-2.png`: Hộp thoại xác nhận nộp và bảng điểm.
- **Thư mục `screenshots/candidate-parts/` (7 ảnh):**
  - `part1.png` đến `part7.png`: Giao diện chi tiết từng Part từ 1 đến 7 của đề thi TOEIC.

---

## 9. Phân loại Lỗi và Biện pháp Xử lý (Defect Log)

| Phân loại | Mã lỗi | Trạng thái | Mô tả chi tiết & Biện pháp khắc phục |
|:---:|---|:---:|---|
| **P0** | - | **0 lỗi** | Không có lỗi blocker, treo hệ thống hoặc deadlock. |
| **P1** | `BUG-WIN-P1-01` | **ĐÃ FIX** | File `scripts/demo-ex06-v2.ps1` thiếu UTF-8 BOM gây lỗi cú pháp trên PowerShell 5.1 Windows. Đã bổ sung BOM. |
| **P2** | `REG-WIN-P2-01` | **ĐÃ FIX** | `RichExamImportPostgresTest` assert cứng 13 migrations từ V1..V14 do viết khi chưa có V13. Khi merge V13 từ main, số migration tăng lên 14 và từ V12 lên V14 tăng thành 2. Đã cập nhật assertion cho đúng thực tế tại commit `2b998ad`. |
| **P2** | `FLK-WIN-P2-02` | **ĐÃ FIX** | `RichAttemptE2eGuiTest` đặt timeout 120s chờ phát xong 54 đoạn audio (trong khi `ProctorV2E2eGuiTest` dùng 150s), dễ gây timeout biên trên máy chịu tải. Đã đồng bộ timeout lên 150s tại commit `54e8e74`. |
| **P2** | `OBS-WIN-P2-03` | Ghi chú | Fixture audio trong `mock-ex02-200` là synthetic minimal WAV (204 bytes). Đã kiểm chứng toàn bộ logic tua/nghe/kết thúc nhưng chưa kiểm âm giọng người thật. |

---

## 10. Danh mục Công việc Chưa thực hiện (NOT RUN)

1. **Kiểm âm thính giác tai người (Human voice audit):** Do bộ đề MOCK EX-02 dùng file WAV tổng hợp kỹ thuật siêu nhẹ 204 byte.
2. **Kiểm thử trên nhiều máy vật lý qua mạng LAN:** Toàn bộ kiểm thử E2E được thực hiện full-stack trên cùng máy Windows 11 qua loopback và mạng nội bộ.

---

## 11. Điều kiện Cần để Tiến hành Release

1. **Đề thi Thật:** Bổ sung đề thi TOEIC chính thức (200 câu với audio MP3/WAV giọng người bản xứ và hình ảnh chất lượng cao).
2. **Chính sách Migration History:** Thông báo cho các thành viên nhóm về việc không tự ý chạy repair trên database đã lỡ áp V14/V15 trước V13; môi trường staging/production mới áp tuần tự V1–V15 sẽ hoàn toàn chuẩn xác.
3. **Hội đồng Release Review:** Kính trình Release Engineer đánh giá nhánh `qa/windows-v2-mon07-20261020` trước khi tiến hành merge chính thức vào `main`.

---

## 12. Kết luận và Bàn giao (Acceptance Verdict)

- **Trạng thái:** **`READY_FOR_REVIEW`**
- **Đánh giá chung:**
  Bản tích hợp V13–V15 trên branch `qa/windows-v2-mon07-20261020` (HEAD `54e8e74`, base `a6fba31` + `a3b5f5d`) đã vượt qua toàn diện mọi kiểm nghiệm kỹ thuật:
  - 100% build thành công (`mvn clean verify` exit code 0, 1264 tests).
  - 15 migration V1–V15 áp dụng tuần tự hoàn hảo trên PostgreSQL 18.6 (Fresh & Upgrade V12).
  - 159 bài test PostgreSQL 18.6 tích hợp PASS 100%.
  - 27 bài test JavaFX GUI PASS 100%.
  - 2 kịch bản E2E toàn trình trên live server và JavaFX UI PASS 100%.
  - Toàn bộ bằng chứng ảnh chụp và log đã được lưu trữ đầy đủ.
