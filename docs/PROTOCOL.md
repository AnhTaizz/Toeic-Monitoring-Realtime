# Protocol đang dùng

**Trạng thái: v0 chưa chốt.** File này mô tả những gì code thật đang gửi và nhận. Quy tắc nghiệp vụ đằng sau nằm ở `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`; không chép lại ở đây.

Owner: C (monitoring, khung message chung), A (auth, ca thi, lưu/nộp). Người dùng: B.

Đổi một message đã có người dùng thì: owner sửa file này, reviewer duyệt, người dùng cập nhật fixture trong cùng nhánh tích hợp.

## Phân kênh

| Kênh | Dùng cho |
|---|---|
| HTTP/REST | Login, import đề và ca thi, tải đề và audio, lưu, nộp, đọc trạng thái, đọc timeline |
| WebSocket + JSON | Heartbeat, monitoring (state, event), lệnh start, trạng thái, cảnh báo tới giám thị |

## Khung message WebSocket

Theo hợp đồng mục 1, mỗi message có các trường sau. Tên trường cụ thể chốt ở T1-C1.

| Trường | Ý nghĩa |
|---|---|
| `protocolVersion` | Phiên bản protocol |
| `type` | Loại message |
| `messageId` / `requestId` | Nhận diện message để chống trùng khi retry |
| `attemptId` | Lượt thi mà message thuộc về |
| _correlation ID_ | Nối request với ACK và với log (traceId) |
| _payload_ | Dữ liệu nghiệp vụ theo `type` |

Mẫu JSON v0: _C điền ở T1-C1, ghi nhãn `MOCK` cho tới khi có message thật chạy qua server._

## Danh mục endpoint HTTP

| Method + đường dẫn | Ai gọi | Request | Response | Lỗi | Task | Trạng thái |
|---|---|---|---|---|---|---|
| _login_ | Thí sinh, giám thị | | | | T1-A1 | Chưa có |
| _timeline của một thí sinh_ | Giám thị | | | | T1-A3 | Chưa có |
| _import đề / tạo ca_ | Giám thị | | | | T2-A1 | Chưa có |
| _lấy đề (không có đáp án đúng)_ | Thí sinh | | | | T2-A1 | Chưa có |
| _autosave_ | Thí sinh | | | | T2-A2 | Chưa có |
| _submit_ | Thí sinh | | | | T2-A3 | Chưa có |
| _trạng thái attempt (answers, revision, state, deadline)_ | Thí sinh | | | | T2-A4 | Chưa có |
| _manifest và tải audio_ | Thí sinh | | | | T3-A1 | Chưa có |

## Danh mục message WebSocket

| `type` | Hướng | Payload | ACK | Task | Trạng thái |
|---|---|---|---|---|---|
| _heartbeat_ | client → server | | | T1-C1, T1-A4 | Chưa có |
| _event process_ | thí sinh → server | | Sau khi commit | T1-C3, T1-A3 | Chưa có |
| _cảnh báo_ | server → giám thị | | | T1-A3, T1-B3 | Chưa có |
| _presence (ONLINE/UNKNOWN)_ | server → giám thị | | | T1-A4 | Chưa có |
| _full snapshot_ | thí sinh → server | | Sau khi state được chấp nhận | T2-C1 | Chưa có |
| _delta_ | thí sinh → server | | | T3-C1, T3-C2 | Chưa có |
| _yêu cầu resync / epoch mới_ | server → thí sinh | | | T2-C1, T3-C2 | Chưa có |
| _READY / start / interrupted (Listening)_ | hai chiều | | | T3-A2, T3-B2 | Chưa có |

## Mã lỗi

Nhóm lỗi theo hợp đồng: unauthorized, forbidden, invalid input, invalid state, stale revision/writer/epoch, conflict, expired, retryable server error. Mã cụ thể: _chốt ở QD-04._

| Mã | Khi nào | Client nên làm gì |
|---|---|---|
| | | |

## Tham số cấu hình khởi đầu

Giá trị thử nghiệm theo hợp đồng, phải ghi lại giá trị thật khi đo.

| Tham số | Giá trị khởi đầu |
|---|---|
| Chu kỳ poll process | 1.000 ms |
| Heartbeat | 2 giây |
| Server timeout heartbeat | 6 giây |
| Queue event phía client | 500 |
| Full snapshot đối soát (chế độ delta) | 30 giây |

## Lịch sử thay đổi

| Ngày | Thay đổi | Ai | Ai đã cập nhật theo |
|---|---|---|---|
| | | | |
