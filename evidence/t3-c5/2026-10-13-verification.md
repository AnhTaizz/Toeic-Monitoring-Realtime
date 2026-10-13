# T3-C5 — Kiểm tra bản nháp tài liệu · 13/10/2026

Base `f97c081ed87184ff80e0778e2d4f9a9133f9c6b0`, nhánh `docs/t3-c5-monitoring-sync-report`. Chỉ Markdown; [bản nháp](../../docs/MONITORING_SYNC_REPORT_DRAFT.md). Kết quả Git cuối xem PR/final, không dùng SHA tài liệu thay SHA runtime cũ.

## Kiểm tra mới

Thực hiện bằng đọc source và script kiểm tra tài liệu local trên Windows/PowerShell; các script/output chỉ ở `target/t3-c5/`, không thêm công cụ runtime hoặc dependency.

| Kiểm tra mới | Kết quả / giới hạn |
|---|---|
| Link nội bộ trong cả 10 file Markdown thay đổi | PASS: 257 lượt liên kết file/thư mục/anchor, gồm 49 lượt trong nội dung thêm; 0 lỗi. Không kiểm URL ngoài mạng bằng bộ kiểm này. |
| Method được gọi tên trong nhãn link Java | PASS: 50 lượt tên có trong source, gồm 24 lượt trong nội dung thêm. Đây là kiểm tĩnh, không gọi các method. |
| Mốc cấu hình và nhánh source | PASS: 24 đối chiếu text cho opt-in/capability, refresh2s/full30s, retry5/queue500/in-flight4, STALE6s/TTL5phút/worker500ms, base/sequence/cache64 và quyền ACTIVE khác quyền đọc bài đã chốt. Đã đọc luồng ACK, reducer và callback để đối chiếu ngữ nghĩa. |
| Bảng evidence | PASS: 4 hàng trỏ đúng report/SHA lịch sử; SHA là tổ tiên của base. Đã đọc phân loại môi trường/nguồn/fault, không suy runtime PASS mới. |
| Mermaid | Rà tĩnh các node/cạnh tách state/event/presence; chưa render GUI, không tuyên bố kiểm ảnh. |
| Phạm vi, bí mật và dung lượng | Chỉ 10 Markdown: 1 draft + 1 report C5 + 8 tài liệu liên quan. Rà nội dung thêm và mẫu credential/token/private-key/đường dẫn cá nhân: không thấy secret. Không raw/ZIP/ảnh/generated output mới; artifact/checksum cũ không đổi. |
| Git diff | `git diff --check` PASS; kiểm lại staged diff trước commit. Nhật ký chỉ nối cuối, không đổi tracker/kế hoạch/QD/runtime/.env/migrations. |

Có một lệnh đọc đầu phiên dùng cú pháp brace của shell khác nên PowerShell từ chối; đã chạy lại đúng cú pháp. Không phải lỗi chương trình và không dùng lần đó làm kết quả kiểm thử. Các đếm link/method là lượt tham chiếu, không phải số file hay test case runtime độc lập.

## Phạm vi và việc chưa chạy

Không chạy lại Java/package/Python analyzer/smoke/GUI/LAN/E1/E2. Bảng bằng chứng giữ nguồn [C1](../t3-c1/2026-10-11-verification.md), [C2](../t3-c2/2026-10-12-verification.md), [C3](../t3-c3/2026-10-12-verification.md), [C4](../t3-c4/2026-10-13-verification.md); 531/554/569/579 là số lịch sử ở SHA tương ứng, không phải test mới trên f97c081. Không tái tạo artifact hoặc sửa checksum cũ. Output kiểm tra tạm ở target/t3-c5/ đã được ignore.

B review và ChatGPT planning/review NOT RUN; giữ opt-out kết nối trước đó. Gate NOT_DECIDED/PARTIAL, thiếu LAN hai máy và B review. Merge bản nháp không là approval hoặc bật delta. Không quyết định kiến trúc mới; tracker/kế hoạch/runtime/.env/migrations giữ nguyên. Dừng sau T3-C5.
