# DEMO RUNBOOK — TOEIC Monitor

Hướng dẫn cài, chạy và demo. Máy chủ chạy Server từ repo (PostgreSQL trong Docker). Thí sinh và Giám thị cài bộ cài MSI, hoặc chạy Client từ repo bằng `START-CLIENT-*.bat`. Tóm tắt nhanh ở [README](../README.md#cách-chạy); chạy qua Internet và chẩn đoán tunnel ở [deploy/CLOUDFLARE-TUNNEL.md](deploy/CLOUDFLARE-TUNNEL.md).

## 0. Cần chuẩn bị

| Thứ | Ghi chú |
|---|---|
| Máy chủ Windows 11 | JDK 21, Maven 3.9+, Docker Desktop; qua Internet thì thêm `cloudflared` và token tunnel. Có thể kiêm luôn cửa sổ Admin/Giám thị. |
| Máy Thí sinh, Giám thị | Bộ cài `ToeicMonitor-<phiên bản>.msi` ở trang [Releases](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/releases) (có sẵn Java 21). Qua Internet: mạng nào cũng được. Qua LAN: cùng mạng với máy chủ. |
| Đề thi đã chuyển sang định dạng V2 | Đề STUDY4 nội bộ: dùng `tools/study4-adapter` (mục 2). Không phát hành lại nội dung bên thứ ba. |
| Loa/tai nghe | Cho phần thử loa và Listening. |

## 1. Cấu hình máy chủ (một lần)

1. Chép `.env.example` ở gốc repo thành `.env`, điền DB (`DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PORT`). `docker compose up -d --wait` dựng PostgreSQL 18 chỉ mở trên `127.0.0.1`. Nâng cấp từ bản cũ: **sao lưu DB trước**; Flyway tự áp dụng migration mới khi khởi động, không reset dữ liệu cũ.
2. `TOEIC_AUDIO_ROOT`, `TOEIC_IMAGE_ROOT`: thư mục `audio` và `images` của đề đã chuyển (mục 2). Đường dẫn tương đối tính theo thư mục chứa `.env`.
3. Tạo khóa PIN một lần (PowerShell), dán dòng in ra vào `.env`; giữ nguyên qua mọi lần khởi động:

   ```powershell
   $b = New-Object byte[] 32
   $r = [Security.Cryptography.RandomNumberGenerator]::Create()
   try { $r.GetBytes($b) } finally { $r.Dispose() }
   'TOEIC_ROOM_PIN_KEY=' + [Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_')
   ```

4. Lần đầu với DB mới, điền `TOEIC_ADMIN_BOOTSTRAP_USERNAME` và `TOEIC_ADMIN_BOOTSTRAP_PASSWORD` (20–72 ký tự). Không có đăng ký công khai. Launcher không tạo tài khoản demo (`candidate1`, `candidate2`, `proctor1`): với DB mới, Admin bootstrap là tài khoản duy nhất; tài khoản đã có trong DB cũ được giữ nguyên.
5. Qua Internet: cài `cloudflared`, lưu token tunnel và khai báo Public Hostname theo [CLOUDFLARE-TUNNEL.md](deploy/CLOUDFLARE-TUNNEL.md), mục "Làm một lần trên máy chủ".
6. Qua LAN: Windows Firewall cho phép inbound TCP 8080, profile Private.

`.env` và file token chứa bí mật cục bộ: không đưa vào Git, ảnh chụp hay báo cáo.

**Mỗi lần chạy:**

```powershell
mvn -q package -DskipTests        # sau mỗi lần đổi code
docker compose up -d --wait
```

Rồi bấm `START-CLOUDFLARE-SERVER.bat` (thêm `-AllowLan` để máy cùng mạng vào được bằng `http://IP_MAY_CHU:8080`). Script mở Server trong cửa sổ riêng với giám sát bật như bản phát hành, chờ Server sẵn sàng, mở connector Cloudflare nếu chưa có, rồi kiểm tra qua Internet. `STATUS-CLOUDFLARE.bat` xem nhanh; `STOP-CLOUDFLARE-SERVER.bat` chỉ dừng Server và connector do script mở.

## 2. Nạp đề STUDY4 (một lần cho mỗi đề)

Thực hiện trên máy có source repository và bản export STUDY4 nội bộ (không commit vào Git):

```powershell
mvn -pl tools/study4-adapter -am package -DskipTests
java -cp "tools/study4-adapter/target/classes;protocol/target/classes;$env:USERPROFILE/.m2/repository/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar" `
  vn.edu.toeic.tools.study4.Study4Adapter --input D:\du-lieu\STUDY4_6852 --output D:\du-lieu\study4-v2 --permission-date 2026-10-22
```

Đặt `TOEIC_AUDIO_ROOT=D:\du-lieu\study4-v2\audio` và `TOEIC_IMAGE_ROOT=D:\du-lieu\study4-v2\images` trong `.env`, khởi động Server, rồi import bằng tài khoản Giám thị (mật khẩu được hỏi, không ghi vào lịch sử lệnh):

```powershell
$server = 'http://127.0.0.1:8080'
$cred = Get-Credential -Message 'Tài khoản Giám thị'
$body = @{ username = $cred.UserName; password = $cred.GetNetworkCredential().Password; requestId = [guid]::NewGuid().ToString() } | ConvertTo-Json
$login = Invoke-RestMethod "$server/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod "$server/api/v2/exams/import" -Method Post -ContentType 'application/json; charset=utf-8' `
  -Headers @{ Authorization = "Bearer $($login.token)" } -InFile 'D:\du-lieu\study4-v2\rich-import.json'
```

Thành công trả HTTP 201. Import lại cùng `examId` trả **409 CONFLICT** và không ghi đè đề đã có; lỗi giữa chừng rollback toàn bộ. Đề dùng media MOCK hoặc thiếu file sẽ không READY được.

## 3. Luồng demo (một Giám thị, hai Thí sinh)

Mỗi người mở **ToeicMonitor** đã cài từ MSI (Server `https://toeic.anhtaizz.id.vn` được đặt sẵn). Thử trên một máy: bấm `START-CLIENT-CLOUDFLARE.bat` hoặc `START-CLIENT-LAN.bat` nhiều lần, mỗi vai một cửa sổ. Qua LAN, máy Thí sinh dùng `http://IP_MAY_CHU:8080` (IP lấy bằng `ipconfig` trên máy chủ), không dùng `localhost` của máy Thí sinh.

1. **Admin** đăng nhập → *Thêm tài khoản* → tạo một Giám thị và hai Thí sinh, tự đặt mật khẩu (8–128 ký tự, nhập hai lần) rồi bàn giao riêng. Thí sinh quên mật khẩu: *Đổi mật khẩu* (không đổi khi người đó đang thi; phiên đăng nhập cũ bị thu hồi). *Khóa*/*Mở khóa* để tạm chặn đăng nhập.
2. **Giám thị** đăng nhập → màn **Phòng thi** → *+ Tạo phòng* → chọn đề → *Tạo phòng* → nhận **PIN 5 số** chung cho cả phòng. PIN tự ẩn sau hai phút (*Ẩn PIN* để ẩn ngay); *Sao chép* xóa clipboard sau 30 giây nếu chưa bị thay.
3. **Thí sinh** (mỗi máy) đăng nhập → nhập PIN → *Vào phòng*. Ở phòng chờ: *Tải âm thanh* → *Thử loa* → *Tôi nghe được* → *Xác nhận sẵn sàng*; ảnh Part 1 được kiểm tự động. Vào lại phòng không tạo lượt thi mới.
4. Giám thị thấy từng thí sinh xuất hiện trong bảng của phòng: "Đang chuẩn bị" → "Sẵn sàng", cùng trạng thái kết nối. Bấm **Bắt đầu phòng thi** và xác nhận.
   - Phòng chưa ai vào: không bắt đầu được.
   - Có người chưa sẵn sàng: hộp xác nhận nêu "Sẵn sàng a / đã vào b"; người chưa sẵn sàng **mất Listening** của lượt này ("Chỉ làm Reading") và làm Reading trong thời gian còn lại, không cộng giờ.
   - Kết quả bắt đầu chưa rõ (mạng chập chờn): *Thử lại bắt đầu* gửi lại đúng yêu cầu cũ.
5. Thí sinh đã sẵn sàng nghe Listening **một lần** rồi làm Reading. Chọn đáp án, chân trang hiện "✓ Đã lưu"; *Nộp bài* và xác nhận → hộp kết quả (số câu đúng, giờ Việt Nam).
6. Giám thị theo dõi trên cùng bảng: "Đang thi · x/y" → "Đã nộp · đúng x/y" hoặc "Hết giờ · đúng x/y"; cột phần nghe, USB, Focus, Cửa sổ và cảnh báo (nhãn "Mới" cho cảnh báo chưa xem). Bấm một hàng để mở chi tiết: trạng thái hiện tại, nguồn tín hiệu, cảnh báo gần đây và *Lịch sử đầy đủ*. Mất heartbeat hiện "Không có tín hiệu" — đó là "quan sát thấy", không phải kết luận gian lận.

Phòng mà mọi lượt đã nộp hoặc hết giờ tự biến khỏi danh sách "Phòng đang mở".

**Demo tiến trình**: trên máy Thí sinh đang làm bài, mở một trong 9 ứng dụng được quan sát (ví dụ Chrome, Edge, Zalo, Teams, Discord, AnyDesk, TeamViewer); Giám thị thấy cảnh báo trên hàng của thí sinh và trong chi tiết, một lần khi tiến trình xuất hiện (không lặp ở mỗi lần quét). Ứng dụng ngoài danh sách (Notepad, PowerShell…) không được quan sát — đó là chính sách `process-policy-v4`, không phải lỗi.

**Demo USB, Focus, Cửa sổ** (máy Thí sinh thật, đang ở màn làm bài): cắm USB và giữ khoảng 10 giây → cột USB hiện "Đã cắm USB · giờ"; USB cắm sẵn từ đầu hiện "Có USB từ đầu · giờ". Chuyển sang cửa sổ khác → "Rời cửa sổ N lần"; thu nhỏ app → "Thu nhỏ N lần". Vi phạm đã quan sát được giữ trên hàng, kể cả khi USB đã rút hay thí sinh đã quay lại app; diễn biến từng lần nằm trong *Lịch sử đầy đủ*. Lỗi đọc USB không được hiểu là "đã rút" và không làm gián đoạn bài thi. Chỉ quan sát USB lưu trữ; không đọc file hay serial, không chặn thiết bị. Thao tác cắm/rút nhanh hơn chu kỳ quét có thể không được ghi.

Tình huống đáng demo:

- **Rớt mạng/reconnect**: Thí sinh mất kết nối giữa Listening → audio dừng, bị khóa, ghi cờ gián đoạn; kết nối lại **không phát lại** và không reset deadline. Giám thị thấy cột phần nghe "Phần nghe bị gián đoạn".
- **Mở bài ở Client khác** (takeover): Client cũ mất quyền ghi; đáp án đã lưu giữ nguyên.
- **Mất phản hồi khi tạo phòng**: không bấm tạo lại; *Làm mới* danh sách, chọn phòng vừa tạo, *Cấp lại PIN* (chỉ trước khi bắt đầu; PIN cũ bị thu hồi).
- Nhập sai PIN 5 lần trong 5 phút khóa vào phòng 5 phút.

## 4. Checklist hai máy thật và nghe thật

Test JavaFX/HTTP/WS cùng một máy không thay thế các bước này. Qua Internet (một máy dùng 4G): làm theo checklist 10 bước ở cuối [CLOUDFLARE-TUNNEL.md](deploy/CLOUDFLARE-TUNNEL.md). Qua LAN:

- [ ] Hai máy cùng LAN; máy chủ chạy `START-CLOUDFLARE-SERVER.bat -AllowLan`; `Test-NetConnection IP_MAY_CHU -Port 8080` thành công từ máy Thí sinh.
- [ ] Đăng nhập; WebSocket kết nối, Giám thị thấy Thí sinh vào phòng.
- [ ] Audio và ảnh tải qua cùng địa chỉ Server, không lỗi thiếu file/checksum.
- [ ] Người thật nghe sample bằng loa/tai nghe; không coi sự kiện MediaPlayer hoàn thành là bằng chứng nghe được.
- [ ] Hai Thí sinh sẵn sàng → bắt đầu → nghe hết Listening không phát lại → Reading → tự lưu → Nộp. Ghi phiên bản, giờ, máy, kết quả thật.

## 5. Sự cố thường gặp

| Hiện tượng | Xử lý |
|---|---|
| "Thiếu file cấu hình" / "Thiếu trong .env: …" | Chép `.env.example` thành `.env` và điền các dòng được nêu. |
| "TOEIC_AUDIO_ROOT trỏ tới thư mục không tồn tại" | Sửa đường dẫn media trong `.env` (tương đối thì tính theo thư mục chứa `.env`). |
| "Chưa có server JAR" | `mvn -q package -DskipTests`. |
| "Server dừng khi khởi động" | Xem cửa sổ Server: thường là PostgreSQL chưa chạy (`docker compose up -d --wait`), sai DB hoặc khóa PIN không hợp lệ. |
| "Cổng 8080 đang bị chương trình khác dùng" | Script không dừng chương trình đó; giải phóng cổng hoặc chạy với `-Port`. |
| Tạo/vào phòng bị từ chối "PIN key" | `TOEIC_ROOM_PIN_KEY` thiếu hoặc đã đổi; dùng lại khóa cũ. |
| Thí sinh không "Sẵn sàng" | Media thiếu/sai checksum hoặc chưa xác nhận nghe được. |
| Client không kết nối | Qua Internet: chạy `STATUS-CLOUDFLARE.bat` và xem mục chẩn đoán trong [CLOUDFLARE-TUNNEL.md](deploy/CLOUDFLARE-TUNNEL.md). Qua LAN: sai IP/cổng, firewall, hai máy khác mạng, hoặc Server chưa chạy với `-AllowLan`. |
| Windows "protected your PC" khi cài MSI | Bộ cài chưa ký số: *More info* → *Run anyway*. |

## 6. Giao diện legacy

Vào phòng bằng mã phòng + PIN cá nhân, ca V1 và các trang Giám thị cũ vẫn có backend và test nhưng **bị ẩn** mặc định. Chỉ bật để kiểm thử hồi quy: đặt `TOEIC_UI_LEGACY=true` trước khi mở Client (hoặc `-Dtoeic.ui.legacy=true`); khi đó màn Phòng thi có thêm nút *Công cụ cũ*. Đây không phải ranh giới phân quyền; quyền vẫn do Server kiểm.
