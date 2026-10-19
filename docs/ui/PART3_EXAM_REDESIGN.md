# Listening Part 3 — focused JavaFX presentation

Baseline: `main` at `f98a37f73d8035e9386e930412a02e9e92933015` (2026-10-19).

## Scope

Only Part 3 selects `ListeningPart3Pane`. The existing `CandidateExamView` is retained for all other Parts. No HTTP, WebSocket, DB schema or source exam data was changed.

## UI

- Academic white surfaces, warm gray backdrop, restrained orange, Calibri with safe fallbacks; local stylesheet `part3-exam.css`.
- Approximately one-third left for Part, three-question navigation and group answered status; two-thirds right for real question texts and four vertical answer choices.
- No decorative mascot in work area and no transcript/passage text. Header remains the application's existing authenticated header.
- Audio uses the *same* `AudioPreparationView` and `ListeningPlaybackPane`, shown compact in Part 3 only. There is no new play/pause/seek/replay control or fabricated progress bar. The player status is the real status.
- When the existing playback coordinator changes to a Part 3 audio segment, its mapped question IDs select the corresponding actual exam group.
- Answer cells dispatch to the existing `ExamSession.selectAnswer`, clear to `ExamSession.clearAnswer`, and lock based on `ExamSession.canEditQuestion`.
- The 3 question buttons navigate **within the visible group**, not to other audio files. `answered` explicitly means local answer present, not server-saved.
- Live answer ACK refresh updates styling without rebuilding choices; network/outage, timer, submit and writer takeover remain owned by the parent view.

## Files

- New `client/src/main/java/vn/edu/toeic/client/exam/ListeningPart3Pane.java`
- New `client/src/main/resources/part3-exam.css`
- Modified `client/src/main/java/vn/edu/toeic/client/exam/CandidateExamView.java`
- Modified `client/src/main/java/vn/edu/toeic/client/audio/AudioPreparationView.java`
- Modified `client/src/main/java/vn/edu/toeic/client/audio/ListeningPlaybackPane.java`

## Verification status

Source review was performed against GitHub main. Automated Maven build, JavaFX screenshot smoke, real PostgreSQL/Listening playback, Windows 125% scaling and 1366x768 manual layout **NOT RUN** in this environment (Maven and full repository/dependency checkout unavailable). Do not merge without those checks.

### Recommended local checks

```powershell
git fetch origin
git switch ui/part3-exam-focused-20261019
mvn -pl client -am test
mvn -pl client -am -DskipTests package
```

Run a manual full Listening Part 2 → Part 3 → Part 4 case; verify no duplicate audio, answer ACK, loss/recovery behavior, GUI at 1366x768, 1600x900, and 1920x1080. Check wrong-role server protection as before.

### Important known limitations

- The parent view's older top bar, outer page styling and submission/footer design are deliberately unchanged to avoid modifying unrelated screens. A shared design-system migration is a separate task.
- Listening instructions are still controlled by imported exam audio and existing playback metadata. This PR does not add new instruction files or redefine the exam timeline.
- The original fixed 380px playback component is reused in compact mode as a node and moved to the Part 3 audio area; this should be smoke-tested through playback start, reconnection, and end.
