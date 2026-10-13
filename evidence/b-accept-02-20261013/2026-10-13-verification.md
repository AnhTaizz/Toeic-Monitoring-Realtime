# Vai B — nhánh client sạch trên backend mới, review chéo A→B và nghiệm thu bổ sung (13/10/2026)

Agent-driven (UI Automation trên máy build). Thành viên vai A và B chưa tự kiểm: **human review NOT RUN**. Chưa push, chưa merge.

## 1. Ba nhánh xếp chồng

| Thứ tự merge | Nhánh cục bộ | Đầu nhánh | Nội dung |
|---|---|---|---|
| 1 | `sync/pr23-main-20261013` | `412b7ec` | PR #23 (ba sửa P0 của A) đã đồng bộ main `cb30f3d`; fast-forward của `fix/backend-exam-p0-20261011` |
| 2 | `feat/backend-candidate-attempt-history-20261013` | `8787767` | chỉ ba commit API lịch sử của A và tài liệu, đặt trên nhánh 1 |
| 3 | `feat/javafx-exam-client-20261013` | xem `git log` | client JavaFX của B, đặt trên nhánh 2; chứa nguyên lịch sử PR #17 (`2f5fc66`) nên là fast-forward của `test/javafx-full-ui-testing` |

Code của nhánh 3 được kiểm tại commit **3997fef**; các commit sau đó trên nhánh chỉ thêm tài liệu và bằng chứng (`git diff 3997fef HEAD -- client server protocol scripts pom.xml` rỗng).

## 2. Nhánh client gồm gì

| Commit | Nội dung |
|---|---|
| `ee8ab4f` | merge đầu PR #17 `2f5fc66` vào chồng backend |
| `7306aaa` | client lịch sử lượt thi (gốc `770c896`) |
| `be9fd04` | chỉnh smoke theo hợp đồng "deadline khi vào thi" (chỉ test) |
| `7faaa48` | sửa P1 làm mới danh sách lượt thi (gốc `b54a8ad`) |
| `3997fef` | giữ nhãn hai nút như trên main (xem mục 4) |
| `00abda5` | tài liệu nghiệm thu của B và hai thư mục bằng chứng đã che `b-accept-01-*` |

Không commit nào của dòng `integrate/…` cũ nằm trong nhánh. Hai thư mục bằng chứng 12/10 chưa che (`t2-b-acceptance-20261012`, `t2-b-results-20261012`: có tên ứng dụng đang chạy và đường dẫn thư mục người dùng của máy build) không có trong cây thư mục lẫn trong lịch sử của nhánh; các link tới chúng trong tài liệu được thay bằng ghi chú.

### Khác gì so với bản đã nghiệm thu GUI 12/12 (`b54a8ad`)

Trong lúc làm, main nhận thêm PR #27 (T3-C4 của C), có sửa `ToeicClientApplication.java` là file B đã viết lại. Đây là conflict code duy nhất.

- Giữ: toàn bộ màn hình và luồng của B.
- Lấy từ T3-C4: constructor có factory cho test, `monitoringFactory.apply(...)`, dòng trạng thái "Giám sát: Chế độ FULL/DELTA · …", thông báo khi server thôi hỗ trợ delta.
- Bỏ: phần của T3-C4 gắn với bố cục cũ (nhãn mô tả, kích thước cửa sổ, nút "Thử lại chưa xác nhận" không có trên màn của B).
- Các file khác của T3-C4 trùng main. Ngoài ra chỉ có hai nhãn nút ở mục 4.

Vì cây code đã khác `b54a8ad`, kết quả 12/12 ngày 13/10 không tự áp dụng cho bản này. Mục 5 kiểm lại luồng chính và các ca còn thiếu trên đúng bản mới.

## 3. Kiểm tự động trên `3997fef`

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn package` với `TOEIC_PG_INTEGRATION=true` | **717 PASS**, 0 failure/error/skip (protocol 43, client 501, server 172, spike 1) | [tóm tắt](mvn-package-summary.txt) |
| `ExamFlowPostgresSmoke` (server JAR, HTTP, PostgreSQL thật) | **28 PASS, 0 GAP** | [log](exam-flow-smoke.txt) |

717 = 707 của bản `b54a8ad` + 10 test client của T3-C4. PostgreSQL 18.6, mỗi lượt dùng schema riêng và tự xóa.

## 4. Smoke live gate T3-C4 của C trên nhánh client

`scripts/smoke-t3c4.ps1` (đã ở main) mở ứng dụng thật và tìm nút theo đúng chữ `Đăng nhập`, `Bắt đầu giám sát`. Giao diện của B đặt `Đăng Nhập Ngay` và `Bật giám sát tiến trình`.

| Lượt | Bản build | Kết quả | Dừng ở |
|---|---|---|---|
| 1 | nhánh client, nhãn của B | FAIL, 0 kiểm tra chạy | mở giao diện giám thị: không tìm thấy nút `Đăng nhập` |
| 2 | nhánh client, đã đổi nhãn | FAIL sau 8 kiểm tra PASS | `KILL_RESTART_CANDIDATE` (timeout) |
| đối chứng | nhánh API lịch sử, **không có client của B** | FAIL sau 3 kiểm tra PASS | `NORMAL_REAL_EDGE` (timeout) |
| 3 | nhánh client `3997fef` | FAIL, 0 kiểm tra PASS | pha đầu (`MIGRATION`), trước khi hai thí sinh khởi động xong |

[Dòng kết quả của bốn lượt](t3c4-smoke-runs.txt); các dòng `PASS CLEANUP_…` là bước dọn dẹp, không tính là kiểm tra.

Kết luận được phép rút ra:

- Lệch nhãn nút là lỗi tích hợp thật và lặp lại được. Đã sửa ở phía B bằng commit `3997fef` (đổi hai chuỗi, không đổi hành vi), vì nhánh vào sau phải khớp với main.
- Sau khi sửa, smoke này vẫn **không PASS** trên máy build trong phiên này, kể cả với bản không có client của B, và mỗi lượt dừng ở một pha khác nhau. Vậy chưa có bằng chứng lỗi còn lại do client của B, nhưng cũng **chưa có lượt PASS nào** của smoke T3-C4 trên nhánh client. C hoặc B cần chạy lại trên máy rảnh.

## 5. Nghiệm thu GUI bổ sung trên `3997fef`

Môi trường: server JAR và PostgreSQL thật trên schema tạm `b_step4_*` (đã xóa sau khi chạy), client JAR thật, một relay TCP loopback chỉ chuyển tiếp byte. Đề và ca thi là dữ liệu mẫu `MOCK` do nút "Khởi tạo Đề & Ca Thi Mẫu" tạo. Các lỗi được tạo có chủ đích ghi nhãn SIMULATED.

| # | Kịch bản | Kết quả | Quan sát chính |
|---|---|---|---|
| S0 | Đăng nhập bằng nút đã đổi nhãn; bật/dừng giám sát thủ công | PASS | Dòng trạng thái: "Giám sát: Chế độ FULL · Full mặc định. · …"; hai nút đổi trạng thái đúng |
| S1 | Xung đột cùng revision | PASS | Client đang ở revision 1. Một phiên đăng nhập thứ hai của cùng thí sinh gửi revision 2 = `{L1:C}` cùng `writerEpoch` (SIMULATED). Thí sinh đổi sang B → client báo "Bản trên máy và bản trên server cùng revision nhưng khác nội dung", dừng tự lưu, khóa 4/4 đáp án, không ghi "Đã lưu"; DB vẫn `{L1:C}`. Bấm "Nạp bản của server" → revision 2, mở khóa; đổi tiếp → revision 3 được lưu |
| S2 | Token bị thu hồi khi đang thi | PASS, theo đường heartbeat | Đặt `revoked_at` cho đúng phiên của client (SIMULATED thu hồi phía server). Trong vài giây client rời phòng thi về màn đăng nhập: "Phiên hết hiệu lực. Hãy đăng nhập lại." DB không đổi. Đăng nhập lại và vào thi: `writerEpoch` 2 → 3, khôi phục revision 3 từ server |
| S3a | Mất WebSocket khi đang thi (hồi quy sau T3-C4) | PASS | Lấy mẫu dày: 0/4 đáp án bật trong lúc "REALTIME: RECONNECTING" (khoảng 0,3–1,1 giây), 4/4 khi nối lại; nút nộp vẫn bật |
| S3b | Mất response khi nộp | PASS | Relay chuyển request nộp tới server, đọc rồi bỏ response `HTTP/1.1 200` (433 byte) và đóng socket phía client (SIMULATED). Client hiện "Đang nộp bài…", không báo thành công sớm; khoảng 0,4 giây sau hiện "SERVER ĐÃ GHI NHẬN BÀI NỘP (revision 3)" với giờ server chốt. DB: `SUBMITTED`, đúng 1 hàng trong `exam_submit_requests`, đáp án không đổi |
| S4 | Phân trang lịch sử trên 50 lượt | PASS | Chèn 55 lượt đã chốt `MOCK-HIST-001…055` bằng SQL (MOCK). Trang đầu 50 dòng, nút "Tải thêm lượt thi" bật; bấm → 56 dòng, nút tắt. Server liệt kê 58 (50 + 8) vì còn 2 lượt ACTIVE; tab lịch sử chỉ hiện lượt đã chốt (45 SUBMITTED + 11 TIMED_OUT = 56). HTTP hai trang không trùng, đúng thứ tự |
| S5 | Roster chỉ có lượt ACTIVE sau khi một lượt đã chốt | PASS | Trước khi nộp: "Lượt được phân công: 6". Sau khi nộp và làm mới: 5 dòng, khớp 5 lượt ACTIVE trong DB; lượt đã nộp và các lượt MOCK không xuất hiện |

Chi tiết từng thao tác: [ui-actions.txt](ui-actions.txt). Relay: [ws-relay.txt](ws-relay.txt). Dữ liệu cuối trong DB: [db-final.txt](db-final.txt). Ảnh: thư mục `screens/`.

Bảng autosave của lượt thử chỉ có ba hàng, đúng ba thay đổi theo kịch bản (A do GUI, C do lệnh chèn, D do GUI), nên không có thao tác ngoài kịch bản làm đổi đáp án.

Giới hạn của lượt này:

- S2 không tạo được ca "autosave nhận 401" trên GUI vì heartbeat phát hiện phiên bị thu hồi trước. Nhánh 401 của autosave chỉ có test tự động (`ExamSessionTest`, `ExamApiClientTest`).
- S5 đối chiếu bằng số dòng và mã lượt bị cắt ngắn trên màn (`SESSION-BENCHMARK-53…`); không chọn dòng roster để ảnh không chứa tên tiến trình của máy.
- Ảnh chụp bằng `PrintWindow` có thể cũ ở vùng cửa sổ bị che. Ở `s1-02-…png`, dòng chân trang còn ghi "Revision trên máy: 0" trong khi nhãn đọc qua UI Automation lúc đó là "Revision trên máy: 2 | Server đã xác nhận: 1". Mọi kết luận ở bảng trên dựa vào chữ đọc qua UI Automation và DB, không dựa vào ảnh.
- Thao tác "Thoát màn thi" trong S4 được gọi qua UI Automation khi hộp thoại điểm còn mở, điều người dùng thật không làm được; hộp thoại sau đó được đóng bằng nút của chính nó.
- Kiểm thử LAN và máy thứ hai: **NOT RUN** (phiên này chỉ có một máy).

## 6. Review chéo A → B (Agent thực hiện, chưa thay cho review của thành viên A)

Đã đối chiếu với bất biến của dự án:

| Điểm | Kết quả | Căn cứ |
|---|---|---|
| Autosave gửi cả map kèm `requestId`, `writerEpoch`, `answerRevision`; chỉ ghi "Đã lưu" sau ACK | Đạt | S1, S3; `ExamSessionTest`; smoke 28/0 |
| Revision thấp/STALE, cùng revision khác nội dung/CONFLICT, hết giờ/EXPIRED được xử lý riêng | Đạt | S1 trên GUI; các ca còn lại ở lượt 12/12 ngày 13/10 và test tự động |
| Mất response sau commit không tạo bản nộp thứ hai, không chấm lại | Đạt | S3b: 1 hàng nộp |
| Token không nằm trong URL hay log của client | Đạt | grep mã nguồn `client/src/main`: không có `token=` trong URL, không in token/mật khẩu |
| Client không giữ đáp án đúng | Đạt | không có trường `correctOption` trong DTO phía thí sinh |
| Mạng không chạy trên JavaFX Application Thread; đóng app dừng worker | Đạt ở mức đã kiểm | client đóng bằng nút đóng cửa sổ thì JVM thoát (log thao tác); test vòng đời hiện có |
| API lịch sử: `limit=50`, cursor chỉ nhận mã hợp lệ, trang sau không lùi | Đạt | S4; `ExamHistoryReadTest` |
| Mất heartbeat/WS không bị trình bày như gian lận | Đạt | chữ trên màn: "Mất kết nối giám sát tới server…", dashboard: "Đây không phải kết luận gian lận" |

Việc cần người có trách nhiệm xem:

| # | Mức | Việc | Ai |
|---|---|---|---|
| 1 | Cần làm trước khi merge nhánh client | Chạy lại `smoke-t3c4.ps1` trên máy rảnh; hiện chưa có lượt PASS nào trên nhánh client (mục 4) | C, B |
| 2 | Cần xác nhận | Commit `3997fef` đổi hai nhãn nút của B để khớp harness của C. Nếu nhóm muốn giữ nhãn của B thì phải sửa harness của C thay vào đó | B, C |
| 3 | Nên sửa | Sai phương thức HTTP vẫn trả 500 thay vì 405 | A |
| 4 | Ghi nhận | `ToeicClientApplication.java` 548 dòng gộp đăng nhập, màn chính, giám sát và vào thi; `ExamSession.java` 882 dòng. Chạy đúng nhưng khó giải thích từng phần khi thầy hỏi | B |
| 5 | Ghi nhận | Tab lịch sử lọc bỏ lượt ACTIVE ở client, nên một trang 50 dòng của server có thể hiện ít hơn 50 dòng | B |

## 7. Chưa chạy

Thành viên tự kiểm GUI; LAN và máy thứ hai; review của thành viên A; smoke T3-C4 PASS trên nhánh client; app-image; Listening; E1/E2. `TRACKER.json` không đổi; T2-B4 vẫn chưa được đánh dấu xong.

## 8. Biên tập bằng chứng

Không ảnh nào trong thư mục này có bảng process của máy (không chọn dòng roster), nên không cần che. Log không chứa token; mật khẩu chỉ là mật khẩu seed `MOCK` mặc định đã ghi trong `CLAUDE.md`. Thư mục `harness/` là script đã dùng để điều khiển GUI và relay.
