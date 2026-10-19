# 06 - Agent workflow: read, audit, implement, verify, hand off

## Before any code

1. Read `PLAN_V2_START_HERE.md` and this package index.
2. Read existing root `CLAUDE.md`, `docs/plan-v1/02_HOP_DONG.md`, `docs/PROTOCOL.md`, `docs/QUYET_DINH.md`, `docs/TIEN_DO.md`, `docs/KIEM_THU.md`, and selected task spec.
3. Inspect current git state, correct base/branch, outstanding PRs and file ownership. Identify already-implemented functionality and tests; do not infer missing work from the stale original tracker.
4. State conflicts between plan and approved contract explicitly. USB and media changes are requested V2 scope but old `CLAUDE.md` still excludes USB; owner approval and documentation change are required before implementation.
5. Complete a READ-ONLY gap report before coding, with affected files, exact tests and uncertainty.

## Single-task coding loop

- Pick **one task ID** and obtain explicit user instruction to implement it.
- Confirm inputs, owner, reviewer, source SHA, active branch and dependency readiness.
- Draft a narrow file-change plan, the exact DTO/migration contract (if any) and test cases.
- Implement smallest end-to-end slice that proves network/DB/UI functionality. Avoid broad architecture rewrites, unrelated refactors and unnecessary dependencies.
- Run targeted regression, then suitable module and integration tests; check Windows package/UI where relevant.
- Report observed outcomes; do not fabricate PASS, manual/hardware runs, PR merges or reviewer signatures.
- Produce a handoff with changed files, SHA, actual commands, outputs, test limitations, behavior risks and docs needing update.
- Agent never merges, approves a release gate, changes the tracker status or edits lecturer originals without explicit authorization.

## Prompt: start-of-session audit (COPY)

```text
You are the repository technical lead and verification-minded coding Agent.
Read PLAN_V2_START_HERE.md and docs/plan-v2/{README.md,01_SCOPE_AND_SUCCESS.md,02_ARCHITECTURE_CONTRACT.md,03_TASK_CARDS.md,04_EXECUTION_OWNERSHIP.md,05_TESTING_RELEASE.md,07_RISKS_DECISIONS.md,08_BASELINE_SOURCES.md}.
Then read root CLAUDE.md, the existing approved contract, docs/PROTOCOL.md, docs/QUYET_DINH.md, docs/TIEN_DO.md and docs/KIEM_THU.md.
Inspect live git HEAD/branches and source before judging completion. Reconcile the 36 proposed tasks with code, PRs and pinned-SHA evidence. Identify completed work, partially verified work, missing work, duplicates and contract conflicts. Return: (1) task matrix, (2) top risks/dependencies, (3) recommended next parallel tasks for A/B/C, (4) acceptance test list. This call is READ ONLY. Do not edit or merge anything.
```

## Prompt: implement a task (COPY and replace TASK_ID)

```text
Implement exactly TASK_ID from docs/plan-v2/03_TASK_CARDS.md.
First inspect actual branch SHA, relevant source, tests, existing approved contracts and task prerequisites. If a required contract amendment is not approved, STOP with a precise conflict proposal and do not silently override CLAUDE.md.
Provide a short change plan (files allowed, DTO/API effects, tests). Work only on an isolated feature branch and avoid unrelated refactors. Preserve authorization-before-cache, PostgreSQL transaction/commit boundaries, writerEpoch/revision/deadline, monitoring epoch/FULL fallback and Listening no-auto-replay invariants.
Run the required targeted tests and fresh regression. Do not assert a physical USB/LAN/GUI pass based on mocked tests. Provide SHA, diff summary, exact test commands/results, remaining NOT RUN cases, risks and owner/reviewer handoff. Do not merge, auto-complete the tracker or invent credentials/third-party exam data.
```

## Prompt: verification-only after a coding PR

```text
Review task TASK_ID against its acceptance criteria and the current approved protocol. Do not change implementation at first. Inspect diff and source SHA; identify risk to role/attempt scope, ACK-after-commit, epoch/revision, late save, start gate, media access and observer lifecycle. Reproduce issues with targeted tests and real PostgreSQL/WS as appropriate. Report findings P0/P1/P2 and factual status PASS/PARTIAL/FAIL/NOT RUN. Mark physical tests NOT RUN unless you physically performed them. Do not merge.
```

## Good task-handoff response format

```text
Task / owner / reviewer / base SHA / head SHA / branch
Goal and implementation summary
Modified files (and why)
Contract changes or decisions (approved? yes/no)
Tests: command, environment, count, PASS/FAIL/PARTIAL/NOT RUN
Evidence artifacts and logs (paths, hashes)
Known limitations; security/privacy/copyright checks
Unresolved external dependencies; next task(s)
Explicit signoff needed before merge
```
