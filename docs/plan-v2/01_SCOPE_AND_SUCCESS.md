# 01 - Scope, product success and release priorities

## Core product

A **Java 21 + Spring Boot + PostgreSQL + JavaFX** desktop client/server exam application with HTTP/REST for login, exam content and media, autosave, submit, results, and WebSocket JSON for real-time exam control and monitoring. It has two roles: CANDIDATE and PROCTOR, plus server-enforced assignment and scope.

The product is two integrated capabilities:

- **TOEIC testing:** at least one complete, lawful, validated 200-question seven-Part exam, including the actual required listening audio, question/group images, passages, answer options, scoring and a proctor-controlled exam catalog/session.
- **Multi-signal remote proctoring:** process events plus USB mass-storage presence/connect/disconnect, exam-app focus, window minimize/fullscreen, display changes, connection/session/Listening events. The proctor gets a scoped real-time view plus historical timeline; warnings are observations, not cheating verdicts.

## Strict product order (tasks may execute in parallel)

1. Audit/integrate the existing Phase 3 baseline and reconcile contracts.
2. Deliver **EX-08**, the 200-question end-to-end exam, after rich media vertical slices.
3. Polish JavaFX without replacing the FE technology.
4. Deliver **MON-09**, monitoring across actual devices and network, including physical USB.
5. Freeze behavior and spend the last week on acceptance, E1/E2 where valid, reporting and submission.

## Fixed release boundaries; NOT daily task assignments

- 20-Oct DELTA correctness decision in the existing course plan; keep FULL default on a missing/failed gate.
- New feature freeze target: after 23-Oct; do not open new features during the final testing/report week. Any P0 bugfix after freeze requires fresh regression.
- Internal handoff target: 30-Oct. Course source `Submission.md` says **2026-10-31 23:59 ICT**.

## Priority rules

- **P0:** exam data correctness, audio/image delivery, no early access, save/submit invariants, real USB/connectivity/focus/display monitoring, real LAN, acceptance, paper, packaging.
- **P1:** visual polish, exam-local activity counts, alert classification/rules. These must not jeopardize P0 or the freeze.
- **Out of scope unless explicitly re-approved:** webcam/microphone streaming, screenshots, keylogging, clipboard contents, browser history/URL scraping, read USB files, disable USB devices, OS kiosk locks, React, a new broker, microservices, cloud media storage, full admin exam editor, AI marking or attempts at perfect anti-cheat claims.

## Release gate, not a feature count

1. At least one real 200-question valid exam (6/25/39/30/30/16/54 per Part), media integrity checked and a documented usage right for distributed data.
2. Proctor selects the exam; candidate prepares audio without opening the test prematurely; authoritative START; saved state/result consistent under retry/timeout.
3. Candidate events travel over real HTTP/WS to server, are scoped/deduped/stored, and proctor receives or recovers them.
4. Physically detect USB storage and at least one Windows multi-signal event through the entire path, with honest limitations.
5. Multiple candidates continue independently on LAN through disconnect/reconnect; audio never auto-replays after interruption.
6. Experiments with immutable raw evidence and reproducible reporting; every claimed PASS attached to specific SHA/environment.
