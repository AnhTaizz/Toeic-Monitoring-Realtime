# TOEIC Monitoring Realtime

Xương sống kỹ thuật chặng 1 gồm Spring Boot server, JavaFX client hai role, PostgreSQL và spike `ProcessHandle`. Server T1-A2 và client T1-B2 đã tích hợp Bearer REST/WS, heartbeat/ACK transport, bounded reconnect và UI connection lock. Event persistence, presence, collector và nghiệp vụ thi thuộc các task sau.

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

Tài khoản mẫu (`MOCK`, chỉ dùng phát triển) đều có mật khẩu `ChangeMe123!` nếu không đặt `TOEIC_SEED_PASSWORD`:

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
| Gson 2.13.2 | Contract login phía client; strict JSON parsing và serialization envelope/error WS phía server |
| JUnit 5 + AssertJ | Test đơn vị và test hành vi bất đồng bộ |
| Maven Shade Plugin | Tạo fat JAR đầu vào cho spike `jpackage` |

Chi tiết contract, quyết định và bằng chứng kiểm thử nằm trong `docs/`.

## Adapter realtime T1-B2 — đã tích hợp A2

`RealtimeClient` là adapter duy nhất cho raw `java.net.http.WebSocket`; `AuthenticatedWebSocketOpener` parse server origin bằng URI và mở `/ws/v1/realtime`, HTTP → WS, HTTPS → WSS. Mỗi reconnect gửi lại `Authorization: Bearer <token>`. Không dùng query, AUTH message hay subprotocol chứa credential; URL có user-info/query/fragment/path được từ chối bằng thông báo cố định. Không thêm dependency.

`Settings` cấu hình heartbeat (mặc định 2 giây), backoff (1/2/4/8 giây, cap 8 giây), tối đa 4 retry sau lần mở đầu, message tối đa 65.536 ký tự và tối đa 500 ACK/write đang chờ. Mỗi outage mới sau kết nối thành công có budget mới. 401, ERROR UNAUTHORIZED hoặc close 1008 → FAILED, dừng heartbeat/retry, bỏ token trong opener; phải login và tạo adapter mới. 503/network failure → bounded backoff. Hết retry cần đăng nhập lại; không tự retry vô hạn.

Sau login candidate hoặc proctor, JavaFX mở realtime bằng server URL của lần login và token trong memory. Session cho phép heartbeat không có attemptId/collectorSessionId khi scope rỗng; không invent attempt và không bật collector. Callback UI qua Platform.runLater, controls cần mạng chỉ mở khi CONNECTED. Logout/stop gỡ listeners, đóng socket/worker/HttpClient, bỏ reference token/context; không persist token. Màn thi/dashboard vẫn là placeholder, chưa có nghiệp vụ B3.

ACK HEARTBEAT phải khớp requestId/type/attemptId/traceId đang chờ; write success chưa phải ACK. ERROR WS được đọc từ payload trực tiếp `{code,message,retryable}`; client dùng thông báo cố định, không phản chiếu raw exception/JSON. FORBIDDEN/INVALID_INPUT không tự replay message; RETRYABLE_SERVER_ERROR được chuyển cho subscriber, C quản lý retry nghiệp vụ sau này.

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
- HEARTBEAT đã nhận ACK server thật. PROCESS_OBSERVED chỉ giữ interface/validation từ fixture MOCK; server integration/persistence chờ A3/C3. State/full/delta chưa có schema thực, không tự thêm type/payload hoặc cấp scope.
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
- `AuthenticatedUser`/principal lấy từ DB; `AuthorizationService.requireRole` và `requireAttempt` chạy trước nghiệp vụ. `AttemptScopeAuthorizer` production hiện **deny tất cả attempt chưa có assignment** vì schema attempt chưa cài. Provider mới có thể khai báo bean `@Primary`; không cài dữ liệu scope MOCK vào production. Login/me tiếp tục trả attemptScope rỗng.
- Heartbeat không có attemptId là ping transport cho phiên authenticated, chưa theo dõi thi/monitoring/presence. Có attemptId thì bắt buộc scope hợp lệ. ACK ACCEPTED chỉ xác nhận server nhận heartbeat; không khẳng định event DB đã commit. PROCESS_OBSERVED/state/delta vẫn chưa cài và không nhận success ACK.
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
