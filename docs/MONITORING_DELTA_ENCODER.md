# T3-C1 · Encoder delta phía client

Encoder là phần chuyển quan sát process thành message gửi đi. Full chứa cả tập; delta chứa phần thêm và phần bớt so với tập đã được xác nhận. Implementation T3-C1 đã có, chạy trong replay/test. **CandidateMonitoringSession và RealtimeClient production vẫn dùng full. Không có cờ JVM để bật delta production.** Server hiện từ chối MONITORING_DELTA; T3-C2/T3-A3 và gate T3-C3/C4 chưa thực hiện. Không kết luận MT02–MT06 hoặc E2 PASS.

## Contract và đường đi

[Protocol T3-C1](PROTOCOL.md#t3-c1--contract-candidate-delta-chưa-bật-production), QD-16. Cùng collector/policy8tên/PID/start/metadata như full; không thay scanner hoặc quy tắc event. `DeltaSnapshotPayload` kiểm và chuẩn hóa schema. Epoch là thế hệ đồng bộ server cấp qua ACK OPEN; sequence là số thứ tự state; baseSequence là số state gốc mà delta dựa vào. ACK là thông điệp xác nhận đã nhận/áp dụng state RAM, không phải commit lịch sử DB.

1. `DeltaSnapshotDelivery` kiểm `MonitoringTransport.supportsMonitoringDelta()`. Default false, RealtimeClient không override; thử khởi tạo với transport hiện tại bị từ chối trước tạo worker/listener. Chỉ transport replay/test khai báo true. Capability này không phải bằng chứng server hỗ trợ: tích hợp tương lai phải cung cấp negotiation/gate thật.
2. Wrapper dùng cơ chế `FullSnapshotDelivery` đang có, với reconciliationNanos>0. Public constructor FullSnapshotDelivery luôn truyền0: queue16, full mọi scan, không chuyển sang delta. Không đổi production factory, heartbeat/event/server/DB/measurement hooks.
3. `bind()` nhận collectorSessionId một lần. `observe()` là lời gọi trực tiếp từ người dùng encoder/driver để nộp snapshot thành công. `sourceFailed()` xóa quan sát chờ/quan sát gần nhất, giữ base đã ACK và message đang retry; không tạo tập rỗng. Một pending message đã tạo trước lỗi vẫn có thể được ACK, nhưng không được dùng quan sát lỗi làm state mới.
4. `tick()` gọi `pump()`. Worker thật gọi tick fixed-delay25ms; replay/test gọi tick trực tiếp bằng clock điều khiển. OPEN trước; ACK OPEN cấp epoch; full đầu sequence1; chưa ACK full không phát delta.
5. `acknowledgedState` và `acknowledged` trong FullSnapshotDelivery giữ tập và sequence **đã ACK**. `pending.target` giữ tập đích bất biến của message đang chờ. Chỉ `receive()` kiểm ACK đúng mới chuyển pending.target thành acknowledgedState. `sequence` là số đã cấp cho message gửi, không tự chứng minh server áp dụng.
6. Nếu tập canonical bằng base thì không gửi state mới. Nếu cùng identity đổi processName hoặc metadataQuality thì gửi full. Còn lại tạo added/removed, baseSequence=acknowledged, sequence=base+1. So toàn metadata, giữ nanosecond start; không so JSON thô.
7. `sendForGeneration()` chạy ngoài lock; guard kiểm socket và vòng đời trước write. `.whenComplete()` là callback: hệ thống gọi lại khi việc ghi hoàn tất; lỗi gọi failed(), thành công không tiến base. `onMessage(this::receive)` đăng ký callback khi nhận ACK/ERROR; đây không phải lời gọi receive trực tiếp từ observe(). Không chạy I/O trên thread JavaFX.

## Ví dụ xuyên suốt

OPEN được ACK epochE. Full1 có Edge(PID10/startA), Chrome(PID20/startB). Ghi socket thành công vẫn chờ; ACK đúng full1 mới lưu base={Edge,Chrome}/sequence1. Quan sát Chrome+Zalo(PID30/startC) tạo delta2/base1: removed=[10/startA], added=[Zalo30/startC]. ACK đúng delta2 mới đổi base thành Chrome+Zalo/sequence2. Quan sát y nguyên không tăng sequence và không tạo event mỗi poll. Event Zalo do MonitoringDelivery sinh riêng, giữ ID/nội dung qua retry và commit DB như trước.

Trong lúc delta2 chờ ACK, có thể thấy Chrome+Teams rồi Chrome+Discord. Chỉ snapshot cuối được giữ cho **current state**; delta2 không bị sửa. Sau ACK delta2, delta3 được tính từ Chrome+Zalo tới Chrome+Discord. Người gọi vẫn phải đưa từng snapshot vào MonitoringDelivery, nên event Teams/Discord không bị coalescing state bỏ đi. Replay thực hiện cả hai lời gọi này. Tích hợp candidate production chưa đổi.

## Giới hạn, retry và dọn tài nguyên

- Delta giữ1quan sát chờ mới nhất +1latest tham chiếu +1pending target +1base, mỗi tập tối đa128process. Hai tham chiếu có thể trỏ cùng snapshot. 1.000quan sát khi đang chờ vẫn queued=1; không xây lịch sử state vô hạn. Oversize/policy sai không cắt tập; xóa latest delta để tránh đối soát từ tập cũ. Event queue500/in-flight4/gap độc lập giữ nguyên.
- Timeout5s, retry tối đa5 theo settings hiện có, backoff1–8s. Mỗi retry sao chép toàn message bất biến: ID/request/trace/epoch/base/sequence/payload giữ nguyên; snapshot mới không viết lại retry. Transport không thể sửa pending qua object nhận được.
- ACK sai protocol/request/attempt/trace/acknowledgedType/status/epoch/sequence không tiến base. ACK trùng sau đã xử lý không xóa pending khác. Callback write lỗi cũ bị kiểm generation/pending/attempt và bỏ.
- Hết retry hoặc ERROR không retry được: bỏ epoch/base/pending, quên correlation, invalid generation, chờ **scan mới thành công**. Sau đó OPEN bằng ID mới, nhận epoch mới và full1 mới. Không dùng sequence2 để đoán server có nhận full1 hay chưa. Mỗi message có số lần thử giới hạn; nhiều scan mới có thể bắt đầu nhiều lần resync, không phải retry vô hạn cùng message.
- RECONNECTING/CONNECTED xóa epoch/base/observations; OPEN mới, nhưng phải có scan thành công sau CONNECTED để gửi full1. Quan sát offline không chuyển sang epoch mới. Stop đóng listener/worker/RAM, gửi CLOSE best-effort nếu còn quyền/epoch; không đóng socket dùng chung. FORBIDDEN/UNAUTHORIZED đúng correlation và global UNAUTHORIZED trước parse dọn encoder; denied callback phải dừng collector/event/heartbeat trong tích hợp tương lai. Logout/đổi lượt gọi close và chờ stopped ngoài JavaFX thread; không tái bind instance cũ.
- Full đối soát khởi đầu30s tính bằng nanoTime, từ **ACK full cuối**, với Duration cấu hình constructor1ms–5phút. Deadline đến khi đang pending thì chờ ACK; không vượt pending để gửi song song. Gửi latest scan thành công; SOURCE_FAILURE xóa latest nên không báo tươi giả. Clock không dùng giờ lịch/đồng hồ máy khác. Đây không phải cam kết deadline mạng30s hoặc test E2.

## STALE6s so với full30s: bàn giao bắt buộc

Server C1 cập nhật độ mới từ full hợp lệ; heartbeat không gia hạn full. Nếu gắn candidate này vào server cũ, delta bị từ chối; nếu chỉ bỏ gửi lần không đổi thì sau6s state sẽ STALE dù heartbeat ONLINE. Full đối soát30s không giải quyết điều đó. **Không tăng timeout6s hoặc dùng heartbeat để che vấn đề.**

A/T3-C2 cần kiểm quyền/socket/collector/epoch trước mọi ACK, áp delta đúng base và sequence, cache duplicate/conflict, resync khi thiếu base/server restart, publish snapshot và cập nhật freshness sau state chấp nhận (duplicate không gia hạn bằng message cũ). Ngoài ra cần chốt cách chứng minh scan vẫn mới khi không đổi: ví dụ message refresh/no-op delta chỉ được tạo từ scan thành công, hoặc contract freshness riêng. Đây là lựa chọn **chưa chốt/chưa cài**; cần thay encoder và test chung nếu được chọn, không lấy heartbeat mạng thay chứng cứ quét. B review capability/transport ACK, generation và lifecycle; measurement phân loại DELTA/control/resync cũng phải tích hợp trước đo E2. Không dùng adapter replay làm server production.

## Tự chạy CLI

PowerShell tại thư mục gốc; mỗi output phải mới. Full là mặc định. Fixture MOCK có expected viết tay: Edge+Chrome → Chrome+Zalo → y nguyên → quality đổi → mất start → PID reuse → rỗng → lỗi scan → Edge xuất hiện lại → Edge giữ nguyên sau30s.

```powershell
mvn package
$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
$cli = 'vn.edu.toeic.client.monitoring.trace.TraceCli'
$trace = 'client/src/test/resources/monitoring-traces/delta-handwritten-v1.jsonl'
java -cp $jar $cli replay --input $trace --output client/target/full-review.json
java -cp $jar $cli replay --input $trace --mode delta --output client/target/delta-review.json
java -cp $jar $cli replay --input $trace --mode delta --duplicate 100 --reconnect-at 3 --output client/target/delta-reconnect-review.json
java -cp $jar $cli replay --input $trace --mode delta --mutate-delta-at 2 --output client/target/delta-negative-review.json
```

Acceptance CLI có thể chạy gọn bằng `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c1.ps1 -JavaHome '<JDK21>' -RealScan`. Script tạo output GUID mới, kiểm hai mode cả process/metadata/event ID/nội dung, ca lỗi chủ động và TEST refusal; tùy chọn RealScan ghi6scans ProcessHandle thật. Không cần Docker/DB cho script này.

Ba lệnh đầu mong exit0/PASS. Lệnh cuối mong exit1/FAIL: cố ý xóa cả added/removed của delta2; oracle từ raw vẫn đòi Chrome+Zalo. Exit2 là file/config/I/O không hợp lệ. Có thể dùng trace production REAL đã ghi bằng TraceCli.record; TEST harness C4 vẫn bị từ chối, không sửa schema. `--mutate-full-at` cũ giữ nguyên; `--mutate-delta-at` chỉ hợp lệ delta và phải chỉ một scan thực sự sinh delta. T3-C1 delta chỉ hỗ trợ clean/duplicate/reconnect tuần tự và mutation; drop/reorder/drop-full bị từ chối exit2, chờ reducer/fault gate T3-C2/C3. Full giữ toàn lịch gây lỗi C3.

Replay dùng cùng FullSnapshotDelivery/MonitoringDelivery, clock/ACK/epoch/network **SIMULATED**, dữ liệu fixture MOCK hoặc scan trace REAL. SequentialDeltaReplayAdapter chỉ parse và áp tuần tự tối thiểu; không auth/socket/DB/TTL/STALE tự động/resync server. Oracle lấy đáp án từ observation gốc; observationChecks kiểm cả lần không gửi frame, so PID/start/name/quality và event map cùng ID/nội dung. Sequence có thể khác giữa full và delta vì skip unchanged; không ép chúng bằng nhau. Report có giới hạn hiện có, không cắt rồi PASS. Không lấy report bytes hay runtime replay kết luận tối ưu.

[Evidence T3-C1](../evidence/t3-c1/2026-10-11-verification.md) phân biệt test mới thật và NOT RUN. Human B review chưa thực hiện, MT02–MT06 toàn hệ thống/desktop delta/LAN/E1/E2 NOT RUN. Không sửa tracker/kế hoạch gốc/.env/migration/luồng thi; dừng sau T3-C1.
