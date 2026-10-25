# USB trên phiên localhost:8080 — 24/10/2026

**Đã khắc phục và người dùng xác nhận thấy rút/cắm USB trên app giám thị.**
Nhánh hiện tại `feat/usb-app-integration-20261024`, base `acc1f82`, working tree chưa commit.
Giữ các thay đổi/bằng chứng phiên trước; không dùng codex-with-chatgpt theo opt-out.

## Nguyên nhân được xác nhận

1. Hai JVM client trong ảnh nối `localhost:8080` nhưng không có cờ signal/USB.
   Script chạy thường chưa hỗ trợ opt-in; lượt desktop trước dùng runner riêng đã đóng.
2. `DEMO-C3-A` do `demo-c3.ps1` tạo chỉ để demo process, không có `session_id`/đề thi.
   Native UI cho thấy thí sinh vẫn ở trang chủ với lỗi phản hồi, chưa vào màn thi.
3. Listening trả HTTP200, `sessionId:null`, `NOT_STARTED`, writerEpoch1, mọi metadata
   playback rỗng. Parser dashboard từ chối và làm cả chu kỳ Timeline thất bại.
4. Với ca hợp lệ mới, V1 status `ACTIVE/totalQuestions=0` là tổng chấm chưa có; GET đề
   thực có một câu. Preview lại coi tổng0 là lỗi nên hiển thị thông báo gây hiểu nhầm.

## Sửa và kiểm chứng

- Script server thêm `-SignalExperiment`; script client thêm `-ReadUsbStorage` tự
  bật signal, kiểm provider/class/resource trong JAR trước chạy. `-ValidateOnly`
  kiểm cấu hình mà không mở JVM; server không đọc `.env` ở mode này. Default OFF/FULL.
- Chỉ projection Listening của dashboard chấp nhận đúng shape monitor-only rỗng.
  Playback parser vẫn từ chối session null; kiểm attempt trước nhánh null và từ chối
  phase/metadata mâu thuẫn, kiểu sai, field thiếu/thừa. Không đổi wire/DTO/server.
- Preview chỉ nhận ACTIVE0 như số câu chưa biết, giữ guard ID và số âm; không tải đề,
  takeover hay READY để lấy số câu. Không đổi quy tắc chấm/lưu/nộp/START/deadline.
- Hai nhóm test **68 + 29 = 97 PASS, 0 FAIL/ERROR/SKIP**; package client PASS.
  Không gọi đây là lượt full reactor verify mới. [Receipt/test/hash](receipt.json).

Theo lựa chọn người dùng, đã dừng server TEST cũ PID44636, mở server57776 trên cùng8080
với signal bật; hai client cũ đã tự thoát. Hai client cập nhật tiếp theo được đóng
graceful trước package, rồi mở JAR mới cho người dùng đăng nhập. Không reset DB,
không xóa attempt cũ hoặc reset deadline. Native UI đã chọn đúng ca và bộ lọc USB.

Đề/ca mới được tạo **qua API proctor**: `USB-8080-20261024-511c18`, attempt
`USB-8080-20261024-511c18-candidate1`. Nội dung một câu Reading tự soạn gắn **MOCK**,
chỉ kiểm USB; không thay bằng chứng đề TOEIC200. Nguồn USB, app/HTTP/WS/DB và thao tác
desktop là thật. Lịch sử server ghi các giờ UTC+7:

| Giờ quan sát ngày24/10 | Sự kiện | USB count |
| --- | --- | --- |
| 22:17:03.423930 | PRESENT_AT_START, run trước khi cập nhật JAR | 1 |
| 22:23:03.914534 | PRESENT_AT_START, run JAR mới | 1 |
| 22:25:23.057301 | REMOVED | 0 |
| 22:25:34.612962 | CONNECTED | 1 |

Hai baseline thuộc hai run khác nhau, không phải event lặp mỗi poll. Người dùng xác
nhận “Đã thấy rút và cắm USB”. Timeline được đưa lên trước, lọc `WINDOWS_USB_STORAGE`
và cuộn đến bảng history. Lượt `DEMO-C3-A` cũng tải lại được history sau adapter fix.

Diagnostic source-runner tạm không gọi được method preview package-private do
`IllegalAccessError` giữa classloader; không dùng lỗi này làm kết luận nghiệp vụ.
Public client API, test regression và UI native đã xác nhận nguyên nhân ACTIVE0.

Server và hai app được để mở theo nhu cầu người dùng, DB phát triển TEST vẫn giữ
dữ liệu. Không coi đây là runner isolated tự xóa DB. Không đủ H01–H12/LAN/installer,
không ký ADR/policy hoặc task DONE. Không tính latency vật lý vì thiếu timestamp tay.
