# T3-C2 · Reducer delta, resync và độ mới

**Cập nhật T3-C4 ngày13/10/2026:** [Tích hợp candidate/live gate](MONITORING_DELTA_LIVE_GATE.md) bổ sung factory production DELTA opt-in + capability socket hiện tại. FULL vẫn mặc định; các đoạn factory luôn full bên dưới là phạm vi lịch sử C1/C2/C3. Live localhost và replay tách riêng; gate20/10/LAN hai máy/human B review/E1/E2 chưa chốt.


**Cập nhật T3-C3 ngày12/10/2026:** [Chiến dịch replay/model và CLI](MONITORING_DELTA_FAULT_REPLAY.md) đã có72cases/3workload/12fault scenarios cho full và delta; production reducer C2, oracle raw độc lập. ACK baseline enumeration và data faults SIMULATED, resync recorded/no feedback, retry/ACK-loss/presence/live gate/E1/E2 NOT MODELED hoặc NOT RUN. Các đoạn ghi delta drop/reorder chưa hỗ trợ bên dưới là phạm vi task trước; full vẫn mặc định.

Reducer là phần áp thông điệp vào state (danh sách process server đang giữ). Full thay toàn tập; delta thêm/bớt từ một base (state gốc) xác định. T3-C2 có lõi production và tích hợp server/client thử nghiệm. **Full vẫn mặc định**; candidate factory không chọn delta. Gate T3-C3/C4, desktop/LAN và E1/E2 chưa chạy.

## Các lớp và hook dùng lại

- `MonitoringStateReducer` trong protocol là lõi thuần: `full()`/`delta()` nhận DTO đã chuẩn hóa, trả APPLIED, DUPLICATE hoặc RESYNC; lỗi có ErrorCode. Không Spring, socket, auth, DB hoặc scheduler. Replayer gọi cùng lõi này; adapter tuần tự giả của C1 đã bỏ. `FullStateReducer` là facade tương thích C1, delegate vào lõi mới.
- `FullSnapshotReducer` là boundary server: đổi lỗi lõi thành StateRejectedException. Tên cũ giữ cho caller full; có thêm delta/processes/needsFull.
- `MonitoringStateService` dùng lại store RAM C1, instance UUID, revision, TTL/capacity, active binding user/attempt/socket/collector/epoch. `AuthorizationService.requireRole/requireAttempt` kiểm ACTIVE scope trước cache; handler dùng lại SessionAuthenticationService kiểm token mỗi message, registry kiểm auth/assignment mỗi push.
- `RealtimeWebSocketHandler` parse DELTA, gọi service trực tiếp, ACK RAM hoặc gửi resync có correlation. Event/gap vẫn qua transactional service, commit rồi ACK; không thêm migration hoặc xóa history.
- `MonitoringStateMaintenance` worker C1 vẫn gọi maintain định kỳ thật và dọn khi Spring đóng. Không bật scheduling toàn server hoặc sửa ExamTimeoutService.
- `RealtimeClient` B có thêm capability và parser resync/ACK delta. `FullSnapshotDelivery`/`DeltaSnapshotDelivery` C nhận callback này. CandidateMonitoringSession mặc định vẫn full. B review transport/ACK/lifecycle; A review handler/auth/binding/maintenance/push/RAM semantics. **Không đánh dấu T3-A3 DONE thay A**; IT04 toàn hệ thống còn task riêng.

## Sáu quy tắc và atomicity

Epoch là thế hệ đồng bộ cấp qua OPEN. Sequence là số thứ tự state trong epoch; baseSequence là số state delta dựa vào.

1. Lớp tích hợp kiểm token, role, ACTIVE attempt và active binding trước gọi reducer, kể cả duplicate. Lõi cũng ràng collector/epoch sau full đầu; service mới tạo lõi mới khi OPEN đổi epoch.
2. Full đầu phải sequence1. Delta chỉ áp khi có full, không đang needsFull, base=current và sequence=current+1. Delta trước full hoặc có version mới nhưng mất base trả RESYNC.
3. Cache chung full/delta tối đa64 **message ID**, gồm sequence + digest kiểu message/nội dung canonical. Cùng ID hoặc sequence nhưng khác nội dung/type → CONFLICT; cùng nội dung → DUPLICATE. Alias ID có thể chiếm slot. Duplicate không đổi set/sequence/receivedAt/touched/revision, không push lần hai. Ngoài window, version cũ → STALE.
4. Delta mới cho thấy thiếu base: giữ last-known set/sequence/receivedAt, đánh dấu UNSYNCED/needsFull, không lưu message vào dedup. Full mới mới xóa yêu cầu resync. Delta cũ bị STALE/CONFLICT/duplicate không làm state mới thành UNSYNCED. UNSYNCED có dữ liệu nghĩa là dữ liệu cũ, không phải đã đồng bộ; TTL vẫn tính từ state được chấp nhận cuối.
5. Full sequence cao hơn thay toàn tập và xóa needsFull; full cũ không lùi state. Duplicate full cũ khi UNSYNCED vẫn xác nhận đã áp trước đây, nhưng ACK kèm yêu cầu resync; không phục hồi bằng duplicate.
6. OPEN mới đổi epoch và invalid base cũ trong cùng khóa store. Message epoch/socket/collector cũ bị STALE trước cache/reducer. Server restart mất RAM, cấp instance/epoch mới, client reconnect/OPEN/full1; không phục dựng RAM giả từ client.

Identity vẫn collector+PID+start nullable; giữ độ chính xác Instant, tên lowercase và sorting PID/start như full. Không thêm tên executable hoặc đổi collector/event rule. Same identity đổi name/quality dùng full như C1; DELTA không có updated và không cho vừa remove vừa add cùng identity. Remove absent/add identity đã tồn tại/result quá128 → INVALID_INPUT. DTO/schema sai bị từ chối trước áp. Reducer dựng working copy, kiểm hết rồi mới gán processes/sequence/cache; lỗi không để lại set áp nửa.

## Khóa và ACK

Các method open/full/delta/end và phần thay map trong maintain/read/close dùng **cùng monitor của MonitoringStateService**, khóa toàn store một JVM. Nó bảo vệ entries, epoch, reducer/cache, touched, revision và closed; worker bảo trì và thread handler HTTP/WS cùng truy cập. Đây là khóa coarse hiện có, chưa tối ưu throughput. Kiểm scope JDBC trong writer giữ chính sách hiện tại; A cần review chi phí/ranh giới check quyền. Không tuyên bố RAM và quyền DB là một giao dịch phân tán.

Service trả view bất biến sau khi nhận RAM; handler gửi ACK/push ngoài khóa store. Maintain gom view trong khóa rồi publish ngoài khóa. Snapshot có revision tăng trong serverInstanceId; push có thể đến trễ do thread/network, dashboard hiện gộp theo revision/instance và không lùi model. Không gọi socket trong lõi hoặc khóa reducer. Auth/assignment được kiểm lại ở registry trước push.

**ACK state RAM** báo thông điệp đã chấp nhận trong bộ nhớ; không hứa sống qua restart. **ACK event/gap DB** chỉ phát sau service transactional đã commit; rollback không ACK thành công. Delta/full/TTL/resync không xóa event/gap/interruption. Presence heartbeat/history vẫn độc lập.

## Resync đi qua mạng thật

Resync nghĩa là yêu cầu đồng bộ lại bằng full, không đoán delta bị thiếu. `MONITORING_RESYNC` dùng envelope v0: requestId/attemptId/traceId của request, messageId mới do server tạo. Payload:

```json
{"collectorSessionId":"collectorC","syncEpoch":"epochE","currentSequence":2,"reason":"BASE_MISSING"}
```

- Server gửi khi delta chưa có full hoặc base mất. Không ACK thông điệp chưa áp. Đồng thời push UNSYNCED khi trạng thái thay đổi.
- Duplicate đã áp vẫn ACK sequence của request; nếu server đang needsFull thì payload ACK có `resync` là object cùng schema trên. Xác nhận việc đã áp trước đây không có nghĩa base còn dùng được.
- `RealtimeClient.receive()` kiểm scope, pending request/trace, loại FULL/DELTA, collector và epoch trước publish callback; không nhận resync tùy ý. Adapter giải phóng correlation. `FullSnapshotDelivery.receive()` kiểm lần nữa với pending của encoder.
- Client bỏ pending/base/observations, invalid generation cũ, chờ scan mới. Nếu currentSequence>0, giữ epoch và gửi full mới với sequence lớn hơn cả số đã thử gửi lẫn server hiện tại, dùng ID mới. Nếu currentSequence0, số đã hết long hoặc lỗi STALE do epoch/TTL/restart, OPEN mới rồi full1. Retry không được sửa message cũ thành payload khác.
- `observe(snapshot)` là lời gọi trực tiếp từ collector. `onMessage(this::receive)` đăng ký **callback**, hàm được gọi lại khi có reply. `.whenComplete()` nhận kết quả write; write thành công chưa tiến base. Lõi/service được handler gọi trực tiếp, không chạy logic reducer trong JavaFX thread.

Ví dụ: full1={Edge,Chrome}, delta2/base1 bỏEdge/thêmZalo → {Chrome,Zalo}. Retry delta2 ACK lại, không áp thêm. Delta4/base3 tới khi server mới có2 → UNSYNCED giữ{Chrome,Zalo}, resync/current2. Client chờ scan mới, gửi full5 trong epoch đó → SYNCED. Event lịch sử vẫn còn. Cửa sổ/cache/version đầy đủ được kiểm bằng expected viết tay, không lấy reducer làm oracle.

## STALE6s và full30s đã xử lý thế nào?

Baseline6s/TTL5phút không tăng, heartbeat không gia hạn state. Trong delta thử nghiệm, successful scan không đổi có thể skip khi chưa tới hạn refresh; sau **2s từ ACK state cuối** gửi delta rỗng added=[]/removed=[], base hiện tại/sequence+1/ID mới. Server chấp nhận mới thì cập nhật touched/receivedAt; duplicate không gia hạn. Chu kỳ `toeic.delta.refreshMillis` thử nghiệm1–2000ms; poll phải đủ thường xuyên cho timeout mong muốn. Smoke rút refresh200ms/stale1200ms/poll100ms, full đối soát vẫn30000ms.

Message chỉ được tạo từ snapshot mới có observationNanos cùng clock domain của ticker, age>=0 và <refresh interval. Coalescing có giới hạn; quan sát chờ đã quá tuổi bị bỏ và chờ scan mới. **Full đối soát chỉ xét khi có successful snapshot mới**, không tự dùng latest sau30s. SOURCE_FAILURE xóa latest/queue; collector treo hoặc ngừng callback cũng không sinh no-op/full mới. Retry immutable đã tạo có thể vẫn được xác nhận; duplicate không làm dữ liệu tươi mãi. Độ mới server là thời điểm nhận state mới chấp nhận, không đo thời điểm OS chính xác, độ trễ mạng vô hạn hoặc chứng minh client trung thực.

Server giữ UNSYNCED sau mất base đến full phục hồi/TTL/close; STALE do im lặng giữ tập cuối. Presence ONLINE chỉ nói heartbeat còn, không scan. Smoke chờ push STALE/TTL từ worker thật, không gọi maintain hoặc HTTP refresh ở phase này. Cached snapshot gọi lại observe cũng bị chặn theo timestamp scan, không theo thời gian callback.

## Capability, default và demo

Server mặc định `toeic.state.delta-enabled=false`; chỉ khi true mới nhận delta và gửi `monitoringDelta=true` trong ACK heartbeat đã kiểm quyền/commit. Client cần cả JVM `toeic.realtime.delta-experimental=true` và ACK heartbeat đúng correlation từ socket hiện tại. Reconnect xóa capability cũ. Capability cho biết hỗ trợ protocol, không cấp quyền candidate/attempt và không chứng minh gate đã qua. Candidate factory vẫn full kể cả hai flag true; smoke gọi DeltaSnapshotDelivery rõ ràng sau negotiation.

```powershell
docker compose up -d --wait
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c2.ps1 -JavaHome '<JDK21>'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c1.ps1 -JavaHome '<JDK21>' -RealScan
```

Smoke C2 tự dùng server flag/client flag trong JVM test, schema TEST riêng, random port; restart dùng lại port test. Không đổi .env/reset public/kill user process. Readings MOCK, collector worker/server/PG/HTTP/WS/parser/restart REAL; future-base injection SIMULATED. Test không cần JavaFX; hồi quy full-Gui riêng dùng Edge thật/proctor component.

Replay vẫn full default, delta mode dùng cùng MonitoringStateReducer production; oracle độc lập từ raw có refresh2s/full30s, event map giữ IDs/content. Network/control/ACK/auth của replay SIMULATED. C2 không mở chiến dịch faults T3-C3: delta drop/reorder CLI còn refusal; các quy tắc reducer đã kiểm unit/network injection. TEST harness C4 vẫn không là trace production.

Measurement nhận diện DELTA/RESYNC ở MessageIdentity và analyzer, cùng điểm TX/RX transport cũ, không thêm counter trong reducer/delivery; BUSINESS_ACK bytes null giữ nguyên. Không đo E2/CPU/latency hoặc kết luận tiết kiệm. [Evidence C2](../evidence/t3-c2/2026-10-12-verification.md) ghi source/commands/results/limits. Human A/B review, MT02–MT06 toàn gate, candidate GUI/LAN/E1/E2 NOT RUN. Điều kiện bắt đầu T3-C3: dùng main có C2 và fixture/oracle, A/B xem các hooks/contract; không tự bật production hoặc triển khai C3 trong phiên này.
