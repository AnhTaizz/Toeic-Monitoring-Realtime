# Review backend A đối với PR #17 — 11/10/2026

Review source do Agent Vai A thực hiện trên PR17 `2f5fc66`, đối chiếu main `12e2838` và nhánh `fix/backend-exam-p0-20261011`. Không phải human review/approval trên GitHub. Không sửa nhánh B, không merge/push trong lượt này.

## Ba P0 và hợp đồng sau sửa

| Vấn đề | Nguyên nhân | Sau sửa |
|---|---|---|
| Status/exam sau chốt 403 | Hai GET dùng `requireAttempt`/`canAccess` vốn chỉ ACTIVE | `requireExamRead`/`canReadExam` cho ACTIVE/SUBMITTED/TIMED_OUT, owner hoặc proctor có assignment; không mở quyền ACTIVE của WS/monitoring |
| Không tự TIMED_OUT | Annotation Scheduled có nhưng không EnableScheduling | Configuration exam bật annotation, task dùng scheduler riêng; tắt có chủ đích qua property vẫn được hỗ trợ |
| Bài chưa trả lời không hết giờ | Deadline chỉ đặt trong autosave | Takeover đầu khi entry đặt deadline dưới row lock bằng clock_timestamp()+duration; trả field deadlineAt hiện có |

Deadline của lượt được cấp nhưng chưa vào thi còn null. GET status/exam/proctor không bắt đầu thi. PR17 `ExamEntry.open`: status → nếu final chỉ hiển thị kết quả → tải/kiểm đề → takeover. Vì vậy timer bắt đầu trước khi màn làm bài được mở, kể cả answers rỗng. Legacy save-first giữ tương thích; mọi deadline đã có giữ nguyên. Không thêm endpoint/DTO/migration. TIMED_OUT có submittedAt là giờ chốt bởi server; state phân biệt với nộp chủ động. ACTIVE totalQuestions/các điểm trong status vẫn là số0 như implementation hiện có, tổng câu của UI lấy từ đề.

## QD-16 của B (PR #17) — entry xin writerEpoch mới

**Phù hợp hợp đồng server.** Get status không cấp lease ghi, hai thiết bị đọc cùng epoch không có fencing nếu chỉ tái dùng nó. Takeover dưới khóa tăng writerEpoch; autosave/submit epoch cũ bị STALE. `clientSessionId` hiện chỉ có trong DTO request, chưa là idempotency key/lease trên server. Retry takeover cùng requestId vẫn tăng epoch thêm; deadline không reset. B không nên mặc định takeover là idempotent hay tự dùng epoch từ status để ghi.

PR17 có thể tiếp tục như hiện tại. Khi response takeover thất lạc, đọc status giúp đối chiếu nhưng không chứng minh quyền ghi của thiết bị; gọi takeover lại để nhận epoch mới là phù hợp, có thể thay writer khác. Kết quả final sau restart app đọc được qua status sau P0; auth/me vẫn ACTIVE-only nên màn danh sách không tự liệt kê lịch sử. Truy cập final cần attemptId đã biết, chưa có endpoint lịch sử mới.

## QD-17 của B (PR #17) — mất WebSocket khóa sửa, vẫn cho nộp HTTP

**Phù hợp hợp đồng server hiện tại.** Server không yêu cầu socket/collector đang ONLINE để nhận submit HTTP. Bearer hiện hành, role candidate, owner, ACTIVE, writerEpoch, revision/answers và deadline sau khóa vẫn quyết định. Mất WS không đổi trạng thái xác nhận autosave; submit gửi toàn map đã đóng băng. Retry cùng requestId/payload trả kết quả đã chốt chỉ sau kiểm quyền owner; đọc status sau mất response là đường phục hồi hợp lệ.

Không đổi quyết định B. Đây là chính sách UI, không là quy tắc backend cấm autosave từ client đã sửa bỏ khóa WS. Nếu cần enforcement ONLINE về sau phải phối hợp riêng; Listening không nằm trong thay đổi này. Server-off hoàn toàn làm HTTP thất bại và B phải giữ trạng thái chưa xác nhận, không báo đã nộp.

## Ảnh hưởng C và phối hợp tích hợp

`canAccess`, activeAttempts, auth/me/roster, event/full/gap/heartbeat guards giữ ACTIVE-only. Sau chốt thi, scope monitoring bị thu hồi theo cơ chế C hiện có; việc cho đọc exam/status không cấp quyền gửi WS hay đọc monitoring history final. Không sửa reducer, presence, measurement, collector, migration V1–V8 hoặc client production. Scheduler exam có một worker riêng; worker toeic-presence-timeout và toeic-state-maintenance giữ lifecycle riêng, không được gọi thêm bằng annotation.

Tích hợp kiểm thử ở worktree detached riêng từ PR17; cherry-pick các commit code/test A, không đụng branch B. Smoke cũ của B có assertion deadline null khi entry và chỉ khởi tạo ở save; assertion này phải cập nhật fixture theo hợp đồng mới. Thay đổi test nằm riêng trong worktree tích hợp, không thay code client production. [Evidence mới và lệnh chạy](../evidence/t2-a-p0/2026-10-11-verification.md) ghi kết quả, lỗi exploratory và giới hạn.

Backend P0 PASS không tự chuyển T2-B3/B4 thành DONE: vẫn cần tích hợp nhánh A đã review vào PR17, human review, candidate/proctor GUI thật, server-off/reconnect, LAN và app-image/audio máy thứ hai. E1/E2 vẫn NOT RUN.

### Phân biệt mã quyết định sau main T3-C1 — 12/10/2026

Main `144fe55` có QD-16 của C về delta dựa trên state đã ACK; đó là quyết định khác QD-16 writerEpoch của B trên PR17. Giữ nội dung và mã nguồn của cả hai trong lượt sync A, ghi rõ chủ sở hữu khi tham chiếu. Khi B đồng bộ tài liệu cần phối hợp mã riêng để tránh trùng; không thay hợp đồng writerEpoch hoặc bật delta production vì trùng số tài liệu.

### Phân biệt mã quyết định sau main T3-C3 — 13/10/2026

Main `ee714fc` đã dùng QD-17 (reducer T3-C2) và QD-18 (replay T3-C3) của C. Trong tài liệu này "QD-16 của B" và "QD-17 của B" vẫn là số đang ghi trên nhánh PR #17; khi nhánh client của B vào main chúng mang số QD-22 (`writerEpoch` khi vào thi) và QD-20 (mất WebSocket, vẫn nộp qua HTTP). Quyết định deadline của A là QD-21. Nội dung các quyết định không đổi.
