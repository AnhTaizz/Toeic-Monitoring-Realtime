# Mục lục bằng chứng và log

`evidence/<task>/` là nơi tập trung bằng chứng được đưa lên GitHub để thành viên nhóm và công cụ chat web đối chiếu code, kết quả kiểm thử và giới hạn nghiệm thu. Mỗi dòng bên dưới dẫn tới báo cáo của task; báo cáo dẫn tiếp tới log, ảnh, raw, metadata và checksum tương ứng.

Để hiểu dự án trước khi viết prompt, đọc [tiến độ](../docs/TIEN_DO.md), [protocol](../docs/PROTOCOL.md) và [trạng thái kiểm thử](../docs/KIEM_THU.md), rồi mở bằng chứng đúng task. Kết quả thuộc SHA và môi trường ghi trong báo cáo, không mặc nhiên áp dụng cho commit mới hơn.

## Chặng 1

| Task | Nội dung | Báo cáo |
|---|---|---|
| Khởi tạo | Build, DB, login, process probe, package local | [Smoke](stage1/2026-10-03-skeleton-smoke.md), [kiểm tra trước merge](stage1/2026-10-03-merge-readiness.md) |
| T1-A2 | Xác thực REST và WebSocket | [Verification](t1-a2/2026-10-04-verification.md) |
| T1-A3 | Lưu event, ACK và cảnh báo | [Verification](t1-a3/2026-10-04-verification.md) |
| T1-A4 | Heartbeat, presence và lịch sử gián đoạn | [Verification](t1-a4/2026-10-04-verification.md) |
| T1-B2 | Adapter mạng và reconnect | [Verification](t1-b2/2026-10-04-verification.md), [tích hợp thật](t1-b2/2026-10-04-real-integration.md) |
| T1-B3 | Dashboard giám thị | [Verification](t1-b3/2026-10-04-verification.md) |
| T1-C2 | Collector ProcessHandle | [Verification](t1-c2/2026-10-04-verification.md) |
| T1-C3 | Queue, retry và gửi event | [Verification](t1-c3/2026-10-04-verification.md) |
| T1-C4 | Log đo, summary và khảo sát nguồn | [Verification](t1-c4/2026-10-05-verification.md) |

## Chặng 2

| Task | Nội dung | Báo cáo |
|---|---|---|
| T2-A1 | Import đề, tạo ca và lượt thi | [Verification](t2-a1/2026-10-05-verification.md) |
| T2-A2 | Autosave, revision và idempotency | [Verification](t2-a2/2026-10-05-verification.md) |
| T2-A3 | Submit, timeout và chấm điểm | [Verification](t2-a3/2026-10-05-verification.md) |
| T2-A4 | Takeover và tranh chấp PostgreSQL | [Verification](t2-a4/2026-10-05-verification.md) |
| T2-A5 | Giao dịch và bằng chứng quyền | [Verification](t2-a5/2026-10-05-verification.md) |
| T2-C1 | Full snapshot và process hiện tại | [Verification](t2-c1/2026-10-10-verification.md), [sửa scheduler và kiểm tra tự STALE/TTL](t2-c1/scheduling-fix/2026-10-10-verification.md) |
| T2-C2 | Event gửi bù và lịch sử gap | [Verification](t2-c2/2026-10-10-verification.md) |
| T2-C3 | Observation trace, replay và oracle | [Verification](t2-c3/2026-10-10-verification.md) |
| T2-C4 | Controlled process harness và ground truth độc lập | [Verification](t2-c4/2026-10-10-verification.md), [log kiểm tra](t2-c4/logs/) |

## Cách lưu log mới

- Lưu theo task trong `evidence/<task>/`; một lượt chạy bổ sung có thể dùng thư mục con riêng. Thêm báo cáo và liên kết vào mục lục này khi có bằng chứng mới.
- Transcript build/test dùng `.txt`; dữ liệu đo dùng JSONL/JSON/CSV; ảnh dùng PNG. Các định dạng này có thể commit theo cấu hình Git hiện tại. Log chạy local trong `logs/`, file `*.log`, `target/` và `/traces/` vẫn là dữ liệu tạm.
- Báo cáo ghi SHA, môi trường, cấu hình, lệnh, kết quả thật, giới hạn và đường dẫn artifact. Phân biệt REAL, MOCK, SIMULATED và REPLAY; giữ cả kết quả lỗi giúp giải thích thay đổi.
- Giữ raw nguyên bản và checksum theo quy ước của từng task. Summary phải sinh từ script; nếu nén artifact lớn, ghi cách giải nén và checksum dữ liệu gốc.
- Kiểm nội dung trước khi commit: không đưa mật khẩu, token, `.env` hoặc dữ liệu riêng tư vào bằng chứng. Mục lục dẫn tới báo cáo có thể đọc trực tiếp trên GitHub; artifact nén cần tải và giải nén để xem chi tiết.

Mục lục chỉ tổ chức bằng chứng đã có, không xác nhận task hoàn thành hoặc thay đổi trạng thái trong tracker.
