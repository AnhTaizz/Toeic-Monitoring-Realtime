# Checklist thực thi — tất cả ô thủ công chưa xác nhận

Mỗi scenario ghi runId, source/MSI/JAR hash, A/B/OS, UTC, operator, expected/actual, raw path và nhãn REAL/SIMULATED/MOCK. Giữ raw bất biến; không ghi token/password hoặc PID thật vào Git.

- [ ] S0: Hai Windows vật lý, phạm vi TEST và bản cài hiện có đã kiểm, MSI hash giống nhau, cổng/listener xác minh trên cả A/B.
- [ ] S1: Proctor import JSON/tạo ca có candidate1,candidate2; hai candidate vào bài/tải/checksum/thử loa thật/READY; start xác nhận; Reading save, progress committed, submit/history điểm server. Chụp UI và DB trước/sau.
- [ ] S2: Mở/đóng process TEST vô hại thuộc runner; event chỉ một lifecycle, proctor không collector; quan sát/receivedAt đúng nghĩa, không suy gian lận.
- [ ] S3: Operator ngắt Wi-Fi/cáp máy B khi Listening phát; ghi lúc thao tác, lúc phát hiện và UNKNOWN. Nối lại, không autoplay/resume, Reading tiếp tục hợp lệ. Không gọi đóng socket seam là physical fault.
- [ ] S4: Mở app khác cùng candidate; epoch mới tăng, writer cũ409STALE; history/result không takeover. Giữ request/response đã lọc và DB authority.
- [ ] S5: Sau interruption, assigned proctor xác nhận reorganization; old answers/deadline/revision/result/history nguyên vẹn; lượt mới riêng/READY lại; foreign proctor403.
- [ ] S6: HTTP chậm/server TEST tạm dừng, malformed request và logout/đóng app; UI phản hồi, không false SAVED/ACK, worker/media/subscription/child dừng. Chỉ restart process TEST đã xác minh.
- [ ] S7: Gỡ đúng ProductCode của bản TEST, không xóa cache/journal của người dùng; kiểm không còn process/resource owned; A/B/C ghi review thật.

Đối chiếu [ma trận gốc](../../../docs/ACCEPTANCE_B_MANUAL_MATRIX.md). Chưa đánh dấu PASS toàn AT/LT/IT/MT từ automation một host.
