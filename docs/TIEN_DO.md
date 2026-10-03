# Tiến độ hiện tại

Cập nhật lần cuối: 04/10/2026 — T1-A2 code-complete tại `7ac62c4` trên `feat/t1-a2-authenticated-websocket`, base main merge PR #1 `34a7835`. Bearer REST/WS, QD-03 và heartbeat/ACK transport đã cài; `mvn test` và `mvn package` PASS 91/91, 5/5 module. PostgreSQL production smoke PASS. Nhánh B2 riêng chưa tích hợp/merge; tracker giữ nguyên.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **1** (03–08/10)
- Gate kế tiếp: **04/10 nền tảng** — JavaFX build và thử đóng gói, WS có xác thực, ProcessHandle đọc được process.
- Gate sau đó: **08/10 prototype** — event lưu DB rồi lên dashboard, không trùng, heartbeat, package chạy trên máy khác.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | T1-A2 code-complete; handoff B2 | 49 test mới + PostgreSQL/WS production smoke PASS; QD-03 chốt | B tích hợp contract; review C còn chờ, không tự làm A3 | `feat/t1-a2-authenticated-websocket` |
| B | _chưa gán_ | T1-B2 PARTIAL trên nhánh riêng; chờ tích hợp contract A2 | Adapter/mock tests/interface có ở `edd50cf`, chưa trên main | Đồng bộ main A2, cài opener Bearer, ACK/ERROR và unscoped heartbeat; GUI thật | `feat/t1-b2-network-heartbeat-ui` |
| C | _chưa gán_ | Còn review T1-C1; chưa chạy review A2 trong phiên | Unit test/probe có evidence lịch sử | Review auth/WS A2; event persistence/queue chờ task A3/C3 | `feat/stage1-project-skeleton` |

## Đang bị chặn

- GUI manual end-to-end: **NOT RUN** — phiên Agent có terminal Windows/WSL, không có công cụ thao tác GUI thật. Cần người dùng chạy candidate/proctor/sai mật khẩu/server tắt.
- LAN second-machine test: **NOT RUN** — chưa có máy Windows thứ hai thật được cung cấp cho phiên này; không thay bằng localhost.
- Review C→A2: **NOT RUN trong phiên Agent**; không ghi duyệt thay thành viên. Task.txt cho phép merge A2 khi các checks kỹ thuật/PR mergeability PASS; trạng thái Git cuối xem report.
- PR #1 đã merge trên main `34a7835`; các mục GUI/LAN thiếu chứng cứ lịch sử vẫn NOT RUN.
- A2 không còn blocker auth/WS: đã kiểm header handshake trên Spring/Tomcat + Java21 và PostgreSQL thật. Scope provider production hiện deny mọi attempt chưa có assignment; đây là giới hạn có chủ đích khi schema attempt chưa cài, không dùng scope MOCK production.
- B2 vẫn cần phiên tích hợp riêng: hook production, heartbeat/ACK attemptId null, ERROR handling, server-off/re-auth/GUI. Không coi A2 network smoke là B2 acceptance hoặc event/presence đã xong.
- Bằng chứng A2: `evidence/t1-a2/2026-10-04-verification.md`. TRACKER.json giữ nguyên.

Ghi theo mẫu: `[ngày] Ai bị chặn — bởi cái gì — cần ai làm gì — hạn`.

## Contract đang chờ

| Hạn | Ai cung cấp → ai dùng | Nội dung | Trạng thái |
|---|---|---|---|
| 03/10, giờ đầu | C + mẫu auth của A → A, B | Login, role/attempt scope, WS event/ACK/heartbeat v0 | Login + heartbeat transport/ACK thật; event/state vẫn MOCK/CHƯA CÓ |
| 04/10 | B → C | Interface collector → network | MonitoringTransport trên nhánh B2 riêng; chưa tích hợp main |
| 04/10 | A → B | Cách gửi credential HTTP/WS, phạm vi subscription | QD-03 chốt Bearer header; /ws/v1/realtime; unscoped transport heartbeat; subscription/assignment nghiệp vụ chưa cài |
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
