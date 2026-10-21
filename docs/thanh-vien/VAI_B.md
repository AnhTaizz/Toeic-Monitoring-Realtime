# Vai B — Ứng dụng JavaFX, audio, đóng gói

Người đảm nhận: _chưa gán_

## Tóm tắt vai

- **Bạn sở hữu:** ứng dụng desktop JavaFX (giao diện thí sinh và giám thị trong cùng một project), adapter mạng phía client (HTTP và WebSocket), màn làm bài Reading và Listening, phát audio, đóng gói và bộ cài, README hướng dẫn chạy.
- **Bạn không sở hữu:** collector quét process (của C; nó chạy trong ứng dụng của bạn qua một interface), và mọi quyết định nghiệp vụ như deadline, chấm điểm, quyền (của server).
- **A review code của bạn. Bạn review code của C.**
- Tổng: 82 giờ (72 giờ task + 10 giờ review).

Tài liệu phải đọc: `02_HOP_DONG.md` mục 2 (phần "Reconnect và chuyển phiên ghi") và mục 4 (Listening), `03_KIEM_THU_VA_THUC_NGHIEM.md` mục 4.

## Cách dùng file này

Mỗi task có phần hướng dẫn và dòng **Ghi chú làm dở**. Khi dừng giữa chừng, ghi vào đó bạn đang ở bước nào và cái gì chưa chạy. Trạng thái chính thức vẫn cập nhật ở `TRACKER.json`. Phần "Cách làm" là gợi ý; nếu chọn cách khác thì ghi vào `docs/QUYET_DINH.md`.

## Ba nguyên tắc xuyên suốt của phía client

1. **Luồng giao diện chỉ vẽ.** Gọi mạng, đọc ghi file, băm checksum, quét process đều chạy ở luồng nền (`javafx.concurrent.Task` hoặc executor riêng); cập nhật giao diện qua `Platform.runLater`.
2. **Client không quyết định gì.** Khóa nút, ẩn màn hình, đồng hồ đếm ngược chỉ là phản hồi cho người dùng. Server mới là nơi quyết định quyền, deadline và trạng thái.
3. **Chỉ gọi là "đã lưu" khi có ACK.** Chưa có ACK thì hiển thị "đang chờ".

## Contract bạn phải giao và nhận

| Hạn | Bạn giao | Cho ai |
|---|---|---|
| 04/10 | Interface để collector gửi event và state qua adapter mạng | C |
| 16/10 | Audio manifest phía client, READY/start/interrupted (cùng A) | C |

| Hạn | Bạn nhận | Từ ai |
|---|---|---|
| 03/10, giờ đầu | Mẫu login; khung message v0 | A, C |
| 04/10 | Cách gửi credential HTTP/WS | A |
| 09/10 | Schema đề, answers/revision, requestId, writerEpoch, mã lỗi | A |
| 12/10 | Response bài đã chốt, takeover, state khi reconnect | A |
| 24/10 | Harness chạy benchmark polling | C |

Khi contract chưa cài xong, làm với fixture ghi nhãn `MOCK`, rồi nối server thật trước gate.

---

## Chặng 1 (03–08/10) — 17 giờ

Lịch gợi ý: 03/10 B1 · 04/10 B2 (3h) + B3 (1h) · 05/10 B2 (1h) + B3 (2h) · 06/10 B3 (1h) + B4 (1h) · 07–08/10 B4.

### T1-B1 · Khung JavaFX, đăng nhập, hai role
`4h` · phụ thuộc: T1-A1 (mẫu login) · review: A

**Đầu ra:** ứng dụng mở được, đăng nhập được, vào đúng giao diện theo role.

**Cách làm:**
1. Tạo project Maven với JavaFX. Dùng cùng phiên bản JDK với A (QD-01).
2. Màn đăng nhập: nhập địa chỉ server (đọc từ cấu hình, sửa được), tên, mật khẩu.
3. Gọi login bằng `java.net.http.HttpClient` ở luồng nền. Trong lúc chờ server của A, dùng response `MOCK` theo mẫu đã thống nhất.
4. Server trả role → mở giao diện thí sinh hoặc giám thị. Role lấy từ response, người dùng không tự chọn.
5. Hiển thị lỗi đăng nhập và lỗi không kết nối được.
6. **45 phút cuối:** thử `jpackage --type app-image` ngay để lộ sớm trở ngại đóng gói. Ghi lỗi nếu có.

**Dễ sai:**
- Khi đóng gói JavaFX, lớp chứa `main` kế thừa `Application` thường gây lỗi thiếu runtime; tách một lớp `Launcher` thường chỉ gọi `Application.launch`.
- Viết cứng `localhost`: ứng dụng phải chạy được qua LAN.
- Gọi HTTP đồng bộ trong hàm xử lý nút bấm làm đơ giao diện.

**Xong khi:** hai tài khoản khác role mở ra hai giao diện khác nhau; sai mật khẩu hiện lỗi; cửa sổ không đơ khi server phản hồi chậm.

**Ghi chú làm dở:** HTTP chạy nền, route hai role và app-image local đã kiểm ngày 03/10; còn thao tác GUI end-to-end và chạy trên máy thứ hai trước gate đóng gói.

### T1-B2 · Adapter mạng, heartbeat và khóa giao diện
`4h` · phụ thuộc: T1-B1, T1-A2 (contract) · review: A

**Đầu ra:** một lớp adapter duy nhất lo kết nối WS, gửi nhận message, heartbeat và reconnect.

**Cách làm:**
1. Dùng `java.net.http.WebSocket`. Gửi credential theo cách A chốt (QD-03).
2. Heartbeat gửi định kỳ bằng `ScheduledExecutorService` (khởi đầu 2 giây, là tham số cấu hình).
3. Reconnect có giới hạn số lần và thời gian chờ tăng dần (backoff).
4. Khi phát hiện mất kết nối: báo cho giao diện để khóa thao tác và hiện trạng thái.
5. Đóng ứng dụng: đóng WS và tắt hết executor (ghi đè `Application.stop()`).
6. Định nghĩa interface cho collector của C gọi vào (ví dụ: gửi event, gửi state, nhận ACK, biết trạng thái kết nối). Giao cho C trước buổi ghép 04/10.

**Dễ sai:**
- `WebSocket.Listener.onText` có thể giao một message thành nhiều mảnh; ghép lại cho tới khi cờ `last` là true, và nhớ gọi `request(1)` để nhận tiếp.
- Callback của WebSocket chạy ở luồng khác luồng giao diện.
- Executor không tắt làm tiến trình vẫn chạy sau khi đóng cửa sổ.
- Mỗi lần reconnect phải tạo lại kết nối và xác thực lại; đừng cho rằng trạng thái cũ còn nguyên.

**Xong khi:** kết nối WS có xác thực với server thật; tắt server thì giao diện khóa và thử lại có giới hạn; đóng app không còn tiến trình Java treo (phần đầu của IT03).

**Ghi chú làm dở:** 04/10, B2 đã tích hợp A2/QD-03 trên `feat/t1-b2-network-heartbeat-ui`: giữ HEAD cũ `edd50cf`, merge main `f62c732` bằng `57f2a9c`, source cuối `6d5c7f4`. AuthenticatedWebSocketOpener dùng Bearer handshake mới mỗi reconnect; login candidate/proctor nối realtime, unscoped heartbeat/ACK và ERROR payload trực tiếp, 401/UNAUTHORIZED/1008 dừng retry/token cũ, bounded backoff, khóa UI model và shutdown. Test cuối 142/142 PASS (client 90), package PASS 142/142; real Spring/PostgreSQL smoke cuối PASS login/ACK/server-off/reconnect/revoke/expire/proctor/worker shutdown. GUI manual/LAN và review A NOT RUN. MonitoringTransport giữ send = write, ACK/ERROR = onMessage; PROCESS_OBSERVED/state chưa tích hợp server, không cài collector/C3/B3. Evidence: `evidence/t1-b2/2026-10-04-real-integration.md`; tracker giữ nguyên; PR/merge cuối xem report.

### T1-B3 · Màn giám thị tối thiểu
`4h` · phụ thuộc: T1-B2, T1-A3 · review: A

**Đầu ra:** danh sách thí sinh với trạng thái ONLINE/UNKNOWN và timeline cảnh báo.

**Cách làm:**
1. B3 chọn đăng ký listener trước WS, buffer push hữu hạn; mỗi CONNECTED đọc scope/roster/chi tiết HTTP để bù dữ liệu trong lúc offline.
2. Lưu cảnh báo theo `(attemptId,eventId)`; cùng sự kiện qua HTTP/WS chỉ một dòng, giữ order HTTP.
3. Hiển thị riêng trạng thái "dữ liệu cũ" khi chính màn giám thị mất kết nối, để không nhầm với ONLINE.
4. Chữ trên màn hình: "quan sát thấy [ứng dụng]", không viết "gian lận". UNKNOWN là "không còn xác nhận được".

**Dễ sai:** chỉ dựa vào message đẩy thì sẽ mất cảnh báo trong lúc giám thị rớt mạng.

**Xong khi:** mở ứng dụng bị hạn chế trên máy thí sinh → một dòng cảnh báo xuất hiện; kill client thí sinh → UNKNOWN; tắt mở lại màn giám thị vẫn đủ timeline, không dòng nào nhân đôi.

**Ghi chú bàn giao 04/10:** B3 CODE_COMPLETE trên base main `1e084d4`, branch `feat/t1-b3-proctor-dashboard`. Package dashboard tách DTO/JSON/API/model/controller/view; PROCTOR thay placeholder, candidate C3/A4 giữ nguyên. Roster assigned ACTIVE, selection, ONLINE/UNKNOWN/lý do/lastSeen, timeline quan sát với hai nguồn giờ, history thật/recoveredAt; loading/empty/error/stale riêng từng phần. Parser warning/presence giữ ACK/ERROR/request(1)/fragment. Revision long chính xác, HTTP không đè WS mới; equal conflict báo lỗi; roster authoritative và fresh auth/me trim adapter scope; event/history dedupe, generation/cancel tránh callback cũ. Bounded rows/buffer/body, không cache toàn bộ attempt hoặc thêm overflow-history API.

Tests mới54, tổng331/331; final mvn test/package PASS, REAL PG V4/Spring/HTTP/WS/B2/C3/dashboard và visible JavaFX component PASS. MOCK chỉ nguồn process B3; C3 regression có ProcessHandle/owned Edge REAL, A4 regression PASS. Stage proctor chọn dòng/tab, hard-kill candidate JVM riêng → UNKNOWN/history, mở lại → ONLINE/history giữ, offline stale ONLINE/reconnect/refresh/logout callback; ảnh thật và log trong `evidence/t1-b3/2026-10-04-verification.md`. Đây chưa phải GUI login/candidate toàn luồng MT01/MT08; LAN/package máy khác/human A review NOT RUN, prototype PARTIAL. Không thêm dependency, không đổi server production/V1–V4/plans/TRACKER. Dừng B3; commit/PR/merge cuối xem report.

### T1-B4 · Thử đóng gói Windows và audio
`3h` · phụ thuộc: T1-B1, T1-B2 · review: A

**Đầu ra:** app-image chạy được trên máy thứ hai; biết audio có phát được trong bản đóng gói không.

**Cách làm:**
1. Tạo app-image có kèm runtime bằng `jpackage`. Chép sang máy thứ hai không có IDE, chạy đăng nhập và nhận cảnh báo.
2. Thử phát một file audio mẫu bằng JavaFX Media. Định dạng JavaFX Media hỗ trợ gồm MP3, WAV (PCM), AAC; chốt định dạng (QD-07).
3. Thử đặt ứng dụng trong đường dẫn có dấu tiếng Việt và khoảng trắng.
4. Ghi lại mọi lỗi: codec, đường dẫn, module thiếu.

**Dễ sai:** đường dẫn file đưa cho `Media` phải là URI (dùng `file.toURI().toString()`), không phải đường dẫn thô. Bộ cài hoàn chỉnh làm ở T3-B5; ở đây chỉ cần app-image.

**Xong khi:** phần chặng 1 của IT01 có kết quả (qua hoặc không qua đều ghi rõ). Nếu không qua, demo từ IDE và nói rõ "chưa qua gate đóng gói".

**Ghi chú làm dở:** —

---

## Chặng 2 (09–15/10) — 20 giờ

### T2-B1 · Màn Reading theo nhóm đoạn văn
`4h` · phụ thuộc: T2-A1 (schema) · review: A

**Cách làm:**
1. Hiển thị đề theo nhóm: một đoạn văn kèm các câu hỏi của nó.
2. Một model đáp án duy nhất cho cả bài (map từ mã câu sang lựa chọn). Chọn, đổi, bỏ chọn đều sửa trên model này.
3. Xử lý đề lỗi (thiếu câu, nhóm rỗng) bằng thông báo, không để ứng dụng sập.

**Dễ sai:** nhiều nơi cùng giữ bản sao đáp án rồi lệch nhau. DTO phía client không có trường đáp án đúng; nếu thấy server gửi xuống thì báo A ngay.

**Xong khi:** làm được một đề mẫu 10–20 câu; bỏ chọn một câu thì model phản ánh đúng.

**Ghi chú làm dở:** —

### T2-B2 · Autosave và trạng thái lưu
`4h` · phụ thuộc: T2-B1, T2-A2 (contract) · review: A

**Cách làm:**
1. Mỗi thay đổi đáp án tăng `answerRevision` cục bộ.
2. Chờ một khoảng ngắn không có thay đổi rồi mới gửi (debounce, là tham số cấu hình).
3. Khi gửi: chụp một bản bất biến gồm requestId, writerEpoch, revision, toàn bộ map đáp án (ví dụ `Map.copyOf`). Retry gửi lại **đúng bản đó**, không tạo requestId mới, không lấy đáp án mới hơn nhét vào.
4. Ba trạng thái hiển thị: đã lưu (có ACK đúng revision), đang chờ, lỗi.

**Dễ sai:** retry với cùng revision nhưng nội dung đã đổi sẽ bị server trả CONFLICT. Thay đổi mới phải mang revision mới.

**Xong khi:** làm chậm server → giao diện vẫn thao tác được và hiện "đang chờ"; nhận ACK thì chuyển "đã lưu".

**Ghi chú làm dở:** —

### T2-B3 · Nộp bài và đồng hồ phía client
`4h` · phụ thuộc: T2-B2, T2-A3 · review: A

**Cách làm:**
1. Bấm nộp: khóa chỉnh sửa ngay, chụp payload cuối cố định, rồi gửi.
2. Mất phản hồi thì retry nguyên requestId, revision, đáp án.
3. Nhận trạng thái đã chốt thì hiển thị kết quả và không cho sửa.
4. Đồng hồ đếm ngược tính từ deadline do server trả. Hết giờ trên client thì khóa giao diện và hỏi server trạng thái; client không tự gia hạn và không tự coi là đã nộp.

**Dễ sai:** dùng giờ máy client làm chuẩn. Giờ máy có thể sai; server quyết định.

**Xong khi:** nộp rồi rớt mạng rồi retry vẫn chỉ có một kết quả; sau khi chốt không sửa được đáp án.

**Ghi chú làm dở:** —

### T2-B4 · Reconnect Reading và mở lại ứng dụng
`4h` · phụ thuộc: T2-B3, T2-A4 · review: A

**Cách làm:** cài đúng bảng "Reconnect và chuyển phiên ghi" trong hợp đồng mục 2. Sau khi kết nối lại, luôn lấy answers, savedRevision, state, deadline từ server rồi đối chiếu:

| Tình huống | Xử lý |
|---|---|
| Cùng lần chạy, cùng writer, server đã có đúng bản đang chờ | Đánh dấu đã lưu |
| Bản cục bộ có revision cao hơn server (ví dụ server 40, client 42) | Giữ bản cục bộ, gửi tiếp; không lùi revision về 40 |
| Cùng revision nhưng khác nội dung | Báo xung đột, dừng gửi tự động |
| Server mới hơn | Nạp bản của server |
| Mở lại ứng dụng hoặc được cấp writer mới | Lấy toàn bộ từ server, bỏ các bản đang chờ của writer cũ |

**Dễ sai:** sau khi mở lại ứng dụng mà tự gửi cache cũ lên. Một kết nối WS mới không có nghĩa là writer mới.

**Xong khi:** AT11 chạy được với trường hợp server 40 / client 42, mất ACK, và mở ứng dụng mới.

**Ghi chú làm dở:** —

### T2-B5 · Tiến độ trên màn giám thị
`2h` · phụ thuộc: T2-B4, T1-B3 · review: A

Màn giám thị hiện tiến độ làm bài **đã được server xác nhận** (không phải số client tự báo). Chụp ảnh và lưu log của luồng thật để dùng cho báo cáo.

**Ghi chú làm dở:** —

---

## Chặng 3 (16–22/10) — 20 giờ

Gate 22/10: LT01–LT04 và chạy được trên máy thứ hai.

### T3-B1 · Tải audio, checksum và thử loa
`4h` · phụ thuộc: T1-B4, T3-A1 (contract) · review: A

**Cách làm:**
1. Tải audio theo manifest ở luồng nền, có thanh tiến độ.
2. Tính SHA-256 từng file (`MessageDigest`) và so với manifest. Thiếu hoặc sai thì không báo READY, hiện nguyên nhân, cho tải lại.
3. Nút thử loa phát một audio mẫu trước khi thi.
4. Thử trong bản đóng gói với thư mục có dấu và khoảng trắng.

**Xong khi:** LT01 chạy được: xóa một file hoặc sửa một byte thì không READY.

**Ghi chú bàn giao 14/10:** T3-B1 code complete trên `feat/t3-b1-audio-download-readiness`, base main `2e784fa`, source `89082e3d43a322c0e0e210f393cc3b4bf49bd7da`. Adapter Bearer/manifest/writer exact long, cache `.part`/SHA-256 và worker riêng; candidate view có progress, WAV mẫu riêng và nút xác nhận nghe; kiểm lại byte local trước READY, chỉ hiện READY sau ACK đúng request/scope/writer/manifest. Tải lại xóa READY cũ và giữ writer trong cùng phiên; disconnect/đổi lượt/đóng view loại callback cũ. Java657 PASS, 0skip với Spring/HTTP/PG/Flyway thật; app-image runtime21.0.9 ở thư mục Unicode/khoảng trắng PASS6 checks, gồm Media lỗi và tự thoát. Build dùng argv ASCII và Windows SDK mt.exe nhúng UTF-8 cho launcher mới sinh; không đổi locale/JDK. [Guide](../AUDIO_CLIENT_PREPARATION.md), [evidence](../../evidence/t3-b1/2026-10-14-verification.md). Xác nhận nghe trong automation là SIMULATED; human A review, nghe thủ công, LAN/máy thứ hai NOT RUN. Tracker IN_PROGRESS, giờ null. Khi ghép Reading truyền writer hiện tại, không takeover lần hai; B2 phải verify cache lại trước phát. Không làm B2/B3/bộ cài.

### T3-B2 · Phát Listening theo lệnh server
`4h` · phụ thuộc: T3-B1, T3-A2 · review: A

**Cách làm:**
1. Nhận lệnh start có ID từ server thì phát file cục bộ và ghi `playbackStarted`.
2. Ghi nhớ ID lệnh start đã xử lý; lệnh trùng ID không phát lại.
3. Câu hỏi hiển thị khớp với đoạn đang nghe.

**Dễ sai:** hứa đồng bộ audio chính xác giữa các máy. Phạm vi chỉ là mỗi máy phát cục bộ sau khi nhận lệnh.

**Xong khi:** phần start lặp của LT02 chạy được.

**Ghi chú bàn giao 14/10:** T3-B2 code complete trên `feat/t3-b2-listening-playback`, kế thừa branch B1. `RealtimeClient` chỉ chuyển lệnh start/status đúng scope/schema; coordinator kiểm lại SHA-256, writer và manifest đã READY, lấy đề candidate ở worker, ghép câu theo `audioFile`, ghi journal local rồi phát playlist. `onPlaying` gửi STARTED, heartbeat 2 giây và COMPLETED qua production HTTP; trùng attempt/writer/startId bị chặn trong RAM và sau journal. Java666 PASS, 0skip với Spring/HTTP/PG/Flyway thật; app-image Unicode PASS7 gồm JavaFX Media và duplicate start không phát lại. [Guide](../LISTENING_CLIENT_PLAYBACK.md), [evidence](../../evidence/t3-b2/2026-10-14-verification.md). Media error/mất mạng chỉ khóa an toàn; báo INTERRUPTED và phục hồi theo policy thuộc B3. Review A, LAN/máy thứ hai, nghe thủ công, MP3/M4A NOT RUN; tracker IN_PROGRESS, giờ null.

### T3-B3 · Gián đoạn và mở lại đúng chính sách
`4h` · phụ thuộc: T3-B2, T2-B4 · review: A

**Cách làm:**
1. Khi phát hiện mất kết nối hoặc trình phát báo lỗi: dừng audio, khóa thao tác, ghi trạng thái gián đoạn và báo server khi có thể.
2. Reconnect hoặc mở lại ứng dụng: hỏi server trạng thái. Nếu có cờ gián đoạn thì giữ khóa, hiện thông báo chờ giám thị. **Không** tự phát lại từ đầu, **không** tự phát tiếp.
3. Chính sách này tách khỏi chính sách reconnect của Reading ở T2-B4; hai nhánh xử lý riêng.

**Dễ sai:** dùng lại code "reconnect rồi tiếp tục" của Reading cho Listening.

**Xong khi:** LT02 (lỗi media) và LT03 chạy được: rút mạng giữa audio, kill rồi mở lại, đều không nghe lại được.

**Ghi chú bàn giao 14/10:** T3-B3 code complete trên `feat/t3-b3-listening-interruption-recovery`, kế thừa B2. Client dừng/hủy MediaPlayer ngay khi lỗi media hoặc realtime mất kết nối, hủy heartbeat, ghi marker và gửi `INTERRUPTED` với `MEDIA_ERROR`/`NETWORK_LOST`; reconnect/mở view luôn GET status và không có nhánh autoplay/resume. Server `INTERRUPTED` giữ khóa; `ARMED`/`PLAYING` còn dang dở được fail closed và báo mất kết nối. Policy nằm riêng trong Listening, không nhập hay gọi reconnect Reading; T2-B4 chưa có trên nhánh nên gate tích hợp chung còn chờ. Java669 PASS, 0skip với PostgreSQL thật; app-image Unicode PASS8, gồm MediaPlayer thật dừng khi disconnect và reconnect không replay. [Guide](../LISTENING_INTERRUPTION_RECOVERY.md), [evidence](../../evidence/t3-b3/2026-10-14-verification.md). Review A, rút mạng/kill app thủ công, LAN/máy thứ hai và nghe thủ công NOT RUN; tracker IN_PROGRESS, giờ null.

### T3-B4 · Giao diện giám thị điều khiển ca
`4h` · phụ thuộc: T3-B3, T3-A2 · review: A

Các thao tác: import đề và tạo ca, xem READY của từng thí sinh, start, xem kết quả, đánh dấu lượt cần tổ chức lại. Mỗi thao tác gọi server và server kiểm quyền. Không làm quản trị ngân hàng câu hỏi hay soạn đề.

**Xong khi:** phần giao diện của LT04 chạy được: tổ chức lại tạo attempt mới, attempt cũ vẫn xem được nguyên vẹn.

**Bàn giao 15/10/2026:** [Điều khiển ca tối thiểu](../PROCTOR_SESSION_CONTROLS.md), [evidence](../../evidence/t3-b4/2026-10-15-verification.md). Source `06fd215`, clean Java686/0skip + REAL GUI integration LT04 + FULL dashboard smoke PASS; media rerun8PASS, initial file-lock FAIL giữ trong report. Backend A1/A2 giữ nguyên; UI trong dashboard hiện có, không lấy roster ACTIVE làm nguồn kết quả đã chốt. Callback có generation, request start/reorganize giữ ID/nội dung qua mất response; lượt mới READY lại và lượt cũ chỉ đọc.

**Ghi chú làm dở:** Human A review/nghe thủ công/native FileChooser/LAN NOT RUN. Chưa ghép Reading/Listening PR35, chưa B5/E1/E2; FULL mặc định. Không đổi tracker/kế hoạch/.env/migration. Kết quả tests/GUI theo report B4, không dùng số cũ của B1–B3 làm lượt mới.

### T3-B5 · Bộ cài và audio trên máy thứ hai
`2h` · phụ thuộc: T3-B4 · review: A

**Cách làm:** tạo bộ cài Windows bằng `jpackage` (loại `exe` hoặc `msi` cần cài WiX Toolset trên máy build). Thử cài, chạy, gỡ trên máy không có IDE. Ghi build SHA và phiên bản Windows.

**Dễ sai:** gọi app-image là "bộ cài đã xong". Nếu bộ cài lỗi thì ghi rõ lỗi và người xử lý.

**Xong khi:** IT01 qua trên máy thứ hai với bộ cài.

**Ghi chú làm dở:** 15/10/2026: phần đóng gói/local kiểm xong trên `feat/t3-b5-windows-installer`, source `acac38d`, MSI0.3.5 kèm runtime21.0.10/JavaFX. Clean full687 PASS, 0skip; cài/launch không agent và UI B4/FULL/audio/READY/start/WS gián đoạn/đóng/gỡ PASS trên install Unicode bị chặn ghi bằng tài khoản thường. [Lệnh/checklist](../WINDOWS_INSTALLER.md), [report/hash/artifact](../../evidence/t3-b5/2026-10-15-verification.md). Còn máy Windows thứ hai không IDE, LAN, nghe bằng người thật và human A review; IT01 PARTIAL, không tự chốt nghiệm thu/tracker hoặc ghép PR35/T4/E1/E2.

---

## Chặng 4 (23–27/10) — 14 giờ

### T4-B1 · Regression desktop trên máy khác
`4h` · phụ thuộc: T3-B5, T3-A4 · review: A

Trên máy khác, từ bộ cài: Reading, Listening, khởi động lại ứng dụng, ngắt mạng. Chạy AT11, LT01–LT04, IT01–IT04. Lưu log giao diện và audio, phiên bản OS, build SHA. Cập nhật `docs/KIEM_THU.md`.

**Ghi chú làm dở:** —

### T4-B2 · Chạy benchmark polling
`4h` · phụ thuộc: T4-B1, T4-C1 (harness của C) · review: A

**Cách làm:** dùng harness C giao trước 24/10. Chạy trên desktop thật: 4 chu kỳ (250, 500, 1.000, 2.000 ms) × 3 lần lặp = 12 lần, mỗi lần 15 giây khởi động và 120 giây đo. Giữ nguyên máy, ứng dụng nền, kịch bản. Lưu file raw nguyên vẹn kèm metadata.

**Dễ sai:** sửa tay file raw, bỏ lần chạy "xấu" không ghi lý do, đổi ứng dụng nền giữa các lần. Bạn chịu trách nhiệm chạy và lưu; C phân tích.

**Ghi chú làm dở:** —

### T4-B3 · Viết Implementation và hướng dẫn chạy
`4h` · phụ thuộc: T4-B1, T3-B4 · review: A

Phần Implementation phía client cho báo cáo (ảnh chụp từ bản thật). README: yêu cầu hệ thống, cài đặt, cấu hình DB, chạy server, chạy client, chạy nhiều client, cấu hình LAN/tường lửa/port, test, giới hạn, danh sách thư viện ngoài. Quay một video dự phòng ghi rõ ngày và build.

**Ghi chú làm dở:** —

---

## Chặng 5 (28–31/10) — 11 giờ

### T5-B1 · Thử trên máy sạch theo README
`3h` · phụ thuộc: T4-B3, T5-A1 · review: A

Nhờ người không viết phần đó làm theo README trên máy chưa cài gì: dựng DB, build server, cài client, đăng nhập, thi, giám sát. Ghi từng bước bị vướng và sửa README. Máy của chính tác giả đã cài sẵn không tính là máy sạch.

### T5-B2 · Chốt bản phát hành client
`3h` · phụ thuộc: T5-B1 · review: A

Bộ cài, đề và audio mẫu, cấu hình mẫu, checksum, số phiên bản. Gỡ credential thật và đường dẫn cá nhân. Chạy thử nhanh sau lần đóng gói cuối.

### T5-B3 · Luyện demo và bảo vệ
`3h` · phụ thuộc: T5-B2, T5-A1 · review: A

Kịch bản 5–7 phút, thao tác thật, có video dự phòng. Bạn giải thích luồng giao diện, audio, revision; A giải thích lại luồng của bạn; C đặt câu hỏi về lỗi phiên.

**Ghi chú làm dở (chặng 5):** —

---

## Task review (T1-BR … T5-BR) — 2 giờ mỗi chặng

Bạn review phần của **C**. Mỗi chặng: 1 giờ đọc code, test, tài liệu của C; 1 giờ cho các buổi tích hợp ngắn. Review theo từng buổi, không dồn cuối chặng.

Khi review code của C, kiểm tra:
- Collector có chạy trên luồng giao diện không? Có dừng sạch khi đóng app không? Có bị bật trên role giám thị không?
- Một process còn chạy có sinh event ở mỗi lần quét không?
- `eventId` có giữ nguyên khi retry không?
- Queue đầy thì có đếm số bị bỏ không?
- Full và delta có dùng cùng tập process và cùng quy tắc sinh event không?
- Số đo trong thực nghiệm có định nghĩa rõ và tái lập được không?

Bạn cũng là người duyệt quyết định giữ hay cắt delta ngày 20/10. Ghi kết quả review vào `docs/NHAT_KY.md`.

## Phần báo cáo bạn viết

Implementation phía client, luồng làm bài Reading và Listening, chính sách gián đoạn, đóng gói; README.

## Câu hỏi bảo vệ bạn phải trả lời được

- Vì sao không gọi mạng trên luồng giao diện? Bạn chuyển việc sang luồng nền bằng gì?
- Client retry một request lưu như thế nào? Vì sao phải giữ nguyên requestId và nội dung?
- Server đang ở revision 40, client ở 42 sau khi mất mạng: client làm gì?
- Vì sao Listening không tự phát lại sau reconnect trong khi Reading được làm tiếp?
- Nếu thí sinh sửa client để bỏ khóa giao diện thì chuyện gì xảy ra?
- Kết nối WebSocket được mở, xác thực và đóng như thế nào?
- Đóng ứng dụng thì các luồng nền được dọn ra sao?

Bạn cũng phải vẽ lại được luồng của A (khóa, deadline, commit rồi ACK) và của C (snapshot, epoch).


## Bàn giao tích hợp15/10/2026

Nhánh `integration/reading-listening-20261015`, base8f5ac40, history2ca4121 và Reading65f5123. Guide `docs/INTEGRATION_READING_LISTENING.md`; evidence `evidence/integration/2026-10-15-verification.md`. Phạm vi review: B: Reading/Listening/shared writer/Media/callback/reconnect. Clean test833 PASS (protocol43/client569/server220/spike1), PostgreSQL18.6 thật và 0skip; package833 PASS. GUI smoke8nhóm PASS mới; chưa nâng toàn case human/LAN. FULL default, tracker không đổi.

**Ghi chú làm dở:** human A/B/C/LAN hai máy/installer/E1/E2 NOT RUN; ID ACTIVE đặc biệt còn contract QD29. Không dùng alias đọc status làm quyền monitoring; không merge main khi chưa được người dùng chấp thuận. Kết quả cuối/SHA/log trong evidence, không kế thừa PASS nhánh nguồn.

### Ghi chú làm dở overnight 15/10/2026

Phase01 backend checkpoint đề xuất (A/C review pending), targeted49 PASS; xem [evidence](../../evidence/overnight-20261015/phase-01/verification.md). Tiếp tục phase02–06, không coi code là human/LAN acceptance.

Overnight phase03 T2-B4 AT11: exact40/42/ACKfence mới đã kiểmREAL HTTP/PG, no2writer; GUI8group samewriterReadingListening PASSsubset. AT11wholePARTIAL. [Matrix](../../evidence/overnight-20261015/phase-03/AT11_matrix.md).

T3-B4/T2-B5 overnight: UI import/create/READY/start/status/reorg và progress committed đã nối API production; GUI11groups PASS subset. Còn FileChooser/manual/LAN/2candidateGUI và review thật. [Guide](../PROCTOR_SESSION_PROGRESS.md).

T3-B5 overnight: scripts/config/negative tests có; rawASCIIimageMedia8 PASS, cần mt.exe/WiX để nghiệm thu Unicode/installer. Máy2/nghe/cài/gỡ NOT RUN. [Guide](../WINDOWS_PACKAGING.md).

### Handoff overnight15/10 — local gate xong, nghiệm thu còn PARTIAL

SourceJava c1601b5 clean/package848,Flow41/GUI11/C1-C2 PASS; E1 đủ12REALruns, cần C review. B cần nghe/rút mạng/kill thật,2máyLAN và installer được phép;SDKmt/WiX đang thiếu. Import FileChooser/manual còn cần người thử. Sau tạo ca/tổ chức lại bấm “Làm mới quyền và dữ liệu” của dashboard để nạp scope monitoring; progress API độc lập vẫn đọc assignedfinal. Không xóa marker/autoReplay, không thay tracker khi chưa xác nhận. [Summary](../../evidence/overnight-20261015/00-summary.md).

Lưu ý ref trước công bố: main `c0fd903` đã có B4/PR #36 và Windows MSI/PR #37. Code overnight chưa hòa với hai phần này; owner cần chọn một controller và một pipeline, không ghi đè script cùng tên. Xem [đối chiếu upstream](../../evidence/overnight-20261015/UPSTREAM_DIVERGENCE.md). Không kế thừa test/artifact của upstream cho source overnight.


## Handoff bản thống nhất16/10/2026 — HUMAN REVIEW PENDING

Một Application Reading/Listening/history/shared writer; B4#36 duy nhất +progress38; MSI#37 duy nhất, config/preflight port có giới hạn. MSI và parent Unicode BLOCKED, còn nghe thật/FileChooser/LAN/máy2. Checkpointb755646: clean/package mỗi lượt872PASS/1skip, Flow41/GUI11/nativeAudio8/production EXE+PG PASS subset; không nghiệm thu toàncase. [Báo cáo/SHA/count/failure/manual](../../evidence/unified-20261016/verification.md). Không đổi tracker/kế hoạch hoặc tự duyệt A/C.


### Ghi chú làm dở — QA PR39 ngày16/10/2026 (B bàn giao, chưa owner ký)

Bàn giao QA PR39: source9bfc75e sửa3lỗi B (BOM/title/cache race), Maven875+1skip và installer876+0skip mới; MSI037/native History/reorg PASS local, artifact/hash trong report. Nghe thật/DPI/codec ngoàiWAV/máy hai/LAN chưa nghiệm thu; user chỉ có một máy, runbook/testdata/bundle đã chuẩn bị. Shared QA DB còn cleanup do container đang dừng, không tự restart. Đề nghị A review B fixes, không tự ký A/C hoặc cập nhật tracker. [Báo cáo](../../evidence/b-pr39-acceptance-20261016/FINAL_REPORT.md).

### Bàn giao F01 — 16/10/2026

Code `d19c639` trên nhánh local `qa/f01-listening-result-20261016` từ PR40HEAD3fc5bb4: Result giữ nguyên `listeningInterrupted` từ hai DTO; ScoreResultDialog cảnh báo gián đoạn cho SUBMITTED/TIMED_OUT, không tự hủy điểm. History không takeover; thêm regression mapping/session/history/GUI và test HTTP/PG trong file A, cần A review. [Báo cáo](../../evidence/f01-20261016/REPORT.md).

**Ghi chú làm dở:** F01 FIXED và MSI0.3.8 local PASS, chưa human A/C review/LAN/máy2/nghe thật. Clean test/package896PASS+2SKIP; installed FullSuite898PASS+0SKIP; AT11 41/0GAP,GUI11,C1/C2 PASS sau giữ C1 FAIL cleanup đầu (nguyên nhân chưa chứng minh). Không sửa monitoring để né FAIL; đổi temp TEST Windows cho lượt C1 cuối. Cluster TEST riêng đã dừng/0schema;84rawE1 giữ checksum. Không đổi tracker/push/merge/approve PR39/40; đưa patch qua PR40 rồi PR39 một lần để tránh nhân đôi.


### Ghi chú làm dở — RC17/10/2026

MSI0.3.9 installed906PASS local; cần B review F01, hòa PR45 với fix race40 và policy junction; manual hearing/máy2/LAN chưa nghiệm thu. Source TEST c224ba8, không merge nhánh nguồn/main hoặc ký thay owner. [Bàn giao](../../evidence/integration-final-20261017/REPORT.md).


### Ghi chú làm dở — PR47 retarget main, 18/10/2026

Source `3d93820` sửa RealtimeClient thuộc B: giữ dấu correlation đã hủy có giới hạn kể cả ACK đã lấy pending, bỏ phản hồi muộn lặp, retry/reconnect xóa đúng dấu; UNAUTHORIZED toàn phiên vẫn dừng kết nối. Phối hợp C review FullSnapshotDelivery deadline CLOSE/guard/worker. Focused148 PASS, riêng adapter63; clean/package mỗi lượt928 PASS/5SKIP với PG TEST thật; T3-C2 PASS, T3-C4 lượt mới 42/42 PASS LOCAL. C approval còn thiếu, không tự merge/approve hoặc sửa QD/tracker/MSI. [Hồ sơ C review](../../evidence/pr47-stabilize-20261018/REPORT.md).

### Ghi chú làm dở — T3-C4 physical LAN, 18/10/2026

Chưa có máy thứ hai; human B **PENDING**, LAN **NOT RUN/0case**. [Runbook](../T3_C4_PHYSICAL_LAN_ACCEPTANCE.md) +41case/ZIP sourceTask1 `3044e5b`, runtime `3d93820` đã chuẩn bị; kit29checks PASS trên Windows và bản ZIP, không thay human review. B cần cấp máyB, kiểm hash/GUI/process/Reading/Listening thật, rút LAN vật lý F01–F12, chứng minh A không ảnh hưởng, B recovery không reset/autoplay và cleanup. Cùng C đối chiếu ACK/event/gap/epoch/SQL; fault probe SIMULATED/MOCK riêng, runtime chưa thử. Không dùng ZIP2b6c9ce, không đổi default FULL/MSI/QD/tracker hoặc ký hộ C. [Hồ sơ](../../evidence/t3-c4-physical-prep-20261018/REPORT.md).

### Bàn giao công bố Task2.1 — 18/10/2026

Người dùng đã cho phép push nhánh chuẩn bị và mở Draft PR vào `gate/t3-c4-lan-20261017`, không merge. B dùng [runbook GitHub/nhận ZIP](../T3_C4_PHYSICAL_LAN_ACCEPTANCE.md), liên hệ người giữ máy build qua kênh riêng, kiểm SHA256 trên hai máy rồi chạy41case. LAN0/41 NOT RUN, human B PENDING; không dùng kit29PASS thay review/nghe/LAN thật. [Hồ sơ công bố](../../evidence/t3-c4-physical-prep-20261018/PUBLISH.md).

Bộ bàn giao đã ở [Draft PR52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) vào nhánh PR47; chưa merge. B review PENDING và LAN0/41 NOT RUN giữ nguyên.

## Bàn giao corrective audio P1 · 18/10/2026
T3-B1/QD33: source56a27e9 local giữ cache hợp lệ, khóa JVM+process phối hợp publication/verify, không giữ khóa HTTP. [Báo cáo và lệnh](../../evidence/audio-cache-p1-20261018/REPORT.md). Exact-main747/1280FAIL → bản sửa0/1280, hash đúng/partial0; targeted54PASS/2SKIP. Codex review kỹ thuật theo người dùng chỉ định; human chữ ký không tự tạo. Ghi chú làm dở: lần push đầu bị automatic approval review từ chối; ngày19/10 người dùng đã cho phép push và mở corrective PR/CI; main vẫn thiếu fix. B chạy nghe/máy2/LAN, xác nhận dispositionPR45; final MSI chỉ nghiệm thu sau source main đã tích hợp.


## Ghi chú tích hợp19/10/2026
Main8a43a56 có PR47/57 và test56; sourcece38a5f clean/package942PASS/4SKIP, PG68PASS/2SKIPsubset, GUI11/Flow41/MODE8×5PASSlocal. Codex review kỹ thuật theo người dùng giao, không ghi human signatures. PR52/51 đã reuse vào nhánh tích hợp; A5 nhãn envelopev0 được sửa. HarnessC4 localFAIL do TEST backoffnormalpeer được giữ bằng chứng;1072f77 giữ lại productionbackoff, chờ rerun. Còn finalmain/CI/MSI và physicalLAN0/41/hearing/máy2; tracker không đổiDONE, FULL baseline.


19/10/2026 — CI exact-main0363995 run39150213101 FAIL: AudioPreparationTest JUnit TempDir xóa next-speaker WAV đồng thời với cleanup async của production close(). Sửa TEST chờ Files.notExists(sample) bằng await5s hiện có, cho cả AfterEach và view kế tiếp; giữ mọi assertion READY/epoch, không đổi production close/FX. Focused10case×5lượt PASS trên Windows (fixture API/media MOCK), không gọi50case duy nhất. Clean main0363995 test/package mỗi lượt942PASS/4SKIP nhưng CI này vẫn FAIL; không gán CI củaPR58 cho main. Full/PG/CI sau corrective sẽ ghi tiếp; LAN0/41, hearing/máy2 NOT RUN.


19/10/2026 — [Kết quả thực thi cuối](../../evidence/stage3-execution-20261019/REPORT.md), [artifact và thao tác máy thật](../STAGE3_FINAL_HANDOFF.md). Buildsourcecfbba58, haiP1/main/CI/PG/regression/MSIlocal đã kiểm. Reviewer kỹ thuật Codex theo user, không chữ ký vai. Việc dở: nhận MSI/ZIP mới, nghe LT01–04, install máy2 và cùng C chạy41case; không lấy localPASS thay nghe thật. PR45 chỉ đóng sau disposition B.

## Ghi chú làm dở — EX-02 candidate / ADR-V2-03, 19/10/2026

Thiết kế [EX02/D10](../EX02_RICH_EXAM_CONTRACT_V1.md) và [ADR](../plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md) đã TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE theo [nguồn19/10](../plan-v2/EX02_TEAM_APPROVAL_20261019.md), không chữ ký/GitHub review riêng. EX06 bắt buộc lobby shared writer→prefetch Part1/decode/UI_PREPARED→Audio+Visual ACK→START→rich DTO→two visible pulses→play. ListeningPlayback.prepare hiện GETv1 và Pane tựplay khi onReady: bridge V2 rich→existing segments/player/journal, no v1 bypass; budget3000ms, heartbeat6000ms giữ, lỗi sau commit interruption/no reset. [Fixtures](../plan-v2/fixtures/ex02/README.md) chỉ design; real Stage/layout/snapshot/pulse/media test V15–V17 NOT RUN. Integration cần EX03/05/07, không chờ lại approval D01–D10 hoặc tự claim GUI/hearing PASS.

## Ghi chú làm dở — EX05, 19/10/2026

[Checkpoint](../EX05_AUTHENTICATED_IMAGES.md): A code image resolver/manifest/route, B cần tích hợp visual RAM/renderer, C review kỹ thuật auth/lock/path/commit. Agent review không human approval. EX07 authority chưa có nên ảnh fail-closed; PG mới đang chạy, GUI/LAN/hearing NOT RUN. V13/V14 MERGE BLOCKED; tracker/hours giữ. Tiếp tục EX07 trên branch stacked riêng.

20/10/2026: EX05 source05cc694 clean verify1127PASS/6SKIP (29case mới PASS), PG18.1/HTTP thật; [checkpoint/evidence](../EX05_AUTHENTICATED_IMAGES.md). EX07 tiếp tục branch stacked, chưa authority/FE integrated; V13/V14 MERGE BLOCKED, GUI/LAN/hearing NOT RUN.

**EX-07 — Ghi chú làm dở (20/10/2026):** nhánh `feat/ex07-rich-session-lifecycle-20261019` HEAD có code + test PG/HTTP local PASS ([chi tiết](../EX07_SESSION_LIFECYCLE.md)). A/C review lock/transaction và thứ tự Flyway V13/V14/V15; B dùng §1 của tài liệu để làm EX-06. GUI/LAN/hearing NOT RUN; không merge/DONE.

**EX-06 — Ghi chú làm dở (20/10/2026):** nhánh `feat/ex06-javafx-rich-renderer-20261020` (trên EX-07) đã có màn TOEIC Room V2 cho 7 Part, lobby, Visual READY, START và bridge Listening; test GUI và E2E thật PASS local ([bàn giao](../EX06_JAVAFX_RICH_RENDERER.md)). Còn: B review UX/visual, nghe thật bằng loa, MSI/máy hai/LAN, tạo lại audio view khi reclaim writer sau START, màn login/home theo mockup. A review contract client↔EX-07. Không merge/DONE.

**Tích hợp EX-06 + Proctor UI + V2 — Ghi chú làm dở (20/10/2026):** nhánh `integrate/ex06-proctor-20261020`. Launcher route PROCTOR sang `ProctorRedesignView`; panel V2 (`ProctorV2SessionPane`/`ProctorV2Controller`/`ProctorV2ApiClient`) trong "Điều khiển ca" ([bàn giao](../INTEGRATION_EX06_PROCTOR_20261020.md)). E2E thật PASS; còn: chạy runbook UI trên Windows, import rich trong UI (chưa có), danh sách user/ca (chưa có API), tab V1 và `ScoreResultDialog` chưa theo style mới. Không merge/DONE.

## MON-03 — bàn giao focus candidate, 20/10/2026

Nhánh `feat/v2-b-mon03-focus-monitoring-20261020`, base `d2c8f01`: adapter typed
signal, JavaFX Stage binding, MON-07 capability/queue và candidate lifecycle đã tích hợp;
mặc định OFF. Focused 46PASS/0SKIP, trong đó 9 case MON-03 trực tiếp.
Full clean test 1201PASS/144SKIP/0FAIL/ERROR.
[Thiết kế/runbook](../MON03_FOCUS_MONITORING.md), [evidence](../../evidence/v2-b-mon03-focus-20261020/REPORT.md).
Còn reviewer C và manual Alt+Tab/PROCTOR/REAL WS-PG NOT RUN; không đánh dấu DONE.

## MON-04 — bàn giao window state, 20/10/2026

Nhánh `feat/v2-b-mon04-window-state-20261020` stacked trên MON-03 `d5c568a`.
Minimize/restore/fullscreen state dùng chung run/sequence/queue với focus, có baseline INITIAL,
same-pulse debounce và dispose cleanup; observer không ép focus/fullscreen hay hủy bài.
Focused55PASS; full clean1210PASS/144SKIP/0FAIL/ERROR.
[Thiết kế/runbook](../MON04_WINDOW_STATE_MONITORING.md), [evidence](../../evidence/v2-b-mon04-window-20261020/REPORT.md).
Manual Stage/REAL WS-PG và review C NOT RUN; không đánh dấu DONE.

## MON-05 — bàn giao display monitoring, 20/10/2026

Nhánh `feat/v2-b-mon05-display-monitoring-20261020` stacked trên MON-04 `0ae71bf`.
JavaFX Screen count có baseline INITIAL, CHANGED dedupe, polling hữu hạn 1–30 giây, cleanup
và status READY/UNKNOWN/UNSUPPORTED/ERROR dùng chung run/sequence/queue với focus/window.
Full clean 1219PASS/144SKIP/0FAIL/ERROR; 9 case MON-05 trực tiếp là MOCK/TEST seam.
[Thiết kế/runbook](../MON05_DISPLAY_MONITORING.md), [evidence](../../evidence/v2-b-mon05-display-20261020/REPORT.md).
Cắm/rút màn hình thứ hai, REAL WS-HTTP-PG/proctor và review C NOT RUN; không đánh dấu DONE.

## MON-06 — bàn giao exam-local activity, 21/10/2026

Nhánh `feat/v2-b-mon06-exam-activity-20261021` stacked trên MON-05 `66f897c`.
Chỉ tổng hợp navigation/answer-action và idle monotonic; không ghi ID/nội dung, phím, clipboard,
URL, chat, file hay verdict. Threshold configurable và clock gap là PARTIAL, không idle giả.
Focused94PASS; JavaFX Stage MON-06 1PASS; full clean1232PASS/146SKIP/0FAIL/ERROR.
[Thiết kế/runbook](../MON06_ACTIVITY_MONITORING.md), [evidence](../../evidence/v2-b-mon06-activity-20261021/REPORT.md).
REAL WS-PG/V18, manual submit/timeout/history và review C NOT RUN; không đánh dấu DONE.


21/10/2026 — MON-09 C+B: [timeline đa tín hiệu](../MON09_MULTI_SIGNAL_TIMELINE.md), [evidence](../../evidence/cb-mon09-timeline-20261021/REPORT.md).
Base dcb124d; runtime5066347/build1c1523b. Dashboard hiện hữu đọc typed history fixed-through100×3/cycle, poll3s/budget12s/500rows; process state riêng, Listening status projection. Sửa producer identity trùng collector lease có FAIL→9ACK. Real FX existing sources1PASS; MOCK real WS/PG/UI1PASS(A19/B1/drop3); REAL CIM1PASS(8READY0/ACK1/UI1/0USB). Full verify1263PASS/154SKIP/0lỗi, GUI cũ12PASS, JAR24checks; sort oracle sạch05e154a8PASS. TESTschema0/PGstop0/workersclosed, raw ignored. FULLdefault/experimentOFF, USB0/12/physicaldisplay/desktop/LAN NOT_RUN, owner/ADR/policy/gate pending, MON09chưaDONE. Không approval thay B hoặc đổi backlog.
Ghi chú làm dở: cần owner review/hardware/manual/LAN; không đánh MON-09 DONE. Đây là ghi nhận công việc kỹ thuật theo ủy quyền C+B, không phải thành viên B đã review/approve.


21/10/2026 — MON-08 C+B: [rule/aggregation thử nghiệm](../MON08_EXPERIMENTAL_ALERTS.md), [report/ảnh/receipt](../../evidence/cb-mon08-alerts-20261021/REPORT.md).
Base1507591; runtime1e4b1ca/build+TEST9e31d50.4rule EXPERIMENTAL, ngưỡng3/6 trên bucket60s/window300s; server committed/scoped/ACTIVE/capped read model → timeline hiện có, không cache/migration/observer/persistentpipeline. Baseline95/focused134PASS; fullverify1302PASS/155SKIP/0lỗi, GUI12PASS, JAR21checks. MON08MOCK→realWS/HTTP/PG/Scene1PASS(10initialinputs/focus6/USB1/actual3drops/sourcecap20TEST); OFFlegacy1PASS. GiữFAILoverflow/no-store/fixture và0schema/PGstop0. FULLdefault/cả2experimentOFF, USB0/12/physicaldisplay/OSdesktop/LAN NOT_RUN, owner/policy/ADR/gatespending, MON08chưaDONE; không approval thay B/backlog/E1/E2.
Ghi chú làm dở: cần review human owner/ngưỡng/false positives và physical/manual/LAN acceptance; không MON08DONE hoặc policyAPPROVED. Công việc C+B được user ủy quyền, không là B đã review/approve.


21/10/2026 — MON01–09 integrated TEST: [ma trận/runbook](../MONITORING_INTEGRATION.md), [report/receipt](../../evidence/monitoring-integrated-20261021/REPORT.md). Base65fa83b; build/testdffa227, runtime51e65dc. Fix shared status ownership/retire và khóa Start/chọn ca khi fresh scope pending; có FAIL→regression PASS. Integrated Mock4PASS + explicit CIM4PASS (cùng4case, không8test mới), app controls/stop-restart/logout/account isolation + terminal/revoke, owned ProcessHandle/FX/WS/PG. Fullverify1305PASS/159SKIP/0lỗi, 674class/resource match4JAR. Giữ historical allProcesses app startup failures chưa rõ exact cause; fixture cuối owned handle, không claim latency production đã sửa. Schema0/PGstop0/readers/owned workers dọn; FULLdefault/cả2experimentOFF, USB0/12/second-display/desktop/LAN0/41NOT_RUN; policy/ADR/gates/human review pending, không MON DONE.

Ghi chú làm dở: owner review và hardware/desktop/LAN/policy pending; production USB binding chưa triển khai. Historical broader-fixture startup failure không được gọi đã root-cause. Codex review kỹ thuật theo ủy quyền, không người B/C ký duyệt.


21/10/2026 — Bổ sung producer FX reconnect a12d349:1case mở rộng PASS/0SKIP, production tự refresh capability/scoped heartbeat sau loopback reconnect, transition mới COMMIT/ACK/history; không test-side confirm. JAR build dffa227/main output674files giữ byte-identical, không gán nhãn build mới; ACK11/discarded unconfirmed2/drop0,0schema/PGstop0. Chi tiết tại report integrated; full/common campaign vẫn dùng source đã ghi riêng. Không hardware/policy/human approval.

### 21/10/2026 — Demo desktop monitoring từ main 2a596de
Theo ủy quyền người dùng: thêm -Desktop vào entrypoint demo hiện có, hai production JAR/JVM riêng, TEST DB/API và thao tác desktop do người dùng thực hiện. Signal/alert bật theo process demo, FULL/defaults giữ nguyên. Hướng dẫn: docs/MONITORING_DESKTOP_DEMO.md. Trạng thái: PENDING_USER_ACTION, chưa nghiệm thu desktop/USB/LAN, chưa DONE/gate/policy/approval. Computer Use lỗi khởi tạo kernel assets. Kiểm chứng và cleanup sẽ ghi theo lượt thực tế.

21/10/2026 — Chốt demo desktop từ main2a596de: user thao tác hai production JVM thật, xác nhận proctor thấy Chrome/focus/window tại Timeline đa tín hiệu, Thoát màn thi làm Dừng mờ rồi logout/close. Build/package4aacab4, source Java không đổi; after-exit0 monitoring workers, sequence1810/typed113 giữ nguyên; user đóng cả2trước teardown, apps/server/workload0, schema0/dropDB0/PGstop0/PIDabsent. Raw local ignored, không desktop programmatic/MOCK acceptance. CI4aacab4:1307PASS/157SKIP/0lỗi, Python27PASS; CI final/merge ghi tại PR96. Hướng dẫn docs/MONITORING_DESKTOP_DEMO.md; report/receipt evidence/monitoring-desktop-20261021. FULL default/experiments repository OFF; hardware/LAN/ADR/policy/gates/human approval pending, không MON DONE.
