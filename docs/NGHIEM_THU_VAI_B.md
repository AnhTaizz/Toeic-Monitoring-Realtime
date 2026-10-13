# Vai B — checklist tích hợp và nghiệm thu

**Fix B-ACCEPT-01 12/10:** client history chuẩn bị ở770c896, test/package651 và FX component MOCK PASS; API list owner còn PROPOSED/chờ A review, backend thiếu route. Chưa12/12GUI, không ghép PASS cũ. [Contract cho A](CONTRACT_B_ACCEPT_01_PROPOSAL.md), report mới (bản gốc 12/10 chưa che thông tin máy, chỉ giữ ở máy build). B4/P1 vẫn PARTIAL/OPEN.

**Cập nhật 13/10/2026, nhánh sạch trên main T3-C4:** [nghiệm thu bổ sung](../evidence/b-accept-02-20261013/2026-10-13-verification.md) trên `3997fef`: Maven 717, smoke 28/0 GAP, 5 kịch bản GUI còn thiếu đều PASS; smoke T3-C4 của C chưa PASS trên nhánh này; LAN, thành viên tự kiểm, review của A NOT RUN.

**Cập nhật 13/10/2026, sau sửa lỗi:** [nghiệm thu lại](../evidence/b-accept-01-fix-20261013/2026-10-13-verification.md) trên `b54a8ad`: lỗi P1 làm mới danh sách đã sửa; hồi quy 5/5 và 12/12 ca GUI PASS, chạy lại toàn bộ; thành viên tự kiểm, LAN, review A NOT RUN.

**Cập nhật 13/10/2026:** [nghiệm thu B-ACCEPT-01](../evidence/b-accept-01-20261013/2026-10-13-verification.md) trên `6816ff5`: 12/12 ca GUI PASS, tab lịch sử mở lại được lượt `SUBMITTED` và `TIMED_OUT` trong JVM mới; 1 lỗi P1 mới ở làm mới danh sách lượt thi, chưa sửa; thành viên tự kiểm, LAN, review A NOT RUN.

**Cập nhật 12/10/2026:** nghiệm thu mới (bản gốc 12/10 chưa che thông tin máy, chỉ giữ ở máy build) chạy app production/server/PG thật: 11/12 ca PASS; mở lại ACTIVE PASS nhưng bài đã chốt không chọn được từ app mới (P1 B-ACCEPT-01). Ba blocker A dưới đây đã được PR23 xử lý và kiểm trên tích hợp local; phần checklist/bảng11/10 giữ làm baseline lịch sử. T2-B4 PARTIAL; human A review/LAN NOT RUN; không push/merge. Mã quyết định hiện hành sau lần ghép 12/10: QD-22 writerEpoch entry của B, QD-20 mất WebSocket của B, QD-21 deadline của A; QD-16/17/18 là của C.

Gate thi cơ bản: **12/10/2026**. Nguồn tích hợp là PR #17 `41da669` và main `12e2838`. Checklist này là việc cần chạy; chưa tự đánh dấu nghiệm thu vì có test đơn vị hoặc component smoke. [Báo cáo lượt ổn định](../evidence/b-integration/2026-10-11-verification.md), [review C](REVIEW_B_MONITORING.md).

## Chuẩn bị và ghi bằng chứng

- [ ] A xử lý ba blocker bên dưới; build lại server/client từ cùng source đã ghi SHA và trạng thái working tree.
- [ ] PostgreSQL/Flyway V8 hoạt động; dùng đề/ca TEST riêng, biết rõ owner của dữ liệu để chỉ cleanup fixture đó.
- [ ] Có hai máy Windows và một client giám thị; ghi OS/JDK hoặc bundled runtime, topology, URL/port và cấu hình poll/heartbeat/timeout.
- [ ] Password được nhập lúc chạy. Ba nút mẫu chỉ điền username và xóa password; không lấy credential từ JAR hay log.
- [ ] Mỗi ca ghi thời điểm, thao tác, kết quả mong đợi/thực tế, ảnh/log/DB khi phù hợp. Token/password/command line/username hệ điều hành không xuất hiện trong artifact.

## Candidate → proctor

| Ca | Thao tác | Kết quả bắt buộc | Lượt 11/10 |
|---|---|---|---|
| Login | Candidate/proctor đúng mật khẩu; sai mật khẩu; URL sai; server không chạy | Đúng role; lỗi dễ hiểu; UI không treo; password xóa sau submit; collector không chạy cho proctor hoặc candidate chưa bắt đầu | Component shortcut PASS; production flow NOT RUN |
| Scope/entry | Refresh scope; chọn lượt A, bật giám sát; thử chọn lượt B khi A còn active; revoke assignment trước khi vào thi | Fresh `/auth/me`; chỉ vào đúng lượt đang giám sát; không bỏ qua lỗi start; cần Stop hoàn tất trước lượt khác | Guard regression PASS với MOCK scope/transport; production flow NOT RUN |
| Autosave | Chọn/đổi/bỏ chọn liên tục; thay đổi lúc request đang gửi; giữ/mất ACK | Bản cuối đúng trên DB; chỉ ghi Đã lưu khi ACK revision hợp lệ; retry giữ requestId/payload; không lùi revision | Unit + FX component PASS; DB NOT RUN |
| Submit | Nộp khi autosave đang gửi; gửi lặp/mất response; nộp bài chưa chọn câu | Chốt/chấm một lần; không autosave đè bài chốt; đối chiếu status sau mất response; đọc lại kết quả theo quyền | Unit PASS; production NOT RUN, blocker A1 |
| Timer | Vào bài và chưa chọn đáp án; ca ngắn để tự hết giờ | Deadline do server quyết từ mốc bắt đầu đã chốt; client khóa, không tự nộp; server tự TIMED_OUT/chấm điểm; đọc được kết quả | Unit phản hồi client PASS; production NOT RUN, blocker A2/A3 |
| Process/full | Mở Edge, giữ nhiều poll, đóng Edge; mở Notepad | Edge thuộc policy; full thêm/xóa đúng PID/start; event không lặp mỗi poll; Notepad không cảnh báo theo policy v1; không coi empty là máy sạch | C1/C2 unit và tab FX PASS; live flow NOT RUN |
| Warning/history | Proctor chọn đúng lượt; xem current, cảnh báo, gián đoạn và gap; full rỗng sau đóng Edge | Cảnh báo/gap/history giữ nguyên; nhãn nguồn kết nối không bị hiểu là latency; full chỉ là RAM; gap mới đọc bằng refresh | Unit + tab FX PASS; live flow NOT RUN |
| Outage/reconnect | Tắt đúng server TEST đang sở hữu, candidate sửa/nộp; khởi động lại, đợi reconnect | Khóa sửa khi mất WS; bản lưu dựa ACK HTTP; reconnect đối chiếu answers/epoch; OPEN epoch mới/full1; event cũ gửi bù theo origin; proctor có UNKNOWN/recovery, dữ liệu cũ được ghi nhãn | WS/HTTP fixture unit + FX state SIMULATED PASS; production NOT RUN |
| Takeover | Hai cửa sổ cùng candidate vào cùng lượt | Writer cũ bị STALE; bản chưa lưu không ghi đè; reclaim nạp đúng bản server | Unit PASS; live two-client flow NOT RUN |
| Stop/logout/close | Stop khi scan/write còn chờ; đổi lượt; logout/đóng app; gửi callback cũ | Chờ worker cũ dừng ngoài FX thread; lease/listeners/timer/HTTP/WS được dọn; callback cũ không đổi view mới; không tự restart collector | Unit worker thật + FX close PASS; live shutdown NOT RUN |
| LAN | Candidate trên máy thứ hai, server/proctor trên máy thứ nhất; lặp login/save/submit/process/outage | Kết quả và cleanup tương đương; client chỉ nối server, DB không mở ra LAN; ghi bằng chứng trên hai máy thật | NOT RUN |

FAULT SIMULATED và dữ liệu MOCK phải ghi riêng khỏi network/DB/process REAL. Không gọi hai JVM localhost là hai máy LAN. Không nâng AT11/MT01/MT07/MT08/IT hoặc E1/E2 thành PASS toàn hệ thống từ bảng này.

## Ba blocker A lịch sử 11/10 — đã PASS trên bản tích hợp 12/10

1. **A1 — đọc lượt đã chốt:** `ExamService.getAttemptStatus()` dùng `requireAttempt`; `JdbcAttemptScopeStore.canAccess()` chỉ nhận ACTIVE. Cho candidate owner/proctor được phân công đọc kết quả SUBMITTED/TIMED_OUT theo hợp đồng, đồng thời giữ guard ghi và monitoring. Kiểm owner/foreign/role trước và sau submit bằng server thật.
2. **A2 — tự hết giờ:** `ExamTimeoutService.processTimeouts()` có Scheduled nhưng app chưa có scheduling processor. A chọn lifecycle phù hợp và thêm test Spring thật không gọi hàm bằng tay. Kiểm chấm đúng một lần khi timeout/save/submit đồng thời; hồi quy worker full/presence của C.
3. **A3 — mốc bắt đầu/deadline:** hiện deadline chỉ đặt khi autosave đầu; thí sinh chưa chọn câu không bị tính giờ. A chốt mốc bắt đầu, API/DTO trả deadline và tính idempotent; B consume sau khi hợp đồng đã rõ. Hạn xử lý trước nghiệm thu timer của gate 12/10.

Ba điểm này đối chiếu source main; smoke PostgreSQL 11/10 NOT RUN do Docker engine không hoạt động. Kết quả 28 PASS + 3 GAP ngày 09/10 là evidence cũ, không phải lượt chạy mới.

## T1-B4: hoàn thành ở nhánh riêng sau ổn định PR #17

PR #9 closed/unmerged, HEAD `568be80`, base `a04aaa2`. Đã đối chiếu diff: thêm javafx-media, audio fixture/harness TEST và scripts packaging; không có production Java audio. Bản cũ thiếu C1–C5 và exam UI hiện tại, nên không merge nguyên nhánh.

1. Tạo worktree/nhánh B4 mới từ source tích hợp đã duyệt. Port từng phần `package-client.ps1`, media dependency cùng version JavaFX, harness TEST/fixtures sau khi review. Giữ các helper manifest chưa commit trong workspace gốc; không chép chúng như một fix đã nghiệm thu.
2. Stage **chỉ fat JAR production** vào thư mục mới dưới target; không đưa toàn bộ client/target vào `jpackage --input`. Build app-image với runtime JDK21, output mới không ghi đè bản người dùng. Kiểm test harness/log/credential không đi vào image; ghi JAR/image hash.
3. Tái hiện lỗi launcher đường dẫn ngoài ANSI code page của PR #9. Đối chiếu manifest UTF-8 helpers, preserve trust/DPI/dependency nodes, kiểm idempotence, thực hiện patch bằng công cụ Windows và chạy lại đầy đủ launcher/login/monitoring/shutdown. Thử ASCII, khoảng trắng và `Thử nghiệm TOEIC`; ghi code page/OS. Fix cũ ngoài repo chỉ là evidence thử nghiệm.
4. Tạo image TEST riêng chứa launcher audio harness. Smoke MP3 chính, WAV PCM/AAC hỗ trợ khảo sát, file thiếu/hỏng, Path.toUri với dấu/khoảng trắng, dispose MediaPlayer và worker. Kiểm tiến trình và **người thật nghe** âm thanh; trạng thái PLAYED không chứng minh loa phát đúng. Image bàn giao không có TEST launcher. Đây là xác minh T1-B4, không triển khai Listening vào exam.
5. Chép image bàn giao và audio ngoài JAR tới **máy Windows thứ hai không có IDE/JDK/JRE**; kiểm mở bằng runtime kèm theo, path Unicode, login/giám sát/outage LAN, audio và thoát hết process. Giữ log/ảnh/cấu hình/hashes và cả failure. IT01/T1-B4 vẫn PARTIAL đến khi có evidence máy thứ hai.

Lượt này **không build app-image, không thêm javafx-media, không phát audio, không sửa helper B4 local**. PR #9 chưa được trộn vào PR #17.

## Điều kiện merge PR #17

- [ ] Push bản đã kiểm lên đúng nhánh PR #17 sau khi người dùng cấp quyền; remote chưa bị cập nhật bởi người khác; không force-push.
- [ ] Review lại diff với main mới nhất, không conflict; backend/protocol giữ đúng main, A xem QD-22/QD-20 và ba blocker.
- [ ] Chạy lại Maven package và smoke PostgreSQL + candidate/proctor toàn luồng sau fix A; giữ PASS/FAIL/GAP có bằng chứng.
- [ ] A review và các check/review bắt buộc của GitHub (nếu có) hoàn tất; không suy ra không có rule từ danh sách commit statuses trống.
- [ ] Ghi rõ phần LAN/package máy khác còn thiếu nếu chưa được nghiệm thu; không ghi B3/B4 hoàn tất toàn hệ thống.

Agent không merge PR vào main và không bật auto-merge trong lượt ổn định.
