# Giám thị: điều khiển ca và tiến độ

Nhánh overnight thêm hai tab bên cạnh monitoring. Server kiểm quyền từng request; đây không phải hệ thống quản trị đề/người dùng.

1. **Điều khiển ca thi:** chọn file JSON đề để đọc nền và xem trước examId/title/số câu rồi xác nhận import; hoặc dùng nút đề mẫu có sẵn. Nhập mã đề, tiêu đề, usernames phân cách dấu phẩy và sessionId để tạo ca. Sau tạo ca/tổ chức lại, bấm “Làm mới quyền và dữ liệu” của dashboard để nạp scope monitoring; progress đọc trực tiếp API assigned, độc lập roster. Thời lượng mặc định7200s có thể cấu hình `toeic.proctor.sessionDurationSeconds`.
2. Với ca đã được phân công, nhập nguyên sessionId và đọc READY từ server. Start chỉ mở khi tất cả READY, cùng manifest và dữ liệu đọc không quá5s; có xác nhận. Một thao tác retry giữ requestId. Lỗi/timeout đọc status để đối chiếu, không tự phát audio. Không đổi ca khi intent start chưa rõ kết quả.
3. Nhập attemptId, đọc trạng thái Listening. Khi server báo interrupted, nhập mã ca mới/tiêu đề hoặc lý do rồi xác nhận tổ chức lại. Server cấp attempt mới; old answers/deadline/result giữ nguyên. Ca mới phải chuẩn bị READY riêng. Không tổ chức lại nền.
4. **Tiến độ đã lưu:** đọc `GET /api/v1/proctor/attempts/progress`,50 dòng/page, poll3s (`toeic.progress.pollMillis`,500..60000). Có manual refresh/trang đầu/trang sau. Chỉ count/revision đã servercommit; final mới hiện correctCount. Lần lưu cuối nullable, là decisionAt của autosave đãcommit, không bịa thời điểm khi chỉsubmit. Lỗi giữ dữ liệu cũ với nhãn,401 logout. Deadline chỉ tham khảo.

Mọi HTTP/file/hash chạy ngoài FX. Poll và tick có giới hạn, close dừng timer; dashboard sở hữu shared ExamApiClient và đóng toàn bộ lúc logout. Audio vẫn do CandidateExamView/ExamSession sở hữu writer, không đổi.

[Evidence mới](../evidence/overnight-20261015/phase-04/verification.md). Humanreview/LAN/installer/manual hearing chưa nghiệm thu; không đổi tracker hoặc bật delta.
