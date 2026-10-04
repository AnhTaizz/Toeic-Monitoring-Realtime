# T1-C3 verification — 2026-10-04

Runner: Codex Agent, local Windows11 amd64, Temurin21.0.10+7, Maven3.9.15, PostgreSQL18.6 (Docker). Timezone UTC+7; smoke JVM uses UTC. No human review or GUI acceptance is inferred from headless tests.

Base: `74d2704328a794a24509d43c2f88702d63965511` (main PR5).
Branch: `feat/t1-c3-monitoring-event-delivery`.
Client source/tests: `a6b0e188dcf75e0bac92c27520a7ee75796b4df4`.
Complete Java/script source/tests: `35d56eb1554d18f51ef9746be3e593896d7f219a`.
Documentation/evidence commit and final push/PR/merge IDs are in the final report; no future merge is claimed here.

## Commands actually executed

Baseline and final reactor builds used the local Maven cache, offline:

```text
mvn test -o -B -ntp -Dmaven.repo.local=<local-maven-cache>
mvn package -o -B -ntp -Dmaven.repo.local=<local-maven-cache>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c3.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a3.ps1
```

Baseline test finished19:04:53 UTC+7: PASS200, server76/client123/spike1. Final test finished19:37:13 and package19:38:26 UTC+7: PASS253/253 each, client161/server91/spike1, no failures/errors/skips, five reactor modules successful. C3 adds53 cases: MonitoringDeliveryTest30, ScopeLookupTest7, RealtimeClientTest1, MonitoringGapTest15. Counts come from each final Maven module summary, not stale XML test reports left in target.

Logs: [baseline](baseline-test.txt), [test](mvn-test.txt), [package](mvn-package.txt), [real smoke](real-smoke.txt), [A3 regression](a3-regression.txt), [security/packaging](security-scan.txt). BOM/UTF16 redirected output decoded, UTF8/LF stored; workspace/user-home/JDK paths replaced. Logs retain actual runtime labels, not invented GUI screenshots.

## MOCK unit acceptance

- Identity/session/PID/start, initial set, ten stable polls, disappearance/reappearance, metadataQuality change, nullable start→known conservative new event, scan failure retaining baseline.
- Wall Clock versus monotonic observationNanos; frozen microsecond time, ID/request/trace/payload, defensive JsonObject copy, write success retaining pending.
- Register-before-send early ACK; six incorrect correlation fields; duplicate/late ACK; stable retry payload; timeout/backoff/budget, explicit manual retry, reconnect not resetting budget.
- CONFLICT/INVALID_INPUT retain failed; FORBIDDEN/UNAUTHORIZED stop authorized activity; another event can progress past a failed one.
- Capacity includes all pending states; small in-flight; drop-new baseline update; frozen gap and next accumulator; old collector drops cannot be retagged; bounded baseline and adapter slots/heartbeat reservation.
- Latch-controlled blocked scan: stop returns without waiting for network/scan, restart rejected until old worker termination. Late old failed write/ACK cannot mutate a new delivery, subscriptions removed, empty candidate scope and proctor never scan.
- Scope HTTP fixture validation/error handling uses real local HTTP with MOCK response; this is not server assignment acceptance by itself.

## REAL integration acceptance

Production Spring, migration V2→V3, PostgreSQL, HTTP login/me, production B2 transport/C3 delivery/C2 Windows ProcessHandle source: PASS. Own schema `c3_test_UUID` created and dropped; TEST candidate/proctor ACTIVE/foreign/CLOSED/empty assignments only. No shared database reset, PostgreSQL stop, production autoseed attempt or .env modification.

Owned headless Microsoft Edge/profile opened and closed; real snapshots reported its filename/start/PID, event committed and exact ACK drained queue. Multiple real polls and retry kept target PID at one row and one assigned MONITOR_WARNING; unassigned proctor got none. **First event ACK loss SIMULATED** only in the harness observer boundary; actual production server still committed and sent its normal ACK. Offline assigned proctor recovered event using real REST timeline. WS probe receiving a warning is not a B3 GUI warning.

Owned Spring server stop/restart triggered real B2 reconnect; queued **MOCK snapshot** event remained in the same delivery and later committed once. Socket reconnect belongs to B2, no duplicate collector/listener/reconnect loop from C3; shared PostgreSQL kept running.

Queue capacity1, **MOCK snapshots** deliberately produced3 dropped events. REAL gap WS/validation/transaction persisted2 gap rows with total dropped_count3 (frozen gap then next accumulator). A deferred PostgreSQL trigger in the owned TEST schema caused a real COMMIT failure: row0, retryableERROR, gap still pending, no successACK. After dropping that TEST trigger, bounded retry succeeded. **First gap ACK loss SIMULATED** at observer boundary; stable retry IDs/payload yielded no duplicate rows. Heartbeat ACK count progressed while delivery was active. Same gap ID with changed payload gotCONFLICT; foreign/CLOSED/unknown attempts and proctor gap submission gotFORBIDDEN, rows unchanged. Gap never updates process/presence state and has no MONITOR_WARNING or new REST timeline endpoint.

Collector/delivery/realtime workers terminated after lifecycle cleanup: PASS. Harness waits for owned worker names to disappear; old subscription callbacks are removed. RAM data is deliberately discarded on stop/logout/app close, with counts; no queue survival after app kill is promised.

A3 REAL regression after new handler/migration: PASS cleanV1/V2 and upgrade, auth/scope/revocation, commit-before-ACK, concurrent duplicate row1/warning1, conflict/privacy, deferred COMMIT rollback/error, offline timeline/order/isolation. No weakening of A2/A3 scope semantics.

DEV fixture script tested against separate `c3_demo_test_UUID` schema built from V1/V2/V3: CreatePASS, duplicate Create expected refusalPASS, CleanupPASS twice, monitoring_attempts0 and original user_accounts2 retained. Schema removed afterwards; public schema untouched. This verification did not create a development assignment on the user's public schema. For a GUI demo, explicitly run scripts/demo-c3.ps1 as documented in README.

Production JAR exclusion and security scan: PASS. Client/server JARs exclude own test/harness classes; test-scope client dependency is absent from server BOOT-INF/lib. Final changed files contain no actual local DB/seed passwords, raw bearer tokens or private absolute paths. Existing README default-password text matched local configuration and was replaced with variable-based instructions before commit; historical Git credentials were not audited/rewritten. V2 untouched; V3 additive. .env/task.txt not tracked; TRACKER/nguon/plans01–03 unchanged.

Initial unit run had one failing test assumption: it expected gap to occupy the next slot immediately, while an event may send during gap backoff. The test now ACKs that event before asserting the delayed gap retry; production behavior was not forced to fit the wrong ordering. Only final passing runs above are acceptance evidence.

## Ownership / handoff

| Owner | Files needing review | Purpose |
|---|---|---|
| B | client LoginApiClient.java, ToeicClientApplication.java | Fresh auth-me scope, candidate explicit controls/status/Stop/generation/FX coalescing |
| B | client realtime MonitoringTransport.java, RealtimeClient.java | Gap send/ACK validation, forget correlation, fresh scope, bounded heartbeat expiry/reserved slot |
| A | server monitoring MonitoringGap.java/MonitoringGapService.java | Validation, auth/scope/lock, transaction commit-before-ACK, duplicate/conflict |
| A | server realtime RealtimeWebSocketHandler.java; migration V3__monitoring_gaps.sql | Minimal overflow branch/table, no reducer/presence/event schema change |
| A/B | server/pom.xml + updated network/A3 fixtures + MonitoringDeliverySmoke + scripts | Test-scope production integration and explicit DEV/TEST fixture only |

Process warning/timeline A3 contract is unchanged. B3 can consume candidate delivery counts/status and existing event warning/timeline; gaps are persisted separately and not yet exposed as a dashboard/timeline API. A/C stage2 can extend gap/event-late/state semantics separately.

## Limits / not run

**Human B review: NOT RUN. GUI manual: NOT RUN. LAN second machine: NOT RUN. MT01: PARTIAL until B3.** A4 heartbeat-timeout/presence remains separate; no ONLINE/UNKNOWN claim, no B3/C4/state/full/delta/reducer/exam implementation. PID reuse with missing start and between-poll processes remain documented limits. T2-C2 is not complete merely because the minimal overflow hook exists. Technical C3 acceptance above is PASS; full-system prototype acceptance is not claimed.
