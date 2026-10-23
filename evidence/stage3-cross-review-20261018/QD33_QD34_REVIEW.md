# QD-33 v? QD-34 ? review t?i main f864034

Ng?y18/10/2026; m?i ch? k? owner PENDING. K?t qu? m?i t?ch kh?i [stabilization17/10](../stabilize-20261017/REPORT.md) v? packet tr??c t?i `e8acc44880a38bb18595aefb1488dc2443afa185`.

## QD-33: REQUEST_CHANGES

### Policy v? ph?n ??ng ?? ki?m

[AudioFiles](../../client/src/main/java/vn/edu/toeic/client/audio/AudioFiles.java): root normalize absolute, attempt UTF8 SHA256 + manifestId + t?n file ?? validate. Kh?ng ki?m ancestor tr?n configured root; root v? child ph?i directory NOFOLLOW, kh?ng symbolic-link/isOther/reparse, canonical child kh?p real root. Files.createDirectory con catch FileAlreadyExistsException r?i ki?m entry m?i; kh?ng tin entry ch? v? caller kh?c t?o n?.

Windows th?t: ancestor junction v? ancestor8.3 PASS; root/child junction b? ch?n; root b? ??i th?nh junction sau use b? ch?n; 128 directory readers v?640 mixed file/verify operations trong unit tests PASS. Symlink creation SKIP v? privilege, kh?ng m? ph?ng PASS ho?c ??i Developer Mode/ACL.

AudioFiles.validate/validateFile ki?m scope, duplicate case-insensitive, resource caps v? device/path names. Download stream ri?ng `.part`, ??ng stream, ki?m byte/SHA tr??c publish; corrupt/truncated/oversized/cancel kh?ng thay cache h?p l? v? partial ???c d?n. verify ??c byte th?c. AudioPreparation.reportReady ki?m l?i to?n manifest v? ACK request/attempt/writer/manifest; l?i local kh?ng th?nh READY. AudioApiClient ??ng stream khi close/deadline, worker r?i FX thread. M?c ??nh cache ? user data, kh?ng l?y install directory; override root v?n do operator ch?u tr?ch nhi?m.

### S3-P1-01: atomic publication ch?a ???c b?o ??m

AudioFiles.download L97 d?ng `Files.move(partial,target,REPLACE_EXISTING)`; ch?a ATOMIC_MOVE ho?c coordination reader/writer. Tr?n JDK21.0.10 WindowsFileCopy.move nh?nh non-atomic x?a target tr??c MoveFileEx; source entry hash trong source manifest. ??y l? gi?i th?ch t? source JDK, k?t h?p l?i race th?c, kh?ng ph?i suy ra ch? t? t?n exception.

| L??t m?i | Mode | Counted operations | OK | L?i | Final SHA h?p l? / partial |
|---|---|---:|---:|---:|---|
| 1 | 8 download workers |320|156|164|5/5;0|
| 1 | 4 download +4 verify workers |320|247|73|5/5;0|
| 2, c?y m?i | 8 download workers |320|155|165|5/5;0|
| 2, c?y m?i | 4 download +4 verify workers |320|255|65|5/5;0|

M?i mode5round?8worker?8operation; seed/final verify kh?ng n?m trong320. Payload128KiB Random(33) MOCK; filesystem Windows REAL; kh?ng inject corruption, malicious process ho?c filesystem failure. L?i t?i download:97/file:43/verify:108 g?m AccessDenied/FileAlreadyExists/NoSuchFile. Collector exit0 ch? ?? thu ?? JSON, outcome **FAIL** v? error>0.

?nh h??ng x?c nh?n: cache preparation/download/verification h?p l? c? th? th?t b?i v? ch?n READY. Ch?a ch?ng minh corrupted file ???c READY, ghi v??t root, m?t answers ho?c m?t file cu?i; kh?ng g?i l? P0. T?n su?t stress kh?ng ??i di?n production.

[Files.move Java21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)): REPLACE_EXISTING m?t m?nh kh?ng cam k?t atomic move. Vi?c m?i file d?ng partial ri?ng kh?ng ch?ng minh reader th?y stable publication. C?n corrective PR c?a B: regression concurrent publication/reader, ki?m c?c instance/process d?ng chung cache; publication/coordination/fallback r? tr?n filesystem h? tr?, valid-existing reuse v? error contract; gi? directory race/junction policy. Kh?ng kh?ng ??nh th?m m?t flag ??n l? ho?c synchronized t?ng instance l? ?? cho m?i sharing case. Kh?ng s?a production trong packet n?y.

### PR45 v? residual TOCTOU

PR45 HEAD `a652482` ch? AudioFiles + workflow. Original Windows CI commit `c1e3405` v? main `d562581` c?ng patch-id `475510ffc80bb21224678f590831197431cef613`; main workflow d?ng windows-2025/Java21/Maven test, c? th?m stabilize push trigger. Main AudioFiles c? policy ancestor h?p l? v? catch directory race PR45 thi?u. Merge l?i PR45 c? th? b? regression fix; kh?ng ch?a P1 publication v? ?o?n move kh?ng ???c s?a ? PR45.

**PR45 superseded v? patch/provenance: YES. QD33 technically accepted: NO.** Hai k?t lu?n ??c l?p. B v?n c?n quy?t ??nh disposition PR45 v? s?a P1 b?ng PR ri?ng; phi?n n?y kh?ng ??ng/merge PR45. CI historical SUCCESS kh?ng thay local concurrency stress ho?c opt-in PG/hearing tests; ch?a t?o CI success m?i tr??c publish.

Residual TOCTOU: check directory r?i create/open/move c? kho?ng th?i gian ?? ti?n tr?nh ??c h?i c?ng user ??i c?y. Kh?ng c? descriptor-relative sandbox/no-follow xuy?n m?i operation, fsync ho?c whole-manifest crash transaction. Boundary n?y ngo?i threat model ?? ?n hi?n t?i; B ph?i ??c v? x?c nh?n. **Nhi?u reader/download h?p l? kh?ng n?m ngo?i threat model**, n?n kh?ng d?ng TOCTOU disclaimer ?? h? P1 th?nh P2.

## QD-34: ACCEPT_WITH_LIMITATIONS

[ExamService.takeoverWriter](../../server/src/main/java/vn/edu/toeic/server/exam/ExamService.java) L1016+: roleCANDIDATE/shape tr??c SELECT FOR UPDATE; ownership r?i ACTIVE/monitorable/exam tr??c mutation; clock_timestamp sau lock; ch? initialize deadline null, deadline ?? c? kh?ng k?o d?i. UPDATE writer_epoch+1 v? ??nh interrupted cho playback ch?a complete; kh?ng UPDATE answers/savedRevision. Transaction proxy commit tr??c controller tr?200; final tr?ng th?i ch?n tr??c epoch mutation. Kh?ng lookup/dedup requestId.

`writerEpoch` l? quy?n ghi ??p ?n l?u PostgreSQL; `syncEpoch` l? phi?n RAM OPEN/FULL/DELTA c?a monitoring. Reconnect WS/RESYNC kh?ng t? c?p writer ho?c ??i deadline; takeover kh?ng ph?i monitoring OPEN.

### Evidence m?i

- Required PG test `takeoverReplayAfterLostAckAdvancesEpochButNeverLosesAnswersDeadlineOrFinal` PASS: replay c?ng request sau committed response gi? l?p m?tACK ? epoch th?m1, deadline/answers/revision gi?, writer c?409STALE, writer m?i save/submit ???c, replay sauSUBMITTED409INVALID_STATE v? final b?t bi?n.
- Concurrent entry/timeout/row-lock v? final read-scope c?ng PASS. Autonomous timeout case x?c nh?n takeover sauTIMED_OUT409; kh?ng d?ng direct invocation timeout ?? gi? scheduler.
- Required client case `lostTakeoverAckIsNeverRetriedAutomaticallyAndReopenUsesAFreshRequestId` PASS v?i gatewayMOCK. ExamEntry.claimWriter ch? m?t takeover, IOException k?t th?c; m? l?i t?o UUID m?i. INVALID_STATE ??c status m?t l?n; kh?ng l?pPOST. ExamSession.reclaimWriter l? thao t?c ch? ??ng, ch?n in-flight, nh?n authoritative answers/revision/deadline; user edits ch?a ACK c? th? b? b? theo th?ng b?o.
- Production ExamApiClient HTTP probe: server loopback ??c POST r?i ??ng response; **1 request** sau ambiguous response, **2 request** sau explicit call m?i. NetworkREAL; servercommitMOCK/faultSIMULATED; kh?ng d?ng thay PG commit test.
- CandidateExamView d?ng m?t ExamSession writer cho Reading/Listening; AudioPreparation.prepare(sharedWriter) kh?ng takeover th?m. Listening PG ki?m takeover kh?ng resume; view/media/hearing manual NOT RUN.

[JDK networking properties](https://docs.oracle.com/en/java/javase/21/core/java-networking.html): default enableAllMethodRetry=false. Claim ki?m ???c l? kh?ng replay POST ?? ??c/m?t response trong c?u h?nh ?? th?; kh?ng tuy?n b? JDK tuy?t ??i kh?ng retry connection ho?c m?i operator override ??u an to?n.

### Verdict/trade-off

Kh?ng c? correctness blocker m?i c?a QD34 trong tests ?? ch?y: kh?ng m?t answers ?? ACK, kh?ng reset deadline/revision ho?c m? b?i final. Kh?ng exactly-once: repeated takeover c? th? v? hi?u writer ?ang h?p l? th?m l?n n?a; unACKed local edits kh?ng ???c cam k?t gi?. A/B ph?i ch?p nh?n semantics/UX, kh?ng g?i API idempotent. N?u c?n idempotency durable, ?? l? schema/contract/retention/auth-before-cache task ri?ng, kh?ng tri?n khai trong review.

## K?t qu? v? ?i?u ki?n owner

123 positive JUnit:122PASS/1SKIP (16PGsubset); baseline PR47 negative3FAIL t?ch ri?ng; Python12PASS; HTTP1PASS; publication4FAIL. [case-by-case](test-results.json), [commands](TEST_COMMANDS.md), [harness](REPRODUCERS.md), [environment](environment-cleanup.json).

B: QD33 ch?a ?? ?? k? ACCEPT; c?n corrective PR tr??c/sau, ??ng th?i x?c nh?n policy/TOCTOU/superseded45.
A+B: QD34 ?? packet ?? ??c v? quy?t ??nh ACCEPT_WITH_LIMITATIONS ho?c REQUEST_CHANGES; tr?ng th?i owner v?n PENDING.
