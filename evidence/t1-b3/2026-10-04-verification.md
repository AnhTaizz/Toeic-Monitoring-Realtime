# T1-B3 verification — 04/10/2026

Người chạy: Codex Agent. B3 CODE_COMPLETE; nghiệm thu toàn prototype PARTIAL. Human A review, GUI login/candidate/proctor toàn luồng MT01/MT08, LAN và package trên máy thứ hai NOT RUN. Không dùng ChatGPT planning/review theo lựa chọn trước của người dùng.

## Source và môi trường

- Base main sau PR #7: `1e084d4a6ec0d34073502db7c722f8ebf81af175`; branch `feat/t1-b3-proctor-dashboard`.
- Model/API `75e956dbdd35e556a79037c0a46721f35f465021`; parser/UI `e5ead022022f5d1192e338a24e4c177298f2a976`; tests/harness `a601452364dd7580b5ad82f98ffb2d3ec75c3723`. Docs commit sau đó không đổi source. Git/PR/merge cuối trong report.
- Windows11 amd64, Temurin21.0.10+7, Maven3.9.15, JavaFX21.0.12, PostgreSQL18.6 Docker. Không thêm dependency.
- Server người dùng PID24352 đang khóa JAR; build detached checkout tại `client/target/b3-worktree`, từ cùng base, sao chép toàn bộ source/script B3. [19 SHA256](source-checksums.txt) xác nhận byte giống bản được kiểm; [JAR SHA256](jar-checksums.txt). Giữ server/client/Edge người dùng. Public dev vẫn V3; smoke riêng kiểm production Spring V4 thật, không dùng dev V3 làm chứng cứ PASS.

## Build và unit/component

Lệnh thực chạy trong checkout riêng: `mvn test -o -B -ntp` và `mvn package -o -B -ntp`, dùng Maven repository local có sẵn. Baseline277/277 đã chạy lại lúc22:04:14 UTC+7. Final test22:41:50, package22:42:23 UTC+7: PASS331/331, 5/5 modules, failure0/error0/skipped0. Client220, server110, spike1, protocol0.

54 tests mới: MonitoringJson20, DashboardModel9, DashboardController11, MonitoringApiClient11, RealtimeClient3. JSON/MOCK record, fake transport và futures/manual executor được ghi rõ MOCK. HTTP client fixture dùng localhost HttpServer thật nhưng body/auth synthetic MOCK; không gọi đó là production integration.

Đã kiểm numeric long tối đa/overflow/fraction/string, nullable fields/timestamps/status/reason/attempt match, HTTP200 JSON sai, timeout/body4MiB/non200/redirect, Bearer chỉ header. Presence revision mới/cũ/bằng nhau, HTTP cũ sau WS, full roster removal, unknown push buffer, cùng event key khác nội dung/same ID hai attempts, HTTP order và overflow, recoveredAt null→known/history giữ, selection A→B/response cũ, logout, 401/403/5xx riêng từng phần, CONNECTED chưa HTTP fresh, reconnect/dirty history/callback generation và inbox hữu hạn. Parser warning fragment không tiêu thụ ACK; invalid/foreign push không phá connection/pending correlation.

Lượt trước final có một fixture WS thiếu field null do Gson mặc định bỏ null. Test MAX_LONG bị từ chối đúng strict schema. Sửa fixture serializeNulls giống server; không nới validation production. Chỉ lượt final PASS dùng làm chứng cứ. Mockito agent/CDS và shade warnings còn trong log, không làm build thất bại.

Logs: [baseline](baseline-test.txt), [final test](mvn-test.txt), [final package](mvn-package.txt).

## Integration REAL và JavaFX

`powershell -ExecutionPolicy Bypass -File scripts/smoke-b3.ps1 -Gui` chạy final22:42–22:43 UTC+7; [log](real-dashboard-smoke.txt). Harness thuộc server test source chỉ để khởi chạy Spring/PG và production client; không đổi server production/contract.

- UUID schema TEST riêng: Flyway V3 → Spring tự migrate V4; server ephemeral localhost port, login/Bearer/HTTP/WS/production B2/C3/dashboard API/model/parser REAL. Runtime test timeout1200ms/scan25ms/heartbeat100ms/poll50ms, không phải production mặc định6000/500/2000/1000ms hoặc benchmark độ trễ.
- Roster ACTIVE assigned, NEVER UNKNOWN/NOT_SEEN/null; chọn A tải events/history. Candidate B vẫn ONLINE. Owned child candidate A explicit fresh scope/start, MOCK process reading msedge.exe → production C3 event commit/ACK/push → dashboard một dòng. Nhiều polls + HTTP refresh không nhân đôi; other proctor chỉ có C, không nhận A.
- Hard-kill chỉ JVM child do test tạo → A UNKNOWN + một history thật; candidate B tiếp tục ONLINE. JVM mới phục hồi → ONLINE, cùng gapId/recoveredAt, history không mất.
- Chủ động disconnect socket proctor để kiểm offline thật (không gọi đây là lỗi mạng ngẫu nhiên/backoff tự động). Last ONLINE giữ với stale. Khi offline phát sinh event2 và timeout/recovery; reconnect đọc auth/me/roster/events/history thật, bù đủ hai events/hai gaps không trùng. B2 sở hữu reconnect, không vòng mới ở dashboard.
- Xóa assignment A trong schema TEST → selected events HTTP403, dọn A/selection/chi tiết, auth/me cập nhật adapter scope. Revoke token TEST → HTTP401, xóa dữ liệu phiên và callback yêu cầu đăng nhập lại.
- `-Gui` mở visible JavaFX Stage với production ProctorDashboardView và transport thật. Thao tác controls trên JavaFX thread: chọn A/tab process/history, refresh/logout callback. Hard-kill A khi Stage đang mở → UNKNOWN/history3; phục hồi → ONLINE/history3 và recoveredAt. Proctor disconnect → banner “Dashboard mất kết nối…” và ONLINE “Dữ liệu cũ”; reconnect → HTTP đồng bộ. Không mô phỏng ảnh.

Ảnh actual Scene1100×740 đã xem bằng công cụ đọc ảnh: [ONLINE/process](dashboard-online.png), [history](dashboard-history.png), [UNKNOWN/chưa phục hồi](dashboard-unknown.png), [ONLINE/history còn](dashboard-recovered.png), [dashboard stale/ONLINE cũ](dashboard-stale.png). Ảnh chỉ identity TEST, không token/password. GUI component PASS; harness mở view sau login API, không thao tác màn login hay candidate controls và không kiểm toàn luồng desktop MT01/MT08.

## Hồi quy và cleanup

Sau final package, đã chạy lại `scripts/smoke-c3.ps1` và `scripts/smoke-a4.ps1` lúc22:43 UTC+7 trên JAR mới.

- [C3 regression PASS](c3-regression.txt): Windows ProcessHandle và owned Edge REAL → C2/C3/B2 → DB/ACK; first ACK loss SIMULATED, row1/warning1; real server stop/restart/B2 reconnect, MOCK snapshot pending; bounded MOCK overflow → REAL gap/commit/rollback/retry/auth; cleanup worker/profile của lượt test. Không retry xóa folder Edge cũ đã bị automatic review từ chối.
- [A4 regression PASS](a4-regression.txt): scoped heartbeat/hard-kill/recovery/isolation, revoked/CLOSED/invalid inputs, deferred PostgreSQL COMMIT failure/rollback không ACK/push giả, worker retry, offline history/restart. Dòng PRESENCE_SCAN retryable/PowerShell NativeCommandError trong log là stderr từ fault injection mong đợi; process exit0 và final A4 PASS, không che lỗi test thất bại.
- B3 controller/view close unsubscribe/cancel/generation, HTTP/state workers tắt, app đóng transport. Integration xác nhận dashboard/collector/delivery/B2/presence/child-reader workers hết; tất cả schema TEST cleanup. SQL sau smoke: count schema b3_test_/a4_test_/c3_test_ =0; JVM integration còn0; PID24352 người dùng còn sống; public migration3 giữ nguyên. Không reset DB/chạy B4/C4.
- [Security scan PASS](security-scan.txt): actual local credentials/raw bearer/private paths không được thêm; logs UTF8 LF/sanitized; .env/task.txt không tracked; production JAR không Test/Smoke hoặc dependency client test; TRACKER/nguon/plans01–03/server production/V1–V4 không đổi.

## Bàn giao và kiểm còn thiếu

Đã cập nhật README/docs README, PROTOCOL client consume, TIEN_DO, NHAT_KY append, KIEM_THU, VAI_B; giữ TRACKER. Handoff: HTTP full roster authoritative, presence revision long, event key theo attempt, history recoveredAt cập nhật, freshness riêng mỗi phần, bounded buffers/manual refresh. Epoch/full/delta, overflow gap timeline, nghiệp vụ bài thi và B4 không thuộc B3.

Checklist còn cần người dùng/nhóm: chủ động chạy dev server V4 mới; candidate GUI login/start + process Edge thật → proctor GUI, sai mật khẩu/server tắt/login lại; LAN và app-image trên Windows thứ hai; human A review. MT01/MT08 toàn case PARTIAL, không gọi prototype PASS chỉ từ localhost/component. Không có blocker kỹ thuật B3 sau các lượt final PASS; merge chỉ khi PR mergeable và HEAD đúng bản kiểm.
