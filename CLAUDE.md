# TOEIC Monitor — BTL Lập trình mạng

Hệ thống thi TOEIC rút gọn theo mô hình Client–Server, có giám sát process của thí sinh theo thời gian thực. Nhóm 3 sinh viên (vai A/B/C), làm từ 03/10 đến hạn nộp **23:59 ngày 31/10/2026** (UTC+7).

Trả lời và viết tài liệu bằng tiếng Việt; tên định danh trong code bằng tiếng Anh.

## Tài liệu nguồn — đọc trước khi làm task

| Cần gì | Đọc ở đâu |
|---|---|
| Task, owner, phụ thuộc, tiêu chí nghiệm thu | `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md` mục 3, hoặc `TRACKER.json` |
| Quy tắc lưu/nộp/hết giờ, monitoring, Listening | `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md` |
| Test case (AT/MT/LT/IT) và hai thực nghiệm E1, E2 | `Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md` |
| Yêu cầu môn học, cấu trúc báo cáo, rubric | `Ke_hoach_LT_Mang_5_chang/nguon/Instruction.md` |

Khi người dùng nêu mã task (ví dụ `T2-A2`), mở task đó trong tracker, đọc tiêu chí nghiệm thu và phần hợp đồng liên quan rồi mới viết code. Nếu yêu cầu mâu thuẫn với hợp đồng, nói rõ chỗ mâu thuẫn và hỏi, không tự chọn.

Không sửa thư mục `nguon/` (bản gốc của môn học). Chỉ sửa `01`–`03` khi người dùng yêu cầu đổi kế hoạch hoặc hợp đồng.

## Stack đã chốt

- Desktop: Java + JavaFX, **một** project có hai giao diện theo role (thí sinh, giám thị).
- Server: Java + Spring Boot, một ứng dụng chia module. Module monitoring phía server tách riêng (C sở hữu).
- DB: PostgreSQL. Build: Maven.
- Giao tiếp: HTTP/REST cho login, đề, audio, lưu/nộp, đọc trạng thái; WebSocket chuẩn + JSON cho heartbeat, monitoring, start, cảnh báo.
- Monitoring: `ProcessHandle` polling trên máy thí sinh.
- Python chỉ dùng để phân tích CSV và vẽ biểu đồ.

Không thêm: React, C#, Python agent, Socket.IO, message broker, microservices, Kubernetes, webcam/USB/khóa OS, streaming audio, AI chấm bài, SSO/refresh token, editor đề, hàng đợi bền vững trên đĩa. Nếu thấy stack có trở ngại kỹ thuật cụ thể, báo lại kèm bằng chứng thay vì tự đổi.

Phiên bản JDK/JavaFX/Spring Boot/PostgreSQL được khóa sau spike chặng 1; ghi vào mục "Lệnh" bên dưới khi đã chạy thật.

## Ai sở hữu phần nào

| Vai | Phần code |
|---|---|
| A | Server: auth, phân quyền, đề/ca/lượt thi, lưu–nộp–hết giờ, schema PostgreSQL, HTTP/WS session |
| B | JavaFX thí sinh và giám thị, adapter mạng phía client, audio, đóng gói/installer |
| C | Collector process, module monitoring phía server (epoch/sequence/reducer), replayer, harness đo |

Review xoay vòng: C xem A, A xem B, B xem C. Khi một thay đổi chạm vào phần của vai khác hoặc đổi contract giữa hai phần, nêu rõ để người dùng báo owner.

## Bất biến không được vi phạm

Chi tiết ở `02_HOP_DONG.md`; đây là danh sách để tự kiểm mỗi lần sửa code liên quan.

**Quyền**
- Kiểm tra xác thực và scope (user, ca thi, attempt) trước mọi response có dữ liệu, kể cả nhánh retry, cache chống trùng và bài đã chốt.
- Role do server quyết định. Ẩn/hiện giao diện không thay cho phân quyền.
- Đáp án đúng không bao giờ nằm trong DTO gửi xuống client.
- Không ghi mật khẩu, token vào log hay URL query.

**Lưu / nộp / hết giờ**
- Autosave gửi toàn bộ map đáp án kèm `requestId`, `writerEpoch`, `answerRevision`.
- Save, submit, timeout và takeover dùng chung một khóa trên bản ghi attempt, trong transaction.
- Kiểm tra `writerEpoch` **sau khi** đã lấy khóa.
- Lấy giờ quyết định bằng `clock_timestamp()` một lần sau khóa. Không dùng `now()`/`CURRENT_TIMESTAMP` (đó là giờ bắt đầu transaction).
- ACK thành công chỉ sau commit. Rollback hoặc lỗi DB không được báo "đã lưu".
- Revision thấp hơn → STALE. Cùng revision khác nội dung → CONFLICT. So nội dung theo dạng chuẩn hóa, không so chuỗi JSON thô.
- Bài đã chốt thì không đổi đáp án hay điểm, không chấm lại.

**Monitoring**
- Full snapshot là baseline và là đường chạy mặc định. Delta là ứng viên, chỉ bật khi qua MT02–MT06.
- Trạng thái process hiện tại và lịch sử event là hai luồng tách biệt; snapshot mới không xóa gap đã ghi.
- Delta chỉ hợp lệ khi `baseSequence == currentSequence` và `sequence == currentSequence + 1`. Thiếu chuỗi → UNSYNCED, yêu cầu full, không đoán.
- Epoch mới bắt đầu bằng full snapshot; message của epoch cũ bị từ chối.
- Event có `eventId` ổn định qua retry; một lần process xuất hiện tạo một event, không lặp ở mỗi poll.
- Mất heartbeat → server đánh dấu UNKNOWN. Đó không phải bằng chứng gian lận; thông báo dùng chữ "quan sát thấy".
- Collector chỉ chạy trên role thí sinh, không chạy trên máy giám thị.
- Full và delta dùng cùng tập process, cùng policy, cùng quy tắc event.

**Listening**
- Thiếu file hoặc sai checksum thì không READY.
- Lệnh start trùng ID không phát lại. Gián đoạn thì dừng, khóa, ghi cờ; reconnect hay mở lại app không tự phát lại và không tự resume.
- Tổ chức lại là cấp attempt mới; không sửa deadline hay reset đáp án của attempt cũ.

**Client**
- Tác vụ mạng, file, quét process không chạy trên JavaFX Application Thread. Đóng app phải dừng worker.
- JSON sai, type lạ, ID ngoài quyền bị từ chối mà không làm rớt server hay ảnh hưởng client khác.

## Cách làm việc

- **Giữ code đơn giản và giải thích được.** Thầy có thể yêu cầu từng thành viên giải thích class, method, luồng message hoặc sửa lỗi tại chỗ. Ưu tiên code tường minh hơn trừu tượng khéo; không thêm tầng, pattern hay thư viện mà task không cần. Khi viết xong một phần không hiển nhiên (khóa, epoch, luồng), giải thích ngắn cho người dùng vì sao làm vậy.
- **Không mở rộng phạm vi.** Chỉ làm đúng task được giao. Ý tưởng thêm thì đề xuất, không tự cài.
- **Mỗi thư viện ngoài thêm vào phải được ghi vào README** (yêu cầu môn học), kèm lý do.
- **Test bám hợp đồng.** Logic lưu/nộp/khóa test trên PostgreSQL thật; test tranh chấp dùng latch/barrier để tái lập thứ tự, không dựa vào sleep hay bấm tay. Reducer monitoring có thể test thuần.
- **Fixture giả phải ghi nhãn `MOCK`** và không được dùng làm bằng chứng đã tích hợp.
- **Không bịa số liệu hay trạng thái.** Không điền kết quả thực nghiệm, không ghi Passed cho test chưa chạy, không viết vào báo cáo điều chưa đo. Kết quả âm (ví dụ delta không lợi) vẫn báo đúng như đo được.
- **Thực nghiệm:** raw không sửa tay; summary do script sinh ra. Mỗi run ghi SHA, máy, OS, cấu hình, seed. Phân biệt client giả lập với desktop thật. Không trừ timestamp của hai máy chưa đồng bộ.
- **Cấu hình:** địa chỉ server, port, chu kỳ poll, heartbeat/timeout, kích thước queue là tham số cấu hình. Không hard-code `localhost` làm cách chạy LAN. Không commit credential thật hay đường dẫn cá nhân.

## Tài liệu làm việc trong `docs/`

| File | Dùng để |
|---|---|
| `docs/TIEN_DO.md` | Trạng thái hiện tại: chặng, mỗi người đang làm gì, đang bị chặn gì, contract đang chờ |
| `docs/NHAT_KY.md` | Nhật ký từng buổi làm, chỉ thêm vào cuối |
| `docs/QUYET_DINH.md` | Quyết định kỹ thuật đã chốt và đang chờ (mã QD-xx) |
| `docs/PROTOCOL.md` | Endpoint và message đang dùng thật |
| `docs/KIEM_THU.md` | Trạng thái 27 test case và nơi lưu bằng chứng |
| `docs/thanh-vien/VAI_A.md`, `VAI_B.md`, `VAI_C.md` | Hướng dẫn từng task của mỗi vai, kèm "Ghi chú làm dở" |

**Đầu phiên:** đọc `docs/TIEN_DO.md` và mục cuối của `docs/NHAT_KY.md`. Khi nhận một task, đọc phần hướng dẫn của task đó trong file vai tương ứng, kể cả "Ghi chú làm dở".

**Cuối phiên có thay đổi code hoặc tài liệu:** thêm một mục vào `docs/NHAT_KY.md` theo mẫu trong file, sửa dòng tương ứng trong `docs/TIEN_DO.md`, và ghi "Ghi chú làm dở" nếu task chưa xong. Thêm hoặc đổi message thì sửa `docs/PROTOCOL.md`. Chốt một lựa chọn kỹ thuật thì ghi vào `docs/QUYET_DINH.md`. Chạy test thì cập nhật `docs/KIEM_THU.md` với kết quả thật.

## Tracker

`Ke_hoach_LT_Mang_5_chang/TRACKER.json` là nguồn theo dõi tiến độ. Chỉ cập nhật `status`, `actual_hours`, `evidence` khi người dùng xác nhận, và `evidence` phải trỏ tới thứ có thật (commit SHA, tên test đã chạy, file log). Không tự đánh dấu hoàn thành một task chỉ vì code đã viết xong.

## Mốc cần nhớ

- 08/10: gate prototype (login hai role, event lưu DB rồi lên dashboard, heartbeat, package chạy máy khác).
- 15/10: gate tính đúng của lưu/nộp (AT01–AT11).
- 20/10: gate delta. Không qua thì tắt delta mặc định, giữ full snapshot.
- 27/10: đóng băng chức năng; sau đó chỉ sửa lỗi và viết tài liệu.
- 30/10: mục tiêu bàn giao; 31/10 là dự phòng.

## Cấu trúc và lệnh

Chưa có source. Khi scaffold (T1-A1, T1-B1), cập nhật mục này với: cấu trúc thư mục thật, phiên bản đã khóa, lệnh build, lệnh chạy server, lệnh chạy client, lệnh chạy test, cách khởi tạo/reset DB. Chỉ ghi lệnh đã chạy được.
