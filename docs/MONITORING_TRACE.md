# T2-C3: ghi và phát lại quan sát process

Trace là file chứa những lần collector đã quan sát. Replayer đọc lại file này để chạy encoder full/event thật và lõi reducer C1 thật. Oracle là thuật toán tính kết quả mong đợi độc lập từ các quan sát gốc. Công cụ chạy không cần JavaFX, Spring, DB hoặc socket.

## Chạy bằng PowerShell tại thư mục dự án

```powershell
mvn package
$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
$cli = 'vn.edu.toeic.client.monitoring.trace.TraceCli'
java -cp $jar $cli record --output traces/demo.jsonl --scans 20 --poll-ms 500
java -cp $jar $cli replay --input traces/demo.jsonl --output traces/demo-result.json
java -cp $jar $cli replay --input traces/demo.jsonl --output traces/demo-seeded.json --seed 1234 --duplicate 20 --drop 10 --reorder 35 --reconnect-at 3
```

Mỗi output phải là tên chưa tồn tại; không ghi đè trace/report. Khi ghi, có thể mở rồi đóng Edge để tạo quan sát khác nhau. Chỉ những lần quét bắt gặp process mới có trong trace. Không đảm bảo phát hiện process sống ngắn hơn khoảng cách giữa hai lần quét.

`record` dùng `ProcessHandleSnapshotSource` và `ProcessCollector` đang có. Gate candidate trong CLI được mô phỏng (SIMULATED); CLI không chứng minh login/quyền/thi thật. `replay` không quét máy lần nữa.

Exit code: 0 = PASS/ghi COMPLETE; 1 = oracle phát hiện khác biệt; 2 = file/config/I/O sai hoặc trace không hoàn tất. Report JSON nêu cấu hình, checksum, lịch DROP/HOLD/DELIVER/DUPLICATE, bước nguồn, expected/actual state, kết quả xử lý và tập event. Không báo PASS khi report vượt giới hạn.

Chạy cả bộ acceptance, bao gồm Edge riêng do test tạo và dọn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c3.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot' -RealEdge
```

Artifact nằm trong `client/target/t2c3-smoke/<UUID>/`. Bộ này mong đợi exit 1 cho full bị cố ý sửa sai và exit 2 cho bản sao trace sai checksum; đó là kiểm tra khả năng bắt lỗi. Hai report mixed/repeat phải giống từng byte. Seed là số cố định để tái tạo lựa chọn gây lỗi; cùng trace/config/seed thì ID, lịch và kết quả giống nhau.

## Bật recorder trong client đang chạy

Mặc định tắt. Đặt các tham số JVM **trước** `-jar`; PowerShell cần đặt cả tham số `-D...` trong dấu nháy:

```powershell
java '-Dtoeic.trace.enabled=true' '-Dtoeic.trace.directory=traces' '-Dtoeic.trace.queueCapacity=256' -jar client/target/client-0.1.0-SNAPSHOT-all.jar
```

Sau login candidate, fresh scope và Start hợp lệ, collector mới ghi một file `<collectorSessionId>.jsonl`. Stop monitoring bình thường để có STOP/FINAL. File không phải hàng đợi gửi bền vững: không dùng nó để tự gửi lại dữ liệu vào server. Không chứa token, password, tài khoản Windows, path hoặc command line.

`ProcessCollector` gọi `started()` khi Start trên caller, `observed()/failed()` trên worker quét và `stopped()` khi worker kết thúc. Tap chỉ làm việc ngắn trong RAM, sau đó collector tiếp tục callback đang có. `TraceRecorder` xếp record vào queue giới hạn; thread `toeic-trace-writer` mới ghi đĩa. Khi đầy, bỏ record mới và đánh dấu INCOMPLETE. Lỗi tap/đĩa không dừng monitoring. `stopped()` yêu cầu drain/close bất đồng bộ; caller cần chờ `finished()` để xác nhận artifact. `close()` chờ tối đa 2 giây rồi interrupt và báo lỗi. JVM bị kill có thể để file thiếu FINAL; reader từ chối file đó. Không cam kết cưỡng chế đóng ngay một thao tác I/O của OS bị treo.

## Schema `monitoring-observation-trace-v1`

UTF-8, mỗi dòng JSON kết thúc bằng LF, không BOM/CRLF. HEADER: kind, schemaVersion, sourceLabel (REAL/MOCK), pollMillis, policyVersion, wallOrigin. RECORD: kind, index liên tục từ 1, type, elapsedNanos, collectorSessionId, policyVersion, scanDurationNanos, processes. Bắt đầu START tại elapsed 0, kết thúc STOP. OBSERVATION với processes rỗng nghĩa là quét thành công và không thấy process thuộc policy; SOURCE_FAILURE giữ state trước đó, không phải rỗng.

Process chỉ chứa PID, executable basename, startInstant có thể null và metadataQuality. Tập được chuẩn hóa theo policy/identity production. Identity giữ độ chính xác startInstant gốc; event theo contract hiện tại chuẩn hóa timestamp tới microsecond. Scan duration giữ để đối chiếu nguồn, không dùng làm thời gian chạy replay hay số đo E1/E2.

FINAL có kind, status, attempted, written, dropped, sha256. SHA-256 (dấu kiểm tra toàn vẹn) tính trên **chính byte HEADER và mọi RECORD theo thứ tự, kể cả LF**, không tính FINAL. Chỉ COMPLETE, attempted=written=số record, dropped=0, STOP hợp lệ và checksum đúng mới được đọc. Đây không phải chữ ký chứng minh client trung thực. Reader từ chối duplicate/unknown field, UTF-8 sai, JSON sai, index thiếu, time đảo, partial line, record sau FINAL hoặc field có thông tin ngoài schema.

Giới hạn: 16 MiB/file (writer dành 1 KiB cho footer), 65.536 byte/dòng, 10.000 record, 128 process/quan sát; queue mặc định256, cho phép1–4096. Replayer tối đa20.000 message,40.000 dòng kiểm tra và100.000 ô state mỗi lượt. Vượt giới hạn là lỗi, không cắt dữ liệu rồi PASS.

## Luồng và sự độc lập

1. `TraceReader.read()` kiểm file và tạo `TraceData.Trace`.
2. `MonitoringReplayDriver.encode()` đưa từng observation vào **FullSnapshotDelivery.observe()** và **MonitoringDelivery.observe()**, chạy tick bằng đồng hồ replay, transport/ACK baseline mô phỏng và ID xác định. Production bình thường vẫn UUID. ACK này dùng để liệt kê message đầu ra, không phải ACK của mạng gây lỗi.
3. `FaultSchedule.build()` tạo lịch seeded trên full/event; OPEN/CLOSE là control đáng tin cậy trong mô hình. Drop là mất vĩnh viễn, duplicate dùng lại đúng ID/nội dung, reorder giữ một batch rồi đưa đến sau batch tiếp theo; reconnect mở epoch mới trước scan đã chỉ định. Scan lỗi cũng được tính trong số scan của `--reconnect-at`.
4. `ReplayServer.apply()` dùng parser `FullSnapshotPayload` và `FullStateReducer.accept()` giống server. `FullSnapshotReducer` phía Spring chỉ bọc lõi này và đổi lỗi về kiểu sẵn có; auth/locking/maintenance server không chuyển sang CLI. Event store headless là map mô phỏng, không kiểm commit DB.
5. `ObservationOracle` tính state và event từ sample gốc, baseline identity trước đó và quy tắc độc lập. Không gọi encoder/reducer/ReplayServer để sinh expected. So cả khóa, metadata process, outcome, ID và toàn bộ nội dung event; không chỉ so count. `ReplayEngine` kiểm coverage baseline trước rồi kiểm lịch gây lỗi.

Fixture và expected **viết tay** nằm ở `client/src/test/resources/monitoring-traces/`. Bốn lần quét: rỗng → Edge42 → Edge42 → rỗng, state count0→1→1→0, event mới0→1→0→0. CLOSE đổi status thành STALE, giữ tập cuối và lịch sử event. `HandwrittenTraceTest` kiểm cả oracle/replay bằng bảng này. `--mutate-full-at 2` trên fixture cố ý xóa Edge khỏi payload encoder; oracle vẫn tính từ bản gốc, phải FAIL. Mutation yêu cầu scan thành công không rỗng.

## Đọc kết quả lỗi đúng phạm vi

Sau full đầu seq1 được nhận, drop seq2 không ngăn seq3 thay toàn bộ state. Nếu drop full đầu, seq2 trở đi chưa đủ điều kiện khởi tạo: INVALID_INPUT và UNSYNCED. Full epoch cũ không ghi đè epoch mới. Event bị drop có thể thiếu trong deliveredEvents nhưng vẫn hiện trong businessEvents/missingBusinessEvents; full mới không khôi phục hay xóa lịch sử. PASS có nghĩa kết quả khớp lịch lỗi đã mô hình hóa, không có nghĩa không mất event.

Không suy UNKNOWN từ full bị drop: UNKNOWN thuộc heartbeat/presence, **NOT_SIMULATED** ở CLI. Không mô phỏng timer STALE/TTL, auth, socket thật, ACK bị mất, retry trên mạng lỗi, backlog offline, gap/interruption DB. C1/C2/C3/C4/A4 integration kiểm hồi quy riêng các đường production đó. Chưa delta, T2-C4, E1/E2, GUI candidate toàn app hoặc LAN. Human B review vẫn NOT RUN; B cần xem collector tap và seam ID, A xem adapter reducer; không ghi đã được thành viên duyệt.
# Bổ sung T2-C4: trace TEST của child có kiểm soát

Recorder/reader/queue/checksum C3 được tái sử dụng. `TraceData.Process` tách DTO lưu file khỏi DTO gửi mạng: trace production v1 vẫn kiểm 8 executable như trước; trace TEST v1 giữ basename/null và start/null với chất lượng UNREADABLE. Header/record phải cùng policy; các cặp schema/policy khác bị từ chối. Production replay và MonitoringReplayDriver từ chối trace TEST, không nới network contract.

Harness và collector chạy cùng JVM cha. `traceStartElapsedNanos` trong ground-truth header ánh xạ origin collector sang origin run; elapsed cộng offset mới được so sánh. Trace độc lập với ground truth tạo từ ProcessBuilder/READY/GO/waitFor. [Chi tiết T2-C4](CONTROLLED_PROCESS_HARNESS.md). Đây không phải replay E1/E2 hoặc nghiệm thu candidate GUI.
