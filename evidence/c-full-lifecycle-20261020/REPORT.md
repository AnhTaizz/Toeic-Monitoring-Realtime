# FULL lifecycle trên một Windows — 20/10/2026

**TECHNICAL_PROGRAMMATIC_VERIFIED; DESKTOP_INPUT_NOT_RUN.** Không gọi toàn bộ GUI desktop
PASS. Hai production Application/Stage được điều khiển bằng FX controls trong một JVM;
HTTP/WS/PostgreSQL thật trên loopback. Không component-only/headless substitute, nhưng
không phải OS mouse/keyboard, hai candidate JVM hay người thật thao tác/nghe. Native Computer
Use trả `native pipe unavailable (os error2)` cả trước/sau retry/reset; không dùng PowerShell
UI Automation để vượt giới hạn. User đã được cung cấp nhóm thao tác ngắn cho hai JAR desktop
riêng; chưa có xác nhận thao tác thì giữ NOT_RUN, không lấy process đang chạy làm GUI PASS.

## Nguồn, build và TEST

Main fetched `5bbeaf2399490f642e71456fdce93a82907b2b8d` sau PR80; worktree/branch riêng
`test/c-full-gui-lifecycle-20261020`, root main và công việc khác giữ nguyên. Đọc CLAUDE,
hướng dẫn user/TIEN_DO/NHAT_KY/VAI_C/PROTOCOL, MON01/handoff và kit report/runbook.
User opt out codex-with-chatgpt vẫn giữ. Có dùng computer-use skill để kiểm kết nối, không
tuyên bố công cụ đã nhìn/bấm Windows khi native pipe không có.

Windows11 x64 build26200, token thường, Temurin21.0.10+7/Maven3.9.15/PowerShell5.1,
PostgreSQL18.3 native. `mvn --batch-mode --no-transfer-progress -DskipTests package`
trên **main5bbeaf2** tạo server/client JAR dùng cho lượt này; checksum tại [receipt](receipt.json).
Compile scenario qua `mvn -pl client -am -DskipTests test-compile`; classpath là **client
all.jar trước test-classes**, không client/target/classes thay JAR. Production factory
`new ToeicClientApplication()`/`ProcessHandleSnapshotSource`, không observer MOCK injection.

Mỗi run tự initdb SCRAM trên loopback/port cấp động, user/DB TEST riêng và password random
process-only; `createdb`, Flyway vào schema `integration_gui_<random>` riêng. V13 có chạy dù
MON07 OFF; không DB dev/chung, `.env`, reset hoặc root clean. Seeder cấp candidate1/2/proctor1;
`ExamApiClient.importExam/createSession` tạo đề Reading TEST và assignments bằng API có auth.
Fixture smoke chuẩn bị thêm WAV **TEST SYNTHETIC**/Listening TEST, nhưng lượt lifecycle
không phát audio/xác nhận nghe. Không SQL chèn attempt/đáp án/transition. SQL chỉ tạo/drop
schema fixture và kiểm seed/cleanup. Timeout tạo session duration8s qua API, server tự chốt.

FULL và signal experiment OFF explicit; poll1000ms, heartbeat2000ms, presence/state timeout
6000ms, full refresh2000ms/full reconciliation30000ms theo default. Không bật DELTA/seam/fault
trong lượt lifecycle. 215 unit regressions dùng transport/source MOCK, ghi riêng dưới đây.

## Actual theo case

Lượt cuối `corrected-ownership/9699928cfdd9`, scenario source
`47caa50faa1a44157e7a9f6c70a4b382b0a10d13`, production source5bbeaf2. Mỗi checkpoint có
JSON scalar + ảnh Scene thật + `jcmd <ownedPID> Thread.print` raw private. Test kiểm điều kiện
bằng polling hữu hạn60s, không suy worker dừng từ cửa sổ mất hoặc một lần sleep.
Ảnh [candidate FULL](candidate-full.png) là snapshot Scene production đã rà: chỉ TEST identity,
không password/token/answers/IP/hostname thật. Ảnh proctor có các process quan sát thật nên
giữ private. [Receipt](receipt.json) liệt kê hash/raw availability, không link giả ổ local.

| Case | Thao tác/expected từ implementation | Actual mới | Trạng thái trong phạm vi lập trình / desktop |
|---|---|---|---|
| A Login/Start | Candidate được cấp scope rồi Start; proctor không collector; FULL; ProcessHandle→current/history | Proctor login đầu tiên worker monitoring0; candidate Start có3worker/FULL/SYNCED. Edge headless TEST do run sở hữu khớp PID/startInstant độc lập; assigned API có history, dashboard hiển thị đúng current PID và event | PASS kỹ thuật; desktop NOT_RUN |
| B Stop–Start | Stop bounded, không overlap; epoch mới; server không buộc UNKNOWN ngay | Worker3→0; server state STALE/sequence4 sau CLOSE, không áp UNKNOWN tức thì. Start epoch mới/sequence1,3worker mới, IDs disjoint worker cũ | PASS kỹ thuật; old callback/ACK injection chỉ được kiểm MOCK riêng; desktop NOT_RUN |
| C Logout/relogin | Logout invalidate/stop, fresh scope/account | Worker về0, login screen; đăng nhập candidate2, API tạo scope riêng, FULL/SYNCED/collector ID mới; logout về0 rồi candidate1 vào lại attempt cũ. Không UI cũ tái mở trong lượt | PASS kỹ thuật cho scope/worker/identity; không claim exhaustive delayed-network proof hoặc desktop PASS |
| D Submit | Confirm submit qua control thật, server COMMIT/final, dừng monitoring | `exam-submit` + dialog YES qua FX; API owner trả SUBMITTED, worker0; result Stage thật rồi thoát về portal | PASS kỹ thuật; không gọi Button.fire là OS click/human submit |
| E Timeout | Attempt mới8s, không đổi clock/ép DB | API tạo session8s, entry/FULL baseline worker3; server trả TIMED_OUT tự nhiên; result Stage thật, worker0 | PASS kỹ thuật; desktop NOT_RUN |
| F Exit | Stop app/close stage và workers/executors; phân biệt HTTP fixture | Production `Application.stop()` rồi Stage.close; worker3→0; dump cuối **0 thread toeic-*** sau đóng rõ API fixture. Cả Stage không showing, JVM smoke thoát0 | PASS Application.stop kỹ thuật; OS window-close/Launcher shutdown hook desktop NOT_RUN |

Checkpoint A/B/C/D/E/F cuối là10 checkpoints, **không phải10 JUnit test**, cũng không cộng
run baseline/lượt lặp thành test độc nhất. Thu10ảnh/10thread dump local chỉ phục vụ actual.
Không khẳng định mọi event user-account crossing hoặc mọi delayed packet đã được thử bằng
network fault thật; scoped generation/correlation checks là evidence MOCK bổ sung.

## FAIL giữ lại và bản sửa

Không tìm được lỗi production tái hiện trong scope đã chạy. Lượt mở rộng
`process-main/6f5c1f3e204b`, source49007cf, **FAIL** `Owned application executors terminate`.
Thread dump trong lúc chờ còn4 `toeic-exam-http`, monitoring đã0. `ExamApiClient` dùng pool2
lazily; candidate API **fixture** chỉ tạo2thread sau census đầu, nên census ban đầu không
phân biệt được chúng với app. Fixture chưa được close trước assert Exit.

Sửa **test ownership**, không sửa expected/timeout/production: close hai API fixture trước
app-stop/assert executor; nguồn47caa50 chạy lại đúng ca, còn0 `toeic-*`, exit0. Giữ trọn FAIL
receipt/log/dump và PASS mới riêng. Baseline cb021b7 trước đó chỉ assert monitoring worker,
đã PASS nhưng không lấy làm proof executor ownership. Không rerun mù tới PASS.

## Hồi quy, lệnh và giới hạn

```text
mvn -pl client -am -Dtest=CandidateModeSessionTest,FullSnapshotDeliveryTest,MonitoringDeliveryTest,RealtimeClientTest,ProcessCollectorTest,ExamSessionTest,ExamEntryTest,DashboardControllerTest,DashboardModelTest -Dsurefire.failIfNoSpecifiedTests=false test
```

**215 PASS/0FAIL/ERROR/SKIP**; source47caa50. Khi nhập PowerShell phải quote nguyên mỗi `-D...`
argument. Bao gồm old-socket send plans, close correlation, old heartbeat lease, generation
gates, no-overlap/stop/final-state và stale consumer. MOCK không thay proof mạng/desktop thật.

Scenario tái chạy trên TEST đã init/createdb riêng, env DB_HOST/PORT/NAME/USER/PASSWORD và
TOEIC_SEED_PASSWORD process-only do người chạy sở hữu, không đọc `.env`:

```text
java -Duser.timezone=UTC -Dtoeic.realtime.delta-experimental=false -Dprism.order=sw -Dintegration.fullLifecycle=true -Dintegration.runtimeSource=<runtime-SHA> -Dintegration.output=<NEW-ignored-output> -Duser.home=<NEW-test-profile> -cp "client/target/client-0.1.0-SNAPSHOT-all.jar;client/target/test-classes;<PostgreSQL-JDBC-jar>" vn.edu.toeic.client.integration.ReadingListeningPostgresGuiSmoke
```

Raw runner trong target được hash ở receipt, dùng initdb/pg_ctl/createdb native SCRAM,
restore env ở finally và dừng cluster đã tạo. Không commit runner có đường dẫn/cache local.
`integration.manualDesktop=true` thay fullLifecycle để khởi chạy2JAR bằng Launcher, ghi
credential TEST vào file **private**, chờ tối đa240s hoặc manual-done.flag rồi teardown đúng
process. `integration.runtimeSource` là label caller phải kiểm bằng artifact receipt; mặc
định UNVERSIONED, không tự khẳng định JAR source. Không làm auth automation qua đường khác.

Không chạy lại benchmark/E1/E2/full PG campaign. CI đúng publication HEAD trong PR; không
lấy CI/history cũ làm test tự chạy. Không nghe audio, không desktop native input, không
hai máy/LAN, không USB hotplug. USB physical vẫn0/12 NOT_RUN, LAN0/41/Breview/gate pending,
FULL default/experiment OFF/MON01 pending physical; không taskDONE/ADR/offline/retention.

## Bàn giao và bước tiếp

Cleanup từng run ở receipt: Application/HTTP resources → owned server → DROP schema fixture
→ pg_ctl stop cluster. Edge chỉ root/descendants do run spawn, không process-name kill;
profile/marker/raw không reset để replay. Desktop force-destroy fallback nếu user chưa
đóng chỉ là cleanup TEST, không bằng chứng Exit GUI đúng. Không giết JVM/DB khác.

Bước C tiếp theo không USB: khôi phục Computer Use native pipe hoặc bố trí người thao tác,
chạy lại sáu nhóm trên hai production JAR desktop riêng, giữ API/thread checkpoints. Dependency:
desktop điều khiển được/người phối hợp; TEST/API/assets đã tự dựng được, không cần chờ A.
Chỉ kiểm input/window-close/lifecycle, không observer mới, không nâng thành LAN/hardware/DONE.

### Chốt handoff và cleanup

User chọn **tiếp tục tự động; giữ desktop NOT_RUN**. Hai JAR riêng đã khởi động, nhưng
không có login/input được xác nhận; passive API60samples trong60s đều UNSYNCED/sequence0.
Hết240s, hai app chưa tự exit được dừng đúng PID để teardown: ownedAppsAlive0. Không coi
đây là OS window-close PASS. Cả4run đều schema0/clusterstop0; kiểm cuối các owned JVM IDs
còn0 và các PG TEST listeners còn0. Không đụng process/DB khác. 47raw hashes trong receipt;
raw/private credentials không upload. Metadata source của manual mode được đổi sang
property/UNVERSIONED sau run47caa50 để tránh hardcode SHA cho các lượt tương lai; không đổi
production/runtime/scenario logic đã kiểm. CI đúng publication HEAD ghi ở PR.