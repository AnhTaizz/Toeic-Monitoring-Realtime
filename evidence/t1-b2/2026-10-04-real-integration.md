# T1-B2 — real integration 04/10/2026

Người chạy: Codex Agent. Windows 11 x64 / WSL terminal, Oracle JDK 21.0.8, Maven 3.9.11, Docker Desktop 29.4.3, PostgreSQL 18.6. Đây là headless adapter/network verification; GUI manual và LAN máy thứ hai NOT RUN. Không ghi duyệt thay reviewer A.

## Git và phạm vi

- Original B2 HEAD: `edd50cf820bb6ed805ab9b7381bb6807811170a8`, đã push trước phiên này.
- Fetch xác nhận main mới nhất `f62c732191581a308b2cc4dbef926c3c1b45e14b` chứa PR #2/A2.
- Checkout B2, merge origin/main bằng `57f2a9c1d6dd5e2c771e7f917a8edb6d6f8dd57a`; không rebase/reset/force push.
- Conflict README/TIEN_DO/NHAT_KY/KIEM_THU giải theo từng đoạn: giữ source B2, giữ cả lịch sử MOCK B2 và kết quả A2, dùng contract QD-03 mới cho trạng thái hiện tại.
- Source implementation `9064b90`; test/harness `6d5c7f4335743b42e74b7232b2e219a89be2e9ca`. Commit tiếp theo chỉ docs/evidence; kết quả PR/merge cuối ở final report.
- Server/protocol source, QD-03, TRACKER.json và nguon/ giữ nguyên so với main A2; task.txt untracked, không commit.

## Contract đã consume

`ws://<server>:8080/ws/v1/realtime`, HTTPS → WSS; REST/handshake dùng `Authorization: Bearer <token>`. Opener parse URI, từ chối user-info/query/fragment/path; không AUTH message/subprotocol/token URL. Mỗi open/reconnect xây fresh handshake. Token chỉ trong memory; opener close xóa reference và shutdownNow HttpClient, không tạo executor HTTP thừa.

Session cho phép heartbeat attemptId/collectorSessionId null. JSON có thể omit các trường null; A2 cho phép thiếu/null. Không invent attempt, không bật collector. PROCESS_OBSERVED vẫn cần scope/collector theo validation fixture và CHƯA TÍCH HỢP SERVER THẬT.

ACK HEARTBEAT khớp requestId/type/attemptId/traceId đang chờ; socket write completion không phải ACK. ERROR nhận payload trực tiếp {code,message,retryable}, dùng thông báo cố định thay raw server message. FORBIDDEN/INVALID_INPUT không tự replay; RETRYABLE_SERVER_ERROR chuyển subscriber. 401/UNAUTHORIZED/1008 → FAILED, khóa model UI, hủy heartbeat/retry, bỏ token; phải login/tạo adapter mới. 503/network lỗi dùng bounded backoff.

JavaFX capture URL của lần login, nối candidate/proctor transport; callback qua Platform.runLater. Logout/stop gỡ subscriptions, close socket/worker/HTTP, bỏ adapter reference. Controls cần mạng chỉ mở khi CONNECTED; chưa có màn thi/dashboard B3.

## Commands và automated results

```powershell
git fetch origin
git checkout feat/t1-b2-network-heartbeat-ui
git merge origin/main
mvn test # baseline sau merge
mvn test # source cuối
mvn package
docker compose up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-b2.ps1 -JavaHome <JDK21>
```

| Run | Source | Modules | Tests/failures/errors/skipped | Kết quả |
|---|---|---|---|---|
| Baseline sau merge | 57f2a9c | 5/5 | 119/0/0/0 | PASS |
| Final mvn test, 09:48:27 UTC+7 | 6d5c7f4 | 5/5 | 142/0/0/0 | PASS |
| Final mvn package, 09:50:06 UTC+7 | 6d5c7f4 | 5/5 | 142/0/0/0 | PASS |

Server 51; client 90 (LoginApiClient 39, RealtimeClient 36, AuthenticatedWebSocketOpener 15); monitoring 1. Giữ 28 lượt B2 cũ, đổi test default-blocked thành reject credential URL sau contract mới; thêm 23 lượt phiên này. So với main A2, tổng tăng 51. Không dùng lại tổng 70/91 hoặc cộng suite XML không có source. RealtimePostgresSmoke là executable riêng, không cộng vào JUnit count.

Warnings: Mockito/ByteBuddy dynamic agent/CDS và Maven Shade module-info/MANIFEST overlap; không failure/error/skipped. Không chạy clean bổ sung hoặc kill packaged EXE người dùng trong phiên B2. JAR client chứa production opener; số class Test/MOCK/harness trong JAR = 0.

Logs: `2026-10-04-merged-baseline-test.txt`, `2026-10-04-real-mvn-test.txt`, `2026-10-04-real-mvn-package.txt`. Log dẫn xuất chuẩn hóa CRLF/trailing whitespace và thay workspace/user-home bằng placeholder; không sửa kết quả đo.

## Real Spring/PostgreSQL smoke

| Case | Result | Evidence/giới hạn |
|---|---|---|
| PostgreSQL + production JDBC/Flyway | PASS | Không H2/MOCK session provider, không reset volume |
| Candidate REST login | PASS | Dev-only MOCK seed account, token thực trong memory |
| Chính B RealtimeClient connect/heartbeat/ACK | PASS | Scope rỗng, không collectorSessionId/attempt giả; ACK correlation được adapter validate |
| Server-off → RECONNECTING và lock model | PASS | Kill chỉ Java server process do harness tạo; không phải GUI manual |
| Server restart trong retry window → fresh auth handshake/ACK | PASS | Cùng port và session DB; production server reject socket không có Bearer |
| Invalid token HTTP401 → AuthenticationRejectedException/FAILED | PASS | Không retry bằng credential cũ |
| Revoke session active → ERROR UNAUTHORIZED/FAILED | PASS | Chỉ cập nhật session của harness; client abort ngay ERROR, không khẳng định đã quan sát close1008 qua network |
| Expired session handshake → FAILED | PASS | Chỉ expire session của harness |
| Proctor login → authenticated heartbeat ACK | PASS | Không collector, không dashboard |
| Retry budget exhausted server-off → FAILED | PASS | Network/opener thật, 2 retry test config; mặc định production vẫn 4 retry |
| Close adapter/owned worker/server cleanup | PASS | Await các thread toeic-realtime-worker không còn sống, chỉ process harness sở hữu |
| GUI candidate/proctor/wrong password/server-off/restart | NOT RUN | Không công cụ thao tác desktop thật |
| LAN second machine và cross-review A | NOT RUN | Chưa có máy/reviewer thật |

Smoke config: heartbeat 250ms, initial backoff 500ms, cap 2s/maxRetries12 để restart trong cửa sổ; budget case 100/200ms/maxRetries2. Production defaults heartbeat2s, backoff1/2/4/8s, 4 retry. Time waits chỉ giới hạn network/readiness/quiet window; unit race/write/shutdown vẫn dùng latch/scheduler MOCK.

`2026-10-04-real-postgres-smoke.txt` là stdout cố định của lượt cuối; stderr harness/SQL rỗng. SQL qua psql trong toeic-db dùng hash session, chỉ revoke/expire/delete sessions smoke tạo. Không sửa .env hoặc log token/password. Docker DB giữ running theo compose dev; server smoke đã stop.

## Security, handoff và giới hạn

Source/evidence scan: `2026-10-04-real-security-scan.txt`. Exceptions UI/opener fixed, không giữ raw handshake/parser cause; URI chứa credential bị reject trước login GUI. Không persist token. QD-03 giữ nguyên lựa chọn A; PROTOCOL chỉ bổ sung B2 consume status.

C dùng MonitoringTransport.send/onMessage/onConnectionState/connectionState và đóng subscription bằng AutoCloseable. send chỉ là write; ACK/ERROR separate. C2 có ranh giới mạng/connection state; collector integration, eventId/queue/retry/persistence và business ACK vẫn chờ C3/A3. State/full/delta/presence/exam flow/B3 không triển khai. AT/MT/LT/IT chính thức vẫn NOT RUN nếu chưa đủ flow; không đánh PASS IT03 GUI chỉ từ headless worker test. TRACKER giữ nguyên.
