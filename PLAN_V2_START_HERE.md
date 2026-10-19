# TOEIC Monitoring Realtime - Plan V2 (Agent entrypoint)

> **Activation 2026-10-19: V2 SCOPE APPROVED BY TEAM (user-reported).** Follow [adoption record](docs/plan-v2/ADOPTION_STATUS_20261019.md) and [A/B/C kickoff](docs/plan-v2/V2_EXECUTION_KICKOFF_20261019.md). Technical ADRs and Phase 3 physical acceptance remain pending; do not claim either completed.

> Additive, **task-based** project plan. This package contains planning materials only: no source code, binaries, exam content, audio, images, credentials or database migrations. It does not automatically change existing implementation contracts.

**Repository:** `AnhTaizz/Toeic-Monitoring-Realtime`  
**Prepared:** 2026-10-18 (Asia/Ho_Chi_Minh)  
**Team:** 3 owners A (server/DB), B (JavaFX/UI), C (monitoring/network measurements)  
**Submission:** **23:59 on 2026-10-31**; handover target before the deadline.

## Read in this order

1. `docs/plan-v2/README.md` - priority, navigation and non-destructive import.
2. `docs/plan-v2/01_SCOPE_AND_SUCCESS.md` - what the product must do.
3. `docs/plan-v2/02_ARCHITECTURE_CONTRACT.md` - changes to agree before coding.
4. `docs/plan-v2/03_TASK_CARDS.md` - **36 task contracts** (IDs, owner, reviewer, dependencies, acceptance, tests).
5. `docs/plan-v2/BACKLOG.json` - machine-readable mirror for tasks/automation.
6. `docs/plan-v2/04_EXECUTION_OWNERSHIP.md` - dependency and parallel work rules.
7. `docs/plan-v2/05_TESTING_RELEASE.md` - acceptance gates, E1/E2, release.
8. `docs/plan-v2/06_AGENT_RUNBOOK.md` - safe vibecoding workflow and ready-to-use prompts.
9. `docs/plan-v2/07_RISKS_DECISIONS.md` - decisions to explicitly approve.
10. `docs/plan-v2/08_BASELINE_SOURCES.md` - what is verified versus unverified.

## First Agent instruction

**TEAM-SCOPE APPROVAL RECEIVED; READ-ONLY RECONCILIATION FIRST.** Read existing `CLAUDE.md`, current `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`, `docs/TIEN_DO.md`, `docs/KIEM_THU.md`, `docs/PROTOCOL.md`, `docs/QUYET_DINH.md`, and the task cards in this package. Inspect current git HEAD, branches, PR state, code and evidence. Do not assume the status fields in the new backlog reflect actual progress. Report existing, missing and conflicting work before making changes. Scope appendices in `CLAUDE.md` and `02_HOP_DONG.md` authorize the V2 scope, not an unreviewed wire/schema/deadline change. Execute BASE-01 and contract decisions via BASE-04 before interface-changing code; reconcile actual task status before editing tracker.

**Task-oriented, not day-by-day:** dependency gates drive execution. The final week is reserved for testing/report/release, not feature growth. The old planning dates are constraints, not daily assignments.
