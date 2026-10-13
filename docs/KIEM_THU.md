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
| MT01 | Full snapshot hợp lệ; event không lặp mỗi poll; collector không chạy trên giám thị | C / B | T1-C2/C3, T2-C1/C2 | PARTIAL — C1/C2 kỹ thuật + process/proctor component REAL PASS; candidate GUI/LAN chưa đủ | C2 `fdac531`, source/JAR manifest | `evidence/t2-c2/2026-10-10-verification.md`; C1/ProcessHandle/Edge/gate/full separation hồi quy mới PASS |
| MT02 | Replay cùng message sau mất ACK | C / B | T3-C3/C4 | PARTIAL — LOCAL production ACK observer-loss/frozen retry/no duplicate touch/oldACK PASS; fault SIMULATED, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT03 | Thiếu một delta → UNSYNCED, yêu cầu full | C / B | T3-C3/C4 | PARTIAL — LOCAL REAL RESYNC parser/callback/fresh FULL/last-known/GUI recovery PASS; future-base SIMULATED, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT04 | Delta epoch cũ không sửa state epoch mới | C / B | T3-C3/C4 | PARTIAL — LOCAL restart/oldsocket oldepoch rejected PASS + C3 delta oldepoch replay; LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT05 | Restart collector hoặc server → cần epoch và full mới | C / B | T3-C3/C4 | PARTIAL — REAL owned candidate kill/server restart/new epoch/full + revoke/capability loss PASS; one host, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT06 | Không lùi state; full và delta cho cùng state và event set | C / B | T3-C3/C4 | PARTIAL — independent root metadata/stateHTTP-push/eventID-content SQL + C3 full/delta oracle PASS; backgroundOS not frozen, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT07 | Event muộn, queue tràn; snapshot mới không xóa gap | C / B | T2-C2 | PARTIAL toàn luồng — late/dedup/conflict/gap/history/full-isolation/proctor component kỹ thuật PASS; candidate GUI/LAN NOT RUN | `fdac531` | `evidence/t2-c2/2026-10-10-verification.md`; REAL PG/HTTP/WS/collector worker, readings MOCK/faults SIMULATED; raw/ảnh riêng |
| MT08 | Kill client → server tự chuyển UNKNOWN | C / B | T2-C2 | PARTIAL toàn luồng — hard-kill A4/B3/proctor component và C2 tự UNKNOWN/reconnect/history mới PASS; candidate GUI/LAN NOT RUN | C2 `fdac531`, nguồn/JAR trong metadata | `evidence/t2-c2/2026-10-10-verification.md`; kill JVM sở hữu REAL riêng, không gọi ngắt socket mô phỏng là kill |

## Listening và tích hợp

Full-only retry/epoch/order/reconnect đã kiểm trong acceptance T2-C1 bên dưới. Không đổi MT02–MT06 của delta thành PASS và không coi full/C4 log là E1/E2.

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| LT01 | Thiếu audio hoặc sai checksum → không READY | B / A | T3-B1 | PARTIAL — gate backend T3-A1 PASS; checksum file local/GUI/package B1 chưa chạy | Working tree T3-A1 trên main | `evidence/t3-a1/2026-10-13-verification.md` |
| LT02 | Start lặp không phát lại; lỗi media khóa và ghi gián đoạn | B / A | T3-B2/B3 | PARTIAL — backend T3-A2 PASS; media/GUI B chưa chạy | Nhánh feat/t3-a2-listening-lifecycle | `evidence/t3-a2/2026-10-13-verification.md` |
| LT03 | Mất mạng giữa audio; mở lại không tự phát hay resume | B / A | T3-B3 | PARTIAL — backend timeout/takeover/restart PASS; audio/reconnect GUI B chưa chạy | Nhánh feat/t3-a2-listening-lifecycle | `evidence/t3-a2/2026-10-13-verification.md` |
| LT04 | Tổ chức lại: attempt mới, dữ liệu và deadline cũ giữ nguyên | B / A | T3-B4, T3-A2 | PARTIAL — phần server PASS; giao diện B4/LAN chưa chạy | Nhánh feat/t3-a2-listening-lifecycle | `evidence/t3-a2/2026-10-13-verification.md` |
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

## Acceptance T2-C2 — 10/10/2026

Code `fdac531`, base main683adfc sau PR#18. Java425/package PASS (protocol32/client250/server142/spike1), Python10 PASS chạy mới. C2 Spring/PostgreSQL18.6/HTTP/WS/collector worker/proctor Stage thật PASS: tự UNKNOWN không gọi scan bằng tay, B vẫn ONLINE/event ACK, reconnect ONLINE và interruption/recoveredAt giữ nguyên; event từ socket cũ/offline có nhãn đúng, retry DB1/warning1, đổi payload CONFLICT, LIVE retry giữ metadata lần đầu, client date2099 không suy ra delay. Late event không thay full mới; full giữ lịch sử event/gap/interruption. Queue1/drop2/gap retry một dòng, HTTP/GUI đọc đúng; deferred event COMMIT rollback không ACK/warning giả, retry thành công; legacy/V7→V8, role/foreign/CLOSED/SUBMITTED/TIMED_OUT/assignment/token revoke và cleanup PASS.

Inputs process MOCK, outage/write/ACK loss SIMULATED có nhãn; transport/DB/timeout worker REAL. A3/A4/C3/C4/B3-Gui/C1-Gui hồi quy mới PASS, gồm owned JVM hard-kill REAL, ProcessHandle/owned Edge REAL, socket+heartbeat sống không full/CLOSE/HTTP read/manual maintain → tự STALE/TTL4000ms/capacity1 và shutdown dọn RAM/worker. Không bật scheduling toàn app. Raw cuối6endpoint/63category COMPLETE/drop-unwritten-pending0, metadata clean fdac531, JSON/CSV regenerated byte-identical. [Evidence mới](../evidence/t2-c2/2026-10-10-verification.md) có negative test trên main cũ và các lỗi exploratory đã sửa, ảnh, transcripts, configs, checksum.

MT01/MT07/MT08 giữ PARTIAL toàn luồng do candidate GUI/LAN chưa chạy; human A/B review NOT RUN. Không làm C3–C5/delta/E1/E2 hoặc cập nhật tracker. Contract mới QD-13 dùng nguồn kết nối, không wall-clock delay; legacy/trước heartbeat ACK đầu UNSPECIFIED; LIVE chỉ cùng socket. Gap mới đọc bằng refresh, không push riêng.

## Acceptance T2-C1 — 10/10/2026

Base `8eba405`, code `6acd031`; Windows11/Temurin21.0.10/PostgreSQL18.6/Maven3.9.15/Python3.15.0b3. Agent chạy. Baseline trước sửa Java376/A2 PASS; sau sửa Java408/package/Python10 PASS. A2/A3/A4/C2/C3/C4/B3 và B2 riêng trên owned TEST DB PASS. B2 wrapper DB dev có lỗi tiền điều kiện scope rỗng; không xóa fixture để làm test qua. A4/B3 harness hỗ trợ migration mới và B3 tab mới; C3 ERROR phải correlate request gap.

T2-C1 REAL HTTP/WS/PG/ProcessHandle/owned Edge/proctor JavaFX component PASS: full1, Edge xuất hiện/biến mất giữ event, reconnect/new epoch/old epoch STALE, retry/conflict, full rỗng, role/scope, CLOSE/STale, history gap/interruption còn và dọn workers. Fault process sets MOCK; queue/scan failure/order/TTL/late callback/canceled socket write bằng MOCK unit. 4file measurement FINAL, drop/unwritten/pending0, summary COMPLETE. Chi tiết, lỗi ban đầu đã sửa, configs thực và ảnh: [evidence](../evidence/t2-c1/2026-10-10-verification.md).

Bổ sung review-fix cùng ngày: tại30fa6d9, maintain Scheduled không được Spring gọi; các test PASS cũ không chứng minh autonomous STALE/TTL. Harness mới trên implementation cũ FAIL phase AUTO_STALE_WITHOUT_HTTP_OR_CLOSE. Code e07bb7e dùng worker riêng; Java413/package PASS (protocol29/client242/server141/spike1). REAL Spring/PG/WS giữ socket và scoped heartbeat, không full/CLOSE/HTTP state read/manual maintain: tự push STALE, TTL4s tombstone, capacity1 từ OPEN B bị từ chối đến ACK B và giữ history PASS. Spring close xóa RAM/dừng worker PASS. A4/C3/C4/B3 hồi quy chạy lại PASS. [Evidence bổ sung](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md); Python10 thuộc lượt trước, không chạy lại unittest Python vì script phân tích không đổi (summary mới vẫn chạy thật).

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

## T2-C3 — 10/10/2026

[Evidence mới](../evidence/t2-c3/2026-10-10-verification.md), [lệnh và schema](MONITORING_TRACE.md). Baseline425 chạy mới; final461 PASS (+36, protocol32/client286/server142/spike1), Python10 PASS. Không đổi trạng thái MT02–MT06 vì chưa delta; MT01/MT07/MT08 giữ PARTIAL toàn hệ thống.

| Ca C3 | Kết quả | Mức |
|---|---|---|
| Handwritten empty→Edge→Edge→empty: state0/1/1/0, event1 | PASS | MOCK + production REPLAY |
| Roundtrip/empty versus error/PID reuse/missing metadata/nanosecond identity | PASS | MOCK |
| Reader checksum/missing/corrupt/UTF8/field/version/size/order | Reject đúng | MOCK |
| Writer error/full queue/close/tap failure isolation | PASS, không COMPLETE giả | MOCK latch/failure |
| Duplicate/drop/reorder/reconnect/old epoch/history separation | PASS | REPLAY + SIMULATED |
| Same seed/config/input | Byte-identical report PASS | CLI thật |
| Intentional full mutation/corrupt checksum | FAIL exit1/reject exit2 đúng | CLI thật, nguồn MOCK |
| ProcessHandle capture + owned Edge appear/disappear/cleanup | COMPLETE→replay PASS | Nguồn REAL, gate/fault SIMULATED |
| C1-Gui/C2-Gui/event-C3/C4/A4 | PASS mới | REAL localhost/PG/component, source/fault từng ca xem evidence |
| B human review/A adapter review/candidate full GUI/LAN/T2-C4/delta/E1/E2 | NOT RUN | Không lấy replay thay bằng chứng này |

## T2-C4 — harness và dữ liệu chuẩn độc lập (10/10/2026)

Java493/package PASS; 32 ca mới có expected viết tay và child thật. [Evidence/logs/raw/example/checksums](../evidence/t2-c4/2026-10-10-verification.md); [lệnh](CONTROLLED_PROCESS_HARNESS.md). Không đổi MT02–MT06 delta hoặc E1/E2 thành PASS.

| Kiểm tra mới | Kết quả | Mức bằng chứng |
|---|---|---|
| Same seed/manual seed7/ba loại/giới hạn | PASS | Unit schedule/expected viết tay |
| GT độc lập khi collector luôn empty | PASS | Child REAL, collector readings MOCK |
| Clock offset/thứ tự/không ép target bằng thực đo | PASS | Fixture viết tay + audit raw REAL |
| PID/start/collector, reuse, thiếu start/tên/coverage | PASS | Fixture MOCK riêng |
| Trace lỗi/truncated/hash/mutation/existing output | PASS reject/inconclusive | Unit + CLI bản sao lỗi |
| Child lỗi/noREADY/hang/launchfail/APIcancel/known tree | PASS cleanup | Child REAL + source MOCK |
| Windows30child (10 mỗi200/800/3000), poll500/seed1234 | COMPLETE 27 observed/3 notObserved/0 inconclusive | Child+source REAL, TEST policy; gate SIMULATED |
| Checksum/manifest/rejoin/cleanup/no overwrite | PASS | Raw và CLI thật |
| C3 replay/REAL owned Edge trace, C1-Gui, measurement | PASS | Hồi quy riêng; faults/gate MOCK/SIMULATED theo evidence |
| Event lostACK retry/network | Initial concurrent FAIL; isolated PASS | Hai log retained, cause chưa chứng minh |
| Human B review/OS Ctrl+C/hardkill/candidate full GUI/LAN/E1/E2 | NOT RUN | Không giả bằng unit/replay/smoke |

C2-Gui/A4 không chạy lại trong C4; server production không đổi. Python analyzer không đổi, không chạy lại bộ Python10 trong C4. B review policy/TEST DTO/clock/identity/coverage/cleanup.

## T2-C5 — kiểm tra phương pháp và protocol (11/10/2026)

C5 là thay đổi tài liệu. [Phương pháp](MONITORING_BASELINE_METHOD.md), [kiểm tra mới](../evidence/t2-c5/2026-10-11-verification.md). Link/anchor/source method/schema/default và C3/C4 artifact hashes đối chiếu mới; số chi tiết trong doc-checks.json. Không coi các kiểm tra file này là chạy lại test Java493/Python10 hoặc component smoke.

| Kiểm tra/điều kiện | Trạng thái | Phạm vi |
|---|---|---|
| Source policy8tên/TEST/identity/ACK/RAM/default | Đối chiếu PASS | Không đổi code/wire |
| Markdown local link/anchor, class/method/schema/CLI | PASS | Tài liệu thay đổi |
| C3/C4 hashes, FINAL/manifest, gzip phục hồi | PASS | Đọc artifact cũ, không replay lại |
| C3 PASS có missingBusinessEvents/C4 số27/3 và proxy example | Đối chiếu PASS | Kết quả cũ, không E1 |
| Java/Python analyzer unit/desktop smoke | NOT RUN trong C5 | Kết quả cũ giữ riêng |
| B human review/window/sampler/analyzer E1/delta correctness/E2 | NOT RUN hoặc còn thiếu | Không đánh dấu đủ công cụ/nghiệm thu |

MT01/MT07/MT08 giữ PARTIAL toàn hệ thống; MT02–MT06 delta và E1/E2 không nâng PASS. Merge tài liệu không thay review của B hoặc nghiệm thu GUI/LAN.

## Backend P0 unblock PR17 — 11/10/2026 (local, chưa merge)

Nhánh A từ main12e2838: Maven test/package510 PASS, tích hợp tạm PR17 package582 PASS, đều bật11 regression PG thực và không skip. Native PostgreSQL18.1 cluster test riêng; không reset DB dev. Ba GAP B: owner/assigned proctor đọc SUBMITTED/TIMED_OUT, scheduler tự chốt và deadline bắt đầu ngay takeover entry **PASS kỹ thuật**. B smoke JAR/PG/HTTP28 PASS,0GAP; C1 full/reconnect/autonomous STALE/TTL và C2 late/gap/history/permission hồi quy PASS; Python10 PASS.

[Evidence P0 mới](../evidence/t2-a-p0/2026-10-11-verification.md) ghi source/hash, logs, fixture patch và lỗi exploratory. Không sửa các evidence lịch sử B/C hoặc TRACKER. T2-B3/B4 vẫn PARTIAL; human review, full GUI candidate/proctor, server-off/reconnect, LAN/app-image/audio máy thứ hai NOT RUN. E1/E2 không thay trạng thái. Remote PR17 chưa có commit A cho đến khi được phép push và tích hợp.

## T3-C1 — encoder delta candidate (11/10/2026)

[Guide](MONITORING_DELTA_ENCODER.md), [evidence](../evidence/t3-c1/2026-10-11-verification.md). Baseline mới493 PASS; final531 Java PASS (protocol35/client353/server142/spike1), 38 mới. Test snapshot/clock/transport MOCK, scheduled worker REAL; CLI thực thi thật nhưng network/ACK/epoch/adapter SIMULATED. Production vẫn full.

| Kiểm tra | Kết quả | Phạm vi |
|---|---|---|
| First full ACK / ACK sai8trường / base chỉ ACK | PASS | DeltaSnapshotDeliveryTest |
| Thêm/bớt/multiple/empty/metadata full/PID reuse/null start/reappear | PASS | Delivery + handwritten oracle/replay |
| Unchanged không event/poll, coalescing1.000→queued1 | PASS | Event thật trong test, readings MOCK |
| SOURCE_FAILURE khác empty, retry nguyên message/ACK loss/write error | PASS | Clock/transport MOCK, lỗi SIMULATED |
| Retry bounded/error-STale/fresh scan/OPEN/full1/reconnect/oldcallback | PASS | Client lifecycle; không server delta/resync proof |
| Reconciliation30s/scan failure/pending/cleanup/auth revoke | PASS | Controlled clock, worker scheduled/shutdown thật |
| Replay full/delta/canonical metadata/event ID-content/reconnect/duplicate | PASS | Fixture MOCK + trace ProcessHandle REAL; tất cả ACK/network SIMULATED |
| Delta/full cố ý sai | FAIL đúng exit1 | Oracle độc lập bắt sai từ raw; giữ report |
| TEST C4/future delta drop options | Refused đúng exit2 | Không đổi schema hoặc giả gate support |
| MT02–MT06 gate/live delta/E1/E2/human A/B review | NOT RUN | Không nâng status27case/tracker từ component tests |

Lượt package đầu mới thêm test ERROR STALE FAIL vì còn deadline ACK cũ; sửa deadline rejection candidate về now, bản sửa529 PASS; bổ sung2ca pending-ACK, final531 PASS. Không lấy lượt lỗi làm PASS. Hồi quy DB đầu dừng MIGRATION do Docker chưa chạy; bật Docker/compose volume cũ rồi chạy lại PASS. Kết quả hồi quy mạng/DB mới ghi trong evidence, không suy từ529unit.

## Backend A sync main T3-C1 — 12/10/2026 (local, chờ push)

Merge A6cfe7c4 + main144fe55 thành cadd98d, giữ P0 và encoder/transport/regression C. Maven test/package **548PASS**, checkout detached PR17+A/main package **620PASS**, đều0fail/error/skip và gồm11PG runtime +38test C mới. Smoke B **28PASS/0GAP**, C1/C2/CLI regression PASS, Python10PASS. Scheduler exam riêng, C tự STALE/TTL/presence và shutdown PASS; bài trắng tự TIMED_OUT0/2. [Evidence mới](../evidence/t2-a-p0-sync/2026-10-12-verification.md) ghi source/code/fixture hashes, logs và giới hạn.

PR23 remote vẫn6cfe7c4, PR17 vẫn2f5fc66; chưa push local sync/chưa merge/human review. B3/B4 vẫnPARTIAL; full GUI/server-off/LAN/app-image/audio máy thứ hai/live delta/gate/E1/E2 NOT RUN. Không thay tracker hoặc evidence lịch sử.

## T3-C2 — reducer/resync/freshness (12/10/2026)

[Guide](MONITORING_DELTA_REDUCER.md), [evidence](../evidence/t3-c2/2026-10-12-verification.md). Baseline531 mới PASS; final554 Java/package PASS (protocol43/client361/server149/spike1), Python10 PASS.23test mới = core6/measurement2/client freshness-resync5/parser2/oracle1/server7. Không nâng gate MT02–MT06 hoặc tracker.

| Kiểm tra | Kết quả | Mức |
|---|---|---|
| Full đầu/delta/base gap/dup/conflict/common cache64/atomic payload/full recovery/old binding | PASS | Pure core + store/mock scope/clock |
| Quyền trước duplicate, TTL không gia hạn bởi duplicate/gap | PASS | Unit + REAL JDBC/WS scope CLOSED/revoked token |
| OPEN/full/CLOSE tranh chấp delta/maintenance | PASS | Latch/order có kiểm soát, không sleep quyết định thứ tự |
| Correlated resync/parser/client fresh full cùng epoch, seq0 newOPEN | PASS | Unit + REAL Spring/PG/HTTP/WS |
| Successful unchanged scan/noop, scan hung/failure HB ONLINE → STALE/TTL | PASS | Worker thật, readings MOCK; không maintain/HTTP ở phase auto |
| Spring server restart → instance/epoch mới/full, DB history retained | PASS | REAL restart same test port, persisted auth |
| Oracle missing-first-full/missing-delta/recovery/old, manual fixture/negative CLI/REAL trace | PASS hoặc FAIL/refusal đúng ca âm | Shared production core, independent raw expected; ACK/network CLI SIMULATED |
| Full-Gui/C2-Gui/event/presence/measurement | PASS mới | REAL components, fault/readings đúng nhãn trong evidence |
| DELTA/RESYNC measurement/analyzer/byte summary | PASS | Known types, counters cùng transport hooks; không E2 |
| Human A/B review/whole gate/C3 campaign/fullcandidateGUI/LAN/E1/E2 | NOT RUN | Không giả approval hoặc prototype acceptance toàn case |

Các lượt lỗi fixture/constructor oracle/manifest/DB chưa chạy được giữ trong evidence và sửa trước final. Không lấy kết quả dương cũ thay lượt mới.

## T3-C3 · Kiểm tra mới12/10/2026

- Baseline554 PASS22:16:59; final569/package PASS22:37:27 UTC+7 (protocol43/client376/server149/spike1),15new ReplayCampaignTest invocations.38focused replay tests PASS sau2lượt lỗi fixture label/comparison; không bỏ lỗi production bằng sửa oracle.
- CLI campaign72cases=3MOCKworkloads ×12scenarios ×2modes và4ca âm detected; Java repeat nội bộ + full CLI lần2 byte-identical. Raw/code/JAR/sourceSha đúng; summary/matrix/checksum sinh từ file thực. UNKNOWN/presence/TTL/ACK-loss/retry/feedback/DB trong replay NOT MODELED.
- Ca gap giữ last-known/UNSYNCED rồi full định kỳ hoặc newOPEN/full1 phục hồi; duplicate sau RESYNC không tự SYNCED. Event drop vẫn missingBusinessEvents=1, full không sửa history. Intentional delta/full/event mutations oracle FAIL/CLIexit1, bad config/checksum/TEST/nooverwrite exit2.
- C3 script -RealScan PASS; real ProcessHandle trace47business/40delivered/7missing trong seeded cảmode. C1 CLI regression -RealScan PASS; T2-C3 -RealEdge PASS/owned cleanup, source REAL/replay network SIMULATED,45business/40delivered/5missing trong owned-edge seeded. Không so workloads hai lượt/performance.
- Counterexample compiled against old C2 JAR expected PREVIOUS_CONNECTION actual LIVE FAIL; final actual PREVIOUS_CONNECTION PASS. Chỉ fix replayer receiving connection, không production server/transport/reducer/EventOrigin. First handwritten expected ghi LATE nhầm tên nhãn, sửa theo EventOrigin/PROTOCOL; log nguồn giữ. Seeded reconnect0common checkpoint là fault divergence đúng model, công khai NO_COMMON_SYNC_CHECKPOINT; oracle từng mode vẫn nguyên kiểm.
- Không chạy lại live Spring/PG/WS smokes hoặc Python analyzer vì code liên quan không đổi; kết quả C2 là evidence cũ ghi riêng, không PASS mới. Human B/ChatGPT review NOT RUN theo opt-out; MT02–MT06 toàn gate giữ PARTIAL; T3-C4/GUI/LAN/E1/E2 NOT RUN. [Evidence](../evidence/t3-c3/2026-10-12-verification.md).

- Kiểm cuối exact Git fixture bytes: auto text normalization đã làm manifest/expected blob LF khác workspace CRLF. Commit d813cb1 giữ exact byte; verified package569 PASS22:51:24 và campaign runb46f6a5e-e652-4ec4-8a8b-0201ba5b991a72PASS/4negative/77repeat identical,76case reports byte-identical với earlier d337 run, chỉ summary sourceSHA mới. Final REAL capture46business/40delivered/6missing; earlier47/40/7 giữ riêng, không so performance. JAR không đổi FB05E7D3;243source/blob hashes và222artifact/167gzip PASS. Runtime/doc workingtree ghi đúng dirty filenames; live/Python regressions không chạy thêm.

## T3-C4 local preparation13/10/2026

[Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md). Source d9475eb,579Java/package PASS,42local assertions +7affected smoke commands latestPASS; previousfailures retained. OneWindows host/default actual candidate/proctor Applications/two childJVMs/REALProcessHandle-ownedEdge/PG/HTTP/WS. Fault observer/base/source/DB seams SIMULATED, nophysicalpacketloss measurement. Fulldefault, gate20/10notdecided, humanA/B/LANNOTRUN.

MT01/MT07/MT08 thêm LOCAL actualcandidate/proctor Start/FULLfallback/DELTA/history/push/ownedkill/isolation/cleanup PASS, nhưng toàncase vẫn PARTIAL choLAN/human/fullflow. IT02 LOCAL monitoring subset2candidate+1proctor/scopeforgery/kill/isolation PASS; IT03 sourcepark+heartbeat/UI IPC/worker0 subsetPASS. Không nghiệm thu wholeexam/Listening/packageIT01 hoặc IT04; bảng NOT RUN của toànIT giữ nguyên. Replay oracleC3 vàlocalC4 táchriêng; không nângMT02–MT06 gate/E1/E2 thànhPASS.

## Backend A — PR #23 đồng bộ main T3-C3 và review lại P0, 13/10/2026 (local, chờ push)

Commit `9713658` trên nhánh cục bộ `sync/pr23-main-20261013` = head PR #23 `4753149` + main `ee714fc`. Chi tiết: `evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md`.

| Kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn package` với `TOEIC_PG_INTEGRATION=true` | PASS 586, 0 skip | protocol 43, client 376, server 166, spike 1; gồm 11 test `ExamRuntimePostgresTest` trên PostgreSQL thật |
| Smoke C1 full snapshot, smoke C2 late event/gap | PASS | REAL HTTP/WS/PG trên schema riêng; nhãn MOCK/SIMULATED như script ghi |
| Python analyzer | PASS 10 | Python 3.14; lượt đầu bằng Python 3.9 trên PATH lỗi do phiên bản, không tính |
| Review lại ba sửa P0 (đọc sau khi chốt, scheduler hết giờ, deadline khi vào thi) | Đạt, 4 ghi nhận không chặn | Agent review; human review NOT RUN |
| `ExamFlowPostgresSmoke`, GUI, LAN | NOT RUN trên nhánh này | chạy ở nhánh client của B |

Không đổi trạng thái AT/MT/LT/IT trong bảng chính; `TRACKER.json` giữ nguyên.

### Backend A — PR #23 đồng bộ lần hai với main T3-C4, 13/10/2026 (local, chờ push)

Main tiến lên `cb30f3d` (PR #27) trong lúc làm, nên nhánh `sync/pr23-main-20261013` được merge thêm một lần: commit `c64503f`. `mvn package` với `TOEIC_PG_INTEGRATION=true`: **PASS 596**, 0 skip (protocol 43, client 386, server 166, spike 1; 11 test PostgreSQL thật). Smoke C1, C2 PASS; Python 10 PASS. Smoke T3-C4, GUI, LAN, human review: NOT RUN. Bảng 586 ở mục "PR #23 đồng bộ main T3-C3" là của commit `9713658` trước đó. Chi tiết: `evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md`.

## T3-C5 · Kiểm tra bản nháp tài liệu · 13/10/2026

Đối chiếu source main `f97c081`, class/method/defaults/quyền monitoring, liên kết và anchor, bảng evidence C1–C4, phạm vi diff. Kết quả mới xem [báo cáo kiểm tra](../evidence/t3-c5/2026-10-13-verification.md); không chạy lại Java/Python runtime/smoke/GUI/LAN/E1/E2. Các số PASS trước đó giữ task/SHA/môi trường gốc, không gán cho main mới. FULL mặc định, gate NOT_DECIDED/PARTIAL; B review NOT RUN. Không nâng trạng thái MT toàn hệ thống.

## B-ACCEPT-01 phía A — API lịch sử lượt thi (12/10/2026)

SHA `33a2932` trên nhánh cục bộ `fix/backend-candidate-attempt-history-20261012` (base `main` `ee714fc` + 5 commit P0), Windows 11, JDK 21.0.8, PostgreSQL 18.6. Chi tiết: `evidence/t2-a-history/2026-10-12-verification.md`.

| Kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn test`, `mvn package` với `TOEIC_PG_INTEGRATION=true` | PASS 592/592 | protocol 43, client 376, server 172, spike 1; bản Codex để lại (`e81961e`) là 590/590 |
| Endpoint lịch sử: chủ sở hữu, thí sinh khác, role, phiên thu hồi, phân trang, whitelist trường | PASS REAL | Spring runtime + HTTP + PostgreSQL thật, schema riêng tự xóa; 4 test |
| Route API không tồn tại | PASS | 404 `INVALID_INPUT`, không còn 500 |
| Client B `770c896` gọi server nhánh này | PASS REAL 13/13 | HTTP + PostgreSQL thật, không GUI; `SUBMITTED` và `TIMED_OUT` thật |
| GUI production của B, review C→A, LAN | NOT RUN | B-ACCEPT-01 chưa đóng cho tới khi B chạy lại GUI |

Không đổi trạng thái AT01–AT11 hay IT01–IT04; `TRACKER.json` giữ nguyên.

### API lịch sử xếp lại trên PR #23 đã đồng bộ — 13/10/2026

Nhánh cục bộ `feat/backend-candidate-attempt-history-20261013` tại `19f669e`: ba commit lịch sử đặt trên `sync/pr23-main-20261013`, không lặp commit P0. `server/src/main` và `protocol/` trùng từng byte với `7bdac52`. `mvn package` với `TOEIC_PG_INTEGRATION=true`: **PASS 602**, 0 skip (protocol 43, client 386, server 172, spike 1). Quyết định lịch sử mang số QD-23 vì main đã dùng QD-19. Chi tiết: `evidence/t2-a-history/2026-10-13-restack.md`. GUI, LAN, review C→A: NOT RUN.
