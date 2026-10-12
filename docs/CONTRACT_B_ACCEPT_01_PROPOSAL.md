# B-ACCEPT-01 — candidate attempt discovery, đề xuất để A review

**Trạng thái: PROPOSED / CHƯA ĐƯỢC A THỐNG NHẤT, backend chưa triển khai.** Client B được chuẩn bị theo bản đề xuất này; không coi đó là API đã chốt hoặc nghiệm thu production. Không đổi `auth/me`, monitoring scope, DTO protocol hiện hành hoặc backend production trong lượt B.

## Root cause từ sáu file đã kiểm

1. `ToeicClientApplication.candidateMonitoringControls()` lấy picker từ login scope rồi thay bằng `/auth/me.attemptScope`. Entry kiểm fresh scope và gọi `startOrReuseMonitoring()` **trước** `ExamEntry.open()`.
2. `SessionController.me()` trả `scopes.activeAttempts(user)`; `JdbcAttemptScopeStore.activeAttempts()` và `canAccess()` đều cố ý lọc `state='ACTIVE'`. Đây là quyền monitoring/ghi, không phải danh sách bài thi của candidate.
3. `ExamEntry.open()` có nhánh final GET status → Finished, không tải đề/takeover. Tuy nhiên caller không khám phá được ID final và từ chối nó trước khi gọi hàm này. Đổi thứ tự hai lời gọi cũng không tạo ra nguồn discovery cho app/máy mới.
4. `ExamApiClient` biết GET status theo ID, không có hàm list. `ExamController` chỉ có GET exam/status theo ID và POST import/sessions/answers/submit/takeover. Chưa có list candidate-owned attempts.
5. `JdbcAttemptScopeStore.canReadExam()` tách riêng ACTIVE/SUBMITTED/TIMED_OUT theo candidate owner hoặc proctor được phân công; `ExamService.getAttemptStatus()` đã dùng exam-read. Kết quả final không mất khỏi DB, cũng không phải lỗi quyền đọc của A. Vấn đề là discovery/caller của B.

Không giữ ID sau submit/restart ở RAM/file/cache để giải quyết discovery; app mới luôn hỏi server bằng phiên xác thực mới. Không biến scope monitoring thành lịch sử.

## Endpoint cần A cung cấp

```http
GET /api/v1/candidate/attempts?limit=50&afterAttemptId=<optional cursor>
Authorization: Bearer <session token>
```

- Candidate-only. A lấy `AuthenticatedUser.userId()` từ principal đã xác thực; role phải CANDIDATE. Không nhận userId/candidateId/username, request body hoặc order/state tùy ý từ client.
- Không mở rộng `SessionController`, `activeAttempts`, `canAccess`, WS subscription, collector OPEN, answers/submit/takeover hoặc các guard ACTIVE-only.
- Truy vấn riêng danh sách **bài thi** của candidate, có session/exam hợp lệ, state trong ACTIVE/SUBMITTED/TIMED_OUT. Loại CLOSED/INTERRUPTED và monitoring-only attempt cũ không có exam/session. Không lấy roster proctor để suy ra owner history.
- `limit`: mặc định50, integer1..100; giá trị sai →400 INVALID_INPUT, không trả unbounded. B dùng50 mỗi trang và tải thêm bằng thao tác người dùng.
- Thứ tự **attemptId ASC, collation ASCII/C**; ID là unique, charset `[A-Za-z0-9_.:-]{1,128}`. Không gọi thứ tự này là “mới nhất”. Cursor keyset `afterAttemptId` dùng cùng collation và cùng điều kiện owner/state; không dùng offset/order SQL từ client.
- Lấy `limit+1` để quyết định còn trang; response tối đa `limit` rows. Nếu còn, `nextAfterAttemptId` là ID cuối trang; hết thì null. Cursor chỉ là khóa vị trí, không cấp quyền đọc attempt tương ứng.
- Không hứa snapshot xuyên các trang. Khi có bài mới hoặc state thay đổi, client Refresh từ đầu và GET status lại trước xem kết quả. Không dùng state trong list để cấp writer/monitoring hoặc làm điểm cuối.
- Cache-Control: no-store; không ghi token/password/body credentials vào logs.

Response tối thiểu:

```json
{
  "attempts": [
    {
      "attemptId": "SESSION-TEST-candidate1",
      "sessionId": "SESSION-TEST",
      "examId": "EXAM-READING-TEST",
      "examTitle": "Reading sample",
      "sessionTitle": "Ca TEST",
      "state": "SUBMITTED",
      "submittedAt": "2026-10-12T08:43:32.535928Z"
    }
  ],
  "nextAfterAttemptId": null
}
```

ACTIVE có submittedAt=null; SUBMITTED/TIMED_OUT dùng `monitoring_attempts.submitted_at`, ISO-8601 Instant không null. List không chứa writerEpoch, answers, correctOption, đề/câu/option, score breakdown, userId/người khác, token, policy/process/history monitoring. Titles theo schema ≤255 ký tự; IDs như trên. Không cần migration để thêm timestamp mới chỉ cho sort.

SQL phác thảo để A chọn implementation (không phải code đã triển khai):

```sql
SELECT a.attempt_id, a.session_id, a.exam_id,
       e.title AS exam_title, s.title AS session_title,
       a.state, a.submitted_at
FROM monitoring_attempts a
JOIN exam_sessions s ON s.session_id = a.session_id
JOIN exams e ON e.exam_id = a.exam_id
WHERE a.candidate_user_id = :authenticated_user_id
  AND a.state IN ('ACTIVE', 'SUBMITTED', 'TIMED_OUT')
  AND (:after IS NULL OR a.attempt_id COLLATE "C" > :after)
ORDER BY a.attempt_id COLLATE "C" ASC
LIMIT :limit_plus_one;
```

Lỗi: không/expired/revoked Bearer401 UNAUTHORIZED; role khác403 FORBIDDEN; limit/cursor/unknown owner query param sai400 INVALID_INPUT; lỗi DB5xx retryable theo contract lỗi hiện có. Body dùng ApiErrorResponse hiện hành. Không trả row một owner khác trong bất kỳ trang nào.

## Luồng B sử dụng

- Tab “Phòng thi đang diễn ra”: vẫn lấy ACTIVE monitoring scope và giữ entry/Reading/save/submit/reconnect hiện có.
- Tab “Lịch sử / kết quả thi”: list endpoint mới từ phiên hiện tại; chỉ hiện SUBMITTED/TIMED_OUT, page cursor không nhập vào RealtimeClient scope. ACTIVE rows từ list không cấp quyền vào thi.
- Mở kết quả gọi duy nhất **GET `/api/v1/attempts/{attemptId}/status`** hiện tại; server lại kiểm exam-read. UI chỉ hiện state/điểm/số câu đúng/thời điểm do response final xác nhận. Không tạo ExamSession, không gọi ExamEntry/takeover/GET exam hoặc collector, không cho sửa/nộp.
- 401 đưa về đăng nhập và dọn tài nguyên; 403 bỏ dữ liệu lịch sử và báo không có quyền; mạng/5xx/body sai báo lỗi và Retry/Refresh, không dùng điểm cũ để báo thành công. Callback sau logout/đổi màn bị bỏ theo generation.
- Client model/HTTP đang nằm trong package client riêng, không tự thêm DTO protocol cho A trước review.

## Evidence A cần bàn giao trước regression production

- [ ] A review/chốt URL, fields, thứ tự/cursor, limit/error/cache semantics trên đề xuất này hoặc cung cấp contract thay thế rõ ràng.
- [ ] Commit backend + DTO đã build được từ cùng integration; không yêu cầu B rộng hóa auth/me/monitoring.
- [ ] PG tests thật: candidate1/candidate2 sở hữu riêng; ACTIVE/SUBMITTED/TIMED_OUT có, CLOSED/INTERRUPTED/legacy không có; pagination limit/order/duplicate/boundary; 401/revoked/role403; owner-param injection không đổi user; response whitelist không có đáp án đúng.
- [ ] GET status final owner PASS, foreign403; monitoring/state/WS/history write vẫn từ chối final, autosave/submit/takeover final vẫn bị từ chối như cũ; không tăng writerEpoch khi GET list/status.
- [ ] Test data mới do proctor tạo qua UI/API, không cần cache máy cũ. Token/password cấp lúc chạy, không để trong commit/log.

Sau bàn giao: B chạy Maven test/package, ExamFlowPostgresSmoke, 12 ca GUI thật và mở lại cả SUBMITTED/TIMED_OUT bằng JVM/máy mới; foreign-result denial và collector không start cho final. **Không đánh dấu các ca này PASS từ fixture client.**

Review result của A: **PENDING**. Không gửi message/duyệt thay A hoặc tự triển khai backend bằng danh nghĩa A.
