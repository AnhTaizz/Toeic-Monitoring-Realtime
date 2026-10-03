# Tiến độ hiện tại

Cập nhật lần cuối: 03/10/2026 — verification cuối PR #1 tại HEAD `ceb8131`: description đã phản ánh 42/42 test PASS và package PASS từ lượt review-fix; source không đổi. GUI/LAN NOT RUN, cross-review chưa đủ xác nhận thực tế. Ready to merge: NO; tracker giữ nguyên.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **1** (03–08/10)
- Gate kế tiếp: **04/10 nền tảng** — JavaFX build và thử đóng gói, WS có xác thực, ProcessHandle đọc được process.
- Gate sau đó: **08/10 prototype** — event lưu DB rồi lên dashboard, không trùng, heartbeat, package chạy trên máy khác.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | Chờ kiểm LAN/review T1-A1 | Build/test review PASS; reset/login local có evidence phiên trước | Hoàn tất kiểm LAN và review trước quyết định merge PR #1 | `feat/stage1-project-skeleton` |
| B | _chưa gán_ | Chờ kiểm GUI/máy thứ hai T1-B1 | Review-fix response sai; 39/39 client test PASS; app-image có evidence phiên trước | Thao tác GUI end-to-end và kiểm máy thứ hai | `feat/stage1-project-skeleton` |
| C | _chưa gán_ | Chờ review T1-C1 | Unit test PASS; contract v0 MOCK + probe ProcessHandle có evidence phiên trước | Review T1-C1 trước quyết định merge PR #1 | `feat/stage1-project-skeleton` |

## Đang bị chặn

- GUI manual end-to-end: **NOT RUN** — phiên Agent có terminal Windows/WSL, không có công cụ thao tác GUI thật. Cần người dùng chạy candidate/proctor/sai mật khẩu/server tắt.
- LAN second-machine test: **NOT RUN** — chưa có máy Windows thứ hai thật được cung cấp cho phiên này; không thay bằng localhost.
- Review chéo A→B, B→C, C→A: **NOT RUN/chưa đủ xác nhận**. GitHub có 0 review/0 comment; câu “Oke rồi” đang chờ làm rõ cả ba lượt đã review thực tế và không có blocker. Không ghi PASS thay reviewer.
- PR #1 description đã cập nhật (42/42 test, commits review-fix); Ready to merge: **NO**. Schema protocol và status tracker không đổi. Bằng chứng cuối: `evidence/stage1/2026-10-03-merge-readiness.md`.

Ghi theo mẫu: `[ngày] Ai bị chặn — bởi cái gì — cần ai làm gì — hạn`.

## Contract đang chờ

| Hạn | Ai cung cấp → ai dùng | Nội dung | Trạng thái |
|---|---|---|---|
| 03/10, giờ đầu | C + mẫu auth của A → A, B | Login, role/attempt scope, WS event/ACK/heartbeat v0 | Login đã chạy; WS là MOCK rõ nhãn |
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
