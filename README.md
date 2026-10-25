# TOEIC Monitor

Hệ thống thi TOEIC theo mô hình Client–Server, có giám sát máy thí sinh theo thời gian thực. Một ứng dụng JavaFX phục vụ ba vai trò; Server Spring Boot dùng PostgreSQL; HTTP/REST cho dữ liệu, WebSocket cho heartbeat, giám sát và lệnh START. Bài tập lớn môn Lập trình mạng (nhóm 3 người, vai A/B/C).

## Luồng sử dụng

| Vai trò | Sau khi đăng nhập |
|---|---|
| **Admin** | Quản lý tài khoản: tạo Giám thị/Thí sinh, cấp mật khẩu một lần, khóa/mở khóa. |
| **Giám thị** | Điều khiển ca → Phòng thi TOEIC: chọn đề → tạo phòng, nhận **PIN 5 số** → theo dõi JOINED/READY → **START**. Theo dõi tiến trình, USB, focus/cửa sổ của từng thí sinh trên Tổng quan, Chi tiết thí sinh và Timeline. |
| **Thí sinh** | Nhập PIN → phòng chờ (tải audio, thử loa, xác nhận sẵn sàng) → Listening phát một lần → Reading → Nộp bài. Đáp án tự lưu. |

START khi còn người chưa READY cần xác nhận một lần; người đó làm Reading trong thời gian còn lại, không được phát bù Listening. Deadline do Server quyết định chung cho phòng.

## Chạy bản phát hành (Windows portable)

Bản phát hành chính thức là một ZIP Windows portable kèm file SHA-256, đăng ở trang [Releases](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/releases) sau khi PR phát hành được merge. Bản release candidate hiện tại, `toeic-monitor-portable-5a1af3e.zip` (SHA-256 `0a8d5ab55467a6a25fdfe16768f1889aeb5c4c46f9ae9b2fdafe47b8cbd2313d`), và kết quả kiểm thử của nó nằm trong [báo cáo phát hành](docs/release/RELEASE-REPORT.md). Giải nén (đường dẫn có dấu được hỗ trợ), rồi:

1. Máy Server: chép `server.env.example` thành `server.env`, điền DB, media root và khóa PIN theo `READ-ME.txt`, bấm `Start-Server.bat`.
2. Mỗi máy dùng: bấm `Start-Client.bat`, nhập `http://IP_MAY_SERVER:8080`.

Bundle có sẵn Java 21; cần PostgreSQL 18 trên máy Server và media của đề đã import. Hướng dẫn đầy đủ, kể cả nạp đề STUDY4 và checklist LAN: **[docs/DEMO-RUNBOOK.md](docs/DEMO-RUNBOOK.md)**.

## Build từ source

Yêu cầu: Windows 11 x64, JDK 21, Maven 3.9+, PostgreSQL 18 (hoặc Docker Compose cho DB phát triển).

```powershell
mvn package                                   # build + unit test
powershell -ExecutionPolicy Bypass -File scripts\package-phase4-portable.ps1 -JavaHome $env:JAVA_HOME
```

Script đóng gói kiểm bytecode trong JAR khớp source vừa build, tạo runtime Java 21 riêng, `artifact-manifest.json` (SHA-256 từng file) và ZIP trong `target\release\distribution\`. Cần Windows SDK `mt.exe` để runtime chạy được trong đường dẫn Unicode.

Chạy từ source để phát triển: chép `.env.example` thành `.env` và điền, `docker compose up -d --wait`, `mvn package -DskipTests`, rồi `powershell -File scripts\start-monitoring-server.ps1` (tự đọc `.env`); Client: `mvn -pl client -am javafx:run`. Tài khoản `MOCK` `candidate1`, `candidate2`, `proctor1` dùng `TOEIC_SEED_PASSWORD`, chỉ cho môi trường phát triển.

## Kiểm thử

| Lệnh | Phạm vi |
|---|---|
| `mvn test` | Unit test toàn bộ module (không cần DB) |
| `scripts\verify-room-ux.ps1 -Mode Full -PostgresBin <PG18\bin>` | Toàn bộ reactor trên PostgreSQL cô lập tự dựng/tự dọn, gồm GUI JavaFX của luồng ACCOUNT_PIN |
| `scripts\verify-release-server.ps1 -Bundle <thư mục bundle> -PostgresBin <PG18\bin>` | Server portable từ đường dẫn Unicode, kiểm cấu hình, migration, Admin bootstrap; tùy chọn nâng cấp DB từ Server cũ |
| `scripts\verify-phase4-portable.ps1 -Bundle <thư mục bundle> -UnicodeCopy` | Launcher Client, cửa sổ đăng nhập từ runtime đóng gói |

Kết quả của bản phát hành hiện tại: [docs/release/RELEASE-REPORT.md](docs/release/RELEASE-REPORT.md). Trạng thái từng test case AT/MT/LT/IT: [docs/KIEM_THU.md](docs/KIEM_THU.md).

## Tài liệu

| Cần gì | Ở đâu |
|---|---|
| Cài đặt, chạy và luồng demo | [docs/DEMO-RUNBOOK.md](docs/DEMO-RUNBOOK.md) |
| Kiến trúc và module | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Endpoint REST và message WebSocket | [docs/PROTOCOL.md](docs/PROTOCOL.md), [docs/BACKEND_API.md](docs/BACKEND_API.md), [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) |
| Thực nghiệm và kết quả đo | [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) |
| Báo cáo phát hành và ma trận kiểm thử | [docs/release/RELEASE-REPORT.md](docs/release/RELEASE-REPORT.md) |
| Quy tắc lưu/nộp/hết giờ, monitoring, Listening | [docs/plan-v1/02_HOP_DONG.md](docs/plan-v1/02_HOP_DONG.md) |
| Quyết định kỹ thuật, tiến độ, nhật ký | [docs/QUYET_DINH.md](docs/QUYET_DINH.md), [docs/TIEN_DO.md](docs/TIEN_DO.md), [docs/NHAT_KY.md](docs/NHAT_KY.md) |
| Kế hoạch V1/V2, bằng chứng theo task | [docs/plan-v1/README.md](docs/plan-v1/README.md), [docs/plan-v2/README.md](docs/plan-v2/README.md), [evidence/README.md](evidence/README.md) |
| README cũ (hướng dẫn từng task trước bản phát hành) | [docs/archive/README-LICH-SU.md](docs/archive/README-LICH-SU.md) |

## Thư viện ngoài và lý do sử dụng

| Thư viện | Mục đích |
|---|---|
| Spring Boot Web/JDBC 4.1.1 | REST server và truy cập PostgreSQL bằng API JDBC tường minh |
| Spring Boot WebSocket 4.1.1 | Raw WebSocket `/ws/v1/realtime`, handshake auth và tuần tự hóa send; không STOMP/SockJS/broker |
| Spring Security Crypto | BCrypt cho mật khẩu tài khoản; không bật full Spring Security |
| Flyway + PostgreSQL module | Migration schema có version (V1–V21), dựng lại được |
| PostgreSQL JDBC Driver | Kết nối PostgreSQL 18 |
| JavaFX Controls 21.0.12 | Một desktop app cho ba vai trò |
| JavaFX Media 21.0.12 | Phát audio Listening và loa thử từ file đã kiểm checksum |
| Gson 2.13.2 | JSON cho DTO/envelope ở protocol, client, server và công cụ chuyển đề STUDY4 |
| JUnit 5 + AssertJ | Test đơn vị và test hành vi bất đồng bộ |
| Maven Shade Plugin | Fat JAR cho Client đóng gói |

## Giới hạn đã biết

- Nghiệm thu LAN hai máy Windows thật và nghe audio bằng người thật: xem trạng thái trong [báo cáo phát hành](docs/release/RELEASE-REPORT.md); không suy ra từ test cùng máy.
- Bản portable chưa ký số. Giao diện legacy (vào phòng bằng mã phòng + PIN cá nhân, ca V1) bị ẩn mặc định, backend và test vẫn giữ; bật lại cho kiểm thử hồi quy bằng `TOEIC_UI_LEGACY=true`.
- Mất heartbeat được hiển thị là UNKNOWN/"quan sát thấy", không phải bằng chứng gian lận.
- Giám sát tiến trình (`process-policy-v4`) chỉ quan sát đúng 9 tên: `chrome.exe`, `msedge.exe`, `firefox.exe`, `zalo.exe`, `teams.exe`, `ms-teams.exe`, `discord.exe`, `anydesk.exe`, `teamviewer.exe` ([chi tiết](docs/PROCESS_GROUPS.md)). Không đọc nội dung/tham số, không đóng tiến trình; tiến trình sống ngắn hơn chu kỳ poll có thể bị bỏ lỡ. Server và Client phải cùng bản (server cũ không hiểu v4).
- Giám sát USB lưu trữ (có mặt/cắm/rút), focus, cửa sổ và màn hình được **bật bởi launcher phát hành** (`Start-*.bat`; tắt bằng `-NoSignalMonitoring`); chạy JAR trực tiếp thì mặc định tắt. Chỉ quan sát trên máy thí sinh đã được thông báo; không đọc nội dung hay serial USB, không chặn thiết bị. Polling có thể bỏ sót thao tác cắm/rút quá nhanh giữa hai lần quét.
