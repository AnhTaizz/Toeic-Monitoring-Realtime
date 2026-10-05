# Bằng chứng kiểm thử — Task T2-A1: Import đề thi và Mở ca thi

- **Ngày thực hiện:** 05/10/2026
- **Người thực hiện:** Vai A
- **Nhánh:** `feat/t2-a1-exam-import-session`
- **Mục tiêu:** Hoàn thành nhiệm vụ T2-A1 (Chặng 2) theo `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md`, `02_HOP_DONG.md`, và `03_KIEM_THU_VA_THUC_NGHIEM.md`.

---

## 1. Tóm tắt công việc đã hoàn thành

1. **Protocol DTOs (`vn.edu.toeic.protocol.exam`):**
   - Đảm bảo bất biến cốt lõi (**Core Invariant**): Tách biệt DTO của Giám thị (`ExamImportRequest`, `ExamQuestionImportDto`, `ExamOptionImportDto` chứa `correctOption`) và DTO trả về cho Thí sinh (`CandidateExamDto`, `CandidateQuestionDto`, `CandidateOptionDto` **tuyệt đối không có trường `correctOption` hay đáp án**).
   - Hỗ trợ đầy đủ các phần TOEIC (Listening có audio, Reading có nhóm đoạn văn `passageId`, `passageText`, `sequenceOrder`).
   - DTO quản lý ca thi: `CreateSessionRequest`, `CreateSessionResponse`, `AttemptCreationDto`.

2. **Cơ sở dữ liệu (Flyway Migration V5 — `V5__exams_and_sessions.sql`):**
   - Tạo bảng `exams`, `exam_questions`, `exam_options`.
   - Tạo bảng `exam_sessions`, `exam_session_proctors`.
   - Bổ sung các cột vào `monitoring_attempts`: `session_id`, `exam_id`, `deadline_at`, `writer_epoch`, `saved_revision`, `submitted_at`, `score`, `answers_json`.
   - Mở rộng check constraint `state` của `monitoring_attempts` sang `('ACTIVE', 'SUBMITTED', 'CANCELLED', 'TIMEOUT')`.

3. **Backend Service & API Security (Role Guard — AT02):**
   - `ExamValidationService`: Kiểm tra tính toàn vẹn của đề thi (examId hợp lệ, câu hỏi > 0, questionId duy nhất trong đề, sequenceOrder hợp lệ, `correctOption` bắt buộc phải trùng với một trong các optionId, nhóm đoạn văn nhất quán).
   - `ExamService`: Giao dịch import đề (`@Transactional`), truy vấn đề thi cho thí sinh loại bỏ đáp án, tạo ca thi đồng bộ `monitoring_attempts` và `monitoring_proctor_assignments`.
   - `ExamController`:
     - `POST /api/v1/exams/import`: Yêu cầu Role `PROCTOR`, Thí sinh gọi bị trả về 403 `FORBIDDEN` (AT02), Unauthenticated trả về 401 `UNAUTHORIZED`.
     - `POST /api/v1/sessions`: Yêu cầu Role `PROCTOR`.
     - `GET /api/v1/attempts/{attemptId}/exam`: Thí sinh chỉ xem được đề thi của chính mình, Giám thị được phân công trong ca thi xem được đề thi.

---

## 2. Kết quả kiểm thử Unit & Integration Tests

### 2.1. Toàn bộ Test Suite (`mvn test`)
- **Tổng số tests:** **372/372 PASS** (0 Failures, 0 Errors, 0 Skipped).
  - `vn.edu.toeic:protocol`: 23 tests PASS.
  - `vn.edu.toeic:client`: 227 tests PASS.
  - `vn.edu.toeic:server`: 121 tests PASS (bao gồm `ExamValidationTest`, `ExamControllerTest`, `AuthenticatedNetworkTest`, `SessionAuthenticationTest`, `MonitoringGapTest`, `MonitoringPresenceServiceTest`, `ProcessEventTest`).
  - `vn.edu.toeic:monitoring-spike`: 1 test PASS.

### 2.2. Kiểm tra bảo mật & Chống lộ đề (Reflection Assert)
- Unit test `ExamControllerTest` dùng Reflection kiểm tra toàn bộ class hierarchy của `CandidateExamDto`, `CandidateQuestionDto`, `CandidateOptionDto` bảo đảm:
  - Không có bất kỳ field/getter nào chứa từ khóa `"correct"`, `"answer"`, `"key"`.
  - Endpoint `GET /api/v1/attempts/{attemptId}/exam` khi serialize sang JSON không xuất hiện bất kỳ trường `correctOption` hay `correct_option` nào.

---

## 3. Kết quả Smoke Test trên PostgreSQL 18 thật (`scripts/smoke-t2a1.ps1`)

Harness `ExamPostgresSmoke` chạy trên PostgreSQL 18.6 thật qua port 5433 trong schema tạm thời riêng biệt:

```text
15:53:52.321 [main] INFO org.flywaydb.core.FlywayExecutor -- Database: jdbc:postgresql://127.0.0.1:5433/toeic (PostgreSQL 18.6)
15:53:52.649 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Current version of schema: << Empty Schema >>
15:53:52.673 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "1 - auth schema"
15:53:52.771 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "2 - monitoring events"
15:53:52.843 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "3 - monitoring gaps"
15:53:52.886 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "4 - monitoring presence"
15:53:52.932 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema to version "5 - exams and sessions"
15:53:53.012 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Successfully applied 5 migrations to schema, now at version v5
PASS Flyway V1->V5 migration in temporary schema
PASS: Login for proctor1 succeeded
PASS: Login for candidate1 succeeded
PASS: Candidate calling import gets 403 FORBIDDEN (AT02)
PASS: Unauthenticated calling import gets 401 UNAUTHORIZED (AT02)
PASS: Proctor imports 10-question exam successfully
PASS: Imported total questions is 10
PASS: Duplicate exam import rejected with 400
PASS: Proctor creates exam session successfully
PASS: Attempt created in active state
PASS: Candidate retrieves exam paper successfully
PASS: Candidate exam JSON must NEVER contain 'correctOption'
PASS: Candidate exam JSON must NEVER contain 'correct_option'
PASS: Candidate exam JSON must NEVER contain 'correctAnswer'
PASS: Candidate receives exactly 10 questions
T2-A1 REAL PostgreSQL + HTTP Verification PASS!
Cleaned up temporary schema
```

---

## 4. Kết luận

- Task `T2-A1` đã hoàn thành 100% đúng chuẩn đặc tả, an toàn, bảo mật và tương thích toàn bộ hệ thống.
- Sẵn sàng chuyển tiếp sang task `T2-A2` (Autosave bài thi theo revision và khóa bi quan).
