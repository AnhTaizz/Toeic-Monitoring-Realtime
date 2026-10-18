# T3-A4 · Verification 18/10/2026

## Source và phạm vi

- Branch: `test/t3-a4-transaction-multiclient-regression`, kế thừa T3-A3 tại `8fef5bb` (base main `6ac8835`).
- Bổ sung regression PostgreSQL cho ba tác vụ save, submit và timeout cùng tranh chấp một row qua deadline thật.
- Thêm runner `scripts/smoke-t3a4.ps1` chạy lại được toàn bộ acceptance liên quan trên PostgreSQL 18.6, HTTP và WebSocket thật.
- Mỗi smoke sở hữu schema `TEST` ngẫu nhiên và tự xóa; không reset bảng phát triển.

## Lượt xác minh cuối

- Run ID: `T3A4-e1f00de1-e422-41c3-9955-e7c5cc82ad3d`.
- Log tái lập cục bộ: `server/target/t3a4-runtime/<runId>/` (artifact build, không commit).
- Lệnh:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3a4.ps1
mvn -B -o package
```

| Kiểm tra | Kết quả | Mức bằng chứng |
|---|---:|---|
| `ExamRuntimePostgresTest,AuthenticatedNetworkTest` | 74 PASS, 0 fail/error/skip | REAL Spring/PostgreSQL/HTTP; khóa row và waiter quan sát qua `pg_stat_activity` |
| `scripts/smoke-t2a4.ps1` | PASS AT01/AT07/AT09/AT10 | REAL PostgreSQL/HTTP; hai candidate và proctor đăng nhập |
| `scripts/smoke-a3.ps1` | PASS | REAL PostgreSQL/HTTP/WS; hai candidate, assigned proctor và thêm unassigned proctor |
| Full `mvn package` | 912 PASS, 0 fail/error, 58 opt-in skip | Toàn reactor mặc định |

## Đối chiếu acceptance

- **AT01:** candidate 2 bị từ chối `403` khi autosave, submit, takeover, đọc exam và status của attempt candidate 1; sau khi candidate 1 submit, status chéo vẫn `403`.
- **Save/submit/timeout đồng thời:** test giữ khóa `FOR UPDATE` qua deadline, khởi động save và submit bất đồng bộ rồi chờ đủ ba waiter gồm scheduler timeout. Sau khi nhả khóa, hai write đều `409`; trạng thái cuối chỉ là `TIMED_OUT`, revision/answers đã ACK trước đó giữ nguyên, score tính đúng một lần và không có submit-request row.
- **IT02/malformed client:** candidate A gửi JSON hỏng và path không hợp lệ; candidate B vẫn nhận heartbeat ACK. Một kết nối lỗi không làm chết server hay ảnh hưởng kết nối còn lại.
- **Nhiều client:** smoke xác minh hai candidate cùng một assigned proctor; phần four-client isolation thêm unassigned proctor, chặn leak scope/push và vẫn giữ client hợp lệ hoạt động.
- **Retry/idempotency:** retry WebSocket đồng thời trả hai ACK nhưng chỉ một DB row và một warning; submit retry trả cùng một kết quả cuối.

## Giới hạn trung thực

- Tất cả kết nối chạy loopback trên một máy; LAN hai máy: NOT RUN.
- GUI end-to-end và human C review: NOT RUN.
- 58 test opt-in của full package bị skip theo cấu hình mặc định; các smoke PostgreSQL/HTTP/WS trực tiếp cần cho T3-A4 đã chạy riêng và PASS.
- Tracker gốc giữ trạng thái cho đến khi reviewer C cập nhật; không tự ghi duyệt thay C.
