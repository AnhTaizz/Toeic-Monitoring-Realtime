# MON-01?MON-09 ? b?n ki?m t?ch h?p TEST, 21/10/2026

?y quy?n ng??i d?ng t? attachment `100e8713`: code/test/docs tr?c ti?p, publish/review k? thu?t v? merge commit n?u CI/quy ??nh ??t. Kh?ng approval/ch? k? A/B/C, kh?ng task DONE ho?c gate/policy APPROVED. FULL m?c ??nh, signal/alert experiments OFF.

Base ???c fetch l? `65fa83b73d1c11ffc2257345cb94359cf4521cdf` (PR94); worktree ri?ng `test/monitoring-integrated-20261021`. Kh?ng nh?p nh?nh UI `fd3bc3b` hay c?c PR EX/UI ch?a merge. [B?o c?o/receipt](../evidence/monitoring-integrated-20261021/REPORT.md).

## Ma tr?n ??i chi?u source

T?n l?p d??i ??y l? class/method ?? ??c tr?n base v? b?n ki?m, kh?ng l?y t?n PR l?m tr?ng th?i ho?n t?t. M?i h?ng ph?n bi?t code c? s?n, ki?m k? thu?t v? nghi?m thu c?n thi?u.

| MON | Owner / reviewer | ?i?m n?i ch?nh | Production / TEST | Provenance trong l??t ki?m | Server/API/UI | Thi?u tr??c nghi?m thu |
|---|---|---|---|---|---|---|
| MON-01 | C / B | ProcessCollector.start ? CandidateMonitoringSession.start / FULL + MonitoringDelivery | Candidate explicit Start, FULL default | ProcessHandle th?t, ch? PID TEST; workload cmd ???c ??t t?n chrome.exe, kh?ng browser th?t | Events/state/roster/presence API v? timeline c? s?n; FULL ACK l? RAM, event ACK sau DB COMMIT | LAN/metadata/human B, BASE-02 pending |
| MON-02 | C / A | CimSnapshotSource.scan ? CimUsbScanSource (TEST) ? UsbScanCoordinator / UsbSignalAdapter | Provider ch? TEST; kh?ng candidate production binding | Snapshot CIM t?i thi?u ri?ng; connect/remove MOCK, weak slot identity | Typed history/status/timeline/summary qua pipeline chung | USB0/12 NOT_RUN; PnP comparison/ADR-V2-05/owner decision |
| MON-03 | B / C | CandidateExamSignalMonitoring.start ? FxFocusMonitor.attach / FocusSignalAdapter | Candidate opt-in trong m?n thi | JavaFX listeners th?t; activation programmatic, kh?ng Alt+Tab th?t | EXAM_FOCUS_CHANGED qua committed history/timeline/experimental rule | Desktop/human focus/dialog acceptance |
| MON-04 | B / C | FxWindowMonitor.attach / WindowSignalAdapter | C?ng candidate opt-in/context/queue | Stage minimize/restore b?ng property API, kh?ng OS click | EXAM_WINDOW_STATE baseline/CHANGED; kh?ng kiosk/ph?t | Desktop minimize/fullscreen/close acceptance |
| MON-05 | B / C | FxDisplayMonitor.attach ? Screen.getScreens().size / DisplaySignalAdapter | C?ng candidate opt-in/context/status owner | Count JavaFX hi?n t?i th?t; burst display l? seam SIMULATED ri?ng | DISPLAY_COUNT_OBSERVED + OBSERVER_STATUS; kh?ng suy c?u h?nh v?t l? ??y ?? | Physical second-display NOT_RUN |
| MON-06 | B / C | FxActivityMonitor.attach ? ExamActivityEvent / ActivitySignalAdapter | C?ng candidate opt-in; marker legacy/rich ?? c? | Marker NAVIGATION/ANSWER_ACTION programmatic; kh?ng thu n?i dung/ph?m | EXAM_ACTIVITY_SUMMARY privacy-minimal, server/parser/history/UI ?? c? | Human exam activity/idle field acceptance; rule policy pending |
| MON-07 | C / A | SignalRunContext.reserve ? SignalDelivery.tick ? SignalService.store/history | Opt-in OFF default; m?t typed context/queue/socket, process lane ri?ng | REAL WS/HTTP/PG TEST; ngu?n ph?n nh?n; ACK loss/fault SIMULATED trong regression | COMMIT/ACK/dedupe/cursor/auth/caps/status/gap ?? c? | Owner protocol/policy; offline60s/retention30ng?y ch?a duy?t; kh?ng DONE |
| MON-08 | C / B | AlertService.read ? AlertRules.evaluate ? TimelineController | Hai experiment ??u ph?i explicit; repository OFF | Read model committed TEST; threshold EXPERIMENTAL | Window300s/bucket60s, cap TEST20/default200, count event kh?ng verdict | Owner thresholds/false-positive budget/policy approval |
| MON-09 | B / C | MonitoringApiClient ? SignalHistory / TimelineRows ? MultiSignalTimelinePane | Assigned PROCTOR; kh?ng t?o collector/provider | JavaFX controls/Scene + HTTP polling th?t, loopback reconnect | Raw sort/dedupe/time labels + state/presence/Listening status + alert window ri?ng | OS desktop/LAN NOT_RUN; Listening interruption projection MOCK kh?ng playback E2E |

## Demo th?ng nh?t

Gi? entrypoint hi?n c?, kh?ng th?m m?t b? provisioning/demo th? hai:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot' # ho?c JDK21 c?a m?y
.\scripts\demo-mon08-alerts.ps1 -Integrated
# CIM ch? ??c t?i thi?u, explicit; ??y v?n l? l??t mixed provenance:
.\scripts\demo-mon08-alerts.ps1 -Integrated -Mode Real -ReadUsbStorage
```

C?n Windows desktop JavaFX, JDK21, Maven tr?n PATH, PostgreSQL18 binaries (default `C:\Program Files\PostgreSQL\18\bin`, override `-PostgresBin`). Kh?ng c?n DB dev, `.env`, USB th?t ho?c m?y th? hai. TEST DB/schema/port/SCRAM/password RAM do runner d?ng; accounts qua seeder, ??/session/assignment qua API. Flyway ?p DDL v?o TEST ngay c? n?u feature flags OFF, kh?ng d?ng OFF ?? b?o v? DB chung.

`-Integrated` b?t ??ng signal/alert trong TEST; kh?ng s?a defaults. Candidate v? proctor d?ng c?ng reactor classpath/build t? SHA ???c ghi trong `config.json`; kh?ng ch?y JAR c?. C?c mode c? v?n gi?: kh?ng `-Integrated` l? demo MON08 tr??c ??; `demo-usb-integrated.ps1 -Mode Mock` gi? b?n regression USB.

Expected: Stage candidate ngu?n hi?n h?u ch?y; assigned proctor m? timeline/summary th?y process, focus/window/display/activity, USB/status/gap ph? h?p provenance. Demo cho xem m?c ??nh20s (`-ViewSeconds 0..120`) ? b??c coverage, r?i t? ti?p t?c assert/teardown. L??t app th?t ri?ng trong c?ng fixture d?ng MenuItem/Button.fire ?? login proctor ? candidate A Start?Stop?Start ? logout ? candidate B ? Application.stop. Kh?ng t? READY/START b?i thi th?t ho?c ph?t audio. Process workload l? cmd t? s? h?u mang filename chrome.exe ?? ?i qua policy hi?n c?; kh?ng ??i policy hay gi? browser th?t.

D? li?u USB connect/remove ch? MOCK; Real mode thay ngu?n USB b?ng CIM, kh?ng th?m ngu?n ho?c t? resume coordinator sau reconnect. C? mode v?n d?ng account/?? TEST v? marker/burst programmatic. Kh?ng g?i c? l??t REAL. Burst50 display messages l? seam SIMULATED ?? t?o overflow h?u h?n, kh?ng ph?i thay ??i m?n h?nh hay thao t?c ng??i d?ng. Count JavaFX th?t v? burst ???c ph?n bi?t trong receipt.

TEST override: process poll250ms, activity report5000ms, typed data capacity16 + reserved gap1, alert source-cap20. Defaults production gi? poll1000ms/activity30000ms/data256 + reserved1/source-cap200, 1MiB data +8192-byte gap, hai flight/write. Provider v?n default query5000ms/delay1000ms/duration15s h?u h?n; kh?ng t?ng timeout m?c ??nh.

Xem `target/usb-integrated-<run>/result.json`, `demo-summary.json`, `config.json`, `reports.private`, private logs/Scene snapshot. C? th? ch? ??nh `-OutputDirectory` m?i d??i checkout `target`; t?n t?i th? t? ch?i ghi ??. Summary c? source SHA, dirty flag, t?ng ngu?n/run, raw/cap/drop, cleanup. JAR hashes c?a l??t c?ng b? n?m trong receipt li?n k?t; runner demo d?ng compiled classpath, kh?ng g?i ?? l? packaged EXE acceptance.

??ng c?a s? kh?ng thay vi?c d?n: runner h?u h?n t? close/await source/readers/executors, collector stop future, RT worker, fixture HTTP/server, DROP schema v? stop cluster own path. Ki?m receipt0schema/PGstop0/PIDabsent v? JUnit tr??c khi g?i PASS. `stop.request` trong output cho d?ng c? ki?m so?t t?i ?i?m quan s?t; Ctrl+C/OS close/manual desktop ch?a nghi?m thu, kh?ng coi m?t l?n window bi?n m?t l? cleanup. Kh?ng kill theo t?n ti?n tr?nh ho?c root clean.

## Gi?i h?n gi? nguy?n

USB physical0/12, physical second-display, desktop manual v? LAN0/41 NOT_RUN. Listening ch? projection/status conditional ?? c?, kh?ng tuy?n b? playback/interruption E2E. MON01?09 ch?a ???c chuy?n DONE. C?n nh?m quy?t policy/thresholds/false positives, ADR provider, offline/retention v? gate DELTA (NOT_DECIDED, FULL gi? m?c ??nh). Kh?ng tri?n khai observer production USB, retention scheduler, rule/protocol m?i, E1/E2 ho?c EX/UI ngo?i ph?m vi. Human owner review v?n pending; Codex review ch? l? review k? thu?t theo ?y quy?n.
