# BASE-02 — Phase 3 integration review (22/10/2026)

**Status:** `TECHNICAL_REVIEW_COMPLETE / B_REVIEW_AND_PHYSICAL_ACCEPTANCE_PENDING`  
**Owner:** A · **Reviewer:** B · **Priority:** P0  
**Branch:** `review/a-base02-20261022`  
**Source reviewed:** `9fc0f180149b477dcf1bf6ab112bf8aa56841461`, stacked on BASE-01 commit `316f93b`

This review closes the code/document reconciliation portion of BASE-02. It does not claim the human-reviewed matrix, hearing, LAN or second-machine gates that require B and physical equipment.

## 1. Required invariant review

| Contract invariant | Current implementation | Review result | Remaining evidence |
|---|---|---|---|
| Listening never automatically replays | `ListeningPlaybackJournal.reserve` creates a durable marker with `CREATE_NEW`; duplicate commands are rejected. Reconnect checks authoritative server state and maps incomplete/playing state to interruption rather than resume. Completed/interrupted state stays blocked. | **PASS by source + existing tests** | Human LT01–LT04 hearing and physical disconnect remain NOT RUN. |
| Authorization before cache/idempotent response | Autosave and submit lock the attempt and verify candidate ownership/writer/state before reading prior request rows. Audio/image routes verify role/attempt scope before reading bytes. Monitoring event/signal paths revalidate role, active attempt, assignment and session before duplicate/history responses. | **PASS by source + existing PG evidence** | B review; fresh PG run on this exact branch was unavailable because configured PostgreSQL `127.0.0.1:5433` was not listening. |
| Commit before success ACK/push | Exam HTTP success returns after transactional service completion. `MonitoringEventService` and `SignalService` contain no socket calls; handlers form ACK only after the transactional proxy returns. Listening push uses `@TransactionalEventListener(AFTER_COMMIT)`. | **PASS by source + existing PG/WS fault evidence** | Repeat on final RC with PostgreSQL and preserve rollback evidence. |
| Save/submit/timeout serialization | Attempt rows are selected `FOR UPDATE`; writer epoch, deadline and state are checked under the lock. Existing PG tests cover save/submit/timeout waiters and lost ACK retries. | **PASS by source + inherited PG evidence** | Final QA-03 campaign remains separate and depends on QA-01. |
| START/readiness correctness | Session and attempt locks use the documented order; retries return the committed start ID without extending an existing deadline. Rich START requires combined audio/visual readiness. | **PASS by source + PR #82 PG/E2E evidence** | Real media, human hearing and physical LAN remain pending. |
| FULL/DELTA safety | FULL is still the default; DELTA remains capability-gated and opt-in. Missing base/epoch causes resync, not guessed state. | **NO REGRESSION FOUND** | BASE-03 remains `NOT_DECIDED`; this review does not enable DELTA. |
| Installer/release branch | Main contains the unified JavaFX app and packaging scripts; local installed evidence exists for one Windows host. | **CODE READY / PHYSICAL PENDING** | Clean second Windows machine, install/uninstall and LAN acceptance remain NOT RUN. |

## 2. Source checkpoints reviewed

- `ExamService.autosaveAnswers` and `submitExam`: role → V2/attempt lock → ownership/writer/state/deadline → request cache → mutation, all inside a transaction.
- `AudioService.candidateManifest`, `download`, `ready` and `start`: ownership precedes manifest/file access; START is idempotent and publishes only through the post-commit listener.
- `ListeningPlayback` and `ListeningPlaybackJournal`: one-shot marker, no reconnect replay, failure/interruption remains locked.
- `MonitoringEventService.store`: role and attempt authorization precede duplicate lookup; no socket call inside the transaction.
- `SignalService.store/history`: token/session/role/attempt authorization is rechecked after locks and before duplicate/history data; ACK is constructed only after return to the caller.
- `RealtimeWebSocketHandler` and `ListeningDelivery`: handler-level ACK occurs after service return; Listening delivery is explicitly `AFTER_COMMIT`.

No Phase 3 production feature was rebuilt in this branch. The only contract edit corrects stale documentation that still described EX-03/EX-07 as unmerged after PR #82.

## 3. Verification on this branch

| Check | Result | Scope/limit |
|---|---|---|
| Inherited BASE-01 `mvn -B -fae test` on the same source | **PASS:** 1,464 tests, 0 failures/errors, 159 skipped | PostgreSQL, GUI/E2E and physical opt-ins were skipped. |
| `mvn -B -DskipTests package` | **BUILD SUCCESS**, all five reactor modules | Compile/package proof only; tests deliberately skipped because the full unit suite had just passed. |
| PostgreSQL availability preflight | `127.0.0.1:5433` = **not listening** | No fresh PG claim is made. Existing PG18.6 evidence is retained, not relabelled as a new run. |
| `git diff --check` | Run before commit | Documentation consistency only. |

## 4. Human/physical matrix still required

| Gate | Actual status | Required next action |
|---|---|---|
| B review of this integration matrix | **PENDING** | B checks Listening UX, packaged client and the source/evidence claims above. |
| LT01–LT04 human hearing | **NOT RUN** | Use the current source/artifact and record operator, device, expected/actual and evidence. |
| Second-machine MSI | **NOT RUN** | Install/run/uninstall on a clean allowed Windows host; record hashes and runtime. |
| Physical LAN | **0/41 NOT RUN** | Execute the existing T3-C4 kit on at least two physical machines. |
| Physical disconnect/reconnect | **NOT RUN** | Use real network interruption; do not substitute loopback or a simulated socket fault. |

Therefore BASE-02 must remain `TECHNICAL_REVIEW_COMPLETE / B_REVIEW_AND_PHYSICAL_ACCEPTANCE_PENDING`; it cannot be marked DONE by this agent-only branch.

