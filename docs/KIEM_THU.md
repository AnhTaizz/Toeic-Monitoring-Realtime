# Trạng thái kiểm thử

Mô tả đầy đủ từng case (cách gây tình huống, kết quả bắt buộc) ở `Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md`. File này chỉ theo dõi đã chạy chưa và bằng chứng ở đâu.

Trạng thái dùng: `NOT RUN`, `PASS`, `FAIL`, `BLOCKED`; `PARTIAL` khi chỉ một phần case được kiểm (MT01 local C2). Chỉ ghi `PASS` khi đã chạy thật và có bằng chứng.

Mỗi lần chạy ghi đủ: commit SHA, OS/JDK/PostgreSQL, cấu hình, lệnh hoặc bước chạy, kết quả mong đợi, kết quả thật, log hoặc ảnh, người chạy. Lưu bằng chứng vào thư mục `evidence/<testId>/` (tạo khi có lần chạy đầu tiên).

## Answer / Submission

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| AT01 | Thí sinh B không đọc/sửa được bài của A, trước và sau khi nộp | A / C | T2-A4, T3-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT02 | Chưa login hoặc sai role gọi import/start; WS thiếu token | A / C | T1-A2, T2-A1 | **PASS** | `5fafab2` | `evidence/t2-a1/2026-10-05-verification.md` |
| AT03 | Revision 42 rồi 41: bản cũ không ghi đè | A / C | T2-A2 | **PASS** | `443458d` | `evidence/t2-a2/2026-10-05-verification.md` |
| AT04 | Cùng revision cùng nội dung, khác nội dung, đổi thứ tự key | A / C | T2-A2 | **PASS** | `443458d` | `evidence/t2-a2/2026-10-05-verification.md` |
| AT05 | requestId lặp; rollback không ACK thành công | A / C | T2-A2, T4-A1 | **PASS** | `443458d` | `evidence/t2-a2/2026-10-05-verification.md` |
| AT06 | Autosave cũ đến sau submit không đổi bài đã chốt | A / C | T2-A3 | **PASS** | `f28099b` | `evidence/t2-a3/2026-10-05-verification.md` |
| AT07 | Chờ khóa qua deadline: quyết định theo giờ sau khóa | A / C | T2-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT08 | Submit lặp, timeout, save đến gần nhau: một kết quả, chấm một lần | A / C | T2-A3, T4-A1 | **PASS** | `f28099b` | `evidence/t2-a3/2026-10-05-verification.md` |
| AT09 | Request của writer cũ không ghi được sau takeover | A / C | T2-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT10 | Không nhận đáp án sau khi chốt hoặc sau deadline | A / C | T2-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT11 | Server 40 / client 42, mất ACK, reconnect, mở app mới | B / A | T2-B4, T4-B1 | NOT RUN | | |

## Monitoring

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| MT01 | Full snapshot hợp lệ; event không lặp mỗi poll; collector không chạy trên giám thị | C / B | T1-C2/C3, T2-C1/C2 | PARTIAL — T2-C1 full REAL Edge/network/proctor JavaFX component PASS; candidate GUI toàn app và T2-C2 chưa đủ | `6acd031`, source/JAR manifest trong evidence | `evidence/t2-c1/2026-10-10-verification.md`; full hiện tại tách event, Edge đóng biến mất khỏi state, lịch sử giữ nguyên; chưa full GUI/LAN |
| MT02 | Replay cùng message sau mất ACK | C / B | T3-C3 | NOT RUN | | |
| MT03 | Thiếu một delta → UNSYNCED, yêu cầu full | C / B | T3-C3 | NOT RUN | | |
| MT04 | Delta epoch cũ không sửa state epoch mới | C / B | T3-C3/C4 | NOT RUN | | |
| MT05 | Restart collector hoặc server → cần epoch và full mới | C / B | T3-C3/C4 | NOT RUN | | |
| MT06 | Không lùi state; full và delta cho cùng state và event set | C / B | T3-C3 | NOT RUN | | |
| MT07 | Event muộn, queue tràn; snapshot mới không xóa gap | C / B | T2-C2 | NOT RUN | | |
| MT08 | Kill client → server tự chuyển UNKNOWN | C / B | T2-C2 | PARTIAL — A4 server + B3 visible proctor component PASS; candidate GUI toàn luồng NOT RUN | base B3 `1e084d4`, source checksum trong evidence | `evidence/t1-b3/2026-10-04-verification.md`; hard-kill owned JVM → UNKNOWN/history/recovered ONLINE trên Stage thật; không full/state chặng2 |

## Listening và tích hợp

Full-only retry/epoch/order/reconnect đã kiểm trong acceptance T2-C1 bên dưới. Không đổi MT02–MT06 của delta thành PASS và không coi full/C4 log là E1/E2.

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| LT01 | Thiếu audio hoặc sai checksum → không READY | B / A | T3-B1 | NOT RUN | | |
| LT02 | Start lặp không phát lại; lỗi media khóa và ghi gián đoạn | B / A | T3-B2/B3 | NOT RUN | | |
| LT03 | Mất mạng giữa audio; mở lại không tự phát hay resume | B / A | T3-B3 | NOT RUN | | |
| LT04 | Tổ chức lại: attempt mới, dữ liệu và deadline cũ giữ nguyên | B / A | T3-B4, T3-A2 | NOT RUN | | |
| IT01 | Chạy từ package trên máy không có IDE, đường dẫn có dấu | B / A | T1-B4, T3-B5, T5-B1 | NOT RUN | | |
| IT02 | 2 thí sinh + 1 giám thị; một client gửi JSON sai hoặc rớt | A / C | T3-A4, T4-B1 | NOT RUN | | |
| IT03 | Mạng hoặc quét chậm, UI vẫn phản hồi; đóng app dọn worker | B / A | T1-B2, T4-B1 | NOT RUN | | |
| IT04 | DB hoặc server tắt giữa lúc xử lý; khởi động lại | A / C | T3-A3, T4-A1, T4-B1 | NOT RUN | | |

## Thực nghiệm

| Mã | Nội dung | Ai chạy | Task | Trạng thái | Nơi lưu raw |
|---|---|---|---|---|---|
| E1 | Polling 250/500/1.000/2.000 ms, 3 lần lặp (12 run) | B chạy, C cấp harness và phân tích | T4-B2, T4-C3 | NOT RUN | |
| E2 | Full snapshot vs delta, 3 trace × 3 lần × 2 chế độ (18 run) | C | T4-C2 | NOT RUN | |
| Tải | 1/5/10 client giả lập, 3 lần mỗi mức (9 run) | A | T4-A2 | NOT RUN | |

## Kiểm tra acceptance chặng 1 (ngoài 27 case chính thức)

Các kiểm tra dưới đây không thay đổi trạng thái AT/MT/LT/IT ở trên.

| Ngày | Phạm vi | SHA | Môi trường | Kết quả | Bằng chứng |
|---|---|---|---|---|---|
| 03/10/2026 | Maven build + unit test T1-A1/B1/C1 | `864f701` | Windows 11, JDK 21.0.8, Maven 3.9.11 | PASS, 5 module; 7/7 test | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | Reset PostgreSQL, Flyway V1, login candidate/proctor/sai | code sau đó commit tại `bb48376` | PostgreSQL 18.6 Docker | PASS; 200/200/401 | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | ProcessHandle probe | `1f012c4` | Windows 11, user thường | PASS có giới hạn; 133/200 process thiếu metadata | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | Windows app-image local | `864f701` | JDK/jpackage 21.0.8 | PASS local; chưa chạy máy thứ hai | `evidence/stage1/2026-10-03-skeleton-smoke.md` |

## Review-fix PR #1 — 03/10/2026

Code được kiểm: `961feca789fa8d9e3ded554d0ecb21fccbe43d47`. `mvn test` chạy trước commit trên cùng code; `mvn package` chạy sau commit. Môi trường: Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, qua terminal WSL. Người chạy: Codex Agent. Không thay đổi trạng thái 27 case chính thức ở trên.

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn test` | PASS — 5/5 module, 42/42 test; 0 failure/error/skipped | `evidence/stage1/2026-10-03-review-build.txt` |
| `mvn package` | PASS — 5/5 module, 42/42 test; fat JAR client được tạo | Cùng file trích log build |
| Login HTTP 200: malformed JSON, thiếu user, role ADMIN, body rỗng | PASS — future lỗi InvalidServerResponseException; không trả DTO; thông báo UI chung được kiểm ở mức hàm | LoginApiClientTest và trích log build |
| Các trường bắt buộc thiếu/null/blank/sai kiểu; JSON null/array/user sai kiểu/trailing data | PASS — response bị từ chối an toàn | LoginApiClientTest (35 lượt test mới tổng cộng) |
| LAN URL, thiếu scheme, HTTP chậm bằng latch, map candidate/proctor; HTTP proctor hợp lệ và HTTP 401 JSON/non-JSON | PASS — unit/HTTP local; không phải LAN máy thứ hai | LoginApiClientTest |
| Candidate/proctor/sai mật khẩu/server tắt trên GUI thật | NOT RUN — không có công cụ thao tác GUI trong phiên Agent | Phần review-fix trong smoke evidence |
| LAN second-machine test | NOT RUN — chưa có máy Windows thứ hai thật | Cùng smoke evidence |

Reset/migration PostgreSQL, HTTP login thật vào server, app-image và probe ProcessHandle không chạy lại trong phiên review; kết quả lịch sử ở các dòng acceptance phía trên vẫn thuộc phiên trước. Kiểm tra source xác nhận sendAsync/Platform.runLater/stop() vẫn giữ; chưa thay cho test tương tác GUI.

## Verification cuối trước merge PR #1 — 03/10/2026

HEAD đối chiếu: `ceb8131a558660057cf62c46f2466db8515b9b4e`. Chỉ cập nhật docs/evidence, không chạy lại build/test. Source không đổi so với code đã kiểm tại `961feca`; 42/42 PASS và package PASS tiếp tục trỏ tới log lượt chạy thật trước.

| Kiểm tra | Trạng thái | Lý do / bằng chứng |
|---|---|---|
| PR description phản ánh 42/42 test, package và review-fix | PASS | GitHub update PR #1 thành công; không còn dòng 7/7 tests cũ |
| Candidate GUI | NOT RUN | Không có công cụ thao tác GUI thật |
| Proctor GUI | NOT RUN | Cùng lý do |
| Wrong-password GUI | NOT RUN | Cùng lý do |
| Server-off / bad URL GUI | NOT RUN | Cùng lý do |
| Candidate/proctor/wrong password qua LAN máy thứ hai | NOT RUN | Không có máy Windows thứ hai thật được cung cấp |
| A reviewed B | NOT RUN | Chưa đủ xác nhận review thực tế; GitHub chưa có review/comment |
| B reviewed C | NOT RUN | Cùng lý do |
| C reviewed A | NOT RUN | Cùng lý do |

Bằng chứng và giới hạn xác nhận: `evidence/stage1/2026-10-03-merge-readiness.md`. Không thay đổi trạng thái 27 test chính thức hay TRACKER.json. Ready to merge: NO.

## Acceptance T2-C1 — 10/10/2026

Base `8eba405`, code `6acd031`; Windows11/Temurin21.0.10/PostgreSQL18.6/Maven3.9.15/Python3.15.0b3. Agent chạy. Baseline trước sửa Java376/A2 PASS; sau sửa Java408/package/Python10 PASS. A2/A3/A4/C2/C3/C4/B3 và B2 riêng trên owned TEST DB PASS. B2 wrapper DB dev có lỗi tiền điều kiện scope rỗng; không xóa fixture để làm test qua. A4/B3 harness hỗ trợ migration mới và B3 tab mới; C3 ERROR phải correlate request gap.

T2-C1 REAL HTTP/WS/PG/ProcessHandle/owned Edge/proctor JavaFX component PASS: full1, Edge xuất hiện/biến mất giữ event, reconnect/new epoch/old epoch STALE, retry/conflict, full rỗng, role/scope, CLOSE/STale, history gap/interruption còn và dọn workers. Fault process sets MOCK; queue/scan failure/order/TTL/late callback/canceled socket write bằng MOCK unit. 4file measurement FINAL, drop/unwritten/pending0, summary COMPLETE. Chi tiết, lỗi ban đầu đã sửa, configs thực và ảnh: [evidence](../evidence/t2-c1/2026-10-10-verification.md).

MT01 vẫn PARTIAL; GUI candidate toàn app, LAN máy thứ hai, human A/B review, full server restart network và lỗi scan OS thực NOT RUN. Không triển khai task C2–C5 chặng2/delta/E1/E2. Không reset DB/migration/tracker/kế hoạch.

## T1-B2 — kiểm 03/10, ghi nhận 04/10/2026

Code SHA: `75d4b9fc794c4a1939b47bcdd990ffc0def52164`, base main `34a7835`. Windows 11 x64 / Oracle JDK 21.0.8 / Maven 3.9.11, gọi từ WSL; người chạy Codex Agent. Không dùng PostgreSQL/server thật trong phiên này. Evidence tổng: `evidence/t1-b2/2026-10-04-verification.md`.

| Kiểm tra | Kết quả | Bằng chứng / giới hạn |
|---|---|---|
| mvn test | PASS — 5/5 module, 70/70 test, 0 failure/error/skipped | `evidence/t1-b2/2026-10-03-mvn-test.txt`; realtime 28/28 MOCK |
| mvn package | PASS — 5/5 module, 70/70 test, fat JAR client tạo được | `evidence/t1-b2/2026-10-03-mvn-package.txt`; shade cảnh báo module-info/MANIFEST trùng |
| Fragment >=2 phần, parse chỉ last=true; request(1) mỗi fragment; message quá lớn | PASS — MOCK | RealtimeClientTest; không phải socket server thật |
| Heartbeat 2s khi CONNECTED; không gửi sau disconnect/close, không tích lũy khi write kẹt | PASS — MOCK, thời gian ảo | RealtimeClientTest + MockScheduler |
| Mất connection → RECONNECTING; delay tăng/cap; hết 4 retry → FAILED | PASS — MOCK | Test budget 1+4 lần mở, không sleep dài |
| Reconnect dùng socket mới; bỏ fragment/ACK/callback cũ; lỗi auth không retry | PASS — MOCK | Hook opener được gọi lại; chưa chứng minh re-auth thật |
| close hủy heartbeat/retry/handshake, abort socket, close opener, shutdown executor | PASS — MOCK + executor thật | Test worker thật dùng awaitTermination; không phải đóng GUI thật |
| ConnectionState → UI model khóa/mở | PASS — model | Container JavaFX cập nhật qua Platform.runLater; thao tác GUI NOT RUN |
| JSON malformed/type lạ/thiếu trường/sai scope/ACK lệch không crash listener | PASS — MOCK | Message bị từ chối bằng thông báo cố định, message hợp lệ tiếp theo vẫn nhận |
| WS real authenticated, heartbeat server receipt, server-off/reconnect/re-auth | BLOCKED BY T1-A2 | QD-03/endpoint/auth chưa có; không tự bịa contract |
| Login server thật trong phiên B2 | NOT RUN | Không chạy lại smoke T1-A1; kết quả lịch sử giữ nguyên |
| GUI manual / LAN máy thứ hai / shutdown app GUI | NOT RUN | Không có công cụ điều khiển GUI hay máy thứ hai thật |

Không đổi trạng thái IT03/AT02 hoặc 27 case chính thức bằng test MOCK; TRACKER.json giữ nguyên. T1-B2 **PARTIAL / BLOCKED BY T1-A2**.

## T1-A2 — 04/10/2026

Code: `7ac62c4d0bc5120ba10f887efbe5478a693d6385`, base main `34a7835`. Windows 11 x64 / Oracle JDK 21.0.8 / Maven 3.9.11; PostgreSQL 18 Docker. Người chạy: Codex Agent. Evidence: `evidence/t1-a2/2026-10-04-verification.md`.

| Kiểm tra | Trạng thái | Evidence/giới hạn |
|---|---|---|
| mvn test | PASS — 91/91, 5/5 module, 0 failure/error/skipped | `2026-10-04-mvn-test.txt`; 49 tests mới ngoài baseline 42 |
| mvn package | PASS — 91/91, 5/5 module, JAR production | `2026-10-04-mvn-package.txt`; Mockito dynamic-agent/CDS và shade warnings |
| Valid/invalid/expired/exact expiry/revoked/disabled session | PASS | SessionAuthenticationTest; REST/handshake/message network cases; expired/revoked còn kiểm JDBC thật |
| Login public, protected REST thiếu/sai bearer 401, principal đúng user | PASS — real HTTP | AuthenticatedNetworkTest dùng session store MOCK; PostgreSQL smoke /auth/me thật |
| Role đúng/sai 403; own/foreign/proctor scope, check-before-business | PASS — unit + real HTTP/WS | Scope provider MOCK ghi rõ; production deny unknown/all unassigned attempts, không invent data |
| WS missing/invalid/malformed/expired/revoked/disabled header reject | PASS — real java.net.http.WebSocket/Spring | AuthenticatedNetworkTest; thiếu/sai/expired/revoked còn kiểm PostgreSQL smoke |
| Header handshake, heartbeat/ACK correlation, fragmented heartbeat | PASS — real network | Java21 raw WS + Spring/Tomcat; không chứng minh event commit/presence |
| JSON sai/type lạ/foreign scope không crash; client khác còn hoạt động | PASS — real network | Controlled ERROR; client tiếp tục nhận heartbeat ACK |
| Revalidate WS mỗi message, revoke/expiry/disabled → ERROR + close1008 | PASS — real network | MOCK store; PostgreSQL smoke kiểm revoke trên active socket |
| Concurrent send protection | PASS — MOCK raw session, worker thật/latch | ConcurrentRealtimeSendTest kiểm max 1 raw writer với hai caller trùng thời điểm |
| PostgreSQL production smoke | PASS | Script dùng app production, JDBC/Flyway/session thật; không H2, không load test MOCK configuration |
| Token/password không log hoặc reflected trong ERROR | PASS | OutputCapture network test + diff/evidence secret scan; không in credential trong assertion output |
| GUI/LAN, B2 real integration, event DB/presence | NOT RUN | Ngoài task A2; nhánh B2 chưa đổi/merge, A3/C3/A4 chưa cài |

AT02 mới kiểm các thành phần auth/role/scope/WS của A2; endpoint import/start thật chưa có, nên không đánh PASS toàn bộ case AT02 hoặc 27 cases bằng test fixture. TRACKER.json giữ nguyên. Optional clean FAIL vì app-image EXE bị Windows khóa; không ảnh hưởng test/package PASS và không kill process người dùng.

## T1-B2 — real integration 04/10/2026

Source `6d5c7f4335743b42e74b7232b2e219a89be2e9ca`, merge main A2 `f62c732` bằng `57f2a9c`. Windows 11/JDK21.0.8/Maven3.9.11/PostgreSQL18.6; người chạy Codex Agent. Evidence `evidence/t1-b2/2026-10-04-real-integration.md`.

| Kiểm tra | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| Baseline sau merge main | PASS 119/119, 5/5 module | Log merged-baseline-test |
| Final mvn test/package | PASS 142/142 mỗi lượt, 5/5 module; 0 failure/error/skipped | Logs real-mvn-test/package; 23 lượt mới phiên này |
| URI HTTP→WS/HTTPS→WSS, no query, header mỗi open | PASS | Opener unit + real HTTP fixture 401/503; production WS smoke |
| Fragment/JSON/bounded writes/ACK correlation/shutdown | PASS | 36 RealtimeClient MOCK test lượt; worker thật được await |
| Unscoped heartbeat/optional collector/ACK, direct ERROR/1008 | PASS | Unit/mock parser; real heartbeat ACK/ERROR; close1008 riêng là MOCK test |
| Candidate/proctor real login → chính B adapter → ACK | PASS | Production Spring/JDBC/Flyway/PostgreSQL; không H2/provider MOCK |
| Invalid/expired/revoked → auth FAILED, no old-token retry | PASS | PostgreSQL real smoke + controlled unit tests |
| Owned server stop/restart → lock model/reconnect/fresh auth | PASS | Real process/network; không phải GUI manual |
| Retry budget exhausted + owned worker/server cleanup | PASS | Real smoke và unit shutdown |
| Token/password/private path diff/evidence scan | PASS | Security scan log, fixed smoke stdout, empty stderr |
| GUI manual/LAN/cross-review A | NOT RUN | Chưa có công cụ desktop/máy/reviewer thật |

Không đổi trạng thái 27 case chính thức: IT03 mới kiểm headless model/worker, chưa thao tác GUI; event/presence/queue/dashboard chờ A3/A4/C3/B3. TRACKER giữ nguyên.

## T1-C2 — 04/10/2026

Source `02789a86c1c9785bc54f31b4584751eae3340f10`, base main `9f01861`. Windows11/JDK21.0.8/Maven3.9.11, người chạy Codex Agent. Evidence `evidence/t1-c2/2026-10-04-verification.md`.

| Kiểm tra | Trạng thái | Evidence/giới hạn |
|---|---|---|
| Final mvn test/package | PASS 175/175 mỗi lượt, 5/5 module, 0 failure/error/skipped | Server51/client123/monitoring1; C2 thêm33 lượt |
| QD-08 v1 filenames/case-insensitive/immutable list | PASS | ProcessPolicyAndSourceTest, không mở rộng policy |
| Immutable restricted-only snapshot + unreadable counts | PASS | Unknown command không phải sạch; lỗi poll clear latest thay vì publish clean snapshot |
| Session/PID/start identity, PID reuse/start null | PASS | Start khác → identity khác; missing start UNREADABLE; restart UUID mới |
| Fixed-delay/no-overlap/thread off caller | PASS | Slow fake source/latch, second poll không chạy khi first giữ latch; interval sau first finish |
| start/stop/restart/no later poll/worker termination | PASS | Interrupt source và await worker; ngăn restart khi old source chưa kết thúc |
| Source/listener/diagnostic listener failures | PASS | Controlled problem enum; poll sau tiếp tục |
| Active/inactive candidate, active/inactive proctor gate | PASS | Lifecycle side effects với MOCK contexts, không chỉ kiểm enum |
| Real ProcessHandle Windows source | PASS | Smoke:378 scanned,162 unreadable; discord.exe/msedge.exe/zalo.exe observed |
| Owned headless Edge open/close process-set transition | PASS | Profile tạm riêng, chỉ close owned tree; không kill process user |
| Real collector stop/restart/cleanup | PASS | Source production; active context MOCK, không giả assignment |
| GUI manual/LAN/review B | NOT RUN | Headless/role-gate unit không phải GUI PASS |

MT01 PARTIAL: local polling/gate/lifecycle đã kiểm; network full/state/event non-dup/persistence còn C3/T2-C1/C2. E1 không chạy, không sửa TRACKER. Smoke đầu FAIL trước sửa PID0 idle validation; after-fix smoke PASS trên code sau đó commit02789a8. Mockito/ByteBuddy + shade warnings không làm fail build.

## T1-A3 — 04/10/2026

Source/test/script `88ef95a2d96db202edf39e3ed5c72f026bfdb7fc`, base main `6b9572e`. Windows11 amd64/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6; người chạy Codex Agent. Evidence `evidence/t1-a3/2026-10-04-verification.md` và sanitized logs.

| Kiểm tra | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| Final mvn test/package | PASS 200/200 mỗi lượt; 5/5 module; 0 failure/error/skipped | Server76/client123/spike1, A3 mới25; package không đóng Test/Smoke classes |
| Payload validation và privacy | PASS | ProcessEventTest24 + real WS invalid JSON/field/path |
| Schema clean V1+V2 và upgrade V1→V2 | PASS — PostgreSQL thật | Hai schema TEST riêng; FK/unique SQLSTATE23503/23505 |
| Production assignment scope, login/auth-me | PASS — DB/HTTP/WS thật | Own/assigned ACTIVE allowed; foreign/unknown/CLOSED denied |
| Event commit→ACK→push assigned | PASS — mạng/DB thật | Không broadcast proctor khác; candidate chỉ ACK |
| Duplicate same/changed, concurrent retry | PASS — mạng/DB thật | ACK2/row1/warning1; CONFLICT không overwrite; CyclicBarrier hai workers |
| Deferred COMMIT fail rollback/no success ACK/no warning | PASS — PostgreSQL thật | Trigger TEST chạy tại COMMIT; retryable error/row0 |
| Timeline auth/order/offline recovery | PASS — HTTP/DB thật | Bearer+PROCTOR+scope; received_at,id order; offline event phục hồi |
| Revalidate assignment/session trước push/message | PASS — mạng/DB thật | Xóa assignment→no push/403; revoked proctor/candidate→close1008 |
| Concurrent ACK/ACK và ACK/warning send | PASS — MOCK raw session + latch/worker thật | Max1 raw writer; không dùng sleep để điều khiển overlap |
| Secret/path scan | PASS | security-scan.txt; TEST-only password và synthetic rejected path không credential thật |
| DB-off smoke | NOT RUN | DB dùng chung; dùng COMMIT-failure test an toàn, không gọi là DB-off PASS |
| C human review, GUI/LAN | NOT RUN | Không giả review hoặc desktop demo từ headless network harness |

Không đánh PASS 27 case chính thức bằng test thành phần A3: C3 queue/collector integration, B3 dashboard, A4 presence/full/delta/exam flow chưa làm. TRACKER giữ nguyên. Không thêm QD implementation-detail; QD-05 vẫn áp dụng. Lỗi JDBC Timestamp/test fixtures đã sửa trước lượt PASS, xem evidence.

## T1-C3 — 04/10/2026

Base main `74d2704`. Windows11/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6. Baseline chạy lại PASS200; final mvn test19:37:13 và package19:38:26 UTC+7 PASS253/253 mỗi lượt, client161/server91/spike1; 53 mới (delivery30, scope7, adapter1, gap15). Số lấy từ summary Maven, không cộng XML surefire cũ trong target. Evidence `evidence/t1-c3/2026-10-04-verification.md`.

| Kiểm tra | Kết quả | Phân loại / giới hạn |
|---|---|---|
| First snapshot, 10 poll không trùng, disappearance/reappearance, PID/start reuse | PASS | MOCK snapshots, fake wall/monotonic clock |
| Missing start/quality, source failure, bounded baseline | PASS | MOCK; null→known coi identity mới; không hứa PID reuse hoàn hảo |
| Frozen ID/payload/time, write≠ACK, early/wrong/duplicate ACK | PASS | MOCK transport; deep copy, sáu correlation fields |
| Timeout/backoff/budget/manual retry và permanent/permission errors | PASS | MOCK virtual time; reconnect không reset budget |
| Overflow/frozen gap/next accumulator/heartbeat slot | PASS | MOCK source/transport unit; real delivery bên dưới |
| Late old send completion/ACK, asyncStop, restart chờ blocked scan | PASS | MOCK + latch, không long sleep làm chứng cứ |
| Fresh auth-me, empty candidate scope/proctor gate | PASS | MOCK HTTP unit và REAL TEST login/me; không scan/queue scope rỗng |
| Windows ProcessHandle + owned Edge open/close + C2→C3→B2→DB/ACK | PASS REAL | Profile/process tree riêng; warning kiểm qua WS test, không GUI |
| Lost ACK retry row1/warning1, many real polls | PASS | ACK loss SIMULATED tại observer; HTTP/WS/DB/C2/C3 REAL |
| Offline assigned proctor đọc lại timeline | PASS REAL | Endpoint A3 giữ nguyên; không B3 dashboard |
| B2 reconnect sau owned Spring server stop/restart | PASS | Server/network REAL; queued snapshot MOCK; PostgreSQL không tắt |
| Capacity1 overflow→persist/commitACK gap, dedup/next accumulator | PASS | Snapshots MOCK; WS/PG/ACK REAL; đầu gap ACK loss SIMULATED |
| Deferred gap COMMIT failure→rollback/error→retry | PASS REAL | Trigger TEST riêng, không sửa production handler để bỏ ACK |
| Gap conflict/foreign/CLOSED/unknown/proctor forbidden | PASS REAL | Row không đổi; event state/warning contract giữ nguyên |
| Collector/delivery/B2 workers/subscription cleanup | PASS | Harness đợi worker sở hữu kết thúc; stop unit idempotent |
| A3 regression smoke trên V3 mới | PASS REAL | Scope/revocation/concurrent duplicate/commit failure/timeline PASS |
| DEV fixture Create/duplicate rejection/idempotent Cleanup | PASS REAL | Schema c3_demo_test_UUID riêng, user2 giữ nguyên, public không chạm |
| JAR/security/diff check | PASS | Evidence scan; test/harness/client test dependency không lọt serverJAR |
| Human B review, GUI manual, LAN máy2 | NOT RUN | Không giả Agent/headless review thành review thành viên |

MT01 vẫn PARTIAL tới B3; A4 presence chưa làm. Không thực nghiệmE1, không full/delta/reducer/đề thi, không đổi TRACKER hoặc nguồn01–03. Lượt unit đầu có một assertion gap retry sai giả định thứ tự slot; đã sửa test theo việc event dùng được slot lúc gap backoff, final PASS ở trên. Không dùng lượt lỗi làm chứng cứ PASS.

## T1-A4 — 04/10/2026

Base main99b4f39. Windows11/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6; người chạy Codex Agent. Baseline chạy lại20:48:02 UTC+7 PASS253. Final mvn test21:26:27 và package21:28:28 PASS277/277, client166/server110/spike1, 24 lượt mới; test/package ở checkout build riêng vì JAR dev đang được server người dùng giữ. Source checksum đối chiếu ở security-scan, code không thay bởi checkout. Test và package cùng code hành vi; package bổ sung import IOException tương đương thay FQN trong harness cleanup. Evidence `evidence/t1-a4/2026-10-04-verification.md`.

| Kiểm tra | Kết quả thật | Giới hạn |
|---|---|---|
| Scoped lease start/stop/reconnect/old callback, unscoped/proctor/malformed | PASS | Unit MOCK và HTTP/WS thật; không thao tác GUI |
| Deadline6s fake ticker, wall-clock jump, CAS/race có latch, association bounded/cleanup | PASS | Service MOCK store/clock; không đo LAN |
| PostgreSQL V3→V4, roster role/ACTIVE scope, history permission | PASS REAL | Schema TEST UUID; public vẫn V3 vì giữ server đang chạy |
| Hai candidate B2/C3, hard-kill JVM A riêng, B ONLINE/event/ACK | PASS REAL | Process snapshot MOCK; hard-kill thật, không gọi graceful close là kill |
| Assigned/foreign MONITOR_PRESENCE, old socket, recoveredAt + stable gapId | PASS REAL | WS test giám thị, không GUI dashboard |
| Deferred COMMIT failure heartbeat/timeout, rollback/no fake ACK/push/gap, retry | PASS REAL | TEST constraint trigger; không tắt/reset DB dùng chung |
| Token revoke/CLOSED cleanup, Stop→timeout, offline roster/history, server restart | PASS REAL | Restart server do test sở hữu, không server dev |
| Presence/collector/delivery/B2/child-reader cleanup | PASS REAL | Final run hết workers; thư mục Edge từ lượt hồi quy lỗi còn riêng bên dưới |
| A3 regression | PASS REAL | Event/duplicate/conflict/concurrency/deferred COMMIT/live scope/timeline giữ contract |
| C3 regression | PASS REAL | Windows ProcessHandle/owned Edge thật; ACK loss SIMULATED, overflow source MOCK → gap REAL |
| GUI/LAN/package máy khác/human C review | NOT RUN | B3 còn dashboard/parser/HTTP recovery; chưa nghiệm thu toàn prototype |

Lượt đầu decorator test phát hiện Gson không serialize Instant; đã thêm adapter ISO UTC và chạy lại PASS. Hồi quy đầu A3 dùng scoped ping không collector, chỉnh sang unscoped đúng mục đích transport. Hồi quy đầu C3 lỗi khóa profile Edge sau exit, thêm bounded cleanup retry; lượt final PASS. Lệnh dọn riêng profile sót của lượt lỗi bị kiểm duyệt tự động từ chối (`blocked by policy`), profile còn nhưng kiểm tra không process Edge nào dùng nó; không kill process người dùng. Không lấy lượt lỗi làm evidence PASS. MT08 PARTIAL ở mức case toàn hệ thống, thành phần server A4 PASS; IT02/IT03/GUI chưa đánh PASS toàn bộ. TRACKER/nguon/01–03 và V1–V3 giữ nguyên.

## T1-C4 — 05/10/2026

Base main PR#8 a04aaa2; final source c70c93e sạch trong checkout build riêng. Baseline331 PASS20:26:45 UTC+7; final test366 PASS20:56:53/package366 PASS20:57:53 (+35: protocol23/client7/server5). Tổng protocol23/client227/server115/spike1. Python9 PASS riêng; 3 concurrent cases chỉ bổ sung assertion không tăng test count. Source/script sau build không thay đổi, docs/evidence bổ sung riêng. [Evidence C4](../evidence/t1-c4/2026-10-05-verification.md) và transcripts/hash/raw/summary cùng thư mục.

| Kiểm tra | Kết quả thật | Giới hạn |
|---|---|---|
| Toàn JSON UTF-8 ASCII/Vietnamese/emoji, TX success/failure, retry cùng event | PASS MOCK | Đúng chuỗi serialize/send, ticket terminal1, ACK nghiệp vụ riêng không thêm byte |
| RX fragmented/surrogate/malformed/binary/oversized | PASS MOCK | Full text1; INVALID lọc ID/cause, UNMEASURED nullablebyte; demand/rejection giữ |
| Server ACK/ERROR/warning/presence, decorator enqueue/delegate/race | PASS MOCK + REAL | Byte exact socket; latch enqueue2 chưa write2, release đo đúng2 |
| Counters concurrent, bounded queue/drop, file failure/timeout/flush/disabled | PASS MOCK | Counter độc lập raw; logger hỏng vẫn event/ACK; daemon + bounded close không giữ JVM |
| Summary known trace/hand calc/reproducibility/schema/drop/truncation | PASS MOCK | 220byte =2×100+20, không TX/RX/outcome đếm đôi; 9 Python tests |
| PG18.6/Spring/HTTP/WS/B2 candidate+proctor/dashboard model | PASS REAL | TEST schema/port, cả7types log; nguồn event/overflow MOCK, không GUI |
| ProcessHandle collector production scan/stop | PASS REAL | Windows đọc processesScanned>0 riêng; không dùng MOCK làm bằng chứng process thật |
| Retry + gap overflow | PASS MIXED | ACK observer C3 suppression SIMULATED sau B2 accepted; DB1/dashboard1 trước overflow, capacity1/drop2/gapACK REAL |
| Raw3file/FINAL khỏe/26category/summary regenerated | PASS REAL | TX37message12568byte mỗi outcome; JSON/CSV hash tái tạo byte-identical, raw untouched |
| B3 hồi quy | PASS REAL | C3/event/presence/dashboard, owned hard-kill, stale/reconnect/history, 403/401/cleanup; source MOCK; GUI NOT RUN |
| Worker/TEST schema/security/JAR/protected/source checks | PASS | TEST schemas0, public vẫn V3; no live secret/private path/payload, JAR không test/harness; TRACKER/01–03/nguon/migrations không đổi |
| Human B review/GUI toàn luồng/LAN/WMI/ETW/E1/E2/full-delta | NOT RUN | C4 chuẩn bị công cụ đo, chưa nghiệm thu toàn MT01/MT08 hay thử hiệu năng |

Lượt exploratory phát hiện assertion/control-record và compile signature/constant trong test, đã sửa trước final PASS. Final sạch trên commit c70c93e; không lấy exploratory làm evidence. Automatic review chặn dọn bản sao target tạo thừa; giữ ignored, không retry xóa. Dừng C4; không B4/chặng2, không claim novelty/delta tốt hơn.
