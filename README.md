# TOEIC Monitoring Realtime

Xương sống kỹ thuật chặng 1 gồm Spring Boot server, JavaFX client hai role, PostgreSQL và spike `ProcessHandle`. Đăng nhập đã cài; adapter realtime T1-B2 có state machine/test MOCK và khóa UI. **Authenticated WebSocket thật đang BLOCKED BY T1-A2/QD-03**; collector polling và nghiệp vụ thi thuộc các task sau.

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
| Spring Security Crypto | BCrypt cho mật khẩu mẫu; chưa bật full Spring Security ở T1-A1 |
| Flyway + PostgreSQL module | Migration schema có version, dựng lại được |
| PostgreSQL JDBC Driver | Kết nối PostgreSQL 18 |
| JavaFX Controls 21.0.12 | Một desktop app có giao diện thí sinh/giám thị |
| Gson 2.13.2 | Serialize/deserialize contract login trong client thuần Java |
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
