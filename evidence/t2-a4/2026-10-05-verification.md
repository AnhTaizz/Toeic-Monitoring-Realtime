# Bằng chứng kiểm thử — Task T2-A4: Phiên ghi và kiểm thử tranh chấp trên PostgreSQL

- **Ngày thực hiện:** 05/10/2026
- **Người thực hiện:** Vai A
- **Nhánh:** `feat/t2-a4-concurrency-and-takeover`
- **Mục tiêu:** Hoàn thành nhiệm vụ T2-A4 (Chặng 2) theo `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md`, `02_HOP_DONG.md` (Mục 2), và `03_KIEM_THU_VA_THUC_NGHIEM.md` (AT01, AT07, AT09, AT10).

---

## 1. Tóm tắt thiết kế & Tuân thủ hợp đồng Mục 2

1. **Takeover Writer Protocol (`vn.edu.toeic.protocol.exam`):**
   - `POST /api/v1/attempts/{attemptId}/takeover`:
     - Yêu cầu thí sinh sở hữu attempt đăng nhập (`CANDIDATE`).
     - Yêu cầu DTO `TakeoverWriterRequest`: `requestId`, `attemptId`.
     - Trả về DTO `TakeoverWriterResponse`: `attemptId`, `writerEpoch` (tăng lên), `savedRevision`, `state`, `deadlineAt`, `answers`.
   - `GET /api/v1/attempts/{attemptId}/status`:
     - Trả về DTO `CandidateAttemptStatusResponse`: `attemptId`, `sessionId`, `examId`, `candidateUsername`, `writerEpoch`, `savedRevision`, `state`, `startedAt`, `deadlineAt`, `answers`, `totalQuestions`, `score`, `submittedAt`.

2. **Cơ chế Khóa Bi quan & Bảo vệ Tranh chấp trên Database (PostgreSQL 18):**
   - Khóa bi quan `SELECT ... FROM monitoring_attempts a WHERE a.attempt_id = :attemptId FOR UPDATE OF a` ngăn chặn hoàn toàn race conditions giữa nhiều writer, background timeout job, autosave và submit.
   - Khi Takeover writer: server mở transaction khóa hàng attempt, kiểm tra thí sinh, kiểm tra trạng thái `ACTIVE` (nếu đã kết thúc thì không cho takeover), tăng `writer_epoch = writer_epoch + 1`, commit transaction và trả về trạng thái hiện tại.
   - Khi một request đang giữ khóa bi quan qua hạn `deadlineAt`: request khác bị chặn chờ khóa khi khóa được nhả ra sẽ đọc `clock_timestamp()` mới nhất và bị từ chối với HTTP 409 `EXPIRED` (AT07).
   - Khi Writer 1 bị lock/chờ mạng, Writer 2 thực hiện takeover và nâng `writerEpoch`: request của Writer 1 khi được xử lý sẽ thấy `request.writerEpoch < attempt.writerEpoch` và bị từ chối với HTTP 409 `STALE` (AT09).
   - Khi quá hạn nộp bài: mọi thao tác sửa đổi hay nộp bài đều bị từ chối ngay lập tức với HTTP 409 `EXPIRED` bằng kiểm tra `clock_timestamp() >= deadline_at` sau khóa, mà không cần phải chờ background timeout job chạy xong (AT10).
   - Cách ly dữ liệu giữa các thí sinh: Thí sinh A không thể xem, autosave, submit, hay takeover trên attempt của Thí sinh B (HTTP 403) cả trước và sau khi submit (AT01).

---

## 2. Kết quả kiểm thử Unit & Integration Tests (`mvn test`)

- **Tổng số tests:** **375/375 PASS** (0 Failures, 0 Errors, 0 Skipped).
  - `vn.edu.toeic:protocol`: 23 tests PASS.
  - `vn.edu.toeic:client`: 227 tests PASS.
  - `vn.edu.toeic:server`: 124 tests PASS (bao gồm các test case takeover, status, concurrency isolation, idempotency, scoring, validation).
  - `vn.edu.toeic:monitoring-spike`: 1 test PASS.

---

## 3. Kết quả Concurrency Smoke Test trên PostgreSQL 18.6 thật (`scripts/smoke-t2a4.ps1`)

Kiểm thử đa luồng và JDBC Connection độc lập đồng thời kết nối trực tiếp đến PostgreSQL 18.6:

```text
16:40:45.962 [main] INFO org.flywaydb.core.FlywayExecutor -- Database: jdbc:postgresql://127.0.0.1:5433/toeic (PostgreSQL 18.6)
16:40:46.076 [main] INFO org.flywaydb.core.internal.schemahistory.JdbcTableSchemaHistory -- Schema history table "t2a4_smoke_8570a0745cbc491a988db0118ca091d2"."flyway_schema_history" does not exist yet
16:40:46.082 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 7 migrations (execution time 00:00.034s)
16:40:46.108 [main] INFO org.flywaydb.core.internal.schemahistory.JdbcTableSchemaHistory -- Creating Schema History table "t2a4_smoke_8570a0745cbc491a988db0118ca091d2"."flyway_schema_history" ...
16:40:46.178 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Current version of schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2": << Empty Schema >>
16:40:46.192 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "1 - auth schema"
16:40:46.253 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "2 - monitoring events"
16:40:46.294 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "3 - monitoring gaps"
16:40:46.329 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "4 - monitoring presence"
16:40:46.373 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "5 - exams and sessions"
16:40:46.440 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "6 - exam autosave requests"
16:40:46.470 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2" to version "7 - exam submits"
16:40:46.506 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Successfully applied 7 migrations to schema "t2a4_smoke_8570a0745cbc491a988db0118ca091d2", now at version v7 (execution time 00:00.148s)
PASS Flyway migrations in temporary schema: t2a4_smoke_8570a0745cbc491a988db0118ca091d2
2026-10-05T16:40:49.951Z  INFO 27916 --- [toeic-server] [1-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-10-05T16:40:49.954Z  INFO 27916 --- [toeic-server] [1-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 2 ms
PASS: Login for proctor1 succeeded
PASS: Login for candidate1 succeeded
PASS: Login for candidate2 succeeded
PASS: Proctor imported exam successfully
PASS: Proctor created session successfully
--- Running AT01: Cross-Candidate Access Isolation ---
PASS: AT01: Candidate2 cannot autosave on Candidate1 attempt (403)
PASS: AT01: Candidate2 cannot submit Candidate1 attempt (403)
PASS: AT01: Candidate2 cannot takeover Candidate1 writer (403)
PASS: AT01: Candidate2 cannot get exam for Candidate1 attempt (403)
PASS: AT01: Candidate2 cannot get status for Candidate1 attempt (403)
PASS: Candidate1 submitted attempt1 successfully
PASS: AT01: After submit, Candidate2 cannot get status for Candidate1 attempt (403)
--- Running AT07: Pessimistic Lock Held Past Deadline ---
DB Connection acquired lock FOR UPDATE on SESSION-T2A4-candidate2
PASS: Lock holder acquired pessimistic lock
DB Connection committed and released lock on SESSION-T2A4-candidate2
PASS: AT07: HTTP request completed after lock release
PASS: AT07: Request blocked waiting for lock past deadline is rejected with 409
PASS: AT07: Response error code is EXPIRED
--- Running AT09: Writer Takeover & Stale Writer Concurrency ---
PASS: AT09 lock holder acquired lock
PASS: AT09: Writer 1 request completed
PASS: AT09: Writer 1 with stale epoch rejected with 409
PASS: AT09: Error code is STALE
PASS: AT09: Takeover writer endpoint returns 200 OK
PASS: AT09: New writerEpoch is 3
PASS: AT09: Writer 2 with writerEpoch=3 saves successfully
PASS: Status endpoint returns 200 OK on reconnect
PASS: Status has writerEpoch = 3
PASS: Status has savedRevision = 1
PASS: Status has state = ACTIVE
PASS: Status answers contains L1=A
--- Running AT10: Tampering After Final & Delayed Timeout Job ---
PASS: AT10: Save after deadline rejected with 409 EXPIRED without waiting for timeout job
PASS: AT10: Error is EXPIRED
PASS: AT10: Submit after deadline rejected with 409 EXPIRED without waiting for timeout job
PASS: AT10: Error is EXPIRED
T2-A4 REAL PostgreSQL 18 Concurrency Verification (AT01, AT07, AT09, AT10) PASS!
Cleaned up temporary schema: t2a4_smoke_8570a0745cbc491a988db0118ca091d2
```

---

## 4. Kết luận

- Task `T2-A4` hoàn thành toàn bộ các yêu cầu của Vai A trong Chặng 2.
- Toàn bộ các tiêu chí kiểm thử AT01, AT07, AT09, AT10 đã được xác minh trên PostgreSQL 18 thật với kịch bản concurrency và pessimistic locking.
