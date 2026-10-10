# T2-C4 verification — 10/10/2026

Code commit `01642976fda7ebbfcc4b09b6d4c449c216560ca8`, branch `feat/t2-c4-controlled-process-harness`, base/main fetched `f6d46b27d7309c5d0f6c89bb35e948a5969bfadf` (C3 PR#20 merged). No pre-existing C4 implementation/branch. PR/merge SHA is reported by final Git check, not invented in this pre-merge report.

## Executed checks

- Fresh baseline Maven package: 461 tests PASS, 21:28:58. Final package: 493 tests PASS (protocol32/client318/server142/spike1), 21:56:00; 32 new C4 invocations, no skipped/failure/error. `java-tests.json` derives counts from actual Surefire XML. Build before code commit has identical Java sources to that commit; docs changed only afterward. Test failure fixture is absent from production JAR; CLI/child are packaged.
- Windows11/Temurin21.0.10+7/Maven3.9.15. Committed full run started 22:05:30, clean working tree recorded in `verification-metadata.json`, seed1234/poll500/perDuration10/maxConcurrent2, REAL ProcessHandle + production collector + actual Java children. Candidate gate SIMULATED; TEST identity policy, no production name spoofing. `raw/` exact files, core manifest and ground-truth/trace FINAL hashes verified independently with Python; rejoin equals original byte-for-byte. Metadata binds code SHA/client JAR SHA/source file hashes/clock offset/config/anonymous environment. Source byte hashes are working-copy hashes (Git text normalization may differ), not Git blob IDs.
- 30 normal EXITED/exit0 trials, no forced cleanup; resourcesCleaned true. Parent-observed launch→exit interval peak2. Target200: observed7/notObserved3; target800:10/0; target3000:10/0; inconclusive0. These are this trace's counts, no general miss-rate/latency/E1 claim. Earlier exploratory dirty-source run:25 observed/5 notObserved. Both results retained, no cherry-picking or requirement all children observed.
- Tampered copy trace: join exit2/INCOMPLETE, all30 INCONCLUSIVE, definite notObserved0. Original raw unchanged. Exact corruption retained under `tampered-copy/`; its old hashes deliberately fail.
- C3 CLI replay regression PASS: manual faults/seed/corrupt rejection; REAL ProcessHandle/owned Edge capture/cleanup then REPLAY. Gate/faults SIMULATED; no network/DB from replay.
- C1 `-Gui` PASS: REAL localhost Spring/PG/WS/HTTP + owned Edge + proctor component, full/state/autoSTALE/TTL/shutdown; fault sets MOCK. Measurement C4 PASS: REAL localhost transport/PG, event+overflow MOCK, ACK suppression SIMULATED.
- Event-C3 initially FAIL at real lost-ACK retry while the above smokes ran concurrently. No code changed for this failure. Rerun alone PASS: REAL owned Edge/ProcessHandle/PG/network commit/dedup/reconnect, MOCK overflow, SIMULATED ACK loss. Initial log retained. Concurrent test interference/timing is possible, cause not proven; no false initial PASS. Run desktop integration smokes sequentially for review.

## New testcase coverage

| Test | Invocations | Independent expected or failure checked |
|---|---:|---|
| HarnessPlanTest | 4 | Manual seed7 expected schedule; same seed; three durations; bounds |
| ObservationJoinerTest | 11 | Handwritten identity/clock/coverage fixture; PID reuse/missing start/name; trace/source error; measured times not target |
| HarnessArtifactsTest | 4 | Exact roundtrip; truncation/checksum/duplicate/size/existing file; manifest mutation |
| TestTracePolicyTest | 3 | TEST missing metadata retained; unchanged production8; TEST network/replay rejected; ownership/reuse |
| ControlledProcessHarnessTest | 8 | Real children with EMPTY MOCK collector still generate truth; child fail/noREADY/hang/launchfail/sourcefail; API cancel; known grandchild after root exit; process/worker cleanup |
| HarnessCliTest | 2 | Invalid options/existing output; broken trace yields inconclusive |

Ground truth is from parent ProcessBuilder start/READY receive/GO send/waitFor return. Collector only supplies observation trace. Same parent JVM nanoTime origin is mapped by traceStartElapsedNanos; child time is used solely to hold itself. Process lifetime includes JVM startup/notification differences; exitObserved is parent notification, not exact kernel exit. `example.json` selects a real200ms trial, full ground-truth row, actual scan and join; shared PID/start/collector and offset addition audited.

## Limitations and review

Human B review NOT RUN: B must inspect ProcessSelection/OwnedProcessPolicy, TEST DTO/parser isolation, clock binding, identity and coverage, cancellation/cleanup. ChatGPT planning/review NOT RUN per user's existing opt-out. OS Ctrl+C/hardkill of harness parent, candidate full GUI, LAN/second-machine/package acceptance, E1/E2/CPU/memory/detection latency/general miss-rate NOT RUN. C2-Gui/A4 not rerun in C4 (covered by unchanged server unit suite and previous task evidence); server/protocol production source untouched. C4 has no DB/network/GUI dependency; separate regressions use temporary owned schemas/ports/resources and do not reset shared DB.

Initial development failures were fixed before final package: ambiguous matches(null) overload avoided by includes seam; Windows READY CRLF accepted; cancellation interruption consumed during cleanup and repeated child failures no longer interrupt owner cleanup. New tests cover these lifecycle cases.

No .env/tracker/original plan/migration/exam-lifecycle changes. Artifacts omit credential/full command/user/private path; logs are clearly labeled sanitized copies. Ground-truth/trace/core metadata remain exact bytes, protected by .gitattributes. `checksums.json` covers retained evidence files including this report, excluding the checksum ledger itself. SHA is integrity, not authenticated signature.

Run: `mvn package`, then `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c4.ps1 -JavaHome <JDK21>`. [CLI/contracts/meaning of each timestamp](../../docs/CONTROLLED_PROCESS_HARNESS.md). Stop after C4; no C5/delta.
