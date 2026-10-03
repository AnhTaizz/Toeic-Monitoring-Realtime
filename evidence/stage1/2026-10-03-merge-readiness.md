# Stage 1 — verification cuối trước merge PR #1

- Ngày: 03/10/2026 (UTC+7); người kiểm: Codex Agent.
- Branch: `feat/stage1-project-skeleton`; HEAD bắt đầu/được đối chiếu: `ceb8131a558660057cf62c46f2466db8515b9b4e`.
- Code fix: `961feca789fa8d9e3ded554d0ecb21fccbe43d47`. `git diff 961feca HEAD -- client protocol server monitoring-spike pom.xml` không có thay đổi.
- Phiên này chỉ cập nhật PR description và docs/evidence; không sửa source, PROTOCOL.md hay TRACKER.json. Không merge hoặc push main. `task.txt` là file người dùng được giữ ngoài commit.

## PR và automated evidence

- [PR #1](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/1): OPEN, NOT MERGED; base main, head feat/stage1-project-skeleton.
- Description đã được cập nhật thành Maven test **42/42 PASS**, Maven package **PASS**, 5 module; không còn dòng 7/7 tests cũ. Thêm mô tả reject login responses sai và commits 961feca/ceb8131; liệt kê GUI/LAN/cross-review còn chờ.
- Lần gọi update đầu timeout ở bước approval review; retry một lần được công cụ xác nhận thành công.
- PASS build/test là lượt chạy thật trước, không phải lượt chạy mới trong phiên này. Bằng chứng: [review-build.txt](2026-10-03-review-build.txt) và [smoke/review-fix](2026-10-03-skeleton-smoke.md).
- Không chạy lại Maven vì source không đổi và không có lỗi mới được xác minh.

## GUI manual

| Case | Trạng thái | Lý do |
|---|---|---|
| Candidate login → giao diện thí sinh | NOT RUN | Phiên terminal Windows/WSL không có công cụ thao tác GUI thật |
| Proctor login → giao diện giám thị | NOT RUN | Cùng lý do |
| Wrong password → lỗi, app không crash | NOT RUN | Cùng lý do |
| Server-off / bad URL → lỗi kết nối, app không crash | NOT RUN | Cùng lý do |

Không khởi động DB/server/app-image trong phiên này vì không thể thao tác các bước GUI thật. Không có screenshot/log GUI mới; bằng chứng app-image khởi động phiên skeleton không được dùng làm GUI login PASS.

## LAN second machine

- Second machine available: NO — không có máy Windows thứ hai thật được cung cấp cho phiên này.
- Candidate/proctor/wrong password over LAN: NOT RUN.
- Không dùng localhost hay HTTP test server local thay cho máy thứ hai; không thay đổi firewall.

## Cross-review người thật

- Đã đọc danh sách review submissions và timeline comments của PR #1 qua GitHub connector: **0 reviews, 0 comments** tại thời điểm kiểm tra.
- Tài liệu tiến độ chưa gán tên thật vào A/B/C. Không dùng Agent hoặc vai giả lập để thay người review.
- Đã hỏi xác nhận review; người dùng trả lời “Oke rồi”. Đã hỏi lại xem cả A→B, B→C, C→A thực sự review code tại ceb8131, hiểu code và không có blocker hay chưa; chưa nhận xác nhận rõ tại thời điểm ghi bằng chứng.
- Không kết luận các review chưa từng diễn ra ngoài GitHub; chỉ ghi chưa đủ bằng chứng để xác nhận PASS.

| Review | Trạng thái | Cần xác nhận |
|---|---|---|
| A reviewed B | NOT RUN / chưa xác nhận | Người thật đã đọc client, hiểu code và kết luận/blocker |
| B reviewed C | NOT RUN / chưa xác nhận | Người thật đã đọc contract/spike, hiểu code và kết luận/blocker |
| C reviewed A | NOT RUN / chưa xác nhận | Người thật đã đọc server/DB/login, hiểu code và kết luận/blocker |

## Decision

- Ready to merge: **NO**.
- Remaining blockers: GUI end-to-end thật; LAN máy Windows thứ hai; xác nhận đủ cross-review A/B/C.
- Merge performed: **NO**. TRACKER changed: **NO**.
