# Khảo sát phương pháp thu thập process — T1-C4

Ngày tra nguồn: 05/10/2026. Đây là khảo sát lựa chọn kỹ thuật, chưa phải thực nghiệm E1/E2. “Process” là một chương trình đang chạy; “polling” là quét lại theo chu kỳ; “sự kiện” là thông báo khi một việc xảy ra.

| Tiêu chí | Java ProcessHandle polling | Windows WMI ProcessStartTrace |
|---|---|---|
| Cơ chế | Mỗi chu kỳ gọi allProcesses rồi đọc info của những process nhìn thấy. Đây là ảnh tại thời điểm quét. | Đăng ký nhận Win32_ProcessStartTrace khi process bắt đầu; không tự cung cấp ảnh đầy đủ các process đã chạy trước đăng ký. |
| API/platform | JDK từ 9; hỗ trợ phụ thuộc OS. Repo dùng JDK21 trên Windows. | Windows, namespace Root/CIMV2; tài liệu nêu Vista/Server2008 trở lên. |
| Tích hợp Java | API JDK sẵn có; repo đã có ProcessHandleSnapshotSource và ProcessCollector. | Phải có cầu nối Windows hoặc chương trình phụ để đăng ký và chuyển thông báo vào Java. Đây là phương án thiết kế suy luận, chưa triển khai; C4 không thêm JNI/JNA. |
| Quyền/điều kiện | OS giới hạn process và thông tin mà chương trình được đọc; không mặc định mọi metadata đều có. | Quyền nhận sự kiện phụ thuộc security descriptor của provider và cấu hình WMI; chưa kiểm trên máy chạy repo. Không khẳng định mọi tài khoản đều nhận được. |
| Metadata | PID; info có command/startInstant/user/CPU… khi có. Repo chỉ giữ tên executable, thời điểm bắt đầu nullable và việc đọc user có thành công; không giữ username/path/arguments. | ProcessID, ParentProcessID, ProcessName, SessionID, Sid và TIME_CREATED; thời gian UTC theo đơn vị 100ns từ 1601. Nếu triển khai sau này phải lọc dữ liệu nhạy cảm trước log. |
| Giới hạn | Process có thể thay đổi/chết trong lúc đọc; PID có thể tái sử dụng. Suy luận từ quét định kỳ: process sống hoàn toàn giữa hai poll có thể bị bỏ sót. Thiếu metadata không có nghĩa máy sạch. | Riêng start event không chứng minh trạng thái hiện tại hoặc sự kết thúc. Chưa đo độ tin cậy, tốc độ hay mất sự kiện trên hệ thống này; không suy ra “không bao giờ bỏ sót”. |
| Độ phức tạp trong repo | Thấp hơn theo đánh giá thiết kế: dùng trực tiếp JDK, worker và policy hiện có. | Cao hơn theo đánh giá thiết kế: thêm phụ thuộc Windows, đăng ký/hủy đăng ký, chuyển dữ liệu và xử lý khi nguồn hỏng. Chưa đo chi phí. |
| Trạng thái | Đã triển khai C1/C2. C4 chạy REAL collector scan riêng; event và overflow trong demo byte dùng snapshot MOCK, không coi chúng là process thật. | Chỉ khảo sát tài liệu; triển khai/chạy thực tế: NOT RUN. |
| Nguồn chính thức | [Oracle ProcessHandle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.html), [Oracle ProcessHandle.Info](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.Info.html). | [Microsoft Win32_ProcessStartTrace](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/krnlprov/win32-processstarttrace). |

Nhóm giữ ProcessHandle polling vì phù hợp Java/JDK và phần collector đã có, ít công tích hợp thêm trong thời gian môn học. Đây là lựa chọn triển khai, không phải kết luận rằng polling nhanh nhất. E1 về khả năng quan sát và E2 về chi phí message còn phải chạy theo kế hoạch. Chưa có bằng chứng novelty hay lợi ích full/delta; không triển khai WMI/ETW, Windows service hoặc cầu nối native trong C4.
