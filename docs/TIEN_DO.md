# Tiến độ hiện tại

Cập nhật lần cuối: 05/10/2026 — T2-A4 CODE_COMPLETE: Phiên ghi (Takeover Writer) và Kiểm thử tranh chấp trên PostgreSQL; `POST /api/v1/attempts/{attemptId}/takeover`, `GET /api/v1/attempts/{attemptId}/status`, AT01 (phân quyền cách ly thí sinh), AT07 (khóa bi quan giữ qua deadline), AT09 (takeover writer và chặn stale epoch đang chờ lock), AT10 (từ chối nộp bài/lưu bài sau deadline ngay lập tức bằng `clock_timestamp()` sau khóa). 375/375 tests PASS, REAL PG18.6 smoke PASS. Evidence `evidence/t2-a4/2026-10-05-verification.md`.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **2** (09–16/10) — Bắt đầu triển khai thi và giám sát.
- Gate kế tiếp: **12/10 thi cơ bản** — nộp bài, chấm điểm, lưu đáp án, đổi câu hỏi, timer.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | T2-A4 code-complete; chuẩn bị T2-A5 | Takeover Writer, Status/Sync, Concurrency Smoke PG18 (AT01, AT07, AT09, AT10) PASS | Bắt đầu T2-A5 (Tài liệu giao dịch và bằng chứng quyền) | `feat/t2-a4-concurrency-and-takeover` |
| B | _chưa gán_ | T1-B3 code-complete, dừng sau bàn giao | Dashboard/API/model/parser, 331 tests và REAL integration/GUI proctor component PASS | Human A review, GUI candidate toàn luồng, LAN/package máy khác NOT RUN; chuẩn bị Chặng 2 | `feat/t1-b3-proctor-dashboard` |
| C | _chưa gán_ | T1-C4 code-complete; log đo và survey | Tests/package366 + Python9; REAL transport/log-summary/ProcessHandle scan, MOCK overflow và SIMULATED retry PASS | Human B review, GUI/LAN và E1/E2 NOT RUN; chuẩn bị Chặng 2 | `feat/t1-c4-monitoring-measurement` |

## Đang bị chặn

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
| 09/10 | A → B, C | Import đề, answers/revision, requestId, writerEpoch, deadline/state, mã lỗi | Chưa có |
| 12/10 | A → B | Trạng thái đã chốt, takeover writer, state khi reconnect | Chưa có |
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
