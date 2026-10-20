# MON-01 evidence — 19/10/2026

[Report](REPORT.md) · [Pipeline14chặng](pipeline-audit.json) · [15criteria](acceptance-matrix.json) · [BASE03 reuse](reuse-assessment.json) · [Summary](summary.json) · [Backup receipt](base03-backup-receipt.json) · [B handoff](../../docs/V2_C_MON01_PHYSICAL_HANDOFF.md).

Status **TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW**. BASE-02 approval PENDING, physical0/B PENDING, FULL default, DELTA gate NOT_DECIDED, E2NO. Không tự DONE.

## Lượt MON-01 mới

- Source `f78b40776d0e414e2a631f9e0eb0725b2f1033aa`, base9a97cab.
- Run `MON01-20261019T103217Z-25e62fdd`; C2 harness `T2C2-4a57ef56-cc9e-449a-87f6-b2d8fab2c618`.
- 65 Java focused PASS/0FAIL/0SKIP, package-DskipTests SUCCESS, C2 late/history/presence/proctor GUI + new semantics/lifecycle assertions PASS.
- Sources MOCK; actual Spring/HTTP/WS/PG/FX/workers REAL; disconnect/write/ACK loss and DB end-state transitions SIMULATED. Physical NOT_RUN.
- New test coverage: UNREADABLE/time ownership/empty observation/no-cheating UX; workers auto-stop SUBMITTED/TIMED_OUT/CLOSED before explicit cleanup.
- No new ProcessHandle campaign; actual Windows Edge observation is explicitly REUSED from BASE-03. Full983, oracle72, C4local42 and Python22 are not new MON-01 totals.
- Diagnostic measurement COMPLETE is not a performance/E1/E2 claim.

[Run metadata/commands](MON01-20261019T103217Z-25e62fdd/run.json), [test cases](MON01-20261019T103217Z-25e62fdd/mon01-dashboard-focused.json), [assertions](MON01-20261019T103217Z-25e62fdd/smoke-checks.json), [configuration](configuration.json), [raw/artifact SHA256](raw-manifest.json).

## Reproduce

Use a clean dedicated Windows worktree at f78b407 or record a newer exact HEAD. Need Java21, cached Maven dependencies, Python, native PostgreSQL bin, desktop access and a **verified external BASE-03 backup** receipt. Do not copy .env from main. This runner checks unchanged production/POM against9a97cab, creates a new SCRAM TEST cluster/database on random loopback port and an ignored .env only inside its own worktree; it stops the cluster and removes credentials afterward.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-v2-c-mon01.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot' -Base03BackupReceipt 'D:\Toeic-Evidence-Archive\BASE03-before-MON01-20261019T102703Z-51d0baf1\receipt.json'
```

The archive receipt is account-only; run as its authorized account. A restricted sandbox reader is expected to be denied, so do not weaken the archive ACL. Adjust tool/receipt paths to the actual installed environment and verified copy. No global execution policy change.

Runner stages: six dashboard-focused classes → package-DskipTests → copy existing SmokeCleanup helper into owned C2 test loader → smoke-t2c2.ps1 -Gui → schema-count/pg-stop. Maven uses -o and explicit user cache. It never executes full BASE-03 verification again or changes production defaults. The C2 GUI is a proctor component; the full candidate app/end/submit flow still needs the B physical checklist.

Raw original run-metadata.original.json is kept under target/mon01-runs/<runId> and hashed. Public run.json redacts only worktree/user-home command paths. Assertions are selected stdout lines linked to their original log hash. Surefire XML can include private environment properties; public case metadata omits those properties. No raw screenshots, logs, XML, binaries, PG data, password file or .env are committed.

## Preservation

BASE-03 was backed up before new work:477raw/27public/Gitbundle, checksum receipt above. Original and backup477raw files were reverified after MON-01. Source/evidence BASE-03 unchanged.

MON-0151raw/artifacts (including exact JARs and test-support classes) remain in its owned worktree target paths and are backed up separately outside the repo at:

`D:\Toeic-Evidence-Archive\MON01-20261019T103217Z-25e62fdd`

Archive receipt.json and archive-manifest.json cover exact copied bytes plus the local Git bundle. The backup runs after the documentation commit, so its receipt is outside that commit. No overwrite/deletion; account-only directory. Preserve archives and worktrees for reviewer. Do not distribute raw environment/screenshot content without review.

## Public integrity / review

public-sha256.json lists all published files except itself. .gitattributes preserves exact bytes across Git checkouts. From repository root:

```powershell
$m=Get-Content evidence/v2-c-mon01/public-sha256.json -Raw|ConvertFrom-Json
foreach($e in $m.files){
  if((Get-FileHash -LiteralPath $e.path).Hash.ToLowerInvariant() -cne $e.sha256){throw ('HASH_MISMATCH '+$e.path)}
}
```

B uses [separate16row checklist](physical-pending/cases.csv) and [unsigned review](physical-pending/review.json). These include human/technical review rows; physical executed remains0. Follow the current BASE-03 physical runbook with the **exact preserved ZIP/hash** and FULL mode. Do not use old cfbba58/MSI historical hashes or call local JVMs/LAN faults physical.

A/B approvals, BASE-02 closure and hardware/operator availability remain human dependencies. MON-07/USB work is a recommendation only, not part of this execution.
