# TOEIC Monitoring Realtime

Xương sống kỹ thuật chặng 1 gồm Spring Boot server, JavaFX client hai role, PostgreSQL và spike `ProcessHandle`. Phạm vi hiện tại mới có đăng nhập; WebSocket, collector polling và nghiệp vụ thi thuộc các task sau.

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
