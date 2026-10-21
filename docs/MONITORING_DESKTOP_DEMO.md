# Demo desktop monitoring TEST

Chạy từ main đã xác minh, dùng hai ứng dụng production JAR/JVM riêng. Không dùng JavaFX programmatic để đăng nhập hoặc tạo thao tác người dùng. Đề Reading một câu là dữ liệu TEST tự soạn; process TEST là bản sao `cmd.exe` mang tên `chrome.exe` để đi qua policy hiện có, **không phải browser thật hoặc kết luận vi phạm**. Không có observer USB hay nguồn sự kiện MOCK trong chế độ này.

Điều kiện: Windows desktop, JDK21 (`JAVA_HOME`), Maven trên PATH, PostgreSQL18 binaries. Từ checkout sạch đã commit:

```powershell
.\scripts\demo-mon08-alerts.ps1 -Desktop
```

Có thể truyền `-JavaHome` và `-PostgresBin`. Runner hiện có tạo cluster/database TEST loopback với SCRAM, mật khẩu ngẫu nhiên, không đọc `.env`/DB dev. Build JAR từ HEAD sạch, ghi SHA/hash trong `target/usb-integrated-*/desktop-build-receipt.json`; bật signal/alert experiment cho các process demo, FULL giữ mặc định, defaults repository không đổi. Không tạo installer/kit khác.

Nếu PowerShell hiện chặn script, dùng `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-mon08-alerts.ps1 -Desktop` (chỉ process này, không đổi policy hệ thống). `-JavaHome` cần trỏ tới JDK21 nếu chưa đặt JAVA_HOME.

Hai cửa sổ app và Notepad hướng dẫn/credential TEST sẽ mở. Mật khẩu chỉ dùng cục bộ trong lượt chạy, không công bố file private. Cùng bản JAR, hai JVM độc lập; app thứ nhất dùng candidate1, app thứ hai proctor1.

1. Đăng nhập, candidate chọn attempt `DESKTOP-…-candidate1`, bấm **Bắt đầu giám sát**, sau đó **Vào phòng thi & làm bài** để gắn observer focus/window/display/activity đúng phạm vi màn thi.
2. Proctor chọn **Timeline đa tín hiệu** trong menu trái, ngay dưới **Nhật ký giám sát**; chọn đúng attempt, bộ lọc **Tất cả nguồn**, cuộn xuống bảng lịch sử bên dưới nhóm cảnh báo. Focus/window có loại EXAM_FOCUS_CHANGED / EXAM_WINDOW_STATE. Phần Process hiện tại nằm dưới bảng, tách biệt history. Kiểm baseline/display/status, không suy USB từ display.
3. Mở `chrome.exe` ngay trong thư mục output (process TEST thật, vô hại). Chuyển focus, thu nhỏ/khôi phục candidate, chọn đáp án. Chờ ít nhất 35 giây nếu cần bản tổng hợp activity theo chu kỳ mặc định. Proctor kiểm process, focus/window và timeline. Các cảnh báo chỉ EXPERIMENTAL, không duyệt policy/gian lận.
4. Đóng process TEST bằng `exit`. Candidate chọn **Thoát màn thi** và xác nhận rời phòng thi: ứng dụng tự dừng các observer/collector; nút **Dừng giám sát** ở cổng thí sinh sẽ mờ. Nếu nút còn bật thì bấm Dừng trước khi đăng xuất. Đăng xuất cả hai app, xác nhận về login rồi đóng chúng. Không nộp bài trước khi đối chiếu history vì quyền ACTIVE được áp dụng.

Runner quan sát API/history thật, phân trang tối đa 20 trang/100 dòng có cờ truncated, ghi thời gian và loại sự kiện trong output ignored/private. Không tự đánh dấu desktop PASS: cần xác nhận thao tác và màn proctor từ người dùng. Không đọc câu trả lời trong hoạt động monitoring, không phát sinh sự kiện thủ công. App production vẫn dùng collector process hiện có, không thay nguồn bằng seam TEST.

Khi cả hai app đóng, hoặc hết ngân sách 60 phút/đặt file `stop.request` trong output: runner dọn đúng app/server/workload mình sở hữu, drop database TEST, stop cluster. Xem `desktop-cleanup.json` và `result.json`; việc window biến mất chưa đủ để gọi cleanup PASS. Không kill process theo tên, không root clean. Nếu abort/đóng shell cưỡng bức, giữ output để kiểm tài nguyên sở hữu trước khi dọn.

USB hotplug, màn hình thứ hai và LAN vẫn NOT_RUN. Gate DELTA NOT_DECIDED; không chuyển MON task DONE hay thay human owner approval. Computer Use lỗi khởi tạo kernel assets; lượt này do người dùng thao tác desktop thật. Kết quả demo và giới hạn xem [report](../evidence/monitoring-desktop-20261021/REPORT.md).

Tên candidate/proctor trong PID/profile của runner chỉ là nhãn lúc mở. Người dùng có thể đăng nhập role theo thứ tự ngược; đối chiếu role thật theo server/UI/worker, không suy từ nhãn PID. Nút tài khoản mẫu ở login xóa ô password: chọn account trước rồi dán mật khẩu TEST sau. Lượt handoff đầu đã xác nhận API và LoginApiClient trong production JAR đăng nhập được cả hai account; người dùng xác nhận đã vào, server SYNCED và có các tín hiệu FX thật. Lượt tiếp theo đã được người dùng xác nhận nhìn thấy Chrome, focus/window trên proctor, thoát màn thi (Dừng mờ), đăng xuất và đóng hai app; xem report để đối chiếu nguồn build và cleanup.
