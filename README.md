# TOEIC Monitoring Realtime

Xương sống kỹ thuật chặng 1 gồm Spring Boot server, JavaFX client hai role, PostgreSQL và spike `ProcessHandle`. Server T1-A2 hỗ trợ Bearer REST auth, raw WebSocket xác thực và heartbeat/ACK ở mức transport. Event persistence, presence, collector và nghiệp vụ thi thuộc các task sau.

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

## Adapter realtime T1-B2 — PARTIAL / BLOCKED BY T1-A2

`client/.../realtime/RealtimeClient` là adapter WS duy nhất; dùng `java.net.http.WebSocket`, Gson và `ScheduledExecutorService` có tên luồng `toeic-realtime-worker`. Không thêm dependency. Adapter mặc định từ chối kết nối bằng `AuthContractUnavailableException`; không có endpoint/header/AUTH tự chọn và không đưa credential vào URL/log.

`Settings` cấu hình heartbeat (mặc định 2 giây), backoff (1/2/4/8 giây, cap 8 giây), tối đa 4 retry sau lần mở đầu, message tối đa 65.536 ký tự và tối đa 500 ACK/write đang chờ. Có thể truyền scheduler khác để kiểm thử bằng thời gian ảo. Mỗi retry gọi lại `ConnectionOpener.open(listener)`; hook tương lai phải mở WS mới và xác thực lại, chỉ hoàn thành future sau khi auth thành công. Hook phải từ chối token trong URL/query, không lộ credential trong exception/log, ánh xạ lỗi credential sang `AuthenticationRejectedException`, và dọn HTTP client/executor trong `close()` mà không chặn FX thread. **Hook production này chưa cài, chờ A chốt contract.**

Trạng thái `CONNECTING`, `CONNECTED`, `RECONNECTING`, `DISCONNECTED`, `FAILED` được chuyển thành `ConnectionViewModel`; container thao tác cần mạng chỉ mở khi `CONNECTED`. JavaFX nhận callback bằng `Platform.runLater`; logout gỡ listener/hủy kết nối, `Application.stop()` đóng adapter và HTTP login. Màn hiện tại chỉ có vùng placeholder bị khóa, chưa có màn thi/dashboard hay thao tác nghiệp vụ. Không bật chế độ MOCK trong ứng dụng thật.

### Bàn giao cho C

Interface `vn.edu.toeic.client.realtime.MonitoringTransport`:

```java
CompletableFuture<Void> send(MessageEnvelope<JsonObject> message);
ConnectionState connectionState();
AutoCloseable onConnectionState(Consumer<ConnectionState> listener);
AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener);
```

- C phụ thuộc interface này, không dùng raw WebSocket. Subscription trả `AutoCloseable` để gỡ listener; callback không bảo đảm chạy trên FX thread (state có thể phát từ luồng gọi hoặc worker), bên UI luôn marshal về FX thread.
- `send` hiện chấp nhận envelope v0 `PROCESS_OBSERVED`/`HEARTBEAT` theo fixture MOCK đã có. Scope do server cấp phải truyền qua `Session`, không tự tạo scope trong production; login hiện trả scope rỗng nên chưa thể lập phiên monitoring thật.
- Future `send` thành công chỉ nghĩa là ghi lên socket; **không phải ACK/DB commit**. ACK `ACCEPTED` khớp requestId/type/attemptId/traceId mới chuyển tới `onMessage`. Không replay tự động event, không giữ ACK pending qua reconnect. C3 quản lý eventId, queue, retry/payload và cách đối soát ACK; retry phải giữ nguyên ID/nội dung.
- State/full/delta chưa có schema thực trong PROTOCOL, nên cùng phương thức `send` sẽ nhận envelope state sau khi C chốt type/payload và B cập nhật danh sách validation. Hiện gửi type state chưa hỗ trợ bị từ chối; chưa tuyên bố state integration PASS.
- C có thể dùng interface/test MOCK ngay; gửi dữ liệu thật còn chờ A2 và C3. B không cài collector hay queue nghiệp vụ của C.

`RealtimeClientTest` và `MockScheduler` ghi rõ MOCK: kiểm fragment, heartbeat, retry, shutdown, JSON/scope/ACK và model khóa UI. Các test này không thay thế integration với server thật hoặc kiểm GUI bằng tay.

## T1-A2 — REST và WebSocket có xác thực

- `POST /api/v1/auth/login` vẫn public. `GET /api/v1/auth/me` và mọi route `/api/**` sau login cần `Authorization: Bearer <token>`.
- WebSocket: `ws://<server>:8080/ws/v1/realtime` (dùng `wss://` khi server triển khai TLS). Header handshake cũng là `Authorization: Bearer <token>`; endpoint không nhận query. Không log/password/token trong URL. Kết nối mới/reconnect phải gửi lại credential.
- Server lookup SHA-256 trong `login_sessions`, kiểm expires/revoked/user enabled; WS kiểm lại mỗi message. 401 là thiếu/sai/hết hạn/revoked credential, 403 là sai role/scope. Session bị vô hiệu trên WS nhận ERROR UNAUTHORIZED rồi đóng 1008.
- `AuthenticatedUser`/principal lấy từ DB; `AuthorizationService.requireRole` và `requireAttempt` chạy trước nghiệp vụ. `AttemptScopeAuthorizer` production hiện **deny tất cả attempt chưa có assignment** vì schema attempt chưa cài. Provider mới có thể khai báo bean `@Primary`; không cài dữ liệu scope MOCK vào production. Login/me tiếp tục trả attemptScope rỗng.
- Heartbeat không có attemptId là ping transport cho phiên authenticated, chưa theo dõi thi/monitoring/presence. Có attemptId thì bắt buộc scope hợp lệ. ACK ACCEPTED chỉ xác nhận server nhận heartbeat; không khẳng định event DB đã commit. PROCESS_OBSERVED/state/delta vẫn chưa cài và không nhận success ACK.
- Envelope mẫu, field bắt buộc, ACK/ERROR và handoff cho B2 ở [PROTOCOL](docs/PROTOCOL.md). B cần cho phép heartbeat/ACK unscoped, cài opener header auth, xử lý ERROR và auth lại khi reconnect. Nhánh B2 chưa được thay đổi/merge trong task A2.
- WS giới hạn message/buffer 65.536 byte và send timeout 5.000ms qua `WS_MAX_MESSAGE_BYTES`, `WS_SEND_BUFFER_BYTES`, `WS_SEND_TIMEOUT_MS`. Tất cả send đi qua `ConcurrentWebSocketSessionDecorator`.

Kiểm thử mặc định có real HTTP/WS trên Spring/Tomcat và `java.net.http.WebSocket`, dùng session/scope store **MOCK** (không H2). Smoke PostgreSQL riêng dùng toàn bộ app production và JDBC/Flyway thật:

```powershell
docker compose up -d --wait
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a2.ps1
```

Script đọc DB/seed variables từ `.env` vào process, không in credential, không reset DB; harness chỉ xóa login session do chính smoke tạo. Test account seed là MOCK/dev only. Script dùng timezone UTC và port random local để không chiếm port server đang chạy. Nếu Java chưa có trong PATH/JAVA_HOME, truyền `-JavaHome <thư mục JDK 21>` cho script. Không thay đổi file `.env`.
