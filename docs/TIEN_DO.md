# Tiến độ hiện tại

Cập nhật lần cuối: 03/10/2026 — có `.gitignore`, `.gitattributes` và `docker-compose.yml` cho PostgreSQL (đề xuất, chờ A chốt QD-01/QD-06); chưa có source.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **1** (03–08/10)
- Gate kế tiếp: **04/10 nền tảng** — JavaFX build và thử đóng gói, WS có xác thực, ProcessHandle đọc được process.
- Gate sau đó: **08/10 prototype** — event lưu DB rồi lên dashboard, không trùng, heartbeat, package chạy trên máy khác.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | — | — | T1-A1: dựng server, schema, tài khoản mẫu | — |
| B | _chưa gán_ | — | — | T1-B1: khung JavaFX, login hai role | — |
| C | _chưa gán_ | — | — | T1-C1: phát mẫu message v0 trong giờ đầu | — |

## Đang bị chặn

_Chưa có._

Ghi theo mẫu: `[ngày] Ai bị chặn — bởi cái gì — cần ai làm gì — hạn`.

## Contract đang chờ

| Hạn | Ai cung cấp → ai dùng | Nội dung | Trạng thái |
|---|---|---|---|
| 03/10, giờ đầu | C + mẫu auth của A → A, B | Login, role/attempt scope, WS event/ACK/heartbeat v0 | Chưa có |
| 04/10 | B → C | Interface collector → network | Chưa có |
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
