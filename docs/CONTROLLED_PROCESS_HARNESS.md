# T2-C4 — Công cụ phát process có kiểm soát

Harness là chương trình điều khiển thử nghiệm. Process là một chương trình đang chạy, có PID (số nhận diện do hệ điều hành cấp). C4 tạo process Java riêng, ghi dữ liệu gốc độc lập với collector (bộ quét process), rồi đối chiếu hai nguồn. Nó giúp kiểm tra việc quan sát process ngắn giữa hai lần quét; chưa phải phép đo E1/E2.

## Tự chạy trên Windows PowerShell

Tại thư mục gốc, dùng JDK 21 và Maven:

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c4.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
```

Script chạy 30 process thật: 10 lượt giữ sống 200 ms, 10 lượt 800 ms và 10 lượt 3.000 ms; tối đa 2 child đồng thời. Output mới nằm trong `client/target/t2c4-smoke/<UUID>/`. Không cần DB, server, đăng nhập hoặc mở GUI. Script đọc lại artifact, ghép lần hai giống từng byte, rồi làm hỏng một **bản sao** để kiểm tra kết luận chưa đủ dữ liệu. PASS không yêu cầu collector thấy mọi child.

Chạy CLI (giao diện nhận lệnh) trực tiếp; đổi tên thư mục output cho mỗi lần:

```powershell
$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
$sha = (git rev-parse HEAD).Trim()
java -cp $jar vn.edu.toeic.client.monitoring.harness.HarnessCli run --output-dir client/target/my-c4-run --poll-ms 500 --seed 1234 --per-duration 10 --max-concurrent 2 --source-sha $sha
java -cp $jar vn.edu.toeic.client.monitoring.harness.HarnessCli join --input-dir client/target/my-c4-run --output client/target/my-c4-joined.json
```

Mặc định READY timeout 3.000 ms, exit slack 1.500 ms, thời hạn chạy 180.000 ms. Có thể truyền `--ready-timeout-ms`, `--exit-slack-ms`, `--max-run-ms`. Poll 25–2.000 ms, 1–30 lượt mỗi loại, 1–4 child đồng thời; cấu hình/lịch không vừa hạn sẽ bị từ chối. Một lượt đủ C4 cần ít nhất 10 mỗi loại. Exit code 0 cho COMPLETE, 2 cho lỗi/incomplete. Output có sẵn bị từ chối, không ghi đè.

## Ai làm gì

- `HarnessPlan.create()` trộn ba nhóm thời gian và sinh pha lệch theo seed (số đầu vào để tái tạo lịch). Cùng seed/config cho cùng lịch dự kiến. Hàng đợi hoặc hệ điều hành có thể làm thời gian launch thật muộn hơn.
- `ControlledProcessHarness.run()` tạo child bằng `ProcessBuilder`, quản lý worker (luồng thực hiện công việc), đồng hồ và tài nguyên. Không dùng callback collector để tạo dữ liệu gốc.
- `ControlledProcessChild.main()` in READY (đã sẵn sàng), đợi GO (bắt đầu giữ sống), giữ sống ít nhất thời gian mục tiêu theo đồng hồ riêng rồi thoát. Khởi động JVM (môi trường chạy Java) xảy ra trước READY và không thuộc thời gian giữ mục tiêu.
- `OwnedProcessPolicy.includes()` chọn những PID/start của root child được chính harness tạo. Danh tính được giữ đến khi collector dừng để không loại một quan sát vừa đọc trước lúc child thoát. Thiếu metadata (thông tin tên/thời điểm bắt đầu) vẫn giữ lại.
- `ProcessCollector` và `ProcessHandleSnapshotSource` hiện có thực hiện quét. Không có scanner thứ hai. `TraceRecorder`/`TraceReader` C3 ghi/đọc trace (nhật ký quan sát theo từng lần quét).
- `ObservationJoiner.join()` ghép dữ liệu gốc và trace theo collector session, PID, startInstant và cửa sổ thời gian quét. PID được hệ điều hành tái dùng nên PID đơn lẻ chưa chứng minh cùng process.

## Đồng hồ và ý nghĩa từng mốc

Mọi mốc so sánh được ghi trong **JVM cha**, bằng `System.nanoTime() - runOrigin`. `AlignedTrace.started()` lưu `traceStartElapsedNanos = collectorOrigin - runOrigin`. Vì vậy:

```text
scanEndTrongRun = traceStartElapsedNanos + record.elapsedNanos
scanBeginTrongRun = scanEndTrongRun - record.scanDurationNanos
```

Child chỉ dùng nanoTime của nó để chờ; không gửi mốc đó cho parent. Không trừ đồng hồ giữa hai JVM hoặc hai máy. `Instant` dùng cho chú thích lịch và startInstant nhận diện process. [JDK System.nanoTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()) giải thích origin chỉ có ý nghĩa trong cùng JVM.

| Mốc ground truth | Ý nghĩa do JVM cha quan sát |
|---|---|
| plannedLaunchElapsedNanos | Lịch dự kiến, không phải launch thật |
| queuedElapsedNanos | Giao công việc cho worker |
| launchBeginElapsedNanos | Trước gọi ProcessBuilder.start() |
| startReturnedElapsedNanos | start() đã trả về process |
| registeredElapsedNanos | Đăng ký identity vào policy TEST |
| readyReceivedElapsedNanos | Parent đọc xong READY |
| goSentElapsedNanos | Parent bắt đầu ghi GO, trước flush |
| exitObservedElapsedNanos | Parent nhận biết kết thúc sau waitFor/cleanup |

`observedLifetimeNanos = exitObserved - startReturned`; `observedHoldNanos = exitObserved - goSent`. Hai giá trị bao gồm độ trễ thông báo/đường ống/lập lịch; không phải thời điểm kernel chính xác, không buộc bằng 200/800/3.000 ms. [JDK Process.onExit](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html#onExit()) cũng lưu ý việc thông báo sau kết thúc. C4 không tính detection latency (độ trễ phát hiện).

## File và cách kết luận

`ground-truth.jsonl`: schema `controlled-ground-truth-v1`, HEADER + mỗi TRIAL + FINAL. JSONL là mỗi dòng một đối tượng JSON. Ghi đầy đủ lịch, PID, mốc thật, metadata, trạng thái và cleanup; tối đa 1 MiB, dòng 8 KiB, 90 trial. FINAL chứa SHA-256 (mã kiểm tra dữ liệu có bị đổi) của các dòng trước, kể cả LF.

`observations.jsonl`: recorder/reader/checksum/queue/giới hạn C3; schema riêng `monitoring-observation-trace-test-v1`, policy `test-owned-process-v1`. Trace DTO an toàn cho tên basename hoặc null, không command/path/user. Không thêm java.exe vào 8 tên production. Trace TEST bị production replay/network driver từ chối; trace production v1 vẫn giữ contract cũ.

`metadata.json`: seed/config/plan hash/source SHA, clock mapping, collector session, TEST scope, OS/JDK/máy ẩn danh, số scan, trạng thái tài nguyên và lỗi. `join.json` chứa kết quả từng trial. `manifest.json` kiểm hash **toàn bộ byte** của bốn file; sửa/trộn artifact sẽ làm join INCOMPLETE. SHA kiểm toàn vẹn, không phải chữ ký xác thực.

| Kết luận | Điều kiện |
|---|---|
| OBSERVED | Cùng collector, PID và startInstant, scan giao khoảng registered→exitObserved |
| NOT_OBSERVED_IN_TRACE | Trace COMPLETE, có scan khỏe trước/sau khoảng thử, không SOURCE_FAILURE, có GT start và không thấy identity |
| INCONCLUSIVE | Thiếu start, PID conflict, thiếu coverage, ground truth incomplete hoặc trace/hash lỗi |

OBSERVED có thể mang metadata UNREADABLE nếu thiếu tên. PID-only không ghép chắc chắn. NOT_OBSERVED_IN_TRACE chỉ nói về trace và phạm vi TEST của lần này, không phải tỷ lệ bỏ sót tổng quát. First scan là cửa sổ bắt đầu/kết thúc quét, không phải thời điểm đọc PID chính xác.

## Cleanup và giới hạn bằng chứng

READY/exit/run/cleanup đều có hạn. Khi thành công, lỗi hoặc `cancel()`, harness dừng collector/recorder, đóng streams, chờ worker/reader và chỉ kết thúc root/cây process đã xác định sở hữu. Không taskkill theo tên. Đã kiểm child thật bình thường, lỗi, không READY, treo, API cancel và cây con đã biết sau khi root thoát. OS Ctrl+C/hard kill của parent chưa kiểm trực tiếp; không cam kết dọn tài nguyên sau mất điện/kill cưỡng bức.

REAL: Windows child và ProcessHandle thật. MOCK: dữ liệu giả trong unit test để kiểm metadata/identity/coverage. SIMULATED: candidate gate trong harness, faults replay. Không mạng/DB/GUI trong C4; C3/C1/event hồi quy là các kiểm tra riêng. Không CPU/memory/latency/miss-rate/E1/E2. Human B review và ChatGPT planning/review NOT RUN; B cần xem policy seam, trace TEST, clock mapping, identity/coverage và lifecycle.

Evidence: [báo cáo T2-C4](../evidence/t2-c4/2026-10-10-verification.md). Dừng sau T2-C4; chưa triển khai C5/delta.
