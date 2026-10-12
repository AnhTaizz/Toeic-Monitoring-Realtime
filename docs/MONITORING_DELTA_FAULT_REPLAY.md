# T3-C3 — Chiến dịch replay full/delta

**Cập nhật T3-C4 ngày13/10/2026:** [Tích hợp candidate/live gate](MONITORING_DELTA_LIVE_GATE.md) bổ sung factory production DELTA opt-in + capability socket hiện tại. FULL vẫn mặc định; các đoạn factory luôn full bên dưới là phạm vi lịch sử C1/C2/C3. Live localhost và replay tách riêng; gate20/10/LAN hai máy/human B review/E1/E2 chưa chốt.


Replay là phát lại file quan sát đã lưu, không quét OS lần nữa. Oracle là phần tính đáp án độc lập từ raw observations (quan sát gốc) và lịch giao message. Full vẫn mặc định. C3 kiểm tính đúng theo **mô hình nhận dữ liệu**; không thay live T3-C4, gate của B hoặc E1/E2.

## Chạy bằng PowerShell

```powershell
mvn package
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c3.ps1 -JavaHome $jdk -RealScan

$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
$cli = 'vn.edu.toeic.client.monitoring.trace.TraceCli'
$sha = git rev-parse HEAD
java -cp $jar $cli campaign --fixtures client/src/test/resources/monitoring-traces/t3-c3 --output client/target/my-c3-campaign --seed 20261012 --source-sha $sha
java -cp $jar $cli replay --input client/src/test/resources/monitoring-traces/t3-c3/low-change.jsonl --output client/target/my-gap.json --mode delta --scenario drop-state
```

Output phải chưa tồn tại. Campaign tạo72report (3workload ×12scenario ×2mode),4report âm và summary sinh bởi Java; mỗi positive case chạy lại trong RAM, script còn chạy nguyên CLI campaign lần2 và so byte mọi file. Summary ghi source SHA, manifest SHA, seed, report SHA, phạm vi nguồn/fault. Script metadata thêm command/OS/JDK/config/JAR hash/source manifest và dirty tree. Source SHA là revision thật dùng build; docs commit về sau không đổi nhãn runtime.

Exit0 = kết quả khớp oracle/model; exit1 = mismatch; exit2 = input/config/checksum/schema/I/O không hợp lệ. Ca âm cố ý sai full/delta/event phải exit1; summary campaign PASS đòi ca âm được phát hiện. File corrupt/config sai/TEST harness/no-overwrite phải exit2. Không sửa trace để làm PASS.

## Ba workload và expected nhỏ

File trong [fixtures](../client/src/test/resources/monitoring-traces/t3-c3/): production schema `monitoring-observation-trace-v1`, policy8tên không đổi, source MOCK. Manifest version `monitoring-replay-fixtures-v1`, provenance MOCK_HANDWRITTEN, fileSHA và trace checksum exact bytes. Mỗi file72record/70scan attempts/71giây **thời gian ảo**, không runtime benchmark. Reader/manifest kiểm trước tạo thư mục output; không nhận trace TEST C4 hoặc path traversal.

| Workload | Quan sát chủ yếu | Reconnect |
|---|---|---|
| low-change | Edge+Chrome → Chrome+Zalo → Chrome → rỗng → Edge, rồi nhiều scan không đổi | Không, trừ scenario chỉ định |
| high-change | Thêm/bớt, empty, quality đổi cùng identity, PID reuse/start mới, start null, SOURCE_FAILURE rồi quét thành công | Không, trừ scenario chỉ định |
| reconnect | Thay tập trước/sau epoch, metadata, lỗi source và lịch sử event xuyên reconnect | Trước scan6, kể cả scan lỗi được tính |

[Bảng expected viết tay](../client/src/test/resources/monitoring-traces/t3-c3/handwritten-expected.json) ghi PID và số event mới cho5/12/12checkpoint đầu. Test kiểm bảng này, chất lượng metadata/start null, state theo từng observation và event ID/nội dung; không chỉ count. Low-change đủ dài để có delta rỗng refresh2s và full30s từ scan mới. SOURCE_FAILURE không thành empty hoặc reset event identity baseline.

## Fault model: việc gì có và chưa có?

`MonitoringReplayDriver.encode()` đưa raw vào FullSnapshotDelivery/MonitoringDelivery production bằng đồng hồ trace; delta mode dùng đường shared encoder C1/C2. ACK được tạo **chỉ để liệt kê baseline wire stream**, trước khi gây fault. `FaultSchedule` sau đó giao/drop/hold/duplicate stream này tới `ReplayServer`, dùng parser và MonitoringStateReducer production C2.

**Drop là server chưa nhận message**, không phải mất ACK. HOLD là chưa nhận cho đến RELEASE_LATE ở `atRecord` được lưu. Duplicate giữ nguyên ID/payload. Conflict probe giao sau original, đổi content giữ ID hoặc alias ID giữ sequence; event alias vẫn giữ eventId. RESYNC/ACK_WITH_RESYNC chỉ được ghi thành phản hồi mong đợi, không gửi lại encoder trong vòng mạng lỗi. Phục hồi được kiểm bằng full định kỳ từ successful scan hoặc OPEN/full1 ở reconnect. Không claim replay đã kiểm automatic resync callback/retry/ACK-loss; C2 có bằng chứng mạng thật riêng cho callback và C1 unit có retry. MT02 chỉ phần replay duplicate/conflict, mất ACK end-to-end NOT MODELED.

OPEN/CLOSE đáng tin cậy trong mô hình, reconnect trước scan chỉ định tạo epoch mới. Không mất/duplicate OPEN/CLOSE/ACK/control; không auth/scope revoke/server restart RAM/TTL timer/heartbeat. **UNKNOWN thuộc presence, NOT MODELED**, không suy từ data drop. STALE trong CLI chỉ là CLOSE hoặc rejection outcome, không timeout. Headless event map không PostgreSQL commit/durability/gap/interruption DB. ProcessHandle REAL chỉ là nguồn; transport/ACK vẫn REPLAY/SIMULATED. Candidate toàn GUI, mạng/LAN/hai desktop T3-C4 NOT RUN.

| Scenario | Lịch cố định hoặc ánh xạ | Kiểm chính |
|---|---|---|
| clean | Giao nguyên stream | Canonical identity/metadata/events, skip/refresh/full30s |
| duplicate | Nhân đôi mọi state/event | Không áp/lưu hai lần |
| drop-first | Bỏ state đầu; reconnect trước scan6 | Chưa full không áp delta; OPEN/full1 phục hồi |
| drop-state | Bỏ state ordinal2 của mode | Delta sau RESYNC/UNSYNCED last-known; full mode full mới thay toàn tập |
| reorder | Giữ state2, giao sau state tiếp | Base tương lai không được đoán |
| reconnect-old | Giữ state2 tới sau full1 epoch mới | Epoch cũ STALE, state mới không đổi |
| late-after-full | Giữ state2 tới full tiếp | State version cũ không lùi tập đã phục hồi |
| retry-resync | Giữ state3, sau state4 giao trễ rồi replay full/delta đã áp trước đó | RESYNC và duplicate không tự làm SYNCED |
| conflict-id | Đổi metadata/content của first FULL/DELTA/event sau original, cùng ID | CONFLICT, state/event không đổi |
| conflict-sequence | Alias messageId, đổi content, cùng seq/eventId | CONFLICT, shared sequence full/delta |
| event-drop | Bỏ eventId đầu | Full phục hồi không phục hồi event mất |
| seeded | Seed20261012, duplicate45/drop15/reorder65 trong campaign | Lịch tái lập, không buộc cuối synchronized |

Standalone legacy options `--duplicate/--drop/--reorder/--drop-full-at` vẫn dùng lịch seeded cũ; delta không còn refusal drop/reorder. Explicit scenario khác seeded từ chối trộn random faults để tránh cấu hình mơ hồ. Scenario cần target/reconnect/full recovery mà input thiếu sẽ bị từ chối, không bỏ qua rồi PASS.

Campaign seeded mới: event decision `Random(seed XOR hashCode("event:"+eventId))`, state decision theo ordinal riêng mỗi mode; 3draw lần lượt drop/duplicate/hold. Mỗi channel event/state giữ batch riêng, release sau message cùng channel kế tiếp; held cuối giao sau control cuối và ghi rõ record. Cùng eventId cùng decision/lịch event, cùng observations/policy/event rule/reconnect. **Cùng seed không có nghĩa cùng state fault**, vì full và delta phát số message khác nhau. Report của mỗi mode lưu riêng payload, ID, source, ordinal, generation, expectedSequence, operation và atRecord.

## Oracle và cách đọc PASS

`ObservationOracle` không gọi encoder/reducer. Nó tính business event ID/content từ sự xuất hiện trong raw, canonical expected set/metadata theo observation và version/epoch được driver theo dõi độc lập payload. Nhận full hợp lệ lấy nguyên raw expected; delta liên tiếp lấy raw target tương ứng, thiếu version giữ last-known. Conflict-only probe expected rejection do intent lịch, không lấy digest hoặc decoder production làm đáp án. Mutation trước delivery vẫn expected raw, nên sai added/removed/metadata/full/event bị phát hiện.

`ReplayEngine.replay()` kiểm clean coverage trước, gồm observation không có wire frame; kiểm per-fault outcome/state và toàn event map sau từng action. Reportv2 có businessSteps, observationChecks, cleanChecks, faultChecks, actual/expected deliveredEvents, missingBusinessEvents, full recovery points, resync correlation, mutation config và lastDataState (last non-CLOSE action, có thể STALE nếu delayed data tới sau CLOSE).

Summary so tại cùng raw checkpoint khi **cả hai mode đã khớp observation đó**, không ép sequence bằng nhau. Lúc full mới còn delta UNSYNCED, mỗi mode phải đúng oracle của lịch riêng; checkpoint được ghi divergence. Seeded có thể không có checkpoint cùng synchronized: báo NO_COMMON_SYNC_CHECKPOINT, không claim đã so state bằng nhau. Clean và targeted recovery cases vẫn bắt buộc có checkpoint chung/final SYNCED. UNKNOWN không được tự tạo.

PASS dù missingBusinessEvents>0 nghĩa là công cụ đã xác nhận chính xác event nào không tới server dưới lịch drop, không nghĩa toàn bộ lịch sử được khôi phục. `event-drop` phải thiếu replay-event-1, state cuối vẫn SYNCED. Giữ business expected riêng delivered expected; full không sửa history.

## Bàn giao B và giới hạn

B review mapping fault/source/version, coverage/oracle độc lập, metadata/identity, event deliveryStatus và bounds/tái lập/exit codes. C3 không thay encoder/server/transport production, không thêm message/schema mạng/migration/dependency hoặc bật delta. Lỗi mô hình replay event trước reconnect gắn LIVE được sửa để so receiving connection hiện tại: PREVIOUS_CONNECTION theo EventOrigin hiện có; counterexample và log trước/sau giữ trong evidence. Không giả human B review.

MT02 duplicate/conflict replay; MT03 gap/reorder/last-known/recovery; MT04 epoch cũ; MT05 chỉ reconnect transport/epoch giả lập, restart/revoke thật thuộc C2/C4; MT06 3workload/full cao/low version/metadata/events. **Đây là bằng chứng REPLAY/PARTIAL toàn gate**, không tự chốt QD10 hoặc bắt đầu C4. T3-C4 cần B/A chốt hook và chạy hai desktop, ACK-loss/restart/revoke/control thật. Không E1/E2 hoặc kết luận CPU/latency/byte/hiệu suất.
