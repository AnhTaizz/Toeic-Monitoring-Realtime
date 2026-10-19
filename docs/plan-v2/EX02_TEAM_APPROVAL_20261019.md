# EX-02 — nguồn xác nhận phê duyệt thiết kế

**Status: TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE.** Ngày xác nhận19/10/2026.

Nguồn xác nhận giữ nguyên:

> User-provided confirmation from the project team representative, 2026-10-19.

Người dùng giao nhiệm vụ trong `task.txt` xác nhận đại diện nhóm: A đồng ý phần backend/database; B đồng ý JavaFX/lobby/audio/image rendering; C đồng ý kiểm thử/auth/locking/timeout/monitoring. Nhóm thông qua D01–D09, hướng ADR-V2-03 (PROCTORED_V2, authoritative Listening START, deadline) và D10 **Part1 Visual Readiness Option A**. Xem [decision register](EX02_DECISIONS_ROADMAP.md), [contract](../EX02_RICH_EXAM_CONTRACT_V1.md), [ADR](ADR-V2-03_SESSION_START_DEADLINE.md).

Option A chấp nhận có chủ đích việc candidate được phân công nhận bytes ảnh Part1 trước START. RAM cache/no-store/ẩn UI không ngăn chủ máy trích xuất bytes; phạm vi ngoại lệ chỉ là ảnh được Part1 tham chiếu, không prompt/options/keys/passages/quan hệ các Part khác. D10: A/B phê duyệt thiết kế theo xác nhận này, C review bảo mật/timeout trong phạm vi nhóm đã thông qua.

Đây là **xác nhận được đại diện nhóm cung cấp**, không chữ ký số, không biên bản có chữ ký đính kèm, không tài khoản reviewer hoặc thời điểm duyệt riêng cho từng người. Lần audit PR67 đầu task19/10 trả `reviews=[]`; không tự tạo GitHub Approving Review hoặc tuyên bố GitHub đã chứng nhận phê duyệt. Commit/CI cũng không thay review của người thật.

Phê duyệt thiết kế cho phép dùng hợp đồng này làm đầu vào task triển khai tiếp theo; task hiện tại chỉ cập nhật docs/fixtures/checkers và PR67, **không cấp phạm vi sửa runtime ở lượt này**. Backend HTTP/PG/WS, JavaFX rendering/media, integration và physical acceptance vẫn NOT IMPLEMENTED/NOT RUN cho target V2. Không đánh EX03/05/06/07/08 DONE, không sửa tracker/giờ công hoặc legacy QD21; PR67 dừng trước merge.
