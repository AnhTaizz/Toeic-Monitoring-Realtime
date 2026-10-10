# TOEIC Monitoring Realtime

Spring Boot server, JavaFX client hai role, PostgreSQL và collector `ProcessHandle`. Event/queue/retry, heartbeat/presence/history và dashboard chặng 1 đã có. Server có backend thi chặng 2; T2-C1 thêm full snapshot và tab **Process hiện tại**. Delta, GUI thi toàn luồng và đóng gói trên máy thứ hai chưa được nghiệm thu. Các mục chặng 1 bên dưới mô tả phạm vi lịch sử của từng task; phần full hiện tại ở cuối.

## Yêu cầu môi trường

- Windows 11 x64
- JDK 21 (đã kiểm với Oracle JDK 21.0.8)
- Maven 3.9+
- Docker Desktop và Docker Compose

## Chạy nhanh

1. Chép `.env.example` thành `.env` và đổi `DB_PASSWORD`.
2. Khởi động DB: `docker compose up -d --wait`.
3. Nạp các biến `DB_*` từ `.env` vào terminal, rồi chạy server: `mvn -pl server -am spring-boot:run`.
4. Ở terminal khác, chạy client: `mvn -pl client -am javafx:run`.

Client đọc URL mặc định từ biến `TOEIC_SERVER_URL` hoặc system property `toeic.server.url`; người dùng cũng có thể sửa URL ngay trên màn đăng nhập. Server lắng nghe `0.0.0.0:8080` mặc định để máy khác trong LAN gọi được.

Tài khoản mẫu (`MOCK`, chỉ dùng phát triển) dùng `TOEIC_SEED_PASSWORD`. Mật khẩu mặc định nằm trong cấu hình server; hãy đặt giá trị riêng ở `.env` và không commit.

| Tài khoản | Role |
|---|---|
| `candidate1` | `CANDIDATE` |
| `candidate2` | `CANDIDATE` |
| `proctor1` | `PROCTOR` |

## Build, test và reset DB

- Build + unit test: `mvn test`
- Đóng gói toàn bộ: `mvn package`
- Reset DB phát triển: `powershell -ExecutionPolicy Bypass -File scripts/reset-db.ps1 -Force`
- Chạy probe: `mvn -q -pl monitoring-spike exec:java -Dexec.args="--limit=200"`

Lệnh reset xóa volume `toeic-pgdata`, vì vậy toàn bộ dữ liệu phát triển hiện có sẽ mất. Flyway tự dựng schema khi server khởi động lại.

Tạo Windows app-image sau `mvn package`:

```powershell
jpackage --type app-image --dest jpackage-out --name ToeicMonitor `
  --input client\target --main-jar client-0.1.0-SNAPSHOT-all.jar `
  --main-class vn.edu.toeic.client.Launcher --app-version 0.1.0
```

## Thư viện ngoài và lý do sử dụng

| Thư viện | Mục đích |
|---|---|
| Spring Boot Web/JDBC 4.1.1 | REST server và truy cập PostgreSQL bằng API JDBC tường minh |
| Spring Boot WebSocket 4.1.1 | Raw WebSocket /ws/v1/realtime, handshake auth và tuần tự hóa send; không STOMP/SockJS/broker |
| Spring Security Crypto | BCrypt cho mật khẩu mẫu; chưa bật full Spring Security ở T1-A1 |
| Flyway + PostgreSQL module | Migration schema có version, dựng lại được |
| PostgreSQL JDBC Driver | Kết nối PostgreSQL 18 |
| JavaFX Controls 21.0.12 | Một desktop app có giao diện thí sinh/giám thị |
| Gson 2.13.2 | Contract login/client và envelope/error WS server; C4 dùng lại trong protocol để lọc metadata và ghi JSONL, không thêm framework telemetry |
| JUnit 5 + AssertJ | Test đơn vị và test hành vi bất đồng bộ |
| Maven Shade Plugin | Tạo fat JAR đầu vào cho spike `jpackage` |

Chi tiết contract, quyết định và bằng chứng kiểm thử nằm trong `docs/`.

## Adapter realtime T1-B2 — đã tích hợp A2

`RealtimeClient` là adapter duy nhất cho raw `java.net.http.WebSocket`; `AuthenticatedWebSocketOpener` parse server origin bằng URI và mở `/ws/v1/realtime`, HTTP → WS, HTTPS → WSS. Mỗi reconnect gửi lại `Authorization: Bearer <token>`. Không dùng query, AUTH message hay subprotocol chứa credential; URL có user-info/query/fragment/path được từ chối bằng thông báo cố định. Không thêm dependency.

`Settings` cấu hình heartbeat (mặc định 2 giây), backoff (1/2/4/8 giây, cap 8 giây), tối đa 4 retry sau lần mở đầu, message tối đa 65.536 ký tự và tối đa 500 ACK/write đang chờ. Mỗi outage mới sau kết nối thành công có budget mới. 401, ERROR UNAUTHORIZED hoặc close 1008 → FAILED, dừng heartbeat/retry, bỏ token trong opener; phải login và tạo adapter mới. 503/network failure → bounded backoff. Hết retry cần đăng nhập lại; không tự retry vô hạn.

Sau login candidate hoặc proctor, JavaFX mở realtime bằng server URL của lần login và token trong memory. Session cho phép heartbeat không có attemptId/collectorSessionId khi scope rỗng. Callback UI qua Platform.runLater; logout/stop gỡ listeners, đóng socket/worker/HttpClient, bỏ reference token/context. PROCTOR mở dashboard B3; CANDIDATE giữ các nút giám sát C3/A4, nghiệp vụ bài thi chưa cài.

ACK HEARTBEAT phải khớp requestId/type/attemptId/traceId đang chờ; write success chưa phải ACK. ERROR WS được đọc từ payload trực tiếp `{code,message,retryable}`; client dùng thông báo cố định, không phản chiếu raw exception/JSON. C3 quản lý retry event và gap; B2 chỉ quản lý reconnect socket.

### Bàn giao cho C

Interface `vn.edu.toeic.client.realtime.MonitoringTransport`:

```java
CompletableFuture<Void> send(MessageEnvelope<JsonObject> message);
ConnectionState connectionState();
AutoCloseable onConnectionState(Consumer<ConnectionState> listener);
AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener);
```

- C dùng interface, không dùng raw WebSocket. Subscription trả AutoCloseable để gỡ listener; callback không bảo đảm FX thread.
- `send` hoàn thành khi socket write xong; ACK/ERROR đi qua `onMessage`. ACK pending được xóa khi disconnect, không replay event tự động. C3 quản lý eventId/queue/retry và đối soát business ACK.
- HEARTBEAT, PROCESS_OBSERVED và MONITORING_GAP đã nhận ACK server thật. Event/gap chỉ được ACK sau COMMIT. T2-C1 thêm OPEN/FULL/CLOSE/MONITOR_STATE theo contract ở cuối PROTOCOL; ACK FULL xác nhận state RAM, delta chưa có.
- C2 có thể dùng trạng thái kết nối và ranh giới transport; việc nối collector không được triển khai trong B2.

Unit tests dùng MOCK socket/clock ghi nhãn; HTTP handshake tests chạy network với fixture response 401/503. Real smoke riêng dùng PostgreSQL + production Spring server + chính RealtimeClient B, kiểm candidate/proctor, heartbeat ACK, stop/restart server, revoke/expire, retry budget và shutdown:

```powershell
docker compose up -d --wait
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-b2.ps1
```

Script đọc .env vào process, không reset DB, không in credential; harness chỉ stop server process và sửa/xóa session do nó tạo. Cần Docker Desktop để cập nhật session dev bằng psql trong container `toeic-db`. Có thể truyền `-JavaHome <thư mục JDK 21>` khi Java chưa có trong PATH/JAVA_HOME. Evidence: `evidence/t1-b2/2026-10-04-real-integration.md`. GUI manual/LAN máy thứ hai chưa chạy; headless smoke không thay bằng chứng GUI.

## T1-A2 — REST và WebSocket có xác thực

- `POST /api/v1/auth/login` vẫn public. `GET /api/v1/auth/me` và mọi route `/api/**` sau login cần `Authorization: Bearer <token>`.
- WebSocket: `ws://<server>:8080/ws/v1/realtime` (dùng `wss://` khi server triển khai TLS). Header handshake cũng là `Authorization: Bearer <token>`; endpoint không nhận query. Không log/password/token trong URL. Kết nối mới/reconnect phải gửi lại credential.
- Server lookup SHA-256 trong `login_sessions`, kiểm expires/revoked/user enabled; WS kiểm lại mỗi message. 401 là thiếu/sai/hết hạn/revoked credential, 403 là sai role/scope. Session bị vô hiệu trên WS nhận ERROR UNAUTHORIZED rồi đóng 1008.
- `AuthenticatedUser`/principal lấy từ DB; `AuthorizationService.requireRole` và `requireAttempt` chạy trước nghiệp vụ. A3 dùng assignment JDBC: candidate sở hữu/proctor được phân công vào attempt ACTIVE. Không có assignment thì scope rỗng; không tự cấp lượt khi login.
- Heartbeat không có attemptId là ping transport cho phiên authenticated, chưa chứng minh monitoring. A4 scoped heartbeat bắt buộc candidate ACTIVE scope và collectorSessionId, ACK sau presence COMMIT; event/gap ACK sau COMMIT tương ứng. State/delta chưa cài.
- Envelope mẫu, field bắt buộc, ACK/ERROR và handoff cho B2 ở [PROTOCOL](docs/PROTOCOL.md). B cần cho phép heartbeat/ACK unscoped, cài opener header auth, xử lý ERROR và auth lại khi reconnect. B2 đã consume contract trong phiên riêng sau khi PR #2 merge; thao tác Git cuối xem report.
- WS giới hạn message/buffer 65.536 byte và send timeout 5.000ms qua `WS_MAX_MESSAGE_BYTES`, `WS_SEND_BUFFER_BYTES`, `WS_SEND_TIMEOUT_MS`. Tất cả send đi qua `ConcurrentWebSocketSessionDecorator`.

Kiểm thử mặc định có real HTTP/WS trên Spring/Tomcat và `java.net.http.WebSocket`, dùng session/scope store **MOCK** (không H2). Smoke PostgreSQL riêng dùng toàn bộ app production và JDBC/Flyway thật:

```powershell
docker compose up -d --wait
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a2.ps1
```

Script đọc DB/seed variables từ `.env` vào process, không in credential, không reset DB; harness chỉ xóa login session do chính smoke tạo. Test account seed là MOCK/dev only. Script dùng timezone UTC và port random local để không chiếm port server đang chạy. Nếu Java chưa có trong PATH/JAVA_HOME, truyền `-JavaHome <thư mục JDK 21>` cho script. Không thay đổi file `.env`.

## Collector local T1-C2

`client/.../monitoring/ProcessCollector` cung cấp start(context, snapshotListener, problemListener), stop(), close(), latestSnapshot(). Start trả collectorSessionId UUID mới; callback trên worker `toeic-process-collector`, consumer UI tự marshal bằng Platform.runLater. `ScheduledThreadPoolExecutor` (ScheduledExecutorService) dùng scheduleWithFixedDelay, mặc định 1.000ms; constructor nhận Duration để đổi interval (250/500/1.000/2.000ms). Không thêm dependency và không sửa monitoring-spike C1.

Gate chỉ cho CANDIDATE + monitoringActive=true. C3 lấy attempt từ server và chỉ bật khi người dùng chọn/bắt đầu; login đơn lẻ không đủ. Context active trong smoke C2 vẫn là MOCK rõ nhãn; smoke C3 dùng assignment TEST thật ở schema riêng.

Source production gọi ProcessHandle.allProcesses, đọc command/startInstant và availability của user. Chỉ giữ executable filename, không command line/arguments/full path/username; policy QD-08 so filename case-insensitive với đúng 8 tên v1. Thiếu command/start/user → UNREADABLE, unknown command chỉ vào diagnostic count, không diễn giải sạch. PID0 idle của Windows được tính UNREADABLE khi metadata thiếu thay vì làm fail enumeration.

Snapshot immutable chứa collectorSessionId, policyVersion, observationNanos/scanDurationNanos, restrictedProcesses và diagnostic counts. ProcessIdentity = collectorSessionId + pid + startInstant nullable; thiếu startInstant vẫn có giới hạn PID reuse. Monotonic nanoTime chỉ dùng trong JVM này, không chứng minh clock sync. Poll lỗi không publish snapshot sạch: clear latestSnapshot và báo SOURCE_FAILURE; poll sau vẫn chạy. Listener lỗi báo LISTENER_FAILURE, không kill scheduler.

stop hủy task, interrupt scan, shutdown worker và bỏ listeners/latest snapshot. Future stop chỉ hoàn thành khi poll/callback đang chạy đã kết thúc; caller await ngoài FX thread. Không restart khi worker cũ chưa kết thúc; close là terminal. Source tự cài không đáp ứng interrupt phải trả về trước khi stop future hoàn thành, không giả dừng worker bằng cách mở worker mới.

C3 consume ProcessSnapshot và so theo ProcessIdentity, không so toàn bộ observation (quality có thể đổi). ProcessCollector C2 vẫn độc lập mạng; coordinator/delivery riêng tạo event/queue/retry.

```powershell
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c2.ps1 -JavaHome <JDK21>
```

Smoke C2 dùng source production và context active MOCK; mở Edge headless bằng profile tạm riêng, chỉ đóng process tree do harness tạo. Log không full path/user/arguments. Evidence: `evidence/t1-c2/2026-10-04-verification.md`. C3 bổ sung real event delivery bên dưới; B3 component GUI được kiểm riêng, MT01 toàn case còn PARTIAL.

## T1-C3 — bật giám sát và gửi sự kiện

Candidate chọn lượt được cấp → “Bắt đầu giám sát” → client kiểm `/api/v1/auth/me` lại → collector chạy. Scope rỗng hiển thị “Chưa được cấp lượt giám sát”, không scan/worker/queue. Proctor không có collector/queue candidate. “Dừng giám sát” đếm rồi bỏ dữ liệu chưa xác nhận, chờ worker cũ kết thúc trước khi chọn lượt khác. Logout/đóng app ghi số bỏ vào console. Queue chỉ ở RAM: kill app không phục hồi được.

DEV/TEST demo có chủ động cấp `DEMO-C3-A` cho `candidate1`, giám thị `proctor1`. Khởi động DB/server trước để Flyway áp dụng V3 và seed tài khoản; script không tự chạy từ server/login. Không tạo ca thi production:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo-c3.ps1 -Action Create
java '-Dtoeic.monitoring.pollMillis=500' -jar client\target\client-0.1.0-SNAPSHOT-all.jar
```

Đăng nhập candidate1 bằng mật khẩu seed cấu hình local, kiểm tra lượt thi, chọn DEMO-C3-A, bắt đầu. Mở Microsoft Edge (`msedge.exe`, thuộc policy v1), chờ vài poll. Mong đợi số “Đã xác nhận” tăng rồi giữ nguyên khi process vẫn tồn tại. Đóng/mở lại có thể tăng số event; Edge tạo nhiều process nên không mặc định mỗi cửa sổ chỉ một event. Notepad không nằm trong policy v1. Đăng nhập proctor1 trên client thứ hai, chọn DEMO-C3-A để xem dashboard B3 (server cần V4). Sau demo dừng giám sát, rồi cleanup chủ động (xóa lịch sử chỉ của DEMO-C3-A, giữ tài khoản/lượt khác):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo-c3.ps1 -Action Cleanup
```

Create từ chối attempt đã tồn tại; Cleanup từ chối nếu owner/assignment khác fixture. Tham số Schema chỉ dành public hoặc schema TEST do harness tạo. Kiểm thực tế GUI các bước trên: NOT RUN.

| Property `toeic.monitoring.*` | Mặc định | Ý nghĩa |
|---|---:|---|
| pollMillis | 1000 | Chu kỳ quét C2 |
| queueCapacity | 500 | Tất cả event chưa xác nhận, gồm failed/exhausted |
| inFlight | 4 | Tổng event + gap chờ ACK; nên nhỏ hơn ACK slots B2 |
| ackTimeoutMillis | 5000 | Chờ ACK mỗi lần gửi |
| maxAttempts | 5 | Tổng lần gửi, gồm lần đầu |
| initialBackoffMillis / maxBackoffMillis | 1000 / 8000 | Đợi retry, tăng gấp đôi tới giới hạn |

Ví dụ PowerShell: `java '-Dtoeic.monitoring.queueCapacity=10' '-Dtoeic.monitoring.inFlight=2' -jar client\target\client-0.1.0-SNAPSHOT-all.jar`. Queue nhỏ hơn 4 phải giảm inFlight cùng lúc. Một timer delivery 25ms; baseline tối đa 10.000 identity, snapshot vượt giới hạn báo SNAPSHOT_LIMIT và giữ baseline cũ. Adapter maps/writes có giới hạn riêng; C3 giải phóng correlation khi timeout/stop, B2 dành một slot cho heartbeat và hết hạn correlation heartbeat sau 3 chu kỳ. Không đảm bảo tiến trình xuất hiện rồi biến mất giữa hai poll sẽ được thấy.

Chỉ ACK đúng v0/request/attempt/trace/status/type mới xóa queue. ID, payload và UTC microsecond timestamps giữ nguyên khi retry. Retry hết lượt giữ event chưa xác nhận; nút “Thử lại chưa xác nhận” cấp lượt thử mới rõ ràng. CONFLICT/INVALID_INPUT dừng tự retry event đó; FORBIDDEN/UNAUTHORIZED dừng giám sát và yêu cầu dừng/kiểm quyền hoặc login lại. Overflow giữ cũ/drop mới, baseline vẫn cập nhật; gap riêng ghi số mất và khoảng thời gian client khai, không khôi phục event đã mất.

```powershell
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c3.ps1
```

Smoke C3 dùng production Spring/PostgreSQL/B2/C3/C2 Windows, schema TEST UUID riêng tự cleanup, Edge profile tạm riêng. Mất ACK SIMULATED ở observer của harness; overflow/reconnect snapshots MOCK, còn HTTP/WS/DB/ACK REAL. Không tắt/reset PostgreSQL chung. Server có dependency client **test-scope** chỉ cho harness, không đưa client vào server JAR production. Evidence: `evidence/t1-c3/2026-10-04-verification.md`. MT01 PARTIAL, GUI/LAN/human B review NOT RUN; T2-C2 gap/state/event muộn còn riêng.

## T1-A4 — heartbeat đúng phiên và presence

Login chỉ mở kết nối. Khi candidate chọn lượt ACTIVE và nhấn bắt đầu, coordinator lấy collectorSessionId thật từ C2 rồi gắn heartbeat B2 vào lượt đó. Mặc định gửi mỗi 2 giây và gửi đầu ngay nếu đã kết nối. Stop/logout/switch gỡ binding; reconnect cùng run giữ collector, collector đã Stop không tự bật lại. Ping không gắn attempt vẫn ACK nhưng không làm ONLINE.

Server nhận và commit heartbeat hợp lệ thì ONLINE, lưu lastSeenAt UTC. Không association nào còn heartbeat hợp lệ trong 6 giây thì UNKNOWN/HEARTBEAT_TIMEOUT, lưu một interruption. Quay lại ONLINE giữ lịch sử và cập nhật recoveredAt. Không có message STOP: dừng chỉ được server nhận ra khi hết timeout. UNKNOWN là mất xác nhận liên lạc, không phải cảnh báo gian lận hoặc process. Restart server chuyển ONLINE cũ thành UNKNOWN/SERVER_RESTART trước khi phục vụ; không bịa thời điểm ngắt hoặc tạo timeout gap giả.

| Cấu hình | Mặc định | Dùng cho |
|---|---:|---|
| JVM client `toeic.realtime.heartbeatMillis` | 2000 | Chu kỳ heartbeat |
| ENV server `MONITORING_TIMEOUT_MS` | 6000 | Deadline heartbeat |
| `MONITORING_TIMEOUT_SCAN_MS` | 500 | Chu kỳ quét |
| `MONITORING_PRESENCE_MAX_ATTEMPTS` | 4096 | Số attempt hiện được theo dõi trong RAM |
| `MONITORING_ASSOCIATIONS_PER_ATTEMPT` | 8 | Số socket còn được theo dõi mỗi attempt |

PowerShell phải đặt property Java trong dấu nháy, ví dụ:

```powershell
java '-Dtoeic.realtime.heartbeatMillis=2000' -jar client\target\client-0.1.0-SNAPSHOT-all.jar
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a4.ps1
```

Nếu server đang chạy giữ khóa JAR trên Windows, dừng server của mình trước khi đóng gói lại, hoặc build checkout riêng. Phiên A4 dùng checkout build riêng để giữ server/client người dùng đang chạy; ứng dụng đang chạy chưa tự chuyển sang code mới. Migration V4 được Flyway áp dụng khi khởi động bản mới, không reset database.

Smoke A4 dùng TEST schema UUID riêng, timeout700ms/scan25ms/heartbeat100ms; PostgreSQL/Spring/HTTP/WS/B2/C3 thật, nguồn process MOCK. Hai candidate ONLINE, hard-kill JVM do test tạo → A UNKNOWN trong khi B vẫn gửi event/ACK; proctor đúng assignment nhận presence, proctor khác không nhận; A quay lại giữ history. Có kiểm COMMIT failure thật, Stop/unscoped, reconnect/socket cũ, roster/history khi proctor offline, server restart và worker cleanup. Không kill Java/Edge người dùng, không tắt/reset DB chung. Smoke C3 vẫn kiểm ProcessHandle/Edge thật.

B3 đã dùng các endpoint và push A3/A4, giữ nguyên server schema. Schema và quy tắc revision/HTTP-WS recovery ở [PROTOCOL](docs/PROTOCOL.md). A4 server verification ở [A4 evidence](evidence/t1-a4/2026-10-04-verification.md); dashboard component verification ở [B3 evidence](evidence/t1-b3/2026-10-04-verification.md). Human review, GUI toàn luồng candidate/proctor và LAN máy thứ hai còn NOT RUN; nghiệm thu prototype PARTIAL.

## Dashboard giám thị T1-B3

Server phải chạy bản đã có Flyway V4. Server dev đang chạy JAR cũ cần được người dùng chủ động dừng và chạy lại bản mới; không xóa volume để nâng cấp. Xem `flyway_schema_history` trong đúng database của server. Build đang khóa JAR trên Windows có thể dùng checkout riêng; evidence B3 ghi checksum source/build tương ứng.

1. Tạo fixture DEMO-C3-A bằng script demo phía trên, sau khi server V4 đã khởi động.
2. Mở hai client. Candidate1 chọn lượt và bấm **Bắt đầu giám sát**; proctor1 đăng nhập sẽ mở **Giám sát thí sinh**.
3. Proctor chọn dòng DEMO-C3-A. Tab **Cảnh báo process** hiện “Quan sát thấy msedge.exe”. Hai cột giờ tách máy thí sinh quan sát và server nhận. Không lấy hiệu hai giờ này để suy ra độ trễ.
4. Tab **Lịch sử gián đoạn** hiện liên lạc cuối, server phát hiện và phục hồi. Dừng candidate rồi chờ timeout mặc định 6 giây: server gửi UNKNOWN và ghi history; bắt đầu lại: ONLINE, history vẫn còn.
5. Khi máy giám thị mất mạng, dashboard giữ trạng thái nhận gần nhất kèm **Dữ liệu cũ**. Khi kết nối lại, từng phần chỉ bỏ nhãn này sau khi HTTP đồng bộ xong. Nút **Làm mới quyền và dữ liệu** đọc lại phân công; server hiện chưa đẩy thay đổi assignment tức thì.
6. **Đăng xuất** trở về màn đăng nhập, bỏ dữ liệu và đóng worker/socket. Proctor không bật collector.

UTC được giữ trong model; giờ trên màn theo timezone máy, hoặc `"-Dtoeic.dashboard.timezone=Asia/Ho_Chi_Minh"`. Các property tùy chọn khác: `toeic.dashboard.httpTimeoutMillis=10000`, `toeic.dashboard.maxRows=5000`, `toeic.dashboard.pushBuffer=256`. Truyền property Java trong dấu ngoặc kép khi chạy PowerShell. Body HTTP giới hạn 4 MiB; vượt giới hạn sẽ báo lỗi/dữ liệu cũ, không tự coi lịch sử đầy đủ. Bộ đệm đầy yêu cầu HTTP đồng bộ; tự làm mới có ngân sách hữu hạn, hết ngân sách cần bấm nút. Client chỉ giữ chi tiết lượt đang chọn.

Kiểm tra tự động riêng sau `mvn package`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-b3.ps1
powershell -ExecutionPolicy Bypass -File scripts/smoke-b3.ps1 -Gui
```

Script đọc `.env` riêng, tạo schema TEST UUID/server cổng riêng và các JVM candidate do test sở hữu; kết thúc tự dọn. HTTP/WS/PostgreSQL/B2/C3/dashboard là REAL, nguồn quan sát process là MOCK. `-Gui` mở Stage JavaFX thật, chọn dòng/đổi tab/làm mới/đăng xuất bằng controls JavaFX, kiểm UNKNOWN sau hard-kill và ONLINE phục hồi, chụp ảnh vào `client/target/b3-dashboard-*.png`. Đây là kiểm component proctor; đăng nhập và thao tác candidate GUI đầy đủ, LAN/package máy khác và human A review vẫn cần kiểm riêng. Không thêm thư viện ngoài cho B3.

## Log đo monitoring T1-C4

C4 đếm UTF-8 của toàn JSON WebSocket (envelope + payload), tách TX/RX và ATTEMPTED/WRITE_COMPLETED/WRITE_FAILED/RECEIVED. ACK nghiệp vụ có record riêng không cộng byte lần nữa. Retry cùng event vẫn tính thêm lần gửi; tổng gửi chỉ cộng CLIENT TX + SERVER TX theo một outcome, không cộng RX hay attempted với completed. Không đo framing/TCP/IP/TLS/HTTP và chưa phải E1/E2.

Recorder mặc định tắt, dùng lại Gson và JDK; bật ở cả hai JVM với cùng runId. Ví dụ PowerShell sau khi nạp DB variables cho server:

```powershell
java "-Dtoeic.measurement.enabled=true" "-Dtoeic.measurement.runId=DEMO-C4" -jar server/target/server-0.1.0-SNAPSHOT.jar
java "-Dtoeic.measurement.enabled=true" "-Dtoeic.measurement.runId=DEMO-C4" -jar client/target/client-0.1.0-SNAPSHOT-all.jar
# Runtime JSONL ở logs/monitoring (ignored). Cần đóng app/server bình thường để flush FINAL.
python scripts/summarize-monitoring.py logs/monitoring --output logs/monitoring-summary
```

Demo verification tự động dùng PostgreSQL theo `.env`, schema TEST/cổng riêng và transport production của candidate/proctor, không reset DB hoặc kill process người dùng:

```powershell
docker compose up -d --wait
mvn test
mvn package
python -m unittest discover -s scripts -p test_summarize_monitoring.py -v
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c4.ps1
```

Script in thư mục `server/target/c4-runtime/<runId>` chứa raw/metadata/summary JSON+CSV. Python3.11+ stdlib; không cài thêm package. Event/overflow source MOCK, ACK suppression tại observer C3 SIMULATED; HTTP/WS/DB/B2/dashboard model REAL. ProcessHandle collector scan REAL được kiểm riêng, không giả process mở từ MOCK. Metadata ghi source SHA/dirty/hash source+JAR, OS/JDK/settings/clock domain và lệnh sanitize. Đây là smoke không mở GUI; LAN, human B review, WMI/ETW và E1/E2 NOT RUN.

Queue log mặc định1024/drop-new, flush2000ms, writer daemon; I/O lỗi không phá delivery. Summary exit0=COMPLETE các file cung cấp, exit2=INCOMPLETE/lỗi; báo drop/unwritten/truncated/malformed/counter mismatch, không sửa raw. Cấu hình, schema, chi phí khi bật và giới hạn: [MONITORING_MEASUREMENTS](docs/MONITORING_MEASUREMENTS.md). [Khảo sát ProcessHandle/WMI có nguồn Oracle/Microsoft](docs/PROCESS_MONITORING_SURVEY.md), [evidence C4](evidence/t1-c4/2026-10-05-verification.md).

## Full snapshot T2-C1

Candidate bấm **Bắt đầu giám sát** sẽ OPEN để lấy syncEpoch, rồi gửi toàn bộ tập process theo policy v1 sau mỗi lần quét thành công. Full đầu sequence1; ACK có epoch/sequence sau khi server chấp nhận RAM. Event cảnh báo vẫn lưu riêng trong DB. Proctor chọn lượt được phân công, tab **Process hiện tại** cho thấy filename/PID/startInstant/metadataQuality; tab cảnh báo và gián đoạn giữ lịch sử khi process đóng.

Kiểm tự động với DB theo `.env`, schema TEST/port riêng, Windows Edge do test tự mở và dọn:

```powershell
docker compose up -d --wait
mvn package
python -m unittest discover -s scripts -p test_summarize_monitoring.py -v
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 -JavaHome $env:JAVA_HOME -Gui
```

`JavaHome` là thư mục JDK21, không phải `bin/java.exe`; nếu JAVA_HOME rỗng, script dùng `java` trong PATH. Python dùng stdlib, không cần rg/winget cho smoke này. `-Gui` mở component giám thị JavaFX thật, chọn lượt, kiểm process Edge xuất hiện/biến mất và chụp ảnh ở `server/target/t2c1-smoke/`. Raw, checksum source/JAR, metadata và summary nằm dưới đường dẫn runId mà script in. Component PASS chưa thay GUI candidate toàn app hay LAN.

Tự demo trong app: build lại, dừng server dev cũ và chạy bản mới (không reset volume). Nạp `.env` vào PowerShell rồi chạy server:

```powershell
foreach ($line in Get-Content -Encoding UTF8 .env) {
    if ($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
}
java "-Duser.timezone=UTC" -jar server/target/server-0.1.0-SNAPSHOT.jar
```

Ở hai terminal khác, mỗi terminal chạy `java -jar client/target/client-0.1.0-SNAPSHOT-all.jar`. Nếu đã có DEMO-C3-A thì dùng lại; nếu chưa có, chạy `scripts/demo-c3.ps1 -Action Create` như mục demo C3. Candidate1 chọn lượt/bắt đầu; proctor1 chọn cùng lượt. Mở Edge, chờ vài poll: bảng hiện tại có một hoặc nhiều PID Edge. Đóng **tất cả process Edge đó**: chúng biến mất khỏi bảng hiện tại; cảnh báo lịch sử vẫn còn. Edge có thể giữ process nền sau đóng cửa sổ nên phải đối chiếu PID. Notepad không thuộc policy v1.

Dừng giám sát: state STALE giữ tập cuối với nhãn dữ liệu cũ. Kết nối lại: OPEN mới, UNSYNCED cho tới full1. Lỗi scan không gửi full rỗng; full rỗng chỉ từ scan thành công không có process phù hợp. Default server stale6s, TTL5phút,4096attempt; client full queue16+1pending, snapshot128process, retry5. Quá giới hạn báo lỗi, không cắt tập. Restart server mất state RAM; lịch sử PostgreSQL không mất. Không hỗ trợ nhiều server cùng giữ full state. [Contract](docs/PROTOCOL.md#t2-c1--contract-full-snapshot-baseline-v1), [QD-12](docs/QUYET_DINH.md), [evidence](evidence/t2-c1/2026-10-10-verification.md).

## Heartbeat, event muộn và gap T2-C2

Khi client mất liên lạc, A4 tự báo UNKNOWN và ghi lần heartbeat cuối/thời điểm server phát hiện timeout. Khi nối lại, event chưa được xác nhận có thể gửi bù vào lịch sử; full mới phục hồi danh sách process hiện tại. Hai luồng này độc lập: process đã đóng vẫn có cảnh báo lịch sử, còn danh sách hiện tại không được event muộn làm sống lại.

Event mới có nguồn kết nối đóng băng lúc quan sát. Dashboard thêm cột "Cách nhận": "Đến muộn · từ kết nối trước", "Đến muộn · quan sát khi mất kết nối", "Cùng kết nối lúc quan sát" hoặc "Chưa có thông tin kết nối lúc quan sát". Đây là thông tin về kết nối, không phải số mili giây trễ hoặc bằng chứng gian lận. Event trước ACK heartbeat đầu/legacy có thể chưa xác định; không suy luận bằng cách trừ giờ hai máy.

Tab "Khoảng trống dữ liệu" đọc báo cáo QUEUE_OVERFLOW qua `/api/v1/monitoring/attempts/{attemptId}/gaps`. Bấm "Làm mới quyền và dữ liệu" để lấy báo cáo mới. Mất heartbeat nằm riêng ở "Lịch sử gián đoạn" và không có số event mất tự suy ra. Nâng server trước client mới; Flyway tự thêm V8, không reset DB hoặc sửa migration đã áp dụng. V8 giữ các event cũ với nguồn UNSPECIFIED.

Demo tự động trên Windows, từ thư mục repository, với Docker/PostgreSQL đang chạy và `.env` local đã có:

```powershell
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot' # đổi thành JDK 21 của máy bạn
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c2.ps1 -JavaHome $jdk -Gui
# Hồi quy full/process thật và tự STALE/TTL/shutdown:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 -JavaHome $jdk -Gui
# Hard-kill client JVM thật, nhiều socket, revoke, khôi phục presence:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a4.ps1 -JavaHome $jdk
```

C2 smoke tự dựng schema TEST và server port riêng; không cần dừng server dev. Nó chạy lần lượt: hai candidate ONLINE → giữ một event chưa gửi → ngắt socket A → tự UNKNOWN → tạo event offline → reconnect/ONLINE → gửi bù có nhãn → full rỗng phục hồi state → kiểm retry/conflict/COMMIT lỗi → ép queue tràn → giám thị đọc gap → kiểm CLOSED/SUBMITTED/TIMED_OUT/revoke → dọn worker/schema. Mạng/DB/Spring/collector worker và GUI proctor component là REAL; process readings là MOCK; ngắt kết nối/giữ write/mất ACK được điều khiển (SIMULATED). Smoke C1 riêng dùng ProcessHandle/Edge thật; A4 riêng hard-kill JVM sở hữu thật. Không dùng nút Stop để giả mất mạng trong app: Stop chủ động dọn và bỏ pending theo contract.

Kết quả mong đợi là các dòng PASS và `Summary COMPLETE`, file ở `server/target/t2c2-runtime/<runId>/`, ảnh ở `server/target/t2c2-smoke/`. LAN và candidate GUI toàn app chưa được mô phỏng thành PASS. Chỉ có các số drop mà client thực sự báo qua overflow mới xuất hiện ở tab gap. [Contract C2](docs/PROTOCOL.md#t2-c2--heartbeat-event-đến-muộn-và-khoảng-trống).

Review-fix e07bb7e: `MonitoringStateMaintenance` tự gọi maintain theo scan-ms bằng worker riêng; server close hủy worker và xóa full RAM. Không bật EnableScheduling toàn app để tránh kích hoạt scheduler bài thi A ngoài phạm vi. Smoke full nay kiểm thêm socket/heartbeat vẫn sống, ngừng full → tự push STALE, TTL4s tự xóa entry/capacity1 cấp được lượt khác; không đọc HTTP state hay gọi maintain bằng tay. [Evidence sửa scheduling](evidence/t2-c1/scheduling-fix/2026-10-10-verification.md). Kết quả gốc30fa6d9 chưa chứng minh tác vụ tự chạy.

## Trace và oracle T2-C3

Recorder tùy chọn trên collector hiện tại, CLI không cần giao diện dùng full/event encoder production và lõi reducer C1, oracle độc lập từ quan sát gốc, gây lỗi duplicate/drop/reorder/reconnect bằng seed. Không thêm thư viện hoặc message mạng. [Cách chạy và schema](docs/MONITORING_TRACE.md), [evidence mới](evidence/t2-c3/2026-10-10-verification.md).

```powershell
mvn package
java -cp client/target/client-0.1.0-SNAPSHOT-all.jar vn.edu.toeic.client.monitoring.trace.TraceCli record --output traces/demo.jsonl --scans 20 --poll-ms 500
java -cp client/target/client-0.1.0-SNAPSHOT-all.jar vn.edu.toeic.client.monitoring.trace.TraceCli replay --input traces/demo.jsonl --output traces/demo-result.json --seed 1234 --duplicate 20 --drop 10 --reorder 35 --reconnect-at 3
```

Exit0 PASS,1 oracle mismatch,2 invalid/incomplete/config/I/O. Output mới, không ghi đè. REAL chỉ nói nguồn ProcessHandle thực; phát lại là REPLAY, lỗi/ACK/gate SIMULATED. Không suy UNKNOWN từ full bị mất hoặc lấy thời gian replay làm E1/E2. Human B review/candidate GUI toàn app/LAN NOT RUN; dừng sau C3.
# Chạy thử process có kiểm soát (T2-C4)

Sau `mvn package`, chạy `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c4.ps1 -JavaHome '<JDK21>'` tại thư mục gốc. Công cụ tạo 30 child Java thật, ghi ground truth độc lập và trace collector rồi ghép theo PID/start/clock mapping; không cần DB hoặc GUI. Policy TEST riêng không đổi 8 tên production. [Lệnh CLI, từng mốc thời gian và giới hạn](docs/CONTROLLED_PROCESS_HARNESS.md); [evidence](evidence/t2-c4/2026-10-10-verification.md). Không coi mọi child không thấy là lỗi, không suy ra E1/E2 hay hiệu năng.
