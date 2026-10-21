# BASE-04 — V2 decision packet (22/10/2026)

**Status: `TECHNICAL_DECISION_PACKET_COMPLETE / A_C_B_HUMAN_DECISIONS_PENDING`.**

This packet reconciles the decision register with integrated source at `origin/main` SHA `9fc0f18` and the stacked A review branches. It is a technical recommendation, not an A/C/B signature, a production enablement decision, or physical acceptance.

## Decision matrix

| ADR | Implemented / evidenced reality | Recommended disposition | Remaining authority or evidence |
| --- | --- | --- | --- |
| V2-01 — USB scope | The approved scope is observation of USB storage changes for an assigned exam only. The repository does not justify reading files, recording serial numbers, or blocking devices. Production hardware acceptance H01–H12 is still 0/12. | Ratify the narrow disclosed scope only: presence/change metadata, no contents/serial/blocking, active only for the assigned attempt. Keep production use disabled until acceptance. | A + C wording approval; B/C physical evidence. |
| V2-02 — dataset/media | Rich schema and authenticated media routes are integrated. The portable Java importer exists on the unmerged EX-01 branch and has only MOCK/synthetic evidence. | Accept the schema and route shape. Block redistribution and release of any third-party TOEIC assets until provenance and rights are recorded. | A + B dataset approval; EX-01 review/merge and real-source evidence. |
| V2-03 — START/deadline | The PROCTORED_V2 START gate, shared DB-clock deadline, Audio+Visual READY checks, authenticated content gate and AFTER_COMMIT delivery are integrated through PR #82. The technical review is recorded in BASE-02. | Ratify the implemented V2 policy. Do not silently change legacy QD-21 semantics or create a second START authority. | A/B owner review and remaining PostgreSQL/GUI/physical acceptance. |
| V2-04 — typed event protocol | Typed signal/activity persistence and authenticated ACK-after-service-return paths are integrated, behind experimental configuration gates. They do not replace the FULL/DELTA process-state reducer. | Approve the wire/auth/commit semantics as experimental. Keep signal and alert experiments OFF outside explicit TEST until notice, retention and deletion policy are approved. | C + A protocol/privacy approval; production retention controls and acceptance. |
| V2-05 — hardware approach | The current USB integration is test-adapter evidence, not production PnP validation. No actual-machine H01–H12 result supports choosing Windows PnP or bounded CIM/WMI fallback. | Defer the production mechanism decision. Keep the production sensor disabled; compare candidates on real no-admin hardware before choosing. | C + B physical decision and evidence. |
| V2-06 — parser language | Project policy requires Java unless an exception is approved. The EX-01 branch follows that policy with an offline Java parser. | Keep Java as the parser authority. Review and merge EX-01 only after provenance and real-source gaps are explicit. | A code/data review. |
| V2-07 — release branch | The audited integration base is `9fc0f18`; BASE-01 is `316f93b` and BASE-02 is `87431c7` in the sequential local review stack. Historical reports apply only to their pinned SHA. | Pin every release report to its integrated SHA and rerun the applicable gates after merges. | A + C release selection and final integrated run. |
| V2-08 — evidence/novelty | FULL is the runtime default. DELTA requires explicit experimental client/server enablement. E1 is integrated; E2 remains conditional on correctness and physical LAN evidence. | Preserve FULL as default and claim E2 only after the documented correctness, fault and physical-network gates pass. | C + A evidence review; physical LAN run. |

## Cross-cutting operating policy

- **Retention:** source inspection found no implemented retention/deletion job for typed signal history. The earlier 30-day value is a proposal, not runtime policy. Until an approved duration and deletion mechanism exist, multi-signal and alert experiments stay disabled outside explicit TEST data that is removed during test teardown.
- **Notice and consent:** the repository does not yet contain complete, accepted production notice/consent evidence for the expanded monitoring set. Do not enable it for production candidates before that text and flow are approved.
- **Lifetime and scope:** observe only an authenticated, assigned attempt in the allowed active session states. Stop and dispose observers on terminal attempt/session state, logout, revocation, or application teardown. Technical lifecycle checks do not substitute for the pending physical cases.
- **Human judgment:** focus, display, process, signal, alert, disconnect and USB observations are review cues, never an automatic cheating verdict. Missing telemetry can also mean platform restrictions or connectivity failure.
- **Safe defaults:** FULL process snapshots remain the default; DELTA, typed signal and alerts require explicit experimental flags. USB production integration remains disabled pending hardware selection and H01–H12.
- **Data minimization:** do not collect file contents, raw USB serials, key presses, URLs, screenshots, or unrelated system activity. Do not distribute third-party exam assets without recorded rights.

## Evidence used

- BASE-01 fresh Maven run at the integrated baseline: 1,464 tests, 0 failures/errors, 159 documented opt-in skips.
- BASE-02 source review and successful five-module package build, including no-replay, auth-before-cache and commit-before-delivery checkpoints.
- Integrated migrations V13–V18 and their gated server/client configuration paths.
- Current plan, contract and ADR records. Historical reports are not promoted to fresh acceptance.

This is a documentation-only reconciliation branch, so BASE-04 adds no new runtime test claim. After human decisions, the adopted wording must be copied into the governing contract/configuration and verified on the final integrated SHA.
