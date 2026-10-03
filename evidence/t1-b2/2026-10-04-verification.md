# T1-B2 — PARTIAL / BLOCKED BY T1-A2

Phiên 03–04/10/2026 (UTC+7), B / Codex Agent. Các lệnh Maven cuối chạy 03/10; tài liệu hoàn tất 04/10. Không quy đổi giờ công hay cập nhật TRACKER.json.

## Dependency và Git base

- Repository: AnhTaizz/Toeic-Monitoring-Realtime.
- Đã chạy `git fetch origin`, `git checkout main`, `git pull --ff-only origin main` trước khi tạo branch.
- Main base: `34a78350d462bd472917171dbef77d70aa8e6b88`, mới nhất sau fetch; PR #1 đã merge.
- Branch: `feat/t1-b2-network-heartbeat-ui`.
- Implementation commit: `7325da1`; test/code SHA được kiểm cuối: `75d4b9fc794c4a1939b47bcdd990ffc0def52164`.
- Commit tài liệu tiếp theo chỉ thay docs/evidence/README. Không merge vào main, không reset/force-push, không sửa task.txt.
- QD-03: **chưa chốt** trong bảng quyết định đang chờ. Phần mô tả header handshake ở mục Mẫu không phải quyết định của A.
- PROTOCOL: WS/envelope/heartbeat/event/ACK vẫn MOCK; chưa ghi endpoint/cách gửi credential thật.
- Server: chưa có WebSocket handler/config/auth handshake.
- Sau fetch không có remote branch A2; GitHub search không thấy open PR hoặc PR T1-A2 làm source of truth (chỉ skeleton PR #1 khi tìm A2).
- T1-A2 contract available: **NO**. Real authenticated WS available: **NO**. Blocked by A2: **YES**.

## Implementation có thể kiểm ngay

`RealtimeClient` là adapter duy nhất dùng raw `java.net.http.WebSocket`. Production default opener trả `AuthContractUnavailableException`, không tạo URL/header/AUTH giả. Hook `ConnectionOpener` là điểm tích hợp chờ A: mỗi lần mở/retry phải mở WS mới, xác thực lại, chỉ hoàn thành sau auth và cleanup HTTP/executor trong close. Các opener trong unit test đều **MOCK**.

- State: CONNECTING / CONNECTED / RECONNECTING / DISCONNECTED / FAILED.
- Heartbeat: `ScheduledExecutorService`, khởi đầu 2 giây, envelope/payload đúng fixture MOCK trong PROTOCOL; chỉ gửi CONNECTED, không tích lũy heartbeat khi write chưa xong.
- Retry: 4 lần sau lần mở đầu; 1/2/4/8 giây, cap 8 giây, Settings configurable; lỗi credential/auth-contract không retry. Budget reset sau khi socket mới kết nối thành công.
- Fragment: tích lũy cho đến last=true; giới hạn 65.536 ký tự rồi bỏ toàn bộ message quá lớn; request(1) sau mỗi text fragment; parser strict, message tiếp theo vẫn xử lý được sau lỗi.
- Validation: bắt buộc v0/type/messageId/requestId/traceId/attemptId/payload; scope guard phía client không thay quyền server. Type chưa hỗ trợ bị từ chối. ACK event phải khớp requestId/type/attemptId/traceId và ACCEPTED. Không tự invent schema WS ERROR/state, chờ owner.
- Write: tuần tự hóa sendText bằng future chain, giới hạn 500 write/ACK pending; future send chỉ là socket write, không phải DB commit/ACK. Không có C3 event retry queue.
- UI: ConnectionViewModel chỉ mở network container khi CONNECTED; callback qua Platform.runLater, gỡ subscription/guard callback cũ khi logout. Container hiện là placeholder, chưa cài thao tác thi hay dashboard. Ứng dụng không bật/mock kết nối giả để mở khóa.
- Shutdown: hủy heartbeat/retry/pending handshake, abort WS kể cả socket đến muộn, fail pending writes, đóng opener, shutdownNow scheduler `toeic-realtime-worker`. Application.stop đóng adapter và HTTP login; HttpClient.shutdownNow + shutdown executor tránh chặn FX thread để chờ network.

## Build và tests đã chạy thật

Môi trường Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11; terminal WSL gọi `mvn.cmd` cài sẵn. Lệnh Maven Java21 được chạy qua cmd.exe sau khi kiểm phiên bản. Không chạy DB/server hay GUI thật trong phiên.

| Lệnh | Kết quả | Module | Tests | Kết thúc (UTC+7) |
|---|---|---|---|---|
| mvn test | PASS | 5/5 | 70/70, 0 failure/error/skipped | 03/10/2026 23:56:53 |
| mvn package | PASS | 5/5 | 70/70, 0 failure/error/skipped | 03/10/2026 23:58:40 |

5 module = root aggregator + protocol + server + client + monitoring-spike. Tổng test = AuthServiceTest 2 + LoginApiClientTest 39 + RealtimeClientTest 28 + ProcessObservationTest 1. Client tổng 67/67. Lượt thử đầu trước 5 test bổ sung là 65/65 PASS, không thay thế log cuối.

Logs đầy đủ (chỉ chuẩn hóa CRLF và che đường dẫn workspace, không sửa kết quả):

- [mvn test](2026-10-03-mvn-test.txt)
- [mvn package](2026-10-03-mvn-package.txt)

Warnings package: Maven Shade báo module-info.class/strong encapsulation; `META-INF.versions.9.module-info` trùng Gson/error_prone_annotations; `META-INF/MANIFEST.MF` trùng các JAR. BUILD SUCCESS và fat JAR client được tạo; không coi package PASS là installer/GUI PASS.

Một lần automatic approval review lệnh Maven có redirect log hết deadline; retry một lần bằng lệnh trực tiếp thành công. Đây không phải test FAIL; các log trên thuộc những lệnh thực sự chạy và hoàn thành.

### Bắt buộc A–F

| Nhóm | Trạng thái | Test chính / giới hạn |
|---|---|---|
| A Fragmented message | PASS — MOCK | fragmentedTextIsParsedOnlyAtLastFragmentAndDemandContinues; chia 3 mảnh, chưa last không parse |
| B Heartbeat | PASS — MOCK | heartbeatFollowsConfiguredIntervalAndStopsOnDisconnect; thời gian ảo, không sleep dài |
| C Reconnect | PASS — MOCK | retryUsesIncreasingCappedDelayAndExhaustsBudget; reconnectOpensFreshSocketAndIgnoresStaleCallbacks |
| D Shutdown | PASS — MOCK + worker thật | closeCancelsHeartbeatRetryAndClosesOpenerExactlyOnce; closeWhileConnectedStopsPeriodicHeartbeat; closeDuringHandshakeCancelsFutureAndAbortsLateSocket; realWorkerRunsOffCallerAndTerminatesAfterClose |
| E State → UI model | PASS — model | connectionStateDrivesLockModelAndObserversCannotKillAdapter; tất cả state khác CONNECTED khóa |
| F Invalid JSON | PASS — MOCK | 8 parameterized malformed variants; rejectsUnknownTypeWrongScopeMissingFieldsAndMismatchedAck; message đúng tiếp theo vẫn nhận |

Test bổ sung: oversized fragmented message; serialize write; stalled heartbeat không tích lũy; send error → reconnect, không expose cause/token; request ID khác payload; giới hạn pending ACK; reject type/state chưa chốt; hủy retry thủ công; không reuse fragment/ACK cũ sau reconnect; Settings/Session invalid; auth rejected không retry; default opener blocked by A2. Không có Socket.IO/STOMP/SockJS/broker hoặc dependency mới.

## Real integration

| Kiểm tra | Trạng thái | Lý do |
|---|---|---|
| Login candidate với server thật trong phiên B2 | NOT RUN | Không chạy lại smoke T1-A1 |
| WS authenticated connection | BLOCKED | T1-A2 chưa có contract/endpoint/auth thật |
| Heartbeat server receipt | BLOCKED | Cùng blocker |
| Server-off behavior trên kết nối thật | BLOCKED | Cùng blocker; MOCK state test không thay thế |
| Reconnect / re-auth với server thật | BLOCKED | Cùng blocker; chỉ kiểm opener gọi lại và socket mới bằng MOCK |
| GUI manually verified | NOT RUN | Không có công cụ thao tác GUI thật |
| Đóng app GUI, không còn Java process | NOT RUN | Chỉ kiểm worker thật terminate, chưa chạy desktop lifecycle thật |
| LAN/second machine | NOT RUN | Không có máy thứ hai thật |

AT02/IT03 và 27 case chính thức không được đánh PASS bằng tests MOCK. Review A→B chưa chạy trong phiên này.

## Handoff to C

Interface `vn.edu.toeic.client.realtime.MonitoringTransport`:

```java
CompletableFuture<Void> send(MessageEnvelope<JsonObject> message);
ConnectionState connectionState();
AutoCloseable onConnectionState(Consumer<ConnectionState> listener);
AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener);
```

C có thể dùng interface và test doubles ngay; gửi PROCESS_OBSERVED/HEARTBEAT hiện kiểm bằng fixture MOCK v0, chưa qua server thật. Scope Session phải đến từ server, không tự tạo scope production. Gửi ở trạng thái mất kết nối trả future lỗi. ACK accepted chỉ qua onMessage khi đã match request. C3 phải giữ ID/payload khi retry và xử lý pending sau reconnect; adapter không tự replay hay tạo event. State/full/delta sẽ dùng cùng send khi C chốt schema/type và B cập nhật validation; hiện type state chưa hỗ trợ bị từ chối. Không implement collector polling C2, event DB A3, proctor dashboard B3 hay Listening.

## Documentation và phần còn lại

- NHAT_KY append; TIEN_DO ghi PARTIAL/BLOCKED; KIEM_THU chỉ ghi test thực sự chạy; VAI_B ghi chú làm dở; README bàn giao interface/config/giới hạn.
- PROTOCOL/QUYET_DINH giữ nguyên ownership A/C; không tự chốt QD-03. TRACKER changed: NO. Không sửa `nguon/` hay `task.txt`.
- T1-B2: **PARTIAL / BLOCKED BY T1-A2**, không DONE.
- Cần A chốt endpoint/auth/credential/scope/error, có WS thật, sau đó B implement authenticated opener và smoke reconnect/re-auth/server-off/UI/shutdown thật; A review B. C chốt state schema khi tới C3.
- Không merge, không chuyển T1-B3.
