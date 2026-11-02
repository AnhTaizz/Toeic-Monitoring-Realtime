# TOEIC Monitor — Ghi chú thuyết trình (bám file Word)

**Trình chiếu:** `presentation/index.html` gồm **11 slide** (10 mục theo file *Slide - TOEIC Monitor.docx* và slide Cảm ơn nguyên bản). Font / biểu đồ / cảm giác thiết kế theo bản Claymoon Flow trên `main`. Đây là phần giải thích cho người thuyết trình, không hiển thị trên màn chiếu.

## 01 — Giới thiệu
Hệ thống TOEIC desktop Client–Server có giám sát thời gian thực. BTL Lập trình mạng, nhóm ba thành viên; ứng dụng có ba vai trò Admin, giám thị, thí sinh. Không mô tả camera hoặc AI nếu code không làm.

## 02 — Bài toán
Thí sinh cần vào phòng, nghe đề, làm và nộp. Server quản lý quyền, deadline, đáp án đã lưu. Giám thị theo dõi trạng thái và tín hiệu bất thường. Nhấn mạnh tổ chức ca thi có giám thị chứ không chỉ luyện thi; không khẳng định tất cả website đều thiếu giám sát.

## 03 — Kiến trúc
JavaFX client hiển thị bài thi, gửi đáp án, thu thập tín hiệu; Spring Boot xử lý nghiệp vụ; PostgreSQL lưu tài khoản, dữ liệu thi, kết quả, lịch sử. HTTP/REST cho đăng nhập, đề/media, lưu/nộp. WebSocket cho trạng thái, sự kiện, điều khiển ca thi. Client không vào DB trực tiếp.

## 04 — Quy trình thi
Admin chuẩn bị đề/tài khoản → giám thị tạo phòng và PIN → thí sinh vào phòng, thử âm thanh, READY → giám thị START, thí sinh làm Listening/Reading → autosave, nộp hoặc hết giờ. Deadline do server quyết định; reconnect không reset giờ.

## 05 — Lưu bài và đồng thời
Chỉ hiển thị “Đã lưu” sau khi giao dịch lưu thành công. Server khóa bản ghi lượt thi khi save/submit/timeout, kiểm writerEpoch/revision để ngăn dữ liệu cũ, kiểm deadline bằng đồng hồ server. Gửi trùng submit trả kết quả đã chốt. Giữ SQL cho hỏi đáp, không để trên slide.

## 06 — Giám sát
Tín hiệu từ process policy, USB lưu trữ, focus/trạng thái cửa sổ, màn hình. Client → WebSocket → server → giao diện giám thị. Phân biệt trạng thái hiện tại với lịch sử sự kiện. Cảnh báo chỉ hỗ trợ giám thị xem xét; không tự kết luận gian lận.

## 07 — Kiểm thử chức năng (28/10)
Server 20 suite: **363 PASS, 4 SKIP**. Client/protocol 9 suite: **144 PASS, 0 SKIP**. E1 analyzer: **30 PASS, 0 SKIP**. Trong các lượt được báo cáo: 0 FAIL / 0 ERROR. Có test giả lập và tích hợp DB/mạng. SKIP không tính là PASS. Không nói dự án không còn lỗi.

## 08 — E1 polling
File Word dùng mốc riêng: **12 lượt / 360 lần tạo process**, hold mục tiêu 200 ms. Chu kỳ 250 / 500 / 1.000 / 2.000 ms phát hiện **27/30, 14/30, 6/30, 4/30**, CPU trung bình **19,01%, 10,44%, 5,57%, 3,03% một lõi**. Quét thường xuyên tăng khả năng bắt tiến trình ngắn nhưng tăng CPU. Đây là thử collector; không suy rộng toàn app. **Chú ý:** các con số này khác bộ E1 trong slide/repo bản cũ; trình bày và dẫn nguồn thống nhất theo Word hoặc xác minh lại log gốc trước khi bảo vệ.

## 09 — Thử nghiệm truyền thông
Mức tải **1, 2, 5, 10, 20 phiên giả lập** trên một máy, ba lượt/mức; tổng **25.227 HTTP requests**, **342 mẫu event**, không có mẫu thất bại. Ở mức 20: p95 vào phòng **1,19 giây**, event **34,46 ms**. DELTA giảm payload so với FULL lần lượt **68,34%, 15,60%, 65,20%** theo workload ít thay đổi, nhiều thay đổi, có reconnect. E2 một lượt/mode/workload. FULL vẫn mặc định, giảm payload không chứng minh giảm CPU; không suy ra sức chứa 20 máy thật.

## 10 — Kết quả và phạm vi
Đạt: ứng dụng tích hợp thi và giám sát; hai máy HTTPS ngày 28/10 vào bài, người dùng báo thử loa nghe rõ; giám thị báo thấy **3 lần cắm và 3 lần rút USB**. USB là xác nhận người thao tác, chưa có log độc lập giám thị. Cần đo thêm ca thi dài/nhiều máy thật, Listening đầy đủ, độ trễ/lưu bền USB và hiệu năng phiên bản mới.

## 11 — Cảm ơn
Giữ **nguyên HTML và bố cục từ bản slide gốc**, kết thúc hỏi đáp. Có thể mở bản đầy đủ `presentation/full.html` và code khi cần trả lời chuyên sâu.
