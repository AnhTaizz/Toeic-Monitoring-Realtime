# Sắp xếp thí sinh theo cảnh báo mới nhất

Mở `ToeicMonitor.exe` trong bộ `TOEIC-ALERT-SORT-20261101` cho giám thị.

- Bảng **Thí sinh trong phòng** tự đưa người có cảnh báo mới nhất lên đầu khi nhận cảnh báo realtime hoặc tải lịch sử.
- So sánh thời gian server ghi nhận, dùng cùng tập cảnh báo với cột Cảnh báo và danh sách cảnh báo của phòng: process cần xem xét, USB/focus/cửa sổ, mất kết nối và thiếu dữ liệu. Không sắp theo tổng số cảnh báo hoặc đồng hồ client.
- Người chưa có thời gian cảnh báo nằm phía dưới. Cùng thời điểm hoặc cùng chưa có cảnh báo giữ thứ tự thành viên server trả về để hạn chế nhảy dòng.
- Thí sinh đang được chọn và bảng chi tiết đang mở giữ đúng attempt khi thứ tự thay đổi. Dữ liệu phòng khác không tham gia sắp xếp.

Thay đổi nằm ở client giám thị, không đổi API/schema/server cho phần sắp xếp. Bộ chạy giữ các bản sửa READY/START và nhập đáp án trước đó. Nếu đã cập nhật server theo bộ READY-FIX thì chỉ cần mở client giám thị mới; giữ nguyên DB và media.

Kiểm chứng: 40 PASS/1 SKIP/0 FAIL/ERROR, Maven package PASS. `ProctorRoomDataTest` kiểm thời gian server thay vì client, thứ tự mới/cũ khác loại cảnh báo, tie, không cảnh báo và lọc phòng. `ProctorRoomViewGuiTest` chạy JavaFX thật với API/transport MOCK, kiểm push mới đưa thí sinh lên đầu và giữ lựa chọn/drawer. `RoomFeedTest` và `RoomFeedSignalsTest` kiểm luồng feed. Case chụp ảnh review tùy chọn không bật nên SKIP. Chưa triển khai lên server từ xa.
