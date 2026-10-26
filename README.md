# TOEIC Monitor

Hệ thống thi TOEIC theo mô hình Client–Server, có giám sát máy thí sinh theo thời gian thực. Một ứng dụng JavaFX phục vụ ba vai trò; Server Spring Boot dùng PostgreSQL; HTTP/REST cho dữ liệu, WebSocket cho heartbeat, giám sát và lệnh START. Bài tập lớn môn Lập trình mạng (nhóm 3 người, vai A/B/C).

## Luồng sử dụng

| Vai trò | Sau khi đăng nhập |
|---|---|
| **Admin** | Quản lý tài khoản: tạo Giám thị/Thí sinh với mật khẩu Admin tự nhập (8–128 ký tự, nhập hai lần), đổi mật khẩu thí sinh, khóa/mở khóa. |
| **Giám thị** | Một màn **Phòng thi**: "+ Tạo phòng" → chọn đề → nhận **PIN 5 số** chung cho phòng → theo dõi thí sinh vào phòng và "Sẵn sàng" → **Bắt đầu phòng thi**. Bảng thí sinh của phòng đang chọn hiện trạng thái, kết nối, phần nghe, USB/Focus/Cửa sổ và cảnh báo; bấm một hàng để xem chi tiết và lịch sử đầy đủ. |
| **Thí sinh** | Nhập PIN → phòng chờ (tải âm thanh, thử loa, xác nhận sẵn sàng) → Listening phát một lần → Reading → Nộp bài. Đáp án tự lưu. |

Bắt đầu khi còn người chưa sẵn sàng cần xác nhận một lần; người đó làm Reading trong thời gian còn lại, không được phát bù Listening. Deadline do Server quyết định chung cho phòng. Giờ hiển thị theo giờ Việt Nam (GMT+7).

## Cách chạy

Có hai phía: **một máy chủ** (Server + PostgreSQL) và **mỗi người dùng một máy Client** (Admin, Giám thị, Thí sinh dùng chung một app). Client nói chuyện với Server qua một địa chỉ duy nhất:

| Mạng | Địa chỉ Server Client dùng |
|---|---|
| Internet (Cloudflare Tunnel) | `https://toeic.anhtaizz.id.vn` |
| Cùng mạng LAN | `http://IP_MAY_CHU:8080` (IP lấy bằng `ipconfig` trên máy chủ) |
| Cùng một máy | `http://127.0.0.1:8080` |

### Máy chủ (từ repo)

Cần Windows 11 x64, JDK 21, Maven 3.9+, Docker Desktop. Qua Internet thì cần thêm `cloudflared` và token tunnel.

**Làm một lần:**

1. Chép `.env.example` thành `.env` rồi điền:
   - DB (`DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PORT`);
   - thư mục media của đề đã nạp: `TOEIC_AUDIO_ROOT`, `TOEIC_IMAGE_ROOT` (đường dẫn tương đối tính theo thư mục chứa `.env`);
   - `TOEIC_ROOM_PIN_KEY`: tạo một lần theo lệnh trong [docs/DEMO-RUNBOOK.md](docs/DEMO-RUNBOOK.md) mục 1, giữ nguyên qua các lần khởi động;
   - lần đầu với DB mới: `TOEIC_ADMIN_BOOTSTRAP_USERNAME`, `TOEIC_ADMIN_BOOTSTRAP_PASSWORD` (20–72 ký tự). Launcher bên dưới không tạo tài khoản demo, nên Admin này là tài khoản đầu tiên.
2. Nạp đề: [docs/DEMO-RUNBOOK.md](docs/DEMO-RUNBOOK.md) mục 2.
3. Chạy qua Internet: cài `cloudflared`, lưu token tunnel và khai báo hostname theo [docs/deploy/CLOUDFLARE-TUNNEL.md](docs/deploy/CLOUDFLARE-TUNNEL.md) ("Làm một lần trên máy chủ").

**Mỗi lần chạy:**

```powershell
mvn -q package -DskipTests        # sau mỗi lần đổi code
docker compose up -d --wait       # PostgreSQL, chỉ mở 127.0.0.1
```

Rồi bấm đúp ở gốc repo:

| File | Việc làm |
|---|---|
| `START-CLOUDFLARE-SERVER.bat` | Mở Server (bật giám sát như bản phát hành) và connector Cloudflare, rồi kiểm tra qua Internet. Server đang chạy thì dùng lại. Thêm `-AllowLan` để máy cùng mạng vào được bằng `http://IP:8080`. |
| `STATUS-CLOUDFLARE.bat` | Xem nhanh Server, connector, đường Internet. |
| `STOP-CLOUDFLARE-SERVER.bat` | Chỉ dừng Server và connector do script mở. |

Máy chủ phải luôn bật, có mạng và không sleep trong giờ thi. Lỗi thường gặp và cách xử lý: [docs/deploy/CLOUDFLARE-TUNNEL.md](docs/deploy/CLOUDFLARE-TUNNEL.md), mục "Chẩn đoán lỗi".

### Thí sinh và Giám thị: cài MSI (khuyên dùng)

1. Nhận file `ToeicMonitor-<phiên bản>.msi` từ người tổ chức. File cài không nằm trong Git (`.gitignore` chặn `*.msi`).
2. Bấm đúp để cài cho riêng tài khoản Windows của mình (không cần quyền Admin). SmartScreen báo "Windows protected your PC" thì chọn *More info* → *Run anyway* (bộ cài chưa ký số).
3. Mở **ToeicMonitor** ở Desktop hoặc Start menu → đăng nhập.

Bộ cài có sẵn Java 21, đã bật giám sát và đặt sẵn Server `https://toeic.anhtaizz.id.vn`: không cần Java, không cần nhập địa chỉ. Cài bản mới hơn sẽ thay bản cũ.

Muốn một máy dùng Server khác (ví dụ LAN), ghi một dòng địa chỉ vào `%LOCALAPPDATA%\TOEIC Monitor\server-url.txt`.

Tạo MSI (máy có JDK 21, WiX 3 và Windows SDK `mt.exe`):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 -JavaHome <JDK21> -WixHome <WiX3> -ManifestTool <mt.exe> `
  -AppVersion <phiên bản> -ServerUrl https://toeic.anhtaizz.id.vn
```

### Client chạy từ repo (máy có JDK 21, đã `mvn package`)

| File | Server |
|---|---|
| `START-CLIENT-CLOUDFLARE.bat` | `https://toeic.anhtaizz.id.vn` |
| `START-CLIENT-LAN.bat` | Hỏi địa chỉ; nhập `http://IP_MAY_CHU:8080` hoặc `http://127.0.0.1:8080` |

Mở nhiều lần để có nhiều cửa sổ (mỗi vai một cửa sổ) khi thử trên một máy.

### Luồng thử nhanh

1. **Admin** đăng nhập → tạo một Giám thị và một Thí sinh, tự đặt mật khẩu.
2. **Giám thị** đăng nhập → "+ Tạo phòng" → chọn đề → đọc PIN cho thí sinh.
3. **Thí sinh** đăng nhập → nhập PIN → làm 3 bước âm thanh → hàng của thí sinh bên Giám thị chuyển "Sẵn sàng".
4. Giám thị bấm **Bắt đầu phòng thi** → thí sinh nghe Listening, làm bài, chân trang hiện "✓ Đã lưu" → *Nộp bài*.
5. Trong lúc thi, mở Chrome/Zalo, rời cửa sổ hoặc cắm/rút USB trên máy thí sinh → Giám thị thấy cảnh báo và cột USB/Focus/Cửa sổ đổi.

Checklist đầy đủ cho hai máy khác mạng: [docs/deploy/CLOUDFLARE-TUNNEL.md](docs/deploy/CLOUDFLARE-TUNNEL.md), mục cuối.

## Bản phát hành Windows portable (ZIP)

Bản phát hành chính thức là một ZIP Windows portable kèm file SHA-256, đăng ở trang [Releases](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/releases) sau khi PR phát hành được merge. Bản release candidate hiện tại, `toeic-monitor-portable-5173462.zip` (SHA-256 `cd2b6e2bbd95c771b122cd05d6443bdbe279ea6111630c2f9fd64acac1adc1f8`), và kết quả kiểm thử của nó nằm trong [báo cáo phát hành](docs/release/RELEASE-REPORT.md). Giải nén (đường dẫn có dấu được hỗ trợ), rồi:

1. Máy Server: chép `server.env.example` thành `server.env`, điền DB, media root, khóa PIN và (lần đầu) tài khoản Admin bootstrap theo `READ-ME.txt`, bấm `Start-Server.bat`. Bản phát hành không tạo tài khoản demo; Admin tạo Giám thị và Thí sinh.
2. Mỗi máy dùng: bấm `Start-Client.bat`, nhập `http://IP_MAY_SERVER:8080`.
3. Qua Internet (máy Client ở mạng khác): máy chủ chạy Cloudflare Tunnel, Client bấm `Start-Client-Internet.bat` (`https://toeic.anhtaizz.id.vn`). Cài đặt, script và chẩn đoán: **[docs/deploy/CLOUDFLARE-TUNNEL.md](docs/deploy/CLOUDFLARE-TUNNEL.md)**.

Bundle có sẵn Java 21; cần PostgreSQL 18 trên máy Server và media của đề đã import. Hướng dẫn đầy đủ, kể cả nạp đề STUDY4 và checklist LAN: **[docs/DEMO-RUNBOOK.md](docs/DEMO-RUNBOOK.md)**.

## Build từ source

Yêu cầu: Windows 11 x64, JDK 21, Maven 3.9+, PostgreSQL 18 (hoặc Docker Compose cho DB phát triển).

```powershell
mvn package                                   # build + unit test
powershell -ExecutionPolicy Bypass -File scripts\package-phase4-portable.ps1 -JavaHome $env:JAVA_HOME
```

Script đóng gói kiểm bytecode trong JAR khớp source vừa build, tạo runtime Java 21 riêng, `artifact-manifest.json` (SHA-256 từng file) và ZIP trong `target\release\distribution\`. Cần Windows SDK `mt.exe` để runtime chạy được trong đường dẫn Unicode.

Chạy Server kiểu phát triển (không qua launcher phát hành): sau khi có `.env`, `docker compose up -d --wait`, `mvn package -DskipTests`, chạy `powershell -File scripts\start-monitoring-server.ps1` (tự đọc `.env`, nghe `0.0.0.0:8080`); Client: `mvn -pl client -am javafx:run`. Tài khoản `MOCK` `candidate1`, `candidate2`, `proctor1` chỉ được tạo khi bật tường minh `TOEIC_SEED_DEMO_ACCOUNTS=true` kèm `TOEIC_SEED_PASSWORD` (đã có trong `.env.example`), chỉ cho phát triển/kiểm thử; Server không có mật khẩu demo mặc định, và launcher phát hành luôn tắt tài khoản demo. Test Maven của module `server` bật cờ này cho JVM test trong `server/pom.xml`.

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
