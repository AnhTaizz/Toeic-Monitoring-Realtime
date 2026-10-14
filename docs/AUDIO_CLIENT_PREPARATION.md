# T3-B1 · Chuẩn bị audio trên JavaFX candidate

Nhánh `feat/t3-b1-audio-download-readiness` tạo từ main `2e784fa`. Client dùng [contract T3-A1](AUDIO_RESOURCES.md), không đổi API hoặc migration server. Chỉ role CANDIDATE có phần **Chuẩn bị Listening**; PROCTOR giữ dashboard hiện có. Các nhánh T1-B4 và Reading chưa merge không được ghép vào task này.

## Cách dùng

1. Đăng nhập candidate vào server thật, chờ WebSocket kết nối. Chọn một lượt trong scope server cấp.
2. Bấm **Tải / chuẩn bị lại audio**. Lần đầu trong phiên ứng dụng, client gọi takeover để lấy writer mới; thao tác này bắt đầu deadline theo QD-21. Lần tải lại cùng lượt trong cùng phiên giữ writer. Writer bị thay thế cần đăng nhập lại.
3. Client gửi `ready=false` và chờ response hợp lệ trước khi lấy manifest/tải file. Nếu ca đã start hoặc hết hạn, server từ chối chuẩn bị; client không tự phát lại audio đề.
4. Thanh tiến độ theo tổng byte manifest. SHA-256 và kích thước từng file phải khớp mới cho thử loa. File thiếu/sai hiện tên file và cho tải lại.
5. Bấm **Thử loa** để phát WAV PCM mẫu 440 Hz, một giây, riêng với audio đề. Khi mẫu phát hết, bấm **Tôi nghe được** nếu thực sự nghe được. Lỗi Media/timeout 15 giây chặn bước xác nhận và báo `ready=false` khi còn quyền.
6. Bấm **Xác nhận sẵn sàng**. Client xóa READY cũ, đọc lại toàn bộ file local, tính checksum thực, rồi gửi READY true với manifest/writer/checksums/speakerTestPassed. Chỉ response đúng requestId/attempt/writer/manifest và có readyAt mới hiện **Server đã xác nhận READY**.

Tải, ghi file, băm và HTTP đều ở worker `toeic-audio-preparation`. JavaFX chỉ vẽ và điều khiển MediaPlayer; callback worker được gộp trước `Platform.runLater` để tránh một hàng đợi UI cho mỗi chunk tải. Các thao tác audio cần mạng khóa khi WebSocket mất kết nối. Reconnect yêu cầu bấm chuẩn bị lại, không tự xác nhận hoặc phát mẫu/đề. Callback media và công việc cũ không được cập nhật lượt/màn mới sau đổi lựa chọn, mất mạng hoặc đóng view.

Scope audio lấy từ login; nếu vừa được cấp thêm lượt sau login, đăng nhập lại để lấy danh sách mới. Takeover của màn audio độc lập với collector/syncEpoch của C. Khi ghép nhánh Reading, B phải truyền cùng writer hiện tại vào phần audio, tránh takeover lần hai lúc đang làm bài.

## Cache và giới hạn

Cache mặc định: `%LOCALAPPDATA%/TOEIC Monitor/audio-cache` (fallback thư mục người dùng nếu không có LOCALAPPDATA). Có thể cấu hình `-Dtoeic.audio.cacheDirectory=<thư mục có quyền ghi>`. Trong development với Maven JavaFX, truyền qua `JAVA_TOOL_OPTIONS`.

Mỗi lượt/manifest có thư mục riêng: hash attemptId và manifestId; file audio giữ tên Unicode. Client encode tên thành URI path segment, credential chỉ ở Bearer header, không theo redirect. Manifest giới hạn 100 file, 64 MiB/file và 256 MiB tổng; JSON tối đa 1 MiB; HTTP connect 5 giây, response/header và body tối đa 30 giây mỗi bước. Tên đường dẫn Windows nguy hiểm, reserved device, file trùng khác hoa/thường và link/junction trong cache bị từ chối.

Tải vào `.part` ngẫu nhiên trong thư mục manifest, chỉ thay file đích khi byte/checksum khớp; lỗi luôn dọn `.part`. Cache exam giữ trên đĩa để T3-B2 dùng; mẫu thử loa được dọn khi đóng view. Không có tự động dọn toàn bộ cache lâu ngày trong B1. Trước phát đề, B2 phải gọi lại `AudioFiles.verify(manifest)`: READY trước đó không chứng minh file chưa bị xóa/sửa sau ACK.

## Kiểm chứng và đóng gói

```powershell
# Dùng JDK 21; Maven offline cần dependency đã có trong cache.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b1.ps1 -JavaHome '<JDK21>'
# .env/DB đang chạy; PostgreSQL test dùng schema riêng, tự dọn.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b1.ps1 -JavaHome '<JDK21>' -Postgres -PackageSmoke
# Bản app-image để chạy ứng dụng, không kèm launcher TEST:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-t3b1.ps1 -JavaHome '<JDK21>'
# Chỉ kiểm lại package, không chạy lại Maven/DB:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3b1-package.ps1 -JavaHome '<JDK21>'
```

Script package tạo thư mục mới dưới `jpackage-out/`, không xóa output cũ. `-WithAudioSmoke` chỉ thêm launcher `AudioSmoke.exe` vào app-image kiểm thử; harness nằm ở test sources, không vào JAR production. Smoke dùng view/cache/MediaPlayer production và runtime đóng gói, API MOCK, click xác nhận nghe tự động. Nó kiểm phản hồi UI trong lúc takeover chậm, xóa/sửa file local, READY sau ACK, Media lỗi, Unicode/khoảng trắng và shutdown; không xác nhận người thật nghe được loa.

`-Postgres` đọc `.env` và dùng các giá trị mặc định development như script A1/A2; DB phải đang chạy. Có thể chỉ định `-DatabasePort <port>` cho DB kiểm thử khác cổng, không sửa `.env`. Không đoán credential hoặc đổi mật khẩu DB khi xác thực thất bại. Script UTF-8 BOM để Windows PowerShell 5.1 giữ đúng đường dẫn tiếng Việt.

Build package cần Windows SDK `mt.exe` (tự tìm bản x64 mới nhất hoặc `-ManifestTool <mt.exe>` khi gọi script package). JDK 21 launcher ban đầu không tìm được runtime ở đường dẫn tiếng Việt ngoài code page của Windows trên máy này. Script build với tham số đường dẫn ASCII, giữ manifest gốc về DPI/quyền, thêm `activeCodePage=UTF-8` vào đúng EXE mới sinh rồi copy image sang đường dẫn Unicode. Không sửa JDK, DLL hay locale hệ thống. Windows hỗ trợ code page UTF-8 theo manifest từ phiên bản 1903; xem [tài liệu Microsoft](https://learn.microsoft.com/en-us/windows/apps/design/globalizing/use-utf8-code-page). Khả năng chạy trên Windows cũ hơn chưa được nghiệm thu.

T3-B2/B3 sẽ nối WS start, deduplicate startId, playback và gián đoạn theo [T3-A2](LISTENING_LIFECYCLE.md). B1 không autoplay từ HTTP/WS/status. App-image là runtime đóng gói, không phải bộ cài EXE/MSI của T3-B5. Review A, thử nghe thủ công và LAN/máy thứ hai phải ghi riêng trong [bằng chứng T3-B1](../evidence/t3-b1/2026-10-14-verification.md).
