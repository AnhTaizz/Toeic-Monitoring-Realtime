# Log đo C4 — định nghĩa, schema và cách chạy

Phạm vi: chỉ JSON text WebSocket của monitoring session. Không đo HTTP login/roster/audio, handshake, WebSocket framing, TCP/IP, TLS hoặc retransmission mạng. Đây là application message bytes, không phải tổng băng thông hay chi phí toàn kỳ thi.

`bytesUtf8 = serializedMessage.getBytes(StandardCharsets.UTF_8).length`: toàn bộ JSON envelope + payload, từ đúng chuỗi gửi/nhận. Không dùng String.length, dung lượng object, payload riêng hay kích thước file log. Retry cùng eventId là lần gửi mới và tính lại toàn bộ byte.

TX có ATTEMPTED, WRITE_COMPLETED, WRITE_FAILED riêng. WRITE_COMPLETED chỉ là API write trả thành công; không chứng minh bên nhận đã thấy hay DB đã commit. Client đo tại sendText; server đo tại delegate thật nằm bên trong ConcurrentWebSocketSessionDecorator, không lấy lúc decorator mới enqueue làm write-completed. RX ghép text xong mới đo một lần; Unicode qua fragment phải giữ nguyên. Binary/oversized không có text hoàn chỉnh ghi UNMEASURED và bytes null, không đoán số byte.

ACK nghiệp vụ là record BUSINESS_ACK riêng sau client validate pending correlation; bytes null vì ACK JSON đã được tính ở MESSAGE/RX. Một ACK heartbeat không phải commit event. Không thêm bộ đếm ở delivery/service vì transport đã có một điểm ghi đo chung.

Summary báo từng endpoint/clock domain/direction/type/outcome. Tổng gửi ứng dụng theo lần thử = CLIENT TX ATTEMPTED + SERVER TX ATTEMPTED. Tổng write-completed báo riêng. Không cộng RX vào TX để gọi là byte truyền duy nhất; không cộng cả ATTEMPTED và COMPLETED vì hai record mô tả cùng lần gửi.

recordedAt là UTC wall clock; elapsedNanos là nanoTime trừ đầu recorder, chỉ so trong cùng clockDomain. runId nối file, không đồng bộ đồng hồ. sequence lấy từ wire khi hợp lệ, hiện protocol chưa gửi sequence nên null; recordIndex chỉ là thứ tự local log để phát hiện mất record, không phải sequence monitoring.

Định nghĩa trên được ghi trước khi triển khai. C4 không chạy E1/E2, không triển khai full/delta và không kết luận hiệu năng.

## Schema JSON Lines v1

JSON Lines là file mà mỗi dòng chứa một object JSON hoàn chỉnh, UTF-8, xuống dòng LF. `schemaVersion` luôn là `monitoring-measurement-v1`. Mỗi recorder có một file và clockDomain UUID riêng; một demo có cùng runId. Hai client trong cùng JVM vẫn có hai clockDomain vì mốc bắt đầu recorder khác nhau.

| Field | Ý nghĩa/quy tắc |
|---|---|
| schemaVersion, runId, clockDomain, endpoint | Phiên bản, nhóm run, miền đồng hồ riêng, CLIENT hoặc SERVER. |
| recordType | METADATA đầu file; MESSAGE cho byte; BUSINESS_ACK xác nhận đã qua correlation B2; OBSERVATION cho binary/oversized; FINAL cuối file. |
| recordIndex | Số tăng dần của record dữ liệu, bắt đầu1; khoảng nhảy cho biết có record không ghi được. Không phải wire sequence. METADATA/FINAL không có field này. |
| direction, messageType, outcome | TX/RX, một trong HEARTBEAT/PROCESS_OBSERVED/MONITORING_GAP/ACK/ERROR/MONITOR_WARNING/MONITOR_PRESENCE/UNKNOWN/INVALID; outcome theo bảng dưới. Không áp dụng cho control record. |
| messageId, requestId, traceId, attemptId | ID từ envelope khi là chuỗi hợp lệ; nullable. |
| eventId, gapId, collectorSessionId | ID từ payload khi là chuỗi hợp lệ; nullable. |
| sequence | Số nguyên không âm trong payload khi thực sự có, vừa long; còn lại null. v0 hiện không gửi sequence. |
| bytesUtf8 | MESSAGE: số byte của toàn chuỗi JSON. BUSINESS_ACK/OBSERVATION: null, không cộng byte lần nữa/không đoán. |
| recordedAt | Instant UTC với đuôi Z, dùng đọc giờ và nối evidence. |
| elapsedNanos | System.nanoTime trừ mốc đầu recorder; chỉ so cùng clockDomain, không suy ra độ trễ giữa máy. |
| settings | Chỉ METADATA: sourceSha, OS/JDK, nhãn workload/fault, queue/flush và setting transport thực tế. Cấu hình collector/delivery/demo bổ sung trong run-metadata.json. |
| status | Chỉ FINAL: danh sách counter key/messages/bytesUtf8 và trạng thái recorder, xem bên dưới. |

| Record/direction | outcome | Có cộng bytes? |
|---|---|---|
| MESSAGE/TX | ATTEMPTED | Có, theo lần thử. |
| MESSAGE/TX | WRITE_COMPLETED hoặc WRITE_FAILED | Có, báo riêng từng kết quả; không cộng vào ATTEMPTED. |
| MESSAGE/RX | RECEIVED | Có, kể cả JSON malformed nhận đầy đủ. |
| BUSINESS_ACK/RX | ACCEPTED | Không. B2 đã validate pending correlation; C3 vẫn tự quyết định bỏ event/gap khỏi queue. Demo cố tình chặn observer C3 có thể có record này trước retry. |
| OBSERVATION/RX | UNMEASURED | Không. Không có text đầy đủ để đo. |

Counter key chỉ gồm recordType/direction/type/outcome, số loại hữu hạn; không lập map theo eventId. Counters được khóa ngắn cùng enqueue, snapshot sao chép dữ liệu, không ghi file khi giữ khóa producer. `logDroppedCount` đếm record mới bị bỏ khi queue đầy hoặc writer đã lỗi. `logUnwrittenCount` đếm record đã nhận vào queue nhưng chưa flush thành công ra Writer. `pendingWrites` đếm ticket TX chưa có kết quả. FINAL còn có writerFailed/flushTimedOut/closed. BUSINESS_ACK là số lần B2 chấp nhận tương quan ACK, không phải tổng event duy nhất được commit.

ID chỉ giữ khi match `[A-Za-z0-9_.:-]{1,128}`. Envelope không đúng v0, thiếu ID bắt buộc/object payload hoặc không parse được → INVALID, bỏ mọi ID. Type lạ → UNKNOWN, không giữ tên type input. Đây là phân loại phục vụ đo, không thay validation nghiệp vụ. Không ghi payload, token, password, tên user OS, processName/PID/path/command line hoặc cause exception. runId cấu hình chỉ cho `[A-Za-z0-9_.-]{1,80}` để không chèn đường dẫn vào filename.

## Vị trí đo và giới hạn

- `protocol/.../measurement/MessageIdentity.read`: chỉ đọc metadata đã lọc; Gson hiện có được dùng lại trong protocol, không thêm framework.
- `MessageMeasurements.attempt/Tx.completed/Tx.failed/received/businessAck`: đếm và enqueue; một writer daemon ghi file; snapshot/finished cho harness kiểm đóng.
- B: `RealtimeClient.write` dùng chính chuỗi Gson đã serialize cho sendText và đo; kết quả future ghi outcome. Listener.onText chỉ đo khi `last=true` và nối xong Unicode; nhận quá lớn/binary giữ rejection hiện có.
- A: `RealtimeWebSocketHandler.handleTextMessage` đo full text trước validation; `RealtimeSessionRegistry.MeasuredSession` đặt bên trong ConcurrentWebSocketSessionDecorator. Message chờ trong send buffer chưa được coi là write; khi delegate thực sự gọi sendMessage mới có ticket/counter. ACK/ERROR/warning/presence dùng cùng điểm này.

TRANSPORT write success vẫn không thay ACK sau COMMIT của EventService/GapService/PresenceService. Log không thay DB và không dùng để cấp quyền. Counter server gộp các socket trong một registry; client counter theo từng adapter. JSON được tạo trước khi gửi; parse metadata và tạo UTF-8 byte array thêm chi phí khi bật. Có lock ngắn, tạo record, thêm queue; writer serialize/flush mỗi record. Chưa đo CPU/memory/latency hoặc overhead recorder. Vì có chi phí này, E1/E2 phải ghi bật/tắt và cấu hình giống nhau giữa phương án so sánh.

## Cấu hình recorder

Các tham số là Java system properties (`-D...`) ở cả server và client, không sửa `.env` hay wire message:

| Property toeic.measurement.* | Mặc định | Giới hạn |
|---|---|---|
| enabled | false | Tắt: không writer, file I/O, parsing đo hoặc counter; vẫn có ticket no-op nhỏ trên đường send. |
| runId | UUID mới | Muốn nối file hai endpoint phải truyền cùng runId. |
| directory | logs/monitoring | Runtime folder ignored; không commit log lớn. |
| queueCapacity | 1024 | 1..100000 records; DROP_NEW, không chặn producer chờ file. |
| flushMillis | 2000 | 1..30000; close không chặn caller. |
| sourceSha | UNSPECIFIED | SHA40hex của source; demo script có manifest/hash JAR và sourceDirty để đối chiếu build. |
| workloadLabel | REAL | REAL/MOCK/MIXED; chỉ đặt REAL khi đúng nguồn dữ liệu. |
| faultLabel | NONE | NONE/SIMULATED; nêu cụ thể fault ở metadata ngoài. |

File mở trong writer nền với CREATE_NEW; config sai tắt đo và in mã cố định; file lỗi in mã cố định và giữ counter hoạt động. Logger lỗi không làm message hợp lệ bị từ chối. DROP_NEW vẫn tăng counter, tăng droppedCount, báo trace không đủ. Không thêm disk queue cho event C3: queue log này chỉ phục vụ đo.

Close ngừng nhận record mới, cho TX ticket đang gửi hoàn thành trong ngân sách, drain và FINAL. `finished()` cho harness chờ ngoài FX/WS worker. Quá hạn báo timeout/unwritten/pending; writer bị interrupt và là daemon nên không giữ JVM sống nếu filesystem không chịu interrupt. Nếu bị kill đột ngột/Writer hỏng có thể không có FINAL; script báo INCOMPLETE. Flush là Writer.flush, không phải fsync hay bảo đảm mất điện. Log chỉ COMPLETE khi FINAL khỏe và raw/counter khớp; process exit tự nó không chứng minh flush.

## Chạy demo và tổng hợp

Cần JDK21, Maven, PostgreSQL theo `.env`, Python3.11+ stdlib; phiên này kiểm Python3.15.0b3. Task yêu cầu script đọc JSONL rồi xuất CSV/JSON, vì vậy dùng Python cho bước tổng hợp này; không có Python agent hay collector mới.

```powershell
docker compose up -d --wait
mvn test
mvn package
python -m unittest discover -s scripts -p test_summarize_monitoring.py -v
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c4.ps1
# Script in đường dẫn tương đối <runtime>; có raw/, run-metadata.json, summary.json/csv.
python scripts/summarize-monitoring.py <runtime>/raw --output <runtime>/summary
```

Demo tạo schema c4_test_UUID và port riêng rồi dọn chúng; không reset public DB, không kill process người dùng. HTTP login/scope và candidate/proctor RealtimeClient + dashboard model đều production. Collector ProcessHandle quét thật riêng, chỉ kiểm số lượng/diagnostic; snapshot điều khiển event và overflow là MOCK. SIMULATED retry chặn ACK tại observer C3 sau B2 đã nhận và validate, không giả mạng bị mất packet. Không mở GUI, không chứng minh LAN. Metadata ghi settings poll200ms/HB200ms/presence1200ms/scan25ms/queue event4 và overflow1/in-flight1/retry250ms max5 backoff75..300ms/policy-v1, source manifest và JAR hash. Transport metadata giữ settings thực tế từng endpoint. Một JVM demo không cho phép suy ra độ trễ trên hai máy.

Summary validate schema/ID/UTC/sequence/index/domain/category/control order/FINAL; báo thiếu cuối file, malformed/duplicate field, oversized line, drop/unwritten/timedout, counter mismatch và file endpoint trùng. JSON+CSV do script sinh, input SHA256 bao phủ cả file; không sửa raw. Exit0=COMPLETE, exit2=INCOMPLETE/lỗi. RawMessages/rawBytesUtf8 và counterMessages/counterBytesUtf8 báo riêng. Tổng run chỉ cộng MESSAGE/TX CLIENT+SERVER theo outcome. Nếu thiếu endpoint/file hoàn toàn, script không thể biết một socket không được bật recorder: COMPLETE chỉ có nghĩa các file được cung cấp hợp lệ, không bảo đảm đã quan sát mọi máy trong hệ thống.

Kiểm tính tay trong `test_summarize_monitoring.py`: event100byte thử gửi hai lần (200) + server ACK20byte (20) → tổng TX220byte cho ATTEMPTED, WRITE_COMPLETED riêng cũng220. RX không cộng; hai record TX outcome không cộng với nhau. Fixture MOCK này kiểm phép cộng, không phải byte của message production.

Khảo sát có nguồn và giới hạn: [PROCESS_MONITORING_SURVEY.md](PROCESS_MONITORING_SURVEY.md). Evidence phiên thật: [2026-10-05-verification.md](../evidence/t1-c4/2026-10-05-verification.md). Chưa chạy E1/E2/full/delta, WMI/ETW, GUI/LAN hoặc human B review.

## Bổ sung T2-C1 — 10/10/2026

Whitelist type và Python summary nhận thêm MONITORING_SYNC_OPEN/MONITORING_FULL/MONITORING_SYNC_CLOSE/MONITOR_STATE. Hook đọc sequence từ payload cho FULL, MONITOR_STATE và ACK FULL; OPEN/CLOSE không có sequence nên giữ null. Byte/outcome/schema/hook không đổi: FULL ACK vẫn là BUSINESS_ACK sau correlation, không cộng byte ACK lần hai. REAL full/Edge/GUI component dùng recorder transport hiện có, 4 endpoint có FINAL và summary COMPLETE; fault process set MOCK. [Evidence full](../evidence/t2-c1/2026-10-10-verification.md). Chưa đo overhead/CPU/memory/latency/miss-rate, delta/E1/E2 hoặc LAN; COMPLETE chỉ cho các file cung cấp.
