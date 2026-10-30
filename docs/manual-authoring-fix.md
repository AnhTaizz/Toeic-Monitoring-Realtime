# Soạn đề thủ công: PENDING và âm thanh từ máy

Ảnh lỗi HTTP 404 ở `/api/v2/admin/exams/manual` cho thấy server đang kết nối không cung cấp API mà client đang gọi. API này có trên nhánh soạn đề thủ công; sửa JSON hoặc tạo lại database không làm server cũ có thêm API. Cần triển khai **server.jar và client cùng bản**. Chạy một server mới ở máy khác không thay thế server đang phục vụ địa chỉ đăng nhập hiện tại.

## Cách dùng

- Chọn **Tạo đề bằng tay**, nhập mã/tên đề và soạn các câu theo thứ tự bất kỳ. **Lưu lên server** giữ đề chưa hoàn thiện ở PENDING; đủ 200 câu hợp lệ mới chuyển READY. PENDING không thể dùng tạo ca thi.
- Trước đây trường âm thanh chỉ nhận tên file đã nằm trong thư mục âm thanh của đề trên server, không mở hộp chọn file và không tải file từ máy lên.
- Hiện tại Part 1–4 có **Chọn âm thanh từ máy…** và **Bỏ chọn**. Hỗ trợ MP3, WAV, M4A; tối đa 64 MiB/tệp và 256 MiB/đề. Âm thanh được gửi cùng đề qua `/manual/bundle`; tên file trên server được tạo từ số câu và SHA-256 nên tên có dấu/khoảng trắng trên máy vẫn dùng được. Đây là chức năng âm thanh của form thủ công; import JSON v2 tiếp tục xử lý ảnh và âm thanh qua gói media hiện có.
- **Lưu nháp vào máy…** lưu nội dung và các âm thanh đã chọn thành ZIP. **Mở nháp từ máy…** cho phép soạn tiếp, kể cả khi server chưa hỗ trợ API. Thông báo phân biệt rõ lưu trên máy và lưu lên server. Các file chỉ có sẵn trên server không tự được tải vào ZIP.
- Nếu server báo 404 hoặc mất kết nối, form giữ nguyên nội dung và cho lưu nháp trên máy. Không báo thành công giả khi chưa lưu được lên server.
- Khi tiếp tục một đề đã có trên server, giữ đúng revision. Nếu server đã nhận một lần lưu nhưng kết nối mất trước khi client nhận phản hồi, mở lại đề từ danh sách để lấy revision mới trước khi sửa tiếp.

## Cập nhật server

1. Dừng server cũ bằng quy trình đang dùng. Giữ nguyên PostgreSQL, cấu hình `server.env`, khóa PIN và thư mục media.
2. Triển khai JAR server mới trên chính máy/tiến trình phục vụ URL đăng nhập. Với Docker cần build lại image từ nhánh này.
3. Khởi động server mới; Flyway tự áp dụng migration còn thiếu, bao gồm V22 cho bản nháp. Không sửa/xóa migration đã chạy, không tạo lại DB.
4. Chạy client mới, đăng nhập ADMIN và thử lưu một đề còn trống. Mở lại từ danh sách và kiểm tra trạng thái PENDING.

## Kiểm chứng

Chạy `scripts/verify-manual-authoring.ps1 -PostgresBin '<PostgreSQL>/bin'` trên Windows có JDK 21/Maven và cache phụ thuộc. Runner tạo PostgreSQL riêng trên cổng ngẫu nhiên, dùng dữ liệu MOCK và dừng cluster sau kiểm thử. Không dùng DB ứng dụng.

Kiểm thử bao gồm lưu PENDING rồi READY, chặn PENDING khỏi ca thi, revision conflict, phân quyền, gói âm thanh từ máy, hoàn tác file khi lưu thất bại, ZIP round-trip và chống đường dẫn thoát thư mục, giữ nội dung form khi gặp 404 rồi lưu lại thành công. Các bài cần bộ crawl ngoài được đánh dấu bỏ qua khi thiếu dữ liệu.
