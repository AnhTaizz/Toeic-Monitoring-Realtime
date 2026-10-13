# Vai B — sửa lỗi P1 làm mới danh sách lượt thi và nghiệm thu lại, 13/10/2026

**Kết quả: lỗi P1 đã sửa; 5/5 kịch bản hồi quy và 12/12 ca GUI PASS trên JavaFX production với server JAR và PostgreSQL thật, đều chạy lại trên commit đã sửa.** Không kế thừa PASS của lượt trước. Không push, không merge, không bật delta production, không đụng backend.

Mọi thao tác GUI do Agent điều khiển bằng Windows UI Automation. Thành viên tự thao tác và review A→B vẫn **NOT RUN**. Đây là bản đã biên tập để công khai; xem `REDACTION.md`.

## 1. Lỗi và cách sửa

- **Triệu chứng:** thí sinh không có lượt `ACTIVE` đăng nhập thì dòng trạng thái kẹt ở "Đang kiểm tra quyền với server…"; sau khi giám thị tạo ca mới, bấm làm mới vẫn không vào thi được cho tới khi đăng nhập lại.
- **Root cause:** callback làm mới trong `ToeicClientApplication` gọi `scope.attemptScope().contains(selected)`. `attemptScope` là `Set` bất biến (`Set.copyOf`), còn `selected` là `null` khi ô chọn lượt đang rỗng. `Set` bất biến của JDK ném `NullPointerException` với `contains(null)`: `Set12.contains` khi có 1–2 phần tử, `SetN.contains` khi rỗng hoặc từ 3 phần tử. Ngoại lệ nổ trên luồng JavaFX giữa chừng callback nên trạng thái và nút vào thi không được cập nhật. Stack trace của lượt trước: `../b-accept-01-20261013/client-c1-jvm3-npe.txt`, `client-c1-jvm4-history-npe.txt`.
- **Sửa:** tách quyết định sau khi làm mới ra lớp thuần `AttemptRefresh` (kiểm `null` trước khi tra `Set`, giữ lượt còn quyền, không thì chọn lượt đầu, lỗi mạng thì giữ nguyên danh sách). `ToeicClientApplication` chỉ áp kết quả lên control.
- **File đã sửa:** `client/.../ToeicClientApplication.java` (callback làm mới), thêm `client/.../AttemptRefresh.java` và `client/src/test/.../AttemptRefreshTest.java` (12 test).
- **Không đổi:** scope monitoring ACTIVE-only, API lịch sử, `writerEpoch`, autosave, server, protocol. Hành vi khi làm mới lỗi mạng giữ như cũ: danh sách còn nguyên, nút vào thi tắt cho tới khi làm mới được.

## 2. Source đã chạy

- **Commit đã build, test và chạy GUI:** `b54a8ad539312a6d759d82c101734b162545a668`, nhánh cục bộ `integrate/b-accept-01-public-20261013`. Nhánh này gồm commit ghép `6816ff5` đã nghiệm thu lượt trước và đúng một commit sửa lỗi; worktree sạch lúc chạy.
- Cùng bản sửa cũng nằm ở `301a2e8` trên nhánh cục bộ giữ bằng chứng gốc; source ngoài `docs/` và `evidence/` của hai commit giống hệt nhau.
- Delta vẫn tắt mặc định; không JVM nào được truyền `toeic.realtime.delta-experimental`.

## 3. Môi trường

Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, JavaFX 21.0.12, PostgreSQL 18.6 trong container `toeic-db`. Server GUI chạy trên schema tạm `t2b_accept02_5c0ea4da5c`, cổng loopback 18082, relay 18083 → 18082; schema đã xóa sau khi chạy, `public` không bị đụng tới. Tài khoản và đề đều là seed/đề mẫu `MOCK`.

Khác lượt trước: script không còn đưa cửa sổ thử nghiệm lên foreground khi đăng nhập. Nguyên nhân phải làm vậy ở lượt trước là thông điệp `WM_CHAR` được gửi với số lần lặp bằng 0; sửa thành 1 thì JavaFX nhận ký tự mà không cần foreground.

## 4. Kiểm thử kỹ thuật

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn package`, `TOEIC_PG_INTEGRATION=true`, trên `b54a8ad` | **PASS 707**, 0 FAIL, 0 ERROR, 0 SKIP (protocol 43, client 491, server 172, spike 1) | `mvn-package-summary.txt` |
| `mvn test` và `mvn package`, cùng cấu hình, trên `301a2e8` | **PASS 707** mỗi lệnh | `mvn-test-summary-301a2e8.txt` |
| `AttemptRefreshTest` (tính trong 707) | **PASS 12** | danh sách rỗng, không có lựa chọn (1/2/3/5 lượt), rỗng → có lượt, lượt đang chọn bị thu hồi, lỗi mạng rồi thử lại |
| `ExamFlowPostgresSmoke` trên `b54a8ad` | **PASS 28, 0 GAP** | `exam-flow-smoke.txt` |

Lượt trước có 695 test; 12 test mới là của `AttemptRefreshTest`. Các smoke monitoring của C và bộ Python không chạy lại.

## 5. Hồi quy GUI cho lỗi vừa sửa

| # | Kịch bản | Kết quả | Quan sát |
|---|---|---|---|
| R1 | Thí sinh chưa có lượt nào đăng nhập; giám thị tạo ca; thí sinh bấm làm mới rồi vào thi, không đăng nhập lại | **PASS** | Sau đăng nhập: "Chưa được cấp lượt thi nào…", làm mới lần nữa vẫn vậy. Sau khi tạo ca: ô chọn hiện lượt mới, nút vào thi bật, vào thi được; `candidate1` chỉ có 1 phiên đăng nhập. `r1-01`, `r1-02`, `04` |
| R2 | Nộp bài xong (danh sách rỗng), giám thị tạo ca mới, bấm làm mới — đúng bước đã lỗi ở lượt trước | **PASS** | Ô chọn hiện lượt mới, vào thi được ngay trong cùng phiên. `25`, `r2-01` |
| R3 | JVM mới, thí sinh không còn lượt `ACTIVE` nào | **PASS** | "Chưa được cấp lượt thi nào…", nút vào thi tắt, nút làm mới bật. `30` |
| R4 | Lượt đang chọn bị thu hồi rồi làm mới | **PASS** | Ô chọn chuyển sang lượt còn lại, nút vào thi bật. Lượt bị đóng bằng SQL trên schema tạm. `r4-01` |
| R5 | Làm mới khi server tắt, rồi thử lại | **PASS** | Khi lỗi: giữ nguyên lựa chọn, báo "Không kết nối được tới server…", nút vào thi tắt, nút làm mới bật. Sau khi bật server và bấm lại: bình thường. `r5-01`, `r5-02` |

Không có `Exception` nào trong stderr của cả 7 JVM client của lượt chạy.

## 6. Mười hai ca JavaFX production trên `b54a8ad`

| # | Ca | Kết quả | Quan sát và bằng chứng |
|---|---|---|---|
| 1 | Candidate/proctor đăng nhập hợp lệ | **PASS** | `proctor1`, `candidate1`, `candidate2` vào đúng màn theo role, WS đã kết nối. `01`, `r1-01`, `35` |
| 2 | Proctor tạo đề và ca mẫu | **PASS** | Ca 900 giây và ca 40 giây, mỗi ca 2 lượt. `02`, `26` |
| 3 | Reading chọn/đổi/bỏ đáp án | **PASS** | Câu 6: B → A → bỏ → B; trang nhóm 9–10 có đoạn văn; DB khớp từng bước. `06`, `08`, `09` |
| 4 | Autosave chỉ thành công khi có ACK | **PASS** | 120 ms sau khi chọn: "Chưa lưu — đang chờ gửi revision 1", DB `rev=0`; sau ACK: "Đã lưu (server xác nhận revision 1)". `05`, `06` |
| 5 | Đồng hồ chạy trước đáp án đầu | **PASS** | Vào thi: epoch 2, 0 đáp án, revision 0, `00:14:57`, DB có `deadline_at`. Ca 40 giây: `00:00:37`. `04`, `27` |
| 6 | Nộp bài và điểm từ server | **PASS** | Thử lại sau khi bật server gửi đúng bài revision 5: "SERVER ĐÃ GHI NHẬN BÀI NỘP", 2/10. DB `SUBMITTED`, `rev=5`, 1 dòng submit ở revision 5. `24` |
| 7 | Bài trắng tự `TIMED_OUT`, 0 điểm | **PASS** | Không chọn, không lưu, không nộp; server tự chốt, hộp thoại 0/10. DB `TIMED_OUT`, 0 dòng autosave, 0 dòng submit. `27`, `28` |
| 8 | Đóng/mở app và phục hồi | **PASS** | Lượt `ACTIVE`: JVM mới nhận epoch 4, revision 5, đáp án cũ, `deadline_at` không đổi (`18`). Lượt đã chốt: JVM mới, tab lịch sử có `SUBMITTED` và `TIMED_OUT`, mở được cả hai kết quả (`31`, `32`, `33`) |
| 9 | Mất WS, reconnect, đồng bộ | **PASS** | Relay đóng socket WS: `RECONNECTING`, lựa chọn 0/8, nhãn lưu giữ revision 5; sau reconnect 8/8, epoch và revision không đổi. `13`, `14`, `ws-relay.txt` |
| 10 | Server tắt, không báo lưu/nộp sai | **PASS** | Lựa chọn 0/4, nhãn lưu giữ ACK cũ; nộp bài báo "Bài CHƯA được coi là đã nộp", không có hộp thoại kết quả; DB `ACTIVE`, 0 dòng submit. `20`, `21`, `23` |
| 11 | Hai cửa sổ, takeover, chặn writer cũ | **PASS** | Cửa sổ thứ hai nhận epoch 3; cửa sổ cũ bị khóa, 0/4 lựa chọn, nút nộp tắt; DB giữ `rev=5`. `15`, `16` |
| 12 | Collector policy → cảnh báo proctor | **PASS** | Dashboard hiện 39 process và 40 cảnh báo cho lượt đang chọn; DB có 40 event với 3 tên tiến trình thuộc policy, presence `ONLINE`. Tên tiến trình và PID đã che trong ảnh. `11`, `12` |

### Các bước riêng của B-ACCEPT-01 trên `b54a8ad`

- Tab lịch sử trong JVM mới có cả hai lượt đã chốt; mở kết quả 2/10 và 0/10 mà không mở màn thi. `db-before-history.txt` và `db-after-history.txt` giống hệt nhau; số dòng autosave và submit không đổi.
- `candidate2`: tab lịch sử 0 dòng (`36`); đọc status và đề của hai lượt của `candidate1` qua HTTP nhận 403 (`api-probes.txt`).
- Monitoring ACTIVE-only qua HTTP: scope của `candidate1` rỗng; proctor đọc event của lượt đã chốt nhận 403, của lượt `ACTIVE` nhận 200; takeover trên lượt đã chốt nhận 409 `INVALID_STATE`.
- **NOT RUN trong lượt này:** kiểm roster ACTIVE-only trên dashboard sau khi các lượt đã chốt. Lúc chụp, dashboard đang báo "mất kết nối — dữ liệu cũ" vì server vừa bị dừng có chủ đích cho R5 (`37-proctor-dashboard-stale-after-server-stop.png`).

## 7. Thay đổi đáp án ngoài kịch bản

- **Lượt này:** `autosave-log.txt` có đúng 5 dòng, khớp 5 thao tác trong `ui-actions.txt` cả về thứ tự và thời điểm. Bài nộp ở ca 6 mang đúng revision 5. Không có thay đổi nào ngoài script.
- **Lượt trước (`6816ff5`):** có 4 lần autosave (revision 6–9) không do script gửi, lúc cửa sổ thử nghiệm bị đưa lên foreground. Vì vậy mọi nhận định cần revision chính xác (bài nộp mang revision nào, số dòng autosave) chỉ dựa vào lượt này, không dựa vào lượt trước.

## 8. T2-B4: phần vẫn NOT RUN

- Thành viên tự thao tác GUI; máy Windows thứ hai và LAN; review A→B.
- GUI trên bản này cho: xung đột cùng revision, phiên bị thu hồi (401), nộp bài khi mất response, nút "Tải thêm lượt thi" với hơn 50 lượt.
- Roster ACTIVE-only trên dashboard sau khi các lượt đã chốt (xem mục 6).
- Các smoke monitoring của C và bộ Python.
