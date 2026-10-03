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
