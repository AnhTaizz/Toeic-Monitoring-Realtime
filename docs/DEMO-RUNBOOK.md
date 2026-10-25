# DEMO RUNBOOK — TOEIC Monitor

Hướng dẫn duy nhất để cài, chạy và demo bản phát hành. Mọi bước dùng bundle Windows portable (`Start-Server.bat`, `Start-Client.bat`). Kết quả kiểm thử của bản này: [release/RELEASE-REPORT.md](release/RELEASE-REPORT.md).

## 0. Cần chuẩn bị

| Thứ | Ghi chú |
|---|---|
| Máy Server Windows 11 | Chạy Server + PostgreSQL 18. Có thể kiêm luôn cửa sổ Admin/Giám thị. |
| Máy Thí sinh | Cùng mạng LAN với Server; chỉ cần bundle (có Java 21 sẵn). |
| Đề thi đã chuyển sang định dạng V2 | Đề STUDY4 nội bộ: dùng `tools/study4-adapter` (mục 2). Không phát hành lại nội dung bên thứ ba. |
| Loa/tai nghe | Cho phần thử loa và Listening. |

## 1. Cấu hình Server (một lần)

1. Tạo database và user riêng trong PostgreSQL 18 (ví dụ `toeic`/`toeic`). Không mở cổng 5432 ra LAN. Nâng cấp từ bản cũ: **sao lưu DB trước**; Flyway tự áp dụng V19–V21 khi khởi động, không reset dữ liệu cũ.
2. Giải nén bundle, chép `server.env.example` thành `server.env` cùng thư mục, điền mọi dòng. Media root là **đường dẫn tuyệt đối** tới thư mục `audio` và `images` của đề đã chuyển (mục 2).
3. Tạo khóa PIN một lần (PowerShell), dán dòng in ra vào `server.env`; giữ nguyên qua mọi lần khởi động:

   ```powershell
   $b = New-Object byte[] 32
   $r = [Security.Cryptography.RandomNumberGenerator]::Create()
   try { $r.GetBytes($b) } finally { $r.Dispose() }
   'TOEIC_ROOM_PIN_KEY=' + [Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_')
   ```

4. Lần đầu điền `TOEIC_ADMIN_BOOTSTRAP_USERNAME` và `TOEIC_ADMIN_BOOTSTRAP_PASSWORD` (20–72 ký tự, khác seed password). Không có đăng ký công khai.
5. `Start-Server.bat -ValidateOnly` kiểm cấu hình mà không khởi động. Sau đó `Start-Server.bat`: launcher kiểm Java, biến bắt buộc, khóa PIN, thư mục media, kết nối PostgreSQL và cổng 8080, rồi mới chạy. Giữ cửa sổ mở; `Ctrl+C` để dừng.
6. Windows Firewall: cho phép inbound TCP 8080, profile Private.

`server.env` chứa bí mật cục bộ: không đưa vào ZIP, Git, ảnh chụp hay báo cáo.

## 2. Nạp đề STUDY4 (một lần cho mỗi đề)

Thực hiện trên máy có source repository và bản export STUDY4 nội bộ (không commit vào Git):

```powershell
mvn -pl tools/study4-adapter -am package -DskipTests
java -cp "tools/study4-adapter/target/classes;protocol/target/classes;$env:USERPROFILE/.m2/repository/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar" `
  vn.edu.toeic.tools.study4.Study4Adapter --input D:\du-lieu\STUDY4_6852 --output D:\du-lieu\study4-v2 --permission-date 2026-10-22
```

Đặt `TOEIC_AUDIO_ROOT=D:\du-lieu\study4-v2\audio` và `TOEIC_IMAGE_ROOT=D:\du-lieu\study4-v2\images` trong `server.env`, khởi động Server, rồi import bằng tài khoản Giám thị (mật khẩu được hỏi, không ghi vào lịch sử lệnh):

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

Mở `Start-Client.bat` nhiều lần (mỗi vai một cửa sổ). Máy Thí sinh nhập `http://IP_SERVER:8080` (IP lấy bằng `ipconfig` trên máy Server), không dùng `localhost` của máy Thí sinh.

1. **Admin** đăng nhập → *Quản lý tài khoản* → tạo một Giám thị và hai Thí sinh. Mỗi mật khẩu ngẫu nhiên chỉ hiện **một lần**; bàn giao riêng. Mất mật khẩu: *Cấp mật khẩu* (không cấp khi người đó đang thi; token cũ bị thu hồi).
2. **Giám thị** đăng nhập → *Điều khiển ca* → *Phòng thi TOEIC* → chọn đề → *Tạo phòng* → nhận PIN 5 số. PIN tự ẩn sau hai phút; nút Copy xóa clipboard sau 30 giây.
3. **Thí sinh** (mỗi máy) đăng nhập bằng tài khoản được cấp → nhập PIN → *Vào phòng*. Tải/chuẩn bị audio, *Thử loa*, *Tôi nghe được*, *Xác nhận sẵn sàng*; ảnh Part 1 được kiểm tự động. JOIN lặp không tạo lượt thi mới.
4. Giám thị theo dõi JOINED, kết nối và READY rồi bấm **START**.
   - Phòng chưa ai JOIN: START bị từ chối.
   - Có người chưa READY: một hộp xác nhận; người đó **mất Listening** của lượt này và làm Reading trong thời gian còn lại, không cộng giờ.
   - Tất cả READY: START trực tiếp.
5. Thí sinh READY nghe Listening **một lần** rồi làm Reading. Chọn đáp án, chờ tự lưu, *Nộp bài* và xác nhận.
6. Giám thị xem *Tổng quan*, *Cảnh báo*, *Timeline đa tín hiệu*. Mất heartbeat hiển thị UNKNOWN — "quan sát thấy", không phải kết luận gian lận.

Tình huống đáng demo:

- **Rớt mạng/reconnect**: Thí sinh mất kết nối giữa Listening → audio dừng, bị khóa, ghi cờ gián đoạn; kết nối lại **không phát lại** và không reset deadline.
- **Mở bài ở Client khác** (takeover): Client cũ mất quyền ghi; đáp án đã lưu giữ nguyên.
- **Mất response tạo phòng**: không tạo lại; tải danh sách phòng, chọn phòng vừa tạo, *Cấp lại PIN* (chỉ trước START).
- Nhập sai PIN 5 lần trong 5 phút khóa JOIN 5 phút.

## 4. Checklist LAN hai máy và nghe thật

Kết quả của bản hiện tại ghi trong [báo cáo phát hành](release/RELEASE-REPORT.md). Test JavaFX/HTTP/WS cùng một máy không thay thế các bước này.

- [ ] Hai máy cùng LAN; `Test-NetConnection IP_SERVER -Port 8080` thành công từ máy Thí sinh.
- [ ] Đăng nhập qua HTTP; WebSocket kết nối, dashboard Giám thị thấy Thí sinh.
- [ ] Audio và ảnh tải qua cùng Server URL, không lỗi thiếu file/checksum.
- [ ] Người thật nghe sample bằng loa/tai nghe; không coi sự kiện MediaPlayer hoàn thành là bằng chứng nghe được.
- [ ] Hai Thí sinh READY → START → nghe hết Listening không phát lại → Reading → tự lưu → Nộp. Ghi phiên bản, giờ, máy, kết quả thật.

## 5. Sự cố thường gặp

| Hiện tượng | Xử lý |
|---|---|
| `Missing ...` khi chạy Server | Thiếu dòng trong `server.env`. |
| `Cannot reach PostgreSQL` | PostgreSQL chưa chạy hoặc sai `DB_HOST`/`DB_PORT`. |
| `Port 8080 is already in use` | Server cũ còn chạy; dừng bằng `Ctrl+C` hoặc `Start-Server.bat -Port 8090`. |
| Tạo/vào phòng bị từ chối "PIN key" | `TOEIC_ROOM_PIN_KEY` thiếu hoặc đã đổi; dùng lại khóa cũ. |
| Thí sinh không READY | Media thiếu/sai checksum hoặc chưa xác nhận nghe được. |
| Client không kết nối | Sai IP/cổng, firewall, hai máy khác mạng. |

## 6. Giao diện legacy

Vào phòng bằng mã phòng + PIN cá nhân, ca V1 và tab phòng V2 cũ vẫn có backend và test nhưng **bị ẩn** trong bản phát hành. Chỉ bật để kiểm thử hồi quy: đặt `TOEIC_UI_LEGACY=true` trước khi mở Client (hoặc `-Dtoeic.ui.legacy=true`). Đây không phải ranh giới phân quyền; quyền vẫn do Server kiểm.
