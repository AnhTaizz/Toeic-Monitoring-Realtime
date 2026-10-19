# ADR-V2-03 — START toàn bài và deadline ca V2

**Status: PROPOSED_FOR_OWNER_REVIEW — 19/10/2026.** Owner A/B phải duyệt; C review khóa, monitoring/restart và timeout. Chưa owner sign-off; không sửa QD-21, production hoặc migrations trong PR thiết kế này. [Contract ứng viên](../EX02_RICH_EXAM_CONTRACT_V1.md), [decision register](EX02_DECISIONS_ROADMAP.md).

## Context và bằng chứng

Baseline `1d35b53729abade7b460d35ab07d042b6f9f3571` có V11/V12 nhưng chưa rich API/session-version/START gate. [EX04](../EX04_V11_VERIFICATION.md) có HTTP reproducer GET/takeover/save/submit trước START. `ExamService.getCandidateExam` kiểm owner/assignment và attempt state, không kiểm session IN_PROGRESS. `takeoverWriter` đặt deadline ở DB clock sau attempt lock; autosave có save-first fallback; QD-21 cho legacy entry/takeover bắt đầu giờ. Attempt ACTIVE đang cần cho monitoring/READY, **không đủ để cấp quyền làm bài**.

`AudioService.start`: assigned proctor → session FOR UPDATE → manifest → roster attempts FOR UPDATE ORDER BY attempt_id → clock_timestamp() → session IN_PROGRESS + startId + deadlines COALESCE → event. READY hiện cho phép deadline null. `ListeningDelivery` chỉ AFTER_COMMIT; explicit retry chỉ gửi lại cùng ID cho attempt còn ARMED, writer đúng, chưa phát/interrupted, heartbeat/deadline hợp lệ. Reconnect không tự gửi lại; restart đánh interruption. Đây là nền sẵn có, không xây authority thứ hai.

Khác biệt tài liệu/source cần ghi đúng: `ExamService.autosaveAnswers` kiểm ACTIVE/writer/deadline **trước** cache; retry sau final không hứa cached200. `submitExam` kiểm auth/owner rồi cache payload **trước** ACTIVE và current writer check, nên matched cached submit có thể200 sau final; source legacy chưa kiểm current writer trước nhánh cache. Quy tắc “auth-before-cache” không đồng nghĩa mọi retry đều200, và “stale writer luôn bị từ chối” chưa được source legacy bảo đảm ở nhánh submit-cache đó. Không đổi ngầm legacy ở ADR; V2 khuyến nghị stricter current-writer check, test riêng trước triển khai.

## Quyết định khuyến nghị duy nhất

1. **Ca mới `PROCTORED_V2` tính giờ tại proctor START**, DB `clock_timestamp()` sau toàn bộ khóa/READY/file verification. `deadlineAt = startedAt + durationMinutes`, cùng mốc cho roster. Takeover trước START chỉ cấp writer, không tạo deadline. Không dùng null deadline để suy version.
2. **Reuse POST `/api/v1/sessions/{sessionId}/listening/start` là authority duy nhất cho toàn bài TOEIC200 V2.** Giữ HTTP/WS shape v0, startId/manifestId hiện có. Không tạo GENERAL_START hoặc nút START độc lập. Profile V2 hiện chỉ TOEIC200 có Listening; từ chối rich reading-only. Legacy reading-only vẫn entry/takeover theo QD-21; hỗ trợ V2 reading-only cần ADR tiếp theo, chưa có fallback tự start.
3. Explicit `exam_sessions.contract_version` default LEGACY_V1 cho mọi row cũ, immutable sau create; POST v2/sessions đặt PROCTORED_V2; rich exam có content_version2. V1 create từ exam rich bị409, v1 read exam của ca V2 bị409 để tránh downgrade/bypass. V1 save/submit/takeover/audio vẫn dùng nhưng policy branch theo **persisted session version**, không tin header client. Không auto-convert ca legacy đang ACTIVE/SCHEDULED/IN_PROGRESS. Reorganization copy version, cấp ca/attempt mới, không sửa deadline/answers cũ.
4. Giữ deadline đã có: V2 SCHEDULED gặp deadline nonnull là invariant violation →409 INVALID_STATE, rollback START, không reset/COALESCE cho qua. IN_PROGRESS retry/takeover không đổi deadline. LEGACY_V1 giữ COALESCE và deadline entry/save-first hiện tại. Không backfill startedAt/deadline hoặc kéo dài cho ca đã chạy.

Phương án bị loại: takeover làm mốc cho V2 khiến mỗi người có thời gian bắt đầu khác giám thị; suy version theo deadlineNULL nhầm ca legacy chưa vào; đổi toàn bộ ca cũ phá QD21; generalSTART + ListeningSTART tạo hai authority; generalSTART wrapper lúc này tăng wire/UI/migration mà TOEIC200 luôn có Listening. Chỉ đổi nhãn UI nút Listening thành “Bắt đầu bài thi” cho ca V2 sau duyệt, endpoint không đổi.

## State machine và route authorization

SCHEDULED + ACTIVE là lobby/monitoring; READY gắn writerEpoch+manifestId, chưa có quyền làm bài. START transaction chuyển SCHEDULED→IN_PROGRESS; CLOSED là terminal, không reopen. SUBMITTED/TIMED_OUT attempt là terminal độc lập với ca; status/history/result theo quyền vẫn đọc được. Không tạo state attempt mới chỉ để chặn UI.

Mọi route: token còn hiệu lực trước role/scope/cache; foreign/unknown attempt403, unassigned proctor403, revoked401. Candidate endpoint không dành cho proctor. Assignment metadata không chứa câu hỏi/ảnh/keys. Ma trận dưới là **target V2, chưa triển khai**; legacy giữ hiện trạng ngoại trừ thay đổi bảo mật riêng nếu được owner giao task.

| Route / actor | SCHEDULED + ACTIVE | IN_PROGRESS + ACTIVE, chưa deadline | CLOSED / attempt final |
|---|---|---|---|
| Login/history/status / owner | 200, không đặt giờ | 200 | 200 theo quyền, không keys |
| GET v2 preparation / owner | 200 metadata, deadline null | 200 metadata | 409 INVALID_STATE; dùng status/history |
| GET v2 exam/images / owner | 409 INVALID_STATE | 200 | 409 INVALID_STATE |
| GET v1 exam / owner của ca V2 | 409 INVALID_STATE cần client V2 | 409 INVALID_STATE | 409 INVALID_STATE |
| POST v1 takeover / owner | 200 tăng writer, clear READY, deadline null | 200 writer mới, giữ deadline, Listening interruption như cũ | 409 INVALID_STATE |
| POST v1 answers (autosave) / owner | 409 INVALID_STATE, không cache/write | 200 sau commit hoặc STALE/CONFLICT/EXPIRED | 409 INVALID_STATE; request stale writer ưu tiên409 STALE |
| POST v1 submit / owner | 409 INVALID_STATE | 200 sau commit; strict writer/revision | Chỉ matched committed submit + current writer đúng được200; còn lại409 INVALID_STATE/STALE/CONFLICT |
| GET audio manifest/files / owner | 200 chuẩn bị, checksum bắt buộc | Giữ policy ACTIVE/no-replay hiện hành | 409 INVALID_STATE |
| POST audio READY / owner | 200 nếu writer/checksum/speaker đúng | 409 INVALID_STATE | 409 INVALID_STATE |
| GET readiness / assigned proctor | 200 | 200 metadata | Giữ current read policy, không cấp start |
| POST Listening START / assigned proctor | 200 khi tất cả READY | Same manifest →200 cùng startId/startedAt; khác409 CONFLICT | CLOSED409 INVALID_STATE; không resend dù requestId cũ |
| Listening status/event / owner + scope | Status200 NOT_STARTED; không giả STARTED | Giữ startId/writer/phase checks hiện có | Status/history được phép; không cấp lại quyền phát |
| Monitoring WS / owner ACTIVE | Cho phép baseline FULL, quan sát như hiện tại | Cho phép | Giữ final/close hiện hành, không reopen |
| Session create v1 từ exam rich / PROCTOR | 409 INVALID_STATE | Không áp dụng | Không áp dụng |

Lỗi gate áp dụng sau scope, trước đọc content/cache trả success. GET exam/image IN_PROGRESS nhưng hết giờ:409 EXPIRED, không kéo dài; final409 INVALID_STATE; status là đường xác nhận. SCHEDULED không gọi timeout theo deadlineNULL. Hết giờ do worker quyết định bằng DBclock sau khóa và chốt một lần. CLOSED: không ghi mới kể cả chưa có deadline; valid submit receipt vẫn có thể đọc, không emit submit/score lại. Cached submit current writer khác →409 STALE dù request từng commit; status/history cung cấp kết quả không mutation. Retry không bỏ qua revoked/foreign checks. Nếu cached payload cùng requestId khác canonical answers/revision/epoch thì409 CONFLICT; current-writer mismatch ưu tiên409 STALE cho V2.

## Transaction, khóa và races

EX07 phải audit các path liên quan, không chỉ thêm annotation ở controller. Chọn thứ tự V2 **session row → attempt rows tăng attemptId COLLATE "C"**. Ban đầu lookup attempt→session được đọc không khóa, sau khóa phải recheck membership/exam/version/owner. Không được giữ attempt lock rồi chờ session lock. Tất cả code path lấy nhiều khóa (START, READY, takeover, save, submit, timeout, close, reorganization) theo thứ tự đó. Listening heartbeat/status/markLost chỉ lấy attempt nếu không lấy session sau đó; đối chiếu khi triển khai. `SKIP LOCKED` timeout/maintenance không được đánh “đã chốt” khi thực ra bỏ qua khóa; scan lại. Global scheduler không giữ nhiều session rồi đảo thứ tự; một transaction/session hoặc session sorted stable. Lock policy cũ legacy không đổi trong task thiết kế; concurrency với ca khác vẫn phải test.

START transaction target:

1. Auth assigned enabled PROCTOR, requestId1..128 và manifestId đúng; session lock, recheck assignment/state/version. CLOSED409 trước cache. Không tin client readiness counters.
2. Đọc immutable manifest và verify actual files; request manifest mismatch409 CONFLICT. Nếu đã START, trả cùng receipt sau auth/manifest/state, không sửa startedAt/deadline/answers/writer. Nếu chưa START: lock toàn roster stable; recheck ACTIVE, enabled, exam binding, writer>0 trùng READY writer, manifest, speaker/checksum, không interrupted, V2 deadlines null. Roster thiếu/không READY →409 INVALID_STATE.
3. Lấy clock_timestamp() **một lần sau khóa và verification**; set session IN_PROGRESS/startId mới/manifest/startedAt; set mỗi attempt authorized fields+deadline=startedAt+duration. Ghi tất cả trong cùng transaction. Recheck không version/final thay đổi trước commit. Không ACK ở controller trước transaction proxy COMMIT.
4. Publish transactional event, delivery AFTER_COMMIT. Rollback: không startId/deadline/WS; HTTP5xx retryable cho DB transient. Mất response sau COMMIT: giám thị retry cùng manifest →200 cùng persisted receipt; không double-start. Delivery lỗi không undo COMMIT, trạng thái lấy qua HTTP. Chỉ explicit proctor retry được re-deliver **cùng startId** tới ARMED heartbeat/writer/deadline hợp lệ theo ListeningService; đã STARTED/COMPLETED/INTERRUPTED thì không phát lại.

Idempotency scope hiện có là **session+manifest sau START**, không phải bảng requestId: hai requestId khác nhau cho cùng ca+manifest coalesce cùng authority; requestId chỉ correlation. Concurrent START serialize trên session lock; response requestId có thể khác, startId/startedAt/deadline giống nhau. Không hứa retry sau CLOSED trả200; dùng status. Không đưa file/hash network IO vào transaction JavaFX; server file verification có cost, phải đo sau triển khai, không đổi DB clock semantics để tối ưu giả.

V2 save/submit: auth→session/attempt locks→scope/version/current writer→gate→DBclock→canonical map/revision/request cache và mutation. Với submit, sau scope+current writer cho phép đọc committed receipt trước terminal gate; không ghi lần nữa. Autosave cache chỉ kiểm sau ACTIVE/IN_PROGRESS/deadline, giữ behavior hiện có; response bị mất rồi retry sau submit/timeout nhận409 và client fetchstatus, **không kết luận ACK cũ bị mất**. AnswerRevision không giảm, cùng revision khác nội dung409 CONFLICT; stale409 STALE. Giữ Listening answers freeze sau interruption, grading một lần, ACK chỉ sau COMMIT.

| Race | Kết quả tuyến tính bắt buộc |
|---|---|
| Takeover thắng trước START | Epoch mới, READY cũ bị xóa; START409 tới khi ready lại; chưa deadline |
| START thắng trước takeover | START commit cấp deadline cũ cố định; takeover mới đánh interruption, không replay/không gia hạn |
| Save thắng session lock trước START | SCHEDULED409, không đáp án/deadline |
| START thắng trước save | Save epoch đúng có thể200, decisionAt sau START, deadline giữ nguyên |
| Save/submit/timeout đồng thời | Session→attempt serialize, một final; mọi ACK trước final được bảo toàn |
| Close thắng trước START/save | INVALID_STATE, không mutation; cached submit hợp lệ vẫn đọc theo policy |
| Reorg với START/takeover | Lock oldsession→oldattempt; allocate new session/attempts riêng, copy contract version, không reopen cũ |
| Server restart | mark unfinished Listening INTERRUPTED theo code, no replay/rearm; deadline/answers/startId giữ, operator reorg cấp attempt mới nếu cần |

## Acceptance bắt buộc trước merge EX07 runtime

| ID | Test (PG thật / HTTP / WS khi thích hợp) | Expected |
|---|---|---|
| S01 | Owner GET v2/v1 exam + image trước START | 409, không content/bytes/cache metadata |
| S02 | Save/submit trước START qua v1 | 409, answers/revision/deadline không đổi |
| S03 | Takeover SCHEDULED rồi READY deadline null | 200, writer mới, chưa deadline; READY hợp lệ |
| S04 | Assigned START tất cả READY | One DBclock, deadlines đúng duration; WS chỉ sau COMMIT |
| S05 | Foreign owner / unassigned proctor / revoked token cả retry | 403/403/401, không cached receipt/bytes |
| S06 | Barrier 2 START, cùng/khác requestId | Same authority; manifest khác409; không double deadline |
| S07 | Latch START vs takeover, cả hai thứ tự | Đúng race table, không stale writer được phát |
| S08 | Latch save vs START, rollback sau partial write | Gate tuyến tính; rollback không WS/deadline/ACK |
| S09 | Mất HTTP sau commit, explicit retry | Same receipt, ARMED-only redelivery, không replay client journal |
| S10 | Retry autosave sau submit/timeout/expired | 409 theo state/epoch; status xác nhận answers đã ACK |
| S11 | Retry submit sau final/CLOSED | Same receipt nếu auth/current writer/payload đúng; stale/foreign/revoked bị từ chối |
| S12 | Current deadline nonnull SCHEDULED V2 | START409, không reset; legacy COALESCE giữ |
| S13 | Restart/reconnect/app reopen/takeover poststart | INTERRUPTED/no replay/no resume; deadline không đổi |
| S14 | V1 create rich/v1 exam V2/old client |409, không bypass; legacy short reading regression PASS |
| S15 | Timeout/close/reorg nhiều attempts bằng barriers | Không deadlock, một final; new version copy, old data bất biến |
| S16 | Conditional image GET/no token trước START |401 trước cache; owner409; không304 |

Tất cả S01–S16 là **NOT RUN cho target V2** trong task thiết kế. EX04 là bằng chứng hiện trạng legacy, không test target gate. Fixture decision scenarios chỉ kiểm tính nhất quán tài liệu, không thay PG races/WS/GUI. Cần A duyệt version/lock/receipt/current-writer semantics và migration; B duyệt lobby/upgrade/no-replay/UX receipt; C review stable locks/maintenance/monitoring. Sau duyệt mới sửa hợp đồng QD21 bằng phụ lục versioned và thêm regression. Rủi ro: old client chưa V2, file verification kéo dài khóa, giao thoa scheduler, no-replay có thể cần reorg khi mất START — đều phải kiểm thật trước release.
