# T3-A1 — Audio manifest, tải tài nguyên và READY

Backend phục vụ metadata và audio cho đúng chủ lượt thi, ghi READY vào PostgreSQL, và chặn start Listening khi chưa đủ điều kiện. Client T3-B1 đã được hoàn tác theo yêu cầu; giao diện tải/checksum/thử loa sẽ dùng contract dưới đây khi triển khai lại.

## Cấp tài nguyên cho server

Import đề và tạo ca bằng API T2-A1. Mỗi câu `LISTENING` phải có `audioFile` là tên một file MP3, WAV hoặc M4A. Người tổ chức đặt file vào `<TOEIC_AUDIO_ROOT>/<examId>/<audioFile>`; mặc định `audio-resources` tính từ working directory server. Tài nguyên nằm ngoài JAR server. `scripts/start-monitoring-server.ps1` đọc `TOEIC_AUDIO_ROOT` từ `.env`; Maven/Spring cũng nhận biến môi trường này hoặc `--toeic.audio.root=<path>`.

Ví dụ đề `EXAM-01` tham chiếu `part1.wav` thì đặt file tại `audio-resources/EXAM-01/part1.wav`. Thư mục gốc và tên file có thể có dấu tiếng Việt, khoảng trắng. `examId`/tên file phải là một thành phần đường dẫn, không có `/`, `\`, `:`, tên `.`/`..`, ký tự điều khiển hoặc dấu chấm/khoảng trắng cuối tên. Không dùng symbolic link hoặc junction tới ngoài thư mục tài nguyên.

Server không nhận đường dẫn từ client để đọc file. Server chỉ phục vụ file `audioFile` đã tham chiếu trong câu Listening của đúng đề. File JSON/ZIP/key đáp án hoặc file không được tham chiếu không được tải. Manifest và response tải không chứa câu hỏi hoặc đáp án. Giám thị không dùng endpoint tải của thí sinh.

Giới hạn: 100 file, 64 MiB/file, tổng 256 MiB. File rỗng, thiếu, sai tên hoặc vượt giới hạn khiến manifest/READY/start thất bại với 409 `INVALID_STATE`. Khi nhiều câu dùng chung audio, manifest chỉ có một file. Server kiểm lại byte và checksum khi tạo manifest và tải file; response binary có `Cache-Control: no-store`.

Không sửa tài nguyên khi đang chuẩn bị hoặc chạy ca. Trước start, thay đổi một byte sẽ đổi `manifestId` và làm READY cũ không còn hợp lệ. Sau start, server từ chối manifest đã thay đổi với 409 `CONFLICT`. Việc sửa file trực tiếp trên đĩa không phải API giao dịch; người tổ chức phải giữ file ổn định trong suốt ca.

## Contract cho B

Tất cả endpoint dùng `Authorization: Bearer <token>`. Server URL vẫn là cấu hình chung của app. `manifestId` là SHA-256 của sessionId, examId và danh sách file đã sắp xếp cùng tên/kích thước/checksum; hai thí sinh cùng ca nhận cùng manifestId.

1. Khi mở app mới, dùng takeover T2-A4 và lấy `writerEpoch` mới. Mỗi writer mới phải xác nhận lại READY.
2. Gọi `POST /api/v1/attempts/{attemptId}/audio/ready` với `ready=false` trước khi bắt đầu lại việc chuẩn bị. Việc này xóa READY cũ của writer hiện tại trong bước chuẩn bị.
3. Lấy `GET /api/v1/attempts/{attemptId}/audio/manifest`.
4. Tải từng file bằng `GET /api/v1/attempts/{attemptId}/audio/files/{fileName}?manifestId=<manifestId>`. Encode tên file thành URI path segment, không truyền raw đường dẫn local.
5. Kiểm kích thước và SHA-256 từng file ở worker; thử loa bằng audio mẫu riêng, rồi xác nhận nghe được.
6. Báo `ready=true` với toàn bộ checksum lowercase hex và `speakerTestPassed=true`. Chỉ hiển thị server READY khi nhận response thành công. Thiếu/hỏng file hoặc lỗi thử loa thì báo `ready=false`; không gửi READY true. Trước playback phải kiểm lại file local.

Manifest mẫu (giá trị hash MOCK, schema thật):

```json
{
  "protocolVersion": "v0",
  "attemptId": "SESSION-01-candidate1",
  "sessionId": "SESSION-01",
  "manifestId": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "files": [{
    "fileName": "part1.wav",
    "sizeBytes": 1234,
    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  }]
}
```

READY mẫu (các hash phải lấy từ manifest và kiểm trên file thật):

```json
{
  "requestId": "ready-001",
  "attemptId": "SESSION-01-candidate1",
  "writerEpoch": 2,
  "ready": true,
  "manifestId": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "checksums": {"part1.wav": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
  "speakerTestPassed": true
}
```

`ready=false` chỉ cần requestId, attemptId, writerEpoch và ready. Response false có manifestId/readyAt null. Response true có readyAt do DB cấp, sau COMMIT. Lặp lại READY hợp lệ cho cùng writer/manifest giữ readyAt; payload sai bị từ chối và không thay dữ liệu đã ghi. Server so khớp chính xác tập file/checksum, kể cả file thừa hoặc thiếu; checksum không khớp trả 409 `CONFLICT`, thiếu xác nhận thử loa trả 400 `INVALID_INPUT`. Sai writer trả 409 `STALE`. Lượt không ACTIVE/hết deadline bị từ chối. READY chỉ thay đổi trước start.

READY là báo cáo của client. Server không quan sát được file local hoặc xác minh người dùng thực sự nghe loa; B chịu trách nhiệm kiểm checksum và xử lý lỗi media trước khi gửi.

## Gate start và bàn giao A2

Giám thị được gán trong `exam_session_proctors` xem `GET /api/v1/sessions/{sessionId}/audio/readiness`, rồi gọi `POST /api/v1/sessions/{sessionId}/listening/start` với requestId và manifestId đang thấy.

Start lấy khóa ca rồi toàn bộ lượt theo thứ tự attemptId. Sau khi khóa và kiểm tài nguyên, server dùng `clock_timestamp()` để quyết định. Ca phải SCHEDULED, có ít nhất một thí sinh, và **mọi lượt trong ca** phải ACTIVE, chưa hết deadline, tài khoản enabled, đúng đề, có READY khớp manifest và writerEpoch hiện tại. Không bỏ qua lượt chưa READY hoặc đã chốt.

Start hợp lệ ghi `IN_PROGRESS`, `listening_start_id`, `listening_manifest_id` và startedAt trong cùng transaction. Retry/concurrent start trả cùng startId và startedAt; không tạo lần start thứ hai. Không thay answers, savedRevision, writerEpoch hoặc deadline của bất kỳ lượt nào. Ca CLOSED không start được; lỗi COMMIT rollback và trả lỗi thay cho success.

T3-A2 sẽ dùng startId bền vững này để gửi lệnh WS, thêm deadline/phase trong lệnh, ghi playbackStarted, timeout/gián đoạn và tổ chức lại. T3-A1 chưa phát lệnh WS hoặc bật playback, chưa thực hiện chính sách gián đoạn. Không dùng response HTTP để tự phát lại sau reconnect.

## Chạy kiểm thử

```powershell
mvn package
docker compose up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3a1.ps1 -JavaHome <JDK21>
```

Smoke đọc DB/seed từ `.env`, không in credential, chạy production HTTP/Flyway/JDBC trên schema `t3a1_<UUID>` và regression bài thi trên schema riêng, rồi dọn tài nguyên sở hữu. WAV test được sinh trong `server/target` với thư mục có dấu/khoảng trắng. Docker phải đang chạy. Script dùng Maven offline với cache hiện có; chạy Maven bình thường trước nếu chưa có dependency.
