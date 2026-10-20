# Bằng chứng tích hợp EX-06 + Proctor redesign + PROCTORED_V2 (20/10/2026)

Nhánh `integrate/ex06-proctor-20261020`. **Dữ liệu là fixture MOCK `mock-ex02-200` (CC0, tự tạo), không phải đề TOEIC thật.**

## `e2e/` — server + PostgreSQL + WebSocket thật

- **Source:** commit `182f5f9`, chưa sửa gì thêm. Test: `ProctorV2E2eGuiTest`, PASS 3/3 lượt liên tiếp trên commit này. Thư mục này lưu ảnh và log của lượt cuối.
- **Trước đó** trên working tree cùng nội dung code: 1 lượt FAIL. Nguyên nhân là chính test đọc node UI trước khi FX render; đã sửa bằng cách chờ UI. Sau khi sửa: 5/5 PASS.
- **Máy:** cloud container Linux 6.18 x86_64 (một máy). Thí sinh và giám thị chạy cùng một JVM test, mỗi bên một Stage và một token riêng.
- **Phần mềm:**
  - OpenJDK 21.0.12.1, JavaFX 21.0.12, Maven 3.9.11.
  - **PostgreSQL 16.15** (bản đã khóa là 18; bản 18 chưa chạy ở đây).
  - Xvfb 1600×1000. Âm thanh qua ALSA `null` nên MediaPlayer chạy thật nhưng không ai nghe.
- **Server:** `java -jar server/target/server-0.1.0-SNAPSHOT.jar`, build từ cùng commit:
  - DB `toeic_int_e2e` mới tạo.
  - Flyway áp V1–V12, V14, V15 (không có V13; xem mục Flyway trong `docs/INTEGRATION_EX06_PROCTOR_20261020.md`).
  - Media root trỏ vào `docs/plan-v2/fixtures/ex02/assets`.
- **Tài khoản:** `proctor1`, `candidate1`, `candidate2` (seed MOCK). Mật khẩu đặt qua `TOEIC_SEED_PASSWORD`, không ghi ở đây.
- **Thao tác bằng UI thật:**
  - Giám thị: đăng nhập, mở "Điều khiển ca", tab V2, chọn đề, nhập ID thí sinh, tạo ca, theo dõi READY, START qua hộp xác nhận.
  - Thí sinh: Audio READY bằng 4 nút, Part 1 Visual READY, làm bài 200 câu, chọn câu qua palette, nộp qua hộp xác nhận, xem kết quả.
- **Dùng HTTP trực tiếp** (không qua UI giám thị): import fixture, đọc trạng thái server để assert, và các probe âm.
- `e2e-log.txt`: log từng bước (HTTP code, sessionId/attemptId/startId do server cấp).
- Ảnh `p*` là của giám thị, `c*` là của thí sinh. `c4-result-*` chụp mọi cửa sổ đang mở khi có kết quả, trong đó `c4-result-2` là hộp kết quả của server.

## `gui-mock/` — JavaFX Stage thật, API giả (MOCK)

Do `ProctorV2SessionPaneGuiTest` chụp với `TOEIC_PROCTOR_SCREENSHOTS=true`. Ảnh dùng để xem bố cục; **không phải bằng chứng tích hợp server**.

## NOT RUN

- Windows, đóng gói `jpackage`/MSI, LAN với hai máy.
- PostgreSQL 18.
- Nghe bằng loa thật.
- Token hết hạn theo thời gian thật. Ở đây mới thử token sai → 401.
- Reconnect/mất mạng giữa bài trong luồng tích hợp này.
