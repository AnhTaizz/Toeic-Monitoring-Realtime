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
