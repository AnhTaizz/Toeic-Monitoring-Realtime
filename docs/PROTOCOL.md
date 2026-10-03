# Protocol đang dùng

**Trạng thái: v0 đã chốt cho login; message WebSocket bên dưới là MOCK cho tới khi T1-A2/T1-C3 nối server thật.** File này mô tả những gì code thật đang gửi và nhận. Quy tắc nghiệp vụ đằng sau nằm ở `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`; không chép lại ở đây.

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
| _timeline của một thí sinh_ | Giám thị | | | | T1-A3 | Chưa có |
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

Token chỉ trả trong JSON body của login. Cách gửi credential cho các request HTTP/WS sau login vẫn chờ QD-03/T1-A2; không đặt token trong URL query.

## Danh mục message WebSocket

| `type` | Hướng | Payload | ACK | Task | Trạng thái |
|---|---|---|---|---|---|
| _heartbeat_ | client → server | | | T1-C1, T1-A4 | Chưa có |
| _event process_ | thí sinh → server | | Sau khi commit | T1-C3, T1-A3 | Chưa có |
| _cảnh báo_ | server → giám thị | | | T1-A3, T1-B3 | Chưa có |
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
