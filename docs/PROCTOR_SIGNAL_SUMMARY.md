# Chỉ báo đa tín hiệu trên Tổng quan và Chi tiết thí sinh

Theo điều chỉnh người dùng ngày 25/10/2026, USB/focus/cửa sổ dùng chung thao tác bắt/dừng với process và cùng khối giám sát trong Chi tiết thí sinh:

| Màn hình | Nội dung |
| --- | --- |
| Tổng quan | Danh sách thí sinh và nút Chi tiết; không còn cột USB/focus/cửa sổ riêng. |
| Chi tiết thí sinh | Khối Đang quan sát trên máy thí sinh gồm tiến trình, USB, focus và trạng thái cửa sổ; có giờ client quan sát, giờ server nhận, chất lượng metadata và lần cắm/rút USB gần nhất. |
| Timeline đa tín hiệu | Lịch sử thay đổi. Nút Xem Timeline của từng nguồn trong chi tiết chọn đúng lượt thi và nguồn, kể cả lúc trang lịch sử đầu tiên chưa tải xong. |

## Ý nghĩa dữ liệu

Các giá trị là **quan sát gần nhất đã được server ghi nhận**, không phải khẳng định trạng thái phần cứng tại thời điểm đang xem. Focus/cửa sổ nói về Stage ứng dụng thí sinh đang dùng trong phiên giám sát được cấp quyền; không quan sát cửa sổ ứng dụng khác. USB chỉ gồm thiết bị lưu trữ được nguồn Windows quan sát, không đọc nội dung hoặc serial.

Nút **Bắt đầu giám sát** tại trang thí sinh nay bật process và các nguồn được opt-in cùng nhau sau fresh scope/capability/heartbeat. Không còn phụ thuộc việc mở được màn làm bài. Vào bài thi giữ nguyên collector/run/sequence, chỉ bật thêm bộ đếm tương tác trong bài; không phát baseline USB lần hai. Nút **Dừng giám sát** đóng cả hai đường và chỉ cho bật lại sau khi collector cùng worker USB đã dừng. Logout/rời phiên/thu hồi quyền cũng đóng các nguồn. Dữ liệu process và typed signal vẫn giữ schema riêng; USB không bị giả thành process/PID.

- Không có observation thì hiện chưa xác định; không suy USB bằng 0 từ `OBSERVER_STATUS READY`, lịch sử rỗng hoặc nguồn bị tắt.
- Mất kết nối, presence không ONLINE, lần đọc lỗi/quá hạn, hoặc lịch sử chưa đọc đủ làm chỉ báo chưa xác định/dữ liệu cũ. Chi tiết có thể giữ giá trị cuối cùng cùng thời điểm để đối chiếu.
- `SIGNAL_GAP` làm các observation trước gap chưa đủ để xác nhận nguồn. READY sau gap không khôi phục số USB cũ; phải có observation mới.
- Metadata `PARTIAL` được ghi rõ, không coi là định danh thiết bị chắc chắn. Màu chỉ giúp giám thị xem xét, không kết luận gian lận.
- Khi chuyển thí sinh, thay collector/run, mất scope hoặc đóng phiên, không hiển thị observation của lượt/run trước cho lượt đang chọn.

## Cách đọc và giới hạn

`CandidateSignalsController` dùng một HTTP client riêng cho khối giám sát trong Chi tiết, mượn kết nối WebSocket hiện có để biết trạng thái kết nối. Mỗi chu kỳ xác thực lại scope trước khi đọc endpoint typed history hiện hữu. Không đổi DTO/wire, migration hoặc API quan sát OS.

Chu kỳ mặc định 3 giây khi rảnh, ngân sách 12 giây, tối đa hai lượt đọc đồng thời; tối đa 100 lượt và ba trang × 100 event/lượt/chu kỳ. Lượt đang mở chi tiết được ưu tiên; các lượt khác luân phiên. Khi vượt giới hạn hoặc hết ngân sách, giao diện phải giữ trạng thái chưa xác định/cũ. Không cam kết độ trễ 3 giây trong mạng chậm hoặc roster lớn.

Reader giữ con trỏ fixed-through và bản tổng hợp kích thước cố định mỗi lượt: ba observation, trạng thái nguồn, lần cắm/rút cuối và mốc gap. Vì vậy sự kiện USB cũ hơn cửa sổ 500 dòng của Timeline vẫn có thể được dùng làm quan sát cuối khi lịch sử đã đọc đủ. Thứ tự chọn observation theo sequence history do server cấp; không so độ mới bằng đồng hồ hai máy. Giá trị đọc thành công quá 15 giây chưa xác nhận lại được xem là có thể cũ.

Tác vụ HTTP và tổng hợp chạy ngoài JavaFX Application Thread. Đóng view hủy các request/timer/listener và đóng HTTP client thuộc reader; socket dùng chung vẫn do ứng dụng quản lý.

## Chạy demo hoặc nối server được triển khai

Giám thị chỉ đọc dữ liệu. **Nguồn thu phải chạy trên app desktop của thí sinh**, trong phòng thi được phân công; deploy server lên mạng không tự quan sát USB của máy thí sinh.

Trong môi trường TEST được phép dùng monitoring thử nghiệm:

```powershell
# Terminal server: cấu hình DB/port như hướng dẫn hiện có trước khi chạy.
.\scripts\start-monitoring-server.ps1 -SignalExperiment

# Máy thí sinh: dùng địa chỉ server mà máy này truy cập được.
.\scripts\start-monitoring-client.ps1 -ServerUrl 'http://TEN-MAY-CHU:8080' -ReadUsbStorage

# Máy giám thị: đăng nhập role PROCTOR, không cần nguồn USB riêng.
.\scripts\start-monitoring-client.ps1 -ServerUrl 'http://TEN-MAY-CHU:8080'
```

Đăng nhập candidate, chọn lượt được cấp quyền và mở **Giám sát trong phiên thi → Bắt đầu giám sát**; hoặc vào phòng thi để khởi động tự động. Proctor chọn **Chi tiết** đúng lượt, xem khối **Đang quan sát trên máy thí sinh**. Rút USB, đợi một chu kỳ truy vấn hoàn tất rồi cắm lại; đối chiếu giờ server nhận trong chi tiết và Timeline. Chạy với USB đã cắm từ trước tạo baseline PRESENT_AT_START thay vì sự kiện CONNECTED. Lượt đã nộp/hết giờ không thu tiếp; chọn lượt ACTIVE còn quyền. USB không được Windows/provider nhận dạng không được coi là USB bằng 0.

Hai script chỉ mở app/cấu hình cờ; đăng nhập xong vẫn cần bật giám sát hoặc vào bài như trên. Sau build mới phải đóng và mở lại JVM client cũ; ứng dụng đang chạy không tự nạp class mới. Cờ trên server không thể tự bật nguồn trên máy thí sinh.

Nếu server là JAR được triển khai trực tiếp, phải cấu hình `toeic.monitoring.signal-experiment-enabled=true`; app candidate cần cả signal experiment và `toeic.monitoring.usb-enabled=true`. Script client `-ReadUsbStorage` thiết lập hai cờ này và hiện thông báo quan sát. Cần chạy đúng bản build chứa `windows-observer` và resource CIM. Mặc định repository vẫn OFF/FULL; đây không là phê duyệt policy/ADR/retention, USB H01–H12 hoặc nghiệm thu LAN.

Kiểm thử tự động dùng fixture **MOCK** cho account/đề và thao tác window bằng JavaFX khi được ghi rõ; test CIM thật chỉ chứng minh baseline trên máy chạy, không thay lần rút/cắm vật lý của người dùng. Bằng chứng UI cũ tại `evidence/proctor-signals-20261024/`; sửa luồng giám sát chung và bỏ cột riêng tại `evidence/unified-monitoring-20261025/`.
