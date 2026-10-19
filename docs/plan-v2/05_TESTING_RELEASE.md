# 05 - Testing, experimental evidence and release

## Quality policy

- **UNIT** verifies pure logic and schema validation; **INTEGRATION** uses real PostgreSQL and real HTTP/WebSocket where applicable; **GUI** uses the packaged JavaFX app; **PHYSICAL** means actual USB, monitors and multiple LAN hosts. Mocks may validate logic, never physical phenomena.
- Every run: `testId`, `runId`, code SHA, owner/reviewer, OS/JDK/PostgreSQL versions, hardware, full command/steps, expected/actual, PASS/FAIL/PARTIAL/BLOCKED/NOT RUN, raw artifacts and limitations.
- Historical 17-Oct tests must be rerun or explicitly scoped to their old SHA; passing 924 local tests does not mean LAN/USB/Listening hearing was executed.

## Acceptance matrix

| Test family | Representative cases | Conditions |
|---|---|---|
| `EXM01` Exam data | 200 unique IDs; Part counts `6,25,39,30,30,16,54`; no missing audio/images/key; no invented text | Validate actual import and media manifest |
| `EXM02` Content/authorization | multi-image/long passage; user A cannot retrieve B attempt; candidate gets no answer key | HTTP + PG + JavaFX |
| `EXM03` Start gate | `SCHEDULED` candidate can prepare/READY but cannot improperly access/submit answers; authorized START unlocks | PG + HTTP + WS |
| `EXM04` End-to-end exam | 200Q navigation, save/submit/result, clock, retry and Listening interruption | Real packaged client |
| `USB01` Presence/plug/unplug | present before start, connect, remove, duplicate OS callbacks | Physical Windows USB storage |
| `USB02` Reliability | connection loss after observation, stable event retry, dedup, history after reconnect | Real WS + PG, physical USB |
| `FOCUS01` Focus/window | focus lost/restored; minimize/restore; fullscreen exit where active | JavaFX and manual actions |
| `DISPLAY01` Multi-display | display count before exam, add/remove second monitor | Physical monitor when available; else NOT RUN |
| `ACT01` Activity | idle/window metrics within exam scope only; no capture after exam ends | GUI/worker lifecycle tests |
| `MS-SEC01` Multi-signal security | bad role, other-attempt, replay with changed body, malformed payload | Real PG/WS |
| `MS-REL01` Multi-signal ACK | DB rollback does not produce success; reconnect reads committed history | Real PG/WS |
| `AT01-AT11` Existing answer invariants | auth, revision, writer takeover, save/submit/timeout lock order | Use existing repo test matrix |
| `MT01-MT08` Existing monitoring | FULL/DELTA oracle, epochs, replay, gap, presence and UNKNOWN | FULL default unless delta gate accepted |
| `LT01-LT04` Existing Listening | manifest, READY, start idempotency, no replay on interruption, new attempt | Real audio hearing is distinct from automated decoder tests |
| `IT01-IT04` Existing integration | installer, multiple clients, GUI responsiveness, DB/server restart | LAN/test machines and logs |

## E1: polling experiment

Existing plan: poll periods 250/500/1000/2000ms, 3 repeats each, controlled process lifespan scenarios and ground-truth observations. Measurements: detected vs missed, detection latency p50/p95 for detected events, resource coverage, collector CPU and memory. Use exact methods in `Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md` and `docs/E1_T4C1_MEASUREMENT.md`. Do not manipulate raw data or silently drop failed runs. Device/API blind spots must be reported.

## E2: conditional FULL vs DELTA experiment

No E2 claims until the correctness gate passes with comparable FULL/DELTA input, ACK/retry, event/heartbeat policy, measurement endpoints and window coverage. Existing plan calls for 3 trace profiles * 3 repeats * 2 modes = 18 runs if fully conducted. Count serialized JSON UTF-8, retries and control messages properly; never call those values total network-wire bandwidth. If the gate is incomplete, explain the blocker and collect defensible FULL-mode data instead.

## Concurrency/load

The existing plan specifies headless 1/5/10 clients with repeated runs and clearly distinguished JavaFX desktop acceptance. Capture RTT, success/errors, throughput and CPU/memory; respect machine limits. A real negative performance result is a valid result.

## Final handover

1. Clean clone/build/package; migrations/seed and config without secrets; no IDE required to run client.
2. Real data/media that is legally distributable and complete for the demo; checksum and paths documented.
3. Source/Report packaging according to the course `Submission.md`; project/group names filled by humans, never guessed.
4. `.docx` paper checked on Compilatio (course requirement: similarity + plagiarism <=20%), plus diagrams/tables backed by raw evidence.
5. Technical presentation: (a) proctor selects 200Q exam, (b) candidates run, (c) USB and focus events on remote proctor view, (d) network disconnect -> UNKNOWN -> resync, (e) compare measured E1 and optional E2.
6. Internal handover target 30-Oct; final official deadline 31-Oct-2026 23:59 ICT.
