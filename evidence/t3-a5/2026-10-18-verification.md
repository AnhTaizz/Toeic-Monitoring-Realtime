# T3-A5 · Verification 18/10/2026

## Phạm vi

- Branch: `docs/t3-a5-network-concurrency-report`.
- Base: T3-A4 `44b426d`; report commit: `411be40`.
- Đầu ra: [bản nháp kiến trúc mạng và đồng thời](../../docs/T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md).
- Task chỉ viết tài liệu từ source và evidence đã chạy; không đổi production/test code.

## Kiểm tra tài liệu

| Kiểm tra | Kết quả |
|---|---:|
| 36 Markdown local links resolve từ thư mục `docs/` | PASS |
| `git diff --check` | PASS |
| Audit claim trên 6 source owner | PASS |

Audit source kiểm trực tiếp:

- endpoint và message limit trong `RealtimeWebSocketConfiguration`;
- decorated writer, auth/assignment revalidation trong `RealtimeSessionRegistry`;
- auth mỗi message, commit-before-ACK và invalid input isolation trong `RealtimeWebSocketHandler`;
- `FOR UPDATE OF a`, `clock_timestamp()`, `savedRevision`, `writerEpoch` trong `ExamService`;
- scheduler pool size 1 trong `ExamSchedulingConfiguration`;
- transaction, no-socket boundary và event conflict identity trong `MonitoringEventService`.

## Nguồn bằng chứng runtime được trích dẫn

- T3-A4 `T3A4-e1f00de1-e422-41c3-9955-e7c5cc82ad3d`: targeted 74 PASS; AT01/07/09/10 PASS; REAL PostgreSQL/HTTP/WS malformed-client và four-client isolation PASS; full package 912 PASS, 58 opt-in skip.
- T3-A3: deferred COMMIT failure không success ACK/warning; REAL restart đổi instance/epoch, giữ DB history và buộc full resync.

T3-A5 không chạy lại runtime test vì commit đầu ra chỉ là Markdown và không thay source. Báo cáo ghi rõ đây là reuse evidence, không tạo PASS mới.

## Giới hạn

- LAN hai máy, GUI end-to-end, load test và human C review: NOT RUN.
- Chưa có throughput/latency/resource table; phần đó chờ T4-A2/T4-A3.
- Báo cáo vẫn là DRAFT; tracker gốc giữ nguyên cho tới review của C.
