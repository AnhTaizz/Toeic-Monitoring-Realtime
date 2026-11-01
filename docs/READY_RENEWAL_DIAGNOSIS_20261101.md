# Chẩn đoán vòng lặp READY — 01/11/2026

Đã tái hiện lỗi phục hồi READY trên mã nguồn hiện tại (base 040340b và working changes), bằng 2 kiểm thử chẩn đoán, 2 PASS/0 FAIL/0 ERROR/0 SKIP. Không sửa runtime trong lượt xác nhận này. Nội dung đề trong kiểm thử là MOCK, không phải dữ liệu của phiên người dùng trên VPS.

## Kết quả xác nhận

1. PostgreSQL/Flyway/HTTP thật: tạo phòng ACCOUNT_PIN, JOIN, takeover, audio READY và visual READY. API giám thị trả `allReady=true`.
2. Đặt các timestamp readiness về quá khứ bằng SQL trong schema thử nghiệm để tái hiện một lease đã hết hạn. Không giả định đã đo được mạng chậm hay media chậm trên VPS.
3. Gửi ba ATTEST mới, requestId khác nhau, với audio/ảnh/manifest hợp lệ và cùng writer/epoch. Cả ba đều trả HTTP 409 `INVALID_STATE`. Audio vẫn READY; visual hết hạn (`VISUAL_EXPIRED`), `allReady=false`.
4. START phòng trả thành công nhưng gắn `NOT_READY_AT_START` cho thí sinh đã từng READY, dẫn đến không được nghe Listening.
5. Kiểm thử state machine client với API MOCK trả đúng lỗi trên: `READY → UNVERIFIED → UI_PREPARING → REPORTING → UNVERIFIED` lặp lại. Client không gửi INVALIDATE; chỉ dựng lại UI và ATTEST lại bản hết hạn. Vì vậy không tự phục hồi về READY.

`VisualReadinessService.ready()` từ chối ATTEST nếu row readiness hiện tại đã hết hạn. `VisualReadyCoordinator.handle(INVALID_STATE)` bỏ preparedKey và thử lại nhưng không hủy readiness phía server. `RichLobbyPane` hiện "Chưa sẵn sàng" trong các bước chuẩn bị/báo lại, trong khi audio vẫn hiển thị "Đã sẵn sàng". Đây là đường lỗi khớp ảnh chụp người dùng, độc lập với tên đề.

## Giới hạn kết luận

- Hạn mặc định trên server là 6000 ms, nhịp client mặc định 2000 ms. Test server vốn dùng lease 60000 ms để ổn định; tái hiện bằng cách đặt thời gian hết hạn tường minh, không chờ bằng sleep.
- Chưa xác nhận vì sao lease của phiên người dùng hết hạn: cần log/đo trên server đang phục vụ phiên đó. Giả thuyết "hash media quá chậm" trước đây chưa được chứng minh. Ba lần ATTEST cục bộ đo được 252/281/227 ms; các số này không đại diện VPS hay tải nhiều thí sinh.
- Có việc đọc/hash toàn bộ audio và xác minh graph/media trong đường READY. Điều này là chi phí cần xem xét, nhưng bản thân việc thấy code đó không chứng minh nó đã vượt 6 giây trong phiên thật.
- Xác nhận này không chứng minh một vòng chuyển READY mới thành công sau khi hết hạn: server thực tế từ chối liên tiếp; phía UI luân phiên các bước chuẩn bị và chưa xác minh.

## Kiểm thử và bằng chứng

- `AccountPinPostgresTest#diagnosticExpiredReadyRejectsFreshAttestationsAndStartDisablesListening`: HTTP và DB thật; expiry được chèn trong dữ liệu thử nghiệm.
- `VisualReadyCoordinatorTest#diagnosticInvalidStateAfterReadyLoopsThroughPreparationWithoutInvalidatingExpiredRow`: API MOCK; state machine client thật.
- Receipt: `ready-renewal-diagnostic.json`. Cluster tạm đã dừng exit 0, không còn schema thử nghiệm.
- Lượt đầu fixture timestamp vi phạm constraint; đã sửa timestamp fixture và chạy lại thành công. Không thay runtime để làm test đạt.
