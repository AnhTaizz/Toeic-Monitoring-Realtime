# 07 - Decisions to approve and fallback policy

> **Status (19-Oct-2026):** The team has approved the **overall V2 scope**, as reported by the user. ADR-V2-01/02 and the remaining entries below are **technical decision checkpoints**, not a claim that A/B/C have each approved exact wire fields, QD-21 clock semantics, OS API choice or data rights. See [adoption record](ADOPTION_STATUS_20261019.md).

> **Implementation reconciliation (22-Oct-2026):** See the [BASE-04 decision packet](BASE04_DECISION_PACKET_20261022.md). It records what is now integrated and recommends safe dispositions, while keeping A/C/B human decisions, data rights, retention policy and physical USB/LAN evidence explicitly pending.

## Explicit V2 change requests

| Decision | Existing reality / conflict | Proposed resolution | Owner |
|---|---|---|---|
| `ADR-V2-01` USB scope | `CLAUDE.md` explicitly excludes webcam/USB/OS locking | Permit **USB storage observation only** during an authorized, disclosed exam; no USB blocking/file reading. Amend approved contract with human signoff. | A + C |
| `ADR-V2-02` Dataset/media | Existing exam supports text/passage/audio but not ordered image blocks; audio root exists | Add narrowly scoped image/content metadata and authenticated delivery; keep audio contract intact. | A + B |
| `ADR-V2-03` Session start/deadline | `SCHEDULED` session may have `ACTIVE` monitoring attempts, and QD-21 starts deadline at takeover | Define proctor START gate and exact deadline policy with regression; do not silently change QD-21. | A + B |
| `ADR-V2-04` Event protocol | `PROCESS_OBSERVED` is a process event; FULL/DELTA is current process state | Add typed exam-activity event history with eventId/ACK/dedup; process state reducer unchanged. | C + A |
| `ADR-V2-05` Hardware approach | JDK ProcessHandle cannot read every Windows PnP event; USB sensor not implemented in original scope | Compare native Windows PnP with bounded WMI/CIM fallback; choose on actual hardware/no-admin evidence. | C + B |
| `ADR-V2-06` Parser language | Existing `CLAUDE.md` says Python only for CSV/plots | Use Java offline parser by default; propose a clearly approved exception before adding a Python parser. | A |
| `ADR-V2-07` Release branch | RC reports mention PR39-43 and other branch work but human approval/merge was incomplete at last check | Audit HEAD and PR state fresh; use pinned integrated SHA and record what actually passed. | A + C |
| `ADR-V2-08` Evidence / novelty | Delta code and replay exist, but physical LAN/E2 and human gate were not complete in the checked report | FULL default; E2 only after correctness, E1/fault-tolerance contributions supported by evidence. | C + A |

## Failure/fallback decisions

- **Missing actual TOEIC assets / answer key:** block dataset release; do not fill gaps with guessed original material. A permitted original/synthetic exam may be used as an explicitly labeled alternative, not misrepresented as the missing authentic exam.
- **Image feature affects Listening:** preserve current audio implementation and show a deterministic image placeholder/error; fix before EX-08 can be accepted.
- **USB PnP method unreliable:** document a limited WMI/CIM fallback, detection latency and class coverage; fail physical acceptance honestly if it does not satisfy required cases.
- **False positive focus/display event:** annotate sensor limitations and remove automatic accusation. Prefer review classification and human judgment.
- **DELTA gate failed or unreviewed:** FULL snapshot remains default; E2 improvement is not claimed.
- **No second physical machine:** do not call two app windows a LAN test; show the missing test as BLOCKED and seek a real host before release.
- **Freeze approached:** drop P1 cosmetic/idle/rules enhancements first; do not cut authorization, 200Q validity, USB scope, real network acceptance or report evidence.

## Privacy, integrity and distribution rules

- Explain monitoring types before the exam, make it active only for assigned candidate attempts, and shut observers down on completion/logout.
- Collect minimal event metadata needed for a proctor review and technical experiment. Do not store raw USB serials, file contents, key presses, URLs or screenshots by default.
- Session telemetry is not a tamper-proof anti-cheat guarantee. Missing observation may mean OS restrictions, missed polls, disconnection or unsupported hardware; explain this in the paper.
- Check rights before redistributing a third-party TOEIC exam, image or audio in a public repo or a submission package. Do not commit credentials or personal data.
