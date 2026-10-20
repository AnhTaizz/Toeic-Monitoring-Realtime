# Báo cáo nghiệm thu Portable Exam Seed trên Windows 11 (Task EX-01)

- **Thời gian thực hiện:** 2026-10-20T18:50:15+07:00
- **Môi trường:**
  - OS: Windows 11 x64
  - JDK: Oracle JDK 21.0.8 (`E:\Java\Jav\jdk-21\bin\java.exe`)
  - PostgreSQL: 18.1 (`E:\PostgreSQL\18\bin`)
  - Backend V2: JAR build từ `main` commit `ad8c370992423d58c730db1320d7fe247beb587f` (SHA-256: `c2e9349140ffbf93640cc599f9c5dd589de8da1823e067aa79edf704de8c6d20`)
  - Thư mục lưu trữ ngoài Git: `E:\toeic-portable-seeds`
  - Run ID: `a1c7bbbd38dd`
  - Đường dẫn seed pack: `E:\toeic-portable-seeds\portable-seed-a1c7bbbd38dd\mock-ex02-200`

---

## 1. Kết quả kiểm thử Unit Test

Đã chạy lại toàn bộ test suite của module `tools/exam-dataset`:
- Lệnh: `mvn -f tools/exam-dataset/pom.xml test`
- Kết quả: **82/82 PASS, 0 Failures, 0 Errors, 0 Skipped** (thời gian: 37.4s).

---

## 2. Kết quả chạy kịch bản nghiệm thu `verify-portable-seed.ps1`

- Lệnh thực thi:
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File tools/exam-dataset/verify-portable-seed.ps1 `
    -PostgresBin 'E:\PostgreSQL\18\bin' `
    -JavaExe 'E:\Java\Jav\jdk-21\bin\java.exe' `
    -ServerJar 'E:\Project\Toeic-Monitoring-Realtime\target\ex01-final-main-20261020\server\target\server-0.1.0-SNAPSHOT.jar' `
    -BackendCommit 'ad8c370992423d58c730db1320d7fe247beb587f' `
    -ExternalRoot 'E:\toeic-portable-seeds'
  ```
- Kết quả đầu ra:
  ```text
  PORTABLE_SEED_CREATE_PASS exam=mock-ex02-200 HTTP201/selectable questions=200
  PORTABLE_SEED_RESTORE_PASS exam=mock-ex02-200 questions=200 media=57
  PORTABLE_EXAM_SEED_PASS run=a1c7bbbd38dd pack=E:\toeic-portable-seeds\portable-seed-a1c7bbbd38dd\mock-ex02-200
  ```
- Mã thoát: `0` (SUCCESS).

---

## 3. Xác minh chi tiết luồng nghiệp vụ

1. **Export từ DB nguồn:**
   - Khởi động cluster PostgreSQL 18.1 TEST nguồn tại cổng 61572, database `toeic_seed_source_test_a1c7bbbd38dd`.
   - Backend V2 khởi động, nạp Flyway V1–V15. Đăng nhập tài khoản `proctor1`.
   - Nạp đề thi mẫu qua `POST /api/v2/exams/import` đạt HTTP 201.
   - Catalog `GET /api/v2/exams` xác nhận `selectable=true` và `blockedReasons=[]`.
   - `PortableExamSeed create` kết xuất dữ liệu đúng 7 bảng đề thi và sao chép 57 file media sang pack ngoài Git.
   - Kiểm tra `seed.sql` chỉ chứa lệnh `INSERT INTO public.<7 bảng>` (không UPDATE, DELETE, DROP hay chạm bảng user/session/monitoring).
2. **Xoá triệt để nguồn MOCK:**
   - Cluster nguồn đã được dừng an toàn (`pg_ctl stop -m fast`).
   - Xoá hoàn toàn thư mục fixture nguồn `source-input/docs/plan-v2/fixtures/ex02`.
   - Xác nhận `Test-Path $fixture` trả về `False` trước khi tiến hành restore.
3. **Restore độc lập vào DB đích mới:**
   - Khởi động cluster PostgreSQL 18.1 TEST đích độc lập tại cổng 55053, database `toeic_seed_target_test_a1c7bbbd38dd`.
   - Chạy bootstrap Backend V2 để áp dụng Flyway V1–V15 và lấy user ID của giám thị.
   - Dịch chuyển sequence của `exam_sources`, `exam_questions`, `exam_options` lên 10000 để chứng minh việc remapping ID.
   - Khởi tạo tiến trình PowerShell mới độc lập, dọn sạch `PATH` (loại bỏ Java, Git, Maven) để chạy `restore-exam-seed.ps1` trực tiếp từ pack.
   - Quá trình restore hoàn tất trong một single-transaction duy nhất (`psql -X --single-transaction -v ON_ERROR_STOP=1`).
4. **Đối soát tính toàn vẹn dữ liệu:**
   - **Số lượng:** 1 exam, 1 source, 76 question groups, 57 media assets (audio/image), 200 câu hỏi, 775 lựa chọn, 286 content blocks, 24 documents.
   - **Khớp dữ liệu:** Dữ liệu JSON trích xuất từ 7 bảng trên DB đích khớp 100% từng trường (`$sourceData -ceq $targetData`).
   - **ID remapping:** Khóa chính của đề thi ở DB đích được cấp mới bắt đầu từ 10000 (`min(id) >= 10000`).
   - **Bảo toàn dữ liệu ngoài đề:** Fingerprint băm MD5 của toàn bộ 19 bảng ngoài đề (user_accounts, flyway_schema_history, monitoring tables, v.v.) trước và sau khi restore giống nhau tuyệt đối.
   - **Kiểm tra Catalog & Media:**
     - Backend V2 chạy trên DB đích với media root mới: `GET /api/v2/exams` trả về `mock-ex02-200` có `selectable=true`, `blockedReasons=[]`.
     - Toàn bộ 57 file media được kiểm tra lại từng file, băm SHA-256 khớp 100% với manifest.

---

## 4. Kiểm tra các trường hợp từ chối (Negative Checks)

Cả 7 ca kiểm thử từ chối đều đã được kích hoạt và kiểm tra bắt lỗi chính xác:
1. `candidate PROCTOR refused before writes`: Tài khoản không phải PROCTOR (ví dụ CANDIDATE) bị từ chối ngay trước khi ghi DB.
2. `duplicate examId refused before writes`: examId đã tồn tại trong DB bị chặn bởi advisory lock và exception trước khi ghi.
3. `wrong confirmation refused`: Nhập sai tên DB xác nhận (`ConfirmDatabase`) bị từ chối.
4. `existing media destination refused`: Thư mục media đích đã tồn tại bị từ chối ghi đè.
5. `corrupt media refused before writes`: File media bị sửa đổi/sai checksum SHA-256 bị từ chối ngay trước khi mở transaction DB.
6. `missing Flyway schema refused`: DB trống chưa chạy migration V14/V15 bị từ chối do lệch schema signature.
7. `non-test database refused`: Tên database không bắt đầu bằng `toeic_*test_*` (như `production`) bị chặn tuyệt đối.

Sau tất cả các negative checks, dữ liệu đề thi đã restore trên DB đích được kiểm tra lại và không bị suy suyển.

---

## 5. Checksum & Artifacts

- **seed.sql SHA-256:** `512e46657e569f1353b94c63b935d082a8316be681b3554442d2daf4e0b40d03`
- **seed-manifest.json SHA-256:** `258ae641fd3d6fc80ade163c0327a2fd55a40a60284a3945212ff819907704e7`
- **Backend JAR SHA-256:** `c2e9349140ffbf93640cc599f9c5dd589de8da1823e067aa79edf704de8c6d20`
- **Receipt file:** `evidence/ex01-portable-seed-20261020/receipt.json`

---

## 6. Trạng thái hiện tại & Vấn đề tồn tại

- **Portable Exam Seed:** Đã nghiệm thu thành công trọn vẹn trên Windows 11 với PostgreSQL 18.1 và Backend V2 từ `main`. Sẵn sàng cho PR review.
- **ETS Test 01:** Vẫn ở trạng thái `BLOCKED` (theo đúng thiết kế fail-closed do thiếu dữ liệu đáp án gốc). Không dùng fixture MOCK để mở gate cho ETS.
