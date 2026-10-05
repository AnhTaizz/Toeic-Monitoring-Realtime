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

## 2026-10-05 · B · T1-B4 — đóng gói Windows và audio (PARTIAL)

- Thời lượng: khoảng 1 giờ phiên Agent (Claude Code); không tính vào giờ của thành viên. Base main `a04aaa2`, nhánh `feat/t1-b4-windows-package-audio`. `task.txt` và `.env` giữ nguyên, không commit.
- Đã làm: thêm `org.openjfx:javafx-media` 21.0.12; harness TEST `AudioSmokeHarness` + `ToneWav` trong test source (Play/Stop, READY/PLAYING/STOPPED/ERROR, bắt `MediaException`/`onError`, nạp bằng `path.toUri()`), fixture tone MP3/M4A; `scripts/package-client.ps1` (jpackage app-image, chỉ đưa fat JAR vào `--input`, tùy chọn `-WithAudioSmoke`); `scripts/smoke-b4.ps1`. Không đổi `client/src/main`, server, protocol.
- Commit: `2d5a985` (media + harness), `9cbaf8b` (script); commit tài liệu ngay sau đó.
- Đã kiểm: baseline `mvn test` 331/331; final `mvn test`/`mvn package` 338/338 (client 227, server 110, spike 1). App-image 158,4 MB, 287 file, có runtime, không có `java.exe`. Smoke B4 lượt cuối 21 PASS / 1 FAIL / 1 BLOCKED: app mở/đóng sạch ở thư mục build và thư mục có khoảng trắng; WAV/MP3/M4A PLAYED trong app-image với thư mục audio có dấu; file hỏng/thiếu → ERROR. Bản bàn giao + server thật trên cùng máy, điều khiển bằng UI Automation: sai mật khẩu, login candidate/proctor qua URL IPv4 LAN của máy, `TOEIC_SERVER_URL`, WS, 21 event cho 21 `msedge.exe` không lặp theo poll, cảnh báo trên dashboard, ONLINE → UNKNOWN, đóng app hết process — 18/18 bước.
- FAIL đã ghi: app-image đặt trong `Thử nghiệm TOEIC\` (ký tự ngoài code page ANSI 1252) không khởi động, `could not find java.dll`; thư mục có dấu trong code page chạy được. Tham số dòng lệnh chứa ký tự ngoài code page cũng hỏng. Thử nghiệm ngoài repo: nhúng `activeCodePage=UTF-8` vào manifest launcher thì app và audio chạy được từ thư mục đó; chưa đưa vào script.
- Chưa kiểm: máy Windows thứ hai và LAN hai máy (NOT RUN, không thay bằng localhost); nghe bằng tai; file MP3 dài; Windows bản N; server tắt/bật lại trên bản đóng gói; human A review.
- Quyết định: QD-07 chốt MP3, file ngoài JAR, nạp bằng URI. Mở QD-11 (đang chờ) cho đường dẫn cài đặt có ký tự ngoài code page.
- Còn dở: T1-B4 PARTIAL, IT01 PARTIAL. Xem "Ghi chú làm dở" trong VAI_B.
- Tiếp theo: chạy checklist máy thứ hai trong `evidence/t1-b4/2026-10-05-verification.md` trước gate 08/10; quyết QD-11 trước T3-B5.
- Cần người khác: nhóm cung cấp máy Windows thứ hai; A review và duyệt QD-11; **C** sửa `scripts/demo-c3.ps1 -Action Cleanup` (lỗi khóa ngoại với `monitoring_presence`/`monitoring_interruptions` của V4 — phiên này đã xóa tay dữ liệu `DEMO-C3-A` do chính nó tạo, không sửa script). A/C lưu ý DB dev trên máy build vừa được Flyway nâng từ V1 lên V4.
- Tài liệu: README, CLAUDE.md (lệnh), TIEN_DO, KIEM_THU, QUYET_DINH, VAI_B, evidence `t1-b4/`. PROTOCOL không đổi (không đổi contract mạng). TRACKER.json không đổi.
