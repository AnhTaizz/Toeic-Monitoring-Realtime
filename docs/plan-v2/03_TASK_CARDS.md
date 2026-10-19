# 03 - Task contracts: 36 tasks

**No day-by-day calendar.** Follow dependencies and the code owner/reviewer. All statuses below are `TO_AUDIT`, not automatically TODO/DONE for actual source. Each task requires a fresh pinned-SHA test report and a human owner approval.

## Quick task registry

| ID | P | Owner / reviewer | Dependencies | Task |
|---|---|---|---|---|
| `BASE-01` | P0 | A / C | none | Audit actual integration baseline |
| `BASE-02` | P0 | A / B | `BASE-01` | Close Phase 3 review and integration gaps |
| `BASE-03` | P0 | C / B | `BASE-01`, `BASE-02` | Delta correctness go/no-go |
| `BASE-04` | P0 | A / C | `BASE-01` | Approve new scope and contract amendments |
| `EX-01` | P0 | A / B | `BASE-01` | Audit one legitimate 200-question TOEIC dataset |
| `EX-02` | P0 | A / B | `EX-01`, `BASE-04` | Define rich exam import and render contract |
| `EX-03` | P0 | A / C | `EX-02`, `EX-04` | Build offline parser, validator and repeatable seed |
| `EX-04` | P0 | A / C | `EX-02`, `BASE-04` | Add image metadata and ordered rich-content storage |
| `EX-05` | P0 | A / B | `EX-04` | Authenticated image delivery and media validation |
| `EX-06` | P0 | B / A | `EX-02`, `EX-05` | Render real seven-Part content in JavaFX |
| `EX-07` | P0 | A / B | `BASE-02`, `EX-02`, `BASE-04` | Proctor exam catalog and authoritative start gate |
| `EX-08` | P0 | A / B | `EX-03`, `EX-05`, `EX-06`, `EX-07`, `BASE-02` | End-to-end real 200-question acceptance |
| `UI-01` | P1 | B / A | `BASE-01` | JavaFX design system |
| `UI-02` | P1 | B / A | `EX-06`, `UI-01` | Polish candidate exam interaction |
| `UI-03` | P1 | B / A | `BASE-02`, `UI-01` | Polish Listening preparation and interruption UI |
| `UI-04` | P1 | B / A | `UI-01`, `EX-07` | Polish proctor roster, control and exam selection |
| `UI-05` | P0 | B / C | `UI-02`, `UI-03`, `UI-04` | GUI responsiveness and packaging regression |
| `MON-01` | P0 | C / B | `BASE-02` | Prove existing process monitoring end to end |
| `MON-02` | P0 | C / A | `BASE-04`, `MON-07` | Windows USB-storage presence and plug/unplug events |
| `MON-03` | P0 | B / C | `BASE-04`, `MON-07` | Candidate exam focus monitoring |
| `MON-04` | P0 | B / C | `BASE-04`, `MON-07` | Minimize and fullscreen-state monitoring |
| `MON-05` | P0 | B / C | `BASE-04`, `MON-07` | Connected-display count and changes |
| `MON-06` | P1 | B / C | `MON-07`, `EX-06` | Exam-local activity and idle metrics |
| `MON-07` | P0 | C / A | `BASE-04`, `MON-01` | Typed multi-signal event protocol and persistence |
| `MON-08` | P1 | C / B | `MON-07`, `MON-02`, `MON-03` | Rule-based alert classification |
| `MON-09` | P0 | B / C | `UI-01`, `MON-01`, `MON-02`, `MON-03`, `MON-04`, `MON-05`, `MON-07` | Unified multi-signal proctor timeline |
| `QA-01` | P0 | A / B | `EX-08`, `UI-05`, `MON-09` | Full functional and contract regression |
| `QA-02` | P0 | B / A | `QA-01` | Multi-client physical LAN acceptance |
| `QA-03` | P0 | A / C | `QA-01` | Fault injection and concurrency consistency |
| `QA-04` | P0 | C / B | `MON-02`, `MON-03`, `MON-04`, `MON-05`, `MON-07` | Physical multi-signal acceptance |
| `QA-05` | P0 | C / B | `BASE-02`, `MON-01` | E1 controlled polling trade-off experiment |
| `QA-06` | P0 | A / C | `BASE-03`, `MON-01` | E2 conditional delta and concurrent-client benchmarks |
| `REL-01` | P0 | A / B | `BASE-01`, `EX-02`, `MON-07` | Technical paper-style report |
| `REL-02` | P0 | C / A | `QA-05`, `QA-03` | Results, discussion, novelty and limitations |
| `REL-03` | P0 | B / A | `QA-01`, `EX-08`, `MON-09` | Repeatable deployment and source packaging |
| `REL-04` | P0 | A / C | `REL-01`, `REL-02`, `REL-03`, `QA-02`, `QA-03`, `QA-04` | Final demo, paper check and submission |

---

## EPIC 00 - Baseline integration and contract

### BASE-01 - Audit actual integration baseline

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** none
- **Objective:** Choose a source-of-truth integration SHA without assuming that main or the old tracker reflects all completed work.
- **Acceptance criteria:**
  - [ ] Record current main, RC branch, candidate PRs, heads, merged/unmerged status and potential conflicts.
  - [ ] Compare actual code and fresh tests with docs/TIEN_DO.md and docs/KIEM_THU.md.
  - [ ] Publish a verified status table; never mark manual/LAN tasks PASS based on automated loopback tests.
- **Verification/evidence:**
  - Repository/PR audit signed off by A and C.
  - Clean build on the selected candidate baseline, with exact SHA and environment.
- **Likely files/components:** Git refs, docs/TIEN_DO.md, docs/KIEM_THU.md, evidence/
- **Watch-out:** Existing 17-Oct report is historical evidence, not proof of current HEAD.

### BASE-02 - Close Phase 3 review and integration gaps

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-01
- **Objective:** Reconcile Listening, autosave/submit, delta hooks, server state, installer and release branch.
- **Acceptance criteria:**
  - [ ] Review the Phase 3 implementation against 02_HOP_DONG.md, especially Listening no-replay, auth-before-cache and ACK-after-commit.
  - [ ] Write a human-reviewed integration matrix and unresolved issues.
  - [ ] Do not rebuild Phase 3 tasks whose code already exists.
- **Verification/evidence:**
  - Clean Java package tests with PostgreSQL where required.
  - Manual hearing, packaged JavaFX and LAN checks remain NOT RUN until performed.
- **Likely files/components:** server/, client/, protocol/, docs/, evidence/

### BASE-03 - Delta correctness go/no-go

- **Owner:** C / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-01, BASE-02
- **Objective:** Make an evidence-backed decision about experimental DELTA; preserve FULL as the operational fallback.
- **Acceptance criteria:**
  - [ ] Use the pre-existing 20-Oct gate for MT02-MT06 and live disconnect/rejoin conditions.
  - [ ] If incomplete, incorrect or unreviewed: FULL remains the default; publish failure or missing evidence.
  - [ ] Do not claim E2 performance until correctness and measurement prerequisites are met.
- **Verification/evidence:**
  - Oracle replay vs FULL, epoch/reconnect/retry and human reviewer notes.
  - Update gate status explicitly: PASS, FAIL or NOT_DECIDED.
- **Likely files/components:** docs/MONITORING_DELTA_LIVE_GATE.md, docs/PROTOCOL.md, evidence/
- **Watch-out:** Never silently turn DELTA on in production.

### BASE-04 - Approve new scope and contract amendments

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** BASE-01
- **Objective:** Document requested expanded exam media and multi-signal monitoring before interface-changing work.
- **Acceptance criteria:**
  - [ ] Propose amendments to CLAUDE.md, 02_HOP_DONG.md, docs/PROTOCOL.md and testing docs; original lecturer sources stay unchanged.
  - [ ] Explicitly remove only the existing USB out-of-scope restriction after owner approval.
  - [ ] Record data minimization, consent/notice, capture lifetime, media copyright and retention assumptions.
  - [ ] Resolve or flag the QD-21 deadline/start-gate conflict; do not silently change deadline semantics.
- **Verification/evidence:**
  - A and C approve the delta; B reviews the affected client contract.
  - New message types, API DTO and migrations are versioned and regression-tested.
- **Likely files/components:** CLAUDE.md, Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md, docs/PROTOCOL.md, docs/QUYET_DINH.md
- **Watch-out:** This ZIP is a proposal, not an automatic override of the current contract.


---

## EPIC 01 - Complete TOEIC exam (200 questions)

### EX-01 - Audit one legitimate 200-question TOEIC dataset

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-01
- **Objective:** Identify one complete 7-Part exam and verify all source materials before coding large-scale imports.
- **Acceptance criteria:**
  - [ ] Count 6/25/39/30/30/16/54 questions in Parts 1-7, respectively.
  - [ ] Inventory audio, images, passages, groups, original answer options and correct answer keys.
  - [ ] Report missing items honestly; separate remote media URLs from files already downloaded.
  - [ ] Identify the license/permission status before distributing third-party material.
- **Verification/evidence:**
  - Produce manifest with original source, SHA-256 where local, missing-file flags and zero invented answer data.
  - Content review by A/B.
- **Likely files/components:** Offline input HTML/JSON, media manifest, dataset-audit report
- **Watch-out:** The plan package includes no copyrighted exams or media.

### EX-02 - Define rich exam import and render contract

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** EX-01, BASE-04
- **Objective:** Extend the existing exam DTO only as needed for grouped passages, images and legitimate audio-based choices.
- **Acceptance criteria:**
  - [ ] Support question order, 7 Parts, groupId, reusable audio, zero-or-more ordered content blocks and per-question options.
  - [ ] Allow Part 1/2 letter choices when transcripts are not available; do not fabricate dialogue/transcripts.
  - [ ] Correct options remain server-only; authorized candidate DTO never exposes answer key.
  - [ ] Keep compatibility with existing text/audio JSON if possible.
- **Verification/evidence:**
  - Schema/DTO fixtures including Part 1 photo, Part 3 shared audio, Part 6 passage and Part 7 multi-document.
  - Negative validator fixtures.
- **Likely files/components:** protocol exam DTOs, import contracts, docs/PROTOCOL.md

### EX-03 - Build offline parser, validator and repeatable seed

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** EX-02, EX-04
- **Objective:** Transform permitted content into validated imports; seed once and create multiple sessions from the existing exam.
- **Acceptance criteria:**
  - [ ] Validator enforces 200 unique ordered questions and expected Part distribution.
  - [ ] Every referenced audio/image exists, has an allowed format and passes SHA-256 before release.
  - [ ] No duplicate group mismatch, missing correct option or broken passage links.
  - [ ] Importer is deterministic and refuses a conflicting duplicate examId rather than silently overwriting.
- **Verification/evidence:**
  - Valid fixture succeeds; incomplete/corrupt/mismatched fixtures are rejected with exact reasons.
  - Database import + retrieval round trip on test PostgreSQL.
- **Likely files/components:** Offline tooling (Java by default), import/seed adapter, local dataset report
- **Watch-out:** CLAUDE.md currently limits Python to CSV/plots. Do not introduce a Python HTML parser unless the owner explicitly amends that rule.

### EX-04 - Add image metadata and ordered rich-content storage

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** EX-02, BASE-04
- **Objective:** Add minimum Flyway migration and filesystem configuration for exam images and content blocks.
- **Acceptance criteria:**
  - [ ] Check the latest existing Flyway version before naming a new migration; never edit applied V1..Vn.
  - [ ] Keep structured metadata in PostgreSQL; media binaries remain outside the DB/JAR.
  - [ ] Store relative paths, MIME, content length, SHA-256, ordering and question/group association.
  - [ ] Do not break the established TOEIC_AUDIO_ROOT manifest/READY semantics.
- **Verification/evidence:**
  - Fresh database migration test and upgrade test from the current release schema.
  - Reject invalid file paths and dangling asset references.
- **Likely files/components:** server/src/main/resources/db/migration/, exam services, configuration

### EX-05 - Authenticated image delivery and media validation

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** EX-04
- **Objective:** Allow authorized candidates/proctors to retrieve referenced image assets without expanding the audio protocol.
- **Acceptance criteria:**
  - [ ] Check user role, assignment and attempt scope before returning bytes or cache metadata.
  - [ ] Prevent ../ traversal, escaping symlinks, unexpected MIME and oversized files.
  - [ ] Support predictable checksum and cache behavior; no answer key in image endpoint metadata.
  - [ ] Keep the existing audio manifest, READY and start semantics intact.
- **Verification/evidence:**
  - Real HTTP auth tests for owner vs other attempt, missing/changed asset, traversal and checksums.
  - Manual client downloads with filenames containing spaces/Unicode.
- **Likely files/components:** server exam media controller/service, protocol DTOs, media config

### EX-06 - Render real seven-Part content in JavaFX

- **Owner:** B / **Reviewer:** A / **Priority:** P0 / **Dependencies:** EX-02, EX-05
- **Objective:** Display text, images and grouping as the TOEIC exam actually requires.
- **Acceptance criteria:**
  - [ ] Correctly render Part 1 photos; Part 3/4 shared audio and optional figures; Part 6/7 passage layouts and multiple content blocks.
  - [ ] Part 1/2 option labels work even without transcript text.
  - [ ] Network/download and decoding stay off JavaFX Application Thread; failed images show an honest retry/error state.
  - [ ] Image navigation does not restart Listening or lose unsaved answers.
- **Verification/evidence:**
  - GUI fixture tests for every Part and media errors.
  - Packaged JavaFX smoke with actual image/audio.
- **Likely files/components:** client exam views, styling, media client, UI tests

### EX-07 - Proctor exam catalog and authoritative start gate

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-02, EX-02, BASE-04
- **Objective:** Let a proctor select a stored exam and start a scheduled session without granting premature answer access.
- **Acceptance criteria:**
  - [ ] Provide an authenticated exam catalog with metadata only; preserve existing import/session compatibility.
  - [ ] An attempt may be ACTIVE for monitoring and READY while its session is SCHEDULED; GET questions, submit and save must be gated by authoritative server rules.
  - [ ] Preserve allowed pre-start manifest/download/checksum and READY flows.
  - [ ] Resolve server deadline policy explicitly against QD-21; retries/takeover cannot reset or prolong an existing deadline.
- **Verification/evidence:**
  - PG/HTTP tests before START and after START for questions, save and submit.
  - RBAC and READY/Listening start idempotency/reconnect regression.
- **Likely files/components:** server ExamService/controllers, client proctor exam picker, contract documentation
- **Watch-out:** Backend gate is mandatory; disabling UI controls is not a security boundary.

### EX-08 - End-to-end real 200-question acceptance

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** EX-03, EX-05, EX-06, EX-07, BASE-02
- **Objective:** Prove one complete exam works from proctor selection to final score.
- **Acceptance criteria:**
  - [ ] Proctor selects approved stored exam, assigns candidates, confirms READY and starts it.
  - [ ] Candidate navigates all Parts with real media, autosaves, submits or times out.
  - [ ] Correct-answer keys never leak into candidate DTO and final score is computed by the server.
  - [ ] Listening interruption, stale save, deadline and writer takeover remain correct.
- **Verification/evidence:**
  - Real PostgreSQL end-to-end evidence for 200 questions.
  - Packaged Windows app smoke and checksum manifest for all media.
- **Likely files/components:** Integration tests, evidence/exam-200/, delivery guide


---

## EPIC 02 - JavaFX polish

### UI-01 - JavaFX design system

- **Owner:** B / **Reviewer:** A / **Priority:** P1 / **Dependencies:** BASE-01
- **Objective:** Create a coherent lightweight JavaFX style without changing the UI stack.
- **Acceptance criteria:**
  - [ ] Define reusable CSS tokens, text hierarchy, spacing, buttons, badges, tables, hover/focus and disabled states.
  - [ ] Preserve accessibility and contrast; do not hide network failure or data-stale status.
  - [ ] No HTML/WebView replacement or framework migration.
- **Verification/evidence:**
  - Visual before/after screenshots and UI smoke.
  - No change in server or monitoring contract.
- **Likely files/components:** client JavaFX stylesheet, shared controls

### UI-02 - Polish candidate exam interaction

- **Owner:** B / **Reviewer:** A / **Priority:** P1 / **Dependencies:** EX-06, UI-01
- **Objective:** Make the 200-question exam easy to complete under exam constraints.
- **Acceptance criteria:**
  - [ ] Responsive panes for passage/image and answer area; clear question palette and answered counts.
  - [ ] Timer, save ACK, offline/stale and navigation status unambiguous.
  - [ ] No automatic playback or model reset on view reflow.
- **Verification/evidence:**
  - UI smoke at different window sizes, 200-question navigation and media stress.
  - Regression save/submit and Listening behavior.
- **Likely files/components:** client exam view/CSS

### UI-03 - Polish Listening preparation and interruption UI

- **Owner:** B / **Reviewer:** A / **Priority:** P1 / **Dependencies:** BASE-02, UI-01
- **Objective:** Show media readiness and Listening lifecycle clearly without altering authority.
- **Acceptance criteria:**
  - [ ] Download/checksum/speaker test and READY feedback have proper progress/error states.
  - [ ] START received only through valid server command; interrupted Listening visibly remains locked.
  - [ ] No duplicate start, replay or silent auto-resume.
- **Verification/evidence:**
  - LT01-LT04 targeted regression and packaged hearing test.
- **Likely files/components:** client audio/listening view/CSS

### UI-04 - Polish proctor roster, control and exam selection

- **Owner:** B / **Reviewer:** A / **Priority:** P1 / **Dependencies:** UI-01, EX-07
- **Objective:** Improve proctor readability and safe action affordances.
- **Acceptance criteria:**
  - [ ] Clear session state, candidate roster, readiness, exam catalog, starts, results and pending actions.
  - [ ] Distinguish loading/stale/disconnected from success, especially after lost HTTP response.
  - [ ] Retain server-side authorization and pending-request semantics.
- **Verification/evidence:**
  - GUI smoke with 2 candidate records and explicit wrong-role cases.
  - Compare rendered state after reconnect.
- **Likely files/components:** client proctor/dashboard JavaFX views

### UI-05 - GUI responsiveness and packaging regression

- **Owner:** B / **Reviewer:** C / **Priority:** P0 / **Dependencies:** UI-02, UI-03, UI-04
- **Objective:** Protect the functioning JavaFX application from regressions due to polish.
- **Acceptance criteria:**
  - [ ] No file, HTTP, process scan or USB probing on JavaFX Application Thread.
  - [ ] Workers stop on logout/exit; stale callbacks cannot overwrite a newly selected attempt.
  - [ ] Installer/app-image runs without IDE and with Unicode paths.
- **Verification/evidence:**
  - UI interaction and worker cleanup tests.
  - Packaged smoke and evidence on intended OS.
- **Likely files/components:** client, package config, evidence/gui/


---

## EPIC 03 - Multi-signal monitoring, including USB

### MON-01 - Prove existing process monitoring end to end

- **Owner:** C / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-02
- **Objective:** Treat existing ProcessCollector and FULL monitoring as reusable infrastructure.
- **Acceptance criteria:**
  - [ ] Candidate-only collector observes allowlisted apps and sends event/state to server.
  - [ ] Proctor sees correct attempt event/state; no proctor collector, no duplicate event per scan.
  - [ ] Missing metadata and absence of events do not become false cheating conclusions.
- **Verification/evidence:**
  - MT01/MT07/MT08 technical regression and a manual LAN observation.
  - Store observed/received times separately.
- **Likely files/components:** client monitoring, server monitoring, proctor view

### MON-02 - Windows USB-storage presence and plug/unplug events

- **Owner:** C / **Reviewer:** A / **Priority:** P0 / **Dependencies:** BASE-04, MON-07
- **Objective:** Monitor actual USB mass-storage presence and changes during an authorized exam.
- **Acceptance criteria:**
  - [ ] Compare Windows PnP notification with a documented, bounded WMI/CIM fallback; choose one justified by hardware evidence.
  - [ ] Emit PRESENT_AT_START, CONNECTED and REMOVED only for classified storage, not every USB mouse/keyboard.
  - [ ] Debounce duplicate OS notifications; report unknown classification rather than inventing device identity.
  - [ ] No reading files, serial numbers in ordinary logs, hidden monitoring or OS-level blocking.
- **Verification/evidence:**
  - Use a physical storage USB on Windows: baseline, plug, unplug, fast duplicate, reconnect and process restart.
  - Test no-admin deployment and explicit unsupported-device statuses.
- **Likely files/components:** client monitoring/windows USB observer; integration via MON-07
- **Watch-out:** Native dependencies such as JNA need README declaration and packaging test; no hardware equals NOT RUN, not PASS.

### MON-03 - Candidate exam focus monitoring

- **Owner:** B / **Reviewer:** C / **Priority:** P0 / **Dependencies:** BASE-04, MON-07
- **Objective:** Observe candidate window focus changes only during an authorized attempt.
- **Acceptance criteria:**
  - [ ] Emit focus lost/restored with monotonic in-client duration if measurable.
  - [ ] Account for legitimate JavaFX internal dialogs and startup transitions.
  - [ ] Avoid global window-title scraping or reading contents of other applications.
- **Verification/evidence:**
  - JavaFX automated event tests and manual alt-tab/outside-window demo.
  - No events from proctor role or after session ends.
- **Likely files/components:** client exam view/observer, MON-07 event adapter

### MON-04 - Minimize and fullscreen-state monitoring

- **Owner:** B / **Reviewer:** C / **Priority:** P0 / **Dependencies:** BASE-04, MON-07
- **Objective:** Track window minimize and exit from fullscreen without requiring kiosk/OS lockdown.
- **Acceptance criteria:**
  - [ ] Emit minimize/restore and fullscreen-exit only when applicable to the current window mode.
  - [ ] Debounce duplicate property transitions and distinguish intentional non-fullscreen initial state.
  - [ ] No automatic punishment or forced focus to hide the event.
- **Verification/evidence:**
  - JavaFX lifecycle and manual window actions, including closing/disposal.
- **Likely files/components:** client JavaFX stage observer, MON-07 event adapter

### MON-05 - Connected-display count and changes

- **Owner:** B / **Reviewer:** C / **Priority:** P0 / **Dependencies:** BASE-04, MON-07
- **Objective:** Report baseline display count and changes visible to the client OS.
- **Acceptance criteria:**
  - [ ] Record reported count at monitoring start and detected change during the attempt.
  - [ ] Use a bounded interval/event-driven refresh and clean resource lifecycle.
  - [ ] Unknown/unsupported changes must be labeled, not treated as proof of one-display use.
- **Verification/evidence:**
  - Physical connect/disconnect of second display where hardware permits.
  - Mock JavaFX screen data tested separately, labeled MOCK.
- **Likely files/components:** client display observer, MON-07 event adapter

### MON-06 - Exam-local activity and idle metrics

- **Owner:** B / **Reviewer:** C / **Priority:** P1 / **Dependencies:** MON-07, EX-06
- **Objective:** Capture minimal actions inside the exam application only.
- **Acceptance criteria:**
  - [ ] Aggregate question navigations/answer actions and elapsed no-interaction time.
  - [ ] Do not record keystrokes, clipboard contents, browser URL, chat messages or private files.
  - [ ] Configurable thresholds do not label the student guilty.
- **Verification/evidence:**
  - Fixture tests for idle thresholds and no-data/clock gaps.
  - Verify metrics stop after session ends.
- **Likely files/components:** client exam activity observer, MON-07 events

### MON-07 - Typed multi-signal event protocol and persistence

- **Owner:** C / **Reviewer:** A / **Priority:** P0 / **Dependencies:** BASE-04, MON-01
- **Objective:** Add a single typed event stream for USB/window/display/activity observations while preserving existing process state semantics.
- **Acceptance criteria:**
  - [ ] Document schema version, eventType, eventId, attemptId, client-observed time, server-received time, metadata limits and source.
  - [ ] Check authentication and attempt ownership before duplicate/cached response; no cross-attempt information leakage.
  - [ ] Bound the queue and retries; stable eventId across retries, dedup in DB, ACK success only after commit.
  - [ ] Process current FULL/DELTA state remains distinct from history; do not reuse PROCESS_OBSERVED schema for unrelated signals.
- **Verification/evidence:**
  - Real PostgreSQL/HTTP/WS tests: accepted, duplicate same/different, malformed, expired session, unauthorized, COMMIT failure.
  - Simulated ACK loss + actual reconnect and persisted history checks.
- **Likely files/components:** protocol, server monitoring handler/persistence, client event delivery
- **Watch-out:** Do not refactor the existing reliable process pipeline until equivalence is proven.

### MON-08 - Rule-based alert classification

- **Owner:** C / **Reviewer:** B / **Priority:** P1 / **Dependencies:** MON-07, MON-02, MON-03
- **Objective:** Group observations into actionable, explainable proctor notices.
- **Acceptance criteria:**
  - [ ] Use explicit rules with INFO/REVIEW/HIGH_ATTENTION, configured per approved policy.
  - [ ] Aggregate duplicate and burst events without deleting raw history.
  - [ ] USB or focus event alone never triggers an automatic cheating verdict or exam cancellation.
- **Verification/evidence:**
  - Deterministic rules tests, thresholds/burst fixtures and harmless false-positive examples.
- **Likely files/components:** server monitoring alert summary, client proctor display

### MON-09 - Unified multi-signal proctor timeline

- **Owner:** B / **Reviewer:** C / **Priority:** P0 / **Dependencies:** UI-01, MON-01, MON-02, MON-03, MON-04, MON-05, MON-07
- **Objective:** Let a proctor inspect an authorized candidate exam through a single reliable event timeline.
- **Acceptance criteria:**
  - [ ] Show process, USB, focus, minimize/fullscreen, display, heartbeat/session and Listening interruption from authoritative sources.
  - [ ] Sort/label client observed versus server received time; show stale/UNKNOWN and detection limitations.
  - [ ] Scoped history retrieval and reconnect refresh work; filtering must not make unobserved periods look healthy.
- **Verification/evidence:**
  - GUI session with two candidate attempts and wrong-role/other-attempt restrictions.
  - Network reconnect and stale data UX regression.
- **Likely files/components:** client proctor dashboard/model/API


---

## EPIC 04 - Verification and experiments

### QA-01 - Full functional and contract regression

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** EX-08, UI-05, MON-09
- **Objective:** Validate all user-facing paths and the network invariants of the integrated release candidate.
- **Acceptance criteria:**
  - [ ] AT01-AT11, LT01-LT04, MT01-MT08 and IT01-IT04 handled according to their specified coverage.
  - [ ] Include new image/media, USB, focus/display and start-gate cases.
  - [ ] Capture actual failures and skipped cases by ID, SHA, OS, environment and input.
- **Verification/evidence:**
  - Clean Maven test/package with real PostgreSQL where required.
  - Report PASS/PARTIAL/FAIL/BLOCKED/NOT RUN without promotion based on mocks.
- **Likely files/components:** docs/KIEM_THU.md, evidence/, test modules

### QA-02 - Multi-client physical LAN acceptance

- **Owner:** B / **Reviewer:** A / **Priority:** P0 / **Dependencies:** QA-01
- **Objective:** Demonstrate real network behavior, not multiple windows on one host.
- **Acceptance criteria:**
  - [ ] One server/database and at least two candidate apps plus a proctor across at least two physical machines.
  - [ ] Test correct scoped proctor view, concurrent saving, client disconnect and independent clients.
  - [ ] Listening disconnect preserves no-auto-replay rule; monitoring reconnect reopens fresh FULL.
- **Verification/evidence:**
  - Record topology, addresses excluding secrets, SHA, OS, steps, logs and screenshots.
  - Mark BLOCKED/NOT RUN if the second physical machine cannot be arranged.
- **Likely files/components:** docs/LAN_TWO_MACHINE_RUNBOOK.md, evidence/lan/

### QA-03 - Fault injection and concurrency consistency

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** QA-01
- **Objective:** Prove the server remains correct under duplicate, stale, malformed and concurrent requests.
- **Acceptance criteria:**
  - [ ] Test save-vs-submit-vs-timeout and writer takeover with PostgreSQL locks and deterministic barriers.
  - [ ] Test loss of ACK, server restart, stale epoch and wrong-attempt data; malformed requests cannot take down other clients.
  - [ ] Do not return success on DB rollback or return another users private exam data.
- **Verification/evidence:**
  - Recorded deterministic steps, SQL state and reproducible logs.
  - Review end state and regression against known passing SHA.
- **Likely files/components:** server/protocol tests, evidence/faults/

### QA-04 - Physical multi-signal acceptance

- **Owner:** C / **Reviewer:** B / **Priority:** P0 / **Dependencies:** MON-02, MON-03, MON-04, MON-05, MON-07
- **Objective:** Validate physical USB/display plus focus and session lifecycle through the real event path.
- **Acceptance criteria:**
  - [ ] Storage USB present-before-start/plug/unplug/duplicate works with real Windows hardware.
  - [ ] Focus/fullscreen/minimize/display events are attributed to correct attempt and timeline.
  - [ ] Reconnect retains committed history, and a stopped attempt has no active observers.
- **Verification/evidence:**
  - Hardware inventory and permissions, SHA, Windows edition, timestamps, DB rows and proctor screenshots.
  - Mock tests are supplemental, not proof of physical USB behavior.
- **Likely files/components:** evidence/multi-signal/, docs/KIEM_THU.md

### QA-05 - E1 controlled polling trade-off experiment

- **Owner:** C / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-02, MON-01
- **Objective:** Measure ProcessHandle polling trade-offs using controlled process ground truth.
- **Acceptance criteria:**
  - [ ] Poll periods 250/500/1000/2000 ms, repeated campaign as prescribed in 03_KIEM_THU_VA_THUC_NGHIEM.md.
  - [ ] Record actual observation/miss/detection latency, CPU coverage and memory definition.
  - [ ] Preserve original raw files/checksums and clearly separate historical from newly rerun data.
- **Verification/evidence:**
  - Analyzer reproduces summary from raw and source SHA.
  - Invalid/partial runs remain visible and are excluded by written rules.
- **Likely files/components:** docs/E1_T4C1_MEASUREMENT.md, evidence/e1/, analyzer

### QA-06 - E2 conditional delta and concurrent-client benchmarks

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** BASE-03, MON-01
- **Objective:** Provide defensible network/performance evidence independent of GUI polish.
- **Acceptance criteria:**
  - [ ] Run 1/5/10 headless-client load testing as supported by hardware and the existing plan, with repeats and resource metrics.
  - [ ] Run FULL-vs-DELTA E2 only when correctness gate and equivalent measurement inputs/coverage are achieved.
  - [ ] If E2 is blocked, report the reason and use FULL benchmark evidence; do not invent percentage savings.
  - [ ] Distinguish serialized application payload bytes from total TCP/IP wire bytes and headless clients from real JavaFX desktops.
- **Verification/evidence:**
  - Versioned raw metrics, test configuration, independent analyzer and peer review.
  - Negative or inconclusive results are documented.
- **Likely files/components:** evidence/e2/, evidence/load/, docs/monitoring measurement notes
- **Watch-out:** E2 is conditional even though concurrency measurement remains a required release activity.


---

## EPIC 05 - Report and release

### REL-01 - Technical paper-style report

- **Owner:** A / **Reviewer:** B / **Priority:** P0 / **Dependencies:** BASE-01, EX-02, MON-07
- **Objective:** Write a technically defensible paper, not just user instructions.
- **Acceptance criteria:**
  - [ ] Include problem, objectives, architecture, protocol, concurrent requests, persistence, failure handling and implementation.
  - [ ] Clearly separate Spring/JavaFX/DB framework behavior from logic authored by the group.
  - [ ] Explain full 200-question workflows and multi-signal observer sources with limitations.
- **Verification/evidence:**
  - A/B/C technical cross-review; relevant source/evidence links and references.
  - Report draft builds and has consistent terminology.
- **Likely files/components:** report/ (in actual repo after approval), diagrams, references

### REL-02 - Results, discussion, novelty and limitations

- **Owner:** C / **Reviewer:** A / **Priority:** P0 / **Dependencies:** QA-05, QA-03
- **Objective:** Tie every performance and reliability claim to measured runs or explicit negative results.
- **Acceptance criteria:**
  - [ ] Results use valid real metrics with test topology, run count, SHA, environment and confidence limits.
  - [ ] Novelty discusses dependable exam event delivery plus polling and delta only to the extent supported by evidence.
  - [ ] Explicitly document blind spots: process polling miss, client tampering, USB coverage, lack of webcam and monitoring consent.
  - [ ] Do not imply an E2 improvement when it was not run or was inconclusive.
- **Verification/evidence:**
  - All tables/plots are generated from immutable raw files.
  - Peer review cross-checks at least one metric by hand.
- **Likely files/components:** report/Results, Discussion, Novelty, Limitations, evidence/

### REL-03 - Repeatable deployment and source packaging

- **Owner:** B / **Reviewer:** A / **Priority:** P0 / **Dependencies:** QA-01, EX-08, MON-09
- **Objective:** Deliver an installable/runnable submission containing all legally distributable dependencies and media.
- **Acceptance criteria:**
  - [ ] Windows JavaFX client with documented Java runtime, Spring server, PostgreSQL migration and seed data.
  - [ ] TOEIC_AUDIO_ROOT/image root and URLs are configurable and do not assume localhost.
  - [ ] Clean machine startup, import/seed, login, exam, monitoring, exit/uninstall and no-secret packaging documented.
  - [ ] Do not include copyrighted content without distribution rights.
- **Verification/evidence:**
  - Cold build and clean Windows installation with hashes/screenshots.
  - README has one path to build, run and test.
- **Likely files/components:** README.md proposed update, scripts/installer, config examples, release artifact

### REL-04 - Final demo, paper check and submission

- **Owner:** A / **Reviewer:** C / **Priority:** P0 / **Dependencies:** REL-01, REL-02, REL-03, QA-02, QA-03, QA-04
- **Objective:** Hand in a credible runnable project before the lecturer deadline.
- **Acceptance criteria:**
  - [ ] Prepare one full-exam and multi-signal LAN demonstration with visible event/reconnect/USB behavior.
  - [ ] Run Compilatio and meet course similarity policy; final report version matches handed-over source.
  - [ ] Package NhomXX_TenProject_Source and NhomXX_TenProject_Report.docx in the required Source/Report tree.
  - [ ] Obtain handover acknowledgment; 31-Oct 23:59 ICT is a hard stop, 30-Oct is internal target.
- **Verification/evidence:**
  - Final signoff checklist and source/report artifact hashes.
  - Document any incomplete gate honestly.
- **Likely files/components:** final Source/, Report/, submission checklist
