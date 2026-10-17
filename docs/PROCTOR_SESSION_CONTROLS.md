# T3-B4 — Giám thị điều khiển ca và xem kết quả

Trong dashboard JavaFX hiện có, mở tab **Điều khiển ca / kết quả**. Các tab process, cảnh báo, gián đoạn và gap vẫn dùng luồng monitoring hiện có. PROCTOR không tạo collector. Backend A1/A2 được dùng nguyên trạng; không có migration, endpoint hay quyền mới.

## Luồng demo

1. Build `mvn package`, bật PostgreSQL và chạy server/client theo README. Đăng nhập `proctor1` bằng mật khẩu dev đã cấu hình. Mọi client dùng cùng ServerUrl. Đề và tài khoản minh họa là **MOCK**, không dùng làm đề thật.
2. Chuẩn bị file JSON đúng `ExamImportRequest` (ví dụ bên dưới), bấm **Import JSON đề**. Đọc file và gọi API diễn ra ở worker — luồng nền giúp cửa sổ vẫn phản hồi. File tối đa 1 MiB, tối đa 500 câu/10 lựa chọn mỗi câu. Không ghi nội dung có đáp án đúng vào log. Đây là nhập dữ liệu, không có màn soạn đề.
3. Cấp audio riêng tại `<TOEIC_AUDIO_ROOT>/<examId>/<audioFile>`, theo [quy tắc tài nguyên](AUDIO_RESOURCES.md). Import không tải audio lên server. Thiếu file/manifest không hợp lệ sẽ hiện lỗi, không giả READY.
4. Nhập mã đề, mã ca mới, tiêu đề, thời lượng tính bằng giây, danh sách tài khoản thí sinh/giám thị cách nhau bằng dấu phẩy. Giám thị rỗng nghĩa là dùng người tạo theo backend. Bấm **Tạo ca**. Response trả mã các lượt; không tự cấp quyền local, dashboard đọc lại `/auth/me`.
5. Nhập mã ca vừa tạo, bấm **Tải READY**. Candidate đăng nhập, chọn lượt, tải/kiểm checksum, thử loa và xác nhận theo [B1](AUDIO_CLIENT_PREPARATION.md). Refresh READY để xem từng lượt. Server tính lại theo manifest, writer và deadline; dữ liệu trên màn hình chỉ là lần đọc gần nhất.
6. Khi đủ READY, bấm **Start Listening**. Server vẫn kiểm lại khi xử lý. Chỉ thông báo startId khi response hợp lệ; candidate phát do `LISTENING_START` qua WebSocket, không do response HTTP. B2/B3 vẫn chống trùng và không resume sau gián đoạn.
7. Chọn/nhập mã lượt, bấm **Đọc kết quả / Listening**. Có ACTIVE, SUBMITTED và TIMED_OUT theo quyền. ACTIVE hiện **Chưa chốt điểm**; bài đã chốt hiển thị điểm thực tế server (hiện đếm câu đúng, không phải thang TOEIC 990). Có answers, writerEpoch, savedRevision, deadline và lý do gián đoạn. Đọc không takeover, không sửa đáp án hay bật collector.
8. Nếu lượt có cờ gián đoạn, nhập mã ca mới và tiêu đề rồi bấm **Tổ chức lại / thử lại**. Server trả oldAttemptId/newAttemptId/newSessionId; UI hiển thị cả ba. Bấm **Mở lượt mới** để đọc kết quả/READY riêng. Lượt mới cần chuẩn bị lại; lượt cũ vẫn mở bằng ô mã lượt, kể cả sau hết giờ.

Không cần `demo-c3.ps1`: đó là fixture giám sát, không phải ca có đề Listening.

Ví dụ JSON nhỏ, lưu thành file UTF-8 `.json`:

```json
{
  "examId": "DEMO-B4", "title": "MOCK hai câu", "description": "Chỉ để thử",
  "questions": [
    {"questionId":"L1","section":"LISTENING","part":1,"audioFile":"sample.wav",
     "prompt":"MOCK Listening","correctOption":"A","orderIndex":1,
     "options":[{"optionId":"A","optionText":"One","orderIndex":1},
                {"optionId":"B","optionText":"Two","orderIndex":2}]},
    {"questionId":"R1","section":"READING","part":5,"prompt":"MOCK Reading",
     "correctOption":"B","orderIndex":2,
     "options":[{"optionId":"A","optionText":"One","orderIndex":1},
                {"optionId":"B","optionText":"Two","orderIndex":2}]}
  ]
}
```

File audio trên server cho ví dụ này là `audio-resources/DEMO-B4/sample.wav` nếu dùng root mặc định. Dùng WAV/MP3/M4A hợp lệ; không đổi tên file văn bản thành audio. Xem [Listening lifecycle](LISTENING_LIFECYCLE.md) cho contract đầy đủ.

## Khi lỗi hoặc chưa rõ kết quả

- Refresh đang tải hoặc lỗi: giữ dữ liệu cũ với nhãn cũ, không coi READY đó là mới. Đổi ca/lượt tạo số thế hệ đọc mới; phản hồi cũ không ghi đè lựa chọn mới. Chỉnh mã trong ô nhập phải bấm đọc lại trước thao tác tương ứng.
- 401 nghĩa là phiên hết hiệu lực: trở về đăng nhập. 403 nghĩa là thiếu quyền/phân công cho thao tác. 409 có thể do READY hết hiệu lực, thiếu tài nguyên, manifest/checksum/writer thay đổi, hết hạn hoặc chưa có cờ gián đoạn; tải lại READY/trạng thái để phân biệt. Không tự sửa quyền ở client.
- Mất response start/tổ chức lại: UI không báo thành công. `pending` là yêu cầu đang chờ xác nhận; giữ nguyên requestId và toàn nội dung để bấm thử lại. Form tổ chức lại bị khóa khi pending; đổi ô form không tạo replacement thứ hai. Đọc lại trạng thái không tự phát hoặc tự tạo ca.
- Lỗi server từ chối rõ ràng 4xx kết thúc pending đó; muốn sửa đầu vào cần đọc trạng thái mới. Mỗi lượt cũ chỉ có một replacement trực tiếp theo backend. Sau thành công, đừng dùng mã khác để cố tổ chức lại cùng lượt cũ.
- Import/tạo ca không có requestId idempotency trong contract. Nếu response mất, kiểm mã ca đã nhập qua READY/trạng thái và dữ liệu server; không đổi mã để gửi lại tùy tiện.
- Pending giữ trong RAM của view, tối đa 64 ca start/64 lượt tổ chức lại; đóng/logout bỏ pending local, không hủy giao dịch đã commit ở server. Sau khi mở lại mà chưa biết kết quả, kiểm dữ liệu server trước thao tác mới; không hứa phục hồi pending qua restart client.

## Dữ liệu đi qua các lớp nào?

| Thành phần | Nhiệm vụ |
|---|---|
| [ProctorSessionView](../client/src/main/java/vn/edu/toeic/client/proctor/ProctorSessionView.java) | Tab controls và `render()`. Event của nút gọi controller. `Platform.runLater()` xếp việc cập nhật control về luồng JavaFX; không đợi mạng trên luồng này. |
| [ProctorInputs.readExam()/session()](../client/src/main/java/vn/edu/toeic/client/proctor/ProctorInputs.java) | Giới hạn/kiểm schema file và biểu mẫu. Mã mới 1–40 ký tự ASCII chữ/số/_/-, tài khoản tối đa 64; mã lượt sinh ra nằm trong monitoring v0 tối đa 128. Mã ca/lượt có sẵn đọc theo tập ký tự v0, không áp giới hạn mã mới. |
| [ProctorSessionController](../client/src/main/java/vn/edu/toeic/client/proctor/ProctorSessionController.java) | `loadSession()/loadAttempt()` đọc ngoài UI, giữ nhãn loading/stale. `start()/reorganize()` đóng băng yêu cầu và chống nhấn lặp. Callback là hàm chạy khi tác vụ kết thúc; kiểm số thế hệ để bỏ kết quả cũ. `close()` vô hiệu callback, hủy worker và API. |
| [ProctorSessionApiClient](../client/src/main/java/vn/edu/toeic/client/proctor/ProctorSessionApiClient.java) | Bearer chỉ ở header, không redirect; response tối đa 2 MiB, request/import 1 MiB; deadline toàn HTTP/body mặc định 10 giây. Parse/check ID và số long chính xác, không qua double. |
| [ProctorDashboardView](../client/src/main/java/vn/edu/toeic/client/dashboard/ProctorDashboardView.java) | Chứa tab mới, đọc lại quyền qua controller monitoring sau tạo ca/replacement và đóng tab con khi logout. |

UI → controller → worker gọi HTTP → server kiểm role/phân công/trạng thái và commit → response → callback cập nhật snapshot bất biến → `Platform.runLater()` vẽ view. Khi tổ chức lại, chỉ server tạo lượt mới; UI không reset dữ liệu cũ.

## API và phần ghép PR #35

Dùng nguyên `POST /exams/import`, `POST /sessions`, `GET /sessions/{id}/audio/readiness`, `POST /sessions/{id}/listening/start`, `GET /attempts/{id}/status`, `GET /attempts/{id}/listening/status`, `POST /attempts/{id}/listening/reorganize`, đều dưới `/api/v1`. Không bịa API liệt kê ca/user: nhập mã đã biết hoặc dùng response tạo ca/READY; danh sách gợi ý chỉ thuộc phiên view, tối đa 1.000 lượt.

Đã đọc PR #35 draft head `11c5da9` để đối chiếu `ExamApiClient` và dashboard. B4 dùng package `client.proctor` và DTO protocol hiện có, không tạo một `ExamApiClient` thứ hai hay đưa Reading của PR35 vào main. Điểm ghép sau này là tab mới và `close()` trong `ProctorDashboardView`; giữ nguyên các nút/tab của hai bên khi tích hợp. Chưa nghiệm thu Reading + Listening tích hợp PR35, không merge/cherry-pick PR đó.

A cần review quyền đọc lượt đã chốt, phân công ca/start, semantics retry/reorganization và kết quả LT04. Không có hook backend production mới. Human A review, nghe thủ công, LAN/máy thứ hai và native FileChooser thao tác thủ công chưa nghiệm thu trong task này; kiểm tự động import gọi cùng controller sau khi đã có đường dẫn file.

## Tái lập kiểm tra

```powershell
docker compose up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b4.ps1 -JavaHome '<JDK21>' -Gui
# Chỉ nhóm B4 và Listening backend:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b4.ps1 -JavaHome '<JDK21>' -Focused -Gui
```

Script clean output các module, dùng cache Maven offline và đọc `.env` local, không đổi file. PostgreSQL dùng schema TEST UUID riêng; UTC chỉ áp dụng tiến trình test. Output lớn nằm trong `target/t3-b4/`, ảnh GUI trong `server/target/t3-b4-gui/`; không reset DB dev. Cần JDK21/dependency đã tải và desktop Windows có media. [Báo cáo kiểm thử B4](../evidence/t3-b4/2026-10-15-verification.md) ghi SHA/kết quả thật và giới hạn. Dừng sau B4; chưa B5/E1/E2 hoặc chốt gate delta, FULL vẫn mặc định.


## Tích hợp 16/10/2026

Bản thống nhất giữ nền B4/MSI main, chuyển guard/progress/config cần thiết từ #38. Source và test mới xem [báo cáo](../evidence/unified-20261016/verification.md); không kế thừa PASS installer hoặc số test của nhánh nguồn. [Cách chạy hiện tại](UNIFIED_APPLICATION.md).
