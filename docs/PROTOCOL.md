# Protocol đang dùng

**Trạng thái: v0 có login/Bearer/heartbeat; C3 nối collector → event/queue/retry → A3 DB/ACK/warning/timeline. MONITORING_GAP tối thiểu cho overflow đã có persistence/ACK riêng. B3 dashboard, A4 presence và full/delta chưa có.** File này mô tả những gì code thật đang gửi và nhận. Quy tắc nghiệp vụ đằng sau nằm ở `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`; không chép lại ở đây.

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
| _import đề / tạo ca_ | Giám thị | | | | T2-A1 | Chưa có |
| _lấy đề (không có đáp án đúng)_ | Thí sinh | | | | T2-A1 | Chưa có |
| _autosave_ | Thí sinh | | | | T2-A2 | Chưa có |
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
| `HEARTBEAT` | client → server | `sentAt` bắt buộc; `collectorSessionId` optional | ACK transport | T1-A2 | Đã cài; chưa có presence T1-A4 |
| `ACK` (heartbeat) | server → client | `status: ACCEPTED`, `acknowledgedType: HEARTBEAT` | Không | T1-A2 | Đã cài |
| `ERROR` | server → client | `code`, `message`, `retryable` | Không | T1-A2 | Đã cài |
| `PROCESS_OBSERVED` | thí sinh → server | Event v0; attempt bắt buộc | ACK sau commit | T1-A3/C3 | Collector→queue→B2→DB/ACK đã cài |
| `MONITORING_GAP` | thí sinh → server | C3 QUEUE_OVERFLOW; attempt bắt buộc | ACK sau commit | T1-C3 + hook A | Đã cài tối thiểu; chưa reducer/state |
| `ACK` (event) | server → thí sinh | `status: ACCEPTED`, `acknowledgedType: PROCESS_OBSERVED` | Không | T1-A3 | Đã cài |
| `MONITOR_WARNING` | server → giám thị được phân công | Timeline item v0 | Không | T1-A3/B3 | Server đã cài; dashboard chưa có |
| _presence (ONLINE/UNKNOWN)_ | server → giám thị | | | T1-A4 | Chưa có |
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

## Contract T1-A2 và handoff cho B2

### Kết nối và credential

- URL: `ws://<server>:<port>/ws/v1/realtime`; mặc định port 8080. HTTPS deployment dùng `wss://`. Không thêm `/api` vào path WS.
- Handshake header: `Authorization: Bearer <token>` (scheme Bearer không phân biệt hoa/thường). Chỉ nhận một header, một khoảng trắng giữa scheme/token; token opaque do login cấp. Thiếu/sai/malformed/expired/revoked/disabled → HTTP 401, `WWW-Authenticate: Bearer`, không upgrade.
- Endpoint WS không nhận query; kể cả header hợp lệ kèm query cũng bị 401. Không dùng URL query, subprotocol hay message AUTH để gửi token.
- Mỗi reconnect tạo WS mới và gửi header auth lại. Server giữ user và **hash** session trong attributes; mỗi message tra DB lại để kiểm expiry/revoke/enabled/current role. Phiên bị vô hiệu → ERROR UNAUTHORIZED rồi close 1008. Lookup session tạm thời lỗi → handshake 503 hoặc WS ERROR RETRYABLE_SERVER_ERROR; không trả stack trace.
- REST `/api/**` ngoại trừ POST login dùng cùng Bearer semantics và identity/principal. Token trong query `token`/`access_token` bị từ chối; sai role/scope → 403 FORBIDDEN. Không tự bật browser CORS wildcard.

### Trường nhận ở T1-A2

| Trường | Quy tắc |
|---|---|
| protocolVersion | Bắt buộc string `v0` |
| type | Bắt buộc string; HEARTBEAT và PROCESS_OBSERVED được hỗ trợ (event từ A3) |
| messageId | Bắt buộc, 1–128 ký tự `[A-Za-z0-9_.:-]` |
| traceId | Bắt buộc, cùng giới hạn ID; ACK giữ correlation |
| requestId | Có thể thiếu/null, khi đó dùng messageId; nếu có phải bằng messageId |
| attemptId | HEARTBEAT có thể thiếu/null. Nếu có phải là ID hợp lệ và được AttemptScopeAuthorizer cho phép |
| payload | HEARTBEAT bắt buộc object, `sentAt` là string ISO-8601 Instant; `collectorSessionId` optional, nếu có là ID hợp lệ |

Giới hạn text message/buffer: 65.536 byte mặc định, configurable; send timeout 5.000ms. Message vượt giới hạn container có thể bị đóng 1009; binary không được hỗ trợ. JSON sai/type lạ/thiếu trường trong giới hạn → ERROR INVALID_INPUT chỉ tới session đó; kết nối vẫn nhận heartbeat tiếp theo.

**Heartbeat thiếu attemptId chỉ là transport ping của phiên authenticated**, dùng khi login chưa cấp attempt. Nó không ghi presence/ONLINE/UNKNOWN, không bật collector và không cấp quyền truy cập attempt. Nếu có attemptId, auth → scope check → ACK. Từ A3, production scope đọc assignment PostgreSQL: candidate sở hữu hoặc proctor được phân công vào attempt ACTIVE. Không có assignment thì scope rỗng; không tạo fixture production.

### HEARTBEAT/ACK đã cài

Các ID/thời gian dưới đây là giá trị minh họa; shape được kiểm qua network thật:

```json
{"protocolVersion":"v0","type":"HEARTBEAT","messageId":"sample-hb-001","requestId":"sample-hb-001","attemptId":null,"traceId":"sample-trace-001","payload":{"sentAt":"2026-10-04T00:00:00Z"}}
```

```json
{"protocolVersion":"v0","type":"ACK","messageId":"server-generated-id","requestId":"sample-hb-001","attemptId":null,"traceId":"sample-trace-001","payload":{"status":"ACCEPTED","acknowledgedType":"HEARTBEAT"}}
```

ACK heartbeat xác nhận transport đã nhận/chấp nhận message; **không phải ACK event persistence/DB commit**. ACK PROCESS_OBSERVED từ A3 chỉ gửi sau commit, theo contract bên dưới. Full/delta và message khác chưa được hỗ trợ.

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
