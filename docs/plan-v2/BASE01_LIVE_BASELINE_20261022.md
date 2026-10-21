# BASE-01 — Live integration baseline audit (22/10/2026)

**Status:** `AUDIT_COMPLETE / C_REVIEW_PENDING`  
**Owner:** A · **Reviewer:** C · **Priority:** P0  
**Audit branch:** `audit/a-base01-20261022`  
**Audited source:** `origin/main` `9fc0f180149b477dcf1bf6ab112bf8aa56841461`

This document reconciles the static Plan V2 backlog with the repository after PRs #70–#97. It does not create an A/B/C signature and does not turn technical or MOCK evidence into physical acceptance.

## 1. Repository baseline

- `origin/main` advanced from the previous audit point `a9ab660` to `9fc0f18` through PR #97, which publishes the new QA-05/E1 campaign.
- The rich-exam chain was integrated by PR #82: EX-03, EX-05, EX-07, EX-06, the proctor V2 UI and the resolved V13–V15 Flyway order are present on `main`.
- The monitoring chain was integrated by PRs #84–#96: typed signals, USB TEST adapter/demo, focus/window/display/activity observers, MON-08 alerts, MON-09 timeline and the verified desktop handoff are present on `main`.
- `origin/feat/ex01-html-pipeline-20261020` remains unmerged. Its head is `1c1d6a86bfeb52b9e44d33878ea2a054e43118bb`; its merge base with current `main` is `a6fba31178a1694f42df4a682d4a0ed205d77e45`. The branch changes 99 paths and must be reviewed as a separate EX-01 delivery.
- `origin/feat/process-risk-change-timeline-20261021` remains one client-only commit ahead of the old `a9ab660` main. It is a MON-09 presentation follow-up, not an A-owned backend task.
- Remote pruning removed already-merged topic branches. Deleted remote refs are not evidence that their work was lost; the corresponding merge commits remain on `main`.

## 2. Reconciled Plan V2 task matrix

The status vocabulary below is intentionally stricter than `DONE`:

- `INTEGRATED_REVIEW_PENDING`: production code is on `main`, but the required owner/reviewer acceptance is not recorded.
- `TECH_VERIFIED_PHYSICAL_PENDING`: technical evidence exists, while required hardware, LAN, hearing or second-machine evidence is absent.
- `BLOCKED`: a declared dependency or required real input is unavailable.
- `NOT_STARTED`: no task-specific deliverable meeting the card's acceptance criteria was found.

| Task | Owner/reviewer | Live status | Evidence and remaining gate |
|---|---|---|---|
| BASE-01 | A/C | **AUDIT_COMPLETE / C_REVIEW_PENDING** | This report; fresh source `9fc0f18`. |
| BASE-02 | A/B | **TECH_VERIFIED_PHYSICAL_PENDING** | Phase 3 code and local MSI evidence are integrated; LAN remains 0/41, second machine and human LT01–LT04 remain NOT RUN. |
| BASE-03 | C/B | **TECH_VERIFIED / GATE_NOT_DECIDED** | PR #70 and BASE-03 evidence are on main; FULL remains default, DELTA remains experimental/off. |
| BASE-04 | A/C | **SCOPE_APPROVED / POLICY_REVIEW_PENDING** | Runtime contracts exist, but monitoring consent/retention, USB policy and final owner approvals remain pending. |
| EX-01 | A/B | **UNMERGED / REAL_SOURCE_BLOCKED** | Portable Exam Seed branch `1c1d6a8` is ready for code review with MOCK acceptance; ETS Test 01 still lacks sufficient verified rights/content/media evidence. |
| EX-02 | A/B | **CONTRACT_ADOPTED / TASK_REVIEW_PENDING** | PR #67 contract and ADR are on main; team-scope approval is recorded, not individual GitHub approval. |
| EX-03 | A/C | **INTEGRATED_REVIEW_PENDING** | Rich importer and PG tests entered main through PR #82; production licensed dataset acceptance is outside the technical fixture proof. |
| EX-04 | A/C | **INTEGRATED_REVIEW_PENDING** | V11/V12 rich storage plus V14 import metadata are on main; C review is not recorded as task acceptance. |
| EX-05 | A/B | **INTEGRATED_REVIEW_PENDING** | Authenticated visual delivery is on main with PG/HTTP evidence; real licensed media remains pending through EX-01. |
| EX-06 | B/A | **INTEGRATED_REVIEW_PENDING** | Seven-Part JavaFX renderer and real server/PG E2E with MOCK fixture are on main; Windows hearing/LAN and real dataset are pending. |
| EX-07 | A/B | **INTEGRATED_REVIEW_PENDING** | Catalog, PROCTORED_V2 preparation and authoritative START are on main; owner/reviewer acceptance and physical flow remain pending. |
| EX-08 | A/B | **BLOCKED** | Blocked by EX-01 real dataset/license/media and BASE-02 physical acceptance; MOCK E2E does not satisfy this card. |
| UI-01 | B/A | **INTEGRATED_REVIEW_PENDING** | TOEIC Room/proctor CSS and reusable presentation components are integrated; explicit A review is not recorded. |
| UI-02 | B/A | **INTEGRATED_REVIEW_PENDING** | Candidate rich interaction is integrated; real-data/manual accessibility acceptance remains pending. |
| UI-03 | B/A | **INTEGRATED_REVIEW_PENDING** | Listening preparation/interruption UI exists; human hearing and disconnect acceptance remain pending. |
| UI-04 | B/A | **INTEGRATED_REVIEW_PENDING** | Proctor redesign and V2 session controls are integrated; physical/manual acceptance remains pending. |
| UI-05 | B/C | **INTEGRATED / PHYSICAL_PENDING** | Unified application flow exists; clean-machine/second-machine and complete physical lifecycle are not accepted. |
| MON-01 | C/B | **TECH_VERIFIED_PHYSICAL_PENDING** | PR #71 and later integrated demo evidence exist; physical LAN review remains pending. |
| MON-02 | C/A | **INTEGRATED_TEST / USB_PHYSICAL_PENDING** | Adapter and integrated TEST demo are on main; H01–H12 remain 0/12 physical. |
| MON-03 | B/C | **INTEGRATED_REVIEW_PENDING** | Focus observer is on main; manual Alt+Tab and complete real-path review remain pending. |
| MON-04 | B/C | **INTEGRATED_REVIEW_PENDING** | Window observer is on main; manual minimize/fullscreen acceptance remains pending. |
| MON-05 | B/C | **INTEGRATED / DISPLAY_PHYSICAL_PENDING** | Display observer is on main; second-display hardware acceptance is NOT RUN. |
| MON-06 | B/C | **INTEGRATED_REVIEW_PENDING** | Exam-local aggregate activity is on main; terminal lifecycle/manual review remains pending. |
| MON-07 | C/A | **INTEGRATED / POLICY_REVIEW_PENDING** | Typed event/ACK/dedup/history pipeline is on main and technically verified; A must still approve auth/retention policy. |
| MON-08 | C/B | **INTEGRATED_EXPERIMENTAL** | Explainable rule engine is on main but repository defaults remain OFF and thresholds/policy are not approved. |
| MON-09 | B/C | **INTEGRATED_REVIEW_PENDING** | Unified timeline and desktop handoff are on main; USB/display/LAN physical coverage is incomplete. |
| QA-01 | A/B | **BLOCKED** | EX-08 is blocked; UI-05 and MON-09 still have physical gates. |
| QA-02 | B/A | **NOT_RUN / BLOCKED** | Requires QA-01 and at least two physical machines. |
| QA-03 | A/C | **NOT_STARTED_AS_V2_GATE** | Earlier transaction/fault tests exist, but the V2 card depends on QA-01 and has no final V2 campaign/sign-off. |
| QA-04 | C/B | **NOT_RUN_PHYSICAL** | USB H01–H12, second display and full physical multi-signal flow are incomplete. |
| QA-05 | C/B | **COLLECTION_VERIFIED / HUMAN_ACCEPTANCE_PENDING** | PR #97 contains a twelve-run E1 campaign; B method/acceptance review is not asserted. |
| QA-06 | A/C | **BLOCKED_CONDITIONAL** | Concurrent-client load remains required; FULL-vs-DELTA E2 cannot be claimed while BASE-03 is NOT_DECIDED. |
| REL-01 | A/B | **NOT_STARTED_AS_V2_DELIVERABLE** | Legacy drafts exist, but no Plan V2 paper-style report satisfying this card was found. |
| REL-02 | C/A | **BLOCKED** | Requires QA-05 acceptance and QA-03. |
| REL-03 | B/A | **BLOCKED** | Requires QA-01, EX-08 and MON-09 acceptance. |
| REL-04 | A/C | **BLOCKED** | All final QA/release dependencies remain incomplete. |

## 3. Current release blockers

1. **Real 200-question source:** the available ETS/Zenlish-derived material is not yet sufficient for EX-01/EX-08 acceptance. Rights, original options/keys/group mappings and distributable local media must be verified; otherwise the task stays BLOCKED.
2. **Physical acceptance:** LAN 0/41, USB H01–H12, second display, second-machine MSI and human Listening checks remain incomplete.
3. **Owner decisions:** BASE-03 remains NOT_DECIDED; FULL is the safe default. Monitoring retention/consent and alert policy still require the named humans.
4. **Static status drift:** `BACKLOG.json` and `BACKLOG.csv` still say `TO_AUDIT` for all 36 cards. They should only be changed after the corresponding owner/reviewer decision; this audit supplies the reconciliation input and does not forge those decisions.

## 4. Next authorized sequence for A

1. BASE-02: review the integrated Phase 3 invariants and prepare/execute the remaining physical handoff with B.
2. BASE-04: record A/C decisions for auth, transaction, retention, notice/consent, USB scope and monitoring policy against the code already on main.
3. EX-01: review the unmerged portable-seed branch and either supply a legally usable, verified exam or record EX-08 as blocked.
4. Review the already integrated EX-02/03/04/05/06/07 work; do not rebuild it.
5. Only after those gates, run EX-08, QA-01, QA-03 and the required concurrency portion of QA-06.

## 5. Verification performed for this audit

- Fetched and pruned `origin`; verified current main and surviving remote branch heads.
- Audited first-parent merges #70–#97 and checked ancestry of the unmerged EX-01 branch.
- Compared task ownership/dependencies in `BACKLOG.json`, `03_TASK_CARDS.md` and `04_EXECUTION_OWNERSHIP.md` with progress, test and evidence reports.
- Ran a fresh `mvn -B -fae test` on Windows from source `9fc0f18`: **BUILD SUCCESS**, 1,464 tests, 0 failures, 0 errors and 159 skipped opt-in tests (protocol 86/0 skipped; monitoring-spike 35/0; client 874/28; server 469/131). PostgreSQL, GUI/E2E and physical cases guarded by environment flags remained skipped, so this result is a regression baseline rather than physical acceptance. The first invocation did not execute tests because Maven resolved its repository to inaccessible `C:\.m2\repository`; it was rerun with the valid user Maven cache.

