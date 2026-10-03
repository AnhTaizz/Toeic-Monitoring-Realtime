# Tiến độ hiện tại

Cập nhật lần cuối: 04/10/2026 — T1-B2 **PARTIAL / BLOCKED BY T1-A2** trên `feat/t1-b2-network-heartbeat-ui`, code `75d4b9f`. Main mới nhất là merge PR #1 `34a7835`. Adapter/state machine, heartbeat/backoff/fragment/shutdown và model khóa UI được kiểm bằng MOCK; `mvn test` và `mvn package` PASS 70/70, 5/5 module (lượt chạy 03/10). Chưa có authenticated WS thật; GUI/LAN NOT RUN, tracker giữ nguyên.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **1** (03–08/10)
- Gate kế tiếp: **04/10 nền tảng** — JavaFX build và thử đóng gói, WS có xác thực, ProcessHandle đọc được process.
- Gate sau đó: **08/10 prototype** — event lưu DB rồi lên dashboard, không trùng, heartbeat, package chạy trên máy khác.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | Còn kiểm LAN/review T1-A1; T1-A2 chưa có trên main | Build/test review PASS; reset/login local có evidence phiên trước | Kiểm LAN/review và chốt QD-03/endpoint/auth/scope cho T1-A2 | `feat/stage1-project-skeleton` |
| B | _chưa gán_ | T1-B2 PARTIAL / BLOCKED BY T1-A2 | Adapter + 28 test realtime MOCK PASS; tổng Maven 70/70; interface MonitoringTransport | Nhận contract A2, cài opener xác thực thật; smoke server-off/re-auth và GUI | `feat/t1-b2-network-heartbeat-ui` |
| C | _chưa gán_ | Còn review T1-C1 | Unit test PASS; contract v0 MOCK + probe ProcessHandle có evidence phiên trước | Review T1-C1; dùng interface B giao khi làm C3 | `feat/stage1-project-skeleton` |

## Đang bị chặn

- GUI manual end-to-end: **NOT RUN** — phiên Agent có terminal Windows/WSL, không có công cụ thao tác GUI thật. Cần người dùng chạy candidate/proctor/sai mật khẩu/server tắt.
- LAN second-machine test: **NOT RUN** — chưa có máy Windows thứ hai thật được cung cấp cho phiên này; không thay bằng localhost.
- Review chéo: **NOT RUN trong phiên B2/chưa có xác nhận mới**. Cần A review adapter B2; không ghi PASS thay reviewer. Giới hạn review trước merge được giữ trong evidence lịch sử.
- PR #1 đã merge trên main `34a7835`; GUI/LAN còn thiếu bằng chứng như nhật ký lịch sử. Schema protocol và status tracker không đổi.
- [03/10] B bị chặn phần authenticated real-WS integration — QD-03 còn trong bảng chờ, PROTOCOL chưa có endpoint/cách gửi credential, server chưa có WS handler, không thấy branch/PR A2 làm source of truth — cần A chốt T1-A2 trước gate 04/10. Không tự chọn auth/endpoint.
- Evidence B2: `evidence/t1-b2/2026-10-04-verification.md`, `2026-10-03-mvn-test.txt`, `2026-10-03-mvn-package.txt`; tất cả socket fixture đều MOCK. Chưa kiểm login/server thật, heartbeat server receipt, server-off GUI hay re-auth thật trong phiên này.

Ghi theo mẫu: `[ngày] Ai bị chặn — bởi cái gì — cần ai làm gì — hạn`.

## Contract đang chờ

| Hạn | Ai cung cấp → ai dùng | Nội dung | Trạng thái |
|---|---|---|---|
| 03/10, giờ đầu | C + mẫu auth của A → A, B | Login, role/attempt scope, WS event/ACK/heartbeat v0 | Login đã chạy; WS là MOCK rõ nhãn |
| 04/10 | B → C | Interface collector → network | Đã cài MonitoringTransport; xem README. Gửi event theo fixture MOCK; type/payload state chờ C chốt, real transport chờ A2 |
| 04/10 | A → B | Cách gửi credential HTTP/WS, phạm vi subscription | Chưa có |
| 09/10 | A → B, C | Import đề, answers/revision, requestId, writerEpoch, deadline/state, mã lỗi | Chưa có |
| 12/10 | A → B | Trạng thái đã chốt, takeover writer, state khi reconnect | Chưa có |
| 16/10 | A + B → C | Audio manifest, READY/start/interrupted | Chưa có |
| 16/10, khóa 18/10 | C → A, B | Monitoring epoch/base/sequence, định nghĩa metric | Chưa có |

## Câu hỏi mở cần nhóm hoặc thầy trả lời

1. Mỗi người thật sự có bao nhiêu giờ mỗi tuần? (kế hoạch cần khoảng 25; nếu chỉ 15 phải xin thu hẹp phạm vi)
2. Gán tên thật vào A/B/C: ai từng làm JavaFX, Spring, SQL transaction?
3. Ngày gặp thầy trong tuần 05–11/10? Có đủ hai máy Windows không?
4. Thầy có chấp nhận phạm vi TOEIC rút gọn, hướng đóng góp polling/delta và chính sách Listening gián đoạn không?
5. Kênh nộp cuối: USB qua lớp trưởng hay Google Drive?

## Dự báo giờ (cập nhật ngày 08/10)

| Vai | Giờ đã dùng | Giờ còn lại ước tính | Ghi chú |
|---|---:|---:|---|
| A | — | — | |
| B | — | — | |
| C | — | — | |
