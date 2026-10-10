# Sổ quyết định kỹ thuật

Ghi lại lựa chọn đã chốt để sau này không phải tranh luận lại, và để viết phần "các quyết định thiết kế" trong báo cáo. Các quyết định nền (stack, hợp đồng lưu/nộp, monitoring, Listening) đã có ở `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`; file này chỉ ghi những gì hợp đồng để ngỏ hoặc phát sinh khi làm.

## Mẫu

```markdown
## QD-03 · Cách gửi credential cho WebSocket
- Ngày: 2026-10-04 · Người quyết: A · Duyệt: C
- Bối cảnh: hợp đồng cấm để token trong URL query.
- Lựa chọn: gửi token trong header lúc handshake.
- Đã cân nhắc: message AUTH đầu tiên sau khi mở kết nối (phải giữ kết nối chưa xác thực một lúc).
- Hệ quả: B đặt header khi tạo WebSocket; server từ chối handshake nếu thiếu.
- Liên quan: T1-A2, T1-B2, AT02
```

## Đang chờ quyết

| Mã | Cần quyết | Ai quyết | Hạn | Task |
|---|---|---|---|---|
| QD-06 | Cách chạy test trên PostgreSQL thật (DB test cục bộ hay container) | A | 12/10 | T2-A4 |
| QD-07 | Định dạng audio và cách đóng gói tài nguyên | B | 08/10 | T1-B4 |
| QD-09 | Quy tắc chấm điểm nội bộ (câu sai, câu trống) | A | 12/10 | T2-A3 |
| QD-10 | Giữ hay cắt delta khỏi bản chính | C, B duyệt | 20/10 | T3-C4 |

Khi chốt, chuyển dòng tương ứng xuống mục dưới theo mẫu.

## Đã chốt

## QD-03 · Bearer header cho REST và WebSocket handshake
- Ngày: 2026-10-04 · Người quyết: A theo task T1-A2 · Review C: chưa diễn ra trong phiên Agent, không ghi duyệt thay C.
- Lựa chọn: `Authorization: Bearer <token>` cho REST `/api/**` sau login và raw WS `ws://<server>:<port>/ws/v1/realtime`. Token chỉ xuất hiện ở login JSON body và header truyền qua mạng; không log, không lưu raw trong DB/session attributes, không dùng URL query.
- Lý do: cùng semantics REST, handshake từ chối ngay credential không hợp lệ; Java 21 WebSocket.Builder thực sự gửi được header và Spring/Tomcat đọc/xác thực được.
- Alternative đã cân nhắc: AUTH message đầu tiên sau upgrade; không chọn vì header hoạt động qua network thật, không cần giữ socket chưa xác thực. Không dùng subprotocol/query để mang token.
- Evidence: AuthenticatedNetworkTest dùng real HTTP/WS với MOCK session/scope store; smoke `scripts/smoke-a2.ps1` dùng production Spring app + PostgreSQL thật, login → Bearer handshake → heartbeat/ACK, reject thiếu/sai/expired/revoked và kiểm revoke trên WS đang mở. Log sạch và SHA ở `evidence/t1-a2/2026-10-04-verification.md`.
- Hệ quả cho B: mỗi reconnect mở socket mới và gửi Authorization header lại; handle HTTP 401/503; ERROR UNAUTHORIZED trên WS đi kèm close 1008. Heartbeat unscoped chỉ là transport khi login scope rỗng; B cần cho phép attemptId null và đọc heartbeat ACK/ERROR, không tạo scope giả. Chi tiết field ở PROTOCOL.
- Scope: production AttemptScopeAuthorizer deny unknown/all attempts chưa được cấp; own/proctor assignment chỉ MOCK trong test tới khi có schema thật. Heartbeat ACK không phải event commit/presence.
- Liên quan: T1-A2, T1-B2, AT02. Không đổi TRACKER.json, không sửa nhánh B2 hay triển khai A3/A4.

## QD-01 · Phiên bản nền tảng chặng 1
- Ngày: 2026-10-03 · Người quyết: A + B · Review: C
- Lựa chọn: Java/JDK 21, JavaFX 21.0.12 LTS, Spring Boot 4.1.1, PostgreSQL image major `18` (lần chạy hiện tại là 18.6), Maven 3.9+.
- Đã chạy: Oracle JDK 21.0.8, Maven 3.9.11, Docker 29.4.3, Docker Compose 5.1.3 trên Windows 11 x64.
- Hệ quả: server/client cùng target Java 21; `docker-compose.yml` hiện có tiếp tục được dùng, không tạo compose thứ hai.
- Liên quan: T1-A1, T1-B1.

## QD-02 · Flyway quản lý schema và reset bằng volume phát triển
- Ngày: 2026-10-03 · Người quyết: A · Review: C
- Lựa chọn: migration SQL có version trong `server/src/main/resources/db/migration`; `scripts/reset-db.ps1 -Force` xác minh đúng volume `toeic-pgdata`, xóa volume rồi dựng PostgreSQL sạch. Flyway chạy khi server khởi động.
- Hệ quả: reset làm mất toàn bộ dữ liệu DB phát triển và không dùng cho production; không trộn `schema.sql` với Flyway.
- Đã kiểm: xóa volume, tạo lại container, Flyway chạy V1 và login lại được.
- Liên quan: T1-A1.

## QD-04 · Mã lỗi protocol v0
- Ngày: 2026-10-03 · Người quyết: C + A · Review: B
- Lựa chọn: `UNAUTHORIZED`, `FORBIDDEN`, `INVALID_INPUT`, `INVALID_STATE`, `STALE`, `CONFLICT`, `EXPIRED`, `RETRYABLE_SERVER_ERROR`; lỗi có `retryable`, `requestId`, `traceId`.
- Hệ quả: client quyết định theo mã, không theo câu thông báo. Chi tiết ở `docs/PROTOCOL.md`.
- Liên quan: T1-C1, T1-A1, T1-B1.

## QD-05 · Tầng truy cập DB bằng Spring JdbcClient
- Ngày: 2026-10-03 · Người quyết: A · Review: C
- Lựa chọn: Spring `JdbcClient`/JDBC thay vì JPA để SQL, transaction và lock của các task sau nhìn thấy rõ.
- Hệ quả: mapping record và câu SQL viết tường minh; PostgreSQL JDBC driver là dependency runtime.
- Liên quan: T1-A1, T2-A2–T2-A4.

## QD-08 · Process policy v1 cho demo
- Ngày: 2026-10-03 · Người quyết: C · Review: B
- Lựa chọn: so tên file executable không phân biệt hoa/thường. Policy `process-policy-v1` gồm browser (`chrome.exe`, `msedge.exe`, `firefox.exe`), ứng dụng trao đổi (`zalo.exe`, `teams.exe`, `discord.exe`) và điều khiển từ xa (`anydesk.exe`, `teamviewer.exe`).
- Ứng dụng demo: `msedge.exe`; probe hiện tại cũng quan sát được `Zalo.exe`.
- Giới hạn: process thiếu `command`, `startInstant` hoặc `user` mang chất lượng `UNREADABLE`, không được coi là sạch. Danh sách chỉ phục vụ demo kỹ thuật, chưa phải chính sách thi thật.
- Liên quan: T1-C1, T1-C2.

## QD-09 · Identity khi thiếu thời điểm bắt đầu process
- Ngày: 2026-10-04 · Người quyết: C · Human review B: NOT RUN.
- Lựa chọn: C3 giữ identity C2 `(collectorSessionId,pid,startInstant nullable)`, không dùng metadataQuality để phân biệt. Start null→known hoặc known→null coi là identity mới, thay vì đoán chúng là cùng process.
- Lý do: không đủ dữ liệu để chứng minh PID chưa bị tái sử dụng; ưu tiên khai rõ quan sát và bất định thay vì gộp có thể mất event.
- Hệ quả: metadata hồi phục có thể phát thêm event dù process thực chưa đổi. PID reuse với start luôn null có thể không phân biệt nếu không quan sát được khoảng vắng. Scan lỗi không coi là tập rỗng; polling vẫn có thể bỏ sót process sống giữa hai poll. Hai giới hạn này không được diễn giải thành “máy sạch”.
- Kiểm chứng: MonitoringDeliveryTest missingStart/PID reuse/scan failure; PROTOCOL C3, evidence/t1-c3/2026-10-04-verification.md.
- Liên quan: T1-C3; không thay contract state/reducer chặng2.

## QD-10 · Presence theo heartbeat trong một server JVM

- Ngày: 2026-10-04 · Người quyết: A theo task T1-A4 · Human C review: NOT RUN.
- Lựa chọn: tổng hợp association socket/collector còn heartbeat hợp lệ theo attempt, deadline đơn điệu trong JVM; snapshot và revision cùng interruption lưu PostgreSQL V4. Default6s/scan500ms, tối đa4096attempt/8socket mỗi attempt; khóa theo attempt và DB CAS, push sau commit ngoài lock.
- Lý do: socket cũ đóng không được làm UNKNOWN socket mới đang sống; chỉ dựa close frame không phát hiện hard-kill. UTC là timestamp hiển thị/lịch sử, wall clock chỉnh không quyết định deadline. Revision giúp B3 bỏ HTTP/WS update cũ dù push có thể đến lệch thứ tự.
- Startup: persisted ONLINE → UNKNOWN/SERVER_RESTART, tăng revision trước phục vụ; không dùng nanoTime JVM cũ, không bịa downtime gap/time. Heartbeat mới phục hồi ONLINE; interruption trước đó được giữ và ghi recoveredAt.
- Phạm vi: một server JVM writer trên schema. Không full syncEpoch/reducer hay nhiều server writer; ONLINE xác nhận liên lạc, UNKNOWN không kết luận gian lận. Overflow V3 client-reported tách server-detected HEARTBEAT_TIMEOUT V4.
- Kiểm chứng: fake ticker/wall-clock/latch/association unit; REAL PostgreSQL/B2/C3/hard-kill/multi-client/reconnect/COMMIT failure/restart/cleanup. Contract PROTOCOL T1-A4; evidence/t1-a4/2026-10-04-verification.md. B3 dashboard/parser và GUI/LAN NOT RUN.

## QD-11 · C4 đo toàn JSON và ghi log có giới hạn

- Ngày: 2026-10-05 · Vai C theo task C4 · Human B review: NOT RUN.
- Lựa chọn: byte UTF-8 của toàn JSON envelope+payload tại transport, tách attempted/write-completed/write-failed/full-RX; BUSINESS_ACK không cộng byte. Server hook ở delegate bên trong ConcurrentWebSocketSessionDecorator để không coi enqueue là write. Tổng gửi cộng CLIENT TX+SERVER TX theo một outcome, không cộng RX.
- Recorder opt-in mặc định tắt; queue1024/drop-new, counter độc lập, JSONL writer daemon/flush2000ms. I/O lỗi không thay delivery; thiếu FINAL/drop/mismatch phải báo INCOMPLETE. Không disk-queue event, không thêm telemetry framework.
- Lý do: định nghĩa byte tái kiểm từ đúng chuỗi, không đếm đôi retry/fragment/outcome, không chặn FX/WS bằng file I/O. Chưa đo overhead hay full/delta; WMI chỉ khảo sát nguồn chính thức, giữ ProcessHandle polling hiện có.
- Kiểm chứng và giới hạn: [schema](MONITORING_MEASUREMENTS.md), [survey](PROCESS_MONITORING_SURVEY.md), [evidence C4](../evidence/t1-c4/2026-10-05-verification.md); không tự đổi kế hoạch01–03 hoặc TRACKER.

## QD-12 · Full snapshot v1 có trạng thái RAM và quyền theo epoch/socket

- Ngày: 10/10/2026 · Vai C theo T2-C1 · Human A/B review: NOT RUN.
- Chọn một store RAM trong một server JVM; ACK FULL xác nhận reducer đã nhận RAM, không xác nhận commit DB. Event/gap/interruption tiếp tục dùng storage hooks A và PostgreSQL hiện có. Không thêm hoặc sửa migration.
- OPEN cấp UUID syncEpoch gắn attempt/user/collector/socket, UNSYNCED sequence0; full đầu bắt buộc1. Full mới thay toàn tập nên được phép nhảy sequence, full rỗng hợp lệ. Retry cùng nội dung trong cửa sổ64 ID ACK lại; khác nội dung CONFLICT; cũ/epoch hoặc socket sai STALE. Socket mở trước không giành lại epoch của socket mở sau. Giữ64 OPEN ID gần nhất để retry OPEN đã nghỉ không mở lại generation trên socket dùng chung.
- Mỗi lần cập nhật tăng revision theo serverInstanceId, để B gộp HTTP/push qua reconnect không so sequence của hai epoch. Publish ngoài lock; auth/epoch/reducer cùng lock nhằm tránh hai request kiểm tra trên một state rồi ghi đè nhau. Client kiểm generation collector và socket ngay trước socket write; callback cũ cũng bị bỏ. Đóng collector không đóng WS dùng chung.
- Giới hạn:128process/full, queue16 +1pendingfull, retry5; server4096attempt,64full ID/attempt, STALE6s và TTL5phút từ OPEN/full mới nhất. TTL bỏ state hiện tại, giữ lịch sử DB; restart mất RAM, phải OPEN/full1. Lỗi scan không gửi tập rỗng, quá giới hạn không cắt tập. Policy/identity theo QD-08/QD-09.
- Lý do: đây là baseline kiểm tính đúng và điểm tích hợp tối thiểu; chưa có yêu cầu durability/full nhiều server, delta hoặc benchmark. Không suy ra máy sạch từ tập rỗng hay UNKNOWN.
- Contract: [PROTOCOL](PROTOCOL.md#t2-c1--contract-full-snapshot-baseline-v1); bằng chứng và giới hạn: [T2-C1](../evidence/t2-c1/2026-10-10-verification.md).

### QD-12 bổ sung — worker bảo trì full riêng (10/10/2026)

- Review của người dùng chỉ ra `@Scheduled maintain()` chưa được kích hoạt ở30fa6d9. Chọn bean `MonitoringStateMaintenance` sở hữu một ScheduledThreadPoolExecutor daemon, fixed delay theo `toeic.state.scan-ms`, cùng cách dùng worker riêng của presence A4. Bỏ annotation Scheduled trên maintain để tránh chạy trùng nếu A bật scheduling sau này.
- Không bật EnableScheduling toàn app: thao tác đó đồng thời kích hoạt ExamTimeoutService, vượt phạm vi sửa C1. Scheduler bài thi của A vẫn là vấn đề riêng cần A xử lý/phối hợp; chưa sửa hoặc nghiệm thu nó trong task này.
- Worker bắt RuntimeException không lộ cause/credential để scan sau tiếp tục. PreDestroy hủy/interrupt, đợi tối đa5s; store PreDestroy xóa entries/chặn request muộn. Phụ thuộc Spring maintenance→store giúp hủy worker trước khi hủy store.
- Test Spring/WS/PG giữ socket và heartbeat hoạt động nhưng không gửi full/CLOSE, không gọi maintain/read state: cần tự push STALE, rồi TTL tombstone và OPEN lượt khác thành công khi capacity1. Unit riêng kiểm tự gọi/retry sau lỗi/dừng task đang chạy và dọn RAM. [Evidence bổ sung](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md).
