# Tiến độ hiện tại

Cập nhật lần cuối: 09/10/2026 — Vai B trên PR #17 (`test/javafx-full-ui-testing`, code `e362ed9`): luồng thi JavaFX được sửa để chạy đúng hợp đồng. Lỗi chặn của PR gốc: client không parse được response có `Instant` nên màn thi chưa từng mở được với server thật; autosave bỏ mất thay đổi khi đang gửi; hết giờ thì tự nộp. Tests/package 441/441 PASS (65 test mới). REAL Spring/PostgreSQL smoke 28 PASS + 3 GAP phía server; GUI thật agent-driven (UI Automation) PASS các bước đã chạy. T2-B1, T2-B2 CODE_COMPLETE; T2-B3, T2-B4 PARTIAL vì server: lượt đã chốt bị 403 khi đọc status, job hết giờ không chạy, deadline chỉ đặt ở lần lưu đầu. Thành viên tự thao tác GUI, LAN và review A NOT RUN. Evidence `evidence/t2-b/2026-10-09-verification.md`.

Trạng thái trước đó (05/10/2026) — T2-A5 CODE_COMPLETE: Tài liệu giao dịch và bằng chứng quyền; Hoàn thành tài liệu chuyên sâu `docs/GIAO_DICH_VA_QUYEN.md` (4 sơ đồ tuần tự Mermaid, mẫu response, trích xuất log/SQL `FOR UPDATE OF a`, 4 câu hỏi bảo vệ), cập nhật toàn bộ test cases AT01–AT10 trong `docs/KIEM_THU.md` đạt 100% PASS. Hoàn thành toàn bộ Chặng 2 của Vai A (T2-A1 -> T2-A5). Bằng chứng `evidence/t2-a5/2026-10-05-verification.md`.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **2** (09–16/10) — Bắt đầu triển khai thi và giám sát.
- Gate kế tiếp: **12/10 thi cơ bản** — nộp bài, chấm điểm, lưu đáp án, đổi câu hỏi, timer.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | Chặng 2 CODE_COMPLETE (T2-A1..T2-A5); sẵn sàng Chặng 3 | Tài liệu giao dịch `docs/GIAO_DICH_VA_QUYEN.md`, nghiệm thu AT01–AT10 PASS | Sẵn sàng Chặng 3 (T3-A1: Audio manifest & READY) | `feat/t2-a5-transaction-docs-and-auth-evidence` |
| B | _chưa gán_ | T2-B1..B4 trên PR #17; B1, B2 CODE_COMPLETE, B3, B4 PARTIAL (chờ A) | `ExamSession` (autosave, nộp, hết giờ, đối chiếu, takeover), màn thi theo nhóm, 441 tests, smoke REAL và GUI agent-driven | Chờ A xử lý 3 điểm ở mục "Đang bị chặn" rồi chạy lại smoke; thành viên tự kiểm GUI theo checklist trong evidence; A review PR #17; chưa làm T2-B5 | `test/javafx-full-ui-testing` |
| C | _chưa gán_ | T1-C4 code-complete; log đo và survey | Tests/package366 + Python9; REAL transport/log-summary/ProcessHandle scan, MOCK overflow và SIMULATED retry PASS | Human B review, GUI/LAN và E1/E2 NOT RUN; chuẩn bị Chặng 2 | `feat/t1-c4-monitoring-measurement` |

## Đang bị chặn

- [09/10] B bị chặn nghiệm thu T2-B3/T2-B4 (đọc kết quả, mở lại lượt đã chốt) — bởi `GET /attempts/{id}/status` và `/exam` trả 403 cho chính thí sinh sở hữu khi lượt đã `SUBMITTED`/`TIMED_OUT` (`JdbcAttemptScopeStore.canAccess` chỉ nhận `state = 'ACTIVE'`) — cần A cho phép đọc lượt đã chốt trong phạm vi quyền, đúng hợp đồng mục 2 bước 4 — hạn trước gate 12/10.
- [09/10] B bị chặn nghiệm thu hết giờ (T2-B3) — bởi job `ExamTimeoutService` không chạy trong bản jar vì server thiếu `@EnableScheduling`; lượt quá hạn vẫn `ACTIVE`, không có `TIMED_OUT` và điểm — cần A bật scheduling và thêm test không gọi `processTimeouts()` bằng tay — hạn trước gate 12/10.
- [09/10] Cần A quyết: deadline hiện chỉ được đặt ở lần autosave đầu tiên, thí sinh chưa chọn câu nào thì không bị tính giờ; response autosave cũng không mang deadline nên client phải đọc thêm status — hạn trước gate 15/10.
- [09/10] Đánh số quyết định bị trùng giữa các nhánh: `main` dùng QD-11 cho C4, nhánh B4 (PR #9) cũng dùng QD-11 cho lỗi code page; PR #17 dùng QD-12, QD-13. B đổi số trên nhánh B4 khi merge.
- C4 human B review: **NOT RUN**; A/B cần xem RealtimeClient và handler/registry hooks. Đã kiểm BYTE/fragment/retry/concurrent-decorator/failure/cleanup và B3 regression. Log COMPLETE chỉ cho file đã cung cấp; chưa đo CPU/memory/latency/miss-rate hoặc lợi ích delta. ProcessHandle scan REAL riêng, event/overflow MOCK; WMI/ETW chỉ khảo sát. Không tự làm B4/chặng2/E1/E2.

- GUI manual end-to-end: **NOT RUN** — B3 đã mở Stage JavaFX thật bằng harness và thao tác controls proctor, có ảnh UNKNOWN/recovery/stale. Chưa thao tác toàn app login/candidate/process thật → proctor; cần người dùng kiểm candidate/proctor/sai mật khẩu/server tắt. Component proctor PASS không thay toàn MT01/MT08.
- LAN second-machine test: **NOT RUN** — chưa có máy Windows thứ hai thật được cung cấp cho phiên này; không thay bằng localhost.
- Review C→A2 và A→B2: **NOT RUN trong phiên Agent**; không ghi duyệt thay thành viên. Task.txt C2 cho phép merge khi code acceptance/PR mergeability PASS; review B→C2 cũng NOT RUN. PR #2/#3 đã merge, Git C2 cuối xem final report.
- PR #1 đã merge trên main `34a7835`; các mục GUI/LAN thiếu chứng cứ lịch sử vẫn NOT RUN.
- A3 đã thay deny-all A2 bằng assignment scope JDBC; candidate sở hữu/proctor được phân công vào attempt ACTIVE. Không seed assignment production; khi chưa tạo lượt thi, scope vẫn rỗng. Schema tối thiểu chưa phải hệ thống đề/ca thi.
- B2 đã có real integration riêng: production opener, heartbeat/ACK attemptId null, ERROR, server-off/re-auth. Headless model lock PASS; không coi là GUI manual hoặc event/presence đã xong. Evidence mới: `evidence/t1-b2/2026-10-04-real-integration.md`.
- C3 đã nối production C2/B2/A3, fresh scope + explicit candidate start/stop; queue500/in-flight4/retry bounded và MONITORING_GAP persistence/ACKV3. B3 không đổi collector/delivery/heartbeat lease, C3/A4 real regression đã chạy lại PASS. MT01/MT08 còn PARTIAL toàn case: B3 dashboard component đã kiểm, full/delta chặng2 và GUI candidate/LAN chưa nghiệm thu. Evidence B3 `evidence/t1-b3/2026-10-04-verification.md`; human A review NOT RUN.
- DB-off smoke A3: NOT RUN vì PostgreSQL đang dùng chung; deferred constraint trigger TEST trong schema riêng đã kiểm lỗi COMMIT thật, rollback, RETRYABLE_SERVER_ERROR, không ACK/warning. Review C→A3 NOT RUN.
- Bằng chứng A2: `evidence/t1-a2/2026-10-04-verification.md`. TRACKER.json giữ nguyên.

Ghi theo mẫu: `[ngày] Ai bị chặn — bởi cái gì — cần ai làm gì — hạn`.

## Contract đang chờ

| Hạn | Ai cung cấp → ai dùng | Nội dung | Trạng thái |
|---|---|---|---|
| 03/10, giờ đầu | C + mẫu auth của A → A, B | Login, role/attempt scope, WS event/ACK/heartbeat v0 | A3/C3 collector→event/ACK/push/timeline thật; overflow gap riêng; state chưa có |
| 04/10 | B → C | Interface collector → network | MonitoringTransport B2 send=write, ACK/ERROR riêng; C3 dùng event contract A3, state chưa có |
| 04/10 | A → B | Cách gửi credential HTTP/WS, phạm vi subscription | Bearer header; B3 đã consume warning/timeline, HTTP scope refresh và dedupe attempt/event |
| 04/10 | A → B, C | Scoped heartbeat, roster/presence/history | A4 V4; B3 consume roster/history/push presence, revision, stale và HTTP recovery; REAL integration PASS; schema không đổi |
| 09/10 | A → B, C | Import đề, answers/revision, requestId, writerEpoch, deadline/state, mã lỗi | A đã có trên main (T2-A1..A3); B đã consume trên PR #17, REAL smoke PASS; deadline còn lệch (xem mục bị chặn) |
| 12/10 | A → B | Trạng thái đã chốt, takeover writer, state khi reconnect | Takeover và status khi ACTIVE: có, B đã consume; trạng thái đã chốt: status trả 403 và job hết giờ không chạy, đang chờ A |
| 16/10 | A + B → C | Audio manifest, READY/start/interrupted | Chưa có |
| 16/10, khóa 18/10 | C → A, B | Monitoring epoch/base/sequence, định nghĩa metric | Chưa có |

## Câu hỏi mở cần nhóm hoặc thầy trả lời

1. Mỗi người thật sự có bao nhiêu giờ mỗi tuần? (kế hoạch cần khoảng 25; nếu chỉ 15 phải xin thu hẹp phạm vi)
2. Gán tên thật vào A/B/C: ai từng làm JavaFX, Spring, SQL transaction?
3. Ngày gặp thầy trong tuần 05–11/10? Có đủ hai máy Windows không?
4. Thầy có chấp nhận phạm vi TOEIC rút gọn, hướng đóng góp polling/delta và chính sách Listening gián đoạn không?
5. Kênh nộp cuối: USB qua lớp trưởng hay Google Drive?

## Dự báo giờ (cập nhật ngày 08/10)

| Vai | Giờ đã dùng | Giờ còn lại ước tính | Ghi chú |
|---|---:|---:|---|
| A | — | — | |
| B | — | — | |
| C | — | — | |
