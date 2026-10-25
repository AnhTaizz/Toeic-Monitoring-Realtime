# Mở rộng nhóm process — 25/10/2026

**Bản hiện hành sau phản hồi người dùng: process-policy-v3,31tên.** Đã bỏ đúng8tên dễ nhầm ở mục review cuối; bảng39tên bên dưới là catalogv2 được giữ để đọc dữ liệu cũ. Chúng được ẩn khỏi summary/danh sách chung, raw DB/API/Timeline vẫn giữ để đối chiếu. Các process Windows khác còn trong catalog chỉ có trạng thái/nhật ký, không tự tăng cảnh báo.

Theo yêu cầu người dùng: trình duyệt, chat/họp (cả teams.exe/ms-teams.exe), điều khiển từ xa, tài liệu/ghi chú/PDF, terminal/script và một danh sách cụ thể các process Windows/dịch vụ nền. Đây là catalog có tên rõ ràng, không phải mọi exe trên máy. Chỉ quan sát; không đọc nội dung/arguments, không chặn/kill process người dùng.

## Review kỹ thuật trước thay đổi contract

- Giữ schema field/endpoint/WS envelope; thêm policy process-policy-v2 cho catalog mở rộng, server/client đọc được v1 cũ. V1 giữ8tên/128process, V2 tối đa256process và tập tên hữu hạn. Không gán catalog mới cho trace v1. Cần nâng cấp server cùng client.
- Full/delta/event cùng ProcessPolicy, epoch không đổi policy giữa run; reducer giữ nguyên quy tắc sequence/dedup/resync. Frame tối đa256 process tên catalog ngắn phải được test dưới64KiB; quá hạn mức báo SNAPSHOT_LIMIT/từ chối dữ liệu mới, không cắt snapshot rồi báo sạch; state cuối hết độ mới theo cơ chế STALE hiện có. FULL vẫn mặc định; delta vẫn opt-in.
- Phân loại hiển thị dùng chung catalog. Background có trong state/nhật ký nhưng không vào Cảnh báo/count/recent-alerts/MON08 review. Events raw vẫn lưu đủ; MON08 rules/ngưỡng không thay.
- Loại PID app hiện tại và PowerShell helper CIM do ứng dụng tạo bằng PID/start, không loại cả tên powershell/java. Không đổi câu truy vấn CIM/observer approach; chỉ theo dõi danh tính child đã được app sở hữu. Bounded128 child identities gần nhất tránh race giữa đọc snapshot và child vừa thoát; thiếu start chỉ dùng child còn sống để tránh bỏ nhầm PID reuse.
- Trace/replay chấp nhận policyv1/v2, kiểm membership theo đúng policy. Nguồn thiếu metadata vẫn ghi UNREADABLE, không suy bình thường. Process hệ thống không đọc được tên không được bịa tên.

Review này là tự rà kỹ thuật trước sửa code, không là chữ ký owner A/C/B. Người dùng đã yêu cầu triển khai; opt-out codex-with-chatgpt tiếp tục. Không đổi exam/START/deadline/DB schema/OS API/policy gian lận.

## Danh sách triển khai

| Nhóm | Tên executable được nhận (không phân biệt hoa/thường) |
|---|---|
| Trình duyệt | chrome.exe, msedge.exe, firefox.exe |
| Chat/họp | zalo.exe, discord.exe, teams.exe, ms-teams.exe |
| Điều khiển từ xa | anydesk.exe, teamviewer.exe |
| Tài liệu/PDF | winword.exe, excel.exe, notepad.exe, acrord32.exe, acrobat.exe, foxitpdfreader.exe, sumatrapdf.exe |
| Terminal | cmd.exe, powershell.exe, pwsh.exe |
| Nền Windows | svchost.exe, runtimebroker.exe, dwm.exe, explorer.exe, csrss.exe, lsass.exe, services.exe, wininit.exe, winlogon.exe, smss.exe, dllhost.exe, conhost.exe, taskhostw.exe, sihost.exe, searchhost.exe, searchindexer.exe, shellexperiencehost.exe, startmenuexperiencehost.exe, applicationframehost.exe, msedgewebview2.exe |

Tổng39tên, không là mọi process hoặc mọi biến thể tên/ứng dụng. App Teams mới dùng ms-teams.exe đã được kiểm thấy bằng cả Get-Process và ProcessHandle trên máy này trước sửa. Tên process không chứng minh nội dung đang đọc/chia sẻ; WebView2 chỉ là nền, không suy thí sinh mở trình duyệt.

## Nâng cấp và chạy

Cần cả server và client mới. Script không tự build; đóng/mở JVM không tự thay JAR server cũ. Sau mvn package mặc định dùng start-monitoring-server.ps1 -SignalExperiment như cũ. Nếu server cũ đang khóa JAR mặc định, bản verify được đóng gói riêng, có thể chọn bằng tham số mới khi tự khởi động lại server:

~~~powershell
.\scripts\start-monitoring-server.ps1 -SignalExperiment -ServerJar .\server\target\server-process-filter-20261025.jar
.\scripts\start-monitoring-client.ps1 -ServerUrl 'http://localhost:8080' -ReadUsbStorage
~~~

Thay địa chỉ bằng server LAN được cấu hình nếu chạy khác máy. Mở lại app giám thị bằng client mới, đăng nhập/chọn đúng lượt; candidate bật Bắt đầu giám sát hoặc vào bài. Không tự reset DB/lượt/deadline. Chỉ mở lại proctor không bổ sung process chưa từng được candidate gửi; client cũ policyv1 vẫn chỉ thu8tên. Server cũ không chấp nhận full policyv2 nên phải nâng cấp đồng bộ.

## Giới hạn kiểm chứng

Đây là mở rộng catalog hữu hạn, không bảo đảm nhận mọi ứng dụng/biến thể tên hoặc process không đọc được metadata. Giới hạn256 được kiểm ở DTO/frame/reducer, không là số process tối đa của Windows. Process sống ngắn hơn chu kỳ poll có thể bị bỏ lỡ. E1/E2 cũ với policyv1 không được gán lại cho catalog39tên; chưa đo lại hiệu năng/LAN nhiều máy cho policyv2. Không cần thư viện mới hoặc quyền admin để thêm tên vào catalog; không yêu cầu nâng quyền để đọc process bị hạn chế.

## Review trước điều chỉnh theo phản hồi tiếp theo — 25/10/2026

Người dùng muốn bỏ những tên dễ nhầm vừa được liệt kê. Bỏ đúng8tên: powershell.exe, pwsh.exe, cmd.exe, msedgewebview2.exe, conhost.exe, runtimebroker.exe, dllhost.exe, svchost.exe. Giữ các tên khác (gồm Teams) và nguồn USB/focus/window. Thêm process-policy-v3/current31tên, không đổi nghĩa v2/39 và v1/8; full/delta/event cùng policy, epoch không đổi policy, limit256 nhưv2. Không đổi DTO field/DB/OSquery. Consumer danh sách chung và summary bỏ8tên cả khi đọc snapshot/historyv2; raw DB/API/Timeline gốc không xóa. Đây là review kỹ thuật riêng trước đổi policy, không thay chữ ký owner A/C/B; người dùng đã cho phép triển khai, opt-outChatGPT giữ.

Cấu hìnhv3 vẫn limit256 và đọc lại v1/v2 theo catalog cũ; không đổi nguồn USB/focus/window. Khi khởi động lại dùng server-process-filter-20261025.jar ở lệnh trên và mở lại cả2client mới.

Việc loại theo tên áp dụng cả CMD/PowerShell do người dùng tự mở, không chỉ helper của app thi. Các tên này không được candidatev3 ghi nhận nữa; đây là hệ quả trực tiếp của danh sách loại người dùng yêu cầu.
