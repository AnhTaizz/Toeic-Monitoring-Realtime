# T2-C5 — Phương pháp baseline monitoring

Đối chiếu implementation tại main `82557bf4a5daddd213469196c3501968b3bbe21e`, ngày **11/10/2026**. Baseline là cách chạy làm mốc so sánh: hiện dùng full snapshot, tức gửi lại toàn bộ tập process thuộc policy đã quan sát được. Tài liệu này chốt cách đọc dữ liệu và **đề xuất phép đo**, không ghi E1/E2 PASS. Không thêm field (trường dữ liệu) trong thông điệp mạng, code đo hay delta (chỉ gửi phần thay đổi). Human B review **NOT RUN**.

## 1. Hệ thống theo dõi cái gì?

Process là một chương trình đang chạy. `ProcessHandleSnapshotSource.scan()` liệt kê process và đọc thông tin mà quyền hệ điều hành cho phép. Collector là bộ quét định kỳ; `ProcessCollector` lọc kết quả theo policy, tức quy tắc chọn process liên quan.

Production dùng `policyVersion = process-policy-v1`, đúng 8 executable (tên file chạy, không phân biệt chữ hoa/thường):

```text
chrome.exe   msedge.exe   firefox.exe   zalo.exe
teams.exe    discord.exe  anydesk.exe   teamviewer.exe
```

Đối chiếu [ProcessPolicy.matches()/includes()](../client/src/main/java/vn/edu/toeic/client/monitoring/ProcessPolicy.java) và [FullSnapshotPayload.EXECUTABLES/POLICY](../protocol/src/main/java/vn/edu/toeic/protocol/monitoring/FullSnapshotPayload.java). Source liệt kê process trước khi lọc. Không gọi đây là quét riêng 8 tên từ hệ điều hành; càng không gọi tập đã lọc là mọi chương trình trên máy.

Ví dụ thí sinh mở Edge: collector có thể quan sát `msedge.exe` và gửi event/full. Mở Notepad không thuộc 8 tên nên không sinh cảnh báo theo policy này. Không thấy process trong policy không chứng minh máy sạch, thí sinh không gian lận hoặc metadata mọi process đều đọc được.

Harness C4 là chương trình điều khiển thử nghiệm. Nó tạo Java child riêng và dùng `test-owned-process-v1`: chọn root process do chính harness đăng ký, giữ identity đến khi collector dừng. [OwnedProcessPolicy.includes()](../client/src/main/java/vn/edu/toeic/client/monitoring/harness/OwnedProcessPolicy.java) kiểm start khi cả hai phía có dữ liệu; thiếu metadata vẫn giữ lại. Không thêm java.exe vào production, không giả tên Edge và không có scanner thứ hai. Trace là nhật ký những lần collector quan sát. Trace TEST dùng `monitoring-observation-trace-test-v1`; trace production dùng `monitoring-observation-trace-v1`. [MonitoringReplayDriver.encode()](../client/src/main/java/vn/edu/toeic/client/monitoring/MonitoringReplayDriver.java) và [ReplayEngine.replay()](../client/src/main/java/vn/edu/toeic/client/monitoring/trace/ReplayEngine.java) **từ chối TEST**.

## 2. Danh tính process và chất lượng dữ liệu

Khóa local [ProcessIdentity](../client/src/main/java/vn/edu/toeic/client/monitoring/ProcessIdentity.java) là `(collectorSessionId, pid, startInstant)`:

| Thành phần | Nghĩa và giới hạn |
|---|---|
| collectorSessionId | ID một lần Start collector, không phải user/attempt/socket. Start mới có ID mới. |
| PID | Số hệ điều hành cấp cho process. Sau khi process thoát, số đó có thể cấp cho process khác. |
| startInstant | Thông tin thời điểm process bắt đầu khi đọc được. Dùng nhận diện, không trừ với nanoTime để đo latency. |

Khác collector hoặc khác start là khác khóa dù cùng PID. Thiếu start chỉ còn khóa yếu theo collector/PID; không phân biệt hoàn hảo PID reuse. Tên executable không đủ làm identity. Full giữ startInstant gốc; event chuẩn hóa timestamp đến microsecond theo [ProcessEvent.parse()](../server/src/main/java/vn/edu/toeic/server/monitoring/ProcessEvent.java) và [MonitoringMessage.observed()](../client/src/main/java/vn/edu/toeic/client/monitoring/MonitoringMessage.java); không ghép các luồng bằng chuỗi timestamp đã đổi độ chính xác một cách tùy tiện.

Metadata là thông tin phụ như tên, start và khả năng đọc user. [ProcessReading.metadataQuality()](../client/src/main/java/vn/edu/toeic/client/monitoring/ProcessReading.java) chỉ COMPLETE khi có tên, start và userAvailable; còn lại UNREADABLE. Không lưu username. Production không chọn được process thiếu tên vì không biết nó thuộc 8 executable nào. Diagnostics trong [ProcessSnapshot](../client/src/main/java/vn/edu/toeic/client/monitoring/ProcessSnapshot.java) giữ số đọc thiếu tên/start/user của toàn lượt quét; trace C3 chỉ lưu tập đã chọn và quality, **không lưu các diagnostic counts**. Muốn phân tích thiếu metadata của toàn máy theo thời gian cần output bổ sung sau này; hiện **NOT MEASURED**, không bịa field trong trace.

`OBSERVATION` rỗng là quét thành công nhưng không thấy process được policy chọn. `SOURCE_FAILURE` là lỗi quét; không được thay nó bằng rỗng. Collector không callback snapshot khi lỗi; full delivery báo SOURCE_FAILURE và event baseline giữ trước đó. Empty full hợp lệ chỉ thay current state, không xóa lịch sử.

## 3. Bốn loại dữ liệu và ACK

ACK là thông điệp xác nhận. Phải đọc nó xác nhận việc gì, không chỉ đọc chữ ACCEPTED.

| Luồng | Ví dụ | Lưu/ACK và điều không suy ra |
|---|---|---|
| Event — lịch sử quan sát | Edge lần đầu được quan sát xuất hiện | `MonitoringDelivery.observe()` tạo eventId ổn định qua retry. Event đã commit (hoàn tất ghi giao dịch) vào DB mới ACK; duplicate cùng nội dung không tạo thêm warning. Không phải thời điểm mở app chính xác. |
| Current state — tập process gần nhất | Full tiếp theo bỏ Edge đã thoát | `MonitoringStateService.full()` thay toàn tập RAM (bộ nhớ tạm, mất khi đóng tiến trình). ACK FULL xác nhận nhận vào RAM một server JVM, không phải commit DB/durability. |
| Presence — tình trạng liên lạc | ONLINE hoặc UNKNOWN khi mất heartbeat | Heartbeat là thông điệp báo vẫn liên lạc. UNKNOWN không chứng minh process nào đã mở/đóng hoặc số event mất. Scoped heartbeat cập nhật presence và ACK sau transaction; unscoped không cập nhật presence. |
| Gap — khoảng thiếu lịch sử đã biết | Queue (hàng đợi tạm) event đầy, bỏ thêm event | MONITORING_GAP QUEUE_OVERFLOW ghi droppedCount rồi commit DB/ACK. Khác monitoring_interruptions do heartbeat timeout, vốn không biết số event mất. |

Full mới không phục hồi event chưa quan sát, không xóa event/gap/interruption đã lưu. Process vẫn chạy qua nhiều poll không tạo event mới mỗi poll; biến mất rồi xuất hiện lại giữa hai poll có thể không được biết. Queue RAM mất khi logout/kill, trace không phải queue bền vững để tự gửi bù.

[RealtimeWebSocketHandler.handleTextMessage()](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeWebSocketHandler.java) gọi [MonitoringEventService.store()](../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringEventService.java) / [MonitoringGapService.store()](../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringGapService.java) qua transaction trước gửi ACK. Lỗi COMMIT không được báo đã lưu. Auth/scope được kiểm trước cả duplicate: candidate sở hữu attempt ACTIVE, proctor được phân công khi đọc/push; full còn bị ràng socket/epoch. Không bypass quyền bằng replay hoặc origin client báo.

`deliveryStatus` là nhãn **nguồn kết nối**, không phải số đo độ trễ. LIVE chỉ nói origin khớp socket nhận; BUFFERED_OFFLINE là quan sát khi offline; PREVIOUS_CONNECTION là socket trước; UNSPECIFIED là thiếu thông tin. Server giữ nhãn lần INSERT đầu qua retry. [EventOrigin.deliveryStatus()](../protocol/src/main/java/vn/edu/toeic/protocol/monitoring/EventOrigin.java), [protocol C2](PROTOCOL.md#t2-c2--heartbeat-event-đến-muộn-và-khoảng-trống). Không trừ observedAt candidate với receivedAt server.

### Epoch, sequence, revision và restart

- `syncEpoch` là thế hệ đồng bộ server cấp bởi OPEN. Epoch mới UNSYNCED, sequence0; full đầu bắt buộc sequence1. Sau đó full toàn tập ở sequence cao hơn được nhảy số. Không áp quy tắc delta liền kề vào full hiện tại.
- `sequence` là thứ tự full **trong epoch**. Retry cùng ID/sequence/nội dung trong cửa sổ dedup (chống nhận trùng) được ACK lại; khác nội dung CONFLICT; cũ hoặc epoch/socket sai bị từ chối. `baseSequence`/delta/resync delta **chưa có**. [FullStateReducer.accept()](../protocol/src/main/java/vn/edu/toeic/protocol/monitoring/FullStateReducer.java).
- `revision` là thứ tự cập nhật state view trong `serverInstanceId`; HTTP/push gộp theo cặp này. Không so sequence giữa epoch hoặc revision giữa hai serverInstanceId như một chuỗi liên tục.
- STALE giữ last-known sau CLOSE/socket mất hoặc timeout không có full mới; worker riêng [MonitoringStateMaintenance](../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringStateMaintenance.java) gọi `maintain()`. TTL là thời hạn giữ entry RAM; hết hạn xóa entry, không xóa history DB. Restart mất current-state RAM, cần OPEN/full1; presence có quy tắc restart riêng, không bịa thời gian downtime.

### Mặc định đã xác nhận, không phải cấu hình smoke

| Thành phần/source | Mặc định production |
|---|---|
| `ProcessCollector.DEFAULT_INTERVAL`, `CandidateMonitoringSession` | Poll 1.000 ms, cấu hình `toeic.monitoring.pollMillis`; lịch fixed delay nên scan/callback làm khoảng thực giữa poll khác poll danh nghĩa. |
| `RealtimeClient.Settings.defaults()` | Heartbeat 2.000 ms. |
| `MonitoringPresenceService` | Timeout6.000 ms, scan500 ms, max4.096attempt và8associations/attempt. |
| `MonitoringDelivery.Settings.defaults()` | Queue500event, inFlight4, ACK timeout5.000 ms, max5attempts, backoff1.000–8.000 ms, pump25 ms. |
| `FullSnapshotDelivery`, `FullSnapshotPayload` | Queue16scan +1pending full/control, full tối đa128process; dùng ACK/retry settings delivery. Vượt giới hạn không cắt tập rồi nhận thành công. |
| `MonitoringStateService`, `MonitoringStateMaintenance` | STALE6.000 ms, TTL300.000 ms từ OPEN/full mới được chấp nhận, scan500 ms, max4.096attempt; dedup64full ID và64OPEN ID gần nhất. Duplicate full không gia hạn touched. |
| `TraceRecorder`, `MessageMeasurements.configured()` | Mặc định tắt. Observation trace queue256; measurement queue1.024/flushMillis2.000. Bật recorder có chi phí chưa benchmark. |

Harness mặc định poll500/seed1234/10lượt mỗi loại/maxConcurrent2/READY timeout3.000/exitSlack1.500/maxRun180.000 ms là **cấu hình TEST**, không thay production. Smoke C1/C2 dùng timeout/queue/capacity nhỏ để ép tình huống; xem metadata từng run thay vì áp chúng vào hệ thống thật. Nguồn mặc định: [CandidateMonitoringSession](../client/src/main/java/vn/edu/toeic/client/monitoring/CandidateMonitoringSession.java), [RealtimeClient](../client/src/main/java/vn/edu/toeic/client/realtime/RealtimeClient.java), [MonitoringPresenceService](../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringPresenceService.java), [MonitoringDelivery](../client/src/main/java/vn/edu/toeic/client/monitoring/MonitoringDelivery.java), [FullSnapshotDelivery](../client/src/main/java/vn/edu/toeic/client/monitoring/FullSnapshotDelivery.java), [MonitoringStateService](../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringStateService.java), [TraceRecorder](../client/src/main/java/vn/edu/toeic/client/monitoring/trace/TraceRecorder.java), [MessageMeasurements](../protocol/src/main/java/vn/edu/toeic/protocol/measurement/MessageMeasurements.java).

## 4. E1: dùng ground truth và trace thế nào?

E1 là thực nghiệm thay chu kỳ polling. Phần dưới là phương pháp chuẩn bị; chưa chạy nghiệm thu.

Ground truth là dữ liệu harness ghi từ việc tự tạo/quản lý child, độc lập collector. Trace là nhật ký collector quan sát. [ControlledProcessHarness.run()/runChild()](../client/src/main/java/vn/edu/toeic/client/monitoring/harness/ControlledProcessHarness.java) ghi mọi mốc so sánh trong JVM cha (môi trường chạy Java), bằng nanoTime trừ một run origin. Child chỉ dùng đồng hồ riêng để giữ sống, không gửi timestamp đó cho cha. [Chi tiết file/CLI/checksum](CONTROLLED_PROCESS_HARNESS.md).

| Đại lượng có sẵn | Định nghĩa và giới hạn |
|---|---|
| targetMillis | Thời gian giữ mục tiêu sau child nhận GO; không phải toàn bộ đời process. |
| Startup proxy | `readyReceivedElapsedNanos - launchBeginElapsedNanos`; bao gồm ProcessBuilder/JVM/đường ống/lập lịch, không phải thời gian CPU (thời gian bộ xử lý làm việc) hay startup JVM chính xác. |
| observedLifetimeNanos | `exitObservedElapsedNanos - startReturnedElapsedNanos`; khoảng tồn tại parent quan sát được, không phải kernel birth→death. |
| observedHoldNanos | `exitObservedElapsedNanos - goSentElapsedNanos`; GO ghi trước flush, exit sau waitFor/cleanup; có độ trễ thông báo. |
| Khoảng TEST chọn process | `registeredElapsedNanos` đến `exitObservedElapsedNanos`; không bao phủ đoạn launch→registered. |
| scanBegin/scanEnd | `scanEnd = traceStartElapsedNanos + record.elapsedNanos`; `scanBegin = scanEnd - scanDurationNanos`. Chỉ biết khoảng quét, không biết mốc đọc từng PID. |

`traceStartElapsedNanos` ánh xạ origin recorder sang origin run, cùng JVM cha. `Instant` dùng chú thích lịch/identity; không thay nanoTime đo khoảng. Không trừ đồng hồ hai JVM/hai máy hoặc lấy runtime replay làm latency. [JDK nanoTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()).

**200 ms sau GO không tự động nghĩa là process chỉ tồn tại 200 ms.** Child đã là process trong lúc JVM khởi động, chờ READY và GO. Sau registered, collector có thể thấy nó trước GO. Workload C4 kiểm việc quan sát **Java child với startup/READY/GO**, không mô phỏng một executable chỉ tồn tại đúng200ms từ birth. Muốn kiểm trường hợp đó cần thiết kế workload/mốc birth/exit đáng tin cậy riêng và B review trước; C5 chỉ ghi thiếu, không triển khai.

### Kết luận hiện có của ObservationJoiner

Đối chiếu [ObservationJoiner.join()/row()](../client/src/main/java/vn/edu/toeic/client/monitoring/harness/ObservationJoiner.java), [HarnessArtifacts.readGroundTruth()/verifyManifest()](../client/src/main/java/vn/edu/toeic/client/monitoring/harness/HarnessArtifacts.java), [TraceReader.read()](../client/src/main/java/vn/edu/toeic/client/monitoring/trace/TraceReader.java). Trace/readers kiểm schema (cấu trúc file), policy, giới hạn, thứ tự, FINAL và checksum (mã kiểm toàn vẹn); manifest ràng file cùng run, SHA chỉ kiểm toàn vẹn không phải chữ ký chống sửa client.

| Kết luận | Điều kiện implementation |
|---|---|
| OBSERVED | Trial bình thường; trace/run binding hợp lệ; cùng collector/PID/start khác-null; khoảng scan giao registered→exitObserved. First scan là scan khớp đầu, không phải mốc mở app. |
| NOT_OBSERVED_IN_TRACE | Không thấy identity; có GT start; trace COMPLETE có scan thành công kết thúc trước registered và scan bắt đầu sau exitObserved; không SOURCE_FAILURE trong trace. Đây là quy tắc bảo thủ của joiner, không phải khẳng định hệ điều hành bỏ sót. |
| INCONCLUSIVE | Chưa đủ kết luận: GT/trace/hash lỗi, trial lỗi, thiếu start, PID conflict hoặc coverage không đủ. Thiếu trace không thành definite miss. |

Metadata là chiều **riêng**: OBSERVED vẫn có thể UNREADABLE do thiếu tên/userAvailable nhưng PID/start đủ ghép. Report row `metadataQuality = NOT_CONFIRMED` khi chưa ghép, không có nghĩa ground truth không có metadata. Báo riêng quality của GT/quan sát/row và tỷ lệ thiếu trường nào nếu raw có; không loại UNREADABLE âm thầm để làm kết quả đẹp. Trace hiện không giữ nguyên nhân thiếu user/tên/start thành diagnostic counters cho toàn máy.

### Đề xuất tỷ lệ không được quan sát trong trace

Chưa có analyzer E1 chính thức. Với từng nhóm thời gian thực đo đã khóa trước khi chạy:

```text
O = số trial eligible kết luận OBSERVED
N = số trial eligible kết luận NOT_OBSERVED_IN_TRACE
R = N / (O + N) × 100%
```

Eligibility (điều kiện đưa vào mẫu) phải dùng **cùng quy tắc cho O và N**: run/artifact COMPLETE; child EXITED/exit0 không forced cleanup; identity GT có start; toàn khoảng startReturned→exitObserved nằm trong cửa sổ đo đã ghi cùng miền clock; policy/collector/clock khớp; trace có scan khỏe bracket registered→exitObserved và không SOURCE_FAILURE. Joiner có thể trả OBSERVED khi không bracket đủ, nên không lấy toàn bộ `observed` làm mẫu số mà bỏ điều kiện này.

Giữ INCONCLUSIVE, child lỗi, run/trace incomplete, thiếu coverage và trial ngoài cửa sổ thành nhóm riêng kèm lý do; không cộng vào N, không im lặng bỏ khỏi báo cáo. Mẫu số0 → NOT MEASURED. Báo O/N/eligible/total/excluded trước phần trăm, theo từng run; không gộp các run lỗi hoặc dùng tỷ lệ có điều kiện thành kết luận tổng quát.

Phân nhóm chính theo **observedLifetimeNanos** (khoảng parent startReturned→exitObserved) và công bố định nghĩa proxy. Giữ targetMillis và observedHoldNanos thành cột phụ, không gọi nhóm target200 là nhóm đời process200. Có thể khóa trước bins (các nhóm khoảng độ dài) như `<500ms`, `500–<1000ms`, `>=1000ms`; đây là **đề xuất**, chưa có script/report bin này. Nếu cần đời process kernel thật thì các bins proxy không thay thế nó.

### Độ trễ phát hiện: đại lượng nào chưa đo được?

Độ trễ mong muốn từ kernel bắt đầu process tới lần đọc PID đầu: **NOT MEASURED**. Chưa có kernel birth dạng monotonic cùng parent và timestamp đọc từng PID. `startInstant` wall-clock không được trừ với nanoTime. Khoảng quét và startReturned/registered chỉ cho proxy, không đủ số chính xác để tuyên bố p50/p95 detection latency.

Đề xuất chỉ báo chẩn đoán: `firstScanEndElapsedNanos - registeredElapsedNanos` là thời gian đến lúc scan đầu khớp **hoàn tất** từ lúc policy TEST đăng ký; báo kèm khoảng `[firstScanBegin - registered, firstScanEnd - registered]`, không gọi là kernel latency. Khoảng có thể bắt đầu trước registered; giữ dấu và giải thích scan giao cửa sổ, không tự clip về0. Chỉ tính trên trial eligible OBSERVED, không gán latency0 cho N/INCONCLUSIVE.

Nếu chọn GO làm mốc, phải ghi tên khác: `firstScanEnd - goSent` là chênh lệch với GO, **không phải độ trễ xuất hiện process**. Process đã quan sát trước GO có thể cho số âm; giữ số âm/đánh dấu PRE_GO và tách khỏi thống kê first-appearance sau GO. Không ép về0, không chọn scan sau GO để giả thành lần quan sát đầu.

Muốn đo latency mong muốn cần B chốt anchor/workload, nguồn timestamp từng PID/độ không chắc chắn và collector tap tương ứng; muốn ACK RTT (thời gian gửi→nhận ACK cùng client) cần correlation đúng lần retry và cửa sổ/clock của recorder. Hiện measurement không lưu span kernel/commit/render, không có analyzer chính thức cho các đại lượng đó. CPU-time (thời gian bộ xử lý dùng), heap/RSS (bộ nhớ JVM/bộ nhớ cư trú của process) theo role và cửa sổ E1 cũng **chưa có công cụ nghiệm thu**; scanDuration không phải CPU time.

### Đầu vào E1 đề xuất và phần harness chưa hỗ trợ

Theo [kế hoạch E1](../Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md#e1-trade-off-chu-kỳ-polling): poll250/500/1000/2000ms, mỗi poll3run, warmup15s/đo120s, tối thiểu10trial mỗi target/run. Đề xuất khóa seed1234/1235/1236 và dùng cùng bộ seed ở mọi poll, đảo thứ tự poll giữa lần lặp, giữ máy/JDK/policy/concurrency/recorder/background giống nhau. Cùng seed cho lịch dự kiến tái lập, không buộc timestamp thật giống byte.

**Không chạy nghiệm thu bằng cách lặp nguyên smoke C4.** Harness hiện không có option warmup15s/cửa sổ120s, marker window, schedule nằm trọn window hoặc resource sampler/analyzer E1. Plan phụ thuộc poll và slot `readyTimeout+3000+exitSlack+poll`, nên cùng seed ở poll khác có lịch launch khác. B phải chốt cách so workload, actual phase và timestamp window; thiếu các đầu vào này thì chỉ gọi functional smoke, không E1 PASS. Output-dir mới mỗi run; giữ raw/manifest/metadata và cả run lỗi/corrupt copy tách nhãn, không sửa raw/checksum để khớp phương pháp.

## 5. E2: chuẩn bị so full và delta về sau

E2 so cách truyền state, không so hai tập process khác nhau. Delta (chỉ gửi phần thay đổi) **chưa triển khai**; không có benchmark full-vs-delta. [Kế hoạch E2](../Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md#e2-full-snapshot-so-với-snapshot--delta) là đầu vào đề xuất, không bằng chứng implementation.

Trước phép so: cùng trace production schema/checksum, policy, identity, observation workload, event rule, poll, ACK/retry/heartbeat/auth/compression/topology/window. Source phải liệt kê/lọc cùng cách; không so full quét mọi process với delta chỉ lọc8tên. Trace TEST harness C4 hiện bị production replay từ chối: không đổi schema bằng tay để ép chạy, không tuyên bố nó là production benchmark input.

Oracle là bộ tính kết quả mong đợi độc lập. [ObservationOracle](../client/src/main/java/vn/edu/toeic/client/monitoring/trace/ObservationOracle.java) C3 kiểm state/history expected theo quan sát và lịch drop/dup/reorder/reconnect, với fixture viết tay và full mutation. Replay PASS có thể vẫn có `missingBusinessEvents`: PASS nghĩa khớp lịch drop đã mô hình hóa, không phải event được gửi đủ. Trước đo delta cần reducer/encoder/oracle delta và MT02–MT06 qua gate correctness; mismatch phải giữ failure trace và loại delta khỏi claim hiệu suất. C3 full replay hiện không kiểm auth/DB/heartbeat UNKNOWN/STALE TTL/network ACK loss/GUI.

Khóa3trace production: ít đổi/nhiều đổi/reconnect lịch định trước, mỗi trace120s, poll1000ms hoặc giá trị khóa trước; 2mode×3trace×3lặp=18run, warmup15s, đảo full/delta. Full đối soát30s ở delta và các marker window là **đề xuất trong kế hoạch**, chưa có công cụ delta. Cần ít nhất một cặp desktop live sau correctness; không thay bằng chạy replayer nhanh.

### Byte đo ở đâu và không đếm đôi thế nào?

Byte UTF-8 là dung lượng **toàn chuỗi JSON envelope+payload** đã serialize tại transport: `text.getBytes(StandardCharsets.UTF_8).length`. Không dùng String.length hoặc dung lượng file log. [MessageMeasurements.attempt()/received()/businessAck()](../protocol/src/main/java/vn/edu/toeic/protocol/measurement/MessageMeasurements.java), [RealtimeClient.write()/Listener.onText()](../client/src/main/java/vn/edu/toeic/client/realtime/RealtimeClient.java), [RealtimeSessionRegistry.MeasuredSession.sendMessage()](../server/src/main/java/vn/edu/toeic/server/realtime/RealtimeSessionRegistry.java). Server đo delegate thật bên trong concurrent decorator, không coi enqueue thành write.

| Outcome | Diễn giải |
|---|---|
| ATTEMPTED | Một lần transport thử ghi toàn message; retry tính thêm lần mới. |
| WRITE_COMPLETED | API write thành công, không chứng minh peer nhận hoặc DB commit. |
| WRITE_FAILED | API write thất bại; báo riêng, không đổi thành byte giao thành công. |
| RECEIVED | RX text hoàn chỉnh đã ghép fragment, gồm JSON malformed; binary/oversized không đầy đủ là UNMEASURED/bytes null. |
| BUSINESS_ACK ACCEPTED | Client validate correlation; ACK JSON đã tính ở RX, record này bytes null nên không cộng lần hai. Ý nghĩa RAM/DB tùy acknowledgedType. |

```text
B_attempted = Σ CLIENT TX ATTEMPTED + Σ SERVER TX ATTEMPTED
B_completed = Σ CLIENT TX WRITE_COMPLETED + Σ SERVER TX WRITE_COMPLETED
```

Báo hai tổng riêng. Không cộng attempted+completed hoặc cộng TX+RX hai đầu cùng frame thành lưu lượng duy nhất. Server TX phải chứa cả ACK/ERROR/push đến giám thị; số proctor/socket là một phần cấu hình. Tách state data (FULL; DELTA tương lai), state control (OPEN/CLOSE/ACK state; resync tương lai), event/gap/ACK history, heartbeat/presence/error/push, rồi báo tổng cùng phạm vi. Raw measurement ACK không lưu acknowledgedType/syncEpoch/revision nên tách ACK state/event hoàn hảo cần analyzer correlation TX requestId/traceId trên đúng endpoint và quy tắc retry đã khóa; **chưa có phân tích category E2 chính thức**, không bịa field.

Tool [summarize-monitoring.py](../scripts/summarize-monitoring.py) hiện kiểm raw/counters/FINAL/drop/unwritten/pending/writer failure, tổng hợp theo endpoint/clock/direction/type/outcome. `COMPLETE` chỉ áp những file được cung cấp; không chứng minh mọi socket/window đều có file. Không có option cửa sổ E2/CPU/memory/delta correctness từ tool này. Phải lập inventory endpoints và mapping origin/window từng recorder trước đo; cùng runId không đồng bộ clockDomain. Không trừ thời gian hai máy chưa đồng bộ.

Log hiện chỉ **JSON text WebSocket monitoring session**: không gồm HTTP login/roster/state refresh/thi/audio, HTTP/WebSocket handshake, TCP/IP/TLS/WebSocket framing/retransmission mạng. Nếu đề xuất tổng E2 có auth/handshake thì cần instrumentation bổ sung hoặc thu hẹp claim rõ thành WS monitoring JSON. Không gọi đó là tổng băng thông hoặc tổng kỳ thi. Chi tiết [MONITORING_MEASUREMENTS](MONITORING_MEASUREMENTS.md). Recorder bật có overhead chưa đo; hai mode dùng cùng cấu hình ghi. Không lấy log smoke C4/replay runtime kết luận delta tiết kiệm hoặc nhanh hơn.

## 6. Khi nào kết quả không hợp lệ?

Các trường hợp sau phải loại khỏi **claim tương ứng**, giữ artifact/lý do, không sửa thành PASS: sai policy/schema/identity/clock mapping; trace thiếu FINAL/checksum/record/coverage hoặc SOURCE_FAILURE làm không kết luận được absence; child lỗi/forced cleanup; output thiếu run/window/endpoint inventory; measurement dropped/unwritten/pending/writer failure; khác workload giữa mode; oracle mismatch; so hai clock không đồng bộ; dùng target thay đời process thực; thiếu CPU/resource/span mà vẫn điền số. Một trial lỗi không chứng minh collector sai; một trace hợp lệ cũng không chứng minh all-system acceptance.

## 7. Bằng chứng hiện tại, không phải kết quả mới C5

| Bằng chứng | Mức và giới hạn |
|---|---|
| [C1](../evidence/t2-c1/2026-10-10-verification.md), [scheduler fix](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md) | Full RAM/epoch/sequence đã kiểm; bản cũ gọi maintain tay không chứng minh scheduler tự chạy. Dùng evidence sửa và hồi quy C4 cho autoSTALE/TTL/shutdown. |
| [C2](../evidence/t2-c2/2026-10-10-verification.md) | REAL Spring/PG/HTTP/WS/proctor component; readings MOCK, outage/ACK faults SIMULATED; không candidate GUI toàn luồng/LAN/human review. |
| [C3](../evidence/t2-c3/2026-10-10-verification.md) | REAL source/owned Edge riêng, REPLAY với control/gate/fault SIMULATED; PASS vẫn có missingBusinessEvents theo drop. Không latency mạng/DB/presence proof. |
| [C4 raw summary](../evidence/t2-c4/summary.json), [C4 verification](../evidence/t2-c4/2026-10-10-verification.md) | Lượt clean0164297:30child REAL/poll500/seed1234/TEST;27observed/3notObserved/0inconclusive. Cả3không xuất hiện thuộc target200, không suy rộng tỷ lệ hay khẳng định đời200ms. |
| [C4 example](../evidence/t2-c4/example.json) | trial006 target200 nhưng observedLifetime329,8944ms và observedHold211,9458ms; OBSERVED dù observation metadata UNREADABLE. Các mốc là parent-observed. |
| [Event concurrent FAIL](../evidence/t2-c4/logs/regression-event-initial-failed.txt), [isolated PASS](../evidence/t2-c4/logs/regression-event-isolated.txt) | Giữ cả hai log. Không đổi code để rerun; nguyên nhân chưa chứng minh, không khẳng định do concurrency như sự thật. |
| Java493 PASS ở C4; Python10 ở C3 | Kết quả **task trước**, không chạy lại trong C5 chỉ để lấy số mới. Kiểm tra mới C5 là tài liệu/source/link/evidence integrity. |

REAL là thành phần chạy thật; MOCK là dữ liệu giả; SIMULATED là kết nối/gây lỗi được mô hình hóa; NOT RUN là chưa thực hiện. Component smoke không thay human B review, candidate GUI toàn hệ thống, LAN, package máy khác hoặc nghiệm thu MT01/07/08; MT02–MT06 delta chưa được nâng PASS. **E1/E2 NOT RUN**.

## 8. Checklist bàn giao B và trước phép đo

- [ ] B đọc policy 8 tên/TEST, khóa PID/start/collector, quyền đọc metadata và giới hạn thiếu start; xác nhận không nhầm giữ sống 200 ms với đời process 200 ms.
- [ ] B chốt câu hỏi E1, mốc bắt đầu, cửa sổ đo, điều kiện đưa vào mẫu, nhóm thời gian thực đo và độ không chắc chắn; giữ dấu số đo PRE_GO, không gán 0 cho lượt không quan sát hoặc chưa kết luận.
- [ ] Có mốc kết thúc làm nóng (warmup)/cửa sổ đo, lịch process khóa theo poll/seed, công cụ lấy mẫu CPU/bộ nhớ và phân tích E1 độc lập; xác nhận bằng dữ liệu tính tay trước chạy 12 lượt. Các công cụ này hiện còn thiếu, cần task được giao riêng.
- [ ] Khóa phiên bản code/JAR, máy/OS/JDK, ứng dụng nền, production hay TEST, số child đồng thời, timeout và recorder; ghi lịch dự kiến/thực tế; output mới mỗi lượt, giữ cả lỗi và mẫu bị loại.
- [ ] E2: delta, MT02–MT06, oracle, ca sai cố ý và reconnect phải đúng trước khi đo hiệu suất; cùng trace production, 8 tên, cách lọc, event/ACK/retry/heartbeat/push, cửa sổ và sơ đồ kết nối.
- [ ] Kiểm danh sách endpoint (đầu gửi/nhận), ánh xạ mốc gốc/cửa sổ, raw đầy đủ/checksum/bộ đếm và byte không đếm đôi; công khai HTTP/handshake/framing chưa đo nếu muốn kết luận tổng rộng hơn.
- [ ] CPU-time/heap/RSS, RTT và khoảng xử lý/hiển thị đúng định nghĩa, cùng đồng hồ; field/tool chưa có ghi NOT MEASURED. Hai mode bật cùng mức ghi đo.
- [ ] Chạy cặp desktop, GUI/LAN khi có môi trường; giữ raw và trace lỗi; chỉ ghi reviewer/kết quả sau người thật review. Merge tài liệu không đánh dấu các checkbox này đã xong.

## 9. Đọc và chạy lại công cụ đã có

Từ thư mục gốc, các lệnh dưới là **hướng dẫn**, không được chạy lại trong C5:

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c4.ps1 -JavaHome '<JDK21>'
$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
java -cp $jar vn.edu.toeic.client.monitoring.harness.HarnessCli join --input-dir evidence/t2-c4/raw --output client/target/c4-join-review.json
java -cp $jar vn.edu.toeic.client.monitoring.trace.TraceCli replay --input client/src/test/resources/monitoring-traces/handwritten-v1.jsonl --output client/target/production-replay-review.json
python scripts/summarize-monitoring.py --help
```

Output report phải mới. `join` dùng TEST; `replay` dùng production fixture, không thay bằng raw TEST C4. Tham số measurement/bật trace và ý nghĩa CLI xem tài liệu riêng. [Protocol hiện có](PROTOCOL.md), [trace/replay](MONITORING_TRACE.md), [harness](CONTROLLED_PROCESS_HARNESS.md), [mục lục evidence](../evidence/README.md). Dừng sau T2-C5; không tự làm chặng3/delta/E1/E2.
