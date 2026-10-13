# Tiến độ hiện tại

Cập nhật ba nhánh đã push, chưa merge: 13/10/2026 — người dùng cho phép push; PR #23 trên GitHub đã ở `412b7ec`, hai nhánh còn lại là nhánh mới chưa mở PR, nhánh `test/javafx-full-ui-testing` của PR #17 chưa đổi. (1) `sync/pr23-main-20261013` = PR #23 đã đồng bộ main `cb30f3d`, 596 test; (2) `feat/backend-candidate-attempt-history-20261013` = API lịch sử xếp trên (1), 602 test; (3) `feat/javafx-exam-client-20261013` = client của B xếp trên (2), code `3997fef`, 717 test, smoke 28/0 GAP, 5 kịch bản GUI bổ sung PASS. Smoke T3-C4 của C chưa PASS trên máy build (kể cả bản không có client của B). Agent-driven; human review, LAN NOT RUN; chưa merge. [Evidence](../evidence/b-accept-02-20261013/2026-10-13-verification.md). T2-B5 mới có [bản thiết kế](THIET_KE_T2_B5_TIEN_DO.md).

Cập nhật T3-C4:13/10/2026 — candidate Application chọn DELTA opt-in + capability hiện tại, FULL mặc định. Code/test sourced9475eb; Java579 PASS. [Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md). LOCAL preparation riêng với replay; gate20/10/LAN hai máy/human B review chưa chốt, T3-A3 không tự đánh dấu DONE, E1/E2 NOT RUN.

Cập nhật backend A: 13/10/2026 — nhánh cục bộ `sync/pr23-main-20261013` tại `c64503f` đưa ba sửa P0 của PR #23 lên trên main `cb30f3d` (đã gồm T3-C4). Maven package 596 PASS, 0 skip, có PostgreSQL thật; smoke C1/C2 PASS; Python 10 PASS. Review lại ba sửa P0: đạt, có 4 ghi nhận không chặn. Agent-driven; human review NOT RUN; chưa push/merge. [Evidence](../evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md).

Cập nhật sau sửa lỗi P1: 13/10/2026 — nhánh cục bộ `integrate/b-accept-01-public-20261013` tại `b54a8ad` = bản ghép `6816ff5` + một commit sửa làm mới danh sách lượt thi (`AttemptRefresh`). Maven package 707 PASS, 0 skip, có PostgreSQL thật; ExamFlowPostgresSmoke 28 PASS / 0 GAP; JavaFX production: 5/5 kịch bản hồi quy của lỗi và **12/12 ca PASS, chạy lại toàn bộ trên bản sửa**. Không còn lỗi P1 đã biết ở luồng thi. Agent-driven; thành viên tự kiểm, LAN, review A NOT RUN; không push/merge. [Evidence](../evidence/b-accept-01-fix-20261013/2026-10-13-verification.md).

Cập nhật nghiệm thu B-ACCEPT-01: 13/10/2026 — nhánh cục bộ `integrate/b-accept-01-20261013` tại `6816ff5` ghép client B (`6381ddf`) với A-history (`7bdac52`: main `ee714fc`, ba fix P0, API lịch sử). Maven test/package 695 PASS, 0 skip, có PostgreSQL thật; ExamFlowPostgresSmoke 28 PASS / 0 GAP; JavaFX production + server JAR + PostgreSQL thật: **12/12 ca PASS**, lượt `SUBMITTED` và `TIMED_OUT` mở lại được từ tab lịch sử trong JVM mới. Phát hiện 1 lỗi P1 mới ở client (làm mới danh sách khi chưa chọn lượt → NullPointerException), chưa sửa. Agent-driven; thành viên tự kiểm, LAN, review A NOT RUN; không push/merge. [Evidence](../evidence/b-accept-01-20261013/2026-10-13-verification.md).

Cập nhật fix B-ACCEPT-01: client read-only history chuẩn bị ở770c896, Maven test/package651 PASS (31B mới), FX component REAL controls/MOCK gateway PASS. Contract candidate-owned list **PROPOSED/chờ A review**; backend chưa có endpoint (REAL probe500/NoResourceFoundException), GUI production regression chưa chạy. B-ACCEPT-01 vẫn OPEN/P1, T2-B4 PARTIAL; PR17 chưa đủ điều kiện merge. Báo cáo mới (bản gốc 12/10 chưa che thông tin máy, chỉ giữ ở máy build), [contract](CONTRACT_B_ACCEPT_01_PROPOSAL.md). Không đổi backend/protocol/monitoring, không push/merge/B4/T2-B5/Listening/delta.

Cập nhật Vai B 12/10/2026: worktree tích hợp riêng main144fe559 + PR23/4753149 + PR17/2f5fc66, commit **b8ebe67**. Maven test/package620 PASS, B smoke28/0GAP, C1/C2/T3-C1 hồi quy PASS. JavaFX production + server/PG thật: **11/12 ca PASS, ca mở lại FAIL khi bài đã chốt**; ACTIVE restore PASS. P1 B-ACCEPT-01: picker chỉ có scope ACTIVE, không mở lại kết quả SUBMITTED từ app mới. T2-B4 PARTIAL; chưa T2-B5/Listening. Báo cáo/evidence mới (bản gốc 12/10 chưa che thông tin máy, chỉ giữ ở máy build). Không push/merge; B4/files chưa commit giữ nguyên.

Cập nhật mới T3-C1: 11/10/2026 — encoder delta candidate/replay đã triển khai; production vẫn full, server T3-C2/gate chưa có. Human B review/E1/E2 NOT RUN. [Hướng dẫn](MONITORING_DELTA_ENCODER.md), [evidence](../evidence/t3-c1/2026-10-11-verification.md).

Cập nhật B-ACCEPT-01 phía A: 12/10/2026 — `GET /api/v1/candidate/attempts` (lịch sử lượt thi của thí sinh) đã cài theo đúng đề xuất của B, kèm sửa route không tồn tại trả 404 thay cho 500. Nhánh cục bộ `fix/backend-candidate-attempt-history-20261012` tại `33a2932`, base `main` `ee714fc` + 5 commit P0; **chưa push, chưa merge**. `mvn package` 592/592 với PostgreSQL thật; client B `770c896` gọi server này PASS 13/13. GUI production của B và review C→A NOT RUN. [Evidence](../evidence/t2-a-history/2026-10-12-verification.md).

Cập nhật mới T3-C2: 12/10/2026 — reducer production/resync/capability/scan freshness thử nghiệm có; full vẫn default. Java554/Python10 và REAL Spring/PG/WS/restart, CLI/hồi quy PASS. Readings MOCK trong delta smoke; gate/LAN/human A/B/E1/E2 NOT RUN. [Guide](MONITORING_DELTA_REDUCER.md), [evidence](../evidence/t3-c2/2026-10-12-verification.md).

Mốc C5 trước đó: 11/10/2026 — T2-C5 tài liệu phương pháp và đối chiếu protocol hoàn tất từ main82557bf. Không đổi runtime; không chạy lại Java493/smoke/E1/E2. Kiểm tra mới là source/link/schema/default/evidence integrity. B human review trước phép đo NOT RUN; marker cửa sổ E1, resource sampler/analyzer và delta/E2 còn thiếu. Giữ các kết quả C1–C4 theo evidence gốc.

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
| B | _chưa gán_ | Nhánh client sạch chờ review; T2-B4 PARTIAL; T2-B5 mới thiết kế | Code `3997fef`: package 717, smoke 28/0 GAP, 5 kịch bản GUI bổ sung PASS | Chạy lại smoke T3-C4 với C; LAN và máy thứ hai; thành viên tự kiểm; A trả lời câu hỏi API của T2-B5 | `feat/javafx-exam-client-20261013` (đã push, chưa mở PR) |
| C | _chưa gán_ | T3-C4 tích hợp Application/live preparation; gate PARTIAL | Coded9475eb,579Java PASS; kết quả live/hồi quy xem evidence | LAN hai máy, human A/B review và gate20/10; FULL default | `feat/t3-c4-monitoring-delta-live-gate` |

## Đang bị chặn

- [13/10] `scripts/smoke-t3c4.ps1` chưa có lượt PASS nào trên nhánh client của B — lượt đầu do lệch nhãn nút (đã sửa ở `3997fef`), các lượt sau timeout cả trên bản không có client của B; cần C chạy lại trên máy rảnh trước khi merge nhánh client.
- [13/10] T2-B5 chờ A chọn phương án API tiến độ (xem `THIET_KE_T2_B5_TIEN_DO.md` mục 8).
- [13/10] ~~P1 client làm mới danh sách lượt thi khi chưa chọn lượt (`NullPointerException`)~~ — **đã sửa** tại `b54a8ad`, có 12 test và đã chạy lại GUI. [Proof](../evidence/b-accept-01-fix-20261013/2026-10-13-verification.md).
- [13/10] B-ACCEPT-01: các bước kiểm đã đạt trên `6816ff5` (agent-driven); chờ người dùng review và quyết định đóng. Hai dòng B-ACCEPT-01 ngày 12/10 bên dưới là trạng thái trước lần ghép này.
- [13/10] Nhánh để mở PR cho API lịch sử là `feat/backend-candidate-attempt-history-20261013` (`19f669e`, xếp trên PR #23 đã đồng bộ, 602 test PASS); nhánh `…-20261012` bên dưới là bản cũ có lặp commit P0, không dùng để push.
- [12/10] **P1 B-ACCEPT-01:** kết quả đã chốt đọc được qua API owner nhưng không xuất hiện trong picker GUI sau login mới. Cần A/B thống nhất đường exam-read riêng với monitoring ACTIVE; không mở rộng monitoring scope. Proof (bản gốc 12/10 chưa che thông tin máy, chỉ giữ ở máy build). Ba P0 A cũ (final read/scheduler/deadline) đã PASS kỹ thuật và GUI trên tích hợp local; PR23 chưa merge main.
- [12/10] B-ACCEPT-01: backend đã sẵn ở nhánh cục bộ `fix/backend-candidate-attempt-history-20261012` (`33a2932`) nhưng chưa push/merge — cần người dùng quyết định đưa nhánh lên và ghép với nhánh tích hợp của B (`770c896`), rồi B chạy lại GUI production theo mục 5 của evidence — hạn gate 15/10.
- [12/10] Gọi sai phương thức HTTP (ví dụ `POST` vào route chỉ có `GET`) vẫn trả 500 thay vì 405 — A xem có sửa cùng handler lỗi không; không chặn B-ACCEPT-01.
- [09/10] B bị chặn nghiệm thu T2-B3/T2-B4 (đọc kết quả, mở lại lượt đã chốt) — bởi `GET /attempts/{id}/status` và `/exam` trả 403 cho chính thí sinh sở hữu khi lượt đã `SUBMITTED`/`TIMED_OUT` (`JdbcAttemptScopeStore.canAccess` chỉ nhận `state = 'ACTIVE'`) — cần A cho phép đọc lượt đã chốt trong phạm vi quyền, đúng hợp đồng mục 2 bước 4 — hạn trước gate 12/10.
- [09/10] B bị chặn nghiệm thu hết giờ (T2-B3) — bởi job `ExamTimeoutService` không chạy trong bản jar vì server thiếu `@EnableScheduling`; lượt quá hạn vẫn `ACTIVE`, không có `TIMED_OUT` và điểm — cần A bật scheduling và thêm test không gọi `processTimeouts()` bằng tay — hạn trước gate 12/10.
- [09/10] Cần A quyết: deadline hiện chỉ được đặt ở lần autosave đầu tiên, thí sinh chưa chọn câu nào thì không bị tính giờ; response autosave cũng không mang deadline nên client phải đọc thêm status — hạn trước gate 15/10.
- T2-C2 human A/B review: **NOT RUN**, không ngăn code/test/PR/merge đã được người dùng ủy quyền nếu GitHub không có yêu cầu bắt buộc thiếu. [Evidence C2](../evidence/t2-c2/2026-10-10-verification.md). MT01/MT07/MT08 giữ PARTIAL cấp toàn hệ thống do candidate GUI/LAN chưa chạy; các kiểm tra kỹ thuật mới PASS có nhãn REAL/MOCK/SIMULATED. Origin legacy/trước heartbeat ACK đầu UNSPECIFIED; queue RAM/full RAM một JVM, gap mới cần HTTP refresh.
- C1 lỗi annotation Scheduled đã sửa bằng worker riêng và merge PR#18 tại683adfc. Nhánh C1 giữ local/remote. Không bật scheduling toàn app hoặc sửa ExamTimeoutService; issue scheduler bài thi chuyển A xem riêng. [Evidence sửa C1](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md). C2 đã chạy lại hồi quy tự STALE/TTL/capacity/shutdown PASS.

- C4 human B review: **NOT RUN**; A/B cần xem RealtimeClient và handler/registry hooks. Đã kiểm BYTE/fragment/retry/concurrent-decorator/failure/cleanup và B3 regression. Log COMPLETE chỉ cho file đã cung cấp; chưa đo CPU/memory/latency/miss-rate hoặc lợi ích delta. ProcessHandle scan REAL riêng, event/overflow MOCK; WMI/ETW chỉ khảo sát. Không tự làm B4/chặng2/E1/E2.

- GUI production agent-driven đã chạy12/10: login/Reading/save/submit/timeout/process→proctor/server-off/WS/takeover; 11PASS/1FAIL ở final reopening (ACTIVE restore PASS). Thành viên tự thao tác và LAN vẫn NOT RUN; không nâng MT01/MT08/E1/E2 toàn hệ thống.
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
| 16/10, khóa 18/10 | C → A, B | Monitoring epoch/base/sequence, định nghĩa metric | C1 OPEN/full/CLOSE/epoch/sequence/state đã merge; C2 bổ sung EventOrigin/deliveryStatus và HTTP gap theo QD-13, server trước client mới; T3-C2 encoder/reducer/base/resync/freshness có qua capability/flag thử nghiệm; gate và metric E1-E2 chưa có; human A/B review NOT RUN |

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
| B | _chưa gán_ | Nghiệm thu T2 GUI thực; B4 PARTIAL | Tích hợp b8ebe67; test/package620, smoke28/0GAP; GUI11PASS/1FAIL | P1 mở lại kết quả đã chốt, human A review và LAN; không push/merge | `integrate/t2b-acceptance-20261012` (local riêng) |
| C | — | — | T3-C1 candidate encoder/test/replay có; B review/live delta/gate và E1/E2 NOT RUN. Không điền giờ chưa đo. |

## Bàn giao T2-C3 ngày10/10/2026

C2 đã merge PR#19 bằng merge commit b2926ea, giữ C1/C2 local/remote. C3 tạo từ main này, không tiếp tục nhánh cũ. Trace tùy chọn mặc định tắt, full/event encoder và lõi reducer C1 production, oracle độc lập/fixture viết tay/seed faults có bằng chứng; [cách chạy](MONITORING_TRACE.md), [evidence](../evidence/t2-c3/2026-10-10-verification.md). Human B review/A adapter review NOT RUN, không giả approval; người dùng cho phép merge commit khi không blocker GitHub và giữ C3. Trạng thái Git cuối xem báo cáo/PR. Không suy UNKNOWN từ full mất; CLI không presence/TTL/auth/DB, không MT02–MT06 delta PASS hoặc E1/E2. Dừng sau C3.

## Bàn giao T2-C4 ngày10/10/2026

C4 từ main f6d46b2, code0164297. [Hướng dẫn](CONTROLLED_PROCESS_HARNESS.md), [evidence](../evidence/t2-c4/2026-10-10-verification.md). Ground truth độc lập từ parent, clock mapping cùng JVM, TEST owned identity giữ đến collector stop; production8 tên và network contract không đổi. Java493 PASS, full30 REAL child:27/3/0, nguồn scan REAL/gate SIMULATED, checksum/rejoin/cleanup PASS. C1-Gui/C3-replay/measurement/event riêng PASS; event chạy song song lần đầu FAIL retained, cause chưa chứng minh. Human B review NOT RUN; B cần xem policy/trace/clock/identity/lifecycle. Chưa C5/delta/E1/E2; dừng sau C4. Git merge cuối xem báo cáo/PR, không giả review.

## Bàn giao T2-C5 ngày11/10/2026

[Phương pháp baseline](MONITORING_BASELINE_METHOD.md), [kiểm tra tài liệu](../evidence/t2-c5/2026-10-11-verification.md). Base82557bf có mục lục evidence sau C4 merge PR#21. Tài liệu không đổi collector/harness/reducer/wire; sửa mô tả byte type/sequence cũ, phân biệt hold200ms với parent-observed lifetime và policy TEST/production. Rate có eligibility chung, INCONCLUSIVE riêng, latency chính xác NOT MEASURED; E2 chưa delta. Human B review/GUI/LAN/E1/E2 NOT RUN. Không sửa tracker/kế hoạch/old artifacts; QUYET_DINH giữ nguyên vì không quyết định kiến trúc mới. Git cuối xem PR/final; dừng sau C5.

## Bàn giao T3-C1 ngày11/10/2026

[Encoder candidate](MONITORING_DELTA_ENCODER.md), [evidence](../evidence/t3-c1/2026-10-11-verification.md). Main remote12e2838 sau C5PR#22; chưa branch/PR delta hoặc A3 hook delta. Production factory/transport/server giữ full; baseSequence encoder/replay đã có, server delta/resync/freshness/measurement classification và gate chưa làm. STALE6s/full30s bàn giao A/C, không âm thầm thay timeout/heartbeat. Human B review/ChatGPT planning-review NOT RUN theo opt-out; không thay tracker/task completion hoặc MT02–MT06 toàn hệ thống. Người dùng ủy quyền merge commit khi không blocker GitHub và giữ nhánh; Git cuối xem evidence/PR/final. Dừng T3-C1, không T3-C2/E1/E2.

## Bàn giao T3-C2 ngày12/10/2026

Dùng lại AuthorizationService/SessionAuthenticationService/MonitoringStateService C1 và registry/maintenance. Thêm lõi MonitoringStateReducer, resync DTO/handler/parser/client hook, optional heartbeat capability và empty delta từ scan mới; candidate factory full không đổi. T3-A3 chưa tự đánh dấu DONE; A/B cần review auth/binding/store/global lock/ACK/push/parser/capability/clock/lifecycle. [Evidence](../evidence/t3-c2/2026-10-12-verification.md), QD17. Gate MT02–MT06/E1/E2/full candidate GUI/LAN/human review NOT RUN; không thay tracker. Dừng T3-C2, chưa C3/C4. Git cuối trong PR/final.

## Bàn giao T3-C3 ngày12/10/2026

- [Guide](MONITORING_DELTA_FAULT_REPLAY.md), [evidence](../evidence/t3-c3/2026-10-12-verification.md):3MOCKproduction-schema traces/handwritten expected,72positive acceptance replay cases/4detected negatives, summary sinh Java, repeat report byte-identical. Java569/PASS (+15), CLI/ProcessHandle/owned Edge regressions PASS; không source/network benchmark.
- Fix replayer receiving connection dùng PREVIOUS_CONNECTION đúng contract; không sửa encoder/reducer/server/transport production. ACK baseline enumeration, resync recorded/no feedback; retry/ACK-loss/presence/TTL/network/DB trong replay NOT MODELED. Seeded reconnect có0common synced checkpoints ghi riêng, không claim state equivalence ở đó.
- MT02–MT06 PARTIAL cấp toàn gate với evidence REPLAY; T3-C4/human B/GUI/LAN/hai desktop/E1/E2 NOT RUN. Không tự quyết QD10/bật delta hoặc thay tracker/.env/kế hoạch gốc/migrations/exam/PR A-B. Git cuối trong PR/final; giữ nhánh C3 và sync main an toàn theo ủy quyền.

## Bàn giao T3-C4 ngày13/10/2026

Baseee714fc (C3PR26). Opt-in/current capability/fixed mode, reconnect fresh OPEN/full, scope heartbeat callback, actual candidate/proctor Application LOCAL GUI; code2d867c2 + fix stdoutd9475eb. [Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md), QD19 liên kết QD10 presence. Source/JAR/raw/failed attempts giữ riêng, không sửa `.env`/tracker/kế hoạch/migrations/exam/PR A-B. Gate preparation/PARTIAL, cần LAN hai máy và B review; không giả approve hoặc gate20/10 đã diễn ra. Git cuối trong PR/final; giữ nhánh và sync main khi an toàn. Dừng sau C4, không C5/E1/E2.
