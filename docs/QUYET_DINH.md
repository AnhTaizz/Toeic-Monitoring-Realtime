# Sổ quyết định kỹ thuật

Ghi lại lựa chọn đã chốt để sau này không phải tranh luận lại, và để viết phần "các quyết định thiết kế" trong báo cáo. Các quyết định nền (stack, hợp đồng lưu/nộp, monitoring, Listening) đã có ở `docs/plan-v1/02_HOP_DONG.md`; file này chỉ ghi những gì hợp đồng để ngỏ hoặc phát sinh khi làm.

## Mẫu

```markdown
## QD-03 · Cách gửi credential cho WebSocket
- Ngày: 2026-10-04 · Người quyết: A · Duyệt: C
- Bối cảnh: hợp đồng cấm để token trong URL query.
- Lựa chọn: gửi token trong header lúc handshake.
- Đã cân nhắc: message AUTH đầu tiên sau khi mở kết nối (phải giữ kết nối chưa xác thực một lúc).
- Hệ quả: B đặt header khi tạo WebSocket; server từ chối handshake nếu thiếu.
- Liên quan: T1-A2, T1-B2, AT02
```

## Đang chờ quyết

| Mã | Cần quyết | Ai quyết | Hạn | Task |
|---|---|---|---|---|
| QD-06 | Cách chạy test trên PostgreSQL thật (DB test cục bộ hay container) | A | 12/10 | T2-A4 |
| QD-07 | Định dạng audio và cách đóng gói tài nguyên | B | 08/10 | T1-B4 |
| QD-09 | Quy tắc chấm điểm nội bộ (câu sai, câu trống) | A | 12/10 | T2-A3 |
| QD-10 | Giữ hay cắt delta khỏi bản chính | C, B duyệt | 20/10 | T3-C4 |
| QD-31 | Chấp nhận policy QD-29 đã cài: cấp mới fail-closed, legacy ACTIVE chỉ đọc | A, C; B xem thông báo | trước merge PR39 | QD-29 |
| QD-32 | Chấp nhận 409 `INVALID_STATE` không retry cho lịch sử >5000 | A, C | trước merge PR43 | PR43 |
| QD-33 | Chấp nhận policy cache audio thay PR45 và đóng PR45 | B; A xem | trước merge PR39 | PR40/PR45 |
| QD-34 | Chấp nhận takeover không idempotent theo requestId là giới hạn có chủ đích | A, B | trước 27/10 | T2-A4 |

Khi chốt, chuyển dòng tương ứng xuống mục dưới theo mẫu.

## Đã chốt

## QD-03 · Bearer header cho REST và WebSocket handshake
- Ngày: 2026-10-04 · Người quyết: A theo task T1-A2 · Review C: chưa diễn ra trong phiên Agent, không ghi duyệt thay C.
- Lựa chọn: `Authorization: Bearer <token>` cho REST `/api/**` sau login và raw WS `ws://<server>:<port>/ws/v1/realtime`. Token chỉ xuất hiện ở login JSON body và header truyền qua mạng; không log, không lưu raw trong DB/session attributes, không dùng URL query.
- Lý do: cùng semantics REST, handshake từ chối ngay credential không hợp lệ; Java 21 WebSocket.Builder thực sự gửi được header và Spring/Tomcat đọc/xác thực được.
- Alternative đã cân nhắc: AUTH message đầu tiên sau upgrade; không chọn vì header hoạt động qua network thật, không cần giữ socket chưa xác thực. Không dùng subprotocol/query để mang token.
- Evidence: AuthenticatedNetworkTest dùng real HTTP/WS với MOCK session/scope store; smoke `scripts/smoke-a2.ps1` dùng production Spring app + PostgreSQL thật, login → Bearer handshake → heartbeat/ACK, reject thiếu/sai/expired/revoked và kiểm revoke trên WS đang mở. Log sạch và SHA ở `evidence/t1-a2/2026-10-04-verification.md`.
- Hệ quả cho B: mỗi reconnect mở socket mới và gửi Authorization header lại; handle HTTP 401/503; ERROR UNAUTHORIZED trên WS đi kèm close 1008. Heartbeat unscoped chỉ là transport khi login scope rỗng; B cần cho phép attemptId null và đọc heartbeat ACK/ERROR, không tạo scope giả. Chi tiết field ở PROTOCOL.
- Scope: production AttemptScopeAuthorizer deny unknown/all attempts chưa được cấp; own/proctor assignment chỉ MOCK trong test tới khi có schema thật. Heartbeat ACK không phải event commit/presence.
- Liên quan: T1-A2, T1-B2, AT02. Không đổi TRACKER.json, không sửa nhánh B2 hay triển khai A3/A4.

## QD-01 · Phiên bản nền tảng chặng 1
- Ngày: 2026-10-03 · Người quyết: A + B · Review: C
- Lựa chọn: Java/JDK 21, JavaFX 21.0.12 LTS, Spring Boot 4.1.1, PostgreSQL image major `18` (lần chạy hiện tại là 18.6), Maven 3.9+.
- Đã chạy: Oracle JDK 21.0.8, Maven 3.9.11, Docker 29.4.3, Docker Compose 5.1.3 trên Windows 11 x64.
- Hệ quả: server/client cùng target Java 21; `docker-compose.yml` hiện có tiếp tục được dùng, không tạo compose thứ hai.
- Liên quan: T1-A1, T1-B1.

## QD-02 · Flyway quản lý schema và reset bằng volume phát triển
- Ngày: 2026-10-03 · Người quyết: A · Review: C
- Lựa chọn: migration SQL có version trong `server/src/main/resources/db/migration`; `scripts/reset-db.ps1 -Force` xác minh đúng volume `toeic-pgdata`, xóa volume rồi dựng PostgreSQL sạch. Flyway chạy khi server khởi động.
- Hệ quả: reset làm mất toàn bộ dữ liệu DB phát triển và không dùng cho production; không trộn `schema.sql` với Flyway.
- Đã kiểm: xóa volume, tạo lại container, Flyway chạy V1 và login lại được.
- Liên quan: T1-A1.

## QD-04 · Mã lỗi protocol v0
- Ngày: 2026-10-03 · Người quyết: C + A · Review: B
- Lựa chọn: `UNAUTHORIZED`, `FORBIDDEN`, `INVALID_INPUT`, `INVALID_STATE`, `STALE`, `CONFLICT`, `EXPIRED`, `RETRYABLE_SERVER_ERROR`; lỗi có `retryable`, `requestId`, `traceId`.
- Hệ quả: client quyết định theo mã, không theo câu thông báo. Chi tiết ở `docs/PROTOCOL.md`.
- Liên quan: T1-C1, T1-A1, T1-B1.

## QD-05 · Tầng truy cập DB bằng Spring JdbcClient
- Ngày: 2026-10-03 · Người quyết: A · Review: C
- Lựa chọn: Spring `JdbcClient`/JDBC thay vì JPA để SQL, transaction và lock của các task sau nhìn thấy rõ.
- Hệ quả: mapping record và câu SQL viết tường minh; PostgreSQL JDBC driver là dependency runtime.
- Liên quan: T1-A1, T2-A2–T2-A4.

## QD-08 · Process policy v1 cho demo
- Ngày: 2026-10-03 · Người quyết: C · Review: B
- Lựa chọn: so tên file executable không phân biệt hoa/thường. Policy `process-policy-v1` gồm browser (`chrome.exe`, `msedge.exe`, `firefox.exe`), ứng dụng trao đổi (`zalo.exe`, `teams.exe`, `discord.exe`) và điều khiển từ xa (`anydesk.exe`, `teamviewer.exe`).
- Ứng dụng demo: `msedge.exe`; probe hiện tại cũng quan sát được `Zalo.exe`.
- Giới hạn: process thiếu `command`, `startInstant` hoặc `user` mang chất lượng `UNREADABLE`, không được coi là sạch. Danh sách chỉ phục vụ demo kỹ thuật, chưa phải chính sách thi thật.
- Liên quan: T1-C1, T1-C2.

## QD-09 · Identity khi thiếu thời điểm bắt đầu process
- Ngày: 2026-10-04 · Người quyết: C · Human review B: NOT RUN.
- Lựa chọn: C3 giữ identity C2 `(collectorSessionId,pid,startInstant nullable)`, không dùng metadataQuality để phân biệt. Start null→known hoặc known→null coi là identity mới, thay vì đoán chúng là cùng process.
- Lý do: không đủ dữ liệu để chứng minh PID chưa bị tái sử dụng; ưu tiên khai rõ quan sát và bất định thay vì gộp có thể mất event.
- Hệ quả: metadata hồi phục có thể phát thêm event dù process thực chưa đổi. PID reuse với start luôn null có thể không phân biệt nếu không quan sát được khoảng vắng. Scan lỗi không coi là tập rỗng; polling vẫn có thể bỏ sót process sống giữa hai poll. Hai giới hạn này không được diễn giải thành “máy sạch”.
- Kiểm chứng: MonitoringDeliveryTest missingStart/PID reuse/scan failure; PROTOCOL C3, evidence/t1-c3/2026-10-04-verification.md.
- Liên quan: T1-C3; không thay contract state/reducer chặng2.

## QD-10 · Presence theo heartbeat trong một server JVM

- Ngày: 2026-10-04 · Người quyết: A theo task T1-A4 · Human C review: NOT RUN.
- Lựa chọn: tổng hợp association socket/collector còn heartbeat hợp lệ theo attempt, deadline đơn điệu trong JVM; snapshot và revision cùng interruption lưu PostgreSQL V4. Default6s/scan500ms, tối đa4096attempt/8socket mỗi attempt; khóa theo attempt và DB CAS, push sau commit ngoài lock.
- Lý do: socket cũ đóng không được làm UNKNOWN socket mới đang sống; chỉ dựa close frame không phát hiện hard-kill. UTC là timestamp hiển thị/lịch sử, wall clock chỉnh không quyết định deadline. Revision giúp B3 bỏ HTTP/WS update cũ dù push có thể đến lệch thứ tự.
- Startup: persisted ONLINE → UNKNOWN/SERVER_RESTART, tăng revision trước phục vụ; không dùng nanoTime JVM cũ, không bịa downtime gap/time. Heartbeat mới phục hồi ONLINE; interruption trước đó được giữ và ghi recoveredAt.
- Phạm vi: một server JVM writer trên schema. Không full syncEpoch/reducer hay nhiều server writer; ONLINE xác nhận liên lạc, UNKNOWN không kết luận gian lận. Overflow V3 client-reported tách server-detected HEARTBEAT_TIMEOUT V4.
- Kiểm chứng: fake ticker/wall-clock/latch/association unit; REAL PostgreSQL/B2/C3/hard-kill/multi-client/reconnect/COMMIT failure/restart/cleanup. Contract PROTOCOL T1-A4; evidence/t1-a4/2026-10-04-verification.md. B3 dashboard/parser và GUI/LAN NOT RUN.

## QD-11 · C4 đo toàn JSON và ghi log có giới hạn

- Ngày: 2026-10-05 · Vai C theo task C4 · Human B review: NOT RUN.
- Lựa chọn: byte UTF-8 của toàn JSON envelope+payload tại transport, tách attempted/write-completed/write-failed/full-RX; BUSINESS_ACK không cộng byte. Server hook ở delegate bên trong ConcurrentWebSocketSessionDecorator để không coi enqueue là write. Tổng gửi cộng CLIENT TX+SERVER TX theo một outcome, không cộng RX.
- Recorder opt-in mặc định tắt; queue1024/drop-new, counter độc lập, JSONL writer daemon/flush2000ms. I/O lỗi không thay delivery; thiếu FINAL/drop/mismatch phải báo INCOMPLETE. Không disk-queue event, không thêm telemetry framework.
- Lý do: định nghĩa byte tái kiểm từ đúng chuỗi, không đếm đôi retry/fragment/outcome, không chặn FX/WS bằng file I/O. Chưa đo overhead hay full/delta; WMI chỉ khảo sát nguồn chính thức, giữ ProcessHandle polling hiện có.
- Kiểm chứng và giới hạn: [schema](MONITORING_MEASUREMENTS.md), [survey](PROCESS_MONITORING_SURVEY.md), [evidence C4](../evidence/t1-c4/2026-10-05-verification.md); không tự đổi kế hoạch01–03 hoặc TRACKER.

## QD-12 · Full snapshot v1 có trạng thái RAM và quyền theo epoch/socket

- Ngày: 10/10/2026 · Vai C theo T2-C1 · Human A/B review: NOT RUN.
- Chọn một store RAM trong một server JVM; ACK FULL xác nhận reducer đã nhận RAM, không xác nhận commit DB. Event/gap/interruption tiếp tục dùng storage hooks A và PostgreSQL hiện có. Không thêm hoặc sửa migration.
- OPEN cấp UUID syncEpoch gắn attempt/user/collector/socket, UNSYNCED sequence0; full đầu bắt buộc1. Full mới thay toàn tập nên được phép nhảy sequence, full rỗng hợp lệ. Retry cùng nội dung trong cửa sổ64 ID ACK lại; khác nội dung CONFLICT; cũ/epoch hoặc socket sai STALE. Socket mở trước không giành lại epoch của socket mở sau. Giữ64 OPEN ID gần nhất để retry OPEN đã nghỉ không mở lại generation trên socket dùng chung.
- Mỗi lần cập nhật tăng revision theo serverInstanceId, để B gộp HTTP/push qua reconnect không so sequence của hai epoch. Publish ngoài lock; auth/epoch/reducer cùng lock nhằm tránh hai request kiểm tra trên một state rồi ghi đè nhau. Client kiểm generation collector và socket ngay trước socket write; callback cũ cũng bị bỏ. Đóng collector không đóng WS dùng chung.
- Giới hạn:128process/full, queue16 +1pendingfull, retry5; server4096attempt,64full ID/attempt, STALE6s và TTL5phút từ OPEN/full mới nhất. TTL bỏ state hiện tại, giữ lịch sử DB; restart mất RAM, phải OPEN/full1. Lỗi scan không gửi tập rỗng, quá giới hạn không cắt tập. Policy/identity theo QD-08/QD-09.
- Lý do: đây là baseline kiểm tính đúng và điểm tích hợp tối thiểu; chưa có yêu cầu durability/full nhiều server, delta hoặc benchmark. Không suy ra máy sạch từ tập rỗng hay UNKNOWN.
- Contract: [PROTOCOL](PROTOCOL.md#t2-c1--contract-full-snapshot-baseline-v1); bằng chứng và giới hạn: [T2-C1](../evidence/t2-c1/2026-10-10-verification.md).

### QD-12 bổ sung — worker bảo trì full riêng (10/10/2026)

- Review của người dùng chỉ ra `@Scheduled maintain()` chưa được kích hoạt ở30fa6d9. Chọn bean `MonitoringStateMaintenance` sở hữu một ScheduledThreadPoolExecutor daemon, fixed delay theo `toeic.state.scan-ms`, cùng cách dùng worker riêng của presence A4. Bỏ annotation Scheduled trên maintain để tránh chạy trùng nếu A bật scheduling sau này.
- Không bật EnableScheduling toàn app: thao tác đó đồng thời kích hoạt ExamTimeoutService, vượt phạm vi sửa C1. Scheduler bài thi của A vẫn là vấn đề riêng cần A xử lý/phối hợp; chưa sửa hoặc nghiệm thu nó trong task này.
- Worker bắt RuntimeException không lộ cause/credential để scan sau tiếp tục. PreDestroy hủy/interrupt, đợi tối đa5s; store PreDestroy xóa entries/chặn request muộn. Phụ thuộc Spring maintenance→store giúp hủy worker trước khi hủy store.
- Test Spring/WS/PG giữ socket và heartbeat hoạt động nhưng không gửi full/CLOSE, không gọi maintain/read state: cần tự push STALE, rồi TTL tombstone và OPEN lượt khác thành công khi capacity1. Unit riêng kiểm tự gọi/retry sau lỗi/dừng task đang chạy và dọn RAM. [Evidence bổ sung](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md).

## QD-13 · Nhãn event muộn dựa trên kết nối, giữ cố định khi lưu

- Ngày: 10/10/2026 · C, T2-C2 · Human A/B review chưa chạy.
- Chọn EventOrigin đóng băng lúc quan sát: OFFLINE, CONNECTED kèm ID server trả trong ACK heartbeat, hoặc UNSPECIFIED khi thiếu dữ liệu. So với socket nhận để phân loại BUFFERED_OFFLINE/PREVIOUS_CONNECTION/LIVE/UNSPECIFIED; không so hai đồng hồ chưa đồng bộ. LIVE chỉ nói cùng kết nối, không cam kết độ trễ thấp.
- Lưu classification riêng với payload trong V8; retry cùng event/nguồn gốc trả metadata lần lưu đầu, không relabel/conflict hoặc phát thêm warning. Không áp syncEpoch của full vào event lịch sử. Legacy/mốc trước ACK đầu giữ UNSPECIFIED; cần nâng server trước client mới.
- Gap overflow vẫn V3; heartbeat interruption vẫn V4. Thêm HTTP/tab gap riêng, không suy ra droppedCount từ timeout. Không thêm message type/worker hoặc durability queue, giữ hooks đo C4.
- Cross-owner: A xem migration/transaction/ACK/permission, B xem ACK identity/parser/dashboard và guard plan cũ. Contract chi tiết [T2-C2](PROTOCOL.md#t2-c2--heartbeat-event-đến-muộn-và-khoảng-trống).

## QD-14 · Trace quan sát và replay production với oracle độc lập

- Ngày10/10/2026 · C, T2-C3 · Human B review NOT RUN, A cần xem adapter reducer.
- Tap recorder tùy chọn trên collector sẵn có, không scanner mới/không file queue delivery. Worker ghi đĩa riêng, queue bounded drop-new, lỗi tách khỏi monitoring; thiếu/dropped/lỗi không COMPLETE. Giữ quan sát an toàn, empty success riêng SOURCE_FAILURE, checksum exact byte, không credential/path/command/user.
- Tách thuật toán C1 nguyên trạng vào FullStateReducer dùng chung server và CLI. Server giữ boundary lỗi/lock/auth/store/lifecycle. Driver dùng full/event encoder thật, injection ID ở package constructor không đổi UUID production, đồng hồ theo trace. Không thêm dependency/framework/message/DB schema.
- Oracle từ observation gốc + identity/event rule + lịch delivery riêng; không gọi reducer/encoder để tạo expected. Kiểm oracle bằng fixture/expected viết tay và full sai cố ý. Seed tái tạo IDs/lịch/report byte-identical.
- Transport/ACK baseline/epoch gate SIMULATED; headless event map không chứng minh commit ACK/auth/presence. Drop/reorder data riêng, OPEN/CLOSE reliable; business event khác delivered event. Không suy UNKNOWN/time STALE/TTL. Giới hạn và counterexample [MONITORING_TRACE](MONITORING_TRACE.md); evidence REAL source capture + REPLAY + hồi quy production riêng.
- Không delta/T2-C4/E1/E2. B review collector tap, deterministic ID seam/full/event driver; A review shared reducer adapter. Việc B/A chưa duyệt được ghi riêng với code acceptance, không giả approval.

## QD-15 · Harness child và trace TEST tách khỏi production

- Ngày10/10/2026 · C, T2-C4 · Human B review NOT RUN.
- Reuse ProcessCollector/ProcessHandleSnapshotSource, chỉ thêm ProcessSelection; default ProcessPolicy vẫn 8 tên. OwnedProcessPolicy giữ identity root thuộc lượt chạy đến khi collector dừng, so start khi cả hai đọc được. Không đổi executable production, không giả Edge, không scanner mới.
- ProcessBuilder child Java READY→GO→hold→exit; ground truth từ parent quản lý process, độc lập callback collector. Cùng JVM cha nanoTime, lưu offset origin recorder C3; không trừ đồng hồ child/parent hoặc hai máy. Exit là parent nhận biết, có độ trễ thông báo, không phải kernel time.
- TraceData.Process là DTO lưu file an toàn; production schema/policy giữ kiểm cũ, TEST có schema/policy riêng và bị network/replay driver production từ chối. Reader/parser/recorder/checksum C3 reuse, không đổi FullSnapshotPayload.
- Join xác nhận collector+PID+start+cửa sổ quét; thiếu start/conflict/coverage/lỗi trace thành INCONCLUSIVE. Chỉ complete/bracketed/khỏe mới nói NOT_OBSERVED_IN_TRACE; không tính latency/miss-rate/E1/E2.
- Bounded schedule/queue/threads/READY/exit/run/cleanup; chỉ dọn root/cây sở hữu, CREATE_NEW không overwrite. Test child thật success/error/cancel/known descendant; OS Ctrl+C/hardkill parent NOT RUN. [Contract và cách chạy](CONTROLLED_PROCESS_HARNESS.md).

## QD-16 · Delta dựa vào state đã ACK, coalescing và full khi metadata đổi

- Ngày11/10/2026 · C, T3-C1 · Human B/A review NOT RUN; gate production chưa chạy.
- Reuse FullSnapshotDelivery cho lifecycle/ACK/retry/guard, public constructor full vẫn chế độ0. DeltaSnapshotDelivery yêu cầu capability transport explicit; RealtimeClient mặc định false, candidate production không đổi. Không negotiation giả hoặc cờ bật production.
- Base lưu canonical tập process+sequence đã ACK; pending target bất biến, retry toàn message. Chỉ một pending state và một latest waiting; observations trung gian có thể bỏ trong current state, event delivery độc lập không coalescing theo state. Metadata đổi cùng identity dùng full, vì added/removed chưa có updated. Schema/cách chạy [delta encoder](MONITORING_DELTA_ENCODER.md).
- Full đối soát30s monotonic từ ACK full; source lỗi xóa latest, không tạo empty. Hết retry/không chắc epoch thì invalid base và cần fresh scan→OPEN epoch mới→full1, không tái dùng ID/payload cũ với base mới. Shared transport/listeners/worker cleanup giữ guard.
- Replay dùng adapter delta tuần tự SIMULATED, oracle raw+manual fixture+mutation negative, kiểm observation không phát message và event IDs/content. Delta drop/reorder/resync gate chưa làm, CLI từ chối các option đó. Full faults C3 giữ nguyên.
- STALE6s của full server xung đột skip-unchanged/full30s: giữ nguyên production; cơ chế chứng minh successful scan mới khi không đổi cần A/C chốt riêng T3-C2/T3-A3 và client integration. Không dùng heartbeat để làm tươi full. Measurement DELTA/live negotiation/gate do A/B/C review và tích hợp sau. Không mở E1/E2 hoặc giả human approval.

## QD-17 · Reducer chung, resync có correlation và scan-based refresh

- Ngày12/10/2026 · C, T3-C2 · Human A/B review NOT RUN. Không đánh dấu T3-A3 DONE thay A.
- MonitoringStateReducer thuần là lõi production chung full/delta; FullStateReducer/FullSnapshotReducer giữ facade caller. Cache64ID chung canonical type+payload; invalid set atomic, missing base needsFull/UNSYNCED last-known; full cao phục hồi. Auth/socket/epoch trước cache và một khóa store C1 tuần tự hóa. Publish/write ngoài khóa, dashboard revision/instance chống nhận trễ.
- MONITORING_RESYNC correlated cho message chưa áp; duplicate ACK kèm resync object nếu cần full. Client bỏ base/pending, fresh observation rồi full cao trong epoch, hoặc OPEN/full1 khi chưa base/STALE/restart/TTL. Capability chỉ từ server flag và correlated heartbeat ACK + local opt-in; candidate factory full không đổi.
- Chốt vấn đề6s/30s của QD16 bằng empty delta từ successful scan mới, refresh2s default, không từ heartbeat/latest cũ. Snapshot age theo observationNanos cùng local ticker, queued observation quá tuổi bị bỏ; periodic full cũng cần successful scan mới. Duplicate không touch freshness/TTL. Receiver receipt freshness không OS/transport latency guarantee.
- Thay adapter giả C1 bằng lõi production trong replay; oracle độc lập cập nhật refresh2s. C2 kiểm đơn vị/race + real Spring/PG/WS callback/restart, không triển khai chiến dịch gate T3-C3 hoặc E1/E2. Full/delta cùng process policy/event; measurement known types mới ở hooks cũ.
- A review SessionAuthentication/Authorization hooks/handler/binding/RAM/push/maintenance; B review RealtimeClient capability/resync/ACK/guard/clock/lifecycle. Contract [T3-C2](MONITORING_DELTA_REDUCER.md); production delta vẫn tắt.

## QD-18 · Replay acceptance faults, lịch riêng mỗi mode và resync không feedback

- Ngày12/10/2026 · C, T3-C3 · Human B review NOT RUN, full vẫn mặc định.
- Giữ production full/event/delta encoder và reducer C2, oracle raw độc lập. Gây data faults sau baseline ACK enumeration; drop không là ACK-loss, RESYNC chỉ ghi, recovery bằng fresh periodic full/reconnect. Không giả retry/resync feedback/live network/control reliability toàn diện.
- Ba production-schema MOCK workloads/checksum/handwritten table;72cases/12scenarios ×2mode,4negative, repeat byte-identical và summary sinh Java. Event seeded decisions keyed stable eventId/channel riêng, state theo mode ordinal; không suy cùng seed thành cùng state fault. Lịch source/ID/payload/version/atRecord và expected/actual state/event map lưu reportv2.
- Coverage raw checkpoint kể cả không phát frame; so full/delta khi cùng khớp observation. Seeded zero common checkpoint báo NO_COMMON_SYNC_CHECKPOINT, không ép UNSYNCED bằng SYNCED hoặc claim state equivalence ở chỗ không có. PASS với missingBusinessEvents nghĩa khớp drop model, không khôi phục history.
- Replayer event so receiving connection hiện tại để giữ PREVIOUS_CONNECTION, không origin-as-receiving giả LIVE. Không sửa server/EventOrigin/schema DB/transport/candidate factory. MT02–MT06 REPLAY/PARTIAL cấp toàn gate; live ACK-loss/GUI/LAN/restart/revoke/C4/gate B/E1/E2 còn riêng. [CLI/model](MONITORING_DELTA_FAULT_REPLAY.md).

## QD-19 · Candidate DELTA opt-in và bàn giao live gate chưa chốt

- Ngày13/10/2026, T3-C4. Mốc gate kế hoạch20/10 chưa diễn ra; human B review/LAN hai máy NOT RUN. QD10 là presence heartbeat, giữ nguyên; QD19 liên kết gate delta để không ghi đè quyết định khác.
- Candidate factory thật chọn DELTA chỉ khi cờ thử nghiệm + capability đã xác nhận trên socket hiện tại. FULL mặc định, UNKNOWN/old/disabled -> FULL pinned đến Start mới. Đang DELTA reconnect -> chờ capability mới, OPEN/epoch/full từ scan mới; support mất -> stop, Start mới FULL, không đổi base/pending ngầm.
- Sửa hai lỗi có evidence: ACK malformed connectionId không được bật capability; FORBIDDEN của scoped heartbeat phải báo coordinator dừng đúng collector/socket dù source treo và không có state pending. Transient DB không coi revoke, callback cũ không dừng lượt mới. Chỉ callback nội bộ, không đổi schema/wire/migration hoặc thi/Listening.
- Local dùng default candidate/proctor Applications thật + REAL ProcessHandle/Edge/PG/HTTP/WS, TEST seams chỉ gây fault. ACK observer drop/base/source/DB trigger SIMULATED, không đo mất gói vật lý. Raw thiếu FINAL do hard-kill giữ INCOMPLETE; không kết luận byte/latency/benchmark.
- **Gate chưa chốt, FULL vẫn mặc định.** Thiếu môi trường/review không là lỗi delta đã chứng minh. A review auth/binding/scope notification/maintenance, B review factory/callback/guard/UX và chạy LAN. Không đánh dấu T3-A3 DONE thay A. [Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md); acceptance PARTIAL có thể merge code an toàn với opt-in off, không giả approval hoặc tự T3-C5/E1/E2.

## QD-21 · Deadline bắt đầu khi thí sinh nhận quyền ghi để vào thi

- Ngày11/10/2026 · Vai A (Agent) · Human review NOT RUN · nhánh P0 chưa merge.
- Dùng POST takeover hiện có làm mốc entry: dưới khóa hàng và sau kiểm owner/ACTIVE, server đặt deadline chưa có bằng clock_timestamp()+duration ca và trả field deadlineAt hiện có. Hai máy được tuần tự hóa; takeover/reconnect sau giữ nguyên deadline. Status/exam GET không khởi tạo giờ.
- PR17 luôn takeover sau status/exam, nên không đổi wire/DTO/client production. Legacy save-first được giữ; ca chưa vào vẫn chưa bắt đầu giờ. WriterEpoch vẫn tăng mỗi entry (QD16 trên PR17), không được dùng epoch full của C làm writerEpoch.
- Read history tách scope ACTIVE, scheduler exam riêng và chốt theo answers đã commit. [Review A/B](REVIEW_A_PR17.md).

Ghi chú đánh số 13/10/2026: khi đồng bộ nhánh P0 với main `ee714fc`, QD-17 và QD-18 đã thuộc T3-C2/T3-C3 của C, nên quyết định deadline của A (trước mang số QD-18 trên nhánh này) đổi thành QD-21. QD-20 và QD-22 dành cho hai quyết định của B trên PR #17 (mất WebSocket; `writerEpoch` khi vào thi); các mục đó vào main cùng PR tương ứng. Đồng bộ lần hai với main `cb30f3d`: QD-19 đã thuộc T3-C4 của C, nên quyết định API lịch sử lượt thi của A (bản ghép cục bộ trước đó ghi QD-19) mang số QD-23.

## QD-24 · Tài nguyên audio ngoài JAR, READY theo manifest/writer và gate start bền vững

- Ngày13/10/2026 · T3-A1 theo yêu cầu người dùng · Human C review NOT RUN.
- Người tổ chức cấp file vào `TOEIC_AUDIO_ROOT/<examId>/<audioFile>`; server chỉ cho đúng chủ lượt tải các file Listening được đề tham chiếu. Không tải cả thư mục/gói đề; không gửi đáp án hoặc credential trong URL. Manifest chứa tên/kích thước/SHA-256, định danh cả ca và nội dung. MP3/WAV/M4A là allow-list phục vụ byte; QD-07 và codec thực tế JavaFX vẫn chờ B kiểm package.
- READY là báo cáo client về checksum/thử loa, lưu DB theo attemptId + writerEpoch + manifestId; takeover hoặc tài nguyên đổi làm READY cũ không hợp lệ. Client gửi ready=false khi chuẩn bị lại/phát hiện lỗi. Server không thể chứng minh nội dung file local hoặc khả năng nghe của người dùng.
- HTTP start khóa ca rồi tất cả lượt, dùng clock_timestamp sau khóa, chỉ ghi khi mọi lượt có READY hợp lệ. Lưu startId/manifest/startedAt trong một COMMIT; retry giữ cùng ID. Không đổi deadline/answers/writer của lượt. A2 nối WS/phase/gián đoạn vào startId này; chưa có playback trong A1.
- Contract và giới hạn: [AUDIO_RESOURCES.md](AUDIO_RESOURCES.md). Evidence: [T3-A1](../evidence/t3-a1/2026-10-13-verification.md). Không thêm dependency hoặc sửa migration cũ.

## QD-25 · Listening lifecycle bền vững và heartbeat riêng

- Ngày13/10/2026 · T3-A2 theo yêu cầu người dùng · Human C review chưa chạy.
- Tái sử dụng startId của A1. Metadata Listening của lượt và deadline chưa có được ghi cùng start; deadline cũ giữ nguyên. WS command/status chỉ phát sau COMMIT, kiểm lại auth/scope; explicit retry gửi cùng ID cho ARMED hợp lệ, không gửi khi reconnect hoặc playback đã bắt đầu/gián đoạn.
- Heartbeat Listening mang startId/writerEpoch riêng, 2s/timeout6s theo tham số khởi đầu hợp đồng. Không thay presence/full/delta của C. Worker riêng quét DB; mọi event/save/submit kiểm dưới khóa hàng và dùng DB clock, không cho heartbeat đến muộn phục hồi Listening. Restart/takeover chưa COMPLETED giữ cờ gián đoạn; nâng V9→V10 cũng bảo vệ ca đã start.
- Gián đoạn là cờ riêng, không dùng state INTERRUPTED để cắt Reading/lịch sử. Server đóng băng phần LISTENING của answers đã commit; Reading vẫn ghi trong deadline. Status/submit/chốt timeout giữ marker. Giám thị được gán tạo replacement mới, READY/start riêng, không reset/xóa dữ liệu lượt cũ.
- Mỗi lượt cũ có một replacement trực tiếp; retry cùng request/payload trả cùng ID, request khác trả CONFLICT. Không dùng status hoặc HTTP response để autoplay. Adapter/media/GUI B tiếp tục theo [contract](LISTENING_LIFECYCLE.md); [evidence T3-A2](../evidence/t3-a2/2026-10-13-verification.md).

## QD-26 · Chuẩn bị audio client và xác nhận nghe riêng trước READY

- Ngày 14/10/2026 · T3-B1 · review A chưa chạy. Branch từ main `2e784fa`, không ghép các nhánh B4/Reading chưa merge.
- Tách adapter HTTP, cache/checksum, state machine và view JavaFX. Worker duy nhất xử lý mạng/file/hash; UI chỉ nhận snapshot qua callback được gộp. READY cũ được xóa và ACK trước chuẩn bị; READY true cần kiểm lại byte local, xác nhận nghe mẫu riêng và ACK đúng scope/writer/manifest/request.
- Lần đầu chuẩn bị lấy writer bằng takeover hiện có; tải lại cùng lượt cùng phiên giữ writer. Deadline bắt đầu theo QD-21. Khi ghép Reading phải truyền writer dùng chung thay vì takeover lần hai. Disconnect/đổi lựa chọn/đóng view loại callback cũ; reconnect không tự phát/xác nhận.
- Mẫu thử loa WAV PCM một giây được sinh ngoài UI, không dùng audio đề. Media phát hết chỉ mở nút xác nhận nghe; không tự suy ra người dùng đã nghe. Media lỗi chặn READY. JavaFX Media dùng cùng version 21.0.12 với controls. MP3/M4A vẫn là allow-list byte theo A1, chưa được B1 nghiệm thu codec.
- Cache nằm ngoài app-image, theo hash attempt/manifest; `.part` chỉ đổi thành file đích sau checksum. B2 phải kiểm lại cache ngay trước playback. B1 không nối start/playback/gián đoạn đề; bộ cài và máy thứ hai giữ ở B5. [Hướng dẫn](AUDIO_CLIENT_PREPARATION.md), [bằng chứng](../evidence/t3-b1/2026-10-14-verification.md).
- Package local phát hiện launcher JDK 21 không tìm runtime trong thư mục Unicode. Build dùng argv ASCII và Windows SDK `mt.exe` để thêm UTF-8 vào manifest launcher mới sinh, giữ DPI/asInvoker gốc; runtime vẫn 21.0.9. Không đổi locale Windows hoặc patch JDK/DLL. Windows hỗ trợ setting này từ 1903 theo [Microsoft](https://learn.microsoft.com/en-us/windows/apps/design/globalizing/use-utf8-code-page); package smoke trên máy hiện tại kiểm lại cả WAV và view production.

## QD-27 · Playback một lần theo startId và journal fail-closed

- Ngày 14/10/2026 · T3-B2 · review A chưa chạy. Branch kế thừa T3-B1 để dùng cùng writer/manifest/cache READY; không takeover hoặc nhập nhánh Reading.
- Chỉ `LISTENING_START` WS hợp lệ cấp quyền phát. Client kiểm envelope/scope, so exact attempt/writer/manifest, SHA-256 lại cache và ghép đề candidate theo `audioFile`. HTTP start/status/reconnect không autoplay.
- Dedupe key là attemptId + writerEpoch + startId. Worker reserve marker bằng CREATE_NEW trước Media; marker tồn tại chặn replay cả sau restart. Crash sau reserve nhưng trước loa là fail-closed và cần lượt tổ chức lại, không xóa marker để nghe lại.
- JavaFX chỉ render/MediaPlayer. Worker ghi timestamp local, gửi STARTED, heartbeat 2 giây và COMPLETED qua HTTP để nhận status sau COMMIT. Thời điểm local không thay server clock và không cam kết đồng bộ sample giữa máy.
- Câu hỏi được nhóm đúng tên file manifest và sắp theo orderIndex; thiếu mapping chặn phát. Media/network failure khóa ngay, còn event INTERRUPTED/status/reconnect thuộc T3-B3. [Hướng dẫn](LISTENING_CLIENT_PLAYBACK.md), [bằng chứng](../evidence/t3-b2/2026-10-14-verification.md).

## QD-28 · MSI Windows per-user từ app-image đã sửa manifest Unicode

- Ngày 15/10/2026 · T3-B5 · Human A review NOT RUN. Dùng jpackage JDK21 + WiX3.14.1 để tạo MSI thực, không gọi app-image là installer; tái sử dụng manifest UTF-8 B1 bằng Microsoft SDK mt.exe. Tool portable được đặt trong ignored target, không đổi JDK/dependency/locale toàn hệ thống.
- MSI cài client/runtime/JavaFX cho tài khoản hiện tại; server/PostgreSQL/audio và credential tách riêng. URL server cấu hình ở login hoặc environment. Cache/journal mặc định LOCALAPPDATA, app đọc thư mục cài; gỡ MSI giữ journal chống replay. FULL mặc định, không đổi contract/backend hoặc ghép PR35.
- Build từ Git sạch, metadata commit/version/dirty/tool/JAR hash và sidecar MSI SHA-256. JAR/staging chỉ chứa runtime production; external TEST agent nằm ngoài installer. Kiểm local bằng tài khoản không elevated với ACL deny write, Unicode install, DLL runtime thực, UI/audio và gỡ. [Hướng dẫn](WINDOWS_INSTALLER.md), [evidence](../evidence/t3-b5/2026-10-15-verification.md). Máy hai/nghe thật/LAN/review còn riêng, IT01 PARTIAL.

## QD-23 · Lịch sử lượt thi tách khỏi scope monitoring, phân trang theo attemptId

- Ngày: 2026-10-12 · Người quyết: A (phiên Agent, theo đề xuất của B) · Review C: NOT RUN.
- Lựa chọn: thêm endpoint đọc riêng `GET /api/v1/candidate/attempts` để thí sinh khám phá lượt thi của mình, thay vì nới `auth/me.attemptScope`. Sắp xếp và phân trang keyset theo `attemptId` với `COLLATE "C"`; `limit` 1..100, mặc định 50.
- Lý do: `attemptScope` là quyền monitoring và ghi, chỉ gồm lượt `ACTIVE`; đưa lượt đã chốt vào đó sẽ mở lại quyền gửi heartbeat/event cho lượt đã chốt. `attemptId` là khóa duy nhất có sẵn nên không cần migration hay cột thời gian mới. Collation mặc định của DB là `en_US`, sắp `a` trước `B`; client so bằng `String.compareTo` nên server phải ép thứ tự byte.
- Hệ quả: thứ tự không phải "mới nhất trước"; không có snapshot xuyên trang. Lượt đã chốt thiếu `submitted_at` bị ẩn. Danh sách chỉ để tìm ID; kết quả vẫn đọc qua `GET /status` và được kiểm quyền lại ở đó.
- Kèm theo: route API không tồn tại trả 404 `INVALID_INPUT` thay cho 500. `ErrorCode` không có mã riêng cho "không tìm thấy" và không thêm mã mới để khỏi đổi contract lỗi v0.
- Kiểm chứng: `ExamRuntimePostgresTest` (PostgreSQL thật) và kiểm chéo với client B `770c896`; `evidence/t2-a-history/2026-10-12-verification.md`.
- Liên quan: B-ACCEPT-01, T2-A4, T2-B4, AT01, AT11.

### QD-23 · Bổ sung phạm vi cursor — 13/10/2026

Theo yêu cầu người dùng, validator cursor nhận nguyên giá trị không rỗng, không NUL, tối đa128 Unicode code point như PostgreSQL `VARCHAR(128)`, thay regex ASCII để không từ chối cursor do server phát. Không trim/normalization, SQL parameter binding và byte UTF-8/C order giữ nguyên. B phải URL-encode và đồng bộ validator/ordering ở lượt riêng; không sửa client trong fix A này. Không đổi schema hoặc API tạo ca; vượt128 do ghép ID là rủi ro riêng đã tái hiện500+rollback. Regression PostgreSQL mới có mixed-ID/limit1,2,3/foreign owner/biên128-129/NUL; code `00b1df1`, Maven test/package638 và probe11/11 PASS. Evidence local `target/pr28-cursor-20261013/REPORT.md`; human review và GUI NOT RUN.


## QD-30 · Một owner writer cho Reading và Listening trong màn thi

Ngày15/10/2026 · Nhánh integration/reading-listening-20261015 · Theo task.txt; human A/B/C review NOT RUN.

ExamEntry cấp writer cho lần vào ACTIVE; CandidateExamView sở hữu ExamSession và truyền đúng epoch cho phần audio cùng attempt. Không có audio takeover hoặc autoplay khi đọc status/reconnect/history. Callback Media/API có generation và guard đóng view. Khi writer/state không hợp lệ, đóng audio/worker/subscription; reclaim rõ ràng mới cấp writer khác. Gián đoạn Listening được đối chiếu với server rồi chỉ phần Listening trong full-map bị giữ theo bản đã commit; Reading không mất thay đổi và revision không giảm. Query status alias xử lý slash như dữ liệu, giữ Tomcat path policy và service authorization. A review route/scope, B review view/session/Media/lifecycle; C review collector/realtime hooks. Không thay transaction/schema/delta default.

## QD-29 · Contract ID ACTIVE đặc biệt còn chờ A/B/C

Ngày15/10/2026 · ĐANG CHỜ, không coi là đã duyệt.

History/DB nhận raw Unicode/space/slash tối đa128 code point; monitoring v0 đang dùng ASCII cho attempt envelope/heartbeat. Phiên tích hợp sửa scope HTTP để không làm login/history thất bại, thêm query đọc status và giữ giới hạn WS hiện có. UI không mở một bài ACTIVE không giám sát. Cần A/B/C chọn: giới hạn/chuẩn hóa ID khi cấp lượt bằng quy tắc mới có kiểm thử, hoặc mở rộng riêng typed attemptId qua toàn bộ monitoring/audio endpoints. Không tự mở rộng messageId/collectorId, không nới container toàn server. Rủi ro sessionId+username vượt128 và sai HTTP method trả500 giữ ở backlog A, ngoài tích hợp P0.

Ghi chú tích hợp: main dùng QD-28 cho MSI; PR #35 từng dùng cùng số cho shared writer. Trong bản thống nhất, shared writer mang QD-30, QD-28 giữ MSI, QD-29 giữ contract ID. Evidence cũ được đọc theo SHA/ngữ cảnh gốc, không sửa nội dung lịch sử. Human A/B/C review vẫn chưa thực hiện.

### QD-29 — Overnight safe issuance policy (PROPOSED, NOT HUMAN APPROVED)

Nhánh overnight từ PR35 kiểm toàn bộ ID `sessionId + '-' + original username` theo monitoring-v0 trước cấp ACTIVE; từ chối nguyên request thay vì transliterate/trim. Reorganize cùng boundary, collision không làm sống lại attempt cũ. Raw Unicode/slash legacy vẫn đọc history/query-status; không mở rộng WS/container. Đổi lại username/session Unicode có thể bị từ chối cấp mới. API progress read-only là đề xuất để A/C review, không tự đồng thuận A/B/C.

### Overnight E1 — phương án kỹ thuật nối QD15, HUMAN C REVIEW PENDING

E1 dùng same-seed/test-owned policy và cùng launchplan cho4poll, xoay thứ tự qua3repeats; window15s+120s chỉ gồm lifecycle nằm trọn window. Parent launchAPI→scan-end là cận trên, không kernel timestamp; targethold khác actual lifetime. CPU collector/parent JVM mộtcore riêng, memory=heap parent (không RSS). Suite dùng cùng parent JVM nên GC/JIT/heap carryover và background workstation là giới hạn; chưa human approve phương pháp hoặc gate. Raw bất biến ở jpackage-out, public chỉ derived/noPID + provenance. Không đổi FULL default/QD19 hay bật delta. [Evidence](../evidence/overnight-20261015/phase-06/verification.md).


## Tích hợp16/10/2026 — chọn nền B4/MSI

Giữ controller/API/view B4#36 tại client.proctor và pipeline MSI#37, chỉ chuyển guard/progress/config/preflight cần thiết từ38; bỏ workflow/controller38 và demo seeder trùng. Một ExamSession sở hữu writer; progress/history không cấp writer. QD28 MSI/QD30 writer, QD29 và E1-method human review pending. TEST C1/C2 chỉ harden null-safe cleanup và FX20s; C1 cuối PASS, các FAIL profile cleanup ban đầu giữ log và còn cần C review độ ổn định. [Quyết định conflict và giới hạn](../evidence/unified-20261016/conflict-resolution.md). Không tự đồng thuận owner hoặc đổi default FULL.


### QD-29 — đề xuất trước RC17/10 (PROPOSED_FOR_A_C_APPROVAL)

Chưa APPROVED và chưa owner ký. [Decision matrix](../evidence/integration-final-20261017/DECISION_MATRIX.md) đề xuất fail-closed issuance, legacy ACTIVE bị chặn trước vào/resume thi và hướng dẫn cấp lượt mới, thông báo client/regression/dataset cần A/C quyết định. Không tự migrate/cancel legacy hoặc mở rộng protocol; takeoverWriter vẫn không idempotent theo requestId. A cần review riêng history5001/HTTP500 retryable của PR43; B quyết định policy root/junction của PR45. FULL mặc định, không chốt DELTA production.


## Ổn định hóa 17/10/2026 — ĐÃ CÀI, CHỜ OWNER DUYỆT

Bốn mục dưới là khuyến nghị kỹ thuật đã cài và test trên nhánh `stabilize/final-rc-20261017`. Không có review/comment nào của A/B/C trên PR39–45 khi kiểm (GitHub API, 17/10). Trạng thái từng mục là IMPLEMENTED; chỉ đổi thành OWNER_APPROVED khi owner xác nhận thật. Bằng chứng: `evidence/stabilize-20261017/REPORT.md`.

### QD-31 · Thực thi QD-29: cấp ACTIVE fail-closed, legacy ACTIVE chỉ đọc
- Trạng thái: IMPLEMENTED · Cần: A và C duyệt, B xem thông báo.
- Policy cấp mới (giữ như đề xuất QD-29): attemptId sinh ra `sessionId + '-' + username` phải thuộc `[A-Za-z0-9_.:-]{1,128}`; kiểm cả batch trước mọi mutation; không trim/transliterate; create và reorganize dùng cùng `createSession`; va chạm với ID đã có (kể cả FINAL) → 400, không mở lại bài cũ.
- Mới: tạo ca đồng thời cùng `sessionId` trước đây làm request thua nhận 500 unique violation; nay `ON CONFLICT DO NOTHING` → 400 `INVALID_INPUT`, không mutation (test latch PostgreSQL: bỏ fix thì 500).
- Legacy ACTIVE (ID đã lưu ngoài monitoring v0): status theo path/query và history vẫn 200 theo quyền. takeover, autosave, submit, audio READY → 409 `INVALID_STATE` `retryable:false`, message cố định `AttemptIdentifier.MONITORING_INCOMPATIBLE_MESSAGE`. Kiểm sau row lock và sau ownership/state, nên thí sinh khác vẫn nhận 403 và không biết lượt có tương thích hay không; không đổi writerEpoch, deadline, đáp án, state.
- Hệ quả: lượt legacy không cấp được writer và không READY, nên ca có lượt legacy không start Listening được (fail-closed). Lượt legacy đã có deadline sẽ TIMED_OUT theo timeout thường với đáp án đã lưu; lượt chưa vào (deadline null) giữ ACTIVE cho audit. Thay thế: giám thị tạo ca mới với `sessionId` ASCII. Nếu chính username ngoài ASCII thì không cấp được lượt mới nào cho tài khoản đó — giới hạn đã biết; đổi tên tài khoản nằm ngoài phạm vi, không tự migrate/cancel dữ liệu.
- Client: thông báo trước khi vào thi dùng cùng hằng; ExamEntry gặp 409 INVALID_STATE đọc lại status một lần rồi dừng, không lặp.
- Test: `ExamRuntimePostgresTest.qd29*` (biên 128/129, Unicode/space batch không mutation, role 401/403, va chạm đồng thời, legacy ACTIVE/FINAL, thay thế), `ExamPaperAndEntryTest.legacyActiveRejectedByServerEndsEntryWithoutLooping`, `ExamMonitoringIntegrationTest`.

### QD-32 · Lịch sử giám sát vượt 5000 dòng: 409 không retry, không cắt bớt
- Trạng thái: IMPLEMENTED · Cần: A (HTTP/contract) và C (monitoring) duyệt.
- Bối cảnh: PR43 giới hạn `events`/`interruptions` 5000 dòng nhưng trả 500 `RETRYABLE_SERVER_ERROR` `retryable:true`; retry không bao giờ làm lịch sử ngắn lại. `gaps` có cùng giới hạn mà chưa có test.
- Lựa chọn: một helper `MonitoringHistoryLimit` cho cả ba endpoint: `LIMIT 5001`; ≤5000 trả đủ; >5000 → 409 `INVALID_STATE` `retryable:false`, message cố định, không danh sách một phần. Dùng mã enum có sẵn để client cũ không gặp giá trị lạ. Quyền và scope kiểm trước query như PR43.
- Đã cân nhắc: phân trang cursor (đúng hơn về lâu dài nhưng thêm protocol sát hạn đóng băng 27/10, và không có ca thi 2 giờ nào gần 5000 event trong dữ liệu đo); 413/422 (không khớp quy ước 409 cho state đang có của repo).
- Client: 409 từ ba endpoint → `HistoryLimitException`; dashboard hiện thông báo riêng, giữ danh sách đủ gần nhất, đánh dấu stale. Trần body dashboard 4 → 8 MiB vì 5000 event ở độ dài trường tối đa ≈4,3 MB từng bị coi là response không hợp lệ.
- Test: `MonitoringHistoryBoundsPostgresTest` (0/1/5000/5001 cả ba endpoint, 401/403/assignment khác, thứ tự), `MonitoringApiClientTest`, `DashboardControllerTest`.

### QD-33 · Cache audio: hợp nhất fix race PR40 với policy junction, thay PR45
- Trạng thái: IMPLEMENTED · Cần: B duyệt và đóng PR45 là superseded; A xem CI.
- Root cause PR45: commit `a652482` dựa trên main nên mất `catch FileAlreadyExistsException` của PR40 → probe 16 reader × 8 vòng × 5 lần: 112/640 lỗi; bản mới 0/640. Root cause lỗi CI mà PR45 nhắm tới: RC kiểm `toRealPath()==path` cho mọi ancestor từ gốc ổ đĩa nên từ chối root nằm dưới junction hoặc tên 8.3 (test mới tái hiện trên RC cũ).
- Lựa chọn: không kiểm ancestor phía trên root cấu hình; root và mọi thư mục con phải là thư mục thường (không symlink, không junction — Java báo junction là `isDirectory && isOther` — không reparse point) và `toRealPath` phải bằng `realRoot` + cùng tên con; tạo song song được bắt và kiểm lại. File partial chỉ move thành file thật sau khi đủ byte và đúng SHA-256.
- CI: cherry-pick riêng commit workflow Windows của PR45 (`c1e3405`, giữ tác giả) + trigger push cho `stabilize/**`. Windows Maven test không thay MSI installed FullSuite.
- Giới hạn: kiểm rồi mới ghi nên vẫn có cửa sổ TOCTOU nếu một tiến trình cùng user thay thư mục giữa hai bước; mô hình đe dọa của đồ án không gồm tiến trình độc hại cùng user.
- Test: `AudioFilesTest` (stress, ancestor junction, ancestor 8.3, junction ở/ dưới root, root bị thay, symlink — skip khi thiếu quyền tạo symlink, hủy tải).

### QD-34 · Takeover không idempotent theo requestId — giới hạn có chủ đích
- Trạng thái: PHÂN TÍCH + TEST, không đổi code · Cần: A và B xác nhận.
- Hành vi thực (test PostgreSQL): gửi lại cùng request sau khi server đã commit cấp epoch mới (epoch+1); writer cũ STALE; answers, savedRevision, deadline giữ nguyên; bài đã nộp trả 409 và không đổi.
- Client: không tự gửi lại takeover. Mở lại phòng thi hoặc bấm "lấy lại quyền ghi" tạo requestId mới; Java HttpClient không tự retry POST. Mất ACK thì người dùng thấy lỗi và thao tác lại, kết quả giống hệt một takeover mới.
- Kết luận: không có lỗi correctness (không mất đáp án đã ACK, không reset deadline, không mở lại bài chốt). Thêm idempotency theo requestId cần bảng/cột mới — không làm sát hạn đóng băng. Rủi ro còn lại: một writer hợp lệ bị thay thêm một lần khi chính người dùng mở lại, đúng ngữ nghĩa takeover.
- Test: `ExamRuntimePostgresTest.takeoverReplayAfterLostAckAdvancesEpochButNeverLosesAnswersDeadlineOrFinal`, `ExamPaperAndEntryTest.lostTakeoverAckIsNeverRetriedAutomaticallyAndReopenUsesAFreshRequestId`.


### QD-33 · Corrective publication S3-P1-01, 18/10/2026
Trạng thái: IMPLEMENTED_LOCAL tại56a27e9, review kỹ thuật Codex theo chỉ định người dùng trong phiên; không OWNER_APPROVED A/B/C và chưa merge main. QD33 ban đầu chỉ sửa directory race, không đủ để đóng P1 publication phát hiện ởPR55.

Giữ nguyên cache đã đúng size/SHA; chỉ repair missing/known corrupt dưới khóa directory manifest. `file()`, verify và publication chia64striped JVM locks + sidecar FileLock liên process, không giữ khóa trong HTTP. Sidecar NOFOLLOW/plain file, giữ lâu dài để không split lock khi process khác chờ; cancellation interruptible. Không dùng ATOMIC_MOVE đơn lẻ; không replace valid file đang phát. Native FileLock là cooperative, chỉ xác minh local Windows với cùng phiên bản mới; network share/client cũ và malicious same-user TOCTOU không được nhận bảo đảm. Partial được dọn sau failure/cancel; hard kill giữa download ngoài bảo đảm cleanup.

[Trước/sau, giới hạn, lệnh tái lập](../evidence/audio-cache-p1-20261018/REPORT.md). B còn dispositionPR45; không tự đóng45. Tracker không đổi.


### 19/10/2026 · review kỹ thuật do người dùng giao và release FULL
Người dùng chỉ định Codex reviewer và cho phép push hai nhánh mới/PR/CI. PR47 mergecd9a89d, PR57 merge8a43a56 sau kiểm source/test/CI đúngHEAD. Agent technical review QD31/32/34 theo giới hạn đã công bố, QD33 theo corrective57; không gán chữ ký A/B/C hoặc actualhours. FULL giữ baseline theo hợp đồng; DELTA chưa được promote, C/B gatephysical còn pending. Lượt localC4FAIL được giữ và sửa harness backoff1072f77, không thay production retry. MSI0.3.10/ZIP3044 là lịch sử, cần artifact mới ràng buộc source cuối.


19/10/2026 — Giữ close() production không chặn FX; lifecycle test phải chờ cleanup sample trước khi JUnit TempDir thu hồi thư mục. Không dùng retry cả CI để che failure. Review kỹ thuật Codex theo ủy quyền; không cấp chữ ký A/B/C hay nghiệm thu vật lý.


### 19/10/2026 · Kết luận kỹ thuật Phase3 sau corrective

QD31/32/34 giữ policy và giới hạn hiện hành; QD33 corrective57 đã main, stress1280/1280/0partial và sharing thật. Technical review Codex theo ủy quyền người dùng; không owner signatures A/B/C hoặc tự đóngPR45. Buildsourcecfbba58 CI+PG+MSIlocal PASS; FULL là baseline theo hợp đồng, DELTA experimental. Gate C quyết định/B review sau physical41; hearing/máy2 vẫn NOT RUN. Không sửa tracker/hours hoặc cấp STAGE3_DONE. [Evidence](../evidence/stage3-execution-20261019/REPORT.md).

## QD-35 · Schema rich content cộng thêm, giữ tương thích API V5

Ngày 19/10/2026 · Trạng thái: IMPLEMENTED, chờ A/B review importer và renderer.

- Dữ liệu `seedingTest` có 200 câu, 42 group dùng chung, audio/ảnh và passage nhiều block; schema V5 lặp `passage_text`/`audio_file` trên từng câu và không lưu provenance/media verification.
- Thêm Flyway V11 thay vì sửa V1–V10: `exam_sources`, `exam_question_groups`, `exam_media_assets`, `exam_content_blocks`; backfill group cũ và thêm FK/unique/check cho thứ tự, section/part và correct option.
- Seed Test 01 sinh từ HTML chứng minh `order_index` không đủ thay cho provenance. Vì vậy câu có `source_id` + `source_question_number`; source/group/question lưu riêng trạng thái xác minh answer key, mapping và media. Có `correct_option` không được hiểu là đã `VERIFIED`.
- Binary media tiếp tục ở filesystem. `REMOTE_REFERENCE` chỉ là inventory; `LOCAL_VERIFIED` bắt buộc local file/MIME/bytes/SHA-256. PostgreSQL chỉ giữ metadata.
- Giữ cột V5 và API v0 hiện tại để không phá client. Service import tạo group normalized tối thiểu từ DTO cũ; provenance/content blocks/media và renderer rich content là lượt riêng. JSON hiện là **seed candidate** có thể tái tạo, nhưng `importable:false` và không phải dataset READY cho tới khi các blocker được xác minh.
- Không đổi wire protocol, endpoint hoặc default FULL/DELTA. Xem [schema](DATABASE_SCHEMA.md) và [API backend](BACKEND_API.md).

Ghi chú kiểm chứng19/10/2026, nhánhreviewEX04 chưamerge: [reportPG](EX04_V11_VERIFICATION.md) tái hiệnV11NULL-type2FAIL; V12append-only thêmCHECK để thực thi đúng typed-media invariant trên. Fresh/upgrade/rollback/legacyHTTP vàcleanverify đãchạy; không sửaV1–V11 hoặc đổiQD21. [EX02/START/media proposal](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md) vẫnCHƯA DUYỆT; không xem đây là quyết định schema/API/deadline mới hoặc chữ kýA/B/C.


## MON-07 · Ủy quyền thử nghiệm người dùng, 19/10/2026 (không chữ ký owner)

Nguồn: yêu cầu trong phiên cho phép mục11.1 PR72 tại c9a5829, D07-01–04 **chỉ MOCK DISPLAY_COUNT_OBSERVED → REAL WS → PostgreSQL TEST → commit/ACK → paged history**; no observer/no real capture/no push/dashboard, teardown TEST. [Phạm vi và giới hạn](../evidence/v2-c-mon07-signal-pipeline/REPORT.md). Ghi USER_AUTHORIZED_EXPERIMENT, không chuyển D07 thành human APPROVED hoặc ký A/B/C. Offline60s/retention30ngày vẫn PENDING/chưa áp dụng; FULLdefault/gate/hardware không đổi.

## EX-02 / ADR-V2-03 / D10 — nhóm thông qua theo đại diện, 19/10/2026

**TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE**: [D01–D10](plan-v2/EX02_DECISIONS_ROADMAP.md), [contract](EX02_RICH_EXAM_CONTRACT_V1.md), [ADR](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md). [Nguồn19/10](plan-v2/EX02_TEAM_APPROVAL_20261019.md): “User-provided confirmation from the project team representative, 2026-10-19.” A/B/C đồng ý theo đại diện nhóm, không chữ ký riêng hoặc GitHub Approving Review. Chọn Option A: owner prefetch đúng ảnh Part1 trước START (chấp nhận chủ máy có thể trích xuất bytes), không prompts/options/keys/passages; whole roster Audio+Visual READY current epoch/lease trước Listening START duy nhất. Server clock/deadline tại START, ảnh visible gate trước play; lỗi sau commit interruption/no replay/no deadline reset. Ca legacy giữ QD21, không infer version bằng deadlineNULL. Đây phê duyệt thiết kế, không migration/Java/runtime/GUI PASS hoặc EX03–08 DONE.


## EX-01 — Lựa chọn triển khai công cụ offline, 20/10/2026

Pipeline Java21 độc lập dùng jsoup DOM/Gson và JLayer để decodeMP3 offline; không thêm dependency vào server/client. Giữ review explanation tách khỏi options; Part1/2 chỉ nhãn, không transcript. Group singleton lấy từ article/audio thật; document Part6/7 không suy boundary từ ảnh khi chưa review. Media ETL chỉ URL public đã có xác nhận quyền, receipt pin checksum, không auth/redirect/hotlink hoặc binary Git; sourceURL chỉ provenance. Không đổi contract/Flyway/START/monitoring; A/B review nội dung còn PENDING. [Evidence](ex01-html-pipeline/REPORT.md).


## EX-01 — Recovery evidence và archive/TEST restore, 20/10/2026

Tiếp tục Java ETL cũ: recovery giữ literalSOURCE_EXACT khác human/backendVERIFIED; tách teacher/Vietnamese/evidence khỏi candidate, không suy A–D từ vocabulary hoặc document boundaries từ ảnh. Nguồn bổ sung joinexamId+part+questionNumber và đối chiếu nhãn key. Không có HUMAN_VERIFIED thì draft vẫn UNVERIFIED/BLOCKED, không sửa validator.

Archive versioned ngoài Git giữ normalized/V2/recovery/provenance/receipt vàmedia<examId>/<localFile>, manifestsize/SHA; restore chỉ thư mục mới sau verify. SQL dùngpg_dump plainUTF8/psqltransaction, khôngINSERTtựviết/clean/drop; restore phải xác nhận đúng tên vàtựCREATE TESTmới+rỗng. MOCK thực chứng minh mechanics, không thay ETS acceptance. [Evidence và giới hạn](ex01-html-pipeline/BACKUP_RESTORE.md), [recovery](ex01-html-pipeline/RECOVERY_REPORT.md). Không quyết định mới vềschema/API/START hoặc quyền bypass.
