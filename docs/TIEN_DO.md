# Tiến độ hiện tại

Cập nhật lần cuối: 05/10/2026 — T1-B4 trên base main `a04aaa2`: app-image Windows kèm runtime (158,4 MB) đóng bằng `scripts/package-client.ps1`; bản đóng gói chạy login candidate/proctor, WS, giám sát, cảnh báo và UNKNOWN với server thật trên máy build (thao tác bằng UI Automation); JavaFX Media phát MP3/WAV/M4A trong app-image, QD-07 chốt MP3. Tests/package 338/338 PASS. **FAIL đã ghi:** app đặt trong thư mục có ký tự ngoài code page ANSI không khởi động (QD-11 đang chờ). Máy Windows thứ hai và LAN thật NOT RUN nên T1-B4 PARTIAL, IT01 PARTIAL. Evidence `evidence/t1-b4/2026-10-05-verification.md`.

Trạng thái trước đó (04/10/2026) — base main PR #7 `1e084d4` đã có A4 V4. T1-B3 có dashboard PROCTOR, roster/presence, timeline/history, parser push, HTTP recovery/scope và xử lý dữ liệu cũ. Tests/package331/331 PASS; REAL PostgreSQL/Spring/HTTP/WS/B2/C3/dashboard và Stage JavaFX proctor hard-kill/UNKNOWN/recovery/history PASS. C3/A4 regression PASS. CODE_COMPLETE B3; nghiệm thu prototype PARTIAL. GUI candidate/proctor toàn luồng, LAN/package máy khác và human A review NOT RUN; TRACKER giữ nguyên. Evidence `evidence/t1-b3/2026-10-04-verification.md`; Git/PR/merge cuối xem report.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

## Đang ở đâu

- Chặng: **1** (03–08/10)
- Gate 04/10 nền tảng — JavaFX build và thử đóng gói, WS có xác thực, ProcessHandle đọc được process: các phần này đã có kết quả trên máy build (xem KIEM_THU).
- Gate kế tiếp: **08/10 prototype** — event lưu DB rồi lên dashboard, không trùng, heartbeat, package chạy trên máy khác. Còn thiếu đúng phần "package chạy trên máy khác".

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | T1-A4; bàn giao roster/presence/history cho B3 | Scoped heartbeat, timeout/recovery/restart, multi-client isolation và lỗi COMMIT PASS | Human C review NOT RUN; dừng sau A4, GUI/LAN chờ kiểm riêng | `feat/t1-a4-monitoring-presence` |
| B | _chưa gán_ | T1-B4 PARTIAL, dừng sau bàn giao | App-image + runtime, luồng login/giám sát trên bản đóng gói, audio MP3/WAV/M4A, QD-07; 338 tests PASS | Chép `ToeicMonitor\` sang máy Windows thứ hai (không IDE/JDK) và chạy checklist trong evidence; quyết QD-11; human A review NOT RUN; không tự làm T2-B1 | `feat/t1-b4-windows-package-audio` |
| C | _chưa gán_ | T1-C3 code-complete; event/queue/retry và gap | Tests/package253; real Windows/B2/DB/ACK/reconnect/gap/cleanup PASS | Human B review/GUI/LAN NOT RUN; bàn giao B3/A, dừng C3 | `feat/t1-c3-monitoring-event-delivery` |

## Đang bị chặn

- [05/10] B bị chặn nghiệm thu T1-B4/IT01 — bởi chưa có máy Windows thứ hai (không IDE/JDK) cùng LAN với máy server — cần nhóm cung cấp máy và chạy checklist "Máy thứ hai" trong `evidence/t1-b4/2026-10-05-verification.md` — hạn gate 08/10.
- [05/10] App-image không khởi động khi đặt trong thư mục có ký tự ngoài code page ANSI của máy (ví dụ `Thử nghiệm TOEIC` trên máy 1252; JVM báo `could not find java.dll`). Thư mục có khoảng trắng và thư mục có dấu trong code page chạy được. Thử nghiệm nhúng `activeCodePage=UTF-8` vào manifest launcher cho kết quả chạy được nhưng chưa đưa vào script — cần B quyết QD-11, A duyệt — hạn 22/10 (trước T3-B5).
- [05/10] `scripts/demo-c3.ps1 -Action Cleanup` lỗi khóa ngoại `monitoring_presence_attempt_id_fkey` sau khi lượt DEMO-C3-A đã có heartbeat (bảng V4 của A4 chưa được script xóa) — cần C (chủ script) bổ sung xóa `monitoring_interruptions`/`monitoring_presence` cho DEMO-C3-A, A xác nhận thứ tự bảng — hạn trước buổi demo 08/10.
- Ghi chú cho A/C: lần đầu chạy server bản mới trên DB dev của máy build, Flyway áp dụng V2–V4 lên schema `public` (trước đó V1); không reset dữ liệu.
- GUI manual end-to-end: **PARTIAL từ 05/10** — B4 đã chạy toàn luồng login candidate/proctor → bật giám sát (ProcessHandle, `msedge.exe` thật) → cảnh báo → UNKNOWN trên bản đóng gói bằng UI Automation; thành viên tự thao tác và server tắt/login lại vẫn NOT RUN. Ghi chú cũ: B3 đã mở Stage JavaFX thật bằng harness và thao tác controls proctor, có ảnh UNKNOWN/recovery/stale. Chưa thao tác toàn app login/candidate/process thật → proctor; cần người dùng kiểm candidate/proctor/sai mật khẩu/server tắt. Component proctor PASS không thay toàn MT01/MT08.
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
