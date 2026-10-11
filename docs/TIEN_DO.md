# Tiến độ hiện tại

Cập nhật lần cuối: **11/10/2026 — tích hợp ổn định Vai B** từ PR #17 `41da669` và main `12e2838` trong worktree/nhánh local riêng. Merge conflict dashboard và tài liệu đã xử lý; gỡ password demo, sửa fresh-scope/same-attempt/stop guard. Maven package **565 PASS**, Python10, REAL FX component với gateway/WS state MOCK PASS; PostgreSQL/full GUI/LAN **NOT RUN** do Docker engine chưa hoạt động. [Evidence](../evidence/b-integration/2026-10-11-verification.md), [checklist và blocker A](NGHIEM_THU_VAI_B.md), [review C của agent Vai B](REVIEW_B_MONITORING.md). Chưa push/chưa merge main; human A/B approval và E1/E2 NOT RUN.

C đã hoàn tất T2-C5 tài liệu phương pháp trên main; window/sampler/analyzer E1 và delta/E2 còn thiếu. Kết quả C1–C4 giữ provenance gốc. B1/B2 CODE_COMPLETE; B3/B4 PARTIAL do ba blocker server, chưa làm T2-B5/Listening/delta.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

10/10/2026: thêm [mục lục log và bằng chứng](../evidence/README.md) theo task để đọc trên GitHub và chuẩn bị prompt cho chat web. Kết quả kiểm thử và trạng thái nghiệm thu giữ theo báo cáo của từng lượt chạy.

## Đang ở đâu

- Chặng: **2** (09–16/10) — Bắt đầu triển khai thi và giám sát.
- Gate kế tiếp: **12/10 thi cơ bản** — nộp bài, chấm điểm, lưu đáp án, đổi câu hỏi, timer.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | Chặng 2 CODE_COMPLETE (T2-A1..T2-A5); sẵn sàng Chặng 3 | Tài liệu giao dịch `docs/GIAO_DICH_VA_QUYEN.md`, nghiệm thu AT01–AT10 PASS | Sẵn sàng Chặng 3 (T3-A1: Audio manifest & READY) | `feat/t2-a5-transaction-docs-and-auth-evidence` |
| B | _chưa gán_ | Ổn định PR17 với main C1–C5; B1/B2 CODE_COMPLETE, B3/B4 PARTIAL | Package565/Python10/FX fixture PASS; review source/evidence C; password demo gỡ; guard entry/switch/stop | A xử lý3blocker và review; production smoke/GUI/LAN sau fix; chưa push, chưa merge main | `stabilize/pr17-main-20261011` (đích PR17: `test/javafx-full-ui-testing`) |
| C | _chưa gán_ | T2-C5 tài liệu/kiểm tra hoàn tất; dừng sau C5 | eb6ba59: phương pháp baseline, identity/E1/E2/byte/clock và protocol đối chiếu source; C4 PR#21 merge b576a07 | B review trước đo NOT RUN; cần window/resource/analyzer E1, delta correctness trước E2; chưa chạy benchmark | `docs/t2-c5-monitoring-baseline-methodology` |

## Đang bị chặn

- [09/10] B bị chặn nghiệm thu T2-B3/T2-B4 (đọc kết quả, mở lại lượt đã chốt) — bởi `GET /attempts/{id}/status` và `/exam` trả 403 cho chính thí sinh sở hữu khi lượt đã `SUBMITTED`/`TIMED_OUT` (`JdbcAttemptScopeStore.canAccess` chỉ nhận `state = 'ACTIVE'`) — cần A cho phép đọc lượt đã chốt trong phạm vi quyền, đúng hợp đồng mục 2 bước 4 — hạn trước gate 12/10.
- [09/10] B bị chặn nghiệm thu hết giờ (T2-B3) — bởi job `ExamTimeoutService` không chạy trong bản jar vì server thiếu `@EnableScheduling`; lượt quá hạn vẫn `ACTIVE`, không có `TIMED_OUT` và điểm — cần A bật scheduling và thêm test không gọi `processTimeouts()` bằng tay — hạn trước gate 12/10.
- [09/10] Cần A quyết: deadline hiện chỉ được đặt ở lần autosave đầu tiên, thí sinh chưa chọn câu nào thì không bị tính giờ; response autosave cũng không mang deadline nên client phải đọc thêm status — hạn trước gate 15/10.

- T2-C2 human A/B review: **NOT RUN**, không ngăn code/test/PR/merge đã được người dùng ủy quyền nếu GitHub không có yêu cầu bắt buộc thiếu. [Evidence C2](../evidence/t2-c2/2026-10-10-verification.md). MT01/MT07/MT08 giữ PARTIAL cấp toàn hệ thống do candidate GUI/LAN chưa chạy; các kiểm tra kỹ thuật mới PASS có nhãn REAL/MOCK/SIMULATED. Origin legacy/trước heartbeat ACK đầu UNSPECIFIED; queue RAM/full RAM một JVM, gap mới cần HTTP refresh.
- C1 lỗi annotation Scheduled đã sửa bằng worker riêng và merge PR#18 tại683adfc. Nhánh C1 giữ local/remote. Không bật scheduling toàn app hoặc sửa ExamTimeoutService; issue scheduler bài thi chuyển A xem riêng. [Evidence sửa C1](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md). C2 đã chạy lại hồi quy tự STALE/TTL/capacity/shutdown PASS.

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
| 16/10, khóa 18/10 | C → A, B | Monitoring epoch/base/sequence, định nghĩa metric | C1 OPEN/full/CLOSE/epoch/sequence/state đã merge; C2 bổ sung EventOrigin/deliveryStatus và HTTP gap theo QD-13, server trước client mới; baseSequence/delta/metric E1-E2 chưa có; human A/B review NOT RUN |

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
| C | — | — | T2-C5 tài liệu hoàn tất; B review và E1/E2 NOT RUN. Không điền giờ chưa đo. |

## Bàn giao T2-C3 ngày10/10/2026

C2 đã merge PR#19 bằng merge commit b2926ea, giữ C1/C2 local/remote. C3 tạo từ main này, không tiếp tục nhánh cũ. Trace tùy chọn mặc định tắt, full/event encoder và lõi reducer C1 production, oracle độc lập/fixture viết tay/seed faults có bằng chứng; [cách chạy](MONITORING_TRACE.md), [evidence](../evidence/t2-c3/2026-10-10-verification.md). Human B review/A adapter review NOT RUN, không giả approval; người dùng cho phép merge commit khi không blocker GitHub và giữ C3. Trạng thái Git cuối xem báo cáo/PR. Không suy UNKNOWN từ full mất; CLI không presence/TTL/auth/DB, không MT02–MT06 delta PASS hoặc E1/E2. Dừng sau C3.

## Bàn giao T2-C4 ngày10/10/2026

C4 từ main f6d46b2, code0164297. [Hướng dẫn](CONTROLLED_PROCESS_HARNESS.md), [evidence](../evidence/t2-c4/2026-10-10-verification.md). Ground truth độc lập từ parent, clock mapping cùng JVM, TEST owned identity giữ đến collector stop; production8 tên và network contract không đổi. Java493 PASS, full30 REAL child:27/3/0, nguồn scan REAL/gate SIMULATED, checksum/rejoin/cleanup PASS. C1-Gui/C3-replay/measurement/event riêng PASS; event chạy song song lần đầu FAIL retained, cause chưa chứng minh. Human B review NOT RUN; B cần xem policy/trace/clock/identity/lifecycle. Chưa C5/delta/E1/E2; dừng sau C4. Git merge cuối xem báo cáo/PR, không giả review.

## Bàn giao T2-C5 ngày11/10/2026

[Phương pháp baseline](MONITORING_BASELINE_METHOD.md), [kiểm tra tài liệu](../evidence/t2-c5/2026-10-11-verification.md). Base82557bf có mục lục evidence sau C4 merge PR#21. Tài liệu không đổi collector/harness/reducer/wire; sửa mô tả byte type/sequence cũ, phân biệt hold200ms với parent-observed lifetime và policy TEST/production. Rate có eligibility chung, INCONCLUSIVE riêng, latency chính xác NOT MEASURED; E2 chưa delta. Human B review/GUI/LAN/E1/E2 NOT RUN. Không sửa tracker/kế hoạch/old artifacts; QUYET_DINH giữ nguyên vì không quyết định kiến trúc mới. Git cuối xem PR/final; dừng sau C5.
