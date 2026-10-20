# BASE-03 evidence — 19/10/2026

[Execution report A–J](REPORT.md) · [Summary](summary.json) · [Case matrix](test-matrix.json) · [Artifact hashes](artifacts.json) · [Raw SHA-256 inventory](raw-manifest.json) · [Environment failures retained](environment-findings.json) · [Pending physical cases](physical-pending/cases.csv).

Gate **NOT_DECIDED**, FULL default preserved, B review PENDING, physical LAN0/41 NOT_RUN, E2_ALLOWED=NO. All results here are new; previous audit numbers are excluded.

| Run | Source | Scope |
|---|---|---|
| BASE03-20261019T095007Z-25f35add | fc1652408694272dd85880af64d87c3628a91123 | Wrapper/Windows inherited-pipe failure before tests, retained |
| BASE03-20261019T095202Z-66b7cc19 | 834f683e50b22e214ab63bbf7d046a7d418b8242 | FULL-first,163+151 focused,983 regression/7 skip,22 Python,oracle72,local live42 |
| BASE03-20261019T095950Z-e51076ac | 463cbadc50465c6ca2f2d4c0a0701dcb9c38e034 | Additional real WS stale DELTA epoch/FULL sequence probes; production identical to prior run |
| T3C4-LAN-98875f94-04f0-436d-94ce-1fc8fe0d523f | test463cbad / runtime834f683 | Preparation only;0/41 executed;B PENDING |

314 focused cases are a subset of983 regression PASS. Smoke assertions, campaign cases and repeated kit checks have different units and are reported separately. Intentional negative oracle results are expected FAILs successfully detected. C4 measurement is INCOMPLETE from an owned hard-kill, not a performance run.

## Reproduce the FULL-first suite

Use a clean dedicated Windows worktree at source834f683 (or record the new exact HEAD), without an existing .env. Require JDK21, Maven with cached dependencies, Python, native PostgreSQL bin/initdb/pg_ctl/createdb/psql, Edge for the owned ProcessHandle fixture and desktop access. Do not copy the shared .env. Runner creates its own SCRAM cluster/database/port and ephemeral DB/seed credentials; it never selects dev DB settings.

From that worktree, use the same command as this run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-v2-c-base03.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
```

Adjust installed tool paths explicitly. This starts owned test GUI/Edge/JVMs and uses loopback. It does not change execution policy globally. Actual stage commands, times, exit codes and random TEST port are in each run.json. Maven uses -o and an explicit user cache; failure before tests is an environment failure and must be retained.

Reproduce only the supplementary probes from source463cbad or a later descendant with identical production/POM files:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-v2-c-base03.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot' -PriorSuccessfulRun evidence/v2-c-base03/BASE03-20261019T095202Z-66b7cc19/run.json
```

Supplement mode validates successful prior FULL/cleanup, ancestry and no production/POM diff. It creates a NEW isolated cluster, packages test classes, runs C2 including BASE03_FULL_SEQUENCE and BASE03_RETIRED_DELTA, then stops/cleans it. It does not claim a second full regression. Run the complete suite when production changes.

## B handoff

ZIP is local at target/base03-handoff/T3C4-BASE03-463cbad.zip in this worktree. Hashes in artifacts.json are the independent receipt. Do not publish binaries or copy credentials. Read [physical runbook](../../docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md); launch a process-scoped `powershell -NoProfile -ExecutionPolicy Bypass` session before its script snippets if this host's default policy blocks scripts. No machine policy change is needed.

Bundle source pin463cbad, runtime artifact834f683, kit implementation commit2ac13d2. All22 manifest files were checked on source and extracted ZIP;29+29 kit checks are MOCK/no-network. Initialize on extracted ZIP succeeded without contacting a server. B must create new run directories on both actual machines; the pending example here has not run a physical case.

To rebuild the exact kit, use a NEW absolute output path and an explicit repository root (PowerShell5.1 cannot resolve this script's PSScriptRoot default reliably):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/lan-t3c4/New-T3C4LanBundle.ps1 -OutputDirectory '<NEW_ABSOLUTE_BUNDLE_PATH>' -RepositoryRoot '<THIS_WORKTREE>'
```

Common.ps1 rejects different JAR/source hashes. Do not weaken it or replace checksums to make a new build pass. Future runtime changes require a new tested source/hash pin and a new bundle.

## Evidence and privacy

477 raw/local artifacts were inventoried after cleanup. Original logs, XML, traces, screenshots and binaries stay in this worktree's ignored target paths; preserve the worktree for review. Raw JUnit XML may contain environment properties, so only case/status/runtime and XML hashes are published. No PostgreSQL data directory, password file, .env, home cache or raw screenshot is committed.

run.json is a path-redacted metadata derivative: the unmodified original is retained under target/base03-runs/<run-id>/run-metadata.original.json with its SHA-256. Test statuses/times/exit codes are unchanged. pg_ctl start intentionally has no redirected stdout/stderr; postgres.private.txt is the authoritative server log. Assertion JSONs contain selected PASS lines and a link/hash to the full private stdout, not invented logs. Oracle summary and local report are exact copies; raw-manifest identifies their originals.

Public checksums are in public-sha256.json (excluding itself). Verify the public files from repo root:

```powershell
$root=(Get-Location).Path
$m=Get-Content evidence/v2-c-base03/public-sha256.json -Raw|ConvertFrom-Json
foreach($e in $m.files){
  $p=Join-Path $root $e.path
  if((Get-FileHash -LiteralPath $p).Hash.ToLowerInvariant() -cne $e.sha256){throw ('HASH_MISMATCH: '+$e.path)}
}
```

Trace replay inputs are versioned under client/src/test/resources/monitoring-traces/t3-c3; fixture/source checksums are in harness metadata and raw-manifest. Re-run summaries into a new output directory; do not edit/overwrite raw or add an END to the killed endpoint.

Remaining work belongs to the physical session and B review. No local report constitutes their signature.
