# Vai A — Server, cơ sở dữ liệu, giao dịch

Người đảm nhận: _chưa gán_

## Tóm tắt vai

- **Bạn sở hữu:** ứng dụng Spring Boot, schema PostgreSQL, đăng nhập và phân quyền, ca thi và lượt thi, lưu–nộp–hết giờ, phiên HTTP/WebSocket, đo tải, ghép báo cáo và nộp bài.
- **Bạn không sở hữu:** module monitoring phía server từ chặng 2 trở đi (của C; bạn chỉ cấp hook lưu trữ và xác thực), và mọi thứ phía JavaFX (của B).
- **C review code của bạn. Bạn review code của B.**
- Tổng: 82 giờ (72 giờ task + 10 giờ review).

Tài liệu phải đọc: `02_HOP_DONG.md` mục 1 và mục 2 (đọc kỹ "Thứ tự quyết định phía server"), `03_KIEM_THU_VA_THUC_NGHIEM.md` mục 2.

## Cách dùng file này

Mỗi task có phần hướng dẫn và dòng **Ghi chú làm dở**. Khi dừng giữa chừng, ghi vào đó bạn đang ở bước nào và cái gì chưa chạy. Trạng thái chính thức vẫn cập nhật ở `TRACKER.json`. Phần "Cách làm" là gợi ý; nếu chọn cách khác thì ghi vào `docs/QUYET_DINH.md`.

## Contract bạn phải giao và nhận

| Hạn | Bạn giao | Cho ai |
|---|---|---|
| 03/10, giờ đầu | Mẫu request/response login, cấu trúc role và attempt scope | B, C |
| 04/10 | Cách gửi credential cho HTTP và WS, phạm vi subscription của giám thị | B |
| 09/10 | Schema đề, answers/revision, requestId, writerEpoch, deadline/state, mã lỗi | B, C |
| 12/10 | Response khi bài đã chốt, takeover writer, state khi reconnect | B |
| 16/10 | Audio manifest, READY/start/interrupted (cùng B) | C |

| Hạn | Bạn nhận | Từ ai |
|---|---|---|
| 03/10, giờ đầu | Khung message v0 (event, heartbeat, ACK) | C |
| 16/10 | Monitoring epoch/sequence, điểm cần hook lưu trữ | C |

Giao mẫu trước khi cài xong, ghi vào `docs/PROTOCOL.md`. Người dùng sẽ làm với fixture `MOCK` rồi nối thật sau.

---

## Chặng 1 (03–08/10) — 17 giờ

Lịch gợi ý: 03/10 A1 · 04/10 A2 · 05/10 A3 (3h) · 06/10 A3 (1h) + A4 (1h) · 07–08/10 A4.

### T1-A1 · Khởi tạo server, DB và tài khoản mẫu
`4h` · phụ thuộc: không · review: C

**Đầu ra:** lệnh chạy server, schema tạo lại được, tài khoản test, mẫu response login.

**Cách làm:**
1. Trong giờ đầu, thống nhất với C mẫu login (request, response, lỗi) và ghi vào `docs/PROTOCOL.md`. Làm việc này trước khi viết code để B không phải chờ.
2. Tạo project Maven Spring Boot. Chốt phiên bản JDK, Spring Boot, PostgreSQL với B (QD-01) vì B dùng cùng JDK.
3. Tạo database và schema tối thiểu: bảng người dùng (có role) và bảng phiên đăng nhập. Viết sao cho xóa và tạo lại được bằng một lệnh (QD-02).
4. Seed ít nhất 2 thí sinh và 1 giám thị. Mật khẩu lưu dạng băm (ví dụ BCrypt), kể cả tài khoản mẫu.
5. Viết endpoint login: đúng thì trả token và role, sai thì trả lỗi xác thực.
6. Ghi lệnh build, chạy, reset DB và phiên bản môi trường vào mục "Cấu trúc và lệnh" của `CLAUDE.md`.

**Dễ sai:**
- Cấu hình DB và port để trong file cấu hình hoặc biến môi trường, không viết cứng trong code.
- Server phải lắng nghe được từ máy khác trong LAN, không chỉ `localhost`. Thử từ máy thứ hai sớm (tường lửa Windows hay chặn).
- Thông báo lỗi login không tiết lộ là sai tên hay sai mật khẩu.

**Xong khi:** `mvn` build được; chạy lệnh reset rồi khởi động lại vẫn login được; login sai trả lỗi; B gọi được từ máy khác.

**Ghi chú làm dở:** Code/build/reset DB/login local đã đạt ngày 03/10 trên nhánh `feat/stage1-project-skeleton`; còn kiểm gọi từ máy LAN thứ hai và review C trước khi xin cập nhật tracker.

### T1-A2 · Xác thực REST và WebSocket
`4h` · phụ thuộc: T1-A1, T1-C1 (contract) · review: C

**Đầu ra:** mọi request HTTP và kết nối WS đều gắn được với một người dùng; có kiểm tra role và attempt scope.

**Cách làm:**
1. Chốt cách gửi credential (QD-03). Hợp đồng cấm để token trong URL query. Hai cách hợp lệ: header lúc handshake, hoặc message xác thực đầu tiên sau khi mở kết nối.
2. HTTP: một lớp lọc đọc token, tra phiên, gắn người dùng vào request; thiếu hoặc sai thì trả lỗi xác thực.
3. WS: dùng handler WebSocket thuần của Spring (không STOMP, không SockJS) để client `java.net.http.WebSocket` của B nói chuyện trực tiếp. Từ chối handshake nếu credential không hợp lệ.
4. Viết một hàm kiểm tra quyền dùng chung: "người dùng này có được thao tác trên attempt/ca thi này không". Mọi handler gọi hàm đó trước khi trả dữ liệu.
5. Gửi message xuống một session từ nhiều luồng phải được tuần tự hóa (Spring có `ConcurrentWebSocketSessionDecorator`).

**Dễ sai:**
- Kiểm tra token lúc mở WS là chưa đủ; mỗi message vẫn phải kiểm tra `attemptId` có thuộc người gửi không.
- Không in token ra log, kể cả log debug.
- JSON sai hoặc `type` lạ: trả lỗi cho đúng client đó, không để exception làm đóng kết nối của client khác.

**Xong khi:** AT02 chạy được (chưa login, sai role, WS thiếu token đều bị từ chối); một thí sinh gửi message với `attemptId` của người khác bị từ chối.

**Ghi chú làm dở:** 04/10, T1-A2 code-complete tại `7ac62c4` trên `feat/t1-a2-authenticated-websocket`: Bearer REST/WS, session SHA-256 lookup/revalidate, role/scope guards, raw `/ws/v1/realtime`, heartbeat ACK/ERROR và send decorator đã cài. Mvn test/package PASS 91/91, 5/5 module; PostgreSQL production smoke PASS. QD-03/PROTOCOL đã chốt; review C chưa chạy, không ghi duyệt thay C. Production scope deny unknown vì chưa có schema attempt; own/proctor assignments chỉ MOCK trong test. B2 cần phiên tích hợp riêng, không sửa/merge B2 trong task A2. Không cài A3/A4/C3, không đổi TRACKER.json. Git merge kết quả xem report/evidence phiên này.

### T1-A3 · Lưu event và đẩy cảnh báo cho giám thị
`4h` · phụ thuộc: T1-A2, T1-C1 · review: C

**Đầu ra:** event từ collector được lưu, ACK cho thí sinh, đẩy cho giám thị; giám thị đọc lại được timeline.

**Cách làm:**
1. Bảng event có ràng buộc duy nhất trên `eventId` (trong phạm vi attempt). Chèn kiểu "nếu đã có thì bỏ qua".
2. Thứ tự bắt buộc: kiểm quyền → lưu → **commit** → ACK cho thí sinh → đẩy cho giám thị. Trong Spring, phần ACK và đẩy đặt sau khi phương thức `@Transactional` trả về, hoặc dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`.
3. Event trùng: ACK lại, không lưu lần hai, không đẩy lần hai.
4. Endpoint HTTP cho giám thị đọc timeline từ DB (dùng khi giám thị reconnect).
5. Lỗi DB: không ACK thành công; trả lỗi retry được.

**Dễ sai:**
- ACK hoặc đẩy bên trong transaction: nếu commit thất bại, giám thị đã thấy một event không tồn tại.
- Giám thị chỉ được nhận event của ca mình được giao.
- Từ chặng 2, phần xử lý monitoring chuyển sang module của C (T2-C1). Viết phần này gọn và tách biệt để C tiếp quản; giữ lại phần lưu trữ và kiểm quyền làm hook.

**Xong khi:** gửi cùng `eventId` hai lần chỉ có một dòng trong DB và một cảnh báo trên màn giám thị; giám thị tắt mở lại vẫn thấy đủ timeline; tắt DB thì client không nhận ACK thành công.

**Ghi chú làm dở:** 04/10/2026, T1-A3 server code-complete trên `feat/t1-a3-monitoring-events`: V2 assignment/events, JDBC scope/login/auth-me, validation, unique attempt/event, so payload, transaction proxy commit-before-ACK, assigned-proctor push qua decorator, timeline ordered DB. Real PostgreSQL18.6 + HTTP/WS PASS gồm duplicate/concurrent retry, conflict, scope, offline recovery và deferred COMMIT failure rollback không ACK/warning. DB-off NOT RUN vì DB dùng chung; C review NOT RUN, không ghi duyệt thay C. Không viết C3/B3/A4; TRACKER giữ nguyên. Contract PROTOCOL và evidence `evidence/t1-a3/2026-10-04-verification.md`; Git/PR/merge xem final report.

### T1-A4 · Presence và tích hợp prototype
`3h` · phụ thuộc: T1-A2, T1-B2 · review: C

**Đầu ra:** server tự phát hiện client mất liên lạc; luồng demo chạy được.

**Cách làm:**
1. Mỗi heartbeat cập nhật `lastSeenAt`. Một tác vụ định kỳ quét: quá thời gian timeout (khởi đầu 6 giây, là tham số cấu hình) thì đánh dấu UNKNOWN và ghi `timeoutDetectedAt`.
2. Đẩy thay đổi trạng thái cho giám thị.
3. Thử: kill một client thí sinh trong khi client khác vẫn chạy; client còn lại không bị ảnh hưởng.
4. Chốt luồng demo với B và C, chuẩn bị log server để trình bày.

**Dễ sai:**
- Client bị kill không gửi được gì; việc phát hiện hoàn toàn do server.
- UNKNOWN nghĩa là "không còn xác nhận được", không phải vi phạm. `timeoutDetectedAt` là lúc server phát hiện, không phải lúc client thật sự ngắt.
- Khi client quay lại ONLINE, khoảng trống đã ghi không bị xóa.

**Xong khi:** gate 08/10 qua phần server: event commit rồi ACK, không trùng, heartbeat timeout hiển thị UNKNOWN.

**Ghi chú làm dở:** 04/10 T1-A4 trên `feat/t1-a4-monitoring-presence`, base main `99b4f39`. Scoped heartbeat gắn collector UUID thật khi explicit start; lease Stop/logout/switch/reconnect, unscoped không ONLINE. Presence service deadline đơn điệu 6s/scan500ms, association bounded/tổng hợp nhiều socket, revision/CAS, push sau commit ngoài DB lock. V4 bảng presence và interruptions riêng V3 overflow; recovery giữ gapId/recoveredAt, restart UNKNOWN/SERVER_RESTART không bịa gap. Roster/history REST và MONITOR_PRESENCE chỉ proctor có assignment ACTIVE. Real hai candidate/owned JVM hard-kill/recovery/push isolation/deferred COMMIT/cleanup PASS; test/package và hồi quy xem evidence `evidence/t1-a4/2026-10-04-verification.md`. Chạm B2 adapter và C3 coordinator tối thiểu, không đổi event/overflow. Human C review, GUI/LAN NOT RUN; B3 dashboard/parser chưa làm, không đánh toàn prototype PASS. TRACKER giữ nguyên; Git/PR/merge cuối xem report. Dừng A4.

---

## Chặng 2 (09–15/10) — 20 giờ

Đây là chặng nặng nhất của bạn. Gate 15/10 yêu cầu AT01–AT11 có bằng chứng.

### T2-A1 · Import JSON và mở ca thi
`4h` · phụ thuộc: T1-A2 · review: C

**Đầu ra:** giám thị import đề và tạo được ca mẫu; thí sinh lấy đề không có đáp án đúng.

**Cách làm:**
1. Đầu buổi 09/10, giao schema đề và DTO cho B trước khi cài.
2. Kiểm tra khi import: ID câu hỏi trùng, đáp án đúng phải nằm trong các lựa chọn của câu, câu Reading thuộc đúng nhóm đoạn văn, file audio được tham chiếu có trong manifest. Đề sai trả lỗi nêu rõ chỗ sai.
3. Chỉ role giám thị gọi được import và tạo ca.
4. Dùng hai lớp dữ liệu riêng: một cho lưu trữ (có đáp án đúng), một cho gửi xuống client (không có). Không dùng chung một lớp rồi ẩn trường.

**Dễ sai:** dùng chung một lớp và quên ẩn `correctOption` ở một endpoint nào đó. Viết test đọc JSON trả về và khẳng định không có trường đáp án.

**Xong khi:** import đề mẫu 10–20 câu thành công; đề sai bị từ chối có lý do; thí sinh gọi import bị từ chối (AT02); JSON đề gửi cho thí sinh không chứa đáp án.

**Ghi chú làm dở:** 05/10/2026, T2-A1 code-complete trên `feat/t2-a1-exam-import-session`: Flyway V5 schema (exams, exam_questions, exam_options, exam_sessions, exam_session_proctors, mở rộng monitoring_attempts), ExamValidationService, ExamService, ExamController (import đề, tạo ca thi, cấp đề thí sinh), 372/372 unit/integration tests PASS, candidate DTO độc lập tuyệt đối không chứa correctOption (tuân thủ AT02 và invariant), Contract bàn giao ghi trong PROTOCOL.md. Real PostgreSQL smoke script `scripts/smoke-t2a1.ps1` sẵn sàng. Review C chưa chạy; chuẩn bị T2-A2.

### T2-A2 · Autosave toàn bộ đáp án theo revision
`4h` · phụ thuộc: T2-A1 · review: C

**Đầu ra:** endpoint autosave tuân thủ đúng thứ tự quyết định trong hợp đồng mục 2.

**Cách làm — đúng thứ tự này:**
1. Xác thực, kiểm quyền trên attempt (trước cả việc tra cache chống trùng).
2. Mở transaction, khóa dòng attempt (`SELECT ... FOR UPDATE`).
3. Kiểm tra `writerEpoch` (sau khi đã có khóa).
4. Nếu bài đã chốt: không sửa, trả trạng thái.
5. Chạy `SELECT clock_timestamp()` một lần, lưu làm `decisionAt`. Chỉ nhận nếu `decisionAt < deadlineAt`.
6. Áp quy tắc revision và requestId (bảng trong hợp đồng): mới hơn thì lưu; cùng revision cùng nội dung thì trả lại kết quả cũ; cùng revision khác nội dung là CONFLICT; thấp hơn là STALE.
7. Commit, rồi mới trả ACK.

**Dễ sai:**
- Dùng `now()` thay cho `clock_timestamp()`: `now()` trả giờ bắt đầu transaction, tức là trước khi chờ khóa.
- So sánh nội dung bằng chuỗi JSON thô: cùng map nhưng khác thứ tự key sẽ bị coi là khác. Chuẩn hóa (sắp xếp key) rồi mới so hoặc băm.
- Kiểm tra `writerEpoch` trước khi lấy khóa rồi tin kết quả đó.
- Kiểm tra đáp án gửi lên có thuộc đúng đề của attempt không.

**Xong khi:** AT03, AT04, AT05 chạy trên PostgreSQL thật; log có `decisionAt` và `savedRevision`.

**Ghi chú làm dở:** —

### T2-A3 · Submit, timeout và chấm điểm một lần
`4h` · phụ thuộc: T2-A2 · review: C

**Đầu ra:** nộp bài và hết giờ đều chốt bài đúng một lần.

**Cách làm:**
1. Submit mang theo bộ đáp án cuối; đi qua cùng khóa và cùng các bước kiểm tra như autosave, sau đó lưu, chấm và đổi trạng thái sang đã chốt trong **cùng một transaction**.
2. Timeout: một tác vụ định kỳ tìm attempt quá hạn, lấy cùng khóa, kiểm tra lại bằng `clock_timestamp()`, chốt từ bản đã lưu. Timeout không nhận đáp án mới từ client.
3. Endpoint autosave và submit vẫn tự kiểm tra deadline; không dựa vào việc tác vụ timeout đã chạy hay chưa.
4. Submit lặp (cùng requestId) trả lại kết quả cũ, không chấm lại.
5. Chốt quy tắc chấm câu sai và câu trống (QD-09). Điểm là số câu đúng hoặc điểm nội bộ, không gọi là điểm TOEIC chuẩn.

**Dễ sai:** tác vụ timeout chạy trễ không được làm deadline bị trễ theo. Submit với revision cũ (stale) không tự chốt bằng bản cũ.

**Xong khi:** AT06, AT07 (phần liên quan), AT08 chạy được; kết quả đã commit không đổi dù có request muộn.

**Ghi chú làm dở:** —

### T2-A4 · Phiên ghi và kiểm thử tranh chấp trên PostgreSQL
`4h` · phụ thuộc: T2-A2, T2-A3 · review: C

**Đầu ra:** cơ chế takeover writer và bộ test tranh chấp tái lập được.

**Cách làm:**
1. Takeover: tăng `writerEpoch` trong transaction có cùng khóa attempt. Kết nối WS mới không tự động là writer mới.
2. Giao cho B (hạn 12/10) response khi bài đã chốt và state trả về khi reconnect: answers, savedRevision, state, deadline, writerEpoch.
3. Viết test tranh chấp trên PostgreSQL thật (QD-06). Để ép thứ tự, dùng `CountDownLatch`/`CyclicBarrier`, hoặc mở một kết nối thứ hai giữ khóa dòng attempt rồi thả ra đúng lúc.
4. AT07: giữ khóa cho tới sau deadline, thả ra, kiểm tra request đang chờ bị từ chối và `decisionAt >= deadline`.
5. AT09: request của writer cũ dừng trước khi lấy khóa → takeover commit → thả request cũ → bị từ chối. Thử thêm thứ tự ngược lại (request cũ commit trước takeover là hợp lệ).

**Dễ sai:** test dựa vào `sleep` sẽ lúc qua lúc không. Deadline trong test đặt đủ xa để không phụ thuộc tốc độ máy.

**Xong khi:** AT01, AT07, AT09, AT10 có bằng chứng; chạy lại nhiều lần cho cùng kết quả.

**Ghi chú làm dở:** —

### T2-A5 · Tài liệu giao dịch và bằng chứng quyền
`2h` · phụ thuộc: T2-A4 · review: C

**Đầu ra:** ghi chú sẽ dùng cho báo cáo: sơ đồ tuần tự của lưu, nộp, hết giờ; mẫu response; đoạn log có `decisionAt`/`savedRevision`; giải thích vì sao chờ khóa không làm sai deadline và vì sao cache chống trùng không bỏ qua kiểm quyền.

**Xong khi:** C đọc xong tự vẽ lại được luồng lưu–nộp. Cập nhật `docs/PROTOCOL.md` và `docs/KIEM_THU.md`.

**Ghi chú làm dở:** —

---

## Chặng 3 (16–22/10) — 20 giờ

### T3-A1 · API tài nguyên audio và READY
`4h` · phụ thuộc: T2-A1, T1-B4 · review: C

**Cách làm:**
1. Manifest cho mỗi ca: tên file, kích thước, SHA-256.
2. Endpoint tải audio chỉ phục vụ thí sinh thuộc ca đó.
3. Client báo READY sau khi tải và đối chiếu checksum; server ghi nhận và kiểm tra trước khi cho start. Ca chưa đủ điều kiện thì không start.
4. Giao contract cho B đầu buổi 16/10.

**Dễ sai:** gói tài nguyên gửi xuống không được chứa đáp án. Người ngoài ca không tải được audio.

**Xong khi:** thiếu READY thì lệnh start bị từ chối; thí sinh ngoài ca không tải được file.

**Ghi chú làm dở:** —

### T3-A2 · Start, gián đoạn Listening và lượt thi mới
`4h` · phụ thuộc: T3-A1, T2-A3 · review: C

**Cách làm:**
1. Server phát lệnh start có ID riêng. Gửi lại lệnh (retry) dùng cùng ID; server không tạo lần start thứ hai.
2. Nhận báo gián đoạn từ client hoặc tự ghi khi heartbeat timeout trong lúc Listening; lưu cờ gián đoạn phía server.
3. Khi có cờ gián đoạn, server từ chối ghi đáp án Listening theo hợp đồng mục 4, dù UI có khóa hay không.
4. Tổ chức lại: giám thị (và chỉ giám thị) tạo attempt mới với ID mới. Attempt cũ giữ nguyên đáp án và deadline; hết giờ thì chốt kèm trạng thái gián đoạn.

**Dễ sai:** kéo dài deadline cũ hoặc xóa đáp án cũ khi tổ chức lại.

**Xong khi:** phần server của LT04 chạy được; lệnh start lặp không tạo trạng thái mới.

**Ghi chú làm dở:** —

### T3-A3 · Hook cho delta và xử lý lỗi server
`4h` · phụ thuộc: T2-A4, T2-C1 · review: C

**Cách làm:**
1. Cung cấp cho C điểm lưu trữ epoch/sequence và hàm kiểm quyền cho state và event. Giao trước khi C làm T3-C2.
2. Server khởi động lại: mọi phiên monitoring cũ không còn hợp lệ, client phải resync. Phối hợp với C và B để thử.
3. Lỗi DB ở bất kỳ đường ghi nào đều không được ACK thành công.
4. Review phạm vi quyền của state và event trong module của C.

**Dễ sai:** viết lại logic của collector hoặc reducer. Phần đó của C; bạn chỉ cấp hook.

**Xong khi:** C dùng được hook; phần server của IT04 chạy được.

**Ghi chú làm dở:** —

### T3-A4 · Regression giao dịch và nhiều client
`4h` · phụ thuộc: T3-A2, T3-A3, T3-C4 · review: C

**Cách làm:** chạy 2 thí sinh và 1 giám thị. Thử gửi chéo attempt (AT01), save/submit/timeout đồng thời, một client gửi JSON hỏng hoặc rớt giữa chừng (IT02). Ghi log sao cho chạy lại được cùng kịch bản.

**Xong khi:** AT01 và IT02 có bằng chứng; client hỏng không ảnh hưởng client khác.

**Ghi chú làm dở:** —

### T3-A5 · Bản nháp báo cáo network/concurrency
`2h` · phụ thuộc: T3-A4 · review: C

**Đầu ra:** bản nháp các mục kiến trúc, giao tiếp, phân quyền, giao dịch. Nêu rõ framework lo phần nào (ví dụ quản lý kết nối, thread pool) và nhóm tự thiết kế phần nào (khóa theo attempt, revision, epoch, thứ tự commit rồi ACK). Mỗi khẳng định kèm bằng chứng thật.

**Ghi chú làm dở:** —

---

## Chặng 4 (23–27/10) — 14 giờ

### T4-A1 · Chạy bộ tranh chấp và lỗi lưu trữ
`4h` · phụ thuộc: T3-A4 · review: C

Chạy lại AT01–AT11 trên bản đã tích hợp, lưu kết quả kèm fixture và seed. Thử DB lỗi và timeout (AT05, AT08, IT04). Chỉ sửa lỗi; không thêm chức năng.

**Ghi chú làm dở:** —

### T4-A2 · Đo tải 1/5/10 client giả lập
`4h` · phụ thuộc: T4-A1, T3-C4 · review: C

**Cách làm:**
1. Viết client headless (không JavaFX) đăng nhập, mở WS, gửi heartbeat, autosave theo nhịp cố định và message monitoring.
2. Mỗi mức 1, 5, 10 kết nối: 3 lần chạy, mỗi lần 15 giây khởi động và 120 giây đo.
3. Ghi: tỷ lệ thành công và lỗi, RTT (đo ở client từ lúc gửi tới lúc nhận ACK cùng requestId, bằng đồng hồ đơn điệu), CPU và bộ nhớ server, thông số máy.

**Dễ sai:** gọi 10 client giả lập là "10 ứng dụng desktop". Nếu mức 10 không chạy nổi thì ghi mức cuối đo được và nguyên nhân; không xóa lần chạy hỏng.

**Ghi chú làm dở:** —

### T4-A3 · Phân tích server và viết phần phương pháp
`4h` · phụ thuộc: T4-A2, T3-A5 · review: C

Bảng kết quả từ số đo thật, cấu hình test, giải thích về giao dịch và chờ khóa. Rà lại quyền ở các nhánh đã chốt và nhánh retry. Liệt kê thư viện đã dùng và phần kế thừa.

**Ghi chú làm dở:** —

---

## Chặng 5 (28–31/10) — 11 giờ

### T5-A1 · Ghép và rà báo cáo
`3h` · phụ thuộc: T4-A3, T4-B3, T4-C3 · review: C

Ghép phần A/B/C theo cấu trúc ở `nguon/Instruction.md` mục 8. Mỗi khẳng định phải khớp số liệu. Viết Abstract và Conclusion sau khi có Results.

### T5-A2 · Compilatio và rà trích dẫn
`3h` · phụ thuộc: T5-A1 · review: C

Mục tiêu nộp kiểm tra ngày 29/10. Lưu kết quả. Xử lý bằng cách trích dẫn đúng, không tìm cách lách. Ngưỡng theo tài liệu môn (≤ 20%).

### T5-A3 · Chốt gói và bàn giao
`3h` · phụ thuộc: T5-A2, T5-B2, T5-C2 · review: C

Đặt tên Source/Report theo `nguon/Submission.md`, đúng bản báo cáo đã qua Compilatio, có danh sách file và checksum. Bàn giao ngày 30/10, có xác nhận đã nhận. Ngày 31/10 chỉ là dự phòng.

**Ghi chú làm dở (chặng 5):** —

---

## Task review (T1-AR … T5-AR) — 2 giờ mỗi chặng

Bạn review phần của **B**. Mỗi chặng: 1 giờ đọc code, test, tài liệu của B; 1 giờ cho các buổi tích hợp ngắn (10–15 phút mỗi lần). Review theo từng buổi, không dồn cuối chặng.

Khi review code của B, kiểm tra:
- Có tác vụ mạng, file hay quét nào chạy trên luồng giao diện không?
- Đóng app có dừng hết worker không?
- Retry có giữ nguyên requestId, revision, nội dung không?
- Client có hiển thị "đã lưu" trước khi nhận ACK không?
- Client có tự cho mình quyền gì mà server không kiểm tra không?

Ghi kết quả review (đã xem gì, phát hiện gì, B đã sửa chưa) vào `docs/NHAT_KY.md`.

## Phần báo cáo bạn viết

Kiến trúc server, thiết kế giao tiếp mạng, phân quyền, giao dịch và đồng thời, kết quả đo tải. Bạn cũng là người ghép báo cáo cuối.

## Câu hỏi bảo vệ bạn phải trả lời được

- Vì sao lấy giờ bằng `clock_timestamp()` sau khi có khóa, không dùng `now()`?
- Hai request save và submit đến cùng lúc thì chuyện gì xảy ra, theo thứ tự nào?
- Nếu server commit xong nhưng ACK bị mất trên đường về, client retry thì sao?
- Một thí sinh đoán được `attemptId` của người khác thì có đọc được bài không? Chặn ở đâu?
- `writerEpoch` giải quyết vấn đề gì? Cho ví dụ nếu không có nó.
- Spring lo phần nào, nhóm tự viết phần nào?
- Server xử lý nhiều client đồng thời bằng cơ chế gì?

Bạn cũng phải vẽ lại được luồng của B (autosave phía client) và của C (full snapshot, epoch).
