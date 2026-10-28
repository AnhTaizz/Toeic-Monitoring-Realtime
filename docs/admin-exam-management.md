# Quản lý đề thi cho Admin

Nhánh gốc: `feat/fix-room-ui-history-fullscreen` (`073cbf0`).

Đăng nhập bằng tài khoản ADMIN, chọn **Quản lý đề thi** ngay dưới **Quản lý tài khoản**.

![Giao diện quản lý đề thi với dữ liệu minh họa](admin-exam-management.png)

- Tìm theo mã hoặc tên đề, xem danh sách phân trang 20 đề.
- **Thêm đề thi**: nhập mã, tên, mô tả. Dùng **Biên soạn câu hỏi** để thêm/sửa/xóa câu hỏi v1, nhập Part, đoạn văn, tên tệp nghe, lựa chọn và đáp án; bấm **Lưu câu hỏi**, **Áp dụng**, sau đó **Lưu đề thi**.
- **Nhập tệp JSON** hỗ trợ cả định dạng import v1 và v2 TOEIC 200 câu. Đề v2 giữ nguyên cấu trúc nhóm, nguồn, hình ảnh, âm thanh và đáp án; có thể sửa nội dung JSON trước khi lưu.
- **Sửa** tải toàn bộ nội dung đề; mã đề không đổi. **Xóa** yêu cầu xác nhận.
- Đề đã gắn với bất kỳ ca thi hoặc lượt thi nào chỉ được **Xem**. Muốn sửa nội dung, tạo đề mới với mã khác. Không xóa bài làm hoặc lịch sử kết quả.
- Tệp âm thanh/hình ảnh phải có sẵn trên server theo cơ chế import hiện tại; chức năng này không tải tệp media từ máy admin lên server.

## API

Tất cả endpoint yêu cầu Bearer token có role ADMIN, phản hồi không được cache.

| Phương thức | Đường dẫn | Công dụng |
| --- | --- | --- |
| GET | `/api/v2/admin/exams?search=...&afterExamId=...` | Danh sách, tối đa 20 đề, cursor `nextAfterExamId` |
| GET | `/api/v2/admin/exams/item?examId=...` | Đọc đề có đáp án, chỉ admin |
| POST | `/api/v2/admin/exams?version=1` hoặc `version=2` | Thêm đề, HTTP 201 |
| PUT | `/api/v2/admin/exams/item?examId=...&version=1` hoặc `version=2` | Thay nội dung đề, HTTP 200 |
| DELETE | `/api/v2/admin/exams/item?examId=...` | Xóa đề chưa sử dụng, HTTP 204 |

Body dùng schema import tương ứng, tối đa 2 MiB, từ chối trường lạ/trùng, kiểu sai. Version mặc định là 2. Mã đề trong query phải URL-encode.

Sửa/xóa khóa hàng `exams` bằng `FOR UPDATE`, kiểm tra tham chiếu ca thi/lượt thi trong cùng transaction. Thay nội dung và kiểm tra dữ liệu/media cùng transaction; lỗi phải rollback về đề cũ. Không đổi phân quyền endpoint import của giám thị và không thêm migration.

## Kiểm thử

`AdminExamPostgresTest` chạy HTTP với PostgreSQL thật khi `TOEIC_PG_INTEGRATION=true` và các biến `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `TOEIC_SEED_PASSWORD` được thiết lập. Mỗi test dùng schema riêng và xóa schema sau khi chạy.

`AdminExamGuiTest` dùng JavaFX thật và HTTP stub khi `TOEIC_CANDIDATE_GUI=true`: điều hướng, khóa thao tác trên đề đã dùng, thêm/sửa, giữ form khi server từ chối, xác nhận xóa, hết phiên và biên soạn câu hỏi. Ảnh kiểm tra giao diện được lưu tại `client/target/admin-exams.png`.
