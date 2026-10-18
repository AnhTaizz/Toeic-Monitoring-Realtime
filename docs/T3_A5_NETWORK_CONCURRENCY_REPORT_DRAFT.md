# T3-A5 · Bản nháp báo cáo kiến trúc mạng và đồng thời

Trạng thái: **DRAFT để C review** · nguồn đối chiếu: nhánh T3-A4 tại `44b426d` · ngày 18/10/2026.

Bản nháp này mô tả implementation hiện có, không thay cho kết quả đo tải T4-A2/T4-A3. Các từ **REAL**, **MOCK** và **SIMULATED** giữ đúng nghĩa trong evidence; không suy rộng kiểm thử loopback thành kết quả LAN.

## 1. Kiến trúc server

```mermaid
flowchart LR
    C[JavaFX candidate] -->|HTTP(S) REST JSON| T[Spring MVC / embedded Tomcat]
    P[JavaFX proctor] -->|HTTP(S) REST JSON| T
    C <-->|WS(S) raw JSON envelope| W[Spring WebSocket handler]
    P <-->|WS(S) raw JSON envelope| W
    T --> A[Authentication + authorization]
    W --> A
    A --> E[Exam / audio / monitoring services]
    E -->|Spring JDBC transactions| D[(PostgreSQL)]
    E --> R[Current monitoring state in JVM RAM]
    S[Single-thread exam timeout scheduler] --> E
```

Server là ứng dụng Spring Boot gồm ba lớp trách nhiệm:

1. Lớp transport nhận REST và raw WebSocket, giới hạn message, ánh xạ JSON/envelope và trả response.
2. Lớp service xác thực lại quyền, thực thi state machine của attempt và đặt ranh giới transaction.
3. PostgreSQL lưu session, assignment, attempt, answers, idempotency record, kết quả và monitoring history. Current process state full/delta nằm trong RAM một JVM, nên restart bắt buộc client mở sync epoch mới và gửi full trước delta.

Source: [cấu hình server](../server/src/main/resources/application.properties), [WebSocket configuration](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketConfiguration.java), [server hook contract](MONITORING_SERVER_HOOKS.md), [T3-A3 evidence](../evidence/t3-a3/2026-10-18-verification.md).

## 2. Phần framework cung cấp và phần nhóm tự thiết kế

| Hạng mục | Framework/thư viện cung cấp | Nhóm cấu hình hoặc tự thiết kế | Bằng chứng |
|---|---|---|---|
| HTTP server | Spring MVC và embedded servlet container nhận request, dispatch thread, bind controller | Route `/api/v1`, DTO, error code, giới hạn nghiệp vụ và thứ tự kiểm tra | [server POM](../server/pom.xml), [network tests](../server/src/test/java/vn/edu/toeic/server/auth/AuthenticatedNetworkTest.java) |
| WebSocket | Spring quản lý handshake, connection lifecycle và callback text message | Raw endpoint `/ws/v1/realtime`, envelope v0, không STOMP/SockJS/broker; auth mỗi message, ACK/ERROR, scope push | [configuration](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketConfiguration.java), [handler](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketHandler.java) |
| Gửi WS đồng thời | `ConcurrentWebSocketSessionDecorator` tuần tự hóa write của một connection và áp timeout/buffer | Một decorated writer dùng chung cho ACK/ERROR/push; đóng connection khi write lỗi; revalidate trước data-bearing push | [session registry](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeSessionRegistry.java) |
| Connection pool | HikariCP do Spring Boot cấu hình và cấp JDBC connection | `DB_POOL_SIZE`, mặc định 10; không coi kích thước pool là khóa nghiệp vụ | [application.properties](../server/src/main/resources/application.properties) |
| Transaction | Spring `@Transactional` mở/commit/rollback transaction quanh service proxy | Chọn transaction boundary sao cho DB commit hoàn tất trước ACK; không gửi socket bên trong `MonitoringEventService` | [event service](../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringEventService.java), [handler](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketHandler.java) |
| Đồng thời DB | PostgreSQL thực thi row lock, unique/FK constraint, transaction isolation và `clock_timestamp()` | SQL `FOR UPDATE OF a`, state machine, `writerEpoch`, `savedRevision`, idempotency key và thứ tự quyết định | [ExamService](../server/src/main/java/vn/edu/toeic/server/exam/ExamService.java), [transaction notes](GIAO_DICH_VA_QUYEN.md) |
| Scheduler | Spring gọi method `@Scheduled` bằng `ThreadPoolTaskScheduler` | Pool riêng một thread, chu kỳ scan, từng attempt vẫn đi qua cùng service/row lock như HTTP | [scheduler config](../server/src/main/java/vn/edu/toeic/server/exam/ExamSchedulingConfiguration.java), [timeout service](../server/src/main/java/vn/edu/toeic/server/exam/ExamTimeoutService.java) |
| Migration | Flyway phát hiện và áp migration có version | Nhóm viết migration SQL, constraint và quy tắc không sửa migration đã áp dụng | [migration directory](../server/src/main/resources/db/migration), [server POM](../server/pom.xml) |
| JSON | Gson parse/serialize payload | Protocol envelope, field bắt buộc, identifier, canonical answers và giới hạn 65.536 byte | [handler](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketHandler.java), [protocol](PROTOCOL.md) |

Điểm quan trọng: framework cung cấp cơ chế chạy thread, connection và transaction, nhưng không tự tạo ra tính đúng của bài thi. Tính đúng đến từ state machine, khóa theo attempt, kiểm quyền và quy tắc retry do nhóm định nghĩa.

## 3. Thiết kế giao tiếp mạng

### 3.1. REST

REST dùng cho đăng nhập, exam/session, takeover, autosave, submit, status/result/history, manifest/audio và các truy vấn giám thị. Mọi route `/api/**` ngoài login đi qua `BearerAuthenticationFilter`; token trong query bị từ chối, chỉ chấp nhận đúng một header `Authorization`. Filter gắn `AuthenticatedUser` vào request, còn service vẫn kiểm role và attempt scope theo từng nghiệp vụ.

HTTP status mang nghĩa ổn định: `400` input sai, `401` session không hợp lệ, `403` sai role/scope, `404` resource/route không tồn tại, `409` xung đột state/deadline/epoch/revision và `503` lỗi tạm thời. Client không được suy quyền từ việc biết `attemptId`.

Source: [BearerAuthenticationFilter](../server/src/main/java/vn/edu/toeic/server/auth/BearerAuthenticationFilter.java), [AuthorizationService](../server/src/main/java/vn/edu/toeic/server/auth/AuthorizationService.java), [protocol](PROTOCOL.md).

### 3.2. Raw WebSocket

WebSocket dùng endpoint `/ws/v1/realtime`, không đặt token trong URL. Handshake yêu cầu đúng một Bearer header, chỉ giữ token hash trong attributes. Mỗi message lại tra session từ store để phát hiện expire, revoke, disable hoặc đổi role; handshake thành công không cấp quyền vĩnh viễn cho socket.

Mỗi message có `protocolVersion`, `type`, `messageId`, `requestId`, `attemptId`, `traceId` và payload tùy loại. Server giới hạn text/binary message 65.536 byte; outbound của mỗi connection có send timeout 5 giây và buffer 65.536 byte theo mặc định. JSON hỏng nhận `INVALID_INPUT` trên connection đó; test T3-A4 chứng minh candidate khác vẫn heartbeat/ACK bình thường.

Source: [handshake interceptor](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeHandshakeInterceptor.java), [session authentication](../server/src/main/java/vn/edu/toeic/server/auth/SessionAuthenticationService.java), [handler](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketHandler.java), [T3-A4 evidence](../evidence/t3-a4/2026-10-18-verification.md).

### 3.3. Commit rồi mới ACK

Với dữ liệu bền vững như `PROCESS_OBSERVED`, handler gọi service qua transactional proxy. Service khóa/kiểm scope, insert hoặc xác minh duplicate trong PostgreSQL rồi return sau commit; chỉ lúc đó handler gửi ACK và push warning. Nếu deferred COMMIT thất bại, caller nhận exception trước ACK, server trả lỗi retryable và không push warning thành công.

ACK phải đọc theo loại dữ liệu: ACK event/gap xác nhận transaction DB; ACK full/delta xác nhận current state đã được nhận vào RAM của server instance hiện tại, không chứng minh durability sau restart.

Evidence: [T3-A3 verification](../evidence/t3-a3/2026-10-18-verification.md), [baseline method](MONITORING_BASELINE_METHOD.md).

## 4. Xác thực và phân quyền

Thứ tự quyết định tổng quát:

```text
authenticate current session
→ validate message/request shape
→ require role
→ lock/read authoritative attempt
→ require current owner/assignment and state
→ inspect idempotency record
→ mutate or return the authorized cached result
```

Idempotency không đứng trước quyền. Vì vậy request biết `requestId` của người khác vẫn không thể lấy cached response. Candidate chỉ truy cập attempt mình sở hữu; proctor chỉ đọc/push attempt được phân công; proctor không được gửi monitoring event thay candidate. Data-bearing push revalidate token, role và assignment ngay trước send; token revoke hoặc assignment thay đổi có hiệu lực với socket đang mở.

AT01 đã kiểm candidate 2 không thể autosave, submit, takeover, đọc exam/status của candidate 1 cả trước và sau submit. Four-client isolation đã kiểm hai candidate, assigned proctor và unassigned proctor không rò scope/push.

Source/evidence: [AuthorizationService](../server/src/main/java/vn/edu/toeic/server/auth/AuthorizationService.java), [session registry](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeSessionRegistry.java), [T3-A4 verification](../evidence/t3-a4/2026-10-18-verification.md).

## 5. Giao dịch và đồng thời

### 5.1. Đơn vị tuần tự hóa

Mỗi thao tác thay đổi attempt đọc row bằng `SELECT ... FOR UPDATE OF a`. PostgreSQL cho đúng một transaction giữ exclusive row lock; save, submit, takeover và timeout trên cùng attempt vì thế được tuần tự hóa theo thứ tự lấy khóa. Hai attempt khác nhau không dùng một khóa ứng dụng toàn cục.

Sau khi lấy khóa, service đọc `clock_timestamp()` để tạo `decisionAt`. Không dùng transaction-start `now()`: request đến trước deadline nhưng chờ khóa đến sau deadline phải bị từ chối như một write muộn.

### 5.2. Revision, epoch và idempotency

- `writerEpoch` đổi khi takeover; writer cũ bị `409 STALE`, kể cả request của nó đã đến server nhưng còn chờ row lock.
- `savedRevision` chỉ tiến đơn điệu. Revision thấp hơn bị từ chối; retry cùng nội dung có thể nhận lại kết quả đã lưu; cùng idempotency key nhưng payload khác là conflict.
- Autosave và submit lưu request record trong cùng transaction với mutation. PostgreSQL unique constraint ngăn hai transaction cùng tạo hai kết quả cho một key.
- Monitoring event dùng identity `(attempt_id, event_id)`: retry cùng payload ACK lại nhưng không tạo row/warning thứ hai; payload khác với cùng identity là conflict.

### 5.3. Save, submit và timeout cùng tranh chấp

Kịch bản T3-A4 giữ row lock qua deadline thật, khởi động save và submit bất đồng bộ, đồng thời để scheduler timeout chạy. Test quan sát đủ ba waiter trong `pg_stat_activity` trước khi nhả khóa. Sau đó:

- save và submit lấy khóa sau deadline nên trả `409 EXPIRED` hoặc thấy state cuối và trả `409 INVALID_STATE`;
- timeout chốt duy nhất `TIMED_OUT`;
- answers và `savedRevision` đã ACK trước cuộc đua được giữ nguyên;
- score được tính một lần, không có submit-request row giả.

Kết luận không phụ thuộc `sleep` hoặc thứ tự thread giả định; bằng chứng đồng bộ là row lock thật và waiter PostgreSQL. Source/test: [ExamRuntimePostgresTest](../server/src/test/java/vn/edu/toeic/server/exam/ExamRuntimePostgresTest.java), [T3-A4 evidence](../evidence/t3-a4/2026-10-18-verification.md).

## 6. Lỗi, retry và phục hồi

| Tình huống | Hành vi thiết kế | Bằng chứng hiện có |
|---|---|---|
| Mất ACK sau commit | Client retry cùng identity; server trả kết quả tương đương, không ghi/push lần hai | T3-A3/T3-A4 PostgreSQL WS smoke |
| JSON hỏng | ERROR cho client gây lỗi; connection/client khác tiếp tục | T3-A4 malformed-client isolation |
| Client rớt | Registry loại connection; state/history của client khác không bị xóa | T3-A4 four-client isolation |
| Token revoke khi WS còn mở | Authenticate hash mỗi message; push cũng revalidate; socket unauthorized bị đóng policy violation | AuthenticatedNetworkTest và smoke A3 |
| Server restart | DB history/session còn; current state RAM mất; instance/epoch mới buộc OPEN + full resync | T3-A3 restart evidence |
| DB commit lỗi | Không success ACK/warning; trả retryable error; retry sau recovery có thể commit | T3-A3 deferred-COMMIT smoke |
| Timeout job lỗi một attempt | Worker log lỗi và tiếp tục attempt khác ở scan sau; mutation vẫn qua transaction/row lock | ExamTimeoutService và PostgreSQL tests |

## 7. Ma trận khẳng định và bằng chứng

| ID | Khẳng định dùng trong báo cáo | Source kiểm tra | Bằng chứng chạy |
|---|---|---|---|
| NC01 | REST và raw WS đều yêu cầu session hiện hành; WS revalidate mỗi message | auth filter, handshake, handler | AuthenticatedNetworkTest: 36 PASS trong lượt T3-A4 |
| NC02 | Một client JSON hỏng không làm hỏng client khác | handler catch theo message/connection | T3-A4 REAL WS smoke PASS |
| NC03 | Push chỉ tới proctor còn token/role/assignment hợp lệ | RealtimeSessionRegistry | T3-A4 four-client isolation PASS |
| NC04 | Event DB commit trước success ACK/push | MonitoringEventService + handler | T3-A3 deferred COMMIT failure PASS |
| NC05 | Save/submit/timeout cùng attempt được serialize bằng row lock | ExamService | T3-A4 three-waiter PostgreSQL test PASS |
| NC06 | Cross-attempt access bị chặn trước/sau submit | AuthorizationService + ExamService | AT01 REAL PostgreSQL/HTTP PASS |
| NC07 | Retry không tạo duplicate side effect | request/event tables và unique identity | submit retry + concurrent WS retry PASS |
| NC08 | Restart giữ DB history nhưng current state cần epoch mới/full resync | MonitoringStateService contract | T3-A3 REAL restart smoke PASS |

Lệnh tái lập và transcript của NC01–NC08 nằm trong [T3-A4 verification](../evidence/t3-a4/2026-10-18-verification.md); contract hook/restart nằm trong [T3-A3 verification](../evidence/t3-a3/2026-10-18-verification.md).

## 8. Kết quả đã có và giới hạn của bản nháp

- T3-A4 targeted transaction/network: 74 PASS, không fail/error/skip.
- AT01/AT07/AT09/AT10 trên PostgreSQL/HTTP thật: PASS.
- Multi-client PostgreSQL/HTTP/WS thật trên loopback: PASS, gồm malformed client và four-client isolation.
- Full reactor sau T3-A4: 912 PASS, 58 opt-in skip.
- LAN hai máy, GUI end-to-end, tải nhiều client và human C review: **NOT RUN** cho T3-A5.
- Bản nháp chưa có bảng throughput/latency/resource; số đo đó thuộc T4-A2/T4-A3 và không được suy ra từ test chức năng.
- Current monitoring state chỉ hỗ trợ một server JVM; chưa có distributed state/lock hoặc bảo đảm active-active.

## 9. Việc reviewer C cần chốt

1. Xác nhận mô tả ranh giới ownership A/C: A cung cấp auth/persistence/epoch hooks, C sở hữu collector/reducer monitoring.
2. Xác nhận câu chữ ACK DB so với ACK state RAM không gây hiểu nhầm durability.
3. Xác nhận phạm vi claim multi-client chỉ là loopback functional regression, không phải load/LAN result.
4. Sau T4-A2, thay phần kết quả placeholder bằng bảng số đo thật và cấu hình máy; không sửa ngược transcript T3-A4.
