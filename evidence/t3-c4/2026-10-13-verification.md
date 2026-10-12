# T3-C4 · Candidate DELTA thật và chuẩn bị live gate ·13/10/2026

## Git/source và trạng thái bàn giao

Base remote/main `ee714fcf8e02cc22cd91494790840f9d6532f914` (C3PR26), workspace ban đầu sạch. Branch `feat/t3-c4-monitoring-delta-live-gate`. Commit implementation/test/guide `2d867c27c4183993b9273851ed485f6aeccd49b0`; fix stdout readiness `d9475ebd20ccb1590cb234571f810896c5e31304`. Docs/evidence commit, PR/merge cuối xem PR/final; không đổi raw sourceSha thành SHA docs/merge.

Final runtime `T3C4-692e8c8a-0265-4211-afc6-151a5d5e6048` tại source **d9475eb**, 249source hashes khớp Git sau chuẩn hóa CRLF và khớp exact workspace bytes ở lúc chạy; [audit](source-audit.json). Source/code/scripts sạch; metadata ghi đúng docs/attributes/evidence đang dirty, không gọi toàn checkout sạch. Source trước fix stdout2d867 được giữ [audit riêng](source-audit-2d867c2.json).

- Client JAR SHA256: `96266b4c529a41d9138ffa0b51a3f371147b985e41cb59331b31d5c3b9a688d3`.
- Server JAR SHA256: `c1e14821db56409e02c08345688d724bb61e500f3d09937c48e1fe8f0111049f`.
- Không TEST LiveCandidateApp/DeltaLiveGateSmoke trong các JAR production. Helpers chỉ test-classes, PropertiesLauncher/child test classpath.
- FULL mặc định ở cả hai đầu. Server/client opt-in mới cho DELTA; merge code không bật gate và không thay human approval.
- `.env`, tracker, kế hoạch gốc, migrations, server main, luồng thi/Listening và PR A/B không đổi. Chỉ dọn owned children/Edge/profile/sockets/TEST schema, không kill user resources/reset DB. Final audit0C4schemas, public version7; các smoke chỉ migrate TEST schema lênV8.

## Topology/config thật

**Một Windows11 host build26200**, JDK Temurin21.0.10+7, Maven3.9.15, PostgreSQL18.6 đang có. Spring server loopback port riêng và schema UUID riêng; hai candidate JVM/Stage thật, proctor default Application/Stage thật. Đây không phải LAN hai máy. URL/port actual, Java, hashes/config trong [metadata](T3C4-692e8c8a-0265-4211-afc6-151a5d5e6048/metadata.json).

Normal candidateA opt-in DELTA và candidateB FULL dùng default production factory, REAL ProcessHandle/policy8tên; owned Edge headless có rootPID/start độc lập. Instrumented candidate thay khi restartA vẫn cùng production coordinator/collector/encoder/transport; chỉ TEST observer/source/base hooks gây fault. Proctor không collector. poll1000ms, refresh2000ms/full30000ms, stale6000ms giữ production; TTL12000ms TEST thay300000ms production, heartbeat/ACK250ms/max5attempts. Không dùng seed ngẫu nhiên; toggles deterministic, không giả clock giữa máy.

## Kết quả đã chạy

| Kiểm tra | Kết quả / evidence |
|---|---|
| Baseline main | Java569/package PASS; transcript baseline |
| Final source d9475eb | Java579,0failure/error/skipped/package PASS; [unit totals](unit-results.json), final-package transcript |
| C4 default Applications + live faults | **42 assertions PASS**, gồm Start/cleanup; [report](T3C4-692e8c8a-0265-4211-afc6-151a5d5e6048/local-report.json), không gọi42scenarios độc lập |
| C2 reducer | smoke-t3c2 PASS: real Spring/PG/HTTP/WS, MOCK readings/future-base SIMULATED |
| Full GUI baseline | smoke-t2c1 -Gui PASS: REAL ProcessHandle/owned Edge, proctor component, riêng fault sets MOCK |
| Event/gap/late GUI | smoke-t2c2 -Gui PASS: REAL HTTP/WS/PG/worker/proctor component, readings MOCK/network faults SIMULATED |
| Event delivery | smoke-c3 PASS: REAL owned Edge/PG/HTTP/WS/DB commit failure/gap; separate fake overflow readings |
| Presence | smoke-a4 final PASS sau stdout compatibility fix; separate owned JVM kill REAL, readings MOCK |
| Measurement | smoke-c4 PASS, COMPLETE3endpoints/26categories, readings MOCK/ACK observer suppression SIMULATED |
| Replay | smoke-t3c3 -RealScan PASS72positive/4detected negatives/repeat deterministic reports; REAL scan capture + SIMULATED delivery, không live network |
| Artifact/cleanup | exact archive/member checks, source249/JAR hashes, real credential absence, TEST helpers absent;0C4schemas/publicV7 |

Các regression t3c2/t2c1/t2c2/c3 chạy trên2d867; final d947 chỉ bỏ stdout mode line (không đổi network/state), rồi579/package, C4/A4/measurement/replay chạy lại trên d947. Mọi desktop smoke chạy tuần tự. [Initial regression results](t3c4-regression-results.json) giữ A4FAIL, [final rerun results](t3c4-after-results.json) giữ PASS riêng.

## Ma trận local bắt buộc

Precondition chung: TEST candidate ownersA/B ACTIVE, PROCTOR assignedboth, authenticated actual UI/HTTP/WS, serverdelta=true, A opt-in và capability current confirmed/B opt-in off; source/config/hashes như metadata. Mỗi row tên assertion/expected/phase/level nằm trong report, raw/wire/trace trong ZIP.

| Nhóm | Expected và assertion thực | Level / kết quả |
|---|---|---|
| Start/OPEN/full/ACK | default factoryA DELTA/B FULL, fresh scope/fullACK; proctor.monitoring=null | REAL Application/HTTP/WS/source PASS |
| Owned Edge mở/đóng | cả hai state có root; PID/start/name/quality bằng independent ProcessHandle; bảng proctor có/rồi mất root, lịch sử≥2collectors còn | REAL OS/state/push/GUI/PG PASS |
| Scan freshness / full | giữ owned Edge mở ít nhất31s monotonic, sequence tiến/SYNCED/HBONLINE và thêm FULL TX thật | REAL time/source PASS, unit riêng kiểm timer unchanged |
| ACK loss/retry | suppress ACK đã REALserver nhận, park source; retry whole envelope identical, sequence/revision/receivedAt không touch, oldACK không lùi ackSequence | REAL network/encoder + SIMULATED delivery-observer ACK drop PASS |
| RESYNC thực | tăngbase/seq một lần; server last-known UNSYNCED, adapter callback -> RESYNC_WAIT_FRESH; resumeREALscan/full/ACK/GUI SYNCED sameepoch, history giữ | REAL network/parser/callback/RAM/DB, base alteration SIMULATED PASS |
| Retry exhaustion |5attempts rồi WAIT_FRESH, resume -> OPEN newepoch/full1 | REAL delivery, all-ACK observer drop SIMULATED PASS |
| Candidate restart/isolation | kill ownedA JVM, UNKNOWN; B vẫn ONLINE/active; newAppA newcollector/epoch/full1, oldsocket payloadSTALE | REAL hard-kill/Applications/network; oldpayload SIMULATED PASS |
| Server restart | sameTESTport/serverinstance mới/epoch mới, bothreconnect/capability/full; DB event/gap còn | REAL Spring restart/PG/WS PASS |
| Capability loss | restartserverdeltaoff -> runningDELTA dừng; Stop/StartFULL; supportreturn không auto-switch; nextStartDELTA | REAL server/adapter/coordinator/UI PASS |
| Source hang/failure | source park -> push STALE6s/retainedprocesses/HBONLINE; exceptions -> TTL12s TEST tombstone/HBONLINE; resumefreshOPEN/full | REAL maintenance/push/HB; source control SIMULATED PASS |
| Role/scope/revoke | proctor write/othercandidate scopeFORBIDDEN; CLOSED attempt trước accepted-state duplicate coordinator stops; revokedtoken UNAUTH stops; assignment removed HTTPstate/events403, noA pushes whileB progresses | REAL auth/PG/network/UI; forged/duplicate trigger SIMULATED PASS |
| DB scope/commit | own table rename -> RETRYABLE_SERVER_ERROR/noACK/no freshness touch; own deferred event commit trigger ->rollback/pending/noevent, remove ->retry single frozen eventId/content/origin equalsSQL | REAL JDBC/transaction/network/Edge; own schema fault SIMULATED PASS |
| Cleanup | Application.stop owns worker count0, schema drop/serverstate RAM0, own root descendants/profile gone | REAL resource lifecycle PASS |

HTTP state equals latest push during paused quiet acknowledged state. Event identity/content retry expected is frozen wire payload + independent SQL rows, not reducer. Root process metadata expected comes from independent parent OS probe. C3raw oracle/replay preserves full/delta equivalent checkpoints and event IDs/content; real host background set is not frozen.

**Limits of unchanged case:** report's UNCHANGED_FRESH_30S counts actual new FULL after a≥31s observation interval; other background processes can vary, metadata changes can also cause FULL. It does not independently attribute every FULL to timer alone. Strict unchanged refresh/full30s timing is unit/C2 evidence. Exploratory first577run usedsequence+15 as proxy; that was weaker and is not used as proof of elapsed31s. Raw/report remains unmodified.

## Lỗi tìm được và provenance của reruns

1. **Malformed ACK enables delta:** old C3Jar `fb05e7d30b9be60c2eb06db21eaa7c37dc3efbbb98a7b4f2939e1e8858d19f3b`, MOCK correlated heartbeat ACK with numericconnectionId42: expectedfalse/actualtrue, exit1. Parser updated capability before verifying remainder. Fixed to publish immutable capability only after wholeACK valid. Unit negative/current socket/oldserver tests; same standalone [counterexample](CapabilityCounterexample.java) nowexpectedfalse/actualfalse/exit0 including finalJar. Before/after/final transcripts retained. No network proof claimed for MOCK input.
2. **Scoped revoke with hung source:** first live `T3C4-57713825-508d-4ea1-8322-00d7fcc00d22` failed ATTEMPT_REVOKE_DUPLICATE. Actual serverFORBIDDEN replies in fault-wire but source parked/no delivery state pending; coordinator remained active. Fix internal scoped-heartbeat rejection callback correlatedattempt/trace/collector/socketgeneration; transientDBexcluded. Unit stalecollector/socket negative, adapterwrongtrace/transient/late tests and actual reruns show coordinator stops. First dirty-source manifest/log kept. RerunPASS is supported by these specific tests, not guessed timing.
3. **PowerShell version capture:** second local command aborted before topology launch because stderr `java -version` became terminatingNativeCommandError under Stop. Captured version with .NETProcess redirected stderr; retained failed command transcript. It is a test-launcher issue, not DB/network failure.
4. **Stdout compatibility:** initialA4 regression failed Ownedchildready. Newly added MONITORING_MODE printed during Candidate.start, before TEST_CLIENT_READY; A4ready expects first line. Removed mode stdout in d947; UI retains mode+reason. A4 rerunPASS through kill/recovery/revoke/DB/cleanup. No A production/harness/PR edits, no timing assumption; failed transcript retained.

Runs dirty-before callbackfixFAIL, source577/third579PASS, committed2d867PASS and finald947PASS all retained with own metadata/raw/screenshots. They are not substituted for each other.

## Measurement/trace, screenshots và integrity

Final **12 measurement endpoints/140categories overall INCOMPLETE**, exactly one hard-killed normalcandidateA writer lacksFINAL. Define healthy cohort in advance as allserver/proctor + FULLcandidate + instrumentedcandidate + disabledfallback, excluding only that deliberately killed child: **11endpoints/128categories COMPLETE**, no changed raw. Full raw retained, not rewritten toCOMPLETE. Healthy summaries in finalZIP; counters are WS JSON application-byte bookkeeping, not latency/savings/CPU/memory/E1/E2 conclusions. Hard-killed observationtrace lacksEND likewise INCOMPLETE.

Screenshot review performed for actual DELTA, disabledserverFULLfallback and proctorEdge/resync state; visible labels/buttons, safe mode reasons, stale T1 login message replaced. Candidate sceneheight600 avoids clipping Stop/logout; no new config screen. [DELTA](T3C4-692e8c8a-0265-4211-afc6-151a5d5e6048/candidate-delta-default/candidate-delta.png), [FULLfallback](T3C4-692e8c8a-0265-4211-afc6-151a5d5e6048/candidate-opt-in-unsupported/candidate-full-fallback.png), [recovered proctor](T3C4-692e8c8a-0265-4211-afc6-151a5d5e6048/proctor-resync-recovered.png).

Each ZIP preserves exact originalUTF8raw/metadata/trace/PNG. [Archive/member manifest](archive-manifest.json), SHA256SUMS and `python evidence/t3-c4/verify.py` verify retained bytes. [Transcript provenance](transcripts/provenance.json): originals PowerShellUTF16 BOM decoded source-aware toUTF8/LF for reading; original andderivedhash kept. ExistingU+FFFD decoration in earliest logs retained and counted, not reconstructed. New final JVM stdout UTF8. No realDB/seedpassword/token in retained artifact payloads; `.env` not copied.

## Gate / NOT RUN / bàn giao

**Gate NOT_DECIDED / acceptance PARTIAL.** Đây là preparation12–13/10, không giả gate20/10 đã diễn ra. Không có máy Windows thứ hai/URL môi trường từ người dùng; hai candidate windows/JVM một máy không là LAN. Missing environment/humanB review không là delta failed. QD19 liên kết QD10presence nguyên trạng, FULLdefault.

NOT RUN: LAN hai máy/humanB approval/humanA review, physicalpacketloss, OSforcedPIDreuse/live allmetadata transitions, full exam/Listening/IT01/fullIT02–IT04/C5/E1/E2. Unit/replay/matrixlocal có cấp riêng, không nâng toànprototypePASS. A3 hooks auth/epoch/scope/restart dùng thật nhưng không tự đánh dấu T3-A3DONE. ChatGPT planning/review NOT RUN theo opt-out của người dùng.

A review scopedHBnotification/authbinding trướcduplicate/maintenance; B review factory/mode/parser/generation/base/ACK/RESYNC/UX và chạy LAN checklist. [Guide/config/fallback](../../docs/MONITORING_DELTA_LIVE_GATE.md) có lệnh server/client với URL cấu hình, demo full/delta và quay vềFULL. Code merge với deltaoff khi không còn knowntechnical/GitHubblocker không là gateapproval. Dừng sau T3-C4.
