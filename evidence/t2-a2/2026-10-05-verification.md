# Bằng chứng kiểm thử — Task T2-A2: Autosave toàn bộ đáp án theo revision

- **Ngày thực hiện:** 05/10/2026
- **Người thực hiện:** Vai A
- **Nhánh:** `feat/t2-a2-autosave-answers`
- **Mục tiêu:** Hoàn thành nhiệm vụ T2-A2 (Chặng 2) theo `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md`, `02_HOP_DONG.md`, và `03_KIEM_THU_VA_THUC_NGHIEM.md`.

---

## 1. Tóm tắt thiết kế & Tuân thủ hợp đồng Mục 2

1. **Protocol DTOs (`vn.edu.toeic.protocol.exam`):**
   - `AutosaveAnswersRequest`: `requestId`, `attemptId`, `writerEpoch`, `answerRevision`, `answers` (Map câu hỏi -> lựa chọn).
   - `AutosaveAnswersResponse`: `requestId`, `attemptId`, `status` ("SAVED", "ALREADY_SAVED"), `savedRevision`, `writerEpoch`, `decisionAt`.

2. **Cơ sở dữ liệu (Flyway Migration V6 — `V6__exam_autosave_requests.sql`):**
   - Bảng `exam_autosave_requests`: lưu trữ idempotency request log gồm `attempt_id`, `request_id`, `writer_epoch`, `answer_revision`, `answers_json`, `decision_at`.

3. **Thứ tự quyết định phía Server (Pessimistic Locking & Strict Contract):**
   - **Bước 1 (Xác thực & Phân quyền):** Kiểm tra `AuthenticatedUser` role `CANDIDATE`. Thí sinh chỉ được thao tác trên `attemptId` mình sở hữu (Proctor / Unauthenticated / Foreign candidate bị từ chối 401/403).
   - **Bước 2 (Khóa bi quan):** `SELECT ... FROM monitoring_attempts a WHERE a.attempt_id = :attemptId FOR UPDATE OF a`.
   - **Bước 3 (Kiểm tra writerEpoch sau khi có khóa):** Nếu `request.writerEpoch != attempt.writerEpoch` -> 409 `STALE`.
   - **Bước 4 (Kiểm tra trạng thái bài thi):** Nếu `state != 'ACTIVE'` -> 409 `INVALID_STATE`.
   - **Bước 5 (Kiểm tra deadline bằng `clock_timestamp()`):** Lấy `clock_timestamp()` làm `decisionAt`. Nếu `decisionAt >= deadlineAt` -> 409 `EXPIRED`.
   - **Bước 6 (Kiểm tra tính hợp lệ của câu hỏi & lựa chọn):** Mọi `questionId` phải thuộc `exam_questions` của đề thi; mọi `optionId` phải thuộc `exam_options` của câu hỏi tương ứng. Nếu sai -> 400 `INVALID_INPUT`.
   - **Bước 7 (Chuẩn hóa Canonical Map & Idempotency / RequestId Check - AT05):** 
     - Sắp xếp key bản đồ đáp án qua `TreeMap` để đảm bảo so sánh JSON chuẩn tắc.
     - Nếu `requestId` đã tồn tại với cùng payload -> trả về `ALREADY_SAVED` (idempotent retry).
     - Nếu `requestId` đã tồn tại với khác payload -> 409 `CONFLICT` (AT05).
   - **Bước 8 (Quy tắc Revision - AT03 & AT04):**
     - Nếu `answerRevision < attempt.savedRevision`: 409 `STALE` (AT03 - không ghi đè revision cũ).
     - Nếu `answerRevision == attempt.savedRevision`:
       - Cùng nội dung (kể cả đổi thứ tự key JSON): 200 `ALREADY_SAVED` (AT04 - không gây xung đột giả).
       - Khác nội dung: 409 `CONFLICT` (AT04 - xung đột nội dung).
     - Nếu `answerRevision > attempt.savedRevision`: Cập nhật `monitoring_attempts`, ghi `exam_autosave_requests`, trả 200 `SAVED`.
   - **Bước 9:** Commit transaction thành công trước khi trả response về client.

---

## 2. Kết quả kiểm thử Unit Tests (`mvn test`)

- **Tổng số tests:** **373/373 PASS** (0 Failures, 0 Errors, 0 Skipped).
  - `vn.edu.toeic:protocol`: 23 tests PASS.
  - `vn.edu.toeic:client`: 227 tests PASS.
  - `vn.edu.toeic:server`: 122 tests PASS (bao gồm `ExamControllerTest`, `ExamValidationTest`, `AuthenticatedNetworkTest`, `SessionAuthenticationTest`, `MonitoringGapTest`, `MonitoringPresenceServiceTest`, `ProcessEventTest`).
  - `vn.edu.toeic:monitoring-spike`: 1 test PASS.

---

## 3. Kết quả Smoke Test trên PostgreSQL 18 thật (`scripts/smoke-t2a2.ps1`)

```text
16:15:24.873 [main] INFO org.flywaydb.core.FlywayExecutor -- Database: jdbc:postgresql://127.0.0.1:5433/toeic (PostgreSQL 18.6)
16:15:25.262 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Current version of schema: << Empty Schema >>
16:15:25.289 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "1 - auth schema"
16:15:25.390 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "2 - monitoring events"
16:15:25.468 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "3 - monitoring gaps"
16:15:25.527 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "4 - monitoring presence"
16:15:25.594 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "5 - exams and sessions"
16:15:25.718 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "6 - exam autosave requests"
16:15:25.764 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Successfully applied 6 migrations to schema, now at version v6
PASS Flyway V1->V6 migration in temporary schema
PASS: Login for proctor1 succeeded
PASS: Login for candidate1 succeeded
PASS: Proctor imported exam successfully
PASS: Proctor created session successfully
PASS: Candidate saves revision 1 successfully
PASS: Status is SAVED
PASS: savedRevision is 1
PASS: decisionAt is present
PASS: Candidate saves revision 2 successfully
PASS: savedRevision is 2
PASS: AT03: Stale revision 1 rejected with 409
PASS: Error code is STALE
PASS: AT04: Same revision 2 with different key order returns 200
PASS: Status is ALREADY_SAVED
PASS: AT04: Same revision 2 with different content rejected with 409 CONFLICT
PASS: Error code is CONFLICT
PASS: AT05: Idempotent retry with same requestId returns 200
PASS: Status is ALREADY_SAVED
PASS: AT05: Reusing requestId with different payload rejected with 409 CONFLICT
PASS: Error code is CONFLICT
PASS: Proctor calling candidate autosave gets 403 FORBIDDEN
T2-A2 REAL PostgreSQL + HTTP Verification PASS!
Cleaned up temporary schema
```

---

## 4. Kết luận

- Task `T2-A2` đã hoàn thành 100% đúng chuẩn đặc tả Hợp đồng Mục 2, đạt toàn bộ tiêu chí nghiệm thu AT03, AT04, AT05 trên PostgreSQL 18.
- Sẵn sàng chuyển tiếp sang task `T2-A3` (Submit, timeout và chấm điểm một lần).
