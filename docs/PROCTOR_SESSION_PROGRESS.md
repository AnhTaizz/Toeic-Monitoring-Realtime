# Giám thị: điều khiển ca và tiến độ

Bản thống nhất dùng duy nhất B4 của PR #36 tại `client.proctor`, cùng progress được chuyển từ #38. Server kiểm quyền từng request.

1. Tab **Điều khiển ca / kết quả**: chọn JSON đề, xác nhận import; tạo ca với mã đề, tiêu đề và danh sách usernames. Không có demo seeder hoặc workflow điều khiển ca thứ hai. Sau tạo/tổ chức lại, làm mới quyền dashboard để nạp scope monitoring.
2. Đọc READY theo nguyên sessionId; intent start mới cần READY đúng ca còn trong 5 giây và đủ thí sinh. Start có xác nhận. Retry giữ nguyên requestId/manifest; mất response đối chiếu authority Listening, không phát audio từ HTTP response. Không đổi ca khi start chưa rõ kết quả.
3. Đọc attempt/result, kể cả legacy raw ID qua query chỉ đọc; tổ chức lại chỉ mở với Listening interrupted mới. Lượt mới có ID mới và READY riêng; dữ liệu/deadline/result lượt cũ nguyên vẹn.
4. Tab **Tiến độ đã lưu** đọc API scoped `/api/v1/proctor/attempts/progress`, 50 dòng/page, poll 3 giây. Một request đang chờ; cursor/page chỉ tiến khi thành công. Lỗi giữ trang/readAt đã xác nhận, nhãn stale và khóa trang sau; 401 logout. Count/revision từ committed DB; correctCount chỉ final. lastSavedAt nullable là decisionAt của autosave đã commit, không phải giờ COMMIT vật lý. Deadline chỉ tham khảo.

I/O ngoài FX; timer/worker đóng cùng dashboard. Không mở writer từ progress/history/B4. CandidateExamView và ExamSession sở hữu writer chung Reading/Listening. [Bằng chứng bản ghép](../evidence/unified-20261016/verification.md), [hướng dẫn ứng dụng](UNIFIED_APPLICATION.md). Human A/C, LAN, cài/gỡ và nghe thật vẫn chờ nghiệm thu.
