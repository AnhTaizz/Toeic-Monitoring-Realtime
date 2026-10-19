# Plan V2 adoption status - 19/10/2026

**PROPOSED / NOT APPROVED FOR IMPLEMENTATION.** This documents-only import is to make the proposed V2 plan visible to the team. It does **not** change the approved implementation contract, mark tasks DONE, or turn newly proposed capabilities into current product requirements. No Java, Flyway, protocol, tracked status, original plan or test evidence is modified by this import.

## Grounded repository baseline

- Baseline checked before V2 import: `main` at `88781b1d1f167634200fd30a0109a3d7b39a910b` (PR #60 integrated).
- Phase 3 status: `CODE_READY_STAGE3_ACCEPTANCE_PENDING`, **NOT `STAGE3_DONE`**. Confirm the live HEAD, CI and tracker again before any new task.
- Two known Phase 3 P1 fixes have been merged: monitoring CLOSE/correlation (PR #47) and concurrent audio cache publication (PR #57). CI on that main HEAD succeeded; this does not replace physical acceptance.
- Still requires two physical Windows hosts (LAN 41-case runbook), a person checking LT01-LT04 audio, MSI installed/used/uninstalled on the second host, C/B monitoring gate decision and A/B/C signoffs. FULL remains the production default; DELTA remains experimental.
- Main source of truth for accepted behavior: `CLAUDE.md`, `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`, `docs/PROTOCOL.md`, `docs/QUYET_DINH.md`, code and actual merged PRs. Read `docs/STAGE3_FINAL_HANDOFF.md` for acceptance steps.

## Proposed V2 scope requiring explicit decisions

| V2 proposal | Conflict/gate | Human owner(s) |
|---|---|---|
| Full 200-question exam with lawful photo/audio/multi-document assets | Existing Phase 3 implements a smaller exam surface and cannot be labeled EX-08 complete. Agree legal content, rich metadata, migrations and UI changes first (`ADR-V2-02`). | A + B |
| USB mass-storage observation, not device blocking or file reads | Current `CLAUDE.md` explicitly excludes USB; agree exception and disclosure before sensor/protocol code (`ADR-V2-01`, `ADR-V2-05`). | A + C, B UI review |
| Focus/minimize/fullscreen and display signals | New scoped, privacy-limited event model; requires review of eventId/ACK/dedup, OS capabilities and physical tests (`ADR-V2-04`). | C + A + B |
| Session START / deadline semantics | Must preserve QD-21 until an approved cross-layer contract change (`ADR-V2-03`). | A + B |
| Feature freeze | V2 proposes **after 23/10**; original approved 5-stage plan lists **27/10**. This import does not silently replace the original milestone. | A + B + C |
| Dataset and media rights | Public repo must not redistribute third-party TOEIC questions/audio/images without permission. Missing real content means acceptance remains blocked, not guessed. | A + team |

**Priority recommendation:** Finish real Phase 3 acceptance before expanding implementation scope. The hard submission deadline is 31/10/2026 at 23:59 ICT. V2 describes 36 proposed task cards, not 36 newly assigned or already approved tasks; `BACKLOG.csv` / `BACKLOG.json` statuses must be reconciled against current code and evidence. If the new workload cannot be completed and verified before the deadline, document an agreed reduced scope instead of claiming 200Q/USB DONE.

## Safe team workflow

1. A/B/C read `PLAN_V2_START_HERE.md`, this note, `07_RISKS_DECISIONS.md` and the old approved contract.
2. A/B/C explicitly accept/reject/defer each scope-impacting ADR and priority change; only then amend `CLAUDE.md` and `02_HOP_DONG.md` through a separate reviewed change.
3. For each approved task, reconcile existing code/PRs/evidence, assign owner/reviewer and acceptance gate; do not reset `docs/TIEN_DO.md` or `TRACKER.json` from V2 `TO_AUDIT` placeholders.
4. Use `06_AGENT_RUNBOOK.md` for small PRs and independently verifiable tests. Never let an agent sign off for a human, simulate physical hardware PASS, or promote DELTA by assumption.
5. Keep the original 5-stage plan and historical evidence intact for reproducibility.

### Import provenance

Original user-provided archive: `Toeic_Monitoring_Plan_V2_Agent_20261018(1).zip` (prepared 18/10/2026). This docs import fixes an invalid `03_TASK_CARDS.md` PowerShell path/control character in the V2 index, normalizes CSV line endings for text import, and regenerates SHA256SUMS without an impossible self-referential checksum. All other task bodies are retained from the proposal. Hashes in `SHA256SUMS.md` refer to the imported text files.
