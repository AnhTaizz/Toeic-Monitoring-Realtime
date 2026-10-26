# Chạy TOEIC Monitor qua Internet bằng Cloudflare Tunnel

Máy chủ là máy Windows cá nhân; thí sinh và giám thị ở mạng khác truy cập qua `https://toeic.anhtaizz.id.vn`.
Không cần mở cổng router, không public PostgreSQL, máy Client không cần cài `cloudflared`.

```
JavaFX Client (Internet) ──HTTPS/WSS──► Cloudflare (toeic.anhtaizz.id.vn)
                                              │ tunnel "toeic-local"
                                              ▼
Máy chủ Windows:  cloudflared ──HTTP──► Server Spring Boot 127.0.0.1:8080 ──► PostgreSQL 127.0.0.1:5433 (Docker)
```

| Thành phần | Giá trị |
|---|---|
| Tunnel | `toeic-local` (named tunnel, quản lý trên Cloudflare Dashboard) |
| Public hostname | `toeic.anhtaizz.id.vn` |
| Service type / URL | `HTTP` / `127.0.0.1:8080` (connector chạy trực tiếp trên Windows) |
| Path | để trống: REST (`/api/...`), WebSocket (`/ws/v1/realtime`), ảnh và audio cùng đi qua một hostname |

## Vì sao không phải sửa Server hay Client

Đã kiểm trong code:

- Client lấy địa chỉ Server theo thứ tự `-Dtoeic.server.url` → biến môi trường `TOEIC_SERVER_URL` → file
  `%LOCALAPPDATA%\TOEIC Monitor\server-url.txt` → mặc định `http://127.0.0.1:8080` (`ClientSettings`).
- REST, ảnh, audio đều là đường dẫn tương đối ghép vào địa chỉ đó (`RichExamApiClient`, `AudioApiClient`, `ListeningApiClient`…).
  Server không trả URL tuyệt đối nào.
- WebSocket: `https://` tự thành `wss://<host>/ws/v1/realtime`, `http://` thành `ws://` (`AuthenticatedWebSocketOpener`).
  Xác thực bằng header `Authorization: Bearer`, không dùng cookie; reconnect giữ nguyên.
- Server không dùng IP hay scheme của người gọi, không giới hạn Origin WebSocket (Client Java không gửi `Origin`).

## Bí mật nằm ở đâu (ngoài Git)

| Bí mật | Vị trí |
|---|---|
| DB, thư mục media, khóa PIN `TOEIC_ROOM_PIN_KEY` | `.env` ở gốc repo (mẫu: `.env.example`). Đường dẫn media tương đối tính theo thư mục chứa `.env`. |
| Token tunnel | `%LOCALAPPDATA%\TOEIC Monitor\cloudflare-tunnel.token` (đổi bằng `-TokenFile` hoặc biến `TOEIC_TUNNEL_TOKEN_FILE`) |

`.gitignore` đã chặn `.env`, `*.token`, `*.pem`, `.cloudflared/`, `cloudflared-credentials*.json`. Script không in giá trị nào;
token được đưa cho `cloudflared` qua biến môi trường `TUNNEL_TOKEN`, không nằm trên dòng lệnh.

## Làm một lần trên máy chủ

1. **Cài `cloudflared`** (script không tự cài):

   ```powershell
   winget install --id Cloudflare.cloudflared
   ```

   Mở lại cửa sổ PowerShell sau khi cài.

2. **Tạo tunnel trên Cloudflare Dashboard**: Zero Trust → Networks → Tunnels → *Create a tunnel* → loại **Cloudflared** →
   tên `toeic-local` → *Save tunnel*. Ở bước cài connector, Dashboard hiện lệnh dạng
   `cloudflared.exe service install eyJh...`: **chỉ chép phần token** (chuỗi dài sau `install`/`--token`).

3. **Lưu token vào file** (không dán vào chat, Git hay ảnh chụp):

   ```powershell
   $secure = Read-Host 'Dán token tunnel' -AsSecureString
   $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
   New-Item -ItemType Directory -Force "$env:LOCALAPPDATA\TOEIC Monitor" | Out-Null
   Set-Content -LiteralPath "$env:LOCALAPPDATA\TOEIC Monitor\cloudflare-tunnel.token" -Value $token -NoNewline -Encoding ASCII
   Remove-Variable token, secure
   ```

4. **Public Hostname** của tunnel (tab *Public Hostname* / *Published application routes*):
   Subdomain `toeic`, Domain `anhtaizz.id.vn`, Path **để trống**, Service Type **HTTP**, URL **`127.0.0.1:8080`**.
   Cloudflare tự tạo bản ghi DNS. Không gắn Cloudflare Access cho hostname này (Client Java không đi qua trang đăng nhập Access).
   WebSockets mặc định bật ở mục *Network* của domain; đừng tắt.

5. **Build** bản hiện tại (ở gốc repo): `mvn -q package -DskipTests`.

> Nếu chạy connector trong Docker thay vì trên Windows, Service URL **không** được là `127.0.0.1` (đó là chính container);
> dùng `host.docker.internal:8080`. Cách khuyến nghị ở tài liệu này là connector chạy trực tiếp trên Windows.

## Chạy hằng ngày trên máy chủ

1. PostgreSQL: `docker compose up -d --wait` (chỉ mở `127.0.0.1:5433`, không ra Internet).
2. **`START-CLOUDFLARE-SERVER.bat`** ở gốc repo:
   - Server TOEIC đã chạy trên cổng 8080 → dùng lại; cổng bị chương trình khác giữ → báo lỗi, không dừng gì.
   - Chưa chạy → mở Server trong cửa sổ riêng bằng launcher bản phát hành (`scripts/start-release.ps1`, giám sát USB/focus bật,
     không tạo tài khoản demo), chờ `GET /api/v1/auth/me` trả 401 (Server sẵn sàng).
   - Đã có Windows service `cloudflared` hoặc một `cloudflared` khác đang chạy → không mở thêm. Chưa có → mở connector (cửa sổ thu nhỏ).
   - Kiểm tra `https://toeic.anhtaizz.id.vn/api/v1/auth/me` qua Internet và báo nguyên nhân nếu chưa thông.
3. **`STATUS-CLOUDFLARE.bat`**: xem nhanh Server, connector, Internet.
4. **`STOP-CLOUDFLARE-SERVER.bat`**: chỉ dừng Server và connector do script mở (theo PID + giờ khởi động đã ghi);
   Server hay service mở cách khác giữ nguyên.

Tham số (thêm sau tên file `.bat` hoặc dùng `scripts\start-cloudflare.ps1`):

| Tham số | Ý nghĩa |
|---|---|
| `-AllowLan` | Server nghe cả mạng LAN (`0.0.0.0`) để máy cùng mạng vào bằng `http://IP:8080` song song. Mặc định chỉ `127.0.0.1` (chỉ qua tunnel). |
| `-EnvFile <đường dẫn>` | Dùng file cấu hình khác `.env` của repo. |
| `-TokenFile <đường dẫn>` | File token khác mặc định. |
| `-PublicUrl https://...` | Hostname khác (ví dụ sau này chuyển backend sang AWS mà đổi tên). |
| `-Port <cổng>` | Cổng Server khác 8080 (Service URL trên Dashboard phải đổi theo). |

Máy chủ phải **luôn bật, có Internet và không sleep** trong giờ thi (Settings → System → Power: Sleep = Never khi cắm điện),
không để Windows Update tự khởi động lại.

## Bộ cài cho thí sinh và giám thị (khuyên dùng)

Build một lần trên máy có JDK 21, WiX 3 và `mt.exe` (đường dẫn công cụ của máy dự án ở `docs/NHAT_KY.md`):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 -JavaHome <JDK21> -WixHome <WiX3> -ManifestTool <mt.exe> `
  -AppVersion 0.4.0 -ServerUrl https://toeic.anhtaizz.id.vn
```

Kết quả là `ToeicMonitor-<phiên bản>.msi` (có sẵn Java 21, giám sát bản phát hành bật, Server mặc định là hostname trên).
Người dùng tải file về → bấm đúp → cài cho riêng tài khoản Windows của mình (không cần quyền Admin) → mở icon
**ToeicMonitor** ở Desktop hoặc Start menu → đăng nhập. Không cần Java, không cần nhập địa chỉ Server.
Bộ cài chưa ký số: Windows SmartScreen có thể hiện "Windows protected your PC" → *More info* → *Run anyway*.
Cài bản mới hơn sẽ thay bản cũ (cùng upgrade code).

## Chạy Client

| Chế độ | Cách chạy |
|---|---|
| Internet (Cloudflare) | Repo: `START-CLIENT-CLOUDFLARE.bat`. Bản portable: `Start-Client-Internet.bat` (hoặc `Start-Client.bat` rồi nhập `https://toeic.anhtaizz.id.vn`). |
| LAN / cùng máy | Repo: `START-CLIENT-LAN.bat` rồi nhập `http://IP_MÁY_CHỦ:8080` (cùng máy: `http://127.0.0.1:8080`). Bản portable: `Start-Client.bat`. |
| Cố định cho một máy | Ghi một dòng địa chỉ vào `%LOCALAPPDATA%\TOEIC Monitor\server-url.txt`; app mở bằng cách khác cũng dùng địa chỉ này. |

Toàn bộ luồng (đăng nhập, PIN, đề, ảnh, audio, lưu, giám sát, nộp) dùng chung một địa chỉ đó.

**Quay lại LAN**: dừng bằng `STOP-CLOUDFLARE-SERVER.bat`, chạy Server bằng `Start-Server.bat` (bản portable, nghe `0.0.0.0`)
hoặc `START-CLOUDFLARE-SERVER.bat -AllowLan`, Client chọn chế độ LAN như trên. Không cần đổi gì trên Cloudflare.

## Chẩn đoán lỗi

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `STATUS`: "DNS: chưa có bản ghi" | Chưa thêm Public Hostname, hoặc DNS chưa lan | Làm bước 4; chờ vài phút. Một số nhà mạng tự trả IP lạ cho tên chưa tồn tại, nên script hỏi DNS 1.1.1.1. |
| "Tunnel offline", HTTP 530 / Error 1033 | `cloudflared` không chạy hoặc token của tunnel khác | Chạy lại `START-CLOUDFLARE-SERVER.bat`; xem cửa sổ `cloudflared`; tunnel trên Dashboard phải "Healthy". |
| HTTP 502/503/504 | Tunnel chạy nhưng không tới được origin | Server chưa chạy ở `127.0.0.1:8080`, hoặc Service URL/Type sai (phải HTTP, không phải HTTPS). |
| HTTP 403 từ Cloudflare | Access/WAF chặn | Bỏ Access policy hoặc rule chặn cho hostname này. |
| Client báo sai tài khoản/mật khẩu | Lỗi xác thực thật (Server đã nhận request) | Kiểm tra tài khoản trong màn Admin; không liên quan tunnel. |
| Đăng nhập được nhưng luôn "Mất kết nối — đang thử lại" | REST đi qua nhưng WebSocket bị chặn | Bật lại WebSockets trong *Network* của domain; kiểm tra proxy/antivirus của máy Client chặn `wss://`. |
| Client "Địa chỉ server phải là gốc http:// hoặc https://" | Nhập kèm đường dẫn, ví dụ `https://toeic.anhtaizz.id.vn/api` | Chỉ nhập gốc `https://toeic.anhtaizz.id.vn`. |

## Đã kiểm tra / chưa kiểm tra (26/10/2026)

Đã chạy trên máy Windows của dự án, DB dev thật:

- `START`: mở Server, chờ READY, dùng lại Server đang chạy, từ chối cổng bị chương trình khác giữ, báo thiếu `.env`,
  thiếu `cloudflared`, thiếu token, token dán sai; `STATUS` báo đúng DNS chưa có bản ghi; `STOP` chỉ dừng Server do script mở.
- Launcher Client chấp nhận `https://toeic.anhtaizz.id.vn` và địa chỉ LAN; test đơn vị `https` → `wss` cho đúng hostname.

Sau khi cài `cloudflared` dạng Windows service (`Cloudflared`, Automatic) và thêm Public Hostname (CNAME tới `*.cfargotunnel.com`):
`STATUS` báo "Qua Cloudflare tới Server TOEIC: OK"; `GET https://toeic.anhtaizz.id.vn/api/v1/auth/me` trả 401 JSON của Server
qua `server: cloudflare`; bắt tay `wss://toeic.anhtaizz.id.vn/ws/v1/realtime` không token trả 401 giống hệt khi gọi `127.0.0.1`.
**Chưa** chạy luồng thi đầy đủ (đăng nhập, PIN, audio, lưu, nộp, mất mạng) từ máy ở mạng khác: làm theo checklist dưới đây.

Lưu ý: service `Cloudflared` tự chạy cùng Windows, nhưng Server thì không. Sau khi bật máy, chạy `docker compose up -d --wait`
rồi `START-CLOUDFLARE-SERVER.bat`; script thấy service và không mở connector thứ hai.

## Checklist thử hai máy khác mạng (một máy Client dùng 4G)

Luồng mặc định hiện tại là đăng nhập tài khoản + PIN chung của phòng (Room Code + PIN riêng chỉ còn ở "Công cụ cũ").

1. Máy chủ: `docker compose up -d --wait`, `START-CLOUDFLARE-SERVER.bat` báo "Qua Cloudflare tới Server TOEIC: OK".
2. Máy giám thị (khác mạng): `Start-Client-Internet.bat` → đăng nhập giám thị → "+ Tạo phòng" → chọn đề → có PIN.
3. Máy thí sinh (4G): `Start-Client-Internet.bat` → đăng nhập thí sinh → nhập PIN → giám thị thấy thí sinh xuất hiện.
4. Thí sinh làm 3 bước âm thanh (tải audio, thử loa, xác nhận) → hàng thí sinh chuyển "Sẵn sàng".
5. Giám thị "Bắt đầu phòng thi" → thí sinh nghe được Listening, thấy ảnh Part 1.
6. Thí sinh chọn đáp án → chân trang "✓ Đã lưu"; giám thị thấy "Đang thi · x/y" tăng.
7. Thí sinh mở trình duyệt/ứng dụng khác, cắm/rút USB → giám thị thấy cảnh báo và cột USB/Focus đổi.
8. Tắt 4G 20–30 giây rồi bật lại → thí sinh thấy "Mất kết nối…" rồi tự kết nối lại, đáp án đã lưu vẫn còn; giám thị thấy mất/có lại kết nối.
9. Thí sinh "Nộp bài" → hộp kết quả; giám thị thấy "Đã nộp · đúng x/y".
10. Đăng nhập lại / xem lịch sử: kết quả vẫn được lưu.
