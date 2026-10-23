# Stage 3 ? cross-review k? thu?t v? packet ch? owner

Ng?y 18/10/2026. Main fetch/review: `f86403489bdd01887b926f2f9e4b72ddf05d1423`.
Branch `review/stage3-cross-review-20261018`; worktree ri?ng ngo?i m?i `target/` c?a workspace chung.
**Ch?a ?? sign-off Ch?ng 3: QD-33 REQUEST_CHANGES; l?i CLOSE/correlation tr?n main c?n PR47.**
??y l? review c?a agent ?? owner x?c nh?n, kh?ng ph?i ch? k? A/B/C. FULL m?c ??nh; kh?ng quy?t gate DELTA, release ho?c c?p nh?t tracker.

## Ph?m vi v? ph?n c?ng th?c t?

[01_KE_HOACH](../../Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md), d?ng 128/134/140 v? h??ng d?n [A](../../docs/thanh-vien/VAI_A.md), [B](../../docs/thanh-vien/VAI_B.md), [C](../../docs/thanh-vien/VAI_C.md):

| Task | C?ng vi?c c?a owner | Ng??i x?c nh?n ghi ch? review | Technical verdict phi?n n?y |
|---|---|---|---|
| T3-AR | A review ph?n B | C | REQUEST_CHANGES: cache publication P1; lifecycle ?? ki?m subset, MSI/m?y2/nghe th?t ch?a ki?m m?i |
| T3-BR | B review ph?n C | A | REQUEST_CHANGES_ON_MAIN: CLOSE/correlation P1 ?? c? candidate PR47; reducer/fallback/maintenance subset PASS |
| T3-CR | C review ph?n A | B | ACCEPT_WITH_LIMITATIONS trong contract hi?n t?i; QD34 c?n A/B x?c nh?n, QD31/32 k? th?a PR53, l?i nh?n protocol trong report c?n A ??nh ch?nh |

Hai gi?/task v? c?c checkpoint t?ch h?p l? y?u c?u k? ho?ch, kh?ng ???c t? ghi ?? ho?n t?t ho?c t? khai gi? ng??i th?t. Nh?t k?/test/handoff c?a phi?n n?m trong th? m?c m?i n?y ?? tr?nh s?a t?i li?u chung ho?c file PR47/51/52/53 ?ang s? h?u.

## Review A ? backend v? d? li?u (??u v?o cho T3-CR)

| Ch? ?? | Code/method ?? ??i chi?u | Evidence m?i v? gi?i h?n |
|---|---|---|
| HTTP/WS auth/scope | BearerAuthenticationFilter.doFilterInternal; AuthorizationService.requireRole/requireAttempt/requireExamRead; RealtimeWebSocketHandler.handleTextMessage | Network tests: real loopback, stores MOCK; PG audio/exam ki?m quy?n production th?t. WS revalidates m?i message, quy?n tr??c business/dedup |
| Save/submit/takeover | ExamService.autosaveAnswers/submitExam/timeoutAttempt/takeoverWriter; ExamController.takeoverWriter | Row lock chung; writer/deadline sau lock; clock_timestamp; transaction k?t th?c tr??c response. 8 PG exam cases g?m final read-scope, lost-ACK replay, concurrent entry, save/submit/timeout; kh?ng g?i l? to?n AT01?AT11 |
| Listening/READY | AudioService.ready/start; ListeningService.event; RealtimeSessionRegistry | 4 Audio +4 Listening PG cases: checksum/READY, writer invalidation, role/scope, commit failure kh?ng ACK/start push, takeover kh?ng resume; media/hearing/fault l? MOCK/SIMULATED |
| T3-A3 hooks | MonitoringStateService/MonitoringStateMaintenance; WS handler; [contract](../../docs/MONITORING_SERVER_HOOKS.md) | Fresh service/restart-epoch/maintenance cases. ACK state x?c nh?n RAM, kh?c ACK event/gap/presence sau DB commit. Kh?ng ch?y l?i smoke A3/restart Spring to?n lu?ng; d?ng historical evidence c? ghi SHA ri?ng |
| History | QD32 t?i [PR53](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/53) | K? th?a technical ACCEPT; kh?ng review l?i ho?c ch?y l?i 166 case. >5000 kh?ng c? pagination, owner A/C PENDING |
| B?o c?o A5 | [report](../../docs/T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md), b?ng framework | S3-P2-05: d?ng 36 ghi envelope v1 nh?ng Protocol.VERSION l? v0; ??nh ch?nh v?n b?n, kh?ng ??i contract |

## Review B ? client/Windows (??u v?o cho T3-AR)

- CandidateExamView.synchronizeAudio/close d?ng m?t ExamSession writer, ??i writer ??ng audio c?; close d?ng clock/audio/session/subscriptions. ToeicClientApplication.stop ??ng client/view; m?ng/file c?a AudioPreparation ? worker, render qua Platform.runLater/generation guard. ??y l? source review v? unit lifecycle, ch?a GUI m?i.
- ExamEntry.claimWriter v? ExamSession.reclaimWriter kh?ng schedule replay takeover; autosave/submit retry d?ng payload ?? ??ng b?ng. S?u case ExamSession ki?m stale writer/final/close/Reading v?n gi? edits khi Listening b? kh?a; entry lost-ACK m?t case; real HTTP lost-response probe ri?ng.
- AudioFiles policy/root/junction/8.3 v? directory-creation stress PASS tr?n Windows th?t; file publication ??ng th?i FAIL b?n batch. Chi ti?t v? P1 t?i [QD33_QD34_REVIEW](QD33_QD34_REVIEW.md).
- AudioPreparationView.cacheRoot m?c ??nh LOCALAPPDATA/user.home; AudioFiles/partial/speaker/journal ? user data. Override root l? c?u h?nh operator, kh?ng c? b?o ??m m?i override ??u n?m ngo?i install directory.
- MSI: ch? ??i chi?u source package-windows.ps1/Windows installer v? [stabilization evidence](../stabilize-20261017/REPORT.md). Kh?ng build/c?i/g? MSI m?i; artifact c? kh?ng t?m th?y t?i workspace trong audit tr??c. Kh?ng d?ng evidence historical l?m PASS m?i ho?c nh?n app-image l? installer/m?y hai.
- FULL default v? fallback opt-in ???c ki?m m?i b?ng CandidateModeSessionTest; kh?ng b?t DELTA m?c ??nh ho?c t? retry/resume Listening.

## Review C ? monitoring/network (??u v?o cho T3-BR)

- MonitoringStateReducer.full/delta gi? sequence chung, bound collector/syncEpoch, duplicate digest, base thi?u ? RESYNC/gi? last-known; input sai kh?ng mutation m?t ph?n. 6 reducer +16 delta-delivery cases m?i ki?m ACK-base, ACK loss/frozen retry, reconnect/old callbacks, scan failure, fresh scan/full recovery v? scope rejection.
- MonitoringDelivery event/gap queue v?n t?ch current state; source ??i chi?u c?ng CandidateMonitoringSession, FULL/DELTA d?ng c?ng observations/event stream. Case mode default/shared stream/role/fallback ?? ch?y; kh?ng rerun to?n event-queue campaign ho?c MT02?MT06/live LAN trong phi?n n?y.
- Presence li?n l?c kh?c state freshness: heartbeat kh?ng l?m process m?i h?n. Maintenance autonomous STALE/TTL c? worker ri?ng v? shutdown; service/maintenance fresh tests kh?ng thay real desktop/LAN.
- C1 cleanup: SmokeCleanup 3 unit PASS, l?i t?ng b??c ???c gi? v? kh?ng che primary failure. FullSnapshotSmoke.cleanupProfile h?n 45s ch? l? mitigation historical, kernel/AV ch?a ???c ch?ng minh; kh?ng ch?y Edge/GUI ho?c r?t m?ng ?? x?c nh?n l?i nguy?n nh?n.
- E1: 12 Python fixture tests m?i PASS, c? weighted CPU/coverage/counters/checksum/binding/duplicate-run/negative latency. Kh?ng t?o campaign/raw thay th?; raw E1 l?ch s? ch?a t?m th?y hi?n t?i, n?n ch?a ki?m l?i 84 hash ho?c reanalysis. [Methodology](../../docs/E1_T4C1_MEASUREMENT.md) v? historical report ??c theo ng?y/SHA, kh?ng ??i ch? k? B/C.

## PR47 ? hai production file, ch?a merge

PR47 OPEN, base main, HEAD `3044e5bc963f7ac9d8cdc4d8410696c301080534`; source fix `3d93820`.
Diff production so main ??ng hai file:

| File/method | ?i?u c?n review | Owner |
|---|---|---|
| FullSnapshotDelivery.close/worker termination | workerStopped kh?c stopped; best-effort CLOSE ch? t?i ?a ackTimeout; copy future, guard h?y write ?? x?p h?ng, forget correlation v? d?ng worker; generation/socket/epoch kh?ng ??i contract | C |
| RealtimeClient.forgetPending/receive/send/disconnect | bounded insertion-ordered tombstone, retired ACK/ERROR/RESYNC l?p b? b? ??n retry c?ng ID; retry x?a tombstone, disconnect x?a cache; guard connection generation v? UNAUTHORIZED to?n phi?n v?n c?n B ??i chi?u | B; C xem ranh gi?i delivery |

Tr?n archive PR47 ??c l?p: **7 regression PASS**. Tr?n archive main production + ??ng PR47 test source: **3 expected FAIL** (2 stalled CLOSE,1 repeated retired correlation: CONNECTED th?nh FAILED). Kh?ng ch?p fix v?o nh?nh review; production main v? branch PR47 gi? nguy?n.

Case `forgottenCorrelationIgnoresRepeatedAckErrorAndResyncUntilRetried` ?? ch?y c? retired correlated UNAUTHORIZED (b? qua) v? ERROR UNAUTHORIZED to?n phi?n kh?ng requestId (chuy?n FAILED). ??y l? hai ranh gi?i kh?c nhau, kh?ng nh?n m?i l?i auth c? l? revoke phi?n hi?n t?i.

**READY_FOR_C_AND_B_REVIEW**, ch?a OWNER_APPROVED. PR47 gi?i quy?t S3-P1-02 nh?ng ch?a n?m main; technical review kh?ng t? merge ho?c nghi?m thu LAN/DELTA gate. Kh?ng d?ng k?t qu? source3044e5b g?n cho mainf864034.

## Findings v? gi?i h?n

| ID | M?c | File/method | Finding/evidence | Owner/vi?c ti?p theo |
|---|---|---|---|---|
| S3-P1-01 | P1 | AudioFiles.download L97; file L43; verify L108 | Confirmed existing QD33-P1: 467 l?i/1280 counted stress operations tr?n 4 batch; replace kh?ng ph?i h?p reader/writer, c? th? ch?n prepare/READY | B: corrective PR ri?ng v?i regression tr??c/sau; ch?a k? QD33 |
| S3-P1-02 | P1, ?? c? PR s?a | FullSnapshotDelivery.close; RealtimeClient.forgetPending/receive | Main thi?u bounded/guarded CLOSE v? retired-correlation cache; 3 regression FAIL tr?n main, PR47 7 PASS | C/B review PR47; ch? owner quy?t merge |
| S3-P2-01 | P2 | AudioFiles.safeDirectory/download/verify | Residual validate-then-use TOCTOU; kh?ng sandbox ??i ph? ti?n tr?nh ??c h?i c?ng user; kh?ng fsync/whole-manifest transaction | B x?c nh?n threat model, t?ch kh?i P1 concurrency h?p l? |
| S3-P2-02 | P2, trade-off c? ch? ??ch | ExamService.takeoverWriter; ExamEntry.claimWriter; ExamSession.reclaimWriter | Kh?ng dedup requestId; user reopen c? th? thay writer th?m l?n n?a; edits ch?a ACK c? th? b? b? khi adopt server | A/B x?c nh?n QD34, kh?ng th?m schema/idempotency trong review |
| S3-P2-03 | P2, gi?i h?n evidence | FullSnapshotSmoke.cleanupProfile; SmokeCleanup.finish | 45s mitigation ch?a x?c nh?n root cause file lock; unit cleanup kh?ng thay Edge/manual m?i | C ghi r? mitigation, c?n ki?m l?i n?u l?i t?i di?n |
| S3-P2-04 | P2, gi?i h?n ki?m ch?ng | E1 analyzer/review + docs E1_T4C1_MEASUREMENT | Fixtures PASS nh?ng raw campaign l?ch s? ch?a t?m th?y, kh?ng x?c nh?n l?i hash/CPU k?t lu?n l?ch s? | B/C x?c ??nh kho raw tr??c sign-off ph?p ?o; kh?ng t?o s? thay th? |
| S3-P2-05 | P2, t?i li?u | T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT L36; Protocol.VERSION | Report ghi envelope v1, source th?c l? v0 | A s?a nh?n t?i li?u ? l??t ri?ng; C/B x?c nh?n kh?ng ??i wire contract |

Kh?ng ph?t hi?n P0 trong ph?m vi ?? ki?m. P1 n?u tr?n ??u l? l?i ?? ???c packet/PR tr??c ghi nh?n, phi?n n?y t?i l?p ??c l?p; kh?ng nh?n l? ph?t hi?n ??u ti?n. Kh?ng m? r?ng k?t lu?n sang m?i interleaving, security threat ho?c t?nh ??ng to?n h? th?ng.

## Evidence m?i, historical v? vi?c ch?t

123 positive JUnit executions: **122 PASS /1 SKIP**; 16 PG cases l? subset. Ri?ng baseline diagnostic: **3 FAIL c? ch? ??ch**, kh?ng c?ng v?o PASS. Python **12 PASS**, HTTP probe **1 PASS**, publication **4 FAIL**. Kh?ng th? g?i phi?n review l? all-green ch? v? Maven targeted positive tr?0.
[Case results](test-results.json), [publication](publication-results.json), [commands](TEST_COMMANDS.md), [source manifest](source-manifest.json), [cleanup](environment-cleanup.json).
Raw log/XML/private source snapshots gi? ignored target/stage3-review-runtime; kh?ng publish credential, token, DB port, user path ho?c raw benchmark.

Kh?ng ch?y full Maven suite/package m?i; QD31/32 technical ACCEPT k? th?a PR53, s?166 ch? historical. Kh?ng ??i QUYET_DINH/PROTOCOL/KIEM_THU/NHAT_KY/TIEN_DO/VAI*/tracker ho?c code; kh?ng ch?m c?c PR ngu?n. T?t c? owner sign-off PENDING trong [matrix](OWNER_SIGNOFF_MATRIX.md). Deadline Ch?ng3 theo task giao20/10; kh?ng s?a k? ho?ch g?c.
