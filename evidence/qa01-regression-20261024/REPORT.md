# QA-01 integrated regression - 2026-10-24

Outcome: **PARTIAL**. The automated release-candidate regression, real PostgreSQL paths, STUDY4 200-question acceptance, opt-in JavaFX suites, packaged app-image smoke, and real Windows CIM baseline were executed. Physical LAN, human hearing, installer/clean-machine, USB hot-plug, and second-display operations were not run. One integrated application-control case fails reproducibly at proctor login and is not promoted to PASS.

## Verified sources and environment

- Release-candidate source: `acc1f82ed64410ae3759c572813cb80dcc6de8f4` (`origin/main` at campaign start).
- Test-only stabilization after the first JavaFX run: `682358e` waits for the V2 create form to be mounted before querying it. No production source changed.
- Windows NT `10.0.26300.0`; Eclipse Temurin `21.0.12.1`; Maven `3.9.16`; PostgreSQL `18.1`.
- Internal-only input: `STUDY4_6852`, 200 questions, Part distribution `6/25/39/30/30/16/54`, 73 groups, 54 audio files, 11 images. Generated rich-import SHA-256: `354408ded85e5c160c1556bd644bb82cab5bb5011757ee0832c4e26104d4424c`.
- Raw exam/media, credentials, databases, package images, and private logs remain Git-ignored and are not committed.

## Executed runs

| Run | Actual result | Scope boundary |
|---|---|---|
| Clean reactor `mvn verify` | **1500 PASS / 53 SKIP / 0 FAIL / 0 ERROR**, 1553 total | Real PostgreSQL 18.1 where required; skipped IDs are preserved in `skipped-tests.json` |
| EX-08 STUDY4 acceptance | **1/1 PASS** | Real media, Flyway, PostgreSQL, HTTP; all 65 media downloads hash-verified |
| Opt-in JavaFX regression | Initial **36 PASS / 1 ERROR**, then **37/37 PASS** after test synchronization fix | Real JavaFX Stage; API fixtures are MOCK unless the test says otherwise |
| Windows app-image smoke | **8/8 assertions PASS** | Bundled JRE and real JavaFX WAV lifecycle in Unicode path; API/WS MOCK, hearing SIMULATED, installer NOT RUN |
| Integrated monitoring, attempt 1 | **3/4 PASS, 1 FAIL** | Real JavaFX/HTTP/WS/PostgreSQL and owned ProcessHandle; USB snapshots and OS actions are MOCK/programmatic |
| Integrated monitoring, attempt 2 | **3/4 PASS, 1 FAIL** | Same stable failure: `applicationControlsStopRestartLogoutAndSwitchAccount` times out waiting for proctor login |
| Real Windows CIM baseline | **1/1 PASS** | 10 scans, provider ready, storage count 0, ACK 1, workers stopped; no physical connect/remove |

Every owned PostgreSQL runner reported zero remaining schemas and a clean cluster stop. The full and EX-08 result receipts, package metadata, GUI XML hashes, monitoring run IDs, and source hashes are recorded in `receipt.json`.

## Acceptance matrix

| IDs | Status | QA-01 evidence and limitation |
|---|---|---|
| AT01-AT10 | PASS | Clean real-PostgreSQL regression retained authorization, revision/idempotency, lock/deadline, submit/timeout, takeover, stale-writer, and answer-finality coverage. |
| AT11 | PARTIAL | Automated ACK/reconnect/app-state coverage passes; physical cable/network and fresh-app LAN exercise were not run. |
| LT01-LT04 | PARTIAL | Media validation, start/replay gates, disconnect stop/no replay, takeover/timeout, JavaFX Media, and packaged Unicode-path smoke pass; human hearing, codec fault, and physical LAN remain not run. |
| MT01-MT08 | PARTIAL | Protocol/state/history/retry/gap/epoch/UNKNOWN component coverage passes. Integrated monitoring commits focus/window/display/activity/status/gap and MOCK USB data, but the application-control case fails at login; physical USB/display and LAN remain not run. |
| IT01 | PARTIAL | Fresh app-image with bundled runtime and Unicode path passes; MSI, clean second machine, and human hearing are not run. |
| IT02 | NOT RUN | No two-candidate/one-proctor physical LAN campaign was executed. |
| IT03 | PARTIAL | JavaFX responsiveness, disconnect cleanup, process/source worker termination, and slow real-CIM scans were exercised; physical network degradation was not. |
| IT04 | NOT RUN | Component rollback/restart tests exist, but this campaign did not stop and restart the complete packaged DB/server/client system mid-operation. |
| Images/media/start gate | PASS (automated scope) | STUDY4 imports and authenticates all 65 media assets; seven-Part JavaFX views and server-authoritative READY/START cases pass. |
| USB/focus/display | PARTIAL | MOCK transitions traverse real WS/HTTP/PostgreSQL; real CIM baseline is healthy with zero devices. Physical hot-plug, Alt-Tab, and second-display changes were not run here. |

## Findings

1. The first opt-in GUI run exposed a test race: the controller catalog became ready before `#v2-candidates` was mounted. The isolated case passed, and the test now waits for the form node. The complete selected GUI set then passed 37/37.
2. `MonitoringTimelinePostgresTest.applicationControlsStopRestartLogoutAndSwitchAccount` failed twice at the bounded wait `real application proctor login`. Earlier cases in each run completed and produced valid summary evidence, but the four-case run is **FAIL**, not PASS. Follow-up owner: A; inspect login scene error/status capture and make the failure diagnostic explicit before changing production behavior.
3. The full clean run's 53 skips are not silently counted as passes. Thirty-two opt-in GUI skips were rerun explicitly; STUDY4 was rerun separately; package and monitoring opt-ins have separate receipts. Remaining environment/physical cases retain PARTIAL or NOT RUN above.

## Handoff

Reviewer B should verify the matrix and the reproducible monitoring login failure. QA-02 remains the correct lane for multi-machine LAN acceptance; QA-04 remains the physical multi-signal lane. This report is technical evidence and does not impersonate reviewer B's approval.
