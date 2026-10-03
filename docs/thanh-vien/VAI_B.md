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

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

### T1-B3 · Màn giám thị tối thiểu
`4h` · phụ thuộc: T1-B2, T1-A3 · review: A

**Đầu ra:** danh sách thí sinh với trạng thái ONLINE/UNKNOWN và timeline cảnh báo.

**Cách làm:**
1. Khi mở màn hình hoặc sau reconnect: tải timeline từ DB qua HTTP, sau đó mới nhận message đẩy.
2. Lưu cảnh báo theo `eventId` (map), nên message đến hai lần không tạo hai dòng.
3. Hiển thị riêng trạng thái "dữ liệu cũ" khi chính màn giám thị mất kết nối, để không nhầm với ONLINE.
4. Chữ trên màn hình: "quan sát thấy [ứng dụng]", không viết "gian lận". UNKNOWN là "không còn xác nhận được".

**Dễ sai:** chỉ dựa vào message đẩy thì sẽ mất cảnh báo trong lúc giám thị rớt mạng.

**Xong khi:** mở ứng dụng bị hạn chế trên máy thí sinh → một dòng cảnh báo xuất hiện; kill client thí sinh → UNKNOWN; tắt mở lại màn giám thị vẫn đủ timeline, không dòng nào nhân đôi.

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

### T3-B2 · Phát Listening theo lệnh server
`4h` · phụ thuộc: T3-B1, T3-A2 · review: A

**Cách làm:**
1. Nhận lệnh start có ID từ server thì phát file cục bộ và ghi `playbackStarted`.
2. Ghi nhớ ID lệnh start đã xử lý; lệnh trùng ID không phát lại.
3. Câu hỏi hiển thị khớp với đoạn đang nghe.

**Dễ sai:** hứa đồng bộ audio chính xác giữa các máy. Phạm vi chỉ là mỗi máy phát cục bộ sau khi nhận lệnh.

**Xong khi:** phần start lặp của LT02 chạy được.

**Ghi chú làm dở:** —

### T3-B3 · Gián đoạn và mở lại đúng chính sách
`4h` · phụ thuộc: T3-B2, T2-B4 · review: A

**Cách làm:**
1. Khi phát hiện mất kết nối hoặc trình phát báo lỗi: dừng audio, khóa thao tác, ghi trạng thái gián đoạn và báo server khi có thể.
2. Reconnect hoặc mở lại ứng dụng: hỏi server trạng thái. Nếu có cờ gián đoạn thì giữ khóa, hiện thông báo chờ giám thị. **Không** tự phát lại từ đầu, **không** tự phát tiếp.
3. Chính sách này tách khỏi chính sách reconnect của Reading ở T2-B4; hai nhánh xử lý riêng.

**Dễ sai:** dùng lại code "reconnect rồi tiếp tục" của Reading cho Listening.

**Xong khi:** LT02 (lỗi media) và LT03 chạy được: rút mạng giữa audio, kill rồi mở lại, đều không nghe lại được.

**Ghi chú làm dở:** —

### T3-B4 · Giao diện giám thị điều khiển ca
`4h` · phụ thuộc: T3-B3, T3-A2 · review: A

Các thao tác: import đề và tạo ca, xem READY của từng thí sinh, start, xem kết quả, đánh dấu lượt cần tổ chức lại. Mỗi thao tác gọi server và server kiểm quyền. Không làm quản trị ngân hàng câu hỏi hay soạn đề.

**Xong khi:** phần giao diện của LT04 chạy được: tổ chức lại tạo attempt mới, attempt cũ vẫn xem được nguyên vẹn.

**Ghi chú làm dở:** —

### T3-B5 · Bộ cài và audio trên máy thứ hai
`2h` · phụ thuộc: T3-B4 · review: A

**Cách làm:** tạo bộ cài Windows bằng `jpackage` (loại `exe` hoặc `msi` cần cài WiX Toolset trên máy build). Thử cài, chạy, gỡ trên máy không có IDE. Ghi build SHA và phiên bản Windows.

**Dễ sai:** gọi app-image là "bộ cài đã xong". Nếu bộ cài lỗi thì ghi rõ lỗi và người xử lý.

**Xong khi:** IT01 qua trên máy thứ hai với bộ cài.

**Ghi chú làm dở:** —

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
