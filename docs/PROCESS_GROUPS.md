# Danh sách process hiện hành và lịch sử policy — 25/10/2026

**Bản hiện hành: `process-policy-v4`, đúng 9 tên theo yêu cầu người dùng.** Candidate chỉ thu các tên trong bảng dưới, không phân biệt hoa/thường:

| Nhóm | Tên executable hiện hành |
|---|---|
| Trình duyệt | chrome.exe, msedge.exe, firefox.exe |
| Chat/họp | zalo.exe, teams.exe, ms-teams.exe, discord.exe |
| Điều khiển từ xa | anydesk.exe, teamviewer.exe |

V4 là 8 tên gốc của v1 cộng `ms-teams.exe`. Notepad, ứng dụng tài liệu/PDF, terminal và process Windows nằm ngoài danh sách hiện hành. Danh sách chung, số đếm, summary và MON-08 review chỉ xét 9 tên này, kể cả khi đọc dữ liệu cũ. Raw DB/API/Timeline gốc vẫn được giữ để đối chiếu.

USB giữ nguyên nguồn quan sát, wire và cách hiển thị: `USB_STORAGE_PRESENT_AT_START` vẫn có trong feed chung dưới dạng thông tin; cắm/rút USB, focus và cửa sổ không thay đổi. Chỉ quan sát process; không đọc nội dung/arguments, không chặn/kill process người dùng.

## Lịch sử review mở rộng v2

- Giữ schema field/endpoint/WS envelope; thêm `process-policy-v2` cho catalog mở rộng, server/client đọc được v1 cũ. V1 giữ 8 tên/128 process, v2 tối đa 256 process và tập tên hữu hạn. Không gán catalog mới cho trace v1. Cần nâng cấp server cùng client.
- Full/delta/event cùng ProcessPolicy, epoch không đổi policy giữa run; reducer giữ nguyên quy tắc sequence/dedup/resync. Frame tối đa 256 process tên catalog ngắn phải được test dưới 64 KiB; quá hạn mức báo SNAPSHOT_LIMIT/từ chối dữ liệu mới, không cắt snapshot rồi báo sạch; state cuối hết độ mới theo cơ chế STALE hiện có. FULL vẫn mặc định; delta vẫn opt-in.
- Ở v2, background có trong state/nhật ký nhưng không vào Cảnh báo/count/recent-alerts/MON-08 review. Events raw vẫn lưu đủ; MON-08 rules/ngưỡng không thay. Bộ lọc hiển thị hiện hành v4 ở đầu tài liệu thay thế cách hiển thị này.
- Loại PID app hiện tại và PowerShell helper CIM do ứng dụng tạo bằng PID/start; không đổi câu truy vấn CIM/observer approach. Bounded 128 child identities gần nhất tránh race giữa đọc snapshot và child vừa thoát; thiếu start chỉ dùng child còn sống để tránh bỏ nhầm PID reuse. V4 không thu tên PowerShell, kể cả process do người dùng tự mở.
- Trace/replay được khai báo hỗ trợ v1/v2 ở lần mở rộng này; mỗi policy phải giữ membership riêng. Nguồn thiếu metadata vẫn ghi UNREADABLE, không suy bình thường. Process hệ thống không đọc được tên không được bịa tên.

Review này là tự rà kỹ thuật trước sửa code, không là chữ ký owner A/C/B. Người dùng đã yêu cầu triển khai; opt-out codex-with-chatgpt tiếp tục. Không đổi exam/START/deadline/DB schema/OS API/policy gian lận.

## Catalog lịch sử v2 — 39 tên

Lần mở rộng v2 từng nhận trình duyệt, chat/họp, điều khiển từ xa, tài liệu/ghi chú/PDF, terminal/script và danh sách cụ thể các process Windows/dịch vụ nền. Bảng này giữ để đọc dữ liệu cũ, không phải danh sách thu hiện hành.

| Nhóm | Tên executable được nhận (không phân biệt hoa/thường) |
|---|---|
| Trình duyệt | chrome.exe, msedge.exe, firefox.exe |
| Chat/họp | zalo.exe, discord.exe, teams.exe, ms-teams.exe |
| Điều khiển từ xa | anydesk.exe, teamviewer.exe |
| Tài liệu/PDF | winword.exe, excel.exe, notepad.exe, acrord32.exe, acrobat.exe, foxitpdfreader.exe, sumatrapdf.exe |
| Terminal | cmd.exe, powershell.exe, pwsh.exe |
| Nền Windows | svchost.exe, runtimebroker.exe, dwm.exe, explorer.exe, csrss.exe, lsass.exe, services.exe, wininit.exe, winlogon.exe, smss.exe, dllhost.exe, conhost.exe, taskhostw.exe, sihost.exe, searchhost.exe, searchindexer.exe, shellexperiencehost.exe, startmenuexperiencehost.exe, applicationframehost.exe, msedgewebview2.exe |

Tổng 39 tên, không là mọi process hoặc mọi biến thể tên/ứng dụng. App Teams mới dùng `ms-teams.exe` đã được kiểm thấy bằng cả Get-Process và ProcessHandle trên máy này trước lần mở rộng. Tên process không chứng minh nội dung đang đọc/chia sẻ; WebView2 chỉ là nền, không suy thí sinh mở trình duyệt.

## Nâng cấp và chạy

Cần nâng cấp đủ **server, candidate và proctor** để dùng policy v4 cùng bộ lọc hiển thị mới. Script khởi động không tự build; chuẩn bị JAR từ source mới trước. Sau `mvn package`, có thể dùng JAR mặc định với `start-monitoring-server.ps1 -SignalExperiment`. Bản đóng gói riêng cho thay đổi này dùng tên `server-process-nine-20261025.jar`; chọn rõ bằng `-ServerJar` khi chủ động khởi động server:

~~~powershell
.\scripts\start-monitoring-server.ps1 -SignalExperiment -ServerJar .\server\target\server-process-nine-20261025.jar
.\scripts\start-monitoring-client.ps1 -ServerUrl 'http://localhost:8080' -ReadUsbStorage
~~~

Lệnh client có `-ReadUsbStorage` dành cho candidate cần quan sát USB như cấu hình cũ; mở proctor bằng cùng JAR client mới và URL server. Thay địa chỉ bằng server LAN được cấu hình nếu chạy khác máy. Candidate đăng nhập/chọn đúng lượt rồi bật **Bắt đầu giám sát** hoặc vào bài; proctor chọn cùng lượt được phân công.

Lệnh trên không tự reset DB/lượt/deadline hoặc dừng JVM đang chạy. Chỉ nâng cấp và khởi động lại khi đã chủ động sắp xếp phiên làm việc; thay JAR không tự nạp code vào JVM cũ. Chỉ mở lại proctor không bổ sung process chưa từng được candidate gửi. Client cũ vẫn thu theo policy của nó (v1: 8 tên, v2: 39 tên, v3: 31 tên); server cũ không hiểu v4, nên cần nâng cấp đồng bộ cả ba thành phần. Không đổi policy giữa một epoch; phiên giám sát mới bắt đầu bằng full baseline như cũ.

## Giới hạn kiểm chứng

Đây là danh sách hữu hạn, không bảo đảm nhận mọi ứng dụng/biến thể tên hoặc process không đọc được metadata. Hạn mức là 128 process cho v1 và 256 process cho v2/v3/v4, không phải số process tối đa của Windows; quá hạn mức phải báo lỗi, không cắt snapshot. Process sống ngắn hơn chu kỳ poll có thể bị bỏ lỡ. E1/E2 cũ với policy v1 không được gán lại cho các policy sau; chưa đo lại hiệu năng/LAN nhiều máy cho v4. Không cần thư viện mới hoặc quyền admin để thêm tên vào catalog; không yêu cầu nâng quyền để đọc process bị hạn chế. Tài liệu này không xác nhận test mới hoặc owner signoff.

## Lịch sử review thu gọn v3 — 25/10/2026

Ở lần phản hồi tiếp theo, người dùng muốn bỏ đúng 8 tên dễ nhầm: powershell.exe, pwsh.exe, cmd.exe, msedgewebview2.exe, conhost.exe, runtimebroker.exe, dllhost.exe, svchost.exe. V3 giữ 31 tên còn lại của v2 và nguồn USB/focus/window. `process-policy-v3` giữ membership riêng, không đổi nghĩa v2 (39 tên) và v1 (8 tên); full/delta/event cùng policy, epoch không đổi policy, hạn mức 256 như v2. Không đổi DTO field/DB/OS query. Khi v3 là bản hiện hành, danh sách chung và summary bỏ 8 tên này cả khi đọc snapshot/history v2; raw DB/API/Timeline gốc không xóa. Đây là review kỹ thuật trước đổi policy, không thay chữ ký owner A/C/B; người dùng đã cho phép triển khai và giữ opt-out ChatGPT.

Cấu hình v3 vẫn có hạn mức 256 và đọc lại v1/v2 theo catalog cũ; không đổi nguồn USB/focus/window. JAR riêng ở lần này mang tên `server-process-filter-20261025.jar`; lệnh nâng cấp hiện hành phía trên đã chuyển sang bản v4.

Việc loại theo tên áp dụng cả CMD/PowerShell do người dùng tự mở, không chỉ helper của app thi. Các tên này không được candidate v3 ghi nhận nữa; đây là hệ quả trực tiếp của danh sách loại người dùng yêu cầu.

## Review trước thu gọn đúng chín process — 25/10/2026

Người dùng chốt đúng 9 tên ở đầu tài liệu và giữ nguyên USB. Thêm `process-policy-v4` làm policy hiện hành, bảo toàn membership v1 = 8, v2 = 39, v3 = 31. Tập v3 tách khỏi bộ lọc UI để không đổi nghĩa raw/trace cũ; summary, danh sách chung và số đếm chỉ hiện 9 tên, kể cả khi đọc dữ liệu cũ. Không xóa raw DB/API/Timeline, không đổi field/endpoint/DB/OS observer. Full/delta/event/trace mới dùng cùng tập 9 tên, epoch không đổi policy; hạn mức 256 giữ nguyên. Trace/replay kiểm membership và hạn mức theo từng policy v1/v2/v3/v4. USB source/wire/projection, `USB_STORAGE_PRESENT_AT_START`, cắm/rút, focus và cửa sổ giữ nguyên. Review kỹ thuật trước sửa không thay owner signoff A/C/B; người dùng đã ủy quyền code/push và giữ opt-out ChatGPT.
