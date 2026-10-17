# T4-C1 — dữ liệu, phép đo E1 và bàn giao cho B

Ngày 16/10/2026. Phạm vi: review E1 có sẵn và chuẩn bị đo; **chưa nghiệm thu toàn T4-C1**. T3-C4 còn LAN hai máy, review người thật và gate delta. FULL vẫn mặc định. [Báo cáo review](../evidence/t4-c1/2026-10-16-review.md). Không sửa kế hoạch/hợp đồng gốc.

## Ba công việc khác nhau

| Công việc | Đầu vào và mục đích | Trạng thái ở đây |
|---|---|---|
| E1 | Process Java do harness tạo; thay chu kỳ quét để khảo sát quan sát/CPU | Có runner, analyzer được sửa, smoke; số lịch sử cần kiểm lại raw |
| Gate delta | Oracle và lỗi trùng/thiếu/đảo/reconnect, cùng state/event | T3-C4 PARTIAL; E1 không thay bằng chứng gate |
| E2 | Cùng trace cho FULL/DELTA, tính cả byte/CPU/control/retry | Chưa chạy; chỉ chuẩn bị inventory và phép kiểm byte |

E1Cli chỉ nối collector → trace và ground truth. Nó **không mở server/socket, không chạy encoder FULL/event/ACK hoặc GUI**. Chữ FULL mặc định nói về cấu hình ứng dụng được giữ nguyên; không có số đo chi phí đường truyền FULL trong E1. CPU E1 không đại diện toàn ứng dụng thi.

## Cấu hình khóa và lịch

- Windows, JDK21; Python3.11+ stdlib; cùng máy/runtime/source/policy cho cả chiến dịch.
- Trước `--full`, đặt alias máy không nhạy cảm và giữ nguyên cho cả 12 run, ví dụ `-Dtoeic.e1.machineAlias=LAB-A`. Runner từ chối full nếu thiếu alias; analyzer báo `MACHINE_ALIAS_REQUIRED` hoặc môi trường bị trộn thay vì coi hằng số giả là bằng chứng cùng máy.
- 250/500/1000/2000 ms, ba lượt mỗi chu kỳ. Thứ tự: 250→500→1000→2000; 500→1000→2000→250; 1000→2000→250→500.
- Seed1234; mỗi run 10 child cho từng target hold200/800/3000ms (30 child). TEST `test-owned-process-v1`, maxConcurrent2, READY timeout3000ms, exit slack1500ms, maxRun180000ms. Production vẫn policy8 tên; không thêm java.exe.
- Warmup15s, cửa sổ đo [15s,135s] tính từ origin JVM cha của run. Warmup hiện chỉ quét, chưa tạo child: startup/JIT của child chưa được warmup. Đây là giới hạn cần công bố, không tự coi 15s là steady state toàn workload.
- `E1Window.plan`: trộn duration bằng seed; available=120000−8000ms. Launch dự kiến =15000+1000+i×112000/30+phase; phase trong [0,499]ms. Cùng seed cho cùng lịch giữa poll. `planSha256` khóa lịch. Queue/scheduler có thể làm launch thật muộn; giữ cả planned/queued/launch trong raw.
- Fixed-delay: quét/callback xong mới chờ pollMs; khoảng quét thực không bằng chính xác pollMs. Không chạy các suite song song. Một parent JVM cho12run có carryover heap/GC/JIT; thứ tự xoay giảm chứ không loại nhiễu. Ba nhóm duration trong một run không phải ba repeat độc lập.
- FULL baseline vận hành: mỗi observation; DELTA ứng viên: full đầu epoch, full đối soát30s từ quan sát mới, empty delta freshness2s nếu cần. Lịch này dành cho E2/gate, không áp giả vào E1 offline. Trước E2 phải khóa cả ACK/HB/retry/compression/endpoints giống nhau và giải quyết dependency gate.

## Identity, thời gian và mẫu số

Ground truth do `ControlledProcessHarness.runChild` ghi từ ProcessBuilder/READY/GO/waitFor, độc lập với collector. Identity ghép chính xác collectorSessionId+PID+startInstant khác-null. PID-only/thiếu start giữ INCONCLUSIVE, không đổi thành missed hoặc detected. Metadata thiếu tên/user có thể UNREADABLE nhưng identity vẫn ghép được nếu start có đủ.

Mọi khoảng đều dùng `System.nanoTime` trong JVM cha. `scanEnd=traceStartElapsedNanos+elapsedNanos`; `scanBegin=scanEnd−scanDurationNanos`. Child chỉ dùng đồng hồ riêng để giữ sống, không xuất timestamp để trừ với cha. `startInstant` là khóa nhận diện, không là mốc monotonic để tính latency.

| Mốc/metric | Ý nghĩa |
|---|---|
| launchBegin → startReturned | Trước/sau lời gọi tạo process; không biết kernel birth chính xác |
| registered → READY received → GO sent | Đăng ký vào policy; nhận child sẵn sàng; bắt đầu gửi lệnh giữ sống |
| exitObserved | Parent nhận biết thoát, có trễ thông báo/scheduling |
| observedLifetime | exitObserved−startReturned; không phải target hold |
| observedHold | exitObserved−goSent; gồm chi phí pipe/scheduling |
| Eligible | launchBegin và exitObserved có đủ, launchBegin≥windowStart, exitObserved≤windowEnd; toàn lifecycle proxy nằm trọn cửa sổ |
| Detected | Joiner OBSERVED; cùng identity và scan giao registered→exitObserved |
| Missed | NOT_OBSERVED_IN_TRACE: có start, trace hoàn tất và bracket trước/sau, không source failure |
| Inconclusive | Chưa đủ dữ liệu để kết luận; báo reason và metadataQuality |
| Censored | Ngoài cửa sổ/thiếu boundary; giữ trong events nhưng ngoài mẫu số eligible |
| Latency proxy | (firstScanEnd−launchBegin)/1e6 ms; cận trên có startup/registration/scan, không OS creation→exact PID read |

p50/p95 dùng nearest-rank `sorted[ceil(n×p)−1]` trên detected eligible. Missed/unknown không có latency, không điền0. Latency âm là lỗi đầu vào, không ép thành0. N=detected+missed+unknown. Khi unknown=0: missRate=missed/N; khi có unknown: missRate=null, bounds=[missed/N,(missed+unknown)/N]. Bounds không là confidence interval. Mean và phương sai mẫu giữa run dùng n và n−1; n<2 thì variance=null. Luôn báo số run đọc được, không xem n<3 là đủ3repeat.

Ví dụ child giữ200ms có thể đã được collector thấy lúc JVM đang khởi động trước GO. Vì vậy không gọi nhóm này là “mọi process sống đúng200ms”. Kết quả chỉ mô tả Java child/TEST/máy/quyền đã chạy.

## CPU, heap và chất lượng resource

`E1Resources` lấy mẫu500ms: CPU thread `toeic-process-collector`, CPU **JVM cha** và heap-used cha. CPU child JVM không được cộng. CPU cha gồm harness, sampler, trace writer, GC và collector. Thread CPU không gồm trace-writer CPU. Không có RSS.

Trong run: `100 × tổng ΔCPU / tổng Δwall` trên các cặp mẫu kề nhau, có cả hai đầu nằm trong cửa sổ và counter đọc được. Không lấy trung bình không trọng số của phần trăm. Ví dụ100% trong1s rồi0% trong9s:10%, không phải50%. CPU một core có thể vượt100% với nhiều thread; nếu cần chuẩn hóa toàn máy, chia thêm availableProcessors và đặt tên metric riêng, không thay metric một core.

Các cột `collectorCpuCoveredNanos`, `parentCpuCoveredNanos`, `resourceCoveredNanos`, `resourceMaxGapNanos`, readable counts và invalidIntervals nêu rõ phần đo được. Không nội suy qua boundary hoặc counter−1. `resourceStatus=PARTIAL` nếu coverage<95%, gap>2×500ms, counter thiếu/giảm, heap thiếu hoặc ít hơn2mẫu. Đây là tiêu chí QC mới cần B review trước phép đo, không hồi tố biến run cũ thành FAIL. Giá trị PARTIAL vẫn giữ để chẩn đoán, không dùng như CPU đủ120s.

Heap mean là trung bình các mẫu hợp lệ trong cửa sổ (không phải trung bình liên tục theo thời gian). Peak là cực đại **lấy mẫu**, có thể bỏ qua peak giữa hai mẫu. Đọc CPU rồi timestamp không atomic; có jitter. Sampler thread định kỳ có thể dừng nếu JVM API ném exception; analyzer mới báo coverage thiếu, raw hiện không lưu nguyên nhân exception. Nếu cần chẩn đoán sâu phải bổ sung instrumentation ở lượt mới. Chi phí sampler/trace chưa đo độc lập; giữ cùng cấu hình mọi poll, không claim overhead bằng0.

## Inventory/checksum và raw bất biến

- E1 lịch sử: [raw-provenance.json](../evidence/overnight-20261015/phase-06/e1/raw-provenance.json),12run/84file, source `c1601b55b7b40cd84238921e87355108cfaea5fd`. Metadata công khai kiểm hash12/12. Raw local theo manifest chưa có trong workspace review; chưa xác nhận lại84file hoặc CPU cũ.
- Mỗi run mới: `ground-truth.jsonl`, `observations.jsonl`, `metadata.json`, `join.json`, `manifest.json`, `resources.csv`, `e1-manifest.json`. Manifest khóa4file; e1-manifest khóa resources và manifest. Header/record JSONL có checksum nội bộ riêng không gồm FINAL. Không nhầm checksum nội bộ với SHA256 toàn file.
- [Manifest trace T3-C3](../client/src/test/resources/monitoring-traces/t3-c3/manifest.json): ba trace **MOCK viết tay** đã kiểm hash toàn file dưới đây. Chúng dùng kiểm oracle, chưa phải ba trace120s cho E2. Không kéo dài/chỉnh timestamp các file cũ để giả dữ liệu mới.

| Trace | SHA256 toàn file |
|---|---|
| low-change.jsonl | `175ceea908f9a528d65f39fd2b406e3d484b407a81e12329c2d8aadfd67d18e6` |
| high-change.jsonl | `88e21d78eaebea84eeaa717affb4f7b43a2ceb8402a61c1044678aef3799397b` |
| reconnect.jsonl | `255cd30574e9017a0fa4ad3a49eca5ee117a0a96c9e7fdeaa21557ac9b60dac2` |

Trước T4-C2 cần tạo/khóa riêng trace120s ít thay đổi, nhiều thay đổi, reconnect với lịch40–45/80–85s hoặc lịch được chốt trước. T4-C1 hiện chưa có bộ trace120s đủ nghiệm thu E2; không tự triển khai E2 trong review này. Bàn giao những gì đã tồn tại và ghi rõ dependency, không gọi fixture ngắn là dữ liệu đo đủ.

## File đầu ra và metadata bổ sung

Analyzer tạo `run-status.json` giữ cả run lỗi; có INVALID thì thoát khác0 và không sinh summary chiến dịch. RunId lặp không được tính thành repeat mới. `campaign-status.json` báo thiếu12run, sai window/workload hoặc trộn source/config/máy. CANDIDATE_REVIEW_REQUIRED không phải human PASS. Các CSV còn lại: mỗi run-events.csv, summary-by-duration.csv, summary.csv và analysis.md. File events E1 chỉ nói quan sát local; received/ack/bytes không được đo, không điền0 như số đo mạng.

`metadata.json` là tên file runner có sẵn, chứa config/window/clock/source/binary/plan hash/OS/JDK/cores/heap scope. Giữ nguyên nó. B tạo thêm `run-metadata.json` ở thư mục campaign **mới** để ghi thông tin chưa được runner tự thu; mẫu sau chưa có số đo:

```json
{
  "schemaVersion": "t4c1-campaign-context-v1",
  "status": "TEMPLATE_NOT_MEASURED",
  "suiteDirectory": null,
  "sourceSha": null,
  "sourceDirty": null,
  "binarySha256": null,
  "analyzerSha256": null,
  "machineAlias": null,
  "cpuModel": null,
  "osBuild": null,
  "javaVersion": null,
  "backgroundApplicationsAndPowerMode": null,
  "runner": "E1Cli",
  "seed": 1234,
  "warmupMillis": 15000,
  "measureMillis": 120000,
  "pollOrder": [[250,500,1000,2000],[500,1000,2000,250],[1000,2000,250,500]],
  "policyVersion": "test-owned-process-v1",
  "transportScope": "NONE; offline collector only",
  "bytes": "NOT_MEASURED",
  "resourceSamplePeriodMillis": 500,
  "traceChecksums": [],
  "exclusionsWithReasons": [],
  "humanBReview": "NOT_RUN"
}
```

Không lấy HEAD mới gán vào metadata raw cũ. Khi phân tích lại ghi analyzer SHA/hash riêng và hash input, output mới. Checksum bảo vệ toàn vẹn, không chứng minh client trung thực.

## Kiểm byte bằng JSON serialize thật

`E1ByteAccountingTest` gọi Gson và chính `MessageMeasurements` production, payload **MOCK** để kiểm Unicode (không claim server chấp nhận schema MOCK này):

```json
{"protocolVersion":"v0","type":"HEARTBEAT","messageId":"m1","payload":{"note":"á😀"}}
```

Toàn envelope có82byte ASCII + á2byte + emoji4byte = **88byte UTF-8**. ACK serialize `{"protocolVersion":"v0","type":"ACK","requestId":"m1","payload":{}}` có67byte. Client gửi cùng chuỗi hai lần (retry), server ACK một lần: tổng TX application hai chiều=2×88+67=**243byte**. RX là bản nhận của cùng payload, không cộng lần nữa. ATTEMPTED và WRITE_COMPLETED là hai cách xem riêng, không cộng với nhau; BUSINESS_ACK có0byte vì là record semantics của ACK đã đếm ở MESSAGE. Nếu write thất bại giữ attempted/failed riêng, không gọi byte attempted là đã tới peer.

Không gồm WebSocket/TLS/TCP/IP, HTTP/audio hoặc capture wire; chưa là E2. E2 cần inventory toàn endpoint và cùng cửa sổ, tách state data/control/event/HB/retry rồi tổng application; recorder hiện không tự phân loại mọi ACK theo request nghiệp vụ.

## B chạy lại như sau

Tại PowerShell, repo root. Không cần DB. Dùng JDK21 đã cài. Không chạy `clean` tại root vì target chứa raw lịch sử.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -B -ntp "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -pl protocol,client -am package '-Dtest=E1*Test,*Harness*Test,ObservationJoinerTest,*Trace*Test,MessageMeasurementsTest,TestTracePolicyTest' '-Dsurefire.failIfNoSpecifiedTests=false'
python -m unittest discover -s scripts/benchmarks/e1 -p 'test*.py' -v
$sha = (git rev-parse HEAD).Trim()
$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
$cli = 'vn.edu.toeic.client.monitoring.harness.E1Cli'
java -cp $jar $cli --dry-run target/e1 $sha
java -cp $jar $cli --smoke-short target/e1 $sha
# Sau checklist/B review, dành riêng thời gian ~28 phút, KHÔNG chạy tự động trong review:
# java -cp $jar $cli --full target/e1 $sha
$suite = 'target/e1/E1-THAY-BANG-ID-THAT'
$analysis = 'target/e1-analysis-' + [guid]::NewGuid().ToString('N')
foreach ($run in Get-ChildItem $suite -Directory -Filter 'run-*') {
    $out = 'target/rejoin-' + [guid]::NewGuid().ToString('N') + '.json'
    java -cp $jar vn.edu.toeic.client.monitoring.harness.HarnessCli join --input-dir $run.FullName --output $out
    if ($LASTEXITCODE -ne 0) { throw "Rejoin không hợp lệ: $($run.Name)" }
    if ((Get-FileHash $out).Hash -ne (Get-FileHash (Join-Path $run.FullName 'join.json')).Hash) { throw 'Join khác raw gốc; giữ cả hai để kiểm' }
}
python scripts/benchmarks/e1/analyze.py $suite $analysis
if ($LASTEXITCODE -ne 0) { throw 'Xem run-status.json; không dùng summary cũ' }
```

Checklist trước đo: source sạch/JAR đúng hash; máy đủ RAM/disk; ghi CPU/power/background; không suite hoặc tải khác do nhóm khởi chạy; giữ cấu hình/trace/sampler giống nhau; kiểm dry-run, tests, smoke, rejoin/hash; ghi reviewer thật. Trong12run không chỉnh seed/threshold vì kết quả không đẹp. Không đóng ứng dụng của người dùng tự động.

Loại **khỏi kết luận** (giữ raw và lý do): hash/binding sai, thiếu FINAL, child/source/cleanup lỗi, time đảo, schedule/window sai, runId trùng, raw không tìm thấy. Unknown không là lý do xóa trial/run: giữ bounds. Censored giữ số đếm ngoài mẫu số. Resource PARTIAL chỉ hạn chế claim resource; không tự xóa detection hợp lệ. Nếu máy ngủ/tải nền bất thường ghi run và hoàn cảnh, không âm thầm bỏ. Thiếu lượt thì suite PARTIAL; chạy bổ sung phải runId mới, ghi vị trí/thời gian khác để đánh giá carryover.

Bước B tiếp theo: tìm raw12run nguồn c1601b5, kiểm84hash theo inventory, rejoin bằng bản sửa và phân tích ra thư mục mới. Chỉ nếu raw mất hoặc resource coverage không đạt mới cần đo lại các lượt bị ảnh hưởng; muốn kết luận trên bản tích hợp/JDK khác thì phải chiến dịch mới cùng môi trường. Không thay số cũ, không dùng smoke thay12run, không chuyển E2/gate bằng kết quả review này.
