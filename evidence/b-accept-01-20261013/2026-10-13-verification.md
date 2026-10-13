# Vai B — nghiệm thu GUI cuối cho B-ACCEPT-01, 13/10/2026

> **Bản đã biên tập để công khai.** Kết quả và nhận định giữ nguyên như lúc ghi; chỉ che tên tiến trình và PID của máy thử. Chi tiết ở `REDACTION.md`. Lỗi P1 nêu ở mục 6 đã được sửa sau đó; xem `../b-accept-01-fix-20261013/`.

**Kết quả: 12/12 ca GUI PASS trên JavaFX production với server JAR và PostgreSQL thật. Phát hiện 1 lỗi P1 mới ở client, nằm ngoài 12 ca (mục 6). B-ACCEPT-01 đạt đủ các bước kiểm; quyết định đóng thuộc về người review.** Không push, không merge PR/main, không bật delta production, không triển khai Listening hay T2-B5.

Mọi thao tác GUI do Agent điều khiển bằng Windows UI Automation. Đây không phải kiểm thử tay của thành viên; review A→B và thành viên tự thao tác vẫn **NOT RUN**.

## 1. Source đã chạy

- **Commit đã build, test và chạy GUI:** `6816ff54fb123d10b551b93dbda92ad7622d5626` trên nhánh cục bộ `integrate/b-accept-01-20261013`, worktree `E:/Project/Toeic-Monitoring-Realtime-b-accept01`. Commit bằng chứng sau đó không đổi source.
- **Hai phía được ghép:** `integrate/t2b-acceptance-20261012` tại `6381ddf` (client B, gồm `770c896`) và `fix/backend-candidate-attempt-history-20261012` tại `7bdac52` (A: `main` `ee714fc` với T3-C1/C2/C3, ba fix P0, API lịch sử).
- **Xung đột:** 8 file, không file nào là code production: 6 tài liệu, `.gitattributes`, `ExamRuntimePostgresTest.java` (lấy bản A, là tập cha của bản B).
- **Sau merge:** code production của `server/` và `protocol/` trùng khít nhánh A-history. Ở client, ngoài các file monitoring của C, chỉ `RealtimeClient.java` nhận thay đổi T3-C2 từ `main`. Delta vẫn tắt trừ khi đặt `toeic.realtime.delta-experimental`; không JVM nào trong lượt chạy được truyền cờ này.
- **Mã quyết định:** QD-17, QD-18 của C và QD-19 của A giữ nguyên. Ba mục trùng số của phía B đổi thành QD-20 (mất WebSocket), QD-21 (deadline khi nhận quyền ghi), QD-22 (`writerEpoch` khi vào thi). Tài liệu hiện hành đã đổi theo; bằng chứng và nhật ký cũ giữ mã lúc ghi.

## 2. Môi trường

Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, JavaFX 21.0.12, PostgreSQL 18.6 trong container `toeic-db` (Docker 29.4.3).

- Test Maven và smoke tự tạo rồi xóa schema riêng (`t2a_p0_*`, `t2b_smoke_*`).
- Server của lượt GUI chạy trên schema tạm `t2b_accept01_fb82713c6d`, cổng loopback 18082; Flyway chỉ chạy trong schema đó. Schema đã xóa sau khi chạy; `public` không bị đụng tới, không reset DB.
- Relay loopback 18083 → 18082 chuyển tiếp nguyên byte, không tự sinh response. Ca mất WebSocket đóng đúng socket WS qua relay; đây là lỗi do Agent chủ động gây, không phải mất mạng LAN thật. Mã nguồn: `harness/relay.ps1`.
- Tài khoản `candidate1`, `candidate2`, `proctor1` là seed `MOCK` của môi trường phát triển. Đề 10 câu là đề mẫu `MOCK` do nút của giám thị tạo.

## 3. Kiểm thử kỹ thuật

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn test`, `TOEIC_PG_INTEGRATION=true` | **PASS 695**, 0 FAIL, 0 ERROR, 0 SKIP (protocol 43, client 479, server 172, spike 1) | `mvn-test-summary.txt` |
| `mvn package`, cùng cấu hình | **PASS 695**, 5 module SUCCESS | `mvn-package-summary.txt` |
| `ExamRuntimePostgresTest` (tính trong 695) | **PASS**, gồm 5 test của API lịch sử và route lạ | các dòng `PASS candidate history…` |
| `ExamFlowPostgresSmoke` | **PASS 28, 0 GAP**, 0 FAIL | `exam-flow-smoke.txt` |

Không chạy lại trong lượt này: các smoke monitoring của C (C1, C2, CLI T3-C1/C2/C3) và bộ Python. Chúng không được tính là PASS mới.

## 4. Mười hai ca JavaFX production

Client chạy từ `client-0.1.0-SNAPSHOT-all.jar`, server từ `server-0.1.0-SNAPSHOT.jar` của đúng commit trên. PASS chỉ áp dụng cho localhost trên máy build. Ảnh nằm trong `screens/`, dòng thời gian thao tác trong `ui-actions.txt`.

| # | Ca | Kết quả | Quan sát và bằng chứng |
|---|---|---|---|
| 1 | Candidate/proctor đăng nhập hợp lệ | **PASS** | `proctor1`, `candidate1`, `candidate2` vào đúng màn theo role, WS `Đã kết nối`. `01`, `03`, `35` |
| 2 | Proctor tạo đề và ca mẫu | **PASS** | Ca 900 giây (`SESSION-BENCHMARK-6980`) và ca 40 giây (`…-3007`), mỗi ca 2 lượt. `02`, `26` |
| 3 | Reading chọn/đổi/bỏ đáp án | **PASS** | Câu 6: B → A → bỏ → B; số câu đã chọn 1 → 0 → 1; trang nhóm 9–10 có đoạn văn. DB khớp từng bước. `06`, `08`, `09` |
| 4 | Autosave chỉ thành công khi có ACK | **PASS** | 120 ms sau khi chọn: "Chưa lưu — đang chờ gửi revision 1", DB còn `rev=0`; sau ACK: "Đã lưu (server xác nhận revision 1)", DB `rev=1`. `05`, `06` |
| 5 | Đồng hồ chạy trước đáp án đầu | **PASS** | Vào thi: epoch 2, 0 đáp án, revision 0, đồng hồ `00:14:57`; DB có `deadline_at` ngay lúc vào. Ca 40 giây: `00:00:37` khi chưa chọn gì. `04`, `27` |
| 6 | Nộp bài và điểm từ server | **PASS** | Sau khi bật lại server, bấm Thử lại gửi đúng bài revision 9: hộp thoại "SERVER ĐÃ GHI NHẬN BÀI NỘP", 2/10 (Reading 2). DB `SUBMITTED`, `score=2`, 1 dòng submit. `24` |
| 7 | Bài trắng tự `TIMED_OUT`, 0 điểm | **PASS** | Ca 40 giây, không chọn, không lưu, không nộp: scheduler của server tự chốt, hộp thoại "HẾT GIỜ — SERVER ĐÃ CHỐT BÀI" 0/10. DB `TIMED_OUT`, 0 dòng autosave, 0 dòng submit. `27`, `28` |
| 8 | Đóng/mở app và phục hồi | **PASS** | **Lượt ACTIVE:** đóng JVM, mở JVM mới, vào lại: epoch 4, revision 5 và đáp án cũ, `deadline_at` không đổi (`18`). **Lượt đã chốt:** đóng hẳn JVM, mở JVM mới, tab lịch sử liệt kê cả `SUBMITTED` và `TIMED_OUT`, mở được kết quả từng lượt (`31`, `32`, `33`). Lần trước ca này FAIL ở nhánh lượt đã chốt. Xem mục 5 và lỗi ở mục 6 |
| 9 | Mất WS, reconnect, đồng bộ | **PASS** | Relay đóng socket WS: `REALTIME: RECONNECTING`, lựa chọn bật 0/8, nhãn lưu giữ "revision 5"; sau reconnect: `CONNECTED`, 8/8, epoch và revision không đổi. `13`, `14`, `ws-relay.txt` |
| 10 | Server tắt, không báo lưu/nộp sai | **PASS** | Dừng server: lựa chọn 0/4, nhãn lưu giữ ACK cũ. Bấm nộp: "Đang nộp bài…" rồi "Bài CHƯA được coi là đã nộp", không có hộp thoại kết quả; DB vẫn `ACTIVE`. `20`, `21`, `23` |
| 11 | Hai cửa sổ, takeover, chặn writer cũ | **PASS** | Cửa sổ thứ hai nhận epoch 3. Cửa sổ cũ chọn thêm đáp án: báo bị thay phiên ghi, 0/4 lựa chọn, nút nộp tắt; DB giữ `rev=5` và đáp án cũ. `15`, `16` |
| 12 | Collector policy → cảnh báo proctor | **PASS** | Collector production (`ProcessHandle`) báo ba tên tiến trình thuộc policy đang chạy sẵn trên máy (tên đã che trong bản này); dashboard hiện 39 process và 41 cảnh báo "Quan sát thấy…", presence `ONLINE`. Không mở hay đóng tiến trình nào của người dùng. `11`, `12` |

## 5. Các bước riêng của B-ACCEPT-01

| Bước | Kết quả | Bằng chứng |
|---|---|---|
| Đăng nhập, tạo ca, nộp bài `SUBMITTED` | PASS | ca 2, 6; `db-final.txt` |
| Ca ngắn, scheduler tự `TIMED_OUT` | PASS | ca 7 |
| Đóng hẳn JVM | PASS | `ui-actions.txt`: mọi JVM thí sinh đã thoát trước khi mở JVM mới |
| JVM mới, không cache, đăng nhập lại | PASS | Client không ghi gì ra đĩa; JVM `c1-jvm4-history` nối thẳng server |
| Tab lịch sử có cả hai lượt | PASS | "Đã tải 2 kết quả do server liệt kê", hai dòng `TIMED_OUT` và `SUBMITTED` (`31`) |
| Xem kết quả, không takeover, không khởi động collector | PASS | Hộp thoại 2/10 và 0/10 đúng với DB. `db-before-history.txt` và `db-after-history.txt` giống hệt: epoch, revision, số event và presence không đổi; số dòng autosave/submit không đổi; màn thi không mở |
| Thí sinh khác không thấy, không đọc được | PASS | Tab lịch sử của `candidate2` có 0 dòng (`36`). Qua HTTP: `candidate2` đọc status và đề của hai lượt đó nhận 403; danh sách của `candidate2` chỉ có lượt của mình (`api-probes.txt`) |
| Monitoring vẫn ACTIVE-only | PASS | `auth/me` của `candidate1` trả scope rỗng; proctor đọc event của lượt đã chốt nhận 403, của lượt `ACTIVE` nhận 200; roster giám thị chỉ còn 2 lượt `ACTIVE` (`37`); takeover trên lượt đã chốt nhận 409 `INVALID_STATE` |

## 6. Lỗi mới phát hiện (P1, ngoài 12 ca, chưa sửa)

**Làm mới danh sách lượt thi khi chưa chọn lượt nào thì callback ném `NullPointerException`.**

- **Vị trí:** `ToeicClientApplication.java:330`, `scope.attemptScope().contains(selected)`. `attemptScope` là `Set` bất biến và `selected` là `null` khi ô chọn lượt đang rỗng.
- **Khi nào gặp:** (a) thí sinh không có lượt `ACTIVE` nào đăng nhập — màn tự làm mới ngay lúc mở; (b) danh sách đang rỗng, giám thị tạo ca mới, thí sinh bấm "Làm mới danh sách ca thi".
- **Hậu quả:** dòng trạng thái kẹt ở "Đang kiểm tra quyền với server…". Ở (b) nút vào thi tắt hẳn dù server đã cấp lượt mới; bấm làm mới lần nữa vẫn lỗi. Cách thoát: đăng xuất rồi đăng nhập lại.
- **Phạm vi:** lỗi có sẵn ở client của B, không do lần ghép này. Tab lịch sử không bị ảnh hưởng.
- **Bằng chứng:** `client-c1-jvm3-npe.txt`, `client-c1-jvm4-history-npe.txt`, `screens/26b-refresh-stuck-npe.png`, `screens/34-active-tab-status-stuck.png`.
- **Vì sao chưa sửa:** yêu cầu của lượt này là báo trước khi sửa code production ngoài phạm vi tích hợp.

## 7. Thao tác ngoài script

Từ 17:01:58 đến 17:02:03 UTC, server ghi nhận 4 lần autosave của câu 6 (`C → B → A → B`, revision 6–9) mà script không gửi (`autosave-log.txt`). Lúc đó cửa sổ thử nghiệm đang ở foreground vì bước đăng nhập phải đưa cửa sổ lên trước để gõ mật khẩu, nên nhiều khả năng đây là thao tác chuột thật trên máy. Đáp án sau cùng trùng trạng thái trước đó (`R1=B`, `R4=D`); bài nộp ở ca 6 vì vậy mang revision 9 thay cho 5. Không ca nào dựa vào các revision này để kết luận.

## 8. Chưa chạy trong lượt này

- Thành viên tự thao tác GUI; máy Windows thứ hai và LAN; review A→B.
- GUI cho xung đột cùng revision, phiên bị thu hồi (401), nộp bài khi mất response: đã chạy ngày 09/10 trên bản cũ, không chạy lại trên `6816ff5`.
- Phân trang lịch sử trên GUI (nút "Tải thêm lượt thi") với hơn 50 lượt: mới có test PostgreSQL và kiểm chéo HTTP phía A.
- Các smoke monitoring của C và bộ Python.
