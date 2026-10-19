# Plan V2 index - task-first agent package

> **Status: PROPOSAL / A-B-C APPROVAL PENDING.** See [ADOPTION_STATUS_20261019.md](ADOPTION_STATUS_20261019.md). This import does not change the authoritative Phase 3 gates, `CLAUDE.md`, or existing contracts.

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
3. This plan adds **proposals** for 200-question rich media, JavaFX polish and authorized USB/focus/display monitoring. A scope conflict must be resolved and approved, not ignored.
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
