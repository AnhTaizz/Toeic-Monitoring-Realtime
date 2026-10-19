# Backend API — tài liệu tích hợp đầy đủ

Tài liệu này liệt kê các endpoint HTTP và message WebSocket đang được code backend hỗ trợ tại protocol `v0`. Nội dung được tổng hợp trực tiếp từ controller, DTO, service và bộ lọc xác thực hiện tại.

## 1. Quy ước chung

- Base HTTP: `http://<server>:8080`
- WebSocket: `ws://<server>:8080/ws/v1/realtime`
- JSON dùng UTF-8; timestamp dùng ISO-8601 UTC, ví dụ `2026-10-19T10:15:30Z`.
- Trừ login, mọi route `/api/**` yêu cầu đúng một header `Authorization: Bearer <token>`.
- Không truyền token bằng query `token` hoặc `access_token`.
- Token sống 8 giờ, có thể hết hạn/bị thu hồi/tài khoản bị disable.
- Các response chứa trạng thái nhạy cảm thường dùng `Cache-Control: no-store`.
- Role do server đọc từ DB: `CANDIDATE` hoặc `PROCTOR`.
- `attemptId` monitoring v0 chỉ nhận `[A-Za-z0-9_.:-]{1,128}`. Hai API đọc status/history còn hỗ trợ ID DB Unicode qua query đã URL-encode.

### Error HTTP chuẩn

```json
{
  "protocolVersion": "v0",
  "type": "ERROR",
  "requestId": "req-123",
  "traceId": "server-trace-id",
  "error": {
    "code": "INVALID_INPUT",
    "message": "Mô tả an toàn cho client",
    "retryable": false
  }
}
```

| HTTP | Trường hợp thường gặp |
|---:|---|
| 400 | JSON/field/query không hợp lệ. |
| 401 | Thiếu/sai/hết hạn token; có `WWW-Authenticate: Bearer`. |
| 403 | Sai role, không sở hữu hoặc không được phân công attempt/session. |
| 404 | Route/tài nguyên API không tồn tại. |
| 405 | Sai HTTP method; response có header `Allow` nếu framework xác định được. |
| 409 | State, epoch, revision, deadline, manifest hoặc idempotency conflict. |
| 500 | Lỗi server chưa phân loại; không lộ stack trace. |
| 503 | Không thể kiểm tra session ở authentication filter. |

Mã lỗi: `UNAUTHORIZED`, `FORBIDDEN`, `INVALID_INPUT`, `INVALID_STATE`, `STALE`, `CONFLICT`, `EXPIRED`, `RETRYABLE_SERVER_ERROR`.

## 2. Danh mục nhanh HTTP

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/auth/me` | Đã đăng nhập |
| POST | `/api/v1/exams/import` | PROCTOR |
| POST | `/api/v1/sessions` | PROCTOR |
| GET | `/api/v1/candidate/attempts` | CANDIDATE |
| GET | `/api/v1/attempts/{attemptId}/exam` | Owner candidate hoặc assigned proctor |
| POST | `/api/v1/attempts/{attemptId}/answers` | Owner CANDIDATE |
| POST | `/api/v1/attempts/{attemptId}/submit` | Owner CANDIDATE |
| POST | `/api/v1/attempts/{attemptId}/takeover` | Owner CANDIDATE |
| GET | `/api/v1/attempts/{attemptId}/status` | Owner candidate hoặc assigned proctor |
| GET | `/api/v1/attempts/status?attemptId=...` | Như route status trên; hỗ trợ ID có `/` |
| GET | `/api/v1/proctor/attempts/progress` | PROCTOR |
| GET | `/api/v1/attempts/{attemptId}/audio/manifest` | Owner CANDIDATE |
| GET | `/api/v1/attempts/{attemptId}/audio/files/{fileName}` | Owner CANDIDATE |
| POST | `/api/v1/attempts/{attemptId}/audio/ready` | Owner CANDIDATE |
| GET | `/api/v1/sessions/{sessionId}/audio/readiness` | Assigned PROCTOR |
| POST | `/api/v1/sessions/{sessionId}/listening/start` | Assigned PROCTOR |
| GET | `/api/v1/attempts/{attemptId}/listening/status` | Có quyền đọc attempt |
| POST | `/api/v1/attempts/{attemptId}/listening/events` | Owner CANDIDATE |
| POST | `/api/v1/attempts/{attemptId}/listening/reorganize` | Assigned PROCTOR |
| GET | `/api/v1/monitoring/attempts` | PROCTOR; chỉ attempt được phân công |
| GET | `/api/v1/monitoring/attempts/{attemptId}/interruptions` | Assigned PROCTOR |
| GET | `/api/v1/monitoring/attempts/{attemptId}/state` | Assigned PROCTOR |
| GET | `/api/v1/monitoring/attempts/{attemptId}/events` | Assigned PROCTOR |
| GET | `/api/v1/monitoring/attempts/{attemptId}/gaps` | Assigned PROCTOR |

## 3. Authentication

### POST `/api/v1/auth/login`

Không cần Bearer token.

Request:

```json
{
  "requestId": "login-001",
  "username": "candidate1",
  "password": "<password>"
}
```

`requestId`, username và password đều bắt buộc, không được blank. Username được trim trước khi lookup.

Response 200:

```json
{
  "protocolVersion": "v0",
  "requestId": "login-001",
  "traceId": "...",
  "token": "...",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-19T18:00:00Z",
  "user": {
    "id": 1,
    "username": "candidate1",
    "displayName": "Candidate 1",
    "role": "CANDIDATE"
  },
  "attemptScope": ["session-1-candidate1"]
}
```

Sai credential trả 401; input thiếu trả 400. Không log hoặc hiển thị password/token.

### GET `/api/v1/auth/me`

Trả identity hiện tại và danh sách attempt `ACTIVE` thuộc scope tại thời điểm đọc.

```json
{
  "protocolVersion": "v0",
  "traceId": "...",
  "user": {"userId": 1, "username": "candidate1", "role": "CANDIDATE"},
  "attemptScope": ["session-1-candidate1"]
}
```

`attemptScope` không phải lịch sử bài thi; dùng API candidate attempts để tìm lượt đã kết thúc.

## 4. Đề thi và ca thi

### POST `/api/v1/exams/import`

Chỉ PROCTOR. Import là một transaction; `examId` đã tồn tại bị từ chối, không overwrite.

```json
{
  "examId": "exam-01",
  "title": "TOEIC Test 01",
  "description": "...",
  "questions": [
    {
      "questionId": "q1",
      "section": "LISTENING",
      "part": 1,
      "groupId": null,
      "passageText": null,
      "audioFile": "q1.mp3",
      "prompt": "Question 1",
      "correctOption": "B",
      "orderIndex": 1,
      "options": [
        {"optionId": "A", "optionText": "A", "orderIndex": 1},
        {"optionId": "B", "optionText": "B", "orderIndex": 2}
      ]
    }
  ]
}
```

Validation chính:

- exam ID/title và danh sách câu bắt buộc;
- question ID không trùng; section chỉ `LISTENING`/`READING`; part 1–7;
- prompt bắt buộc; ít nhất 2 option; option ID không trùng;
- `correctOption` phải thuộc option của câu;
- cùng `groupId` không được có `passageText` khác nhau;
- DB V11 còn bắt thứ tự câu/option duy nhất và section/part nhất quán.

Response 200:

```json
{"examId":"exam-01","title":"TOEIC Test 01","totalQuestions":200,"status":"IMPORTED"}
```

Lưu ý: request hiện là contract V5 dạng phẳng. Backend tạo group normalized từ `groupId`, nhưng request chưa nhận provenance, ordered content blocks hoặc media assets V11.

### POST `/api/v1/sessions`

Chỉ PROCTOR. Tạo ca, assignment và một attempt `ACTIVE` cho từng candidate.

```json
{
  "sessionId": "session-01",
  "examId": "exam-01",
  "title": "Ca sáng",
  "durationSeconds": 7200,
  "proctorUsernames": ["proctor1"],
  "candidateUsernames": ["candidate1", "candidate2"]
}
```

Candidate phải tồn tại đúng role; danh sách candidate không rỗng/không trùng. Attempt ID được tạo theo `<sessionId>-<candidateUsername>` và phải tương thích monitoring v0. Session/exam phải tồn tại hợp lệ; va chạm ID bị từ chối và transaction rollback.

Response 200:

```json
{
  "sessionId": "session-01",
  "examId": "exam-01",
  "title": "Ca sáng",
  "durationSeconds": 7200,
  "state": "SCHEDULED",
  "createdAttempts": [
    {"attemptId":"session-01-candidate1","candidateUsername":"candidate1","state":"ACTIVE"}
  ]
}
```

## 5. Candidate exam, lưu bài và kết quả

### GET `/api/v1/candidate/attempts`

Chỉ CANDIDATE. Query hợp lệ: `limit` (mặc định 50, 1–100) và `afterAttemptId`. Không chấp nhận query lạ hoặc lặp. Phân trang keyset theo `attemptId COLLATE "C"`.

```json
{
  "attempts": [
    {
      "attemptId": "session-01-candidate1",
      "sessionId": "session-01",
      "examId": "exam-01",
      "examTitle": "TOEIC Test 01",
      "sessionTitle": "Ca sáng",
      "state": "ACTIVE",
      "submittedAt": null
    }
  ],
  "nextAfterAttemptId": null
}
```

### GET `/api/v1/attempts/{attemptId}/exam`

Owner candidate hoặc assigned proctor. Response không có `correctOption`.

```json
{
  "examId": "exam-01",
  "title": "TOEIC Test 01",
  "description": "...",
  "totalQuestions": 200,
  "questions": [
    {
      "questionId": "q1",
      "section": "LISTENING",
      "part": 1,
      "groupId": null,
      "passageText": null,
      "audioFile": "q1.mp3",
      "prompt": "Question 1",
      "orderIndex": 1,
      "options": [{"optionId":"A","optionText":"A","orderIndex":1}]
    }
  ]
}
```

### POST `/api/v1/attempts/{attemptId}/takeover`

Owner CANDIDATE. Cấp writer mới, tăng `writerEpoch`, và nếu chưa có thì chốt deadline theo DB clock. `attemptId` URL phải trùng body.

```json
{"requestId":"takeover-1","attemptId":"session-01-candidate1","clientSessionId":"client-uuid"}
```

```json
{
  "requestId":"takeover-1",
  "attemptId":"session-01-candidate1",
  "writerEpoch":2,
  "savedRevision":0,
  "state":"ACTIVE",
  "deadlineAt":"2026-10-19T12:00:00Z",
  "answers":{}
}
```

### POST `/api/v1/attempts/{attemptId}/answers`

Owner CANDIDATE. Gửi **toàn bộ** map answers, không phải patch.

```json
{
  "requestId":"save-17",
  "attemptId":"session-01-candidate1",
  "writerEpoch":2,
  "answerRevision":17,
  "answers":{"q1":"B","q2":"D"}
}
```

```json
{
  "requestId":"save-17",
  "attemptId":"session-01-candidate1",
  "status":"SAVED",
  "savedRevision":17,
  "writerEpoch":2,
  "decisionAt":"2026-10-19T10:30:00Z"
}
```

Quy tắc:

- `requestId`/attempt bắt buộc; epoch/revision > 0.
- Mỗi question/option phải thuộc đúng exam.
- Cùng request ID + cùng payload trả lại quyết định cũ; khác payload trả `CONFLICT`.
- Revision thấp hơn trả `STALE`; cùng revision khác nội dung trả `CONFLICT`.
- Kiểm ownership, lock attempt, epoch, state và deadline trước commit; ACK chỉ sau commit.
- Listening đã gián đoạn làm phần Listening bị giữ theo bản đã commit; Reading vẫn có thể lưu trong deadline.

### POST `/api/v1/attempts/{attemptId}/submit`

Body giống autosave. Submit lưu snapshot, chấm một lần và chốt attempt.

```json
{
  "requestId":"submit-1",
  "attemptId":"session-01-candidate1",
  "writerEpoch":2,
  "answerRevision":18,
  "answers":{"q1":"B","q2":"D"}
}
```

```json
{
  "requestId":"submit-1",
  "attemptId":"session-01-candidate1",
  "state":"SUBMITTED",
  "totalQuestions":200,
  "correctCount":150,
  "listeningCorrect":75,
  "readingCorrect":75,
  "score":750,
  "submittedAt":"2026-10-19T11:30:00Z",
  "decisionAt":"2026-10-19T11:30:00Z",
  "savedRevision":18,
  "listeningInterrupted":false
}
```

Sau `SUBMITTED`/`TIMED_OUT`, answers và score không đổi. Timeout tự động dùng cùng row lock và DB clock với save/submit.

### GET status

- `/api/v1/attempts/{attemptId}/status`
- `/api/v1/attempts/status?attemptId=<URL-encoded-id>`

Route query dùng cho ID DB có ký tự path separator; chỉ nhận đúng một query `attemptId` dài tối đa 128 Unicode code point, không NUL.

```json
{
  "attemptId":"session-01-candidate1",
  "sessionId":"session-01",
  "examId":"exam-01",
  "writerEpoch":2,
  "savedRevision":18,
  "state":"SUBMITTED",
  "deadlineAt":"...",
  "submittedAt":"...",
  "totalQuestions":200,
  "correctCount":150,
  "listeningCorrect":75,
  "readingCorrect":75,
  "score":750,
  "answers":{"q1":"B"},
  "listeningInterrupted":false
}
```

### GET `/api/v1/proctor/attempts/progress`

Chỉ PROCTOR, chỉ attempt được phân công và state `ACTIVE`, `SUBMITTED`, `TIMED_OUT`. Query/pagination giống candidate attempts.

```json
{
  "readAt":"2026-10-19T10:45:00Z",
  "attempts":[{
    "attemptId":"session-01-candidate1",
    "sessionId":"session-01",
    "candidateUsername":"candidate1",
    "candidateDisplayName":"Candidate 1",
    "state":"ACTIVE",
    "savedRevision":17,
    "answeredCount":120,
    "totalQuestions":200,
    "lastSavedAt":"...",
    "deadlineAt":"...",
    "submittedAt":null,
    "correctCount":null
  }],
  "nextAfterAttemptId":null
}
```

Khi attempt còn `ACTIVE`, `correctCount` luôn null và response không chứa answers/writer credential.

## 6. Audio và Listening

### GET `/api/v1/attempts/{attemptId}/audio/manifest`

Owner CANDIDATE; attempt phải ACTIVE/chưa hết hạn và session chưa CLOSED.

```json
{
  "protocolVersion":"v0",
  "attemptId":"session-01-candidate1",
  "sessionId":"session-01",
  "manifestId":"<sha256>",
  "files":[{"fileName":"part1.mp3","sizeBytes":123456,"sha256":"<sha256>"}]
}
```

### GET `/api/v1/attempts/{attemptId}/audio/files/{fileName}?manifestId=...`

Trả bytes với `Content-Type: application/octet-stream`, `Content-Length`, `X-Content-Type-Options: nosniff`, `X-Audio-SHA256` và `Cache-Control: no-store`. File phải thuộc manifest hiện tại; server kiểm lại size/hash trước response.

### POST `/api/v1/attempts/{attemptId}/audio/ready`

Owner CANDIDATE, chỉ lúc session `SCHEDULED`. READY gắn với writer epoch và manifest chính xác.

```json
{
  "requestId":"ready-1",
  "attemptId":"session-01-candidate1",
  "writerEpoch":2,
  "ready":true,
  "manifestId":"<sha256>",
  "checksums":{"part1.mp3":"<sha256>"},
  "speakerTestPassed":true
}
```

`ready:false` rút READY và có thể để manifest/checksums null. READY true yêu cầu đủ file/checksum và speaker test pass.

### GET `/api/v1/sessions/{sessionId}/audio/readiness`

Assigned PROCTOR. Trả manifest và READY của từng attempt:

```json
{
  "protocolVersion":"v0",
  "sessionId":"session-01",
  "manifestId":"<sha256>",
  "allReady":true,
  "attempts":[{"attemptId":"session-01-candidate1","ready":true}]
}
```

### POST `/api/v1/sessions/{sessionId}/listening/start`

Assigned PROCTOR. Tất cả attempt hợp lệ phải READY đúng manifest. Commit authorization trước khi phát WS `LISTENING_START`.

```json
{"requestId":"start-1","manifestId":"<sha256>"}
```

```json
{
  "protocolVersion":"v0",
  "requestId":"start-1",
  "sessionId":"session-01",
  "manifestId":"<sha256>",
  "startId":"...",
  "startedAt":"2026-10-19T10:00:00Z"
}
```

Retry tường minh cùng request hợp lệ có thể phát lại command cho attempt còn ARMED; reconnect không tự replay/resume.

### GET `/api/v1/attempts/{attemptId}/listening/status`

Owner candidate hoặc assigned proctor.

```json
{
  "protocolVersion":"v0",
  "requestId":null,
  "attemptId":"session-01-candidate1",
  "sessionId":"session-01",
  "startId":"...",
  "manifestId":"...",
  "writerEpoch":2,
  "phase":"PLAYING",
  "deadlineAt":"...",
  "playbackStartedAt":"...",
  "playbackCompletedAt":null,
  "interrupted":false,
  "interruptedAt":null,
  "interruptionReason":null
}
```

### POST `/api/v1/attempts/{attemptId}/listening/events`

Owner CANDIDATE. Event hợp lệ: `STARTED`, `COMPLETED`, `HEARTBEAT`, `INTERRUPTED`. `INTERRUPTED` chỉ nhận reason `NETWORK_LOST` hoặc `MEDIA_ERROR`; các event khác phải để reason null.

```json
{"requestId":"listen-4","startId":"...","writerEpoch":2,"event":"STARTED","reason":null}
```

Response là `ListeningStatusResponse`. Server dùng DB clock, không nhận client timestamp. Interruption là terminal cho phần Listening của attempt đó.

### POST `/api/v1/attempts/{attemptId}/listening/reorganize`

Assigned PROCTOR; chỉ attempt đã interrupted. Tạo session và attempt mới, không sửa/xóa lượt cũ.

```json
{"requestId":"reorg-1","newSessionId":"session-01-retry","title":"Thi lại Listening"}
```

```json
{
  "protocolVersion":"v0",
  "requestId":"reorg-1",
  "oldAttemptId":"session-01-candidate1",
  "newAttemptId":"session-01-retry-candidate1",
  "newSessionId":"session-01-retry"
}
```

`newSessionId` phải khớp `[A-Za-z0-9_-]{1,40}`; title 1–255 ký tự. Retry cùng request/payload là idempotent; payload khác trả conflict.

## 7. Monitoring HTTP

### GET `/api/v1/monitoring/attempts`

Roster presence cho PROCTOR, chỉ scope được phân công.

```json
{
  "protocolVersion":"v0",
  "traceId":"...",
  "serverTime":"...",
  "attempts":[{
    "attemptId":"session-01-candidate1",
    "candidateUserId":1,
    "candidateDisplayName":"Candidate 1",
    "status":"ONLINE",
    "reason":"HEARTBEAT",
    "revision":3,
    "collectorSessionId":"collector-1",
    "lastSeenAt":"...",
    "timeoutDetectedAt":null
  }]
}
```

Presence chỉ là bằng chứng liên lạc: `UNKNOWN` không đồng nghĩa gian lận.

### GET `/api/v1/monitoring/attempts/{attemptId}/interruptions`

Assigned PROCTOR. Trả lịch sử heartbeat timeout/recovery bền vững:

```json
{"protocolVersion":"v0","traceId":"...","attemptId":"...","interruptions":[{
  "gapId":"...","attemptId":"...","collectorSessionId":"...","reason":"HEARTBEAT_TIMEOUT",
  "lastSeenAt":"...","timeoutDetectedAt":"...","recoveredAt":"..."
}]}
```

### GET `/api/v1/monitoring/attempts/{attemptId}/state`

Assigned PROCTOR. Trả current process state trong RAM:

```json
{
  "attemptId":"...","serverInstanceId":"...","syncEpoch":"...","collectorSessionId":"...",
  "revision":4,"sequence":12,"status":"SYNCED","policyVersion":"process-policy-v1",
  "processes":[{"pid":1234,"processName":"chrome.exe","startInstant":"...","metadataQuality":"COMPLETE"}],
  "receivedAt":"..."
}
```

`status`: `UNSYNCED`, `SYNCED`, `STALE`. Tối đa 128 process. State này tách biệt history event/gap.

### GET `/api/v1/monitoring/attempts/{attemptId}/events`

Assigned PROCTOR. Tối đa 5001 row được đọc để phát hiện vượt giới hạn; nếu history > 5000 API trả conflict thay vì trả thiếu âm thầm.

```json
{"protocolVersion":"v0","traceId":"...","attemptId":"...","events":[{
  "eventId":"...","attemptId":"...","processName":"chrome.exe","metadataQuality":"COMPLETE",
  "policyVersion":"process-policy-v1","observedAt":"...","receivedAt":"...","deliveryStatus":"LIVE"
}]}
```

`deliveryStatus`: `LIVE`, `BUFFERED_OFFLINE`, `PREVIOUS_CONNECTION`, `UNSPECIFIED`.

### GET `/api/v1/monitoring/attempts/{attemptId}/gaps`

Assigned PROCTOR; cùng giới hạn history như events.

```json
{"protocolVersion":"v0","traceId":"...","attemptId":"...","gaps":[{
  "gapId":"...","attemptId":"...","collectorSessionId":"...","reason":"QUEUE_OVERFLOW",
  "droppedCount":5,"firstDroppedAt":"...","lastDroppedAt":"...","receivedAt":"..."
}]}
```

## 8. WebSocket realtime

### Kết nối và envelope

Handshake phải có đúng một header Bearer, không được có query string. Sai auth trả HTTP 401; lỗi backend khi auth trả 503. Server chỉ nhận text JSON, tối đa 65,536 byte mặc định.

```json
{
  "protocolVersion":"v0",
  "type":"HEARTBEAT",
  "messageId":"msg-1",
  "requestId":"msg-1",
  "attemptId":"session-01-candidate1",
  "traceId":"trace-1",
  "payload":{}
}
```

`messageId`, `traceId`, các ID wire phải khớp `[A-Za-z0-9_.:-]{1,128}`. `requestId` có thể null/thiếu và khi đó được gán bằng `messageId`; nếu có thì phải bằng `messageId`. Mỗi message revalidate session DB trước khi parse nghiệp vụ.

### Client → server

#### `HEARTBEAT`

Role bất kỳ có thể gửi heartbeat không attempt. Heartbeat có `attemptId` chỉ CANDIDATE và phải có scope.

```json
{"sentAt":"2026-10-19T10:00:00Z","collectorSessionId":"collector-1"}
```

`collectorSessionId` có thể null khi heartbeat không gắn attempt; bắt buộc khi có attempt.

#### `PROCESS_OBSERVED`

Chỉ CANDIDATE có scope. Persist trước ACK; retry cùng `eventId` và cùng nội dung là idempotent, khác nội dung trả `CONFLICT`.

```json
{
  "eventId":"event-1","collectorSessionId":"collector-1","policyVersion":"process-policy-v1",
  "pid":1234,"processName":"chrome.exe","startInstant":"2026-10-19T09:59:00Z",
  "metadataQuality":"COMPLETE","observedAt":"2026-10-19T10:00:00Z",
  "observationContext":"CONNECTED","observationConnectionId":"server-connection-id"
}
```

`metadataQuality`: `COMPLETE` hoặc `UNREADABLE`; COMPLETE bắt buộc có startInstant. `observationContext`: `CONNECTED`, `OFFLINE`, `UNSPECIFIED`; connection ID chỉ có với CONNECTED.

#### `MONITORING_GAP`

```json
{
  "gapId":"gap-1","collectorSessionId":"collector-1","reason":"QUEUE_OVERFLOW",
  "droppedCount":3,"firstDroppedAt":"...","lastDroppedAt":"..."
}
```

Persist history nhưng không sửa current process/presence state.

#### `MONITORING_SYNC_OPEN`

```json
{"collectorSessionId":"collector-1"}
```

Mở epoch đồng bộ mới trên active socket. Server ACK trả `syncEpoch`.

#### `MONITORING_FULL`

```json
{
  "collectorSessionId":"collector-1","syncEpoch":"...","sequence":1,
  "policyVersion":"process-policy-v1",
  "processes":[{"pid":1234,"processName":"chrome.exe","startInstant":"...","metadataQuality":"COMPLETE"}]
}
```

FULL là baseline mặc định. Epoch mới bắt đầu bằng sequence 1; tập process canonical, tối đa 128.

#### `MONITORING_DELTA`

Chỉ dùng khi ACK heartbeat quảng bá `monitoringDelta:true`.

```json
{
  "collectorSessionId":"collector-1","syncEpoch":"...","baseSequence":1,"sequence":2,
  "policyVersion":"process-policy-v1",
  "added":[],
  "removed":[{"pid":1234,"startInstant":"..."}]
}
```

Yêu cầu `baseSequence == currentSequence` và `sequence == baseSequence + 1`; thiếu base làm server phát `MONITORING_RESYNC` và không đoán state.

#### `MONITORING_SYNC_CLOSE`

```json
{"collectorSessionId":"collector-1","syncEpoch":"..."}
```

#### `LISTENING_EVENT` và `LISTENING_HEARTBEAT`

Chỉ owner CANDIDATE.

```json
{"startId":"...","writerEpoch":2,"event":"STARTED","reason":null}
```

Với `LISTENING_HEARTBEAT`, payload không được có field `event`; server tự ánh xạ thành `HEARTBEAT`. Các validation transition giống HTTP Listening events.

### Server → client

#### `ACK`

```json
{
  "protocolVersion":"v0","type":"ACK","messageId":"server-id","requestId":"msg-1",
  "attemptId":"...","traceId":"trace-1",
  "payload":{"status":"ACCEPTED","acknowledgedType":"HEARTBEAT","connectionId":"...","monitoringDelta":true}
}
```

ACK sync có `syncEpoch`, có thể có `sequence` và object `resync`. ACK Listening có `phase` và `interrupted`. Event/gap có ACK chỉ sau transaction commit.

#### `ERROR`

Payload là `{code,message,retryable}` theo bảng lỗi chung. Nếu session bị thu hồi server gửi `UNAUTHORIZED` rồi đóng socket với policy violation khi có thể.

#### `MONITORING_RESYNC`

```json
{"collectorSessionId":"collector-1","syncEpoch":"...","currentSequence":1,"reason":"BASE_MISSING"}
```

Candidate phải gửi fresh FULL, không tự suy state.

#### Push cho proctor

- `MONITOR_WARNING`: payload là timeline item của process event mới commit.
- `MONITOR_PRESENCE`: payload `PresenceSnapshot`.
- `MONITOR_STATE`: payload `MonitoringStateView`.
- `LISTENING_STATUS`: payload `ListeningStatusResponse`; candidate và assigned proctor có thể nhận tùy scope.

Trước mỗi push có dữ liệu, server kiểm lại token, role và assignment. Push là best-effort; REST/history DB vẫn là nguồn phục hồi.

#### Push cho candidate

`LISTENING_START`:

```json
{
  "startId":"...","sessionId":"session-01","attemptId":"session-01-candidate1",
  "manifestId":"...","writerEpoch":2,"startedAt":"...","deadlineAt":"..."
}
```

Client chỉ phát đúng manifest/writer/start ID, không autoplay lại khi reconnect.

## 9. Cấu hình backend liên quan API

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---:|---|
| `SERVER_ADDRESS` | `0.0.0.0` | Địa chỉ bind HTTP/WS. |
| `SERVER_PORT` | `8080` | Cổng server. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | xem `application.properties` | PostgreSQL. |
| `DB_POOL_SIZE` | `10` | Hikari pool size. |
| `WS_MAX_MESSAGE_BYTES` | `65536` | Giới hạn text/binary frame. |
| `WS_SEND_TIMEOUT_MS` | `5000` | Timeout writer WS. |
| `WS_SEND_BUFFER_BYTES` | `65536` | Buffer gửi WS. |
| `MONITORING_TIMEOUT_MS` | `6000` | Ngưỡng heartbeat presence. |
| `MONITORING_STATE_STALE_MS` | `6000` | Ngưỡng state thành STALE. |
| `MONITORING_STATE_TTL_MS` | `300000` | TTL state RAM. |
| `EXAM_TIMEOUT_SCAN_INTERVAL_MS` | `1000` | Chu kỳ quét hết giờ. |
| `TOEIC_AUDIO_ROOT` | `audio-resources` | Root file audio theo `<examId>/<audioFile>`. |
| `LISTENING_HEARTBEAT_TIMEOUT_MS` | `6000` | Ngưỡng gián đoạn Listening. |

## 10. Những API chưa có

- Chưa có logout/revoke REST public.
- Chưa có CRUD user, catalog exam, sửa/xóa exam/session.
- Chưa có endpoint import/đọc rich content V11, ảnh hoặc provenance.
- Chưa có OpenAPI/Swagger runtime.
- Chưa có API monitoring đa tín hiệu V2 (USB/focus/display); chỉ process monitoring v0 hiện hữu.
