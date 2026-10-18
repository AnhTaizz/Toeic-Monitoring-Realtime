# Quyết định tích hợp đề xuất — 17/10/2026

Trạng thái: PROPOSED_FOR_A_C_APPROVAL. Không có owner approval trong reviews/comments/threads của PR39/40/41/42/43/45 khi preflight. Agent review không thay approval của người phụ trách.

## QD-29

- ACTIVE mới phải có generated attemptId thuộc `[A-Za-z0-9_.:-]{1,128}`. Giữ nguyên username/sessionId đầu vào; không trim/transliterate.
- Validate toàn batch trước mutation; create/reorganization cùng boundary; collision không reopen FINAL.
- Legacy Unicode/space/slash chỉ đọc history/query-status theo auth hiện hành; không suy ra quyền dùng WS monitoring.
- Đề xuất legacy ACTIVE không tương thích: chặn vào/resume thi, cho đọc trạng thái; giám thị tổ chức lại thành attempt mới tương thích. Attempt cũ giữ bất biến theo quy tắc hiện hành. Cần A/C xác nhận cách xử lý trạng thái/dữ liệu cũ trước thực thi; không tự đóng/cancel/migrate dữ liệu.
- Đề xuất thông báo B: “Mã lượt thi không tương thích giám sát. Vui lòng liên hệ giám thị để cấp lượt mới.” Với batch: báo request bị từ chối toàn bộ và identifier không hợp lệ, không lộ dữ liệu ngoài scope.
- Regression cần A/C chấp nhận: ASCII boundary 1/128/129; Unicode/space/slash; batch trộn valid/invalid không mutation; reorganization/collision FINAL; legacy đọc history/status không takeover; legacy ACTIVE bị chặn trước collector; reconnect/event ownership không lẫn attempt.
- Demo: dùng username/sessionId ASCII, kiểm generated length trước buổi demo; không đổi tên account hay sửa raw/DB legacy tự động.

A/C cần trả lời: chấp nhận/sửa fail-closed, legacy handling, thông báo, regression và ảnh hưởng dataset. QD-29 vẫn PENDING; không ký thay owner.

## PR43 / backend history — cần A review

Giữ auth/scope trước query; LIMIT 5001, 5000 trả đủ, 5001 trả lỗi toàn response. Không truncate im lặng. HTTP 500 RETRYABLE_SERVER_ERROR/retryable=true là hành vi hiện tại từ generic error handler, tương tự gap; lỗi quá ngưỡng sẽ không tự hết chỉ nhờ retry. Dashboard giữ stale/error, không giả lịch sử đầy đủ. A quyết định chấp nhận giới hạn MVP hay yêu cầu thay đổi contract riêng; không tự thêm pagination.

## PR45 — cần B review

Không nhập nguyên patch: merge-tree xung đột AudioFiles; source PR45 không giữ catch FileAlreadyExistsException của PR40. Probe filesystem thật, MOCK manifest, 16 reader x 8 vòng: PR45 18 lỗi/128, RC 0/128. Không phải kết quả nghe audio. Policy PR45 tin parent bên ngoài configured root (ví dụ junction của runner), kiểm root trở xuống bằng attributes; policy PR40 kiểm toàn ancestor bằng real path. B phải quyết định boundary và giữ fix race cùng regression root/junction/traversal trước tích hợp. Workflow Windows chỉ được xét riêng sau quyết định; CI PASS của PR45 không chứng minh RC PASS CI.

## takeoverWriter

Không idempotent theo requestId: mỗi request ACTIVE thành công tăng writerEpoch dưới row lock. Mất ACK rồi retry có thể tăng epoch thêm và vô hiệu hóa writer cũ; không nộp/chấm lại và không kéo dài deadline đã có. Không tự mở rộng protocol. A/B cần hiểu đường phục hồi/hiển thị và quyết định có cần task riêng; phiên này không chứng minh một lỗi mất dữ liệu mới.

## Thứ tự thao tác cần owner cho phép

1. PR42 → PR40 sau A/B review F01 và C nhận monitoring evidence.
2. PR43 → PR41 sau C xác nhận terminal/join và A chấp nhận history behavior.
3. PR41 → PR40 sau C xác nhận raw/rejoin/CPU mới và B review phương pháp.
4. PR40 → PR39 sau khi cả F01/C stack đã ở base; không cherry-pick lặp patch.
5. Fetch lại HEAD/checks và so runtime/test/build tree với RC trước đề nghị PR39 → main. Phiên này không merge main.

Mỗi merge thật phải dùng expected HEAD hiện tại, method merge commit theo graph đã review, kiểm ancestry/tree và giữ nhánh nguồn. Nếu HEAD đổi, kết quả/test SHA cũ chỉ là historical evidence.
