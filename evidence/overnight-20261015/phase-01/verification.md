# Phase 01 — Backend đề xuất cho A/C review

CODE PROPOSED / TESTED LOCAL / HUMAN A-C REVIEW PENDING. Base integration `11c5da9`; SHA checkpoint xem `../00-summary.md`.

- Chặn toàn batch ID ACTIVE ngoài monitoring-v0 hoặc trùng trước mutation, giữ nguyên raw ID history/status. Không trim/normalize. Reorganize dùng cùng kiểm tra; retry cũ vẫn trả replacement đã lưu.
- Bỏ upsert có thể kích hoạt lại bài đã chốt khi collision; insert xung đột trả 400 và transaction rollback. Sai phương thức trả structured 405/Allow; 404 và JSON 400 giữ nguyên.
- API progress mới chỉ PROCTOR được phân công; finalized vẫn đọc được, không nới monitoring scope. MVCC read, không khóa attempt/takeover, không trả answers/writer/correctOption. `lastSavedAt` là max decision_at của autosave đã commit, nullable; `correctCount` chỉ final. Count accepted option keys, total theo exam version.
- JDK Oracle21.0.8/Maven3.9.11/Windows11; PostgreSQL18.6 qua Docker compose service db hiện hữu. Chỉ schema owned của test được tạo/xóa, không reset public/volume.
- Baseline trước Docker: FAIL môi trường — connection refused 127.0.0.1:5433. Log local `target/overnight-tools/baseline-real.log` ở workspace gốc; không dùng test baseline cũ làm PASS. Runner PowerShell ban đầu không thu được native exit/output: NOT RUN, thay bằng cmd/Maven thực.
- Targeted chạy thật: `mvn -o -B -ntp -Dtest=ExamControllerTest,ExamRuntimePostgresTest,ListeningRuntimePostgresTest -Dsurefire.failIfNoSpecifiedTests=false test`: **49 PASS / 0 FAIL / 0 SKIP** (Spring controller7, PostgreSQL exam32/listening10). Tóm tắt log đính kèm; raw local không publish SQL/PID. Boundary test protocol và kiểm tra explicit reorganization thêm sau lượt này, sẽ kiểm ở full gate; không gán 49 cho code thêm chưa chạy.

Cần review: QD29 issuance/legacy; collision và scope finalized; ngữ nghĩa count/lastSavedAt; 405/Allow; index/performance aggregate khi data lớn. Không có schema migration. QD29 **PROPOSED, NOT HUMAN APPROVED**. AT/LT/IT toàn hệ thống chưa được nâng PASS.
