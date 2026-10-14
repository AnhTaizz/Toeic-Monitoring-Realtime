# Bằng chứng kiểm thử — B-ACCEPT-01 phía A: API lịch sử lượt thi của thí sinh

- **Ngày chạy:** 12/10/2026 (UTC+7)
- **Người chạy:** hai phiên Agent làm vai A. Codex viết phần cài đặt và nhóm test đầu (`e81961e`); phiên Claude chạy kiểm thử, thêm test (`33a2932`), kiểm chéo với client của B và viết tài liệu. Chưa có thành viên nào review; review C→A **NOT RUN**.
- **Nhánh:** `fix/backend-candidate-attempt-history-20261012`, chỉ có ở máy build, **chưa push, chưa merge**.
- **Base:** `main` `ee714fc` (đã gồm T3-C1, T3-C2, T3-C3 của C). Trên base là 5 commit P0 của A (`b7a8d69`, `0401c51`, `fc27776`, `7a83e94`, `30ffcaf`), rồi `e81961e` và `33a2932`.
- **SHA code được kiểm:** `33a2932781b2f5d24eaf390a02e3635c5c4cc43b`
- **Môi trường:** Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, PostgreSQL 18.6 (container `toeic-db`, collation mặc định `en_US.utf8`), Docker 29.4.3

## 1. Lệnh đã chạy

```powershell
# nạp DB_NAME/DB_USER/DB_PASSWORD/DB_PORT từ .env, đặt DB_HOST và TOEIC_SEED_PASSWORD
$env:TOEIC_PG_INTEGRATION = "true"
mvn test
mvn package
```

Thiếu `TOEIC_PG_INTEGRATION=true` thì `ExamRuntimePostgresTest` bị bỏ qua và kết quả không chứng minh gì về PostgreSQL.

## 2. Kết quả

| Phạm vi | Loại | Kết quả | Bằng chứng |
|---|---|---|---|
| `mvn test` trên `e81961e` (bản Codex để lại) | JUnit + PostgreSQL thật | **PASS** 590/590 (protocol 43, client 376, server 170, spike 1) | log không lưu vào repo; số liệu ghi tại đây |
| `mvn package` trên `33a2932` | JUnit + PostgreSQL thật | **PASS** 592/592 (protocol 43, client 376, server 172, spike 1) | `mvn-package-summary.txt` |
| 5 test của endpoint lịch sử và route lạ trong `ExamRuntimePostgresTest` | REAL Spring runtime + HTTP + PostgreSQL, schema riêng tự xóa | **PASS** | các dòng `PASS candidate history…` và `PASS unknown API route…` |
| `ExamControllerTest.unknownAuthenticatedApiRouteReturnsStructured404` | JUnit, không cần PostgreSQL | **PASS** | `mvn-package-summary.txt` |
| Client của B (`770c896`) gọi server nhánh này | REAL HTTP + PostgreSQL, không GUI | **PASS** 13/13 | `b-client-crosscheck.txt`, mã nguồn driver `BClientHistoryCheck.java.txt` |
| GUI production của B (12 ca, mở lại `SUBMITTED`/`TIMED_OUT` bằng JVM mới) | Tay hoặc agent-driven | **NOT RUN** | việc của B, hướng dẫn ở mục 5 |
| Review C→A, LAN, máy thứ hai | Người | **NOT RUN** | — |

Các lượt chạy đều dùng schema tạm (`t2a_p0_*`, `t2a_hist_xchk_*`) và đã xóa đúng schema đó. Schema `public` của DB dev không bị đụng tới.

## 3. Từng yêu cầu của bản giao việc và test tương ứng

| Yêu cầu | Test trong `ExamRuntimePostgresTest` |
|---|---|
| Thí sinh chỉ thấy lượt của chính mình | `candidateAttemptHistoryIsOwnedRoleProtectedAndFieldWhitelisted` (candidate1 và candidate2 không thấy lượt của nhau; tham số `candidateUserId` bị từ chối 400) |
| Có `ACTIVE`, `SUBMITTED`, `TIMED_OUT`; không có `CLOSED`, `INTERRUPTED` | cùng test trên; `TIMED_OUT` trong `candidateAttemptHistoryHidesNonExamRowsAndRejectsRevokedSessions` |
| Phân trang có thứ tự xác định, cursor an toàn | `candidateAttemptHistoryUsesBoundedDeterministicKeysetPagination` (limit 0/101/abc/lặp, cursor rỗng, cursor chứa dấu nháy → 400; cursor của người khác không lộ dòng) và `candidateAttemptHistoryOrdersByAsciiAndHandlesPageBoundaries` (thứ tự byte dưới DB `en_US`, trang vừa khít, cursor quá cuối, cursor không tồn tại) |
| Không trả đáp án đúng, `writerEpoch`, dữ liệu monitoring | so đúng tập 7 khóa của mỗi dòng và 2 khóa của trang; body không chứa `correctOption`, `answers`, `writerEpoch`, `savedRevision`, `candidateUserId`, `score`, `process`, `policy`, `token` |
| Không đổi `auth/me.attemptScope` và quyền monitoring ACTIVE-only | `attemptScope` chỉ có lượt `ACTIVE`; `writer_epoch` không đổi sau khi gọi list; `readsRequireOwnershipOrAssignmentForEveryState` (test P0 có sẵn) vẫn PASS |
| Role và phiên | proctor → 403; không token, token lạ, phiên bị thu hồi → 401 |
| Lượt monitoring cũ không có ca thi | không được liệt kê (dòng chèn trực tiếp vào `monitoring_attempts`) |
| Route không tồn tại không trả 500 | `unknownApiRouteReturnsStructured404InsteadOf500` và test trong `ExamControllerTest`: 404, `error.code = INVALID_INPUT`, `retryable = false` |

## 4. Kiểm chéo với client của B

Lớp `ExamApiClient`, `CandidateAttemptPage`, `CandidateAttemptSummary` được build nguyên trạng từ commit `770c896` của B trong thư mục tạm, rồi gọi server jar của nhánh này. `CandidateAttemptPage` của B tự từ chối trang không tăng chặt theo `String.compareTo`, cursor khác ID cuối, hoặc dòng đã chốt thiếu `submittedAt`, nên việc nó parse được là kiểm tra thật của contract.

- 53 lượt của candidate1: trang 1 có 50 dòng và cursor, trang 2 có 3 dòng và cursor null.
- Một lượt `SUBMITTED` thật (nộp qua API) và một lượt `TIMED_OUT` thật (ca 2 giây, do scheduler của server tự chốt) đều có `submittedAt`.
- candidate2 chỉ thấy lượt của mình, kể cả khi dùng cursor của candidate1; proctor bị `FORBIDDEN`.
- `GET /status` của hai lượt đã chốt trả điểm server chấm (1/2 và 0/2); candidate2 đọc kết quả của candidate1 bị `FORBIDDEN`.

Kết luận: không cần đổi contract B đề xuất. Đây không phải kiểm thử GUI.

## 5. Hướng dẫn để B chạy lại GUI production

1. Lấy backend: nhánh `fix/backend-candidate-attempt-history-20261012` tại `33a2932` (hiện chỉ có ở máy build; cần người dùng push hoặc merge vào nhánh tích hợp của B).
2. `mvn package`, rồi chạy `java -jar server\target\server-0.1.0-SNAPSHOT.jar` với `DB_*` từ `.env`. Lần chạy đầu trên DB dev sẽ để Flyway áp các migration còn thiếu lên schema `public`.
3. Build client từ nhánh tích hợp của B (đang ở `770c896`) trên cùng base; không cần sửa `ExamApiClient`.
4. Giám thị tạo ca mới qua UI. Thí sinh làm và nộp một lượt, để một lượt ngắn tự hết giờ (server nay tự chốt `TIMED_OUT`).
5. Đóng hẳn client, mở JVM mới, đăng nhập lại: tab lịch sử phải liệt kê cả hai lượt đã chốt và mở được kết quả qua `GET /status`.
6. Đăng nhập thí sinh khác: không thấy và không mở được lượt của người kia.

## 6. Giới hạn và điểm cần biết

- Lượt đã chốt nhưng `submitted_at` rỗng **không được liệt kê**. Dữ liệu như vậy chỉ có ở DB đã chạy bản server trước fix P0 (job hết giờ cũ không ghi `submitted_at`). Client của B từ chối cả trang nếu gặp dòng như vậy, nên server ẩn chúng thay vì trả về.
- Thứ tự là `attemptId` tăng dần theo byte, không phải "mới nhất trước".
- Không có snapshot xuyên trang: lượt mới tạo giữa hai lần gọi có thể xuất hiện hoặc không tùy vị trí so với cursor.
- Gọi sai phương thức (ví dụ `POST /api/v1/candidate/attempts`) hiện vẫn trả HTTP 500 vì `HttpRequestMethodNotSupportedException` rơi vào handler chung. Bản giao việc chỉ yêu cầu xử lý route không tồn tại nên phần này chỉ được ghi nhận, chưa sửa.
- Không thêm migration, không thêm index. Truy vấn lọc theo `candidate_user_id` rồi sắp xếp; chưa đo với số lượt lớn.
