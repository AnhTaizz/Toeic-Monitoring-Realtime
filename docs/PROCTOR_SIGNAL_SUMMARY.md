# Chỉ báo đa tín hiệu trên Tổng quan và Chi tiết thí sinh

Theo thiết kế người dùng chốt ngày 24/10/2026, dữ liệu có ba mức hiển thị:

| Màn hình | Nội dung |
| --- | --- |
| Tổng quan | Mỗi lượt thi có USB, focus, cửa sổ và thời điểm client quan sát gần nhất. Bấm một chỉ báo mở đúng Chi tiết thí sinh. |
| Chi tiết thí sinh | Giữ Process đang quan sát; khối Giám sát mở rộng ngay dưới hiển thị từng nguồn, giờ client quan sát, giờ server nhận, chất lượng metadata và lần cắm/rút USB gần nhất. |
| Timeline đa tín hiệu | Lịch sử thay đổi. Nút Xem Timeline của từng nguồn trong chi tiết chọn đúng lượt thi và nguồn, kể cả lúc trang lịch sử đầu tiên chưa tải xong. |

## Ý nghĩa dữ liệu

Các giá trị là **quan sát gần nhất đã được server ghi nhận**, không phải khẳng định trạng thái phần cứng tại thời điểm đang xem. Focus/cửa sổ nói về cửa sổ bài thi; USB chỉ gồm thiết bị lưu trữ được nguồn Windows quan sát, không đọc nội dung hoặc serial.

- Không có observation thì hiện chưa xác định; không suy USB bằng 0 từ `OBSERVER_STATUS READY`, lịch sử rỗng hoặc nguồn bị tắt.
- Mất kết nối, presence không ONLINE, lần đọc lỗi/quá hạn, hoặc lịch sử chưa đọc đủ làm chỉ báo chưa xác định/dữ liệu cũ. Chi tiết có thể giữ giá trị cuối cùng cùng thời điểm để đối chiếu.
- `SIGNAL_GAP` làm các observation trước gap chưa đủ để xác nhận nguồn. READY sau gap không khôi phục số USB cũ; phải có observation mới.
- Metadata `PARTIAL` được ghi rõ, không coi là định danh thiết bị chắc chắn. Màu chỉ giúp giám thị xem xét, không kết luận gian lận.
- Khi chuyển thí sinh, thay collector/run, mất scope hoặc đóng phiên, không hiển thị observation của lượt/run trước cho lượt đang chọn.

## Cách đọc và giới hạn

`CandidateSignalsController` dùng một HTTP client riêng cho Tổng quan/Chi tiết, mượn kết nối WebSocket hiện có để biết trạng thái kết nối. Mỗi chu kỳ xác thực lại scope trước khi đọc endpoint typed history hiện hữu. Không đổi DTO/wire, migration hoặc nguồn thu.

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

Đăng nhập candidate, chọn đúng lượt thi rồi vào phòng thi; proctor xem cùng lượt ở Tổng quan/Chi tiết. Rút USB, đợi một chu kỳ truy vấn hoàn tất rồi cắm lại; đối chiếu giờ server nhận trong chi tiết và Timeline. Chạy với USB đã cắm từ trước có thể tạo baseline PRESENT_AT_START thay vì sự kiện CONNECTED. USB không được Windows/provider nhận dạng không được coi là USB bằng 0.

Nếu server là JAR được triển khai trực tiếp, phải cấu hình `toeic.monitoring.signal-experiment-enabled=true`; app candidate cần cả signal experiment và `toeic.monitoring.usb-enabled=true`. Script client `-ReadUsbStorage` thiết lập hai cờ này và hiện thông báo quan sát. Cần chạy đúng bản build chứa `windows-observer` và resource CIM. Mặc định repository vẫn OFF/FULL; đây không là phê duyệt policy/ADR/retention, USB H01–H12 hoặc nghiệm thu LAN.

Kiểm thử tự động dùng fixture **MOCK** cho thao tác focus/window/USB khi được ghi rõ; test CIM thật chỉ chứng minh baseline trên máy chạy, không thay lần rút/cắm vật lý của người dùng. Bằng chứng lượt này được ghi riêng trong `evidence/proctor-signals-20261024/`.
