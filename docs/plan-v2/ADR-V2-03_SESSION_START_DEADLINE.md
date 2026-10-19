# ADR-V2-03 — START toàn bài và deadline ca V2

**Status: TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE — 19/10/2026.** [Nguồn xác nhận](EX02_TEAM_APPROVAL_20261019.md): “User-provided confirmation from the project team representative, 2026-10-19.” Đại diện nhóm xác nhận A/B/C đã đồng ý D01–D09, hướng ADR và D10 Option A; không phải chữ ký riêng hoặc GitHub Approving Review. Thiết kế được thông qua, runtime chưa triển khai/nghiệm thu. Không sửa QD-21 legacy, production hoặc migrations trong task này. [Contract](../EX02_RICH_EXAM_CONTRACT_V1.md), [decision register](EX02_DECISIONS_ROADMAP.md).

## Context và bằng chứng

Baseline `1d35b53729abade7b460d35ab07d042b6f9f3571` có V11/V12 nhưng chưa rich API/session-version/START gate. [EX04](../EX04_V11_VERIFICATION.md) có HTTP reproducer GET/takeover/save/submit trước START. `ExamService.getCandidateExam` kiểm owner/assignment và attempt state, không kiểm session IN_PROGRESS. `takeoverWriter` đặt deadline ở DB clock sau attempt lock; autosave có save-first fallback; QD-21 cho legacy entry/takeover bắt đầu giờ. Attempt ACTIVE đang cần cho monitoring/READY, **không đủ để cấp quyền làm bài**.

`AudioService.start`: assigned proctor → session FOR UPDATE → manifest → roster attempts FOR UPDATE ORDER BY attempt_id → clock_timestamp() → session IN_PROGRESS + startId + deadlines COALESCE → event. READY hiện cho phép deadline null. `ListeningDelivery` chỉ AFTER_COMMIT; explicit retry chỉ gửi lại cùng ID cho attempt còn ARMED, writer đúng, chưa phát/interrupted, heartbeat/deadline hợp lệ. Reconnect không tự gửi lại; restart đánh interruption. Đây là nền sẵn có, không xây authority thứ hai.

Khác biệt tài liệu/source cần ghi đúng: `ExamService.autosaveAnswers` kiểm ACTIVE/writer/deadline **trước** cache; retry sau final không hứa cached200. `submitExam` kiểm auth/owner rồi cache payload **trước** ACTIVE và current writer check, nên matched cached submit có thể200 sau final; source legacy chưa kiểm current writer trước nhánh cache. Quy tắc “auth-before-cache” không đồng nghĩa mọi retry đều200, và “stale writer luôn bị từ chối” chưa được source legacy bảo đảm ở nhánh submit-cache đó. Không đổi ngầm legacy ở ADR; V2 khuyến nghị stricter current-writer check, test riêng trước triển khai.

## Quyết định thiết kế đã được nhóm thông qua theo đại diện

1. **Ca mới `PROCTORED_V2` tính giờ tại proctor START**, DB `clock_timestamp()` sau toàn bộ khóa/READY/file verification. `deadlineAt = startedAt + durationMinutes`, cùng mốc cho roster. Takeover trước START chỉ cấp writer, không tạo deadline. Không dùng null deadline để suy version.
2. **Reuse POST `/api/v1/sessions/{sessionId}/listening/start` là authority duy nhất cho toàn bài TOEIC200 V2.** Giữ HTTP/WS shape v0, startId/manifestId hiện có. Không tạo GENERAL_START hoặc nút START độc lập. Profile V2 hiện chỉ TOEIC200 có Listening; từ chối rich reading-only. Legacy reading-only vẫn entry/takeover theo QD-21; hỗ trợ V2 reading-only cần ADR tiếp theo, chưa có fallback tự start.
3. Explicit `exam_sessions.contract_version` default LEGACY_V1 cho mọi row cũ, immutable sau create; POST v2/sessions đặt PROCTORED_V2; rich exam có content_version2. V1 create từ exam rich bị409, v1 read exam của ca V2 bị409 để tránh downgrade/bypass. V1 save/submit/takeover/audio vẫn dùng nhưng policy branch theo **persisted session version**, không tin header client. Không auto-convert ca legacy đang ACTIVE/SCHEDULED/IN_PROGRESS. Reorganization copy version, cấp ca/attempt mới, không sửa deadline/answers cũ.
4. Giữ deadline đã có: V2 SCHEDULED gặp deadline nonnull là invariant violation →409 INVALID_STATE, rollback START, không reset/COALESCE cho qua. IN_PROGRESS retry/takeover không đổi deadline. LEGACY_V1 giữ COALESCE và deadline entry/save-first hiện tại. Không backfill startedAt/deadline hoặc kéo dài cho ca đã chạy.
5. **D10 Option A: prefetch ảnh Part1 có quyền trước START; mọi roster member phải có Audio READY AND Visual READY** cùng writerEpoch/preparationEpoch/manifest, lease và login hợp lệ. Visual READY là precondition, không authority mới. Chấp nhận có chủ đích ảnh đã tải có thể bị chủ máy trích xuất; chỉ Part1 allowlist, không prompts/options/keys/passages hoặc ảnh chỉ thuộc Part4/6/7. RAM/no-store không bảo mật tuyệt đối. API/DTO/hash/lifecycle chính xác ở contract mục7.1.

Phương án bị loại: takeover làm mốc cho V2 khiến mỗi người có thời gian bắt đầu khác giám thị; suy version theo deadlineNULL nhầm ca legacy chưa vào; đổi toàn bộ ca cũ phá QD21; generalSTART + ListeningSTART tạo hai authority; generalSTART wrapper lúc này tăng wire/UI/migration mà TOEIC200 luôn có Listening. Chỉ đổi nhãn UI nút Listening thành “Bắt đầu bài thi” cho ca V2 sau duyệt, endpoint không đổi.

## State machine và route authorization

SCHEDULED + ACTIVE là lobby/monitoring; Audio+Visual READY gắn writerEpoch/preparationEpoch/manifest, chưa có quyền đọc câu hỏi hoặc làm bài. Part1 photo prefetch là ngoại lệ hữu hạn. START transaction chuyển SCHEDULED→IN_PROGRESS; CLOSED là terminal, không reopen. SUBMITTED/TIMED_OUT attempt là terminal độc lập với ca; status/history/result theo quyền vẫn đọc được. Không tạo state attempt mới chỉ để chặn UI.

Mọi route: token còn hiệu lực trước role/scope/cache; foreign/unknown attempt403, unassigned proctor403, revoked401. Candidate endpoint không dành cho proctor. Preparation chứa metadata/Part1 image manifest, không câu hỏi/options/keys/answers/source URLs. Ma trận dưới là **target V2, chưa triển khai**; legacy giữ hiện trạng ngoại trừ thay đổi bảo mật riêng nếu được owner giao task.

| Route / actor | SCHEDULED + ACTIVE | IN_PROGRESS + ACTIVE, chưa deadline | CLOSED / attempt final |
|---|---|---|---|
| Login/history metadata / owner |200, không đặt giờ |200 |200 theo quyền, không keys |
| V1 status/path+query alias có answers / owner V2 |409 INVALID_STATE; dùng preparation metadata |200 theo quyền |200 kết quả/history theo quyền |
| GET v2 preparation / owner |200 metadata + Part1 manifest, deadline null |200 metadata, visualManifest null; full nội dung từ exam |409 INVALID_STATE; dùng status/history |
| GET v2 exam / owner |409 INVALID_STATE |200 |409 INVALID_STATE |
| GET v2 images / owner |200 chỉ Part1 allowlist + current visualManifestId; other-Part/foreign403 |200 referenced verified image; expired409 EXPIRED |409 INVALID_STATE |
| GET v1 exam / owner của ca V2 | 409 INVALID_STATE cần client V2 | 409 INVALID_STATE | 409 INVALID_STATE |
| POST v1 takeover / owner | 200 tăng writer, clear READY, deadline null | 200 writer mới, giữ deadline, Listening interruption như cũ | 409 INVALID_STATE |
| POST v1 answers (autosave) / owner | 409 INVALID_STATE, không cache/write | 200 sau commit hoặc STALE/CONFLICT/EXPIRED | 409 INVALID_STATE; request stale writer ưu tiên409 STALE |
| POST v1 submit / owner | 409 INVALID_STATE | 200 sau commit; strict writer/revision | Chỉ matched committed submit + current writer đúng được200; còn lại409 INVALID_STATE/STALE/CONFLICT |
| GET audio manifest/files / owner | 200 chuẩn bị, checksum bắt buộc | Giữ policy ACTIVE/no-replay hiện hành | 409 INVALID_STATE |
| POST audio READY / owner | 200 nếu writer/checksum/speaker đúng | 409 INVALID_STATE | 409 INVALID_STATE |
| POST v2 visual/ready / owner |200 ATTEST/INVALIDATE sau current writer/epoch/manifest và file checks; ATTEST cần Audio READY |409 INVALID_STATE, không quay lobby |409 INVALID_STATE |
| GET v2 session readiness / assigned proctor |200 Audio+Visual flags, reasons/lease, không content |200 flags allReady=false, không authority mới |200 scoped metadata, allReady=false |
| GET readiness / assigned proctor | 200 | 200 metadata | Giữ current read policy, không cấp start |
| POST Listening START / assigned proctor |200 chỉ mọi roster Audio+Visual READY hiện hành, thiếu409 không mutation |Same audio manifest →200 cùng startId/startedAt; khác409 CONFLICT, không reattest hay double-start |CLOSED409 INVALID_STATE; không resend dù requestId cũ |
| Listening status/event / owner + scope | Status200 NOT_STARTED; không giả STARTED | Giữ startId/writer/phase checks hiện có | Status/history được phép; không cấp lại quyền phát |
| Monitoring WS / owner ACTIVE | Cho phép baseline FULL, quan sát như hiện tại | Cho phép | Giữ final/close hiện hành, không reopen |
| Session create v1 từ exam rich / PROCTOR | 409 INVALID_STATE | Không áp dụng | Không áp dụng |

Lỗi gate áp dụng sau scope, trước đọc content/cache trả success. GET exam/image IN_PROGRESS nhưng hết giờ:409 EXPIRED, không kéo dài; final409 INVALID_STATE; status là đường xác nhận. SCHEDULED không gọi timeout theo deadlineNULL. Hết giờ do worker quyết định bằng DBclock sau khóa và chốt một lần. CLOSED: không ghi mới kể cả chưa có deadline; valid submit receipt vẫn có thể đọc, không emit submit/score lại. Cached submit current writer khác →409 STALE dù request từng commit; status/history cung cấp kết quả không mutation. Retry không bỏ qua revoked/foreign checks. Nếu cached payload cùng requestId khác canonical answers/revision/epoch thì409 CONFLICT; current-writer mismatch ưu tiên409 STALE cho V2.

## Transaction, khóa và races

EX07 phải audit các path liên quan, không chỉ thêm annotation ở controller. Chọn thứ tự V2 **session row → attempt rows tăng attemptId COLLATE "C" → readiness rows**. Ban đầu lookup attempt→session được đọc không khóa, sau khóa phải recheck membership/exam/version/owner. Không được giữ attempt/readiness/auth lock rồi chờ session lock. Tất cả code path lấy nhiều khóa (START, audio/visual READY, invalidate/renew, takeover, save, submit, timeout, close, reorganization, revocation làm mất READY) theo thứ tự đó. Token login session của ATTEST là metadata private, START recheck validity; revocation/disconnect invalidation phải serialize cùng attempt gate, không giữ token-row lock theo chiều ngược. Listening heartbeat/status/markLost chỉ lấy attempt nếu không lấy session sau đó; đối chiếu khi triển khai. `SKIP LOCKED` timeout/maintenance không được đánh “đã chốt” khi thực ra bỏ qua khóa; scan lại. Global scheduler không giữ nhiều session rồi đảo thứ tự; một transaction/session hoặc session sorted stable. Lock policy cũ legacy không đổi trong task thiết kế; concurrency với ca khác vẫn phải test.

START transaction target:

1. Auth assigned enabled PROCTOR, requestId1..128 và manifestId đúng; session lock, recheck assignment/state/version. CLOSED409 trước cache. Không tin client readiness counters.
2. Đọc immutable audio manifest và verify actual files; request manifest mismatch409 CONFLICT. Nếu đã START, trả cùng receipt sau auth/manifest/state, không sửa startedAt/deadline/answers/writer; không yêu cầu Visual READY được ghi lại sau START. Nếu chưa START: lock toàn roster stable và readiness; recheck ACTIVE, enabled, exam binding, writer>0 trùng cả Audio+Visual READY writer, preparationEpoch khớp, audio manifest/checksum/speaker, visual manifest/verified Part1 files/attested login session, không interrupted, V2 deadlines null. Không lấy `allReady=true` từ UI làm điều kiện đủ.
3. Lấy clock_timestamp() **một lần sau khóa và verification**, dùng để kiểm visual lease chưa hết (`decisionAt < leaseExpiresAt`) và mọi readiness freshness. Bất kỳ candidate thiếu/hỏng/stale/expired một loại READY →409 INVALID_STATE, rollback không startedAt/deadline/startId/WS/answers/revision/writer mutation; session vẫn SCHEDULED. Nếu đủ, set session IN_PROGRESS/startId mới/audio manifest/startedAt; set mỗi attempt authorized fields+committed visualManifestId+deadline=startedAt+duration. Ghi tất cả trong cùng transaction, một authority/mốc giờ. Không ACK ở controller trước transaction proxy COMMIT.
4. Publish transactional event, delivery AFTER_COMMIT. Rollback: không startId/deadline/WS; HTTP5xx retryable cho DB transient. Mất response sau COMMIT: giám thị retry cùng manifest →200 cùng persisted receipt; không double-start. Delivery lỗi không undo COMMIT, trạng thái lấy qua HTTP. Chỉ explicit proctor retry được re-deliver **cùng startId** tới ARMED heartbeat/writer/deadline hợp lệ theo ListeningService; đã STARTED/COMPLETED/INTERRUPTED thì không phát lại.

Idempotency scope hiện có là **session+manifest sau START**, không phải bảng requestId: hai requestId khác nhau cho cùng ca+manifest coalesce cùng authority; requestId chỉ correlation. Concurrent START serialize trên session lock; response requestId có thể khác, startId/startedAt/deadline giống nhau. Không hứa retry sau CLOSED trả200; dùng status. Không đưa file/hash network IO vào transaction JavaFX; server file verification có cost, phải đo sau triển khai, không đổi DB clock semantics để tối ưu giả.

V2 save/submit: auth→session/attempt locks→scope/version/current writer→gate→DBclock→canonical map/revision/request cache và mutation. Với submit, sau scope+current writer cho phép đọc committed receipt trước terminal gate; không ghi lần nữa. Autosave cache chỉ kiểm sau ACTIVE/IN_PROGRESS/deadline, giữ behavior hiện có; response bị mất rồi retry sau submit/timeout nhận409 và client fetchstatus, **không kết luận ACK cũ bị mất**. AnswerRevision không giảm, cùng revision khác nội dung409 CONFLICT; stale409 STALE. Giữ Listening answers freeze sau interruption, grading một lần, ACK chỉ sau COMMIT.

| Race | Kết quả tuyến tính bắt buộc |
|---|---|
| Takeover thắng trước START | Epoch mới, READY cũ bị xóa; START409 tới khi ready lại; chưa deadline |
| Visual invalidate/revoke/disconnect thắng trước START |Cả READY cũ mất hiệu lực; START409, không đồng hồ/WS; lease giới hạn disconnect chưa quan sát được |
| Visual lease hết trong lúc START chờ khóa |Decision clock sau lock/hash thấy expired →409, không dùng giờ trước lock để cho qua |
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
| S01 | Owner GET v2/v1 full exam trước START; images kiểm riêng V01–V20 |409, không prompts/options/keys/passages; ngoại lệ Part1 prefetch được D10 cho phép |
| S02 | Save/submit trước START qua v1 | 409, answers/revision/deadline không đổi |
| S03 | Takeover SCHEDULED rồi READY deadline null | 200, writer mới, chưa deadline; READY hợp lệ |
| S04 | Assigned START toàn roster Audio+Visual READY |One DBclock, deadlines đúng duration; WS chỉ sau COMMIT |
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
| S16 | Conditional image GET/no token trước START |401 trước cache; owner Part1 current manifest200, non-Part1-only403; không304 |

## Acceptance visual Option A — runtime NOT RUN

| ID | Test HTTP/PG/WS/GUI phải triển khai | Expected target |
|---|---|---|
| V01 |Owner SCHEDULED preparation →6slots Part1, có reuse media |200 manifest/hash đúng, không question/options/answers/source paths |
| V02 |Owner/foreign/wrong-Part4/7-only/wrong-exam/unverified media |200 chỉ allowlist Part1; sai scope403 trước file/cache |
| V03 |Revoked/missing token, kể cả If-None-Match/If-Modified-Since |401, không bytes/cache/304; no-store cho owner200 |
| V04 |Hash/size/signature/dimensions/decode lỗi hoặc file mất |Không Visual READY, START409; HTTP authorized corrupt409 |
| V05 |Visual ATTEST writer cũ |409 STALE, không đổi readiness/deadline |
| V06 |Epoch/manifest cũ, verifiedAssets thiếu/thừa/trùng/saihash |409 CONFLICT hoặc400 INVALID_INPUT, không READY |
| V07 |ATTEST trước ảnh/UI prepared hoặc trước Audio READY |Client không gửi; server không Audio READY409; GUI chứng minh client preconditions |
| V08 |ATTEST/retry/renew/new requestId, lease hết và INVALIDATE retry |Receipt chỉ sau commit; retry không renew/resurrect; new renewal đúng epoch; invalidate xoay một lần |
| V09 |START thiếu Visual READY của một member |409, session SCHEDULED, không startId/clock/deadline/WS/answers/revision/writer mutation |
| V10 |START thiếu Audio READY dù Visual trước đây valid |409; audio withdraw đã invalidate visual/epoch |
| V11 |Latches START vs takeover/invalidate/revoke, hai thứ tự |Cũ không authorize client mới; đã START không reset; lease kiểm sau khóa |
| V12 |Toàn roster cả hai READY |Một START/deadline, committed visual ID, WS AFTER_COMMIT |
| V13 |Mất response START rồi retry/concurrent duplicate |Cùng startId/deadline; no double authority/replay; ARMED redelivery cùng ID |
| V14 |Image error/scene failure sau commit, trước play hoặc khi chuyển Part1 |Dispose/INTERRUPTED, không tự replay/reset deadline; reorg nếu cần |
| V15 |GUI Stage thật, decode + layout +2pulses +widget snapshot |Trace IMAGE_ATTACHED→LAYOUT_DONE→PULSE1→PULSE2→PLAY_CALLED→ON_PLAYING; không chỉ new ImageView |
| V16 |Hold rich GET/scene transition/mediaOnReady độc lập bằng latch |Không play trước imageVisibleGate AND mediaReady; dùng v2 provider, no GETv1 |
| V17 |Slow network/render/DTO lỗi hoặc vượt3000ms; heartbeat stale |Fail closed, bounded wait, INTERRUPTED; không nới6000ms server timeout |
| V18 |Server restart/WS reconnect/rời app/logout trước START |Epoch mới/READY cũ không dùng lại; lease giới hạn mất kết nối chưa quan sát |
| V19 |START valid nhưng reconnect/restart sau đó |No replay/resume; journal giữ ID, deadline và receipt bất biến |
| V20 |Legacy QD21/client/audio; V2 old client cố GETv1 |Legacy semantics giữ; V2 GETv1409; Part3/4 shared segment, một player |

S01–S16 giữ IDs, chỉ sửa image expectations theo quyết định D10, không xóa test cũ. S01–S16/V01–V20 đều **NOT IMPLEMENTED / NOT RUN cho target runtime V2**. Fixture P01–P09/N01–N20 chỉ kiểm thiết kế; không thay backend auth/PG/WS races hoặc GUI thật. Thiết kế D01–D10/ADR đã được đại diện nhóm xác nhận, không còn blocker “chưa owner đồng ý” này; implementation acceptance, source/data evidence và human merge riêng vẫn bắt buộc. Rủi ro: rò ảnh trước START được chấp nhận, server không thấy pixel vật lý, client có thể khai báo sai, lease có độ trễ, old client chưa V2, file hashing giữ khóa, giao thoa scheduler và no-replay cần reorg khi sự cố — phải kiểm thật trước release.
