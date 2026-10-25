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

## 11/10/2026 — C: T2-C5 phương pháp baseline và protocol

- Fetch xác minh main82557bf (thêm mục lục evidence sau C4 PR#21/b576a07), workspace sạch; nhánh docs/t2-c5-monitoring-baseline-methodology từ main. Commit phương pháp eb6ba59; không tiếp tục nhánh C4 hoặc thay runtime.
- [MONITORING_BASELINE_METHOD](MONITORING_BASELINE_METHOD.md): production8tên/TEST, identity/metadata, empty vs source failure, event/current-state/presence/gap, epoch/sequence/revision và RAM restart; ACK full RAM khác event/gap commit. Default source riêng smoke.
- E1 định nghĩa targetGO/startup proxy/parent-observed lifetime/scan bounds/clock offset, OBSERVED/NOT_OBSERVED_IN_TRACE/INCONCLUSIVE và metadata độc lập. Rate/actual-lifetime bins là đề xuất chưa có analyzer, chung eligibility O/N; latency kernel NOT MEASURED; không clip PRE_GO âm. Harness chưa có warmup15s/window120s/resource sampler. E2 chỉ phương pháp, TEST trace bị production replay từ chối, thiếu delta/oracle/correctness gate; byte JSON transport/outcome/endpoint/window tránh đếm đôi và scope HTTP/framing chưa đo.
- Đồng bộ PROTOCOL, measurement type/sequence mô tả cũ, trace scope C4, harness note, README/index; không thêm QD kiến trúc vì chỉ đối chiếu hiện có. Giữ raw/old checksums, log event concurrent FAIL và isolated PASS; cause chưa chứng minh. C3 replay PASS vẫn có6missingBusinessEvents trong owned-edge-seeded. C4 trial006 target200/lifetime329,8944ms/hold211,9458ms minh họa đúng proxy.
- Kiểm tra mới: Markdown link/anchor, tên method/field/schema/default, diff scope và checksum C3/C4/gzip restoration. [Report/checks](../evidence/t2-c5/2026-10-11-verification.md). Java493/Python10 thuộc task trước; C5 không chạy lại runtime tests/smoke/benchmark. B human review và ChatGPT review NOT RUN theo opt-out hiện có; GUI/LAN/MT toàn hệ thống/E1/E2 không nâng PASS.
- Không sửa .env/tracker/kế hoạch gốc/migration/exam code/collector/harness/reducer/old evidence. B cần chốt window/eligibility/actual bins/uncertainty/workload và byte endpoint/correlation trước đo; công cụ còn thiếu phải task riêng. Người dùng ủy quyền push/PR/merge commit nếu không blocker, giữ C5 và sync main an toàn; kết quả Git cuối trong PR/final. Dừng sau T2-C5.

## 11/10/2026 — C: T3-C1 encoder delta candidate

- Fetch/main12e2838 sau C5PR#22, branch mới feat/t3-c1-monitoring-delta-encoder. Code33828d4, thêm2pending-ACK cases/giữ full stop counter7041c6e. PR17UI/PR23exam fix vẫn riêng, chưa hook delta A/T3-A3. Người dùng ủy quyền code/tests/docs/commit/push/PR/merge commit khi không blocker và giữ nhánh; Git cuối trong PR/final.
- Reuse FullSnapshotDelivery lifecycle/transport guard/ACK/backoff/worker, event delivery nguyên trạng. DeltaSnapshotPayload added/removed identity canonical; base acknowledgedState chỉ tiến sau ACK đúng, queue coalescing1latest+1pending, metadata sameidentity full, sourcefailure khác empty; retry frozen whole message/max5, reconnect/exhaustion cần fresh scan/OPEN/full1. Full đối soát30s monotonic; public full constructor/mọi scan/default production/counter stop giữ baseline, transport capabilityfalse không bật delta.
- Replay/CLI mode full default, delta candidate với sequential adapter SIMULATED; raw oracle/independent sequence/epoch/checkpoints including no frame, event maps cùngIDs/content. Manual expected fixture, metadata/PIDreuse/nullstart/reappear/empty/failure/negative delta mutation được kiểm. Delta drop/reorder/resync gate chưa làm, CLI từ chối; TEST C4 vẫn refusal.
- Baseline493 mới PASS19:58:25; final531/package PASS20:21:48 (protocol35/client353/server142/spike1,38new). Real scheduled worker/latch/cleanup; snapshots/clock/ACKfault MOCK/SIMULATED. CLI fixture/reconnect/dup100/full faults PASS, mutationdelta/full FAIL đúngexit1, TEST/futureconfig refuseexit2;6reports repeats byte-identical. Final REAL ProcessHandle6scans trace replay cảmode PASS45business/events nội dung giữ; initialcapture49backgroundevents retained, không so haiworkloads/không performance claim.
- Hồi quy REALfull-Gui/source7041c6e: Edge/WS/PG/proctor component, tự STALE/TTL/capacity/shutdown,6endpoints63categories COMPLETE. C3event/A4presence/C4measurement source33828d4 chạy tuần tự PASS; không rerun chúng sau fix stop counter không đổi wire. Measurement3endpoints26categories COMPLETE; raw/checksums/gziprestore/source228manifest/finalsummaryJSONCSV byte-identical. Schema TEST0/publicV7, không reset DB.
- Package ERROR STALE mới initialFAIL vì giữ ACK deadline; sửa candidate về now rồi PASS, giữ transcript. DB regression đầuMIGRATIONfail doDocker chưa chạy; bật Docker Desktop/compose volume cũ rồi PASS, không sửa.env. Audit lầnđầuKeyError dùngsummaries thayrows đã sửa auditor, khôngraw/runtime. [Evidence](../evidence/t3-c1/2026-10-11-verification.md), [guide](MONITORING_DELTA_ENCODER.md), QD16.
- A/C cần server nhận delta/binding/auth/epoch/seq/dedup/resync/freshness successful scan và giải quyết STALE6s vs full30s/skipunchanged trước production; không heartbeat gia hạn/tăngtimeout ngầm. B cần capability negotiation/ACK/guard/lifecycle/measurement classification và review. Human A/B/ChatGPT planning-review NOT RUN theooptout; fullcandidateGUI/LAN/MT02–MT06gate/Pythonunit/E1/E2 NOT RUN. Không fake approval hoặc status27case/tracker.
- Không sửa tracker/kế hoạch gốc/.env/migration/server/exam/dependencies. Dừng T3-C1; không T3-C2/gate hoặc tự bật production/benchmark. Git cuối trong PR/final.

### Đính chính evidence T3-C1 cùng phiên

- Log PowerShell gốc là UTF-16 BOM; bước sao chép transcript đầu đọc sai UTF-8, dẫn NUL/binary diff. Đã tạo lại bản sao từ đúng log gốc, decode BOM→UTF-8/LF/sanitized prefixes; originalSHA không đổi, derivedSHA cập nhật và không NUL/replacement. Không thay runtime hoặc chạy lại tests vì sửa format evidence. Privacy/source/hash audit chạy lại trước PR; không giả raw/test PASS mới.

## 12/10/2026 — C: T3-C2 reducer delta phía server

- Fetch/base144fe55 sau PR24, branch feat/t3-c2-monitoring-delta-reducer; PR17/23 giữ riêng. Code/contract/harness1c3f982, independent oracle cca9003/e241cc6, sửa constructor oracle4042b1c. Final runtime source4042b1c, docs/evidence commit và PR/merge SHA cuối trong PR/final; không sửa runtime sau package/smokes.
- MonitoringStateReducer production chung full/delta, cache64ID typed canonical digest, validate working copy atomic, thiếu base giữ last-known/UNSYNCED/RESYNC, full cao phục hồi. Reuse auth/ACTIVE scope, binding và khóa store/worker C1; service/handler delta, resync correlated và capability explicit opt-in. Replayer bỏ adapter giả, dùng core production với raw oracle độc lập. A review auth/binding/ACK/RAM/TTL/push; B review transport/capability/parser/observation clock/lifecycle. Không đánh dấu T3-A3 DONE thay A.
- Full vẫn mặc định, candidate factory không chọn delta. Successful scan mới không đổi sau2s phát delta rỗng seq mới; duplicate không touch. Cached/aged snapshot bị chặn, full30s chỉ từ scan mới, lỗi/treo không refresh; heartbeat chỉ presence. Baseline STALE6s/TTL5phút giữ nguyên. ACK state RAM khác ACK event/gap sau DB commit; history không xóa.
- Baseline531 PASS20:31:23; final554/package PASS21:25:37 (protocol43/client361/server149/spike1,+23); Python10 PASS. Expected viết tay/core/auth/atomic/cache và race latch; clock/snapshot/scope MOCK đúng nhãn. Final7smokes tuần tự PASS: t3c2/t3c1/t2c1-Gui/t2c2-Gui/c3/a4/c4. C2 REAL Spring/PG/HTTP/WS/collector worker/parser/restart/autoSTALE-TTL/shutdown, readings MOCK/future-base injection SIMULATED. Token revoke trước duplicate, resync callback và heartbeat ONLINE khi collector lỗi/treo được kiểm thật.
- Finaldelta run T3C2-8e57770e-105b-4396-bd30-75cdac832d14:4endpoints57categories COMPLETE,0drop/unwritten/pending. CLI REAL ProcessHandle6scans100ms replay full/delta PASS45events cùngIDs/content; replay network/control SIMULATED. Default full regressions không DELTA/RESYNC.69artifact hashes/gzip/source233manifest/JARs/summary JSON-CSV byte-identical PASS, schemas TEST0/publicV7. Không E2/performance claim hoặc lấy replay làm delta desktop live.
- Initial failures freshness fixture/expected conflict/clock-domain/oracle constructor được sửa trước final; manifest deletedsource và Docker daemon off được xử lý không reset shared DB. Giữ failure/exploratory/final transcripts và raw riêng; decode BOM đúng, chỉ sanitize transcript/prefix, raw bytes/gzip restoration không đổi. [Evidence](../evidence/t3-c2/2026-10-12-verification.md), [guide](MONITORING_DELTA_REDUCER.md), QD17.
- Không sửa .env/tracker/kế hoạch gốc/migrations/exam hoặc PR17/23. Human A/B và ChatGPT planning/review NOT RUN theo opt-out; candidate GUI toàn app/LAN/delta OS live hai máy/gate MT02–MT06/E1/E2 NOT RUN. Người dùng cho phép push/PR/merge commit nếu không blocker C2 và yêu cầu GitHub đáp ứng, giữ local/remote branch, sync main an toàn. Dừng sau T3-C2, chưa C3/C4 hoặc tự bật delta.

## 12/10/2026 — C: T3-C3 acceptance fault replay

- Fetch/base6a41031 sau PR25, branch feat/t3-c3-monitoring-delta-fault-replay, workspace ban đầu sạch; không nhánh/PR C3 cũ. Code/tool/fixture/guide dc87fcc; d337fea chỉ thêm gitattributes giữ exact bytes fixture/evidence. Package trên dc87fcc, final CLI d337fea clean; source/JAR hashes khớp, không gán docs commit cho runtime. PR17/23 riêng.
- Reuse MonitoringReplayDriver/FullSnapshotDelivery/MonitoringDelivery và core MonitoringStateReducer C2. FaultSchedule targeted + seeded eventId channel độc lập/stateordinal riêngmode, reportv2 source/atRecord/outcomes/state/metadata/events/resync/recovery/mutation. ReplayCampaign validate manifest/checksum/production schema,3MOCKworkloads/expectedmanual,72cases/4negatives/summary/repeat. Full default, no deps/migration/message/transport/server/encoder change.
- ACK enumeration trước data fault, drop không ACK-loss; OPEN/CLOSE reliable, RESYNC ghi/no feedback, recovery từ fresh periodic full hoặc reconnect/full1. UNKNOWN/heartbeat/TTL/auth/commit/retry/ACK-loss/livedesktop NOT MODELED. Eventdrop stateSYNCED vẫn missing1; không khôi phục history hoặc gọi efficiency.
- Baseline554 PASS22:16:59; final569/package PASS22:37:27 (43/376/149/1,+15). Focused38PASS sau firstfail2 (nhãn expected LATE nhầm contract và comparison), secondfail1 ép commoncheckpoint seeded; đã sửa policy reference theo EventOrigin PREVIOUS_CONNECTION và không ép state giữa lịchfault divergence. Per-mode oracle vẫn bắt outcome/metadata/eventmap; seeded reconnect báo0common, không fake equivalence. Failures/diagnostic giữ riêng.
- Counterexample old packaged C2 JAR expected PREVIOUS_CONNECTION actual LIVE, final đúng nhãn; fix only replayer current receiving connection, no productionbug claim. Initialcounterexample dùng từ LATE nhầm nhãn cũng giữ; corrected old/final logs và helper source lưu.
- Finalsmoke run2bd621c6-7577-499e-b300-b69fc226a45e: CLI72PASS/4negative detected, all77reports+summary repeatbyte-identical; negative CLIexit1, badconfig/checksum/TEST/nooverwrite exit2. REAL ProcessHandle47business/40delivered/7missing cảmode vớiseeded fault. C1 CLI20c044de-22cf-412a-aa1e-851c97fae7b6 và T2-C3owned Edge421db06a-2832-48c1-a0d6-2da54314c989 regression tuần tự PASS; ownedEdge45business/40delivered/5missing, cleanupPASS. Không so performance hai lượt/nguồn khác nhau.
- [Evidence](../evidence/t3-c3/2026-10-12-verification.md), [guide](MONITORING_DELTA_FAULT_REPLAY.md), QD18. MT02–MT06 PARTIAL toàn gate/replay acceptance PASS; humanB/ChatGPT planning-review NOT RUN theooptout. B cần model/coverage/oracle/mapping/recovery/metadata/history/repeat/bounds; C4 hai desktop/ACK-loss/restart/revoke/control thật/GUI/LAN còn riêng. Không tự C4/gate/E1/E2/.env/tracker/originalplans/migrations/exam hoặc PR A-B; Git cuối PR/final, giữ nhánh và sync main an toàn theo ủy quyền.

### T3-C3 · Kiểm cuối exact fixture bytes cùng phiên

- Audit phát hiện manifest/expected JSON workspace CRLF nhưng initial Git blob LF; d337 thêm -text chưa tự đổi index cũ. Commit d813cb1 renormalize đúng2file theo -text để Git bytes khớp thật. Không sửa observation trace/raw hoặc thuật toán; không giả source hash. Chạy lại package569 PASS22:51:24 và CLI72PASS/4negative/77repeat identical trên d813cb1, runb46f6a5e-e652-4ec4-8a8b-0201ba5b991a;76case files identical earlier d337, metadata/summary sourceSHA mới ghi rõ dirty docs/evidence. Final REAL capture46business/40delivered/6missing, earlier47/40/7 còn nguyên.243source/Gitblob/JAR và222artifact/167gzip PASS; JAR FB05E7D3 unchanged, C1/T2-C3 regressions d337 vẫn cùng runtime source bytes. Raw original UTF16 cli.txt regression giữ nguyên BOM, auditor decode theo BOM. Git cuối trong PR/final.

- Staged diff check bắt derived CSV CRCRLF; regenerate2matrix từ summary nguyên byte bằng explicit UTF8/LF,72rows và hashes/previousDerivedSHA ghi ledger. Không raw/runtime change, không giả test mới; staged check chạy lại.

## 13/10/2026 — C — T3-C4 candidate delta integration/live preparation

Base main ee714fc/C3PR26, branch feat/t3-c4-monitoring-delta-live-gate, code2d867c2 + fix stdoutd9475eb. Java579/PASS, factory production opt-in/current capability, pinned mode, reconnect fresh OPEN/full, safe fallback. Counterexample malformed ACK bật delta trên C3 giữ failure trước/sau. Live exploratory source treo + attempt CLOSED: server FORBIDDEN nhưng coordinator còn active; thêm scoped-heartbeat callback có collector/socket guard, unit và rerun thực kiểm lại. Các lượt lỗi giữ artifact, không sửa raw thành PASS. [Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md) ghi final live/hồi quy/manifest.

Một Windows host/hai candidate JVM + proctor Application thật, PG TEST schema/REAL ProcessHandle/owned Edge; fault injection SIMULATED. QD19, QD10presence nguyên trạng; gate20/10 chưa diễn ra, LAN/human B/A review NOT RUN, không E1/E2/C5/Listening/taskA3 DONE. `.env`/tracker/kế hoạch gốc/migrations/exam/PR A-B giữ phạm vi người dùng. Git push/PR/merge cuối xem PR/final; merge code opt-in off không là human approval/gate acceptance.

## 2026-10-13 · A · PR #23: đồng bộ main T3-C3, review lại ba sửa P0
- Thời lượng: một phiên Agent; giờ của thành viên chưa ghi.
- Đã làm: merge `origin/main` `ee714fc` vào head PR #23 `4753149` trên nhánh cục bộ `sync/pr23-main-20261013`. Conflict chỉ ở tài liệu; quyết định deadline của A đổi số QD-18 → QD-21 vì main đã dùng QD-17/QD-18 cho T3-C2/T3-C3. Đọc lại diff production của ba sửa P0 và ghi bốn điểm không chặn merge.
- Commit: `9713658` (merge) và commit tài liệu kế tiếp. Không push.
- Đã kiểm: `mvn package` 586 PASS, 0 skip với PostgreSQL thật; smoke C1 và C2 PASS; Python 10 PASS (bằng Python 3.14, bản 3.9 trên PATH không chạy được script).
- Còn dở: chưa push nên PR #23 trên GitHub vẫn báo conflict.
- Tiếp theo: người dùng cho phép push thì PR #23 merge được; sau đó PR API lịch sử, rồi PR client của B.
- Cần người khác: C review PR #23 (review xoay vòng C xem A); A xem bốn ghi nhận trong `evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md`.

## 2026-10-13 · A · PR #23: đồng bộ lần hai với main T3-C4
- Thời lượng: cùng phiên Agent.
- Đã làm: `origin/main` tiến lên `cb30f3d` (PR #27, T3-C4) sau lần đồng bộ đầu, nên merge thêm một lần. Conflict chỉ ở tài liệu. Main đã dùng QD-19 cho T3-C4, nên sửa ghi chú đánh số: quyết định API lịch sử của A sẽ mang số QD-23.
- Commit: `c64503f` (merge) và commit tài liệu kế tiếp. Không push.
- Đã kiểm: `mvn package` 596 PASS, 0 skip với PostgreSQL thật; smoke C1, C2 PASS; Python 10 PASS.
- Còn dở: chưa push. Nếu main tiến tiếp trước khi merge thì phải đồng bộ thêm.
- Tiếp theo: xếp lại nhánh API lịch sử và nhánh client của B trên commit này.
- Cần người khác: C review PR #23. Bằng chứng `evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md`.

## 2026-10-13 · A · T3-A1 audio/READY
- Theo yêu cầu mới: hoàn tác toàn bộ T3-B1 của phiên trước, triển khai T3-A1 trên main 80ee1ba (working tree chưa commit).
- Manifest/tải audio theo owner và file tham chiếu, SHA-256/size, READY gắn writer/manifest, V9, proctor readiness và gate start mọi lượt trong transaction. Không thay deadline/answers/monitoring.
- Maven package PASS600 test chạy (16opt-in skip); lượt riêng HTTP/PG/Flyway PASS8audio+11exam. Các lỗi env/JDBC/fixture ban đầu giữ trong evidence và đã sửa; rollback khi COMMIT lỗi, concurrent start và expired-after-lock-wait PASS.
- Contract B/A2: docs/AUDIO_RESOURCES.md. Evidence: evidence/t3-a1/2026-10-13-verification.md.
- Human C review, B1/GUI/audio package/máy thứ hai NOT RUN. A2 WS/playback/gián đoạn chưa làm; không đổi tracker/kế hoạch hoặc ghi giờ chưa đo.

## 2026-10-13 · A · Chia commit và bàn giao nhánh T3-A1
- Theo yêu cầu người dùng, tạo nhánh feat/t3-a1-audio-manifest-ready từ main 80ee1ba và chia commit theo DTO/schema, API, test, contract và evidence.
- Code/schema:363bb90 +8f98e11; test/smoke:9ed067a. Không sửa code đã kiểm thử; hash đối chiếu tại evidence/t3-a1/committed-code-audit.json.
- Evidence giữ kết quả600test +19PostgreSQL và log lỗi ban đầu. Git attributes giữ nguyên byte transcript để checksum không đổi khi checkout; source-audit.json là snapshot workspace trước commit.
- T3-B1 đã hoàn tác; C review/T3-A2/package/LAN vẫn theo giới hạn trong báo cáo. Người dùng yêu cầu push nhánh; trạng thái remote cuối xem history Git và phản hồi bàn giao.

## 2026-10-13 · A · T3-A2 Listening lifecycle
- Theo yêu cầu tiếp tục A2, tạo nhánh feat/t3-a2-listening-lifecycle; T3-A1 đã merge PR #29 vào main8894452 với cùng tree nhánh A1.
- V10 + DTO/API/WS: start sau COMMIT, retry cùng ID, playback/event/heartbeat và interruption bền vững; không đổi monitoring C hoặc client GUI.
- Save/submit giữ phần Listening sau gián đoạn; Reading tiếp tục trong deadline. Assigned proctor tạo replacement mới idempotent, old answers/deadline/history giữ nguyên; timeout vẫn chấm kèm marker.
- REAL HTTP/WS/PG/Flyway PASS29 gồm10 test Listening mới +19 regression; restart Spring thật, worker tự động, upgrade V9→V10, deferred COMMIT rollback và LT04 PASS. Lượt đầu lỗi SQL timestamp đã sửa và giữ log.
- Contract docs/LISTENING_LIFECYCLE.md; evidence/t3-a2/2026-10-13-verification.md. GUI/audio thật/LAN/human C review chưa chạy; không sửa tracker/kế hoạch gốc hoặc ghi giờ chưa đo.

## 13/10/2026 — C · T3-C5 bản nháp đồng bộ và giới hạn

- Fetch main `f97c081ed87184ff80e0778e2d4f9a9133f9c6b0`, workspace sạch; tạo `docs/t3-c5-monitoring-sync-report`. Main đã có Listening của A; kiểm quyền đọc bài sau chốt tách khỏi scope monitoring ACTIVE.
- Viết [bản nháp](MONITORING_SYNC_REPORT_DRAFT.md): state/event/presence/gap, ACK RAM/DB, FULL/DELTA/epoch/base/retry/RESYNC, độ mới và cleanup; dẫn bằng chứng C1–C4, không sao chép raw. Sửa vài mô tả hiện trạng cũ ở README/index/progress, chú thích phạm vi lịch sử trong protocol/baseline; không tạo QD mới.
- [Kiểm tra tài liệu](../evidence/t3-c5/2026-10-13-verification.md): source/default/method, link/anchor, provenance bảng evidence và diff. Không chạy lại runtime hoặc gọi số test cũ là kết quả main mới. Output tạm ở target/, chỉ một report Markdown C5 được lưu.
- B review bản nháp và A/B review gate NOT RUN; không dùng ChatGPT planning/review theo opt-out trước đó. C4 gate NOT_DECIDED/PARTIAL, FULL mặc định; LAN hai máy/E1/E2 chưa chạy. Giữ tracker/kế hoạch gốc/runtime/migration/.env/artifact cũ.
- Người dùng ủy quyền commit/push/PR/merge commit nếu không blocker, giữ nhánh và đồng bộ main an toàn; Git cuối xem PR/final. Dừng sau T3-C5.

## 14/10/2026 — B · T3-B1 tải audio, checksum và thử loa

- Đọc docs vai B, hợp đồng/kế hoạch/kiểm thử, backend A1/A2, client/network/monitoring và lịch sử commit contributor. Workspace ban đầu sạch, local main sau origin 14 commit; fetch và tạo branch riêng từ origin/main `2e784fa`. Không merge các nhánh B4/Reading chưa vào main.
- Triển khai HTTP Bearer/manifest/READY, writer exact long từ takeover, download/cache/SHA-256 nền, WAV mẫu riêng, xác nhận nghe, revalidate local bytes trước READY và guard callback. Bổ sung candidate view, JavaFX Media, script app-image và harness TEST; server production/API/migration/.env giữ nguyên.
- Java657/657 PASS, 0 skip với PostgreSQL thật; 27 test client mới và một test client→production HTTP/PG mới. Local file thiếu/sửa một byte chặn READY/start; tải lại khôi phục và xóa READY cũ. Package ở thư mục có dấu/khoảng trắng PASS6 checks, Media WAV thật, API/nhấn xác nhận nghe MOCK/SIMULATED, tiến trình tự thoát.
- Sửa lỗi script gặp trong kiểm chứng: PS 5.1 stderr không tương đương exit failure; .env thiếu defaults; Docker ban đầu tắt/native PG không khớp credential; jpackage argv và launcher ANSI mất Unicode; mt.exe cần bỏ ReadOnly tạm trên EXE mới sinh; wrapper Start-Process không cho exit code đáng tin. Đã bật Docker theo cấu hình hiện có, không reset DB/đổi mật khẩu/locale/JDK.
- Source `89082e3d43a322c0e0e210f393cc3b4bf49bd7da`; [guide](AUDIO_CLIENT_PREPARATION.md), [log/ảnh/metadata](../evidence/t3-b1/2026-10-14-verification.md). Chia commit download/cache, UI/READY, test, package, docs, evidence. Review A/nghe thủ công/LAN/máy thứ hai NOT RUN; tracker chỉ T3-B1 IN_PROGRESS, actual_hours null; chưa push/PR/merge theo phạm vi yêu cầu. B2/B3 sẽ dùng writer chung khi ghép Reading và verify cache trước phát; không autoplay từ HTTP/status.

## 14/10/2026 — B · T3-B2 phát Listening theo lệnh server

- Tạo `feat/t3-b2-listening-playback` từ branch T3-B1 vì dependency chưa merge main. Nối parser start/status của RealtimeClient, production HTTP exam/event, coordinator worker, journal local và JavaFX playlist/câu hỏi.
- Dedupe attempt/writer/startId trong RAM và bằng marker CREATE_NEW. Cache được SHA-256 lại trước reserve; lệnh phải khớp context READY. `onPlaying` ghi local và gửi STARTED; heartbeat 2 giây; file cuối gửi COMPLETED. Không autoplay từ HTTP/status/reconnect.
- Full package bật PostgreSQL PASS666, 0skip: protocol43 + client421 + server201 + spike1. Case client/HTTP/Spring/PG thật xác nhận mapping câu hỏi, exact writer, STARTED/COMPLETED và duplicate bị chặn. Package Unicode runtime21.0.9 PASS7 với Media WAV thật; API/WS fixture MOCK.
- Source/test `987d9b27f400c339a50914de11de6d094649a271`; [guide](LISTENING_CLIENT_PLAYBACK.md), [evidence](../evidence/t3-b2/2026-10-14-verification.md). B3 interruption/reconnect, review A, human hearing, LAN/máy thứ hai và MP3/M4A NOT RUN. Tracker chỉ T3-B2 IN_PROGRESS, actual_hours null.

## 14/10/2026 — B · T3-B3 gián đoạn và mở lại Listening

- Tạo `feat/t3-b3-listening-interruption-recovery` từ B2. Thêm status GET production, state RECOVERING/BLOCKED, hủy MediaPlayer/heartbeat khi lỗi, marker interruption bền và event `MEDIA_ERROR`/`NETWORK_LOST` có request ổn định để retry.
- Mở view/reconnect đọc trạng thái authoritative. `INTERRUPTED` và `COMPLETED` không phát; `ARMED`/`PLAYING` còn dang dở fail closed và báo mất kết nối. Không dùng hay sửa `ExamSession`; T2-B4 chưa có trên nhánh nên gate kết hợp Reading còn chờ.
- Full package Microsoft JDK21.0.9 + PostgreSQL thật PASS669, 0skip (protocol43/client424/server201/spike1). App-image Unicode PASS8; JavaFX MediaPlayer thật dừng giữa audio khi transport disconnect, marker ghi NETWORK_LOST, reconnect/status và start trùng không replay.
- [Guide](LISTENING_INTERRUPTION_RECOVERY.md), [evidence](../evidence/t3-b3/2026-10-14-verification.md). Review A, human hearing, rút mạng/kill app thủ công, LAN/máy thứ hai và MP3/M4A NOT RUN; tracker T3-B3 IN_PROGRESS, actual_hours null.

## 15/10/2026 — B · T3-B4 điều khiển ca tối thiểu

- Fetch/main `8f5ac40`, workspace ban đầu sạch; tạo `feat/t3-b4-proctor-session-controls`. Đối chiếu PR35 draft `11c5da9` nhưng không merge/cherry-pick; package proctor riêng tránh tạo ExamApiClient trùng. Điểm ghép duy nhất là tab/close dashboard.
- Source `3d91ea9`, nhãn điểm ACTIVE và assert GUI `832e040`, kiểm kiểu JSON trước DTO `06fd215`. Thêm import JSON giới hạn nền, tạo ca/phân công, READY/start có server quyết định, đọc kết quả ngoài roster ACTIVE và reorganization giữ requestId/payload qua mất response. Cleanup/đổi selection có guard. Không backend production/hook mới; A cần review auth/assigned/final-read/start/retry/LT04.
- [Guide](PROCTOR_SESSION_CONTROLS.md), [report mới](../evidence/t3-b4/2026-10-15-verification.md) ghi baseline exact-main, clean tests và GUI thật cùng giới hạn. Lượt mới khác ID; old answers/deadline/writer/revision/history giữ, timeout do scheduler thật. Test dữ liệu mẫu/nhấn nghe và lỗi mô phỏng được gắn nhãn; không coi local là LAN hay human review. Lỗi build đầu timezone và expected điểm, validation đọc ca cũ được ghi riêng, không sửa raw thành PASS.
- Baseline exact-main669, clean package06fd215 **686/686 PASS, 0skip**; GUI LT04/candidate WAV/HTTP/WS/PG và FULL dashboard smoke PASS. Media rerun8PASS; initial file-lock FAIL/đối chứng main8PASS giữ riêng, byte22audio classes và harness giống hệt, nguyên nhân lock chưa chứng minh. Hai ảnh final, worker0/schema t3a2 còn0 sau chạy.
- Output lớn chỉ target/, evidence mới giữ Markdown và ảnh tiêu biểu, không tạo bulk JSON/ZIP/hash manifest. Không .env/tracker/kế hoạch gốc/migrations/installer/PR khác/E1/E2; FULL mặc định. Không dùng ChatGPT planning/review theo opt-out hiện có.
- Người dùng ủy quyền commit/push/PR/merge commit khi không blocker B4/GitHub, giữ nhánh và sync main an toàn; kết quả Git cuối xem PR/final. Dừng sau B4. Human A review, native FileChooser/nghe thủ công/LAN và tích hợp PR35 NOT RUN.

## 15/10/2026 — B · T3-B5 MSI Windows và bàn giao máy hai

- Main/fetch B4 `0b9c5fa`, workspace sạch; tạo `feat/t3-b5-windows-installer`, giữ nhánh cũ và PR35 draft `11c5da9` riêng. Commit `6fafcad` triển khai MSI/test, `acac38d` sửa probe JVM child/handle/cleanup và guide. Không đổi production Java/API/migration/.env/kế hoạch/tracker; FULL mặc định.
- JDK21.0.10+7, WiX3.14.1 portable và SDK mt10.0.26100.6584 trong ignored target. App-image B1 là trung gian, thêm manifest UTF-8, runtime/JavaFX và source metadata; jpackage MSI per-user0.3.5 thực được build từ Git sạch acac38d. [Lệnh/cài/gỡ/checklist máy hai](WINDOWS_INSTALLER.md), [hash/artifact/evidence](../evidence/t3-b5/2026-10-15-verification.md). Installer/runtime/raw không commit.
- Baseline exact-main clean package PG/GUI BUILD SUCCESS; không lấy686 lịch sử làm lượt mới. Lượt build đầu chọn root clean đã dọn nhầm raw baseline trong target; sửa chỉ clean protocol/client, ghi rõ log mất. Guard bắt nhầm DepthTest và probe wrapper/exitCode null giữ FAIL riêng; cuối giữ handle JVM child và dọn owned descendants. Tracked evidence cũ không đổi.
- Final MSI 37,860,724 bytes, SHA256 `7c145740789a934079d402bc6f72bd3f1b0bde152ba3711e61836a84bfa169f0`. Normal user, install có dấu/khoảng trắng ACL deny Write, ordinary launch không agent/JAVA_HOME và DLL JVM đi kèm, đóng cả wrapper/JVM exit0; sau đó real production EXE UI login hai vai/B4 READY/FULL ACK/event/download+SHA256/MediaPlayer/READY/start/WS close+reconnect locked/journal/cleanup và uninstall PASS. Hearing click/second candidate READY/network fault ghi nhãn SIMULATED/MOCK.
- Full suite mới687 PASS,0skip:43protocol+439client+204server+1spike, PG thật và cả B4 GUI/installed integration. FULL/dashboard smoke mới PASS, automatic STALE/TTL/capacity/shutdown/history,6endpoints/63categories COMPLETE không phải E1/E2. SQL TEST schema/audio và owned process dọn; raw/cache TEST giữ dưới ignored target.
- Phần đóng gói/local hoàn tất; IT01 PARTIAL. Chưa có máy thứ hai không IDE/Java, LAN, human hearing/review A/rút mạng thật/MP3/M4A; không nghiệm thu Reading–Listening PR35 hoặc T4/E1/E2. Không dùng ChatGPT planning/review theo opt-out. User ủy quyền push/PR/merge commit nếu không blocker GitHub, giữ branch local/remote và sync main an toàn; Git cuối xem PR/final.

## 2026-10-12 · A · B-ACCEPT-01: API lịch sử lượt thi
- Thời lượng: hai phiên Agent (Codex rồi Claude); giờ của thành viên chưa ghi.
- Đã làm: Codex cài `GET /api/v1/candidate/attempts`, hai DTO `CandidateAttemptPageResponse`/`CandidateAttemptSummaryDto`, handler 404 cho `NoResourceFoundException` và ba test PostgreSQL (`e81961e`). Phiên Claude nhận lại: chạy toàn bộ test, thêm hai test (thứ tự byte dưới DB `en_US`, biên phân trang, lượt cũ và lượt chốt thiếu `submitted_at` bị ẩn, phiên thu hồi), kiểm chéo bằng client của B, viết contract chính thức và bằng chứng.
- Commit: `e81961e` (Codex), `33a2932` và commit tài liệu kế tiếp, nhánh `fix/backend-candidate-attempt-history-20261012`. Không push, không merge.
- Đã kiểm: `mvn test` 590/590 trên `e81961e`; `mvn package` 592/592 trên `33a2932`, đều với `TOEIC_PG_INTEGRATION=true` trên PostgreSQL 18.6; client B `770c896` gọi server jar của nhánh này PASS 13/13 (HTTP + PostgreSQL thật, có lượt `SUBMITTED` và `TIMED_OUT` thật).
- Còn dở: GUI production của B chưa chạy nên B-ACCEPT-01 chưa đóng; sai phương thức HTTP vẫn trả 500 (ngoài phạm vi giao việc, chỉ ghi nhận); chưa đo truy vấn với số lượt lớn.
- Tiếp theo: người dùng quyết định push/merge; B ghép với `770c896` và chạy lại GUI.
- Cần người khác: B chạy GUI production theo mục 5 của evidence; C review phần auth/handler lỗi của A. Bằng chứng `evidence/t2-a-history/2026-10-12-verification.md`.

## 2026-10-13 · A · API lịch sử: xếp lại trên PR #23 đã đồng bộ
- Thời lượng: một phiên Agent; giờ của thành viên chưa ghi.
- Đã làm: tạo nhánh `feat/backend-candidate-attempt-history-20261013` từ `sync/pr23-main-20261013` và cherry-pick đúng ba commit lịch sử (`e81961e`, `33a2932`, `7bdac52`), bỏ năm bản sao P0 của nhánh cũ để P0 chỉ vào main qua PR #23. Conflict chỉ ở tài liệu, giữ cả hai phía; quyết định lịch sử đổi số QD-19 → QD-23.
- Commit: `ae05e48`, `ba8dac7`, `19f669e` và commit tài liệu kế tiếp. Không push.
- Đã kiểm: `git diff 7bdac52 19f669e -- server/src/main protocol` rỗng; `mvn package` 602 PASS, 0 skip với PostgreSQL thật (lượt đầu treo do máy ngủ, đã hủy và chạy lại).
- Còn dở: sai phương thức HTTP vẫn trả 500 (đã ghi nhận từ 12/10, chưa sửa).
- Tiếp theo: mở PR sau khi PR #23 vào main; nhánh client của B xếp trên nhánh này.
- Cần người khác: C review; A quyết định có sửa 405 hay không. Bằng chứng `evidence/t2-a-history/2026-10-13-restack.md`.

## 2026-10-13 · A/Integration · Fix P1 cursor PR #28 theo ủy quyền
- Thời lượng: phiên Agent, chưa ghi giờ thành viên.
- Đã làm: sửa riêng validator history theo VARCHAR128/code point, không trim/normalize; thêm3PostgreSQL regression; giữSQL/owner/DTO/monitoring/Listening/schema. Code/test commit `00b1df1`, tích hợp main2e784fa trên merge4362d89.
- Đã kiểm: Maven test/package638 PASS mỗi lượt, PG37, probe nguyên bản11/11. Mixed limit1/2/3, Unicode/space/URL-encoding/biên128-129 và foreign owner PASS.
- Còn dở: code chỉ local, chưa push/retarget/bỏDraft/merge; B cần validator/encode/byte order riêng. API tạo ca oversizedID vẫn500+rollback, nằm ngoài scope. GUI/LAN/human review NOT RUN.
- Tiếp theo: người dùng review bản local và cho phép riêng trước push; sau push kiểm diff/checks/mergeability rồi mới retarget/merge theo phép riêng.
- Evidence: local `target/pr28-cursor-20261013/REPORT.md`; bảo toàn evidence gốc, B4, worktree khác và file chưa commit; không prune hoặc bật production delta.


## 15/10/2026 — Integration: Reading, Listening và history

- Nhận task.txt, audit/fetch/PR17-28-34/SHA/worktree; giữ main6a41031, mọi nhánh/worktree cũ và4file untracked. Worktree mới `target/integration-reading-listening-20261015/worktree` từ origin/main8f5ac40; nhánh `integration/reading-listening-20261015`.
- Merge history2ca4121 tại81ae662; conflict chỉ3doc, hợp nhất cả history và Listening. Lấy từng file Reading từ65f5123/d425182, không merge mù PR17; compose Application/view tại9115776, giữ pipeline audio B1–B3 của main và FULL default.
- ExamSession sở hữu writer/answers; audio nhận epoch, không takeover. Status/heartbeat/Media/callback có guard; server-interrupted đóng băng Listening theo bản server và giữ Reading local. Query status alias giữ owner/scope và Tomcat encoded-slash policy. HTTP scope raw không nới WS v0; QD28/29 ghi owner review và quyết định ID còn chờ.
- Targeted142 PASS sau5FAILassertion path cũ; PG49 PASS mới. Clean test9115776 và package3f4d5d6 đều833 PASS,0skip trên JDK21.0.8/Maven3.9.11/Windows11/PostgreSQL18.6. GUI harness actual Application/JAR/Media/PG/WS/restart đang kiểm; firstFAIL do port readiness trước seed commit, sửa precondition count3users, giữ log gốc. Không ghi PASS GUI khi chưa chạy xong.
- PS native runner bị treo và native cmd quoting lần đầu FAIL; chuyển runner môi trường/nativecmd, credentials chỉ qua env. Docker service db bật bằng fileCompose/EnvFile cụ thể sau rejection của auto-review khi target chưa rõ; giữ volume và hai container khác. Test dùng schema owned, không reset public.
- Tài liệu/guide/protocol/owner handoff cập nhật; tracker/kế hoạch gốc/.env/migrations/raw cũ giữ nguyên. Evidence cuối `evidence/integration/2026-10-15-verification.md`; human A/B/C/LAN/installer/E1/E2 NOT RUN. Chưa merge main; push/Draft PR sau khi đủ evidence hoặc bàn giao PARTIAL có blocker cụ thể.


### Chốt kiểm tra runtime và GUI trong phiên tích hợp

- Visual QA phát hiện prepare button không nhận thay đổi playback; sửa387ed99, package833 PASS mới. GUI proctor ban đầu chỉ check HTTP nên ảnh loading; siết roster+state đã render. Assertion literal SYNCED-label FAIL vì UI dùng nhãn quan sát; d809a00 kiểm đúng nhãn và exact HTTP SYNCED, GUI final8nhóm PASS với6ảnh.
- C1 lần đầu driver thiếu heartbeat200ms trong script chuẩn (default2s lớn hơn timeout800ms của smoke) FAIL; dùng đúng flag rồi FULL/epoch/reconnect/autoSTALE/TTL/cleanup PASS. C2 late/gap/UNKNOWN/retry/conflict/permissions PASS mới. Không đổi monitoring runtime hoặc gọi faults MOCK/SIMULATED là dữ liệu production.
- Raw/log/ảnh/version/hash và các lượt lỗi giữ riêng; audit credential match0. B review thêm contrast của roster selected row (P2), A review rawID ACTIVE/oversized generatedID/wrong-method500 (P1/P2); human/LAN/installer/E1/E2 vẫn NOT RUN.


### Công bố nhánh tích hợp

Git WSL không có credential HTTPS nên push bị chặn ở auth, không phải review hay code. Git Windows dùng credential đã lưu đã push f853e13 tới riêng integration/reading-listening-20261015, không main/force-push. Git blob/provenance hashes khớp; thêm attribute giữ exact public UTF8/LF để checksum không đổi khi checkout Windows. Source/runtime/test results không đổi. Draft PR đang tạo; cập nhật URL sau khi server xác nhận.


### Draft PR và bàn giao cuối

- Git Windows push872d12b thành công; GitHub tạo [Draft PR#35](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/35), base main8f5ac40, head integration/reading-listening-20261015. Không merge, không request human approval thay A/B/C.
- Chốt local gate: clean833/package833 (runtime387ed99), GUI8nhóm/proctor đã render (harnessd809a00), ExamFlow28/0GAP và concurrency AT01/07/09/10 + C1/C2 PASS trên final JARs. Tất cả logs/source/JAR/derived hashes/6ảnh có evidence; transcript exact-byte attributes bảo toàn checksum khi checkout Windows.
- Whole acceptance PARTIAL: QD29 ID ACTIVE đặc biệt, human/LAN/installer/gate delta/E1/E2/load chưa nghiệm thu. Main local6a41031 và refs/worktrees/file chưa commit của người khác giữ nguyên; branch integration sạch, chỉ docs-link cập nhật sau PR.

## 15/10/2026 — B overnight phase 01 (backend A/C cần review)

- Worktree anh em/branch `agent/b-overnight-followups-20261015` từ remote11c5da9, main8f5ac40 đã fetch/ls-remote. Không sửa worktree/file dirty cũ.
- ID issuance fail-closed, collision không reopen final, 405 và API progress có phân quyền. Không đổi schema/tracker/kế hoạch. QD29 proposed, chưa human approve.
- Targeted49 PASS thật với PostgreSQL sau baseline connection refused/Docker-off; bổ sung boundary/reorg guard sẽ chạy full gate. [Evidence](../evidence/overnight-20261015/phase-01/verification.md).

## 15/10/2026 — B overnight phase02 (C review pending)

Harness E1 window/sampler/CLI/analyzer có; targeted22 PASS sau một lượt normal child INCOMPLETE retained/cause chưa chứng minh; Python3/dry/smoke/checksum mutation PASS. SMOKE không E1 final; FULL default. [Evidence](../evidence/overnight-20261015/phase-02/verification.md).

## 15/10/2026 — B overnight phase03

Sourcef31ba3d: clean/package839 PASS, ExamFlow41/0GAP, GUI8group và C1/C2 retest PASS. CompilelambdaFAIL và C1runnerclasspathFAIL giữlog; rawE1đầu bị clean xóa đã đính chính và chuyển output bền. AT11/LT/ITwhole PARTIAL, human/LANNOTRUN. [Evidence](../evidence/overnight-20261015/phase-03/verification.md).

## 15/10/2026 — B overnight phase04

Thêm UI điều khiển ca/progress qua API thực, immutable start/reorg intents, stale/401/close guards và contrast roster. Full package846 PASS trước follow-up; targeted35client+33PG PASS sau follow-up. GUI cuối11groups PASS với controls trong viewport và DB assertions. SelectorFAIL cũ giữlog; human/LAN/manual còn NOT RUN. [Evidence](../evidence/overnight-20261015/phase-04/verification.md).

## 15/10/2026 — B overnight phase05

PipelineWindows/parser3/negative5 có kiểm. Sửa relative.NETpath của package-t3b1; rawASCIIimage và bundledMedia8checks PASS (API/WS MOCK, hearingSIMULATED). SDKmt/WiX thiếu nên Unicode/installer BLOCKED, IT01/human/máy2 NOT RUN. Không cài tool hoặc đổi hệ thống. [Evidence](../evidence/overnight-20261015/phase-05/verification.md).

## 15/10/2026 — B overnight phase06 và bàn giao

- Java freezec1601b55b7b40cd84238921e87355108cfaea5fd; clean/package848 PASS,0skip; Flow41/0GAP,GUI11,C1/C2 có GUI PASS mới. PG18.6 thật trong schemaowned; unitMOCK/faultSIMULATED phân biệt.
- E1 đủ12REALrun,15s+120s/run,360GTtrials, quality={'eligible': 360, 'detected': 287, 'missed': 62, 'inconclusive': 11, 'censored': 0}; cleanup/checksums/reanalysis byte-identical PASS. Python4 PASS với analyzerSHA riêng, không thay JAR đang đo; upper-bound latency/parentheap/carryover/background limits đã ghi. E1 acceptance/human C review pending, E2/load/LAN/manual NOT RUN; FULL default.
- PackageASCII8 subset nguồn0057d81; Unicode/installer SDK/WiX BLOCKED. Không cài/gỡ/resetDB/đổi tracker/kế hoạch; checkpoint local giữ, public snapshot sạch sẽ công bố riêng để không gửi ảnh process trong9bbe106. [Bàn giao](../evidence/overnight-20261015/00-summary.md).

### Ref cuối trong phiên overnight

Fetch thấy main8f5ac40→0b9c5fa (PR36 B4), integration11c5da9 không đổi; target merge-base11c5da9 vẫn đúng. Không hợp/đổi nhánh owner. B4 có overlap cần reviewer chọn một controller; [kế hoạch](../evidence/overnight-20261015/UPSTREAM_DIVERGENCE.md). Java source đã kiểm vẫnc1601b5, chưa gọi kết quả đó là test của main mới.

## 15/10/2026 — công bố Draft PR overnight

- Draft PR: [https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/38](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/38), nhánh `agent/b-overnight-followups-20261015-public`, base integration `11c5da9`. Snapshot đầu `4350c9d87da529796d73de98d4a169c93c3a41db` từ checkpoint `a393eb541bb8cdcbc1af3037a3c7811f7d9126d4`; public tree khớp checkpoint, Java khớp source đã test `c1601b5`. Đã kiểm ảnh process/PID cũ không reachable; checkpoint local giữ nguyên và không push. Commit công bố sau chỉ đổi hồ sơ, không chạy lại/nhận lại kết quả Java.
- Main tiếp tục lên `c0fd903` qua PR #37 B5; integration và target merge-base vẫn không đổi. B4/PR #36 và B5/PR #37 cần owner hòa trước merge; chưa kiểm trên main mới. Evidence MSI upstream dùng môi trường/tooling khác, không kế thừa cho overnight. [Chi tiết](../evidence/overnight-20261015/UPSTREAM_DIVERGENCE.md).
- Không merge/approve/mark ready, không đổi tracker/kế hoạch/.env/migrations hoặc worktree khác. E1 PARTIAL (11 inconclusive); Unicode/installer overnight BLOCKED tại môi trường này; human A/B/C, LAN, nghe thật và máy thứ hai còn chờ.

Kiểm tra archive của commit bàn giao phát hiện manifest checksum liệt kê thêm `phase-01/targeted-test-summary.log` vốn chỉ ở local và bị Git ignore. Đã giới hạn manifest vào file evidence tracked, không xóa/sửa log gốc; kiểm tra lại byte của archive trước push cuối. Đây là lỗi phạm vi manifest, không đổi Java/JAR hoặc số test.

Kiểm tra byte tiếp theo phát hiện CSV phase 02 đã bị Git chuẩn hóa CRLF thành LF trước khi thêm quy tắc evidence -text. Giữ file local nguyên byte, áp dụng lại quy tắc -text cho index và kiểm tra archive khớp manifest. Các failure kiểm tra công bố được giữ trong checkpoint; không sửa raw phép đo hoặc Java/JAR.

## 16/10/2026 — tích hợp ứng dụng thống nhất, đang kiểm

Tạo branch/worktree mới từ main c0fd903, hòa #35 tại456583b và #38 theo từng conflict. Giữ B4 #36/MSI #37, loại controller/demo trùng, thêm committed progress và guard/cấu hình runtime có test. QD-28 giữ MSI, shared writer đổi số QD-30 do trùng; QD-29 vẫn đề xuất. Targeted đầu52 có1FAIL test queue, sửa và lượt mới56PASS; parser/negative có nhãn. PostgreSQL/GUI/clean/package đang chạy, chưa kế thừa848/687/MSI. [Conflict/evidence](../evidence/unified-20261016/conflict-resolution.md); raw E1 cũ đã hash trước và không sửa. Không reset/clean/rebase/force-push nhánh cũ, không sửa tracker/kế hoạch/nguon/migrations/.env hoặc duyệt thay A/C.


## 16/10/2026 — kiểm chứng và bàn giao ứng dụng thống nhất

Production merge24f70bd từmain c0fd903 +PR35/38, checkpoint testb755646 chỉ thêm hardening TEST C1/C2. Giữ nền B4#36 và MSI#37; một Application/shared writer, committed progress/validationQD29/AT11/E1. Clean/package mới mỗi lượt872PASS/1skip; Flow41/0GAP, GUI11, Audio8/production EXE vớiPG, C2+GUI PASS subset. C1 cuối PASS sau các FAIL cleanup được giữ; MSI/Unicode SDK/WiX BLOCKED. Lượt FAIL runner/GUI/native/C1 giữ log, không kế thừa số nguồn. Raw84E1 và14CSV repro không đổi; smoke mới runId riêng, full12 bản ghépNOTRUN. [Báo cáo](../evidence/unified-20261016/verification.md). Không resetDB/nhánh cũ, không sửa tracker/kế hoạch/nguon/migration/.env. Draft base main và HEAD cuối xem publication; không merge/đóngPR cũ/approve thayA-C.


Công bố16/10: đã push duy nhất branch thống nhất và tạo [Draft PR #39](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/39) base main, head công bố đầu6ff7656. Source testb755646 giữ nguyên; commit sau chỉ bổ sung URL/hồ sơ và manifest, không nhận thêm test PASS. Đã kiểm88file evidence mới+overnight từ byte Git commit, private blob không reachable và source/test không đổi. Không merge/đóngPR cũ/duyệt thayA-C.


## 16/10/2026 — QA/stabilization PR39, MSI mới và runbook LAN

Nhánh QA riêng từ093429a; main/merge-base c0fd903. Source cuối9bfc75e sửaBOM0a00d0f/title dcbdf85/cache lifecycle9bfc75e; giữ mọi FAIL, cache stress BEFORE10FAIL+1ERROR/65 → AFTER65PASS rồi targeted26PASS. Full clean/package875PASS+1SKIP mỗi lượt, installed FullSuite876PASS/0SKIP trên PG18.6 thật; Flow41/0GAP, concurrency, ApplicationGUI11/C1-C2 PASS subset. MSI037 source sạch quyền thường/Unicode/noJavaHome/bundledJVM/denyWrite/close/uninstall PASS; private external agent thêm History/reorg old immutable/new fresh PASS, compiled test agent phục hồi. Không source/backend/contract A-C changes hoặc human hearing/physical LAN claim. Sau khoảng nghỉ sharedDB đã dừng, dùng TEST PG tmpfs riêng rồi remove; QA database đầu phiên chờ owner cleanup khi shared service được phép chạy. User xác nhận chỉ một máy: LAN runbook/testdata/bundle sẵn, physicalNOTRUN.84rawE1 giữ checksum, không E1/E2 rerun, tracker/nguon không sửa. [Evidence](../evidence/b-pr39-acceptance-20261016/FINAL_REPORT.md); Draft QA targetPR39 được ghi ở PUBLICATION.md, không merge/markReady/close PR nguồn hoặc duyệt thay A/C.


Công bố QA16/10: đã push nhánh `qa/b-pr39-acceptance-20261016`, tạo [Draft PR #40](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/40) nhắm nhánh PR39, base093429a, commit công bố đầu b5626168. Source đã test/build9bfc75e giữ nguyên; chỉ thêm URL/hồ sơ/checksum sau đó, không nhận thêm test PASS. Git commit evidence mới và manifest lịch sử đã đối chiếu byte; root HEAD6a410316 cùng5untracked giữ nguyên. Không merge/markReady/close/approve PR nguồn. [Bàn giao](../evidence/b-pr39-acceptance-20261016/PUBLICATION.md).

## 16/10/2026 — F01: giữ cờ Listening gián đoạn trong kết quả

- Base: fetch PR39 `093429a`, PR40 `3fc5bb4`, GitHub xác nhận cả hai OPEN/Draft; tạo worktree `target/f01-worktree`, nhánh `qa/f01-listening-result-20261016`. Root main và file/worktree cũ giữ nguyên. PR40 HEAD kiểm lại cuối phiên vẫn3fc5bb4.
- Code/test `d19c63902725662aeb6f43c6f04ca80ea6caff42`: Result thiếu trường làm mất cờ ở cả hai mapper; bổ sung cờ server trực tiếp và cảnh báo tiếng Việt trên ScoreResultDialog cho SUBMITTED/TIMED_OUT. History dùng mapping chung nên không cần sửa view/ExamResults; không takeover, không suy từ score/state, không sửa điểm/backend/wire/QD29/monitoring/E1.
- Đã kiểm mới: trước sửa mapping6ERROR đúng thiếu thuộc tính; sau sửa targeted104PASS. Maven clean test/package mỗi lượt896PASS/2SKIP; MSI0.3.8 installed FullSuite898PASS/0SKIP (XML archive từng lượt),0failure/error. AT11/ExamFlow41PASS/0GAP, ApplicationGUI11, PG concurrency AT01/07/09/10, C2+GUI PASS. C1 đầu FAIL EDGE_PROFILE_CLEANUP/FileSystemException, log giữ nguyên; đổi thư mục tạm TEST sang Windows Temp, không sửa source, lượt mới C1 FULL+GUI PASS; nguyên nhân đầu chưa chứng minh.
- Môi trường Windows11/JDK21.0.8/Maven3.9.11/JavaFX21.0.12; Docker đang tắt nên dùng cluster PostgreSQL18.1 native TEST riêng, không restart shared service. Hậu kiểm0schema, dừng cluster, dọn profile TEST và xóa seed settings local. Hai probe Surefire đầu và wrapper exit-code cleanup đầu có lỗi môi trường; không tính là test F01 PASS.
- MSI0.3.8 từ source sạch d19c639, pipeline hiện hữu, SHA256 `df17a3fa168579ccd222cfc1429599277b4e05ec7e8f71b017ef6bd91faefc49`. Quyền thường/Unicode/no agent/no JAVA_HOME/bundled JVM/deny-write/close/uninstall PASS local;5439class entries packaged/final bằng byte.84rawE1 hash lại trước/sau giữ nguyên, không chạy E1/E2.
- Bàn giao: [báo cáo/counts/ảnh/metadata/index log](../evidence/f01-20261016/REPORT.md). Thay đổi production thuộc B; bổ sung test trong file A cần A review. MOCK/SIMULATED phân biệt với REAL trong report; human review/nghe thật/DPI rộng/LAN/máy2 NOT RUN. Không đổi tracker, không push/tạo PR/merge/approve/đóng PR nguồn. Ưu tiên cherry-pick commit sửa vào PR40 rồi đưa PR40 vào PR39 một lần; commit hồ sơ sau không đổi code đã test.

## 16/10/2026 — C · review E1 và chuẩn bị T4-C1

- Main sạch c0fd903; fetch/API xác nhận PR38–40 OPEN/DRAFT, latest PR40 3fc5bb4. Tạo nhánh riêng review/c-e1-methodology-t4c1, target qa/b-pr39-acceptance-20261016. Không thay nhánh nguồn/tracker/kế hoạch/hợp đồng/.env/DB; FULL default.
- Sửa CPU mean-of-percentages bằng tổng delta/time, E1 rejoin dùng window checksummed thay C4 plan, analyzer coverage/invalid-run/identity binding/latency/duplicate guards. Source/test c2cc312, byte serialize MOCK243byte kiểm production counters. [Guide](E1_T4C1_MEASUREMENT.md), [review/evidence mới](../evidence/t4-c1/2026-10-16-review.md).
- Tái hiện CPU50% sai so expected10%; baseline smokeCOMPLETE nhưng rejoinexit2. Sau sửa87Java/11Python PASS, final smoke REAL3child/gateSIMULATED COMPLETE, rejoin byte-identical/reanalysis deterministic. Giữ lỗi cache/PowerShell và1ERROR hồi quy trong ignored target/t4-c1, sửa implementation để C4 test gốc PASS. Không full12/E2/load/LAN hoặc benchmark overhead.
- 12metadata lịch sử/3trace MOCK hash khớp; derived CSV count287/62/11. Raw84file c1601b5 chưa có tại provenance, đã hỏi vị trí; không nhận reanalysis cũ PASS. Toàn12run cần phân tích lại resource; chưa có lý do tự chạy lại ngay toàn campaign.
- Chuẩn bị T4-C1 có thể review, nghiệm thu PARTIAL: human B/raw cũ/T3-C4 LAN-gate/trace120s E2 còn mở. Không thay human review bằng agent; ChatGPT planning/review NOT RUN theo opt-out. User ủy quyền commit/push/PR riêng, không merge; Git cuối trong PR/phản hồi. Dừng review E1/T4-C1.

## 17/10/2026 — C · chuẩn bị gate T3-C4 LAN/human B

- Tạo `gate/t3-c4-lan-20261017` từ HEAD PR #43 `772c6e9`; không merge/đóng/mark Ready PR #39–#43. Sửa delivery/transport để stop chờ CLOSE giải phóng correlation và bỏ phản hồi muộn của correlation đã quên; commit code `2b6c9ce`.
- Focused client 77 PASS; local live gate 42/42 PASS trên một Windows host với app/ProcessHandle/Edge/Spring/PostgreSQL/HTTP/WS thật và fault seams ghi SIMULATED. Full Maven rerun với temp trong workspace BUILD SUCCESS: protocol45/client610/server219/spike1, 48 PG test skip ở lệnh full; local gate PG thật PASS.
- App-image/ZIP ignored từ source sạch `2b6c9ce`, FULL mặc định; ZIP SHA-256 `9e920fba5d6440f002ef51f2ba629b49ddbb7dae12177a4c6282c1e2b34c4667`, JAR SHA-256 `b3febc078d845e181bffb914c08a7ad49e78250ee693075fd35a67eead40452f`. Máy build thiếu `mt.exe`, nên runbook yêu cầu đường dẫn ASCII.
- [Runbook](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-17-lan-gate.md). LAN hai máy và human B review `NOT RUN`; gate `PARTIAL / NOT DECIDED`; không sửa TRACKER/QD, không chạy E1/E2/load.


## 17/10/2026 — Integration release candidate sau review A/C

- Fetch/API inventory39/40/41/42/43/45; latest main07c4024. Giữ root6a410316/năm untracked/worktrees cũ. Không có review/comment/inline approval của owner; không merge/push/approve PR nguồn hoặc main.
- Worktree/branch TEST riêng: ghép F01 và C41/43, conflict thuần NHAT_KY/TIEN_DO giữ cả hai phía; source c224ba8/tree22858b4. PR45 conflict AudioFiles và probe REAL filesystem/MOCK manifest tái hiện18/128 FileAlreadyExistsException, RC0/128; chưa tự hòa semantics/policy.
- Mới trên đúng source: clean test/package mỗi lượt904PASS/2SKIP/0failure/error; installed MSI0.3.9 FullSuite906PASS/0SKIP; Flow41,GUI11,PGconcurrency AT01/07/09/10,C1FULL/C2+GUI PASS. REAL/MOCK/SIMULATED ghi theo harness; không cộng standalone/repeated suite vào JUnit. Installed ordinary launch không agent/JAVA_HOME/bundledJVM/Unicode/denywrite/close/uninstall PASS local.
- MSI0.3.9 source sạch c224ba8, SHA256 bef7528f77b35695904812b2959ea389b6ac790ba6fc8f9f91fc665632af604e;5439class packaged/final bằng byte;8class khác MSI0.3.8. Giữ artifact cũ.0schema/Edge/product TEST, dừng cluster riêng, không shared DB/Docker/firewall/registry app người dùng.
- E1 raw84 truy cập được và hash trước/sau khớp; Java mới rejoin12byte-identical; analyzer Python12PASS,17output deterministic.287/62/11 không đổi; CPU mới time-weighted khác nhẹ, resource coverage OK. Raw/sourcec1601b5 giữ nguyên, không campaign mới/E2/LAN/load/hearing hoặc owner signoff.
- QD29 PROPOSED_FOR_A_C_APPROVAL; A/C chốt fail-closed/legacy, A review history5001/HTTP500/UX, C xác nhận E1 mới, B xử lý45 và manual gates. C1 FAIL lịch sử giữ UNRESOLVED. [Báo cáo/counts/provenance/next actions](../evidence/integration-final-20261017/REPORT.md). Tracker/kế hoạch/nguon/migration giữ nguyên; hồ sơ commit riêng sau test, không đổi source/test/build/resources.

## 2026-10-17 · agent · ổn định hóa bản tích hợp cuối
- Đã làm: nhánh `stabilize/final-rc-20261017` từ RC `a447bfb`. QD-29 server chặn legacy ACTIVE (409) và va chạm tạo ca đồng thời (400 thay 500); lịch sử >5000 trả 409 không retry, dashboard báo đúng, trần body 8 MiB; cache audio hợp nhất fix race PR40 với policy junction, thay PR45; CI Windows của PR45 cherry-pick riêng; test takeover replay; cleanup C1/C2 báo từng thành phần, dừng Edge đúng thứ tự, hạn 45 s theo số đo.
- Commit: `991ad0e`…`3702b6d` (16 commit), source test/MSI `3702b6d`.
- Đã kiểm: clean test/package 924 PASS/4 SKIP; MSI 0.3.10 installed FullSuite 926 PASS/2 SKIP; Flow/concurrency/GUI/C1/C2 PASS; CI Windows SUCCESS; E1 84 hash + 12 Python PASS. Lượt FAIL trung gian ghi trong [báo cáo](../evidence/stabilize-20261017/REPORT.md).
- Còn dở: owner duyệt QD-31..34, C xác nhận C1/E1; LAN/nghe thật/máy sạch/tải/E2 NOT RUN; nguyên nhân khóa profile Edge (không phải tiến trình user-mode) chưa xác nhận.
- Tiếp theo: owner review Draft PR vào nhánh PR39, merge theo mục H của báo cáo.
- Cần người khác: A/C (QD-31, QD-32), A/B (QD-34), B (QD-33, đóng PR45), C (C1, E1).

## 2026-10-18 · A · T3-A3 hook delta và lỗi server
- Tạo nhánh `feat/t3-a3-delta-server-hooks` từ main `6ac8835`; review code đã tích hợp từ C2/C4 theo scope A: auth trước dedup, active socket/collector/epoch, RAM freshness/TTL, ACK/push assignment và persistence event/gap/presence. Không viết lại collector/reducer, không thêm migration.
- Thêm regression hai `MonitoringStateService` trước/sau restart: server instance và sync epoch đổi, replay full epoch cũ bị `STALE`, OPEN/full mới phục hồi. Thêm contract [MONITORING_SERVER_HOOKS](MONITORING_SERVER_HOOKS.md).
- Targeted31 PASS; full package912 PASS,58 opt-in skip. `smoke-a3.ps1` REAL PostgreSQL18.6/HTTP/WS PASS deferred COMMIT rollback + retryable ERROR/no success ACK/warning. `smoke-t3c2.ps1` REAL Spring restart cùng port/PG/HTTP/WS/client PASS persisted auth/history, new instance/epoch, reconnect/full; schema TEST tự xóa. LAN hai máy/human C review NOT RUN, tracker gốc giữ nguyên. [Evidence](../evidence/t3-a3/2026-10-18-verification.md).

## 2026-10-18 · A · T3-A4 regression giao dịch và nhiều client
- Tạo nhánh `test/t3-a4-transaction-multiclient-regression` từ T3-A3 `8fef5bb`. Thêm case save, submit và autonomous timeout cùng bị chặn bởi khóa row qua deadline; đủ ba waiter được quan sát bằng `pg_stat_activity`, không suy luận race chỉ từ timing.
- Thêm `scripts/smoke-t3a4.ps1`: clean package tránh artifact test cũ, lưu metadata/transcript theo run ID, chạy targeted transaction/network, AT01 trực tiếp và smoke multi-client WS; chuẩn hóa `JAVA_HOME` có quote/trailing separator trên Windows.
- Lượt cuối `T3A4-e1f00de1-e422-41c3-9955-e7c5cc82ad3d`: targeted74 PASS; AT01/07/09/10 PASS; REAL PostgreSQL/HTTP/WS malformed client không ảnh hưởng client khác và four-client isolation PASS. Full package912 PASS,58 opt-in skip. Schema TEST tự xóa; LAN hai máy/GUI/human C review NOT RUN, tracker gốc giữ nguyên. [Evidence](../evidence/t3-a4/2026-10-18-verification.md).

## 2026-10-18 · A · T3-A5 bản nháp network/concurrency
- Tạo nhánh `docs/t3-a5-network-concurrency-report` từ T3-A4 `44b426d`; report commit `411be40`. Viết bản nháp kiến trúc server, REST/raw WS, auth/scope, transaction và đồng thời; tách trách nhiệm framework/library khỏi state machine, row lock, revision, epoch và commit-before-ACK do nhóm thiết kế.
- Mỗi claim chính gắn source và evidence T3-A3/T3-A4; thêm ma trận NC01–NC08 và checklist reviewer C. Ghi rõ current state RAM một JVM, loopback không phải LAN/load, chưa có throughput/latency/resource.
- 36 Markdown local links PASS, `git diff --check` PASS, audit 6 source owner PASS. Không chạy lại runtime vì docs-only; không tạo PASS mới. Load/LAN/GUI/human C review NOT RUN, báo cáo giữ DRAFT và tracker gốc giữ nguyên. [Report](T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md), [evidence](../evidence/t3-a5/2026-10-18-verification.md).

## 2026-10-18 · agent · T3-C5 cập nhật và kiểm PR #47 trên main
- Đã làm: thêm mục 9 vào bản nháp T3-C5 (dừng hẳn khi bị từ chối quyền, lịch sử giới hạn 5000/409, restart đổi epoch theo T3-A3, cách ly nhiều client T3-A4, sửa ACK CLOSE muộn còn ở PR #47). Ghép cục bộ PR #47 (`5d81aa0`) lên main `f864034` thành `eca6ad3` để kiểm, không push bản ghép.
- Commit: nhánh `docs/t3-c5-update-20261018` (chỉ docs/evidence).
- Đã kiểm trên `eca6ad3`: `mvn clean test` có PG/GUI 927 PASS/0 FAIL/4 SKIP; `smoke-t3c4.ps1` 42/42 PASS, measurement INCOMPLETE đúng dự kiến (hard-kill). [kiểm tra 18/10](../evidence/t3-c5/2026-10-18-update.md)
- Còn dở: gate T3-C4 NOT_DECIDED/PARTIAL; LAN hai máy và B review NOT RUN; PR #47 chưa vào main.
- Tiếp theo: C đổi base PR #47 về main; B review T3-C5 và checklist gate; chạy LAN hai máy.
- Cần người khác: C (PR #47, quyết gate), B (review, máy thứ hai).


## 18/10/2026 — ổn định và retarget PR #47

- Giữ nguyên workspace gốc main và file/worktree chưa commit; checkout Git độc lập `target/pr47-stabilize-20261018`. Fetch xác minh PR47 `5d81aa0`, main `f864034`; merge commit `167caf3`, conflict NHAT_KY/TIEN_DO giữ evidence cả hai phía. Push thường, base PR47 đã là main.
- Source `3d93820`: regression trước sửa tái hiện CLOSE future treo, phản hồi muộn lặp làm FAILED và ACK lấy pending trước cleanup. Giới hạn chờ CLOSE bằng ackTimeout/guard hủy, cache correlation giữ dấu có giới hạn kể cả pending đã được ACK. Chạm FullSnapshotDelivery của C và RealtimeClient của B; cần owner xem. Không đổi server hooks T3-A3 hoặc wire.
- Focused cuối148 PASS/0SKIP, RealtimeClient63; server-focused118 PASS/4SKIP. Clean test/package mới mỗi lượt928 PASS/5SKIP,0failure/error; trong đó PG67 PASS/3SKIP. T3-C2 REAL Spring/PG/HTTP/WS/restart/resync PASS, process readings MOCK/fault SIMULATED. T3-C4 lượt mới 42/42 PASS LOCAL.
- Giữ regression FAIL trước sửa, một FAIL fixture delta sau thêm guard (đã sửa fixture), hai lượt clean FAIL cấu hình thiếu DB_HOST/cổng5433. Cluster PostgreSQL18.1 TEST riêng loopback55447 thay môi trường cũ không hoạt động; không restart DB chung hoặc sửa .env gốc. Log công bố là bản dẫn xuất có hash, raw/XML giữ nguyên ignored.
- [Bàn giao và checklist C](../evidence/pr47-stabilize-20261018/REPORT.md). CI source3d93820 SUCCESS; CI HEAD hồ sơ xem PR/checks và phản hồi cuối. C approval NOT RUN; không merge/approve thay C, không sửa tracker/QD owner approval hoặc quyết định FULL/DELTA; không LAN/MSI/E1/E2/load/đóng PR41/42/43/45.

- Hậu kiểm cuối: T3-C4 mới42/42 PASS, source/JAR hash khớp C2/C4; measurement C4 INCOMPLETE do hard-kill chủ đích, không kết luận E2. 0 schema TEST, dừng cluster sở hữu và phục hồi env checkout. READY_FOR_C_REVIEW, chưa merge; CI HEAD hồ sơ và Git SHA cuối được xác minh trong PR/phản hồi.

## 18/10/2026 — chuẩn bị T3-C4 physical LAN cho B/C

- Người dùng xác nhận chưa có máy thứ hai; chuẩn bị bàn giao, không chạy/chấm hộ LAN. Nhánh local riêng `prepare/t3-c4-physical-lan-20261018` từ kết quả Task1 `3044e5b`, runtime `3d93820`; không thay PR47 hoặc workspace gốc.
- Thêm runbook A1–A4/B–H/MT02–MT06, F01–F12 Reading/Listening,9script PowerShell TEST, checklist41case/topology/review/SQL. ZIP có thật22file, SHA256 `5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a`; hash JAR giữ đúng Task1. Hướng dẫn vận hành cũ trỏ sang artifact3044, evidence cũ giữ nguyên.
- Kiểm thật Windows PowerShell5.1/JDK21:29/29kit checks PASS, lặp29/29trên ZIP giải nén;22file/hash/init41NOT RUN/tamper reject PASS. Fixture origin/map/file MOCK, không gọi mạng. Protocol/fault runtime, PG/SQL, JavaFX, Maven/CI mới và physical acceptance NOT RUN; không dùng42/42local trước làm LAN.
- LAN NOT RUN /0case thật; B/C human review PENDING. Cần máyB, DB TEST/assignment/audio/attempt mới và evidence physical F/ACK/DB/UI/cleanup. FULL default, không QD/tracker/gate quyết định; production source không đổi. [Báo cáo và artifact](../evidence/t3-c4-physical-prep-20261018/REPORT.md), [runbook](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md). Chưa push nhánh Task2.

## 18/10/2026 — Task2.1 công bố bàn giao T3-C4

- Người dùng ủy quyền push nhánh chuẩn bị và mở Draft PR vào nhánh PR47, không vào main. Fetch/GitHub xác nhận PR47 HEAD3044e5b; commit local e4c86aa có3044 làm ancestor,30file diff ban đầu được review; workspace/worktree gốc giữ nguyên.
- Bổ sung hướng dẫn B/C nhận ZIP từ người giữ máy build qua USB/kênh chia sẻ riêng được phép, kiểm SHA256 độc lập trên A/B trước giải nén/chạy; ZIP hiện có giữ hash5b04dcb1…1685a và ngoài Git. Chỉ công bố text/script TEST/template/evidence đã lọc; production runtime giữ nguyên.
- Checklist41case vẫn NOT RUN, LAN0/41, B review PENDING. Kiểm publication là ancestry/diff/hash/privacy/link/cú pháp lệnh, không chạy Maven/PG/physical LAN hoặc tạo kết quả runtime mới. [Hồ sơ công bố](../evidence/t3-c4-physical-prep-20261018/PUBLISH.md); PR/remote cuối xác minh sau push.

- Kết quả Task2.1: push thường commit6e58a1b và mở [Draft PR52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) vào `gate/t3-c4-lan-20261017`, GitHub32file/2commit lúc mở. Hash sourcePR47 trước/sau push vẫn3044e5b. Ghi nhận URL bằng commit hồ sơ tiếp theo; không merge/approve hoặc gửi ZIP thay người giữ máy build. Checksum8file evidence PASS; physical LAN0/41 NOT RUN, B PENDING.

## 18/10/2026 · Codex · corrective S3-P1-01 và tích hợp Phase3
- Task: task.txt; bảo toàn checkout main6a41031 đang có uncommitted files, fetch xác nhận origin/main f864034 và PR45/47/51–56 chưa merge. Worktree riêng, không đổi branch thành viên.
- Source56a27e9: cache hợp lệ không replace; static bounded JVM locks + persistent FileLock sidecar phối hợp publication/file/verify đa instance/process. HTTP không giữ khóa, partial đủ byte/SHA mới xét publish; lỗi I/O khác không bị coi là corrupt. Giữ QD33 root/junction/8.3/Unicode, v0/FULL và worker ngoài FX.
- Windows/JDK21: exact main trước sửa747 lỗi/1280operations; sau sửa0/1280,20/20final hash,0partial. Targeted54PASS/2SKIP (symlink privilege); có bốn JVM, reader NOSHARE_DELETE, cancellation/retry/peer death/READY. Diagnostic encoding và wrapper failure giữ riêng, không gọi PASS.
- [Evidence](../evidence/audio-cache-p1-20261018/REPORT.md). Người dùng chỉ định Codex reviewer kỹ thuật; không gán chữ ký A/B/C hoặc tự đánh tracker DONE. PR47/test56 ghép ở nhánh TEST riêng; full-run/source và packaging ghi hồ sơ tích hợp. MSI preflight PASS với WiX/SDK sẵn có; ZIP3044 tìm lại/hash đúng nhưng là source cũ.
- Còn dở tại snapshot: lần push đầu bị automatic approval review từ chối vì yêu cầu ủy quyền trực tiếp; ngày19/10 người dùng đã cấp quyền push hai nhánh, mở PR và chạy CI. Chưa PR/CI/merge main ở lượt hồ sơ này. LAN0/41, nghe thật/máy2 NOT RUN. Tiếp tục kiểm local; xin quyền push cụ thể khi đầu ra reviewable hoàn tất.


## 19/10/2026 · Codex · tích hợp main và sửa harness gate
- Theo task.txt và người dùng chỉ định Codex reviewer: PR47 review kỹ thuật/CI đúng HEAD rồi mergecd9a89d; corrective audio PR57 CI39148710282 SUCCESS tạif92ea7b rồi merge8a43a56. Không gán human signatures, không force-push hoặc đổi nhánh thành viên.
- Main chứa hai fix production và original test wait56 (giữ tác giả). Source tích hợpce38a5f clean test/package mỗi lượt942PASS/4SKIP; PG68PASS/2SKIPsubset. MODE8case×5lượt targetedPASS, không gọi40case duy nhất. GUI Reading/Listening11/11 và ExamFlow41/41PASSlocal, không hearing/LAN.
- C4 localFAIL tại event rollback: FULL peer2event đều5SEND/5ERROR/noACK, cleanup báo2unconfirmed; A retry/commit được. Test backoff100/400 khiến FULL hết budget trước lúc peer khác scan/gỡtrigger. Harness1072f77 giữ productionbackoff cho normalpeer, instrumentedpeer vẫnTEST100/400; maxAttempts5/await12s/assertions giữ. Không sửa production. Giữ failure raw/report; Python cũ không hỗ trợ Path.write_text newline nên runner đổi sangPython3.13 sẵn có, không sửa analyzer để tạoPASS.
- Reuse PR52 (retargetmain) rồi PR51 bằng merge vào nhánh riêng, conflict docs giữ cả hai phía; áp patchC5 từPR56 rồi refresh trạng thái47/57/ZIP/E1. Sửa đúng nhãn envelopev1→v0 trong reportA5, không đổi protocol. Chạm tài liệu A/C và harnessC theo nhiệm vụ tích hợp; reviewer kỹ thuật là agent được người dùng giao, không owner giả.
- Còn dở: CI workflow exact-main/harness mới cần nhập nhánh tích hợp; rerunC4, finalmain clean/PG/CI/MSI. PhysicalLAN0/41, máy2/hearing NOT RUN. Tracker/hours không đổi, FULL baseline.


## 19/10/2026 · Codex · sửa race cleanup test trên main

19/10/2026 — CI exact-main0363995 run39150213101 FAIL: AudioPreparationTest JUnit TempDir xóa next-speaker WAV đồng thời với cleanup async của production close(). Sửa TEST chờ Files.notExists(sample) bằng await5s hiện có, cho cả AfterEach và view kế tiếp; giữ mọi assertion READY/epoch, không đổi production close/FX. Focused10case×5lượt PASS trên Windows (fixture API/media MOCK), không gọi50case duy nhất. Clean main0363995 test/package mỗi lượt942PASS/4SKIP nhưng CI này vẫn FAIL; không gán CI củaPR58 cho main. Full/PG/CI sau corrective sẽ ghi tiếp; LAN0/41, hearing/máy2 NOT RUN.


## 19/10/2026 · Codex · main ổn định và artifact cuối

19/10/2026 — CODE_READY_STAGE3_ACCEPTANCE_PENDING: main buildsourcecfbba58 có47/57/58/59, CI39151021995SUCCESS; clean test/package mỗi lượt942PASS/4SKIP, installed MSI0.3.11 FullSuite943PASS/3SKIP trên Windows/PG thật. HaiP1 FIXED/MERGED; C4 local42/42, kit source/portable29checks mỗi bộ, physicalLAN0/41, hearing/máy2 NOT RUN. [Kết quả và hash](../evidence/stage3-execution-20261019/REPORT.md), [bàn giao](STAGE3_FINAL_HANDOFF.md). FULL baseline/DELTA experimental; technical reviewer Codex được người dùng giao, không ký A/B/C; tracker/hours giữ nguyên.

Raw local được giữ private/ignored, XML chỉ công bố case+SHA không environment. PG TEST ba lượt đều0schema và dừng; MSI đã gỡ, kit không chạy network. Đã giữ failure CI0363995/C4 trước corrective, không đổi kết quả lịch sử. Release followup chỉ pin kit/source/template/runbook và hồ sơ, không Java/POM/protocol; buildsource giữ SHA trên, kiểm equivalence lại khi merge.

## 2026-10-19 · chung · Plan V2 scope activation / BASE-04 preparation

- **Nguồn xác nhận:** người đại diện nhóm cho biết cả nhóm phê duyệt V2 tối 18/10; không có biên bản chữ ký A/B/C trong lần cập nhật này.
- **Đã làm:** chuyển nhãn V2 từ PROPOSED sang TEAM_SCOPE_APPROVED, cập nhật CLAUDE và phụ lục hợp đồng để cho phép giới hạn USB mass-storage observation/200Q/media/focus/display; thêm kickoff song song A/B/C và phân định các ADR kỹ thuật còn chờ.
- **Baseline:** main trước thay đổi `20d119f5732022f387be8b31e8153489418c859c`, CI [39186372875](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39186372875) SUCCESS. Không sửa Java/SQL/protocol runtime trong đợt này.
- **Đã kiểm:** đối chiếu kế hoạch 36 task và phân định DONE code vs NOT RUN physical; CI trên commit cập nhật phải xác minh riêng.
- **Còn dở:** BASE-01 đối soát source/task, BASE-04 owner duyệt DTO/START/deadline, EX-01 dataset/license, USB/LAN/Listening/MSI physical.
- **Tiếp theo:** A/B/C nhận các task trong [kickoff](plan-v2/V2_EXECUTION_KICKOFF_20261019.md). Không cập nhật `STAGE3_DONE` trước physical acceptance.

## 19/10/2026 · Codex · công bố input seedingTest theo yêu cầu

- Người dùng yêu cầu đưa data/seedingTest lên main và xác nhận có quyền công khai dữ liệu Zenlish; chưa cung cấp văn bản cấp phép. Ghi đúng nguồn và lời xác nhận, không tự gán license.
- Base main50f9678; nhánh riêng data/seeding-test-html-20261019. Thêm5 HTML đã loại script/form/nonce/inline handlers, README và manifest SHA256; giữ bản gốc riêng có hash, không commit dữ liệu form hoặc script trang nguồn.
- Kiểm thực tế:5 file×200 article bảo toàn, audio/image element counts giữ nguyên, checksum bản công bố và scan active-content/nonce PASS. test04 và test05 trùng nên chỉ4 nội dung riêng. Kiểm token chỉ ghi loại/số dòng, không ghi giá trị bí mật vào log/tài liệu.
- Chỉ thay dữ liệu/tài liệu và thuộc tính Git bảo toàn checksum; Java/POM/SQL/protocol không đổi. Runtime Maven/PG/GUI/import/media download/hearing NOT RUN trong lượt local. CI PR/main sẽ xác minh riêng trên GitHub trước/sau merge.
- Owner EX-01 A, reviewer nội dung B; Codex review kỹ thuật theo người dùng giao, không ký vai. Chưa đánh dấu EX-01/EX-08 DONE hoặc sửa tracker/giờ; còn kiểm đáp án, groups/passages, media local và lưu căn cứ quyền.

## 19/10/2026 · Codex · schema dữ liệu và tài liệu backend API

- Đọc docs V2/import checklist, toàn bộ V1–V10, controller/DTO/service và năm HTML trong `data/seedingTest`. Kiểm kê mỗi file 200 câu, 23 Listening groups + 19 Reading groups; 54 audio và 43/48 ảnh. Test04/Test05 vẫn là một nội dung trùng theo manifest.
- Thêm Flyway `V11__rich_exam_content.sql`: provenance nguồn/quyền/checksum, group chuẩn hóa, media remote/local verified, ordered TEXT/IMAGE/AUDIO blocks; thêm unique order, section-part check, deferred FK cho correct option và FK group. Không sửa migration cũ, binary media vẫn ngoài DB.
- Sửa importer để tạo group normalized trước câu hỏi và chuẩn hóa group blank thành null; validator chặn section/part, thứ tự câu/option và group definition không nhất quán trước khi chạm constraint DB. Thêm 4 regression, `ExamValidationTest` 8/8 PASS.
- Viết `DATABASE_SCHEMA.md` giải thích ánh xạ/giới hạn và `BACKEND_API.md` tổng hợp 25 route REST cùng contract WebSocket v0, auth, error, payload, role, lifecycle và config. Cập nhật mục lục/tiến độ/QD-35; không đổi wire nên không sửa nội dung contract `PROTOCOL.md`.
- Kiểm tra cuối: clean focused compile/test PASS 8/8; `mvn test` PASS 933, 0 failure/error, 59 skip opt-in; package cuối SUCCESS và JAR có V11; audit đủ 25/25 HTTP route, 17/17 WS message và 0 link hỏng. Flyway V11 trên PostgreSQL thật chưa chạy: Docker daemon không hoạt động, DB theo `.env` ở 5433 từ chối kết nối; PostgreSQL khác ở 5432 không nhận credential dự án. Hai lượt smoke fail do môi trường được giữ đúng, không gọi PASS.
- Hai lệnh chẩn đoán trung gian cũng FAIL do cách gọi: clean riêng module server lấy protocol artifact cũ; lần reactor đầu không quote property có dấu chấm nên PowerShell tách sai. Chạy lại clean reactor đúng dependency/quote đã PASS; đây không phải failure source nhưng được ghi để không che lượt lỗi.
- Chưa import HTML, chưa xác minh answer key/group mapping/media local và chưa thay DTO/importer/client renderer; không đánh dấu EX-01/EX-08 DONE hoặc sửa tracker/giờ.

## 19/10/2026 · Codex · sửa lại theo data seed thực tế

- Theo phản hồi người dùng, đọc lại trực tiếp `ets2026_test01.html` thay vì chỉ dùng thống kê manifest. Thêm `scripts/build-exam-seed.ps1`; parser kiểm SHA nguồn, lấy đủ câu/options/đáp án/group/media, loại chrome ngoài question/group và fail-closed nếu cardinality hoặc liên kết sai.
- Sinh `data/seedingTest/generated/ets2026_test01.seed.json`: 200 câu, 775 options, 42 group, 54 audio, 38 ảnh nội dung và 292 content block; phân bố Part `6/25/39/30/30/16/54`. Output chạy lại deterministic, SHA-256 `8cc6f345ef3885893e65c855b4dca4eb55a296cfcaf8b8cf87721eabb78d2b86`; 0 owner sai, 0 correct option mồ côi.
- Giữ seed `importable:false`: manifest ghi answer key/group mapping NOT VERIFIED, media remote chưa playback; option lấy từ review/explanation và chưa phải candidate-facing content đã xác minh. Không biến dữ liệu chưa xác minh thành đề vận hành. V11 bổ sung source path, permission/license evidence, các trạng thái verification, cùng `source_id`/`source_question_number` cho câu.
- Test source cuối: Maven 933 PASS, 0 failure/error, 59 skip; server package SUCCESS và JAR có V11. Lần gọi Maven đầu trong sandbox fail trước test do `C:\.m2` không ghi được, chạy lại với repository người dùng đã PASS. PostgreSQL/Flyway runtime vẫn NOT RUN do môi trường DB nêu trên; tài liệu API 25 REST/17 WebSocket giữ nguyên.

## 19/10/2026 · Codex · EX04 kiểm chứng PostgreSQL và EX02 contract review

- Đọc task.txt mới, fetch origin/mainf98a37f/PR65; tạo worktree/nhánh review/ex04-verification-ex02-contract-20261019. Giữ checkout gốc main3f5775b và docs/run.bat/untracked đang làm dở; không stash/reset/merge main.
- Audit V1–V11, QD21/QD35, service/validator/controllers/DTO và các taskEX01–08; phân biệt sourceSQL, runtimeAPI và kết quảDB. Native PostgreSQL18.1 clusterSCRAM/database riêng thay cho DB dùng chung, random loopback port; không xóa volume hoặc dữ liệu development.
- Test nguồn3077f40 tái hiện2FAIL thật: IMAGE/AUDIO cóNULL media_type bỏ quaCHECK/typedFK, COMMIT được mediaId không tồn tại. Thêm V12 CHECK discriminator, giữ nguyên V1–V11; không sửa/xóa dữ liệu lỗi, migration fail transactionally nếu tồn tại row không hợp lệ.
- NguồnSQL/test975284d: targeted58PASS; fresh11/upgrade10→11,5bad-upgrade rollback,28constraint negatives, V12good/bad-data và legacyHTTP đều đã chạy. Full clean verify984PASS/0FAIL/0ERROR/6SKIP. Mỗi lượt0schema test vàclusterstop0; password file/environment được dọn, raw/XML giữ ignored, công bố case/status/hash đã lọc.
- Runner metadata sửa cách chọnJava version để bỏ bannerJAVA_TOOL_OPTIONS; bản dẫn xuất lấy21.0.8 từ stderr gốc, giữrawResultSha256 vàkhông sửaraw. Không thay assertion hoặc SQL sau lượt verify; chỉ sửa metadata/tài liệu.
- [Báo cáoV11/findings](EX04_V11_VERIFICATION.md), [EX02gap matrix/records/JSON/media/START/roadmap](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md), [evidence](../evidence/ex04-v11-20261019/README.md). HTTP reproducer xác nhậnSCHEDULED vẫnGET/takeover/save/submit200 theoQD21; chỉ đề xuấtADR, không đổi semantics.
- Không importETSseed, không tảimedia, không đổiimportable/keys/JavaFX/v0/writer/revision/FULL. Phạm viA cầnC reviewSQL/test vàB reviewcontract/render; không tạo chữ kýowner, không tựDONE/tracker/hours hoặcmerge. Còn richimport/read/image/filesystem config, nguồn/key/options/groups/media vàphysical acceptance.


## 19/10/2026 · C/agent · MON-02 probe USB storage độc lập

- Fetch main1d35b537, PR70/71 OPEN và PR72 Draft da89962 chưa merge; đọc CLAUDE/entrypoint/BASE04/MON02/MON07/QA04/ADR/source và proposal72, search không thấy probe trùng. Nhánh riêng spike/v2-c-mon02-usb-storage, giữ checkout/nhánh cũ. Không skill codex-with-chatgpt theo lựa chọn trước của người dùng.
- Source276c4fa: Java21 CLI trong monitoring-spike, CIM MSFT_Disk(Number,BusType) và Win32_DiskDrive(Index,InterfaceType), bounded query/pipes/time/output, stable random per-run refs/diff/error isolation/generation; build/source/runtime hash receipt và run wrapper. Không Java/native dependency mới; PnP/window message và WMI subscription chỉ nghiên cứu NOT_IMPLEMENTED.
- Lượt Ctrl+C qua Tee mất FINISH giữ INCOMPLETE. Source30166b3 thêm direct JSONL CREATE_NEW, chạy lại clean verify riêng module35PASS/0FAIL/ERROR/SKIP và JAR SUCCESS; 34USB tests MOCK,1process test cũ, process/pipes và PowerShell resource thật. [Evidence/report](../evidence/v2-c-mon02/REPORT.md), [runbook](USB_STORAGE_PROBE.md).
- REAL Windows11/Java21.0.10/PowerShell5.1 STANDARD token: MSFT READY/count0; Win32 SCSI→UNKNOWN/nullcount; watch, timeout100ms exit3, maxLines4 exit5, JAR snapshot, Ctrl+C directfile FINISH130/children0/readers terminated. Existing file reject/hash unchanged, tampered receipt reject/byte restore, badargs64. QueryMillis không là OS notification latency. Không operator-confirmed USB action; physical0/12 NOT_RUN, không gọi count0 là hardware absence PASS.
- Maven sandbox đầu fail cache trước test và sandbox script policy chặn; dùng ngữ cảnh người dùng được phép, token STANDARD, không đổi ExecutionPolicy/registry hoặc che provider permission bằng Admin. Không sửa/xóa raw; raw29file/log/XML/receipt private ignored, public chỉ case/status/runID/summary/hash. Không có physical evidence khó tái tạo để tạo archive giả.
- Khuyến nghị MSFT polling để kiểm hardware tiếp, Win32 fallback có giới hạn/UNKNOWN; ADR-V2-05 C/B review PENDING, A xem auth/privacy/retention trước MON-07 integration. Không approve thay owner/DONE/backlog/MON07/08/E1/E2; không sửa Java production/protocol/migration/.env/process policy/FULL/gate. Chỉ commit/push/mở Draft PR theo ủy quyền rồi dừng.


## 19/10/2026 · C/agent · sửa CI PR73

- Tiếp tục nhánh spike/v2-c-mon02-usb-storage HEADfa764c9 sạch, fetch/GitHub xác nhận PR73Draft/chưa merge. Đọc CLAUDE/AGENTS đã cung cấp/runbook/report/source/workflow và log job118078191497 run39258040529:35test/1failure classificationREADY→ERROR,5.102s, khôngproblem diagnostic. Giữ log fail, không suy root cause từ thời lượng.
- Bổ sung diagnostic-only baseline5s:1PASS trong fresh JVM/PS processes, không fresh VM; faultMOCK delay5500ms:5s→QUERY_TIMEOUT5072ms,10s→READY5872ms, cleanup0. Source1e75924 chỉ test classification budget10s và scalar diagnostic sau close; không đổi expected, production/default5s/resource/parser/pipe/timeout150ms/CI workflow.
- Focused1case×3định trướcPASS; module clean verify35PASS/0FAIL/ERROR/SKIP, JAR/receipt nguồn1e. MSFTREADY0/Win32UNKNOWN/JARsnapshot thật tokenSTANDARD,15runtime hashes giống30166b3. Giữ JAR/receipt/report cũ và29raw SHA nguyên; không rootclean hoặc đụng worktree khác.
- CI39264645975 nguồn1e75924 SUCCESS, clean verify toàn reactor; module35PASS/classification6case suite3.612s. Chưa chứng minh chính xác nguyên nhân CI gốc vìlogcũ thiếuproblem, không gọi rerunPASS là proof. [Report](../evidence/v2-c-mon02/REPORT.md), [summary/newraw manifest](../evidence/v2-c-mon02/ci73-fix-20261019.json).
- Bản hồ sơ sau source chỉ docs/evidence; push cùngPR73 và chờCI HEAD mới, cập nhật mô tảPR, giữDraft/khôngmerge/approve. Physical0/12NOT_RUN, chưaADR-V2-05/MON02DONE/gate; không PnP/MON07/08/server/dashboard/E1/E2/backlog hoặc sửaPR70–72.

## 19/10/2026 · C/agent · MON-07 đề xuất contract đa tín hiệu

- Theo yêu cầu chỉ proposal: fetch origin/main `1d35b53729abade7b460d35ab07d042b6f9f3571`, kiểm branch/worktree/source/PR và tìm trùng. PR70 BASE-03 và PR71 MON-01 vẫn mở/chưa merge; chưa thấy proposal MON-07 riêng nên tạo nhánh `docs/v2-c-mon07-contract-proposal` từ main mới nhất trong worktree riêng. Giữ checkout gốc và các nhánh cũ.
- Đối chiếu envelope/validator, process encoder/queue/transport, WS auth/transaction/ACK, schema/history/push/parser/dashboard và CandidateMonitoringSession lifecycle. Source hiện tại đã sửa history vượt5000 thành409 INVALID_STATE retryable:false; ghi rõ khác thông tin đầu vào. Flyway mới nhất V12, proposal không tự chọn version SQL tiếp theo.
- Viết [contract đề xuất](MONITORING_MULTISIGNAL_CONTRACT_PROPOSAL.md): typed signal riêng, fields/bounds/JSON MOCK, auth/lifetime/preSTART giữ QD21, immutable retry/dedup/COMMIT-before-ACK, overflow/status, storage/cursor/recovery, privacy/disclosure/retention và owner decisions. Kèm [fixture](mon07-proposal/mock-exchanges.json) và22 implementation cases **NOT_RUN**. Queue256/2flight/offline60s/retention30ngày là đề xuất chờ duyệt, chưa cấu hình thực tế.
- Kiểm docs/fixture/source refs/diff/privacy được ghi [biên bản](mon07-proposal/VALIDATION.md), không chạy lại Maven/PG/GUI/WS runtime/USB/display/LAN/E1/E2. Không sửa production Java/DTO/migration/dependency/config/PROTOCOL hoặc backlog/tracker.
- Bàn giao C encoder/delivery, A auth/schema/transaction/retention, B consumer/emitter/lifetime. ADR-V2-04/BASE-04 và human A/B/C approval PENDING; ADR-V2-05/hardware riêng. MON-01 TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW, BASE-02/LAN/B PENDING, FULL default, BASE-03 NOT_DECIDED. Chỉ push/mở Draft PR theo ủy quyền; không merge, không MON-07 DONE/MON-02/MON-08 hoặc ký thay owner. URL/HEAD cuối được trả sau khi push và xác minh PR.


## 19/10/2026 · C/agent · MON-07 review PR72, chuẩn bị quyết định

- Fetch main1d35b537 và kiểm PR70–73, PR72 Draft/da89962/CI39248150270SUCCESS; reviews/inline/conversation PR72 rỗng, không tìm approval contract. Đọc lại CLAUDE/task BASE04/MON01/MON07, architecture/ownership/ADR, protocol/QD và source transport/auth/store/push; không dùng ChatGPT theo opt-out của người dùng.
- Sửa ngay proposal/fixture hiện hữu: chọn DISPLAY_COUNT_OBSERVED MOCK/no push cho slice đầu; tách catalog tương lai, không quảng cáo type chưa hỗ trợ. Bỏ410/tombstone không reachable dưới ACTIVE-only; làm rõ counter init atomic, auth sau chờ lock/revoke serialization, ACK/write credits và guard write treo. D07-01–05 đềuPROPOSED, gồm60s offline/30ngày retention; có kế hoạch file/thứ tự/test sau approval.
- Kiểm docs/JSON bằng Node.js, giữ kết quả lịch sử trong VALIDATION; source/runtime mới chưa code, toàn matrix22NOT_RUN. PR73 probe độc lập/physical12NOT_RUN không kéo vào production; BASE03gateNOT_DECIDED/FULLdefault, MON01physical/B/BASE02pending không đổi.
- Chỉ commit/push/cập nhật PR72 và theo CI, không merge/approve/backlog/DONE, không tạo nhánh implementation khi thiếu approval. A/C chốt wire/storage/auth, B transport/lifetime, nhóm notice/retention/offline. Kết quả CI cuối xem PR/report bàn giao.


## 19/10/2026 · C/agent · MON07 slice MOCK được người dùng ủy quyền

- User cho phép D07-01–04/mục11.1 PR72c9a5829 trong TEST cách ly, không thay approval A/B/C. Fetch/main1d35b537, proposal chưa merge; tạo feat/v2-c-mon07-signal-pipeline từ đúngc9a5829, sẽ Draft stacked vào nhánh proposal.
- Thêm typed strict parser/immutable encoder, queue RAM riêng, opt-in capability/ACK hook cùng RealtimeClient, conditional server service/counter/history và V13append-only. Không tự bật observer/capture/push/dashboard hoặc D07-05 offline/retention.
- Baseline/focused unit ban đầu qua; integration compile có PG testsSKIP khi chưa bật fixture, không gọi PG PASS. Runtime/CI/source evidence sẽ ghi [report](../evidence/v2-c-mon07-signal-pipeline/REPORT.md); logs ngoài reactor target để giữ nguyên lịch sử. Chưa DONE/gate/owner review/merge, giữ FULLdefault.


### MON07 — kiểm chứng cuối source0f9d8e7

- Focused216PASS/0SKIP; fullverify1031total=1025PASS/6SKIP/0FAIL/ERROR,12PG/WS cases,41case mới làsubset. Runtime thật Spring/HTTP/WS/PG loopback, inputMOCK, ACK-loss/deferred failureSIMULATED; actual reconnect/counter ordering/token/terminal/unassign locks và teardown0schema/clusterstop0. [Report/receipt/provenance](../evidence/v2-c-mon07-signal-pipeline/REPORT.md).
- Hai regression mới đã tái hiện FAIL: integerwrap getAsLong và same-socket new-run capability. Sửa exact parse/clock và bindexpectedCollector/currentgen; localFAIL/XML nguyên bản giữignored. Runner entrypoint trướccluster và summaryfilter EX04 sai cũng giữbiên bản, không sửaraw hoặc nhậnsummarysai.
- Sinh verification/case/XMLhash/raw inventory/JARreceipt từ source0f9d8e7. Chỉ docs/evidence sau đó, kiểm runtime diff rỗng. Main mới3bdefe6 chỉdocs/fixturesEX02, Java/SQL/POM không đổi, V12latest; không merge/rebase hoặc suy approval EX02 thànhMON07. Draft implementation vẫnstackedPR72c9a5829; CI đúngHEAD theoPR saupush.
- FULLdefault, BASE03NOT_DECIDED, MON01physical/B/BASE02pending, MON02physical0/12NOT_RUN; khôngcapture/push/dashboard/D07-05, khônghuman approval/MON07DONE/merge.


### MON07 — source cuối c77d8f8 / Draft PR74

- Bổ sung regression thật: expiry trong session-lock wait đãFAIL trên3674633+tests (DBclock target-list tính trước lockwait);7cb7478 đọc DBclock sau locks. UnitterminalFAILED/globalUNAUTHORIZED cleanup đãFAIL trên7cb7478+tests; c77d8f8 closeworker/queue/reportunconfirmed, actual tokenrevoke casePASS. Giữraw/XML/JAR/report/receipt cũ, không sửa evidence lịch sử.
- Sourcec77d8f8 focused219PASS/0SKIP, fullverify1034total=1028PASS/6SKIP/0FAIL/ERROR;44new cases subset/14REAL PG/WS,0schema/clusterstop0. [Report cuối](../evidence/v2-c-mon07-signal-pipeline/REPORT.md), versioned receipt/hash mới. Chỉ docs/evidence sau source; DraftPR74 stacked PR72, CI đúngpublicationHEAD theoPR. Không merge/approve/DONE/observer/push/dashboard/D07-05; FULLdefault/gate/physicalpending giữ nguyên.


## 20/10/2026 · C/agent · review và sửa slice MON-07 PR74

- Đối chiếu GitHub/Git: PR72/74 Draft/open, c9a5829 → 8b1b388; main3bdefe6/V12, worktree sạch và root f98a37f giữ nguyên. CI8b1b388 run39347449291 SUCCESS là baseline, không approval hoặc CI source sửa.
- Review toàn đường MOCK → serialize/queue/WS → auth/transaction/COMMIT → ACK/dequeue → paged history. Tái hiện 6 adapter regression FAIL (lease identity/stale heartbeat ACK+FORBIDDEN/wrong run/queued IO/old write deleting new correlation) và REAL HTTP/PG expiry sau assignment lock vẫn200 thay401. Logs/XML giữignored; không sửa expected hoặc timeout để che lỗi.
- Sửa RealtimeClient trực tiếp theo quyền user giao, B review identity/guard/owned cleanup; A/C review clock sau final assignment lock trong SignalService. Source2d73605 focused227PASS/0SKIP,16REAL PG cases; fullverify1042total=1036PASS+6SKIP/0FAIL/ERROR và JAR SUCCESS, teardown0schema/stop0. Receipt nguồn/JAR mới và evidence trướcfix trong review-20261020.json; CI exact publication HEAD theo Draft PR74 saupush.
- Kiểm OFF trên schema TEST riêng: Flyway13 SUCCESS, bảng signal/counter rỗng nhưng không service/routes. Đây là migration additive độc lập runtime flag, không sửa SQL đã áp dụng hoặc DB dev. Giữ raw và JAR/receipt c77 cũ với hash đúng. [Review/evidence](../evidence/v2-c-mon07-signal-pipeline/REVIEW-20261020.md).
- Chỉ MOCK/TEST; không observer/USB/push/dashboard/signal mới/offline60s/retention30ngày/.env/tracker. FULL default; gate/physical pending, không ký A/B/C/MON07DONE hoặc merge72/74.

## 19/10/2026 · C/Codex · BASE-03 kiểm chứng FULL/DELTA

- Yêu cầu thực thi BASE-03 sau audit; người dùng yêu cầu không dùng codex-with-chatgpt. Fetch và worktree riêng từ origin/main1d35b53; main local f98a37f giữ nguyên/sạch. Không push/PR/merge/reset/rebase/stash/clean/xóa worktree.
- Nguồn834f683: FULL-first163PASS, DELTA151PASS; real ProcessHandle/Edge/WS/PG/proctor, A3 COMMIT/dedup/auth, C2 RESYNC/restart và C4 hai candidate child JVM42/42 PASS. Oracle72ca/4negative detected,77file lặp cùng bytes,35/36 cặp có checkpoint chung; một seeded pair giữ NO_COMMON_SYNC_CHECKPOINT.
- Maven package regression983PASS/7SKIP/0FAIL/0ERROR với PG18.3 SCRAM cluster/database riêng, Java21.0.10/Maven3.9.15/Python3.15.0b3; Python analyzer22PASS. Nguồn463cbad bổ sung test WS retired DELTA và FULL low/same-sequence conflict PASS, guard bảo đảm production/POM không đổi. Hai lượt0schema,cluster stop0; private .env/password file xóa.
- Giữ lỗi runner inherited-pipe trước test, sửa wrapper rồi chạy lại; assertion/production không sửa. C4 measurement INCOMPLETE do owned hard-kill, không gọi là performance/E1/E2. Raw/XML/screenshots/binary giữ target ignored và SHA256; metadata công bố có bản gốc/hash, không đưa env secret vào Git.
- Kit mới pin source463cbad/runtime834f683/JAR hashes đã kiểm;22file và29+29checks source/giải nén PASS. Checklist41case vẫn NOT RUN, topology/signature B PENDING; không có thao tác mạng vật lý.
- [Báo cáo A–J](../evidence/v2-c-base03/REPORT.md), [handoff](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md). GateNOT_DECIDED, FULL default, E2NO; C chờ cùng B chạy vật lý, A xem auth/COMMIT/DB isolation. Không sửa production/wire/migration/JavaFX/QD/tracker hoặc triển khai MON-07/MON-02.


## 19/10/2026 · C/Codex · MON-01 verification kỹ thuật

- Xác minh BASE-03 HEAD9a97cab sạch và remote main1d35b53; sao lưu bền vững ngoài repo tại D:/Toeic-Evidence-Archive/BASE03-before-MON01-20261019T102703Z-51d0baf1:477raw/27public/5commit Gitbundle, checksum và account-only ACL. Sau MON-01 kiểm lại477file gốc và copy khớp; source/evidence BASE-03 giữ nguyên.
- Tạo nhánh/worktree test/v2-c-mon01-process-e2e từ9a97cab. Audit14chặng process pipeline và15acceptance criteria; không thấy BASE-02 human approval, giữ dependency pending. Người dùng tiếp tục yêu cầu không dùng codex-with-chatgpt.
- Chỉ thêm test còn thiếu vào LateEventsAndGapsSmoke: GUI UNREADABLE/absence/time ownership/no-cheating và automatic collector/event/state worker shutdown cho SUBMITTED/TIMED_OUT/CLOSED trước explicit cleanup. Chuyển state DB TEST là SIMULATED, không nhận HTTP submit/timeout toàn luồng.
- Sourcef78b407, runMON01-20261019T103217Z-25e62fdd:65dashboardPASS/0FAIL/0SKIP; package-DskipTests và C2 REAL Spring/WS/HTTP/PG/proctor JavaFX PASS; metadata2099/time columns, offline provenance/dedup/gap/rollback/auth/lifecycle PASS. Sources MOCK, faults SIMULATED; measurement COMPLETE chỉ diagnostic, không E1/E2. Own SCRAM cluster/DB/cổng riêng;0schema/stop0/private.env/password file đã xóa.
- Kế thừa BASE-03 REAL ProcessHandle/FULL/A3/C4/regression với đúng SHA/run/env; không chạy lại983/oracle72/42local để tăng PASS. [Report](../evidence/v2-c-mon01/REPORT.md), [handoff16dòng](V2_C_MON01_PHYSICAL_HANDOFF.md); physical0/B PENDING/BASE02 PENDING, MON01TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW, không DONE/tracker/hours.
- Giữ FULL default/DELTA NOT_DECIDED/E2NO; không đổi production/protocol/publicDTO/migration/auth/ACK/scope. Không MON-07/USB/focus/display; không push/PR/merge/xóa branch/worktree.

## 19/10/2026 · Codex · EX-02 contract candidate / ADR-V2-03

- Fetch main1d35b53, xác nhận PR65/V11 và PR66/V12 đã merge, CI baseline39214612088 SUCCESS; tạo worktree/branch docs/ex02-contract-adr-start-20261019 riêng. Root main3f5775b và file người dùng chưa commit được giữ, không reset/stash/checkout root.
- Hoàn thiện [contract candidate](EX02_RICH_EXAM_CONTRACT_V1.md), [ADR START/deadline](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md), [decision register/roadmap](plan-v2/EX02_DECISIONS_ROADMAP.md). Chọn document metadata Part7, strict v2 DTO, authenticated image no304 và existing audio bridge. ADR khuyến nghị explicit PROCTORED_V2, deadline tại Listening START duy nhất, legacy QD21 giữ; audit cache/locks/gate cả v1.
- [Fixture MOCK tự soạn](plan-v2/fixtures/ex02/README.md):200 câu/76group/55media,54 WAV im lặng và1PNG8×8; checker thiết kế positive và19 negative+10 access cases PASS. Đây không HTTP/PG/GUI/hearing production evidence. Kiểm chữ ký DTO/links/diff và CI ghi ở [report](../evidence/ex02-contract-20261019/REPORT.md) theo kết quả cuối.
- Còn dở: A/B duyệt D01–09/ADR; C review migration/auth/lock/timeout. Chưa sửa runtime/DTO Java/migrations/QD21 hoặc seed importable:false; EX03/05/07/06 chưa được triển khai ở task này, EX08 vẫn cần data/media hợp lệ và integrated/physical acceptance. Không merge, không ký owner hoặc đánh BACKLOG/TRACKER DONE.

## 19/10/2026 · Codex · PR67 Option A và xác nhận thiết kế theo đại diện nhóm

- Task mới xác nhận A/B/C đã đồng ý D01–D09, ADR-V2-03 và D10 Part1 Visual Readiness Option A; ghi TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE với nguồn “User-provided confirmation from the project team representative, 2026-10-19.” Không tạo chữ ký/tên reviewer/timestamp cá nhân hoặc GitHub Approving Review (reviews đầu task=[]).
- Fetch lại main1d35b53/PR67 HEAD91786ba, worktree riêng clean, rootmain3f5775b+dirty user files được snapshot giữ nguyên. Lần escalation fetch đầu timeout, retry một lần theo hướng dẫn tool rồi chạy được; không bypass quyền.
- [Contract](EX02_RICH_EXAM_CONTRACT_V1.md)/[ADR](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md)/[D01–D10](plan-v2/EX02_DECISIONS_ROADMAP.md): Part1-only allowlist prefetch/manifest, ATTEST/INVALIDATE/epoch/lease, all-roster Audio+Visual trước authoritative Listening START; clock không khởi ở prefetch. Chấp nhận bytes Part1 có thể bị chủ máy trích xuất; no-store/RAM không bảo mật tuyệt đối. EX06 bridge rich→existing segments và image-visible/layout/two pulses trước play, budget/fail-closed/no replay/no deadline reset; source player/AudioService/ExamService giữ nguyên.
- Fixture MOCK tự tạo200Q/76group/57media,54audio, six Part1 slots reuse một image và Part4/7-only refs riêng. Lượt thiết kế đầu58cases PASS (29 cũ+P01–P09/N01–N20); .NET PNG size/hash/decode thật không JavaFX GUI. Checks cuối records/JSON/canonical Java hash/links/diff/CI ghi [report](../evidence/ex02-contract-20261019/REPORT.md) theo kết quả thực.
- Không Java/runtime/migrations/POM/WSv0/legacyQD21/seed/tracker changes; target HTTP/PG/WS/JavaFX/hearing/LAN NOT RUN. Phê duyệt thiết kế đã có theo đại diện; còn implementation tasks/evidence/dataset và human merge. Chỉ push/update PR67, không merge hoặc tự đánh EX03–08 DONE.


## 20/10/2026 · Codex technical review PR72

Đối chiếu proposal c9a5829/fixture7cases/43relative links với implementation thử nghiệm PR74 và test parser/correlation/dedup/cursor đã đọc. Hòa docs main giữ đủ BASE03/MON01/EX02, không đổi fixture/schema/runtime; cho phép merge tài liệu vẫn PROPOSED theo user. Không áp offline60s/retention30ngày, không human owner approval/ADR APPROVED hoặc MON07DONE.


## 20/10/2026 · Codex technical review USB probe PR73

Đọc CIM projection/classification, tracker, parser, timeout/output/child/pipe cleanup và source receipt. Tự chạy35tests/0SKIP/JAR trên aa260ef, MSFT READY0/Win32 UNKNOWN trên STANDARD, child0/readers true; không hardware hotplug. Hòa main chỉ docs/shared tests, probe source/scripts không đổi, public7hash giữ nguyên. User cho phép merge spike nếu CI HEAD cuối đạt; không ADR-V2-05 approval, physical0/12NOT_RUN, không integration/observer/retention/FULL gate changes.

## 20/10/2026 · Codex · review kỹ thuật C và merge theo ủy quyền user

- Đã đọc/check GitHub/head/base/diff/CI/source cho70–74, không audit lại toànT1–T4. Bảo vệbranch main không enabled, rulesets rỗng; protection endpoint403, không bypass hoặc GitHub APPROVE. Merge commit70e0ef252/712595106/72a636e2a/7346869eb; xác minh tree exact testedHEAD, giữbranches/rootmainf98a37f.
- Findings mới: standaloneC2 thiếuSmokeCleanup trong loader (FAIL NoClassDef/1schema) →92f9faf copyhelper,65PASS/GUI/WS/PGcleanup0. Fulltích hợpa5339dc FAIL1USBchild vìJAVA_TOOL_OPTIONS bannerstderr; controlled có/không env chứng minhCHILD_DIAGNOSTIC→READY, d6bf60a cô lập/restoreenv vàMavenUTC, không đổiprobeexpected/timeout. RawFAIL giữ nguyên.
- Source d6bf60a fullverify1071PASS/6SKIP/0FAIL/ERROR,17REAL PG cases,5001event đềuquaWS/auth/transaction/COMMIT/ACK,51pages +24concurrent +revoke/unassign giữa trang. Headlessprocess smoke dùngRealtimeClienttích hợp PASS/0schema/stop0, không lặpunit/GUIcampaign. Focusedcd00630=228PASS làsubset. Receipt4JAR/sourcehash tại [báo cáo tổng hợp](../evidence/c-technical-review-20261020/REPORT.md).
- #74 chuyểnbase main/sau72, không lặpproposal. MigrationV13OFF vẫnáp, maintrước74V12; EX03V14/EX07V15 không collision tại audit. Không .env/devDB/Flywayclean/rootclean/productiondeploy, không observer/push/dashboard/offline60s/retention30ngày.
- Codex technical review phầnA/B liên quan đãlàm; không chữkýowner/ADR APPROVED/hardware/gate/DONE. #74 chỉmerge sauCIpublicationHEADđạt; kết quảcuối/mergeSHA ghiPR/bàn giao. FULLdefault/LAN0/41/USB0/12pending; khôngđổitracker.

## 20/10/2026 · Codex · kit main tích hợp, không chờ USB

Từ main a6fba311/worktree riêng, tái hiện builder cũ từ chối runtime mới; thêm BuildFromHead package/pins bản xuất và SkipArchive, regression MOCK và CI script checks. Main mới không lấy spike cũ làm nguồn. Mở rộng H01–H12 và runbook TEST GUI/LAN, phân loại đủ41cases. Mới chạy35USB/65consumer/33kit PASS, Windows MSFT READY0/Win32 UNKNOWN/cleanup/cap/no-overwrite; invocation Maven đầu sai quoting giữ log, chưa chạy test rồi sửa command. Không DB/server/.env/production runtime/hardware. [Report](../evidence/c-integrated-kit-20261020/REPORT.md) sẽ ghi source build/receipt/CI/PR đúng HEAD. User cho phép review/merge commit nếu đủ điều kiện, giữ nhánh; không human approval/gate/DONE, physicalUSB0/12/LAN0/41/Bpending. Bước sau C/B GUI FULL lifecycle với TEST scope do A cấp, chưa thực hiện trong task này.
## 2026-10-19 · A / Codex · EX-03 rich importer

- Đã làm: branch/worktree riêng từ main3bdefe6 (CI đúng merge SUCCESS); strict POST v2 import200Q/7Part,11records/7enums, media bytes verification/recheck, sourceRef→RETURNING IDs, audio_file bridge, atomic transaction/deferred FK/create-only409; v1 create rich409. V14 vì PR74 đã dùngV13; không sửa V1–V12/monitoring/JavaFX/Listening hoặc seed ETS.
- Commit: c16b617 importer; d3b0c19 corrective DB-clock test + read-back;9244321 chặn credential query aliases và NUL/surrogate. Workspace gốc và dirty tracked files giữ nguyên.
- Đã kiểm: source9244321, run ex03-20261019T154906Z-34c16188, clean verify1104total=1098PASS/6SKIP/0FAIL/ERROR;114case mới PASS, PostgreSQL18.1/Flyway/Spring HTTP thật, fixtureMOCK tự soạn. Fresh/upgrade/preservation/200Q/documents/54audio/source mapping/roles/negative/race/rollback/v1/scoring PASS; junction Windows thật PASS;3JAR có receipt,0schema còn lại/clusterstop0.
- Lịch sử âm giữ riêng:11ERROR helper thiếu examId trong asset path;1FAIL legacy test so DB/JVM lệch5ms;9FAIL mới trước sửa secret aliases/Unicode. [Report/evidence và6SKIP](EX03_RICH_IMPORTER_VERIFICATION.md) phân biệt từng source/run, không lấy PASS cũ thay source mới. GUI/hearing/LAN/máy2/dataset production NOT RUN.
- Còn dở: READY_FOR_REVIEW, không tự merge/human signoff/DONE/hours. C/B review schema/auth/transaction/shared DTO; A/C phối hợp V13/V14 trước merge/deploy. EX05 image delivery/allowlist, EX07 session/visual/START, EX06 renderer/bridge/visible gate và EX01/08 media/quyền/keys thật còn riêng.
- Tiếp theo: push nhánh/mở PR EX03 và đọc CI đúng HEAD theo task; giữ main ổn định, không merge trong lượt này.

## EX05 — checkpoint triển khai, 19/10/2026

19/10/2026 — EX05 đang triển khai trên stack EX03: resolver verified bytes, Part1 canonical manifest và image route fail-closed khi thiếu EX07 authority. [Checkpoint](EX05_AUTHENTICATED_IMAGES.md). Main/PR75 chưa merge; V13/V14 MERGE BLOCKED. Targeted PG/HTTP đang chạy; không EX05/FE DONE, không sửa tracker/hours.

Unit focused ghi25case RichImageTest PASS; lượt PG mới/chính xác source đang chạy, không kế thừa PASS từ EX03. GUI/LAN/hearing NOT RUN.

## EX05 — clean verify20/10/2026

Source05cc694 clean verify1127PASS/6SKIP/0FAIL/0ERROR, PG18.1/HTTP thật, đề MOCK tự soạn;0schema/clusterstop0. [Evidence](EX05_AUTHENTICATED_IMAGES.md).29caseEX05 mới PASS; V13 sau14 bịFlywaydefaultchặn thật. AuthorityEX07 còn thiếu, image route fail-closed; GUI_PHYSICAL_NOT_RUN/hearing/LAN NOT RUN; MERGE BLOCKED/PR75/V13coordination. Không tracker/hours/human signoff.

## 2026-10-20 · A / Claude Code (cloud) · EX-07 resume

- Khôi phục: WIP người dùng push `6226cce` trên `feat/ex07-rich-session-lifecycle-20261019` (từ EX-05 `05cc694`). Không reset/rebase/clean; WIP giữ nguyên trong lịch sử.
- Đã làm (`f5df3af`): reorganize V2 chép PROCTORED_V2; restart xoay epoch/xóa READY ca SCHEDULED; timeout chỉ bỏ qua SCHEDULED; `toeic.visual.lease-ms`; fixture test đăng ký bean `V2SessionPolicy` (injection vẫn bắt buộc, fail-closed); test PG/HTTP `RichSessionLifecyclePostgresTest` 8 case.
- Đã kiểm: PostgreSQL 16.15 container (không phải 18), JDK 21.0.12 Linux. Lifecycle 8/8 PASS, race 3 lượt PASS; `mvn clean verify` server 443/0 fail/4 skip, client 3 FAIL `ControlledProcessHarnessTest` có sẵn trên Linux. Lỗi trước sửa: WIP 38 ERROR (thiếu bean + thiếu `TOEIC_SEED_PASSWORD`). GUI/LAN/hearing NOT RUN.
- Còn dở: thứ tự Flyway V13/V14/V15; chi phí verify toàn media mỗi ATTEST (owner quyết); WS disconnect hook; S08/S09/S10/S11/S15, V03/V04/V14–V20 NOT RUN. Không merge, không DONE/tracker/hours.
- Tiếp theo: A/C review lock/transaction, B tích hợp EX-06 theo [EX07_SESSION_LIFECYCLE](EX07_SESSION_LIFECYCLE.md) §1.

## 2026-10-20 · B / Claude Code (cloud) · EX-06 TOEIC Room JavaFX 7 Part

- Đã làm: nhánh `feat/ex06-javafx-rich-renderer-20261020` từ EX-07 `93c7019`. Có `RichExamModel` (7 Part, bridge Listening §7.1), `RichExamApiClient` (chặn khóa scoring/provenance), `RichImageStore` (RAM, kiểm SHA/pixel), `VisiblePulseGate` (hai pulse thật), `ListeningPlayGate`, `RichExamView`, `RichLobbyPane`, `VisualReadyCoordinator`, `RichAttemptView`, `RichEntry` (probe preparation để chọn luồng V2 hay legacy), `toeic-room.css`. Có thêm script demo và ghi chú khoảng trống Zenlish→V2.
- Commit theo checkpoint: `a25305d` (CP1), `ddc1dff` (CP2–3), `34e225f` (CP4); CP5 là E2E, tài liệu và chỉnh giao diện.
- Đã kiểm: Linux/OpenJDK 21.0.12/Xvfb/PostgreSQL 16.15. Test rich 31/31 PASS, gồm E2E thật với server, PG và WS (3/3 lần). Lượt E2E: import MOCK 201 → lobby READY → START → 54 đoạn Listening phát xong → autosave được server xác nhận. Số liệu `mvn clean verify` ghi ở `KIEM_THU.md`. Lỗi gặp trước khi sửa: ConcurrentModification trong `RichImageStore.useScope`; ảnh tràn khung; các helper test duyệt node trùng. Ba lỗi đều đã sửa và giữ lại test tái hiện.
- Còn dở: nghe thật, MSI/LAN/máy hai NOT RUN; âm thanh trong container dùng ALSA null. Chờ EX-07 review/merge và thứ tự Flyway; dữ liệu đề thật EX-01 chưa có. Không merge, không DONE.

## 2026-10-20 · B (tích hợp) / Claude Code (cloud) · EX-06 + Proctor redesign + PROCTORED_V2

- Đã làm: worktree riêng, nhánh `integrate/ex06-proctor-20261020` từ EX-06 `448f58e`, merge UI giám thị `1359dba` (`7c53613`, không xung đột). Launcher route PROCTOR → `ProctorRedesignView` (`5c8e4a4`). Panel V2 trong "Điều khiển ca": catalog có cursor, tạo ca, READY polling có giới hạn, START có xác nhận và retry cùng `requestId` (`f002bc1`). E2E thật và CSS tab (`182f5f9`), bằng chứng (`64b7c5f`). Script demo thêm `-ImportOnly`. Không đổi contract V1/V2, migration hay server.
- Đã kiểm: Linux, OpenJDK 21.0.12.1, PG 16.15, Xvfb. Test V2 mới 25/25 PASS; E2E thật PASS 3/3 trên `182f5f9`, giám thị tạo ca và START bằng UI, thí sinh làm bài và nộp, kết quả 1/200 khớp key MOCK, các probe âm đúng. `mvn -fae clean verify` exit 1 (chi tiết ở `KIEM_THU.md`). Lỗi gặp trước khi sửa: thiếu locale UTF-8 khi chạy suite; nút mất dấu `_` do mnemonic; race trong chính test E2E (đọc UI trước khi render).
- Còn dở: Windows runbook/MSI/LAN, PG18, nghe thật NOT RUN. Thứ tự Flyway V13↔V14/V15 chờ A/C chọn ([phương án](INTEGRATION_EX06_PROCTOR_20261020.md#6-audit-flyway-chỉ-đề-xuất--không-sửa-migration-không-bật-outoforder)). LT04 GUI legacy FAIL cả trên main trong môi trường Linux. Chưa review/merge, không DONE.
- Tiếp theo: A review client↔EX-07, C review START/READY, B chạy runbook mục 5 trên Windows.

## 2026-10-20 · Release (Agent 3) / Claude Code (cloud) · Review chuỗi V2 trước merge `main`

- Đã làm: kiểm ancestry, PR #72/#74/#75/#76 và CI. Tạo PR stacked #77 (EX-05), #78 (EX-07), #79 (EX-06). Nhánh ứng viên `stabilize/v2-rich-main-candidate-20261020` gộp chuỗi với EX-05 docs `6c5065d` và `main` `e0ef252`; xung đột chỉ ở docs, giữ cả hai bên. Sửa test phụ thuộc thứ tự `RichAttemptE2eGuiTest` (`3f5a114`). Sửa regression `refreshScope` chạy ngoài FX thread, kèm test hồi quy (`56ea3e2`). Thêm release review và bản nháp handoff Agent 4.
- Đã kiểm: PostgreSQL 18.1 thật cho 6 kịch bản Flyway và full verify. CI Windows success. Phân loại lỗi trong [release review](RELEASE_V2_MAIN_REVIEW_20261020.md) §5.
- Còn dở: A/C chốt Flyway; review/approval của người cho 5 PR; giải xung đột docs #75/#78; Windows PG18 + GUI. Không merge `main`, không DONE, không ghi approval.
- Tiếp theo: owner quyết §4 rồi merge theo checklist §3; acceptance trên `main`; cập nhật `EX01_DATA_PIPELINE_HANDOFF.md`.

## 20/10/2026 · Codex · FULL lifecycle local, desktop NOT_RUN theo user

- Main5bbeaf2 sạch/worktree riêng, build main JAR/Java21/Maven3.9.15, native PG18.3 TEST/SCRAM/port động. Seeder và import/session API cấp candidate1/2/proctor1/assignment, TEST Reading và SYNTHETIC WAV theo fixture; không .env/DB dev/SQL ép transition.
- Mở production Application bằng JAR trong smoke có sẵn, FX controls lập trình/two Stages one JVM:6nhóm/10checkpoints Start FULL3worker→Stop0→epoch mới, logout/relogin khác candidate, process Edge sở hữu PID/start→state/history/dashboard, submit thật qua button/dialog→SUBMITTED, timeout tự nhiên8s→TIMED_OUT và app-stop→0worker/0toeic-thread. Đây không native desktop input/human acceptance.
- ComputerUse native pipe unavailable sau retry/reset; đã chuẩn bị hai production JAR JVM riêng và nhóm thao tác ngắn. User chọn tiếp tục tự động/desktop NOT_RUN. Passive60sample UNSYNCED/seq0; hết cửa sổ240s dọn đúng hai app, không gọi force teardown là GUI Exit PASS.
- Lượt extended49007cf FAIL executor assertion:4toeic-exam-http gồm fixture lazy workers; giữ log/dump.47caa50 đóng rõ API fixture trước assert, rerun cùng ca PASS; không sửa production expected/timeout.215unit regression PASS/0SKIP MOCK riêng, không cộng10checkpoints hoặc run lặp.
- [Report/receipt](../evidence/c-full-lifecycle-20261020/REPORT.md):4run schema0/clusterstop0, Edgealive0, owned JVM/PGlistener0; raw/JAR/thread dumps/ảnh chưa lọc ignored, chỉ1ảnh Scene TEST đã rà công bố. FULLdefault/experimentOFF/USB0/12/LAN0/41/gate/humanpending, không DONE/ADR. Bước sau C không USB: desktop input/window-close khi helper hoặc người thao tác sẵn sàng, TEST/API tự dựng được; chưa thực hiện bước sau này.


## 2026-10-20 · Release Integration Engineer · Hoàn tất nghiệm thu chuỗi V2 & tích hợp main

- Worktree riêng \E:\Project\toeic-v2-release-integration\, branch elease/v2-main-integration-20261020\ (HEAD aa830b\).
- Hợp nhất hoàn chỉnh nhánh QA Windows \qa/windows-v2-mon07-20261020\ (commit 8006f\) với \origin/main\ mới nhất (bbeaf2\ và sync 9954b\ PR #81).
- Flyway V1–V15 áp dụng tuần tự không lỗi, 0 out-of-order, 0 checksum mismatch trên PostgreSQL 18.6 độc lập (cổng 55434).
- Maven build & test: \mvn test\ BUILD SUCCESS 1264 tests (0 failures, 0 errors).
- PostgreSQL 18.6 Integration: 159/159 tests PASS 100% (gồm 43/43 Flyway migration scenarios, 28/28 V2 rich/image/session, 70/70 runtime/audio/history bounds, 18/18 MON-07 signal pipeline & disabled check).
- JavaFX GUI: 27/27 component tests PASS 100% (\RichExamViewGuiTest\, \ScoreResultDialogTest\, \ProctorRedesignGuiTest\, \ProctorV2SessionPaneGuiTest\).
- Live Server E2E Full-Stack: 2/2 tests PASS 100% (\ProctorV2E2eGuiTest\, \RichAttemptE2eGuiTest\) trên server 18081 thật và PostgreSQL 18.6; xác minh trọn vẹn import V2, catalog, ca thi, negative probes, READY, START, autosave, submit và clean worker shutdown.
- Windows PowerShell 5.1 demo script: \demo-ex06-v2.ps1\ exit code 0 cho cả \-ImportOnly\ và tạo ca PROCTORED_V2.
- Bảo toàn tuyệt đối database sản xuất \	oeic-db\ (cổng 5433) và worktree khác. Nhánh \eat/ex01-html-pipeline-20261020\ của Agent 4 không bị đụng tới.
- Push release integration branch lên remote: elease/v2-main-integration-20261020\ sẵn sàng merge vào main.
- Trạng thái: **READY_FOR_AGENT4_BACKEND**.

## 20/10/2026 · Codex · E1 join dẫn xuất có provenance

- Nhánh riêng từ main159954b, đọc QA05/E1/analyzer/Joiner/HarnessCli/manifest; openPR không có triển khai trùng. Chốt contract nhỏ trong README trước code, Pythonstdlib, user optout codex-with-chatgpt giữ. Không Java/production/DB/wire changes.
- derived.py gọi HarnessCli join vào root mới, khóa7input và original join/manifest/e1manifest/resources/run/clock/collector/trial/JAR/creator+helper. Analyzer explicit vẫn fullvalidate original rồi mới chọn derived; no fallback/skip validation; diff trial/metric và receipt có input/output/toolhash, finalraw recheck. JAR version bằnghash, source null nếu không kiểmđược; raw/analyzer/toolsource tách riêng, không gánHEAD cho raw cũ.
- Baseline12PASS, fixture MOCK thayjoin bị analyzer159954b từchối RAW_CHECKSUM đúnggiới hạn; toolingc7f12a7 27PASS/0SKIP gồm15case mới, không cộng baseline như39unique.1smoke-short (3trial) vớiJARsource5bbeaf2 đãkiểmreceipt/hash vàJava sourceequivalent:COMPLETE/resourcesCleanedtrue; rejoin0diff,7rawhash giữ,2analysis có8file giốngbyte/campaignPARTIAL. Invocation Java đầu sai quoting trướcmain giữlog, không retrymeasurement.
- Rawlịchsử NOT_AVAILABLE:kiểm đúngsuite path trong19registeredworktree roots vàtargetđượcdocs/receipt nêu, không quét ổ/đo lại12. [Report/receipt](../evidence/c-e1-derived-20261020/REPORT.md) safehash/scalar; raw/JAR/CSV/log localignored. FULLdefault/MON07OFF/gate/E1acceptancepending, khôngQA05DONE/tracker/hours. PR/CI/merge sẽghi đúngHEAD trongPR, chỉmerge commit khiđủđiềukiện, giữnghánh.

## 20/10/2026 — C: slice typed signals MON-07 (USER_AUTHORIZED_EXPERIMENT)

- Base main ad8c370; nhánh feat/c-mon07-typed-signals-20261020 riêng, giữ worktree cũ.
  Đọc task MON-02–09/proposal/pipeline/probe; open EX PR chỉ tới V15, chọn V16 append-only.
- Chốt bảng contract trước code; runtime a0e9200/test c71ddde thêm ba USB/focus/window, giữ display,
  parser strict/immutable, một queue, capability subset và typed persistence/history.
- Regression FAIL chứng minh queued USB write qua sau capability thu hẹp; sửa guard ngay
  trước socket IO. Legacy fixture unsupported USB nay schema hợp lệ: giữ fixture lịch sử,
  test phân biệt schema/capability. Log/XML lỗi còn local ignored.
- Baseline227/focused252 PASS, 21 PG pipeline cases; input MOCK, WS/HTTP/PG thật và faults
  SIMULATED. Upgrade15→16 giữ nguyên display/counter/checksum; OFF không mở runtime lane.
  Custom receipt selector thiếu suite #methods: giữ receipt cũ, reconciled riêng từ log/XML.
- [Evidence](../evidence/c-mon07-typed-20261020/REPORT.md), build/CI/PR cuối được ghi tại đó/PR.
  Teardown0schema/cluster stop0. Không .env/DB dev/observer/capture/push/dashboard/E1/E2,
  không ký owner, đổi gate/tracker/DONE; FULL mặc định, experiment OFF, hardware pending.

### 20/10/2026 — MON-07 final review trước merge PR84

Trên publication10cabe2 phát hiện raw payload/details byte bounds đo compact JSON, có
thể bỏ sót whitespace. Test1/1FAIL được giữ; runtime abb282e đo original object spans,
không thay process parser/schema/defaults. Focused cuối253PASS/0SKIP,21 REAL PG/WS cases
kiểm thêm padded payload/details reject/no insert; TEST0schema/stop0. JAR/receipt cũ
lưu riêng, build mới pin abb282e; CI HEAD cũ không dùng để merge bản mới. Source/hashes/
CI cuối tại report/PR84, không ký owner hoặc đổi gate/DONE/hardware.

## 20/10/2026 — C: adapter USB MOCK qua MON-07

- Từ main e189b61, nhánh feat/c-mon02-usb-adapter-20261020 riêng; đọc probe/typed pipeline/
  task cards, không PR adapter trùng. Chốt semantics trong MON02_USB_ADAPTER_EXPERIMENT
  trước code; client không phụ thuộc monitoring-spike, server chỉ test dependency để
  bridge model/classification, không copy provider/CLI hoặc scanner runtime mới.
- Source693cf44 có unit174PASS và focused310PASS/24PG; review tìm hai lỗi ownership
  duplicate adapter/rebind old context,2/2FAIL được giữ. aac7528 sửa one USB claim/context;
  focused cuối312PASS/0SKIP, cùng24PGcases,0schema/stop0; không rerun blind.
- Baseline154PASS trước sửa. SetupPG đầu lỗi socket Permission denied trước test, runner
  mask DateTime lỗi vì chưa có startUtc; log cũ giữ nguyên. Kiểm Windows excluded ranges,
  chọn range TEST khác và ghi cổng ở runner local; không khẳng định nguyên nhân exclusion
  khi số cổng FAIL chưa được lưu, không đổi .env/DBdev hoặc source provider/timeout.
- Shared run cấp IDs/sequence, source baseline độc lập delivery/offline/full, map≤64 và
  generation cleanup; allPARTIAL, ref chỉ slot correlation liên tục, lỗi bỏ ref/no removal.
  [Contract](MON02_USB_ADAPTER_EXPERIMENT.md)/[evidence](../evidence/c-mon02-adapter-20261020/REPORT.md).
- Build/CI/PR cuối ghi trong report/PR. Giữ USB0/12NOT_RUN, MON-02chưaDONE, FULL/default
  experimentOFF/ADR/gate/ownerpending; không production observer/dashboard/rules/E1/E2.


## 20/10/2026 — C: MON-07 status/gap USER_AUTHORIZED_EXPERIMENT

- Base main519063d/PR85, branch feat/c-mon07-status-gap-20261020 riêng; read task/proposal/
  typed/USB/source paths.11openPR không trùng slice/migration, maxV15; mainV16. Contract
  trước code dùng enums proposal; activity source deferred, không human approval.
- Runtime1b84d30 thêm strict immutable status/gap DTO, generic capability/store/history
  dùng chung; V17 append-only. Status latest4slots/1s monotonic, không worker mới.
  Data256/1MiB+reserve1gap8192, tổng257/1056768bytes/2flight-write; freeze/reset atomic,
  drop mới batch khác, no recursion, alternating control/data. CounterMAX/sequence stop
  giữ unreported, close/revoke không flush; context lifetime claim và generation.
- Baseline246PASS. First unit1FAIL fixture cũ coi status unsupported; giữlog/XML, sửa
  fixture và thêm admission/pre-write controls guards. Không timeout/skip/swallow.
  Focused334PASS source1b84; test-only1c008c8 tăng OFF check hai types, cuối334PASS/29PG.
- Fullverify/JARsource1b84:1192PASS/144SKIP/0lỗi,10JARchecks,4artifact hashes. PG thật TEST,
  inputsMOCK, ACK loss/COMMIT faultsSIMULATED; historyfixedthrough/auth/rollback/upgrade
 15/16→17 và OFF PASS, teardown0schema/stop0/password/pidabsent cả2focusedruns.
- [Report/receipt](../evidence/c-mon07-status-gap-20261020/REPORT.md); raw ignored, không
  .env/DBdev/rootclean/broadkill. Giữ FULLdefault/experimentOFF/USB0of12NOT_RUN,
  MON07chưaDONE/ADR/gates/policy/ownerpending. CI/PR/merge đúngHEAD ghi trongPR, giữbranch.


## 20–21/10/2026 — C: USB integrated TEST từ provider tới history

- Attachment e80d60fa USER_AUTHORIZED_EXPERIMENT cho đọc CIM tối thiểu trong TEST explicit,
  không human A/B/C approval; base mainb68e19a gồm B MON03/04, branch/worktree riêng.
- Chọn coordinator provider-agnostic trong client, bridge/projection/demo trên server
  test classpath đã có dependency; không module/native/lib/scanner mới, không runtime
  CIM trong client/server JAR, không hook candidate production. Cadence fixed-delay,
  query timeout100..10000ms/duration1..120s, no overlap; control flush/stop độc lập query.
- Baseline194PASS. Coordinator+shared/provider regressions204PASS,10case mới dùng latch;
  timeline/error/unknown/queue drop/reconnect/stop/duration/ownership/invalidconfig.
- Runner36cc749 lỗi pg_ctl inherited output pipe: PG READY nhưng wrapper chưa return,
  chưa chạy Maven. Giữ rawFAIL, stop đúng cluster ownpath; fdc90a1 dùng NoCapture startup,
  cleanup tự động sau đó PASS. Không đổi provider/parser/timeouts/DBdev/.env/broadkill.
- MOCK4REAL WS/HTTP/PG cases trên fdc/e7PASS:9event/status/canonical history, ACK loss
  SIMULATED retry1row, admin revoke fixture/actualWSdenial, business deadline thật,
  deferred COMMIT faultSIMULATED rollback/noACK rồi retrycùngmessage. Seeder+API login/
  import/create-session tạo scope, không SQL INSERT user/attempt/assignment.
- REAL33e/e7 MSFT15s/delay1s/query5s:10scan READY0 STANDARD,1OBSERVER_STATUS READY/INITIAL
  COMPLETE được COMMIT/ACK/history,0USBevent. e7 lưu readback/XML theo invocation. Stop-file
  REAL sau1scan CANCELLED/metadata130/shell1/pipelineVerifiedfalse; cleanup tất cả0child/
  readers+workers stopped/0schema/PGstop0. Không hotplug hoặc kiểm phímCtrl+C thực.
- Build e7 fullverify1220PASS/149SKIP/0lỗi,15JAR/dependency checks; probeClasspathCLIhelp0.
  Invocation -jar sai vì probe không có mainmanifest đã giữlog, không phải sourcebug.
- [Report/receipt](../evidence/c-usb-integrated-20261021/REPORT.md), raw/JAR localignored.
  FULLdefault/experimentOFF/USB0of12NOT_RUN/MON02+07chưaDONE/ADR/gate/ownerpolicy pending.
  Chỉ merge commit sau exactHEAD CI/rules/finalreview; giữ branch/worktree, không task khác.


21/10/2026 — MON-09 C+B: [timeline đa tín hiệu](MON09_MULTI_SIGNAL_TIMELINE.md), [evidence](../evidence/cb-mon09-timeline-20261021/REPORT.md).
Base dcb124d; runtime5066347/build1c1523b. Dashboard hiện hữu đọc typed history fixed-through100×3/cycle, poll3s/budget12s/500rows; process state riêng, Listening status projection. Sửa producer identity trùng collector lease có FAIL→9ACK. Real FX existing sources1PASS; MOCK real WS/PG/UI1PASS(A19/B1/drop3); REAL CIM1PASS(8READY0/ACK1/UI1/0USB). Full verify1263PASS/154SKIP/0lỗi, GUI cũ12PASS, JAR24checks; sort oracle sạch05e154a8PASS. TESTschema0/PGstop0/workersclosed, raw ignored. FULLdefault/experimentOFF, USB0/12/physicaldisplay/desktop/LAN NOT_RUN, owner/ADR/policy/gate pending, MON09chưaDONE. Không approval thay B hoặc đổi backlog.
Ủy quyền người dùng: triển khai C+B và merge commit nếu CI/quy định đạt; trạng thái CI/merge cuối tại PR. Không cấp human approval. Giữ nhánh và evidence lịch sử.


21/10/2026 — MON-08 C+B: [rule/aggregation thử nghiệm](MON08_EXPERIMENTAL_ALERTS.md), [report/ảnh/receipt](../evidence/cb-mon08-alerts-20261021/REPORT.md).
Base1507591; runtime1e4b1ca/build+TEST9e31d50.4rule EXPERIMENTAL, ngưỡng3/6 trên bucket60s/window300s; server committed/scoped/ACTIVE/capped read model → timeline hiện có, không cache/migration/observer/persistentpipeline. Baseline95/focused134PASS; fullverify1302PASS/155SKIP/0lỗi, GUI12PASS, JAR21checks. MON08MOCK→realWS/HTTP/PG/Scene1PASS(10initialinputs/focus6/USB1/actual3drops/sourcecap20TEST); OFFlegacy1PASS. GiữFAILoverflow/no-store/fixture và0schema/PGstop0. FULLdefault/cả2experimentOFF, USB0/12/physicaldisplay/OSdesktop/LAN NOT_RUN, owner/policy/ADR/gatespending, MON08chưaDONE; không approval thay B/backlog/E1/E2.
Người dùng cho phép C+B implementation/technical review/merge commit khi exactHEAD CI và rules đạt; final PR/CI/merge được ghi tại PR, giữ nhánh và raw. Không ký human approval.


## 21/10/2026 — Codex: kiểm chung MON01–09 theo ủy quyền người dùng


21/10/2026 — MON01–09 integrated TEST: [ma trận/runbook](MONITORING_INTEGRATION.md), [report/receipt](../evidence/monitoring-integrated-20261021/REPORT.md). Base65fa83b; build/testdffa227, runtime51e65dc. Fix shared status ownership/retire và khóa Start/chọn ca khi fresh scope pending; có FAIL→regression PASS. Integrated Mock4PASS + explicit CIM4PASS (cùng4case, không8test mới), app controls/stop-restart/logout/account isolation + terminal/revoke, owned ProcessHandle/FX/WS/PG. Fullverify1305PASS/159SKIP/0lỗi, 674class/resource match4JAR. Giữ historical allProcesses app startup failures chưa rõ exact cause; fixture cuối owned handle, không claim latency production đã sửa. Schema0/PGstop0/readers/owned workers dọn; FULLdefault/cả2experimentOFF, USB0/12/second-display/desktop/LAN0/41NOT_RUN; policy/ADR/gates/human review pending, không MON DONE.

Không approval/chữ ký A/B/C, không tracker/DONE/E1/E2. Git/CI/merge cuối tại PR.


21/10/2026 — Bổ sung producer FX reconnect a12d349:1case mở rộng PASS/0SKIP, production tự refresh capability/scoped heartbeat sau loopback reconnect, transition mới COMMIT/ACK/history; không test-side confirm. JAR build dffa227/main output674files giữ byte-identical, không gán nhãn build mới; ACK11/discarded unconfirmed2/drop0,0schema/PGstop0. Chi tiết tại report integrated; full/common campaign vẫn dùng source đã ghi riêng. Không hardware/policy/human approval.

### 21/10/2026 — Demo desktop monitoring từ main 2a596de
Theo ủy quyền người dùng: thêm -Desktop vào entrypoint demo hiện có, hai production JAR/JVM riêng, TEST DB/API và thao tác desktop do người dùng thực hiện. Signal/alert bật theo process demo, FULL/defaults giữ nguyên. Hướng dẫn: docs/MONITORING_DESKTOP_DEMO.md. Trạng thái: PENDING_USER_ACTION, chưa nghiệm thu desktop/USB/LAN, chưa DONE/gate/policy/approval. Computer Use lỗi khởi tạo kernel assets. Kiểm chứng và cleanup sẽ ghi theo lượt thực tế.

21/10/2026 — Chốt demo desktop từ main2a596de: user thao tác hai production JVM thật, xác nhận proctor thấy Chrome/focus/window tại Timeline đa tín hiệu, Thoát màn thi làm Dừng mờ rồi logout/close. Build/package4aacab4, source Java không đổi; after-exit0 monitoring workers, sequence1810/typed113 giữ nguyên; user đóng cả2trước teardown, apps/server/workload0, schema0/dropDB0/PGstop0/PIDabsent. Raw local ignored, không desktop programmatic/MOCK acceptance. CI4aacab4:1307PASS/157SKIP/0lỗi, Python27PASS; CI final/merge ghi tại PR96. Hướng dẫn docs/MONITORING_DESKTOP_DEMO.md; report/receipt evidence/monitoring-desktop-20261021. FULL default/experiments repository OFF; hardware/LAN/ADR/policy/gates/human approval pending, không MON DONE.

21/10/2026 — QA-05/E1 chiến dịch MỚI từ main a9ab660:12lượt COMPLETE/cleanup sạch, 4poll×3repeats theo lịch xoay, source/JAR/seed1234/alias QA05-LAB-A cố định.360eligible=273observed+84missed+3INCONCLUSIVE,0censored;3OKresource repeats/poll. CPU collector mean250/500/1000/2000ms=17,01/9,02/4,94/2,57% một core; unknown giữ bounds, heap là JVM cha, không RSS.84rawhash bất biến,12rejoin byte-identical/360diff0changed,19analysis files deterministic;4figures PNG/SVG và summary tại evidence/qa05-e1-20261021. Review kỹ thuật Codex, không humanB approval/DONE; giữ kết quả lịch sử riêng. Javafocused89PASS/Pythonbaseline27PASS; exporter tests và CI cuối tại PR. Full/experiments defaults không đổi, không E2/MON/GUI/LAN/USB/release. Raw/JAR/log lớn local ignored, rendererCP313 local sau khi đo để tránh lỗi native CP315beta, không thu bù.

## 23/10/2026 — A: EX-08 STUDY4 real 200-question acceptance

- Thêm module `tools/study4-adapter`: chạy offline trên export do người dùng cung cấp, so sánh public server/client JSON, cấm key đáp án trong client JSON, kiểm phân bố 7 Part, tạo payload V2 và stage media có SHA-256/kích thước ảnh. Raw `STUDY4_6852` và output đều ở vùng Git-ignored, không phát hành lại nội dung bên thứ ba.
- Dữ liệu kết quả: 200 câu `6/25/39/30/30/16/54`, 73 group, 54 audio, 11 ảnh; payload SHA-256 `354408ded85e5c160c1556bd644bb82cab5bb5011757ee0832c4e26104d4424c`.
- Thêm runner `verify-ex08-postgres.ps1` dựng cluster PostgreSQL riêng, chỉ chạy case EX-08, lưu receipt và luôn dọn schema/cluster. Lượt chốt trên source `e6b4320`, PostgreSQL 18.1: `1/1 PASS`, `0 FAIL/ERROR/SKIP`, 0 schema còn lại, stop exit 0.
- Case thật kiểm catalog/create/READY/START, retry không đổi deadline, production client model đủ 7 Part/200 câu, tải và hash đủ 65 media, DTO không lộ key/source, autosave, takeover làm Listening `INTERRUPTED`, stale writer bị từ chối, submit và server score `200/200`.
- Dựng app-image mới và chạy smoke Windows trong đường dẫn Unicode: bundled Java 21.0.12.1, JavaFX Media lifecycle PASS; API/WS riêng của smoke này là MOCK. Human hearing, MSI, clean-machine, LAN và reviewer B cá nhân NOT RUN/không được giả mạo. Chi tiết: [EX-08 closeout](plan-v2/EX08_STUDY4_ACCEPTANCE_20261023.md).

## 24/10/2026 — Codex: test phát hiện USB vật lý theo yêu cầu người dùng

- Không dùng codex-with-chatgpt theo opt-out, không sửa code. Đọc runbook/contract/ghi chú C; đối chiếu source/resource, không thấy baseline che USB. App candidate production chưa gắn nguồn USB.
- Lượt sandbox `usb-ac48d52caf264843` bị chặn TCP local10013 trước Maven; standalone CIM QUERY_FAILED. Giữ raw lỗi, cluster stop0/PID absent; chạy lại ngoài sandbox bằng token Windows STANDARD.
- Người dùng xác nhận USB lưu trữ đang cắm, sau đó xác nhận rút/đợi/cắm lại trong lượt 120s `usb-92ad0baf1e9d4dc4`. Source `acc1f82`, chỉ AGENTS.md untracked trước test. MSFT ban đầu count0, sau cắm lại scan42 count1; chưa rõ nguyên nhân không thấy ban đầu.
- JUnit1PASS/0FAIL/ERROR/SKIP,86scan READY:41count0/45count1; một USB_STORAGE_CONNECTED lúc20:53:54.556761 UTC+7, không lặp44scan sau. Status+USB2COMMIT/ACK/history canonical equality,0drop/pending/unreported; PG18.3/schema0/stop0/PIDabsent/child0/readers+workers stopped.
- [Report/receipt và hash raw](../evidence/usb-hardware-20261024/REPORT.md). Raw ignored giữ nguyên cả FAIL. Chỉ xác nhận0→1 trên thiết bị này; không đo latency, không REMOVED/PRESENT_AT_START/fullH01–H12/GUI/production binding hoặc taskDONE. Runner hardware label cố định được giải thích riêng, không sửa raw.

## 24/10/2026 — USB thật trong ứng dụng trên nhánh mới

- Theo yêu cầu người dùng tạo codex/usb-app-integration-20261024 từ acc1f82, giữ dirty docs/evidence cũ và AGENTS.md. Opt-out codex-with-chatgpt tiếp tục. Review OS riêng trước code, review lifecycle sau code; không chữ ký owner.
- Tách windows-observer, tái dùng nguyên classification/parser/resource; app chỉ chạy nguồn sau capability/scoped heartbeat và role candidate, dùng một run/queue/status/sequence. Thêm pause–fresh-auth–resume, generation guard, lazy source ngoàiFX, non-daemon cleanup giữJVM; fix list handle tăng theo poll bằng prune+counter. Không wire/schema/default FULL changes.
- Focused187PASS trước fix cuối. Test phase mới1342PASS/169SKIP/0FAIL-ERROR/143XML; mvnverify và package retry lỗi server repackage vì JVMcũPID56484 giữdefaultJAR. Khôngstop JVMcũ. Thêm artifact-name cấu hình default khôngđổi, desktop nonce-JAR packagePASS toànreactor skipTests. STUDY4test chưa chạy trongverifybịngắt, không báo fullverifyPASS.
- REALhook1PASS runusb-300be357ef1749f4: provider READY0 → productioncandidate → WS/PG/ACK/history/realproctorScene, reconnect và cleanup. Desktop runusb-c26aa31b588a43f2 haiJVM thật; user xác nhận nhìn thấy cắm/rút trênapp; raw count1→0→1 với CONNECTED21:35:58,REMOVED21:36:09,CONNECTED21:36:19 UTC+7. Ref removal khớp lầncắmđầu. Không đo latency vật lý.
- [Report/receipt](../evidence/usb-app-integration-20261024/REPORT.md), [runbook](USB_APP_INTEGRATION.md). Auto-review từ chối ghi password ra file; đã bỏ password trong notes, dùng accountMOCK công bố chỉ TESTloopback. Source/JAR hash và frozen snapshot giữ raw lỗi/thànhcông; không đọcfile/serialUSB.
- Live demo còn mở tại thời điểm xác nhận; runner dọn khi haiappđóng/hết60phút, chưaclaimcleanupPASS. Launcher saubuild đổi flag cho cả2cửa sổ để khôngphụthuộcthứtựlogin, chỉ flowCANDIDATE tạoobserver; Java/JARđãxem giữnguyên. Không fullH01–12/ADR/retention/PnP/LAN/humanapproval/taskDONE/tracker. Chạm C/B và packagingA, cầnownerreview trước dùngrộng.
## 24/10/2026 — Debug USB trên phiên localhost:8080

- User gửiảnh DEMO-C3-A trốngTimeline. ĐốichiếuJVM: client14708/55112 khôngsignal/USBflags; oldisolateddemo đãđóng. UserchọnkhởiđộnglạiphiênTEST8080: servercũ44636stop, servermới57776signalON, clientsflagsON. GiữDB/oldattempt/deadline.
- NativeCUA xácnhận candidate vẫnportal, khôngphảiexam; demo-c3 tạoattemptkhôngsession/đề. RawListening200 sessionnull/NOT_STARTED/writer1/toànplaybackrỗng làmallOfTimelinefail. Review kỹthuật riêng: narrowdashboardprojection rỗng, giữstrictplaybackvàattemptscope, khôngwire/serverchange.
- PublicclientAPI diagnostic caMOCK hợp lệ: ACTIVEstatusgradingtotal0 nhưngpaper1câu. Sửa previewACTIVE0 thànhunknowncount, khôngpaperfetch/takeover/READY. Source-runner tạm gọipackageprivateloader lỗiIllegalAccessErrordoClassLoader, giữlogvàkhôngsuynghiệpvụtừlỗiđó.
- Test68parser/API/timeline+29preview=97PASS/0SKIP, packageclientPASS; 4launchconfigOFF/ONvalidationPASS vàdiffcheck. Khôngfullverifymới.
- TạoMOCK1câu/ca USB-8080-20261024-511c18 quaAPIproctor trongDBTESThiệntại, khôngSQLreset/oldattemptdelete. Sauđăngnhậplại, nativeUI chọnca/nhấnvàobài, mởTimeline/lọcWINDOWS_USB_STORAGE/cuộnbảng. UserxácnhậnthấyrútvàcắmUSB; historyREAL REMOVED22:25:23→CONNECTED22:25:34UTC+7/count0→1, baseline1trướcrút.
- [Report/receipt](../evidence/usb-8080-fix-20261024/REPORT.md), JAR16815b39…/97XML-testcases vàfrozenAPIraw; secretchỉRAM. Branchuserđangởfeat/usb-app-integration-20261024, giữnguyênmọichangesphiêntrước; opt-outChatGPTgiữ. Skillcomputer-use chỉđiềukhiểnappJavaFXđãlogin, người dùngtựđăngnhập.
- Server8080vàhaiapp đểmở, DBTESTgiữdữliệu; khôngclaimisolatedcleanup/fullH01–12/LAN/installer/policy/DONE. ChạmB vàscriptsA; ownerreviewvẫnchờ.

## 25/10/2026 — Tổng quan và Chi tiết thí sinh có giám sát mở rộng

- Theo thiết kế người dùng chốt: giữ Process, thêm USB/focus/window, hai mốc client/server, last USB change, deep link đúng attempt/source; Tổng quan có cụm chỉ báo mở đúng drawer. Opt-out codex-with-chatgpt tiếp tục, không yêu cầu người dùng thao tác/đăng nhập lại trong lượt tự động.
- Reader raw typed history theo current collector và fresh scope, fixed-through/3×100page/lượt/2historyrequest/100lượt/12s; constant-memory aggregate giữ USB ngoài500rows. Fix globalstale không lây từ OFFLINE và fairness nonfocused queue; uncancellable oldfuture giữglobalpermit. Scope/run/switch/close clear, gap/partial/unknown không suy USB0. Không wire/DB/nguồn thu mới cho riêng consumer.
- Fullverify1396PASS/172SKIP/0FAIL-ERROR, GUI bật; focused33PASS overlap và PendingLimit1PASS trongfull. PG MOCK1method+REALCIM1method PASS; REAL local0/8ACK không hotplug mới. Mỗiownedcluster cleanup0schema/stop0/PIDabsent, DBTESTcũ giữ. Giữ lỗi compilePowerShellargument và hai GUIlayoutfixture rồi sửa/rerun, không che lỗi.
- MavenShade giữ clientall mặc định; bỏ property thin-client không cần thiết, packagefinalPASS. 13JARentries byte-identical với output test, SHA9b929c50…; helper xuất summaryNode thật để QA toàn bộ card không bị viewport cắt. Ảnh MOCK ghi rõ provenance. [Report](../evidence/proctor-signals-20261024/REPORT.md), [runbook](PROCTOR_SIGNAL_SUMMARY.md).
- Bàn giao cả sharedUSB/candidate/Listeninglegacy/ACTIVEpreviewfix từ các lượt trước làm dependency của nhánh. KhôngAGENTS/secret/rawtarget/media/trackerDONE; B/C vẫn cần review cá nhân, flagsOFF/FULL, policy/ADR/H01–H12/LAN/installer chưa nghiệm thu mới. Người dùng cho phép commit+push nhánh hiện tại, cấm PR/merge; GitHubworkflow khôngtriggerfeat nên khôngclaimCI.


## 25/10/2026 — Sửa luồng người dùng bắt USB cùng tiến trình

- User muốn bỏ cột USB/focus/window riêng và bắt chung với process; ảnh server8080 có DEMO-C3-A process đang chạy, lượt có bài đã hết giờ. Kiểm JVM flagsON cả2client/server, nativeCIMSTANDARD1USB; Computer Use đọc portal candidate có FULL ACK nhưng chưa vào được bài. Rootcause hook typed chỉsauExamEntry, manualStart khôngtạo nguồn. Opt-out codex-with-chatgpt tiếp tục.
- Gắn startSession vào cùng đường freshCandidateScope/process; dùngmộtcollector/run/sequence. Vào bài giữcoordinator, chỉbậtactivitysauentry. ManualStop closes typed trướcprocess, chờcollector+USBworker ngoàiFXtrướcStart; logout/revoke/callbackguard giữ. Không wire/DB/OSAPI/queryCIM changes, defaultOFF/FULL. UIbỏcột riêngvàkhốiriêng, gomprocess+3nguồn ở Đang quan sát trên máy thí sinh; giờclient/server/unknown/partial/Timeline giữ.
- Focused50PASS; fullverify1420PASS/154SKIP/0lỗi/1574test/149suite,7modulebuildPASS; PGREAL2methodPASS, USBbaseline1/9ACKdirecthook, manualStartchưaexamcóUSB/focus/window, stopcleanup/restartnewrun/entryreuse/activitygatePASS. OwnedPG0schema/stop0/PIDabsent, khôngđổiDBTEST8080/oldattempt/deadline.
- Giữ2PGfailedfixtures (loginplaceholdercũ, snapshotnodevừaunmount) vàfullGUI2layoutreaderrors; sửastableID/waitlayout/retryattached rồiPASS. Helperexportcuốichụpnguyênkhốichung, PGREAL2methodrerunPASS; productiongiữnguyênsaufullverify. JARc35b931a…/10entriesbyteequal, [report](../evidence/unified-monitoring-20261025/REPORT.md), [hướngdẫn](PROCTOR_SIGNAL_SUMMARY.md).
- CUA chỉđọchaiappđãlogin, khôngauth/restartJVMngườidùng; JARmới cầnđóngmởclientcũ. Processmetadata/name trongảnhPG làMOCKownedfixture, USBREAL/Stagepropertiesautomated; khôngclaimhotplug/AltTabvậtlýmới/H01–H12/LAN/installer/policy/ownerapproval. B+C thayđổiđiểmgắnlifecycle, reviewhumanvẫncòn; khôngtrackerDONE. GiữAGENTS.mdngoàiGit, commit/pushcùngnhánh theoủyquyềntrước, khôngPR/merge/CIclaim.

Làm rõ provenance process của ảnh trên: nguồn ProcessHandle thật giới hạn đúng PID workload do test sở hữu; tên chrome.exe thuộc workload fixture, không phải browser người dùng. Nhãn MOCK áp dụng account/đề và ngữ cảnh workload kiểm policy; USB baseline1 là CIM thật.

## 25/10/2026 — USB/focus/cửa sổ trong Cảnh báo và Nhật ký chung

RoomFeed trước chỉ đọc process/interruption/gap, nên các nguồn typed không có trong Cảnh báo và hai khối gần đây. Đã đọc typed history commit, cập nhật tăng dần theo watermark reader scoped hiện có, chống trùng/giữ fixed-through/tối đa500dòng liên quan/lượt và thông báo giới hạn. Thêm filter USB/Focus/Cửa sổ, chi tiết event gốc; baseline focus/window và READY chỉ vào Nhật ký, thay đổi vào các quan sát cần xem xét. Hai khối gần đây chỉ hiện3mục mới nhất, không suy USB0/vi phạm từ thiếu sự kiện. Không đổi wire/DB/OSAPI/defaultOFF/FULL.

Fullverify1435PASS/154SKIP/0lỗi (1589test/152suite), GUI bật; bộ mới15PASS, PG REAL2PASS baselineCIM0, window Stage API tự cập nhật danh sách và filter. Owned PG cleanup0schema/stop0/PIDabsent; không đổi DBTEST8080. Giữ testCompile fixture sai constant và2PG kỳ vọngUSBalert khi count0; sửa fixture, không che lỗi. JAR72e124c4…/538entrymatch. [Report](../evidence/proctor-feed-20261025/REPORT.md). B/C cần owner review, không hardwarehotplug/H01–H12/LAN/policy/trackerDONE/CIclaim; pushnhánh hiện tại, khôngPR/merge, giữ opt-outChatGPT.

## 25/10/2026 — Lọc danh sách chung theo ba nhóm người dùng chốt

Chỉ giữ process, USB CONNECTED/REMOVED và thay đổi focus/window; loại READY/INITIAL/status/gap typed/PRESENT_AT_START/baseline/dialog nội bộ khỏi Cảnh báo/Nhật ký/hai khối gần đây và số đếm. Raw typed history/Timeline/trạng thái nguồn trong Chi tiết giữ nguyên; luồng kết nối/gap legacy không đổi. Không wire/DB/OSAPI/policy/defaultOFF/FULL changes.

Client package+GUI40PASS/0lỗi/skip; PG REAL2PASS count0/Stagewindow tự cập nhật,0schema/stop0/PIDabsent. Cursor qua bản ghi ẩn và USB cắm/rút MOCK đã kiểm; không hotplug vật lý/fullverify mới. JARdc91c420…/538entrymatch. [Report](../evidence/proctor-actions-only-20261025/REPORT.md). B/C còn review cá nhân, không trackerDONE/policy/H01–H12/LAN/installer/CIclaim; opt-outChatGPT giữ, pushnhánh hiện tại khôngPR/merge.


## 25/10/2026 — Tích hợp bản phát hành cuối (nhánh `integrate/final-toeic-release`) — TẠM DỪNG

Base `origin/main` `acc1f82`. Đã merge ACCOUNT_PIN `ff6aacf` (`f0fd664`), ẩn lối vào legacy sau cờ `toeic.ui.legacy`/`TOEIC_UI_LEGACY` (`cbbd251`), launcher portable + tài liệu phát hành (`dd00e1a`), revert fix QA01 bị ACCOUNT_PIN thay thế (`5c7a7b9`), merge nhánh USB của C tới `4b52cdc` (`09e0f3f`), bật USB/tín hiệu trong launcher phát hành và bổ sung kiểm USB vào E2E ACCOUNT_PIN (`17f94c6`), merge `9299075` (`161c586`), launcher báo Server READY + smoke import đề thật (`05822d8`). Backup: tag `backup/account-pin-ff6aacf-20261025`, `backup/main-acc1f82-20261025`, ref `refs/backup/account-pin-wip-20261025`, bản sao file chưa track tại `E:\Toeic-Workspace-Backups\final-integration-20261025`.

Kết quả thật (PostgreSQL 18.1 cô lập, Windows 11, JDK 21.0.8): full `e188153` 1662 PASS/48 SKIP/0 lỗi; full `-AllGui` `dd00e1a` 1 FAIL (fix QA01 bị thay thế → revert); full `-AllGui` `5c7a7b9` 2 ERROR (CandidateManagementGuiPostgresTest do ẩn tab legacy → opt-in); targeted `17f94c6` 91/91 PASS (E2E ACCOUNT_PIN 3/3 có USB CIM thật); runner USB của C trên `17f94c6`: Mock tích hợp Timeline 4/4, Mock UsbIntegratedDemo 4/4, Real CIM 1/1, Real CIM+Timeline 2/2; full `-AllGui` `17f94c6` 1756 PASS/23 SKIP/0 lỗi. Full trên `05822d8` bị dừng giữa chừng theo yêu cầu người dùng, **không có kết quả**. STUDY4_6854 chuyển bằng adapter thành công (200 câu, 54 audio, 11 ảnh); 6853 bị từ chối vì ảnh WebP mang đuôi .jpg; 6852 thiếu client_exam.json.

Chưa push/PR/merge. USB cắm/rút vật lý chỉ có bằng chứng của C trên nhánh USB (`evidence/usb-hardware-20261024`, `evidence/usb-8080-fix-20261024`), chưa chạy trên commit tích hợp; LAN hai máy, nghe thật NOT TESTED.

### Ghi chú làm dở

Đang chờ Công Lâm hoàn tất nhánh `feat/usb-app-integration-20261024`. Khi tiếp tục: fetch, review commit mới của C sau `9299075`, merge có chọn lọc; chạy lại full `-AllGui` trên commit code cuối, runner USB của C (Mock tích hợp/Mock/Real/Real+Timeline), EX-08 với STUDY4_6854, đóng gói portable, `verify-phase4-portable.ps1 -UnicodeCopy`, `verify-release-server.ps1` (kèm `-UpgradeFromServerJar` JAR server main V18 và `-ImportJson` STUDY4_6854); viết `docs/release/RELEASE-REPORT.md` rồi push/PR/merge/release. PR #107 (QA01) giữ riêng.
