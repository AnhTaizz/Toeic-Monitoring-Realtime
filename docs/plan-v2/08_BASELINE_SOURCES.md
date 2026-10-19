# 08 - Grounding notes: sources, known facts, unknowns

## Current course and repository sources to read

- `Ke_hoach_LT_Mang_5_chang/nguon/Instruction.md`: network programming, protocol, concurrency, failures, experiments, Novelty and technical-paper report are required.
- `Ke_hoach_LT_Mang_5_chang/nguon/Submission.md`: deadline **2026-10-31 23:59**, Compilatio and source/report physical handoff.
- `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md`: original Phase 3, 20-Oct DELTA gate, original 27-Oct freeze and original owner tasks. This plan proposes an earlier freeze, but does not overwrite old plan.
- `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`: approved state, authorization, submit, Listening and monitoring semantics. Must be read before code.
- `Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md`: AT, MT, LT, IT and E1/E2 methodology.
- `docs/TIEN_DO.md`: progress by SHA and branch, including the stabilization report from 17-Oct. Historical evidence only.
- `docs/KIEM_THU.md`: detailed state of actual verified tests; NOT RUN and PARTIAL are meaningful.
- `docs/PROTOCOL.md`, `docs/QUYET_DINH.md`: protocol and design decisions to preserve/amend.
- `docs/AUDIO_RESOURCES.md`, `docs/LISTENING_LIFECYCLE.md`, `docs/PROCTOR_SESSION_CONTROLS.md`: established audio, READY, START and proctor controls.
- `docs/MONITORING_SYNC_REPORT_DRAFT.md`, `docs/MONITORING_DELTA_LIVE_GATE.md`, `docs/PROCESS_MONITORING_SURVEY.md`: monitoring scope/limitations, FULL/DELTA and source selection.
- `docs/E1_T4C1_MEASUREMENT.md`, `docs/LAN_TWO_MACHINE_RUNBOOK.md`: experiment and physical LAN procedures.
- `server/src/main/resources/db/migration/V5__exams_and_sessions.sql`: existing exam/session DB structure; inspect all subsequent migrations before changing it.
- `client/src/main/java/vn/edu/toeic/client/exam/CandidateExamView.java`: current text/passage/audio UI; no assumed image renderer.
- `client/src/main/java/vn/edu/toeic/client/monitoring/CandidateMonitoringSession.java`: current collector/delivery/full/delta bridge.
- `server/src/main/java/vn/edu/toeic/server/monitoring/MonitoringStateService.java`: RAM-state/epoch and delta capability.

## Verified from repository documents as of planning discussion

- The source already has audio manifest/READY/START and Listening interruption logic, answer persistence and proctor control components, process monitoring, FULL default and experimental DELTA path.
- 17-Oct stabilization report cited clean tests and MSI smoke for a **specific historical SHA**; it explicitly kept physical LAN, manual hearing, clean-machine/load and E2 as NOT RUN.
- `CLAUDE.md` bans USB monitoring at baseline and limits Python use. New scope requires an approved amendment, not a silent exception.
- Candidate exam view was using `Label` for passage and `audioFile`; rich image blocks were not an established feature in the examined source.
- The source document reports `exam_sessions.state=SCHEDULED` while attempts can be `ACTIVE`; precise start gate and deadline contract require resolution.
- The old tracker is not reliable evidence that every TODO is absent; the progress document records work across branches.

## Not verified by this planning package

- The current repo HEAD/PR state, latest migration number, correct licensing of third-party audio/images, completeness of the user-supplied HTML, whether any physical USB/LAN/multiple-display test has since run, approval of QD-21 changes, and current Windows runtime privileges. Agent must inspect these afresh before coding or stating completion.
- The proposed V2 tasks are **requirements**, not completed features, passed tests, or guaranteed results.

## New lecturer feedback (user-supplied transcript)

The lecturer warned about project difficulty and remaining time, explicitly confirmed the October 31 deadline, and asked what the group had actually built. The user additionally reports the lecturer wants **USB monitoring**. This plan treats USB storage monitoring as a requested acceptance feature, while staying honest that the quoted short transcript itself only directly captures the scope/deadline conversation.
