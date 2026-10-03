# T1-A2 verification — 04/10/2026

Vai A / Codex Agent. Code-complete; không đổi TRACKER.json. Git push/PR/merge cuối được xác nhận trong report trả người dùng; không sửa/merge nhánh B2, không tiếp tục A3.

## Source và môi trường

- Base main sau fetch/pull: `34a78350d462bd472917171dbef77d70aa8e6b88` (merge PR #1).
- Branch: `feat/t1-a2-authenticated-websocket`.
- Implementation: `3c570af`; test/harness và source được kiểm cuối: `7ac62c4d0bc5120ba10f887efbe5478a693d6385`.
- Commit docs tiếp theo chỉ README/docs/evidence; không đổi source đã kiểm.
- Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, Docker Desktop 29.4.3; WSL gọi Windows tools.
- PostgreSQL thật: 18.6 (Debian 18.6-1.pgdg13+2), image postgres:18 trong compose hiện có. `docker compose up -d --wait` healthy, không reset/xóa volume.

## Authentication/authorization

Login token raw chỉ trả JSON body. AuthService và SessionAuthenticationService dùng chung TokenHash SHA-256; JdbcLoginSessionStore JOIN login_sessions/user_accounts lấy expiry/revoked/enabled/current role. REST nhận AuthenticatedUser/principal; WS attributes chỉ user + hash, không raw header/token. Mỗi WS message revalidate DB trước authz/parsing/business.

BearerAuthenticationFilter bảo vệ `/api/**`, ngoại trừ POST `/api/v1/auth/login` public. GET `/api/v1/auth/me` chứng minh context sau auth, trả identity và scope rỗng, không token. Credential lỗi 401 UNAUTHORIZED; quyền sai 403 FORBIDDEN. WS header lỗi không upgrade; session active bị revoked/expired/disabled → ERROR UNAUTHORIZED rồi close1008.

AuthorizationService.requireRole/requireAttempt là helper chung. Role lấy từ DB, không tin payload. Scope provider production deny unknown/tất cả attempt chưa được cấp do schema attempt chưa cài. Không thêm ca thi/attempt giả. Provider riêng tương lai có thể dùng bean @Primary; assignment candidate/proctor trong test là **MOCK**, không phải scope production.

| Kiểm tra | Trạng thái | Mức chứng minh |
|---|---|---|
| Valid token | PASS | Unit, real REST/WS và PostgreSQL production smoke |
| Invalid/missing/malformed token | PASS | Real HTTP/WS handshake; PostgreSQL thiếu/sai token |
| Expired/exact expiry | PASS | Unit boundary, real network MOCK store; expired còn qua PostgreSQL |
| Revoked | PASS | Unit/network + PostgreSQL, kể cả WS đang mở |
| Disabled user | PASS | Unit + real REST/WS với MOCK DB; không sửa enabled của tài khoản production |
| Role đúng/sai → allowed/403 | PASS | Helper + real REST/WS với MOCK DB identity |
| Own attempt | PASS — MOCK scope | Provider chỉ trong test, không giả DB assignment đã cài |
| Foreign attempt | PASS | MOCK own/foreign network + production deny unknown PG smoke |
| Check before business | PASS | Authentication trước message; role/scope trước ACK/unsupported handling; mock REST foreign handler AssertionError không được chạy |

## WebSocket

Raw Spring WebSocket, endpoint `/ws/v1/realtime`, không STOMP/SockJS/Socket.IO/broker. RealtimeHandshakeInterceptor đọc đúng `Authorization: Bearer <token>`, không nhận query, chỉ một header. QD-03 chốt sau khi Java21 WebSocket.Builder nói chuyện thực sự với Spring/Tomcat.

HEARTBEAT v0 unscoped (`attemptId` null/thiếu) cho phép phiên authenticated thử transport trong khi login chưa có attempt. Scoped heartbeat bắt buộc scope guard; không có presence/UNKNOWN/lastSeen hoặc collector. Payload `sentAt` bắt buộc ISO Instant, collectorSessionId optional. ACK requestId/traceId/attemptId tương quan, payload ACCEPTED/HEARTBEAT. ACK này không chứng minh event commit. PROCESS_OBSERVED/state/delta chưa cài; không phát success ACK cho event.

Strict JSON validation giữ listener hoạt động sau malformed/unknown type/thiếu field; ERROR payload cố định, không raw input/cause/stack trace. ConcurrentWebSocketSessionDecorator bao toàn bộ outbound send cho từng session, timeout/buffer configurable.

| Kiểm tra | Kết quả |
|---|---|
| Missing/invalid/malformed Bearer handshake | PASS — HTTP401, không upgrade |
| Expired/revoked/disabled handshake | PASS — HTTP401 |
| Valid Bearer handshake | PASS — Java21/Spring network + PostgreSQL thật |
| Token query dù header valid | PASS — bị từ chối |
| HEARTBEAT → ACK/correlation | PASS — real network và PG production |
| Fragmented Java WS heartbeat | PASS — Spring nhận complete message, ACK |
| Malformed JSON/unknown type/payload/time sai | PASS — ERROR INVALID_INPUT, heartbeat sau vẫn ACK |
| Foreign attempt | PASS — FORBIDDEN, không business ACK |
| Unsupported PROCESS_OBSERVED | PASS — không ACK thành công, role/scope kiểm trước |
| Multi-client isolation | PASS — client lỗi không làm client thứ hai mất ACK |
| Concurrent send | PASS — MOCK raw session, hai caller/latch ép overlap, max 1 raw writer |
| Revocation trên active WS | PASS — ERROR + close1008, cả MOCK network và PG thật |

## Verification đã chạy thật

| Lệnh | Kết quả | Kết thúc (UTC+7) | Evidence |
|---|---|---|---|
| mvn test | PASS, 5/5 module, 91/91; 0 failure/error/skipped | 04/10 00:52:07 | [log](2026-10-04-mvn-test.txt) |
| mvn package | PASS, 5/5 module, 91/91, fat JAR client/server JAR | 04/10 00:53:30 | [log](2026-10-04-mvn-package.txt) |
| PostgreSQL smoke script | PASS, app production + JDBC/Flyway + real Java WS | Lượt cuối sau package 7ac62c4 | [log](2026-10-04-postgres-smoke.txt) |

5 module = root aggregator + protocol + server + client + monitoring-spike. Server 51 (AuthServiceTest 2, SessionAuthenticationTest 18, AuthenticatedNetworkTest 30, ConcurrentRealtimeSendTest 1), client 39, monitoring 1. **49 tests mới** so với baseline 42. Smoke executable riêng không được cộng giả vào JUnit count. Không dùng 70/70 B2 cũ hay XML report của test không còn source.

AuthenticatedNetworkTest chạy network Spring/Tomcat thật, session/scope stores MOCK ghi nhãn rõ; không H2. PostgresWebSocketSmoke chạy app production với JDBC thật, không MOCK provider. Script expose **chỉ harness class** vào loader path, không toàn bộ test @Configuration/controller; production server JAR được kiểm không chứa test fixtures, client JAR không chứa class B2 cũ.

PostgreSQL flow: login candidate → stored token hash → GET me → reject missing/invalid handshake → WS Authorization header → HEARTBEAT/ACK → unknown scope FORBIDDEN → malformed JSON ERROR rồi heartbeat vẫn ACK → UPDATE session do smoke tạo thành revoked, kiểm active WS/REST/handshake reject → login session mới, expired DB session bị REST/WS reject. Cleanup chỉ DELETE các login session do chính smoke tạo; không sửa tài khoản/password hoặc reset DB.

Warnings: Mockito/ByteBuddy dynamic agent và JVM CDS; Maven Shade module-info/strong encapsulation và MANIFEST/module-info trùng. Build PASS. Optional `mvn clean` FAIL vì packaged ToeicMonitor.exe bị Windows khóa; không kill process người dùng. Các class source được compile lại, package JAR đã kiểm không có stale B2/MOCK classes; mvn test/package PASS. Old B2 XML report được loại khi đếm source hiện có.

Các lượt lỗi trước đã sửa: duplicate test scope bean → @Primary; PowerShell Java không có PATH → JavaHome parameter; production smoke isolate classpath harness. Chỉ log lượt cuối PASS được dùng làm evidence. GUI/LAN và B2 integration NOT RUN; không suy ra PASS từ server test. C review chưa diễn ra, không ghi approval thay C.

## Security và documentation

- No token/password log: OutputCapture real network test đưa credential vào malformed payload, kiểm log/ERROR không chứa chúng bằng boolean assertions; không in secret trong failure output.
- Không raw token DB/WS attributes/toString, không token query, không log raw exception của auth/JSON. API unexpected logger chỉ traceId + exception class, không dump request/cause.
- [Secret scan](2026-10-04-security-scan.txt): diff/current changed files/evidence được kiểm không `.env`, private machine paths hoặc credential thật; MOCK/dev seed fixtures được ghi nhãn. TRACKER/nguôn/client/protocol source không đổi.
- PROTOCOL/QD-03 đã chốt; NHAT_KY append; TIEN_DO/KIEM_THU/VAI_A/README ghi kết quả thật và handoff. Không đánh PASS toàn bộ AT02 vì import/start thật chưa tồn tại; không thay trạng thái 27 cases bằng fixture.

## Handoff B2

- URL `ws://<server>:<port>/ws/v1/realtime`, port mặc định8080 (TLS deployment dùng wss).
- Header `Authorization: Bearer <token>` cho REST/WS; reconnect auth lại; handshake401 là lỗi auth, 503/network là lỗi có thể backoff giới hạn.
- Cho phép heartbeat/ACK attemptId null khi scope login rỗng; sentAt bắt buộc, collectorSessionId optional; không tạo attempt giả.
- Cài default production ConnectionOpener của B bằng WebSocket.Builder.header/buildAsync, đóng HttpClient/executor trong close.
- Adapter B hiện chỉ nhận event ACK: cần thêm heartbeat ACK và ERROR payload, re-auth/1008 UI lock; future write không phải ACK/commit.
- Scope assignment thật, event persistence/queue/presence chờ task sau. Không sửa branch B2 trong A2.

## Kết luận

T1-A2 code-complete/DONE về phạm vi task; TRACKER changed NO. User task.txt cho phép merge khi test/package/auth/WS/heartbeat/scope/QD/docs/secret scan PASS và PR mergeable, không blocker thật. Kết quả merge/commit được kiểm trong report cuối. Không chuyển A3 hay B2.
