# Plan V2 index - task-first agent package

EX-01 internal dataset evidence: [STUDY4_6852 audit - 22/10/2026](EX01_STUDY4_6852_AUDIT_20261022.md) and [SHA-256 manifest](EX01_STUDY4_6852_SHA256_20261022.csv).

EX-02 rich exam contract evidence: [contract closeout - 23/10/2026](EX02_CLOSEOUT_20261023.md).

Current live reconciliation: [BASE-01 audit — 22/10/2026](BASE01_LIVE_BASELINE_20261022.md).

Phase 3 integration review: [BASE-02 review — 22/10/2026](BASE02_PHASE3_REVIEW_20261022.md).

V2 technical decision packet: [BASE-04 review — 22/10/2026](BASE04_DECISION_PACKET_20261022.md).

> **Status: TEAM SCOPE APPROVED (user-reported, 19-Oct-2026).** See [adoption record](ADOPTION_STATUS_20261019.md) and [kickoff](V2_EXECUTION_KICKOFF_20261019.md). Scope-only appendices have been made to `CLAUDE.md` and the existing contract; exact protocol, deadline, migration and hardware implementation ADRs still require A/B/C decisions. Phase 3 physical gates are unchanged.

This is an additive `docs/plan-v2/` planning pack intended for the existing Java TOEIC + realtime monitoring repository. All work is defined as tasks, **not as a daily calendar**.

## Safe installation

Extract the ZIP at the **repository root**. It creates `PLAN_V2_START_HERE.md` and `docs/plan-v2/`. These names were chosen to avoid overwriting the repository's `README.md`, `CLAUDE.md`, old five-stage plan, source files or `TRACKER.json`. If an existing `docs/plan-v2/` is already present, inspect diffs before replacing it.

Example PowerShell after extracting:

```powershell
Get-Content .\PLAN_V2_START_HERE.md
Get-Content .\docs\plan-v2\03_TASK_CARDS.md
```

Prompt the coding Agent: `Read PLAN_V2_START_HERE.md and docs/plan-v2/06_AGENT_RUNBOOK.md; audit the selected task before code changes.`

## Precedence and safety

1. Course requirements and `Submission.md` are authoritative for deliverables and deadline.
2. Current approved `CLAUDE.md`, `02_HOP_DONG.md`, protocol, repository code and PR merge state govern implementation.
3. **Approved V2 scope** includes lawful 200-question rich media, JavaFX work and authorized, disclosed USB-storage/focus/display observation. Exact wire, DB and deadline contracts require separate decision records; do not treat broad scope approval as technical signoff.
4. A historical test report is evidence for its own SHA only; `PASS` must be established for the integrated build.
5. The old `TRACKER.json` is not altered by this ZIP. Never automatically convert tasks from TODO to DONE or invent acceptance.

## Document map

| File | Purpose |
| --- | --- |
| `01_SCOPE_AND_SUCCESS.md` | Product scope, stages, done criteria, explicit exclusions |
| `02_ARCHITECTURE_CONTRACT.md` | Existing architecture, proposed media/monitoring extensions |
| `03_TASK_CARDS.md` | 36 actionable task specs and test evidence |
| `04_EXECUTION_OWNERSHIP.md` | Parallel task queues, owners, dependency handoffs |
| `05_TESTING_RELEASE.md` | Release acceptance, physical tests, E1/E2, submission |
| `06_AGENT_RUNBOOK.md` | Coding/verification Agent workflow and task prompts |
| `07_RISKS_DECISIONS.md` | Decisions needing approval and fallback rules |
| `08_BASELINE_SOURCES.md` | Known repo facts, source pointers, confidence limits |
| `BACKLOG.json` | Machine-readable DAG, owner, reviewer and criteria |
| `TASK_TEMPLATE.md` | Copyable implementation task ticket |
| `DATA_IMPORT_CHECKLIST.md` | Dataset and media acceptance checklist |

## General acceptance definition

A task is not DONE because an Agent wrote code. It is DONE only after: diff review, appropriate fresh tests, evidence at a pinned SHA, an owner/reviewer signoff, correct docs and no violated invariants. Physical USB/LAN/hearing evidence cannot be replaced with MOCK traces.
