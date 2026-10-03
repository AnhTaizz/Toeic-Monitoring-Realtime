# Bằng chứng smoke T1-A1 / T1-B1 / T1-C1 — 2026-10-03

## Môi trường

- Branch: `feat/stage1-project-skeleton`
- Code HEAD khi chạy lượt cuối: `864f701`
- Windows 11 x64; Oracle JDK 21.0.8; Maven 3.9.11
- Docker 29.4.3; Docker Compose 5.1.3; PostgreSQL 18.6
- Spring Boot 4.1.1; JavaFX 21.0.12

## Build và unit test

- Lệnh: `mvn package`
- Kết quả: PASS, reactor 5/5 module thành công.
- Test: `AuthServiceTest` 2/2; `LoginApiClientTest` 4/4; `ProcessObservationTest` 1/1; tổng 7/7.
- Client test gồm HTTP phản hồi chậm bị giữ bằng latch nhưng lời gọi trả `CompletableFuture` ngay, và role server trả được ánh xạ sang đúng màn thí sinh/giám thị.

## PostgreSQL và login thật

- Compose hiện có được dùng lại; không tạo compose thứ hai.
- Đã xác minh volume duy nhất là `toeic-pgdata`, chạy `docker compose down -v`, rồi `docker compose up -d --wait`.
- Server khởi động trên DB sạch; Flyway chạy migration V1 và seed ba tài khoản băm BCrypt.
- Smoke cuối qua HTTP thật:
  - `candidate1` → HTTP 200, role `CANDIDATE`.
  - `proctor1` → HTTP 200, role `PROCTOR`.
  - mật khẩu sai → HTTP 401, code `UNAUTHORIZED`, trả lại đúng `requestId`.
  - token trả về dài 43 ký tự; DB lưu SHA-256, không lưu token raw.
- Quét log `final-smoke.out.log`/`final-smoke.err.log`: không thấy mật khẩu mẫu hay token.
- Lưu ý: dữ liệu volume phát triển trước reset đã bị xóa và không thể khôi phục nếu không có backup; container/volume sạch đã được tạo lại.

## JavaFX app-image

- Fat JAR được tạo bởi `mvn package`.
- `jpackage --type app-image` tạo thành công app-image Windows từ staging input riêng.
- `ToeicMonitor.exe` của bản cuối khởi động và còn sống sau 4 giây; sau đó tiến trình được dừng chủ động.
- Chưa kiểm trên máy Windows thứ hai và chưa thao tác login GUI bằng tay; đây là phần kiểm còn lại trước khi gọi gate đóng gói/LAN hoàn chỉnh.

## ProcessHandle

- Lệnh: `mvn --% -q -pl monitoring-spike exec:java -Dexec.args=--limit=200`.
- Trong 200 process đầu: 133 thiếu `command`, `startInstant` và `user`; probe in `<UNREADABLE>` thay vì suy diễn “sạch”.
- Đã quan sát được tên thật gồm `msedge.exe`, `Zalo.exe`, `Code.exe`, `java.exe`; chọn `msedge.exe` làm ứng dụng demo policy v1.
- Username đầy đủ và đường dẫn command không được đưa vào bằng chứng này.

## Script reset

- `scripts/reset-db.ps1` đã qua PowerShell parser (`PowerShell syntax: OK`).
- Chuỗi Docker mà script bao bọc đã chạy thật như ghi ở trên. Wrapper chưa được chạy riêng trong lượt này vì thao tác xóa volume lặp lại không cần thiết.

## Review fix before PR #1 merge

### Phạm vi và SHA

- Starting SHA: `ad3ba93dabb3323dbc9e8205a1ed6dda3eb4514a`.
- Code fix SHA: `961feca789fa8d9e3ded554d0ecb21fccbe43d47` — `fix(client): reject invalid login responses`.
- `mvn test` chạy trước commit, trên working tree chứa đúng code được commit tại SHA trên; `mvn package` chạy sau commit, tại SHA trên. Sau package chỉ thay đổi docs/evidence.
- `task.txt` là file người dùng untracked, được giữ nguyên ngoài commit theo xác nhận của người dùng.

### Môi trường và automated verification

- Người chạy: Codex Agent; terminal WSL gọi toolchain Windows 11 x64.
- Oracle JDK 21.0.8, Maven 3.9.11; JavaFX 21.0.12, Gson 2.13.2 theo POM. Không thêm thư viện.
- `mvn test`: PASS, BUILD SUCCESS, 5/5 module; 42/42 test; 0 failure/error/skipped.
- `mvn package`: PASS, BUILD SUCCESS, 5/5 module; 42/42 test; client fat JAR tạo thành công.
- Cơ cấu: AuthServiceTest 2/2; LoginApiClientTest 39/39; ProcessObservationTest 1/1. Root aggregator + 4 module con = 5 reactor entries; protocol chưa có test.
- Thêm 35 lượt test client: 4 case malformed/missing user/ADMIN/empty; 24 case 6 trường bắt buộc × missing/null/blank/number; 4 cấu trúc JSON sai; 1 response proctor hợp lệ; 2 case HTTP 401 JSON/non-JSON.
- Các case response sai dùng HttpServer local thật để đưa HTTP 200 vào sendAsync; future hoàn thành lỗi InvalidServerResponseException, không trả LoginResponse. Nhánh userMessage nhận CompletionException được kiểm trả đúng “Phản hồi từ server không hợp lệ.” và không giữ parser cause chứa dữ liệu response.
- Giữ 4 test cũ: URL LAN, scheme, async HTTP chậm bằng latch, map hai role. Không dùng sleep.
- Trích log build tự động (đã loại đường dẫn máy): [2026-10-03-review-build.txt](2026-10-03-review-build.txt). Log đầy đủ cục bộ: `logs/review-mvn-test.log`, `logs/review-mvn-package.log` (ignored, không commit).
- Maven shade cảnh báo module-info và tài nguyên trùng; package vẫn PASS. Không xác nhận lại khả năng chạy app-image từ cảnh báo/build này.

### UI và manual verification

- Review source: HTTP vẫn sendAsync → CompletableFuture → Platform.runLater; lỗi response quay về nhánh lỗi và bật lại nút login, không gọi showRoleScene. stop() vẫn đóng executor.
- Candidate GUI: NOT RUN — không có công cụ thao tác GUI thật trong phiên Agent.
- Proctor GUI: NOT RUN — cùng lý do.
- Wrong-password GUI: NOT RUN — cùng lý do.
- Server-off GUI behavior: NOT RUN — cùng lý do.
- GUI manual end-to-end: NOT RUN. Unit test thông báo UI không thay cho thao tác JavaFX thật.
- LAN second-machine test: NOT RUN — không có máy Windows thứ hai thật; không giả lập bằng localhost. Đây không phải FAIL.
- Không chạy lại PostgreSQL reset/Flyway, server login smoke, app-image hay ProcessHandle probe trong phiên review; các bằng chứng ở phần trước thuộc phiên skeleton trước.

### Giới hạn và kiểm tra trước commit

- Schema protocol không đổi; PROTOCOL.md không sửa. TRACKER.json không đổi.
- Không log JSON/token/password; lỗi parser được chuyển sang exception có thông báo cố định và không giữ cause.
- Kiểm tra source/diff và danh sách file staged: không thêm .env, token thật hay đường dẫn Windows cá nhân; credential test là MOCK.
- Chờ GUI/LAN thật và review chéo A/B/C trước quyết định merge. PR #1 không được merge trong phiên này.
