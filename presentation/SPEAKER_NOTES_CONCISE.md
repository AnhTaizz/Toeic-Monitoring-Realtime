# TOEIC Monitor — Ghi chú thuyết trình cho bản ít chữ

Đây là ghi chú cho người thuyết trình, **không hiển thị trên slide**. Nội dung gốc được giữ bên dưới để đối chiếu khi thầy hỏi.

## Slide 1 — Bìa

**Cách nói:** Giới thiệu đây là hệ thống thi TOEIC desktop theo mô hình Client–Server, có giám sát tại máy thí sinh. Ba vai: Admin, giám thị, thí sinh. Không dùng camera hay AI để kết luận gian lận.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

BTL Lập trình mạng Nhóm 3 thành viên · vai A · B · C TOEIC Monitor Thi TOEIC Client–Server và giám sát máy thí sinh theo thời gian thực — quan sát, không phán xét. Process USB Focus Display JavaFX · Spring Boot · PostgreSQL 18 · REST + WebSocket

</details>

## Slide 2 — Bài toán

**Cách nói:** Diễn giải ba yêu cầu: autosave không báo thành công khi chưa thực sự ghi DB; cùng một deadline từ server; giám thị nhận tín hiệu process/USB/focus/display và tự xem xét. Ví dụ mất mạng giữa ca thi.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

01 · Bài toán Một phòng thi máy tính cần ba điều cùng lúc 1 Bài làm không mất Mạng chập chờn, app bị đóng, hai cửa sổ cùng ghi một lượt thi — đáp án đã báo “đã lưu” phải thật sự nằm trong DB. 2 Giờ do Server quyết Deadline chung cho cả phòng, không tin đồng hồ máy thí sinh. Phần Listening chỉ phát một lần, không tua, không phát bù. 3 Giám thị nhìn thấy Process, USB, focus, cửa sổ trên máy thí sinh hiện gần thời gian thực — là sự kiện “quan sát thấy” , không phải kết luận gian lận. Ràng buộc Nhiều client đồng thời · chạy qua LAN hoặc Internet (Cloudflare Tunnel) · mất kết nối là chuyện thường, không phải ngoại lệ 02

</details>

## Slide 3 — Kiến trúc tổng thể

**Cách nói:** Giải thích kiến trúc: JavaFX là client, Spring Boot cung cấp nghiệp vụ, PostgreSQL lưu bền. HTTP/REST cho yêu cầu/trả lời (lấy đề, save/submit), WebSocket cho realtime (heartbeat, snapshot, event, cảnh báo). Client không truy cập thẳng DB.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

02 · Kiến trúc REST cho dữ liệu, WebSocket cho thời gian thực Client · JavaFX 21 · một JAR Desktop app Admin tài khoản, đề thi Giám thị phòng PIN, START, dashboard Thí sinh phòng chờ, 7 Part, audio Collector ProcessHandle · CIM · cửa sổ HTTP / REST · /api/v1, /api/v2 Yêu cầu – trả lời Đăng nhập, đề, audio & ảnh có xác thực, lưu đáp án, nộp bài, đọc trạng thái, timeline. WebSocket + JSON · /ws/v1/realtime Đẩy hai chiều Heartbeat, snapshot & event giám sát, lệnh phát Listening, cảnh báo tới giám thị. Server · Spring Boot 4.1 Một ứng dụng, chia module auth admin exam phòng, attempt audio monitoring reducer, signal realtime PostgreSQL 18 Flyway V1–V23 · JDBC tường minh Raw WebSocket — không STOMP/SockJS/broker. Nguồn: docs/ARCHITECTURE.md, README “Thư viện ngoài”, server/src/main/resources/db/migration. 03

</details>

## Slide 4 — Luồng phòng thi

**Cách nói:** Luồng phòng thi: Admin chuẩn bị đề, giám thị tạo phòng/PIN, thí sinh JOIN và tải audio/test loa rồi READY; giám thị START để server xác lập deadline; Listening và autosave; giám thị xem monitoring; thí sinh submit hoặc timeout. Cần phân biệt HTTP với WS. Trùng START không phát lại, reconnect không reset deadline. Với requireAllReady, START thiếu READY có thể trả 409 và cần xác nhận có chủ ý để bắt đầu một phần.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

02 · Luồng message Một phòng thi, từ PIN đến nộp bài Thí sinh Server Giám thị HTTP WebSocket Tạo phòng → PIN 5 số (lưu BCrypt + HMAC) JOIN: token tài khoản + PIN → đúng 1 Attempt Kiểm checksum audio, thử loa → READY START: khóa Session, deadline chung LISTENING_START — gửi sau COMMIT Autosave toàn bộ đáp án → ACK sau commit HEARTBEAT 2 s · MONITORING_FULL · event MONITOR_STATE · MONITOR_WARNING Nộp bài / hết giờ → chốt, chấm một lần Start trùng ID không phát lại . Reconnect không reset deadline. 04

</details>

## Slide 5 — Autosave

**Cách nói:** Autosave: server khóa attempt khi xử lý save/submit/timeout/takeover; kiểm tra writerEpoch, revision, deadline bằng clock_timestamp() rồi commit. Client chỉ báo Đã lưu sau HTTP response thành công. Với yêu cầu nộp lặp, trả lại kết quả đã chốt; không tạo thêm lượt nộp. Đừng gọi autosave HTTP response là ACK WebSocket.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

03 · Tính đúng Một khóa, một đồng hồ, ACK sau COMMIT 1 Khóa bản ghi attempt — save, submit, timeout, takeover dùng chung 2 Kiểm writerEpoch sau khóa — cửa sổ cũ bị takeover → STALE 3 Đọc clock_timestamp() một lần — không dùng now() của đầu transaction 4 So revision : thấp hơn → STALE; bằng nhau mà nội dung khác (dạng chuẩn hóa) → CONFLICT 5 COMMIT rồi mới ACK — rollback không bao giờ báo “đã lưu” -- ExamService.java · autosave SELECT a.writer_epoch, a.saved_revision, a.state, … FROM monitoring_attempts a WHERE a.attempt_id = :attemptId FOR UPDATE OF a -- ① if (request.writerEpoch() != attempt.writerEpoch()) throw … ErrorCode.STALE -- ② Instant decisionAt = jdbc.sql( "SELECT clock_timestamp()" )…single(); -- ③ if (!decisionAt.isBefore(deadlineAt)) throw … ErrorCode.EXPIRED Trích server/src/main/java/vn/edu/toeic/server/exam/ExamService.java (rút gọn). 05

</details>

## Slide 6 — Pipeline giám sát

**Cách nói:** Collector tại máy thí sinh đọc các process trong policy được theo dõi; tách luồng JavaFX. Dữ liệu hiện tại về RAM server, event lịch sử ghi DB, ACK tùy loại. USB/focus/display có nguồn quan sát riêng; UNKNOWN khi mất heartbeat không phải kết luận gian lận. Giám thị xem và đánh giá sự kiện.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

04 · Pipeline Từ ProcessHandle tới màn hình giám thị Thí sinh Poll ProcessHandle mỗi 1 000 ms, ngoài luồng JavaFX Policy v4 Lọc 9 tên Không đọc tham số, không đóng process Identity Định danh (collectorSessionId, pid, startInstant) WebSocket Gửi FULL làm mới 2 s · event có eventId ổn định qua retry Server Hai luồng State: reducer trong RAM · History: commit DB rồi ACK Heartbeat 2 s · timeout 6 s Mất heartbeat → UNKNOWN . Đó là mất liên lạc, không phải bằng chứng gian lận. Một lần xuất hiện = một event Không lặp event ở mỗi poll. Collector chỉ chạy ở role thí sinh. Tham số mặc định: docs/PROTOCOL.md (T2-C5). Chín tên: chrome, msedge, firefox, zalo, teams, ms-teams, discord, anydesk, teamviewer (.exe). 06

</details>

## Slide 7 — E1 polling

**Cách nói:** Đây là thử nghiệm E1 với process con tạo có kiểm soát, không phải benchmark toàn ứng dụng. Poll quá chậm có thể bỏ sót process ngắn. Nêu rõ N=30 mỗi chu kỳ, số lượt chưa kết luận và CPU theo % một lõi; kết quả 1000 ms và 2000 ms không đơn điệu.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

05 · E1 Chu kỳ polling Poll nhanh bắt được nhiều hơn, nhưng tốn CPU hơn Tỉ lệ bỏ sót · process sống ~200 ms N = 30 / chu kỳ · khoảng = có lượt chưa kết luận 250 ms 500 ms 1000 ms 2000 ms 0% 50% 100% 250 ms: 0–3,33% (29 thấy / 0 sót / 1 chưa kết luận) 500 ms: 46,67% (16 / 14 / 0) 1000 ms: 86,67% (4 / 26 / 0) 2000 ms: 73,33% (7 / 22 / 1) tới 76,67% nếu lượt chưa kết luận là sót 0–3,3% 46,7% 86,7% 73,3–76,7% CPU collector · % một lõi Trung bình 3 lượt, mỗi lượt OK 250 ms 500 ms 1000 ms 2000 ms 0% 10% 20% 250 ms: 17,01 ± 0,60% 500 ms: 9,02 ± 0,38% 1000 ms: 4,94 ± 0,20% 2000 ms: 2,57 ± 0,22% 17,01 9,02 4,94 2,57 360 process con có kiểm soát: 273 thấy · 84 sót · 3 chưa kết luận 12 lượt (4 chu kỳ × 3), seed 1234 · Intel Core Ultra 5 125H, Windows 11 · ProcessHandle thật, client giả lập, không qua mạng evidence/qa05-e1-20261021/REPORT.md · Một máy, mẫu nhỏ: 1000 ms vs 2000 ms không đơn điệu — không suy rộng. Process ≥ 3 s: 30/30 thấy ở mọi chu kỳ. 07

</details>

## Slide 8 — Kiểm thử hồi quy

**Cách nói:** Đây là các MỐC kiểm thử lịch sử tại commit cụ thể, không phải tuyên bố HEAD mới nhất không lỗi. 1533 PASS/316 SKIP ở ce94173 và 1797 PASS/23 SKIP ở 5173462; 3/3 ACCOUNT_PIN E2E. Kết quả fail=0 thuộc các lượt đã chạy. Các case NOT TESTED vẫn còn và có ca GUI từng fail dưới tải CPU cao được kiểm tra lại.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

05 · Kiểm thử main ce94173 · 5173462 Bộ hồi quy đầy đủ: không lỗi nào còn mở 1533 PASS 0 FAIL · 316 SKIP — main mới nhất , mvn test 7 module, server trên PostgreSQL thật; GUI/tùy chọn bị bỏ qua 1797 PASS 0 FAIL · 23 SKIP — bản phát hành 5173462, gồm GUI JavaFX thật; + 22 windows-observer 3 /3 PASS E2E ACCOUNT_PIN: Admin → Giám thị → 2 thí sinh → START → nộp Lỗi tìm ra khi review cuối Sau SUBMIT/TIMEOUT, kênh tín hiệu vẫn chạy. Dưới tải CPU: code cũ 3/6 FAIL , bản sửa 6/6 PASS . Chưa kiểm — ghi đúng là chưa LAN hai máy vật lý · người nghe audio thật · cắm/rút USB vật lý trên commit tích hợp: NOT TESTED . main ce94173, 31/10: Windows 11, JDK 21.0.8, PostgreSQL 18.6 DB tạm đã xóa; lượt đầu 1 test chập chờn (CandidateSignalsPendingLimitTest), chạy lại PASS. 5173462: docs/release/RELEASE-REPORT.md §2, §5. 08

</details>

## Slide 9 — Kịch bản demo

**Cách nói:** Demo theo vai: Admin tạo tài khoản/đề, giám thị tạo phòng, thí sinh JOIN–READY, giám thị START, client lưu/nộp, rồi quan sát các event. Chỉ trình bày chức năng và kết quả thực sự có bằng chứng; USB thật chưa kiểm trên mọi commit tích hợp. Nên lấy ảnh giám thị thật để nhấn mạnh Monitoring.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

06 · Kịch bản demo Năm bước thử nhanh Admin Tạo Giám thị, Thí sinh; import đề JSON hoặc soạn tay đủ 200 câu Giám thị “+ Tạo phòng” → chọn đề → đọc PIN cho thí sinh Thí sinh Nhập PIN → tải âm thanh, thử loa, xác nhận → hàng chuyển “Sẵn sàng” Giám thị Bắt đầu phòng thi → Listening phát, chân trang “✓ Đã lưu” → Nộp bài Thử Mở Chrome/Zalo, rời cửa sổ, cắm/rút USB → cảnh báo hiện bên Giám thị Màn Admin. Chụp từ lượt GUI thật của ACCOUNT_PIN, tài khoản MOCK do test tạo. README.md “Luồng thử nhanh”. Server: Cloudflare Tunnel hoặc http://IP_MAY_CHU:8080 trong LAN. 09

</details>

## Slide 10 — Giới hạn

**Cách nói:** Giới hạn thực tế: chưa có ca thi nhiều máy vật lý và nghe Listening đầy đủ được nghiệm thu, polling không đảm bảo bắt mọi process rất ngắn, RAM state mất khi server restart nhưng history còn; DELTA chưa được bật mặc định. Phương hướng: test E2E hai máy, đo performance, nghiệm thu audio/USB, cải tiến thu thập OS nếu phù hợp.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

06 · Giới hạn & hướng tới Những gì còn chưa làm được Giới hạn Thi đầy đủ qua Internet / LAN hai máy vật lý chưa chạy xong Chưa có người nghe audio thật qua loa/tai nghe Polling bỏ sót process sống ngắn hơn chu kỳ Restart Server mất trạng thái hiện tại (lịch sử vẫn giữ) Bộ cài MSI chưa ký số; delta chưa qua gate nên tắt Hướng phát triển Chạy checklist hai máy khác mạng đã soạn sẵn Đo byte thật của full và delta (E2) trước khi bật delta Đánh giá nguồn sự kiện OS thay cho polling (cần review riêng) Ký số bộ cài, nghiệm thu âm thanh với người thật README.md “Giới hạn đã biết” · docs/release/RELEASE-REPORT.md §5.6, §7. 10

</details>

## Slide 11 — Kết

**Cách nói:** Cảm ơn, mời đặt câu hỏi. Nếu hỏi sâu, mở full.html 30 slide và mã nguồn làm bằng chứng.

<details><summary>Nội dung chi tiết từ bản slide gốc</summary>

Hỏi & đáp Cảm ơn thầy cô & các bạn Mã nguồn, tài liệu và bằng chứng github.com/AnhTaizz/Toeic-Monitoring-Realtime

</details>

