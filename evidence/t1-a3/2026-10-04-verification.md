# T1-A3 — bằng chứng kiểm chứng 04/10/2026

Người chạy: Codex Agent; review C **NOT RUN**, không thay bằng review Agent.

- Base main: `6b9572ed3119c3e19fe6cf73f9ea0f73c1dfac9d` (PR #4, đã fetch/pull trước khi tạo nhánh).
- Nhánh: `feat/t1-a3-monitoring-events`.
- Source/test/script: `88ef95a2d96db202edf39e3ed5c72f026bfdb7fc`; các lượt dưới chạy trên nội dung working tree sau đó commit SHA này. Commit docs không đổi code.
- Windows11 amd64, Temurin JDK21.0.10+7, Maven3.9.15, SpringBoot4.1.1, PostgreSQL18.6 đang chạy local Docker, JDBC thật. Không dùng H2.
- Smoke server riêng bind127.0.0.1/port ngẫu nhiên; Java HttpClient/WebSocket headless thật. Không JavaFX/desktop manual, LAN second-machine hoặc benchmark E1/E2.
- Không thêm thư viện; JDBC/Flyway/Spring/Java WebSocket đã có trên main.

## Build và unit/network fixture

| Lệnh | Thực tế | Log |
|---|---|---|
| `mvn test` | PASS, 200/200, 5/5 module, 0 failures/errors/skipped; xong18:40:34 UTC+7 | [mvn-test.txt](mvn-test.txt) |
| `mvn package` | PASS, 200/200, 5/5 module; xong18:41:15 UTC+7 | [mvn-package.txt](mvn-package.txt) |
| JAR inspection | PASS: V2/production classes có trong server JAR; Test/Smoke classes không đóng vào JAR | `jar tf` đã kiểm trong phiên |

Lệnh thực tế thêm `-o -B -ntp` và `-Dmaven.repo.local=<maven-cache>` để dùng cache đã có, không tải thư viện. Cache ngoài workspace cần quyền Maven. Paths cá nhân trong log đã thay `<workspace>`/`<user-home>`; chuyển UTF-16 PowerShell sang UTF-8, chuẩn hóa newline/bỏ trailing whitespace; không sửa kết quả test.

Tổng: server76 + client123 + monitoring-spike1 =200. A3 thêm **25 lượt** so với175 baseline: ProcessEventTest24; concurrent-send tăng1 (ACK/ACK và ACK/warning). AuthenticatedNetworkTest30 dùng session/scope/JDBC/event service MOCK và mạng thật; không lấy các fixture này làm bằng chứng persistence production. AuthServiceTest2, SessionAuthenticationTest18; constructor/scope test cũ đã cập nhật theo provider JDBC. Mockito agent/CDS và shade warnings vẫn build PASS.

## PostgreSQL + HTTP + WebSocket thật

Chạy `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a3.ps1` sau package. Lượt cuối PASS, bắt đầu18:35:48 UTC+7. [postgres-smoke.txt](postgres-smoke.txt) là stdout thật, không token/password.

Harness `MonitoringPostgresSmoke` chỉ được expose qua loader directory riêng, không đưa unit-test MOCK configurations vào component scan. Dùng production Spring application/services, JDBC scope, Flyway migrations và raw Java HttpClient/WebSocket.

TEST data ở hai schema UUID riêng trên PostgreSQL thật. Mỗi schema được CREATE bởi run này trước khi thêm vào danh sách cleanup; chỉ DROP schema do run sở hữu. Database/volume/public dev tables/server người dùng không reset hoặc dừng. Mọi tài khoản/lượt thi/event ghi nhãn TEST; password fixture chỉ cho TEST schema, không credential production. Không seed assignment trong production migration/boot.

| Kiểm tra | Kết quả thực tế |
|---|---|
| Schema mới V1+V2, schema V1 được app production upgrade V2 | PASS |
| Owner/assignment/event FK và unique `(attempt_id,event_id)` | PASS, PostgreSQL reject SQLSTATE23503/23505 |
| Production scope bean là JdbcAttemptScopeStore | PASS |
| Candidate sở hữu ACTIVE; proctor assigned ACTIVE | PASS |
| Candidate foreign/CLOSED/unknown; proctor unassigned/CLOSED/unknown | PASS, FORBIDDEN; không có event mới |
| Login và `/auth/me` scope own/assigned, không leak foreign/CLOSED | PASS, bốn TEST users có đúng scope |
| Timeline cần Bearer, role PROCTOR, scope thật | PASS: 401/403 hoặc200 tương ứng |
| Event hợp lệ → commit row1 → candidate ACK → assigned warning | PASS; DB đọc từ connection khác sau ACK thấy row |
| Same eventId/payload gửi hai lần | PASS: ACK2, row1, warning1 |
| Same eventId đổi processName | PASS: CONFLICT, row cũ giữ nguyên, không warning/ACK mới |
| Malformed JSON, invalid field, missing attempt, path processName | PASS: INVALID_INPUT; client khác tiếp tục heartbeat ACK |
| Proctor không được gửi event candidate | PASS: FORBIDDEN |
| Lỗi PostgreSQL lúc COMMIT | PASS: deferred constraint trigger TEST chạy sau INSERT/service body; transaction rollback, RETRYABLE_SERVER_ERROR retryable=true, row0, không success ACK/warning |
| StartInstant null/UNREADABLE và retry | PASS: JDBC nhận null, duplicate ACK không warning |
| Concurrent retry hai socket | PASS: CyclicBarrier hai workers; ACK2, row1, warning1 |
| Candidate B/proctor B không nhận event A | PASS: bốn client và bounded negative receive assertions |
| Giám thị offline, login/reconnect đọc timeline | PASS: event vẫn commit/ACK; REST có đủ4 rows tại thời điểm kiểm, không duplicate |
| Timeline ordering | PASS: REST order khớp `received_at,id` DB, không theo observedAt |
| Assignment bị xóa khi socket còn mở | PASS: không push; timeline403 |
| Token proctor bị revoke khi socket còn mở | PASS: không push, close1008 |
| Token candidate revoke | PASS: UNAUTHORIZED/close1008, không insert |
| Cleanup TEST schemas | PASS |

Absence assertions dùng receive timeout200ms sau các positive ACK/warning, không coi đây là số đo hiệu năng. Concurrent raw writer kiểm bằng latch kiểm soát overlap, không dựa sleep: max1 writer cho ACK/ACK và ACK/warning. Push best effort; timeline là đường phục hồi, chưa có durable push queue hoặc bảo đảm delivery.

## NOT RUN và giới hạn

- **DB-off smoke: NOT RUN** — PostgreSQL dùng chung; không dừng DB của người dùng. Lỗi COMMIT thật đã kiểm an toàn bằng deferred trigger trong schema TEST, đủ bằng chứng DB failure không success ACK. Không gọi trigger test là DB-off PASS.
- **Human C review: NOT RUN**; task cho phép merge nếu technical acceptance/mergeability PASS mà human review chưa chạy. Không tự gửi tin nhắn/yêu cầu review tới người khác.
- **GUI/LAN: NOT RUN**; B3 dashboard và C3 collector→event/queue/retry chưa cài, nên không gọi flow desktop “mở Notepad → dashboard” là PASS.
- Full/delta/presence/A4/exam flow chưa cài. 27 case chính thức giữ trạng thái cũ; không gọi AT05/MT02 end-to-end PASS từ test thành phần A3.
- Assignment schema tối thiểu; chưa có API tạo đề/ca/lượt thi. Không tự tạo attempt khi boot; DB dev không có assignment thì login scope rỗng là đúng.
- Timeline chưa phân trang. Timestamp payload chuẩn hóa UTC/cắt microseconds theo PostgreSQL; khác dưới microsecond được xem tương đương. Nội dung còn lại so record đã parse, không JSON raw/envelope.

## Security và documents

[security-scan.txt](security-scan.txt): PASS — actual local DB/seed secrets không xuất hiện trong changes/evidence; không raw bearer, personal absolute paths hoặc `.env`/`task.txt` trong Git. Full-path rejection strings/password TEST chỉ nằm trong fixture âm được ghi nhãn; production lưu filename, không OS username/command line. Warning/timeline không có credential. Smoke không in response login hoặc exception chứa credential.

PROTOCOL/TIEN_DO/NHAT_KY/KIEM_THU/VAI_A cập nhật. QUYET_DINH giữ nguyên: không thêm quyết định kiến trúc ngoài lựa chọn đã nêu trong task/QD-05. TRACKER.json/nguôn/01–03/client/protocol DTO/collector giữ nguyên.

## Handoff

C3: PROCESS_OBSERVED v0 + attemptId ACTIVE bắt buộc; payload eventId,collectorSessionId,policyVersion,pid,processName,startInstant nullable,metadataQuality,observedAt. Mapping requestId→event; chỉ xóa queue sau ACK đúng requestId/acknowledgedType. Retry giữ event/payload; duplicate ACK lại; CONFLICT dừng retry; RETRYABLE_SERVER_ERROR retry có giới hạn. Không coi transport.send completion là commit.

B3: `GET /api/v1/monitoring/attempts/{attemptId}/events`, response protocolVersion/traceId/attemptId/events. MONITOR_WARNING payload cùng item timeline: eventId,attemptId,processName,metadataQuality,policyVersion,observedAt,receivedAt; envelope attemptId. Dedupe `(attemptId,eventId)`. Timeline→render→live WS, đọc timeline đối chiếu sau handshake hoặc buffer WS trước để bù race reconnect. UI “Quan sát thấy …”. Chi tiết JSON trong [PROTOCOL](../../docs/PROTOCOL.md).

## Lỗi đã gặp và sửa trước lượt PASS

- Maven cache ngoài sandbox bị chặn: chạy với quyền đọc cache; không giả lần lỗi là build PASS.
- Tests cũ gọi deny-all constructor và fixture không có JdbcClient: cập nhật theo provider JDBC, thêm bean MOCK rõ nhãn cho A2 network fixture.
- Harness gọi TokenHash package-private: fixture revoke theo TEST user ID, không mở API production vì test.
- Timestamp JDBC gắn TIMESTAMP_WITH_TIMEZONE không phù hợp `java.sql.Timestamp`: dùng Types.TIMESTAMP/setTimestamp với cột PostgreSQL TIMESTAMPTZ, xử lý null được smoke thật kiểm.
- Fixture `eventId="INVALID"` thực ra hợp lệ theo ID regex: sửa dữ liệu âm thành ID có khoảng trắng, không sửa production validator để ép test PASS.

Các lượt lỗi không được cộng vào evidence PASS. Final source/test và smoke PASS nêu ở trên; docs commit sau đó không đổi implementation. Git/PR/merge SHAs được trả ở final report; không xóa nhánh sau merge.
