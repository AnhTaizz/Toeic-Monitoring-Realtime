# EX-08 closeout — real 200-question acceptance

Date: 2026-10-23

Owner/reviewer lane: A / B

Verified implementation source: `e6b43205cafffb19f4466a71969825e9224d19df`

## Outcome

The user-supplied, internal-only `STUDY4_6852` export is now converted offline into the V2 rich-import contract without committing the raw exam or media. The adapter rejects an unsafe/non-empty destination, compares the public server/client question data, rejects answer keys in the client export, validates the TOEIC Part distribution, stages media under the configured server roots and records SHA-256 plus image dimensions.

Generated local output (Git-ignored):

| Item | Result |
|---|---:|
| Questions / Parts 1–7 | `200` / `6,25,39,30,30,16,54` |
| Groups | `73` |
| Audio | `54` |
| Images | `11` |
| Source JSON SHA-256 | `5322db75467f82d724158f5fa949a91e2cfecbb5e94a21c3cb7cfea54d1173ed` |
| `rich-import.json` SHA-256 | `354408ded85e5c160c1556bd644bb82cab5bb5011757ee0832c4e26104d4424c` |
| Local manifest SHA-256 | `570727fc23a4062d0b453e36830edf71834b3fff0d5780643d2dc0ad802bf6eb` |

Permission remains `USER_CONFIRMED_INTERNAL_ONLY` as confirmed by the user. This is not permission to redistribute third-party content.

## Acceptance mapping

| Requirement | Evidence | Result |
|---|---|---|
| Proctor selects the stored approved exam, assigns one candidate, receives READY and commits START | Real catalog/create/readiness/START HTTP flow on PostgreSQL 18.1 | PASS |
| Candidate can resolve all seven Parts and all 200 questions through production client mapping | `RichExamApiClient` + `RichExamModel`; every question resolves to a navigation unit | PASS |
| Real media is deliverable and unchanged | All `54` audio and `11` images downloaded through authenticated routes and matched to their SHA-256 | PASS |
| Candidate DTO does not expose answer/source keys; server scores | Forbidden-key assertion plus server submit of the audited answer map; persisted result `200/200` | PASS |
| Retry/deadline/stale writer/takeover/interruption remain authoritative | duplicate START keeps ID/deadline; old writer gets `STALE`; takeover changes Listening to `INTERRUPTED/WRITER_TAKEOVER` | PASS |
| Packaged Windows smoke | Fresh app-image, bundled Java 21.0.12.1, Unicode path, real JavaFX Media/WAV lifecycle | PASS (API/WS fixture is MOCK) |

## Reproducible verification

```text
scripts/verify-ex08-postgres.ps1 ...
runId: ex08-20261023T023208Z-8e120bde
sourceSha: e6b43205cafffb19f4466a71969825e9224d19df
OS: Windows NT 10.0.26300.0
Java: 21.0.12.1
PostgreSQL: 18.1
tests: 1 passed, 0 failed, 0 errors, 0 skipped
ownedSchemasRemaining: 0
clusterStopExit: 0

scripts/smoke-package-windows.ps1 ...
bundled runtime: 21.0.12.1
client JAR SHA-256: df904fae9f0e808d0d3809f8b42ac62f7e95b797fa8a109099b96b6ada786847
Unicode launcher: REAL
JavaFX audio lifecycle: PASS
API/WS for package-only smoke: MOCK
```

The earlier whole-reactor attempt stopped in the pre-existing `ControlledProcessHarnessTest.realChildrenCreateGroundTruthEvenWhenCollectorAlwaysReportsEmpty` client harness before the server module. It is not counted as an EX-08 pass. The scoped EX-08 runner was added so the real dataset test is repeatable and not hidden by that unrelated harness failure.

## Scope boundary

No raw exam, audio, image, token or database credential is committed. The automated Windows package smoke is not a human hearing review, installer test, clean-machine test or physical LAN acceptance. This document records technical acceptance and does not impersonate reviewer B's personal approval.
