# Backend A — PR #23 đồng bộ main `ee714fc`, review lại ba sửa P0 (13/10/2026)

> **Bản để push là `c64503f`**, không phải `9713658`: trong lúc làm, main tiến thêm PR #27 (T3-C4) lên `cb30f3d`. Xem mục cuối "Đồng bộ lần hai". Các mục bên dưới giữ nguyên như đã ghi cho `9713658`.

Agent-driven. Thành viên vai A và reviewer C chưa xem: **human review NOT RUN**. Chưa push, chưa merge.

## Nguồn

- PR #23 trên GitHub: nhánh `fix/backend-exam-p0-20261011`, head `4753149`, đang báo không merge được (`mergeable_state: dirty`) vì main đã có T3-C2 và T3-C3.
- Nhánh cục bộ `sync/pr23-main-20261013`, commit đã kiểm **`9713658cfe5d05c30dd36f5cf2b0dcba2cde7b10`** = merge `4753149` + `origin/main` `ee714fc`. Đây là fast-forward của nhánh PR #23: push thường là đủ, không cần force.
- Máy: Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, PostgreSQL 18.6 (container `toeic-db`), Python 3.14.

## Conflict khi đồng bộ

Chỉ có conflict tài liệu, không có conflict code.

| File | Cách giải quyết |
|---|---|
| `.gitattributes` | Giữ rule giữ byte của cả hai phía |
| `docs/KIEM_THU.md` | Giữ mục P0/sync của A và mục T3-C2, T3-C3 của C |
| `docs/QUYET_DINH.md` | QD-17, QD-18 của C trên main giữ số. Quyết định "deadline bắt đầu khi nhận quyền ghi" của A đổi số **QD-18 → QD-21**, nội dung không đổi |

Kiểm sau merge: 7 file production mà P0 sửa trong `server/src/main` trùng từng byte với head PR #23 `4753149`; phần còn lại của `server/src/main`, `protocol/src/main`, `client/src/main` trùng main `ee714fc`.

## Review lại ba sửa P0

| Sửa | Commit gốc | Kết luận | Điểm đã soát |
|---|---|---|---|
| Đọc trạng thái/đề sau khi lượt đã chốt | `6c2a5d0` | Đạt | `GET status` và `GET exam` dùng `requireExamRead`: chủ lượt hoặc giám thị được phân công, trạng thái ACTIVE/SUBMITTED/TIMED_OUT. `canReadExam` mặc định trả `false` (fail-closed). `canAccess` của monitoring, WebSocket, save/submit/takeover không đổi nên quyền ghi vẫn chỉ ACTIVE. CLOSED/INTERRUPTED vẫn 403. DTO đề không có đáp án đúng |
| Scheduler hết giờ | `6ec3213` | Đạt | `@EnableScheduling` nằm trong `ExamSchedulingConfiguration`, job gắn vào bean `examTimeoutScheduler` (1 luồng, dừng khi shutdown), tắt được bằng `EXAM_TIMEOUT_SCHEDULER_ENABLED`. Lỗi của một lượt không chặn lượt khác. Worker monitoring của C vẫn tự quản, không chuyển sang `@Scheduled` |
| Deadline bắt đầu khi vào thi | `f175d63` | Đạt | Takeover lấy khóa hàng attempt (`FOR UPDATE`), đọc `clock_timestamp()` một lần sau khóa, rồi `UPDATE ... WHERE deadline_at IS NULL`: vào lại, reconnect, takeover không dời deadline. Sau deadline takeover trả `EXPIRED`. Bốn chỗ so giờ (save, submit, takeover, scheduler) cùng coi đúng thời điểm deadline là hết giờ. TIMED_OUT ghi `submitted_at` = giờ quyết định |

Ghi nhận không chặn merge (chuyển owner A cân nhắc, không tự sửa trong lượt này):

1. Thời lượng mặc định 7200 giây được viết cứng ở hai nơi (`COALESCE` trong truy vấn và `initializeDeadline`). Nó chỉ áp dụng cho lượt không gắn ca thi; ca tạo qua API luôn có thời lượng dương (service từ chối `<= 0`, cột có `CHECK > 0`).
2. `@EnableScheduling` có hiệu lực toàn ứng dụng. Hiện chỉ có một method `@Scheduled`. Method `@Scheduled` thêm sau này mà không ghi `scheduler = ...` sẽ chạy chung luồng `exam-timeout-`.
3. Nhánh tương thích "save đầu tiên khởi tạo deadline" vẫn còn cho client cũ không gọi takeover. Client JavaFX của B luôn takeover khi vào phòng thi.
4. Takeover sau deadline trả `EXPIRED` trong khi trạng thái có thể còn ACTIVE tới một chu kỳ quét (mặc định 1 giây). Client phải đọc lại status; đã kiểm ở lượt nghiệm thu GUI của B, không kiểm lại ở đây.

## Hồi quy đã chạy trên `9713658`

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn package` với `TOEIC_PG_INTEGRATION=true` | **586 PASS**, 0 failure/error/skip (protocol 43, client 376, server 166, spike 1); 5 module đóng gói được | [tóm tắt](mvn-package-summary.txt) |
| `ExamRuntimePostgresTest` (nằm trong 586) | 11 PASS trên PostgreSQL thật, schema riêng `t2a_p0_*` tự xóa | dòng `PASS` trong tóm tắt |
| `scripts/smoke-t2c1.ps1` (C1 full snapshot) | PASS | [log lọc](c1-smoke.txt); ProcessHandle/Edge/HTTP/WS/PG REAL, tập process lỗi MOCK |
| `scripts/smoke-t2c2.ps1` (C2 late event/gap) | PASS | [log lọc](c2-smoke.txt); HTTP/WS/PG REAL, số đọc MOCK, mất kết nối SIMULATED |
| `scripts/test_summarize_monitoring.py` | 10 PASS | [log](python-tests.txt) |
| Schema test còn sót sau khi chạy | 0 | đếm `full_snapshot_test_*`, `late_events_test_*`, `t2a_p0_*`; schema `public` không bị đụng tới |

586 = 548 của lượt đồng bộ 12/10 + 38 test T3-C2/T3-C3 mà main mới thêm (protocol +8, client +23, server +7).

Lượt chạy đầu của bộ Python và bước tóm tắt của smoke C1 **lỗi** vì `python` trên PATH của máy là bản 3.9 (script cần `Path.write_text(newline=...)`, từ 3.10). Phần Java của smoke C1 ở lượt đó vẫn PASS đủ 6 dòng. Chạy lại bằng Python 3.14 qua tham số `-Python` thì qua; kết quả trong bảng là của lượt chạy lại. Không sửa script.

Ba file `.txt` của Maven và smoke là bản lọc lấy dòng kết quả từ log đầy đủ (log đầy đủ không commit).

## Chưa chạy

- `ExamFlowPostgresSmoke` của B: không nằm trong nhánh này (thuộc nhánh client của B); chạy ở nhánh client xếp trên.
- Smoke T3-C1/C2/C3 CLI, T2-C3/C4, A4: không chạy lại; code của chúng trùng main `ee714fc`.
- Human review của A và C, LAN, máy thứ hai.

## Để merge

1. Push thường `sync/pr23-main-20261013` lên `fix/backend-exam-p0-20261011` (fast-forward từ `4753149`).
2. Trên GitHub, PR #23 sẽ hết trạng thái conflict; merge bằng merge commit như các PR trước.
3. Sau đó mới mở PR API lịch sử lượt thi (xếp trên nhánh này) rồi PR client của B.

## Đồng bộ lần hai với main `cb30f3d` (T3-C4)

- Merge `origin/main` `cb30f3d` vào nhánh, commit đã kiểm **`c64503f09c7a64e90ff2feb047d548ff57a99e04`**. Vẫn là fast-forward của nhánh PR #23 (`4753149` là tổ tiên).
- Conflict chỉ ở tài liệu (`KIEM_THU`, `NHAT_KY`, `QUYET_DINH`, `TIEN_DO`, `evidence/README`), giữ cả hai phía. T3-C4 không sửa `server/src/main` hay `protocol/src/main`; so với main `cb30f3d`, code production của nhánh vẫn chỉ khác đúng 7 file P0, và 7 file đó trùng từng byte với head PR #23.
- Main dùng **QD-19** cho quyết định T3-C4 của C. Ghi chú đánh số trong `docs/QUYET_DINH.md` sửa lại: quyết định API lịch sử của A (bản ghép cục bộ trước đó ghi QD-19) mang số **QD-23**. QD-21 (deadline của A), QD-20 và QD-22 (của B) không đổi.
- Review ba sửa P0 ở trên không đổi vì code P0 không đổi.

| Kiểm tra trên `c64503f` | Kết quả | Bằng chứng |
|---|---|---|
| `mvn package` với `TOEIC_PG_INTEGRATION=true` | **596 PASS**, 0 failure/error/skip (protocol 43, client 386, server 166, spike 1) | [tóm tắt](mvn-package-summary-c64503f.txt) |
| `ExamRuntimePostgresTest` (nằm trong 596) | 11 PASS trên PostgreSQL thật | dòng `PASS` trong tóm tắt |
| `scripts/smoke-t2c1.ps1`, `scripts/smoke-t2c2.ps1` | PASS, PASS | [C1](c1-smoke-c64503f.txt), [C2](c2-smoke-c64503f.txt) |
| `scripts/test_summarize_monitoring.py` (Python 3.14) | 10 PASS | [log](python-tests-c64503f.txt) |
| `scripts/smoke-t3c4.ps1` (live gate cục bộ của C) | NOT RUN | code T3-C4 trùng main; không thuộc phần A |

596 = 586 của lượt trước + 10 test client mà T3-C4 thêm. Lệnh push ở mục "Để merge" giữ nguyên, áp dụng cho `c64503f` và commit tài liệu ngay sau nó.
