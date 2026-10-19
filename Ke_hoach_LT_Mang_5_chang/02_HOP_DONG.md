# HỢP ĐỒNG TRIỂN KHAI ĐÃ CHỐT

Phiên bản 1.0 · Dùng để viết task/test, chưa phải xác nhận implementation. Các giá trị timeout/giới hạn dưới đây là cấu hình thử nghiệm khởi đầu của kế hoạch; phải ghi lại giá trị thực khi đo.

## 1. Ranh giới thành phần và quyền

| Thành phần | Trách nhiệm | Owner |
|---|---|---|
| JavaFX candidate | Làm bài, answers model, audio; worker mạng và monitoring ngoài UI thread | B; collector do C cung cấp |
| JavaFX proctor | Danh sách thí sinh, trạng thái, tiến độ đã lưu, timeline; thao tác ca theo quyền | B |
| Server application | Auth, quyền, đề/ca/lượt thi, điểm/deadline, HTTP/WS session | A |
| Server monitoring module | Nhận state/event, epoch/sequence, chống trùng, dữ liệu giám sát | C; A cung cấp persistence/auth hook |
| PostgreSQL | Trạng thái authoritative, answers, kết quả, event và khóa theo attempt | A |

Một project desktop, hai giao diện; collector chỉ bật trên role candidate, trong phiên giám sát hợp lệ. Role do server quyết định. Client không được tự khai role để vượt quyền. Mỗi request và WS message ràng buộc người dùng, ca thi và attempt. Giám thị chỉ đọc/điều khiển ca được giao, thí sinh chỉ đọc/ghi lượt được cấp.

Kiểm tra auth và scope **trước mọi response có dữ liệu**, kể cả cache chống trùng, bài đã chốt hoặc event resend. Dùng lỗi không lộ thông tin lượt người khác; người chưa đăng nhập nhận lỗi xác thực. Mở được màn giám thị không chứng minh có quyền server.

HTTP dùng cho login, import/ca thi, đề/audio, lưu/nộp, đọc trạng thái và timeline. WS chuẩn/JSON dùng cho heartbeat, monitoring, start/status/cảnh báo. Xác thực khi mở WS và kiểm tra scope khi xử lý message. Không dùng Socket.IO nói chuyện với raw WebSocket/TCP. Credential không nằm trong log hoặc URL query. Cách credential cụ thể khóa tại T1-A2, không thêm hệ thống SSO/refresh token phức tạp.

Khung dữ liệu có `protocolVersion`, `type`, `messageId`/`requestId`, `attemptId`, dữ liệu nghiệp vụ và correlation ID. `attemptId` được server cấp khi join/chuẩn bị thi để monitoring có thể chạy trước start; không phải cứ có attempt là được lưu đáp án. Server duy trì trạng thái chuẩn bị/đang thi/đã chốt, cùng trạng thái giám sát và cờ gián đoạn Listening riêng.

Giới hạn request, message và queue có cấu hình; JSON sai/type lạ/ID không thuộc ca phải được từ chối mà không làm rớt toàn bộ server. Không log mật khẩu, token, nội dung clipboard hoặc lịch sử duyệt web. Định dạng mã lỗi được khóa ở contract v0, có các nhóm rõ như unauthorized/forbidden, invalid input/state, stale revision/writer/epoch, conflict, expired, retryable server error.

## 2. Lưu đáp án, nộp, timeout

### Mẫu payload mang tính hợp đồng

```json
{
  "requestId": "sample-request-42",
  "attemptId": "sample-attempt-A",
  "writerEpoch": 3,
  "answerRevision": 42,
  "answers": {"q1": "b", "q2": "d"}
}
```

Đây là dữ liệu minh họa, không phải request từng gửi thành công. Autosave gửi **toàn bộ answers**; bỏ lựa chọn phải phản ánh trong toàn bộ map. Server kiểm tra question/option thuộc đúng đề của attempt, không tin điểm do client gửi. Correct answers nằm ở server.

### Thứ tự quyết định phía server

1. Xác thực; kiểm tra quyền truy cập attempt trước khi đọc/trả kết quả có dữ liệu, kể cả idempotency cache.
2. Trong transaction, lấy khóa bản ghi attempt mà save/submit/timeout/takeover cùng dùng.
3. Với thao tác ghi, kiểm tra `writerEpoch` sau khi đã lấy khóa. Phiên cũ không được ghi; không dùng kết quả kiểm tra trước thời gian chờ khóa làm bằng chứng phiên còn hợp lệ.
4. Nếu đã chốt: không sửa. Trả trạng thái/kết quả trong phạm vi được xem, hoặc lỗi phiên theo hợp đồng; dù trả lại kết quả cũ cũng không được bỏ qua auth.
5. Nếu đang thi: chạy một truy vấn riêng lấy **`clock_timestamp()` một lần** sau khóa, lưu `decisionAt`. Chỉ nhận answers mới nếu `decisionAt < deadlineAt`, đúng phase và không bị khóa do Listening gián đoạn.
6. Kiểm tra requestId/revision/nội dung hợp lệ; cập nhật hoặc từ chối theo bảng dưới. Submit hợp lệ lưu bộ answers cuối và chốt/chấm trong cùng transaction. Timeout không nhận answers client mới mà chốt bản đã lưu.
7. Commit trước ACK thành công. Transaction rollback/lỗi DB không được ACK “đã lưu/đã nộp”. Nếu response mất sau commit, retry phải trả trạng thái đúng, không chấm lại hoặc ghi đè.

Tài liệu PostgreSQL §9.9.5 phân biệt giờ thực khi gọi `clock_timestamp()` với thời điểm bắt đầu transaction của `CURRENT_TIMESTAMP`/`now()`. Do đó không lấy giờ trước khi chờ khóa. Nguồn: [PostgreSQL Date/Time Functions](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT), đối chiếu 02/10/2026. Quy tắc chọn thời điểm sau khóa là quyết định của nhóm.

Request gửi trước deadline nhưng kiểm tra sau deadline bị từ chối nhận đáp án mới. Request kiểm tra trước deadline rồi commit sau deadline được chấp nhận nếu transaction thành công. Không có grace period ngầm. Task timeout dù chạy chậm cũng không mở lại khả năng ghi sau deadline; endpoint vẫn kiểm tra thời hạn.

### Revision và retry

| Tình huống | Kết quả phải có |
|---|---|
| Revision mới hơn, payload hợp lệ, còn quyền và thời hạn | Lưu toàn bộ map và savedRevision trong cùng giao dịch |
| Cùng revision, cùng nội dung chuẩn hóa | Không ghi hai lần; trả revision/state đã xác nhận |
| Cùng revision, nội dung khác | CONFLICT, không tự chọn một bản |
| Revision thấp hơn savedRevision | STALE, không ghi đè; submit stale không tự chốt bằng bản cũ |
| Cùng requestId và đúng payload | Idempotent; vẫn kiểm quyền trước trả kết quả |
| Cùng requestId, payload khác | CONFLICT; không tái sử dụng ID để làm thao tác mới |
| Bài đã chốt | Không thay đổi answers/điểm, kể cả retry muộn |

So sánh nội dung theo biểu diễn chuẩn (cùng map có thể khác thứ tự key JSON). Không so chuỗi JSON thô rồi tự coi đổi thứ tự key là đổi đáp án. Request retry giữ nguyên requestId, epoch, revision và answers. Bấm submit khóa chỉnh sửa và chụp payload cố định trước gửi. Khi save đang chờ và submit tới trước, submit mang bộ cuối nên không phụ thuộc save đó hoàn thành; save muộn bị chặn vì bài đã chốt.

### Reconnect và chuyển phiên ghi

| Trường hợp | Xử lý |
|---|---|
| Cùng lần chạy ứng dụng, cùng writer còn hợp lệ | Lấy answers, savedRevision, state/deadline từ server để đối chiếu; không giảm revision cục bộ; retry chưa rõ kết quả giữ payload nguyên vẹn |
| Server đã lưu đúng revision/nội dung đang chờ | Đánh dấu đã lưu; bỏ pending đã được xác nhận |
| Local revision cao hơn server | Giữ local; chỉ retry/gửi bản hợp lệ trong cùng writer, còn thời hạn; không gán lại số 40 rồi tái dùng 41/42 |
| Cùng revision nhưng nội dung khác | Báo conflict; khóa gửi tự động cho tới khi lấy lại trạng thái rõ ràng, không tự merge |
| Server mới hơn local | Nạp state server làm mốc authoritative trước sửa tiếp; không tái dùng revision cũ |
| Khởi động lại hoặc được cấp writer mới | Lấy answers/revision/state server; bỏ pending của writer cũ; không tự gửi local cache cũ |
| Listening gián đoạn | Không dùng chính sách tiếp tục Reading để tự nghe lại hoặc tự resume |

`writerEpoch` tăng khi cấp quyền cho lần ghi mới. Chuyển phiên và mọi cập nhật khóa cùng attempt. Nếu request cũ lấy khóa trước và commit trước khi takeover, nó được xem là xảy ra trước takeover; nếu phiên mới đã kích hoạt xong, request cũ xử lý sau bắt buộc bị từ chối. Đây là ranh giới tuần tự rõ ràng, không hứa hủy một transaction đã commit.

Một kết nối WS mới **không tự động** có nghĩa writer mới: reconnect cùng lần chạy có thể giữ writer còn hợp lệ. `writerEpoch` và `syncEpoch` có mục đích khác nhau.

## 3. Monitoring: trạng thái khác lịch sử

Baseline vận hành là full snapshot. Delta là ứng viên, chưa là cải tiến được chứng minh. Cả hai dùng cùng tập **process liên quan policy được quan sát được**; không lấy baseline gửi mọi process nhưng delta vừa lọc vừa giảm payload.

Collector quét bằng ProcessHandle với polling; mặc định thử 1.000 ms, thay đổi trong thí nghiệm. Quét xong mới xếp lịch theo chính sách được ghi; không chồng vô hạn các vòng quét. Timestamp quan sát của client không trở thành bằng chứng đồng hồ các máy đã đồng bộ.

Khóa process gồm collector identity + PID + thời điểm start nếu đọc được. Nếu thiếu start/name, đánh dấu chất lượng dữ liệu và giới hạn phân biệt PID tái sử dụng. Không tuyên bố nhận diện hoàn hảo; benchmark có process điều khiển được và ghi trường nào quan sát được. Process không đọc được metadata không được diễn giải thành “không vi phạm”.

| Trường | Trách nhiệm |
|---|---|
| attemptId | Lượt thi/phiên chuẩn bị được phép giám sát |
| collectorSessionId | Một lần khởi chạy collector, khác attempt và khác writer |
| syncEpoch | Thế hệ đồng bộ server cấp, đổi khi kết nối giám sát lại |
| sequence | Thứ tự state trong epoch |
| baseSequence | State mà delta dùng làm gốc |
| messageId | Nhận diện state message retry |
| eventId | Nhận diện một sự kiện lịch sử ổn định khi retry |
| policyVersion | Chính sách lọc/cảnh báo để replay và so sánh công bằng |

### Quy tắc reducer state

1. Xác thực collector/attempt và epoch đang active trước áp dụng; epoch đầu nhận full snapshot mới, ví dụ sequence=1. Chưa có full hợp lệ thì UNSYNCED.
2. Delta chỉ hợp lệ khi `baseSequence == currentSequence` và `sequence == currentSequence + 1`.
3. Message đã áp dụng với cùng ID/sequence/nội dung được ACK lại; không sửa state hai lần. Cùng sequence/ID khác nội dung là lỗi. Message cũ không nằm cửa sổ dedup chỉ được bỏ/stale, không được áp lại.
4. Thiếu chuỗi/đảo thứ tự làm mất base: đánh dấu UNSYNCED, giữ last-known state với nhãn stale, yêu cầu full. Không dựng state mới bằng phỏng đoán delta bị thiếu.
5. Full snapshot ở sequence cao hơn trong epoch hợp lệ có thể thay thế state làm điểm đối soát; không lùi sequence. ACK state sau khi trạng thái authoritative tương ứng đã được chấp nhận; ghi rõ durability của state trong implementation.
6. Sau reconnect, server cấp syncEpoch mới; client bỏ delta pending của epoch cũ, quét full mới, chờ ACK rồi mới gửi delta. Server reject epoch cũ. Server restart làm session cũ không còn hợp lệ; client phải resync, không tiếp tục state đoán từ RAM đã mất.

Quyết định thứ tự “publish epoch mới, accept full mới, vô hiệu epoch cũ” phải được tuần tự hóa theo attempt/collector trong module monitoring. Đây là task reducer, không xây một distributed coordinator.

### Luồng lịch sử event

Cảnh báo được sinh theo **cùng một quy tắc quan sát** cho full và delta: một event cho một lần process bị quan sát là xuất hiện trong policy; không tạo thêm ở mỗi poll chỉ vì vẫn còn chạy. Việc một process biến mất rồi xuất hiện giữa hai lần poll có thể không được biết. Thông báo cho người xem phải nói “quan sát thấy”, không tự khẳng định thời điểm mở chính xác.

Event có eventId ổn định; server kiểm tra quyền/association lịch sử, lưu chống trùng, commit rồi ACK và báo giám thị. Event cũ được retry trong phạm vi được cấp phép của collector/lượt đó, có nhãn đến muộn; không dùng epoch cũ như quyền cập nhật state. Không cho người dùng khác gắn event vào attempt không thuộc quyền. Giám thị reconnect đọc timeline từ DB để không phụ thuộc nhận đủ push.

Client có queue RAM giới hạn, retry có giới hạn/backoff; không xây hàng đợi bền vững trên đĩa trong MVP. Giá trị khởi đầu để thử: heartbeat 2 giây, server timeout 6 giây; queue tối đa 500 event. Đây là tham số cấu hình phải kiểm chứng, không là cam kết phát hiện đúng trong 6 giây mọi môi trường. Khi quá tải ghi droppedCount, khoảng thời gian biết được và đánh dấu gap lúc có thể gửi.

Client bị kill không thể tự gửi MONITORING_GAP. Server lưu lastSeenAt và timeoutDetectedAt, đánh dấu **không còn xác nhận được monitoring** sau thiếu heartbeat. Không biết chính xác monitoring dừng lúc nào; không suy diễn vi phạm từ disconnect. Snapshot mới phục hồi state hiện tại nhưng không phục hồi mọi sự kiện trong khoảng trống. Gap đã ghi không bị xóa khi trở lại ONLINE.

## 4. Listening trong MVP

| Tình huống | Chính sách |
|---|---|
| Chuẩn bị | Tải đầy đủ, đối chiếu manifest/checksum; test audio mẫu và thiết bị phát |
| File thiếu/hỏng hoặc test phát lỗi | Không READY; hiển thị nguyên nhân, cho tải lại trước thi |
| Bắt đầu | Server cấp lệnh start có ID và thời hạn; client phát cục bộ, ghi playbackStarted; message start trùng không nghe lại |
| Mất kết nối được phát hiện/lỗi audio | Dừng audio, khóa thao tác, đánh dấu gián đoạn; giữ log; không dùng timestamp client để xin thêm giờ |
| Reconnect hoặc mở app lại | Không tự phát từ đầu và không tự resume; giữ cờ gián đoạn phía server khi đã ghi nhận |
| Client bị kill | Server heartbeat timeout; lúc client quay lại không được bypass chính sách để nghe lại |
| Xử lý tiếp | Giám thị đánh dấu cần tổ chức lại; nếu tổ chức lại thì cấp attempt mới; không sửa deadline hoặc reset answers của attempt cũ |
| Lượt cũ hết giờ | Chốt answers server đã lưu, kèm trạng thái gián đoạn để không trình bày như kết quả thi bình thường |

Khóa UI là phản hồi người dùng; server vẫn phải kiểm phase/writer/deadline/cờ gián đoạn khi ghi. Trong khoảng chưa phát hiện mất mạng, hệ thống không biết chắc trạng thái client; giới hạn heartbeat phải được công khai. Không hứa đồng bộ sample audio tuyệt đối giữa các máy hoặc phục hồi sau mất điện.

## 5. Các điều bất biến để review

- Không ai đọc/sửa bài người khác qua bất kỳ nhánh retry/final/cache nào.
- Không ACK save/submit/event đã lưu khi transaction chưa commit thành công.
- Không ghi đáp án sau chốt; không ghi theo writer đã bị thay thế sau takeover hoàn tất.
- Không dùng `now()` như giờ thực sau lock wait; không tái dùng revision cho payload khác.
- Delta không làm lùi state hoặc biến gap thành lịch sử đã quan sát đầy đủ.
- Full và delta khác cách truyền state; không khác tập process/quy tắc event/độ tin cậy.
- Listening gián đoạn không được tự nghe lại; monitoring giám thị không chạy nhầm trên máy proctor.

Mỗi thay đổi sau bản này phải gắn một test và reviewer. Không thay đổi stack hoặc bổ sung chức năng để né một test chưa qua.

## Phụ lục V2 — phạm vi triển khai được nhóm duyệt (19/10/2026)

**Nguồn quyết định:** người đại diện nhóm xác nhận qua cuộc trao đổi ngày 19/10 rằng cả nhóm đã phê duyệt Plan V2 tối 18/10/2026. Đây là ghi nhận **phê duyệt phạm vi** do người dùng cung cấp, không phải biên bản có chữ ký A/B/C hoặc nghiệm thu thực nghiệm. [Điểm vào](../../PLAN_V2_START_HERE.md) · [hồ sơ](../../docs/plan-v2/ADOPTION_STATUS_20261019.md) · [kickoff](../../docs/plan-v2/V2_EXECUTION_KICKOFF_20261019.md).

**Bổ sung phạm vi**: (1) một đề 200 câu theo 7 Part, đúng số câu `6/25/39/30/30/16/54`, có âm thanh/hình/passage/nhóm câu thật hoặc dữ liệu hợp pháp được gắn nhãn đúng; (2) quan sát **USB mass-storage** hiện diện/cắm/rút, focus/minimize/fullscreen và thay đổi display trên **client thí sinh được giao ca, đã được thông báo**; (3) timeline đa tín hiệu phía giám thị, phân quyền/ACK/persistence/reconnect; (4) nghiệm thu Windows/LAN/USB/media có evidence thực tế. **Không** đọc dữ liệu USB, chặn/điều khiển thiết bị, ghi phím, chụp màn hình, thu âm webcam/microphone hoặc khẳng định gian lận tự động.

**Giữ nguyên các điều kiện correctness đã chốt:** auth/scope trước trả dữ liệu/cache, commit DB trước success ACK, writerEpoch/answerRevision/deadline theo contract hiện hành, Listening không tự phát lại, FULL baseline, DELTA chỉ opt-in sau correctness gate, process state RAM tách event history DB. Phase 3 hiện `CODE_READY_STAGE3_ACCEPTANCE_PENDING`; không hợp thức hóa physical LAN, manual hearing hoặc MSI máy thứ hai chưa chạy.

**Checkpoint kỹ thuật trước khi sửa interface/schema:** `ADR-V2-02` (ordered content blocks/media access/migration và quyền dữ liệu); `ADR-V2-03` (proctor START / QD-21 deadline: giữ nguyên semantics hiện tại cho đến khi A/B chọn và có regression); `ADR-V2-04` (typed event v0 compatibility, ID/ACK/dedup/size/correlation); `ADR-V2-05` (PnP so với CIM/WMI, privilege/physical coverage); `ADR-V2-06` (parser mặc định Java); `ADR-V2-08` (FULL/DELTA gate). Quyết định cấp giao diện do owner/reviewer ghi evidence, **không coi phê duyệt kế hoạch là phê duyệt DTO hay migration cụ thể**. Tài liệu `docs/PROTOCOL.md` chỉ đổi đồng thời với implementation đã review. Không tự thay đổi `protocolVersion=v0`.

**Mốc V2:** gate DELTA 20/10, mục tiêu đóng băng tính năng sau 23/10, bàn giao nội bộ 30/10; hạn nộp chính thức 31/10/2026 23:59 (ICT). Kế hoạch cũ giữ nguyên để truy xuất lịch sử; phụ lục này chỉ bổ sung phạm vi V2, không sửa kết quả kiểm thử cũ.
