# T3-A2 · Start, gián đoạn Listening và tổ chức lại

Backend dùng gate READY của [T3-A1](AUDIO_RESOURCES.md). Migration V10 lưu trạng thái Listening riêng trên lượt thi; cờ gián đoạn không đổi state ACTIVE thành INTERRUPTED, nên Reading và lịch sử vẫn dùng quyền hiện có. B đã thêm [chuẩn bị audio T3-B1](AUDIO_CLIENT_PREPARATION.md) và [phát theo start T3-B2](LISTENING_CLIENT_PLAYBACK.md) trên các branch phụ thuộc ngày 14/10/2026. Chính sách gián đoạn/reconnect T3-B3 chưa triển khai đầy đủ.

## Start và lệnh WebSocket

Giám thị được gán ca gọi `POST /api/v1/sessions/{sessionId}/listening/start` với requestId/manifestId. Mọi lượt phải READY đúng manifest/writer, ACTIVE và chưa hết giờ. Server ghi một startId cho ca và metadata Listening cho mỗi lượt trong cùng transaction. Deadline null được đặt bằng startedAt + durationSeconds; deadline đã có không thay đổi. Retry/concurrent start giữ nguyên startId, startedAt và deadline.

Sau COMMIT, server gửi `LISTENING_START` trên `/ws/v1/realtime` tới các kết nối của đúng chủ lượt, kiểm lại token và scope. Envelope v0 có messageId = startId, requestId null, attemptId và traceId. Payload:

```json
{
  "startId": "START-01",
  "sessionId": "SESSION-01",
  "attemptId": "SESSION-01-candidate1",
  "manifestId": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "writerEpoch": 2,
  "startedAt": "2026-10-13T10:00:00Z",
  "deadlineAt": "2026-10-13T12:00:00Z"
}
```

Ví dụ dùng ID/hash minh họa. startedAt là mốc server cấp quyền, không phải thời điểm loa thực sự bắt đầu. Lỗi COMMIT không phát lệnh. Socket delivery là best effort; giám thị có thể retry để gửi cùng ID cho lượt còn ARMED, chưa timeout, đúng writer. Sau STARTED/COMPLETED/gián đoạn, server không phát lại lệnh cho lượt đó. Reconnect không tự nhận lệnh start.

Client T3-B2 deduplicate theo attempt/writer/startId, kiểm attempt/manifest/writer và SHA-256 local trước khi phát; journal local giữ lệnh đã dành/phát để mở lại không coi ID cũ là quyền nghe lại. Chỉ `LISTENING_START` hợp lệ mới tạo playlist; HTTP start/status không tự phát. Mở app mới phải dùng takeover; nếu playback chưa hoàn tất thì server ghi WRITER_TAKEOVER và khóa Listening. `RealtimeClient` kiểm chặt và chuyển tiếp `LISTENING_START`/`LISTENING_STATUS`; event playback dùng HTTP production để nhận response trạng thái sau COMMIT.

## Báo playback và heartbeat

Thí sinh dùng `POST /api/v1/attempts/{attemptId}/listening/events` với:

```json
{"requestId":"event-01","startId":"START-01","writerEpoch":2,"event":"INTERRUPTED","reason":"MEDIA_ERROR"}
```

Các event: STARTED, COMPLETED, INTERRUPTED, HEARTBEAT. reason chỉ dùng cho INTERRUPTED, nhận NETWORK_LOST hoặc MEDIA_ERROR. Server kiểm owner, ACTIVE, deadline, startId và writerEpoch cấp start dưới khóa hàng. Không nhận timestamp client. STARTED ghi playbackStartedAt một lần; COMPLETED cần STARTED, ghi playbackCompletedAt một lần; retry không tạo playback mới. Hoàn tất không bị heartbeat timeout của Listening nữa.

WS tương đương dùng envelope v0, messageId=requestId, traceId và attemptId. `LISTENING_EVENT` có payload startId/writerEpoch/event/reason; `LISTENING_HEARTBEAT` chỉ có startId/writerEpoch. Khuyến nghị gửi heartbeat riêng mỗi 2 giây từ lúc nhận start cho đến COMPLETED/gián đoạn. Heartbeat monitoring hiện có không thay heartbeat Listening và không cấp quyền resume. Writer phải là số nguyên chính xác.

ACK chỉ gửi sau COMMIT, payload có status=ACCEPTED, acknowledgedType, phase, interrupted. HTTP trả ListeningStatusResponse. Sau gián đoạn, event trả phase=INTERRUPTED, không cấp lại quyền phát. B phải kiểm phase/interrupted, không chỉ HTTP 200 hoặc status=ACCEPTED. Token/quyền bị thu hồi vẫn trả lỗi; sai writer trả STALE, sai startId trả CONFLICT, payload sai trả INVALID_INPUT. Lỗi COMMIT trả lỗi retryable, không ACK thành công.

## Trạng thái và gián đoạn

`GET /api/v1/attempts/{attemptId}/listening/status` cho owner hoặc giám thị được gán, gồm cả lượt SUBMITTED/TIMED_OUT. Response có protocolVersion, requestId null, attemptId/sessionId/startId/manifestId, writerEpoch hiện tại, phase, deadlineAt, playbackStartedAt/playbackCompletedAt, interrupted/interruptedAt/interruptionReason. Các phase:

| Phase | Ý nghĩa |
|---|---|
| NOT_STARTED | Chưa có quyền start |
| ARMED | Có start, chưa nhận STARTED |
| PLAYING | Đã nhận STARTED, chưa hoàn tất |
| COMPLETED | Đã nhận COMPLETED |
| INTERRUPTED | Cờ gián đoạn đã được ghi; không resume |

Status GET chỉ đọc, không phát audio, tạo deadline hoặc hồi phục Listening. Sau COMMIT của event/worker, server push `LISTENING_STATUS` best effort cho owner và giám thị được gán; payload là response status, messageId mới, requestId null. GET là đường đọc lại khi mất push.

Worker riêng quét DB mỗi giây, tối đa 100 lượt/lần, SKIP LOCKED. ARMED/PLAYING thiếu heartbeat 6000ms bị ghi HEARTBEAT_TIMEOUT; cấu hình `LISTENING_HEARTBEAT_TIMEOUT_MS` (ít nhất 1000). Worker dùng clock_timestamp; heartbeat đến muộn kiểm lại sau khóa và ghi gián đoạn thay vì gia hạn lastSeen. Save/submit cũng kiểm khoảng im lặng dưới khóa, nên không sửa được Listening trong khoảng chờ worker. Restart server ghi SERVER_RESTART cho playback đang dở; V10 cũng bảo vệ các ca đã start từ V9.

Cờ đầu tiên giữ nguyên thời điểm/lý do khi retry. Lý do server bổ sung WRITER_TAKEOVER, SERVER_RESTART, HEARTBEAT_TIMEOUT. Worker và Listening heartbeat độc lập với presence/full/delta của monitoring. Không cam kết phát hiện chính xác trong 6 giây khi DB hoặc mạng chậm; client vẫn phải dừng/khóa ngay khi phát hiện mất kết nối/lỗi media.

## Lưu/nộp bài và tổ chức lại

Khi gián đoạn, save/submit từ chối thêm, sửa hoặc xóa bất kỳ đáp án LISTENING so với answers đã commit, trả 409 INVALID_STATE. Reading vẫn được sửa trong deadline với writer hợp lệ. Payload full answers phải giữ nguyên phần Listening. Retry một request đã commit vẫn trả kết quả idempotent; không tạo ghi mới. Status bài thi và SubmitExamResponse có boolean `listeningInterrupted`, kể cả sau timeout/chấm điểm; timestamp/lý do chi tiết ở Listening status.

Chỉ giám thị được gán lượt cũ gọi `POST /api/v1/attempts/{oldAttemptId}/listening/reorganize`:

```json
{"requestId":"reorganize-01","newSessionId":"SESSION-RETRY-01","title":"Thi Listening tổ chức lại"}
```

Lượt cũ phải có cờ gián đoạn. Server tạo ca SCHEDULED và attempt mới cho cùng thí sinh/đề/thời lượng, sao chép danh sách giám thị; answers={}, revision=0, writerEpoch=1, deadline/start/READY chưa có. Response có protocolVersion, requestId, oldAttemptId, newAttemptId, newSessionId. newSessionId nhận 1–40 ký tự ASCII chữ/số/underscore/hyphen, title 1–255 ký tự; requestId 1–128 ký tự. Một lượt cũ chỉ có một replacement trực tiếp. Retry cùng requestId/payload giữ cùng replacement; payload khác trả 409 CONFLICT. Có thể tổ chức tiếp từ replacement nếu chính replacement bị gián đoạn.

Không đổi answers, revision, writerEpoch, deadline hoặc lịch sử lượt cũ. Đến deadline, scheduler exam hiện có chấm answers đã commit, giữ cờ gián đoạn. Lượt mới phải lấy manifest riêng của ca mới và READY lại trước start; không kế thừa audio authorization của lượt cũ.

## Kiểm thử

```powershell
docker compose up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3a2.ps1 -JavaHome <JDK21>
# Toàn bộ build/package, gồm các test PostgreSQL:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3a2.ps1 -JavaHome <JDK21> -FullBuild
```

Script đọc .env, dùng cache Maven offline, không in credential; mỗi suite dùng schema riêng và audio tạm trong server/target, dọn tài nguyên sở hữu. Suite mới kiểm HTTP/WS scope, retry/start, deferred COMMIT, LT04 save/submit/reorganization/timeout, heartbeat, takeover, restart Spring thật và nâng cấp V9→V10. [Evidence T3-A2](../evidence/t3-a2/2026-10-13-verification.md). GUI/audio thật/LAN và human C review chưa chạy.
