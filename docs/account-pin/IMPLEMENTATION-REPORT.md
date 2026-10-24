# ACCOUNT_PIN — implementation and verification

Implementation is on `feature/account-pin-20261024`, based on Phase 4.5 checkpoint `185e1b3c5318062f41d94f9f7fb85e7cc77e8975`. Existing local data, unrelated changes and historical release artifacts are retained. Main is not merged or pushed. This document is updated with final run IDs and counts after verification; intermediate numbers are not release acceptance.

The existing Server image-root environment fix is kept separately in checkpoint `d49da88`. The implementation file inventory is [FILES.txt](FILES.txt); unrelated README additions, local launch menus, datasets, acceptance files and line-ending-only changes remain outside the implementation checkpoint.

## Implemented contract

```mermaid
flowchart TD
    A[Admin creates accounts and hands over individual passwords] --> P[Proctor selects exam and creates ACCOUNT_PIN room]
    P --> S[Session SCHEDULED: no Attempts or deadline]
    S --> C[Candidate account login and shared PIN]
    C --> J[Transactional JOIN: create or reuse one Attempt and issue scoped token]
    J --> W[Waiting Room: Audio and Visual preparation]
    W --> T[Proctor START with at least one joined member]
    T --> R{Valid individual READY at START?}
    R -->|Yes| L[Existing one-shot Listening and Reading]
    R -->|No| B[Listening interrupted: NOT_READY_AT_START]
    B --> Q[Complete personal preparation before common deadline]
    Q --> D[Reading only]
    L --> E[Existing autosave and Submit]
    D --> E
```

`ACCOUNT_PIN` is an admission mode; Session contract remains `PROCTORED_V2`. Legacy rooms and non-room attempts retain their authentication and all-READY START policy. Account tokens cannot access room attempts before or after JOIN. Scoped tokens remain restricted by candidate, assigned attempt and membership/session binding for HTTP and WebSocket monitoring.

## Changes and security invariants

- **Protocol:** `CreatePinRoomRequest`, redacted `CreatedPinRoom` and `JoinPinRoomRequest`; optional `roomMode`/`readyForExam` on preparation and `roomMode`/`canStart` on readiness. Original constructors remain compatible; `allReady` still means every member is READY.
- **Room/session:** `ExamRoomService`, `ExamRoomController`, `RichSessionService` create an empty Session, transactionally create membership/Attempt on first JOIN, preserve writer/epoch/readiness/answers/deadline on repeated JOIN, cap membership at 100 and reject new membership after START. Session locks serialize JOIN/START/rotate; membership is re-read after acquiring locks. Failed inserts roll back the Attempt too.
- **PIN:** new `RoomPinCodec`, application config and launcher environment loading. SecureRandom five-digit PIN, BCrypt verification, HMAC-SHA256 lookup with operator-supplied persistent key, unique lookup index. Persisted account-level failed-PIN window survives login/restart. Rotation only before START revokes room admission tokens and changes only room credential; the old PIN cannot issue new tokens.
- **Admin:** `AccountProvisioner`, Admin service/controller/client/view issue one-time distinct Candidate passwords on the new UI. Old API calls without the optional flag retain compatibility. Candidate-only reset follows Session→account lock order, rejects active exams, changes only password hash and revokes all tokens; it does not enable an account or change its role.
- **START/preparation:** `AudioService`, `VisualReadinessService`, `V2SessionPolicy`, `RichImageService`, `ExamService` select the policy from persisted room mode. START verifies actual media, snapshots valid receipts/token/writer/epoch/lease under locks and records one start context/common deadline. Non-ready members are interrupted before COMMIT and excluded from existing post-COMMIT playback delivery. Retry cannot arm late members. Pending personal preparation has restricted image access; exam/save/Submit are guarded on the server. Late attestation commits the individual visual manifest without resetting Listening, deadline or epoch. Exact successful late-attestation retry is supported.
- **Client:** Candidate account-login PIN page; Proctor shared-PIN creation tab, one short START warning only when needed, scoped roster/monitoring refresh; existing legacy tabs retained. Audio/visual coordinators permit narrow late preparation, then enter Reading. Existing media player, autosave, Submit, writer and monitoring infrastructure are reused. One-time PIN/password fields and credential DTO strings are redacted/cleared; sensitive successful responses use `Cache-Control: no-store`.
- **Verification tools:** existing room-UX runner includes ACCOUNT_PIN suites. Existing benchmark supports optional `-AccountPin`; existing portable packager supports a release name and runbook. No new benchmark infrastructure or external service.

## API and migration

| API | Authorization / behavior |
|---|---|
| POST `/api/v2/pin-rooms` | PROCTOR; `{examId,durationMinutes?}`; default 120 minutes; Session and one-time common PIN |
| GET `/api/v2/pin-rooms` | Assigned PROCTOR only; existing cursor page, no credential |
| POST `/api/v2/pin-rooms/join` | CANDIDATE account token only; `{pin,requestId}`; principal supplies identity; scoped admission response |
| POST `/api/v2/pin-rooms/{sessionId}/pin/rotate` | Assigned PROCTOR, SCHEDULED only; one-time new PIN and token revocation |
| POST `/api/v2/admin/accounts/{id}/password/reset` | ADMIN; Candidate only, no active exam; random one-time password and all-token revocation |
| POST `/api/v2/admin/accounts` | Existing API plus optional boolean `issueLoginPassword`, default false; Admin UI sends true |
| Existing START endpoint | Same request/response shape; policy chosen by DB room mode, retries preserve start context |

Migration **V21__account_pin_admission.sql** adds admission mode/shared hash/shared lookup and shape/uniqueness constraints, permits NULL personal member PIN for the new mode and adds the persisted per-account JOIN limit table. V1–V20 are unchanged. A populated V20 upgrade test compares existing account/session/attempt/member data and migration checksums before/after upgrade; legacy room mode defaults to `LEGACY_PIN`.

## Verification record

Final PASS/FAIL/ERROR/SKIP counts, artifact/checksum and raw-evidence references will be recorded after the running suites complete. Historical Phase 4.5 counts are not counted as results of this implementation.

Checkpoint gates completed (independent invocations, not an additive total):

| Run | PASS | FAIL / ERROR | SKIP | Scope |
|---|---:|---:|---:|---|
| `core/room-ux-20261024T105008Z-31149c7b` | 121 | 0 / 0 | 0 | New client/GUI login tests, crypto and 16 real PostgreSQL ACCOUNT_PIN cases including collision, capacity, races, migration and restart |
| `gui-focus/room-ux-20261024T104458Z-5e4d92cb` | 3 | 0 / 0 | 0 | Production Admin, Proctor and two Candidate applications; 2/2, 1/2 and 0/2 READY; pre/post-START transport reconnect; media playback, Reading, Monitoring, autosave and Submit |
| `backend/room-ux-20261024T100631Z-274c2a7a` | 78 | 0 / 0 | 0 | Legacy admission/authentication/Admin/V2 lifecycle regression plus the earlier new-case set |
| `scope/room-ux-20261024T110934Z-dadd0710` | 6 | 0 / 0 | 0 | Legacy scope guard explicitly enters retained legacy route after account login; new PIN-entry GUI cases also pass |
| `full/room-ux-20261024T111254Z-08ded180` | 1688 | 0 / 0 | 20 | All 1708 JUnit cases completed at `88bf034`; Maven subsequently failed at Windows JAR rename during repackage, not during tests |
| `timeout/room-ux-20261024T120705Z-536978c3` | 1 | 0 / 0 | 0 | Real PostgreSQL: disabled joined Candidate finalizes/scored TIMED_OUT exactly once; old tokens remain denied |
| `postfix/room-ux-20261024T120936Z-e335001a` | 110 | 0 / 0 | 0 | After narrow timeout fix: 17 ACCOUNT_PIN cases, legacy runtime/lifecycle/Admin/authorization/scheduling and new/legacy production E2E |
| `full/room-ux-20261024T110340Z-3b50fce4` | 1055 | 1 / 0 | 4 | Initial full run stopped in Client at the old scope-navigation harness; server suites were not run in this invocation; corrected and targeted rerun passed above |
| `gui/room-ux-20261024T102733Z-5a7f3041` | 102 | 3 / 0 | 0 | Historical debug run: legacy E2E 2/2 passed; three ACCOUNT_PIN harness cases failed and were subsequently fixed and rerun above |

Run roots are `target/account-pin/runs/` (junction to the owned Windows QA output). Raw XML and environment-bearing logs remain in `.private` folders; sanitized final evidence will be copied to this documentation folder. Legacy assertions were retained.

Initial failed runs are retained for diagnosis: PostgreSQL exposed Instant binding errors in the new PIN-limit/START updates; explicit `java.sql.Timestamp` binding fixes those paths. A legacy START–disable race exposed an incorrect 403 when an assigned candidate was disabled; that internal START validation now returns INVALID_STATE/409 while preserving candidate access denial. ACCOUNT_PIN transport reconnect now preserves confirmed audio preparation and does not invalidate the preparation epoch; legacy reconnect behavior is retained. GUI harness needed real credential-dialog closure, a non-null return marker for custom-content dialogs, selection of the actual active tab and waits for enabled controls/server READY receipts before clicking START; assertions were not relaxed or disabled. A stale joined count in the room catalog is refreshed from existing presence polling.

Final lifecycle review found that `timeoutAttempt()` reused an enabled-account guard: an ACCOUNT_PIN member disabled before START could be started as non-ready but then remain ACTIVE after deadline. The minimal fix adds a trusted internal `lockForTimeout()` inspection and changes only the timeout caller. Disabled-account inspection is allowed only for ACCOUNT_PIN; HTTP/WebSocket and legacy guards are unchanged. The new real PostgreSQL test verifies scoring/idempotence and unchanged writer/deadline/answers/disabled state/Listening interruption. This patch was tested by the final 110-case affected regression and E2E invocation after the full baseline; the full baseline count is not presented as a full rerun of the post-fix source. The runner also correctly accounts for `Class#method` selectors instead of silently counting zero XML cases.

The full Maven invocation returned exit 1 because Windows refused renaming `server/target/server-0.1.0-SNAPSHOT.jar` to `.jar.original` after all tests passed. No running user Server or unrelated artifact was killed/deleted to overcome this. Release binaries are built separately from the verified checkpoint in a clean detached worktree; that package-only build is recorded separately and is not acceptance evidence.

## Limits and rollback boundary

- Physical LAN on two Windows machines and direct human hearing: **NOT TESTED** without the devices. Same-host JavaFX/MediaPlayer and simulated benchmark READY are separate evidence categories.
- No audio catch-up or replay; a candidate without valid READY at START loses Listening for that attempt and uses the remaining common time for Reading.
- PIN namespace has 90,000 values. Lookups remain reserved after START/restart; automatic room cleanup/PIN reuse is deferred. Shared key must be retained; key loss/rotation is an operator recovery issue, not an automatic startup action.
- New account/password/PIN credentials are shown once. Lost create response is recovered from the assigned-room list and pre-START PIN rotation; the client never silently recreates a room. An uncertain create outcome blocks further creation in that view until recovery or a fresh login.
- The portable bundle does not contain a DB, exam media, passwords or HMAC key. Both binaries must be upgraded together. Operator application DB has not been migrated by the isolated verification runs.
- The local workspace `.env` was provisioned once with a random 32-byte persistent PIN key, without printing/committing it or changing existing DB/media settings. This does not restart the running application or migrate its DB. Other deployments must provision their own persistent key as described in the runbook.
- Checkpoints include only tested implementation files. No reset, rollback, merge, push, destructive file cleanup or down migration. A fallback demo must use the historical binary/artifact with a separate pre-upgrade test DB, not overwrite live exam data or assume old-binary/new-schema compatibility.
