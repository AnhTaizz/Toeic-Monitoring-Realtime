# Quản lý đề thi: Import JSON và Sửa

Đăng nhập ADMIN → **Quản lý đề thi** dưới **Quản lý tài khoản**. Màn hình chỉ có **Import JSON** và **Sửa**; không có form tạo đề thủ công hoặc nút xóa.

![Danh sách quản lý đề thi](admin-exam-management.png)

## Import từ STUDY4

1. Giữ nguyên `server_exam.json` và thư mục `media` đã crawl trong cùng thư mục đề. Những đường dẫn như `media\audio\...mp3` và `media\images\...jpg` trong JSON phải trỏ tới các tệp có thật.
2. Bấm **Import JSON**, chọn `server_exam.json`. Không chọn `client_exam.json` vì tệp này không có đáp án đúng. Không cần có bản client export cạnh JSON.
3. Ứng dụng chuyển cấu trúc STUDY4 sang đề thi, chuẩn bị media và hiển thị mã đề, tên, số câu, số tệp sẽ gửi. Có thể đổi mã đề, tên/mô tả trước khi import. Khi đổi mã đề mới, ứng dụng tự đổi thư mục media trong gói gửi lên cho khớp mã mới; không thay đổi tệp crawl gốc. Mã đề của một đề đang sửa vẫn giữ nguyên.
4. Bấm **Import đề thi**. Client gửi JSON cùng âm thanh/hình ảnh lên server; bạn không cần tự sao chép media sang máy server. Server kiểm tra toàn bộ đề/media trước khi xác nhận thành công.

![Xem trước thông tin đề trước khi import](admin-exam-import.png)

Chức năng không tải đường dẫn web. Nếu chỉ có URL trong JSON, cần crawl/tải media về đúng các đường dẫn tương đối trước. Không thay nội dung tệp crawl gốc.

Ảnh WebP (kể cả được lưu với đuôi `.jpg`) được tự chuyển thành PNG trong gói gửi lên server. Không cần tự đổi định dạng ảnh trước khi import.

JSON nội bộ v1/v2 vẫn được hỗ trợ. Nếu chọn `rich-import.json`, media phải đã được provision trên server theo cơ chế cũ; chuyển cả bộ crawl bằng `server_exam.json` là luồng tự gửi media.

## Sửa đề có sẵn

- **Đề chưa sử dụng:** sửa tên, mô tả và nội dung JSON hoặc chọn JSON thay thế. Thay bằng `server_exam.json` sẽ gửi cả media mới; mã đề phải trùng đề đang sửa.
- **Đề đã dùng trong ca thi:** sửa tên/mô tả; câu hỏi và đáp án giữ nguyên để bảo toàn bài làm và kết quả. Muốn thay nội dung cho ca thi mới, import một đề có mã khác.
- Chỉnh tên/mô tả và chỉnh JSON được đồng bộ; không có hai bản thông tin cạnh tranh khi lưu. Import lỗi giữ lại form để sửa/thử lại. Không tự gửi lại yêu cầu ghi khi mất mạng.

## Giải thích lỗi và cấu trúc dữ liệu

`Không tìm thấy tài nguyên API` là phản hồi 404 khi server chưa có route quản lý đề, không phải lỗi ràng buộc DB. Phải cập nhật **cả server và client** của nhánh này hoặc chọn đúng địa chỉ server. Client mới hiển thị hướng dẫn cập nhật server và khóa Import khi chưa tải được danh sách.

DB lưu nhiều đề theo `exam_id`; nội dung không bị gắn cứng vào một đề mẫu. Tuy nhiên bộ đọc TOEIC v2 yêu cầu đủ 200 câu, đúng phân bố Part 1–7 (6/25/39/30/30/16/54), nhóm câu, đáp án và media hợp lệ. Có đủ 200 câu trong JSON crawl chưa có nghĩa JSON đó đã là schema import nội bộ. Bộ chuyển STUDY4 xử lý bước này; v1 dành cho đề ngắn vẫn dùng schema v1.

Không cần đổi schema DB, reset DB hoặc xóa lịch sử thi để dùng chức năng mới.

## API và tính nhất quán

Các route dùng Bearer token ADMIN, phản hồi `no-store`:

| Phương thức | Đường dẫn | Công dụng |
| --- | --- | --- |
| GET | `/api/v2/admin/exams?search=...&afterExamId=...` | Danh sách tối đa 20 đề |
| GET | `/api/v2/admin/exams/item?examId=...` | Đọc đề để sửa |
| POST | `/api/v2/admin/exams?version=1` hoặc `version=2` | Import JSON nội bộ |
| PUT | `/api/v2/admin/exams/item?examId=...&version=1` hoặc `version=2` | Thay nội dung đề chưa sử dụng |
| POST/PUT | `/api/v2/admin/exams/bundle` (PUT có `examId`) | Gói JSON + media, `application/zip` |
| PATCH | `/api/v2/admin/exams/item?examId=...` | Sửa `{title, description}`, kể cả đề đã dùng |

Client tự tạo ZIP từ crawl, người dùng chỉ chọn JSON. Server chỉ nhận `rich-import.json` và media được JSON tham chiếu; kiểm đường dẫn, số lượng, dung lượng, hash và định dạng. Giới hạn giải nén: JSON 2 MiB, mỗi audio 64 MiB, mỗi ảnh 8 MiB, tổng 322 MiB, tối đa 200 media. Proxy/tunnel phía trước server cũng cần cho phép dung lượng gói thực tế.

Import media và ghi đề được điều phối dưới khóa theo mã đề; lỗi ghi DB/kiểm media khôi phục lại các tệp cũ và rollback đề. Đề đã dùng bị kiểm tra lại trên server trước khi đổi bất kỳ media nào.

## Kiểm thử

`AdminExamImportTest` kiểm chuyển JSON STUDY4 200 câu với media cạnh tệp, thiếu media, JSON sai và không phụ thuộc client export. `AdminExamGuiTest` kiểm hai thao tác trên giao diện, đồng bộ metadata/JSON, sửa đề đã dùng và thông báo server thiếu API.

`AdminExamPostgresTest` chạy với `TOEIC_PG_INTEGRATION=true` cùng các biến `DB_*`, `TOEIC_SEED_PASSWORD`; dùng schema/media tạm riêng. Kiểm upload, rollback tệp + DB, đường dẫn không hợp lệ, phân quyền và sửa metadata. Khi `TOEIC_STUDY4_SOURCE` trỏ tới `server_exam.json` thực tế, chạy thêm toàn bộ luồng chuyển và upload bộ crawl đó. Các tệp crawl thật không được commit vào Git.
