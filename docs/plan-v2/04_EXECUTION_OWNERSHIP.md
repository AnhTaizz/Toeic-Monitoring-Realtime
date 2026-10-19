# 04 - Ownership, parallel execution and dependency gates

## Ownership rule

| Role | Technical ownership | Human cross-review |
|---|---|---|
| A | Spring Boot, REST, PostgreSQL/Flyway, exam import/media/catalog, transaction/correctness, load | C reviews auth/concurrency; B reviews UI impact |
| B | JavaFX exam/audio/proctor UX, focus/minimize/display/activity observers, installer and real UI acceptance | A reviews exam/client contract; C reviews monitoring emitters |
| C | Process/USB observers, multi-signal WS transport, monitoring service/state, FULL/DELTA gate and experiments | B reviews proctor consumption; A reviews authorization and durable event storage |

Coding Agents may work under A/B/C but are **not an owner or approval authority**. Do not auto-assign unsigned tasks as DONE.

## Workstreams that can run in parallel

```text
A / Agent (licensed dataset + rich exam):
  BASE-01 -> EX-01 -> EX-02 -> EX-04 -> EX-03
                         EX-04 -> EX-05 -> EX-06 (B)
  BASE-04 + EX-02 + BASE-02 -> EX-07 -> EX-08

B / JavaFX:
  UI-01 (styling tokens) --------> UI-02 / UI-03 / UI-04
  EX-02 + EX-05 -----------------> EX-06 -> EX-08
  BASE-04 + MON-07 --------------> MON-03/MON-04/MON-05

C / Monitoring:
  BASE-01 -> BASE-02 -> BASE-03 (delta gate)
  BASE-04 + MON-01 -> MON-07 -> MON-02 (USB integration)
                             \-> MON-03/04/05 -> MON-09
                             \-> MON-08 (P1 only)

Final integration and evidence:
  EX-08 + UI-05 + MON-09 -> QA-01 -> QA-02 / QA-03
  MON-02..05 -> QA-04
  MON-01 -> QA-05; BASE-03 + MON-01 -> QA-06
  QA evidence -> REL-01/02/03 -> REL-04
```

Interpret these arrows as interface/finish gates, **not instructions to delay all exploratory coding**. A task can start its read-only audit, isolated spike, mock fixture or separate view/style work before prerequisites are complete. It cannot be marked integrated DONE before prerequisites are accepted.

## Coordination checkpoints by deliverable, not by date

- **Contract checkpoint:** A/B/C agree rich content representation, media endpoint scopes, start gate/deadline semantics, multi-signal JSON envelope, retention and observer lifetime. Update official docs before interface changes.
- **Vertical slice checkpoint:** one real image, one shared audio group and one multi-document passage render across PostgreSQL -> server -> JavaFX. Independent USB probe detects a genuine device event on Windows (without claiming server integration yet).
- **Network checkpoint:** USB + focus/display event genuinely reaches server + proctor with dedup and reconnect; no source contract regressions.
- **Release candidate checkpoint:** EX-08, MON-09 and key regressions accepted; freeze new features.
- **Final QA checkpoint:** physical LAN, hardware, fault, E1, conditional E2/load, installer and paper signed off.

## Suggested isolated PR topics (names are illustrative)

- `feat/exam-rich-data-validation`
- `feat/exam-media-storage-api`
- `feat/exam-catalog-start-gate`
- `style/javafx-exam-proctor`
- `feat/monitor-multisignal-protocol`
- `feat/monitor-usb-storage`
- `feat/monitor-focus-display`
- `feat/proctor-unified-timeline`
- `test/release-multiclient-acceptance`

Do not guess base SHA from a branch name. Confirm it at task start and limit each PR to a reviewable set of files.
