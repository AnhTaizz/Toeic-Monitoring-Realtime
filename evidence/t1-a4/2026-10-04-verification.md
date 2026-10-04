# T1-A4 — kiểm chứng ngày 04/10/2026

Người chạy: Codex Agent. CODE_COMPLETE phần A4, chưa nghiệm thu toàn GUI/prototype. Human C review, GUI manual, LAN/máy Windows thứ hai và package trên máy khác: **NOT RUN**. Không dùng review Agent thay review thành viên. Người dùng trước đó đã chọn không kết nối ChatGPT; không tuyên bố ChatGPT lập kế hoạch/rà mã.

## Git và code được kiểm

- Base main sau fetch/pull: `99b4f39d5bcf93f2f8c7dfba172d0df68ffa7cfa` (PR#6 C3).
- Branch: `feat/t1-a4-monitoring-presence`, tạo mới trên working tree sạch; nhánh cũ/.env giữ nguyên.
- Client hook commit: `d61ac98b5b3847c7d9dfcb5f552d6a34824b923e`.
- Server/tests/harness commit: `3f61c0720288f2ef8f773357705384b971b0a699`.
- Tests chạy trước commit trên cùng source; 21 changed source/script files có SHA256 giống checkout đã build (security-scan). Docs/evidence commit sau code, Git push/PR/merge SHA cuối trong report/PR, không đoán SHA chưa tạo.
- Không reset/force push/code trực tiếp main. TRACKER.json/nguon/plans01–03, V1/V2/V3 không đổi. `.env`/task.txt không tracked/commit. Nhánh A4 được giữ sau merge nếu technical gate đạt.

## Môi trường, build riêng và log

Windows11 amd64, Eclipse Temurin21.0.10+7, Maven3.9.15, PostgreSQL18.6 Docker Compose service db. Dùng local Maven cache có sẵn; flags thực tế `-o -B -ntp -Dmaven.repo.local=<local-cache>`. Log đã bỏ private workspace/user/JDK path, chuẩn UTF8 LF không BOM.

Server dev của người dùng đang chạy và giữ JAR trên Windows. Lượt `mvn package` tại workspace chính không rename được JAR khi repackage. Không dừng PID/server/client/Edge người dùng. Tạo detached checkout build riêng tại `<workspace>/server/target/a4-worktree` từ base, copy đúng file task và `.env` private ignored. Không sửa POM/workspace để né lỗi; thư mục build riêng ignored và vẫn giữ artifact kiểm chứng. Main workspace đang chạy **chưa áp dụng A4**; public schema không được migrate V4 trong test. Cần người dùng dừng server dev và build/start bản mới khi muốn dùng A4; Flyway nâng V4, không reset DB.

| Lượt | Kết quả thật | Thời gian UTC+7 | Log |
|---|---|---|---|
| Baseline `mvn test` trên main99b4f39 | PASS253/253, client161/server91/spike1 | 20:48:02 | [baseline-test.txt](baseline-test.txt) |
| Final `mvn test` trong build checkout | PASS277/277, client166/server110/spike1; 0 failure/error/skipped, 5 module SUCCESS | 21:26:27 | [mvn-test.txt](mvn-test.txt) |
| Final `mvn package` trong build checkout | PASS277/277; server Boot JAR/client fat JAR tạo được | 21:28:28 | [mvn-package.txt](mvn-package.txt) |
| `scripts/smoke-a4.ps1` | PASS REAL PostgreSQL/Spring/HTTP/WS/B2/C3; nguồn process MOCK | Bắt đầu21:23:04, xong sau21:23:28 | [real-smoke.txt](real-smoke.txt) |
| `scripts/smoke-a3.ps1` hồi quy final | PASS REAL | Bắt đầu21:29:53 | [a3-regression.txt](a3-regression.txt) |
| `scripts/smoke-c3.ps1` hồi quy final | PASS REAL Windows ProcessHandle/Edge/B2/C3/DB | Bắt đầu21:29:53 | [c3-regression.txt](c3-regression.txt) |
| Secret/protected/source/JAR scan | PASS | Sau code/docs | [security-scan.txt](security-scan.txt) |

Số277 lấy summary Maven, không cộng báo cáo XML cũ trong target. Tăng24: client5 (adapter lease4, coordinator1), server19 (presence12, malformed scoped4, unscoped1, proctor1, concurrency decorator thêm1). A4 smoke dùng source/harness A4 giống final package; giữa smoke và final package chỉ đổi A3 transport fixture và C3 cleanup harness. Final test trước đổi FQN IOException thành import tương đương; package chạy lại đầy đủ277 trên final source. Các lần lỗi trước đó không tính PASS.

Chạy lại từ checkout có JAR không bị khóa, DB đang chạy và `.env` của máy đó:

```powershell
mvn test
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a4.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a3.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-c3.ps1
```

Smoke không tự start/reset/stop database. A4 dùng schema `a4_test_<UUID>`; A3/C3 schema UUID riêng và cleanup do harness sở hữu. Cuối phiên query read-only xác nhận không còn schema test của các run; public presence table chưa có (dev vẫn V3). Không seed attempt production tự động.

## Contract và cấu hình đã kiểm

Production: heartbeat2.000ms, timeout6.000ms, scan500ms; max tracked attempts4096, associations8/attempt. Real A4 harness: heartbeat100ms, timeout700ms, scan25ms để chạy nhanh; **không báo đây là đo default6s hoặc LAN**. Service unit fake ticker kiểm chính xác trước6s/quá6s; wall clock +20 ngày/-40 ngày không thay deadline.

`CandidateMonitoringSession` explicit start sau fresh role/scope, lấy UUID thật từ `ProcessCollector.start` và gắn `MonitoringTransport.monitoringHeartbeat`. RealtimeClient binding lease CAS + guard callback, gửi ngay nếu CONNECTED. Stop/logout/switch/mất quyền gỡ scoped lease; old lease không gỡ phiên mới. Socket reconnect cùng run giữ UUID; không bật collector đã Stop. Unscoped ACK chỉ ping transport, không tạo/refresh presence. Không có STOP message, Stop dẫn tới UNKNOWN khi deadline của heartbeat cuối hết; heartbeat đang truyền có thể được chấp nhận sau nút Stop.

`RealtimeWebSocketHandler` revalidate auth/role/candidate ACTIVE scope, validation payload rồi gọi service; không đặt scheduler/DB vào handler. Scoped payload chỉ sentAt Instant và collectorSessionId ID bắt buộc. Proctor/foreign/CLOSED/unknown/malformed/expired/revoked không refresh. Event/gap C3 không dùng thay heartbeat.

`MonitoringPresenceService` aggregate association socket/collector theo attempt, max bounded, per-attempt stripe + durable revision CAS. Tick nanoTime chỉ cùng JVM; UTC server microsecond lưu DB/UI, không sentAt client. Các heartbeat/timeout trên cùng attempt tuần tự, push ngoài stripe/DB lock sau proxy commit. Scan định kỳ dọn expired/revoked/CLOSED; một attempt lỗi không dừng scan các attempt khác. Prototype một server JVM writer, không multi-server reducer.

V4 additive: monitoring_presence snapshot/revision, monitoring_interruptions stable UUID/collector/socket/lastSeen/timeoutDetected/recoveredAt; unique transition(attempt,revision), một open interruption/attempt. V3 monitoring_gaps QUEUE_OVERFLOW không đổi. Một timeout ghi một record, phục hồi giữ ID/history và ghi recoveredAt. Startup demote ONLINE cũ→UNKNOWN/SERVER_RESTART, revision tăng, detection null; không nanoTime cũ/fake downtime gap.

## Kết quả có chứng cứ

| Trường hợp | Kết quả | Loại bằng chứng |
|---|---|---|
| Scoped HB ONLINE; unscoped/proctor không làm candidate ONLINE | PASS | Unit MOCK + REAL network/DB |
| Malformed/missing/extra field, role/foreign/CLOSED/unknown không refresh | PASS | Network fixture MOCK stores + REAL PostgreSQL smoke |
| Threshold6s/UTC timestamps/repeated scan một gap, capacity/slot release | PASS | MOCK clock/ticker/committed store |
| HB vs timeout đang in-flight, HB mới trước old scan | PASS | Fake ticker + latch/thread controls, không long sleep race |
| Hai candidate explicit start, đúng UUID thật, ONLINE | PASS | Production B2/C3/C2 lifecycle, MOCK process source, REAL network |
| Child A JVM hard-kill do parent tạo; không final app message | PASS | REAL destroyForcibly owned Process; no user Java/Edge kill |
| A UNKNOWN/history1, B ONLINE/event commit/ACK | PASS | REAL PostgreSQL/WS/B2/C3, B process observation MOCK |
| Assigned proctor nhận ONLINE/UNKNOWN, proctor khác không nhận | PASS | REAL WS probes; không GUI B3 |
| Recovery giữ stable gapId/recoveredAt; reconnect giữ collector | PASS | REAL child restart/socket reconnect/DB |
| Old socket abort/expiry không làm live A UNKNOWN | PASS | REAL additional socket; repeated live B heartbeats vượt timeout |
| Token revoked và attempt CLOSED dọn association, không fake timeout gap | PASS | REAL login_sessions/attempt mutation TEST only |
| Deferred heartbeat COMMIT failure | PASS | PostgreSQL constraint trigger cuối transaction: ERROR retryable, row0, không success ACK/ONLINE push |
| Deferred timeout COMMIT failure, scheduler retry | PASS | Rollback UNKNOWN+gap, không UNKNOWN push; B tiếp tục ONLINE; bỏ trigger, retry commit đúng gap1 |
| Stop B unbind, proctor offline → HTTP roster/history vẫn đúng | PASS | REAL B2 lease/unscoped pings/DB/HTTP |
| Server restart từ persisted ONLINE, history giữ | PASS | Restart owned Spring context, UNKNOWN/SERVER_RESTART detection null/no fake gap, scoped HB mới ONLINE |
| ACK/warning/presence không raw-send overlap | PASS | Controlled latch/decorator MOCK raw session, serializer Instant đã sửa |
| Worker cleanup presence/collector/delivery/B2/child-reader | PASS | REAL thread termination check, owned child shutdown |
| A3 events dedup/conflict/concurrent retry/timeline/live auth scope/COMMIT failure | PASS | REAL PostgreSQL/Spring/WS hồi quy |
| C3 ProcessHandle/owned Edge/events/queue/retry/overflow/history | PASS | REAL collector/network/DB; event/gap ACK loss SIMULATED, overflow snapshots MOCK |

Thời điểm timeoutDetected là lúc server phát hiện, không lúc client thật ngắt. ONLINE không chứng minh collector chống sửa hay quan sát mọi process; UNKNOWN không bằng process warning/gian lận. MT08 whole case **PARTIAL** do GUI B3 chưa có; thành phần server hard-kill PASS. Không nâng PASS toàn IT02/IT03 hoặc gate08/10.

## Lỗi trước final PASS và giới hạn cleanup

- Main package bị Windows giữ JAR → dùng checkout build riêng, giữ server người dùng.
- Initial unit decorator case PRESENCE chỉ1 write: Gson phản chiếu Instant không hợp lệ trên JDK21. Thêm adapter ISO UTC, final case ACK/WARNING/PRESENCE đều PASS.
- Initial A3 regression gửi scoped transport ping thiếu collector: chỉnh fixture sang unscoped đúng mục đích transport, không bỏ scope/event denial assertions.
- Initial C3 regression Windows chưa nhả profile handle sau process exit: bounded10s cleanup retry trong path đã xác minh thuộc temp profile do harness tạo; final C3 run cleanup PASS.
- Một profile từ lượt C3 lỗi (`toeic-c3-owned-edge-9207720505247423889`) còn trong temp. Lệnh cleanup riêng bị automatic approval review từ chối với lý do duy nhất `blocked by policy`; không tiếp tục xóa. Query process xác nhận không Edge đang dùng profile này; final-run workers/profile đã dọn. Đây là file tạm sót, không worker còn chạy/DB dữ liệu bị mất. Không che giấu lượt lỗi hoặc nhận cleanup của lượt lỗi là PASS.
- Không tắt DB dùng chung để test DB-off: **NOT RUN**; deferred COMMIT fault thật chỉ ở TEST schema không thay DB-off test.

## Security và bàn giao

Secret scan đọc local DB/seed passwords trong memory rồi kiểm added diff/new files, không in value. Những public defaults pre-existing trong application.properties không phải credential mới thêm; không sửa auth default trong A4. Scan raw Bearer/private paths/NUL; test negative path synthetic TEST được phân biệt. .env/task không tracked; migrationsV1–V3 và kế hoạch protected giữ nguyên. Client/server JAR không Test/Smoke classes, server không client test dependency.

Cross-owner: B2 RealtimeClient/MonitoringTransport, C3 CandidateMonitoringSession, harness wrapper forwarding lease; không sửa collector process/policy/event payload/queue budgets hoặc dashboard. README/PROTOCOL/TIEN_DO/NHAT_KY append/KIEM_THU/VAI_A/B/C/QD10 cập nhật.

B3 nhận:

- GET `/api/v1/monitoring/attempts`: PROCTOR, ACTIVE assigned, minimal candidate identity + snapshot.
- `MONITOR_PRESENCE`: envelope attemptId, payload cùng snapshot item, revision per-attempt.
- GET `.../{attemptId}/interruptions`: scoped server timeout history; recoveredAt refresh theo gapId.
- `MONITOR_WARNING`/events timeline A3 giữ nguyên, dedupe(attempt,event).
- WS buffer + HTTP read/re-read after handshake/reconnect, chỉ apply presence revision lớn hơn; refresh roster để bỏ scope bị thu, dashboard offline hiện stale riêng. UNKNOWN/NOT_SEEN revision0 không fake interruption.
- B cần implement dashboard/parser/reconciliation/UI stale/history; A4 không làm B3 hoặc chặng2.

Remaining acceptance: human C review, B3 dashboard/GUI manual, LAN hai máy và package chạy máy khác. Task cho phép merge commit khi technical PASS dù các mục này NOT RUN. PR/merge state xem final report; dừng A4.
