# T1-C2 — verification 04/10/2026

Codex Agent; Windows11 x64/JDK21.0.8/Maven3.9.11 qua terminal WSL. GUI manual, LAN/review B NOT RUN. Không benchmark E1; smoke/headless không được gọi là GUI acceptance.

## Git/source

Base main mới nhất sau fetch/pull: `9f018616fb6bdbe13a7d8214bfadc2fbf6fd5b53` (PR #3), branch `feat/t1-c2-process-collector`. Implementation `9621f73`; tests/harness `02789a86c1c9785bc54f31b4584751eae3340f10`. Commit tiếp theo chỉ docs/evidence. Không conflict; giữ task.txt untracked, không reset/force push. PR/merge SHA cuối xem final report.

## Collector và API C3

- Production code: client/src/main/java/vn/edu/toeic/client/monitoring; C1 monitoring-spike giữ độc lập.
- ProcessCollector.start(MonitoringSessionGate.Context, Consumer<ProcessSnapshot>, Consumer<Problem>) → collectorSessionId UUID. ID cố định trong một run, khác ở restart.
- stop() → CompletableFuture<Void>, hủy task/interrupt/shutdown, release listener/latest; future chờ poll/callback và worker. Caller await ngoài FX thread; không restart khi old run chưa kết thúc. close() terminal và khởi động stop; latestSnapshot() Optional.empty khi stop hoặc poll fail.
- ScheduledExecutorService-backed ScheduledThreadPoolExecutor riêng, thread toeic-process-collector, scheduleWithFixedDelay/default1000ms. Constructor Duration configurable; E1 chưa chạy.
- Callback worker, không Platform/UI trong core. Gate active CANDIDATE mới start; inactive candidate/proctor active/inactive đều denied trước worker/source side effects. Production chưa có monitoring-session trigger: không sửa/auto-start JavaFX login; active context test/smoke MOCK, không invent attempt.
- ProcessHandle.allProcesses stream được close; command/startInstant/user availability đọc safe. Chỉ giữ filename, nullable start và boolean availability, không full path/user/arguments/commandLine. Missing metadata → UNREADABLE; unknown command chỉ diagnostics, không suy diễn sạch. PID0 Windows idle được chấp nhận, không fail whole poll.
- Process-policy-v1 đúng QD-08: chrome.exe/msedge.exe/firefox.exe/zalo.exe/teams.exe/discord.exe/anydesk.exe/teamviewer.exe, case-insensitive executable filename, không mở rộng danh sách.
- ProcessSnapshot immutable: collectorSessionId, policyVersion, observationNanos, scanDurationNanos, Set<ObservedProcess>, Diagnostics(processesScanned/unreadable/missingCommand/missingStartInstant/missingUser).
- ProcessIdentity=(collectorSessionId,pid,startInstant nullable). Start khác hoặc collector restart khác identity; thiếu start không hứa phân biệt hoàn hảo PID reuse. C3 so theo identity, không so toàn bộ observation vì metadataQuality có thể đổi.
- System.nanoTime/local monotonic marker; Instant chỉ OS start metadata, không clock-sync evidence. SOURCE_FAILURE/LISTENER_FAILURE là enum an toàn; source fail clear latest, không publish empty clean snapshot; poll tiếp chạy. Diagnostic listener failure cũng isolate.

## Commands/results

```powershell
git fetch origin
git checkout main
git pull --ff-only origin main
git checkout -b feat/t1-c2-process-collector
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c2.ps1 -JavaHome <JDK21>
```

| Run | Code | Modules | Tests/failures/errors/skipped | Result |
|---|---|---|---|---|
| Final mvn test10:42:55 UTC+7 | 02789a8 | 5/5 | 175/0/0/0 | PASS |
| Final mvn package10:44:26 UTC+7 | 02789a8 | 5/5 | 175/0/0/0 | PASS |

Server51/client123/monitoring1. C2 thêm33 test (ProcessCollector14, ProcessPolicyAndSource19), baseline142 không được dùng thay175. Tests snapshot/source/context có nhãn MOCK; worker/latch/lifecycle thật. No-overlap giữ poll1 bằng latch qua nhiều interval; poll2 chỉ bắt đầu sau poll1 finish+fixed delay. Stop ngắt scan/await thread, no callback/later poll; non-cooperative fake source phải kết thúc trước restart. Gate kiểm side effects cả4 tổ hợp role/active.

Logs mvn-test/package.txt cùng thư mục: normalize CRLF/trailing whitespace, replace workspace/user-home/start-user bằng placeholders; không đổi kết quả đo. Warnings Mockito/ByteBuddy agent/CDS và Maven Shade module-info/MANIFEST overlap; build vẫn PASS. Client JAR chứa production collector, C2 Test/Smoke classes=0.

## Real ProcessHandle/owned demo

Smoke sau fix chạy trước commits trên đúng code sau đó commit02789a8; không chạy lại chỉ để đổi nhãn SHA. Windows11/JDK21.0.8, interval250ms, active monitoring context MOCK. `2026-10-04-process-smoke.txt` là stdout fixed sanitized của lượt PASS, stderr rỗng.

- Processes scanned378; unreadable162; missing command/startInstant/user đều162. Đây là sample poll tại máy chạy, không toàn bộ process luôn có các số này.
- Restricted filenames thực tế: discord.exe, msedge.exe, zalo.exe. Không dump process table/PID/user/path/arguments.
- Mở msedge.exe headless about:blank/profile tạm riêng; owned PID vắng ở baseline, xuất hiện với COMPLETE metadata, sau stop đúng owned root/descendants thì vắng ở snapshot mới. Không kill theo tên hoặc đụng browser user đã mở trước đó; temporary profile cleanup thành công.
- Stop/restart collector session mới, workers terminated, proctor denied PASS. Không GUI interaction.
- Smoke đầu FAIL khi source validation chỉ nhận pid>=1; xác nhận Windows idle PID0 tồn tại, nhận pid0/metadata UNREADABLE + regression test; smoke sau fix PASS. Không dùng lượt lỗi làm PASS và không giấu metadata unreadable.

## Scope/privacy/handoff

Privacy scan: `2026-10-04-security-scan.txt`; không .env/task/token/password/private full paths/process usernames/arguments trong diff/evidence. QD-08/QD-03, B2 adapter/JavaFX login, server/protocol DTO/spike/nguon/TRACKER giữ nguyên. PROTOCOL chỉ bổ sung local collector status, PROCESS_OBSERVED vẫn MOCK/CHƯA CÓ.

C3 sẽ consume ProcessSnapshot/Identity, diff set và thêm stable eventId/queue/retry/ACK handling, lấy context từ assignment thật, nối MonitoringTransport B2. C2 không gọi transport.send, không heartbeat/PROCESS_OBSERVED/eventId/retry queue/persistence/full/delta. A3 phải cung cấp scope/auth/persistence + commit-before-ACK. MT01 PARTIAL local C2; network state/event non-dup và các phần T2-C1/C2 chưa chạy. Không tự làm C3.
