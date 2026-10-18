# Cross-review QD-31 và QD-32 — 18/10/2026

**Main được fetch và review: `f86403489bdd01887b926f2f9e4b72ddf05d1423`.** Technical verdict: **QD-31 ACCEPT; QD-32 ACCEPT** trong phạm vi contract hiện tại. Không phát hiện P0/P1 qua đối chiếu source và các test đã chạy. Đây là nhận xét kỹ thuật của agent, **không phải A/C ký duyệt**: cả hai QD giữ nguyên `IMPLEMENTED`, owner approval **PENDING**.

**Kiểm thử mới trên main này: 166 PASS, 0 FAIL/ERROR/SKIP**, gồm **25 case PostgreSQL thật** và 141 case unit/client với fixture MOCK. Không chạy lại toàn bộ Maven suite, không lấy kết quả stabilization cũ làm lượt kiểm mới. Đã xác nhận 0 schema TEST còn lại và dừng cluster riêng; không reset DB dùng chung/Docker volume.

## 1. Nền review, phạm vi và evidence

- Fetch remote main trong checkout riêng `target/cross-review-20261018`, nhánh `review/qd31-qd32-20261018`; workspace gốc bẩn và worktree khác được bảo toàn. Không checkout/sửa/push nhánh PR47, PR51 hoặc PR52; chỉ đọc danh sách file PR51 để tránh trùng phạm vi.
- Chỉ công bố file mới trong `evidence/cross-review-20261018/`. Theo yêu cầu tránh các file PR47/51/52 đang sửa, không cập nhật `TIEN_DO`, `NHAT_KY`, `KIEM_THU`, `VAI_*`, `QUYET_DINH` hoặc `PROTOCOL`; packet này chứa nhật ký, kết quả test và các bước owner cần làm. Không đổi tracker, protocol, production code hoặc test source.
- Contract đọc: [QD-31/QD-32](../../docs/QUYET_DINH.md#qd-31--thực-thi-qd-29-cấp-active-fail-closed-legacy-active-chỉ-đọc), [PROTOCOL](../../docs/PROTOCOL.md#ổn-định-hóa17102026--đã-cài-chờ-ac-xác-nhận), [hợp đồng](../../Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md). Các đường dẫn source trong bảng dưới trỏ đúng tree main được review; [source-manifest.json](source-manifest.json) ghi Git blob ID tại SHA này.
- [Stabilization report](../stabilize-20261017/REPORT.md) là evidence **lịch sử** của source `3702b6d009e4ade0ac151bad6a408c1e24b8539c` (clean/package924 PASS/4SKIP). Chỉ dùng để truy nguyên quyết định và regression, không gán số đó cho main hoặc lượt review mới.
- Evidence mới: [test-results.json](test-results.json), [lệnh và môi trường tái chạy](test-commands.json), [cleanup](environment-cleanup.json), [manifest source](source-manifest.json), [SHA256SUMS](SHA256SUMS.txt). Không công bố XML system properties, token, credential, raw HTTP/SQL/log hoặc đường dẫn riêng của máy.

### Các lượt kiểm mới

| Lượt | Phạm vi | PASS | FAIL / ERROR / SKIP | PostgreSQL |
|---|---|---:|---|---:|
| targeted-1 | Protocol identifier; client entry/monitoring/API/history/dashboard; server validation, QD-29, reorganization và ba history endpoint | 161 | 0 / 0 / 0 | 20 |
| targeted-history-extra | Raw/Unicode history cursor giữ nguyên ID; ownership/assignment cho ACTIVE/SUBMITTED/TIMED_OUT; ẩn monitoring-only rows và từ chối session bị revoke | 5 | 0 / 0 / 0 | 5 |
| Tổng case duy nhất | Không cộng lại case trùng giữa hai lượt | **166** | **0 / 0 / 0** | **25** |

Windows, JDK21.0.8, Maven3.9.11, PostgreSQL18.1. Spring server/HTTP/JDBC/Flyway và transaction chạy thật trên cluster TEST loopback riêng, từng suite tạo schema UUID rồi drop. Dữ liệu thí sinh/lịch sử là fixture TEST, không phải dữ liệu production. Client HTTP tests dùng server HTTP fixture trả payload MOCK; controller/entry tests dùng gateway/transport MOCK và executor/worker có kiểm soát. Không chạy LAN, GUI nghe thật, tải, benchmark, MSI hoặc test của nhánh PR47.

## 2. QD-31 — Attempt ID fail-closed

### 2.1 Contract hiện tại

1. Attempt mới là `sessionId + '-' + username`, toàn bộ ID phải thuộc `[A-Za-z0-9_.:-]{1,128}`. Không trim, chuẩn hóa Unicode, transliterate hoặc đổi ID đã lưu. Text ID trong DB có contract rộng hơn để vẫn đọc legacy.
2. `createSession` kiểm regex/duplicate của toàn batch trước INSERT. `ListeningService.reorganize` kiểm ID thay thế và gọi lại `createSession` trong transaction. Collision với attempt đã có, kể cả FINAL, không mở lại hoặc ghi đè dữ liệu.
3. Legacy **exam attempt ACTIVE** ngoài monitoring v0 vẫn đọc được theo quyền; takeover/autosave/submit/audio READY hợp lệ về shape bị chặn bằng409 `INVALID_STATE`, `retryable:false`. Ownership/state/row lock nằm trước guard compatibility; guard nằm trước thay đổi writer/answers/deadline/readiness.
4. Từ chối request không làm mất dữ liệu đã lưu. Timeout scheduler vẫn được phép chốt bài đã hết hạn theo contract; không diễn giải “chỉ đọc” là cấm timeout vĩnh viễn. Monitoring-only row không phải exam history hợp lệ và được lọc riêng.
5. Client hiện tại chặn vào thi với ID không tương thích; `ExamEntry` gặp INVALID_STATE đọc lại status một lần rồi dừng nếu vẫn ACTIVE, không tự takeover lặp. Luồng save/submit/status có ngân sách retry/poll hữu hạn.

### 2.2 Source và methods đã kiểm

| Source | Methods / điều đã đối chiếu |
|---|---|
| [AttemptIdentifier.java](../../protocol/src/main/java/vn/edu/toeic/protocol/exam/AttemptIdentifier.java) | `valid`, `monitoringV0`, `MONITORING_INCOMPATIBLE_MESSAGE`; phân biệt DB raw ID với monitoring ASCII/128 |
| [ExamService.java](../../server/src/main/java/vn/edu/toeic/server/exam/ExamService.java) | `createSession` (L487): preflight toàn batch; `ON CONFLICT` trên cả session và attempt; lỗi RuntimeException rollback toàn transaction |
| cùng ExamService | `autosaveAnswers` (L146), `submitExam` (L659), `takeoverWriter` (L1016), `requireMonitorable` (L1220): row lock/owner/state/guard trước mutation; autosave kiểm writer trước guard; các lỗi vẫn409 non-retryable |
| cùng ExamService | `getAttemptStatus` (L1123), `listCandidateAttempts` (L70), `timeoutAttempt`: raw read theo quyền, pagination history và timeout dùng dữ liệu đã commit |
| [AudioService.java](../../server/src/main/java/vn/edu/toeic/server/audio/AudioService.java) | `ready`: owner trước resource lookup; session→attempt lock; active/compatibility trước readiness write |
| [ListeningService.java](../../server/src/main/java/vn/edu/toeic/server/audio/ListeningService.java) | `reorganize`: quyền/old row lock, ID thay thế, dùng `exams.createSession`; lưu replacement riêng, không reset bài cũ |
| [JdbcAttemptScopeStore.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/JdbcAttemptScopeStore.java), [AuthorizationService.java](../../server/src/main/java/vn/edu/toeic/server/auth/AuthorizationService.java) | `canReadExam`/`requireExamRead`: candidate owner hoặc assigned proctor, ACTIVE/SUBMITTED/TIMED_OUT; monitoring scope ACTIVE là luồng khác |
| [ApiExceptionHandler.java](../../server/src/main/java/vn/edu/toeic/server/auth/ApiExceptionHandler.java) | `examApiException`, handler `InvalidExamException`: status/code ổn định và `retryable:false` |
| [ToeicClientApplication.java](../../client/src/main/java/vn/edu/toeic/client/ToeicClientApplication.java), [ExamEntry.java](../../client/src/main/java/vn/edu/toeic/client/exam/ExamEntry.java) | Guard ID ở entry; `open`/`claimWriter` không lặp takeover khi legacy vẫn ACTIVE |
| [ExamSession.java](../../client/src/main/java/vn/edu/toeic/client/exam/ExamSession.java), [ExamSettings.java](../../client/src/main/java/vn/edu/toeic/client/exam/ExamSettings.java) | Phân loại INVALID_STATE; `onPollStatus`, `nextSubmitStep`, `onSyncStatus` có giới hạn; không coi error là SAVED/SUBMITTED |

**Thứ tự transaction:** regex/duplicate của batch được kiểm trước ghi; lookup tài khoản/va chạm có thể xảy ra sau INSERT session nhưng vẫn trong `@Transactional` và rollback toàn bộ nếu thất bại. Không nhầm rollback không có thay đổi bền vững với việc mọi lookup đều chạy trước INSERT. Hai request trùng session dùng `ON CONFLICT(session_id) DO NOTHING`; attempt collision dùng `ON CONFLICT(attempt_id) DO NOTHING`, lỗi400 thay unique-violation500.

### 2.3 Evidence mới và phạm vi kiểm chứng

| Yêu cầu | Evidence đã chạy trên main |
|---|---|
| Regex, biên128/129, raw Unicode/space không bị chuẩn hóa | `AttemptIdentifierBoundaryTest`; `ExamRuntimePostgresTest.qd29GeneratedIdBoundaryUnicodeAndRoleAreCheckedBeforeAnyMutation` |
| Batch có người hợp lệ + username Unicode/space phải từ chối toàn bộ | Cùng PG case:400 INVALID_INPUT, false retryable, không tạo session/attempt; source xác nhận batch regex trước mutation |
| Collision FINAL và duplicate candidate | `activeIssuanceRejectsEntireBatchAndPreservesCollidingFinalAttempt`:400; bài SUBMITTED/revision42 giữ nguyên |
| Concurrent create không500 | `qd29ConcurrentSameSessionCreatesOneSessionAndLoserGetsStable400`: khóa PG buộc cả hai chờ; kết quả đúng một200/một400, một attempt commit |
| Legacy ACTIVE own takeover/save/submit/READY | `qd29LegacyActiveIsReadableButCannotBeEnteredSavedSubmittedOrMarkedReady`: cả bốn409 INVALID_STATE/false retryable/fixed message; snapshot row trước/sau bằng nhau, không readiness/submit-request mới |
| Đọc legacy và không lộ cho người khác | Cùng case: candidate/assigned proctor status200, candidate khác403; `queryStatusReadsRawIdsWithoutRelaxingPathRoutingAndChecksOwner` có9 tham số; lượt bổ sung history raw/cursor và quyền ba state |
| Legacy FINAL và cấp lượt khác không sửa bài cũ | `qd29LegacyFinalKeepsReadsAndReplacementUsesSafeNewAttempt`; `ListeningRuntimePostgresTest.onlyAssignedProctorCreatesOneFreshAttemptAndOldOneTimesOutWithMarker`, `proctorB4ApiUsesRealPermissionReadinessAndIdempotentReplacement` |
| Client legacy dừng entry | `ExamPaperAndEntryTest.legacyActiveRejectedByServerEndsEntryWithoutLooping`: một takeover, hai status rồi hoàn tất exceptional; `ExamMonitoringIntegrationTest` kiểm guard ID/scope |

**Giới hạn của bằng chứng:** foreign legacy takeover/status có negative HTTP trực tiếp; thứ tự foreign save/submit/READY được kiểm source nhưng chưa có case HTTP legacy riêng cho từng nhánh trong lượt này. Legacy ACTIVE snapshot regression dùng row ban đầu có answers rỗng/deadline null; vị trí guard trước mutation và các test FINAL/reorganization củng cố bất biến, nhưng không nhận là đã chạy thêm matrix legacy ACTIVE có answers/deadline không rỗng. Không thử mọi cặp session/username gây ambiguous concatenation đồng thời; constraint/ON CONFLICT và rollback đã kiểm source, race cùng session đã chạy thật.

### 2.4 Findings và giới hạn đã biết

| Mức / mã | Nhận xét | Ảnh hưởng và xử lý |
|---|---|---|
| P0/P1 | Không có phát hiện mới có evidence | Không có corrective production patch/reproducer cần mở trong review này |
| P2 · QD31-L1 | Username ngoài ASCII hoặc không còn đủ ngân sách128 ký tự không thể cấp attempt mới; đổi sessionId sang ASCII không khắc phục username Unicode | Đây là giới hạn đã ghi QD-31, không phải fallback tự động. A/C phải xác nhận quy trình vận hành trước khi ký |
| P2 · QD31-L2 | Legacy có deadline sẽ timeout bình thường; legacy chưa vào (deadline null) giữ ACTIVE cho audit; một legacy chưa READY chặn start Listening của ca | Không reset/cancel/migrate bài cũ để ép PASS; giám thị cấp ca mới cho tài khoản tương thích, giữ liên kết audit |
| P2 · QD31-T1 | Một số negative matrix nêu trên chỉ được đối chiếu source, chưa thành regression riêng | Có thể bổ sung targeted regression sau theo owner; không diễn giải166PASS là bao phủ mọi tổ hợp |

**Vận hành username Unicode:** trước kỳ thi, owner A/B kiểm danh sách username dự định gán và độ dài ID sinh ra. Dùng username ASCII được cấp qua quy trình tài khoản hiện có; display name vẫn có thể là Unicode. Nếu tài khoản đã có username Unicode, dừng cấp lượt và chuyển cho A/C lựa chọn/chấp thuận tài khoản thay thế có đối soát danh tính và audit. Review này không tạo/đổi tên tài khoản, sửa foreign key, transliterate username hay chuyển answers. Không bảo người vận hành chỉ đổi sessionId rồi thử lại vô hạn. Chính sách/migration tài khoản nếu cần là task riêng được owner cho phép.

### 2.5 Verdict, câu hỏi owner và comment đề xuất

**Technical verdict: ACCEPT** cho policy đã mô tả, với QD31-L1/L2 là giới hạn vận hành công khai. **Owner approval: PENDING**. Nếu yêu cầu thực tế bắt buộc cấp lượt cho username Unicode hiện hữu mà không có phương án tài khoản tương thích được duyệt, đó là blocker triển khai của môi trường đó; review này không xác nhận deployment đã sẵn sàng.

A/C cần xác nhận: có chấp nhận fail-closed/legacy chỉ đọc và việc username Unicode không thể cấp lượt mới không; ai chịu trách nhiệm kiểm danh sách/cấp tài khoản tương thích và đối soát audit; có yêu cầu thêm negative matrix QD31-T1 trước ký không?

Comment GitHub **đề xuất, chưa đăng thay owner**:

> Review kỹ thuật QD-31 trên main f864034: ACCEPT trong contract hiện tại. Guard ASCII/128, batch preflight, ON CONFLICT/rollback, legacy409 non-retryable và owner/scope đã được đối chiếu; targeted PostgreSQL race/legacy/history cùng client entry PASS. Không có P0/P1 mới. Username Unicode không cấp được attempt mới, không được tự transliterate/migrate; xin A/C xác nhận chấp nhận giới hạn và quy trình tài khoản tương thích. Agent không xác nhận OWNER_APPROVED.

## 3. QD-32 — Monitoring history vượt5000

### 3.1 Contract hiện tại

Cả `events`, `interruptions`, `gaps`: PROCTOR được phân công vào attempt ACTIVE mới được đọc. Role/scope kiểm trước query lấy history. Query lấy tối đa5001; 0..5000 trả đủ200, 5001+ trả409 `INVALID_STATE`, `retryable:false`, message cố định, không có mảng lịch sử một phần. Không thêm ErrorCode hoặc pagination mới.

Client map409 của đúng ba endpoint thành `HistoryLimitException`; dashboard giữ dữ liệu đủ gần nhất và stale/error, không nhận prefix thiếu là đầy đủ. Client có trần8 MiB trước parse JSON, cùng row/parser validation. Lịch sử exam có contract quyền/pagination riêng; không suy từ QD-31 rằng proctor được đọc monitoring history của FINAL/CLOSED.

### 3.2 Source và methods đã kiểm

| Source | Methods / điều đã đối chiếu |
|---|---|
| [MonitoringHistoryLimit.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringHistoryLimit.java) | `MAX_ROWS=5000`, `QUERY_LIMIT=5001`, `requireComplete`: lỗi409 toàn response |
| [MonitoringEventService.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringEventService.java) | `timeline`/`lockAndAuthorize`: role, shared attempt lock, scope, query ORDER BY received_at,id LIMIT5001 rồi requireComplete |
| [JdbcPresenceStore.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/JdbcPresenceStore.java) | `interruptions`: role/lock/scope, ORDER BY presence_revision LIMIT5001 và requireComplete |
| [MonitoringGapService.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringGapService.java) | `timeline`: role/attempt lock/scope trước query history; ORDER BY received_at,id LIMIT5001 và requireComplete |
| [MonitoringTimelineController.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringTimelineController.java), [MonitoringPresenceController.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringPresenceController.java) | Chỉ dựng success DTO khi service trả đủ; exception handler không gửi mảng bị cắt |
| [ApiExceptionHandler.java](../../server/src/main/java/vn/edu/toeic/server/auth/ApiExceptionHandler.java), [JdbcAttemptScopeStore.java](../../server/src/main/java/vn/edu/toeic/server/monitoring/JdbcAttemptScopeStore.java) |409/INVALID_STATE/false retryable; scope phải ACTIVE, đúng assignment; query khóa/xác định quyền khác query history |
| [MonitoringApiClient.java](../../client/src/main/java/vn/edu/toeic/client/dashboard/MonitoringApiClient.java) | `history`, `get`, `LimitedBody.onNext`:409 mapping theo endpoint, không phản chiếu error body; cancel source future;8 MiB trước parse |
| [DashboardController.java](../../client/src/main/java/vn/edu/toeic/client/dashboard/DashboardController.java), [DashboardModel.java](../../client/src/main/java/vn/edu/toeic/client/dashboard/DashboardModel.java) | `fail`, `resync`, các `*Failed`: giữ dữ liệu/error/stale; không đổi status candidate thành UNKNOWN vì HTTP history lỗi |
| [ProctorDashboardView.java](../../client/src/main/java/vn/edu/toeic/client/dashboard/ProctorDashboardView.java), [MonitoringJson.java](../../client/src/main/java/vn/edu/toeic/client/dashboard/MonitoringJson.java) | Render ưu tiên error/stale, parser kiểm row count/identity; không hiển thị “đã đồng bộ” cho response thiếu |

### 3.3 Evidence mới và phạm vi kiểm chứng

| Yêu cầu | Evidence đã chạy trên main |
|---|---|
|0/1 row cả ba endpoint | `MonitoringHistoryBoundsPostgresTest.emptyAndSingleRecordHistoriesAreCompleteForAllThreeEndpoints` |
|5000 events/interruptions/gaps trả đủ và có thứ tự | Ba case `*ReturnsExactly5000InOrderAndRejects5001`: kiểm count và phần tử đầu/cuối, fixture khác attempt không trộn vào |
|5001 trả409/INVALID_STATE/false retryable, không success một phần | Cùng ba case: kiểm type ERROR, protocol/trace, fixed message, không có history field và không lộ SQL/table/internal exception |
|401/403 trước history, không lộ giữa role/proctor | `verifyAuthorizationBeforeHistory` của PG test được gọi trên empty và overflow: no token401, candidate403, foreign proctor403. Source xác nhận query history sau scope; test không gắn tracer SQL để khẳng định không có bất kỳ query lock/scope nào trước đó |
|Client mapping history409;403/401 giữ phân loại | `MonitoringApiClientTest.historyOverflow409IsNonRetryableLimitOnlyForHistoryEndpoints` và các parameterized error tests |
|Response lớn đầy đủ vẫn được nhận | `fullHistoryOf5000EventsAtMaximumFieldLengthsIsAcceptedInOrder`: HTTP fixture MOCK >4 MiB,5000rows đủ/thứ tự đúng |
|Trần body | `oversizedBodyRejectedBeforeRender`:8 MiB+1 bị từ chối trước render; source subscriber cancel trước chuyển chunk vượt cap cho parser |
|Dashboard giữ last-known/error/stale | `DashboardControllerTest.historyOverLimitShowsNonRetryableMessageAndKeepsLastCompleteList`; `gapHttpFailureKeepsLastKnownWithStaleLabelAndScopeRevocationClearsIt`; mã chung `fail("gaps",…)` được đối chiếu |

**Giới hạn evidence:** giới hạn body lớn được kiểm qua HTTP fixture của client, không phải một lượt production PostgreSQL→dashboard với5000row ở mọi trường max. Server5000/5001 chạy PG/HTTP thật, body ngắn hơn fixture max. Test controller409 trực tiếp cho events/interruptions, gaps có409 API test và stale/error test503 cùng nhánh source, chưa có test controller409 riêng. UI render được đọc source; không chạy GUI manual mới. Không suy ra kết quả load/latency hoặc mọi độ dài body từ166PASS.

### 3.4 Findings và giới hạn đã biết

| Mức / mã | Nhận xét | Ảnh hưởng và xử lý |
|---|---|---|
| P0/P1 | Không có phát hiện mới có evidence | Các biên/routing/error/integrity trong phạm vi review đều đúng contract |
| P2 · QD32-L1 | Lịch sử >5000 vẫn tồn tại trong DB nhưng cả endpoint từ chối; retry không làm ngắn lại; chưa có cursor API | A/C phải chấp nhận giới hạn vận hành này. Không xóa lịch sử hoặc tăng cap tùy tiện để mở dashboard; nhu cầu export/pagination là task riêng được owner cấp quyền |
| P2 · QD32-L2 | Client hiện map mọi409 từ ba history endpoint thành HistoryLimitException, không kiểm error.code/retryable của body | Hợp contract hiện tại vì chỉ có nghĩa409 này ở ba endpoint; nếu thêm nghĩa409 khác phải review client/contract đồng thời. Không tự mở rộng protocol ở đây |
| P2 · QD32-T1 | Matrix max-body end-to-end và controller409 riêng cho gaps chưa có lượt mới | Evidence đã tách rõ fixture/real PG; không dùng làm claim GUI/production acceptance |

### 3.5 Verdict, câu hỏi owner và comment đề xuất

**Technical verdict: ACCEPT** cho lựa chọn reject-whole-response vượt5000 và client giữ stale thay completeness giả. **Owner approval: PENDING**. Không có corrective production patch cần mở trong review này; không đề nghị pagination/mã lỗi mới như điều kiện ngầm để merge.

A cần xác nhận HTTP409/INVALID_STATE/false retryable, giới hạn không pagination và quy trình xử lý audit khi vượt5000. C cần xác nhận shared helper/ba service, dữ liệu không bị truncation và semantics last-known/stale; B cần biết giới hạn mapping409/body client. Owner có yêu cầu bổ sung QD32-T1 trước ký thì nêu rõ phạm vi riêng.

Comment GitHub **đề xuất, chưa đăng thay owner**:

> Review kỹ thuật QD-32 trên main f864034: ACCEPT trong contract hiện tại. PostgreSQL/HTTP thật kiểm0/1/5000/5001 cho events/interruptions/gaps PASS;5001 trả409 INVALID_STATE retryable:false và không có mảng một phần. Scope trước query history; client HistoryLimitException, body8 MiB và stale/last-known đã đối chiếu/test. Không có P0/P1 mới. Xin A/C xác nhận chấp nhận hard cap không pagination và quy trình khi cần audit >5000. Không ghi OWNER_APPROVED thay A/C.

## 4. Handoff cho A/C và phạm vi PR

1. A/C đọc findings/giới hạn/evidence theo source SHA, tự xác nhận hoặc yêu cầu thay đổi. Hai câu đề xuất để người thật ký (chưa được ký):
   - **A:** “Tôi chấp nhận HTTP/transaction của QD-31 và QD-32 cùng các giới hạn vận hành được nêu trong packet.”
   - **C:** “Tôi chấp nhận fail-closed ID và lịch sử không cắt bớt; evidence và các giới hạn kiểm chứng trong packet đáp ứng phạm vi review.”
2. A/C/B thống nhất người kiểm username và cấp tài khoản ASCII có đối soát trước kỳ thi; xử lý trường hợp cần đọc history vượt5000 qua quy trình được cấp quyền, không tự xóa dữ liệu/đổi contract.
3. Chỉ owner mới được cập nhật trạng thái QD/approval sau khi xác nhận thật. Review này không APPROVE/merge/close PR hoặc xác nhận LAN/human review.
4. PR packet là **docs/evidence-only từ main f864034**, chỉ thêm thư mục review mới; không chứa lại PR47/51/52 và không thay các file họ đang sửa. Raw logs/XML/cluster nằm trong ignored target; file công bố là bản dẫn xuất đã lọc, có checksum.
