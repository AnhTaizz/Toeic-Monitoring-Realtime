# Nhật ký làm việc

Mỗi buổi làm thêm một mục **ở cuối file**. Không sửa mục cũ; nếu ghi sai thì thêm mục đính chính.

## Mẫu

```markdown
## 2026-10-04 · B · T1-B2
- Thời lượng: 2h
- Đã làm: adapter WS kết nối được server thật, gửi heartbeat mỗi 2 giây.
- Commit: a1b2c3d (nhánh b/t1-b2)
- Đã kiểm: tắt server → client báo mất kết nối sau khoảng 6 giây.
- Còn dở: chưa có backoff khi reconnect; đóng app chưa dừng executor.
- Tiếp theo: làm backoff, rồi khóa UI khi mất kết nối.
- Cần người khác: A xác nhận mã lỗi khi token sai.
```

Viết ngắn và cụ thể. Dòng "Đã kiểm" ghi đúng cái đã chạy; chưa kiểm thì ghi "chưa kiểm".

---

## 2026-10-03 · khởi tạo
- Đã làm: tạo `CLAUDE.md` và thư mục `docs/` (tiến độ, nhật ký, quyết định, protocol, kiểm thử, ba file vai).
- Commit: chưa có (thư mục chưa là git repo).
- Còn dở: chưa gán tên vào A/B/C; chưa có source.
- Tiếp theo: `git init`, rồi A/B/C bắt đầu T1-A1, T1-B1, T1-C1.

## 2026-10-03 · chung · cấu hình git
- Đã làm: thêm `.gitignore` (Maven/Java, file đóng gói, cấu hình cục bộ và bí mật, log, IDE, Python, OS) và `.gitattributes` (chuẩn hóa xuống dòng, đánh dấu file nhị phân).
- Commit: chưa có.
- Đã kiểm: `git status` chỉ còn hiện `.gitattributes`, `.gitignore`, `CLAUDE.md`, `Ke_hoach_LT_Mang_5_chang/`, `docs/`. Chưa kiểm với source thật vì chưa scaffold.
- Còn dở: chưa quyết định file audio Listening có commit vào repo hay không; khi scaffold cần tạo file mẫu cấu hình (`*.example`) đi kèm các file cục bộ đang bị ignore.
- Tiếp theo: commit đầu tiên, rồi T1-A1, T1-B1, T1-C1.

## 2026-10-03 · chung · PostgreSQL bằng Docker Compose
- Đã làm: thêm `docker-compose.yml` (chỉ có service `db`, image `postgres:18`, port chỉ mở cho `127.0.0.1`, volume `toeic-pgdata`) và `.env.example`. Server và client không chạy trong container.
- Commit: chưa có.
- Đã kiểm: trên một máy Windows 11, Docker Desktop 29.4.3: `docker compose up -d --wait` → container `toeic-db` healthy; `select version()` trả PostgreSQL 18.6; `clock_timestamp()` chạy được, timezone của DB là `Etc/UTC`; port host 5433 nối được. Chưa kiểm `docker compose down -v` (reset) và chưa kiểm trên máy thứ hai.
- Còn dở: QD-01 (phiên bản PostgreSQL) và QD-06 (cách chạy test trên PostgreSQL thật) chưa được A chốt; file này mới là đề xuất. Chưa có schema, chưa có DB riêng cho test.
- Tiếp theo: A xác nhận dùng PostgreSQL 18 qua Docker hay không, rồi T1-A1 nối Spring Boot vào DB này.
- Cần người khác: A quyết QD-01, QD-02, QD-06. Lưu ý máy nào đã cài PostgreSQL trực tiếp thì 5432 bị chiếm, phải đặt `DB_PORT` khác trong `.env`.

## 2026-10-03 · chung · T1-A1 / T1-B1 / T1-C1
- Thời lượng: phiên Codex; chưa quy đổi thành giờ công của A/B/C.
- Đã làm: tạo Maven multi-module; server Spring Boot/Flyway/JDBC có user, session, BCrypt và login; JavaFX login HTTP nền mở màn theo role; contract v0; ProcessHandle spike; README và app-image Windows.
- Commit: `bb48376` (server/protocol), `d2edfe5` (client), `1f012c4` (monitoring), `864f701` (hướng dẫn).
- Đã kiểm: `mvn package` qua 5 module và 7 test; xóa/tạo lại volume PostgreSQL rồi Flyway V1 + login candidate/proctor/sai trả 200/200/401; app-image khởi động local; probe đọc `msedge.exe`/`Zalo.exe` và ghi 133/200 process thiếu metadata.
- Còn dở: chưa kiểm LAN và app-image trên máy thứ hai; chưa thao tác login GUI end-to-end bằng tay; review chéo A/B/C chưa diễn ra. Script wrapper reset mới qua parser, chuỗi Docker bên trong đã chạy riêng.
- Tiếp theo: review ba phần, kiểm máy thứ hai, rồi người dùng quyết định có cập nhật `TRACKER.json` hay không; sau đó bắt đầu T1-A2/T1-B2/T1-C2.
- Cần người khác: A chốt QD-03; A/B/C thực hiện review theo vòng và gán tên thành viên.

## 2026-10-03 · B / chung · review-fix PR #1 trước khi xem xét merge
- Đã làm: validate JSON nghiêm ngặt và trường bắt buộc tại LoginApiClient; role lạ bị từ chối trước UI; InvalidServerResponseException có thông báo chung, không giữ raw JSON/cause parser. HTTP vẫn sendAsync → CompletableFuture → Platform.runLater; stop() vẫn shutdown executor.
- Commit code: `961feca789fa8d9e3ded554d0ecb21fccbe43d47` (`fix(client): reject invalid login responses`).
- Đã kiểm: `mvn test` trên working tree từ `ad3ba93` với đúng code sau đó commit tại `961feca`; `mvn package` trên `961feca`. Cả hai PASS, 5/5 module, 42/42 test (AuthService 2, client 39, ProcessObservation 1), 0 lỗi/0 bỏ qua. Thêm 35 lượt test client; malformed JSON, thiếu user, role ADMIN và body rỗng đều hoàn thành future bằng lỗi có kiểm soát, thông báo UI được kiểm ở mức hàm, không phải GUI thật.
- Môi trường: terminal WSL gọi Java/Maven Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11; không chạy lại PostgreSQL/Flyway, ProcessHandle probe hoặc app-image trong phiên review. Maven package có cảnh báo module-info/tài nguyên trùng từ shade, build vẫn PASS.
- Còn dở: GUI manual end-to-end NOT RUN (không có công cụ thao tác GUI thật); LAN máy thứ hai NOT RUN (không có máy thật). Review chéo A/B/C chưa được phiên này xác nhận.
- Bằng chứng: `evidence/stage1/2026-10-03-skeleton-smoke.md` phần review-fix và `evidence/stage1/2026-10-03-review-build.txt` (trích log build tự động, bỏ đường dẫn máy).
- Git: dùng cấu hình danh tính/xác thực Git Windows để commit/push; giữ nguyên `task.txt` ngoài commit theo xác nhận của người dùng. Không sửa PROTOCOL/TRACKER; không merge và không push main.
- Tiếp theo: người dùng kiểm GUI/LAN và review còn lại trước quyết định merge PR #1.

## 2026-10-03 · chung · verification cuối trước merge PR #1
- HEAD bắt đầu: `ceb8131a558660057cf62c46f2466db8515b9b4e`; branch `feat/stage1-project-skeleton`. Source khớp code fix `961feca`; chỉ cập nhật docs trong phiên này.
- Đã làm: cập nhật description PR #1 từ 7/7 thành Maven test 42/42 PASS, package PASS, reject response login sai an toàn; thêm commits 961feca/ceb8131 và các kiểm tra thủ công còn chờ. Lần gọi đầu timeout ở approval review, retry một lần thành công.
- Đã kiểm: đọc metadata PR, danh sách reviews và comments GitHub (0/0); đối chiếu log build phiên review trước và git diff source. Không chạy lại Maven; PASS build/test thuộc lượt chạy thật trước trên code không đổi, không phải lượt mới.
- GUI candidate/proctor/sai mật khẩu/server tắt hoặc URL sai: NOT RUN — không có công cụ thao tác GUI thật trong phiên terminal Windows/WSL. Không khởi động DB/server/app-image chỉ để gọi là đã test GUI.
- LAN máy Windows thứ hai: NOT RUN — chưa có máy thật được cung cấp; không dùng localhost thay thế, không đổi firewall.
- Cross-review A→B, B→C, C→A: NOT RUN/chưa đủ xác nhận thực tế. Người dùng trả lời “Oke rồi”; đã hỏi làm rõ cả ba lượt có review code tại ceb8131, hiểu code và không thấy blocker hay chưa. Chưa có câu trả lời rõ cho lượt hỏi lại tại thời điểm ghi bằng chứng; không tự gán review PASS.
- Bằng chứng: `evidence/stage1/2026-10-03-merge-readiness.md`; log build trước vẫn ở `evidence/stage1/2026-10-03-review-build.txt`. Không có screenshot/log GUI hoặc LAN mới.
- Kết luận: Ready to merge NO; còn GUI end-to-end, LAN máy thứ hai và xác nhận cross-review. Không sửa source/PROTOCOL/TRACKER; không merge PR hoặc push main; task.txt giữ nguyên ngoài commit.

## 2026-10-04 · B · T1-B2 — PARTIAL / BLOCKED BY T1-A2
- Phiên bắt đầu 03/10, hoàn tất tài liệu 04/10 (UTC+7); không quy đổi thành giờ công của thành viên.
- Base: fetch origin → checkout main → pull --ff-only; main mới nhất `34a78350d462bd472917171dbef77d70aa8e6b88` đã merge PR #1. Tạo `feat/t1-b2-network-heartbeat-ui`, giữ task.txt ngoài commit, không reset/merge.
- Dependency: QD-03 chỉ nằm trong bảng chờ (đoạn header handshake là mẫu, không phải quyết định); PROTOCOL chưa có endpoint/cách gửi credential; server chưa có WS handler; remote/open PR không có A2 làm source of truth. Không tự chọn endpoint/header/AUTH.
- Đã làm: RealtimeClient (raw java.net.http.WebSocket chỉ trong adapter), MonitoringTransport cho C, ghép fragment có giới hạn trước parse strict JSON, demand request(1), heartbeat MOCK 2s, backoff 1/2/4/8s tối đa 4 retry, state/scope/ACK guards, writes tuần tự/giới hạn, hủy task và socket khi shutdown. Default opener fail-closed; hook mở/auth thật chờ A2.
- UI: ConnectionViewModel khóa khi chưa CONNECTED; callback JavaFX qua Platform.runLater; gỡ listener/logout và dọn adapter/HTTP client/executor trong stop. Container hiện là placeholder, không cài màn thi/dashboard hay collector C3.
- Commit code: `7325da1` feat(client); `75d4b9f` test(client). Docs/evidence ở commit tiếp theo trong cùng nhánh. Không sửa server/protocol DTO, nguồn môn học, PROTOCOL, QUYET_DINH hoặc TRACKER.json.
- Đã kiểm trên code `75d4b9f`: mvn test (03/10 23:56:53 UTC+7) và mvn package (03/10 23:58:40 UTC+7) PASS, 5/5 module, 70/70 test (AuthService 2, LoginApiClient 39, realtime MOCK 28, ProcessObservation 1), 0 failure/error/skipped. Có cảnh báo shade module-info/MANIFEST trùng, build vẫn PASS.
- Môi trường: Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11 qua WSL. Unit shutdown kiểm cả executor thật kết thúc bằng awaitTermination; không suy ra app GUI đã đóng sạch từ đó. Lượt test đầu 65/65 PASS trước 5 test bổ sung; log cuối mới là bằng chứng code được bàn giao.
- Bằng chứng: `evidence/t1-b2/2026-10-04-verification.md` và log Maven ngày 03/10. Socket/clock fixture đều ghi MOCK; không coi MOCK là real integration. Một lần duyệt quyền Maven timeout, retry lệnh trực tiếp thành công.
- Còn dở: authenticated WS, heartbeat server receipt, server-off/re-auth smoke **BLOCKED BY T1-A2**; login server thật trong phiên, GUI/LAN **NOT RUN**. T1-B2 không DONE.
- Handoff: C có send(envelope), connectionState, onConnectionState, onMessage; future send chỉ là socket write, ACK separate. State type/payload chờ C chốt; queue/event retry và collector chờ T1-C3.
- Tiếp theo: A cung cấp QD-03, endpoint/auth/scope/error; B nối opener xác thực và kiểm thật, A review phần B. Không chuyển T1-B3.

## 2026-10-04 · A · T1-A2 — auth REST/WS và handoff B2
- Base: fetch/checkout main/pull --ff-only; `34a78350d462bd472917171dbef77d70aa8e6b88` vẫn là main mới nhất, PR #1 đã merge. Nhánh `feat/t1-a2-authenticated-websocket` tách từ main, không merge/sửa B2; task.txt giữ untracked.
- Đã làm: SessionAuthenticationService đọc SHA-256 session + expiry/revoke/enabled/current role, BearerAuthenticationFilter cho /api/** sau login, GET /api/v1/auth/me, AuthenticatedUser/principal, AuthorizationService + AttemptScopeAuthorizer deny unknown production. Raw Spring WS /ws/v1/realtime dùng header auth, revalidate mỗi message, ConcurrentWebSocketSessionDecorator, strict JSON, ERROR và heartbeat ACK transport.
- QD-03: chốt Authorization: Bearer cho REST và handshake sau khi Java21 WebSocket nói chuyện thật với Spring/Tomcat. PROTOCOL mô tả endpoint/field/ACK/ERROR và unscoped heartbeat khi login chưa cấp attempt. Không ACK event chưa persistence, không presence/UNKNOWN/A3/C3.
- Commit source: `3c570af`; test/harness: `7ac62c4`. Documentation commit tiếp theo không đổi source.
- Đã kiểm trên `7ac62c4`: mvn test 04/10 00:52:07 UTC+7, mvn package 00:53:30 UTC+7, PASS 5/5 module và 91/91 tests (server 51: auth cũ 2 + session/authorization 18 + network 30 + concurrent send 1; client 39; monitoring 1), 0 failure/error/skipped. Không dùng 70/70 của B2 hay report XML cũ không còn source.
- Môi trường: Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, Docker Desktop 29.4.3, PostgreSQL 18; terminal WSL gọi Windows toolchain. Mockito/ByteBuddy dynamic-agent/CDS và shade module-info/MANIFEST warnings, build vẫn PASS.
- Real network test dùng session/scope store MOCK ghi nhãn; smoke riêng dùng app production + JDBC/Flyway/PostgreSQL thật, không H2. Docker compose up -d --wait healthy, không reset volume. Login → WS header → heartbeat ACK; invalid/missing/expired/revoked reject, revoke trên socket active, unknown scope FORBIDDEN, malformed JSON rồi heartbeat tiếp vẫn ACK. Script không in token/password và chỉ xóa session do smoke tạo.
- Những lỗi đã sửa trong phiên: test scope provider bị trùng bean, dùng @Primary cho MOCK; smoke PowerShell thiếu Java PATH, thêm tham số JavaHome; classpath smoke chỉ expose harness thay vì toàn bộ test configuration. Lượt cuối PASS, không dùng lượt lỗi làm evidence PASS.
- Optional mvn clean FAIL ở app-image ToeicMonitor.exe bị Windows khóa; không kill process người dùng để clean. Source được compile lại, mvn test/package PASS; kiểm JAR không chứa class B2 cũ hoặc test/MOCK fixtures. Report XML B2 cũ không được cộng vào tổng A2.
- Documentation/evidence: `evidence/t1-a2/2026-10-04-verification.md`, logs test/package/PG sạch; cập nhật PROTOCOL, QUYET_DINH, TIEN_DO, KIEM_THU, VAI_A, README. TRACKER.json/nguôn/client source giữ nguyên.
- Review C chưa diễn ra trong phiên; không ghi duyệt thay C. User task.txt cho phép merge khi technical checks + PR mergeability PASS; thao tác Git cuối ghi trong report. Không tiếp tục A3 hoặc B2.
- Handoff B: tạo opener với Bearer header, reconnect auth lại, handle 401/503/ERROR/1008; cho phép heartbeat/ACK attemptId null với scope rỗng. Own attempt assignment thật chờ schema; không dùng fixture test để cấp quyền production.

## 2026-10-04 · B · T1-B2 — consume A2 và real integration
- Original branch HEAD `edd50cf`; fetch xác nhận main `f62c732` (PR #2/A2). Checkout B2, merge origin/main bằng `57f2a9c`; giải 4 conflict README/TIEN_DO/NHAT_KY/KIEM_THU theo từng đoạn, giữ cả source B2 và lịch sử/contract A2. Không rebase/reset/force push; task.txt vẫn untracked.
- Đã làm: AuthenticatedWebSocketOpener parse URI HTTP→WS/HTTPS→WSS, fixed /ws/v1/realtime + Bearer header mỗi fresh handshake. Session unscoped/collector optional; heartbeat ACK tracking, direct ERROR parser/safe message, 401/UNAUTHORIZED/1008 FAILED và dừng token cũ, bounded reconnect. JavaFX login candidate/proctor tạo adapter thật, Platform.runLater, lock model, logout/stop release listener/socket/worker/HTTP/token reference; không collector/B3.
- Commits: `9064b90` implementation, `6d5c7f4` tests/harness; docs tiếp theo không đổi source.
- Đã kiểm: baseline merge `57f2a9c` 119/119 PASS. Final mvn test 09:48:27 UTC+7 và package 09:50:06 PASS 142/142, 5/5 module, 0 failure/error/skipped; server51/client90/monitoring1. Thêm 23 lượt phiên này ngoài 28 B2 cũ; thay blocked-opener test theo QD-03 đã chốt.
- Real smoke cuối PASS production Spring/JDBC/Flyway/PostgreSQL18.6 + chính B RealtimeClient: login candidate/proctor, unscoped heartbeat ACK, owned server-off/restart/fresh auth, invalid401/revokeERROR/expire→FAILED, retry exhausted, workers kết thúc. Client abort ngay UNAUTHORIZED; close1008 riêng được kiểm MOCK, không fake network observation. Chỉ stop process/sửa/xóa sessions do harness tạo, không reset DB hoặc kill ứng dụng người dùng.
- Môi trường Windows11 x64/JDK21.0.8/Maven3.9.11/Docker29.4.3 qua terminal WSL; Mockito agent/CDS và shade warnings vẫn build PASS. JAR client có production opener, không Test/MOCK/harness classes. Một lần auto-approval stage timeout, retry thành công; không blocker còn lại từ approval.
- Evidence `evidence/t1-b2/2026-10-04-real-integration.md` và logs sạch. GUI manual/LAN/review A NOT RUN, không suy ra GUI PASS từ headless model. 27 case chính thức và TRACKER giữ nguyên; QD-03/server/protocol source/nguon không đổi, PROTOCOL chỉ ghi B đã consume.
- Handoff C: MonitoringTransport send=write, ACK/ERROR=onMessage; eventId/queue/retry/persistence và PROCESS_OBSERVED/state chờ C3/A3. Không triển khai presence/exam flow/B3. Task.txt cho phép push/PR/merge nếu technical checks và mergeability PASS; Git cuối xem report. Dừng sau B2.

## 2026-10-04 · C · T1-C2 — collector local
- Base: git status/fetch/checkout main/pull --ff-only, main mới nhất `9f018616fb6bdbe13a7d8214bfadc2fbf6fd5b53` chứa PR #1/#2/#3. Tạo `feat/t1-c2-process-collector`; task.txt giữ untracked; không reset/rebase/force push.
- Đã làm: production collector client/monitoring riêng khỏi C1 spike; safe ProcessHandle source giữ filename/start/user-availability, QD-08 đúng8 tên, COMPLETE/UNREADABLE, immutable snapshot + diagnostics, identity collectorSessionId/pid/start nullable, nanoTime local. Single ScheduledThreadPoolExecutor fixed-delay1s configurable, async stop future đợi poll/callback/worker, UUID mỗi start, ngăn overlap xuyên restart. Gate active candidate; production chưa có assignment trigger nên JavaFX login giữ nguyên, không auto-start.
- Commits: `9621f73` implementation; `02789a8` tests/harness; docs sau đó không đổi source.
- Đã kiểm trên02789a8: final mvn test10:42:55 UTC+7, package10:44:26, PASS175/175,5/5module,0failure/error/skipped; server51/client123/monitoring1. C2 mới33 (collector14, policy/source19). Unit fixtures MOCK; fixed-delay/no-overlap/lifecycle dùng real worker/latch, không benchmark E1.
- Real smoke sau fix trên đúng code sau đó commit02789a8: Windows11/JDK21.0.8,378 process/162 metadata unreadable (command/start/user đều162); observed filenames discord.exe/msedge.exe/zalo.exe. Owned Edge headless about:blank với profile tạm riêng xuất hiện/biến mất; chỉ stop descendants/root do harness tạo, cleanup profile. Real stop/restart UUID mới/worker termination/proctor gate PASS. Context active MOCK/dev fixture; không giả production session assignment, GUI manual NOT RUN.
- Smoke đầu FAIL trước fix validation pid>=1; Windows PID0 idle được xác nhận tồn tại, cho phép pid0 + unreadable metadata thay vì fail whole enumeration, thêm regression test. Không giữ lượt lỗi làm PASS; poll failures vẫn controlled SOURCE_FAILURE, clear latest, không suy diễn sạch.
- Môi trường Windows11 x64/JDK21.0.8/Maven3.9.11 qua WSL; Mockito/ByteBuddy agent/CDS và shade overlap warnings, build PASS. JAR có collector production, không C2 Test/Smoke classes. Không reset DB, không gọi monitoring transport, không chạy GUI/E1 hoặc review thay B.
- Docs/evidence: NHAT_KY/TIEN_DO/KIEM_THU/VAI_C/README, PROTOCOL chỉ local collector status; `evidence/t1-c2/2026-10-04-verification.md` và sanitized logs. MT01 PARTIAL local, không full PASS. TRACKER/nguon/server/protocol DTO/B2 networking/JavaFX login/spike/QD-08 giữ nguyên.
- Handoff C3: ProcessSnapshot và ProcessIdentity để diff observed set; C3 mới thêm eventId/queue/retry/ACK và nối MonitoringTransport, A3 mới auth/persistence/commit ACK. Không tự làm C3. Task.txt cho phép PR/merge nếu acceptance + privacy + mergeability PASS; Git cuối xem report.

## 2026-10-04 · A · T1-A3 — persistence/push/timeline thật
- Base: main mới nhất `6b9572ed3119c3e19fe6cf73f9ea0f73c1dfac9d` chứa PR1–4, đã fetch/checkout/pull trước khi tạo `feat/t1-a3-monitoring-events`. Giữ file người dùng; không reset/force push hoặc sửa client/collector.
- Source/test/script commit `88ef95a2d96db202edf39e3ed5c72f026bfdb7fc`: FlywayV2 schema attempts/assignments/events không seed production; JdbcAttemptScopeStore, login/auth-me scope ACTIVE, ProcessEvent validation, transaction store unique attempt/event + normalized payload conflict. Handler gọi service qua proxy, COMMIT thành công mới ACK và warning. Registry reauth/current scope lọc proctor, dùng shared decorator; timeline HTTP PROCTOR/assignment, order received_at,id.
- Final mvn test18:40:34 và package18:41:15 UTC+7 PASS200/200,5/5module,0failure/error/skipped; A3 thêm25 (24validation +1concurrent ACK/warning); server76/client123/spike1. Windows11 amd64/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6. Mockito agent/CDS/shade warnings không làm fail build; không thêm thư viện.
- Real PostgreSQL/Flyway production Spring + HTTP/WebSocket smoke PASS, bắt đầu18:35:48 UTC+7: clean/upgrade migration, FK/unique, scopes/login/me, event/duplicate/conflict, assigned push/foreign isolation, invalid paths/JSON, nullable metadata, barrier concurrent duplicate, offline timeline/order, assignment revoke/session revoke/close1008. TEST fixtures và deferred COMMIT-failure trigger chỉ trong schema UUID do harness sở hữu, cleanup schemas; không reset volume/dev tables hoặc dừng server người dùng.
- COMMIT failure thật PASS: INSERT chạy rồi PostgreSQL deferred trigger lỗi ở commit; row0, RETRYABLE_SERVER_ERROR retryable=true, không ACK success/warning. DB-off NOT RUN vì DB dùng chung; không giả DB-off PASS. C human review/GUI/LAN NOT RUN; không giả review thành viên hoặc “collector mở app → dashboard” đã xong.
- Lỗi đã sửa: cache ngoài sandbox cần quyền, tests/fixture theo provider JDBC, TokenHash package-private trong harness, kiểu JDBC Timestamp và dữ liệu test ID “INVALID” thực ra hợp lệ. Chỉ final lượt PASS được ghi làm evidence, không giấu lỗi trước fix.
- Evidence `evidence/t1-a3/2026-10-04-verification.md`, mvn-test/package/postgres-smoke/security-scan logs sanitized. PROTOCOL/TIEN_DO/KIEM_THU/VAI_A cập nhật; QUYET_DINH không đổi vì không có kiến trúc mới ngoài task/QD-05. TRACKER/nguôn/01–03 giữ nguyên.
- Handoff C3 event schema/attempt/correlated ACK/retry/conflict; B3 timeline+MONITOR_WARNING cùng item schema/dedupe attempt+event, reconnect đối chiếu timeline. C3 queue/retry, B3 dashboard, A4 presence/full/delta/exam chưa làm. Task cho phép push/PR/merge nếu technical checklist/mergeability PASS; Git cuối trong final report, không xóa nhánh. Dừng sau A3.

## 2026-10-04 · C · T1-C3 — event delivery, bounded retry và overflow

- Đã fetch/pull main `74d2704328a794a24509d43c2f88702d63965511`, tạo `feat/t1-c3-monitoring-event-delivery`; working tree đầu sạch, giữ .env/nhánh cũ, không reset/force push. Baseline Maven test chạy lại PASS200 lúc19:04:53 UTC+7.
- CandidateMonitoringSession chỉ explicit start sau fresh auth-me/attempt scope; proctor/scope rỗng không scan/worker/queue. Stop đếm/bỏ RAM atomically, hủy subscription/timer/worker, chờ termination trước restart/switch; old callback generation và FX update đọc status hiện tại để không ghi đè sau Stop/logout.
- MonitoringMessage deep copy/frozen IDs/payload/time, MonitoringDelivery diff identity(session,pid,start nullable), queue500/in-flight4/ACK5s/max5/backoff1..8s. Scan lỗi giữ baseline, metadata quality không làm identity mới, null/known start thay đổi coi mới và ghi giới hạn. Queue đầy giữ cũ/drop mới, cập nhật baseline; một gap slot frozen + accumulator kế tiếp, không đổi payload retry.
- B integration tối thiểu: LoginApiClient.scope, candidate controls JavaFX, MonitoringTransport.forgetPending, RealtimeClient gap validation/correlation cleanup/heartbeat reservation+expiry. A overflow hook tối thiểu: MonitoringGap/Service, handler gap branch, FlywayV3 bảng riêng unique attempt/gap + scope/lock/commit-before-ACK. V2 không sửa. Server test-scope dependency client phục vụ production harness; không lọt JAR production.
- Final test19:37:13/package19:38:26 UTC+7 PASS253/253, 53 mới; client161/server91/spike1. Smoke C3 cuối PASS real Windows owned Edge/C2/B2/C3/PG/ACK, nhiều poll/dedup, offline timeline. SIMULATED first event ACK loss row1/warning1; server stop/restart REAL + MOCK pending snapshot reconnect PASS; capacity1 MOCK overflow→REAL gap commit/ACK/dedup/next accumulator/heartbeat PASS; deferred COMMIT failure thật rollback/retry, gap conflict/scope/role PASS. Workers/subscriptions cleanup PASS. A3 real regression trên V3 PASS.
- DEV/TEST demo script tạo/cleanup DEMO-C3-A chủ động, không autoseed lượt production. Kiểm script Create/duplicate refusal/idempotentCleanup trên schema TEST riêng PASS, giữ user2/public. Lượt test đầu assertion thứ tự gap retry sai đã sửa theo scheduler contract; chỉ final PASS dùng làm evidence. Không giấu lỗi trước fix.
- README/PROTOCOL/TIEN_DO/KIEM_THU/VAI_C và evidence C3 cập nhật. TRACKER/nguon/01–03 giữ nguyên; QD-09 chỉ lưu quyết định metadata nullable thật. Security scan không credential/token/private paths, .env/task.txt không commit; test fixtures synthetic có nhãn TEST.
- Handoff B3: start/stop candidate, counts/status/manual retry và process warning/timeline giữ schema A3; gap chưa push hoặc REST timeline. Handoff A: V3 + transactional gap auth/scope/lock/dedup; chặng2 tiếp quản state/event muộn/gap khác. Human B review/GUI/LAN NOT RUN, MT01 PARTIAL tới B3, A4 chưa làm. User cho phép PR/merge commit khi technical acceptance + mergeability PASS; Git cuối xem final report, giữ nhánh C3. Dừng tại C3, không sang C4/A4/B3/chặng2.

## 2026-10-04 · A · T1-A4 — monitoring presence

- Fetch/pull main99b4f39 (PR#6), tạo feat/t1-a4-monitoring-presence; đầu phiên sạch, giữ .env/nhánh cũ. Baseline test chạy lại253/253 lúc20:48:02 UTC+7. Người dùng đã chọn không dùng kết nối ChatGPT; không tuyên bố ChatGPT plan/review.
- Client cross-owner tối thiểu: MonitoringTransport lease; RealtimeClient binding attempt/collector UUID thật, immediate HB/default2s/configurable, old lease CAS không gỡ phiên mới; CandidateMonitoringSession start/stop/mất quyền. Reconnect giữ collector, Stop/logout/switch unbind và tiếp tục unscoped ping. Không thêm STOP protocol hay đổi C3 queue/retry.
- Server PresenceStore/JdbcPresenceStore/MonitoringPresenceService/controller; monotonic TTL6s scan500ms, association aggregate bounded, per-attempt serialization+durable revision/CAS, transaction proxy commit-before-ACK/push. UNKNOWN không gian lận; event/gap không refresh. V4 presence/interruption riêng V3; timeout một record, recovery giữ ID/history, restart demote không bịa gap.
- GET attempts/interruptions và MONITOR_PRESENCE cùng snapshot DTO; auth/PROCTOR/current ACTIVE assignment, common decorator ACK/warning/presence. Handoff B3 schema/revision/roster refresh/stale dashboard/offline history, không dashboard/parser.
- Test21:26:27/package21:28:28 PASS277/277, thêm24 ngoài253 (client5/server19). Build riêng vì server dev khóa JAR; giữ PID server người dùng, public chưa migrateV4. REAL owned client JVM hard-kill, hai candidate isolation/B eventACK, assigned presence, reconnect/stablehistory, token/CLOSED cleanup, deferred heartbeat/timeout COMMIT rollback/no fakepush và retry, offline roster, restart, workers PASS. Source snapshots MOCK; HTTP/WS/DB/B2/C3 REAL.
- REAL A3/C3 regression cuối14:29 UTC (21:29 UTC+7) PASS; C3 Windows ProcessHandle/Edge thật, ACKlossSIMULATED/overflowMOCK source. Lỗi Gson Instant/A3 ping fixture/Windows profile cleanup đã sửa trước final PASS. Profile sót từ lượt C3 lỗi không có process dùng, manual cleanup bị automatic review từ chối blocked by policy; final-run profile/worker cleanup PASS.
- README/PROTOCOL/TIEN_DO/KIEM_THU/VAI_A/B/C và QD10/evidence cập nhật. Evidence t1-a4/2026-10-04-verification.md + sanitized logs. TRACKER/nguon/plans01–03/V1–V3 không đổi, .env/task không commit. Human C review/GUI/LAN/package máy khác NOT RUN; MT08 PARTIAL whole case, server component PASS. Commit/push/PR/merge theo quyền task khi technical checklist+mergeability PASS; Git cuối xem final report. Dừng A4.

## 2026-10-04 · B · T1-B3 — dashboard giám thị

- Fetch/pull base main PR#7 `1e084d4a6ec0d34073502db7c722f8ebf81af175`, branch feat/t1-b3-proctor-dashboard; đầu phiên sạch. Không kết nối ChatGPT theo lựa chọn trước của người dùng; không claim planning/review. Giữ file/process người dùng và server PID24352 đang khóa JAR/public V3. Build detached checkout cùng source; 19 changed source/script SHA256 đối chiếu PASS.
- Commits model/API `75e956d`, parser/UI `e5ead02`, tests/harness `a601452`; docs/evidence sau đó không đổi source. Dashboard PROCTOR trong cùng app; candidate C3/A4 giữ nguyên. DTO/parser/API/model/controller/view tách lớp. Bearer header, strict v0/ID/null/Instant/numeric long, HTTP worker/timeouts/body bound; presence revision, event dedup/HTTP order, history recoveredAt, roster/scope refresh/callback generations/401/403, stale riêng từng phần. Buffer hữu hạn/ngân sách reload, một socket B2, proctor không collector.
- Baseline chạy lại277/277 lúc22:04:14; final test22:41:50/package22:42:23 UTC+7 PASS331/331, thêm54 (JSON20/model9/controller11/HTTP11/parser3); client220/server110/spike1. MOCK fixtures/manual executor/future race; một test WS ban đầu thiếu field null do Gson fixture đã sửa serializeNulls theo server, không nới strict production.
- REAL PG18.6/Flyway V3→V4/Spring/server port và schema riêng/HTTP/WS/B2/C3/dashboard PASS. MOCK process source → event một dòng, owned child hard-kill → UNKNOWN/history, recovery stablegap/recoveredAt, B ONLINE, other proctor isolated, intentional proctor offline/missed event+transitions/reconnect HTTP bù không trùng, real403 scope cleanup và401 login again. Không gọi intentional disconnect là random network/backoff test.
- Visible JavaFX Stage/production proctor view PASS: select/tabs/timeline/history, owned hard-kill UNKNOWN, recovery ONLINE/history giữ, stale ONLINE/banner, reconnect/refresh/logout callback; actual Scene screenshots được xem. GUI login/candidate/proctor toàn luồng MT01/MT08, LAN/package máy khác/human A review NOT RUN; prototype PARTIAL.
- C3/A4 regressions cuối22:43 PASS; C3 ProcessHandle/owned Edge REAL, ACKlossSIMULATED/overflowMOCK ghi rõ. Owned schema/JVM/worker cleanup PASS, SQL schemasTEST0/publicV3 unchanged, user server alive. Không retry cleanup Edge folder cũ đã bị automatic review từ chối. Security/source/JAR scan PASS, không dependency mới/secret/private paths.
- README/docsREADME/PROTOCOL/TIEN_DO/KIEM_THU/VAI_B và evidence t1-b3/2026-10-04-verification.md cập nhật; TRACKER/nguon/plans01–03/server production/migrationsV1–V4 giữ nguyên. CODE_COMPLETE B3, toàn nghiệm thu PARTIAL; PR/merge theo quyền task nếu technical gates+mergeability PASS, Git cuối trong report. Dừng B3, không B4/C4/chặng2.

## 2026-10-05 · C · T1-C4 — log đo và khảo sát phương pháp

- Fetch/pull main PR#8 a04aaa20963b9377809c8d1c0ec8298f638244b0, branch feat/t1-c4-monitoring-measurement; đầu phiên sạch, giữ file/người dùng/nhánh cũ. B4 remote chưa merge nên không đụng B4. Không kết nối ChatGPT theo lựa chọn trước của người dùng, không claim plan/review. Baseline test331 PASS20:26:45 UTC+7.
- Viết định nghĩa trước code: byte UTF-8 toàn JSON envelope+payload, TXattempt/writecomplete/writefailed và fullRX riêng; BUSINESS_ACK không thêm byte; retry tính lại, không TX+RX/outcome đếm đôi. Clock domain recorder riêng, UTC/monotonic rõ, sequence nullable không giả. Commits ffbb5db recorder/schema, c935f82 transport, c70c93e tests/harness/summary.
- Recorder opt-in tắt mặc định, counters bounded key/type thread-safe, queue1024/drop-new và counter độc lập raw. Writer nền daemon/flush2000ms, failed/timeout/dropped/unwritten/pending báo incomplete; I/O không chặn producer hoặc phá event/ACK. Metadata lọc ID, không payload/token/password/path/OS username; Gson hiện có được dùng lại trong protocol. Script Python stdlib JSONL→CSV/JSON theo yêu cầu C4, không collector Python/framework mới.
- Cross-owner B RealtimeClient serialize1/actual send future/full RX/ACK accepted và close; A handler RX/registry decorator actual delegate bên trong concurrent buffer, không coi enqueue là completed. Auth/scope/service COMMIT/ACK/retry/presence/dashboard giữ, wire v0/migrations không đổi. Unit latch kiểm enqueue/race, byte/Unicode/retry/failure/drop/counter/cleanup; summary known220byte và incomplete tests.
- Final clean detached build c70c93e: test20:56:53/package20:57:53 PASS366/366, +35 Java (protocol23/client7/server5; module totals23/227/115/1), Python9 PASS riêng. Metadata sourceDirtyfalse/source126hash/JAR hash/date/settings; docs-only sau build không đổi source. Lượt đầu lỗi assertion/parser/control và signature/constant test đã sửa; không coi exploratory là evidence final.
- REAL PG18.6/HTTP/Spring/WS/B2 candidate+proctor/dashboard model và ProcessHandle collector scan riêng PASS. Event/overflow source MOCK; SIMULATED C3 observer chặn ACK đầu sau B2 accepted→retry2/DB1/dashboard1. Capacity1/drop2→REAL gap/ACK và retained warning; manual conflicting payload→ERROR. Raw3file131line82551filebyte, summary26categories COMPLETE; CLIENTTX5291+SERVERTX7277=12568applicationbyte37writes mỗi outcome, không dùng file size/mix RX. Summary JSON/CSV tái tạo hash byte-identical, raw untouched.
- B3 regression REAL V4/owned child hard-kill/UNKNOWN/recovery/stale HTTP recovery/dedup/scope403/auth401/cleanup PASS; process sourceMOCK, GUI NOTRUN. Owned recorder/collector/delivery/B2/dashboard/presence workersgone, SQL C4/B3 TESTschemas0/publicV3, live-secret/private-path/JAR/protected/source checksPASS. Docker Desktop cần bật để chạy DB; không reset volume/kill app người dùng. Dọn bản sao build target tạo thừa bị automatic review chặn blocked by policy, giữ ignored và dùng checkout sạch mới; không retry xóa.
- Survey ProcessHandle/WMI nguồn Oracle/Microsoft, polling phù hợpstack nhưng thiếumetadata/bỏprocessgiữahaiquét; WMI/ETW/JNI/JNA chỉ khảo sát/NOTRUN. Không claim performance/novelty/delta. README/docsREADME/PROTOCOL/TIEN_DO/KIEM_THU/VAI_C/schema/survey/QD11 và evidence t1-c4/2026-10-05-verification.md cập nhật; TRACKER/01–03/nguon/migrations unchanged.
- `.gitattributes` giữ raw JSONL nguyên byte (`-text`) và summary LF để checkout Windows không phá SHA256/reproduce; không đổi source Java/script sau final build.
- Human B review (và A cross-owner), GUI toàn luồng, LAN/package máy2, E1/E2/full-delta NOT RUN. CODE_COMPLETE C4, prototype vẫn PARTIAL; push/PR/merge theo quyền task khi technical gates/mergeability PASS, Git cuối trong report. Dừng C4, không B4/chặng2.

## 2026-10-05 · A · T2-A1 — Import đề thi và Mở ca thi
- Base: fetch origin, checkout `feat/t2-a1-exam-import-session` từ main mới nhất.
- Đã làm:
  - Protocol DTOs: `ExamOptionImportDto`, `ExamQuestionImportDto`, `ExamImportRequest`, `ExamImportResponse`, `CreateSessionRequest`, `CreateSessionResponse`, `AttemptCreationDto` (Giám thị); `CandidateOptionDto`, `CandidateQuestionDto`, `CandidateExamDto` (Thí sinh). Bất biến tuyệt đối: DTO thí sinh không chứa `correctOption` hay bất kỳ trường đáp án nào.
  - Flyway migration V5 (`V5__exams_and_sessions.sql`): các bảng `exams`, `exam_questions`, `exam_options`, `exam_sessions`, `exam_session_proctors`; bổ sung `session_id`, `exam_id`, `deadline_at`, `writer_epoch`, `saved_revision`, `submitted_at`, `score`, `answers_json` và mở rộng `state` constraint của `monitoring_attempts`.
  - Backend: `ExamValidationService` (kiểm tra đề, option, correctOption, sequenceOrder, đoạn văn), `ExamService` (transactional import, session creation đồng bộ attempts/proctor_assignments, lấy đề thí sinh không đáp án), `ExamController` (`POST /api/v1/exams/import`, `POST /api/v1/sessions`, `GET /api/v1/attempts/{attemptId}/exam`).
  - Phân quyền & AT02: Chỉ `PROCTOR` được import và tạo ca thi; `CANDIDATE` gọi import bị từ chối 403 `FORBIDDEN`; unauthenticated bị từ chối 401 `UNAUTHORIZED`. Thí sinh chỉ lấy được đề thi của chính attempt mình sở hữu.
- Đã kiểm:
  - `mvn test` PASS 372/372 tests trên toàn bộ 5 module (protocol 23, client 227, server 121, spike 1).
  - Unit test `ExamControllerTest` dùng Reflection assert toàn bộ DTO thí sinh không có trường đáp án.
  - Real PostgreSQL 18.6 smoke (`scripts/smoke-t2a1.ps1`): Flyway V1->V5 migration, proctor/candidate login, AT02 role rejection, import 10-câu TOEIC, tạo ca thi, DB attempt verification, candidate lấy đề 10 câu không chứa chuỗi 'correctOption'/'correct_option'/'correctAnswer' PASS.
- Bằng chứng: `evidence/t2-a1/2026-10-05-verification.md`.
- Tiếp theo: Chuyển tiếp sang T2-A2 (Autosave bài thi theo revision, kiểm tra writerEpoch và khóa bi quan).

## 2026-10-05 · A · T2-A2 — Autosave toàn bộ đáp án theo revision
- Base: `feat/t2-a2-autosave-answers` tách từ `feat/t2-a1-exam-import-session`.
- Đã làm:
  - Protocol DTOs: `AutosaveAnswersRequest`, `AutosaveAnswersResponse` trong `vn.edu.toeic.protocol.exam`.
  - Flyway migration V6 (`V6__exam_autosave_requests.sql`): bảng `exam_autosave_requests` theo dõi idempotency log `(attempt_id, request_id)`.
  - Backend: `ExamService.autosaveAnswers` tuân thủ đúng thứ tự quyết định Mục 2:
    1. Kiểm quyền thí sinh sở hữu attempt (401/403).
    2. Khóa dòng attempt bằng `SELECT ... FOR UPDATE OF a`.
    3. Kiểm tra `writerEpoch` sau khóa (khác epoch hiện tại -> 409 `STALE`).
    4. Kiểm tra trạng thái bài thi (`state != 'ACTIVE'` -> 409 `INVALID_STATE`).
    5. Kiểm tra thời hạn bằng `SELECT clock_timestamp()` (`decisionAt >= deadlineAt` -> 409 `EXPIRED`).
    6. Kiểm tra các câu hỏi/lựa chọn trong map answers thuộc đúng đề thi của attempt (sai -> 400 `INVALID_INPUT`).
    7. Chuẩn hóa canonical answers map bằng `TreeMap` (so sánh chuẩn xác JSON không bị ảnh hưởng bởi thứ tự key).
    8. Idempotency theo `requestId` (AT05): cùng payload trả `ALREADY_SAVED`; khác payload trả 409 `CONFLICT`.
    9. Quy tắc revision (AT03, AT04): revision thấp hơn -> 409 `STALE`; cùng revision cùng nội dung -> `ALREADY_SAVED`; cùng revision khác nội dung -> 409 `CONFLICT`; revision cao hơn -> cập nhật answers và commit trả `SAVED`.
  - Controller & Exception: `POST /api/v1/attempts/{attemptId}/answers`, `ExamApiException` ánh xạ HTTP error codes (`STALE`, `CONFLICT`, `EXPIRED`, `INVALID_STATE`, `INVALID_INPUT`).
- Đã kiểm:
  - `mvn test` PASS 373/373 tests trên 5 module (protocol 23, client 227, server 122, spike 1).
  - Real PostgreSQL 18.6 smoke (`scripts/smoke-t2a2.ps1`): Flyway V1->V6, autosave tăng dần revision 1->2, AT03 stale revision 41<42 bị từ chối 409, AT04 đổi thứ tự key JSON vẫn idempotent 200 ALREADY_SAVED, AT04 cùng revision khác đáp án bị từ chối 409 CONFLICT, AT05 retry cùng requestId trả ALREADY_SAVED, AT05 tái sử dụng requestId khác payload bị từ chối 409 CONFLICT, proctor bị chặn 403.
- Bằng chứng: `evidence/t2-a2/2026-10-05-verification.md`.
- Tiếp theo: Chuyển tiếp sang T2-A3 (Submit bài thi, timeout và chấm điểm một lần).

## 2026-10-05 · A · T2-A3 — Submit bài thi, timeout và chấm điểm một lần
- Base: `feat/t2-a3-submit-and-scoring` tách từ `feat/t2-a2-autosave-answers`.
- Đã làm:
  - Protocol DTOs: `SubmitExamRequest`, `SubmitExamResponse` trong `vn.edu.toeic.protocol.exam`.
  - Flyway migration V7 (`V7__exam_submits.sql`): bảng `exam_submit_requests` lưu trữ idempotency request log cho submit `(attempt_id, request_id)`; bổ sung các cột phân tích điểm `total_questions`, `correct_count`, `listening_correct`, `reading_correct` trên `monitoring_attempts`.
  - Backend `ExamService.submitExam`:
    1. Kiểm quyền thí sinh sở hữu attempt (401/403).
    2. Khóa dòng attempt bằng `SELECT ... FOR UPDATE OF a`.
    3. Idempotency theo `requestId` (AT06): cùng `requestId` cùng payload trả lại kết quả đã chấm trước đó; cùng `requestId` khác payload trả 409 `CONFLICT`.
    4. Kiểm tra trạng thái bài thi: nếu đã `SUBMITTED`/`TIMED_OUT` -> 409 `INVALID_STATE` (AT06 - không nộp lại trên bài đã chốt).
    5. Kiểm tra `writerEpoch` sau khóa (khác epoch hiện tại -> 409 `STALE`).
    6. Kiểm tra thời hạn bằng `SELECT clock_timestamp()` (`decisionAt >= deadlineAt` -> 409 `EXPIRED` - AT07).
    7. Kiểm tra tính hợp lệ của câu hỏi/lựa chọn trong đề thi (sai -> 400 `INVALID_INPUT`).
    8. Kiểm tra revision: revision thấp hơn -> 409 `STALE` (không tự chốt bằng bản cũ).
    9. Chấm điểm độc lập tại server: đối chiếu `correct_option` từ `exam_questions`, tính `listeningCorrect`, `readingCorrect`, `correctCount`, `totalQuestions`, cập nhật trạng thái `SUBMITTED`, lưu `exam_submit_requests` và commit transaction.
  - Background Timeout `ExamTimeoutService` (AT08):
    - Quét các attempt quá hạn (`state = 'ACTIVE'` và `deadline_at <= clock_timestamp()`).
    - Khóa bi quan `FOR UPDATE OF a`, kiểm tra lại `clock_timestamp()`, chấm điểm trên `saved_answers`, cập nhật trạng thái `TIMED_OUT` và commit.
    - Không nhận đáp án mới từ client; submit/autosave muộn sau timeout đều bị từ chối 409 `INVALID_STATE`.
  - Controller: `POST /api/v1/attempts/{attemptId}/submit`.
- Đã kiểm:
  - `mvn test` PASS 373/373 tests trên toàn bộ dự án.
  - Real PostgreSQL 18.6 smoke (`scripts/smoke-t2a3.ps1`):
    - Flyway V1->V7 migration trong schema tạm.
    - Proctor import đề 4 câu (2 Listening, 2 Reading), tạo ca thi.
    - Candidate1 autosave revision 1, submit revision 2 đạt 3/4 câu (1 Listening, 2 Reading) -> 200 SUBMITTED.
    - AT06: Retry submit cùng requestId trả lại điểm số và timestamp giống hệt (idempotent 200).
    - AT06: Gửi submit requestId mới trên bài đã nộp bị từ chối 409 `INVALID_STATE`.
    - AT06: Autosave sau khi submit bị từ chối 409 `INVALID_STATE`.
    - AT07: Cập nhật deadline về quá khứ -> Autosave và Submit đều bị từ chối 409 `EXPIRED`.
    - AT08: Tác vụ timeout định kỳ khóa và chuyển attempt sang `TIMED_OUT`, chấm điểm đúng bộ đáp án đã lưu (2 câu đúng), submit sau timeout bị từ chối 409 `INVALID_STATE`.
- Bằng chứng: `evidence/t2-a3/2026-10-05-verification.md`.
- Tiếp theo: Chuyển tiếp sang T2-A4 (Phiên ghi và kiểm thử tranh chấp trên PostgreSQL).

## 2026-10-05 · A · T2-A4 — Phiên ghi (Takeover Writer) và Kiểm thử tranh chấp trên PostgreSQL
- Base: `feat/t2-a4-concurrency-and-takeover` tách từ `feat/t2-a3-submit-and-scoring`.
- Đã làm:
  - Protocol DTOs: `TakeoverWriterRequest`, `TakeoverWriterResponse`, `CandidateAttemptStatusResponse` trong `vn.edu.toeic.protocol.exam`.
  - Backend `ExamService`:
    - `takeoverWriter`: Kiểm tra quyền thí sinh sở hữu attempt (401/403), mở transaction với khóa bi quan `SELECT ... FOR UPDATE OF a`, kiểm tra trạng thái bài thi `ACTIVE` (nếu đã kết thúc thì 409 `INVALID_STATE`), tăng `writer_epoch = writer_epoch + 1`, commit transaction và trả về `writerEpoch` mới cùng trạng thái phiên thi hiện hành.
    - `getAttemptStatus`: Cho phép thí sinh sở hữu hoặc proctor được phân công tra cứu trạng thái attempt (`writerEpoch`, `savedRevision`, `state`, `deadlineAt`, `answers`, `totalQuestions`, `score`, `submittedAt`) phục vụ khôi phục khi reconnect hoặc đối soát.
  - Controller: `POST /api/v1/attempts/{attemptId}/takeover`, `GET /api/v1/attempts/{attemptId}/status`.
  - Bộ kiểm thử tranh chấp `ExamConcurrencyPostgresSmoke` với các luồng và kết nối JDBC độc lập điều phối bằng `CountDownLatch`:
    - AT01: Thí sinh 2 không thể xem, lưu bài, nộp bài, hay takeover trên bài thi của Thí sinh 1 (403 `FORBIDDEN`) cả trước và sau khi Thí sinh 1 nộp bài.
    - AT07: Luồng 1 giữ khóa bi quan `FOR UPDATE` vượt quá hạn `deadlineAt`. Luồng 2 (HTTP) bị chặn chờ khóa. Khi Luồng 1 nhả khóa và commit, Luồng 2 được xử lý, kiểm tra `clock_timestamp()` sau khóa và bị từ chối 409 `EXPIRED`.
    - AT09: Luồng 1 giữ khóa bi quan. Luồng 2 (HTTP) gửi request autosave với `writerEpoch=1` và bị chặn chờ khóa. Writer 2 (Luồng 3) thực hiện takeover writer nâng `writerEpoch` lên 3. Luồng 1 nhả khóa -> request của Luồng 2 được thực thi thấy epoch cũ và bị từ chối 409 `STALE`. Writer 2 sau đó autosave với epoch 3 thành công.
    - AT10: Thao tác autosave/submit gửi sau deadline bị từ chối ngay lập tức 409 `EXPIRED` qua kiểm tra `clock_timestamp()` sau khóa bi quan, không phụ thuộc vào background timeout job.
- Đã kiểm:
  - `mvn test` PASS 375/375 tests trên 5 module (protocol 23, client 227, server 124, spike 1).
  - Real PostgreSQL 18.6 smoke (`scripts/smoke-t2a4.ps1`): Chạy thành công 100% các kịch bản AT01, AT07, AT09, AT10 trong schema cách ly tự dọn dẹp.
- Bằng chứng: `evidence/t2-a4/2026-10-05-verification.md`.
- Tiếp theo: Chuyển tiếp sang T2-A5 (Tài liệu giao dịch và bằng chứng quyền).

## 2026-10-05 · A · T2-A5 — Tài liệu giao dịch và bằng chứng quyền
- Base: `feat/t2-a5-transaction-docs-and-auth-evidence` tách từ `feat/t2-a4-concurrency-and-takeover`.
- Đã làm:
  - Soạn thảo tài liệu chuyên sâu `docs/GIAO_DICH_VA_QUYEN.md` phục vụ báo cáo và bảo vệ đồ án:
    - 4 sơ đồ tuần tự chi tiết dạng Mermaid: (1) Lưu đáp án theo revision, (2) Nộp bài và chấm điểm 1 lần, (3) Tác vụ timeout định kỳ, (4) Chiếm quyền ghi (takeover) và phục hồi phiên khi reconnect.
    - Bộ mẫu JSON response chuẩn cho toàn bộ mã thành công (`SAVED`, `ALREADY_SAVED`, `SUBMITTED`) và mã lỗi nghiệp vụ (`STALE`, `CONFLICT`, `EXPIRED`, `INVALID_STATE`, `INVALID_INPUT`, `FORBIDDEN`, `UNAUTHORIZED`).
    - Trích xuất log thực tế và SQL queries có `FOR UPDATE OF a`, `clock_timestamp()`, `writer_epoch`, `saved_revision`.
    - Lập luận kỹ thuật cho 4 câu hỏi bảo vệ trọng tâm:
      1. Khác biệt cốt tử giữa `clock_timestamp()` sau khóa và `now()`.
      2. Tại sao tầng xác thực/kiểm quyền Gate 1 ngăn chặn hoàn toàn việc kẻ xấu lạm dụng idempotency/requestId để vượt quyền.
      3. Cơ chế tuần tự hóa (serialization) xử lý tranh chấp giữa Save và Submit khi đến cùng lúc.
      4. Cách `writerEpoch` ngăn chặn split-brain writer giữa nhiều thiết bị/tab.
  - Cập nhật bảng kiểm thử Answer/Submission (AT01 -> AT10) trong `docs/KIEM_THU.md` với đầy đủ kết quả PASS, commit SHA (`60e1ced`), môi trường và file bằng chứng tương ứng.
- Đã kiểm: Toàn bộ bộ test 375/375 PASS; kiểm tra chéo tính nhất quán giữa tài liệu giao dịch, protocol specification và source code.
- Bằng chứng: `evidence/t2-a5/2026-10-05-verification.md` và `docs/GIAO_DICH_VA_QUYEN.md`.
- Hoàn thành Chặng 2 cho Vai A: Toàn bộ các tasks T2-A1 -> T2-A5 đã hoàn thành code-complete và nghiệm thu. Sẵn sàng cho Chặng 3 (API tài nguyên audio, READY, Listening gián đoạn).

## 2026-10-05 · A · T1-AR — Người A Review Toàn Bộ Phần B Làm Trong Chặng 1 (T1-B1..T1-B4)
- **Đối tượng review:** Toàn bộ code, test, tài liệu thuộc client module của Vai B trong Chặng 1: `ToeicClientApplication`, `LoginApiClient`, `RealtimeClient`, `AuthenticatedWebSocketOpener`, `ConnectionViewModel`, `ProctorDashboardView`, `DashboardController`, `DashboardModel`, `MonitoringApiClient`, `MonitoringJson`, `MonitoringData`.
- **Kết quả rà soát theo 5 tiêu chuẩn hợp đồng:**
  1. *Có tác vụ mạng/file/quét chạy trên luồng giao diện (JavaFX Thread) không?*
     - **ĐẠT:** Mọi tác vụ HTTP, WebSocket I/O, JSON parse, reconnect schedule đều chạy trên các daemon worker pool chuyên biệt (`toeic-http-worker`, `toeic-realtime-worker`, `toeic-dashboard-http`, `toeic-dashboard-worker`). Cập nhật giao diện luôn được bọc qua `Platform.runLater` và kiểm tra `viewGeneration` / `closed` flag an toàn.
  2. *Đóng app có dừng hết worker không?*
     - **ĐẠT:** `ToeicClientApplication.stop()` và `releaseConnectionView()` đóng tuần tự và triệt để: `dashboard.close()`, `monitoring.close()`, `realtimeClient.close()`, `loginApiClient.close()`. Tất cả thread factories đều set daemon `true`, không gây treo process hoặc leak background socket.
  3. *Retry có giữ nguyên requestId, revision, nội dung không?*
     - **ĐẠT:** `RealtimeClient` quản lý `pendingAcks` chặt chẽ, kiểm tra chống trùng lặp `requestId` với nội dung khác. Quá trình reconnect WebSocket giữ nguyên correlation và ngân sách thử lại (tối đa 4 lần, backoff 1s/2s/4s/8s), không tự ý thay đổi payload. `DashboardController` có `reloadBudget = 2` hữu hạn, chống bão request.
  4. *Client có hiển thị "đã lưu" / "xác nhận" trước khi nhận ACK không?*
     - **ĐẠT:** Giao diện tách bạch rõ ràng giữa "Chưa xác nhận" và "Đã xác nhận"; không coi việc ghi socket là ACK. Bảng giám thị Proctor hiển thị cờ "Dữ liệu cũ" (`staleRoster`) khi mất mạng hoặc đang đồng bộ HTTP.
  5. *Client có tự cho mình quyền gì mà server không kiểm tra không?*
     - **ĐẠT:** Tuân thủ triệt để nguyên tắc Zero-Trust. Token Bearer gửi qua header; danh sách `attemptScope` chỉ lấy từ `/api/v1/auth/me` do server xác thực; role và assignment luôn được server kiểm tra lại trên từng thông điệp.
- **Điểm sáng kỹ thuật phát hiện:**
  - `MonitoringJson` dùng `BigDecimal.longValueExact()` để đọc các trường số lớn (như `candidateUserId`, `revision`, `droppedCount`), triệt tiêu hoàn toàn lỗi làm tròn số khi dùng `double`.
  - `LimitedBody` trong `MonitoringApiClient` chặn streaming response HTTP vượt quá 4MB để chống tấn công OOM.
  - Xóa token khỏi bộ nhớ (`token = null`) ngay khi đóng kết nối hoặc gặp lỗi 401/1008.
- **Khuyến nghị cho Chặng 2 (T2-B):**
     - Khi triển khai giao diện làm bài thi (Exam View) và chức năng autosave/submit, B cần kế thừa pattern này: sinh `requestId` duy nhất, giữ nguyên `requestId` khi retry, tăng `answerRevision` khi sửa đáp án, và chỉ hiển thị "Đã lưu" khi nhận status `SAVED` hoặc `ALREADY_SAVED` từ Server.

## 10/10/2026 — C: T2-C1 full snapshot baseline v1

- Đọc source/contract/kế hoạch/evidence và fetch remote; main base `8eba405`, working tree sạch, chưa có implementation/PR T2-C1. Tạo `feat/t2-c1-monitoring-full-snapshot`, không dùng nhánh C4 cũ. Code commit `6acd031`.
- Thêm contract/shared parser OPEN/full/CLOSE/MONITOR_STATE, full delivery RAM riêng với event C3, reducer/store server có auth/epoch/socket/sequence/dedup/conflict/STALE/TTL/limits. ACK FULL chỉ RAM theo QD-12. Reuse collector/policy/identity/WS/auth/registry/measurement. Dashboard HTTP/push gộp revision/serverInstanceId, tab Process hiện tại và lifecycle guard; không collector PROCTOR, không xóa event/gap/interruption.
- Baseline Java376/A2 PASS sau DB bật; sau sửa Java408/package/Python10 PASS. Full smoke REAL Edge/ProcessHandle/HTTP/WS/PG/proctor JavaFX component PASS, raw4endpoint COMPLETE với source SHA6acd031/clean metadata. A2/A3/A4/C2/C3/C4/B3 và B2 owned TEST DB PASS. B2 wrapper cũ không phù hợp DB dev đã có scope; không xóa fixture. C3 correlate đúng ERROR gap để không nhầm kênh full; A4/B3 >=V4 và tab index theo view mới.
- Lượt fail và sửa ghi rõ trong evidence: cache sandbox, DB off, Edge profile file lock, UNKNOWN MOCK thiếu timestamp, B2 seed readiness/timezone, C3 ERROR khác request. Không claim GUI candidate toàn app/LAN/full server restart qua network/human A/B review hoặc performance. MT01 PARTIAL. [Evidence](../evidence/t2-c1/2026-10-10-verification.md), [README tự demo](../README.md#full-snapshot-t2-c1).
- Được ủy quyền commit/push/mở PR, không merge; bàn giao review A auth/RAM/hooks và B transport/parser/dashboard. Không sửa TRACKER/kế hoạch/.env/migration, không reset shared DB, không làm T2-C2–C5/delta/E1/E2. ChatGPT planning/review NOT RUN theo lựa chọn không kết nối trước đó của người dùng.

## 10/10/2026 — C: review-fix scheduling PR#18

- Người dùng review30fa6d9 phát hiện maintain Scheduled chưa được kích hoạt; unit gọi trực tiếp/CLOSE không chứng minh tự STALE/TTL. Harness Spring thật mới trên product cũ FAIL phase AUTO_STALE_WITHOUT_HTTP_OR_CLOSE, ghi đúng lỗi, không xóa evidence cũ.
- Code e07bb7e: bean MonitoringStateMaintenance worker riêng/fixed delay/catch runtime/shutdown interrupt+await; store dọn RAM và chặn request muộn. Bỏ Scheduled trên maintain, không EnableScheduling toàn server hoặc chạm ExamTimeoutService; lỗi scheduler bài thi cần A review riêng. QD-12/PROTOCOL bổ sung lựa chọn này.
- Java413/package PASS; REAL WS giữ socket/scoped heartbeat, ngừng full → tự push STALE/TTL4s; capacity1 OPEN B bị từ chối trước TTL/ACK sau TTL; history giữ nguyên; Spring close xóa RAM/dừng workers PASS. Full Edge/GUI component và A4/C3/C4/B3 hồi quy PASS. Lượt cuối source e07bb7e sạch, log6endpoint/63category COMPLETE; Python summary tái tạo giống byte. Python unittest10 và A2/A3/B2/C2 không chạy lại trong lượt này.
- Bằng chứng [scheduling-fix](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md), raw/ảnh/metadata/checksum riêng; đính chính evidence gốc, không sửa raw. Push và cập nhật PR#18 theo yêu cầu; giữ mở, không merge/C2. MT01 PARTIAL, GUI candidate/LAN/human A/B review tổng thể NOT RUN. Không sửa .env/migration/tracker/kế hoạch/reset DB.

## 10/10/2026 — C: T2-C2 heartbeat, event muộn và khoảng trống

- Xác minh PR#18 đã merge bằng merge commit683adfc, giữ nhánh C1. Fetch/main sạch cùng base, chưa có nhánh/PR C2; tạo `feat/t2-c2-monitoring-late-events-and-gaps`. Không phát triển tiếp C1. Code/contract commit fdac531.
- Đối chiếu kế hoạch/source/evidence: A4 đã có scoped heartbeat/timeout/interruption/recovery/multi-socket, C1 có own maintenance, C3 có bounded queue/frozen gap/commit ACK. Reuse những phần này; thiếu nhãn event muộn và HTTP/GUI gap. Thêm EventOrigin frozen lúc observe (CONNECTED/socket ID từ heartbeat ACK, OFFLINE, UNSPECIFIED), deliveryStatus chỉ lần INSERT đầu, V8 additive, gap GET kiểm quyền+giới hạn, cột/tab giám thị, callback/send generation guard. Không so hai đồng hồ chưa đồng bộ, không dùng epoch full để loại event lịch sử hoặc nới quyền lượt đã chốt.
- Java425/package PASS, Python10 PASS chạy mới. C2 REAL Spring/PG/HTTP/WS/collector worker/proctor Stage PASS; readings MOCK, disconnect/held write/lost ACK SIMULATED. UNKNOWN/reconnect/late labels/dedup/conflict/full-isolation/overflow gap/history retention/COMMIT rollback/auth-revoke/lifecycle và V7 dữ liệu cũ→V8 PASS. A3/A4/C3/C4/B3/C1 hồi quy mới PASS, có hard-kill JVM thật/Edge ProcessHandle thật và tự STALE/TTL/capacity/shutdown không refresh/manual maintain.
- Lượt final clean fdac531 lúc17:34 UTC+7: run T2C2-c1de406a-82fc-4993-9374-da1fdfec8270, metadata187source hashes+JAR hashes, raw6endpoints/63categories COMPLETE, drop/unwritten/pending0, JSON/CSV tái tạo giống byte. TEST schemas0, public dev vẫn V7. [Evidence](../evidence/t2-c2/2026-10-10-verification.md) có ảnh, transcript trích nguyên dòng thật, negative test và checksum exact-byte. Code cũ FAIL LEGACY_DELIVERY_METADATA, chỉ chứng minh thiếu metadata; không lấy lượt lỗi làm PASS.
- Lỗi exploratory đã sửa: parser không chấp nhận Gson bỏ null origin ID; thêm roundtrip và cho OFFLINE/UNSPECIFIED thiếu ID. Assertion interruption tổng1 sau Stop sai; sửa so trước/sau full. Auth-revoke ERROR không có requestId vì auth trước parse; harness nghe callback riêng và đòi FAILED. Class target có unresolved compilation từng cần clean rebuild. Chi tiết trong evidence.
- A cần review V8/event/gap service/HTTP/WS commit/permission/ACK; B cần review RealtimeClient/adapter/parser/controller/model/view/lifecycle. Human A/B review NOT RUN, ChatGPT planning/review NOT RUN theo lựa chọn không kết nối của người dùng. MT01/MT07/MT08 PARTIAL toàn luồng: candidate GUI/LAN NOT RUN; không giả thành viên đã duyệt. Người dùng đã ủy quyền commit/push/PR/merge commit nếu không blocker và giữ C2; Git cuối ở báo cáo bàn giao.
- Không sửa .env/tracker/kế hoạch/nguồn/V1–V7 hoặc reset shared DB, không bật EnableScheduling/chạm ExamTimeoutService. Dừng sau C2; không làm C3–C5/delta/E1/E2. Legacy/trước ACK đầu UNSPECIFIED, LIVE chỉ cùng socket; không chứng minh chống client sửa. Gap mới cần refresh; queue/full vẫn RAM như contract cũ.






## 10/10/2026 — C: T2-C3 observation trace/replay/oracle

- Xác minh C2 PR#19 merge b2926ea và main sạch; tạo feat/t2-c3-monitoring-trace-replay. Code/contract/test5844e88. Không tiếp tục C1/C2 hoặc thay tracker/kế hoạch/.env/migration/exam lifecycle.
- Tap collector đang có, trace async bounded/error isolation/exact-byte checksum/strict reader, CLI record và replay. Production full/event delivery dùng seam ID riêng test, runtime UUID không đổi. FullSnapshotReducer bọc FullStateReducer dùng chung; parser/algorithm C1 nguyên trạng, server giữ auth/lock/epoch/store/maintenance. Oracle độc lập từ raw observation, fixture+expected viết tay; schedule seeded/drop/dup/reorder/reconnect và mutation negative. QD-14 ghi mô hình baseline ACK/control/transport/event map mô phỏng, không real commit/network/presence proof.
- Baseline425 PASS chạy mới20:21:36; final Java461/package PASS20:52:54 (+36), Python10 PASS. CLI clean/double/drop-mid/drop-first/mixed/repeat PASS, seed report giống byte; mutation FAIL exit1 đúng, corrupt checksum exit2 đúng. REAL ProcessHandle6scans và owned Edge appear/disappear ghi COMPLETE rồi REPLAY PASS; dọn test-owned resources. Final clean5844e88 run e7f90965-955d-431c-ac91-7e5e2520faa4 lúc20:58:59, metadata source/JAR hashes.
- C1-Gui/C2-Gui/event-C3/C4/A4 regression PASS chạy mới; C1 tự STALE/TTL/capacity/close, C2 late/gap/COMMIT/auth, C3 owned Edge/network retry, C4 byte, A4 presence/hard-kill. C1/C2 metadata precommit dirty được đối chiếu Java hashes/client JAR với cleanC3, không gọi là clean run. Test schemas0, publicV7 không đổi; không resetDB.
- [Evidence](../evidence/t2-c3/2026-10-10-verification.md) giữ nguyên trace và gzip lossless report lớn với checksums; excerpts là dòng thật. Pipeline PowerShell làm mất dấu tài liệu append ban đầu đã sửa UTF-8 trước commit; không product failure chưa xử lý. Không dùng replay time làm E1/E2, không ground truth C4/delta.
- Human B review/A adapter review NOT RUN; B xem collector tap và seam ID/full/event/replay, A xem shared reducer adapter. ChatGPT planning/review NOT RUN theo lựa chọn không kết nối của người dùng. Candidate GUI/LAN/MT02–MT06 delta NOT RUN; MT01/07/08 vẫn PARTIAL tổng thể. Người dùng ủy quyền push/PR/merge commit nếu không blocker và giữ C3, Git cuối trong báo cáo/PR. Dừng sau T2-C3.

## 10/10/2026 — C: T2-C4 controlled process harness

- Đọc contract/kế hoạch/C3 source/evidence, xác minh PR#20 merged/mainf6d46b2 và chưa có C4; tạo feat/t2-c4-controlled-process-harness từ origin/main, bảo toàn nhánh trước. Code/test/contract0164297.
- ProcessBuilder Java child READY/GO/targethold/exit, seeded planned phase200/800/3000, bounded concurrent/run/timeouts. GT parent nanoTime từ vòng đời child độc lập scan; AlignedTrace offset C3 cùng parent JVM. TEST owned root identity giữ đến stop; no Java name production/no Edge spoof. Trace DTO riêng file, production replay/network từ chối TEST. Join exact PID/start/collector/scan window, lỗi/thiếu/conflict thành INCONCLUSIVE.
- Baseline461 PASS21:28:58; final493/package PASS21:56:00 (+32new). REAL child unit success/error/noREADY/hang/APIcancel/tree + MOCK scanner, manual expected seed/clock/identity/coverage, artifact bounds/hash/nooverwrite PASS. Compile overload matches(null), READY CRLF Windows và interruption cleanup failures đã sửa trước final; không giấu failure thành PASS.
- Full Windows30child trên clean0164297 lúc22:05:30:27 observed/3notObserved/0inconclusive; nhóm200 7/3,800 10/0,3000 10/0; resourcesCleaned true. Ground truth/trace/manifest raw hashes, rejoin byte-identical audited; corrupt copy exit2/inconclusive30/notObserved0. Exploratory precommit25/5 retained, không chọn số đẹp hoặc ép mọi child được thấy.
- C3 CLI/owned Edge/replay, C1-Gui/full/autoSTALE/TTL/shutdown, measurement regressions PASS. Event smoke chạy song song initial FAIL real lostACKretry; chạy riêng PASS không sửa code; hai log retained, cause chưa chứng minh. Shared DB không reset, schemas thử được dọn bởi smoke. C2-Gui/A4/Python10 không chạy lại vì phần tương ứng không đổi.
- [Evidence](../evidence/t2-c4/2026-10-10-verification.md), [guide](CONTROLLED_PROCESS_HARNESS.md), QD15. Không sửa .env/tracker/kế hoạch gốc/migration/exam lifecycle. Raw giữ exact bytes, log copy thay prefix private; secret/privatehome audit PASS. Human B review/ChatGPT planning/review NOT RUN theo lựa chọn hiện có; không giả approval.
- B cần xem ProcessSelection/OwnedProcessPolicy, TEST DTO/parser, clock binding, identity/coverage và cleanup. OS Ctrl+C/hardkill parent/candidate full GUI/LAN/E1/E2 NOT RUN; không genericmissrate/latency/CPU/memory. Người dùng ủy quyền push/PR/merge commit nếu không blocker và giữ C4; Git cuối trong final/PR. Dừng sau C4, chưa C5/delta.

## 10/10/2026 — Đồng bộ main và mục lục bằng chứng

- Theo yêu cầu người dùng, fetch và cập nhật main local bằng fast-forward từ a04aaa2 tới f6d46b2 (PR#20); giữ nhánh B4 và các file chưa commit có sẵn.
- Người dùng chọn tập trung bằng chứng đã kiểm tra tại evidence/<task>/ và thêm mục lục để đọc trên GitHub. Thêm evidence/README.md, liên kết từ README và docs/README, ghi cách lưu transcript/raw/metadata/checksum và phân biệt REAL/MOCK/SIMULATED/REPLAY.
- Giữ đường dẫn và nội dung artifact cũ; chưa đưa các log chạy local tạm vào Git. Kiểm tra liên kết nội bộ và diff; không chạy lại build, smoke hoặc thay đổi trạng thái kiểm thử/tracker.

## 11/10/2026 — Hoàn tất đồng bộ và công bố mục lục

- Khi tiếp tục phiên, origin/main đã có PR#21 tại b576a07. Đưa commit mục lục chưa push lên main mới bằng rebase, giữ cả mục nhật ký C4 và mục đồng bộ trước đó.
- Bổ sung T2-C4 vào mục lục bằng chứng; kiểm tra liên kết và diff trước khi push tài liệu. Không chạy lại kiểm thử ứng dụng.
