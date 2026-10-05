# Bằng chứng kiểm thử — Task T2-A3: Submit bài thi, timeout và chấm điểm một lần

- **Ngày thực hiện:** 05/10/2026
- **Người thực hiện:** Vai A
- **Nhánh:** `feat/t2-a3-submit-and-scoring`
- **Mục tiêu:** Hoàn thành nhiệm vụ T2-A3 (Chặng 2) theo `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md`, `02_HOP_DONG.md` (Mục 2), và `03_KIEM_THU_VA_THUC_NGHIEM.md` (AT06, AT07, AT08).

---

## 1. Tóm tắt thiết kế & Tuân thủ hợp đồng Mục 2

1. **Protocol DTOs (`vn.edu.toeic.protocol.exam`):**
   - `SubmitExamRequest`: `requestId`, `attemptId`, `writerEpoch`, `answerRevision`, `answers` (Map câu hỏi -> lựa chọn).
   - `SubmitExamResponse`: `requestId`, `attemptId`, `state` ("SUBMITTED"), `totalQuestions`, `correctCount`, `listeningCorrect`, `readingCorrect`, `score`, `submittedAt`, `decisionAt`, `savedRevision`.

2. **Cơ sở dữ liệu (Flyway Migration V7 — `V7__exam_submits.sql`):**
   - Bảng `exam_submit_requests`: lưu trữ idempotency request log gồm `attempt_id`, `request_id`, `writer_epoch`, `answer_revision`, `answers_json`, `total_questions`, `correct_count`, `listening_correct`, `reading_correct`, `score`, `submitted_at`, `decision_at`.
   - Bổ sung cột điểm và phân tích câu đúng trên `monitoring_attempts`: `total_questions`, `correct_count`, `listening_correct`, `reading_correct`.

3. **Thứ tự quyết định phía Server (Pessimistic Locking & Strict Transaction Order):**
   - **Xác thực & Phân quyền:** Kiểm tra `AuthenticatedUser` role `CANDIDATE`. Thí sinh chỉ được submit trên `attemptId` mình sở hữu (401 cho unauthenticated, 403 cho proctor hoặc thí sinh khác).
   - **Khóa bi quan:** `SELECT ... FROM monitoring_attempts a WHERE a.attempt_id = :attemptId FOR UPDATE OF a`.
   - **Kiểm tra Idempotency trước:**
     - Nếu `(attempt_id, request_id)` đã tồn tại trong `exam_submit_requests` với cùng payload: trả về kết quả đã chấm trước đó (idempotent submit retry).
     - Nếu cùng `requestId` khác payload: 409 `CONFLICT`.
   - **Kiểm tra trạng thái đóng (AT06):**
     - Nếu attempt không ở trạng thái `ACTIVE` (ví dụ đã `SUBMITTED`, `TIMED_OUT`): từ chối với 409 `INVALID_STATE`.
     - Autosave sau khi nộp bài cũng bị từ chối với 409 `INVALID_STATE`.
   - **Kiểm tra writerEpoch:** Nếu `request.writerEpoch != attempt.writerEpoch` -> 409 `STALE`.
   - **Kiểm tra Deadline bằng `clock_timestamp()` sau khóa (AT07):** Lấy `clock_timestamp()` làm `decisionAt`. Nếu `decisionAt >= deadlineAt` -> 409 `EXPIRED`.
   - **Kiểm tra Question & Option:** Mọi `questionId` và `optionId` phải thuộc đề thi tương ứng (400 `INVALID_INPUT` nếu vi phạm).
   - **Kiểm tra Revision (AT03 / AT06):**
     - Nếu `answerRevision < attempt.savedRevision`: 409 `STALE` (không tự chốt bằng bản cũ).
     - Nếu `answerRevision == attempt.savedRevision` khác nội dung: 409 `CONFLICT`.
   - **Chấm điểm độc lập phía Server (Scoring Engine):**
     - Đối chiếu câu trả lời với đáp án đúng `correct_option` từ bảng `exam_questions`.
     - Phân loại điểm nghe (`LISTENING`), đọc (`READING`), tổng số câu đúng (`correctCount`), tổng số câu (`totalQuestions`).
     - Cập nhật điểm, đổi trạng thái sang `SUBMITTED`, lưu `exam_submit_requests`, commit transaction trước khi trả response.

4. **Tác vụ Timeout định kỳ (`ExamTimeoutService` - AT08):**
   - Định kỳ quét các attempt quá hạn (`state = 'ACTIVE'` và `deadline_at <= clock_timestamp()`).
   - Mở transaction khóa bi quan `FOR UPDATE OF a`, kiểm tra lại với `clock_timestamp()`.
   - Chấm điểm trên bộ đáp án đã lưu (`saved_answers`), cập nhật trạng thái `TIMED_OUT` và lưu điểm số vào DB.
   - Không nhận đáp án mới từ client; mọi nộp bài sau đó đều bị chặn với 409 `INVALID_STATE`.

---

## 2. Kết quả kiểm thử Unit Tests (`mvn test`)

- **Tổng số tests:** **373/373 PASS** (0 Failures, 0 Errors, 0 Skipped).
  - `vn.edu.toeic:protocol`: 23 tests PASS.
  - `vn.edu.toeic:client`: 227 tests PASS.
  - `vn.edu.toeic:server`: 122 tests PASS (bao gồm `ExamControllerTest`, `ExamValidationTest`, `AuthenticatedNetworkTest`, `SessionAuthenticationTest`, `MonitoringGapTest`, `MonitoringPresenceServiceTest`, `ProcessEventTest`).
  - `vn.edu.toeic:monitoring-spike`: 1 test PASS.

---

## 3. Kết quả Smoke Test trên PostgreSQL 18.6 thật (`scripts/smoke-t2a3.ps1`)

```text
16:27:15.991 [main] INFO org.flywaydb.core.FlywayExecutor -- Database: jdbc:postgresql://127.0.0.1:5433/toeic (PostgreSQL 18.6)
16:27:16.127 [main] INFO org.flywaydb.core.internal.schemahistory.JdbcTableSchemaHistory -- Schema history table "t2a3_smoke_eaba5d082c364337b454a380624f9f75"."flyway_schema_history" does not exist yet
16:27:16.135 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 7 migrations (execution time 00:00.040s)
16:27:16.166 [main] INFO org.flywaydb.core.internal.schemahistory.JdbcTableSchemaHistory -- Creating Schema History table "t2a3_smoke_eaba5d082c364337b454a380624f9f75"."flyway_schema_history" ...
16:27:16.264 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Current version of schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75": << Empty Schema >>
16:27:16.280 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "1 - auth schema"
16:27:16.349 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "2 - monitoring events"
16:27:16.407 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "3 - monitoring gaps"
16:27:16.446 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "4 - monitoring presence"
16:27:16.493 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "5 - exams and sessions"
16:27:16.573 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "6 - exam autosave requests"
16:27:16.607 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75" to version "7 - exam submits"
16:27:16.647 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Successfully applied 7 migrations to schema "t2a3_smoke_eaba5d082c364337b454a380624f9f75", now at version v7 (execution time 00:00.178s)
PASS Flyway V1->V7 migration in temporary schema: t2a3_smoke_eaba5d082c364337b454a380624f9f75
PASS: Login for proctor1 succeeded
PASS: Login for candidate1 succeeded
PASS: Login for candidate2 succeeded
PASS: Proctor imported exam successfully
PASS: Proctor created session successfully
PASS: Candidate 1 saved revision 1
PASS: Candidate 1 submitted exam successfully
PASS: State is SUBMITTED
PASS: totalQuestions is 4
PASS: correctCount is 3
PASS: listeningCorrect is 1
PASS: readingCorrect is 2
PASS: score is 3
PASS: submittedAt present
PASS: AT06: Idempotent submit retry returns 200
PASS: Retry score remains 3
PASS: Retry submittedAt identical
PASS: AT06: Re-submitting closed attempt rejected with 409
PASS: Error code is INVALID_STATE
PASS: AT06: Autosave after submit rejected with 409
PASS: Error code is INVALID_STATE
PASS: AT07: Autosave past deadline rejected with 409 EXPIRED
PASS: Error code is EXPIRED
PASS: AT07: Submit past deadline rejected with 409 EXPIRED
PASS: Error code is EXPIRED
PASS: AT08: Timeout service processed at least 1 expired attempt
PASS: AT08: Attempt state transitioned to TIMED_OUT
PASS: AT08: Timed out attempt score is 2
PASS: AT08: Timed out listeningCorrect is 1
PASS: AT08: Timed out readingCorrect is 1
PASS: AT08: Submit on TIMED_OUT attempt rejected with 409
PASS: Error code is INVALID_STATE
T2-A3 REAL PostgreSQL 18 + HTTP Verification (AT06, AT07, AT08) PASS!
Cleaned up temporary schema: t2a3_smoke_eaba5d082c364337b454a380624f9f75
```

---

## 4. Kết luận

- Task `T2-A3` đã hoàn thành 100% đúng chuẩn đặc tả Hợp đồng Mục 2, đạt toàn bộ tiêu chí nghiệm thu AT06, AT07, AT08 trên PostgreSQL 18.
- Sẵn sàng chuyển tiếp sang task `T2-A4` (Phiên ghi và kiểm thử tranh chấp trên PostgreSQL).
