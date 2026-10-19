# TOEIC Monitor — BTL Lập trình mạng

Hệ thống thi TOEIC rút gọn theo mô hình Client–Server, có giám sát process của thí sinh theo thời gian thực. Nhóm 3 sinh viên (vai A/B/C), làm từ 03/10 đến hạn nộp **23:59 ngày 31/10/2026** (UTC+7).

Trả lời và viết tài liệu bằng tiếng Việt; tên định danh trong code bằng tiếng Anh.

> **Phạm vi V2 được nhóm thông qua** (người đại diện xác nhận ngày 19/10/2026 về buổi tối 18/10): mục tiêu 1 đề TOEIC **200 câu/7 Part** với media được phép sử dụng và monitoring đa tín hiệu **process + USB mass-storage presence/connect/disconnect + focus/minimize/fullscreen + display changes**. Không xem đây là tính năng đã làm xong. Xem [V2 kickoff](docs/plan-v2/V2_EXECUTION_KICKOFF_20261019.md), [36 task](docs/plan-v2/03_TASK_CARDS.md) và [phụ lục V2 của hợp đồng](docs/plan-v1/02_HOP_DONG.md). Giữ những bất biến đã chốt; API/wire schema, QD-21 START/deadline, và OS-observer approach phải review riêng trước khi thay đổi code tương ứng.


## Tài liệu nguồn — đọc trước khi làm task

| Cần gì | Đọc ở đâu |
|---|---|
| Task, owner, phụ thuộc, tiêu chí nghiệm thu | **V2:** `docs/plan-v2/03_TASK_CARDS.md`, `docs/plan-v2/BACKLOG.json`, [kickoff](docs/plan-v2/V2_EXECUTION_KICKOFF_20261019.md); **legacy:** `docs/plan-v1/01_KE_HOACH.md` hoặc `TRACKER.json` (lịch sử/đối chiếu) |
| Quy tắc lưu/nộp/hết giờ, monitoring, Listening | `docs/plan-v1/02_HOP_DONG.md` |
| Test case (AT/MT/LT/IT) và hai thực nghiệm E1, E2 | `docs/plan-v1/03_KIEM_THU_VA_THUC_NGHIEM.md` |
| Yêu cầu môn học, cấu trúc báo cáo, rubric | `docs/plan-v1/nguon/Instruction.md` |

Khi người dùng nêu mã task (ví dụ `T2-A2` hoặc `EX-01`), mở task trong tracker legacy hoặc V2 task cards tương ứng, đọc tiêu chí nghiệm thu và phần hợp đồng liên quan rồi mới viết code. Nếu yêu cầu mâu thuẫn với hợp đồng, nói rõ chỗ mâu thuẫn và hỏi, không tự chọn.

Không sửa thư mục `nguon/` (bản gốc của môn học). Chỉ sửa `01`–`03` khi người dùng yêu cầu đổi kế hoạch hoặc hợp đồng.

## Stack đã chốt

- Desktop: Java + JavaFX, **một** project có hai giao diện theo role (thí sinh, giám thị).
- Server: Java + Spring Boot, một ứng dụng chia module. Module monitoring phía server tách riêng (C sở hữu).
- DB: PostgreSQL. Build: Maven.
- Giao tiếp: HTTP/REST cho login, đề, audio, lưu/nộp, đọc trạng thái; WebSocket chuẩn + JSON cho heartbeat, monitoring, start, cảnh báo.
- Monitoring: `ProcessHandle` polling trên máy thí sinh.
- Python chỉ dùng để phân tích CSV và vẽ biểu đồ.

Không thêm: React, C#, Python agent, Socket.IO, message broker, microservices, Kubernetes, webcam/microphone streaming, khóa/chặn thiết bị hoặc OS, đọc file USB, screenshot/keylogging, streaming audio, AI chấm bài, SSO/refresh token, editor đề, hàng đợi bền vững trên đĩa. **Ngoại lệ V2 đã duyệt phạm vi:** chỉ quan sát **USB mass-storage** (hiện diện/cắm/rút) trên candidate được phân quyền và được thông báo, cùng các tín hiệu focus/minimize/fullscreen/display; không chặn thiết bị hoặc đọc nội dung/serial USB. Chủ sở hữu A/C chốt schema/protocol/retention và C/B chốt API quan sát Windows theo ADR trước khi tích hợp. Nếu thấy trở ngại, báo evidence thay vì tự đổi stack.

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

`docs/plan-v1/TRACKER.json` là nguồn theo dõi tiến độ. Chỉ cập nhật `status`, `actual_hours`, `evidence` khi người dùng xác nhận, và `evidence` phải trỏ tới thứ có thật (commit SHA, tên test đã chạy, file log). V2 `BACKLOG.json` đang mang placeholder `TO_AUDIT`; duyệt kế hoạch **không đồng nghĩa** đánh dấu 36 task DONE hoặc xác nhận giờ công. Không tự đánh dấu hoàn thành một task chỉ vì code đã viết xong.

## Mốc cần nhớ

- 08/10: gate prototype (login hai role, event lưu DB rồi lên dashboard, heartbeat, package chạy máy khác).
- 15/10: gate tính đúng của lưu/nộp (AT01–AT11).
- 20/10: gate delta. Không qua thì tắt delta mặc định, giữ full snapshot.
- **V2:** mục tiêu đóng băng tính năng **sau 23/10** để dành thời gian QA/report; đây là mốc áp dụng cho scope mới đã được nhóm duyệt. Mốc 27/10 thuộc kế hoạch 5 chặng cũ (tham chiếu lịch sử), không tự mở rộng feature qua V2 freeze.
- 30/10: mục tiêu bàn giao; 31/10 là dự phòng.

## Cấu trúc và lệnh

### Cấu trúc source

| Module | Nội dung |
|---|---|
| `protocol/` | DTO login, error code và envelope message v0 dùng chung |
| `server/` | Spring Boot REST, JDBC/Flyway, user/session và seed tài khoản mẫu |
| `client/` | Một JavaFX app; login HTTP nền và hai màn hình theo role server trả |
| `monitoring-spike/` | Chương trình khảo sát `ProcessHandle` độc lập |

### Phiên bản đã khóa và chạy thật

- Oracle JDK 21.0.8; target source/release Java 21.
- JavaFX 21.0.12 LTS; Spring Boot 4.1.1; PostgreSQL 18 (container đã chạy 18.6).
- Maven 3.9.11; Docker 29.4.3; Docker Compose 5.1.3; Windows 11 x64.

### Lệnh đã chạy thành công

```powershell
# Build và unit test toàn bộ
mvn test
mvn package

# DB (cần .env tạo từ .env.example)
docker compose up -d --wait
docker compose down -v
docker compose up -d --wait

# Server: nạp DB_NAME/DB_USER/DB_PASSWORD/DB_PORT từ .env vào terminal trước
java -jar server\target\server-0.1.0-SNAPSHOT.jar

# Probe ProcessHandle
mvn --% -q -pl monitoring-spike exec:java -Dexec.args=--limit=200

# Tạo app-image Windows sau mvn package
jpackage --type app-image --dest jpackage-out --name ToeicMonitor --input client\target `
  --main-jar client-0.1.0-SNAPSHOT-all.jar --main-class vn.edu.toeic.client.Launcher `
  --app-version 0.1.0 --vendor "TOEIC Monitor Team"

# Chạy client đã đóng gói
.\jpackage-out\ToeicMonitor\ToeicMonitor.exe
```

`scripts/reset-db.ps1 -Force` đóng gói đúng chuỗi `config --volumes` → `down -v` → `up -d --wait` thành một lệnh và từ chối chạy nếu compose có volume khác `toeic-pgdata`. Chuỗi lệnh Docker bên trên đã chạy thật; script wrapper chưa chạy riêng trong phiên này.

Tài khoản phát triển `MOCK`: `candidate1`, `candidate2`, `proctor1`; mật khẩu mặc định `ChangeMe123!`, có thể đổi bằng `TOEIC_SEED_PASSWORD`. Không dùng các credential này ngoài môi trường phát triển.
