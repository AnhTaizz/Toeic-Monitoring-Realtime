# Protocol đang dùng

**Trạng thái: v0 có login/Bearer; C3 nối collector → event/queue/retry → A3 DB/ACK/warning/timeline. A4 nối scoped heartbeat, presence/timeout/history, roster REST và MONITOR_PRESENCE. B3 đã consume trên dashboard giám thị, HTTP recovery và parser push. Overflow C3 giữ contract riêng; full/delta chưa có.** File này mô tả những gì code thật đang gửi và nhận. Quy tắc nghiệp vụ đằng sau nằm ở `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`; không chép lại ở đây.

Owner: C (monitoring, khung message chung), A (auth, ca thi, lưu/nộp). Người dùng: B.

Đổi một message đã có người dùng thì: owner sửa file này, reviewer duyệt, người dùng cập nhật fixture trong cùng nhánh tích hợp.

## Phân kênh

| Kênh | Dùng cho |
|---|---|
| HTTP/REST | Login, import đề và ca thi, tải đề và audio, lưu, nộp, đọc trạng thái, đọc timeline |
| WebSocket + JSON | Heartbeat, monitoring (state, event), lệnh start, trạng thái, cảnh báo tới giám thị |

## Khung message WebSocket

Theo hợp đồng mục 1, mỗi message dùng envelope sau. Trường không áp dụng cho một loại message có thể là `null`, nhưng `protocolVersion`, `type`, `messageId` và `traceId` luôn bắt buộc.

| Trường | Ý nghĩa |
|---|---|
| `protocolVersion` | Phiên bản protocol |
| `type` | Loại message |
| `messageId` | Nhận diện message và giữ nguyên qua retry |
| `requestId` | Nối ACK với request; bằng `messageId` nếu message chỉ có một request |
| `attemptId` | Lượt thi mà message thuộc về |
| `traceId` | Correlation ID nối request, ACK và log |
| `payload` | Dữ liệu nghiệp vụ theo `type` |

### Mẫu message v0 (MOCK)

Các ID dưới đây chỉ là fixture minh họa, **chưa phải bằng chứng đã chạy qua WebSocket thật**.

Heartbeat từ thí sinh:

```json
{
  "protocolVersion": "v0",
  "type": "HEARTBEAT",
  "messageId": "mock-message-hb-001",
  "requestId": "mock-message-hb-001",
  "attemptId": "mock-attempt-A",
  "traceId": "mock-trace-hb-001",
  "payload": {
    "collectorSessionId": "mock-collector-001",
    "sentAt": "2026-10-03T10:00:00Z"
  }
}
```

Event process quan sát thấy:

```json
{
  "protocolVersion": "v0",
  "type": "PROCESS_OBSERVED",
  "messageId": "mock-message-event-001",
  "requestId": "mock-message-event-001",
  "attemptId": "mock-attempt-A",
  "traceId": "mock-trace-event-001",
  "payload": {
    "eventId": "mock-event-001",
    "collectorSessionId": "mock-collector-001",
    "policyVersion": "process-policy-v1",
    "pid": 4242,
    "processName": "msedge.exe",
    "startInstant": "2026-10-03T09:59:58Z",
    "metadataQuality": "COMPLETE",
    "observedAt": "2026-10-03T10:00:00Z"
  }
}
```

ACK thành công chỉ được phát sau khi thao tác tương ứng đã được chấp nhận/commit:

```json
{
  "protocolVersion": "v0",
  "type": "ACK",
  "messageId": "mock-message-ack-001",
  "requestId": "mock-message-event-001",
  "attemptId": "mock-attempt-A",
  "traceId": "mock-trace-event-001",
  "payload": {
    "status": "ACCEPTED",
    "acknowledgedType": "PROCESS_OBSERVED"
  }
}
```

## Danh mục endpoint HTTP

| Method + đường dẫn | Ai gọi | Request | Response | Lỗi | Task | Trạng thái |
|---|---|---|---|---|---|---|
| `POST /api/v1/auth/login` | Thí sinh, giám thị | `requestId`, `username`, `password` | `protocolVersion`, `requestId`, `traceId`, `token`, `tokenType`, `expiresAt`, `user`, `attemptScope` | 400 `INVALID_INPUT`; 401 `UNAUTHORIZED`; 500 `RETRYABLE_SERVER_ERROR` | T1-A1 | Đã cài |
| `GET /api/v1/auth/me` | Người đã login | Header Bearer | `protocolVersion`, `traceId`, `user: {userId, username, role}`, `attemptScope`: các attempt ACTIVE có quyền | 401 `UNAUTHORIZED`; 503 khi lookup session lỗi; 500 khi lookup scope lỗi, `RETRYABLE_SERVER_ERROR` | T1-A2/A3 | Đã cài |
| `GET /api/v1/monitoring/attempts/{attemptId}/events` | Giám thị được phân công | Header Bearer | `protocolVersion`, `traceId`, `attemptId`, `events` | 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 500/503 `RETRYABLE_SERVER_ERROR` | T1-A3 | Đã cài |
| `GET /api/v1/monitoring/attempts` | Giám thị | Header Bearer | `protocolVersion`, `traceId`, `serverTime`, `attempts`: identity + presence của ACTIVE được phân công | 401; 403 sai role; 500/503 lỗi DB | T1-A4 | Đã cài |
| `GET /api/v1/monitoring/attempts/{attemptId}/interruptions` | Giám thị được phân công | Header Bearer | `protocolVersion`, `traceId`, `attemptId`, `interruptions` | 401; 403 sai role/foreign/CLOSED/unknown; 500/503 lỗi DB | T1-A4 | Đã cài |
| `POST /api/v1/exams/import` | Giám thị | `examId`, `title`, `description`, `questions: [{questionId, section, part, groupId, passageText, audioFile, prompt, correctOption, orderIndex, options: [{optionId, optionText, orderIndex}]}]` | `examId`, `title`, `totalQuestions`, `status` | 400 `INVALID_INPUT`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 500/503 | T2-A1 | Đã cài |
| `POST /api/v1/sessions` | Giám thị | `sessionId`, `examId`, `title`, `durationSeconds`, `proctorUsernames`, `candidateUsernames` | `sessionId`, `examId`, `title`, `durationSeconds`, `state`, `createdAttempts: [{attemptId, candidateUsername, state}]` | 400 `INVALID_INPUT`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 500/503 | T2-A1 | Đã cài |
| `GET /api/v1/attempts/{attemptId}/exam` | Thí sinh / Giám thị phân công | Header Bearer | `examId`, `title`, `description`, `totalQuestions`, `questions` (**TUYỆT ĐỐI KHÔNG CÓ `correctOption`**) | 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 500/503 | T2-A1 | Đã cài |
| `POST /api/v1/attempts/{attemptId}/answers` | Thí sinh sở hữu | `requestId`, `attemptId`, `writerEpoch`, `answerRevision`, `answers: {qId: optId}` | `requestId`, `attemptId`, `status`, `savedRevision`, `writerEpoch`, `decisionAt` | 400 `INVALID_INPUT`; 401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 409 `STALE`/`CONFLICT`/`INVALID_STATE`/`EXPIRED` | T2-A2 | Đã cài |
| _submit_ | Thí sinh | | | | T2-A3 | Chưa có |
| _trạng thái attempt (answers, revision, state, deadline)_ | Thí sinh | | | | T2-A4 | Chưa có |
| _manifest và tải audio_ | Thí sinh | | | | T3-A1 | Chưa có |

Request/response login mẫu (`MOCK` về giá trị ID/token, đúng schema code đang chạy):

```json
{
  "requestId": "login-request-001",
  "username": "candidate1",
  "password": "<MOCK_PASSWORD>"
}
```

```json
{
  "protocolVersion": "v0",
  "requestId": "login-request-001",
  "traceId": "server-generated-trace-id",
  "token": "<MOCK_BEARER_TOKEN>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-03T18:00:00Z",
  "user": {
    "id": 1,
    "username": "candidate1",
    "displayName": "Thí sinh 1",
    "role": "CANDIDATE"
  },
  "attemptScope": []
}
```

Token chỉ trả trong JSON body của login. Request HTTP `/api/**` sau login và WS handshake đều dùng `Authorization: Bearer <token>` theo QD-03. Login POST vẫn public. Client không tự khai role; session/user DB quyết định identity và role. Token raw không lưu DB hay session attributes/log; lookup bằng SHA-256. Token expired/revoked hoặc user disabled đều UNAUTHORIZED.

## Danh mục message WebSocket

| `type` | Hướng | Payload | ACK | Task | Trạng thái |
|---|---|---|---|---|---|
| `HEARTBEAT` | client → server | `sentAt` bắt buộc; scoped bắt buộc collectorSessionId | Unscoped ACK transport; scoped ACK sau presence commit | T1-A2/A4 | Đã cài |
| `ACK` (heartbeat) | server → client | `status: ACCEPTED`, `acknowledgedType: HEARTBEAT` | Không | T1-A2 | Đã cài |
| `ERROR` | server → client | `code`, `message`, `retryable` | Không | T1-A2 | Đã cài |
| `PROCESS_OBSERVED` | thí sinh → server | Event v0; attempt bắt buộc | ACK sau commit | T1-A3/C3 | Collector→queue→B2→DB/ACK đã cài |
| `MONITORING_GAP` | thí sinh → server | C3 QUEUE_OVERFLOW; attempt bắt buộc | ACK sau commit | T1-C3 + hook A | Đã cài tối thiểu; chưa reducer/state |
| `ACK` (event) | server → thí sinh | `status: ACCEPTED`, `acknowledgedType: PROCESS_OBSERVED` | Không | T1-A3 | Đã cài |
| `MONITOR_WARNING` | server → giám thị được phân công | Timeline item v0 | Không | T1-A3/B3 | Server và dashboard đã cài |
| `MONITOR_PRESENCE` | server → giám thị được phân công | PresenceSnapshot: identity/status/reason/revision/timestamps | Không | T1-A4/B3 | Server và dashboard đã cài |
| _full snapshot_ | thí sinh → server | | Sau khi state được chấp nhận | T2-C1 | Chưa có |
| _delta_ | thí sinh → server | | | T3-C1, T3-C2 | Chưa có |
| _yêu cầu resync / epoch mới_ | server → thí sinh | | | T2-C1, T3-C2 | Chưa có |
| _READY / start / interrupted (Listening)_ | hai chiều | | | T3-A2, T3-B2 | Chưa có |

## Mã lỗi

Mã lỗi v0 dùng chữ hoa, ổn định trong trường `error.code`. HTTP status và `retryable` vẫn được gửi riêng; client không phân tích câu thông báo tiếng Việt để quyết định logic.

| Mã | Khi nào | Client nên làm gì |
|---|---|---|
| `UNAUTHORIZED` | Thiếu/sai credential | Hiện lỗi đăng nhập hoặc yêu cầu đăng nhập lại |
| `FORBIDDEN` | Đã xác thực nhưng sai role/scope | Không retry tự động; khóa thao tác đó |
| `INVALID_INPUT` | JSON/trường/giá trị không hợp lệ | Sửa request trước khi gửi lại |
| `INVALID_STATE` | Thao tác không hợp phase/state | Nạp lại state server |
| `STALE` | Revision/writer/epoch cũ | Đồng bộ state; không đổi payload rồi tái dùng ID |
| `CONFLICT` | Cùng ID/revision nhưng nội dung khác | Dừng retry tự động, báo xung đột |
| `EXPIRED` | Hết deadline/phiên | Khóa thao tác và nạp trạng thái chốt |
| `RETRYABLE_SERVER_ERROR` | Lỗi tạm thời phía server/DB | Retry có giới hạn/backoff với cùng ID/payload |

Mẫu lỗi login thật:

```json
{
  "protocolVersion": "v0",
  "type": "ERROR",
  "requestId": "login-request-001",
  "traceId": "server-generated-trace-id",
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Tên đăng nhập hoặc mật khẩu không đúng",
    "retryable": false
  }
}
```

Server không phân biệt công khai sai username hay sai password. Password/token không được ghi vào log; `toString()` của DTO auth luôn che hai trường này.

## Tham số cấu hình khởi đầu

Giá trị thử nghiệm theo hợp đồng, phải ghi lại giá trị thật khi đo.

| Tham số | Giá trị khởi đầu |
|---|---|
| Chu kỳ poll process | 1.000 ms |
| Heartbeat | 2 giây |
| Server timeout heartbeat | 6 giây |
| Queue event phía client | 500 |
| Full snapshot đối soát (chế độ delta) | 30 giây |

## Lịch sử thay đổi

| Ngày | Thay đổi | Ai | Ai đã cập nhật theo |
|---|---|---|---|
| 03/10/2026 | Chốt login REST, envelope/message MOCK v0 và mã lỗi v0 cho T1-A1/T1-C1 | A + C | Client T1-B1 |
| 04/10/2026 | QD-03 Bearer header; endpoint WS thật, heartbeat transport/ACK/ERROR, auth/role/scope guard | A (T1-A2) | B2 cần tích hợp ở phiên riêng; chưa sửa nhánh B2 |
| 09/10/2026 | B consume T2-A1…A4 trên PR #17; ghi 4 điểm server khác tài liệu; không đổi endpoint/DTO | B | A cần xử lý các điểm 1–3 và sửa mẫu JSON status |

## Contract T1-A2 và handoff cho B2

### Kết nối và credential

- URL: `ws://<server>:<port>/ws/v1/realtime`; mặc định port 8080. HTTPS deployment dùng `wss://`. Không thêm `/api` vào path WS.
- Handshake header: `Authorization: Bearer <token>` (scheme Bearer không phân biệt hoa/thường). Chỉ nhận một header, một khoảng trắng giữa scheme/token; token opaque do login cấp. Thiếu/sai/malformed/expired/revoked/disabled → HTTP 401, `WWW-Authenticate: Bearer`, không upgrade.
- Endpoint WS không nhận query; kể cả header hợp lệ kèm query cũng bị 401. Không dùng URL query, subprotocol hay message AUTH để gửi token.
- Mỗi reconnect tạo WS mới và gửi header auth lại. Server giữ user và **hash** session trong attributes; mỗi message tra DB lại để kiểm expiry/revoke/enabled/current role. Phiên bị vô hiệu → ERROR UNAUTHORIZED rồi close 1008. Lookup session tạm thời lỗi → handshake 503 hoặc WS ERROR RETRYABLE_SERVER_ERROR; không trả stack trace.
- REST `/api/**` ngoại trừ POST login dùng cùng Bearer semantics và identity/principal. Token trong query `token`/`access_token` bị từ chối; sai role/scope → 403 FORBIDDEN. Không tự bật browser CORS wildcard.

### Trường nhận ở T1-A2, cập nhật A3/C3/A4

| Trường | Quy tắc |
|---|---|
| protocolVersion | Bắt buộc string `v0` |
| type | Bắt buộc string; HEARTBEAT, PROCESS_OBSERVED, MONITORING_GAP được hỗ trợ |
| messageId | Bắt buộc, 1–128 ký tự `[A-Za-z0-9_.:-]` |
| traceId | Bắt buộc, cùng giới hạn ID; ACK giữ correlation |
| requestId | Có thể thiếu/null, khi đó dùng messageId; nếu có phải bằng messageId |
| attemptId | HEARTBEAT có thể thiếu/null. Scoped bắt buộc candidate sở hữu ACTIVE; event/gap bắt buộc attempt |
| payload | HEARTBEAT chỉ sentAt ISO Instant và collectorSessionId identifier; collector bắt buộc nếu scoped, optional/null nếu unscoped |

Giới hạn text message/buffer: 65.536 byte mặc định, configurable; send timeout 5.000ms. Message vượt giới hạn container có thể bị đóng 1009; binary không được hỗ trợ. JSON sai/type lạ/thiếu trường trong giới hạn → ERROR INVALID_INPUT chỉ tới session đó; kết nối vẫn nhận heartbeat tiếp theo.

**Heartbeat thiếu attemptId chỉ là transport ping của phiên authenticated**, dùng khi chưa start collector hoặc sau Stop. Nó không ghi presence/ONLINE/UNKNOWN, không bật collector và không cấp quyền truy cập attempt. Scoped heartbeat A4: auth → candidate/ACTIVE scope → validation → presence COMMIT → ACK/push. Từ A3, production scope đọc assignment PostgreSQL: candidate sở hữu hoặc proctor được phân công vào attempt ACTIVE; proctor scope chỉ phục vụ đọc/push, không cho gửi scoped candidate heartbeat. Không có assignment thì scope rỗng; không tạo fixture production.

### HEARTBEAT/ACK đã cài

Các ID/thời gian dưới đây là giá trị minh họa; shape được kiểm qua network thật:

```json
{"protocolVersion":"v0","type":"HEARTBEAT","messageId":"sample-hb-001","requestId":"sample-hb-001","attemptId":null,"traceId":"sample-trace-001","payload":{"sentAt":"2026-10-04T00:00:00Z"}}
```

```json
{"protocolVersion":"v0","type":"ACK","messageId":"server-generated-id","requestId":"sample-hb-001","attemptId":null,"traceId":"sample-trace-001","payload":{"status":"ACCEPTED","acknowledgedType":"HEARTBEAT"}}
```

ACK unscoped heartbeat xác nhận transport đã nhận/chấp nhận ping; scoped A4 xác nhận presence commit, không thay ACK event/gap. ACK PROCESS_OBSERVED/MONITORING_GAP chỉ gửi sau commit tương ứng, theo contract bên dưới. Full/delta chưa được hỗ trợ.

### ERROR envelope WS đã cài

```json
{"protocolVersion":"v0","type":"ERROR","messageId":"server-generated-id","requestId":"sample-hb-001","attemptId":"sample-foreign-attempt","traceId":"sample-trace-001","payload":{"code":"FORBIDDEN","message":"Không có quyền thực hiện thao tác này","retryable":false}}
```

ERROR dùng payload trực tiếp `{code,message,retryable}`; khác REST lỗi có top-level `error`. Với JSON lỗi chưa đọc được ID, requestId/attemptId có thể null và traceId do server tạo. Không phản chiếu raw JSON/parser cause. Chưa biết type → INVALID_INPUT; foreign/unknown attempt → FORBIDDEN, không tiết lộ attempt có tồn tại hay không.

### B cần đổi ở phiên B2

1. Đồng bộ main A2 vào nhánh B2 riêng; thay default blocked ConnectionOpener bằng `HttpClient.newWebSocketBuilder().header("Authorization", "Bearer " + token).buildAsync(url, listener)`.
2. Hook phải sở hữu/dọn HttpClient/executor; future mở WS hoàn thành sau handshake hợp lệ. 401 ánh xạ lỗi auth không retry vô hạn; reconnect luôn gửi credential lại. 503/network error đi qua backoff giới hạn.
3. Cho phép heartbeat/ACK attemptId null khi scope login rỗng; không invent scope/attempt. Không coi unscoped heartbeat là monitoring được cấp quyền.
4. Theo dõi ACK HEARTBEAT và xử lý ERROR payload/correlation; parser B hiện chỉ nhận event ACK nên cần cập nhật. Khi UNAUTHORIZED/1008: khóa UI/yêu cầu login lại. Không coi socket write là ACK/commit.
5. Giữ PROCESS_OBSERVED/state integration chờ A3/C3. Phiên A2 không sửa/merge nhánh B2, không kiểm GUI thay B.

## B2 consume contract A2 — 04/10/2026

Client B2 đã cài opener Bearer cho `/ws/v1/realtime`, heartbeat/ACK unscoped và ERROR payload trực tiếp theo contract ở trên. Mỗi reconnect mở handshake mới; 401/UNAUTHORIZED/1008 dừng retry bằng token cũ và yêu cầu login lại. JavaFX dùng cùng transport cho candidate/proctor, không bật collector. MonitoringTransport giữ send = socket write, ACK/ERROR = onMessage; PROCESS_OBSERVED/state/full/delta vẫn CHƯA TÍCH HỢP SERVER THẬT, chờ A3/C3. Không đổi QD-03 hoặc semantics do A/C sở hữu. Evidence: `evidence/t1-b2/2026-10-04-real-integration.md`; GUI manual NOT RUN.

## Collector local C2 — 04/10/2026

ProcessCollector client đã có polling local theo process-policy-v1 và immutable ProcessSnapshot. Gate yêu cầu candidate + monitoring session active; production chưa có trigger nên không auto-start từ login. C2 không gọi MonitoringTransport, không gửi PROCESS_OBSERVED/heartbeat/full/delta, không eventId/queue/ACK. A3 đã cài server event; collector→transport vẫn chờ C3. QD-03/QD-08 giữ nguyên. Handoff API/evidence: `evidence/t1-c2/2026-10-04-verification.md`.

## Contract T1-A3 — server đã cài, handoff C3/B3 (04/10/2026)

### Assignment và phạm vi quyền

Flyway V2 tạo `monitoring_attempts`, `monitoring_proctor_assignments`, `monitoring_events`. Migration chỉ tạo schema; không seed lượt thi. Candidate chỉ có quyền trên attempt ACTIVE của chính mình; proctor chỉ có quyền trên attempt ACTIVE được phân công. Unknown/CLOSED/foreign đều trả FORBIDDEN chung. Login và `/auth/me` trả `attemptScope` theo DB, sắp xếp attemptId; thay đổi assignment được đọc lại khi gọi `/auth/me`, xử lý message, timeline và trước mỗi push. Không được dùng scope lưu ở client để thay kiểm quyền server.

### PROCESS_OBSERVED → ACK

Request và ACK dùng đúng mẫu v0 phía trên. `attemptId` bắt buộc. `messageId`, `traceId`, `eventId`, `collectorSessionId` là ID 1–128 ký tự `[A-Za-z0-9_.:-]`. `requestId` thiếu/null được lấy bằng messageId; có giá trị phải bằng messageId. Payload chỉ nhận tám trường trong mẫu, từ chối trường lạ.

| Trường payload | Kiểm tra |
|---|---|
| eventId, collectorSessionId | Bắt buộc ID hợp lệ |
| policyVersion | String không trống, tối đa128 ký tự, không ký tự điều khiển |
| pid | Số nguyên dương trong phạm vi Java long; không nhận string số |
| processName | Filename 1–255 ký tự `[A-Za-z0-9_.-]`, không `.`/`..`; reject path, khoảng trắng/arguments, ký tự điều khiển |
| startInstant | ISO-8601 Instant hoặc thiếu/null; thiếu phải dùng UNREADABLE |
| metadataQuality | COMPLETE hoặc UNREADABLE |
| observedAt | ISO-8601 Instant bắt buộc; năm0001–9999 |

Các Instant được chuẩn hóa UTC và cắt phần nhỏ hơn microsecond trước khi lưu/so sánh, theo độ chính xác PostgreSQL. `observedAt` do client khai, không phải bằng chứng đồng bộ đồng hồ. `receivedAt` lấy bằng PostgreSQL `clock_timestamp()` khi insert, không sửa khi retry.

Luồng: xác thực lại → role CANDIDATE → scope → validation → service transaction → COMMIT → ACK → push nếu row mới. Unique `(attempt_id,event_id)`. So sánh record đã chuẩn hóa, không so chuỗi JSON hay envelope. Cùng payload dù thứ tự key khác: ACK lại, một row, một warning. Khác bất kỳ trường payload đã chuẩn hóa: ERROR CONFLICT, không overwrite/ACK/push. ID event thuộc attempt; không unique toàn hệ thống.

Lỗi WS dùng `{code,message,retryable}` trong `payload`: INVALID_INPUT/FORBIDDEN/CONFLICT/UNAUTHORIZED đều `retryable:false`; lỗi DB/commit là RETRYABLE_SERVER_ERROR `retryable:true`, không ACK success hoặc warning. UNAUTHORIZED đóng socket1008. Error không phản chiếu payload, path hoặc stack trace.

**C3:** giữ eventId và payload qua retry; giữ mapping requestId→queued event. `MonitoringTransport.send` hoàn thành chỉ nghĩa là ghi socket. Chỉ xóa queued event khi nhận ACK với requestId đúng và `acknowledgedType:PROCESS_OBSERVED`. CONFLICT dừng retry tự động; lỗi retryable/mất ACK retry có giới hạn cùng event. A3 chưa viết queue/retry/collector integration.

### MONITOR_WARNING và timeline REST

Ví dụ minh họa schema (ID/thời gian TEST, không credential):

```json
{"protocolVersion":"v0","type":"MONITOR_WARNING","messageId":"server-generated-id","requestId":null,"attemptId":"TEST-attempt-A","traceId":"TEST-trace","payload":{"eventId":"TEST-event-1","attemptId":"TEST-attempt-A","processName":"notepad.exe","metadataQuality":"COMPLETE","policyVersion":"process-policy-v1","observedAt":"2026-10-04T00:00:01Z","receivedAt":"2026-10-04T00:00:02Z"}}
```

Push chỉ tới session proctor còn hợp lệ và có assignment tại thời điểm kiểm quyền; candidate/proctor khác không nhận. Mọi ACK/ERROR/warning đi qua cùng ConcurrentWebSocketSessionDecorator. Giao nhận push best effort: offline/socket lỗi không làm mất event đã commit, không biến ACK thành lỗi. Không đảm bảo thứ tự warning giữa các connection gửi đồng thời; REST là nguồn lịch sử ổn định.

`GET /api/v1/monitoring/attempts/{attemptId}/events` với Bearer header, chỉ PROCTOR có scope ACTIVE. Chưa có phân trang. Response200:

```json
{"protocolVersion":"v0","traceId":"server-generated-id","attemptId":"TEST-attempt-A","events":[{"eventId":"TEST-event-1","attemptId":"TEST-attempt-A","processName":"notepad.exe","metadataQuality":"COMPLETE","policyVersion":"process-policy-v1","observedAt":"2026-10-04T00:00:01Z","receivedAt":"2026-10-04T00:00:02Z"}]}
```

`events` có thể rỗng. Item timeline và payload warning cùng schema. Order `received_at ASC,id ASC`; không theo observedAt. Không có password/token/OS username/full path/command line. B3 hiển thị “Quan sát thấy …”, không kết luận gian lận. Dedupe bằng `(attemptId,eventId)`.

**B3 reconnect:** đọc timeline → render → mở/live WS, rồi đọc timeline đối chiếu lần nữa và gộp theo dedupe key để bù khoảng trống giữa HTTP và handshake. Có thể mở WS/buffer trước rồi đọc timeline. Chưa có subscription message riêng; server dùng assignment để lọc các socket proctor. A3 chưa viết dashboard.

Kiểm chứng: `scripts/smoke-a3.ps1` chạy production Spring + PostgreSQL thật + Java HttpClient/WebSocket trong TEST schema riêng; evidence ở `evidence/t1-a3/2026-10-04-verification.md`. Review C là NOT RUN trong phiên Agent.

## Contract T1-C3 — event delivery và overflow (04/10/2026)

Các ghi chú “chờ C3” trong phần bàn giao lịch sử A2/B2/C2 ở trên mô tả phiên trước. Hiện C3 đã nối production collector vào B2 và A3. Không đổi payload/warning/timeline PROCESS_OBSERVED của A3.

Candidate chủ động chọn attempt từ server và bắt đầu; gọi `/api/v1/auth/me` lại trước start, cập nhật scope B2 khi không có pending delivery cũ. Server kiểm auth/role/assignment ACTIVE cho từng message. Scope rỗng/proctor không scan hoặc tạo worker/queue candidate. Stop/switch/logout đóng delivery, báo số event/gap/drop chưa xác nhận bị bỏ, rồi chờ collector/delivery cũ kết thúc. Không chuyển pending giữa attempt, không giữ trên đĩa.

Identity đúng C2 `(collectorSessionId,pid,startInstant nullable)`. Snapshot đầu phát event mỗi process trong policy; nhiều poll cùng identity không phát thêm. Biến mất rồi xuất hiện/PID với start khác phát event mới. MetadataQuality thay đổi không đổi identity. Start null giữ identity null; null→known hoặc known→null coi mới theo identity nghiêm ngặt, có thể báo thêm khi metadata hồi phục. PID reuse không đọc được start có thể không phân biệt nếu chưa thấy vắng mặt. Process giữa hai poll có thể bị bỏ sót. Scan lỗi giữ baseline hợp lệ trước; snapshot vượt 10.000 identity báo SNAPSHOT_LIMIT và không thay baseline. Production mỗi lần collector start tạo delivery mới; API không cho retag accumulator chưa đóng băng sang collector khác.

MonitoringMessage tạo eventId/messageId=requestId/traceId/observedAt một lần; JsonObject queued không lộ ra, envelope trả deep copy. Clock UTC lấy khi nhận snapshot, cắt dưới microsecond; observationNanos chỉ đo local, không đổi thành Unix time. Payload chỉ tám trường A3, filename không full path; start null luôn UNREADABLE.

Queue RAM default500 gồm QUEUED/WAITING/RETRY_WAIT/FAILED/EXHAUSTED; in-flight4 tổng event + gap. ACK timeout5s, tối đa5 lần gửi gồm lần đầu, backoff1/2/4/8s cap8s. Register pending trước send. Chỉ ACK v0 đúng requestId, attemptId, traceId, `status:ACCEPTED`, acknowledgedType của message mới loại pending. Send completed chỉ socket write. ACK heartbeat/sai/duplicate không xóa event khác. B2 có correlation riêng, C3 forget khi timeout/exhaustion/stop; callback generation cũ không thay phiên mới. Một timer pump25ms, không mỗi event một timer; map/list/baseline/write đều bounded. B2 dành một pending ACK slot cho heartbeat (cấu hình slots>1), hết hạn pending heartbeat sau3chu kỳ.

Reconnect do B2, retry event/gap do C3; giữ collector và budget gửi qua reconnect. Không send khi disconnected. Send failure/mấtACK/RETRYABLE_SERVER_ERROR retry theo backoff. CONFLICT/INVALID_INPUT hoặc lỗi permanent khác giữ FAILED, không chặn event khác; FORBIDDEN/UNAUTHORIZED dừng hoạt động được cấp quyền, giữ pending tới explicitStop. Hết lượt giữ EXHAUSTED, chưa có bằng chứng lưu; chỉ nút thử lại cấp budget mới và giữ nguyên payload/ID. Không hứa khôi phục queue sau kill app.

### MONITORING_GAP tối thiểu đã triển khai

Ví dụ schema TEST, timestamp client-reported:

```json
{"protocolVersion":"v0","type":"MONITORING_GAP","messageId":"TEST-gap-request","requestId":"TEST-gap-request","attemptId":"TEST-attempt-A","traceId":"TEST-gap-trace","payload":{"gapId":"TEST-gap-1","collectorSessionId":"TEST-collector-1","reason":"QUEUE_OVERFLOW","droppedCount":3,"firstDroppedAt":"2026-10-04T00:00:01Z","lastDroppedAt":"2026-10-04T00:00:02Z"}}
```

Payload đúng sáu trường, không thêm attemptId trong payload. gapId/collectorSessionId theo identifier envelope `[A-Za-z0-9_.:-]{1,128}`; reason chỉ QUEUE_OVERFLOW. droppedCount số nguyên JSON chính xác trong1..Long.MAX_VALUE; không chuỗi/fraction/overflow. Thời gian ISO Instant, chuẩn UTC microsecond, năm1..9999, last>=first sau chuẩn hóa như A3. Không chứng minh đồng hồ client đúng hoặc thời điểm server mất kết nối. Khi wall clock lùi, accumulator giữ last không nhỏ hơn first. Counter không wrap: giới hạn Long.MAX_VALUE dừng activity với COUNT_LIMIT.

Queue đầy giữ event cũ/drop event mới, tăng count/thời gian; identity drop vẫn vào baseline để không đếm lại mỗi poll. Ngoài500event có **một frozen gap slot + một accumulator kế tiếp** (count và first/last). Gap có budget/correlation giống event, dùng chung in-flight nhỏ; ưu tiên slot gap. Sau freeze không sửa ID/payload; drop mới gom vào accumulator tiếp theo, sau ACK gap cũ tạo gap mới. Gap failed/exhausted vẫn giữ slot tới explicit retry/stop, accumulator vẫn bounded. Không lưu danh sách các event đã drop.

Server: auth lại → roleCANDIDATE → scopeACTIVE → validation → MonitoringGapService transaction → COMMIT → ACK `acknowledgedType:MONITORING_GAP`. V3 tạo monitoring_gaps, không sửaV2. Lock attempt FOR SHARE bảo vệ transaction khi state đổi; unique `(attempt_id,gap_id)`. Cùng normalized payload ACK lại/mộtrow; payload khácCONFLICT, không overwrite. LỗiCOMMIT rollback/RETRYABLE_SERVER_ERROR, không successACK. Gap không sửa monitoring_events/process state, không pushMONITOR_WARNING, chưa có gapRESTtimeline; B3/A/C giai đoạn sau phải dùng contract riêng nếu cần hiển thị gap. Process warning/events endpoint giữ nguyên.

Đây chỉ là hook overflow C3. T2-C2 còn event muộn/state/gap khác; không presence/reducer/full/delta/dashboard ở task này. Evidence `evidence/t1-c3/2026-10-04-verification.md`: real WindowsProcessHandle/B2/Spring/PostgreSQL; ACKlossSIMULATED, overflow sourceMOCK nhưng gapnetwork/commit/ACKREAL. MT01 PARTIAL tới B3; GUI/LAN/humanBreview NOTRUN.

## Contract T1-A4 — heartbeat và presence (04/10/2026)

### Bắt đầu, dừng và reconnect

Sau fresh `/api/v1/auth/me` và explicit start, `CandidateMonitoringSession` lấy UUID thật từ `ProcessCollector.start`, rồi gọi `MonitoringTransport.monitoringHeartbeat(attemptId,collectorSessionId)`. `RealtimeClient` gửi heartbeat đầu ngay khi CONNECTED và tiếp tục mỗi 2 giây mặc định. JVM property `toeic.realtime.heartbeatMillis` cấu hình chu kỳ; không đổi queue/in-flight/ACK budget C3.

Lease trả về chỉ gỡ binding mà chính nó tạo. Stop/logout/switch và mất quyền gỡ lease; callback/generation cũ không thể gỡ hoặc bật lại phiên mới. Reconnect cùng run giữ attempt/collector; không restart collector đã Stop. Chưa start, role PROCTOR hoặc scope rỗng dùng unscoped ping. Legacy Session fields vẫn phục vụ API B2; sau khi dùng lease thì lifecycle explicit quyết định binding, không khôi phục fields cũ sau Stop. Không có message STOP riêng: server chỉ UNKNOWN sau timeout tính từ heartbeat hợp lệ cuối, kể cả một heartbeat đang trên đường truyền khi người dùng Stop.

Scoped envelope dùng mẫu HEARTBEAT đầu file: `attemptId` bắt buộc có quyền candidate sở hữu ACTIVE, payload chỉ `sentAt` (ISO Instant bắt buộc) và `collectorSessionId` (identifier bắt buộc). Unscoped có `attemptId:null`, collector thiếu/null được phép; giữ ACK HEARTBEAT như B2, không tạo/refresh presence. Proctor không được gửi scoped heartbeat. Malformed, token hết hiệu lực, foreign/CLOSED/unknown bị từ chối trước refresh. PROCESS_OBSERVED/MONITORING_GAP không thay heartbeat.

Scoped heartbeat qua transaction proxy COMMIT rồi ACK và push; lỗi DB/COMMIT trả `RETRYABLE_SERVER_ERROR`, không ghi runtime freshness hoặc push thành công. Scheduler lỗi DB giữ association để retry, vẫn xử lý các attempt khác. Socket gửi ACK/ERROR/warning/presence chung decorator hiện có, không raw send song song.

### Trạng thái và thời gian

| status / reason | Ý nghĩa |
|---|---|
| ONLINE / HEARTBEAT | Vừa nhận và lưu heartbeat hợp lệ |
| UNKNOWN / NOT_SEEN | Chưa từng nhận; revision0, collector/lastSeenAt/timeoutDetectedAt null, không row timeout giả |
| UNKNOWN / HEARTBEAT_TIMEOUT | Không association hợp lệ nào còn heartbeat trong deadline |
| UNKNOWN / ACCESS_REVOKED | Tất cả association mất auth/scope/ACTIVE; không tạo heartbeat timeout giả |
| UNKNOWN / SERVER_RESTART | ONLINE lưu từ JVM trước đã được demote lúc startup; timeoutDetectedAt null |

ONLINE chỉ xác nhận liên lạc đã quan sát; không chứng minh collector chưa bị sửa hoặc mọi process được thấy. UNKNOWN không phải kết luận gian lận. `lastSeenAt` và `timeoutDetectedAt` dùng Clock server UTC, cắt dưới microsecond; `sentAt` client không quyết định timeout. Deadline dùng `System.nanoTime()` trong cùng JVM, nên chỉnh wall clock không kéo dài/rút ngắn deadline. Timestamp UTC có thể lùi khi đồng hồ server lùi; thứ tự update dùng revision, không dùng timestamp. Không so nanoTime giữa máy hoặc giữa lần chạy server.

Timeout mặc định `MONITORING_TIMEOUT_MS=6000`, scan `MONITORING_TIMEOUT_SCAN_MS=500`. Khi đủ ngưỡng, scan kế tiếp xử lý; không hứa đúng tuyệt đối 6 giây nếu DB/worker chậm. RAM giới hạn `MONITORING_PRESENCE_MAX_ATTEMPTS=4096`, `MONITORING_ASSOCIATIONS_PER_ATTEMPT=8`; hết chỗ trả retryable error, không nhận thành công giả.

Identity tách user / attempt / collector / socket. Mỗi socket+attempt có một collector hiện hành; nhiều socket hợp lệ cùng attempt được tổng hợp. Close callback không demote ngay: association hết hạn theo heartbeat hoặc bị thu quyền. Socket cũ đóng/hết hạn không phá socket mới còn hợp lệ. Scan dọn association expired/revoked/CLOSED; khi không còn eligible thì transaction cập nhật UNKNOWN một lần và xóa run RAM. Có khóa theo attempt bằng 128 stripe cố định, không một khóa chung toàn bộ client. Heartbeat và timeout tuần tự trên cùng stripe, DB compare-and-set ONLINE+expected revision chống timeout cũ ghi đè. Mỗi heartbeat/transition được commit tăng revision BIGINT theo attempt. Socket push sau commit và ngoài khóa DB/stripe; có thể đến lệch thứ tự, B3 phải đối chiếu revision. Prototype chạy một server JVM cùng schema; chưa hỗ trợ nhiều server writer đồng thời.

### Lịch sử gián đoạn do server

V4 mới tạo `monitoring_presence` và `monitoring_interruptions`, không sửa V1–V3. Interruption chứa gapId UUID ổn định, attempt/collector/socket, presence_revision, reason HEARTBEAT_TIMEOUT, last_seen_at, timeout_detected_at, recovered_at nullable. Unique `(attempt_id,presence_revision)` và một interruption chưa recovered mỗi attempt. Một lần timeout tạo một record/push UNKNOWN; scan tiếp không lặp. Heartbeat phục hồi cập nhật recoveredAt và giữ gapId/record. Đây là khoảng server biết, không phải thời điểm client chắc chắn dừng.

Startup demote persisted ONLINE thành UNKNOWN/SERVER_RESTART và tăng revision trước khi API/WS phục vụ. Không phục hồi deadline nanoTime cũ, không bịa downtime timestamp/gap; lịch sử trước restart giữ nguyên. Heartbeat mới mới làm ONLINE. Overflow C3 vẫn ở `monitoring_gaps`, reason QUEUE_OVERFLOW và payload sáu trường; không đưa HEARTBEAT_TIMEOUT vào MONITORING_GAP. T2-C2 còn state/event muộn/gap khác.

### Roster, presence push và history

GET roster chỉ PROCTOR; một SQL lọc ACTIVE + assignment, trả identity tối thiểu và snapshot. Mẫu TEST:

```json
{"protocolVersion":"v0","traceId":"TEST-trace","serverTime":"2026-10-04T00:00:02Z","attempts":[{"attemptId":"TEST-attempt-A","candidateUserId":101,"candidateDisplayName":"TEST Candidate A","status":"ONLINE","reason":"HEARTBEAT","revision":5,"collectorSessionId":"TEST-collector-A","lastSeenAt":"2026-10-04T00:00:02Z","timeoutDetectedAt":null}]}
```

`MONITOR_PRESENCE` payload **cùng PresenceSnapshot schema** với roster item, attemptId có cả envelope và payload. Ví dụ timeout:

```json
{"protocolVersion":"v0","type":"MONITOR_PRESENCE","messageId":"TEST-presence","requestId":null,"attemptId":"TEST-attempt-A","traceId":"TEST-trace","payload":{"attemptId":"TEST-attempt-A","candidateUserId":101,"candidateDisplayName":"TEST Candidate A","status":"UNKNOWN","reason":"HEARTBEAT_TIMEOUT","revision":6,"collectorSessionId":"TEST-collector-A","lastSeenAt":"2026-10-04T00:00:02Z","timeoutDetectedAt":"2026-10-04T00:00:08Z"}}
```

Registry revalidate auth, role PROCTOR và assignment ACTIVE trước từng push; offline/buffer lỗi không mất snapshot/history DB. Không trả raw socket/token/password/OS path. HTTP history chỉ PROCTOR có scope ACTIVE, order presence_revision; chưa phân trang:

```json
{"protocolVersion":"v0","traceId":"TEST-trace","attemptId":"TEST-attempt-A","interruptions":[{"gapId":"TEST-gap-A","attemptId":"TEST-attempt-A","collectorSessionId":"TEST-collector-A","reason":"HEARTBEAT_TIMEOUT","lastSeenAt":"2026-10-04T00:00:02Z","timeoutDetectedAt":"2026-10-04T00:00:08Z","recoveredAt":"2026-10-04T00:00:09Z"}]}
```

### Handoff B3

B cần cài dashboard và parser MONITOR_PRESENCE/MONITOR_WARNING. Mở WS/buffer trước rồi tải roster và timeline/history; hoặc tải trước rồi đọc lại sau handshake. Với presence, map theo attemptId, chỉ nhận revision lớn hơn hiện có; revision bằng nhau là cùng snapshot. HTTP về trễ không ghi đè WS revision mới. Revision0 NOT_SEEN là trạng thái chưa có heartbeat, không phải timeout. JSON revision là số nguyên BIGINT; Java dùng long.

Sau reconnect tải roster mới để bỏ attempt không còn được phân công; scope/assignment thay đổi cũng cần refresh. Khi chính dashboard mất mạng phải hiện dữ liệu cũ/stale dù last snapshot ONLINE. History gộp theo gapId, thay recoveredAt khi refresh; event gộp `(attemptId,eventId)`, schema/timeline A3 giữ nguyên. Push best effort không thay HTTP recovery. Không cài dashboard/parser B3 hoặc epoch/full/delta trong A4.

Demo tự động: `scripts/smoke-a4.ps1` login/fresh scope/explicit start hai candidate, event commit/ACK, hard-kill JVM riêng, timeout A trong khi B sống, scoped recovery và history; nguồn process MOCK, HTTP/WS/DB/B2/C3 thật. Demo Windows Edge thật vẫn `smoke-c3.ps1`. Evidence `evidence/t1-a4/2026-10-04-verification.md`; GUI/LAN/human C review NOT RUN.

### B3 đã consume — 04/10/2026

`MonitoringApiClient` đọc `/auth/me`, roster, events và interruptions bằng Bearer header. `MonitoringJson` validate v0, object/array, identity, số nguyên Java long chính xác, timestamp/null, status/reason và attempt nhất quán. Không đổi endpoint, envelope, DTO hay migration A3/A4. RealtimeClient thêm warning/presence vào onMessage, requestId null không đi qua pending ACK; fragment handling và B2 reconnect giữ nguyên.

Dashboard đăng ký listener trước khi mở WS; mỗi CONNECTED tải scope/roster và chi tiết đang chọn. Push chờ trong buffer có giới hạn. Roster HTTP đầy đủ quyết định membership; auth/me làm mới scope adapter. Presence chỉ thay khi revision lớn hơn; bằng nhau/cùng nội dung không đổi, bằng nhau/khác nội dung báo mâu thuẫn và đối chiếu HTTP. HTTP revision thấp không ghi đè WS mới. Push attempt chưa có trong roster không tự tạo identity hoặc cấp quyền; cần refresh/buffer hữu hạn.

Events dùng `(attemptId,eventId)`, cùng key khác nội dung báo lỗi. Giữ order response HTTP (`received_at,id`); push mới tạm nối cuối, không suy ra internal id hay tuyệt đối thứ tự timestamp bằng nhau. History dùng `(attemptId,gapId)`; refresh cho phép recoveredAt null thành giá trị thật, callback generation cũ không đưa nó về null. ONLINE không xóa history, presence không tự tạo gap. Overflow gap C3 vẫn chưa có REST/push cho dashboard.

HTTP/state worker riêng, snapshot bất biến và render JavaFX thread. Đổi selection, reconnect, logout và thu hồi quyền vô hiệu hóa callback cũ; 401 dọn phiên/đăng nhập lại, 403 chi tiết loại attempt rồi refresh scope/roster. Network/5xx/schema lỗi giữ dữ liệu cũ theo từng phần; không đổi candidate sang UNKNOWN vì dashboard mất mạng. Buffer/rows/body hữu hạn; overflow hiện cần đồng bộ HTTP, retry tự động có ngân sách 2 vòng mỗi refresh/reconnect, không lặp vô hạn.

Evidence [B3](../evidence/t1-b3/2026-10-04-verification.md): 331 tests, REAL PostgreSQL V4/HTTP/WS/dashboard/B2/C3 và Stage JavaFX proctor, hard-kill owned JVM/UNKNOWN/history/recovery, reconnect HTTP và auth. Process source B3 MOCK; C3 regression Windows ProcessHandle/owned Edge REAL. GUI toàn luồng candidate, LAN/package máy khác và human A review NOT RUN; prototype PARTIAL.

## C4 — instrumentation cục bộ, 05/10/2026

Wire vẫn `protocolVersion=v0`; không thêm field/message/endpoint/migration. `monitoring-measurement-v1` là phiên bản file log cục bộ, không gửi qua mạng. Định nghĩa và fields: [MONITORING_MEASUREMENTS](MONITORING_MEASUREMENTS.md).

Client serialize một lần cho check size/send/đếm byte; TX có ticket attempted rồi completed/failed. RX ghép đủ text một lần, kể cả Unicode qua fragment; malformed có byte RX/INVALID, binary/oversized UNMEASURED nullable byte theo rejection hiện có. Server nhận full text ở handler và đo outbound tại raw delegate nằm trong ConcurrentWebSocketSessionDecorator, vì enqueue chưa phải write xong. ACK/ERROR/warning/presence cùng đường send; không thêm counter ở service.

Ghi đo opt-in; auth/role/scope/pending correlation/ACK sau COMMIT/C3 retry/presence/dashboard giữ nguyên. BUSINESS_ACK chỉ sau B2 chấp nhận correlation; byte ACK đã tính ở RX, không cộng lần hai. Trong demo SIMULATED chặn observer C3, B2 vẫn nhận ACK, nên có thể thấy ACCEPTED rồi C3 gửi retry: đây là chủ đích test, không phải bằng chứng mất ACK trên mạng. Sequence v0 chưa có nên null; không dùng recordIndex làm sequence.

Cross-owner tối thiểu: B RealtimeClient, A RealtimeWebSocketHandler/RealtimeSessionRegistry. Human B review NOT RUN; [evidence C4](../evidence/t1-c4/2026-10-05-verification.md) ghi kiểm transport/recorder và dữ liệu REAL/MOCK/SIMULATED. Không triển khai state/full/delta/E1/E2.

## Contract T2-A1 — Import đề thi và Quản lý ca thi (05/10/2026)

### 1. Schema DB Flyway V5
- Tạo các bảng: `exams`, `exam_questions`, `exam_options`, `exam_sessions`, `exam_session_proctors`.
- Mở rộng bảng `monitoring_attempts`: thêm các cột `session_id`, `exam_id`, `deadline_at`, `writer_epoch`, `saved_revision`, `submitted_at`, `score`, `answers_json`.
- Cho phép các trạng thái `state IN ('ACTIVE', 'CLOSED', 'SUBMITTED', 'TIMED_OUT', 'INTERRUPTED')`.

### 2. Endpoint `POST /api/v1/exams/import` (Chỉ PROCTOR)
- Header: `Authorization: Bearer <token>`.
- Phân quyền: Role `PROCTOR` bắt buộc. Role `CANDIDATE` nhận `403 FORBIDDEN` (tuân thủ `AT02`).
- Payload import:
```json
{
  "examId": "EXAM-TOEIC-SAMPLE-10",
  "title": "TOEIC 10-Question Benchmark Exam",
  "description": "Đề thi mẫu gồm 5 câu Listening và 5 câu Reading",
  "questions": [
    {
      "questionId": "L1",
      "section": "LISTENING",
      "part": 1,
      "groupId": null,
      "passageText": null,
      "audioFile": "audio_part1_1.mp3",
      "prompt": "Listen and choose the best description",
      "correctOption": "A",
      "orderIndex": 1,
      "options": [
        {"optionId": "A", "optionText": "The woman is typing on a laptop.", "orderIndex": 1},
        {"optionId": "B", "optionText": "The woman is reading a book.", "orderIndex": 2}
      ]
    }
  ]
}
```
- Validation rules:
  - `examId`, `title`, `questions` không được rỗng.
  - `questionId` là duy nhất trong toàn bộ đề.
  - `section` thuộc `LISTENING` hoặc `READING`, `part` từ 1 đến 7.
  - Mỗi câu hỏi phải có tối thiểu 2 lựa chọn (`options`).
  - `correctOption` **BẮT BUỘC** phải khớp với một trong các `optionId` của câu hỏi.
  - Các câu hỏi chung `groupId` phải có nội dung `passageText` nhất quán.

### 3. Endpoint `POST /api/v1/sessions` (Chỉ PROCTOR)
- Tạo ca thi, gán danh sách thí sinh và giám thị.
- Tự động sinh `monitoring_attempts` (`state = 'ACTIVE'`) và `monitoring_proctor_assignments` cho từng thí sinh.

### 4. Endpoint `GET /api/v1/attempts/{attemptId}/exam` (Thí sinh / Giám thị)
- Header: `Authorization: Bearer <token>`.
- Kiểm quyền: `attemptId` phải thuộc quyền của người gọi (Candidate sở hữu hoặc Proctor được phân công).
- **QUY TẮC BẤT BIẾN:** Response DTO gửi xuống Client **TUYỆT ĐỐI KHÔNG CÓ** trường `correctOption`.
- Schema trả về:
```json
{
  "examId": "EXAM-TOEIC-SAMPLE-10",
  "title": "TOEIC 10-Question Benchmark Exam",
  "description": "Đề thi mẫu gồm 5 câu Listening và 5 câu Reading",
  "totalQuestions": 10,
  "questions": [
    {
      "questionId": "L1",
      "section": "LISTENING",
      "part": 1,
      "groupId": null,
      "passageText": null,
      "audioFile": "audio_part1_1.mp3",
      "prompt": "Listen and choose the best description",
      "orderIndex": 1,
      "options": [
        {"optionId": "A", "optionText": "The woman is typing on a laptop.", "orderIndex": 1},
        {"optionId": "B", "optionText": "The woman is reading a book.", "orderIndex": 2}
      ]
    }
  ]
}
```

## Contract T2-A2 — Autosave Toàn Bộ Đáp Án Theo Revision (05/10/2026)

### 1. Schema DB Flyway V6
- Tạo bảng `exam_autosave_requests`: lưu trữ idempotency request log gồm `attempt_id`, `request_id`, `writer_epoch`, `answer_revision`, `answers_json`, `decision_at`, `created_at`.

### 2. Endpoint `POST /api/v1/attempts/{attemptId}/answers` (Thí sinh)
- Header: `Authorization: Bearer <token>`.
- Phân quyền: Role `CANDIDATE` sở hữu `attemptId` (Proctor/unauthenticated/foreign attempt nhận 401/403).
- Request body:
```json
{
  "requestId": "sample-request-42",
  "attemptId": "sample-attempt-A",
  "writerEpoch": 1,
  "answerRevision": 42,
  "answers": {
    "L1": "A",
    "R1": "B"
  }
}
```
- Response 200 OK:
```json
{
  "requestId": "sample-request-42",
  "attemptId": "sample-attempt-A",
  "status": "SAVED",
  "savedRevision": 42,
  "writerEpoch": 1,
  "decisionAt": "2026-10-05T16:15:30.850Z"
}
```

### 3. Quy tắc Thứ tự Quyết định & Xử lý Lỗi (Tuân thủ Hợp đồng Mục 2):
1. **Xác thực & Kiểm quyền:** Trước khi đọc hoặc ghi. Sai role/không sở hữu -> 403 `FORBIDDEN`.
2. **Khóa bản ghi:** `SELECT ... FROM monitoring_attempts a WHERE a.attempt_id = :attemptId FOR UPDATE OF a`.
3. **Kiểm tra `writerEpoch` sau khóa:** Khác epoch hiện tại -> 409 `STALE`.
4. **Kiểm tra trạng thái bài thi:** Khác `ACTIVE` (ví dụ `SUBMITTED`, `TIMEOUT`) -> 409 `INVALID_STATE`.
5. **Kiểm tra deadline bằng `clock_timestamp()`:** `decisionAt >= deadlineAt` -> 409 `EXPIRED`.
6. **Kiểm tra hợp lệ đề thi:** Mọi `questionId` phải thuộc đề của attempt; mọi `optionId` phải thuộc câu hỏi đó. Sai -> 400 `INVALID_INPUT`.
7. **Idempotency theo requestId (AT05):** Cùng `requestId` và cùng payload -> 200 `ALREADY_SAVED`; cùng `requestId` khác payload -> 409 `CONFLICT`.
8. **Quy tắc Revision (AT03, AT04):**
   - `answerRevision < savedRevision`: 409 `STALE` (không ghi đè bản cũ).
   - `answerRevision == savedRevision`: Cùng nội dung (kể cả đổi thứ tự key JSON) -> 200 `ALREADY_SAVED`; khác nội dung -> 409 `CONFLICT`.
   - `answerRevision > savedRevision`: Cập nhật `monitoring_attempts` và ghi `exam_autosave_requests` -> 200 `SAVED`.
9. **Commit transaction:** Thành công trước khi trả response về client.

## Contract T2-A3 — Submit, Timeout và Chấm Điểm Một Lần (05/10/2026)

### 1. Schema DB Flyway V7
- Tạo bảng `exam_submit_requests`: lưu trữ idempotency request log cho submit gồm `attempt_id`, `request_id`, `writer_epoch`, `answer_revision`, `answers_json`, `total_questions`, `correct_count`, `listening_correct`, `reading_correct`, `score`, `submitted_at`, `decision_at`, `created_at`.
- Bổ sung các cột tính điểm trên `monitoring_attempts`: `total_questions`, `correct_count`, `listening_correct`, `reading_correct`.

### 2. Endpoint `POST /api/v1/attempts/{attemptId}/submit` (Thí sinh)
- Header: `Authorization: Bearer <token>`.
- Phân quyền: Role `CANDIDATE` sở hữu `attemptId` (Proctor/unauthenticated/foreign attempt nhận 401/403).
- Request body:
```json
{
  "requestId": "submit-request-001",
  "attemptId": "SESSION-T2A3-candidate1",
  "writerEpoch": 1,
  "answerRevision": 43,
  "answers": {
    "L1": "A",
    "L2": "B",
    "R1": "B",
    "R2": "A"
  }
}
```
- Response 200 OK:
```json
{
  "requestId": "submit-request-001",
  "attemptId": "SESSION-T2A3-candidate1",
  "state": "SUBMITTED",
  "totalQuestions": 4,
  "correctCount": 3,
  "listeningCorrect": 1,
  "readingCorrect": 2,
  "score": 3,
  "submittedAt": "2026-10-05T16:27:18.123Z",
  "decisionAt": "2026-10-05T16:27:18.123Z",
  "savedRevision": 43
}
```

### 3. Quy tắc Thứ tự Quyết định & Nghiệm thu (AT06, AT07, AT08):
1. **Xác thực & Kiểm quyền:** Trước khi đọc hoặc ghi. Sai role/không sở hữu -> 403 `FORBIDDEN`.
2. **Khóa bản ghi:** `SELECT ... FROM monitoring_attempts a WHERE a.attempt_id = :attemptId FOR UPDATE OF a`.
3. **Idempotency theo requestId (AT06):**
   - Nếu `(attempt_id, request_id)` đã tồn tại với cùng payload -> trả về kết quả đã chấm trước đó (200 OK).
   - Nếu cùng `requestId` khác payload -> 409 `CONFLICT`.
4. **Kiểm tra trạng thái bài thi (AT06):**
   - Nếu attempt không ở trạng thái `ACTIVE` (đã `SUBMITTED`, `TIMED_OUT`...) -> 409 `INVALID_STATE`.
   - Autosave sau khi bài đã submit/timeout -> 409 `INVALID_STATE`.
5. **Kiểm tra `writerEpoch` sau khóa:** Khác epoch hiện tại -> 409 `STALE`.
6. **Kiểm tra deadline bằng `clock_timestamp()` sau khóa (AT07):** `decisionAt >= deadlineAt` -> 409 `EXPIRED`.
7. **Kiểm tra hợp lệ đề thi:** Mọi `questionId` và `optionId` phải thuộc đề thi. Sai -> 400 `INVALID_INPUT`.
8. **Quy tắc Revision:** `answerRevision < savedRevision` -> 409 `STALE` (không tự chốt bằng bản cũ).
9. **Chấm điểm độc lập tại Server (Scoring Engine):**
   - Đối chiếu với đáp án đúng `correct_option` từ `exam_questions`.
   - Tính toán `totalQuestions`, `correctCount`, `listeningCorrect`, `readingCorrect`, `score`.
   - Cập nhật `monitoring_attempts` (`state = 'SUBMITTED'`) và ghi `exam_submit_requests`.
   - Commit transaction thành công trước khi trả response về client.

### 4. Tác vụ Timeout Định kỳ (`ExamTimeoutService` - AT08)
- Định kỳ quét các attempt quá hạn (`state = 'ACTIVE'` và `deadline_at <= clock_timestamp()`).
- Mở transaction khóa bi quan `FOR UPDATE OF a`, kiểm tra lại với `clock_timestamp()`.
- Chấm điểm trên bộ đáp án đã lưu (`saved_answers`), cập nhật trạng thái `TIMED_OUT` và lưu điểm số vào DB.
- Timeout không nhận đáp án mới từ client; mọi request nộp bài sau đó đều bị từ chối 409 `INVALID_STATE`.

## Contract T2-A4 — Phiên Ghi (Takeover Writer) và Kiểm Thử Tranh Chấp (05/10/2026)

### 1. Endpoint `POST /api/v1/attempts/{attemptId}/takeover` (Thí sinh)
- Header: `Authorization: Bearer <token>`.
- Phân quyền: Role `CANDIDATE` sở hữu `attemptId` (Proctor/unauthenticated/foreign attempt nhận 401/403).
- Request body:
```json
{
  "requestId": "takeover-request-001",
  "attemptId": "SESSION-T2A4-candidate1"
}
```
- Response 200 OK:
```json
{
  "requestId": "takeover-request-001",
  "attemptId": "SESSION-T2A4-candidate1",
  "writerEpoch": 2,
  "savedRevision": 42,
  "state": "ACTIVE",
  "deadlineAt": "2026-10-05T18:00:00Z",
  "answers": {
    "L1": "A",
    "R1": "B"
  }
}
```
- Cơ chế hoạt động:
  - Khóa bi quan `SELECT ... FOR UPDATE OF a`.
  - Kiểm tra trạng thái bài thi: nếu không ở trạng thái `ACTIVE` (ví dụ `SUBMITTED`, `TIMED_OUT`) -> từ chối với 409 `INVALID_STATE`.
  - Tăng `writer_epoch = writer_epoch + 1`.
  - Commit transaction và trả về trạng thái phiên thi mới nhất cùng `writerEpoch` mới. Mọi writer cũ giữ `writerEpoch` cũ sẽ bị chặn với 409 `STALE` (AT09).

### 2. Endpoint `GET /api/v1/attempts/{attemptId}/status` (Thí sinh / Giám thị)
- Header: `Authorization: Bearer <token>`.
- Phân quyền: `attemptId` phải thuộc quyền của người gọi (Candidate sở hữu hoặc Proctor được phân công).
- Response 200 OK:
```json
{
  "attemptId": "SESSION-T2A4-candidate1",
  "sessionId": "SESSION-T2A4",
  "examId": "EXAM-TOEIC-SAMPLE-10",
  "candidateUsername": "candidate1",
  "writerEpoch": 2,
  "savedRevision": 42,
  "state": "ACTIVE",
  "startedAt": "2026-10-05T16:00:00Z",
  "deadlineAt": "2026-10-05T18:00:00Z",
  "answers": {
    "L1": "A",
    "R1": "B"
  },
  "totalQuestions": 4,
  "score": null,
  "submittedAt": null
}
```

### 3. Nghiệm thu Concurrency & Tranh chấp (AT01, AT07, AT09, AT10):
- **AT01 (Cross-Candidate Access Isolation):** Thí sinh B không thể đọc, autosave, submit, hay takeover trên attempt của Thí sinh A (403 FORBIDDEN) cả trước và sau khi submit.
- **AT07 (Pessimistic Lock Held Past Deadline):** Khi một transaction giữ khóa bi quan qua hạn `deadlineAt`, request khác bị chặn chờ khóa khi được xử lý sau khi nhả khóa sẽ kiểm tra lại `clock_timestamp()` và bị từ chối với 409 `EXPIRED`.
- **AT09 (Takeover & Stale Writer Concurrency):** Request của Writer 1 với stale epoch đang chờ lock bị từ chối với 409 `STALE` sau khi Writer 2 takeover và commit thành công. Writer 2 với `writerEpoch` mới ghi đáp án thành công.
- **AT10 (Tampering After Deadline & Timeout Job):** Mọi request gửi sau deadline đều bị từ chối ngay lập tức với 409 `EXPIRED` qua kiểm tra `clock_timestamp() >= deadline_at` sau khóa, không phụ thuộc vào việc background timeout job đã chạy hay chưa.

## B consume contract T2-A1…T2-A4 — 09/10/2026

Không thêm hay đổi endpoint, message hoặc DTO. Phần này ghi cách client JavaFX (PR #17) dùng các endpoint ở trên và những chỗ hành vi server khác tài liệu, đã chạy thật ngày 09/10 trên `main` `8eba405`.

| Bước của client | Endpoint | Ghi chú |
|---|---|---|
| Vào phòng thi | `GET /status` → `GET /exam` → `POST /takeover` | Làm bài bằng `writerEpoch` do takeover trả (QD-12) |
| Autosave | `POST /answers` | Toàn bộ map; retry giữ nguyên requestId, epoch, revision, answers; chỉ coi là ACK khi `status` là `SAVED`/`ALREADY_SAVED` và `savedRevision` bằng revision đã gửi |
| Biết deadline | `GET /status` sau ACK đầu tiên | Response autosave không có `deadlineAt` |
| Nộp bài | `POST /submit` | Một payload đóng băng; bài chưa sửa lần nào nộp với `answerRevision = 1` vì server từ chối 0 |
| Kết quả không rõ, hết giờ, kết nối lại, `STALE` | `GET /status` | `STALE` không cho biết do epoch hay revision nên client đọc status để phân biệt |

Client rẽ nhánh theo `error.code` và HTTP status, không theo `error.message`, và không hiển thị lại câu chữ của server. 5xx, timeout, mất mạng và body 2xx không đọc được đều được coi là "chưa rõ kết quả".

Hành vi server khác tài liệu (bằng chứng `evidence/t2-b/2026-10-09-verification.md`):

1. `GET /status` và `GET /exam` trả `403 FORBIDDEN` cho chính thí sinh sở hữu khi lượt đã `SUBMITTED` hoặc `TIMED_OUT`; tài liệu mô tả status đọc được ở mọi trạng thái.
2. Lượt quá hạn không tự chuyển `TIMED_OUT` trong bản jar: `ExamTimeoutService` có `@Scheduled` nhưng server không bật scheduling.
3. `deadlineAt` là null cho tới lần autosave thành công đầu tiên.
4. Mẫu JSON của `GET /status` ở trên có `candidateUsername`, `startedAt` và `"score": null`; record `CandidateAttemptStatusResponse` thật không có hai trường đầu và `score` là số.

Tham số phía client (system property, đổi khi chạy JVM):

| Property | Mặc định | Ý nghĩa |
|---|---|---|
| `toeic.exam.autosaveDebounceMillis` | 800 | Chờ không có thay đổi rồi mới gửi |
| `toeic.exam.retryInitialMillis` / `toeic.exam.retryMaxMillis` | 1000 / 8000 | Khoảng chờ retry, gấp đôi mỗi lần |
| `toeic.exam.maxRetries` | 4 | Số lần tự gửi lại autosave, nộp bài, đối chiếu |
| `toeic.exam.statusPollMillis` / `toeic.exam.maxStatusPolls` | 1000 / 30 | Hỏi trạng thái sau khi hết giờ |
| `toeic.exam.httpTimeoutMillis` | 10000 | Timeout mỗi request HTTP của màn thi |
